# Pedidos: asociación de IMEI y pedido en Amazon («plan B») — diseño de la parte del ERP

Fecha: 2026-11-13. Estado: aprobado en conversación (la parte del ERP); la conexión con los libros de ventas de Amazon, pendiente.
Base: «Pedidos como la hoja» (0.9.34 en producción, fase A).

## Por qué

En Amazon, qué móvil va en qué pedido lo decide una persona en los libros de ventas de Amazon (uno por plataforma: el de Amazon y
el de Amazon Stockroom). Ese criterio no se puede replicar todavía, así que el ERP **no asocia de primeras** un IMEI de Amazon a un
número de pedido: la asociación solo vale cuando sale en esos libros. Hasta ahora lo hacía, con la hoja de pedidos, un programa que
copiaba el número de pedido del libro a la hoja. Esto sustituye a la idea de que el ERP rotara los IMEI con un criterio propio.

Las webs no cambian: allí el pedido va asociado desde el principio.

## Qué ve el equipo

- Las filas de **Amazon y Amazon Stockroom** salen **sin número de pedido** («sin asociar») hasta que se confirma la asociación.
  El ERP sigue sabiendo internamente de qué pedido de ShippyPro es cada fila (el «hueco»), porque el estado del envío sale de ahí,
  pero no lo enseña.
- El IMEI se lee con el lector como hoy, en cualquier fila del SKU: queda puesto en la fila, sin pedido.
- Cuando se confirma, la fila enseña su número de pedido. Las filas de un mismo pedido salen juntas y con su «1/2, 2/2» (en Amazon,
  solo las confirmadas).
- Panel de la fila: «Pedido: sin asociar» o «Pedido B · asociado desde el libro de ventas el dd/mm hh:mm».
- Filtro «Sin asociar» (filas de Amazon sin confirmar) y, junto a la píldora de fuentes, el estado de la última asociación: cuándo
  fue y cuántas quedaron «para revisar», con su lista.

## Qué hace el ERP con cada asociación «IMEI X → pedido B» (de una plataforma de Amazon)

Se aplica sobre las filas de ShippyPro de ese canal (`AMAZON` o `AMAZON_STOCKROOM`); el número de pedido se normaliza como en la
lectura de ShippyPro. Nunca se toca una fila con la etiqueta ya generada (`ENVIO_FECHA` no nula) ni una con la marca «Cancelado».

1. **X ya está en una fila de B:** se marca confirmada.
2. **X está en otra fila (de otro pedido) sin etiqueta:** se **intercambian los IMEI** entre esa fila y una fila de B sin confirmar
   (la primera por posición): X queda en el hueco de B y se marca confirmada; el IMEI que tenía la fila de B (o ninguno) pasa a la
   otra, que queda sin confirmar. Si esa otra fila estaba confirmada con X para otro pedido, deja de estarlo: manda lo último que
   diga el libro.
3. **X no está en ninguna fila:** se pone en una fila libre (sin IMEI, sin confirmar) de B y se marca confirmada. Si X no está en
   NSYS, la celda sale en naranja con «no está en NSYS», como cualquier IMEI.
4. **B no tiene ninguna fila libre o sin confirmar**, X está en una fila con etiqueta, o B no está entre los pedidos del ERP: no se
   toca nada y queda **para revisar**, con su motivo («el pedido no tiene hueco», «el móvil ya salió en otro pedido», «el pedido no
   está en el ERP»).

Una asociación ya aplicada no hace nada la segunda vez. Todo cambio queda en el historial de las filas: «Pedido asociado (libro de
ventas)», «IMEI intercambiado con la fila n», y los cambios de IMEI como los de siempre.

## Servidor

- `Pedido_fila`: `ASOCIADA_EN DATETIME NULL` (cuándo se confirmó; NULL = sin asociar en Amazon) y `ASOCIADA_ORIGEN VARCHAR(20)
  NULL` (`LIBRO` o `HISTORICO`). Tabla `Pedido_asociacion_revisar` (canal, pedido, IMEI, motivo, cuándo), que se rehace en cada
  pasada de cada canal. Historial: tipos nuevos `ASOCIADA` y `IMEI_INTERCAMBIADO` (con `DETALLE`). Migración aditiva y repetible
  (FK con `FOREIGN KEY IF NOT EXISTS`).
- Servicio `AsociacionAmazon.aplicar(canal, pares)`: las reglas de arriba en una transacción por pasada (`READ_COMMITTED`, filas
  `FOR UPDATE`); devuelve recuentos (confirmadas, intercambiadas, puestas, ya estaban, para revisar).
- Ruta de máquina `POST /api/pedidos/asociaciones` con su propia clave (`PEDIDOS_ASOCIACIONES_CLAVE`, mismo patrón que
  `/api/pedidos/sincronizados`): cuerpo `{canal, pares: [{pedido, imei}]}`, con tope. La usará quien lea los libros (el programa
  que hoy escribe en la hoja, o un lector del propio ERP; por decidir).
- `FilaVista` gana `asociadaEn` y `asociadaOrigen`; para Amazon, `pedido` sigue viniendo (lo necesita el estado), y la web decide
  qué enseñar.
- Carga del histórico: los traspasos por número de pedido de Amazon (regla A) entran como asociados (`HISTORICO`); los demás de
  Amazon, sin asociar.
- `GET /api/pedidos/vendidos`: para Amazon sin asociar, `pedido` va nulo (el IMEI se sigue sirviendo). Comprobar con quien
  mantiene el bot que no lo necesita.
- `GET /api/pedidos/fuentes` (o similar): fecha de la última asociación por canal y cuántas quedaron para revisar; ruta para la
  lista «para revisar».

## Web

- Columna «Pedido»: en Amazon, el número solo si `asociadaEn`; si no, «sin asociar» en gris. Buscador: el número de una fila sin
  asociar no se encuentra (no está a la vista).
- Panel: estado de la asociación y origen; historial con los tipos nuevos.
- «1/n»: en Amazon, solo entre filas asociadas.
- Filtro «Sin asociar»; píldora con la última asociación y la lista «para revisar».
- Ayuda «Cómo se lee esta tabla»: una línea sobre «sin asociar».

## Pruebas

Servidor: las cuatro reglas (incluido el intercambio cuando la otra fila estaba confirmada para otro pedido), filas con etiqueta y
«Cancelado» intocables, idempotencia, normalización del número, pedido ajeno, varias asociaciones del mismo pedido (2 de 3), ruta y
claves, carga del histórico, vendidos con `pedido` nulo. Web: columna, panel, «1/n», filtro, píldora y lista, ayuda.

## Lector de los libros (añadido 2026-11-16)

Decidido: el propio ERP lee los dos libros de ventas desde Google Drive, con la cuenta de servicio que ya lee el Items de NSYS.

- Ficheros por su identificador de Drive (no por nombre: hay copias viejas con nombres parecidos): ajustes
  `PEDIDOS_LIBRO_AMAZON` (canal `AMAZON`) y `PEDIDOS_LIBRO_STOCKROOM` (canal `AMAZON_STOCKROOM`); interruptor
  `PEDIDOS_LIBROS_ACTIVA` (apagado por defecto). Sin identificador, ese canal no se lee.
- Solo la pestaña **«Paso 1. Meter IMEI»**, con la cabecera en la **fila 6**; las columnas se buscan por su nombre de cabecera:
  «order id», «purchase-date» e «IMEI» en los dos libros. Una fila por móvil. El IMEI puede venir como texto o como número (sin
  decimales ni notación científica) y con espacios; textos como «CANCELADO» o celdas vacías se descartan. Las demás pestañas
  («Hoja1», «IMEIS SUELTO») son apuntes de logística y no se leen.
- Cada 10 minutos mira la fecha de modificación del fichero en Drive y **solo lo descarga y lo lee si cambió** desde la última
  pasada de ese canal (son libros de 15-26 MB: lectura en streaming, sin cargar el libro entero en memoria).
- Manda al servicio de asociación solo las filas con «purchase-date» de los **últimos 30 días** (los pedidos antiguos no están en
  el ERP y llenarían la lista «para revisar»).
- Errores (Drive no responde, falta la pestaña o una cabecera, libro que no se puede leer): no se toca nada, se apunta el error en
  la pasada de ese canal y la píldora lo enseña; se reintenta en la siguiente vuelta.
- Los IMEI de los libros casi nunca cambian después de teclearse; si cambian, manda lo último que diga el libro (regla 2).

## Fuera de esto

Etiquetas desde el ERP. Atajo a Asignaciones.
