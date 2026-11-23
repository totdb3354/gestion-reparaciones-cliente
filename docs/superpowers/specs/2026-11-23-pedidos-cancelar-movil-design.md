# Pedidos: «Cancelar móvil» con botón y confirmación — diseño

Fecha: 2026-11-23. Estado: aprobado en conversación, pendiente de revisar este texto.
Versión: **0.9.42** (servidor + web; la 0.9.41 es la celda Pedido en blanco).
Base: las marcas de la fila (`2026-11-13-pedidos-marcas-design.md`) y el envío parcial (`2026-11-13-pedidos-envios-parciales-design.md`).
Prototipo aprobado: `assets/2026-11-23-pedidos-cancelar-boton-prototipo.html`.

## Para qué

Hoy «Cancelado» es una de las cuatro marcas del desplegable (celda del IMEI y panel de la fila) y **se guarda al elegirla**, sin
confirmación: un mal clic cancela un móvil. Además, «Cancelado» y «Falta por enviar» se guardan por separado y una fila puede tener
las dos, que no tiene sentido (cancelado = ese móvil no sale; falta por enviar = salió el pedido y queda ese móvil). En esa mezcla el
IMEI sigue bloqueado para otras filas, porque el término «enviada con falta» de `SQL_DUPLICADO` no mira la marca.

## Qué cambia

### 1. Web: cancelar es un botón del panel, con confirmación

- «Cancelado» **sale del desplegable** de marcas en los dos sitios (celda del IMEI y panel). Quedan En espera de stock, En revisión
  y Pendiente de color.
- En el desplegable de la celda, debajo, el texto «Para cancelar el móvil, abre el panel de la fila».
- **Panel de una fila editable sin cancelar:** botón **«Cancelar móvil»** (borde rojo) al final de la sección «Marca». Abre una
  confirmación modal:
  - Título «¿Cancelar este móvil?». Texto: «Pedido X, IMEI Y. No saldrá en este pedido y su IMEI quedará libre para otro pedido.»
    (sin IMEI, sin la parte del IMEI; en Amazon sin asociar, sin número de pedido).
  - Si tiene «Falta por enviar»: «Se quitará también "Falta por enviar".»
  - Si tiene otra marca: «Sustituye a la marca "En revisión".»
  - Campo de nota **opcional** (máx. 200, como hoy).
  - Botones «Volver» y «Cancelar el móvil» (rojo). Escape o «Volver» no cambian nada.
  - Manda el mismo `PUT …/marca` de hoy con `marca = CANCELADO`, la nota y `marcaAnterior`.
- **Panel de una fila cancelada:** enseña «Cancelado», la nota, «Cancelado por X el dd/MM HH:mm. Este móvil no sale en este pedido
  y su IMEI está libre.» y el botón **«Quitar cancelación»**. **No** salen el desplegable de marca ni el botón «Falta por enviar».
  - Confirmación: «¿Quitar la cancelación?» — «La fila vuelve a estar pendiente y su IMEI vuelve a contar como ocupado.» y
    «"Falta por enviar" no vuelve: si hace falta, márcala otra vez.» Botones «Volver» y «Quitar cancelación».
  - Manda `PUT …/marca` con `marca = null` y `marcaAnterior = CANCELADO`.
- **Celda del IMEI de una fila cancelada:** el desplegable no ofrece marcas; dice «Fila cancelada: la marca se cambia desde el
  panel («Quitar cancelación»)».
- Errores: como hoy (aviso arriba de la tabla con el mensaje del servidor; el 409 recarga la fila).
- Ayuda de Pedidos (`AyudaPedidos.tsx`): «Cancelado» deja de ser una marca del desplegable y pasa a «Cancelar móvil» en el panel.

### 2. Servidor: cancelar se come «Falta por enviar»

En `FilasDAO.ponerMarca`, con la fila ya bloqueada:
- Al **poner CANCELADO** en una fila con `FALTA_ENVIAR_EN` no nulo: en la misma transacción `FALTA_ENVIAR_EN = NULL`,
  `FALTA_ENVIAR_USU = NULL` y un apunte `YA_ENVIADO` con detalle **«al cancelar»** (la web lo pinta «quitó "Falta por enviar" al
  cancelar», no «ya ha salido»).
- `ponerFaltaEnviar(falta = true)` sobre una fila con `MARCA = 'CANCELADO'` → nuevo resultado **CANCELADA_NO_MARCABLE**, 409 «Este móvil está cancelado: quita la
  cancelación antes de marcar "Falta por enviar"» (el `MSG_NO_MARCABLE` de hoy habla de la etiqueta y confundiría). La web no
  enseña el botón en una cancelada; esto cubre a dos personas a la vez.
  Quitar la falta (`falta = false`) no cambia.
- Quitar la cancelación **no** devuelve la falta.

### 3. Servidor: quitar la cancelación comprueba el IMEI duplicado

Mientras una fila está cancelada su IMEI está libre y alguien puede haberlo puesto en otra fila. Al **salir de CANCELADO** (a sin
marca o a otra marca) en una fila con IMEI, `ponerMarca` pasa la misma consulta `SQL_DUPLICADO` (IMEI, propia fila). Si devuelve
una fila: lanza `ImeiDuplicadoException` sin escribir, el controlador responde **409 «Este IMEI ya está en el pedido X»** (mismo
texto que el PUT del IMEI) y la fila sigue cancelada.

### 4. Datos que ya existan

Antes de la migración, en preprod y en producción (solo lectura):
`SELECT COUNT(*) FROM Pedido_fila WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;`
Si hay alguna, `migracion-0.9.42.sql` les pone `FALTA_ENVIAR_EN = NULL, FALTA_ENVIAR_USU = NULL` (sin apunte en el historial; se
anota en la doc del módulo). Si no hay ninguna, la migración no hace falta y no se crea.

## Lo que no cambia

- En la BD sigue siendo `MARCA = 'CANCELADO'` con su nota, quién y cuándo; una fila cancelada no lleva otra marca a la vez.
- `SQL_DUPLICADO`, los vendidos del bot, lo pendiente de buscar y la asociación de Amazon no se tocan. Con el punto 2 la mezcla
  cancelado + falta deja de existir y el caso límite del duplicado desaparece solo.
- El endpoint `PUT /api/pedidos/filas/{id}/marca` y su contrato (OpenAPI) no cambian; solo responde 409 en un caso más.
- Cancelar es por fila («Cancelar móvil»), no por pedido. Cancelar en ShippyPro desde el ERP queda para valorar (plan-futuro,
  Pedidos fase 2, punto 5).

## Pruebas

- **Servidor (`FilasDAOTest`):** poner CANCELADO con falta la borra y apunta `YA_ENVIADO` «al cancelar»; sin falta no apunta nada
  más; `ponerFaltaEnviar(true)` en cancelada → CANCELADA_NO_MARCABLE (y su 409 en el controlador); quitar CANCELADO con IMEI duplicado → `ImeiDuplicadoException` sin
  escribir; sin duplicado o sin IMEI → PUESTO; pasar de CANCELADO a otra marca también comprueba. `FilasControllerTest`: el 409.
- **Web:** el desplegable sin «Cancelado» (celda y panel); «Cancelar móvil» abre la confirmación, «Volver»/Escape no mandan nada,
  confirmar manda `CANCELADO` con la nota; textos de falta y de marca sustituida; en cancelada solo «Quitar cancelación», sin
  desplegable ni «Falta por enviar»; el 409 sale en el aviso; el historial pinta «al cancelar».
- **E2E en preprod** (datos sintéticos por API y limpieza): cancelar una fila web con falta, quitar la cancelación, y el 409 con el
  IMEI puesto en otra fila.

## Documentación

`docs/modulos/pedidos.md`: §3 (marcas: tres en el desplegable + «Cancelar móvil» en el panel), §4 (IMEI duplicado: quitar la
cancelación comprueba; cancelar quita «Falta por enviar»).
