# 0.9.21 — SKU del taller (pieza 1: la base)

Fecha: 2026-10-21. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va en la **0.9.21**. Al principio iba en una subida conjunta de la 0.9.19, pero la 0.9.19 (completar pulidos y formulario
«Nueva o Reutilizada») y la 0.9.20 (panel de carga) salieron a producción el 2026-10-21 sin esperar a esto; así que sale
sola. **Servidor + base + web**, desde los `main` de ese momento (web `55db8e9`, 0.9.20; servidor 0.9.18.2). El SKU del
taller se parte en dos piezas (decisión del 2026-10-21): esta es la **base**; la **pieza 2** (lo automático desde las
reparaciones: chasis de otro color → color, chasis nuevo → A, batería nueva → «100») va en su propia spec y se apoya en esta.
Prototipo aprobado: `assets/2026-10-21-v0921-prototipo-sku-del-taller-base.html` (variantes elegidas: lista «A · se vacía sola»
y «NSYS: Azul» debajo).
Decisiones de producto previas: `Apuntes/plan-futuro.md` §5 («SKU del taller»).

## 1. Qué cambia

**Hoy** el color, el grado y el SKU de un móvil son los de NSYS (el espejo `Nsys_movil`, que la importación rehace desde el
maestro en cada pasada). Cuando el taller transforma un móvil (chasis de otro color, batería nueva, sube de grado), en NSYS se
cambia a mano y casi nunca: el ERP sigue enseñando lo de antes y la venta sale de NSYS con el dato viejo.

**Ahora** el ERP guarda **lo del taller por encima de NSYS** y lo enseña en todas partes, con lo de NSYS al lado; calcula el
SKU nuevo con el maestro; deja corregirlo a mano; y lleva una lista **«Por pasar a NSYS»** con lo que hay que cambiar en NSYS y
qué poner. El ERP no escribe en NSYS (solo lee el Items): la lista es la cola de deberes para quien lo actualiza.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Qué guarda el taller | Color y grado (el grado lleva dentro la batería nueva, «100», y el mensaje de pieza, «Ms»), cada uno por separado. |
| Hasta cuándo manda | Mientras NSYS no lo alcance. Si NSYS pasa a coincidir → «pasado». Si **después** alguien pone en NSYS **otro** valor en ese dato → **manda NSYS** (lo más reciente), dato a dato. |
| Lista «Por pasar a NSYS» | Se **vacía sola** cuando la importación ve NSYS igual (cada ~30 min). **Sin** botón «Ya lo pasé» (un error al pasarlo a NSYS seguiría a la vista). |
| Quién la trabaja | Supertécnico y admin (los que ya ven el stock de teléfonos). Sin permisos nuevos. |
| Cómo se ve | El valor del taller con la marca «taller» y, debajo, en pequeño y gris, «NSYS: Azul». |
| Corrección a mano | Color, grado, batería nueva y Ms; motivo obligatorio; supertécnico y admin. Modelo, capacidad y eSIM no (no cambian en el taller). |

## 2. Comportamiento

### 2.1 Qué vale
- Para cada móvil (por IMEI), **lo que vale** es lo del taller vigente para ese dato, si lo hay; si no, lo de NSYS.
- Va **por IMEI**, no por entrada en stock: un móvil vendido que vuelve sigue siendo del color que le dejó el taller (hasta que
  NSYS lo alcance o lo cambie).
- **SKU que vale:** se busca en `Nsys_producto` con el modelo, la capacidad y la eSIM del móvil (los del espejo) y el color y
  el grado que valen (`CALIDAD_DETALLE` = grado). Si hay **una** fila con `SKU_WEB` → ese SKU; si no hay ninguna, o hay varias con
  SKU distinto, o el móvil no tiene modelo en el maestro → **«sin SKU en el maestro»** (se guarda igual; no bloquea).
- Si un móvil no tiene nada del taller, todo sigue exactamente como hoy (el SKU, el de `Nsys_movil.SKU`).

### 2.2 Cierre automático (tras cada importación)
Tras cada importación **ACTUAL** que acaba en `OK`, para cada dato del taller vigente se mira el valor de NSYS ahora (la fila
más reciente del IMEI en `Nsys_movil`) y el que NSYS tenía cuando se hizo el cambio del taller:
- NSYS **ahora = lo del taller** → se cierra como **«pasado a NSYS»**.
- NSYS ahora **≠ lo que tenía al hacer el cambio** (y ≠ lo del taller) → se cierra como **«manda NSYS»**: alguien lo cambió
  después a otra cosa.
- Si no → sigue vigente.
Las dos cosas quedan en el rastro. Un móvil que ya no está en el espejo deja sus cambios como estaban.

### 2.3 Corrección a mano
- En la **ficha del IMEI** (página del IMEI, a la que lleva pulsar un móvil del stock), botón **«Corregir color, grado o
  batería…»**, solo para supertécnico y admin, y solo si el IMEI está en el espejo.
- Diálogo:
  - **Color:** los colores del maestro para ese modelo, capacidad y eSIM (distintos de `Nsys_producto.COLOR`), empezando por el
    que vale.
  - **Grado:** A, A-, B o C, más las casillas **«Batería nueva (100)»** y **«Mensaje de pieza (Ms)»**. El grado resultante se
    escribe como en NSYS: `[Ms]<letra>[100]` (p. ej. `A100`, `MsA-`). Si el grado que vale no es de esa forma (ID, F, n/a…), el
    selector empieza vacío y el grado solo cambia si se elige.
  - **Motivo:** obligatorio.
  - **Vista previa:** color de → a, grado de → a, SKU nuevo (o «sin SKU en el maestro: se guarda igual») y «En NSYS habrá que
    poner: <producto> · grado <grado>».
  - **«Volver a lo de NSYS»** (si hay algo del taller): quita lo del taller del móvil (con el motivo, si se ha escrito).
  - «Guardar» solo con algún cambio y motivo.
- Al guardar, por cada dato cambiado:
  - si el valor nuevo **es el de NSYS**, se quita lo del taller de ese dato (no queda pendiente);
  - si no, queda vigente (sustituye al anterior del taller, si lo había);
  - queda en el **registro de actividad** (acción `CORREGIR_MOVIL_TALLER`, detalle con IMEI, dato, de → a y motivo) y en el
    **rastro**.

### 2.4 Pantallas
- **Stock de teléfonos:** la tabla, los filtros, la columna de modelos y la tabla color × grado usan **lo que vale**. En la
  tabla, las celdas Color, Grado y SKU cambiadas por el taller llevan la marca «taller» y debajo «NSYS: <valor>».
- **Ficha del IMEI:** lo mismo en Color, Grado y SKU; si hay algo por pasar, una línea «Por pasar a NSYS: <producto> · grado
  <grado>».
- **«Pegar varios IMEIs»** y el buscador de IMEI: color y grado que valen, con la marca.
- **Rastro:** cada cambio del taller («Taller: color Azul → Rojo (a mano · Imad · «motivo»)») y cada cierre («Pasado a NSYS»,
  «Manda NSYS: …», «Vuelve a lo de NSYS»), mezclados por fecha con lo de NSYS y los trabajos, como hoy.
- **Pestaña «Por pasar a NSYS (N)»** en el stock de teléfonos, junto a la vista de móviles:
  - columnas: IMEI · Modelo · En NSYS hoy (producto · grado) · Poner en NSYS (producto · grado) · SKU nuevo · Por qué (a mano:
    quién y motivo; reparación: en la pieza 2) · Desde;
  - **«Exportar a Excel»**, como la exportación de «Pegar varios IMEIs»;
  - todos los móviles con algún dato del taller vigente (de cualquier modelo), los más antiguos primero;
  - sin botones por fila: se vacía sola (§2.2).
- **Producto a poner en NSYS** (texto de Goods):
  - si solo cambia el grado, el Goods actual del móvil;
  - si cambia el color, el Goods **más usado** en `Nsys_movil` entre los móviles del mismo modelo, capacidad, eSIM y color nuevo;
    si ninguno lo tiene, «(ningún móvil de ese color en NSYS: búscalo a mano)».

## 3. Errores y casos límite
- **Sin modelo en el maestro:** el color no se puede corregir (no hay colores del maestro que ofrecer; el selector sale
  deshabilitado con «Sin colores en el maestro»); el grado sí. El SKU sale «sin SKU en el maestro».
- **Varias filas del maestro con SKU distinto** (los «conflictos del maestro»): «sin SKU en el maestro», como hoy con esos
  productos.
- **IMEI en varios lotes:** vale la fila más reciente del IMEI (el mismo criterio que la ficha de hoy).
- **Corrección concurrente** (dos supertécnicos a la vez): la última gana; las dos quedan en el registro y en el rastro.
- **Importación que falla o NSYS sin datos:** no se cierra nada; la lista sigue igual y el aviso «NSYS sin datos» ya existe.
- **Técnico:** no ve el stock de teléfonos ni la ficha de NSYS: nada cambia para él.

## 4. Construcción (orientativa; el plan la fija)

**Base de datos** (migración aditiva, `sql/migracion-sku-taller.sql`, y `crear_bd.sql`):
- Tabla **`Taller_cambio`**: `ID_CAMBIO` (PK), `IMEI_SN`, `DATO` enum(`COLOR`, `GRADO`), `VALOR` (lo del taller),
  `VALOR_NSYS` (lo que NSYS tenía al hacerlo), `ORIGEN` enum(`MANO`, `REPARACION`), `ID_REP` (null; pieza 2), `ID_USU`,
  `MOTIVO`, `FECHA`, `ESTADO` enum(`VIGENTE`, `PASADO`, `MANDA_NSYS`, `QUITADO`, `SUSTITUIDO`), `CERRADO_EN`,
  `ID_CAMBIO_ANTERIOR` (null; el cambio vigente del mismo IMEI y dato al que sustituye: lo usa la pieza 2 para «al deshacer,
  vuelve el anterior»; añadido el 21/10 para no hacer otra migración). Índices por
  (`IMEI_SN`, `DATO`, `ESTADO`) y por `ESTADO`. Como mucho un `VIGENTE` por IMEI y dato (lo garantiza el servidor).
- Sin cambios en las tablas `Nsys_*`.

**Servidor** (paquete `nsys`):
- Una regla pura «lo que vale» (valor NSYS + cambios vigentes → color, grado, batería nueva, SKU y lo de NSYS) y otra para el
  **cierre** (§2.2), con tests propios.
- Búsqueda del SKU y de los colores del maestro por (modelo, capacidad, eSIM[, color, calidad]), y del Goods más usado.
- `StockTelefonosController` (ya limitado a SUPERTECNICO y ADMIN):
  - `GET /` (stock), `GET /{imeiSn}` (ficha), `POST /comprobar` y `GET /buscar` con lo que vale y, cuando el taller cambia
    algo, lo de NSYS al lado (`colorNsys`, `gradoNsys`, `skuNsys`, null si no);
  - `GET /{imeiSn}/rastro` con los eventos del taller;
  - **nuevo** `PUT /{imeiSn}/taller` `{ color, grado, motivo }` (null = no cambia ese dato) y `DELETE /{imeiSn}/taller`
    `{ motivo }` (volver a lo de NSYS), con registro de actividad;
  - **nuevo** `GET /por-pasar` (la lista);
  - **nuevo** `GET /{imeiSn}/colores` (colores del maestro para el diálogo).
- El cierre se lanza al acabar una importación ACTUAL en `OK` (`ImportadorNsys`).

**Web** (`modules/telefonos/stock` y `modules/taller/imeis`):
- Marca «taller» + «NSYS: …» en las celdas (un componente común).
- Pestaña «Por pasar a NSYS» con su exportación.
- Diálogo «Corregir…» en `FichaMovil`.
- Filtros y tabla color × grado con lo que vale (ya lo traen los datos del servidor).

## 5. Pruebas
- **Servidor (JUnit):** lo que vale (sin cambios, con color, con grado, con los dos); SKU (una fila, ninguna, varias con SKU
  distinto, sin modelo); cierre (coincide → pasado; NSYS cambia a otro → manda NSYS; NSYS igual que antes → sigue; dos datos
  independientes); corrección (crea, sustituye, igual a NSYS quita, volver a NSYS, motivo obligatorio, 400 sin cambios, IMEI
  fuera del espejo → 404, rol técnico → 403, registro de actividad); lista (orden, producto a poner, «ningún móvil de ese color»);
  Goods más usado; rastro con los eventos del taller.
- **Web (Vitest):** marca y «NSYS: …» en tabla y ficha; filtros y color × grado con lo que vale; diálogo (colores, grado con
  casillas, vista previa, motivo obligatorio, volver a NSYS); pestaña con contador y exportación.
- **E2E en preprod** (datos sintéticos, nunca en producción): corregir un móvil de prueba → sale en la lista y en el rastro; volver
  a lo de NSYS → sale de la lista.

## 6. Entrega
- Ramas `dev_v0921_sku_taller` en servidor y web, en worktrees.
- Migración aditiva (solo `CREATE TABLE`): compatible con la web anterior; despliegue según
  `feedback_despliegue_en_horario` (sale sola: servidor, web y migración).
- Paridad (nueva sección en `docs/paridad/imeis.md` y en la del stock), CHANGELOG de servidor y web, novedades de la 0.9.21 (`docs/novedades/NOVEDADES-v0.9.21.md`).
- Merge `--no-ff`, push y tags solo con el OK del usuario.

## 7. Fuera de esta pieza
- **Pieza 2:** cambios automáticos desde las reparaciones (con su aviso en el formulario), que escribirán en `Taller_cambio`
  con `ORIGEN = REPARACION` y `ID_REP`.
- Escribir en NSYS desde el ERP (no hay API de escritura).
- La descripción completa del móvil en las tablas del taller (idea aparte del plan).
