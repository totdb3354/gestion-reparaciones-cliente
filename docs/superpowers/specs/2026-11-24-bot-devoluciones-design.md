# El bot de NSYS devuelve teléfonos a stock — diseño

Fecha: 24/11/2026. Afecta al servidor (`gestion-reparaciones-servidor`) y al bot de stock (repo de automatización,
`stock/nsys_bot_completo.py`).

## 1. Para qué

Hoy el bot solo descuenta: junta los vendidos del Panel y los del ERP (`GET /api/pedidos/vendidos`) y los mueve a
«Pedidos realizados». Cuando el IMEI de una fila de Pedidos se cambia después de que el bot lo descontara (a mano, la
asociación con el libro de Amazon, el traspaso del corte o el «libro manda» de la 0.9.45), el teléfono de antes se queda en
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
| 4 | Un móvil no vendible no entra en Pedidos: **bloqueo** si lo teclea una persona; si lo escribe el libro o el traspaso, se guarda con marca y el bot no lo descuenta. |
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
- `SIN_DESTINO` — detalle con la caja de origen («<caja> no es vendible») o «sin caja». Una sola vez por IMEI y fila.

Se añaden a `CambioImei` y al texto del historial en la web.

### 4.3 Marca nueva `NO_VENDIBLE`

Marca de fila (§7.2) con el motivo en la nota. La pone el ERP, no una persona.

## 5. La lista de devoluciones

Se calcula en cada llamada; no se guarda. Un IMEI entra si cumple todo:

1. **Estuvo en una fila de Pedidos:** hay un apunte en `Pedido_fila_cambio` en que salió (`IMEI_ANTERIOR` de un `IMEI` o
   un `IMEI_INTERCAMBIADO`). De ahí salen la fila y quién lo cambió. Si salió de varias filas, cuenta la última.
2. **Hoy no está en ninguna fila de Pedidos** (vender gana a devolver).
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

**Apuntes:** el ERP escribe `DEVUELTO` / `NO_DEVUELTO` al recibir el `POST` del bot (§6.2); `SIN_DESTINO` al calcular la lista.

**Coordinación con «libro manda» (0.9.45):** no se toca su código. Solo se cuenta con que un IMEI retirado de una fila
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

### 7.1 Lo teclea una persona

Al poner el IMEI en una fila (`FilasDAO.SQL_PONER_IMEI`), si el móvil no es vendible (§3, con la excepción), el ERP lo
rechaza con el motivo («grado F», «está en Pedidos realizados», «está en <caja>», «vendido en NSYS»…), como hoy rechaza uno
que está en otra fila. Para estos casos deja de existir la opción de darlo por bueno (`VALIDADA` de `GRADO_NO_VENDIBLE`). Los
`VALIDADA` que ya existen se quedan como están.

### 7.2 Lo escribe el libro o el traspaso

`AsociacionAmazon.SQL_ACTUALIZAR` y `FilasDAO.SQL_TRASPASAR_IMEI` guardan el IMEI y, si no es vendible, el ERP pone en la fila
la marca `NO_VENDIBLE` con el motivo en la nota (apunte `MARCA` en el historial). Una fila con marca no sale en vendidos. Al
poner otro IMEI, el ERP quita la marca («al poner el IMEI»), como ya hace con otras. Si el libro tenía razón y el móvil
salió de verdad, una persona lo mueve a mano en NSYS.

**Ojo con «libro manda» (0.9.45):** esa sesión cambia `AsociacionAmazon`. La marca de §7.2 se engancha después de que esté
en `main`, sobre su código.

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
2. **ERP**, siguiente versión libre después de la 0.9.45: migración que solo añade (`Bot_movimiento`), las dos rutas, la regla
   de la lista, los apuntes, la pieza «no vendible»; sube el número de la web. Al desplegar, la lista sale vacía (no hay
   `DESCONTADO` informados).
3. **Bot, fase «informar»:** `POST` de lo que descuenta, sin devolver, más los dos pendientes. `Bot_movimiento` se llena y
   se puede mirar qué daría la lista sin mover nada.
4. **Bot, fase «devolver»:** `"devolver": true`, después del ensayo de solo lectura con Imad delante y de unos días mirando
   la lista. Vuelta atrás: `false`, sin reinstalar.

### 10.2 Pruebas

- **Servidor (TDD):** la regla de la lista condición a condición, un test por cada caso que no debe salir (en una fila,
  lo movió una persona, movido después, ya devuelto, intentos agotados, caja no vendible, grado F); agrupación; repetidos del
  `POST`; apuntes; clave de máquina (403 con roles de usuario); «no vendible»: bloqueo al teclear, excepción de la misma fila,
  marca en libro y traspaso, quitar la marca al poner otro IMEI. `mvn test` completo, contexto incluido.
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
