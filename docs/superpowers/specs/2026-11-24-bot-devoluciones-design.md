# El bot de NSYS devuelve teléfonos a stock — diseño

Fecha: 24/11/2026. Afecta al servidor (`gestion-reparaciones-servidor`) y al bot de stock (repo de automatización,
`stock/nsys_bot_completo.py`).

## 1. Para qué

Hoy el bot solo descuenta: junta los vendidos del Panel y los del ERP (`GET /api/pedidos/vendidos`) y los mueve a
«Pedidos realizados». Cuando el IMEI de una fila de Pedidos se cambia después de que el bot lo descontara (a mano, la
asociación con el libro de Amazon, el traspaso del corte o el «libro manda» de la 0.9.46), el teléfono de antes se queda en
NSYS como vendido pero sigue físicamente en la estantería: stock que no vemos.

Este trabajo:

1. **Devolución automática** de los IMEI corregidos a la caja donde estaban antes de que el bot los moviera.
2. **«No vendible»:** un móvil no vendible no puede entrar en Pedidos (y por tanto el bot no lo descuenta).
3. **Saneamiento del pasado:** una lista, solo para revisar a mano con los móviles delante, de lo que el bot movió a
   «Pedidos realizados» y ya no tiene venta que lo respalde.

La lista de devoluciones se diseña para que la **reserva de IMEI** (fase 2 de Pedidos, otra sesión) solo tenga que añadir
filas con otro motivo, sin cambiar el contrato.

## 2. Decisiones (brainstorming del 24/11)

| # | Decisión |
|---|----------|
| 1 | El ERP sabe que «lo movió el bot» porque **el bot se lo informa** (no se deduce). |
| 2 | El saneamiento abarca lo que **movió el bot** (según `movimientos.csv`), sigue en «Pedidos realizados» sin venta en NSYS, no está en ninguna fila de Pedidos y no está en las ventas del Panel. |
| 3 | Destino = **la caja donde estaba antes**, y solo si hoy es vendible. **Nunca se inventa una caja.** Sin caja conocida o con caja no vendible: no se devuelve solo. |
| 4 | Un móvil no vendible (por grado o por caja) se guarda con **aviso rojo validable**, lo ponga quien lo ponga (NSYS a veces se equivoca). El bot solo lo descuenta cuando alguien ha **validado ese IMEI** (revisado 25/11; antes: bloqueo + marca). |
| 5 | Si el bot no puede devolver: **3 vueltas seguidas** con error y se deja; `NO_APARECE` se deja a la primera. |
| 6 | Se ve **solo en el historial de la fila**. Sin avisos en la web por ahora; con los datos del historial se valorará si hace falta (pendiente en el plan maestro). |
| 7 | El saneamiento **solo produce la lista**; cómo se devuelven se decide después de revisarla. |
| 8 | Contrato: **dos rutas nuevas** separadas de vendidos (`POST /api/bot/movimientos`, `GET /api/bot/devoluciones`). |
| 9 | **Vender gana a devolver:** un IMEI en una fila de Pedidos, o en la lista de descontar de la vuelta, no se devuelve. |
| 10 | Entran en el paquete del bot los dos pendientes: `en_revisar` una vez al día por IMEI y refiltrar una sola vez. |

Consecuencia de la 1: la devolución automática solo puede tocar lo que el bot haya informado. Lo movido antes de que el bot
empiece a informar solo sale en el saneamiento. Esa es la frontera entre lo acumulado y lo nuevo, sin fechas de corte.

## 3. Vendible

Una sola definición, la usan la devolución (§5) y la pieza «no vendible» (§7). Un móvil **no es vendible** si:

- su grado (el que vale, con el taller) es F, FD o RMA, o ID en un canal de Amazon (regla de la 0.9.30);
- su caja no cuenta como stock: «Pedidos realizados», las de revisar (`LOCATIONS_NO_TOCAR` del bot: «Pendiente revisar si
  hay error de inventario» y la «MV …»), cualquier caja que no cuenta o no se trae, o una caja que no está en
  `Nsys_ubicacion`;
- está Released o de baja en NSYS (`EstadoMovil` VENDIDO o BAJA).

Para la caja: es vendible si `EstadoMovil.de(status, caja)` daría STOCK y no es de revisar.

**Excepción (solo para §7):** un móvil en «Pedidos realizados» porque el bot lo descontó para **esta misma fila**
(`DESCONTADO · OK` en `Bot_movimiento` posterior al apunte que puso ese IMEI en la fila) cuenta como vendible para esa fila.

## 4. Datos

### 4.1 Tabla nueva `Bot_movimiento`

Lo que el bot informa que hizo. Solo se añade; nunca se cambia ni se borra.

| Columna | Tipo | Notas |
|---------|------|-------|
| `ID` | BIGINT AUTO_INCREMENT | |
| `RUN_ID` | VARCHAR(40) | La vuelta del bot |
| `IMEI` | VARCHAR(40) | |
| `ACCION` | VARCHAR(20) | `DESCONTADO` o `DEVUELTO` |
| `RESULTADO` | VARCHAR(20) | `OK`, `NO_APARECE` o `ERROR` |
| `ORIGEN_NSYS` | VARCHAR(80) NULL | Location leída por el bot antes de mover, tal cual |
| `DESTINO_NSYS` | VARCHAR(80) NULL | |
| `MOTIVO` | VARCHAR(30) NULL | En DEVUELTO: el motivo de la lista (§6.1) |
| `FECHA` | DATETIME | Hora de Madrid del bot |
| `RECIBIDO` | DATETIME | Hora en que lo guardó el ERP |

Única `(RUN_ID, IMEI, ACCION)`: un reenvío no duplica. Índice `(IMEI, ACCION, FECHA)`.

### 4.2 Apuntes nuevos en `Pedido_fila_cambio`

En la fila de la que salió el IMEI, sin usuario, con el IMEI en `IMEI_ANTERIOR`:

- `DEVUELTO` — detalle «de Pedidos realizados a <caja>; IMEI cambiado por <usuario o proceso> el <fecha>».
- `NO_DEVUELTO` — detalle con el motivo (`NO_APARECE` o «3 errores»).
- `SIN_DESTINO` — detalle con la caja de origen («<caja> no es vendible»), «sin caja» o «reparado después (<ID_REP>)». Una sola vez por IMEI y fila.

Se añaden a `CambioImei` y al texto del historial en la web.

### 4.3 ~~Marca nueva `NO_VENDIBLE`~~

Descartada el 25/11: se usa el aviso rojo validable de §7.

## 5. La lista de devoluciones

Se calcula en cada llamada; no se guarda. Un IMEI entra si cumple todo:

1. **Estuvo en una fila de Pedidos:** hay un apunte en `Pedido_fila_cambio` en que salió (`IMEI_ANTERIOR` de un `IMEI` o
   un `IMEI_INTERCAMBIADO`). De ahí salen la fila y quién lo cambió. Si salió de varias filas, cuenta la última.
2. **Hoy no está en ninguna fila de Pedidos** (vender gana a devolver).
   **Decidido 25/11 (móvil devuelto por el cliente y vendido otra vez):** una fila **cerrada** que aún tiene el IMEI no cuenta
   si, después de su cierre (`CERRADA_EN`), NSYS registra que el móvil volvió a la empresa (`Nsys_evento` del mismo IMEI, de
   cualquier lote): `VENTA_DESHECHA` (estaba Released y vuelve), `REENTRADA` (entra como lote nuevo), o `UBICACION` que sale de
   «Pedidos realizados» (`DE`) o entra «sin localización» (`A_NSYS` vacío). Una devolución entra siempre «sin localización»
   (Imad). Sin vuelta registrada, la fila cuenta y no se devuelve (en la duda, no). Las filas abiertas cuentan siempre.
3. **Lo movió el bot:** hay un `DESCONTADO · OK` en `Bot_movimiento`.
4. **Nadie lo ha tocado después:** el espejo (`Nsys_movil`) lo tiene `DE_PEDIDO` en «Pedidos realizados», y su última
   entrada en esa caja (último `Nsys_evento` UBICACION con `A_NSYS` = «Pedidos realizados») es la primera exportación
   posterior al `DESCONTADO` del bot, con un margen de 30 min. Si alguien lo sacó y lo volvió a meter, la entrada es
   posterior y no cuenta.
5. **No se devolvió ya:** no hay un `DEVUELTO · OK` posterior a ese `DESCONTADO` (cubre los ~15 min que tarda el espejo en ver
   la vuelta).
6. **No ha agotado los intentos:** no hay un `DEVUELTO · NO_APARECE` ni 3 `DEVUELTO · ERROR` seguidos posteriores a ese
   `DESCONTADO`.

**Destino:** el `ORIGEN_NSYS` de ese `DESCONTADO`. Si hoy no es vendible (§3, por caja o por grado del móvil) o está vacío,
el IMEI **no sale** en la lista y la fila recibe `SIN_DESTINO` (una vez).

**Reparado después (decidido 25/11, caso 404-4440823):** si el móvil tiene una reparación (`Reparacion`, cualquier tipo:
reparación, glass o pulido) con `FECHA_ASIG` posterior al `DESCONTADO` del bot, la caja de antes ya no vale (chasis, tapa,
batería…). El IMEI **no sale** en la lista y la fila recibe `SIN_DESTINO` «reparado después (<ID_REP>)». Lo mueve una persona a
la caja donde esté de verdad.
Ojo con la hora: `Reparacion.FECHA_ASIG` se escribe con `NOW()` de MariaDB (UTC) y `Bot_movimiento`/`Nsys_evento` van en hora
de Madrid; comparar en la misma zona. El saneamiento usa 3 h de margen y lo enseña en la columna «Reparado después».

**Apuntes:** el ERP escribe `DEVUELTO` / `NO_DEVUELTO` al recibir el `POST` del bot (§6.2); `SIN_DESTINO` al calcular la lista.

**Coordinación con «libro manda» (0.9.46):** no se toca su código. Solo se cuenta con que un IMEI retirado de una fila
quede en `Pedido_fila_cambio` como `IMEI_ANTERIOR`, que es lo que ya hace la asociación.

## 6. Contrato ERP ↔ bot

Las dos rutas entran con la clave de máquina que ya usa vendidos (`ClaveVendidosFilter`, ampliada a `/api/bot/**`);
ningún rol de usuario las abre. Vendidos no cambia.

### 6.1 `GET /api/bot/devoluciones`

```json
{ "grupos": [ { "destino": "<caja de NSYS>",
                "imeis": [ { "imei": "35…", "motivo": "IMEI_CORREGIDO" } ] } ] }
```

- Agrupada por caja de destino, en orden estable (caja, IMEI). Tope defensivo de IMEI en total.
- `motivo`: hoy solo `IMEI_CORREGIDO`. La reserva añadirá `RESERVA_LIBERADA` sin más cambios. El bot no mira el motivo para
  mover; lo devuelve en el `POST`.

### 6.2 `POST /api/bot/movimientos`

```json
{ "run": "<RUN_ID>",
  "movimientos": [ { "imei": "35…", "accion": "DESCONTADO", "resultado": "OK",
                     "origen": "<caja>", "destino": "Pedidos realizados", "motivo": null,
                     "fecha": "2026-11-24T10:12:03" } ] }
```

- Valida acción, resultado y tamaño; guarda en `Bot_movimiento` ignorando los repetidos; escribe los apuntes de §4.2.
- Responde con cuántos guardó y cuántos ya estaban.

## 7. No vendible en Pedidos

**Revisado 25/11 con Imad** (sustituye al bloqueo y a la marca `NO_VENDIBLE` de la primera versión).

**EN ESPERA (25/11):** nunca se bloquea el input del IMEI. Otra sesión está cambiando cómo funcionan los avisos y la
validación de la celda; §7 se cierra con sus conclusiones (Imad las pasa al acabar). Hasta entonces, lo de abajo es provisional y
el plan 2 no incluye esta pieza.
Coordinación (25/11): la sesión «IMEI en la celda» (rama web `dev_pedidos_imei_celda`, informe en Apuntes) reescribe
`CeldaImei.tsx`, `TablaFilas.tsx`, `PedidosPage.tsx`, `estado.ts`, `filas.ts`, `AyudaPedidos.tsx` y sus pruebas; «Validar SKU»
queda solo en el panel de la derecha y los avisos de SKU nunca frenan. La parte web de §7 (aviso por caja, validar en impresos)
parte de esa rama una vez mergeada; no tocar esos ficheros en paralelo. El servidor no lo toca esa sesión.

### 7.1 Aviso rojo validable

No se rechaza ni se marca: NSYS a veces se equivoca (p. ej. la capacidad, 24/11). Da igual quién ponga el IMEI (persona,
libro, traspaso): si el móvil no es vendible (§3, con la excepción), la celda lo dice en **rojo** con un motivo corto, y se
puede **validar** como hoy el de capacidad (`VALIDADA_IMEI`). Solo dos motivos:

- **por el grado:** `no se vende: F` (FD, RMA), como ya está desde la 0.9.46.2;
- **por la caja (nuevo):** `no se vende: <caja>` cuando la caja de NSYS no cuenta como stock (Pedidos realizados, revisar, no
  cuenta, no se trae, desconocida). Rojo, fondo rojo, cuenta en «no cuadran» y se da por bueno con «Validar SKU», igual que el
  de grado (tabla de la celda en `docs/modulos/pedidos.md`).

### 7.2 El bot solo descuenta lo validado

En `SQL_VENDIDOS`, una fila cuyo móvil no es vendible solo sale si `VALIDADA_IMEI` = su IMEI. Si se cambia el IMEI, la
validación deja de valer (como hoy). En los pedidos **impresos** se respeta la 0.9.46.2: el aviso solo
informa y no cuenta en «no cuadran»; la única excepción es que el aviso `no se vende` (grado o caja) **sí** se puede validar ahí,
o el bot no descontaría nunca ese móvil. El resto de avisos de un impreso sigue sin validarse.

**Ojo:** el código de la celda y de la asociación es de las versiones 0.9.46.x (en `main`). Esta pieza va encima, en la 0.9.47.

## 8. El bot

### 8.1 Una vuelta

1. Login.
2. Descontar como hoy (Panel + ERP + repesca → «Pedidos realizados»). Lo que mueve se guarda con su Location leída.
3. **Devolver** (§8.2), solo si `"devolver": true` en `stock_config.json`.
4. `POST /api/bot/movimientos` (§8.3).
5. Exportar Items, como hoy.

### 8.2 Devolver

- `GET /api/bot/devoluciones`. Si falla, en esta vuelta no se devuelve nada y todo lo demás sigue.
- Antes de nada, quita de los grupos cualquier IMEI de la lista de descontar de esta vuelta.
- Como mucho **5 grupos por vuelta**; el resto, en la siguiente.
- Por grupo:
  - Filtros (3 de los 4 que admite NSYS): IMEI = los del grupo; Status ≠ Released; **Location = Pedidos realizados**.
  - Antes de mover cada página, lee Location de las filas (la lectura de la regla de revisar). Si alguna no está en «Pedidos
    realizados», no mueve esa página.
  - Mueve las filas a `destino`.
  - Los IMEI del grupo que no aparecen: `NO_APARECE`.
  - Un fallo de NSYS a mitad del grupo: `ERROR` para lo que quedaba en el grupo, limpia filtros y sigue con el siguiente.
- La devolución nunca impide exportar Items.

### 8.3 Informar al ERP

- Al final de la vuelta, `POST` con los `DESCONTADO` y los `DEVUELTO`.
- Si el ERP no responde, lo guarda en `Automatizacion_datos\sistema\bot_movimientos_pendientes.json` y lo reenvía en la
  vuelta siguiente (el ERP descarta los repetidos).
- Un `DESCONTADO` perdido solo significa que ese móvil no se devolverá solo: el fallo es seguro.
- `movimientos.csv` sigue igual y apunta también `devuelto`, `no_aparece_devolver` y `error_devolver`.

### 8.4 Los dos pendientes del paquete

- `en_revisar` se apunta en `movimientos.csv` una vez al día por IMEI (hoy unas 144 filas al día por IMEI).
- Al detectar móviles en revisar, recorre las páginas que quedan, junta todos los de revisar y refiltra **una sola vez**
  (se quita el tope `INTENTOS_FILTRO_REVISAR`).

### 8.5 Tiempo

Cada grupo cuesta una pasada más de filtros, unos 20–30 s. Lo normal serán 0 o 1 grupos por vuelta; con el tope de 5, una
vuelta no debería pasar de unos 6–7 min dentro del ciclo de 10. Se mide en el ensayo (§10).

## 9. Saneamiento (solo lista)

1. **Consulta de solo lectura en producción** (bloque SSH que ejecuta Imad: `hostname`, comprobación de
   `/usr/local/sbin/backup-erp.sh`, la consulta). Saca, de todos los móviles `DE_PEDIDO` en «Pedidos realizados»: IMEI,
   modelo, grado, fecha de entrada en la caja, caja de origen (`A_NSYS` del evento UBICACION o ENTRADA anterior, o «sin
   caja») y, de `Pedido_fila_cambio`, si estuvo en alguna fila, en cuál, quién se lo quitó y si sigue en alguna.
2. **Script en el PC** que cruza ese resultado con `movimientos.csv` (traído del PC del bot por USB: ¿lo movió el bot?) y con
   las ventas del Panel (¿hay venta que lo respalde?).
3. **Excel en `Documents/Apuntes`** (lleva IMEI reales: nunca en el repo). Una fila por IMEI con todo lo anterior, los días en
   «Pedidos realizados» sin venta en NSYS y una columna vacía **«Comprobado»**. Ordenado de más días a menos.

Entra en la lista lo que cumple las condiciones de la decisión 2.

**Punto abierto:** cuánta historia de ventas da el Panel con `accion=verificacion`. Se comprueba al escribir el plan; si se
queda corta, la lista lo dice y esa condición solo cubre esos días.

## 10. Salida, pruebas y vuelta atrás

### 10.1 Orden

1. **Saneamiento.** Solo lectura, sin desplegar. Imad lo revisa con los móviles delante; después se decide cómo devolverlos.
2. **ERP**, versión 0.9.47 (orden acordado: 0.9.46 libro manda, 0.9.47 esta, 0.9.48 reserva de IMEI): migración que solo añade (`Bot_movimiento`), las dos rutas, la regla
   de la lista, los apuntes, la pieza «no vendible»; sube el número de la web. Al desplegar, la lista sale vacía (no hay
   `DESCONTADO` informados).
3. **Bot, fase «informar»:** `POST` de lo que descuenta, sin devolver, más los dos pendientes. `Bot_movimiento` se llena y
   se puede mirar qué daría la lista sin mover nada.
4. **Bot, fase «devolver»:** `"devolver": true`, después del ensayo de solo lectura con Imad delante y de unos días mirando
   la lista. Vuelta atrás: `false`, sin reinstalar.

### 10.2 Pruebas

- **Servidor (TDD):** la regla de la lista condición a condición, un test por cada caso que no debe salir (en una fila,
  lo movió una persona, movido después, ya devuelto, intentos agotados, caja no vendible, grado F); agrupación; repetidos del
  `POST`; apuntes; clave de máquina (403 con roles de usuario); «no vendible»: aviso por grado y por caja, excepción de la misma fila, la fila no sale en vendidos sin validar y sí validada (también impresa). `mvn test` completo, contexto incluido.
- **Bot (pytest):** quitar de devolver lo que se descuenta; tope de 5 grupos; página con una fila fuera de «Pedidos
  realizados» no se mueve; `NO_APARECE`; un error en un grupo no corta la vuelta; pendientes del `POST` y reenvío;
  interruptor apagado; los dos pendientes.
- **Ensayo en la web real** (como `herramientas/ensayo_revisar.py`): los 3 filtros de devolver con unos IMEI que estén en
  «Pedidos realizados», lectura de la tabla y tiempo, **sin mover nada**, con Imad delante, antes de instalar.
- **E2E en preprod** con datos sintéticos por API: fila con IMEI → `POST DESCONTADO` simulado → cambiar el IMEI → la lista
  lo da con su destino → `POST DEVUELTO` → apunte en el historial; limpieza. Nunca en producción.

### 10.3 Instalación del bot

Prompt por USB para el Claude del PC de producción: revisar primero lo instalado, copias `.bak_<fecha>`, y traer lo que
cambió para guardarlo en el repo. Un fallo de login del bot no se debe al cambio: no se restaura por eso.

## 11. Fuera de este trabajo

- Avisos de devoluciones en la web (Pedidos o «Qué revisar»): se valora con los datos del historial. Pendiente en el plan
  maestro con la pregunta «¿cuántas devoluciones fallan al mes?».
- Cómo se devuelven los del saneamiento: después de revisar la lista.
- La reserva de IMEI: solo añadirá filas con `motivo = RESERVA_LIBERADA` a §6.1.

**Nota (24/11, tras la 0.9.45 «celda del IMEI y cajas»):** la migración de la 0.9.45 renombró en BD las ubicaciones del ERP
«Taller · Pulir/Glass/Reparación/Pantalla rota» → «Caja · …» y «Listos» → «Caja · Listos» (`Nsys_ubicacion`, `Nsys_movil`,
`Nsys_evento`.`DE`/`A`). La regla de destino usa `A_NSYS` (caja de NSYS tal cual, no cambia) y las marcas de
`Nsys_ubicacion`; nunca compara con nombres de ubicación del ERP.

**Nota (24/11 tarde, tras la 0.9.46 «libro manda», en prod 18:05):** los descuadres ERP≠libro de Amazon ya los corrigió la
0.9.46; cada IMEI quitado queda en `Pedido_fila_cambio` TIPO `IMEI` con `IMEI_ANTERIOR` y `DETALLE` «LIBRO», o sea que
entra en la regla de §5 sin cambios. Lo acumulado se recoge con una 2.ª pasada del saneamiento (§9); no hace falta una carga
aparte de `movimientos.csv` para ellos. La reserva de IMEI (0.9.48) está implementada con `pedidos.reserva.al-bot=false`
hasta que exista esta lista: añadirá filas con motivo `RESERVA_LIBERADA` (§6), sin cambiar el contrato.

**Nota (24/11 noche, 0.9.46.2 en prod, servidor 43e85fb / web 61ad299):** el libro manda siempre (pone el IMEI aunque no
cuadre o esté en otro pedido, y corrige pedidos ya asociados si cambia el libro); cada IMEI quitado sigue quedando como TIPO
`IMEI` + `DETALLE` «LIBRO». Habrá más IMEI retirados, pero el contrato no cambia. La marca `NO_VENDIBLE` se descartó (25/11): ver §7.

**Decidido (25/11, caso 404-4440823):** reparado después → sin devolución automática, lo mueve una persona (§5).
