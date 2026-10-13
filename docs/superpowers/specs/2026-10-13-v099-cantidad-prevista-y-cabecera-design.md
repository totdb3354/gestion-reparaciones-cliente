# 0.9.9 (ajuste) — Cantidad prevista en «Nuevo pedido», aviso de precio en la cabecera e icono de nota

Fecha: 2026-10-13. Estado: aprobado e implementado (plan `docs/superpowers/plans/2026-10-13-v099-cantidad-prevista-y-cabecera.md`); regla del cambio de pieza ajustada tras la revisión final.
Va **dentro de la 0.9.9**: servidor `main` `f00761c` y web `main` `4f0a863`, validada en preproducción, sin desplegar en
producción ni etiquetar. **Solo web**: sin cambios en el servidor ni en la base. Prototipo de la cabecera:
`assets/2026-10-13-v099-prototipo-cabecera-bloque.html`.

## 1. Cantidad prevista y piezas repetidas en «Nuevo pedido»

**Hoy:**
- Una línea nueva entra con cantidad 1, y elegir la pieza no la cambia.
- «Añadir previsión» no duplica: si la pieza ya está, sube su cantidad.
- Al revés sí se duplica. Si se añade la previsión y luego, a mano, una línea con una pieza que ya estaba, quedan dos líneas de la misma pieza y el bloque la lleva dos veces.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Cantidad al elegir la pieza de una línea | Su **«Pedir 60 d»** (`pedir60` del master) si es > 0 y, si no, 1, siempre que la cantidad de la línea siga siendo la **automática**: el «1» de entrada o la prevista de la pieza anterior. Ajuste del 2026-10-13 tras la revisión final («Recalcular»): si se elige A (16) y se corrige a B, B toma la suya. Si se cambió a mano, no se toca. Volver a elegir la misma pieza no cambia nada. Vale para cualquier pieza con previsión, en Auto o en Manual. |
| «Pedir» desde Stock y «Pedir todas las piezas» desde la campana (precarga `componentes`) | Cada pieza entra con su «Pedir 60 d» si es > 0; si no, con 1. Las líneas salen al momento con 1 y la cantidad se pone **cuando llega la lista recién cargada**, no con la de la caché, que puede ser vieja y duplicar un pedido. Es el mismo criterio que «Añadir previsión» del bloque. |
| Solicitudes de técnicos (precarga `solicitudes`) | **Sin cambios**: tantas unidades como solicitudes. |
| Pieza repetida | Si la pieza elegida en una línea ya está en **otra** línea del pedido, se **junta**: la línea que se estaba rellenando desaparece, queda **seleccionada** la que ya la tenía (su cantidad no cambia) y sale el aviso «{pieza} ya está en el pedido (línea {n}).». Lo mismo si se cambia la pieza de una línea a otra que ya estaba. |

**Detalles:**
- `{pieza}` es el texto de la opción del buscador, el de `nombreGrupo`; `{n}` es el número de la línea que queda.
- El aviso va en la línea de información del diálogo, como los de la previsión, y se borra al tocar las líneas.
- Funciona igual en el modo «Añadir líneas» de un bloque.
- Para seleccionar la línea que queda, `DialogoLineas` recibe una petición de selección, porque hoy la selección es interna.

## 2. Aviso de líneas sin precio en la cabecera del bloque

- Chip ámbar **«! N sin precio»**: el `fila-solicitud-bg` y el `fila-solicitud-brd` del «⚠» y del urgente en camino. Va justo detrás de los chips de estado.
- N = las líneas del bloque que hoy llevan el «!» en su fila: **recibidas** con P.Unit. 0 o con EUR 0 (`marcaPrecioCero || marcaTotalCero`).
- Las pendientes o en camino a 0,00 **no cuentan**: el precio se puede poner antes de recibir.
- Tooltip: «N líneas recibidas sin precio: ponlo con «Editar».» (o «1 línea recibida…»).
- Sin líneas así, no sale nada.

## 3. Icono de la nota

- En la cabecera del bloque, el emoji «📝» se cambia por el icono **`MessageSquareText` de lucide-react**, la misma familia que la flecha de los desplegables. Va a 14 px, en `text-azul-gris` y decorativo (`aria-hidden`).
- El texto de la nota y su tooltip no cambian.

## 4. Pruebas y despliegue

- **Funciones puras (`formulario/lineas.ts`):**
  - cantidad prevista al elegir una pieza: con `pedir60` > 0, con 0 o nulo, y con la cantidad ya cambiada;
  - pieza repetida: se junta, devuelve la línea que queda y el aviso;
  - precarga `componentes` con la cantidad prevista y `solicitudes` igual que antes.
- **`NuevoPedidoDialog`:**
  - elegir una pieza pone su cantidad;
  - elegir una repetida deja una sola línea, seleccionada, con el aviso.
- **`CabeceraBloque`:**
  - el chip con recibidas a 0, y ninguno con pendientes a 0 ni sin líneas así;
  - el icono de la nota sin el emoji.
- **E2E:**
  - Una pieza ya no se repite en un mismo diálogo. Por eso los E2E de bloques montan el bloque de dos líneas del SKU de prueba con «Nuevo pedido» y luego «Añadir líneas…».
  - Escriben la cantidad 1 a mano para no depender de la previsión del SKU en preproducción.
- **Preproducción:** solo la web, con las E2E de siempre. **Producción:** el guion `prod-v099.md` cambia el commit de la web.
- **Documentación:** CHANGELOG `[0.9.9]`, `NOVEDADES-v0.9.9.md` y las fichas de paridad de Pedidos.

## 5. Fuera de alcance

Los CSV de Pedidos, que se revisan después con el usuario.
