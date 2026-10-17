# 0.9.17 — Pieza 1 del módulo de teléfonos: stock de teléfonos y rastro · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** el ERP tiene un espejo de solo lectura de NSYS, leído de Drive cada 5 minutos, con el que enseña el stock de
teléfonos y el rastro de cada teléfono. Además, el modelo de un IMEI sale del espejo y deja de pedirse a IMEIcheck.

**Architecture:**
- **Servidor:** paquete nuevo `com.reparaciones.servidor.nsys`.
  - **Reglas puras, sin base de datos y probadas sin ella:** normalización, traducción con el maestro y las cajas,
    estado del móvil y comparación de exportaciones.
  - **Lectores de `.xlsx`:** Apache POI en streaming.
  - **Acceso a datos:** un DAO con JdbcTemplate sobre cinco tablas `Nsys_*` nuevas.
  - **Drive:** un cliente REST mínimo con la librería de autenticación de Google.
  - **Tareas:** una de importación (cada 5 min) y otra del histórico (de noche). Las dos se encienden por ajuste.
  - **Lectura:** un controlador de solo lectura.
  - **IMEIcheck:** se sustituye por `ModeloNsys`.
- **Web:**
  - módulo nuevo `telefonos`: pantalla «Stock de teléfonos», opción C del prototipo;
  - en `taller/imeis`, la ficha y el panel del rastro en el detalle del IMEI, y el aviso de móviles sin trabajos en la
    lista.
- **E2E** de solo lectura en preprod.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB, Apache POI 5.2.5 (`poi-ooxml`) y google-auth-library 1.23.0.
  Pruebas con JUnit 5, Mockito y MockMvc.
- **Web:** React 19 + TypeScript, React Router (data router), TanStack Query/Table y openapi-fetch. Pruebas con Vitest,
  Testing Library, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-16-pieza1-stock-telefonos-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-10-16-pieza1-prototipo-stock-telefonos.html`.

## Global Constraints

**Versión, código de partida y base de datos**
- Versión **0.9.17**. Parte de servidor `main` `83be807` y web `main` `4aca632` (0.9.16).
- **Base de datos:** migración aditiva `gestion-reparaciones-servidor/sql/migracion-nsys-espejo.sql`. Son cinco tablas
  nuevas más la carga de `Nsys_ubicacion`, sin claves ajenas a `Telefono`. **La aplica el usuario a mano.**

**Ramas, worktrees y registro**
- Todo va en la rama **`dev_v0917_stock_telefonos`**, en worktrees. Los clones normales no se tocan.
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0917`.
  - Web: `/c/Users/dev/Documents/wt/web-0917`, con `npm ci` dentro.
  - Las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan se refieren a esos worktrees;
    `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0917/`** (raíz).

**Commits, merge y datos de prueba**
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- Commits en español sin tildes, con prefijo `feat:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- **Repos públicos:** datos de prueba sintéticos («Proveedor A», IMEI `35000000000xxxx`, lotes `BT-9001`…). Nunca
  identificadores de Drive, correos de cuentas de servicio, IP ni datos reales de NSYS.

**Reglas de rol** (spec §3)
- `/api/stock-telefonos/**`: `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")` en la clase. No hay jerarquía de
  roles: `hasRole('SUPERTECNICO')` dejaría fuera al ADMIN.
- **Web:** el grupo «Teléfonos», la ficha de NSYS, el botón «Ver rastro» y el aviso de móviles sin trabajos solo con
  `esAdminOSuperTecnico(sesion)`. El TECNICO ve IMEIs exactamente como hoy.
- `GET /api/telefonos/{imei}/modelo`: misma ruta, misma respuesta (`ValorTexto`) y mismos permisos que hoy.

**Ajustes del servidor**

| Propiedad | Variable de entorno | Por defecto |
|---|---|---|
| `nsys.importacion.activa` | `NSYS_IMPORTACION_ACTIVA` | `false` |
| `nsys.drive.carpeta-items` | `NSYS_DRIVE_CARPETA_ITEMS` | `""` |
| `nsys.drive.maestro` | `NSYS_DRIVE_MAESTRO` | `""` |
| `nsys.drive.credenciales` | `NSYS_DRIVE_CREDENCIALES` | `""` |
| `nsys.historico.activo` | `NSYS_HISTORICO_ACTIVO` | `false` |

- Las dos tareas llevan `@ConditionalOnProperty(..., havingValue = "true")`: en los tests no existen.
- `ClienteDrive` lee las credenciales la primera vez que hace falta, nunca al arrancar.

**Horas y umbrales**
- Todas las horas del espejo son de **Europe/Madrid**, como `LocalDateTime` sin zona, tanto en la base de datos como en la
  API. **Ojo:** el resto de la BD (y la JVM) va en UTC, y la web lo pasa a Madrid con `shared/lib/fechas.ts`. Las horas
  de `Reparacion` que entran en el rastro se pasan de UTC a Madrid en `NsysDAO.trabajos` (`utcAMadrid`). La web corta
  las del espejo tal cual (`diaNsys`, `rastro.ts`, `stock/nsys.ts`), sin `formatear`.
- **Importación:** `@Scheduled(initialDelay = 60_000, fixedDelay = 300_000)`.
- **Freno de seguridad:** un fichero con **menos del 90 %** de las filas del fichero anterior (`OK` o `RECHAZADO`; si
  tiene 0 filas, sin freno) se rechaza (`MINIMO_FILAS = 0.9`). Cambiado el 17/10 en la revisión de la Task 6: antes
  comparaba con el último `OK` y no se recuperaba solo de una bajada real.
- **Espera de Drive:** un Items modificado hace menos de 2 minutos se deja para la siguiente pasada
  (`ESPERA_DRIVE`). `FicheroItems` lleva `Instant modificado`. Añadido el 17/10 en la revisión de la Task 6.
- **Revisión final (17/10).** Añadido en el servidor:
  - freno por claves repetidas (> 1 %);
  - ficheros con fecha más de un día en el futuro, saltados;
  - celdas numéricas leídas enteras;
  - anchos de columna en `AnchosNsys`, cortados al leer;
  - fallo de `rellenarModelosTelefono` sin tumbar la importación;
  - plazo total en las llamadas a Drive;
  - lote más reciente por `VISTO_PRIMERO, RECEIVING_DATE, BATCH`;
  - motivo genérico para `ERROR`;
  - `gradoAnterior` sin los cambios solo de batería;
  - migración con `SET NAMES utf8mb4`, vista previa de intercalaciones y comprobación HEX (6).

  Añadido en la web:
  - `textoGrado`;
  - «Volver» al stock con su estado guardado;
  - píldora gris sin datos;
  - aviso de IMEIs solo con IMEI completo;
  - el TECNICO sin el texto de «sin trabajos».

  Detalle en `.superpowers/sdd/v0917/progress.md`.
- **Aviso:** se cuentan solo los minutos **hábiles** (07:00-23:00). Más de **60** → `RETRASO`; más de **180** →
  `SIN_DATOS`; sin ninguna importación → `SIN_DATOS`.
- **Histórico:** `@Scheduled(initialDelay = 120_000, fixedDelay = 60_000)`, **un fichero por pasada** y solo entre las
  **23:00 y las 07:00**.

**Textos exactos de la web**
- Grupo «Teléfonos»; enlace y título «Stock de teléfonos».
- Interruptor: «Ver también lo que no cuenta como stock».
- Botón: «Ver rastro (n)».
- Detalle vacío: «Este móvil no ha pasado por el taller».
- **Píldora de NSYS:**
  - `NSYS: exportación de hoy HH:MM · hace N min` (al día);
  - `NSYS: última exportación hace N h N min (HH:MM)` (`RETRASO`);
  - `NSYS: sin datos nuevos desde hace N h` (`SIN_DATOS` con fecha);
  - `NSYS: sin datos todavía` (sin importaciones).
- **Banda roja** (`SIN_DATOS`): `No llegan datos nuevos de NSYS desde las HH:MM. El PC de producción o Drive pueden
  estar parados. Se enseñan los últimos datos recibidos.` Sin fecha: `Todavía no ha llegado ningún dato de NSYS.`
- **Guarda:** aviso `MSG_SIN_PERMISOS` (`No tienes permisos para realizar esta acción.`) y vuelta a `/reparaciones`.

**Dependencias nuevas** (servidor, `pom.xml`)
- `org.apache.poi:poi-ooxml:5.2.5`, la misma que usaba el JavaFX.
- `com.google.auth:google-auth-library-oauth2-http:1.23.0`.

**Ajustes a la spec, ya recogidos en ella**
- **`Nsys_movil`:** no tiene `VISTO_ULTIMO`. Basta con `CAMBIADO` y la hora de la última importación, y así no se
  reescriben 70.000 filas cada pasada. Guarda también `MODELO_MAESTRO`.
- **`Nsys_importacion`:**
  - `ORIGEN` admite `MAESTRO`;
  - `RESULTADO` admite `EN_CURSO`;
  - `FICHERO` no es único, porque un fichero que da `ERROR` se reintenta.
- **Tipo de evento `PRODUCTO`:** cambia «Goods», por ejemplo el color tras un cambio de chasis.
- **Ventas:** Released → Available/InProcess/Repair es `VENTA_DESHECHA`; Released → Removed es `ESTADO` (reentrada).
- **Venta repetida:** el esqueleto apunta `VENTA` con `SOLD_DATE` en cualquier fila que la tenga. Si el mismo lote
  tiene además una `VENTA` con hora (del histórico o de una importación), el rastro no enseña la de solo el día.
- **Modelos:** un código que no está en `CatalogoPiezas.MODELOS_ORDENADOS` (17e, SE 2022, antiguos) deja `MODELO` a
  `NULL` y se avisa en el registro de la importación. **No se tocan las listas de modelos**, igual que cuando IMEIcheck
  no sabía el modelo.
- **API:** el rastro llega estructurado (`EventoRastro`) y la web compone los textos.

**Comandos**
- **Servidor** (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `/c/Users/dev/Documents/wt/servidor-0917`.
- **Web:** `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint` dentro de `/c/Users/dev/Documents/wt/web-0917`.

**Preproducción y producción**
- **Claude no hace SSH.** Prepara los guiones en `Apuntes/` y el usuario los ejecuta y pega la salida.
- Cada bloque de guion empieza comprobando la máquina: `hostname` + `backup-erp.sh`, que solo existe en producción.
- La clave de Drive se copia a cada VM siguiendo `Apuntes/diseno-modulo-telefonos/drive-cuenta-servicio-erp.md`
  (fase 2).
- `NSYS_HISTORICO_ACTIVO` va a `"true"` desde el despliegue, en preprod y en producción. La tarea solo trabaja de 23:00 a
  07:00, así que el histórico empieza esa misma noche sin otro reinicio, que es lo que pide la spec (§11).
- `IMEICHECK_API_KEY` se queda en el compose hasta que el usuario da por buena la producción, para que la vuelta atrás
  recupere IMEIcheck (spec §11.5). Después se quita y el usuario pide anular las claves.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`, paquete `src/main/java/com/reparaciones/servidor/nsys/` salvo que se diga otra cosa)

| Fichero | Responsabilidad |
|---|---|
| `sql/migracion-nsys-espejo.sql` (nuevo), `sql/crear_bd.sql`, `sql/reset-uat.sql`, `docs/schema.md` | Esquema de las cinco tablas `Nsys_*` |
| `Normalizacion.java` | Claves del maestro, cajas, código de modelo del ERP, eSIM, grado y batería |
| `UbicacionNsys.java`, `ProductoMaestro.java`, `EstadoMovil.java`, `Traductor.java` | Traducción de una fila de NSYS: producto del maestro, caja → ubicación y estado |
| `FilaItems.java`, `ClaveMovil.java`, `MovilNsys.java`, `TipoEvento.java`, `EventoNsys.java`, `ResultadoComparacion.java`, `Comparador.java` | Comparación pura de una exportación con lo anterior (foto y eventos) |
| `FormatoItemsException.java`, `HojaXlsx.java`, `LectorItems.java`, `LectorMaestro.java`, `ResultadoMaestro.java` | Lectura en streaming del fichero Items y del maestro |
| `NsysDAO.java`, `Importacion.java`, `TrabajoTaller.java` | Acceso a las tablas `Nsys_*` y a los trabajos del taller de un IMEI |
| `ClienteDrive.java` | Listar, leer metadatos y descargar de Drive con la cuenta de servicio |
| `ImportadorNsys.java`, `TareaImportacionNsys.java` | Una pasada de importación y su tarea cada 5 min |
| `HistoricoNsys.java`, `TareaHistoricoNsys.java` | Carga del histórico, un fichero por pasada y de noche |
| `AvisoNsys.java`, `EventoRastro.java`, `Rastro.java`, `StockTelefonosController.java` | API de solo lectura: stock, estado, buscar, ficha y rastro |
| `ModeloNsys.java` | Modelo de un IMEI desde el espejo (sustituye a IMEIcheck) |
| `controller/TelefonoController.java`, `controller/AsignacionController.java`, `controller/GlassController.java`, `controller/PulidoController.java` | Pasan de `ImeiLookupService` a `ModeloNsys` |
| `service/ImeiLookupService.java`, `security/FrenoLookup.java` (y sus tests) | Se borran |
| `pom.xml`, `docs/deployment.md` | Dependencias y variables nuevas |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts` | Contrato y alias de tipos |
| `src/shared/lib/telefonoNsys.ts` | Nombre del modelo, colores de las ubicaciones, texto del estado y día dd/mm/aaaa: lo comparten el módulo `telefonos` y `taller/imeis` |
| `src/test/server.ts` | Handlers por defecto de MSW: NSYS no conoce el IMEI y la búsqueda no encuentra nada |
| `src/shared/lib/enlaces.ts`, `src/app/shell/navegacion.ts`, `src/app/shell/preferencias.ts`, `src/app/router.tsx` | Grupo «Teléfonos» y ruta `/telefonos/stock` |
| `src/modules/telefonos/rutas.tsx` | Enlaces del grupo y guarda `RequiereAccesoTelefonos` |
| `src/shared/api/nsys.ts` | `useBuscarMoviles`: buscar un IMEI en el espejo (lo usan los dos módulos) |
| `src/modules/telefonos/api.ts` | `useStockTelefonos`, `useEstadoNsys` |
| `src/modules/telefonos/stock/modelo.ts` | Reglas puras: grupos, totales, filtros, opciones y muestras de color |
| `src/modules/telefonos/stock/nsys.ts` | Textos de la píldora y de la banda de NSYS, en hora de Madrid |
| `src/modules/telefonos/stock/{StockTelefonosPage,ListaModelos,FiltrosMoviles,PildoraNsys,BuscadorImei}.tsx`, `columnas.tsx` | Pantalla «Stock de teléfonos» |
| `src/modules/telefonos/test/fabrica.ts` | Datos sintéticos de los tests del módulo |
| `src/modules/taller/imeis/apiNsys.ts` | `useFichaMovil`, `useRastro` |
| `src/modules/taller/imeis/rastro.ts`, `FichaMovil.tsx`, `PanelRastro.tsx` | Textos del rastro, ficha y panel lateral |
| `src/modules/taller/imeis/ImeiDetallePage.tsx`, `ImeisPage.tsx`, `src/modules/taller/test/fabrica.ts` | Ficha + «Ver rastro» en el detalle; aviso de móviles sin trabajos en la lista |
| `tests/e2e/stock-telefonos.spec.ts`, `.env.e2e.example` | E2E de solo lectura en preprod |
| `package.json`, `CHANGELOG.md`, `docs/paridad/imeis.md` | Versión 0.9.17 y documentación |

**Raíz y apuntes** (fuera de los worktrees)

| Fichero | Responsabilidad |
|---|---|
| `docs/novedades/NOVEDADES-v0.9.17.md` | Novedades para el taller |
| `Apuntes/preprod/v0917-preprod.md`, `Apuntes/prod-v0917.md` | Guiones de despliegue (privados) |

---

### Task 1: Servidor — esquema del espejo y traducción de una fila de NSYS

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-nsys-espejo.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (al final), `gestion-reparaciones-servidor/sql/reset-uat.sql`, `gestion-reparaciones-servidor/docs/schema.md`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Normalizacion.java`, `UbicacionNsys.java`, `ProductoMaestro.java`, `EstadoMovil.java`, `Traductor.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/NormalizacionTest.java`, `EstadoMovilTest.java`, `TraductorTest.java`

**Interfaces:**
- Consumes: `com.reparaciones.servidor.util.CatalogoPiezas.esModelo(String)` (ya existe).
- Produces:
  - `Normalizacion.clave(String goods, String grade)`, `Normalizacion.clave(String productNameCalidad)`, `Normalizacion.caja(String location)`, `Normalizacion.modeloErp(String modeloMaestro)`, `Normalizacion.esEsim(String goods)`, `Normalizacion.gradoSinBateria(String grade)`, `Normalizacion.bateriaNueva(String grade)` (todos `public static`).
  - `record UbicacionNsys(String location, String ubicacion, boolean cuentaStock, boolean esPedido)`.
  - `record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color, String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim)`.
  - `enum EstadoMovil { STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO, BAJA }` con `public static final Set<String> VIVOS` y `public static EstadoMovil de(String status, UbicacionNsys caja)`.
  - `final class Traductor` con `Traductor(Collection<ProductoMaestro>, Collection<UbicacionNsys>)`, `ProductoMaestro producto(String goods, String grade)` y `UbicacionNsys caja(String location)`.

- [x] **Step 1: Migración**

Crea `sql/migracion-nsys-espejo.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-nsys-espejo.sql — 0.9.17, pieza 1 del módulo de teléfonos: espejo de NSYS
-- (spec docs/superpowers/specs/2026-10-16-pieza1-stock-telefonos-design.md §4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.17. Aditiva: el servidor y la web anteriores no usan estas tablas.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: el SELECT debe devolver 0 (ninguna existe).
-- 2) Ejecutar los CREATE TABLE y el INSERT de Nsys_ubicacion.
-- Horas de Madrid en todas las columnas DATETIME.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Nsys_importacion', 'Nsys_movil', 'Nsys_evento', 'Nsys_producto', 'Nsys_ubicacion');

-- Un registro por fichero procesado. FICHERO no es único: un fichero que da ERROR se reintenta.
CREATE TABLE Nsys_importacion (
    ID_IMPORTACION    INT          NOT NULL AUTO_INCREMENT,
    FICHERO           VARCHAR(120) NOT NULL,
    FECHA_EXPORTACION DATETIME     NOT NULL COMMENT 'Items: la del nombre del fichero; maestro: su modifiedTime',
    ORIGEN            ENUM('ACTUAL','HISTORICO','MAESTRO') NOT NULL,
    EMPEZADA          DATETIME     NOT NULL,
    TERMINADA         DATETIME     NULL,
    FILAS             INT          NOT NULL DEFAULT 0,
    CAMBIOS           INT          NOT NULL DEFAULT 0,
    RESULTADO         ENUM('EN_CURSO','OK','RECHAZADO','ERROR') NOT NULL DEFAULT 'EN_CURSO',
    MENSAJE           VARCHAR(500) NULL,
    PRIMARY KEY (ID_IMPORTACION),
    KEY idx_nsys_importacion (ORIGEN, RESULTADO, FECHA_EXPORTACION)
);

-- La foto actual: una fila por móvil y lote, como en la última exportación. Sin costes ni precios.
CREATE TABLE Nsys_movil (
    IMEI_SN          VARCHAR(40)  NOT NULL COMMENT '«IMEI/SN» de NSYS: puede ser un número de serie',
    BATCH            VARCHAR(40)  NOT NULL,
    GOODS            VARCHAR(150) NULL,
    GRADE            VARCHAR(20)  NULL,
    STATUS           VARCHAR(30)  NULL,
    LOCATION         VARCHAR(80)  NOT NULL DEFAULT '' COMMENT 'Caja de NSYS tal cual; vacía = sin caja',
    SUPPLIER         VARCHAR(80)  NULL,
    RECEIVING_DATE   DATE         NULL,
    SOLD_DATE        DATE         NULL,
    SKU              VARCHAR(60)  NULL,
    MODELO           VARCHAR(20)  NULL COMMENT 'Código de modelo del ERP (13pro…); NULL si no está en el catálogo',
    MODELO_MAESTRO   VARCHAR(40)  NULL,
    CAPACIDAD        VARCHAR(20)  NULL,
    COLOR            VARCHAR(40)  NULL,
    GRADO_DETALLE    VARCHAR(20)  NULL,
    ES_ESIM          BOOLEAN      NOT NULL DEFAULT FALSE,
    UBICACION        VARCHAR(40)  NULL COMMENT 'Ubicación del ERP; NULL si la caja no se trae',
    UBICACION_ULTIMA VARCHAR(40)  NULL COMMENT 'Última ubicación del ERP antes de «Pedidos realizados»',
    ESTADO           ENUM('STOCK','DE_PEDIDO','NO_CUENTA','VENDIDO','BAJA') NOT NULL,
    VISTO_PRIMERO    DATETIME     NOT NULL,
    CAMBIADO         DATETIME     NOT NULL,
    PRIMARY KEY (IMEI_SN, BATCH),
    KEY idx_nsys_movil_estado (ESTADO),
    KEY idx_nsys_movil_modelo (MODELO, CAPACIDAD)
);

-- El historial para el rastro: solo se añade, nunca se modifica.
CREATE TABLE Nsys_evento (
    ID_EVENTO      BIGINT       NOT NULL AUTO_INCREMENT,
    IMEI_SN        VARCHAR(40)  NOT NULL,
    BATCH          VARCHAR(40)  NOT NULL,
    FECHA          DATETIME     NOT NULL COMMENT 'Hora de la exportación en que se vio el cambio',
    SOLO_DIA       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Esqueleto: fecha de NSYS sin hora',
    TIPO           ENUM('ENTRADA','UBICACION','GRADO','PRODUCTO','ESTADO','VENTA','VENTA_DESHECHA','REENTRADA') NOT NULL,
    DE             VARCHAR(150) NULL,
    A              VARCHAR(150) NULL,
    A_NSYS         VARCHAR(80)  NULL COMMENT 'En UBICACION: la caja de NSYS tal cual',
    ID_IMPORTACION INT          NULL,
    PRIMARY KEY (ID_EVENTO),
    KEY idx_nsys_evento_imei (IMEI_SN, FECHA),
    CONSTRAINT fk_nsys_evento_importacion FOREIGN KEY (ID_IMPORTACION) REFERENCES Nsys_importacion (ID_IMPORTACION)
);

-- El maestro de productos: se recarga entero cuando cambia en Drive.
CREATE TABLE Nsys_producto (
    CLAVE           VARCHAR(160) NOT NULL COMMENT '«Product Name Calidad» sin espacios y en minúsculas',
    MODELO          VARCHAR(20)  NULL COMMENT 'Código de modelo del ERP; NULL si no está en el catálogo',
    MODELO_MAESTRO  VARCHAR(40)  NULL,
    CAPACIDAD       VARCHAR(20)  NULL,
    COLOR           VARCHAR(40)  NULL,
    CALIDAD_DETALLE VARCHAR(20)  NULL,
    SKU_WEB         VARCHAR(60)  NULL,
    SKU_AMAZON      VARCHAR(60)  NULL,
    SKU_SIN_GRADO   VARCHAR(60)  NULL,
    ES_ESIM         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (CLAVE)
);

-- Traducción de cajas de NSYS a ubicaciones del ERP. Es una tabla, no código: se ajusta sin programar.
CREATE TABLE Nsys_ubicacion (
    LOCATION     VARCHAR(80) NOT NULL COMMENT 'Caja de NSYS, texto exacto; vacía = sin caja',
    UBICACION    VARCHAR(40) NULL COMMENT 'Ubicación del ERP; NULL = no se trae',
    CUENTA_STOCK BOOLEAN     NOT NULL DEFAULT FALSE,
    ES_PEDIDO    BOOLEAN     NOT NULL DEFAULT FALSE,
    ORDEN        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (LOCATION)
);

-- Propuesta del 16/10/2026 (spec §4.5), pendiente de que el usuario la confirme: solo cambia esta carga.
INSERT INTO Nsys_ubicacion (LOCATION, UBICACION, CUENTA_STOCK, ES_PEDIDO, ORDEN) VALUES
    ('',                                                 'En revisión',            TRUE,  FALSE,  10),
    ('Funcionalidad pendiente',                          'Devolución',             TRUE,  FALSE,  20),
    ('CAJA PULIR',                                       'Taller · Pulir',         TRUE,  FALSE,  30),
    ('CAJA GLASS',                                       'Taller · Glass',         TRUE,  FALSE,  40),
    ('CAJA REP. OFICINA',                                'Taller · Reparación',    TRUE,  FALSE,  50),
    ('C.R. Pantalla Rota',                               'Taller · Pantalla rota', TRUE,  FALSE,  60),
    ('CAJA LISTOS',                                      'Listos',                 TRUE,  FALSE,  70),
    ('AFaltaRepuestos',                                  'Esperando pieza',        FALSE, FALSE,  80),
    ('CF021',                                            'No vendible',            FALSE, FALSE,  90),
    ('Pedidos realizados',                               NULL,                     FALSE, TRUE,  100),
    ('Desguace',                                         NULL,                     FALSE, FALSE, 200),
    ('Pendiente revisar si hay error de inventario',     NULL,                     FALSE, FALSE, 210),
    ('CF018',                                            NULL,                     FALSE, FALSE, 220),
    ('CF029',                                            NULL,                     FALSE, FALSE, 230),
    ('Incidencia (pérdida, robo, transportista)',        NULL,                     FALSE, FALSE, 240),
    ('MV CF018',                                         NULL,                     FALSE, FALSE, 250),
    ('MV CF021',                                         NULL,                     FALSE, FALSE, 260),
    ('MV Desguace',                                      NULL,                     FALSE, FALSE, 270),
    ('MV Pedidos Realizados',                            NULL,                     FALSE, FALSE, 280),
    ('MV Pendiente revisar si hay error de inventario',  NULL,                     FALSE, FALSE, 290);
```

- [x] **Step 2: `crear_bd.sql`, `reset-uat.sql` y `schema.md`**

- **`crear_bd.sql`:** al final del fichero, tras la última sentencia, añade una línea en blanco y el comentario
  `-- 0.9.17: espejo de NSYS (pieza 1 del módulo de teléfonos; ver sql/migracion-nsys-espejo.sql)`.
  Debajo, copia **tal cual** los cinco `CREATE TABLE` y el `INSERT INTO Nsys_ubicacion` del Step 1, sin el `USE` ni la
  vista previa.
- **`reset-uat.sql`:** justo antes de `-- 6. Teléfonos (IMEIs)`, añade:

```sql
-- 5b. Espejo de NSYS (0.9.17): eventos antes que importaciones (clave ajena). Se conservan el maestro y las cajas.
DELETE FROM Nsys_evento;
DELETE FROM Nsys_movil;
DELETE FROM Nsys_importacion;

```

- **`docs/schema.md`:** al final de la lista de tablas, añade:

```markdown
- `Nsys_importacion` (0.9.17: un registro por fichero de NSYS procesado —Items, histórico o maestro—; alimenta el aviso de datos antiguos)
- `Nsys_movil` (0.9.17: la foto actual de NSYS, una fila por IMEI/SN y lote; sin costes ni precios)
- `Nsys_evento` (0.9.17: historial de cambios de NSYS para el rastro del teléfono; solo se añade)
- `Nsys_producto` (0.9.17: el maestro de productos, «Goods + Grade» → modelo, color y SKU)
- `Nsys_ubicacion` (0.9.17: traducción de cajas de NSYS a ubicaciones del ERP; datos, no código)
```

- [x] **Step 3: Tests de las reglas de texto, del estado y del traductor (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/NormalizacionTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizacionTest {

    @Test void laClaveDelMaestroVaSinEspaciosYEnMinusculas() {
        assertEquals("appleiphone13128gbbluea-", Normalizacion.clave("Apple iPhone 13 128GB Blue", "A-"));
        assertEquals("appleiphone13128gbbluea-", Normalizacion.clave("  Apple iPhone 13  128GB Blue A- "));
        assertEquals("", Normalizacion.clave(null, null));
    }

    @Test void laCajaVaSinEspaciosALosLadosYSinCajaEsVacia() {
        assertEquals("CAJA LISTOS", Normalizacion.caja(" CAJA LISTOS "));
        assertEquals("", Normalizacion.caja(null));
        assertEquals("", Normalizacion.caja("   "));
    }

    @Test void elModeloDelMaestroPasaAlCodigoDelErp() {
        assertEquals("13pro", Normalizacion.modeloErp("13 PRO"));
        assertEquals("14promax", Normalizacion.modeloErp("14 PRO MAX eSIM"));
        assertEquals("16e", Normalizacion.modeloErp("16 e"));
        assertEquals("se2020", Normalizacion.modeloErp("SE2020"));
        assertEquals("xsmax", Normalizacion.modeloErp("XS Max"));
        assertEquals("6splus", Normalizacion.modeloErp("6S Plus"));
        assertEquals("air", Normalizacion.modeloErp("Air"));
        assertEquals("13", Normalizacion.modeloErp("13"));
    }

    @Test void unModeloFueraDelCatalogoQuedaSinCodigo() {
        assertNull(Normalizacion.modeloErp("17 e eSIM"));
        assertNull(Normalizacion.modeloErp("SE2022"));
        assertNull(Normalizacion.modeloErp("5C"));
        assertNull(Normalizacion.modeloErp(null));
    }

    @Test void elEsimSaleDelNombreDelProducto() {
        assertTrue(Normalizacion.esEsim("Apple iPhone 15 128GB eSIM Black"));
        assertFalse(Normalizacion.esEsim("Apple iPhone 15 128GB Black"));
        assertFalse(Normalizacion.esEsim(null));
    }

    @Test void elCienDelGradoEsLaBateriaNueva() {
        assertEquals("A-", Normalizacion.gradoSinBateria("A-100"));
        assertEquals("C", Normalizacion.gradoSinBateria("C100"));
        assertEquals("A+", Normalizacion.gradoSinBateria("A+100"));
        assertEquals("A", Normalizacion.gradoSinBateria("A"));
        assertEquals("n/a", Normalizacion.gradoSinBateria("n/a"));
        assertEquals("", Normalizacion.gradoSinBateria(null));
        assertTrue(Normalizacion.bateriaNueva("B100"));
        assertFalse(Normalizacion.bateriaNueva("B"));
        assertFalse(Normalizacion.bateriaNueva("100"));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/EstadoMovilTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadoMovilTest {

    private static final UbicacionNsys LISTOS = new UbicacionNsys("CAJA LISTOS", "Listos", true, false);
    private static final UbicacionNsys PIEZA = new UbicacionNsys("AFaltaRepuestos", "Esperando pieza", false, false);
    private static final UbicacionNsys PEDIDOS = new UbicacionNsys("Pedidos realizados", null, false, true);
    private static final UbicacionNsys DESGUACE = new UbicacionNsys("Desguace", null, false, false);

    @Test void releasedEsVendido() {
        assertEquals(EstadoMovil.VENDIDO, EstadoMovil.de("Released", LISTOS));
    }

    @Test void losQueNoEstanVivosSonBaja() {
        for (String status : new String[]{"Removed", "RMA", "ReturnedToSupplier", null}) {
            assertEquals(EstadoMovil.BAJA, EstadoMovil.de(status, LISTOS));
        }
    }

    @Test void vivosSegunSuCaja() {
        for (String status : new String[]{"Available", "InProcess", "Repair"}) {
            assertEquals(EstadoMovil.STOCK, EstadoMovil.de(status, LISTOS));
            assertEquals(EstadoMovil.NO_CUENTA, EstadoMovil.de(status, PIEZA));
            assertEquals(EstadoMovil.DE_PEDIDO, EstadoMovil.de(status, PEDIDOS));
            assertEquals(EstadoMovil.BAJA, EstadoMovil.de(status, DESGUACE));
        }
    }

    @Test void unaCajaDesconocidaEsBaja() {
        assertEquals(EstadoMovil.BAJA, EstadoMovil.de("Available", null));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/TraductorTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraductorTest {

    private final ProductoMaestro azulA = new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"),
            "13", "13", "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false);
    private final Traductor t = new Traductor(List.of(azulA), List.of(
            new UbicacionNsys("", "En revisión", true, false),
            new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));

    @Test void encuentraElProductoPorGoodsYGrade() {
        assertSame(azulA, t.producto("Apple iPhone 13 128GB  Blue", "A"));
        assertNull(t.producto("Apple iPhone 13 128GB Blue", "B"));
    }

    @Test void encuentraLaCajaSinEspaciosYSinCaja() {
        assertEquals("Listos", t.caja(" CAJA LISTOS").ubicacion());
        assertEquals("En revisión", t.caja(null).ubicacion());
        assertNull(t.caja("CAJA NUEVA"));
    }
}
```

- [x] **Step 4: Ejecuta los tests (fallan)**

Run: `mvn -q test -Dtest='NormalizacionTest,EstadoMovilTest,TraductorTest'`
Expected: error de compilación: no existen `Normalizacion`, `EstadoMovil`, `UbicacionNsys`, `ProductoMaestro` ni `Traductor`.

- [x] **Step 5: Implementación**

Crea `src/main/java/com/reparaciones/servidor/nsys/Normalizacion.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Locale;

/** Reglas de texto del espejo de NSYS (spec pieza 1 §4.4 y §7): claves del maestro, cajas, modelos, eSIM y batería. */
public final class Normalizacion {

    private Normalizacion() {}

    /** Clave del maestro: «Goods» + «Grade» sin espacios y en minúsculas, igual que el verificador de SKU de las
     *  automatizaciones. */
    public static String clave(String goods, String grade) {
        String texto = (goods == null ? "" : goods) + (grade == null ? "" : grade);
        return texto.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** Clave de una fila del maestro a partir de su «Product Name Calidad». */
    public static String clave(String productNameCalidad) {
        return clave(productNameCalidad, null);
    }

    /** Caja de NSYS tal como se busca en Nsys_ubicacion: sin espacios a los lados; sin caja = "". */
    public static String caja(String location) {
        return location == null ? "" : location.strip();
    }

    /** Código de modelo del ERP a partir de la columna «Modelo» del maestro: en minúsculas, sin espacios y sin «eSIM»
     *  ("13 PRO" → 13pro, "16 e" → 16e, "SE2020" → se2020). null si el código no está en el catálogo del ERP: entonces el
     *  modelo se escribe a mano, como cuando IMEIcheck no lo sabía. */
    public static String modeloErp(String modeloMaestro) {
        if (modeloMaestro == null) return null;
        String codigo = modeloMaestro.toLowerCase(Locale.ROOT).replace("esim", "").replaceAll("\\s+", "");
        return CatalogoPiezas.esModelo(codigo) ? codigo : null;
    }

    /** NSYS escribe «eSIM» en el nombre del producto de los móviles solo eSIM. */
    public static boolean esEsim(String goods) {
        return goods != null && goods.toLowerCase(Locale.ROOT).contains("esim");
    }

    /** El grado sin la batería: "A-100" → "A-", "C100" → "C". La batería va en su propia columna. */
    public static String gradoSinBateria(String grade) {
        if (grade == null) return "";
        return bateriaNueva(grade) ? grade.substring(0, grade.length() - 3) : grade;
    }

    /** El «100» del final del grado de NSYS es la batería nueva al 100 %. */
    public static boolean bateriaNueva(String grade) {
        return grade != null && grade.length() > 3 && grade.endsWith("100");
    }
}
```

Crea `UbicacionNsys.java`:

```java
package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_ubicacion: la caja de NSYS, su ubicación del ERP (null = no se trae), si cuenta como stock y si es
 *  «Pedidos realizados». */
public record UbicacionNsys(String location, String ubicacion, boolean cuentaStock, boolean esPedido) {}
```

Crea `ProductoMaestro.java`:

```java
package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo». */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {}
```

Crea `EstadoMovil.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Set;

/** Estado de un móvil en el ERP según NSYS (spec pieza 1 §4.6). */
public enum EstadoMovil {
    STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO, BAJA;

    /** Los Status de NSYS que cuentan como móvil en la empresa (regla del Excel de stock). */
    public static final Set<String> VIVOS = Set.of("Available", "InProcess", "Repair");

    /** Released → VENDIDO; otro Status no vivo → BAJA; vivo: «Pedidos realizados» → DE_PEDIDO, caja que no se trae (o
     *  desconocida) → BAJA, caja que no cuenta → NO_CUENTA y el resto → STOCK. */
    public static EstadoMovil de(String status, UbicacionNsys caja) {
        if ("Released".equals(status)) return VENDIDO;
        if (status == null || !VIVOS.contains(status)) return BAJA;
        if (caja != null && caja.esPedido()) return DE_PEDIDO;
        if (caja == null || caja.ubicacion() == null) return BAJA;
        return caja.cuentaStock() ? STOCK : NO_CUENTA;
    }
}
```

Crea `Traductor.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Traduce lo que dice NSYS a lo que entiende el ERP: el producto del maestro y la ubicación de cada caja. */
public final class Traductor {

    private final Map<String, ProductoMaestro> productos = new HashMap<>();
    private final Map<String, UbicacionNsys> cajas = new HashMap<>();

    public Traductor(Collection<ProductoMaestro> productos, Collection<UbicacionNsys> cajas) {
        for (ProductoMaestro p : productos) this.productos.put(p.clave(), p);
        for (UbicacionNsys u : cajas) this.cajas.put(Normalizacion.caja(u.location()), u);
    }

    /** El producto del maestro para «Goods» + «Grade»; null si el maestro no lo tiene. */
    public ProductoMaestro producto(String goods, String grade) {
        return productos.get(Normalizacion.clave(goods, grade));
    }

    /** La traducción de una caja; null si no está en Nsys_ubicacion (caja nueva: se trata como «no se trae»). */
    public UbicacionNsys caja(String location) {
        return cajas.get(Normalizacion.caja(location));
    }
}
```

- [x] **Step 6: Ejecuta los tests (pasan)**

Run: `mvn -q test -Dtest='NormalizacionTest,EstadoMovilTest,TraductorTest'`
Expected: `BUILD SUCCESS`, 12 tests en verde.

- [x] **Step 7: Commit**

```bash
git add sql/migracion-nsys-espejo.sql sql/crear_bd.sql sql/reset-uat.sql docs/schema.md src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys
git commit -m "feat: esquema del espejo de NSYS y traduccion de una fila (maestro, cajas y estado)"
```

---

### Task 2: Servidor — comparación de una exportación con lo anterior

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/FilaItems.java`, `ClaveMovil.java`, `MovilNsys.java`, `TipoEvento.java`, `EventoNsys.java`, `ResultadoComparacion.java`, `Comparador.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/ComparadorTest.java`

**Interfaces:**
- Consumes: `Normalizacion`, `Traductor`, `UbicacionNsys`, `ProductoMaestro` y `EstadoMovil` (Task 1).
- Produces:
  - `record FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location, String supplier, LocalDate receivingDate, LocalDate soldDate)` con `ClaveMovil clave()`.
  - `record ClaveMovil(String imeiSn, String batch)`.
  - `record MovilNsys(String imeiSn, String batch, String goods, String grade, String status, String location, String supplier, LocalDate receivingDate, LocalDate soldDate, String sku, String modelo, String modeloMaestro, String capacidad, String color, String gradoDetalle, boolean esEsim, String ubicacion, String ubicacionUltima, EstadoMovil estado, LocalDateTime vistoPrimero, LocalDateTime cambiado)` con `ClaveMovil clave()`, `FilaItems comoFila()`, `MovilNsys conFechas(LocalDateTime, LocalDateTime)` y `boolean mismoContenido(MovilNsys)`.
  - `enum TipoEvento { ENTRADA, UBICACION, GRADO, PRODUCTO, ESTADO, VENTA, VENTA_DESHECHA, REENTRADA }`.
  - `record EventoNsys(String imeiSn, String batch, LocalDateTime fecha, boolean soloDia, TipoEvento tipo, String de, String a, String aNsys)`.
  - `record ResultadoComparacion(List<MovilNsys> aGuardar, List<EventoNsys> eventos, int desaparecidos, Set<String> cajasDesconocidas)`.
  - `Comparador.comparar(Map<ClaveMovil, MovilNsys> anteriores, List<FilaItems> filas, LocalDateTime fecha, Comparador.Modo modo, Traductor t)` → `ResultadoComparacion`.
  - `Comparador.foto(List<FilaItems>, Traductor, LocalDateTime)` → `Map<ClaveMovil, MovilNsys>`.
  - `enum Comparador.Modo { PRIMERA_CARGA, NORMAL, HISTORICO }`.

- [x] **Step 1: Test (falla)**

Crea `src/test/java/com/reparaciones/servidor/nsys/ComparadorTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de comparación de la spec pieza 1 §5.4 y §6 (datos inventados). */
class ComparadorTest {

    private static final String AZUL = "Apple iPhone 13 128GB Blue";
    private static final LocalDateTime AYER = LocalDateTime.of(2026, 10, 15, 13, 31);
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 16, 9, 1);
    private static final Traductor T = new Traductor(
            List.of(new ProductoMaestro(Normalizacion.clave(AZUL, "A"), "13", "13", "128GB", "Azul", "A",
                            "i13128AzulA", "i13128AzulA", "i13128Azul", false),
                    new ProductoMaestro(Normalizacion.clave(AZUL, "C100"), "13", "13", "128GB", "Azul", "C100",
                            "i13128AzulA100", "i13128AzulA", "i13128Azul", false)),
            List.of(new UbicacionNsys("", "En revisión", true, false),
                    new UbicacionNsys("CAJA GLASS", "Taller · Glass", true, false),
                    new UbicacionNsys("CAJA LISTOS", "Listos", true, false),
                    new UbicacionNsys("AFaltaRepuestos", "Esperando pieza", false, false),
                    new UbicacionNsys("Pedidos realizados", null, false, true),
                    new UbicacionNsys("Desguace", null, false, false)));

    private static FilaItems fila(String imei, String batch, String grade, String status, String caja) {
        return new FilaItems(imei, batch, AZUL, grade, status, caja, "Proveedor A", LocalDate.of(2026, 9, 2), null);
    }

    private static Map<ClaveMovil, MovilNsys> previo(FilaItems... filas) {
        return new HashMap<>(Comparador.foto(List.of(filas), T, AYER));
    }

    private static ResultadoComparacion normal(Map<ClaveMovil, MovilNsys> antes, FilaItems... ahora) {
        return Comparador.comparar(antes, List.of(ahora), HOY, Comparador.Modo.NORMAL, T);
    }

    @Test void primeraCargaGuardaTodoYApuntaEntradaYVentaSoloConElDia() {
        FilaItems vendido = new FilaItems("350000000000001", "BT-9001", AZUL, "A", "Released", "Pedidos realizados",
                "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 12));
        FilaItems enStock = fila("350000000000002", "BT-9001", "A", "Available", "CAJA LISTOS");
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(vendido, enStock), HOY, Comparador.Modo.PRIMERA_CARGA, T);

        assertEquals(2, r.aGuardar().size());
        MovilNsys listo = r.aGuardar().get(1);
        assertEquals("i13128AzulA", listo.sku());
        assertEquals("13", listo.modelo());
        assertEquals("Listos", listo.ubicacion());
        assertEquals(EstadoMovil.STOCK, listo.estado());
        assertEquals(HOY, listo.vistoPrimero());
        assertEquals(List.of(
                new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 9, 2, 0, 0), true, TipoEvento.ENTRADA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 9, 12, 0, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000002", "BT-9001", LocalDateTime.of(2026, 9, 2, 0, 0), true, TipoEvento.ENTRADA, null, null, null)),
                r.eventos());
    }

    @Test void primeraCargaConVariosLotesDelMismoImeiApuntaReentradaEnElPosterior() {
        FilaItems primero = new FilaItems("350000000000003", "BT-9001", AZUL, "A", "Removed", "", "Proveedor A",
                LocalDate.of(2026, 9, 2), null);
        FilaItems vuelta = new FilaItems("350000000000003", "BT-9002", AZUL, "A", "InProcess", "Funcionalidad pendiente",
                "Proveedor A", LocalDate.of(2026, 9, 29), null);
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(vuelta, primero), HOY, Comparador.Modo.PRIMERA_CARGA, T);

        assertEquals(TipoEvento.REENTRADA, r.eventos().get(0).tipo());
        assertEquals("BT-9002", r.eventos().get(0).a());
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), r.eventos().get(0).fecha());
        assertEquals(TipoEvento.ENTRADA, r.eventos().get(1).tipo());
        assertEquals("BT-9001", r.eventos().get(1).batch());
    }

    @Test void sinCambiosNoGuardaNiApunta() {
        FilaItems f = fila("350000000000004", "BT-9001", "A", "Available", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(f), f);
        assertTrue(r.aGuardar().isEmpty());
        assertTrue(r.eventos().isEmpty());
        assertEquals(0, r.desaparecidos());
    }

    @Test void cambioDeCajaApuntaLaUbicacionTraducidaYGuardaConLaHoraDelCambio() {
        FilaItems antes = fila("350000000000005", "BT-9001", "C100", "InProcess", "CAJA GLASS");
        FilaItems ahora = fila("350000000000005", "BT-9001", "C100", "Available", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(antes), ahora);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals("Listos", guardado.ubicacion());
        assertEquals(AYER, guardado.vistoPrimero());
        assertEquals(HOY, guardado.cambiado());
        assertEquals(List.of(
                new EventoNsys("350000000000005", "BT-9001", HOY, false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000005", "BT-9001", HOY, false, TipoEvento.ESTADO, "InProcess", "Available", null)),
                r.eventos());
    }

    @Test void alPasarAPedidosRealizadosConservaLaUltimaUbicacion() {
        FilaItems antes = fila("350000000000006", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems ahora = fila("350000000000006", "BT-9001", "A", "Available", "Pedidos realizados");
        ResultadoComparacion r = normal(previo(antes), ahora);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.DE_PEDIDO, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Listos", guardado.ubicacionUltima());
        assertEquals(new EventoNsys("350000000000006", "BT-9001", HOY, false, TipoEvento.UBICACION, "Listos", null, "Pedidos realizados"),
                r.eventos().get(0));
    }

    @Test void cambioDeGradoYDeProducto() {
        FilaItems antes = fila("350000000000007", "BT-9001", "C100", "Available", "CAJA LISTOS");
        FilaItems ahora = new FilaItems("350000000000007", "BT-9001", "Apple iPhone 13 128GB Pink", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null);
        ResultadoComparacion r = normal(previo(antes), ahora);

        assertEquals(List.of(TipoEvento.GRADO, TipoEvento.PRODUCTO), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("C100", r.eventos().get(0).de());
        assertEquals("A", r.eventos().get(0).a());
        assertEquals(AZUL, r.eventos().get(1).de());
        assertNull(r.aGuardar().get(0).sku(), "el maestro de la prueba no tiene el rosa");
    }

    @Test void ventaVentaDeshechaYBajaPorReentrada() {
        FilaItems vivo = fila("350000000000008", "BT-9001", "A", "Available", "Pedidos realizados");
        FilaItems vendido = fila("350000000000008", "BT-9001", "A", "Released", "Pedidos realizados");
        assertEquals(TipoEvento.VENTA, normal(previo(vivo), vendido).eventos().get(0).tipo());
        assertEquals(TipoEvento.VENTA_DESHECHA, normal(previo(vendido), vivo).eventos().get(0).tipo());
        FilaItems quitado = fila("350000000000008", "BT-9001", "A", "Removed", "Pedidos realizados");
        EventoNsys baja = normal(previo(vendido), quitado).eventos().get(0);
        assertEquals(TipoEvento.ESTADO, baja.tipo());
        assertEquals("Removed", baja.a());
    }

    @Test void filaNuevaDeUnImeiConocidoApuntaEntradaYReentrada() {
        FilaItems viejo = fila("350000000000009", "BT-9001", "A", "Removed", "");
        FilaItems nuevo = fila("350000000000009", "BT-9002", "A", "InProcess", "Funcionalidad pendiente");
        ResultadoComparacion r = normal(previo(viejo), viejo, nuevo);

        assertEquals(1, r.aGuardar().size());
        assertEquals(List.of(TipoEvento.ENTRADA, TipoEvento.REENTRADA), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals(HOY, r.eventos().get(0).fecha());
        assertFalse(r.eventos().get(0).soloDia());
        assertEquals("BT-9002", r.eventos().get(1).a());
        assertEquals(Set.of("Funcionalidad pendiente"), r.cajasDesconocidas(), "la prueba no tiene esa caja");
    }

    @Test void historicoSoloApuntaCambiosYNoGuarda() {
        FilaItems antes = fila("350000000000010", "BT-9001", "C100", "InProcess", "CAJA GLASS");
        FilaItems ahora = fila("350000000000010", "BT-9001", "C100", "InProcess", "CAJA LISTOS");
        FilaItems nuevo = fila("350000000000011", "BT-9001", "A", "InProcess", "");
        ResultadoComparacion r = Comparador.comparar(previo(antes), List.of(ahora, nuevo), HOY, Comparador.Modo.HISTORICO, T);

        assertTrue(r.aGuardar().isEmpty());
        assertEquals(1, r.eventos().size());
        assertEquals(TipoEvento.UBICACION, r.eventos().get(0).tipo());
    }

    @Test void unaCajaDesconocidaSeAvisaYElMovilQuedaDeBaja() {
        FilaItems f = fila("350000000000012", "BT-9001", "A", "Available", "CAJA NUEVA");
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.NORMAL, T);
        assertEquals(Set.of("CAJA NUEVA"), r.cajasDesconocidas());
        assertEquals(EstadoMovil.BAJA, r.aGuardar().get(0).estado());
        assertNull(r.aGuardar().get(0).ubicacion());
    }

    @Test void losQueDesaparecenSeCuentanYLasFilasRepetidasCuentanUnaVez() {
        FilaItems a = fila("350000000000013", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems b = fila("350000000000014", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems aMovido = fila("350000000000013", "BT-9001", "A", "Available", "CAJA GLASS");
        ResultadoComparacion r = normal(previo(a, b), aMovido, a);
        assertEquals(1, r.desaparecidos());
        assertEquals(1, r.eventos().size(), "la segunda fila del mismo móvil se ignora");
        assertEquals("Taller · Glass", r.aGuardar().get(0).ubicacion());
    }

    @Test void sinProductoEnElMaestroElEsimSaleDelNombre() {
        FilaItems f = new FilaItems("350000000000015", "BT-9001", "Apple iPhone 15 128GB eSIM Black", "A", "Available",
                "CAJA LISTOS", "Proveedor A", null, null);
        MovilNsys m = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.NORMAL, T).aGuardar().get(0);
        assertNull(m.sku());
        assertNull(m.modelo());
        assertTrue(m.esEsim());
    }

    @Test void primeraCargaSinFechaDeEntradaUsaLaDeLaExportacion() {
        FilaItems f = new FilaItems("350000000000016", "BT-9001", AZUL, "A", "Available", "", "Proveedor A", null, null);
        EventoNsys entrada = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.PRIMERA_CARGA, T).eventos().get(0);
        assertEquals(HOY, entrada.fecha());
        assertFalse(entrada.soloDia());
    }
}
```

- [x] **Step 2: Ejecuta el test (falla)**

Run: `mvn -q test -Dtest=ComparadorTest`
Expected: error de compilación: no existen `FilaItems`, `ClaveMovil`, `MovilNsys`, `EventoNsys`, `TipoEvento`, `ResultadoComparacion` ni `Comparador`.

- [x] **Step 3: Implementación**

Crea `FilaItems.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;

/** Una fila del fichero Items de NSYS, solo con las columnas que usa el ERP (sin costes ni precios). */
public record FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate) {

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }
}
```

Crea `ClaveMovil.java`:

```java
package com.reparaciones.servidor.nsys;

/** Un móvil en NSYS: IMEI/SN + lote. El IMEI se repite cuando vuelve a entrar; el par, nunca. */
public record ClaveMovil(String imeiSn, String batch) {}
```

Crea `MovilNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de Nsys_movil: lo que dice NSYS de un móvil y su traducción al ERP (spec pieza 1 §4.1). */
public record MovilNsys(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate,
                        String sku, String modelo, String modeloMaestro, String capacidad, String color,
                        String gradoDetalle, boolean esEsim, String ubicacion, String ubicacionUltima,
                        EstadoMovil estado, LocalDateTime vistoPrimero, LocalDateTime cambiado) {

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }

    /** Lo que NSYS dijo de este móvil, como si volviera a llegar en un fichero (para retraducir con un maestro nuevo). */
    public FilaItems comoFila() {
        return new FilaItems(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate);
    }

    public MovilNsys conFechas(LocalDateTime vistoPrimero, LocalDateTime cambiado) {
        return new MovilNsys(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate, sku,
                modelo, modeloMaestro, capacidad, color, gradoDetalle, esEsim, ubicacion, ubicacionUltima, estado,
                vistoPrimero, cambiado);
    }

    /** Igual salvo las fechas de seguimiento (VISTO_PRIMERO y CAMBIADO). */
    public boolean mismoContenido(MovilNsys otro) {
        return otro != null && conFechas(null, null).equals(otro.conFechas(null, null));
    }
}
```

Crea `TipoEvento.java`:

```java
package com.reparaciones.servidor.nsys;

/** Tipos de evento del rastro que salen de NSYS (spec pieza 1 §4.2). */
public enum TipoEvento { ENTRADA, UBICACION, GRADO, PRODUCTO, ESTADO, VENTA, VENTA_DESHECHA, REENTRADA }
```

Crea `EventoNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_evento. En UBICACION, {@code de} y {@code a} son ubicaciones del ERP (o la caja de NSYS si no se
 *  traduce) y {@code aNsys} la caja de NSYS de destino tal cual. */
public record EventoNsys(String imeiSn, String batch, LocalDateTime fecha, boolean soloDia, TipoEvento tipo,
                         String de, String a, String aNsys) {}
```

Crea `ResultadoComparacion.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.Set;

/** Lo que sale de comparar una exportación: filas nuevas o cambiadas, eventos del rastro, cuántos móviles anteriores no
 *  venían en el fichero y las cajas que no están en Nsys_ubicacion. */
public record ResultadoComparacion(List<MovilNsys> aGuardar, List<EventoNsys> eventos, int desaparecidos,
                                   Set<String> cajasDesconocidas) {}
```

Crea `Comparador.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compara una exportación de NSYS con lo que ya había y saca la foto nueva y los eventos del rastro (spec pieza 1 §5.4
 *  y §6). Es puro: no toca la base de datos. */
public final class Comparador {

    /**
     * Modo de la comparación:
     * - PRIMERA_CARGA: no hay nada previo. Sale el esqueleto, con la entrada y la venta en las fechas de NSYS (solo el día).
     * - NORMAL: cada exportación nueva contra la foto guardada.
     * - HISTORICO: exportaciones antiguas, una contra la anterior. Solo cambios; no toca la foto.
     */
    public enum Modo { PRIMERA_CARGA, NORMAL, HISTORICO }

    static final String RELEASED = "Released";

    private Comparador() {}

    public static ResultadoComparacion comparar(Map<ClaveMovil, MovilNsys> anteriores, List<FilaItems> filas,
                                                LocalDateTime fecha, Modo modo, Traductor t) {
        List<MovilNsys> aGuardar = new ArrayList<>();
        List<EventoNsys> eventos = new ArrayList<>();
        Set<String> desconocidas = new TreeSet<>();
        Set<ClaveMovil> vistas = new HashSet<>();
        Map<String, List<FilaItems>> lotesPorImei = modo == Modo.PRIMERA_CARGA ? porImei(filas) : Map.of();
        Set<String> imeisPrevios = new HashSet<>();
        if (modo == Modo.NORMAL) for (ClaveMovil k : anteriores.keySet()) imeisPrevios.add(k.imeiSn());

        for (FilaItems f : filas) {
            if (!vistas.add(f.clave())) continue;               // fila repetida en el fichero: cuenta la primera
            UbicacionNsys caja = t.caja(f.location());
            if (caja == null) desconocidas.add(Normalizacion.caja(f.location()));
            MovilNsys anterior = anteriores.get(f.clave());
            if (anterior == null) {
                if (modo == Modo.HISTORICO) continue;           // el histórico no da altas: las pone el esqueleto
                MovilNsys nuevo = construir(f, t, caja, null, fecha);
                aGuardar.add(nuevo);
                if (modo == Modo.PRIMERA_CARGA) {
                    esqueleto(f, lotesPorImei.get(f.imeiSn()), fecha, eventos);
                } else {
                    eventos.add(evento(f, fecha, false, TipoEvento.ENTRADA, null, nuevo.ubicacion(), nuevo.location()));
                    if (imeisPrevios.contains(f.imeiSn()))
                        eventos.add(evento(f, fecha, false, TipoEvento.REENTRADA, null, f.batch(), null));
                }
                continue;
            }
            MovilNsys nuevo = construir(f, t, caja, anterior, fecha);
            cambios(anterior, nuevo, fecha, eventos);
            if (modo != Modo.HISTORICO && !anterior.mismoContenido(nuevo))
                aGuardar.add(nuevo.conFechas(anterior.vistoPrimero(), fecha));
        }
        int desaparecidos = 0;
        for (ClaveMovil k : anteriores.keySet()) if (!vistas.contains(k)) desaparecidos++;
        return new ResultadoComparacion(aGuardar, eventos, desaparecidos, desconocidas);
    }

    /** La foto de una exportación sin nada previo. La usan el histórico (exportación contra exportación) y los tests. */
    public static Map<ClaveMovil, MovilNsys> foto(List<FilaItems> filas, Traductor t, LocalDateTime fecha) {
        Map<ClaveMovil, MovilNsys> foto = new HashMap<>();
        for (FilaItems f : filas) foto.putIfAbsent(f.clave(), construir(f, t, t.caja(f.location()), null, fecha));
        return foto;
    }

    static MovilNsys construir(FilaItems f, Traductor t, UbicacionNsys caja, MovilNsys anterior, LocalDateTime fecha) {
        ProductoMaestro p = t.producto(f.goods(), f.grade());
        String ubicacion = caja == null ? null : caja.ubicacion();
        String ultima = ubicacion != null ? ubicacion
                : anterior == null ? null
                : anterior.ubicacion() != null ? anterior.ubicacion() : anterior.ubicacionUltima();
        return new MovilNsys(f.imeiSn(), f.batch(), f.goods(), f.grade(), f.status(), Normalizacion.caja(f.location()),
                f.supplier(), f.receivingDate(), f.soldDate(),
                p == null ? null : p.skuWeb(), p == null ? null : p.modelo(), p == null ? null : p.modeloMaestro(),
                p == null ? null : p.capacidad(), p == null ? null : p.color(), p == null ? null : p.calidadDetalle(),
                p != null ? p.esEsim() : Normalizacion.esEsim(f.goods()),
                ubicacion, ultima, EstadoMovil.de(f.status(), caja),
                anterior == null ? fecha : anterior.vistoPrimero(), anterior == null ? fecha : anterior.cambiado());
    }

    private static void cambios(MovilNsys antes, MovilNsys ahora, LocalDateTime fecha, List<EventoNsys> eventos) {
        FilaItems f = ahora.comoFila();
        if (!Objects.equals(antes.location(), ahora.location()))
            eventos.add(evento(f, fecha, false, TipoEvento.UBICACION,
                    antes.ubicacion() != null ? antes.ubicacion() : antes.location(), ahora.ubicacion(), ahora.location()));
        if (!Objects.equals(antes.grade(), ahora.grade()))
            eventos.add(evento(f, fecha, false, TipoEvento.GRADO, antes.grade(), ahora.grade(), null));
        if (!Objects.equals(antes.goods(), ahora.goods()))
            eventos.add(evento(f, fecha, false, TipoEvento.PRODUCTO, antes.goods(), ahora.goods(), null));
        if (!Objects.equals(antes.status(), ahora.status())) {
            TipoEvento tipo = RELEASED.equals(ahora.status()) ? TipoEvento.VENTA
                    : RELEASED.equals(antes.status()) && EstadoMovil.VIVOS.contains(ahora.status()) ? TipoEvento.VENTA_DESHECHA
                    : TipoEvento.ESTADO;
            eventos.add(evento(f, fecha, false, tipo, antes.status(), ahora.status(), null));
        }
    }

    /** Primera carga: la entrada (o reentrada, si el IMEI tiene un lote anterior) y la venta, con las fechas de NSYS. */
    private static void esqueleto(FilaItems f, List<FilaItems> lotes, LocalDateTime fecha, List<EventoNsys> eventos) {
        boolean primera = lotes == null || lotes.stream().noneMatch(o -> entroAntes(o, f));
        LocalDateTime entrada = f.receivingDate() != null ? f.receivingDate().atStartOfDay() : fecha;
        boolean soloDia = f.receivingDate() != null;
        eventos.add(primera ? evento(f, entrada, soloDia, TipoEvento.ENTRADA, null, null, null)
                            : evento(f, entrada, soloDia, TipoEvento.REENTRADA, null, f.batch(), null));
        if (f.soldDate() != null)
            eventos.add(evento(f, f.soldDate().atStartOfDay(), true, TipoEvento.VENTA, null, null, null));
    }

    /** ¿Entró {@code o} antes que {@code f}? Por fecha de recepción y, si empatan o falta alguna, por lote. */
    private static boolean entroAntes(FilaItems o, FilaItems f) {
        if (o.batch().equals(f.batch())) return false;
        LocalDate a = o.receivingDate();
        LocalDate b = f.receivingDate();
        if (a != null && b != null && !a.equals(b)) return a.isBefore(b);
        return o.batch().compareTo(f.batch()) < 0;
    }

    private static Map<String, List<FilaItems>> porImei(List<FilaItems> filas) {
        Map<String, List<FilaItems>> mapa = new HashMap<>();
        for (FilaItems f : filas) mapa.computeIfAbsent(f.imeiSn(), k -> new ArrayList<>()).add(f);
        return mapa;
    }

    private static EventoNsys evento(FilaItems f, LocalDateTime fecha, boolean soloDia, TipoEvento tipo,
                                     String de, String a, String aNsys) {
        return new EventoNsys(f.imeiSn(), f.batch(), fecha, soloDia, tipo, de, a, aNsys);
    }
}
```

- [x] **Step 4: Ejecuta el test (pasa)**

Run: `mvn -q test -Dtest=ComparadorTest`
Expected: `BUILD SUCCESS`, 13 tests en verde.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys/ComparadorTest.java
git commit -m "feat: comparacion de exportaciones de NSYS (foto y eventos del rastro)"
```

---

### Task 3: Servidor — lectura en streaming del fichero Items y del maestro

**Files:**
- Modify: `gestion-reparaciones-servidor/pom.xml` (dependencia `poi-ooxml`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/FormatoItemsException.java`, `HojaXlsx.java`, `LectorItems.java`, `LectorMaestro.java`, `ResultadoMaestro.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/XlsxDePrueba.java`, `LectorItemsTest.java`, `LectorMaestroTest.java`

**Interfaces:**
- Consumes: `FilaItems`, `ProductoMaestro` y `Normalizacion` (Tasks 1 y 2).
- Produces:
  - `class FormatoItemsException extends RuntimeException` (`FormatoItemsException(String)`).
  - `LectorItems.leer(InputStream)` → `List<FilaItems>` (`throws IOException`), `LectorItems.CABECERAS` y `LectorItems.fecha(String)` → `LocalDate`.
  - `LectorMaestro.leer(InputStream)` → `ResultadoMaestro` (`throws IOException`) y `LectorMaestro.HOJA` = `"Maestro producto NSYS"`.
  - `record ResultadoMaestro(List<ProductoMaestro> productos, List<String> conflictos, Set<String> modelosFueraDeCatalogo)`.
  - En tests, `XlsxDePrueba.libro(Map<String, List<List<Object>>>)`, `XlsxDePrueba.items(List<List<Object>>)` y `XlsxDePrueba.fila(String imei, String goods, String grade, String status, String batch, String location, String recibido, String vendido)`.

- [x] **Step 1: Dependencia**

En `pom.xml`, justo antes de `<!-- Testing -->`, añade:

```xml
        <!-- Lectura en streaming de los .xlsx de NSYS (0.9.17, espejo de NSYS): la misma versión que usaba el JavaFX -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>

```

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`; se descarga POI.

- [x] **Step 2: Tests (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/XlsxDePrueba.java`:

```java
package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Libros .xlsx de datos inventados, hechos en memoria para los tests de los lectores. */
final class XlsxDePrueba {

    private XlsxDePrueba() {}

    /** Un libro con estas hojas, en orden; números como celdas numéricas y null como celda vacía. */
    static byte[] libro(Map<String, List<List<Object>>> hojas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (Map.Entry<String, List<List<Object>>> hoja : hojas.entrySet()) {
                Sheet s = libro.createSheet(hoja.getKey());
                int n = 0;
                for (List<Object> fila : hoja.getValue()) {
                    Row r = s.createRow(n++);
                    for (int c = 0; c < fila.size(); c++) {
                        Object v = fila.get(c);
                        if (v instanceof Number numero) r.createCell(c).setCellValue(numero.doubleValue());
                        else if (v != null) r.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Un fichero Items con la cabecera de NSYS y estas filas. */
    static byte[] items(List<List<Object>> filas) throws IOException {
        List<List<Object>> todo = new ArrayList<>();
        todo.add(new ArrayList<Object>(LectorItems.CABECERAS));
        todo.addAll(filas);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", todo);
        return libro(hojas);
    }

    /** Una fila de Items en el orden de LectorItems.CABECERAS; lleva un coste para comprobar que no se lee. */
    static List<Object> fila(String imei, String goods, String grade, String status, String batch, String location,
                             String recibido, String vendido) {
        Object[] v = new Object[LectorItems.CABECERAS.size()];
        v[0] = imei;
        v[1] = goods;
        v[3] = grade;
        v[4] = status;
        v[6] = "Proveedor A";
        v[7] = batch;
        v[11] = 123.45;
        v[15] = recibido;
        v[16] = vendido;
        v[18] = location;
        return Arrays.asList(v);
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/LectorItemsTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LectorItemsTest {

    private static List<FilaItems> leer(byte[] xlsx) throws Exception {
        return LectorItems.leer(new ByteArrayInputStream(xlsx));
    }

    @Test void leeLasFilasConSusFechasAmericanasYSinCostes() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000002", "Apple iPhone 13 128GB Blue", "A-100", "Released", "BT-9001", "Pedidos realizados", "9/2/2026", "10/15/2026")));
        assertEquals(List.of(
                new FilaItems("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                        "Proveedor A", LocalDate.of(2026, 9, 2), null),
                new FilaItems("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "A-100", "Released", "Pedidos realizados",
                        "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 15))),
                leer(xlsx));
    }

    @Test void cabecerasConEspacioFinalYEnOtroOrdenValen() throws Exception {
        List<Object> cabecera = new ArrayList<>(LectorItems.CABECERAS);
        cabecera.set(0, "IMEI/SN ");
        cabecera.set(11, "Total cost ");
        java.util.Collections.swap(cabecera, 0, 18);
        List<Object> fila = new ArrayList<>(XlsxDePrueba.fila("350000000000003", "Apple iPhone 13 128GB Blue", "A",
                "InProcess", "BT-9001", null, "9/2/2026", null));
        java.util.Collections.swap(fila, 0, 18);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", List.of(cabecera, fila));
        FilaItems f = leer(XlsxDePrueba.libro(hojas)).get(0);
        assertEquals("350000000000003", f.imeiSn());
        assertNull(f.location(), "sin caja");
    }

    @Test void siFaltaUnaColumnaSeRechazaDiciendoCual() {
        List<Object> cabecera = new ArrayList<>(LectorItems.CABECERAS);
        cabecera.set(18, "Ubicacion");
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", List.of(cabecera));
        FormatoItemsException e = assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(hojas)));
        assertEquals("Faltan columnas en el fichero de NSYS: Location", e.getMessage());
    }

    @Test void unFicheroQueNoEsXlsxOVacioSeRechaza() throws Exception {
        assertThrows(FormatoItemsException.class, () -> leer("no es un xlsx".getBytes(StandardCharsets.UTF_8)));
        Map<String, List<List<Object>>> vacio = new LinkedHashMap<>();
        vacio.put("Items", List.of());
        assertEquals("El fichero de NSYS está vacío",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(vacio))).getMessage());
    }

    @Test void lasFilasSinImeiSeIgnoranYUnaFechaRaraQuedaVacia() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila(null, "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000004", "Apple iPhone 13 128GB Blue", "A", "Available", null, "", "ayer", null)));
        List<FilaItems> filas = leer(xlsx);
        assertEquals(1, filas.size());
        assertNull(filas.get(0).receivingDate());
        assertEquals("", filas.get(0).batch(), "sin lote, la clave sigue siendo válida");
    }

    @Test void formatosDeFecha() {
        assertEquals(LocalDate.of(2026, 10, 5), LectorItems.fecha("10/5/2026"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("10/15/26"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("2026-10-15"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("10/15/2026 13:31"));
        assertNull(LectorItems.fecha("x"));
        assertNull(LectorItems.fecha(null));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/LectorMaestroTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LectorMaestroTest {

    private static final List<Object> CABECERA = List.of("Product Name Calidad", "Product Name", "Familia", "Modelo",
            "Capacidad", "Color", "Calidad Detalle", "SKU (stock web)", "SKU (Amazon)", "SKU sin grado");

    private static List<Object> fila(String nombre, String familia, Object modelo, String color, String detalle,
                                     String web, String amazon) {
        return Arrays.asList(nombre, "x", familia, modelo, "128GB", color, detalle, web, amazon, "i13128Azul");
    }

    private static byte[] maestro(List<List<Object>> filas) throws Exception {
        List<List<Object>> todo = new ArrayList<>();
        todo.add(CABECERA);
        todo.addAll(filas);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("TD1", List.of(List.of("tabla dinámica")));
        hojas.put(LectorMaestro.HOJA, todo);
        return XlsxDePrueba.libro(hojas);
    }

    private static ResultadoMaestro leer(byte[] xlsx) throws Exception {
        return LectorMaestro.leer(new ByteArrayInputStream(xlsx));
    }

    @Test void leeSuHojaConModelosYSku() throws Exception {
        ResultadoMaestro r = leer(maestro(List.of(
                fila("Apple iPhone 13 128GB Blue A", "iPhone", 13, "Azul", "A", "i13128AzulA", "i13128AzulA"),
                fila("Apple iPhone 17e 256GB eSIM Black A", "iPhone", "17 e eSIM", "Negro", "A", "i17e256NegroA", null),
                fila("Apple iPad 11 11in 128GB Blue n/a", "iPad", "11", null, "n/a", null, null))));

        assertEquals(new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13", "13", "128GB", "Azul",
                "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false), r.productos().get(0));
        ProductoMaestro diecisiete = r.productos().get(1);
        assertNull(diecisiete.modelo(), "17e no está en el catálogo del ERP");
        assertEquals("17 e eSIM", diecisiete.modeloMaestro());
        assertTrue(diecisiete.esEsim());
        assertNull(r.productos().get(2).modelo(), "un iPad nunca es un iPhone 11");
        assertEquals(Set.of("17 e eSIM"), r.modelosFueraDeCatalogo());
        assertTrue(r.conflictos().isEmpty());
    }

    @Test void unaClaveRepetidaConOtroSkuSeQuedaSinSku() throws Exception {
        ResultadoMaestro r = leer(maestro(List.of(
                fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", "Azul", "A", "i13128AzulA", "i13128AzulA"),
                fila("Apple iPhone 13 128GB  Blue A", "iPhone", "13", "Azul", "A", "i13128AzulB", "i13128AzulB"))));
        assertEquals(1, r.productos().size());
        assertNull(r.productos().get(0).skuWeb());
        assertEquals(List.of("Apple iPhone 13 128GB  Blue A"), r.conflictos());
    }

    @Test void sinSuHojaOSinUnaColumnaSeRechaza() throws Exception {
        Map<String, List<List<Object>>> otra = new LinkedHashMap<>();
        otra.put("Check SKUs", List.of(CABECERA));
        assertEquals("Falta la hoja «Maestro producto NSYS»",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(otra))).getMessage());

        List<Object> sinModelo = new ArrayList<>(CABECERA);
        sinModelo.set(3, "Modelo texto");
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, List.of(sinModelo));
        assertEquals("Faltan columnas en el maestro: Modelo",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(hojas))).getMessage());
    }
}
```

- [x] **Step 3: Ejecuta los tests (fallan)**

Run: `mvn -q test -Dtest='LectorItemsTest,LectorMaestroTest'`
Expected: error de compilación: no existen `LectorItems`, `LectorMaestro`, `ResultadoMaestro` ni `FormatoItemsException`.

- [x] **Step 4: Implementación**

Crea `FormatoItemsException.java`:

```java
package com.reparaciones.servidor.nsys;

/** El fichero no tiene la forma esperada (no es .xlsx, falta la hoja o faltan columnas): se rechaza sin aplicar nada y
 *  se conservan los datos anteriores (spec pieza 1 §5.1). */
public class FormatoItemsException extends RuntimeException {

    public FormatoItemsException(String mensaje) {
        super(mensaje);
    }
}
```

Crea `HojaXlsx.java`:

```java
package com.reparaciones.servidor.nsys;

import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Recorre una hoja de un .xlsx fila a fila (SAX), sin cargar el libro entero en memoria. */
final class HojaXlsx {

    /** Recibe cada fila como lista de textos: null en las celdas vacías; los números y fechas, tal como se ven. */
    interface Filas {
        void fila(List<String> celdas);
    }

    private HojaXlsx() {}

    /** Recorre la hoja {@code nombreHoja}, o la primera si es null. */
    static void leer(InputStream xlsx, String nombreHoja, Filas destino) throws IOException {
        try (OPCPackage paquete = OPCPackage.open(xlsx)) {
            XSSFReader lector = new XSSFReader(paquete);
            ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(paquete);
            StylesTable estilos = lector.getStylesTable();
            XSSFReader.SheetIterator hojas = (XSSFReader.SheetIterator) lector.getSheetsData();
            while (hojas.hasNext()) {
                try (InputStream hoja = hojas.next()) {
                    if (nombreHoja != null && !nombreHoja.equals(hojas.getSheetName())) continue;
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(estilos, textos, new Recolector(destino),
                            new DataFormatter(Locale.ROOT), false));
                    xml.parse(new InputSource(hoja));
                    return;
                }
            }
            throw new FormatoItemsException(nombreHoja == null ? "El fichero no tiene hojas" : "Falta la hoja «" + nombreHoja + "»");
        } catch (OpenXML4JException | SAXException | ParserConfigurationException | UnsupportedFileFormatException e) {
            throw new FormatoItemsException("No es un .xlsx válido: " + e.getMessage());
        }
    }

    /** Índice de cada columna por su nombre (sin espacios a los lados); si falta alguna de {@code necesarias}, se rechaza. */
    static Map<String, Integer> columnas(List<String> cabecera, List<String> necesarias, String deQue) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = cabecera.get(i);
            if (nombre != null) indices.putIfAbsent(nombre.strip(), i);
        }
        List<String> faltan = necesarias.stream().filter(c -> !indices.containsKey(c)).toList();
        if (!faltan.isEmpty()) throw new FormatoItemsException("Faltan columnas en " + deQue + ": " + String.join(", ", faltan));
        return indices;
    }

    static String valor(List<String> celdas, Integer indice) {
        return indice == null || indice >= celdas.size() ? null : celdas.get(indice);
    }

    private static final class Recolector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Filas destino;
        private final List<String> fila = new ArrayList<>();
        private int siguiente;

        Recolector(Filas destino) {
            this.destino = destino;
        }

        @Override public void startRow(int numero) {
            fila.clear();
            siguiente = 0;
        }

        @Override public void endRow(int numero) {
            destino.fila(new ArrayList<>(fila));
        }

        @Override public void cell(String referencia, String valor, XSSFComment comentario) {
            int columna = referencia == null ? siguiente : new CellReference(referencia).getCol();
            while (fila.size() < columna) fila.add(null);
            fila.add(valor == null || valor.isBlank() ? null : valor.strip());
            siguiente = columna + 1;
        }

        @Override public void headerFooter(String texto, boolean esCabecera, String etiqueta) {}
    }
}
```

Crea `LectorItems.java`:

```java
package com.reparaciones.servidor.nsys;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lee el fichero Items de NSYS (spec pieza 1 §5.1): valida las 19 columnas por su nombre y se queda con las que usa el
 *  ERP. Los costes y precios no se leen. */
public final class LectorItems {

    static final List<String> CABECERAS = List.of("IMEI/SN", "Goods", "SKU", "Grade", "Status", "Warehouse", "Supplier",
            "Batch", "Release", "Reserve", "Diagnostics", "Total cost", "Selling price", "Sold price", "Margin",
            "Receiving date", "Sold date", "Tags", "Location");

    private static final List<DateTimeFormatter> FORMATOS = List.of(DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"), DateTimeFormatter.ISO_LOCAL_DATE);

    private LectorItems() {}

    public static List<FilaItems> leer(InputStream xlsx) throws IOException {
        Recolector r = new Recolector();
        HojaXlsx.leer(xlsx, null, r);
        if (r.columnas == null) throw new FormatoItemsException("El fichero de NSYS está vacío");
        return r.filas;
    }

    /** Las fechas de NSYS van en texto americano ("10/15/2026"); si traen hora, solo cuenta el día. null si no se entiende. */
    static LocalDate fecha(String texto) {
        if (texto == null) return null;
        String t = texto.strip();
        int corte = t.indexOf(' ');
        if (corte > 0) t = t.substring(0, corte);
        corte = t.indexOf('T');
        if (corte > 0) t = t.substring(0, corte);
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(t, formato);
            } catch (DateTimeParseException otroFormato) {
                // se prueba el siguiente
            }
        }
        return null;
    }

    private static final class Recolector implements HojaXlsx.Filas {
        Map<String, Integer> columnas;
        final List<FilaItems> filas = new ArrayList<>();

        @Override public void fila(List<String> celdas) {
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, CABECERAS, "el fichero de NSYS");
                return;
            }
            String imei = valor(celdas, "IMEI/SN");
            if (imei == null) return;                       // filas vacías o de pie
            String lote = valor(celdas, "Batch");
            filas.add(new FilaItems(imei, lote == null ? "" : lote, valor(celdas, "Goods"), valor(celdas, "Grade"),
                    valor(celdas, "Status"), valor(celdas, "Location"), valor(celdas, "Supplier"),
                    fecha(valor(celdas, "Receiving date")), fecha(valor(celdas, "Sold date"))));
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }
    }
}
```

Crea `ResultadoMaestro.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.Set;

/** Lo leído del maestro: los productos, los nombres que salieron repetidos con SKU distintos (se quedan sin SKU) y los
 *  textos de «Modelo» que no son un modelo del catálogo del ERP. */
public record ResultadoMaestro(List<ProductoMaestro> productos, List<String> conflictos, Set<String> modelosFueraDeCatalogo) {}
```

Crea `LectorMaestro.java`:

```java
package com.reparaciones.servidor.nsys;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Lee la hoja «Maestro producto NSYS» del maestro de productos (spec pieza 1 §4.4). Solo los iPhone tienen código de
 *  modelo del ERP: un iPad 11 nunca es un iPhone 11. */
public final class LectorMaestro {

    public static final String HOJA = "Maestro producto NSYS";
    static final List<String> COLUMNAS = List.of("Product Name Calidad", "Familia", "Modelo", "Capacidad", "Color",
            "Calidad Detalle", "SKU (stock web)", "SKU (Amazon)", "SKU sin grado");

    private LectorMaestro() {}

    public static ResultadoMaestro leer(InputStream xlsx) throws IOException {
        Recolector r = new Recolector();
        HojaXlsx.leer(xlsx, HOJA, r);
        if (r.columnas == null) throw new FormatoItemsException("La hoja «" + HOJA + "» está vacía");
        return new ResultadoMaestro(new ArrayList<>(r.productos.values()), r.conflictos, r.fuera);
    }

    private static final class Recolector implements HojaXlsx.Filas {
        Map<String, Integer> columnas;
        final Map<String, ProductoMaestro> productos = new LinkedHashMap<>();
        final List<String> conflictos = new ArrayList<>();
        final Set<String> fuera = new TreeSet<>();

        @Override public void fila(List<String> celdas) {
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, COLUMNAS, "el maestro");
                return;
            }
            String nombre = valor(celdas, "Product Name Calidad");
            if (nombre == null) return;
            String modeloMaestro = valor(celdas, "Modelo");
            String modelo = "iPhone".equals(valor(celdas, "Familia")) ? Normalizacion.modeloErp(modeloMaestro) : null;
            if (modelo == null && modeloMaestro != null && "iPhone".equals(valor(celdas, "Familia"))) fuera.add(modeloMaestro);
            ProductoMaestro p = new ProductoMaestro(Normalizacion.clave(nombre), modelo, modeloMaestro,
                    valor(celdas, "Capacidad"), valor(celdas, "Color"), valor(celdas, "Calidad Detalle"),
                    valor(celdas, "SKU (stock web)"), valor(celdas, "SKU (Amazon)"), valor(celdas, "SKU sin grado"),
                    Normalizacion.esEsim(nombre));
            ProductoMaestro previo = productos.get(p.clave());
            if (previo == null) {
                productos.put(p.clave(), p);
            } else if (!Objects.equals(previo.skuWeb(), p.skuWeb())) {
                conflictos.add(nombre);
                productos.put(p.clave(), new ProductoMaestro(previo.clave(), previo.modelo(), previo.modeloMaestro(),
                        previo.capacidad(), previo.color(), previo.calidadDetalle(), null, null, null, previo.esEsim()));
            }
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }
    }
}
```

> El test `leeSuHojaConModelosYSku` escribe el modelo 13 como número. `DataFormatter` lo devuelve como `"13"`, igual que
> en el maestro real.

- [x] **Step 5: Ejecuta los tests (pasan)**

Run: `mvn -q test -Dtest='LectorItemsTest,LectorMaestroTest'`
Expected: `BUILD SUCCESS`, 9 tests en verde.

- [x] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys
git commit -m "feat: lectura en streaming del fichero Items y del maestro de NSYS"
```

---

### Task 4: Servidor — acceso a las tablas del espejo

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Importacion.java`, `TrabajoTaller.java`, `NsysDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java`

**Interfaces:**
- Consumes: `MovilNsys`, `EventoNsys`, `ResultadoComparacion`, `ProductoMaestro`, `UbicacionNsys`, `ClaveMovil` y `EstadoMovil` (Tasks 1-2).
- Produces (`NsysDAO`, `@Repository`):
  - `Map<ClaveMovil, MovilNsys> cargarMoviles()`, `List<UbicacionNsys> cargarUbicaciones()`, `List<ProductoMaestro> cargarProductos()`.
  - `@Transactional void reemplazarProductos(List<ProductoMaestro>)`.
  - `@Transactional void aplicar(ResultadoComparacion, Integer idImportacion)` y `@Transactional void insertarEventos(List<EventoNsys>, Integer idImportacion)`.
  - `int empezarImportacion(String fichero, LocalDateTime fechaExportacion, String origen, LocalDateTime ahora)` y `void terminarImportacion(int id, String resultado, int filas, int cambios, String mensaje, LocalDateTime ahora)`.
  - `Optional<Importacion> ultimaImportacion(String origen, Set<String> resultados)`, `Optional<Importacion> primeraImportacion(String origen, Set<String> resultados)` y `Set<String> ficherosProcesados(String origen)`.
  - `int rellenarModelosTelefono()` y `String modeloDe(String imei)`.
  - `List<MovilNsys> enEmpresa()`, `List<MovilNsys> buscar(String texto, int limite)`, `List<MovilNsys> porImei(String imeiSn)`, `List<EventoNsys> eventos(String imeiSn)`, `List<TrabajoTaller> trabajos(String imei)` (horas ya en Madrid) y `static LocalDateTime utcAMadrid(Timestamp)`.
  - `record Importacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas, String mensaje)`.
  - `record TrabajoTaller(String idRep, LocalDateTime asignado, LocalDateTime terminado, String tecnico)`.

- [x] **Step 1: Test (falla)**

Crea `src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NsysDAOTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 16, 9, 1);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NsysDAO dao = new NsysDAO(jdbc);

    private static MovilNsys movil(int n) {
        return new MovilNsys(String.format("3500000000%05d", n), "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null, "i13128AzulA", "13", "13", "128GB",
                "Azul", "A", false, "Listos", "Listos", EstadoMovil.STOCK, HOY, HOY);
    }

    @SuppressWarnings("unchecked")
    @Test void aplicarGuardaPorLotesDeMilYDespuesLosEventos() {
        List<MovilNsys> moviles = new ArrayList<>();
        for (int i = 0; i < 2500; i++) moviles.add(movil(i));
        EventoNsys e = new EventoNsys("350000000000001", "BT-9001", HOY, false, TipoEvento.UBICACION, "Taller · Glass",
                "Listos", "CAJA LISTOS");
        dao.aplicar(new ResultadoComparacion(moviles, List.of(e), 0, Set.of()), 7);

        ArgumentCaptor<List<Object[]>> lotes = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(3)).batchUpdate(eq(NsysDAO.SQL_GUARDAR_MOVIL), lotes.capture());
        assertEquals(List.of(1000, 1000, 500), lotes.getAllValues().stream().map(List::size).toList());
        ArgumentCaptor<List<Object[]>> eventos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_EVENTO), eventos.capture());
        assertArrayEquals(new Object[]{"350000000000001", "BT-9001", Timestamp.valueOf(HOY), false, "UBICACION",
                "Taller · Glass", "Listos", "CAJA LISTOS", 7}, eventos.getValue().get(0));
    }

    @Test void losArgumentosDeUnMovilVanEnElOrdenDeLasColumnas() {
        assertArrayEquals(new Object[]{"350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", java.sql.Date.valueOf(LocalDate.of(2026, 9, 2)), null, "i13128AzulA", "13",
                "13", "128GB", "Azul", "A", false, "Listos", "Listos", "STOCK", Timestamp.valueOf(HOY),
                Timestamp.valueOf(HOY)}, NsysDAO.argumentos(movil(1)));
    }

    @SuppressWarnings("unchecked")
    @Test void reemplazarProductosBorraYCargaTodo() {
        dao.reemplazarProductos(List.of(new ProductoMaestro("clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true)));
        verify(jdbc).update("DELETE FROM Nsys_producto");
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_PRODUCTO), lote.capture());
        assertArrayEquals(new Object[]{"clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true}, lote.getValue().get(0));
    }

    @Test void empezarImportacionDevuelveElIdGenerado() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 42));
            return 1;
        });
        assertEquals(42, dao.empezarImportacion("Items_2026.10.16_09.01.xlsx", HOY, "ACTUAL", HOY.plusMinutes(3)));
    }

    @Test void terminarImportacionRecortaElMensaje() {
        dao.terminarImportacion(42, "ERROR", 0, 0, "x".repeat(600), HOY);
        verify(jdbc).update(NsysDAO.SQL_TERMINAR, "ERROR", 0, 0, "x".repeat(500), Timestamp.valueOf(HOY), 42);
    }

    @SuppressWarnings("unchecked")
    @Test void ultimaImportacionFiltraPorOrigenYResultados() {
        when(jdbc.query(contains("ORDER BY FECHA_EXPORTACION DESC"), any(RowMapper.class), eq("ACTUAL"), eq("OK"), eq("RECHAZADO")))
                .thenReturn(List.of(new Importacion(3, "Items_2026.10.16_09.01.xlsx", HOY, "OK", 70000, null)));
        assertEquals(Optional.of(new Importacion(3, "Items_2026.10.16_09.01.xlsx", HOY, "OK", 70000, null)),
                dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "OK")));
        assertEquals(Optional.empty(), dao.ultimaImportacion("MAESTRO", Set.of("OK")));
    }

    @Test void rellenarModelosDelTallerConElEspejo() {
        when(jdbc.update(NsysDAO.SQL_RELLENAR_MODELOS)).thenReturn(4);
        assertEquals(4, dao.rellenarModelosTelefono());
    }

    @SuppressWarnings("unchecked")
    @Test void modeloDeUnImeiQueNoEstaEsNull() {
        when(jdbc.query(contains("FROM Nsys_movil WHERE IMEI_SN = ? AND MODELO IS NOT NULL"), any(RowMapper.class), eq("350000000000001")))
                .thenReturn(List.of());
        assertNull(dao.modeloDe("350000000000001"));
    }

    @SuppressWarnings("unchecked")
    @Test void buscarLimpiaElTextoYSinTextoNoConsulta() {
        dao.buscar(" 35%_00 ", 10);
        verify(jdbc).query(contains("WHERE IMEI_SN LIKE ?"), any(RowMapper.class), eq("%3500%"), eq(10));
        clearInvocations(jdbc);
        assertTrue(dao.buscar("%%", 10).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test void lasHorasDelTallerPasanDeUtcAMadrid() {
        // La BD del taller guarda UTC; el espejo y su API van en hora de Madrid (verano +2, invierno +1).
        assertEquals(LocalDateTime.of(2026, 9, 12, 13, 1), NsysDAO.utcAMadrid(Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 11, 1))));
        assertEquals(LocalDateTime.of(2026, 1, 12, 12, 1), NsysDAO.utcAMadrid(Timestamp.valueOf(LocalDateTime.of(2026, 1, 12, 11, 1))));
        assertNull(NsysDAO.utcAMadrid(null));
    }
}
```

- [x] **Step 2: Ejecuta el test (falla)**

Run: `mvn -q test -Dtest=NsysDAOTest`
Expected: error de compilación: no existen `NsysDAO` ni `Importacion`.

- [x] **Step 3: Implementación**

Crea `Importacion.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_importacion. */
public record Importacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas, String mensaje) {}
```

Crea `TrabajoTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Un trabajo del taller (fila de Reparacion) de un IMEI, para el rastro. */
public record TrabajoTaller(String idRep, LocalDateTime asignado, LocalDateTime terminado, String tecnico) {}
```

Crea `NsysDAO.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Acceso a las tablas del espejo de NSYS (spec pieza 1 §4). Las horas del espejo son de Madrid; las del taller
 *  (`Reparacion`) están en UTC, como el resto de la BD, y se pasan a Madrid al leerlas. */
@Repository
public class NsysDAO {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final int LOTE = 1000;
    static final String COLUMNAS_MOVIL = "IMEI_SN, BATCH, GOODS, GRADE, STATUS, LOCATION, SUPPLIER, RECEIVING_DATE,"
            + " SOLD_DATE, SKU, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, GRADO_DETALLE, ES_ESIM, UBICACION,"
            + " UBICACION_ULTIMA, ESTADO, VISTO_PRIMERO, CAMBIADO";
    static final String SQL_GUARDAR_MOVIL = "INSERT INTO Nsys_movil (" + COLUMNAS_MOVIL + ") VALUES"
            + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE GOODS = VALUES(GOODS), GRADE = VALUES(GRADE), STATUS = VALUES(STATUS),"
            + " LOCATION = VALUES(LOCATION), SUPPLIER = VALUES(SUPPLIER), RECEIVING_DATE = VALUES(RECEIVING_DATE),"
            + " SOLD_DATE = VALUES(SOLD_DATE), SKU = VALUES(SKU), MODELO = VALUES(MODELO),"
            + " MODELO_MAESTRO = VALUES(MODELO_MAESTRO), CAPACIDAD = VALUES(CAPACIDAD), COLOR = VALUES(COLOR),"
            + " GRADO_DETALLE = VALUES(GRADO_DETALLE), ES_ESIM = VALUES(ES_ESIM), UBICACION = VALUES(UBICACION),"
            + " UBICACION_ULTIMA = VALUES(UBICACION_ULTIMA), ESTADO = VALUES(ESTADO), CAMBIADO = VALUES(CAMBIADO)";
    static final String SQL_EVENTO = "INSERT INTO Nsys_evento (IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS,"
            + " ID_IMPORTACION) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_PRODUCTO = "INSERT INTO Nsys_producto (CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR,"
            + " CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON, SKU_SIN_GRADO, ES_ESIM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_EMPEZAR = "INSERT INTO Nsys_importacion (FICHERO, FECHA_EXPORTACION, ORIGEN, EMPEZADA)"
            + " VALUES (?, ?, ?, ?)";
    static final String SQL_TERMINAR = "UPDATE Nsys_importacion SET RESULTADO = ?, FILAS = ?, CAMBIOS = ?, MENSAJE = ?,"
            + " TERMINADA = ? WHERE ID_IMPORTACION = ?";
    /** Rellena el modelo de los teléfonos del taller que no lo tienen, sin pisar nunca uno puesto (spec §7). */
    static final String SQL_RELLENAR_MODELOS = "UPDATE Telefono t JOIN (SELECT IMEI_SN, MAX(MODELO) AS MODELO"
            + " FROM Nsys_movil WHERE MODELO IS NOT NULL GROUP BY IMEI_SN) n ON n.IMEI_SN = t.IMEI"
            + " SET t.MODELO = n.MODELO WHERE t.MODELO IS NULL OR t.MODELO = ''";
    private static final String COLUMNAS_IMPORTACION = "ID_IMPORTACION, FICHERO, FECHA_EXPORTACION, RESULTADO, FILAS, MENSAJE";

    private final JdbcTemplate jdbc;

    public NsysDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- foto, maestro y cajas

    public Map<ClaveMovil, MovilNsys> cargarMoviles() {
        Map<ClaveMovil, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil", NsysDAO::movil)) mapa.put(m.clave(), m);
        return mapa;
    }

    public List<UbicacionNsys> cargarUbicaciones() {
        return jdbc.query("SELECT LOCATION, UBICACION, CUENTA_STOCK, ES_PEDIDO FROM Nsys_ubicacion ORDER BY ORDEN",
                (rs, n) -> new UbicacionNsys(rs.getString("LOCATION"), rs.getString("UBICACION"),
                        rs.getBoolean("CUENTA_STOCK"), rs.getBoolean("ES_PEDIDO")));
    }

    public List<ProductoMaestro> cargarProductos() {
        return jdbc.query("SELECT CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON,"
                        + " SKU_SIN_GRADO, ES_ESIM FROM Nsys_producto",
                (rs, n) -> new ProductoMaestro(rs.getString("CLAVE"), rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"),
                        rs.getString("CAPACIDAD"), rs.getString("COLOR"), rs.getString("CALIDAD_DETALLE"),
                        rs.getString("SKU_WEB"), rs.getString("SKU_AMAZON"), rs.getString("SKU_SIN_GRADO"),
                        rs.getBoolean("ES_ESIM")));
    }

    @Transactional
    public void reemplazarProductos(List<ProductoMaestro> productos) {
        jdbc.update("DELETE FROM Nsys_producto");
        for (List<ProductoMaestro> lote : trozos(productos))
            jdbc.batchUpdate(SQL_PRODUCTO, lote.stream().map(p -> new Object[]{p.clave(), p.modelo(), p.modeloMaestro(),
                    p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado(), p.esEsim()}).toList());
    }

    /** Guarda las filas nuevas o cambiadas y los eventos de una comparación, todo o nada. */
    @Transactional
    public void aplicar(ResultadoComparacion r, Integer idImportacion) {
        for (List<MovilNsys> lote : trozos(r.aGuardar()))
            jdbc.batchUpdate(SQL_GUARDAR_MOVIL, lote.stream().map(NsysDAO::argumentos).toList());
        insertarEventos(r.eventos(), idImportacion);
    }

    @Transactional
    public void insertarEventos(List<EventoNsys> eventos, Integer idImportacion) {
        for (List<EventoNsys> lote : trozos(eventos))
            jdbc.batchUpdate(SQL_EVENTO, lote.stream().map(e -> new Object[]{e.imeiSn(), e.batch(), Timestamp.valueOf(e.fecha()),
                    e.soloDia(), e.tipo().name(), corta(e.de(), 150), corta(e.a(), 150), corta(e.aNsys(), 80),
                    idImportacion}).toList());
    }

    static Object[] argumentos(MovilNsys m) {
        return new Object[]{corta(m.imeiSn(), 40), corta(m.batch(), 40), corta(m.goods(), 150), corta(m.grade(), 20),
                corta(m.status(), 30), corta(m.location(), 80), corta(m.supplier(), 80), fecha(m.receivingDate()),
                fecha(m.soldDate()), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(), m.gradoDetalle(),
                m.esEsim(), m.ubicacion(), m.ubicacionUltima(), m.estado().name(), Timestamp.valueOf(m.vistoPrimero()),
                Timestamp.valueOf(m.cambiado())};
    }

    // ---------------------------------------------------------------- registro de importaciones

    public int empezarImportacion(String fichero, LocalDateTime fechaExportacion, String origen, LocalDateTime ahora) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, corta(fichero, 120));
            ps.setTimestamp(2, Timestamp.valueOf(fechaExportacion));
            ps.setString(3, origen);
            ps.setTimestamp(4, Timestamp.valueOf(ahora));
            return ps;
        }, claves);
        return claves.getKey().intValue();
    }

    public void terminarImportacion(int id, String resultado, int filas, int cambios, String mensaje, LocalDateTime ahora) {
        jdbc.update(SQL_TERMINAR, resultado, filas, cambios, corta(mensaje, 500), Timestamp.valueOf(ahora), id);
    }

    public Optional<Importacion> ultimaImportacion(String origen, Set<String> resultados) {
        return unaImportacion(origen, resultados, "DESC");
    }

    public Optional<Importacion> primeraImportacion(String origen, Set<String> resultados) {
        return unaImportacion(origen, resultados, "ASC");
    }

    /** Los ficheros de un origen ya resueltos (OK o RECHAZADO): no se vuelven a procesar. */
    public Set<String> ficherosProcesados(String origen) {
        return new HashSet<>(jdbc.query("SELECT FICHERO FROM Nsys_importacion WHERE ORIGEN = ? AND RESULTADO IN ('OK', 'RECHAZADO')",
                (rs, n) -> rs.getString("FICHERO"), origen));
    }

    private Optional<Importacion> unaImportacion(String origen, Set<String> resultados, String orden) {
        List<String> estados = new ArrayList<>(new TreeSet<>(resultados));
        List<Object> argumentos = new ArrayList<>();
        argumentos.add(origen);
        argumentos.addAll(estados);
        String sql = "SELECT " + COLUMNAS_IMPORTACION + " FROM Nsys_importacion WHERE ORIGEN = ? AND RESULTADO IN ("
                + String.join(", ", Collections.nCopies(estados.size(), "?")) + ") ORDER BY FECHA_EXPORTACION " + orden
                + ", ID_IMPORTACION " + orden + " LIMIT 1";
        List<Importacion> filas = jdbc.query(sql, (rs, n) -> new Importacion(rs.getInt("ID_IMPORTACION"),
                rs.getString("FICHERO"), rs.getTimestamp("FECHA_EXPORTACION").toLocalDateTime(), rs.getString("RESULTADO"),
                rs.getInt("FILAS"), rs.getString("MENSAJE")), argumentos.toArray());
        return filas.stream().findFirst();
    }

    // ---------------------------------------------------------------- taller (IMEIcheck fuera)

    public int rellenarModelosTelefono() {
        return jdbc.update(SQL_RELLENAR_MODELOS);
    }

    /** Código de modelo del ERP según NSYS (el lote más reciente que lo tenga), o null. */
    public String modeloDe(String imei) {
        List<String> modelos = jdbc.query("SELECT MODELO FROM Nsys_movil WHERE IMEI_SN = ? AND MODELO IS NOT NULL"
                + " ORDER BY VISTO_PRIMERO DESC LIMIT 1", (rs, n) -> rs.getString("MODELO"), imei);
        return modelos.isEmpty() ? null : modelos.get(0);
    }

    // ---------------------------------------------------------------- lecturas de la API

    /** Los móviles que están en la empresa: en stock, de pedido o que no cuentan como stock. */
    public List<MovilNsys> enEmpresa() {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE ESTADO IN ('STOCK', 'DE_PEDIDO', 'NO_CUENTA')",
                NsysDAO::movil);
    }

    /** Móviles cuyo IMEI/SN contiene {@code texto} (solo letras y cifras), de cualquier estado, los más recientes primero. */
    public List<MovilNsys> buscar(String texto, int limite) {
        String limpio = texto == null ? "" : texto.replaceAll("[^A-Za-z0-9]", "");
        if (limpio.isEmpty()) return List.of();
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN LIKE ? ORDER BY VISTO_PRIMERO DESC LIMIT ?",
                NsysDAO::movil, "%" + limpio + "%", limite);
    }

    /** Todos los lotes de un IMEI/SN, el más reciente primero. */
    public List<MovilNsys> porImei(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN = ? ORDER BY VISTO_PRIMERO DESC, BATCH DESC",
                NsysDAO::movil, imeiSn);
    }

    public List<EventoNsys> eventos(String imeiSn) {
        return jdbc.query("SELECT IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS FROM Nsys_evento WHERE IMEI_SN = ?"
                        + " ORDER BY FECHA, ID_EVENTO",
                (rs, n) -> new EventoNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"),
                        rs.getTimestamp("FECHA").toLocalDateTime(), rs.getBoolean("SOLO_DIA"),
                        TipoEvento.valueOf(rs.getString("TIPO")), rs.getString("DE"), rs.getString("A"),
                        rs.getString("A_NSYS")), imeiSn);
    }

    /** Los trabajos del taller de un IMEI (asignaciones A… y trabajos hechos R/G/P), con el nombre del técnico. Sus horas
     *  pasan de UTC a Madrid para que el rastro no mezcle zonas con los eventos de NSYS. */
    public List<TrabajoTaller> trabajos(String imei) {
        return jdbc.query("SELECT r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN, t.NOMBRE FROM Reparacion r"
                        + " JOIN Tecnico t ON t.ID_TEC = r.ID_TEC WHERE r.IMEI = ? ORDER BY r.FECHA_ASIG, r.ID_REP",
                (rs, n) -> new TrabajoTaller(rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")),
                        utcAMadrid(rs.getTimestamp("FECHA_FIN")), rs.getString("NOMBRE")), imei);
    }

    // ---------------------------------------------------------------- utilidades

    static MovilNsys movil(ResultSet rs, int n) throws SQLException {
        return new MovilNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"), rs.getString("GOODS"), rs.getString("GRADE"),
                rs.getString("STATUS"), rs.getString("LOCATION"), rs.getString("SUPPLIER"),
                dia(rs.getDate("RECEIVING_DATE")), dia(rs.getDate("SOLD_DATE")), rs.getString("SKU"),
                rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"), rs.getString("CAPACIDAD"), rs.getString("COLOR"),
                rs.getString("GRADO_DETALLE"), rs.getBoolean("ES_ESIM"), rs.getString("UBICACION"),
                rs.getString("UBICACION_ULTIMA"), EstadoMovil.valueOf(rs.getString("ESTADO")),
                fechaHora(rs.getTimestamp("VISTO_PRIMERO")), fechaHora(rs.getTimestamp("CAMBIADO")));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += LOTE) trozos.add(lista.subList(i, Math.min(lista.size(), i + LOTE)));
        return trozos;
    }

    static String corta(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static Date fecha(LocalDate dia) {
        return dia == null ? null : Date.valueOf(dia);
    }

    private static LocalDate dia(Date fecha) {
        return fecha == null ? null : fecha.toLocalDate();
    }

    private static LocalDateTime fechaHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Una hora del taller (UTC sin zona, como guarda la BD con la JVM y la sesión en UTC) en hora de Madrid. */
    static LocalDateTime utcAMadrid(Timestamp ts) {
        return ts == null ? null
                : ts.toLocalDateTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(MADRID).toLocalDateTime();
    }
}
```

> `buscar(" 35%_00 ", 10)` limpia el texto a `"3500"`: el test comprueba el `LIKE '%3500%'`.

- [x] **Step 4: Ejecuta el test (pasa)**

Run: `mvn -q test -Dtest=NsysDAOTest`
Expected: `BUILD SUCCESS`, 10 tests en verde.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: acceso a las tablas del espejo de NSYS"
```

---

### Task 5: Servidor — cliente de Drive con la cuenta de servicio

**Files:**
- Modify: `gestion-reparaciones-servidor/pom.xml` (dependencia `google-auth-library-oauth2-http`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/ClienteDrive.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/ClienteDriveTest.java`

**Interfaces:**
- Produces (`ClienteDrive`, `@Component`):
  - `record ClienteDrive.FicheroDrive(String id, String nombre, Instant modificado)`.
  - `List<FicheroDrive> listarItems(String carpeta)`, `FicheroDrive metadatos(String id)` y `byte[] descargar(String id)`, todos `throws IOException, InterruptedException`.
  - Constructor de Spring `ClienteDrive(@Value("${nsys.drive.credenciales:}") String)` y de paquete `ClienteDrive(HttpClient, ProveedorToken)`.
  - `interface ClienteDrive.ProveedorToken { String token() throws IOException; }` y `static ProveedorToken cuentaDeServicio(String ruta)`.

- [x] **Step 1: Dependencia**

En `pom.xml`, justo después del bloque de `poi-ooxml` de la Task 3, añade:

```xml
        <!-- Token de la cuenta de servicio de Google para leer de Drive (0.9.17, espejo de NSYS) -->
        <dependency>
            <groupId>com.google.auth</groupId>
            <artifactId>google-auth-library-oauth2-http</artifactId>
            <version>1.23.0</version>
        </dependency>

```

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`.

- [x] **Step 2: Test (falla)**

Crea `src/test/java/com/reparaciones/servidor/nsys/ClienteDriveTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Drive sin red: un HttpClient mockeado y un token fijo (patrón de TipoCambioDAOTest). */
class ClienteDriveTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteDrive drive = new ClienteDrive(http, () -> "tok-1");

    @SuppressWarnings("unchecked")
    private static <T> HttpResponse<T> respuesta(int estado, T cuerpo) {
        HttpResponse<T> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        return r;
    }

    private List<HttpRequest> peticiones(int n) throws Exception {
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(n)).send(c.capture(), any());
        return c.getAllValues();
    }

    @Test void listarItemsRecorreTodasLasPaginasConElToken() throws Exception {
        doReturn(respuesta(200, "{\"nextPageToken\":\"p2\",\"files\":[{\"id\":\"f1\",\"name\":\"Items_2026.10.16_09.01.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:01:30.123Z\"}]}"),
                respuesta(200, "{\"files\":[{\"id\":\"f2\",\"name\":\"Items_2026.10.16_09.31.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:31:30Z\"}]}"))
                .when(http).send(any(HttpRequest.class), any());

        List<ClienteDrive.FicheroDrive> ficheros = drive.listarItems("carpeta-1");

        assertEquals(List.of(
                new ClienteDrive.FicheroDrive("f1", "Items_2026.10.16_09.01.xlsx", Instant.parse("2026-10-16T07:01:30.123Z")),
                new ClienteDrive.FicheroDrive("f2", "Items_2026.10.16_09.31.xlsx", Instant.parse("2026-10-16T07:31:30Z"))),
                ficheros);
        List<HttpRequest> pedidas = peticiones(2);
        String primera = pedidas.get(0).uri().toString();
        assertTrue(primera.contains("q=%27carpeta-1%27%20in%20parents%20and%20name%20contains%20%27Items_%27"), primera);
        assertTrue(primera.contains("supportsAllDrives=true&includeItemsFromAllDrives=true"), primera);
        assertFalse(primera.contains("pageToken"));
        assertTrue(pedidas.get(1).uri().toString().endsWith("&pageToken=p2"));
        assertEquals("Bearer tok-1", pedidas.get(0).headers().firstValue("Authorization").orElseThrow());
    }

    @Test void metadatosYDescarga() throws Exception {
        doReturn(respuesta(200, "{\"id\":\"m1\",\"name\":\"Maestro.xlsx\",\"modifiedTime\":\"2026-10-15T10:00:00Z\"}"),
                respuesta(200, new byte[]{1, 2, 3}))
                .when(http).send(any(HttpRequest.class), any());

        assertEquals(new ClienteDrive.FicheroDrive("m1", "Maestro.xlsx", Instant.parse("2026-10-15T10:00:00Z")), drive.metadatos("m1"));
        assertArrayEquals(new byte[]{1, 2, 3}, drive.descargar("m1"));
        List<HttpRequest> pedidas = peticiones(2);
        assertTrue(pedidas.get(0).uri().toString().contains("/files/m1?fields=id%2Cname%2CmodifiedTime&supportsAllDrives=true"));
        assertTrue(pedidas.get(1).uri().toString().endsWith("/files/m1?alt=media&supportsAllDrives=true"));
    }

    @Test void unaRespuestaQueNoEs200EsUnFallo() throws Exception {
        doReturn(respuesta(404, "{}")).when(http).send(any(HttpRequest.class), any());
        IOException e = assertThrows(IOException.class, () -> drive.metadatos("m1"));
        assertEquals("Drive respondió 404 a https://www.googleapis.com/drive/v3/files/m1", e.getMessage());
    }

    @Test void sinCredencialesFallaAlPedirElTokenNoAlCrearse() {
        ClienteDrive.ProveedorToken sinClave = ClienteDrive.cuentaDeServicio("");
        IOException e = assertThrows(IOException.class, sinClave::token);
        assertEquals("Falta el ajuste nsys.drive.credenciales", e.getMessage());
        assertDoesNotThrow(() -> new ClienteDrive(""), "el contexto arranca sin el fichero de la clave");
    }
}
```

- [x] **Step 3: Ejecuta el test (falla)**

Run: `mvn -q test -Dtest=ClienteDriveTest`
Expected: error de compilación: no existe `ClienteDrive`.

- [x] **Step 4: Implementación**

Crea `ClienteDrive.java`:

```java
package com.reparaciones.servidor.nsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lee de Drive, en solo lectura, con la cuenta de servicio propia del ERP (spec pieza 1 §2 y §5.1): listar la carpeta de
 *  los Items, leer los metadatos de un fichero y descargarlo. Mismo patrón que el bot de WhatsApp de las automatizaciones. */
@Component
public class ClienteDrive {

    static final String API = "https://www.googleapis.com/drive/v3/files";
    static final String ALCANCE = "https://www.googleapis.com/auth/drive.readonly";
    private static final Duration ESPERA = Duration.ofSeconds(60);

    public record FicheroDrive(String id, String nombre, Instant modificado) {}

    /** Da el token de acceso de Google; los tests lo sustituyen por uno fijo. */
    interface ProveedorToken {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorToken tokens;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteDrive(@Value("${nsys.drive.credenciales:}") String credenciales) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                cuentaDeServicio(credenciales));
    }

    /** Paquete: para que los tests sustituyan la red y el token. */
    ClienteDrive(HttpClient http, ProveedorToken tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /** Credenciales perezosas: se leen la primera vez que hacen falta, así el contexto arranca sin el fichero (tests y
     *  entornos con la importación apagada). */
    static ProveedorToken cuentaDeServicio(String ruta) {
        return new ProveedorToken() {
            private GoogleCredentials credenciales;

            @Override public synchronized String token() throws IOException {
                if (credenciales == null) {
                    if (ruta == null || ruta.isBlank()) throw new IOException("Falta el ajuste nsys.drive.credenciales");
                    try (InputStream in = Files.newInputStream(Path.of(ruta))) {
                        credenciales = GoogleCredentials.fromStream(in).createScoped(List.of(ALCANCE));
                    }
                }
                credenciales.refreshIfExpired();
                return credenciales.getAccessToken().getTokenValue();
            }
        };
    }

    /** Los ficheros «Items_*.xlsx» de la carpeta, recorriendo todas las páginas. */
    public List<FicheroDrive> listarItems(String carpeta) throws IOException, InterruptedException {
        String consulta = "'" + carpeta + "' in parents and name contains 'Items_' and trashed = false";
        List<FicheroDrive> ficheros = new ArrayList<>();
        String pagina = null;
        do {
            String url = API + "?q=" + codificar(consulta) + "&fields=" + codificar("nextPageToken,files(id,name,modifiedTime)")
                    + "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true&corpora=allDrives"
                    + (pagina == null ? "" : "&pageToken=" + codificar(pagina));
            JsonNode cuerpo = json.readTree(pedir(url, HttpResponse.BodyHandlers.ofString()));
            for (JsonNode f : cuerpo.path("files")) ficheros.add(fichero(f));
            pagina = cuerpo.path("nextPageToken").asText(null);
        } while (pagina != null);
        return ficheros;
    }

    public FicheroDrive metadatos(String id) throws IOException, InterruptedException {
        String url = API + "/" + codificar(id) + "?fields=" + codificar("id,name,modifiedTime") + "&supportsAllDrives=true";
        return fichero(json.readTree(pedir(url, HttpResponse.BodyHandlers.ofString())));
    }

    public byte[] descargar(String id) throws IOException, InterruptedException {
        return pedir(API + "/" + codificar(id) + "?alt=media&supportsAllDrives=true", HttpResponse.BodyHandlers.ofByteArray());
    }

    private <T> T pedir(String url, HttpResponse.BodyHandler<T> cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url)).timeout(ESPERA)
                .header("Authorization", "Bearer " + tokens.token()).GET().build();
        HttpResponse<T> respuesta = http.send(peticion, cuerpo);
        if (respuesta.statusCode() != 200)
            throw new IOException("Drive respondió " + respuesta.statusCode() + " a " + url.substring(0, url.indexOf('?')));
        return respuesta.body();
    }

    private static FicheroDrive fichero(JsonNode f) {
        return new FicheroDrive(f.path("id").asText(), f.path("name").asText(), Instant.parse(f.path("modifiedTime").asText()));
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
```

- [x] **Step 5: Ejecuta el test (pasa)**

Run: `mvn -q test -Dtest=ClienteDriveTest`
Expected: `BUILD SUCCESS`, 4 tests en verde.

- [x] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/reparaciones/servidor/nsys/ClienteDrive.java src/test/java/com/reparaciones/servidor/nsys/ClienteDriveTest.java
git commit -m "feat: cliente de Drive de solo lectura con la cuenta de servicio del ERP"
```

---

### Task 6: Servidor — importación cada 5 minutos

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java`, `TareaImportacionNsys.java`
- Modify: `gestion-reparaciones-servidor/docs/deployment.md` (variables), `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (solo el comentario de la clase)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java`, `TareaImportacionNsysTest.java`

**Interfaces:**
- Consumes:
  - `ClienteDrive` (Task 5);
  - `NsysDAO` e `Importacion` (Task 4);
  - `LectorItems`, `LectorMaestro`, `ResultadoMaestro` y `FormatoItemsException` (Task 3);
  - `Comparador`, `Traductor` y `ResultadoComparacion` (Tasks 1-2).
- Produces:
  - **`ImportadorNsys`** (`@Service`):
    - `void pasada()`;
    - constantes `MADRID`, `MINIMO_FILAS`, `ACTUAL`, `HISTORICO`, `MAESTRO`, `OK`, `RECHAZADO` y `ERROR`;
    - `record FicheroItems(String id, String nombre, LocalDateTime fecha)`;
    - `static Optional<LocalDateTime> fechaDelNombre(String)`;
    - `static List<FicheroItems> items(ClienteDrive, String carpeta)`, del más antiguo al más reciente.
  - **`TareaImportacionNsys`** (`@Component`): solo existe con `nsys.importacion.activa=true`.

- [x] **Step 1: Tests (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Una pasada de importación con Drive y la base de datos mockeados; los .xlsx se leen de verdad (spec pieza 1 §5). */
class ImportadorNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 9, 5);
    private final ClienteDrive drive = mock(ClienteDrive.class);
    private final NsysDAO dao = mock(NsysDAO.class);
    private final ImportadorNsys importador = new ImportadorNsys(drive, dao, "carpeta-1", "maestro-1",
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() throws Exception {
        when(dao.empezarImportacion(anyString(), any(), anyString(), any())).thenReturn(1, 2, 3);
        when(dao.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));
        when(dao.cargarMoviles()).thenReturn(Map.of());
        // Maestro sin cambios: el último que se cargó es más reciente que el de Drive.
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-15T08:00:00Z")));
        when(dao.ultimaImportacion("MAESTRO", Set.of("OK", "RECHAZADO"))).thenReturn(Optional.of(
                new Importacion(9, "Maestro.xlsx", LocalDateTime.of(2026, 10, 15, 10, 0), "OK", 23000, null)));
    }

    private static ClienteDrive.FicheroDrive enDrive(String nombre) {
        return new ClienteDrive.FicheroDrive("id-" + nombre, nombre, Instant.parse("2026-10-16T07:00:00Z"));
    }

    private static byte[] items(int filas) throws IOException {
        List<List<Object>> lista = new ArrayList<>();
        for (int i = 0; i < filas; i++)
            lista.add(XlsxDePrueba.fila(String.format("3500000000%05d", i), "Apple iPhone 13 128GB Blue", "A", "Available",
                    "BT-9001", "CAJA LISTOS", "9/2/2026", null));
        return XlsxDePrueba.items(lista);
    }

    private void ultimaActual(LocalDateTime fecha, int filas) {
        Optional<Importacion> ultima = Optional.of(new Importacion(5, "previo", fecha, "OK", filas, null));
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK", "RECHAZADO"))).thenReturn(ultima);
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(ultima);
    }

    @Test void laPrimeraVezSoloImportaElMasRecienteComoPrimeraCarga() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_08.31.xlsx"),
                enDrive("Items_2026.10.15_17.01.xlsx"), enDrive("otro.xlsx"), enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn(items(2));

        importador.pasada();

        verify(drive, times(1)).descargar(anyString());
        verify(dao).empezarImportacion("Items_2026.10.16_09.01.xlsx", LocalDateTime.of(2026, 10, 16, 9, 1), "ACTUAL", AHORA);
        ArgumentCaptor<ResultadoComparacion> r = ArgumentCaptor.forClass(ResultadoComparacion.class);
        verify(dao).aplicar(r.capture(), eq(1));
        assertEquals(2, r.getValue().aGuardar().size());
        assertTrue(r.getValue().eventos().stream().allMatch(e -> e.tipo() == TipoEvento.ENTRADA && e.soloDia()));
        verify(dao).rellenarModelosTelefono();
        verify(dao).terminarImportacion(1, "OK", 2, 2, null, AHORA);
    }

    @Test void procesaLosPendientesEnOrden() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.31.xlsx"),
                enDrive("Items_2026.10.16_08.01.xlsx"), enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(drive);
        orden.verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        orden.verify(drive).descargar("id-Items_2026.10.16_09.31.xlsx");
        verify(drive, never()).descargar("id-Items_2026.10.16_08.01.xlsx");
        verify(dao, times(2)).aplicar(any(), anyInt());
    }

    @Test void unFicheroConMenosDel90PorCientoSeRechazaYSeSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 100);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn(items(50));
        when(drive.descargar("id-Items_2026.10.16_09.31.xlsx")).thenReturn(items(95));

        importador.pasada();

        verify(dao).terminarImportacion(1, "RECHAZADO", 50, 0,
                "Trae 50 filas frente a las 100 de la última importación: se trata como incompleto", AHORA);
        verify(dao, never()).aplicar(any(), eq(1));
        verify(dao).aplicar(any(), eq(2));
        verify(dao).terminarImportacion(eq(2), eq("OK"), eq(95), anyInt(), any(), eq(AHORA));
    }

    @Test void unFormatoMaloSeRechazaYSeSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn("roto".getBytes(StandardCharsets.UTF_8));
        when(drive.descargar("id-Items_2026.10.16_09.31.xlsx")).thenReturn(items(2));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("RECHAZADO"), eq(0), eq(0), startsWith("No es un .xlsx válido"), eq(AHORA));
        verify(dao).terminarImportacion(eq(2), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
    }

    @Test void unFalloDeDriveDejaErrorYParaLaPasada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenThrow(new IOException("Drive respondió 500 a x"));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("ERROR"), eq(0), eq(0), contains("Drive respondió 500"), eq(AHORA));
        verify(drive, never()).descargar("id-Items_2026.10.16_09.31.xlsx");
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void unMaestroNuevoSeRecargaYSinItemsNuevosSeRetraduce() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00.250Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        ProductoMaestro azul = new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13", "13",
                "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false);
        when(dao.cargarProductos()).thenReturn(List.of(azul));
        MovilNsys sinSku = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null, null, null, null, null, null, null, false,
                "Listos", "Listos", EstadoMovil.STOCK, AHORA.minusDays(10), AHORA.minusDays(10));
        when(dao.cargarMoviles()).thenReturn(Map.of(sinSku.clave(), sinSku));
        ultimaActual(LocalDateTime.of(2026, 10, 16, 9, 1), 1);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));

        importador.pasada();

        verify(dao).empezarImportacion("Maestro.xlsx", LocalDateTime.of(2026, 10, 16, 9, 0), "MAESTRO", AHORA);
        verify(dao).reemplazarProductos(List.of(azul));
        verify(dao).terminarImportacion(1, "OK", 1, 0, null, AHORA);
        ArgumentCaptor<ResultadoComparacion> r = ArgumentCaptor.forClass(ResultadoComparacion.class);
        verify(dao).aplicar(r.capture(), isNull());
        assertEquals("i13128AzulA", r.getValue().aGuardar().get(0).sku());
        assertTrue(r.getValue().eventos().isEmpty());
    }

    @Test void unMaestroSinCambiosNoSeDescarga() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());
        importador.pasada();
        verify(drive, never()).descargar("maestro-1");
        verify(dao, never()).reemplazarProductos(any());
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void sinCarpetaNoHaceNada() {
        new ImportadorNsys(drive, dao, "", "", Clock.systemUTC()).pasada();
        verifyNoInteractions(drive);
    }

    @Test void laFechaSaleDelNombre() {
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 16, 9, 1)), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.13.40_25.99.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01 (1).xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre(null));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/TareaImportacionNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TareaImportacionNsysTest {

    @Test void cadaEjecucionEsUnaPasada() {
        ImportadorNsys importador = mock(ImportadorNsys.class);
        new TareaImportacionNsys(importador).ejecutar();
        verify(importador).pasada();
    }

    @Test void soloExisteConLaImportacionEncendida() {
        ConditionalOnProperty condicion = TareaImportacionNsys.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"nsys.importacion.activa"}, condicion.name());
        assertEquals("true", condicion.havingValue());
    }
}
```

- [x] **Step 2: Ejecuta los tests (fallan)**

Run: `mvn -q test -Dtest='ImportadorNsysTest,TareaImportacionNsysTest'`
Expected: error de compilación: no existen `ImportadorNsys` ni `TareaImportacionNsys`.

- [x] **Step 3: Implementación**

Crea `ImportadorNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Una pasada de importación del espejo de NSYS (spec pieza 1 §5): el maestro si ha cambiado y, después, los ficheros
 *  Items pendientes, en orden. La primera vez solo importa el más reciente (esqueleto). */
@Service
public class ImportadorNsys {

    private static final Logger log = LoggerFactory.getLogger(ImportadorNsys.class);

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    public static final double MINIMO_FILAS = 0.9;
    public static final String ACTUAL = "ACTUAL";
    public static final String HISTORICO = "HISTORICO";
    public static final String MAESTRO = "MAESTRO";
    public static final String OK = "OK";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ERROR = "ERROR";
    private static final Pattern NOMBRE = Pattern.compile("Items_(\\d{4})\\.(\\d{2})\\.(\\d{2})_(\\d{2})\\.(\\d{2})\\.xlsx");

    /** Un fichero Items de Drive con la fecha que lleva en el nombre (hora de Madrid). */
    public record FicheroItems(String id, String nombre, LocalDateTime fecha) {}

    private final ClienteDrive drive;
    private final NsysDAO dao;
    private final String carpeta;
    private final String maestro;
    private final Clock clock;

    @Autowired
    public ImportadorNsys(ClienteDrive drive, NsysDAO dao, @Value("${nsys.drive.carpeta-items:}") String carpeta,
                          @Value("${nsys.drive.maestro:}") String maestro) {
        this(drive, dao, carpeta, maestro, Clock.system(MADRID));
    }

    ImportadorNsys(ClienteDrive drive, NsysDAO dao, String carpeta, String maestro, Clock clock) {
        this.drive = drive;
        this.dao = dao;
        this.carpeta = carpeta == null ? "" : carpeta;
        this.maestro = maestro == null ? "" : maestro;
        this.clock = clock;
    }

    public static Optional<LocalDateTime> fechaDelNombre(String nombre) {
        Matcher m = NOMBRE.matcher(nombre == null ? "" : nombre);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))));
        } catch (DateTimeException fechaImposible) {
            return Optional.empty();
        }
    }

    /** Los Items de la carpeta con una fecha válida en el nombre, del más antiguo al más reciente. */
    public static List<FicheroItems> items(ClienteDrive drive, String carpeta) throws IOException, InterruptedException {
        List<FicheroItems> ficheros = new ArrayList<>();
        for (ClienteDrive.FicheroDrive f : drive.listarItems(carpeta))
            fechaDelNombre(f.nombre()).ifPresent(fecha -> ficheros.add(new FicheroItems(f.id(), f.nombre(), fecha)));
        ficheros.sort(Comparator.comparing(FicheroItems::fecha));
        return ficheros;
    }

    public void pasada() {
        if (carpeta.isBlank()) {
            log.warn("NSYS: falta el ajuste nsys.drive.carpeta-items; no se importa nada");
            return;
        }
        boolean maestroNuevo = actualizarMaestro();
        List<FicheroItems> pendientes;
        try {
            pendientes = pendientes();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.warn("NSYS: no se pudo listar la carpeta de Items en Drive: {}", e.toString());
            return;
        }
        if (pendientes.isEmpty()) {
            if (maestroNuevo) retraducir();
            return;
        }
        for (FicheroItems f : pendientes) if (!procesar(f)) return;
    }

    private List<FicheroItems> pendientes() throws IOException, InterruptedException {
        List<FicheroItems> todos = items(drive, carpeta);
        if (todos.isEmpty()) return List.of();
        Optional<Importacion> ultima = dao.ultimaImportacion(ACTUAL, Set.of(OK, RECHAZADO));
        if (ultima.isEmpty()) return List.of(todos.get(todos.size() - 1));
        LocalDateTime desde = ultima.get().fechaExportacion();
        return todos.stream().filter(f -> f.fecha().isAfter(desde)).toList();
    }

    /** Procesa un fichero. Devuelve false si hay que parar la pasada (error de Drive o de la base de datos): el mismo
     *  fichero se reintenta en la siguiente. */
    private boolean procesar(FicheroItems f) {
        int id = dao.empezarImportacion(f.nombre(), f.fecha(), ACTUAL, ahora());
        try {
            List<FilaItems> filas = LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id())));
            Optional<Importacion> previa = dao.ultimaImportacion(ACTUAL, Set.of(OK));
            if (previa.isPresent() && filas.size() < previa.get().filas() * MINIMO_FILAS) {
                dao.terminarImportacion(id, RECHAZADO, filas.size(), 0, String.format(
                        "Trae %d filas frente a las %d de la última importación: se trata como incompleto",
                        filas.size(), previa.get().filas()), ahora());
                return true;
            }
            Map<ClaveMovil, MovilNsys> anteriores = dao.cargarMoviles();
            Comparador.Modo modo = anteriores.isEmpty() ? Comparador.Modo.PRIMERA_CARGA : Comparador.Modo.NORMAL;
            ResultadoComparacion r = Comparador.comparar(anteriores, filas, f.fecha(), modo, traductor());
            dao.aplicar(r, id);
            int modelos = dao.rellenarModelosTelefono();
            dao.terminarImportacion(id, OK, filas.size(), r.eventos().size(), resumen(r, modelos), ahora());
            return true;
        } catch (FormatoItemsException e) {
            dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
            return false;
        } catch (Exception e) {
            log.warn("NSYS: error al importar {}", f.nombre(), e);
            dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
            return false;
        }
    }

    /** Recarga el maestro si en Drive hay uno más nuevo que el último cargado o rechazado. true si lo ha recargado. */
    private boolean actualizarMaestro() {
        if (maestro.isBlank()) return false;
        Integer id = null;
        try {
            ClienteDrive.FicheroDrive meta = drive.metadatos(maestro);
            LocalDateTime modificado = LocalDateTime.ofInstant(meta.modificado(), MADRID).truncatedTo(ChronoUnit.SECONDS);
            Optional<Importacion> ultimo = dao.ultimaImportacion(MAESTRO, Set.of(OK, RECHAZADO));
            if (ultimo.isPresent() && !modificado.isAfter(ultimo.get().fechaExportacion())) return false;
            id = dao.empezarImportacion(meta.nombre(), modificado, MAESTRO, ahora());
            ResultadoMaestro m = LectorMaestro.leer(new ByteArrayInputStream(drive.descargar(maestro)));
            dao.reemplazarProductos(m.productos());
            dao.terminarImportacion(id, OK, m.productos().size(), 0, resumen(m), ahora());
            return true;
        } catch (FormatoItemsException e) {
            if (id != null) dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (id != null) dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
            return false;
        } catch (Exception e) {
            log.warn("NSYS: no se pudo actualizar el maestro: {}", e.toString());
            if (id != null) dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
            return false;
        }
    }

    /** Con un maestro nuevo y ningún Items pendiente, vuelve a traducir la foto guardada (SKU, modelo…) sin eventos. */
    private void retraducir() {
        Map<ClaveMovil, MovilNsys> anteriores = dao.cargarMoviles();
        if (anteriores.isEmpty()) return;
        List<FilaItems> filas = anteriores.values().stream().map(MovilNsys::comoFila).toList();
        dao.aplicar(Comparador.comparar(anteriores, filas, ahora(), Comparador.Modo.NORMAL, traductor()), null);
        dao.rellenarModelosTelefono();
    }

    private Traductor traductor() {
        return new Traductor(dao.cargarProductos(), dao.cargarUbicaciones());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    static String resumen(ResultadoComparacion r, int modelos) {
        List<String> partes = new ArrayList<>();
        if (!r.cajasDesconocidas().isEmpty())
            partes.add("Cajas sin traducir: " + String.join(", ",
                    r.cajasDesconocidas().stream().map(c -> c.isEmpty() ? "(sin caja)" : c).toList()));
        if (r.desaparecidos() > 0) partes.add(r.desaparecidos() + " móviles anteriores no venían en el fichero");
        if (modelos > 0) partes.add("Modelo rellenado en " + modelos + " teléfonos del taller");
        return partes.isEmpty() ? null : String.join(". ", partes);
    }

    static String resumen(ResultadoMaestro m) {
        List<String> partes = new ArrayList<>();
        if (!m.conflictos().isEmpty())
            partes.add("Sin SKU por nombre repetido con SKU distintos: " + String.join(", ", m.conflictos()));
        if (!m.modelosFueraDeCatalogo().isEmpty())
            partes.add("Modelos fuera del catálogo del ERP: " + String.join(", ", m.modelosFueraDeCatalogo()));
        return partes.isEmpty() ? null : String.join(". ", partes);
    }
}
```

> El test del maestro usa `"2026-10-16T07:00:00.250Z"`. Al truncar a segundos queda `09:00:00` de Madrid; sin truncar,
> el `DATETIME` guardado (sin milésimas) haría que el maestro pareciera nuevo en cada pasada.

Crea `TareaImportacionNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 5 minutos, una pasada de importación (spec pieza 1 §5). Solo existe con nsys.importacion.activa=true: en los
 *  tests y en los entornos sin la cuenta de Drive no hay tarea. */
@Component
@ConditionalOnProperty(name = "nsys.importacion.activa", havingValue = "true")
public class TareaImportacionNsys {

    private final ImportadorNsys importador;

    public TareaImportacionNsys(ImportadorNsys importador) {
        this.importador = importador;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void ejecutar() {
        importador.pasada();
    }
}
```

- [x] **Step 4: Ejecuta los tests (pasan)**

Run: `mvn -q test -Dtest='ImportadorNsysTest,TareaImportacionNsysTest'`
Expected: `BUILD SUCCESS`, 11 tests en verde.

- [x] **Step 5: Documentación de despliegue y comentario del contrato**

En `docs/deployment.md`, tras la línea `- \`SPRINGDOC_SWAGGER_UI_ENABLED\``, añade:

```markdown
- `NSYS_IMPORTACION_ACTIVA` (0.9.17; por defecto `false`): enciende la importación del espejo de NSYS cada 5 minutos.
- `NSYS_DRIVE_CARPETA_ITEMS` y `NSYS_DRIVE_MAESTRO`: identificadores en Drive de la carpeta de los ficheros Items y del maestro de productos.
- `NSYS_DRIVE_CREDENCIALES`: ruta, dentro del contenedor, del JSON de la cuenta de servicio de Google. Es un fichero montado en solo lectura; nunca va al repo.
- `NSYS_HISTORICO_ACTIVO` (por defecto `false`): carga de noche el histórico de exportaciones; cuando termina, no hace nada.
```

En el Javadoc de `OpenApiContractTest`, cambia `—nada consulta` + salto + ` * al arrancar y el único @Scheduled corre a medianoche—` por:

```
—nada consulta
 * al arrancar: el único @Scheduled siempre activo corre a medianoche y las tareas de NSYS (0.9.17) solo
 * existen con nsys.importacion.activa o nsys.historico.activo—
```

Run: `mvn -q test -Dtest=OpenApiContractTest`
Expected: `BUILD SUCCESS`. El contexto arranca con `ImportadorNsys` y `ClienteDrive`, pero sin ninguna tarea de NSYS.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys docs/deployment.md src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: importacion del espejo de NSYS cada 5 minutos con freno del 90 por ciento"
```

---

### Task 7: Servidor — carga del histórico, de noche

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/HistoricoNsys.java`, `TareaHistoricoNsys.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/HistoricoNsysTest.java`

**Interfaces:**
- Consumes: `ImportadorNsys.items`, `ImportadorNsys.FicheroItems` y sus constantes (Task 6); `NsysDAO` (Task 4); `LectorItems` (Task 3); `Comparador` (Task 2).
- Produces:
  - **`HistoricoNsys`** (`@Service`): `void paso()` y `static boolean deNoche(LocalTime)`.
  - **`TareaHistoricoNsys`** (`@Component`): solo existe con `nsys.historico.activo=true`.

- [x] **Step 1: Test (falla)**

Crea `src/test/java/com/reparaciones/servidor/nsys/HistoricoNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El histórico (spec pieza 1 §6): de noche, un fichero por paso, solo eventos y reanudable. */
class HistoricoNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String PRIMERO = "Items_2026.10.01_09.01.xlsx";
    private static final String SEGUNDO = "Items_2026.10.01_09.31.xlsx";
    private static final String ACTUAL = "Items_2026.10.16_09.01.xlsx";
    private final ClienteDrive drive = mock(ClienteDrive.class);
    private final NsysDAO dao = mock(NsysDAO.class);

    private HistoricoNsys a(int hora, int minuto) {
        LocalDateTime cuando = LocalDateTime.of(2026, 10, 16, hora, minuto);
        return new HistoricoNsys(drive, dao, "carpeta-1", Clock.fixed(cuando.atZone(MADRID).toInstant(), MADRID));
    }

    private static ClienteDrive.FicheroDrive enDrive(String nombre) {
        return new ClienteDrive.FicheroDrive("id-" + nombre, nombre, Instant.parse("2026-10-16T07:00:00Z"));
    }

    private static byte[] unMovilEn(String caja) throws IOException {
        return XlsxDePrueba.items(List.of(XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "C100",
                "InProcess", "BT-9001", caja, "9/2/2026", null)));
    }

    @BeforeEach void base() throws Exception {
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(1, ACTUAL, LocalDateTime.of(2026, 10, 16, 9, 1), "OK", 1, null)));
        when(dao.empezarImportacion(anyString(), any(), anyString(), any())).thenReturn(11, 12, 13);
        when(dao.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA GLASS", "Taller · Glass", true, false),
                new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive(ACTUAL), enDrive(SEGUNDO), enDrive(PRIMERO),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-" + PRIMERO)).thenReturn(unMovilEn("CAJA GLASS"));
        when(drive.descargar("id-" + SEGUNDO)).thenReturn(unMovilEn("CAJA LISTOS"));
    }

    @Test void soloDeNoche() {
        assertTrue(HistoricoNsys.deNoche(LocalTime.of(23, 0)));
        assertTrue(HistoricoNsys.deNoche(LocalTime.of(6, 59)));
        assertFalse(HistoricoNsys.deNoche(LocalTime.of(7, 0)));
        assertFalse(HistoricoNsys.deNoche(LocalTime.of(22, 59)));
    }

    @Test void deDiaNoHaceNada() {
        a(12, 0).paso();
        verifyNoInteractions(drive, dao);
    }

    @Test void sinLaPrimeraImportacionNormalEspera() {
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.empty());
        a(23, 30).paso();
        verifyNoInteractions(drive);
    }

    @SuppressWarnings("unchecked")
    @Test void elPrimerFicheroEsElPuntoDePartidaYElSiguienteApuntaCambios() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(), Set.of(PRIMERO));
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.empty(),
                Optional.of(new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 1, null)));
        HistoricoNsys historico = a(23, 30);

        historico.paso();
        verify(dao).terminarImportacion(eq(11), eq("OK"), eq(1), eq(0), eq("Punto de partida del histórico"), any());
        verify(dao, never()).insertarEventos(any(), any());

        historico.paso();
        ArgumentCaptor<List<EventoNsys>> eventos = ArgumentCaptor.forClass(List.class);
        verify(dao).insertarEventos(eventos.capture(), eq(12));
        assertEquals(List.of(new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 10, 1, 9, 31), false,
                TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")), eventos.getValue());
        verify(drive, times(1)).descargar("id-" + PRIMERO);
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void trasReiniciarRecargaLaFotoDelUltimoFicheroHecho() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(PRIMERO));
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 1, null)));

        a(2, 0).paso();

        InOrder orden = inOrder(drive);
        orden.verify(drive).descargar("id-" + PRIMERO);
        orden.verify(drive).descargar("id-" + SEGUNDO);
        verify(dao).insertarEventos(argThat(l -> l.size() == 1), eq(11));
    }

    @Test void alAcabarNoVuelveAMirarDrive() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(PRIMERO, SEGUNDO, ACTUAL));
        HistoricoNsys historico = a(23, 30);
        historico.paso();
        historico.paso();
        verify(drive, times(1)).listarItems("carpeta-1");
        verify(drive, never()).descargar(anyString());
    }
}
```

> En `trasReiniciarRecargaLaFotoDelUltimoFicheroHecho` solo se registra una importación, la del segundo fichero: es la
> primera llamada a `empezarImportacion`, que devuelve 11. Releer el primer fichero no abre registro, porque ya está
> hecho.

- [x] **Step 2: Ejecuta el test (falla)**

Run: `mvn -q test -Dtest=HistoricoNsysTest`
Expected: error de compilación: no existe `HistoricoNsys`.

- [x] **Step 3: Implementación**

Crea `HistoricoNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.ImportadorNsys.*;

/**
 * Carga del histórico (spec pieza 1 §6):
 * - recorre en orden las exportaciones antiguas de Drive, hasta la primera importación normal incluida, y compara cada
 *   una con la anterior;
 * - solo añade eventos: nunca toca la foto ni repite la entrada y la venta del esqueleto;
 * - de noche y un fichero por paso;
 * - se reanuda donde lo dejó y, al terminar, no vuelve a mirar Drive hasta el siguiente arranque.
 */
@Service
public class HistoricoNsys {

    private static final Logger log = LoggerFactory.getLogger(HistoricoNsys.class);
    static final LocalTime EMPIEZA = LocalTime.of(23, 0);
    static final LocalTime ACABA = LocalTime.of(7, 0);

    private final ClienteDrive drive;
    private final NsysDAO dao;
    private final String carpeta;
    private final Clock clock;
    private Map<ClaveMovil, MovilNsys> fotoPrevia;
    private String ficheroPrevio;
    private int filasPrevias;
    private boolean terminado;

    @Autowired
    public HistoricoNsys(ClienteDrive drive, NsysDAO dao, @Value("${nsys.drive.carpeta-items:}") String carpeta) {
        this(drive, dao, carpeta, Clock.system(MADRID));
    }

    HistoricoNsys(ClienteDrive drive, NsysDAO dao, String carpeta, Clock clock) {
        this.drive = drive;
        this.dao = dao;
        this.carpeta = carpeta == null ? "" : carpeta;
        this.clock = clock;
    }

    static boolean deNoche(LocalTime hora) {
        return !hora.isBefore(EMPIEZA) || hora.isBefore(ACABA);
    }

    /** Procesa como mucho un fichero antiguo. */
    public synchronized void paso() {
        if (terminado || carpeta.isBlank() || !deNoche(LocalTime.now(clock))) return;
        Optional<Importacion> primera = dao.primeraImportacion(ACTUAL, Set.of(OK));
        if (primera.isEmpty()) return;                       // hasta que exista la foto actual
        try {
            LocalDateTime limite = primera.get().fechaExportacion();
            List<FicheroItems> candidatos = items(drive, carpeta).stream().filter(f -> !f.fecha().isAfter(limite)).toList();
            Set<String> hechos = dao.ficherosProcesados(HISTORICO);
            Optional<FicheroItems> siguiente = candidatos.stream().filter(f -> !hechos.contains(f.nombre())).findFirst();
            if (siguiente.isEmpty()) {
                terminado = true;
                log.info("NSYS: histórico cargado");
                return;
            }
            prepararFotoPrevia(candidatos);
            procesar(siguiente.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("NSYS: histórico: {}", e.toString());
        }
    }

    /** La foto del último fichero histórico hecho: la que hay en memoria o, tras un reinicio, la que se vuelve a leer. */
    private void prepararFotoPrevia(List<FicheroItems> candidatos) throws Exception {
        Optional<Importacion> ultimo = dao.ultimaImportacion(HISTORICO, Set.of(OK));
        if (ultimo.isEmpty()) {
            fotoPrevia = null;
            return;
        }
        String nombre = ultimo.get().fichero();
        if (fotoPrevia != null && Objects.equals(ficheroPrevio, nombre)) return;
        FicheroItems f = candidatos.stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElse(null);
        if (f == null) {
            fotoPrevia = null;                               // ya no está en Drive: este fichero hace de punto de partida
            return;
        }
        guardarFoto(f, LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id()))), traductor());
    }

    private void procesar(FicheroItems f) {
        int id = dao.empezarImportacion(f.nombre(), f.fecha(), HISTORICO, ahora());
        try {
            List<FilaItems> filas = LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id())));
            Traductor t = traductor();
            if (fotoPrevia == null) {
                guardarFoto(f, filas, t);
                dao.terminarImportacion(id, OK, filas.size(), 0, "Punto de partida del histórico", ahora());
                return;
            }
            if (filas.size() < filasPrevias * MINIMO_FILAS) {
                dao.terminarImportacion(id, RECHAZADO, filas.size(), 0, String.format(
                        "Trae %d filas frente a las %d de la anterior: se trata como incompleto", filas.size(), filasPrevias),
                        ahora());
                return;
            }
            ResultadoComparacion r = Comparador.comparar(fotoPrevia, filas, f.fecha(), Comparador.Modo.HISTORICO, t);
            dao.insertarEventos(r.eventos(), id);
            guardarFoto(f, filas, t);
            dao.terminarImportacion(id, OK, filas.size(), r.eventos().size(), null, ahora());
        } catch (FormatoItemsException e) {
            dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
        } catch (Exception e) {
            log.warn("NSYS: histórico: error con {}", f.nombre(), e);
            dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
        }
    }

    private void guardarFoto(FicheroItems f, List<FilaItems> filas, Traductor t) {
        fotoPrevia = Comparador.foto(filas, t, f.fecha());
        ficheroPrevio = f.nombre();
        filasPrevias = filas.size();
    }

    private Traductor traductor() {
        return new Traductor(dao.cargarProductos(), dao.cargarUbicaciones());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
```

Crea `TareaHistoricoNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto, un paso del histórico (spec pieza 1 §6). Solo existe con nsys.historico.activo=true; el paso no hace nada
 *  de día ni cuando ya ha terminado. */
@Component
@ConditionalOnProperty(name = "nsys.historico.activo", havingValue = "true")
public class TareaHistoricoNsys {

    private final HistoricoNsys historico;

    public TareaHistoricoNsys(HistoricoNsys historico) {
        this.historico = historico;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 60_000)
    public void ejecutar() {
        historico.paso();
    }
}
```

- [x] **Step 4: Ejecuta el test (pasa)**

Run: `mvn -q test -Dtest=HistoricoNsysTest`
Expected: `BUILD SUCCESS`, 6 tests en verde.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys/HistoricoNsysTest.java
git commit -m "feat: carga nocturna del historico de exportaciones de NSYS para el rastro"
```

---

### Task 8: Servidor — API de solo lectura: stock, estado, buscar, ficha y rastro

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/AvisoNsys.java`, `EventoRastro.java`, `Rastro.java`, `StockTelefonosController.java`
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (test nuevo)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/AvisoNsysTest.java`, `RastroTest.java`, `StockTelefonosControllerTest.java`, `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java`

**Interfaces:**
- Consumes: `NsysDAO`, `Importacion` y `TrabajoTaller` (Task 4); `Normalizacion` (Task 1); `ImportadorNsys.MADRID` y sus constantes (Task 6).
- Produces:
  - `AvisoNsys.nivel(LocalDateTime ultima, LocalDateTime ahora)` → `"OK" | "RETRASO" | "SIN_DATOS"`.
  - `record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo, String lote, String de, String a, String aNsys, String idRep, String tecnico, String categoria)`, esquema `EventoRastro`.
  - `Rastro.unir(List<EventoNsys>, List<TrabajoTaller>)` → `List<EventoRastro>`, del más reciente al más antiguo.
  - Rutas (todas `GET`, `hasAnyRole('SUPERTECNICO', 'ADMIN')`):
    - `/api/stock-telefonos` → `StockTelefonosMovilStock[]`;
    - `/api/stock-telefonos/estado` → `StockTelefonosEstadoImportacion`;
    - `/api/stock-telefonos/buscar?imei=` → `StockTelefonosMovilBuscado[]`;
    - `/api/stock-telefonos/{imeiSn}` → `StockTelefonosFichaMovil` (404 si NSYS no lo conoce);
    - `/api/stock-telefonos/{imeiSn}/rastro` → `EventoRastro[]`.
  - Campos de los esquemas:
    - `MovilStock`: `imeiSn, lote, modelo?, modeloMaestro?, capacidad?, color?, grado, bateriaNueva, esEsim, sku?, ubicacion?, estado, dias?`;
    - `EstadoImportacion`: `ultimaExportacion?, nivel, ultimoFallo?, motivoFallo?`;
    - `MovilBuscado`: `imeiSn, modelo?, modeloMaestro?, capacidad?, color?, estado, ubicacion?, fechaVenta?`;
    - `FichaMovil`: `imeiSn, lote, modelo?, modeloMaestro?, capacidad?, color?, grado, bateriaNueva, gradoAnterior?, sku?, ubicacion?, estado, dias?, entradas, fechaVenta?`.

    `?` = `nullable`.

- [x] **Step 1: Tests (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/AvisoNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Aviso de datos antiguos (spec pieza 1 §5.7): solo cuentan los minutos de 07:00 a 23:00. */
class AvisoNsysTest {

    private static LocalDateTime t(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, dia, hora, minuto);
    }

    @Test void alDia() {
        assertEquals("OK", AvisoNsys.nivel(t(16, 13, 31), t(16, 13, 43)));
    }

    @Test void retrasoPasadaLaHora() {
        assertEquals("RETRASO", AvisoNsys.nivel(t(16, 13, 31), t(16, 15, 0)));
    }

    @Test void sinDatosPasadasTresHorasHabilesOSinImportaciones() {
        assertEquals("SIN_DATOS", AvisoNsys.nivel(t(16, 8, 31), t(16, 13, 43)));
        assertEquals("SIN_DATOS", AvisoNsys.nivel(null, t(16, 13, 43)));
    }

    @Test void laNocheNoCuenta() {
        assertEquals(50, AvisoNsys.minutosHabiles(t(15, 22, 30), t(16, 7, 20)));
        assertEquals("OK", AvisoNsys.nivel(t(15, 22, 30), t(16, 7, 20)));
        assertEquals(0, AvisoNsys.minutosHabiles(t(16, 13, 0), t(16, 12, 0)));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/RastroTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
                new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION"),
                new EventoRastro(t(8, 13), false, "NSYS", "UBICACION", "BT-9001", "Taller · Glass", "Listos", "CAJA LISTOS", null, null, null),
                new EventoRastro(t(8, 12), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "G20260905_1", "Técnico 3", "GLASS"),
                new EventoRastro(t(5, 9), false, "ERP", "TRABAJO_ASIGNADO", null, null, null, null, "AG20260905_1", "Técnico 3", "GLASS"),
                new EventoRastro(t(2, 0), true, "NSYS", "ENTRADA", "BT-9001", null, null, null, null, null, null)),
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

    @Test void categoriaPorElIdDelTrabajo() {
        assertEquals("GLASS", Rastro.categoria("AG1"));
        assertEquals("GLASS", Rastro.categoria("G1"));
        assertEquals("PULIDO", Rastro.categoria("AP1"));
        assertEquals("PULIDO", Rastro.categoria("P1"));
        assertEquals("REPARACION", Rastro.categoria("A1"));
        assertEquals("REPARACION", Rastro.categoria("R1"));
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La API de solo lectura con el DAO mockeado y la hora fija (16/10/2026 13:43 en Madrid). Las respuestas son records
 *  privados del controlador: se miran como JSON, igual que las ve la web. */
class StockTelefonosControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 13, 43);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final NsysDAO dao = mock(NsysDAO.class);
    private final StockTelefonosController api = new StockTelefonosController(dao,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static MovilNsys movil(String imei, String batch, String grade, String ubicacion, String ultima, EstadoMovil estado) {
        return new MovilNsys(imei, batch, "Apple iPhone 13 128GB Blue", grade, "Available", "x", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", grade, false, ubicacion,
                ultima, estado, AHORA.minusDays(15), AHORA.minusDays(1));
    }

    private static JsonNode json(Object respuesta) {
        return JSON.valueToTree(respuesta);
    }

    @Test void stockConGradoYBateriaSeparadosYLaUbicacionDeLosDePedido() {
        when(dao.enEmpresa()).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A-100", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", null, "Taller · Glass", EstadoMovil.DE_PEDIDO)));
        JsonNode stock = json(api.stock());
        assertEquals("A-", stock.get(0).get("grado").asText());
        assertTrue(stock.get(0).get("bateriaNueva").asBoolean());
        assertEquals("BT-9001", stock.get(0).get("lote").asText());
        assertEquals(15, stock.get(0).get("dias").asInt(), "del 1/10 al 16/10");
        assertEquals("Taller · Glass", stock.get(1).get("ubicacion").asText(), "un móvil de pedido sigue en su caja");
        assertEquals("DE_PEDIDO", stock.get(1).get("estado").asText());
    }

    @Test void estadoConElUltimoOkYUnFalloPosterior() {
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(5, "a", LocalDateTime.of(2026, 10, 16, 13, 31), "OK", 70000, null)));
        when(dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "ERROR"))).thenReturn(Optional.of(
                new Importacion(6, "b", LocalDateTime.of(2026, 10, 16, 13, 40), "RECHAZADO", 10, "Trae 10 filas…")));
        JsonNode estado = json(api.estado());
        assertEquals("OK", estado.get("nivel").asText());
        assertTrue(estado.get("ultimaExportacion").asText().startsWith("2026-10-16T13:31"));
        assertEquals("Trae 10 filas…", estado.get("motivoFallo").asText());
    }

    @Test void unFalloAnteriorAlUltimoOkNoSeEnsena() {
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(5, "a", LocalDateTime.of(2026, 10, 16, 8, 31), "OK", 70000, null)));
        when(dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "ERROR"))).thenReturn(Optional.of(
                new Importacion(4, "b", LocalDateTime.of(2026, 10, 16, 8, 1), "ERROR", 0, "viejo")));
        JsonNode estado = json(api.estado());
        assertEquals("SIN_DATOS", estado.get("nivel").asText());
        assertTrue(estado.get("motivoFallo").isNull());
    }

    @Test void buscarConMenosDeCuatroCaracteresNoConsulta() {
        assertTrue(api.buscar("350").isEmpty());
        verifyNoInteractions(dao);
        when(dao.buscar("3500", 10)).thenReturn(List.of(movil("350000000000001", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        assertEquals("350000000000001", json(api.buscar("3500")).get(0).get("imeiSn").asText());
    }

    @Test void fichaConElLoteMasRecienteSusEntradasYElGradoAnterior() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9002", "ID", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000001", "BT-9001", "A100", null, "Listos", EstadoMovil.BAJA)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(30), false, TipoEvento.GRADO, "C100", "A100", null),
                new EventoNsys("350000000000001", "BT-9002", AHORA.minusDays(16), false, TipoEvento.GRADO, "A100", "ID", null)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("BT-9002", ficha.get("lote").asText());
        assertEquals(2, ficha.get("entradas").asInt());
        assertEquals("A100", ficha.get("gradoAnterior").asText());
        assertEquals("ID", ficha.get("grado").asText());
    }

    @Test void fichaDeUnImeiQueNsysNoConoceEs404() {
        when(dao.porImei("x")).thenReturn(List.of());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> api.ficha("x"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test void rastroUneNsysYTaller() {
        when(dao.eventos("350000000000001")).thenReturn(List.of());
        when(dao.trabajos("350000000000001")).thenReturn(List.of(new TrabajoTaller("G1", AHORA.minusDays(3), AHORA.minusDays(2), "Técnico 3")));
        assertEquals("TRABAJO_TERMINADO", api.rastro("350000000000001").get(0).tipo());
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stock de teléfonos (0.9.17, spec pieza 1 §3): SUPERTECNICO y ADMIN; el TECNICO recibe 403 sin llegar al DAO. Cadena de
 *  seguridad real y DAO mockeado (sin base de datos). */
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
class RolesStockTelefonosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean NsysDAO dao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final List<String> RUTAS = List.of("/api/stock-telefonos", "/api/stock-telefonos/estado",
            "/api/stock-telefonos/buscar?imei=3500", "/api/stock-telefonos/350000000000001",
            "/api/stock-telefonos/350000000000001/rastro");

    @Test void elTecnicoRecibe403SinLlegarAlDao() throws Exception {
        for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 10, 1, 10, 0);
        when(dao.porImei("350000000000001")).thenReturn(List.of(new MovilNsys("350000000000001", "BT-9001",
                "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A", null, null, null, null, null,
                null, null, null, false, "Listos", "Listos", EstadoMovil.STOCK, t, t)));
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", supertecnico())).andExpect(status().isOk());
            mvc.perform(get(ruta).header("Authorization", admin())).andExpect(status().isOk());
        }
    }
}
```

En `OpenApiContractTest`, tras el método `elContratoPublicaLaGestion()`, añade:

```java
    /** 0.9.17, pieza 1 del módulo de teléfonos: el stock de teléfonos y el rastro (spec 2026-10-16 §8). */
    @Test void elContratoPublicaElStockDeTelefonos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/stock-telefonos", "/api/stock-telefonos/estado", "/api/stock-telefonos/buscar",
                "/api/stock-telefonos/{imeiSn}", "/api/stock-telefonos/{imeiSn}/rastro")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/estado", "get", "200").endsWith("/StockTelefonosEstadoImportacion"));
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/{imeiSn}", "get", "200").endsWith("/StockTelefonosFichaMovil"));
        JsonNode imei = parametro(paths, "/api/stock-telefonos/buscar", "get", "imei");
        assertEquals("query", imei.path("in").asText());

        assertNullable(esquemas, "StockTelefonosMovilStock", "modelo", "modeloMaestro", "capacidad", "color", "sku", "ubicacion", "dias");
        assertNoNullable(esquemas, "StockTelefonosMovilStock", "imeiSn", "lote", "grado", "bateriaNueva", "esEsim", "estado");
        assertNullable(esquemas, "StockTelefonosEstadoImportacion", "ultimaExportacion", "ultimoFallo", "motivoFallo");
        assertNoNullable(esquemas, "StockTelefonosEstadoImportacion", "nivel");
        assertNullable(esquemas, "StockTelefonosMovilBuscado", "modelo", "modeloMaestro", "capacidad", "color", "ubicacion", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosMovilBuscado", "imeiSn", "estado");
        assertNullable(esquemas, "StockTelefonosFichaMovil", "modelo", "modeloMaestro", "capacidad", "color", "gradoAnterior",
                "sku", "ubicacion", "dias", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "imeiSn", "lote", "grado", "bateriaNueva", "estado", "entradas");
        assertNullable(esquemas, "EventoRastro", "lote", "de", "a", "aNsys", "idRep", "tecnico", "categoria");
        assertNoNullable(esquemas, "EventoRastro", "fecha", "soloDia", "fuente", "tipo");
    }
```

- [x] **Step 2: Ejecuta los tests (fallan)**

Run: `mvn -q test -Dtest='AvisoNsysTest,RastroTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: error de compilación: no existen `AvisoNsys`, `EventoRastro`, `Rastro` ni `StockTelefonosController`.

- [x] **Step 3: Implementación**

Crea `AvisoNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Aviso de datos antiguos (spec pieza 1 §5.7). Solo cuentan los minutos de 07:00 a 23:00, cuando el bot exporta: más
 *  de 60 es RETRASO y más de 180, SIN_DATOS. */
public final class AvisoNsys {

    static final LocalTime ABRE = LocalTime.of(7, 0);
    static final LocalTime CIERRA = LocalTime.of(23, 0);
    static final long RETRASO = 60;
    static final long SIN_DATOS = 180;

    private AvisoNsys() {}

    public static String nivel(LocalDateTime ultima, LocalDateTime ahora) {
        if (ultima == null) return "SIN_DATOS";
        long minutos = minutosHabiles(ultima, ahora);
        return minutos > SIN_DATOS ? "SIN_DATOS" : minutos > RETRASO ? "RETRASO" : "OK";
    }

    static long minutosHabiles(LocalDateTime desde, LocalDateTime hasta) {
        long total = 0;
        for (LocalDate dia = desde.toLocalDate(); !dia.isAfter(hasta.toLocalDate()); dia = dia.plusDays(1)) {
            LocalDateTime inicio = mayor(desde, dia.atTime(ABRE));
            LocalDateTime fin = menor(hasta, dia.atTime(CIERRA));
            if (fin.isAfter(inicio)) total += Duration.between(inicio, fin).toMinutes();
        }
        return total;
    }

    private static LocalDateTime mayor(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime menor(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
```

Crea `EventoRastro.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys) o del taller del ERP (idRep, técnico,
 *  categoría). {@code tipo}: los de {@link TipoEvento} o TRABAJO_ASIGNADO / TRABAJO_TERMINADO. La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria) {}
```

Crea `Rastro.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Junta los eventos de NSYS y los trabajos del taller de un IMEI en una sola línea de tiempo, del más reciente al más
 *  antiguo (spec pieza 1 §8). La venta del esqueleto (solo el día) no sale si el mismo lote tiene una con hora. */
public final class Rastro {

    private Rastro() {}

    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        List<EventoRastro> todos = new ArrayList<>();
        for (EventoNsys e : nsys) {
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null));
        }
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep())));
        }
        todos.sort(Comparator.comparing(EventoRastro::fecha).reversed());
        return todos;
    }

    /** Asignaciones A/AG/AP y trabajos hechos R/G/P. */
    static String categoria(String idRep) {
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return "GLASS";
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return "PULIDO";
        return "REPARACION";
    }
}
```

Crea `StockTelefonosController.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.ImportadorNsys.*;

/** Stock de teléfonos y rastro (spec pieza 1 §8): solo lectura, del espejo de NSYS. SUPERTECNICO y ADMIN; no hay
 *  jerarquía de roles, así que van los dos nombrados. */
@RestController
@RequestMapping("/api/stock-telefonos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class StockTelefonosController {

    private final NsysDAO dao;
    private final Clock clock;

    @Autowired
    public StockTelefonosController(NsysDAO dao) {
        this(dao, Clock.system(MADRID));
    }

    StockTelefonosController(NsysDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Los móviles que están en la empresa (en stock, de pedido o que no cuentan). La web filtra en local. */
    @GetMapping
    public List<MovilStock> stock() {
        LocalDate hoy = LocalDate.now(clock);
        return dao.enEmpresa().stream().map(m -> new MovilStock(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(),
                m.capacidad(), m.color(), Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()),
                m.esEsim(), m.sku(), ubicacionVisible(m), m.estado().name(), dias(m, hoy))).toList();
    }

    @GetMapping("/estado")
    public EstadoImportacion estado() {
        Optional<Importacion> ok = dao.ultimaImportacion(ACTUAL, Set.of(OK));
        Optional<Importacion> fallo = dao.ultimaImportacion(ACTUAL, Set.of(RECHAZADO, ERROR));
        LocalDateTime ultima = ok.map(Importacion::fechaExportacion).orElse(null);
        boolean falloReciente = fallo.isPresent() && (ultima == null || fallo.get().fechaExportacion().isAfter(ultima));
        return new EstadoImportacion(ultima, AvisoNsys.nivel(ultima, LocalDateTime.now(clock)),
                falloReciente ? fallo.get().fechaExportacion() : null, falloReciente ? fallo.get().mensaje() : null);
    }

    /** Hasta 10 móviles de cualquier estado cuyo IMEI/SN contiene el texto (mínimo 4 caracteres). */
    @GetMapping("/buscar")
    public List<MovilBuscado> buscar(@RequestParam String imei) {
        if (imei == null || imei.strip().length() < 4) return List.of();
        return dao.buscar(imei.strip(), 10).stream().map(m -> new MovilBuscado(m.imeiSn(), m.modelo(), m.modeloMaestro(),
                m.capacidad(), m.color(), m.estado().name(), ubicacionVisible(m),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null)).toList();
    }

    @GetMapping("/{imeiSn}")
    public FichaMovil ficha(@PathVariable String imeiSn) {
        List<MovilNsys> lotes = dao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        MovilNsys m = lotes.get(0);
        String gradoAnterior = dao.eventos(imeiSn).stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && e.batch().equals(m.batch()))
                .reduce((a, b) -> b).map(EventoNsys::de).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), gradoAnterior, m.sku(),
                ubicacionVisible(m), m.estado().name(), enEmpresa ? dias(m, LocalDate.now(clock)) : null, lotes.size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null);
    }

    @GetMapping("/{imeiSn}/rastro")
    public List<EventoRastro> rastro(@PathVariable String imeiSn) {
        return Rastro.unir(dao.eventos(imeiSn), dao.trabajos(imeiSn));
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

    private record MovilStock(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva, boolean esEsim,
                              @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias) {}

    private record EstadoImportacion(@Schema(nullable = true) LocalDateTime ultimaExportacion, String nivel,
                                     @Schema(nullable = true) LocalDateTime ultimoFallo,
                                     @Schema(nullable = true) String motivoFallo) {}

    private record MovilBuscado(String imeiSn, @Schema(nullable = true) String modelo,
                                @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                @Schema(nullable = true) String color, String estado,
                                @Schema(nullable = true) String ubicacion, @Schema(nullable = true) LocalDate fechaVenta) {}

    private record FichaMovil(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva,
                              @Schema(nullable = true) String gradoAnterior, @Schema(nullable = true) String sku,
                              @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, int entradas,
                              @Schema(nullable = true) LocalDate fechaVenta) {}
}
```

> `StockTelefonosControllerTest` lee las respuestas con Jackson (`valueToTree`) porque los records son privados, como
> en `TelefonoController`. `jackson-datatype-jsr310` ya viene con `spring-boot-starter-web`.

- [x] **Step 4: Ejecuta los tests (pasan)**

Run: `mvn -q test -Dtest='AvisoNsysTest,RastroTest,StockTelefonosControllerTest,RolesStockTelefonosTest,OpenApiContractTest'`
Expected: `BUILD SUCCESS`. Los tests nuevos en verde (4 + 3 + 7 + 2 + 1) y el resto de `OpenApiContractTest` sin cambios.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: API de solo lectura del stock de telefonos y del rastro"
```

---

### Task 9: Servidor — el modelo de un IMEI sale del espejo (adiós IMEIcheck)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/ModeloNsys.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`, `AsignacionController.java`, `GlassController.java`, `PulidoController.java`
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/AsignacionControllerTest.java`, `FiltroTecnicoControllersTest.java`
- Delete: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/ImeiLookupService.java`, `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/FrenoLookup.java`, `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/ImeiLookupServiceTest.java`, `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/security/FrenoLookupTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/ModeloNsysTest.java`, `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/TelefonoControllerModeloTest.java`

**Interfaces:**
- Consumes: `NsysDAO.modeloDe(String)` (Task 4).
- Produces:
  - **`ModeloNsys`** (`@Service`): `String lookupModeloInterno(String imei)`, la misma firma que tenía `ImeiLookupService`. Devuelve `null` si NSYS no conoce el IMEI.
  - **Ruta del modelo:** `GET /api/telefonos/{imei}/modelo` mantiene la ruta, la respuesta (`ValorTexto`) y los permisos, ahora sin llamar fuera ni freno.

- [x] **Step 1: Tests (fallan)**

Crea `src/test/java/com/reparaciones/servidor/nsys/ModeloNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ModeloNsysTest {

    private final NsysDAO dao = mock(NsysDAO.class);
    private final ModeloNsys modelos = new ModeloNsys(dao);

    @Test void elModeloSaleDelEspejo() {
        when(dao.modeloDe("350000000000001")).thenReturn("13pro");
        assertEquals("13pro", modelos.lookupModeloInterno(" 350000000000001 "));
    }

    @Test void sinImeiNoConsulta() {
        assertNull(modelos.lookupModeloInterno(" "));
        assertNull(modelos.lookupModeloInterno(null));
        verifyNoInteractions(dao);
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/controller/TelefonoControllerModeloTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EnvioDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.MovimientoDAO;
import com.reparaciones.servidor.dao.RevisionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.nsys.ModeloNsys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** GET /api/telefonos/{imei}/modelo (0.9.17): el modelo del taller y, si no lo tiene, el del espejo de NSYS. Sustituye a
 *  IMEIcheck: sin llamada externa ni freno. */
class TelefonoControllerModeloTest {

    private final TelefonoDAO dao = mock(TelefonoDAO.class);
    private final ModeloNsys nsys = mock(ModeloNsys.class);
    private final TelefonoController controller = new TelefonoController(dao, nsys, mock(LogDAO.class),
            mock(RevisionDAO.class), mock(EnvioDAO.class), mock(MovimientoDAO.class));

    @Test void conModeloEnElTallerNoMiraNsys() {
        when(dao.getModelo("350000000000001")).thenReturn("12");
        assertEquals("12", controller.getModelo("350000000000001").value());
        verifyNoInteractions(nsys);
    }

    @Test void sinModeloEnElTallerLoDaNsys() {
        when(nsys.lookupModeloInterno("350000000000001")).thenReturn("13pro");
        assertEquals("13pro", controller.getModelo("350000000000001").value());
    }

    @Test void siNadieLoSabeVaVacio() {
        assertEquals("", controller.getModelo("350000000000001").value());
    }
}
```

En `AsignacionControllerTest` y `FiltroTecnicoControllersTest`:
- cambia `import com.reparaciones.servidor.service.ImeiLookupService;` por `import com.reparaciones.servidor.nsys.ModeloNsys;`;
- cambia `ImeiLookupService` por `ModeloNsys`. En `AsignacionControllerTest` es la línea `private final ModeloNsys lookup = mock(ModeloNsys.class);`; en `FiltroTecnicoControllersTest`, los dos `mock(ModeloNsys.class)`.
- El resto de los tests no cambia: `lookupModeloInterno` mantiene el nombre.

- [x] **Step 2: Ejecuta los tests (fallan)**

Run: `mvn -q test -Dtest='ModeloNsysTest,TelefonoControllerModeloTest,AsignacionControllerTest,FiltroTecnicoControllersTest'`
Expected: error de compilación (no existe `ModeloNsys` y `TelefonoController` no tiene ese constructor).

- [x] **Step 3: Implementación**

Crea `src/main/java/com/reparaciones/servidor/nsys/ModeloNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.stereotype.Service;

/** Modelo de un IMEI según el espejo de NSYS (spec pieza 1 §7). Sustituye a IMEIcheck con la misma firma: null si NSYS no
 *  conoce el IMEI o su modelo no está en el catálogo del ERP. Entonces se escribe a mano, como antes. */
@Service
public class ModeloNsys {

    private final NsysDAO dao;

    public ModeloNsys(NsysDAO dao) {
        this.dao = dao;
    }

    public String lookupModeloInterno(String imei) {
        if (imei == null || imei.isBlank()) return null;
        return dao.modeloDe(imei.strip());
    }
}
```

**`TelefonoController`:**
- Imports: quita `import com.reparaciones.servidor.security.FrenoLookup;` y `import com.reparaciones.servidor.service.ImeiLookupService;` y añade `import com.reparaciones.servidor.nsys.ModeloNsys;`.
- Campos: `private final ImeiLookupService imeiLookupService;` pasa a `private final ModeloNsys modeloNsys;` y se borra `private final FrenoLookup frenoLookup;`.
- Constructor completo:

```java
    public TelefonoController(TelefonoDAO dao, ModeloNsys modeloNsys, LogDAO logDao, RevisionDAO revisionDao,
                             EnvioDAO envioDao, MovimientoDAO movimientoDao) {
        this.dao = dao;
        this.modeloNsys = modeloNsys;
        this.logDao = logDao;
        this.revisionDao = revisionDao;
        this.envioDao = envioDao;
        this.movimientoDao = movimientoDao;
    }
```

- Método `getModelo` completo:

```java
    /** El modelo del taller y, si no lo tiene, el del espejo de NSYS (0.9.17: sustituye a IMEIcheck, sin llamar fuera
     *  ni freno). Vacío si nadie lo sabe: se escribe a mano. */
    @GetMapping("/{imei}/modelo")
    public ValorTexto getModelo(@PathVariable String imei) {
        String modelo = dao.getModelo(imei);
        if (modelo == null || modelo.isBlank()) modelo = modeloNsys.lookupModeloInterno(imei);
        return new ValorTexto(modelo != null ? modelo : "");
    }
```

**`AsignacionController`:**
- Imports: `ImeiLookupService` pasa a `com.reparaciones.servidor.nsys.ModeloNsys`.
- Campo: `private final ImeiLookupService imeiLookupService;` pasa a `private final ModeloNsys modeloNsys;`.
- Constructor: el parámetro `ImeiLookupService imeiLookupService` pasa a `ModeloNsys modeloNsys` y la asignación a `this.modeloNsys = modeloNsys;`.
- En `guardarLote`, el comentario `// El lookup externo va aquí, ANTES de entrar en la transacción del servicio.` pasa a `// El modelo de NSYS se busca aquí, antes de entrar en la transacción del servicio.`
- En `conModeloDePulido`:
  - el Javadoc pasa a `/** Como hoy POST /pulidos/asignaciones: un IMEI que solo va a pulido y no tiene modelo ni en el lote ni en BD lo toma del espejo de NSYS (0.9.17; antes, de IMEIcheck). Fuera de la transacción. */`;
  - la llamada `imeiLookupService.lookupModeloInterno(t.imei())` pasa a `modeloNsys.lookupModeloInterno(t.imei())`.

**`GlassController` y `PulidoController`:**
- Import: `com.reparaciones.servidor.service.ImeiLookupService` pasa a `com.reparaciones.servidor.nsys.ModeloNsys`.
- Campo: `private final ImeiLookupService imeiLookupService;` pasa a `private final ModeloNsys modeloNsys;`.
- Constructor: el parámetro `ImeiLookupService imeiLookupService` pasa a `ModeloNsys modeloNsys` y `this.imeiLookupService = imeiLookupService;` a `this.modeloNsys = modeloNsys;`.
- En `insertarAsignacion`, `imeiLookupService.lookupModeloInterno(req.imei())` pasa a `modeloNsys.lookupModeloInterno(req.imei())`.

**Borra:**

```bash
git rm src/main/java/com/reparaciones/servidor/service/ImeiLookupService.java src/main/java/com/reparaciones/servidor/security/FrenoLookup.java src/test/java/com/reparaciones/servidor/service/ImeiLookupServiceTest.java src/test/java/com/reparaciones/servidor/security/FrenoLookupTest.java
```

Run: `grep -rn "ImeiLookupService\|FrenoLookup\|imeicheck" src/`
Expected: sin resultados.

- [x] **Step 4: Ejecuta los tests (pasan) y la suite entera**

Run: `mvn -q test -Dtest='ModeloNsysTest,TelefonoControllerModeloTest,AsignacionControllerTest,FiltroTecnicoControllersTest'`
Expected: `BUILD SUCCESS`.

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, toda la suite en verde, incluidos `OpenApiContractTest` (la ruta `/api/telefonos/{imei}/modelo` sigue respondiendo `ValorTexto`) y `TelefonoControllerEliminarTest`. Anota el total de tests.

- [x] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: el modelo de un IMEI sale del espejo de NSYS y se retira IMEIcheck"
```

---

### Task 10: Web — contrato, tipos, API y piezas compartidas

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (generados), `gestion-reparaciones-web/src/shared/api/client.ts`, `gestion-reparaciones-web/src/test/server.ts`
- Create: `gestion-reparaciones-web/src/shared/lib/telefonoNsys.ts`, `gestion-reparaciones-web/src/shared/api/nsys.ts`, `gestion-reparaciones-web/src/modules/telefonos/api.ts`, `gestion-reparaciones-web/src/modules/telefonos/test/fabrica.ts`, `gestion-reparaciones-web/src/modules/taller/imeis/apiNsys.ts`
- Test: `gestion-reparaciones-web/src/shared/lib/telefonoNsys.test.ts`, `gestion-reparaciones-web/src/modules/telefonos/api.test.tsx`

**Interfaces:**
- Consumes: el contrato del servidor de las Tasks 8 y 9 (`target/openapi.json` de `/c/Users/dev/Documents/wt/servidor-0917`).
- Produces:
  - Alias en `client.ts`: `MovilStock`, `EstadoNsys`, `MovilBuscado`, `FichaMovil`, `EventoRastro`.
  - En `shared/lib/telefonoNsys.ts`: `nombreModeloNsys(modelo, modeloMaestro)`, `clasesUbicacion(ubicacion)`, `textoEstadoMovil(estado)` y `diaNsys(iso)` («2023-05-02» → «02/05/2023»).
  - En `shared/api/nsys.ts`: `useBuscarMoviles(texto, activo = true)`. Va en `shared` (como `useClientes` en `shared/api/clientes.ts`) porque lo usan los dos módulos y un módulo no importa de otro.
  - En `modules/telefonos/api.ts`: `useStockTelefonos()` y `useEstadoNsys()`.
  - En `modules/taller/imeis/apiNsys.ts`: `useFichaMovil(imei, activo)` y `useRastro(imei, activo)`.
  - Fábricas de test: `movil(parcial)`, `estadoNsys(parcial)` y `movilBuscado(parcial)`.
  - Handlers por defecto de MSW para `/api/stock-telefonos/buscar`, `/{imeiSn}` (404) y `/{imeiSn}/rastro`.

- [x] **Step 1: Contrato y tipos**

Run (en `/c/Users/dev/Documents/wt/web-0917`):
`cp /c/Users/dev/Documents/wt/servidor-0917/target/openapi.json api/openapi.json && npm run api:types:offline`
Expected: `schema.d.ts` regenerado. `grep -c "StockTelefonosMovilStock\|EventoRastro" src/shared/api/schema.d.ts` da un número mayor que 0.

En `src/shared/api/client.ts`, tras el último `export type … = components['schemas'][…]` de la cabecera (justo antes de
`const baseUrl`), añade:

```ts
/** Stock de teléfonos y rastro (0.9.17, pieza 1 del módulo de teléfonos): espejo de solo lectura de NSYS. `estado` del
 *  móvil: STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO o BAJA; `nivel` del aviso: OK, RETRASO o SIN_DATOS; fechas en hora de
 *  Madrid, sin zona. */
export type MovilStock = components['schemas']['StockTelefonosMovilStock']
export type EstadoNsys = components['schemas']['StockTelefonosEstadoImportacion']
export type MovilBuscado = components['schemas']['StockTelefonosMovilBuscado']
export type FichaMovil = components['schemas']['StockTelefonosFichaMovil']
export type EventoRastro = components['schemas']['EventoRastro']
```

- [x] **Step 2: Tests (fallan)**

Crea `src/shared/lib/telefonoNsys.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { clasesUbicacion, diaNsys, nombreModeloNsys, textoEstadoMovil } from './telefonoNsys'

describe('piezas compartidas del espejo de NSYS', () => {
  it('nombre del modelo: el código del ERP, si no el del maestro y si no un aviso', () => {
    expect(nombreModeloNsys('13pro', '13 PRO')).toBe('iPhone 13 Pro')
    expect(nombreModeloNsys(null, 'SE2022')).toBe('iPhone SE2022')
    expect(nombreModeloNsys(null, null)).toBe('Sin modelo en el maestro')
  })

  it('colores de las ubicaciones; las desconocidas en gris', () => {
    expect(clasesUbicacion('Listos')).toBe('bg-fila-recibido-bg text-fila-recibido-brd')
    expect(clasesUbicacion('Taller · Glass')).toBe('bg-tipo-glass-bg text-tipo-glass-text')
    expect(clasesUbicacion('Caja nueva')).toBe('bg-badge-neutro-bg text-azul-gris')
    expect(clasesUbicacion(null)).toBe('bg-badge-neutro-bg text-azul-gris')
  })

  it('texto del estado del móvil', () => {
    expect(['STOCK', 'DE_PEDIDO', 'NO_CUENTA', 'VENDIDO', 'BAJA'].map(textoEstadoMovil))
      .toEqual(['Libre', 'De pedido', 'No cuenta como stock', 'Vendido', 'Baja en NSYS'])
  })

  it('día de NSYS en dd/mm/aaaa, con o sin hora detrás', () => {
    expect(diaNsys('2023-05-02')).toBe('02/05/2023')
    expect(diaNsys('2026-09-12T13:01:00')).toBe('12/09/2026')
  })
})
```

Crea `src/modules/telefonos/test/fabrica.ts`:

```ts
import type { EstadoNsys, MovilBuscado, MovilStock } from '@/shared/api/client'

/** Datos inventados del stock de teléfonos (repos públicos). */
export function movil(p: Partial<MovilStock> = {}): MovilStock {
  return {
    imeiSn: '350000000000001', lote: 'BT-9001', modelo: '13', modeloMaestro: '13', capacidad: '128GB', color: 'Azul',
    grado: 'A', bateriaNueva: false, esEsim: false, sku: 'i13128AzulA', ubicacion: 'Listos', estado: 'STOCK', dias: 6, ...p,
  }
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
```

Crea `src/modules/telefonos/api.test.tsx`:

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
import { useBuscarMoviles } from '@/shared/api/nsys'
import { useEstadoNsys, useStockTelefonos } from './api'
import { estadoNsys, movil } from './test/fabrica'

function envoltorio() {
  guardarSesion(SESION_SUPER)
  const qc = crearQueryClient({ retry: false })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider>{children}</SessionProvider></QueryClientProvider>
  )
}

describe('api del stock de teléfonos', () => {
  it('pide el stock y el estado de NSYS', async () => {
    server.use(
      http.get('*/api/stock-telefonos', () => HttpResponse.json([movil()])),
      http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
    )
    const stock = renderHook(() => useStockTelefonos(), { wrapper: envoltorio() })
    await waitFor(() => expect(stock.result.current.data).toHaveLength(1))
    const estado = renderHook(() => useEstadoNsys(), { wrapper: envoltorio() })
    await waitFor(() => expect(estado.result.current.data?.nivel).toBe('OK'))
  })

  it('el buscador no pregunta con menos de 4 caracteres y manda el texto sin espacios', async () => {
    const pedidas: string[] = []
    server.use(http.get('*/api/stock-telefonos/buscar', ({ request }) => {
      pedidas.push(new URL(request.url).searchParams.get('imei') ?? '')
      return HttpResponse.json([])
    }))
    const corto = renderHook(() => useBuscarMoviles('350'), { wrapper: envoltorio() })
    expect(corto.result.current.fetchStatus).toBe('idle')
    const largo = renderHook(() => useBuscarMoviles(' 3500 '), { wrapper: envoltorio() })
    await waitFor(() => expect(largo.result.current.isSuccess).toBe(true))
    expect(pedidas).toEqual(['3500'])
  })
})
```

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/api.test.tsx`
Expected: FAIL: no existen `./telefonoNsys` ni `./api`.

- [x] **Step 3: Implementación**

Crea `src/shared/lib/telefonoNsys.ts`:

```ts
import { traducirModelo } from './modelos'

/** Piezas del espejo de NSYS (0.9.17, spec «Pieza 1 del módulo de teléfonos») que usan dos módulos: el stock de teléfonos
 *  y el detalle del IMEI del taller. */

/** Nombre de un modelo de NSYS: el código del ERP traducido o, si no está en el catálogo, el texto del maestro. */
export function nombreModeloNsys(modelo: string | null, modeloMaestro: string | null): string {
  if (modelo) return traducirModelo(modelo)
  if (modeloMaestro) return `iPhone ${modeloMaestro}`
  return 'Sin modelo en el maestro'
}

/** Colores de las ubicaciones del ERP (los nombres de Nsys_ubicacion); las que no están aquí, en gris. */
const CLASES_UBICACION: Record<string, string> = {
  'En revisión': 'bg-fila-solicitud-bg text-fila-solicitud-brd',
  Devolución: 'bg-fila-solicitud-bg text-notif-urgente-text',
  'Taller · Pulir': 'bg-tipo-pulido-bg text-tipo-pulido-text',
  'Taller · Glass': 'bg-tipo-glass-bg text-tipo-glass-text',
  'Taller · Reparación': 'bg-tipo-reparacion-bg text-tipo-reparacion-text',
  'Taller · Pantalla rota': 'bg-entrega-bg text-entrega-text',
  Listos: 'bg-fila-recibido-bg text-fila-recibido-brd',
}

export function clasesUbicacion(ubicacion: string | null | undefined): string {
  return (ubicacion && CLASES_UBICACION[ubicacion]) || 'bg-badge-neutro-bg text-azul-gris'
}

const TEXTO_ESTADO: Record<string, string> = {
  DE_PEDIDO: 'De pedido', NO_CUENTA: 'No cuenta como stock', VENDIDO: 'Vendido', BAJA: 'Baja en NSYS',
}

export function textoEstadoMovil(estado: string): string {
  return TEXTO_ESTADO[estado] ?? 'Libre'
}

/** «2023-05-02» (o «2023-05-02T13:01:00») → «02/05/2023». Las fechas del espejo ya son de Madrid: se cortan del texto,
 *  sin pasar por `formatear` de `fechas.ts`, que da por hecho UTC. */
export function diaNsys(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`
}
```

Crea `src/modules/telefonos/api.ts`:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type EstadoNsys, type MovilStock } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'

export const CLAVE_STOCK_TELEFONOS = ['telefonos', 'stock'] as const
export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const

/** GET /api/stock-telefonos: los móviles que están en la empresa. Los filtros se aplican en la pantalla (spec §8). Sondea
 *  como las demás vistas: la importación trae datos nuevos cada 5 min. */
export function useStockTelefonos(): UseQueryResult<MovilStock[]> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_STOCK_TELEFONOS,
    queryFn: async () => (await api.GET('/api/stock-telefonos')).data ?? [],
    refetchInterval: intervalo,
  })
}

/** GET /api/stock-telefonos/estado: hora de la última exportación de NSYS y nivel del aviso. Sin capa de carga: es la
 *  píldora de la cabecera. */
export function useEstadoNsys(): UseQueryResult<EstadoNsys | null> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_ESTADO_NSYS,
    queryFn: async () => (await api.GET('/api/stock-telefonos/estado')).data ?? null,
    refetchInterval: intervalo,
    meta: { sinCapa: true },
  })
}
```

Crea `src/shared/api/nsys.ts`:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type MovilBuscado } from './client'

/** Busca por IMEI en todo el espejo de NSYS, también los vendidos (0.9.17). Lo usan el buscador del stock de teléfonos y
 *  la lista de IMEIs; por eso vive en `shared`. Con menos de 4 caracteres, o con `activo` a false, no pregunta. */
export function useBuscarMoviles(texto: string, activo = true): UseQueryResult<MovilBuscado[]> {
  const limpio = texto.trim()
  return useQuery({
    queryKey: ['nsys', 'buscar', limpio],
    enabled: activo && limpio.length >= 4,
    queryFn: async () => (await api.GET('/api/stock-telefonos/buscar', { params: { query: { imei: limpio } } })).data ?? [],
    meta: { sinCapa: true },
  })
}
```

Crea `src/modules/taller/imeis/apiNsys.ts`:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type EventoRastro, type FichaMovil } from '@/shared/api/client'

/** Lecturas del espejo de NSYS para el detalle y la lista de IMEIs (0.9.17). Solo SUPERTECNICO y ADMIN las piden
 *  (`activo`). La búsqueda de móviles es `useBuscarMoviles` de `shared/api/nsys.ts`. */

/** La ficha de NSYS de un IMEI. Un 404 (NSYS no lo conoce) es normal: sin diálogo, sin reintentos y sin ficha. */
export function useFichaMovil(imei: string, activo: boolean): UseQueryResult<FichaMovil | null> {
  return useQuery({
    queryKey: ['imeis', 'ficha', imei],
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/{imeiSn}', { params: { path: { imeiSn: imei } } })).data ?? null,
    retry: false,
    meta: { silenciarError: true, sinCapa: true },
  })
}

export function useRastro(imei: string, activo: boolean): UseQueryResult<EventoRastro[]> {
  return useQuery({
    queryKey: ['imeis', 'rastro', imei],
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/{imeiSn}/rastro', { params: { path: { imeiSn: imei } } })).data ?? [],
    meta: { sinCapa: true },
  })
}
```

En `src/test/server.ts`, al final de la lista de handlers de `setupServer(...)`, añade (con su comentario):

```ts
  // 0.9.17: el detalle de IMEIs del supertécnico y del admin pide la ficha de NSYS (y su rastro) y la lista busca en el
  // espejo al filtrar por IMEI. Por defecto NSYS no conoce el IMEI (404, la ficha no sale) y la búsqueda no encuentra nada.
  // «buscar» va antes que «:imeiSn» porque el patrón también lo cubriría.
  http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([])),
  http.get('*/api/stock-telefonos/:imeiSn/rastro', () => HttpResponse.json([])),
  http.get('*/api/stock-telefonos/:imeiSn', () => HttpResponse.json({ message: 'NSYS no conoce el IMEI' }, { status: 404 })),
```

- [x] **Step 4: Ejecuta los tests (pasan) y el tipado**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/api.test.tsx && npx tsc -b`
Expected: 6 tests en verde; `tsc` limpio.

- [x] **Step 5: Commit**

```bash
git add api/openapi.json src/shared/api src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/telefonos src/modules/taller/imeis/apiNsys.ts src/test/server.ts
git commit -m "feat: contrato y API del stock de telefonos y del rastro"
```

---

### Task 11: Web — reglas puras del stock de teléfonos

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.ts`, `gestion-reparaciones-web/src/modules/telefonos/stock/nsys.ts`
- Test: `gestion-reparaciones-web/src/modules/telefonos/stock/modelo.test.ts`, `gestion-reparaciones-web/src/modules/telefonos/stock/nsys.test.ts`

**Interfaces:**
- Consumes: `MovilStock`, `MovilBuscado` y `EstadoNsys` (Task 10); `nombreModeloNsys`, `textoEstadoMovil` y `diaNsys` (Task 10).
- Produces:
  - **`modelo.ts`:**
    - tipos `Dimension = 'color' | 'grado' | 'bateria' | 'ubicacion' | 'estado'`, `Filtros`, `Opcion = { valor; cuenta }`, `Totales` y `Grupo = { clave; sinModelo; moviles; totales }`;
    - `FILTROS_VACIOS`, `BATERIA_NUEVA = 'Nueva (100 %)'`, `BATERIA_ORIGINAL = 'Original'` y `SIN_DATO = '—'`;
    - funciones `grupoDe(m)`, `valor(dim, m)`, `cuentaComoStock(m)`, `pasa(m, f, salvo?)`, `opciones(moviles, dim, f)`, `totales(moviles)`, `agrupar(moviles, verNoCuenta)`, `contarFiltros(f)`, `ordenarTabla(moviles)`, `textoBuscado(m)` y `muestraColor(color)` (color CSS de la muestra; gris si no se conoce).
  - **`nsys.ts`:** `minutosDesde(isoMadrid, ahora)`, `textoPildora(estado, ahora)` y `textoBanda(estado, ahora)`. `textoBanda` devuelve `string | null`: solo hay banda con `SIN_DATOS`.

- [x] **Step 1: Tests (fallan)**

Crea `src/modules/telefonos/stock/modelo.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { movil, movilBuscado } from '../test/fabrica'
import {
  agrupar, BATERIA_NUEVA, contarFiltros, FILTROS_VACIOS, grupoDe, muestraColor, opciones, ordenarTabla, pasa, textoBuscado, totales, valor,
} from './modelo'

const A = movil({ imeiSn: '350000000000001', color: 'Azul', grado: 'A', ubicacion: 'Listos' })
const B = movil({ imeiSn: '350000000000002', color: 'Azul', grado: 'A-', bateriaNueva: true, ubicacion: 'Taller · Glass', estado: 'DE_PEDIDO', dias: 30 })
const C = movil({ imeiSn: '350000000000003', color: 'Rosa', grado: 'n/a', ubicacion: 'En revisión' })
const D = movil({ imeiSn: '350000000000004', color: 'Azul', grado: 'B', ubicacion: 'Esperando pieza', estado: 'NO_CUENTA' })
const E = movil({ imeiSn: '350000000000005', modelo: '14', modeloMaestro: '14', ubicacion: 'Listos' })
const F = movil({ imeiSn: '350000000000006', modelo: null, modeloMaestro: null, capacidad: null })

describe('stock de teléfonos: grupos y totales', () => {
  it('modelo + capacidad, del más nuevo al más antiguo y los sin modelo al final', () => {
    expect(grupoDe(A)).toBe('iPhone 13 128GB')
    expect(agrupar([A, F, E, B], false).map((g) => g.clave)).toEqual(['iPhone 14 128GB', 'iPhone 13 128GB', 'Sin modelo en el maestro'])
  })

  it('lo que no cuenta como stock solo sale con el interruptor y nunca suma en los totales', () => {
    expect(agrupar([A, B, C, D], false)[0].moviles).toHaveLength(3)
    const conTodo = agrupar([A, B, C, D], true)[0]
    expect(conTodo.moviles).toHaveLength(4)
    expect(conTodo.totales).toEqual({ total: 3, libres: 2, dePedido: 1, listos: 1, taller: 1, revision: 1, noCuentan: 1 })
    expect(totales([D])).toEqual({ total: 0, libres: 0, dePedido: 0, listos: 0, taller: 0, revision: 0, noCuentan: 1 })
  })
})

describe('stock de teléfonos: filtros', () => {
  it('valores de cada dimensión', () => {
    expect(valor('bateria', B)).toBe(BATERIA_NUEVA)
    expect(valor('estado', B)).toBe('De pedido')
    expect(valor('ubicacion', movil({ ubicacion: null }))).toBe('—')
  })

  it('las opciones cuentan con los demás filtros puestos y los grados van en su orden', () => {
    const f = { ...FILTROS_VACIOS, color: new Set(['Azul']) }
    expect(opciones([A, B, C], 'grado', f)).toEqual([{ valor: 'A', cuenta: 1 }, { valor: 'A-', cuenta: 1 }, { valor: 'n/a', cuenta: 0 }])
    expect(opciones([A, B, C], 'color', f)).toEqual([{ valor: 'Azul', cuenta: 2 }, { valor: 'Rosa', cuenta: 1 }])
  })

  it('pasa los filtros salvo la dimensión que se está contando', () => {
    const f = { ...FILTROS_VACIOS, color: new Set(['Rosa']), ubicacion: new Set(['Listos']) }
    expect(pasa(A, f)).toBe(false)
    expect(pasa(A, f, 'color')).toBe(true)
    expect(contarFiltros(f)).toBe(2)
  })

  it('la tabla pone primero los de pedido y después por color y antigüedad', () => {
    expect(ordenarTabla([C, A, B]).map((m) => m.imeiSn)).toEqual(['350000000000002', '350000000000001', '350000000000003'])
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

Crea `src/modules/telefonos/stock/nsys.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { estadoNsys } from '../test/fabrica'
import { minutosDesde, textoBanda, textoPildora } from './nsys'

// 16/10/2026 13:43 en Madrid (CEST, UTC+2).
const AHORA = new Date('2026-10-16T11:43:00Z')

describe('aviso de NSYS', () => {
  it('minutos desde una hora de Madrid, sea cual sea la zona del navegador', () => {
    expect(minutosDesde('2026-10-16T13:31:00', AHORA)).toBe(12)
  })

  it('píldora según el nivel', () => {
    expect(textoPildora(estadoNsys(), AHORA)).toBe('NSYS: exportación de hoy 13:31 · hace 12 min')
    expect(textoPildora(estadoNsys({ nivel: 'RETRASO', ultimaExportacion: '2026-10-16T11:31:00' }), AHORA))
      .toBe('NSYS: última exportación hace 2 h 12 min (11:31)')
    expect(textoPildora(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: '2026-10-16T08:31:00' }), AHORA))
      .toBe('NSYS: sin datos nuevos desde hace 5 h')
    expect(textoPildora(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: null }), AHORA)).toBe('NSYS: sin datos todavía')
    expect(textoPildora(estadoNsys({ ultimaExportacion: '2026-10-15T22:31:00' }), AHORA)).toBe('NSYS: exportación del 15/10 22:31')
  })

  it('la banda solo sale sin datos', () => {
    expect(textoBanda(estadoNsys(), AHORA)).toBeNull()
    expect(textoBanda(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: '2026-10-16T08:31:00' }), AHORA)).toBe(
      'No llegan datos nuevos de NSYS desde las 08:31. El PC de producción o Drive pueden estar parados. Se enseñan los últimos datos recibidos.')
    expect(textoBanda(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: '2026-10-15T22:31:00' }), AHORA)).toBe(
      'No llegan datos nuevos de NSYS desde el 15/10 a las 22:31. El PC de producción o Drive pueden estar parados. Se enseñan los últimos datos recibidos.')
    expect(textoBanda(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: null }), AHORA)).toBe('Todavía no ha llegado ningún dato de NSYS.')
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock`
Expected: FAIL: no existen `./modelo` ni `./nsys`.

- [x] **Step 2: Implementación**

Crea `src/modules/telefonos/stock/modelo.ts`:

```ts
import type { MovilBuscado, MovilStock } from '@/shared/api/client'
import { diaNsys, nombreModeloNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'

/** Reglas puras de «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2): grupos modelo + capacidad
 *  con sus totales y filtros de un clic por color, grado, batería, ubicación y estado. */

export type Dimension = 'color' | 'grado' | 'bateria' | 'ubicacion' | 'estado'
export type Filtros = Record<Dimension, ReadonlySet<string>>
export type Opcion = { valor: string; cuenta: number }
export type Totales = { total: number; libres: number; dePedido: number; listos: number; taller: number; revision: number; noCuentan: number }
export type Grupo = { clave: string; sinModelo: boolean; moviles: MovilStock[]; totales: Totales }

export const FILTROS_VACIOS: Filtros = { color: new Set(), grado: new Set(), bateria: new Set(), ubicacion: new Set(), estado: new Set() }
export const BATERIA_NUEVA = 'Nueva (100 %)'
export const BATERIA_ORIGINAL = 'Original'
export const SIN_DATO = '—'
const ORDEN_GRADOS = ['A+', 'A', 'A-', 'B+', 'B', 'B-', 'C', 'MsA+', 'MsA', 'MsA-', 'MsB', 'MsC', 'Ms', 'ID', 'n/a']

/** Grupo de la lista de la izquierda: modelo + capacidad («iPhone 13 128GB»). */
export function grupoDe(m: MovilStock): string {
  return [nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad].filter(Boolean).join(' ')
}

export function valor(dim: Dimension, m: MovilStock): string {
  switch (dim) {
    case 'color': return m.color ?? SIN_DATO
    case 'grado': return m.grado || SIN_DATO
    case 'bateria': return m.bateriaNueva ? BATERIA_NUEVA : BATERIA_ORIGINAL
    case 'ubicacion': return m.ubicacion ?? SIN_DATO
    case 'estado': return textoEstadoMovil(m.estado)
  }
}

export const cuentaComoStock = (m: MovilStock): boolean => m.estado !== 'NO_CUENTA'

/** ¿Pasa los filtros? `salvo`: una dimensión que no se aplica (para contar las opciones de esa misma dimensión). */
export function pasa(m: MovilStock, f: Filtros, salvo?: Dimension): boolean {
  return (Object.keys(f) as Dimension[]).every((d) => d === salvo || f[d].size === 0 || f[d].has(valor(d, m)))
}

/** Las opciones de una dimensión y cuántos móviles quedarían con cada una, con los demás filtros puestos. */
export function opciones(moviles: MovilStock[], dim: Dimension, f: Filtros): Opcion[] {
  const cuentas = new Map<string, number>()
  for (const m of moviles) {
    const v = valor(dim, m)
    if (!cuentas.has(v)) cuentas.set(v, 0)
    if (pasa(m, f, dim)) cuentas.set(v, (cuentas.get(v) ?? 0) + 1)
  }
  const valores = [...cuentas.keys()]
  const indice = (g: string) => (ORDEN_GRADOS.includes(g) ? ORDEN_GRADOS.indexOf(g) : ORDEN_GRADOS.length)
  valores.sort(dim === 'grado' ? (a, b) => indice(a) - indice(b) || a.localeCompare(b, 'es') : (a, b) => a.localeCompare(b, 'es'))
  return valores.map((v) => ({ valor: v, cuenta: cuentas.get(v) ?? 0 }))
}

/** Totales de un grupo: solo cuentan los que cuentan como stock; los demás van aparte en `noCuentan`. */
export function totales(moviles: MovilStock[]): Totales {
  const t: Totales = { total: 0, libres: 0, dePedido: 0, listos: 0, taller: 0, revision: 0, noCuentan: 0 }
  for (const m of moviles) {
    if (!cuentaComoStock(m)) {
      t.noCuentan++
      continue
    }
    t.total++
    if (m.estado === 'DE_PEDIDO') t.dePedido++
    else t.libres++
    const u = m.ubicacion ?? ''
    if (u === 'Listos') t.listos++
    else if (u.startsWith('Taller')) t.taller++
    else if (u === 'En revisión' || u === 'Devolución') t.revision++
  }
  return t
}

/** Grupos modelo + capacidad, del modelo más nuevo al más antiguo; los que no tienen modelo, al final. */
export function agrupar(moviles: MovilStock[], verNoCuenta: boolean): Grupo[] {
  const mapa = new Map<string, MovilStock[]>()
  for (const m of moviles) {
    if (!verNoCuenta && !cuentaComoStock(m)) continue
    const clave = grupoDe(m)
    mapa.set(clave, [...(mapa.get(clave) ?? []), m])
  }
  return [...mapa.entries()]
    .map(([clave, lista]) => ({ clave, sinModelo: !lista[0].modelo && !lista[0].modeloMaestro, moviles: lista, totales: totales(lista) }))
    .sort((a, b) => Number(a.sinModelo) - Number(b.sinModelo) || b.clave.localeCompare(a.clave, 'es', { numeric: true }))
}

export function contarFiltros(f: Filtros): number {
  return Object.values(f).reduce((n, s) => n + s.size, 0)
}

/** Orden de la tabla: primero los de pedido (son los urgentes), después por color y los que llevan más días arriba. */
export function ordenarTabla(moviles: MovilStock[]): MovilStock[] {
  return [...moviles].sort((a, b) => Number(b.estado === 'DE_PEDIDO') - Number(a.estado === 'DE_PEDIDO')
    || (a.color ?? '').localeCompare(b.color ?? '', 'es') || (b.dias ?? 0) - (a.dias ?? 0))
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

Crea `src/modules/telefonos/stock/nsys.ts`:

```ts
import type { EstadoNsys } from '@/shared/api/client'

/** Textos del aviso de datos de NSYS (0.9.17, spec «Pieza 1» §5.7). El servidor da las horas de Madrid sin zona; para
 *  restarlas de «ahora» se lleva ahora a la hora de Madrid, sea cual sea la zona del navegador. */

const MADRID = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

/** «2026-10-16 13:43» de ahora en Madrid. */
function ahoraEnMadrid(ahora: Date): string {
  return MADRID.format(ahora).replace(',', '')
}

/** Una hora de pared (sin zona) a minutos desde una referencia fija, para restar dos horas de Madrid. */
function aMinutos(fechaHora: string): number {
  const [d, h] = fechaHora.replace('T', ' ').split(' ')
  const [anio, mes, dia] = d.split('-').map(Number)
  const [hora, minuto] = h.split(':').map(Number)
  return Date.UTC(anio, mes - 1, dia, hora, minuto) / 60_000
}

export function minutosDesde(isoMadrid: string, ahora: Date = new Date()): number {
  return Math.max(0, aMinutos(ahoraEnMadrid(ahora)) - aMinutos(isoMadrid))
}

const hora = (iso: string) => iso.slice(11, 16)
const diaMes = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
const esHoy = (iso: string, ahora: Date) => iso.slice(0, 10) === ahoraEnMadrid(ahora).slice(0, 10)

export function textoPildora(e: EstadoNsys | null | undefined, ahora: Date = new Date()): string {
  if (!e?.ultimaExportacion) return 'NSYS: sin datos todavía'
  const iso = e.ultimaExportacion
  const minutos = minutosDesde(iso, ahora)
  if (e.nivel === 'SIN_DATOS') {
    const horas = Math.floor(minutos / 60)
    return horas > 0 ? `NSYS: sin datos nuevos desde hace ${horas} h` : `NSYS: sin datos nuevos desde hace ${minutos} min`
  }
  if (e.nivel === 'RETRASO') return `NSYS: última exportación hace ${Math.floor(minutos / 60)} h ${minutos % 60} min (${hora(iso)})`
  return esHoy(iso, ahora) ? `NSYS: exportación de hoy ${hora(iso)} · hace ${minutos} min` : `NSYS: exportación del ${diaMes(iso)} ${hora(iso)}`
}

/** La banda roja de la cabecera: solo cuando no llegan datos (SIN_DATOS). */
export function textoBanda(e: EstadoNsys | null | undefined, ahora: Date = new Date()): string | null {
  if (!e || e.nivel !== 'SIN_DATOS') return null
  if (!e.ultimaExportacion) return 'Todavía no ha llegado ningún dato de NSYS.'
  const iso = e.ultimaExportacion
  const desde = esHoy(iso, ahora) ? `las ${hora(iso)}` : `el ${diaMes(iso)} a las ${hora(iso)}`
  return `No llegan datos nuevos de NSYS desde ${desde}. El PC de producción o Drive pueden estar parados. Se enseñan los últimos datos recibidos.`
}
```

> `Intl` con `sv-SE` da `2026-10-16 13:43`, el formato ISO con espacio. Así no depende de la zona del navegador ni de
> la del entorno de pruebas.

- [x] **Step 3: Ejecuta los tests (pasan)**

Run: `npx vitest run src/modules/telefonos/stock && npx tsc -b`
Expected: 11 tests en verde; `tsc` limpio.

- [x] **Step 4: Commit**

```bash
git add src/modules/telefonos/stock
git commit -m "feat: reglas puras del stock de telefonos (grupos, totales, filtros y aviso de NSYS)"
```

---

### Task 12: Web — pantalla «Stock de teléfonos», grupo «Teléfonos» y ruta

**Files:**
- Create: `gestion-reparaciones-web/src/modules/telefonos/rutas.tsx`, `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.tsx`, `ListaModelos.tsx`, `FiltrosMoviles.tsx`, `PildoraNsys.tsx`, `BuscadorImei.tsx`, `columnas.tsx`
- Modify: `gestion-reparaciones-web/src/shared/lib/enlaces.ts`, `gestion-reparaciones-web/src/app/shell/navegacion.ts`, `gestion-reparaciones-web/src/app/shell/preferencias.ts`, `gestion-reparaciones-web/src/app/router.tsx`
- Test: `gestion-reparaciones-web/src/modules/telefonos/rutas.test.tsx`, `gestion-reparaciones-web/src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, `gestion-reparaciones-web/src/app/shell/navegacion.test.ts` (modificado)

**Interfaces:**
- Consumes: las Tasks 10 y 11; `DataTable`, `MultiSelect`, `PildoraContador` y `CLASES_PILDORA` (`shared/ui`); `cn`.
- Produces:
  - `enlacesTelefonos(sesion)`: `[{ to: '/telefonos/stock', label: 'Stock de teléfonos', icono: Smartphone }]` para SUPERTECNICO y ADMIN, vacío para el resto.
  - `RequiereAccesoTelefonos`: aviso `MSG_SIN_PERMISOS` y vuelta a `/reparaciones`.
  - `StockTelefonosPage`.
  - `IdGrupoNav` con `'telefonos'`.

- [x] **Step 1: Tests (fallan)**

Crea `src/modules/telefonos/rutas.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { Smartphone } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { renderConRouter, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { enlacesTelefonos, RequiereAccesoTelefonos } from './rutas'

describe('rutas del módulo de teléfonos', () => {
  it('«Stock de teléfonos» solo para supertécnico y admin', () => {
    expect(enlacesTelefonos(SESION_SUPER)).toEqual([{ to: '/telefonos/stock', label: 'Stock de teléfonos', icono: Smartphone }])
    expect(enlacesTelefonos(SESION_ADMIN)).toHaveLength(1)
    expect(enlacesTelefonos(SESION_TEC)).toEqual([])
    expect(enlacesTelefonos(null)).toEqual([])
  })

  it('el técnico que entra por la dirección recibe el aviso y vuelve a /reparaciones', async () => {
    renderConRouter([
      { element: <RequiereAccesoTelefonos />, children: [{ path: '/telefonos/stock', element: <p>stock</p> }] },
      { path: '/reparaciones', element: <p>reparaciones</p> },
    ], { sesion: SESION_TEC, ruta: '/telefonos/stock' })
    expect(await screen.findByText('reparaciones')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
  })

  it('el supertécnico entra', async () => {
    renderConRouter([{ element: <RequiereAccesoTelefonos />, children: [{ path: '/telefonos/stock', element: <p>stock</p> }] }],
      { sesion: SESION_SUPER, ruta: '/telefonos/stock' })
    expect(await screen.findByText('stock')).toBeInTheDocument()
  })
})
```

Crea `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { estadoNsys, movil, movilBuscado } from '../test/fabrica'
import { StockTelefonosPage } from './StockTelefonosPage'

const STOCK = [
  movil({ imeiSn: '350000000000001', color: 'Azul', grado: 'A', ubicacion: 'Listos' }),
  movil({ imeiSn: '350000000000002', color: 'Azul', grado: 'A-', bateriaNueva: true, ubicacion: 'Taller · Glass', estado: 'DE_PEDIDO' }),
  movil({ imeiSn: '350000000000003', color: 'Rosa', grado: 'n/a', ubicacion: 'En revisión' }),
  movil({ imeiSn: '350000000000004', color: 'Azul', grado: 'B', ubicacion: 'Esperando pieza', estado: 'NO_CUENTA' }),
  movil({ imeiSn: '350000000000005', modelo: '14', modeloMaestro: '14', ubicacion: 'Listos' }),
]

beforeEach(() => {
  server.use(
    http.get('*/api/stock-telefonos', () => HttpResponse.json(STOCK)),
    http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
  )
})

const abrir = () => renderConProviders(<StockTelefonosPage />, {
  sesion: SESION_SUPER, ruta: '/telefonos/stock',
  rutas: <Route path="/reparaciones/imeis/:imei" element={<p>Detalle del IMEI</p>} />,
})
const filas = () => screen.getAllByRole('row').slice(1)

describe('Stock de teléfonos (spec pieza 1 §9.2)', () => {
  it('cabecera con el total en stock, la píldora de NSYS y los modelos con sus totales', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Stock de teléfonos' })).toBeInTheDocument()
    expect(await screen.findByText('4 móviles en stock')).toBeInTheDocument()
    expect(screen.getByText(/^NSYS: /)).toHaveAttribute('role', 'status')
    const modelos = within(screen.getByRole('navigation', { name: 'Modelos' })).getAllByRole('button')
    expect(modelos.map((b) => b.textContent?.match(/^iPhone \d+ \d+GB/)?.[0])).toEqual(['iPhone 14 128GB', 'iPhone 13 128GB'])
    expect(modelos[1]).toHaveTextContent('2 libres')
  })

  it('al elegir un modelo salen sus móviles y los filtros de un clic se combinan', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: /^iPhone 13 128GB/ }))
    expect(filas()).toHaveLength(3)
    expect(filas()[0]).toHaveTextContent('350000000000002')
    expect(filas()[0]).toHaveClass('border-l-fila-urgente-brd')

    await user.click(within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Azul/ }))
    expect(filas()).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Ubicación' }))
    await user.click(screen.getByRole('checkbox', { name: 'Listos (1)' }))
    expect(filas()).toHaveLength(1)
    expect(within(screen.getByRole('group', { name: 'Color' })).getByRole('button', { name: /^Rosa/ })).toBeDisabled()

    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Quitar filtros (2)' }))
    expect(filas()).toHaveLength(3)
  })

  it('el interruptor enseña lo que no cuenta como stock, en gris y fuera de los totales', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: /^iPhone 13 128GB/ }))
    await user.click(screen.getByRole('switch', { name: 'Ver también lo que no cuenta como stock' }))
    expect(filas()).toHaveLength(4)
    expect(screen.getByText('NO CUENTA')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^iPhone 13 128GB/ })).toHaveTextContent('1 no cuentan')
    expect(screen.getByText('4 móviles en stock')).toBeInTheDocument()
  })

  it('el IMEI de una fila abre su detalle', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: 'Abrir 350000000000005' }))
    expect(await screen.findByText('Detalle del IMEI')).toBeInTheDocument()
  })

  it('el buscador encuentra cualquier IMEI y abre su detalle', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      movilBuscado({ imeiSn: '353918700112233', modelo: '11', modeloMaestro: '11', capacidad: '64GB', color: 'Negro', estado: 'VENDIDO', ubicacion: null, fechaVenta: '2023-05-02' }),
    ])))
    abrir()
    await user.type(await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }), '3539')
    await user.click(await screen.findByRole('button', { name: /353918700112233 · iPhone 11 64GB Negro · Vendido el 02\/05\/2023/ }))
    expect(await screen.findByText('Detalle del IMEI')).toBeInTheDocument()
  })

  it('sin datos de NSYS sale la banda roja', async () => {
    server.use(http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys({ nivel: 'SIN_DATOS', ultimaExportacion: null }))))
    abrir()
    expect(await screen.findByRole('alert')).toHaveTextContent('Todavía no ha llegado ningún dato de NSYS.')
  })
})
```

En `src/app/shell/navegacion.test.ts`:
- en el test del supertécnico, la lista de ids pasa a `['reparaciones', 'stock', 'telefonos', 'estadisticas', 'clientes']` y se añade:
  `expect(grupos[2].enlaces.map((e) => [e.label, e.to])).toEqual([['Stock de teléfonos', '/telefonos/stock']])`;
- el test del técnico no cambia: no tiene grupo «Teléfonos»;
- el de «sin sesión» tampoco cambia.

Run: `npx vitest run src/modules/telefonos src/app/shell/navegacion.test.ts`
Expected: FAIL: no existen `./rutas` ni `./StockTelefonosPage`, y `navegacion` no tiene el grupo.

- [x] **Step 2: Implementación**

Crea `src/modules/telefonos/rutas.tsx`:

```tsx
import { useEffect } from 'react'
import { Smartphone } from 'lucide-react'
import { Navigate, Outlet } from 'react-router'
import { MSG_SIN_PERMISOS } from '@/shared/api/errors'
import type { Enlace } from '@/shared/lib/enlaces'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdminOSuperTecnico, type Sesion } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'

/** Grupo «Teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §3): solo SUPERTECNICO y ADMIN. El TECNICO (o sin
 *  sesión) no tiene grupo. */
// eslint-disable-next-line react-refresh/only-export-components -- la función vive con las rutas que la usan, patrón del proyecto
export function enlacesTelefonos(sesion: Sesion | null): Enlace[] {
  return esAdminOSuperTecnico(sesion) ? [{ to: '/telefonos/stock', label: 'Stock de teléfonos', icono: Smartphone }] : []
}

/** El TECNICO que llega por la dirección recibe el aviso genérico y vuelve a /reparaciones. Mismo patrón que
 *  RequiereAccesoPedidos (modules/almacen/rutas.tsx), copiado porque un módulo no importa de otro. */
export function RequiereAccesoTelefonos() {
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const permitido = esAdminOSuperTecnico(sesion)
  useEffect(() => {
    if (!permitido) mostrarError(MSG_SIN_PERMISOS)
  }, [permitido, mostrarError])
  if (!permitido) return <Navigate to="/reparaciones" replace />
  return <Outlet />
}
```

Crea `src/modules/telefonos/stock/PildoraNsys.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { useEstadoNsys } from '../api'
import { textoBanda, textoPildora } from './nsys'

const CLASES: Record<string, string> = {
  OK: 'border-borde-input bg-superficie text-azul-gris',
  RETRASO: 'border-ambar bg-form-agotado-bg text-form-agotado-text',
  SIN_DATOS: 'border-fila-urgente-brd bg-urgente-bg text-fila-urgente-brd',
}
const PUNTO: Record<string, string> = { OK: 'bg-verde-ok', RETRASO: 'bg-ambar', SIN_DATOS: 'bg-fila-urgente-brd' }

/** Píldora de la cabecera con la hora de la última exportación de NSYS (spec §5.7). El motivo del último fallo, al
 *  pasar el ratón. */
export function PildoraNsys() {
  const { data, isPending } = useEstadoNsys()
  const nivel = data?.nivel ?? 'SIN_DATOS'
  return (
    <span role="status" title={data?.motivoFallo ?? undefined}
      className={cn('ml-auto inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11.5px]', CLASES[nivel] ?? CLASES.OK)}>
      <span aria-hidden className={cn('h-2 w-2 rounded-full', PUNTO[nivel] ?? PUNTO.OK)} />
      {isPending ? 'NSYS: comprobando…' : textoPildora(data)}
    </span>
  )
}

/** La banda roja cuando no llegan datos de NSYS. */
export function BandaNsys() {
  const { data } = useEstadoNsys()
  const texto = textoBanda(data)
  if (!texto) return null
  return (
    <div role="alert" className="mb-2.5 rounded-lg border border-fila-urgente-brd bg-urgente-bg px-3 py-2 text-[12.5px] text-[#7f1d1d]">
      {texto}
    </div>
  )
}
```

Crea `src/modules/telefonos/stock/BuscadorImei.tsx`:

```tsx
import { useState } from 'react'
import { useBuscarMoviles } from '@/shared/api/nsys'
import { nombreModeloNsys } from '@/shared/lib/telefonoNsys'
import { textoBuscado } from './modelo'

/** Buscador de IMEI en todos los móviles del espejo, también los vendidos: con 4 caracteres o más sugiere hasta 10; al
 *  elegir uno (o Enter con el IMEI exacto) se abre su detalle. */
export function BuscadorImei({ onElegir }: { onElegir: (imeiSn: string) => void }) {
  const [texto, setTexto] = useState('')
  const [abierto, setAbierto] = useState(false)
  const limpio = texto.trim()
  const { data: encontrados = [], isFetching } = useBuscarMoviles(limpio)
  const elegir = (imeiSn: string) => {
    setAbierto(false)
    onElegir(imeiSn)
  }
  return (
    <div className="relative">
      <input type="search" aria-label="Buscar IMEI en todos los móviles" placeholder="Buscar IMEI en todos los móviles…" value={texto}
        onChange={(e) => {
          setTexto(e.target.value.replace(/[^A-Za-z0-9]/g, ''))
          setAbierto(true)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setAbierto(false)
          if (e.key === 'Enter') {
            const exacto = encontrados.find((m) => m.imeiSn === limpio)
            if (exacto) elegir(exacto.imeiSn)
          }
        }}
        className="h-9 w-[240px] rounded border border-borde-input bg-superficie px-2.5 text-[12px] text-azul-medio" />
      {abierto && limpio.length >= 4 && (
        <ul aria-label="Móviles encontrados" className="absolute top-10 left-0 z-20 w-[440px] rounded-lg border border-pill-borde bg-superficie p-1 shadow-lg">
          {encontrados.length === 0 && !isFetching && <li className="px-2.5 py-1.5 text-[12px] text-azul-gris">Ningún móvil con ese IMEI</li>}
          {encontrados.map((m) => (
            <li key={m.imeiSn}>
              <button type="button" onClick={() => elegir(m.imeiSn)}
                className="w-full cursor-pointer rounded px-2.5 py-1.5 text-left text-[12px] text-azul-medio hover:bg-seleccion-suave">
                {`${m.imeiSn} · ${[nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad, m.color].filter(Boolean).join(' ')} · ${textoBuscado(m)}`}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
```

Crea `src/modules/telefonos/stock/ListaModelos.tsx`:

```tsx
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import type { Grupo } from './modelo'

const MINI = 'inline-block min-w-5 rounded-lg px-1.5 text-center text-[10.5px] font-bold'
const VERDE = 'bg-fila-recibido-bg text-fila-recibido-brd'
const AZUL = 'bg-tipo-reparacion-bg text-tipo-reparacion-text'
const AMBAR = 'bg-fila-solicitud-bg text-fila-solicitud-brd'
const ROJO = 'bg-urgente-bg text-fila-urgente-brd'

function Mini({ clase, titulo, children }: { clase: string; titulo?: string; children: ReactNode }) {
  return <span title={titulo} className={cn(MINI, clase)}>{children}</span>
}

type Props = {
  grupos: Grupo[]
  elegido: string | null
  texto: string
  verNoCuenta: boolean
  onTexto: (texto: string) => void
  onElegir: (clave: string) => void
}

/** La columna de la izquierda: modelo + capacidad con el total en stock y cuatro números (listos, taller, revisión y de
 *  pedido) más los libres (opción C del prototipo, con los totales de la B). */
export function ListaModelos({ grupos, elegido, texto, verNoCuenta, onTexto, onElegir }: Props) {
  return (
    <nav aria-label="Modelos" className="max-h-[calc(100vh-260px)] overflow-auto rounded-md bg-superficie">
      <input aria-label="Buscar modelo" placeholder="Buscar modelo…" value={texto} onChange={(e) => onTexto(e.target.value)}
        className="m-2 h-[34px] w-[calc(100%-16px)] rounded border border-borde-input px-2.5 text-[12px]" />
      <div aria-hidden className="flex flex-wrap gap-1.5 border-b border-fila-sep px-3 pb-2">
        <Mini clase={VERDE}>listos</Mini><Mini clase={AZUL}>taller</Mini><Mini clase={AMBAR}>revisión</Mini><Mini clase={ROJO}>de pedido</Mini>
      </div>
      {grupos.length === 0 && <p className="p-4 text-[12px] text-azul-gris">Ningún modelo</p>}
      {grupos.map((g) => {
        const t = g.totales
        const activo = g.clave === elegido
        return (
          <button key={g.clave} type="button" aria-pressed={activo} onClick={() => onElegir(g.clave)}
            className={cn('flex w-full cursor-pointer items-center gap-2 border-b border-fila-sep px-3 py-2 text-left',
              activo ? 'bg-azul-noche text-texto-nav-activo' : 'text-azul-medio hover:bg-seleccion-suave')}>
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-bold">{g.clave}</span>
              <span className="mt-0.5 flex flex-wrap items-center gap-1">
                <Mini clase={VERDE} titulo="Listos">{t.listos}</Mini>
                <Mini clase={AZUL} titulo="En el taller">{t.taller}</Mini>
                <Mini clase={AMBAR} titulo="En revisión">{t.revision}</Mini>
                <Mini clase={ROJO} titulo="De pedido">{t.dePedido}</Mini>
                <span className={cn('text-[11px]', activo ? 'text-[#c9d3e6]' : 'text-azul-gris')}>
                  · {t.libres} libres{verNoCuenta && t.noCuentan > 0 ? ` · ${t.noCuentan} no cuentan` : ''}
                </span>
              </span>
            </span>
            <span className="text-[15px] font-extrabold">{t.total}</span>
          </button>
        )
      })}
    </nav>
  )
}
```

Crea `src/modules/telefonos/stock/FiltrosMoviles.tsx`:

```tsx
import type { MovilStock } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { contarFiltros, FILTROS_VACIOS, muestraColor, opciones, type Dimension, type Filtros } from './modelo'

type Props = { moviles: MovilStock[]; filtros: Filtros; onCambiar: (f: Filtros) => void }

/** Filtros de la derecha: chips de color y grado con su número y desplegables de batería, ubicación y estado. Cada número
 *  dice cuántos móviles quedarían con los demás filtros puestos. Los que darían cero, apagados. */
export function FiltrosMoviles({ moviles, filtros, onCambiar }: Props) {
  const alternar = (dim: Dimension, v: string) => {
    const s = new Set(filtros[dim])
    if (s.has(v)) s.delete(v)
    else s.add(v)
    onCambiar({ ...filtros, [dim]: s })
  }
  const activos = contarFiltros(filtros)
  const desplegable = (dim: Dimension, titulo: string, todas: string) => (
    <MultiSelect opciones={opciones(moviles, dim, filtros)} clave={(o) => o.valor} etiqueta={(o) => `${o.valor} (${o.cuenta})`}
      seleccion={new Set(filtros[dim])} onChange={(s) => onCambiar({ ...filtros, [dim]: s })}
      textoVacio={`${titulo}: ${todas}`} textoPlural={(n) => `${titulo}: ${n}`} etiquetaAccesible={titulo} className="min-w-[150px]" />
  )
  return (
    <div className="mb-2.5">
      {(['color', 'grado'] as const).map((dim) => (
        <div key={dim} role="group" aria-label={dim === 'color' ? 'Color' : 'Grado'} className="mb-2 flex flex-wrap items-center gap-1.5">
          <span className="w-[52px] text-[10.5px] font-extrabold tracking-wider text-azul-gris uppercase">{dim === 'color' ? 'Color' : 'Grado'}</span>
          {opciones(moviles, dim, filtros).map((o) => {
            const marcado = filtros[dim].has(o.valor)
            const apagado = o.cuenta === 0 && !marcado
            return (
              <button key={o.valor} type="button" aria-pressed={marcado} disabled={apagado} onClick={() => alternar(dim, o.valor)}
                className={cn('inline-flex cursor-pointer items-center gap-1 rounded-full border px-2.5 py-0.5 text-[12px] font-bold',
                  marcado ? 'border-azul-noche bg-azul-noche text-superficie' : 'border-pill-borde bg-pill-bg text-azul-gris',
                  apagado && 'cursor-default opacity-35')}>
                {dim === 'color' && <span aria-hidden className="h-2.5 w-2.5 rounded-full border border-pill-borde" style={{ background: muestraColor(o.valor) }} />}
                {o.valor}<span className="font-normal opacity-80">{o.cuenta}</span>
              </button>
            )
          })}
        </div>
      ))}
      <div className="flex flex-wrap items-center gap-2">
        {desplegable('bateria', 'Batería', 'todas')}
        {desplegable('ubicacion', 'Ubicación', 'todas')}
        {desplegable('estado', 'Estado', 'todos')}
        {activos > 0 && (
          <button type="button" onClick={() => onCambiar(FILTROS_VACIOS)} className="ml-auto cursor-pointer text-[12px] font-bold text-texto-accion hover:underline">
            Quitar filtros ({activos})
          </button>
        )}
      </div>
    </div>
  )
}
```

Crea `src/modules/telefonos/stock/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { MovilStock } from '@/shared/api/client'
import { clasesUbicacion, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { muestraColor } from './modelo'

/** Columnas de la tabla de móviles: el SKU partido (color, grado, batería, eSIM) y completo, ubicación, estado y días. El
 *  IMEI es un botón que abre el detalle. */
export function columnasMoviles(abrir: (imeiSn: string) => void): ColumnDef<MovilStock>[] {
  return [
    {
      id: 'imei', header: 'IMEI', size: 150, maxSize: 160,
      cell: ({ row }) => (
        <button type="button" aria-label={`Abrir ${row.original.imeiSn}`} onClick={(e) => { e.stopPropagation(); abrir(row.original.imeiSn) }}
          className="cursor-pointer font-mono text-[11.5px] text-azul-noche hover:underline">{row.original.imeiSn}</button>
      ),
    },
    {
      id: 'color', header: 'Color', size: 100,
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="h-2.5 w-2.5 rounded-full border border-pill-borde" style={{ background: muestraColor(row.original.color) }} />
          {row.original.color ?? '—'}
        </span>
      ),
    },
    {
      id: 'grado', header: 'Grado', size: 70, maxSize: 80,
      cell: ({ row }) => (
        <span className={cn(CLASES_PILDORA, row.original.grado === 'n/a' ? 'bg-fila-solicitud-bg text-fila-solicitud-brd' : 'bg-badge-neutro-bg text-azul-medio')}>
          {row.original.grado}
        </span>
      ),
    },
    { id: 'bateria', header: 'Batería', size: 80, accessorFn: (m) => (m.bateriaNueva ? 'Nueva' : 'Original') },
    { id: 'esim', header: 'eSIM', size: 55, maxSize: 60, accessorFn: (m) => (m.esEsim ? 'Sí' : '') },
    { id: 'sku', header: 'SKU', size: 140, cell: ({ row }) => <span className="font-mono text-[11.5px]">{row.original.sku ?? '—'}</span> },
    {
      id: 'ubicacion', header: 'Ubicación', size: 160,
      cell: ({ row }) => (
        <>
          <span className={cn(CLASES_PILDORA, clasesUbicacion(row.original.ubicacion))}>{row.original.ubicacion ?? '—'}</span>
          {row.original.estado === 'NO_CUENTA' && <span className="ml-1 text-[10px] font-extrabold text-azul-gris">NO CUENTA</span>}
        </>
      ),
    },
    {
      id: 'estado', header: 'Estado', size: 110,
      cell: ({ row }) => row.original.estado === 'DE_PEDIDO'
        ? <span className="font-bold text-fila-urgente-brd">De pedido</span>
        : <span className="text-azul-gris">{textoEstadoMovil(row.original.estado)}</span>,
    },
    { id: 'dias', header: 'Días', size: 55, maxSize: 60, accessorFn: (m) => m.dias ?? '' },
  ]
}
```

Crea `src/modules/telefonos/stock/StockTelefonosPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { cn } from '@/shared/lib/utils'
import { DataTable } from '@/shared/ui/DataTable'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useStockTelefonos } from '../api'
import { BuscadorImei } from './BuscadorImei'
import { columnasMoviles } from './columnas'
import { FiltrosMoviles } from './FiltrosMoviles'
import { ListaModelos } from './ListaModelos'
import { agrupar, cuentaComoStock, FILTROS_VACIOS, ordenarTabla, pasa, type Filtros } from './modelo'
import { BandaNsys, PildoraNsys } from './PildoraNsys'

/** «Stock de teléfonos» (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.2; opción C del prototipo aprobado):
 *  modelo + capacidad a la izquierda, sus móviles a la derecha con filtros de un clic. Solo lectura: los datos vienen del
 *  espejo de NSYS. Un móvil abre su detalle de IMEIs, con la ficha de NSYS y el rastro. */
export function StockTelefonosPage() {
  const navigate = useNavigate()
  const { data: moviles = [] } = useStockTelefonos()
  const [verNoCuenta, setVerNoCuenta] = useState(false)
  const [textoModelo, setTextoModelo] = useState('')
  const [elegido, setElegido] = useState<string | null>(null)
  const [filtros, setFiltros] = useState<Filtros>(FILTROS_VACIOS)
  const grupos = useMemo(() => agrupar(moviles, verNoCuenta), [moviles, verNoCuenta])
  const visibles = grupos.filter((g) => g.clave.toLowerCase().includes(textoModelo.trim().toLowerCase()))
  const grupo = grupos.find((g) => g.clave === elegido) ?? grupos[0] ?? null
  const filas = useMemo(() => (grupo ? ordenarTabla(grupo.moviles.filter((m) => pasa(m, filtros))) : []), [grupo, filtros])
  const enStock = moviles.filter(cuentaComoStock).length
  const abrir = (imeiSn: string) => navigate(`/reparaciones/imeis/${encodeURIComponent(imeiSn)}`)
  const columnas = useMemo(() => columnasMoviles(abrir), [navigate]) // eslint-disable-line react-hooks/exhaustive-deps -- abrir solo depende de navigate
  return (
    <div className="p-10 max-[1500px]:p-5">
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Stock de teléfonos</h1>
        <PildoraContador texto={`${enStock} ${enStock === 1 ? 'móvil' : 'móviles'} en stock`} />
        <PildoraNsys />
      </div>
      <BandaNsys />
      <div className="mb-3 flex flex-wrap items-center gap-4">
        <BuscadorImei onElegir={abrir} />
        <button type="button" role="switch" aria-checked={verNoCuenta} onClick={() => setVerNoCuenta((v) => !v)}
          className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-azul-medio">
          <span className={cn('relative h-[18px] w-[34px] rounded-full transition-colors', verNoCuenta ? 'bg-verde-ok' : 'bg-pill-borde')}>
            <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-superficie transition-[left]', verNoCuenta ? 'left-[18px]' : 'left-[2px]')} />
          </span>
          Ver también lo que no cuenta como stock
        </button>
      </div>
      <div className="grid grid-cols-[280px_1fr] items-start gap-3 max-[1500px]:grid-cols-[230px_1fr]">
        <ListaModelos grupos={visibles} elegido={grupo?.clave ?? null} texto={textoModelo} verNoCuenta={verNoCuenta} onTexto={setTextoModelo}
          onElegir={(clave) => { setElegido(clave); setFiltros(FILTROS_VACIOS) }} />
        <section aria-label="Móviles del modelo" className="min-w-0 rounded-md bg-superficie p-3">
          {grupo && (
            <>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-base font-bold text-azul-noche">{grupo.clave}</h2>
                <PildoraContador texto={`${filas.length} de ${grupo.moviles.length}`} />
              </div>
              <FiltrosMoviles moviles={grupo.moviles} filtros={filtros} onCambiar={setFiltros} />
              <DataTable columns={columnas} data={filas} vacio="Ningún móvil con estos filtros" ajuste="fluido" alturaMax="calc(100vh - 360px)"
                getRowId={(m) => `${m.imeiSn}|${m.lote}`} onAbrir={(m) => abrir(m.imeiSn)}
                filaClase={(m) => cn('border-l-4', m.estado === 'DE_PEDIDO' ? 'border-l-fila-urgente-brd' : 'border-l-transparent',
                  m.estado === 'NO_CUENTA' && 'text-texto-suave')} />
              <p className="mt-2 text-[11.5px] text-azul-gris">El IMEI abre el detalle del móvil. El borde rojo marca los que están de pedido.</p>
            </>
          )}
          {!grupo && <p className="p-4 text-[12px] text-azul-gris">No hay móviles en stock.</p>}
        </section>
      </div>
    </div>
  )
}
```

> Si `npm run lint` rechaza el comentario `eslint-disable-line` de `columnas`, envuelve `abrir` en `useCallback` con
> `[navigate]` y pasa `[abrir]` a `useMemo`. Es el patrón de `ImeisPage.tsx`.

**Navegación, preferencias y ruta:**
- **`src/shared/lib/enlaces.ts`:** `IdGrupoNav` pasa a `'reparaciones' | 'stock' | 'telefonos' | 'estadisticas' | 'clientes' | 'gestion'`.
- **`src/app/shell/preferencias.ts`:** `IDS_GRUPOS` pasa a `['reparaciones', 'stock', 'telefonos', 'estadisticas', 'clientes', 'gestion']`.
- **`src/app/shell/navegacion.ts`:**
  - importa `enlacesTelefonos` de `@/modules/telefonos/rutas`;
  - tras el grupo `stock`, añade:

    ```ts
      const telefonos = enlacesTelefonos(sesion)
      if (telefonos.length > 0) grupos.splice(2, 0, { id: 'telefonos', titulo: 'Teléfonos', enlaces: telefonos })
    ```

    justo después de construir `grupos` y antes del `if (esAdmin(sesion))`;
  - actualiza su Javadoc: «Stock, Teléfonos (0.9.17: solo SUPERTECNICO y ADMIN), Estadísticas…».
- **`src/app/router.tsx`:**
  - importa `RequiereAccesoTelefonos` de `@/modules/telefonos/rutas` y `StockTelefonosPage` de `@/modules/telefonos/stock/StockTelefonosPage`;
  - tras el bloque de `RequiereAccesoPedidos`, añade:

    ```tsx
              {
                // 0.9.17: el stock de teléfonos solo para SUPERTECNICO y ADMIN; el TECNICO por URL recibe el aviso y vuelve a /reparaciones.
                element: <RequiereAccesoTelefonos />,
                children: [{ path: '/telefonos/stock', element: <StockTelefonosPage /> }],
              },
    ```

- [x] **Step 3: Ejecuta los tests (pasan) y las pruebas del shell**

Run: `npx vitest run src/modules/telefonos src/app/shell`
Expected: todo en verde.
- Si `SubNav.test.tsx` o `AppLayout.test.tsx` comparan la lista exacta de cabeceras o enlaces de una sesión SUPERTECNICO o ADMIN, añade `'Teléfonos'` tras `'Stock'` en las cabeceras y `'Stock de teléfonos'` tras `'Proveedores'` en los enlaces (con la columna plegada, `'Teléfonos · Stock de teléfonos'` tras `'Stock · Proveedores'`).
- Ajusta también el número de líneas entre grupos.
- No toques las listas del TECNICO ni las de sin sesión.

Run: `npx tsc -b && npm run lint`
Expected: limpio.

- [x] **Step 4: Commit**

```bash
git add src/modules/telefonos src/shared/lib/enlaces.ts src/app
git commit -m "feat: pantalla Stock de telefonos y grupo Telefonos para supertecnico y admin"
```

---

### Task 13: Web — IMEIs: ficha de NSYS, panel del rastro y móviles sin trabajos

**Files:**
- Create: `gestion-reparaciones-web/src/modules/taller/imeis/rastro.ts`, `FichaMovil.tsx`, `PanelRastro.tsx`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/ImeiDetallePage.tsx`, `ImeisPage.tsx`, `gestion-reparaciones-web/src/modules/taller/test/fabrica.ts`
- Test: `gestion-reparaciones-web/src/modules/taller/imeis/rastro.test.ts`, `ImeiDetallePage.test.tsx` y `ImeisPage.test.tsx` (tests nuevos)

**Interfaces:**
- Consumes:
  - `useFichaMovil` y `useRastro` (Task 10, `apiNsys.ts`) y `useBuscarMoviles` (Task 10, `shared/api/nsys.ts`);
  - `nombreModeloNsys`, `clasesUbicacion` y `textoEstadoMovil` (Task 10);
  - el mecanismo de la página: `setSeleccionada` + `setPeticionDesplazamiento`.
- Produces:
  - `fechaRastro(iso, soloDia)`, `verEvento(e)` → `{ fecha, titulo, detalle, color }`, `hayAntesDelHistorico(eventos)` y `NOTA_ANTES_DEL_HISTORICO`;
  - componentes `FichaMovil` y `PanelRastro`;
  - en la fábrica de tests del taller, `fichaMovil(parcial)` y `eventoRastro(parcial)`.

- [x] **Step 1: Tests (fallan)**

En `src/modules/taller/test/fabrica.ts`, añade al final:

```ts
/** 0.9.17: ficha de NSYS y eventos del rastro (datos inventados). */
export function fichaMovil(p: Partial<FichaMovil> = {}): FichaMovil {
  return {
    imeiSn: '351900000000041', lote: 'BT-9001', modelo: '16', modeloMaestro: '16', capacidad: '128GB', color: 'Negro',
    grado: 'A', bateriaNueva: true, gradoAnterior: 'C', sku: 'i16128NegroA100', ubicacion: 'Listos', estado: 'DE_PEDIDO',
    dias: 12, entradas: 1, fechaVenta: null, ...p,
  }
}

export function eventoRastro(p: Partial<EventoRastro> = {}): EventoRastro {
  return {
    fecha: '2026-09-12T13:01:00', soloDia: false, fuente: 'NSYS', tipo: 'UBICACION', lote: 'BT-9001', de: 'Taller · Glass',
    a: 'Listos', aNsys: 'CAJA LISTOS', idRep: null, tecnico: null, categoria: null, ...p,
  }
}
```

y amplía su `import type { … } from '@/shared/api/client'` con `EventoRastro, FichaMovil`.

Crea `src/modules/taller/imeis/rastro.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { eventoRastro } from '../test/fabrica'
import { fechaRastro, hayAntesDelHistorico, verEvento } from './rastro'

describe('textos del rastro (spec pieza 1 §9.3)', () => {
  it('fechas: con hora o solo el día', () => {
    expect(fechaRastro('2026-09-12T13:01:00', false)).toBe('12/09/2026 13:01')
    expect(fechaRastro('2023-03-14T00:00:00', true)).toBe('14/03/2023')
  })

  it('cada tipo de evento', () => {
    expect(verEvento(eventoRastro())).toMatchObject({ titulo: 'Pasa a Listos', detalle: 'Desde: Taller · Glass', color: 'bg-fila-recibido-brd' })
    expect(verEvento(eventoRastro({ a: null, aNsys: 'Pedidos realizados', de: 'Listos' })).titulo).toBe('De pedido (vendido, pendiente de envío)')
    expect(verEvento(eventoRastro({ a: null, aNsys: 'Desguace' })).titulo).toBe('Pasa a Desguace · sale del stock')
    expect(verEvento(eventoRastro({ tipo: 'ENTRADA', de: null, a: null, aNsys: null, soloDia: true })).titulo).toBe('Entra en el lote BT-9001')
    expect(verEvento(eventoRastro({ tipo: 'REENTRADA', a: 'BT-9002' })).titulo).toBe('Vuelve a entrar (lote BT-9002)')
    expect(verEvento(eventoRastro({ tipo: 'GRADO', de: 'C100', a: 'A100' }))).toMatchObject({ titulo: 'Se le pone el grado A100', detalle: 'Antes: C100' })
    expect(verEvento(eventoRastro({ tipo: 'PRODUCTO', de: 'Blue', a: 'Pink' })).detalle).toBe('Blue → Pink')
    expect(verEvento(eventoRastro({ tipo: 'ESTADO', de: 'InProcess', a: 'Available' })).titulo).toBe('Estado en NSYS: Available')
    expect(verEvento(eventoRastro({ tipo: 'VENTA', soloDia: true })).detalle).toBe('Del fichero de NSYS: solo se sabe el día')
    expect(verEvento(eventoRastro({ tipo: 'VENTA_DESHECHA', a: 'Available' })).titulo).toBe('Venta deshecha en NSYS')
    expect(verEvento(eventoRastro({ fuente: 'ERP', tipo: 'TRABAJO_ASIGNADO', idRep: 'AG1', tecnico: 'Técnico 3', categoria: 'GLASS' })).titulo)
      .toBe('Trabajo de glass asignado a Técnico 3')
    expect(verEvento(eventoRastro({ fuente: 'ERP', tipo: 'TRABAJO_TERMINADO', idRep: 'G1', tecnico: 'Técnico 3', categoria: 'GLASS' })))
      .toMatchObject({ titulo: 'Glass terminado', detalle: 'Técnico 3' })
  })

  it('la nota del histórico sale si hay eventos de antes de dic. 2023', () => {
    expect(hayAntesDelHistorico([eventoRastro(), eventoRastro({ fecha: '2023-03-14T00:00:00', soloDia: true })])).toBe(true)
    expect(hayAntesDelHistorico([eventoRastro({ fecha: '2023-12-01T09:00:00' })])).toBe(false)
  })
})
```

Al final de `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, añade (y amplía sus imports con `fichaMovil`, `eventoRastro` y `SESION_ADMIN`):

```tsx
describe('ImeiDetallePage: ficha de NSYS y rastro (0.9.17)', () => {
  const conNsys = (pedidas: string[] = []) => server.use(
    http.get(`*/api/stock-telefonos/${A}`, () => { pedidas.push('ficha'); return HttpResponse.json(fichaMovil()) }),
    http.get(`*/api/stock-telefonos/${A}/rastro`, () => {
      pedidas.push('rastro')
      return HttpResponse.json([
        eventoRastro(),
        eventoRastro({ fecha: '2026-09-12T12:00:00', fuente: 'ERP', tipo: 'TRABAJO_TERMINADO', lote: null, de: null, a: null, aNsys: null,
          idRep: 'G20260912_1', tecnico: 'tecnico_i', categoria: 'GLASS' }),
      ])
    }),
  )

  it('el supertécnico ve la ficha encima de los trabajos y abre el rastro en un panel lateral', async () => {
    const user = userEvent.setup()
    conNsys()
    abrir()
    const ficha = await screen.findByRole('region', { name: 'Ficha de NSYS' })
    expect(ficha).toHaveTextContent('iPhone 16 128GB Negro')
    expect(ficha).toHaveTextContent('Grado A (antes C) · batería nueva')
    await user.click(await screen.findByRole('button', { name: 'Ver rastro (2)' }))
    const panel = screen.getByRole('dialog', { name: 'Rastro' })
    expect(within(panel).getByText('Pasa a Listos')).toBeInTheDocument()
    expect(screen.getByText('IMEI: ' + A)).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Glass terminado' }))
    expect(screen.getByRole('row', { name: /^Glass G20260912_1 / })).toHaveAttribute('aria-selected', 'true')

    await user.click(within(panel).getByRole('button', { name: 'Cerrar el rastro' }))
    expect(screen.queryByRole('dialog', { name: 'Rastro' })).not.toBeInTheDocument()
  })

  it('el admin también la ve', async () => {
    conNsys()
    abrir(SESION_ADMIN)
    expect(await screen.findByRole('region', { name: 'Ficha de NSYS' })).toBeInTheDocument()
  })

  it('el técnico ve el detalle como hoy: no pide la ficha ni el rastro', async () => {
    const pedidas: string[] = []
    conNsys(pedidas)
    abrir(SESION_TEC)
    expect(await screen.findByText('• 3 trabajos')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ficha de NSYS' })).not.toBeInTheDocument()
    expect(pedidas).toEqual([])
  })

  it('si NSYS no conoce el IMEI no hay ficha ni aviso de error', async () => {
    abrir()
    expect(await screen.findByText('• 3 trabajos')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ficha de NSYS' })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
  })

  it('un IMEI sin trabajos del taller: la tabla lo dice', async () => {
    renderConProviders(<ImeiDetallePage />, { sesion: SESION_SUPER, ruta: '/reparaciones/imeis/350000000009999', patron: '/reparaciones/imeis/:imei' })
    expect(await screen.findByText('Este móvil no ha pasado por el taller')).toBeInTheDocument()
  })
})
```

Al final de `src/modules/taller/imeis/ImeisPage.test.tsx`, añade lo siguiente. Los imports que usa (`userEvent`,
`HttpResponse`, `http`, `server`, `Route`, `within` y las sesiones) ya están en el fichero.

```tsx
describe('ImeisPage: móviles sin trabajos del taller (0.9.17)', () => {
  it('el supertécnico que filtra por un IMEI ve también los móviles de NSYS sin trabajos y abre su detalle', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      { imeiSn: '353918700112233', modelo: '11', modeloMaestro: '11', capacidad: '64GB', color: 'Negro', estado: 'VENDIDO', ubicacion: null, fechaVenta: '2023-05-02' },
    ])))
    renderConProviders(<ImeisPage />, { sesion: SESION_SUPER, ruta: '/reparaciones/imeis',
      rutas: <Route path="/reparaciones/imeis/:imei" element={<p>Detalle del IMEI</p>} /> })
    await user.type(await screen.findByPlaceholderText('Filtrar por IMEI'), '35391870')
    // Por su texto: la capa de carga también usa role="status".
    const aviso = await screen.findByText(/También hay 1 móvil sin trabajos del taller con ese IMEI/)
    expect(aviso).toHaveAttribute('role', 'status')
    expect(aviso).toHaveTextContent('iPhone 11, vendido el 02/05/2023')
    await user.click(within(aviso).getByRole('button', { name: '353918700112233' }))
    expect(await screen.findByText('Detalle del IMEI')).toBeInTheDocument()
  })

  it('un IMEI con trabajos no sale en el aviso aunque los filtros de fecha lo escondan', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      { imeiSn: A, modelo: '16', modeloMaestro: '16', capacidad: '128GB', color: 'Negro', estado: 'STOCK', ubicacion: 'Listos', fechaVenta: null },
    ])))
    filtrosImeis.set({ ...filtrosImeis.get(), desde: '2030-01-01' })
    renderConProviders(<ImeisPage />, { sesion: SESION_SUPER, ruta: '/reparaciones/imeis' })
    await user.type(await screen.findByPlaceholderText('Filtrar por IMEI'), A.slice(0, 8))
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByText(/También hay/)).not.toBeInTheDocument()
  })

  it('el técnico no busca en NSYS', async () => {
    const user = userEvent.setup()
    let pedidas = 0
    server.use(http.get('*/api/stock-telefonos/buscar', () => { pedidas++; return HttpResponse.json([]) }))
    renderConProviders(<ImeisPage />, { sesion: SESION_TEC, ruta: '/reparaciones/imeis' })
    await user.type(await screen.findByPlaceholderText('Filtrar por IMEI'), '35391870')
    // Margen para que una petición, si la hubiera, llegase a MSW.
    await new Promise((r) => setTimeout(r, 50))
    expect(pedidas).toBe(0)
  })
})
```

> `src/test/setup.ts` vuelve los stores de filtros a su valor inicial después de cada test (`reiniciarStores`), así que
> el `desde` del segundo test no se queda para los demás.

Run: `npx vitest run src/modules/taller/imeis`
Expected: FAIL: no existe `./rastro` y no se pintan ni la ficha ni el aviso.

- [x] **Step 2: Implementación**

Crea `src/modules/taller/imeis/rastro.ts`:

```ts
import type { EventoRastro } from '@/shared/api/client'
import { diaNsys } from '@/shared/lib/telefonoNsys'

/** Textos y colores del rastro (0.9.17, spec «Pieza 1 del módulo de teléfonos» §9.3). El servidor manda el evento
 *  estructurado y aquí se compone lo que se lee. Las horas son de Madrid, sin zona: se cortan del texto sin convertir. */
export type EventoVisible = { fecha: string; titulo: string; detalle: string | null; color: string }

const CATEGORIA: Record<string, string> = { GLASS: 'glass', PULIDO: 'pulido', REPARACION: 'reparación' }
const mayuscula = (t: string) => t.charAt(0).toUpperCase() + t.slice(1)

export function fechaRastro(iso: string, soloDia: boolean): string {
  const dia = diaNsys(iso)
  return soloDia ? dia : `${dia} ${iso.slice(11, 16)}`
}

function colorUbicacion(u: string): string {
  if (u === 'Listos') return 'bg-fila-recibido-brd'
  if (u.startsWith('Taller')) return 'bg-tipo-reparacion-text'
  if (u === 'Devolución') return 'bg-fila-solicitud-brd'
  if (u === 'En revisión') return 'bg-ambar'
  return 'bg-gris-borde'
}

export function verEvento(e: EventoRastro): EventoVisible {
  const fecha = fechaRastro(e.fecha, e.soloDia)
  const desde = e.de ? `Desde: ${e.de}` : null
  switch (e.tipo) {
    case 'ENTRADA':
      return { fecha, titulo: `Entra en el lote ${e.lote ?? '—'}`, detalle: e.a ? `En ${e.a}` : null, color: 'bg-texto-suave' }
    case 'REENTRADA':
      return { fecha, titulo: `Vuelve a entrar (lote ${e.a ?? e.lote ?? '—'})`, detalle: null, color: 'bg-fila-solicitud-brd' }
    case 'UBICACION':
      if (e.a) return { fecha, titulo: `Pasa a ${e.a}`, detalle: desde, color: colorUbicacion(e.a) }
      if (e.aNsys === 'Pedidos realizados') return { fecha, titulo: 'De pedido (vendido, pendiente de envío)', detalle: desde, color: 'bg-fila-urgente-brd' }
      return { fecha, titulo: `Pasa a ${e.aNsys || 'sin caja'} · sale del stock`, detalle: desde, color: 'bg-gris-borde' }
    case 'GRADO':
      return { fecha, titulo: `Se le pone el grado ${e.a ?? '—'}`, detalle: e.de ? `Antes: ${e.de}` : null, color: 'bg-ambar' }
    case 'PRODUCTO':
      return { fecha, titulo: 'Cambia el producto en NSYS', detalle: `${e.de ?? '—'} → ${e.a ?? '—'}`, color: 'bg-ambar' }
    case 'ESTADO':
      return { fecha, titulo: `Estado en NSYS: ${e.a ?? '—'}`, detalle: e.de ? `Antes: ${e.de}` : null, color: 'bg-gris-borde' }
    case 'VENTA':
      return { fecha, titulo: 'Vendido (Released)', detalle: e.soloDia ? 'Del fichero de NSYS: solo se sabe el día' : null, color: 'bg-azul-noche' }
    case 'VENTA_DESHECHA':
      return { fecha, titulo: 'Venta deshecha en NSYS', detalle: e.a ? `Vuelve a ${e.a}` : null, color: 'bg-fila-solicitud-brd' }
    case 'TRABAJO_ASIGNADO':
      return { fecha, titulo: `Trabajo de ${CATEGORIA[e.categoria ?? ''] ?? 'taller'} asignado a ${e.tecnico ?? '—'}`, detalle: null, color: 'bg-tipo-reparacion-text' }
    case 'TRABAJO_TERMINADO':
      return { fecha, titulo: `${mayuscula(CATEGORIA[e.categoria ?? ''] ?? 'trabajo')} terminado`, detalle: e.tecnico, color: 'bg-tipo-reparacion-text' }
    default:
      return { fecha, titulo: e.tipo, detalle: null, color: 'bg-gris-borde' }
  }
}

/** Las exportaciones de Drive empiezan en dic. 2023: antes solo hay el esqueleto (entrada y venta, con solo el día). */
const INICIO_HISTORICO = '2023-12-01'
export const NOTA_ANTES_DEL_HISTORICO = 'Antes de dic. 2023 solo se conoce la entrada y la venta.'

export function hayAntesDelHistorico(eventos: EventoRastro[]): boolean {
  return eventos.some((e) => e.fecha < INICIO_HISTORICO)
}
```

Crea `src/modules/taller/imeis/FichaMovil.tsx`:

```tsx
import type { FichaMovil as Ficha } from '@/shared/api/client'
import { clasesUbicacion, diaNsys, nombreModeloNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'

type Props = { ficha: Ficha; eventos: number | null; onVerRastro: () => void }

/** Ficha del móvil según NSYS, encima de la vista de trabajos de hoy (0.9.17, spec §9.3), con el botón del rastro. */
export function FichaMovil({ ficha, eventos, onVerRastro }: Props) {
  const venta = ficha.fechaVenta ? diaNsys(ficha.fechaVenta) : null
  return (
    <section aria-label="Ficha de NSYS" className="mb-3 flex flex-wrap items-center gap-x-5 gap-y-1.5 rounded-lg bg-superficie px-4 py-3 text-[12.5px] text-azul-medio">
      <span className="font-mono text-[17px] font-extrabold text-azul-noche">{ficha.imeiSn}</span>
      <span>{[nombreModeloNsys(ficha.modelo, ficha.modeloMaestro), ficha.capacidad, ficha.color].filter(Boolean).join(' ')}</span>
      <span>Grado <b className="text-azul-noche">{ficha.grado}</b>{ficha.gradoAnterior && ` (antes ${ficha.gradoAnterior})`}{ficha.bateriaNueva && ' · batería nueva'}</span>
      {ficha.sku && <span>SKU <b className="font-mono text-azul-noche">{ficha.sku}</b></span>}
      {ficha.ubicacion && <span className={cn(CLASES_PILDORA, clasesUbicacion(ficha.ubicacion))}>{ficha.ubicacion}</span>}
      {ficha.estado === 'VENDIDO'
        ? <span className={cn(CLASES_PILDORA, 'bg-azul-noche text-superficie')}>{venta ? `Vendido el ${venta}` : 'Vendido'}</span>
        : <span className={ficha.estado === 'DE_PEDIDO' ? 'font-bold text-fila-urgente-brd' : 'text-azul-gris'}>{textoEstadoMovil(ficha.estado)}</span>}
      <span>Lote <b className="text-azul-noche">{ficha.lote}</b></span>
      {ficha.dias != null && <span>En stock <b className="text-azul-noche">{ficha.dias} días</b>{ficha.entradas > 1 && ` (${ficha.entradas} entradas)`}</span>}
      <button type="button" onClick={onVerRastro} disabled={eventos == null}
        className="ml-auto inline-flex cursor-pointer items-center gap-1.5 rounded-3xl border border-azul-noche bg-superficie px-3.5 py-1.5 text-[12px] font-bold text-azul-noche hover:bg-seleccion-suave disabled:cursor-default disabled:opacity-50">
        {eventos == null ? 'Ver rastro' : `Ver rastro (${eventos})`}
      </button>
    </section>
  )
}
```

Crea `src/modules/taller/imeis/PanelRastro.tsx`:

```tsx
import { useEffect } from 'react'
import type { EventoRastro } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { hayAntesDelHistorico, NOTA_ANTES_DEL_HISTORICO, verEvento } from './rastro'

const LEYENDA: [string, string][] = [
  ['bg-texto-suave', 'Lote'], ['bg-ambar', 'Revisión'], ['bg-tipo-reparacion-text', 'Taller'], ['bg-fila-recibido-brd', 'Listos'],
  ['bg-fila-urgente-brd', 'De pedido'], ['bg-azul-noche', 'Vendido'], ['bg-fila-solicitud-brd', 'Devolución'], ['bg-gris-borde', 'No cuenta como stock'],
]

type Props = { imei: string; eventos: EventoRastro[]; onCerrar: () => void; onIrATrabajo: (idRep: string) => void }

/** El rastro del teléfono en un panel lateral sobre la vista de trabajos (0.9.17, spec §9.3; opción A del prototipo):
 *  línea de tiempo del más reciente al más antiguo. Un evento de taller resalta su fila en la tabla de detrás. */
export function PanelRastro({ imei, eventos, onCerrar, onIrATrabajo }: Props) {
  useEffect(() => {
    const tecla = (e: KeyboardEvent) => { if (e.key === 'Escape') onCerrar() }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  }, [onCerrar])
  return (
    <aside role="dialog" aria-label="Rastro" className="fixed inset-y-0 right-0 z-40 flex w-[min(500px,42vw)] min-w-[380px] flex-col bg-superficie shadow-[-6px_0_24px_rgba(0,18,50,0.25)]">
      <header className="flex items-center gap-2.5 border-b border-fila-sep px-[18px] py-3.5">
        <h2 className="text-[15px] font-bold text-azul-noche">Rastro</h2>
        <span className="font-mono text-[12px] text-azul-gris">{imei}</span>
        <button type="button" aria-label="Cerrar el rastro" onClick={onCerrar} className="ml-auto cursor-pointer text-xl text-azul-gris">×</button>
      </header>
      <div className="flex-1 overflow-auto px-[18px] py-3">
        <ul aria-hidden className="mb-2 flex flex-wrap gap-3 text-[11px] text-azul-gris">
          {LEYENDA.map(([color, texto]) => (
            <li key={texto} className="flex items-center gap-1.5"><span className={cn('h-2.5 w-2.5 rounded-full', color)} />{texto}</li>
          ))}
        </ul>
        <p className="mb-3 text-[11.5px] text-azul-gris">Las horas de NSYS son las de la exportación en que se vio el cambio (± 30 min). Las del ERP son exactas.</p>
        {eventos.length === 0 && <p className="text-[12px] text-azul-gris">Todavía no hay eventos.</p>}
        <ol className="relative ml-2.5 border-l-[3px] border-fila-sep pl-6">
          {eventos.map((e, i) => {
            const v = verEvento(e)
            const idRep = e.idRep
            return (
              <li key={i} className="relative mb-3">
                <span aria-hidden className={cn('absolute top-[3px] -left-[35px] h-3.5 w-3.5 rounded-full border-[3px] border-superficie', v.color)} />
                <div className="text-[11px] font-bold text-azul-gris">
                  {v.fecha}
                  <span className={cn('ml-1.5 rounded px-1.5 text-[9.5px] font-extrabold', e.fuente === 'ERP' ? 'bg-recibido-bg text-recibido-text' : 'bg-badge-neutro-bg text-azul-gris')}>{e.fuente}</span>
                </div>
                {idRep
                  ? <button type="button" onClick={() => onIrATrabajo(idRep)} className="cursor-pointer text-left text-[13px] font-bold text-azul-noche underline decoration-dotted">{v.titulo}</button>
                  : <div className="text-[13px] font-bold text-azul-noche">{v.titulo}</div>}
                {v.detalle && <div className="text-[12px] text-azul-medio">{v.detalle}</div>}
              </li>
            )
          })}
        </ol>
        {hayAntesDelHistorico(eventos) && <p className="mt-1 text-[11.5px] text-azul-gris italic">{NOTA_ANTES_DEL_HISTORICO}</p>}
      </div>
    </aside>
  )
}
```

**`ImeiDetallePage.tsx`:**
- Imports: `useFichaMovil` y `useRastro` de `./apiNsys`, `FichaMovil` de `./FichaMovil` y `PanelRastro` de `./PanelRastro`. `esAdminOSuperTecnico` ya está importado.
- Tras `const trabajos = useTrabajos()`, añade:

```tsx
  // 0.9.17: la ficha de NSYS y el rastro, solo para SUPERTECNICO y ADMIN; el TECNICO ve el detalle como hoy.
  const veNsys = esAdminOSuperTecnico(sesion)
  const ficha = useFichaMovil(imei, veNsys)
  const rastro = useRastro(imei, veNsys && !!ficha.data)
  const [rastroAbierto, setRastroAbierto] = useState(false)
  const sinTrabajos = !trabajos.some((t) => t.imei === imei)
```

- Tras el `<h1 …>Agrupado por IMEI</h1>`, añade:

```tsx
      {veNsys && ficha.data && <FichaMovil ficha={ficha.data} eventos={rastro.data?.length ?? null} onVerRastro={() => setRastroAbierto(true)} />}
```

- En el `DataTable`, `vacio=""` pasa a `vacio={sinTrabajos ? 'Este móvil no ha pasado por el taller' : ''}`.
- Antes de `{dialogos}`, añade:

```tsx
      {rastroAbierto && rastro.data && (
        <PanelRastro imei={imei} eventos={rastro.data} onCerrar={() => setRastroAbierto(false)}
          onIrATrabajo={(idRep) => { setSeleccionada(idRep); setPeticionDesplazamiento((n) => n + 1) }} />
      )}
```

> `onCerrar` es una función nueva en cada render. El efecto del panel solo vuelve a registrar el listener de Escape;
> no hace falta `useCallback`.

**`ImeisPage.tsx`:**
- Imports: `esAdminOSuperTecnico` (junto a `esSuperTecnico`), `useBuscarMoviles` de `@/shared/api/nsys` y `diaNsys` y `nombreModeloNsys` de `@/shared/lib/telefonoNsys`.
- Tras `const grupos = useMemo(...)`, añade:

```tsx
  // 0.9.17: al filtrar por un IMEI, el supertécnico y el admin ven también los móviles de NSYS sin trabajos del taller.
  const veNsys = esAdminOSuperTecnico(sesion)
  const { data: enNsys = [] } = useBuscarMoviles(filtros.imei.split(',')[0] ?? '', veNsys)
  // Contra todos los trabajos, no contra los grupos visibles: un IMEI con trabajos fuera de las fechas del filtro no es «sin trabajos».
  const sinTrabajos = enNsys.filter((m) => !trabajos.some((t) => t.imei === m.imeiSn))
```

- Entre `<BarraFiltrosImeis …/>` y el `<DataTable`, añade:

```tsx
      {sinTrabajos.length > 0 && (
        <div role="status" className="mb-2 rounded-lg border border-fila-edicion-brd bg-seleccion-suave px-3 py-2 text-[12.5px] text-azul-medio">
          {sinTrabajos.length === 1 ? 'También hay 1 móvil' : `También hay ${sinTrabajos.length} móviles`} sin trabajos del taller con ese IMEI:{' '}
          {sinTrabajos.map((m, i) => (
            <span key={m.imeiSn}>
              {i > 0 && ' · '}
              <button type="button" onClick={() => abrir(m.imeiSn)} className="cursor-pointer font-bold text-texto-accion underline">{m.imeiSn}</button>
              {` (${nombreModeloNsys(m.modelo, m.modeloMaestro)}${m.fechaVenta ? `, vendido el ${diaNsys(m.fechaVenta)}` : ''})`}
            </span>
          ))}
        </div>
      )}
```

- [x] **Step 3: Ejecuta los tests (pasan)**

Run: `npx vitest run src/modules/taller/imeis`
Expected: todo en verde: los tests de antes y los nuevos.

Run: `npx tsc -b && npm run lint`
Expected: limpio.

- [x] **Step 4: Commit**

```bash
git add src/modules/taller
git commit -m "feat: ficha de NSYS y rastro en panel lateral en el detalle de IMEIs; moviles sin trabajos en la lista"
```

---

### Task 14: Web — E2E del stock de teléfonos y del rastro (solo lectura, preproducción)

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/stock-telefonos.spec.ts`
- Modify: `gestion-reparaciones-web/.env.e2e.example` (una línea de comentario)

**Interfaces:**
- Consumes:
  - el comportamiento de las Tasks 8 a 13, desplegado en preprod con la importación encendida (Task 16);
  - las variables `TEC_USER`/`TEC_PASS`, `E2E_USER`/`E2E_PASS` y `ADMIN_USER`/`ADMIN_PASS` de `~/.env.e2e`.
- Produces: `stock-telefonos.spec.ts`, que la Task 16 lanza contra preprod.

- [x] **Step 1: La E2E**

Crea `tests/e2e/stock-telefonos.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// stock-telefonos.spec.ts (0.9.17, spec «Pieza 1 del módulo de teléfonos»): el técnico no tiene «Teléfonos» (ni en la
// columna, ni por URL, ni por la API); el supertécnico y el admin ven el stock de teléfonos, abren un móvil, su ficha y su
// rastro, y el modelo por IMEI sale del espejo de NSYS. Solo LEE: los datos son los que la importación trae de NSYS y no
// se apunta ningún IMEI en el código (se usa el primero de la tabla). Como todas las E2E, solo contra PREPRODUCCIÓN: se
// niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

type Lectura<T> = { estado: number; cuerpo: T | null }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** GET con el token de la sesión de la página (misma origin que la app; patrón de roles-stock.spec.ts). */
async function leer<T>(page: Page, ruta: string): Promise<Lectura<T>> {
  return page.evaluate(async (r) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(r, { headers: { Authorization: `Bearer ${sesion.token}` } })
    return { estado: res.status, cuerpo: res.ok ? await res.json() : null }
  }, ruta) as Promise<Lectura<T>>
}

test('el técnico no tiene Teléfonos: ni en la columna, ni por URL, ni por la API', async ({ page }) => {
  const { usuario, clave } = credenciales('TEC_USER', 'TEC_PASS')
  await entrar(page, usuario, clave)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna.getByRole('link', { name: 'Stock actual', exact: true })).toBeVisible()
  await expect(columna.getByRole('link', { name: 'Stock de teléfonos', exact: true })).toHaveCount(0)
  await expect(columna.getByRole('button', { name: /^Teléfonos/ })).toHaveCount(0)
  await page.goto('/telefonos/stock')
  await expect(page.getByRole('dialog', { name: 'Error' })).toContainText('No tienes permisos para realizar esta acción.')
  await expect(page).toHaveURL(/\/reparaciones/)
  expect((await leer(page, '/api/stock-telefonos/estado')).estado).toBe(403)
})

for (const [rol, varUsuario, varClave] of [['supertécnico', 'E2E_USER', 'E2E_PASS'], ['admin', 'ADMIN_USER', 'ADMIN_PASS']] as const) {
  test(`el ${rol} ve el stock de teléfonos, abre un móvil y su rastro`, async ({ page }) => {
    const { usuario, clave } = credenciales(varUsuario, varClave)
    await entrar(page, usuario, clave)
    const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
    await columna.getByRole('link', { name: 'Stock de teléfonos', exact: true }).click()
    await expect(page).toHaveURL(/\/telefonos\/stock$/)
    await expect(page.getByRole('heading', { name: 'Stock de teléfonos', exact: true })).toBeVisible()
    await expect(page.getByText(/^\d+ móviles? en stock$/)).toBeVisible()
    await expect(page.getByRole('status').filter({ hasText: /^NSYS: / })).toBeVisible()
    const estado = await leer<{ ultimaExportacion: string | null }>(page, '/api/stock-telefonos/estado')
    expect(estado.estado).toBe(200)
    expect(estado.cuerpo?.ultimaExportacion, 'la importación de preprod ya ha traído una exportación').toBeTruthy()

    // El primer modelo de la lista y el primer móvil de su tabla.
    const modelos = page.getByRole('navigation', { name: 'Modelos' }).getByRole('button')
    await modelos.first().click()
    await expect(modelos.first()).toHaveAttribute('aria-pressed', 'true')
    const boton = page.getByRole('button', { name: /^Abrir / }).first()
    const imei = ((await boton.getAttribute('aria-label')) ?? '').replace('Abrir ', '')
    await boton.click()
    await page.waitForURL(/\/reparaciones\/imeis\//)
    expect(new URL(page.url()).pathname).toBe(`/reparaciones/imeis/${encodeURIComponent(imei)}`)

    // Ficha de NSYS y rastro en el panel lateral; Escape lo cierra.
    await expect(page.getByRole('region', { name: 'Ficha de NSYS' })).toContainText(imei)
    await page.getByRole('button', { name: /^Ver rastro \(\d+\)$/ }).click()
    const panel = page.getByRole('dialog', { name: 'Rastro' })
    await expect(panel).toBeVisible()
    await expect(panel.getByRole('listitem').first()).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(panel).toHaveCount(0)
  })
}

test('el modelo por IMEI sale del espejo de NSYS (adiós IMEIcheck)', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await entrar(page, usuario, clave)
  const stock = await leer<{ imeiSn: string; modelo: string | null }[]>(page, '/api/stock-telefonos')
  expect(stock.estado).toBe(200)
  // Un móvil con modelo que no haya pasado por el taller: así su modelo solo puede salir del espejo, no de Telefono.
  const candidatos = (stock.cuerpo ?? []).filter((m) => m.modelo && /^\d{15}$/.test(m.imeiSn)).slice(0, 10)
  let elegido: { imeiSn: string; modelo: string | null } | undefined
  for (const m of candidatos) {
    const rastro = await leer<{ fuente: string }[]>(page, `/api/stock-telefonos/${m.imeiSn}/rastro`)
    if (rastro.cuerpo && !rastro.cuerpo.some((e) => e.fuente === 'ERP')) {
      elegido = m
      break
    }
  }
  expect(elegido, 'entre los 10 primeros móviles con modelo hay alguno sin trabajos del taller').toBeDefined()
  const modelo = await leer<{ value: string | null }>(page, `/api/telefonos/${elegido!.imeiSn}/modelo`)
  expect(modelo.estado).toBe(200)
  expect(modelo.cuerpo?.value).toBe(elegido!.modelo)
})
```

- [x] **Step 2: Línea en `.env.e2e.example`**

Al final de `.env.e2e.example`, con una línea en blanco antes:

```
# stock-telefonos.spec.ts SOLO LEE (0.9.17, solo PREPRODUCCION: se niega a correr sin el letrero PREPRODUCCION): con TEC_USER
# comprueba que no hay Telefonos (columna, URL y API 403); con E2E_USER y ADMIN_USER abre el stock, un movil, su ficha y su
# rastro, y con E2E_USER que el modelo por IMEI sale del espejo de NSYS. Necesita la importacion de NSYS encendida.
```

- [x] **Step 3: Que Playwright la encuentre y compile**

Run: `npx playwright test --list tests/e2e/stock-telefonos.spec.ts`
Expected: 4 tests listados (`el técnico no tiene…`, `el supertécnico ve…`, `el admin ve…`, `el modelo por IMEI…`). No se lanza aquí: necesita preprod con la 0.9.17 y la importación encendida (Task 16).

Run: `npx tsc -b && npm run lint`
Expected: sin errores.

- [x] **Step 4: Commit**

```bash
git add tests/e2e/stock-telefonos.spec.ts .env.e2e.example
git commit -m "test: e2e de solo lectura del stock de telefonos, el rastro y el modelo por IMEI (0.9.17)"
```

---

### Task 15: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión).
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (entrada `[0.9.17]`).
- Modify: `gestion-reparaciones-web/docs/paridad/imeis.md` (sección 0.9.17).
- Create: `docs/novedades/NOVEDADES-v0.9.17.md` (raíz; lo sube el controlador con los gitlinks).

**Interfaces:**
- Consumes: todo lo de las Tasks 1 a 14.
- Produces: las ramas `dev_v0917_stock_telefonos` (servidor y web) listas para el merge.

- [x] **Step 1: Versión de la web**

Run (en `/c/Users/dev/Documents/wt/web-0917`): `npm version 0.9.17 --no-git-tag-version`
Expected: `v0.9.17`; `package.json` y las dos entradas de la raíz de `package-lock.json` en `0.9.17`.

- [x] **Step 2: CHANGELOG de la web**

En `CHANGELOG.md`, tras la línea `El formato sigue [Keep a Changelog]…` y su línea en blanco, antes de `## [0.9.16]`:

```markdown
## [0.9.17] - 2026-10-XX — Stock de teléfonos y rastro

- **Stock de teléfonos** (grupo nuevo «Teléfonos», solo supertécnico y administrador): los móviles que hay en la empresa según NSYS, por modelo y capacidad, con sus totales (listos, taller, revisión, de pedido y libres) y filtros de un clic por color, grado, batería, ubicación y estado. Busca cualquier IMEI, también los vendidos, y avisa de la hora de los datos de NSYS (en ámbar con retraso; banda roja si no llegan). Es de solo lectura.
- **IMEIs** (supertécnico y administrador): el detalle lleva encima la ficha de NSYS y «Ver rastro», que abre en un panel lateral la línea de tiempo del teléfono (cajas, grados, venta y trabajos del taller). Un IMEI sin trabajos del taller también tiene detalle, y la lista avisa de los móviles de NSYS sin trabajos al filtrar por IMEI. El técnico ve IMEIs como hasta ahora.
- El modelo que se rellena al escribir un IMEI sale del espejo de NSYS en vez de IMEIcheck. Si NSYS no conoce el IMEI, se escribe a mano, como cuando IMEIcheck fallaba.
- Requiere el servidor 0.9.17 y la migración `migracion-nsys-espejo.sql`.
```

- [x] **Step 3: Ficha de paridad**

Al final de `docs/paridad/imeis.md`, con una línea en blanco antes:

```markdown
## 0.9.17 — Ficha de NSYS y rastro (sin equivalente en el JavaFX)

Añadido, decidido en la spec `2026-10-16-pieza1-stock-telefonos-design.md` (raíz). Para SUPERTECNICO y ADMIN:

- el detalle lleva la ficha de NSYS y el panel del rastro;
- admite IMEIs sin trabajos («Este móvil no ha pasado por el taller»);
- la lista avisa de los móviles de NSYS sin trabajos.

Pruebas: `ImeiDetallePage.test.tsx`, `ImeisPage.test.tsx` y `stock-telefonos.spec.ts`.

La vista de trabajos, sus filtros, el menú de fila y el CSV no cambian. El TECNICO ve lo mismo que antes.
```

- [x] **Step 4: Novedades (raíz, sin commit)**

Crea `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.17.md`:

```markdown
# 🎉 Novedades — Versión 0.9.17 (web)

Primera pieza del módulo de teléfonos: el **stock de teléfonos** y el **rastro** de cada teléfono, con los datos de NSYS.

---

## 🧰 Para todos

- Al escribir un IMEI, el **modelo** se rellena con los datos de NSYS (antes, con IMEIcheck). Si no sale, se escribe a
  mano, como siempre.
- El técnico ve **IMEIs** como hasta ahora.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos (grupo nuevo «Teléfonos»)

- Los móviles que hay en la empresa según NSYS, agrupados por **modelo y capacidad**. Cada grupo dice cuántos hay
  listos, en el taller, en revisión y de pedido, y cuántos están libres.
- Al elegir un modelo salen sus móviles: color, grado, batería, eSIM, SKU, ubicación, estado y días. Los **de pedido**
  van primero, con el borde rojo.
- **Filtros de un clic:**
  - color y grado, en chips con su número;
  - batería, ubicación y estado, en desplegables;
  - «Quitar filtros» los borra todos.
- **Buscar IMEI** en todos los móviles, también en los vendidos.
- «Ver también lo que no cuenta como stock» enseña, en gris, los que esperan pieza o no se pueden vender. No suman en
  los totales.
- Arriba se ve la hora de la última exportación de NSYS:
  - si va con retraso, sale en ámbar;
  - si no llegan datos, lo avisa una banda roja.
- El ERP mira cada 5 minutos si NSYS ha dejado una exportación nueva en Drive.
- Es solo de consulta: los cambios se siguen haciendo en NSYS.

### 🔎 IMEIs

- El detalle de un IMEI lleva encima la **ficha de NSYS**: modelo, color, grado (y el que tenía antes), SKU, ubicación,
  estado, lote y días en stock.
- **«Ver rastro»** abre a la derecha la historia del teléfono: entradas, cambios de caja y de grado, la venta y los
  trabajos del taller. Un trabajo del rastro marca su fila en la tabla.
- Un IMEI que nunca ha pasado por el taller también se puede abrir.
- Al filtrar la lista por un IMEI, si NSYS tiene móviles con ese IMEI y sin trabajos del taller, sale un aviso con el
  enlace.
```

- [x] **Step 5: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0917`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total de tests en el informe.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0917`
Expected:
- lint con 0 errores (el aviso de `BadgeEstadoPedido.tsx` ya existía);
- `tsc -b` limpio;
- todos los tests de Vitest en verde. Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista incluye los 4 tests de `stock-telefonos.spec.ts` y los de siempre, sin errores de compilación.

- [x] **Step 6: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git commit -m "docs: version 0.9.17, changelog y paridad del stock de telefonos"
```

- [x] **Step 7: Entrega (controlador, con OK del usuario)**

1. Revisión final de las dos ramas.
2. Con el OK del usuario:
   - merge `--no-ff` de `dev_v0917_stock_telefonos` en `main` del servidor y de la web, con push de los dos;
   - en la raíz: gitlinks, `NOVEDADES-v0.9.17.md` y el plan marcado.
3. Worktrees y ramas se borran después de producción.

---

### Task 16: Preproducción

**Files:**
- Create: `Apuntes/preprod/v0917-preprod.md` (guion; fuera de los repos).
- Modify:
  - `Apuntes/despliegue_preprod.md` (registro);
  - `Apuntes/diseno-modulo-telefonos/drive-cuenta-servicio-erp.md` (estado y nombres de las variables confirmados).

**Requisitos antes de empezar:**
- La fase 1 de `drive-cuenta-servicio-erp.md` está hecha: la cuenta de servicio del ERP existe, la carpeta y el maestro
  están compartidos y `probar_drive_erp.py` ha dado OK.
- Se tienen los dos identificadores, que solo se apuntan en `Apuntes/`.
- **Ubicaciones:** si el usuario cambia la tabla de §4.5 al confirmarla, se cambia el `INSERT INTO Nsys_ubicacion` de
  `migracion-nsys-espejo.sql` y de `crear_bd.sql` en la rama antes del merge. Si cambia después, basta con un
  `UPDATE Nsys_ubicacion` (no hay que programar) y apuntarlo en la migración.

- [ ] **Step 1: Guion**

Basado en `preprod/v0915-preprod.md` (servidor + web + migración) y en la fase 2 de `drive-cuenta-servicio-erp.md`.
**Cada bloque empieza con `hostname` y el seguro de `backup-erp.sh`.**

- **Bloque 1 (solo lee):**
  - commits y contenedores;
  - `information_schema`: las cinco tablas `Nsys_*` aún no existen (0);
  - `SELECT COUNT(*) FROM Telefono WHERE MODELO IS NULL OR MODELO = ''`, para ver después cuántos rellena el espejo;
  - espacio libre (`df -h /`);
  - si el compose tiene `IMEICHECK_API_KEY`, solo con `grep -c` (la clave nunca se imprime).
- **Bloque 2 (escribe), con el OK de Claude:**
  - **Clave de Drive** (fase 2 de la guía):
    - desde el PC, `scp` a la VM y `Get-FileHash`;
    - en la VM, `mv`, `chmod 600` y `sha256sum`, que tiene que coincidir.
  - **Compose:**
    - copia `docker-compose.yml.bak-v0917`;
    - en el servicio `backend`, las cinco variables `NSYS_*` y el volumen `:ro` de la guía.
    - `NSYS_HISTORICO_ACTIVO` va a `"true"` desde el principio: la tarea solo trabaja de 23:00 a 07:00, así que empieza
      esa noche sin otro reinicio. En preprod sirve para probarla antes que en producción.
  - `pull` de servidor y web.
  - `migracion-nsys-espejo.sql`:
    - la vista previa da 0;
    - después del `CREATE`, `SHOW TABLES LIKE 'Nsys%'` da 5 y `Nsys_ubicacion` tiene 20 filas.
  - `docker compose up -d --build`. Avisar de que tarda ~1 min sin salida.
  - `Started App` sin `ERROR`.
- **Bloque 3 (solo lee), unos 5 minutos después:**
  - **`Nsys_importacion`:** un `MAESTRO` `OK` y un `ACTUAL` `OK` con unas 70.000 filas, y su `MENSAJE`.
  - **`Nsys_movil` por `ESTADO`:** los `STOCK` + `DE_PEDIDO` tienen que andar por el millar.
  - **Cajas sin traducir:** se ven en el `MENSAJE` («Cajas sin traducir: …»). También, de los móviles no vendidos, las
    `LOCATION` que no están en `Nsys_ubicacion`, con su número.
  - **Modelos sin código del ERP:** `MODELO_MAESTRO` con `MODELO IS NULL` en los `STOCK`, `DE_PEDIDO` y `NO_CUENTA`,
    con su número.
  - **`Telefono` sin modelo:** el mismo recuento del bloque 1, que tiene que bajar o quedar igual.
  - **Logs:** ningún `NSYS:` de error.
- **Comprobaciones de Claude desde el PC:**
  - `/version.json` da 0.9.17.
  - E2E de una en una, con ≥ 60 s entre ficheros:
    - `stock-telefonos`, la nueva;
    - `roles-stock`, `taller`, `asignar` y `columna-lateral`.
  - Capturas de «Stock de teléfonos» a 1366 y 1920 con la cuenta de supertécnico, solo para verlas Claude y el usuario:
    tienen datos reales y no se guardan en ningún repo.
- **Bloque 4 (solo lee), las dos mañanas siguientes:**
  - `Nsys_importacion` agrupado por `ORIGEN` y `RESULTADO`: los `HISTORICO` crecen de noche y ninguno da `ERROR`;
  - `NSYS: histórico cargado` en los logs al terminar;
  - tamaño de las tablas `Nsys_*` (`information_schema.TABLES`) y `df -h /`;
  - el rastro de un móvil antiguo ya tiene pasos intermedios.
- **Prueba de aceptación (spec §10), dos o tres días laborables:**
  1. El usuario refresca el Excel de stock y apunta qué fichero Items ha usado.
  2. Cuando el último `ACTUAL` `OK` de `Nsys_importacion` es ese mismo fichero, pega la salida de
     `SELECT SKU, COUNT(*) FROM Nsys_movil WHERE ESTADO = 'STOCK' AND SKU IS NOT NULL GROUP BY SKU ORDER BY SKU`.
  3. Claude la compara con la hoja «Saldo stock» (lee el Excel con `python -I`, en solo lectura).
  4. Las diferencias se explican una a una en el registro. Ya se conoce una causa: el arreglo pendiente del Power Query
     (F/FD/RMA, `mapa-flujo-telefono.md` §8).
- **Vuelta atrás:**
  - `NSYS_IMPORTACION_ACTIVA` y `NSYS_HISTORICO_ACTIVO` a `"false"`;
  - código anterior (servidor `83be807`, web `4aca632`) y `up -d --build`;
  - las tablas `Nsys_*` se quedan: la versión anterior no las usa;
  - `IMEICHECK_API_KEY` sigue en el compose hasta producción (Task 17), así que la versión anterior vuelve con su
    IMEIcheck.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Al final, entrada en `despliegue_preprod.md` con:
   - commits;
   - resultado de la migración y de la primera importación (filas, cajas sin traducir, modelos sin código);
   - E2E;
   - histórico (noches, ficheros y tamaño);
   - prueba de aceptación y explicación de cada diferencia.
3. En `drive-cuenta-servicio-erp.md`: casillas de preprod marcadas, nombres de las variables confirmados y
   `NSYS_HISTORICO_ACTIVO` a `"true"` desde el despliegue (la tarea solo trabaja de noche).
4. Si una E2E falla por la prueba y no por el programa, se arregla en la rama, se vuelve a pasar y se apunta.
5. **Producción solo cuando:** la prueba de aceptación cuadra (o cada diferencia está explicada), el histórico ha
   terminado sin errores y el usuario da el OK.

---

### Task 17: Producción

**Files:**
- Create: `Apuntes/prod-v0917.md` (guion).
- Modify: `Apuntes/despliegue_vdc_produccion.md` (registro), `Apuntes/diseno-modulo-telefonos/drive-cuenta-servicio-erp.md` (estado).

- [ ] **Step 1: Guion**

Basado en `prod-v0915.md` (con migración) y en la fase 2 de `drive-cuenta-servicio-erp.md`. **Cada bloque empieza con
`hostname` y la comprobación `PRODUCCIÓN (ok)`.**

- **Bloque 1:**
  - copia `backup-erp.sh`;
  - commits y contenedores;
  - lo mismo que el bloque 1 de preprod: tablas `Nsys_*` (0), `Telefono` sin modelo, `df -h /` y `grep -c IMEICHECK`.
- **Bloque 2:**
  - clave de Drive con su huella;
  - copia del compose y las variables `NSYS_*` con el volumen, con `NSYS_HISTORICO_ACTIVO` a `"true"` (como en preprod:
    empieza esa noche sola);
  - `pull`, migración (vista previa y comprobación) y `up -d --build`.
- **Bloque 3 (solo lee), unos 5 minutos después:** el mismo que el bloque 3 de preprod. Las cifras tienen que parecerse
  a las de preprod del mismo día.
- **Comprobación ligera desde el PC, sin escribir datos:**
  - versión 0.9.17 en `/version.json`;
  - sin letrero de preproducción (`preprod/comprobar-letrero.cjs` con `URL=https://erp.fonestore.es ESPERA=no`);
  - API sin sesión → 403, también en `/api/stock-telefonos`.
- **A mano (usuario, sin guardar nada):**
  - con su cuenta de supertécnico: «Stock de teléfonos», un modelo, abrir un móvil, su ficha y «Ver rastro»;
  - en el formulario, escribir el IMEI de un móvil del stock y ver que sale el modelo, sin guardar;
  - con una cuenta de técnico: sin el grupo «Teléfonos».
- **Momento:** en horario. La migración es aditiva, el corte es de unos 30 s, la web vieja es compatible y la vuelta
  atrás es simple.
- **Aviso al taller:** «Hemos actualizado el programa. Los supertécnicos tienen un grupo nuevo, "Teléfonos", con el
  stock de teléfonos de NSYS, y en IMEIs la ficha y el rastro de cada móvil. El modelo de un IMEI ya sale de NSYS.
  Recargad la página. Novedades en `NOVEDADES-v0.9.17`.»
- **Bloque 4 (solo lee), las mañanas siguientes:** el mismo que el bloque 4 de preprod, hasta `NSYS: histórico cargado`.
- **Bloque 5 (escribe), retirar IMEIcheck, cuando el usuario lo diga** (unos días después, cuando ya no se piense en
  volver atrás):
  - quitar `IMEICHECK_API_KEY` del compose de producción y del de preprod, con copia antes;
  - `up -d` sin `--build`;
  - `Started App` sin `ERROR`.
  - Después, el usuario pide al proveedor de IMEIcheck que anule **las dos** claves: la vieja, que quedó a la vista
    (memoria `project_imei_lookup_prod_sin_clave`), y la actual.
- **Vuelta atrás:**
  - `NSYS_IMPORTACION_ACTIVA` y `NSYS_HISTORICO_ACTIVO` a `"false"`;
  - servidor `83be807` y web `4aca632` + `up -d --build`, en cualquier orden;
  - las tablas se quedan;
  - mientras no se haya hecho el bloque 5, IMEIcheck vuelve con la versión anterior;
  - datos: la copia del bloque 1.

- [ ] **Step 2: Ejecución con el usuario y cierre**

1. Registro en `despliegue_vdc_produccion.md`: commits, migración, primera importación, histórico e IMEIcheck retirado.
2. Fecha real en `[0.9.17]` del CHANGELOG de la web: commit, push y gitlink.
3. `plan-futuro.md`:
   - la pieza 1 del módulo de teléfonos, marcada `[x]`;
   - las piezas 2, 3 y 4 siguen como `[ ]`.
4. `drive-cuenta-servicio-erp.md`: la casilla de producción, marcada.
5. Tags `v0.9.17` cuando lo diga el usuario.
6. Worktrees y ramas borrados.
7. Memoria del proyecto actualizada (`project_stock_telefonos_nsys`, `project_produccion_vdc` y
   `project_imei_lookup_prod_sin_clave`).
