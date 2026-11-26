# Reserva web: un n/a vale para un pedido A o A100 (0.9.49)

Fecha: 2026-11-26 · Estado: diseño aprobado en conversación, pendiente de revisar por escrito · Versión prevista: 0.9.49

## 1. Por qué

Con la regla «n/a como A» el ciclo de stock publica en las tres webs (Distribuidores, Particulares, BackiPhone) y en la lista pública EU
**una unidad A o A100 sacada de los n/a** (regla «n/a como A»; diseño en el repo de automatización,
`docs/stock/2026-11-26-publicar-na-como-a-diseno.md`). Cuando esa unidad se vende por la web, el pedido llega por ShippyPro y la
**reserva de IMEI** (0.9.48) tiene que poder darle un n/a; si no, pasa esto:

1. La fila se queda «sin stock»: por definición de la regla no hay A ni A100 con grado del que tirar.
2. Sin reserva, el bot no lleva ningún móvil a «Pedidos realizados» y NSYS sigue contando los mismos n/a.
3. El ciclo vuelve a publicar 1 unidad a los 10 minutos: el «de uno en uno» no frena nada.

Hoy `Reparto.puestoWeb` no acepta nunca un n/a (spec de la reserva §4.2: «Los pedidos web de N/A no existen hoy»).

El recomendador de la celda **ya** propone los n/a (nivel 3, «hay que transformarlo»): no cambia.

## 2. La regla nueva (tabla 4.2 de la reserva)

| Puesto | Teléfono | Cuándo | Formato | Color |
|---|---|---|---|---|
| 1 | El SKU exacto del pedido (como hoy) | siempre | el del pedido | el del pedido |
| **2** | **N/A** | pedido **A o A100** | el del pedido | el del pedido |
| 3 | B o C (antes puesto 2) | pedido A o A100 | el del pedido | el del pedido |

- **N/A** es el grado que cuenta de la reserva (§3): el `n/a` de NSYS, y también los móviles **sin caja** o en **Devolución**
  sea cual sea su grado (están por revisar). Hoy esos «N/A por caja» se excluyen de la web; pasan a valer en el puesto 2.
- **Por qué el N/A va delante del B o C** (Imad, 26/11): un n/a puede salir A al revisarlo y ahorrarse la reparación; un B o C
  la necesita siempre. Es el mismo orden que Amazon (su puesto 1 ya es «encaja tal cual o N/A»; el B o C va en el 2).
- **Pedido A o A100** es lo que ya decide `pedidoA` (A, A+, A100…; nunca A- ni A-100).
- **Mismo modelo, capacidad, formato (eSIM o física) y color**, siempre. Nunca Ms, ID, F, FD ni RMA (como hoy).
- **Con un trabajo del taller abierto:** solo el puesto 1, como hoy (no se le cambia el trabajo al técnico).
- **Dentro del puesto**, el orden de §4.3 sin cambios: caja (Listos → … → Devolución = En revisión), grado, antigüedad, IMEI.

## 3. Lo que no cambia

- Amazon (tabla 4.1), el orden de las filas (§2), el ciclo de vida de la reserva (§5), el bot y el recomendador.
- Si el n/a reservado resulta no valer al revisarlo (un F, por ejemplo), la reserva no se suelta sola por el grado: una persona
  cambia el IMEI en la celda, como con cualquier reserva hoy.

## 4. Cambios

- **Servidor:** `Reparto.puestoWeb`: el filtro `g.naPorCaja()` deja de excluir; puesto 1 sigue pidiendo el SKU exacto (un N/A por
  caja no es exacto: su SKU es de un grado sin revisar); puesto 2 = `pedidoA && g.na()`; puesto 3 = `pedidoA && g.bOC()`. El texto
  del motivo («por qué») del N/A, como en Amazon.
- **Tests (`RepartoTest`):** n/a mismo color y formato → 2 para A y A100; nulo para A- y A-100; otro color u otro formato → nulo;
  B y C → 3; A sin caja o en Devolución → 2 (antes nulo: cambian los dos asserts de las líneas 302-303); n/a con trabajo abierto →
  nulo; Ms/ID en Devolución siguen nulos; en un reparto con n/a y B del mismo color para una fila A, se elige el n/a.
- **Web:** solo el número de versión (0.9.49) y la línea de novedades. La ayuda de Pedidos que explique los puestos web, si la
  hay, añade el N/A.
- **Docs:** la spec de la reserva (`2026-11-24-pedidos-reserva-imei-design.md` §4.2) apunta a esta.

## 5. Pruebas y despliegue

1. `mvn test` del servidor (incluye el test de arranque) y `npm test` de la web.
2. Preprod: E2E con datos sintéticos por API (fila web A con un n/a del mismo color en stock → se reserva el n/a; con un B además
   → sigue siendo el n/a; fila A- → no reserva el n/a) y limpieza.
3. Producción **antes** de activar la regla del ciclo de stock (si fuera al revés, habría ventas sin reserva). El corte es de
   segundos y no hay migración: en horario.
