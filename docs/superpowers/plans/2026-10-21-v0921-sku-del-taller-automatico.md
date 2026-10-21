# 0.9.21 — SKU del taller (pieza 2: lo automático desde las reparaciones) · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que al guardar, editar o borrar una reparación con chasis, tapa trasera o batería el ERP cambie (o deshaga) solo el
color y el grado del móvil por encima de NSYS, con una banda que lo avisa en el formulario, y que todo quede en el rastro y en
«Por pasar a NSYS».

**Architecture:**
- **Base de datos:** sin migración propia. Una tabla más, `Taller_aviso`, en el mismo `sql/migracion-sku-taller.sql` de la
  pieza 1 (aún no aplicado en ningún sitio): lo que el taller avisa sin cambiar el dato.
- **Servidor** (paquete `com.reparaciones.servidor.nsys`, salvo `GuardadoReparacion`):
  - `TallerDAO` gana el origen `REPARACION` con su `ID_REP`, el candado, deshacer/reactivar y los avisos;
  - reglas puras: `PiezaTaller` (qué pieza cuenta), `GradoTaller` (`[Ms]<letra>[100]` con una pieza), `ColorChasis` (el color
    del maestro a partir del SKU del chasis), `EfectoTaller` (lo que hace un guardado, fila a fila) y `PlanDeshacer` (qué se
    deshace al editar o borrar una R…);
  - servicio `TallerReparaciones` (aplicar, editar, deshacer y previsualizar, con la **misma** cuenta);
  - `service.GuardadoReparacion`: la transacción que une `ReparacionDAO` y el taller en `completa`, `filas`, `PUT` y `DELETE`;
  - `SkuTallerController`: `POST /api/reparaciones/sku-taller/previsualizar`;
  - `Rastro` y `StockTelefonosController.rastro` con los avisos.
- **Web:** `usePreviaTaller` (TanStack Query con retraso) y la banda en `ZonaGuardar`; textos del rastro y de «Por qué» con la
  reparación.
- **E2E** en preprod con el móvil sintético de la pieza 1 y una asignación de prueba del propio supertécnico.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5 (también `@ParameterizedTest`), Mockito, MockMvc y
  el contexto mínimo con `DataSourceTransactionManager` de `AsignacionLoteServiceTransaccionTest` (sin base de datos).
- **Web:** React 19 + TypeScript, TanStack Query, openapi-fetch. Pruebas con Vitest, Testing Library, user-event, MSW y
  Playwright.

**Spec:** `docs/superpowers/specs/2026-10-21-v0921-sku-del-taller-automatico-design.md` (raíz). Se apoya en el plan de la pieza 1,
`docs/superpowers/plans/2026-10-21-v0921-sku-del-taller-base.md`, y en su spec (§8).

## Global Constraints

**Todo lo de las Global Constraints de la pieza 1 vale aquí** (versión 0.9.21, una sola subida, ramas y worktrees, registro,
commits en español sin tildes y sin `Co-Authored-By`, merge/push/tags solo con OK, datos sintéticos, horas de Madrid con
`LocalDateTime.now(clock)` truncado a segundos, comandos, inestabilidades conocidas de la web, E2E solo en preprod, Claude no
hace SSH). Lo que añade o precisa esta pieza:

**Orden, ramas y código de partida**
- Esta pieza va en las **mismas ramas** `dev_v0921_sku_taller`, en los **mismos worktrees**
  (`/c/Users/dev/Documents/wt/servidor-0921-sku` y `/c/Users/dev/Documents/wt/web-0921-sku`), **después de los Steps 1-7 de la
  Task 13 de la pieza 1 y antes de su Task 14** (preprod). El Step 8 de esa Task 13 (entrega) pasa a la Task 13 de este plan.
  Si al empezar la pieza 1 no ha llegado ahí, **parar y avisar**.
- La «Corrección en NSYS» ya está dentro (va antes, en los `main` de los que salieron las ramas). Forma que este plan da por
  hecha, **la misma que dan por hecha las Global Constraints de la pieza 1** (si al llegar aquí difiere, el controlador adapta
  los briefs de las Tasks 8 y 11 antes de despacharlas):
  - `EventoRastro` tiene 16 campos, en este orden: `fecha, soloDia, fuente, tipo, lote, de, a, aNsys, idRep, tecnico, categoria,
    revision, loteAnterior, dato, origen, motivo`;
  - `Rastro.unir(lotes, nsys, trabajos)` y `Rastro.unir(lotes, nsys, trabajos, cambios)` (esta, de la pieza 1);
  - `StockTelefonosController.rastro` llama a `Rastro.unir(<lotes>, dao.eventos(imeiSn), dao.trabajos(imeiSn), taller.cambios(imeiSn))`.
- Nombres de la pieza 1 que se usan tal cual: `Taller_cambio`, `TallerDAO` (`guardar`, `vigentes`, `deImei`, `Nuevo`,
  `COLUMNAS`, `DESDE`, `ORDEN`, `SQL_BLOQUEAR`, `SQL_VIGENTE`, `SQL_SUSTITUIR`, `SQL_INSERTAR`, `cambio`, `entero`),
  `CambioTaller` (`Estado`, `MANO`), `DatoTaller` (`deNsys`), `LoQueVale` (`valor`, `SIN_DATO`), `Maestro` (`colores`),
  `NsysDAO.goodsMasUsados`, `Normalizacion.mismoTexto`, `TallerNsys` (`perezoso`, `cambios`), `TallerDePrueba` (`HOY`,
  `movil`, `producto`), `FilaPorPasar`/`PorQueTaller`, `MarcaTaller`, `rastro.ts` (`comoTaller`, `dato`), `porPasar.ts`
  (`textoPorQue`) y los tokens `taller`, `taller-bg`, `taller-text`.

**Base de datos**
- **Sin migración propia:** `CREATE TABLE Taller_aviso` va en `sql/migracion-sku-taller.sql` (detrás de `Taller_cambio`) y en
  `sql/crear_bd.sql`; la vista previa del fichero pasa a mirar las dos tablas (debe dar 0). Sigue siendo aditiva.

**Reglas de negocio** (spec §2, con los ajustes de abajo)
- **Qué cuenta:** las R… (nunca G…, P… ni A…) cuya pieza, por el prefijo del SKU (`Componente.TIPO`, con
  `CatalogoPiezas.prefijo`), es `cha` (chasis), `tapa` (tapa trasera) o `bat` (batería). Nueva = `ES_REUTILIZADO` falso
  (la web 0.9.19 manda `reutilizado` true y cantidad 0 para «Reutilizada»).
- **A qué móviles:** todo IMEI que esté en `Nsys_movil`, en cualquier estado; vale la fila más reciente (`NsysDAO.porImei`).
- **Grado** `[Ms]<letra>[100]` (letra `A-`, `A`, `B` o `C`): chasis o tapa → la letra pasa a `A`, conservando `Ms` y `100`;
  batería nueva → con `100`; batería reutilizada → sin `100`. Un grado sin esa forma (ID, F, FD, n/a, vacío…) no cambia con
  ninguna pieza y se avisa.
- **Color:** el del maestro que encaja con el SKU (`ColorChasis`); si no encaja con uno solo, no cambia y se avisa. En un mismo
  guardado, si un chasis deduce su color, la tapa no toca el color (manda el chasis).
- **Cada R… crea sus propios cambios** (`ORIGEN = REPARACION`, `ID_REP`, `MOTIVO` = la pieza, `ID_USU` = quien guarda), en el
  orden de las filas, partiendo de lo que vale en ese momento; si el valor nuevo es el de NSYS nace `QUITADO`, si es igual al
  que vale no se crea nada (la misma regla que la corrección a mano de la pieza 1).
- **Deshacer** (borrar la R…, o editarla cambiando el SKU o nueva ↔ reutilizada): por dato, el último cambio de esa R…; si es
  `VIGENTE` pasa a `SUSTITUIDO` y su anterior, si sigue `SUSTITUIDO`, vuelve a `VIGENTE` (saltando los de reparaciones ya
  borradas); si ya no manda, solo un aviso. Editar solo la observación o la cantidad no toca el taller.
- **Todo en la misma transacción** que la reparación (`GuardadoReparacion`, `@Transactional`): si algo falla, no queda nada.
- **Candado:** antes de leer lo que vale, `TallerDAO.bloquear` (el `SELECT … FOR UPDATE` de `Nsys_movil` de la pieza 1).

**Roles**
- `POST /api/reparaciones/sku-taller/previsualizar`: `@PreAuthorize("hasAnyRole('TECNICO', 'SUPERTECNICO', 'ADMIN')")`. Es
  una lectura: sin registro de actividad ni `Idempotency-Key`. No devuelve precios ni stock.
- Las escrituras del formulario conservan sus guardas y su idempotencia; solo cambian lo que hacen dentro.

**Textos exactos** (los compone la web; el servidor manda los datos)
- **Pieza** (`MOTIVO` y rastro): «chasis <sku> nuevo» / «chasis <sku> reutilizado», «tapa <sku> nueva» / «… reutilizada»,
  «batería <sku> nueva» / «… reutilizada».
- **Banda** (`data-testid="banda-sku-taller"`, `role="status"`, encima del botón de la zona de guardar): «Al guardar, »
  seguido de las partes que tocan, unidas con «; » y sin punto final:
  - «este móvil pasará a: Rojo · A100 (ahora: Azul · B)» (solo los datos que cambian, color y después grado);
  - «el color del chasis no se puede deducir: lo corregirá un supertécnico»;
  - «el grado (ID) no se puede calcular: lo corregirá un supertécnico».
- **Rastro** (fuente TALLER; quien es el usuario que guardó):
  - «Taller: color Azul → Rojo (reparación R20261021_3 · Técnico 2 · chasis chai13red nuevo)» (la pieza, sin comillas);
  - «Vuelve a lo de NSYS: color Rojo → Azul (reparación R… · Técnico 2 · chasis chai13blue nuevo)»;
  - «Taller: el color del chasis chai13xxx no se puede deducir: lo corregirá un supertécnico (reparación R… · Técnico 2)»
    («de la tapa tapai13xxx» si es una tapa);
  - «Taller: el grado (ID) no se puede calcular: lo corregirá un supertécnico (reparación R… · Técnico 2 · batería bati13 nueva)»;
  - «Se deshace: color Rojo → Azul (se borró la reparación R… · Técnico 2)» («se editó» al editar);
  - «Se borró la reparación R… que cambió el color; corrígelo a mano si hace falta (Técnico 2)» («Se editó…»).
- **«Por qué»** de «Por pasar a NSYS»: «Color: reparación R20261021_3 · Técnico 2 · chasis chai13red nuevo».

## Ajustes a la spec (se suben a la spec, como §8, en el commit del plan)

1. **Los avisos van en una tabla nueva, `Taller_aviso`**, dentro de la misma migración de la pieza 1 (sin migración propia):
   el rastro tiene que apuntar «no se puede deducir», «no se puede calcular», «Se deshace…» y «corrígelo a mano», y no son
   cambios de `Taller_cambio`.
2. **Deshacer pasa el cambio a `SUSTITUIDO`, no a `QUITADO`:** en la pieza 1 `QUITADO` es «vuelve a lo de NSYS» (nace cerrado)
   y el rastro lo contaría así; el «Se deshace…» lo cuenta el aviso. Un `SUSTITUIDO` al que no apunta ningún
   `ID_CAMBIO_ANTERIOR` es uno ya deshecho (una edición anterior) y no vuelve a avisar.
3. **Al deshacer, se salta el anterior que venga de una reparación ya borrada** (vuelve el de antes de ese, o NSYS): si no,
   volvería el color de una reparación que ya no existe.
4. **Nombre en inglés de cada color:** el del Goods más usado de ese color (`NsysDAO.goodsMasUsados` de la pieza 1), detrás de
   la capacidad y sin «eSIM» final; un color del maestro sin ningún móvil en NSYS no tiene nombre y no encaja (aviso).
5. **Encaje del color:** primero el nombre exacto (con los alias de `Color_equivalencia` en los dos lados); solo si ninguno,
   el color base. Tabla oficial → base fija de 14 entradas (las de §2.2 más los titanio, mist/sky/deep blue, cloud white y
   light gold de `colores.ts`).
6. **Previsualización:** cada fila lleva `idRep` (null en las nuevas; el de la R… que se edita), para deshacer y volver a
   aplicar como «Guardar cambios»; la respuesta siempre trae `enNsys`, `antes` y `despues` (iguales si no cambia nada) en vez
   de «vacío» (un objeto nullable junto a un `$ref` da problemas en el contrato).
7. **La ruta va en un controlador propio**, `SkuTallerController` (`/api/reparaciones/sku-taller`), para no tocar las guardas
   del de reparaciones.
8. **«<técnico>» en el rastro y en «Por qué» es quien guarda** (su usuario, como el «quién» de la pieza 1): el técnico en su
   formulario; el supertécnico al editar o borrar.
9. **El aviso «el grado (…) no se puede calcular» también va al rastro** (la spec solo lo pedía en la banda): es donde el
   supertécnico lo ve.
10. **Editar solo la observación o la cantidad no toca el taller** (spec: «otro SKU, o nueva ↔ reutilizada»).
11. **La tapa no toca el color si en el mismo guardado hay un chasis cuyo color se deduce**, sea cual sea el orden, y entonces
    tampoco avisa de su color.
12. **Retraso de la banda: 500 ms**, con el mismo patrón de temporizador que el autoguardado (sus 2 s llegarían tarde al
    segundo clic de «Terminar»).
13. **«No la pide en un IMEI que no está en NSYS»:** la web no lo sabe de antemano (el técnico no ve NSYS); la primera
    respuesta lo dice (`enNsys: false`) y ese formulario deja de pedir.
14. **La batería reutilizada sobre un grado sin forma también avisa** («con ninguna pieza», spec §2.1).

## Caminos del servidor que crean, cambian o borran una R…

| Camino | Qué hace | Pieza 2 |
|---|---|---|
| `POST /api/reparaciones/completa` → `ReparacionDAO.insertarCompleta` | una R… (o G…) por fila con uso: «Terminar asignación» y las filas nuevas de «Guardar cambios» | **aplica** (Task 6) |
| `POST /api/reparaciones/{idAsignacion}/filas` → `guardarFilaIndividual` | la R… de «✓ Guardar fila» (y la de «✓ Guardar» de una acción) | **aplica** |
| `PUT /api/reparaciones/{idRep}` → `editarReparacion` | otro SKU, nueva ↔ reutilizada, observación, cantidad | **deshace y aplica** si cambia la pieza |
| `DELETE /api/reparaciones/{idRep}` → `eliminar` | borra la R… (también la limpieza de las E2E) | **deshace** |
| `EntregaGlassService.entregar` / `marcarLlegada` → `guardarFilaIndividual` | el «Abrir» automático (acción `otro…`) | no: una acción no cuenta |
| `EntregaGlassService.deshacer` → `eliminar` | borra ese «Abrir» | no: nunca tiene cambios del taller |
| `POST /api/reparaciones` (`insertar`), `POST /api/reparacion-componentes`, `DELETE /api/reparacion-componentes/{idRep}/{idCom}` | una R… sin pieza; añadir o quitar piezas sueltas | **retiradas** (`RutasRetiradas`, 403): no se enganchan; si se reactivan, hay que engancharlas |
| `eliminarAsignacion`, `borrarIncidenciaPorImei`, `ReparacionComponenteDAO.borrarIncidencia`, `eliminarPulido`, `eliminarAsignacionPulido`, `completarPulido`, `marcarIncidenciaYAsignar`, `corregirCliente`, `actualizarTecnico` | A…/AG…/AP…/P… o campos que no son la pieza | no |
| `TelefonoDAO.eliminar` | solo borra un teléfono sin trabajos | no |
| `sql/reset-uat.sql` | vacía el taller en preprod | borra también `Taller_aviso` (Task 1) |

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`; clases en `src/main/java/com/reparaciones/servidor/nsys/` y pruebas en
`src/test/java/com/reparaciones/servidor/nsys/` salvo que se diga otra cosa)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `sql/migracion-sku-taller.sql`, `sql/crear_bd.sql`, `sql/reset-uat.sql`, `docs/schema.md` | Tabla `Taller_aviso` | 1 |
| `AvisoTaller.java` (nuevo), `CambioTaller.java`, `TallerDAO.java` + `TallerDAOTest.java` | Origen REPARACION, candado, deshacer, avisos | 1 |
| `PiezaTaller.java`, `GradoTaller.java` (nuevos) + `PiezaTallerTest`, `GradoTallerTest` | Qué pieza cuenta y el grado con una pieza | 2 |
| `ColorChasis.java` (nuevo) + `ColorChasisTest` | El color del chasis | 3 |
| `EfectoTaller.java`, `PlanDeshacer.java` (nuevos) + `EfectoTallerTest`, `PlanDeshacerTest`, `TallerDePrueba.java` | Un guardado y su deshacer | 4 |
| `TallerReparaciones.java`, `PeticionPreviaTaller.java`, `FilaPreviaTaller.java`, `PreviaTaller.java`, `ValoresTaller.java` (nuevos), `TallerNsys.java` + `TallerReparacionesTest` | Servicio | 5 |
| `dao/ReparacionDAO.java`, `service/GuardadoReparacion.java` (nuevo), `controller/ReparacionController.java` + `dao/ReparacionDAOCreadasTest`, `service/GuardadoReparacionTest`, `service/GuardadoReparacionTransaccionTest` y 8 tests de `controller/` | Enganche en la misma transacción | 6 |
| `SkuTallerController.java` (nuevo) + `SkuTallerControllerTest`, `controller/RolesSkuTallerTest`, `OpenApiContractTest` | Ruta, roles y contrato | 7 |
| `Rastro.java`, `TallerNsys.java`, `StockTelefonosController.java` + `RastroTest`, `StockTelefonosControllerTest` | Rastro con los avisos | 8 |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts`, `src/test/server.ts`, `src/modules/taller/test/fabrica.ts` | Contrato, alias, MSW y fábrica | 9 |
| `src/modules/taller/formulario/usePreviaTaller.ts` (nuevo, + test) | La consulta con retraso | 9 |
| `src/modules/taller/formulario/skuTaller.ts` (nuevo, + test), `ZonaGuardar.tsx`, `FormularioReparacion.tsx` (+ test) | La banda | 10 |
| `src/modules/taller/imeis/rastro.ts`, `src/modules/telefonos/stock/porPasar.ts` (+ tests) | Textos con la reparación | 11 |
| `tests/e2e/sku-taller-reparacion.spec.ts` (nuevo) | E2E en preprod | 12 |
| `CHANGELOG.md`, `docs/paridad/formulario.md`, `docs/paridad/imeis.md` | Documentación | 13 |

**Raíz y apuntes** (fuera de los worktrees): `docs/novedades/NOVEDADES-v0.9.21.md` y `Apuntes/plan-futuro.md` (Task 13).

---

### Task 1: Servidor — `Taller_aviso` y lo que la pieza 2 necesita de `TallerDAO`

**Files:**
- Modify: `gestion-reparaciones-servidor/sql/migracion-sku-taller.sql`, `sql/crear_bd.sql`, `sql/reset-uat.sql`, `docs/schema.md`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/AvisoTaller.java`
- Modify: `…/nsys/CambioTaller.java`, `…/nsys/TallerDAO.java`
- Test: `…/nsys/TallerDAOTest.java`

**Interfaces:**
- Consumes: `TallerDAO` de la pieza 1 (Task 1: `COLUMNAS`, `DESDE`, `ORDEN`, `SQL_BLOQUEAR`, `SQL_VIGENTE`, `SQL_SUSTITUIR`,
  `guardar`, `cambio`, `entero`), `NsysDAO.marcas(int)`.
- Produces:
  - `public record AvisoTaller(int id, String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de,
    String a, String motivo, Integer idUsu, String usuario, LocalDateTime fecha)` con
    `public enum Tipo { SIN_COLOR, SIN_GRADO, DESHECHO, SIN_DESHACER }`, `public enum Causa { BORRADA, EDITADA }` y
    `public record Nuevo(String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a, String motivo)`.
  - `CambioTaller.REPARACION = "REPARACION"`.
  - En `TallerDAO`:
    - `SQL_INSERTAR` con `ID_REP` (12 parámetros) y `@Transactional void guardar(String imeiSn, List<Nuevo> nuevos, String idRep,
      int idUsu, String motivo, LocalDateTime ahora)` (origen `REPARACION` con `idRep`, `MANO` con null); el `guardar` de cinco
      parámetros llama a este con `null`;
    - `boolean bloquear(String imeiSn)`, `List<CambioTaller> deReparacion(String idRep)`, `int deshacer(int idCambio,
      LocalDateTime ahora)`, `int reactivar(int idCambio)`, `void avisar(AvisoTaller.Nuevo a, int idUsu, LocalDateTime ahora)`,
      `List<AvisoTaller> avisosDeImei(String imeiSn)`, `Map<Integer, String> tiposDeComponentes(Collection<Integer> idComs)`,
      `Optional<PiezaDeReparacion> piezaDeReparacion(String idRep)`, `Set<String> reparacionesQueExisten(Collection<String> idReps)`;
    - `public record PiezaDeReparacion(String idRep, String imei, int idCom, boolean reutilizada)`;
    - de paquete: `SQL_REACTIVAR`, `SQL_AVISAR`, `SQL_AVISOS`, `SQL_PIEZA` y `static AvisoTaller aviso(ResultSet, int)`.

- [ ] **Step 1: Migración, `crear_bd.sql`, `reset-uat.sql` y `schema.md`**

En `sql/migracion-sku-taller.sql` (el de la pieza 1):

1. En la cabecera, cambia `-- migracion-sku-taller.sql — 0.9.21, SKU del taller (pieza 1: la base)` por
   `-- migracion-sku-taller.sql — 0.9.21, SKU del taller (piezas 1 y 2)` y `-- 2) Ejecutar el CREATE TABLE.` por
   `-- 2) Ejecutar los dos CREATE TABLE (Taller_cambio y Taller_aviso).`
2. En la vista previa, cambia `WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_cambio';` por
   `WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Taller_cambio', 'Taller_aviso');`
3. Al final del fichero, tras el `);` del `CREATE TABLE Taller_cambio`, añade:

```sql

-- Pieza 2 (lo automático desde las reparaciones): lo que el taller avisa al guardar, editar o borrar una reparación sin
-- cambiar el dato (el color de un chasis que no se puede deducir, un grado que no se puede calcular, un cambio que se
-- deshace y uno que ya no se puede deshacer). Solo se añade; lo lee el rastro del IMEI.
CREATE TABLE Taller_aviso (
    ID_AVISO   INT          NOT NULL AUTO_INCREMENT,
    IMEI_SN    VARCHAR(40)  NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN',
    ID_REP     VARCHAR(30)  NOT NULL COMMENT 'La reparación R… (puede estar ya borrada)',
    TIPO       ENUM('SIN_COLOR','SIN_GRADO','DESHECHO','SIN_DESHACER') NOT NULL,
    CAUSA      ENUM('BORRADA','EDITADA') NULL COMMENT 'DESHECHO y SIN_DESHACER: por qué',
    DATO       ENUM('COLOR','GRADO') NOT NULL,
    DE         VARCHAR(100) NULL COMMENT 'SIN_COLOR: el SKU; SIN_GRADO: el grado; DESHECHO y SIN_DESHACER: lo que puso la reparación',
    A          VARCHAR(40)  NULL COMMENT 'DESHECHO: lo que vuelve a valer',
    MOTIVO     VARCHAR(500) NULL COMMENT 'La pieza: «chasis chai13red nuevo»',
    ID_USU     INT          NULL,
    FECHA      DATETIME     NOT NULL,
    PRIMARY KEY (ID_AVISO),
    KEY idx_taller_aviso_imei (IMEI_SN, FECHA),
    CONSTRAINT fk_taller_aviso_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

En `sql/crear_bd.sql`:
- en la lista del principio, justo antes de `DROP TABLE IF EXISTS Nsys_evento;`, añade `DROP TABLE IF EXISTS Taller_aviso;` y,
  si no está, `DROP TABLE IF EXISTS Taller_cambio;` (el fichero «se puede relanzar cuantas veces se quiera»);
- al final, tras el `CREATE TABLE Taller_cambio (…);` de la pieza 1, una línea en blanco y, **tal cual**, el comentario de tres
  líneas y el `CREATE TABLE Taller_aviso (…);` de arriba.

En `sql/reset-uat.sql`, en el bloque `-- 5a. SKU del taller (0.9.21)…` de la pieza 1, tras `DELETE FROM Taller_cambio;`,
añade `DELETE FROM Taller_aviso;`.

En `docs/schema.md`, tras la línea de `Taller_cambio`, añade:

```markdown
- `Taller_aviso` (0.9.21, pieza 2: lo que el taller avisa al guardar, editar o borrar una reparación sin cambiar el dato —color que no se deduce, grado que no se calcula, cambio deshecho o que ya no se puede deshacer—; solo se añade y lo lee el rastro; ver `sql/migracion-sku-taller.sql`)
```

- [ ] **Step 2: Tests (fallan)**

En `TallerDAOTest.java`:

1. Imports: añade `java.util.Set`.
2. En `guardarBloqueaElMovilSustituyeElVigenteYApuntaAEl`, el `ID_REP` va detrás del `ORIGEN`: cambia las dos verificaciones de
   `SQL_INSERTAR` por

```java
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "MANO", null, 7, "motivo", t, "VIGENTE",
                null, null);
```

   y

```java
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "GRADO", "B", "B", "MANO", null, 7, "motivo", t, "QUITADO", t, 4);
```

3. Añade al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.21, pieza 2: desde las reparaciones

    @Test void unCambioDeUnaReparacionLlevaSuOrigenYSuReparacion() {
        dao.guardar(IMEI, List.of(new TallerDAO.Nuevo(DatoTaller.COLOR, "Rojo", "Azul", CambioTaller.Estado.VIGENTE)),
                "R20261021_3", 8, "chasis chai13red nuevo", HOY);
        verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "REPARACION", "R20261021_3", 8,
                "chasis chai13red nuevo", Timestamp.valueOf(HOY), "VIGENTE", null, null);
        assertTrue(TallerDAO.SQL_INSERTAR.contains("ORIGEN, ID_REP, ID_USU"), TallerDAO.SQL_INSERTAR);
    }

    @Test void guardarDesdeUnaReparacionTambienEsTodoONada() throws NoSuchMethodException {
        assertNotNull(TallerDAO.class.getMethod("guardar", String.class, List.class, String.class, int.class, String.class,
                LocalDateTime.class).getAnnotation(Transactional.class));
    }

    @Test void elCandadoDiceSiNsysConoceElImei() {
        when(jdbc.queryForList(TallerDAO.SQL_BLOQUEAR, String.class, IMEI)).thenReturn(List.of(IMEI));
        assertTrue(dao.bloquear(IMEI));
        assertFalse(dao.bloquear("350000000000099"));
    }

    @SuppressWarnings("unchecked")
    @Test void losCambiosDeUnaReparacion() {
        assertTrue(dao.deReparacion("R20261021_3").isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.ID_REP = ?" + TallerDAO.ORDEN),
                any(RowMapper.class), eq("R20261021_3"));
    }

    @Test void deshacerSustituyeSoloUnVigenteYReactivarSoloUnSustituido() {
        when(jdbc.update(TallerDAO.SQL_SUSTITUIR, Timestamp.valueOf(HOY), 5)).thenReturn(1);
        assertEquals(1, dao.deshacer(5, HOY));
        dao.reactivar(4);
        verify(jdbc).update(TallerDAO.SQL_REACTIVAR, 4);
        assertTrue(TallerDAO.SQL_REACTIVAR.contains("SET ESTADO = 'VIGENTE', CERRADO_EN = NULL"), TallerDAO.SQL_REACTIVAR);
        assertTrue(TallerDAO.SQL_REACTIVAR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'SUSTITUIDO'"), TallerDAO.SQL_REACTIVAR);
    }

    @Test void unAvisoConYSinCausa() {
        Timestamp t = Timestamp.valueOf(HOY);
        dao.avisar(new AvisoTaller.Nuevo(IMEI, "R20261021_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA,
                DatoTaller.COLOR, "Rojo", "Azul", "chasis chai13red nuevo"), 8, HOY);
        verify(jdbc).update(TallerDAO.SQL_AVISAR, IMEI, "R20261021_3", "DESHECHO", "BORRADA", "COLOR", "Rojo", "Azul",
                "chasis chai13red nuevo", 8, t);
        dao.avisar(new AvisoTaller.Nuevo(IMEI, "R20261021_4", AvisoTaller.Tipo.SIN_COLOR, null, DatoTaller.COLOR, "chai13xxx",
                null, "chasis chai13xxx nuevo"), 8, HOY);
        verify(jdbc).update(TallerDAO.SQL_AVISAR, IMEI, "R20261021_4", "SIN_COLOR", null, "COLOR", "chai13xxx", null,
                "chasis chai13xxx nuevo", 8, t);
    }

    @SuppressWarnings("unchecked")
    @Test void losAvisosDeUnImeiConQuienPorSuNombre() {
        assertTrue(dao.avisosDeImei(IMEI).isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_AVISOS), any(RowMapper.class), eq(IMEI));
        assertTrue(TallerDAO.SQL_AVISOS.contains("LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU"), TallerDAO.SQL_AVISOS);
        assertTrue(TallerDAO.SQL_AVISOS.endsWith(" WHERE v.IMEI_SN = ? ORDER BY v.FECHA, v.ID_AVISO"), TallerDAO.SQL_AVISOS);
    }

    @Test void unaFilaDeTallerAviso() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_AVISO")).thenReturn(2);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("ID_REP")).thenReturn("R20261021_3");
        when(rs.getString("TIPO")).thenReturn("SIN_GRADO");
        when(rs.getString("DATO")).thenReturn("GRADO");
        when(rs.getString("DE")).thenReturn("ID");
        when(rs.getString("MOTIVO")).thenReturn("batería bati13 nueva");
        when(rs.getInt("ID_USU")).thenReturn(8);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Técnico 2");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        assertEquals(new AvisoTaller(2, IMEI, "R20261021_3", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO, "ID", null,
                "batería bati13 nueva", 8, "Técnico 2", HOY), TallerDAO.aviso(rs, 0));
        when(rs.getString("CAUSA")).thenReturn("EDITADA");
        assertEquals(AvisoTaller.Causa.EDITADA, TallerDAO.aviso(rs, 0).causa());
    }

    @SuppressWarnings("unchecked")
    @Test void losSkuDeLosComponentesEnUnaConsulta() {
        assertTrue(dao.tiposDeComponentes(List.of(101, 131, 101)).isEmpty());
        verify(jdbc).query(eq("SELECT ID_COM, TIPO FROM Componente WHERE ID_COM IN (?, ?)"), any(RowMapper.class), eq(101), eq(131));
        clearInvocations(jdbc);
        assertTrue(dao.tiposDeComponentes(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void laPiezaDeUnaReparacionEsSuFilaConUso() {
        assertTrue(dao.piezaDeReparacion("R20261021_3").isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_PIEZA), any(RowMapper.class), eq("R20261021_3"));
        assertTrue(TallerDAO.SQL_PIEZA.contains("rc.ES_SOLICITUD = 0"), TallerDAO.SQL_PIEZA);
    }

    @Test void lasReparacionesQueSiguenExistiendo() {
        when(jdbc.queryForList("SELECT ID_REP FROM Reparacion WHERE ID_REP IN (?, ?)", String.class, "R1", "R2"))
                .thenReturn(List.of("R2"));
        assertEquals(Set.of("R2"), dao.reparacionesQueExisten(List.of("R1", "R2")));
        clearInvocations(jdbc);
        assertEquals(Set.of(), dao.reparacionesQueExisten(List.of()));
        verifyNoInteractions(jdbc);
    }
```

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='TallerDAOTest'`
Expected: error de compilación: no existen `AvisoTaller`, `TallerDAO.bloquear`, `SQL_AVISAR`… ni el `guardar` con `idRep`.

- [ ] **Step 4: Implementación**

Crea `AvisoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_aviso (spec 0.9.21 «SKU del taller», pieza 2, §2.2 y §2.3): lo que el taller avisa al guardar, editar o
 *  borrar una reparación sin cambiar el dato. {@code de}: en SIN_COLOR, el SKU de la pieza; en SIN_GRADO, el grado que no se
 *  pudo calcular; en DESHECHO y SIN_DESHACER, lo que había puesto la reparación. {@code a}: en DESHECHO, lo que vuelve a
 *  valer. {@code motivo}: la pieza («chasis chai13red nuevo»). {@code usuario}: quien guardó (null si ya no existe). Horas de
 *  Madrid, como el espejo. */
public record AvisoTaller(int id, String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                          String motivo, Integer idUsu, String usuario, LocalDateTime fecha) {

    /** SIN_COLOR: el color del chasis o de la tapa no se puede deducir del maestro. SIN_GRADO: el grado no es de la forma
     *  [Ms]<letra>[100]. DESHECHO: se deshizo el cambio de la reparación. SIN_DESHACER: ya no mandaba y no se tocó. */
    public enum Tipo { SIN_COLOR, SIN_GRADO, DESHECHO, SIN_DESHACER }

    /** Por qué se deshace (DESHECHO y SIN_DESHACER). */
    public enum Causa { BORRADA, EDITADA }

    /** Un aviso que entra. */
    public record Nuevo(String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                        String motivo) {}
}
```

En `CambioTaller.java`, sustituye

```java
    /** Origen de los cambios de esta pieza («Corregir…» y «Volver a lo de NSYS»); REPARACION es de la pieza 2. */
    public static final String MANO = "MANO";
```

por

```java
    /** Origen de los cambios a mano («Corregir…» y «Volver a lo de NSYS»). */
    public static final String MANO = "MANO";
    /** Origen de los cambios que hace una reparación al guardarse (pieza 2): llevan su {@code idRep}. */
    public static final String REPARACION = "REPARACION";
```

En `TallerDAO.java`:

1. Imports: añade `java.util.HashMap`, `java.util.HashSet`, `java.util.LinkedHashSet`, `java.util.Map`, `java.util.Optional` y
   `java.util.Set`.
2. Sustituye la constante `SQL_INSERTAR` por:

```java
    static final String SQL_INSERTAR = "INSERT INTO Taller_cambio (IMEI_SN, DATO, VALOR, VALOR_NSYS, ORIGEN, ID_REP, ID_USU,"
            + " MOTIVO, FECHA, ESTADO, CERRADO_EN, ID_CAMBIO_ANTERIOR) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
```

3. Sustituye el método `guardar` entero (con su Javadoc y su `@Transactional`) por:

```java
    /** Guarda unos cambios a mano de un móvil, todo o nada (ver el de abajo). */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, int idUsu, String motivo, LocalDateTime ahora) {
        guardar(imeiSn, nuevos, null, idUsu, motivo, ahora);
    }

    /** Guarda unos cambios de un móvil, todo o nada: el VIGENTE anterior de cada dato, si lo hay, pasa a SUSTITUIDO y el
     *  nuevo apunta a él (ID_CAMBIO_ANTERIOR). Un QUITADO nace cerrado. {@code idRep}: la reparación que los hace (ORIGEN
     *  REPARACION, pieza 2) o null (a mano). */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, String idRep, int idUsu, String motivo, LocalDateTime ahora) {
        jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn);
        Timestamp t = Timestamp.valueOf(ahora);
        String origen = idRep == null ? CambioTaller.MANO : CambioTaller.REPARACION;
        for (Nuevo n : nuevos) {
            List<Integer> vigentes = jdbc.queryForList(SQL_VIGENTE, Integer.class, imeiSn, n.dato().name());
            for (Integer id : vigentes) jdbc.update(SQL_SUSTITUIR, t, id);
            Integer anterior = vigentes.stream().max(Integer::compare).orElse(null);
            boolean cerrado = n.estado() != CambioTaller.Estado.VIGENTE;
            jdbc.update(SQL_INSERTAR, imeiSn, n.dato().name(), n.valor(), n.valorNsys(), origen, idRep, idUsu, motivo, t,
                    n.estado().name(), cerrado ? t : null, anterior);
        }
    }
```

4. Tras la constante `SQL_CERRAR`, añade:

```java
    static final String SQL_REACTIVAR = "UPDATE Taller_cambio SET ESTADO = 'VIGENTE', CERRADO_EN = NULL"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'SUSTITUIDO'";
    static final String SQL_AVISAR = "INSERT INTO Taller_aviso (IMEI_SN, ID_REP, TIPO, CAUSA, DATO, DE, A, MOTIVO, ID_USU,"
            + " FECHA) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_AVISOS = "SELECT v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.TIPO, v.CAUSA, v.DATO, v.DE, v.A, v.MOTIVO,"
            + " v.ID_USU, u.NOMBRE_USUARIO, v.FECHA FROM Taller_aviso v LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU"
            + " WHERE v.IMEI_SN = ? ORDER BY v.FECHA, v.ID_AVISO";
    /** La fila con uso real de una reparación (una R… tiene una). */
    static final String SQL_PIEZA = "SELECT r.IMEI, rc.ID_COM, rc.ES_REUTILIZADO FROM Reparacion r"
            + " JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0 WHERE r.ID_REP = ?";
```

5. Tras el método `cerrar`, añade:

```java

    // ---------------------------------------------------------------- 0.9.21, pieza 2: lo automático desde las reparaciones

    /** La pieza que tiene ahora una reparación. */
    public record PiezaDeReparacion(String idRep, String imei, int idCom, boolean reutilizada) {}

    /** El candado del móvil ({@link #SQL_BLOQUEAR}) pedido antes de leer lo que vale: dos reparaciones del mismo móvil a la
     *  vez se aplican una detrás de otra, cada una sobre lo que dejó la anterior (spec pieza 2 §3). false si NSYS no conoce
     *  el IMEI. */
    public boolean bloquear(String imeiSn) {
        return !jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn).isEmpty();
    }

    /** Los cambios de una reparación, de cualquier estado, del más antiguo al más reciente. */
    public List<CambioTaller> deReparacion(String idRep) {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ID_REP = ?" + ORDEN, TallerDAO::cambio, idRep);
    }

    /** Deshace el VIGENTE de una reparación borrada o editada: pasa a SUSTITUIDO (lo tapa lo que vuelve). 0 si ya no lo era. */
    public int deshacer(int idCambio, LocalDateTime ahora) {
        return jdbc.update(SQL_SUSTITUIR, Timestamp.valueOf(ahora), idCambio);
    }

    /** El cambio al que sustituía uno deshecho vuelve a VIGENTE. 0 si ya no era SUSTITUIDO. */
    public int reactivar(int idCambio) {
        return jdbc.update(SQL_REACTIVAR, idCambio);
    }

    public void avisar(AvisoTaller.Nuevo a, int idUsu, LocalDateTime ahora) {
        jdbc.update(SQL_AVISAR, a.imeiSn(), a.idRep(), a.tipo().name(), a.causa() == null ? null : a.causa().name(),
                a.dato().name(), a.de(), a.a(), a.motivo(), idUsu, Timestamp.valueOf(ahora));
    }

    /** Los avisos del taller de un IMEI, para el rastro, del más antiguo al más reciente. */
    public List<AvisoTaller> avisosDeImei(String imeiSn) {
        return jdbc.query(SQL_AVISOS, TallerDAO::aviso, imeiSn);
    }

    /** El SKU (Componente.TIPO) de cada componente, en una consulta; uno que no existe no sale. */
    public Map<Integer, String> tiposDeComponentes(Collection<Integer> idComs) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(idComs));
        if (ids.isEmpty()) return Map.of();
        Map<Integer, String> tipos = new HashMap<>();
        for (Map.Entry<Integer, String> e : jdbc.query("SELECT ID_COM, TIPO FROM Componente WHERE ID_COM IN ("
                + NsysDAO.marcas(ids.size()) + ")", (rs, n) -> Map.entry(rs.getInt("ID_COM"), rs.getString("TIPO")), ids.toArray()))
            tipos.put(e.getKey(), e.getValue());
        return tipos;
    }

    public Optional<PiezaDeReparacion> piezaDeReparacion(String idRep) {
        return jdbc.query(SQL_PIEZA, (rs, n) -> new PiezaDeReparacion(idRep, rs.getString("IMEI"), rs.getInt("ID_COM"),
                rs.getBoolean("ES_REUTILIZADO")), idRep).stream().findFirst();
    }

    /** De estas reparaciones, las que siguen existiendo. */
    public Set<String> reparacionesQueExisten(Collection<String> idReps) {
        List<String> ids = List.copyOf(new LinkedHashSet<>(idReps));
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("SELECT ID_REP FROM Reparacion WHERE ID_REP IN (" + NsysDAO.marcas(ids.size())
                + ")", String.class, ids.toArray()));
    }

    static AvisoTaller aviso(ResultSet rs, int n) throws SQLException {
        String causa = rs.getString("CAUSA");
        return new AvisoTaller(rs.getInt("ID_AVISO"), rs.getString("IMEI_SN"), rs.getString("ID_REP"),
                AvisoTaller.Tipo.valueOf(rs.getString("TIPO")), causa == null ? null : AvisoTaller.Causa.valueOf(causa),
                DatoTaller.valueOf(rs.getString("DATO")), rs.getString("DE"), rs.getString("A"), rs.getString("MOTIVO"),
                entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getTimestamp("FECHA").toLocalDateTime());
    }
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='TallerDAOTest,TallerNsysTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS` (los de la pieza 1 siguen en verde: `TallerNsys` llama al `guardar` de cinco parámetros).

- [ ] **Step 6: Commit**

```bash
git add sql/migracion-sku-taller.sql sql/crear_bd.sql sql/reset-uat.sql docs/schema.md \
  src/main/java/com/reparaciones/servidor/nsys/AvisoTaller.java src/main/java/com/reparaciones/servidor/nsys/CambioTaller.java \
  src/main/java/com/reparaciones/servidor/nsys/TallerDAO.java src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java
git commit -m "feat: tabla Taller_aviso y cambios del taller desde una reparacion en TallerDAO (0.9.21, pieza 2)"
```

---

### Task 2: Servidor — qué pieza cuenta y el grado con una pieza

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/PiezaTaller.java`, `GradoTaller.java`
- Create (test): `…/nsys/PiezaTallerTest.java`, `…/nsys/GradoTallerTest.java`

**Interfaces:**
- Consumes: `CatalogoPiezas.prefijo(String)` (`util`).
- Produces:
  - `public record PiezaTaller(String idRep, String sku, Tipo tipo, boolean nueva)` con `String texto()` y
    `public enum Tipo { CHASIS, TAPA, BATERIA }` con `boolean conColor()` y `static Optional<Tipo> deSku(String sku)`.
  - `GradoTaller.conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva): Optional<String>` (vacío = no se puede
    calcular) y `GradoTaller.FORMA` (de paquete).

- [ ] **Step 1: Tests (fallan)**

Crea `PiezaTallerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.reparaciones.servidor.nsys.PiezaTaller.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Qué piezas cambian el móvil (spec 0.9.21 pieza 2 §2.1): por el prefijo del SKU. */
class PiezaTallerTest {

    @Test void cuentanChasisTapaYBateria() {
        assertEquals(Optional.of(CHASIS), PiezaTaller.Tipo.deSku("chai13red"));
        assertEquals(Optional.of(CHASIS), PiezaTaller.Tipo.deSku(" CHAI16TEALESIM "));
        assertEquals(Optional.of(TAPA), PiezaTaller.Tipo.deSku("tapai13red"));
        assertEquals(Optional.of(BATERIA), PiezaTaller.Tipo.deSku("bati13promax"));
    }

    @Test void elRestoNoCuenta() {
        for (String sku : new String[] {"lcdi13", "cami13", "gi13", "mci13", "otroi13", "", "  "})
            assertEquals(Optional.empty(), PiezaTaller.Tipo.deSku(sku), sku);
        assertEquals(Optional.empty(), PiezaTaller.Tipo.deSku(null));
    }

    @Test void soloChasisYTapaLlevanColor() {
        assertTrue(CHASIS.conColor());
        assertTrue(TAPA.conColor());
        assertFalse(BATERIA.conColor());
    }

    @Test void elTextoDeLaPiezaParaElRastro() {
        assertEquals("chasis chai13red nuevo", new PiezaTaller("R1", "chai13red", CHASIS, true).texto());
        assertEquals("chasis chai13red reutilizado", new PiezaTaller("R1", "chai13red", CHASIS, false).texto());
        assertEquals("tapa tapai13red reutilizada", new PiezaTaller("R1", "tapai13red", TAPA, false).texto());
        assertEquals("batería bati13 nueva", new PiezaTaller(null, "bati13", BATERIA, true).texto());
    }
}
```

Crea `GradoTallerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** El grado con una pieza (spec 0.9.21 pieza 2 §2.1): [Ms]<letra>[100]; cada pieza toca su parte. */
class GradoTallerTest {

    @ParameterizedTest(name = "{0} + {1} (nueva {2}) → {3}")
    @CsvSource({
            // chasis y tapa: la letra pasa a A; Ms y 100 se quedan
            "B,       CHASIS,  true,  A",
            "B,       CHASIS,  false, A",
            "MsB,     CHASIS,  true,  MsA",
            "C100,    CHASIS,  false, A100",
            "MsA-100, TAPA,    true,  MsA100",
            "A,       CHASIS,  true,  A",
            "A-,      TAPA,    false, A",
            // batería: nueva pone el 100, reutilizada lo quita; Ms y la letra se quedan
            "B,       BATERIA, true,  B100",
            "MsA-,    BATERIA, true,  MsA-100",
            "B100,    BATERIA, true,  B100",
            "B100,    BATERIA, false, B",
            "A,       BATERIA, false, A",
            "MsB100,  BATERIA, false, MsB",
            // sin espacios a los lados
            "' B ',   CHASIS,  true,  A"
    })
    void conLaFormaDeNsys(String grado, PiezaTaller.Tipo tipo, boolean nueva, String esperado) {
        assertEquals(Optional.of(esperado), GradoTaller.conPieza(grado, tipo, nueva));
    }

    @ParameterizedTest(name = "«{0}» no se puede calcular")
    @CsvSource({"ID", "F", "FD", "n/a", "A+", "100", "a", "MsMsA", "''"})
    void sinLaFormaNoCambiaConNingunaPieza(String grado) {
        for (PiezaTaller.Tipo tipo : PiezaTaller.Tipo.values()) {
            assertEquals(Optional.empty(), GradoTaller.conPieza(grado, tipo, true), grado + " " + tipo);
            assertEquals(Optional.empty(), GradoTaller.conPieza(grado, tipo, false), grado + " " + tipo);
        }
    }

    @Test void sinGradoTampoco() {
        assertEquals(Optional.empty(), GradoTaller.conPieza(null, PiezaTaller.Tipo.BATERIA, true));
    }

    @Test void elEjemploDeLaSpec() {
        // MsB + chasis nuevo + batería nueva → MsA100
        String trasChasis = GradoTaller.conPieza("MsB", PiezaTaller.Tipo.CHASIS, true).orElseThrow();
        assertEquals("MsA100", GradoTaller.conPieza(trasChasis, PiezaTaller.Tipo.BATERIA, true).orElseThrow());
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='PiezaTallerTest,GradoTallerTest'`
Expected: error de compilación: no existen `PiezaTaller` ni `GradoTaller`.

- [ ] **Step 3: Implementación**

Crea `PiezaTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Locale;
import java.util.Optional;

/** Una pieza de una reparación que cambia el móvil (spec 0.9.21 «SKU del taller», pieza 2, §2.1): chasis, tapa trasera o
 *  batería, por el prefijo de su SKU (Componente.TIPO). {@code idRep}: la R… (null en la previsualización); {@code nueva}:
 *  gasta del stock (ES_REUTILIZADO falso). */
public record PiezaTaller(String idRep, String sku, Tipo tipo, boolean nueva) {

    public enum Tipo {
        CHASIS("cha", "chasis", true), TAPA("tapa", "tapa", false), BATERIA("bat", "batería", false);

        private final String prefijo;
        private final String nombre;
        private final boolean masculino;

        Tipo(String prefijo, String nombre, boolean masculino) {
            this.prefijo = prefijo;
            this.nombre = nombre;
            this.masculino = masculino;
        }

        /** Chasis y tapa cambian el color; la batería, solo el grado. */
        public boolean conColor() {
            return this != BATERIA;
        }

        /** El tipo de un SKU por su prefijo (lo que va antes de la primera «i»: chai13red → cha); vacío si no cuenta. */
        public static Optional<Tipo> deSku(String sku) {
            if (sku == null || sku.isBlank()) return Optional.empty();
            String prefijo = CatalogoPiezas.prefijo(sku.strip().toLowerCase(Locale.ROOT));
            for (Tipo t : values()) if (t.prefijo.equals(prefijo)) return Optional.of(t);
            return Optional.empty();
        }
    }

    /** «chasis chai13red nuevo», «batería bati13 reutilizada»: el MOTIVO de sus cambios y lo que dice el rastro. */
    public String texto() {
        String estado = nueva ? (tipo.masculino ? "nuevo" : "nueva") : (tipo.masculino ? "reutilizado" : "reutilizada");
        return tipo.nombre + " " + sku + " " + estado;
    }
}
```

Crea `GradoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** El grado de un móvil con una pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.1). El grado se escribe como en NSYS,
 *  [Ms]<letra>[100], y cada pieza toca su parte: chasis o tapa, la letra pasa a A (Ms y 100 se quedan); batería nueva, con
 *  100; reutilizada, sin 100. «Ms» nunca cambia solo. Un grado sin esa forma (ID, F, n/a…) no se puede calcular. Es puro. */
public final class GradoTaller {

    /** La forma del grado de NSYS (la de TallerNsys.GRADO); «A-» antes que «A» para leerlo entero. */
    static final Pattern FORMA = Pattern.compile("(Ms)?(A-|A|B|C)(100)?");

    private GradoTaller() {}

    /** El grado tras la pieza (puede ser el mismo); vacío si el grado no tiene la forma de NSYS. */
    public static Optional<String> conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva) {
        if (grado == null) return Optional.empty();
        Matcher f = FORMA.matcher(grado.strip());
        if (!f.matches()) return Optional.empty();
        String ms = f.group(1) == null ? "" : f.group(1);
        String letra = tipo.conColor() ? "A" : f.group(2);
        boolean cien = tipo == PiezaTaller.Tipo.BATERIA ? nueva : f.group(3) != null;
        return Optional.of(ms + letra + (cien ? "100" : ""));
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='PiezaTallerTest,GradoTallerTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/PiezaTaller.java src/main/java/com/reparaciones/servidor/nsys/GradoTaller.java \
  src/test/java/com/reparaciones/servidor/nsys/PiezaTallerTest.java src/test/java/com/reparaciones/servidor/nsys/GradoTallerTest.java
git commit -m "feat: piezas que cambian el movil y el grado con una pieza (0.9.21, pieza 2)"
```

---

### Task 3: Servidor — el color del chasis (`ColorChasis`)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/ColorChasis.java`
- Create (test): `…/nsys/ColorChasisTest.java`

**Interfaces:**
- Consumes: `CatalogoPiezas.prefijo` y `CatalogoPiezas.modelo` (`util`).
- Produces (`public final class ColorChasis`):
  - `public static ColorChasis de(List<String> colores, Map<String, String> goodsPorColor, String capacidad, Map<String, String> alias)`
    (colores del maestro, `NsysDAO.goodsMasUsados`, capacidad del móvil y `Color_equivalencia` texto → oficial, sin normalizar);
  - `public Optional<String> deducir(String sku)`;
  - de paquete: `static String token(String sku)`, `static String nombreDelGoods(String goods, String capacidad)`,
    `static String normalizar(String texto)` y `static final Map<String, String> BASE`.

- [ ] **Step 1: Tests (fallan)**

Crea `ColorChasisTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** El color del chasis → el color del móvil (spec 0.9.21 pieza 2 §2.2): deducido del maestro, nunca por trozos. */
class ColorChasisTest {

    /** El iPhone 13 128GB de prueba: Azul (Blue), Medianoche (Midnight), Rojo ((PRODUCT)RED) y Rosa, que ningún móvil de
     *  NSYS tiene. */
    private static ColorChasis delTrece(Map<String, String> alias) {
        return ColorChasis.de(List.of("Azul", "Medianoche", "Rojo", "Rosa"),
                Map.of("Azul", "Apple iPhone 13 128GB Blue", "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED",
                        "Medianoche", "Apple iPhone 13 128GB Midnight"), "128GB", alias);
    }

    private static final ColorChasis TRECE = delTrece(Map.of());

    @Test void leeElColorDelSkuComoLaWeb() {
        assertEquals("ultramarine", ColorChasis.token("chai16ultramarineesim"));
        assertEquals("red", ColorChasis.token("tapai13red"));
        assertEquals("sierrablue", ColorChasis.token("chai13prosierrablue"), "gana el modelo más largo");
        assertEquals("black", ColorChasis.token("chai16eblackesim"));
        assertEquals("red", ColorChasis.token(" CHAI13RED "));
        assertEquals("", ColorChasis.token(null));
    }

    @Test void elNombreEnInglesSaleDelGoodsDetrasDeLaCapacidad() {
        assertEquals("productred", ColorChasis.nombreDelGoods("Apple iPhone 13 128GB (PRODUCT)RED", "128GB"));
        assertEquals("blacktitanium", ColorChasis.nombreDelGoods("Apple iPhone 15 Pro eSIM 256GB Black Titanium", "256GB"));
        assertEquals("blue", ColorChasis.nombreDelGoods("Apple iPhone 13 128GB Blue eSIM", "128GB"));
        assertNull(ColorChasis.nombreDelGoods("Apple iPhone 13 Blue", "128GB"), "sin la capacidad no se sabe dónde empieza");
        assertNull(ColorChasis.nombreDelGoods("Apple iPhone 13 128GB", "128GB"), "no queda nada");
        assertNull(ColorChasis.nombreDelGoods(null, "128GB"));
    }

    @Test void conElMismoNombreEncaja() {
        assertEquals(Optional.of("Azul"), TRECE.deducir("chai13blue"));
        assertEquals(Optional.of("Medianoche"), TRECE.deducir("tapai13midnightesim"));
    }

    @Test void conElColorBaseEncajaSiEsUnoSolo() {
        assertEquals(Optional.of("Rojo"), TRECE.deducir("chai13red"), "red es el color base de productred");
    }

    @Test void nuncaPorTrozos() {
        assertEquals("pro", ColorChasis.token("chaixpro"));
        assertEquals(Optional.empty(), TRECE.deducir("chaixpro"), "productred contiene pro y no por eso encaja");
        assertEquals(Optional.empty(), TRECE.deducir("chai13product"), "productred contiene product");
        assertEquals(Optional.empty(), TRECE.deducir("chai13ed"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13mid"));
    }

    @Test void ningunoOUnColorSinMovilesEnNsysNoSeDeduce() {
        assertEquals(Optional.empty(), TRECE.deducir("chai13green"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13pink"), "Rosa no tiene ningún móvil en NSYS: no se sabe su nombre");
    }

    @Test void variosNoSeDeduceSalvoQueUnoSeaExacto() {
        ColorChasis doce = ColorChasis.de(List.of("Azul Pacífico", "Azul Sierra"),
                Map.of("Azul Pacífico", "Apple iPhone 12 Pro 128GB Pacific Blue", "Azul Sierra", "Apple iPhone 12 Pro 128GB Sierra Blue"),
                "128GB", Map.of());
        assertEquals(Optional.empty(), doce.deducir("chai12problue"), "los dos tienen base blue");
        assertEquals(Optional.of("Azul Sierra"), doce.deducir("chai12prosierrablue"), "la coincidencia exacta gana");
    }

    @Test void dosColoresConElMismoNombreNoSeDeducen() {
        ColorChasis raro = ColorChasis.de(List.of("Negro", "Medianoche"),
                Map.of("Negro", "Apple iPhone 13 128GB Black", "Medianoche", "Apple iPhone 13 128GB Black"), "128GB", Map.of());
        assertEquals(Optional.empty(), raro.deducir("chai13black"));
    }

    @Test void losAliasDeColorEquivalenciaValen() {
        assertEquals(Optional.of("Rojo"), delTrece(Map.of("rojo", "(PRODUCT)RED")).deducir("chai13rojo"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13rojo"), "sin el alias no encaja");
    }

    @Test void sinModeloEnElMaestroNoHayCandidatos() {
        assertEquals(Optional.empty(), ColorChasis.de(List.of(), Map.of(), "128GB", Map.of()).deducir("chai13red"));
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='ColorChasisTest'`
Expected: error de compilación: no existe `ColorChasis`.

- [ ] **Step 3: Implementación**

Crea `ColorChasis.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** El color de un móvil a partir del SKU de un chasis o una tapa trasera (spec 0.9.21 «SKU del taller», pieza 2, §2.2). Los
 *  candidatos son los colores del maestro para el modelo, la capacidad y la eSIM del móvil, cada uno con su nombre en
 *  inglés: el del Goods más usado de ese color en NSYS, detrás de la capacidad y normalizado («(PRODUCT)RED» → productred).
 *  Encaja el que tiene el mismo nombre que el color del SKU o, si ninguno, el mismo color base ({@link #BASE}); los alias
 *  de Color_equivalencia valen en los dos lados. Siempre por palabra entera, nunca por trozos («productred» contiene
 *  «pro» y no por eso encaja). Solo vale uno: con ninguno o varios, no se deduce. Es puro. */
public final class ColorChasis {

    /** Color oficial → color base: la tabla fija de los SKU de chasis (los tokens de colores.ts de la web). Un SKU con el
     *  color base encaja con el oficial («chai13red» con «(PRODUCT)RED»). */
    static final Map<String, String> BASE = Map.ofEntries(
            Map.entry("productred", "red"),
            Map.entry("pacificblue", "blue"), Map.entry("sierrablue", "blue"), Map.entry("bluetitanium", "blue"),
            Map.entry("mistblue", "blue"), Map.entry("skyblue", "blue"), Map.entry("deepblue", "blue"),
            Map.entry("alpinegreen", "green"), Map.entry("deeppurple", "purple"),
            Map.entry("spaceblack", "black"), Map.entry("blacktitanium", "black"),
            Map.entry("whitetitanium", "white"), Map.entry("cloudwhite", "white"),
            Map.entry("lightgold", "gold"));

    private final Map<String, String> nombres;
    private final Map<String, String> alias;

    /** {@code nombres}: color del maestro → su nombre en inglés normalizado (un color sin nombre no está).
     *  {@code alias}: texto normalizado → color oficial normalizado. */
    private ColorChasis(Map<String, String> nombres, Map<String, String> alias) {
        this.nombres = nombres;
        this.alias = alias;
    }

    /** Con los colores del maestro del modelo ({@link Maestro#colores}), el Goods más usado de cada uno
     *  ({@link NsysDAO#goodsMasUsados}), la capacidad del móvil y Color_equivalencia (texto → color oficial). */
    public static ColorChasis de(List<String> colores, Map<String, String> goodsPorColor, String capacidad,
                                 Map<String, String> alias) {
        Map<String, String> goods = new HashMap<>();
        goodsPorColor.forEach((color, g) -> {
            if (color != null) goods.put(color.strip(), g);
        });
        Map<String, String> nombres = new LinkedHashMap<>();
        for (String color : colores) {
            String nombre = nombreDelGoods(goods.get(color.strip()), capacidad);
            if (nombre != null) nombres.put(color, nombre);
        }
        Map<String, String> limpios = new HashMap<>();
        alias.forEach((texto, oficial) -> {
            String t = normalizar(texto);
            String o = normalizar(oficial);
            if (!t.isEmpty() && !o.isEmpty()) limpios.put(t, o);
        });
        return new ColorChasis(nombres, limpios);
    }

    /** El color del maestro de este SKU, si encaja con uno solo. */
    public Optional<String> deducir(String sku) {
        String token = token(sku);
        if (token.isEmpty()) return Optional.empty();
        Set<String> delSku = conAlias(token);
        List<String> exactos = candidatos(nombre -> !Collections.disjoint(conAlias(nombre), delSku));
        if (!exactos.isEmpty()) return exactos.size() == 1 ? Optional.of(exactos.get(0)) : Optional.empty();
        Set<String> bases = bases(delSku);
        List<String> porBase = candidatos(nombre -> !Collections.disjoint(bases(conAlias(nombre)), bases));
        return porBase.size() == 1 ? Optional.of(porBase.get(0)) : Optional.empty();
    }

    /** El color de un SKU de chasis o tapa, como leerSku de la web: lo que queda entre el modelo (el más largo del catálogo)
     *  y un «esim» final («chai16ultramarineesim» → ultramarine). */
    static String token(String sku) {
        if (sku == null) return "";
        String s = sku.strip().toLowerCase(Locale.ROOT);
        String prefijo = CatalogoPiezas.prefijo(s);
        String modelo = CatalogoPiezas.modelo(s, prefijo);
        String resto = s.length() > prefijo.length() ? s.substring(prefijo.length()) : "";
        if (resto.startsWith("i")) resto = resto.substring(1);
        if (modelo != null) resto = resto.substring(modelo.length());
        if (resto.endsWith("esim")) resto = resto.substring(0, resto.length() - "esim".length());
        return normalizar(resto);
    }

    /** El nombre en inglés de un Goods: lo que va detrás de la capacidad, sin un «eSIM» final («Apple iPhone 13 128GB
     *  (PRODUCT)RED» → productred). null si el Goods no lleva la capacidad o no queda nada. */
    static String nombreDelGoods(String goods, String capacidad) {
        if (goods == null || capacidad == null) return null;
        String g = normalizar(goods);
        String cap = normalizar(capacidad);
        int i = cap.isEmpty() ? -1 : g.lastIndexOf(cap);
        if (i < 0) return null;
        String nombre = g.substring(i + cap.length());
        if (nombre.endsWith("esim")) nombre = nombre.substring(0, nombre.length() - "esim".length());
        return nombre.isEmpty() ? null : nombre;
    }

    /** En minúsculas y solo letras y cifras («Sierra Blue» → sierrablue). */
    static String normalizar(String texto) {
        return texto == null ? "" : texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private List<String> candidatos(Predicate<String> encaja) {
        return nombres.entrySet().stream().filter(e -> encaja.test(e.getValue())).map(Map.Entry::getKey).toList();
    }

    private Set<String> conAlias(String nombre) {
        Set<String> conjunto = new HashSet<>();
        conjunto.add(nombre);
        String oficial = alias.get(nombre);
        if (oficial != null) conjunto.add(oficial);
        return conjunto;
    }

    private static Set<String> bases(Set<String> nombres) {
        return nombres.stream().map(n -> BASE.getOrDefault(n, n)).collect(Collectors.toSet());
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='ColorChasisTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/ColorChasis.java src/test/java/com/reparaciones/servidor/nsys/ColorChasisTest.java
git commit -m "feat: color del movil deducido del SKU del chasis con el maestro (0.9.21, pieza 2)"
```

---

### Task 4: Servidor — lo que hace un guardado y el plan de deshacer (`EfectoTaller`, `PlanDeshacer`)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/EfectoTaller.java`, `PlanDeshacer.java`
- Create (test): `…/nsys/EfectoTallerTest.java`, `…/nsys/PlanDeshacerTest.java`
- Modify (test): `…/nsys/TallerDePrueba.java`

**Interfaces:**
- Consumes: `PiezaTaller`, `GradoTaller` (Task 2); `TallerDAO.Nuevo`, `CambioTaller`, `DatoTaller`, `Normalizacion.mismoTexto`,
  `LoQueVale.SIN_DATO` (pieza 1).
- Produces:
  - `EfectoTaller.calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
    Function<String, Optional<String>> colorDeChasis): EfectoTaller.Resultado`, con
    `record Paso(PiezaTaller pieza, List<TallerDAO.Nuevo> nuevos, boolean colorSinDeducir, String gradoSinCalcular)` (el
    grado que no se pudo calcular, o null) y `record Resultado(String colorAntes, String gradoAntes, String colorDespues,
    String gradoDespues, List<Paso> pasos)` con `boolean colorSinDeducir()` y `boolean gradoSinCalcular()`;
    `static Optional<TallerDAO.Nuevo> cambio(DatoTaller, String nuevo, String actual, MovilNsys)` (de paquete).
  - `PlanDeshacer.de(List<CambioTaller> delImei, String idRep, Predicate<String> reparacionExiste): PlanDeshacer.Plan`, con
    `record Deshecho(CambioTaller cambio, CambioTaller vuelve)` (vuelve null: lo de NSYS) y
    `record Plan(List<Deshecho> deshechos, List<CambioTaller> sinDeshacer)` con `List<CambioTaller> vigentes(List<CambioTaller>)`
    y `boolean vacio()`.
  - `TallerDePrueba.deReparacion(int id, String imei, DatoTaller dato, String valor, String valorNsys, String idRep,
    CambioTaller.Estado estado, Integer anterior)` y `TallerDePrueba.aMano(int id, String imei, DatoTaller dato, String valor,
    String valorNsys, CambioTaller.Estado estado, Integer anterior)`.

- [ ] **Step 1: Ayudas de prueba**

En `TallerDePrueba.java` (pieza 1), añade antes del método `producto`:

```java
    /** Un cambio de la reparación {@code idRep} («Técnico 2», ID_USU 8), de ayer, por un chasis rojo nuevo, en el estado dado. */
    static CambioTaller deReparacion(int id, String imei, DatoTaller dato, String valor, String valorNsys, String idRep,
                                     CambioTaller.Estado estado, Integer anterior) {
        return new CambioTaller(id, imei, dato, valor, valorNsys, valorNsys, CambioTaller.REPARACION, idRep, 8, "Técnico 2",
                "chasis chai13red nuevo", HOY.minusDays(1), estado, estado == CambioTaller.Estado.VIGENTE ? null : HOY, null,
                anterior);
    }

    /** Un cambio a mano de «Técnico 1» (ID_USU 7), de anteayer, en el estado dado. */
    static CambioTaller aMano(int id, String imei, DatoTaller dato, String valor, String valorNsys, CambioTaller.Estado estado,
                              Integer anterior) {
        return new CambioTaller(id, imei, dato, valor, valorNsys, valorNsys, CambioTaller.MANO, null, 7, "Técnico 1", "prueba",
                HOY.minusDays(2), estado, estado == CambioTaller.Estado.VIGENTE ? null : HOY, null, anterior);
    }
```

- [ ] **Step 2: Tests (fallan)**

Crea `EfectoTallerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.QUITADO;
import static com.reparaciones.servidor.nsys.CambioTaller.Estado.VIGENTE;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.PiezaTaller.Tipo.*;
import static com.reparaciones.servidor.nsys.TallerDePrueba.movil;
import static org.junit.jupiter.api.Assertions.*;

/** Lo que hace un guardado (spec 0.9.21 pieza 2 §2.1): fila a fila, desde lo que vale en ese momento. */
class EfectoTallerTest {

    private static final String IMEI = "350000000000001";
    private static final MovilNsys AZUL_B = movil(IMEI, "Azul", "B");
    /** El color de cada SKU del iPhone 13 de prueba: rojo y azul se deducen; «chai13xxx», no. */
    private static final Function<String, Optional<String>> COLORES = sku -> switch (sku) {
        case "chai13red", "tapai13red" -> Optional.of("Rojo");
        case "chai13blue", "tapai13blue" -> Optional.of("Azul");
        default -> Optional.empty();
    };

    private static PiezaTaller pieza(String idRep, String sku, PiezaTaller.Tipo tipo, boolean nueva) {
        return new PiezaTaller(idRep, sku, tipo, nueva);
    }

    @Test void unChasisDeOtroColorCambiaElColorYPoneLaA() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "chai13red", CHASIS, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE), new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)),
                r.pasos().get(0).nuevos());
        assertEquals("Rojo", r.colorDespues());
        assertEquals("A", r.gradoDespues());
        assertFalse(r.colorSinDeducir());
        assertFalse(r.gradoSinCalcular());
    }

    @Test void msbConChasisYBateriaNuevosQuedaMsA100YCadaFilaConLoSuyo() {
        MovilNsys msb = movil(IMEI, "Azul", "MsB");
        EfectoTaller.Resultado r = EfectoTaller.calcular(msb, "Azul", "MsB",
                List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE), new TallerDAO.Nuevo(GRADO, "MsA", "MsB", VIGENTE)),
                r.pasos().get(0).nuevos());
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "MsA100", "MsB", VIGENTE)), r.pasos().get(1).nuevos());
        assertEquals("MsA100", r.gradoDespues());
    }

    @Test void siChasisYTapaDeducenColoresDistintosMandaElChasisSeaCualSeaElOrden() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B",
                List.of(pieza("R1", "tapai13blue", TAPA, true), pieza("R2", "chai13red", CHASIS, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), r.pasos().get(0).nuevos(), "la tapa no toca el color");
        assertFalse(r.pasos().get(0).colorSinDeducir());
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(1).nuevos());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void unaTapaSolaCambiaElColor() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "tapai13red", TAPA, false)), COLORES);
        assertEquals("Rojo", r.colorDespues());
        assertEquals("A", r.gradoDespues());
    }

    @Test void unColorQueNoSeDeduceNoCambiaPeroElGradoSi() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "chai13xxx", CHASIS, true)), COLORES);
        assertTrue(r.pasos().get(0).colorSinDeducir());
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), r.pasos().get(0).nuevos());
        assertEquals("Azul", r.colorDespues());
        assertTrue(r.colorSinDeducir());
    }

    @Test void unGradoSinLaFormaDeNsysNoCambiaConNingunaPiezaYSeAvisa() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID",
                List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(0).nuevos(), "el color sí");
        assertEquals("ID", r.pasos().get(0).gradoSinCalcular());
        assertTrue(r.pasos().get(1).nuevos().isEmpty());
        assertEquals("ID", r.pasos().get(1).gradoSinCalcular());
        assertEquals("ID", r.gradoDespues());
        assertTrue(r.gradoSinCalcular());
    }

    @Test void sinGradoSeAvisaConUnaRaya() {
        MovilNsys sin = movil(IMEI, "Azul", null);
        assertEquals("—", EfectoTaller.calcular(sin, "Azul", null, List.of(pieza("R1", "bati13", BATERIA, true)), COLORES)
                .pasos().get(0).gradoSinCalcular());
    }

    @Test void volverAlValorDeNsysQuitaLoDelTaller() {
        // NSYS dice Azul · B; el taller tenía Rojo · A: un chasis azul vuelve el color a lo de NSYS.
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Rojo", "A", List.of(pieza("R1", "chai13blue", CHASIS, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Azul", "Azul", QUITADO)), r.pasos().get(0).nuevos(), "el grado ya era A");
    }

    @Test void igualALoQueValeNoCreaNada() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Rojo", "A", List.of(pieza("R1", "chai13red", CHASIS, false)), COLORES);
        assertTrue(r.pasos().get(0).nuevos().isEmpty());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void laBateriaReutilizadaQuitaEl100YLaNuevaNoLoRepite() {
        MovilNsys cien = movil(IMEI, "Azul", "B100");
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "B", "B100", VIGENTE)),
                EfectoTaller.calcular(cien, "Azul", "B100", List.of(pieza("R1", "bati13", BATERIA, false)), COLORES).pasos().get(0).nuevos());
        assertTrue(EfectoTaller.calcular(cien, "Azul", "B100", List.of(pieza("R1", "bati13", BATERIA, true)), COLORES)
                .pasos().get(0).nuevos().isEmpty());
    }

    @Test void sinPiezasElMovilQuedaIgual() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(), COLORES);
        assertEquals("Azul", r.colorDespues());
        assertEquals("B", r.gradoDespues());
        assertTrue(r.pasos().isEmpty());
    }
}
```

Crea `PlanDeshacerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.aMano;
import static com.reparaciones.servidor.nsys.TallerDePrueba.deReparacion;
import static org.junit.jupiter.api.Assertions.*;

/** Qué se deshace al borrar o editar una reparación (spec 0.9.21 pieza 2 §2.3): solo lo que sigue mandando. */
class PlanDeshacerTest {

    private static final String IMEI = "350000000000001";
    private static final String R = "R20261021_3";
    private static final Predicate<String> TODAS = idRep -> true;

    @Test void unVigenteSinAnteriorSeDeshaceYValeLoDeNsys() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(c), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, null)), plan.deshechos());
        assertTrue(plan.sinDeshacer().isEmpty());
        assertTrue(plan.vigentes(List.of(c)).isEmpty());
    }

    @Test void unVigenteQueSustituyoAOtroLoDevuelve() {
        CambioTaller a = aMano(4, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, 4);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(a, c), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, a)), plan.deshechos());
        assertEquals(List.of(a), plan.vigentes(List.of(c)));
    }

    @Test void loQueYaNoMandaSoloSeAvisa() {
        CambioTaller sustituido = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, null);
        CambioTaller posterior = aMano(6, IMEI, COLOR, "Verde", "Azul", VIGENTE, 5);
        CambioTaller pasado = deReparacion(7, IMEI, GRADO, "A", "B", R, PASADO, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(sustituido, posterior, pasado), R, TODAS);
        assertTrue(plan.deshechos().isEmpty());
        assertEquals(List.of(sustituido, pasado), plan.sinDeshacer());
        assertFalse(plan.vacio());
    }

    @Test void mandaNsysYQuitadoTampocoSeDeshacen() {
        CambioTaller manda = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, MANDA_NSYS, null);
        CambioTaller quitado = deReparacion(6, IMEI, GRADO, "B", "B", R, QUITADO, null);
        assertEquals(List.of(manda, quitado), PlanDeshacer.de(List.of(manda, quitado), R, TODAS).sinDeshacer());
    }

    @Test void unoYaDeshechoEnUnaEdicionAnteriorNoVuelveAAvisar() {
        // SUSTITUIDO y ningún cambio apunta a él: lo deshizo una edición de la propia reparación.
        CambioTaller deshecho = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, null);
        assertTrue(PlanDeshacer.de(List.of(deshecho), R, TODAS).vacio());
    }

    @Test void deCadaDatoCuentaElUltimoCambioDeLaReparacion() {
        CambioTaller a = aMano(3, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller primero = deReparacion(4, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, 3);   // deshecho al editar
        CambioTaller segundo = deReparacion(5, IMEI, COLOR, "Rosa", "Azul", R, VIGENTE, 3);
        assertEquals(List.of(new PlanDeshacer.Deshecho(segundo, a)), PlanDeshacer.de(List.of(a, primero, segundo), R, TODAS).deshechos());
    }

    @Test void elAnteriorDeUnaReparacionBorradaSeSaltaYVuelveElDeAntes() {
        CambioTaller mano = aMano(3, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller deOtra = deReparacion(4, IMEI, COLOR, "Rojo", "Azul", "R20261020_1", SUSTITUIDO, 3);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rosa", "Azul", R, VIGENTE, 4);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(mano, deOtra, c), R, idRep -> !idRep.equals("R20261020_1"));
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, mano)), plan.deshechos());
    }

    @Test void colorYGradoVanCadaUnoPorSuLado() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R, PASADO, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(color, grado), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(color, null)), plan.deshechos());
        assertEquals(List.of(grado), plan.sinDeshacer());
    }

    @Test void sinCambiosDeLaReparacionNoHayNada() {
        CambioTaller deOtra = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", "R20261020_1", VIGENTE, null);
        assertTrue(PlanDeshacer.de(List.of(deOtra), R, TODAS).vacio());
    }
}
```

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='EfectoTallerTest,PlanDeshacerTest'`
Expected: error de compilación: no existen `EfectoTaller` ni `PlanDeshacer`.

- [ ] **Step 4: Implementación**

Crea `EfectoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Lo que hace un guardado de reparaciones en el color y el grado de un móvil (spec 0.9.21 «SKU del taller», pieza 2, §2.1):
 *  fila a fila y en su orden, cada una desde lo que vale tras la anterior. Chasis o tapa: el color deducido (si es otro) y
 *  la letra A; batería: el 100. Si el valor nuevo es el de NSYS, nace QUITADO; si es el que ya vale, no se crea nada (la
 *  misma regla que la corrección a mano de la pieza 1). En un guardado con un chasis cuyo color se deduce, la tapa no toca
 *  el color. La misma cuenta para guardar y para la banda del formulario. Es puro. */
public final class EfectoTaller {

    /** Lo que hace una fila: sus cambios (0, 1 o 2) y sus avisos ({@code gradoSinCalcular}: el grado que no se pudo
     *  calcular, «—» si no había, o null). */
    public record Paso(PiezaTaller pieza, List<TallerDAO.Nuevo> nuevos, boolean colorSinDeducir, String gradoSinCalcular) {}

    public record Resultado(String colorAntes, String gradoAntes, String colorDespues, String gradoDespues, List<Paso> pasos) {

        public boolean colorSinDeducir() {
            return pasos.stream().anyMatch(Paso::colorSinDeducir);
        }

        public boolean gradoSinCalcular() {
            return pasos.stream().anyMatch(p -> p.gradoSinCalcular() != null);
        }
    }

    private EfectoTaller() {}

    /** {@code colorVale} y {@code gradoVale}: lo que vale ahora (LoQueVale). {@code colorDeChasis}: SKU → color del maestro
     *  (ColorChasis). */
    public static Resultado calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
                                     Function<String, Optional<String>> colorDeChasis) {
        boolean chasisConColor = guardado.stream()
                .anyMatch(p -> p.tipo() == PiezaTaller.Tipo.CHASIS && colorDeChasis.apply(p.sku()).isPresent());
        String color = colorVale;
        String grado = gradoVale;
        List<Paso> pasos = new ArrayList<>();
        for (PiezaTaller p : guardado) {
            List<TallerDAO.Nuevo> nuevos = new ArrayList<>();
            boolean sinColor = false;
            if (p.tipo().conColor() && !(p.tipo() == PiezaTaller.Tipo.TAPA && chasisConColor)) {
                Optional<String> deducido = colorDeChasis.apply(p.sku());
                if (deducido.isEmpty()) sinColor = true;
                else {
                    Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.COLOR, deducido.get(), color, m);
                    if (n.isPresent()) {
                        nuevos.add(n.get());
                        color = n.get().valor();
                    }
                }
            }
            String gradoMal = null;
            Optional<String> nuevoGrado = GradoTaller.conPieza(grado, p.tipo(), p.nueva());
            if (nuevoGrado.isEmpty()) gradoMal = grado == null || grado.isBlank() ? LoQueVale.SIN_DATO : grado;
            else {
                Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.GRADO, nuevoGrado.get(), grado, m);
                if (n.isPresent()) {
                    nuevos.add(n.get());
                    grado = n.get().valor();
                }
            }
            pasos.add(new Paso(p, List.copyOf(nuevos), sinColor, gradoMal));
        }
        return new Resultado(colorVale, gradoVale, color, grado, List.copyOf(pasos));
    }

    /** Un dato que pasa a {@code nuevo}: nada si ya es ese; QUITADO si es lo de NSYS; si no, VIGENTE. */
    static Optional<TallerDAO.Nuevo> cambio(DatoTaller dato, String nuevo, String actual, MovilNsys m) {
        if (Normalizacion.mismoTexto(nuevo, actual)) return Optional.empty();
        String nsys = dato.deNsys(m);
        CambioTaller.Estado estado = Normalizacion.mismoTexto(nuevo, nsys) ? CambioTaller.Estado.QUITADO
                : CambioTaller.Estado.VIGENTE;
        return Optional.of(new TallerDAO.Nuevo(dato, nuevo.strip(), nsys, estado));
    }
}
```

Crea `PlanDeshacer.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Qué se deshace al borrar una reparación o al cambiar su pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.3). De cada dato
 *  cuenta el último cambio de esa R…: si es VIGENTE se deshace y vuelve el cambio al que sustituyó, si sigue SUSTITUIDO
 *  (saltando los de reparaciones que ya no existen), o lo de NSYS; si ya no manda (lo sustituyó otro posterior, pasó a NSYS,
 *  mandó NSYS o volvió a NSYS al nacer), solo se avisa. Un SUSTITUIDO al que no apunta ningún cambio ya se deshizo antes (una
 *  edición de la propia reparación) y no cuenta. La misma cuenta para guardar y para la banda del formulario. Es puro. */
public final class PlanDeshacer {

    /** Un cambio que se deshace y el que vuelve a valer (null: lo de NSYS). */
    public record Deshecho(CambioTaller cambio, CambioTaller vuelve) {}

    public record Plan(List<Deshecho> deshechos, List<CambioTaller> sinDeshacer) {

        /** Los vigentes del IMEI tras deshacer: sin los deshechos y con los que vuelven. */
        public List<CambioTaller> vigentes(List<CambioTaller> vigentes) {
            Set<Integer> fuera = deshechos.stream().map(d -> d.cambio().id()).collect(Collectors.toSet());
            List<CambioTaller> lista = new ArrayList<>(vigentes.stream().filter(c -> !fuera.contains(c.id())).toList());
            for (Deshecho d : deshechos) if (d.vuelve() != null) lista.add(d.vuelve());
            return lista;
        }

        public boolean vacio() {
            return deshechos.isEmpty() && sinDeshacer.isEmpty();
        }
    }

    private PlanDeshacer() {}

    /** {@code delImei}: todos los cambios del IMEI (TallerDAO.deImei). {@code reparacionExiste}: si una R… sigue existiendo. */
    public static Plan de(List<CambioTaller> delImei, String idRep, Predicate<String> reparacionExiste) {
        Map<Integer, CambioTaller> porId = new HashMap<>();
        Set<Integer> apuntados = new HashSet<>();
        for (CambioTaller c : delImei) {
            porId.put(c.id(), c);
            if (c.idAnterior() != null) apuntados.add(c.idAnterior());
        }
        List<Deshecho> deshechos = new ArrayList<>();
        List<CambioTaller> sinDeshacer = new ArrayList<>();
        for (DatoTaller dato : DatoTaller.values()) {
            Optional<CambioTaller> ultimo = delImei.stream().filter(c -> c.dato() == dato && idRep.equals(c.idRep()))
                    .max(Comparator.comparingInt(CambioTaller::id));
            if (ultimo.isEmpty()) continue;
            CambioTaller c = ultimo.get();
            if (c.estado() == CambioTaller.Estado.VIGENTE) deshechos.add(new Deshecho(c, vuelve(c, porId, reparacionExiste)));
            else if (c.estado() != CambioTaller.Estado.SUSTITUIDO || apuntados.contains(c.id())) sinDeshacer.add(c);
        }
        return new Plan(List.copyOf(deshechos), List.copyOf(sinDeshacer));
    }

    private static CambioTaller vuelve(CambioTaller c, Map<Integer, CambioTaller> porId, Predicate<String> reparacionExiste) {
        CambioTaller a = anterior(c, porId);
        while (a != null && a.idRep() != null && !reparacionExiste.test(a.idRep())) a = anterior(a, porId);
        return a != null && a.estado() == CambioTaller.Estado.SUSTITUIDO ? a : null;
    }

    private static CambioTaller anterior(CambioTaller c, Map<Integer, CambioTaller> porId) {
        return c.idAnterior() == null ? null : porId.get(c.idAnterior());
    }
}
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='EfectoTallerTest,PlanDeshacerTest,LoQueValeTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/EfectoTaller.java src/main/java/com/reparaciones/servidor/nsys/PlanDeshacer.java \
  src/test/java/com/reparaciones/servidor/nsys/EfectoTallerTest.java src/test/java/com/reparaciones/servidor/nsys/PlanDeshacerTest.java \
  src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java
git commit -m "feat: lo que hace un guardado de reparaciones y que se deshace al borrar o editar (0.9.21, pieza 2)"
```

---

### Task 5: Servidor — servicio `TallerReparaciones`: aplicar, editar, deshacer y previsualizar

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/TallerReparaciones.java`,
  `PeticionPreviaTaller.java`, `FilaPreviaTaller.java`, `PreviaTaller.java`, `ValoresTaller.java`
- Modify: `…/nsys/TallerNsys.java` (`perezoso` de paquete)
- Create (test): `…/nsys/TallerReparacionesTest.java`

**Interfaces:**
- Consumes: Tasks 1-4; `NsysDAO.porImei`, `cargarProductos`, `goodsMasUsados`; `Maestro.colores`; `LoQueVale.valor`;
  `ColorEquivalenciaDAO.getAll()` (`List<Map<String, String>>` con `textoExterno` y `colorOficial`); `ImportadorNsys.MADRID`.
- Produces:
  - Records públicos (esquemas con su nombre simple): `PeticionPreviaTaller(String imei, List<FilaPreviaTaller> filas)`,
    `FilaPreviaTaller(String idRep, int idCom, boolean reutilizada)` (`idRep` nullable), `PreviaTaller(boolean enNsys,
    ValoresTaller antes, ValoresTaller despues, boolean colorSinDeducir, boolean gradoSinCalcular)` con
    `PreviaTaller.FUERA_DE_NSYS`, y `ValoresTaller(String color, String grado)` (los dos nullables) con `ValoresTaller.VACIO`.
  - `TallerReparaciones` (`@Service`): `TallerReparaciones(NsysDAO, TallerDAO, ColorEquivalenciaDAO)` (Spring) y
    `(…, Clock)` (tests); `public record PiezaGuardada(String idRep, int idCom, boolean reutilizada)`;
    `void aplicar(String imei, List<PiezaGuardada> guardado, int idUsu)`;
    `Optional<TallerDAO.PiezaDeReparacion> pieza(String idRep)`;
    `void editar(TallerDAO.PiezaDeReparacion antes, PiezaGuardada nueva, int idUsu)`;
    `void deshacer(String idRep, AvisoTaller.Causa causa, int idUsu)`;
    `PreviaTaller previsualizar(PeticionPreviaTaller peticion)`;
    de paquete: `static boolean mismaPieza(TallerDAO.PiezaDeReparacion, int idCom, boolean reutilizada)`.
  - `TallerNsys.perezoso(Supplier<T>)` pasa a ser de paquete (`static`, sin `private`).

- [ ] **Step 1: Tests (fallan)**

Crea `TallerReparacionesTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El SKU del taller desde las reparaciones, con los DAO mockeados y la hora fija (21/10/2026 10:30 en Madrid). El móvil de
 *  prueba es un iPhone 13 128GB Azul · B; sus SKU: 101 bati13, 111 lcdi13, 131 chai13red, 132 chai13pink (Rosa no tiene
 *  ningún móvil en NSYS: no se deduce) y 133 chai13blue. */
class TallerReparacionesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 30);
    private static final String IMEI = "350000000000001";
    private static final String R1 = "R20261021_3";
    private static final String R2 = "R20261021_4";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO taller = mock(TallerDAO.class);
    private final ColorEquivalenciaDAO colores = mock(ColorEquivalenciaDAO.class);
    private final TallerReparaciones servicio = new TallerReparaciones(nsys, taller, colores,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(taller.bloquear(IMEI)).thenReturn(true);
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("(PRODUCT)RED", "Rojo", "B"),
                producto("Pink", "Rosa", "B")));
        when(nsys.goodsMasUsados(any())).thenReturn(Map.of("Azul", "Apple iPhone 13 128GB Blue",
                "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        when(taller.tiposDeComponentes(any())).thenReturn(Map.of(101, "bati13", 111, "lcdi13", 131, "chai13red",
                132, "chai13pink", 133, "chai13blue"));
    }

    private static TallerReparaciones.PiezaGuardada fila(String idRep, int idCom, boolean reutilizada) {
        return new TallerReparaciones.PiezaGuardada(idRep, idCom, reutilizada);
    }

    // ---------------------------------------------------------------- al guardar (spec §2.1)

    @Test void unChasisDeOtroColorCambiaElColorYElGradoConLaReparacion() {
        servicio.aplicar(IMEI, List.of(fila(R1, 131, false)), 8);
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).vigentes(List.of(IMEI));
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13red nuevo", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void lasFilasSeAplicanEnSuOrdenYCadaUnaConSuReparacion() {
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false), fila(R2, 131, true)), 8);
        InOrder orden = inOrder(taller);
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "B100", "B", VIGENTE)), R1, 8,
                "batería bati13 nueva", AHORA);
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), R2, 8, "chasis chai13red reutilizado", AHORA);
    }

    @Test void unColorQueNoSeDeduceSeAvisaYElGradoCambia() {
        servicio.aplicar(IMEI, List.of(fila(R1, 132, false)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13pink nuevo", AHORA);
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13pink", null,
                "chasis chai13pink nuevo"), 8, AHORA);
    }

    @Test void unGradoQueNoSePuedeCalcularSeAvisaYNoCambia() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "ID")));
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false)), 8);
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "ID", null,
                "batería bati13 nueva"), 8, AHORA);
    }

    @Test void unImeiQueNsysNoConoceNoCambiaNada() {
        when(taller.bloquear(IMEI)).thenReturn(false);
        servicio.aplicar(IMEI, List.of(fila(R1, 131, false)), 8);
        verify(taller, never()).vigentes(any());
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
    }

    @Test void unaPiezaQueNoCuentaOUnaGlassNoTocanElMovil() {
        servicio.aplicar(IMEI, List.of(fila(R1, 111, false)), 8);
        servicio.aplicar(IMEI, List.of(fila("G20261021_1", 131, false)), 8);
        verify(taller, never()).bloquear(any());
    }

    // ---------------------------------------------------------------- editar y borrar (spec §2.3)

    @Test void deshacerUnVigenteQueSustituyoAOtroDevuelveElAnterior() {
        CambioTaller a = aMano(4, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, 4);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(a, c));
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).deshacer(5, AHORA);
        orden.verify(taller).reactivar(4);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, COLOR,
                "Rojo", "Verde", "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void deshacerSinAnteriorVuelveALoDeNsys() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(c));
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        verify(taller).deshacer(5, AHORA);
        verify(taller, never()).reactivar(anyInt());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, COLOR,
                "Rojo", "Azul", "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void loQueYaNoMandaSoloSeAvisa() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, PASADO, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(c));
        servicio.deshacer(R1, AvisoTaller.Causa.EDITADA, 7);
        verify(taller, never()).deshacer(anyInt(), any());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_DESHACER, AvisoTaller.Causa.EDITADA, COLOR,
                "Rojo", null, "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void unaReparacionSinCambiosDelTallerNoTocaNada() {
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        servicio.deshacer("G20261021_1", AvisoTaller.Causa.BORRADA, 7);
        verify(taller, never()).bloquear(any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void editarConLaMismaPiezaNoTocaNada() {
        servicio.editar(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false), fila(R1, 131, false), 7);
        verify(taller, never()).deReparacion(any());
        verify(taller, never()).bloquear(any());
    }

    @Test void editarConOtraPiezaDeshaceLoSuyoYAplicaLaNueva() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R1, VIGENTE, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(color, grado));
        when(taller.deImei(IMEI)).thenReturn(List.of(color, grado));
        servicio.editar(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false), fila(R1, 133, true), 7);
        InOrder orden = inOrder(taller);
        orden.verify(taller).deshacer(5, AHORA);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA, COLOR,
                "Rojo", "Azul", "chasis chai13red nuevo"), 7, AHORA);
        orden.verify(taller).deshacer(6, AHORA);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA, GRADO,
                "A", "B", "chasis chai13red nuevo"), 7, AHORA);
        // Tras deshacer vale Azul · B (el mock de vigentes está vacío): el chasis azul solo pone la A.
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 7,
                "chasis chai13blue reutilizado", AHORA);
    }

    @Test void laPiezaDeUnaReparacionSoloParaLasR() {
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        assertEquals(131, servicio.pieza(R1).orElseThrow().idCom());
        assertTrue(servicio.pieza("G20261021_1").isEmpty());
        verify(taller, never()).piezaDeReparacion("G20261021_1");
    }

    // ---------------------------------------------------------------- la banda del formulario (spec §2.4)

    @Test void previsualizarHaceLaMismaCuentaSinEscribir() {
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(null, 131, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Rojo", "A"), false, false), previa);
        verify(taller, never()).bloquear(any());
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void previsualizarConLosAvisosYVariasFilasJuntas() {
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI,
                List.of(new FilaPreviaTaller(null, 132, false), new FilaPreviaTaller(null, 101, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Azul", "A100"), true, false), previa);
    }

    @Test void previsualizarSinPiezasQueCuentenDejaElMovilIgual() {
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Azul", "B"), false, false),
                servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(null, 111, false)))));
    }

    @Test void previsualizarUnImeiQueNsysNoConoce() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(PreviaTaller.FUERA_DE_NSYS, servicio.previsualizar(new PeticionPreviaTaller("350000000000099", List.of())));
        assertEquals(PreviaTaller.FUERA_DE_NSYS, servicio.previsualizar(new PeticionPreviaTaller("  ", List.of())));
        assertFalse(PreviaTaller.FUERA_DE_NSYS.enNsys());
    }

    @Test void previsualizarUnaEdicionDeshaceLoSuyoAntesDeAplicarLaNueva() {
        // La R… puso Rojo · A con un chasis rojo; se edita a un chasis rosa (no se deduce): al deshacer vuelve Azul · B y el
        // rosa solo pone la A. Sin deshacer se vería Rojo · A como si nada.
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R1, VIGENTE, null);
        when(taller.deImei(IMEI)).thenReturn(List.of(color, grado));
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(R1, 132, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Rojo", "A"), new ValoresTaller("Azul", "A"), true, false), previa);
        verify(taller, never()).deshacer(anyInt(), any());
    }

    @Test void previsualizarUnaEdicionConLaMismaPiezaNoDeshace() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        when(taller.deImei(IMEI)).thenReturn(List.of(color));
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Rojo", "B"), new ValoresTaller("Rojo", "B"), false, false),
                servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(R1, 131, false)))));
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='TallerReparacionesTest'`
Expected: error de compilación: no existen `TallerReparaciones`, `PreviaTaller`, `PeticionPreviaTaller`…

- [ ] **Step 3: Implementación**

Crea `ValoresTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** El color y el grado que valen de un móvil (spec 0.9.21 pieza 2 §2.4); null si NSYS no los sabe. */
public record ValoresTaller(@Schema(nullable = true) String color, @Schema(nullable = true) String grado) {

    static final ValoresTaller VACIO = new ValoresTaller(null, null);
}
```

Crea `FilaPreviaTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una fila del formulario para la previsualización (spec 0.9.21 pieza 2 §2.4, Ajuste 6): su componente y si es reutilizada.
 *  {@code idRep}: null en una fila nueva; el de la R… que se edita en «Guardar cambios» (se deshace lo suyo y se aplica la
 *  nueva, como al guardar). */
public record FilaPreviaTaller(@Schema(nullable = true) String idRep, int idCom, boolean reutilizada) {}
```

Crea `PeticionPreviaTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.List;

/** POST /api/reparaciones/sku-taller/previsualizar (spec 0.9.21 pieza 2 §2.4): el IMEI del formulario y las filas que se van
 *  a guardar. */
public record PeticionPreviaTaller(String imei, List<FilaPreviaTaller> filas) {}
```

Crea `PreviaTaller.java`:

```java
package com.reparaciones.servidor.nsys;

/** Lo que el guardado cambiará en el móvil (spec 0.9.21 pieza 2 §2.4, Ajuste 6): lo que vale ahora y lo que valdrá (iguales
 *  si nada cambia) y si el color o el grado no se pueden calcular. {@code enNsys} false: NSYS no conoce el IMEI (la web deja
 *  de preguntar). */
public record PreviaTaller(boolean enNsys, ValoresTaller antes, ValoresTaller despues, boolean colorSinDeducir,
                           boolean gradoSinCalcular) {

    public static final PreviaTaller FUERA_DE_NSYS = new PreviaTaller(false, ValoresTaller.VACIO, ValoresTaller.VACIO, false,
            false);
}
```

En `TallerNsys.java` (pieza 1), cambia `private static <T> Supplier<T> perezoso(Supplier<T> fuente) {` por
`static <T> Supplier<T> perezoso(Supplier<T> fuente) {` (lo usa también `TallerReparaciones`).

Crea `TallerReparaciones.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** El SKU del taller desde las reparaciones (spec 0.9.21, pieza 2): al guardar una R… con chasis, tapa trasera o batería, el
 *  color y el grado del móvil cambian solos (Taller_cambio, ORIGEN REPARACION); al editarla o borrarla se deshace lo suyo si
 *  sigue mandando; y la banda del formulario hace la misma cuenta sin escribir. Solo las R…: glass (G…) y el resto no
 *  cambian nada. Lo llama GuardadoReparacion dentro de la transacción de la reparación. */
@Service
public class TallerReparaciones {

    /** Una fila guardada de una reparación: la R… (null en la previsualización), su componente y si es reutilizada. */
    public record PiezaGuardada(String idRep, int idCom, boolean reutilizada) {}

    private final NsysDAO nsysDao;
    private final TallerDAO tallerDao;
    private final ColorEquivalenciaDAO colorDao;
    private final Clock clock;

    @Autowired
    public TallerReparaciones(NsysDAO nsysDao, TallerDAO tallerDao, ColorEquivalenciaDAO colorDao) {
        this(nsysDao, tallerDao, colorDao, Clock.system(ImportadorNsys.MADRID));
    }

    TallerReparaciones(NsysDAO nsysDao, TallerDAO tallerDao, ColorEquivalenciaDAO colorDao, Clock clock) {
        this.nsysDao = nsysDao;
        this.tallerDao = tallerDao;
        this.colorDao = colorDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- al guardar (spec §2.1)

    /** Un guardado: las R… de una misma llamada («Terminar asignación», «✓ Guardar fila» o una fila editada), en el orden de
     *  sus filas. Cada R… que cambia algo crea sus propios cambios y sus avisos. Un IMEI que NSYS no conoce no cambia nada. */
    public void aplicar(String imei, List<PiezaGuardada> guardado, int idUsu) {
        List<PiezaTaller> piezas = piezas(guardado.stream().filter(p -> esReparacion(p.idRep())).toList());
        if (piezas.isEmpty() || !tallerDao.bloquear(imei)) return;
        Optional<MovilNsys> movil = masReciente(imei);
        if (movil.isEmpty()) return;
        MovilNsys m = movil.get();
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m), piezas, colorDeChasis(m));
        LocalDateTime ahora = ahora();
        for (EfectoTaller.Paso p : r.pasos()) {
            PiezaTaller pieza = p.pieza();
            if (!p.nuevos().isEmpty()) tallerDao.guardar(m.imeiSn(), p.nuevos(), pieza.idRep(), idUsu, pieza.texto(), ahora);
            if (p.colorSinDeducir())
                tallerDao.avisar(new AvisoTaller.Nuevo(m.imeiSn(), pieza.idRep(), AvisoTaller.Tipo.SIN_COLOR, null,
                        DatoTaller.COLOR, pieza.sku(), null, pieza.texto()), idUsu, ahora);
            if (p.gradoSinCalcular() != null)
                tallerDao.avisar(new AvisoTaller.Nuevo(m.imeiSn(), pieza.idRep(), AvisoTaller.Tipo.SIN_GRADO, null,
                        DatoTaller.GRADO, p.gradoSinCalcular(), null, pieza.texto()), idUsu, ahora);
        }
    }

    // ---------------------------------------------------------------- editar y borrar (spec §2.3)

    /** La pieza que tiene ahora una R… (las demás reparaciones no cuentan). */
    public Optional<TallerDAO.PiezaDeReparacion> pieza(String idRep) {
        return esReparacion(idRep) ? tallerDao.piezaDeReparacion(idRep) : Optional.empty();
    }

    /** Tras «Guardar cambios» de una R…: si cambia la pieza (otro SKU, o nueva ↔ reutilizada), se deshace lo suyo y se
     *  aplica la nueva; si solo cambia la observación o la cantidad, nada (Ajuste 10). */
    public void editar(TallerDAO.PiezaDeReparacion antes, PiezaGuardada nueva, int idUsu) {
        if (mismaPieza(antes, nueva.idCom(), nueva.reutilizada())) return;
        deshacer(nueva.idRep(), AvisoTaller.Causa.EDITADA, idUsu);
        aplicar(antes.imei(), List.of(nueva), idUsu);
    }

    /** Al borrar una R… o al cambiar su pieza: lo suyo que sigue mandando se deshace (vuelve lo de antes o lo de NSYS) y lo
     *  que ya no manda solo se avisa. Todo queda en el rastro. */
    public void deshacer(String idRep, AvisoTaller.Causa causa, int idUsu) {
        if (!esReparacion(idRep)) return;
        List<CambioTaller> suyos = tallerDao.deReparacion(idRep);
        if (suyos.isEmpty()) return;
        String imei = suyos.get(0).imeiSn();
        tallerDao.bloquear(imei);
        PlanDeshacer.Plan plan = planDeshacer(tallerDao.deImei(imei), idRep);
        if (plan.vacio()) return;
        Optional<MovilNsys> m = masReciente(imei);
        LocalDateTime ahora = ahora();
        for (PlanDeshacer.Deshecho d : plan.deshechos()) {
            CambioTaller c = d.cambio();
            tallerDao.deshacer(c.id(), ahora);
            if (d.vuelve() != null) tallerDao.reactivar(d.vuelve().id());
            String vuelveA = d.vuelve() != null ? d.vuelve().valor() : m.map(c.dato()::deNsys).orElse(c.valorNsys());
            tallerDao.avisar(new AvisoTaller.Nuevo(imei, idRep, AvisoTaller.Tipo.DESHECHO, causa, c.dato(), c.valor(),
                    vuelveA, c.motivo()), idUsu, ahora);
        }
        for (CambioTaller c : plan.sinDeshacer())
            tallerDao.avisar(new AvisoTaller.Nuevo(imei, idRep, AvisoTaller.Tipo.SIN_DESHACER, causa, c.dato(), c.valor(),
                    null, c.motivo()), idUsu, ahora);
    }

    // ---------------------------------------------------------------- la banda del formulario (spec §2.4)

    /** Lo que haría guardar estas filas, sin escribir nada: primero la R… que se edita (si su pieza cambia, se deshace lo
     *  suyo y se aplica la nueva, como en «Guardar cambios»; el formulario edita una a la vez) y después las filas nuevas,
     *  juntas, como un guardado. */
    public PreviaTaller previsualizar(PeticionPreviaTaller peticion) {
        String imei = peticion == null || peticion.imei() == null ? "" : peticion.imei().strip();
        Optional<MovilNsys> movil = imei.isEmpty() ? Optional.empty() : masReciente(imei);
        if (movil.isEmpty()) return PreviaTaller.FUERA_DE_NSYS;
        MovilNsys m = movil.get();
        List<FilaPreviaTaller> filas = peticion.filas() == null ? List.of() : peticion.filas();
        List<CambioTaller> todos = tallerDao.deImei(m.imeiSn());
        List<CambioTaller> vigentes = todos.stream().filter(c -> c.estado() == CambioTaller.Estado.VIGENTE).toList();
        ValoresTaller antes = new ValoresTaller(LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m));
        Function<String, Optional<String>> colorDe = colorDeChasis(m);
        String color = antes.color();
        String grado = antes.grado();
        boolean sinColor = false;
        boolean sinGrado = false;
        Optional<FilaPreviaTaller> editada = filas.stream().filter(f -> f.idRep() != null).findFirst();
        Optional<TallerDAO.PiezaDeReparacion> actual = editada.flatMap(f -> pieza(f.idRep()));
        if (editada.isPresent() && actual.isPresent()
                && !mismaPieza(actual.get(), editada.get().idCom(), editada.get().reutilizada())) {
            FilaPreviaTaller f = editada.get();
            List<CambioTaller> trasDeshacer = planDeshacer(todos, f.idRep()).vigentes(vigentes);
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(trasDeshacer, DatoTaller.COLOR, m),
                    LoQueVale.valor(trasDeshacer, DatoTaller.GRADO, m),
                    piezas(List.of(new PiezaGuardada(f.idRep(), f.idCom(), f.reutilizada()))), colorDe);
            color = r.colorDespues();
            grado = r.gradoDespues();
            sinColor = r.colorSinDeducir();
            sinGrado = r.gradoSinCalcular();
        }
        List<PiezaTaller> nuevas = piezas(filas.stream().filter(f -> f.idRep() == null)
                .map(f -> new PiezaGuardada(null, f.idCom(), f.reutilizada())).toList());
        if (!nuevas.isEmpty()) {
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, color, grado, nuevas, colorDe);
            color = r.colorDespues();
            grado = r.gradoDespues();
            sinColor |= r.colorSinDeducir();
            sinGrado |= r.gradoSinCalcular();
        }
        return new PreviaTaller(true, antes, new ValoresTaller(color, grado), sinColor, sinGrado);
    }

    // ---------------------------------------------------------------- utilidades

    /** La misma pieza: el mismo componente y la misma elección (nueva o reutilizada). */
    static boolean mismaPieza(TallerDAO.PiezaDeReparacion antes, int idCom, boolean reutilizada) {
        return antes.idCom() == idCom && antes.reutilizada() == reutilizada;
    }

    private static boolean esReparacion(String idRep) {
        return idRep != null && idRep.startsWith("R");
    }

    /** Las filas que cuentan, con su SKU, en su orden. */
    private List<PiezaTaller> piezas(List<PiezaGuardada> filas) {
        if (filas.isEmpty()) return List.of();
        Map<Integer, String> tipos = tallerDao.tiposDeComponentes(filas.stream().map(PiezaGuardada::idCom).toList());
        return filas.stream().flatMap(f -> {
            String sku = tipos.get(f.idCom());
            return PiezaTaller.Tipo.deSku(sku).map(t -> new PiezaTaller(f.idRep(), sku.strip(), t, !f.reutilizada())).stream();
        }).toList();
    }

    /** El plan de deshacer de una R…, sabiendo qué otras reparaciones con cambios siguen existiendo (Ajuste 3). */
    private PlanDeshacer.Plan planDeshacer(List<CambioTaller> delImei, String idRep) {
        Set<String> otras = new LinkedHashSet<>();
        for (CambioTaller c : delImei) if (c.idRep() != null && !c.idRep().equals(idRep)) otras.add(c.idRep());
        Set<String> vivas = otras.isEmpty() ? Set.of() : tallerDao.reparacionesQueExisten(otras);
        return PlanDeshacer.de(delImei, idRep, vivas::contains);
    }

    /** La fila más reciente del IMEI en el espejo (vale esa si el IMEI está en varios lotes). */
    private Optional<MovilNsys> masReciente(String imei) {
        return nsysDao.porImei(imei).stream().findFirst();
    }

    /** SKU → color del maestro, con el maestro, el Goods más usado de cada color y Color_equivalencia leídos solo si hace
     *  falta (una batería no los necesita). */
    private Function<String, Optional<String>> colorDeChasis(MovilNsys m) {
        Supplier<ColorChasis> chasis = TallerNsys.perezoso(() -> ColorChasis.de(new Maestro(nsysDao.cargarProductos()).colores(m),
                nsysDao.goodsMasUsados(m), m.capacidad(), alias()));
        return sku -> chasis.get().deducir(sku);
    }

    private Map<String, String> alias() {
        Map<String, String> alias = new HashMap<>();
        for (Map<String, String> fila : colorDao.getAll()) alias.put(fila.get("textoExterno"), fila.get("colorOficial"));
        return alias;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='TallerReparacionesTest,TallerNsysTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS` (el contexto cablea `TallerReparaciones`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/TallerReparaciones.java src/main/java/com/reparaciones/servidor/nsys/PeticionPreviaTaller.java \
  src/main/java/com/reparaciones/servidor/nsys/FilaPreviaTaller.java src/main/java/com/reparaciones/servidor/nsys/PreviaTaller.java \
  src/main/java/com/reparaciones/servidor/nsys/ValoresTaller.java src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java \
  src/test/java/com/reparaciones/servidor/nsys/TallerReparacionesTest.java
git commit -m "feat: servicio del taller desde las reparaciones: aplicar, editar, deshacer y previsualizar (0.9.21, pieza 2)"
```

---

### Task 6: Servidor — enganche en la misma transacción: `GuardadoReparacion` y el controlador

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (`insertarCompleta`)
- Create: `…/service/GuardadoReparacion.java`
- Modify: `…/controller/ReparacionController.java`
- Create (test): `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOCreadasTest.java`,
  `…/service/GuardadoReparacionTest.java`, `…/service/GuardadoReparacionTransaccionTest.java`
- Modify (test): `…/controller/FiltroTecnicoControllersTest.java`, `IdempotenciaReparacionControllerTest.java`,
  `PropiedadAsignacionControllersTest.java`, `ReparacionControllerContadoresTest.java`,
  `ReparacionControllerCorregirClienteTest.java`, `ReparacionControllerEliminarTest.java`,
  `ReparacionControllerEntregaGlassTest.java`, `RolesReparacionFormularioTest.java`

**Interfaces:**
- Consumes: `TallerReparaciones` (`aplicar`, `pieza`, `editar`, `deshacer`, `PiezaGuardada`) y `TallerDAO.PiezaDeReparacion`
  (Tasks 1 y 5); `AvisoTaller.Causa`.
- Produces:
  - `ReparacionDAO.insertarCompleta(…)` (las dos variantes) devuelve `List<String>`: las reparaciones creadas, una por fila con
    uso real (no solicitud) y en el orden de las filas.
  - `GuardadoReparacion` (`@Service`, `service`): `GuardadoReparacion(ReparacionDAO, TallerReparaciones)`;
    `@Transactional void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec, String idRepAnterior,
    String idAsignacion, String categoria, int idUsu)`; `@Transactional String guardarFilaIndividual(List<FilaReparacion> filas,
    String imei, int idTec, String idRepAnterior, String idAsignacion, int idUsu)`; `@Transactional void editarReparacion(String
    idRep, int idComNuevo, boolean esReutilizadoNuevo, String observacionNueva, int nNuevas, LocalDateTime updatedAt, int idUsu)`;
    `@Transactional void eliminar(String idRep, int idUsu)`; `static List<TallerReparaciones.PiezaGuardada> guardado(List<FilaReparacion>
    filas, List<String> creadas)`.
  - `ReparacionController(…, EntregaGlassService entregaGlass, GuardadoReparacion guardado)`: `completa`, `filas`, `PUT
    /{idRep}` y `DELETE /{idRep}` pasan por `guardado` con `principal.getIdUsu()`.

- [ ] **Step 1: Test del DAO (falla)**

Crea `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOCreadasTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/** «Terminar asignación» devuelve las reparaciones que crea (0.9.21, pieza 2 del SKU del taller: el taller las aplica). */
class ReparacionDAOCreadasTest {

    private static final String IMEI = "355400000000111";
    private static final String HOY = LocalDate.now(ZoneId.of("Europe/Madrid")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    /** Deja pasar las comprobaciones y numera los ID_REP como la base de datos: 1, 2, 3… */
    private static ReparacionDAO dao() {
        AtomicInteger siguiente = new AtomicInteger();
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            if (metodo.equals("queryForObject"))
                return String.valueOf(invocacion.getArgument(0)).contains("SUBSTRING_INDEX") ? siguiente.incrementAndGet() : 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            return RETURNS_DEFAULTS.answer(invocacion);
        });
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static FilaReparacion uso(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        return f;
    }

    private static FilaReparacion solicitud(int idCom) {
        FilaReparacion f = uso(idCom);
        f.esSolicitud = true;
        return f;
    }

    @Test void devuelveUnaReparacionPorFilaConUsoEnSuOrden() {
        assertEquals(List.of("R" + HOY + "_1", "R" + HOY + "_2"),
                dao().insertarCompleta(List.of(uso(101), solicitud(102), uso(131)), IMEI, 4, null, "A20260916_1", null));
    }

    @Test void unaGlassDevuelveSusG() {
        assertEquals(List.of("G" + HOY + "_1"), dao().insertarCompleta(List.of(uso(141)), IMEI, 4, null, "AG20260916_2", null));
    }

    @Test void sinFilasConUsoNoDevuelveNinguna() {
        assertEquals(List.of(), dao().insertarCompleta(List.of(), IMEI, 4, null, "A20260916_1", null));
        assertEquals(List.of(), dao().insertarCompleta(List.of(solicitud(102)), IMEI, 4, null, "A20260916_1"));
    }
}
```

Run: `mvn -q test -Dtest='ReparacionDAOCreadasTest'`
Expected: error de compilación (`insertarCompleta` devuelve `void`).

- [ ] **Step 2: El DAO devuelve las reparaciones creadas**

En `ReparacionDAO.java`:

1. Sustituye

```java
    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,
                                  String idRepAnterior, String idAsignacion) {
        insertarCompleta(filas, imei, idTec, idRepAnterior, idAsignacion, null);
    }
```

por

```java
    @Transactional
    public List<String> insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,
                                         String idRepAnterior, String idAsignacion) {
        return insertarCompleta(filas, imei, idTec, idRepAnterior, idAsignacion, null);
    }
```

2. En el Javadoc de la variante con `categoria`, tras «sin asignación (p.ej. altas desde la edición de un glass) decide
   {@code categoria}.», añade la línea « * Devuelve las reparaciones creadas (R… o G…), una por fila con uso real y en el orden
   de las filas: el SKU del taller las aplica (0.9.21).» y cambia su firma `public void insertarCompleta(List<FilaReparacion> filas,
   String imei, int idTec,` por `public List<String> insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,`.
3. Tras `Object[] entrega = entregaHeredable(idAsignacion);` (el de `insertarCompleta`), añade
   `List<String> creadas = new ArrayList<>();`.
4. Tras `creoReparacion = true;`, añade `creadas.add(idRep);`.
5. Al final del método, tras la `}` del `if (idAsignacion != null) { … }` que cierra la asignación, añade `return creadas;`.

Run: `mvn -q test -Dtest='ReparacionDAOCreadasTest,ReparacionDAOChasisTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Tests del guardado (fallan)**

Crea `src/test/java/com/reparaciones/servidor/service/GuardadoReparacionTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerDAO;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import com.reparaciones.servidor.nsys.TallerReparaciones.PiezaGuardada;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Las escrituras del formulario con el SKU del taller (0.9.21, pieza 2): la reparación y su efecto en el móvil. */
class GuardadoReparacionTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 9, 16, 7, 2);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TallerReparaciones taller = mock(TallerReparaciones.class);
    private final GuardadoReparacion guardado = new GuardadoReparacion(dao, taller);

    private static FilaReparacion fila(int idCom, boolean reutilizado, boolean solicitud) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = reutilizado ? 0 : 1;
        f.reutilizado = reutilizado;
        f.esSolicitud = solicitud;
        return f;
    }

    @Test void terminarAplicaCadaReparacionCreadaConSuFila() {
        List<FilaReparacion> filas = List.of(fila(101, false, false), fila(102, false, true), fila(131, true, false));
        when(dao.insertarCompleta(filas, IMEI, 4, null, ASIG, null)).thenReturn(List.of("R20260916_5", "R20260916_6"));
        guardado.insertarCompleta(filas, IMEI, 4, null, ASIG, null, 8);
        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 101, false),
                new PiezaGuardada("R20260916_6", 131, true)), 8);
    }

    @Test void guardarFilaAplicaLaReparacionQueDevuelveElDao() {
        List<FilaReparacion> filas = List.of(fila(131, false, false));
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        assertEquals("R20260916_5", guardado.guardarFilaIndividual(filas, IMEI, 4, null, ASIG, 8));
        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 131, false)), 8);
    }

    @Test void siLaReparacionFallaNoSeTocaElTaller() {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));
        assertThrows(ResponseStatusException.class, () -> guardado.insertarCompleta(List.of(fila(101, false, false)), IMEI, 4,
                null, ASIG, null, 8));
        verifyNoInteractions(taller);
    }

    @Test void editarLeeLaPiezaDeAntesEditaYDespuesAjustaElTaller() {
        TallerDAO.PiezaDeReparacion antes = new TallerDAO.PiezaDeReparacion("R20260916_5", IMEI, 131, false);
        when(taller.pieza("R20260916_5")).thenReturn(Optional.of(antes));
        guardado.editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED, 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).pieza("R20260916_5");
        orden.verify(dao).editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED);
        orden.verify(taller).editar(antes, new PiezaGuardada("R20260916_5", 133, true), 7);
    }

    @Test void editarUnaReparacionSinPiezaQueContarSoloEdita() {
        when(taller.pieza("G20260916_5")).thenReturn(Optional.empty());
        guardado.editarReparacion("G20260916_5", 141, false, null, 1, UPDATED, 7);
        verify(dao).editarReparacion("G20260916_5", 141, false, null, 1, UPDATED);
        verify(taller, never()).editar(any(), any(), anyInt());
    }

    @Test void borrarDeshaceAntesDeBorrar() {
        guardado.eliminar("R20260916_5", 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).deshacer("R20260916_5", AvisoTaller.Causa.BORRADA, 7);
        orden.verify(dao).eliminar("R20260916_5");
    }

    @Test void lasCuatroEscriturasSonUnaSolaTransaccion() throws NoSuchMethodException {
        assertNotNull(GuardadoReparacion.class.getMethod("insertarCompleta", List.class, String.class, int.class, String.class,
                String.class, String.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("guardarFilaIndividual", List.class, String.class, int.class,
                String.class, String.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("editarReparacion", String.class, int.class, boolean.class,
                String.class, int.class, LocalDateTime.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("eliminar", String.class, int.class).getAnnotation(Transactional.class));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/service/GuardadoReparacionTransaccionTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frontera transaccional REAL de GuardadoReparacion (spec 0.9.21 pieza 2 §2.1: «si el guardado falla, no queda ningún
 *  cambio»), con el mismo montaje que AsignacionLoteServiceTransaccionTest: contexto mínimo, DataSourceTransactionManager
 *  sobre un DataSource mock y los DAO como objetos normales (no beans, para que el auto-proxy no los envuelva). */
@SpringJUnitConfig(GuardadoReparacionTransaccionTest.Config.class)
class GuardadoReparacionTransaccionTest {

    private static final String IMEI = "355400000000111";
    private static final ReparacionDAO dao = mock(ReparacionDAO.class);
    private static final TallerReparaciones taller = mock(TallerReparaciones.class);

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        GuardadoReparacion guardadoReparacion() {
            return new GuardadoReparacion(dao, taller);
        }
    }

    @Autowired GuardadoReparacion guardado;
    @Autowired DataSource dataSource;

    private Connection connection;

    @BeforeEach
    void reiniciar() throws SQLException {
        reset(dataSource, dao, taller);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test void siElTallerFallaLaReparacionTampocoQueda() throws SQLException {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any())).thenReturn(List.of("R1"));
        doThrow(new DataAccessResourceFailureException("BD caída")).when(taller).aplicar(any(), any(), anyInt());
        assertThrows(DataAccessResourceFailureException.class,
                () -> guardado.insertarCompleta(List.of(), IMEI, 4, null, "A1", null, 8));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void siLaReparacionFallaNoSeTocaElTallerYNadaQueda() throws SQLException {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));
        assertThrows(ResponseStatusException.class, () -> guardado.insertarCompleta(List.of(), IMEI, 4, null, "A1", null, 8));
        verifyNoInteractions(taller);
        verify(connection).rollback();
    }

    @Test void siBorrarFallaTrasDeshacerNoQuedaDeshecho() throws SQLException {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado")).when(dao).eliminar("R1");
        assertThrows(ResponseStatusException.class, () -> guardado.eliminar("R1", 7));
        verify(taller).deshacer("R1", AvisoTaller.Causa.BORRADA, 7);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void elCaminoFelizHaceCommit() throws SQLException {
        when(dao.guardarFilaIndividual(any(), any(), anyInt(), any(), any())).thenReturn("R1");
        guardado.guardarFilaIndividual(List.of(), IMEI, 4, null, "A1", 8);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
```

Run: `mvn -q test -Dtest='GuardadoReparacionTest,GuardadoReparacionTransaccionTest'`
Expected: error de compilación: no existe `GuardadoReparacion`.

- [ ] **Step 4: `GuardadoReparacion`**

Crea `src/main/java/com/reparaciones/servidor/service/GuardadoReparacion.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerDAO;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Las escrituras del formulario de reparación con el SKU del taller (spec 0.9.21, pieza 2, §2.1 y §2.3): la reparación y lo
 *  que cambia en el móvil, en la misma transacción (las de ReparacionDAO son @Transactional REQUIRED y se unen a esta): si
 *  algo falla, no queda nada. Las usa ReparacionController en «Terminar asignación», «✓ Guardar fila», «Guardar cambios» y
 *  el borrado de una reparación. El «Abrir» automático de glass (EntregaGlassService) sigue yendo al DAO: es una acción
 *  «otro», que no cambia el móvil. */
@Service
public class GuardadoReparacion {

    private final ReparacionDAO dao;
    private final TallerReparaciones taller;

    public GuardadoReparacion(ReparacionDAO dao, TallerReparaciones taller) {
        this.dao = dao;
        this.taller = taller;
    }

    /** «Terminar asignación» y las filas nuevas de «Guardar cambios»: un guardado, en el orden de sus filas. */
    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec, String idRepAnterior,
                                 String idAsignacion, String categoria, int idUsu) {
        List<String> creadas = dao.insertarCompleta(filas, imei, idTec, idRepAnterior, idAsignacion, categoria);
        taller.aplicar(imei, guardado(filas, creadas), idUsu);
    }

    /** «✓ Guardar fila» (la web manda una fila con uso; el DAO devuelve su R…). */
    @Transactional
    public String guardarFilaIndividual(List<FilaReparacion> filas, String imei, int idTec, String idRepAnterior,
                                        String idAsignacion, int idUsu) {
        String idRep = dao.guardarFilaIndividual(filas, imei, idTec, idRepAnterior, idAsignacion);
        List<FilaReparacion> conUso = filas.stream().filter(f -> !f.esSolicitud).toList();
        if (!conUso.isEmpty()) {
            FilaReparacion f = conUso.get(conUso.size() - 1);
            taller.aplicar(imei, List.of(new TallerReparaciones.PiezaGuardada(idRep, f.idCom, f.reutilizado)), idUsu);
        }
        return idRep;
    }

    /** «Guardar cambios» de una reparación: la pieza de antes se lee antes de editar; si cambia, el taller deshace lo suyo y
     *  aplica la nueva. */
    @Transactional
    public void editarReparacion(String idRep, int idComNuevo, boolean esReutilizadoNuevo, String observacionNueva,
                                 int nNuevas, LocalDateTime updatedAt, int idUsu) {
        Optional<TallerDAO.PiezaDeReparacion> antes = taller.pieza(idRep);
        dao.editarReparacion(idRep, idComNuevo, esReutilizadoNuevo, observacionNueva, nNuevas, updatedAt);
        antes.ifPresent(a -> taller.editar(a, new TallerReparaciones.PiezaGuardada(idRep, idComNuevo, esReutilizadoNuevo), idUsu));
    }

    /** Borrar una reparación: primero se deshace lo suyo en el móvil. */
    @Transactional
    public void eliminar(String idRep, int idUsu) {
        taller.deshacer(idRep, AvisoTaller.Causa.BORRADA, idUsu);
        dao.eliminar(idRep);
    }

    /** Cada reparación creada con su fila: el DAO crea una por fila con uso real, en su orden. */
    static List<TallerReparaciones.PiezaGuardada> guardado(List<FilaReparacion> filas, List<String> creadas) {
        List<FilaReparacion> conUso = filas.stream().filter(f -> !f.esSolicitud).toList();
        List<TallerReparaciones.PiezaGuardada> guardado = new ArrayList<>();
        for (int i = 0; i < Math.min(conUso.size(), creadas.size()); i++)
            guardado.add(new TallerReparaciones.PiezaGuardada(creadas.get(i), conUso.get(i).idCom, conUso.get(i).reutilizado));
        return guardado;
    }
}
```

Run: `mvn -q test -Dtest='GuardadoReparacionTest,GuardadoReparacionTransaccionTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: El controlador pasa por `GuardadoReparacion`**

En `ReparacionController.java`:

1. Imports: tras `import com.reparaciones.servidor.service.EntregaGlassService;`, añade
   `import com.reparaciones.servidor.service.GuardadoReparacion;`.
2. Tras `private final EntregaGlassService entregaGlass;`, añade `private final GuardadoReparacion guardado;`.
3. En el constructor, cambia `CargaAsignacionesService cargaAsignaciones, EntregaGlassService entregaGlass) {` por
   `CargaAsignacionesService cargaAsignaciones, EntregaGlassService entregaGlass, GuardadoReparacion guardado) {` y, tras
   `this.entregaGlass = entregaGlass;`, añade `this.guardado = guardado;`.
4. En `insertarCompleta`, sustituye

```java
                () -> {
                    dao.insertarCompleta(req.filas(), req.imei(), idTec,
                            req.idRepAnterior(), req.idAsignacion(), req.categoria());
                    return null;
                },
```

por

```java
                () -> {
                    // 0.9.21: con el SKU del taller, en la misma transacción.
                    guardado.insertarCompleta(req.filas(), req.imei(), idTec,
                            req.idRepAnterior(), req.idAsignacion(), req.categoria(), principal.getIdUsu());
                    return null;
                },
```

5. En `editarReparacion`, sustituye

```java
                    dao.editarReparacion(idRep, req.idComNuevo(), req.esReutilizadoNuevo(),
                            req.observacionNueva(), req.nNuevas(), req.updatedAt());
```

por

```java
                    guardado.editarReparacion(idRep, req.idComNuevo(), req.esReutilizadoNuevo(),
                            req.observacionNueva(), req.nNuevas(), req.updatedAt(), principal.getIdUsu());
```

6. En `guardarFilaIndividual`, sustituye

```java
                () -> new ValorTexto(dao.guardarFilaIndividual(req.filas(), req.imei(), idTec,
                        req.idRepAnterior(), idAsignacion)),
```

por

```java
                () -> new ValorTexto(guardado.guardarFilaIndividual(req.filas(), req.imei(), idTec,
                        req.idRepAnterior(), idAsignacion, principal.getIdUsu())),
```

7. En `eliminar` (el de `@DeleteMapping("/{idRep}")`), cambia `dao.eliminar(idRep);` por `guardado.eliminar(idRep, principal.getIdUsu());`.

En los siete tests que construyen el controlador (`FiltroTecnicoControllersTest`, `IdempotenciaReparacionControllerTest`,
`PropiedadAsignacionControllersTest`, `ReparacionControllerContadoresTest`, `ReparacionControllerCorregirClienteTest`,
`ReparacionControllerEliminarTest` y `ReparacionControllerEntregaGlassTest`):
- imports: `com.reparaciones.servidor.nsys.TallerReparaciones` y `com.reparaciones.servidor.service.GuardadoReparacion`;
- el último argumento del `new ReparacionController(…)` pasa a ir seguido de `, new GuardadoReparacion(dao, mock(TallerReparaciones.class))`:
  en seis de ellos, `mock(CargaAsignacionesService.class), mock(EntregaGlassService.class));` queda
  `mock(CargaAsignacionesService.class), mock(EntregaGlassService.class), new GuardadoReparacion(dao, mock(TallerReparaciones.class)));`;
  en `ReparacionControllerEntregaGlassTest`, `mock(CargaAsignacionesService.class), servicio);` queda
  `mock(CargaAsignacionesService.class), servicio, new GuardadoReparacion(dao, mock(TallerReparaciones.class)));`.
  Sus verificaciones contra el DAO no cambian: `GuardadoReparacion` le llama con los mismos argumentos.

En `RolesReparacionFormularioTest.java` (contexto entero; sin base de datos, la transacción real de `GuardadoReparacion` no
podría abrirse):
1. Imports: `com.reparaciones.servidor.nsys.TallerReparaciones`, `org.springframework.transaction.PlatformTransactionManager` y
   `java.util.List`.
2. Tras `@MockBean DificultadPuntosDAO dificultadDao;`, añade

```java
    /** 0.9.21: las escrituras del formulario pasan por GuardadoReparacion (@Transactional): sin MariaDB, el gestor de
     *  transacciones va mockeado, como en RolesParametrosPrevisionTest, y el taller también. */
    @MockBean TallerReparaciones taller;
    @MockBean PlatformTransactionManager transactionManager;
```

3. Añade al final de la clase:

```java
    /** 0.9.21 (pieza 2 del SKU del taller): «✓ Guardar fila» avisa al taller con la reparación creada y el usuario del token. */
    @Test void filasSiendoTecnicoAvisaAlTaller() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(any(), eq(IMEI), eq(4), isNull(), eq(ASIG))).thenReturn("R20260916_5");
        int status = mvc.perform(post("/api/reparaciones/" + ASIG + "/filas")
                        .header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_FILAS))
                .andReturn().getResponse().getStatus();
        assertEquals(201, status);
        verify(taller).aplicar(IMEI, List.of(new TallerReparaciones.PiezaGuardada("R20260916_5", 101, false)), 8);
    }
```

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='ReparacionDAOCreadasTest,ReparacionDAOChasisTest,GuardadoReparacionTest,GuardadoReparacionTransaccionTest,FiltroTecnicoControllersTest,IdempotenciaReparacionControllerTest,PropiedadAsignacionControllersTest,ReparacionControllerContadoresTest,ReparacionControllerCorregirClienteTest,ReparacionControllerEliminarTest,ReparacionControllerEntregaGlassTest,RolesReparacionFormularioTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/service/GuardadoReparacion.java \
  src/main/java/com/reparaciones/servidor/controller/ReparacionController.java \
  src/test/java/com/reparaciones/servidor/dao/ReparacionDAOCreadasTest.java \
  src/test/java/com/reparaciones/servidor/service/GuardadoReparacionTest.java src/test/java/com/reparaciones/servidor/service/GuardadoReparacionTransaccionTest.java \
  src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java src/test/java/com/reparaciones/servidor/controller/IdempotenciaReparacionControllerTest.java \
  src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerContadoresTest.java \
  src/test/java/com/reparaciones/servidor/controller/ReparacionControllerCorregirClienteTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEliminarTest.java \
  src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java src/test/java/com/reparaciones/servidor/controller/RolesReparacionFormularioTest.java
git commit -m "feat: guardar, editar y borrar reparaciones cambia el movil en la misma transaccion (0.9.21, pieza 2)"
```

---

### Task 7: Servidor — la ruta de la previsualización, sus roles y el contrato

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/SkuTallerController.java`
- Create (test): `…/nsys/SkuTallerControllerTest.java`, `src/test/java/com/reparaciones/servidor/controller/RolesSkuTallerTest.java`
- Modify (test): `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `TallerReparaciones.previsualizar`, `PeticionPreviaTaller`, `FilaPreviaTaller`, `PreviaTaller`, `ValoresTaller` (Task 5).
- Produces: `POST /api/reparaciones/sku-taller/previsualizar` (cuerpo `PeticionPreviaTaller`, respuesta 200 `PreviaTaller`),
  TECNICO, SUPERTECNICO y ADMIN. Esquemas `PeticionPreviaTaller`, `FilaPreviaTaller`, `PreviaTaller`, `ValoresTaller`.

- [ ] **Step 1: Tests (fallan)**

Crea `SkuTallerControllerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SkuTallerControllerTest {

    @Test void laPreviaSaleDelServicio() {
        TallerReparaciones taller = mock(TallerReparaciones.class);
        PeticionPreviaTaller peticion = new PeticionPreviaTaller("350000000000001", List.of(new FilaPreviaTaller(null, 131, false)));
        PreviaTaller previa = new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Rojo", "A"), false, false);
        when(taller.previsualizar(peticion)).thenReturn(previa);
        assertEquals(previa, new SkuTallerController(taller).previsualizar(peticion));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/controller/RolesSkuTallerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.nsys.FilaPreviaTaller;
import com.reparaciones.servidor.nsys.PeticionPreviaTaller;
import com.reparaciones.servidor.nsys.PreviaTaller;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** La previsualización del SKU del taller (spec 0.9.21 pieza 2 §2.4): es el formulario del técnico, así que la piden técnico,
 *  supertécnico y admin; sin sesión, 403. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesSkuTallerTest {

    private static final String RUTA = "/api/reparaciones/sku-taller/previsualizar";
    private static final String CUERPO =
            "{\"imei\":\"350000000000001\",\"filas\":[{\"idRep\":null,\"idCom\":131,\"reutilizada\":false}]}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TallerReparaciones taller;

    private String token(String rol, Integer idTec) {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(9, "usuario_prueba", "", rol, idTec));
    }

    @Test void tecnicoSupertecnicoYAdminPrevisualizan() throws Exception {
        when(taller.previsualizar(any())).thenReturn(PreviaTaller.FUERA_DE_NSYS);
        for (String auth : List.of(token("TECNICO", 4), token("SUPERTECNICO", 3), token("ADMIN", null))) {
            MockHttpServletResponse res = mvc.perform(post(RUTA).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andReturn().getResponse();
            assertEquals(200, res.getStatus(), auth);
            assertTrue(res.getContentAsString().contains("\"enNsys\":false"), res.getContentAsString());
        }
        verify(taller, times(3)).previsualizar(new PeticionPreviaTaller("350000000000001",
                List.of(new FilaPreviaTaller(null, 131, false))));
    }

    @Test void sinSesionEs403SinLlegarAlServicio() throws Exception {
        int status = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(taller);
    }
}
```

En `OpenApiContractTest.java`, añade tras el método `elContratoPublicaElStockDeTelefonos`:

```java
    /** 0.9.21 (spec «SKU del taller», pieza 2, §2.4): la banda del formulario. */
    @Test void elContratoPublicaLaPreviaDelSkuDelTaller() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");
        String ruta = "/api/reparaciones/sku-taller/previsualizar";
        assertTrue(paths.has(ruta), "falta la ruta de la previsualización del SKU del taller");
        assertTrue(refDelCuerpo(paths, ruta, "post").endsWith("/PeticionPreviaTaller"));
        assertTrue(refDeLaRespuesta(paths, ruta, "post", "200").endsWith("/PreviaTaller"));
        assertNoNullable(esquemas, "PeticionPreviaTaller", "imei", "filas");
        assertNullable(esquemas, "FilaPreviaTaller", "idRep");
        assertNoNullable(esquemas, "FilaPreviaTaller", "idCom", "reutilizada");
        assertNoNullable(esquemas, "PreviaTaller", "enNsys", "antes", "despues", "colorSinDeducir", "gradoSinCalcular");
        assertNullable(esquemas, "ValoresTaller", "color", "grado");
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='SkuTallerControllerTest,RolesSkuTallerTest,OpenApiContractTest'`
Expected: error de compilación (no existe `SkuTallerController`).

- [ ] **Step 3: Implementación**

Crea `SkuTallerController.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El aviso del SKU del taller en el formulario de reparación (spec 0.9.21, pieza 2, §2.4). Es el formulario del técnico:
 *  técnico, supertécnico y admin; no devuelve precios ni stock. Va aparte de ReparacionController para no tocar sus
 *  guardas (Ajuste 7). Es una lectura: sin registro de actividad ni clave de idempotencia. */
@RestController
@RequestMapping("/api/reparaciones/sku-taller")
@PreAuthorize("hasAnyRole('TECNICO', 'SUPERTECNICO', 'ADMIN')")
public class SkuTallerController {

    private final TallerReparaciones taller;

    public SkuTallerController(TallerReparaciones taller) {
        this.taller = taller;
    }

    /** Lo que el guardado de estas filas cambiará en el móvil, con la misma cuenta que al guardar. */
    @PostMapping("/previsualizar")
    public PreviaTaller previsualizar(@RequestBody PeticionPreviaTaller peticion) {
        return taller.previsualizar(peticion);
    }
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='SkuTallerControllerTest,RolesSkuTallerTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS` y `target/openapi.json` con la ruta nueva
(`grep -c "sku-taller/previsualizar" target/openapi.json` da 1 o más).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/SkuTallerController.java src/test/java/com/reparaciones/servidor/nsys/SkuTallerControllerTest.java \
  src/test/java/com/reparaciones/servidor/controller/RolesSkuTallerTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: previsualizacion del SKU del taller para el formulario de reparacion (0.9.21, pieza 2)"
```

---

### Task 8: Servidor — el rastro con los avisos del taller

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Rastro.java`, `TallerNsys.java`,
  `StockTelefonosController.java`
- Test: `…/nsys/RastroTest.java`, `…/nsys/StockTelefonosControllerTest.java`

**Interfaces:**
- Consumes: `AvisoTaller`, `TallerDAO.avisosDeImei` (Task 1); `EventoRastro` y `Rastro` con la forma de la corrección y de la
  pieza 1 (Global Constraints).
- Produces:
  - `Rastro.unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos, List<CambioTaller> cambios,
    List<AvisoTaller> avisos)`; la de cuatro listas llama a esta con `List.of()`.
  - Eventos de fuente `TALLER` y tipo `TALLER_SIN_COLOR`, `TALLER_SIN_GRADO`, `TALLER_DESHECHO_BORRADA`,
    `TALLER_DESHECHO_EDITADA`, `TALLER_SIN_DESHACER_BORRADA` y `TALLER_SIN_DESHACER_EDITADA`, con `dato`, `de`, `a`, `idRep`,
    `tecnico` (quien guardó), `origen` = REPARACION y `motivo` (la pieza). Sin campos nuevos en `EventoRastro`.
  - `TallerNsys.avisos(String imeiSn): List<AvisoTaller>`.

- [ ] **Step 1: Tests (fallan)**

En `RastroTest.java`, añade al final de la clase (imports: `java.time.LocalDateTime` si no está):

```java
    @Test void losAvisosDelTallerSalenConSuTipoYDetrasDeLosCambiosALaMismaHora() {
        // 0.9.21 (pieza 2 §2.5): al editar una reparación, «Se deshace…» y el cambio nuevo llevan la misma hora; el cambio
        // es lo último y sale primero.
        String imei = "350000000000001";
        LocalDateTime hora = LocalDateTime.of(2026, 10, 21, 10, 0);
        CambioTaller nuevo = new CambioTaller(7, imei, DatoTaller.COLOR, "Verde", "Azul", "Azul", "REPARACION", "R20261021_3", 8,
                "Técnico 2", "chasis chai13green nuevo", hora, CambioTaller.Estado.VIGENTE, null, null, null);
        AvisoTaller deshecho = new AvisoTaller(1, imei, "R20261021_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA,
                DatoTaller.COLOR, "Rojo", "Azul", "chasis chai13red nuevo", 8, "Técnico 2", hora);
        AvisoTaller sinGrado = new AvisoTaller(2, imei, "R20261021_4", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO, "ID",
                null, "batería bati13 nueva", 8, "Técnico 2", hora.minusHours(1));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(nuevo), List.of(deshecho, sinGrado));
        assertEquals(List.of("TALLER_CAMBIO", "TALLER_DESHECHO_EDITADA", "TALLER_SIN_GRADO"),
                rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora, false, "TALLER", "TALLER_DESHECHO_EDITADA", null, "Rojo", "Azul", null,
                "R20261021_3", "Técnico 2", null, null, null, "COLOR", "REPARACION", "chasis chai13red nuevo"), rastro.get(1));
        assertEquals(new EventoRastro(hora.minusHours(1), false, "TALLER", "TALLER_SIN_GRADO", null, "ID", null, null,
                "R20261021_4", "Técnico 2", null, null, null, "GRADO", "REPARACION", "batería bati13 nueva"), rastro.get(2));
        assertEquals("REPARACION", rastro.get(0).origen());
        assertEquals("R20261021_3", rastro.get(0).idRep());
    }

    @Test void sinAvisosComoAntes() {
        assertEquals(Rastro.unir(List.of(), List.of(), List.of(), List.of()),
                Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of()));
    }
```

En `StockTelefonosControllerTest.java`, añade al final de la clase:

```java
    @Test void elRastroLlevaLosAvisosDelTaller() {
        // 0.9.21 (pieza 2 §2.5)
        when(tallerDao.avisosDeImei(IMEI)).thenReturn(List.of(new AvisoTaller(1, IMEI, "R20261021_3", AvisoTaller.Tipo.SIN_COLOR,
                null, DatoTaller.COLOR, "chai13xxx", null, "chasis chai13xxx nuevo", 8, "Técnico 2",
                LocalDateTime.of(2026, 10, 21, 10, 0))));
        List<EventoRastro> rastro = api.rastro(IMEI);
        assertEquals("TALLER_SIN_COLOR", rastro.get(0).tipo());
        assertEquals("chai13xxx", rastro.get(0).de());
        assertEquals("Técnico 2", rastro.get(0).tecnico());
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='RastroTest,StockTelefonosControllerTest'`
Expected: error de compilación: `Rastro.unir` no tiene cinco parámetros.

- [ ] **Step 3: Implementación**

En `Rastro.java`:

1. Cambia la firma de la versión de `unir` que recibe `cambios` (la de cuatro listas de la pieza 1) por
   `public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos, List<CambioTaller> cambios, List<AvisoTaller> avisos) {`
   y, en su Javadoc, añade al final « Desde la pieza 2, los avisos del taller (TALLER_SIN_COLOR, TALLER_SIN_GRADO,
   TALLER_DESHECHO_* y TALLER_SIN_DESHACER_*), a su hora; a la misma hora que un cambio, detrás de él.».
2. Justo encima, añade la de cuatro listas, que llama a la nueva:

```java
    public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos,
                                          List<CambioTaller> cambios) {
        return unir(lotes, nsys, trabajos, cambios, List.of());
    }
```

3. En la de cinco, justo antes del bucle `for (CambioTaller c : cambios) {`, añade:

```java
        // 0.9.21, pieza 2: los avisos van antes que los cambios en la lista para que, a la misma hora (una edición: se
        // deshace lo suyo y se aplica la pieza nueva en el mismo segundo), el cambio salga primero.
        for (AvisoTaller a : avisos) todos.add(deAviso(a));
```

4. Tras el método `deTaller`, añade:

```java

    /** «TALLER_» + el tipo del aviso (+ «_BORRADA» o «_EDITADA»). {@code tecnico}: quien guardó; {@code motivo}: la pieza. */
    private static EventoRastro deAviso(AvisoTaller a) {
        String tipo = "TALLER_" + a.tipo().name() + (a.causa() == null ? "" : "_" + a.causa().name());
        return new EventoRastro(a.fecha(), false, "TALLER", tipo, null, a.de(), a.a(), null, a.idRep(), a.usuario(), null, null,
                null, a.dato().name(), CambioTaller.REPARACION, a.motivo());
    }
```

En `TallerNsys.java`, tras el método `cambios(String imeiSn)`, añade:

```java

    /** Los avisos del taller de un IMEI (pieza 2), para el rastro. */
    public List<AvisoTaller> avisos(String imeiSn) {
        return tallerDao.avisosDeImei(imeiSn);
    }
```

En `StockTelefonosController.java`, en el método `rastro`, añade `, taller.avisos(imeiSn)` como último argumento de
`Rastro.unir(…)`.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='RastroTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Suite entera del servidor**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, sin fallos; `target/openapi.json` queda al día (lo copia la Task 9). Anota el total en
`.superpowers/sdd/v0921-sku/progress.md`, sección «Pieza 2».

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java \
  src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/RastroTest.java \
  src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java
git commit -m "feat: el rastro con los avisos del taller desde las reparaciones (0.9.21, pieza 2)"
```

---

### Task 9: Web — contrato, tipos y `usePreviaTaller`

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts`
- Modify: `gestion-reparaciones-web/src/test/server.ts`, `src/modules/taller/test/fabrica.ts`
- Create: `gestion-reparaciones-web/src/modules/taller/formulario/usePreviaTaller.ts`, `usePreviaTaller.test.tsx`

**Interfaces:**
- Consumes: el contrato de las Tasks 1-8 (`/c/Users/dev/Documents/wt/servidor-0921-sku/target/openapi.json`).
- Produces:
  - Alias en `client.ts`: `PreviaTaller`, `ValoresTaller`, `PeticionPreviaTaller`, `FilaPreviaTaller`.
  - `usePreviaTaller(peticion: PeticionPreviaTaller | null, retrasoMs = RETARDO_PREVIA_MS): PreviaTaller | null` y
    `RETARDO_PREVIA_MS = 500` en `modules/taller/formulario/usePreviaTaller.ts`.
  - Fábrica `previaTaller(p)` en `modules/taller/test/fabrica.ts`: por defecto `enNsys` true, Azul · B → Azul · B, sin avisos.
  - MSW por defecto: `POST */api/reparaciones/sku-taller/previsualizar` → fuera de NSYS (sin banda).

- [ ] **Step 1: Contrato, tipos y alias**

Run (en `/c/Users/dev/Documents/wt/web-0921-sku`):
`cp /c/Users/dev/Documents/wt/servidor-0921-sku/target/openapi.json api/openapi.json && npm run api:types:offline`
Expected: `grep -c "PreviaTaller\|sku-taller/previsualizar" src/shared/api/schema.d.ts` da más de 0.

En `src/shared/api/client.ts`, tras el bloque de alias de la pieza 1 (`… export type PeticionTaller = …`), añade:

```ts
/** 0.9.21 (spec «SKU del taller», pieza 2): la banda del formulario de reparación. Se piden las filas que se van a guardar
 *  (`idRep` null en las nuevas; el de la reparación que se edita) y llega lo que vale ahora y lo que valdrá (iguales si nada
 *  cambia), con los avisos; `enNsys` false: NSYS no conoce el IMEI. */
export type PreviaTaller = components['schemas']['PreviaTaller']
export type ValoresTaller = components['schemas']['ValoresTaller']
export type PeticionPreviaTaller = components['schemas']['PeticionPreviaTaller']
export type FilaPreviaTaller = components['schemas']['FilaPreviaTaller']
```

- [ ] **Step 2: Fábrica y MSW**

En `src/modules/taller/test/fabrica.ts`:
1. En el `import type { … } from '@/shared/api/client'`, añade `PreviaTaller`.
2. Al final del fichero, añade:

```ts
/** 0.9.21 (pieza 2 del SKU del taller): la previa de la banda; por defecto, un móvil de NSYS Azul · B al que nada cambia. */
export function previaTaller(p: Partial<PreviaTaller> = {}): PreviaTaller {
  return {
    enNsys: true, antes: { color: 'Azul', grado: 'B' }, despues: { color: 'Azul', grado: 'B' }, colorSinDeducir: false,
    gradoSinCalcular: false, ...p,
  }
}
```

En `src/test/server.ts`, tras la línea de `http.post('*/api/auth/evaluar-password', …)`, añade:

```ts
  // 0.9.21 (pieza 2 del SKU del taller): el formulario de reparación pide la previa de la banda al elegir un chasis, una tapa
  // o una batería. Por defecto, NSYS no conoce el IMEI: no hay banda y el formulario deja de preguntar.
  http.post('*/api/reparaciones/sku-taller/previsualizar', () => HttpResponse.json({
    enNsys: false, antes: { color: null, grado: null }, despues: { color: null, grado: null }, colorSinDeducir: false,
    gradoSinCalcular: false,
  })),
```

y añade al final del comentario de cabecera de `server`: «Y desde la 0.9.21, la previa del SKU del taller (fuera de NSYS).»

- [ ] **Step 3: Tests del hook (fallan)**

Crea `src/modules/taller/formulario/usePreviaTaller.test.tsx`:

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { PeticionPreviaTaller } from '@/shared/api/client'
import { crearQueryClient } from '@/shared/api/queryClient'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { guardarSesion } from '@/shared/session/storage'
import { server } from '@/test/server'
import { SESION_TEC } from '@/test/render'
import { previaTaller } from '../test/fabrica'
import { usePreviaTaller } from './usePreviaTaller'

const IMEI = '355400000000111'
const BATERIA: PeticionPreviaTaller = { imei: IMEI, filas: [{ idRep: null, idCom: 101, reutilizada: false }] }
const CHASIS: PeticionPreviaTaller = { imei: IMEI, filas: [{ idRep: null, idCom: 131, reutilizada: true }] }
const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms))

function envoltorio() {
  guardarSesion(SESION_TEC)
  const qc = crearQueryClient({ retry: false })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider>{children}</SessionProvider></QueryClientProvider>
  )
}

/** Responde con `respuesta` y apunta los cuerpos que llegan. */
function servir(respuesta: () => Response): unknown[] {
  const cuerpos: unknown[] = []
  server.use(http.post('*/api/reparaciones/sku-taller/previsualizar', async ({ request }) => {
    cuerpos.push(await request.json())
    return respuesta()
  }))
  return cuerpos
}

describe('usePreviaTaller (spec 0.9.21 pieza 2 §2.4)', () => {
  it('pide la previa con las filas y devuelve lo que dice el servidor', async () => {
    const cuerpos = servir(() => HttpResponse.json(previaTaller({ despues: { color: 'Azul', grado: 'B100' } })))
    const { result } = renderHook(() => usePreviaTaller(BATERIA, 0), { wrapper: envoltorio() })
    await waitFor(() => expect(result.current?.despues.grado).toBe('B100'))
    expect(cuerpos).toEqual([BATERIA])
  })

  it('sin filas que cuenten no pide nada', async () => {
    const cuerpos = servir(() => HttpResponse.json(previaTaller()))
    const { result } = renderHook(() => usePreviaTaller(null, 0), { wrapper: envoltorio() })
    await esperar(50)
    expect(result.current).toBeNull()
    expect(cuerpos).toEqual([])
  })

  it('un IMEI que NSYS no conoce: tras la primera respuesta deja de pedir', async () => {
    const cuerpos = servir(() => HttpResponse.json(previaTaller({ enNsys: false })))
    const { result, rerender } = renderHook(({ p }) => usePreviaTaller(p, 0), { wrapper: envoltorio(), initialProps: { p: BATERIA } })
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    rerender({ p: CHASIS })
    await esperar(50)
    expect(cuerpos).toHaveLength(1)
    expect(result.current).toBeNull()
  })

  it('si la previa falla (red), no hay previa y no rompe', async () => {
    const cuerpos = servir(() => HttpResponse.error())
    const { result } = renderHook(() => usePreviaTaller(BATERIA, 0), { wrapper: envoltorio() })
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    await esperar(50)
    expect(result.current).toBeNull()
  })

  it('espera al retraso: dos cambios seguidos hacen una sola petición, con lo último', async () => {
    const cuerpos = servir(() => HttpResponse.json(previaTaller()))
    const { rerender } = renderHook(({ p }) => usePreviaTaller(p, 100),
      { wrapper: envoltorio(), initialProps: { p: null as PeticionPreviaTaller | null } })
    rerender({ p: BATERIA })
    rerender({ p: CHASIS })
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    await esperar(150)
    expect(cuerpos).toEqual([CHASIS])
  })
})
```

Run: `npx vitest run src/modules/taller/formulario/usePreviaTaller.test.tsx`
Expected: FAIL (no existe `./usePreviaTaller`).

- [ ] **Step 4: El hook**

Crea `src/modules/taller/formulario/usePreviaTaller.ts`:

```ts
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, type PeticionPreviaTaller, type PreviaTaller } from '@/shared/api/client'

/** Lo que espera la banda tras el último cambio de filas antes de preguntar: el mismo patrón de temporizador que el
 *  autoguardado del borrador, pero corto (el aviso tiene que verse antes del segundo clic de «Terminar»; Ajuste 12). */
export const RETARDO_PREVIA_MS = 500

/** El valor, `ms` después de su último cambio. */
function useDiferido<T>(valor: T, ms: number): T {
  const [diferido, setDiferido] = useState(valor)
  useEffect(() => {
    const t = setTimeout(() => setDiferido(valor), ms)
    return () => clearTimeout(t)
  }, [valor, ms])
  return diferido
}

/** POST /api/reparaciones/sku-taller/previsualizar (0.9.21, spec «SKU del taller», pieza 2, §2.4): lo que el guardado
 *  cambiará en el móvil. Pregunta con un pequeño retraso tras cada cambio de filas; sin capa de carga y sin diálogo de
 *  error (si falla, no hay banda: el guardado aplica igual). Un IMEI que NSYS no conoce (`enNsys` false en una respuesta) no
 *  vuelve a preguntar mientras el formulario siga abierto (Ajuste 13). null: nada que enseñar. */
export function usePreviaTaller(peticion: PeticionPreviaTaller | null, retrasoMs = RETARDO_PREVIA_MS): PreviaTaller | null {
  const diferida = useDiferido(peticion === null ? null : JSON.stringify(peticion), retrasoMs)
  const [fueraDeNsys, setFueraDeNsys] = useState<string | null>(null)
  const activa = peticion !== null && diferida !== null && fueraDeNsys !== peticion.imei
  const previa = useQuery({
    queryKey: ['formulario', 'previa-taller', diferida],
    enabled: activa,
    queryFn: async () => {
      const cuerpo = JSON.parse(diferida ?? 'null') as PeticionPreviaTaller
      const datos = (await api.POST('/api/reparaciones/sku-taller/previsualizar', { body: cuerpo })).data ?? null
      if (datos !== null && !datos.enNsys) setFueraDeNsys(cuerpo.imei)
      return datos
    },
    // Mientras llega la previa de las filas nuevas, la banda sigue con la anterior: sin parpadeos.
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
    meta: { silenciarError: true, sinCapa: true },
  })
  return activa ? (previa.data ?? null) : null
}
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npx vitest run src/modules/taller/formulario`
Expected: `tsc` limpio y todo en verde (los tests de siempre del formulario no cambian: la previa por defecto está fuera de NSYS
y nadie la pinta todavía).

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/test/server.ts src/modules/taller/test/fabrica.ts \
  src/modules/taller/formulario/usePreviaTaller.ts src/modules/taller/formulario/usePreviaTaller.test.tsx
git commit -m "feat: contrato y consulta de la previa del SKU del taller en el formulario (0.9.21, pieza 2)"
```

---

### Task 10: Web — la banda encima de «Terminar asignación» / «Guardar cambios»

**Files:**
- Create: `gestion-reparaciones-web/src/modules/taller/formulario/skuTaller.ts`, `skuTaller.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/ZonaGuardar.tsx` (entero), `FormularioReparacion.tsx`
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/FormularioReparacion.test.tsx`

**Interfaces:**
- Consumes: `usePreviaTaller` (Task 9); `planTerminar`, `planGuardarCambios`, `zonaGuardarVisible` (`estado.ts`);
  `PREFIJO_CHASIS`, `PREFIJO_TAPA` (`../lib/colores`); fábrica `previaTaller`.
- Produces:
  - En `skuTaller.ts`: `PREFIJOS_SKU_TALLER`, `peticionPreviaTaller(e: EstadoFormulario): PeticionPreviaTaller | null` y
    `textoBandaTaller(p: PreviaTaller | null): string | null`.
  - `ZonaGuardar({ estado, aviso?, onPulsar })`: con `aviso`, la banda `data-testid="banda-sku-taller"` (`role="status"`)
    encima del botón.

- [ ] **Step 1: Tests de las reglas (fallan)**

Crea `src/modules/taller/formulario/skuTaller.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, detalleEdicion, previaTaller } from '../test/fabrica'
import { estadoInicial, reducir, type AccionFormulario, type DatosEditar, type DatosNuevo, type EstadoFormulario } from './estado'
import { peticionPreviaTaller, textoBandaTaller } from './skuTaller'

const IMEI = '355400000000111'

function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return {
    modo: 'nuevo', idAsignacion: 'A20260916_1', imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null,
    modeloTelefono: null, ...parcial,
  }
}
function datosEditar(parcial: Partial<DatosEditar> = {}): DatosEditar {
  return {
    modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: [],
    ...parcial,
  }
}
const aplicar = (e: EstadoFormulario, ...acciones: AccionFormulario[]) => acciones.reduce(reducir, e)
const conModelo = (parcial: Partial<DatosNuevo> = {}) => reducir(estadoInicial(datosNuevo(parcial)), { tipo: 'CAMBIAR_MODELO', modelo: '13' })
const CHASIS_REUTILIZADO: AccionFormulario[] = [
  { tipo: 'ELEGIR', prefijo: 'cha', eleccion: 'reutilizada' }, { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 131 },
]

describe('lo que se pide para la banda del SKU del taller (spec 0.9.21 pieza 2 §2.4)', () => {
  it('una batería nueva y un chasis reutilizado: las filas que se van a terminar', () => {
    const e = aplicar(conModelo(), { tipo: 'ELEGIR', prefijo: 'bat', eleccion: 'nueva' }, ...CHASIS_REUTILIZADO)
    expect(peticionPreviaTaller(e)).toEqual({
      imei: IMEI, filas: [{ idRep: null, idCom: 101, reutilizada: false }, { idRep: null, idCom: 131, reutilizada: true }],
    })
  })

  it('una pieza que no cambia el móvil (pantalla) o nada elegido: no se pregunta', () => {
    expect(peticionPreviaTaller(aplicar(conModelo(), { tipo: 'ELEGIR', prefijo: 'lcd', eleccion: 'nueva' }))).toBeNull()
    expect(peticionPreviaTaller(conModelo())).toBeNull()
  })

  it('una fila ya guardada con «✓ Guardar fila» no va: ya se aplicó', () => {
    const e = aplicar(conModelo(), { tipo: 'ELEGIR', prefijo: 'bat', eleccion: 'nueva' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' }, ...CHASIS_REUTILIZADO)
    expect(peticionPreviaTaller(e)?.filas).toEqual([{ idRep: null, idCom: 131, reutilizada: true }])
  })

  it('en glass no se pregunta', () => {
    const e = aplicar(reducir(estadoInicial(datosNuevo({ modo: 'glass', idAsignacion: 'AG20260916_2' })), { tipo: 'CAMBIAR_MODELO', modelo: '13' }),
      { tipo: 'ELEGIR', prefijo: 'g', eleccion: 'nueva' })
    expect(peticionPreviaTaller(e)).toBeNull()
  })

  it('en edición, la fila editada va con su reparación y las nuevas detrás', () => {
    const e = aplicar(estadoInicial(datosEditar()), { tipo: 'VOLVER', prefijo: 'bat' },
      { tipo: 'ELEGIR', prefijo: 'bat', eleccion: 'reutilizada' }, ...CHASIS_REUTILIZADO)
    expect(peticionPreviaTaller(e)).toEqual({
      imei: IMEI, filas: [{ idRep: 'R20260916_5', idCom: 101, reutilizada: true }, { idRep: null, idCom: 131, reutilizada: true }],
    })
  })

  it('en edición sin cambios no se pregunta', () => {
    expect(peticionPreviaTaller(estadoInicial(datosEditar()))).toBeNull()
  })
})

describe('el texto de la banda (spec 0.9.21 pieza 2 §2.4)', () => {
  it('lo que cambia, con lo de ahora', () => {
    expect(textoBandaTaller(previaTaller({ despues: { color: 'Rojo', grado: 'A100' } })))
      .toBe('Al guardar, este móvil pasará a: Rojo · A100 (ahora: Azul · B)')
    expect(textoBandaTaller(previaTaller({ despues: { color: 'Azul', grado: 'B100' } })))
      .toBe('Al guardar, este móvil pasará a: B100 (ahora: B)')
  })

  it('los avisos del color y del grado', () => {
    expect(textoBandaTaller(previaTaller({ despues: { color: 'Azul', grado: 'A' }, colorSinDeducir: true })))
      .toBe('Al guardar, este móvil pasará a: A (ahora: B); el color del chasis no se puede deducir: lo corregirá un supertécnico')
    expect(textoBandaTaller(previaTaller({ antes: { color: 'Azul', grado: 'ID' }, despues: { color: 'Rojo', grado: 'ID' }, gradoSinCalcular: true })))
      .toBe('Al guardar, este móvil pasará a: Rojo (ahora: Azul); el grado (ID) no se puede calcular: lo corregirá un supertécnico')
    expect(textoBandaTaller(previaTaller({ colorSinDeducir: true })))
      .toBe('Al guardar, el color del chasis no se puede deducir: lo corregirá un supertécnico')
  })

  it('nada que decir: sin banda', () => {
    expect(textoBandaTaller(null)).toBeNull()
    expect(textoBandaTaller(previaTaller())).toBeNull()
    expect(textoBandaTaller(previaTaller({ enNsys: false, despues: { color: 'Rojo', grado: 'A' } }))).toBeNull()
  })
})
```

Run: `npx vitest run src/modules/taller/formulario/skuTaller.test.ts`
Expected: FAIL (no existe `./skuTaller`).

- [ ] **Step 2: Las reglas**

Crea `src/modules/taller/formulario/skuTaller.ts`:

```ts
import type { FilaPreviaTaller, PeticionPreviaTaller, PreviaTaller } from '@/shared/api/client'
import { PREFIJO_CHASIS, PREFIJO_TAPA } from '../lib/colores'
import { planGuardarCambios, planTerminar, zonaGuardarVisible, type EstadoFormulario } from './estado'

/** La banda del SKU del taller encima de la zona de guardar (0.9.21, spec «SKU del taller», pieza 2, §2.4): qué se pregunta
 *  y qué se dice. La cuenta la hace el servidor, la misma que al guardar. Puro. */

/** Las piezas que cambian el color o el grado del móvil: chasis, tapa trasera y batería. */
export const PREFIJOS_SKU_TALLER: readonly string[] = [PREFIJO_CHASIS, PREFIJO_TAPA, 'bat']

const cuenta = (prefijo: string | null) => prefijo !== null && PREFIJOS_SKU_TALLER.includes(prefijo)

/** Lo que se pide a POST /api/reparaciones/sku-taller/previsualizar: las filas que va a mandar «Terminar asignación» o
 *  «Guardar cambios» y cambian el móvil (las ya guardadas con «✓ Guardar fila» ya se aplicaron). En edición, la fila editada
 *  va con el idRep de su reparación (el servidor deshace lo suyo y aplica la nueva, como al guardar) y las nuevas detrás.
 *  null: nada que preguntar (zona de guardar oculta, glass o ninguna pieza que cuente). */
export function peticionPreviaTaller(e: EstadoFormulario): PeticionPreviaTaller | null {
  if (e.categoria === 'G' || !zonaGuardarVisible(e)) return null
  const filas: FilaPreviaTaller[] = []
  if (e.modo === 'editar') {
    const plan = planGuardarCambios(e)
    const editada = e.filas.find((f) => f.rol === 'editada')
    if (plan.editarFila !== null && e.edicion !== null && editada !== undefined && cuenta(editada.prefijo)) {
      filas.push({ idRep: e.edicion.idRep, idCom: plan.editarFila.idComNuevo, reutilizada: plan.editarFila.esReutilizadoNuevo })
    }
    for (const f of plan.completaFilas?.filas ?? []) if (cuenta(f.prefijo)) filas.push({ idRep: null, idCom: f.idCom, reutilizada: f.reutilizado })
  } else {
    // El técnico (0) no cuenta para las filas: solo rellena el cuerpo de «completa».
    for (const f of planTerminar(e, 0).completa?.filas ?? []) if (cuenta(f.prefijo)) filas.push({ idRep: null, idCom: f.idCom, reutilizada: f.reutilizado })
  }
  return filas.length === 0 ? null : { imei: e.imei, filas }
}

/** «Al guardar, este móvil pasará a: Rojo · A100 (ahora: Azul · B)», solo con los datos que cambian, y los avisos del color
 *  y del grado, unidos con «; ». null si no hay nada que decir (IMEI fuera de NSYS, nada cambia o la previa falló). */
export function textoBandaTaller(p: PreviaTaller | null): string | null {
  if (p === null || !p.enNsys) return null
  const despues: string[] = []
  const ahora: string[] = []
  if (p.despues.color !== p.antes.color) {
    despues.push(p.despues.color ?? '—')
    ahora.push(p.antes.color ?? '—')
  }
  if (p.despues.grado !== p.antes.grado) {
    despues.push(p.despues.grado ?? '—')
    ahora.push(p.antes.grado ?? '—')
  }
  const partes: string[] = []
  if (despues.length > 0) partes.push(`este móvil pasará a: ${despues.join(' · ')} (ahora: ${ahora.join(' · ')})`)
  if (p.colorSinDeducir) partes.push('el color del chasis no se puede deducir: lo corregirá un supertécnico')
  if (p.gradoSinCalcular) partes.push(`el grado (${p.antes.grado ?? '—'}) no se puede calcular: lo corregirá un supertécnico`)
  return partes.length === 0 ? null : `Al guardar, ${partes.join('; ')}`
}
```

Run: `npx vitest run src/modules/taller/formulario/skuTaller.test.ts`
Expected: PASS.

- [ ] **Step 3: Tests de la banda en el formulario (fallan)**

En `src/modules/taller/formulario/FormularioReparacion.test.tsx`, añade `previaTaller` al import de `'../test/fabrica'` y, al
final del fichero:

```tsx
describe('FormularioReparacion — banda del SKU del taller (spec 0.9.21 pieza 2 §2.4)', () => {
  /** Responde con `respuesta` y apunta los cuerpos que llegan. */
  function previa(respuesta: Parameters<typeof previaTaller>[0]): unknown[] {
    const cuerpos: unknown[] = []
    server.use(http.post('*/api/reparaciones/sku-taller/previsualizar', async ({ request }) => {
      cuerpos.push(await request.json())
      return HttpResponse.json(previaTaller(respuesta))
    }))
    return cuerpos
  }

  it('con una pieza que cambia el móvil, la banda sale encima de «Terminar asignación» con lo que dice el servidor', async () => {
    const { llamadas } = abrir()
    const cuerpos = previa({ despues: { color: 'Azul', grado: 'B100' } })
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await sumar('bat', 'Batería')
    const zona = screen.getByTestId('zona-guardar')
    const banda = await within(zona).findByTestId('banda-sku-taller', {}, { timeout: 3000 })
    expect(banda).toHaveTextContent('Al guardar, este móvil pasará a: B100 (ahora: B)')
    expect(banda).toHaveAttribute('role', 'status')
    expect(cuerpos[cuerpos.length - 1]).toEqual({ imei: '355400000000111', filas: [{ idRep: null, idCom: 101, reutilizada: false }] })
    expect(botonZona().textContent).toBe('Terminar asignación')
    expect(escrituras(llamadas)).toEqual([])
  })

  it('si nada cambia no hay banda', async () => {
    abrir()
    const cuerpos = previa({})
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await sumar('bat', 'Batería')
    await waitFor(() => expect(cuerpos).toHaveLength(1), { timeout: 3000 })
    expect(screen.queryByTestId('banda-sku-taller')).not.toBeInTheDocument()
  })

  it('una pieza que no cambia el móvil (pantalla) no pregunta', async () => {
    abrir()
    const cuerpos = previa({ despues: { color: 'Rojo', grado: 'A' } })
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await sumar('lcd', 'Pantalla')
    await new Promise((r) => setTimeout(r, 800))
    expect(cuerpos).toEqual([])
    expect(screen.queryByTestId('banda-sku-taller')).not.toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/taller/formulario/FormularioReparacion.test.tsx`
Expected: FAIL en el primer test (no hay banda).

- [ ] **Step 4: La zona de guardar y el formulario**

Sustituye `src/modules/taller/formulario/ZonaGuardar.tsx` entero por:

```tsx
import { BotonPrimario } from '@/shared/ui/Botones'
import { textoBotonGuardar, zonaGuardarVisible, type EstadoFormulario } from './estado'

type Props = { estado: EstadoFormulario; aviso?: string | null; onPulsar: () => void }

/** Zona pegada al fondo con el único botón navy. Se muestra u oculta ENTERA (`zonaGuardarVisible`); el botón nunca se deshabilita:
 *  mientras un guardado está en curso, quien recibe `onPulsar` ignora el clic. whitespace-pre conserva el doble espacio de
 *  "✓  Confirmar terminar". Desde la 0.9.21 (pieza 2 del SKU del taller), encima del botón, una banda fina con lo que el
 *  guardado cambiará en el móvil (`aviso`): no bloquea ni añade clics. */
export function ZonaGuardar({ estado, aviso = null, onPulsar }: Props) {
  if (!zonaGuardarVisible(estado)) return null
  return (
    <div data-testid="zona-guardar" className="flex flex-col items-end gap-2 border-t border-form-zona-brd bg-form-zona-bg px-4 py-3">
      {aviso && (
        <p role="status" data-testid="banda-sku-taller" className="w-full rounded border border-taller bg-taller-bg px-3 py-1.5 text-[12.5px] text-taller-text">
          {aviso}
        </p>
      )}
      <BotonPrimario onClick={onPulsar} className="whitespace-pre">{textoBotonGuardar(estado)}</BotonPrimario>
    </div>
  )
}
```

En `src/modules/taller/formulario/FormularioReparacion.tsx`:

1. Imports: tras `import { OtrasAcciones } from './OtrasAcciones'`, añade `import { peticionPreviaTaller, textoBandaTaller } from './skuTaller'`;
   tras `import { useGuardado } from './useGuardado'`, añade `import { usePreviaTaller } from './usePreviaTaller'`.
2. En `FormularioCargado`, tras la línea `const guardado = useGuardado({ estado, dispatch, onGuardado: alGuardar, antesDeCerrar: descartar })`, añade:

```tsx
  // 0.9.21 (pieza 2 del SKU del taller): lo que el guardado cambiará en el móvil, en una banda encima del botón.
  const avisoTaller = textoBandaTaller(usePreviaTaller(peticionPreviaTaller(estado)))
```

3. Cambia `<ZonaGuardar estado={estado} onPulsar={guardado.pulsarGuardar} />` por
   `<ZonaGuardar estado={estado} aviso={avisoTaller} onPulsar={guardado.pulsarGuardar} />`.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npm run lint && npx vitest run src/modules/taller/formulario`
Expected: `tsc` y lint limpios y todo en verde (los de siempre de la zona de guardar siguen: la previa por defecto está fuera de
NSYS y no hay banda). Si algún «orden de llamadas…» pasa de los 5 s con la suite entera, repetir ese fichero solo y apuntarlo.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/formulario/skuTaller.ts src/modules/taller/formulario/skuTaller.test.ts src/modules/taller/formulario/ZonaGuardar.tsx \
  src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat: banda del SKU del taller encima de terminar asignacion y guardar cambios (0.9.21, pieza 2)"
```

---

### Task 11: Web — textos del rastro y de «Por pasar a NSYS» con la reparación

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/rastro.ts`, `src/modules/telefonos/stock/porPasar.ts`
- Test: `src/modules/taller/imeis/rastro.test.ts`, `src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: los tipos `TALLER_*` de la Task 8; `eventoRastro` y `porQue` (fábricas de la pieza 1).
- Produces: `verEvento` compone los textos de la pieza 2 (Global Constraints → «Textos exactos»); `comoTaller` distingue a mano
  y reparación; `textoPorQue` no pone comillas a la pieza de una reparación.

- [ ] **Step 1: Tests (fallan)**

En `src/modules/taller/imeis/rastro.test.ts`, añade al final del `describe`:

```ts
  it('lo que hacen las reparaciones (spec 0.9.21 pieza 2 §2.5)', () => {
    const rep = (p: Parameters<typeof eventoRastro>[0]) =>
      eventoRastro({ fuente: 'TALLER', lote: null, aNsys: null, tecnico: 'Técnico 2', origen: 'REPARACION', idRep: 'R20261021_3', ...p })
    expect(verEvento(rep({ tipo: 'TALLER_CAMBIO', dato: 'COLOR', de: 'Azul', a: 'Rojo', motivo: 'chasis chai13red nuevo' })))
      .toMatchObject({ titulo: 'Taller: color Azul → Rojo (reparación R20261021_3 · Técnico 2 · chasis chai13red nuevo)', color: 'bg-taller' })
    expect(verEvento(rep({ tipo: 'TALLER_VUELTA', dato: 'COLOR', de: 'Rojo', a: 'Azul', motivo: 'chasis chai13blue nuevo' })).titulo)
      .toBe('Vuelve a lo de NSYS: color Rojo → Azul (reparación R20261021_3 · Técnico 2 · chasis chai13blue nuevo)')
    expect(verEvento(rep({ tipo: 'TALLER_SIN_COLOR', dato: 'COLOR', de: 'chai13xxx', motivo: 'chasis chai13xxx nuevo' })))
      .toMatchObject({ titulo: 'Taller: el color del chasis chai13xxx no se puede deducir: lo corregirá un supertécnico (reparación R20261021_3 · Técnico 2)', color: 'bg-ambar' })
    expect(verEvento(rep({ tipo: 'TALLER_SIN_COLOR', dato: 'COLOR', de: 'tapai13xxx' })).titulo)
      .toContain('el color de la tapa tapai13xxx no se puede deducir')
    expect(verEvento(rep({ tipo: 'TALLER_SIN_GRADO', dato: 'GRADO', de: 'ID', motivo: 'batería bati13 nueva' })).titulo)
      .toBe('Taller: el grado (ID) no se puede calcular: lo corregirá un supertécnico (reparación R20261021_3 · Técnico 2 · batería bati13 nueva)')
    expect(verEvento(rep({ tipo: 'TALLER_DESHECHO_BORRADA', dato: 'COLOR', de: 'Rojo', a: 'Azul' })))
      .toMatchObject({ titulo: 'Se deshace: color Rojo → Azul (se borró la reparación R20261021_3 · Técnico 2)', color: 'bg-taller' })
    expect(verEvento(rep({ tipo: 'TALLER_DESHECHO_EDITADA', dato: 'GRADO', de: 'A', a: 'B' })).titulo)
      .toBe('Se deshace: grado A → B (se editó la reparación R20261021_3 · Técnico 2)')
    expect(verEvento(rep({ tipo: 'TALLER_SIN_DESHACER_BORRADA', dato: 'COLOR', de: 'Rojo' })))
      .toMatchObject({ titulo: 'Se borró la reparación R20261021_3 que cambió el color; corrígelo a mano si hace falta (Técnico 2)', color: 'bg-ambar' })
    expect(verEvento(rep({ tipo: 'TALLER_SIN_DESHACER_EDITADA', dato: 'GRADO', de: 'A' })).titulo)
      .toBe('Se editó la reparación R20261021_3 que cambió el grado; corrígelo a mano si hace falta (Técnico 2)')
  })
```

En `src/modules/telefonos/stock/porPasar.test.ts`, añade al final del `describe`:

```ts
  it('por qué desde una reparación: la reparación, quién y la pieza, sin comillas (spec 0.9.21 pieza 2 §2.5)', () => {
    expect(textoPorQue(porQue({ dato: 'COLOR', origen: 'REPARACION', idRep: 'R20261021_3', usuario: 'Técnico 2', motivo: 'chasis chai13red nuevo' })))
      .toBe('Color: reparación R20261021_3 · Técnico 2 · chasis chai13red nuevo')
  })
```

Run: `npx vitest run src/modules/taller/imeis/rastro.test.ts src/modules/telefonos/stock/porPasar.test.ts`
Expected: FAIL (la pieza va con comillas y los tipos nuevos salen por `default`).

- [ ] **Step 2: Implementación**

En `src/modules/taller/imeis/rastro.ts`, sustituye el bloque de la pieza 1

```ts
/** 0.9.21 (spec «SKU del taller» §2.4): los cambios del taller por encima de NSYS. */
const DATO: Record<string, string> = { COLOR: 'color', GRADO: 'grado' }
const ORIGEN: Record<string, string> = { MANO: 'a mano', REPARACION: 'por una reparación' }
const dato = (e: EventoRastro) => DATO[e.dato ?? ''] ?? 'dato'

/** «(a mano · Imad · «motivo»)»: cómo, quién y por qué; sin motivo, el paréntesis acaba en el nombre. */
function comoTaller(e: EventoRastro): string {
  return `(${[ORIGEN[e.origen ?? ''] ?? 'a mano', e.tecnico ?? '—', e.motivo ? `«${e.motivo}»` : null].filter(Boolean).join(' · ')})`
}
```

por

```ts
/** 0.9.21 (spec «SKU del taller»): los cambios del taller por encima de NSYS (pieza 1) y lo que hacen las reparaciones
 *  (pieza 2). */
const DATO: Record<string, string> = { COLOR: 'color', GRADO: 'grado' }
const dato = (e: EventoRastro) => DATO[e.dato ?? ''] ?? 'dato'

/** «(a mano · Técnico 1 · «motivo»)» o, desde una reparación, «(reparación R… · Técnico 2 · chasis chai13red nuevo)»: cómo,
 *  quién y por qué (la pieza, sin comillas); sin motivo, el paréntesis acaba en el nombre. */
function comoTaller(e: EventoRastro): string {
  const partes = e.origen === 'REPARACION'
    ? [`reparación ${e.idRep ?? '—'}`, e.tecnico ?? '—', e.motivo]
    : ['a mano', e.tecnico ?? '—', e.motivo ? `«${e.motivo}»` : null]
  return `(${partes.filter(Boolean).join(' · ')})`
}

/** Los avisos de la pieza 2 sin la pieza: «(reparación R… · Técnico 2)». */
const deReparacion = (e: EventoRastro) => `(reparación ${e.idRep ?? '—'} · ${e.tecnico ?? '—'})`
/** «del chasis chai13xxx» o «de la tapa tapai13xxx», por el prefijo del SKU. */
const delaPieza = (sku: string | null) => ((sku ?? '').toLowerCase().startsWith('tapa') ? `de la tapa ${sku}` : `del chasis ${sku ?? '—'}`)
/** «borró» o «editó», por la causa del tipo (…_BORRADA o …_EDITADA). */
const causa = (e: EventoRastro) => (e.tipo.endsWith('_BORRADA') ? 'borró' : 'editó')
```

y, en `verEvento`, justo antes de `    default:` (tras los casos `TALLER_*` de la pieza 1), añade:

```ts
    case 'TALLER_SIN_COLOR':
      return { fecha, titulo: `Taller: el color ${delaPieza(e.de)} no se puede deducir: lo corregirá un supertécnico ${deReparacion(e)}`, detalle: null, color: 'bg-ambar' }
    case 'TALLER_SIN_GRADO':
      return { fecha, titulo: `Taller: el grado (${e.de ?? '—'}) no se puede calcular: lo corregirá un supertécnico ${comoTaller(e)}`, detalle: null, color: 'bg-ambar' }
    case 'TALLER_DESHECHO_BORRADA':
    case 'TALLER_DESHECHO_EDITADA':
      return { fecha, titulo: `Se deshace: ${dato(e)} ${e.de ?? '—'} → ${e.a ?? '—'} (se ${causa(e)} la reparación ${e.idRep ?? '—'} · ${e.tecnico ?? '—'})`, detalle: null, color: 'bg-taller' }
    case 'TALLER_SIN_DESHACER_BORRADA':
    case 'TALLER_SIN_DESHACER_EDITADA':
      return { fecha, titulo: `Se ${causa(e)} la reparación ${e.idRep ?? '—'} que cambió el ${dato(e)}; corrígelo a mano si hace falta (${e.tecnico ?? '—'})`, detalle: null, color: 'bg-ambar' }
```

En `src/modules/telefonos/stock/porPasar.ts`, sustituye la función `textoPorQue` (con su Javadoc) por:

```ts
/** «Color: a mano · Técnico 1 · «motivo»»; el de una reparación (pieza 2), «Color: reparación R… · Técnico 2 · chasis chai13red
 *  nuevo» (la pieza, sin comillas). */
export function textoPorQue(p: PorQueTaller): string {
  const reparacion = p.origen === 'REPARACION'
  const como = reparacion ? `reparación ${p.idRep ?? '—'}` : 'a mano'
  const motivo = !p.motivo ? null : reparacion ? p.motivo : `«${p.motivo}»`
  return `${DATO[p.dato] ?? p.dato}: ${[como, p.usuario ?? '—', motivo].filter(Boolean).join(' · ')}`
}
```

- [ ] **Step 3: Ejecutar los tests y ver que pasan**

Run: `npx tsc -b && npm run lint && npx vitest run src/modules/taller/imeis src/modules/telefonos`
Expected: `tsc` y lint limpios y todo en verde (los textos a mano de la pieza 1 no cambian).

- [ ] **Step 4: Commit**

```bash
git add src/modules/taller/imeis/rastro.ts src/modules/taller/imeis/rastro.test.ts \
  src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts
git commit -m "feat: textos del rastro y de por pasar a NSYS con lo que hacen las reparaciones (0.9.21, pieza 2)"
```

---

### Task 12: Web — E2E: una reparación cambia el móvil y borrarla lo deshace (preproducción)

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/sku-taller-reparacion.spec.ts`

**Interfaces:**
- Consumes: la pantalla y la API de las Tasks 1-11; el móvil sintético de la pieza 1 (`350000000009921` o `E2E_IMEI_TALLER`,
  que mete el guion de preprod: un iPhone 13 128GB, grado `B`); `credenciales('E2E_USER', 'E2E_PASS')` (supertécnico con
  técnico); `POST /api/asignaciones/lote` como en `formulario.spec.ts`.
- Produces: 2 tests en serie con un solo inicio de sesión. Se pasa **después** de `sku-taller.spec.ts` (con ≥ 60 s entre
  ficheros); los dos dejan el móvil sin nada del taller.

- [ ] **Step 1: El fichero**

Crea `tests/e2e/sku-taller-reparacion.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// sku-taller-reparacion.spec.ts (0.9.21, spec «SKU del taller», pieza 2): una reparación con un chasis de otro color
// (reutilizado: no gasta stock) cambia sola el color y el grado del móvil de prueba, con su banda encima de «Terminar
// asignación»; el stock, la ficha, el rastro y «Por pasar a NSYS» lo enseñan; borrar la reparación lo deshace y lo saca de la
// lista. Solo contra PREPRODUCCIÓN (se niega a seguir sin el letrero) y con el móvil SINTÉTICO del guion de preprod (el de
// sku-taller.spec.ts: por defecto 350000000009921, grado B; otro con E2E_IMEI_TALLER): nunca un móvil real. La asignación de
// prueba es del propio supertécnico (E2E_USER) y, al acabar, se borra todo lo del taller de ese IMEI.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const IMEI = process.env.E2E_IMEI_TALLER ?? '350000000009921'
const MOTIVO = 'E2E 0.9.21: SKU del taller desde una reparación'

type Respuesta = { estado: number; cuerpo: unknown }
type Valores = { color: string | null; grado: string | null }
type Previa = { enNsys: boolean; antes: Valores; despues: Valores; colorSinDeducir: boolean; gradoSinCalcular: boolean }
type Chasis = { idCom: number; tipo: string; antes: Valores; despues: Valores }

const escapar = (t: string) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Una petición a la API con el token de la sesión de la página (patrón de sku-taller.spec.ts). */
async function pedir(page: Page, metodo: string, ruta: string, cuerpo?: unknown, cabeceras: Record<string, string> = {}): Promise<Respuesta> {
  return page.evaluate(async ({ metodo, ruta, cuerpo, cabeceras }) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(ruta, {
      method: metodo,
      headers: { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json', ...cabeceras },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    const texto = await res.text()
    return { estado: res.status, cuerpo: texto ? (JSON.parse(texto) as unknown) : null }
  }, { metodo, ruta, cuerpo, cabeceras })
}

/** Lo del taller de ese IMEI fuera: las reparaciones (las R… antes que las asignaciones; borrar una R… deshace lo suyo), el
 *  teléfono del lote y, por si quedó algo a mano, «Volver a lo de NSYS». */
async function limpiar(page: Page) {
  const r = await pedir(page, 'GET', `/api/reparaciones/imei/${IMEI}`)
  expect(r.estado, `GET /api/reparaciones/imei/${IMEI}`).toBe(200)
  const ids = (r.cuerpo as { idRep: string }[]).map((x) => x.idRep)
  for (const idRep of [...ids.filter((id) => id.startsWith('R')), ...ids.filter((id) => !id.startsWith('R'))]) {
    const b = await pedir(page, 'DELETE', `/api/reparaciones/${idRep}`, { motivo: 'E2E: limpieza' })
    expect.soft(b.estado, `limpieza DELETE /api/reparaciones/${idRep}`).toBe(204)
  }
  const t = await pedir(page, 'DELETE', `/api/telefonos/${IMEI}`)
  expect.soft([204, 404], `limpieza DELETE /api/telefonos/${IMEI}`).toContain(t.estado)
  const v = await pedir(page, 'DELETE', `/api/stock-telefonos/${IMEI}/taller`, { motivo: 'E2E: limpieza' })
  expect.soft([204, 400], 'limpieza del móvil de prueba').toContain(v.estado)
}

/** Un chasis del iPhone 13 (ni Pro ni mini) que, reutilizado, cambia el color del móvil de prueba a otro que se deduce. */
async function elegirChasis(page: Page): Promise<Chasis> {
  const r = await pedir(page, 'GET', '/api/componentes/agrupados')
  expect(r.estado, 'GET /api/componentes/agrupados').toBe(200)
  const chasis = ((r.cuerpo as Record<string, { idCom: number; tipo: string; activo: boolean }[]>).cha ?? [])
    .filter((c) => c.activo && /^chai13(?!pro|mini)[a-z]+$/.test(c.tipo))
  for (const c of chasis) {
    const p = await pedir(page, 'POST', '/api/reparaciones/sku-taller/previsualizar', { imei: IMEI, filas: [{ idRep: null, idCom: c.idCom, reutilizada: true }] })
    expect(p.estado, 'POST /api/reparaciones/sku-taller/previsualizar').toBe(200)
    const previa = p.cuerpo as Previa
    if (previa.enNsys && !previa.colorSinDeducir && previa.despues.color !== previa.antes.color) {
      return { idCom: c.idCom, tipo: c.tipo, antes: previa.antes, despues: previa.despues }
    }
  }
  throw new Error(`Ningún chasis del iPhone 13 cambia el color del móvil de prueba ${IMEI}: revisa el catálogo de preprod`)
}

test.describe('0.9.21: SKU del taller desde una reparación', () => {
  test.describe.configure({ mode: 'serial' })
  let pagina: Page
  let chasis: Chasis
  let idAsig: string
  let idRep: string | null = null

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
    expect((await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).estado,
      `falta el móvil sintético ${IMEI} en el espejo de preprod (lo mete el guion)`).toBe(200)
    await limpiar(pagina)
    const ficha = (await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).cuerpo as { grado: string; porPasar: boolean }
    expect(ficha, 'el móvil de prueba empieza en B y sin nada del taller').toMatchObject({ grado: 'B', porPasar: false })
    chasis = await elegirChasis(pagina)
    const idTec = await pagina.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { idTec?: number }).idTec)
    expect(typeof idTec, 'idTec del supertécnico en la sesión').toBe('number')
    const lote = await pedir(pagina, 'POST', '/api/asignaciones/lote', {
      telefonos: [{ imei: IMEI, modelo: '13', idCli: null, clienteExplicito: false }],
      asignaciones: [{ imei: IMEI, categoria: 'R', idTec, comentario: MOTIVO, esChasis: false }],
    }, { 'Idempotency-Key': `e2e-sku-taller-${Date.now()}` })
    expect(lote.estado, 'POST /api/asignaciones/lote').toBe(200)
    const creadas = (lote.cuerpo as { creadas: { idRep: string }[] }).creadas
    expect(creadas, 'una asignación de prueba').toHaveLength(1)
    idAsig = creadas[0].idRep
  })

  test.afterAll(async () => {
    if (pagina) await limpiar(pagina)
    await pagina?.close()
  })

  test('una reparación con un chasis de otro color cambia el color y el grado, con su banda, y sale en «Por pasar a NSYS»', async () => {
    const { tipo, antes, despues } = chasis
    await pagina.goto(`/reparaciones/pendientes/reparar/${idAsig}`)
    const formulario = pagina.getByRole('dialog', { name: /^Nueva reparación — IMEI / })
    await expect(formulario).toBeVisible({ timeout: 30_000 })
    const modelo = formulario.getByRole('combobox', { name: 'Filtrar por modelo' })
    if (await modelo.isEnabled()) {
      await modelo.click()
      await pagina.getByRole('listbox').getByRole('button', { name: 'iPhone 13', exact: true }).click()
    }
    const fila = formulario.getByTestId('fila-cha')
    await fila.getByRole('button', { name: 'Reutilizada Chasis' }).first().click()
    await fila.getByRole('combobox', { name: 'SKU de Chasis' }).click()
    await pagina.getByRole('listbox', { name: 'SKU de Chasis' }).getByTitle(tipo).click()
    await expect(formulario.getByTestId('banda-sku-taller'))
      .toHaveText(`Al guardar, este móvil pasará a: ${despues.color} · ${despues.grado} (ahora: ${antes.color} · ${antes.grado})`)
    const boton = formulario.getByTestId('zona-guardar').getByRole('button')
    await boton.click()
    await expect(boton).toHaveText('✓  Confirmar terminar')
    await boton.click()
    await expect(formulario).toHaveCount(0, { timeout: 30_000 })

    const reparaciones = await pedir(pagina, 'GET', `/api/reparaciones/imei/${IMEI}`)
    idRep = (reparaciones.cuerpo as { idRep: string }[]).map((r) => r.idRep).find((id) => id.startsWith('R')) ?? null
    expect(idRep, 'la reparación creada').not.toBeNull()

    // El stock: el buscador enseña el color nuevo con la marca y lo de NSYS.
    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }).fill(IMEI)
    await expect(pagina.getByRole('button', {
      name: new RegExp(`^${IMEI} · .*${escapar(despues.color ?? '')} taller NSYS: ${escapar(antes.color ?? '')} · `),
    })).toBeVisible({ timeout: 30_000 })
    await pagina.keyboard.press('Escape')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS \(\d+\)$/ }).click()
    const enLista = pagina.getByRole('table', { name: 'Móviles por pasar a NSYS' }).getByRole('row', { name: new RegExp(IMEI) })
    await expect(enLista).toContainText(`Color: reparación ${idRep}`)
    await expect(enLista).toContainText(`Grado: reparación ${idRep}`)

    // La ficha y el rastro.
    await pagina.goto(`/reparaciones/imeis/${IMEI}`)
    const ficha = pagina.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toContainText(`NSYS: ${antes.color}`, { timeout: 30_000 })
    await expect(ficha).toContainText('Por pasar a NSYS:')
    await ficha.getByRole('button', { name: /^Ver rastro/ }).click()
    const rastro = pagina.getByRole('dialog', { name: 'Rastro' })
    await expect(rastro).toContainText(`Taller: color ${antes.color} → ${despues.color} (reparación ${idRep} · `)
    await expect(rastro).toContainText(`Taller: grado ${antes.grado} → ${despues.grado} (reparación ${idRep} · `)
    await pagina.keyboard.press('Escape')
  })

  test('borrar la reparación deshace el color y el grado y la saca de la lista', async () => {
    expect(idRep, 'el test anterior dejó la reparación').not.toBeNull()
    const { antes, despues } = chasis
    const b = await pedir(pagina, 'DELETE', `/api/reparaciones/${idRep}`, { motivo: MOTIVO })
    expect(b.estado, `DELETE /api/reparaciones/${idRep}`).toBe(204)
    await pagina.goto(`/reparaciones/imeis/${IMEI}`)
    const ficha = pagina.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toBeVisible({ timeout: 30_000 })
    await expect(ficha).not.toContainText('Por pasar a NSYS:')
    await ficha.getByRole('button', { name: /^Ver rastro/ }).click()
    const rastro = pagina.getByRole('dialog', { name: 'Rastro' })
    await expect(rastro).toContainText(`Se deshace: color ${despues.color} → ${antes.color} (se borró la reparación ${idRep}`)
    await expect(rastro).toContainText(`Se deshace: grado ${despues.grado} → ${antes.grado} (se borró la reparación ${idRep}`)
    await pagina.keyboard.press('Escape')
    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS \(\d+\)$/ }).click()
    await expect(pagina.getByRole('table', { name: 'Móviles por pasar a NSYS' }).getByRole('row', { name: new RegExp(IMEI) })).toHaveCount(0)
  })
})
```

- [ ] **Step 2: Compila y se lista**

Run: `npx playwright test tests/e2e/sku-taller-reparacion.spec.ts --list`
Expected: 2 tests, sin errores de compilación. No se ejecutan aquí: solo contra preprod (Task 14 de la pieza 1).

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/sku-taller-reparacion.spec.ts
git commit -m "test: e2e del SKU del taller desde una reparacion con el movil sintetico de preprod (0.9.21, pieza 2)"
```

---

### Task 13: Cierre — documentación, suites y entrega (las dos piezas)

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (entrada `[0.9.21]`), `docs/paridad/formulario.md`, `docs/paridad/imeis.md`
- Modify: `docs/novedades/NOVEDADES-v0.9.21.md` (raíz; lo sube el controlador con los gitlinks)
- Modify: `Apuntes/plan-futuro.md` (fuera de los repos)

**Interfaces:**
- Consumes: todo lo de las Tasks 1-12 y de la pieza 1.
- Produces: las ramas `dev_v0921_sku_taller` (servidor y web) con las dos piezas, listas para preprod (Task 14 de la pieza 1).

- [ ] **Step 1: CHANGELOG de la web**

Dentro de la entrada `## [0.9.21]` (la abrió la «Corrección en NSYS» y la amplió la pieza 1), tras las viñetas del SKU del
taller y **antes** de la línea «Requiere el servidor de la 0.9.21…», añade:

```markdown
- **Las reparaciones cambian solas el color y el grado del móvil** (pieza 2 del SKU del taller): al guardar una reparación con **chasis** o **tapa trasera** (nueva o reutilizada), el móvil pasa al color de la pieza (deducido del maestro) y a grado **A**, conservando «Ms» y «100»; con **batería nueva**, el grado lleva «100»; con batería reutilizada, se le quita. Solo en los móviles que están en NSYS; glass y marco no cambian nada. Si el color no se puede deducir, o el grado no es de la forma de NSYS (ID, F, n/a…), ese dato no cambia y el rastro lo avisa para corregirlo a mano.
- **Formulario de reparación:** encima de «Terminar asignación» / «Guardar cambios», una banda dice cómo quedará el móvil: «Al guardar, este móvil pasará a: Rojo · A100 (ahora: Azul · B)». No añade clics ni bloquea.
- **Editar o borrar una reparación** deshace lo suyo si nadie lo ha cambiado después ni ha pasado ya a NSYS; si no, el rastro avisa para corregirlo a mano.
- **Rastro y «Por pasar a NSYS»:** «Taller: color Azul → Rojo (reparación R… · quién · chasis chai13red nuevo)», «Se deshace: …» y los avisos; en «Por qué», «Color: reparación R… · quién · pieza».
```

Y cambia la línea «Requiere el servidor de la 0.9.21 y la migración `migracion-sku-taller.sql`.» por «Requiere el servidor de
la 0.9.21 y la migración `migracion-sku-taller.sql` (tablas `Taller_cambio` y `Taller_aviso`).» (el resto de la línea, igual).

- [ ] **Step 2: Paridad**

Al final de `docs/paridad/formulario.md`, con una línea en blanco antes:

```markdown
## 0.9.21 — Banda del SKU del taller encima de la zona de guardar (sin equivalente en el JavaFX)

Añadido, decidido en la spec `2026-10-21-v0921-sku-del-taller-automatico-design.md` (raíz). En el flujo nuevo y en la edición
de una reparación (no en glass), cuando alguna fila que se va a guardar es un chasis, una tapa trasera o una batería, la zona de
guardar enseña encima del botón lo que el servidor calcula que pasará al móvil:
- «Al guardar, este móvil pasará a: <color> · <grado> (ahora: <color> · <grado>)», solo con los datos que cambian;
- «… el color del chasis no se puede deducir: lo corregirá un supertécnico» y «… el grado (<grado>) no se puede calcular: lo
  corregirá un supertécnico».

La pide 500 ms después del último cambio (`POST /api/reparaciones/sku-taller/previsualizar`, la misma cuenta que al guardar),
no la vuelve a pedir si NSYS no conoce el IMEI y, si falla, no sale. No bloquea ni añade clics: el cambio lo hace el servidor al
guardar, en la misma transacción. Las filas ya guardadas con «✓ Guardar fila» no cuentan (ya se aplicaron).

Pruebas: `skuTaller.test.ts`, `usePreviaTaller.test.tsx`, `FormularioReparacion.test.tsx` y `sku-taller-reparacion.spec.ts`.
```

Al final de la sección «0.9.21 — SKU del taller en la ficha y en el rastro» de `docs/paridad/imeis.md` (la de la pieza 1), antes
de su párrafo «Pruebas: …», añade:

```markdown
Desde la pieza 2 (`2026-10-21-v0921-sku-del-taller-automatico-design.md`), el rastro también cuenta lo que hacen las
reparaciones:
- «Taller: color Azul → Rojo (reparación R… · <quién> · chasis chai13red nuevo)» y «Vuelve a lo de NSYS: … (reparación …)»;
- «Se deshace: color Rojo → Azul (se borró la reparación R… · <quién>)» (o «se editó»);
- avisos: «Taller: el color del chasis <SKU> no se puede deducir: …», «Taller: el grado (ID) no se puede calcular: …» y «Se borró
  la reparación R… que cambió el color; corrígelo a mano si hace falta (<quién>)».
```

- [ ] **Step 3: Novedades (raíz, sin commit)**

En `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.21.md`:

1. Sustituye la sección

```markdown
## 🧰 Para todos

- El técnico ve el programa como hasta ahora.
```

por

```markdown
## 🔧 Formulario de reparación

- Al guardar una reparación con **chasis**, **tapa trasera** o **batería** de un móvil de NSYS, el programa **cambia solo el
  color y el grado** del móvil:
  - chasis o tapa (nuevos o reutilizados) → el color de la pieza y grado **A** (se conservan «Ms» y «100»);
  - batería nueva → el grado lleva «100»; batería reutilizada → se le quita.
- Encima de «Terminar asignación» sale una banda con cómo quedará: «Al guardar, este móvil pasará a: Rojo · A100 (ahora: Azul ·
  B)». Si el color o el grado no se pueden calcular, la banda lo dice y lo corregirá un supertécnico.
- Editar o borrar una reparación deshace lo que había cambiado (si nadie lo ha tocado después).
```

2. En «👀 Supertécnicos y administrador» → «🔎 IMEIs», añade al final: «- El rastro dice qué reparación cambió el color o el
   grado, cuándo se deshizo y lo que hay que corregir a mano.»; y en «📱 Stock de teléfonos», tras la viñeta de la pestaña,
   «- En «Por pasar a NSYS» también salen los cambios de las reparaciones («Color: reparación R… · quién · pieza»).»
3. En el aviso final, borra la frase «Lo automático desde las reparaciones (chasis de otro color, batería nueva…) llega en la
   siguiente pieza.» (queda «⚠️ El ERP no cambia nada en NSYS: la lista es lo que hay que pasar a mano.»).
4. En el párrafo de entrada, tras «…cambia en un móvil (color, grado y batería nueva)», añade «—también solo, al guardar una
   reparación—».

- [ ] **Step 4: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0921-sku`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total en el registro.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0921-sku`
Expected: lint sin errores nuevos, `tsc -b` limpio y Vitest en verde (los lentos conocidos, de uno en uno si hace falta). Anota
el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista incluye los 2 de `sku-taller.spec.ts`, los 2 de `sku-taller-reparacion.spec.ts` y los de siempre.

- [ ] **Step 5: Commit de la web**

```bash
git add CHANGELOG.md docs/paridad/formulario.md docs/paridad/imeis.md
git commit -m "docs: changelog y paridad de la pieza 2 del SKU del taller (0.9.21)"
```

- [ ] **Step 6: Plan maestro**

En `Apuntes/plan-futuro.md` §5, en la entrada del SKU del taller: tras lo que dejó la pieza 1, añade «Pieza 2 (lo automático
desde las reparaciones): plan `2026-10-21-v0921-sku-del-taller-automatico.md` (raíz), en las mismas ramas; preprod y
producción, juntas con la pieza 1 y la corrección en NSYS.» Sin marcar `[x]` hasta producción.

- [ ] **Step 7: Entrega (controlador, con OK del usuario)** — sustituye al Step 8 de la Task 13 de la pieza 1

1. Revisión final de las dos ramas, con las dos piezas (`git log --oneline main..dev_v0921_sku_taller` en cada repo).
2. Con el OK del usuario: merge `--no-ff` de `dev_v0921_sku_taller` en `main` del servidor y de la web, con push de los dos; en
   la raíz, gitlinks, `NOVEDADES-v0.9.21.md`, este plan y el de la pieza 1 marcados y el §8 de las dos specs.
3. Después, la Task 14 de la pieza 1 (preprod) con los cambios de la sección de abajo; los worktrees y las ramas se borran
   tras producción (Task 15).

---

## Cambios en el plan de la pieza 1

No se edita el plan de la pieza 1: esto es lo que cambia en él al ir esta pieza en sus ramas.

**Orden y Global Constraints**
- Las Tasks 1-13 de este plan van **entre el Step 7 de la Task 13 y la Task 14** de la pieza 1. El **Step 8 de su Task 13**
  (entrega) **no se hace** al acabar la pieza 1: lo hace el Step 7 de la Task 13 de este plan, con las dos piezas.
- «Base de datos: solo una migración aditiva (`CREATE TABLE Taller_cambio`)» pasa a ser «… (`CREATE TABLE Taller_cambio` y
  `CREATE TABLE Taller_aviso`, en el mismo fichero)». Sigue siendo aditiva y compatible con la web 0.9.20 (las escrituras del
  formulario no cambian de forma; los tipos nuevos del rastro los pinta la web vieja con su texto genérico).
- El TECNICO **sí** ve algo nuevo: la banda del formulario (la regla «El TECNICO no ve nada nuevo» de la pieza 1 deja de valer
  para la 0.9.21 completa).

**Código de la pieza 1 que tocan las tareas de esta** (por si el revisor lo ve cambiado)
- Task 1: `TallerDAO.SQL_INSERTAR` lleva `ID_REP` (y el test `guardarBloqueaElMovilSustituyeElVigenteYApuntaAEl`, un `null`
  más); `guardar` gana la variante con `idRep`; `migracion-sku-taller.sql`, `crear_bd.sql` (también el `DROP TABLE IF EXISTS
  Taller_cambio;` que faltaba), `reset-uat.sql` y `schema.md` con `Taller_aviso`; `CambioTaller.REPARACION`.
- Task 5: `TallerNsys.perezoso` pasa a ser de paquete.
- Task 8: `Rastro.unir` gana `avisos`; `TallerNsys.avisos`; `StockTelefonosController.rastro` los pasa.
- Task 11: en `rastro.ts` desaparece el mapa `ORIGEN` («por una reparación») y `comoTaller` distingue a mano y reparación; en
  `porPasar.ts`, `textoPorQue` no pone comillas a la pieza de una reparación (su test de la pieza 1 sigue valiendo).

**Task 13 (cierre) de la pieza 1**
- Step 2 (CHANGELOG) y Step 3 (paridad de `imeis.md`): se hacen como están; la Task 13 de este plan les añade lo suyo.
- Step 4 (novedades): la sección «🧰 Para todos — El técnico ve el programa como hasta ahora» y la frase «Lo automático desde
  las reparaciones… llega en la siguiente pieza» las cambia la Task 13 de este plan (aviso al taller: ahora las reparaciones
  cambian el color y el grado).
- Step 5 (suites): se repiten al final de esta pieza.
- Step 7 (plan maestro): «falta preprod y producción» sigue valiendo; esta pieza añade su línea.
- Step 8 (entrega): pasa a la Task 13 de este plan.

**Task 14 (preproducción) de la pieza 1**
- **Bloque 1 (solo lee):**
  - la vista previa de tablas pasa a `… AND TABLE_NAME IN ('Taller_cambio', 'Taller_aviso');` → 0;
  - los chasis del iPhone 13 del catálogo (la E2E elige uno de otro color):
    `SELECT TIPO, ACTIVO FROM Componente WHERE TIPO LIKE 'chai13%' AND TIPO NOT LIKE 'chai13pro%' AND TIPO NOT LIKE 'chai13mini%';`
    debe haber al menos dos colores activos;
  - los colores del iPhone 13 128GB del espejo con su Goods (de ahí sale el nombre en inglés):
    `SELECT COLOR, GOODS, COUNT(*) FROM Nsys_movil WHERE MODELO = '13' AND CAPACIDAD = '128GB' AND ES_ESIM = FALSE AND COLOR IS NOT NULL GROUP BY COLOR, GOODS ORDER BY COLOR, 3 DESC;`
    **Si un color sale con un Goods que no lleva la capacidad o no lleva el color en inglés detrás, PARAR y avisar**
    (`ColorChasis.nombreDelGoods` no lo leería).
- **Bloque 2:** la migración crea las dos tablas; después `SHOW CREATE TABLE Taller_cambio\G` y `SHOW CREATE TABLE Taller_aviso\G`.
- **E2E:** `sku-taller`, ≥ 60 s, **`sku-taller-reparacion`**, ≥ 60 s, `stock-telefonos`, `roles-stock`, `formulario` y
  `taller` (las dos del formulario: el guardado pasa ahora por `GuardadoReparacion`).
- **Pruebas con datos reales (usuario, en preprod):** abrir el formulario de dos o tres asignaciones reales de móviles de NSYS,
  elegir un chasis de otro color y una batería nueva y comprobar la banda (color del maestro, grado); «×» para volver atrás sin
  guardar, o guardar y borrar la reparación (en preprod se puede) y ver «Se deshace…» en el rastro. Un móvil con grado `ID` o
  `n/a` debe decir «el grado (…) no se puede calcular».
- **Limpieza (bloque 3):** además de lo de la pieza 1, `DELETE FROM Taller_aviso WHERE IMEI_SN = '350000000009921';` y, si la
  E2E dejó algo (`SELECT ID_REP FROM Reparacion WHERE IMEI = '350000000009921';` no vacío), sus filas:
  `DELETE rc FROM Reparacion_componente rc JOIN Reparacion r ON r.ID_REP = rc.ID_REP WHERE r.IMEI = '350000000009921'; DELETE FROM Reparacion_borrador WHERE ID_REP IN (SELECT ID_REP FROM Reparacion WHERE IMEI = '350000000009921'); DELETE FROM Reparacion WHERE IMEI = '350000000009921'; DELETE FROM Telefono WHERE IMEI = '350000000009921';`
  (el `UPDATE … ID_CAMBIO_ANTERIOR = NULL` y el `DELETE FROM Taller_cambio` de la pieza 1 van antes).
- **Vuelta atrás:** también `DROP TABLE Taller_aviso;` si se quitan las tablas.

**Task 15 (producción) de la pieza 1**
- Bloque 1: la vista previa con las dos tablas → 0. Bloque 2: la migración crea las dos; `SHOW CREATE TABLE` de las dos.
- Comprobación ligera: además, `POST /api/reparaciones/sku-taller/previsualizar` sin sesión → 403.
- A mano (usuario): la primera reparación real con chasis o batería de un móvil de NSYS: la banda sale y, tras terminar, la
  ficha y «Por pasar a NSYS» lo enseñan. Sin crear datos de prueba en producción.
- Aviso al taller: añadir «Al guardar una reparación con chasis, tapa trasera o batería, el programa cambia solo el color y el
  grado del móvil; lo veréis en una banda encima de «Terminar asignación».»
- Cierre: `plan-futuro.md` con las dos piezas marcadas `[x]`; memoria del proyecto con las dos.

## Preguntas para el usuario

1. **¿Vale guardar los avisos en una tabla nueva, `Taller_aviso`, dentro de la misma migración?** Recomendación: sí; es
   aditiva, va en el mismo fichero que aún no se ha aplicado y es lo que permite que el rastro diga «no se puede deducir» y
   «Se deshace…».
2. **¿Quién sale como «<técnico>» en el rastro y en «Por qué»: quien guarda o el técnico de la reparación?** Recomendación:
   quien guarda (su usuario, como en la corrección a mano): en el formulario del técnico es él; al editar o borrar, el
   supertécnico que lo hace.
3. **¿Basta con el rastro para los avisos «lo corregirá un supertécnico»?** Recomendación: sí por ahora (así lo dice la
   spec); si se quedan sin ver, una marca en la lista o en el stock iría en una versión siguiente.
