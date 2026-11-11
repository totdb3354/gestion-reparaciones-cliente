# «Pedidos» como la hoja: mejoras de la pantalla (v2)

- **Fecha:** 11/11/2026
- **Estado:** aprobada por el usuario el 11/11; pendiente de plan de implementación
- **Amplía:** `2026-11-10-pedidos-tabla-design.md` (§3 la pantalla, §6 bis los estados, §7 la API)
- **Prototipo:** `assets/2026-11-11-pedidos-pantalla-v2-prototipo.html` (datos inventados)

## 1. El objetivo

La tabla «Teléfonos › Pedidos» ya funciona en preprod. Esta entrega mejora **cómo se ve y cómo se trabaja** con ella, sin
cambiar qué datos lleva ni cómo se llenan. La norma sigue siendo la del usuario: tiene que parecerse a la hoja (una fila por
móvil, mismo orden de columnas, lo viejo arriba y lo nuevo abajo).

Las columnas y los datos nuevos (asignar el trabajo desde la fila, «quién lo tiene», «localización», observaciones con valores
fijos, los «OK» finales de la hoja) van en una spec posterior: dependen de decisiones sobre lo que hoy se apunta en la hoja.

## 2. Cómo se trabaja (lo que dijo el usuario)

Se va **de uno en uno**: se mira una fila y su SKU, se abre la celda para ver los recomendados, la persona se levanta, busca el
móvil en el stock físico y, si lo encuentra, vuelve y pone el IMEI; si no, la fila se queda vacía. Luego, la fila de abajo.

De ahí salen dos decisiones: lo que la persona se lleva al almacén es **la caja** del recomendado, y al volver tiene que
**encontrar la fila** que dejó. Un salto automático a la siguiente fila tras guardar no aporta nada con este flujo y no entra.

## 3. Lo que entra

### 3.1 Plataforma se filtra por tienda

La columna no cambia: `AMZ`, `FNSK` o el nombre del cliente, como en la hoja. Cambia la lista de su filtro, que deja de tener
un valor por cliente:

| Casilla del filtro | Canal del ERP |
|---|---|
| AMZ | `AMAZON` |
| FNSK | `AMAZON_STOCKROOM` |
| Fonestockroom | `DISTRIBUIDORES` |
| Fonestore | `PARTICULARES` |
| BackiPhone | `BACKIPHONE` |
| Histórico (otros) | filas del histórico sin canal |

- Solo se enseñan las casillas que tienen filas entre las recibidas, cada una con su recuento.
- Un cliente concreto se busca por su nombre en el buscador; no hay casillas por cliente.
- En el histórico, el canal se deduce del texto de Plataforma solo para las dos cuentas de Amazon (`AMZ`; `FNSK` o `FSNK`).
  Todo lo demás del histórico (clientes, incidencias, vacías) va a «Histórico (otros)».

### 3.2 El estado, como etiqueta

El estado de la fila deja de teñir la fila entera y pasa a ser una etiqueta de color con texto en la columna «Enviado»:

| Estado | En la columna «Enviado» | La fila |
|---|---|---|
| Pendiente | etiqueta «Pendiente» (discreta, borde punteado) | texto normal |
| Enviada | etiqueta verde «Enviado dd/mm» y, tras ella, transportista y seguimiento | texto normal |
| Ya no pendiente | etiqueta azul «Ya no está pendiente» | texto normal |
| Histórico | etiqueta gris «De la hoja» | **gris** |

El gris queda para una sola cosa: lo que no se puede editar (el histórico). Una fila enviada sigue siendo editable (§6 bis de la
spec base) y por eso se lee como las demás. Los colores de la celda del IMEI (ámbar y naranja) no cambian.

El filtro de la columna «Enviado» pasa a tener cuatro casillas: «Pendientes», «Enviados», «Ya no pendientes» e «Histórico de la
hoja» (hoy el histórico cuenta como enviado).

### 3.3 Contadores pulsables

Junto al título, cuatro contadores que son botones: **«N filas»**, **«N pendientes»**, **«N sin IMEI»** y **«N no cuadran»**.

- Cuentan sobre las filas recibidas (el periodo o la búsqueda), no sobre lo filtrado, como hoy.
- Pulsar uno **sustituye** los filtros de columna por el suyo: «pendientes» deja el estado Pendiente; «sin IMEI», las editables
  sin IMEI (enviadas incluidas, como hoy); «no cuadran», las que tienen el IMEI en naranja; «filas» los quita todos. El texto
  del buscador y el periodo no se tocan.
- El que está aplicado se ve marcado. Con algún filtro puesto se añade «N a la vista», que no es botón.
- «No cuadran» es nuevo también como casilla del filtro de la columna IMEI, junto a «Solo sin IMEI».

Hacen el papel de los filtros guardados de otros programas. Los filtros guardados por el usuario no entran.

### 3.4 Los filtros puestos, a la vista

Cada filtro de columna activo se enseña como una etiqueta dentro de la caja del buscador, delante del texto: «Plataforma:
Fonestockroom», «SKU: …», «Pendientes», «Sin IMEI», «No cuadran». Cada una lleva una ✕ que quita solo ese filtro. «Quitar
filtros» sigue existiendo y quita todos (y vacía el buscador). La cabecera de una columna con filtro sigue resaltada.

### 3.5 Panel de la fila

Al pulsar una fila en cualquier celda que no sea la del IMEI se abre un panel a la derecha de la tabla (no la tapa; la tabla se
estrecha). Pulsar otra fila lo cambia; pulsar la misma, o «Cerrar», lo cierra. La fila elegida queda señalada. Contenido:

- **El pedido:** número, estado (la misma etiqueta), tienda, plataforma, SKU, fecha, fecha de compra y, si está enviado,
  transportista y seguimiento.
- **El móvil:** el IMEI puesto y lo que el ERP sabe de él y ya sirve la tabla (qué SKU es, su caja, el motivo del aviso).
- **Quién ha tocado el IMEI:** del más reciente al más antiguo, quién, cuándo y qué hizo (puso, cambió uno por otro, quitó).
  En una fila del histórico: «Viene de la hoja: no hay registro de quién lo puso». Sin cambios: «Nadie ha puesto IMEI todavía».

El panel solo enseña; el IMEI se sigue poniendo en la celda.

### 3.6 La caja delante y la fila marcada

- En el desplegable de recomendados, cada candidato empieza por **la caja**, resaltada, y después el IMEI y lo demás. El título
  pasa a «Recomendados para *SKU* · dónde buscarlo».
- La última fila cuya celda de IMEI se abrió queda marcada con una **raya azul** en su borde izquierdo, se guarde o no un IMEI.
  La marca dura mientras la pantalla esté abierta y pasa a la siguiente celda que se abra. No se guarda en el servidor.

### 3.7 El histórico, mezclado

Las filas del histórico salen **mezcladas por fecha** con las del ERP dentro del periodo elegido, como en la hoja: en gris, con
la etiqueta «De la hoja», sus textos tal cual (un IMEI puede ser «CANCELADO») y sin edición. Cuentan en «N filas». Para no
verlas se usa el filtro de la columna «Enviado».

## 4. Qué necesita del servidor

| Para | Cambio |
|---|---|
| §3.1 | `GET /api/pedidos/filas` añade `canal` a cada fila: el de `Pedido_fila` en las de ShippyPro; en el histórico, `AMAZON` o `AMAZON_STOCKROOM` si la plataforma es una de las dos cuentas, y `null` en las demás. Si la carga del histórico ya guarda ese canal, basta con servirlo. |
| §3.5 | `GET /api/pedidos/filas/{id}/cambios` (SUPERTECNICO y ADMIN): los cambios de `Pedido_fila_cambio` de esa fila, del más reciente al más antiguo: usuario, fecha y hora, IMEI anterior e IMEI nuevo. En una fila del histórico, lista vacía. Se pide al abrir el panel y se vuelve a pedir tras guardar un IMEI en esa fila. |
| §3.2, §3.3, §3.4, §3.6, §3.7 | Nada: salen de lo que la API ya sirve (`estado`, `nivel`, `editable`, `caja`, `skuMovil`, `motivo` y los datos del envío). |

Todo el filtrado sigue en el cliente sobre lo recibido. Ninguna petición nueva pone en riesgo el límite de 15 s.

## 5. Un riesgo que el plan tiene que medir

Con el histórico cargado, «Este año» y «Todo» traen decenas de miles de filas a una tabla sin páginas. «Último mes», el periodo
por defecto, ronda las 2.000 y va bien hoy. El plan debe medir en preprod, con el histórico real, la petición (límite de 15 s) y
la fluidez de la tabla con el periodo más largo. Si no aguanta, la salida preferida es pintar solo las filas visibles a partir
de cierto número; su coste es que el Control+F del navegador deja de ver las filas no pintadas (el buscador de la pantalla sí
las ve), y eso se le consulta al usuario antes de hacerlo.

## 6. Fuera de esta entrega

- El salto automático a la siguiente fila sin IMEI tras guardar (§2).
- **Enviados sin IMEI:** no deberían darse. Siguen contando en «sin IMEI», sin aviso propio. Ideas del usuario para después:
  validar que haya IMEI antes de sacar la etiqueta de envío, y avisos más visibles (pintar la fila o una vista de
  notificaciones).
- Filtros guardados por cada usuario y casillas por cliente en el filtro de Plataforma.
- Columnas y datos nuevos (§1): otra spec.

## 7. Cómo se comprueba

- El filtro de Plataforma ofrece tiendas y no clientes; marcar una deja sus filas con el nombre del cliente en la columna.
- Cada contador filtra lo que dice y su número coincide con las filas que deja; la ✕ de cada etiqueta quita solo ese filtro.
- Una fila enviada se lee en texto normal con su etiqueta; una del histórico, en gris, y su celda de IMEI no se abre.
- El panel enseña los cambios de IMEI de una fila con varios, en orden, y se actualiza al guardar otro.
- Tras abrir una celda y cerrarla, la raya azul sigue en esa fila al filtrar y al llegar filas nuevas.
- El TECNICO recibe `403` en la consulta nueva.
- En preprod, con el histórico cargado: las medidas de §5.
