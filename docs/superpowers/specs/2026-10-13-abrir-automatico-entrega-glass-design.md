# «Abrir» automático al entregar a glass y atajo «Cerrar»

Fecha: 2026-10-13
Estado: **APROBADA por el usuario (2026-10-13, brainstorm en sesión)**, con prototipo interactivo aprobado: `assets/2026-10-13-prototipo-abrir-automatico.html` (datos inventados).
Repos: servidor (`gestion-reparaciones-servidor`) y web (`gestion-reparaciones-web`). El JavaFX está retirado: no se toca.
Versión: la siguiente tras la 0.9.10 (se fija al planificar).

---

## 1. Contexto y motivación

Siempre que un teléfono baja a glass, alguien lo ha abierto antes. Hasta ahora ese trabajo se apuntaba a mano como acción «otro» («abrir», «Abrir teléfono»…), pero unos técnicos lo hacían y otros no. Resultado: el historial no refleja quién abrió el teléfono y las estadísticas por puntos no comparan en igualdad (cada acción «otro» puntúa).

Al volver de glass, quien cierra el teléfono lo apunta también como acción «otro»; si lo cierra alguien distinto del que lo entregó, se le reasigna la reparación normal a esa persona. Ese paso **sigue siendo manual**: este cambio solo automatiza el abrir y normaliza el texto del cerrar.

Base existente (spec `2026-08-28-entrega-glass-design.md`): «Entregar a X» (dueño de la reparación normal `A…`) sella `ENTREGADO_AT`/`ENTREGADO_POR` en las `AG…` abiertas del IMEI; «Marcar que llegó» es la válvula del técnico de glass cuando arriba no se pulsó «Entregar»; ambos se deshacen solo por quien firmó. Las acciones «otro» son filas reales `R…`/`G…` con el componente `otroi<modelo>`, `CANTIDAD = 0` (neutras de stock) y la descripción en `OBSERVACIONES`; «✓ Guardar» de una línea las guarda sin cerrar la asignación (`guardarFilaIndividual`).

En los ejemplos, «Luis» es el técnico de la reparación normal y «Ana» la de glass (nombres inventados).

## 2. Comportamiento

### 2.1 Al entregar («Entregar a Ana», lo pulsa Luis)

- Se sella la entrega como hoy.
- **En la misma operación** se guarda una acción «otro» con el texto exacto **`Abrir`**, a nombre de Luis, colgada de su reparación normal (fila `R…` nueva; la `A…` sigue abierta). Componente `otroi<modelo del teléfono>`, `CANTIDAD = 0`, no reutilizada, con el mismo `ID_REP_ANTERIOR` que la `A…` (si viene de incidencia).
- Aviso breve: **«Entregado a Ana · Abierto»**.

### 2.2 Al marcar la llegada («Marcar que llegó», lo pulsa Ana)

- Igual, pero el «Abrir» se apunta al **dueño de la reparación normal abierta más antigua del IMEI** (el de la píldora «Rep: Luis»), colgado de esa `A…`.
- Sin reparación normal abierta en el IMEI → no se apunta a nadie (la llegada se registra igual).
- Aviso breve: **«Llegada registrada · Abierto (Luis)»**.

### 2.3 Cuándo NO se crea el «Abrir» (la entrega o llegada se registra igual)

| Caso | Regla | Aviso breve |
|---|---|---|
| Ya apuntado a mano | El técnico al que se apuntaría ya tiene, en ese IMEI, una acción «otro» de reparación (`R…`) guardada en o después de la `FECHA_ASIG` de su `A…` cuyo texto (sin espacios a los lados, sin distinguir mayúsculas) empieza por `abr` o `abi` | «Entregado a Ana · ya estaba abierto» |
| Sin pieza «otro» | El teléfono no tiene modelo, o el modelo no tiene componente `otroi<modelo>` | «Entregado a Ana · sin "Abrir" (el modelo no tiene pieza otro)» |
| Llegada sin normal abierta | §2.2 | «Llegada registrada» |

(En la llegada los textos empiezan por «Llegada registrada · …» en lugar de «Entregado a Ana · …».)

### 2.4 Al deshacer («Deshacer entrega» / «Deshacer llegada»)

- Se borra **el «Abrir» que creó esa entrega, y solo ese** (el que guarda la `AG…`, §3). Si la entrega no creó ninguno (ya existía uno a mano, sin pieza, sin normal) no se borra nada.
- Si ese «Abrir» ya no existe (un supertécnico lo borró desde el Historial), no pasa nada.
- Aviso breve: «Entrega deshecha · "Abrir" borrado» / «Llegada deshecha · "Abrir" borrado», o sin la segunda parte si no había nada que borrar.
- Volver a entregar tras deshacer crea un «Abrir» nuevo (el anterior se borró).

### 2.5 El «Abrir» es una acción más

- Sale en Historial, Agrupado y CSV como cualquier acción «otro» (Componente `otroi<modelo>`, Observaciones `Abrir`).
- El supertécnico puede editarla o borrarla como cualquier otra.
- Es un hecho: no cambia si después se reasigna la normal o la glass.
- Riesgo aceptado: si alguien vuelve a apuntar «Abrir» a mano **después** del automático, queda duplicado (se ve en el Historial y el supertécnico lo borra). Se avisa al equipo de que el abrir ya es automático.

### 2.6 Atajo «Cerrar» en Otras acciones

- Botón **«+ Cerrar»** junto a «+ Añadir acción», en los formularios de **reparación y de glass**, solo al completar (no en modo editar).
- Añade una línea con el texto `Cerrar` ya escrito y enfocada, lista para «✓ Guardar» (se puede editar o quitar como cualquier línea nueva).
- **Solo aparece si el IMEI tiene más aperturas que cierres**: se cuentan todas las acciones «otro» guardadas del IMEI, en reparación y glass juntas; apertura = texto que empieza por `abr` o `abi`, cierre = texto que empieza por `cerr` (mismo criterio de §2.3). Las líneas del propio formulario cuyo texto empieza por `cerr` (guardadas en esta apertura o no) también cuentan como cierre: el atajo desaparece al pulsarlo.

| Situación | Aperturas | Cierres | ¿Atajo? |
|---|---|---|---|
| Cambio de pantalla sin glass | 0 | 0 | No |
| Entregado a glass | 1 | 0 | Sí |
| Vuelve y se guarda «Cerrar» | 1 | 1 | No |
| Meses después, otra vez a glass | 2 | 1 | Sí |

- Es un empujón, no un bloqueo: escribir «Cerrar» a mano sigue siendo posible.
- Si la consulta de acciones falla, el atajo no sale (silencioso).

### 2.7 Aviso breve (componente nuevo en la web)

Hoy la web no avisa de los éxitos (las marcas de urgente, por cerrar o entrega recargan en silencio) y los errores usan el diálogo de `AlertaProvider`. Para los mensajes de §2.1–2.4 se añade un **aviso breve** en `shared/ui`: no es un modal (no oscurece la pantalla, no roba el foco, no hay que pulsar nada y se puede seguir trabajando), aparece abajo al centro con una **barra que va encogiendo** durante 4 s para indicar que se va, y al vaciarse desaparece solo. Un clic lo cierra antes. Si llega otro, sustituye al anterior y la barra vuelve a empezar. Los errores siguen yendo por `AlertaProvider` como hoy.

Aspecto (el del prototipo): fondo azul noche, texto crema, barra amarilla de 4 px en el borde inferior, y un icono redondo a la izquierda según el resultado:

| Icono | Cuándo |
|---|---|
| ✓ verde | «Abrir» creado o borrado |
| «i» gris azulado | ya estaba abierto, llegada sin normal abierta, o deshacer sin «Abrir» que borrar |
| «!» ámbar | sin pieza «otro» (caso de seguridad: la entrega se hace igual) |

### 2.8 Puntos (estadísticas)

1 punto ≈ 30 min. Claves nuevas en `Dificultad_puntos`:

| Clave | Puntos | Por qué |
|---|---|---|
| `abrir` | 0,25 | igual que una acción «otro» hoy |
| `cerrar` | 0,17 | 5 min = 5/30 = 0,1667, a 2 decimales |

- Una pieza de componente «otro» (`TIPO` empieza por `otro`) cuyo texto empieza por `abr`/`abi` puntúa `abrir`; por `cerr`, `cerrar`. El resto de acciones «otro», las reparaciones sin piezas y los SKU no reconocidos siguen como `otro`.
- Si falta la clave `abrir` o `cerrar` en la tabla, puntúa como `otro` (hoy una clave que falta puntúa 0: así nunca puntúan menos que antes del cambio).
- Los puntos se calculan al consultar con los valores vigentes: aplica también hacia atrás (los «cerrar» antiguos pasan de 0,25 a 0,17; los «abrir» antiguos no cambian).
- Hoy no hay pantalla de Estadísticas ni modal de valores en la web: los valores entran por la migración y se cambian por SQL hasta que se migre Estadísticas (su modal leerá las claves de la tabla y mostrará las dos nuevas).

## 3. Base de datos

Migración `gestion-reparaciones-servidor/sql/migracion-abrir-glass.sql` (vista previa → cambio → comprobación), solo aditiva:

```sql
ALTER TABLE Reparacion
    ADD COLUMN ID_REP_ABRIR VARCHAR(30) NULL AFTER ENTREGADO_POR;

INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('abrir',  0.25),
    ('cerrar', 0.17);
```

- `ID_REP_ABRIR` solo se rellena en filas `AG…`: el `R…` del «Abrir» que creó su entrega o llegada. Sin FK (si el `R…` se borra a mano, deshacer lo ignora). Con varias `AG` abiertas en el IMEI (caso jamás visto) todas apuntan al mismo `R…`.
- La `G…` que nace al completar la glass **no** hereda `ID_REP_ABRIR` (solo sirve para deshacer mientras la `AG` está abierta).
- `crear_bd.sql` en sync (columna y las dos filas de la semilla).
- MER `Apuntes/Tabla BBDD(Corregido).drawio`: `ID_REP_ABRIR: VARCHAR(30) (nullable, solo filas AG)`.
- Compatibilidad: la web actual ignora la columna; el servidor actual no la lee.

## 4. Servidor

### 4.1 Entrega, llegada y sus deshacer

Los cuatro endpoints existentes (`PATCH …/{idRep}/entrega-glass`, `PATCH` y `DELETE …/{idRep}/llegada`) mantienen ruta, cuerpo de petición y validaciones (422/404/403 de la spec de 2026-08-28). Cambia:

- **Una transacción** por petición: sellar/limpiar la entrega + crear/borrar el «Abrir» + `ID_REP_ABRIR` (lógica en un servicio transaccional nuevo; el controlador solo valida, llama y registra el log).
- **Crear el «Abrir»**: resolver el técnico y la `A…` (§2.1/§2.2) → comprobar duplicado (§2.3) → resolver el componente `otro` del modelo del teléfono con `CatalogoPiezas.modelo` (mismo criterio que la web) → insertar con la misma lógica que `guardarFilaIndividual` (fila `R…` + `Reparacion_componente` con `CANTIDAD = 0`, `OBSERVACIONES = 'Abrir'`) → `UPDATE … SET ID_REP_ABRIR = ?, UPDATED_AT = UPDATED_AT` en las `AG` abiertas del IMEI.
- **Deshacer**: leer los `ID_REP_ABRIR` no nulos de las `AG` abiertas del IMEI, borrar ese `R…` con el borrado existente (`ReparacionDAO.eliminar`, stock neutro) si existe, y poner la columna a `NULL`.
- **Respuesta**: pasa de 204 a **200** con cuerpo (la web actual no lo lee):
  - entregar / llegada: `{ "abrir": "CREADO" | "YA_EXISTIA" | "SIN_PIEZA_OTRO" | "SIN_NORMAL", "tecnicoAbrir": "Luis" | null }`
  - deshacer: `{ "abrirBorrado": true | false }`
- **Log**: `ENTREGAR_GLASS` / `DESHACER_ENTREGA_GLASS` añaden al detalle `, ABRIR: R…` o `, ABRIR: YA_EXISTIA|SIN_PIEZA_OTRO|SIN_NORMAL` (y `, ABRIR BORRADO: R…` al deshacer). Sin acciones de log nuevas.

### 4.2 Estadísticas por puntos

- `getEstadisticasPuntos` añade `rc.OBSERVACIONES` a la consulta; `FilaPuntos` y `Pieza` llevan la observación.
- `PuntosCalculo`: clave `abrir`/`cerrar` según §2.8, con caída a `otro` si falta la clave.
- `gestion-reparaciones-cliente/docs/metricas-estadisticas.md` (diccionario de métricas): las dos claves nuevas, la regla del texto y la caída a `otro`.

## 5. Web

- `api.ts`: `useEntregaGlass`, `useMarcarLlegada`, `useDeshacerLlegada` leen la respuesta y muestran el aviso breve (§2.7) con el texto de §2.1–2.4. Esquema OpenAPI regenerado.
- Textos en una función pura en `lib/entregaGlass.ts` (con test), junto al resto de textos de la entrega.
- Formulario (modos completar de reparación y glass): la carga pide además las acciones «otro» del IMEI con el endpoint existente `GET /api/reparaciones/imei/{imei}/acciones` (dos llamadas en paralelo, `categoria=R` y `categoria=G`, sin `excluir`; si fallan → lista vacía). Selector puro `atajoCerrarVisible(estado)` (§2.6) y acción `ANADIR_CERRAR` en el reductor; botón «+ Cerrar» en `OtrasAcciones`.
- Nada más cambia: badges, píldoras, «Por cerrar», carga de técnicos y CSV siguen igual.

## 6. Decisiones (con su porqué)

1. **Servidor y no web**: entrega y «Abrir» en una transacción; un reintento no duplica; la regla vive en un solo sitio.
2. **El «Abrir» es una fila real**, no un punto implícito por entrega: sale en el Historial, se edita y borra como cualquier acción, y encaja con cómo ya trabajaban algunos.
3. **Llegada → dueño de la normal**: casi siempre lo abrió él y solo se olvidó de pulsar «Entregar».
4. **`ID_REP_ABRIR` en la `AG`**: deshacer borra exactamente lo que creó la entrega, sin confundirlo con un «abrir» manual.
5. **`abr`/`abi` para detectar aperturas manuales**: cubre «abrir», «Abrir teléfono», «abierto para glass».
6. **Atajo solo «Cerrar»**: el abrir ya es automático; un atajo «Abrir» invitaría a duplicarlo.
7. **Atajo por cuenta aperturas > cierres**: sin fechas ni asignaciones; se adapta a teléfonos que vuelven y no tienta a sumar «Cerrar» en reparaciones sin glass.
8. **Valores por migración + caída a `otro`**: no hay modal en la web hasta migrar Estadísticas; nunca puntúan menos que antes.

## 7. Fuera de alcance

Automatizar el cerrar o la devolución glass → arriba. Bloquear «Cerrar» escrito a mano o sin apertura previa. Mostrar en el formulario de completar las acciones ya guardadas. Modal de valores y pantalla de Estadísticas en la web (sub-proyecto de Estadísticas: debe incluir `abrir` y `cerrar`). Rellenar «Abrir» en entregas pasadas.

## 8. Despliegue

- Migración aditiva y web actual compatible → candidata a despliegue **en horario** (criterio de 2026-10-08), primero en preproducción.
- Orden: migración → servidor → web.
- Antes de desplegar: avisar al equipo de que el abrir ya es automático y del atajo «Cerrar».
- `Apuntes/plan-futuro.md`: nota en el bloque de Estadísticas (el modal de valores incluye `abrir` y `cerrar`).

## 9. Verificación

- **Servidor** (JUnit, patrón de `ReparacionControllerEntregaGlassTest`): entregar crea el «Abrir» del que entrega y rellena `ID_REP_ABRIR`; no lo crea con un «abr…»/«abi…» previo desde la asignación (sí con uno anterior a ella); sin modelo o sin pieza «otro» → `SIN_PIEZA_OTRO` y la entrega se sella igual; llegada → al dueño de la normal más antigua, `SIN_NORMAL` sin normal; deshacer borra solo ese `R…` y limpia la columna; deshacer con el `R…` ya borrado no falla; las validaciones 422/404/403 siguen igual; `PuntosCalculo`: «Abrir»/«abierto…» → 0,25, «Cerrar» → 0,17, otro texto → `otro`, clave ausente → valor de `otro`.
- **Web** (Vitest): textos del aviso para cada resultado; `atajoCerrarVisible` con la tabla de §2.6 (incluidas líneas no guardadas y fallo de la carga); «+ Cerrar» añade la línea enfocada; el aviso breve se cierra solo.
- **E2E en preproducción** (Chromium, datos sintéticos por API y limpieza que deja todo igual): IMEI con normal + glass → Entregar → aviso y «Abrir» en el Historial a nombre del que entrega → Deshacer → el «Abrir» desaparece → Marcar que llegó (desde glass) → «Abrir» al de la normal → completar glass → en el formulario de la normal sale «+ Cerrar» → guardarlo → el atajo ya no sale.
