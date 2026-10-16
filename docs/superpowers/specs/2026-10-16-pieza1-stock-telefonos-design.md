# Pieza 1 del módulo de teléfonos — Stock de teléfonos y rastro (espejo de NSYS)

Fecha: 2026-10-16. Estado: diseño aprobado por partes en la sesión del 16/10; pendiente de que el usuario revise esta
spec y del plan.

Toca **base de datos** (cinco tablas nuevas, migración aditiva), **servidor** y **web**. Parte de servidor `main`
`83be807` y web `main` `4aca632` (0.9.16).

Es la primera de las cuatro piezas del módulo de teléfonos: (1) stock de teléfonos, (2) pedidos y asignar IMEI,
(3) revisión final, etiquetas y envío, (4) venta directa. Cada pieza se entrega sola. El análisis del flujo, las cifras
reales y las decisiones de todo el módulo están fuera del repo, en las notas del usuario. Prototipo aprobado:
[`assets/2026-10-16-pieza1-prototipo-stock-telefonos.html`](assets/2026-10-16-pieza1-prototipo-stock-telefonos.html)
(datos inventados).

## 1. Problema y objetivo

**Hoy:**
- El stock de teléfonos vive en NSYS (el programa de inventario de lotes) y en un Excel de stock. El ERP solo ve el
  taller.
- Un bot exporta de NSYS el fichero **Items** (`Items_AAAA.MM.DD_HH.MM.xlsx`) cada unos 30 minutos y lo deja en una
  carpeta de la unidad compartida de Google Drive. Es un inventario completo desde 2022: una fila por móvil y lote, con
  su caja (`Location`), su estado (`Status`) y su grado, pero solo el estado **actual**.
- Un maestro de productos (Excel, mantenido a mano) traduce «Goods + Grade» de NSYS al SKU de cada canal.
- El ERP pregunta a **IMEIcheck** (API externa con clave) el modelo de un IMEI nuevo, cuando el fichero de NSYS ya lo
  dice para casi todos los reparados.

**Objetivo de la pieza 1:** un **espejo de solo lectura** de NSYS dentro del ERP para:
- ver el stock de teléfonos;
- tener el **rastro** de cada teléfono (por dónde ha pasado y cuándo);
- **retirar IMEIcheck**.

No cambia cómo se trabaja hoy: NSYS, PEDIDOS 2026 y el bot siguen igual. Es una solución temporal hasta que el ERP
lleve el flujo entero.

## 2. Decisiones del usuario (2026-10-16)

- **Fuente:** el ERP lee el fichero Items y el maestro **de Drive**, con una **cuenta de servicio de Google propia del
  ERP** y de solo lectura. Es el mismo patrón que el bot de WhatsApp en la VM de automatizaciones. Descartado: que el
  PC del bot suba el fichero, y pagar a NSYS la exportación por API (se podrá cambiar a ella sin tocar lo demás).
- **Espejo aparte:** tablas propias, sin tocar el modelo de teléfonos de la fase 2 (`Telefono.ESTADO`, `Lote`,
  `Movimiento_telefono`…) ni el taller. Se cruza con el taller por IMEI. **Sin costes ni precios.**
- **Ubicaciones del ERP:** las cajas de NSYS se traducen a ubicaciones propias. Las que no se usan no se traen.
  «Pedidos realizados» no es una ubicación, sino el estado **«de pedido»**: el móvil conserva su última caja conocida.
- **Pantalla «Stock de teléfonos»** (opción C del prototipo):
  - a la izquierda, modelo + capacidad con sus totales;
  - a la derecha, sus móviles con el SKU partido en columnas y **filtros de un clic** con su número.

  Lo que no cuenta como stock va oculto, con un interruptor. **Solo la ven SUPERTECNICO y ADMIN.**
- **Detalle del IMEI:** sigue siendo **la vista de trabajos de hoy**, sin cambios. Encima va una ficha del móvil y un
  botón **«Ver rastro»** que abre la línea de tiempo en un **panel lateral**. El rastro es un adicional, no la vista
  principal.
- **Lista «IMEIs»:** igual que hoy. Su buscador encuentra **cualquier** móvil, aunque no tenga trabajos.
- **Histórico del rastro:**
  - el **esqueleto** (entrada, venta, reentradas) de todos los móviles sale del fichero de hoy;
  - los **pasos intermedios** desde dic. 2023 salen de las exportaciones guardadas en Drive, cargadas una vez, de noche;
  - desde el arranque se registra todo.
- **IMEIcheck:** la ruta del modelo por IMEI responde desde el espejo; se quitan la clave y el freno.

## 3. Qué ve cada rol

| | TECNICO | SUPERTECNICO | ADMIN |
|---|:---:|:---:|:---:|
| Grupo «Teléfonos» → «Stock de teléfonos» | **no** | sí | sí |
| Lista «IMEIs» | como hoy | como hoy | como hoy |
| Buscador de cualquier IMEI en «IMEIs» | no (como hoy) | sí | sí |
| Detalle del IMEI: vista de trabajos | como hoy | como hoy | como hoy |
| Detalle del IMEI: ficha de NSYS y «Ver rastro» | **no** | sí | sí |
| Modelo por IMEI (modal «Asignar trabajos», formulario) | como hoy | como hoy | como hoy |

La ficha y el rastro siguen la misma regla que el stock (datos de NSYS solo para SUPERTECNICO y ADMIN). Está deducido
de la decisión sobre el stock: se confirma en la revisión de esta spec.

En el servidor: `hasAnyRole('SUPERTECNICO', 'ADMIN')`. Ojo: no hay jerarquía de roles, así que `hasRole('SUPERTECNICO')`
dejaría fuera al ADMIN.

## 4. Datos

Migración aditiva `sql/migracion-nsys-espejo.sql` (servidor), con el formato de las demás: cabecera, `USE`, vista
previa sobre `information_schema` y DDL. La aplica el usuario a mano. **Ninguna tabla tiene clave ajena a `Telefono`:**
el espejo se cruza por IMEI y se puede retirar entero sin tocar el taller.

### 4.1 `Nsys_movil` — la foto actual

Una fila por móvil y lote, tal como está en la última exportación.

| Columna | Tipo | Qué es |
|---|---|---|
| `IMEI_SN` | VARCHAR(40) | «IMEI/SN» de NSYS. Puede ser un número de serie, no siempre 15 dígitos |
| `BATCH` | VARCHAR(40) | Lote. **PK (`IMEI_SN`, `BATCH`)**: el par es único (un IMEI se repite si vuelve a entrar) |
| `GOODS`, `GRADE`, `STATUS`, `LOCATION`, `SUPPLIER` | VARCHAR | Tal cual NSYS |
| `RECEIVING_DATE`, `SOLD_DATE` | DATE | Fechas de NSYS (solo el día) |
| `SKU`, `MODELO`, `CAPACIDAD`, `COLOR`, `GRADO_DETALLE`, `ES_ESIM` | | Traducidos con el maestro (§4.4). `MODELO` es el código del ERP (`13pro`…). `NULL` si no está en el maestro |
| `UBICACION` | VARCHAR(30) | Ubicación del ERP traducida (§4.5); `NULL` si la caja no se trae |
| `UBICACION_ULTIMA` | VARCHAR(30) | Última ubicación del ERP antes de pasar a «Pedidos realizados» |
| `ESTADO` | ENUM | `STOCK`, `DE_PEDIDO`, `NO_CUENTA`, `VENDIDO`, `BAJA` (§4.6) |
| `VISTO_PRIMERO`, `VISTO_ULTIMO`, `CAMBIADO` | DATETIME | Horas de exportación |

Índices: `IMEI_SN` (búsqueda), `ESTADO`, (`MODELO`, `CAPACIDAD`).

### 4.2 `Nsys_evento` — el historial para el rastro

Solo se añade, nunca se modifica.

| Columna | Tipo | Qué es |
|---|---|---|
| `ID` | BIGINT AUTO_INCREMENT | PK |
| `IMEI_SN`, `BATCH` | VARCHAR(40) | El móvil |
| `FECHA` | DATETIME | Hora de la exportación en que se vio el cambio |
| `SOLO_DIA` | BOOLEAN | `true` en el esqueleto (fechas de NSYS sin hora) |
| `TIPO` | ENUM | `ENTRADA`, `UBICACION`, `GRADO`, `ESTADO`, `VENTA`, `VENTA_DESHECHA`, `REENTRADA` |
| `DE`, `A` | VARCHAR(80) | Valor anterior y nuevo (en `UBICACION`, la ubicación del ERP; la caja de NSYS va en `A_NSYS`) |
| `A_NSYS` | VARCHAR(80) | Caja de NSYS tal cual, para no perder información |
| `ID_IMPORTACION` | INT | FK a `Nsys_importacion` |

Índice (`IMEI_SN`, `FECHA`).

### 4.3 `Nsys_importacion` — un registro por fichero

`ID`; `FICHERO` (único); `FECHA_EXPORTACION` (la del nombre del fichero); `ORIGEN` (`ACTUAL` | `HISTORICO`);
`EMPEZADA`, `TERMINADA`; `FILAS`; `CAMBIOS`; `RESULTADO` (`OK` | `RECHAZADO` | `ERROR`); `MENSAJE`.

Alimenta el aviso de datos antiguos y deja rastro de cada fichero rechazado y por qué.

### 4.4 `Nsys_producto` — el maestro

- **Columnas:**
  - `CLAVE`: PK, «Product Name Calidad» normalizado (sin espacios y en minúsculas, como el verificador de SKU de las
    automatizaciones).
  - `MODELO_MAESTRO`, `CAPACIDAD`, `COLOR`, `CALIDAD_DETALLE`, `SKU_WEB`, `SKU_AMAZON`, `SKU_SIN_GRADO`, `ES_ESIM`,
    `CARGADO`.
- **Recarga:** se recarga entera cuando cambia el maestro en Drive (§5.6).
- **Claves repetidas:** si una clave sale repetida con SKU distintos, se queda sin SKU y se avisa en el registro, como
  hace el verificador.

### 4.5 `Nsys_ubicacion` — traducción de cajas

`LOCATION` (PK, la caja de NSYS, texto exacto), `UBICACION` (ERP; `NULL` = no se trae), `CUENTA_STOCK` (bool),
`ES_PEDIDO` (bool, «Pedidos realizados»), `ORDEN`.

**Es una tabla, no código:** se puede ajustar sin programar. La migración carga la propuesta del 16/10, pendiente de
que el usuario la confirme (§12):

| Caja de NSYS | Ubicación ERP | Cuenta como stock |
|---|---|:---:|
| (vacía) | En revisión | sí |
| Funcionalidad pendiente | Devolución | sí |
| CAJA PULIR / CAJA GLASS / CAJA REP. OFICINA | Taller · Pulir / Glass / Reparación | sí |
| C.R. Pantalla Rota | Taller · Pantalla rota | sí |
| CAJA LISTOS | Listos | sí |
| AFaltaRepuestos | Esperando pieza | no |
| CF021 | No vendible | no |
| Pedidos realizados | — (`ES_PEDIDO`) | — |
| Desguace, Pendiente revisar si hay error de inventario, CF018, CF029, MV…, Incidencia (pérdida…) | no se traen | no |

Una caja que no está en la tabla se trata como «no se trae» y se avisa en el registro de la importación.

### 4.6 Estado del móvil

1. `Status` = Released → `VENDIDO`.
2. `Status` = Removed (fila sustituida por una reentrada o una corrección), RMA o ReturnedToSupplier → `BAJA`.
3. `Status` ∈ {Available, InProcess, Repair}:
   1. Si la caja es «Pedidos realizados» → `DE_PEDIDO`; la ubicación mostrada es `UBICACION_ULTIMA`.
   2. Si no, si la caja no se trae → `BAJA`.
   3. Si no, si no cuenta como stock → `NO_CUENTA`.
   4. Si no → `STOCK`.

La regla 3 es la del Excel de stock (Status + caja). El Excel además exige SKU en el maestro y por eso no cuenta los
`n/a`; el ERP sí los enseña («En revisión»), porque están físicamente en la empresa. Para comparar con el Excel (§10)
se cuentan solo los `STOCK` con SKU.

## 5. Importación (servidor)

### 5.1 Piezas

| Pieza | Qué hace |
|---|---|
| `ClienteDrive` | Pide el token de la cuenta de servicio (librería de autenticación de Google) y hace dos llamadas REST con `java.net.http`: listar la carpeta y descargar un fichero (`supportsAllDrives`, solo lectura). `HttpClient` inyectable para las pruebas, como `TipoCambioDAO` |
| `LectorItems` | Lee el `.xlsx` **fila a fila** con Apache POI en streaming, sin cargarlo entero. Valida las 19 cabeceras por nombre, tolerando el espacio final que traen algunas (`IMEI/SN `, `Total cost `…). Ignora las columnas de costes y precios |
| `LectorMaestro` | Lee la hoja «Maestro producto NSYS» del maestro |
| `ImportadorNsys` | Compara el fichero con `Nsys_movil` y aplica los cambios y los eventos **en una transacción** |
| `TareaImportacionNsys` | `@Scheduled` cada 5 min (`fixedDelay`). Solo actúa si hay un Items más reciente que el último procesado |
| `TareaHistoricoNsys` | La carga del histórico (§6) |

Dependencias nuevas en `pom.xml`: Apache POI (`poi-ooxml`) y la librería de autenticación de Google
(`google-auth-library-oauth2-http`). No se usa el cliente completo de la API de Drive.

### 5.2 Ajustes (`@Value`, como el resto)

- `nsys.importacion.activa` (por defecto `false`)
- `nsys.drive.carpeta-items` (id de la carpeta)
- `nsys.drive.maestro` (id del fichero del maestro)
- `nsys.drive.credenciales` (ruta del JSON de la cuenta de servicio)
- `nsys.historico.activo` (por defecto `false`)

En producción y preprod van como variables de entorno en el compose, y el JSON como fichero montado en solo lectura.
En los tests todo queda apagado: `OpenApiContractTest` asume que nada consulta al arrancar.

### 5.3 Qué fichero se procesa

- Se lista la carpeta y se toman los `Items_*.xlsx` con fecha en el nombre posterior a la del último `OK` o `RECHAZADO`
  de origen `ACTUAL`.
- **Si hay varios, se procesan en orden**, para no perder pasos del rastro (por ejemplo, si el servidor estuvo parado).
- Si uno falla por error (Drive caído, fichero a medio escribir), se reintenta en la siguiente pasada. Si se
  **rechaza** (formato), se salta y queda registrado.

### 5.4 Reglas de comparación

Por cada fila (`IMEI_SN`, `BATCH`):
- **No estaba** → se inserta y se apunta `ENTRADA`. Si el mismo `IMEI_SN` ya existía con otro lote → además `REENTRADA`.
- **Cambia la caja** → `UBICACION` (de → a en ubicaciones del ERP, más `A_NSYS`). Al pasar a «Pedidos realizados» se
  guarda `UBICACION_ULTIMA`. Si la caja nueva no se trae (Desguace, por ejemplo), el evento lleva `A = NULL` y la
  caja en `A_NSYS`, y el rastro enseña esa caja: el móvil sale del stock, pero su historia no se pierde.
- **Cambia el grado** → `GRADO`.
- **Cambia el Status** → `ESTADO`; si pasa a Released → `VENTA`; si sale de Released → `VENTA_DESHECHA` (pasa en NSYS).
- **Desaparece del fichero** (raro) → se deja como estaba y se avisa en el registro.

Además:
- Con cada cambio se actualizan `CAMBIADO` y `VISTO_ULTIMO`.
- Los campos traducidos se recalculan al cambiar `GOODS` o `GRADE`.

### 5.5 Freno de seguridad

Si el fichero trae **menos del 90 %** de las filas del anterior, se trata como incompleto: `RECHAZADO`, no se aplica y
sale en el aviso.

### 5.6 Maestro

En cada pasada se mira su `modifiedTime` en Drive. Si ha cambiado, se recarga `Nsys_producto` y se recalculan los
campos traducidos de todos los `Nsys_movil`, en la misma transacción que la importación.

### 5.7 Aviso de datos antiguos

Con la `FECHA_EXPORTACION` del último `OK`:
- más de **1 h** → ámbar;
- más de **3 h** → rojo, con la banda «No llegan datos nuevos de NSYS desde las HH:MM…».

Entre las 23:00 y las 7:00 no salta, porque el bot no exporta.

### 5.8 Rendimiento

- Una exportación normal son unas 70.000 filas y unos cientos de cambios: segundos.
- La primera inserta todas las filas por lotes (`batchUpdate`).
- Todo corre en la tarea, fuera de las peticiones web, así que el límite de 15 s de la web no aplica.

## 6. Histórico

1. **Esqueleto.** En la primera importación (sin filas previas) cada móvil recibe:
   - `ENTRADA` con `RECEIVING_DATE`;
   - `VENTA` con `SOLD_DATE` si está Released;
   - `REENTRADA` si su IMEI tiene más lotes.

   Todos con `SOLO_DIA = true`. No hay eventos de cambio, porque no hay nada con qué comparar.
2. **Pasos intermedios** (`TareaHistoricoNsys`, con `nsys.historico.activo`):
   - Recorre en orden las exportaciones de Drive desde la más antigua (dic. 2023) hasta la primera `ACTUAL`, y compara
     cada una con la anterior.
   - **Solo añade eventos** `UBICACION`, `GRADO` y `ESTADO`, con su fecha. Nunca toca `Nsys_movil` ni repite la entrada
     y la venta del esqueleto.
   - Cada fichero queda en `Nsys_importacion` con `ORIGEN = HISTORICO`, así que si se corta sigue donde lo dejó, y al
     terminar no hace nada.
   - **Solo trabaja de 23:00 a 7:00**, un fichero cada vez. Unas 8 horas en total: una o dos noches.

En el rastro, los eventos con `SOLO_DIA` enseñan solo el día, y antes de dic. 2023 sale la nota «Antes de dic. 2023 solo
se conoce la entrada y la venta».

## 7. IMEIcheck

- **`GET /api/telefonos/{imei}/modelo`:**
  - Mantiene la ruta, la respuesta (`ValorTexto`) y los permisos de hoy, así que la web y el contrato no cambian.
  - Ahora responde desde el espejo: el `MODELO` traducido de la fila más reciente de ese IMEI en `Nsys_movil`.
  - Si no está, responde vacío y el modelo se escribe a mano, como cuando IMEIcheck fallaba.
- **Traducción al código del ERP:** se toma la columna «Modelo» del maestro en minúsculas, sin espacios y sin «eSIM».
  Por ejemplo, «13 PRO» → `13pro`, «16 e» → `16e`, «SE2020» → `se2020`, «XS Max» → `xsmax`, «Air» → `air`.
  - Si el resultado no está en la lista de modelos del ERP, se avisa en el registro.
  - Se añaden a la lista los que falten y se vendan (por ejemplo `17e`), en el servidor y en `taller/lib/modelos.ts` de
    la web.
- **Al importar** se rellena `Telefono.MODELO` cuando está vacío, solo para IMEI de 15 dígitos que ya existan en
  `Telefono` (`COALESCE`, sin pisar nunca un valor).
- **Se quitan** `ImeiLookupService`, `FrenoLookup` (solo lo usa esa ruta), la propiedad `imeicheck.api-key` y la
  llamada desde `AsignacionController` (el modelo de los IMEI que van solo a pulido sale del espejo).
- Se ajustan sus tests.

## 8. API (servidor)

Todas son `GET`, de solo lectura, con `hasAnyRole('SUPERTECNICO', 'ADMIN')`:

| Ruta | Devuelve |
|---|---|
| `GET /api/stock-telefonos` | Los móviles en `STOCK`, `DE_PEDIDO` y `NO_CUENTA` con lo que pinta la pantalla. La web filtra en local: unos mil móviles caben en una respuesta |
| `GET /api/stock-telefonos/estado` | Última importación correcta, nivel del aviso (`OK` / `RETRASO` / `SIN_DATOS`) y último rechazo, si lo hay |
| `GET /api/stock-telefonos/buscar?imei=` | Hasta 10 móviles cuyo `IMEI_SN` contiene el texto (mínimo 4 caracteres), de cualquier estado, también vendidos |
| `GET /api/stock-telefonos/{imeiSn}` | La ficha: fila más reciente, número de entradas y grado anterior si cambió |
| `GET /api/stock-telefonos/{imeiSn}/rastro` | Los eventos de NSYS y los trabajos del taller de ese IMEI (asignado y terminado, de `Reparacion` R/G/P), de más reciente a más antiguo, con su fuente (`NSYS` / `ERP`) y si la hora es aproximada |

- Los records van anidados en el controller, como el resto (springdoc los prefija).
- `OpenApiContractTest` añade las rutas nuevas y sus 401/403.
- La web regenera `schema.d.ts`.

## 9. Web

### 9.1 Navegación y rutas

- **`gruposNav`:** un grupo nuevo, `telefonos` («Teléfonos»; hay que ampliar el tipo `IdGrupoNav`), con «Stock de
  teléfonos». Solo con `esAdminOSuperTecnico(sesion)`: el técnico no ve el grupo.
- **Ruta:** `/telefonos/stock`, con una guarda del estilo de `RequiereSupertecnicoOAdmin`.
- El cambio de nombre «Pedidos» → «Compras» del grupo Stock **no entra aquí**: va con la pieza 2, que es cuando aparecen
  los pedidos de clientes.

### 9.2 «Stock de teléfonos»

Como el prototipo aprobado:
- **Cabecera:**
  - título, total de móviles en stock y la píldora de NSYS («exportación de hoy HH:MM · hace N min», en ámbar o rojo
    según §5.7);
  - la banda roja cuando no llegan datos;
  - buscador de IMEI en todos los móviles (sugerencias mientras se escribe; al elegir uno, abre su detalle);
  - el interruptor «Ver también lo que no cuenta como stock».
- **Izquierda:** buscador de modelo y la lista de modelo + capacidad. Cada grupo lleva su total y cuatro números de
  color (listos, taller, revisión y de pedido) más los libres.
- **Derecha:**
  - **Filtros:**
    - chips de **color** (con muestra) y de **grado**, con su número; los que darían cero salen apagados;
    - desplegables con casillas y número para **batería**, **ubicación** y **estado**;
    - «Quitar filtros (n)».
  - **Tabla:** IMEI, color, grado, batería, eSIM, SKU, ubicación, estado («Libre» / «De pedido») y días.
  - **Filas:** los de pedido llevan el borde rojo de los urgentes. Los que no cuentan como stock, en gris con
    «NO CUENTA».
  - **Clic en una fila:** abre el detalle del IMEI (§9.3).
- **Pantalla:** cabe en 1366×768 y en 1920×1080, comprobado en el prototipo.
- **Filtros:** el usuario los quiere **pulir** en la implementación, y el concepto está aprobado.

Con la pieza 1 no hay número de pedido: el estado sale «De pedido» a secas, que es lo que dice NSYS. El número llega con
la pieza 2.

### 9.3 «IMEIs»

- **Lista:** sin cambios. Para SUPERTECNICO y ADMIN, su buscador también consulta `buscar`. Si el IMEI no tiene trabajos
  del taller, sale un aviso con enlace a su detalle.
- **Detalle** (`/reparaciones/imeis/:imei`):
  - La vista de trabajos de hoy, **sin cambios**: filtros, «Id Rep. Anterior», menú de fila, colores de incidencia y
    CSV.
  - Encima, para SUPERTECNICO y ADMIN:
    - la **ficha** del móvil de NSYS (modelo, color, grado y su cambio, SKU, ubicación, estado, lote, días en stock y
      entradas);
    - el botón **«Ver rastro (n)»**, que abre el **panel lateral** con la línea de tiempo. Lleva leyenda de colores, la
      etiqueta NSYS / ERP en cada evento y la nota de que las horas de NSYS son las de la exportación (± 30 min).
  - Un evento de taller del rastro resalta su fila en la tabla de trabajos.
  - El panel mide `min(500px, 42vw)` y deja ver los trabajos detrás.
- **Sin trabajos:** el detalle tiene que funcionar también para un IMEI sin trabajos (hoy se construye solo con
  trabajos). Sale la ficha, la tabla vacía con «Este móvil no ha pasado por el taller» y el rastro.

## 10. Pruebas

- **Servidor (JUnit, con ficheros `.xlsx` pequeños de datos inventados en `src/test/resources`):**
  - **Lectura:** cabeceras (con espacio final y con una columna cambiada → `RECHAZADO`).
  - **Comparación:** cada tipo de evento, la reentrada, «Pedidos realizados» con `UBICACION_ULTIMA` y la venta deshecha.
  - **Freno:** el freno del 90 %; varios ficheros pendientes en orden; un fallo a mitad no deja nada a medias.
  - **Maestro y traducciones:** recarga del maestro y claves repetidas; traducción de cajas (incluida una caja
    desconocida), estado y modelos.
  - **Histórico:** solo añade eventos y se reanuda.
  - **Ruta del modelo:** ahora desde el espejo.
  - **Drive:** con un `HttpClient` falso.
  - **Contrato:** `OpenApiContractTest` actualizado.
  - **Permisos:** el técnico recibe 403 en las rutas nuevas.
- **Web (Vitest):** filtros y totales, interruptor, buscador, aviso de NSYS, panel del rastro, detalle sin trabajos y lo
  que ve cada rol.
- **Preprod (E2E en Chromium):**
  - Recorrido de las pantallas con los datos reales de NSYS, que solo se leen.
  - **Prueba de aceptación:** durante unos días, el total por SKU de los `STOCK` con SKU tiene que coincidir con el
    «Saldo stock» del Excel de stock de la misma exportación. Las diferencias se explican una a una.

## 11. Compatibilidad, despliegue y vuelta atrás

1. **Google** (lo hace el usuario): crear la cuenta de servicio del ERP en el proyecto de Google existente y compartir
   con ella, **como lector**, la carpeta de los Items y el maestro.
2. **Base de datos:** `migracion-nsys-espejo.sql` (aditiva; la web y el servidor anteriores siguen funcionando). Se
   añaden las tablas nuevas a `sql/reset-uat.sql` y al esquema de referencia.
3. **Preprod primero:**
   - poner las variables y el JSON en el compose;
   - encender la importación;
   - dejarla unos días con la comparación de §10;
   - comprobar que el modelo por IMEI sale del espejo.
4. **Producción**, en horario: corte de segundos, migración aditiva y vuelta atrás simple.
   - Mismo compose que en preprod; IMEIcheck se retira en esta misma versión.
   - El histórico se enciende esa noche (`nsys.historico.activo`) y termina en una o dos noches.
5. **Vuelta atrás:** apagar `nsys.importacion.activa` y volver a la imagen anterior. Las tablas nuevas no molestan.
   Volver a IMEIcheck exigiría además la clave, así que se retira solo tras comprobarlo en preprod.

## 12. Pendiente (no bloquea el plan)

- **Ubicaciones definitivas:** el usuario confirma la tabla de §4.5 el lunes 19/10. Solo cambia la carga inicial de
  `Nsys_ubicacion`.
- **Ficha y rastro para el técnico:** confirmar en la revisión que no los ve (§3).
- **Cuenta de servicio:** crearla y compartir la carpeta y el maestro (paso 1 de §11).

## 13. Fuera de alcance

- Pedidos de clientes, asignar IMEI y el verificador de SKU (pieza 2).
- Revisión final, etiquetas y envío (pieza 3).
- Venta directa (pieza 4).
- Renombrar «Pedidos» → «Compras» (pieza 2).
- Costes y precios de NSYS.
- Escribir en NSYS.
- Sustituir el Excel de stock o sus CSV de las webs.
- Roles nuevos.
- Usar el modelo de teléfonos de la fase 2.
