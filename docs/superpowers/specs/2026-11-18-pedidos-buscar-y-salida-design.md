# Pedidos: «Descargar para buscar», fecha de salida e IMEI duplicado — diseño

Fecha: 2026-11-18. Estado: aprobado en conversación. Va en la 0.9.37 (Pedidos), antes del corte.

## Por qué

Lo que le falta a «Pedidos como la hoja» para sustituir del todo el trabajo diario de logística:

- **Cada mañana** logística descarga los pedidos sin enviar (uno de cada cuenta de Amazon y otro de las webs) y hace una
  tabla dinámica: cuántos móviles de cada SKU hay que buscar, por país. Esa tabla la puede dar el ERP.
- ShippyPro dice **hasta cuándo** se puede enviar cada pedido de Amazon («situación de la tienda»), y las webs tienen su
  plazo (24/48 h o 24/72 h laborables). Hoy nadie lo ve en el ERP: hay que distinguir lo que sale hoy, lo que puede esperar
  y lo que va con retraso.
- El mismo IMEI se puede poner en dos pedidos pendientes; solo se avisa en naranja.
- La ayuda de la tabla no cabe en pantallas bajas y no se puede bajar.

## 1. Calendario laboral

Tabla nueva **`Calendario_festivo`** (base para el futuro módulo de recursos humanos: calendario, horarios…):

| Columna | Tipo | |
|---|---|---|
| `FECHA` | `DATE` PK | |
| `NOMBRE` | `VARCHAR(100)` NOT NULL | «Inmaculada Concepción» |
| `AMBITO` | `VARCHAR(20)` NOT NULL | `COMUNIDAD` (de momento el único) |

- La empresa se rige por los **12 festivos de la Comunidad de Madrid** de cada año (decreto anual en el BOCM, en septiembre).
  **No** se cargan festivos locales (el almacén está en Pozuelo de Alarcón, pero se sigue el calendario de Madrid y solo lo
  seguro).
- La migración carga lo que queda de 2026 (7, 8 y 25 de diciembre) y los 12 de 2027 (1 y 6 de enero; 19, 25 y 26 de marzo;
  1 de mayo; 16 de agosto; 12 de octubre; 1 de noviembre; 6, 8 y 25 de diciembre). Los de 2028 se añaden con un `INSERT`
  cuando se publiquen (tarea anual en el plan maestro).
- **Día laborable:** de lunes a viernes y no en `Calendario_festivo`. Servicio `CalendarioLaboral` (lee la tabla al
  arrancar y cada día; `esLaborable(fecha)`, `sumarLaborables(fecha, n)`).

## 2. Fecha de salida de cada fila

Columnas nuevas en `Pedido_fila`: **`FECHA_SALIDA DATE NULL`** (último día para enviar el pedido) y **`PAIS VARCHAR(2) NULL`**
(país de destino, para el Excel de §4).

- **Amazon y Amazon Stockroom:** el campo `status` de `GetPendingOrders` trae «`Unshipped Latest ship: 2026-11-19`»: esa
  fecha. Sin ese texto, `NULL`.
- **Webs (DISTRIBUIDORES, PARTICULARES, BACKIPHONE):** `status` solo dice «processing». El plazo sale del envío que eligió
  el cliente (`shippingservice`): si contiene «24/48», **2 días laborables**; si contiene «24/72» o cualquier otro texto,
  **3 días laborables** (el más largo, para no dar falsas alarmas). `FECHA_SALIDA` = el día de entrada del pedido (la fecha
  de ShippyPro, hora de Madrid) más ese número de días laborables, sin contar el de entrada. Ejemplos: entra el lunes, plazo
  48 h → miércoles; entra el viernes, 48 h → martes; entra el sábado, 48 h → martes.
- La lectura de cada 5 minutos pone `FECHA_SALIDA` y `PAIS` en **todas las filas vivas** de cada pedido pendiente (también
  las ya creadas: así las filas de antes de la migración se rellenan solas en la primera lectura). Las filas cerradas
  conservan lo último que tenían. El histórico no lleva fecha de salida.
- `FilaVista` gana `fechaSalida` (ISO, nullable) y `pais` (nullable).

## 3. La columna «Enviado»

Sin columna nueva. En las filas **pendientes** y en las de **«Falta por enviar»** (envío parcial de web), la etiqueta de la
columna «Enviado» pasa a depender de `fechaSalida` y de hoy (hora de Madrid):

| Caso | Texto | Color |
|---|---|---|
| `fechaSalida` < hoy | **Con retraso** | rojo intenso |
| `fechaSalida` = hoy | **Sale hoy** | rojo |
| `fechaSalida` = mañana | Sale mañana | gris |
| más tarde | Sale vie 20/11 | gris |
| sin `fechaSalida` | Pendiente (como hoy) / Falta por enviar | como hoy |

- En «Falta por enviar» el plazo se suma al texto cuando es hoy o ya pasó: «Envío parcial · falta por enviar · sale hoy» (color de
  «Sale hoy») y «… · con retraso» (color de «Con retraso»); con fecha posterior, sin sufijo. Cuentan en «N salen hoy»/«M con
  retraso» y en sus filtros (revisión final, 18/11). No hay «vencida»: lo que ya no se
  envía tiene la marca «Cancelado», que manda sobre todo (como hoy).
- Las enviadas, como hoy (pierden el aviso).
- El filtro de la columna gana **«Sale hoy»** y **«Con retraso»**.
- Arriba, junto a los contadores: **«N salen hoy»** y, si hay, **«M con retraso»** (sobre lo recibido, como los demás).
- La ayuda explica las nuevas etiquetas.

## 4. «Descargar para buscar»

Botón en la cabecera de Pedidos con tres opciones; cada una baja **un fichero** `.xlsx`:

- **AMZ** (canal AMAZON), **FNSK** (AMAZON_STOCKROOM) y **Webs** (DISTRIBUIDORES, PARTICULARES y BACKIPHONE juntos).
- Nombre: «Buscar AMZ 18-11.xlsx» (plataforma y día de Madrid).

**Qué filas cuenta:** filas de ShippyPro del canal, **sin IMEI**, **sin la marca «Cancelado»**, que estén **pendientes** o en
**«Falta por enviar»**. Nada más (ni enviadas, ni «ya no pendiente», ni histórico).

**Formato** (el de la tabla dinámica que usa logística), en una sola hoja, dos tablas una debajo de otra con una fila en
blanco entre medias:

1. **«Para hoy»**: filas con `fechaSalida` ≤ hoy o sin fecha.
2. **«Pueden esperar»**: filas con `fechaSalida` > hoy.

Cada tabla: título; cabecera `SKU | <países> | Total general`; una fila por SKU (orden alfabético, sin distinguir mayúsculas:
`i13128AzuLA` e `i13128AzulA` son el mismo y suman en una fila, que se escribe como la forma más repetida); una columna por
país presente en esa tabla (orden alfabético; sin país, columna «—»); celdas vacías donde es 0; última fila «Total general».
Si una tabla no tiene filas, su título y «No hay móviles que buscar». Preparada para imprimir: A4 al ancho de una página, en
vertical. Sin cabecera repetida en cada página (con dos tablas en la hoja confundiría; revisión final, 18/11).

**El de las webs es distinto (Imad, 18/11 noche, tras verlo en preprod):** en las webs se envía todo en el día, así que **no se
divide** en «Para hoy»/«Pueden esperar» y lo que importa es el cliente. Una sola hoja con **un bloque por web**, en este orden:
«Fonestockroom (distribuidores)» (DISTRIBUIDORES), «Fonestore (particulares)» (PARTICULARES) y «BackiPhone» (BACKIPHONE). Cada
bloque: título; cabecera `Cliente | SKU | Uds | Entró`; una fila por cliente, SKU (sin distinguir mayúsculas, escrito como la forma
más repetida) y día de entrada, con las unidades; «Entró» = la columna Fecha de la fila (día en que el ERP vio el pedido), como
`dd/mm/aaaa`; orden por «Entró» (lo más antiguo primero) y después por cliente y SKU; última fila «Total» con las unidades. Sin
número de pedido, sin país y sin envío. Un bloque sin filas: su título y «No hay móviles que buscar». Misma impresión (A4, ancho de
una página, vertical). La columna «Enviado» de la tabla NO cambia: en las webs sigue con su plazo de 48/72 h y «Con retraso».

Servidor: `GET /api/pedidos/buscar?plataforma=AMZ|FNSK|WEBS` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
(otra plataforma, `400`). Roles como el resto de Pedidos (SUPERTECNICO y ADMIN). La consulta es una sola y acotada (filas
vivas o con la marca de falta), dentro del límite de 15 s.

## 5. IMEI duplicado

`PUT /api/pedidos/filas/{id}/imei`: si el IMEI ya está en **otra** fila de ShippyPro **pendiente** o en **«Falta por
enviar»**, no se pone: `409` con «Este IMEI ya está en el pedido X (fila del dd/mm)» (en Amazon sin asociar: «… en una fila
de AMZ del dd/mm sin asociar»). Contra filas enviadas, «ya no pendiente» o del histórico **sí** se deja (un móvil devuelto se
vuelve a vender), y tampoco bloquea una fila pendiente con la marca «Cancelado» (ese móvil no saldrá en ese pedido; revisión
final, 18/11). Dos peticiones simultáneas con el mismo IMEI pueden pasar las dos (la comprobación no bloquea la otra fila): se
acepta, el aviso naranja «en otra fila» lo enseña después. La comprobación va en la transacción del DAO, con la fila bloqueada. El aviso naranja «en otra fila» se
queda para los casos que ya existan. No cambia la asociación desde los libros ni el plugin.

## 6. IMEI que no está en NSYS

Sin cambios: se pone y sale en naranja.

## 7. La ayuda

El desplegable «Cómo se lee esta tabla» lleva una altura máxima (lo que cabe en la ventana) con su barra de scroll.

## Migración

`sql/migracion-pedidos-salida.sql`, aditiva y repetible (`IF NOT EXISTS`, `INSERT IGNORE`): la tabla `Calendario_festivo` con
sus fechas y las dos columnas de `Pedido_fila`. `crear_bd.sql` y `migracion-pedidos-tabla.sql` al día. Va en el bloque C1 de
la guía de producción con las otras dos migraciones de Pedidos.

## Pruebas

- Servidor: cálculo de días laborables con fines de semana y festivos (lunes, viernes, sábado, víspera de festivo); lectura
  del `status` de Amazon (con y sin fecha) y del plazo de las webs («24/48», «24/72», otro); la lectura rellena y actualiza
  `FECHA_SALIDA` y `PAIS` en las vivas y no toca las cerradas; Excel (qué filas entran y cuáles no, SKU en distinto caso,
  países, las dos tablas, tabla vacía, totales, nombre); `409` del IMEI duplicado contra pendiente y contra falta por enviar,
  y que contra enviada e histórico sí se pone; roles.
- Web: textos y colores de «Enviado» según la fecha (hoy, mañana, más tarde, pasado, sin fecha, falta por enviar), filtros,
  contadores, botón de descarga con sus tres opciones, mensaje del `409`, scroll de la ayuda.
- Preprod, fuera de horario: migración, lectura real (las fechas de salida cuadran con «Latest ship» de ShippyPro) y una
  prueba en Chromium con datos sintéticos (prefijo 0099) que se limpian al acabar.
