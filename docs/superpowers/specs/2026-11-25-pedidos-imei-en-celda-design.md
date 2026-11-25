# Pedidos: el IMEI se pica en la propia celda y la vista filtrada no se mueve — diseño

Fecha: 2026-11-25. Estado: aprobado en conversación, pendiente de revisar este texto.
Versión: el número se pone al sacarla (orden acordado: 0.9.47 bot de devoluciones, 0.9.48 reserva de IMEI). Solo web.
Base: «Pedidos como la hoja» (celda del IMEI, salto del lector), `CeldaImei.tsx`, `TablaFilas.tsx`, `PedidosPage.tsx`, `estado.ts`.
Prototipo aprobado: `assets/2026-11-25-pedidos-imei-en-celda-prototipo.html` (selector «Hoy (se van) / Nuevo (se quedan)» y
botones que simulan el lector, un compañero y un pedido nuevo).

## Para qué

Los usuarios dicen que a veces les cuesta picar los teléfonos. Pasan dos cosas:

1. **El desplegable de la celda tapa filas.** Mide unos 360 px y repite cosas que ya están en el panel de la derecha (la marca con
   su nota, «Validar SKU», el texto de cancelar). Tras el salto automático se abre en la fila siguiente y tapa las de debajo: para
   pulsar otro pedido hay que cerrarlo antes.
2. **Las filas se mueven bajo el ratón.** Con un filtro puesto (sobre todo «Sin IMEI»), la fila picada deja de cumplirlo cuando
   contesta el servidor y desaparece: las de debajo suben una posición y el clic cae en la fila que no era. Además, la fila hecha
   se esfuma y no se ve si se guardó bien. Pasa igual con los cambios de otros que llegan por el sondeo.

Y una tercera, de la cifra de control: hoy una lectura con la cifra de control mala bloquea el campo en esa celda con «Guardar
igualmente». Quien pica en automático y revisa al final sigue leyendo sin mirar, y puede guardar un IMEI erróneo pulsando donde
estaba el botón.

## Qué cambia

### 1. La celda del IMEI se edita en su sitio

- **En reposo**, igual que hoy: el IMEI con sus piezas de aviso (0.9.45), «validado» o la caja; o «pulsa para poner el IMEI…».
  Las filas no editables (histórico) siguen solo con el texto.
- **Al pulsarla**, la misma celda pasa a ser el campo: input a todo el ancho, el contador `n/15` a la derecha y un botón **▾**
  pequeño (recomendados). Borde azul de 2 px por dentro. Mismo alto que la fila (no crece, no tapa nada). Si la fila tenía IMEI,
  el campo empieza vacío, como hoy.
- **Desaparece el desplegable** (el `Popover` de la celda). Salen de la celda: la marca y su nota (`EditorMarca`), «Validar SKU»
  (`BotonValidarSku`), el texto «Para cancelar el móvil, abre el panel de la fila» y el botón «Cerrar». Todo eso sigue en el panel
  de la derecha (`PanelFila`), que ya lo tiene.
- **Cerrar sin cambiar**: Escape, o pulsar fuera de la celda (y fuera de la lista de recomendados y de un diálogo). Perder el foco
  sin pulsar (cambiar de ventana) no cierra. El foco vuelve a la celda, como hoy.
- **Quitar el IMEI**: en una celda con IMEI, dejar el campo vacío y pulsar Enter. No hay ✕ visible (para no quitar sin querer).
  Sin IMEI, Enter con el campo vacío no hace nada (es el Enter del lector en la celda recién abierta).
- **El lector**, como hoy: solo cifras (también al pegar), a las 15 se espera `QUIETO_MS` y se comprueba; Enter comprueba al
  momento; con un guardado en vuelo no se aceptan entradas; idempotencia y `imeiAnterior` sin cambios.
- **Pocas cifras** (menos de 15 y Enter, tecleando a mano): error en el sitio, celda en rojo, «Un IMEI son 15 cifras». No salta.

### 2. Lectura no válida: rojo y sigue (no bloquea)

«No válida» = más de 15 cifras, o 15 con la cifra de control (Luhn) mala. Es solo el formato del número; no se consulta nada.

- **No se guarda** (no hay PUT) y **no frena**: la celda queda en rojo con «IMEI no válido · …6537» (las 4 últimas cifras
  leídas) o «IMEI no válido · 16 cifras», el texto entero en el `title`, el aviso no modal («Pedido X: IMEI no válido: … No se ha
  guardado.», con el mismo arranque que los demás avisos), y el campo salta a la siguiente celda como con un IMEI bueno.
- **El aviso no mueve la tabla** (añadido tras probarlo en Chromium: el aviso encima de la tabla la empujaba ~40 px). Todos los
  avisos de esa zona (no válido, repetido, fallo al guardar) van en la barra del buscador, en el hueco de la derecha, en una línea
  (cortado con «…» y entero en el `title`), con su ✕. Prototipo: `assets/2026-11-25-pedidos-aviso-sin-mover-prototipo.html` (opción C).
- **El salto se salta las celdas en rojo por lectura no válida** (son para revisar, no para picar encima).
- **Al revisar**: pulsar la celda roja la abre con la lectura mala ya en el campo. Si se lee o escribe otro IMEI, sustituye a la
  lectura mala (una entrada tras pausa, como hoy con `SUSTITUIR_MS`) y se guarda; con uno bueno, salta como siempre. **Solo en
  una celda reabierta así y solo si fue la cifra de control** sale el botón pequeño **«Guardar igual»** dentro de la celda: guarda
  esa lectura tal cual y la celda se cierra (no salta). Con más de 15 cifras no hay «Guardar igual».
- Escape o pulsar fuera en una celda roja la cierra **y la deja en rojo**. El rojo se quita al guardar un IMEI en esa fila (por
  quien sea: si el sondeo trae la fila con IMEI, el rojo de lectura no válida se borra).
- **Se guarda en el navegador** (`localStorage`, clave `pedidos.imeiNoValidos.<usuario>`): `idFila`, la lectura, el motivo
  (`luhn` | `mas`) y cuándo. Aguanta recargar y cerrar el navegador; caduca a las 24 h. Por usuario, para que en un PC compartido
  no salgan los rojos de otro. No lo ven los demás usuarios (decisión: quien pica la tanda es quien la revisa; la fila sigue
  saliendo como «sin IMEI» para todos). Cerrar sesión no lo borra.
- Hoy «Guardar igualmente» solo sale en el flujo de picar; eso desaparece.

### 3. Recomendados en el ▾

- El ▾ de la celda abierta despliega la lista de recomendados (la de hoy: caja delante, IMEI, «es …» en nivel 3, la etiqueta del
  nivel). Solo se pide al servidor al desplegarla, como hoy. Es lo único que flota: `Popover` anclado al ▾, debajo o encima según
  quepa, cierra con Escape o pulsando fuera.
- Elegir uno lo guarda y cierra la celda (no salta), como hoy. El foco sigue en el campo mientras la lista está abierta, para que
  el lector pueda leer igualmente.

### 4. La vista filtrada no se mueve (como en Excel)

- Al poner o cambiar un **filtro de columna** (incluidos los contadores de arriba, que ponen filtros), el **periodo o rango**, la
  **búsqueda** o el «Ver» de pendientes de fuera, se calcula qué filas cumplen y **esa es la vista**. A partir de ahí, una fila de
  la vista que deja de cumplir el filtro **se queda en su sitio**, sin marca ni aviso, sea por un cambio propio o por el sondeo.
- **Filas nuevas** (un `idFila` que no estaba en lo recibido al fijar la vista) que cumplen el filtro **entran**, en su sitio del
  orden (al final con el orden por fecha), sin mover las de arriba. Así no se pierde un pedido que llega mientras se pica.
- Una fila que el servidor deja de mandar (p. ej. sale del periodo) desaparece: no hay datos que pintar.
- Cambiar el **orden** (clic en la cabecera SKU) reordena la vista pero no la recalcula.
- **Los contadores de arriba van al día** (cuentan las filas recibidas, como hoy): con la vista fija, «194 sin IMEI» puede no
  coincidir con las filas a la vista. Es lo esperado.
- Sin filtros de columna la vista es todo lo recibido: nada cambia respecto a hoy.
- Pulsar un contador de arriba recalcula siempre la vista, aunque sea el que ya está puesto (es la forma de «limpiar» las
  hechas sin quitar el filtro).
- La vista fija vive en memoria, en la página: salir y volver a Pedidos la recalcula.

### 5. Salto automático

Se mantiene, como hoy: al guardar un IMEI leído o tecleado (y ahora también tras una lectura no válida), el campo pasa a la
siguiente fila de la vista editable, sin IMEI, sin marca que lo impida (`saltaElLector`) y **sin rojo de lectura no válida**. Sin
ninguna, la celda se cierra. Con la vista fija, la fila de abajo ya no se mueve al contestar el servidor.

## Qué no cambia

- El servidor: ninguna API, ni la regla de IMEI repetido (`SQL_DUPLICADO`: otra fila de ShippyPro pendiente no cancelada, o enviada
  con «falta por enviar»; una devolución, cuya fila ya está cerrada, no cuenta).
- El 409 por IMEI repetido y los fallos de red: como hoy (celda en rojo en memoria, aviso arriba, reintentar con la misma clave).
- El panel de la derecha, sus botones y el historial.
- Los avisos de SKU (naranja/ámbar): se guardan, no frenan, se ven en la celda en reposo; «Validar SKU» queda solo en el panel.
- El modo ventana (más de `UMBRAL_VENTANA` filas): la fila con la celda abierta se sigue pintando aunque salga de la ventana.

## Piezas

- `CeldaImei.tsx`: sin `Popover` de celda; en edición pinta `CampoImei` (nuevo, en el mismo fichero o `CampoImei.tsx`) con la
  lógica que hoy está en `Desplegable` (cifras, `QUIETO_MS`, Luhn, `guardar`, idempotencia). `Desplegable` desaparece.
- `ListaRecomendados` (sale de `Desplegable`): el `Popover` del ▾.
- `estado.ts`: `FalloImei` gana la variante de lectura no válida (`noValido: 'luhn' | 'mas'`, la lectura, sin clave); los de este
  tipo se leen y escriben en `localStorage` (módulo pequeño `noValidos.ts`: cargar con caducidad, guardar, borrar por fila).
- `TablaFilas.tsx`: `saltar` también desde una lectura no válida y salta las filas en rojo de ese tipo.
- `PedidosPage.tsx`: la vista fija (ids fijados + ids conocidos al fijar) recalculada al cambiar filtros, periodo, búsqueda o
  «Ver»; `vistas` = filas recibidas que están en la vista fija o son nuevas y cumplen. Función pura en `filas.ts`
  (`vistaFija(filas, fijadas, conocidas, filtros, hoy)`) con sus tests.
- `AyudaPedidos.tsx`: la ayuda dice que el IMEI se pica en la celda, qué es el rojo «IMEI no válido», cómo revisarlo, y que el
  filtro no saca las filas ya picadas hasta cambiarlo.

## Pruebas

- Unitarias (`filas.test.ts`, `noValidos.test.ts`): vista fija (deja de cumplir → se queda; nueva que cumple → entra; nueva que no
  cumple → no; la que deja de llegar → fuera; recalcular al cambiar filtros); `localStorage` (por usuario, caducidad 24 h, se borra
  cuando la fila trae IMEI).
- Componente (`TablaFilas.*.test.tsx`, `PedidosFiltros.test.tsx`): la celda se edita en su sitio sin desplegable; no hay marca ni
  «Validar SKU» en la celda; salto tras IMEI bueno; lectura con Luhn mala → rojo, sin PUT, salta, el salto siguiente la evita;
  16 cifras igual; reabrir la roja trae la lectura y «Guardar igual» (solo Luhn) guarda sin saltar; Escape la deja roja; quitar
  con vacío + Enter; ▾ abre recomendados y elegir guarda; con «Sin IMEI» puesto, picar no saca la fila; el sondeo con un IMEI de
  otro tampoco; un pedido nuevo entra; cambiar el filtro recalcula.
- Se actualizan los tests existentes que usan el desplegable (`TablaFilas.salto`, `PedidosV2`, `PedidosMarcas`,
  `PedidosValidar`, `PedidosCancelar`…): lo de marca y validar se prueba ya solo desde el panel.
- Preprod: E2E con datos sintéticos por API y limpieza (lectura buena, mala, 16 cifras y repetida con el filtro «Sin IMEI»).
