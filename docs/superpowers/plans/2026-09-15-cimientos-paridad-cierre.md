# Cimientos — lote de cierre tras la verificación de paridad (2026-09-15)

Correcciones acordadas tras comparar la web desplegada con las capturas del cliente JavaFX (fichas `docs/paridad/{shell,clientes}.md` del repo web). Cuatro tareas independientes; las tres del repo web van en secuencia sobre `feature/web-cimientos`; la del servidor va en paralelo sobre su `feature/web-cimientos`.

## Global Constraints

- Ramas: web `feature/web-cimientos` y servidor `feature/web-cimientos`. Nunca `main`. Sin push, merge ni tag.
- Commits sin trailers `Co-Authored-By`. Mensajes en español con prefijo `feat(web):`, `fix(web):`, `docs(web):`, `docs(servidor):`.
- Web: `npm run check` (lint + `tsc -b` + Vitest) en verde al terminar cada tarea; los tests existentes se adaptan, no se borran. TDD: el test primero cuando hay comportamiento nuevo.
- Tokens: SIEMPRE los de `src/shared/styles/tokens.css` (`@theme`), nunca colores literales en componentes. Si falta un token, se añade ahí.
- Paridad = calcar el JavaFX (`gestion-reparaciones-cliente/src/main/resources/styles/app.css` y las capturas); no "mejorar" el diseño.
- Nada sensible en los repos públicos: sin IPs, secretos, nombres de usuarios reales ni capturas.
- Alcance cerrado: nada fuera de lo listado en cada tarea.

## Referencias JavaFX (valores exactos, de `app.css` y `ConfirmDialog.java`)

- Columna lateral (`.stock-sidebar`): fondo `#FFFFFF`; botón (`.stock-sidebar-btn`): transparente, texto `#2C3B54` 13 px negrita, padding 10 16, alineado a la izquierda, ancho completo, hover `rgba(44,59,84,0.08)`; activo (`.stock-sidebar-btn-active`): fondo `#001232`, texto `#FAFAFA`, mismo padding, esquinas de píldora (radio 24). En las capturas la columna mide ~200 px de ancho, el botón activo ~180×50 px con margen 10 px, y el contenido empieza ~24 px a la derecha.
- Banner (`.banner-conexion`): fondo `#F6C453`, texto `#5A4500` 12 px negrita, padding 4 12, altura mínima 24, centrado. Texto: `⚠ Sin conexión con el servidor. Reintentando…`.
- Diálogo de error del JavaFX al fallar una ACCIÓN del usuario sin conexión: título "Error", texto `Sin conexión con el servidor: <detalle>`, botón Aceptar. En refrescos de fondo solo banner.
- Menú contextual y menú de usuario (`.context-menu`, `.menu-item`): fondo `#FFFFFF`, borde `#C2C8D0` 1 px, radio 8, sombra suave; ítems: texto `#001232` 12 px negrita, padding 6 12; hover/foco: fondo `#001232`, texto `#FFFFFF`, radio 8.
- Filtros desplegables (`.combo-box-base`, también el `MultiSelectDropdown`): fondo `#001232`, radio 24, texto `#FAFAFA` 12 px negrita, padding 6 12, flecha ▾ clara a la derecha, hover `#0A2040`; popup blanco con borde `#C2C8D0`, radio 8, sombra. Ancho ~200 px, alto ~40 px.
- ConfirmDialog (`ConfirmDialog.mostrar`): contenedor 400 px, padding 24, separación 10; título 18 px negrita `#B03040` con "✕" a la derecha; descripción 13 px `#2C3B54`; botón de acción a ancho completo, fondo `#A84040`, texto crema `#F6F6F6`, radio 4, 12 px, padding 10; debajo botón Cancelar a ancho completo, fondo crema, texto y borde `#586376`, radio 4.
- Vista Clientes: título "Clientes" en su propia línea; debajo la fila con el filtro (píldora navy "Cliente ▾", ~200 px) y, a ~40 px, el botón "Nuevo cliente"; tabla con columnas Nombre (~340 px) y Estado (~130 px) y el resto en blanco (relleno), como el TableView.

---

### Task 1: Shell — columna lateral, banner con tokens, diálogo de error en acciones y estilo de menús

**Files (repo web):**
- Create: `src/app/shell/SubNav.tsx`, `src/app/shell/SubNav.test.tsx`
- Modify: `src/app/shell/AppLayout.tsx`, `src/shared/styles/tokens.css`, `src/app/shell/ConnectionBanner.tsx`, `src/app/shell/ConnectionBanner.test.tsx`, `src/shared/api/queryClient.ts`, `src/shared/api/queryClient.test.tsx`, `src/shared/api/errors.ts` (+ `errors.test.ts` si cambia la clase), `src/shared/api/client.ts` (solo si hace falta el detalle del fallo), `src/shared/ui/dropdown-menu.tsx`, `src/shared/ui/context-menu.tsx`

**Pasos:**

1. **Columna lateral de sub-navegación** (`SubNav`). Un `<aside>` de 200 px, fondo blanco, padding 8 px, que ocupa toda la altura bajo la barra y a la izquierda del `<main>`, siempre visible. Muestra los enlaces de la sección actual (primer segmento de la ruta): `/clientes` → `[{ to: '/clientes', label: 'Clientes' }]`; el resto de secciones (`/reparaciones`, `/stock`, `/estadisticas`, `/gestion/*`, `/cuenta/*`) sin enlaces por ahora (los añadirán los sub-proyectos 1-6; dejar el mapa `SUBNAV: Record<string, { to: string; label: string }[]>` preparado y comentado). Cada enlace es un `NavLink` a ancho completo con el estilo de `.stock-sidebar-btn` / `-active` de las referencias (tokens: `azul-medio`, `azul-noche`, `texto-nav-activo`; hover `azul-medio/8`; radio `rounded-3xl`; 13 px negrita; padding 10 16). `AppLayout` pasa a `<div className="flex flex-1"><SubNav /><main className="flex-1">…</main></div>`. Tests: en `/clientes` renderiza el enlace "Clientes" activo (aria-current="page"); en `/reparaciones` renderiza el aside sin enlaces.
2. **Tokens del banner.** Añadir en `tokens.css`: `--color-banner-bg: #F6C453;` y `--color-banner-text: #5A4500;`. `ConnectionBanner` usa `bg-banner-bg text-banner-text`, `px-3 py-1 min-h-6 text-center text-[12px] font-bold`. Test existente adaptado si comprueba clases.
3. **Diálogo de error además del banner, solo para acciones del usuario.** Regla (calco de `enRefresco` del JavaFX): (a) `MutationCache.onError`: si el error es `ConexionError`, emitir SIEMPRE el diálogo con `Sin conexión con el servidor: <detalle>` (aunque la mutación tenga `meta.silenciarError`, que solo cubre los 409 propios de la vista); el resto igual que hoy. (b) `QueryCache.onError`: si es `ConexionError` y la consulta NO tenía datos previos (`query.state.data === undefined`, es decir, carga inicial provocada por navegar), emitir el mismo diálogo; si ya tenía datos (refresco de fondo), solo banner, como hoy. (c) `esErrorGestionadoGlobalmente` no cambia de firma; `avisarEdicion` de Clientes sigue sin duplicar avisos. `<detalle>`: para 5xx `HTTP <status>`; para fallo de red el mensaje del error subyacente (p. ej. `Failed to fetch`); para timeout `tiempo de espera agotado`. Guardar el detalle en `ConexionError` (campo `detalle` opcional) sin cambiar `message` (`MSG_SIN_CONEXION`), que sigue alimentando el banner. Tests en `queryClient.test.tsx`: mutación con `ConexionError` → diálogo; consulta inicial con `ConexionError` → diálogo; refetch con datos previos → sin diálogo; mutación con `StaleDataError` y `silenciarError` → sin diálogo (ya existe o se añade).
4. **Estilo de menús.** En `dropdown-menu.tsx` y `context-menu.tsx` (ítems y contenedor): contenedor `bg-white border border-fila-sep rounded-lg shadow-md p-1`; ítems `rounded-lg px-3 py-1.5 text-[12px] font-bold text-azul-noche focus:bg-azul-noche focus:text-white data-[disabled]:opacity-50`. Sin cambiar la API de los componentes.
5. `npm run check` en verde. Commit(s): `feat(web): columna lateral de sub-navegacion, banner con tokens, dialogo de error en acciones sin conexion y estilo de menus`.

**Expected:** en producción, `/clientes` muestra la columna blanca con la píldora navy "Clientes"; el banner es amarillo #F6C453 con texto #5A4500; crear un cliente con la API caída abre el diálogo "Error / Sin conexión con el servidor: …" además del banner; los menús se ven como en el JavaFX.

---

### Task 2: Clientes y UI compartida — filtro píldora, layout, ConfirmDialog, etiqueta y anchos de tabla

**Files (repo web):**
- Modify: `src/shared/ui/MultiSelect.tsx`, `src/shared/ui/MultiSelect.test.tsx`, `src/shared/ui/ConfirmDialog.tsx`, `src/shared/ui/ConfirmDialog.test.tsx`, `src/shared/ui/DataTable.tsx`, `src/modules/gestion/clientes/ClientesPage.tsx`, `src/modules/gestion/clientes/ClientesPage.test.tsx`, `src/modules/gestion/clientes/ClienteDialog.tsx`

**Pasos:**

1. **Filtro como píldora navy** (`MultiSelect`): el `PopoverTrigger` pasa a `flex h-10 min-w-[200px] items-center justify-between rounded-3xl bg-azul-noche px-4 text-[12px] font-bold text-texto-nav-activo hover:bg-azul-noche-hover` con un `ChevronDown` (lucide, 16 px) a la derecha. El `PopoverContent`: `bg-white border border-fila-sep rounded-lg shadow-md`. La etiqueta de texto (`textoMultiSelect`) no cambia. Tests: el trigger sigue siendo accesible por su texto ("Cliente", "N clientes").
2. **Layout de la vista Clientes**: `<h1>` "Clientes" en su propia línea (`mb-4`); debajo `<div className="mb-4 flex flex-wrap items-center gap-10">` con el `MultiSelect` y, si `puedeEditar`, el botón "Nuevo cliente" (quitar `ml-auto`). Botón: `h-10 rounded-3xl bg-azul-noche px-5 text-[12px] font-bold text-texto-nav-activo hover:bg-azul-noche-hover`.
3. **ConfirmDialog** calcado de `ConfirmDialog.mostrar`: `DialogContent` de `max-w-[400px] p-6 gap-2.5`; título `text-[18px] font-bold text-texto-error`; descripción `text-[13px] text-azul-medio`; botones apilados a ancho completo en este orden: acción (`w-full rounded bg-rojo-accion py-2.5 text-[12px] text-crema hover:bg-rojo-accion/90`) y debajo Cancelar (`w-full rounded border border-azul-gris bg-crema py-2.5 text-[12px] text-azul-gris`). Mantener `DialogTitle`/`DialogDescription` (accesibilidad: `getByRole('dialog', { name })` y los tests existentes). El botón "✕" lo pone ya `DialogContent`.
4. **ClienteDialog**: `Label` con `whitespace-nowrap` para que "Nombre del cliente:" no se parta.
5. **Anchos de tabla** (`DataTable`): honrar `size` de `ColumnDef` aplicando `style={{ width: size }}` en `TableHead` y `TableCell` cuando la columna lo define, y añadir una columna de relleno vacía al final (cabecera y celdas vacías, sin `aria-label`) para que las columnas con tamaño no se estiren. En `ClientesPage`, `columnas`: Nombre `size: 340`, Estado `size: 130`. Tests: `getByRole('row', { name: /AMAZON/ })` y el resto siguen pasando; añadir un test de `DataTable` (o en ClientesPage) que compruebe que las cabeceras "Nombre" y "Estado" llevan el ancho.
6. `npm run check` en verde. Commit: `feat(web): filtro pildora navy, layout de Clientes, ConfirmDialog y anchos de tabla como el JavaFX`.

**Expected:** la vista Clientes se ve como `Apuntes/paridad-capturas/clientes/lista-supertecnico.png` salvo la fila seleccionada (diferencia aceptada).

---

### Task 3: Servidor — `docs/deployment.md` obsoleto pasa a ser un puntero

**Files (repo servidor):**
- Modify: `docs/deployment.md`

**Pasos:**

1. Sustituir el contenido (guía antigua sin Docker: JAR bajo systemd, MariaDB en el host, puerto 8080 abierto) por un documento corto en español que diga: el servidor se despliega en contenedores junto a la web; el `Dockerfile` de este repo construye la imagen (`eclipse-temurin:17-jdk` + Maven → `eclipse-temurin:17-jre`); los ficheros de referencia del despliegue (compose, nginx) viven en el repo `gestion-reparaciones-web`, carpeta `deploy/`; la configuración se inyecta por variables de entorno (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`, `SERVER_ERROR_INCLUDE_MESSAGE`, `SPRINGDOC_SWAGGER_UI_ENABLED`), sin valores; el paso a paso operativo está fuera del repo (guía privada del equipo). Sin IPs, dominios internos ni credenciales de ejemplo. Conservar una nota de que la guía antigua se retiró el 2026-09-15 por obsoleta.
2. Commit: `docs(servidor): deployment.md apunta al despliegue en contenedores (deploy/ del repo web)`.

---

### Task 4: Web — punteros de despliegue, nginx `/v3/api-docs`, CHANGELOG y cierre de las fichas de paridad

**Files (repo web):**
- Modify: `deploy/README.md`, `README.md`, `deploy/nginx/default.conf`, `docs/paridad/shell.md`, `docs/paridad/clientes.md`
- Create: `CHANGELOG.md`

**Pasos:**

1. `deploy/README.md`: la última línea pasa a: "El paso a paso de la primera instalación y el mantenimiento están en la guía privada del equipo `Apuntes/despliegue_vdc_produccion.md` (fuera del repo)." Nada más cambia.
2. `README.md`: añadir sección `## Despliegue` (antes de "Documentación") con dos frases: los ficheros de referencia están en `deploy/` (compose, nginx, README) y la guía operativa es privada (`Apuntes/despliegue_vdc_produccion.md`, fuera del repo).
3. `deploy/nginx/default.conf`: en `location /v3/api-docs` añadir `proxy_set_header Host $host;` (para que springdoc genere `servers.url` con el nombre público y no el interno). Añadir una línea de comentario encima explicándolo. Nota en el commit: en la VM hay que volver a copiar el fichero y reiniciar nginx (lo hace el usuario).
4. `CHANGELOG.md` nuevo, formato "Keep a Changelog" en español, con la entrada `## [0.1.0] - 2026-09-15 — Cimientos` y viñetas: shell (barra superior, columna lateral, menú de usuario, banner de conexión, refresco al volver), login y sesión (JWT, expiración, guard de rutas), API tipada desde OpenAPI (`npm run api:types`, snapshot `api/openapi.json`), componentes compartidos (DataTable, MultiSelect, StatusBadge, ConfirmDialog, AlertaProvider), módulo Clientes (CRUD con bloqueo optimista y menú contextual), CI, Dockerfile + `deploy/`, smoke e2e con Playwright.
5. Fichas de paridad. `docs/paridad/shell.md`: marcar `[x]` todos los puntos verificados el 2026-09-15 contra producción y las capturas; dejar `[ ]` solo "Los 4 botones visibles para los 3 roles" con la nota "(SUPERTECNICO y TECNICO verificados; ADMIN pendiente de comprobar en navegador)"; añadir estos puntos nuevos marcados: "Columna lateral de sub-navegación (200 px, blanca, píldora navy del apartado activo; en Clientes una sola píldora "Clientes"; el resto de secciones la rellenan los sub-proyectos 1-6)", "Banner con tokens `--color-banner-bg` #F6C453 / `--color-banner-text` #5A4500", "Diálogo "Error / Sin conexión con el servidor: <detalle>" además del banner cuando falla una acción del usuario o la carga inicial de una vista; en refrescos de fondo solo banner". Añadir a "Diferencias aceptadas": "el panel inicial por rol (ADMIN→Historial, SUPERTECNICO→Asignaciones, TECNICO→Pendientes) queda para el sub-proyecto 1, cuando existan esas vistas; hasta entonces todos entran en `/reparaciones`" y "sin vistas que sondeen, el banner se cura al siguiente request (acción o volver a la pestaña), no cada 5 s; el intervalo de 5 s llega con `useIntervaloRefresco` en el sub-proyecto 1". `docs/paridad/clientes.md`: marcar `[x]` todos los puntos (verificados el 2026-09-15 contra producción y las capturas, incluido el smoke e2e); en "Diferencias aceptadas" quitar los dos "(añadido tras la revisión, pendiente de confirmar con el usuario)" y dejar ambas como confirmadas el 2026-09-15; añadir "la tabla web no tiene la columna de relleno del TableView como columna real, pero reserva el mismo espacio en blanco".
6. Commit: `docs(web): punteros de despliegue, Host en /v3/api-docs, CHANGELOG 0.1.0 y fichas de paridad cerradas`.
