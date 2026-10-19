# 0.9.18 — Stock de teléfonos: columna compacta, buscador de SKU, «Quién lo tiene» y «Revisado» · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que «Stock de teléfonos» responda sin salir de la pantalla a «¿cuántos hay de cada color y grado?», «¿dónde está
este SKU?» y «¿quién tiene este móvil?»; que la ficha del IMEI diga cuándo se revisó, y que se vea desde la web si el
volcado de NSYS funciona.

**Architecture:**
- **Servidor** (paquete `com.reparaciones.servidor.nsys`, sin tocar la base de datos):
  - una consulta nueva de `NsysDAO` con las asignaciones abiertas (A/AG/AP sin `FECHA_FIN`), cruzadas en Java por IMEI con
    el espejo: cada móvil del stock lleva sus `trabajos`;
  - una regla pura `Revision` («sin caja» → caja) que usan la ficha y el rastro;
  - dos rutas nuevas en `StockTelefonosController`: `POST /comprobar` (lista de IMEIs) y `GET /importaciones` (las últimas
    importaciones y el progreso del histórico).
- **Web** (módulo `telefonos` y `taller/imeis`):
  - reglas puras nuevas: `quien.ts` («Quién lo tiene» y descuadre), `grado.ts` (color por familia), `buscador.ts` (modelo o
    SKU) y `varios.ts` (pegar varios IMEIs); `modelo.ts` cambia a una fila por modelo, pestañas de capacidad, filtros con
    varios valores (técnico) y la tabla color × grado;
  - pantalla: columna compacta, pestañas, tabla color × grado, columnas nuevas, diálogo de varios IMEIs, popover de la
    píldora de NSYS e indicador «NSYS sin datos» en la barra superior;
  - IMEIs: «Revisado …» en la ficha y «Revisado →» en el rastro.
- **E2E** de solo lectura en preproducción.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5, Mockito y MockMvc.
- **Web:** React 19 + TypeScript, React Router, TanStack Query/Table, openapi-fetch y Radix (Dialog, Popover). Pruebas con
  Vitest, Testing Library, user-event, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-19-v0918-stock-telefonos-quien-lo-tiene-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-10-19-v0918-prototipo-stock-telefonos.html`, con los tres (D) encendidos.

## Global Constraints

**Versión, código de partida y base de datos**
- Versión **0.9.18**. Toca servidor y web. **No toca la base de datos:** ni migración, ni `crear_bd.sql`, ni `schema.md`.
- **Se empieza cuando la 0.9.17 está en producción** (spec §9). Las ramas salen del `main` de ese momento (hoy servidor
  `d2b717d` y web `f460c75`); si la 0.9.17 recibe arreglos antes, ya estarán en `main`.

**Ramas, worktrees y registro**
- Todo va en la rama **`dev_v0918_stock_telefonos`**, en worktrees. Los clones normales no se tocan.
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0918` (lo crea la Task 1).
  - Web: `/c/Users/dev/Documents/wt/web-0918`, con `npm ci` dentro (lo crea la Task 5).
  - Las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan se refieren a esos worktrees;
    `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0918/`** (raíz).

**Commits, merge y datos de prueba**
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- Commits en español sin tildes, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- **Repos públicos:** datos de prueba sintéticos (técnicos «Técnico 1/2/3», IMEI `35000000000xxxx`, lotes `BT-9001`…). Nunca
  identificadores de Drive, correos de cuentas de servicio, IP, nombres reales ni datos reales de NSYS.

**Reglas de rol** (spec §3)
- `/api/stock-telefonos/**`, también `POST /comprobar` y `GET /importaciones`: la guarda de clase
  `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")`, que ya existe. No hay jerarquía de roles.
- **Web:** el popover de importaciones va dentro de «Stock de teléfonos» (ya guardada). El indicador de la barra solo para
  `esAdminOSuperTecnico(sesion)`; el TECNICO **no hace la petición** (`useEstadoNsys(false)`).

**Horas y umbrales**
- Todas las horas que da la API son de **Europe/Madrid**, como `LocalDateTime` sin zona. Las de `Reparacion` (UTC, como el
  resto de la BD) pasan a Madrid con `NsysDAO.utcAMadrid`, también `FECHA_ASIG` y `ENTREGADO_AT` de los trabajos abiertos. La
  web corta las horas del texto, sin `formatear` de `shared/lib/fechas.ts`.
- **Descuadre:** trabajo abierto asignado hace **más de 60 minutos** (`MINUTOS_DESCUADRE = 60`) con el móvil en «Listos» o de
  pedido. Se cuenta en minutos de reloj desde la hora de la carga del stock (`dataUpdatedAt`).
- **Huecos del popover:** más de **45 minutos hábiles** (`HUECO_MINUTOS = 45`) entre dos exportaciones seguidas, contando solo
  de 07:00 a 23:00 (la misma cuenta que `AvisoNsys.minutosHabiles`).
- **`POST /comprobar`:** como mucho **500** IMEIs (`MAX_COMPROBAR` en el servidor, `MAX_IMEIS` en la web); más da 400. Los
  `IN` van en tandas de 500 (`NsysDAO.TANDA_IN`).
- **`GET /importaciones`:** `limite` 20 por defecto; por encima de 50 se queda en 50 (`LIMITE_IMPORTACIONES`), y por debajo de
  1, en 1.
- **Cruce espejo ↔ taller:** por `NsysDAO.claveCruce(imeiSn)` (sin espacios a los lados y en mayúsculas).

**Textos exactos de la web**
- **Columna lateral:**
  - buscador: `aria-label` «Buscar modelo o SKU», placeholder «Buscar modelo o SKU…»;
  - fila de modelo: botón con `aria-label` «iPhone 13 Pro: 12 en stock»; pastilla: «iPhone 13 Pro 128GB: 4»;
  - lista de SKU: «SKU EN STOCK», «y N más», pie «Enter: todos los que empiezan así» o «Hay SKU de N modelos: elige uno o
    escribe más»;
  - avisos del Enter: «Hay SKU de N modelos que empiezan así: elige uno o escribe más» y «Ningún móvil en stock tiene un SKU
    que empiece así»;
  - sin filas: «Ningún modelo ni SKU en stock empieza así.»
- **Cabecera del modelo:**
  - pestañas «Todas» y «128 GB», con `aria-label` «128 GB: 4»;
  - «N móviles» (1 «móvil»);
  - pastilla «SKU i13PRO128GrafitoA ×» o «SKU empieza por i13PRO128 ×», con el × de `aria-label` «Quitar el filtro de SKU»;
  - «Quitar filtros (n)».
- **Tabla color × grado:**
  - `aria-label` «Color y grado», esquina «color \ grado», fila y columna «Total»;
  - casilla con `aria-label` «Grafito A: 4» y `title` «Grafito A: 4 listos · 2 en taller · 1 en revisión»; vacía, «·»;
  - cabeceras con `aria-label` «Color Grafito» y «Grado A»;
  - ayuda «Pulsa una casilla para ver ese color y grado; el color o el grado de la cabecera, para ver solo eso. Ctrl+clic
    suma casillas.»; sin stock, «No hay móviles en stock con estos filtros.»
- **Filtros:** «Batería: todas», «Ubicación: todas», «Técnico: todos», con las opciones especiales «Sin asignar (en caja de
  taller)» y «Con descuadre».
- **Avisos:** «⚠ N en caja de taller sin asignar» y «⚠ N con descuadre».
- **Tabla de móviles:**
  - columnas «IMEI · Cap. · Color · Grado · SKU · Ubicación · Quién lo tiene · Días» («Cap.» solo con «Todas»);
  - marcas «100» (`title` «Batería nueva») y «eSIM»; «NO CUENTA · De pedido» y «NO CUENTA · No cuenta como stock»;
  - `aria-label` «Tabla de móviles» en la región que la envuelve;
  - pie: «"Quién lo tiene" sale de las asignaciones abiertas del ERP, al momento; la caja, de la última exportación de NSYS.
    Pasa el ratón por encima para ver desde cuándo. El IMEI abre el detalle del móvil.»
- **«Quién lo tiene»** (texto plano, el mismo que la celda):
  - «Técnico 1», «Técnico 1 · glass Técnico 3 sin entregar», «Técnico 3 (glass) llegó 10:30», «Técnico 3 (glass) llegó
    15/10», «Técnico 2 (pulido)»;
  - «⚠ En caja, sin asignar» (ámbar), «⚠ Técnico 1: trabajo abierto» (rojo) y «—»;
  - detalle: «Reparación de X desde el 17/10», «Glass de X desde el 18/10: el móvil aún no le ha llegado» / «…: el móvil le
    llegó a las 10:30» / «…: el móvil le llegó el 18/10 a las 10:30», «Pulido de X desde el 17/10», «NSYS dice "Listos", pero
    hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?» y «Está en una caja de taller y nadie lo tiene asignado»,
    unidos con « · ».
- **Pegar varios IMEIs:**
  - botón «Pegar varios IMEIs»; diálogo «Comprobar una lista de IMEIs»;
  - ayuda «Pega los IMEIs como vengan: uno por línea, separados por comas o copiados de una columna de Excel. Se buscan en
    todos los móviles de NSYS, también en los vendidos.»;
  - «Comprobar», «N líneas», «Comprobando…», «Copiar el resultado (para Excel)» y el aviso «Copiado: pégalo en Excel»;
  - error «Como mucho 500 IMEIs por vez: hay N.»;
  - tabla `aria-label` «Resultado», columnas «# · IMEI · Modelo · Color · Grado · Ubicación · Quién lo tiene · Estado»;
  - estados «No es un IMEI», «Repetido (línea n)», «No está en NSYS», «Libre», «De pedido», «No cuenta», «Vendido
    dd/mm/aaaa», «Vendido» y «Baja en NSYS»;
  - resumen «N en stock», «N de pedido», «N no cuenta(n)», «N vendido(s)», «N de baja», «N no está(n) en NSYS», «N
    repetido(s)», «N no es / no son un IMEI».
- **Popover de la píldora:**
  - `aria-label` «Importaciones de NSYS»; título «Últimas importaciones de NSYS»; lista `aria-label` «Importaciones»;
  - resultado «OK», «RECHAZADO», «ERROR», «en curso»; «N cambios» (1 «cambio»); «N s»;
  - hueco «⚠ falta un Items entre 15:31 y 16:31»;
  - histórico: «Histórico: N ficheros hechos, hasta el Items del 03/05/2025; termina en el del 16/10/2026» (1 «fichero
    hecho») y, terminado, «Histórico: terminado (N ficheros)»;
  - «Cargando…», «No se pudo cargar la lista.» y «Todavía no hay importaciones.»
- **Barra superior:** enlace «NSYS sin datos» a `/telefonos/stock`.
- **Ficha del IMEI:** «Revisado el 17/10/2026, hacia las 10:31», «Revisado (devolución) el …», «Pendiente de revisar desde
  hace N días» (1 «día»; 0, «desde hoy»).
- **Rastro:** «Revisado → Taller · Pulir» y «Revisado (devolución) → …»; en la leyenda, «Revisado».

**Colores (tokens del tema, spec §4.8)**
- **Grado**, píldora con borde de 1,5 px y fondo `bg-superficie`:
  - A: `border-tipo-reparacion-text text-tipo-reparacion-text` (azul);
  - B: `border-tipo-pulido-text text-tipo-pulido-text` (violeta);
  - C: `border-aviso-conflicto-text text-aviso-conflicto-text` (naranja);
  - Ms…: `border-tipo-glass-text text-tipo-glass-text` (verde azulado);
  - ID: `border-fila-incidencia-brd text-fila-incidencia-brd` (rosa);
  - `n/a`: `border-dashed border-fila-solicitud-brd text-fila-solicitud-brd` (ámbar, discontinuo);
  - cualquier otro o vacío: `border-pill-borde text-azul-medio`.
- **Reparto** listos / taller / revisión: `bg-fila-recibido-brd`, `bg-tipo-reparacion-text` y `bg-ambar` (los del rastro).
- **Resultado de una importación:** OK `bg-fila-recibido-bg text-fila-recibido-brd`, RECHAZADO
  `bg-fila-solicitud-bg text-fila-solicitud-brd`, ERROR `bg-urgente-bg text-fila-urgente-brd`, en curso
  `bg-badge-neutro-bg text-azul-gris`.

**Comandos**
- **Servidor** (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest='Clase1,Clase2'`) dentro de `/c/Users/dev/Documents/wt/servidor-0918`.
- **Web:** `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint` dentro de `/c/Users/dev/Documents/wt/web-0918`.
- **Lint de la web:** `eslint-plugin-react-hooks` 7 (reglas del compilador): nada de `new Date()` ni `Date.now()` en el cuerpo
  de un componente o un hook. La «hora de la carga» es `stock.dataUpdatedAt` (número), y las funciones puras de
  `nsys.ts` llevan `ahora: Date = new Date()` por defecto, como en la 0.9.17.

**Preproducción y producción**
- **Claude no hace SSH.** Prepara los guiones en `Apuntes/` y el usuario los ejecuta y pega la salida.
- Cada bloque de guion empieza comprobando la máquina: `hostname` + `backup-erp.sh`, que solo existe en producción.
- Sin migración: se despliegan juntos, **primero el servidor y luego la web**. La web 0.9.17 funciona con el servidor 0.9.18.

## Ajustes a la spec (se suben a la spec en el commit del plan)

1. **`historico` siempre viene** en `GET /importaciones`, con un campo `total` (importaciones `HISTORICO` de cualquier
   resultado): sin ninguna, `total` = 0 y la web no pinta la línea. Así el contrato no lleva un objeto nullable, que springdoc
   no marca de forma fiable junto a un `$ref`. Con el histórico terminado (`ultimo` = `hasta`) la línea dice «Histórico:
   terminado (N ficheros)».
2. **Pestañas de capacidad:** la web no tiene un catálogo de capacidades. Salen las capacidades que el espejo tiene de ese
   modelo, de cualquier estado de los que llegan al stock. Sin stock, apagadas; con el interruptor encendido se pueden abrir
   si tienen algo que no cuenta.
3. **Escribir en el buscador no cambia el modelo abierto:** solo filtra la lista. Se abre con un clic, con Enter o eligiendo
   un SKU. En la 0.9.17 el panel seguía al primer modelo visible.
4. **Ctrl+clic** suma a lo elegido el color y el grado de la casilla, como el prototipo: los filtros siguen siendo por color
   y por grado (Grafito A + Oro B elige Grafito y Oro en A y B).
5. **Pegar varios IMEIs:**
   - un número de serie lleva letras y cifras (8-20, con alguna de cada); unas cifras que no son 15 son «No es un IMEI»;
   - los números de serie se pasan a mayúsculas antes de comprobarlos;
   - el resumen añade «N no cuenta(n)» y «N de baja» cuando los hay.
6. **`PeticionComprobar`** es un record del paquete (no privado) para que el test lo construya; su esquema se llama igual,
   `StockTelefonosPeticionComprobar`.
7. **Un móvil de pedido** no sale «en caja, sin asignar» aunque su última caja fuera de taller: ya no está en esa caja.
8. **Ficha:** la de una devolución dice «Revisado (devolución) el …»; «Pendiente de revisar desde hoy» con 0 días.
9. **`useEstadoNsys` no abre el diálogo de error** (`silenciarError`): la píldora ya dice «no se pudo comprobar», y el
   indicador de la barra va en todas las vistas.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`, paquete `src/main/java/com/reparaciones/servidor/nsys/` salvo que se diga otra cosa)

| Fichero | Responsabilidad |
|---|---|
| `TrabajoAbierto.java` (nuevo) | Un trabajo abierto de un IMEI: categoría, técnico, desde y entrega (glass) |
| `Revision.java` (nuevo) | Regla pura de «revisado»: qué cambio de caja es la revisión, la última del lote y desde cuándo está pendiente |
| `RegistroImportacion.java`, `ResumenHistorico.java` (nuevos) | Filas de `Nsys_importacion` con sus horas y el resumen del histórico |
| `NsysDAO.java` | Asignaciones abiertas (todas o de unos IMEI), lotes de unos IMEI, últimas importaciones y resumen del histórico |
| `EventoRastro.java`, `Rastro.java` | El rastro marca el evento de revisión (`revision`) |
| `StockTelefonosController.java` | `trabajos` en el stock, revisión en la ficha, `POST /comprobar` y `GET /importaciones` |
| `src/test/java/…/nsys/{NsysDAOTest,RastroTest,RevisionTest,StockTelefonosControllerTest}.java` | Pruebas sin base de datos |
| `src/test/java/…/controller/RolesStockTelefonosTest.java`, `src/test/java/…/OpenApiContractTest.java` | Guarda de roles y contrato |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts` | Contrato y alias de tipos |
| `src/test/server.ts` | Handlers por defecto de MSW: estado de NSYS al día y lista de importaciones vacía |
| `src/modules/telefonos/api.ts` | `useEstadoNsys(activo)`, `useImportacionesNsys()`, `useComprobarImeis(imeis)` |
| `src/modules/telefonos/test/fabrica.ts` | Fábricas sintéticas: móvil, trabajo, comprobado, importación y móvil ya preparado |
| `src/modules/telefonos/stock/quien.ts`, `CeldaQuien.tsx` | «Quién lo tiene»: textos, detalle, descuadre, «en caja» y etiquetas del filtro |
| `src/modules/telefonos/stock/grado.ts`, `PildoraGrado.tsx` | Grado con color por familia |
| `src/modules/telefonos/stock/modelo.ts` | Reglas del stock: una fila por modelo, pestañas, filtros, SKU, tabla color × grado y orden |
| `src/modules/telefonos/stock/estado.ts` | Store de la pantalla: modelo, capacidad, filtros y SKU, interruptor y texto del buscador |
| `src/modules/telefonos/stock/buscador.ts`, `BuscadorModelo.tsx` | Buscador de modelo o SKU de la columna |
| `src/modules/telefonos/stock/{ListaModelos,BarraReparto,PestanasCapacidad,PastillaSku,MatrizColorGrado,FiltrosMoviles}.tsx`, `columnas.tsx` | Piezas de la pantalla |
| `src/modules/telefonos/stock/varios.ts`, `ComprobarImeis.tsx`, `BuscadorImei.tsx` | Pegar varios IMEIs |
| `src/modules/telefonos/stock/nsys.ts`, `PildoraNsys.tsx`, `ListaImportacionesNsys.tsx` | Huecos, horas e histórico; popover de la píldora |
| `src/modules/telefonos/IndicadorNsys.tsx`, `src/app/shell/TopBar.tsx` | Indicador «NSYS sin datos» en la barra superior |
| `src/modules/telefonos/stock/StockTelefonosPage.tsx` | La pantalla |
| `src/shared/lib/telefonoNsys.ts` | `diasDesdeNsys`: días desde un día de NSYS (hora de Madrid) |
| `src/modules/taller/imeis/{rastro.ts,FichaMovil.tsx,PanelRastro.tsx}`, `src/modules/taller/test/fabrica.ts` | «Revisado» en la ficha y en el rastro |
| `tests/e2e/stock-telefonos.spec.ts` | E2E de solo lectura en preprod |
| `package.json`, `CHANGELOG.md`, `docs/paridad/imeis.md` | Versión 0.9.18 y documentación |

**Raíz y apuntes** (fuera de los worktrees)

| Fichero | Responsabilidad |
|---|---|
| `docs/novedades/NOVEDADES-v0.9.18.md` | Novedades para el taller |
| `Apuntes/preprod/v0918-preprod.md`, `Apuntes/prod-v0918.md` | Guiones de despliegue (privados) |

---

### Task 1: Servidor — trabajos abiertos de cada móvil del stock

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/TrabajoAbierto.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java` (`stock()` y `MovilStock`)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java`, `…/nsys/StockTelefonosControllerTest.java`, `…/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `Rastro.categoria(idRep)` (A/AG/AP → REPARACION/GLASS/PULIDO) y `NsysDAO.utcAMadrid(Timestamp)`, ya en la 0.9.17.
- Produces:
  - `public record TrabajoAbierto(String categoria, String tecnico, LocalDateTime desde, LocalDateTime entregado)`; esquema `TrabajoAbierto`.
  - En `NsysDAO`:
    - `SQL_TRABAJOS_ABIERTOS`, `ORDEN_TRABAJOS` y `TANDA_IN = 500`;
    - `Map<String, List<TrabajoAbierto>> trabajosAbiertos()` y `trabajosAbiertos(Collection<String> imeis)`, con la clave
      de `claveCruce`;
    - `public static String claveCruce(String imeiSn)`;
    - `record FilaTrabajoAbierto(String imei, TrabajoAbierto trabajo)`, `static FilaTrabajoAbierto filaTrabajoAbierto(ResultSet, int)`
      y `static Map<String, List<TrabajoAbierto>> porImeiDelTaller(List<FilaTrabajoAbierto>)`;
    - `static <T> List<List<T>> trozos(List<T> lista, int tamano)` y `static String marcas(int n)` («?, ?, ?»).
  - `GET /api/stock-telefonos`: cada `MovilStock` añade `List<TrabajoAbierto> trabajos` (vacía si no tiene).

- [x] **Step 1: Worktree del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git status --short   # vacío
git worktree add -b dev_v0918_stock_telefonos /c/Users/dev/Documents/wt/servidor-0918 main
cd /c/Users/dev/Documents/wt/servidor-0918 && git log --oneline -1
```

Expected: el último commit de `main` (la 0.9.17 ya en producción).

- [x] **Step 2: Tests (fallan)**

En `NsysDAOTest.java`, añade los imports `java.sql.ResultSet` y `java.sql.SQLException`, y estos tests al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.18: trabajos abiertos (spec §6.1)

    @Test void lasAsignacionesAbiertasSonLasAConFechaFinNulaYConElTecnico() {
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("r.ID_REP LIKE 'A%'"), "A, AG y AP");
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("r.FECHA_FIN IS NULL"));
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("JOIN Tecnico t ON t.ID_TEC = r.ID_TEC"));
    }

    @SuppressWarnings("unchecked")
    @Test void lasAsignacionesAbiertasDeTodoElTallerVanEnUnaConsulta() {
        assertTrue(dao.trabajosAbiertos().isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_TRABAJOS_ABIERTOS + NsysDAO.ORDEN_TRABAJOS), any(RowMapper.class));
    }

    private static ResultSet filaDeReparacion(String imei, String idRep, LocalDateTime asignada, LocalDateTime entregada)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn(imei);
        when(rs.getString("ID_REP")).thenReturn(idRep);
        when(rs.getString("NOMBRE")).thenReturn("Técnico 3");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(Timestamp.valueOf(asignada));
        when(rs.getTimestamp("ENTREGADO_AT")).thenReturn(entregada == null ? null : Timestamp.valueOf(entregada));
        return rs;
    }

    @Test void unaAsignacionAbiertaLlevaSuCategoriaYSusHorasEnMadrid() throws SQLException {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 16, 8, 30);
        NsysDAO.FilaTrabajoAbierto glass = NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "AG20261016_1", utc, utc.plusHours(1)), 0);
        assertEquals("350000000000001", glass.imei());
        assertEquals(new TrabajoAbierto("GLASS", "Técnico 3", LocalDateTime.of(2026, 10, 16, 10, 30),
                LocalDateTime.of(2026, 10, 16, 11, 30)), glass.trabajo(), "UTC → Madrid (verano, +2)");
        assertEquals("PULIDO", NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "AP20261016_1", utc, null), 0).trabajo().categoria());
        TrabajoAbierto reparacion = NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "A20261016_1", utc, utc), 0).trabajo();
        assertEquals("REPARACION", reparacion.categoria());
        assertNull(reparacion.entregado(), "la entrega solo cuenta en la glass");
    }

    @Test void losTrabajosSeAgrupanPorImeiYUnNumeroDeSerieEnMinusculasCruza() {
        TrabajoAbierto r = new TrabajoAbierto("REPARACION", "Técnico 1", HOY, null);
        TrabajoAbierto g = new TrabajoAbierto("GLASS", "Técnico 3", HOY, null);
        Map<String, List<TrabajoAbierto>> mapa = NsysDAO.porImeiDelTaller(List.of(
                new NsysDAO.FilaTrabajoAbierto("350000000000001", r), new NsysDAO.FilaTrabajoAbierto("350000000000001", g),
                new NsysDAO.FilaTrabajoAbierto(" f2lxabc123 ", r)));
        assertEquals(List.of(r, g), mapa.get("350000000000001"), "reparación y glass: dos trabajos, en su orden");
        assertEquals(List.of(r), mapa.get(NsysDAO.claveCruce("F2LXABC123")));
        assertEquals("F2LXABC123", NsysDAO.claveCruce(" f2lxabc123 "));
        assertEquals("", NsysDAO.claveCruce(null));
    }

    @Test void losTrabajosDeUnosImeisVanEnTandasDeQuinientos() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.trabajosAbiertos(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(0).startsWith(NsysDAO.SQL_TRABAJOS_ABIERTOS + " AND r.IMEI IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).contains(" AND r.IMEI IN (?)" + NsysDAO.ORDEN_TRABAJOS), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.trabajosAbiertos(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }
```

En `StockTelefonosControllerTest.java`, añade el import `java.util.Map` y este test tras `stockConGradoYBateriaSeparadosYLaUbicacionDeLosDePedido`:

```java
    @Test void stockConLosTrabajosAbiertosDeCadaMovil() {
        when(dao.enEmpresa()).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A", "Taller · Reparación", "Taller · Reparación", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        LocalDateTime asignada = LocalDateTime.of(2026, 10, 16, 9, 30);
        when(dao.trabajosAbiertos()).thenReturn(Map.of("350000000000001", List.of(
                new TrabajoAbierto("REPARACION", "Técnico 1", asignada, null),
                new TrabajoAbierto("GLASS", "Técnico 3", asignada.plusHours(1), asignada.plusHours(2)))));
        JsonNode stock = json(api.stock());
        JsonNode trabajos = stock.get(0).get("trabajos");
        assertEquals(2, trabajos.size(), "reparación y glass: dos trabajos");
        assertEquals("REPARACION", trabajos.get(0).get("categoria").asText());
        assertTrue(trabajos.get(0).get("entregado").isNull());
        assertEquals("Técnico 3", trabajos.get(1).get("tecnico").asText());
        assertTrue(trabajos.get(1).get("desde").asText().startsWith("2026-10-16T10:30"));
        assertTrue(trabajos.get(1).get("entregado").asText().startsWith("2026-10-16T11:30"));
        assertEquals(0, stock.get(1).get("trabajos").size(), "sin trabajos abiertos, lista vacía");
    }
```

En `OpenApiContractTest.java`, método `elContratoPublicaElStockDeTelefonos`, cambia la línea

```java
        assertNoNullable(esquemas, "StockTelefonosMovilStock", "imeiSn", "lote", "grado", "bateriaNueva", "esEsim", "estado");
```

por

```java
        assertNoNullable(esquemas, "StockTelefonosMovilStock", "imeiSn", "lote", "grado", "bateriaNueva", "esEsim", "estado",
                "trabajos");
        // 0.9.18: los trabajos abiertos de cada móvil (spec 2026-10-19 §6.1).
        assertNullable(esquemas, "TrabajoAbierto", "entregado");
        assertNoNullable(esquemas, "TrabajoAbierto", "categoria", "tecnico", "desde");
```

- [x] **Step 3: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest'`
Expected: error de compilación: no existe `TrabajoAbierto` ni `NsysDAO.trabajosAbiertos`.

- [x] **Step 4: Implementación**

Crea `TrabajoAbierto.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un trabajo abierto del taller de un IMEI (spec 0.9.18 §6.1): una asignación A… (REPARACION), AG… (GLASS) o AP…
 *  (PULIDO) sin FECHA_FIN, con su técnico y la hora de asignación. {@code entregado} es la llegada del móvil a la glass
 *  (ENTREGADO_AT) y solo se rellena en la glass. Horas de Madrid. */
public record TrabajoAbierto(String categoria, String tecnico, LocalDateTime desde,
                             @Schema(nullable = true) LocalDateTime entregado) {}
```

En `NsysDAO.java`:

1. Imports: añade `java.util.Collection` y `java.util.Locale`.
2. Tras la constante `COLUMNAS_IMPORTACION`, añade:

```java
    /** Las asignaciones abiertas del taller (A… reparación, AG… glass, AP… pulido; sin FECHA_FIN) con el técnico. Se
     *  cruzan con el espejo en Java por IMEI, para no depender de las intercalaciones de Nsys_movil y Reparacion
     *  (spec 0.9.18 §6.1). */
    static final String SQL_TRABAJOS_ABIERTOS = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.ENTREGADO_AT, t.NOMBRE"
            + " FROM Reparacion r JOIN Tecnico t ON t.ID_TEC = r.ID_TEC WHERE r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL";
    static final String ORDEN_TRABAJOS = " ORDER BY r.FECHA_ASIG, r.ID_REP";
    /** Tamaño de las tandas de los IN por IMEI (spec 0.9.18 §6.2). */
    static final int TANDA_IN = 500;
```

3. Tras el método `trabajos(String imei)`, añade:

```java
    /** Las asignaciones abiertas de todo el taller, por IMEI ({@link #claveCruce}). Son unos cientos como mucho. */
    public Map<String, List<TrabajoAbierto>> trabajosAbiertos() {
        return porImeiDelTaller(jdbc.query(SQL_TRABAJOS_ABIERTOS + ORDEN_TRABAJOS, NsysDAO::filaTrabajoAbierto));
    }

    /** Las asignaciones abiertas de estos IMEI, por IMEI, en tandas de {@value #TANDA_IN}. */
    public Map<String, List<TrabajoAbierto>> trabajosAbiertos(Collection<String> imeis) {
        List<FilaTrabajoAbierto> filas = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            filas.addAll(jdbc.query(SQL_TRABAJOS_ABIERTOS + " AND r.IMEI IN (" + marcas(tanda.size()) + ")" + ORDEN_TRABAJOS,
                    NsysDAO::filaTrabajoAbierto, tanda.toArray()));
        return porImeiDelTaller(filas);
    }

    /** Una fila de las asignaciones abiertas: el IMEI del taller y su trabajo. */
    record FilaTrabajoAbierto(String imei, TrabajoAbierto trabajo) {}

    static FilaTrabajoAbierto filaTrabajoAbierto(ResultSet rs, int n) throws SQLException {
        String categoria = Rastro.categoria(rs.getString("ID_REP"));
        LocalDateTime entregado = "GLASS".equals(categoria) ? utcAMadrid(rs.getTimestamp("ENTREGADO_AT")) : null;
        return new FilaTrabajoAbierto(rs.getString("IMEI"), new TrabajoAbierto(categoria, rs.getString("NOMBRE"),
                utcAMadrid(rs.getTimestamp("FECHA_ASIG")), entregado));
    }

    /** Los trabajos por IMEI, en el orden en que llegan (por fecha de asignación). */
    static Map<String, List<TrabajoAbierto>> porImeiDelTaller(List<FilaTrabajoAbierto> filas) {
        Map<String, List<TrabajoAbierto>> mapa = new HashMap<>();
        for (FilaTrabajoAbierto f : filas) mapa.computeIfAbsent(claveCruce(f.imei()), k -> new ArrayList<>()).add(f.trabajo());
        return mapa;
    }

    /** Clave para cruzar un IMEI/SN del espejo con el del taller: sin espacios a los lados y en mayúsculas (un número de
     *  serie puede venir en minúsculas de un lado y en mayúsculas del otro). */
    public static String claveCruce(String imeiSn) {
        return imeiSn == null ? "" : imeiSn.strip().toUpperCase(Locale.ROOT);
    }
```

4. Sustituye el método `trozos` por estos dos y añade `marcas` debajo:

```java
    static <T> List<List<T>> trozos(List<T> lista) {
        return trozos(lista, LOTE);
    }

    static <T> List<List<T>> trozos(List<T> lista, int tamano) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += tamano) trozos.add(lista.subList(i, Math.min(lista.size(), i + tamano)));
        return trozos;
    }

    /** «?, ?, ?» para un IN de {@code n} valores. */
    static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }
```

En `StockTelefonosController.java`:

1. Imports: añade `java.util.Map`.
2. Sustituye el método `stock()` por:

```java
    /** Los móviles que están en la empresa (en stock, de pedido o que no cuentan), cada uno con sus trabajos abiertos del
     *  taller (0.9.18). La web filtra en local y calcula «Quién lo tiene». */
    @GetMapping
    public List<MovilStock> stock() {
        LocalDate hoy = LocalDate.now(clock);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos();
        return dao.enEmpresa().stream().map(m -> new MovilStock(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(),
                m.capacidad(), m.color(), Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()),
                m.esEsim(), m.sku(), ubicacionVisible(m), m.estado().name(), dias(m, hoy),
                abiertos.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()))).toList();
    }
```

3. Sustituye el record `MovilStock` por:

```java
    private record MovilStock(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva, boolean esEsim,
                              @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, List<TrabajoAbierto> trabajos) {}
```

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. `RolesStockTelefonosTest` sigue en verde: el `@MockBean` devuelve un mapa vacío.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/TrabajoAbierto.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java \
  src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java \
  src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: trabajos abiertos de cada movil en el stock de telefonos (0.9.18)"
```

---

### Task 2: Servidor — «Revisado» en la ficha y en el rastro

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Revision.java`
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/RevisionTest.java`
- Modify: `…/nsys/EventoRastro.java`, `…/nsys/Rastro.java`, `…/nsys/StockTelefonosController.java` (`ficha()` y `FichaMovil`)
- Test: `…/nsys/RastroTest.java`, `…/nsys/StockTelefonosControllerTest.java`, `…/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `EventoNsys`, `MovilNsys`, `TipoEvento` y `NsysDAO.eventos(imeiSn)` (ordenados por `FECHA, ID_EVENTO`).
- Produces:
  - `Revision.ENTRADA = "ENTRADA"`, `Revision.DEVOLUCION = "DEVOLUCION"`.
  - `Revision.tipos(List<EventoNsys>) → List<String>` (paralela a la entrada; null donde no hay revisión).
  - `Revision.ultima(List<EventoNsys>, String lote) → Revision.Ultima(LocalDateTime fecha, boolean devolucion)` o null.
  - `Revision.pendienteDesde(MovilNsys, List<EventoNsys>) → LocalDate` o null.
  - `EventoRastro` añade al final `String revision` (null, `ENTRADA` o `DEVOLUCION`).
  - `FichaMovil` añade al final `LocalDateTime revisadoEl`, `boolean revisadoDevolucion` y `LocalDate pendienteRevisionDesde`.

- [x] **Step 1: Tests (fallan)**

Crea `RevisionTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Revisado» (spec 0.9.18 §5.1): de «sin caja» (En revisión) a una caja que no sea «Pedidos realizados». */
class RevisionTest {

    private static LocalDateTime t(int dia, int hora) {
        return LocalDateTime.of(2026, 10, dia, hora, 31);
    }

    private static EventoNsys ubicacion(String lote, LocalDateTime fecha, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, false, TipoEvento.UBICACION, de, a, aNsys);
    }

    private static MovilNsys movil(String ubicacion, LocalDate recepcion) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "", "Proveedor A",
                recepcion, null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, ubicacion, ubicacion,
                EstadoMovil.STOCK, t(1, 9), t(1, 9));
    }

    @Test void deEnRevisionAUnaCajaEsLaRevisionDeEntrada() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(17, 10), "En revisión", "Taller · Pulir", "CAJA PULIR"),
                ubicacion("BT-9001", t(18, 10), "Taller · Pulir", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList("ENTRADA", null), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(17, 10), false), Revision.ultima(e, "BT-9001"));
    }

    @Test void deEnRevisionAPedidosRealizadosEsUnaVentaSinRevisar() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(17, 10), "En revisión", null, "Pedidos realizados"));
        assertEquals(Collections.singletonList(null), Revision.tipos(e));
        assertNull(Revision.ultima(e, "BT-9001"));
    }

    @Test void soloElPrimerCambioDesdeEnRevisionDeCadaLoteEsDeEntrada() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9002", t(12, 10), "En revisión", "Taller · Glass", "CAJA GLASS"),
                ubicacion("BT-9001", t(14, 10), "Listos", "En revisión", ""),
                ubicacion("BT-9001", t(15, 10), "En revisión", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList("ENTRADA", "ENTRADA", null, null), Revision.tipos(e));
    }

    @Test void salirDeDevolucionAUnaCajaEsLaRevisionDeUnaDevolucion() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "Devolución", "Funcionalidad pendiente"),
                ubicacion("BT-9001", t(13, 10), "Devolución", "Taller · Reparación", "CAJA REP. OFICINA"),
                ubicacion("BT-9001", t(14, 10), "Devolución", null, "Pedidos realizados"));
        assertEquals(Arrays.asList("ENTRADA", null, "DEVOLUCION", null), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(13, 10), true), Revision.ultima(e, "BT-9001"));
    }

    @Test void laUltimaRevisionEsDelLoteQueSePide() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"));
        assertNull(Revision.ultima(e, "BT-9002"));
    }

    @Test void pendienteDesdeElUltimoEventoQueLoDejoEnRevision() {
        List<EventoNsys> e = List.of(
                new EventoNsys("350000000000001", "BT-9001", t(5, 9), false, TipoEvento.ENTRADA, null, "En revisión", ""),
                ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 12), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void pendienteSinEventosDesdeLaRecepcionDelLote() {
        assertEquals(LocalDate.of(2026, 10, 1),
                Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), List.of()));
    }

    @Test void fueraDeRevisionNoHayPendienteYSinEventosNoHayNada() {
        assertNull(Revision.pendienteDesde(movil("Listos", LocalDate.of(2026, 10, 1)), List.of()));
        assertEquals(List.of(), Revision.tipos(List.of()));
        assertNull(Revision.ultima(List.of(), "BT-9001"));
    }
}
```

Sustituye `RastroTest.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RastroTest {

    private static LocalDateTime t(int dia, int hora) {
        return LocalDateTime.of(2026, 9, dia, hora, 0);
    }

    @Test void uneNsysYTallerDelMasRecienteAlMasAntiguo() {
        List<EventoRastro> rastro = Rastro.unir(
                List.of(new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.ENTRADA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(8, 13), false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")),
                List.of(new TrabajoTaller("AG20260905_1", t(5, 9), null, "Técnico 3"),
                        new TrabajoTaller("G20260905_1", t(5, 9), t(8, 12), "Técnico 3"),
                        new TrabajoTaller("R20260930_2", t(30, 10), null, "Técnico 5")));

        assertEquals(List.of(
                new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION", null),
                new EventoRastro(t(8, 13), false, "NSYS", "UBICACION", "BT-9001", "Taller · Glass", "Listos", "CAJA LISTOS", null, null, null, null),
                new EventoRastro(t(8, 12), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "G20260905_1", "Técnico 3", "GLASS", null),
                new EventoRastro(t(5, 9), false, "ERP", "TRABAJO_ASIGNADO", null, null, null, null, "AG20260905_1", "Técnico 3", "GLASS", null),
                new EventoRastro(t(2, 0), true, "NSYS", "ENTRADA", "BT-9001", null, null, null, null, null, null, null)),
                rastro);
    }

    @Test void laVentaDelEsqueletoSeQuitaSiHayUnaConHora() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(12, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", t(12, 17), false, TipoEvento.VENTA, "Available", "Released", null),
                new EventoNsys("350000000000001", "BT-9002", t(20, 0), true, TipoEvento.VENTA, null, null, null)), List.of());
        assertEquals(List.of("BT-9002", "BT-9001"), rastro.stream().map(EventoRastro::lote).toList());
        assertEquals(List.of(true, false), rastro.stream().map(EventoRastro::soloDia).toList());
    }

    @Test void aLaMismaHoraVaPrimeroLoUltimoApuntadoYElTrabajoDelTaller() {
        // Esqueleto con entrada y venta el mismo día; un cambio de NSYS a la misma hora que un trabajo terminado.
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.ENTRADA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.VENTA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(8, 13), false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")),
                List.of(new TrabajoTaller("G20260905_1", t(5, 9), t(8, 13), "Técnico 3")));
        assertEquals(List.of("TRABAJO_TERMINADO", "UBICACION", "VENTA", "ENTRADA"),
                rastro.stream().map(EventoRastro::tipo).toList());
    }

    @Test void categoriaPorElIdDelTrabajo() {
        assertEquals("GLASS", Rastro.categoria("AG1"));
        assertEquals("GLASS", Rastro.categoria("G1"));
        assertEquals("PULIDO", Rastro.categoria("AP1"));
        assertEquals("PULIDO", Rastro.categoria("P1"));
        assertEquals("REPARACION", Rastro.categoria("A1"));
        assertEquals("REPARACION", Rastro.categoria("R1"));
    }

    @Test void elCambioDeCajaQueEsLaRevisionLlevaSuTipo() {
        // 0.9.18 (spec §5.3): de «En revisión» a una caja es ENTRADA; salir de «Devolución», DEVOLUCION.
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(2, 9), false, TipoEvento.UBICACION, "En revisión", "Taller · Pulir", "CAJA PULIR"),
                new EventoNsys("350000000000001", "BT-9001", t(4, 9), false, TipoEvento.UBICACION, "Taller · Pulir", "Devolución", "Funcionalidad pendiente"),
                new EventoNsys("350000000000001", "BT-9001", t(6, 9), false, TipoEvento.UBICACION, "Devolución", "Listos", "CAJA LISTOS")), List.of());
        assertEquals(Arrays.asList("DEVOLUCION", null, "ENTRADA"), rastro.stream().map(EventoRastro::revision).toList());
    }

    @Test void laRevisionNoSeDescuadraAlQuitarLaVentaDelEsqueleto() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(1, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", t(2, 9), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000001", "BT-9001", t(3, 9), false, TipoEvento.VENTA, "Available", "Released", null)), List.of());
        assertEquals(List.of("VENTA", "UBICACION"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(Arrays.asList(null, "ENTRADA"), rastro.stream().map(EventoRastro::revision).toList());
    }
}
```

En `StockTelefonosControllerTest.java`, añade tras `unSoloCambioDeBateriaNoDaGradoAnterior`:

```java
    @Test void fichaConLaUltimaRevisionDelLote() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(new EventoNsys("350000000000001", "BT-9001",
                AHORA.minusDays(3), false, TipoEvento.UBICACION, "En revisión", "Taller · Pulir", "CAJA PULIR")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T13:43"));
        assertFalse(ficha.get("revisadoDevolucion").asBoolean());
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
    }

    @Test void fichaDeUnMovilEnRevisionSinEventosPendienteDesdeLaRecepcion() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "n/a", "En revisión", "En revisión", EstadoMovil.STOCK)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("2026-10-01", ficha.get("pendienteRevisionDesde").asText());
        assertTrue(ficha.get("revisadoEl").isNull());
    }
```

En `OpenApiContractTest.java` (`elContratoPublicaElStockDeTelefonos`), sustituye estas tres sentencias:

```java
        assertNullable(esquemas, "StockTelefonosFichaMovil", "modelo", "modeloMaestro", "capacidad", "color", "gradoAnterior",
                "sku", "ubicacion", "dias", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "imeiSn", "lote", "grado", "bateriaNueva", "estado", "entradas");
        assertNullable(esquemas, "EventoRastro", "lote", "de", "a", "aNsys", "idRep", "tecnico", "categoria");
```

por:

```java
        assertNullable(esquemas, "StockTelefonosFichaMovil", "modelo", "modeloMaestro", "capacidad", "color", "gradoAnterior",
                "sku", "ubicacion", "dias", "fechaVenta", "revisadoEl", "pendienteRevisionDesde");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "imeiSn", "lote", "grado", "bateriaNueva", "estado", "entradas",
                "revisadoDevolucion");
        assertNullable(esquemas, "EventoRastro", "lote", "de", "a", "aNsys", "idRep", "tecnico", "categoria", "revision");
```

- [x] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='RevisionTest,RastroTest,StockTelefonosControllerTest'`
Expected: error de compilación: no existe `Revision` y `EventoRastro` no tiene 12 componentes.

- [x] **Step 3: Implementación**

Crea `Revision.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** «Revisado» (spec 0.9.18 §5.1). Un lote se revisa al pasar de «sin caja» (la ubicación «En revisión») a una caja que no
 *  sea «Pedidos realizados», que es una venta sin revisar; salir de «Devolución» hacia una caja es la revisión de una
 *  devolución. La hora es la de la exportación en que se vio el cambio, y solo hay pasos intermedios desde dic. 2023. */
public final class Revision {

    public static final String ENTRADA = "ENTRADA";
    public static final String DEVOLUCION = "DEVOLUCION";
    static final String EN_REVISION = "En revisión";
    static final String SALIDA_DEVOLUCION = "Devolución";
    static final String CAJA_PEDIDOS = "Pedidos realizados";

    /** La última revisión de un lote: su hora y si fue la de una devolución. */
    public record Ultima(LocalDateTime fecha, boolean devolucion) {}

    private Revision() {}

    /** Qué revisión es cada evento, en el orden de la lista (FECHA, ID_EVENTO): ENTRADA, DEVOLUCION o null. ENTRADA solo es
     *  el primer cambio de «En revisión» a una caja de cada lote; una devolución puede revisarse varias veces. */
    public static List<String> tipos(List<EventoNsys> eventos) {
        Set<String> lotesRevisados = new HashSet<>();
        List<String> tipos = new ArrayList<>(eventos.size());
        for (EventoNsys e : eventos) {
            String tipo = null;
            if (e.tipo() == TipoEvento.UBICACION && !CAJA_PEDIDOS.equals(e.aNsys())) {
                if (EN_REVISION.equals(e.de())) {
                    if (lotesRevisados.add(e.batch())) tipo = ENTRADA;
                } else if (SALIDA_DEVOLUCION.equals(e.de())) {
                    tipo = DEVOLUCION;
                }
            }
            tipos.add(tipo);
        }
        return tipos;
    }

    /** La última revisión del lote, de entrada o de devolución; null si no la hay. */
    public static Ultima ultima(List<EventoNsys> eventos, String lote) {
        List<String> tipos = tipos(eventos);
        Ultima ultima = null;
        for (int i = 0; i < eventos.size(); i++) {
            if (tipos.get(i) != null && Objects.equals(eventos.get(i).batch(), lote))
                ultima = new Ultima(eventos.get(i).fecha(), DEVOLUCION.equals(tipos.get(i)));
        }
        return ultima;
    }

    /** Si el móvil está ahora en «En revisión», desde cuándo: el último evento del lote que lo dejó allí (su entrada o un
     *  cambio de caja) o, si no lo hay, la recepción del lote. null si no está pendiente o no hay fecha. */
    public static LocalDate pendienteDesde(MovilNsys m, List<EventoNsys> eventos) {
        if (!EN_REVISION.equals(m.ubicacion())) return null;
        LocalDate desde = null;
        for (EventoNsys e : eventos) {
            boolean loDejaAlli = (e.tipo() == TipoEvento.UBICACION || e.tipo() == TipoEvento.ENTRADA) && EN_REVISION.equals(e.a());
            if (loDejaAlli && Objects.equals(e.batch(), m.batch())) desde = e.fecha().toLocalDate();
        }
        return desde != null ? desde : m.receivingDate();
    }
}
```

Sustituye `EventoRastro.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys) o del taller del ERP (idRep, técnico,
 *  categoría). {@code tipo}: los de {@link TipoEvento} o TRABAJO_ASIGNADO / TRABAJO_TERMINADO. {@code revision}: ENTRADA o
 *  DEVOLUCION en el cambio de caja que es la revisión del lote (0.9.18, {@link Revision}). La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria, @Schema(nullable = true) String revision) {}
```

En `Rastro.java`, sustituye el método `unir` por:

```java
    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        // Sobre la lista entera, antes de quitar la venta del esqueleto: así cada tipo sigue con su evento.
        List<String> revisiones = Revision.tipos(nsys);
        List<EventoRastro> todos = new ArrayList<>();
        for (int i = 0; i < nsys.size(); i++) {
            EventoNsys e = nsys.get(i);
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null, revisiones.get(i)));
        }
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep()), null));
        }
        // Del más reciente al más antiguo. A la misma hora, primero lo último apuntado: los eventos de NSYS llegan por
        // orden de alta (ID_EVENTO) y los del taller van detrás, así que un trabajo terminado sale antes que el cambio de
        // NSYS de esa misma exportación, y la venta del esqueleto antes que su entrada.
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) orden.add(i);
        orden.sort(Comparator.comparing((Integer i) -> todos.get(i).fecha()).thenComparing(i -> i).reversed());
        return orden.stream().map(todos::get).toList();
    }
```

En `StockTelefonosController.java`, sustituye el método `ficha` por:

```java
    @GetMapping("/{imeiSn}")
    public FichaMovil ficha(@PathVariable String imeiSn) {
        List<MovilNsys> lotes = dao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        MovilNsys m = lotes.get(0);
        List<EventoNsys> eventos = dao.eventos(imeiSn);
        // El último cambio de grado de verdad del lote: un cambio de batería (A → A100) no cuenta, o taparía el grado
        // anterior real (C → A → A100 da C).
        String gradoAnterior = eventos.stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && Objects.equals(e.batch(), m.batch()))
                .filter(e -> !Normalizacion.gradoSinBateria(e.de()).equals(Normalizacion.gradoSinBateria(e.a())))
                .reduce((a, b) -> b).map(e -> e.de() == null ? null : Normalizacion.gradoSinBateria(e.de())).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        // 0.9.18 (spec §5.2): la última revisión del lote y, si está en «En revisión», desde cuándo.
        Revision.Ultima revisado = Revision.ultima(eventos, m.batch());
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), gradoAnterior, m.sku(),
                ubicacionVisible(m), m.estado().name(), enEmpresa ? dias(m, LocalDate.now(clock)) : null, lotes.size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                revisado == null ? null : revisado.fecha(), revisado != null && revisado.devolucion(),
                Revision.pendienteDesde(m, eventos));
    }
```

y el record `FichaMovil` por:

```java
    private record FichaMovil(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva,
                              @Schema(nullable = true) String gradoAnterior, @Schema(nullable = true) String sku,
                              @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, int entradas,
                              @Schema(nullable = true) LocalDate fechaVenta,
                              @Schema(nullable = true) LocalDateTime revisadoEl, boolean revisadoDevolucion,
                              @Schema(nullable = true) LocalDate pendienteRevisionDesde) {}
```

Comprueba que nadie más construye `EventoRastro`: `grep -rn "new EventoRastro" src` solo da `Rastro.java` y `RastroTest.java`.

- [x] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='RevisionTest,RastroTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/Revision.java src/main/java/com/reparaciones/servidor/nsys/EventoRastro.java \
  src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java \
  src/test/java/com/reparaciones/servidor/nsys/RevisionTest.java src/test/java/com/reparaciones/servidor/nsys/RastroTest.java \
  src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: revisado en la ficha y en el rastro, de sin caja a una caja (0.9.18)"
```

---

### Task 3: Servidor — `POST /api/stock-telefonos/comprobar` (una lista de IMEIs)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java` (`porImeis`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java` (`comprobar`)
- Test: `…/nsys/NsysDAOTest.java`, `…/nsys/StockTelefonosControllerTest.java`, `…/controller/RolesStockTelefonosTest.java`, `…/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `NsysDAO.trabajosAbiertos(Collection<String>)`, `claveCruce`, `trozos(lista, tamano)`, `marcas(n)` y `TANDA_IN` (Task 1).
- Produces:
  - `NsysDAO.porImeis(Collection<String> imeis) → List<MovilNsys>`: de cada IMEI, su lote más reciente primero.
  - `StockTelefonosController.MAX_COMPROBAR = 500` y `record PeticionComprobar(List<String> imeis)` (del paquete); esquema
    `StockTelefonosPeticionComprobar`.
  - `POST /api/stock-telefonos/comprobar` → `List<MovilComprobado>`, una entrada por IMEI pedido y en su orden:
    `MovilComprobado(String imei, boolean encontrado, modelo, modeloMaestro, capacidad, color, grado, boolean bateriaNueva,
    ubicacion, estado, LocalDate fechaVenta, List<TrabajoAbierto> trabajos)`; esquema `StockTelefonosMovilComprobado`. Sin
    encontrar, todo null salvo `imei`, `encontrado` (false), `bateriaNueva` (false) y `trabajos` (vacía). Más de 500, 400.

- [x] **Step 1: Tests (fallan)**

En `NsysDAOTest.java`, añade al final:

```java
    // ---------------------------------------------------------------- 0.9.18: comprobar una lista (spec §6.2)

    @Test void losLotesDeUnosImeisVanEnTandasYDelMasRecienteAlMasAntiguo() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.porImeis(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size());
        assertTrue(sql.get(0).contains("FROM Nsys_movil WHERE IMEI_SN IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).endsWith("IMEI_SN IN (?)" + MAS_RECIENTE_PRIMERO), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.porImeis(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }
```

En `StockTelefonosControllerTest.java`, añade los imports `java.util.ArrayList` y, tras `rastroUneNsysYTaller`:

```java
    // ---------------------------------------------------------------- 0.9.18: comprobar una lista (spec §6.2)

    private static StockTelefonosController.PeticionComprobar pedir(String... imeis) {
        return new StockTelefonosController.PeticionComprobar(List.of(imeis));
    }

    @Test void comprobarDaUnaEntradaPorImeiConElLoteMasRecienteSusTrabajosYLaFechaDeVenta() {
        MovilNsys vendido = new MovilNsys("350000000000003", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released", "x",
                "Proveedor A", LocalDate.of(2023, 4, 1), LocalDate.of(2023, 5, 2), null, "13", "13", "128GB", "Azul", "A",
                false, null, null, EstadoMovil.VENDIDO, AHORA.minusDays(900), AHORA.minusDays(800));
        List<String> imeis = List.of("350000000000001", "350000000000002", "350000000000003");
        when(dao.porImeis(imeis)).thenReturn(List.of(
                movil("350000000000001", "BT-9002", "B", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000001", "BT-9001", "A", null, "Listos", EstadoMovil.BAJA), vendido));
        when(dao.trabajosAbiertos(imeis)).thenReturn(Map.of("350000000000001",
                List.of(new TrabajoAbierto("REPARACION", "Técnico 1", AHORA.minusHours(3), null))));
        JsonNode r = json(api.comprobar(pedir("350000000000001", "350000000000002", "350000000000003")));
        assertEquals(3, r.size(), "una entrada por IMEI pedido, en su orden");
        assertEquals("350000000000001", r.get(0).get("imei").asText());
        assertTrue(r.get(0).get("encontrado").asBoolean());
        assertEquals("STOCK", r.get(0).get("estado").asText(), "del IMEI, el lote más reciente");
        assertEquals("B", r.get(0).get("grado").asText());
        assertEquals("Listos", r.get(0).get("ubicacion").asText());
        assertEquals("Técnico 1", r.get(0).get("trabajos").get(0).get("tecnico").asText());
        assertEquals("350000000000002", r.get(1).get("imei").asText());
        assertFalse(r.get(1).get("encontrado").asBoolean());
        assertTrue(r.get(1).get("estado").isNull());
        assertEquals(0, r.get(1).get("trabajos").size());
        assertEquals("VENDIDO", r.get(2).get("estado").asText(), "también los vendidos");
        assertEquals("2023-05-02", r.get(2).get("fechaVenta").asText());
    }

    @Test void comprobarMasDeQuinientosEs400SinConsultar() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> api.comprobar(new StockTelefonosController.PeticionComprobar(imeis)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test void comprobarUnaListaVaciaNoConsulta() {
        assertTrue(api.comprobar(pedir()).isEmpty());
        assertTrue(api.comprobar(new StockTelefonosController.PeticionComprobar(null)).isEmpty());
        verifyNoInteractions(dao);
    }
```

En `RolesStockTelefonosTest.java`, añade los imports `org.springframework.http.MediaType`, `java.util.stream.Collectors`,
`java.util.stream.IntStream` y el estático `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`, y estos tests al final:

```java
    /** 0.9.18 (spec §6.2): «Pegar varios IMEIs» con la misma guarda que el resto del stock de teléfonos. */
    @Test void comprobarUnaListaSoloParaSupertecnicoYAdmin() throws Exception {
        String cuerpo = "{\"imeis\":[\"350000000000001\"]}";
        mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dao);
        for (String token : List.of(supertecnico(), admin())) {
            mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].imei").value("350000000000001"))
                    .andExpect(jsonPath("$[0].encontrado").value(false));
        }
    }

    @Test void comprobarMasDeQuinientosEs400() throws Exception {
        String imeis = IntStream.range(0, 501).mapToObj(n -> "\"" + String.format("3500000000%05d", n) + "\"")
                .collect(Collectors.joining(","));
        mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imeis\":[" + imeis + "]}"))
                .andExpect(status().isBadRequest());
    }
```

En `OpenApiContractTest.java` (`elContratoPublicaElStockDeTelefonos`), añade al final del método:

```java
        // 0.9.18: comprobar una lista de IMEIs (spec 2026-10-19 §6.2).
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/comprobar", "post").endsWith("/StockTelefonosPeticionComprobar"));
        assertNoNullable(esquemas, "StockTelefonosPeticionComprobar", "imeis");
        assertNullable(esquemas, "StockTelefonosMovilComprobado", "modelo", "modeloMaestro", "capacidad", "color", "grado",
                "ubicacion", "estado", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosMovilComprobado", "imei", "encontrado", "bateriaNueva", "trabajos");
```

- [x] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest'`
Expected: error de compilación: no existen `NsysDAO.porImeis` ni `StockTelefonosController.PeticionComprobar`.

- [x] **Step 3: Implementación**

En `NsysDAO.java`, tras el método `porImei`, añade:

```java
    /** Los lotes de estos IMEI/SN, en tandas de {@value #TANDA_IN}: de cada IMEI, el más reciente primero (spec 0.9.18 §6.2). */
    public List<MovilNsys> porImeis(Collection<String> imeis) {
        List<MovilNsys> moviles = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            moviles.addAll(jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN IN (" + marcas(tanda.size())
                    + ")" + LOTE_MAS_RECIENTE_PRIMERO, NsysDAO::movil, tanda.toArray()));
        return moviles;
    }
```

En `StockTelefonosController.java`:

1. Imports: añade `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody` y `java.util.HashMap`.
2. Tras la constante `MOTIVO_ERROR`, añade:

```java
    /** Como mucho los IMEIs de una vez en «Pegar varios IMEIs» (spec 0.9.18 §4.9). */
    static final int MAX_COMPROBAR = 500;
```

3. Tras el método `rastro`, añade:

```java
    /** «Pegar varios IMEIs» (spec 0.9.18 §4.9 y §6.2): una entrada por IMEI pedido, en el mismo orden. Busca en todo el
     *  espejo, también los vendidos; de cada IMEI, el lote más reciente, con sus trabajos abiertos. La web manda los IMEIs
     *  limpios y sin repetir; más de {@value #MAX_COMPROBAR} es un 400. */
    @PostMapping("/comprobar")
    public List<MovilComprobado> comprobar(@RequestBody PeticionComprobar peticion) {
        List<String> imeis = peticion == null || peticion.imeis() == null ? List.of() : peticion.imeis();
        if (imeis.size() > MAX_COMPROBAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Como mucho " + MAX_COMPROBAR + " IMEIs por vez");
        if (imeis.isEmpty()) return List.of();
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : dao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos(imeis);
        return imeis.stream().map(imei -> {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(imei));
            if (m == null) return new MovilComprobado(imei, false, null, null, null, null, null, false, null, null, null, List.of());
            return new MovilComprobado(imei, true, m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                    Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), ubicacionVisible(m),
                    m.estado().name(), m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                    abiertos.getOrDefault(NsysDAO.claveCruce(imei), List.of()));
        }).toList();
    }
```

4. Al final de la clase, tras el record `FichaMovil`, añade:

```java
    /** Del paquete (no privado) para que los tests lo construyan; el esquema se llama StockTelefonosPeticionComprobar. */
    record PeticionComprobar(List<String> imeis) {}

    private record MovilComprobado(String imei, boolean encontrado, @Schema(nullable = true) String modelo,
                                   @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                   @Schema(nullable = true) String color, @Schema(nullable = true) String grado,
                                   boolean bateriaNueva, @Schema(nullable = true) String ubicacion,
                                   @Schema(nullable = true) String estado, @Schema(nullable = true) LocalDate fechaVenta,
                                   List<TrabajoAbierto> trabajos) {}
```

- [x] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. El test de idempotencia del contrato sigue en verde: `POST /comprobar` no declara `Idempotency-Key`.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java \
  src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java \
  src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: comprobar una lista de IMEIs contra el espejo de NSYS (0.9.18)"
```

---

### Task 4: Servidor — `GET /api/stock-telefonos/importaciones` (lista de la píldora)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/RegistroImportacion.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/ResumenHistorico.java`
- Modify: `…/nsys/NsysDAO.java` (`importaciones`, `registroImportacion`, `resumenHistorico`)
- Modify: `…/nsys/StockTelefonosController.java` (`importaciones`)
- Test: `…/nsys/NsysDAOTest.java`, `…/nsys/StockTelefonosControllerTest.java`, `…/controller/RolesStockTelefonosTest.java`, `…/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `NsysDAO.primeraImportacion(ACTUAL, Set.of(OK))` y `MOTIVO_ERROR` (0.9.17).
- Produces:
  - `public record RegistroImportacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas,
    int cambios, LocalDateTime empezada, LocalDateTime terminada, String mensaje)`.
  - `public record ResumenHistorico(int total, int hechos, LocalDateTime ultimo)`.
  - `NsysDAO.importaciones(String origen, int limite) → List<RegistroImportacion>`, de la más reciente a la más antigua.
  - `NsysDAO.resumenHistorico() → ResumenHistorico`.
  - `StockTelefonosController.LIMITE_IMPORTACIONES = 50`.
  - `GET /api/stock-telefonos/importaciones?limite=20` → `ListaImportaciones(List<FilaImportacion> importaciones,
    ProgresoHistorico historico)`, con:
    - `FilaImportacion(String fichero, LocalDateTime fechaExportacion, String resultado, int filas, int cambios, Long segundos, String motivo)`;
    - `ProgresoHistorico(int total, int hechos, LocalDateTime ultimo, LocalDateTime hasta)`;
    - esquemas `StockTelefonosListaImportaciones`, `StockTelefonosFilaImportacion` y `StockTelefonosProgresoHistorico`.

- [x] **Step 1: Tests (fallan)**

En `NsysDAOTest.java`, añade el import estático `org.mockito.AdditionalMatchers.and` y al final:

```java
    // ---------------------------------------------------------------- 0.9.18: lista de importaciones (spec §6.5)

    @SuppressWarnings("unchecked")
    @Test void lasImportacionesSonDeUnOrigenDeLaMasRecienteALaMasAntigua() {
        dao.importaciones("ACTUAL", 20);
        verify(jdbc).query(and(contains("FROM Nsys_importacion WHERE ORIGEN = ?"),
                        contains("ORDER BY FECHA_EXPORTACION DESC, ID_IMPORTACION DESC LIMIT ?")),
                any(RowMapper.class), eq("ACTUAL"), eq(20));
    }

    @Test void unaImportacionEnCursoNoTieneHoraDeFin() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_IMPORTACION")).thenReturn(9);
        when(rs.getString("FICHERO")).thenReturn("Items_2026.10.16_13.31.xlsx");
        when(rs.getTimestamp("FECHA_EXPORTACION")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 16, 13, 31)));
        when(rs.getString("RESULTADO")).thenReturn("EN_CURSO");
        when(rs.getTimestamp("EMPEZADA")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 16, 13, 36)));
        assertEquals(new RegistroImportacion(9, "Items_2026.10.16_13.31.xlsx", LocalDateTime.of(2026, 10, 16, 13, 31),
                "EN_CURSO", 0, 0, LocalDateTime.of(2026, 10, 16, 13, 36), null, null), NsysDAO.registroImportacion(rs, 0));
    }

    @SuppressWarnings("unchecked")
    @Test void elResumenDelHistoricoCuentaTodasLasHechasYLaFechaDeLaUltimaHecha() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<ResumenHistorico>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        dao.resumenHistorico();
        verify(jdbc).queryForObject(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("FROM Nsys_importacion WHERE ORIGEN = 'HISTORICO'"), sql.getValue());
        assertTrue(sql.getValue().contains("RESULTADO IN ('OK', 'RECHAZADO')"), sql.getValue());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("TOTAL")).thenReturn(3);
        when(rs.getInt("HECHOS")).thenReturn(2);
        when(rs.getTimestamp("ULTIMO")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2025, 5, 3, 22, 31)));
        assertEquals(new ResumenHistorico(3, 2, LocalDateTime.of(2025, 5, 3, 22, 31)), mapper.getValue().mapRow(rs, 0));
    }
```

En `StockTelefonosControllerTest.java`, añade al final:

```java
    // ---------------------------------------------------------------- 0.9.18: lista de importaciones (spec §6.5)

    private static RegistroImportacion registro(int id, LocalDateTime fecha, String resultado, String mensaje, Integer segundos) {
        LocalDateTime empezada = fecha.plusMinutes(3);
        return new RegistroImportacion(id, "Items_" + id + ".xlsx", fecha, resultado, 70000, 12, empezada,
                segundos == null ? null : empezada.plusSeconds(segundos), mensaje);
    }

    @Test void importacionesDelActualDeLaMasRecienteALaMasAntiguaConSusSegundosYSuMotivo() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        when(dao.importaciones("ACTUAL", 20)).thenReturn(List.of(
                registro(8, AHORA.minusMinutes(12), "EN_CURSO", null, null),
                registro(7, AHORA.minusMinutes(42), "OK", "Cajas sin traducir: CAJA NUEVA", 17),
                registro(6, AHORA.minusMinutes(72), "RECHAZADO", "Trae 10 filas…", 2)));
        JsonNode r = json(api.importaciones(20));
        JsonNode filas = r.get("importaciones");
        assertEquals(3, filas.size());
        assertEquals("EN_CURSO", filas.get(0).get("resultado").asText());
        assertTrue(filas.get(0).get("segundos").isNull(), "en curso: sin segundos");
        assertTrue(filas.get(0).get("motivo").isNull());
        assertEquals("Items_7.xlsx", filas.get(1).get("fichero").asText());
        assertEquals(12, filas.get(1).get("cambios").asInt());
        assertEquals(17, filas.get(1).get("segundos").asInt());
        assertEquals("Cajas sin traducir: CAJA NUEVA", filas.get(1).get("motivo").asText());
        assertEquals("Trae 10 filas…", filas.get(2).get("motivo").asText());
        assertEquals(0, r.get("historico").get("total").asInt(), "sin filas HISTORICO la web no pinta la línea");
    }

    @Test void unErrorDeLaListaSaleConElMotivoGenericoYNuncaConSuMensaje() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        when(dao.importaciones("ACTUAL", 20)).thenReturn(List.of(registro(5, AHORA.minusMinutes(30), "ERROR",
                "java.io.IOException: Drive respondió 403 a https://www.googleapis.com/drive/v3/files/id-secreto", 4)));
        JsonNode fila = json(api.importaciones(20)).get("importaciones").get(0);
        assertEquals("Error al leer de Drive o al guardar; ver el registro del servidor", fila.get("motivo").asText());
        assertFalse(fila.toString().contains("id-secreto"));
    }

    @Test void elLimiteSeQuedaEntreUnoYCincuenta() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        api.importaciones(200);
        api.importaciones(0);
        api.importaciones(-3);
        verify(dao).importaciones("ACTUAL", 50);
        verify(dao, times(2)).importaciones("ACTUAL", 1);
    }

    @Test void elProgresoDelHistoricoConHechosUltimoYHasta() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(140, 138, LocalDateTime.of(2025, 5, 3, 22, 31)));
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(1, "Items_2026.10.16_14.31.xlsx", LocalDateTime.of(2026, 10, 16, 14, 31), "OK", 70000, null)));
        JsonNode h = json(api.importaciones(20)).get("historico");
        assertEquals(140, h.get("total").asInt());
        assertEquals(138, h.get("hechos").asInt());
        assertTrue(h.get("ultimo").asText().startsWith("2025-05-03T22:31"));
        assertTrue(h.get("hasta").asText().startsWith("2026-10-16T14:31"));
    }
```

En `RolesStockTelefonosTest.java`:

1. Añade los imports `com.reparaciones.servidor.nsys.ResumenHistorico` y el estático `org.mockito.Mockito.verify`.
2. Sustituye la lista `RUTAS` por:

```java
    private static final List<String> RUTAS = List.of("/api/stock-telefonos", "/api/stock-telefonos/estado",
            "/api/stock-telefonos/buscar?imei=3500", "/api/stock-telefonos/350000000000001",
            "/api/stock-telefonos/350000000000001/rastro", "/api/stock-telefonos/importaciones");
```

3. En `supertecnicoYAdminLasLeen`, como primera línea del método:

```java
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
```

4. Al final de la clase:

```java
    @Test void laListaDeImportacionesPideVeintePorDefecto() throws Exception {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        mvc.perform(get("/api/stock-telefonos/importaciones").header("Authorization", admin())).andExpect(status().isOk());
        verify(dao).importaciones("ACTUAL", 20);
    }
```

En `OpenApiContractTest.java`:

1. En la lista de rutas del bucle de `elContratoPublicaElStockDeTelefonos`, añade `"/api/stock-telefonos/importaciones"`.
2. Al final del método:

```java
        // 0.9.18: lista de importaciones de la píldora (spec 2026-10-19 §6.5). `historico` siempre viene (con total = 0
        // si no hay ninguna importación HISTORICO), para no depender de un objeto nullable junto a un $ref.
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/importaciones", "get", "200")
                .endsWith("/StockTelefonosListaImportaciones"));
        assertEquals("query", parametro(paths, "/api/stock-telefonos/importaciones", "get", "limite").path("in").asText());
        assertNoNullable(esquemas, "StockTelefonosListaImportaciones", "importaciones", "historico");
        assertNullable(esquemas, "StockTelefonosFilaImportacion", "segundos", "motivo");
        assertNoNullable(esquemas, "StockTelefonosFilaImportacion", "fichero", "fechaExportacion", "resultado", "filas", "cambios");
        assertNullable(esquemas, "StockTelefonosProgresoHistorico", "ultimo", "hasta");
        assertNoNullable(esquemas, "StockTelefonosProgresoHistorico", "total", "hechos");
```

- [x] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest'`
Expected: error de compilación: no existen `RegistroImportacion`, `ResumenHistorico` ni `importaciones`.

- [x] **Step 3: Implementación**

Crea `RegistroImportacion.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_importacion con sus horas (Madrid), para la lista de la píldora de NSYS (spec 0.9.18 §6.5).
 *  {@code terminada} es null mientras está EN_CURSO. */
public record RegistroImportacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas,
                                  int cambios, LocalDateTime empezada, LocalDateTime terminada, String mensaje) {}
```

Crea `ResumenHistorico.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** El histórico en Nsys_importacion (spec 0.9.18 §6.5): cuántas importaciones HISTORICO hay de cualquier resultado,
 *  cuántas están hechas (OK o RECHAZADO) y la fecha del Items del último fichero hecho (null si ninguno). */
public record ResumenHistorico(int total, int hechos, LocalDateTime ultimo) {}
```

En `NsysDAO.java`, tras el método `ficherosProcesados`, añade:

```java
    /** Las últimas importaciones de un origen, de la más reciente a la más antigua (spec 0.9.18 §6.5). */
    public List<RegistroImportacion> importaciones(String origen, int limite) {
        return jdbc.query("SELECT ID_IMPORTACION, FICHERO, FECHA_EXPORTACION, RESULTADO, FILAS, CAMBIOS, EMPEZADA, TERMINADA,"
                        + " MENSAJE FROM Nsys_importacion WHERE ORIGEN = ? ORDER BY FECHA_EXPORTACION DESC, ID_IMPORTACION DESC"
                        + " LIMIT ?", NsysDAO::registroImportacion, origen, limite);
    }

    static RegistroImportacion registroImportacion(ResultSet rs, int n) throws SQLException {
        return new RegistroImportacion(rs.getInt("ID_IMPORTACION"), rs.getString("FICHERO"),
                rs.getTimestamp("FECHA_EXPORTACION").toLocalDateTime(), rs.getString("RESULTADO"), rs.getInt("FILAS"),
                rs.getInt("CAMBIOS"), fechaHora(rs.getTimestamp("EMPEZADA")), fechaHora(rs.getTimestamp("TERMINADA")),
                rs.getString("MENSAJE"));
    }

    /** El histórico de un vistazo: todas sus importaciones, las hechas (OK o RECHAZADO) y la fecha de la última hecha. */
    public ResumenHistorico resumenHistorico() {
        return jdbc.queryForObject("SELECT COUNT(*) AS TOTAL, COALESCE(SUM(RESULTADO IN ('OK', 'RECHAZADO')), 0) AS HECHOS,"
                        + " MAX(CASE WHEN RESULTADO IN ('OK', 'RECHAZADO') THEN FECHA_EXPORTACION END) AS ULTIMO"
                        + " FROM Nsys_importacion WHERE ORIGEN = 'HISTORICO'",
                (rs, n) -> new ResumenHistorico(rs.getInt("TOTAL"), rs.getInt("HECHOS"), fechaHora(rs.getTimestamp("ULTIMO"))));
    }
```

En `StockTelefonosController.java`:

1. Imports: añade `java.time.Duration`.
2. Tras `MAX_COMPROBAR`, añade:

```java
    /** Tope de filas de la lista de importaciones (spec 0.9.18 §6.5); por defecto, 20. */
    static final int LIMITE_IMPORTACIONES = 50;
    private static final String EN_CURSO = "EN_CURSO";
```

3. Tras el método `comprobar`, añade:

```java
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
```

4. Al final de la clase:

```java
    private record ListaImportaciones(List<FilaImportacion> importaciones, ProgresoHistorico historico) {}

    private record FilaImportacion(String fichero, LocalDateTime fechaExportacion, String resultado, int filas, int cambios,
                                   @Schema(nullable = true) Long segundos, @Schema(nullable = true) String motivo) {}

    /** {@code total} = 0: no hay ninguna importación HISTORICO y la web no pinta la línea. {@code hasta}: el Items de la
     *  primera importación ACTUAL OK, donde termina el histórico. */
    private record ProgresoHistorico(int total, int hechos, @Schema(nullable = true) LocalDateTime ultimo,
                                     @Schema(nullable = true) LocalDateTime hasta) {}
```

`GET /importaciones` no choca con `GET /{imeiSn}`: Spring prefiere la ruta literal.

- [x] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='NsysDAOTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`.

- [x] **Step 5: Suite completa del servidor y contrato para la web**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, sin fallos; anota el total de tests. Queda `target/openapi.json` con las rutas nuevas:
`grep -c "stock-telefonos/importaciones\|stock-telefonos/comprobar\|TrabajoAbierto" target/openapi.json` da más de 0.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/RegistroImportacion.java src/main/java/com/reparaciones/servidor/nsys/ResumenHistorico.java \
  src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java \
  src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java \
  src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: lista de importaciones de NSYS y progreso del historico (0.9.18)"
```

---

### Task 5: Web — contrato, tipos, API, fábricas y handlers de MSW

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (generados)
- Modify: `gestion-reparaciones-web/src/shared/api/client.ts` (alias)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/api.ts`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/test/fabrica.ts`, `gestion-reparaciones-web/src/modules/taller/test/fabrica.ts`
- Modify: `gestion-reparaciones-web/src/test/server.ts`
- Test: `gestion-reparaciones-web/src/modules/telefonos/api.test.tsx`

**Interfaces:**
- Consumes: el contrato de las Tasks 1-4 (`/c/Users/dev/Documents/wt/servidor-0918/target/openapi.json`).
- Produces:
  - Alias en `client.ts`: `TrabajoAbierto`, `MovilComprobado`, `ListaImportaciones`, `ImportacionNsys` (`StockTelefonosFilaImportacion`)
    y `ProgresoHistorico`. `MovilStock` lleva `trabajos`; `FichaMovil`, `revisadoEl`, `revisadoDevolucion` y
    `pendienteRevisionDesde`; `EventoRastro`, `revision`.
  - En `modules/telefonos/api.ts`:
    - `useEstadoNsys(activo = true)`, con `silenciarError`;
    - `useImportacionesNsys()`;
    - `useComprobarImeis(imeis: string[])` (consulta, no mutación);
    - `MAX_IMEIS = 500` y `CLAVE_IMPORTACIONES_NSYS`.
  - Fábricas de `telefonos/test/fabrica.ts`: `movil` (con `trabajos: []`), `trabajo(p)`, `movilComprobado(p)`,
    `noEncontrado(imei)`, `importacion(p)` y `listaImportaciones(p)`.
  - Handlers por defecto de MSW para `/estado` (al día) y `/importaciones` (vacía), antes de `:imeiSn`.

- [x] **Step 1: Worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git status --short   # vacío salvo lo que ya no se sigue (dist, test-results)
git worktree add -b dev_v0918_stock_telefonos /c/Users/dev/Documents/wt/web-0918 main
cd /c/Users/dev/Documents/wt/web-0918 && npm ci
```

Expected: `npm ci` sin errores.

- [x] **Step 2: Contrato y tipos**

Run (en `/c/Users/dev/Documents/wt/web-0918`):
`cp /c/Users/dev/Documents/wt/servidor-0918/target/openapi.json api/openapi.json && npm run api:types:offline`
Expected: `schema.d.ts` regenerado; `grep -c "StockTelefonosMovilComprobado\|TrabajoAbierto\|StockTelefonosListaImportaciones" src/shared/api/schema.d.ts` da más de 0.

En `src/shared/api/client.ts`, tras `export type EventoRastro = components['schemas']['EventoRastro']`, añade:

```ts
/** 0.9.18 (spec «Columna compacta, SKU, Quién lo tiene y Revisado»): los trabajos abiertos de cada móvil (`categoria`
 *  REPARACION, GLASS o PULIDO; `entregado`, solo en la glass), la comprobación de una lista de IMEIs y la lista de
 *  importaciones de la píldora (`resultado` OK, RECHAZADO, ERROR o EN_CURSO; `historico.total` = 0 si no hay histórico).
 *  Horas de Madrid, sin zona. */
export type TrabajoAbierto = components['schemas']['TrabajoAbierto']
export type MovilComprobado = components['schemas']['StockTelefonosMovilComprobado']
export type ListaImportaciones = components['schemas']['StockTelefonosListaImportaciones']
export type ImportacionNsys = components['schemas']['StockTelefonosFilaImportacion']
export type ProgresoHistorico = components['schemas']['StockTelefonosProgresoHistorico']
```

- [x] **Step 3: Tests (fallan)**

En `src/modules/telefonos/api.test.tsx`, cambia los imports de la API y de las fábricas por:

```ts
import { useComprobarImeis, useEstadoNsys, useImportacionesNsys, useStockTelefonos } from './api'
import { estadoNsys, listaImportaciones, movil, movilComprobado } from './test/fabrica'
```

y añade al final del `describe`:

```tsx
  it('el estado de NSYS no pregunta si no está activo (el técnico)', () => {
    let pedidas = 0
    server.use(http.get('*/api/stock-telefonos/estado', () => {
      pedidas++
      return HttpResponse.json(estadoNsys())
    }))
    const estado = renderHook(() => useEstadoNsys(false), { wrapper: envoltorio() })
    expect(estado.result.current.fetchStatus).toBe('idle')
    expect(pedidas).toBe(0)
  })

  it('la lista de importaciones pide las últimas 20', async () => {
    const limites: (string | null)[] = []
    server.use(http.get('*/api/stock-telefonos/importaciones', ({ request }) => {
      limites.push(new URL(request.url).searchParams.get('limite'))
      return HttpResponse.json(listaImportaciones())
    }))
    const lista = renderHook(() => useImportacionesNsys(), { wrapper: envoltorio() })
    await waitFor(() => expect(lista.result.current.data?.importaciones).toHaveLength(1))
    expect(limites).toEqual(['20'])
  })

  it('comprobar manda los IMEIs en el cuerpo y sin IMEIs no pregunta', async () => {
    const cuerpos: unknown[] = []
    server.use(http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
      cuerpos.push(await request.json())
      return HttpResponse.json([movilComprobado()])
    }))
    const vacio = renderHook(() => useComprobarImeis([]), { wrapper: envoltorio() })
    expect(vacio.result.current.fetchStatus).toBe('idle')
    const lleno = renderHook(() => useComprobarImeis(['350000000000001']), { wrapper: envoltorio() })
    await waitFor(() => expect(lleno.result.current.data).toHaveLength(1))
    expect(cuerpos).toEqual([{ imeis: ['350000000000001'] }])
  })
```

Run: `npx vitest run src/modules/telefonos/api.test.tsx`
Expected: FAIL (no existen `useImportacionesNsys`, `useComprobarImeis` ni las fábricas nuevas) y `npx tsc -b` con errores
en las fábricas (faltan `trabajos`, `revisadoEl`… y `revision`).

- [x] **Step 4: Implementación**

Sustituye `src/modules/telefonos/api.ts` entero por:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type EstadoNsys, type ListaImportaciones, type MovilComprobado, type MovilStock } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'

export const CLAVE_STOCK_TELEFONOS = ['telefonos', 'stock'] as const
export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const
export const CLAVE_IMPORTACIONES_NSYS = ['telefonos', 'importaciones'] as const
/** Como mucho los IMEIs que se comprueban de una vez (spec 0.9.18 §4.9; el servidor da 400 por encima). */
export const MAX_IMEIS = 500

/** GET /api/stock-telefonos: los móviles que están en la empresa, con sus trabajos abiertos (0.9.18). Los filtros se
 *  aplican en la pantalla (spec §8). Sondea como las demás vistas: la importación trae datos nuevos cada 5 min. */
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
```

Sustituye `src/modules/telefonos/test/fabrica.ts` entero por:

```ts
import type {
  EstadoNsys, ImportacionNsys, ListaImportaciones, MovilBuscado, MovilComprobado, MovilStock, TrabajoAbierto,
} from '@/shared/api/client'

/** Datos inventados del stock de teléfonos (repos públicos). */
export function movil(p: Partial<MovilStock> = {}): MovilStock {
  return {
    imeiSn: '350000000000001', lote: 'BT-9001', modelo: '13', modeloMaestro: '13', capacidad: '128GB', color: 'Azul',
    grado: 'A', bateriaNueva: false, esEsim: false, sku: 'i13128AzulA', ubicacion: 'Listos', estado: 'STOCK', dias: 6,
    trabajos: [], ...p,
  }
}

/** Un trabajo abierto: por defecto, una reparación de «Técnico 1» asignada el 16/10 a las 09:00 (Madrid). */
export function trabajo(p: Partial<TrabajoAbierto> = {}): TrabajoAbierto {
  return { categoria: 'REPARACION', tecnico: 'Técnico 1', desde: '2026-10-16T09:00:00', entregado: null, ...p }
}

export function estadoNsys(p: Partial<EstadoNsys> = {}): EstadoNsys {
  return { ultimaExportacion: '2026-10-16T13:31:00', nivel: 'OK', ultimoFallo: null, motivoFallo: null, ...p }
}

export function movilBuscado(p: Partial<MovilBuscado> = {}): MovilBuscado {
  return {
    imeiSn: '350000000000001', modelo: '13', modeloMaestro: '13', capacidad: '128GB', color: 'Azul', estado: 'STOCK',
    ubicacion: 'Listos', fechaVenta: null, ...p,
  }
}

export function movilComprobado(p: Partial<MovilComprobado> = {}): MovilComprobado {
  return {
    imei: '350000000000001', encontrado: true, modelo: '13', modeloMaestro: '13', capacidad: '128GB', color: 'Azul',
    grado: 'A', bateriaNueva: false, ubicacion: 'Listos', estado: 'STOCK', fechaVenta: null, trabajos: [], ...p,
  }
}

/** Lo que responde el servidor por un IMEI que no está en el espejo. */
export function noEncontrado(imei: string): MovilComprobado {
  return movilComprobado({
    imei, encontrado: false, modelo: null, modeloMaestro: null, capacidad: null, color: null, grado: null, ubicacion: null,
    estado: null,
  })
}

export function importacion(p: Partial<ImportacionNsys> = {}): ImportacionNsys {
  return {
    fichero: 'Items_2026.10.16_13.31.xlsx', fechaExportacion: '2026-10-16T13:31:00', resultado: 'OK', filas: 70000,
    cambios: 12, segundos: 17, motivo: null, ...p,
  }
}

export function listaImportaciones(p: Partial<ListaImportaciones> = {}): ListaImportaciones {
  return { importaciones: [importacion()], historico: { total: 0, hechos: 0, ultimo: null, hasta: null }, ...p }
}
```

En `src/modules/taller/test/fabrica.ts`:
- en `fichaMovil`, tras `entradas: 1, fechaVenta: null,` añade `revisadoEl: null, revisadoDevolucion: false, pendienteRevisionDesde: null,`;
- en `eventoRastro`, tras `categoria: null,` añade `revision: null,`.

En `src/test/server.ts`, justo antes de `http.get('*/api/stock-telefonos/buscar', …)`, añade:

```ts
  // 0.9.18: el indicador «NSYS sin datos» de la barra superior pide el estado en cualquier vista del supertécnico y del
  // admin: por defecto, al día (no se pinta). La lista de importaciones de la píldora, vacía. Van antes que «:imeiSn», que
  // también las cubriría.
  http.get('*/api/stock-telefonos/estado', () =>
    HttpResponse.json({ ultimaExportacion: '2026-10-16T13:31:00', nivel: 'OK', ultimoFallo: null, motivoFallo: null })),
  http.get('*/api/stock-telefonos/importaciones', () =>
    HttpResponse.json({ importaciones: [], historico: { total: 0, hechos: 0, ultimo: null, hasta: null } })),
```

y en el comentario de cabecera de `server` (la lista de handlers por defecto), añade al final: «Y desde la 0.9.18, el estado
de NSYS y la lista de importaciones (el indicador de la barra superior).»

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos src/modules/taller/imeis src/app/shell`
Expected: `tsc` limpio y todo en verde.

- [x] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/api.ts src/modules/telefonos/api.test.tsx \
  src/modules/telefonos/test/fabrica.ts src/modules/taller/test/fabrica.ts src/test/server.ts
git commit -m "feat: contrato y api de la 0.9.18, trabajos abiertos, comprobar IMEIs e importaciones"
```

---

### Task 6: Web — «Quién lo tiene», grado con color, filtro Técnico y columnas de la tabla

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/quien.ts`, `quien.test.ts`
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/grado.ts`, `grado.test.ts`, `PildoraGrado.tsx`, `CeldaQuien.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/nsys.ts` (`diaDeHoyEnMadrid`)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.ts` (entero), `modelo.test.ts` (entero)
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/columnas.tsx` (entero), `FiltrosMoviles.tsx`, `StockTelefonosPage.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/test/fabrica.ts` (`AHORA`, `visto`)
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`

**Interfaces:**
- Consumes: `MovilStock.trabajos`, `TrabajoAbierto` y las fábricas `movil`, `trabajo` (Task 5); `minutosDesde` de `nsys.ts`.
- Produces:
  - `quien.ts`:
    - `MINUTOS_DESCUADRE = 60`, `SIN_ASIGNAR`, `CON_DESCUADRE`;
    - `type ParteQuien = { prefijo: string | null; tecnico: string; sufijo: string | null; entrega: { texto: string; llego: boolean } | null }`;
    - `type Quien = { partes: ParteQuien[]; enCaja: boolean; descuadre: boolean; etiquetas: string[]; texto: string; titulo: string }`;
    - `quienLoTiene(m: Pick<MovilStock, 'ubicacion' | 'estado' | 'trabajos'>, ahora: number): Quien`.
  - `grado.ts`: `type FamiliaGrado`, `familiaGrado(grado)`, `clasesGrado(grado)`. `PildoraGrado({ grado })`.
  - `CeldaQuien({ quien })`.
  - `nsys.ts`: `diaDeHoyEnMadrid(ahora?: Date): string` («2026-10-16»).
  - `modelo.ts`:
    - `type Movil = MovilStock & { quien: Quien }`;
    - `Dimension` = `'color' | 'grado' | 'bateria' | 'ubicacion' | 'tecnico'` (sin `'estado'`);
    - `preparar(moviles, ahora: number)`, `valores(dim, m): string[]`, `pasa(m, f, salvo: readonly Dimension[] = [])`;
    - `opciones` (con varios valores), `esEspecialTecnico(v)`, `compararGrados(a, b)`, `ORDEN_GRADOS` y `ordenarTabla`
      (el orden nuevo);
    - `agrupar`, `grupoDe` y `totales` siguen hasta la Task 7.
  - Fábrica: `AHORA` (ms del 16/10/2026 13:43 en Madrid) y `visto(p, ahora = AHORA): Movil`.

- [x] **Step 1: Tests de las reglas (fallan)**

Crea `src/modules/telefonos/stock/quien.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { MovilStock } from '@/shared/api/client'
import { AHORA, movil, trabajo } from '../test/fabrica'
import { CON_DESCUADRE, quienLoTiene, SIN_ASIGNAR } from './quien'

const q = (p: Partial<MovilStock>) => quienLoTiene(movil(p), AHORA)

describe('«Quién lo tiene» (spec 0.9.18 §4.7)', () => {
  it('una reparación: el técnico, y el detalle con su fecha', () => {
    const r = q({ ubicacion: 'Taller · Reparación', trabajos: [trabajo({ tecnico: 'Técnico 1' })] })
    expect(r.texto).toBe('Técnico 1')
    expect(r.etiquetas).toEqual(['Técnico 1'])
    expect(r.titulo).toBe('Reparación de Técnico 1 desde el 16/10')
    expect(r.enCaja).toBe(false)
    expect(r.descuadre).toBe(false)
  })

  it('reparación y glass sin entregar, en ese orden aunque la glass venga antes', () => {
    const r = q({ ubicacion: 'Taller · Reparación', trabajos: [
      trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3', desde: '2026-10-15T12:00:00' }), trabajo({ tecnico: 'Técnico 1' })] })
    expect(r.texto).toBe('Técnico 1 · glass Técnico 3 sin entregar')
    expect(r.titulo).toBe('Reparación de Técnico 1 desde el 16/10 · Glass de Técnico 3 desde el 15/10: el móvil aún no le ha llegado')
    expect(r.etiquetas).toEqual(['Técnico 1', 'Técnico 3'])
  })

  it('solo glass: «llegó» con la hora si es de hoy y con el día si no', () => {
    const hoy = q({ ubicacion: 'Taller · Glass', trabajos: [trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3', entregado: '2026-10-16T10:30:00' })] })
    expect(hoy.texto).toBe('Técnico 3 (glass) llegó 10:30')
    expect(hoy.titulo).toBe('Glass de Técnico 3 desde el 16/10: el móvil le llegó a las 10:30')
    expect(hoy.partes[0].entrega).toEqual({ texto: 'llegó 10:30', llego: true })
    const ayer = q({ ubicacion: 'Taller · Glass', trabajos: [trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3', entregado: '2026-10-15T18:05:00' })] })
    expect(ayer.texto).toBe('Técnico 3 (glass) llegó 15/10')
    expect(ayer.titulo).toBe('Glass de Técnico 3 desde el 16/10: el móvil le llegó el 15/10 a las 18:05')
  })

  it('pulido', () => {
    const r = q({ ubicacion: 'Taller · Pulir', trabajos: [trabajo({ categoria: 'PULIDO', tecnico: 'Técnico 2' })] })
    expect(r.texto).toBe('Técnico 2 (pulido)')
    expect(r.titulo).toBe('Pulido de Técnico 2 desde el 16/10')
  })

  it('en una caja de taller sin trabajos: en caja, sin asignar', () => {
    const r = q({ ubicacion: 'Taller · Glass' })
    expect(r.enCaja).toBe(true)
    expect(r.texto).toBe('⚠ En caja, sin asignar')
    expect(r.etiquetas).toEqual([SIN_ASIGNAR])
    expect(r.titulo).toBe('Está en una caja de taller y nadie lo tiene asignado')
  })

  it('un móvil de pedido no está en la caja, aunque su última caja sea de taller', () => {
    const r = q({ ubicacion: 'Taller · Glass', estado: 'DE_PEDIDO' })
    expect(r.enCaja).toBe(false)
    expect(r.texto).toBe('—')
  })

  it('descuadre: trabajo abierto hace más de 60 minutos con el móvil en Listos o de pedido', () => {
    // AHORA es el 16/10 a las 13:43 en Madrid; 12:42 son 61 minutos.
    const listos = q({ ubicacion: 'Listos', trabajos: [trabajo({ desde: '2026-10-16T12:42:00' })] })
    expect(listos.descuadre).toBe(true)
    expect(listos.texto).toBe('⚠ Técnico 1: trabajo abierto')
    expect(listos.etiquetas).toEqual(['Técnico 1', CON_DESCUADRE])
    expect(listos.titulo).toBe('Reparación de Técnico 1 desde el 16/10 · NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?')
    const pedido = q({ ubicacion: 'Taller · Reparación', estado: 'DE_PEDIDO', trabajos: [trabajo()] })
    expect(pedido.descuadre).toBe(true)
    expect(pedido.titulo).toContain('NSYS dice "Pedidos realizados"')
  })

  it('con 60 minutos justos todavía no es descuadre: la caja de NSYS llega con retraso', () => {
    const r = q({ ubicacion: 'Listos', trabajos: [trabajo({ desde: '2026-10-16T12:43:00' })] })
    expect(r.descuadre).toBe(false)
    expect(r.texto).toBe('Técnico 1')
  })

  it('fuera de taller y sin trabajos: una raya, sin etiquetas ni detalle', () => {
    const r = q({ ubicacion: 'En revisión' })
    expect(r.texto).toBe('—')
    expect(r.etiquetas).toEqual([])
    expect(r.titulo).toBe('')
  })
})
```

Crea `src/modules/telefonos/stock/grado.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { clasesGrado, familiaGrado } from './grado'

describe('grado con color por familias (spec 0.9.18 §4.8)', () => {
  it('familias', () => {
    expect(['A+', 'A', 'A-', 'B+', 'B', 'B-', 'C', 'MsA+', 'MsA', 'Ms', 'MsB', 'ID', 'n/a', '', 'X'].map(familiaGrado))
      .toEqual(['A', 'A', 'A', 'B', 'B', 'B', 'C', 'MS', 'MS', 'MS', 'MS', 'ID', 'NA', 'OTRO', 'OTRO'])
    expect(familiaGrado(null)).toBe('OTRO')
  })

  it('borde de color sobre blanco, para no confundirse con las ubicaciones; n/a con borde discontinuo', () => {
    expect(clasesGrado('A')).toContain('border-tipo-reparacion-text')
    expect(clasesGrado('A')).toContain('bg-superficie')
    expect(clasesGrado('B-')).toContain('border-tipo-pulido-text')
    expect(clasesGrado('C')).toContain('border-aviso-conflicto-text')
    expect(clasesGrado('MsB')).toContain('border-tipo-glass-text')
    expect(clasesGrado('ID')).toContain('border-fila-incidencia-brd')
    expect(clasesGrado('n/a')).toContain('border-dashed')
    expect(clasesGrado(null)).toContain('border-pill-borde')
  })
})
```

Sustituye `src/modules/telefonos/stock/modelo.test.ts` entero por:

```ts
import { describe, expect, it } from 'vitest'
import { movilBuscado, trabajo, visto } from '../test/fabrica'
import {
  agrupar, BATERIA_NUEVA, contarFiltros, cuentaComoStock, FILTROS_VACIOS, grupoDe, muestraColor, opciones, ordenarTabla, pasa, textoBuscado, totales, valores,
} from './modelo'
import { CON_DESCUADRE, SIN_ASIGNAR } from './quien'

const A = visto({ imeiSn: '350000000000001', color: 'Azul', grado: 'A', ubicacion: 'Listos' })
const B = visto({ imeiSn: '350000000000002', color: 'Azul', grado: 'A-', bateriaNueva: true, ubicacion: 'Taller · Glass', estado: 'DE_PEDIDO', dias: 30 })
const C = visto({ imeiSn: '350000000000003', color: 'Rosa', grado: 'n/a', ubicacion: 'En revisión' })
const D = visto({ imeiSn: '350000000000004', color: 'Azul', grado: 'B', ubicacion: 'Esperando pieza', estado: 'NO_CUENTA' })
const E = visto({ imeiSn: '350000000000005', modelo: '14', modeloMaestro: '14', ubicacion: 'Listos' })
const F = visto({ imeiSn: '350000000000006', modelo: null, modeloMaestro: null, capacidad: null })
const DOS = visto({ imeiSn: '350000000000008', ubicacion: 'Taller · Reparación', trabajos: [trabajo({ tecnico: 'Técnico 1' }), trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3' })] })

describe('stock de teléfonos: grupos y totales', () => {
  it('modelo + capacidad, del más nuevo al más antiguo y los sin modelo al final', () => {
    expect(grupoDe(A)).toBe('iPhone 13 128GB')
    expect(agrupar([A, F, E, B], false).map((g) => g.clave)).toEqual(['iPhone 14 128GB', 'iPhone 13 128GB', 'Sin modelo en el maestro'])
  })

  it('lo que no cuenta como stock solo sale con el interruptor y nunca suma en los totales', () => {
    expect(agrupar([A, B, C, D], false)[0].moviles).toHaveLength(2)
    const conTodo = agrupar([A, B, C, D], true)[0]
    expect(conTodo.moviles).toHaveLength(4)
    expect(conTodo.totales).toEqual({ total: 2, libres: 2, listos: 1, taller: 0, revision: 1, noCuentan: 2 })
    expect(totales([D])).toEqual({ total: 0, libres: 0, listos: 0, taller: 0, revision: 0, noCuentan: 1 })
  })

  it('lo que está de pedido no cuenta como stock vendible, como el Excel', () => {
    expect(cuentaComoStock(B)).toBe(false)
    expect(cuentaComoStock(D)).toBe(false)
    expect(cuentaComoStock(A)).toBe(true)
    expect(agrupar([B], false)).toEqual([])
  })
})

describe('stock de teléfonos: filtros', () => {
  it('valores de cada dimensión; en el técnico, varios o ninguno', () => {
    expect(valores('bateria', B)).toEqual([BATERIA_NUEVA])
    expect(valores('ubicacion', visto({ ubicacion: null }))).toEqual(['—'])
    expect(valores('tecnico', DOS)).toEqual(['Técnico 1', 'Técnico 3'])
    expect(valores('tecnico', visto({ ubicacion: 'Taller · Glass' }))).toEqual([SIN_ASIGNAR])
    expect(valores('tecnico', A)).toEqual([])
  })

  it('las opciones cuentan con los demás filtros puestos y los grados van en su orden', () => {
    const f = { ...FILTROS_VACIOS, color: new Set(['Azul']) }
    expect(opciones([A, B, C], 'grado', f)).toEqual([{ valor: 'A', cuenta: 1 }, { valor: 'A-', cuenta: 1 }, { valor: 'n/a', cuenta: 0 }])
    expect(opciones([A, B, C], 'color', f)).toEqual([{ valor: 'Azul', cuenta: 2 }, { valor: 'Rosa', cuenta: 1 }])
  })

  it('las opciones del técnico: los técnicos por nombre y al final sin asignar y con descuadre', () => {
    const lista = [
      visto({ imeiSn: '350000000000011', ubicacion: 'Taller · Reparación', trabajos: [trabajo({ tecnico: 'Técnico 2' })] }),
      visto({ imeiSn: '350000000000012', ubicacion: 'Taller · Glass' }),
      visto({ imeiSn: '350000000000013', ubicacion: 'Listos', trabajos: [trabajo({ tecnico: 'Técnico 1' })] }),
    ]
    expect(opciones(lista, 'tecnico', FILTROS_VACIOS)).toEqual([
      { valor: 'Técnico 1', cuenta: 1 }, { valor: 'Técnico 2', cuenta: 1 }, { valor: SIN_ASIGNAR, cuenta: 1 }, { valor: CON_DESCUADRE, cuenta: 1 },
    ])
  })

  it('dentro de un filtro basta con que coincida uno de los valores del móvil', () => {
    expect(pasa(DOS, { ...FILTROS_VACIOS, tecnico: new Set(['Técnico 3']) })).toBe(true)
    expect(pasa(DOS, { ...FILTROS_VACIOS, tecnico: new Set([SIN_ASIGNAR]) })).toBe(false)
  })

  it('pasa los filtros salvo las dimensiones que se están contando', () => {
    const f = { ...FILTROS_VACIOS, color: new Set(['Rosa']), ubicacion: new Set(['Listos']) }
    expect(pasa(A, f)).toBe(false)
    expect(pasa(A, f, ['color'])).toBe(true)
    expect(pasa(C, f, ['color', 'ubicacion'])).toBe(true)
    expect(contarFiltros(f)).toBe(2)
  })

  it('la tabla: primero lo que cuenta; dentro, los descuadres y los sin asignar; luego por días, de menos a más', () => {
    const descuadre = visto({ imeiSn: '350000000000021', ubicacion: 'Listos', dias: 40, trabajos: [trabajo()] })
    const enCaja = visto({ imeiSn: '350000000000022', ubicacion: 'Taller · Pulir', dias: 30 })
    const nuevo = visto({ imeiSn: '350000000000023', ubicacion: 'Listos', dias: 1 })
    const viejo = visto({ imeiSn: '350000000000024', ubicacion: 'Listos', dias: 9 })
    const sinDias = visto({ imeiSn: '350000000000025', ubicacion: 'Listos', dias: null })
    expect(ordenarTabla([B, viejo, sinDias, nuevo, enCaja, descuadre]).map((m) => m.imeiSn)).toEqual([
      '350000000000021', '350000000000022', '350000000000023', '350000000000024', '350000000000025', '350000000000002',
    ])
  })

  it('texto de un móvil encontrado por el buscador', () => {
    expect(textoBuscado(movilBuscado())).toBe('Listos')
    expect(textoBuscado(movilBuscado({ estado: 'DE_PEDIDO', ubicacion: 'Taller · Glass' }))).toBe('De pedido · Taller · Glass')
    expect(textoBuscado(movilBuscado({ estado: 'VENDIDO', ubicacion: null, fechaVenta: '2023-05-02' }))).toBe('Vendido el 02/05/2023')
    expect(textoBuscado(movilBuscado({ estado: 'BAJA', ubicacion: null }))).toBe('Baja en NSYS')
  })

  it('muestra del color: en español o en inglés, sin mayúsculas; gris si no se conoce', () => {
    expect(muestraColor('Azul')).toBe('#3b6fd1')
    expect(muestraColor('midnight')).toBe('#1d2533')
    expect(muestraColor('Titanio desierto')).toBe('#cccccc')
    expect(muestraColor(null)).toBe('#cccccc')
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/quien.test.ts src/modules/telefonos/stock/grado.test.ts src/modules/telefonos/stock/modelo.test.ts`
Expected: FAIL (no existen `quien.ts`, `grado.ts`, `visto` ni `valores`).

- [x] **Step 2: Reglas puras**

En `src/modules/telefonos/stock/nsys.ts`, tras la función `ahoraEnMadrid`, añade:

```ts
/** «2026-10-16»: el día de hoy en Madrid. */
export function diaDeHoyEnMadrid(ahora: Date = new Date()): string {
  return ahoraEnMadrid(ahora).slice(0, 10)
}
```

Crea `src/modules/telefonos/stock/quien.ts`:

```ts
import type { MovilStock, TrabajoAbierto } from '@/shared/api/client'
import { diaDeHoyEnMadrid, minutosDesde } from './nsys'

/** «Quién lo tiene» (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.7): las asignaciones abiertas del
 *  ERP cruzadas con la caja de NSYS. Los trabajos son del momento; la caja, de la última exportación (hasta ~35 min de
 *  retraso), por eso el descuadre espera 60 minutos. Las horas son de Madrid, sin zona: se cortan del texto. */

export const MINUTOS_DESCUADRE = 60
export const SIN_ASIGNAR = 'Sin asignar (en caja de taller)'
export const CON_DESCUADRE = 'Con descuadre'

/** Un trozo de la celda: «glass» + **técnico** + «(pulido)» + la entrega de la glass. */
export type ParteQuien = { prefijo: string | null; tecnico: string; sufijo: string | null; entrega: { texto: string; llego: boolean } | null }
export type Quien = {
  partes: ParteQuien[]
  enCaja: boolean
  descuadre: boolean
  /** Lo que el móvil aporta al filtro «Técnico»: sus técnicos y, si toca, «Sin asignar» y «Con descuadre». */
  etiquetas: string[]
  /** Lo que se lee en la celda; también va en el diálogo de varios IMEIs y al copiar para Excel. */
  texto: string
  /** El detalle al pasar el ratón. */
  titulo: string
}

type Datos = Pick<MovilStock, 'ubicacion' | 'estado' | 'trabajos'>

const ORDEN_CATEGORIA: Record<string, number> = { REPARACION: 0, GLASS: 1, PULIDO: 2 }
const diaMes = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
const hora = (iso: string) => iso.slice(11, 16)

function entrega(t: TrabajoAbierto, hoy: string): { texto: string; llego: boolean } {
  if (!t.entregado) return { texto: 'sin entregar', llego: false }
  return { texto: `llegó ${t.entregado.slice(0, 10) === hoy ? hora(t.entregado) : diaMes(t.entregado)}`, llego: true }
}

function detalle(t: TrabajoAbierto, hoy: string): string {
  const desde = `desde el ${diaMes(t.desde)}`
  if (t.categoria === 'GLASS') {
    const llegada = !t.entregado ? 'el móvil aún no le ha llegado'
      : t.entregado.slice(0, 10) === hoy ? `el móvil le llegó a las ${hora(t.entregado)}`
        : `el móvil le llegó el ${diaMes(t.entregado)} a las ${hora(t.entregado)}`
    return `Glass de ${t.tecnico} ${desde}: ${llegada}`
  }
  return `${t.categoria === 'PULIDO' ? 'Pulido' : 'Reparación'} de ${t.tecnico} ${desde}`
}

/** «Quién lo tiene» de un móvil. `ahora`: milisegundos de la hora de la carga del stock, para el límite del descuadre. */
export function quienLoTiene(m: Datos, ahora: number): Quien {
  const fecha = new Date(ahora)
  const hoy = diaDeHoyEnMadrid(fecha)
  const trabajos = [...m.trabajos].sort((a, b) => (ORDEN_CATEGORIA[a.categoria] ?? 9) - (ORDEN_CATEGORIA[b.categoria] ?? 9))
  const hayReparacion = trabajos.some((t) => t.categoria === 'REPARACION')
  const partes = trabajos.map((t): ParteQuien => {
    if (t.categoria === 'GLASS') {
      return { prefijo: hayReparacion ? 'glass' : null, tecnico: t.tecnico, sufijo: hayReparacion ? null : '(glass)', entrega: entrega(t, hoy) }
    }
    return { prefijo: null, tecnico: t.tecnico, sufijo: t.categoria === 'PULIDO' ? '(pulido)' : null, entrega: null }
  })
  const tecnicos = [...new Set(trabajos.map((t) => t.tecnico))]
  // Un móvil de pedido ya no está en su caja de taller, aunque sea la última que se le conoce.
  const enCaja = m.estado !== 'DE_PEDIDO' && (m.ubicacion ?? '').startsWith('Taller') && trabajos.length === 0
  const fueraDelTaller = m.estado === 'DE_PEDIDO' || m.ubicacion === 'Listos'
  const descuadre = fueraDelTaller && trabajos.some((t) => minutosDesde(t.desde, fecha) > MINUTOS_DESCUADRE)
  const caja = m.estado === 'DE_PEDIDO' ? 'Pedidos realizados' : (m.ubicacion ?? '')
  const texto = descuadre ? `⚠ ${tecnicos.join(' · ')}: trabajo abierto`
    : enCaja ? '⚠ En caja, sin asignar'
      : partes.length === 0 ? '—'
        : partes.map((p) => [p.prefijo, p.tecnico, p.sufijo, p.entrega?.texto].filter(Boolean).join(' ')).join(' · ')
  const titulo = [
    ...trabajos.map((t) => detalle(t, hoy)),
    ...(descuadre ? [`NSYS dice "${caja}", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?`] : []),
    ...(enCaja ? ['Está en una caja de taller y nadie lo tiene asignado'] : []),
  ].join(' · ')
  const etiquetas = [...tecnicos, ...(enCaja ? [SIN_ASIGNAR] : []), ...(descuadre ? [CON_DESCUADRE] : [])]
  return { partes, enCaja, descuadre, etiquetas, texto, titulo }
}
```

Crea `src/modules/telefonos/stock/grado.ts`:

```ts
/** Familias del grado de NSYS para su color (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.8). */
export type FamiliaGrado = 'A' | 'B' | 'C' | 'MS' | 'ID' | 'NA' | 'OTRO'

export function familiaGrado(grado: string | null | undefined): FamiliaGrado {
  if (!grado) return 'OTRO'
  if (grado === 'n/a') return 'NA'
  if (grado === 'ID') return 'ID'
  if (grado.startsWith('Ms')) return 'MS'
  const letra = grado.charAt(0)
  return letra === 'A' || letra === 'B' || letra === 'C' ? letra : 'OTRO'
}

/** Borde de color y fondo blanco: así el grado no se confunde con las ubicaciones, que son píldoras rellenas. Tokens del
 *  tema: A azul, B violeta, C naranja, Ms verde azulado, ID rosa y n/a ámbar con borde discontinuo. */
const CLASES_FAMILIA: Record<FamiliaGrado, string> = {
  A: 'border-tipo-reparacion-text text-tipo-reparacion-text',
  B: 'border-tipo-pulido-text text-tipo-pulido-text',
  C: 'border-aviso-conflicto-text text-aviso-conflicto-text',
  MS: 'border-tipo-glass-text text-tipo-glass-text',
  ID: 'border-fila-incidencia-brd text-fila-incidencia-brd',
  NA: 'border-dashed border-fila-solicitud-brd text-fila-solicitud-brd',
  OTRO: 'border-pill-borde text-azul-medio',
}

export function clasesGrado(grado: string | null | undefined): string {
  return `inline-block rounded-full border-[1.5px] bg-superficie px-2 text-[11.5px] leading-[18px] font-bold ${CLASES_FAMILIA[familiaGrado(grado)]}`
}
```

Crea `src/modules/telefonos/stock/PildoraGrado.tsx`:

```tsx
import { clasesGrado } from './grado'

/** El grado con su color de familia (spec §4.8): en la tabla de móviles, en la tabla color × grado y en el diálogo de
 *  varios IMEIs. Sin grado, una raya. */
export function PildoraGrado({ grado }: { grado: string | null | undefined }) {
  return <span className={clasesGrado(grado)}>{grado || '—'}</span>
}
```

Sustituye `src/modules/telefonos/stock/modelo.ts` entero por:

```ts
import type { MovilBuscado, MovilStock } from '@/shared/api/client'
import { MODELOS_ORDENADOS } from '@/shared/lib/modelos'
import { diaNsys, nombreModeloNsys } from '@/shared/lib/telefonoNsys'
import { CON_DESCUADRE, quienLoTiene, SIN_ASIGNAR, type Quien } from './quien'

/** Reglas puras de «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; 0.9.18, spec «Columna
 *  compacta, SKU, Quién lo tiene y Revisado» §4): grupos con sus totales, filtros por color, grado, batería, ubicación y
 *  técnico, y el orden de la tabla. */

/** Un móvil del stock con «Quién lo tiene» ya calculado a la hora de la carga. */
export type Movil = MovilStock & { quien: Quien }
export type Dimension = 'color' | 'grado' | 'bateria' | 'ubicacion' | 'tecnico'
export type Filtros = Record<Dimension, ReadonlySet<string>>
export type Opcion = { valor: string; cuenta: number }
export type Totales = { total: number; libres: number; listos: number; taller: number; revision: number; noCuentan: number }
export type Grupo = { clave: string; sinModelo: boolean; moviles: Movil[]; totales: Totales }

export const FILTROS_VACIOS: Filtros = { color: new Set(), grado: new Set(), bateria: new Set(), ubicacion: new Set(), tecnico: new Set() }
export const BATERIA_NUEVA = 'Nueva (100 %)'
export const BATERIA_ORIGINAL = 'Original'
export const SIN_DATO = '—'
export const ORDEN_GRADOS: readonly string[] = ['A+', 'A', 'A-', 'B+', 'B', 'B-', 'C', 'MsA+', 'MsA', 'MsA-', 'MsB', 'MsC', 'Ms', 'ID', 'n/a']
/** Las opciones del filtro «Técnico» que no son un técnico: van al final, tras una línea. */
const ESPECIALES_TECNICO: readonly string[] = [SIN_ASIGNAR, CON_DESCUADRE]

/** Añade «Quién lo tiene» a cada móvil. `ahora`: milisegundos de la carga (`dataUpdatedAt`), para el límite del descuadre. */
export function preparar(moviles: MovilStock[], ahora: number): Movil[] {
  return moviles.map((m) => ({ ...m, quien: quienLoTiene(m, ahora) }))
}

/** Grupo de la lista de la izquierda: modelo + capacidad («iPhone 13 128GB»). */
export function grupoDe(m: MovilStock): string {
  return [nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad].filter(Boolean).join(' ')
}

/** Los valores de un móvil en una dimensión: uno, salvo en «Técnico», donde puede tener varios (reparación de uno y glass
 *  de otro) o ninguno. */
export function valores(dim: Dimension, m: Movil): string[] {
  switch (dim) {
    case 'color': return [m.color || SIN_DATO]
    case 'grado': return [m.grado || SIN_DATO]
    case 'bateria': return [m.bateriaNueva ? BATERIA_NUEVA : BATERIA_ORIGINAL]
    case 'ubicacion': return [m.ubicacion ?? SIN_DATO]
    case 'tecnico': return m.quien.etiquetas
  }
}

/** Solo el stock vendible cuenta: lo que está de pedido (vendido y sin liberar en NSYS) y las cajas que no cuentan, no. */
export const cuentaComoStock = (m: MovilStock): boolean => m.estado === 'STOCK'

/** ¿Pasa los filtros? Entre filtros, Y; dentro de uno, O: basta con que coincida uno de los valores del móvil. `salvo`: las
 *  dimensiones que no se aplican (para contar las opciones de esa misma dimensión). */
export function pasa(m: Movil, f: Filtros, salvo: readonly Dimension[] = []): boolean {
  return (Object.keys(f) as Dimension[]).every((d) => salvo.includes(d) || f[d].size === 0 || valores(d, m).some((v) => f[d].has(v)))
}

export const esEspecialTecnico = (v: string): boolean => ESPECIALES_TECNICO.includes(v)

const indiceGrado = (g: string) => (ORDEN_GRADOS.includes(g) ? ORDEN_GRADOS.indexOf(g) : ORDEN_GRADOS.length)
export const compararGrados = (a: string, b: string): number => indiceGrado(a) - indiceGrado(b) || a.localeCompare(b, 'es')

function orden(dim: Dimension): (a: string, b: string) => number {
  if (dim === 'grado') return compararGrados
  // Los técnicos por nombre; «Sin asignar» y «Con descuadre», al final y en ese orden.
  if (dim === 'tecnico') return (a, b) => ESPECIALES_TECNICO.indexOf(a) - ESPECIALES_TECNICO.indexOf(b) || a.localeCompare(b, 'es')
  return (a, b) => a.localeCompare(b, 'es')
}

/** Las opciones de una dimensión y cuántos móviles quedarían con cada una, con los demás filtros puestos. */
export function opciones(moviles: Movil[], dim: Dimension, f: Filtros): Opcion[] {
  const cuentas = new Map<string, number>()
  for (const m of moviles) {
    const pasaLosDemas = pasa(m, f, [dim])
    for (const v of valores(dim, m)) cuentas.set(v, (cuentas.get(v) ?? 0) + (pasaLosDemas ? 1 : 0))
  }
  return [...cuentas.keys()].sort(orden(dim)).map((v) => ({ valor: v, cuenta: cuentas.get(v) ?? 0 }))
}

/** Totales de un grupo: solo cuentan los que cuentan como stock; los demás van aparte en `noCuentan`. */
export function totales(moviles: MovilStock[]): Totales {
  const t: Totales = { total: 0, libres: 0, listos: 0, taller: 0, revision: 0, noCuentan: 0 }
  for (const m of moviles) {
    if (!cuentaComoStock(m)) {
      t.noCuentan++
      continue
    }
    t.total++
    t.libres++
    const u = m.ubicacion ?? ''
    if (u === 'Listos') t.listos++
    else if (u.startsWith('Taller')) t.taller++
    else if (u === 'En revisión' || u === 'Devolución') t.revision++
  }
  return t
}

/** Capacidad en GB («64GB» → 64, «1TB» → 1024); null si no se entiende. */
export function capacidadGb(capacidad: string | null | undefined): number | null {
  const r = /^\s*(\d+(?:[.,]\d+)?)\s*(GB|TB)\s*$/i.exec(capacidad ?? '')
  if (!r) return null
  return Number(r[1].replace(',', '.')) * (r[2].toUpperCase() === 'TB' ? 1024 : 1)
}

/** Rango del grupo: 0 con modelo del catálogo, 1 solo con modelo del maestro, 2 sin modelo. */
function rango(m: MovilStock): number {
  if (m.modelo) return 0
  return m.modeloMaestro ? 1 : 2
}

/** Grupos modelo + capacidad en el orden de la tienda: modelos del catálogo del más nuevo al más antiguo, después los que
 *  solo tienen modelo del maestro y al final los que no tienen modelo; dentro de un modelo, capacidad de menor a mayor. */
export function agrupar(moviles: Movil[], verNoCuenta: boolean): Grupo[] {
  const mapa = new Map<string, Movil[]>()
  for (const m of moviles) {
    if (!verNoCuenta && !cuentaComoStock(m)) continue
    const clave = grupoDe(m)
    const lista = mapa.get(clave)
    if (lista) lista.push(m)
    else mapa.set(clave, [m])
  }
  const indice = (m: MovilStock) => MODELOS_ORDENADOS.indexOf(m.modelo ?? '')
  return [...mapa.entries()]
    .map(([clave, lista]) => ({ clave, sinModelo: !lista[0].modelo && !lista[0].modeloMaestro, moviles: lista, totales: totales(lista) }))
    .sort((a, b) => {
      const ma = a.moviles[0]
      const mb = b.moviles[0]
      const porRango = rango(ma) - rango(mb)
      if (porRango) return porRango
      if (rango(ma) === 0 && indice(ma) !== indice(mb)) return indice(mb) - indice(ma)
      if (rango(ma) === 1 && ma.modeloMaestro !== mb.modeloMaestro) return (mb.modeloMaestro ?? '').localeCompare(ma.modeloMaestro ?? '', 'es', { numeric: true })
      const ga = capacidadGb(ma.capacidad)
      const gb = capacidadGb(mb.capacidad)
      if (ga !== gb) return ga === null ? 1 : gb === null ? -1 : ga - gb
      return a.clave.localeCompare(b.clave, 'es', { numeric: true })
    })
}

export function contarFiltros(f: Filtros): number {
  return Object.values(f).reduce((n, s) => n + s.size, 0)
}

/** Orden de la tabla (spec 0.9.18 §4.6): primero lo que cuenta; dentro de eso, los descuadres y los «en caja, sin
 *  asignar»; luego por días, de menos a más (sin días, al final). */
export function ordenarTabla(moviles: Movil[]): Movil[] {
  const aviso = (m: Movil) => (m.quien.descuadre ? 0 : m.quien.enCaja ? 1 : 2)
  const dias = (m: Movil) => m.dias ?? Number.MAX_SAFE_INTEGER
  return [...moviles].sort((a, b) => Number(cuentaComoStock(b)) - Number(cuentaComoStock(a)) || aviso(a) - aviso(b) || dias(a) - dias(b))
}

/** Lo que se dice de un móvil encontrado por el buscador de IMEI. */
export function textoBuscado(m: MovilBuscado): string {
  if (m.estado === 'VENDIDO') return m.fechaVenta ? `Vendido el ${diaNsys(m.fechaVenta)}` : 'Vendido'
  if (m.estado === 'BAJA') return 'Baja en NSYS'
  if (m.estado === 'DE_PEDIDO') return `De pedido · ${m.ubicacion ?? SIN_DATO}`
  if (m.estado === 'NO_CUENTA') return `${m.ubicacion ?? SIN_DATO} · no cuenta como stock`
  return m.ubicacion ?? SIN_DATO
}

/** Muestra de color de los chips y de la tabla (las del prototipo, en español y en inglés). Las que no están, en gris. */
const MUESTRAS: Record<string, string> = {
  azul: '#3b6fd1', blue: '#3b6fd1', medianoche: '#1d2533', midnight: '#1d2533', blanco: '#f4f4f4', white: '#f4f4f4',
  'blanco estelar': '#f4f1ea', starlight: '#f4f1ea', rosa: '#f3b6c7', pink: '#f3b6c7', morado: '#b9a6dc', purple: '#b9a6dc',
  verde: '#9cc9a6', green: '#9cc9a6', rojo: '#c8323a', red: '#c8323a', negro: '#222222', black: '#222222',
  lavanda: '#cdbfe8', lavender: '#cdbfe8', amarillo: '#f2d46b', yellow: '#f2d46b', plata: '#d8d8d8', silver: '#d8d8d8',
  oro: '#e6cfa1', gold: '#e6cfa1', grafito: '#4a4a4c', graphite: '#4a4a4c', 'gris espacial': '#5b5d61', 'space gray': '#5b5d61',
}

export function muestraColor(color: string | null | undefined): string {
  return (color && MUESTRAS[color.trim().toLowerCase()]) || '#cccccc'
}
```

En `src/modules/telefonos/test/fabrica.ts`, añade el import `import { preparar, type Movil } from '../stock/modelo'` y,
tras la función `movil`:

```ts
/** 16/10/2026 13:43 en Madrid (CEST, UTC+2), en milisegundos: la «hora de la carga» de los tests de reglas. */
export const AHORA = Date.parse('2026-10-16T11:43:00Z')

/** Un móvil ya preparado para las reglas, con «Quién lo tiene» calculado a las 13:43 del 16/10. */
export function visto(p: Partial<MovilStock> = {}, ahora: number = AHORA): Movil {
  return preparar([movil(p)], ahora)[0]
}
```

Run: `npx vitest run src/modules/telefonos/stock/quien.test.ts src/modules/telefonos/stock/grado.test.ts src/modules/telefonos/stock/modelo.test.ts`
Expected: PASS. `npx tsc -b` todavía falla en `FiltrosMoviles.tsx`, `columnas.tsx` y `StockTelefonosPage.tsx` (usan
`'estado'` y `MovilStock`); se arreglan en los pasos siguientes.

- [x] **Step 3: Tests de la pantalla (fallan)**

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`:
1. Cambia el import de las fábricas por `import { estadoNsys, movil, movilBuscado, trabajo } from '../test/fabrica'`.
2. En «el interruptor enseña lo que no cuenta como stock…», cambia `expect(screen.getAllByText('NO CUENTA')).toHaveLength(2)`
   por `expect(screen.getAllByText(/^NO CUENTA · /)).toHaveLength(2)`.
3. En «lo de pedido se oculta sin el interruptor…», cambia `expect(within(fila).getByText('De pedido')).not.toHaveClass('text-fila-urgente-brd')`
   por `expect(within(fila).getByText('NO CUENTA · De pedido')).not.toHaveClass('text-fila-urgente-brd')`.
4. Añade al final del `describe`:

```tsx
  const CON_TRABAJOS = [
    movil({ imeiSn: '350000000000011', ubicacion: 'Taller · Reparación', dias: 3,
      trabajos: [trabajo({ tecnico: 'Técnico 1' }), trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3' })] }),
    movil({ imeiSn: '350000000000012', ubicacion: 'Taller · Glass', dias: 5 }),
    movil({ imeiSn: '350000000000013', ubicacion: 'Listos', dias: 1, trabajos: [trabajo({ tecnico: 'Técnico 2', desde: '2026-01-12T09:00:00' })] }),
  ]
  const imeiDe = (fila: HTMLElement) => fila.textContent?.match(/3500000000000\d\d/)?.[0]

  it('«Quién lo tiene»: el descuadre y la caja sin asignar primero, con sus avisos junto a los filtros', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json(CON_TRABAJOS)))
    abrir()
    await user.click(await screen.findByRole('button', { name: /^iPhone 13 128GB/ }))
    expect(screen.getByRole('columnheader', { name: 'Quién lo tiene' })).toBeInTheDocument()
    expect(filas().map(imeiDe)).toEqual(['350000000000013', '350000000000012', '350000000000011'])
    expect(filas()[0]).toHaveTextContent('⚠ Técnico 2: trabajo abierto')
    expect(filas()[1]).toHaveTextContent('⚠ En caja, sin asignar')
    expect(filas()[2]).toHaveTextContent('Técnico 1 · glass Técnico 3 sin entregar')
    expect(within(filas()[2]).getByText('Técnico 1').closest('[title]')).toHaveAttribute('title',
      'Reparación de Técnico 1 desde el 16/10 · Glass de Técnico 3 desde el 16/10: el móvil aún no le ha llegado')
    expect(screen.getByText('⚠ 1 en caja de taller sin asignar')).toBeInTheDocument()
    expect(screen.getByText('⚠ 1 con descuadre')).toBeInTheDocument()
  })

  it('el filtro Técnico: un técnico, los sin asignar y los descuadres; un móvil sale si coincide uno', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json(CON_TRABAJOS)))
    abrir()
    await user.click(await screen.findByRole('button', { name: /^iPhone 13 128GB/ }))
    await user.click(screen.getByRole('button', { name: 'Técnico' }))
    await user.click(screen.getByRole('checkbox', { name: 'Técnico 3 (1)' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000011'])
    await user.click(screen.getByRole('checkbox', { name: 'Sin asignar (en caja de taller) (1)' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000012', '350000000000011'])
    await user.click(screen.getByRole('checkbox', { name: 'Con descuadre (1)' }))
    expect(filas()).toHaveLength(3)
    expect(screen.getByRole('button', { name: 'Técnico' })).toHaveTextContent('Técnico: 3')
  })

  it('el grado lleva su color y las marcas de batería nueva y eSIM', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json([
      movil({ imeiSn: '350000000000031', grado: 'MsB', bateriaNueva: true, esEsim: true }),
    ])))
    abrir()
    await user.click(await screen.findByRole('button', { name: /^iPhone 13 128GB/ }))
    const celda = within(filas()[0]).getAllByRole('cell')[2]
    expect(within(celda).getByText('MsB')).toHaveClass('border-tipo-glass-text')
    expect(within(celda).getByText('100')).toHaveAttribute('title', 'Batería nueva')
    expect(within(celda).getByText('eSIM')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Batería' })).not.toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Estado' })).not.toBeInTheDocument()
  })
```

Run: `npx vitest run src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no hay columna «Quién lo tiene» ni filtro «Técnico»).

- [x] **Step 4: Pantalla**

Crea `src/modules/telefonos/stock/CeldaQuien.tsx`:

```tsx
import { Fragment } from 'react'
import { cn } from '@/shared/lib/utils'
import type { Quien } from './quien'

/** La celda «Quién lo tiene» (spec 0.9.18 §4.7): la única de la tabla que puede partirse en dos líneas. El detalle (desde
 *  cuándo y la llegada a la glass), al pasar el ratón. Su texto es el mismo que `quien.texto`. */
export function CeldaQuien({ quien }: { quien: Quien }) {
  const titulo = quien.titulo || undefined
  if (quien.descuadre) return <span title={titulo} className="font-bold whitespace-normal text-fila-urgente-brd">{quien.texto}</span>
  if (quien.enCaja) return <span title={titulo} className="font-bold whitespace-normal text-fila-solicitud-brd">{quien.texto}</span>
  if (quien.partes.length === 0) return <span className="text-texto-suave">—</span>
  return (
    <span title={titulo} className="whitespace-normal">
      {quien.partes.map((p, i) => (
        <Fragment key={i}>
          {i > 0 && ' · '}
          {p.prefijo && <span className="text-azul-gris">{`${p.prefijo} `}</span>}
          <b className="text-azul-noche">{p.tecnico}</b>
          {p.sufijo && <span className="text-azul-gris">{` ${p.sufijo}`}</span>}
          {p.entrega && (
            <>
              {' '}
              <span className={cn('rounded px-1 text-[10.5px] font-bold', p.entrega.llego ? 'bg-tipo-reparacion-bg text-tipo-reparacion-text' : 'bg-fila-solicitud-bg text-fila-solicitud-brd')}>
                {p.entrega.texto}
              </span>
            </>
          )}
        </Fragment>
      ))}
    </span>
  )
}
```

Sustituye `src/modules/telefonos/stock/columnas.tsx` entero por:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import { clasesUbicacion, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { CeldaQuien } from './CeldaQuien'
import { muestraColor, type Movil } from './modelo'
import { PildoraGrado } from './PildoraGrado'

const MARCA = 'ml-1 inline-block rounded border px-1 align-[1px] text-[10px] font-bold'

/** Columnas de la tabla de móviles (0.9.18, spec §4.6): IMEI · Color · Grado (con «100» si la batería es nueva y «eSIM»)
 *  · SKU · Ubicación (con «NO CUENTA · …» si no cuenta como stock) · Quién lo tiene · Días. El IMEI es un botón que abre
 *  el detalle. */
export function columnasMoviles(abrir: (imeiSn: string) => void): ColumnDef<Movil>[] {
  return [
    {
      id: 'imei', header: 'IMEI', size: 150, maxSize: 160,
      cell: ({ row }) => (
        <button type="button" aria-label={`Abrir ${row.original.imeiSn}`} onClick={(e) => { e.stopPropagation(); abrir(row.original.imeiSn) }}
          className="cursor-pointer font-mono text-[11.5px] text-azul-noche hover:underline">{row.original.imeiSn}</button>
      ),
    },
    {
      id: 'color', header: 'Color', size: 110,
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="h-2.5 w-2.5 rounded-full border border-pill-borde" style={{ background: muestraColor(row.original.color) }} />
          {row.original.color ?? '—'}
        </span>
      ),
    },
    {
      id: 'grado', header: 'Grado', size: 110, maxSize: 120,
      cell: ({ row }) => (
        <>
          <PildoraGrado grado={row.original.grado} />
          {row.original.bateriaNueva && <span title="Batería nueva" className={cn(MARCA, 'border-fila-recibido-brd text-fila-recibido-brd')}>100</span>}
          {row.original.esEsim && <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>eSIM</span>}
        </>
      ),
    },
    { id: 'sku', header: 'SKU', size: 150, cell: ({ row }) => <span className="font-mono text-[11.5px]">{row.original.sku ?? '—'}</span> },
    {
      id: 'ubicacion', header: 'Ubicación', size: 190,
      cell: ({ row }) => (
        <>
          <span className={cn(CLASES_PILDORA, clasesUbicacion(row.original.ubicacion))}>{row.original.ubicacion ?? '—'}</span>
          {(row.original.estado === 'NO_CUENTA' || row.original.estado === 'DE_PEDIDO') && (
            <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>{`NO CUENTA · ${textoEstadoMovil(row.original.estado)}`}</span>
          )}
        </>
      ),
    },
    { id: 'quien', header: 'Quién lo tiene', size: 210, cell: ({ row }) => <CeldaQuien quien={row.original.quien} /> },
    { id: 'dias', header: 'Días', size: 55, maxSize: 60, accessorFn: (m) => m.dias ?? '' },
  ]
}
```

En `src/modules/telefonos/stock/FiltrosMoviles.tsx`:
1. Cambia los imports de tipos por `import { contarFiltros, esEspecialTecnico, FILTROS_VACIOS, muestraColor, opciones, type Dimension, type Filtros, type Movil } from './modelo'`
   y quita el de `MovilStock`.
2. Cambia `type Props = { moviles: MovilStock[]; … }` por `type Props = { moviles: Movil[]; filtros: Filtros; onCambiar: (f: Filtros) => void }`.
3. Sustituye la función `desplegable` por:

```tsx
  const desplegable = (dim: Dimension, titulo: string, todas: string) => {
    const lista = opciones(moviles, dim, filtros)
    return (
      <MultiSelect opciones={lista} clave={(o) => o.valor} etiqueta={(o) => `${o.valor} (${o.cuenta})`}
        seleccion={new Set(filtros[dim])} onChange={(s) => onCambiar({ ...filtros, [dim]: s })}
        textoVacio={`${titulo}: ${todas}`} textoPlural={(n) => `${titulo}: ${n}`} etiquetaAccesible={titulo} className="min-w-[150px]"
        separadorAntes={dim === 'tecnico' ? lista.find((o) => esEspecialTecnico(o.valor))?.valor : undefined} />
    )
  }
  // Avisos de «Quién lo tiene» (spec §4.7): de los móviles que se ven del modelo; con el interruptor apagado, solo el stock.
  const enCaja = moviles.filter((m) => m.quien.enCaja).length
  const descuadres = moviles.filter((m) => m.quien.descuadre).length
```

4. Cambia `{desplegable('estado', 'Estado', 'todos')}` por:

```tsx
        {desplegable('tecnico', 'Técnico', 'todos')}
        {enCaja > 0 && <span className="text-[12px] font-bold text-fila-solicitud-brd">{`⚠ ${enCaja} en caja de taller sin asignar`}</span>}
        {descuadres > 0 && <span className="text-[12px] font-bold text-fila-urgente-brd">{`⚠ ${descuadres} con descuadre`}</span>}
```

5. Cambia el comentario de la función por: «Filtros de la derecha: chips de color y grado con su número y desplegables de
   batería, ubicación y técnico (0.9.18), con los avisos de «Quién lo tiene». Cada número dice cuántos móviles quedarían
   con los demás filtros puestos. Los que darían cero, apagados.»

En `src/modules/telefonos/stock/StockTelefonosPage.tsx`:
1. Cambia el import de `./modelo` por `import { agrupar, cuentaComoStock, FILTROS_VACIOS, ordenarTabla, pasa, preparar, type Filtros } from './modelo'`.
2. Cambia `const moviles = useMemo(() => stock.data ?? [], [stock.data])` por:

```tsx
  // «Quién lo tiene» se calcula a la hora de la carga (dataUpdatedAt): el sondeo de 60 s lo vuelve a calcular.
  const moviles = useMemo(() => preparar(stock.data ?? [], stock.dataUpdatedAt), [stock.data, stock.dataUpdatedAt])
```

3. Cambia el texto del pie (`<p className="mt-2 …">El IMEI abre el detalle…</p>`) por:

```tsx
              <p className="mt-2 text-[11.5px] text-azul-gris">{PIE}</p>
```

y, tras los imports, la constante:

```tsx
const PIE = '"Quién lo tiene" sale de las asignaciones abiertas del ERP, al momento; la caja, de la última exportación de NSYS. Pasa el ratón por encima para ver desde cuándo. El IMEI abre el detalle del móvil.'
```

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 6: Commit**

```bash
git add src/modules/telefonos
git commit -m "feat: quien lo tiene, grado con color y filtro tecnico en el stock de telefonos (0.9.18)"
```

---

### Task 7: Web — columna compacta y pestañas de capacidad

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.ts` (entero), `modelo.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/estado.ts` (entero)
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/BarraReparto.tsx`, `PestanasCapacidad.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/ListaModelos.tsx` (entero), `columnas.tsx` (entero), `StockTelefonosPage.tsx` (entero)
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx` (entero)

**Interfaces:**
- Consumes: `Movil`, `preparar`, `valores`, `pasa`, `opciones`, `ordenarTabla`, `contarFiltros` y la fábrica `visto` (Task 6).
- Produces:
  - `modelo.ts`:
    - tipos `Reparto = { listos; taller; revision }`, `Capacidad = { capacidad: string; stock: number }`,
      `ModeloLista = { clave; total; capacidades: Capacidad[]; reparto: Reparto; moviles: Movil[] }` y
      `Pestana = { capacidad: string | null; stock: number; activa: boolean }`;
    - `modeloDe(m)` («iPhone 13 Pro»), `capacidadDe(m)` (`m.capacidad` o «—»), `textoCapacidad(c)` («128 GB»),
      `capacidadCorta(c)` («128»), `reparto(moviles)`, `capacidades(moviles)`, `modelosLista(moviles, verNoCuenta)` y
      `pestanasCapacidad(todos, verNoCuenta)`;
    - se quitan `Grupo`, `Totales`, `grupoDe`, `totales` y `agrupar`.
  - `estado.ts`: `EstadoStock` añade `capacidad: string | null`; `claveFiltros(modelo, capacidad)`.
  - `BarraReparto({ reparto, className })`, `PestanasCapacidad({ pestanas, elegida, onElegir })`.
  - `ListaModelos({ modelos, elegido, capacidad, buscador, onAbrir(modelo, capacidad) })`: el buscador entra como `ReactNode`.
  - `columnasMoviles(abrir, { conCapacidad })`: con `conCapacidad`, la columna «Cap.» detrás del IMEI.

- [x] **Step 1: Tests de las reglas (fallan)**

En `src/modules/telefonos/stock/modelo.test.ts`:
1. Cambia el import de `./modelo` por:

```ts
import {
  BATERIA_NUEVA, capacidadCorta, contarFiltros, cuentaComoStock, FILTROS_VACIOS, modelosLista, muestraColor, opciones, ordenarTabla, pasa,
  pestanasCapacidad, reparto, textoBuscado, textoCapacidad, valores,
} from './modelo'
```

2. Sustituye el `describe('stock de teléfonos: grupos y totales', …)` entero por:

```ts
describe('stock de teléfonos: una fila por modelo (spec 0.9.18 §4.1) y pestañas de capacidad (§4.3)', () => {
  it('una fila por modelo: del catálogo del más nuevo al más antiguo, los del maestro después y los sin modelo al final', () => {
    expect(modelosLista([A, F, E], false).map((g) => g.clave)).toEqual(['iPhone 14', 'iPhone 13', 'Sin modelo en el maestro'])
    const p = (modelo: string | null, capacidad: string | null, maestro: string | null = modelo) => visto({ modelo, modeloMaestro: maestro, capacidad })
    const lista = [
      p(null, null, null), p('xsmax', '64GB'), p(null, '128GB', '17e'), p('13promax', '1TB'), p('se2020', '64GB'),
      p('16', '128GB'), p('13promax', '256GB'), p('16e', '128GB'),
    ]
    expect(modelosLista(lista, false).map((g) => g.clave)).toEqual([
      'iPhone 16e', 'iPhone 16', 'iPhone 13 Pro Max', 'iPhone XS Max', 'iPhone SE 2020', 'iPhone 17e', 'Sin modelo en el maestro',
    ])
  })

  it('el total y las capacidades cuentan solo el stock, también con el interruptor; capacidades de menor a mayor', () => {
    const fila = modelosLista([A, B, C, D, visto({ imeiSn: '350000000000009', capacidad: '1TB' }),
      visto({ imeiSn: '350000000000010', capacidad: '256GB', estado: 'DE_PEDIDO' })], true)[0]
    expect(fila.clave).toBe('iPhone 13')
    expect(fila.total).toBe(3)
    expect(fila.capacidades).toEqual([{ capacidad: '128GB', stock: 2 }, { capacidad: '256GB', stock: 0 }, { capacidad: '1TB', stock: 1 }])
    expect(fila.reparto).toEqual({ listos: 2, taller: 0, revision: 1 })
    expect(fila.moviles).toHaveLength(6)
    expect(modelosLista([A, B, C, D], false)[0].moviles).toHaveLength(2)
  })

  it('con el interruptor apagado solo salen los modelos con stock', () => {
    expect(modelosLista([B, D], false)).toEqual([])
    expect(modelosLista([B, D], true).map((g) => g.clave)).toEqual(['iPhone 13'])
  })

  it('las pestañas: «Todas» y cada capacidad del modelo; sin stock, apagadas salvo con el interruptor si tienen algo', () => {
    const todos = [A, C, visto({ imeiSn: '350000000000010', capacidad: '512GB', estado: 'DE_PEDIDO' })]
    expect(pestanasCapacidad(todos, false)).toEqual([
      { capacidad: null, stock: 2, activa: true }, { capacidad: '128GB', stock: 2, activa: true }, { capacidad: '512GB', stock: 0, activa: false },
    ])
    expect(pestanasCapacidad(todos, true)[2]).toEqual({ capacidad: '512GB', stock: 0, activa: true })
  })

  it('textos de la capacidad: «128 GB» en las pestañas y «128» en las pastillas', () => {
    expect(textoCapacidad('128GB')).toBe('128 GB')
    expect(textoCapacidad('1TB')).toBe('1 TB')
    expect(textoCapacidad('—')).toBe('—')
    expect(capacidadCorta('128GB')).toBe('128')
    expect(capacidadCorta('1TB')).toBe('1TB')
  })

  it('lo que está de pedido no cuenta como stock vendible, como el Excel', () => {
    expect(cuentaComoStock(B)).toBe(false)
    expect(cuentaComoStock(D)).toBe(false)
    expect(cuentaComoStock(A)).toBe(true)
    expect(reparto([B, D])).toEqual({ listos: 0, taller: 0, revision: 0 })
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/modelo.test.ts`
Expected: FAIL (no existen `modelosLista`, `pestanasCapacidad`…).

- [x] **Step 2: Reglas puras y store**

Sustituye `src/modules/telefonos/stock/modelo.ts` entero por:

```ts
import type { MovilBuscado, MovilStock } from '@/shared/api/client'
import { MODELOS_ORDENADOS } from '@/shared/lib/modelos'
import { diaNsys, nombreModeloNsys } from '@/shared/lib/telefonoNsys'
import { CON_DESCUADRE, quienLoTiene, SIN_ASIGNAR, type Quien } from './quien'

/** Reglas puras de «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; 0.9.18, spec «Columna
 *  compacta, SKU, Quién lo tiene y Revisado» §4): una fila por modelo con sus capacidades, pestañas de capacidad, filtros
 *  por color, grado, batería, ubicación y técnico, y el orden de la tabla. */

/** Un móvil del stock con «Quién lo tiene» ya calculado a la hora de la carga. */
export type Movil = MovilStock & { quien: Quien }
export type Dimension = 'color' | 'grado' | 'bateria' | 'ubicacion' | 'tecnico'
export type Filtros = Record<Dimension, ReadonlySet<string>>
export type Opcion = { valor: string; cuenta: number }
export type Reparto = { listos: number; taller: number; revision: number }
export type Capacidad = { capacidad: string; stock: number }
/** Una fila de la columna lateral (spec §4.1): un modelo con su total en stock, sus capacidades y el reparto listos /
 *  taller / revisión. `moviles`: los que se ven del modelo (con el interruptor apagado, solo el stock). */
export type ModeloLista = { clave: string; total: number; capacidades: Capacidad[]; reparto: Reparto; moviles: Movil[] }
/** Una pestaña de capacidad de la cabecera del modelo (spec §4.3): `capacidad` null es «Todas»; `activa`, que tiene algo
 *  que enseñar. */
export type Pestana = { capacidad: string | null; stock: number; activa: boolean }

export const FILTROS_VACIOS: Filtros = { color: new Set(), grado: new Set(), bateria: new Set(), ubicacion: new Set(), tecnico: new Set() }
export const BATERIA_NUEVA = 'Nueva (100 %)'
export const BATERIA_ORIGINAL = 'Original'
export const SIN_DATO = '—'
export const ORDEN_GRADOS: readonly string[] = ['A+', 'A', 'A-', 'B+', 'B', 'B-', 'C', 'MsA+', 'MsA', 'MsA-', 'MsB', 'MsC', 'Ms', 'ID', 'n/a']
/** Las opciones del filtro «Técnico» que no son un técnico: van al final, tras una línea. */
const ESPECIALES_TECNICO: readonly string[] = [SIN_ASIGNAR, CON_DESCUADRE]

/** Añade «Quién lo tiene» a cada móvil. `ahora`: milisegundos de la carga (`dataUpdatedAt`), para el límite del descuadre. */
export function preparar(moviles: MovilStock[], ahora: number): Movil[] {
  return moviles.map((m) => ({ ...m, quien: quienLoTiene(m, ahora) }))
}

/** El modelo de la columna lateral («iPhone 13 Pro»): el código del ERP traducido o, si no, el del maestro. */
export const modeloDe = (m: MovilStock): string => nombreModeloNsys(m.modelo, m.modeloMaestro)
/** La capacidad de un móvil («128GB»), o una raya si NSYS no la sabe. */
export const capacidadDe = (m: MovilStock): string => m.capacidad || SIN_DATO

/** «128GB» → «128 GB» y «1TB» → «1 TB»: pestañas y columna «Cap.». Lo que no entiende, tal cual. */
export function textoCapacidad(c: string): string {
  return c.replace(/^(\d+(?:[.,]\d+)?)\s*(GB|TB)$/i, '$1 $2')
}

/** «128GB» → «128»; «1TB» se queda: las pastillas de la columna lateral. */
export function capacidadCorta(c: string): string {
  return c.replace(/\s*GB$/i, '')
}

/** Los valores de un móvil en una dimensión: uno, salvo en «Técnico», donde puede tener varios (reparación de uno y glass
 *  de otro) o ninguno. */
export function valores(dim: Dimension, m: Movil): string[] {
  switch (dim) {
    case 'color': return [m.color || SIN_DATO]
    case 'grado': return [m.grado || SIN_DATO]
    case 'bateria': return [m.bateriaNueva ? BATERIA_NUEVA : BATERIA_ORIGINAL]
    case 'ubicacion': return [m.ubicacion ?? SIN_DATO]
    case 'tecnico': return m.quien.etiquetas
  }
}

/** Solo el stock vendible cuenta: lo que está de pedido (vendido y sin liberar en NSYS) y las cajas que no cuentan, no. */
export const cuentaComoStock = (m: MovilStock): boolean => m.estado === 'STOCK'

/** ¿Pasa los filtros? Entre filtros, Y; dentro de uno, O: basta con que coincida uno de los valores del móvil. `salvo`: las
 *  dimensiones que no se aplican (para contar las opciones de esa misma dimensión). */
export function pasa(m: Movil, f: Filtros, salvo: readonly Dimension[] = []): boolean {
  return (Object.keys(f) as Dimension[]).every((d) => salvo.includes(d) || f[d].size === 0 || valores(d, m).some((v) => f[d].has(v)))
}

export const esEspecialTecnico = (v: string): boolean => ESPECIALES_TECNICO.includes(v)

const indiceGrado = (g: string) => (ORDEN_GRADOS.includes(g) ? ORDEN_GRADOS.indexOf(g) : ORDEN_GRADOS.length)
export const compararGrados = (a: string, b: string): number => indiceGrado(a) - indiceGrado(b) || a.localeCompare(b, 'es')

function orden(dim: Dimension): (a: string, b: string) => number {
  if (dim === 'grado') return compararGrados
  // Los técnicos por nombre; «Sin asignar» y «Con descuadre», al final y en ese orden.
  if (dim === 'tecnico') return (a, b) => ESPECIALES_TECNICO.indexOf(a) - ESPECIALES_TECNICO.indexOf(b) || a.localeCompare(b, 'es')
  return (a, b) => a.localeCompare(b, 'es')
}

/** Las opciones de una dimensión y cuántos móviles quedarían con cada una, con los demás filtros puestos. */
export function opciones(moviles: Movil[], dim: Dimension, f: Filtros): Opcion[] {
  const cuentas = new Map<string, number>()
  for (const m of moviles) {
    const pasaLosDemas = pasa(m, f, [dim])
    for (const v of valores(dim, m)) cuentas.set(v, (cuentas.get(v) ?? 0) + (pasaLosDemas ? 1 : 0))
  }
  return [...cuentas.keys()].sort(orden(dim)).map((v) => ({ valor: v, cuenta: cuentas.get(v) ?? 0 }))
}

/** Reparto listos / taller / revisión del stock; lo que no cuenta, fuera. */
export function reparto(moviles: MovilStock[]): Reparto {
  const r: Reparto = { listos: 0, taller: 0, revision: 0 }
  for (const m of moviles) {
    if (!cuentaComoStock(m)) continue
    const u = m.ubicacion ?? ''
    if (u === 'Listos') r.listos++
    else if (u.startsWith('Taller')) r.taller++
    else if (u === 'En revisión' || u === 'Devolución') r.revision++
  }
  return r
}

/** Capacidad en GB («64GB» → 64, «1TB» → 1024); null si no se entiende. */
export function capacidadGb(capacidad: string | null | undefined): number | null {
  const r = /^\s*(\d+(?:[.,]\d+)?)\s*(GB|TB)\s*$/i.exec(capacidad ?? '')
  if (!r) return null
  return Number(r[1].replace(',', '.')) * (r[2].toUpperCase() === 'TB' ? 1024 : 1)
}

function compararCapacidades(a: string, b: string): number {
  const ga = capacidadGb(a)
  const gb = capacidadGb(b)
  if (ga !== gb) return ga === null ? 1 : gb === null ? -1 : ga - gb
  return a.localeCompare(b, 'es', { numeric: true })
}

/** Las capacidades de unos móviles, de menor a mayor (las que no se entienden, al final), con cuántas hay en stock. */
export function capacidades(moviles: MovilStock[]): Capacidad[] {
  const mapa = new Map<string, number>()
  for (const m of moviles) mapa.set(capacidadDe(m), (mapa.get(capacidadDe(m)) ?? 0) + (cuentaComoStock(m) ? 1 : 0))
  return [...mapa.keys()].sort(compararCapacidades).map((c) => ({ capacidad: c, stock: mapa.get(c) ?? 0 }))
}

/** Rango del modelo: 0 con modelo del catálogo, 1 solo con modelo del maestro, 2 sin modelo. */
function rango(m: MovilStock): number {
  if (m.modelo) return 0
  return m.modeloMaestro ? 1 : 2
}

/** La columna lateral: una fila por modelo con algo que enseñar (con el interruptor apagado, con stock), en el orden de la
 *  tienda: catálogo del más nuevo al más antiguo, después los que solo tienen modelo del maestro y al final los sin
 *  modelo. El total y las capacidades cuentan solo el stock, también con el interruptor encendido. */
export function modelosLista(moviles: Movil[], verNoCuenta: boolean): ModeloLista[] {
  const mapa = new Map<string, Movil[]>()
  for (const m of moviles) {
    if (!verNoCuenta && !cuentaComoStock(m)) continue
    const clave = modeloDe(m)
    const lista = mapa.get(clave)
    if (lista) lista.push(m)
    else mapa.set(clave, [m])
  }
  const indice = (m: MovilStock) => MODELOS_ORDENADOS.indexOf(m.modelo ?? '')
  return [...mapa.entries()]
    .map(([clave, lista]) => ({ clave, total: lista.filter(cuentaComoStock).length, capacidades: capacidades(lista), reparto: reparto(lista), moviles: lista }))
    .sort((a, b) => {
      const ma = a.moviles[0]
      const mb = b.moviles[0]
      const porRango = rango(ma) - rango(mb)
      if (porRango) return porRango
      if (rango(ma) === 0) return indice(mb) - indice(ma)
      return b.clave.localeCompare(a.clave, 'es', { numeric: true })
    })
}

/** Las pestañas de la cabecera del modelo: «Todas» y una por capacidad que el espejo tiene del modelo, con su número de
 *  stock. La web no tiene un catálogo de capacidades: salen de todos los móviles del modelo que llegan al stock, cuenten o
 *  no. Sin stock, apagada; con el interruptor encendido se puede abrir si tiene algo que no cuenta. */
export function pestanasCapacidad(todos: Movil[], verNoCuenta: boolean): Pestana[] {
  const stock = todos.filter(cuentaComoStock).length
  return [
    { capacidad: null, stock, activa: stock > 0 || (verNoCuenta && todos.length > 0) },
    ...capacidades(todos).map((c) => ({ capacidad: c.capacidad, stock: c.stock, activa: c.stock > 0 || verNoCuenta })),
  ]
}

export function contarFiltros(f: Filtros): number {
  return Object.values(f).reduce((n, s) => n + s.size, 0)
}

/** Orden de la tabla (spec 0.9.18 §4.6): primero lo que cuenta; dentro de eso, los descuadres y los «en caja, sin
 *  asignar»; luego por días, de menos a más (sin días, al final). */
export function ordenarTabla(moviles: Movil[]): Movil[] {
  const aviso = (m: Movil) => (m.quien.descuadre ? 0 : m.quien.enCaja ? 1 : 2)
  const dias = (m: Movil) => m.dias ?? Number.MAX_SAFE_INTEGER
  return [...moviles].sort((a, b) => Number(cuentaComoStock(b)) - Number(cuentaComoStock(a)) || aviso(a) - aviso(b) || dias(a) - dias(b))
}

/** Lo que se dice de un móvil encontrado por el buscador de IMEI. */
export function textoBuscado(m: MovilBuscado): string {
  if (m.estado === 'VENDIDO') return m.fechaVenta ? `Vendido el ${diaNsys(m.fechaVenta)}` : 'Vendido'
  if (m.estado === 'BAJA') return 'Baja en NSYS'
  if (m.estado === 'DE_PEDIDO') return `De pedido · ${m.ubicacion ?? SIN_DATO}`
  if (m.estado === 'NO_CUENTA') return `${m.ubicacion ?? SIN_DATO} · no cuenta como stock`
  return m.ubicacion ?? SIN_DATO
}

/** Muestra de color de la tabla (las del prototipo, en español y en inglés). Las que no están, en gris. */
const MUESTRAS: Record<string, string> = {
  azul: '#3b6fd1', blue: '#3b6fd1', medianoche: '#1d2533', midnight: '#1d2533', blanco: '#f4f4f4', white: '#f4f4f4',
  'blanco estelar': '#f4f1ea', starlight: '#f4f1ea', rosa: '#f3b6c7', pink: '#f3b6c7', morado: '#b9a6dc', purple: '#b9a6dc',
  verde: '#9cc9a6', green: '#9cc9a6', rojo: '#c8323a', red: '#c8323a', negro: '#222222', black: '#222222',
  lavanda: '#cdbfe8', lavender: '#cdbfe8', amarillo: '#f2d46b', yellow: '#f2d46b', plata: '#d8d8d8', silver: '#d8d8d8',
  oro: '#e6cfa1', gold: '#e6cfa1', grafito: '#4a4a4c', graphite: '#4a4a4c', 'gris espacial': '#5b5d61', 'space gray': '#5b5d61',
}

export function muestraColor(color: string | null | undefined): string {
  return (color && MUESTRAS[color.trim().toLowerCase()]) || '#cccccc'
}
```

Sustituye `src/modules/telefonos/stock/estado.ts` entero por:

```ts
import { crearStore } from '@/shared/lib/store'
import { FILTROS_VACIOS, type Filtros } from './modelo'

/** Lo que el usuario ha elegido en «Stock de teléfonos»: el modelo y su pestaña de capacidad, los filtros de esa pestaña,
 *  el interruptor de lo que no cuenta y el texto del buscador de la columna. Vive en un store de módulo (no en `useState`)
 *  para que «← Volver» desde el detalle de un móvil lo deje todo donde estaba (spec 0.9.18 §4.10); `reiniciarStores` lo
 *  limpia al cerrar sesión. Los filtros llevan la clave de su modelo y su pestaña: si cambian, valen como vacíos. */
export type EstadoStock = {
  verNoCuenta: boolean
  textoModelo: string
  elegido: string | null
  /** La pestaña de capacidad del modelo elegido; null es «Todas». */
  capacidad: string | null
  filtrosDe: { clave: string | null; filtros: Filtros }
}

export const estadoStockTelefonos = crearStore<EstadoStock>({
  verNoCuenta: false, textoModelo: '', elegido: null, capacidad: null, filtrosDe: { clave: null, filtros: FILTROS_VACIOS },
})

/** La clave de los filtros: el modelo y su pestaña («Todas» = cadena vacía). */
export const claveFiltros = (modelo: string, capacidad: string | null): string => `${modelo}|${capacidad ?? ''}`
```

Run: `npx vitest run src/modules/telefonos/stock/modelo.test.ts`
Expected: PASS (la pantalla todavía no compila; se arregla en el paso 4).

- [x] **Step 3: Tests de la pantalla (fallan)**

Sustituye `src/modules/telefonos/stock/StockTelefonosPage.test.tsx` entero por:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route, useLocation } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { estadoNsys, movil, movilBuscado, trabajo } from '../test/fabrica'
import { StockTelefonosPage } from './StockTelefonosPage'

// iPhone 13: 128GB con 001 (Listos) y 003 (En revisión), 256GB con 006 y 512GB solo de pedido (007); además 002 de pedido
// y 004 que no cuenta. iPhone 14: 005. En stock, 4.
const STOCK = [
  movil({ imeiSn: '350000000000001', color: 'Azul', grado: 'A', ubicacion: 'Listos', sku: 'i13128AzulA', dias: 2 }),
  movil({ imeiSn: '350000000000002', color: 'Azul', grado: 'A-', bateriaNueva: true, ubicacion: 'Taller · Glass', estado: 'DE_PEDIDO', sku: 'i13128AzulA-100', dias: 30 }),
  movil({ imeiSn: '350000000000003', color: 'Rosa', grado: 'n/a', ubicacion: 'En revisión', sku: null, dias: 4 }),
  movil({ imeiSn: '350000000000004', color: 'Azul', grado: 'B', ubicacion: 'Esperando pieza', estado: 'NO_CUENTA', sku: 'i13128AzulB', dias: 9 }),
  movil({ imeiSn: '350000000000005', modelo: '14', modeloMaestro: '14', ubicacion: 'Listos', sku: 'i14128AzulA', dias: 3 }),
  movil({ imeiSn: '350000000000006', capacidad: '256GB', color: 'Rosa', grado: 'B', ubicacion: 'Listos', sku: 'i13256RosaB', dias: 1 }),
  movil({ imeiSn: '350000000000007', capacidad: '512GB', ubicacion: 'Listos', estado: 'DE_PEDIDO', sku: 'i13512AzulA', dias: 50 }),
]

beforeEach(() => {
  server.use(
    http.get('*/api/stock-telefonos', () => HttpResponse.json(STOCK)),
    http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
  )
})

function DetalleFalso() {
  const desde = (useLocation().state as { desde?: string } | null)?.desde
  return <p>Detalle del IMEI{desde ? ` desde ${desde}` : ''}</p>
}

const abrir = () => renderConProviders(<StockTelefonosPage />, {
  sesion: SESION_SUPER, ruta: '/telefonos/stock',
  rutas: <Route path="/reparaciones/imeis/:imei" element={<DetalleFalso />} />,
})
const filas = () => screen.getAllByRole('row').slice(1)
const imeiDe = (fila: HTMLElement) => fila.textContent?.match(/3500000000000\d\d/)?.[0]
const lista = () => screen.getByRole('navigation', { name: 'Modelos' })
const modelosDeLaLista = () => within(lista()).getAllByRole('button', { name: / en stock$/ }).map((b) => b.getAttribute('aria-label'))
/** La celda de una fila bajo la cabecera `columna` de su misma tabla. */
const celda = (fila: HTMLElement, columna: string) => {
  const cabeceras = within(fila.closest('table') as HTMLElement).getAllByRole('columnheader')
  return within(fila).getAllByRole('cell')[cabeceras.findIndex((c) => c.textContent === columna)]
}
const colorAzul = () => within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Azul/ })
const interruptor = () => screen.getByRole('switch', { name: 'Ver también lo que no cuenta como stock' })

describe('Stock de teléfonos (spec pieza 1 §9.2 y spec 0.9.18 §4)', () => {
  it('cabecera con el total en stock, la píldora de NSYS y una fila por modelo con su total y sus capacidades', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Stock de teléfonos' })).toBeInTheDocument()
    expect(await screen.findByText('4 móviles en stock')).toBeInTheDocument()
    expect(screen.getByText(/^NSYS: /)).toHaveAttribute('role', 'status')
    expect(modelosDeLaLista()).toEqual(['iPhone 14: 1 en stock', 'iPhone 13: 3 en stock'])
    expect(within(lista()).getByRole('button', { name: 'iPhone 13 128GB: 2' })).toBeInTheDocument()
    expect(within(lista()).getByRole('button', { name: 'iPhone 13 256GB: 1' })).toBeInTheDocument()
    expect(within(lista()).queryByRole('button', { name: /^iPhone 13 512GB/ })).not.toBeInTheDocument()
  })

  it('el nombre abre el modelo con «Todas» y la columna Cap.; una pastilla, con esa capacidad', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    expect(screen.getByRole('heading', { level: 2, name: 'iPhone 13' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Todas: 3' })).toHaveAttribute('aria-pressed', 'true')
    expect(filas().map(imeiDe)).toEqual(['350000000000006', '350000000000001', '350000000000003'])
    expect(screen.getByRole('columnheader', { name: 'Cap.' })).toBeInTheDocument()
    expect(celda(filas()[0], 'Cap.')).toHaveTextContent('256 GB')
    expect(screen.getByText('3 móviles')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'iPhone 13 128GB: 2' }))
    expect(screen.getByRole('button', { name: '128 GB: 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'iPhone 13 128GB: 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(filas().map(imeiDe)).toEqual(['350000000000001', '350000000000003'])
    expect(screen.queryByRole('columnheader', { name: 'Cap.' })).not.toBeInTheDocument()
  })

  it('las pestañas sin stock van apagadas, con el interruptor se abren y cambiar de pestaña quita los filtros', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    expect(screen.getByRole('button', { name: '512 GB: 0' })).toBeDisabled()
    await user.click(colorAzul())
    expect(screen.getByRole('button', { name: 'Quitar filtros (1)' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '128 GB: 2' }))
    expect(screen.queryByRole('button', { name: /Quitar filtros/ })).not.toBeInTheDocument()
    await user.click(interruptor())
    await user.click(screen.getByRole('button', { name: '512 GB: 0' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000007'])
  })

  it('al elegir un modelo salen sus móviles y los filtros de un clic se combinan', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13 128GB: 2' }))
    expect(filas()).toHaveLength(2)
    await user.click(colorAzul())
    expect(filas()).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Ubicación' }))
    await user.click(screen.getByRole('checkbox', { name: 'Listos (1)' }))
    expect(filas()).toHaveLength(1)
    expect(within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Rosa/ })).toBeDisabled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Quitar filtros (2)' }))
    expect(filas()).toHaveLength(2)
  })

  it('el interruptor enseña lo que no cuenta como stock, en gris y fuera de los totales', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    await user.click(interruptor())
    expect(filas().map(imeiDe)).toEqual(['350000000000006', '350000000000001', '350000000000003', '350000000000004', '350000000000002', '350000000000007'])
    expect(screen.getAllByText('NO CUENTA · De pedido')).toHaveLength(2)
    expect(screen.getAllByText('NO CUENTA · No cuenta como stock')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'iPhone 13: 3 en stock' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Todas: 3' })).toBeInTheDocument()
    expect(screen.getByText('4 móviles en stock')).toBeInTheDocument()
  })

  it('lo de pedido se oculta sin el interruptor y con él sale en gris, sin borde rojo', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13 128GB: 2' }))
    expect(screen.queryByText('350000000000002')).not.toBeInTheDocument()
    await user.click(interruptor())
    const fila = filas().find((f) => imeiDe(f) === '350000000000002')!
    expect(fila).toHaveTextContent('NO CUENTA · De pedido')
    expect(fila).toHaveClass('text-texto-suave')
    expect(fila).not.toHaveClass('border-l-fila-urgente-brd')
  })

  it('el IMEI de una fila abre el detalle diciendo que viene del stock', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Abrir 350000000000005' }))
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/stock')).toBeInTheDocument()
  })

  it('el buscador de IMEI encuentra cualquier IMEI y abre su detalle', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      movilBuscado({ imeiSn: '353918700112233', modelo: '11', modeloMaestro: '11', capacidad: '64GB', color: 'Negro', estado: 'VENDIDO', ubicacion: null, fechaVenta: '2023-05-02' }),
    ])))
    abrir()
    await user.type(await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }), '3539')
    await user.click(await screen.findByRole('button', { name: /353918700112233 · iPhone 11 64GB Negro · Vendido el 02\/05\/2023/ }))
    expect(await screen.findByText(/^Detalle del IMEI/)).toBeInTheDocument()
  })

  it('el desplegable del buscador de IMEI se cierra al salir y vuelve al enfocar', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([movilBuscado({ imeiSn: '353918700112233' })])))
    abrir()
    const caja = await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' })
    await user.type(caja, '3539')
    expect(await screen.findByRole('list', { name: 'Móviles encontrados' })).toBeInTheDocument()
    await user.click(interruptor())
    expect(screen.queryByRole('list', { name: 'Móviles encontrados' })).not.toBeInTheDocument()
    await user.click(caja)
    expect(await screen.findByRole('list', { name: 'Móviles encontrados' })).toBeInTheDocument()
  })

  it('escribir en el buscador de la columna filtra la lista pero no cambia el modelo abierto', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    await user.type(screen.getByRole('textbox', { name: 'Buscar modelo o SKU' }), '14')
    expect(modelosDeLaLista()).toEqual(['iPhone 14: 1 en stock'])
    expect(screen.getByRole('heading', { level: 2, name: 'iPhone 13' })).toBeInTheDocument()
  })

  it('si tras recargar el modelo elegido ya no existe, cae al primero sin filtros', async () => {
    const user = userEvent.setup()
    const { queryClient } = abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    await user.click(colorAzul())
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json([STOCK[4]])))
    await queryClient.invalidateQueries()
    expect(await screen.findByRole('heading', { level: 2, name: 'iPhone 14' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Quitar filtros/ })).not.toBeInTheDocument()
    expect(filas()).toHaveLength(1)
  })

  it('volver a pulsar el modelo elegido no borra sus filtros', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    await user.click(colorAzul())
    await user.click(screen.getByRole('button', { name: 'iPhone 13: 3 en stock' }))
    expect(screen.getByRole('button', { name: 'Quitar filtros (1)' })).toBeInTheDocument()
  })

  it('sin datos de NSYS sale la banda roja', async () => {
    server.use(http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: null }))))
    abrir()
    expect(await screen.findByRole('alert')).toHaveTextContent('Todavía no ha llegado ningún dato de NSYS.')
  })

  it('la fila y el buscador también abren el detalle diciendo que vienen del stock', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([movilBuscado({ imeiSn: '353918700112233' })])))
    const { unmount } = abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 14: 1 en stock' }))
    await user.dblClick(filas()[0])
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/stock')).toBeInTheDocument()
    unmount()
    abrir()
    await user.type(await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }), '3539')
    await user.click(await screen.findByRole('button', { name: /353918700112233/ }))
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/stock')).toBeInTheDocument()
  })

  it('al volver de un móvil siguen el modelo, la capacidad, el filtro, el interruptor y el texto del buscador', async () => {
    const user = userEvent.setup()
    const primera = abrir()
    await user.click(await screen.findByRole('switch', { name: 'Ver también lo que no cuenta como stock' }))
    await user.click(await screen.findByRole('button', { name: 'iPhone 13 128GB: 2' }))
    await user.click(colorAzul())
    await user.type(screen.getByRole('textbox', { name: 'Buscar modelo o SKU' }), 'iPhone 1')
    await user.click(screen.getAllByRole('button', { name: /^Abrir / })[0])
    expect(await screen.findByText(/^Detalle del IMEI/)).toBeInTheDocument()
    primera.unmount()

    abrir()
    expect(await screen.findByRole('heading', { level: 2, name: 'iPhone 13' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '128 GB: 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Quitar filtros (1)' })).toBeInTheDocument()
    expect(interruptor()).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('textbox', { name: 'Buscar modelo o SKU' })).toHaveValue('iPhone 1')
    expect(filas()).toHaveLength(3)
  })

  it('mientras NSYS no contesta la píldora es neutra, no roja', async () => {
    server.use(http.get('*/api/stock-telefonos/estado', async () => { await new Promise(() => {}) }))
    abrir()
    const pildora = await screen.findByText('NSYS: comprobando…')
    expect(pildora).not.toHaveClass('bg-urgente-bg')
    expect(pildora).not.toHaveClass('text-fila-urgente-brd')
    expect(pildora.querySelector('span')).not.toHaveClass('bg-verde-ok')
    expect(pildora.querySelector('span')).toHaveClass('bg-gris-borde')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('si falla la consulta del estado la píldora lo dice sin banda roja', async () => {
    server.use(http.get('*/api/stock-telefonos/estado', () => new HttpResponse(null, { status: 500 })))
    abrir()
    const pildora = await screen.findByText('NSYS: no se pudo comprobar')
    expect(pildora).not.toHaveClass('bg-urgente-bg')
    expect(pildora).not.toHaveClass('text-fila-urgente-brd')
    expect(pildora.querySelector('span')).not.toHaveClass('bg-verde-ok')
    expect(pildora.querySelector('span')).toHaveClass('bg-gris-borde')
    expect(screen.queryByText(/Todavía no ha llegado ningún dato/)).not.toBeInTheDocument()
  })

  it('si la primera carga del stock falla no dice que hay 0 móviles', async () => {
    server.use(http.get('*/api/stock-telefonos', () => new HttpResponse(null, { status: 500 })))
    abrir()
    expect(await screen.findByText('No se pudo cargar el stock de teléfonos.')).toBeInTheDocument()
    expect(screen.queryByText('No hay móviles en stock.')).not.toBeInTheDocument()
    expect(screen.queryByText(/móviles? en stock$/)).not.toBeInTheDocument()
  })

  it('un grado vacío se ve como raya en la tabla', async () => {
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json([movil({ imeiSn: '350000000000009', grado: '' })])))
    abrir()
    expect(celda((await screen.findAllByRole('row'))[1], 'Grado')).toHaveTextContent('—')
  })

  it('los chips de color y grado se leen con un espacio entre el nombre y el número', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13 128GB: 2' }))
    expect(within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Azul \d+$/ })).toBeInTheDocument()
  })

  const CON_TRABAJOS = [
    movil({ imeiSn: '350000000000011', ubicacion: 'Taller · Reparación', dias: 3,
      trabajos: [trabajo({ tecnico: 'Técnico 1' }), trabajo({ categoria: 'GLASS', tecnico: 'Técnico 3' })] }),
    movil({ imeiSn: '350000000000012', ubicacion: 'Taller · Glass', dias: 5 }),
    movil({ imeiSn: '350000000000013', ubicacion: 'Listos', dias: 1, trabajos: [trabajo({ tecnico: 'Técnico 2', desde: '2026-01-12T09:00:00' })] }),
  ]

  it('«Quién lo tiene»: el descuadre y la caja sin asignar primero, con sus avisos junto a los filtros', async () => {
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json(CON_TRABAJOS)))
    abrir()
    expect(await screen.findByRole('columnheader', { name: 'Quién lo tiene' })).toBeInTheDocument()
    expect(filas().map(imeiDe)).toEqual(['350000000000013', '350000000000012', '350000000000011'])
    expect(celda(filas()[0], 'Quién lo tiene')).toHaveTextContent('⚠ Técnico 2: trabajo abierto')
    expect(celda(filas()[1], 'Quién lo tiene')).toHaveTextContent('⚠ En caja, sin asignar')
    expect(celda(filas()[2], 'Quién lo tiene')).toHaveTextContent('Técnico 1 · glass Técnico 3 sin entregar')
    expect(within(filas()[2]).getByText('Técnico 1').closest('[title]')).toHaveAttribute('title',
      'Reparación de Técnico 1 desde el 16/10 · Glass de Técnico 3 desde el 16/10: el móvil aún no le ha llegado')
    expect(screen.getByText('⚠ 1 en caja de taller sin asignar')).toBeInTheDocument()
    expect(screen.getByText('⚠ 1 con descuadre')).toBeInTheDocument()
  })

  it('el filtro Técnico: un técnico, los sin asignar y los descuadres; un móvil sale si coincide uno', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json(CON_TRABAJOS)))
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Técnico' }))
    await user.click(screen.getByRole('checkbox', { name: 'Técnico 3 (1)' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000011'])
    await user.click(screen.getByRole('checkbox', { name: 'Sin asignar (en caja de taller) (1)' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000012', '350000000000011'])
    await user.click(screen.getByRole('checkbox', { name: 'Con descuadre (1)' }))
    expect(filas()).toHaveLength(3)
    expect(screen.getByRole('button', { name: 'Técnico' })).toHaveTextContent('Técnico: 3')
  })

  it('el grado lleva su color y las marcas de batería nueva y eSIM, sin columnas Batería ni Estado', async () => {
    server.use(http.get('*/api/stock-telefonos', () => HttpResponse.json([
      movil({ imeiSn: '350000000000031', grado: 'MsB', bateriaNueva: true, esEsim: true }),
    ])))
    abrir()
    const grado = celda((await screen.findAllByRole('row'))[1], 'Grado')
    expect(within(grado).getByText('MsB')).toHaveClass('border-tipo-glass-text')
    expect(within(grado).getByText('100')).toHaveAttribute('title', 'Batería nueva')
    expect(within(grado).getByText('eSIM')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Batería' })).not.toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Estado' })).not.toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no hay pastillas ni pestañas; `tsc` falla en la pantalla).

- [x] **Step 4: Pantalla**

Crea `src/modules/telefonos/stock/BarraReparto.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import type { Reparto } from './modelo'

/** Barra listos / taller / revisión (verde, azul y ámbar, los colores del rastro). Cada trozo, a proporción. */
export function BarraReparto({ reparto, className }: { reparto: Reparto; className?: string }) {
  return (
    <span aria-hidden className={cn('flex overflow-hidden bg-pill-bg', className)}>
      <i className="block h-full bg-fila-recibido-brd" style={{ flexGrow: reparto.listos }} />
      <i className="block h-full bg-tipo-reparacion-text" style={{ flexGrow: reparto.taller }} />
      <i className="block h-full bg-ambar" style={{ flexGrow: reparto.revision }} />
    </span>
  )
}
```

Crea `src/modules/telefonos/stock/PestanasCapacidad.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { textoCapacidad, type Pestana } from './modelo'

type Props = { pestanas: Pestana[]; elegida: string | null; onElegir: (capacidad: string | null) => void }

/** Pestañas de capacidad a la derecha del nombre del modelo (spec 0.9.18 §4.3): «Todas» y una por capacidad, con su número
 *  de stock; sin nada que enseñar, apagadas. */
export function PestanasCapacidad({ pestanas, elegida, onElegir }: Props) {
  return (
    <div role="group" aria-label="Capacidad" className="flex overflow-hidden rounded-lg border border-borde-input">
      {pestanas.map((p) => {
        const texto = p.capacidad === null ? 'Todas' : textoCapacidad(p.capacidad)
        const on = p.capacidad === elegida
        return (
          <button key={p.capacidad ?? ''} type="button" aria-label={`${texto}: ${p.stock}`} aria-pressed={on} disabled={!p.activa}
            onClick={() => onElegir(p.capacidad)}
            className={cn('cursor-pointer border-r border-borde-input px-3 py-1 text-[12px] font-semibold whitespace-nowrap last:border-r-0',
              on ? 'bg-azul-noche text-superficie' : 'bg-superficie text-azul-medio hover:bg-seleccion-suave',
              'disabled:cursor-default disabled:text-texto-suave disabled:hover:bg-superficie')}>
            {texto}<span className={cn('ml-1', on ? 'text-[#c9d3e6]' : 'text-azul-gris')}>{p.stock}</span>
          </button>
        )
      })}
    </div>
  )
}
```

Sustituye `src/modules/telefonos/stock/ListaModelos.tsx` entero por:

```tsx
import { useEffect, useRef, type ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import { BarraReparto } from './BarraReparto'
import { capacidadCorta, type ModeloLista } from './modelo'

type Props = {
  modelos: ModeloLista[]
  elegido: string | null
  capacidad: string | null
  /** El buscador de la cabecera de la columna. */
  buscador: ReactNode
  onAbrir: (modelo: string, capacidad: string | null) => void
}

const LEYENDA: [string, string][] = [['bg-fila-recibido-brd', 'listos'], ['bg-tipo-reparacion-text', 'taller'], ['bg-ambar', 'revisión']]

/** La columna de la izquierda (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.1): una fila por modelo
 *  con su total en stock, una pastilla por capacidad con stock y la barra listos / taller / revisión en el borde de abajo.
 *  El nombre abre el modelo con «Todas»; una pastilla, con esa capacidad. La fila elegida se resalta y se desplaza a la vista. */
export function ListaModelos({ modelos, elegido, capacidad, buscador, onAbrir }: Props) {
  const filaElegida = useRef<HTMLLIElement>(null)
  useEffect(() => {
    filaElegida.current?.scrollIntoView({ block: 'nearest' })
  }, [elegido])
  return (
    <nav aria-label="Modelos" className="flex max-h-[calc(100vh-300px)] min-h-0 flex-col overflow-hidden rounded-md bg-superficie max-[1500px]:max-h-[calc(100vh-260px)]">
      <div className="border-b border-fila-sep p-2">{buscador}</div>
      <div aria-hidden className="flex gap-3 border-b border-fila-sep px-3 py-1.5 text-[10.5px] text-azul-gris">
        {LEYENDA.map(([color, texto]) => (
          <span key={texto} className="flex items-center gap-1"><i className={cn('h-2 w-2 rounded-sm', color)} />{texto}</span>
        ))}
      </div>
      <ul className="min-h-0 flex-1 overflow-y-auto">
        {modelos.length === 0 && <li className="p-4 text-[12px] text-azul-gris">Ningún modelo</li>}
        {modelos.map((g) => {
          const activo = g.clave === elegido
          return (
            <li key={g.clave} ref={activo ? filaElegida : undefined} className={cn('relative border-b border-fila-sep px-3 pt-1.5 pb-2.5', activo && 'bg-seleccion-suave')}>
              <button type="button" aria-label={`${g.clave}: ${g.total} en stock`} aria-pressed={activo && capacidad === null} onClick={() => onAbrir(g.clave, null)}
                className="flex w-full cursor-pointer items-baseline text-left">
                <span className="text-[13px] font-bold text-azul-medio">{g.clave}</span>
                <span className="ml-auto text-[15px] font-extrabold text-azul-noche">{g.total}</span>
              </button>
              <div className="mt-1 flex flex-wrap gap-1">
                {g.capacidades.filter((c) => c.stock > 0).map((c) => {
                  const on = activo && capacidad === c.capacidad
                  return (
                    <button key={c.capacidad} type="button" aria-label={`${g.clave} ${c.capacidad}: ${c.stock}`} aria-pressed={on} onClick={() => onAbrir(g.clave, c.capacidad)}
                      className={cn('group cursor-pointer rounded-md border px-1.5 py-px text-[11.5px] font-semibold whitespace-nowrap',
                        on ? 'border-azul-noche bg-azul-noche text-superficie' : 'border-pill-borde bg-pill-bg text-azul-medio hover:border-azul-noche hover:bg-azul-noche hover:text-superficie')}>
                      {capacidadCorta(c.capacidad)}
                      <span className={cn('ml-1', on ? 'text-[#c9d3e6]' : 'text-azul-gris group-hover:text-[#c9d3e6]')}>{c.stock}</span>
                    </button>
                  )
                })}
              </div>
              <BarraReparto reparto={g.reparto} className="absolute inset-x-0 bottom-0 h-1" />
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
```

Sustituye `src/modules/telefonos/stock/columnas.tsx` entero por:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import { clasesUbicacion, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { CeldaQuien } from './CeldaQuien'
import { capacidadDe, muestraColor, textoCapacidad, type Movil } from './modelo'
import { PildoraGrado } from './PildoraGrado'

const MARCA = 'ml-1 inline-block rounded border px-1 align-[1px] text-[10px] font-bold'

/** `conCapacidad`: con la pestaña «Todas», la columna «Cap.» detrás del IMEI (spec §4.3). */
export type OpcionesColumnas = { conCapacidad?: boolean }

/** Columnas de la tabla de móviles (0.9.18, spec §4.6): IMEI · (Cap.) · Color · Grado (con «100» si la batería es nueva y
 *  «eSIM») · SKU · Ubicación (con «NO CUENTA · …» si no cuenta como stock) · Quién lo tiene · Días. El IMEI es un botón
 *  que abre el detalle. */
export function columnasMoviles(abrir: (imeiSn: string) => void, { conCapacidad = false }: OpcionesColumnas = {}): ColumnDef<Movil>[] {
  const imei: ColumnDef<Movil> = {
    id: 'imei', header: 'IMEI', size: 150, maxSize: 160,
    cell: ({ row }) => (
      <button type="button" aria-label={`Abrir ${row.original.imeiSn}`} onClick={(e) => { e.stopPropagation(); abrir(row.original.imeiSn) }}
        className="cursor-pointer font-mono text-[11.5px] text-azul-noche hover:underline">{row.original.imeiSn}</button>
    ),
  }
  const capacidad: ColumnDef<Movil> = { id: 'capacidad', header: 'Cap.', size: 70, maxSize: 80, accessorFn: (m) => textoCapacidad(capacidadDe(m)) }
  return [
    imei,
    ...(conCapacidad ? [capacidad] : []),
    {
      id: 'color', header: 'Color', size: 110,
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="h-2.5 w-2.5 rounded-full border border-pill-borde" style={{ background: muestraColor(row.original.color) }} />
          {row.original.color ?? '—'}
        </span>
      ),
    },
    {
      id: 'grado', header: 'Grado', size: 110, maxSize: 120,
      cell: ({ row }) => (
        <>
          <PildoraGrado grado={row.original.grado} />
          {row.original.bateriaNueva && <span title="Batería nueva" className={cn(MARCA, 'border-fila-recibido-brd text-fila-recibido-brd')}>100</span>}
          {row.original.esEsim && <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>eSIM</span>}
        </>
      ),
    },
    { id: 'sku', header: 'SKU', size: 150, cell: ({ row }) => <span className="font-mono text-[11.5px]">{row.original.sku ?? '—'}</span> },
    {
      id: 'ubicacion', header: 'Ubicación', size: 190,
      cell: ({ row }) => (
        <>
          <span className={cn(CLASES_PILDORA, clasesUbicacion(row.original.ubicacion))}>{row.original.ubicacion ?? '—'}</span>
          {(row.original.estado === 'NO_CUENTA' || row.original.estado === 'DE_PEDIDO') && (
            <span className={cn(MARCA, 'border-pill-borde text-azul-gris')}>{`NO CUENTA · ${textoEstadoMovil(row.original.estado)}`}</span>
          )}
        </>
      ),
    },
    { id: 'quien', header: 'Quién lo tiene', size: 210, cell: ({ row }) => <CeldaQuien quien={row.original.quien} /> },
    { id: 'dias', header: 'Días', size: 55, maxSize: 60, accessorFn: (m) => m.dias ?? '' },
  ]
}
```

Sustituye `src/modules/telefonos/stock/StockTelefonosPage.tsx` entero por:

```tsx
import { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { DataTable } from '@/shared/ui/DataTable'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useStockTelefonos } from '../api'
import { BuscadorImei } from './BuscadorImei'
import { columnasMoviles } from './columnas'
import { claveFiltros, estadoStockTelefonos } from './estado'
import { FiltrosMoviles } from './FiltrosMoviles'
import { ListaModelos } from './ListaModelos'
import { capacidadDe, cuentaComoStock, FILTROS_VACIOS, modeloDe, modelosLista, ordenarTabla, pasa, pestanasCapacidad, preparar, type Filtros } from './modelo'
import { PestanasCapacidad } from './PestanasCapacidad'
import { BandaNsys, PildoraNsys } from './PildoraNsys'

const PIE = '"Quién lo tiene" sale de las asignaciones abiertas del ERP, al momento; la caja, de la última exportación de NSYS. Pasa el ratón por encima para ver desde cuándo. El IMEI abre el detalle del móvil.'

/** «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; 0.9.18, spec «Columna compacta, SKU, Quién lo
 *  tiene y Revisado» §4): una fila por modelo a la izquierda y, a la derecha, el modelo con sus pestañas de capacidad, sus
 *  filtros y sus móviles. Solo lectura: los datos vienen del espejo de NSYS y de las asignaciones abiertas del ERP. Un
 *  móvil abre su detalle de IMEIs, con la ficha de NSYS y el rastro. */
export function StockTelefonosPage() {
  const navigate = useNavigate()
  const stock = useStockTelefonos()
  // «Quién lo tiene» se calcula a la hora de la carga (dataUpdatedAt): el sondeo de 60 s lo vuelve a calcular.
  const moviles = useMemo(() => preparar(stock.data ?? [], stock.dataUpdatedAt), [stock.data, stock.dataUpdatedAt])
  const sinCargar = stock.isError && !stock.data
  const [{ verNoCuenta, textoModelo, elegido, capacidad: capacidadElegida, filtrosDe }, setEstado] = useStore(estadoStockTelefonos)
  const lista = useMemo(() => modelosLista(moviles, verNoCuenta), [moviles, verNoCuenta])
  const texto = textoModelo.trim().toLowerCase()
  const visibles = lista.filter((g) => g.clave.toLowerCase().includes(texto))
  // El modelo abierto no depende de lo escrito en el buscador: solo cambia al elegir otro (spec §4.2).
  const modelo = lista.find((g) => g.clave === elegido) ?? lista[0] ?? null
  const todos = useMemo(() => (modelo ? moviles.filter((m) => modeloDe(m) === modelo.clave) : []), [moviles, modelo])
  const pestanas = pestanasCapacidad(todos, verNoCuenta)
  const capacidad = modelo !== null && modelo.clave === elegido && pestanas.some((p) => p.capacidad !== null && p.capacidad === capacidadElegida && p.activa)
    ? capacidadElegida : null
  const clave = modelo ? claveFiltros(modelo.clave, capacidad) : null
  const filtros = filtrosDe.clave === clave ? filtrosDe.filtros : FILTROS_VACIOS
  const setFiltros = (f: Filtros) => setEstado((e) => ({ ...e, filtrosDe: { clave, filtros: f } }))
  const abrirModelo = (m: string, c: string | null) => {
    // Volver a pulsar lo que ya está abierto no borra sus filtros.
    if (m === modelo?.clave && c === capacidad) return
    setEstado((e) => ({ ...e, elegido: m, capacidad: c, filtrosDe: { clave: claveFiltros(m, c), filtros: FILTROS_VACIOS } }))
  }
  const delModelo = useMemo(() => (modelo?.moviles ?? []).filter((m) => capacidad === null || capacidadDe(m) === capacidad), [modelo, capacidad])
  const filas = useMemo(() => ordenarTabla(delModelo.filter((m) => pasa(m, filtros))), [delModelo, filtros])
  const enStock = moviles.filter(cuentaComoStock).length
  const abrir = useCallback((imeiSn: string) => navigate(`/reparaciones/imeis/${encodeURIComponent(imeiSn)}`, { state: { desde: '/telefonos/stock' } }), [navigate])
  const columnas = useMemo(() => columnasMoviles(abrir, { conCapacidad: capacidad === null }), [abrir, capacidad])
  return (
    <div className="p-10 max-[1500px]:p-5">
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Stock de teléfonos</h1>
        {!sinCargar && <PildoraContador texto={`${enStock} ${enStock === 1 ? 'móvil' : 'móviles'} en stock`} />}
        <PildoraNsys />
      </div>
      <BandaNsys />
      <div className="mb-3 flex flex-wrap items-center gap-4">
        <BuscadorImei onElegir={abrir} />
        <button type="button" role="switch" aria-checked={verNoCuenta} onClick={() => setEstado((e) => ({ ...e, verNoCuenta: !e.verNoCuenta }))}
          className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-azul-medio">
          <span className={cn('relative h-[18px] w-[34px] rounded-full transition-colors', verNoCuenta ? 'bg-verde-ok' : 'bg-pill-borde')}>
            <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-superficie transition-[left]', verNoCuenta ? 'left-[18px]' : 'left-[2px]')} />
          </span>
          Ver también lo que no cuenta como stock
        </button>
      </div>
      <div className="grid grid-cols-[270px_1fr] items-start gap-3 max-[1500px]:grid-cols-[230px_1fr]">
        <ListaModelos modelos={visibles} elegido={modelo?.clave ?? null} capacidad={capacidad} onAbrir={abrirModelo}
          buscador={(
            <input aria-label="Buscar modelo o SKU" placeholder="Buscar modelo o SKU…" value={textoModelo} autoComplete="off"
              onChange={(e) => setEstado((s) => ({ ...s, textoModelo: e.target.value }))}
              className="h-8 w-full rounded-md border border-borde-input px-2.5 text-[12.5px]" />
          )} />
        <section aria-label="Móviles del modelo" className="min-w-0 rounded-md bg-superficie p-3">
          {modelo && (
            <>
              <div className="mb-2 flex flex-wrap items-center gap-2.5">
                <h2 className="text-base font-bold text-azul-noche">{modelo.clave}</h2>
                <PestanasCapacidad pestanas={pestanas} elegida={capacidad} onElegir={(c) => abrirModelo(modelo.clave, c)} />
                <PildoraContador texto={`${filas.length} ${filas.length === 1 ? 'móvil' : 'móviles'}`} />
              </div>
              <FiltrosMoviles moviles={delModelo} filtros={filtros} onCambiar={setFiltros} />
              <DataTable columns={columnas} data={filas} vacio="Ningún móvil con estos filtros" ajuste="fluido" alturaMax="calc(100vh - 430px)"
                getRowId={(m) => `${m.imeiSn}|${m.lote}`} onAbrir={(m) => abrir(m.imeiSn)}
                filaClase={(m) => cn('border-l-4', 'border-l-transparent', !cuentaComoStock(m) && 'text-texto-suave')} />
              <p className="mt-2 text-[11.5px] text-azul-gris">{PIE}</p>
            </>
          )}
          {!modelo && sinCargar && <p className="p-4 text-[12px] text-azul-gris">No se pudo cargar el stock de teléfonos.</p>}
          {!modelo && !sinCargar && stock.data && <p className="p-4 text-[12px] text-azul-gris">No hay móviles en stock.</p>}
        </section>
      </div>
    </div>
  )
}
```

Comprueba que nada más usa lo que se quita: `grep -rn "agrupar\|grupoDe\|totales(" src --include=*.ts --include=*.tsx` no da nada fuera de `modelo.ts`.

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 6: Commit**

```bash
git add src/modules/telefonos
git commit -m "feat: columna compacta por modelo y pestanas de capacidad en el stock de telefonos (0.9.18)"
```

---

### Task 8: Web — buscador de modelo o SKU y filtro de SKU

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/buscador.ts`, `buscador.test.ts`, `BuscadorModelo.tsx`, `PastillaSku.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.ts` (`FiltroSku`, `normalizar`, `pasaSku`, `partesSku`), `modelo.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/estado.ts`, `ListaModelos.tsx`, `columnas.tsx`, `StockTelefonosPage.tsx` (entero)
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`

**Interfaces:**
- Consumes: `ModeloLista`, `modelosLista`, `modeloDe`, `capacidadDe`, `textoCapacidad`, `claveFiltros` y `cuentaComoStock` (Task 7).
- Produces:
  - `modelo.ts`:
    - `type FiltroSku = { texto: string; exacto: boolean }`;
    - `normalizar(texto)` (sin mayúsculas ni espacios);
    - `pasaSku(m, sku: FiltroSku | null)`;
    - `partesSku(sku, filtro) → [resaltado, resto] | null`.
  - `buscador.ts`:
    - `MAX_SUGERENCIAS = 7`;
    - tipos `SugerenciaSku = { sku; modelo; capacidad; cuenta }`,
      `Destino = { modelo: string; capacidad: string | null; sku: FiltroSku | null }` y
      `Resultado = { destino: Destino } | { aviso: string }`;
    - `sugerenciasSku(moviles, texto)`, `filtrarModelos(lista, texto, sugerencias)`, `modeloExacto(lista, texto)`,
      `aplicarSku(moviles, texto, exacto)` y `resolverEnter(lista, moviles, texto)`.
  - `estado.ts`: `filtrosDe` añade `sku: FiltroSku | null`.
  - `BuscadorModelo({ lista, moviles, texto, sugerencias, onTexto, onAbrir(destino) })`, `PastillaSku({ sku, onQuitar })`
    (`data-testid="pastilla-sku"`).
  - `columnasMoviles(abrir, { conCapacidad, sku })`: con `sku`, la parte buscada va en `<mark>` en la columna SKU.

- [x] **Step 1: Tests de las reglas (fallan)**

Crea `src/modules/telefonos/stock/buscador.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { visto } from '../test/fabrica'
import { aplicarSku, filtrarModelos, modeloExacto, resolverEnter, sugerenciasSku } from './buscador'
import { modelosLista } from './modelo'

/** Un iPhone 13 Pro en stock (por defecto). */
const pro = (p: Parameters<typeof visto>[0]) => visto({ modelo: '13pro', modeloMaestro: '13 PRO', ...p })
const MOVILES = [
  pro({ imeiSn: '350000000000001', sku: 'i13PRO128GrafitoA' }),
  pro({ imeiSn: '350000000000002', sku: 'i13PRO128GrafitoA' }),
  pro({ imeiSn: '350000000000003', sku: 'i13PRO128GrafitoA100' }),
  pro({ imeiSn: '350000000000004', sku: 'i13PRO256OroB', capacidad: '256GB' }),
  pro({ imeiSn: '350000000000005', sku: 'i13PRO128PlataA', estado: 'DE_PEDIDO' }),
  visto({ imeiSn: '350000000000006', sku: 'i14128AzulA', modelo: '14', modeloMaestro: '14' }),
  pro({ imeiSn: '350000000000007', sku: null }),
]
const LISTA = modelosLista(MOVILES, false)

describe('buscador de modelo o SKU (spec 0.9.18 §4.2)', () => {
  it('los SKU en stock que empiezan así: los más cortos primero, luego los que más hay; a partir de 2 caracteres', () => {
    expect(sugerenciasSku(MOVILES, 'i')).toEqual([])
    expect(sugerenciasSku(MOVILES, 'I13 pro')).toEqual([
      { sku: 'i13PRO256OroB', modelo: 'iPhone 13 Pro', capacidad: '256GB', cuenta: 1 },
      { sku: 'i13PRO128GrafitoA', modelo: 'iPhone 13 Pro', capacidad: '128GB', cuenta: 2 },
      { sku: 'i13PRO128GrafitoA100', modelo: 'iPhone 13 Pro', capacidad: '128GB', cuenta: 1 },
    ])
    expect(sugerenciasSku(MOVILES, 'i13PRO128Plata'), 'el de pedido no es stock').toEqual([])
  })

  it('la lista de modelos: los que lo llevan en el nombre y los que tienen esos SKU', () => {
    expect(filtrarModelos(LISTA, '13 pro', []).map((g) => g.clave)).toEqual(['iPhone 13 Pro'])
    expect(filtrarModelos(LISTA, 'i14', sugerenciasSku(MOVILES, 'i14')).map((g) => g.clave)).toEqual(['iPhone 14'])
    expect(filtrarModelos(LISTA, '', [])).toHaveLength(2)
  })

  it('el modelo exacto, con «iPhone» o sin él', () => {
    expect(modeloExacto(LISTA, '13 pro')?.clave).toBe('iPhone 13 Pro')
    expect(modeloExacto(LISTA, 'iphone 14')?.clave).toBe('iPhone 14')
    expect(modeloExacto(LISTA, '13')).toBeNull()
  })

  it('un SKU exacto abre su modelo y su capacidad con ese SKU', () => {
    expect(aplicarSku(MOVILES, 'i13PRO128GrafitoA', true)).toEqual({
      destino: { modelo: 'iPhone 13 Pro', capacidad: '128GB', sku: { texto: 'i13PRO128GrafitoA', exacto: true } },
    })
  })

  it('un principio de SKU: su modelo y, si es de una capacidad, también esa; si un SKU es igual a lo escrito, ese exacto', () => {
    expect(aplicarSku(MOVILES, 'i13pro', false)).toEqual({ destino: { modelo: 'iPhone 13 Pro', capacidad: null, sku: { texto: 'i13pro', exacto: false } } })
    expect(aplicarSku(MOVILES, 'i13PRO128', false)).toEqual({ destino: { modelo: 'iPhone 13 Pro', capacidad: '128GB', sku: { texto: 'i13PRO128', exacto: false } } })
    expect(aplicarSku(MOVILES, 'i13pro128grafitoa', false)).toEqual({
      destino: { modelo: 'iPhone 13 Pro', capacidad: '128GB', sku: { texto: 'i13PRO128GrafitoA', exacto: true } },
    })
  })

  it('SKU de varios modelos o ninguno: avisa', () => {
    expect(aplicarSku(MOVILES, 'i1', false)).toEqual({ aviso: 'Hay SKU de 2 modelos que empiezan así: elige uno o escribe más' })
    expect(aplicarSku(MOVILES, 'x9', false)).toEqual({ aviso: 'Ningún móvil en stock tiene un SKU que empiece así' })
  })

  it('Enter: el modelo exacto si ningún SKU empieza así; si no, el filtro por el principio del SKU', () => {
    expect(resolverEnter(LISTA, MOVILES, '14')).toEqual({ destino: { modelo: 'iPhone 14', capacidad: null, sku: null } })
    expect(resolverEnter(LISTA, MOVILES, 'i14')).toEqual({ destino: { modelo: 'iPhone 14', capacidad: '128GB', sku: { texto: 'i14', exacto: false } } })
  })
})
```

En `src/modules/telefonos/stock/modelo.test.ts`, añade `normalizar, partesSku, pasaSku` al import de `./modelo` y, al final:

```ts
describe('stock de teléfonos: filtro de SKU (spec 0.9.18 §4.2)', () => {
  const G = visto({ sku: 'i13PRO128GrafitoA' })
  it('sin mayúsculas ni espacios', () => {
    expect(normalizar(' i13 PRO ')).toBe('i13pro')
  })

  it('SKU exacto o que empieza por lo escrito; sin SKU, no pasa', () => {
    expect(pasaSku(G, null)).toBe(true)
    expect(pasaSku(G, { texto: 'i13pro128grafitoa', exacto: true })).toBe(true)
    expect(pasaSku(G, { texto: 'i13PRO128', exacto: true })).toBe(false)
    expect(pasaSku(G, { texto: 'i13 pro', exacto: false })).toBe(true)
    expect(pasaSku(visto({ sku: null }), { texto: 'i13', exacto: false })).toBe(false)
  })

  it('la parte buscada del SKU, para resaltarla', () => {
    expect(partesSku('i13PRO128GrafitoA', { texto: 'i13pro', exacto: false })).toEqual(['i13PRO', '128GrafitoA'])
    expect(partesSku('i13PRO128GrafitoA', { texto: 'i13PRO128GrafitoA', exacto: true })).toEqual(['i13PRO128GrafitoA', ''])
    expect(partesSku('i14128AzulA', { texto: 'i13', exacto: false })).toBeNull()
    expect(partesSku('i13PRO128GrafitoA', null)).toBeNull()
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/buscador.test.ts src/modules/telefonos/stock/modelo.test.ts`
Expected: FAIL (no existen `buscador.ts` ni `pasaSku`).

- [x] **Step 2: Reglas puras y store**

En `src/modules/telefonos/stock/modelo.ts`, tras `contarFiltros`, añade:

```ts
/** El filtro de SKU de la cabecera del modelo (spec 0.9.18 §4.2): un SKU exacto o los que empiezan por un texto. */
export type FiltroSku = { texto: string; exacto: boolean }

/** Para comparar SKU y nombres de modelo: sin mayúsculas ni espacios. */
export const normalizar = (texto: string): string => texto.toLowerCase().replace(/\s+/g, '')

export function pasaSku(m: MovilStock, sku: FiltroSku | null): boolean {
  if (!sku) return true
  if (!m.sku) return false
  const q = normalizar(sku.texto)
  return sku.exacto ? normalizar(m.sku) === q : normalizar(m.sku).startsWith(q)
}

/** La parte del SKU que coincide con el filtro, para resaltarla en la columna SKU: [resaltado, resto], o null. Los SKU no
 *  llevan espacios: se corta por el largo de lo buscado sin espacios. */
export function partesSku(sku: string, filtro: FiltroSku | null): [string, string] | null {
  if (!filtro || !normalizar(sku).startsWith(normalizar(filtro.texto))) return null
  const n = filtro.exacto ? sku.length : normalizar(filtro.texto).length
  return [sku.slice(0, n), sku.slice(n)]
}
```

Crea `src/modules/telefonos/stock/buscador.ts`:

```ts
import { capacidadDe, cuentaComoStock, modeloDe, normalizar, type FiltroSku, type ModeloLista, type Movil } from './modelo'

/** El buscador de la columna lateral (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.2): acepta un
 *  modelo («13 pro») o un SKU, entero o el principio, sin mayúsculas ni espacios. Los SKU que cuentan son los del stock. */

export const MAX_SUGERENCIAS = 7
export type SugerenciaSku = { sku: string; modelo: string; capacidad: string; cuenta: number }
/** Adónde lleva el buscador: un modelo, su pestaña de capacidad (null = «Todas») y el filtro de SKU. */
export type Destino = { modelo: string; capacidad: string | null; sku: FiltroSku | null }
export type Resultado = { destino: Destino } | { aviso: string }

const enStockConSku = (m: Movil): m is Movil & { sku: string } => cuentaComoStock(m) && !!m.sku

/** Los SKU en stock que empiezan por lo escrito, a partir de 2 caracteres: los más cortos primero, después los que más
 *  hay y por último por orden alfabético. */
export function sugerenciasSku(moviles: Movil[], texto: string): SugerenciaSku[] {
  const q = normalizar(texto)
  if (q.length < 2) return []
  const mapa = new Map<string, SugerenciaSku>()
  for (const m of moviles.filter(enStockConSku)) {
    if (!normalizar(m.sku).startsWith(q)) continue
    const s = mapa.get(m.sku) ?? { sku: m.sku, modelo: modeloDe(m), capacidad: capacidadDe(m), cuenta: 0 }
    s.cuenta++
    mapa.set(m.sku, s)
  }
  return [...mapa.values()].sort((a, b) => a.sku.length - b.sku.length || b.cuenta - a.cuenta || a.sku.localeCompare(b.sku))
}

/** La lista de modelos con lo escrito: los que lo llevan en el nombre y los que tienen alguno de los SKU sugeridos. */
export function filtrarModelos(lista: ModeloLista[], texto: string, sugerencias: SugerenciaSku[]): ModeloLista[] {
  const q = normalizar(texto)
  if (!q) return lista
  const conSku = new Set(sugerencias.map((s) => s.modelo))
  return lista.filter((g) => normalizar(g.clave).includes(q) || conSku.has(g.clave))
}

/** El modelo que es exactamente lo escrito, con «iPhone» delante o sin él («13 pro» → iPhone 13 Pro). */
export function modeloExacto(lista: ModeloLista[], texto: string): ModeloLista | null {
  const sinIphone = (t: string) => normalizar(t).replace(/^iphone/, '')
  const q = sinIphone(texto)
  if (!q) return null
  return lista.find((g) => sinIphone(g.clave) === q) ?? null
}

/** Filtrar por SKU: los móviles en stock con ese SKU (exacto) o que empiezan por lo escrito. Si son de un modelo, lo abre;
 *  si además son de una capacidad, también la elige. Si algún SKU es igual a lo escrito, el filtro es ese SKU exacto. */
export function aplicarSku(moviles: Movil[], texto: string, exacto: boolean): Resultado {
  const q = normalizar(texto)
  const xs = moviles.filter(enStockConSku).filter((m) => (exacto ? normalizar(m.sku) === q : normalizar(m.sku).startsWith(q)))
  if (xs.length === 0) return { aviso: 'Ningún móvil en stock tiene un SKU que empiece así' }
  const modelos = new Set(xs.map(modeloDe))
  if (modelos.size > 1) return { aviso: `Hay SKU de ${modelos.size} modelos que empiezan así: elige uno o escribe más` }
  const capacidades = new Set(xs.map(capacidadDe))
  const igual = xs.find((m) => normalizar(m.sku) === q)
  return {
    destino: {
      modelo: modeloDe(xs[0]),
      capacidad: capacidades.size === 1 ? [...capacidades][0] : null,
      sku: igual ? { texto: igual.sku, exacto: true } : { texto: texto.trim(), exacto: false },
    },
  }
}

/** Enter sin elegir ningún SKU: si lo escrito es exactamente un modelo y ningún SKU empieza así, abre ese modelo con
 *  «Todas»; si no, filtra por los SKU que empiezan por lo escrito. */
export function resolverEnter(lista: ModeloLista[], moviles: Movil[], texto: string): Resultado {
  const exacto = modeloExacto(lista, texto)
  const q = normalizar(texto)
  const algunSku = moviles.filter(enStockConSku).some((m) => normalizar(m.sku).startsWith(q))
  if (exacto && !algunSku) return { destino: { modelo: exacto.clave, capacidad: null, sku: null } }
  return aplicarSku(moviles, texto, false)
}
```

En `src/modules/telefonos/stock/estado.ts`:
1. Cambia el import por `import { FILTROS_VACIOS, type FiltroSku, type Filtros } from './modelo'`.
2. En `EstadoStock`, cambia `filtrosDe: { clave: string | null; filtros: Filtros }` por
   `filtrosDe: { clave: string | null; filtros: Filtros; sku: FiltroSku | null }`.
3. En el valor inicial, cambia `filtrosDe: { clave: null, filtros: FILTROS_VACIOS }` por `filtrosDe: { clave: null, filtros: FILTROS_VACIOS, sku: null }`.
4. En el comentario de `EstadoStock`, cambia «los filtros de esa pestaña» por «los filtros y el SKU de esa pestaña».

Run: `npx vitest run src/modules/telefonos/stock/buscador.test.ts src/modules/telefonos/stock/modelo.test.ts`
Expected: PASS.

- [x] **Step 3: Tests de la pantalla (fallan)**

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, añade dentro del `describe`, tras «escribir en el buscador de la columna…»:

```tsx
  const buscador = () => screen.getByRole('textbox', { name: 'Buscar modelo o SKU' })
  const skusSugeridos = () => within(screen.getByRole('list', { name: 'SKU en stock' })).getAllByRole('button')
    .map((b) => b.querySelector('.font-mono')?.textContent)

  it('un principio de SKU con Enter abre su modelo, filtra por los que empiezan así y lo resalta', async () => {
    const user = userEvent.setup()
    abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(buscador(), 'i13')
    expect(skusSugeridos()).toEqual(['i13128AzulA', 'i13256RosaB'])
    expect(screen.getByText('Enter: todos los que empiezan así')).toBeInTheDocument()
    expect(modelosDeLaLista()).toEqual(['iPhone 13: 3 en stock'])
    await user.keyboard('{Enter}')
    expect(screen.getByRole('heading', { level: 2, name: 'iPhone 13' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Todas: 3' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('pastilla-sku')).toHaveTextContent('SKU empieza por i13')
    expect(filas().map(imeiDe)).toEqual(['350000000000006', '350000000000001'])
    expect(within(filas()[0]).getByText('i13', { selector: 'mark' })).toBeInTheDocument()
    expect(buscador()).toHaveValue('')
  })

  it('un SKU exacto con las flechas y Enter abre su modelo y su capacidad con ese SKU', async () => {
    const user = userEvent.setup()
    abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(buscador(), 'i13')
    await user.keyboard('{ArrowDown}{Enter}')
    expect(screen.getByRole('button', { name: '128 GB: 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('pastilla-sku')).toHaveTextContent('SKU i13128AzulA')
    expect(screen.getByTestId('pastilla-sku')).not.toHaveTextContent('empieza por')
    expect(filas().map(imeiDe)).toEqual(['350000000000001'])
  })

  it('un clic en un SKU de la lista hace lo mismo', async () => {
    const user = userEvent.setup()
    abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(buscador(), 'i13')
    await user.click(within(screen.getByRole('list', { name: 'SKU en stock' })).getAllByRole('button')[1])
    expect(screen.getByRole('button', { name: '256 GB: 1' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('pastilla-sku')).toHaveTextContent('SKU i13256RosaB')
    expect(filas().map(imeiDe)).toEqual(['350000000000006'])
  })

  it('si los SKU son de varios modelos, Enter avisa y no abre nada', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    await user.type(buscador(), 'i1')
    expect(screen.getByText('Hay SKU de 2 modelos: elige uno o escribe más')).toBeInTheDocument()
    await user.keyboard('{Enter}')
    expect(screen.getByText('Hay SKU de 2 modelos que empiezan así: elige uno o escribe más')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'iPhone 13' })).toBeInTheDocument()
    expect(screen.queryByTestId('pastilla-sku')).not.toBeInTheDocument()
  })

  it('un modelo escrito entero con Enter lo abre con «Todas»; sin nada que empiece así, lo dice; Escape lo borra', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13 128GB: 2' }))
    await user.type(buscador(), '14{Enter}')
    expect(screen.getByRole('heading', { level: 2, name: 'iPhone 14' })).toBeInTheDocument()
    expect(buscador()).toHaveValue('')
    await user.type(buscador(), '13 pro')
    expect(within(lista()).queryAllByRole('button', { name: / en stock$/ })).toHaveLength(0)
    expect(screen.getByText('Ningún modelo ni SKU en stock empieza así.')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(buscador()).toHaveValue('')
    expect(modelosDeLaLista()).toHaveLength(2)
  })

  it('la × de la pastilla quita el SKU, y cambiar de pestaña también', async () => {
    const user = userEvent.setup()
    abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(buscador(), 'i13{Enter}')
    await user.click(screen.getByRole('button', { name: 'Quitar el filtro de SKU' }))
    expect(screen.queryByTestId('pastilla-sku')).not.toBeInTheDocument()
    expect(filas()).toHaveLength(3)
    await user.type(buscador(), 'i13{Enter}')
    expect(screen.getByTestId('pastilla-sku')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '128 GB: 2' }))
    expect(screen.queryByTestId('pastilla-sku')).not.toBeInTheDocument()
  })

  it('al volver de un móvil sigue el filtro de SKU', async () => {
    const user = userEvent.setup()
    const primera = abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(buscador(), 'i13{Enter}')
    await user.click(screen.getAllByRole('button', { name: /^Abrir / })[0])
    expect(await screen.findByText(/^Detalle del IMEI/)).toBeInTheDocument()
    primera.unmount()
    abrir()
    expect(await screen.findByTestId('pastilla-sku')).toHaveTextContent('SKU empieza por i13')
    expect(filas()).toHaveLength(2)
  })
```

Run: `npx vitest run src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no hay lista de SKU ni pastilla).

- [x] **Step 4: Pantalla**

Crea `src/modules/telefonos/stock/BuscadorModelo.tsx`:

```tsx
import { useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { aplicarSku, MAX_SUGERENCIAS, resolverEnter, type Destino, type Resultado, type SugerenciaSku } from './buscador'
import { normalizar, textoCapacidad, type ModeloLista, type Movil } from './modelo'

type Props = {
  lista: ModeloLista[]
  moviles: Movil[]
  texto: string
  sugerencias: SugerenciaSku[]
  onTexto: (texto: string) => void
  onAbrir: (destino: Destino) => void
}

/** El buscador de la columna lateral (0.9.18, spec §4.2): escribir filtra la lista de modelos y, a partir de 2 caracteres,
 *  enseña los SKU en stock que empiezan así (hasta 7). Elegir uno (clic, o flechas y Enter) abre su modelo y su capacidad
 *  con ese SKU exacto; Enter sin elegir abre el modelo escrito o filtra por los SKU que empiezan así. Escape borra. */
export function BuscadorModelo({ lista, moviles, texto, sugerencias, onTexto, onAbrir }: Props) {
  const [marcada, setMarcada] = useState(-1)
  const [aviso, setAviso] = useState<string | null>(null)
  const vistas = sugerencias.slice(0, MAX_SUGERENCIAS)
  const largo = normalizar(texto).length
  const modelosConSku = new Set(sugerencias.map((s) => s.modelo)).size
  const escribir = (t: string) => {
    onTexto(t)
    setMarcada(-1)
    setAviso(null)
  }
  const resolver = (r: Resultado) => {
    if ('aviso' in r) {
      setAviso(r.aviso)
      return
    }
    escribir('')
    onAbrir(r.destino)
  }
  return (
    <div>
      <input aria-label="Buscar modelo o SKU" placeholder="Buscar modelo o SKU…" value={texto} autoComplete="off"
        onChange={(e) => escribir(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown' && vistas.length > 0) {
            e.preventDefault()
            setMarcada((i) => Math.min(i + 1, vistas.length - 1))
          } else if (e.key === 'ArrowUp' && vistas.length > 0) {
            e.preventDefault()
            setMarcada((i) => Math.max(i - 1, -1))
          } else if (e.key === 'Enter' && texto.trim()) {
            e.preventDefault()
            resolver(marcada >= 0 && vistas[marcada] ? aplicarSku(moviles, vistas[marcada].sku, true) : resolverEnter(lista, moviles, texto))
          } else if (e.key === 'Escape' && texto) {
            escribir('')
          }
        }}
        className="h-8 w-full rounded-md border border-borde-input px-2.5 text-[12.5px]" />
      {aviso && <p className="mt-1.5 text-[11.5px] font-bold text-fila-solicitud-brd">{aviso}</p>}
      {vistas.length > 0 && (
        <div className="mt-1.5 rounded-lg border border-borde-input bg-superficie p-1">
          <p className="mx-1.5 mt-0.5 mb-1 text-[10.5px] font-bold text-azul-gris">SKU EN STOCK</p>
          <ul aria-label="SKU en stock">
            {vistas.map((s, i) => (
              <li key={s.sku}>
                <button type="button" aria-current={i === marcada || undefined} onClick={() => resolver(aplicarSku(moviles, s.sku, true))}
                  className={cn('w-full cursor-pointer rounded-md px-1.5 py-1 text-left', i === marcada ? 'bg-seleccion-suave' : 'hover:bg-seleccion-suave')}>
                  <span className="block font-mono text-[11.5px] break-all text-azul-medio"><b className="text-azul-noche">{s.sku.slice(0, largo)}</b>{s.sku.slice(largo)}</span>
                  <span className="flex text-[11px] text-azul-gris">{`${s.modelo} · ${textoCapacidad(s.capacidad)}`}<b className="ml-auto text-azul-medio">{s.cuenta}</b></span>
                </button>
              </li>
            ))}
          </ul>
          <p className="px-1.5 pt-1 pb-0.5 text-[11px] text-azul-gris">
            {`${sugerencias.length > MAX_SUGERENCIAS ? `y ${sugerencias.length - MAX_SUGERENCIAS} más · ` : ''}${modelosConSku > 1 ? `Hay SKU de ${modelosConSku} modelos: elige uno o escribe más` : 'Enter: todos los que empiezan así'}`}
          </p>
        </div>
      )}
    </div>
  )
}
```

Crea `src/modules/telefonos/stock/PastillaSku.tsx`:

```tsx
import type { FiltroSku } from './modelo'

/** El filtro de SKU en la cabecera del modelo (spec 0.9.18 §4.2): «SKU i13PRO128GrafitoA ×» o «SKU empieza por i13PRO128 ×». */
export function PastillaSku({ sku, onQuitar }: { sku: FiltroSku; onQuitar: () => void }) {
  return (
    <span data-testid="pastilla-sku" className="inline-flex items-center gap-1.5 rounded-full bg-azul-noche py-0.5 pr-1 pl-2.5 text-[12px] font-semibold text-superficie">
      {sku.exacto ? 'SKU' : 'SKU empieza por'} <b className="font-mono">{sku.texto}</b>
      <button type="button" aria-label="Quitar el filtro de SKU" onClick={onQuitar}
        className="h-[18px] w-[18px] cursor-pointer rounded-full bg-white/20 leading-4 text-superficie">×</button>
    </span>
  )
}
```

En `src/modules/telefonos/stock/ListaModelos.tsx`, cambia `Ningún modelo</li>` por `Ningún modelo ni SKU en stock empieza así.</li>`.

En `src/modules/telefonos/stock/columnas.tsx`:
1. Cambia el import de `./modelo` por `import { capacidadDe, muestraColor, partesSku, textoCapacidad, type FiltroSku, type Movil } from './modelo'`.
2. Sustituye el tipo y la firma por:

```tsx
/** `conCapacidad`: con la pestaña «Todas», la columna «Cap.» detrás del IMEI (spec §4.3). `sku`: el filtro de SKU, cuya
 *  parte buscada se resalta en la columna SKU (§4.2). */
export type OpcionesColumnas = { conCapacidad?: boolean; sku?: FiltroSku | null }
```

y `export function columnasMoviles(abrir: (imeiSn: string) => void, { conCapacidad = false, sku = null }: OpcionesColumnas = {}): ColumnDef<Movil>[] {`.
3. Sustituye la columna `sku` por:

```tsx
    {
      id: 'sku', header: 'SKU', size: 150,
      cell: ({ row }) => {
        const valor = row.original.sku
        const partes = valor ? partesSku(valor, sku) : null
        return (
          <span className="font-mono text-[11.5px]">
            {partes ? <><mark className="bg-amarillo/60 text-azul-noche">{partes[0]}</mark>{partes[1]}</> : (valor ?? '—')}
          </span>
        )
      },
    },
```

Sustituye `src/modules/telefonos/stock/StockTelefonosPage.tsx` entero por:

```tsx
import { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { DataTable } from '@/shared/ui/DataTable'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useStockTelefonos } from '../api'
import { filtrarModelos, sugerenciasSku, type Destino } from './buscador'
import { BuscadorImei } from './BuscadorImei'
import { BuscadorModelo } from './BuscadorModelo'
import { columnasMoviles } from './columnas'
import { claveFiltros, estadoStockTelefonos } from './estado'
import { FiltrosMoviles } from './FiltrosMoviles'
import { ListaModelos } from './ListaModelos'
import {
  capacidadDe, cuentaComoStock, FILTROS_VACIOS, modeloDe, modelosLista, ordenarTabla, pasa, pasaSku, pestanasCapacidad, preparar,
  type Filtros, type FiltroSku,
} from './modelo'
import { PastillaSku } from './PastillaSku'
import { PestanasCapacidad } from './PestanasCapacidad'
import { BandaNsys, PildoraNsys } from './PildoraNsys'

const PIE = '"Quién lo tiene" sale de las asignaciones abiertas del ERP, al momento; la caja, de la última exportación de NSYS. Pasa el ratón por encima para ver desde cuándo. El IMEI abre el detalle del móvil.'

/** «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; 0.9.18, spec «Columna compacta, SKU, Quién lo
 *  tiene y Revisado» §4): una fila por modelo a la izquierda, con el buscador de modelo o SKU, y a la derecha el modelo con
 *  sus pestañas de capacidad, el filtro de SKU, sus filtros y sus móviles. Solo lectura: los datos vienen del espejo de NSYS
 *  y de las asignaciones abiertas del ERP. Un móvil abre su detalle de IMEIs, con la ficha de NSYS y el rastro. */
export function StockTelefonosPage() {
  const navigate = useNavigate()
  const stock = useStockTelefonos()
  // «Quién lo tiene» se calcula a la hora de la carga (dataUpdatedAt): el sondeo de 60 s lo vuelve a calcular.
  const moviles = useMemo(() => preparar(stock.data ?? [], stock.dataUpdatedAt), [stock.data, stock.dataUpdatedAt])
  const sinCargar = stock.isError && !stock.data
  const [{ verNoCuenta, textoModelo, elegido, capacidad: capacidadElegida, filtrosDe }, setEstado] = useStore(estadoStockTelefonos)
  const lista = useMemo(() => modelosLista(moviles, verNoCuenta), [moviles, verNoCuenta])
  const sugerencias = useMemo(() => sugerenciasSku(moviles, textoModelo), [moviles, textoModelo])
  const visibles = filtrarModelos(lista, textoModelo, sugerencias)
  // El modelo abierto no depende de lo escrito en el buscador: solo cambia al elegir otro (spec §4.2).
  const modelo = lista.find((g) => g.clave === elegido) ?? lista[0] ?? null
  const todos = useMemo(() => (modelo ? moviles.filter((m) => modeloDe(m) === modelo.clave) : []), [moviles, modelo])
  const pestanas = pestanasCapacidad(todos, verNoCuenta)
  const capacidad = modelo !== null && modelo.clave === elegido && pestanas.some((p) => p.capacidad !== null && p.capacidad === capacidadElegida && p.activa)
    ? capacidadElegida : null
  const clave = modelo ? claveFiltros(modelo.clave, capacidad) : null
  const { filtros, sku } = filtrosDe.clave === clave ? filtrosDe : { filtros: FILTROS_VACIOS, sku: null }
  const cambiarFiltros = (f: Filtros, s: FiltroSku | null) => setEstado((e) => ({ ...e, filtrosDe: { clave, filtros: f, sku: s } }))
  const abrirModelo = (d: Destino) => {
    // Volver a pulsar lo que ya está abierto no borra sus filtros; elegir un SKU sí los cambia.
    if (d.sku === null && d.modelo === modelo?.clave && d.capacidad === capacidad) return
    setEstado((e) => ({ ...e, elegido: d.modelo, capacidad: d.capacidad, filtrosDe: { clave: claveFiltros(d.modelo, d.capacidad), filtros: FILTROS_VACIOS, sku: d.sku } }))
  }
  const delModelo = useMemo(
    () => (modelo?.moviles ?? []).filter((m) => (capacidad === null || capacidadDe(m) === capacidad) && pasaSku(m, sku)),
    [modelo, capacidad, sku],
  )
  const filas = useMemo(() => ordenarTabla(delModelo.filter((m) => pasa(m, filtros))), [delModelo, filtros])
  const enStock = moviles.filter(cuentaComoStock).length
  const abrir = useCallback((imeiSn: string) => navigate(`/reparaciones/imeis/${encodeURIComponent(imeiSn)}`, { state: { desde: '/telefonos/stock' } }), [navigate])
  const columnas = useMemo(() => columnasMoviles(abrir, { conCapacidad: capacidad === null, sku }), [abrir, capacidad, sku])
  return (
    <div className="p-10 max-[1500px]:p-5">
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Stock de teléfonos</h1>
        {!sinCargar && <PildoraContador texto={`${enStock} ${enStock === 1 ? 'móvil' : 'móviles'} en stock`} />}
        <PildoraNsys />
      </div>
      <BandaNsys />
      <div className="mb-3 flex flex-wrap items-center gap-4">
        <BuscadorImei onElegir={abrir} />
        <button type="button" role="switch" aria-checked={verNoCuenta} onClick={() => setEstado((e) => ({ ...e, verNoCuenta: !e.verNoCuenta }))}
          className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-azul-medio">
          <span className={cn('relative h-[18px] w-[34px] rounded-full transition-colors', verNoCuenta ? 'bg-verde-ok' : 'bg-pill-borde')}>
            <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-superficie transition-[left]', verNoCuenta ? 'left-[18px]' : 'left-[2px]')} />
          </span>
          Ver también lo que no cuenta como stock
        </button>
      </div>
      <div className="grid grid-cols-[270px_1fr] items-start gap-3 max-[1500px]:grid-cols-[230px_1fr]">
        <ListaModelos modelos={visibles} elegido={modelo?.clave ?? null} capacidad={capacidad}
          onAbrir={(m, c) => abrirModelo({ modelo: m, capacidad: c, sku: null })}
          buscador={<BuscadorModelo lista={lista} moviles={moviles} texto={textoModelo} sugerencias={sugerencias}
            onTexto={(t) => setEstado((e) => ({ ...e, textoModelo: t }))} onAbrir={abrirModelo} />} />
        <section aria-label="Móviles del modelo" className="min-w-0 rounded-md bg-superficie p-3">
          {modelo && (
            <>
              <div className="mb-2 flex flex-wrap items-center gap-2.5">
                <h2 className="text-base font-bold text-azul-noche">{modelo.clave}</h2>
                <PestanasCapacidad pestanas={pestanas} elegida={capacidad} onElegir={(c) => abrirModelo({ modelo: modelo.clave, capacidad: c, sku: null })} />
                <PildoraContador texto={`${filas.length} ${filas.length === 1 ? 'móvil' : 'móviles'}`} />
                {sku && <PastillaSku sku={sku} onQuitar={() => cambiarFiltros(filtros, null)} />}
              </div>
              <FiltrosMoviles moviles={delModelo} filtros={filtros} onCambiar={(f) => cambiarFiltros(f, sku)} />
              <DataTable columns={columnas} data={filas} vacio="Ningún móvil con estos filtros" ajuste="fluido" alturaMax="calc(100vh - 430px)"
                getRowId={(m) => `${m.imeiSn}|${m.lote}`} onAbrir={(m) => abrir(m.imeiSn)}
                filaClase={(m) => cn('border-l-4', 'border-l-transparent', !cuentaComoStock(m) && 'text-texto-suave')} />
              <p className="mt-2 text-[11.5px] text-azul-gris">{PIE}</p>
            </>
          )}
          {!modelo && sinCargar && <p className="p-4 text-[12px] text-azul-gris">No se pudo cargar el stock de teléfonos.</p>}
          {!modelo && !sinCargar && stock.data && <p className="p-4 text-[12px] text-azul-gris">No hay móviles en stock.</p>}
        </section>
      </div>
    </div>
  )
}
```

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 6: Commit**

```bash
git add src/modules/telefonos
git commit -m "feat: buscador de modelo o SKU y filtro de SKU en el stock de telefonos (0.9.18)"
```

---

### Task 9: Web — tabla color × grado en lugar de los chips, y la pantalla sin scroll

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/MatrizColorGrado.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.ts` (tabla color × grado), `modelo.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/FiltrosMoviles.tsx` (entero), `ListaModelos.tsx`, `StockTelefonosPage.tsx` (entero)
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`

**Interfaces:**
- Consumes: `Movil`, `Filtros`, `valores`, `pasa`, `reparto`, `compararGrados`, `contarFiltros`, `muestraColor` (Tasks 6-7);
  `FiltroSku`, `PastillaSku` y `BuscadorModelo` (Task 8); `PildoraGrado` y `BarraReparto`.
- Produces:
  - `modelo.ts`:
    - tipos `Casilla = { total: number; reparto: Reparto }` y
      `Matriz = { colores: string[]; grados: string[]; casillas; porColor: ReadonlyMap<string, number>; porGrado: ReadonlyMap<string, number>; total: number }`;
    - `matriz(moviles, f)`, `casilla(m, color, grado)`, `tituloCasilla(color, grado, c)`;
    - `casillaElegida(f, color, grado)`, `colorElegido(f, color)`, `gradoElegido(f, grado)`;
    - `pulsarCasilla(f, color, grado, sumar)`, `pulsarColor(f, color)` y `pulsarGrado(f, grado)`.
  - `MatrizColorGrado({ moviles, filtros, onCambiar })` (`aria-label` «Color y grado»).
  - `FiltrosMoviles` pasa a ser la columna de la derecha de la tabla: ayuda, desplegables y avisos (sin chips ni «Quitar filtros»).
  - La página: «Quitar filtros (n)» en la cabecera del modelo (n cuenta también el SKU), la región «Tabla de móviles» y la
    altura ajustada a la ventana (`h-[calc(100vh-var(--spacing-navbar))]`, la tabla con `alturaMax="100%"`).

- [x] **Step 1: Tests de las reglas (fallan)**

En `src/modules/telefonos/stock/modelo.test.ts`, añade al import de `./modelo`:
`casilla, casillaElegida, colorElegido, gradoElegido, matriz, pulsarCasilla, pulsarColor, pulsarGrado, tituloCasilla`, y al final:

```ts
describe('stock de teléfonos: tabla color × grado (spec 0.9.18 §4.4)', () => {
  const X = [
    visto({ imeiSn: '350000000000041', color: 'Grafito', grado: 'A', ubicacion: 'Listos' }),
    visto({ imeiSn: '350000000000042', color: 'Grafito', grado: 'A', ubicacion: 'Taller · Pulir', bateriaNueva: true }),
    visto({ imeiSn: '350000000000043', color: 'Grafito', grado: 'n/a', ubicacion: 'En revisión' }),
    visto({ imeiSn: '350000000000044', color: 'Oro', grado: 'B', ubicacion: 'Listos' }),
    visto({ imeiSn: '350000000000045', color: 'Oro', grado: 'A', ubicacion: 'Listos', estado: 'DE_PEDIDO' }),
  ]

  it('cuenta solo el stock, con sus totales y el reparto de cada casilla', () => {
    const m = matriz(X, FILTROS_VACIOS)
    expect(m.colores).toEqual(['Grafito', 'Oro'])
    expect(m.grados).toEqual(['A', 'B', 'n/a'])
    expect(casilla(m, 'Grafito', 'A')).toEqual({ total: 2, reparto: { listos: 1, taller: 1, revision: 0 } })
    expect(casilla(m, 'Oro', 'A').total).toBe(0)
    expect(m.porColor.get('Grafito')).toBe(3)
    expect(m.porGrado.get('A')).toBe(2)
    expect(m.total).toBe(4)
    expect(tituloCasilla('Grafito', 'A', casilla(m, 'Grafito', 'A'))).toBe('Grafito A: 1 listos · 1 en taller · 0 en revisión')
  })

  it('respeta los demás filtros pero no su propia selección; lo elegido sale aunque se quede a cero', () => {
    const m = matriz(X, { ...FILTROS_VACIOS, bateria: new Set([BATERIA_NUEVA]), color: new Set(['Oro']) })
    expect(m.total).toBe(1)
    expect(casilla(m, 'Grafito', 'A').total).toBe(1)
    expect(m.colores).toEqual(['Grafito', 'Oro'])
  })

  it('una casilla filtra por ese color y ese grado, y volver a pulsarla lo quita; con Ctrl suma su color y su grado', () => {
    const f1 = pulsarCasilla(FILTROS_VACIOS, 'Grafito', 'A', false)
    expect([...f1.color, ...f1.grado]).toEqual(['Grafito', 'A'])
    expect(contarFiltros(pulsarCasilla(f1, 'Grafito', 'A', false))).toBe(0)
    const f2 = pulsarCasilla(f1, 'Oro', 'B', true)
    expect([...f2.color]).toEqual(['Grafito', 'Oro'])
    expect([...f2.grado]).toEqual(['A', 'B'])
    expect(casillaElegida(f2, 'Oro', 'A')).toBe(true)
    expect(casillaElegida(f1, 'Oro', 'A')).toBe(false)
    expect(casillaElegida(FILTROS_VACIOS, 'Oro', 'A')).toBe(false)
  })

  it('el color o el grado de la cabecera filtra solo por eso, y volver a pulsarlo lo quita', () => {
    const porColor = pulsarColor(pulsarCasilla(FILTROS_VACIOS, 'Grafito', 'A', false), 'Oro')
    expect([...porColor.color]).toEqual(['Oro'])
    expect(porColor.grado.size).toBe(0)
    expect(colorElegido(porColor, 'Oro')).toBe(true)
    expect(pulsarColor(porColor, 'Oro').color.size).toBe(0)
    const porGrado = pulsarGrado(porColor, 'B')
    expect([...porGrado.grado]).toEqual(['B'])
    expect(porGrado.color.size).toBe(0)
    expect(gradoElegido(porGrado, 'B')).toBe(true)
    expect(casillaElegida(porGrado, 'Grafito', 'B')).toBe(true)
    expect(pulsarGrado(porGrado, 'B').grado.size).toBe(0)
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/modelo.test.ts`
Expected: FAIL (no existe `matriz`).

- [x] **Step 2: Reglas puras**

En `src/modules/telefonos/stock/modelo.ts`, tras `partesSku`, añade:

```ts
export type Casilla = { total: number; reparto: Reparto }
/** La tabla color × grado (spec 0.9.18 §4.4): filas de color, columnas de grado y sus totales. */
export type Matriz = {
  colores: string[]
  grados: string[]
  casillas: ReadonlyMap<string, Casilla>
  porColor: ReadonlyMap<string, number>
  porGrado: ReadonlyMap<string, number>
  total: number
}

const CASILLA_VACIA: Casilla = { total: 0, reparto: { listos: 0, taller: 0, revision: 0 } }
const claveCasilla = (color: string, grado: string) => `${color}\u0000${grado}`

/** La tabla de los móviles que se ven del modelo: solo el stock, con los filtros de batería, ubicación y técnico pero sin su
 *  propia selección de color y grado, para verla entera con lo elegido resaltado. Los colores y grados elegidos salen
 *  aunque se queden a cero, para poder quitarlos. Colores por orden alfabético; grados, en su orden de siempre. */
export function matriz(moviles: Movil[], f: Filtros): Matriz {
  const base = moviles.filter((m) => cuentaComoStock(m) && pasa(m, f, ['color', 'grado']))
  const color = (m: Movil) => valores('color', m)[0]
  const grado = (m: Movil) => valores('grado', m)[0]
  const colores = [...new Set([...base.map(color), ...f.color])].sort((a, b) => a.localeCompare(b, 'es'))
  const grados = [...new Set([...base.map(grado), ...f.grado])].sort(compararGrados)
  const grupos = new Map<string, Movil[]>()
  const porColor = new Map<string, number>()
  const porGrado = new Map<string, number>()
  for (const m of base) {
    const k = claveCasilla(color(m), grado(m))
    grupos.set(k, [...(grupos.get(k) ?? []), m])
    porColor.set(color(m), (porColor.get(color(m)) ?? 0) + 1)
    porGrado.set(grado(m), (porGrado.get(grado(m)) ?? 0) + 1)
  }
  const casillas = new Map([...grupos].map(([k, xs]) => [k, { total: xs.length, reparto: reparto(xs) }] as const))
  return { colores, grados, casillas, porColor, porGrado, total: base.length }
}

export function casilla(m: Matriz, color: string, grado: string): Casilla {
  return m.casillas.get(claveCasilla(color, grado)) ?? CASILLA_VACIA
}

/** El detalle al pasar el ratón: «Grafito A: 4 listos · 2 en taller · 1 en revisión». */
export function tituloCasilla(color: string, grado: string, c: Casilla): string {
  return `${color} ${grado}: ${c.reparto.listos} listos · ${c.reparto.taller} en taller · ${c.reparto.revision} en revisión`
}

const solo = (s: ReadonlySet<string>, v: string) => s.size === 1 && s.has(v)

/** ¿Va resaltada la casilla? Con algo elegido, las que caen dentro del color y del grado elegidos. */
export function casillaElegida(f: Filtros, color: string, grado: string): boolean {
  if (f.color.size === 0 && f.grado.size === 0) return false
  return (f.color.size === 0 || f.color.has(color)) && (f.grado.size === 0 || f.grado.has(grado))
}

/** ¿Va resaltada la cabecera? Un color (o un grado) elegido solo, sin el otro. */
export const colorElegido = (f: Filtros, color: string): boolean => f.color.has(color) && f.grado.size === 0
export const gradoElegido = (f: Filtros, grado: string): boolean => f.grado.has(grado) && f.color.size === 0

/** Clic en una casilla: filtra por ese color y ese grado, y volver a pulsarla lo quita. Con Ctrl (o Cmd) suma su color y su
 *  grado a lo elegido, como el prototipo: Grafito A + Oro B deja Grafito y Oro en A y B. */
export function pulsarCasilla(f: Filtros, color: string, grado: string, sumar: boolean): Filtros {
  if (sumar) return { ...f, color: new Set([...f.color, color]), grado: new Set([...f.grado, grado]) }
  if (solo(f.color, color) && solo(f.grado, grado)) return { ...f, color: new Set(), grado: new Set() }
  return { ...f, color: new Set([color]), grado: new Set([grado]) }
}

/** Clic en un color de la cabecera: solo ese color, de cualquier grado; volver a pulsarlo lo quita. */
export function pulsarColor(f: Filtros, color: string): Filtros {
  if (solo(f.color, color) && f.grado.size === 0) return { ...f, color: new Set() }
  return { ...f, color: new Set([color]), grado: new Set() }
}

/** Clic en un grado de la cabecera: solo ese grado, de cualquier color; volver a pulsarlo lo quita. */
export function pulsarGrado(f: Filtros, grado: string): Filtros {
  if (solo(f.grado, grado) && f.color.size === 0) return { ...f, grado: new Set() }
  return { ...f, grado: new Set([grado]), color: new Set() }
}
```

Run: `npx vitest run src/modules/telefonos/stock/modelo.test.ts`
Expected: PASS.

- [x] **Step 3: Tests de la pantalla (fallan)**

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`:

1. Sustituye los ayudantes `filas` y `colorAzul` por:

```tsx
const filas = () => within(screen.getByRole('region', { name: 'Tabla de móviles' })).getAllByRole('row').slice(1)
const primeraFila = async () => (await within(await screen.findByRole('region', { name: 'Tabla de móviles' })).findAllByRole('row'))[1]
const matriz = () => screen.getByRole('table', { name: 'Color y grado' })
const colorAzul = () => within(matriz()).getByRole('button', { name: 'Color Azul' })
```

2. En «al elegir un modelo salen sus móviles y los filtros de un clic se combinan», cambia el título por «al elegir un modelo
   salen sus móviles y los filtros se combinan» y sustituye la línea
   `expect(within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Rosa/ })).toBeDisabled()` por:

```tsx
    // La tabla color × grado respeta los demás filtros: en Listos no hay ningún Rosa, así que su fila desaparece.
    expect(within(matriz()).queryByRole('button', { name: 'Color Rosa' })).not.toBeInTheDocument()
```

3. En «un grado vacío se ve como raya en la tabla» y «el grado lleva su color…», cambia `celda((await screen.findAllByRole('row'))[1], 'Grado')`
   por `celda(await primeraFila(), 'Grado')`.
4. Sustituye el test «los chips de color y grado se leen con un espacio entre el nombre y el número» por:

```tsx
  it('la tabla color × grado cuenta el stock y filtra por casilla, color, grado y Ctrl+clic', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'iPhone 13: 3 en stock' }))
    expect(screen.queryByRole('group', { name: 'Color' })).not.toBeInTheDocument()
    // Del iPhone 13 en stock: Azul A (001), Rosa B (006, 256 GB) y Rosa n/a (003, en revisión).
    expect(within(matriz()).getByRole('button', { name: 'Azul A: 1' })).toHaveAttribute('title', 'Azul A: 1 listos · 0 en taller · 0 en revisión')
    expect(within(matriz()).getByRole('button', { name: 'Rosa n/a: 1' })).toHaveAttribute('title', 'Rosa n/a: 0 listos · 0 en taller · 1 en revisión')
    await user.click(within(matriz()).getByRole('button', { name: 'Rosa B: 1' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000006'])
    expect(within(matriz()).getByRole('button', { name: 'Azul A: 1' })).toBeInTheDocument()
    await user.click(within(matriz()).getByRole('button', { name: 'Rosa B: 1' }))
    expect(filas()).toHaveLength(3)
    await user.click(within(matriz()).getByRole('button', { name: 'Color Rosa' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000006', '350000000000003'])
    await user.click(within(matriz()).getByRole('button', { name: 'Grado A' }))
    expect(filas().map(imeiDe)).toEqual(['350000000000001'])
    await user.click(within(matriz()).getByRole('button', { name: 'Rosa B: 1' }))
    await user.keyboard('{Control>}')
    await user.click(within(matriz()).getByRole('button', { name: 'Azul A: 1' }))
    await user.keyboard('{/Control}')
    expect(filas().map(imeiDe)).toEqual(['350000000000006', '350000000000001'])
    expect(screen.getByRole('button', { name: 'Quitar filtros (4)' })).toBeInTheDocument()
  })

  it('«Quitar filtros» cuenta también el SKU y lo quita todo', async () => {
    const user = userEvent.setup()
    abrir()
    await screen.findByText('4 móviles en stock')
    await user.type(screen.getByRole('textbox', { name: 'Buscar modelo o SKU' }), 'i13{Enter}')
    await user.click(colorAzul())
    await user.click(screen.getByRole('button', { name: 'Quitar filtros (2)' }))
    expect(screen.queryByTestId('pastilla-sku')).not.toBeInTheDocument()
    expect(filas()).toHaveLength(3)
  })
```

Run: `npx vitest run src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no hay tabla «Color y grado» ni región «Tabla de móviles»).

- [x] **Step 4: Pantalla**

Crea `src/modules/telefonos/stock/MatrizColorGrado.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { BarraReparto } from './BarraReparto'
import {
  casilla, casillaElegida, colorElegido, gradoElegido, matriz, muestraColor, pulsarCasilla, pulsarColor, pulsarGrado, tituloCasilla,
  type Filtros, type Movil,
} from './modelo'
import { PildoraGrado } from './PildoraGrado'

type Props = { moviles: Movil[]; filtros: Filtros; onCambiar: (f: Filtros) => void }

const TOTAL = 'bg-pill-bg text-center font-bold text-azul-medio'

/** La tabla color × grado (0.9.18, spec §4.4), en lugar de los chips de color y de grado: una fila por color, una columna
 *  por grado y cada casilla con su número y la barrita listos / taller / revisión. Cuenta solo el stock y respeta los demás
 *  filtros, pero no su propia selección: se ve entera, con lo elegido resaltado. */
export function MatrizColorGrado({ moviles, filtros, onCambiar }: Props) {
  const m = matriz(moviles, filtros)
  if (m.colores.length === 0) return <p className="px-1.5 py-5 text-[11.5px] text-azul-gris">No hay móviles en stock con estos filtros.</p>
  return (
    <table aria-label="Color y grado" className="border-separate border-spacing-0 text-[12.5px] text-azul-medio">
      <thead>
        <tr>
          <th scope="col" className="border-b border-fila-sep px-1.5 py-1 text-left text-[11px] font-semibold text-azul-gris">color \ grado</th>
          {m.grados.map((g) => (
            <th key={g} scope="col" className={cn('border-b border-fila-sep px-1 py-1 text-center', gradoElegido(filtros, g) && 'bg-azul-noche')}>
              <button type="button" aria-label={`Grado ${g}`} aria-pressed={gradoElegido(filtros, g)} onClick={() => onCambiar(pulsarGrado(filtros, g))} className="cursor-pointer">
                <PildoraGrado grado={g} />
              </button>
            </th>
          ))}
          <th scope="col" className={cn('w-[50px] border-b border-fila-sep', TOTAL)}>Total</th>
        </tr>
      </thead>
      <tbody>
        {m.colores.map((c) => (
          <tr key={c}>
            <th scope="row" className={cn('border-b border-pill-bg py-0.5 pr-2.5 pl-1 text-left font-semibold whitespace-nowrap', colorElegido(filtros, c) && 'bg-azul-noche text-superficie')}>
              <button type="button" aria-label={`Color ${c}`} aria-pressed={colorElegido(filtros, c)} onClick={() => onCambiar(pulsarColor(filtros, c))}
                className="inline-flex cursor-pointer items-center gap-1.5">
                <span aria-hidden className="h-2.5 w-2.5 rounded-full border border-pill-borde" style={{ background: muestraColor(c) }} />
                {c}
              </button>
            </th>
            {m.grados.map((g) => {
              const n = casilla(m, c, g)
              const elegida = casillaElegida(filtros, c, g)
              return (
                <td key={g} className={cn('relative h-[29px] w-12 border-b border-pill-bg p-0 text-center', elegida && 'bg-azul-noche text-superficie')}>
                  {n.total === 0 ? <span aria-hidden className="text-texto-suave">·</span> : (
                    <button type="button" aria-label={`${c} ${g}: ${n.total}`} aria-pressed={elegida} title={tituloCasilla(c, g, n)}
                      onClick={(e) => onCambiar(pulsarCasilla(filtros, c, g, e.ctrlKey || e.metaKey))}
                      className={cn('h-full w-full cursor-pointer font-bold', !elegida && 'hover:bg-seleccion-suave')}>
                      {n.total}
                      <BarraReparto reparto={n.reparto} className="absolute inset-x-[9px] bottom-1 h-[3px] rounded-sm" />
                    </button>
                  )}
                </td>
              )
            })}
            <td className={cn('border-b border-pill-bg', TOTAL)}>{m.porColor.get(c) ?? 0}</td>
          </tr>
        ))}
        <tr>
          <th scope="row" className={cn('px-1 text-left', TOTAL)}>Total</th>
          {m.grados.map((g) => <td key={g} className={TOTAL}>{m.porGrado.get(g) ?? 0}</td>)}
          <td className={TOTAL}>{m.total}</td>
        </tr>
      </tbody>
    </table>
  )
}
```

Sustituye `src/modules/telefonos/stock/FiltrosMoviles.tsx` entero por:

```tsx
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { esEspecialTecnico, opciones, type Dimension, type Filtros, type Movil } from './modelo'

type Props = { moviles: Movil[]; filtros: Filtros; onCambiar: (f: Filtros) => void }

/** A la derecha de la tabla color × grado (0.9.18, spec §4.4, §4.5 y §4.7): la ayuda, los desplegables de batería,
 *  ubicación y técnico (cada opción con cuántos móviles quedarían con los demás filtros puestos) y los avisos de «Quién lo
 *  tiene», que cuentan los móviles que se ven del modelo (con el interruptor apagado, solo el stock). */
export function FiltrosMoviles({ moviles, filtros, onCambiar }: Props) {
  const desplegable = (dim: Dimension, titulo: string, todas: string) => {
    const lista = opciones(moviles, dim, filtros)
    return (
      <MultiSelect opciones={lista} clave={(o) => o.valor} etiqueta={(o) => `${o.valor} (${o.cuenta})`}
        seleccion={new Set(filtros[dim])} onChange={(s) => onCambiar({ ...filtros, [dim]: s })}
        textoVacio={`${titulo}: ${todas}`} textoPlural={(n) => `${titulo}: ${n}`} etiquetaAccesible={titulo} className="h-8 w-full min-w-0"
        separadorAntes={dim === 'tecnico' ? lista.find((o) => esEspecialTecnico(o.valor))?.valor : undefined} />
    )
  }
  const enCaja = moviles.filter((m) => m.quien.enCaja).length
  const descuadres = moviles.filter((m) => m.quien.descuadre).length
  return (
    <div className="flex w-[220px] shrink-0 flex-col gap-2">
      <p className="text-[11.5px] leading-snug text-azul-gris">
        Pulsa una casilla para ver ese color y grado; el color o el grado de la cabecera, para ver solo eso. <b>Ctrl+clic</b> suma casillas.
      </p>
      {desplegable('bateria', 'Batería', 'todas')}
      {desplegable('ubicacion', 'Ubicación', 'todas')}
      {desplegable('tecnico', 'Técnico', 'todos')}
      {enCaja > 0 && <p className="text-[12px] font-bold text-fila-solicitud-brd">{`⚠ ${enCaja} en caja de taller sin asignar`}</p>}
      {descuadres > 0 && <p className="text-[12px] font-bold text-fila-urgente-brd">{`⚠ ${descuadres} con descuadre`}</p>}
    </div>
  )
}
```

En `src/modules/telefonos/stock/ListaModelos.tsx`, cambia la clase del `<nav>` por
`"flex min-h-0 flex-col overflow-hidden rounded-md bg-superficie"`: ahora la alta la pone la rejilla de la página.

Sustituye `src/modules/telefonos/stock/StockTelefonosPage.tsx` entero por:

```tsx
import { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { DataTable } from '@/shared/ui/DataTable'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useStockTelefonos } from '../api'
import { filtrarModelos, sugerenciasSku, type Destino } from './buscador'
import { BuscadorImei } from './BuscadorImei'
import { BuscadorModelo } from './BuscadorModelo'
import { columnasMoviles } from './columnas'
import { claveFiltros, estadoStockTelefonos } from './estado'
import { FiltrosMoviles } from './FiltrosMoviles'
import { ListaModelos } from './ListaModelos'
import { MatrizColorGrado } from './MatrizColorGrado'
import {
  capacidadDe, contarFiltros, cuentaComoStock, FILTROS_VACIOS, modeloDe, modelosLista, ordenarTabla, pasa, pasaSku, pestanasCapacidad,
  preparar, type Filtros, type FiltroSku,
} from './modelo'
import { PastillaSku } from './PastillaSku'
import { PestanasCapacidad } from './PestanasCapacidad'
import { BandaNsys, PildoraNsys } from './PildoraNsys'

const PIE = '"Quién lo tiene" sale de las asignaciones abiertas del ERP, al momento; la caja, de la última exportación de NSYS. Pasa el ratón por encima para ver desde cuándo. El IMEI abre el detalle del móvil.'

/** «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; 0.9.18, spec «Columna compacta, SKU, Quién lo
 *  tiene y Revisado» §4): una fila por modelo a la izquierda, con el buscador de modelo o SKU, y a la derecha el modelo con
 *  sus pestañas de capacidad, la tabla color × grado, los filtros y sus móviles. Ocupa la ventana sin scroll de página: el
 *  scroll va dentro de la columna y de la tabla. Solo lectura: los datos vienen del espejo de NSYS y de las asignaciones
 *  abiertas del ERP. Un móvil abre su detalle de IMEIs, con la ficha de NSYS y el rastro. */
export function StockTelefonosPage() {
  const navigate = useNavigate()
  const stock = useStockTelefonos()
  // «Quién lo tiene» se calcula a la hora de la carga (dataUpdatedAt): el sondeo de 60 s lo vuelve a calcular.
  const moviles = useMemo(() => preparar(stock.data ?? [], stock.dataUpdatedAt), [stock.data, stock.dataUpdatedAt])
  const sinCargar = stock.isError && !stock.data
  const [{ verNoCuenta, textoModelo, elegido, capacidad: capacidadElegida, filtrosDe }, setEstado] = useStore(estadoStockTelefonos)
  const lista = useMemo(() => modelosLista(moviles, verNoCuenta), [moviles, verNoCuenta])
  const sugerencias = useMemo(() => sugerenciasSku(moviles, textoModelo), [moviles, textoModelo])
  const visibles = filtrarModelos(lista, textoModelo, sugerencias)
  // El modelo abierto no depende de lo escrito en el buscador: solo cambia al elegir otro (spec §4.2).
  const modelo = lista.find((g) => g.clave === elegido) ?? lista[0] ?? null
  const todos = useMemo(() => (modelo ? moviles.filter((m) => modeloDe(m) === modelo.clave) : []), [moviles, modelo])
  const pestanas = pestanasCapacidad(todos, verNoCuenta)
  const capacidad = modelo !== null && modelo.clave === elegido && pestanas.some((p) => p.capacidad !== null && p.capacidad === capacidadElegida && p.activa)
    ? capacidadElegida : null
  const clave = modelo ? claveFiltros(modelo.clave, capacidad) : null
  const { filtros, sku } = filtrosDe.clave === clave ? filtrosDe : { filtros: FILTROS_VACIOS, sku: null }
  const cambiarFiltros = (f: Filtros, s: FiltroSku | null) => setEstado((e) => ({ ...e, filtrosDe: { clave, filtros: f, sku: s } }))
  const abrirModelo = (d: Destino) => {
    // Volver a pulsar lo que ya está abierto no borra sus filtros; elegir un SKU sí los cambia.
    if (d.sku === null && d.modelo === modelo?.clave && d.capacidad === capacidad) return
    setEstado((e) => ({ ...e, elegido: d.modelo, capacidad: d.capacidad, filtrosDe: { clave: claveFiltros(d.modelo, d.capacidad), filtros: FILTROS_VACIOS, sku: d.sku } }))
  }
  const delModelo = useMemo(
    () => (modelo?.moviles ?? []).filter((m) => (capacidad === null || capacidadDe(m) === capacidad) && pasaSku(m, sku)),
    [modelo, capacidad, sku],
  )
  const filas = useMemo(() => ordenarTabla(delModelo.filter((m) => pasa(m, filtros))), [delModelo, filtros])
  const activos = contarFiltros(filtros) + (sku ? 1 : 0)
  const enStock = moviles.filter(cuentaComoStock).length
  const abrir = useCallback((imeiSn: string) => navigate(`/reparaciones/imeis/${encodeURIComponent(imeiSn)}`, { state: { desde: '/telefonos/stock' } }), [navigate])
  const columnas = useMemo(() => columnasMoviles(abrir, { conCapacidad: capacidad === null, sku }), [abrir, capacidad, sku])
  return (
    <div className="flex h-[calc(100vh-var(--spacing-navbar))] flex-col p-10 max-[1500px]:p-5">
      <div className="mb-2 flex shrink-0 flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Stock de teléfonos</h1>
        {!sinCargar && <PildoraContador texto={`${enStock} ${enStock === 1 ? 'móvil' : 'móviles'} en stock`} />}
        <PildoraNsys />
      </div>
      <BandaNsys />
      <div className="mb-3 flex shrink-0 flex-wrap items-center gap-4">
        <BuscadorImei onElegir={abrir} />
        <button type="button" role="switch" aria-checked={verNoCuenta} onClick={() => setEstado((e) => ({ ...e, verNoCuenta: !e.verNoCuenta }))}
          className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-azul-medio">
          <span className={cn('relative h-[18px] w-[34px] rounded-full transition-colors', verNoCuenta ? 'bg-verde-ok' : 'bg-pill-borde')}>
            <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-superficie transition-[left]', verNoCuenta ? 'left-[18px]' : 'left-[2px]')} />
          </span>
          Ver también lo que no cuenta como stock
        </button>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-[270px_1fr] gap-3 max-[1500px]:grid-cols-[230px_1fr]">
        <ListaModelos modelos={visibles} elegido={modelo?.clave ?? null} capacidad={capacidad}
          onAbrir={(m, c) => abrirModelo({ modelo: m, capacidad: c, sku: null })}
          buscador={<BuscadorModelo lista={lista} moviles={moviles} texto={textoModelo} sugerencias={sugerencias}
            onTexto={(t) => setEstado((e) => ({ ...e, textoModelo: t }))} onAbrir={abrirModelo} />} />
        <section aria-label="Móviles del modelo" className="flex min-h-0 min-w-0 flex-col gap-2 rounded-md bg-superficie p-3">
          {modelo && (
            <>
              <div className="flex shrink-0 flex-wrap items-center gap-2.5">
                <h2 className="text-base font-bold text-azul-noche">{modelo.clave}</h2>
                <PestanasCapacidad pestanas={pestanas} elegida={capacidad} onElegir={(c) => abrirModelo({ modelo: modelo.clave, capacidad: c, sku: null })} />
                <PildoraContador texto={`${filas.length} ${filas.length === 1 ? 'móvil' : 'móviles'}`} />
                {sku && <PastillaSku sku={sku} onQuitar={() => cambiarFiltros(filtros, null)} />}
                {activos > 0 && (
                  <button type="button" onClick={() => cambiarFiltros(FILTROS_VACIOS, null)} className="cursor-pointer text-[12px] font-bold text-texto-accion hover:underline">
                    Quitar filtros ({activos})
                  </button>
                )}
              </div>
              <div className="flex shrink-0 items-start gap-4 overflow-x-auto">
                <MatrizColorGrado moviles={delModelo} filtros={filtros} onCambiar={(f) => cambiarFiltros(f, sku)} />
                <FiltrosMoviles moviles={delModelo} filtros={filtros} onCambiar={(f) => cambiarFiltros(f, sku)} />
              </div>
              <div role="region" aria-label="Tabla de móviles" className="min-h-0 flex-1">
                <DataTable columns={columnas} data={filas} vacio="Ningún móvil con estos filtros" ajuste="fluido" alturaMax="100%"
                  getRowId={(m) => `${m.imeiSn}|${m.lote}`} onAbrir={(m) => abrir(m.imeiSn)}
                  filaClase={(m) => cn('border-l-4', 'border-l-transparent', !cuentaComoStock(m) && 'text-texto-suave')} />
              </div>
              <p className="shrink-0 text-[11.5px] text-azul-gris">{PIE}</p>
            </>
          )}
          {!modelo && sinCargar && <p className="p-4 text-[12px] text-azul-gris">No se pudo cargar el stock de teléfonos.</p>}
          {!modelo && !sinCargar && stock.data && <p className="p-4 text-[12px] text-azul-gris">No hay móviles en stock.</p>}
        </section>
      </div>
    </div>
  )
}
```

`muestraColor` ya no la usa nadie fuera de `modelo.ts`, `columnas.tsx` y `MatrizColorGrado.tsx`: se queda. Comprueba que no
queda ninguna referencia a los chips: `grep -rn "group', { name: 'Color'\|aria-label={dim === 'color'" src` solo da el test
que comprueba que ya no están.

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 6: Comprobación visual con el prototipo**

La medida real (sin scroll de página ni lateral a 1366×768 y a 1920×1080, spec §4.6) se comprueba en preprod con datos reales
(Task 15). Aquí basta con revisar el código frente al prototipo: la tabla color × grado y la columna de filtros en una fila,
la tabla de móviles debajo ocupando el resto y la columna lateral con su propio scroll.

- [x] **Step 7: Commit**

```bash
git add src/modules/telefonos
git commit -m "feat: tabla color por grado en lugar de los chips y stock de telefonos sin scroll de pagina (0.9.18)"
```

---

### Task 10: Web — pegar varios IMEIs

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/varios.ts`, `varios.test.ts`
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/ComprobarImeis.tsx`, `ComprobarImeis.test.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/BuscadorImei.tsx`, `StockTelefonosPage.tsx`
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`

**Interfaces:**
- Consumes: `useComprobarImeis(imeis)` y `MAX_IMEIS` (Task 5); `quienLoTiene` y `CeldaQuien` (Task 6); `PildoraGrado`;
  `copiarAlPortapapeles` (`shared/ui/copiar.ts`) y `avisar` (`shared/ui/avisos.ts`).
- Produces:
  - `varios.ts`:
    - `esImei(t)`, `trocear(texto)`, `imeisEn(texto)`;
    - tipos `Linea` (`NO_IMEI` | `REPETIDO` con `de` | `IMEI` con `imei`), `Fila = { linea; movil }` y `Cifra = { texto; tono }`;
    - `analizar(texto) → { lineas, imeis }`, `cruzar(lineas, respuesta) → Fila[]`, `textoEstado(fila)`,
      `resumen(filas) → Cifra[]`, `modeloYCapacidad(m)` y `paraExcel(filas, ahora)`.
  - `ComprobarImeis({ abierto, textoInicial, onCerrar, onAbrirImei })`.
  - `BuscadorImei({ onElegir, onPegarVarios })`: pegar más de un IMEI abre el diálogo con lo pegado.

- [x] **Step 1: Tests de las reglas (fallan)**

Crea `src/modules/telefonos/stock/varios.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { AHORA, movilComprobado, noEncontrado, trabajo } from '../test/fabrica'
import { analizar, cruzar, esImei, imeisEn, paraExcel, resumen, textoEstado, trocear } from './varios'

describe('pegar varios IMEIs (spec 0.9.18 §4.9)', () => {
  it('trocea lo pegado: líneas, comas, puntos y comas, tabuladores y una columna de Excel', () => {
    expect(trocear('350000000000001\r\n350000000000002, 350000000000003;350000000000004\t350000000000005\n\n')).toEqual([
      '350000000000001', '350000000000002', '350000000000003', '350000000000004', '350000000000005',
    ])
  })

  it('un IMEI son 15 cifras; un número de serie, de 8 a 20 letras y cifras, con alguna de cada', () => {
    expect(esImei('350000000000001')).toBe(true)
    expect(esImei('F2LXABC123')).toBe(true)
    expect(esImei('35000000000001')).toBe(false)
    expect(esImei('12345678')).toBe(false)
    expect(esImei('pendiente')).toBe(false)
    expect(esImei('F2L1')).toBe(false)
  })

  it('cada línea: no es un IMEI, repetido (con la línea del primero) o un IMEI que preguntar; las series en mayúsculas', () => {
    const { lineas, imeis } = analizar('350000000000001\nf2lxabc123\npendiente\n350000000000001\nF2LXABC123')
    expect(lineas).toEqual([
      { n: 1, texto: '350000000000001', tipo: 'IMEI', imei: '350000000000001' },
      { n: 2, texto: 'f2lxabc123', tipo: 'IMEI', imei: 'F2LXABC123' },
      { n: 3, texto: 'pendiente', tipo: 'NO_IMEI' },
      { n: 4, texto: '350000000000001', tipo: 'REPETIDO', de: 1 },
      { n: 5, texto: 'F2LXABC123', tipo: 'REPETIDO', de: 2 },
    ])
    expect(imeis).toEqual(['350000000000001', 'F2LXABC123'])
    expect(imeisEn('350000000000001 hola 350000000000002')).toBe(2)
  })

  it('el estado de cada línea con la respuesta del servidor', () => {
    const { lineas } = analizar('350000000000001 350000000000002 350000000000003 350000000000004 350000000000005 350000000000006 350000000000007 x')
    const filas = cruzar(lineas, [
      movilComprobado({ imei: '350000000000001' }),
      noEncontrado('350000000000002'),
      movilComprobado({ imei: '350000000000003', estado: 'DE_PEDIDO' }),
      movilComprobado({ imei: '350000000000004', estado: 'NO_CUENTA' }),
      movilComprobado({ imei: '350000000000005', estado: 'VENDIDO', fechaVenta: '2026-10-02' }),
      movilComprobado({ imei: '350000000000006', estado: 'VENDIDO', fechaVenta: null }),
      movilComprobado({ imei: '350000000000007', estado: 'BAJA' }),
    ])
    expect(filas.map(textoEstado)).toEqual([
      'Libre', 'No está en NSYS', 'De pedido', 'No cuenta', 'Vendido 02/10/2026', 'Vendido', 'Baja en NSYS', 'No es un IMEI',
    ])
  })

  it('el resumen: siempre «N en stock» y lo demás si lo hay, en singular o en plural', () => {
    const { lineas } = analizar('350000000000001 350000000000002 350000000000003 350000000000001 x y')
    const filas = cruzar(lineas, [
      movilComprobado({ imei: '350000000000001' }), noEncontrado('350000000000002'),
      movilComprobado({ imei: '350000000000003', estado: 'VENDIDO', fechaVenta: '2026-10-02' }),
    ])
    expect(resumen(filas)).toEqual([
      { texto: '1 en stock', tono: 'ok' }, { texto: '1 vendido', tono: 'aviso' }, { texto: '1 no está en NSYS', tono: 'mal' },
      { texto: '1 repetido', tono: 'neutro' }, { texto: '2 no son un IMEI', tono: 'neutro' },
    ])
    expect(resumen([])).toEqual([{ texto: '0 en stock', tono: 'ok' }])
  })

  it('la tabla para Excel: separada por tabuladores, con cabecera y «Quién lo tiene»', () => {
    const { lineas } = analizar('350000000000001\nx')
    const filas = cruzar(lineas, [movilComprobado({ imei: '350000000000001', ubicacion: 'Taller · Reparación', trabajos: [trabajo({ tecnico: 'Técnico 1' })] })])
    expect(paraExcel(filas, AHORA)).toBe([
      '#\tIMEI\tModelo\tColor\tGrado\tUbicación\tQuién lo tiene\tEstado',
      '1\t350000000000001\tiPhone 13 128GB\tAzul\tA\tTaller · Reparación\tTécnico 1\tLibre',
      '2\tx\t\t\t\t\t\tNo es un IMEI',
    ].join('\n'))
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/varios.test.ts`
Expected: FAIL (no existe `varios.ts`).

- [x] **Step 2: Reglas puras**

Crea `src/modules/telefonos/stock/varios.ts`:

```ts
import type { MovilComprobado } from '@/shared/api/client'
import { diaNsys, nombreModeloNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { quienLoTiene } from './quien'

/** «Pegar varios IMEIs» (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.9): partir lo pegado, marcar
 *  cada línea, juntarla con la respuesta del servidor y resumir. */

/** Un IMEI son 15 cifras; un número de serie, de 8 a 20 letras y cifras con alguna de cada (así «pendiente» o un IMEI al
 *  que le falta una cifra no pasan por número de serie). */
export function esImei(texto: string): boolean {
  return /^\d{15}$/.test(texto) || /^(?=.*\d)(?=.*[A-Za-z])[A-Za-z0-9]{8,20}$/.test(texto)
}

/** Los trozos de lo pegado: uno por línea o separados por comas, puntos y comas, tabuladores o espacios (una columna de
 *  Excel llega con un salto de línea por celda). */
export function trocear(texto: string): string[] {
  return texto.split(/[\s,;]+/).filter(Boolean)
}

/** Cuántos IMEIs (o números de serie) hay en un texto: con más de uno, el buscador abre el diálogo. */
export const imeisEn = (texto: string): number => trocear(texto).filter(esImei).length

export type Linea =
  | { n: number; texto: string; tipo: 'NO_IMEI' }
  | { n: number; texto: string; tipo: 'REPETIDO'; de: number }
  | { n: number; texto: string; tipo: 'IMEI'; imei: string }
export type Fila = { linea: Linea; movil: MovilComprobado | null }
export type Cifra = { texto: string; tono: 'ok' | 'aviso' | 'mal' | 'neutro' }

/** Marca cada trozo: no es un IMEI, repetido (con la línea del primero) o un IMEI que preguntar, en mayúsculas para que un
 *  número de serie en minúsculas cuente igual. `imeis`: los que se preguntan, sin repetir y en orden. */
export function analizar(texto: string): { lineas: Linea[]; imeis: string[] } {
  const vistos = new Map<string, number>()
  const lineas = trocear(texto).map((t, i): Linea => {
    const n = i + 1
    if (!esImei(t)) return { n, texto: t, tipo: 'NO_IMEI' }
    const imei = t.toUpperCase()
    const primero = vistos.get(imei)
    if (primero !== undefined) return { n, texto: t, tipo: 'REPETIDO', de: primero }
    vistos.set(imei, n)
    return { n, texto: t, tipo: 'IMEI', imei }
  })
  return { lineas, imeis: [...vistos.keys()] }
}

/** Junta las líneas con la respuesta del servidor, que trae una entrada por IMEI pedido. */
export function cruzar(lineas: Linea[], respuesta: MovilComprobado[]): Fila[] {
  const porImei = new Map(respuesta.map((m) => [m.imei.toUpperCase(), m]))
  return lineas.map((linea) => ({ linea, movil: linea.tipo === 'IMEI' ? porImei.get(linea.imei) ?? null : null }))
}

export function textoEstado({ linea, movil }: Fila): string {
  if (linea.tipo === 'NO_IMEI') return 'No es un IMEI'
  if (linea.tipo === 'REPETIDO') return `Repetido (línea ${linea.de})`
  if (!movil?.encontrado) return 'No está en NSYS'
  if (movil.estado === 'VENDIDO') return movil.fechaVenta ? `Vendido ${diaNsys(movil.fechaVenta)}` : 'Vendido'
  if (movil.estado === 'NO_CUENTA') return 'No cuenta'
  return textoEstadoMovil(movil.estado ?? '')
}

const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`

/** El resumen de arriba: siempre «N en stock» y lo demás solo si lo hay. */
export function resumen(filas: Fila[]): Cifra[] {
  const k = { stock: 0, pedido: 0, noCuenta: 0, vendidos: 0, baja: 0, noEstan: 0, repetidos: 0, noImei: 0 }
  for (const { linea, movil } of filas) {
    if (linea.tipo === 'NO_IMEI') k.noImei++
    else if (linea.tipo === 'REPETIDO') k.repetidos++
    else if (!movil?.encontrado) k.noEstan++
    else if (movil.estado === 'STOCK') k.stock++
    else if (movil.estado === 'DE_PEDIDO') k.pedido++
    else if (movil.estado === 'NO_CUENTA') k.noCuenta++
    else if (movil.estado === 'VENDIDO') k.vendidos++
    else k.baja++
  }
  const cifras: (Cifra | null)[] = [
    { texto: `${k.stock} en stock`, tono: 'ok' },
    k.pedido > 0 ? { texto: `${k.pedido} de pedido`, tono: 'aviso' } : null,
    k.noCuenta > 0 ? { texto: plural(k.noCuenta, 'no cuenta', 'no cuentan'), tono: 'aviso' } : null,
    k.vendidos > 0 ? { texto: plural(k.vendidos, 'vendido', 'vendidos'), tono: 'aviso' } : null,
    k.baja > 0 ? { texto: `${k.baja} de baja`, tono: 'neutro' } : null,
    k.noEstan > 0 ? { texto: plural(k.noEstan, 'no está en NSYS', 'no están en NSYS'), tono: 'mal' } : null,
    k.repetidos > 0 ? { texto: plural(k.repetidos, 'repetido', 'repetidos'), tono: 'neutro' } : null,
    k.noImei > 0 ? { texto: plural(k.noImei, 'no es un IMEI', 'no son un IMEI'), tono: 'neutro' } : null,
  ]
  return cifras.filter((c): c is Cifra => c !== null)
}

/** «iPhone 13 128GB». */
export const modeloYCapacidad = (m: MovilComprobado): string => [nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad].filter(Boolean).join(' ')

const CABECERA_EXCEL = ['#', 'IMEI', 'Modelo', 'Color', 'Grado', 'Ubicación', 'Quién lo tiene', 'Estado']
const celda = (t: string) => t.replace(/[\t\r\n]+/g, ' ')

/** El resultado como tabla separada por tabuladores, para pegarlo en Excel. `ahora`: la hora de la respuesta (ms), para
 *  «Quién lo tiene». */
export function paraExcel(filas: Fila[], ahora: number): string {
  const lineas = filas.map((f) => {
    const m = f.movil?.encontrado ? f.movil : null
    const quien = m ? quienLoTiene({ ubicacion: m.ubicacion, estado: m.estado ?? '', trabajos: m.trabajos }, ahora).texto : ''
    return [String(f.linea.n), f.linea.texto, m ? modeloYCapacidad(m) : '', m?.color ?? '', m?.grado ?? '', m?.ubicacion ?? '', quien, textoEstado(f)]
  })
  return [CABECERA_EXCEL, ...lineas].map((columnas) => columnas.map(celda).join('\t')).join('\n')
}
```

Run: `npx vitest run src/modules/telefonos/stock/varios.test.ts`
Expected: PASS.

- [x] **Step 3: Tests del diálogo y de la pantalla (fallan)**

Crea `src/modules/telefonos/stock/ComprobarImeis.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { movilComprobado, noEncontrado } from '../test/fabrica'
import { ComprobarImeis } from './ComprobarImeis'

const abrirDialogo = (textoInicial = '', onAbrirImei = vi.fn()) =>
  renderConProviders(<ComprobarImeis abierto textoInicial={textoInicial} onCerrar={() => {}} onAbrirImei={onAbrirImei} />, { sesion: SESION_SUPER })
const dialogo = () => screen.findByRole('dialog', { name: 'Comprobar una lista de IMEIs' })
const responder = () => server.use(http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
  const { imeis } = (await request.json()) as { imeis: string[] }
  return HttpResponse.json(imeis.map((i) => (i === '350000000000099' ? noEncontrado(i) : movilComprobado({ imei: i }))))
}))

describe('Comprobar una lista de IMEIs (spec 0.9.18 §4.9)', () => {
  it('con lo pegado comprueba al abrirse, marca cada línea y resume', async () => {
    responder()
    abrirDialogo('350000000000001\n350000000000099\npendiente\n350000000000001')
    const tabla = await within(await dialogo()).findByRole('table', { name: 'Resultado' })
    const estados = within(tabla).getAllByRole('row').slice(1).map((f) => within(f).getAllByRole('cell').at(-1)?.textContent)
    expect(estados).toEqual(['Libre', 'No está en NSYS', 'No es un IMEI', 'Repetido (línea 1)'])
    const d = await dialogo()
    expect(within(d).getByText('4 líneas')).toBeInTheDocument()
    for (const cifra of ['1 en stock', '1 no está en NSYS', '1 repetido', '1 no es un IMEI']) expect(within(d).getByText(cifra)).toBeInTheDocument()
  })

  it('sin nada pegado espera a «Comprobar»; con más de 500 avisa y no pregunta', async () => {
    let pedidas = 0
    server.use(http.post('*/api/stock-telefonos/comprobar', () => {
      pedidas++
      return HttpResponse.json([])
    }))
    const user = userEvent.setup()
    abrirDialogo()
    const d = await dialogo()
    expect(within(d).queryByRole('table', { name: 'Resultado' })).not.toBeInTheDocument()
    await user.click(within(d).getByRole('textbox', { name: 'IMEIs' }))
    await user.paste(Array.from({ length: 501 }, (_, i) => String(350000000000000 + i)).join('\n'))
    await user.click(within(d).getByRole('button', { name: 'Comprobar' }))
    expect(within(d).getByText('Como mucho 500 IMEIs por vez: hay 501.')).toBeInTheDocument()
    expect(pedidas).toBe(0)
  })

  it('copia el resultado para Excel y un IMEI encontrado abre su página', async () => {
    responder()
    const user = userEvent.setup()
    const onAbrirImei = vi.fn()
    abrirDialogo('350000000000001', onAbrirImei)
    const d = await dialogo()
    await within(d).findByRole('table', { name: 'Resultado' })
    await user.click(within(d).getByRole('button', { name: 'Copiar el resultado (para Excel)' }))
    await waitFor(async () => expect(await navigator.clipboard.readText()).toMatch(/^#\tIMEI\tModelo/))
    await user.click(within(d).getByRole('button', { name: 'Abrir 350000000000001' }))
    expect(onAbrirImei).toHaveBeenCalledWith('350000000000001')
  })
})
```

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, añade `waitFor` al import de `@testing-library/react`,
`movilComprobado` al de las fábricas, y al final del `describe`:

```tsx
  it('pegar varios IMEIs en el buscador abre el diálogo con lo pegado y lo comprueba; el botón lo abre vacío', async () => {
    const user = userEvent.setup()
    const pedidos: string[][] = []
    server.use(http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
      const { imeis } = (await request.json()) as { imeis: string[] }
      pedidos.push(imeis)
      return HttpResponse.json(imeis.map((i) => movilComprobado({ imei: i })))
    }))
    abrir()
    const caja = await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' })
    await user.click(caja)
    await user.paste('350000000000001\n350000000000002')
    const dialogo = await screen.findByRole('dialog', { name: 'Comprobar una lista de IMEIs' })
    expect(within(dialogo).getByRole('textbox', { name: 'IMEIs' })).toHaveValue('350000000000001\n350000000000002')
    expect(await within(dialogo).findByText('2 en stock')).toBeInTheDocument()
    expect(pedidos).toEqual([['350000000000001', '350000000000002']])
    expect(caja).toHaveValue('')
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Comprobar una lista de IMEIs' })).not.toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Pegar varios IMEIs' }))
    const vacio = await screen.findByRole('dialog', { name: 'Comprobar una lista de IMEIs' })
    expect(within(vacio).getByRole('textbox', { name: 'IMEIs' })).toHaveValue('')
  })

  it('pegar un solo IMEI en el buscador lo deja en el buscador', async () => {
    const user = userEvent.setup()
    abrir()
    const caja = await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' })
    await user.click(caja)
    await user.paste('350000000000001')
    expect(caja).toHaveValue('350000000000001')
    expect(screen.queryByRole('dialog', { name: 'Comprobar una lista de IMEIs' })).not.toBeInTheDocument()
  })
```

Run: `npx vitest run src/modules/telefonos/stock/ComprobarImeis.test.tsx src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: FAIL (no existe `ComprobarImeis`, ni el botón ni el pegado).

- [x] **Step 4: Diálogo, buscador y pantalla**

Crea `src/modules/telefonos/stock/ComprobarImeis.tsx`:

```tsx
import { useState } from 'react'
import { clasesUbicacion } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { avisar } from '@/shared/ui/avisos'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { copiarAlPortapapeles } from '@/shared/ui/copiar'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { MAX_IMEIS, useComprobarImeis } from '../api'
import { CeldaQuien } from './CeldaQuien'
import { PildoraGrado } from './PildoraGrado'
import { quienLoTiene } from './quien'
import { analizar, cruzar, modeloYCapacidad, paraExcel, resumen, textoEstado, type Cifra } from './varios'

type Props = { abierto: boolean; textoInicial: string; onCerrar: () => void; onAbrirImei: (imeiSn: string) => void }

const TONO: Record<Cifra['tono'], string> = {
  ok: 'bg-fila-recibido-bg text-fila-recibido-brd',
  aviso: 'bg-fila-solicitud-bg text-fila-solicitud-brd',
  mal: 'bg-urgente-bg text-fila-urgente-brd',
  neutro: 'bg-badge-neutro-bg text-azul-gris',
}
const COLUMNAS = ['#', 'IMEI', 'Modelo', 'Color', 'Grado', 'Ubicación', 'Quién lo tiene', 'Estado']

/** «Comprobar una lista de IMEIs» (0.9.18, spec §4.9): se abre con el botón «Pegar varios IMEIs» o pegando varios IMEIs en
 *  el buscador, y entonces comprueba nada más abrirse. Busca en todos los móviles del espejo, también en los vendidos. */
export function ComprobarImeis({ abierto, textoInicial, onCerrar, onAbrirImei }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(o) => { if (!o) onCerrar() }}>
      <DialogContent aria-describedby={undefined} className="flex max-h-[88vh] w-[min(900px,94vw)] max-w-[min(900px,94vw)] flex-col gap-3 bg-superficie p-5">
        <DialogTitle className="text-[16px] font-bold text-azul-noche">Comprobar una lista de IMEIs</DialogTitle>
        {/* Hijo de DialogContent: Radix lo desmonta al cerrar, así que cada apertura empieza con lo que se pegó. */}
        <Contenido textoInicial={textoInicial} onAbrirImei={onAbrirImei} />
      </DialogContent>
    </Dialog>
  )
}

function Contenido({ textoInicial, onAbrirImei }: { textoInicial: string; onAbrirImei: (imeiSn: string) => void }) {
  const [texto, setTexto] = useState(textoInicial)
  // Lo que se comprueba: con lo pegado, nada más abrirse; si no, al pulsar «Comprobar».
  const [pedido, setPedido] = useState(() => (textoInicial.trim() ? analizar(textoInicial) : null))
  const demasiados = pedido !== null && pedido.imeis.length > MAX_IMEIS
  const consulta = useComprobarImeis(pedido !== null && !demasiados ? pedido.imeis : [])
  const listo = pedido !== null && !demasiados && (pedido.imeis.length === 0 || consulta.data !== undefined)
  const filas = listo ? cruzar(pedido.lineas, consulta.data ?? []) : null
  const ahora = consulta.dataUpdatedAt
  return (
    <div className="flex min-h-0 flex-col gap-2.5">
      <p className="text-[11.5px] text-azul-gris">
        Pega los IMEIs como vengan: uno por línea, separados por comas o copiados de una columna de Excel. Se buscan en todos los móviles de NSYS, también en los vendidos.
      </p>
      <textarea aria-label="IMEIs" value={texto} onChange={(e) => setTexto(e.target.value)}
        className="h-[88px] w-full rounded-lg border border-borde-input px-2.5 py-2 font-mono text-[12px]" />
      <div className="flex items-center gap-2">
        <BotonPrimario onClick={() => setPedido(analizar(texto))}>Comprobar</BotonPrimario>
        {pedido && <span className="text-[11.5px] text-azul-gris">{`${pedido.lineas.length} líneas`}</span>}
        {pedido && !demasiados && !listo && !consulta.isError && <span className="text-[11.5px] text-azul-gris">Comprobando…</span>}
        <BotonSecundario className="ml-auto" disabled={!filas} onClick={() => {
          if (!filas) return
          void copiarAlPortapapeles(paraExcel(filas, ahora))
          avisar({ texto: 'Copiado: pégalo en Excel', tipo: 'ok' })
        }}>Copiar el resultado (para Excel)</BotonSecundario>
      </div>
      {demasiados && <p className="text-[12px] font-bold text-fila-urgente-brd">{`Como mucho ${MAX_IMEIS} IMEIs por vez: hay ${pedido.imeis.length}.`}</p>}
      {filas && (
        <>
          <div className="flex flex-wrap gap-1.5">
            {resumen(filas).map((c) => <span key={c.texto} className={cn('rounded-full px-2.5 py-0.5 text-[12px] font-bold', TONO[c.tono])}>{c.texto}</span>)}
          </div>
          <div className="min-h-0 overflow-auto rounded-lg border border-fila-sep">
            <table aria-label="Resultado" className="w-full border-collapse text-[12.5px] text-azul-medio">
              <thead className="sticky top-0 bg-crema">
                <tr>{COLUMNAS.map((c) => <th key={c} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
              </thead>
              <tbody>
                {filas.map((f) => {
                  const m = f.movil?.encontrado ? f.movil : null
                  const falta = !m && f.linea.tipo === 'IMEI'
                  return (
                    <tr key={f.linea.n} onClick={m ? () => onAbrirImei(m.imei) : undefined}
                      className={cn('border-t border-fila-sep', m && 'cursor-pointer hover:bg-seleccion-suave', falta && 'bg-urgente-bg', f.linea.tipo !== 'IMEI' && 'text-texto-suave')}>
                      <td className="px-2 py-1">{f.linea.n}</td>
                      <td className="px-2 py-1 font-mono text-[11.5px]">
                        {m ? (
                          <button type="button" aria-label={`Abrir ${m.imei}`} onClick={(e) => { e.stopPropagation(); onAbrirImei(m.imei) }}
                            className="cursor-pointer text-azul-noche hover:underline">{f.linea.texto}</button>
                        ) : f.linea.texto}
                      </td>
                      <td className="px-2 py-1 whitespace-nowrap">{m ? modeloYCapacidad(m) : ''}</td>
                      <td className="px-2 py-1">{m?.color ?? ''}</td>
                      <td className="px-2 py-1">{m && <PildoraGrado grado={m.grado} />}</td>
                      <td className="px-2 py-1">{m?.ubicacion && <span className={cn(CLASES_PILDORA, clasesUbicacion(m.ubicacion))}>{m.ubicacion}</span>}</td>
                      <td className="px-2 py-1">{m && <CeldaQuien quien={quienLoTiene({ ubicacion: m.ubicacion, estado: m.estado ?? '', trabajos: m.trabajos }, ahora)} />}</td>
                      <td className={cn('px-2 py-1 font-bold whitespace-nowrap', falta && 'text-fila-urgente-brd', m && m.estado !== 'STOCK' && 'text-fila-solicitud-brd')}>{textoEstado(f)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
```

En `src/modules/telefonos/stock/BuscadorImei.tsx`:
1. Añade el import `import { imeisEn } from './varios'`.
2. Cambia la firma por `export function BuscadorImei({ onElegir, onPegarVarios }: { onElegir: (imeiSn: string) => void; onPegarVarios: (texto: string) => void }) {`.
3. En el `<input>`, tras `onChange={…}`, añade:

```tsx
        onPaste={(e) => {
          // Con más de un IMEI, lo pegado va al diálogo de «Pegar varios IMEIs» (spec 0.9.18 §4.9).
          const pegado = e.clipboardData.getData('text')
          if (imeisEn(pegado) > 1) {
            e.preventDefault()
            setAbierto(false)
            onPegarVarios(pegado)
          }
        }}
```

4. Añade al comentario de la función: «Pegar más de un IMEI abre «Comprobar una lista de IMEIs» con lo pegado.»

En `src/modules/telefonos/stock/StockTelefonosPage.tsx`:
1. Cambia el import de React por `import { useCallback, useMemo, useState } from 'react'` y añade
   `import { BotonSecundario } from '@/shared/ui/Botones'` y `import { ComprobarImeis } from './ComprobarImeis'`.
2. Tras `const [{ verNoCuenta, … }, setEstado] = useStore(estadoStockTelefonos)`, añade:

```tsx
  // El diálogo de «Pegar varios IMEIs»: null cerrado; si no, lo que se pegó (vacío con el botón).
  const [varios, setVarios] = useState<string | null>(null)
```

3. Cambia `<BuscadorImei onElegir={abrir} />` por:

```tsx
        <BuscadorImei onElegir={abrir} onPegarVarios={setVarios} />
        <BotonSecundario onClick={() => setVarios('')}>Pegar varios IMEIs</BotonSecundario>
```

4. Antes del último `</div>` de la página, añade:

```tsx
      <ComprobarImeis abierto={varios !== null} textoInicial={varios ?? ''} onCerrar={() => setVarios(null)} onAbrirImei={abrir} />
```

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 6: Commit**

```bash
git add src/modules/telefonos
git commit -m "feat: pegar varios IMEIs y comprobarlos contra el espejo de NSYS (0.9.18)"
```

---

### Task 11: Web — lista de importaciones en la píldora de NSYS e indicador en la barra superior

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/nsys.ts`, `nsys.test.ts`
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/ListaImportacionesNsys.tsx`, `PildoraNsys.test.tsx`
- Modify: `gestion-reparaciones-web/src/modules/telefonos/stock/PildoraNsys.tsx`
- Create: `gestion-reparaciones-web/src/modules/telefonos/IndicadorNsys.tsx`
- Modify: `gestion-reparaciones-web/src/app/shell/TopBar.tsx`, `TopBar.test.tsx`

**Interfaces:**
- Consumes: `useEstadoNsys(activo)`, `useImportacionesNsys()` y las fábricas `importacion`, `listaImportaciones`,
  `estadoNsys` (Task 5); `ImportacionNsys`, `ProgresoHistorico`.
- Produces:
  - `nsys.ts`: `HUECO_MINUTOS = 45`, `minutosHabiles(desde, hasta)`,
    `type FilaLista = { tipo: 'importacion'; importacion } | { tipo: 'hueco'; desde; hasta }`, `conHuecos(importaciones)`,
    `horaItems(iso, ahora?)`, `textoCambios(n)` y `textoHistorico(h)`.
  - `ListaImportacionesNsys()`; `PildoraNsys` con su popover (`aria-label` «Importaciones de NSYS»).
  - `IndicadorNsys()` en `modules/telefonos` (lo monta `app/shell/TopBar.tsx`, que puede importar de los módulos).

- [x] **Step 1: Tests de las reglas (fallan)**

En `src/modules/telefonos/stock/nsys.test.ts`, cambia los imports por:

```ts
import { describe, expect, it } from 'vitest'
import { estadoNsys, importacion } from '../test/fabrica'
import { conHuecos, horaItems, minutosDesde, minutosHabiles, textoBanda, textoCambios, textoHistorico, textoPildora, type FilaLista } from './nsys'
```

y añade al final:

```ts
describe('lista de importaciones de la píldora (spec 0.9.18 §4.11)', () => {
  it('minutos hábiles, de 07:00 a 23:00, como AvisoNsys del servidor', () => {
    expect(minutosHabiles('2026-10-16T15:31:00', '2026-10-16T16:01:00')).toBe(30)
    expect(minutosHabiles('2026-10-16T22:31:00', '2026-10-17T07:01:00')).toBe(30)
    expect(minutosHabiles('2026-10-16T22:31:00', '2026-10-17T08:01:00')).toBe(90)
    expect(minutosHabiles('2026-10-16T16:01:00', '2026-10-16T15:31:00')).toBe(0)
  })

  const items = (...filas: [string, string?][]) => filas.map(([f, resultado = 'OK']) => importacion({ fechaExportacion: f, resultado }))
  const huecos = (filas: FilaLista[]) => filas.flatMap((f) => (f.tipo === 'hueco' ? [`${f.desde.slice(11, 16)}-${f.hasta.slice(11, 16)}`] : []))

  it('30 minutos no es un hueco y 46 dentro de 07-23 sí', () => {
    expect(huecos(conHuecos(items(['2026-10-16T16:01:00'], ['2026-10-16T15:31:00'])))).toEqual([])
    const filas = conHuecos(items(['2026-10-16T16:31:00'], ['2026-10-16T15:45:00']))
    expect(filas.map((f) => f.tipo)).toEqual(['importacion', 'hueco', 'importacion'])
    expect(huecos(filas)).toEqual(['15:45-16:31'])
  })

  it('la noche no cuenta: de 22:31 a 07:01 no hay hueco y de 22:31 a 08:01 sí (29 + 61 minutos hábiles)', () => {
    expect(huecos(conHuecos(items(['2026-10-17T07:01:00'], ['2026-10-16T22:31:00'])))).toEqual([])
    expect(huecos(conHuecos(items(['2026-10-17T08:01:00'], ['2026-10-16T22:31:00'])))).toEqual(['22:31-08:01'])
  })

  it('un RECHAZADO cuenta como exportación que llegó', () => {
    expect(huecos(conHuecos(items(['2026-10-16T16:31:00'], ['2026-10-16T16:01:00', 'RECHAZADO'], ['2026-10-16T15:31:00'])))).toEqual([])
  })

  it('la hora del Items: de hoy, la hora; de otro día, también el día', () => {
    expect(horaItems('2026-10-16T13:31:00', AHORA)).toBe('13:31')
    expect(horaItems('2026-10-15T22:31:00', AHORA)).toBe('15/10 22:31')
    expect(textoCambios(1)).toBe('1 cambio')
    expect(textoCambios(12)).toBe('12 cambios')
  })

  it('la línea del histórico: sin histórico, ninguna; en marcha, hasta dónde va; acabado, terminado', () => {
    expect(textoHistorico({ total: 0, hechos: 0, ultimo: null, hasta: null })).toBeNull()
    expect(textoHistorico({ total: 140, hechos: 138, ultimo: '2025-05-03T22:31:00', hasta: '2026-10-16T14:31:00' }))
      .toBe('Histórico: 138 ficheros hechos, hasta el Items del 03/05/2025; termina en el del 16/10/2026')
    expect(textoHistorico({ total: 2, hechos: 1, ultimo: '2024-01-02T09:01:00', hasta: null })).toBe('Histórico: 1 fichero hecho, hasta el Items del 02/01/2024')
    expect(textoHistorico({ total: 990, hechos: 990, ultimo: '2026-10-16T14:31:00', hasta: '2026-10-16T14:31:00' })).toBe('Histórico: terminado (990 ficheros)')
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/nsys.test.ts`
Expected: FAIL (no existen `minutosHabiles`, `conHuecos`…).

- [x] **Step 2: Reglas puras**

En `src/modules/telefonos/stock/nsys.ts`:
1. Cambia el import de tipos por `import type { EstadoNsys, ImportacionNsys, ProgresoHistorico } from '@/shared/api/client'` y
   añade `import { diaNsys } from '@/shared/lib/telefonoNsys'`.
2. Añade al final del fichero:

```ts
const ABRE = 7 * 60
const CIERRA = 23 * 60
/** Más de estos minutos hábiles entre dos exportaciones seguidas es un hueco: falta un Items (spec 0.9.18 §4.11). */
export const HUECO_MINUTOS = 45

/** Minutos de 07:00 a 23:00 entre dos horas de Madrid, cuando el bot exporta: la misma cuenta que AvisoNsys del servidor. */
export function minutosHabiles(desde: string, hasta: string): number {
  const d = aMinutos(desde)
  const h = aMinutos(hasta)
  let total = 0
  for (let dia = Math.floor(d / 1440) * 1440; dia < h; dia += 1440) {
    const inicio = Math.max(d, dia + ABRE)
    const fin = Math.min(h, dia + CIERRA)
    if (fin > inicio) total += fin - inicio
  }
  return total
}

export type FilaLista = { tipo: 'importacion'; importacion: ImportacionNsys } | { tipo: 'hueco'; desde: string; hasta: string }

/** Intercala «falta un Items» entre dos exportaciones seguidas separadas por más de 45 minutos hábiles. Todas las filas
 *  cuentan como exportación que llegó (el fichero existía, aunque se rechazara o diera error: su resultado ya avisa). La
 *  lista llega de la más reciente a la más antigua. */
export function conHuecos(importaciones: ImportacionNsys[]): FilaLista[] {
  const filas: FilaLista[] = []
  importaciones.forEach((imp, i) => {
    filas.push({ tipo: 'importacion', importacion: imp })
    const anterior = importaciones[i + 1]
    if (anterior && minutosHabiles(anterior.fechaExportacion, imp.fechaExportacion) > HUECO_MINUTOS) {
      filas.push({ tipo: 'hueco', desde: anterior.fechaExportacion, hasta: imp.fechaExportacion })
    }
  })
  return filas
}

/** La hora del Items: «13:31» si es de hoy y «15/10 13:31» si no. */
export function horaItems(iso: string, ahora: Date = new Date()): string {
  return esHoy(iso, ahora) ? hora(iso) : `${diaMes(iso)} ${hora(iso)}`
}

export const textoCambios = (n: number): string => (n === 1 ? '1 cambio' : `${n} cambios`)

/** La línea del progreso del histórico al pie del popover; sin importaciones HISTORICO, ninguna. El histórico termina en
 *  el Items de la primera importación ACTUAL OK (`hasta`): cuando el último hecho llega ahí, está terminado. */
export function textoHistorico(h: ProgresoHistorico | null | undefined): string | null {
  if (!h || h.total === 0) return null
  if (h.ultimo && h.hasta && h.ultimo >= h.hasta) return `Histórico: terminado (${h.hechos} ${h.hechos === 1 ? 'fichero' : 'ficheros'})`
  const hechos = `${h.hechos} ${h.hechos === 1 ? 'fichero hecho' : 'ficheros hechos'}`
  return `Histórico: ${hechos}${h.ultimo ? `, hasta el Items del ${diaNsys(h.ultimo)}` : ''}${h.hasta ? `; termina en el del ${diaNsys(h.hasta)}` : ''}`
}
```

Run: `npx vitest run src/modules/telefonos/stock/nsys.test.ts`
Expected: PASS.

- [x] **Step 3: Tests de la píldora y de la barra (fallan)**

Crea `src/modules/telefonos/stock/PildoraNsys.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { importacion, listaImportaciones } from '../test/fabrica'
import { PildoraNsys } from './PildoraNsys'

const abrirPildora = () => renderConProviders(<PildoraNsys />, { sesion: SESION_SUPER })
const popover = () => screen.queryByRole('dialog', { name: 'Importaciones de NSYS' })

describe('píldora de NSYS: lista de importaciones (spec 0.9.18 §4.11)', () => {
  it('al pulsarla abre las últimas importaciones con su resultado, sus cambios, sus segundos, el motivo y los huecos', async () => {
    server.use(http.get('*/api/stock-telefonos/importaciones', () => HttpResponse.json(listaImportaciones({ importaciones: [
      importacion({ fechaExportacion: '2026-10-16T13:31:00', resultado: 'EN_CURSO', cambios: 0, segundos: null }),
      importacion({ fechaExportacion: '2026-10-16T13:01:00', resultado: 'OK', cambios: 1, segundos: 17 }),
      importacion({ fechaExportacion: '2026-10-16T12:01:00', resultado: 'RECHAZADO', cambios: 0, segundos: 2, motivo: 'Trae 10 filas…' }),
      importacion({ fechaExportacion: '2026-10-16T11:31:00', resultado: 'ERROR', cambios: 0, segundos: 4, motivo: 'Error al leer de Drive o al guardar; ver el registro del servidor' }),
    ] }))))
    const user = userEvent.setup()
    abrirPildora()
    await user.click(await screen.findByRole('button', { name: /^NSYS: / }))
    const filas = await within(await screen.findByRole('dialog', { name: 'Importaciones de NSYS' })).findAllByRole('listitem')
    expect(filas).toHaveLength(5)
    expect(filas[0]).toHaveTextContent('en curso')
    expect(within(filas[0]).getByText('en curso')).toHaveClass('bg-badge-neutro-bg')
    expect(filas[1]).toHaveTextContent('1 cambio')
    expect(filas[1]).toHaveTextContent('17 s')
    expect(within(filas[1]).getByText('OK')).toHaveClass('bg-fila-recibido-bg')
    expect(filas[2].textContent).toMatch(/^⚠ falta un Items entre (16\/10 )?12:01 y (16\/10 )?13:01$/)
    expect(within(filas[3]).getByText('RECHAZADO')).toHaveClass('bg-fila-solicitud-bg')
    expect(filas[3]).toHaveTextContent('Trae 10 filas…')
    expect(within(filas[4]).getByText('ERROR')).toHaveClass('bg-urgente-bg')
    expect(filas[4]).toHaveTextContent('Error al leer de Drive o al guardar; ver el registro del servidor')
  })

  it('pide la lista al abrirse, no la vuelve a pedir mientras sigue abierta y pide otra al volver a abrirla', async () => {
    let pedidas = 0
    server.use(http.get('*/api/stock-telefonos/importaciones', () => {
      pedidas++
      return HttpResponse.json(listaImportaciones())
    }))
    const user = userEvent.setup()
    abrirPildora()
    const boton = await screen.findByRole('button', { name: /^NSYS: / })
    await user.click(boton)
    expect(await within(await screen.findByRole('dialog', { name: 'Importaciones de NSYS' })).findAllByRole('listitem')).toHaveLength(1)
    expect(pedidas).toBe(1)
    await user.keyboard('{Escape}')
    await waitFor(() => expect(popover()).not.toBeInTheDocument())
    await user.click(boton)
    await screen.findByRole('dialog', { name: 'Importaciones de NSYS' })
    await waitFor(() => expect(pedidas).toBe(2))
    await user.click(document.body)
    await waitFor(() => expect(popover()).not.toBeInTheDocument())
  })

  it('la línea del histórico sale con histórico y no sale sin él', async () => {
    const user = userEvent.setup()
    const { unmount } = abrirPildora()
    await user.click(await screen.findByRole('button', { name: /^NSYS: / }))
    expect(await screen.findByText('Todavía no hay importaciones.')).toBeInTheDocument()
    expect(screen.queryByText(/^Histórico/)).not.toBeInTheDocument()
    unmount()
    server.use(http.get('*/api/stock-telefonos/importaciones', () => HttpResponse.json(listaImportaciones({
      historico: { total: 2, hechos: 1, ultimo: '2024-01-02T09:01:00', hasta: '2026-10-16T14:31:00' },
    }))))
    abrirPildora()
    await user.click(await screen.findByRole('button', { name: /^NSYS: / }))
    expect(await screen.findByText('Histórico: 1 fichero hecho, hasta el Items del 02/01/2024; termina en el del 16/10/2026')).toBeInTheDocument()
  })
})
```

En `src/app/shell/TopBar.test.tsx`, añade al final:

```tsx
describe('indicador de NSYS en la barra superior (0.9.18, spec §4.12)', () => {
  const estado = (nivel: string) => ({ ultimaExportacion: nivel === 'SIN_DATOS' ? null : '2026-10-16T13:31:00', nivel, ultimoFallo: null, motivoFallo: null })

  it.each([['supertécnico', SESION_SUPER], ['admin', SESION_ADMIN]])('%s: sale con SIN_DATOS y lleva a Stock de teléfonos', async (_rol, sesion) => {
    server.use(...handlersNotificaciones(), http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estado('SIN_DATOS'))))
    renderConProviders(<AppLayout />, { sesion, rutas: <Route path="/telefonos/stock" element={<p>STOCK DE TELÉFONOS</p>} /> })
    await userEvent.click(await screen.findByRole('link', { name: 'NSYS sin datos' }))
    expect(await screen.findByText('STOCK DE TELÉFONOS')).toBeInTheDocument()
  })

  it.each(['RETRASO', 'OK'])('con %s no sale', async (nivel) => {
    let pedidas = 0
    server.use(...handlersNotificaciones(), http.get('*/api/stock-telefonos/estado', () => {
      pedidas++
      return HttpResponse.json(estado(nivel))
    }))
    renderConProviders(<AppLayout />, { sesion: SESION_SUPER })
    await waitFor(() => expect(pedidas).toBe(1))
    expect(screen.queryByRole('link', { name: 'NSYS sin datos' })).not.toBeInTheDocument()
  })

  it('el técnico no pregunta el estado de NSYS ni ve el indicador', async () => {
    let pedidas = 0
    server.use(http.get('*/api/stock-telefonos/estado', () => {
      pedidas++
      return HttpResponse.json(estado('SIN_DATOS'))
    }))
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    expect(screen.getByText('FSGR:')).toBeInTheDocument()
    await new Promise((r) => setTimeout(r, 50))
    expect(pedidas).toBe(0)
    expect(screen.queryByRole('link', { name: 'NSYS sin datos' })).not.toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/PildoraNsys.test.tsx src/app/shell/TopBar.test.tsx`
Expected: FAIL (la píldora no es un botón y no hay indicador).

- [x] **Step 4: Popover, indicador y barra**

Crea `src/modules/telefonos/stock/ListaImportacionesNsys.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { CLASES_MINI_PILDORA } from '@/shared/ui/pildora'
import { useImportacionesNsys } from '../api'
import { conHuecos, horaItems, textoCambios, textoHistorico } from './nsys'

const TEXTO: Record<string, string> = { OK: 'OK', RECHAZADO: 'RECHAZADO', ERROR: 'ERROR', EN_CURSO: 'en curso' }
const CLASES: Record<string, string> = {
  OK: 'bg-fila-recibido-bg text-fila-recibido-brd',
  RECHAZADO: 'bg-fila-solicitud-bg text-fila-solicitud-brd',
  ERROR: 'bg-urgente-bg text-fila-urgente-brd',
  EN_CURSO: 'bg-badge-neutro-bg text-azul-gris',
}

/** Las últimas importaciones del Items actual (0.9.18, spec §4.11), en el popover de la píldora de NSYS: para comprobar
 *  fichero a fichero que llega un Items cada media hora sin entrar por SSH. Con los huecos de más de 45 minutos hábiles y,
 *  al pie, el progreso del histórico. Se monta al abrir el popover y pide la lista una vez. */
export function ListaImportacionesNsys() {
  const { data, isPending, isError } = useImportacionesNsys()
  const filas = conHuecos(data?.importaciones ?? [])
  const historico = textoHistorico(data?.historico)
  return (
    <div className="text-[12px] text-azul-medio">
      <h3 className="mb-2 text-[13px] font-bold text-azul-noche">Últimas importaciones de NSYS</h3>
      {isPending && <p className="text-azul-gris">Cargando…</p>}
      {isError && <p className="text-azul-gris">No se pudo cargar la lista.</p>}
      {data && filas.length === 0 && <p className="text-azul-gris">Todavía no hay importaciones.</p>}
      {filas.length > 0 && (
        <ol aria-label="Importaciones" className="max-h-[360px] overflow-auto">
          {filas.map((f, i) => f.tipo === 'hueco' ? (
            <li key={`h${i}`} className="border-b border-fila-sep py-1 font-bold text-fila-urgente-brd">
              {`⚠ falta un Items entre ${horaItems(f.desde)} y ${horaItems(f.hasta)}`}
            </li>
          ) : (
            <li key={`i${i}`} className="border-b border-fila-sep py-1">
              <div className="flex items-center gap-2">
                <span className="w-[78px] font-mono">{horaItems(f.importacion.fechaExportacion)}</span>
                <span className={cn(CLASES_MINI_PILDORA, CLASES[f.importacion.resultado] ?? CLASES.EN_CURSO)}>
                  {TEXTO[f.importacion.resultado] ?? f.importacion.resultado}
                </span>
                {f.importacion.resultado !== 'EN_CURSO' && <span>{textoCambios(f.importacion.cambios)}</span>}
                {f.importacion.segundos != null && <span className="ml-auto text-azul-gris">{`${f.importacion.segundos} s`}</span>}
              </div>
              {f.importacion.motivo && <p className="mt-0.5 text-[11px] text-azul-gris">{f.importacion.motivo}</p>}
            </li>
          ))}
        </ol>
      )}
      {historico && <p className="mt-2 text-[11.5px] text-azul-gris">{historico}</p>}
    </div>
  )
}
```

En `src/modules/telefonos/stock/PildoraNsys.tsx`:
1. Añade los imports `import { Popover, PopoverContent, PopoverTrigger } from '@/shared/ui/popover'` y
   `import { ListaImportacionesNsys } from './ListaImportacionesNsys'`.
2. Sustituye la función `PildoraNsys` por:

```tsx
/** Píldora de la cabecera con la hora de la última exportación de NSYS (spec pieza 1 §5.7); el motivo del último fallo, al
 *  pasar el ratón. Desde la 0.9.18 se puede pulsar: abre la lista de las últimas importaciones (spec §4.11), que se cierra
 *  con Escape o con un clic fuera. */
export function PildoraNsys() {
  const { data, isPending, isError } = useEstadoNsys()
  // Sin datos todavía (cargando) o sin poder preguntar (error): neutra, sin rojo ni un motivo que no sabemos.
  const nivel = data ? data.nivel : isPending || isError ? 'OK' : 'SIN_DATOS'
  const texto = data ? textoPildora(data) : isError ? 'NSYS: no se pudo comprobar' : isPending ? 'NSYS: comprobando…' : textoPildora(data)
  return (
    <Popover>
      <PopoverTrigger className="ml-auto cursor-pointer rounded-full">
        <span role="status" title={data?.motivoFallo ?? undefined}
          className={cn('inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11.5px]', CLASES[nivel] ?? CLASES.OK)}>
          <span aria-hidden className={cn('h-2 w-2 rounded-full', !data && (isPending || isError) ? 'bg-gris-borde' : (PUNTO[nivel] ?? PUNTO.OK))} />
          {texto}
        </span>
      </PopoverTrigger>
      <PopoverContent align="end" aria-label="Importaciones de NSYS" className="w-[400px] rounded-lg border border-fila-sep bg-superficie p-3 shadow-md">
        <ListaImportacionesNsys />
      </PopoverContent>
    </Popover>
  )
}
```

Crea `src/modules/telefonos/IndicadorNsys.tsx`:

```tsx
import { NavLink } from 'react-router'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdminOSuperTecnico } from '@/shared/session/storage'
import { useEstadoNsys } from './api'

/** Indicador rojo «NSYS sin datos» en la barra superior (0.9.18, spec §4.12): para ADMIN y SUPERTECNICO, en cualquier
 *  página, cuando no llegan datos de NSYS (SIN_DATOS; con RETRASO no sale, para no hacer ruido). Lleva a «Stock de
 *  teléfonos», con la banda roja y la lista de importaciones. En producción no hay correo de avisos: sin esto, si nadie
 *  abre esa página, nadie se entera de que el volcado se ha parado. El TECNICO no pregunta (el servidor le daría 403). */
export function IndicadorNsys() {
  const { sesion } = useSession()
  const permitido = esAdminOSuperTecnico(sesion)
  const { data } = useEstadoNsys(permitido)
  if (!permitido || data?.nivel !== 'SIN_DATOS') return null
  return (
    <NavLink to="/telefonos/stock" className="inline-flex items-center gap-1.5 rounded-full bg-fila-urgente-brd px-3 py-1 text-[12px] font-bold text-superficie hover:opacity-90">
      <span aria-hidden className="h-2 w-2 rounded-full bg-superficie" />
      NSYS sin datos
    </NavLink>
  )
}
```

En `src/app/shell/TopBar.tsx`:
1. Añade `import { IndicadorNsys } from '@/modules/telefonos/IndicadorNsys'`.
2. Cambia `<div className="flex-1" />` + `<Campana />` por `<div className="flex-1" />`, `<IndicadorNsys />` y `<Campana />`
   (en ese orden).
3. Añade al comentario: «Desde la 0.9.18, el aviso «NSYS sin datos» para el supertécnico y el admin.»

- [x] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/telefonos src/app/shell && npm run lint`
Expected: `tsc` limpio, todo en verde (también los tests de la 0.9.17 de la píldora en `StockTelefonosPage.test.tsx`) y
lint sin errores nuevos.

- [x] **Step 6: Commit**

```bash
git add src/modules/telefonos src/app/shell
git commit -m "feat: lista de importaciones en la pildora de NSYS e indicador NSYS sin datos en la barra (0.9.18)"
```

---

### Task 12: Web — IMEIs: «Revisado» en la ficha y en el rastro

**Files:**
- Modify: `gestion-reparaciones-web/src/shared/lib/telefonoNsys.ts` (`diasDesdeNsys`), `telefonoNsys.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/rastro.ts`, `rastro.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/FichaMovil.tsx`, `PanelRastro.tsx`, `PanelRastro.test.tsx`
- Create: `gestion-reparaciones-web/src/modules/taller/imeis/FichaMovil.test.tsx`

**Interfaces:**
- Consumes: `FichaMovil.revisadoEl / revisadoDevolucion / pendienteRevisionDesde` y `EventoRastro.revision` (Tasks 2 y 5); las
  fábricas `fichaMovil` y `eventoRastro` de `taller/test/fabrica.ts`.
- Produces:
  - `diasDesdeNsys(dia: string, ahora?: Date): number`: días desde un día de NSYS hasta hoy en Madrid (0 si es hoy o futuro).
  - `rastro.ts`: `EventoVisible` añade `revision?: boolean`; `verEvento` rotula «Revisado → …»;
    `textoRevision(ficha, ahora?) → string | null`.
  - `PanelRastro`: marca de revisión (`data-testid="marca-revision"`) en el evento y en la leyenda.

- [x] **Step 1: Tests (fallan)**

En `src/shared/lib/telefonoNsys.test.ts`, añade `diasDesdeNsys` al import y, al final del `describe`:

```ts
  it('días desde un día de NSYS, con el día de hoy en Madrid', () => {
    const ahora = new Date('2026-10-16T22:30:00Z') // 17/10 00:30 en Madrid
    expect(diasDesdeNsys('2026-10-16', ahora)).toBe(1)
    expect(diasDesdeNsys('2026-10-17', ahora)).toBe(0)
    expect(diasDesdeNsys('2026-10-20', ahora)).toBe(0)
    expect(diasDesdeNsys('2026-09-17', ahora)).toBe(30)
  })
```

En `src/modules/taller/imeis/rastro.test.ts`, cambia el import de `./rastro` por
`import { fechaRastro, hayAntesDelHistorico, textoRevision, verEvento } from './rastro'` y añade al final del `describe`:

```ts
  it('el cambio de caja que es la revisión: «Revisado → caja», con su marca (spec 0.9.18 §5.3)', () => {
    expect(verEvento(eventoRastro({ de: 'En revisión', a: 'Taller · Pulir', aNsys: 'CAJA PULIR', revision: 'ENTRADA' })))
      .toMatchObject({ titulo: 'Revisado → Taller · Pulir', detalle: 'Desde: En revisión', revision: true })
    expect(verEvento(eventoRastro({ de: 'Devolución', a: 'Listos', revision: 'DEVOLUCION' })).titulo).toBe('Revisado (devolución) → Listos')
    expect(verEvento(eventoRastro({ de: 'En revisión', a: null, aNsys: 'Desguace', revision: 'ENTRADA' })).titulo).toBe('Revisado → Desguace')
    expect(verEvento(eventoRastro()).revision).toBeFalsy()
  })

  it('la ficha: pendiente de revisar, revisado o nada (spec 0.9.18 §5.2)', () => {
    const ahora = new Date('2026-10-19T10:00:00Z')
    const sin = { revisadoEl: null, revisadoDevolucion: false, pendienteRevisionDesde: null }
    expect(textoRevision({ ...sin, pendienteRevisionDesde: '2026-10-15' }, ahora)).toBe('Pendiente de revisar desde hace 4 días')
    expect(textoRevision({ ...sin, pendienteRevisionDesde: '2026-10-18' }, ahora)).toBe('Pendiente de revisar desde hace 1 día')
    expect(textoRevision({ ...sin, pendienteRevisionDesde: '2026-10-19' }, ahora)).toBe('Pendiente de revisar desde hoy')
    expect(textoRevision({ ...sin, revisadoEl: '2026-10-17T10:31:00' }, ahora)).toBe('Revisado el 17/10/2026, hacia las 10:31')
    expect(textoRevision({ ...sin, revisadoEl: '2026-10-17T10:31:00', revisadoDevolucion: true }, ahora))
      .toBe('Revisado (devolución) el 17/10/2026, hacia las 10:31')
    expect(textoRevision(sin, ahora)).toBeNull()
  })
```

Crea `src/modules/taller/imeis/FichaMovil.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fichaMovil } from '../test/fabrica'
import { FichaMovil } from './FichaMovil'

const ficha = (p: Parameters<typeof fichaMovil>[0] = {}) => render(<FichaMovil ficha={fichaMovil(p)} eventos={3} onVerRastro={() => {}} />)

describe('ficha de NSYS: revisado (spec 0.9.18 §5.2)', () => {
  it('dice cuándo se revisó, junto a la ubicación', () => {
    ficha({ revisadoEl: '2026-10-17T10:31:00' })
    expect(screen.getByRole('region', { name: 'Ficha de NSYS' })).toHaveTextContent('Revisado el 17/10/2026, hacia las 10:31')
  })

  it('o desde cuándo espera la revisión', () => {
    ficha({ ubicacion: 'En revisión', pendienteRevisionDesde: '2026-01-01' })
    expect(screen.getByText(/^Pendiente de revisar desde hace \d+ días$/)).toBeInTheDocument()
  })

  it('sin datos, nada', () => {
    ficha()
    expect(screen.queryByText(/Revisado|Pendiente de revisar/)).not.toBeInTheDocument()
  })
})
```

En `src/modules/taller/imeis/PanelRastro.test.tsx`, añade al final del fichero:

```tsx
describe('PanelRastro: revisado (spec 0.9.18 §5.3)', () => {
  it('el evento de revisión lleva su marca y la leyenda la explica', () => {
    render(<PanelRastro imei="351900000000041" eventos={[eventoRastro({ de: 'En revisión', a: 'Listos', revision: 'ENTRADA' }), eventoRastro()]}
      onCerrar={() => {}} onIrATrabajo={() => {}} />)
    expect(screen.getByText('Revisado → Listos')).toBeInTheDocument()
    expect(screen.getAllByTestId('marca-revision')).toHaveLength(2)
    expect(screen.getByText('Revisado')).toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis`
Expected: FAIL (no existen `diasDesdeNsys` ni `textoRevision`; no hay marca).

- [x] **Step 2: Implementación**

En `src/shared/lib/telefonoNsys.ts`, añade al final:

```ts
const DIA_MADRID = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit' })
const aDiaUtc = (dia: string) => Date.UTC(Number(dia.slice(0, 4)), Number(dia.slice(5, 7)) - 1, Number(dia.slice(8, 10)))

/** Días desde un día de NSYS («2026-10-15», hora de Madrid) hasta hoy en Madrid, sea cual sea la zona del navegador; 0 si
 *  es hoy o después (0.9.18: «Pendiente de revisar desde hace N días»). */
export function diasDesdeNsys(dia: string, ahora: Date = new Date()): number {
  return Math.max(0, Math.round((aDiaUtc(DIA_MADRID.format(ahora)) - aDiaUtc(dia)) / 86_400_000))
}
```

En `src/modules/taller/imeis/rastro.ts`:
1. Cambia los imports por:

```ts
import type { EventoRastro, FichaMovil } from '@/shared/api/client'
import { diaNsys, diasDesdeNsys, textoGrado } from '@/shared/lib/telefonoNsys'
```

2. Cambia el tipo por `export type EventoVisible = { fecha: string; titulo: string; detalle: string | null; color: string; revision?: boolean }`.
3. En `verEvento`, sustituye el `case 'UBICACION':` por:

```ts
    case 'UBICACION':
      // 0.9.18 (spec §5.3): el paso de «sin caja» a una caja es la revisión del lote; salir de «Devolución», la de una devolución.
      if (e.revision) {
        const titulo = `${e.revision === 'DEVOLUCION' ? 'Revisado (devolución)' : 'Revisado'} → ${e.a || e.aNsys || 'sin caja'}`
        return { fecha, titulo, detalle: desde, color: 'bg-verde-ok', revision: true }
      }
      if (e.a) return { fecha, titulo: `Pasa a ${e.a}`, detalle: desde, color: colorUbicacion(e.a) }
      if (e.aNsys === 'Pedidos realizados') return { fecha, titulo: 'De pedido (vendido, pendiente de envío)', detalle: desde, color: 'bg-fila-urgente-brd' }
      return { fecha, titulo: `Pasa a ${e.aNsys || 'sin caja'} · sale del stock`, detalle: desde, color: 'bg-gris-borde' }
```

4. Añade al final:

```ts
/** «Revisado» en la ficha (0.9.18, spec §5.2): si ahora está en «En revisión», desde cuándo espera; si no, la última
 *  revisión del lote (la hora es la de la exportación, ± 30 min); sin datos (antes de dic. 2023 o vendido sin revisar),
 *  nada. */
export function textoRevision(
  f: Pick<FichaMovil, 'revisadoEl' | 'revisadoDevolucion' | 'pendienteRevisionDesde'>, ahora: Date = new Date(),
): string | null {
  if (f.pendienteRevisionDesde) {
    const n = diasDesdeNsys(f.pendienteRevisionDesde, ahora)
    return n === 0 ? 'Pendiente de revisar desde hoy' : `Pendiente de revisar desde hace ${n} ${n === 1 ? 'día' : 'días'}`
  }
  if (f.revisadoEl) return `${f.revisadoDevolucion ? 'Revisado (devolución)' : 'Revisado'} el ${diaNsys(f.revisadoEl)}, hacia las ${f.revisadoEl.slice(11, 16)}`
  return null
}
```

En `src/modules/taller/imeis/FichaMovil.tsx`:
1. Añade el import `import { textoRevision } from './rastro'`.
2. Tras `const venta = …`, añade `const revision = textoRevision(ficha)`.
3. Tras la línea de la píldora de la ubicación (`{ficha.ubicacion && <span …>{ficha.ubicacion}</span>}`), añade:

```tsx
      {revision && <span className="font-semibold text-azul-medio">{revision}</span>}
```

4. En el comentario de la función, añade: «Desde la 0.9.18, junto a la ubicación, cuándo se revisó o desde cuándo espera.»

En `src/modules/taller/imeis/PanelRastro.tsx`:
1. Añade el import `import { Check } from 'lucide-react'`.
2. Tras la constante `LEYENDA`, añade:

```tsx
/** La marca del evento de revisión (0.9.18, spec §5.3): un círculo verde con un ✓, en el rastro y en la leyenda. */
function MarcaRevision({ className }: { className?: string }) {
  return (
    <span aria-hidden data-testid="marca-revision" className={cn('flex items-center justify-center rounded-full bg-verde-ok text-superficie', className)}>
      <Check className="size-2.5" strokeWidth={4} />
    </span>
  )
}
```

3. En la leyenda, tras el `{LEYENDA.map(…)}`, añade:

```tsx
          <li className="flex items-center gap-1.5"><MarcaRevision className="h-3 w-3" />Revisado</li>
```

4. Sustituye el punto del evento (`<span aria-hidden className={cn('absolute top-[3px] -left-[35px] h-3.5 w-3.5 …', v.color)} />`) por:

```tsx
                {v.revision
                  ? <MarcaRevision className="absolute top-[1px] -left-[37px] h-[18px] w-[18px] border-[3px] border-superficie" />
                  : <span aria-hidden className={cn('absolute top-[3px] -left-[35px] h-3.5 w-3.5 rounded-full border-[3px] border-superficie', v.color)} />}
```

- [x] **Step 3: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/shared/lib src/modules/taller/imeis && npm run lint`
Expected: `tsc` limpio, todo en verde y lint sin errores nuevos.

- [x] **Step 4: Commit**

```bash
git add src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis
git commit -m "feat: revisado en la ficha de NSYS y en el rastro del IMEI (0.9.18)"
```

---

### Task 13: Web — E2E del stock de teléfonos ampliadas (solo lectura, preproducción)

**Files:**
- Modify: `gestion-reparaciones-web/tests/e2e/stock-telefonos.spec.ts`

**Interfaces:**
- Consumes: la pantalla de las Tasks 6-12 y sus nombres accesibles (Global Constraints → «Textos exactos»); `credenciales`,
  `entrar` y `leer` del propio fichero.
- Produces: 4 tests nuevos en un bloque en serie con un solo inicio de sesión (el entorno limita los inicios de sesión por
  minuto: con este bloque, el fichero hace 5) y dos comprobaciones nuevas en el test del técnico.

- [x] **Step 1: El técnico sigue sin ver nada**

En el test «el técnico no tiene Teléfonos: ni en la columna, ni por URL, ni por la API»:
1. Como primera línea del test:

```ts
  // 0.9.18: ni la barra superior (indicador «NSYS sin datos») ni la pantalla preguntan por NSYS para el técnico.
  const peticionesNsys: string[] = []
  page.on('request', (r) => { if (r.url().includes('/api/stock-telefonos')) peticionesNsys.push(r.url()) })
```

2. Justo antes de `expect((await leer(page, '/api/stock-telefonos/estado')).estado).toBe(403)`:

```ts
  await expect(page.getByRole('link', { name: 'NSYS sin datos' })).toHaveCount(0)
  expect(peticionesNsys, 'el técnico no pregunta por NSYS').toEqual([])
```

3. Cambia el comentario de cabecera del fichero: tras «…y el modelo por IMEI sale del espejo de NSYS.» añade «Desde la 0.9.18,
   además: «Quién lo tiene», el filtro Técnico y la tabla color × grado, un SKU de la propia tabla, el diálogo de varios
   IMEIs y la lista de importaciones de la píldora.»

- [x] **Step 2: Los tests de la 0.9.18**

Al final del fichero:

```ts
test.describe('0.9.18: Quién lo tiene, SKU, varios IMEIs e importaciones', () => {
  test.describe.configure({ mode: 'serial' })
  // Una sola sesión para los cuatro: el entorno limita los inicios de sesión por minuto.
  let pagina: Page

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
  })

  test.afterAll(async () => {
    await pagina?.close()
  })

  test.beforeEach(async () => {
    await pagina.goto('/telefonos/stock')
    await expect(pagina.getByRole('heading', { name: 'Stock de teléfonos', exact: true })).toBeVisible()
    await expect(pagina.getByTestId('capa-carga')).toHaveCount(0, { timeout: 30_000 })
    await pagina.getByRole('navigation', { name: 'Modelos' }).getByRole('button', { name: / en stock$/ }).first().click()
  })

  test('el supertécnico ve «Quién lo tiene», el filtro Técnico y la tabla color × grado', async () => {
    const tabla = pagina.getByRole('region', { name: 'Tabla de móviles' })
    await expect(tabla.getByRole('columnheader', { name: 'Quién lo tiene' })).toBeVisible()
    await expect(pagina.getByRole('button', { name: 'Técnico' })).toBeVisible()
    await expect(pagina.getByRole('table', { name: 'Color y grado' })).toBeVisible()
  })

  test('busca un SKU de la propia tabla y sale su pastilla', async () => {
    const celdas = pagina.getByRole('region', { name: 'Tabla de móviles' }).locator('td[data-columna="sku"]')
    const skus = (await celdas.allTextContents()).map((t) => t.trim()).filter((t) => t && t !== '—')
    expect(skus.length, 'alguna fila del primer modelo tiene SKU').toBeGreaterThan(0)
    const buscador = pagina.getByRole('textbox', { name: 'Buscar modelo o SKU' })
    await buscador.fill(skus[0])
    await buscador.press('Enter')
    await expect(pagina.getByTestId('pastilla-sku')).toContainText(skus[0])
    await expect(celdas.first()).toContainText(skus[0])
  })

  test('el diálogo de varios IMEIs marca dos de la tabla y un número que no está en NSYS', async () => {
    const stock = await leer<{ imeiSn: string; estado: string }[]>(pagina, '/api/stock-telefonos')
    const imeis = (stock.cuerpo ?? []).filter((m) => m.estado === 'STOCK' && /^\d{15}$/.test(m.imeiSn)).slice(0, 2).map((m) => m.imeiSn)
    expect(imeis, 'hay al menos dos móviles en stock con IMEI').toHaveLength(2)
    await pagina.getByRole('button', { name: 'Pegar varios IMEIs' }).click()
    const dialogo = pagina.getByRole('dialog', { name: 'Comprobar una lista de IMEIs' })
    // 350000000000001 no cumple el dígito de control (Luhn) de un IMEI: no puede estar en NSYS.
    await dialogo.getByRole('textbox', { name: 'IMEIs' }).fill(`${imeis[0]}\n${imeis[1]}\n350000000000001`)
    await dialogo.getByRole('button', { name: 'Comprobar' }).click()
    const filas = dialogo.getByRole('table', { name: 'Resultado' }).getByRole('row')
    await expect(filas).toHaveCount(4)
    await expect(filas.nth(1)).toContainText('Libre')
    await expect(filas.nth(2)).toContainText('Libre')
    await expect(filas.nth(3)).toContainText('No está en NSYS')
    await pagina.keyboard.press('Escape')
    await expect(dialogo).toHaveCount(0)
  })

  test('la píldora de NSYS abre la lista de importaciones y se cierra con Escape', async () => {
    await pagina.getByRole('button', { name: /^NSYS: / }).click()
    const lista = pagina.getByRole('dialog', { name: 'Importaciones de NSYS' })
    await expect(lista.getByRole('list', { name: 'Importaciones' }).getByRole('listitem').first()).toBeVisible()
    await pagina.keyboard.press('Escape')
    await expect(lista).toHaveCount(0)
  })
})
```

- [x] **Step 3: Compila y se listan**

Run: `npx playwright test tests/e2e/stock-telefonos.spec.ts --list`
Expected: 8 tests (los 4 de la 0.9.17, contando por 2 el bucle del supertécnico y el admin, y los 4 nuevos), sin errores
de compilación. No se ejecutan aquí: solo contra preprod (Task 15).

- [x] **Step 4: Commit**

```bash
git add tests/e2e/stock-telefonos.spec.ts
git commit -m "test: e2e del stock de telefonos con quien lo tiene, SKU, varios IMEIs e importaciones (0.9.18)"
```

---

### Task 14: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión).
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (entrada `[0.9.18]`).
- Modify: `gestion-reparaciones-web/docs/paridad/imeis.md` (sección 0.9.18).
- Create: `docs/novedades/NOVEDADES-v0.9.18.md` (raíz; lo sube el controlador con los gitlinks).

**Interfaces:**
- Consumes: todo lo de las Tasks 1 a 13.
- Produces: las ramas `dev_v0918_stock_telefonos` (servidor y web) listas para el merge.

- [x] **Step 1: Versión de la web**

Run (en `/c/Users/dev/Documents/wt/web-0918`): `npm version 0.9.18 --no-git-tag-version`
Expected: `v0.9.18`; `package.json` y las dos entradas de la raíz de `package-lock.json` en `0.9.18`.

- [x] **Step 2: CHANGELOG de la web**

En `CHANGELOG.md`, tras la línea `El formato sigue [Keep a Changelog]…` y su línea en blanco, antes de `## [0.9.17]`:

```markdown
## [0.9.18] - 2026-10-XX — Stock de teléfonos: quién lo tiene, SKU y revisado

- **Stock de teléfonos** (supertécnico y administrador):
  - la columna de la izquierda tiene una fila por modelo, con sus capacidades en pastillas y la barra listos / taller / revisión; el buscador acepta un modelo o un SKU, entero o el principio;
  - el modelo se abre con pestañas de capacidad, y una tabla color × grado sustituye a los chips de color y de grado;
  - la tabla de móviles dice **quién tiene cada móvil** (las asignaciones abiertas de reparación, glass y pulido, con la llegada de la glass), avisa de los móviles en caja de taller sin asignar y de los descuadres (un trabajo abierto con el móvil en Listos o de pedido) y tiene un filtro «Técnico»;
  - el grado lleva color por familias;
  - «Pegar varios IMEIs» comprueba una lista de hasta 500 IMEIs, también los vendidos, y copia el resultado para Excel;
  - la píldora de NSYS abre la lista de las últimas importaciones, con los huecos y el progreso del histórico.
- **Barra superior** (supertécnico y administrador): un aviso rojo «NSYS sin datos» en cualquier página cuando no llegan datos de NSYS.
- **IMEIs** (supertécnico y administrador): la ficha de NSYS dice cuándo se revisó el móvil o desde cuándo espera la revisión, y el rastro rotula «Revisado →».
- Requiere el servidor 0.9.18. Sin migración.
```

- [x] **Step 3: Ficha de paridad**

Al final de `docs/paridad/imeis.md`, con una línea en blanco antes:

```markdown
## 0.9.18 — Revisado en la ficha y en el rastro (sin equivalente en el JavaFX)

Añadido, decidido en la spec `2026-10-19-v0918-stock-telefonos-quien-lo-tiene-design.md` (raíz). Para SUPERTECNICO y ADMIN:

- la ficha de NSYS dice «Revisado el …» o «Pendiente de revisar desde hace N días»;
- en el rastro, el paso de «sin caja» a una caja se rotula «Revisado → …», con su marca en la leyenda.

Pruebas: `FichaMovil.test.tsx`, `PanelRastro.test.tsx` y `rastro.test.ts`. El TECNICO ve lo mismo que antes.
```

- [x] **Step 4: Novedades (raíz, sin commit)**

Crea `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.18.md`:

```markdown
# 🎉 Novedades — Versión 0.9.18 (web)

El **stock de teléfonos** dice quién tiene cada móvil, busca por SKU y cuenta por color y grado de un vistazo. La ficha de un
IMEI dice cuándo se revisó.

---

## 🧰 Para todos

- El técnico ve el programa como hasta ahora.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos

- **La columna de la izquierda** tiene una fila por modelo, con sus capacidades y cuántos hay de cada una. Al pulsar el
  nombre se abre el modelo entero; al pulsar una capacidad, solo esa.
- **El buscador de la columna** acepta un modelo («13 pro») o un **SKU**, entero o el principio. Debajo salen los SKU en
  stock que empiezan así; al elegir uno se abre su modelo con ese SKU.
- **Pestañas de capacidad** junto al nombre del modelo.
- **La tabla color × grado** sustituye a los chips: cuántos hay de cada color y grado, y en qué reparto (listos, taller y
  revisión). Pulsar una casilla filtra por ese color y grado; Ctrl+clic suma casillas.
- **«Quién lo tiene»:** el técnico que tiene el móvil (reparación, glass o pulido) y, en la glass, si ya le llegó. Avisa de
  dos cosas:
  - **«En caja, sin asignar»:** está en una caja de taller y nadie lo tiene;
  - **descuadre:** hay un trabajo abierto pero NSYS dice que está en Listos o de pedido. Falta cerrar el trabajo o mover la
    caja.
- Un filtro **«Técnico»**, con «Sin asignar» y «Con descuadre».
- **El grado tiene color** por familias (A, B, C, Ms, ID y n/a).
- **«Pegar varios IMEIs»:** pega una lista (de una columna de Excel, por ejemplo) y dice de cada IMEI si está en stock, de
  pedido, vendido o no está en NSYS, con quién lo tiene. El resultado se copia para Excel.
- **La píldora de NSYS** se puede pulsar: enseña las últimas importaciones y avisa si falta algún Items.

### 🔔 Barra superior

- Si no llegan datos de NSYS, sale un aviso rojo **«NSYS sin datos»** en cualquier página. Lleva al stock de teléfonos.

### 🔎 IMEIs

- La ficha de NSYS dice **cuándo se revisó** el móvil o **desde cuándo espera** la revisión.
- En el rastro, la revisión sale como **«Revisado → caja»**.
```

- [x] **Step 5: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0918`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total de tests en el informe.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0918`
Expected: lint sin errores (el aviso de `BadgeEstadoPedido.tsx` ya existía), `tsc -b` limpio y todos los tests de Vitest en
verde. Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista incluye los 8 de `stock-telefonos.spec.ts` y los de siempre, sin errores de compilación.

- [x] **Step 6: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git commit -m "docs: version 0.9.18, changelog y paridad del stock de telefonos"
```

- [ ] **Step 7: Entrega (controlador, con OK del usuario)**

1. Revisión final de las dos ramas.
2. Con el OK del usuario:
   - merge `--no-ff` de `dev_v0918_stock_telefonos` en `main` del servidor y de la web, con push de los dos;
   - en la raíz: gitlinks, `NOVEDADES-v0.9.18.md` y el plan marcado.
3. Worktrees y ramas se borran después de producción.

---

### Task 15: Preproducción

**Files:**
- Create: `Apuntes/preprod/v0918-preprod.md` (guion; fuera de los repos).
- Modify: `Apuntes/despliegue_preprod.md` (registro).

**Requisitos antes de empezar:** la 0.9.17 está en producción y el merge de la Task 14 está hecho.

- [ ] **Step 1: Guion**

Basado en `preprod/v0916-preprod.md` (servidor + web, sin base de datos). **Cada bloque empieza con `hostname` y el seguro de
`backup-erp.sh`.**

- **Bloque 1 (solo lee):**
  - commits y contenedores;
  - `SELECT COUNT(*) FROM Reparacion WHERE ID_REP LIKE 'A%' AND FECHA_FIN IS NULL`: las asignaciones abiertas que va a
    cruzar el stock;
  - las últimas `ACTUAL` de `Nsys_importacion`, para compararlas después con el popover:
    `SELECT FICHERO, FECHA_EXPORTACION, RESULTADO, CAMBIOS, TIMESTAMPDIFF(SECOND, EMPEZADA, TERMINADA) FROM Nsys_importacion WHERE ORIGEN = 'ACTUAL' ORDER BY FECHA_EXPORTACION DESC, ID_IMPORTACION DESC LIMIT 20;`
  - el histórico: `SELECT RESULTADO, COUNT(*), MAX(FECHA_EXPORTACION) FROM Nsys_importacion WHERE ORIGEN = 'HISTORICO' GROUP BY RESULTADO;`
  - cinco revisiones recientes, para probar la ficha:
    `SELECT IMEI_SN, BATCH, FECHA, A FROM Nsys_evento WHERE TIPO = 'UBICACION' AND DE = 'En revisión' AND A_NSYS <> 'Pedidos realizados' ORDER BY FECHA DESC LIMIT 5;`
- **Bloque 2 (escribe), con el OK de Claude:** `pull` de servidor y web y `docker compose up -d --build` (primero el servidor,
  luego la web). Avisar de que tarda ~1 min sin salida. `Started App` sin `ERROR`.
- **Comprobaciones de Claude desde el PC:**
  - `/version.json` da 0.9.18.
  - E2E de una en una, con ≥ 60 s entre ficheros: `stock-telefonos` (la ampliada), `roles-stock`, `taller` y `columna-lateral`.
  - Capturas de «Stock de teléfonos» a 1366×768 y a 1920×1080 con la cuenta de supertécnico, solo para verlas Claude y el
    usuario (llevan datos reales y no se guardan en ningún repo):
    - sin scroll de página ni scroll lateral en la tabla (spec §4.6), en los dos ejes: con la columna lateral de la app
      abierta, con «Todas» (que añade «Cap.») y con una capacidad. En local, con datos inventados, ya se midió tras la revisión
      final: `scrollWidth` igual a `clientWidth` en la región «Tabla de móviles»;
    - con el modelo de más colores y grados abierto, la tabla color × grado no pasa del 45 % del panel, con su scroll, y la
      de móviles sigue teniendo filas a la vista.
  - En un navegador de verdad en una pantalla de 1366×768, la ventana útil mide unos 650 px de alto, menos que en la medición
    local. Mirar con el usuario cuántas filas de móviles quedan a la vista. Si se queda corto, bajar el tope del 45 % de la
    tabla color × grado (`StockTelefonosPage.tsx`).
  - El fichero de E2E hace 5 inicios de sesión: si alguno da el 503 del límite, esperar un minuto y repetir solo ese fichero.
  - **Ejecución (19-20/10):** Tasks 1-14 hechas y revisadas. Ramas: servidor `9241c64` y web `ea06933`. El registro está en
    `.superpowers/sdd/v0918/progress.md` y la revisión final en `final-review.md`. Merge y preprod, cuando la 0.9.17 esté en
    producción.
    - Si no, el arreglo va en la rama nueva `fix_v0918_preprod` (anchos de las columnas, `w-[220px]` de los filtros o la
      altura de la tabla), se vuelve a desplegar y se repiten las capturas.
- **Pruebas con datos reales, con el usuario:**
  - **Quién lo tiene:** tres o cuatro móviles con trabajos abiertos, comparados con Asignaciones (técnico, glass y su llegada).
    Un «En caja, sin asignar», comprobado en Asignaciones. Los descuadres, uno a uno con el usuario, para ver si el límite de
    60 minutos vale (spec §10).
  - **Popover:** las filas son las de la consulta del bloque 1 y los huecos cuadran con los ficheros que faltan; la línea del
    histórico cuadra con la consulta del histórico.
  - **Revisado:** la ficha y el rastro de los cinco IMEIs del bloque 1 dicen «Revisado el …» y «Revisado → …» a la hora de
    `FECHA`.
  - **SKU:** un SKU de la hoja «Saldo stock» da el mismo número en la lista de SKU de la columna.
  - **Pegar varios IMEIs:** el usuario pega una columna real de su Excel (sin guardarla en ningún sitio) y mira el resumen.
  - **Indicador de la barra:** no se fuerza un SIN_DATOS en preprod; basta con los tests y con ver que no sale con datos al día.
- **Vuelta atrás:** el código anterior (servidor y web de la 0.9.17) y `up -d --build`. No hay nada que deshacer en la base de datos.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Al final, entrada en `despliegue_preprod.md` con:
   - commits;
   - E2E;
   - capturas (sin guardarlas) y los arreglos de pantalla, si los hubo;
   - pruebas con datos reales y lo que se decide del límite de 60 minutos.
3. Si una E2E falla por la prueba y no por el programa, se arregla en la rama, se vuelve a pasar y se apunta.
4. **Producción solo cuando** las pruebas cuadran y el usuario da el OK.

---

### Task 16: Producción

**Files:**
- Create: `Apuntes/prod-v0918.md` (guion).
- Modify: `Apuntes/despliegue_vdc_produccion.md` (registro).

- [ ] **Step 1: Guion**

Basado en `prod-v0916.md` (servidor + web, sin base de datos). **Cada bloque empieza con `hostname` y la comprobación
`PRODUCCIÓN (ok)`.**

- **Bloque 1:** copia con `backup-erp.sh`, commits y contenedores.
- **Bloque 2:** `pull` y `up -d --build`, primero el servidor y luego la web; `Started App` sin `ERROR`.
- **Comprobación ligera desde el PC, sin escribir datos:**
  - versión 0.9.18 en `/version.json`;
  - sin letrero de preproducción (`preprod/comprobar-letrero.cjs` con `URL=https://erp.fonestore.es ESPERA=no`);
  - API sin sesión → 403 en `GET /api/stock-telefonos/importaciones` y en `POST /api/stock-telefonos/comprobar`.
- **A mano (usuario, sin guardar nada):**
  - con su cuenta de supertécnico: «Stock de teléfonos», un modelo, la tabla color × grado, «Quién lo tiene», un SKU en el
    buscador, el popover de la píldora, «Pegar varios IMEIs» con dos IMEIs y la ficha de un móvil revisado;
  - con una cuenta de técnico: sin el grupo «Teléfonos» ni el aviso de la barra.
- **Momento:** en horario. No hay migración, el corte es de unos 30 s, la web 0.9.17 funciona con el servidor 0.9.18 y la
  vuelta atrás es simple.
- **Aviso al taller:** «Hemos actualizado el programa. En "Stock de teléfonos" (supertécnicos) se ve quién tiene cada móvil,
  se puede buscar por SKU y hay una tabla de color y grado. La ficha de un IMEI dice cuándo se revisó. Recargad la página.
  Novedades en `NOVEDADES-v0.9.18`.»
- **Vuelta atrás:** servidor y web de la 0.9.17 + `up -d --build`. Nada que deshacer en la base de datos.

- [ ] **Step 2: Ejecución con el usuario y cierre**

1. Registro en `despliegue_vdc_produccion.md`: commits, hora y comprobaciones.
2. Fecha real en `[0.9.18]` del CHANGELOG de la web: commit, push y gitlink.
3. `plan-futuro.md`: la 0.9.18 marcada `[x]`, y como `[ ]` lo que queda (spec §10: el límite de 60 minutos con el uso real y
   los fallos de «Diagnostics»).
4. Tags `v0.9.18` cuando lo diga el usuario.
5. Worktrees y ramas borrados.
6. Memoria del proyecto actualizada (`project_stock_telefonos_nsys` y `project_produccion_vdc`) y «Para retomar» de
   `Apuntes/diseno-modulo-telefonos/LEEME.md`.
