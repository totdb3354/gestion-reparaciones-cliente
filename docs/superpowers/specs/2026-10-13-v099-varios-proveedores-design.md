# 0.9.9 (ajuste) — Varios proveedores en un mismo «Nuevo pedido»

Fecha: 2026-10-13. Estado: diseño aprobado con el prototipo, pendiente del plan.

Va **dentro de la 0.9.9**, antes de producción. Hoy están el servidor `main` `f00761c` y la web `main` `343217b`, validados en preproducción.

**Solo web.** El servidor ya guarda en un solo lote líneas de varios proveedores y resuelve el destino de cada uno (`CompraLoteService`: `bloquePorProveedor`, `destinos` por proveedor). La web ya recibe en cada pieza los proveedores que la venden (`Componente.proveedores`, de «Piezas que vende»).

Prototipo, con datos inventados y probado con Playwright: `assets/2026-10-13-v099-prototipo-varios-proveedores.html`.

## 1. Problema y decisión

- Hoy (spec `2026-10-12-v099-un-proveedor-por-pedido`) cada «Nuevo pedido» es para un solo proveedor.
- Cuando hay que pedir una cosa a un proveedor, otra a otro y otra a un tercero, hay que abrir y cerrar el diálogo tres veces.
- Las piezas llegan **de todo un poco**: a mano, de «Pedir todas las piezas» de la campana, de «Pedir» en Stock o de la previsión de varios proveedores.

Se compararon tres formas: recuadros por proveedor, pestañas, y «Confirmar y pedir a otro». El usuario probó las pestañas y eligió **ver todo en la misma vista**:
- **un recuadro por proveedor**;
- **cada recuadro es un bloque**.

La regla «un proveedor = un bloque» no cambia, y sigue el estándar de Odoo: una orden de compra por proveedor.

## 2. El diálogo

**Al abrir vacío** (ajuste del 2026-10-13, opción A, que sustituye a la pantalla «¿A qué proveedor pides?»):
- un primer recuadro, `aria-label` «Pedido sin proveedor», con el desplegable «Proveedor» («Elige el proveedor…») en su cabecera;
- la tabla vacía con «Elige el proveedor para añadir líneas.» y «Añadir previsión» deshabilitado;
- al elegir uno, ese recuadro pasa a ser el del proveedor;
- sin texto de ayuda: el botón se entiende solo.

**Recuadros:**
- uno por proveedor, apilados **en el orden en que se añaden**, con el nuevo al final (ajuste del 2026-10-13; antes iban por nombre);
- los de la precarga entran en el orden de sus líneas;
- el cuerpo del diálogo se desplaza y el pie con los botones queda fijo.

**Cabecera de cada recuadro:**
- ▼ / ▶ para plegarlo o desplegarlo; plegado, la cabecera sigue enseñando líneas y destino;
- el **nombre del proveedor**, «· {n líneas} · {divisa}»;
- «→» y el **destino**: un desplegable «Añadir a Bloque {N} ({m líneas})» / «Bloque nuevo» si tiene uno abierto. Si no, «**Bloque nuevo** (no tiene ninguno abierto)». Son las opciones y la regla de hoy (`opcionesDestino`, `destinoElegido`);
- **«Añadir previsión ({n})»**, que es la «Añadir previsión de X» de hoy para ese proveedor, con la cantidad del §4;
- **×** «Quitar {Proveedor}». Si el recuadro tiene líneas, pregunta antes «¿Quitar {Proveedor} y sus {n} líneas?», o «¿Quitar {Proveedor} y su línea?» con una.

**Cuerpo de cada recuadro:**
- **Tabla:** la de hoy, con Componente, Cant., P. unidad en la divisa del proveedor, Urg. y quitar. La numeración de líneas es **por recuadro**.
- Debajo, «+ Añadir línea».
- Si alguna pieza va también a otro proveedor, un aviso «{pieza} también va a {Proveedor} ({cant}).» (§5).

**«+ Otro proveedor», en el pie del diálogo** (ajuste del 2026-10-13):
- va a la izquierda de «Cancelar» y «Confirmar pedido», siempre visible y con su mismo aspecto, el de botón secundario;
- deshabilitado mientras no hay ningún proveedor elegido, o si ya están todos;
- lista los proveedores activos que aún no tienen recuadro, cada uno con el detalle «Bloque {N} abierto» o «sin bloque abierto»;
- al elegir uno, su recuadro sale al final, desplegado, y la caja baja hasta él.

**Botón de confirmar:**
- «Confirmar pedido» con un recuadro con líneas;
- «**Confirmar pedido ({n} bloques)**» con más de uno.

**Lo que desaparece:**
- el combo «Proveedor general» (`MSG_SIN_PROVEEDOR`, `aplicarProveedorATodas`);
- el recuadro «Bloques» del pie (`ResumenBloques`): el destino va ahora en la cabecera de cada recuadro.

## 3. Reparto de las piezas que llegan precargadas

Afecta a «Pedir todas las piezas» de la campana, a «Pedir» de Stock (precarga `componentes`) y a las solicitudes de técnicos (precarga `solicitudes`).

**Reparto automático:**
- si la pieza (su master) la vende **un solo** proveedor activo según `proveedores`, entra directamente en el recuadro de ese proveedor;
- si la venden **varios o ninguno**, entra en el recuadro **«⚠ Por repartir»**.

**Recuadro «⚠ Por repartir»:**
- va el primero, en ámbar, con los colores `pend-*`;
- texto: «⚠ Por repartir · {n líneas} · las venden varios proveedores o ninguno: elige a quién se las pides»;
- las filas son **las de siempre** y se pueden editar: componente, cantidad, precio, urgente y papelera. Ajuste del plan: así no hace falta otro tipo de fila, y una pieza sin la lista «Piezas que vende» también se puede tocar;
- en la columna Proveedor, el desplegable «**Pasar a…**» con dos grupos: «Lo vende» (los que tienen la pieza en su lista) y «Otros proveedores».
- **Al pasar una línea:**
  - va al recuadro de ese proveedor, que se crea si no existe;
  - si ese recuadro ya tenía la pieza, se queda la línea que había y sale «{pieza} ya estaba en {Proveedor}: se queda la línea que había.»;
  - cuando no queda nada por repartir, el recuadro desaparece.

**Cantidades:** las de hoy.
- `componentes`: su «Pedir 60 d», puesto con la lista **fresca** (`ponerCantidadesPrevistas`).
- `solicitudes`: tantas como solicitudes.
- Las omitidas se cuentan y avisan como hoy.

## 4. Cantidad: lo que falta

**Lo que falta** de una pieza en un recuadro es el «Pedir 60 d» del master menos lo que ya va a **otros proveedores en este mismo pedido**, y nunca menos de 0. El «Pedir 60 d» ya descuenta lo pendiente de los bloques guardados.

| Momento | Cantidad |
|---|---|
| Elegir la pieza de una línea | Si la cantidad sigue siendo la automática, pasa a **lo que falta** si es > 0; si no, a 1. Automática = el «1» de entrada, o lo que faltaba de la pieza anterior: es la regla «Recalcular» del 2026-10-13, ahora con lo que falta. Si se cambió a mano, no se toca. Volver a elegir la misma pieza no cambia nada. |
| «Añadir previsión ({n})» de un recuadro | Las piezas de hoy (marcadas en Auto que vende ese proveedor) **con lo que falta > 0**: línea nueva con lo que falta, o la existente sube a máx(la suya, lo que falta). El contador {n} cuenta esas piezas. |
| Precarga `componentes` | Lo que falta. Como una pieza precargada va a un solo recuadro, es su «Pedir 60 d». |

Ejemplo del usuario, una pantalla con «Pedir 60 d» 7:
1. En el Proveedor A sale 7 y se cambia a 3.
2. En el Proveedor B, al elegirla, sale **4**.
3. Al confirmar, 3 van al bloque de A y 4 al de B.

## 5. Piezas repetidas

- **Dentro de un recuadro**, una pieza no se repite. Es la regla del 2026-10-13: la línea nueva desaparece, queda seleccionada la que ya la tenía y sale el aviso. El aviso lleva ahora el proveedor: «{pieza} ya está en el pedido a {Proveedor} (línea {n}).».
- **En recuadros distintos** sí puede ir, porque es pedir parte a cada uno. Cada recuadro con esa pieza avisa, sin bloquear: «{pieza} también va a {Proveedor} ({cant}).».

## 6. Confirmar

Las comprobaciones van en este orden. Si una falla:
- el error sale en el pie;
- el recuadro que falla se **marca en rojo**, se **despliega** si estaba plegado y se pone **a la vista**.

1. **Algo por repartir:** «Quedan {n} líneas por repartir: elige a quién se las pides.». Con una: «Queda 1 línea por repartir: elige a quién se la pides.».
2. **Ninguna línea en ningún recuadro:** `MSG_SIN_LINEAS`, «Añade al menos una línea.».
3. **En cada recuadro, por orden de nombre:** las validaciones de línea de hoy (`validarLineasCompra`), con el proveedor delante: «{Proveedor} · Línea {n}: selecciona un componente.».

**Recuadros sin líneas:** no cuentan. No crean bloque ni dan error.

**Guardado:**
- Un único `POST` del lote, como hoy, con la clave de idempotencia de hoy.
- Lleva las líneas de todos los recuadros, cada una con su proveedor, y los destinos por proveedor (`destinosDelCuerpo`).
- Los errores se tratan como hoy, por ejemplo el 409 de un destino que ya no existe: recarga los pedidos y el diálogo sigue abierto.

**Cancelar** cierra sin preguntar, como hoy. **Atrás** del navegador con líneas pide «Descartar» (`textoDescartar`), contando las de todos los recuadros y las de «Por repartir». Esto también es como hoy; el prototipo preguntaba al cancelar y era un error.

## 7. Lo que no cambia

- **«Añadir líneas…» y «Añadir previsión…» del menú de un bloque** (modo `bloque`): un solo recuadro, el de ese proveedor, con el destino fijado. No tiene «+ Otro proveedor» ni ×.
- **«Nuevo otro pedido»** (conceptos libres): no se toca.
- **Lo que ya se hacía en cada línea:** cantidad, precio, urgente, la cantidad prevista fresca y C25, que es no autoenfocar las líneas precargadas.

## 8. Pruebas y despliegue

**Funciones puras:**
- reparto automático: uno, varios o ningún proveedor; slave a su master;
- lo que falta;
- cantidad al elegir pieza con otros recuadros;
- previsión por recuadro con lo que falta;
- pasar a otro proveedor, con y sin repetida;
- validación con el proveedor delante y en orden.

**Diálogo (Vitest + MSW):**
- abrir vacío y elegir proveedor;
- «+ Otro proveedor»;
- plegar;
- la × con pregunta;
- «Por repartir» y «Pasar a…»;
- el caso 3 + 4;
- confirmar con 2 bloques: el cuerpo lleva las líneas de los dos proveedores y los dos destinos;
- error en un recuadro plegado.

**E2E:**
- **`pedidos.spec`:** se adapta, porque el proveedor ya no se elige en «Proveedor general».
- **`bloques.spec`:** se adapta. El bloque de dos líneas se monta como hoy, con «Nuevo pedido» y «Añadir líneas…», porque cada proveedor de prueba es único.
- **Caso nuevo:** «Nuevo pedido a dos proveedores crea dos bloques», con dos proveedores sintéticos y limpieza.

**Despliegue y documentación:**
- **Preproducción:** solo la web, con las E2E de siempre y la limpieza 2c ampliada si el caso nuevo crea un segundo proveedor sintético.
- **Producción:** `prod-v099.md` cambia el commit de la web.
- **Documentación:** CHANGELOG `[0.9.9]`, `NOVEDADES-v0.9.9.md` y las fichas de paridad de Pedidos.
  - Se reescribe la viñeta de «Un proveedor por pedido».
  - La regla de un proveedor por **bloque** se queda.

## 9. Fuera de alcance

- Los CSV de Pedidos, que se revisan después con el usuario.
- Mover una línea de un recuadro a otro una vez puesta. Se quita y se añade en el otro.
- Repartir una línea de «Por repartir» entre dos proveedores de una vez. Se pasa a uno, se baja su cantidad y en el otro sale lo que falta.
