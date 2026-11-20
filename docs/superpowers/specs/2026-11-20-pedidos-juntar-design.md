# Pedidos: «Juntar pedidos» (dos pedidos web en una sola caja) — diseño

Fecha: 2026-11-20. Estado: aprobado en conversación (prototipo v6). Fase 2 de Pedidos, después del corte de PEDIDOS 2026.
Prototipo: `assets/2026-11-20-pedidos-juntar-prototipo.html` (datos inventados).

## Por qué

En las webs, cuando un cliente tiene dos pedidos pendientes a la vez, logística valora juntarlos en **una sola caja con una
sola etiqueta** (la del más reciente). Hoy no se marca en ningún sitio: se hace la etiqueta del pedido B con los móviles de los
dos y el pedido A desaparece de ShippyPro cuando la web lo da por completado. Comprobado con la API (20/11, solo lectura):
el pedido juntado no queda ni en pendientes ni en ningún envío, y la API no tiene consulta de archivados. El ERP lo ve
«Ya no está en ShippyPro» sin envío y no sabe que salió.

En los 9 días con datos (11-20/11) hubo 2 pedidos juntados; las dos veces que dos pedidos del mismo cliente coincidieron
pendientes, se juntaron. Falta que logística confirme si se hace siempre o solo con algunos clientes (no cambia el diseño:
el ERP sugiere y decide logística).

Juntar es además la base de lo que viene detrás (orden acordado el 19/11): la **sincronización automática** de la web (un pedido,
o un grupo juntado, se sincroniza entero o no se sincroniza) y las **etiquetas desde el ERP** (un grupo = una etiqueta).

## 1. La regla

- Solo **webs** (DISTRIBUIDORES, PARTICULARES, BACKIPHONE) y **de la misma tienda**. Amazon no: cada pedido lleva su seguimiento.
- Solo pedidos que **siguen en ShippyPro y no han salido**:
  - A: pendiente y **sin etiqueta**;
  - B: pendiente, o con la etiqueta **generada y sin imprimir** (sus móviles aún pueden entrar en la caja).
- Nunca: un pedido que **ya no está en ShippyPro**, uno **enviado** (etiqueta impresa), ni dos pedidos que tengan **cada uno su
  etiqueta** (son dos envíos).
- **El principal** (el que lleva la etiqueta de la caja) lo decide el ERP, empiece logística por A o por B: **el que ya tiene
  etiqueta; si ninguno la tiene, el más reciente** (por la fecha de entrada; a igual fecha, el de la fila más nueva).
- Un principal puede llevar **varios** pedidos; **no hay cadenas**: un pedido juntado no puede ser principal de otro, y un
  principal no se puede juntar a otro.
- Juntar es del **pedido entero**: las filas que le entren después (el cliente añade un móvil) van también en la caja.
- **Separar** se puede mientras las filas de A no tengan el envío del principal.
- Quién: los mismos que ponen IMEI en Pedidos.

## 2. Qué pasa al juntar

- Las filas de A reciben el **envío del principal**: si el principal ya tiene etiqueta, al momento; si no, cuando se genere
  (en la lectura de ShippyPro). Con el envío pasan a «Etiqueta generada» y luego a «Enviado» igual que las del principal, y sus
  móviles pasan a **vendidos** (el bot de vendidos ya cuenta las filas con envío).
- Solo se pone en las filas de A **que siguen vivas**. Si A desaparece de ShippyPro (cancelado…) antes de la etiqueta, sus filas
  no la reciben y el grupo se deshace solo.
- Si el **principal** desaparece de ShippyPro sin etiqueta, el grupo se deshace solo y A vuelve a ir por su cuenta.
- Lo que se deshace solo se apunta en el historial de las filas («se separó: 90108 ya no está en ShippyPro»).
- **Envío parcial:** sin cambios. Con la etiqueta generada, el móvil (de A o del principal) que no entra en la caja se marca
  «Falta por enviar» en su fila, como hoy; sale después con la etiqueta duplicada del principal.
- **Historial:** juntar y separar quedan en el historial de cada fila de A (quién y cuándo), como las marcas.
- **Si se olvida juntar:** A acaba «Ya no está en ShippyPro» y ya no se puede juntar. El stock lo sigue cubriendo la regla del bot
  de la 0.9.37 (spec 2026-11-18 §8: fila web que ya no está, sin envío y sincronizada por la web = vendida), que se queda.

## 3. Pantalla (vista A del prototipo)

**La tabla**
- Los pedidos juntados salen **pegados**, en el sitio de su fila más antigua, con las filas del principal arriba.
- La fracción de la columna Pedido cuenta **los móviles de la caja**: 1/3, 2/3, 3/3. Las filas del principal llevan «+ 90101» y
  las de A, «→ 90108». Una raya morada a la izquierda marca el grupo.
- «Enviado»: las filas de A enseñan el estado del principal con «con 90108». Mientras estén pendientes, el grupo entero enseña el
  **«Sale…» del pedido más antiguo** (la promesa al cliente de A no se esconde: si la caja va tarde, se ve «Con retraso»).
- **Sin avisos en la tabla.**

**El panel de la fila**
- Si el pedido se puede juntar con otro del mismo cliente y tienda: recuadro ámbar «Se puede juntar con 90108» (fecha, móviles,
  estado) con **«Ver 90108»** (salta a ese pedido, que dice lo mismo de vuelta) y **«Juntar»**.
- Botón **«Juntar con otro pedido…»** solo cuando el pedido se puede juntar (no sale en Amazon, en los que ya no están en
  ShippyPro ni en los enviados). En un pedido con etiqueta también sale (puede ser el principal de otro).
- En un pedido juntado: «Va en la caja del pedido 90108», la lista del grupo con el principal señalado y **«Separar»** mientras se
  pueda. En el principal: «Envío juntado: lleva también 90101», con «Separar» en cada uno.
- Mismo cliente = misma tienda y mismo nombre de cliente sin distinguir mayúsculas ni espacios de los lados.

**La ventana «Juntar el pedido X con…»**
- Los pedidos del mismo cliente y tienda, con los que no se pueden juntar en gris y su motivo.
- **Buscar por número de pedido** (el cliente puede salir con el nombre escrito de otra forma).
- «La caja lleva la etiqueta de 90108 (es el más reciente | es el que ya tiene etiqueta).»
- **Aviso de fechas** (decide logística): si juntar hace salir más tarde al que tiene que salir antes, recuadro ámbar: «90105 entró
  el 16/11 y sale hoy; 90111 entró el 19/11 y sale el lun 23/11. Si los juntas, 90105 saldrá con 90111.»
- Qué pasa: una sola etiqueta, la del principal; si ya la tiene, las filas de A pasan ya a «Etiqueta generada»; sus móviles pasan
  a vendidos con la etiqueta; se puede separar mientras A no tenga el envío.

## 4. Servidor

**Datos.** Tabla nueva `Pedido_junto` (por pedido, no por fila; el usuario no la ve):

| Columna | Tipo | |
|---|---|---|
| `CANAL` | `VARCHAR(20)` NOT NULL | tienda web |
| `PEDIDO` | `VARCHAR(60)` NOT NULL | el pedido juntado (A); PK (`CANAL`, `PEDIDO`) |
| `PEDIDO_PRINCIPAL` | `VARCHAR(60)` NOT NULL | B, el de la etiqueta |
| `JUNTADO_EN` | `DATETIME` NOT NULL | |
| `JUNTADO_POR` | `INT` NULL | usuario |

Los tipos del historial de la fila suman `JUNTADO` y `SEPARADO` (con el pedido del otro lado). Migración aditiva y repetible.

**Rutas** (con la sesión y los roles que ponen IMEI):
- `POST /api/pedidos/juntar` `{canal, pedido, con}`: comprueba la regla del §1 con las filas bloqueadas (`FOR UPDATE`,
  `READ COMMITTED`), decide el principal, guarda y, si el principal ya tiene etiqueta, pone su envío en las filas vivas de A. 409
  con el motivo en palabras si no se puede («los dos tienen su propia etiqueta», «90102 ya salió»…).
- `DELETE /api/pedidos/juntar/{canal}/{pedido}`: separa; 409 si A ya tiene el envío.
- `GET /api/pedidos/filas` añade a cada fila de un grupo el pedido principal y los pedidos juntados (para pintar la vista A sin
  otra llamada).

**La lectura de ShippyPro.** Al marcar envíos, el envío del principal se pone también en las filas **vivas** de sus pedidos
juntados (mismos datos: fecha, transportista, seguimiento, id del envío y, luego, la impresión). Al cerrar filas, los grupos cuyo
A o principal ya no tiene filas vivas ni envío se deshacen con su entrada en el historial.

**Lo que no cambia:** `vendidos` (las filas de A con envío cuentan como cualquier enviada), «Falta por enviar», el IMEI
duplicado, el Excel de «Descargar para buscar» (las webs ya van por cliente) y la sincronización manual de la web (por pedido).

## Fuera de esta pieza

- La sincronización automática del plugin (pieza 2) y las etiquetas desde el ERP (pieza 3): usarán `Pedido_junto`.
- **Envíos manuales de pedidos web** (avisos de dirección corregidos a mano en ShippyPro, p. ej. «n.º37748»): el ERP no los
  relaciona con su pedido. Problema aparte, apuntado en `Apuntes/diseno-modulo-telefonos/pedidos-erp-sustituye-pedidos2026.md`.

## Pruebas

- Servidor: la regla del §1 caso por caso (webs/Amazon, tiendas distintas, A con etiqueta, B impreso, ya no está en ShippyPro,
  los dos con etiqueta, cadenas); el principal empezando por A y por B; juntar con B ya etiquetado pone el envío al momento;
  la lectura pone el envío del principal en las filas vivas de A y no en las cerradas; impresión que llega después; grupo que se
  deshace solo (A o principal desaparecen); separar antes y después del envío; filas nuevas de A; `vendidos` cuenta las de A;
  historial; roles; concurrencia (dos juntar a la vez sobre el mismo pedido).
- Web: vista A (pegados, fracción de la caja, «+»/«→», «con 90108», «Sale…» del más antiguo); panel con sugerencia, «Ver» y
  botón solo cuando se puede; ventana con candidatos, búsqueda por número, principal y aviso de fechas; separar; errores 409.
- E2E en preprod con pedidos reales de una web que coincidan (sin tocar ShippyPro) y limpieza al acabar.
