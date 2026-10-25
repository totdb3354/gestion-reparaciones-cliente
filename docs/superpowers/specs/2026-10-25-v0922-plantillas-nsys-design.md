# 0.9.22 — Lo que no cuadra con NSYS: cuatro listas y plantillas para subir

Fecha: 2026-10-25. Estado: **diseño aprobado** (brainstorming del 23-25/10 con el usuario); plan pendiente (otra sesión).
Prototipo aprobado: `assets/2026-10-25-v0922-prototipo-plantillas-nsys.html` (diseño C). Plantilla de NSYS vacía:
`assets/2026-10-25-v0922-plantilla-nsys-vacia.xlsx` («Upload batch with IMEI», tal cual la da NSYS).
Se apoya en la 0.9.21 (SKU del taller, «Por pasar a NSYS», corrección en NSYS, `Taller_cambio` y `Taller_aviso`).

## 1. Qué cambia

**Hoy** el ERP sabe qué móviles no cuadran con NSYS, pero:
- «Por pasar a NSYS» mezcla color y grado en una fila, y en NSYS se corrigen distinto: el color se cambia **borrando el móvil y
  volviéndolo a subir** con la plantilla de NSYS; el grado se cambia **aparte, uno a uno**.
- Los móviles que han pasado por el taller y **no están en NSYS** solo se ven si alguien los busca («Comprobar IMEIs»).
- Los avisos del taller (color sin deducir, grado sin calcular, no se pudo deshacer) solo salen en el rastro de cada IMEI.
- Antes se apuntaba todo a mano en la hoja «Flujo trabajo» del simulador (179 filas: 64 «COLOR», 7 «NO ESTÁ EN NSYS», 106 sin
  motivo, casi todas color o grado) y la plantilla de NSYS se rellenaba a mano.

**Ahora**, en dos piezas:
- **Pieza 1 (lo primero, solo servidor y base):** el ERP **lee mejor el maestro y NSYS** para saber siempre cómo escribe NSYS
  cada color, y guarda el coste de cada móvil al importar. El Excel del maestro **no se toca**.
- **Pieza 2 (servidor, web y base):** la pestaña «Por pasar a NSYS» pasa a tener **cuatro tarjetas** —Cambio de color, Cambio
  de grado, No está en NSYS y Revisar a mano— con un botón **«Hecho»** por fila y **dos plantillas** listas para subir a NSYS.

**Decisiones del usuario (23-25/10):**

| Tema | Decisión |
|---|---|
| Diseño | **C**: cuatro tarjetas con su número y su botón; debajo, la lista de la elegida. |
| Color y grado | **Cada uno por su flujo**: un móvil que cambia de color y de grado sale en las dos tarjetas. La plantilla de color lleva el grado que tiene hoy NSYS. Orden: primero el color, luego el grado. |
| En «Cambio de color» | Destacar exactamente lo que cambia (el color) y avisar en gris si el móvil también está en «Cambio de grado». |
| Grado en NSYS | Uno a uno: el ERP solo da la lista («Copiar lista»), sin plantilla. |
| «Hecho» | Marca visual por fila (y «Marcar todo»): la fila se queda en gris; se va sola con la importación si NSYS ya lo tiene, y vuelve a salir si se marcó por error. Las plantillas no llevan lo marcado. |
| «No está en NSYS» | Todos los IMEIs con algún trabajo del taller (cualquier fecha) que NSYS no conoce. Sin botón para quitarlos: todo móvil tiene que acabar en NSYS. |
| «Revisar a mano» | Se va solo al corregir el móvil en su ficha, o con «Revisado» (si ya estaba bien). |
| Plantillas | Lote **«COLOR»** en la de color y **«AJUSTE»** en la de altas; precio de compra = el mismo «Total cost» que tiene el móvil en NSYS. |
| Maestro | Solo se lee mejor; nunca se edita. |

## 2. Pieza 1 — Leer mejor el maestro y NSYS

### 2.1 El nombre del color tal como lo escribe NSYS
Para la plantilla de color (y para «Poner en NSYS») hace falta el producto escrito **exactamente** como en NSYS: NSYS crea un
producto nuevo si cambia una mayúscula (ya tiene 27 productos duplicados solo por grafía, p. ej. «Apple iPhone 15 128GB Blue» y
«Apple iphone 15 128GB blue»).

- **El maestro dice qué color es.** El color del ERP («Verde») y el modelo y la capacidad del móvil dan su fila del maestro, y
  la columna **«Product Name»** (hoy no se lee) da el nombre en inglés de ese color en ese modelo: lo que va detrás de la
  capacidad («Alpine Green»). La columna «Color en ingles» **no sirve**: es la familia («Orange» para «Cosmic Orange»).
- **NSYS dice cómo se escribe.** Con ese nombre (comparado sin mayúsculas ni espacios), se busca la grafía en NSYS, por orden:
  1. el Goods más usado de ese color entre los móviles del mismo modelo, capacidad y eSIM (lo de hoy, `goodsMasUsados`);
  2. si no hay, ese color en el **mismo modelo con otra capacidad**;
  3. si no hay, ese color en **cualquier modelo**;
  4. si NSYS nunca lo ha escrito, el del maestro, **marcado «compruébalo»** (celda amarilla en la plantilla).
  Si NSYS lo ha escrito de varias formas, vale la más usada.
- **Modelo y capacidad**: del Goods del propio móvil en NSYS (lo que va hasta la capacidad, «Apple iPhone 14 eSIM 256GB»).
- **Medido el 25/10** (maestro de Drive e Items del 22/10, 71.436 filas de iPhone): de 863 combinaciones modelo-capacidad-color
  del maestro, 625 se resuelven en el paso 1, 129 en el 2, 109 en el 3 y **0** necesitan el 4. En producción, 170 de 796
  combinaciones no tienen hoy ningún móvil del mismo modelo, capacidad y eSIM: sin esta pieza caerían en «búscalo a mano».
- La misma regla da `ponerProducto` en «Por pasar a NSYS», la ficha del IMEI y el diálogo «Corregir…» (`OpcionColor`). En la
  pieza 1 (sin cambios en la web) el paso 4 no se usa para enseñar el producto: sin grafía de NSYS sigue saliendo «búscalo a
  mano»; el nombre del maestro con su «compruébalo» llega con la pieza 2.

### 2.2 El color del chasis
`ColorChasis` deduce el color de un chasis comparando su SKU con los nombres en inglés de los colores del maestro, que hoy salen
solo del Goods más usado de cada color en NSYS (un color sin móviles no puede encajar). Desde esta pieza, un color sin móviles
usa el nombre del «Product Name» del maestro (aquí las mayúsculas no importan: se normaliza). Bajan los «color sin deducir».

### 2.3 El coste de cada móvil
El importador de los ficheros Items lee también **«Total cost»** y lo guarda en el espejo (por lote, como el resto). Solo se usa
en la plantilla de color; **no sale en ninguna pantalla ni en la API de listas** (solo supertécnico y administrador descargan la
plantilla).

### 2.4 Base de datos y recarga
- Migración **aditiva**: `Nsys_producto.NOMBRE` (el «Product Name», `VARCHAR(150) NULL`) y `Nsys_movil.COSTE`
  (`DECIMAL(10,2) NULL`). Comprobación final al estilo de `migracion-sku-taller.sql` (antes/después).
- El maestro se recarga solo cuando cambia en Drive: tras desplegar, se recarga una vez por arranque aunque no haya cambiado si
  ninguna fila tiene `NOMBRE` (el maestro se cargó antes de la columna; para no esperar al próximo cambio). Un maestro con
  filas sin nombre de verdad no se recarga en cada arranque. `COSTE` se rellena con la siguiente importación de Items (los lotes
  que ya no vienen en el fichero se quedan sin coste; no hace falta histórico).
- La web no cambia en esta pieza. Puede ir sola a producción en horario (migración aditiva, servidor con corte ~5 s).

## 3. Pieza 2 — Cuatro tarjetas en «Por pasar a NSYS»

### 3.1 Dónde y quién
- **Stock de teléfonos**, pestaña «Por pasar a NSYS (N)» (la de hoy), solo supertécnico y administrador (el técnico no ve
  «Teléfonos»). N = filas pendientes de las cuatro tarjetas (las marcadas «Hecho» no cuentan).
- Arriba, **cuatro tarjetas** con su color de borde, su número («3 móviles · 1 hecho»), una línea de qué se hace y su botón:

| Tarjeta | Qué hay | Botón |
|---|---|---|
| **Cambio de color** (morado) | Móviles con un cambio de **color** del taller vigente (`Taller_cambio` COLOR VIGENTE). | **⬇ Descargar plantilla (n)** |
| **Cambio de grado** (azul) | Móviles con un cambio de **grado** vigente (también los que cambian de color). | **Copiar lista (n)** |
| **No está en NSYS** (rojo) | IMEIs con algún trabajo del taller que NSYS no conoce. | **⬇ Descargar plantilla (n)** |
| **Revisar a mano** (ámbar) | Avisos del taller sin resolver: color sin deducir, grado sin calcular, no se pudo deshacer. | Ver |

- Debajo, una línea de ayuda y la lista de la tarjeta elegida (la de color al entrar). Los botones se deshabilitan sin filas
  pendientes (texto al pasar el ratón: «No hay móviles que corregir»). Los IMEIs abren su detalle, como hoy.
- Orden de cada lista: de lo más antiguo a lo más reciente.

### 3.2 Cambio de color
- Columnas: IMEI · Modelo · **Color** (destacado en morado, «Blue → (PRODUCT)RED»; debajo, en gris, «también en «Cambio de
  grado» (después)» si toca) · En NSYS hoy · Poner en NSYS (el producto con el color resaltado y «grado B (el de NSYS hoy)») ·
  Por qué · Desde · Hecho.
- Un producto sacado del maestro (paso 4 de §2.1) lleva «compruébalo». Si aun así no hay nombre, la fila dice «búscalo a mano»
  y no va en la plantilla (aviso con el número al descargar).
- Ayuda: «En NSYS: bórralos y súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si
  lo hay, va por «Cambio de grado», después). Cada fila se va sola cuando la importación ve el color nuevo en NSYS.»

### 3.3 Cambio de grado
- Columnas: IMEI · Modelo · **Grado** (destacado en azul, «B → A100»; «primero su cambio de color» si el móvil también está en
  Color) · Producto en NSYS hoy · Por qué · Desde · Hecho.
- **«Copiar lista»** copia al portapapeles (para Excel) IMEI, producto, grado en NSYS y grado nuevo de las filas pendientes.
- Ayuda: avisa de que, si el móvil cambia de color, hay que hacer primero el color (la plantilla de color le devolvería el grado
  de antes); se va sola cuando la importación ve el grado nuevo.

### 3.4 No está en NSYS
- Qué entra: todo IMEI de `Reparacion` (cualquier categoría y fecha) que no esté en el espejo de NSYS en ningún lote (cruce por
  `claveCruce`, como el resto). Medido el 23/10 en producción: 11 en todo el historial, ninguno en el taller ahora, casi todos
  «sin cliente» o AMAZON (móviles propios). Se va sola cuando la importación lo ve en NSYS; no hay botón para quitarla.
- Columnas: IMEI · Modelo (el del taller) · Último trabajo (id y tipo; «asignado» si está abierto) · Técnico · Cliente ·
  Trabajos · Desde (primer trabajo) · Hecho.

### 3.5 Revisar a mano
- Qué entra: los `Taller_aviso` **SIN_COLOR, SIN_GRADO y SIN_DESHACER** sin resolver (los DESHECHO son informativos y siguen
  solo en el rastro).
- Columnas: IMEI · Modelo · **Qué pasa** (etiqueta del tipo y texto como en el rastro) · **Qué hacer** («Mira de qué color quedó
  y corrígelo en su ficha», «Decide el grado que le toca y corrígelo en su ficha», «Mira si el móvil tiene de verdad ese color;
  si no, corrígelo en su ficha») · Reparación (quién y cuándo) · «Revisado» y «Corregir…» (abre la ficha).
- **Sale sola** cuando alguien corrige ese móvil en su ficha después del aviso (un `Taller_cambio` de origen MANO del mismo IMEI
  y dato, también «Volver a lo de NSYS»). Si NSYS tiene que cambiar, el móvil aparece después en Color o en Grado.
- **«Revisado»**: para cuando el móvil ya estaba bien. Guarda quién y cuándo en el aviso (columnas nuevas en `Taller_aviso`,
  aditivas), queda en el registro de actividad y el rastro lo dice («Revisado por …»).

### 3.6 «Hecho»
- Cada fila de Color, Grado y No está en NSYS tiene **«Hecho»**; la cabecera, **«✓ Marcar todo (n)»**. Una fila marcada se
  queda **en gris** con «✓ Hecho 12:31» (quién, al pasar el ratón) y «deshacer». Va por tarjeta: marcar el color de un móvil no
  marca su grado.
- Las plantillas y «Copiar lista» **solo llevan las filas sin marcar** (así no se sube dos veces lo mismo).
- Tras cada importación de NSYS que acaba bien, para cada marca **anterior a la hora de exportación del fichero**
  (`FECHA_EXPORTACION`, no la hora de importar: un fichero de antes de marcar no dice nada):
  - si la fila ya no está pendiente (NSYS lo tiene), la marca se borra con la fila;
  - si sigue pendiente, la marca deja de valer y la fila vuelve a salir con «Marcado «Hecho» a las 12:32, pero NSYS sigue
    igual» hasta que se vuelva a marcar o se arregle.
- Base: tabla nueva (p. ej. `Taller_hecho`: IMEI, tarjeta COLOR/GRADO/ALTA, usuario, fecha, «volvió» con su fecha), una marca
  por IMEI y tarjeta. «Hecho» no va al registro de actividad (vive en su tabla con el usuario).

## 4. Las plantillas

- Se generan en el **servidor** (Apache POI, ya en el `pom`) a partir de la plantilla vacía de NSYS guardada como recurso: misma
  hoja («Лист1») y **filas 1 a 6 tal cual**; los datos, desde la **fila 7**. Nombre: `NSYS_color_AAAA-MM-DD.xlsx` y
  `NSYS_altas_AAAA-MM-DD.xlsx`. Al descargar, un aviso con el número de móviles y lo que haya que revisar.
- Formato comprobado con la subida del 22/10: «128 GB» con espacio en Storage → NSYS «128GB» y el maestro lo reconoce; el lote
  propio («Ajuste») es solo una etiqueta (NSYS les pone su BT-xxxx); estado 0 → «InProcess»; el lote viejo queda «Removed».

| Columna | Plantilla de color | Plantilla de altas |
|---|---|---|
| A · IMEI/SN | el IMEI | el IMEI |
| B · Manufacturer | Apple | Apple |
| C · Model | del Goods del móvil en NSYS («iPhone 14 eSIM») | el modelo del taller («iPhone 14»), **en rojo**: comprobar eSIM |
| D · Storage | del móvil en NSYS, con espacio («256 GB») | vacío **en rojo** |
| E · Color | el color nuevo con la grafía de NSYS (§2.1); amarillo si sale del maestro | vacío **en rojo** |
| F · Grade | **el que tiene hoy en NSYS** | vacío **en rojo** |
| G · Purchase price | **el mismo «Total cost»** del lote actual, con sus decimales | vacío **en rojo** |
| H · Supplier Name | el del lote actual, tal cual (si pone «Hy5» se corrige en el Excel) | vacío **en rojo** |
| I · Batch Number | **COLOR** | **AJUSTE** |
| J · Group of Goods | iPhone | iPhone |
| K-M · EAN, UPC, Description | vacío | vacío |
| N · Status | 0 | 0 |

## 5. Errores y casos límite
- **Grado antes que color**: si alguien cambia el grado en NSYS y después sube la plantilla de color (con el grado de antes),
  el grado vuelve atrás y la fila de grado vuelve a salir. La ayuda pide el orden color → grado.
- **Precio con decimales** y **doble subida**: ver §9 (por mirar en NSYS). «Hecho» ya evita subir dos veces desde el ERP.
- **IMEI en NSYS con el IMEI2 o el número de serie**: sale en «No está en NSYS» hasta que NSYS lo tenga con ese IMEI.
- **Rendimiento**: el límite de 15 s por petición no se sube. Las listas son cortas; el cruce de «No está en NSYS» (todos los
  IMEIs del taller contra el espejo) se mide en preprod.
- **Lectura del maestro**: si el maestro de Drive no trae «Product Name», se carga como hoy (sin nombre) y se avisa en el log.

## 6. Pruebas
- **Servidor (JUnit)**: la grafía del color (pasos 1-4 y la más usada), el prefijo del propio móvil, `ColorChasis` con un color
  sin móviles, la lectura de «Product Name» y «Total cost», la migración (antes/después), las cuatro listas (color y grado por
  separado; no está en NSYS; avisos sin resolver), «Hecho» con la hora de exportación (se va, vuelve, fichero viejo no cuenta),
  «Revisado», y las plantillas (filas 1-6 idénticas a la vacía, datos desde la 7, columnas de la tabla del §4, sin las marcadas).
  Rutas solo para supertécnico y administrador; `OpenApiContractTest`.
- **Web (Vitest)**: tarjetas, números y botones deshabilitados, lo destacado en Color y Grado, «Hecho», «Marcar todo»,
  «deshacer», «Copiar lista», «Revisado».
- **Preprod (E2E)**: con el móvil sintético de la 0.9.21, un cambio de color y uno de grado, descarga de la plantilla, «Hecho» y la
  importación simulada; medida del cruce de «No está en NSYS».

## 7. Entrega
- **Pieza 1** primero (servidor + migración aditiva), sola a producción en horario. **Pieza 2** después (servidor + web +
  migración aditiva). Ramas propias desde `main`, merge `--no-ff`; push y tags con el OK del usuario.
- CHANGELOG, novedades y paridad de la pantalla.

## 8. Fuera
- Borrar los móviles en NSYS (lo hace el usuario en NSYS).
- Cambiar grados en bloque en NSYS (no hay forma; se hace uno a uno).
- Avisar de un IMEI repetido en NSYS tras una doble subida (posible control futuro desde la importación).
- Detectar capacidad o SIM/eSIM mal en NSYS (el taller no lo apunta: no hay con qué comparar).
- Quitar a mano un móvil de «No está en NSYS».

## 9. Por mirar en NSYS (con el usuario, juntas)
Se miran a la vez en NSYS, antes o durante el plan; ninguna cambia el diseño, solo detalles de la plantilla o de la ayuda:
1. **Cómo se borran** los móviles antes de volver a subirlos: uno a uno o hay forma en bloque (si la hay, la ayuda de «Cambio de
   color» lo dice; el ERP sigue sin borrar nada).
2. **Decimales en «Purchase price»**: si NSYS los acepta, el «Total cost» va tal cual (535,41); si no, sin decimales.
3. **IMEI repetido**: si NSYS avisa al subir un IMEI que ya tiene en un lote activo (si no avisa, entra en el backlog el control
   desde la importación del §8).
