# Pedidos: marcas de la fila con nota — diseño

Fecha: 2026-11-13. Estado: aprobado en conversación, pendiente de revisar este texto.
Base: «Pedidos como la hoja» (0.9.32/0.9.33). Sustituye a los textos que el equipo escribía en la celda del IMEI de la hoja.

## Para qué

En la hoja, la celda del IMEI servía también para anotar por qué un móvil no salía: «NO HAY», «ESPERA DE STOCK», «PTE COLOR»,
«PTE RESPUESTA», «CANCELADO»… En el ERP la celda solo admite un IMEI. Las marcas devuelven ese uso, con valores fijos que el
ERP entiende, más una nota libre opcional.

## Las marcas

Una fila de ShippyPro (no del histórico) puede tener **como mucho una marca** de estas cuatro, más una **nota opcional** (hasta
200 caracteres):

| Marca | Cuándo | ¿Cuenta como pendiente? | ¿Salta a ella el lector? | Se quita |
|---|---|---|---|---|
| En espera de stock | No hay ese teléfono en el stock de teléfonos | Sí | No | Sola al poner un IMEI en la fila, o a mano |
| En revisión | Hay algo que aclarar antes de enviarlo | Sí | No | A mano |
| Pendiente de color | Lo que en la hoja era «PTE COLOR» (significado por confirmar con el equipo) | Como sin marca | Como sin marca | A mano |
| Cancelado | El cliente anula ese móvil y el resto del pedido sigue | **No** | No | A mano |

- **Cancelado** existe por la cancelación de UN móvil de un pedido de varios: ShippyPro sigue dando el pedido con las unidades
  originales y no tiene estado de cancelado. Una cancelación del pedido entero no necesita marca: el pedido desaparece de los
  pendientes de ShippyPro y sus filas pasan solas a «Ya no está pendiente».
- **Ninguna fila con marca va al bot ni al plugin:** con cualquiera de las cuatro marcas, aunque tenga IMEI, la fila **no se sirve**
  en `GET /api/pedidos/vendidos`. Es lo que pasaba con la hoja: un texto en la celda del IMEI significaba que no había IMEI, y al
  bot solo iba lo que tenía un IMEI escrito. Al quitar la marca, la fila vuelve a servirse y cuenta como recién puesta (para la
  ventana de días de la consulta vale la fecha más reciente entre el último cambio del IMEI y la retirada de la marca), así el bot
  la recoge aunque el IMEI se pusiera hace días.
- **Pendiente de color** solo enseña su etiqueta y su nota hasta que se sepa qué significaba; no cambia contadores ni el lector.
- La marca «Falta por enviar» (envío parcial) es aparte y no cambia: puede convivir con una de estas.

## Cómo se ve y cómo se pone

- **Dónde se pone:** en el desplegable de la celda del IMEI (donde el equipo escribía estos textos), debajo del campo, con un
  selector de marca, la nota y «Quitar la marca»; y en el panel de la fila. Escribir un IMEI con el lector sigue igual.
- **En la tabla:** la celda del IMEI enseña la marca como etiqueta (con la nota al pasar el ratón); sin IMEI, la marca ocupa el
  sitio de «pulsa para poner el IMEI…». Con «Cancelado», la celda de envío dice «Cancelado» en gris en lugar de «Pendiente»,
  «Etiqueta generada» o «Enviado».
- **Filtros y contadores:** el filtro de «Enviado» gana «Cancelado»; un filtro nuevo, o una opción del filtro del IMEI, «Con
  marca», con una casilla por marca y su recuento. «N pendientes» deja fuera las canceladas.
- **Historial:** «Marca: En espera de stock — <nota>» con quién y cuándo; «Marca quitada»; y «Marca quitada al poner el IMEI» cuando
  la quita el ERP. La ayuda «Cómo se lee esta tabla» explica las cuatro marcas.

## Servidor

- `Pedido_fila`: `MARCA VARCHAR(20) NULL` (`ESPERA_STOCK`, `EN_REVISION`, `PENDIENTE_COLOR`, `CANCELADO`), `MARCA_NOTA VARCHAR(200)
  NULL`, `MARCA_EN DATETIME NULL`, `MARCA_USU INT NULL` (FK a `Usuario`, `ON DELETE SET NULL`). `Pedido_fila_cambio`: `DETALLE
  VARCHAR(250) NULL` para la marca y su nota; tipos nuevos `MARCA` y `MARCA_QUITADA`.
- Migración aditiva y repetible para producción y preprod (`ADD COLUMN IF NOT EXISTS`; la FK con `FOREIGN KEY IF NOT EXISTS`, que es
  la sintaxis que acepta MariaDB) y `crear_bd.sql` al día.
- `PUT /api/pedidos/filas/{id}/marca`, cuerpo `{ marca: <una de las cuatro> | null, nota?: string, marcaAnterior?: string | null }`,
  con `Idempotency-Key`, roles como el IMEI (supertécnico y admin), `marcaAnterior` como `imeiAnterior` (409 si otra persona la
  cambió), 409 en el histórico, 400 con una marca desconocida o una nota de más de 200. Devuelve la `FilaVista` releída.
- Poner un IMEI en una fila con «En espera de stock» la quita en la misma transacción y lo apunta.
- `FilaVista`: `marca`, `marcaNota`, `marcaEn`, `marcaPor` (nombre de usuario); nulos sin marca.
- `GET /api/pedidos/vendidos` excluye las filas con cualquier marca, y su `fechaImei` (y la ventana de días) pasa a ser la fecha más
  reciente entre el último cambio de IMEI y la última retirada de marca.

## Pruebas

Servidor: poner, cambiar y quitar marca; nota larga 400; histórico 409; `marcaAnterior` 409; idempotencia; IMEI que quita «En
espera de stock» y no las demás; vendidos sin ninguna fila marcada y con la fila de vuelta (y dentro de la ventana) al quitar la marca; roles. Web: las cuatro marcas en la celda y en el panel, contadores
y lector según la tabla, «Cancelado» en la celda de envío, filtros, historial y ayuda; prueba en navegador real de poner una marca
desde la celda del IMEI.

## Fuera de esto

Etiquetas desde el ERP (después de hablar con logística), «Por sincronizar», atajo a Asignaciones.
