# 0.9.13 — Casillas en las acciones de bloque y «Exportar bloque en CSV»

Fecha: 2026-10-14. Estado: diseño aprobado con el prototipo, pendiente del plan.

**Solo web.** Parte de la web `main` `072fb27`, la 0.9.11. La 0.9.12 (navegación por grupos) la lleva otra sesión en `wt/web-0912`. Las dos versiones se coordinan al pasar por preproducción, que es compartida.

Prototipo, con datos inventados y probado con Playwright: `assets/2026-10-14-v0913-prototipo-casillas-bloque.html`.

## 1. Problema y decisión

Tras la 0.9.9, el usuario pide pulir Pedidos.

- **Las acciones de bloque son todo o nada.** Si en un bloque de 39 líneas en camino llegan 36, «Recibir todo» no sirve: hay que recibir las 36 una a una. Lo mismo pasa al confirmar o al cancelar.
- **Exportar un bloque es lento.** Hay que filtrar hasta ver solo ese bloque y luego usar «Descargar CSV».

Lo decidido:
- **Casillas** en los diálogos de «Confirmar bloque», «Recibir todo» y «Cancelar bloque», para desmarcar las líneas que no deben cambiar.
- **«Exportar bloque en CSV»** en el menú del bloque.
- **El CSV general no cambia.** Ya lleva «Bloque» y «Nota del bloque»; se descartan las filas separadoras.
- **Las parciales se quedan fuera de las acciones de bloque, como hoy.** Con una parcial, «recibir» puede querer decir recibir el resto o cerrarla sin resto, y eso se decide en su fila. Se valoró incluirlas y se descartó por confuso.

**El servidor no cambia.** `BloqueCompraService.aplicar` ya aplica la acción solo a las líneas de la lista que manda la web. Solo «Borrar» exige el bloque entero (`comprobar(…, exigirTodas)`).

**Fuera de esta versión:** «Añadir otro» dentro del «Nuevo pedido» y en el menú del bloque, con los otros dentro del bloque de su proveedor. Toca base de datos y servidor, y tendrá su propia spec y su prototipo.

## 2. Casillas en las acciones de bloque

Afecta a `AccionBloqueDialog`, con las acciones `confirmar`, `recibir` y `cancelar`. **«Borrar bloque» no cambia**: sin casillas, porque borra el bloque entero.

**Lista «cambian»:**
- Son las mismas líneas de hoy (`lineasQueCambian`): pendientes al confirmar, en camino al recibir o cancelar.
- Al abrir, **todas marcadas**.
- Cada línea lleva una casilla con `aria-label` «{tipoComponente} (ID {idCompra})».
- La cabecera de la lista lleva una casilla «Marcar todas» (`aria-label`). Está marcada si lo están todas, a medias (`indeterminate`) si lo están algunas, y desmarcada si no hay ninguna. Al pulsarla, si estaba marcada desmarca todas; si no, marca todas.
- El título cuenta las marcadas:
  - con todas marcadas, como hoy: «Se reciben (suman al stock) (39)»;
  - si no: «Se reciben (suman al stock) (36 de 39)».

**Línea desmarcada:**
- se queda en su sitio, en gris y con el nombre tachado;
- en lugar de su estado lleva «se queda en camino» (recibir y cancelar) o «se queda pendiente» (confirmar);
- se vuelve a marcar con su casilla.

**Aviso bajo la lista**, si hay desmarcadas (`role="status"`):

| Acción | Una | Varias |
|---|---|---|
| Recibir, cancelar | «Se queda en camino 1 línea: la desmarcada no cambia.» | «Se quedan en camino {n} líneas: las desmarcadas no cambian.» |
| Confirmar | «Se queda pendiente 1 línea: la desmarcada no cambia.» | «Se quedan pendientes {n} líneas: las desmarcadas no cambian.» |

**Botón:**
- cuenta las marcadas con el texto de hoy: «Confirmar ({n})», «Recibir ({n})», «Cancelar líneas ({n})»;
- deshabilitado con ninguna marcada.

**Lo que no cambia:**
- La lista «No cambian», con su motivo por línea (`motivoNoCambia`), incluidas las parciales: «parcial (1/4): usa «Recibir resto» en su fila» y «parcial: usa «Cerrar sin resto» en su fila».
- Los textos del menú («Recibir todo ({n})»…), que siguen contando todas las líneas que pueden cambiar.

**Enviar:**
- el cuerpo lleva solo las marcadas (`vistaDe`), en el orden del bloque;
- los errores se tratan como hoy (`tratarFalloBloque`): con un 409, recarga, cierra y avisa.

## 3. «Exportar bloque en CSV»

**Menú del bloque** (`entradasMenuBloque`, ⋮ y clic derecho en la cabecera):
- entrada nueva «Exportar bloque en CSV», acción `exportar`;
- va justo debajo de «Añadir nota…» / «Editar nota…», antes del separador de «Cancelar bloque»;
- nunca está deshabilitada, y vale también para bloques terminados y antiguos.

**Quién la ve:**
- Hoy el menú del bloque solo sale al supertécnico (`puedeEditar`).
- Desde esta versión sale a **todos los que ven Pedidos**: el admin y el técnico ven el ⋮ y el clic derecho solo con «Exportar bloque en CSV»; el supertécnico, con todas las entradas.
- El grupo «Sin bloque» sigue sin menú.

**Qué descarga:**
- **todas las líneas del bloque** (`Bloque.lineas`), aunque haya filtros o búsqueda, en el orden en que salen en el bloque;
- las mismas cabeceras y filas del CSV de Componentes (`CABECERAS_CSV_PEDIDOS`, `filaCsvPedido`);
- nombre de fichero `pedidos_bloque_{idBloque}_yyyy-MM-dd_HH-mm.csv`, con `descargarCsv('pedidos_bloque_{idBloque}', …)`;
- se llama en el mismo clic del menú, sin nada asíncrono antes, porque la ventana «Guardar como» exige un gesto reciente (doc de `descargarCsv`).

## 4. Pruebas

**Funciones puras (`reglas.test.ts`):**
- `entradasMenuBloque` con la entrada `exportar` en su sitio, habilitada también en un bloque terminado;
- sin `puedeEditar`, solo la entrada `exportar`.

**Diálogo (`dialogos.test.tsx`, Vitest + MSW):**
- abre con todas marcadas y el botón con el total;
- desmarcar una cambia el título («36 de 39»), el botón, el aviso y el texto de la línea;
- «Marcar todas»: a medias, desmarcar todas (botón deshabilitado) y volver a marcar;
- el cuerpo del POST lleva solo las marcadas;
- confirmar con una desmarcada dice «se queda pendiente»;
- «Borrar bloque» sin casillas.

**Página (`PedidosPage.bloques.test.tsx`):**
- «Exportar bloque en CSV» llama a `descargarCsv` con `pedidos_bloque_{id}` y todas las líneas del bloque aunque haya un filtro que oculte alguna;
- el admin ve el ⋮ solo con esa entrada.

**E2E (`bloques.spec`)**, con datos sintéticos y la limpieza de siempre:
- «Recibir todo» con una línea desmarcada: esa línea sigue en camino y las demás pasan a recibidas;
- «Exportar bloque en CSV» descarga `pedidos_bloque_{n}_…csv` solo con las líneas de ese bloque, por descarga directa como la prueba del CSV general.

## 5. Despliegue y documentación

**Despliegue:**
- Solo la web: preproducción con las E2E de siempre y luego producción.
- Va **en horario**: el corte es despreciable, no hay migración y la vuelta atrás es simple.
- Se coordina con la 0.9.12 si las dos están a la vez en preproducción.

**Documentación:**
- `package.json` pasa a 0.9.13;
- CHANGELOG `[0.9.13]`;
- `docs/novedades/NOVEDADES-v0.9.13.md` en la raíz;
- la ficha de paridad `docs/paridad/pedidos.md`.

## 6. Fuera de alcance

- Las parciales en las acciones de bloque (§1).
- Filas separadoras o de total en el CSV general.
- «Añadir otro» y los otros dentro de los bloques: spec aparte.
