# «Pedidos» como la hoja — plan 1: el módulo (servidor y web)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una tabla «Pedidos» en el ERP, igual que la hoja de pedidos, cuyas filas crea el servidor desde ShippyPro y en la que solo se teclea el IMEI, con recomendados y avisos.

**Architecture:** Módulo nuevo `pedidos` en el servidor (tabla `Pedido_fila` como fuente de verdad, tarea que sincroniza con ShippyPro cada 5 minutos, avisos y recomendación calculados al servir) y pantalla nueva en la web (tabla con filtro de fechas, buscador y filtros por columna). Las clases puras que ya existen en la rama `dev_v0926_pedidos` se traen copiadas; el resto de esa rama no se usa.

**Tech Stack:** Java 17, Spring Boot 3.3, JdbcTemplate, MariaDB; React + TypeScript, TanStack Query, openapi-fetch, Vitest, Playwright.

Spec: `docs/superpowers/specs/2026-11-10-pedidos-tabla-design.md` (raíz). Prototipo: `docs/superpowers/specs/assets/2026-11-10-pedidos-tabla-prototipo.html`.

**Fuera de este plan (plan 2, después del inventario de §9 de la spec):** la carga del histórico y el traspaso del corte (spec §8). Este plan deja la tabla lista para recibirlos (`ORIGEN = 'HISTORICO'`).

## Global Constraints

- Ramas nuevas desde `main` en los dos repos: `dev_pedidos_tabla`, en worktrees nuevos `C:/Users/dev/Documents/wt/servidor-pedidos-tabla` y `C:/Users/dev/Documents/wt/web-pedidos-tabla`. No tocar los clones normales, ni `wt/*-0926-pedidos`, ni worktrees de otras sesiones.
- Commits en español **sin tildes**, prefijos `feat:`/`fix:`/`test:`/`docs:`/`chore:`, **sin** línea `Co-Authored-By`. Sin push, merge ni tags: los pide el usuario.
- Repos públicos: en código, pruebas y docs solo datos inventados (IMEI `3500000000000xx`, pedidos `402-0000000-00000xx`, «Cliente Web A»). Ningún identificador de hoja, marketplace real, clave, IP ni nombre real.
- Maven desde Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`. Suite completa del servidor sin `-q` (para ver el recuento). Web: `npm run check`.
- El límite de 15 s por petición no se toca.
- Roles: todo `/api/pedidos/**` es `SUPERTECNICO` y `ADMIN`; el `TECNICO` recibe 403.
- Textos de la web en español, con los nombres de columna exactos: `Fecha`, `Plataforma`, `SKU`, `IMEI`, `Pedido`, `Fecha compra`.
- Sin número de versión: no tocar `package.json` ni la cabecera del CHANGELOG salvo una entrada «(pendiente)».

## Estructura de ficheros

Servidor (`src/main/java/com/reparaciones/servidor/pedidos/`):

| Fichero | Responsabilidad |
|---|---|
| `Canal`, `Textos`, `AceptaSku`, `Aceptacion`, `Atributos`, `EstadoUnidad`, `ClienteShippyPro`, `PedidoShippyPro` | Traídos de la rama vieja sin cambios de fondo (Task 1) |
| `FilaPedido` | Record de una fila de `Pedido_fila` |
| `FilasDAO` | Todo el SQL de `Pedido_fila`, `Pedido_fila_cambio` y `Pedido_lectura` |
| `SincroShippyPro` | Puro: de los pedidos leídos y las filas vivas, qué insertar y qué cerrar |
| `LecturaFilas` + `TareaLecturaFilas` | La vuelta de cada 5 minutos |
| `AvisoImei` | Puro: de un IMEI puesto, el nivel y el motivo |
| `Recomendador` | Puro: los candidatos de una fila, ordenados |
| `VistaFilas` | Junta filas, móviles y avisos para la API |
| `FilasController` | Las cuatro rutas |

Web (`src/modules/telefonos/pedidos/`): `PedidosPage.tsx`, `TablaFilas.tsx`, `CeldaImei.tsx`, `FiltroFechas.tsx`, `FiltroColumna.tsx`, `PildoraFuentes.tsx`, `filas.ts` (reglas puras) y sus tests; hooks en `src/modules/telefonos/api.ts`.

---

### Task 1: Ramas, piezas traídas y base de datos

**Files:**
- Create (worktrees): `wt/servidor-pedidos-tabla`, `wt/web-pedidos-tabla`
- Traer de `origin/dev_v0926_pedidos` (servidor): `pedidos/{Canal,Textos,AceptaSku,Aceptacion,Atributos,EstadoUnidad,ClienteShippyPro,PedidoShippyPro}.java` y sus tests `{Canal,Textos,AceptaSku,EstadoUnidad,ClienteShippyPro}Test.java`; `nsys/{AnchosNsys,ImportadorNsys,LectorMaestro,NsysDAO,ProductoMaestro}.java` y sus tests tocados por la rama (`git diff --name-only origin/main origin/dev_v0926_pedidos -- src/test/java/com/reparaciones/servidor/nsys`)
- Create: `sql/migracion-pedidos-tabla.sql`; Modify: `sql/crear_bd.sql`, `docs/schema.md`, `docs/deployment.md`

**Interfaces:**
- Produces: `AceptaSku.para(Canal, String sku) → Aceptacion|null`, `AceptaSku.conVariantes(Collection<String>)`, `Textos.sku`, `Textos.equivalente`, `Textos.pareceTelefono`, `Canal.deAjuste(String) → Map<String, Canal>`, `ClienteShippyPro.pendientes() → Leidos(pedidos, descartados)`, `EstadoUnidad.de(Aceptacion, MovilNsys, LoQueVale.Valor, List<TrabajoAbierto>, Canal) → Resultado(estado, motivo)`, `NsysDAO.productosPorSkuDeCanal(Collection<String>)`.

- [ ] **Step 1:** Crear los dos worktrees con `git worktree add -b dev_pedidos_tabla <ruta> origin/main` (tras `git fetch`).
- [ ] **Step 2:** En el servidor, `git checkout origin/dev_v0926_pedidos -- <cada fichero de la lista>`. `EstadoUnidad` usa `MovilUnidad`? No: solo `Aceptacion`, `Atributos`, `Canal`. Quitar de `Canal` el valor `SIMULADOR` y su uso en `AceptaSku.para` y `Canal.deAjuste` (queda `AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES, BACKIPHONE`); ajustar sus tests. En `NsysDAO`, quitar lo que la rama añadió solo para la línea de pedido de Asignaciones si no compila sin `PedidosDAO` (no se trae).
- [ ] **Step 3:** Escribir `sql/migracion-pedidos-tabla.sql` (a mano en la VM, con vista previa y comprobación final como `migracion-pedidos.sql` de la rama vieja; no idempotente; aditiva) y lo mismo en `crear_bd.sql`:

```sql
CREATE TABLE Pedido_fila (
    ID_FILA      INT          NOT NULL AUTO_INCREMENT,
    FECHA        DATE         NOT NULL COMMENT 'El dia en que el ERP ve el pedido (hora de Madrid)',
    PLATAFORMA   VARCHAR(150) NOT NULL COMMENT 'AMZ, FNSK o el cliente de la web; en el historico, lo de la hoja',
    SKU          VARCHAR(60)  NULL,
    IMEI         VARCHAR(60)  NULL COMMENT 'En el historico, el texto de la hoja tal cual',
    PEDIDO       VARCHAR(60)  NULL,
    FECHA_COMPRA DATE         NULL,
    ORIGEN       VARCHAR(10)  NOT NULL COMMENT 'SHIPPYPRO o HISTORICO',
    CANAL        VARCHAR(20)  NULL COMMENT 'AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES o BACKIPHONE',
    POSICION     INT          NULL,
    ENVIADA_EN   DATETIME     NULL COMMENT 'NULL = viva',
    PRIMARY KEY (ID_FILA),
    UNIQUE KEY uq_fila_pedido (CANAL, PEDIDO, POSICION),
    KEY idx_fila_fecha (FECHA, ID_FILA),
    KEY idx_fila_viva (ENVIADA_EN),
    KEY idx_fila_imei (IMEI),
    KEY idx_fila_pedido (PEDIDO)
);
CREATE TABLE Pedido_fila_cambio (
    ID_CAMBIO     INT         NOT NULL AUTO_INCREMENT,
    ID_FILA       INT         NOT NULL,
    ID_USU        INT         NULL,
    FECHA         DATETIME    NOT NULL,
    IMEI_ANTERIOR VARCHAR(60) NULL,
    IMEI_NUEVO    VARCHAR(60) NULL,
    PRIMARY KEY (ID_CAMBIO),
    KEY idx_cambio_fila (ID_FILA),
    CONSTRAINT fk_cambio_fila FOREIGN KEY (ID_FILA) REFERENCES Pedido_fila (ID_FILA) ON DELETE CASCADE,
    CONSTRAINT fk_cambio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
CREATE TABLE Pedido_lectura (
    ID_LECTURA       INT           NOT NULL AUTO_INCREMENT,
    INICIO           DATETIME      NOT NULL,
    FIN              DATETIME      NULL,
    RESULTADO        VARCHAR(12)   NOT NULL DEFAULT 'EN_CURSO' COMMENT 'EN_CURSO, OK, ERROR o RECHAZADA',
    FILAS            INT           NOT NULL DEFAULT 0,
    SKU_DESCONOCIDOS VARCHAR(500)  NULL,
    MENSAJE          VARCHAR(1000) NULL,
    PRIMARY KEY (ID_LECTURA),
    KEY idx_lectura_inicio (INICIO)
);
ALTER TABLE Nsys_producto
    ADD COLUMN SKU_AMAZON_ESIM  VARCHAR(60) NULL,
    ADD COLUMN SKU_PARTICULARES VARCHAR(60) NULL,
    ADD COLUMN SKU_BACKIPHONE   VARCHAR(60) NULL;
```

- [ ] **Step 4:** `docs/schema.md` (las tres tablas y las tres columnas) y `docs/deployment.md` (ajustes `PEDIDOS_LECTURA_ACTIVA`, `PEDIDOS_SHIPPYPRO_CLAVE`, `PEDIDOS_SHIPPYPRO_CANALES`; solo nombres).
- [ ] **Step 5:** `mvn test` completo: BUILD SUCCESS. Commit: `chore: base del modulo de pedidos como la hoja (piezas traidas, tablas y ajustes)`.

### Task 2: `FilaPedido` y `FilasDAO`

**Files:** Create `pedidos/FilaPedido.java`, `pedidos/FilasDAO.java`, `pedidos/LecturaGuardada.java`; Test `pedidos/FilasDAOTest.java` (JdbcTemplate simulado con Mockito, como el `PedidosDAOTest` de la rama vieja).

**Interfaces — Produces:**

```java
public record FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                         LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime enviadaEn) {
    public boolean viva() { return enviadaEn == null; }
}
public record LecturaGuardada(int id, LocalDateTime inicio, LocalDateTime fin, String resultado, int filas,
                              String skuDesconocidos, String mensaje) {}

public class FilasDAO {                       // @Repository, constructor (JdbcTemplate)
    List<FilaPedido> vivasDeShippyPro();      // ORIGEN='SHIPPYPRO' AND ENVIADA_EN IS NULL
    void insertar(List<FilaPedido> nuevas);   // batch; el id del record se ignora
    void cerrar(Collection<Integer> ids, LocalDateTime ahora);
    List<FilaPedido> delPeriodo(LocalDate desde, LocalDate hasta);   // FECHA entre las dos O ENVIADA_EN IS NULL; ORDER BY FECHA, ID_FILA
    List<FilaPedido> buscar(String texto, int tope);  // LIKE %texto% en PLATAFORMA, SKU, IMEI y PEDIDO; las tope+1 mas recientes, devueltas en orden de hoja
    Optional<FilaPedido> porId(int id);
    List<FilaPedido> vivasConImei(Collection<String> imeis);
    void ponerImei(int idFila, String imei, Integer idUsu, LocalDateTime ahora);  // @Transactional READ_COMMITTED: UPDATE + INSERT en Pedido_fila_cambio con el IMEI anterior
    int empezarLectura(LocalDateTime inicio);
    void terminarLectura(int id, String resultado, int filas, Collection<String> desconocidos, String mensaje, LocalDateTime fin);
    Optional<LecturaGuardada> ultimaLectura(Set<String> resultados);
    void podarLecturas(LocalDateTime antesDe);
}
```

- [ ] **Step 1:** Tests que fallan, uno por método: el SQL que se manda (`startsWith`) y sus argumentos; `buscar` escapa `%` y `_` del texto y pide `tope + 1`; `ponerImei` escribe el cambio con el IMEI anterior leído con `SELECT IMEI … FOR UPDATE`; los textos se cortan a los anchos del DDL (`AnchosNsys.corta`): PLATAFORMA 150, SKU 60, IMEI 60, PEDIDO 60, SKU_DESCONOCIDOS 500, MENSAJE 1000.
- [ ] **Step 2:** Ejecutar: `mvn -q test -Dtest=FilasDAOTest` → falla (no compila).
- [ ] **Step 3:** Implementar. Los `IN (…)` van en tandas de 500 (helper `trozos` como en `NsysDAO`).
- [ ] **Step 4:** `mvn -q test -Dtest=FilasDAOTest` → pasa. Añadir un `AnchosPedidosTest` que compare los anchos con `sql/migracion-pedidos-tabla.sql`, como el de la rama vieja.
- [ ] **Step 5:** Commit: `feat: FilasDAO, las filas de pedidos con su registro de cambios y sus lecturas`.

### Task 3: `SincroShippyPro`, `LecturaFilas` y la tarea

**Files:** Create `pedidos/SincroShippyPro.java`, `pedidos/LecturaFilas.java`, `pedidos/TareaLecturaFilas.java`; Tests `SincroShippyProTest`, `LecturaFilasTest`.

**Interfaces:**
- Consumes: `ClienteShippyPro.pendientes()`, `Canal.deAjuste`, `AceptaSku`, `NsysDAO.productosPorSkuDeCanal`, `FilasDAO`.
- Produces:

```java
public final class SincroShippyPro {
    public record Plan(List<FilaPedido> insertar, List<Integer> cerrar, Set<String> desconocidos) {}
    /** Puro. hoy: la fecha de Madrid. */
    public static Plan de(List<PedidoShippyPro> pendientes, Map<String, Canal> canales, AceptaSku acepta,
                          List<FilaPedido> vivas, LocalDate hoy);
    /** AMZ, FNSK o el cliente del pedido (sin cliente: «Web»). */
    static String plataforma(Canal canal, String cliente);
}
```

Reglas de `de` (spec §6), cada una con su test:
1. Marketplace que no está en `canales` → se ignora.
2. Artículo con `acepta.para(canal, sku) == null` → no crea fila; si `Textos.pareceTelefono(sku)`, va a `desconocidos`.
3. Artículo con cantidad N → N filas con `POSICION` 1..N **contando por pedido** (dos artículos de 2 y 1 dan posiciones 1, 2, 3, en el orden en que llegan).
4. Una fila viva con el mismo (`canal`, `pedido`, `posicion`) ya existe → no se inserta ni se toca.
5. Una fila viva cuyo (`canal`, `pedido`) no está entre los pendientes → va a `cerrar`.
6. `fecha = hoy`; `fechaCompra = pedido.fecha().toLocalDate()`; `origen = "SHIPPYPRO"`; `sku` tal cual llega (sin `equivalente`).

`LecturaFilas.pasada()`: `empezarLectura` → `cliente.pendientes()` → cargar el maestro (`productosPorSkuDeCanal(AceptaSku.conVariantes(skus))`) → `SincroShippyPro.de` → `insertar` y `cerrar` → `terminarLectura("OK", …)`. Sin el ajuste de canales: `RECHAZADA` con el mensaje «Falta el ajuste pedidos.shippypro.canales» y **no se cierra nada**. Si `pendientes()` lanza: `ERROR`, mensaje con la clase de la excepción (sin URL ni clave) y **no se cierra nada**. Poda las lecturas de más de 30 días. `TareaLecturaFilas`: `@ConditionalOnProperty(name = "pedidos.lectura.activa", havingValue = "true")`, `@Scheduled(initialDelay = 90_000, fixedDelay = 300_000)`.

- [ ] **Step 1:** Tests de las seis reglas y de las tres salidas de `pasada()` (OK, RECHAZADA, ERROR sin cerrar). Con un `Clock` fijo en `Europe/Madrid`.
- [ ] **Step 2:** `mvn -q test -Dtest='SincroShippyProTest,LecturaFilasTest'` → falla.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Repetir → pasa. `mvn test` completo (el contexto arranca sin la tarea).
- [ ] **Step 5:** Commit: `feat: las filas de pedidos se crean y se cierran solas desde ShippyPro`.

### Task 4: `AvisoImei`

**Files:** Create `pedidos/AvisoImei.java`; Test `AvisoImeiTest.java`.

**Interfaces — Produces:**

```java
/** nivel: OK, AVISO o MAL. motivo: null con OK. skuMovil y caja: null si el movil no esta en NSYS. */
public record AvisoImei(String nivel, String motivo, String skuMovil, String caja) {
    public static AvisoImei de(Aceptacion ac, Canal canal, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos,
                               boolean enOtraFilaViva);
}
```

Tabla de decisión (en este orden; `r = EstadoUnidad.de(ac, m, v, trabajos, canal)`):

| Caso | nivel | motivo |
|---|---|---|
| `m == null` | MAL | `NO_EN_NSYS` |
| `enOtraFilaViva` | MAL | `EN_OTRA_FILA` |
| `r.estado = NO_CUADRA`, motivo `MODELO` | MAL | `MODELO` |
| `r.estado = NO_CUADRA`, motivo `GRADO_NO_VENDIBLE` | MAL | `GRADO_NO_VENDIBLE` |
| `r.estado = NO_CUADRA`, motivo `SKU_DESCONOCIDO` | MAL | `SKU_DESCONOCIDO` |
| `r.estado = NECESITA_TALLER` (cualquier motivo) | MAL | `COLOR_O_GRADO` |
| `r.estado = EN_TALLER` | AVISO | `EN_TALLER` |
| `r.estado = CON_AVISO` | AVISO | `MS` o `B_POR_A` (el de `r`) |
| `r.estado = CUADRA` | OK | null |

`skuMovil = v.sku()`; `caja = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion()`.

- [ ] **Step 1:** Un test por fila de la tabla (móvil y aceptación construidos como en `EstadoUnidadTest`).
- [ ] **Step 2:** `mvn -q test -Dtest=AvisoImeiTest` → falla. **Step 3:** Implementar. **Step 4:** Pasa.
- [ ] **Step 5:** Commit: `feat: AvisoImei, lo que el ERP dice del IMEI puesto en una fila`.

### Task 5: `Recomendador`

**Files:** Create `pedidos/Recomendador.java`; Test `RecomendadorTest.java`.

**Interfaces — Produces:**

```java
public final class Recomendador {
    /** nivel: 1 igual que el pedido, 2 en el taller y acabara igual, 3 hay que transformarlo. */
    public record Candidato(String imei, String sku, String caja, int nivel) {}
    public static final int TOPE = 5;
    /** Puro. stock: los moviles en la empresa. yaPuestos: claves de cruce (NsysDAO.claveCruce) de los IMEI de filas vivas. */
    public static List<Candidato> de(Aceptacion ac, Canal canal, List<MovilNsys> stock, Function<MovilNsys, LoQueVale.Valor> vale,
                                     Map<String, List<TrabajoAbierto>> trabajos, Set<String> yaPuestos);
}
```

Reglas (spec §5), cada una con su test:
1. Entra si `EstadoMovil.STOCK` tras `m.estado().conGrado(v.grado())`, mismo `modeloMaestro`, `capacidad` y `esEsim` que `ac.pide()`, y su clave no está en `yaPuestos`.
2. Fuera los grados `EstadoMovil.NO_VENDIBLES` y, en Amazon, `ID` (lo da `EstadoUnidad`: se descarta `NO_CUADRA`).
3. Nivel: `EstadoUnidad` da `CUADRA` o `CON_AVISO` → 1; `EN_TALLER` con `ac.acepta(v.sku())` → 2; `NECESITA_TALLER` → 3. `EN_TALLER` sin SKU aceptado no entra.
4. Orden: nivel; después `receivingDate` más antigua (null al final); después `imeiSn`.
5. Como mucho `TOPE`.

- [ ] **Step 1–4:** tests, fallo, implementación, pasa (`-Dtest=RecomendadorTest`).
- [ ] **Step 5:** Commit: `feat: Recomendador, los moviles que valen para una fila, del mejor al peor`.

### Task 6: `VistaFilas`, `FilasController` y el contrato

**Files:** Create `pedidos/VistaFilas.java`, `pedidos/FilasController.java`, `pedidos/FilaVista.java`, `pedidos/ListaFilas.java`, `pedidos/FuenteVista.java`, `pedidos/PeticionImei.java`; Modify `OpenApiContractTest.java`, `docs/api_contract.md`, `docs/autorizacion_endpoints.md`; Tests `VistaFilasTest`, `FilasControllerTest`, `controller/RolesPedidosTest`.

**Interfaces — Produces (API):**

```java
public record FilaVista(int idFila, LocalDate fecha, String plataforma, @Schema(nullable = true) String sku,
                        @Schema(nullable = true) String imei, @Schema(nullable = true) String pedido,
                        @Schema(nullable = true) LocalDate fechaCompra, boolean enviada,
                        @Schema(nullable = true) String nivel, @Schema(nullable = true) String motivo,
                        @Schema(nullable = true) String skuMovil, @Schema(nullable = true) String caja) {}
public record ListaFilas(List<FilaVista> filas, boolean hayMas) {}
public record FuenteVista(String fuente, @Schema(nullable = true) LocalDateTime ultimaBuena,
                          @Schema(nullable = true) String resultado, int filas,
                          @Schema(nullable = true) String skuDesconocidos, @Schema(nullable = true) String motivo, String nivel) {}
public record PeticionImei(@Schema(nullable = true) String imei) {}
```

Rutas (`@RequestMapping("/api/pedidos")`, `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")`):

- `GET /filas?desde=&hasta=&buscar=` → `ListaFilas`. Sin `buscar`: `dao.delPeriodo(desde, hasta)` (por defecto, el último mes hasta hoy), `hayMas = false`. Con `buscar` (2 caracteres o más, si no `400`): `dao.buscar(texto, 500)`, `hayMas` si llegaron 501. Los avisos solo se calculan en filas vivas con IMEI: una sola carga de `nsysDao.porImeis`, `trabajosAbiertos`, `taller.valedor` y `productosPorSkuDeCanal` para todas.
- `GET /filas/{id}/recomendados` → `List<Recomendador.Candidato>`. `404` si no existe; lista vacía si está enviada o su SKU no lo conoce el maestro.
- `PUT /filas/{id}/imei` con `PeticionImei` → `FilaVista` (ya con su aviso). `400` si no es null ni 15 cifras; `404`; `409` si la fila está enviada o es del histórico. Usuario del token (`UsuarioPrincipal`); `Idempotency-Key` como las demás escrituras del formulario.
- `GET /fuentes` → `[ShippyPro, NSYS]`: la de ShippyPro con `ultimaLectura(Set.of("OK"))` y la última de todas (un `ERROR` enseña un motivo fijo, sin el texto de la excepción); la de NSYS, como la da hoy `AvisoNsys`.

- [ ] **Step 1:** Tests: `VistaFilasTest` (avisos solo en vivas con IMEI; `enOtraFilaViva` cuando dos filas vivas llevan el mismo IMEI; el periodo por defecto; `hayMas`), `FilasControllerTest` (los códigos 400/404/409 y el registro del cambio), `RolesPedidosTest` (403 al técnico en las cuatro rutas), y en `OpenApiContractTest` las cuatro rutas y los cuatro esquemas con su nulabilidad.
- [ ] **Step 2:** fallan. **Step 3:** implementar. **Step 4:** `mvn test` completo → BUILD SUCCESS; `target/openapi.json` regenerado.
- [ ] **Step 5:** Docs del contrato y de autorización. Commit: `feat: API de pedidos como la hoja (filas, recomendados, IMEI y fuentes)`.

### Task 7: Web — base: rutas, «Compras» y los hooks

**Files:** Traer de `origin/dev_v0926_pedidos` (web) el cambio de nombre a «Compras» (`src/app/router.tsx`, `src/app/shell/*`, `src/modules/almacen/rutas*.tsx`, `src/modules/almacen/pedidos/PedidosPage*.tsx`, `src/modules/taller/notificaciones/PanelNotificaciones*.tsx`, `src/modules/telefonos/rutas*.tsx`, `tests/e2e/{bloques,columna-lateral,otros-en-bloques,pedidos,roles-stock}.spec.ts`), **sin** `CeldaImeiPendiente`, `ImeiDetallePage` ni `taller/test/fabrica.ts`. Modify `api/openapi.json` + `src/shared/api/schema.d.ts` (desde `target/openapi.json` de la Task 6: `node -e` con `JSON.stringify(j, null, 2)` y `npm run api:types:offline`), `src/shared/api/client.ts` (tipos `FilaVista`, `ListaFilas`, `FuenteVista`, `Candidato`), `src/modules/telefonos/api.ts`, `src/modules/telefonos/test/fabrica.ts`.

**Interfaces — Produces:**

```ts
export const CLAVE_FILAS = ['telefonos', 'pedidos', 'filas'] as const
export function useFilas(p: { desde: string; hasta: string; buscar: string }): UseQueryResult<ListaFilas>   // sondea; placeholderData: keepPreviousData
export function useRecomendados(idFila: number | null): UseQueryResult<Candidato[]>                          // enabled: idFila != null
export function usePonerImei(): UseMutationResult<FilaVista, unknown, { idFila: number; imei: string | null }> // invalida CLAVE_FILAS
export function useFuentesPedidos(): UseQueryResult<FuenteVista[]>
// fabrica.ts (datos inventados): fila(p?), candidato(p?), fuente(p?)
```

- [ ] **Step 1:** Tests de los hooks en `api.test.tsx` (la URL y los parámetros de cada uno; `usePonerImei` invalida la lista).
- [ ] **Step 2–4:** fallan, implementar, `npm run check` → todo verde.
- [ ] **Step 5:** Commit: `feat: base de Pedidos como la hoja en la web (Compras, rutas, tipos y hooks)`.

### Task 8: Web — la tabla y la celda del IMEI

**Files:** Create `pedidos/filas.ts`, `pedidos/TablaFilas.tsx`, `pedidos/CeldaImei.tsx`, `pedidos/PildoraFuentes.tsx`, `pedidos/PedidosPage.tsx` y sus tests.

**Interfaces — Produces (`filas.ts`, puro):**

```ts
export const COLUMNAS = ['Fecha', 'Plataforma', 'SKU', 'IMEI', 'Pedido', 'Fecha compra'] as const
export function textoMotivo(f: FilaVista): string | null
export function claseCelda(f: FilaVista): 'vacia' | 'ok' | 'aviso' | 'mal' | 'enviada'
export function recuento(filas: FilaVista[]): { total: number; sinEnviar: number; sinImei: number }
export const TEXTO_NIVEL: Record<1 | 2 | 3, string>   // 'igual que el pedido', 'en el taller, acabará igual', 'hay que transformarlo'
```

`textoMotivo`: `NO_EN_NSYS` → «no está en NSYS»; `EN_OTRA_FILA` → «ya está en otro pedido»; `MODELO` → «otro modelo, capacidad o eSIM: es {skuMovil}»; `COLOR_O_GRADO` → «otro color o grado: es {skuMovil}»; `GRADO_NO_VENDIBLE` → «ese grado no se vende»; `SKU_DESCONOCIDO` → «el maestro no conoce este SKU»; `EN_TALLER` → «en el taller»; `MS` → «es un Ms»; `B_POR_A` → «B por A». Sin `skuMovil`, la frase acaba antes de los dos puntos.

Comportamiento (el del prototipo):
- Tabla con cabecera fija, una fila por `FilaVista`, en el orden recibido; al montar y al llegar filas nuevas con el usuario al final, se desplaza al final. Las enviadas, en gris.
- `CeldaImei`: vacía → «pulsa para poner el IMEI…»; con IMEI → el IMEI, el motivo si lo hay y, sin motivo, la caja. Fondo según `claseCelda` (ámbar aviso, naranja mal). Al pulsar una fila sin enviar se abre el desplegable: campo (Enter guarda; si no son 15 cifras, mensaje «Un IMEI son 15 cifras» bajo el campo, sin `alert`), los candidatos de `useRecomendados` con su caja y `TEXTO_NIVEL`, «Quitar el IMEI» si tiene y «Cerrar». Escape y clic fuera cierran. Solo una celda abierta a la vez.
- Cabecera de la página: «Pedidos», el recuento (`{total} filas · {sinEnviar} sin enviar · {sinImei} sin IMEI`) y `PildoraFuentes`.
- Estados de carga, error y vacío como en las demás páginas de Teléfonos.

- [ ] **Step 1:** Tests: `filas.test.ts` (cada motivo, cada clase, el recuento) y `PedidosPage.test.tsx` (pinta las seis columnas con esos nombres; abre el desplegable; elegir un candidato llama a `PUT` con ese IMEI; escribir 14 cifras no llama y enseña el mensaje; «Quitar» manda `null`; una fila enviada no abre; la celda `mal` lleva su motivo).
- [ ] **Step 2–4:** fallan, implementar, `npm run check`.
- [ ] **Step 5:** Commit: `feat: la tabla de Pedidos como la hoja, con la celda del IMEI, sus recomendados y sus avisos`.

### Task 9: Web — filtro de fechas, buscador y filtros por columna

**Files:** Create `pedidos/FiltroFechas.tsx`, `pedidos/FiltroColumna.tsx`, `pedidos/estado.ts`; Modify `pedidos/PedidosPage.tsx`, `pedidos/filas.ts` y tests.

**Interfaces — Produces (`filas.ts`):**

```ts
export type Periodo = 'mes' | 'tres-meses' | 'anio' | 'todo' | 'rango'
export function rangoDe(p: Periodo, hoy: Date, rango?: { desde: string; hasta: string }): { desde: string; hasta: string } // 'todo' → desde '2000-01-01'
export function filtrarColumnas(filas: FilaVista[], f: { plataformas: Set<string>; skus: Set<string>; soloSinImei: boolean }): FilaVista[]
export function valores(filas: FilaVista[], columna: 'plataforma' | 'sku'): string[]  // distintos, ordenados
```

Comportamiento:
- `FiltroFechas`: «Último mes» (por defecto), «Últimos 3 meses», «Este año», «Todo» y «Rango…» con dos fechas. Se guarda en el estado de la página (`estado.ts`, como `estadoStockTelefonos`) para que siga al volver.
- Buscador: con 2 caracteres o más y 300 ms de espera, pide con `buscar` (el filtro de fechas no se aplica y se ve atenuado); con `hayMas`, la línea «Hay más de 500 resultados: se enseñan los 500 más recientes. Afina la búsqueda.».
- `FiltroColumna` en las cabeceras de Plataforma y SKU: lista de `valores` con casillas y «Todos»; en la de IMEI, la casilla «Solo sin IMEI» (filas sin enviar y sin IMEI). Un filtro activo marca la cabecera. Todo sobre lo recibido, sin pedir al servidor.
- El recuento de la cabecera cuenta lo recibido, no lo filtrado; si hay filtro de columna, añade «· {n} a la vista».

- [ ] **Step 1:** Tests de `rangoDe` (cada periodo con una fecha fija), `filtrarColumnas`, `valores`, y en la página: cambiar el periodo pide otras fechas; buscar pide con `buscar`; el aviso de los 500; el filtro de plataforma esconde filas sin pedir.
- [ ] **Step 2–4:** fallan, implementar, `npm run check`.
- [ ] **Step 5:** Commit: `feat: filtro de fechas, buscador en todo el historico y filtros por columna en Pedidos`.

### Task 10: E2E, changelog y guion de preprod

**Files:** Create `tests/e2e/pedidos-tabla.spec.ts`; Modify `CHANGELOG.md` (entrada «(pendiente)»); Create (fuera del repo) `C:/Users/dev/Documents/Apuntes/preprod/pedidos-tabla-preprod.md`.

- [ ] **Step 1:** E2E (solo contra preprod, con el letrero «PREPRODUCCIÓN · datos de prueba»): el técnico no tiene «Pedidos» (ni en la columna, ni por URL, ni `GET /api/pedidos/filas`); el supertécnico ve las seis columnas, y en la primera fila viva sin IMEI abre la celda, elige el primer recomendado, comprueba que queda puesto y **lo quita** (deja la fila como estaba). `test.skip` si no hay ninguna fila viva sin IMEI o no hay recomendados.
- [ ] **Step 2:** Entrada del CHANGELOG, en lenguaje de usuario.
- [ ] **Step 3:** Guion de preprod con los bloques de siempre (cada uno con `hostname` y la comprobación de `backup-erp.sh`): estado; `migracion-pedidos-tabla.sql` (ojo: en preprod ya están las tablas viejas `Pedido`, `Pedido_unidad` y `Pedido_lectura` y las tres columnas de `Nsys_producto` de la 0.9.31: el bloque las retira antes — `DROP TABLE Pedido_unidad, Pedido, Pedido_lectura` — y la migración de preprod no repite el `ALTER`); quitar del compose los ajustes `PEDIDOS_PEDIDOS2026_HOJA` y `PEDIDOS_SIMULADOR_HOJA`; servidor y web de `dev_pedidos_tabla`; comprobación de las lecturas y de las filas creadas.
- [ ] **Step 4:** Commit (web): `test: E2E de Pedidos como la hoja y changelog`.

## Revisión final

Tras la última tarea: revisión de toda la rama (servidor y web) con el paquete de revisión, lista de pendientes menores, y `superpowers:finishing-a-development-branch`. La fusión, el push y preprod, con el OK del usuario.
