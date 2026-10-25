# 0.9.22 pieza 2 — «Por pasar a NSYS» con cuatro tarjetas, «Hecho» y plantillas para NSYS · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que la pestaña «Por pasar a NSYS» del stock de teléfonos separe lo que no cuadra con NSYS en cuatro tarjetas (Cambio de
color, Cambio de grado, No está en NSYS y Revisar a mano), con «Hecho» por fila, «Revisado» para los avisos y dos plantillas Excel
listas para subir a NSYS, generadas en el servidor.

**Architecture:**
- **Base de datos:** migración aditiva `sql/migracion-plantillas-nsys.sql`: tabla nueva `Taller_hecho` (una marca por IMEI y
  tarjeta) y dos columnas en `Taller_aviso` (`REVISADO_POR`, `REVISADO_EN`).
- **Servidor** (paquete `com.reparaciones.servidor.nsys`):
  - `TallerDAO` (avisos por revisar y «Revisado»), `HechoDAO` (nuevo, `Taller_hecho`) y `NsysDAO` (IMEIs del taller que NSYS no
    conoce y costes de unos lotes);
  - `ProductoNsys` con el paso 4 («compruébalo») y `TallerNsys.movilesPorPasar()`, la base de las tarjetas;
  - reglas puras `TarjetasNsys` (las cuatro listas, la marca de cada fila y el repaso de las marcas) y `AltaTaller` (los trabajos
    de un IMEI fuera de NSYS);
  - `PlantillaNsys` (Apache POI): las dos plantillas sobre la vacía de NSYS (`src/main/resources/nsys/plantilla-nsys-vacia.xlsx`);
  - servicio `PorPasarNsys` y controlador `PorPasarController` (`/api/stock-telefonos/por-pasar/...`); `ImportadorNsys` repasa
    las marcas tras cada Items ACTUAL OK.
- **Web:** pestaña «Por pasar a NSYS (N)» nueva (tarjetas, listas, «Hecho», «Marcar todo», «Copiar lista», descargas y
  «Revisado»); «compruébalo» en la ficha y en «Corregir…»; «Revisado por …» en el rastro.
- **E2E** en preprod con el móvil sintético de la 0.9.21.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB, Apache POI 5.2.5 (`poi-ooxml`, ya en el `pom`). Pruebas con JUnit 5,
  Mockito y MockMvc (sin base de datos).
- **Web:** React 19 + TypeScript, TanStack Query, openapi-fetch. Pruebas con Vitest, Testing Library, user-event, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md` (raíz), §3 a §6 (la §2, la pieza 1, ya está en
`main`: servidor `8f00adf`). Prototipo aprobado: `docs/superpowers/specs/assets/2026-10-25-v0922-prototipo-plantillas-nsys.html`
(diseño C, variante «con «Revisado»»). Plantilla vacía: `docs/superpowers/specs/assets/2026-10-25-v0922-plantilla-nsys-vacia.xlsx`.

## Global Constraints

**Versión, código de partida y base de datos**
- Versión **0.9.22** (la web pasa de `0.9.21` a `0.9.22`; la entrada `[0.9.22]` del CHANGELOG ya existe, «(pendiente)», con la
  pieza 1). Toca **servidor, base de datos y web**.
- Base de datos: **solo una migración aditiva** (`CREATE TABLE Taller_hecho` y `ALTER TABLE Taller_aviso ADD COLUMN`), en
  `sql/migracion-plantillas-nsys.sql` y en `sql/crear_bd.sql`; vista previa `YA_EXISTEN` = 0 y comprobación `CREADAS` = **3**.
- La pieza 1 está en `main` (servidor `8f00adf`, web `bfcbba8`) y puede estar ya en preprod o en producción. Las ramas salen del
  `origin/main` de cada repo **en el momento de crearlas** (`git fetch` antes y mirar el commit; no dar por hecho ningún hash).
- **Compatibilidad durante el despliegue:** `GET /api/stock-telefonos/por-pasar` **sigue igual** (la web 0.9.21 la usa hasta que
  se despliega la nueva). Los campos nuevos de `FilaPorPasar`, `FichaMovil` y `OpcionColor` van **al final**. Las rutas nuevas
  cuelgan de `/api/stock-telefonos/por-pasar/`.

**Ramas, worktrees y registro**
- Rama **`dev_v0922_plantillas_nsys`** en los dos repos, en worktrees. **Los clones normales no se tocan** (los usan otras sesiones).
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0922-plantillas` (lo crea la Task 1):
    `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor fetch -q origin && git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add -b dev_v0922_plantillas_nsys /c/Users/dev/Documents/wt/servidor-0922-plantillas origin/main`
  - Web: `/c/Users/dev/Documents/wt/web-0922-plantillas`, con `npm ci` dentro (lo crea la Task 8):
    `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web fetch -q origin && git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add -b dev_v0922_plantillas_nsys /c/Users/dev/Documents/wt/web-0922-plantillas origin/main`
  - Las rutas `S/…` y `T/…` del plan son las del worktree del servidor; `W/…`, las del de la web; `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0922-pieza2/`** (raíz).

**Commits, merge y datos de prueba**
- Commits en español **sin tildes**, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Merge `--no-ff`, push, tags y gitlinks **solo con el OK del usuario**; antes de cualquier merge o push, `git fetch` y comparar
  con `origin`.
- **Repos públicos:** datos de prueba sintéticos (IMEI `35000000000xxxx`, «Técnico 1/2», «Supertécnico 1», lotes `BT-9001`,
  «Proveedor A», productos «Apple iPhone 13 128GB Blue»…). Nunca datos reales de NSYS, IP ni nombres reales.

**Reglas de rol** (spec §3.1)
- Todo `/api/stock-telefonos/**`, también las rutas nuevas, va con `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")` en la
  clase. Sin permisos nuevos. El TECNICO no ve «Teléfonos».

**Reglas de negocio** (spec §3 y §4)
- **Cambio de color:** móviles con un cambio de **color** del taller VIGENTE (`Taller_cambio` COLOR VIGENTE) que están en el espejo.
  **Cambio de grado:** con un cambio de **grado** VIGENTE (también los que cambian de color). Un móvil con los dos sale en las dos.
- **No está en NSYS:** todo IMEI de `Reparacion` (cualquier categoría y fecha) que no esté en el espejo en ningún lote (cruce en
  Java por `NsysDAO.claveCruce`, nunca con un JOIN entre `Reparacion` y `Nsys_movil`).
- **Revisar a mano:** los `Taller_aviso` **SIN_COLOR, SIN_GRADO y SIN_DESHACER** sin «Revisado» y sin un `Taller_cambio` de origen
  **MANO** del mismo IMEI y dato **posterior** al aviso. DESHECHO no sale (solo el rastro).
- **«Revisado»:** guarda quién y cuándo (`REVISADO_POR`, `REVISADO_EN`), una línea en el registro de actividad (acción
  **`REVISAR_AVISO_TALLER`**, detalle `IMEI: <imei>, <TIPO> de <idRep>`) y el rastro lo dice.
- **«Hecho»:** una marca por IMEI y tarjeta (COLOR, GRADO, ALTA) en `Taller_hecho`, con quién y cuándo; no va al registro de
  actividad. Las plantillas y «Copiar lista» llevan **solo las filas sin marca**. Tras cada importación ACTUAL OK, para cada marca
  anterior a la `FECHA_EXPORTACION` del fichero: si la fila ya no está, se borra; si sigue, «vuelve» (`VOLVIO`) y la fila sale con
  «Marcado «Hecho» a las HH:MM, pero NSYS sigue igual».
- **Número de la pestaña:** filas pendientes de las cuatro tarjetas (una fila con marca que vale no cuenta; una que «volvió», sí).
- **Plantilla de color** (`NSYS_color_AAAA-MM-DD.xlsx`) y **de altas** (`NSYS_altas_AAAA-MM-DD.xlsx`): la vacía de NSYS (hoja
  «Лист1», filas 1 a 6 tal cual), datos desde la **fila 7**, columnas A-N de la tabla del §4 de la spec. Lote **`COLOR`** y
  **`AJUSTE`**; Manufacturer `Apple`; Group of Goods `iPhone`; Status `0`. En la de color: modelo y capacidad del Goods del móvil
  («iPhone 14 eSIM», «256 GB»), color nuevo con la grafía de NSYS (**amarillo** si sale del maestro), el grado que tiene **hoy**
  NSYS, **el mismo «Total cost»** del lote actual y su proveedor tal cual. En la de altas: el modelo del taller en **rojo** y D-H
  vacías en **rojo**. Un móvil de color sin producto («búscalo a mano») no va.
- **Grafía del color** (§2.1, ya en `main` salvo el paso 4): 1) el Goods más usado del mismo modelo, capacidad y eSIM; 2) otra
  capacidad del mismo modelo; 3) cualquier modelo; **4) el nombre del maestro tal cual, con «compruébalo»** (nuevo en esta pieza:
  también en la ficha y en «Corregir…»).
- **Horas:** `Taller_hecho.FECHA`, `VOLVIO` y `Taller_aviso.REVISADO_EN` en **hora de Madrid**, con `LocalDateTime.now(clock)`
  truncado a segundos; nunca `CURRENT_TIMESTAMP`. `Reparacion.FECHA_ASIG` y `FECHA_FIN` van en UTC: se pasan a Madrid con
  `NsysDAO.utcAMadrid`.
- **El límite de 15 s por petición no se sube.** El cruce de «No está en NSYS» se mide en preprod.

**Textos exactos de la web** (spec §3 y prototipo)
- Pestaña: «Por pasar a NSYS (N)». Tarjetas: «Cambio de color», «Cambio de grado», «No está en NSYS», «Revisar a mano»; número
  «3 móviles · 1 hecho» («1 móvil», sin «· …» si no hay hechos; en «Revisar a mano», sin hechos).
- Explicación de cada tarjeta:
  - Color: «Cambia el producto: en NSYS, bórralo y súbelo otra vez con la plantilla (con el grado que tiene hoy en NSYS).»
  - Grado: «Cambia el grado: se corrige aparte en NSYS, uno a uno. Si el móvil también cambia de color, primero el color.»
  - No está: «NSYS no lo conoce: dalo de alta con la plantilla (rellena lo que falta).»
  - Revisar: «El ERP no supo calcularlo: mira el móvil y corrígelo en su ficha.»
- Botones: «⬇ Descargar plantilla (n)» (color y altas), «Copiar lista (n)» (grado), «Ver» (revisar); sin filas pendientes, los
  cuatro deshabilitados con `title` «No hay móviles que corregir».
- Ayuda bajo las tarjetas (una por tarjeta):
  - Color: «Móviles a los que el taller ha cambiado el color. En NSYS: bórralos y súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después). Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.»
  - Grado: «Todos los móviles a los que el taller ha cambiado el grado (chasis → A, batería nueva → 100…), también los que cambian de color: en esos, haz primero el color (si no, la plantilla de color le devolvería el grado de antes). En NSYS el grado se corrige aparte, sin borrar el móvil. Cada fila se va sola cuando la importación ve el grado nuevo.»
  - No está: «Móviles que han pasado por el taller y NSYS no conoce. La plantilla lleva lo que sabe el ERP; lo demás (capacidad, color, grado, precio, proveedor) va vacío para rellenarlo en el Excel. Cada fila se va sola cuando la importación los ve en NSYS.»
  - Revisar: «Al guardar una reparación, el ERP no pudo calcular el color o el grado nuevo, o no pudo deshacer un cambio. Una persona mira el móvil y lo corrige en su ficha («Corregir…»); si NSYS tiene que cambiar, el móvil aparece después en Color o en Grado.»
- Columnas:
  - Color: «IMEI · Modelo · Color · En NSYS hoy · Poner en NSYS · Por qué · Desde · ✓ Marcar todo (n)». Color: «Blue → (PRODUCT)RED»
    y, debajo en gris, «también en «Cambio de grado» (después)»; Poner en NSYS: el producto con el color resaltado y debajo
    «grado B (el de NSYS hoy)»; sin producto, «(ningún móvil de ese color en NSYS: búscalo a mano)» y «no va en la plantilla»;
    del maestro, «compruébalo».
  - Grado: «IMEI · Modelo · Grado · Producto en NSYS hoy · Por qué · Desde · ✓ Marcar todo (n)». Grado: «B → A100» y, si también
    cambia de color, «primero su cambio de color».
  - No está: «IMEI · Modelo · Último trabajo · Técnico · Cliente · Trabajos · Desde · ✓ Marcar todo (n)». Modelo con «el del taller»
    debajo; último trabajo «R20261006_57 · Reparación» (« · asignado» si está abierto).
  - Revisar: «IMEI · Modelo · Qué pasa · Qué hacer · Reparación» y los botones «✓ Revisado» y «Corregir…».
- Vacías: «Ningún cambio de color pendiente.», «Ningún cambio de grado pendiente.», «Todos los móviles del taller están en
  NSYS.», «Nada que revisar: todo lo del taller se pudo calcular.»; sin cargar, «No se pudo cargar la lista.»
- «Hecho»: botón «Hecho»; marcada, «✓ Hecho 12:31» (`title` «Marcado por <quién> a las 12:31») y «deshacer»; vuelta,
  «Marcado «Hecho» a las 12:32, pero NSYS sigue igual».
- Avisos: «Descargado «NSYS_color_2026-10-25.xlsx» · 2 móviles (los marcados «Hecho» no van)» y, si hay, «1 sin producto en NSYS no
  va: búscalo a mano» y «1 con «compruébalo» (en amarillo): míralo antes de subirlo»; «Descargado «NSYS_altas_2026-10-25.xlsx» · 2 móviles. Rellena en el Excel lo que va en rojo antes de
  subirlo»; «Copiado: 3 móviles con su grado nuevo. Cuando los cambies en NSYS, márcalos «Hecho»»; «Revisado: sale de la lista
  (queda en el rastro)».
- Etiquetas de los avisos: «Color sin deducir», «Grado sin calcular», «No se pudo deshacer». Qué hacer: «Mira de qué color quedó y
  corrígelo en su ficha», «Decide el grado que le toca y corrígelo en su ficha», «Mira si el móvil tiene de verdad ese color; si
  no, corrígelo en su ficha» («ese grado» si el aviso es de grado).
- «compruébalo»: el producto lleva detrás « (compruébalo: NSYS nunca ha escrito ese color)» en la ficha, el diálogo y la lista.
- Rastro: «Revisado por Supertécnico 1: color sin deducir (reparación R20261022_2)».

**Colores** (tokens que ya existen): borde superior de las tarjetas `taller` (color, #7C3AED), `tipo-reparacion-text` (grado,
#1565C0), `fila-urgente-brd` (no está, #C62828) y `ambar` (revisar, #E8A825). Marca «Hecho» con `verde-ok`/`fila-recibido-*`.

**Comandos**
- **Servidor** (Bash, en `/c/Users/dev/Documents/wt/servidor-0922-plantillas`):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH" && mvn -q -o test -Dtest=ClaseA,ClaseB`
  (**comas**, no `+`: surefire 3.2.5 no lo admite; sin `-Dtest`, la suite entera: 1434 pruebas al empezar). `OpenApiContractTest`
  levanta el contexto entero y deja `target/openapi.json` para los tipos de la web.
- Java con `\\` (expresiones regulares): escribirlo con las herramientas Edit/Write, nunca con un heredoc de Bash (en Windows reduce
  las barras dobles).
- **Web** (en `/c/Users/dev/Documents/wt/web-0922-plantillas`): `npx vitest run <ruta>`, `npx tsc -b`, `npm run lint` y, al cerrar,
  `npm run check`. Lint con `eslint-plugin-react-hooks` 7: nada de `new Date()` ni `Date.now()` en el cuerpo de un componente o un
  hook. Un módulo no importa de otro: lo común va a `src/shared`.
- **Inestabilidad conocida de la web:** con la suite entera, algún test puede pasar de los 5 s («orden de llamadas…» de
  `FormularioReparacion`, `NuevoPedidoDialog`, `StockPage`, `HistorialPage`); solos pasan. Si pasa, se repite ese fichero solo y
  se apunta en el registro; no se tocan.

**Preproducción y producción**
- **E2E solo en preproducción**, con el móvil sintético `350000000009921` que mete el guion y limpieza; **nunca en producción**.
- **Claude no hace SSH.** Prepara los guiones en `Apuntes/` y el usuario los pega. Cada bloque empieza con `hostname` y la
  comprobación de `backup-erp.sh` (solo existe en producción). `docker compose up -d --build` tarda ~1-2 min sin salida.
- Orden del despliegue: **migración → servidor → web**. La web 0.9.21 funciona con el servidor nuevo (rutas nuevas y campos al final).

## Ajustes a la spec (se suben a la spec, §10, en el commit del plan)

1. **Una marca anterior al cambio de su fila no vale** (era de un cambio anterior del mismo móvil): la fila sale sin marcar y el
   siguiente repaso la borra. En «No está en NSYS» no pasa (la fila nace con el primer trabajo).
2. **Una marca cuya fila ya no está se borra en el repaso**, sea cual sea su hora (si la fila vuelve, la regla 1 la invalida).
3. **Storage:** la plantilla vacía valida la columna D con una lista de números (4 … 16384), pero se escribe el texto «256 GB», como
   en la subida comprobada del 22/10 (spec §4). Si NSYS dejara de leerlo, se cambia en `GrafiasNsys.capacidadDelGoods`.
4. **Purchase price:** número con sus decimales (§9.2 sigue por mirar con el usuario; si NSYS no los acepta, se redondea en
   `PlantillaNsys.color`, una línea). Un lote sin coste deja la celda vacía **en rojo**.
5. **API:** las tarjetas van en `GET /api/stock-telefonos/por-pasar/tarjetas`; `GET /por-pasar` sigue igual por compatibilidad.
   Las descargas mandan el número de móviles en la cabecera `X-Filas` y el nombre en `Content-Disposition`.
6. **«Trabajos» de «No está en NSYS»:** los trabajos hechos (R, G, P) y las asignaciones abiertas (una A terminada ya tiene su
   trabajo hecho). «Último trabajo»: el más reciente de todas sus filas, «asignado» si es una A abierta. **Cliente:** el del último
   trabajo y, si no tiene, el del teléfono.
7. **«Qué hacer» de SIN_DESHACER** dice el dato del aviso («ese color» o «ese grado»).
8. **«Revisado» en el rastro:** evento `TALLER_REVISADO_<tipo>` a la hora de la revisión, «Revisado por <quién>: color sin deducir
   (reparación R…)»; el aviso sigue a su hora.
9. **El paso 4 llega también a la ficha y al diálogo «Corregir…»** (con «compruébalo»), como dice el §2.1.
10. **El número de la pestaña** cuenta las filas pendientes (spec §3.1), no el total que pintaba el prototipo.
11. **«Corregir…» de «Revisar a mano»** abre el detalle del IMEI (ficha, rastro y su «Corregir…»), como el IMEI de cualquier fila.

## Mapa de ficheros

**Servidor** (`S` = `src/main/java/com/reparaciones/servidor/nsys/`, `T` = `src/test/java/com/reparaciones/servidor/nsys/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `sql/migracion-plantillas-nsys.sql` (nuevo), `sql/crear_bd.sql`, `sql/reset-uat.sql`, `docs/schema.md`, `T/DdlPlantillasTest.java` (nuevo) | Tabla `Taller_hecho` y columnas de «Revisado» | 1 |
| `S/AvisoTaller.java`, `S/TallerDAO.java`, `S/Rastro.java` + `T/TallerDAOTest`, `T/RastroTest` | Avisos por revisar, «Revisado» y su evento del rastro | 2 |
| `S/ProductoNsys.java`, `S/TallerNsys.java`, `S/FilaPorPasar.java`, `S/OpcionColor.java`, `S/StockTelefonosController.java` + `T/ProductoNsysTest`, `T/TallerNsysTest`, `T/StockTelefonosControllerTest` | Paso 4 («compruébalo») y la base de las tarjetas | 3 |
| `S/TarjetaHecho.java`, `S/HechoTaller.java`, `S/HechoDAO.java`, `S/AltaTaller.java` (nuevos), `S/NsysDAO.java` + `T/HechoDAOTest`, `T/AltaTallerTest` (nuevos), `T/NsysDAOTest` | Acceso a las marcas, a los IMEIs fuera de NSYS y a los costes | 4 |
| `S/FilaColorNsys.java`, `S/FilaGradoNsys.java`, `S/FilaAltaNsys.java`, `S/FilaRevisarNsys.java`, `S/TarjetasPorPasar.java`, `S/TarjetasNsys.java`, `S/PorPasarNsys.java` (nuevos), `S/ImportadorNsys.java` + `T/TarjetasNsysTest`, `T/PorPasarNsysTest` (nuevos), `T/ImportadorNsysTest` | Las cuatro listas, las marcas y su repaso | 5 |
| `src/main/resources/nsys/plantilla-nsys-vacia.xlsx` (nuevo), `S/PlantillaNsys.java` (nuevo), `S/GrafiasNsys.java`, `S/PorPasarNsys.java` + `T/PlantillaNsysTest` (nuevo), `T/GrafiasNsysTest`, `T/PorPasarNsysTest` | Las dos plantillas | 6 |
| `S/PorPasarController.java` (nuevo) + `controller/RolesStockTelefonosTest`, `OpenApiContractTest` | La API | 7 |

**Web** (`W` = `/c/Users/dev/Documents/wt/web-0922-plantillas/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `W/api/openapi.json`, `W/src/shared/api/schema.d.ts` (generados), `W/src/shared/api/client.ts`, `W/src/shared/lib/descargas.ts` (nuevo), `W/src/shared/lib/csv.ts`, `W/src/modules/telefonos/api.ts` (+ `api.test.tsx`), `W/src/modules/telefonos/test/fabrica.ts`, `W/src/test/server.ts` | Contrato, alias, llamadas y datos de prueba | 8 |
| `W/src/shared/lib/telefonoNsys.ts` (+ test), `W/src/modules/taller/imeis/{rastro.ts,FichaMovil.tsx,corregir.ts,CorregirTallerDialog.tsx}` + sus tests | Textos de los avisos, «compruébalo» y «Revisado» en el rastro | 9 |
| `W/src/modules/telefonos/stock/porPasar.ts` (+ `porPasar.test.ts`) | Reglas de las tarjetas | 10 |
| `W/src/modules/telefonos/stock/{PorPasarNsys.tsx,TarjetasNsys.tsx,CeldaHecho.tsx,ListasPorPasar.tsx}` (+ `PorPasarNsys.test.tsx`), `StockTelefonosPage.tsx` (+ test) | La pestaña | 11 |
| `W/tests/e2e/plantillas-nsys.spec.ts` (nuevo) | E2E en preprod | 12 |
| `W/package.json`, `W/package-lock.json`, `W/CHANGELOG.md`, `W/docs/paridad/imeis.md`, `docs/novedades/NOVEDADES-v0.9.22.md`, `Apuntes/plan-futuro.md` | Versión y documentación | 13 |

---

### Task 1: Base de datos — `Taller_hecho` y «Revisado» en `Taller_aviso`

**Files:**
- Create: `sql/migracion-plantillas-nsys.sql`
- Modify: `sql/crear_bd.sql` (el bloque de `DROP TABLE IF EXISTS` del principio, CREATE TABLE `Taller_aviso` ~línea 579 y, detrás,
  la tabla nueva), `sql/reset-uat.sql` (apartado 5a), `docs/schema.md` (líneas de `Taller_cambio` y `Taller_aviso`)
- Test: `T/DdlPlantillasTest.java` (nuevo), `T/DdlTallerTest.java` (compara el `Taller_aviso` de la 0.9.21 sin lo de la 0.9.22)

**Interfaces:**
- Produces: tabla `Taller_hecho (IMEI_SN, TARJETA ENUM('COLOR','GRADO','ALTA'), ID_USU, FECHA, VOLVIO)` con clave `(IMEI_SN,
  TARJETA)`; columnas `Taller_aviso.REVISADO_POR INT NULL` y `Taller_aviso.REVISADO_EN DATETIME NULL`. Las usan las Tasks 2 y 4.

- [ ] **Step 0: Worktree del servidor**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor fetch -q origin
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor log --oneline -1 origin/main
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add -b dev_v0922_plantillas_nsys /c/Users/dev/Documents/wt/servidor-0922-plantillas origin/main
```

Apunta en el informe el commit de partida (se espera `8f00adf` o uno posterior). Pasa la suite entera una vez (1434 pruebas) antes
de tocar nada.

- [ ] **Step 1: Write the failing test**

`T/DdlPlantillasTest.java` (con la herramienta Write: lleva `\\s`):

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** La base de la 0.9.22 pieza 2 (spec «plantillas NSYS» §3.5 y §3.6): la migración, que es la que se aplica en la VM, y
 *  crear_bd.sql dicen lo mismo de Taller_hecho y de las columnas de «Revisado» de Taller_aviso. Se comparan con los espacios
 *  juntados (crear_bd.sql alinea las columnas). */
class DdlPlantillasTest {

    private static final String MIGRACION = "migracion-plantillas-nsys.sql";
    private static final String CLAVE_REVISADO =
            "fk_taller_aviso_revisado FOREIGN KEY (REVISADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL";

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String junto(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    /** El CREATE TABLE de {@code tabla}, de «CREATE TABLE» a «);». */
    private static String crear(String sql, String tabla) {
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla);
        return junto(sql.substring(desde, sql.indexOf(");", desde) + 2));
    }

    /** Lo que va entre «ADD COLUMN <columna>» y «AFTER» en la migración. */
    private static String deLaMigracion(String columna) throws Exception {
        Matcher m = Pattern.compile("ADD COLUMN " + columna + " (.+?) AFTER ").matcher(fichero(MIGRACION));
        assertTrue(m.find(), columna + " en la migración");
        return junto(m.group(1));
    }

    /** La línea de la columna en el CREATE TABLE de {@code tabla} de crear_bd.sql, sin el nombre ni la coma final. */
    private static String deCrearBd(String tabla, String columna) throws Exception {
        String sql = fichero("crear_bd.sql");
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla + " en crear_bd.sql");
        String bloque = sql.substring(desde, sql.indexOf(");", desde));
        Matcher m = Pattern.compile("(?m)^\\s+" + columna + "\\s+(.+?),?$").matcher(bloque);
        assertTrue(m.find(), columna + " en " + tabla);
        return junto(m.group(1));
    }

    @Test void laTablaDeLasMarcasEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_hecho");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_hecho"));
        assertTrue(migracion.contains("TARJETA ENUM('COLOR','GRADO','ALTA') NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN, TARJETA)"), migracion);
    }

    @Test void lasColumnasDeRevisadoSonLasMismasEnLosDos() throws Exception {
        assertEquals(deLaMigracion("REVISADO_POR"), deCrearBd("Taller_aviso", "REVISADO_POR"));
        assertEquals(deLaMigracion("REVISADO_EN"), deCrearBd("Taller_aviso", "REVISADO_EN"));
        assertTrue(deLaMigracion("REVISADO_POR").startsWith("INT NULL"), deLaMigracion("REVISADO_POR"));
        assertTrue(deLaMigracion("REVISADO_EN").startsWith("DATETIME NULL"), deLaMigracion("REVISADO_EN"));
        assertTrue(junto(fichero(MIGRACION)).contains("ADD CONSTRAINT " + CLAVE_REVISADO));
        assertTrue(junto(fichero("crear_bd.sql")).contains("CONSTRAINT " + CLAVE_REVISADO));
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLasMarcasYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_hecho;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_hecho;"));
    }
}
```

`T/DdlTallerTest.java`: `laMigracionYCrearBdDicenLoMismo` compara línea a línea el `Taller_aviso` de `migracion-sku-taller.sql`
(0.9.21) con el de `crear_bd.sql`, que ahora lleva las dos columnas y la clave de «Revisado». Su segunda comprobación pasa a quitar
lo de la 0.9.22 antes de comparar (y el javadoc de la clase añade «; el Taller_aviso de crear_bd.sql lleva además lo de la 0.9.22
—«Revisado»—, que va en su propia migración»):

```java
    @Test void laMigracionYCrearBdDicenLoMismo() throws Exception {
        assertEquals(crear("migracion-sku-taller.sql", "Taller_cambio"), crear("crear_bd.sql", "Taller_cambio"));
        // 0.9.22: crear_bd.sql lleva también «Revisado» (REVISADO_POR, REVISADO_EN y su clave), de migracion-plantillas-nsys.sql.
        List<String> aviso = new ArrayList<>(crear("crear_bd.sql", "Taller_aviso"));
        aviso.removeIf(l -> l.contains("REVISADO_"));
        aviso.replaceAll(l -> l.startsWith("    CONSTRAINT fk_taller_aviso_usuario") && l.endsWith(",") ? l.substring(0, l.length() - 1) : l);
        assertEquals(crear("migracion-sku-taller.sql", "Taller_aviso"), aviso);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -o test -Dtest=DdlPlantillasTest`
Expected: FAIL (no existe `sql/migracion-plantillas-nsys.sql`).

- [ ] **Step 3: Write the migration and the DDL**

`sql/migracion-plantillas-nsys.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-plantillas-nsys.sql — 0.9.22 pieza 2, lo que no cuadra con NSYS
-- (spec docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md §3.5 y §3.6, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor de la pieza 2. Aditiva: el servidor anterior no lee la tabla ni las columnas.
-- NO idempotente (relanzar = error de tabla o columna existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_hecho y ALTER TABLE Taller_aviso.
-- 3) Comprobación final: CREADAS debe dar 3.
-- Vuelta atrás: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_hecho')
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_aviso'
           AND COLUMN_NAME IN ('REVISADO_POR', 'REVISADO_EN')) AS YA_EXISTEN;

-- «Hecho» de «Por pasar a NSYS» (spec §3.6): una marca por IMEI y tarjeta, con quién y cuándo. VOLVIO: la importación vio NSYS
-- igual después de marcarla, y la fila vuelve a salir.
CREATE TABLE Taller_hecho (
    IMEI_SN  VARCHAR(40) NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN; en ALTA, el IMEI del taller',
    TARJETA  ENUM('COLOR','GRADO','ALTA') NOT NULL,
    ID_USU   INT         NULL,
    FECHA    DATETIME    NOT NULL COMMENT 'Cuándo se marcó (hora de Madrid)',
    VOLVIO   DATETIME    NULL COMMENT 'La importación vio NSYS igual tras marcarlo: la marca deja de valer',
    PRIMARY KEY (IMEI_SN, TARJETA),
    CONSTRAINT fk_taller_hecho_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- «Revisado» de «Revisar a mano» (spec §3.5): quién y cuándo. Taller_aviso tiene unas decenas de filas: el ALTER es inmediato.
ALTER TABLE Taller_aviso
    ADD COLUMN REVISADO_POR INT NULL COMMENT '«Revisado» (0.9.22): quién' AFTER FECHA,
    ADD COLUMN REVISADO_EN DATETIME NULL COMMENT '«Revisado» (0.9.22): cuándo, hora de Madrid' AFTER REVISADO_POR,
    ADD CONSTRAINT fk_taller_aviso_revisado FOREIGN KEY (REVISADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;

-- Comprobación final (no modifica nada): debe dar 3; si no, NO desplegar el servidor de la pieza 2 (lee la tabla y las columnas).
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_hecho')
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_aviso'
           AND COLUMN_NAME IN ('REVISADO_POR', 'REVISADO_EN')) AS CREADAS;
```

`sql/crear_bd.sql`, en el bloque de `DROP TABLE IF EXISTS` del principio, la primera línea (antes de `DROP TABLE IF EXISTS
Taller_aviso;`), para que el fichero se pueda relanzar:

```sql
DROP TABLE IF EXISTS Taller_hecho;
```

En `CREATE TABLE Taller_aviso`: debajo de la línea de `FECHA` añade

```sql
    REVISADO_POR INT          NULL COMMENT '«Revisado» (0.9.22): quién',
    REVISADO_EN  DATETIME     NULL COMMENT '«Revisado» (0.9.22): cuándo, hora de Madrid',
```

y la última línea del bloque (`CONSTRAINT fk_taller_aviso_usuario …`) pasa a llevar coma y detrás

```sql
    CONSTRAINT fk_taller_aviso_revisado FOREIGN KEY (REVISADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
```

Justo después del `);` de `Taller_aviso`, la tabla nueva con su comentario:

```sql

-- 0.9.22 pieza 2 («Por pasar a NSYS» con cuatro tarjetas): la marca «Hecho» de cada fila, una por IMEI y tarjeta. Se borra sola
-- cuando la importación ve NSYS al día; si no, «vuelve» (VOLVIO) y la fila sale otra vez.
CREATE TABLE Taller_hecho (
    IMEI_SN  VARCHAR(40) NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN; en ALTA, el IMEI del taller',
    TARJETA  ENUM('COLOR','GRADO','ALTA') NOT NULL,
    ID_USU   INT         NULL,
    FECHA    DATETIME    NOT NULL COMMENT 'Cuándo se marcó (hora de Madrid)',
    VOLVIO   DATETIME    NULL COMMENT 'La importación vio NSYS igual tras marcarlo: la marca deja de valer',
    PRIMARY KEY (IMEI_SN, TARJETA),
    CONSTRAINT fk_taller_hecho_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

`sql/reset-uat.sql`, en el apartado «5a. SKU del taller (0.9.21)», antes de `UPDATE Taller_cambio …`:

```sql
DELETE FROM Taller_hecho;
```

`docs/schema.md`: la línea de `Taller_aviso` termina en «…; ver `sql/migracion-sku-taller.sql`; desde la 0.9.22, con quién y
cuándo se dio por revisado —`REVISADO_POR`, `REVISADO_EN`—, ver `sql/migracion-plantillas-nsys.sql`)» y, debajo, una línea nueva:

```markdown
- `Taller_hecho` (0.9.22, pieza 2: la marca «Hecho» de cada fila de «Por pasar a NSYS», una por IMEI y tarjeta —COLOR, GRADO o ALTA—, con quién y cuándo; la importación la borra cuando NSYS lo tiene o la marca como vuelta —`VOLVIO`— si NSYS sigue igual; ver `sql/migracion-plantillas-nsys.sql`)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -o test -Dtest=DdlPlantillasTest,DdlNombresCosteTest,DdlTallerTest,AnchosNsysTest`
Expected: PASS (`DdlTallerTest`, con el cambio de arriba; `DdlNombresCosteTest` y `AnchosNsysTest` no miran estas tablas).

- [ ] **Step 5: Commit**

```bash
git add sql/migracion-plantillas-nsys.sql sql/crear_bd.sql sql/reset-uat.sql docs/schema.md src/test/java/com/reparaciones/servidor/nsys/DdlPlantillasTest.java src/test/java/com/reparaciones/servidor/nsys/DdlTallerTest.java
git commit -m "feat: migracion de Taller_hecho y de Revisado en Taller_aviso (0.9.22, pieza 2)"
```

---

### Task 2: Servidor — avisos por revisar, «Revisado» y su evento del rastro

**Files:**
- Modify: `S/AvisoTaller.java`, `S/TallerDAO.java`, `S/Rastro.java`
- Test: `T/TallerDAOTest.java`, `T/RastroTest.java`

**Interfaces:**
- Consumes: columnas `Taller_aviso.REVISADO_POR` y `REVISADO_EN` (Task 1).
- Produces:
  - `AvisoTaller` con dos campos más al final, `String revisadoPor` (nombre de usuario) y `LocalDateTime revisadoEn`; el
    constructor de 12 argumentos de siempre sigue (los dos a null); `AvisoTaller.POR_REVISAR` (`Set<Tipo>` SIN_COLOR, SIN_GRADO,
    SIN_DESHACER);
  - `TallerDAO.avisosPorRevisar()` → `List<AvisoTaller>`; `TallerDAO.avisoPorId(int)` → `Optional<AvisoTaller>`;
    `TallerDAO.revisar(int idAviso, int idUsu, LocalDateTime ahora)` → `int`; constantes `SQL_POR_REVISAR`, `SQL_AVISO`,
    `SQL_REVISAR`;
  - en el rastro, el evento `TALLER_REVISADO_<TIPO>` (fuente TALLER, `tecnico` = quien revisó, `dato`, `idRep`, `de`, `motivo`).

- [ ] **Step 1: Write the failing tests**

`T/TallerDAOTest.java`, al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 2: «Revisar a mano»

    @SuppressWarnings("unchecked")
    @Test void losAvisosPorRevisarSonLosSinRevisarNiCorregidosAMano() {
        assertTrue(dao.avisosPorRevisar().isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_POR_REVISAR), any(RowMapper.class));
        String sql = TallerDAO.SQL_POR_REVISAR;
        assertTrue(sql.contains(" WHERE v.TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"), sql);
        assertTrue(sql.contains(" AND NOT EXISTS (SELECT 1 FROM Taller_cambio c WHERE c.IMEI_SN = v.IMEI_SN AND c.DATO = v.DATO"
                + " AND c.ORIGEN = 'MANO' AND c.FECHA > v.FECHA)"), sql);
        assertTrue(sql.endsWith(" ORDER BY v.FECHA, v.ID_AVISO"), sql);
    }

    @Test void revisarApuntaQuienYCuandoSoloSiNoEstabaRevisado() {
        dao.revisar(2, 7, HOY);
        verify(jdbc).update(TallerDAO.SQL_REVISAR, 7, Timestamp.valueOf(HOY), 2);
        assertEquals("UPDATE Taller_aviso SET REVISADO_POR = ?, REVISADO_EN = ? WHERE ID_AVISO = ? AND REVISADO_EN IS NULL"
                + " AND TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER')", TallerDAO.SQL_REVISAR);
    }

    @SuppressWarnings("unchecked")
    @Test void unAvisoPorSuIdYConQuienLoReviso() {
        assertTrue(dao.avisoPorId(2).isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_AVISO), any(RowMapper.class), eq(2));
        assertTrue(TallerDAO.SQL_AVISO.endsWith(" WHERE v.ID_AVISO = ?"), TallerDAO.SQL_AVISO);
        for (String sql : List.of(TallerDAO.SQL_AVISOS, TallerDAO.SQL_POR_REVISAR, TallerDAO.SQL_AVISO))
            assertTrue(sql.contains("v.REVISADO_EN, r.NOMBRE_USUARIO AS REVISOR FROM Taller_aviso v"
                    + " LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU LEFT JOIN Usuario r ON r.ID_USU = v.REVISADO_POR"), sql);
    }

    @Test void unaFilaDeTallerAvisoRevisada() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_AVISO")).thenReturn(2);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("ID_REP")).thenReturn("R20261021_3");
        when(rs.getString("TIPO")).thenReturn("SIN_COLOR");
        when(rs.getString("DATO")).thenReturn("COLOR");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.getString("REVISOR")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("REVISADO_EN")).thenReturn(Timestamp.valueOf(HOY.plusHours(2)));
        AvisoTaller a = TallerDAO.aviso(rs, 0);
        assertEquals("Supertécnico 1", a.revisadoPor());
        assertEquals(HOY.plusHours(2), a.revisadoEn());
        assertEquals(java.util.Set.of(AvisoTaller.Tipo.SIN_COLOR, AvisoTaller.Tipo.SIN_GRADO, AvisoTaller.Tipo.SIN_DESHACER),
                AvisoTaller.POR_REVISAR);
    }
```

(`unaFilaDeTallerAviso`, la de siempre, sigue igual: sin `REVISOR` ni `REVISADO_EN` en el mock, los dos campos salen a null, como
el constructor de 12 argumentos.)

`T/RastroTest.java`, al final de la clase:

```java
    @Test void unAvisoRevisadoSaleTambienALaHoraDeLaRevisionConQuienLoReviso() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 22, 11, 14);
        AvisoTaller a = new AvisoTaller(1, "350000000000001", "R20261022_2", AvisoTaller.Tipo.SIN_COLOR, null, DatoTaller.COLOR,
                "chai14yellowesim", null, "chasis chai14yellowesim nuevo", 8, "Técnico 2", hora, "Supertécnico 1",
                hora.plusDays(1));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_REVISADO_SIN_COLOR", "TALLER_SIN_COLOR"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusDays(1), false, "TALLER", "TALLER_REVISADO_SIN_COLOR", null, "chai14yellowesim",
                null, null, "R20261022_2", "Supertécnico 1", null, null, null, "COLOR", "MANO", "chasis chai14yellowesim nuevo"),
                rastro.get(0));
    }

    @Test void unAvisoSinRevisarNoTieneEventoDeRevision() {
        AvisoTaller a = new AvisoTaller(1, "350000000000001", "R20261022_2", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "ID", null, "chasis chai13blue nuevo", 8, "Técnico 2", LocalDateTime.of(2026, 10, 22, 11, 14));
        assertEquals(List.of("TALLER_SIN_GRADO"),
                Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a)).stream().map(EventoRastro::tipo).toList());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=TallerDAOTest,RastroTest`
Expected: FAIL de compilación (no existen `avisosPorRevisar`, `revisar`, `avisoPorId` ni el constructor de 14 argumentos).

- [ ] **Step 3: Implement**

`S/AvisoTaller.java` entero:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.Set;

/** Una fila de Taller_aviso (spec 0.9.21 «SKU del taller», pieza 2, §2.2 y §2.3): lo que el taller avisa al guardar, editar o
 *  borrar una reparación sin cambiar el dato. {@code de}: en SIN_COLOR, el SKU de la pieza; en SIN_GRADO, el grado que no se
 *  pudo calcular; en DESHECHO y SIN_DESHACER, lo que había puesto la reparación. {@code a}: en DESHECHO, lo que vuelve a
 *  valer. {@code motivo}: la pieza («chasis chai13red nuevo»). {@code usuario}: quien guardó (null si ya no existe). Desde la
 *  0.9.22, {@code revisadoPor} y {@code revisadoEn}: quién dio el aviso por bueno desde «Revisar a mano» y cuándo (null sin
 *  revisar). Horas de Madrid, como el espejo. */
public record AvisoTaller(int id, String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                          String motivo, Integer idUsu, String usuario, LocalDateTime fecha, String revisadoPor,
                          LocalDateTime revisadoEn) {

    /** Sin «Revisado»: los avisos de antes de la 0.9.22 y las pruebas que no lo necesitan. */
    public AvisoTaller(int id, String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                       String motivo, Integer idUsu, String usuario, LocalDateTime fecha) {
        this(id, imeiSn, idRep, tipo, causa, dato, de, a, motivo, idUsu, usuario, fecha, null, null);
    }

    /** SIN_COLOR: el color del chasis o de la tapa no se puede deducir del maestro. SIN_GRADO: el grado no es de la forma
     *  [Ms]<letra>[100]. DESHECHO: se deshizo el cambio de la reparación. SIN_DESHACER: ya no mandaba y no se tocó. */
    public enum Tipo { SIN_COLOR, SIN_GRADO, DESHECHO, SIN_DESHACER }

    /** Los que van a «Revisar a mano» (spec 0.9.22 §3.5); DESHECHO solo informa y se queda en el rastro. */
    public static final Set<Tipo> POR_REVISAR = Set.of(Tipo.SIN_COLOR, Tipo.SIN_GRADO, Tipo.SIN_DESHACER);

    /** Por qué se deshace (DESHECHO y SIN_DESHACER). */
    public enum Causa { BORRADA, EDITADA }

    /** Un aviso que entra. */
    public record Nuevo(String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                        String motivo) {}
}
```

`S/TallerDAO.java`:
- imports: añade `java.util.Optional` si no está (ya se usa en `piezaDeReparacion`).
- sustituye `SQL_AVISOS` por:

```java
    /** Las columnas de un aviso, con el nombre de quien lo guardó y, desde la 0.9.22, el de quien lo dio por revisado. */
    static final String COLUMNAS_AVISO = "v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.TIPO, v.CAUSA, v.DATO, v.DE, v.A, v.MOTIVO,"
            + " v.ID_USU, u.NOMBRE_USUARIO, v.FECHA, v.REVISADO_EN, r.NOMBRE_USUARIO AS REVISOR";
    static final String DESDE_AVISO = " FROM Taller_aviso v LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU"
            + " LEFT JOIN Usuario r ON r.ID_USU = v.REVISADO_POR";
    static final String SQL_AVISOS = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO + " WHERE v.IMEI_SN = ? ORDER BY v.FECHA, v.ID_AVISO";
    /** «Revisar a mano» (spec 0.9.22 §3.5): los SIN_COLOR, SIN_GRADO y SIN_DESHACER sin «Revisado» y sin una corrección a mano del
     *  mismo IMEI y dato después del aviso («Corregir…» o «Volver a lo de NSYS»: los dos dejan un cambio de origen MANO). */
    static final String SQL_POR_REVISAR = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO
            + " WHERE v.TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM Taller_cambio c WHERE c.IMEI_SN = v.IMEI_SN AND c.DATO = v.DATO"
            + " AND c.ORIGEN = 'MANO' AND c.FECHA > v.FECHA) ORDER BY v.FECHA, v.ID_AVISO";
    static final String SQL_AVISO = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO + " WHERE v.ID_AVISO = ?";
    /** «Revisado»: solo una vez y solo los de «Revisar a mano». */
    static final String SQL_REVISAR = "UPDATE Taller_aviso SET REVISADO_POR = ?, REVISADO_EN = ? WHERE ID_AVISO = ?"
            + " AND REVISADO_EN IS NULL AND TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER')";
```

- debajo de `avisosDeImei`:

```java
    /** «Revisar a mano» (spec 0.9.22 §3.5), del más antiguo al más reciente. Son unos pocos. */
    public List<AvisoTaller> avisosPorRevisar() {
        return jdbc.query(SQL_POR_REVISAR, TallerDAO::aviso);
    }

    public Optional<AvisoTaller> avisoPorId(int idAviso) {
        return jdbc.query(SQL_AVISO, TallerDAO::aviso, idAviso).stream().findFirst();
    }

    /** «Revisado» (spec 0.9.22 §3.5): quién y cuándo. 0 si el aviso no existe, ya estaba revisado o no es de «Revisar a mano». */
    public int revisar(int idAviso, int idUsu, LocalDateTime ahora) {
        return jdbc.update(SQL_REVISAR, idUsu, Timestamp.valueOf(ahora), idAviso);
    }
```

- el mapeo `aviso` añade los dos campos al final:

```java
    static AvisoTaller aviso(ResultSet rs, int n) throws SQLException {
        String causa = rs.getString("CAUSA");
        return new AvisoTaller(rs.getInt("ID_AVISO"), rs.getString("IMEI_SN"), rs.getString("ID_REP"),
                AvisoTaller.Tipo.valueOf(rs.getString("TIPO")), causa == null ? null : AvisoTaller.Causa.valueOf(causa),
                DatoTaller.valueOf(rs.getString("DATO")), rs.getString("DE"), rs.getString("A"), rs.getString("MOTIVO"),
                entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getTimestamp("FECHA").toLocalDateTime(),
                rs.getString("REVISOR"), fechaHora(rs.getTimestamp("REVISADO_EN")));
    }
```

`S/Rastro.java`:
- en el bucle de los avisos de `unir(…, avisos)`:

```java
        for (AvisoTaller a : avisos) {
            todos.add(deAviso(a));
            if (a.revisadoEn() != null) todos.add(deRevisado(a));
        }
```

- debajo de `deAviso`:

```java
    /** «TALLER_REVISADO_» + el tipo del aviso (0.9.22): alguien lo dio por bueno desde «Revisar a mano», a la hora de la
     *  revisión. {@code tecnico}: quién lo revisó; el resto, como el aviso. */
    private static EventoRastro deRevisado(AvisoTaller a) {
        return new EventoRastro(a.revisadoEn(), false, "TALLER", "TALLER_REVISADO_" + a.tipo().name(), null, a.de(), a.a(), null,
                a.idRep(), a.revisadoPor(), null, null, null, a.dato().name(), CambioTaller.MANO, a.motivo());
    }
```

- el javadoc de `unir` con los avisos añade al final: «Desde la 0.9.22, un aviso revisado sale también a la hora de la revisión
  (TALLER_REVISADO_*).»

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=TallerDAOTest,RastroTest,TallerReparacionesTest,GuardadoReparacionTest,StockTelefonosControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/AvisoTaller.java src/main/java/com/reparaciones/servidor/nsys/TallerDAO.java src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java src/test/java/com/reparaciones/servidor/nsys/RastroTest.java
git commit -m "feat: avisos por revisar a mano y Revisado con su evento del rastro (0.9.22, pieza 2)"
```

---

### Task 3: Servidor — el paso 4 («compruébalo») y la base de las tarjetas

**Files:**
- Modify: `S/ProductoNsys.java`, `S/TallerNsys.java`, `S/FilaPorPasar.java`, `S/OpcionColor.java`, `S/StockTelefonosController.java`
- Test: `T/ProductoNsysTest.java` (entero), `T/TallerNsysTest.java` (tres nuevas y dos que cambian), `T/StockTelefonosControllerTest.java`

**Interfaces:**
- Produces:
  - `record ProductoNsys.Poner(String producto, boolean compruebalo)`; `ProductoNsys.paraColor(…)` devuelve `Poner` (null si no
    hay producto);
  - `record TallerNsys.MovilPorPasar(MovilNsys movil, LoQueVale.Valor valor, List<CambioTaller> cambios, ProductoNsys.Poner poner)`
    y `List<MovilPorPasar> TallerNsys.movilesPorPasar()` (de paquete); `static PorQueTaller TallerNsys.porQue(CambioTaller)` (de
    paquete, para la Task 5);
  - `TallerNsys.productoAPoner(MovilNsys, LoQueVale.Valor)` devuelve `ProductoNsys.Poner`;
  - campos nuevos al final: `FilaPorPasar.ponerCompruebalo` (boolean), `OpcionColor.compruebalo` (boolean, con el constructor de 2
    argumentos de siempre a false) y `FichaMovil.ponerCompruebalo` (boolean).

- [ ] **Step 1: Write the failing tests**

`T/ProductoNsysTest.java` entero:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** El producto que hay que poner en NSYS para un color (spec 0.9.22 §2.1, pasos 1 a 4). El móvil de prueba es un iPhone 13 128GB
 *  Blue. */
class ProductoNsysTest {

    private static final MovilNsys AZUL = movil("350000000000001", "Azul", "B");
    private static final Supplier<Maestro> MAESTRO = () -> new Maestro(List.of(productoConNombre("Blue", "Azul", "B"),
            productoConNombre("Pink", "Rosa", "B"), producto("Green", "Verde", "B")));
    private static final Supplier<GrafiasNsys> GRAFIAS = () -> GrafiasNsys.de(List.of(
            new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));

    private static <T> Supplier<T> prohibido() {
        return () -> { throw new AssertionError("no hacía falta leerlo"); };
    }

    @Test void conMovilesDeEseColorEnSuModeloYCapacidadValeEseGoods() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false), ProductoNsys.paraColor(AZUL, "Rosa",
                Map.of("Rosa", "Apple iPhone 13 128GB Pink"), prohibido(), prohibido()));
    }

    @Test void sinMovilesSeComponeConElNombreDelMaestroEscritoComoNsys() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false),
                ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, GRAFIAS));
    }

    @Test void siNsysNuncaLoHaEscritoSaleElNombreDelMaestroTalCualConCompruebalo() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", true),
                ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, () -> GrafiasNsys.de(List.of())));
    }

    @Test void sinNombreEnElMaestroNoHayProducto() {
        assertNull(ProductoNsys.paraColor(AZUL, "Verde", Map.of(), MAESTRO, prohibido()), "el maestro no da el nombre");
        assertNull(ProductoNsys.paraColor(AZUL, null, Map.of(), prohibido(), prohibido()));
    }

    @Test void unMovilSinCapacidadEnSuGoodsNoSeCompone() {
        MovilNsys raro = new MovilNsys("350000000000003", "BT-9001", "Apple iPhone 13 Blue", "B", "Available", "CAJA LISTOS",
                "Proveedor A", AZUL.receivingDate(), null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        assertNull(ProductoNsys.paraColor(raro, "Rosa", Map.of(), prohibido(), prohibido()));
    }
}
```

`T/TallerNsysTest.java`, en la sección de «Por pasar a NSYS»:

```java
    @Test void siNsysNuncaHaEscritoEseColorElProductoSaleDelMaestroConCompruebalo() {
        when(nsys.cargarProductos()).thenReturn(List.of(productoConNombre("Blue", "Azul", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rosa", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        FilaPorPasar f = servicio.porPasar().get(0);
        assertEquals("Apple iPhone 13 128GB Pink", f.ponerProducto());
        assertTrue(f.ponerCompruebalo());
        assertEquals(List.of(new OpcionColor("Azul", "Apple iPhone 13 128GB Blue"),
                new OpcionColor("Rosa", "Apple iPhone 13 128GB Pink", true)), servicio.opciones(IMEI).colores());
    }

    @Test void losMovilesPorPasarLlevanLoQueValeSusCambiosYElProducto() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A100", "B")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(nsys.goodsMasUsados(any())).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        List<TallerNsys.MovilPorPasar> moviles = servicio.movilesPorPasar();
        assertEquals(1, moviles.size());
        assertEquals("Rojo", moviles.get(0).valor().color());
        assertEquals(List.of(1, 2), moviles.get(0).cambios().stream().map(CambioTaller::id).toList());
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false), moviles.get(0).poner());
    }

    @Test void unMovilQueSoloCambiaDeGradoNoBuscaProducto() {
        when(taller.vigentes()).thenReturn(List.of(vigente(2, IMEI, GRADO, "A100", "B")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertNull(servicio.movilesPorPasar().get(0).poner());
        assertEquals("Apple iPhone 13 128GB Blue", servicio.porPasar().get(0).ponerProducto(), "el producto de ahora");
        verify(nsys, never()).goodsMasUsados(any());
    }
```

Y dos pruebas que ya existen en `T/TallerNsysTest.java` cambian con lo nuevo:
- `laListaConLoQueHayQuePonerEnNsysLosMasAntiguosPrimero` (~línea 206) construye la `FilaPorPasar` esperada con 11 argumentos:
  ahora lleva el campo nuevo al final, `AHORA.minusDays(3)), lista.get(0));` → `AHORA.minusDays(3), false), lista.get(0));`.
- `elProductoAPonerEsElDeAhoraSiSoloCambiaElGradoYElMasUsadoSiCambiaElColor` (~líneas 86 y 90): `productoAPoner` devuelve un
  `Poner`; las dos comprobaciones de texto pasan a `servicio.productoAPoner(m, grado).producto()` y
  `servicio.productoAPoner(m, rojo).producto()`. El `assertNull` de la línea 92 sigue igual (los productos de `base()` no tienen
  nombre: sin paso 4).

`T/StockTelefonosControllerTest.java`, junto a las otras de la ficha:

```java
    @Test void laFichaDiceCompruebaloSiElProductoSaleDelMaestroTalCual() {
        when(dao.cargarProductos()).thenReturn(List.of(TallerDePrueba.productoConNombre("Blue", "Azul", "A"),
                TallerDePrueba.productoConNombre("Pink", "Rosa", "A")));
        MovilNsys m = movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK);
        when(dao.porImei(IMEI)).thenReturn(List.of(m));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rosa", "Azul")));
        JsonNode ficha = json(api.ficha(IMEI));
        assertEquals("Apple iPhone 13 128GB Pink", ficha.get("ponerProducto").asText());
        assertTrue(ficha.get("ponerCompruebalo").asBoolean());
    }
```

(Si `laFichaConElColorDelTallerPideElProductoMasUsadoDeEseColor` falla en su última comprobación, es que `conMaestro()` trae
productos con nombre: entonces el paso 4 da producto y la comprobación pasa a ser `ponerCompruebalo` = true. Con los productos de
`TallerDePrueba.producto`, sin nombre, sigue saliendo null.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=ProductoNsysTest,TallerNsysTest,StockTelefonosControllerTest`
Expected: FAIL de compilación (no existen `ProductoNsys.Poner`, `movilesPorPasar`, `ponerCompruebalo` ni el `OpcionColor` de 3).

- [ ] **Step 3: Implement**

`S/ProductoNsys.java` entero:

```java
package com.reparaciones.servidor.nsys;

import java.util.Map;
import java.util.function.Supplier;

/** El producto («Goods») que hay que poner en NSYS cuando el taller cambia el color de un móvil (spec 0.9.22 §2.1). Es puro. */
final class ProductoNsys {

    private ProductoNsys() {}

    /** El producto a poner y si sale del nombre del maestro tal cual (paso 4: NSYS nunca ha escrito ese color; la web dice
     *  «compruébalo» y la plantilla lo pinta en amarillo). */
    record Poner(String producto, boolean compruebalo) {}

    /** Para el color {@code color} del maestro: 1) el Goods más usado de ese color entre los móviles del mismo modelo, capacidad
     *  y eSIM ({@code goodsMismoModelo}, de NsysDAO#goodsMasUsados); 2-3) si no hay, el Goods del propio móvil con el color
     *  cambiado por su nombre en inglés del maestro (Maestro#nombreColor) escrito como lo escribe NSYS en otra capacidad del
     *  mismo modelo o en otro modelo (GrafiasNsys#color); 4) si NSYS nunca lo ha escrito, con el nombre del maestro tal cual y
     *  «compruébalo». null si el maestro no da el nombre o el Goods del móvil no tiene capacidad: la web dice «búscalo a mano».
     *  El maestro y las grafías solo se leen si hacen falta. */
    static Poner paraColor(MovilNsys m, String color, Map<String, String> goodsMismoModelo, Supplier<Maestro> maestro,
                           Supplier<GrafiasNsys> grafias) {
        if (color == null) return null;
        String exacto = goodsMismoModelo.get(color);
        if (exacto != null) return new Poner(exacto, false);
        if (GrafiasNsys.colorDelGoods(m.goods()) == null) return null;
        String nombre = maestro.get().nombreColor(m, color);
        if (nombre == null) return null;
        String grafia = grafias.get().color(m.modeloMaestro(), nombre);
        return grafia != null ? new Poner(GrafiasNsys.cambiarColor(m.goods(), grafia), false)
                : new Poner(GrafiasNsys.cambiarColor(m.goods(), nombre), true);
    }
}
```

`S/FilaPorPasar.java` entero:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Por pasar a NSYS» (spec 0.9.21 §2.4): lo que dice NSYS hoy ({@code productoNsys} = Goods, {@code gradoNsys} =
 *  grado entero), lo que hay que poner ({@code ponerProducto}: null si no se sabe) y el SKU nuevo (null: sin SKU en el maestro).
 *  {@code desde}: la fecha del cambio vigente más antiguo del móvil (hora de Madrid). {@code ponerCompruebalo} (0.9.22): el
 *  producto sale del nombre del maestro tal cual, porque NSYS nunca ha escrito ese color. Desde la 0.9.22 la web usa las
 *  tarjetas (TarjetasPorPasar); esta lista sigue para la web anterior. */
public record FilaPorPasar(String imeiSn, @Schema(nullable = true) String modelo,
                           @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                           @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                           @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                           @Schema(nullable = true) String sku, List<PorQueTaller> porQue, LocalDateTime desde,
                           boolean ponerCompruebalo) {}
```

`S/OpcionColor.java` entero:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un color del maestro para el diálogo «Corregir…» (spec 0.9.21 §2.3) con el producto que habría que poner en NSYS: el de
 *  ahora si es el color de NSYS; si no, el de ProductoNsys; null si no se sabe. {@code compruebalo} (0.9.22): el producto sale del
 *  nombre del maestro tal cual. */
public record OpcionColor(String color, @Schema(nullable = true) String producto, boolean compruebalo) {

    public OpcionColor(String color, String producto) {
        this(color, producto, false);
    }
}
```

`S/TallerNsys.java`:
- `productoAPoner`:

```java
    /** El producto («Goods») que hay que poner en NSYS (spec 0.9.21 §2.4 y 0.9.22 §2.1): si el color no cambia, el de ahora; si
     *  cambia, el que da ProductoNsys, con «compruébalo» si sale del maestro tal cual; null si no se sabe (la web dice «búscalo a
     *  mano»). */
    public ProductoNsys.Poner productoAPoner(MovilNsys m, LoQueVale.Valor v) {
        if (v.colorNsys() == null) return new ProductoNsys.Poner(m.goods(), false);
        return ProductoNsys.paraColor(m, v.color(), nsysDao.goodsMasUsados(m),
                perezoso(() -> new Maestro(nsysDao.cargarProductos())), perezoso(() -> GrafiasNsys.de(nsysDao.goodsContados())));
    }
```

- sustituye `porPasar()` por esto (y `porQue` pasa de `private static` a `static`):

```java
    /** Un móvil de «Por pasar a NSYS»: su fila más reciente del espejo, lo que vale, sus cambios vigentes (del más antiguo al más
     *  reciente) y el producto a poner si cambia el color (null si no cambia o no se sabe; 0.9.22). */
    record MovilPorPasar(MovilNsys movil, LoQueVale.Valor valor, List<CambioTaller> cambios, ProductoNsys.Poner poner) {}

    /** Los móviles con algo del taller vigente que están en el espejo, de su cambio más antiguo al más reciente (spec 0.9.21
     *  §2.4): la base de «Por pasar a NSYS» y de las tarjetas de la 0.9.22. Los del mismo modelo, capacidad y eSIM comparten la
     *  búsqueda del producto; el maestro y las grafías se leen una vez y solo si hacen falta. */
    List<MovilPorPasar> movilesPorPasar() {
        List<CambioTaller> vigentes = tallerDao.vigentes();
        if (vigentes.isEmpty()) return List.of();
        Map<String, MovilNsys> masReciente = masRecientes(vigentes);
        Supplier<Maestro> maestro = perezoso(() -> new Maestro(nsysDao.cargarProductos()));
        Supplier<GrafiasNsys> grafias = perezoso(() -> GrafiasNsys.de(nsysDao.goodsContados()));
        Map<List<Object>, Map<String, String>> goods = new HashMap<>();
        List<MovilPorPasar> moviles = new ArrayList<>();
        porImei(vigentes).forEach((clave, cambios) -> {
            MovilNsys m = masReciente.get(clave);
            if (m == null) return;
            LoQueVale.Valor v = LoQueVale.de(m, cambios, maestro);
            ProductoNsys.Poner poner = v.colorNsys() == null ? null
                    : ProductoNsys.paraColor(m, v.color(), goods.computeIfAbsent(Arrays.asList(m.modelo(), m.modeloMaestro(),
                            m.capacidad(), m.esEsim()), k -> nsysDao.goodsMasUsados(m)), maestro, grafias);
            moviles.add(new MovilPorPasar(m, v, cambios, poner));
        });
        return moviles;
    }

    /** La lista «Por pasar a NSYS» de la 0.9.21 (spec §2.4), para la web anterior: una fila por móvil de
     *  {@link #movilesPorPasar()}. */
    public List<FilaPorPasar> porPasar() {
        return movilesPorPasar().stream().map(p -> {
            MovilNsys m = p.movil();
            LoQueVale.Valor v = p.valor();
            String producto = v.colorNsys() == null ? m.goods() : p.poner() == null ? null : p.poner().producto();
            return new FilaPorPasar(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.goods(), m.grade(), producto,
                    v.grade(), v.sku(), p.cambios().stream().map(TallerNsys::porQue).toList(), p.cambios().get(0).fecha(),
                    p.poner() != null && p.poner().compruebalo());
        }).toList();
    }
```

- en `opciones`, la lista de opciones:

```java
        List<OpcionColor> opciones = colores.stream().map(c -> {
            if (Normalizacion.mismoTexto(c, m.color())) return new OpcionColor(c, m.goods(), false);
            ProductoNsys.Poner p = ProductoNsys.paraColor(m, c, goods, () -> maestro, grafias);
            return new OpcionColor(c, p == null ? null : p.producto(), p != null && p.compruebalo());
        }).toList();
```

`S/StockTelefonosController.java`:
- en `ficha`, antes del `return new FichaMovil(…)`:

```java
        ProductoNsys.Poner poner = v.porPasar() ? taller.productoAPoner(m, v) : null;
```

- en el `new FichaMovil(…)`, el argumento `v.porPasar() ? taller.productoAPoner(m, v) : null` pasa a ser
  `poner == null ? null : poner.producto()`, y al final, detrás de `v.porPasar() ? v.grade() : null`, va
  `poner != null && poner.compruebalo()`;
- el record `FichaMovil` lleva al final `boolean ponerCompruebalo`, y su javadoc añade «{@code ponerCompruebalo} (0.9.22): el
  producto sale del nombre del maestro tal cual.».

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=ProductoNsysTest,TallerNsysTest,StockTelefonosControllerTest,RolesStockTelefonosTest,TallerReparacionesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/ProductoNsys.java src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaPorPasar.java src/main/java/com/reparaciones/servidor/nsys/OpcionColor.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/ProductoNsysTest.java src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java
git commit -m "feat: el producto del maestro tal cual con compruebalo y la base de las tarjetas (0.9.22, pieza 2)"
```

---

### Task 4: Servidor — acceso a las marcas, a los IMEIs que NSYS no conoce y a los costes

**Files:**
- Create: `S/TarjetaHecho.java`, `S/HechoTaller.java`, `S/HechoDAO.java`, `S/AltaTaller.java`
- Modify: `S/NsysDAO.java` (`clavesDelEspejo`, `imeisDelTaller`, `altas`, `costes`, mapeo `coste`)
- Test: `T/HechoDAOTest.java`, `T/AltaTallerTest.java` (nuevos), `T/NsysDAOTest.java`

**Interfaces:**
- Consumes: tabla `Taller_hecho` (Task 1); `NsysDAO.SQL_COSTES` y `record NsysDAO.Coste(ClaveMovil clave, BigDecimal coste)` (pieza 1).
- Produces:
  - `enum TarjetaHecho { COLOR, GRADO, ALTA }`;
  - `record HechoTaller(String imeiSn, TarjetaHecho tarjeta, Integer idUsu, String usuario, LocalDateTime fecha, LocalDateTime volvio)`;
  - `HechoDAO`: `todas()` → `List<HechoTaller>`; `marcar(TarjetaHecho, Collection<String> imeis, int idUsu, LocalDateTime ahora)`;
    `desmarcar(TarjetaHecho, String imeiSn)` → `int`; `repasar(List<HechoTaller> borrar, List<HechoTaller> vuelven, LocalDateTime ahora)`;
  - `record AltaTaller(String imei, String modelo, String ultimo, boolean abierto, String tecnico, String cliente, int trabajos,
    LocalDateTime desde)`, con `record AltaTaller.Trabajo(...)` y `static List<AltaTaller> AltaTaller.de(List<Trabajo>)`;
  - `NsysDAO`: `Set<String> clavesDelEspejo()`, `List<String> imeisDelTaller()`, `List<AltaTaller> altas(Collection<String>)`,
    `Map<ClaveMovil, BigDecimal> costes(Collection<ClaveMovil>)`; constante `SQL_TRABAJOS_DE_IMEIS`.

- [ ] **Step 1: Write the failing tests**

`T/HechoDAOTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Taller_hecho con el JdbcTemplate mockeado, sin base de datos (como TallerDAOTest). */
class HechoDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HechoDAO dao = new HechoDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void lasMarcasConQuienPorSuNombre() {
        assertTrue(dao.todas().isEmpty());
        verify(jdbc).query(eq(HechoDAO.SQL_TODAS), any(RowMapper.class));
        assertEquals("SELECT h.IMEI_SN, h.TARJETA, h.ID_USU, u.NOMBRE_USUARIO, h.FECHA, h.VOLVIO FROM Taller_hecho h"
                + " LEFT JOIN Usuario u ON u.ID_USU = h.ID_USU", HechoDAO.SQL_TODAS);
    }

    @SuppressWarnings("unchecked")
    @Test void marcarEsUnaFilaPorImeiYVolverAMarcarPoneLaHoraNuevaYQuitaElVolvio() {
        dao.marcar(TarjetaHecho.GRADO, List.of("350000000000001", "350000000000002"), 7, AHORA);
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_MARCAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000001", "GRADO", 7, Timestamp.valueOf(AHORA)}, lote.getValue().get(0));
        assertEquals("INSERT INTO Taller_hecho (IMEI_SN, TARJETA, ID_USU, FECHA, VOLVIO) VALUES (?, ?, ?, ?, NULL)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), VOLVIO = NULL", HechoDAO.SQL_MARCAR);
    }

    @Test void deshacerQuitaLaMarcaDeEsaTarjeta() {
        dao.desmarcar(TarjetaHecho.ALTA, "350000000000088");
        verify(jdbc).update(HechoDAO.SQL_DESMARCAR, "350000000000088", "ALTA");
    }

    @SuppressWarnings("unchecked")
    @Test void elRepasoBorraUnasYHaceVolverOtrasYSinNadaNoEscribe() {
        HechoTaller seFue = new HechoTaller("350000000000001", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(1), null);
        HechoTaller sigue = new HechoTaller("350000000000002", TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA.minusHours(1), null);
        dao.repasar(List.of(seFue), List.of(sigue), AHORA);
        ArgumentCaptor<List<Object[]>> borradas = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_DESMARCAR), borradas.capture());
        assertArrayEquals(new Object[]{"350000000000001", "COLOR"}, borradas.getValue().get(0));
        ArgumentCaptor<List<Object[]>> vueltas = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_VOLVIO), vueltas.capture());
        assertArrayEquals(new Object[]{Timestamp.valueOf(AHORA), "350000000000002", "GRADO"}, vueltas.getValue().get(0));
        clearInvocations(jdbc);
        dao.repasar(List.of(), List.of(), AHORA);
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerHecho() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("350000000000001");
        when(rs.getString("TARJETA")).thenReturn("COLOR");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        assertEquals(new HechoTaller("350000000000001", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA, null), HechoDAO.hecho(rs, 0));
        when(rs.getTimestamp("VOLVIO")).thenReturn(Timestamp.valueOf(AHORA.plusHours(1)));
        assertEquals(AHORA.plusHours(1), HechoDAO.hecho(rs, 0).volvio());
    }
}
```

`T/AltaTallerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Los trabajos de un IMEI que NSYS no conoce (spec 0.9.22 §3.4). Datos inventados. */
class AltaTallerTest {

    private static final LocalDateTime DIA = LocalDateTime.of(2026, 10, 6, 9, 0);

    private static AltaTaller.Trabajo trabajo(String imei, String idRep, int dias, boolean terminado, String tecnico, String cliente) {
        LocalDateTime asignado = DIA.plusDays(dias);
        return new AltaTaller.Trabajo(imei, idRep, asignado, terminado ? asignado.plusHours(2) : null, tecnico, "14", cliente);
    }

    @Test void cuentanLosTrabajosHechosYLasAsignacionesAbiertasYElUltimoEsElMasReciente() {
        List<AltaTaller> altas = AltaTaller.de(List.of(
                trabajo("350000000000088", "A20261006_1", 0, true, "Técnico 2", null),
                trabajo("350000000000088", "R20261006_57", 0, true, "Técnico 2", "AMAZON"),
                trabajo("350000000000088", "A20261010_4", 4, false, "Técnico 1", null)));
        assertEquals(List.of(new AltaTaller("350000000000088", "14", "A20261010_4", true, "Técnico 1", null, 2, DIA)), altas);
    }

    @Test void unoPorImeiEnElOrdenEnQueLlegan() {
        List<AltaTaller> altas = AltaTaller.de(List.of(
                trabajo("350000000000099", "P20260918_12", 0, true, "Técnico 5", null),
                trabajo("350000000000088", "R20261006_57", 1, true, "Técnico 2", "AMAZON")));
        assertEquals(List.of("350000000000099", "350000000000088"), altas.stream().map(AltaTaller::imei).toList());
        assertEquals("P20260918_12", altas.get(0).ultimo());
        assertFalse(altas.get(0).abierto());
        assertEquals(1, altas.get(0).trabajos());
        assertEquals("AMAZON", altas.get(1).cliente());
    }

    @Test void sinTrabajosNoHayAltas() {
        assertTrue(AltaTaller.de(List.of()).isEmpty());
    }
}
```

`T/NsysDAOTest.java`: imports `org.springframework.jdbc.core.RowCallbackHandler`, `java.util.Set` y `java.util.ArrayList` si
faltan, y al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 2: «No está en NSYS» y la plantilla

    @Test void lasClavesDelEspejoSonUnaPorImeiSinEspaciosYEnMayusculas() throws SQLException {
        doAnswer(inv -> {
            RowCallbackHandler filas = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI_SN")).thenReturn("350000000000001", " f2lxabc123 ");
            filas.processRow(rs);
            filas.processRow(rs);
            return null;
        }).when(jdbc).query(eq("SELECT DISTINCT IMEI_SN FROM Nsys_movil"), any(RowCallbackHandler.class));
        assertEquals(Set.of("350000000000001", "F2LXABC123"), dao.clavesDelEspejo());
    }

    @Test void losImeisDelTallerDeUnaVez() {
        dao.imeisDelTaller();
        verify(jdbc).queryForList("SELECT DISTINCT IMEI FROM Reparacion", String.class);
    }

    @Test void losTrabajosDeLasAltasVanEnTandasConElClienteDelTrabajoODelTelefono() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.altas(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(1).endsWith(" WHERE r.IMEI IN (?) ORDER BY r.FECHA_ASIG, r.ID_REP"), sql.get(1));
        assertTrue(NsysDAO.SQL_TRABAJOS_DE_IMEIS.contains("JOIN Telefono tel ON tel.IMEI = r.IMEI"
                + " LEFT JOIN Cliente c ON c.ID_CLI = COALESCE(r.ID_CLI, tel.ID_CLI)"), NsysDAO.SQL_TRABAJOS_DE_IMEIS);
    }

    @SuppressWarnings("unchecked")
    @Test void losCostesDeUnosLotesSonSoloLosDeEsosLotes() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES + " AND IMEI_SN IN (?)"), any(RowMapper.class), eq("350000000000001")))
                .thenReturn(List.of(new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("412.35")),
                        new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-8000"), new BigDecimal("300.00"))));
        assertEquals(Map.of(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("412.35")),
                dao.costes(List.of(new ClaveMovil("350000000000001", "BT-9001"))));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=HechoDAOTest,AltaTallerTest,NsysDAOTest`
Expected: FAIL de compilación (no existen `HechoDAO`, `AltaTaller`, `clavesDelEspejo`…).

- [ ] **Step 3: Implement**

`S/TarjetaHecho.java`:

```java
package com.reparaciones.servidor.nsys;

/** Las tarjetas de «Por pasar a NSYS» que llevan «Hecho» (spec 0.9.22 §3.6): Cambio de color, Cambio de grado y No está en NSYS
 *  (ALTA). «Revisar a mano» no lleva: se va al corregir el móvil o con «Revisado». */
public enum TarjetaHecho { COLOR, GRADO, ALTA }
```

`S/HechoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_hecho (spec 0.9.22 §3.6): la marca «Hecho» de un IMEI en una tarjeta, quién ({@code usuario}: su nombre,
 *  null si ya no existe) y cuándo; {@code volvio}: la importación vio NSYS igual después de marcarla (null si no). Horas de
 *  Madrid. */
public record HechoTaller(String imeiSn, TarjetaHecho tarjeta, Integer idUsu, String usuario, LocalDateTime fecha,
                          LocalDateTime volvio) {}
```

`S/HechoDAO.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Acceso a Taller_hecho (spec 0.9.22 §3.6): las marcas «Hecho» de «Por pasar a NSYS», una por IMEI y tarjeta. Son unas
 *  decenas. */
@Repository
public class HechoDAO {

    static final String SQL_TODAS = "SELECT h.IMEI_SN, h.TARJETA, h.ID_USU, u.NOMBRE_USUARIO, h.FECHA, h.VOLVIO FROM Taller_hecho h"
            + " LEFT JOIN Usuario u ON u.ID_USU = h.ID_USU";
    /** Marcar otra vez una fila ya marcada pone quién y cuándo de ahora y quita el «volvió». */
    static final String SQL_MARCAR = "INSERT INTO Taller_hecho (IMEI_SN, TARJETA, ID_USU, FECHA, VOLVIO) VALUES (?, ?, ?, ?, NULL)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), VOLVIO = NULL";
    static final String SQL_DESMARCAR = "DELETE FROM Taller_hecho WHERE IMEI_SN = ? AND TARJETA = ?";
    static final String SQL_VOLVIO = "UPDATE Taller_hecho SET VOLVIO = ? WHERE IMEI_SN = ? AND TARJETA = ? AND VOLVIO IS NULL";

    private final JdbcTemplate jdbc;

    public HechoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HechoTaller> todas() {
        return jdbc.query(SQL_TODAS, HechoDAO::hecho);
    }

    /** «Hecho» o «Marcar todo»: marca estos IMEI en la tarjeta, por lotes de mil (NsysDAO#trozos). */
    @Transactional
    public void marcar(TarjetaHecho tarjeta, Collection<String> imeis, int idUsu, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        List<Object[]> filas = imeis.stream().map(imei -> new Object[]{imei, tarjeta.name(), idUsu, t}).toList();
        for (List<Object[]> lote : NsysDAO.trozos(filas)) jdbc.batchUpdate(SQL_MARCAR, lote);
    }

    /** «deshacer»: quita la marca. */
    public int desmarcar(TarjetaHecho tarjeta, String imeiSn) {
        return jdbc.update(SQL_DESMARCAR, imeiSn, tarjeta.name());
    }

    /** El repaso tras una importación (spec §3.6): unas marcas se borran y otras «vuelven», todo o nada. Sin nada que hacer no
     *  escribe. */
    @Transactional
    public void repasar(List<HechoTaller> borrar, List<HechoTaller> vuelven, LocalDateTime ahora) {
        if (!borrar.isEmpty())
            jdbc.batchUpdate(SQL_DESMARCAR, borrar.stream().map(h -> new Object[]{h.imeiSn(), h.tarjeta().name()}).toList());
        Timestamp t = Timestamp.valueOf(ahora);
        if (!vuelven.isEmpty())
            jdbc.batchUpdate(SQL_VOLVIO, vuelven.stream().map(h -> new Object[]{t, h.imeiSn(), h.tarjeta().name()}).toList());
    }

    static HechoTaller hecho(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        Timestamp volvio = rs.getTimestamp("VOLVIO");
        return new HechoTaller(rs.getString("IMEI_SN"), TarjetaHecho.valueOf(rs.getString("TARJETA")), usuario,
                rs.getString("NOMBRE_USUARIO"), rs.getTimestamp("FECHA").toLocalDateTime(),
                volvio == null ? null : volvio.toLocalDateTime());
    }
}
```

`S/AltaTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un IMEI con trabajos del taller que NSYS no conoce (spec 0.9.22 §3.4, «No está en NSYS»): su modelo del taller
 *  (Telefono.MODELO, un código del ERP como «14pro»), el último trabajo ({@code abierto}: una asignación A… sin terminar), su
 *  técnico y su cliente (el del trabajo o, si no tiene, el del teléfono), cuántos trabajos tiene y desde cuándo (su primera
 *  asignación). Horas de Madrid. */
public record AltaTaller(String imei, String modelo, String ultimo, boolean abierto, String tecnico, String cliente,
                         int trabajos, LocalDateTime desde) {

    /** Una fila de Reparacion de esos IMEI, ya en hora de Madrid ({@code terminado}: FECHA_FIN). */
    record Trabajo(String imei, String idRep, LocalDateTime asignado, LocalDateTime terminado, String tecnico, String modelo,
                   String cliente) {

        boolean esAsignacion() {
            return idRep.startsWith("A");
        }
    }

    private static final Comparator<Trabajo> ORDEN = Comparator.comparing(Trabajo::asignado,
            Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Trabajo::idRep);

    /** Por IMEI, en el orden en que llegan: cuentan los trabajos hechos (R, G, P) y las asignaciones abiertas (una A terminada ya
     *  tiene su trabajo hecho); el último es el más reciente de todas sus filas (por asignación y luego por id) y «desde», la
     *  primera asignación. Es puro. */
    static List<AltaTaller> de(List<Trabajo> trabajos) {
        Map<String, List<Trabajo>> porImei = new LinkedHashMap<>();
        for (Trabajo t : trabajos) porImei.computeIfAbsent(t.imei(), k -> new ArrayList<>()).add(t);
        List<AltaTaller> altas = new ArrayList<>();
        porImei.forEach((imei, delImei) -> {
            Trabajo ultimo = delImei.stream().max(ORDEN).orElseThrow();
            Trabajo primero = delImei.stream().min(ORDEN).orElseThrow();
            int cuenta = (int) delImei.stream().filter(t -> !t.esAsignacion() || t.terminado() == null).count();
            altas.add(new AltaTaller(imei, ultimo.modelo(), ultimo.idRep(), ultimo.esAsignacion() && ultimo.terminado() == null,
                    ultimo.tecnico(), ultimo.cliente(), cuenta, primero.asignado()));
        });
        return altas;
    }
}
```

`S/NsysDAO.java`:
- imports: `org.springframework.jdbc.core.RowCallbackHandler`, `java.util.HashSet`, `java.util.Set` (los que falten).
- junto a `SQL_TRABAJOS_ABIERTOS`:

```java
    /** «No está en NSYS» (0.9.22 §3.4): las filas de Reparacion de unos IMEI con su técnico, el modelo del teléfono y el cliente del
     *  trabajo o, si no tiene, el del teléfono. Se completa con las marcas del IN y ORDEN_TRABAJOS. */
    static final String SQL_TRABAJOS_DE_IMEIS = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN, t.NOMBRE AS TECNICO, tel.MODELO,"
            + " c.NOMBRE AS CLIENTE FROM Reparacion r JOIN Tecnico t ON t.ID_TEC = r.ID_TEC JOIN Telefono tel ON tel.IMEI = r.IMEI"
            + " LEFT JOIN Cliente c ON c.ID_CLI = COALESCE(r.ID_CLI, tel.ID_CLI) WHERE r.IMEI IN (";
```

- el mapeo del coste sale a un método y `cargarCostes` lo usa:

```java
    static Coste coste(ResultSet rs, int n) throws SQLException {
        return new Coste(new ClaveMovil(rs.getString("IMEI_SN"), rs.getString("BATCH")), rs.getBigDecimal("COSTE"));
    }
```

  (en `cargarCostes`, `jdbc.query(SQL_COSTES, NsysDAO::coste)` en lugar de la lambda).
- métodos nuevos, en la sección de los trabajos del taller:

```java
    /** Los IMEI/SN del espejo por {@link #claveCruce} (0.9.22 §3.4), uno por IMEI sea cual sea su lote: para cruzarlos en Java con
     *  los del taller (las tablas tienen intercalaciones distintas). Una consulta sin pasar por una lista de filas. */
    public Set<String> clavesDelEspejo() {
        Set<String> claves = new HashSet<>();
        jdbc.query("SELECT DISTINCT IMEI_SN FROM Nsys_movil",
                (RowCallbackHandler) rs -> claves.add(claveCruce(rs.getString("IMEI_SN"))));
        return claves;
    }

    /** Los IMEI con algún trabajo del taller, de cualquier categoría y fecha (0.9.22 §3.4). */
    public List<String> imeisDelTaller() {
        return jdbc.queryForList("SELECT DISTINCT IMEI FROM Reparacion", String.class);
    }

    /** Lo que «No está en NSYS» enseña de estos IMEI ({@link AltaTaller#de}), en tandas de {@value #TANDA_IN}. Horas de UTC a
     *  Madrid. */
    public List<AltaTaller> altas(Collection<String> imeis) {
        List<AltaTaller.Trabajo> trabajos = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            trabajos.addAll(jdbc.query(SQL_TRABAJOS_DE_IMEIS + marcas(tanda.size()) + ")" + ORDEN_TRABAJOS,
                    NsysDAO::trabajoDeAlta, tanda.toArray()));
        return AltaTaller.de(trabajos);
    }

    static AltaTaller.Trabajo trabajoDeAlta(ResultSet rs, int n) throws SQLException {
        return new AltaTaller.Trabajo(rs.getString("IMEI"), rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")),
                utcAMadrid(rs.getTimestamp("FECHA_FIN")), rs.getString("TECNICO"), rs.getString("MODELO"), rs.getString("CLIENTE"));
    }
```

- y, junto a `cargarCostes` y `guardarCostes`:

```java
    /** El coste de NSYS («Total cost») de estos lotes, para la plantilla de color (0.9.22 §4): en tandas por IMEI de
     *  {@value #TANDA_IN}; un lote sin coste no sale. */
    public Map<ClaveMovil, BigDecimal> costes(Collection<ClaveMovil> lotes) {
        Set<ClaveMovil> pedidos = new HashSet<>(lotes);
        List<String> imeis = pedidos.stream().map(ClaveMovil::imeiSn).distinct().toList();
        Map<ClaveMovil, BigDecimal> mapa = new HashMap<>();
        for (List<String> tanda : trozos(imeis, TANDA_IN))
            for (Coste c : jdbc.query(SQL_COSTES + " AND IMEI_SN IN (" + marcas(tanda.size()) + ")", NsysDAO::coste,
                    tanda.toArray()))
                if (pedidos.contains(c.clave())) mapa.put(c.clave(), c.coste());
        return mapa;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=HechoDAOTest,AltaTallerTest,NsysDAOTest,ImportadorNsysTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/TarjetaHecho.java src/main/java/com/reparaciones/servidor/nsys/HechoTaller.java src/main/java/com/reparaciones/servidor/nsys/HechoDAO.java src/main/java/com/reparaciones/servidor/nsys/AltaTaller.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/test/java/com/reparaciones/servidor/nsys/HechoDAOTest.java src/test/java/com/reparaciones/servidor/nsys/AltaTallerTest.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: marcas Hecho, moviles del taller que NSYS no conoce y costes de unos lotes (0.9.22, pieza 2)"
```

---

### Task 5: Servidor — las cuatro tarjetas, las marcas y su repaso tras la importación

**Files:**
- Create: `S/FilaColorNsys.java`, `S/FilaGradoNsys.java`, `S/FilaAltaNsys.java`, `S/FilaRevisarNsys.java`, `S/TarjetasPorPasar.java`,
  `S/TarjetasNsys.java`, `S/PorPasarNsys.java`
- Modify: `S/ImportadorNsys.java` (constructor y `procesar`)
- Test: `T/TarjetasNsysTest.java`, `T/PorPasarNsysTest.java` (nuevos), `T/ImportadorNsysTest.java`

**Interfaces:**
- Consumes: `TallerNsys.movilesPorPasar()`, `TallerNsys.MovilPorPasar`, `TallerNsys.porQue(CambioTaller)`, `ProductoNsys.Poner` (Task 3);
  `TallerDAO.avisosPorRevisar/avisoPorId/revisar` y `AvisoTaller.POR_REVISAR` (Task 2); `HechoDAO`, `HechoTaller`, `TarjetaHecho`,
  `AltaTaller`, `NsysDAO.clavesDelEspejo/imeisDelTaller/altas` (Task 4).
- Produces:
  - records de la API (los cinco con `hechoPor`, `hechoEn`, `hechoVolvio` donde llevan «Hecho»):
    `FilaColorNsys`, `FilaGradoNsys`, `FilaAltaNsys`, `FilaRevisarNsys`, `TarjetasPorPasar(color, grado, altas, revisar)`;
  - `TarjetasNsys` (puro): `clave`, `porClave`, `marca`, `marcada`, `color`, `grado`, `altas`, `revisar`, `filas`, `repasar` y
    `record Repaso(List<HechoTaller> borrar, List<HechoTaller> vuelven)`;
  - `PorPasarNsys` (`@Service`): `tarjetas()`, `marcar(String tarjeta, List<String> imeis, int idUsu)`,
    `desmarcar(String tarjeta, String imeiSn)`, `revisado(int idAviso, int idUsu)`, `repasarHechos(LocalDateTime exportacion)`,
    `altas()` (de paquete); constantes `ACCION_REVISADO` = `"REVISAR_AVISO_TALLER"` y `MAX_MARCAR` = 500;
  - `ImportadorNsys` recibe `PorPasarNsys` en el constructor (detrás de `TallerNsys`) y llama a `repasarHechos(f.fecha())` tras el
    cierre de cada Items ACTUAL OK.

- [ ] **Step 1: Write the failing tests**

`T/TarjetasNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Las reglas de las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3) y el repaso de las marcas (§3.6). Datos inventados:
 *  iPhone 13 128GB de TallerDePrueba; los cambios de TallerDePrueba.vigente son de ayer (HOY - 1 día). */
class TarjetasNsysTest {

    private static final String IMEI = "350000000000001";
    private static final String OTRO = "350000000000002";
    private static final LocalDateTime AYER = HOY.minusDays(1);

    private static TallerNsys.MovilPorPasar porPasar(MovilNsys m, LoQueVale.Valor v, ProductoNsys.Poner poner,
                                                     CambioTaller... cambios) {
        return new TallerNsys.MovilPorPasar(m, v, List.of(cambios), poner);
    }

    private static LoQueVale.Valor vale(String color, String grade, String colorNsys, String gradoNsys) {
        return new LoQueVale.Valor(color, grade, null, colorNsys, gradoNsys, null, true);
    }

    private static HechoTaller marca(String imei, TarjetaHecho tarjeta, LocalDateTime fecha, LocalDateTime volvio) {
        return new HechoTaller(imei, tarjeta, 7, "Supertécnico 1", fecha, volvio);
    }

    @Test void unMovilQueCambiaDeColorYDeGradoSaleEnLasDosTarjetas() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rojo", "A", "Azul", "B"),
                new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false),
                vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A", "B"));
        FilaColorNsys c = TarjetasNsys.color(List.of(p), Map.of()).get(0);
        assertEquals("Blue", c.colorDe(), "como lo escribe NSYS");
        assertEquals("(PRODUCT)RED", c.colorA());
        assertEquals("Apple iPhone 13 128GB Blue", c.productoNsys());
        assertEquals("B", c.gradoNsys(), "el grado de NSYS hoy: el que lleva la plantilla");
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", c.ponerProducto());
        assertFalse(c.compruebalo());
        assertTrue(c.tambienGrado());
        assertEquals(List.of("COLOR"), c.porQue().stream().map(PorQueTaller::dato).toList());
        assertEquals(AYER, c.desde());
        assertNull(c.hechoEn());
        FilaGradoNsys g = TarjetasNsys.grado(List.of(p), Map.of()).get(0);
        assertEquals("B", g.gradoDe());
        assertEquals("A", g.gradoA());
        assertEquals("Apple iPhone 13 128GB Blue", g.productoNsys());
        assertTrue(g.conColor());
        assertEquals(List.of("GRADO"), g.porQue().stream().map(PorQueTaller::dato).toList());
    }

    @Test void soloGradoNoSaleEnColorYSinProductoElColorNuevoEsElDelMaestro() {
        TallerNsys.MovilPorPasar grado = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A100", null, "B"), null,
                vigente(2, IMEI, GRADO, "A100", "B"));
        assertTrue(TarjetasNsys.color(List.of(grado), Map.of()).isEmpty());
        assertFalse(TarjetasNsys.grado(List.of(grado), Map.of()).get(0).conColor());
        TallerNsys.MovilPorPasar sinProducto = porPasar(movil(OTRO, "Azul", "B"), vale("Verde", "B", "Azul", null), null,
                vigente(3, OTRO, COLOR, "Verde", "Azul"));
        FilaColorNsys c = TarjetasNsys.color(List.of(sinProducto), Map.of()).get(0);
        assertNull(c.ponerProducto(), "búscalo a mano");
        assertEquals("Verde", c.colorA());
        assertFalse(c.tambienGrado());
    }

    @Test void unProductoDelMaestroTalCualLlevaCompruebalo() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rosa", "B", "Azul", null),
                new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", true), vigente(1, IMEI, COLOR, "Rosa", "Azul"));
        FilaColorNsys c = TarjetasNsys.color(List.of(p), Map.of()).get(0);
        assertTrue(c.compruebalo());
        assertEquals("Pink", c.colorA());
    }

    @Test void laMarcaValeSiNoEsAnteriorAlCambioYQuedaComoVueltaConSuHora() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A", null, "B"), null,
                vigente(2, IMEI, GRADO, "A", "B"));
        FilaGradoNsys conMarca = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, HOY, null)))).get(0);
        assertEquals("Supertécnico 1", conMarca.hechoPor());
        assertEquals(HOY, conMarca.hechoEn());
        assertTrue(TarjetasNsys.marcada(conMarca.hechoEn(), conMarca.hechoVolvio()));
        FilaGradoNsys deAntes = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, AYER.minusHours(1), null)))).get(0);
        assertNull(deAntes.hechoEn(), "una marca de un cambio anterior del mismo móvil no vale");
        FilaGradoNsys vuelta = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, HOY, HOY.plusHours(1))))).get(0);
        assertEquals(HOY.plusHours(1), vuelta.hechoVolvio());
        assertFalse(TarjetasNsys.marcada(vuelta.hechoEn(), vuelta.hechoVolvio()), "vuelta: la fila cuenta otra vez");
        assertFalse(TarjetasNsys.marcada(null, null));
    }

    @Test void laMarcaVaPorTarjetaYPorImeiSinMayusculas() {
        TallerNsys.MovilPorPasar p = porPasar(movil("F2LXABC123", "Azul", "B"), vale("Rojo", "A", "Azul", "B"), null,
                vigente(1, "F2LXABC123", COLOR, "Rojo", "Azul"), vigente(2, "F2LXABC123", GRADO, "A", "B"));
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(List.of(marca("f2lxabc123", TarjetaHecho.COLOR, HOY, null)));
        assertEquals(HOY, TarjetasNsys.color(List.of(p), marcas).get(0).hechoEn());
        assertNull(TarjetasNsys.grado(List.of(p), marcas).get(0).hechoEn(), "marcar el color no marca el grado");
    }

    @Test void cadaListaVaDeLoMasAntiguoALoMasReciente() {
        TallerNsys.MovilPorPasar nuevo = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A", null, "B"), null,
                vigente(2, IMEI, GRADO, "A", "B", HOY));
        TallerNsys.MovilPorPasar viejo = porPasar(movil(OTRO, "Azul", "B"), vale("Azul", "C", null, "B"), null,
                vigente(3, OTRO, GRADO, "C", "B", HOY.minusDays(3)));
        assertEquals(List.of(OTRO, IMEI),
                TarjetasNsys.grado(List.of(nuevo, viejo), Map.of()).stream().map(FilaGradoNsys::imeiSn).toList());
    }

    @Test void lasAltasLlevanSuCategoriaYSuMarca() {
        AltaTaller a = new AltaTaller("350000000000088", "14", "R20261006_57", false, "Técnico 2", "AMAZON", 2,
                LocalDateTime.of(2026, 10, 6, 9, 0));
        FilaAltaNsys f = TarjetasNsys.altas(List.of(a),
                TarjetasNsys.porClave(List.of(marca("350000000000088", TarjetaHecho.ALTA, HOY, null)))).get(0);
        assertEquals("REPARACION", f.categoria());
        assertEquals("R20261006_57", f.ultimoTrabajo());
        assertEquals("14", f.modelo());
        assertEquals(2, f.trabajos());
        assertEquals(HOY, f.hechoEn());
    }

    @Test void revisarLlevaElModeloDelEspejoYSoloLosAvisosDeRevisar() {
        AvisoTaller sinColor = new AvisoTaller(1, IMEI, "R20261022_2", AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow",
                null, "chasis chai13yellow nuevo", 8, "Técnico 2", AYER);
        AvisoTaller deshecho = new AvisoTaller(2, IMEI, "R20261022_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA,
                COLOR, "Rojo", "Azul", "chasis chai13red nuevo", 8, "Técnico 2", AYER);
        AvisoTaller fuera = new AvisoTaller(3, OTRO, "R20261022_4", AvisoTaller.Tipo.SIN_DESHACER, AvisoTaller.Causa.EDITADA,
                GRADO, "A", null, "batería bati13 nueva", 8, "Técnico 2", HOY);
        List<FilaRevisarNsys> filas = TarjetasNsys.revisar(List.of(sinColor, deshecho, fuera), Map.of(IMEI, movil(IMEI, "Azul", "B")));
        assertEquals(List.of(1, 3), filas.stream().map(FilaRevisarNsys::idAviso).toList());
        assertEquals("13", filas.get(0).modelo());
        assertEquals("128GB", filas.get(0).capacidad());
        assertEquals("SIN_COLOR", filas.get(0).tipo());
        assertNull(filas.get(1).modelo(), "fuera del espejo, sin modelo");
        assertEquals("EDITADA", filas.get(1).causa());
        assertEquals("GRADO", filas.get(1).dato());
    }

    @Test void lasFilasDelRepasoSonLasDeColorGradoYAltas() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rojo", "A", "Azul", "B"), null,
                vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A", "B"));
        AltaTaller a = new AltaTaller("350000000000088", "14", "R20261006_57", false, "Técnico 2", null, 1, HOY.minusDays(19));
        Map<String, LocalDateTime> filas = TarjetasNsys.filas(List.of(p), List.of(a));
        assertEquals(Set.of(TarjetasNsys.clave(TarjetaHecho.COLOR, IMEI), TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI),
                TarjetasNsys.clave(TarjetaHecho.ALTA, "350000000000088")), filas.keySet());
        assertEquals(AYER, filas.get(TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI)));
    }

    @Test void elRepasoBorraLoQueYaNoEstaYLoDeOtroCambioYHaceVolverLoQueSigueIgual() {
        LocalDateTime fichero = HOY.plusHours(1);
        HechoTaller seFue = marca(IMEI, TarjetaHecho.COLOR, HOY, null);
        HechoTaller sigue = marca(IMEI, TarjetaHecho.GRADO, HOY, null);
        HechoTaller despuesDelFichero = marca(OTRO, TarjetaHecho.GRADO, HOY.plusHours(2), null);
        HechoTaller yaVuelta = marca(OTRO, TarjetaHecho.COLOR, HOY, HOY);
        HechoTaller deOtroCambio = marca("350000000000003", TarjetaHecho.GRADO, AYER.minusDays(1), null);
        Map<String, LocalDateTime> filas = Map.of(
                TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI), AYER,
                TarjetasNsys.clave(TarjetaHecho.GRADO, OTRO), AYER,
                TarjetasNsys.clave(TarjetaHecho.COLOR, OTRO), AYER,
                TarjetasNsys.clave(TarjetaHecho.GRADO, "350000000000003"), AYER);
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(List.of(seFue, sigue, despuesDelFichero, yaVuelta, deOtroCambio), filas,
                fichero);
        assertEquals(List.of(seFue, deOtroCambio), r.borrar());
        assertEquals(List.of(sigue), r.vuelven(), "un fichero de después de marcar que no lo trae: vuelve a salir");
    }
}
```

`T/PorPasarNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3) con todo mockeado y la hora fija (25/10/2026 12:31 en Madrid). */
class PorPasarNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 12, 31);
    private static final String IMEI = "350000000000001";
    private final TallerNsys taller = mock(TallerNsys.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final HechoDAO hechos = mock(HechoDAO.class);
    private final LogDAO registro = mock(LogDAO.class);
    private final PorPasarNsys servicio = new PorPasarNsys(taller, tallerDao, nsys, hechos, registro,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static HttpStatusCode estado(Executable accion) {
        return assertThrows(ResponseStatusException.class, accion).getStatusCode();
    }

    private static TallerNsys.MovilPorPasar soloGrado(String imei) {
        return new TallerNsys.MovilPorPasar(movil(imei, "Azul", "B"), new LoQueVale.Valor("Azul", "A100", null, null, "B", null, true),
                List.of(vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))), null);
    }

    private static AvisoTaller sinGrado() {
        return new AvisoTaller(5, IMEI, "R20261024_19", AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "ID", null,
                "chasis chai13blue nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void lasTarjetasJuntanLoDelTallerLasAltasYLosAvisos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI, "F2LXABC123"));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI, "f2lxabc123", "350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14", "R20261006_57",
                false, "Técnico 2", null, 1, AHORA.minusDays(19))));
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinGrado()));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.color().isEmpty());
        assertEquals(List.of(IMEI), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
        assertEquals(List.of("350000000000088"), t.altas().stream().map(FilaAltaNsys::imeiSn).toList(),
                "un número de serie en minúsculas cruza con el del espejo");
        assertEquals("13", t.revisar().get(0).modelo());
    }

    @Test void sinImeisFueraDeNsysNiAvisosNoSeLeeNadaMas() {
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.altas().isEmpty());
        assertTrue(t.revisar().isEmpty());
        verify(nsys, never()).altas(any());
        verify(nsys, never()).porImeis(any());
    }

    @Test void marcarLimpiaLosImeisYPoneLaHoraDeMadrid() {
        servicio.marcar("GRADO", List.of(" 350000000000001 ", IMEI, "350000000000002"), 7);
        verify(hechos).marcar(TarjetaHecho.GRADO, List.of(IMEI, "350000000000002"), 7, AHORA);
    }

    @Test void marcarRechazaLoQueNoVale() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("REVISAR", List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar(null, List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", List.of(), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", Arrays.asList(IMEI, " "), 7)));
        List<String> muchos = new ArrayList<>();
        for (int n = 0; n < 501; n++) muchos.add(String.format("3500000000%05d", n));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", muchos, 7)));
        verifyNoInteractions(hechos);
    }

    @Test void deshacerQuitaLaMarcaDeEsaTarjeta() {
        servicio.desmarcar("ALTA", " 350000000000088 ");
        verify(hechos).desmarcar(TarjetaHecho.ALTA, "350000000000088");
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("ALTA", " ")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("OTRA", IMEI)));
    }

    @Test void revisadoApuntaQuienYCuandoYLoDejaEnElRegistro() {
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(1);
        servicio.revisado(5, 7);
        verify(registro).insertar(7, "REVISAR_AVISO_TALLER", "IMEI: 350000000000001, SIN_GRADO de R20261024_19", null);
    }

    @Test void revisadoDeUnAvisoQueNoExisteOYaEstaRevisado() {
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.revisado(9, 7)));
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(5, 7)));
        verifyNoInteractions(registro);
    }

    @Test void elRepasoSinMarcasNoLeeNadaMas() {
        servicio.repasarHechos(AHORA);
        verifyNoInteractions(taller, nsys);
        verify(hechos, never()).repasar(any(), any(), any());
    }

    @Test void elRepasoBorraYHaceVolverConLaHoraDeAhora() {
        HechoTaller seFue = new HechoTaller("350000000000009", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        HechoTaller sigue = new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        when(hechos.todas()).thenReturn(List.of(seFue, sigue));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos).repasar(List.of(seFue), List.of(sigue), AHORA);
    }

    @Test void unRepasoSinNadaQueHacerNoEscribe() {
        when(hechos.todas()).thenReturn(List.of(new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA, null)));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos, never()).repasar(any(), any(), any());
    }
}
```

`T/ImportadorNsysTest.java`:
- campo nuevo `private final PorPasarNsys porPasar = mock(PorPasarNsys.class);`, **antes** del campo `importador` (~línea 37),
  que se construye con él; y, en las cuatro construcciones de `ImportadorNsys`, `porPasar` detrás de `taller` (p. ej.
  `new ImportadorNsys(drive, dao, taller, porPasar, "carpeta-1", "maestro-1", …)`);
- pruebas nuevas, junto a las del cierre de lo del taller (añade `import org.mockito.InOrder;` si falta):

```java
    @Test void trasCerrarLoDelTallerSeRepasanLasMarcasConLaHoraDelFichero() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(taller, porPasar);
        orden.verify(taller).cerrar();
        orden.verify(porPasar).repasarHechos(LocalDateTime.of(2026, 10, 16, 9, 1));
    }

    @Test void siFallaElRepasoLaPasadaSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        doThrow(new IllegalStateException("fallo")).when(porPasar).repasarHechos(any());

        assertDoesNotThrow(importador::pasada);
        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest,ImportadorNsysTest`
Expected: FAIL de compilación (no existen `TarjetasNsys`, `PorPasarNsys` ni las filas).

- [ ] **Step 3: Implement**

Los cinco records de la API (`S/`, cada uno en su fichero, que empieza por `package com.reparaciones.servidor.nsys;` y lleva
`import io.swagger.v3.oas.annotations.media.Schema;`, `java.time.LocalDateTime` y `java.util.List` donde haga falta;
`TarjetasPorPasar` solo necesita `java.util.List`):

```java
/** Una fila de «Cambio de color» (spec 0.9.22 §3.2): el color de NSYS hoy y el nuevo como los escribe NSYS ({@code colorDe} →
 *  {@code colorA}; sin producto, el color nuevo es el del maestro), el producto de hoy y su grado (el que lleva la plantilla), el
 *  producto a poner ({@code ponerProducto}: null, «búscalo a mano»; {@code compruebalo}: sale del nombre del maestro tal cual), si
 *  el móvil está también en «Cambio de grado», por qué y desde cuándo (su cambio de color vigente) y la marca «Hecho» que vale
 *  ({@code hechoPor}, {@code hechoEn}; {@code hechoVolvio}: la importación vio NSYS igual y la fila cuenta otra vez). Horas de
 *  Madrid. */
public record FilaColorNsys(String imeiSn, @Schema(nullable = true) String modelo,
                            @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                            @Schema(nullable = true) String colorDe, @Schema(nullable = true) String colorA,
                            @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                            @Schema(nullable = true) String ponerProducto, boolean compruebalo, boolean tambienGrado,
                            List<PorQueTaller> porQue, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                            @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio) {}
```

```java
/** Una fila de «Cambio de grado» (spec 0.9.22 §3.3): el grado de NSYS hoy y el nuevo ({@code gradoDe} → {@code gradoA}, enteros,
 *  con el «100» y el «Ms»), el producto de NSYS hoy, si el móvil cambia también de color (primero el color), por qué, desde cuándo
 *  (su cambio de grado vigente) y la marca «Hecho» que vale, como en FilaColorNsys. */
public record FilaGradoNsys(String imeiSn, @Schema(nullable = true) String modelo,
                            @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                            @Schema(nullable = true) String gradoDe, @Schema(nullable = true) String gradoA,
                            @Schema(nullable = true) String productoNsys, boolean conColor, List<PorQueTaller> porQue,
                            LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                            @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio) {}
```

```java
/** Una fila de «No está en NSYS» (spec 0.9.22 §3.4): el modelo del taller (código del ERP), el último trabajo con su
 *  {@code categoria} (REPARACION, GLASS o PULIDO) y si es una asignación abierta, su técnico y su cliente, cuántos trabajos, desde
 *  cuándo (el primero) y la marca «Hecho» que vale, como en FilaColorNsys. */
public record FilaAltaNsys(String imeiSn, @Schema(nullable = true) String modelo, String ultimoTrabajo, String categoria,
                           boolean abierto, @Schema(nullable = true) String tecnico, @Schema(nullable = true) String cliente,
                           int trabajos, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                           @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio) {}
```

```java
/** Una fila de «Revisar a mano» (spec 0.9.22 §3.5): un aviso del taller ({@code tipo} SIN_COLOR, SIN_GRADO o SIN_DESHACER;
 *  {@code causa} BORRADA o EDITADA en SIN_DESHACER; {@code dato} COLOR o GRADO; {@code de}, {@code a} y {@code motivo} como en
 *  Taller_aviso) con el modelo del espejo (null si el IMEI ya no está), su reparación, quién la guardó y cuándo. */
public record FilaRevisarNsys(int idAviso, String imeiSn, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              String tipo, @Schema(nullable = true) String causa, String dato, @Schema(nullable = true) String de,
                              @Schema(nullable = true) String a, @Schema(nullable = true) String motivo, String idRep,
                              @Schema(nullable = true) String usuario, LocalDateTime fecha) {}
```

```java
/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar) {}
```

`S/TarjetasNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Las reglas de las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3): las listas a partir de los móviles con algo del
 *  taller vigente (TallerNsys#movilesPorPasar), los IMEI que NSYS no conoce y los avisos por revisar; la marca «Hecho» de cada fila
 *  y su repaso tras cada importación (§3.6, con los ajustes 1 y 2 del plan). Es puro. */
final class TarjetasNsys {

    private TarjetasNsys() {}

    /** La marca «Hecho» que vale para una fila; NINGUNA si no hay. */
    record Marca(String por, LocalDateTime en, LocalDateTime volvio) {
        static final Marca NINGUNA = new Marca(null, null, null);
    }

    /** Lo que hace el repaso de las marcas tras una importación OK. */
    record Repaso(List<HechoTaller> borrar, List<HechoTaller> vuelven) {}

    /** La clave de una marca: la tarjeta y el IMEI por {@link NsysDAO#claveCruce}. */
    static String clave(TarjetaHecho tarjeta, String imeiSn) {
        return tarjeta.name() + "|" + NsysDAO.claveCruce(imeiSn);
    }

    static Map<String, HechoTaller> porClave(List<HechoTaller> marcas) {
        Map<String, HechoTaller> mapa = new HashMap<>();
        for (HechoTaller h : marcas) mapa.put(clave(h.tarjeta(), h.imeiSn()), h);
        return mapa;
    }

    /** La marca de una fila que sale desde {@code desde}: la del IMEI y la tarjeta, si no es anterior a la fila (una anterior era
     *  de un cambio de antes del mismo móvil y no vale). */
    static Marca marca(Map<String, HechoTaller> marcas, TarjetaHecho tarjeta, String imeiSn, LocalDateTime desde) {
        HechoTaller h = marcas.get(clave(tarjeta, imeiSn));
        if (h == null || (desde != null && h.fecha().isBefore(desde))) return Marca.NINGUNA;
        return new Marca(h.usuario(), h.fecha(), h.volvio());
    }

    /** Una fila marcada que vale y no ha vuelto: no cuenta ni va en las plantillas ni en «Copiar lista». */
    static boolean marcada(LocalDateTime hechoEn, LocalDateTime hechoVolvio) {
        return hechoEn != null && hechoVolvio == null;
    }

    /** El cambio vigente de {@code dato} de un móvil (el de mayor id si hubiera dos, como LoQueVale). */
    private static Optional<CambioTaller> cambio(TallerNsys.MovilPorPasar p, DatoTaller dato) {
        return p.cambios().stream().filter(c -> c.dato() == dato).max(Comparator.comparingInt(CambioTaller::id));
    }

    private static List<PorQueTaller> porQue(TallerNsys.MovilPorPasar p, DatoTaller dato) {
        return p.cambios().stream().filter(c -> c.dato() == dato).map(TallerNsys::porQue).toList();
    }

    /** «Cambio de color» (§3.2): los móviles con un cambio de color vigente. Si el color que vale es el de NSYS (aún sin cerrar),
     *  el producto es el de ahora. */
    static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        List<FilaColorNsys> filas = new ArrayList<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            Optional<CambioTaller> c = cambio(p, DatoTaller.COLOR);
            if (c.isEmpty()) continue;
            MovilNsys m = p.movil();
            ProductoNsys.Poner poner = p.valor().colorNsys() == null ? new ProductoNsys.Poner(m.goods(), false) : p.poner();
            String deNsys = GrafiasNsys.colorDelGoods(m.goods());
            String colorA = poner == null ? p.valor().color() : GrafiasNsys.colorDelGoods(poner.producto());
            LocalDateTime desde = c.get().fecha();
            Marca h = marca(marcas, TarjetaHecho.COLOR, m.imeiSn(), desde);
            filas.add(new FilaColorNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), deNsys != null ? deNsys : m.color(),
                    colorA, m.goods(), m.grade(), poner == null ? null : poner.producto(), poner != null && poner.compruebalo(),
                    cambio(p, DatoTaller.GRADO).isPresent(), porQue(p, DatoTaller.COLOR), desde, h.por(), h.en(), h.volvio()));
        }
        filas.sort(Comparator.comparing(FilaColorNsys::desde).thenComparing(FilaColorNsys::imeiSn));
        return filas;
    }

    /** «Cambio de grado» (§3.3): los móviles con un cambio de grado vigente, también los que cambian de color. */
    static List<FilaGradoNsys> grado(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        List<FilaGradoNsys> filas = new ArrayList<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            Optional<CambioTaller> c = cambio(p, DatoTaller.GRADO);
            if (c.isEmpty()) continue;
            MovilNsys m = p.movil();
            LocalDateTime desde = c.get().fecha();
            Marca h = marca(marcas, TarjetaHecho.GRADO, m.imeiSn(), desde);
            filas.add(new FilaGradoNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.grade(), p.valor().grade(),
                    m.goods(), cambio(p, DatoTaller.COLOR).isPresent(), porQue(p, DatoTaller.GRADO), desde, h.por(), h.en(),
                    h.volvio()));
        }
        filas.sort(Comparator.comparing(FilaGradoNsys::desde).thenComparing(FilaGradoNsys::imeiSn));
        return filas;
    }

    /** «No está en NSYS» (§3.4). */
    static List<FilaAltaNsys> altas(List<AltaTaller> altas, Map<String, HechoTaller> marcas) {
        List<FilaAltaNsys> filas = new ArrayList<>();
        for (AltaTaller a : altas) {
            Marca h = marca(marcas, TarjetaHecho.ALTA, a.imei(), a.desde());
            filas.add(new FilaAltaNsys(a.imei(), a.modelo(), a.ultimo(), Rastro.categoria(a.ultimo()), a.abierto(), a.tecnico(),
                    a.cliente(), a.trabajos(), a.desde(), h.por(), h.en(), h.volvio()));
        }
        filas.sort(Comparator.comparing(FilaAltaNsys::desde, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FilaAltaNsys::imeiSn));
        return filas;
    }

    /** «Revisar a mano» (§3.5), en el orden de los avisos, con el modelo de la fila más reciente del espejo de su IMEI
     *  ({@code espejo}, por {@link NsysDAO#claveCruce}). */
    static List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Map<String, MovilNsys> espejo) {
        return avisos.stream().filter(a -> AvisoTaller.POR_REVISAR.contains(a.tipo())).map(a -> {
            MovilNsys m = espejo.get(NsysDAO.claveCruce(a.imeiSn()));
            return new FilaRevisarNsys(a.id(), a.imeiSn(), m == null ? null : m.modelo(), m == null ? null : m.modeloMaestro(),
                    m == null ? null : m.capacidad(), a.tipo().name(), a.causa() == null ? null : a.causa().name(),
                    a.dato().name(), a.de(), a.a(), a.motivo(), a.idRep(), a.usuario(), a.fecha());
        }).toList();
    }

    /** Las filas de Color, Grado y No está en NSYS que hay ahora, por clave de marca, con su «desde» (para el repaso). */
    static Map<String, LocalDateTime> filas(List<TallerNsys.MovilPorPasar> moviles, List<AltaTaller> altas) {
        Map<String, LocalDateTime> filas = new HashMap<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            cambio(p, DatoTaller.COLOR).ifPresent(c -> filas.put(clave(TarjetaHecho.COLOR, p.movil().imeiSn()), c.fecha()));
            cambio(p, DatoTaller.GRADO).ifPresent(c -> filas.put(clave(TarjetaHecho.GRADO, p.movil().imeiSn()), c.fecha()));
        }
        for (AltaTaller a : altas) filas.put(clave(TarjetaHecho.ALTA, a.imei()), a.desde());
        return filas;
    }

    /** El repaso con la hora de exportación del fichero (§3.6): una marca cuya fila ya no está, o anterior a su fila, se borra;
     *  una que sigue pendiente y es anterior al fichero «vuelve»; una posterior al fichero (un fichero de antes de marcar no dice
     *  nada) o ya vuelta se queda como está. */
    static Repaso repasar(List<HechoTaller> marcas, Map<String, LocalDateTime> filas, LocalDateTime exportacion) {
        List<HechoTaller> borrar = new ArrayList<>();
        List<HechoTaller> vuelven = new ArrayList<>();
        for (HechoTaller h : marcas) {
            LocalDateTime desde = filas.get(clave(h.tarjeta(), h.imeiSn()));
            if (desde == null || h.fecha().isBefore(desde)) borrar.add(h);
            else if (h.volvio() == null && h.fecha().isBefore(exportacion)) vuelven.add(h);
        }
        return new Repaso(borrar, vuelven);
    }
}
```

`S/PorPasarNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3): las listas, «Hecho», «Revisado» y el repaso de las marcas tras cada
 *  importación. Las reglas están en TarjetasNsys; aquí se lee cada cosa una vez por petición. */
@Service
public class PorPasarNsys {

    private static final Logger log = LoggerFactory.getLogger(PorPasarNsys.class);
    static final String ACCION_REVISADO = "REVISAR_AVISO_TALLER";
    /** Como mucho los IMEI de un «Marcar todo» (como «Pegar varios IMEIs»). */
    static final int MAX_MARCAR = 500;

    private final TallerNsys taller;
    private final TallerDAO tallerDao;
    private final NsysDAO nsysDao;
    private final HechoDAO hechoDao;
    private final LogDAO logDao;
    private final Clock clock;

    @Autowired
    public PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, LogDAO logDao) {
        this(taller, tallerDao, nsysDao, hechoDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, LogDAO logDao, Clock clock) {
        this.taller = taller;
        this.tallerDao = tallerDao;
        this.nsysDao = nsysDao;
        this.hechoDao = hechoDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    /** Las cuatro tarjetas (§3), con una lectura de cada cosa. */
    public TarjetasPorPasar tarjetas() {
        List<TallerNsys.MovilPorPasar> moviles = taller.movilesPorPasar();
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        return new TarjetasPorPasar(TarjetasNsys.color(moviles, marcas), TarjetasNsys.grado(moviles, marcas),
                TarjetasNsys.altas(altas(), marcas), TarjetasNsys.revisar(avisos, espejo(avisos)));
    }

    /** «No está en NSYS» (§3.4): los IMEI del taller que no están en el espejo, cruzados en Java por claveCruce (las tablas tienen
     *  intercalaciones distintas). Sus trabajos solo se leen si hay alguno. */
    List<AltaTaller> altas() {
        Set<String> espejo = nsysDao.clavesDelEspejo();
        List<String> fuera = nsysDao.imeisDelTaller().stream().filter(i -> !espejo.contains(NsysDAO.claveCruce(i))).toList();
        return fuera.isEmpty() ? List.of() : nsysDao.altas(fuera);
    }

    /** La fila más reciente del espejo de los IMEI de estos avisos (para su modelo); sin avisos no pregunta. */
    private Map<String, MovilNsys> espejo(List<AvisoTaller> avisos) {
        if (avisos.isEmpty()) return Map.of();
        Map<String, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(avisos.stream().map(AvisoTaller::imeiSn).distinct().toList()))
            mapa.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        return mapa;
    }

    /** «Hecho» y «Marcar todo» (§3.6): {@code tarjeta} COLOR, GRADO o ALTA; de 1 a {@value #MAX_MARCAR} IMEI sin vacíos (se
     *  limpian los espacios y los repetidos). No va al registro de actividad. */
    public void marcar(String tarjeta, List<String> imeis, int idUsu) {
        TarjetaHecho t = tarjeta(tarjeta);
        if (imeis == null || imeis.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay móviles que marcar");
        if (imeis.size() > MAX_MARCAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Como mucho " + MAX_MARCAR + " móviles por vez");
        if (imeis.stream().anyMatch(i -> i == null || i.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista lleva IMEIs vacíos");
        hechoDao.marcar(t, imeis.stream().map(String::strip).distinct().toList(), idUsu, ahora());
    }

    /** «deshacer» de una marca. */
    public void desmarcar(String tarjeta, String imeiSn) {
        TarjetaHecho t = tarjeta(tarjeta);
        if (imeiSn == null || imeiSn.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el IMEI");
        hechoDao.desmarcar(t, imeiSn.strip());
    }

    /** «Revisado» (§3.5): quién y cuándo en el aviso y una línea en el registro de actividad. 404 si el aviso no existe; 400 si ya
     *  estaba revisado o no es de «Revisar a mano». */
    public void revisado(int idAviso, int idUsu) {
        AvisoTaller a = tallerDao.avisoPorId(idAviso).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el aviso " + idAviso));
        if (tallerDao.revisar(idAviso, idUsu, ahora()) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese aviso ya está revisado o no es de «Revisar a mano»");
        logDao.insertar(idUsu, ACCION_REVISADO, "IMEI: " + a.imeiSn() + ", " + a.tipo().name() + " de " + a.idRep(), null);
    }

    /** Tras cada importación ACTUAL que acaba en OK, con la hora de exportación del fichero (§3.6): borra las marcas que ya no
     *  hacen falta y hace «volver» las que NSYS no ha seguido. Sin marcas no lee nada más. */
    public void repasarHechos(LocalDateTime exportacion) {
        List<HechoTaller> marcas = hechoDao.todas();
        if (marcas.isEmpty()) return;
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(marcas, TarjetasNsys.filas(taller.movilesPorPasar(), altas()), exportacion);
        if (r.borrar().isEmpty() && r.vuelven().isEmpty()) return;
        hechoDao.repasar(r.borrar(), r.vuelven(), ahora());
        log.info("NSYS: marcas «Hecho» tras la importación: {} quitadas, {} vuelven a salir", r.borrar().size(), r.vuelven().size());
    }

    private static TarjetaHecho tarjeta(String texto) {
        try {
            return TarjetaHecho.valueOf(texto == null ? "" : texto.strip());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarjeta es COLOR, GRADO o ALTA");
        }
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
```

`S/ImportadorNsys.java`:
- campo `private final PorPasarNsys porPasar;` (detrás de `taller`);
- los dos constructores reciben `PorPasarNsys porPasar` detrás de `TallerNsys taller` y lo guardan;
- en `procesar`, detrás de `cerrarTaller();`:

```java
            repasarHechos(f.fecha());
```

- debajo de `cerrarTaller()`:

```java
    /** Tras el cierre, el repaso de las marcas «Hecho» (spec 0.9.22 §3.6) con la hora de exportación del fichero. La importación ya
     *  es OK: si el repaso falla, se avisa en el registro del servidor y se reintenta con el siguiente. */
    private void repasarHechos(LocalDateTime exportacion) {
        try {
            porPasar.repasarHechos(exportacion);
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron repasar las marcas «Hecho» tras la importación", e);
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest,ImportadorNsysTest,TallerNsysTest`
Expected: PASS. Después, la suite entera (`mvn -q -o test`): `OpenApiContractTest` levanta el contexto con el servicio nuevo.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/FilaColorNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaGradoNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaAltaNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaRevisarNsys.java src/main/java/com/reparaciones/servidor/nsys/TarjetasPorPasar.java src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java
git commit -m "feat: las cuatro tarjetas de Por pasar a NSYS, Hecho, Revisado y el repaso tras la importacion (0.9.22, pieza 2)"
```

---

### Task 6: Servidor — las dos plantillas para subir a NSYS

**Files:**
- Create: `src/main/resources/nsys/plantilla-nsys-vacia.xlsx` (copia exacta de la de la spec), `S/PlantillaNsys.java`
- Modify: `S/GrafiasNsys.java` (`CAPACIDAD` con grupos, `modeloDelGoods`, `capacidadDelGoods`), `S/PorPasarNsys.java`
  (`plantillaColor`, `plantillaAltas`, `record Plantilla`)
- Test: `T/PlantillaNsysTest.java` (nuevo), `T/GrafiasNsysTest.java`, `T/PorPasarNsysTest.java`

**Interfaces:**
- Consumes: `TarjetasNsys.color/altas/porClave/marcada`, `PorPasarNsys.altas()` (Task 5); `NsysDAO.costes` (Task 4);
  `MovilNsys.clave()`, `MovilNsys.supplier()`.
- Produces:
  - `GrafiasNsys.modeloDelGoods(String)` («iPhone 14 eSIM») y `GrafiasNsys.capacidadDelGoods(String)` («256 GB»);
  - `PlantillaNsys`: `RECURSO`, `PRIMERA_FILA` (6), `FONDO_ROJO`, `TEXTO_ROJO`, `FONDO_AMARILLO`, `LOTE_COLOR` («COLOR»),
    `LOTE_ALTA` («AJUSTE»); `record FilaColor(...)` con `static FilaColor de(MovilNsys, String producto, boolean compruebalo,
    BigDecimal coste)`; `record FilaAlta(String imeiSn, String modelo)`; `static byte[] color(List<FilaColor>)`,
    `static byte[] altas(List<FilaAlta>)`, `static String nombreModelo(String codigo)`;
  - `PorPasarNsys.plantillaColor()` y `plantillaAltas()` → `PorPasarNsys.Plantilla(String nombre, byte[] contenido, int filas)`.

- [ ] **Step 1: El recurso**

```bash
mkdir -p src/main/resources/nsys
cp /c/Users/dev/Documents/ProgramaReparaciones/docs/superpowers/specs/assets/2026-10-25-v0922-plantilla-nsys-vacia.xlsx src/main/resources/nsys/plantilla-nsys-vacia.xlsx
cmp /c/Users/dev/Documents/ProgramaReparaciones/docs/superpowers/specs/assets/2026-10-25-v0922-plantilla-nsys-vacia.xlsx src/main/resources/nsys/plantilla-nsys-vacia.xlsx && echo iguales
```

Es la plantilla vacía de NSYS («Upload batch with IMEI»): hoja «Лист1», filas 1 a 6 de cabecera (aviso, ejemplo, «(required
field)», nombres de columna con A5:A6, F5:F6, G5:G6, H5:H6, I5:I6 y N5:N6 combinadas), columnas de ayuda ocultas P, AJ y AN, y las
celdas de la fila 7 con su formato (texto «@» salvo D, «0», y N, «General»). No lleva datos reales.

- [ ] **Step 2: Write the failing tests**

`T/GrafiasNsysTest.java`, al final de la clase:

```java
    @Test void elModeloYLaCapacidadDeUnGoodsComoLosLlevaLaPlantilla() {
        assertEquals("iPhone 14 eSIM", GrafiasNsys.modeloDelGoods("Apple iPhone 14 eSIM 256GB Midnight"));
        assertEquals("iPhone 13", GrafiasNsys.modeloDelGoods("Apple iPhone 13 128 GB Blue"));
        assertEquals("iphone 15 pro", GrafiasNsys.modeloDelGoods("apple iphone 15 pro 128GB black titanium"), "como lo tiene el móvil");
        assertNull(GrafiasNsys.modeloDelGoods("Apple iPhone 13 Blue"));
        assertNull(GrafiasNsys.modeloDelGoods(null));
        assertEquals("256 GB", GrafiasNsys.capacidadDelGoods("Apple iPhone 14 eSIM 256GB Midnight"));
        assertEquals("1 TB", GrafiasNsys.capacidadDelGoods("Apple iPhone 17 Pro Max eSIM 1tb Cosmic Orange"));
        assertEquals("128 GB", GrafiasNsys.capacidadDelGoods("Apple iPhone 13 128 GB Blue"));
        assertNull(GrafiasNsys.capacidadDelGoods("Apple iPhone 13 Blue"));
        assertNull(GrafiasNsys.capacidadDelGoods(null));
    }
```

`T/PlantillaNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las plantillas para subir a NSYS (spec 0.9.22 §4) sobre la vacía de NSYS. Datos inventados. */
class PlantillaNsysTest {

    private static final PlantillaNsys.FilaColor ROJO = new PlantillaNsys.FilaColor("350000000000011", "iPhone 13", "128 GB",
            "(PRODUCT)RED", false, "B", new BigDecimal("412.35"), "Proveedor A");

    private static XSSFWorkbook abrir(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private static XSSFWorkbook vacia() throws IOException {
        return new XSSFWorkbook(PlantillaNsys.class.getResourceAsStream(PlantillaNsys.RECURSO));
    }

    /** El texto de cada celda de las filas 1 a 6 (41 columnas, hasta AO), como lo enseña Excel. */
    private static List<String> cabecera(XSSFWorkbook libro) {
        DataFormatter formato = new DataFormatter();
        Sheet hoja = libro.getSheetAt(0);
        List<String> textos = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            Row fila = hoja.getRow(r);
            for (int c = 0; c < 41; c++)
                textos.add(fila == null || fila.getCell(c) == null ? "" : formato.formatCellValue(fila.getCell(c)));
        }
        return textos;
    }

    private static XSSFCell celda(XSSFWorkbook libro, int fila, int columna) {
        return libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA + fila).getCell(columna);
    }

    private static byte[] fondo(XSSFCell c) {
        XSSFColor color = ((XSSFCellStyle) c.getCellStyle()).getFillForegroundXSSFColor();
        return color == null ? null : color.getRGB();
    }

    @Test void conservaLaHojaYLaCabeceraDeLaVaciaYLosDatosEmpiezanEnLaFila7() throws IOException {
        try (XSSFWorkbook hecha = abrir(PlantillaNsys.color(List.of(ROJO))); XSSFWorkbook vacia = vacia()) {
            assertEquals("Лист1", hecha.getSheetName(0));
            assertEquals(cabecera(vacia), cabecera(hecha));
            assertEquals(vacia.getSheetAt(0).getNumMergedRegions(), hecha.getSheetAt(0).getNumMergedRegions());
            assertEquals("350000000000011", celda(hecha, 0, 0).getStringCellValue());
        }
    }

    @Test void laDeColorLlevaLoDeNsysYElColorNuevo() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.color(List.of(ROJO)))) {
            assertEquals("Apple", celda(libro, 0, 1).getStringCellValue());
            assertEquals("iPhone 13", celda(libro, 0, 2).getStringCellValue());
            assertEquals("128 GB", celda(libro, 0, 3).getStringCellValue());
            assertEquals("(PRODUCT)RED", celda(libro, 0, 4).getStringCellValue());
            assertEquals("B", celda(libro, 0, 5).getStringCellValue());
            assertEquals(412.35, celda(libro, 0, 6).getNumericCellValue(), 0.0001);
            assertEquals("Proveedor A", celda(libro, 0, 7).getStringCellValue());
            assertEquals("COLOR", celda(libro, 0, 8).getStringCellValue());
            assertEquals("iPhone", celda(libro, 0, 9).getStringCellValue());
            assertEquals(0, celda(libro, 0, 13).getNumericCellValue(), 0.0001);
            assertFalse(java.util.Arrays.equals(PlantillaNsys.FONDO_AMARILLO, fondo(celda(libro, 0, 4))),
                    "un color con la grafía de NSYS no se marca");
        }
    }

    @Test void unColorDelMaestroVaEnAmarilloYLoQueFaltaEnRojo() throws IOException {
        PlantillaNsys.FilaColor delMaestro = new PlantillaNsys.FilaColor("350000000000012", "iPhone 13", "128 GB", "Pink", true, "B",
                null, null);
        try (XSSFWorkbook libro = abrir(PlantillaNsys.color(List.of(ROJO, delMaestro)))) {
            assertArrayEquals(PlantillaNsys.FONDO_AMARILLO, fondo(celda(libro, 1, 4)));
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 1, 6)), "sin coste: rellenar");
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 1, 7)), "sin proveedor: rellenar");
            assertEquals("", celda(libro, 1, 6).toString());
        }
    }

    @Test void laDeAltasLlevaElImeiYElModeloDelTallerEnRojoYLoDemasParaRellenar() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.altas(List.of(new PlantillaNsys.FilaAlta("350000000000088", "iPhone 14"))))) {
            assertEquals("350000000000088", celda(libro, 0, 0).getStringCellValue());
            assertEquals("Apple", celda(libro, 0, 1).getStringCellValue());
            assertEquals("iPhone 14", celda(libro, 0, 2).getStringCellValue());
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 0, 2)));
            for (int c = 3; c <= 7; c++) assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 0, c)), "columna " + c);
            assertEquals("AJUSTE", celda(libro, 0, 8).getStringCellValue());
            assertEquals("iPhone", celda(libro, 0, 9).getStringCellValue());
            assertEquals(0, celda(libro, 0, 13).getNumericCellValue(), 0.0001);
        }
    }

    @Test void sinFilasEsLaPlantillaVacia() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.altas(List.of())); XSSFWorkbook vacia = vacia()) {
            assertEquals(cabecera(vacia), cabecera(libro));
            Row fila7 = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertTrue(fila7 == null || fila7.getCell(0) == null || fila7.getCell(0).toString().isEmpty());
        }
    }

    @Test void elNombreDelModeloDelTallerComoLaWeb() {
        assertEquals("iPhone 14 Pro Max", PlantillaNsys.nombreModelo("14promax"));
        assertEquals("iPhone 16e", PlantillaNsys.nombreModelo("16e"));
        assertEquals("iPhone XS Max", PlantillaNsys.nombreModelo("xsmax"));
        assertEquals("iPhone 13 Mini", PlantillaNsys.nombreModelo("13mini"));
        assertNull(PlantillaNsys.nombreModelo(null));
        assertNull(PlantillaNsys.nombreModelo(" "));
    }
}
```

`T/PorPasarNsysTest.java`: imports `java.io.ByteArrayInputStream`, `java.math.BigDecimal`, `java.util.Map`,
`org.apache.poi.ss.usermodel.Row`, `org.apache.poi.xssf.usermodel.XSSFWorkbook` y `static ...DatoTaller.COLOR`; al final:

```java
    private static TallerNsys.MovilPorPasar deColor(MovilNsys m, ProductoNsys.Poner poner) {
        return new TallerNsys.MovilPorPasar(m, new LoQueVale.Valor("Rosa", "B", null, "Azul", null, null, true),
                List.of(vigente(1, m.imeiSn(), COLOR, "Rosa", "Azul", AHORA.minusDays(1))), poner);
    }

    @Test void laPlantillaDeColorLlevaLoPendienteConProductoYElCosteDeSuLote() throws Exception {
        MovilNsys conProducto = movil(IMEI, "Azul", "B");
        MovilNsys sinProducto = movil("350000000000002", "Azul", "B");
        MovilNsys marcado = movil("350000000000003", "Azul", "B");
        ProductoNsys.Poner rosa = new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false);
        when(taller.movilesPorPasar()).thenReturn(List.of(deColor(conProducto, rosa), deColor(sinProducto, null),
                deColor(marcado, rosa)));
        when(hechos.todas()).thenReturn(List.of(new HechoTaller("350000000000003", TarjetaHecho.COLOR, 7, "Supertécnico 1",
                AHORA, null)));
        when(nsys.costes(List.of(conProducto.clave()))).thenReturn(Map.of(conProducto.clave(), new BigDecimal("412.35")));
        PorPasarNsys.Plantilla p = servicio.plantillaColor();
        assertEquals("NSYS_color_2026-10-25.xlsx", p.nombre());
        assertEquals(1, p.filas());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(p.contenido()))) {
            Row fila = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertEquals(IMEI, fila.getCell(0).getStringCellValue());
            assertEquals("iPhone 13", fila.getCell(2).getStringCellValue());
            assertEquals("128 GB", fila.getCell(3).getStringCellValue());
            assertEquals("Pink", fila.getCell(4).getStringCellValue());
            assertEquals("B", fila.getCell(5).getStringCellValue(), "el grado de NSYS hoy");
            assertEquals(412.35, fila.getCell(6).getNumericCellValue(), 0.0001);
            assertEquals("Proveedor A", fila.getCell(7).getStringCellValue());
        }
    }

    @Test void laPlantillaDeAltasLlevaLoPendienteConElModeloDelTaller() throws Exception {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14pro",
                "R20261006_57", false, "Técnico 2", null, 1, AHORA.minusDays(19))));
        PorPasarNsys.Plantilla p = servicio.plantillaAltas();
        assertEquals("NSYS_altas_2026-10-25.xlsx", p.nombre());
        assertEquals(1, p.filas());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(p.contenido()))) {
            Row fila = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertEquals("350000000000088", fila.getCell(0).getStringCellValue());
            assertEquals("iPhone 14 Pro", fila.getCell(2).getStringCellValue());
            assertEquals("AJUSTE", fila.getCell(8).getStringCellValue());
        }
    }

    @Test void sinNadaPendienteLaPlantillaVaVaciaYNoPideCostes() {
        PorPasarNsys.Plantilla p = servicio.plantillaColor();
        assertEquals(0, p.filas());
        verify(nsys, never()).costes(any());
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=GrafiasNsysTest,PlantillaNsysTest,PorPasarNsysTest`
Expected: FAIL de compilación (no existen `PlantillaNsys`, `modeloDelGoods`, `plantillaColor`…).

- [ ] **Step 4: Implement**

`S/GrafiasNsys.java`:
- `CAPACIDAD` pasa a tener grupos (sigue encontrando lo mismo): `static final Pattern CAPACIDAD = Pattern.compile("\\b(\\d+)\\s?(GB|TB)\\b", Pattern.CASE_INSENSITIVE);`
- import `java.util.Locale`; constante `private static final Pattern FABRICANTE = Pattern.compile("^(?i)apple\\s+");`
- métodos nuevos, al final:

```java
    /** El modelo de un Goods como lo lleva la plantilla de NSYS (spec 0.9.22 §4, columna C): lo que va antes de la capacidad, sin
     *  el fabricante («Apple iPhone 14 eSIM 256GB Midnight» → «iPhone 14 eSIM»). null sin capacidad. */
    static String modeloDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String modelo = FABRICANTE.matcher(goods.substring(0, c.start()).strip()).replaceFirst("").strip();
        return modelo.isEmpty() ? null : modelo;
    }

    /** La capacidad de un Goods con espacio y la unidad en mayúsculas, como la lleva la plantilla (columna D; con espacio, NSYS la
     *  leyó bien en la subida del 22/10): «256GB» → «256 GB». null sin capacidad. */
    static String capacidadDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        return c.find() ? c.group(1) + " " + c.group(2).toUpperCase(Locale.ROOT) : null;
    }
```

`S/PlantillaNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Las dos plantillas para subir a NSYS (spec 0.9.22 §4): la «Upload batch with IMEI» vacía de NSYS (recurso
 *  nsys/plantilla-nsys-vacia.xlsx), con su hoja y sus filas 1 a 6 tal cual, y un móvil por fila desde la 7, columnas A-N. Lo que
 *  hay que rellenar a mano va en rojo; un color sacado del maestro (NSYS nunca lo ha escrito), en amarillo. */
final class PlantillaNsys {

    static final String RECURSO = "/nsys/plantilla-nsys-vacia.xlsx";
    /** La fila 7 de Excel (0 es la 1): la primera de datos; su formato por columna vale para todas. */
    static final int PRIMERA_FILA = 6;
    static final int COLUMNAS = 14;
    static final String FABRICANTE = "Apple";
    static final String GRUPO = "iPhone";
    static final String LOTE_COLOR = "COLOR";
    static final String LOTE_ALTA = "AJUSTE";
    /** Status 0: «In process» (NSYS lo deja así al subirlo). */
    static final int ESTADO = 0;
    /** Rojo: rellenar en el Excel antes de subir. Amarillo: color sacado del maestro, compruébalo. */
    static final byte[] FONDO_ROJO = {(byte) 0xFD, (byte) 0xE8, (byte) 0xE8};
    static final byte[] TEXTO_ROJO = {(byte) 0xB9, (byte) 0x1C, (byte) 0x1C};
    static final byte[] FONDO_AMARILLO = {(byte) 0xFF, (byte) 0xF7, (byte) 0xCC};

    /** Una fila de la plantilla de color: todo como lo tiene hoy NSYS menos el color. */
    record FilaColor(String imeiSn, String modelo, String capacidad, String color, boolean compruebalo, String grado,
                     BigDecimal coste, String proveedor) {

        /** Del lote actual del móvil: modelo y capacidad de su Goods, el color del producto a poner, su grado de hoy, su coste y
         *  su proveedor tal cual. */
        static FilaColor de(MovilNsys m, String producto, boolean compruebalo, BigDecimal coste) {
            return new FilaColor(m.imeiSn(), GrafiasNsys.modeloDelGoods(m.goods()), GrafiasNsys.capacidadDelGoods(m.goods()),
                    GrafiasNsys.colorDelGoods(producto), compruebalo, m.grade(), coste, m.supplier());
        }
    }

    /** Una fila de la plantilla de altas: el IMEI y el modelo del taller («iPhone 14», a comprobar: el taller no sabe la eSIM). */
    record FilaAlta(String imeiSn, String modelo) {}

    private enum Marca { NADA, ROJO, AMARILLO }

    private PlantillaNsys() {}

    static byte[] color(List<FilaColor> filas) {
        return escribir(filas, (hoja, f) -> {
            hoja.texto(0, f.imeiSn(), Marca.NADA);
            hoja.texto(1, FABRICANTE, Marca.NADA);
            hoja.texto(2, f.modelo(), Marca.NADA);
            hoja.texto(3, f.capacidad(), Marca.NADA);
            hoja.texto(4, f.color(), f.compruebalo() ? Marca.AMARILLO : Marca.NADA);
            hoja.texto(5, f.grado(), Marca.NADA);
            if (f.coste() == null) hoja.vacia(6, Marca.ROJO);
            else hoja.numero(6, f.coste().doubleValue());
            hoja.texto(7, f.proveedor(), Marca.NADA);
            hoja.texto(8, LOTE_COLOR, Marca.NADA);
            hoja.texto(9, GRUPO, Marca.NADA);
            hoja.numero(13, ESTADO);
        });
    }

    static byte[] altas(List<FilaAlta> filas) {
        return escribir(filas, (hoja, f) -> {
            hoja.texto(0, f.imeiSn(), Marca.NADA);
            hoja.texto(1, FABRICANTE, Marca.NADA);
            hoja.texto(2, f.modelo(), Marca.ROJO);
            for (int c = 3; c <= 7; c++) hoja.vacia(c, Marca.ROJO);
            hoja.texto(8, LOTE_ALTA, Marca.NADA);
            hoja.texto(9, GRUPO, Marca.NADA);
            hoja.numero(13, ESTADO);
        });
    }

    /** El nombre del modelo del taller (Telefono.MODELO, un código del ERP como «13pro»), calco de traducirModelo de la web
     *  (src/shared/lib/modelos.ts): «iPhone 13 Pro». null sin código. */
    static String nombreModelo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        String limpio = codigo.strip();
        String especial = ESPECIALES.get(limpio);
        if (especial != null) return especial;
        String numero = limpio.replaceAll("[^0-9]", "");
        String variante = limpio.replaceAll("[0-9]", "");
        return "iPhone " + numero + SUFIJOS.getOrDefault(variante, "");
    }

    private static final Map<String, String> ESPECIALES = Map.of("se2020", "iPhone SE 2020", "x", "iPhone X", "xr", "iPhone XR",
            "xs", "iPhone XS", "xsmax", "iPhone XS Max", "6s", "iPhone 6S", "6splus", "iPhone 6S Plus", "air", "iPhone Air");
    private static final Map<String, String> SUFIJOS = Map.of("plus", " Plus", "mini", " Mini", "pro", " Pro",
            "promax", " Pro Max", "e", "e");

    private static <T> byte[] escribir(List<T> filas, BiConsumer<Hoja, T> rellenar) {
        try (InputStream vacia = PlantillaNsys.class.getResourceAsStream(RECURSO)) {
            if (vacia == null) throw new IllegalStateException("Falta la plantilla vacía de NSYS: " + RECURSO);
            try (XSSFWorkbook libro = new XSSFWorkbook(vacia); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
                Hoja hoja = new Hoja(libro);
                for (int i = 0; i < filas.size(); i++) {
                    hoja.fila(i);
                    rellenar.accept(hoja, filas.get(i));
                }
                libro.write(salida);
                return salida.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir la plantilla de NSYS", e);
        }
    }

    /** La hoja de la plantilla: cada celda con el formato de la fila 7 en su columna (texto, número…) y el color de la marca
     *  encima. Los estilos con marca se crean una vez por columna y marca. */
    private static final class Hoja {

        private final XSSFWorkbook libro;
        private final XSSFSheet hoja;
        private final CellStyle[] base = new CellStyle[COLUMNAS];
        private final Map<String, CellStyle> marcados = new HashMap<>();
        private XSSFRow fila;

        Hoja(XSSFWorkbook libro) {
            this.libro = libro;
            this.hoja = libro.getSheetAt(0);
            XSSFRow modelo = hoja.getRow(PRIMERA_FILA);
            for (int c = 0; c < COLUMNAS; c++) {
                XSSFCell celda = modelo == null ? null : modelo.getCell(c);
                base[c] = celda == null ? null : celda.getCellStyle();
            }
        }

        void fila(int n) {
            XSSFRow existente = hoja.getRow(PRIMERA_FILA + n);
            fila = existente != null ? existente : hoja.createRow(PRIMERA_FILA + n);
        }

        /** Un texto; vacío o null, la celda queda vacía en rojo (hay que rellenarla). */
        void texto(int columna, String valor, Marca marca) {
            if (valor == null || valor.isBlank()) {
                vacia(columna, Marca.ROJO);
                return;
            }
            celda(columna, marca).setCellValue(valor);
        }

        void numero(int columna, double valor) {
            celda(columna, Marca.NADA).setCellValue(valor);
        }

        void vacia(int columna, Marca marca) {
            celda(columna, marca).setBlank();
        }

        private XSSFCell celda(int columna, Marca marca) {
            XSSFCell c = fila.getCell(columna) != null ? fila.getCell(columna) : fila.createCell(columna);
            CellStyle estilo = estilo(columna, marca);
            if (estilo != null) c.setCellStyle(estilo);
            return c;
        }

        private CellStyle estilo(int columna, Marca marca) {
            if (marca == Marca.NADA) return base[columna];
            return marcados.computeIfAbsent(columna + ":" + marca, k -> {
                XSSFCellStyle s = libro.createCellStyle();
                if (base[columna] != null) s.cloneStyleFrom(base[columna]);
                s.setFillForegroundColor(new XSSFColor(marca == Marca.ROJO ? FONDO_ROJO : FONDO_AMARILLO, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                if (marca == Marca.ROJO) {
                    XSSFFont original = s.getFont();
                    XSSFFont rojo = libro.createFont();
                    rojo.setFontName(original.getFontName());
                    rojo.setFontHeight(original.getFontHeight());
                    rojo.setColor(new XSSFColor(TEXTO_ROJO, null));
                    s.setFont(rojo);
                }
                return s;
            });
        }
    }
}
```

`S/PorPasarNsys.java`: imports `java.math.BigDecimal`, `java.time.LocalDate`, `java.util.ArrayList`; y al final de la clase:

```java
    /** Un fichero para descargar: su nombre, lo que lleva y cuántos móviles. */
    public record Plantilla(String nombre, byte[] contenido, int filas) {}

    /** La plantilla de color (spec §4): los móviles de «Cambio de color» con producto y sin marca «Hecho» que valga, en el orden de
     *  la lista, con el coste de su lote actual (una consulta, solo si hay alguno). */
    public Plantilla plantillaColor() {
        List<TallerNsys.MovilPorPasar> moviles = taller.movilesPorPasar();
        List<FilaColorNsys> van = TarjetasNsys.color(moviles, TarjetasNsys.porClave(hechoDao.todas())).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()) && f.ponerProducto() != null).toList();
        Map<String, MovilNsys> porImei = new HashMap<>();
        for (TallerNsys.MovilPorPasar p : moviles) porImei.put(NsysDAO.claveCruce(p.movil().imeiSn()), p.movil());
        List<MovilNsys> lotes = van.stream().map(f -> porImei.get(NsysDAO.claveCruce(f.imeiSn()))).toList();
        Map<ClaveMovil, BigDecimal> costes = lotes.isEmpty() ? Map.of() : nsysDao.costes(lotes.stream().map(MovilNsys::clave).toList());
        List<PlantillaNsys.FilaColor> filas = new ArrayList<>();
        for (int i = 0; i < van.size(); i++) {
            MovilNsys m = lotes.get(i);
            filas.add(PlantillaNsys.FilaColor.de(m, van.get(i).ponerProducto(), van.get(i).compruebalo(), costes.get(m.clave())));
        }
        return new Plantilla("NSYS_color_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.color(filas), filas.size());
    }

    /** La plantilla de altas (spec §4): los IMEI de «No está en NSYS» sin marca «Hecho» que valga, con el modelo del taller. */
    public Plantilla plantillaAltas() {
        List<PlantillaNsys.FilaAlta> filas = TarjetasNsys.altas(altas(), TarjetasNsys.porClave(hechoDao.todas())).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()))
                .map(f -> new PlantillaNsys.FilaAlta(f.imeiSn(), PlantillaNsys.nombreModelo(f.modelo()))).toList();
        return new Plantilla("NSYS_altas_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.altas(filas), filas.size());
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=GrafiasNsysTest,PlantillaNsysTest,PorPasarNsysTest,ProductoNsysTest,ColorChasisTest`
Expected: PASS. Si `conservaLaHojaYLaCabeceraDeLaVacia…` falla por la cabecera, compara las celdas: la plantilla no debe tocar
las filas 1 a 6 (ni las columnas de ayuda P, AJ, AN y AO).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/nsys/plantilla-nsys-vacia.xlsx src/main/java/com/reparaciones/servidor/nsys/PlantillaNsys.java src/main/java/com/reparaciones/servidor/nsys/GrafiasNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/PlantillaNsysTest.java src/test/java/com/reparaciones/servidor/nsys/GrafiasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java
git commit -m "feat: plantillas de color y de altas para subir a NSYS sobre la vacia de NSYS (0.9.22, pieza 2)"
```

---

### Task 7: Servidor — la API de las tarjetas

**Files:**
- Create: `S/PorPasarController.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java`,
  `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `PorPasarNsys` (Tasks 5 y 6).
- Produces (todo bajo la guarda SUPERTECNICO/ADMIN):
  - `GET /api/stock-telefonos/por-pasar/tarjetas` → `TarjetasPorPasar`;
  - `PUT /api/stock-telefonos/por-pasar/hecho`, cuerpo `{tarjeta, imeis}` (esquema `PorPasarPeticionHecho`) → 204;
  - `DELETE /api/stock-telefonos/por-pasar/hecho`, cuerpo `{tarjeta, imeiSn}` (esquema `PorPasarPeticionDeshacer`) → 204;
  - `POST /api/stock-telefonos/por-pasar/avisos/{idAviso}/revisado` → 204 (404 / 400);
  - `GET /api/stock-telefonos/por-pasar/plantilla-color` y `/plantilla-altas` → xlsx con `Content-Disposition: attachment;
    filename="NSYS_color_AAAA-MM-DD.xlsx"` y `X-Filas: <n>`.
  - `target/openapi.json` con todo eso (lo usa la Task 8).

- [ ] **Step 1: Write the failing tests**

`RolesStockTelefonosTest.java`:
- imports `com.reparaciones.servidor.nsys.HechoDAO`, `com.reparaciones.servidor.nsys.TarjetaHecho`,
  `static org.mockito.ArgumentMatchers.eq`, `static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header`,
  `static org.hamcrest.Matchers.matchesPattern`;
- `@MockBean HechoDAO hechoDao;`
- `RUTAS` añade `"/api/stock-telefonos/por-pasar/tarjetas"`, `"/api/stock-telefonos/por-pasar/plantilla-color"` y
  `"/api/stock-telefonos/por-pasar/plantilla-altas"`;
- en `elTecnicoRecibe403SinLlegarAlDao`, `sinTokenRecibe403SinLlegarAlDao` y `conTokenInvalidoRecibe401SinLlegarAlDao`, el
  `verifyNoInteractions(dao, tallerDao)` pasa a `verifyNoInteractions(dao, tallerDao, hechoDao)`;
- pruebas nuevas:

```java
    /** 0.9.22 (spec «plantillas NSYS» §3.5 y §3.6): «Hecho», «deshacer» y «Revisado», con la misma guarda que el resto. */
    @Test void lasEscriturasDeLasTarjetasSoloParaSupertecnicoYAdmin() throws Exception {
        String marcar = "{\"tarjeta\":\"GRADO\",\"imeis\":[\"350000000000001\"]}";
        String deshacer = "{\"tarjeta\":\"GRADO\",\"imeiSn\":\"350000000000001\"}";
        mvc.perform(put("/api/stock-telefonos/por-pasar/hecho").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(marcar)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/hecho").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(deshacer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock-telefonos/por-pasar/avisos/5/revisado").header("Authorization", tecnico()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(hechoDao, tallerDao);

        mvc.perform(put("/api/stock-telefonos/por-pasar/hecho").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON).content(marcar)).andExpect(status().isNoContent());
        verify(hechoDao).marcar(eq(TarjetaHecho.GRADO), eq(List.of("350000000000001")), eq(7), any());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/hecho").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(deshacer)).andExpect(status().isNoContent());
        verify(hechoDao).desmarcar(TarjetaHecho.GRADO, "350000000000001");
        mvc.perform(post("/api/stock-telefonos/por-pasar/avisos/5/revisado").header("Authorization", admin()))
                .andExpect(status().isNotFound());
    }

    @Test void laPlantillaSeDescargaConSuNombreYSuNumeroDeMoviles() throws Exception {
        mvc.perform(get("/api/stock-telefonos/por-pasar/plantilla-altas").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"NSYS_altas_\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
                .andExpect(header().string("X-Filas", "0"));
    }
```

`OpenApiContractTest.java`, en `elContratoPublicaLosEsquemasDeLaWeb`:
- a la lista de rutas añade `"/api/stock-telefonos/por-pasar/tarjetas"`, `"/api/stock-telefonos/por-pasar/hecho"`,
  `"/api/stock-telefonos/por-pasar/avisos/{idAviso}/revisado"`, `"/api/stock-telefonos/por-pasar/plantilla-color"` y
  `"/api/stock-telefonos/por-pasar/plantilla-altas"`;
- a la lista de esquemas, `"TarjetasPorPasar"`, `"FilaColorNsys"`, `"FilaGradoNsys"`, `"FilaAltaNsys"`, `"FilaRevisarNsys"`,
  `"PorPasarPeticionHecho"` y `"PorPasarPeticionDeshacer"`;
- junto a los demás `assertNullable`:

```java
        assertNullable(esquemas, "FilaColorNsys", "colorDe", "colorA", "ponerProducto", "hechoPor", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaGradoNsys", "gradoDe", "gradoA", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaAltaNsys", "modelo", "tecnico", "cliente", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaRevisarNsys", "modelo", "causa", "usuario");
        assertNullable(esquemas, "FilaPorPasar", "ponerProducto");
        assertTrue(esquemas.path("FilaPorPasar").path("properties").has("ponerCompruebalo"));
        assertTrue(esquemas.path("OpcionColor").path("properties").has("compruebalo"));
        assertTrue(esquemas.path("StockTelefonosFichaMovil").path("properties").has("ponerCompruebalo"));
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=RolesStockTelefonosTest,OpenApiContractTest`
Expected: FAIL (404 en las rutas nuevas y faltan rutas y esquemas en el contrato).

- [ ] **Step 3: Implement**

`S/PorPasarController.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3 y §4): las listas, «Hecho», «Revisado» y las plantillas para subir a
 *  NSYS. Junto a StockTelefonosController, con la misma guarda (SUPERTECNICO y ADMIN). La lista de la 0.9.21
 *  (GET /api/stock-telefonos/por-pasar) sigue allí para la web anterior. */
@RestController
@RequestMapping("/api/stock-telefonos/por-pasar")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class PorPasarController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Cuántos móviles lleva una plantilla (para el aviso de la web). */
    static final String CABECERA_FILAS = "X-Filas";

    private final PorPasarNsys porPasar;

    public PorPasarController(PorPasarNsys porPasar) {
        this.porPasar = porPasar;
    }

    @GetMapping("/tarjetas")
    public TarjetasPorPasar tarjetas() {
        return porPasar.tarjetas();
    }

    /** «Hecho» de una fila o «Marcar todo» de una tarjeta (§3.6). */
    @PutMapping("/hecho")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcar(@RequestBody PeticionHecho peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.marcar(peticion.tarjeta(), peticion.imeis(), principal.getIdUsu());
    }

    /** «deshacer» de una marca. */
    @DeleteMapping("/hecho")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desmarcar(@RequestBody PeticionDeshacer peticion) {
        porPasar.desmarcar(peticion.tarjeta(), peticion.imeiSn());
    }

    /** «Revisado» de un aviso de «Revisar a mano» (§3.5). */
    @PostMapping("/avisos/{idAviso}/revisado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revisado(@PathVariable int idAviso, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.revisado(idAviso, principal.getIdUsu());
    }

    @GetMapping(value = "/plantilla-color", produces = XLSX)
    public ResponseEntity<byte[]> plantillaColor() {
        return descarga(porPasar.plantillaColor());
    }

    @GetMapping(value = "/plantilla-altas", produces = XLSX)
    public ResponseEntity<byte[]> plantillaAltas() {
        return descarga(porPasar.plantillaAltas());
    }

    private static ResponseEntity<byte[]> descarga(PorPasarNsys.Plantilla p) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.nombre()).build().toString())
                .header(CABECERA_FILAS, String.valueOf(p.filas()))
                .body(p.contenido());
    }

    /** Esquema PorPasarPeticionHecho: {@code tarjeta} COLOR, GRADO o ALTA. */
    record PeticionHecho(String tarjeta, List<String> imeis) {}

    /** Esquema PorPasarPeticionDeshacer. */
    record PeticionDeshacer(String tarjeta, String imeiSn) {}
}
```

(Los dos records van sin `@Schema`: sus campos son obligatorios.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=RolesStockTelefonosTest,OpenApiContractTest`
Expected: PASS. Después, **la suite entera** (`mvn -o test`): 1434 + las nuevas, sin fallos. Comprueba que
`target/openapi.json` tiene `TarjetasPorPasar` (`grep -c TarjetasPorPasar target/openapi.json` > 0): lo usa la Task 8.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/PorPasarController.java src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: API de las tarjetas de Por pasar a NSYS, Hecho, Revisado y descarga de plantillas (0.9.22, pieza 2)"
```

---

### Task 8: Web — contrato, tipos, llamadas y datos de prueba

**Files:**
- Modify (generados): `W/api/openapi.json`, `W/src/shared/api/schema.d.ts`
- Modify: `W/src/shared/api/client.ts`, `W/src/shared/api/nsys.ts`, `W/src/shared/lib/csv.ts`, `W/src/modules/telefonos/api.ts`,
  `W/src/modules/telefonos/test/fabrica.ts`, `W/src/modules/taller/test/fabrica.ts`, `W/src/test/server.ts`, y los dos tests que
  construyen colores de «Corregir…» a mano (`W/src/modules/taller/imeis/corregir.test.ts`, `CorregirTallerDialog.test.tsx`)
- Create: `W/src/shared/lib/descargas.ts` (+ `descargas.test.ts`)
- Test: `W/src/modules/telefonos/api.test.tsx`

**Interfaces:**
- Consumes: `/c/Users/dev/Documents/wt/servidor-0922-plantillas/target/openapi.json` de la Task 7 (rutas y esquemas nuevos).
- Produces:
  - alias en `client.ts`: `TarjetasPorPasar`, `FilaColorNsys`, `FilaGradoNsys`, `FilaAltaNsys`, `FilaRevisarNsys`, `PeticionHecho`
    (`PorPasarPeticionHecho`), `PeticionDeshacerHecho` (`PorPasarPeticionDeshacer`);
  - `CLAVE_TARJETAS_NSYS` (`['telefonos', 'por-pasar', 'tarjetas']`) en `shared/api/nsys.ts`;
  - `descargarBlob(blob, nombre)` y `nombreDeDescarga(cabecera)` en `shared/lib/descargas.ts`;
  - en `telefonos/api.ts`: `usePorPasarTarjetas()`, `useMarcarHecho()`, `useDeshacerHecho()`, `useRevisarAviso()`,
    `useDescargarPlantilla()` (mutación: `'color' | 'altas'` → `{ nombre, filas }`), tipos `Plantilla` y `Descarga`;
  - fábricas `filaColor`, `filaGrado`, `filaAlta`, `filaRevisar`, `tarjetas` en `telefonos/test/fabrica.ts`.
  - `usePorPasarNsys` y `filaPorPasar` **se quedan** hasta la Task 11 (la pestaña de la 0.9.21 los usa todavía).

- [ ] **Step 0: Worktree de la web**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web fetch -q origin
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web log --oneline -1 origin/main
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add -b dev_v0922_plantillas_nsys /c/Users/dev/Documents/wt/web-0922-plantillas origin/main
cd /c/Users/dev/Documents/wt/web-0922-plantillas && npm ci
```

Apunta el commit de partida (se espera `bfcbba8` o uno posterior).

- [ ] **Step 1: El contrato y los tipos**

```bash
cd /c/Users/dev/Documents/wt/web-0922-plantillas
cp /c/Users/dev/Documents/wt/servidor-0922-plantillas/target/openapi.json api/openapi.json
npm run api:types:offline
grep -c "TarjetasPorPasar\|PorPasarPeticionHecho\|plantilla-color" src/shared/api/schema.d.ts
```

Expected: más de 0. Si `target/openapi.json` no lo tiene, vuelve a pasar en el servidor `mvn -q -o test -Dtest=OpenApiContractTest`.
Con los tipos nuevos, `npx tsc -b` da errores donde se construyen a mano una ficha, una fila de la 0.9.21 o un color de «Corregir…»
(faltan `ponerCompruebalo` y `compruebalo`): se arreglan en el Step 4 (`taller/test/fabrica.ts`, `telefonos/test/fabrica.ts`,
`corregir.test.ts` y `CorregirTallerDialog.test.tsx`).

- [ ] **Step 2: Write the failing tests**

`W/src/shared/lib/descargas.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { descargarBlob, nombreDeDescarga } from './descargas'

describe('descargas (0.9.22: las plantillas de NSYS)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('el nombre sale de Content-Disposition; sin él, null', () => {
    expect(nombreDeDescarga('attachment; filename="NSYS_color_2026-10-25.xlsx"')).toBe('NSYS_color_2026-10-25.xlsx')
    expect(nombreDeDescarga('attachment')).toBeNull()
    expect(nombreDeDescarga(null)).toBeNull()
  })

  it('descarga con un enlace que se pulsa y se quita', () => {
    const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    const revocar = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    descargarBlob(new Blob(['PK']), 'NSYS_altas_2026-10-25.xlsx')
    expect(crear).toHaveBeenCalledTimes(1)
    expect((clic.mock.instances[0] as HTMLAnchorElement).download).toBe('NSYS_altas_2026-10-25.xlsx')
    expect(revocar).toHaveBeenCalledWith('blob:x')
    expect(document.querySelector('a[download]')).toBeNull()
  })
})
```

`W/src/modules/telefonos/api.test.tsx`: imports `vi` de vitest, `useDescargarPlantilla`, `useDeshacerHecho`,
`useMarcarHecho`, `usePorPasarTarjetas`, `useRevisarAviso` de `./api` y `filaGrado`, `tarjetas` de `./test/fabrica`; y al final del
`describe`:

```ts
  it('las tarjetas y lo que mandan «Hecho», «deshacer» y «Revisado» (spec 0.9.22 §3)', async () => {
    const pedidos: unknown[] = []
    server.use(
      http.get('*/api/stock-telefonos/por-pasar/tarjetas', () => HttpResponse.json(tarjetas({ grado: [filaGrado()] }))),
      http.put('*/api/stock-telefonos/por-pasar/hecho', async ({ request }) => {
        pedidos.push(['PUT', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
      http.delete('*/api/stock-telefonos/por-pasar/hecho', async ({ request }) => {
        pedidos.push(['DELETE', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
      http.post('*/api/stock-telefonos/por-pasar/avisos/:idAviso/revisado', ({ params }) => {
        pedidos.push(['POST', params.idAviso])
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const lista = renderHook(() => usePorPasarTarjetas(), { wrapper: envoltorio() })
    await waitFor(() => expect(lista.result.current.data?.grado).toHaveLength(1))
    const wrapper = envoltorio()
    const marcar = renderHook(() => useMarcarHecho(), { wrapper })
    await marcar.result.current.mutateAsync({ tarjeta: 'GRADO', imeis: ['350000000000022'] })
    const deshacer = renderHook(() => useDeshacerHecho(), { wrapper })
    await deshacer.result.current.mutateAsync({ tarjeta: 'GRADO', imeiSn: '350000000000022' })
    const revisar = renderHook(() => useRevisarAviso(), { wrapper })
    await revisar.result.current.mutateAsync(1)
    expect(pedidos).toEqual([
      ['PUT', { tarjeta: 'GRADO', imeis: ['350000000000022'] }],
      ['DELETE', { tarjeta: 'GRADO', imeiSn: '350000000000022' }],
      ['POST', '1'],
    ])
  })

  it('la plantilla se guarda con el nombre del servidor y dice cuántos móviles lleva (spec 0.9.22 §4)', async () => {
    const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    server.use(http.get('*/api/stock-telefonos/por-pasar/plantilla-color', () => new HttpResponse(new Blob(['PK']), {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="NSYS_color_2026-10-25.xlsx"',
        'X-Filas': '2',
      },
    })))
    const descarga = renderHook(() => useDescargarPlantilla(), { wrapper: envoltorio() })
    await expect(descarga.result.current.mutateAsync('color')).resolves.toEqual({ nombre: 'NSYS_color_2026-10-25.xlsx', filas: 2 })
    expect(crear).toHaveBeenCalledTimes(1)
    vi.restoreAllMocks()
  })
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `npx vitest run src/shared/lib/descargas.test.ts src/modules/telefonos/api.test.tsx`
Expected: FAIL (no existen `descargas.ts`, las llamadas ni las fábricas).

- [ ] **Step 4: Implement**

`W/src/shared/api/client.ts`, debajo del bloque de la 0.9.21 (`FilaPorPasar` … `PeticionTaller`):

```ts
/** 0.9.22 (spec «plantillas NSYS» §3): las cuatro tarjetas de «Por pasar a NSYS». Las filas de Color, Grado y No está en NSYS
 *  llevan su marca «Hecho» (`hechoPor`, `hechoEn`; `hechoVolvio` si la importación vio NSYS igual y la fila cuenta otra vez); las
 *  peticiones de «Hecho» y de «deshacer» llevan la `tarjeta` (COLOR, GRADO o ALTA). Horas de Madrid, sin zona. */
export type TarjetasPorPasar = components['schemas']['TarjetasPorPasar']
export type FilaColorNsys = components['schemas']['FilaColorNsys']
export type FilaGradoNsys = components['schemas']['FilaGradoNsys']
export type FilaAltaNsys = components['schemas']['FilaAltaNsys']
export type FilaRevisarNsys = components['schemas']['FilaRevisarNsys']
export type PeticionHecho = components['schemas']['PorPasarPeticionHecho']
export type PeticionDeshacerHecho = components['schemas']['PorPasarPeticionDeshacer']
```

`W/src/shared/api/nsys.ts`, debajo de `CLAVE_POR_PASAR_NSYS`:

```ts
/** 0.9.22: las cuatro tarjetas de «Por pasar a NSYS». Cuelga de CLAVE_POR_PASAR_NSYS, así que corregir en la ficha del IMEI (que
 *  recarga ese prefijo) recarga también las tarjetas. */
export const CLAVE_TARJETAS_NSYS = [...CLAVE_POR_PASAR_NSYS, 'tarjetas'] as const
```

`W/src/shared/lib/descargas.ts`:

```ts
/** Descargas de ficheros que no son CSV (0.9.22: las plantillas de NSYS, que hace el servidor). */

/** Descarga clásica del navegador: un enlace con `download` que se pulsa; el fichero va a la carpeta de descargas. */
export function descargarBlob(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** El nombre de fichero de una cabecera `Content-Disposition: attachment; filename="…"`; null si no lo trae. */
export function nombreDeDescarga(cabecera: string | null | undefined): string | null {
  const m = /filename="([^"]+)"/.exec(cabecera ?? '')
  return m ? m[1] : null
}
```

`W/src/shared/lib/csv.ts`: borra la función privada `descargarEnlace` del final, importa
`import { descargarBlob } from './descargas'` y cambia sus tres llamadas `descargarEnlace(blob, nombre)` por
`descargarBlob(blob, nombre)` (`csv.test.ts` sigue igual).

`W/src/modules/telefonos/api.ts`:
- imports: `useMutation`, `useQueryClient` de TanStack; `type PeticionDeshacerHecho`, `type PeticionHecho`, `type TarjetasPorPasar`
  de `client`; `CLAVE_RASTRO_MOVIL`, `CLAVE_TARJETAS_NSYS` de `@/shared/api/nsys`; `descargarBlob`, `nombreDeDescarga` de
  `@/shared/lib/descargas`.
- al final:

```ts
/** GET /api/stock-telefonos/por-pasar/tarjetas (spec 0.9.22 §3): las cuatro tarjetas, para la pestaña y su número. Sondea como
 *  el stock: las filas se van solas con cada importación. */
export function usePorPasarTarjetas(): UseQueryResult<TarjetasPorPasar | null> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_TARJETAS_NSYS,
    queryFn: async () => (await api.GET('/api/stock-telefonos/por-pasar/tarjetas')).data ?? null,
    refetchInterval: intervalo,
    meta: { sinCapa: true },
  })
}

/** Tras «Hecho», «deshacer» o «Revisado», bien o mal: las tarjetas y, con «Revisado», el rastro de los IMEIs (lo cuenta). */
function useRecargarTarjetas(conRastro: boolean): () => void {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: CLAVE_TARJETAS_NSYS })
    if (conRastro) void qc.invalidateQueries({ queryKey: CLAVE_RASTRO_MOVIL })
  }
}

/** PUT /api/stock-telefonos/por-pasar/hecho (spec 0.9.22 §3.6): «Hecho» de una fila o «Marcar todo» de una tarjeta. */
export function useMarcarHecho() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionHecho) => api.PUT('/api/stock-telefonos/por-pasar/hecho', { body: cuerpo }),
    onSettled: recargar,
  })
}

/** DELETE /api/stock-telefonos/por-pasar/hecho: «deshacer» de una marca. */
export function useDeshacerHecho() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionDeshacerHecho) => api.DELETE('/api/stock-telefonos/por-pasar/hecho', { body: cuerpo }),
    onSettled: recargar,
  })
}

/** POST /api/stock-telefonos/por-pasar/avisos/{idAviso}/revisado (spec 0.9.22 §3.5): el aviso estaba bien y sale de la lista. */
export function useRevisarAviso() {
  const recargar = useRecargarTarjetas(true)
  return useMutation({
    mutationFn: (idAviso: number) =>
      api.POST('/api/stock-telefonos/por-pasar/avisos/{idAviso}/revisado', { params: { path: { idAviso } } }),
    onSettled: recargar,
  })
}

export type Plantilla = 'color' | 'altas'
export type Descarga = { nombre: string; filas: number }

/** GET …/por-pasar/plantilla-color o …/plantilla-altas (spec 0.9.22 §4): el Excel lo hace el servidor; aquí se guarda con el
 *  nombre que manda (Content-Disposition) y se devuelve cuántos móviles lleva (cabecera X-Filas). Un error sale en el diálogo de
 *  siempre. */
export function useDescargarPlantilla() {
  return useMutation({
    mutationFn: async (cual: Plantilla): Promise<Descarga> => {
      const { data, response } = cual === 'color'
        ? await api.GET('/api/stock-telefonos/por-pasar/plantilla-color', { parseAs: 'blob' })
        : await api.GET('/api/stock-telefonos/por-pasar/plantilla-altas', { parseAs: 'blob' })
      const nombre = nombreDeDescarga(response.headers.get('Content-Disposition')) ?? `NSYS_${cual}.xlsx`
      if (data) descargarBlob(data, nombre)
      return { nombre, filas: Number(response.headers.get('X-Filas') ?? '0') }
    },
  })
}
```

`W/src/modules/telefonos/test/fabrica.ts`: imports `FilaAltaNsys`, `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys`,
`TarjetasPorPasar`; y al final:

```ts
/** 0.9.22: una fila de «Cambio de color»: un iPhone 13 128GB Blue que pasa a (PRODUCT)RED por un chasis de la reparación
 *  R20261024_12 (24/10 10:12, Madrid), sin marca «Hecho». */
export function filaColor(p: Partial<FilaColorNsys> = {}): FilaColorNsys {
  return {
    imeiSn: '350000000000011', modelo: '13', modeloMaestro: '13', capacidad: '128GB', colorDe: 'Blue', colorA: '(PRODUCT)RED',
    productoNsys: 'Apple iPhone 13 128GB Blue', gradoNsys: 'B', ponerProducto: 'Apple iPhone 13 128GB (PRODUCT)RED',
    compruebalo: false, tambienGrado: false,
    porQue: [porQue({ dato: 'COLOR', origen: 'REPARACION', idRep: 'R20261024_12', usuario: 'Técnico 2', motivo: 'chasis chai13red nuevo' })],
    desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, ...p,
  }
}

/** Una fila de «Cambio de grado»: un iPhone 14 eSIM 256GB Midnight que pasa de A a A100 por una batería (24/10 12:40). */
export function filaGrado(p: Partial<FilaGradoNsys> = {}): FilaGradoNsys {
  return {
    imeiSn: '350000000000022', modelo: '14', modeloMaestro: '14', capacidad: '256GB', gradoDe: 'A', gradoA: 'A100',
    productoNsys: 'Apple iPhone 14 eSIM 256GB Midnight', conColor: false,
    porQue: [porQue({ origen: 'REPARACION', idRep: 'R20261024_31', usuario: 'Técnico 2', motivo: 'batería bati14 nueva' })],
    desde: '2026-10-24T12:40:00', hechoPor: null, hechoEn: null, hechoVolvio: null, ...p,
  }
}

/** Una fila de «No está en NSYS»: un iPhone 14 del taller con dos trabajos, el último una reparación de «Técnico 2» para AMAZON. */
export function filaAlta(p: Partial<FilaAltaNsys> = {}): FilaAltaNsys {
  return {
    imeiSn: '350000000000088', modelo: '14', ultimoTrabajo: 'R20261006_57', categoria: 'REPARACION', abierto: false,
    tecnico: 'Técnico 2', cliente: 'AMAZON', trabajos: 2, desde: '2026-10-06T09:00:00', hechoPor: null, hechoEn: null,
    hechoVolvio: null, ...p,
  }
}

/** Una fila de «Revisar a mano»: el color del chasis chai14yellowesim no se pudo deducir (R20261022_2, «Técnico 3»). */
export function filaRevisar(p: Partial<FilaRevisarNsys> = {}): FilaRevisarNsys {
  return {
    idAviso: 1, imeiSn: '350000000000101', modelo: '14', modeloMaestro: '14', capacidad: '512GB', tipo: 'SIN_COLOR', causa: null,
    dato: 'COLOR', de: 'chai14yellowesim', a: null, motivo: 'chasis chai14yellowesim nuevo', idRep: 'R20261022_2',
    usuario: 'Técnico 3', fecha: '2026-10-22T11:14:00', ...p,
  }
}

/** Las cuatro tarjetas, vacías salvo lo que se pase. */
export function tarjetas(p: Partial<TarjetasPorPasar> = {}): TarjetasPorPasar {
  return { color: [], grado: [], altas: [], revisar: [], ...p }
}
```

`W/src/modules/taller/test/fabrica.ts`: en `fichaMovil`, `ponerCompruebalo: false` detrás de `ponerGrado: null`; en
`opcionesTaller`, cada color lleva `compruebalo: false`.

Los otros sitios que construyen esos tipos a mano (los marca `npx tsc -b`):
- `W/src/modules/telefonos/test/fabrica.ts`, `filaPorPasar` (vive hasta la Task 11): `ponerCompruebalo: false` antes de `...p`;
- `W/src/modules/taller/imeis/corregir.test.ts` (~línea 42): `{ color: 'Verde', producto: null }` →
  `{ color: 'Verde', producto: null, compruebalo: false }`;
- `W/src/modules/taller/imeis/CorregirTallerDialog.test.tsx` (~línea 72): los dos colores llevan `compruebalo: false`.

`W/src/test/server.ts`: debajo del handler de `*/api/stock-telefonos/por-pasar` (que se queda hasta la Task 11), el de las
tarjetas, vacías (el comentario de cabecera añade «Y desde la 0.9.22, las tarjetas de «Por pasar a NSYS» (vacías).»):

```ts
  http.get('*/api/stock-telefonos/por-pasar/tarjetas', () => HttpResponse.json({ color: [], grado: [], altas: [], revisar: [] })),
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `npx vitest run src/shared/lib src/modules/telefonos/api.test.tsx && npx tsc -b`
Expected: PASS y sin errores de tipos.

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/api/nsys.ts src/shared/lib/descargas.ts src/shared/lib/descargas.test.ts src/shared/lib/csv.ts src/modules/telefonos/api.ts src/modules/telefonos/api.test.tsx src/modules/telefonos/test/fabrica.ts src/modules/taller/test/fabrica.ts src/modules/taller/imeis/corregir.test.ts src/modules/taller/imeis/CorregirTallerDialog.test.tsx src/test/server.ts
git commit -m "feat: contrato y llamadas de las tarjetas de Por pasar a NSYS, Hecho, Revisado y plantillas (0.9.22, pieza 2)"
```

---

### Task 9: Web — textos de los avisos, «compruébalo» y «Revisado» en el rastro

**Files:**
- Modify: `W/src/shared/lib/telefonoNsys.ts`, `W/src/modules/taller/imeis/rastro.ts`, `W/src/modules/taller/imeis/FichaMovil.tsx`,
  `W/src/modules/taller/imeis/corregir.ts`, `W/src/modules/taller/imeis/CorregirTallerDialog.tsx`
- Test: `W/src/shared/lib/telefonoNsys.test.ts`, `W/src/modules/taller/imeis/rastro.test.ts`, `W/src/modules/taller/imeis/corregir.test.ts`

**Interfaces:**
- Consumes: `FichaMovil.ponerCompruebalo`, `OpcionColor.compruebalo` y los eventos `TALLER_REVISADO_*` del rastro (Tasks 2 y 3).
- Produces (en `shared/lib/telefonoNsys.ts`, para el rastro y para «Revisar a mano» de la Task 11):
  - `COMPRUEBALO` = `'compruébalo: NSYS nunca ha escrito ese color'`;
  - `textoPoner(producto, grado, compruebalo = false)`;
  - `type AvisoTallerTexto = { tipo; causa; dato; de; idRep; usuario; motivo }` (`tipo` SIN_COLOR, SIN_GRADO o SIN_DESHACER);
  - `textoAvisoTaller(a: AvisoTallerTexto): string` (el texto del rastro, tal cual);
  - `ETIQUETA_AVISO` (`Record<string, string>`) y `queHacerAviso(tipo, dato)`;
  - en `corregir.ts`, `Previa.compruebalo`.

- [ ] **Step 1: Write the failing tests**

`W/src/shared/lib/telefonoNsys.test.ts`: importa también `COMPRUEBALO`, `ETIQUETA_AVISO`, `queHacerAviso` y `textoAvisoTaller`; y
añade:

```ts
  it('lo que hay que poner en NSYS con «compruébalo» si sale del maestro tal cual (0.9.22, paso 4)', () => {
    expect(textoPoner('Apple iPhone 13 128GB Pink', 'B', true))
      .toBe('Apple iPhone 13 128GB Pink (compruébalo: NSYS nunca ha escrito ese color) · grado B')
    expect(textoPoner('Apple iPhone 13 128GB Pink', 'B', false)).toBe('Apple iPhone 13 128GB Pink · grado B')
    expect(textoPoner(null, 'B', true)).toBe('(ningún móvil de ese color en NSYS: búscalo a mano) · grado B')
    expect(COMPRUEBALO).toBe('compruébalo: NSYS nunca ha escrito ese color')
  })

  it('los avisos del taller como los cuenta el rastro, su etiqueta y qué hacer (0.9.22 §3.5)', () => {
    const base = { causa: null, idRep: 'R20261021_3', usuario: 'Técnico 2', motivo: null }
    expect(textoAvisoTaller({ ...base, tipo: 'SIN_COLOR', dato: 'COLOR', de: 'chai13xxx' }))
      .toBe('Taller: el color del chasis chai13xxx no se puede deducir: lo corregirá un supertécnico (reparación R20261021_3 · Técnico 2)')
    expect(textoAvisoTaller({ ...base, tipo: 'SIN_COLOR', dato: 'COLOR', de: 'tapai13xxx' })).toContain('el color de la tapa tapai13xxx')
    expect(textoAvisoTaller({ ...base, tipo: 'SIN_GRADO', dato: 'GRADO', de: 'ID', motivo: 'batería bati13 nueva' }))
      .toBe('Taller: el grado (ID) no se puede calcular: lo corregirá un supertécnico (reparación R20261021_3 · Técnico 2 · batería bati13 nueva)')
    expect(textoAvisoTaller({ ...base, tipo: 'SIN_DESHACER', causa: 'BORRADA', dato: 'COLOR', de: 'Rojo' }))
      .toBe('Se borró la reparación R20261021_3 que cambió el color; corrígelo a mano si hace falta (Técnico 2)')
    expect(ETIQUETA_AVISO).toEqual({ SIN_COLOR: 'Color sin deducir', SIN_GRADO: 'Grado sin calcular', SIN_DESHACER: 'No se pudo deshacer' })
    expect(queHacerAviso('SIN_COLOR', 'COLOR')).toBe('Mira de qué color quedó y corrígelo en su ficha')
    expect(queHacerAviso('SIN_GRADO', 'GRADO')).toBe('Decide el grado que le toca y corrígelo en su ficha')
    expect(queHacerAviso('SIN_DESHACER', 'COLOR')).toBe('Mira si el móvil tiene de verdad ese color; si no, corrígelo en su ficha')
    expect(queHacerAviso('SIN_DESHACER', 'GRADO')).toBe('Mira si el móvil tiene de verdad ese grado; si no, corrígelo en su ficha')
  })
```

`W/src/modules/taller/imeis/rastro.test.ts`, junto a las de los avisos. El ayudante `rep` de las pruebas de `TALLER_SIN_COLOR`
es local a su `it`, así que esta prueba define el suyo igual:

```ts
  it('un aviso revisado: «Revisado por …» con lo que era (0.9.22 §3.5)', () => {
    const rep = (p: Parameters<typeof eventoRastro>[0]) => eventoRastro({
      fuente: 'TALLER', lote: null, aNsys: null, tecnico: 'Técnico 2', origen: 'REPARACION', idRep: 'R20261021_3', ...p,
    })
    expect(verEvento(rep({ tipo: 'TALLER_REVISADO_SIN_COLOR', dato: 'COLOR', tecnico: 'Supertécnico 1', origen: 'MANO' })))
      .toMatchObject({ titulo: 'Revisado por Supertécnico 1: color sin deducir (reparación R20261021_3)', color: 'bg-verde-ok' })
    expect(verEvento(rep({ tipo: 'TALLER_REVISADO_SIN_GRADO', dato: 'GRADO', tecnico: 'Supertécnico 1' })).titulo)
      .toBe('Revisado por Supertécnico 1: grado sin calcular (reparación R20261021_3)')
    expect(verEvento(rep({ tipo: 'TALLER_REVISADO_SIN_DESHACER', dato: 'COLOR', tecnico: 'Supertécnico 1' })).titulo)
      .toBe('Revisado por Supertécnico 1: no se pudo deshacer (reparación R20261021_3)')
  })
```

(Las pruebas de `TALLER_SIN_COLOR`, `TALLER_SIN_GRADO` y `TALLER_SIN_DESHACER_*` siguen igual: el texto no cambia, solo se mueve a
`shared`.)

`W/src/modules/taller/imeis/corregir.test.ts`, junto a las de `previa`:

```ts
  it('la vista previa dice «compruébalo» si el producto del color sale del maestro tal cual (0.9.22)', () => {
    const opciones = opcionesTaller({ colores: [
      { color: 'Negro', producto: 'Apple iPhone 16 128GB Black', compruebalo: false },
      { color: 'Rosa', producto: 'Apple iPhone 16 128GB Pink', compruebalo: true },
    ] })
    expect(previa(fichaMovil(), { ...eleccionInicial(fichaMovil()), color: 'Rosa', motivo: 'chasis rosa' }, opciones).compruebalo).toBe(true)
    expect(previa(fichaMovil(), { ...eleccionInicial(fichaMovil()), motivo: 'x' }, opciones).compruebalo).toBe(false)
  })
```

(Importa `fichaMovil` y `opcionesTaller` de `../test/fabrica` y `eleccionInicial`, `previa` de `./corregir` si el fichero no los
importa ya.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/corregir.test.ts`
Expected: FAIL (no existen `COMPRUEBALO`, `textoAvisoTaller`… ni el caso `TALLER_REVISADO_*`).

- [ ] **Step 3: Implement**

`W/src/shared/lib/telefonoNsys.ts`, sustituye `textoPoner` y añade al final:

```ts
/** 0.9.22 (spec §2.1, paso 4): el producto sale del nombre del maestro tal cual porque NSYS nunca ha escrito ese color. */
export const COMPRUEBALO = 'compruébalo: NSYS nunca ha escrito ese color'

/** «<producto> · grado <grado>»: lo que hay que poner en NSYS (la ficha, el diálogo «Corregir…» y «Por pasar a NSYS»). Sin
 *  producto, el aviso de buscarlo a mano; desde la 0.9.22, con « (compruébalo: …)» detrás si sale del maestro tal cual. */
export function textoPoner(producto: string | null | undefined, grado: string | null | undefined, compruebalo = false): string {
  const queProducto = producto ? `${producto}${compruebalo ? ` (${COMPRUEBALO})` : ''}` : SIN_PRODUCTO_NSYS
  return `${queProducto} · grado ${grado || '—'}`
}

/** 0.9.22 (spec §3.5): un aviso del taller (pieza 2 de la 0.9.21) como lo cuentan el rastro del IMEI y «Revisar a mano».
 *  `tipo` SIN_COLOR, SIN_GRADO o SIN_DESHACER; `causa` BORRADA o EDITADA en SIN_DESHACER; `usuario`: quién guardó la reparación;
 *  `motivo`: la pieza. */
export type AvisoTallerTexto = {
  tipo: string; causa: string | null; dato: string | null; de: string | null; idRep: string | null; usuario: string | null; motivo: string | null
}

const DATO_AVISO: Record<string, string> = { COLOR: 'color', GRADO: 'grado' }
/** «del chasis chai13xxx» o «de la tapa tapai13xxx», por el prefijo del SKU. */
const delaPieza = (sku: string | null) => ((sku ?? '').toLowerCase().startsWith('tapa') ? `de la tapa ${sku}` : `del chasis ${sku ?? '—'}`)

/** El texto del aviso, el mismo del rastro desde la 0.9.21. */
export function textoAvisoTaller(a: AvisoTallerTexto): string {
  const quien = a.usuario ?? '—'
  switch (a.tipo) {
    case 'SIN_COLOR':
      return `Taller: el color ${delaPieza(a.de)} no se puede deducir: lo corregirá un supertécnico (reparación ${a.idRep ?? '—'} · ${quien})`
    case 'SIN_GRADO':
      return `Taller: el grado (${a.de ?? '—'}) no se puede calcular: lo corregirá un supertécnico (${[`reparación ${a.idRep ?? '—'}`, quien, a.motivo].filter(Boolean).join(' · ')})`
    case 'SIN_DESHACER':
      return `Se ${a.causa === 'BORRADA' ? 'borró' : 'editó'} la reparación ${a.idRep ?? '—'} que cambió el ${DATO_AVISO[a.dato ?? ''] ?? 'dato'}; corrígelo a mano si hace falta (${quien})`
    default:
      return a.tipo
  }
}

/** La etiqueta de cada aviso de «Revisar a mano». */
export const ETIQUETA_AVISO: Record<string, string> = {
  SIN_COLOR: 'Color sin deducir', SIN_GRADO: 'Grado sin calcular', SIN_DESHACER: 'No se pudo deshacer',
}

/** «Qué hacer» con cada aviso de «Revisar a mano» (spec §3.5; el de SIN_DESHACER dice su dato, Ajuste 7 del plan). */
export function queHacerAviso(tipo: string, dato: string | null): string {
  if (tipo === 'SIN_COLOR') return 'Mira de qué color quedó y corrígelo en su ficha'
  if (tipo === 'SIN_GRADO') return 'Decide el grado que le toca y corrígelo en su ficha'
  return `Mira si el móvil tiene de verdad ese ${dato === 'GRADO' ? 'grado' : 'color'}; si no, corrígelo en su ficha`
}
```

`W/src/modules/taller/imeis/rastro.ts`:
- import `ETIQUETA_AVISO`, `textoAvisoTaller` y `type AvisoTallerTexto` de `@/shared/lib/telefonoNsys`;
- borra `deReparacion` y `delaPieza` (se van a `shared`); `causa` se queda (la usan los DESHECHO);
- debajo de `causa`:

```ts
/** Un evento de aviso del rastro («TALLER_SIN_COLOR», «TALLER_SIN_DESHACER_BORRADA»…) con lo que piden sus textos. */
function avisoDelEvento(e: EventoRastro): AvisoTallerTexto {
  const resto = e.tipo.replace(/^TALLER_/, '')
  const conCausa = /_(BORRADA|EDITADA)$/.exec(resto)
  return {
    tipo: conCausa ? resto.slice(0, conCausa.index) : resto, causa: conCausa ? conCausa[1] : null, dato: e.dato, de: e.de,
    idRep: e.idRep, usuario: e.tecnico, motivo: e.motivo,
  }
}
```

- los casos de los avisos sin deducir, sin calcular y sin deshacer pasan a ser uno, y van los de «Revisado»:

```ts
    case 'TALLER_SIN_COLOR':
    case 'TALLER_SIN_GRADO':
    case 'TALLER_SIN_DESHACER_BORRADA':
    case 'TALLER_SIN_DESHACER_EDITADA':
      return { fecha, titulo: textoAvisoTaller(avisoDelEvento(e)), detalle: null, color: 'bg-ambar' }
    case 'TALLER_REVISADO_SIN_COLOR':
    case 'TALLER_REVISADO_SIN_GRADO':
    case 'TALLER_REVISADO_SIN_DESHACER': {
      // 0.9.22 (spec «plantillas NSYS» §3.5): alguien dio el aviso por bueno desde «Revisar a mano».
      const que = (ETIQUETA_AVISO[e.tipo.slice('TALLER_REVISADO_'.length)] ?? 'aviso').toLowerCase()
      return { fecha, titulo: `Revisado por ${e.tecnico ?? '—'}: ${que} (reparación ${e.idRep ?? '—'})`, detalle: null, color: 'bg-verde-ok' }
    }
```

`W/src/modules/taller/imeis/FichaMovil.tsx`: `textoPoner(ficha.ponerProducto, ficha.ponerGrado)` pasa a
`textoPoner(ficha.ponerProducto, ficha.ponerGrado, ficha.ponerCompruebalo)`.

`W/src/modules/taller/imeis/corregir.ts`:
- en el tipo `Previa`, debajo de `producto: string | null`, `compruebalo: boolean` (y el javadoc del tipo añade «`compruebalo`: el
  producto sale del maestro tal cual (0.9.22)»);
- en `previa`:

```ts
  const opcion = opciones?.colores.find((c) => c.color === colorFinal)
  const producto = colorFinal === colorNsys ? (opciones?.productoActual ?? null) : (opcion?.producto ?? null)
  const compruebalo = colorFinal !== colorNsys && (opcion?.compruebalo ?? false)
```

  y `compruebalo` en el objeto que devuelve, detrás de `producto`.

`W/src/modules/taller/imeis/CorregirTallerDialog.tsx`: `textoPoner(p.producto, p.gradoFinal)` pasa a
`textoPoner(p.producto, p.gradoFinal, p.compruebalo)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/shared/lib src/modules/taller/imeis && npx tsc -b && npm run lint`
Expected: PASS, sin errores de tipos ni de lint.

- [ ] **Step 5: Commit**

```bash
git add src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis/rastro.ts src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/FichaMovil.tsx src/modules/taller/imeis/corregir.ts src/modules/taller/imeis/corregir.test.ts src/modules/taller/imeis/CorregirTallerDialog.tsx
git commit -m "feat: compruebalo en la ficha y en Corregir, textos de los avisos en comun y Revisado en el rastro (0.9.22, pieza 2)"
```

---

### Task 10: Web — reglas de las tarjetas

**Files:**
- Modify: `W/src/modules/telefonos/stock/porPasar.ts`
- Test: `W/src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: los tipos de la Task 8; `descripcionMovil`, `diaNsys`, `nombreModeloNsys` de `shared/lib/telefonoNsys`.
- Produces (añadidas a `porPasar.ts`; `textoPorQue`, `modeloDeFila`, `textoDesde`, `textoEnNsys` y `paraExcel` de la 0.9.21 se
  quedan hasta la Task 11):
  - `type Tarjeta = 'color' | 'grado' | 'altas' | 'revisar'`, `TARJETAS`, `TARJETA_HECHO` (`{ color: 'COLOR', grado: 'GRADO',
    altas: 'ALTA' }`), `type TarjetaConHecho`, `type ConHecho`;
  - textos `NOMBRE`, `EXPLICACION`, `AYUDA`, `VACIA` (`Record<Tarjeta, string>`, los de las Global Constraints);
  - `marcada(f)`, `pendientes(filas)`, `totalPendientes(t)`, `cuentas(t, cual)` → `{ total, hechos, pendientes }`,
    `palabraMoviles(n)`, `textoHechos(n)`, `textoNumero(total, hechos)`, `paraPlantillaColor(filas)`, `horaDe(iso)`,
    `textoVolvio(f)`, `listaGrado(filas)`, `avisoPlantillaColor(nombre, n, sinProducto, compruebalo = 0)`, `avisoPlantillaAltas(nombre, n)`,
    `avisoCopiado(n)`, `textoUltimoTrabajo(f)`, `modeloDelTaller(f)`;
  - `modeloDeFila` pasa a aceptar cualquier fila con `modelo`, `modeloMaestro` y `capacidad`.

- [ ] **Step 1: Write the failing tests**

`W/src/modules/telefonos/stock/porPasar.test.ts`: importa también, de `../test/fabrica`, `filaAlta`, `filaColor`, `filaGrado`,
`filaRevisar`, `tarjetas`, y de `./porPasar` lo nuevo; y añade un `describe` al final:

```ts
const HECHO = { hechoPor: 'Supertécnico 1', hechoEn: '2026-10-25T12:31:00', hechoVolvio: null }

describe('«Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3)', () => {
  it('una fila marcada no cuenta; una que volvió, sí', () => {
    expect(marcada(filaColor(HECHO))).toBe(true)
    expect(marcada(filaColor({ ...HECHO, hechoVolvio: '2026-10-25T13:01:00' }))).toBe(false)
    expect(marcada(filaColor())).toBe(false)
    expect(pendientes([filaColor(), filaColor({ imeiSn: '350000000000012', ...HECHO })]).map((f) => f.imeiSn)).toEqual(['350000000000011'])
  })

  it('el número de la pestaña es lo pendiente de las cuatro tarjetas', () => {
    const t = tarjetas({
      color: [filaColor(), filaColor({ imeiSn: '350000000000012', ...HECHO })], grado: [filaGrado()],
      altas: [filaAlta({ ...HECHO, hechoVolvio: '2026-10-25T13:01:00' })], revisar: [filaRevisar(), filaRevisar({ idAviso: 2 })],
    })
    expect(totalPendientes(t)).toBe(5)
    expect(totalPendientes(null)).toBe(0)
    expect(cuentas(t, 'color')).toEqual({ total: 2, hechos: 1, pendientes: 1 })
    expect(cuentas(t, 'revisar')).toEqual({ total: 2, hechos: 0, pendientes: 2 })
  })

  it('«3 móviles · 1 hecho», «1 móvil» y sin hechos sin la segunda parte', () => {
    expect(textoNumero(3, 1)).toBe('3 móviles · 1 hecho')
    expect(textoNumero(5, 2)).toBe('5 móviles · 2 hechos')
    expect(textoNumero(1, 0)).toBe('1 móvil')
    expect(textoNumero(0, 0)).toBe('0 móviles')
  })

  it('la plantilla de color lleva lo pendiente con producto', () => {
    const filas = [filaColor(), filaColor({ imeiSn: '350000000000012', ponerProducto: null }), filaColor({ imeiSn: '350000000000013', ...HECHO })]
    expect(paraPlantillaColor(filas).map((f) => f.imeiSn)).toEqual(['350000000000011'])
  })

  it('la hora de la marca y el aviso de una marca que volvió', () => {
    expect(horaDe('2026-10-25T12:31:00')).toBe('12:31')
    expect(textoVolvio(filaGrado({ ...HECHO, hechoVolvio: '2026-10-25T13:01:00' }))).toBe('Marcado «Hecho» a las 12:31, pero NSYS sigue igual')
    expect(textoVolvio(filaGrado(HECHO))).toBeNull()
  })

  it('«Copiar lista» de Grado: IMEI, producto, grado en NSYS y grado nuevo de lo pendiente', () => {
    expect(listaGrado([filaGrado(), filaGrado({ imeiSn: '350000000000033', ...HECHO })])).toBe(
      'IMEI\tProducto\tGrado en NSYS\tPoner grado\n350000000000022\tApple iPhone 14 eSIM 256GB Midnight\tA\tA100')
  })

  it('los avisos de las descargas y de «Copiar lista»', () => {
    expect(avisoPlantillaColor('NSYS_color_2026-10-25.xlsx', 2, 0)).toBe('Descargado «NSYS_color_2026-10-25.xlsx» · 2 móviles (los marcados «Hecho» no van)')
    expect(avisoPlantillaColor('NSYS_color_2026-10-25.xlsx', 1, 1))
      .toBe('Descargado «NSYS_color_2026-10-25.xlsx» · 1 móvil (los marcados «Hecho» no van). 1 sin producto en NSYS no va: búscalo a mano')
    expect(avisoPlantillaColor('NSYS_color_2026-10-25.xlsx', 2, 0, 1))
      .toBe('Descargado «NSYS_color_2026-10-25.xlsx» · 2 móviles (los marcados «Hecho» no van). 1 con «compruébalo» (en amarillo): míralo antes de subirlo')
    expect(avisoPlantillaAltas('NSYS_altas_2026-10-25.xlsx', 2))
      .toBe('Descargado «NSYS_altas_2026-10-25.xlsx» · 2 móviles. Rellena en el Excel lo que va en rojo antes de subirlo')
    expect(avisoCopiado(3)).toBe('Copiado: 3 móviles con su grado nuevo. Cuando los cambies en NSYS, márcalos «Hecho»')
  })

  it('el último trabajo, el modelo del taller y el de NSYS', () => {
    expect(textoUltimoTrabajo(filaAlta())).toBe('R20261006_57 · Reparación')
    expect(textoUltimoTrabajo(filaAlta({ ultimoTrabajo: 'AP20261020_3', categoria: 'PULIDO', abierto: true }))).toBe('AP20261020_3 · Pulido · asignado')
    expect(modeloDelTaller(filaAlta())).toBe('iPhone 14')
    expect(modeloDelTaller(filaAlta({ modelo: null }))).toBe('—')
    expect(modeloDeFila(filaGrado())).toBe('iPhone 14 256GB')
    expect(modeloDeFila(filaRevisar())).toBe('iPhone 14 512GB')
  })

  it('los textos de cada tarjeta', () => {
    expect(NOMBRE).toEqual({ color: 'Cambio de color', grado: 'Cambio de grado', altas: 'No está en NSYS', revisar: 'Revisar a mano' })
    expect(EXPLICACION.grado).toBe('Cambia el grado: se corrige aparte en NSYS, uno a uno. Si el móvil también cambia de color, primero el color.')
    expect(VACIA.altas).toBe('Todos los móviles del taller están en NSYS.')
    expect(AYUDA.color).toMatch(/^Móviles a los que el taller ha cambiado el color\. En NSYS: bórralos y súbelos otra vez/)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts`
Expected: FAIL (no existen las funciones nuevas).

- [ ] **Step 3: Implement**

`W/src/modules/telefonos/stock/porPasar.ts`:
- imports: añade `FilaAltaNsys`, `FilaColorNsys`, `FilaGradoNsys`, `TarjetasPorPasar` a los tipos de `client`;
- `modeloDeFila` pasa a:

```ts
/** «iPhone 13 128GB» de una fila con el modelo del espejo de NSYS. */
export const modeloDeFila = (f: { modelo: string | null; modeloMaestro: string | null; capacidad: string | null }): string =>
  descripcionMovil(nombreModeloNsys(f.modelo, f.modeloMaestro), f.capacidad)
```

- y al final:

```ts
// ---------------------------------------------------------------- 0.9.22: cuatro tarjetas (spec «plantillas NSYS» §3)

export type Tarjeta = 'color' | 'grado' | 'altas' | 'revisar'
export const TARJETAS: readonly Tarjeta[] = ['color', 'grado', 'altas', 'revisar']
/** Cómo llama el servidor a las tarjetas que llevan «Hecho». */
export const TARJETA_HECHO = { color: 'COLOR', grado: 'GRADO', altas: 'ALTA' } as const
export type TarjetaConHecho = keyof typeof TARJETA_HECHO
/** La marca «Hecho» de una fila de Color, Grado o No está en NSYS. */
export type ConHecho = Pick<FilaColorNsys, 'hechoPor' | 'hechoEn' | 'hechoVolvio'>

export const NOMBRE: Record<Tarjeta, string> = {
  color: 'Cambio de color', grado: 'Cambio de grado', altas: 'No está en NSYS', revisar: 'Revisar a mano',
}

export const EXPLICACION: Record<Tarjeta, string> = {
  color: 'Cambia el producto: en NSYS, bórralo y súbelo otra vez con la plantilla (con el grado que tiene hoy en NSYS).',
  grado: 'Cambia el grado: se corrige aparte en NSYS, uno a uno. Si el móvil también cambia de color, primero el color.',
  altas: 'NSYS no lo conoce: dalo de alta con la plantilla (rellena lo que falta).',
  revisar: 'El ERP no supo calcularlo: mira el móvil y corrígelo en su ficha.',
}

export const AYUDA: Record<Tarjeta, string> = {
  color: 'Móviles a los que el taller ha cambiado el color. En NSYS: bórralos y súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después). Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.',
  grado: 'Todos los móviles a los que el taller ha cambiado el grado (chasis → A, batería nueva → 100…), también los que cambian de color: en esos, haz primero el color (si no, la plantilla de color le devolvería el grado de antes). En NSYS el grado se corrige aparte, sin borrar el móvil. Cada fila se va sola cuando la importación ve el grado nuevo.',
  altas: 'Móviles que han pasado por el taller y NSYS no conoce. La plantilla lleva lo que sabe el ERP; lo demás (capacidad, color, grado, precio, proveedor) va vacío para rellenarlo en el Excel. Cada fila se va sola cuando la importación los ve en NSYS.',
  revisar: 'Al guardar una reparación, el ERP no pudo calcular el color o el grado nuevo, o no pudo deshacer un cambio. Una persona mira el móvil y lo corrige en su ficha («Corregir…»); si NSYS tiene que cambiar, el móvil aparece después en Color o en Grado.',
}

export const VACIA: Record<Tarjeta, string> = {
  color: 'Ningún cambio de color pendiente.', grado: 'Ningún cambio de grado pendiente.',
  altas: 'Todos los móviles del taller están en NSYS.', revisar: 'Nada que revisar: todo lo del taller se pudo calcular.',
}

/** Marcada «Hecho» y sin volver: no cuenta ni va en las plantillas ni en «Copiar lista». Una que volvió cuenta otra vez. */
export const marcada = (f: ConHecho): boolean => f.hechoEn !== null && f.hechoVolvio === null
export const pendientes = <T extends ConHecho>(filas: readonly T[]): T[] => filas.filter((f) => !marcada(f))

/** Cuántas filas tiene una tarjeta, cuántas marcadas y cuántas pendientes («Revisar a mano» no lleva «Hecho»). */
export function cuentas(t: TarjetasPorPasar, cual: Tarjeta): { total: number; hechos: number; pendientes: number } {
  if (cual === 'revisar') return { total: t.revisar.length, hechos: 0, pendientes: t.revisar.length }
  const filas: readonly ConHecho[] = cual === 'color' ? t.color : cual === 'grado' ? t.grado : t.altas
  const hechos = filas.filter(marcada).length
  return { total: filas.length, hechos, pendientes: filas.length - hechos }
}

/** El número de la pestaña (spec §3.1): lo pendiente de las cuatro tarjetas. */
export function totalPendientes(t: TarjetasPorPasar | null | undefined): number {
  return t ? TARJETAS.reduce((n, cual) => n + cuentas(t, cual).pendientes, 0) : 0
}

export const palabraMoviles = (n: number): string => (n === 1 ? 'móvil' : 'móviles')
/** « · 1 hecho» o « · 2 hechos»; sin hechos, nada. */
export const textoHechos = (n: number): string => (n > 0 ? ` · ${n} ${n === 1 ? 'hecho' : 'hechos'}` : '')
/** «3 móviles · 1 hecho». */
export const textoNumero = (total: number, hechos: number): string => `${total} ${palabraMoviles(total)}${textoHechos(hechos)}`

/** Lo que lleva la plantilla de color (spec §4): lo pendiente con producto. */
export const paraPlantillaColor = (filas: readonly FilaColorNsys[]): FilaColorNsys[] =>
  pendientes(filas).filter((f) => f.ponerProducto !== null)

/** «12:31»: la hora de una fecha de Madrid sin zona. */
export const horaDe = (iso: string): string => iso.slice(11, 16)

/** «Marcado «Hecho» a las 12:32, pero NSYS sigue igual»: la importación vio NSYS sin cambiar y la fila cuenta otra vez. */
export function textoVolvio(f: ConHecho): string | null {
  return f.hechoVolvio && f.hechoEn ? `Marcado «Hecho» a las ${horaDe(f.hechoEn)}, pero NSYS sigue igual` : null
}

/** «Copiar lista» de «Cambio de grado» (spec §3.3): IMEI, producto, grado en NSYS y grado nuevo de lo pendiente, separado por
 *  tabuladores para pegarlo en Excel. */
export function listaGrado(filas: readonly FilaGradoNsys[]): string {
  const lineas = pendientes(filas).map((f) => [f.imeiSn, f.productoNsys ?? '—', f.gradoDe ?? '—', f.gradoA ?? '—'])
  return [['IMEI', 'Producto', 'Grado en NSYS', 'Poner grado'], ...lineas].map((c) => c.map(celda).join('\t')).join('\n')
}

/** El aviso de la plantilla de color (spec §4: «el número de móviles y lo que haya que revisar»): los que no van por no tener
 *  producto y los que llevan «compruébalo» (en amarillo en el Excel). */
export function avisoPlantillaColor(nombre: string, n: number, sinProducto: number, compruebalo = 0): string {
  const partes = [`Descargado «${nombre}» · ${n} ${palabraMoviles(n)} (los marcados «Hecho» no van)`]
  if (sinProducto > 0) partes.push(`${sinProducto} sin producto en NSYS no ${sinProducto === 1 ? 'va' : 'van'}: búscalo a mano`)
  if (compruebalo > 0) partes.push(`${compruebalo} con «compruébalo» (en amarillo): míralo antes de subirlo`)
  return partes.join('. ')
}

export const avisoPlantillaAltas = (nombre: string, n: number): string =>
  `Descargado «${nombre}» · ${n} ${palabraMoviles(n)}. Rellena en el Excel lo que va en rojo antes de subirlo`

export const avisoCopiado = (n: number): string =>
  `Copiado: ${n} ${palabraMoviles(n)} con su grado nuevo. Cuando los cambies en NSYS, márcalos «Hecho»`

const CATEGORIA: Record<string, string> = { REPARACION: 'Reparación', GLASS: 'Glass', PULIDO: 'Pulido' }

/** «R20261006_57 · Reparación», con « · asignado» si es una asignación sin terminar. */
export const textoUltimoTrabajo = (f: FilaAltaNsys): string =>
  `${f.ultimoTrabajo} · ${CATEGORIA[f.categoria] ?? f.categoria}${f.abierto ? ' · asignado' : ''}`

/** El modelo del taller de «No está en NSYS» («iPhone 14»): el código del ERP traducido. */
export const modeloDelTaller = (f: FilaAltaNsys): string => (f.modelo ? nombreModeloNsys(f.modelo, null) : '—')
```

(`celda` ya existe en el fichero, de `paraExcel`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts && npx tsc -b`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts
git commit -m "feat: reglas de las cuatro tarjetas de Por pasar a NSYS (0.9.22, pieza 2)"
```

---

### Task 11: Web — la pestaña «Por pasar a NSYS» con cuatro tarjetas

**Files:**
- Create: `W/src/modules/telefonos/stock/TarjetasNsys.tsx`, `W/src/modules/telefonos/stock/CeldaHecho.tsx`,
  `W/src/modules/telefonos/stock/ListasPorPasar.tsx`, `W/src/modules/telefonos/stock/PorPasarNsys.test.tsx`
- Modify (reescrito): `W/src/modules/telefonos/stock/PorPasarNsys.tsx`
- Modify: `W/src/modules/telefonos/stock/StockTelefonosPage.tsx`, `W/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`,
  `W/src/modules/telefonos/stock/porPasar.ts` (+ test), `W/src/modules/telefonos/api.ts` (+ test), `W/src/modules/telefonos/test/fabrica.ts`,
  `W/src/shared/api/client.ts`, `W/src/test/server.ts`

**Interfaces:**
- Consumes: Tasks 8 (llamadas y fábricas), 9 (`COMPRUEBALO`, `SIN_PRODUCTO_NSYS`, `textoAvisoTaller`, `ETIQUETA_AVISO`,
  `queHacerAviso`) y 10 (reglas).
- Produces: `PorPasarNsys({ tarjetas, cargando, error, onAbrirImei })`; la página pide `usePorPasarTarjetas` y su pestaña dice
  `totalPendientes`. Se quitan lo de la 0.9.21 que ya no se usa: `usePorPasarNsys`, `filaPorPasar`, el alias `FilaPorPasar`,
  `paraExcel`, `textoEnNsys` y el handler por defecto de `*/api/stock-telefonos/por-pasar`.

- [ ] **Step 1: Write the failing tests**

`W/src/modules/telefonos/stock/PorPasarNsys.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { TarjetasPorPasar } from '@/shared/api/client'
import { AvisoBreveHost } from '@/shared/ui/AvisoBreve'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { filaAlta, filaColor, filaGrado, filaRevisar, tarjetas } from '../test/fabrica'
import { PorPasarNsys } from './PorPasarNsys'

const HECHO = { hechoPor: 'Supertécnico 1', hechoEn: '2026-10-25T12:31:00', hechoVolvio: null }

function pintar(t: TarjetasPorPasar = tarjetas(), onAbrirImei = vi.fn()) {
  renderConProviders(<><PorPasarNsys tarjetas={t} cargando={false} error={false} onAbrirImei={onAbrirImei} /><AvisoBreveHost /></>,
    { sesion: SESION_SUPER })
  return onAbrirImei
}
const cabeceras = (tabla: HTMLElement) => within(tabla).getAllByRole('columnheader').map((c) => c.textContent)
const filas = (tabla: HTMLElement) => within(tabla).getAllByRole('row').slice(1)

describe('«Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('cada tarjeta dice cuántos hay y cuántos hechos; sin pendientes, su botón no se puede pulsar', () => {
    pintar(tarjetas({ color: [filaColor(), filaColor({ imeiSn: '350000000000012', ...HECHO })], revisar: [filaRevisar()] }))
    const color = screen.getByRole('article', { name: 'Cambio de color' })
    expect(color).toHaveTextContent('2 móviles · 1 hecho')
    expect(color).toHaveTextContent('Cambia el producto: en NSYS, bórralo y súbelo otra vez con la plantilla')
    expect(within(color).getByRole('button', { name: '⬇ Descargar plantilla (1)' })).toBeEnabled()
    const grado = screen.getByRole('article', { name: 'Cambio de grado' })
    expect(grado).toHaveTextContent('0 móviles')
    const copiar = within(grado).getByRole('button', { name: 'Copiar lista' })
    expect(copiar).toBeDisabled()
    expect(copiar).toHaveAttribute('title', 'No hay móviles que corregir')
    expect(within(screen.getByRole('article', { name: 'No está en NSYS' })).getByRole('button', { name: '⬇ Descargar plantilla' })).toBeDisabled()
    expect(screen.getByRole('article', { name: 'Revisar a mano' })).toHaveTextContent('1 móvil')
    expect(screen.getByRole('button', { name: 'Cambio de color' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('Color destaca lo que cambia, dice si también está en Grado y lo que no va en la plantilla', () => {
    pintar(tarjetas({ color: [
      filaColor({ tambienGrado: true }),
      filaColor({ imeiSn: '350000000000012', ponerProducto: null, colorA: 'Verde' }),
      filaColor({ imeiSn: '350000000000013', ponerProducto: 'Apple iPhone 13 128GB Pink', colorA: 'Pink', compruebalo: true }),
    ] }))
    const tabla = screen.getByRole('table', { name: 'Cambio de color' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Poner en NSYS', 'Por qué', 'Desde', '✓ Marcar todo (3)'])
    const [uno, dos, tres] = filas(tabla)
    expect(uno).toHaveTextContent('iPhone 13 128GB')
    expect(uno).toHaveTextContent('Blue → (PRODUCT)RED')
    expect(uno).toHaveTextContent('también en «Cambio de grado» (después)')
    expect(uno).toHaveTextContent('Apple iPhone 13 128GB (PRODUCT)RED')
    expect(uno).toHaveTextContent('grado B (el de NSYS hoy)')
    expect(uno).toHaveTextContent('Color: reparación R20261024_12 · Técnico 2 · chasis chai13red nuevo')
    expect(uno).toHaveTextContent('24/10/2026 10:12')
    expect(dos).toHaveTextContent('(ningún móvil de ese color en NSYS: búscalo a mano)')
    expect(dos).toHaveTextContent('no va en la plantilla')
    expect(tres).toHaveTextContent('compruébalo: NSYS nunca ha escrito ese color')
    expect(screen.getByText('1 sin producto en NSYS: no van en la plantilla')).toBeInTheDocument()
    expect(within(screen.getByRole('article', { name: 'Cambio de color' })).getByRole('button', { name: '⬇ Descargar plantilla (2)' }))
      .toBeInTheDocument()
  })

  it('«Hecho» marca, la marcada sale con la hora y «deshacer», y «Marcar todo» lleva lo pendiente', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(
      http.put('*/api/stock-telefonos/por-pasar/hecho', async ({ request }) => {
        pedidos.push(['PUT', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
      http.delete('*/api/stock-telefonos/por-pasar/hecho', async ({ request }) => {
        pedidos.push(['DELETE', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
    )
    pintar(tarjetas({ color: [filaColor(), filaColor({ imeiSn: '350000000000012', ...HECHO }), filaColor({ imeiSn: '350000000000013' })] }))
    expect(screen.getByText('✓ Hecho 12:31')).toHaveAttribute('title', 'Marcado por Supertécnico 1 a las 12:31')
    await user.click(screen.getByRole('button', { name: 'Hecho 350000000000011' }))
    await waitFor(() => expect(pedidos).toHaveLength(1))
    const deshacer = screen.getByRole('button', { name: 'Deshacer Hecho de 350000000000012' })
    await waitFor(() => expect(deshacer).toBeEnabled())
    await user.click(deshacer)
    await waitFor(() => expect(pedidos).toHaveLength(2))
    const todo = screen.getByRole('button', { name: '✓ Marcar todo (2)' })
    await waitFor(() => expect(todo).toBeEnabled())
    await user.click(todo)
    await waitFor(() => expect(pedidos).toEqual([
      ['PUT', { tarjeta: 'COLOR', imeis: ['350000000000011'] }],
      ['DELETE', { tarjeta: 'COLOR', imeiSn: '350000000000012' }],
      ['PUT', { tarjeta: 'COLOR', imeis: ['350000000000011', '350000000000013'] }],
    ]))
  })

  it('una marca que volvió deja la fila pendiente con el aviso', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ grado: [filaGrado({ ...HECHO, hechoVolvio: '2026-10-25T13:01:00' })] }))
    await user.click(screen.getByRole('button', { name: 'Cambio de grado' }))
    expect(screen.getByText('Marcado «Hecho» a las 12:31, pero NSYS sigue igual')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hecho 350000000000022' })).toBeEnabled()
    expect(screen.getByRole('article', { name: 'Cambio de grado' })).toHaveTextContent('1 móvil')
    expect(screen.getByRole('article', { name: 'Cambio de grado' })).not.toHaveTextContent('hecho')
  })

  it('Grado: lo destacado, «primero su cambio de color» y «Copiar lista» con lo pendiente', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ grado: [filaGrado({ conColor: true }), filaGrado({ imeiSn: '350000000000033', ...HECHO })] }))
    await user.click(screen.getByRole('button', { name: 'Cambio de grado' }))
    const tabla = screen.getByRole('table', { name: 'Cambio de grado' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Grado', 'Producto en NSYS hoy', 'Por qué', 'Desde', '✓ Marcar todo (1)'])
    expect(filas(tabla)[0]).toHaveTextContent('A → A100')
    expect(filas(tabla)[0]).toHaveTextContent('primero su cambio de color')
    expect(screen.getByText(/en NSYS se cambian uno a uno/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Copiar lista (1)' }))
    await waitFor(async () => expect(await navigator.clipboard.readText())
      .toBe('IMEI\tProducto\tGrado en NSYS\tPoner grado\n350000000000022\tApple iPhone 14 eSIM 256GB Midnight\tA\tA100'))
    expect(await screen.findByTestId('aviso-breve'))
      .toHaveTextContent('Copiado: 1 móvil con su grado nuevo. Cuando los cambies en NSYS, márcalos «Hecho»')
  })

  it('«Descargar plantilla» guarda el Excel del servidor y dice cuántos móviles lleva', async () => {
    const user = userEvent.setup()
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    server.use(http.get('*/api/stock-telefonos/por-pasar/plantilla-color', () => new HttpResponse(new Blob(['PK']), {
      headers: { 'Content-Disposition': 'attachment; filename="NSYS_color_2026-10-25.xlsx"', 'X-Filas': '1' },
    })))
    pintar(tarjetas({ color: [filaColor(), filaColor({ imeiSn: '350000000000012', ponerProducto: null })] }))
    await user.click(screen.getByRole('button', { name: '⬇ Descargar plantilla (1)' }))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent(
      'Descargado «NSYS_color_2026-10-25.xlsx» · 1 móvil (los marcados «Hecho» no van). 1 sin producto en NSYS no va: búscalo a mano')
    expect((clic.mock.instances[0] as HTMLAnchorElement).download).toBe('NSYS_color_2026-10-25.xlsx')
  })

  it('No está en NSYS: modelo del taller, último trabajo, técnico, cliente, trabajos y desde', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ altas: [filaAlta(), filaAlta({ imeiSn: '350000000000099', modelo: '13pro', ultimoTrabajo: 'AP20260918_12', categoria: 'PULIDO', abierto: true, cliente: null, trabajos: 1 })] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    const tabla = screen.getByRole('table', { name: 'No está en NSYS' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Último trabajo', 'Técnico', 'Cliente', 'Trabajos', 'Desde', '✓ Marcar todo (2)'])
    const [uno, dos] = filas(tabla)
    expect(uno).toHaveTextContent('iPhone 14el del taller')
    expect(uno).toHaveTextContent('R20261006_57 · Reparación')
    expect(uno).toHaveTextContent('AMAZON')
    expect(uno).toHaveTextContent('06/10/2026')
    expect(dos).toHaveTextContent('iPhone 13 Pro')
    expect(dos).toHaveTextContent('AP20260918_12 · Pulido · asignado')
  })

  it('Revisar a mano: qué pasa, qué hacer, «✓ Revisado» y «Corregir…» abre el móvil', async () => {
    const user = userEvent.setup()
    const revisados: string[] = []
    server.use(http.post('*/api/stock-telefonos/por-pasar/avisos/:idAviso/revisado', ({ params }) => {
      revisados.push(String(params.idAviso))
      return new HttpResponse(null, { status: 204 })
    }))
    const abrir = pintar(tarjetas({ revisar: [filaRevisar(), filaRevisar({ idAviso: 2, imeiSn: '350000000000103', tipo: 'SIN_DESHACER', causa: 'BORRADA', idRep: 'R20261023_40', usuario: 'Supertécnico 1' })] }))
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    const tabla = screen.getByRole('table', { name: 'Revisar a mano' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Qué pasa', 'Qué hacer', 'Reparación', ''])
    const [uno, dos] = filas(tabla)
    expect(uno).toHaveTextContent('Color sin deducir')
    expect(uno).toHaveTextContent('el color del chasis chai14yellowesim no se puede deducir')
    expect(uno).toHaveTextContent('Mira de qué color quedó y corrígelo en su ficha')
    expect(uno).toHaveTextContent('R20261022_2Técnico 3 · 22/10/2026 11:14')
    expect(dos).toHaveTextContent('No se pudo deshacer')
    expect(dos).toHaveTextContent('Se borró la reparación R20261023_40 que cambió el color')
    await user.click(within(uno).getByRole('button', { name: 'Revisado R20261022_2 de 350000000000101' }))
    await waitFor(() => expect(revisados).toEqual(['1']))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Revisado: sale de la lista (queda en el rastro)')
    await user.click(within(dos).getByRole('button', { name: 'Corregir 350000000000103' }))
    expect(abrir).toHaveBeenCalledWith('350000000000103')
  })

  it('sin nada lo dice en cada lista, y si no carga lo dice', async () => {
    const user = userEvent.setup()
    pintar()
    expect(screen.getByText('Ningún cambio de color pendiente.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    expect(screen.getByText('Todos los móviles del taller están en NSYS.')).toBeInTheDocument()
  })
})
```

`W/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`: en los imports, `filaPorPasar` pasa a `filaColor, filaGrado, tarjetas`
(y `porQue` se va si ya no se usa); el `describe` de «Por pasar a NSYS (spec 0.9.21 §2.4)» se sustituye por:

```tsx
describe('Stock de teléfonos: «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3)', () => {
  it('la pestaña cuenta lo pendiente de las cuatro tarjetas y abre el detalle desde una fila', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/por-pasar/tarjetas', () => HttpResponse.json(tarjetas({
      color: [filaColor(), filaColor({ imeiSn: '350000000000012', hechoPor: 'Supertécnico 1', hechoEn: '2026-10-25T12:31:00' })],
      grado: [filaGrado()],
    }))))
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Por pasar a NSYS (2)' }))
    expect(screen.getByRole('button', { name: 'Por pasar a NSYS (2)' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('article', { name: 'Cambio de color' })).toHaveTextContent('2 móviles · 1 hecho')
    await user.click(screen.getByRole('button', { name: 'Abrir 350000000000011' }))
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/stock')).toBeInTheDocument()
  })

  it('si la lista no carga lo dice y no afirma que no haya nada', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/por-pasar/tarjetas', () => new HttpResponse(null, { status: 500 })))
    abrir()
    const error = await screen.findByRole('dialog', { name: 'Error' })
    await user.click(within(error).getByRole('button', { name: /Aceptar|Cerrar|OK/i }))
    await user.click(await screen.findByRole('button', { name: /^Por pasar a NSYS/ }))
    expect(await screen.findByText('No se pudo cargar la lista.')).toBeInTheDocument()
    expect(screen.queryByText('Ningún cambio de color pendiente.')).not.toBeInTheDocument()
  })

  it('«Móviles» vuelve a la vista de siempre', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Por pasar a NSYS (0)' }))
    expect(screen.queryByRole('region', { name: 'Tabla de móviles' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Móviles' }))
    expect(await screen.findByRole('region', { name: 'Tabla de móviles' })).toBeInTheDocument()
  })
})
```

`W/src/modules/telefonos/stock/porPasar.test.ts`: borra los casos de `textoEnNsys` y `paraExcel`; el `describe` de la 0.9.21 se
queda con `textoPorQue` y con un caso «el modelo y desde cuándo» sobre `filaColor()`: `modeloDeFila(f)` → «iPhone 13 128GB» y
`textoDesde(f.desde)` → «24/10/2026 10:12» (la fábrica nueva).
`W/src/modules/telefonos/api.test.tsx`: la prueba de `usePorPasarNsys`, si la hay, se borra (con sus imports; ver el Step 3).

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/modules/telefonos`
Expected: FAIL (la pestaña aún es la de la 0.9.21).

- [ ] **Step 3: Implement**

`W/src/modules/telefonos/stock/TarjetasNsys.tsx`:

```tsx
import type { ReactNode } from 'react'
import type { TarjetasPorPasar } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { cuentas, EXPLICACION, NOMBRE, palabraMoviles, TARJETAS, textoHechos, type Tarjeta } from './porPasar'

/** El borde de arriba de cada tarjeta (prototipo «plantillas NSYS», diseño C): morado, azul, rojo y ámbar. */
const BORDE: Record<Tarjeta, string> = {
  color: 'border-t-taller', grado: 'border-t-tipo-reparacion-text', altas: 'border-t-fila-urgente-brd', revisar: 'border-t-ambar',
}

type Props = { tarjetas: TarjetasPorPasar; elegida: Tarjeta; onElegir: (t: Tarjeta) => void; boton: (t: Tarjeta) => ReactNode }

/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3.1): su nombre (que la elige), su número («3 móviles · 1 hecho»), qué se
 *  hace y su botón. Pulsar en la tarjeta también la elige. */
export function TarjetasNsys({ tarjetas, elegida, onElegir, boton }: Props) {
  return (
    <div className="grid shrink-0 grid-cols-4 gap-2.5">
      {TARJETAS.map((t) => {
        const c = cuentas(tarjetas, t)
        return (
          <article key={t} aria-label={NOMBRE[t]} onClick={() => onElegir(t)}
            className={cn('flex cursor-pointer flex-col gap-1.5 rounded-lg border-[1.5px] border-t-4 bg-superficie px-3 py-2.5', BORDE[t],
              elegida === t ? 'border-x-azul-noche border-b-azul-noche shadow-[0_0_0_2px_rgba(7,26,58,0.12)]' : 'border-x-fila-sep border-b-fila-sep hover:border-x-borde-input hover:border-b-borde-input')}>
            <button type="button" aria-pressed={elegida === t} onClick={() => onElegir(t)}
              className="cursor-pointer self-start text-left text-[13px] font-bold text-azul-medio">{NOMBRE[t]}</button>
            <p className="text-[26px] leading-none font-extrabold text-azul-noche">
              {c.total}{' '}<small className="text-[12px] font-semibold text-azul-gris">{`${palabraMoviles(c.total)}${textoHechos(c.hechos)}`}</small>
            </p>
            <p className="min-h-[46px] text-[11.5px] leading-snug text-azul-gris">{EXPLICACION[t]}</p>
            <div className="flex flex-wrap items-center gap-1.5">{boton(t)}</div>
          </article>
        )
      })}
    </div>
  )
}
```

`W/src/modules/telefonos/stock/CeldaHecho.tsx`:

```tsx
import { horaDe, marcada, textoVolvio, type ConHecho } from './porPasar'

type Props = { fila: ConHecho; imeiSn: string; ocupado: boolean; onMarcar: () => void; onDeshacer: () => void }

const BOTON = 'h-[26px] cursor-pointer rounded-lg border border-borde-input bg-superficie px-2.5 text-[12px] font-semibold text-azul-medio hover:bg-seleccion-suave disabled:cursor-not-allowed disabled:opacity-40'

/** «Hecho» de una fila (spec 0.9.22 §3.6): el botón; marcada, «✓ Hecho 12:31» (quién, al pasar el ratón) y «deshacer»; si la
 *  importación vio NSYS igual, el botón otra vez con el aviso en rojo. */
export function CeldaHecho({ fila, imeiSn, ocupado, onMarcar, onDeshacer }: Props) {
  if (marcada(fila) && fila.hechoEn) {
    const hora = horaDe(fila.hechoEn)
    return (
      <>
        <span title={`Marcado por ${fila.hechoPor ?? '—'} a las ${hora}`}
          className="inline-block rounded-full border border-fila-recibido-brd bg-fila-recibido-bg px-2 text-[11px] font-bold whitespace-nowrap text-fila-recibido-brd">
          {`✓ Hecho ${hora}`}
        </span>
        <button type="button" disabled={ocupado} aria-label={`Deshacer Hecho de ${imeiSn}`} onClick={onDeshacer}
          className="block cursor-pointer text-[11px] text-azul-noche hover:underline disabled:opacity-40">deshacer</button>
      </>
    )
  }
  const volvio = textoVolvio(fila)
  return (
    <>
      <button type="button" disabled={ocupado} aria-label={`Hecho ${imeiSn}`} onClick={onMarcar} className={BOTON}>Hecho</button>
      {volvio && <span className="mt-0.5 block max-w-[220px] text-[11px] font-bold whitespace-normal text-fila-urgente-brd">{volvio}</span>}
    </>
  )
}
```

`W/src/modules/telefonos/stock/ListasPorPasar.tsx`:

```tsx
import type { ReactNode } from 'react'
import type { FilaAltaNsys, FilaColorNsys, FilaGradoNsys, FilaRevisarNsys } from '@/shared/api/client'
import { COMPRUEBALO, diaNsys, ETIQUETA_AVISO, queHacerAviso, SIN_PRODUCTO_NSYS, textoAvisoTaller } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { CeldaHecho } from './CeldaHecho'
import { marcada, modeloDeFila, modeloDelTaller, pendientes, textoDesde, textoPorQue, textoUltimoTrabajo, type ConHecho } from './porPasar'

/** Las cuatro listas de «Por pasar a NSYS» (spec 0.9.22 §3.2-§3.5). El IMEI abre el detalle del móvil, como en la 0.9.21. */

const CELDA = 'px-2 py-1.5 align-top'
const GRIS = 'block text-[11px] text-azul-gris'
const CAMBIA_COLOR = 'inline-block rounded border border-taller-bg bg-taller-bg px-1.5 font-bold text-taller-text'
const CAMBIA_GRADO = 'inline-block rounded border border-tipo-reparacion-bg bg-tipo-reparacion-bg px-1.5 font-bold text-tipo-reparacion-text'
const FUERA = 'mt-0.5 inline-block rounded bg-fila-solicitud-bg px-1.5 text-[10.5px] font-bold text-fila-solicitud-brd'
const PEQUENO = 'h-[26px] cursor-pointer rounded-lg border border-borde-input bg-superficie px-2.5 text-[12px] font-semibold whitespace-nowrap text-azul-medio hover:bg-seleccion-suave disabled:cursor-not-allowed disabled:opacity-40'
const ETIQUETA: Record<string, string> = {
  SIN_COLOR: 'bg-fila-solicitud-bg text-fila-solicitud-brd', SIN_GRADO: 'bg-tipo-reparacion-bg text-tipo-reparacion-text',
  SIN_DESHACER: 'bg-urgente-bg text-fila-urgente-brd',
}

type ConHechoProps<T> = {
  filas: T[]; vacio: string; ocupado: boolean; onAbrirImei: (imeiSn: string) => void
  onMarcar: (imeis: string[]) => void; onDeshacer: (imeiSn: string) => void
}

function Imei({ imeiSn, onAbrir }: { imeiSn: string; onAbrir: (imeiSn: string) => void }) {
  return (
    <button type="button" aria-label={`Abrir ${imeiSn}`} onClick={() => onAbrir(imeiSn)}
      className="cursor-pointer font-mono text-[11.5px] text-azul-noche hover:underline">{imeiSn}</button>
  )
}

/** Una lista con «Hecho»: la cabecera con «✓ Marcar todo (n)» (lo pendiente) y las filas marcadas en gris. */
function TablaConHecho<T extends ConHecho & { imeiSn: string }>({ nombre, columnas, celdas, filas, vacio, ocupado, onMarcar, onDeshacer }:
  Omit<ConHechoProps<T>, 'onAbrirImei'> & { nombre: string; columnas: string[]; celdas: (f: T) => ReactNode }) {
  const n = pendientes(filas).length
  return (
    <div className="min-h-0 flex-1 overflow-auto rounded-lg border border-fila-sep">
      <table aria-label={nombre} className="w-full border-collapse text-[12.5px] text-azul-medio">
        <thead className="sticky top-0 bg-crema">
          <tr>
            {columnas.map((c) => <th key={c} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}
            <th scope="col" className="px-2 py-1.5 text-left">
              <button type="button" disabled={n === 0 || ocupado} title="Marca como hechas todas las filas pendientes"
                onClick={() => onMarcar(pendientes(filas).map((f) => f.imeiSn))} className={PEQUENO}>{`✓ Marcar todo (${n})`}</button>
            </th>
          </tr>
        </thead>
        <tbody>
          {filas.length === 0 && (
            <tr><td colSpan={columnas.length + 1} className="px-2 py-3 text-[12px] text-azul-gris">{vacio}</td></tr>
          )}
          {filas.map((f) => (
            <tr key={f.imeiSn} className={cn('border-t border-fila-sep', marcada(f) && 'bg-badge-neutro-bg text-texto-suave')}>
              {celdas(f)}
              <td className={cn(CELDA, 'whitespace-nowrap')}>
                <CeldaHecho fila={f} imeiSn={f.imeiSn} ocupado={ocupado} onMarcar={() => onMarcar([f.imeiSn])} onDeshacer={() => onDeshacer(f.imeiSn)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** El producto con el color nuevo resaltado (lo que cambia). */
function Resaltado({ texto, parte }: { texto: string; parte: string | null }) {
  const i = parte ? texto.lastIndexOf(parte) : -1
  if (!parte || i < 0) return <>{texto}</>
  return <>{texto.slice(0, i)}<span className={CAMBIA_COLOR}>{parte}</span>{texto.slice(i + parte.length)}</>
}

/** «Cambio de color» (§3.2). */
export function ListaColor({ onAbrirImei, ...props }: ConHechoProps<FilaColorNsys>) {
  return (
    <TablaConHecho {...props} nombre="Cambio de color"
      columnas={['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Poner en NSYS', 'Por qué', 'Desde']}
      celdas={(f) => (
        <>
          <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>
            <span className={CAMBIA_COLOR}>{`${f.colorDe ?? '—'} → ${f.colorA ?? '—'}`}</span>
            {f.tambienGrado && <span className={GRIS}>también en «Cambio de grado» (después)</span>}
          </td>
          <td className={CELDA}>{f.productoNsys ?? '—'}</td>
          <td className={CELDA}>
            {f.ponerProducto
              ? <b className="text-azul-noche"><Resaltado texto={f.ponerProducto} parte={f.colorA} />{f.compruebalo && <span className="font-semibold text-fila-solicitud-brd">{` (${COMPRUEBALO})`}</span>}</b>
              : <><span className="font-semibold text-fila-solicitud-brd">{SIN_PRODUCTO_NSYS}</span><br /><span className={FUERA}>no va en la plantilla</span></>}
            <span className={GRIS}>{`grado ${f.gradoNsys || '—'} `}<span className="text-texto-suave">(el de NSYS hoy)</span></span>
          </td>
          <td className={cn(CELDA, 'min-w-[220px]')}>{f.porQue.map((p, i) => <span key={i} className="block">{textoPorQue(p)}</span>)}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{textoDesde(f.desde)}</td>
        </>
      )} />
  )
}

/** «Cambio de grado» (§3.3). */
export function ListaGrado({ onAbrirImei, ...props }: ConHechoProps<FilaGradoNsys>) {
  return (
    <TablaConHecho {...props} nombre="Cambio de grado"
      columnas={['IMEI', 'Modelo', 'Grado', 'Producto en NSYS hoy', 'Por qué', 'Desde']}
      celdas={(f) => (
        <>
          <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>
            <span className={CAMBIA_GRADO}>{`${f.gradoDe ?? '—'} → ${f.gradoA ?? '—'}`}</span>
            {f.conColor && <span className={GRIS}>primero su cambio de color</span>}
          </td>
          <td className={CELDA}>{f.productoNsys ?? '—'}</td>
          <td className={cn(CELDA, 'min-w-[220px]')}>{f.porQue.map((p, i) => <span key={i} className="block">{textoPorQue(p)}</span>)}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{textoDesde(f.desde)}</td>
        </>
      )} />
  )
}

/** «No está en NSYS» (§3.4). */
export function ListaAltas({ onAbrirImei, ...props }: ConHechoProps<FilaAltaNsys>) {
  return (
    <TablaConHecho {...props} nombre="No está en NSYS"
      columnas={['IMEI', 'Modelo', 'Último trabajo', 'Técnico', 'Cliente', 'Trabajos', 'Desde']}
      celdas={(f) => (
        <>
          <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDelTaller(f)}<span className={GRIS}>el del taller</span></td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{textoUltimoTrabajo(f)}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{f.tecnico ?? '—'}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{f.cliente ?? '—'}</td>
          <td className={cn(CELDA, 'text-right whitespace-nowrap')}>{f.trabajos}</td>
          <td className={cn(CELDA, 'whitespace-nowrap')}>{diaNsys(f.desde)}</td>
        </>
      )} />
  )
}

type RevisarProps = { filas: FilaRevisarNsys[]; vacio: string; ocupado: boolean; onAbrirImei: (imeiSn: string) => void; onRevisado: (idAviso: number) => void }

/** «Revisar a mano» (§3.5): sin «Hecho»; «✓ Revisado» si ya estaba bien y «Corregir…», que abre el móvil. */
export function ListaRevisar({ filas, vacio, ocupado, onAbrirImei, onRevisado }: RevisarProps) {
  const columnas = ['IMEI', 'Modelo', 'Qué pasa', 'Qué hacer', 'Reparación', '']
  return (
    <div className="min-h-0 flex-1 overflow-auto rounded-lg border border-fila-sep">
      <table aria-label="Revisar a mano" className="w-full border-collapse text-[12.5px] text-azul-medio">
        <thead className="sticky top-0 bg-crema">
          <tr>{columnas.map((c, i) => <th key={i} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
        </thead>
        <tbody>
          {filas.length === 0 && (
            <tr><td colSpan={columnas.length} className="px-2 py-3 text-[12px] text-azul-gris">{vacio}</td></tr>
          )}
          {filas.map((f) => (
            <tr key={f.idAviso} className="border-t border-fila-sep">
              <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
              <td className={cn(CELDA, 'min-w-[300px]')}>
                <span className={cn('mb-0.5 inline-block rounded px-1.5 text-[10.5px] font-bold', ETIQUETA[f.tipo])}>{ETIQUETA_AVISO[f.tipo] ?? f.tipo}</span>
                <span className="block">{textoAvisoTaller({ tipo: f.tipo, causa: f.causa, dato: f.dato, de: f.de, idRep: f.idRep, usuario: f.usuario, motivo: f.motivo })}</span>
              </td>
              <td className={cn(CELDA, 'min-w-[220px] text-[12px]')}>{queHacerAviso(f.tipo, f.dato)}</td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>{f.idRep}<span className={GRIS}>{`${f.usuario ?? '—'} · ${textoDesde(f.fecha)}`}</span></td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>
                <span className="flex gap-1.5">
                  <button type="button" disabled={ocupado} aria-label={`Revisado ${f.idRep} de ${f.imeiSn}`} onClick={() => onRevisado(f.idAviso)} className={PEQUENO}>✓ Revisado</button>
                  <button type="button" aria-label={`Corregir ${f.imeiSn}`} onClick={() => onAbrirImei(f.imeiSn)} className={PEQUENO}>Corregir…</button>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

`W/src/modules/telefonos/stock/PorPasarNsys.tsx` entero:

```tsx
import { useState } from 'react'
import type { TarjetasPorPasar } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { avisar } from '@/shared/ui/avisos'
import { copiarAlPortapapeles } from '@/shared/ui/copiar'
import { useDescargarPlantilla, useDeshacerHecho, useMarcarHecho, useRevisarAviso } from '../api'
import { ListaAltas, ListaColor, ListaGrado, ListaRevisar } from './ListasPorPasar'
import {
  avisoCopiado, avisoPlantillaAltas, avisoPlantillaColor, AYUDA, listaGrado, paraPlantillaColor, pendientes, TARJETA_HECHO, VACIA,
  type Tarjeta, type TarjetaConHecho,
} from './porPasar'
import { TarjetasNsys } from './TarjetasNsys'

type Props = { tarjetas: TarjetasPorPasar | null; cargando: boolean; error: boolean; onAbrirImei: (imeiSn: string) => void }

const VACIAS: TarjetasPorPasar = { color: [], grado: [], altas: [], revisar: [] }
const BOTON = 'h-[26px] cursor-pointer rounded-lg px-2.5 text-[12px] font-semibold whitespace-nowrap disabled:cursor-not-allowed disabled:opacity-40'
const PRIMARIO = cn(BOTON, 'bg-azul-noche text-superficie hover:bg-azul-noche-hover')
const SECUNDARIO = cn(BOTON, 'border border-borde-input bg-superficie text-azul-medio hover:bg-seleccion-suave')
const PASTILLA = 'rounded px-1.5 text-[10.5px] font-bold bg-fila-solicitud-bg text-fila-solicitud-brd'
const SIN_NADA = 'No hay móviles que corregir'

/** La pestaña «Por pasar a NSYS» del stock de teléfonos desde la 0.9.22 (spec «plantillas NSYS» §3, diseño C del prototipo): arriba
 *  las cuatro tarjetas con su número y su botón; debajo, la ayuda y la lista de la elegida (la de color al entrar). Las plantillas
 *  las hace el servidor; «Copiar lista» copia para Excel. */
export function PorPasarNsys({ tarjetas, cargando, error, onAbrirImei }: Props) {
  const [elegida, setElegida] = useState<Tarjeta>('color')
  const t = tarjetas ?? VACIAS
  const marcar = useMarcarHecho()
  const deshacer = useDeshacerHecho()
  const revisar = useRevisarAviso()
  const descargar = useDescargarPlantilla()
  const ocupado = marcar.isPending || deshacer.isPending
  const sinProducto = pendientes(t.color).filter((f) => f.ponerProducto === null).length
  const vacio = cargando ? 'Cargando…' : error ? 'No se pudo cargar la lista.' : VACIA[elegida]

  const conHecho = (cual: TarjetaConHecho) => ({
    vacio, ocupado, onAbrirImei,
    onMarcar: (imeis: string[]) => { if (imeis.length > 0) marcar.mutate({ tarjeta: TARJETA_HECHO[cual], imeis }) },
    onDeshacer: (imeiSn: string) => deshacer.mutate({ tarjeta: TARJETA_HECHO[cual], imeiSn }),
  })

  const compruebalo = paraPlantillaColor(t.color).filter((f) => f.compruebalo).length
  const bajar = (cual: 'color' | 'altas') => descargar.mutate(cual, {
    onSuccess: ({ nombre, filas }) => avisar({
      texto: cual === 'color' ? avisoPlantillaColor(nombre, filas, sinProducto, compruebalo) : avisoPlantillaAltas(nombre, filas),
      tipo: 'ok',
    }),
  })

  function boton(cual: Tarjeta) {
    if (cual === 'color' || cual === 'altas') {
      const n = cual === 'color' ? paraPlantillaColor(t.color).length : pendientes(t.altas).length
      return (
        <button type="button" disabled={n === 0 || descargar.isPending} title={n === 0 ? SIN_NADA : `${n} ${n === 1 ? 'móvil' : 'móviles'}`}
          onClick={(e) => { e.stopPropagation(); bajar(cual) }} className={PRIMARIO}>
          {`⬇ Descargar plantilla${n > 0 ? ` (${n})` : ''}`}
        </button>
      )
    }
    if (cual === 'grado') {
      const n = pendientes(t.grado).length
      return (
        <button type="button" disabled={n === 0} title={n === 0 ? SIN_NADA : 'IMEI, producto y grado, para pegar'}
          onClick={(e) => {
            e.stopPropagation()
            void copiarAlPortapapeles(listaGrado(t.grado))
            avisar({ texto: avisoCopiado(n), tipo: 'ok' })
          }} className={SECUNDARIO}>
          {`Copiar lista${n > 0 ? ` (${n})` : ''}`}
        </button>
      )
    }
    const n = t.revisar.length
    return (
      <button type="button" disabled={n === 0} title={n === 0 ? SIN_NADA : undefined}
        onClick={(e) => { e.stopPropagation(); setElegida('revisar') }} className={SECUNDARIO}>Ver</button>
    )
  }

  return (
    <section aria-label="Por pasar a NSYS" className="flex min-h-0 flex-1 flex-col gap-2.5 rounded-md bg-superficie p-3">
      <TarjetasNsys tarjetas={t} elegida={elegida} onElegir={setElegida} boton={boton} />
      <div className="flex shrink-0 flex-wrap items-center gap-2.5">
        <p className="max-w-[880px] text-[11.5px] leading-snug text-azul-gris">{AYUDA[elegida]}</p>
        {elegida === 'color' && sinProducto > 0 && <span className={PASTILLA}>{`${sinProducto} sin producto en NSYS: no van en la plantilla`}</span>}
        {elegida === 'grado' && <span className={PASTILLA}>en NSYS se cambian uno a uno: «Copiar lista» para tenerlos delante y «Hecho» al acabar cada uno</span>}
        {elegida === 'revisar' && <span className={PASTILLA}>se va con «Revisado» (queda quién y cuándo) o al corregirlo</span>}
      </div>
      {elegida === 'color' && <ListaColor filas={t.color} {...conHecho('color')} />}
      {elegida === 'grado' && <ListaGrado filas={t.grado} {...conHecho('grado')} />}
      {elegida === 'altas' && <ListaAltas filas={t.altas} {...conHecho('altas')} />}
      {elegida === 'revisar' && (
        <ListaRevisar filas={t.revisar} vacio={vacio} ocupado={revisar.isPending} onAbrirImei={onAbrirImei}
          onRevisado={(idAviso) => revisar.mutate(idAviso, { onSuccess: () => avisar({ texto: 'Revisado: sale de la lista (queda en el rastro)', tipo: 'ok' }) })} />
      )}
    </section>
  )
}
```

`W/src/modules/telefonos/stock/StockTelefonosPage.tsx`:
- `import { usePorPasarNsys, useStockTelefonos } from '../api'` pasa a `import { usePorPasarTarjetas, useStockTelefonos } from '../api'`,
  e importa `totalPendientes` de `./porPasar`;
- `const porPasar = usePorPasarNsys()` y `const nPorPasar = porPasar.data?.length ?? 0` pasan a
  `const porPasar = usePorPasarTarjetas()` y `const nPorPasar = totalPendientes(porPasar.data)` (el comentario dice «0.9.22 (spec
  «plantillas NSYS» §3): las cuatro tarjetas, para su pestaña y lo pendiente»);
- `<PorPasarNsys filas={porPasar.data ?? []} …>` pasa a `<PorPasarNsys tarjetas={porPasar.data ?? null} …>`.

Y se quita lo de la 0.9.21 que ya nadie usa:
- `W/src/modules/telefonos/api.ts`: `usePorPasarNsys` (y `FilaPorPasar` y `CLAVE_POR_PASAR_NSYS` de sus imports, si no se usan);
- `W/src/modules/telefonos/test/fabrica.ts`: `filaPorPasar` (y `FilaPorPasar` del import);
- `W/src/shared/api/client.ts`: el alias `FilaPorPasar` (el servidor lo sigue publicando para la web anterior; su comentario de
  la 0.9.21 deja de nombrarlo);
- `W/src/modules/telefonos/stock/porPasar.ts`: `textoEnNsys`, `paraExcel` y `CABECERA`, y de sus imports `FilaPorPasar`,
  `SIN_SKU_MAESTRO` y `textoPoner` (`celda` se queda para `listaGrado`);
- `W/src/modules/telefonos/api.test.tsx`: la prueba de `usePorPasarNsys` y los imports de `usePorPasarNsys` y `filaPorPasar`;
- `W/src/test/server.ts`: el handler de `*/api/stock-telefonos/por-pasar` (y su comentario de la 0.9.21); el de las tarjetas se queda.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/modules/telefonos && npx tsc -b && npm run lint`
Expected: PASS, sin errores de tipos ni de lint. Después, `npm run check` (la suite entera; ver la inestabilidad conocida).

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos src/shared/api/client.ts src/test/server.ts
git commit -m "feat: Por pasar a NSYS con cuatro tarjetas, Hecho, Copiar lista, plantillas y Revisado (0.9.22, pieza 2)"
```

---

### Task 12: Web — E2E de las tarjetas (preproducción, móvil sintético)

**Files:**
- Create: `W/tests/e2e/plantillas-nsys.spec.ts`

**Interfaces:**
- Consumes: la API de la Task 7, la pestaña de la Task 11, `tests/e2e/credenciales.ts` y el móvil sintético `350000000009921` que mete
  el guion de preprod (como la 0.9.21).
- Produces: el fichero E2E. **No se corre aquí**: se corre contra preprod en el despliegue (guion de preprod), con el
  `e2e-0921.sh` de Apuntes y `WEB=/c/Users/dev/Documents/wt/web-0922-plantillas`.

- [ ] **Step 1: Write the E2E**

`W/tests/e2e/plantillas-nsys.spec.ts`:

```ts
import { readFile } from 'node:fs/promises'
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// plantillas-nsys.spec.ts (0.9.22, spec «plantillas NSYS» §3 y §6): con el móvil SINTÉTICO del guion de preprod (por defecto
// 350000000009921; otro con E2E_IMEI_TALLER), el supertécnico le cambia el color y el grado por la API, lo ve en «Cambio de color» y
// en «Cambio de grado», descarga la plantilla de color y marca y deshace «Hecho». Mide de paso lo que tarda la lista de las tarjetas
// (el cruce de «No está en NSYS»). Solo contra PREPRODUCCIÓN (se niega sin el letrero) y deja el móvil como estaba: sin marcas y con
// «Volver a lo de NSYS».
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const IMEI = process.env.E2E_IMEI_TALLER ?? '350000000009921'
const SIN_NADA = 'Este móvil no tiene nada del taller'
const MOTIVO = 'E2E 0.9.22: prueba de las tarjetas de NSYS'

type Respuesta = { estado: number; cuerpo: unknown }
type Opcion = { color: string; producto: string | null }
type FilaConHecho = { imeiSn: string; hechoEn: string | null }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Una petición a la API con el token de la sesión de la página (patrón de sku-taller.spec.ts). */
async function pedir(page: Page, metodo: string, ruta: string, cuerpo?: unknown): Promise<Respuesta> {
  return page.evaluate(async ({ metodo, ruta, cuerpo }) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(ruta, {
      method: metodo,
      headers: { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json' },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    const texto = await res.text()
    return { estado: res.status, cuerpo: texto ? (JSON.parse(texto) as unknown) : null }
  }, { metodo, ruta, cuerpo })
}

/** Sin marcas «Hecho» y sin nada del taller: 204 al quitar las marcas (aunque no hubiera) y 204, o el 400 «Este móvil no tiene
 *  nada del taller», al volver a lo de NSYS. */
async function limpiar(page: Page) {
  for (const tarjeta of ['COLOR', 'GRADO']) {
    const r = await pedir(page, 'DELETE', '/api/stock-telefonos/por-pasar/hecho', { tarjeta, imeiSn: IMEI })
    expect(r.estado, `quitar la marca ${tarjeta}: ${JSON.stringify(r.cuerpo)}`).toBe(204)
  }
  const r = await pedir(page, 'DELETE', `/api/stock-telefonos/${IMEI}/taller`, { motivo: MOTIVO })
  const detalle = `limpieza del móvil de prueba: ${r.estado} ${JSON.stringify(r.cuerpo)}`
  expect([204, 400], detalle).toContain(r.estado)
  if (r.estado === 400) expect(JSON.stringify(r.cuerpo), detalle).toContain(SIN_NADA)
}

test.describe('0.9.22: «Por pasar a NSYS» con cuatro tarjetas', () => {
  test.describe.configure({ mode: 'serial' })
  // Una sola sesión: el entorno limita los inicios de sesión por minuto.
  let pagina: Page
  // Solo true tras comprobar el letrero de preprod: sin eso la limpieza no corre.
  let enPreprod = false
  let colorNuevo = ''

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
    enPreprod = true
    expect((await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).estado,
      `el móvil de prueba ${IMEI} tiene que estar en el espejo (lo mete el guion de preprod)`).toBe(200)
    await limpiar(pagina)
  })

  test.afterAll(async () => {
    if (enPreprod) await limpiar(pagina)
    await pagina?.close()
  })

  test('un cambio de color y de grado salen en sus dos tarjetas y la plantilla de color se descarga', async () => {
    const ficha = (await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).cuerpo as { color: string | null }
    const opciones = (await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}/colores`)).cuerpo as { colores: Opcion[] }
    const otro = opciones.colores.find((o) => o.color !== ficha.color && o.producto)
    test.skip(!otro, 'el modelo del móvil de prueba no tiene otro color con producto')
    colorNuevo = otro!.color
    const puesto = await pedir(pagina, 'PUT', `/api/stock-telefonos/${IMEI}/taller`, { color: colorNuevo, grado: 'A100', motivo: MOTIVO })
    expect(puesto.estado, JSON.stringify(puesto.cuerpo)).toBe(204)

    const antes = Date.now()
    const tarjetas = await pedir(pagina, 'GET', '/api/stock-telefonos/por-pasar/tarjetas')
    const ms = Date.now() - antes
    console.log(`GET /api/stock-telefonos/por-pasar/tarjetas: ${ms} ms`)
    expect(tarjetas.estado).toBe(200)
    expect(ms, 'el límite de 15 s por petición').toBeLessThan(15_000)

    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS/ }).click()
    const fila = pagina.getByRole('table', { name: 'Cambio de color' }).getByRole('row').filter({ hasText: IMEI })
    await expect(fila).toContainText('también en «Cambio de grado» (después)')
    const descarga = pagina.waitForEvent('download')
    await pagina.getByRole('article', { name: 'Cambio de color' }).getByRole('button', { name: /^⬇ Descargar plantilla/ }).click()
    const fichero = await descarga
    expect(fichero.suggestedFilename()).toMatch(/^NSYS_color_\d{4}-\d{2}-\d{2}\.xlsx$/)
    expect((await readFile(await fichero.path())).subarray(0, 2).toString(), 'un xlsx es un zip').toBe('PK')

    await pagina.getByRole('button', { name: 'Cambio de grado' }).click()
    await expect(pagina.getByRole('table', { name: 'Cambio de grado' }).getByRole('row').filter({ hasText: IMEI }))
      .toContainText('→ A100')
  })

  test('«Hecho» marca la fila y «deshacer» la devuelve', async () => {
    test.skip(!colorNuevo, 'sin cambio de color (ver la prueba anterior)')
    await pagina.getByRole('button', { name: 'Cambio de color' }).click()
    await pagina.getByRole('button', { name: `Hecho ${IMEI}` }).click()
    await expect(pagina.getByRole('button', { name: `Deshacer Hecho de ${IMEI}` })).toBeVisible()
    const tarjetas = (await pedir(pagina, 'GET', '/api/stock-telefonos/por-pasar/tarjetas')).cuerpo as { color: FilaConHecho[] }
    expect(tarjetas.color.find((f) => f.imeiSn === IMEI)?.hechoEn, 'la marca está en el servidor').toBeTruthy()
    await pagina.getByRole('button', { name: `Deshacer Hecho de ${IMEI}` }).click()
    await expect(pagina.getByRole('button', { name: `Hecho ${IMEI}` })).toBeVisible()
  })
})
```

- [ ] **Step 2: Comprobar que se lee**

Run: `npx tsc -b && npm run lint && npx playwright test tests/e2e/plantillas-nsys.spec.ts --list`
Expected: sin errores; `--list` enseña las dos pruebas (no las corre).

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/plantillas-nsys.spec.ts
git commit -m "test: E2E de las tarjetas de Por pasar a NSYS en preprod (0.9.22, pieza 2)"
```

---

### Task 13: Cierre — versión, documentación y suites

**Files:**
- Modify: `W/package.json`, `W/package-lock.json`, `W/CHANGELOG.md`, `W/docs/paridad/imeis.md`
- Modify (raíz): `docs/novedades/NOVEDADES-v0.9.22.md`
- Modify (fuera de los repos): `/c/Users/dev/Documents/Apuntes/plan-futuro.md`

- [ ] **Step 1: Versión de la web**

```bash
cd /c/Users/dev/Documents/wt/web-0922-plantillas && npm version 0.9.22 --no-git-tag-version
```

Expected: `package.json` y `package-lock.json` en `0.9.22`.

- [ ] **Step 2: CHANGELOG de la web**

La entrada `## [0.9.22] - (pendiente) — Pieza 1 de «Lo que no cuadra con NSYS»: …` y sus tres viñetas se sustituyen por:

```markdown
## [0.9.22] - (pendiente) — Lo que no cuadra con NSYS: cuatro tarjetas, «Hecho» y plantillas para subir a NSYS

- **«Por pasar a NSYS» con cuatro tarjetas** (supertécnicos y administrador): **Cambio de color** (con la plantilla de NSYS para borrar y volver a subir el móvil, con el grado que tiene hoy en NSYS y su mismo precio de compra), **Cambio de grado** («Copiar lista»: en NSYS se cambia uno a uno, después del color), **No está en NSYS** (móviles que han pasado por el taller y NSYS no conoce, con su plantilla de altas: lo que falta va en rojo) y **Revisar a mano** (lo que el ERP no supo calcular al guardar una reparación: «✓ Revisado» si ya estaba bien, o «Corregir…»). Cada tarjeta dice cuántos hay y cuántos hechos; la pestaña cuenta lo pendiente.
- **«Hecho»** por fila y **«✓ Marcar todo»**: la fila se queda en gris con quién y cuándo, y las plantillas y «Copiar lista» ya no la llevan. Se va sola cuando la importación ve NSYS al día; si NSYS sigue igual, vuelve a salir con «Marcado «Hecho» a las …, pero NSYS sigue igual».
- **«compruébalo»**: si NSYS nunca ha escrito un color, el producto sale del nombre del maestro tal cual, avisado en la ficha, en «Corregir…» y en la lista (en amarillo en la plantilla).
- **Rastro:** «Revisado por …» cuando alguien da un aviso por bueno.
- **«Poner en NSYS» y la deducción del color del chasis** saben cómo se llama cada color en NSYS aunque ningún móvil de ese modelo y capacidad lo tenga (pieza 1): el nombre en inglés sale del «Product Name» del maestro (el Excel no se toca) y se escribe con la grafía que ya usa NSYS en otra capacidad u otro modelo.
- **Coste de NSYS** (pieza 1): al importar se guarda el «Total cost» de cada móvil; solo lo usa la plantilla de color.
- Servidor con dos migraciones aditivas: `migracion-nombres-y-coste-nsys.sql` (pieza 1) y `migracion-plantillas-nsys.sql` (tabla `Taller_hecho` y «Revisado» en `Taller_aviso`).
```

- [ ] **Step 3: Paridad**

`W/docs/paridad/imeis.md`: lee la sección de la 0.9.21 (SKU del taller) y añade detrás, con el mismo formato, una sección
«0.9.22 — «Por pasar a NSYS» con cuatro tarjetas (sin equivalente en el JavaFX)» con estos puntos:
- **Cuatro tarjetas** en Stock de teléfonos → «Por pasar a NSYS (N)»: Cambio de color, Cambio de grado, No está en NSYS y Revisar a
  mano, con su número («3 móviles · 1 hecho») y su botón; N = lo pendiente.
- **Plantillas** («⬇ Descargar plantilla» de Color y de No está en NSYS): el servidor rellena la plantilla vacía de NSYS desde la
  fila 7: `NSYS_color_AAAA-MM-DD.xlsx` (lote COLOR) y `NSYS_altas_AAAA-MM-DD.xlsx` (lote AJUSTE).
- **«Hecho»** por fila y «✓ Marcar todo»: en gris con quién y cuándo; «deshacer»; la importación la borra o la hace volver.
- **«Revisado»** en Revisar a mano: quién y cuándo en el aviso, registro de actividad `REVISAR_AVISO_TALLER` y «Revisado por …» en
  el rastro.
- **«compruébalo»** en la ficha del IMEI, en «Corregir…» y en la lista.

- [ ] **Step 4: Novedades (raíz)**

`/c/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.22.md` entero:

```markdown
# Novedades de la versión 0.9.22

---

## 📦 Stock de teléfonos (supertécnicos y administrador)

- **«Por pasar a NSYS» tiene ahora cuatro tarjetas**, cada una con su número y su botón:
  - **Cambio de color**: «⬇ Descargar plantilla» da el Excel listo para subir a NSYS (bórralos en NSYS y súbelos otra vez con
    la plantilla; lleva el grado que tienen hoy en NSYS y su mismo precio).
  - **Cambio de grado**: «Copiar lista» para tenerlos delante; en NSYS se cambian uno a uno, **después** del color.
  - **No está en NSYS**: móviles que han pasado por el taller y NSYS no conoce, con su plantilla de altas (rellena en el Excel lo
    que va en rojo).
  - **Revisar a mano**: lo que el programa no supo calcular al guardar una reparación. «Corregir…» abre el móvil; «✓ Revisado»
    si ya estaba bien.
- **«Hecho»** en cada fila (y «✓ Marcar todo»): la fila se queda en gris y ya no va en la plantilla. Desaparece sola cuando NSYS
  lo tiene; si se marcó por error, vuelve a salir con un aviso.
- Si NSYS nunca ha escrito un color, el producto lleva **«compruébalo»** (en amarillo en la plantilla): míralo antes de subirlo.
- El producto que hay que poner en NSYS sale casi siempre, también para colores que ningún móvil de ese modelo y capacidad tiene
  todavía en NSYS.

## 🔧 Formulario de reparación

- Al guardar una reparación con chasis o tapa trasera, el programa deduce el color en más casos (colores que aún no tiene ningún
  móvil en NSYS). Menos avisos de «el color del chasis no se puede deducir».

---

*Para el taller no cambia nada. Para los supertécnicos, ya no hace falta la hoja «Flujo trabajo» ni rellenar a mano la plantilla
de NSYS.*
```

- [ ] **Step 5: Plan maestro (fuera de los repos)**

`/c/Users/dev/Documents/Apuntes/plan-futuro.md`: busca lo de la 0.9.22 («plantillas», «Flujo trabajo», «Por pasar a NSYS»); marca
`[x]` lo que hace esta pieza (pendiente de producción) y añade como `[ ]`:
- «0.9.22, por mirar en NSYS con el usuario (spec §9): cómo se borran los móviles (uno a uno o en bloque), decimales del
  «Purchase price» y si NSYS avisa de un IMEI repetido».
- «Control de IMEI repetido en NSYS desde la importación (spec 0.9.22 §8), si NSYS no avisa».

- [ ] **Step 6: Suites enteras**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-plantillas && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH" && mvn -o test
cd /c/Users/dev/Documents/wt/web-0922-plantillas && npm run check
```

Expected: servidor `BUILD SUCCESS` (1434 + las nuevas, sin fallos); web, lint, tipos y Vitest en verde (si sale la inestabilidad
conocida, repetir ese fichero solo y apuntarlo).

- [ ] **Step 7: Commits**

```bash
git -C /c/Users/dev/Documents/wt/web-0922-plantillas add package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git -C /c/Users/dev/Documents/wt/web-0922-plantillas commit -m "chore: version 0.9.22, changelog y paridad de Por pasar a NSYS con cuatro tarjetas"
git -C /c/Users/dev/Documents/ProgramaReparaciones add docs/novedades/NOVEDADES-v0.9.22.md
git -C /c/Users/dev/Documents/ProgramaReparaciones commit -m "docs: novedades de la 0.9.22 (pieza 2, cuatro tarjetas y plantillas)"
```

---

## Después del plan (fuera de las tareas)

- **Revisión final de la rama** (servidor y web) y resumen; **merge `--no-ff` en `main` y push solo con el OK del usuario** (antes,
  `git fetch` y comparar con `origin`); gitlinks de la raíz en un commit aparte.
- **Preprod** (guion nuevo en `Apuntes/preprod/v0922-p2-preprod.md`, a partir del de la pieza 1):
  - migración `migracion-plantillas-nsys.sql` con vista previa (`YA_EXISTEN` 0) y comprobación (`CREADAS` **3**), servidor y web;
  - móvil sintético `350000000009921` y E2E `plantillas-nsys`, `sku-taller`, `sku-taller-reparacion`, `stock-telefonos`,
    `roles-stock` (con `$env:WEB = "/c/Users/dev/Documents/wt/web-0922-plantillas"` y `e2e-0921.sh`);
  - la vuelta de una marca: marcar «Hecho» el color del sintético, esperar la siguiente importación (NSYS no ha cambiado) → sale
    «Marcado «Hecho» a las …, pero NSYS sigue igual»; después `UPDATE Nsys_movil SET COLOR = '<el nuevo>' …` del sintético, siguiente
    importación → la fila y la marca se van (en el log, «marcas «Hecho» tras la importación: 1 quitadas…»);
  - medir `GET /por-pasar/tarjetas` (la E2E lo escribe) y la descarga de las dos plantillas por debajo de 15 s;
  - **abrir las plantillas en Excel** y compararlas con la vacía de NSYS (cabecera intacta, datos desde la fila 7, rojo y amarillo).
    **No subirlas a NSYS desde preprod**: NSYS es el de verdad;
  - «✓ Revisado» de un aviso real de preprod y «Revisado por …» en su rastro.
- **Producción** en horario (migración aditiva y corte de ~5 s del servidor; la web después), guion en `Apuntes/prod-v0922-p2.md`.
  La primera subida real de una plantilla de color a NSYS, con el usuario, mirando antes lo pendiente del §9 de la spec
  (borrado, decimales, IMEI repetido).

