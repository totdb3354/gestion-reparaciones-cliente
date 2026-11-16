# Pedidos: «Validar SKU» en el aviso naranja del IMEI — diseño

Fecha: 2026-11-16. Estado: aprobado en conversación. Va en la 0.9.35 (aún no publicada en producción).

## Por qué

En la hoja de pedidos, el verificador de SKU pintaba en naranja la celda si el SKU tecleado no cuadraba con el móvil de NSYS, y
si la persona la limpiaba a mano el verificador no la volvía a pintar: la palabra de quien gestiona quedaba por encima del
programa. En el ERP el aviso naranja se calcula cada vez y nadie lo puede anular, y además el naranja impide generar la etiqueta
desde el ERP. Hace falta que una persona pueda darlo por bueno.

## Qué hace

- Un único botón que cambia de texto: **«Validar SKU»** cuando la celda del IMEI sale en naranja por un motivo de SKU, y
  **«Quitar validación»** cuando ya está validada. Está en el panel de la fila y en el desplegable de la celda del IMEI.
- Solo para los motivos de SKU: `MODELO` («otro modelo, capacidad o eSIM»), `COLOR_O_GRADO` («otro color o grado»),
  `GRADO_NO_VENDIBLE` («ese grado no se vende») y `SKU_DESCONOCIDO` («el maestro no conoce este SKU»). **No** para
  `NO_EN_NSYS` ni `EN_OTRA_FILA`, que son errores del IMEI y se arreglan cambiándolo. Tampoco para el ámbar.
- Validada: la celda deja de salir en naranja, enseña el IMEI con una marca pequeña **«validado»** (el aviso original en el
  `title`), no cuenta en «N no cuadran», no bloquea la etiqueta y el lector la trata como una fila con IMEI.
- La validación vale **para ese IMEI en esa fila**: si se cambia o se quita el IMEI, deja de valer y el aviso se vuelve a juzgar.
- Historial: «SKU validado (otro color o grado: es …)» y «Validación quitada», con quién y cuándo.

## Servidor

- `Pedido_fila`: `VALIDADA_IMEI VARCHAR(20) NULL` (el IMEI que se validó), `VALIDADA_EN DATETIME NULL`, `VALIDADA_USU INT NULL`
  (FK a `Usuario`, `ON DELETE SET NULL`, con `FOREIGN KEY IF NOT EXISTS`). Migración aditiva y repetible
  `sql/migracion-pedidos-validacion.sql` (producción y preprod) y `crear_bd.sql`/`migracion-pedidos-tabla.sql` al día.
- `PUT /api/pedidos/filas/{id}/validacion`, cuerpo `{ "validar": true|false, "imeiAnterior": string|null }`, con
  `Idempotency-Key`, roles como el IMEI. Validar exige que la fila tenga IMEI y que su aviso actual sea de uno de los cuatro
  motivos de SKU (si no, 409 con mensaje); `imeiAnterior` como en el PUT del IMEI (409 si otra persona lo cambió). Quitar siempre
  se puede si está validada. Devuelve la `FilaVista`.
- Al servir: si `VALIDADA_IMEI` es el IMEI actual y el motivo es de SKU, el nivel pasa a `OK` y la fila lleva `validada: true` y
  `motivoValidado` (el motivo que tenía). Con otro IMEI, la validación se ignora. Historial con tipos `VALIDADA` (detalle: el motivo)
  y `VALIDACION_QUITADA`.
- `FilaVista` gana `validadaEn`, `validadaPor` y `motivoValidado` (nullable).

## Web

- Botón en el panel y en el desplegable del IMEI, con los textos de arriba; la celda con «validado» y el `title` con el aviso
  original; «N no cuadran» y el filtro «No cuadran» sin las validadas; historial; una línea en la ayuda.

## Pruebas

Servidor: validar cada motivo de SKU, rechazo en `NO_EN_NSYS`/`EN_OTRA_FILA`/ámbar/sin IMEI, quitar, cambiar el IMEI anula la
validación, `imeiAnterior` 409, idempotencia, roles. Web: botón según el estado, celda «validado», contador y filtro, historial.
