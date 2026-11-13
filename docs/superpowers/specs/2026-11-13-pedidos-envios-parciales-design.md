# Pedidos: envíos parciales y móviles del mismo pedido

> **SUSTITUIDA el 13/11/2026. No construir.** El mismo día, el usuario decidió y se construyó otra versión de los envíos
> parciales, que es la que vale: marca manual «falta por enviar» en la fila (solo pedidos de web con la etiqueta generada), que
> se quita a mano o sola cuando el plugin de la web sincroniza ese IMEI, y «1/3, 2/3, 3/3» en los pedidos de varios móviles. Se
> descartó el estado automático de §4.1 («etiqueta impresa y fila sin IMEI»): en las webs el IMEI de una fila es provisional
> hasta que se sincroniza. Lo que vale está en el contrato del servidor (`docs/api_contract.md`: `PUT
> /api/pedidos/filas/{id}/falta-enviar` y `POST /api/pedidos/sincronizados`). De este documento solo queda como idea para
> después la sección «Este pedido» del panel (§4.2) y la vista agrupada por pedido (§6). Se conserva como registro.

- **Fecha:** 13/11/2026
- **Estado:** sustituida el 13/11 (ver el aviso de arriba)
- **Amplía:** `2026-11-10-pedidos-tabla-design.md` (§6 bis, estados) y `2026-11-11-pedidos-pantalla-v2-design.md` (§3.2 la
  columna «Enviado», §3.5 el panel de la fila)

## 1. El problema

Para la empresa, un móvil está **enviado** cuando la etiqueta de su pedido está impresa (pantalla v2, §3.2). ShippyPro da un
solo envío por pedido y no dice qué móviles van en la caja. El ERP, por tanto, pone «Enviado» a todas las filas del pedido en
cuanto la etiqueta sale impresa.

Eso es falso en un **envío parcial**: un pedido de varios móviles, con alguno sin stock y el cliente con prisa, se imprime
entero, salen los móviles que hay y el que falta sale después (normalmente al día siguiente, con la etiqueta duplicada en
ShippyPro). Solo pasa en las webs y es excepcional. ShippyPro no lo puede saber (comprobado el 13/11: ningún pedido con dos
envíos en diez días), así que se marca a mano en el ERP.

Tampoco se ve hoy en la tabla qué filas son del mismo pedido. Los pedidos de varios móviles no son raros: en torno a uno de
cada diez en Amazon y casi la mitad en las webs de distribuidores.

## 2. Lo que se decidió y lo que no

- **La tabla no cambia de forma.** Sigue siendo una fila por móvil, como la hoja: el trabajo diario (mirar el SKU, buscar el
  móvil, poner el IMEI) es fila a fila y no se le meten cabeceras en medio.
- **No hay una segunda vista agrupada por pedido** en esta entrega. Duplicaría filtros, contadores, buscador y el pintado de
  muchas filas para un caso excepcional. Se reserva para cuando el ERP genere las etiquetas, que es trabajo por pedido (§6).
- **Lo que va junto se nota en la tabla** (§3) y **el envío parcial se hace desde el panel de la fila** (§4), que enseña el
  pedido entero.

## 3. Ver qué filas son del mismo pedido

Solo en las filas de ShippyPro de un pedido con más de un móvil:

- En la columna **Pedido**, tras el número, la posición del móvil en el pedido: «1/3», «2/3», «3/3», en gris.
- Una **raya fina a la izquierda** une las filas del mismo pedido cuando están seguidas en lo que se ve. Las filas de un pedido
  nacen juntas (misma fecha), así que salen seguidas salvo que un filtro deje solo alguna; entonces no hay raya y el «2/3» basta.

Las filas del histórico no llevan ninguna de las dos marcas.

## 4. El envío parcial

### 4.1 El estado «Falta por enviar»

Estado nuevo de la fila, en la columna «Enviado», con etiqueta ámbar: el pedido tiene la etiqueta **impresa** pero ese móvil
no ha salido. Queda así la tabla de estados (amplía la de la pantalla v2, §3.2):

| Estado | Cuándo | En la columna «Enviado» |
|---|---|---|
| Pendiente | El pedido no tiene etiqueta | «Pendiente» |
| Etiqueta generada | Etiqueta sin imprimir | «Etiqueta generada dd/mm» + transportista y seguimiento |
| **Falta por enviar** | Etiqueta impresa, y la fila está marcada «sale después» **o** no tiene IMEI | «Falta por enviar» (ámbar) |
| Enviado | Etiqueta impresa y ninguna de las dos cosas anteriores; o marcada «ya ha salido» | «Enviado dd/mm» + transportista y seguimiento |
| Ya no pendiente | Como hoy | «Ya no está pendiente» |
| Histórico | Como hoy | «De la hoja» |

- **La parte automática:** una fila sin IMEI de un pedido con la etiqueta impresa pasa sola a «Falta por enviar». Ese móvil
  seguro que no ha salido. Cubre también el descuido de un pedido enviado sin IMEI apuntado, que así se ve.
- **La parte a mano:** una fila con IMEI que se queda en el almacén se marca «sale después» (§4.2).
- **La fecha de «Enviado»** es la de impresión de la etiqueta; en una fila que salió después, la del día en que se marcó «ya
  ha salido».
- Poner el IMEI más tarde a una fila en «Falta por enviar» **no** la pasa a «Enviado»: hace falta «ya ha salido», para que la
  fecha sea la del envío real y no la de teclear el IMEI.

### 4.2 Las acciones, en el panel de la fila

El panel de cualquier fila de ShippyPro gana una sección **«Este pedido»**: todos los móviles del pedido (posición, SKU, IMEI y
estado), aunque algún filtro o la búsqueda no los tenga a la vista. Desde ahí:

- **«Envío parcial…»** (solo si el pedido tiene etiqueta, generada o impresa, y alguna fila no ha salido): una casilla por móvil
  con la pregunta «¿Sale ahora?». Vienen marcadas las que tienen IMEI y desmarcadas las que no. Al confirmar, las desmarcadas
  quedan «sale después». Se puede hacer antes de imprimir (cuando ya se sabe que un móvil no estará), y surte efecto al imprimirse.
- **«Ya ha salido»** (solo si hay filas en «Falta por enviar»): una casilla por cada una; las marcadas pasan a «Enviado» con la
  fecha de ese momento.
- Las dos se pueden **deshacer** desde el mismo panel mientras el pedido siga con etiqueta: quitar «sale después» y quitar «ya ha
  salido».

En la práctica solo se usa en las webs, pero no se limita por canal.

### 4.3 Otros casos

- **Un pedido que vuelve a estar pendiente** (spec base, §6) pierde sus marcas, como pierde los datos del envío.
- **Un pedido de un solo móvil** puede tener «Falta por enviar» (sin IMEI y con la etiqueta impresa) y su «ya ha salido».
- **La lista de vendidos** (`/api/pedidos/vendidos`) no cambia: una fila en «Falta por enviar» con IMEI cuenta como vendida,
  porque el móvil es de ese cliente aunque salga mañana. El bot de stock y el plugin de la web no se enteran de nada nuevo.

### 4.4 Filtro y panel

- El filtro de la columna «Enviado» gana la casilla **«Falta por enviar»**, que saca de un clic lo que se debe a un cliente.
- El historial del panel («Quién ha tocado el IMEI») pasa a **«Historial de la fila»** y apunta también quién marcó «sale
  después» o «ya ha salido», y cuándo, y quién lo deshizo.

## 5. Qué necesita del servidor

| Para | Cambio |
|---|---|
| §3 | Cada fila de `GET /api/pedidos/filas` lleva su `posicion` y el total de móviles de su pedido (`unidades`); en el histórico, sin ellos. |
| §4.1 | Dos marcas por fila, con quién y cuándo: «sale después» y «ya ha salido». El servidor calcula el estado con ellas y con el IMEI, y sirve el estado nuevo `FALTA_POR_ENVIAR`. La fecha de «Enviado» de una fila con «ya ha salido» es la de esa marca. |
| §4.2 | Las filas del mismo pedido de una fila (canal y número de pedido), con su estado, para la sección «Este pedido» del panel. |
| §4.2 | Dos acciones sobre una lista de filas **del mismo pedido**: marcar o quitar «sale después», y marcar o quitar «ya ha salido». `409` si alguna fila es de otro pedido, del histórico, o si el pedido no tiene etiqueta (o, para «ya ha salido», si la fila no está en «Falta por enviar»). Con `Idempotency-Key`, como las demás escrituras. SUPERTECNICO y ADMIN. |
| §4.3 | Al reabrirse un pedido, se borran sus marcas. |
| §4.4 | El historial de la fila incluye las marcas y sus deshacer. |

## 6. Fuera de esta entrega

- **Vista agrupada por pedido** (una cabecera por pedido con su resumen, como los bloques de compras de piezas). Se plantea
  cuando el ERP genere las etiquetas: ese trabajo es por pedido (preparar la caja, comprobar que están todos, generar), y
  encajaría con el rol de logística.
- Un contador propio de «Falta por enviar» junto a los demás contadores de arriba.
- Saber qué móviles van en cada caja desde ShippyPro: no se puede (un envío por pedido, sin detalle).

## 7. Cómo se comprueba

- Un pedido de tres móviles con etiqueta impresa y los tres con IMEI: los tres «Enviado».
- El mismo con uno sin IMEI: ese queda «Falta por enviar» sin que nadie haga nada.
- «Envío parcial…» con uno desmarcado: ese queda «Falta por enviar»; los otros dos, «Enviado» con la fecha de impresión.
- Poner el IMEI al que faltaba no lo cambia; «Ya ha salido» lo pasa a «Enviado» con la fecha del día.
- Deshacer cada marca devuelve el estado anterior, y el historial apunta las dos cosas.
- Un pedido marcado que vuelve a estar pendiente pierde las marcas.
- Las acciones con filas de dos pedidos distintos, del histórico o de un pedido sin etiqueta dan `409`; el TECNICO, `403`.
- La lista de vendidos da las filas en «Falta por enviar» con IMEI igual que antes.
- En la tabla, «2/3» y la raya en un pedido de varios móviles; nada en los de uno ni en el histórico.
