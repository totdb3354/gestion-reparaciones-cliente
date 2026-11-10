# Novedades de la versión 0.9.29

---

## 📦 Pedidos de clientes (supertécnicos y administrador)

- Pantalla nueva **«Pedidos»**, en el grupo Teléfonos. Enseña los pedidos pendientes de **Amazon, Amazon Stockroom, las webs
  (Distribuidores, Particulares, BackiPhone) y el Simulador**, los más antiguos arriba, con una línea por cada móvil que piden.
- De cada uno dice **si ya tiene móvil (IMEI)** y **si ese móvil cuadra con lo que pide el pedido**.
- **Nada cambia en cómo trabajáis:** los IMEI se siguen tecleando donde hoy, en **PEDIDOS 2026** o en el **Simulador**.
  «Pedidos» solo lee y avisa; no guarda nada.
- Arriba, la **píldora de fuentes** (verde, ámbar o roja; ShippyPro, PEDIDOS 2026, el Simulador y NSYS) dice cuándo se leyó cada una y
  si alguna falló. Si está ámbar o roja,
  lo que ves puede estar desfasado.
- Pulsa una unidad para ver lo que **pide** el pedido, el móvil, dónde lo tecleaste y, si no cuadra, qué es lo que falla.
  **«Ver rastro»** abre la ficha del IMEI y **«← Volver»** te deja la pantalla como estaba (filtros y unidad elegida).
- Arriba hay buscador (nº de pedido, cliente, SKU o IMEI) y filtros por canal y por estado. Los pedidos del Simulador de más
  de 14 días solo salen con el interruptor «Ver también los del Simulador de más de 14 días».

## 🚦 Qué significa cada estado

- **Sin móvil**: nadie ha tecleado todavía un IMEI para esa unidad. Tecléalo en PEDIDOS 2026 (o en el Simulador).
- **No cuadra**: el móvil no sirve tal cual. Una tabla compara lo que pide el pedido con lo que es el móvil (modelo, capacidad,
  eSIM, color y grado) y dice si el taller lo puede cambiar. Hay que cambiar el IMEI si falla el modelo, la capacidad o la
  eSIM, o si **no está en NSYS** o es un grado que no se vende (ID en Amazon, F, FD, RMA). Si es solo el color o el grado,
  **necesita taller**. Un C sin reparar tampoco se vende.
- **En el taller**: el móvil tiene un trabajo abierto; dice con quién está y **en qué SKU tiene que acabar**. No es una alarma.
- **Cuadra**: el móvil es lo que pide el pedido.
- **Con aviso**: cuadra, pero con una pega: es un **Ms**, o un **B para un pedido de A** (solo Amazon).
- Además, si el mismo IMEI está tecleado en dos pedidos abiertos, ambos llevan en la lista el aviso **«IMEI en dos pedidos»** y el
  detalle dice **«Este IMEI está tecleado en dos pedidos.»**

## 🛒 Amazon: «sin pedido concreto»

- En PEDIDOS 2026, **AMZ** es Amazon y **FNSK** o **FSNK** es Amazon Stockroom. Cada cuenta solo usa sus filas.
- Si la fila tiene el **número de «Pedido Amazon»** (se rellena al enviar), es de ese pedido mientras siga pendiente; si ya no
  está pendiente, esa fila no cubre ningún otro.
- Las filas **sin ese número** no dicen a qué pedido van, así que se reparten por SKU: **el móvil más avanzado va al pedido
  más antiguo**. Esas unidades llevan la etiqueta **«sin pedido concreto»**; el reparto puede cambiar si llega otro IMEI.
- **Amazon distingue eSIM y SIM física**: un pedido de un iPhone eSIM solo se cubre con un móvil eSIM, y uno de SIM física con uno
  de SIM física.

## 🔧 Asignaciones y Pendientes

- Debajo del IMEI, en gris pequeño: **«Pedido 402-0000000-0000001 · acabar en {SKU}»**, para saber a qué cliente va el móvil y
  en qué tiene que quedar. Si el IMEI está en dos pedidos, avisa **«IMEI en dos pedidos»**.
- Solo sale en los trabajos abiertos. El resto de la pantalla no cambia.

## 🔤 Stock

- El **«Pedidos»** del grupo Stock (las compras de piezas a proveedores) pasa a llamarse **«Compras»**, para no confundirlo con
  el nuevo. Está en el mismo sitio y los marcadores siguen valiendo.

---

Todavía **no** sugiere qué móvil poner en cada pedido: eso llegará más adelante.
