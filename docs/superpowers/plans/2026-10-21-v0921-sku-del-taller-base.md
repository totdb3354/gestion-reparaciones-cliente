# 0.9.21 — SKU del taller (pieza 1: la base) · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el ERP guarde lo del taller (color y grado, con la batería nueva y el «Ms») por encima de NSYS, lo enseñe en
el stock, la ficha, «Pegar varios IMEIs», el buscador y el rastro con lo de NSYS al lado, calcule el SKU nuevo con el
maestro, deje corregirlo a mano y lleve una lista «Por pasar a NSYS» que se vacía sola.

**Architecture:**
- **Base de datos:** una tabla nueva, `Taller_cambio` (migración aditiva `sql/migracion-sku-taller.sql` y `crear_bd.sql`).
  Cada fila es un cambio del taller a un dato (COLOR o GRADO) de un IMEI; su `ESTADO` dice qué fue de él.
- **Servidor** (paquete `com.reparaciones.servidor.nsys`):
  - `TallerDAO` (Taller_cambio) y `NsysDAO.goodsMasUsados` (el producto de cada color);
  - reglas puras: `Maestro` (SKU, colores y SKU por color y grado), `LoQueVale` (lo del taller sobre NSYS) y
    `CierreTaller` (pasado / manda NSYS / sigue);
  - servicio `TallerNsys`: lo que vale, corrección a mano, «Volver a lo de NSYS», cierre, lista «Por pasar a NSYS» y opciones
    del diálogo; `ImportadorNsys` lanza el cierre tras cada Items ACTUAL que acaba en OK;
  - `StockTelefonosController`: lo que vale en `GET /`, `GET /{imeiSn}`, `POST /comprobar` y `GET /buscar`; los eventos del
    taller en `GET /{imeiSn}/rastro`; nuevas `PUT` y `DELETE /{imeiSn}/taller`, `GET /por-pasar` y `GET /{imeiSn}/colores`.
- **Web:** un componente común `MarcaTaller` («taller» + «NSYS: …») en `shared/ui`; marcas en la tabla del stock, la ficha,
  «Pegar varios IMEIs» y el buscador; textos del rastro; diálogo «Corregir color, grado o batería…» en la ficha del IMEI;
  pestaña «Por pasar a NSYS (N)» con «Exportar a Excel» en el stock de teléfonos.
- **E2E** en preprod con un móvil sintético que mete el guion.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5, Mockito y MockMvc (sin base de datos).
- **Web:** React 19 + TypeScript, TanStack Query/Table, openapi-fetch y Radix (Dialog). Pruebas con Vitest, Testing Library,
  user-event, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-21-v0921-sku-del-taller-base-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-10-21-v0921-prototipo-sku-del-taller-base.html`, variantes «A · se vacía sola» y
«"NSYS: Azul" debajo» (las B y C del prototipo **no** se construyen).

## Global Constraints

**Versión, código de partida y base de datos**
- Versión **0.9.21**. Toca **servidor, base de datos y web**. Sale **sola** (no va en una subida conjunta).
- Base de datos: **solo una migración aditiva** (`CREATE TABLE Taller_cambio`), en `sql/migracion-sku-taller.sql` y al final
  de `sql/crear_bd.sql`; sin cambios en las tablas `Nsys_*` ni en ninguna otra.
- En producción hoy: web `main` `55db8e9` (0.9.20, con el panel flotante de carga en `AppLayout`; el `DialogContent`
  compartido ignora los clics dentro de `[data-encima-de-ventanas]`) y servidor `f27b170` (0.9.18.2; `main` del servidor es su
  merge `4c4cfe5`). Las ramas salen del `origin/main` de cada repo **en el momento de crearlas** (`git fetch` antes y mirar el
  commit; no dar por hecho ningún hash).
- **La «Corrección en NSYS» va antes, dentro de la misma 0.9.21** (decisión del usuario del 21/10, en la otra sesión que la
  programa; ramas `dev_v0921_correccion_nsys` en servidor y web). Entra **primero** en `main` del servidor y de la web, y las
  ramas `dev_v0921_sku_taller` salen de esos `main` con ella dentro; si al crearlas no está mergeada, **parar y preguntar**.
  Esa sesión avisa con los commits y la forma final. Forma prevista, que este plan da por hecha **y que el controlador
  comprueba al crear las ramas** (si difiere, adapta los briefs de las Tasks 5, 7, 9 y 13 antes de despacharlas):
  - `EventoRastro` trae ya `loteAnterior` (nullable) como último campo y el tipo `CORRECCION`. Los campos de esta pieza
    (`dato`, `origen`, `motivo`) van **después** de `loteAnterior`: en cada `new EventoRastro(…)` de las Tasks 5 y 9 va un
    `null` (o el lote anterior) en la posición de `loteAnterior`, antes de los tres nuevos.
  - `Rastro.unir` ya recibe los lotes: `unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos)`.
    Esta pieza añade `cambios` al final: `unir(lotes, nsys, trabajos, cambios)`, y la de tres parámetros llama a esta con
    `List.of()`. La detección de correcciones (`Correcciones`) no se toca.
  - `StockTelefonosController.ficha` y `GET /` ya cuentan días, entradas, revisado y última caja sobre la cadena de
    correcciones; esta pieza añade lo que vale encima sin cambiar esas cuentas.
  - Web: `rastro.ts` ya tiene el caso `CORRECCION`; los casos `TALLER_*` se añaden al lado.
  - **Versión y documentación compartidas:** la primera en entrar (la corrección) sube la web a `0.9.21`, abre la entrada
    `[0.9.21]` del CHANGELOG y crea `docs/novedades/NOVEDADES-v0.9.21.md`. La Task 13 **no** vuelve a subir la versión si
    ya es `0.9.21`: añade su parte a esa entrada y a esas novedades.
  - **Una sola subida:** preprod y producción de la Task 14 y 15 llevan las dos partes (lo que haya en los `main`); el tag
    `v0.9.21` va después de producción, con el OK del usuario.

**Ramas, worktrees y registro**
- Rama **`dev_v0921_sku_taller`** en los dos repos, en worktrees. **Los clones normales no se tocan** (los usan otras sesiones).
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0921-sku` (lo crea la Task 1).
  - Web: `/c/Users/dev/Documents/wt/web-0921-sku`, con `npm ci` dentro (lo crea la Task 7).
  - Las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan se refieren a esos worktrees;
    `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0921-sku/`** (raíz).

**Commits, merge y datos de prueba**
- Commits en español **sin tildes**, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Merge `--no-ff`, push, tags y gitlinks **solo con el OK del usuario**.
- **Repos públicos:** datos de prueba sintéticos (IMEI `35000000000xxxx`, «Técnico 1/2/3», lotes `BT-9001`, productos
  «Apple iPhone 13 128GB Blue»…). Nunca datos reales de NSYS, IP, identificadores de Drive ni nombres reales.

**Reglas de rol** (spec §1 y §3)
- Todo `/api/stock-telefonos/**`, también las rutas nuevas, va con la guarda de clase que ya existe:
  `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")`. Sin permisos nuevos.
- **Web:** el botón «Corregir color, grado o batería…» solo sale en la ficha de NSYS, que ya solo ven SUPERTECNICO y ADMIN y
  solo si el IMEI está en el espejo. El TECNICO no ve nada nuevo.

**Reglas de negocio** (spec §2)
- **Lo que vale**, por IMEI (no por lote): lo del taller VIGENTE de cada dato o, si no hay, lo de NSYS (`Nsys_movil.COLOR` y
  `Nsys_movil.GRADE` de la fila más reciente del IMEI). El grado del taller se escribe como en NSYS: `[Ms]<letra>[100]` con
  letra `A`, `A-`, `B` o `C` (patrón `(Ms)?(A|A-|B|C)(100)?`).
- **SKU que vale:** sin nada del taller, `Nsys_movil.SKU` (como hoy). Con algo, el `SKU_WEB` de `Nsys_producto` con el mismo
  `MODELO`, `MODELO_MAESTRO`, `CAPACIDAD` y `ES_ESIM` que el móvil y `COLOR` y `CALIDAD_DETALLE` = color y grado que valen: un
  solo SKU distinto → ese; ninguno, varios distintos o el móvil sin `MODELO_MAESTRO` → «sin SKU en el maestro».
- **Cierre**, tras cada importación ACTUAL que acaba en `OK`: NSYS ahora = lo del taller → `PASADO`; NSYS ahora ≠ lo que
  tenía al hacer el cambio → `MANDA_NSYS`; si no (o si NSYS ahora no tiene el dato), sigue `VIGENTE`. Si el cierre falla, la
  importación sigue `OK`.
- **Corrección a mano:** motivo obligatorio (como mucho **500** caracteres); por dato que cambia: si el nuevo es lo de NSYS,
  `QUITADO` (nace cerrado); si no, `VIGENTE`; el `VIGENTE` anterior del dato pasa a `SUSTITUIDO` y el nuevo apunta a él
  (`ID_CAMBIO_ANTERIOR`). Sin ningún cambio → 400. IMEI fuera del espejo → 404. «Volver a lo de NSYS»: motivo opcional; sin
  nada del taller → 400.
- **Registro de actividad:** acción **`CORREGIR_MOVIL_TALLER`**, una línea por dato, detalle `IMEI: <imei>, COLOR: Azul → Rojo`
  (o `GRADO: B → A100`), con ` (vuelve a lo de NSYS)` detrás cuando vuelve a lo de NSYS, y el motivo en su columna.
- **Producto a poner en NSYS:** si el color no cambia, el `GOODS` del móvil; si cambia, el `GOODS` más usado en `Nsys_movil`
  entre los móviles del mismo `MODELO`, `MODELO_MAESTRO`, `CAPACIDAD`, `ES_ESIM` y el color nuevo; si ninguno, el texto
  «(ningún móvil de ese color en NSYS: búscalo a mano)».
- **Horas:** `Taller_cambio.FECHA` y `CERRADO_EN` en **hora de Madrid** (como el espejo), puestas por el servidor con
  `LocalDateTime.now(clock)` truncado a segundos; nunca `CURRENT_TIMESTAMP` de la base (que va en UTC).

**Textos exactos de la web**
- **Marca:** «taller» y, debajo (o al lado en la ficha y el buscador), «NSYS: <valor>»; si NSYS no sabe el dato, «NSYS: —».
  Un SKU que no sale del maestro: «sin SKU en el maestro».
- **Ficha del IMEI:** «Por pasar a NSYS: <producto> · grado <grado>», con «(ningún móvil de ese color en NSYS: búscalo a
  mano)» si no hay producto; botón «Corregir color, grado o batería…».
- **Diálogo:** título «Corregir <IMEI>»; campos «Color», «Grado», casillas «Batería nueva (100)» y «Mensaje de pieza (Ms)»,
  «Motivo» (placeholder «Obligatorio: por qué se corrige»); sin colores, la única opción «Sin colores en el maestro»; región
  «Vista previa» con «Color: Azul → Rojo», «Grado: B → A100», «Sin cambios todavía.», «SKU: <sku>» o «SKU: sin SKU en el
  maestro: se guarda igual», «En NSYS habrá que poner: <producto> · grado <grado>» y «Coincide con NSYS: se quita lo del
  taller.»; botones «Volver a lo de NSYS» (`title` «Quita lo del taller y vuelve a lo que dice NSYS»), «Cancelar» y
  «Guardar»; avisos «Corrección guardada» y «Lo del taller quitado: vale lo de NSYS».
- **Stock de teléfonos:** grupo «Vista» con «Móviles» y «Por pasar a NSYS (N)»; tabla «Móviles por pasar a NSYS» con columnas
  «IMEI · Modelo · En NSYS hoy · Poner en NSYS · SKU nuevo · Por qué · Desde»; ayuda «Lo que hay que cambiar a mano en NSYS
  (producto y grado de cada móvil). Cada fila desaparece sola cuando la importación (cada 30 min) ve NSYS igual.»; vacía,
  «Nada por pasar: NSYS está al día con el taller.»; botón «Exportar a Excel» y aviso «Copiado: pégalo en Excel»; «Por qué»:
  «Color: a mano · Supertécnico 1 · «motivo»» (sin motivo, sin la última parte).
- **Rastro** (fuente «TALLER», leyenda «Lo del taller»): «Taller: color Azul → Rojo (a mano · Supertécnico 1 · «motivo»)»,
  «Vuelve a lo de NSYS: grado A100 → B (a mano · Supertécnico 1 · «motivo»)», «Pasado a NSYS: grado A100» y
  «Manda NSYS: color Verde (el del taller, Rojo, deja de valer)». Sin motivo, el paréntesis acaba en el nombre.

**Colores (tokens nuevos del tema, los del prototipo)**
- `--color-taller: #7C3AED` (marca y punto del rastro), `--color-taller-bg: #F1EAFE` (fondo de la fuente TALLER y de la vista
  previa) y `--color-taller-text: #6D28D9` (texto de «Por pasar a NSYS»), en `src/shared/styles/tokens.css`.

**Comandos**
- **Servidor** (Git Bash, en `/c/Users/dev/Documents/wt/servidor-0921-sku`):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest='Clase1,Clase2'`). `OpenApiContractTest` levanta el contexto entero: pilla un
  cableado roto y deja `target/openapi.json` para los tipos de la web.
- **Web** (en `/c/Users/dev/Documents/wt/web-0921-sku`): `npx vitest run <ruta>`, `npx tsc -b`, `npm run lint` y, al cerrar,
  `npm run check`. Lint con las reglas del compilador de `eslint-plugin-react-hooks` 7: nada de `new Date()` ni `Date.now()`
  en el cuerpo de un componente o un hook. Un módulo no importa de otro: lo común va a `src/shared`.
- **Inestabilidad conocida de la web:** con la suite entera, algún test puede pasar de los 5 s («orden de llamadas…» de
  `FormularioReparacion`, `NuevoPedidoDialog`, `StockPage`, `HistorialPage`); solos pasan. Si pasa, se repite ese fichero solo
  y se apunta en el registro; no se tocan.

**Preproducción y producción**
- **E2E solo en preproducción**, con datos sintéticos (el móvil `350000000009921` que mete el guion) y limpieza; **nunca en
  producción**.
- **Claude no hace SSH.** Prepara los guiones en `Apuntes/` y el usuario los ejecuta y pega la salida. Cada bloque empieza con
  `hostname` y la comprobación de `backup-erp.sh` (solo existe en producción). Avisar de que `docker compose up -d --build`
  tarda ~1 min sin salida.
- Orden del despliegue: **migración → servidor → web**. La web 0.9.20 funciona con el servidor 0.9.21 (solo campos nuevos).

## Ajustes a la spec (se suben a la spec en el commit del plan)

1. **Columnas de más en `Taller_cambio`:**
   - `ID_CAMBIO_ANTERIOR` (null, clave ajena a `Taller_cambio.ID_CAMBIO`): el VIGENTE del mismo IMEI y dato al que sustituye
     (el que pasa a SUSTITUIDO). **Añadido por la pieza 2, aprobado el 21/10.** En esta pieza solo se rellena al sustituir.
   - `VALOR_CIERRE` (null): lo que NSYS tenía al cerrarse solo (PASADO y MANDA_NSYS), para que el rastro diga «Manda NSYS:
     color Verde…».
   - «Lo que valía antes» no se guarda: se deduce al leer (el VALOR del anterior o, si no hay, VALOR_NSYS).
   - `VALOR` admite null (solo al volver a un NSYS que no tiene ese dato); `MOTIVO` es `VARCHAR(500)`; `ID_USU` lleva clave
     ajena a `Usuario` con `ON DELETE SET NULL`, como `Log_Actividad`.
2. **Quién y por qué vuelve a NSYS:** «Volver a lo de NSYS», y una corrección al valor de NSYS, se guardan como **un cambio
   propio en estado QUITADO** (nace cerrado, con su usuario y su motivo) y dejan el VIGENTE anterior en SUSTITUIDO. Así el
   rastro y el registro saben quién volvió y por qué sin columnas de cierre.
3. **Cierre con NSYS sin el dato:** si NSYS ahora no tiene el dato (un producto que el maestro no traduce deja el color
   vacío), el cambio sigue vigente: no se sabe si coincide.
4. **SKU también por `MODELO`:** el SKU y los colores se buscan con `MODELO` (código del ERP, null fuera del catálogo)
   además de `MODELO_MAESTRO`, `CAPACIDAD` y `ES_ESIM`: un iPad 11 nunca pasa por un iPhone 11 (como `LectorMaestro`).
5. **`GET /{imeiSn}/colores`** devuelve, además de los colores, el producto que habría que poner en NSYS con cada uno, el
   producto actual y los SKU del modelo por color y grado: la vista previa del diálogo sale sin otra llamada.
6. **«NSYS: —»** cuando el taller cambia un dato que NSYS no sabe (p. ej. un SKU que NSYS no tenía).
7. **Buscador de IMEI:** no enseña el grado (tampoco hoy); lleva el color que vale con la marca.
8. **«Exportar a Excel»** copia la tabla para pegarla en Excel (tabuladores), como «Copiar el resultado (para Excel)» de
   «Pegar varios IMEIs» (la spec lo pide «como la exportación de Pegar varios IMEIs»); el aviso es «Copiado: pégalo en
   Excel». El prototipo simulaba una descarga `.xlsx`: la web no tiene escritor de xlsx.
9. **«Desde»** de la lista es la fecha del cambio vigente **más antiguo** del móvil, y la lista va de más antiguo a más
   reciente por esa fecha (el prototipo tomaba el más reciente).
10. **«Volver a lo de NSYS»** usa la misma acción del registro, `CORREGIR_MOVIL_TALLER`, con « (vuelve a lo de NSYS)» en el
    detalle. `PUT` y `DELETE /{imeiSn}/taller` responden **204**; la web recarga la ficha, el rastro, el stock y la lista.
11. **Ficha:** con el grado cambiado por el taller no se dice «(antes X)» (ese grado anterior es el de NSYS).
12. **Documentación:** el servidor no tiene CHANGELOG; su parte va en `docs/schema.md`. No hay ficha de paridad del stock de
    teléfonos (no existe en el JavaFX): la sección nueva va en `docs/paridad/imeis.md` y el resto en el CHANGELOG de la web.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`; clases en `src/main/java/com/reparaciones/servidor/nsys/` y pruebas en
`src/test/java/com/reparaciones/servidor/nsys/` salvo que se diga otra cosa)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `sql/migracion-sku-taller.sql` (nuevo), `sql/crear_bd.sql`, `sql/reset-uat.sql`, `docs/schema.md` | Tabla `Taller_cambio` | 1 |
| `DatoTaller.java`, `CambioTaller.java`, `TallerDAO.java` (nuevos) + `TallerDAOTest.java`, `TallerDePrueba.java` | Datos del taller y su acceso | 1 |
| `Normalizacion.java` (`mismoTexto`), `Maestro.java`, `SkuMaestro.java` (nuevos), `NsysDAO.java` (`goodsMasUsados`) + `MaestroTest`, `NormalizacionTest`, `NsysDAOTest` | Búsquedas en el maestro y en el espejo | 2 |
| `LoQueVale.java`, `CierreTaller.java` (nuevos) + `LoQueValeTest`, `CierreTallerTest` | Reglas puras | 3 |
| `TallerNsys.java` (nuevo), `ImportadorNsys.java` + `TallerNsysTest`, `ImportadorNsysTest` | Servicio y cierre tras la importación | 4 |
| `StockTelefonosController.java`, `EventoRastro.java`, `Rastro.java` + `StockTelefonosControllerTest`, `RastroTest`, `controller/RolesStockTelefonosTest`, `OpenApiContractTest` | Lecturas con lo que vale y rastro | 5 |
| `FilaPorPasar.java`, `PorQueTaller.java`, `OpcionesTaller.java`, `OpcionColor.java` (nuevos), `TallerNsys.java`, `StockTelefonosController.java` + las mismas pruebas | Corrección, vuelta, lista y opciones | 6 |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts`, `src/shared/api/nsys.ts` | Contrato, alias y claves de consulta comunes | 7 |
| `src/modules/telefonos/api.ts` (+ test), `src/modules/taller/imeis/apiNsys.ts` (+ `apiNsys.test.tsx` nuevo) | `usePorPasarNsys`, `useOpcionesTaller`, `useCorregirTaller`, `useVolverANsys` | 7 |
| `src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts`, `src/test/server.ts`, `src/shared/styles/tokens.css` | Fábricas, MSW y colores | 7 |
| `src/shared/ui/MarcaTaller.tsx` (+ test), `src/shared/lib/telefonoNsys.ts` (+ test) | Marca común y textos | 8 |
| `src/modules/telefonos/stock/{columnas.tsx,ComprobarImeis.tsx,BuscadorImei.tsx}` + `StockTelefonosPage.test.tsx`, `ComprobarImeis.test.tsx`, `modelo.test.ts` | Marcas en el stock | 8 |
| `src/modules/taller/imeis/{FichaMovil.tsx,rastro.ts,PanelRastro.tsx}` + sus tests | Ficha y rastro | 9 |
| `src/modules/taller/imeis/{corregir.ts,CorregirTallerDialog.tsx}` (nuevos, + tests), `ImeiDetallePage.tsx` (+ test) | Diálogo «Corregir…» | 10 |
| `src/modules/telefonos/stock/{porPasar.ts,PorPasarNsys.tsx}` (nuevos, + `porPasar.test.ts`), `estado.ts`, `StockTelefonosPage.tsx` (+ test) | Pestaña «Por pasar a NSYS» | 11 |
| `tests/e2e/sku-taller.spec.ts` (nuevo) | E2E en preprod | 12 |
| `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/imeis.md` | Versión y documentación | 13 |

**Raíz y apuntes** (fuera de los worktrees)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `docs/novedades/NOVEDADES-v0.9.21.md` | Novedades para el taller | 13 |
| `Apuntes/plan-futuro.md` | Plan maestro | 13 |
| `Apuntes/preprod/v0921-preprod.md`, `Apuntes/prod-v0921.md` | Guiones de despliegue (privados) | 14, 15 |

---

### Task 1: Servidor — tabla `Taller_cambio` y su acceso

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-sku-taller.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (al final), `gestion-reparaciones-servidor/sql/reset-uat.sql`, `gestion-reparaciones-servidor/docs/schema.md`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/DatoTaller.java`, `CambioTaller.java`, `TallerDAO.java`
- Create (test): `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java`, `TallerDePrueba.java`

**Interfaces:**
- Consumes: `NsysDAO.trozos(List<T>, int)`, `NsysDAO.marcas(int)` y `NsysDAO.TANDA_IN` (paquete, ya en la 0.9.18).
- Produces:
  - `public enum DatoTaller { COLOR, GRADO }` con `String deNsys(MovilNsys m)` (COLOR → `m.color()`, GRADO → `m.grade()`).
  - `public record CambioTaller(int id, String imeiSn, DatoTaller dato, String valor, String valorAntes, String valorNsys,
    String origen, String idRep, Integer idUsu, String usuario, String motivo, LocalDateTime fecha, Estado estado,
    LocalDateTime cerradoEn, String valorCierre, Integer idAnterior)` con
    `public enum Estado { VIGENTE, SUSTITUIDO, QUITADO, PASADO, MANDA_NSYS }` y `public static final String MANO = "MANO"`.
  - `TallerDAO` (`@Repository`):
    - `List<CambioTaller> vigentes()`, `List<CambioTaller> vigentes(Collection<String> imeis)`, `List<CambioTaller> deImei(String imeiSn)`
      (del más antiguo al más reciente);
    - `public record Nuevo(DatoTaller dato, String valor, String valorNsys, CambioTaller.Estado estado)`;
    - `@Transactional void guardar(String imeiSn, List<Nuevo> nuevos, int idUsu, String motivo, LocalDateTime ahora)`;
    - `int cerrar(int idCambio, CambioTaller.Estado estado, String valorCierre, LocalDateTime ahora)`;
    - constantes de paquete `COLUMNAS`, `DESDE`, `ORDEN`, `SQL_BLOQUEAR`, `SQL_VIGENTE`, `SQL_SUSTITUIR`, `SQL_INSERTAR`,
      `SQL_CERRAR` y `static CambioTaller cambio(ResultSet, int)`.
  - Test: `TallerDePrueba` con `HOY`, `movil(imei, color, grade)`, `sinMaestro(imei, grade)`, `vigente(id, imei, dato, valor, valorNsys)`,
    `vigente(id, imei, dato, valor, valorNsys, fecha)` y `producto(goodsColor, color, grado)` (lo usan las Tasks 2-6).

- [ ] **Step 1: Worktree del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git log --oneline -1 origin/main
git worktree add -b dev_v0921_sku_taller /c/Users/dev/Documents/wt/servidor-0921-sku origin/main
cd /c/Users/dev/Documents/wt/servidor-0921-sku && git log --oneline -1
```

Expected: `Preparing worktree (new branch 'dev_v0921_sku_taller')` y el mismo commit que `origin/main` (hoy `4c4cfe5`, la
0.9.18.2). Crea `.superpowers/sdd/v0921-sku/progress.md` (raíz) con la rama y ese commit.

- [ ] **Step 2: Migración**

Crea `sql/migracion-sku-taller.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-sku-taller.sql — 0.9.21, SKU del taller (pieza 1: la base)
-- (spec docs/superpowers/specs/2026-10-21-v0921-sku-del-taller-base-design.md §4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.21. Aditiva: el servidor y la web anteriores no usan esta tabla.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: el SELECT debe devolver 0 (no existe).
-- 2) Ejecutar el CREATE TABLE.
-- FECHA y CERRADO_EN van en hora de Madrid, como el espejo de NSYS: las pone el servidor.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTE
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_cambio';

-- Lo del taller por encima de NSYS, por IMEI (no por lote): un cambio por fila. Como mucho un VIGENTE por IMEI y dato (lo
-- garantiza el servidor). QUITADO: un cambio a mano que vuelve a lo de NSYS (nace cerrado). SUSTITUIDO: lo tapó otro cambio
-- del mismo dato, que apunta a él con ID_CAMBIO_ANTERIOR.
CREATE TABLE Taller_cambio (
    ID_CAMBIO          INT          NOT NULL AUTO_INCREMENT,
    IMEI_SN            VARCHAR(40)  NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN',
    DATO               ENUM('COLOR','GRADO') NOT NULL,
    VALOR              VARCHAR(40)  NULL COMMENT 'Lo del taller: color del maestro o grado como NSYS ([Ms]<letra>[100]); NULL solo al volver a un NSYS sin ese dato',
    VALOR_NSYS         VARCHAR(40)  NULL COMMENT 'Lo que NSYS tenía al hacer el cambio',
    ORIGEN             ENUM('MANO','REPARACION') NOT NULL DEFAULT 'MANO',
    ID_REP             VARCHAR(30)  NULL COMMENT 'Pieza 2: la reparación que lo cambió',
    ID_USU             INT          NULL,
    MOTIVO             VARCHAR(500) NULL,
    FECHA              DATETIME     NOT NULL,
    ESTADO             ENUM('VIGENTE','PASADO','MANDA_NSYS','QUITADO','SUSTITUIDO') NOT NULL DEFAULT 'VIGENTE',
    CERRADO_EN         DATETIME     NULL,
    VALOR_CIERRE       VARCHAR(40)  NULL COMMENT 'PASADO y MANDA_NSYS: lo que NSYS tenía al cerrarse',
    ID_CAMBIO_ANTERIOR INT          NULL COMMENT 'El VIGENTE del mismo IMEI y dato al que sustituye (pasa a SUSTITUIDO)',
    PRIMARY KEY (ID_CAMBIO),
    KEY idx_taller_cambio_imei (IMEI_SN, DATO, ESTADO),
    KEY idx_taller_cambio_estado (ESTADO),
    CONSTRAINT fk_taller_cambio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL,
    CONSTRAINT fk_taller_cambio_anterior FOREIGN KEY (ID_CAMBIO_ANTERIOR) REFERENCES Taller_cambio (ID_CAMBIO)
);
```

- [ ] **Step 3: `crear_bd.sql`, `reset-uat.sql` y `schema.md`**

- **`crear_bd.sql`:** al final del fichero (tras el `INSERT INTO Nsys_ubicacion …;`), añade una línea en blanco, el comentario
  `-- 0.9.21: SKU del taller (pieza 1; ver sql/migracion-sku-taller.sql)` y, debajo, **tal cual** el comentario de tres líneas
  y el `CREATE TABLE Taller_cambio (…);` del Step 2 (sin el `USE` ni la vista previa).
- **`reset-uat.sql`:** justo antes de `-- 5b. Espejo de NSYS (0.9.17): …`, añade:

```sql
-- 5a. SKU del taller (0.9.21): va por IMEI, así que cae con el espejo. Romper la auto-referencia antes de borrar.
UPDATE Taller_cambio SET ID_CAMBIO_ANTERIOR = NULL;
DELETE FROM Taller_cambio;

```

- **`docs/schema.md`:** al final de la lista de tablas, tras la línea de `Nsys_ubicacion`, añade:

```markdown
- `Taller_cambio` (0.9.21: lo del taller por encima de NSYS —color y grado— por IMEI, con su historia, quién y por qué, y su cierre automático cuando NSYS lo alcanza o lo cambia; ver `sql/migracion-sku-taller.sql`)
```

- [ ] **Step 4: Tests (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Datos inventados del SKU del taller (repos públicos): un iPhone 13 128GB y sus cambios. El SKU de NSYS y el del maestro
 *  se escriben igual, i13128<color><grado>, para que las pruebas se lean de un vistazo. */
final class TallerDePrueba {

    static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 21, 10, 0);

    private TallerDePrueba() {}

    /** Un iPhone 13 128GB en Listos con este color y este grado de NSYS (y su SKU, i13128<color><grado>). */
    static MovilNsys movil(String imei, String color, String grade) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128" + color + grade, "13", "13", "128GB", color, grade, false, "Listos",
                "Listos", EstadoMovil.STOCK, HOY.minusDays(20), HOY.minusDays(1));
    }

    /** Un móvil cuyo producto no está en el maestro: sin modelo, color ni SKU. */
    static MovilNsys sinMaestro(String imei, String grade) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, null, null, null, null, null, null, false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY.minusDays(20), HOY.minusDays(1));
    }

    /** Un cambio VIGENTE a mano de «Técnico 1» (ID_USU 7), de ayer, que no sustituye a ninguno. */
    static CambioTaller vigente(int id, String imei, DatoTaller dato, String valor, String valorNsys) {
        return vigente(id, imei, dato, valor, valorNsys, HOY.minusDays(1));
    }

    static CambioTaller vigente(int id, String imei, DatoTaller dato, String valor, String valorNsys, LocalDateTime fecha) {
        return new CambioTaller(id, imei, dato, valor, valorNsys, valorNsys, CambioTaller.MANO, null, 7, "Técnico 1", "prueba",
                fecha, CambioTaller.Estado.VIGENTE, null, null, null);
    }

    /** Un producto del maestro del iPhone 13 128GB: SKU i13128<color><grado>. */
    static ProductoMaestro producto(String goodsColor, String color, String grado) {
        return new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB " + goodsColor + " " + grado), "13", "13",
                "128GB", color, grado, "i13128" + color + grado, null, "i13128" + color, false);
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El acceso a Taller_cambio con el JdbcTemplate mockeado, sin base de datos (como NsysDAOTest). */
class TallerDAOTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 21, 10, 0);
    private static final String IMEI = "350000000000001";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TallerDAO dao = new TallerDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losVigentesDeTodoElTallerVanEnUnaConsultaDelMasAntiguoAlMasReciente() {
        assertTrue(dao.vigentes().isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.ESTADO = 'VIGENTE'" + TallerDAO.ORDEN),
                any(RowMapper.class));
        assertEquals(" ORDER BY c.FECHA, c.ID_CAMBIO", TallerDAO.ORDEN);
    }

    @Test void losVigentesDeUnosImeisVanEnTandasDeQuinientos() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.vigentes(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(0).contains(" WHERE c.ESTADO = 'VIGENTE' AND c.IMEI_SN IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).endsWith(" AND c.IMEI_SN IN (?)" + TallerDAO.ORDEN), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.vigentes(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void todosLosCambiosDeUnImeiParaElRastro() {
        assertTrue(dao.deImei(IMEI).isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.IMEI_SN = ?" + TallerDAO.ORDEN),
                any(RowMapper.class), eq(IMEI));
    }

    @Test void loQueValiaAntesSaleDelAnteriorOLoDeNsysYQuienPorSuNombre() {
        assertTrue(TallerDAO.COLUMNAS.contains("CASE WHEN a.ID_CAMBIO IS NULL THEN c.VALOR_NSYS ELSE a.VALOR END AS VALOR_ANTES"),
                TallerDAO.COLUMNAS);
        assertTrue(TallerDAO.DESDE.contains("LEFT JOIN Taller_cambio a ON a.ID_CAMBIO = c.ID_CAMBIO_ANTERIOR"), TallerDAO.DESDE);
        assertTrue(TallerDAO.DESDE.contains("LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"), TallerDAO.DESDE);
    }

    @Test void unaFilaDeTallerCambio() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_CAMBIO")).thenReturn(5);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("DATO")).thenReturn("GRADO");
        when(rs.getString("VALOR")).thenReturn("A100");
        when(rs.getString("VALOR_ANTES")).thenReturn("B");
        when(rs.getString("VALOR_NSYS")).thenReturn("B");
        when(rs.getString("ORIGEN")).thenReturn("MANO");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Técnico 1");
        when(rs.getString("MOTIVO")).thenReturn("batería cambiada");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.getString("ESTADO")).thenReturn("PASADO");
        when(rs.getTimestamp("CERRADO_EN")).thenReturn(Timestamp.valueOf(HOY.plusHours(2)));
        when(rs.getString("VALOR_CIERRE")).thenReturn("A100");
        when(rs.getInt("ID_CAMBIO_ANTERIOR")).thenReturn(3);
        assertEquals(new CambioTaller(5, IMEI, DatoTaller.GRADO, "A100", "B", "B", "MANO", null, 7, "Técnico 1",
                "batería cambiada", HOY, CambioTaller.Estado.PASADO, HOY.plusHours(2), "A100", 3), TallerDAO.cambio(rs, 0));
    }

    @Test void sinUsuarioNiAnteriorNiCierreSonNulos() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("DATO")).thenReturn("COLOR");
        when(rs.getString("ESTADO")).thenReturn("VIGENTE");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.wasNull()).thenReturn(true);
        CambioTaller c = TallerDAO.cambio(rs, 0);
        assertNull(c.idUsu());
        assertNull(c.idAnterior());
        assertNull(c.cerradoEn());
    }

    @Test void guardarBloqueaElMovilSustituyeElVigenteYApuntaAEl() {
        when(jdbc.queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "GRADO")).thenReturn(List.of(4));
        dao.guardar(IMEI, List.of(new TallerDAO.Nuevo(DatoTaller.COLOR, "Rojo", "Azul", CambioTaller.Estado.VIGENTE),
                new TallerDAO.Nuevo(DatoTaller.GRADO, "B", "B", CambioTaller.Estado.QUITADO)), 7, "motivo", HOY);
        Timestamp t = Timestamp.valueOf(HOY);
        InOrder orden = inOrder(jdbc);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_BLOQUEAR, String.class, IMEI);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "COLOR");
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "MANO", 7, "motivo", t, "VIGENTE",
                null, null);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "GRADO");
        orden.verify(jdbc).update(TallerDAO.SQL_SUSTITUIR, t, 4);
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "GRADO", "B", "B", "MANO", 7, "motivo", t, "QUITADO", t, 4);
        verifyNoMoreInteractions(jdbc);
    }

    @Test void guardarEsTodoONada() throws NoSuchMethodException {
        assertNotNull(TallerDAO.class.getMethod("guardar", String.class, List.class, int.class, String.class,
                LocalDateTime.class).getAnnotation(Transactional.class));
    }

    @Test void elCierreYLaSustitucionSoloTocanUnVigente() {
        when(jdbc.update(TallerDAO.SQL_CERRAR, "MANDA_NSYS", Timestamp.valueOf(HOY), "Verde", 5)).thenReturn(1);
        assertEquals(1, dao.cerrar(5, CambioTaller.Estado.MANDA_NSYS, "Verde", HOY));
        assertTrue(TallerDAO.SQL_CERRAR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'"), TallerDAO.SQL_CERRAR);
        assertTrue(TallerDAO.SQL_SUSTITUIR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'"), TallerDAO.SQL_SUSTITUIR);
        assertTrue(TallerDAO.SQL_BLOQUEAR.endsWith(" FOR UPDATE"), TallerDAO.SQL_BLOQUEAR);
    }
}
```

- [ ] **Step 5: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='TallerDAOTest'`
Expected: error de compilación: no existen `TallerDAO`, `CambioTaller` ni `DatoTaller`.

- [ ] **Step 6: Implementación**

Crea `DatoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

/** Los datos de un móvil que el taller cambia por encima de NSYS (spec 0.9.21 «SKU del taller» §1): el color (el del
 *  maestro, como Nsys_movil.COLOR) y el grado (como lo escribe NSYS en Nsys_movil.GRADE: lleva dentro la batería nueva,
 *  «100», y el mensaje de pieza, «Ms»). */
public enum DatoTaller {
    COLOR, GRADO;

    /** Lo que dice NSYS de este dato en una fila del espejo. */
    public String deNsys(MovilNsys m) {
        return this == COLOR ? m.color() : m.grade();
    }
}
```

Crea `CambioTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_cambio (spec 0.9.21 §4): un cambio del taller a un dato de un móvil, por IMEI y no por lote.
 *  {@code valorAntes}: lo que valía antes del cambio (el VALOR del cambio al que sustituye o, si no sustituye a ninguno, lo
 *  de NSYS); se deduce al leer, no se guarda. {@code usuario}: el nombre de quien lo hizo (null si ya no existe).
 *  {@code valorCierre}: lo que NSYS tenía al cerrarse solo (PASADO y MANDA_NSYS). {@code idAnterior}: el cambio al que
 *  sustituye. Horas de Madrid, como el espejo. */
public record CambioTaller(int id, String imeiSn, DatoTaller dato, String valor, String valorAntes, String valorNsys,
                           String origen, String idRep, Integer idUsu, String usuario, String motivo,
                           LocalDateTime fecha, Estado estado, LocalDateTime cerradoEn, String valorCierre,
                           Integer idAnterior) {

    /** VIGENTE: manda sobre NSYS (como mucho uno por IMEI y dato). SUSTITUIDO: lo tapó otro cambio del mismo dato.
     *  QUITADO: un cambio a mano que vuelve a lo de NSYS («Volver a lo de NSYS» o elegir lo que dice NSYS); nace cerrado.
     *  PASADO: NSYS lo alcanzó. MANDA_NSYS: NSYS puso después otra cosa en ese dato. */
    public enum Estado { VIGENTE, SUSTITUIDO, QUITADO, PASADO, MANDA_NSYS }

    /** Origen de los cambios de esta pieza («Corregir…» y «Volver a lo de NSYS»); REPARACION es de la pieza 2. */
    public static final String MANO = "MANO";
}
```

Crea `TallerDAO.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Acceso a Taller_cambio (spec 0.9.21 §4): lo del taller por encima de NSYS. Las horas son de Madrid, como las del espejo:
 *  las pone el servidor, nunca la base de datos. */
@Repository
public class TallerDAO {

    static final String COLUMNAS = "c.ID_CAMBIO, c.IMEI_SN, c.DATO, c.VALOR,"
            + " CASE WHEN a.ID_CAMBIO IS NULL THEN c.VALOR_NSYS ELSE a.VALOR END AS VALOR_ANTES, c.VALOR_NSYS, c.ORIGEN,"
            + " c.ID_REP, c.ID_USU, u.NOMBRE_USUARIO, c.MOTIVO, c.FECHA, c.ESTADO, c.CERRADO_EN, c.VALOR_CIERRE,"
            + " c.ID_CAMBIO_ANTERIOR";
    static final String DESDE = " FROM Taller_cambio c LEFT JOIN Taller_cambio a ON a.ID_CAMBIO = c.ID_CAMBIO_ANTERIOR"
            + " LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU";
    static final String ORDEN = " ORDER BY c.FECHA, c.ID_CAMBIO";
    /** El candado del móvil: con dos correcciones a la vez del mismo IMEI, la segunda espera a la primera y la última gana
     *  sin dejar dos VIGENTE del mismo dato (spec §3). */
    static final String SQL_BLOQUEAR = "SELECT IMEI_SN FROM Nsys_movil WHERE IMEI_SN = ? FOR UPDATE";
    static final String SQL_VIGENTE = "SELECT ID_CAMBIO FROM Taller_cambio WHERE IMEI_SN = ? AND DATO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_SUSTITUIR = "UPDATE Taller_cambio SET ESTADO = 'SUSTITUIDO', CERRADO_EN = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_INSERTAR = "INSERT INTO Taller_cambio (IMEI_SN, DATO, VALOR, VALOR_NSYS, ORIGEN, ID_USU, MOTIVO,"
            + " FECHA, ESTADO, CERRADO_EN, ID_CAMBIO_ANTERIOR) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Taller_cambio SET ESTADO = ?, CERRADO_EN = ?, VALOR_CIERRE = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";

    private final JdbcTemplate jdbc;

    public TallerDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los cambios VIGENTES de todo el taller, del más antiguo al más reciente. Son unas decenas. */
    public List<CambioTaller> vigentes() {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE'" + ORDEN, TallerDAO::cambio);
    }

    /** Los VIGENTES de estos IMEI, en tandas de {@value NsysDAO#TANDA_IN}, del más antiguo al más reciente en cada tanda. */
    public List<CambioTaller> vigentes(Collection<String> imeis) {
        List<CambioTaller> cambios = new ArrayList<>();
        for (List<String> tanda : NsysDAO.trozos(List.copyOf(imeis), NsysDAO.TANDA_IN))
            cambios.addAll(jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE' AND c.IMEI_SN IN ("
                    + NsysDAO.marcas(tanda.size()) + ")" + ORDEN, TallerDAO::cambio, tanda.toArray()));
        return cambios;
    }

    /** Todos los cambios de un IMEI, de cualquier estado, para el rastro. */
    public List<CambioTaller> deImei(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.IMEI_SN = ?" + ORDEN, TallerDAO::cambio, imeiSn);
    }

    /** Un cambio que entra: VIGENTE, o QUITADO si vuelve a lo de NSYS. */
    public record Nuevo(DatoTaller dato, String valor, String valorNsys, CambioTaller.Estado estado) {}

    /** Guarda unos cambios a mano de un móvil, todo o nada: el VIGENTE anterior de cada dato, si lo hay, pasa a SUSTITUIDO
     *  y el nuevo apunta a él (ID_CAMBIO_ANTERIOR, que la pieza 2 usará para deshacer). Un QUITADO nace cerrado. */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, int idUsu, String motivo, LocalDateTime ahora) {
        jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn);
        Timestamp t = Timestamp.valueOf(ahora);
        for (Nuevo n : nuevos) {
            List<Integer> vigentes = jdbc.queryForList(SQL_VIGENTE, Integer.class, imeiSn, n.dato().name());
            for (Integer id : vigentes) jdbc.update(SQL_SUSTITUIR, t, id);
            Integer anterior = vigentes.stream().max(Integer::compare).orElse(null);
            boolean cerrado = n.estado() != CambioTaller.Estado.VIGENTE;
            jdbc.update(SQL_INSERTAR, imeiSn, n.dato().name(), n.valor(), n.valorNsys(), CambioTaller.MANO, idUsu, motivo, t,
                    n.estado().name(), cerrado ? t : null, anterior);
        }
    }

    /** El cierre automático de un VIGENTE (PASADO o MANDA_NSYS). 0 si ya no lo era: lo sustituyó una corrección a la vez. */
    public int cerrar(int idCambio, CambioTaller.Estado estado, String valorCierre, LocalDateTime ahora) {
        return jdbc.update(SQL_CERRAR, estado.name(), Timestamp.valueOf(ahora), valorCierre, idCambio);
    }

    static CambioTaller cambio(ResultSet rs, int n) throws SQLException {
        return new CambioTaller(rs.getInt("ID_CAMBIO"), rs.getString("IMEI_SN"), DatoTaller.valueOf(rs.getString("DATO")),
                rs.getString("VALOR"), rs.getString("VALOR_ANTES"), rs.getString("VALOR_NSYS"), rs.getString("ORIGEN"),
                rs.getString("ID_REP"), entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getString("MOTIVO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), CambioTaller.Estado.valueOf(rs.getString("ESTADO")),
                fechaHora(rs.getTimestamp("CERRADO_EN")), rs.getString("VALOR_CIERRE"), entero(rs, "ID_CAMBIO_ANTERIOR"));
    }

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static LocalDateTime fechaHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
```

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='TallerDAOTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. `OpenApiContractTest` arranca el contexto con el `@Repository` nuevo.

- [ ] **Step 8: Commit**

```bash
git add sql/migracion-sku-taller.sql sql/crear_bd.sql sql/reset-uat.sql docs/schema.md \
  src/main/java/com/reparaciones/servidor/nsys/DatoTaller.java src/main/java/com/reparaciones/servidor/nsys/CambioTaller.java \
  src/main/java/com/reparaciones/servidor/nsys/TallerDAO.java src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java \
  src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java
git commit -m "feat: tabla Taller_cambio y su acceso para el SKU del taller (0.9.21)"
```

---

### Task 2: Servidor — el maestro visto desde un móvil y el producto de cada color

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Normalizacion.java` (`mismoTexto`)
- Create: `…/nsys/Maestro.java`, `…/nsys/SkuMaestro.java`
- Modify: `…/nsys/NsysDAO.java` (`SQL_GOODS_POR_COLOR`, `GoodsDeColor`, `goodsMasUsados`, `primeroDeCadaColor`)
- Test: `…/nsys/NormalizacionTest.java`, `…/nsys/MaestroTest.java` (nuevo), `…/nsys/NsysDAOTest.java`

**Interfaces:**
- Consumes: `TallerDePrueba` (Task 1), `ProductoMaestro`, `MovilNsys`.
- Produces:
  - `public static boolean Normalizacion.mismoTexto(String a, String b)`: iguales sin espacios a los lados; null = "".
  - `public record SkuMaestro(String color, String grado, String sku)` (esquema `SkuMaestro`).
  - `public final class Maestro` con `Maestro(List<ProductoMaestro>)`, `String sku(MovilNsys m, String color, String grado)`,
    `List<String> colores(MovilNsys m)` y `List<SkuMaestro> skus(MovilNsys m)`.
  - En `NsysDAO`: `static final String SQL_GOODS_POR_COLOR`, `record GoodsDeColor(String color, String goods)`,
    `public Map<String, String> goodsMasUsados(MovilNsys m)` (color → Goods más usado) y
    `static Map<String, String> primeroDeCadaColor(List<GoodsDeColor>)`.

- [ ] **Step 1: Tests (fallan)**

En `NormalizacionTest.java`, añade al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21: SKU del taller

    @Test void mismoTextoSinEspaciosALosLadosYNullComoVacio() {
        assertTrue(Normalizacion.mismoTexto(" A100 ", "A100"));
        assertTrue(Normalizacion.mismoTexto(null, ""));
        assertFalse(Normalizacion.mismoTexto("A100", "a100"), "el grado distingue mayúsculas, como en NSYS");
        assertFalse(Normalizacion.mismoTexto("Azul", null));
    }
```

Crea `MaestroTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** El maestro visto desde un móvil (spec 0.9.21 §2.1 y §2.3): SKU por modelo, capacidad, eSIM, color y grado. */
class MaestroTest {

    private static final MovilNsys AZUL = movil("350000000000001", "Azul", "B");

    @Test void conUnaFilaSuSku() {
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "A100"), producto("(PRODUCT)RED", "Rojo", "A100")));
        assertEquals("i13128RojoA100", maestro.sku(AZUL, "Rojo", "A100"));
        assertEquals("i13128AzulA100", maestro.sku(AZUL, " Azul ", "A100"), "sin espacios a los lados");
    }

    @Test void sinNingunaFilaNoHaySku() {
        assertNull(new Maestro(List.of(producto("Blue", "Azul", "A100"))).sku(AZUL, "Verde", "B"));
    }

    @Test void variasFilasConSkuDistintoNoDanSkuYConElMismoSi() {
        ProductoMaestro otro = new ProductoMaestro("otraclave", "13", "13", "128GB", "Azul", "B", "i13128AzulB-otro", null,
                null, false);
        ProductoMaestro igual = new ProductoMaestro("otraclave", "13", "13", "128GB", "Azul", "B", "i13128AzulB", null, null,
                false);
        assertNull(new Maestro(List.of(producto("Blue", "Azul", "B"), otro)).sku(AZUL, "Azul", "B"));
        assertEquals("i13128AzulB", new Maestro(List.of(producto("Blue", "Azul", "B"), igual)).sku(AZUL, "Azul", "B"));
    }

    @Test void unaFilaSinSkuNoCuenta() {
        ProductoMaestro conflicto = new ProductoMaestro("conflicto", "13", "13", "128GB", "Azul", "B", null, null, null, false);
        assertNull(new Maestro(List.of(conflicto)).sku(AZUL, "Azul", "B"), "los conflictos del maestro vienen sin SKU");
        assertEquals("i13128AzulB", new Maestro(List.of(conflicto, producto("Blue", "Azul", "B"))).sku(AZUL, "Azul", "B"));
    }

    @Test void sinModeloEnElMaestroNiSkuNiColores() {
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "B")));
        MovilNsys sin = sinMaestro("350000000000001", "B");
        assertNull(maestro.sku(sin, "Azul", "B"));
        assertTrue(maestro.colores(sin).isEmpty());
        assertTrue(maestro.skus(sin).isEmpty());
    }

    @Test void otroModeloCapacidadOEsimNoCuentan() {
        ProductoMaestro ipad = new ProductoMaestro("ipad", null, "13", "128GB", "Verde", "B", "ipad13VerdeB", null, null, false);
        ProductoMaestro grande = new ProductoMaestro("256", "13", "13", "256GB", "Verde", "B", "i13256VerdeB", null, null, false);
        ProductoMaestro esim = new ProductoMaestro("esim", "13", "13", "128GB", "Verde", "B", "i13128VerdeBe", null, null, true);
        Maestro maestro = new Maestro(List.of(ipad, grande, esim));
        assertNull(maestro.sku(AZUL, "Verde", "B"), "un iPad con el mismo modelo del maestro no es un iPhone");
        assertTrue(maestro.colores(AZUL).isEmpty());
    }

    @Test void losColoresDelModeloSinRepetirYPorOrden() {
        Maestro maestro = new Maestro(List.of(producto("(PRODUCT)RED", "Rojo", "A100"), producto("Blue", "Azul", "B"),
                producto("Blue", "Azul", "A100"), producto("Midnight", "Medianoche", "B")));
        assertEquals(List.of("Azul", "Medianoche", "Rojo"), maestro.colores(AZUL));
    }

    @Test void losSkuPorColorYGradoParaLaVistaPrevia() {
        ProductoMaestro otro = new ProductoMaestro("otraclave", "13", "13", "128GB", "Rojo", "B", "i13128RojoB-otro", null,
                null, false);
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "A100"), producto("(PRODUCT)RED", "Rojo", "B"), otro));
        assertEquals(List.of(new SkuMaestro("Azul", "A100", "i13128AzulA100")), maestro.skus(AZUL),
                "Rojo B tiene dos SKU distintos: no sale");
    }
}
```

En `NsysDAOTest.java`, añade al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21: el producto de cada color (spec §2.4)

    @SuppressWarnings("unchecked")
    @Test void elGoodsMasUsadoDeCadaColorDelModeloEnUnaConsulta() {
        assertTrue(dao.goodsMasUsados(movil(1)).isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_GOODS_POR_COLOR), any(RowMapper.class), eq("13"), eq("13"), eq("128GB"), eq(false));
        assertTrue(NsysDAO.SQL_GOODS_POR_COLOR.contains("WHERE MODELO <=> ? AND MODELO_MAESTRO = ? AND CAPACIDAD <=> ? AND ES_ESIM = ?"),
                NsysDAO.SQL_GOODS_POR_COLOR);
        assertTrue(NsysDAO.SQL_GOODS_POR_COLOR.endsWith(" GROUP BY COLOR, GOODS ORDER BY COLOR, N DESC, GOODS"),
                NsysDAO.SQL_GOODS_POR_COLOR);
    }

    @Test void deCadaColorValeElPrimeroQueLlega() {
        assertEquals(Map.of("Azul", "Apple iPhone 13 128GB Blue", "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"),
                NsysDAO.primeroDeCadaColor(List.of(new NsysDAO.GoodsDeColor("Azul", "Apple iPhone 13 128GB Blue"),
                        new NsysDAO.GoodsDeColor("Azul", "Apple iPhone 13 128GB Azul"),
                        new NsysDAO.GoodsDeColor("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"))));
    }

    @Test void sinModeloEnElMaestroNoSeBuscaProducto() {
        assertTrue(dao.goodsMasUsados(TallerDePrueba.sinMaestro("350000000000001", "B")).isEmpty());
        verifyNoInteractions(jdbc);
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='NormalizacionTest,MaestroTest,NsysDAOTest'`
Expected: error de compilación: no existen `Normalizacion.mismoTexto`, `Maestro`, `SkuMaestro` ni `NsysDAO.goodsMasUsados`.

- [ ] **Step 3: Implementación**

En `Normalizacion.java`, añade al final de la clase (antes de la `}` final):

```java

    /** Dos textos de NSYS o del maestro iguales sin espacios a los lados; null y vacío cuentan igual. Distingue mayúsculas,
     *  como NSYS (0.9.21, SKU del taller: grados, colores y SKU). */
    public static boolean mismoTexto(String a, String b) {
        return (a == null ? "" : a.strip()).equals(b == null ? "" : b.strip());
    }
```

Crea `SkuMaestro.java`:

```java
package com.reparaciones.servidor.nsys;

/** Un SKU del maestro para un color y un grado del modelo de un móvil (spec 0.9.21 §2.3): la vista previa del diálogo
 *  «Corregir…». Solo salen las combinaciones con un SKU. */
public record SkuMaestro(String color, String grado, String sku) {}
```

Crea `Maestro.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** El maestro de productos visto desde un móvil (spec 0.9.21 «SKU del taller» §2.1 y §2.3): su SKU con un color y un grado,
 *  los colores de su modelo y los SKU de cada color y grado. Se busca con los datos del espejo: MODELO (el código del ERP,
 *  null fuera del catálogo: así un iPad 11 no pasa por un iPhone 11, como en LectorMaestro), MODELO_MAESTRO, CAPACIDAD y
 *  ES_ESIM. Sin MODELO_MAESTRO el móvil no tiene modelo en el maestro: ni SKU ni colores. Es puro. */
public final class Maestro {

    private final List<ProductoMaestro> productos;

    public Maestro(List<ProductoMaestro> productos) {
        this.productos = List.copyOf(productos);
    }

    /** El SKU del maestro para el modelo del móvil con este color y este grado (CALIDAD_DETALLE = el grado de NSYS): si las
     *  filas que casan tienen un solo SKU_WEB, ese; si no hay ninguna o tienen SKU distintos (los conflictos del maestro ya
     *  vienen sin SKU), null: «sin SKU en el maestro». */
    public String sku(MovilNsys m, String color, String grado) {
        List<String> skus = delModelo(m)
                .filter(p -> Normalizacion.mismoTexto(p.color(), color) && Normalizacion.mismoTexto(p.calidadDetalle(), grado))
                .map(ProductoMaestro::skuWeb).filter(Objects::nonNull).distinct().toList();
        return skus.size() == 1 ? skus.get(0) : null;
    }

    /** Los colores del maestro para el modelo del móvil, sin repetir y por orden alfabético. */
    public List<String> colores(MovilNsys m) {
        return delModelo(m).map(ProductoMaestro::color).filter(c -> c != null && !c.isBlank()).map(String::strip).distinct()
                .sorted(Comparator.comparing((String c) -> c.toLowerCase(Locale.ROOT))).toList();
    }

    /** Los SKU del modelo por color y grado, solo los que tienen uno (las mismas reglas que {@link #sku}). */
    public List<SkuMaestro> skus(MovilNsys m) {
        Map<List<String>, Set<String>> porCombinacion = new LinkedHashMap<>();
        delModelo(m).filter(p -> p.color() != null && p.calidadDetalle() != null).forEach(p -> {
            Set<String> skus = porCombinacion.computeIfAbsent(List.of(p.color().strip(), p.calidadDetalle().strip()),
                    k -> new LinkedHashSet<>());
            if (p.skuWeb() != null) skus.add(p.skuWeb());
        });
        return porCombinacion.entrySet().stream().filter(e -> e.getValue().size() == 1)
                .map(e -> new SkuMaestro(e.getKey().get(0), e.getKey().get(1), e.getValue().iterator().next())).toList();
    }

    private Stream<ProductoMaestro> delModelo(MovilNsys m) {
        if (m.modeloMaestro() == null) return Stream.empty();
        return productos.stream().filter(p -> Objects.equals(p.modelo(), m.modelo())
                && Objects.equals(p.modeloMaestro(), m.modeloMaestro()) && Objects.equals(p.capacidad(), m.capacidad())
                && p.esEsim() == m.esEsim());
    }
}
```

En `NsysDAO.java`:

1. Import: añade `java.util.LinkedHashMap`.
2. Tras la constante `TANDA_IN`, añade:

```java
    /** El «Goods» de los móviles del espejo de un modelo, capacidad y eSIM, por color y de más usado a menos (spec 0.9.21
     *  §2.4): el producto que hay que poner en NSYS al cambiar el color. `<=>`: MODELO y CAPACIDAD pueden ser NULL. */
    static final String SQL_GOODS_POR_COLOR = "SELECT COLOR, GOODS, COUNT(*) AS N FROM Nsys_movil"
            + " WHERE MODELO <=> ? AND MODELO_MAESTRO = ? AND CAPACIDAD <=> ? AND ES_ESIM = ? AND COLOR IS NOT NULL"
            + " AND GOODS IS NOT NULL GROUP BY COLOR, GOODS ORDER BY COLOR, N DESC, GOODS";
```

3. Tras el método `claveCruce`, añade:

```java

    /** Una fila de {@link #SQL_GOODS_POR_COLOR}. */
    record GoodsDeColor(String color, String goods) {}

    /** El «Goods» más usado de cada color (el del maestro) entre los móviles del espejo del mismo modelo, capacidad y eSIM que
     *  {@code m}; un color sin ningún móvil no sale. Sin modelo en el maestro no se pregunta. A igual número, el primero por
     *  orden alfabético. */
    public Map<String, String> goodsMasUsados(MovilNsys m) {
        if (m.modeloMaestro() == null) return Map.of();
        return primeroDeCadaColor(jdbc.query(SQL_GOODS_POR_COLOR, (rs, n) -> new GoodsDeColor(rs.getString("COLOR"),
                rs.getString("GOODS")), m.modelo(), m.modeloMaestro(), m.capacidad(), m.esEsim()));
    }

    /** Las filas llegan por color y, dentro de cada uno, de la más usada a la menos: vale la primera. */
    static Map<String, String> primeroDeCadaColor(List<GoodsDeColor> filas) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (GoodsDeColor f : filas) mapa.putIfAbsent(f.color(), f.goods());
        return mapa;
    }
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='NormalizacionTest,MaestroTest,NsysDAOTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/Normalizacion.java src/main/java/com/reparaciones/servidor/nsys/Maestro.java \
  src/main/java/com/reparaciones/servidor/nsys/SkuMaestro.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java \
  src/test/java/com/reparaciones/servidor/nsys/NormalizacionTest.java src/test/java/com/reparaciones/servidor/nsys/MaestroTest.java \
  src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: SKU y colores del maestro por modelo y el producto mas usado de cada color (0.9.21)"
```

---

### Task 3: Servidor — reglas puras «lo que vale» y cierre

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/LoQueVale.java`, `CierreTaller.java`
- Create (test): `…/nsys/LoQueValeTest.java`, `…/nsys/CierreTallerTest.java`

**Interfaces:**
- Consumes: `Maestro.sku(MovilNsys, String, String)` y `Normalizacion.mismoTexto` (Task 2); `CambioTaller`, `DatoTaller` y
  `TallerDePrueba` (Task 1).
- Produces:
  - `LoQueVale`:
    - `public static final String SIN_DATO = "—"`;
    - `public record Valor(String color, String grade, String sku, String colorNsys, String gradoNsys, String skuNsys,
      boolean porPasar)` con `String grado()` (sin el 100) y `boolean bateriaNueva()`;
    - `public static Valor de(MovilNsys m, List<CambioTaller> vigentes, Supplier<Maestro> maestro)`;
    - `static String valor(List<CambioTaller> vigentes, DatoTaller dato, MovilNsys m)` y `static String siCambia(String taller, String nsys)`.
  - `CierreTaller`: `public enum Decision { SIGUE, PASADO, MANDA_NSYS }` y
    `public static Decision decidir(CambioTaller c, String nsysAhora)`.

- [ ] **Step 1: Tests (fallan)**

Crea `LoQueValeTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Lo que vale de un móvil (spec 0.9.21 §2.1): lo del taller vigente por encima de NSYS, dato a dato. */
class LoQueValeTest {

    private static final String IMEI = "350000000000001";
    private static final MovilNsys AZUL_B = movil(IMEI, "Azul", "B");
    private static final Maestro MAESTRO = new Maestro(List.of(producto("Blue", "Azul", "B"), producto("Blue", "Azul", "A100"),
            producto("(PRODUCT)RED", "Rojo", "B"), producto("(PRODUCT)RED", "Rojo", "A100")));

    @Test void sinNadaDelTallerTodoComoHoyYSinLeerElMaestro() {
        assertEquals(new LoQueVale.Valor("Azul", "B", "i13128AzulB", null, null, null, false),
                LoQueVale.de(AZUL_B, List.of(), () -> fail("sin nada del taller no se lee el maestro")));
    }

    @Test void conElColorDelTaller() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")), () -> MAESTRO);
        assertEquals(new LoQueVale.Valor("Rojo", "B", "i13128RojoB", "Azul", null, "i13128AzulB", true), v);
    }

    @Test void conElGradoDelTallerQueLlevaLaBateria() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals("A100", v.grade());
        assertEquals("A", v.grado());
        assertTrue(v.bateriaNueva());
        assertEquals("B", v.gradoNsys());
        assertNull(v.colorNsys());
        assertEquals("i13128AzulA100", v.sku());
    }

    @Test void conLosDos() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul"),
                vigente(2, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals(new LoQueVale.Valor("Rojo", "A100", "i13128RojoA100", "Azul", "B", "i13128AzulB", true), v);
    }

    @Test void sinSkuEnElMaestroSeQuedaSinSku() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "C", "B")), () -> MAESTRO);
        assertNull(v.sku());
        assertEquals("i13128AzulB", v.skuNsys());
    }

    @Test void loQueNsysNoSabeSaleConUnaRaya() {
        MovilNsys sinSku = new MovilNsys(IMEI, "BT-9001", "Apple iPhone 13 128GB Blue", "B", "Available", "CAJA LISTOS",
                "Proveedor A", null, null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        LoQueVale.Valor v = LoQueVale.de(sinSku, List.of(vigente(1, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals("i13128AzulA100", v.sku());
        assertEquals("—", v.skuNsys());
    }

    @Test void unCambioIgualALoDeNsysNoLlevaMarcaPeroSigueEnLaLista() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "B", "C")), () -> MAESTRO);
        assertNull(v.gradoNsys());
        assertNull(v.skuNsys());
        assertTrue(v.porPasar(), "hasta que el cierre lo vea PASADO");
    }
}
```

Crea `CierreTallerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static com.reparaciones.servidor.nsys.CierreTaller.Decision.*;
import static com.reparaciones.servidor.nsys.CierreTaller.decidir;
import static com.reparaciones.servidor.nsys.TallerDePrueba.vigente;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** El cierre tras una importación (spec 0.9.21 §2.2): un cambio del taller de B a A100. */
class CierreTallerTest {

    private static final CambioTaller A100 = vigente(1, "350000000000001", DatoTaller.GRADO, "A100", "B");

    @Test void nsysYaTieneLoDelTallerEsPasado() {
        assertEquals(PASADO, decidir(A100, "A100"));
        assertEquals(PASADO, decidir(A100, " A100 "), "sin espacios a los lados");
    }

    @Test void nsysCambiaDespuesAOtraCosaMandaNsys() {
        assertEquals(MANDA_NSYS, decidir(A100, "B-"));
    }

    @Test void nsysComoAntesSigue() {
        assertEquals(SIGUE, decidir(A100, "B"));
    }

    @Test void nsysSinElDatoSigue() {
        assertEquals(SIGUE, decidir(A100, null));
        assertEquals(SIGUE, decidir(A100, "  "));
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='LoQueValeTest,CierreTallerTest'`
Expected: error de compilación: no existen `LoQueVale` ni `CierreTaller`.

- [ ] **Step 3: Implementación**

Crea `LoQueVale.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.function.Supplier;

/** «Lo que vale» de un móvil (spec 0.9.21 «SKU del taller» §2.1): lo del taller vigente para cada dato, si lo hay; si no, lo
 *  de NSYS. Con algo del taller, el SKU sale del maestro con el modelo, la capacidad y la eSIM del espejo y el color y el
 *  grado que valen; sin nada, todo como hoy (el SKU, el de Nsys_movil). Es puro. */
public final class LoQueVale {

    /** Lo de NSYS cuando NSYS no lo sabe («NSYS: —» en la web). */
    public static final String SIN_DATO = "—";

    /** {@code grade}: el grado entero, como lo escribe NSYS (con el «100» y el «Ms»). {@code colorNsys}, {@code gradoNsys} y
     *  {@code skuNsys}: lo de NSYS cuando lo del taller lo cambia; null si es lo mismo. {@code porPasar}: el móvil tiene algo
     *  del taller vigente (sale en «Por pasar a NSYS»). */
    public record Valor(String color, String grade, String sku, String colorNsys, String gradoNsys, String skuNsys,
                        boolean porPasar) {

        /** El grado sin la batería, como lo enseña la web («A100» → «A»). */
        public String grado() {
            return Normalizacion.gradoSinBateria(grade);
        }

        public boolean bateriaNueva() {
            return Normalizacion.bateriaNueva(grade);
        }
    }

    private LoQueVale() {}

    /** {@code vigentes}: los cambios VIGENTES de ese IMEI (como mucho uno por dato). {@code maestro}: solo se pide si hay
     *  alguno. */
    public static Valor de(MovilNsys m, List<CambioTaller> vigentes, Supplier<Maestro> maestro) {
        if (vigentes.isEmpty()) return new Valor(m.color(), m.grade(), m.sku(), null, null, null, false);
        String color = valor(vigentes, DatoTaller.COLOR, m);
        String grade = valor(vigentes, DatoTaller.GRADO, m);
        String sku = maestro.get().sku(m, color, grade);
        return new Valor(color, grade, sku, siCambia(color, m.color()), siCambia(grade, m.grade()), siCambia(sku, m.sku()),
                true);
    }

    /** Lo que vale de un dato: lo del taller vigente o, si no hay, lo de NSYS. */
    static String valor(List<CambioTaller> vigentes, DatoTaller dato, MovilNsys m) {
        return vigentes.stream().filter(c -> c.dato() == dato && c.valor() != null).map(CambioTaller::valor).findFirst()
                .orElse(dato.deNsys(m));
    }

    /** Lo de NSYS si lo del taller lo cambia (una raya si NSYS no lo sabe); null si es lo mismo. */
    static String siCambia(String taller, String nsys) {
        if (Normalizacion.mismoTexto(taller, nsys)) return null;
        return nsys == null || nsys.isBlank() ? SIN_DATO : nsys;
    }
}
```

Crea `CierreTaller.java`:

```java
package com.reparaciones.servidor.nsys;

/** El cierre automático de un cambio del taller tras una importación (spec 0.9.21 §2.2), dato a dato: con lo que NSYS dice
 *  ahora (la fila más reciente del IMEI en el espejo) y lo que decía al hacer el cambio. Es puro. */
public final class CierreTaller {

    public enum Decision { SIGUE, PASADO, MANDA_NSYS }

    private CierreTaller() {}

    /** NSYS ahora = lo del taller: PASADO. NSYS ahora distinto de lo que tenía al hacer el cambio: MANDA_NSYS (alguien lo
     *  cambió después a otra cosa: manda lo más reciente). Si no, SIGUE. Si NSYS ahora no sabe el dato (un producto que el
     *  maestro no traduce deja el color vacío), no se sabe si coincide: SIGUE. */
    public static Decision decidir(CambioTaller c, String nsysAhora) {
        if (nsysAhora == null || nsysAhora.isBlank()) return Decision.SIGUE;
        if (Normalizacion.mismoTexto(nsysAhora, c.valor())) return Decision.PASADO;
        if (!Normalizacion.mismoTexto(nsysAhora, c.valorNsys())) return Decision.MANDA_NSYS;
        return Decision.SIGUE;
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='LoQueValeTest,CierreTallerTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/LoQueVale.java src/main/java/com/reparaciones/servidor/nsys/CierreTaller.java \
  src/test/java/com/reparaciones/servidor/nsys/LoQueValeTest.java src/test/java/com/reparaciones/servidor/nsys/CierreTallerTest.java
git commit -m "feat: reglas de lo que vale del taller sobre NSYS y de su cierre automatico (0.9.21)"
```

---

### Task 4: Servidor — servicio del taller: corrección, vuelta, cierre y su llamada tras la importación

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java`
- Modify: `…/nsys/ImportadorNsys.java` (constructores, `procesar` y `cerrarTaller`)
- Create (test): `…/nsys/TallerNsysTest.java`
- Modify (test): `…/nsys/ImportadorNsysTest.java`

**Interfaces:**
- Consumes: `TallerDAO` (Task 1), `Maestro` y `NsysDAO.goodsMasUsados` (Task 2), `LoQueVale` y `CierreTaller` (Task 3),
  `LogDAO.insertar(int idUsu, String accion, String detalle, String motivo)`, `NsysDAO.porImei`, `porImeis`,
  `cargarProductos`, `claveCruce`.
- Produces (`TallerNsys`, `@Service`):
  - `public TallerNsys(NsysDAO, TallerDAO, LogDAO)` (Spring) y `TallerNsys(NsysDAO, TallerDAO, LogDAO, Clock)` (tests);
  - `Function<MovilNsys, LoQueVale.Valor> valedor(Collection<String> imeis)` y `valedorDeTodos()`;
  - `String productoAPoner(MovilNsys m, LoQueVale.Valor v)` (null = «búscalo a mano»);
  - `List<CambioTaller> cambios(String imeiSn)`;
  - `void corregir(String imeiSn, String color, String grado, String motivo, int idUsu)` y
    `void volver(String imeiSn, String motivo, int idUsu)` (lanzan `ResponseStatusException` 400/404);
  - `int cerrar()`;
  - de paquete: `GRADO` (Pattern), `MAX_MOTIVO = 500`, `ACCION = "CORREGIR_MOVIL_TALLER"`,
    `static Map<String, List<CambioTaller>> porImei(List<CambioTaller>)` y los privados `masRecientes`, `perezoso` y `ahora`
    (la Task 6 los usa).
  - `ImportadorNsys`: constructores `(ClienteDrive, NsysDAO, TallerNsys, String carpeta, String maestro)` y
    `(…, Clock)`; tras cada Items ACTUAL en `OK`, `taller.cerrar()` (un fallo no cambia el `OK`).

- [ ] **Step 1: Tests del servicio (fallan)**

Crea `TallerNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El SKU del taller con los DAO mockeados y la hora fija (21/10/2026 10:30 en Madrid). */
class TallerNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 30);
    private static final String IMEI = "350000000000001";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO taller = mock(TallerDAO.class);
    private final LogDAO registro = mock(LogDAO.class);
    private final TallerNsys servicio = new TallerNsys(nsys, taller, registro,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("Blue", "Azul", "A100"),
                producto("(PRODUCT)RED", "Rojo", "B"), producto("(PRODUCT)RED", "Rojo", "A100")));
    }

    private static HttpStatusCode estado(Executable accion) {
        return assertThrows(ResponseStatusException.class, accion).getStatusCode();
    }

    // ---------------------------------------------------------------- lo que vale (spec §2.1)

    @Test void elValedorAplicaLoDelTallerSoloASuImeiYLeeElMaestroUnaVez() {
        String otro = "350000000000002";
        when(taller.vigentes(List.of(IMEI, otro))).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B")));
        Function<MovilNsys, LoQueVale.Valor> vale = servicio.valedor(List.of(IMEI, otro));
        assertEquals("A100", vale.apply(movil(IMEI, "Azul", "B")).grade());
        assertEquals("i13128AzulA100", vale.apply(movil(IMEI, "Azul", "B")).sku());
        assertEquals(new LoQueVale.Valor("Azul", "B", "i13128AzulB", null, null, null, false),
                vale.apply(movil(otro, "Azul", "B")));
        verify(nsys, times(1)).cargarProductos();
    }

    @Test void sinNadaDelTallerNoSeLeeElMaestro() {
        servicio.valedorDeTodos().apply(movil(IMEI, "Azul", "B"));
        verify(nsys, never()).cargarProductos();
    }

    @Test void unNumeroDeSerieEnMinusculasCruza() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, "f2lxabc123", GRADO, "A100", "B")));
        assertEquals("A100", servicio.valedorDeTodos().apply(movil("F2LXABC123", "Azul", "B")).grade());
    }

    @Test void elProductoAPonerEsElDeAhoraSiSoloCambiaElGradoYElMasUsadoSiCambiaElColor() {
        MovilNsys m = movil(IMEI, "Azul", "B");
        LoQueVale.Valor grado = new LoQueVale.Valor("Azul", "A100", "i13128AzulA100", null, "B", "i13128AzulB", true);
        assertEquals("Apple iPhone 13 128GB Blue", servicio.productoAPoner(m, grado));
        verify(nsys, never()).goodsMasUsados(any());
        when(nsys.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        LoQueVale.Valor rojo = new LoQueVale.Valor("Rojo", "B", "i13128RojoB", "Azul", null, "i13128AzulB", true);
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", servicio.productoAPoner(m, rojo));
        LoQueVale.Valor verde = new LoQueVale.Valor("Verde", "B", null, "Azul", null, "i13128AzulB", true);
        assertNull(servicio.productoAPoner(m, verde), "ningún móvil de ese color: la web dice «búscalo a mano»");
    }

    // ---------------------------------------------------------------- corrección a mano (spec §2.3)

    @Test void corregirElGradoLoDejaVigenteYLoApunta() {
        servicio.corregir(IMEI, null, "A100", "  batería cambiada ", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), 7, "batería cambiada", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: B → A100", "batería cambiada");
    }

    @Test void corregirColorYGradoApuntaUnaLineaPorDato() {
        servicio.corregir(IMEI, "Rojo", "A100", "chasis rojo nuevo", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), 7, "chasis rojo nuevo", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, COLOR: Azul → Rojo", "chasis rojo nuevo");
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: B → A100", "chasis rojo nuevo");
    }

    @Test void unaCorreccionSobreOtraDelTallerParteDeLoQueVale() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, GRADO, "A100", "B")));
        servicio.corregir(IMEI, null, "A", "era A sin batería", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), 7, "era A sin batería", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A100 → A", "era A sin batería");
    }

    @Test void elegirLoQueDiceNsysQuitaLoDelTaller() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, GRADO, "A100", "B")));
        servicio.corregir(IMEI, null, "B", "me equivoqué", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "B", "B", QUITADO)), 7, "me equivoqué", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER",
                "IMEI: 350000000000001, GRADO: A100 → B (vuelve a lo de NSYS)", "me equivoqué");
    }

    @Test void sinCambiosEs400YNoGuardaNada() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, "Azul", "B", "nada", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, null, "nada", 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(registro);
    }

    @Test void elMotivoEsObligatorioYNoMuyLargo() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", "   ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", "x".repeat(501), 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
    }

    @Test void elGradoSeEscribeComoEnNsys() {
        for (String malo : List.of("A+", "Z", "100", "a100", "MsMsA"))
            assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, malo, "x", 7)), malo);
        servicio.corregir(IMEI, null, "MsA-100", "mensaje de pieza", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "MsA-100", "B", VIGENTE)), 7, "mensaje de pieza", AHORA);
    }

    @Test void unColorQueNoEstaEnElMaestroParaEseModeloEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, "Verde", null, "x", 7)));
    }

    @Test void unImeiQueNoEstaEnElEspejoEs404() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.corregir("350000000000099", null, "A100", "x", 7)));
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.volver("350000000000099", null, 7)));
    }

    @Test void volverQuitaLosDosDatosConElMotivoSoloSiLoHay() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, COLOR, "Rojo", "Azul"),
                vigente(5, IMEI, GRADO, "A100", "B")));
        servicio.volver(IMEI, "  ", 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Azul", "Azul", QUITADO),
                new TallerDAO.Nuevo(GRADO, "B", "B", QUITADO)), 8, null, AHORA);
        verify(registro).insertar(8, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, COLOR: Rojo → Azul (vuelve a lo de NSYS)", null);
        verify(registro).insertar(8, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A100 → B (vuelve a lo de NSYS)", null);
    }

    @Test void volverSinNadaDelTallerEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.volver(IMEI, "x", 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
    }

    // ---------------------------------------------------------------- cierre tras la importación (spec §2.2)

    @Test void elCierrePasaLoQueNsysYaTieneYCedeLoQueNsysCambioDespues() {
        String otro = "350000000000002";
        String fuera = "350000000000003";
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B"), vigente(2, IMEI, COLOR, "Rojo", "Azul"),
                vigente(3, otro, GRADO, "C", "B"), vigente(4, fuera, GRADO, "A", "B")));
        // Del IMEI, la fila más reciente primero (la de A100): vale esa.
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "A100"), movil(IMEI, "Azul", "B"),
                movil(otro, "Azul", "B-")));
        when(taller.cerrar(anyInt(), any(), any(), any())).thenReturn(1);
        assertEquals(2, servicio.cerrar());
        verify(taller).cerrar(1, PASADO, "A100", AHORA);
        verify(taller).cerrar(3, MANDA_NSYS, "B-", AHORA);
        verify(taller, times(2)).cerrar(anyInt(), any(), any(), any());
    }

    @Test void sinNadaVigenteNoSeLeeElEspejo() {
        assertEquals(0, servicio.cerrar());
        verify(nsys, never()).porImeis(any());
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='TallerNsysTest'`
Expected: error de compilación: no existe `TallerNsys`.

- [ ] **Step 3: Implementación del servicio**

Crea `TallerNsys.java`:

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** El SKU del taller (spec 0.9.21, pieza 1): lo que vale de cada móvil, la corrección a mano, la vuelta a lo de NSYS y el
 *  cierre automático tras cada importación. El ERP no escribe en NSYS: solo guarda lo del taller por encima. */
@Service
public class TallerNsys {

    private static final Logger log = LoggerFactory.getLogger(TallerNsys.class);
    /** El grado como lo escribe NSYS (spec §2.3): [Ms]<letra>[100], con la letra A, A-, B o C. */
    static final Pattern GRADO = Pattern.compile("(Ms)?(A|A-|B|C)(100)?");
    static final int MAX_MOTIVO = 500;
    static final String ACCION = "CORREGIR_MOVIL_TALLER";

    private final NsysDAO nsysDao;
    private final TallerDAO tallerDao;
    private final LogDAO logDao;
    private final Clock clock;

    @Autowired
    public TallerNsys(NsysDAO nsysDao, TallerDAO tallerDao, LogDAO logDao) {
        this(nsysDao, tallerDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    TallerNsys(NsysDAO nsysDao, TallerDAO tallerDao, LogDAO logDao, Clock clock) {
        this.nsysDao = nsysDao;
        this.tallerDao = tallerDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- lo que vale (spec §2.1)

    /** Lo que vale de los móviles de estos IMEI: lee una vez sus cambios vigentes y, solo si hay alguno, el maestro. */
    public Function<MovilNsys, LoQueVale.Valor> valedor(Collection<String> imeis) {
        return valedor(tallerDao.vigentes(imeis));
    }

    /** Lo mismo con todos los cambios vigentes del taller (el stock entero). */
    public Function<MovilNsys, LoQueVale.Valor> valedorDeTodos() {
        return valedor(tallerDao.vigentes());
    }

    private Function<MovilNsys, LoQueVale.Valor> valedor(List<CambioTaller> vigentes) {
        Map<String, List<CambioTaller>> porImei = porImei(vigentes);
        Supplier<Maestro> maestro = perezoso(() -> new Maestro(nsysDao.cargarProductos()));
        return m -> LoQueVale.de(m, porImei.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()), maestro);
    }

    /** El producto («Goods») que hay que poner en NSYS (spec §2.4): si el color no cambia, el de ahora; si cambia, el más
     *  usado de ese color entre los móviles del mismo modelo, capacidad y eSIM; null si ninguno lo tiene (la web dice
     *  «búscalo a mano»). */
    public String productoAPoner(MovilNsys m, LoQueVale.Valor v) {
        return v.colorNsys() == null ? m.goods() : nsysDao.goodsMasUsados(m).get(v.color());
    }

    /** Todos los cambios del taller de un IMEI, para el rastro. */
    public List<CambioTaller> cambios(String imeiSn) {
        return tallerDao.deImei(imeiSn);
    }

    // ---------------------------------------------------------------- corrección a mano (spec §2.3)

    /** «Corregir color, grado o batería…». {@code color} o {@code grado} null: ese dato no cambia. Por cada dato que cambia
     *  respecto a lo que vale: si el nuevo es el de NSYS, se quita lo del taller (QUITADO); si no, queda VIGENTE. Una línea
     *  del registro de actividad por dato. */
    public void corregir(String imeiSn, String color, String grado, String motivo, int idUsu) {
        MovilNsys m = delEspejo(imeiSn);
        String porQue = motivo == null ? "" : motivo.strip();
        if (porQue.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        comprobarLargo(porQue);
        if (grado != null && !GRADO.matcher(grado).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El grado se escribe como en NSYS: A, A-, B o C, con Ms delante y 100 detrás si toca");
        if (color != null && !new Maestro(nsysDao.cargarProductos()).colores(m).contains(color))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El color " + color + " no está en el maestro para este modelo");
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        List<Paso> pasos = new ArrayList<>();
        paso(DatoTaller.COLOR, color, m, vigentes).ifPresent(pasos::add);
        paso(DatoTaller.GRADO, grado, m, vigentes).ifPresent(pasos::add);
        if (pasos.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sin cambios: el color y el grado ya son esos");
        guardar(m, pasos, porQue, idUsu);
    }

    /** «Volver a lo de NSYS»: quita lo del taller de los datos que lo tengan, con el motivo si se ha escrito. */
    public void volver(String imeiSn, String motivo, int idUsu) {
        MovilNsys m = delEspejo(imeiSn);
        String porQue = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (porQue != null) comprobarLargo(porQue);
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        if (vigentes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este móvil no tiene nada del taller");
        List<Paso> pasos = vigentes.stream().map(c -> new Paso(new TallerDAO.Nuevo(c.dato(), c.dato().deNsys(m),
                c.dato().deNsys(m), CambioTaller.Estado.QUITADO), c.valor())).toList();
        guardar(m, pasos, porQue, idUsu);
    }

    /** Un dato que cambia y lo que valía antes (para el registro). */
    private record Paso(TallerDAO.Nuevo nuevo, String antes) {}

    private static Optional<Paso> paso(DatoTaller dato, String nuevo, MovilNsys m, List<CambioTaller> vigentes) {
        if (nuevo == null) return Optional.empty();
        String antes = LoQueVale.valor(vigentes, dato, m);
        if (Normalizacion.mismoTexto(nuevo, antes)) return Optional.empty();
        String nsys = dato.deNsys(m);
        CambioTaller.Estado estado = Normalizacion.mismoTexto(nuevo, nsys) ? CambioTaller.Estado.QUITADO
                : CambioTaller.Estado.VIGENTE;
        return Optional.of(new Paso(new TallerDAO.Nuevo(dato, nuevo.strip(), nsys, estado), antes));
    }

    private void guardar(MovilNsys m, List<Paso> pasos, String motivo, int idUsu) {
        tallerDao.guardar(m.imeiSn(), pasos.stream().map(Paso::nuevo).toList(), idUsu, motivo, ahora());
        for (Paso p : pasos) logDao.insertar(idUsu, ACCION, detalle(m.imeiSn(), p), motivo);
    }

    /** «IMEI: 3500…, COLOR: Azul → Rojo», con « (vuelve a lo de NSYS)» detrás si quita lo del taller. */
    private static String detalle(String imeiSn, Paso p) {
        return "IMEI: " + imeiSn + ", " + p.nuevo().dato().name() + ": " + texto(p.antes()) + " → " + texto(p.nuevo().valor())
                + (p.nuevo().estado() == CambioTaller.Estado.QUITADO ? " (vuelve a lo de NSYS)" : "");
    }

    private static void comprobarLargo(String motivo) {
        if (motivo.length() > MAX_MOTIVO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
    }

    // ---------------------------------------------------------------- cierre tras la importación (spec §2.2)

    /** Tras una importación ACTUAL que acaba en OK: cada VIGENTE se compara con lo que NSYS dice ahora del dato (la fila más
     *  reciente del IMEI). Un móvil que ya no está en el espejo deja sus cambios como estaban. Devuelve cuántos cierra. */
    public int cerrar() {
        List<CambioTaller> vigentes = tallerDao.vigentes();
        if (vigentes.isEmpty()) return 0;
        Map<String, MovilNsys> masReciente = masRecientes(vigentes);
        LocalDateTime ahora = ahora();
        int cerrados = 0;
        for (CambioTaller c : vigentes) {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(c.imeiSn()));
            if (m == null) continue;
            String nsys = c.dato().deNsys(m);
            CierreTaller.Decision d = CierreTaller.decidir(c, nsys);
            if (d == CierreTaller.Decision.PASADO) cerrados += tallerDao.cerrar(c.id(), CambioTaller.Estado.PASADO, nsys, ahora);
            else if (d == CierreTaller.Decision.MANDA_NSYS)
                cerrados += tallerDao.cerrar(c.id(), CambioTaller.Estado.MANDA_NSYS, nsys, ahora);
        }
        if (cerrados > 0) log.info("NSYS: {} cambios del taller cerrados tras la importación", cerrados);
        return cerrados;
    }

    // ---------------------------------------------------------------- utilidades

    private MovilNsys delEspejo(String imeiSn) {
        List<MovilNsys> lotes = nsysDao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        return lotes.get(0);
    }

    /** La fila más reciente del espejo de cada IMEI de estos cambios, por {@link NsysDAO#claveCruce}. */
    private Map<String, MovilNsys> masRecientes(List<CambioTaller> cambios) {
        Set<String> imeis = new LinkedHashSet<>();
        for (CambioTaller c : cambios) imeis.add(c.imeiSn());
        Map<String, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(imeis)) mapa.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        return mapa;
    }

    /** Los cambios por IMEI ({@link NsysDAO#claveCruce}), en el orden en que llegan (del más antiguo al más reciente). */
    static Map<String, List<CambioTaller>> porImei(List<CambioTaller> cambios) {
        Map<String, List<CambioTaller>> mapa = new LinkedHashMap<>();
        for (CambioTaller c : cambios) mapa.computeIfAbsent(NsysDAO.claveCruce(c.imeiSn()), k -> new ArrayList<>()).add(c);
        return mapa;
    }

    /** Un valor que se calcula la primera vez que se pide (el maestro, solo si algún móvil tiene algo del taller). */
    private static <T> Supplier<T> perezoso(Supplier<T> fuente) {
        return new Supplier<>() {
            private T valor;

            @Override public T get() {
                if (valor == null) valor = fuente.get();
                return valor;
            }
        };
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }
}
```

- [ ] **Step 4: Ejecutar los tests del servicio**

Run: `mvn -q test -Dtest='TallerNsysTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Tests del cierre tras la importación (fallan)**

En `ImportadorNsysTest.java`:

1. Sustituye

```java
    private final NsysDAO dao = mock(NsysDAO.class);
    private final ImportadorNsys importador = new ImportadorNsys(drive, dao, "carpeta-1", "maestro-1",
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
```

por

```java
    private final NsysDAO dao = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final ImportadorNsys importador = new ImportadorNsys(drive, dao, taller, "carpeta-1", "maestro-1",
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
```

2. En `sinCarpetaNoHaceNada`, cambia `new ImportadorNsys(drive, dao, "", "", Clock.systemUTC()).pasada();` por
   `new ImportadorNsys(drive, dao, taller, "", "", Clock.systemUTC()).pasada();` y añade `verifyNoInteractions(taller);` al
   final del test.
3. En `unFicheroRecienSubidoEsperaALaSiguientePasada` y en `unRecienSubidoRetieneTambienALosPosterioresAunqueEstenAsentados`,
   cambia `new ImportadorNsys(drive, dao, "carpeta-1", "maestro-1",` por `new ImportadorNsys(drive, dao, taller, "carpeta-1", "maestro-1",`.
4. En `unMaestroSinCambiosNoSeDescarga`, añade al final `verify(taller, never()).cerrar();` (sin Items no se cierra nada).
5. Añade al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21: cierre de lo del taller (spec §2.2)

    @Test void trasCadaItemsOkSeCierraLoDelTaller() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(dao, taller);
        orden.verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        orden.verify(taller).cerrar();
        orden.verify(dao).terminarImportacion(eq(2), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        orden.verify(taller).cerrar();
    }

    @Test void unItemsRechazadoNoCierraNada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 70);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(30));

        importador.pasada();

        verify(taller, never()).cerrar();
    }

    @Test void siElCierreFallaLaImportacionSigueOkYSigueConElSiguiente() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        when(taller.cerrar()).thenThrow(new RuntimeException("la base de datos no responde"));

        importador.pasada();

        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
        verify(dao, times(2)).aplicar(any(), anyInt());
    }
```

Run: `mvn -q test -Dtest='ImportadorNsysTest'`
Expected: error de compilación: `ImportadorNsys` no tiene un constructor con `TallerNsys`.

- [ ] **Step 6: Implementación del cierre tras la importación**

En `ImportadorNsys.java`:

1. Tras `private final NsysDAO dao;`, añade `private final TallerNsys taller;`.
2. Sustituye los dos constructores por:

```java
    @Autowired
    public ImportadorNsys(ClienteDrive drive, NsysDAO dao, TallerNsys taller,
                          @Value("${nsys.drive.carpeta-items:}") String carpeta,
                          @Value("${nsys.drive.maestro:}") String maestro) {
        this(drive, dao, taller, carpeta, maestro, Clock.system(MADRID));
    }

    ImportadorNsys(ClienteDrive drive, NsysDAO dao, TallerNsys taller, String carpeta, String maestro, Clock clock) {
        this.drive = drive;
        this.dao = dao;
        this.taller = taller;
        this.carpeta = carpeta == null ? "" : carpeta;
        this.maestro = maestro == null ? "" : maestro;
        this.clock = clock;
    }
```

3. En `procesar`, sustituye

```java
            dao.terminarImportacion(id, OK, filas.size(), r.eventos().size(), resumen(r, repetidas, modelos), ahora());
            return true;
```

por

```java
            dao.terminarImportacion(id, OK, filas.size(), r.eventos().size(), resumen(r, repetidas, modelos), ahora());
            cerrarTaller();
            return true;
```

4. Tras el método `rellenarModelos`, añade:

```java

    /** Tras un Items OK, el cierre de lo del taller (spec 0.9.21 §2.2). La foto ya está guardada y la importación es OK: si el
     *  cierre falla, se avisa en el registro del servidor y se reintenta tras la siguiente. */
    private void cerrarTaller() {
        try {
            taller.cerrar();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudo cerrar lo del taller tras la importación", e);
        }
    }
```

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='TallerNsysTest,ImportadorNsysTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS` (el contexto cablea `TallerNsys` en `ImportadorNsys`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java \
  src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java
git commit -m "feat: correccion a mano, vuelta a NSYS y cierre del taller tras cada importacion (0.9.21)"
```

---

### Task 5: Servidor — lecturas con lo que vale y rastro con lo del taller

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java` (entero)
- Modify: `…/nsys/EventoRastro.java` (entero), `…/nsys/Rastro.java` (entero)
- Test: `…/nsys/StockTelefonosControllerTest.java`, `…/nsys/RastroTest.java`,
  `src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java`,
  `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `TallerNsys.valedor`, `valedorDeTodos`, `productoAPoner`, `cambios` (Task 4); `LoQueVale.Valor` (Task 3).
- Produces:
  - `StockTelefonosController(NsysDAO, TallerNsys)` (Spring) y `(NsysDAO, TallerNsys, Clock)` (tests).
  - Campos nuevos (todos `@Schema(nullable = true)` salvo `porPasar`):
    - `StockTelefonosMovilStock`: `colorNsys`, `gradoNsys`, `skuNsys`;
    - `StockTelefonosFichaMovil`: `colorNsys`, `gradoNsys`, `skuNsys`, `porPasar` (boolean), `ponerProducto`, `ponerGrado`;
    - `StockTelefonosMovilBuscado`: `colorNsys`;
    - `StockTelefonosMovilComprobado`: `colorNsys`, `gradoNsys`.
    `color`, `grado`, `bateriaNueva` y `sku` pasan a ser **lo que vale**.
  - `EventoRastro` añade `dato`, `origen`, `motivo` (nullables). Fuente `TALLER`; tipos `TALLER_CAMBIO`, `TALLER_VUELTA`,
    `TALLER_PASADO`, `TALLER_MANDA_NSYS`; `tecnico` = quién hizo el cambio.
  - `Rastro.unir(nsys, trabajos, cambios)`; `unir(nsys, trabajos)` sigue (con `List.of()`).

- [ ] **Step 1: Tests (fallan)**

En `StockTelefonosControllerTest.java`:

1. Imports: añade `com.reparaciones.servidor.dao.LogDAO`.
2. Sustituye

```java
    private final NsysDAO dao = mock(NsysDAO.class);
    private final StockTelefonosController api = new StockTelefonosController(dao,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
```

por

```java
    private final NsysDAO dao = mock(NsysDAO.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final Clock reloj = Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID);
    private final StockTelefonosController api = new StockTelefonosController(dao, new TallerNsys(dao, tallerDao, logDao, reloj),
            reloj);
```

3. Añade al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21: lo que vale (spec «SKU del taller» §2.1 y §2.4)

    private static final String IMEI = "350000000000001";

    private void conMaestro() {
        when(dao.cargarProductos()).thenReturn(List.of(TallerDePrueba.producto("Blue", "Azul", "A"),
                TallerDePrueba.producto("(PRODUCT)RED", "Rojo", "A"), TallerDePrueba.producto("Blue", "Azul", "A100")));
    }

    @Test void elStockEnsenaLoQueValeYLoDeNsysAlLado() {
        conMaestro();
        when(dao.enEmpresa()).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes()).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        JsonNode stock = json(api.stock());
        assertEquals("Rojo", stock.get(0).get("color").asText());
        assertEquals("Azul", stock.get(0).get("colorNsys").asText());
        assertEquals("i13128RojoA", stock.get(0).get("sku").asText());
        assertEquals("i13128AzulA", stock.get(0).get("skuNsys").asText());
        assertTrue(stock.get(0).get("gradoNsys").isNull());
        assertTrue(stock.get(1).get("colorNsys").isNull(), "sin nada del taller, como hoy");
        assertEquals("i13128AzulA", stock.get(1).get("sku").asText());
    }

    @Test void unStockSinNadaDelTallerNoLeeElMaestro() {
        when(dao.enEmpresa()).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        api.stock();
        verify(dao, never()).cargarProductos();
    }

    @Test void laFichaConElGradoDelTallerYLoQueHayQuePonerEnNsys() {
        conMaestro();
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "A")));
        JsonNode ficha = json(api.ficha(IMEI));
        assertEquals("A", ficha.get("grado").asText());
        assertTrue(ficha.get("bateriaNueva").asBoolean());
        assertEquals("A", ficha.get("gradoNsys").asText());
        assertEquals("i13128AzulA100", ficha.get("sku").asText());
        assertTrue(ficha.get("porPasar").asBoolean());
        assertEquals("Apple iPhone 13 128GB Blue", ficha.get("ponerProducto").asText(), "solo cambia el grado: el producto de ahora");
        assertEquals("A100", ficha.get("ponerGrado").asText());
        verify(dao, never()).goodsMasUsados(any());
    }

    @Test void laFichaConElColorDelTallerPideElProductoMasUsadoDeEseColor() {
        conMaestro();
        MovilNsys m = movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK);
        when(dao.porImei(IMEI)).thenReturn(List.of(m));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        when(dao.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        JsonNode ficha = json(api.ficha(IMEI));
        assertEquals("Rojo", ficha.get("color").asText());
        assertEquals("Azul", ficha.get("colorNsys").asText());
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", ficha.get("ponerProducto").asText());
        when(dao.goodsMasUsados(m)).thenReturn(Map.of());
        assertTrue(json(api.ficha(IMEI)).get("ponerProducto").isNull(), "ningún móvil de ese color: la web dice «búscalo a mano»");
    }

    @Test void laFichaSinNadaDelTallerNoTieneNadaPorPasar() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        JsonNode ficha = json(api.ficha(IMEI));
        assertFalse(ficha.get("porPasar").asBoolean());
        assertTrue(ficha.get("ponerProducto").isNull());
        assertTrue(ficha.get("ponerGrado").isNull());
        assertTrue(ficha.get("colorNsys").isNull());
    }

    @Test void comprobarConElColorYElGradoQueValen() {
        conMaestro();
        List<String> imeis = List.of(IMEI);
        when(dao.porImeis(imeis)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(imeis)).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul"),
                TallerDePrueba.vigente(2, IMEI, DatoTaller.GRADO, "A100", "A")));
        JsonNode r = json(api.comprobar(pedir(IMEI))).get(0);
        assertEquals("Rojo", r.get("color").asText());
        assertEquals("Azul", r.get("colorNsys").asText());
        assertEquals("A", r.get("grado").asText());
        assertTrue(r.get("bateriaNueva").asBoolean());
        assertEquals("A", r.get("gradoNsys").asText());
    }

    @Test void buscarConElColorQueVale() {
        when(dao.buscar("3500", 10)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        JsonNode r = json(api.buscar("3500")).get(0);
        assertEquals("Rojo", r.get("color").asText());
        assertEquals("Azul", r.get("colorNsys").asText());
    }

    @Test void elRastroLlevaLosCambiosDelTaller() {
        when(tallerDao.deImei(IMEI)).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "B")));
        List<EventoRastro> rastro = api.rastro(IMEI);
        assertEquals("TALLER_CAMBIO", rastro.get(0).tipo());
        assertEquals("TALLER", rastro.get(0).fuente());
        assertEquals("GRADO", rastro.get(0).dato());
    }
```

En `RastroTest.java`:

1. En `uneNsysYTallerDelMasRecienteAlMasAntiguo`, a cada uno de los cinco `new EventoRastro(…)` de la lista esperada añade
   `, null, null, null` antes del `)` final (los campos nuevos `dato`, `origen` y `motivo`). Por ejemplo, la primera línea queda
   `new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION", null, null, null, null),`.
2. Añade al final de la clase:

```java
    @Test void losCambiosDelTallerYSusCierresSeMezclanPorFecha() {
        // 0.9.21 (spec «SKU del taller» §2.4): cada cambio a su hora y cada cierre automático a la del cierre. Un SUSTITUIDO no
        // tiene evento de cierre: lo cuenta el que lo sustituye (aquí, la vuelta a NSYS).
        String imei = "350000000000001";
        CambioTaller pasado = new CambioTaller(1, imei, DatoTaller.GRADO, "A100", "B", "B", "MANO", null, 7, "Técnico 1",
                "batería nueva", t(10, 9), CambioTaller.Estado.PASADO, t(12, 9), "A100", null);
        CambioTaller sustituido = new CambioTaller(2, imei, DatoTaller.COLOR, "Rojo", "Azul", "Azul", "MANO", null, 7,
                "Técnico 1", "chasis rojo", t(11, 9), CambioTaller.Estado.SUSTITUIDO, t(13, 9), null, null);
        CambioTaller vuelta = new CambioTaller(3, imei, DatoTaller.COLOR, "Azul", "Rojo", "Azul", "MANO", null, 8, "Técnico 2",
                null, t(13, 9), CambioTaller.Estado.QUITADO, t(13, 9), null, 2);
        CambioTaller manda = new CambioTaller(4, imei, DatoTaller.COLOR, "Verde", "Azul", "Azul", "MANO", null, 7, "Técnico 1",
                "chasis verde", t(14, 9), CambioTaller.Estado.MANDA_NSYS, t(15, 9), "Rosa", null);
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(pasado, sustituido, vuelta, manda));
        assertEquals(List.of("TALLER_MANDA_NSYS", "TALLER_CAMBIO", "TALLER_VUELTA", "TALLER_PASADO", "TALLER_CAMBIO",
                "TALLER_CAMBIO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(t(15, 9), false, "TALLER", "TALLER_MANDA_NSYS", null, "Verde", "Rosa", null, null,
                "Técnico 1", null, null, "COLOR", "MANO", "chasis verde"), rastro.get(0));
        assertEquals(new EventoRastro(t(13, 9), false, "TALLER", "TALLER_VUELTA", null, "Rojo", "Azul", null, null,
                "Técnico 2", null, null, "COLOR", "MANO", null), rastro.get(2));
        assertEquals(new EventoRastro(t(12, 9), false, "TALLER", "TALLER_PASADO", null, "A100", "A100", null, null,
                "Técnico 1", null, null, "GRADO", "MANO", "batería nueva"), rastro.get(3));
    }
```

En `RolesStockTelefonosTest.java`: añade el import `com.reparaciones.servidor.nsys.TallerDAO` y, tras `@MockBean TelefonoDAO telefonoDao;`,
la línea `@MockBean TallerDAO tallerDao;` (sin base de datos: el stock y la ficha ya leen lo del taller).

En `OpenApiContractTest.java`, método `elContratoPublicaElStockDeTelefonos`, justo después de
`assertNoNullable(esquemas, "EventoRastro", "fecha", "soloDia", "fuente", "tipo");`, añade:

```java
        // 0.9.21 (spec «SKU del taller» §4): lo que vale, con lo de NSYS al lado cuando el taller lo cambia.
        assertNullable(esquemas, "StockTelefonosMovilStock", "colorNsys", "gradoNsys", "skuNsys");
        assertNullable(esquemas, "StockTelefonosFichaMovil", "colorNsys", "gradoNsys", "skuNsys", "ponerProducto", "ponerGrado");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "porPasar");
        assertNullable(esquemas, "StockTelefonosMovilBuscado", "colorNsys");
        assertNullable(esquemas, "StockTelefonosMovilComprobado", "colorNsys", "gradoNsys");
        assertNullable(esquemas, "EventoRastro", "dato", "origen", "motivo");
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='StockTelefonosControllerTest,RastroTest'`
Expected: error de compilación: el controlador no tiene constructor con `TallerNsys`, y `EventoRastro` no tiene 15 campos ni
`Rastro.unir` tres parámetros.

- [ ] **Step 3: Implementación**

Sustituye `EventoRastro.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys), del taller del ERP (idRep, técnico,
 *  categoría) o, desde la 0.9.21, de lo del taller por encima de NSYS (fuente TALLER: {@code dato} COLOR o GRADO, de → a,
 *  {@code origen} MANO o REPARACION, {@code tecnico} = quién lo hizo, {@code motivo}). {@code tipo}: los de
 *  {@link TipoEvento}, TRABAJO_ASIGNADO / TRABAJO_TERMINADO o TALLER_CAMBIO / TALLER_VUELTA / TALLER_PASADO /
 *  TALLER_MANDA_NSYS. {@code revision}: ENTRADA o DEVOLUCION en el cambio de caja que es la revisión del lote (0.9.18,
 *  {@link Revision}). La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria, @Schema(nullable = true) String revision,
                           @Schema(nullable = true) String dato, @Schema(nullable = true) String origen,
                           @Schema(nullable = true) String motivo) {}
```

Sustituye `Rastro.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Junta los eventos de NSYS, los trabajos del taller y, desde la 0.9.21, los cambios del taller por encima de NSYS (spec
 *  «SKU del taller» §2.4) de un IMEI en una sola línea de tiempo, del más reciente al más antiguo (spec pieza 1 §8). La venta
 *  del esqueleto (solo el día) no sale si el mismo lote tiene una con hora. */
public final class Rastro {

    private Rastro() {}

    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        return unir(nsys, trabajos, List.of());
    }

    /** Cada cambio del taller sale a su hora («Taller: …» o, si vuelve a lo de NSYS, «Vuelve a lo de NSYS: …») y su cierre
     *  automático a la hora del cierre («Pasado a NSYS», «Manda NSYS»). Un SUSTITUIDO no tiene evento de cierre: lo cuenta el
     *  cambio que lo sustituye. */
    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos, List<CambioTaller> cambios) {
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        // Sobre la lista entera, antes de quitar la venta del esqueleto: así cada tipo sigue con su evento.
        List<String> revisiones = Revision.tipos(nsys);
        List<EventoRastro> todos = new ArrayList<>();
        for (int i = 0; i < nsys.size(); i++) {
            EventoNsys e = nsys.get(i);
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null, revisiones.get(i), null, null, null));
        }
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep()), null, null, null, null));
        }
        for (CambioTaller c : cambios) {
            todos.add(deTaller(c.fecha(), c.estado() == CambioTaller.Estado.QUITADO ? "TALLER_VUELTA" : "TALLER_CAMBIO", c,
                    c.valorAntes(), c.valor()));
            if (c.cerradoEn() == null) continue;
            if (c.estado() == CambioTaller.Estado.PASADO)
                todos.add(deTaller(c.cerradoEn(), "TALLER_PASADO", c, c.valor(), c.valorCierre()));
            if (c.estado() == CambioTaller.Estado.MANDA_NSYS)
                todos.add(deTaller(c.cerradoEn(), "TALLER_MANDA_NSYS", c, c.valor(), c.valorCierre()));
        }
        // Del más reciente al más antiguo. A la misma hora, primero lo último apuntado: los eventos de NSYS llegan por
        // orden de alta (ID_EVENTO) y los del taller van detrás, así que un trabajo terminado sale antes que el cambio de
        // NSYS de esa misma exportación, y la venta del esqueleto antes que su entrada.
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) orden.add(i);
        orden.sort(Comparator.comparing((Integer i) -> todos.get(i).fecha()).thenComparing(i -> i).reversed());
        return orden.stream().map(todos::get).toList();
    }

    private static EventoRastro deTaller(LocalDateTime fecha, String tipo, CambioTaller c, String de, String a) {
        return new EventoRastro(fecha, false, "TALLER", tipo, null, de, a, null, c.idRep(), c.usuario(), null, null,
                c.dato().name(), c.origen(), c.motivo());
    }

    /** Asignaciones A/AG/AP y trabajos hechos R/G/P. */
    static String categoria(String idRep) {
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return "GLASS";
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return "PULIDO";
        return "REPARACION";
    }
}
```

Sustituye `StockTelefonosController.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.reparaciones.servidor.nsys.ImportadorNsys.*;

/** Stock de teléfonos y rastro (spec pieza 1 §8), del espejo de NSYS y, desde la 0.9.21, con lo del taller por encima (spec
 *  «SKU del taller»): color, grado y SKU son los que valen, con lo de NSYS al lado cuando el taller los cambia. SUPERTECNICO
 *  y ADMIN; no hay jerarquía de roles, así que van los dos nombrados. */
@RestController
@RequestMapping("/api/stock-telefonos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class StockTelefonosController {

    static final String MOTIVO_ERROR = "Error al leer de Drive o al guardar; ver el registro del servidor";

    /** Como mucho los IMEIs de una vez en «Pegar varios IMEIs» (spec 0.9.18 §4.9). */
    static final int MAX_COMPROBAR = 500;

    /** Tope de filas de la lista de importaciones (spec 0.9.18 §6.5); por defecto, 20. */
    static final int LIMITE_IMPORTACIONES = 50;
    private static final String EN_CURSO = "EN_CURSO";

    private final NsysDAO dao;
    private final TallerNsys taller;
    private final Clock clock;

    @Autowired
    public StockTelefonosController(NsysDAO dao, TallerNsys taller) {
        this(dao, taller, Clock.system(MADRID));
    }

    StockTelefonosController(NsysDAO dao, TallerNsys taller, Clock clock) {
        this.dao = dao;
        this.taller = taller;
        this.clock = clock;
    }

    /** Los móviles que están en la empresa (en stock, de pedido o que no cuentan), cada uno con sus trabajos abiertos del
     *  taller (0.9.18) y lo que vale (0.9.21). La web filtra en local y calcula «Quién lo tiene». */
    @GetMapping
    public List<MovilStock> stock() {
        LocalDate hoy = LocalDate.now(clock);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos();
        Function<MovilNsys, LoQueVale.Valor> vale = taller.valedorDeTodos();
        return dao.enEmpresa().stream().map(m -> {
            LoQueVale.Valor v = vale.apply(m);
            return new MovilStock(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), v.color(), v.grado(),
                    v.bateriaNueva(), m.esEsim(), v.sku(), ubicacionVisible(m), m.estado().name(), dias(m, hoy),
                    abiertos.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()), v.colorNsys(), v.gradoNsys(),
                    v.skuNsys());
        }).toList();
    }

    @GetMapping("/estado")
    public EstadoImportacion estado() {
        Optional<Importacion> ok = dao.ultimaImportacion(ACTUAL, Set.of(OK));
        Optional<Importacion> fallo = dao.ultimaImportacion(ACTUAL, Set.of(RECHAZADO, ERROR));
        LocalDateTime ultima = ok.map(Importacion::fechaExportacion).orElse(null);
        boolean falloReciente = fallo.isPresent() && (ultima == null || fallo.get().fechaExportacion().isAfter(ultima));
        return new EstadoImportacion(ultima, AvisoNsys.nivel(ultima, LocalDateTime.now(clock)),
                falloReciente ? fallo.get().fechaExportacion() : null, falloReciente ? motivo(fallo.get()) : null);
    }

    /** Los rechazos los explica un texto nuestro; un ERROR guarda e.toString(), que puede llevar ids de ficheros de
     *  Drive o el correo de la cuenta de servicio, y no sale de la API. */
    private static String motivo(Importacion fallo) {
        return ERROR.equals(fallo.resultado()) ? MOTIVO_ERROR : fallo.mensaje();
    }

    /** Hasta 10 móviles de cualquier estado cuyo IMEI/SN contiene el texto (mínimo 4 caracteres), con el color que vale. */
    @GetMapping("/buscar")
    public List<MovilBuscado> buscar(@RequestParam String imei) {
        if (imei == null || imei.strip().length() < 4) return List.of();
        List<MovilNsys> encontrados = dao.buscar(imei.strip(), 10);
        Function<MovilNsys, LoQueVale.Valor> vale = taller.valedor(encontrados.stream().map(MovilNsys::imeiSn).toList());
        return encontrados.stream().map(m -> {
            LoQueVale.Valor v = vale.apply(m);
            return new MovilBuscado(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), v.color(), m.estado().name(),
                    ubicacionVisible(m), m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null, v.colorNsys());
        }).toList();
    }

    @GetMapping("/{imeiSn}")
    public FichaMovil ficha(@PathVariable String imeiSn) {
        List<MovilNsys> lotes = dao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        MovilNsys m = lotes.get(0);
        // 0.9.21: lo que vale por IMEI, sobre la fila más reciente; con algo del taller, lo que hay que poner en NSYS.
        LoQueVale.Valor v = taller.valedor(List.of(m.imeiSn())).apply(m);
        List<EventoNsys> eventos = dao.eventos(imeiSn);
        // El último cambio de grado de verdad del lote: un cambio de batería (A → A100) no cuenta, o taparía el grado
        // anterior real (C → A → A100 da C).
        String gradoAnterior = eventos.stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && Objects.equals(e.batch(), m.batch()))
                .filter(e -> !Normalizacion.gradoSinBateria(e.de()).equals(Normalizacion.gradoSinBateria(e.a())))
                .reduce((a, b) -> b).map(e -> e.de() == null ? null : Normalizacion.gradoSinBateria(e.de())).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        // 0.9.18 (spec §5.2): la última revisión del lote y, si está en «En revisión», desde cuándo. Un vendido o una baja
        // no espera revisión (0.9.18.2).
        Revision.Ultima revisado = Revision.ultima(eventos, m.batch());
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), v.color(), v.grado(),
                v.bateriaNueva(), gradoAnterior, v.sku(), ubicacionVisible(m), m.estado().name(),
                enEmpresa ? dias(m, LocalDate.now(clock)) : null, lotes.size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                revisado == null ? null : revisado.fecha(), revisado != null && revisado.devolucion(),
                enEmpresa ? Revision.pendienteDesde(m, eventos) : null,
                v.colorNsys(), v.gradoNsys(), v.skuNsys(), v.porPasar(), v.porPasar() ? taller.productoAPoner(m, v) : null,
                v.porPasar() ? v.grade() : null);
    }

    @GetMapping("/{imeiSn}/rastro")
    public List<EventoRastro> rastro(@PathVariable String imeiSn) {
        return Rastro.unir(dao.eventos(imeiSn), dao.trabajos(imeiSn), taller.cambios(imeiSn));
    }

    /** «Pegar varios IMEIs» (spec 0.9.18 §4.9 y §6.2): una entrada por IMEI pedido, en el mismo orden. Busca en todo el
     *  espejo, también los vendidos; de cada IMEI, el lote más reciente, con sus trabajos abiertos y lo que vale (0.9.21).
     *  La web manda los IMEIs limpios y sin repetir; más de {@value #MAX_COMPROBAR}, o alguno nulo o en blanco, es un 400. */
    @PostMapping("/comprobar")
    public List<MovilComprobado> comprobar(@RequestBody PeticionComprobar peticion) {
        List<String> imeis = peticion == null || peticion.imeis() == null ? List.of() : peticion.imeis();
        if (imeis.size() > MAX_COMPROBAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Como mucho " + MAX_COMPROBAR + " IMEIs por vez");
        if (imeis.stream().anyMatch(i -> i == null || i.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista lleva IMEIs vacíos");
        if (imeis.isEmpty()) return List.of();
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : dao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos(imeis);
        Function<MovilNsys, LoQueVale.Valor> vale = taller.valedor(imeis);
        return imeis.stream().map(imei -> {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(imei));
            if (m == null)
                return new MovilComprobado(imei, false, null, null, null, null, null, false, null, null, null, List.of(), null, null);
            LoQueVale.Valor v = vale.apply(m);
            return new MovilComprobado(imei, true, m.modelo(), m.modeloMaestro(), m.capacidad(), v.color(), v.grado(),
                    v.bateriaNueva(), ubicacionVisible(m), m.estado().name(),
                    m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                    abiertos.getOrDefault(NsysDAO.claveCruce(imei), List.of()), v.colorNsys(), v.gradoNsys());
        }).toList();
    }

    /** Las últimas importaciones del Items actual, de la más reciente a la más antigua, y el progreso del histórico (spec
     *  0.9.18 §4.11 y §6.5): para ver desde la web que llega un Items cada media hora. Los huecos los calcula la web. */
    @GetMapping("/importaciones")
    public ListaImportaciones importaciones(@RequestParam(defaultValue = "20") int limite) {
        int n = Math.max(1, Math.min(LIMITE_IMPORTACIONES, limite));
        List<FilaImportacion> filas = dao.importaciones(ACTUAL, n).stream().map(StockTelefonosController::fila).toList();
        ResumenHistorico h = dao.resumenHistorico();
        LocalDateTime hasta = dao.primeraImportacion(ACTUAL, Set.of(OK)).map(Importacion::fechaExportacion).orElse(null);
        return new ListaImportaciones(filas, new ProgresoHistorico(h.total(), h.hechos(), h.ultimo(), hasta));
    }

    /** El motivo: el texto nuestro de OK y RECHAZADO; el genérico de un ERROR, cuyo MENSAJE es e.toString() y puede llevar
     *  ids de Drive o el correo de la cuenta de servicio (como en /estado); nada mientras está en curso. */
    private static FilaImportacion fila(RegistroImportacion r) {
        boolean enCurso = EN_CURSO.equals(r.resultado());
        Long segundos = enCurso || r.empezada() == null || r.terminada() == null ? null
                : Duration.between(r.empezada(), r.terminada()).toSeconds();
        String motivo = enCurso ? null : ERROR.equals(r.resultado()) ? MOTIVO_ERROR : r.mensaje();
        return new FilaImportacion(r.fichero(), r.fechaExportacion(), r.resultado(), r.filas(), r.cambios(), segundos, motivo);
    }

    /** Un móvil de pedido sigue «en su caja»: la última ubicación conocida antes de «Pedidos realizados». */
    private static String ubicacionVisible(MovilNsys m) {
        return m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
    }

    private static Integer dias(MovilNsys m, LocalDate hoy) {
        LocalDate desde = m.receivingDate() != null ? m.receivingDate()
                : m.vistoPrimero() != null ? m.vistoPrimero().toLocalDate() : null;
        return desde == null ? null : (int) ChronoUnit.DAYS.between(desde, hoy);
    }

    /** 0.9.21: {@code color}, {@code grado}, {@code bateriaNueva} y {@code sku} son lo que vale; {@code colorNsys},
     *  {@code gradoNsys} (el grado entero de NSYS, con el 100) y {@code skuNsys}, lo de NSYS cuando el taller lo cambia. */
    private record MovilStock(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva, boolean esEsim,
                              @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, List<TrabajoAbierto> trabajos,
                              @Schema(nullable = true) String colorNsys, @Schema(nullable = true) String gradoNsys,
                              @Schema(nullable = true) String skuNsys) {}

    private record EstadoImportacion(@Schema(nullable = true) LocalDateTime ultimaExportacion, String nivel,
                                     @Schema(nullable = true) LocalDateTime ultimoFallo,
                                     @Schema(nullable = true) String motivoFallo) {}

    private record MovilBuscado(String imeiSn, @Schema(nullable = true) String modelo,
                                @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                @Schema(nullable = true) String color, String estado,
                                @Schema(nullable = true) String ubicacion, @Schema(nullable = true) LocalDate fechaVenta,
                                @Schema(nullable = true) String colorNsys) {}

    /** 0.9.21: {@code porPasar}, el móvil tiene algo del taller vigente; {@code ponerProducto} (null: ningún móvil de ese color
     *  en NSYS) y {@code ponerGrado}, lo que hay que poner en NSYS, solo con {@code porPasar}. */
    private record FichaMovil(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva,
                              @Schema(nullable = true) String gradoAnterior, @Schema(nullable = true) String sku,
                              @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, int entradas,
                              @Schema(nullable = true) LocalDate fechaVenta,
                              @Schema(nullable = true) LocalDateTime revisadoEl, boolean revisadoDevolucion,
                              @Schema(nullable = true) LocalDate pendienteRevisionDesde,
                              @Schema(nullable = true) String colorNsys, @Schema(nullable = true) String gradoNsys,
                              @Schema(nullable = true) String skuNsys, boolean porPasar,
                              @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado) {}

    /** Del paquete (no privado) para que los tests lo construyan; el esquema se llama StockTelefonosPeticionComprobar. */
    record PeticionComprobar(List<String> imeis) {}

    private record ListaImportaciones(List<FilaImportacion> importaciones, ProgresoHistorico historico) {}

    private record FilaImportacion(String fichero, LocalDateTime fechaExportacion, String resultado, int filas, int cambios,
                                   @Schema(nullable = true) Long segundos, @Schema(nullable = true) String motivo) {}

    /** {@code total} = 0: no hay ninguna importación HISTORICO y la web no pinta la línea. {@code hasta}: el Items de la
     *  primera importación ACTUAL OK, donde termina el histórico. */
    private record ProgresoHistorico(int total, int hechos, @Schema(nullable = true) LocalDateTime ultimo,
                                     @Schema(nullable = true) LocalDateTime hasta) {}

    private record MovilComprobado(String imei, boolean encontrado, @Schema(nullable = true) String modelo,
                                   @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                   @Schema(nullable = true) String color, @Schema(nullable = true) String grado,
                                   boolean bateriaNueva, @Schema(nullable = true) String ubicacion,
                                   @Schema(nullable = true) String estado, @Schema(nullable = true) LocalDate fechaVenta,
                                   List<TrabajoAbierto> trabajos, @Schema(nullable = true) String colorNsys,
                                   @Schema(nullable = true) String gradoNsys) {}
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='StockTelefonosControllerTest,RastroTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. Los tests de antes del controlador siguen en verde: sin cambios del taller (el `TallerDAO` mockeado
devuelve listas vacías), todo sale como hoy.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/main/java/com/reparaciones/servidor/nsys/EventoRastro.java \
  src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java \
  src/test/java/com/reparaciones/servidor/nsys/RastroTest.java src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java \
  src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: stock, ficha, comprobar y buscador con lo que vale del taller, y el rastro con sus cambios (0.9.21)"
```

---

### Task 6: Servidor — corrección y vuelta por la API, lista «Por pasar a NSYS» y opciones del diálogo

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/FilaPorPasar.java`, `PorQueTaller.java`,
  `OpcionesTaller.java`, `OpcionColor.java`
- Modify: `…/nsys/TallerNsys.java` (`porPasar`, `opciones`), `…/nsys/StockTelefonosController.java` (cuatro rutas y dos records)
- Test: `…/nsys/TallerNsysTest.java`, `…/nsys/StockTelefonosControllerTest.java`, `…/controller/RolesStockTelefonosTest.java`,
  `…/OpenApiContractTest.java`

**Interfaces:**
- Consumes: todo lo de las Tasks 1-5; `UsuarioPrincipal.getIdUsu()`.
- Produces:
  - Records públicos (esquemas con su nombre simple):
    - `FilaPorPasar(String imeiSn, String modelo, String modeloMaestro, String capacidad, String productoNsys, String gradoNsys,
      String ponerProducto, String ponerGrado, String sku, List<PorQueTaller> porQue, LocalDateTime desde)`;
    - `PorQueTaller(String dato, String origen, String usuario, String motivo, String idRep)`;
    - `OpcionesTaller(String productoActual, List<OpcionColor> colores, List<SkuMaestro> skus)`;
    - `OpcionColor(String color, String producto)`.
  - `TallerNsys.porPasar()` y `TallerNsys.opciones(String imeiSn)`.
  - Rutas: `GET /api/stock-telefonos/por-pasar` → `FilaPorPasar[]`; `GET /{imeiSn}/colores` → `OpcionesTaller`;
    `PUT /{imeiSn}/taller` con `StockTelefonosPeticionTaller { color, grado, motivo }` → 204;
    `DELETE /{imeiSn}/taller` con `StockTelefonosPeticionVolver { motivo }` (cuerpo opcional) → 204.

- [ ] **Step 1: Tests (fallan)**

En `TallerNsysTest.java`, añade al final de la clase (los imports de la Task 4 bastan):

```java
    // ---------------------------------------------------------------- «Por pasar a NSYS» (spec §2.4)

    @Test void laListaConLoQueHayQuePonerEnNsysLosMasAntiguosPrimero() {
        String otro = "350000000000002";
        MovilNsys uno = movil(IMEI, "Azul", "B");
        MovilNsys dos = movil(otro, "Azul", "B");
        when(taller.vigentes()).thenReturn(List.of(vigente(1, otro, COLOR, "Rojo", "Azul", AHORA.minusDays(3)),
                vigente(2, IMEI, GRADO, "A100", "B", AHORA.minusDays(2)), vigente(3, otro, GRADO, "A100", "B", AHORA.minusDays(1))));
        when(nsys.porImeis(any())).thenReturn(List.of(uno, dos));
        when(nsys.goodsMasUsados(dos)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        List<FilaPorPasar> lista = servicio.porPasar();
        assertEquals(List.of(otro, IMEI), lista.stream().map(FilaPorPasar::imeiSn).toList(), "los más antiguos primero");
        assertEquals(new FilaPorPasar(otro, "13", "13", "128GB", "Apple iPhone 13 128GB Blue", "B",
                "Apple iPhone 13 128GB (PRODUCT)RED", "A100", "i13128RojoA100",
                List.of(new PorQueTaller("COLOR", "MANO", "Técnico 1", "prueba", null),
                        new PorQueTaller("GRADO", "MANO", "Técnico 1", "prueba", null)),
                AHORA.minusDays(3)), lista.get(0));
        assertEquals("Apple iPhone 13 128GB Blue", lista.get(1).ponerProducto(), "solo cambia el grado: el producto de ahora");
        assertEquals("i13128AzulA100", lista.get(1).sku());
    }

    @Test void sinNingunMovilDeEseColorElProductoVaVacio() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertNull(servicio.porPasar().get(0).ponerProducto());
    }

    @Test void unMovilQueYaNoEstaEnElEspejoNoSale() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B")));
        assertTrue(servicio.porPasar().isEmpty());
    }

    @Test void sinNadaVigenteLaListaEstaVaciaSinLeerElEspejo() {
        assertTrue(servicio.porPasar().isEmpty());
        verify(nsys, never()).porImeis(any());
        verify(nsys, never()).cargarProductos();
    }

    // ---------------------------------------------------------------- opciones del diálogo (spec §2.3)

    @Test void lasOpcionesConElProductoDeCadaColorYLosSku() {
        MovilNsys m = movil(IMEI, "Azul", "B");
        when(nsys.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED",
                "Azul", "Apple iPhone 13 128GB Azul de otro lote"));
        OpcionesTaller o = servicio.opciones(IMEI);
        assertEquals("Apple iPhone 13 128GB Blue", o.productoActual());
        assertEquals(List.of(new OpcionColor("Azul", "Apple iPhone 13 128GB Blue"),
                new OpcionColor("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED")), o.colores(), "el de NSYS, con el producto de ahora");
        assertTrue(o.skus().contains(new SkuMaestro("Rojo", "A100", "i13128RojoA100")));
        assertEquals(4, o.skus().size());
    }

    @Test void sinModeloEnElMaestroNoHayColoresNiSeBuscaProducto() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(sinMaestro(IMEI, "B")));
        OpcionesTaller o = servicio.opciones(IMEI);
        assertTrue(o.colores().isEmpty());
        assertTrue(o.skus().isEmpty());
        verify(nsys, never()).goodsMasUsados(any());
    }

    @Test void lasOpcionesDeUnImeiFueraDelEspejoSon404() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.opciones("350000000000099")));
    }
```

En `StockTelefonosControllerTest.java`, añade el import `com.reparaciones.servidor.security.UsuarioPrincipal` (`any`, `eq` e
`isNull` ya llegan con `static org.mockito.Mockito.*`) y, al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21: corrección, vuelta, lista y opciones

    private static final UsuarioPrincipal SUPER = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    @Test void corregirGuardaConElUsuarioDeLaSesion() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        api.corregir(IMEI, new StockTelefonosController.PeticionTaller(null, "A100", "batería nueva"), SUPER);
        verify(tallerDao).guardar(eq(IMEI), eq(List.of(new TallerDAO.Nuevo(DatoTaller.GRADO, "A100", "A",
                CambioTaller.Estado.VIGENTE))), eq(7), eq("batería nueva"), any());
        verify(logDao).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A → A100", "batería nueva");
    }

    @Test void volverSinCuerpoVaSinMotivo() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "A")));
        api.volver(IMEI, null, SUPER);
        verify(tallerDao).guardar(eq(IMEI), eq(List.of(new TallerDAO.Nuevo(DatoTaller.GRADO, "A", "A",
                CambioTaller.Estado.QUITADO))), eq(7), isNull(), any());
    }

    @Test void laListaYLasOpcionesSalenDelServicio() {
        assertTrue(api.porPasar().isEmpty());
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        OpcionesTaller o = api.colores(IMEI);
        assertEquals("Apple iPhone 13 128GB Blue", o.productoActual());
        assertTrue(o.colores().isEmpty(), "el maestro del mock está vacío");
    }
```

En `RolesStockTelefonosTest.java`:

1. Imports: añade `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete` y
   `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put`.
2. Sustituye la constante `RUTAS` por:

```java
    private static final List<String> RUTAS = List.of("/api/stock-telefonos", "/api/stock-telefonos/estado",
            "/api/stock-telefonos/buscar?imei=3500", "/api/stock-telefonos/350000000000001",
            "/api/stock-telefonos/350000000000001/rastro", "/api/stock-telefonos/importaciones",
            "/api/stock-telefonos/por-pasar", "/api/stock-telefonos/350000000000001/colores");
```

3. En `elTecnicoRecibe403SinLlegarAlDao`, `sinTokenRecibe403SinLlegarAlDao` y `conTokenInvalidoRecibe401SinLlegarAlDao`, cambia
   `verifyNoInteractions(dao);` por `verifyNoInteractions(dao, tallerDao);`.
4. Añade al final de la clase:

```java
    /** 0.9.21 (spec «SKU del taller» §2.3): corregir y volver a lo de NSYS, con la misma guarda que el resto. */
    @Test void corregirYVolverSoloParaSupertecnicoYAdmin() throws Exception {
        String corregir = "{\"color\":null,\"grado\":\"A100\",\"motivo\":\"batería nueva\"}";
        String volver = "{\"motivo\":null}";
        String ruta = "/api/stock-telefonos/350000000000009/taller";
        mvc.perform(put(ruta).header("Authorization", tecnico()).contentType(MediaType.APPLICATION_JSON).content(corregir))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ruta).header("Authorization", tecnico()).contentType(MediaType.APPLICATION_JSON).content(volver))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dao, tallerDao);
        // NSYS no conoce el 350000000000009 (el DAO mockeado no tiene lotes): llega al servicio y da 404.
        for (String token : List.of(supertecnico(), admin())) {
            mvc.perform(put(ruta).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corregir))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(ruta).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(volver))
                    .andExpect(status().isNotFound());
        }
        verify(tallerDao, never()).guardar(any(), any(), anyInt(), any(), any());
    }
```

   y los imports `static org.mockito.ArgumentMatchers.any`, `static org.mockito.ArgumentMatchers.anyInt` y
   `static org.mockito.Mockito.never` si no están.

En `OpenApiContractTest.java`, método `elContratoPublicaElStockDeTelefonos`, al final (tras las líneas de
`StockTelefonosProgresoHistorico`), añade:

```java

        // 0.9.21 (spec «SKU del taller» §4): la corrección a mano, la lista «Por pasar a NSYS» y las opciones del diálogo.
        for (String ruta : List.of("/api/stock-telefonos/por-pasar", "/api/stock-telefonos/{imeiSn}/colores",
                "/api/stock-telefonos/{imeiSn}/taller")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/{imeiSn}/taller", "put").endsWith("/StockTelefonosPeticionTaller"));
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/{imeiSn}/taller", "delete").endsWith("/StockTelefonosPeticionVolver"));
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/{imeiSn}/colores", "get", "200").endsWith("/OpcionesTaller"));
        assertNullable(esquemas, "StockTelefonosPeticionTaller", "color", "grado");
        assertNoNullable(esquemas, "StockTelefonosPeticionTaller", "motivo");
        assertNullable(esquemas, "StockTelefonosPeticionVolver", "motivo");
        assertNullable(esquemas, "FilaPorPasar", "modelo", "modeloMaestro", "capacidad", "productoNsys", "gradoNsys",
                "ponerProducto", "ponerGrado", "sku");
        assertNoNullable(esquemas, "FilaPorPasar", "imeiSn", "porQue", "desde");
        assertNullable(esquemas, "PorQueTaller", "usuario", "motivo", "idRep");
        assertNoNullable(esquemas, "PorQueTaller", "dato", "origen");
        assertNullable(esquemas, "OpcionesTaller", "productoActual");
        assertNoNullable(esquemas, "OpcionesTaller", "colores", "skus");
        assertNullable(esquemas, "OpcionColor", "producto");
        assertNoNullable(esquemas, "OpcionColor", "color");
        assertNoNullable(esquemas, "SkuMaestro", "color", "grado", "sku");
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='TallerNsysTest,StockTelefonosControllerTest'`
Expected: error de compilación: no existen `FilaPorPasar`, `OpcionesTaller`, `TallerNsys.porPasar`, `api.corregir`…

- [ ] **Step 3: Implementación**

Crea `PorQueTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Por qué un dato de un móvil está «por pasar a NSYS» (spec 0.9.21 §2.4): {@code dato} COLOR o GRADO, {@code origen} MANO
 *  (quién y motivo) o REPARACION (pieza 2: {@code idRep}). */
public record PorQueTaller(String dato, String origen, @Schema(nullable = true) String usuario,
                           @Schema(nullable = true) String motivo, @Schema(nullable = true) String idRep) {}
```

Crea `FilaPorPasar.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Por pasar a NSYS» (spec 0.9.21 §2.4): lo que dice NSYS hoy ({@code productoNsys} = Goods, {@code gradoNsys} =
 *  grado entero), lo que hay que poner ({@code ponerProducto}: null si ningún móvil de ese color está en NSYS) y el SKU nuevo
 *  (null: sin SKU en el maestro). {@code desde}: la fecha del cambio vigente más antiguo del móvil (hora de Madrid). */
public record FilaPorPasar(String imeiSn, @Schema(nullable = true) String modelo,
                           @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                           @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                           @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                           @Schema(nullable = true) String sku, List<PorQueTaller> porQue, LocalDateTime desde) {}
```

Crea `OpcionColor.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un color del maestro para el diálogo «Corregir…» (spec 0.9.21 §2.3) con el producto que habría que poner en NSYS: el de
 *  ahora si es el color de NSYS; si no, el más usado de ese color; null si ningún móvil lo tiene. */
public record OpcionColor(String color, @Schema(nullable = true) String producto) {}
```

Crea `OpcionesTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Lo que necesita el diálogo «Corregir color, grado o batería…» (spec 0.9.21 §2.3): el producto actual del móvil, los
 *  colores del maestro para su modelo, capacidad y eSIM (vacío sin modelo en el maestro) y los SKU por color y grado. */
public record OpcionesTaller(@Schema(nullable = true) String productoActual, List<OpcionColor> colores,
                             List<SkuMaestro> skus) {}
```

En `TallerNsys.java`:

1. Import: añade `java.util.Arrays`.
2. Antes de la sección `// ---------------------------------------------------------------- utilidades`, añade:

```java
    // ---------------------------------------------------------------- «Por pasar a NSYS» y opciones del diálogo

    /** La lista «Por pasar a NSYS» (spec §2.4): cada móvil con algo del taller vigente, de cualquier modelo, de su cambio más
     *  antiguo al más reciente. Un móvil que ya no está en el espejo no sale. */
    public List<FilaPorPasar> porPasar() {
        List<CambioTaller> vigentes = tallerDao.vigentes();
        if (vigentes.isEmpty()) return List.of();
        Map<String, MovilNsys> masReciente = masRecientes(vigentes);
        Supplier<Maestro> maestro = perezoso(() -> new Maestro(nsysDao.cargarProductos()));
        Map<List<Object>, Map<String, String>> goods = new HashMap<>();
        List<FilaPorPasar> filas = new ArrayList<>();
        porImei(vigentes).forEach((clave, cambios) -> {
            MovilNsys m = masReciente.get(clave);
            if (m == null) return;
            LoQueVale.Valor v = LoQueVale.de(m, cambios, maestro);
            // Los móviles del mismo modelo, capacidad y eSIM comparten la búsqueda del producto de cada color.
            String producto = v.colorNsys() == null ? m.goods()
                    : goods.computeIfAbsent(Arrays.asList(m.modelo(), m.modeloMaestro(), m.capacidad(), m.esEsim()),
                            k -> nsysDao.goodsMasUsados(m)).get(v.color());
            filas.add(new FilaPorPasar(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.goods(), m.grade(),
                    producto, v.grade(), v.sku(), cambios.stream().map(TallerNsys::porQue).toList(), cambios.get(0).fecha()));
        });
        return filas;
    }

    private static PorQueTaller porQue(CambioTaller c) {
        return new PorQueTaller(c.dato().name(), c.origen(), c.usuario(), c.motivo(), c.idRep());
    }

    /** Lo que necesita el diálogo «Corregir…» (spec §2.3): los colores del maestro para el modelo, capacidad y eSIM del móvil,
     *  cada uno con el producto que habría que poner en NSYS (el de ahora para el color de NSYS), y los SKU por color y grado. */
    public OpcionesTaller opciones(String imeiSn) {
        MovilNsys m = delEspejo(imeiSn);
        Maestro maestro = new Maestro(nsysDao.cargarProductos());
        List<String> colores = maestro.colores(m);
        Map<String, String> goods = colores.isEmpty() ? Map.of() : nsysDao.goodsMasUsados(m);
        List<OpcionColor> opciones = colores.stream().map(c -> new OpcionColor(c,
                Normalizacion.mismoTexto(c, m.color()) ? m.goods() : goods.get(c))).toList();
        return new OpcionesTaller(m.goods(), opciones, maestro.skus(m));
    }

```

En `StockTelefonosController.java`:

1. Imports: añade `com.reparaciones.servidor.security.UsuarioPrincipal`,
   `org.springframework.security.core.annotation.AuthenticationPrincipal`,
   `org.springframework.web.bind.annotation.DeleteMapping`, `org.springframework.web.bind.annotation.PutMapping` y
   `org.springframework.web.bind.annotation.ResponseStatus`.
2. Tras el método `rastro(…)`, añade:

```java

    /** «Por pasar a NSYS» (spec 0.9.21 §2.4): lo que hay que cambiar a mano en NSYS, de cualquier modelo. */
    @GetMapping("/por-pasar")
    public List<FilaPorPasar> porPasar() {
        return taller.porPasar();
    }

    /** Las opciones del diálogo «Corregir color, grado o batería…» (spec 0.9.21 §2.3). */
    @GetMapping("/{imeiSn}/colores")
    public OpcionesTaller colores(@PathVariable String imeiSn) {
        return taller.opciones(imeiSn);
    }

    /** Corrección a mano (spec 0.9.21 §2.3): {@code color} o {@code grado} null, ese dato no cambia; motivo obligatorio. */
    @PutMapping("/{imeiSn}/taller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void corregir(@PathVariable String imeiSn, @RequestBody PeticionTaller peticion,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        taller.corregir(imeiSn, peticion.color(), peticion.grado(), peticion.motivo(), principal.getIdUsu());
    }

    /** «Volver a lo de NSYS» (spec 0.9.21 §2.3): quita lo del taller del móvil, con el motivo si se ha escrito. */
    @DeleteMapping("/{imeiSn}/taller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void volver(@PathVariable String imeiSn, @RequestBody(required = false) PeticionVolver peticion,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        taller.volver(imeiSn, peticion == null ? null : peticion.motivo(), principal.getIdUsu());
    }
```

3. Tras `record PeticionComprobar(List<String> imeis) {}`, añade:

```java

    /** Del paquete para que los tests lo construyan; esquema StockTelefonosPeticionTaller. El grado, como en NSYS. */
    record PeticionTaller(@Schema(nullable = true) String color, @Schema(nullable = true) String grado, String motivo) {}

    /** Esquema StockTelefonosPeticionVolver. */
    record PeticionVolver(@Schema(nullable = true) String motivo) {}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='TallerNsysTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. Si `refDelCuerpo` del `delete` no encuentra `requestBody`, mirar cómo publica springdoc el
`DELETE /api/reparaciones/{idRep}` (mismo patrón, `@RequestBody(required = false)`) y apuntarlo en el registro antes de tocar
nada.

- [ ] **Step 5: Suite entera del servidor**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total en `.superpowers/sdd/v0921-sku/progress.md`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/FilaPorPasar.java src/main/java/com/reparaciones/servidor/nsys/PorQueTaller.java \
  src/main/java/com/reparaciones/servidor/nsys/OpcionesTaller.java src/main/java/com/reparaciones/servidor/nsys/OpcionColor.java \
  src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java \
  src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java \
  src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: corregir y volver a NSYS por la API, lista por pasar a NSYS y opciones del dialogo (0.9.21)"
```

---

### Task 7: Web — contrato, tipos, API, fábricas, MSW y colores del taller

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (generados)
- Modify: `gestion-reparaciones-web/src/shared/api/client.ts` (alias), `src/shared/api/nsys.ts` (claves)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/api.ts` (entero), `src/modules/taller/imeis/apiNsys.ts` (entero)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts`, `src/test/server.ts`,
  `src/shared/styles/tokens.css`
- Test: `gestion-reparaciones-web/src/modules/telefonos/api.test.tsx`, `src/modules/taller/imeis/apiNsys.test.tsx` (nuevo)

**Interfaces:**
- Consumes: el contrato de las Tasks 1-6 (`/c/Users/dev/Documents/wt/servidor-0921-sku/target/openapi.json`).
- Produces:
  - Alias en `client.ts`: `FilaPorPasar`, `PorQueTaller`, `OpcionesTaller`, `OpcionColor`, `SkuMaestro` y `PeticionTaller`
    (`StockTelefonosPeticionTaller`). `MovilStock` lleva `colorNsys`, `gradoNsys`, `skuNsys`; `FichaMovil`, además, `porPasar`,
    `ponerProducto`, `ponerGrado`; `MovilBuscado`, `colorNsys`; `MovilComprobado`, `colorNsys` y `gradoNsys`; `EventoRastro`,
    `dato`, `origen` y `motivo`.
  - En `src/shared/api/nsys.ts`: `CLAVE_STOCK_TELEFONOS = ['telefonos', 'stock']` y `CLAVE_POR_PASAR_NSYS = ['telefonos', 'por-pasar']`.
  - `usePorPasarNsys(): UseQueryResult<FilaPorPasar[]>` en `modules/telefonos/api.ts`.
  - En `modules/taller/imeis/apiNsys.ts`: `useOpcionesTaller(imei, activo)`, `useCorregirTaller(imei)` (mutación con
    `PeticionTaller`) y `useVolverANsys(imei)` (mutación con `motivo: string | null`).
  - Fábricas: `porQue(p)` y `filaPorPasar(p)` (telefonos); `opcionesTaller(p)` (taller): Negro y Rosa del iPhone 16 128GB con
    los SKU `i16128NegroA100`, `i16128RosaA100` e `i16128NegroB`.
  - MSW por defecto: `GET */api/stock-telefonos/por-pasar` → `[]` (antes de `:imeiSn`).
  - Tokens `bg-taller`, `bg-taller-bg`, `text-taller-text`.

- [ ] **Step 1: Worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git log --oneline -1 origin/main
git worktree add -b dev_v0921_sku_taller /c/Users/dev/Documents/wt/web-0921-sku origin/main
cd /c/Users/dev/Documents/wt/web-0921-sku && npm ci
```

Expected: el mismo commit que `origin/main` (hoy `55db8e9`, la 0.9.20) y `npm ci` sin errores. Apunta el commit en
`.superpowers/sdd/v0921-sku/progress.md`. Línea base: `npm run check` en verde (si algo falla ya en `main`, apuntarlo y avisar).

- [ ] **Step 2: Contrato y tipos**

Run (en `/c/Users/dev/Documents/wt/web-0921-sku`):
`cp /c/Users/dev/Documents/wt/servidor-0921-sku/target/openapi.json api/openapi.json && npm run api:types:offline`
Expected: `schema.d.ts` regenerado; `grep -c "FilaPorPasar\|OpcionesTaller\|StockTelefonosPeticionTaller\|colorNsys" src/shared/api/schema.d.ts`
da más de 0. Si `target/openapi.json` no tiene `FilaPorPasar`, volver a pasar `mvn -q test -Dtest=OpenApiContractTest` en el
worktree del servidor.

En `src/shared/api/client.ts`, tras `export type ProgresoHistorico = components['schemas']['StockTelefonosProgresoHistorico']`, añade:

```ts
/** 0.9.21 (spec «SKU del taller», pieza 1): lo del taller por encima de NSYS. En el stock, la ficha, «Pegar varios IMEIs» y
 *  el buscador, color, grado y SKU son los que valen; `colorNsys`, `gradoNsys` (el grado entero de NSYS, con el 100) y
 *  `skuNsys` traen lo de NSYS cuando el taller lo cambia («—» si NSYS no lo sabe) y null si no. La lista «Por pasar a
 *  NSYS», las opciones del diálogo «Corregir…» y su petición (`color` o `grado` null: no cambia). En el rastro, los eventos
 *  de fuente TALLER llevan `dato` (COLOR o GRADO), `origen` (MANO o REPARACION) y `motivo`; `tecnico` es quién lo hizo. */
export type FilaPorPasar = components['schemas']['FilaPorPasar']
export type PorQueTaller = components['schemas']['PorQueTaller']
export type OpcionesTaller = components['schemas']['OpcionesTaller']
export type OpcionColor = components['schemas']['OpcionColor']
export type SkuMaestro = components['schemas']['SkuMaestro']
export type PeticionTaller = components['schemas']['StockTelefonosPeticionTaller']
```

- [ ] **Step 3: Tests (fallan)**

En `src/modules/telefonos/api.test.tsx`, cambia los imports de la API y de las fábricas por:

```ts
import { useComprobarImeis, useEstadoNsys, useImportacionesNsys, usePorPasarNsys, useStockTelefonos } from './api'
import { estadoNsys, filaPorPasar, listaImportaciones, movil, movilComprobado } from './test/fabrica'
```

y añade al final del `describe`:

```tsx
  it('pide la lista «Por pasar a NSYS» (0.9.21)', async () => {
    server.use(http.get('*/api/stock-telefonos/por-pasar', () => HttpResponse.json([filaPorPasar()])))
    const lista = renderHook(() => usePorPasarNsys(), { wrapper: envoltorio() })
    await waitFor(() => expect(lista.result.current.data).toHaveLength(1))
  })
```

Crea `src/modules/taller/imeis/apiNsys.test.tsx`:

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { crearQueryClient } from '@/shared/api/queryClient'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { guardarSesion } from '@/shared/session/storage'
import { server } from '@/test/server'
import { SESION_SUPER } from '@/test/render'
import { opcionesTaller } from '../test/fabrica'
import { useCorregirTaller, useOpcionesTaller, useVolverANsys } from './apiNsys'

const IMEI = '350000000000001'

function envoltorio() {
  guardarSesion(SESION_SUPER)
  const qc = crearQueryClient({ retry: false })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider>{children}</SessionProvider></QueryClientProvider>
  )
}

describe('api de la ficha: SKU del taller (spec 0.9.21 §2.3)', () => {
  it('las opciones del diálogo salen de /colores y solo con el diálogo abierto', async () => {
    let pedidas = 0
    server.use(http.get('*/api/stock-telefonos/:imeiSn/colores', () => {
      pedidas++
      return HttpResponse.json(opcionesTaller())
    }))
    const cerrado = renderHook(() => useOpcionesTaller(IMEI, false), { wrapper: envoltorio() })
    expect(cerrado.result.current.fetchStatus).toBe('idle')
    const abierto = renderHook(() => useOpcionesTaller(IMEI, true), { wrapper: envoltorio() })
    await waitFor(() => expect(abierto.result.current.data?.colores).toHaveLength(2))
    expect(pedidas).toBe(1)
  })

  it('corregir manda color, grado y motivo por PUT; volver, el motivo por DELETE', async () => {
    const peticiones: unknown[] = []
    server.use(
      http.put('*/api/stock-telefonos/:imeiSn/taller', async ({ request, params }) => {
        peticiones.push(['PUT', params.imeiSn, await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
      http.delete('*/api/stock-telefonos/:imeiSn/taller', async ({ request, params }) => {
        peticiones.push(['DELETE', params.imeiSn, await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const corregir = renderHook(() => useCorregirTaller(IMEI), { wrapper: envoltorio() })
    await corregir.result.current.mutateAsync({ color: null, grado: 'A100', motivo: 'batería nueva' })
    const volver = renderHook(() => useVolverANsys(IMEI), { wrapper: envoltorio() })
    await volver.result.current.mutateAsync(null)
    expect(peticiones).toEqual([
      ['PUT', IMEI, { color: null, grado: 'A100', motivo: 'batería nueva' }],
      ['DELETE', IMEI, { motivo: null }],
    ])
  })
})
```

Run: `npx vitest run src/modules/telefonos/api.test.tsx src/modules/taller/imeis/apiNsys.test.tsx`
Expected: FAIL (no existen `usePorPasarNsys`, `useOpcionesTaller`… ni las fábricas nuevas). `npx tsc -b` da errores en las
fábricas (faltan `colorNsys`, `porPasar`, `dato`…).

- [ ] **Step 4: Implementación**

En `src/shared/api/nsys.ts`, tras la línea `import { api, type MovilBuscado } from './client'`, añade:

```ts

/** Claves de consulta del stock de teléfonos que también recarga la ficha del IMEI al corregir lo del taller (0.9.21): viven
 *  aquí porque un módulo no importa de otro. */
export const CLAVE_STOCK_TELEFONOS = ['telefonos', 'stock'] as const
export const CLAVE_POR_PASAR_NSYS = ['telefonos', 'por-pasar'] as const
```

Sustituye `src/modules/telefonos/api.ts` entero por:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type EstadoNsys, type FilaPorPasar, type ListaImportaciones, type MovilComprobado, type MovilStock } from '@/shared/api/client'
import { CLAVE_POR_PASAR_NSYS, CLAVE_STOCK_TELEFONOS } from '@/shared/api/nsys'
import { useIntervaloRefresco } from '@/shared/api/refresco'

export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const
export const CLAVE_IMPORTACIONES_NSYS = ['telefonos', 'importaciones'] as const
/** Como mucho los IMEIs que se comprueban de una vez (spec 0.9.18 §4.9; el servidor da 400 por encima). */
export const MAX_IMEIS = 500

/** GET /api/stock-telefonos: los móviles que están en la empresa, con sus trabajos abiertos (0.9.18) y lo que vale del taller
 *  (0.9.21). Los filtros se aplican en la pantalla (spec §8). Sondea como las demás vistas: la importación trae datos nuevos
 *  cada 5 min. */
export function useStockTelefonos(): UseQueryResult<MovilStock[]> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_STOCK_TELEFONOS,
    queryFn: async () => (await api.GET('/api/stock-telefonos')).data ?? [],
    refetchInterval: intervalo,
  })
}

/** GET /api/stock-telefonos/estado: hora de la última exportación de NSYS y nivel del aviso. Sin capa de carga ni diálogo
 *  de error: lo enseñan la píldora de la cabecera («no se pudo comprobar») y, desde la 0.9.18, el indicador de la barra
 *  superior en todas las vistas. `activo`: el indicador solo pregunta para SUPERTECNICO y ADMIN (al TECNICO le daría 403). */
export function useEstadoNsys(activo = true): UseQueryResult<EstadoNsys | null> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: CLAVE_ESTADO_NSYS,
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/estado')).data ?? null,
    refetchInterval: intervalo,
    meta: { sinCapa: true, silenciarError: true },
  })
}

/** GET /api/stock-telefonos/importaciones (spec 0.9.18 §4.11): las 20 últimas del Items actual y el progreso del histórico.
 *  Lo monta el popover de la píldora solo mientras está abierto: pide al abrirse (`refetchOnMount: 'always'`) y no vuelve a
 *  pedir mientras sigue abierto (sin sondeo ni recarga por foco); `gcTime: 0` olvida la respuesta al cerrarlo. */
export function useImportacionesNsys(): UseQueryResult<ListaImportaciones | null> {
  return useQuery({
    queryKey: CLAVE_IMPORTACIONES_NSYS,
    queryFn: async () => (await api.GET('/api/stock-telefonos/importaciones', { params: { query: { limite: 20 } } })).data ?? null,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
    meta: { sinCapa: true, silenciarError: true },
  })
}

/** POST /api/stock-telefonos/comprobar (spec 0.9.18 §4.9): una entrada por IMEI pedido. Es una lectura, así que va como
 *  consulta (sin Idempotency-Key ni invalidaciones): sale sola al cambiar la lista y no pregunta sin IMEIs ni con más de
 *  {@link MAX_IMEIS}. El diálogo enseña «Comprobando…» en vez de la capa de carga. */
export function useComprobarImeis(imeis: string[]): UseQueryResult<MovilComprobado[]> {
  return useQuery({
    queryKey: ['telefonos', 'comprobar', imeis],
    enabled: imeis.length > 0 && imeis.length <= MAX_IMEIS,
    queryFn: async () => (await api.POST('/api/stock-telefonos/comprobar', { body: { imeis } })).data ?? [],
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    meta: { sinCapa: true },
  })
}

/** GET /api/stock-telefonos/por-pasar (spec 0.9.21 §2.4): lo que hay que cambiar a mano en NSYS, para la pestaña y su número.
 *  Sondea como el stock: la lista se vacía sola con cada importación. */
export function usePorPasarNsys(): UseQueryResult<FilaPorPasar[]> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_POR_PASAR_NSYS,
    queryFn: async () => (await api.GET('/api/stock-telefonos/por-pasar')).data ?? [],
    refetchInterval: intervalo,
    meta: { sinCapa: true },
  })
}
```

Sustituye `src/modules/taller/imeis/apiNsys.ts` entero por:

```ts
import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { api, type EventoRastro, type FichaMovil, type OpcionesTaller, type PeticionTaller } from '@/shared/api/client'
import { CLAVE_POR_PASAR_NSYS, CLAVE_STOCK_TELEFONOS } from '@/shared/api/nsys'

/** Lecturas del espejo de NSYS para el detalle y la lista de IMEIs (0.9.17). Solo SUPERTECNICO y ADMIN las piden
 *  (`activo`). La búsqueda de móviles es `useBuscarMoviles` de `shared/api/nsys.ts`. Desde la 0.9.21, también la corrección
 *  a mano de lo del taller (spec «SKU del taller» §2.3). */

const claveFicha = (imei: string) => ['imeis', 'ficha', imei] as const
const claveRastro = (imei: string) => ['imeis', 'rastro', imei] as const
const claveOpciones = (imei: string) => ['imeis', 'opciones-taller', imei] as const

/** La ficha de NSYS de un IMEI. Un 404 (NSYS no lo conoce) es normal: sin diálogo, sin reintentos y sin ficha. */
export function useFichaMovil(imei: string, activo: boolean): UseQueryResult<FichaMovil | null> {
  return useQuery({
    queryKey: claveFicha(imei),
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/{imeiSn}', { params: { path: { imeiSn: imei } } })).data ?? null,
    retry: false,
    meta: { silenciarError: true, sinCapa: true },
  })
}

export function useRastro(imei: string, activo: boolean): UseQueryResult<EventoRastro[]> {
  return useQuery({
    queryKey: claveRastro(imei),
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/{imeiSn}/rastro', { params: { path: { imeiSn: imei } } })).data ?? [],
    meta: { sinCapa: true },
  })
}

/** GET /api/stock-telefonos/{imeiSn}/colores: los colores del maestro con su producto y los SKU por color y grado, para el
 *  diálogo «Corregir…»; solo mientras está abierto. */
export function useOpcionesTaller(imei: string, activo: boolean): UseQueryResult<OpcionesTaller | null> {
  return useQuery({
    queryKey: claveOpciones(imei),
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/{imeiSn}/colores', { params: { path: { imeiSn: imei } } })).data ?? null,
    meta: { sinCapa: true },
  })
}

/** Tras corregir o volver a NSYS, bien o mal: la ficha, el rastro y las opciones del IMEI, el stock de teléfonos y la lista
 *  «Por pasar a NSYS». No espera a la recarga, para que el error (si lo hay) salga al momento. */
function useRecargarTrasCorregir(imei: string): () => void {
  const qc = useQueryClient()
  return () => {
    for (const queryKey of [claveFicha(imei), claveRastro(imei), claveOpciones(imei), CLAVE_STOCK_TELEFONOS, CLAVE_POR_PASAR_NSYS])
      void qc.invalidateQueries({ queryKey })
  }
}

/** PUT /api/stock-telefonos/{imeiSn}/taller (spec 0.9.21 §2.3). Un 400 (sin cambios, motivo…) sale en el diálogo de error. */
export function useCorregirTaller(imei: string) {
  const recargar = useRecargarTrasCorregir(imei)
  return useMutation({
    mutationFn: (cuerpo: PeticionTaller) => api.PUT('/api/stock-telefonos/{imeiSn}/taller', { params: { path: { imeiSn: imei } }, body: cuerpo }),
    onSettled: recargar,
  })
}

/** DELETE /api/stock-telefonos/{imeiSn}/taller: «Volver a lo de NSYS», con el motivo si se ha escrito. */
export function useVolverANsys(imei: string) {
  const recargar = useRecargarTrasCorregir(imei)
  return useMutation({
    mutationFn: (motivo: string | null) => api.DELETE('/api/stock-telefonos/{imeiSn}/taller', { params: { path: { imeiSn: imei } }, body: { motivo } }),
    onSettled: recargar,
  })
}
```

En `src/modules/telefonos/test/fabrica.ts`:

1. En el `import type { … } from '@/shared/api/client'`, añade `FilaPorPasar` y `PorQueTaller`.
2. En `movil`, tras `trabajos: [],` añade `colorNsys: null, gradoNsys: null, skuNsys: null,`.
3. En `movilBuscado`, tras `fechaVenta: null,` añade `colorNsys: null,`.
4. En `movilComprobado`, tras `trabajos: [],` añade `colorNsys: null, gradoNsys: null,`.
5. Al final del fichero, añade:

```ts
/** 0.9.21: por qué un dato está por pasar a NSYS; por defecto, el grado a mano de «Técnico 1». */
export function porQue(p: Partial<PorQueTaller> = {}): PorQueTaller {
  return { dato: 'GRADO', origen: 'MANO', usuario: 'Técnico 1', motivo: 'batería nueva', idRep: null, ...p }
}

/** Una fila de «Por pasar a NSYS»: un iPhone 13 128GB Azul que pasa de B a A100 desde el 20/10 a las 10:31 (Madrid). */
export function filaPorPasar(p: Partial<FilaPorPasar> = {}): FilaPorPasar {
  return {
    imeiSn: '350000000000001', modelo: '13', modeloMaestro: '13', capacidad: '128GB', productoNsys: 'Apple iPhone 13 128GB Blue',
    gradoNsys: 'B', ponerProducto: 'Apple iPhone 13 128GB Blue', ponerGrado: 'A100', sku: 'i13128AzulA100', porQue: [porQue()],
    desde: '2026-10-20T10:31:00', ...p,
  }
}
```

En `src/modules/taller/test/fabrica.ts`:

1. En el `import type { … } from '@/shared/api/client'`, añade `OpcionesTaller`.
2. En `fichaMovil`, tras `pendienteRevisionDesde: null,` añade
   `colorNsys: null, gradoNsys: null, skuNsys: null, porPasar: false, ponerProducto: null, ponerGrado: null,`.
3. En `eventoRastro`, tras `revision: null,` añade `dato: null, origen: null, motivo: null,`.
4. Al final del fichero, añade:

```ts
/** 0.9.21: las opciones del diálogo «Corregir…» para la ficha de `fichaMovil` (iPhone 16 128GB Negro A100). */
export function opcionesTaller(p: Partial<OpcionesTaller> = {}): OpcionesTaller {
  return {
    productoActual: 'Apple iPhone 16 128GB Black',
    colores: [{ color: 'Negro', producto: 'Apple iPhone 16 128GB Black' }, { color: 'Rosa', producto: 'Apple iPhone 16 128GB Pink' }],
    skus: [
      { color: 'Negro', grado: 'A100', sku: 'i16128NegroA100' },
      { color: 'Rosa', grado: 'A100', sku: 'i16128RosaA100' },
      { color: 'Negro', grado: 'B', sku: 'i16128NegroB' },
    ],
    ...p,
  }
}
```

En `src/test/server.ts`, justo antes de `http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([])),`, añade:

```ts
  // 0.9.21: el stock de teléfonos pide la lista «Por pasar a NSYS» para el número de su pestaña: por defecto, vacía. Va antes
  // que «:imeiSn», que también la cubriría (y daría 404).
  http.get('*/api/stock-telefonos/por-pasar', () => HttpResponse.json([])),
```

y al comentario de cabecera de `server` añade al final: «Y desde la 0.9.21, la lista "Por pasar a NSYS" (vacía).»

En `src/shared/styles/tokens.css`, tras `--color-aviso-warn: #F5B942;` (última línea antes de la `}` del `@theme`), añade:

```css
  /* SKU del taller (0.9.21, prototipo «SKU del taller»): la marca «taller», el punto y la fuente TALLER del rastro, la vista
     previa del diálogo «Corregir…» y el texto de «Por pasar a NSYS». */
  --color-taller: #7C3AED;
  --color-taller-bg: #F1EAFE;
  --color-taller-text: #6D28D9;
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos src/modules/taller/imeis src/shared/api`
Expected: `tsc` limpio y todo en verde (los tests de antes no cambian: los campos nuevos van a null).

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/api/nsys.ts src/modules/telefonos/api.ts \
  src/modules/telefonos/api.test.tsx src/modules/taller/imeis/apiNsys.ts src/modules/taller/imeis/apiNsys.test.tsx \
  src/modules/telefonos/test/fabrica.ts src/modules/taller/test/fabrica.ts src/test/server.ts src/shared/styles/tokens.css
git commit -m "feat: contrato y api del SKU del taller, lista por pasar a NSYS y correccion a mano (0.9.21)"
```

---

### Task 8: Web — la marca «taller» y lo que vale en el stock, «Pegar varios IMEIs» y el buscador

**Files:**
- Create: `gestion-reparaciones-web/src/shared/ui/MarcaTaller.tsx`, `src/shared/ui/MarcaTaller.test.tsx`
- Modify: `gestion-reparaciones-web/src/shared/lib/telefonoNsys.ts` (+ `telefonoNsys.test.ts`)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/columnas.tsx` (entero), `ComprobarImeis.tsx`, `BuscadorImei.tsx`
- Test: `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, `ComprobarImeis.test.tsx`, `modelo.test.ts`

**Interfaces:**
- Consumes: los campos `colorNsys`, `gradoNsys`, `skuNsys` (Task 7).
- Produces:
  - `MarcaTaller({ nsys, enLinea?, children })` en `@/shared/ui/MarcaTaller`: sin `nsys` (null), el valor tal cual; con él,
    «<valor> taller» y «NSYS: <nsys>» debajo (`block`) o al lado (`enLinea`).
  - En `@/shared/lib/telefonoNsys`: `SIN_SKU_MAESTRO = 'sin SKU en el maestro'`,
    `SIN_PRODUCTO_NSYS = '(ningún móvil de ese color en NSYS: búscalo a mano)'` y
    `textoPoner(producto, grado): string` («<producto> · grado <grado>»).

- [ ] **Step 1: Tests (fallan)**

Crea `src/shared/ui/MarcaTaller.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MarcaTaller } from './MarcaTaller'

describe('MarcaTaller (spec 0.9.21 §2.4)', () => {
  it('sin nada del taller, el valor tal cual', () => {
    const { container } = render(<MarcaTaller nsys={null}>Azul</MarcaTaller>)
    expect(container).toHaveTextContent(/^Azul$/)
    expect(screen.queryByText('taller')).not.toBeInTheDocument()
  })

  it('con lo del taller, la marca y lo de NSYS debajo', () => {
    const { container } = render(<MarcaTaller nsys="Azul">Rojo</MarcaTaller>)
    expect(screen.getByText('taller')).toHaveClass('bg-taller')
    expect(screen.getByText('NSYS: Azul')).toHaveClass('block')
    expect(container).toHaveTextContent('Rojo taller')
  })

  it('en línea, lo de NSYS a la derecha', () => {
    const { container } = render(<MarcaTaller nsys="B" enLinea>A</MarcaTaller>)
    expect(screen.getByText('NSYS: B')).not.toHaveClass('block')
    expect(container).toHaveTextContent('A taller NSYS: B')
  })
})
```

En `src/shared/lib/telefonoNsys.test.ts`, cambia el import por
`import { clasesUbicacion, diaNsys, diasDesdeNsys, textoGrado, nombreModeloNsys, textoEstadoMovil, textoPoner } from './telefonoNsys'`
y añade al final:

```ts
describe('lo que hay que poner en NSYS (0.9.21)', () => {
  it('producto y grado; sin producto, buscarlo a mano', () => {
    expect(textoPoner('Apple iPhone 13 128GB Blue', 'A100')).toBe('Apple iPhone 13 128GB Blue · grado A100')
    expect(textoPoner(null, 'B')).toBe('(ningún móvil de ese color en NSYS: búscalo a mano) · grado B')
    expect(textoPoner('Apple iPhone 13 128GB Blue', null)).toBe('Apple iPhone 13 128GB Blue · grado —')
  })
})
```

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, añade al final del `describe('Stock de teléfonos (spec pieza 1 §9.2 y spec 0.9.18 §4)', …)`:

```tsx
  it('lo del taller: color, grado y SKU con la marca y lo de NSYS debajo (spec 0.9.21 §2.4)', async () => {
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json([
      movil({ imeiSn: '350000000000011', color: 'Rojo', colorNsys: 'Azul', grado: 'A', bateriaNueva: true, gradoNsys: 'B',
        sku: 'i13128RojoA100', skuNsys: 'i13128AzulB', dias: 1 }),
      movil({ imeiSn: '350000000000012', grado: 'C', gradoNsys: 'B', sku: null, skuNsys: 'i13128AzulB', dias: 2 }),
      movil({ imeiSn: '350000000000013', dias: 3 }),
    ])))
    abrir()
    await primeraFila()
    const [rojo, sinSku, normal] = filas()
    expect(celda(rojo, 'Color')).toHaveTextContent('Rojo taller')
    expect(within(celda(rojo, 'Color')).getByText('NSYS: Azul')).toBeInTheDocument()
    expect(within(celda(rojo, 'Grado')).getByText('NSYS: B')).toBeInTheDocument()
    expect(within(celda(rojo, 'SKU')).getByText('NSYS: i13128AzulB')).toBeInTheDocument()
    expect(celda(sinSku, 'SKU')).toHaveTextContent('sin SKU en el maestro')
    expect(within(celda(sinSku, 'Color')).queryByText('taller')).not.toBeInTheDocument()
    expect(within(normal).queryByText('taller')).not.toBeInTheDocument()
    expect(celda(normal, 'SKU')).toHaveTextContent('i13128AzulA')
  })

  it('el buscador de IMEI dice el color que vale con la marca (spec 0.9.21 §2.4)', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      movilBuscado({ imeiSn: '353918700112233', color: 'Rojo', colorNsys: 'Azul' }),
    ])))
    abrir()
    await user.type(await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }), '3539')
    expect(await screen.findByRole('button', { name: /^353918700112233 · iPhone 13 128GB Rojo taller NSYS: Azul · Listos$/ }))
      .toBeInTheDocument()
  })
```

En `src/modules/telefonos/stock/ComprobarImeis.test.tsx`, añade al final del `describe`:

```tsx
  it('lo del taller: el color y el grado que valen, con la marca (spec 0.9.21 §2.4)', async () => {
    server.use(http.post('*/api/stock-telefonos/comprobar', () => HttpResponse.json([
      movilComprobado({ color: 'Rojo', colorNsys: 'Azul', grado: 'A', gradoNsys: 'B' }),
    ])))
    abrirDialogo('350000000000001')
    const tabla = await within(await dialogo()).findByRole('table', { name: 'Resultado' })
    const fila = within(tabla).getAllByRole('row')[1]
    expect(within(fila).getByText('NSYS: Azul')).toBeInTheDocument()
    expect(within(fila).getByText('NSYS: B')).toBeInTheDocument()
    expect(within(fila).getAllByText('taller')).toHaveLength(2)
  })
```

En `src/modules/telefonos/stock/modelo.test.ts`, añade al final del fichero:

```ts
describe('lo del taller en las cuentas (spec 0.9.21 §2.4)', () => {
  it('los filtros y la tabla color × grado cuentan el color y el grado que valen, no los de NSYS', () => {
    const rojo = visto({ imeiSn: '350000000000021', color: 'Rojo', colorNsys: 'Azul', grado: 'A', gradoNsys: 'C' })
    const m = matriz([rojo], FILTROS_VACIOS)
    expect(m.colores).toEqual(['Rojo'])
    expect(m.grados).toEqual(['A'])
    expect(opciones([rojo], 'color', FILTROS_VACIOS)).toEqual([{ valor: 'Rojo', cuenta: 1 }])
  })
})
```

Run: `npx vitest run src/shared/ui/MarcaTaller.test.tsx src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/stock`
Expected: FAIL (no existen `MarcaTaller` ni `textoPoner`; las celdas no llevan la marca). El de `modelo.test.ts` ya pasa: los
datos del servidor ya traen lo que vale (se deja como prueba de la regla).

- [ ] **Step 2: Implementación**

Crea `src/shared/ui/MarcaTaller.tsx`:

```tsx
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'

type Props = { nsys: string | null | undefined; enLinea?: boolean; children: ReactNode }

/** Lo del taller por encima de NSYS (0.9.21, spec «SKU del taller» §2.4; variante «"NSYS: Azul" debajo» del prototipo): el
 *  valor que vale con la marca «taller» y, debajo, en pequeño y gris, «NSYS: <valor>». Sin `nsys` (null: el taller no lo
 *  cambia), el valor tal cual, como antes. `enLinea`: «NSYS: …» a la derecha (la ficha del IMEI y el buscador). */
export function MarcaTaller({ nsys, enLinea = false, children }: Props) {
  if (nsys == null) return <>{children}</>
  return (
    <span className={enLinea ? undefined : 'inline-block align-top leading-tight'}>
      {children}{' '}
      <span className="rounded bg-taller px-1 align-[1px] text-[10px] leading-[15px] font-bold text-superficie">taller</span>
      {enLinea && ' '}
      <span title={`NSYS: ${nsys}`} className={cn('text-[11px] font-normal text-azul-gris', !enLinea && 'block')}>{`NSYS: ${nsys}`}</span>
    </span>
  )
}
```

En `src/shared/lib/telefonoNsys.ts`, añade al final:

```ts

/** 0.9.21 (spec «SKU del taller» §2.1): un SKU que el maestro no da para el color y el grado que valen. */
export const SIN_SKU_MAESTRO = 'sin SKU en el maestro'
/** 0.9.21 (§2.4): el producto que poner en NSYS cuando ningún móvil del espejo tiene ese color. */
export const SIN_PRODUCTO_NSYS = '(ningún móvil de ese color en NSYS: búscalo a mano)'

/** «<producto> · grado <grado>»: lo que hay que poner en NSYS (la ficha, el diálogo «Corregir…» y la lista «Por pasar a
 *  NSYS»). Sin producto, el aviso de buscarlo a mano. */
export function textoPoner(producto: string | null | undefined, grado: string | null | undefined): string {
  return `${producto || SIN_PRODUCTO_NSYS} · grado ${grado || '—'}`
}
```

Sustituye `src/modules/telefonos/stock/columnas.tsx` entero por:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import { clasesUbicacion, SIN_SKU_MAESTRO, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { MarcaTaller } from '@/shared/ui/MarcaTaller'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { CeldaQuien } from './CeldaQuien'
import { capacidadDe, muestraColor, partesSku, textoCapacidad, type FiltroSku, type Movil } from './modelo'
import { PildoraGrado } from './PildoraGrado'

const MARCA = 'ml-1 inline-block rounded border px-1 align-[1px] text-[10px] font-bold'

/** `conCapacidad`: con la pestaña «Todas», la columna «Cap.» detrás del IMEI (spec §4.3). `sku`: el filtro de SKU, cuya
 *  parte buscada se resalta en la columna SKU (§4.2). */
export type OpcionesColumnas = { conCapacidad?: boolean; sku?: FiltroSku | null }

/** Columnas de la tabla de móviles (0.9.18, spec §4.6): IMEI · (Cap.) · Color · Grado (con «100» si la batería es nueva y
 *  «eSIM») · SKU · Ubicación (con «NO CUENTA · …» si no cuenta como stock) · Quién lo tiene · Días. El IMEI es un botón
 *  que abre el detalle. Desde la 0.9.21 (spec «SKU del taller» §2.4), color, grado y SKU son los que valen: si el taller los
 *  cambia, llevan la marca «taller» y, debajo, lo de NSYS. */
export function columnasMoviles(abrir: (imeiSn: string) => void, { conCapacidad = false, sku = null }: OpcionesColumnas = {}): ColumnDef<Movil>[] {
  const imei: ColumnDef<Movil> = {
    id: 'imei', header: 'IMEI', size: 125, maxSize: 160,
    cell: ({ row }) => (
      <button type="button" aria-label={`Abrir ${row.original.imeiSn}`} onClick={(e) => { e.stopPropagation(); abrir(row.original.imeiSn) }}
        className="cursor-pointer font-mono text-[11.5px] text-azul-noche hover:underline">{row.original.imeiSn}</button>
    ),
  }
  const capacidad: ColumnDef<Movil> = { id: 'capacidad', header: 'Cap.', size: 55, maxSize: 80, accessorFn: (m) => textoCapacidad(capacidadDe(m)) }
  return [
    imei,
    ...(conCapacidad ? [capacidad] : []),
    {
      // En línea (no flex), para que la celda corte con «…» un color largo a 1366; el nombre entero, al pasar el ratón.
      id: 'color', header: 'Color', size: 90,
      cell: ({ row }) => (
        <MarcaTaller nsys={row.original.colorNsys}>
          <span title={row.original.color ?? undefined}>
            <span aria-hidden className="mr-1.5 inline-block h-2.5 w-2.5 rounded-full border border-pill-borde align-[-1px]" style={{ background: muestraColor(row.original.color) }} />
            {row.original.color ?? '—'}
          </span>
        </MarcaTaller>
      ),
    },
    {
      // Si no caben las marcas, la celda las corta con «…»: al pasar el ratón se leen todas.
      id: 'grado', header: 'Grado', size: 95, maxSize: 120,
      cell: ({ row }) => (
        <MarcaTaller nsys={row.original.gradoNsys}>
          <span title={[row.original.grado ?? '—', row.original.bateriaNueva && 'batería nueva', row.original.esEsim && 'eSIM'].filter(Boolean).join(' · ')}>
            <PildoraGrado grado={row.original.grado} />
            {row.original.bateriaNueva && <span title="Batería nueva" className={cn(MARCA, 'border-fila-recibido-brd text-fila-recibido-brd')}>100</span>}
            {row.original.esEsim && <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>eSIM</span>}
          </span>
        </MarcaTaller>
      ),
    },
    {
      id: 'sku', header: 'SKU', size: 135,
      cell: ({ row }) => {
        const valor = row.original.sku
        const partes = valor ? partesSku(valor, sku) : null
        // Sin SKU por un cambio del taller (el maestro no lo da): «sin SKU en el maestro»; sin SKU de NSYS, la raya de siempre.
        const vacio = row.original.skuNsys != null ? <i className="font-sans text-fila-solicitud-brd">{SIN_SKU_MAESTRO}</i> : '—'
        return (
          <MarcaTaller nsys={row.original.skuNsys}>
            <span className="font-mono text-[11.5px]" title={valor ?? undefined}>
              {partes ? <><mark className="bg-amarillo/60 text-azul-noche">{partes[0]}</mark>{partes[1]}</> : (valor ?? vacio)}
            </span>
          </MarcaTaller>
        )
      },
    },
    {
      id: 'ubicacion', header: 'Ubicación', size: 145,
      cell: ({ row }) => (
        <span className="whitespace-normal">
          <span className={cn(CLASES_PILDORA, clasesUbicacion(row.original.ubicacion))}>{row.original.ubicacion ?? '—'}</span>
          {(row.original.estado === 'NO_CUENTA' || row.original.estado === 'DE_PEDIDO') && (
            <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>{`NO CUENTA · ${textoEstadoMovil(row.original.estado)}`}</span>
          )}
        </span>
      ),
    },
    { id: 'quien', header: 'Quién lo tiene', size: 160, cell: ({ row }) => <CeldaQuien quien={row.original.quien} /> },
    { id: 'dias', header: 'Días', size: 45, maxSize: 60, accessorFn: (m) => m.dias ?? '' },
  ]
}
```

En `src/modules/telefonos/stock/ComprobarImeis.tsx`:

1. Imports: añade `import { MarcaTaller } from '@/shared/ui/MarcaTaller'`.
2. Sustituye

```tsx
                      <td className="px-2 py-1">{m?.color ?? ''}</td>
                      <td className="px-2 py-1">{m && <PildoraGrado grado={m.grado} />}</td>
```

por

```tsx
                      <td className="px-2 py-1">{m && <MarcaTaller nsys={m.colorNsys}>{m.color ?? ''}</MarcaTaller>}</td>
                      <td className="px-2 py-1">{m && <MarcaTaller nsys={m.gradoNsys}><PildoraGrado grado={m.grado} /></MarcaTaller>}</td>
```

En `src/modules/telefonos/stock/BuscadorImei.tsx`:

1. Imports: añade `import { MarcaTaller } from '@/shared/ui/MarcaTaller'`.
2. Sustituye

```tsx
                {`${m.imeiSn} · ${[nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad, m.color].filter(Boolean).join(' ')} · ${textoBuscado(m)}`}
```

por

```tsx
                {`${m.imeiSn} · ${[nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad].filter(Boolean).join(' ')}`}
                {m.color && <>{' '}<MarcaTaller enLinea nsys={m.colorNsys}>{m.color}</MarcaTaller></>}
                {` · ${textoBuscado(m)}`}
```

- [ ] **Step 3: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/shared/ui/MarcaTaller.test.tsx src/shared/lib/telefonoNsys.test.ts src/modules/telefonos`
Expected: `tsc` limpio y todo en verde (también el test de siempre del buscador: sin `colorNsys`, el nombre del botón es el mismo).

- [ ] **Step 4: Commit**

```bash
git add src/shared/ui/MarcaTaller.tsx src/shared/ui/MarcaTaller.test.tsx src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts \
  src/modules/telefonos/stock/columnas.tsx src/modules/telefonos/stock/ComprobarImeis.tsx src/modules/telefonos/stock/BuscadorImei.tsx \
  src/modules/telefonos/stock/StockTelefonosPage.test.tsx src/modules/telefonos/stock/ComprobarImeis.test.tsx src/modules/telefonos/stock/modelo.test.ts
git commit -m "feat: marca taller con lo de NSYS al lado en el stock, pegar varios IMEIs y el buscador (0.9.21)"
```

---

### Task 9: Web — la ficha del IMEI y el rastro con lo del taller

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/FichaMovil.tsx` (entero), `rastro.ts`, `PanelRastro.tsx`
- Test: `src/modules/taller/imeis/FichaMovil.test.tsx`, `rastro.test.ts`, `PanelRastro.test.tsx`

**Interfaces:**
- Consumes: `MarcaTaller`, `SIN_SKU_MAESTRO` y `textoPoner` (Task 8); los campos nuevos de `FichaMovil` y `EventoRastro` (Task 7).
- Produces:
  - `FichaMovil` acepta `onCorregir?: () => void`: con él, el botón «Corregir color, grado o batería…».
  - `verEvento` compone `TALLER_CAMBIO`, `TALLER_VUELTA`, `TALLER_PASADO` y `TALLER_MANDA_NSYS` (color `bg-taller`,
    `bg-taller`, `bg-fila-recibido-brd` y `bg-ambar`).
  - `PanelRastro`: fuente «TALLER» con `bg-taller-bg text-taller-text`; leyenda «Lo del taller».

- [ ] **Step 1: Tests (fallan)**

En `src/modules/taller/imeis/FichaMovil.test.tsx`, cambia los imports por:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { fichaMovil } from '../test/fabrica'
import { FichaMovil } from './FichaMovil'
```

y añade al final del fichero:

```tsx
describe('ficha de NSYS: lo del taller (spec 0.9.21 §2.4)', () => {
  const region = () => screen.getByRole('region', { name: 'Ficha de NSYS' })

  it('color, grado y SKU del taller con la marca y lo de NSYS al lado, y lo que hay que poner en NSYS', () => {
    ficha({ color: 'Rosa', colorNsys: 'Negro', grado: 'A', bateriaNueva: true, gradoNsys: 'B', gradoAnterior: 'C',
      sku: 'i16128RosaA100', skuNsys: 'i16128NegroB', porPasar: true, ponerProducto: 'Apple iPhone 16 128GB Pink', ponerGrado: 'A100' })
    expect(region()).toHaveTextContent('iPhone 16 128GB Rosa taller NSYS: Negro')
    expect(region()).toHaveTextContent('Grado A · batería nueva taller NSYS: B')
    expect(region()).not.toHaveTextContent('antes')
    expect(region()).toHaveTextContent('SKU i16128RosaA100 taller NSYS: i16128NegroB')
    expect(region()).toHaveTextContent('Por pasar a NSYS: Apple iPhone 16 128GB Pink · grado A100')
  })

  it('sin SKU en el maestro y sin ningún móvil de ese color en NSYS', () => {
    ficha({ color: 'Verde', colorNsys: 'Negro', sku: null, skuNsys: 'i16128NegroA100', porPasar: true, ponerProducto: null, ponerGrado: 'A100' })
    expect(region()).toHaveTextContent('SKU sin SKU en el maestro taller NSYS: i16128NegroA100')
    expect(region()).toHaveTextContent('Por pasar a NSYS: (ningún móvil de ese color en NSYS: búscalo a mano) · grado A100')
  })

  it('sin nada del taller, como antes y sin «Por pasar a NSYS»', () => {
    ficha()
    expect(region()).toHaveTextContent('Grado A (antes C) · batería nueva')
    expect(screen.queryByText('taller')).not.toBeInTheDocument()
    expect(screen.queryByText(/Por pasar a NSYS/)).not.toBeInTheDocument()
  })

  it('«Corregir color, grado o batería…» solo si quien pinta la ficha lo ofrece', async () => {
    const { unmount } = ficha()
    expect(screen.queryByRole('button', { name: 'Corregir color, grado o batería…' })).not.toBeInTheDocument()
    unmount()
    const onCorregir = vi.fn()
    render(<FichaMovil ficha={fichaMovil()} eventos={3} onVerRastro={() => {}} onCorregir={onCorregir} />)
    await userEvent.click(screen.getByRole('button', { name: 'Corregir color, grado o batería…' }))
    expect(onCorregir).toHaveBeenCalledTimes(1)
  })
})
```

En `src/modules/taller/imeis/rastro.test.ts`, añade al final del `describe`:

```ts
  it('lo del taller (spec 0.9.21 §2.4)', () => {
    const taller = (p: Parameters<typeof eventoRastro>[0]) =>
      eventoRastro({ fuente: 'TALLER', lote: null, aNsys: null, tecnico: 'Supertécnico 1', origen: 'MANO', ...p })
    expect(verEvento(taller({ tipo: 'TALLER_CAMBIO', dato: 'COLOR', de: 'Azul', a: 'Rojo', motivo: 'chasis rojo' })))
      .toMatchObject({ titulo: 'Taller: color Azul → Rojo (a mano · Supertécnico 1 · «chasis rojo»)', detalle: null, color: 'bg-taller' })
    expect(verEvento(taller({ tipo: 'TALLER_VUELTA', dato: 'GRADO', de: 'A100', a: 'B', motivo: null })).titulo)
      .toBe('Vuelve a lo de NSYS: grado A100 → B (a mano · Supertécnico 1)')
    expect(verEvento(taller({ tipo: 'TALLER_PASADO', dato: 'GRADO', de: 'A100', a: 'A100' })).titulo).toBe('Pasado a NSYS: grado A100')
    expect(verEvento(taller({ tipo: 'TALLER_MANDA_NSYS', dato: 'COLOR', de: 'Rojo', a: 'Verde' })).titulo)
      .toBe('Manda NSYS: color Verde (el del taller, Rojo, deja de valer)')
  })
```

En `src/modules/taller/imeis/PanelRastro.test.tsx`, añade al final del fichero:

```tsx
describe('PanelRastro: lo del taller (spec 0.9.21 §2.4)', () => {
  it('el cambio del taller sale con su fuente y la leyenda lo explica', () => {
    render(<PanelRastro imei="351900000000041" eventos={[eventoRastro({ fuente: 'TALLER', tipo: 'TALLER_CAMBIO', lote: null, aNsys: null,
      dato: 'GRADO', de: 'B', a: 'A100', tecnico: 'Supertécnico 1', origen: 'MANO', motivo: 'batería' })]} onCerrar={() => {}} onIrATrabajo={() => {}} />)
    expect(screen.getByText('Taller: grado B → A100 (a mano · Supertécnico 1 · «batería»)')).toBeInTheDocument()
    expect(screen.getByText('TALLER')).toHaveClass('bg-taller-bg')
    expect(screen.getByText('Lo del taller')).toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/taller/imeis/FichaMovil.test.tsx src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/PanelRastro.test.tsx`
Expected: FAIL (sin marcas, sin «Por pasar a NSYS», sin botón, sin textos del taller en el rastro).

- [ ] **Step 2: Implementación**

Sustituye `src/modules/taller/imeis/FichaMovil.tsx` entero por:

```tsx
import type { FichaMovil as Ficha } from '@/shared/api/client'
import { clasesUbicacion, diaNsys, nombreModeloNsys, SIN_SKU_MAESTRO, textoEstadoMovil, textoPoner } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { MarcaTaller } from '@/shared/ui/MarcaTaller'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { textoRevision } from './rastro'

type Props = { ficha: Ficha; eventos: number | null; onVerRastro: () => void; onCorregir?: () => void }

const BOTON = 'inline-flex cursor-pointer items-center gap-1.5 rounded-3xl border border-azul-noche bg-superficie px-3.5 py-1.5 text-[12px] font-bold text-azul-noche hover:bg-seleccion-suave disabled:cursor-default disabled:opacity-50'

/** Ficha del móvil según NSYS, encima de la vista de trabajos de hoy (0.9.17, spec §9.3), con el botón del rastro.
 *  Desde la 0.9.18, junto a la ubicación, cuándo se revisó o desde cuándo espera. Desde la 0.9.21 (spec «SKU del taller»
 *  §2.4), color, grado y SKU son los que valen, con la marca «taller» y lo de NSYS al lado; «Por pasar a NSYS: …» mientras
 *  NSYS no lo tenga; y, si quien la pinta lo ofrece (`onCorregir`), «Corregir color, grado o batería…». */
export function FichaMovil({ ficha, eventos, onVerRastro, onCorregir }: Props) {
  const venta = ficha.fechaVenta ? diaNsys(ficha.fechaVenta) : null
  const revision = textoRevision(ficha)
  const bateria = ficha.bateriaNueva && ' · batería nueva'
  return (
    <section aria-label="Ficha de NSYS" className="mb-3 flex flex-wrap items-center gap-x-5 gap-y-1.5 rounded-lg bg-superficie px-4 py-3 text-[12.5px] text-azul-medio">
      <span className="font-mono text-[17px] font-extrabold text-azul-noche">{ficha.imeiSn}</span>
      <span>
        {[nombreModeloNsys(ficha.modelo, ficha.modeloMaestro), ficha.capacidad].filter(Boolean).join(' ')}
        {ficha.color && <>{' '}<MarcaTaller enLinea nsys={ficha.colorNsys}>{ficha.color}</MarcaTaller></>}
      </span>
      {/* Con el grado del taller no se dice «(antes …)»: ese grado anterior es el de NSYS (Ajuste 11 del plan). */}
      <span>Grado {ficha.gradoNsys == null
        ? <><b className="text-azul-noche">{ficha.grado || '—'}</b>{ficha.gradoAnterior && ficha.gradoAnterior !== ficha.grado && ` (antes ${ficha.gradoAnterior})`}{bateria}</>
        : <MarcaTaller enLinea nsys={ficha.gradoNsys}><b className="text-azul-noche">{ficha.grado || '—'}</b>{bateria}</MarcaTaller>}
      </span>
      {(ficha.sku || ficha.skuNsys) && (
        <span>SKU <MarcaTaller enLinea nsys={ficha.skuNsys}>
          {ficha.sku ? <b className="font-mono text-azul-noche">{ficha.sku}</b> : <i className="text-fila-solicitud-brd">{SIN_SKU_MAESTRO}</i>}
        </MarcaTaller></span>
      )}
      {ficha.porPasar && <span className="font-semibold text-taller-text">{`Por pasar a NSYS: ${textoPoner(ficha.ponerProducto, ficha.ponerGrado)}`}</span>}
      {ficha.ubicacion && <span className={cn(CLASES_PILDORA, clasesUbicacion(ficha.ubicacion))}>{ficha.ubicacion}</span>}
      {revision && <span className="font-semibold text-azul-medio">{revision}</span>}
      {ficha.estado === 'VENDIDO'
        ? <span className={cn(CLASES_PILDORA, 'bg-azul-noche text-superficie')}>{venta ? `Vendido el ${venta}` : 'Vendido'}</span>
        : <span className={ficha.estado === 'DE_PEDIDO' ? 'font-bold text-fila-urgente-brd' : 'text-azul-gris'}>{textoEstadoMovil(ficha.estado)}</span>}
      <span>Lote <b className="text-azul-noche">{ficha.lote}</b></span>
      {ficha.dias != null && <span>En stock <b className="text-azul-noche">{ficha.dias} {ficha.dias === 1 ? 'día' : 'días'}</b>{ficha.entradas > 1 && ` (${ficha.entradas} entradas)`}</span>}
      <span className="ml-auto flex flex-wrap gap-2">
        {onCorregir && <button type="button" onClick={onCorregir} className={BOTON}>Corregir color, grado o batería…</button>}
        <button type="button" onClick={onVerRastro} disabled={eventos == null} className={BOTON}>
          {eventos == null ? 'Ver rastro' : `Ver rastro (${eventos})`}
        </button>
      </span>
    </section>
  )
}
```

En `src/modules/taller/imeis/rastro.ts`:

1. Tras la línea `const mayuscula = (t: string) => t.charAt(0).toUpperCase() + t.slice(1)`, añade:

```ts
/** 0.9.21 (spec «SKU del taller» §2.4): los cambios del taller por encima de NSYS. */
const DATO: Record<string, string> = { COLOR: 'color', GRADO: 'grado' }
const ORIGEN: Record<string, string> = { MANO: 'a mano', REPARACION: 'por una reparación' }
const dato = (e: EventoRastro) => DATO[e.dato ?? ''] ?? 'dato'

/** «(a mano · Supertécnico 1 · «motivo»)»: cómo, quién y por qué; sin motivo, el paréntesis acaba en el nombre. */
function comoTaller(e: EventoRastro): string {
  return `(${[ORIGEN[e.origen ?? ''] ?? 'a mano', e.tecnico ?? '—', e.motivo ? `«${e.motivo}»` : null].filter(Boolean).join(' · ')})`
}
```

2. En `verEvento`, justo antes de `    default:`, añade:

```ts
    case 'TALLER_CAMBIO':
      return { fecha, titulo: `Taller: ${dato(e)} ${e.de ?? '—'} → ${e.a ?? '—'} ${comoTaller(e)}`, detalle: null, color: 'bg-taller' }
    case 'TALLER_VUELTA':
      return { fecha, titulo: `Vuelve a lo de NSYS: ${dato(e)} ${e.de ?? '—'} → ${e.a ?? '—'} ${comoTaller(e)}`, detalle: null, color: 'bg-taller' }
    case 'TALLER_PASADO':
      return { fecha, titulo: `Pasado a NSYS: ${dato(e)} ${e.a ?? e.de ?? '—'}`, detalle: null, color: 'bg-fila-recibido-brd' }
    case 'TALLER_MANDA_NSYS':
      return { fecha, titulo: `Manda NSYS: ${dato(e)} ${e.a ?? '—'} (el del taller, ${e.de ?? '—'}, deja de valer)`, detalle: null, color: 'bg-ambar' }
```

En `src/modules/taller/imeis/PanelRastro.tsx`:

1. En `LEYENDA`, añade al final del array `['bg-taller', 'Lo del taller']`.
2. Sustituye

```tsx
                  <span className={cn('ml-1.5 rounded px-1.5 text-[9.5px] font-extrabold', e.fuente === 'ERP' ? 'bg-recibido-bg text-recibido-text' : 'bg-badge-neutro-bg text-azul-gris')}>{e.fuente}</span>
```

por

```tsx
                  <span className={cn('ml-1.5 rounded px-1.5 text-[9.5px] font-extrabold', e.fuente === 'ERP' ? 'bg-recibido-bg text-recibido-text'
                    : e.fuente === 'TALLER' ? 'bg-taller-bg text-taller-text' : 'bg-badge-neutro-bg text-azul-gris')}>{e.fuente}</span>
```

- [ ] **Step 3: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/taller/imeis`
Expected: `tsc` limpio y todo en verde (también `ImeiDetallePage.test.tsx`: sin `onCorregir`, la ficha se ve como antes;
la Task 10 lo enchufa).

- [ ] **Step 4: Commit**

```bash
git add src/modules/taller/imeis/FichaMovil.tsx src/modules/taller/imeis/FichaMovil.test.tsx src/modules/taller/imeis/rastro.ts \
  src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/PanelRastro.tsx src/modules/taller/imeis/PanelRastro.test.tsx
git commit -m "feat: la ficha del IMEI y el rastro con lo del taller y lo que hay que poner en NSYS (0.9.21)"
```

---

### Task 10: Web — diálogo «Corregir color, grado o batería…»

**Files:**
- Create: `gestion-reparaciones-web/src/modules/taller/imeis/corregir.ts`, `corregir.test.ts`
- Create: `gestion-reparaciones-web/src/modules/taller/imeis/CorregirTallerDialog.tsx`, `CorregirTallerDialog.test.tsx`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/ImeiDetallePage.tsx` (+ `ImeiDetallePage.test.tsx`)

**Interfaces:**
- Consumes: `useOpcionesTaller`, `useCorregirTaller`, `useVolverANsys` (Task 7); `textoPoner`, `SIN_SKU_MAESTRO` (Task 8);
  `FichaMovil` con `onCorregir` (Task 9); `useCerrojoEnvio`, `avisar`, `Dialog`, `BotonPrimario`, `BotonSecundario`.
- Produces:
  - En `corregir.ts`: `LETRAS`, `Letra`, `GradoElegido = { letra: Letra | ''; ms: boolean; bateria: boolean }`,
    `Eleccion = { color: string; grado: GradoElegido; motivo: string }`, `gradoCompleto(grado, bateriaNueva)`,
    `leerGrado(grado, bateriaNueva)`, `escribirGrado(g): string | null`, `eleccionInicial(ficha)`,
    `ordenColores(opciones, colorVale): string[]` y `previa(ficha, eleccion, opciones): Previa`.
  - `CorregirTallerDialog({ ficha, abierto, onCerrar })`.

- [ ] **Step 1: Tests de las reglas (fallan)**

Crea `src/modules/taller/imeis/corregir.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { fichaMovil, opcionesTaller } from '../test/fabrica'
import { eleccionInicial, escribirGrado, gradoCompleto, leerGrado, ordenColores, previa } from './corregir'

describe('reglas del diálogo «Corregir…» (spec 0.9.21 §2.3)', () => {
  it('el grado: leer el que vale y escribirlo como en NSYS', () => {
    expect(leerGrado('MsA-', true)).toEqual({ letra: 'A-', ms: true, bateria: true })
    expect(leerGrado('B', false)).toEqual({ letra: 'B', ms: false, bateria: false })
    expect(leerGrado('ID', false)).toEqual({ letra: '', ms: false, bateria: false })
    expect(leerGrado('n/a', true)).toEqual({ letra: '', ms: false, bateria: true })
    expect(escribirGrado({ letra: 'A', ms: false, bateria: true })).toBe('A100')
    expect(escribirGrado({ letra: 'A-', ms: true, bateria: false })).toBe('MsA-')
    expect(escribirGrado({ letra: '', ms: true, bateria: true })).toBeNull()
    expect(gradoCompleto('A', true)).toBe('A100')
    expect(gradoCompleto('B', false)).toBe('B')
  })

  it('los colores: el que vale primero y después los del maestro', () => {
    expect(ordenColores(opcionesTaller(), 'Rosa')).toEqual(['Rosa', 'Negro'])
    expect(ordenColores(opcionesTaller(), 'Verde')).toEqual(['Verde', 'Negro', 'Rosa'])
    expect(ordenColores(null, 'Negro')).toEqual(['Negro'])
  })

  it('sin cambios, nada que mandar', () => {
    const f = fichaMovil()
    expect(previa(f, eleccionInicial(f), opcionesTaller()))
      .toMatchObject({ cambiaColor: false, cambiaGrado: false, valido: false, cuerpo: { color: null, grado: null } })
  })

  it('cambiar el grado: el SKU del maestro y el producto de ahora', () => {
    const f = fichaMovil({ grado: 'B', bateriaNueva: false, sku: 'i16128NegroB' })
    const p = previa(f, { color: 'Negro', grado: { letra: 'A', ms: false, bateria: true }, motivo: ' batería ' }, opcionesTaller())
    expect(p).toMatchObject({ cambiaGrado: true, gradoAntes: 'B', gradoFinal: 'A100', sku: 'i16128NegroA100',
      producto: 'Apple iPhone 16 128GB Black', coincideConNsys: false, valido: true, cuerpo: { color: null, grado: 'A100' } })
  })

  it('cambiar el color: su producto o ninguno, y sin SKU si el maestro no lo tiene', () => {
    const f = fichaMovil()
    expect(previa(f, { ...eleccionInicial(f), color: 'Rosa' }, opcionesTaller())).toMatchObject({ cambiaColor: true,
      colorFinal: 'Rosa', sku: 'i16128RosaA100', producto: 'Apple iPhone 16 128GB Pink', valido: false })
    const verde = previa(f, { ...eleccionInicial(f), color: 'Verde', motivo: 'chasis verde' },
      opcionesTaller({ colores: [{ color: 'Verde', producto: null }] }))
    expect(verde).toMatchObject({ sku: null, producto: null, valido: true, cuerpo: { color: 'Verde', grado: null } })
  })

  it('elegir lo que dice NSYS: coincide y se quita lo del taller', () => {
    const f = fichaMovil({ color: 'Rosa', colorNsys: 'Negro', porPasar: true })
    expect(previa(f, { ...eleccionInicial(f), color: 'Negro' }, opcionesTaller()).coincideConNsys).toBe(true)
  })

  it('un grado que no es de NSYS no cambia solo por las casillas', () => {
    const f = fichaMovil({ grado: 'ID', bateriaNueva: false })
    expect(previa(f, { color: 'Negro', grado: { letra: '', ms: true, bateria: true }, motivo: 'x' }, opcionesTaller()).cambiaGrado).toBe(false)
  })
})
```

Run: `npx vitest run src/modules/taller/imeis/corregir.test.ts`
Expected: FAIL (no existe `./corregir`).

- [ ] **Step 2: Reglas**

Crea `src/modules/taller/imeis/corregir.ts`:

```ts
import type { FichaMovil, OpcionesTaller } from '@/shared/api/client'

/** Reglas puras del diálogo «Corregir color, grado o batería…» (0.9.21, spec «SKU del taller» §2.3). */

export const LETRAS = ['A', 'A-', 'B', 'C'] as const
export type Letra = (typeof LETRAS)[number]
/** El grado en el diálogo: la letra (vacía si el grado que vale no es de la forma [Ms]<letra>, p. ej. ID, F o n/a: entonces
 *  el grado solo cambia si se elige una), el mensaje de pieza («Ms») y la batería nueva («100»). */
export type GradoElegido = { letra: Letra | ''; ms: boolean; bateria: boolean }
export type Eleccion = { color: string; grado: GradoElegido; motivo: string }

const FORMA = /^(Ms)?(A-|A|B|C)$/

/** El grado entero como lo escribe NSYS: el servidor lo manda sin el 100 (`grado`) y con `bateriaNueva` aparte. */
export const gradoCompleto = (grado: string, bateriaNueva: boolean): string => `${grado}${bateriaNueva ? '100' : ''}`

export function leerGrado(grado: string, bateriaNueva: boolean): GradoElegido {
  const r = FORMA.exec(grado)
  if (!r) return { letra: '', ms: false, bateria: bateriaNueva }
  return { letra: r[2] as Letra, ms: r[1] === 'Ms', bateria: bateriaNueva }
}

/** `[Ms]<letra>[100]` («A100», «MsA-»); null sin letra: el grado no cambia. */
export function escribirGrado(g: GradoElegido): string | null {
  return g.letra ? `${g.ms ? 'Ms' : ''}${g.letra}${g.bateria ? '100' : ''}` : null
}

/** Lo que enseña el diálogo al abrirse: lo que vale. */
export function eleccionInicial(f: FichaMovil): Eleccion {
  return { color: f.color ?? '', grado: leerGrado(f.grado, f.bateriaNueva), motivo: '' }
}

/** Los colores del desplegable: el que vale primero y después los del maestro (spec §2.3). */
export function ordenColores(opciones: OpcionesTaller | null | undefined, colorVale: string): string[] {
  const delMaestro = (opciones?.colores ?? []).map((c) => c.color)
  return [...(colorVale ? [colorVale] : []), ...delMaestro.filter((c) => c !== colorVale)]
}

/** La vista previa y lo que se manda. `colorAntes`/`gradoAntes`: lo que vale ahora; `sku`: el del maestro para el color y el
 *  grado que quedarían (null: «sin SKU en el maestro»); `producto`: lo que habría que poner en NSYS (null: búscalo a mano);
 *  `coincideConNsys`: lo que queda es lo de NSYS (se quita lo del taller); `cuerpo`: solo los datos que cambian. */
export type Previa = {
  cambiaColor: boolean
  cambiaGrado: boolean
  colorAntes: string
  colorFinal: string
  gradoAntes: string
  gradoFinal: string
  sku: string | null
  producto: string | null
  coincideConNsys: boolean
  cuerpo: { color: string | null; grado: string | null }
  valido: boolean
}

export function previa(f: FichaMovil, e: Eleccion, opciones: OpcionesTaller | null | undefined): Previa {
  const colorAntes = f.color ?? ''
  const gradoAntes = gradoCompleto(f.grado, f.bateriaNueva)
  const colorNsys = f.colorNsys ?? colorAntes
  const gradoNsys = f.gradoNsys ?? gradoAntes
  const nuevo = escribirGrado(e.grado)
  const cambiaColor = e.color !== '' && e.color !== colorAntes
  const cambiaGrado = nuevo !== null && nuevo !== gradoAntes
  const colorFinal = cambiaColor ? e.color : colorAntes
  const gradoFinal = cambiaGrado && nuevo !== null ? nuevo : gradoAntes
  const sku = opciones?.skus.find((s) => s.color === colorFinal && s.grado === gradoFinal)?.sku ?? null
  const producto = colorFinal === colorNsys
    ? (opciones?.productoActual ?? null)
    : (opciones?.colores.find((c) => c.color === colorFinal)?.producto ?? null)
  return {
    cambiaColor, cambiaGrado, colorAntes, colorFinal, gradoAntes, gradoFinal, sku, producto,
    coincideConNsys: colorFinal === colorNsys && gradoFinal === gradoNsys,
    cuerpo: { color: cambiaColor ? e.color : null, grado: cambiaGrado ? nuevo : null },
    valido: (cambiaColor || cambiaGrado) && e.motivo.trim() !== '',
  }
}
```

Run: `npx vitest run src/modules/taller/imeis/corregir.test.ts`
Expected: PASS.

- [ ] **Step 3: Tests del diálogo (fallan)**

Crea `src/modules/taller/imeis/CorregirTallerDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { fichaMovil, opcionesTaller } from '../test/fabrica'
import { CorregirTallerDialog } from './CorregirTallerDialog'

const A = '351900000000041'
const abrirDialogo = (p: Parameters<typeof fichaMovil>[0] = {}, onCerrar = vi.fn()) =>
  renderConProviders(<CorregirTallerDialog ficha={fichaMovil(p)} abierto onCerrar={onCerrar} />, { sesion: SESION_SUPER })
const dialogo = () => screen.findByRole('dialog', { name: `Corregir ${A}` })
const opcionesDe = (select: HTMLElement) => within(select).getAllByRole('option').map((o) => o.textContent)

beforeEach(() => {
  server.use(http.get('*/api/stock-telefonos/:imeiSn/colores', () => HttpResponse.json(opcionesTaller())))
})

describe('Corregir color, grado o batería (spec 0.9.21 §2.3)', () => {
  it('empieza con lo que vale: el color primero, el grado y sus casillas', async () => {
    abrirDialogo({ color: 'Rosa', colorNsys: 'Negro', grado: 'MsA-', bateriaNueva: true, gradoNsys: 'B', porPasar: true })
    const d = await dialogo()
    const color = within(d).getByRole('combobox', { name: 'Color' })
    await waitFor(() => expect(color).toBeEnabled())
    expect(opcionesDe(color)).toEqual(['Rosa', 'Negro'])
    expect(color).toHaveValue('Rosa')
    expect(within(d).getByRole('combobox', { name: 'Grado' })).toHaveValue('A-')
    expect(within(d).getByRole('checkbox', { name: 'Batería nueva (100)' })).toBeChecked()
    expect(within(d).getByRole('checkbox', { name: 'Mensaje de pieza (Ms)' })).toBeChecked()
    expect(within(d).getByRole('textbox', { name: 'Motivo' })).toHaveAttribute('placeholder', 'Obligatorio: por qué se corrige')
    expect(within(d).getByRole('region', { name: 'Vista previa' })).toHaveTextContent('Sin cambios todavía.')
    expect(within(d).getByRole('button', { name: 'Guardar' })).toBeDisabled()
  })

  it('un grado que no es de NSYS empieza vacío y solo cambia si se elige', async () => {
    const user = userEvent.setup()
    abrirDialogo({ grado: 'ID', bateriaNueva: false })
    const d = await dialogo()
    const grado = within(d).getByRole('combobox', { name: 'Grado' })
    expect(grado).toHaveValue('')
    expect(opcionesDe(grado)).toEqual(['—', 'A', 'A-', 'B', 'C'])
    await user.click(within(d).getByRole('checkbox', { name: 'Batería nueva (100)' }))
    expect(within(d).getByRole('region', { name: 'Vista previa' })).toHaveTextContent('Sin cambios todavía.')
  })

  it('cambiar el grado: vista previa con el SKU nuevo y lo que hay que poner en NSYS, y guarda con el motivo', async () => {
    const cuerpos: unknown[] = []
    server.use(http.put('*/api/stock-telefonos/:imeiSn/taller', async ({ request }) => {
      cuerpos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    const user = userEvent.setup()
    const onCerrar = vi.fn()
    abrirDialogo({ grado: 'B', bateriaNueva: false, sku: 'i16128NegroB' }, onCerrar)
    const d = await dialogo()
    await user.selectOptions(within(d).getByRole('combobox', { name: 'Grado' }), 'A')
    await user.click(within(d).getByRole('checkbox', { name: 'Batería nueva (100)' }))
    const vista = within(d).getByRole('region', { name: 'Vista previa' })
    expect(vista).toHaveTextContent('Grado: B → A100')
    await waitFor(() => expect(vista).toHaveTextContent('SKU: i16128NegroA100'))
    expect(vista).toHaveTextContent('En NSYS habrá que poner: Apple iPhone 16 128GB Black · grado A100')
    expect(within(d).getByRole('button', { name: 'Guardar' })).toBeDisabled()
    await user.type(within(d).getByRole('textbox', { name: 'Motivo' }), 'batería nueva')
    await user.click(within(d).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(cuerpos).toEqual([{ color: null, grado: 'A100', motivo: 'batería nueva' }])
  })

  it('un color sin SKU ni móviles en NSYS se guarda igual y lo avisa', async () => {
    server.use(http.get('*/api/stock-telefonos/:imeiSn/colores', () => HttpResponse.json(opcionesTaller({
      colores: [{ color: 'Negro', producto: 'Apple iPhone 16 128GB Black' }, { color: 'Verde', producto: null }],
    }))))
    const user = userEvent.setup()
    abrirDialogo()
    const d = await dialogo()
    const color = within(d).getByRole('combobox', { name: 'Color' })
    await waitFor(() => expect(color).toBeEnabled())
    await user.selectOptions(color, 'Verde')
    const vista = within(d).getByRole('region', { name: 'Vista previa' })
    expect(vista).toHaveTextContent('Color: Negro → Verde')
    expect(vista).toHaveTextContent('SKU: sin SKU en el maestro: se guarda igual')
    expect(vista).toHaveTextContent('En NSYS habrá que poner: (ningún móvil de ese color en NSYS: búscalo a mano) · grado A100')
  })

  it('sin colores en el maestro el color no se puede cambiar, el grado sí', async () => {
    server.use(http.get('*/api/stock-telefonos/:imeiSn/colores', () => HttpResponse.json(opcionesTaller({ colores: [], skus: [] }))))
    abrirDialogo()
    const d = await dialogo()
    const color = within(d).getByRole('combobox', { name: 'Color' })
    await waitFor(() => expect(color).toHaveTextContent('Sin colores en el maestro'))
    expect(color).toBeDisabled()
    expect(within(d).getByRole('combobox', { name: 'Grado' })).toBeEnabled()
  })

  it('elegir lo que dice NSYS avisa de que se quita lo del taller', async () => {
    const user = userEvent.setup()
    abrirDialogo({ color: 'Rosa', colorNsys: 'Negro', porPasar: true })
    const d = await dialogo()
    const color = within(d).getByRole('combobox', { name: 'Color' })
    await waitFor(() => expect(color).toBeEnabled())
    await user.selectOptions(color, 'Negro')
    const vista = within(d).getByRole('region', { name: 'Vista previa' })
    expect(vista).toHaveTextContent('Color: Rosa → Negro')
    expect(vista).toHaveTextContent('Coincide con NSYS: se quita lo del taller.')
  })

  it('«Volver a lo de NSYS» solo con algo del taller, y sin motivo escrito va sin motivo', async () => {
    const cuerpos: unknown[] = []
    server.use(http.delete('*/api/stock-telefonos/:imeiSn/taller', async ({ request }) => {
      cuerpos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    const user = userEvent.setup()
    const { unmount } = abrirDialogo()
    expect(within(await dialogo()).queryByRole('button', { name: 'Volver a lo de NSYS' })).not.toBeInTheDocument()
    unmount()
    const onCerrar = vi.fn()
    abrirDialogo({ color: 'Rosa', colorNsys: 'Negro', porPasar: true }, onCerrar)
    const volver = within(await dialogo()).getByRole('button', { name: 'Volver a lo de NSYS' })
    expect(volver).toHaveAttribute('title', 'Quita lo del taller y vuelve a lo que dice NSYS')
    await user.click(volver)
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(cuerpos).toEqual([{ motivo: null }])
  })
})
```

En `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, añade `opcionesTaller` al import de `'../test/fabrica'` y, al final
del fichero:

```tsx
describe('ImeiDetallePage: «Corregir…» desde la ficha (0.9.21)', () => {
  it('el supertécnico abre el diálogo desde la ficha de NSYS', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(`*/api/stock-telefonos/${A}`, () => HttpResponse.json(fichaMovil())),
      http.get(`*/api/stock-telefonos/${A}/colores`, () => HttpResponse.json(opcionesTaller())),
    )
    abrir()
    const ficha = await screen.findByRole('region', { name: 'Ficha de NSYS' })
    await user.click(within(ficha).getByRole('button', { name: 'Corregir color, grado o batería…' }))
    expect(await screen.findByRole('dialog', { name: `Corregir ${A}` })).toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/taller/imeis/CorregirTallerDialog.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx`
Expected: FAIL (no existe `CorregirTallerDialog`; la ficha no tiene el botón).

- [ ] **Step 4: El diálogo y su sitio en la página**

Crea `src/modules/taller/imeis/CorregirTallerDialog.tsx`:

```tsx
import { useState } from 'react'
import type { FichaMovil } from '@/shared/api/client'
import { SIN_SKU_MAESTRO, textoPoner } from '@/shared/lib/telefonoNsys'
import { useCerrojoEnvio } from '@/shared/lib/useCerrojoEnvio'
import { avisar } from '@/shared/ui/avisos'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { useCorregirTaller, useOpcionesTaller, useVolverANsys } from './apiNsys'
import { eleccionInicial, LETRAS, ordenColores, previa, type Eleccion, type GradoElegido, type Letra } from './corregir'

type Props = { ficha: FichaMovil; abierto: boolean; onCerrar: () => void }

const CAMPO = 'h-8 rounded border border-borde-input bg-superficie px-2 text-[13px] text-azul-medio disabled:bg-gris-disabled'

/** «Corregir color, grado o batería…» (0.9.21, spec «SKU del taller» §2.3): color (los del maestro para el modelo,
 *  capacidad y eSIM), grado con «Batería nueva (100)» y «Mensaje de pieza (Ms)», motivo obligatorio y vista previa del SKU
 *  nuevo y de lo que hay que poner en NSYS. «Volver a lo de NSYS», si hay algo del taller. Se cierra cuando sale bien; si
 *  falla, sigue abierto y el error sale en el diálogo de siempre. */
export function CorregirTallerDialog({ ficha, abierto, onCerrar }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(o) => { if (!o) onCerrar() }}>
      <DialogContent aria-describedby={undefined} className="flex w-[min(560px,94vw)] max-w-[min(560px,94vw)] flex-col gap-3 bg-superficie p-5">
        <DialogTitle className="text-[15px] font-bold text-azul-noche">{`Corregir ${ficha.imeiSn}`}</DialogTitle>
        {/* Hijo de DialogContent: Radix lo desmonta al cerrar, así que cada apertura empieza con lo que vale. */}
        <Contenido ficha={ficha} onCerrar={onCerrar} />
      </DialogContent>
    </Dialog>
  )
}

function Contenido({ ficha, onCerrar }: { ficha: FichaMovil; onCerrar: () => void }) {
  const opciones = useOpcionesTaller(ficha.imeiSn, true)
  const corregir = useCorregirTaller(ficha.imeiSn)
  const volver = useVolverANsys(ficha.imeiSn)
  const enviando = corregir.isPending || volver.isPending
  const enviar = useCerrojoEnvio({ abierto: true, enviando })
  const [e, setE] = useState<Eleccion>(() => eleccionInicial(ficha))
  const p = previa(ficha, e, opciones.data)
  const sinColores = opciones.data != null && opciones.data.colores.length === 0
  const colores = ordenColores(opciones.data, p.colorAntes)
  const grado = (cambio: Partial<GradoElegido>) => setE((x) => ({ ...x, grado: { ...x.grado, ...cambio } }))
  const guardar = () => corregir.mutate({ ...p.cuerpo, motivo: e.motivo.trim() }, {
    onSuccess: () => { avisar({ texto: 'Corrección guardada', tipo: 'ok' }); onCerrar() },
  })
  const aNsys = () => volver.mutate(e.motivo.trim() || null, {
    onSuccess: () => { avisar({ texto: 'Lo del taller quitado: vale lo de NSYS', tipo: 'ok' }); onCerrar() },
  })
  return (
    <>
      <div className="grid grid-cols-[130px_1fr] items-center gap-x-3 gap-y-2.5 text-[12.5px] text-azul-medio">
        <label htmlFor="taller-color" className="font-semibold">Color</label>
        <select id="taller-color" className={CAMPO} value={sinColores ? '' : e.color} disabled={opciones.data == null || sinColores}
          onChange={(ev) => setE((x) => ({ ...x, color: ev.target.value }))}>
          {sinColores ? <option value="">Sin colores en el maestro</option> : colores.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <label htmlFor="taller-grado" className="font-semibold">Grado</label>
        <select id="taller-grado" className={CAMPO} value={e.grado.letra} onChange={(ev) => grado({ letra: ev.target.value as Letra | '' })}>
          {e.grado.letra === '' && <option value="">—</option>}
          {LETRAS.map((l) => <option key={l} value={l}>{l}</option>)}
        </select>
        <span />
        <label className="flex items-center gap-2"><input type="checkbox" checked={e.grado.bateria} onChange={(ev) => grado({ bateria: ev.target.checked })} />Batería nueva (100)</label>
        <span />
        <label className="flex items-center gap-2"><input type="checkbox" checked={e.grado.ms} onChange={(ev) => grado({ ms: ev.target.checked })} />Mensaje de pieza (Ms)</label>
        <label htmlFor="taller-motivo" className="self-start pt-1.5 font-semibold">Motivo</label>
        <textarea id="taller-motivo" value={e.motivo} placeholder="Obligatorio: por qué se corrige" onChange={(ev) => setE((x) => ({ ...x, motivo: ev.target.value }))}
          className="h-16 resize-y rounded border border-borde-input bg-superficie px-2 py-1.5 text-[13px] text-azul-medio" />
      </div>
      <div role="region" aria-label="Vista previa" className="rounded-lg border border-taller-bg bg-taller-bg px-3 py-2 text-[12.5px] leading-relaxed text-azul-medio">
        {p.cambiaColor && <p>Color: {p.colorAntes || '—'} → <b>{p.colorFinal}</b></p>}
        {p.cambiaGrado && <p>Grado: {p.gradoAntes || '—'} → <b>{p.gradoFinal}</b></p>}
        {!p.cambiaColor && !p.cambiaGrado ? <p>Sin cambios todavía.</p> : (
          <>
            <p>SKU: {p.sku ? <b className="font-mono">{p.sku}</b> : <span className="font-semibold text-fila-solicitud-brd">{`${SIN_SKU_MAESTRO}: se guarda igual`}</span>}</p>
            <p>{`En NSYS habrá que poner: ${textoPoner(p.producto, p.gradoFinal)}`}</p>
            {p.coincideConNsys && <p className="font-semibold text-fila-recibido-brd">Coincide con NSYS: se quita lo del taller.</p>}
          </>
        )}
      </div>
      <div className="flex items-center gap-2">
        {ficha.porPasar && (
          <BotonSecundario disabled={enviando} title="Quita lo del taller y vuelve a lo que dice NSYS" onClick={() => enviar(aNsys)}>Volver a lo de NSYS</BotonSecundario>
        )}
        <BotonSecundario className="ml-auto" disabled={enviando} onClick={onCerrar}>Cancelar</BotonSecundario>
        <BotonPrimario disabled={!p.valido || enviando} onClick={() => enviar(guardar)}>Guardar</BotonPrimario>
      </div>
    </>
  )
}
```

En `src/modules/taller/imeis/ImeiDetallePage.tsx`:

1. Imports: tras `import { useFichaMovil, useRastro } from './apiNsys'`, añade `import { CorregirTallerDialog } from './CorregirTallerDialog'`.
2. Tras `const [rastroAbierto, setRastroAbierto] = useState(false)`, añade
   `const [corrigiendo, setCorrigiendo] = useState(false)`.
3. Sustituye

```tsx
      {veNsys && ficha.data && <FichaMovil ficha={ficha.data} eventos={rastro.data?.length ?? null} onVerRastro={() => setRastroAbierto(true)} />}
```

por

```tsx
      {veNsys && ficha.data && <FichaMovil ficha={ficha.data} eventos={rastro.data?.length ?? null} onVerRastro={() => setRastroAbierto(true)}
        onCorregir={() => setCorrigiendo(true)} />}
      {/* 0.9.21: solo SUPERTECNICO y ADMIN (veNsys) y solo si el IMEI está en el espejo (ficha.data). */}
      {veNsys && ficha.data && <CorregirTallerDialog ficha={ficha.data} abierto={corrigiendo} onCerrar={() => setCorrigiendo(false)} />}
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npm run lint && npx vitest run src/modules/taller/imeis`
Expected: `tsc` y lint limpios, todo en verde.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/imeis/corregir.ts src/modules/taller/imeis/corregir.test.ts src/modules/taller/imeis/CorregirTallerDialog.tsx \
  src/modules/taller/imeis/CorregirTallerDialog.test.tsx src/modules/taller/imeis/ImeiDetallePage.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx
git commit -m "feat: dialogo corregir color, grado o bateria en la ficha del IMEI (0.9.21)"
```

---

### Task 11: Web — pestaña «Por pasar a NSYS» con «Exportar a Excel»

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/porPasar.ts`, `porPasar.test.ts`, `PorPasarNsys.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/estado.ts`, `StockTelefonosPage.tsx` (+ `StockTelefonosPage.test.tsx`)

**Interfaces:**
- Consumes: `usePorPasarNsys` (Task 7); `textoPoner`, `SIN_SKU_MAESTRO`, `SIN_PRODUCTO_NSYS` (Task 8); fábricas `filaPorPasar`
  y `porQue` (Task 7).
- Produces:
  - En `porPasar.ts`: `textoPorQue(p)`, `modeloDeFila(f)`, `textoEnNsys(f)`, `textoDesde(iso)` y `paraExcel(filas)`.
  - `PorPasarNsys({ filas, cargando, onAbrirImei })`.
  - `EstadoStock.vista: 'moviles' | 'por-pasar'` (por defecto `'moviles'`; se recuerda al volver del detalle).

- [ ] **Step 1: Tests (fallan)**

Crea `src/modules/telefonos/stock/porPasar.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { filaPorPasar, porQue } from '../test/fabrica'
import { modeloDeFila, paraExcel, textoDesde, textoEnNsys, textoPorQue } from './porPasar'

describe('lista «Por pasar a NSYS» (spec 0.9.21 §2.4)', () => {
  it('por qué: a mano con quién y motivo; sin motivo, sin la última parte; la reparación de la pieza 2', () => {
    expect(textoPorQue(porQue({ dato: 'COLOR', usuario: 'Supertécnico 1', motivo: 'chasis rojo' }))).toBe('Color: a mano · Supertécnico 1 · «chasis rojo»')
    expect(textoPorQue(porQue({ motivo: null }))).toBe('Grado: a mano · Técnico 1')
    expect(textoPorQue(porQue({ origen: 'REPARACION', idRep: 'R20261020_4', usuario: 'Técnico 2', motivo: null })))
      .toBe('Grado: reparación R20261020_4 · Técnico 2')
  })

  it('el modelo, lo de NSYS hoy y desde cuándo', () => {
    const f = filaPorPasar()
    expect(modeloDeFila(f)).toBe('iPhone 13 128GB')
    expect(textoEnNsys(f)).toBe('Apple iPhone 13 128GB Blue · grado B')
    expect(textoDesde(f.desde)).toBe('20/10/2026 10:31')
  })

  it('para Excel: la cabecera y una línea por móvil, sin tabuladores dentro de las celdas', () => {
    const [cabecera, uno, dos] = paraExcel([
      filaPorPasar(),
      filaPorPasar({ imeiSn: '350000000000002', ponerProducto: null, sku: null, porQue: [porQue({ motivo: 'uno\tdos' })] }),
    ]).split('\n')
    expect(cabecera).toBe('IMEI\tModelo\tEn NSYS hoy\tPoner en NSYS\tSKU nuevo\tPor qué\tDesde')
    expect(uno).toBe('350000000000001\tiPhone 13 128GB\tApple iPhone 13 128GB Blue · grado B\tApple iPhone 13 128GB Blue · grado A100'
      + '\ti13128AzulA100\tGrado: a mano · Técnico 1 · «batería nueva»\t20/10/2026 10:31')
    expect(dos).toContain('(ningún móvil de ese color en NSYS: búscalo a mano) · grado A100\tsin SKU en el maestro\tGrado: a mano · Técnico 1 · «uno dos»')
  })
})
```

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, cambia el import de las fábricas por
`import { estadoNsys, filaPorPasar, movil, movilBuscado, movilComprobado, porQue, trabajo } from '../test/fabrica'` y añade al
final del fichero:

```tsx
describe('Stock de teléfonos: «Por pasar a NSYS» (spec 0.9.21 §2.4)', () => {
  it('la pestaña dice cuántos hay y enseña lo que hay que poner en NSYS', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/por-pasar', () => HttpResponse.json([
      filaPorPasar(),
      filaPorPasar({ imeiSn: '350000000000002', ponerProducto: null, sku: null,
        porQue: [porQue({ dato: 'COLOR', motivo: 'chasis rojo' }), porQue()] }),
    ])))
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Por pasar a NSYS (2)' }))
    expect(screen.getByRole('button', { name: 'Por pasar a NSYS (2)' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/^Lo que hay que cambiar a mano en NSYS \(producto y grado de cada móvil\)\./)).toBeInTheDocument()
    const tabla = screen.getByRole('table', { name: 'Móviles por pasar a NSYS' })
    expect(within(tabla).getAllByRole('columnheader').map((c) => c.textContent))
      .toEqual(['IMEI', 'Modelo', 'En NSYS hoy', 'Poner en NSYS', 'SKU nuevo', 'Por qué', 'Desde'])
    const [uno, dos] = within(tabla).getAllByRole('row').slice(1)
    expect(uno).toHaveTextContent('iPhone 13 128GB')
    expect(uno).toHaveTextContent('grado B')
    expect(uno).toHaveTextContent('grado A100')
    expect(uno).toHaveTextContent('i13128AzulA100')
    expect(uno).toHaveTextContent('Grado: a mano · Técnico 1 · «batería nueva»')
    expect(uno).toHaveTextContent('20/10/2026 10:31')
    expect(dos).toHaveTextContent('(ningún móvil de ese color en NSYS: búscalo a mano)')
    expect(dos).toHaveTextContent('sin SKU en el maestro')
    expect(dos).toHaveTextContent('Color: a mano · Técnico 1 · «chasis rojo»')
    await user.click(within(uno).getByRole('button', { name: 'Abrir 350000000000001' }))
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/stock')).toBeInTheDocument()
  })

  it('sin nada por pasar lo dice, con la pestaña a cero y sin nada que exportar', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Por pasar a NSYS (0)' }))
    expect(screen.getByText('Nada por pasar: NSYS está al día con el taller.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar a Excel' })).toBeDisabled()
  })

  it('«Exportar a Excel» copia la lista para pegarla en Excel', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/por-pasar', () => HttpResponse.json([filaPorPasar()])))
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Por pasar a NSYS (1)' }))
    await user.click(screen.getByRole('button', { name: 'Exportar a Excel' }))
    await waitFor(async () => expect(await navigator.clipboard.readText()).toMatch(/^IMEI\tModelo\tEn NSYS hoy\tPoner en NSYS/))
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

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no existen `porPasar.ts` ni la pestaña).

- [ ] **Step 2: Implementación**

Crea `src/modules/telefonos/stock/porPasar.ts`:

```ts
import type { FilaPorPasar, PorQueTaller } from '@/shared/api/client'
import { diaNsys, nombreModeloNsys, SIN_SKU_MAESTRO, textoPoner } from '@/shared/lib/telefonoNsys'

/** Reglas de la lista «Por pasar a NSYS» (0.9.21, spec «SKU del taller» §2.4). */

const DATO: Record<string, string> = { COLOR: 'Color', GRADO: 'Grado' }

/** «Color: a mano · Supertécnico 1 · «motivo»»; el de una reparación (pieza 2), «Grado: reparación R… · Técnico 2». */
export function textoPorQue(p: PorQueTaller): string {
  const como = p.origen === 'REPARACION' ? `reparación ${p.idRep ?? '—'}` : 'a mano'
  return `${DATO[p.dato] ?? p.dato}: ${[como, p.usuario ?? '—', p.motivo ? `«${p.motivo}»` : null].filter(Boolean).join(' · ')}`
}

/** «iPhone 13 128GB». */
export const modeloDeFila = (f: FilaPorPasar): string => [nombreModeloNsys(f.modelo, f.modeloMaestro), f.capacidad].filter(Boolean).join(' ')

/** Lo que dice NSYS hoy: «<producto> · grado <grado>». */
export const textoEnNsys = (f: FilaPorPasar): string => `${f.productoNsys || '—'} · grado ${f.gradoNsys || '—'}`

/** «20/10/2026 10:31»: la hora es de Madrid, sin zona; se corta del texto. */
export const textoDesde = (iso: string): string => `${diaNsys(iso)} ${iso.slice(11, 16)}`

const CABECERA = ['IMEI', 'Modelo', 'En NSYS hoy', 'Poner en NSYS', 'SKU nuevo', 'Por qué', 'Desde']
const celda = (t: string) => t.replace(/[\t\r\n]+/g, ' ')

/** La lista separada por tabuladores, para pegarla en Excel (como «Copiar el resultado» de «Pegar varios IMEIs»). */
export function paraExcel(filas: FilaPorPasar[]): string {
  const lineas = filas.map((f) => [f.imeiSn, modeloDeFila(f), textoEnNsys(f), textoPoner(f.ponerProducto, f.ponerGrado),
    f.sku ?? SIN_SKU_MAESTRO, f.porQue.map(textoPorQue).join(' / '), textoDesde(f.desde)])
  return [CABECERA, ...lineas].map((columnas) => columnas.map(celda).join('\t')).join('\n')
}
```

Crea `src/modules/telefonos/stock/PorPasarNsys.tsx`:

```tsx
import type { FilaPorPasar } from '@/shared/api/client'
import { SIN_PRODUCTO_NSYS, SIN_SKU_MAESTRO } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { avisar } from '@/shared/ui/avisos'
import { BotonSecundario } from '@/shared/ui/Botones'
import { copiarAlPortapapeles } from '@/shared/ui/copiar'
import { modeloDeFila, paraExcel, textoDesde, textoPorQue } from './porPasar'

type Props = { filas: FilaPorPasar[]; cargando: boolean; onAbrirImei: (imeiSn: string) => void }

const COLUMNAS = ['IMEI', 'Modelo', 'En NSYS hoy', 'Poner en NSYS', 'SKU nuevo', 'Por qué', 'Desde']
const CELDA = 'px-2 py-1.5 align-top'
const GRIS = 'block text-[11px] text-azul-gris'

/** La pestaña «Por pasar a NSYS» del stock de teléfonos (0.9.21, spec «SKU del taller» §2.4; variante «A · se vacía sola»
 *  del prototipo): lo que hay que cambiar a mano en NSYS, de cualquier modelo, los más antiguos primero. Sin botones por fila:
 *  cada una se va sola cuando la importación ve NSYS igual. El IMEI abre su detalle. */
export function PorPasarNsys({ filas, cargando, onAbrirImei }: Props) {
  return (
    <section aria-label="Por pasar a NSYS" className="flex min-h-0 flex-1 flex-col gap-2 rounded-md bg-superficie p-3">
      <div className="flex shrink-0 flex-wrap items-center gap-3">
        <p className="text-[11.5px] text-azul-gris">
          Lo que hay que cambiar a mano en NSYS (producto y grado de cada móvil). Cada fila desaparece sola cuando la importación (cada 30 min) ve NSYS igual.
        </p>
        <BotonSecundario className="ml-auto" disabled={filas.length === 0} onClick={() => {
          void copiarAlPortapapeles(paraExcel(filas))
          avisar({ texto: 'Copiado: pégalo en Excel', tipo: 'ok' })
        }}>Exportar a Excel</BotonSecundario>
      </div>
      <div className="min-h-0 flex-1 overflow-auto rounded-lg border border-fila-sep">
        <table aria-label="Móviles por pasar a NSYS" className="w-full border-collapse text-[12.5px] text-azul-medio">
          <thead className="sticky top-0 bg-crema">
            <tr>{COLUMNAS.map((c) => <th key={c} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
          </thead>
          <tbody>
            {filas.length === 0 && (
              <tr>
                <td colSpan={COLUMNAS.length} className="px-2 py-3 text-[12px] text-azul-gris">
                  {cargando ? 'Cargando…' : 'Nada por pasar: NSYS está al día con el taller.'}
                </td>
              </tr>
            )}
            {filas.map((f) => (
              <tr key={f.imeiSn} className="border-t border-fila-sep">
                <td className={cn(CELDA, 'font-mono text-[11.5px]')}>
                  <button type="button" aria-label={`Abrir ${f.imeiSn}`} onClick={() => onAbrirImei(f.imeiSn)}
                    className="cursor-pointer text-azul-noche hover:underline">{f.imeiSn}</button>
                </td>
                <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
                <td className={CELDA}>{f.productoNsys ?? '—'}<span className={GRIS}>{`grado ${f.gradoNsys || '—'}`}</span></td>
                <td className={CELDA}>
                  {f.ponerProducto ? <b className="text-azul-noche">{f.ponerProducto}</b> : <span className="font-semibold text-fila-solicitud-brd">{SIN_PRODUCTO_NSYS}</span>}
                  <span className={GRIS}>grado <b className="text-azul-noche">{f.ponerGrado || '—'}</b></span>
                </td>
                <td className={cn(CELDA, 'font-mono text-[11.5px]')}>{f.sku ?? <i className="font-sans text-fila-solicitud-brd">{SIN_SKU_MAESTRO}</i>}</td>
                <td className={cn(CELDA, 'min-w-[220px]')}>{f.porQue.map((p, i) => <span key={i} className="block">{textoPorQue(p)}</span>)}</td>
                <td className={cn(CELDA, 'whitespace-nowrap')}>{textoDesde(f.desde)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
```

En `src/modules/telefonos/stock/estado.ts`:

1. En el tipo `EstadoStock`, tras `filtrosDe: { … }`, añade:

```ts
  /** 0.9.21: la pestaña de la pantalla, la de los móviles o «Por pasar a NSYS». */
  vista: 'moviles' | 'por-pasar'
```

2. En `crearStore<EstadoStock>({ … })`, añade `vista: 'moviles',` tras `filtrosDe: { clave: null, filtros: FILTROS_VACIOS, sku: null },`.

En `src/modules/telefonos/stock/StockTelefonosPage.tsx`:

1. Imports: cambia `import { useStockTelefonos } from '../api'` por `import { usePorPasarNsys, useStockTelefonos } from '../api'` y,
   tras `import { PestanasCapacidad } from './PestanasCapacidad'`, añade `import { PorPasarNsys } from './PorPasarNsys'`.
2. Sustituye

```tsx
  const [{ verNoCuenta, textoModelo, elegido, capacidad: capacidadElegida, filtrosDe }, setEstado] = useStore(estadoStockTelefonos)
```

por

```tsx
  const [{ verNoCuenta, textoModelo, elegido, capacidad: capacidadElegida, filtrosDe, vista }, setEstado] = useStore(estadoStockTelefonos)
  // 0.9.21 (spec «SKU del taller» §2.4): la lista «Por pasar a NSYS», para su pestaña y su número.
  const porPasar = usePorPasarNsys()
  const nPorPasar = porPasar.data?.length ?? 0
```

3. Sustituye

```tsx
        <PildoraNsys />
      </div>
```

por

```tsx
        <PildoraNsys />
        <div role="group" aria-label="Vista" className="ml-auto flex overflow-hidden rounded-lg border border-borde-input">
          {([['moviles', 'Móviles'], ['por-pasar', 'Por pasar a NSYS']] as const).map(([v, texto]) => (
            <button key={v} type="button" aria-pressed={vista === v} onClick={() => setEstado((e) => ({ ...e, vista: v }))}
              className={cn('cursor-pointer border-r border-borde-input px-3.5 py-1 text-[12px] font-semibold last:border-r-0',
                vista === v ? 'bg-azul-noche text-superficie' : 'bg-superficie text-azul-medio hover:bg-seleccion-suave')}>
              {texto}
              {v === 'por-pasar' && <>{' '}<span className={vista === v ? 'text-superficie' : 'text-taller-text'}>{`(${nPorPasar})`}</span></>}
            </button>
          ))}
        </div>
      </div>
```

4. Sustituye

```tsx
      <div className="grid min-h-0 flex-1 grid-cols-[270px_1fr] gap-3 max-[1500px]:grid-cols-[230px_1fr]">
```

por

```tsx
      {vista === 'por-pasar' ? <PorPasarNsys filas={porPasar.data ?? []} cargando={porPasar.isPending} onAbrirImei={abrir} /> : (
      <div className="grid min-h-0 flex-1 grid-cols-[270px_1fr] gap-3 max-[1500px]:grid-cols-[230px_1fr]">
```

5. Sustituye

```tsx
        </section>
      </div>
      <ComprobarImeis
```

por

```tsx
        </section>
      </div>
      )}
      <ComprobarImeis
```

- [ ] **Step 3: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npm run lint && npx vitest run src/modules/telefonos`
Expected: `tsc` y lint limpios y todo en verde (los tests de siempre de la pantalla siguen: la vista por defecto es la de los
móviles y la lista por defecto está vacía).

- [ ] **Step 4: Commit**

```bash
git add src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/PorPasarNsys.tsx \
  src/modules/telefonos/stock/estado.ts src/modules/telefonos/stock/StockTelefonosPage.tsx src/modules/telefonos/stock/StockTelefonosPage.test.tsx
git commit -m "feat: pestana por pasar a NSYS con exportar a Excel en el stock de telefonos (0.9.21)"
```

---

### Task 12: Web — E2E del SKU del taller (preproducción, móvil sintético)

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/sku-taller.spec.ts`

**Interfaces:**
- Consumes: la pantalla de las Tasks 8-11 y sus nombres accesibles (Global Constraints → «Textos exactos»);
  `credenciales('E2E_USER', 'E2E_PASS')` de `tests/e2e/credenciales.ts`; el móvil sintético que mete el guion de preprod
  (Task 14): IMEI `350000000009921` (o `E2E_IMEI_TALLER`), grado `B` sin batería nueva.
- Produces: 2 tests en serie con un solo inicio de sesión (el entorno limita los inicios de sesión por minuto).

- [ ] **Step 1: El fichero**

Crea `tests/e2e/sku-taller.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// sku-taller.spec.ts (0.9.21, spec «SKU del taller», pieza 1): el supertécnico corrige a mano el grado de un móvil de prueba
// desde su ficha, lo ve en la ficha, en el rastro y en la pestaña «Por pasar a NSYS», y «Volver a lo de NSYS» lo saca de la
// lista. Solo contra PREPRODUCCIÓN (se niega a seguir sin el letrero) y con un móvil SINTÉTICO que el guion de preprod mete
// en el espejo (por defecto 350000000009921, grado B sin batería nueva; otro con E2E_IMEI_TALLER): nunca un móvil real.
// Deja el móvil como estaba al empezar y al acabar («Volver a lo de NSYS» por la API).
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const IMEI = process.env.E2E_IMEI_TALLER ?? '350000000009921'
const MOTIVO = 'E2E 0.9.21: prueba del SKU del taller'

type Respuesta = { estado: number; cuerpo: unknown }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Una petición a la API con el token de la sesión de la página (misma origin que la app; patrón de stock-telefonos.spec.ts). */
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

/** Vuelve a lo de NSYS si quedó algo del taller (una pasada anterior cortada): 204, o 400 si no había nada. */
async function limpiar(page: Page, motivo: string) {
  const r = await pedir(page, 'DELETE', `/api/stock-telefonos/${IMEI}/taller`, { motivo })
  expect([204, 400], `limpieza del móvil de prueba: ${JSON.stringify(r.cuerpo)}`).toContain(r.estado)
}

test.describe('0.9.21: SKU del taller', () => {
  test.describe.configure({ mode: 'serial' })
  // Una sola sesión para los dos: el entorno limita los inicios de sesión por minuto.
  let pagina: Page

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
    expect((await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).estado,
      `falta el móvil sintético ${IMEI} en el espejo de preprod (lo mete el guion)`).toBe(200)
    await limpiar(pagina, 'E2E: limpieza previa')
    const ficha = (await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).cuerpo as { grado: string; bateriaNueva: boolean; porPasar: boolean }
    expect(ficha, 'el móvil de prueba empieza en B sin batería nueva y sin nada del taller').toMatchObject({ grado: 'B', bateriaNueva: false, porPasar: false })
  })

  test.afterAll(async () => {
    if (pagina) await limpiar(pagina, 'E2E: limpieza')
    await pagina?.close()
  })

  test('corregir el grado lo pone en la ficha, en el rastro y en «Por pasar a NSYS»', async () => {
    await pagina.goto(`/reparaciones/imeis/${IMEI}`)
    const ficha = pagina.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toBeVisible({ timeout: 30_000 })
    await ficha.getByRole('button', { name: 'Corregir color, grado o batería…' }).click()
    const dialogo = pagina.getByRole('dialog', { name: `Corregir ${IMEI}` })
    await dialogo.getByLabel('Grado').selectOption('A')
    await dialogo.getByRole('checkbox', { name: 'Batería nueva (100)' }).check()
    await expect(dialogo.getByRole('region', { name: 'Vista previa' })).toContainText('Grado: B → A100')
    await dialogo.getByRole('textbox', { name: 'Motivo' }).fill(MOTIVO)
    await dialogo.getByRole('button', { name: 'Guardar' }).click()
    await expect(dialogo).toHaveCount(0)
    await expect(ficha).toContainText('Por pasar a NSYS:')
    await expect(ficha).toContainText('NSYS: B')
    await ficha.getByRole('button', { name: /^Ver rastro/ }).click()
    await expect(pagina.getByRole('dialog', { name: 'Rastro' })).toContainText(`Taller: grado B → A100 (a mano · `)
    await pagina.keyboard.press('Escape')
    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS \(\d+\)$/ }).click()
    await expect(pagina.getByRole('table', { name: 'Móviles por pasar a NSYS' }).getByRole('row', { name: new RegExp(IMEI) })).toBeVisible()
  })

  test('«Volver a lo de NSYS» lo saca de la lista y queda en el rastro', async () => {
    await pagina.goto(`/reparaciones/imeis/${IMEI}`)
    const ficha = pagina.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toContainText('Por pasar a NSYS:', { timeout: 30_000 })
    await ficha.getByRole('button', { name: 'Corregir color, grado o batería…' }).click()
    const dialogo = pagina.getByRole('dialog', { name: `Corregir ${IMEI}` })
    await dialogo.getByRole('button', { name: 'Volver a lo de NSYS' }).click()
    await expect(dialogo).toHaveCount(0)
    await expect(ficha).not.toContainText('Por pasar a NSYS:')
    await ficha.getByRole('button', { name: /^Ver rastro/ }).click()
    await expect(pagina.getByRole('dialog', { name: 'Rastro' })).toContainText('Vuelve a lo de NSYS: grado A100 → B')
    await pagina.keyboard.press('Escape')
    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS \(\d+\)$/ }).click()
    await expect(pagina.getByRole('table', { name: 'Móviles por pasar a NSYS' }).getByRole('row', { name: new RegExp(IMEI) })).toHaveCount(0)
  })
})
```

- [ ] **Step 2: Compila y se lista**

Run: `npx playwright test tests/e2e/sku-taller.spec.ts --list`
Expected: 2 tests, sin errores de compilación. No se ejecutan aquí: solo contra preprod (Task 14).

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/sku-taller.spec.ts
git commit -m "test: e2e del SKU del taller con un movil sintetico de preprod (0.9.21)"
```

---

### Task 13: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json`, `package-lock.json` (versión), `CHANGELOG.md` (entrada `[0.9.21]`),
  `docs/paridad/imeis.md` (sección 0.9.21)
- Create: `docs/novedades/NOVEDADES-v0.9.21.md` (raíz; lo sube el controlador con los gitlinks)
- Modify: `Apuntes/plan-futuro.md` (fuera de los repos)

**Interfaces:**
- Consumes: todo lo de las Tasks 1-12.
- Produces: las ramas `dev_v0921_sku_taller` (servidor y web) listas para el merge.

- [ ] **Step 1: Versión de la web**

Si `package.json` ya está en `0.9.21` (la «Corrección en NSYS» entró antes con la versión subida), este paso no hace nada.
Si no:
Run (en `/c/Users/dev/Documents/wt/web-0921-sku`): `npm pkg set version=0.9.21` (convención del proyecto; nada de
`npm version`) y, a mano, las dos entradas de la raíz de `package-lock.json` (`"version"` y `packages[""].version`) a `0.9.21`.
Expected: `package.json` y las dos entradas de la raíz de `package-lock.json` en `0.9.21`.

- [ ] **Step 2: CHANGELOG de la web**

Si ya hay una entrada `## [0.9.21]` (la abrió la «Corrección en NSYS»), sus viñetas van **dentro** de ella, después de las
que tenga, y el título pasa a «… — Corrección en NSYS y SKU del taller» (la fecha la deja `2026-10-XX`). Si no, justo antes de
la primera entrada (`## [0.9.20] …`), añade (las de 0.9.19 y 0.9.20 no se tocan):

```markdown
## [0.9.21] - 2026-10-XX — SKU del taller: lo del taller por encima de NSYS

- **Stock de teléfonos, ficha del IMEI, «Pegar varios IMEIs» y buscador de IMEI** (supertécnico y administrador): el color, el grado y el SKU que se ven son **los que valen**: lo que el taller ha cambiado manda sobre NSYS hasta que NSYS lo alcance. Las celdas cambiadas llevan la marca «taller» y, debajo, lo que dice NSYS («NSYS: Azul»). Los filtros y la tabla color × grado cuentan lo que vale.
- **«Corregir color, grado o batería…»** en la ficha de NSYS del IMEI: color (los del maestro para ese modelo, capacidad y eSIM), grado (A, A-, B o C, con «Batería nueva (100)» y «Mensaje de pieza (Ms)») y motivo obligatorio, con la vista previa del SKU nuevo (o «sin SKU en el maestro», que se guarda igual) y de lo que habrá que poner en NSYS. «Volver a lo de NSYS» quita lo del taller. Queda en el registro de actividad (`CORREGIR_MOVIL_TALLER`) y en el rastro.
- **Pestaña «Por pasar a NSYS (N)»** en el stock de teléfonos: lo que hay que cambiar a mano en NSYS (producto y grado), con el SKU nuevo, quién y por qué; «Exportar a Excel» la copia para pegarla. Se vacía sola cuando la importación ve NSYS igual; si después NSYS pone otra cosa, manda NSYS.
- **Rastro:** los cambios del taller, «Pasado a NSYS», «Manda NSYS» y «Vuelve a lo de NSYS».
- Requiere el servidor de la 0.9.21 y la migración `migracion-sku-taller.sql`. La web 0.9.20 funciona con ese servidor.
```

- [ ] **Step 3: Ficha de paridad**

Al final de `docs/paridad/imeis.md`, con una línea en blanco antes:

```markdown
## 0.9.21 — SKU del taller en la ficha y en el rastro (sin equivalente en el JavaFX)

Añadido, decidido en la spec `2026-10-21-v0921-sku-del-taller-base-design.md` (raíz). Para SUPERTECNICO y ADMIN:

- la ficha de NSYS enseña el color, el grado y el SKU que valen (lo del taller por encima de NSYS), con la marca «taller» y
  «NSYS: …» al lado, y «Por pasar a NSYS: <producto> · grado <grado>» mientras NSYS no lo tenga; con el grado del taller no
  dice «(antes …)»;
- «Corregir color, grado o batería…» abre el diálogo de corrección (motivo obligatorio, vista previa del SKU y de lo que hay
  que poner en NSYS, «Volver a lo de NSYS»);
- el rastro lleva los cambios del taller (fuente «TALLER»): «Taller: …», «Vuelve a lo de NSYS: …», «Pasado a NSYS: …» y
  «Manda NSYS: …».

Pruebas: `FichaMovil.test.tsx`, `CorregirTallerDialog.test.tsx`, `corregir.test.ts`, `rastro.test.ts`, `PanelRastro.test.tsx`,
`ImeiDetallePage.test.tsx` y `sku-taller.spec.ts`. El TECNICO ve lo mismo que antes. El `PATCH /api/telefonos/{imei}/atributos`
del JavaFX cambiaba `Telefono`, que ya no se enseña: no es este dato.
```

- [ ] **Step 4: Novedades (raíz, sin commit)**

Si `NOVEDADES-v0.9.21.md` ya existe (lo creó la «Corrección en NSYS»), añade el párrafo de entrada y la sección
«📱 Stock de teléfonos» de abajo a lo que ya tenga, sin duplicar encabezados. Si no, crea
`C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.21.md`:

```markdown
# 🎉 Novedades — Versión 0.9.21

El ERP ya sabe lo que el taller cambia en un móvil (color, grado y batería nueva) aunque NSYS todavía no lo diga, y lleva la
lista de lo que hay que cambiar en NSYS.

---

## 🧰 Para todos

- El técnico ve el programa como hasta ahora.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos

- **Color, grado y SKU son los que valen:** si el taller ha cambiado algo, manda lo del taller. Esas celdas llevan la marca
  **«taller»** y, debajo, lo que dice NSYS («NSYS: Azul»). Los filtros y la tabla color × grado cuentan lo que vale.
- **Pestaña «Por pasar a NSYS (N)»:** lo que hay que cambiar a mano en NSYS, móvil a móvil: lo que dice hoy, lo que hay que
  poner (producto y grado), el SKU nuevo, quién lo cambió y por qué.
  - **Se vacía sola:** cuando la importación (cada 30 min) ve NSYS igual, la fila desaparece. No hay botón «Ya lo pasé».
  - Si después alguien pone en NSYS **otra cosa**, manda NSYS.
  - **«Exportar a Excel»** copia la lista para pegarla en una hoja.
- «Pegar varios IMEIs» y el buscador de IMEI también enseñan lo que vale, con la marca.

### 🔎 IMEIs

- **«Corregir color, grado o batería…»** en la ficha de NSYS del móvil:
  - el color (los del maestro para ese modelo), el grado (A, A-, B o C), «Batería nueva (100)» y «Mensaje de pieza (Ms)»;
  - el **motivo es obligatorio**;
  - antes de guardar se ve el SKU nuevo (o «sin SKU en el maestro», que se guarda igual) y lo que habrá que poner en NSYS;
  - **«Volver a lo de NSYS»** quita lo del taller.
- La ficha dice **«Por pasar a NSYS: …»** mientras NSYS no lo tenga.
- El **rastro** cuenta cada cambio del taller, cuándo pasó a NSYS y cuándo mandó NSYS.

> ⚠️ El ERP no cambia nada en NSYS: la lista es lo que hay que pasar a mano. Lo automático desde las reparaciones (chasis de
> otro color, batería nueva…) llega en la siguiente pieza.
```

- [ ] **Step 5: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0921-sku`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total en el registro.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0921-sku`
Expected: lint sin errores nuevos, `tsc -b` limpio y Vitest en verde. Si falla alguno de los lentos conocidos (Global
Constraints), repetir ese fichero solo y apuntarlo. Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista incluye los 2 de `sku-taller.spec.ts` y los de siempre, sin errores de compilación.

- [ ] **Step 6: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git commit -m "docs: version 0.9.21, changelog y paridad del SKU del taller"
```

- [ ] **Step 7: Plan maestro**

En `Apuntes/plan-futuro.md` §5, en la entrada «0.9.19 SKU del taller: …» (si el controlador no lo ha hecho ya):
- cambia «0.9.19 SKU del taller» por «0.9.21 SKU del taller (renumerado el 21/10: sale sola, servidor + base + web)»;
- tras «pendiente del plan.», añade: «Plan `2026-10-21-v0921-sku-del-taller-base.md` (raíz); ramas `dev_v0921_sku_taller` del
  servidor y de la web listas (commits: `git log --oneline main..dev_v0921_sku_taller` en cada una); falta preprod y producción.»
Sin marcar `[x]` hasta producción.

- [ ] **Step 8: Entrega (controlador, con OK del usuario)**

1. Revisión final de las dos ramas.
2. Con el OK del usuario: merge `--no-ff` de `dev_v0921_sku_taller` en `main` del servidor y de la web, con push de los dos;
   en la raíz, gitlinks, `NOVEDADES-v0.9.21.md` y el plan marcado.
3. Worktrees y ramas se borran después de producción.

---

### Task 14: Preproducción

**Files:**
- Create: `Apuntes/preprod/v0921-preprod.md` (guion; fuera de los repos)
- Modify: `Apuntes/despliegue_preprod.md` (registro)

**Requisitos antes de empezar:** el merge de la Task 13 está hecho (o el usuario decide probar desde las ramas).

- [ ] **Step 1: Guion**

Basado en `Apuntes/preprod/v0917-preprod.md` (servidor + web **con migración**). **Cada bloque empieza con `hostname` y el
seguro de `backup-erp.sh`** (`if [ ! -x /usr/local/sbin/backup-erp.sh ]; then … else echo "ESTO PARECE PRODUCCIÓN: no se ha
hecho nada"; fi`), y la función `m()` de `v0917-prueba-sql.md` para hablar con MariaDB.

- **Bloque 1 (solo lee):**
  - commits y contenedores;
  - `SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_cambio';`
    → 0;
  - el formato del grado del maestro, del que depende el SKU nuevo (spec §2.1, `CALIDAD_DETALLE` = grado):
    `SELECT CALIDAD_DETALLE, COUNT(*) FROM Nsys_producto GROUP BY CALIDAD_DETALLE ORDER BY 2 DESC LIMIT 30;`
    Debe traer grados enteros como en NSYS (`A100`, `MsA-`, `B`…). **Si no (p. ej. solo `A` para los `A100`), PARAR y avisar:**
    `Maestro.sku` buscaría mal y hay que ajustarlo antes de seguir;
  - un iPhone 13 128GB del espejo para el móvil sintético:
    `SELECT GOODS, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR FROM Nsys_movil WHERE MODELO = '13' AND CAPACIDAD = '128GB' AND ES_ESIM = FALSE AND COLOR IS NOT NULL ORDER BY VISTO_PRIMERO DESC LIMIT 1;`
- **Bloque 2 (escribe), con el OK de Claude:**
  - la migración: `git -C gestion-reparaciones-servidor show origin/main:sql/migracion-sku-taller.sql | m -t` (vista previa 0 y
    el `CREATE TABLE`); después `SHOW CREATE TABLE Taller_cambio\G`;
  - el **móvil sintético** de las E2E (IMEI que no es de nadie; copia el producto del iPhone 13 128GB del bloque 1):

```sql
INSERT INTO Nsys_movil (IMEI_SN, BATCH, GOODS, GRADE, STATUS, LOCATION, SUPPLIER, RECEIVING_DATE, SOLD_DATE, SKU, MODELO,
  MODELO_MAESTRO, CAPACIDAD, COLOR, GRADO_DETALLE, ES_ESIM, UBICACION, UBICACION_ULTIMA, ESTADO, VISTO_PRIMERO, CAMBIADO)
SELECT '350000000009921', 'BT-E2E', GOODS, 'B', 'Available', 'CAJA LISTOS', 'Proveedor E2E', '2026-10-01', NULL, NULL,
  MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, 'B', ES_ESIM, 'Listos', 'Listos', 'STOCK', '2026-10-21 10:00:00', '2026-10-21 10:00:00'
FROM Nsys_movil WHERE MODELO = '13' AND CAPACIDAD = '128GB' AND ES_ESIM = FALSE AND COLOR IS NOT NULL
ORDER BY VISTO_PRIMERO DESC LIMIT 1;
SELECT IMEI_SN, GOODS, GRADE, COLOR FROM Nsys_movil WHERE IMEI_SN = '350000000009921';
```

  La importación no lo toca (no viene en los Items: cuenta como «móvil anterior que no venía en el fichero»).
  - `pull` de servidor y web y `docker compose up -d --build`, **primero el servidor y luego la web** (avisar de que tarda
    ~1 min sin salida); `Started App` sin `ERROR`.
- **Comprobaciones de Claude desde el PC:**
  - `/version.json` da 0.9.21;
  - E2E de una en una, con ≥ 60 s entre ficheros y `E2E_BASE_URL` de preprod (cuentas de `~/.env.e2e`): `sku-taller`,
    `stock-telefonos`, `roles-stock` y `taller`. Si alguna da el 503 del límite de inicios de sesión, esperar un minuto y
    repetir solo ese fichero.
- **Prueba del cierre con el móvil sintético (con el usuario):**
  - desde la web, corregir el grado del `350000000009921` a A con batería nueva (A100), con motivo;
  - SQL: `UPDATE Nsys_movil SET GRADE = 'A100' WHERE IMEI_SN = '350000000009921';` (simula que NSYS lo alcanza);
  - tras la siguiente importación `OK` de preprod (≤ 35 min; la píldora de NSYS lo dice), sale de «Por pasar a NSYS» y el
    rastro dice «Pasado a NSYS: grado A100»;
  - si el modelo tiene otro color en el maestro: corregir el color, `UPDATE … SET COLOR = '<un tercer color del maestro>'`,
    siguiente importación → «Manda NSYS: color …» y deja de valer lo del taller.
- **Pruebas con datos reales (usuario, sin guardar nada fuera de preprod):** abrir el diálogo en dos o tres móviles reales y
  comparar los colores, el SKU de la vista previa y el producto a poner con el maestro y con NSYS; cerrar sin guardar (o
  guardar y «Volver a lo de NSYS»: en preprod se puede).
- **Limpieza (bloque 3, escribe):**

```sql
UPDATE Taller_cambio SET ID_CAMBIO_ANTERIOR = NULL WHERE IMEI_SN = '350000000009921';
DELETE FROM Taller_cambio WHERE IMEI_SN = '350000000009921';
DELETE FROM Nsys_movil WHERE IMEI_SN = '350000000009921';
SELECT COUNT(*) FROM Nsys_movil WHERE IMEI_SN = '350000000009921';
```

- **Vuelta atrás:** servidor `main` anterior y web 0.9.20 + `up -d --build`. `Taller_cambio` puede quedarse (nadie la usa) o
  quitarse con `DROP TABLE Taller_cambio;`.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Al final, entrada en `despliegue_preprod.md` con commits, E2E, la prueba del cierre, lo visto con datos reales y la limpieza.
3. Si una E2E falla por la prueba y no por el programa, se arregla en la rama, se vuelve a pasar y se apunta.
4. **Producción solo cuando** las pruebas cuadran y el usuario da el OK.

---

### Task 15: Producción

**Files:**
- Create: `Apuntes/prod-v0921.md` (guion)
- Modify: `Apuntes/despliegue_vdc_produccion.md` (registro)

- [ ] **Step 1: Guion**

Basado en `Apuntes/prod-v0917.md` (servidor + web con migración). **Cada bloque empieza con `hostname` y la comprobación
`PRODUCCIÓN (ok)`** (`backup-erp.sh` existe).

- **Bloque 1:** copia con `backup-erp.sh`, commits y contenedores; `SELECT COUNT(*) … TABLE_NAME = 'Taller_cambio'` → 0.
- **Bloque 2:** la migración `migracion-sku-taller.sql` de `origin/main` (vista previa 0 y `CREATE TABLE`); `SHOW CREATE TABLE`.
- **Bloque 3:** `pull` y `up -d --build`, primero el servidor y luego la web; `Started App` sin `ERROR`.
- **Comprobación ligera desde el PC, sin escribir datos:**
  - versión 0.9.21 en `/version.json`;
  - sin letrero de preproducción (`preprod/comprobar-letrero.cjs` con `URL=https://erp.fonestore.es ESPERA=no`);
  - API sin sesión → 403 en `GET /api/stock-telefonos/por-pasar` y en `PUT /api/stock-telefonos/350000000000001/taller`.
- **A mano (usuario, sin guardar nada):**
  - con su cuenta de supertécnico: «Stock de teléfonos» con la pestaña «Por pasar a NSYS (0)», y el diálogo «Corregir…» de un
    móvil real abierto y cerrado sin guardar;
  - con una cuenta de técnico: sin cambios (ni grupo «Teléfonos» ni ficha de NSYS).
- **Momento:** en horario (migración aditiva, corte de ~30 s, la web 0.9.20 funciona con el servidor nuevo, vuelta atrás
  simple).
- **Aviso al taller:** «Hemos actualizado el programa. En "Stock de teléfonos" (supertécnicos) el color, el grado y el SKU
  tienen en cuenta lo que cambia el taller, y hay una pestaña "Por pasar a NSYS" con lo que hay que cambiar en NSYS. En la
  ficha de un IMEI se puede corregir el color, el grado o la batería. Recargad la página. Novedades en `NOVEDADES-v0.9.21`.»
- **Vuelta atrás:** servidor y web anteriores + `up -d --build`; la tabla puede quedarse.

- [ ] **Step 2: Ejecución con el usuario y cierre**

1. Registro en `despliegue_vdc_produccion.md`: commits, hora y comprobaciones.
2. Fecha real en `[0.9.21]` del CHANGELOG de la web: commit, push y gitlink (con el OK del usuario).
3. `plan-futuro.md`: la 0.9.21 (pieza 1) marcada `[x]`; la pieza 2 sigue como `[ ]`.
4. Tags `v0.9.21` cuando lo diga el usuario.
5. Worktrees y ramas borrados.
6. Memoria del proyecto actualizada (`project_stock_telefonos_nsys` y `project_produccion_vdc`).

