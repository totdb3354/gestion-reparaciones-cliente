# 0.9.20 — Panel flotante «Carga de técnicos» en directo

Fecha: 2026-10-20. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va en la **0.9.20 de la web** (la 0.9.19, «Completar pulidos», ya está en `main` de la web). **Solo web**: sin cambios en el
servidor ni en la base; funciona con el servidor 0.9.18.
Prototipo aprobado: `assets/2026-10-20-v0920-prototipo-panel-carga.html` (versión 2: forma C, sin pie, con recuerdo).

## 1. Qué cambia

**Hoy** «Carga técnicos» (botón de Asignaciones) abre `CargaTecnicosDialog`: una ventana modal que tapa el ERP, pide la carga
una sola vez al abrirse y no se refresca. Mientras está abierta congela además el sondeo de la tabla de Asignaciones (D4).

**Ahora** es un **panel flotante que no bloquea**: se ve en cualquier pantalla del ERP mientras se trabaja, se puede dejar
abierto todo el día y se mantiene al día solo.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Forma | Flotante, se arrastra por la cabecera y recuerda el sitio (variante «C» del prototipo). |
| Minimizar | «–» lo deja en una **pastilla**: «Carga» + mini-barras de colores + «N al 90 %+» (rojo; gris con 0). Un clic la abre; también se arrastra. |
| Actualización | Lo que hace el usuario desde su navegador, **al momento**; lo demás, **cada 30 s**. |
| Pie | **Ninguno**: ni hora ni contador. Lo que importa (la primera barra) cambia al momento con las asignaciones propias; la verde puede llegar a los 30 s. |
| Pulsar un técnico | Lleva a Asignaciones con el filtro de ese técnico en solitario; el panel sigue abierto. |
| Al volver a entrar | Como lo dejó: abierto/minimizado/cerrado, posición y Pedidos/Total, guardado en el navegador. |
| Ratón encima | El panel no cambia mientras el ratón está encima (las filas no se mueven bajo el cursor). |

**Qué mueve cada barra** (cálculo del servidor, sin cambios, `CargaTecnicos.java`):
- Cuando un técnico **termina** un trabajo, ese trabajo pasa de pendiente a hecho con el mismo peso: la **primera barra no
  cambia**, solo avanza la verde.
- La primera barra cambia al **asignar, reasignar o borrar** (al momento si lo hace el usuario), y también, sin que el usuario
  haga nada (llega en ≤ 30 s): cuando otro supertécnico o el admin asigna desde otro PC, con la asignación automática al
  entregar a glass, y cuando un técnico pide una pieza (ese trabajo deja de contar hasta que se recibe) o se recibe.

## 2. Comportamiento

**Quién y dónde:**
- Solo **SUPERTECNICO y ADMIN** (los dos ven hoy la carga; el endpoint exige uno de los dos). Al técnico no se le monta nada
  ni se pide nada.
- Se ve en **cualquier pantalla** con sesión abierta.

**Abrir y cerrar:**
- **Sin botón en la barra azul** (decisión del usuario tras probarlo en preprod, 2026-10-20 noche: se quitó el botón
  «Carga» que llevaba la primera versión).
- El botón **«Carga técnicos»** de Asignaciones se queda donde está y abre el panel (si ya está abierto, no hace nada; si
  está minimizado, lo abre). **Es la única forma de reabrirlo después de cerrarlo.**
- «×» del panel o de la pastilla lo **cierra**: deja de pedir datos.
- **Escape y un clic fuera no lo cierran** (a diferencia de la campana): es un panel para tenerlo abierto.

**El panel (abierto):**
- 340 px de ancho, fondo blanco, borde `pill-borde`, radio 10 px y sombra.
- **Cabecera** (es el asa de arrastre, cursor `grab`): «⋮⋮», «Carga de técnicos», el toggle **Pedidos | Total** (el de hoy, en
  pequeño), y a la derecha «–» (Minimizar) y «×» (Cerrar), con `title` y `aria-label`.
- **Lista:** la de hoy en versión estrecha: nombre 72 px (antes 110) y cifras 58 px (antes 140); barras, colores, orden,
  tooltip, atenuado al pasar y punto del alcance, **sin cambios**. Alto máximo 400 px (y nunca más que el hueco de pantalla bajo
  la barra azul); si no cabe, scroll dentro de la lista.
- Sin pie.

**La pastilla (minimizado):**
- Píldora navy (`azul-noche`), texto «Carga», una **mini-barra por técnico** en el orden de la lista (4 px de ancho, alto
  proporcional al total saturado al 100 %, mínimo 3 px, color del nivel; sin jornada, mínimo y gris) y el contador
  **«N al 90 %+»**: técnicos con total ≥ 90 en el alcance activo, fondo rojo `carga-alta` si N > 0 y blanco al 18 % si es 0.
- «×» para cerrar. Un clic **sin arrastrar** la abre (se distingue del arrastre por un umbral de 4 px de movimiento).
- Sin datos (primera carga o error sin datos): solo «Carga» y «×».

**Posición:**
- Se guarda como **ancla**: esquina de pantalla (`br`, `bl`, `tr`, `tl`) y distancia a sus dos bordes; arriba se mide desde
  el borde inferior de la barra azul. **De fábrica: arriba a la derecha, bajo la barra azul, a 16 px** (decisión del
  usuario tras preprod; antes, abajo a la derecha). De entrada tapa lo que haya arriba a la derecha de cada pantalla (en
  Asignaciones, «Técnicos de glass» y las últimas columnas de las primeras filas): se mueve o se minimiza, y se recuerda.
- Al **soltar** el panel o la pastilla, la esquina es la más cercana al **centro** de la caja, y las distancias son las
  suyas a esos dos bordes.
- Panel y pastilla **comparten el ancla**: la pastilla aparece en la esquina del panel más cercana al borde, y al abrirla el
  panel crece desde ahí. Ninguno salta al pasar de uno a otro.
- Al dibujar, y al cambiar el tamaño de la ventana del navegador, las distancias se **recortan** para que la caja quepa
  entera en la vista (nunca debajo de la barra azul ni fuera de la pantalla). Lo guardado no se toca: si la ventana vuelve a
  crecer, vuelve a su sitio.
- Durante el arrastre la caja no sale de la vista.

**Pulsar un técnico:**
- Navega a `/reparaciones/asignaciones` (si ya está ahí, no navega) y deja el filtro **Técnico** con ese técnico **en
  solitario**. Si ya estaba en Asignaciones, el resto de filtros se queda como estaba (calco de hoy); si viene de otra pantalla,
  la página arranca con sus filtros de siempre y solo ese técnico.
- El panel **sigue abierto**.
- **Excepción:** con una ventana abierta **fuera de Asignaciones**, pulsar un técnico **no hace nada**. Navegar cerraría esa
  ventana y se perdería lo que no estuviera guardado. En Asignaciones, con «Asignar trabajos» abierto, sí filtra la tabla de
  detrás: ahí no se pierde nada.

**Actualización:**
- Con el panel **abierto o minimizado** (la pastilla enseña datos) la carga se pide cada **30 s**; con el banner de conexión
  activo, cada **5 s** (como el resto de la web). **Cerrado no pide nada.**
- Con la pestaña oculta no pide (comportamiento por defecto de TanStack); al volver a la pestaña recarga al momento
  (`refetchOnWindowFocus`, política global).
- **Escrituras propias al momento, con una regla general:** cada escritura que termina desde este navegador, bien o con
  error (un 409 es que algo cambió por debajo), en cualquier pantalla, invalida la carga. Si el panel está cerrado no hay
  ninguna petición (la consulta no está activa); si está abierto o minimizado, es una petición más. Así no depende de
  acordarse de cada botón (asignar, reasignar, borrar, editar cliente, solicitudes de pieza, glass, pulido, formulario…).
  Las invalidaciones explícitas de la carga que ya existían (`useRecargar`, modal «Asignar trabajos») **se quitan**: con la
  regla general, cada escritura pide la carga una sola vez.
- **Ratón encima:** mientras el puntero está dentro del panel (o arrastrándolo), se pinta la **última copia** y no se
  reordena ni cambia ninguna cifra. Al salir, se pinta lo último que haya llegado. La consulta sigue su curso por debajo.
- El panel **no congela** el sondeo de la tabla de Asignaciones (la ventana de hoy sí lo hacía con `onInteraccion`): estando
  abierto todo el día, la tabla dejaría de refrescarse.

**Recordar:**
- `localStorage`, clave **`panelCarga`**, un JSON `{ estado: 'abierto' | 'minimizado' | 'cerrado', alcance: 'pedidos' |
  'total', ancla: { esq, x, y } }`.
- Lectura y escritura con `try/catch`, como `almacen/pedidos/preferencias.ts`. Lo que no se pueda leer o no tenga forma
  válida (JSON roto, valores desconocidos, números no finitos o negativos) se sustituye campo a campo por lo de fábrica:
  **abierto, Pedidos, arriba a la derecha a 16 px**. Si no se puede guardar, dura lo que la página.
- Se guarda al cambiar de estado, de alcance y al **soltar** un arrastre (no en cada movimiento).
- Es **del navegador, no del usuario** (PCs compartidos): el siguiente supertécnico o admin de ese navegador lo ve como lo
  dejó el anterior. Cerrar sesión **no** borra lo guardado; el estado en memoria se reinicia con `reiniciarStores` y, al volver
  a entrar, se **relee** de `localStorage`.
- Varias pestañas: cada una tiene su panel; lo que guarda una lo coge la otra al recargar (sin sincronizar en vivo).

**Capas:**
- **Sin ventana abierta:** el panel y la pastilla van con `fixed` y **`z-30`**. Quedan por debajo del panel de la campana y
  del rastro (`z-40`) y de los menús desplegables (`z-50`), así que no los tapan.
- **Con una ventana que bloquea abierta** (cualquier diálogo de `shared/ui/dialog.tsx`, es decir, mientras exista su fondo
  oscuro `[data-slot="dialog-overlay"]`), el panel y la pastilla **suben por encima** (`z-[55]`). Siguen por debajo de la capa
  de carga (`z-[60]`) y **se pueden usar**: moverlos, minimizarlos, cambiar Pedidos/Total, desplazar la lista con la rueda
  y pulsar técnicos (con la excepción de «Pulsar un técnico»). Decisión del
  usuario tras preprod (2026-10-20 noche): «precisamente cuando estás asignando es cuando sientes que pierdes el control de
  la ventana». Vale para todas las ventanas.
- Tocar el panel o la pastilla **nunca cierra la ventana de debajo**. El `DialogContent` compartido ignora las interacciones
  de fuera que caen dentro de un elemento marcado `data-encima-de-ventanas`, y el panel y la pastilla llevan esa marca y
  `pointer-events: auto`, porque Radix pone `pointer-events: none` en el `body` mientras hay una ventana abierta.
- **Con una ventana abierta**, además:
  - la rueda y el arrastre táctil sobre la lista no llegan al bloqueo de scroll de Radix, que escucha en `document`
    (`stopPropagation`, el mismo patrón que `shared/ui/popover.tsx`), así que la lista se desplaza de forma nativa;
  - tocar el panel o la pastilla no le quita el foco al campo de la ventana (`preventDefault` en `mousedown`, solo con una
    ventana abierta). Si no, Radix lo devolvería con el texto entero seleccionado y lo medio escrito (un IMEI) se perdería
    con la siguiente tecla.
- **Límites conocidos con una ventana abierta:**
  - el foco del teclado no sale de la ventana;
  - para los lectores de pantalla el panel queda oculto (`aria-hidden`).

## 3. Errores y casos límite

- **Corte de conexión con datos ya pintados:** el banner de siempre; el panel conserva los últimos datos (no se vacía) y
  reintenta cada 5 s.
- **Error sin datos previos** (primera carga): en lugar de la lista, «No se pudo cargar la carga de técnicos.» (el literal de
  hoy), **sin** apilar el diálogo de error global (`meta.silenciarError`). La pastilla, solo «Carga».
- **Capa de carga:** los refrescos del panel **no** la encienden, tampoco su primera carga al entrar con el panel recordado
  abierto (`meta.sinCapa`).
- **Fin de semana / sin jornada:** igual que hoy («—», «sin jornada hoy», barras vacías); la pastilla en gris, «0 al 90 %+».
- **Medianoche:** el día lo decide el servidor (Europe/Madrid); el siguiente refresco ya es del día nuevo.
- **Técnicos dados de alta o de baja:** aparecen o desaparecen en el siguiente refresco.
- **Sin técnicos activos:** lista vacía con «Sin técnicos activos».
- **Pantalla pequeña:** recorte del ancla (§2) y scroll dentro de la lista.
- **Sesión:** el panel solo existe con sesión (vive en `AppLayout`). El refresco **no** cuenta como actividad para el cierre por
  inactividad de 2 h (que mira teclado, clic y rueda).
- **Técnico en el mismo PC:** no se monta ni se pide nada, aunque lo guardado diga «abierto».
- **«Asignar trabajos»** usa la misma consulta (`useCargaTecnicos(true)`, `carga.pedidos`): con el panel abierto sus cifras
  también se refrescan cada 30 s. Es el dato correcto y no afecta a sus colas. El panel queda por encima de la ventana y se
  puede usar mientras se asigna (§2, «Capas»). Ni el panel ni la ventana suman lo que hay en la cola sin guardar: entra al
  pulsar «Guardar».

## 4. Construcción

**Piezas** (nombres orientativos; el plan los fija):
- `shared/lib/panelCarga.ts`: el store del panel (`estado`, `alcance`, `ancla`) con lectura/escritura en `localStorage`, y el
  aviso de un solo uso «filtrar Asignaciones por el técnico N». En `shared` porque lo usan el marco (`AppLayout`) y
  Asignaciones (módulo taller).
- `shared/lib/anclaPanel.ts`: funciones puras — ancla al soltar (esquina más cercana + distancias) y estilo recortado para un
  tamaño de caja y de ventana. Sin DOM, con tests propios.
- `modules/taller/asignaciones/panel/`: `PanelCarga` (decide panel o pastilla), la vista del panel, `PastillaCarga` y el
  arrastre con pointer events (`setPointerCapture`, umbral de 4 px).
- `FilaTecnico` sale de `CargaTecnicosDialog.tsx` a su propio fichero, con medidas para el ancho estrecho. **`CargaTecnicosDialog`
  se borra**, con su test (sustituido por los del panel). `carga.ts` (nivel, orden, formato, tooltip) se reutiliza tal cual.
- `useCargaTecnicos(habilitada)`: `refetchInterval` de 30 s (5 s desconectado) mientras está habilitada,
  `meta: { silenciarError: true, sinCapa: true }`. La clave `['carga-tecnicos']` pasa a `shared` para que la regla general la
  pueda nombrar sin que `shared` importe de un módulo.
- `shared/api/queryClient.ts`: en el `MutationCache`, al terminar cualquier mutación (bien o con error, como dice §2), `invalidateQueries` de la clave de
  la carga.
- `app/shell/TopBar.tsx`: sin cambios (el botón `BotonPanelCarga` de la primera versión se quitó tras preprod).
- `shared/lib/store.ts`: `crearStoreLeido(leer)`, un store que relee su fuente (`localStorage`) al reiniciarse. Las pruebas
  vacían el almacenamiento **antes** de reiniciar los stores.
- La clave de la carga va en `shared/api/cargaTecnicos.ts`.
- `app/shell/AppLayout.tsx`: monta `PanelCarga` (solo supertécnico y admin).
- `AsignacionesPage.tsx`: «Carga técnicos» abre el panel; consume el aviso de filtro (pone `tecnicos: [id]` y lo vacía); se
  quitan `cargaAbierta` y el `marcar` de la ventana.

## 5. Pruebas

**Unitarias (Vitest + Testing Library):**
- Ancla: esquina por cuadrante del centro, distancias, recorte con ventana pequeña y bajo la barra azul, sin saltos panel ↔
  pastilla.
- Recuerdo: lo de fábrica sin nada guardado; JSON roto y campos inválidos; `localStorage` que lanza al leer y al escribir; se
  guarda al soltar y no en cada movimiento.
- Panel: técnico → ni panel ni petición; la barra azul no tiene botón «Carga»; abrir, minimizar, cerrar y reabrir desde
  Asignaciones;
  Escape y clic fuera no cierran; ratón encima → no cambia con datos nuevos y se pone al día al salir; pulsar un técnico →
  navega y deja el filtro en solitario, el panel sigue; intervalo de 30 s con el panel abierto y minimizado, sin peticiones
  cerrado; error sin datos → mensaje y sin diálogo global; sin capa de carga.
- Pastilla: mini-barras y contador (con N > 0, con 0 y sin jornada); clic abre; arrastre no abre.
- Regla general: una mutación cualquiera que termina bien vuelve a pedir la carga; una que falla, también; sin nadie
  mirando la carga, ninguna petición.
- Asignaciones: el panel abierto no congela el sondeo de la tabla.

**E2E en preprod** (Chromium, datos sintéticos creados por API y borrados al terminar; nunca en producción):
- Supertécnico: con el panel abierto, asigna desde la pantalla → la primera barra de ese técnico sube **sin esperar**
  al sondeo.
- Una asignación creada por API («otro PC») aparece en **≤ 35 s**.
- Recargar la página → mismo estado, posición y alcance; arrastrar a otra esquina y minimizar → la pastilla sale ahí.
- Pulsar un técnico desde otra pantalla → Asignaciones filtrada por él, panel abierto.
- Un técnico no ve el botón ni el panel.

## 6. Entrega

- Rama `dev_v0920_panel_carga` (convención de ramas por área) en el **worktree** `/c/Users/dev/Documents/wt/web-0920` (la
  copia principal de la web la usa otra sesión).
- Las E2E de antes entran con el panel **cerrado** (sembrado en `playwright.config.ts`) para que no tape botones; la nueva
  parte de un navegador limpio.
- Versión **0.9.20** de la web: `package.json`, `CHANGELOG.md` (requiere servidor 0.9.18, sin migración) y
  `docs/novedades/NOVEDADES-v0.9.20.md` en la raíz.
- `docs/paridad/asignaciones.md`: la sección «Ventana "Carga de técnicos"» pasa a describir el panel, con las diferencias
  deliberadas respecto al JavaFX (no modal, en directo, recordado, no congela la tabla, pulsar no cierra).
- Si la 0.9.19 no se ha desplegado antes, va incluida en la 0.9.20.
- Despliegue de **solo web, en horario** (sin migración, vuelta atrás = web anterior).
- Merge `--no-ff`, push y tag **solo cuando lo diga el usuario**.
