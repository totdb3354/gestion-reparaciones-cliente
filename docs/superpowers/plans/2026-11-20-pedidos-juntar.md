# Pedidos: «Juntar pedidos» (dos pedidos web en una sola caja) — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que logística pueda marcar en el ERP que dos (o más) pedidos web del mismo cliente y tienda salen en una sola caja con la
etiqueta de uno de ellos (el principal), que las filas del juntado reciban ese envío (y sus móviles pasen a vendidos) y que la tabla de
Pedidos los enseñe pegados.

**Architecture:** Servidor: tabla `Pedido_junto` (un pedido juntado → su principal); reglas puras en `Juntos` (estado de un pedido por sus
filas, principal, motivo para no juntar, grupos, grupos a deshacer); `FilasDAO.juntar`/`separar` con las filas bloqueadas (`FOR UPDATE`,
`READ COMMITTED`); `JuntarController` (`POST /api/pedidos/juntar`, `DELETE /api/pedidos/juntar/{canal}/{pedido}`); `FilaVista` gana
`juntoPrincipal` y `juntados`; la lectura de ShippyPro (`SincroShippyPro` + `LecturaFilas`) pone el envío del principal en las filas vivas
de sus juntados, no reabre las filas del juntado que ya salieron y deshace sola los grupos cuyo A o principal desaparece. Web: lógica pura
en `pedidos/juntar.ts` (espejo de las reglas del servidor), la tabla pinta los grupos pegados (vista A del prototipo), el panel enseña la
sugerencia, el grupo y «Separar», y la ventana `JuntarDialog` elige con quién juntar.

**Tech Stack:** Spring Boot 3 + JdbcTemplate + MariaDB 11, JUnit 5 + Mockito (JdbcTemplate simulado, como el resto de DAOs de pedidos);
React 19 + TypeScript + TanStack Query + openapi-fetch, Vitest + Testing Library + MSW, Playwright (E2E solo en preprod).

**Spec:** `docs/superpowers/specs/2026-11-20-pedidos-juntar-design.md` (repo raíz `C:/Users/dev/Documents/ProgramaReparaciones`). Léela
entera antes de empezar. Prototipo aprobado (vista A): `docs/superpowers/specs/assets/2026-11-20-pedidos-juntar-prototipo.html` — ábrelo en
el navegador para ver el comportamiento; **no copies su HTML**: la tabla real es `TablaFilas.tsx` (virtualizada, filas de alto fijo).

## Global Constraints

- Ramas `dev_pedidos_juntar` en servidor y en web, creadas desde lo último de `origin/dev_pedidos_tabla` (Task 0), en worktrees
  `C:/Users/dev/Documents/wt/servidor-pedidos-juntar` y `C:/Users/dev/Documents/wt/web-pedidos-juntar`. **Otra sesión de Claude está
  cambiando `dev_pedidos_tabla` a la vez**: no toques esa rama, sus worktrees (`wt/servidor-pedidos-tabla`, `wt/web-pedidos-tabla`) ni otros
  worktrees de `C:/Users/dev/Documents/wt/`. Nada en `main`.
- Commits en español **sin tildes ni eñes** en el mensaje, prefijos `feat:`/`fix:`/`test:`/`docs:`/`chore:`, **sin línea `Co-Authored-By`**.
  **Nunca push, merge a `main` ni tags** sin el OK explícito de Imad (el usuario). Traer `origin/dev_pedidos_tabla` a tu rama (Task 13) sí.
- **No toques el número de versión** (ni `pom.xml`, ni `package.json`, ni CHANGELOG): se decide al fusionar.
- Repos públicos: datos de prueba inventados (pedidos `901xx`, clientes «Cliente Web A», IMEI que empiezan por `0099`), nada real en tests
  ni docs. Los textos del código pueden llevar tildes (los de pantalla y los mensajes 409 las llevan).
- E2E **solo contra preprod** (nunca producción), restaurando lo que toquen. No despliegues tú: el despliegue en preprod lo lanza Imad.
- Toolchain servidor (Git Bash, en cada comando):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17 PATH=/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH`.
  Tests: `mvn -q test -Dtest=Clase` y al final `mvn -q test` (hoy pasan 2317; incluye `OpenApiContractTest`, que levanta el contexto y deja
  `target/openapi.json`).
- Web: `npx vitest run <ruta>`, `npx tsc -b --noEmit`, `npx eslint <ruta>`, y al final `npm run check` (lint + typecheck + tests; 3020 pruebas
  hoy; tarda ~6 min: lánzalo con un timeout de 10 min).
- Tipos de la API en la web: tras `mvn -q test -Dtest=OpenApiContractTest` en el servidor, copiar `target/openapi.json` del servidor a
  `api/openapi.json` de la web y `npm run api:types:offline` (regenera `src/shared/api/schema.d.ts`; `FilaVista` sale de ahí vía
  `src/shared/api/client.ts`). Así se hizo en el commit «openapi reconciled» 7f7178b.
- Transacciones que leen y luego escriben: `@Transactional(isolation = Isolation.READ_COMMITTED)` y las filas bloqueadas con `FOR UPDATE`
  (MariaDB 11.6+ da el error 1020 con aislamiento de instantánea; ver `FilasDAO.ponerFaltaEnviar`). Orden de bloqueo siempre: primero las
  filas de `Pedido_fila`, luego `Pedido_junto`.
- Ninguna petición puede pasar de 15 s (el límite no se sube).
- Hora de la empresa: Madrid (`ImportadorNsys.MADRID` en el servidor; `hoyMadrid()` de `@/shared/lib/fechas` en la web).
- Mismos roles que poner IMEI: SUPERTECNICO y ADMIN (`@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")`).
- Textos exactos (servidor y web dicen lo mismo):
  - motivos: «es el mismo pedido», «Amazon: cada pedido lleva su propio seguimiento», «otra tienda» (solo web), «no hay ningún pedido
    90102 en esa tienda», «90101 ya va con 90108», «90102 ya no está en ShippyPro: solo se juntan pedidos que siguen allí», «90102 ya
    salió: esa caja ya no se puede abrir», «los dos tienen su propia etiqueta: son dos envíos», «90101 ya es el principal de otro envío»;
  - 409 del POST: «No se puede juntar 90101 con 90102: <motivo>»; 409 del DELETE: «Este pedido ya tiene el envío de su principal: no se
    puede separar»;
  - pantalla: «Se puede juntar con 90108», «Ver 90108», «Juntar», «Juntar con otro pedido…», «Va en la caja del pedido 90108»,
    «Separar de 90108», «Ya tiene el envío de 90108: no se puede separar.», «Envío juntado: lleva también 90101», «Separar»,
    «Juntar el pedido 90105 con…», «La caja lleva la etiqueta de 90111 (es el más reciente)» / «(es el que ya tiene etiqueta)»,
    aviso «90105 saldrá más tarde de lo que toca» + «90105 entró el 16/11 y sale hoy; 90111 entró el 19/11 y sale el lun 23/11. Si los
    juntas, 90105 saldrá con 90111.», chips «+ 90101» y «→ 90108», «con 90108» en Enviado;
  - historial: «Juntado: va en la caja de 90108», «Separado de 90108», «Se separó de 90108: 90108 ya no está en ShippyPro».

---

## Mapa de ficheros

Servidor (`src/main/java/com/reparaciones/servidor/pedidos/` salvo indicación):

| Fichero | Qué |
|---|---|
| `sql/migracion-pedidos-juntar.sql` (nuevo) | tabla `Pedido_junto`; aditiva y repetible |
| `sql/crear_bd.sql` | `DROP` y `CREATE TABLE Pedido_junto` |
| `Juntos.java` (nuevo) | reglas puras: `Junto`, `EstadoPedido`, `Decision`, `Grupo`, `Deshacer`, `estado`, `decidir`, `mapa`, `grupos`, `aDeshacer` |
| `CambioImei.java` | constantes `JUNTADO` y `SEPARADO` |
| `FilasDAO.java` | `juntos()`, `deShippyProDePedidos(...)`, `juntar(...)`, `separar(...)` |
| `JuntarController.java` (nuevo) | `POST /api/pedidos/juntar`, `DELETE /api/pedidos/juntar/{canal}/{pedido}` |
| `FilaVista.java`, `VistaFilas.java` | `juntoPrincipal` y `juntados` en cada fila |
| `SincroShippyPro.java` | envío del principal para los juntados; no reabrir las filas del juntado que ya salieron |
| `LecturaFilas.java` | lee los grupos, se los pasa a `SincroShippyPro` y deshace los que toca |
| `docs/api_contract.md`, `docs/autorizacion_endpoints.md`, `docs/schema.md` | rutas, campos, roles y tabla |
| Tests (`src/test/java/com/reparaciones/servidor/pedidos/`) | `DdlJuntoTest`, `JuntosTest`, `JuntarDAOTest`, `JuntarControllerTest` (nuevos); `VistaFilasTest`, `SincroShippyProTest`, `LecturaFilasTest`; `controller/RolesPedidosTest`; `OpenApiContractTest` |

Web (`src/modules/telefonos/` salvo indicación):

| Fichero | Qué |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | regenerados |
| `test/fabrica.ts` | `juntoPrincipal: null, juntados: []` en `fila()` |
| `pedidos/salida.ts` | `textoSalidaPlano(fecha, hoy)` |
| `pedidos/juntar.ts` (nuevo) | lógica pura: pedidos, estado, motivo, principal, sugerencia, orden de la tabla, aviso de fechas |
| `pedidos/TablaFilas.tsx` | grupo de cada fila: raya morada, chips, «con 90108»; desplazarse a la fila elegida («Ver») |
| `pedidos/PedidosPage.tsx` | ordena con los grupos y pasa `grupos`, `pedidos` y `hoy` |
| `pedidos/historial.ts` | apuntes `JUNTADO` y `SEPARADO` |
| `api.ts` | `useJuntar()`, `useSeparar()` |
| `pedidos/SeccionJuntar.tsx` (nuevo), `pedidos/PanelFila.tsx` | el apartado «Envío» del panel |
| `pedidos/JuntarDialog.tsx` (nuevo) | la ventana «Juntar el pedido X con…» |
| Tests | `pedidos/juntar.test.ts`, `pedidos/PedidosJuntar.test.tsx` (nuevos); `pedidos/salida.test.ts`, `pedidos/historial.test.ts` |
| `tests/e2e/pedidos-juntar.spec.ts` (nuevo) | E2E en preprod |

---

### Task 0: Ramas y worktrees

**Files:** ninguno (solo git).

- [ ] **Step 1: traer lo último y crear las ramas desde origin**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git worktree add -b dev_pedidos_juntar /c/Users/dev/Documents/wt/servidor-pedidos-juntar origin/dev_pedidos_tabla
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git worktree add -b dev_pedidos_juntar /c/Users/dev/Documents/wt/web-pedidos-juntar origin/dev_pedidos_tabla
```

Expected: `Preparing worktree (new branch 'dev_pedidos_juntar')` dos veces. Si la rama ya existe, para y pregunta (no la borres).
Anota los commits de partida: `git -C /c/Users/dev/Documents/wt/servidor-pedidos-juntar log --oneline -1` y lo mismo en la web (al escribir
este plan eran `5ebb26d` y `03d66d2`; pueden ser más nuevos).

- [ ] **Step 2: dependencias de la web y línea base**

```bash
cd /c/Users/dev/Documents/wt/web-pedidos-juntar && npm ci
cd /c/Users/dev/Documents/wt/servidor-pedidos-juntar && export JAVA_HOME=/c/Users/dev/tools/jdk-17 PATH=/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH && mvn -q test
```

Expected: `npm ci` sin errores; `mvn -q test` termina con `BUILD SUCCESS` (o sin salida de error) y `Tests run: 2317` o más en
`target/surefire-reports`. Si algo ya falla en la línea base, para y avisa: no es de esta rama.

---

### Task 1: Servidor — tabla `Pedido_junto` y tipos del historial

**Files:**
- Create: `sql/migracion-pedidos-juntar.sql`
- Modify: `sql/crear_bd.sql` (bloque de `DROP` del principio y detrás de `CREATE TABLE Pedido_lectura (...)`)
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java`
- Modify: `docs/schema.md` (detrás de la línea de `Pedido_lectura`)
- Test: `src/test/java/com/reparaciones/servidor/pedidos/DdlJuntoTest.java`

**Interfaces:**
- Produces: tabla `Pedido_junto (CANAL VARCHAR(20), PEDIDO VARCHAR(60), PEDIDO_PRINCIPAL VARCHAR(60), JUNTADO_EN DATETIME, JUNTADO_POR INT NULL)`,
  PK (`CANAL`, `PEDIDO`); `CambioImei.JUNTADO = "JUNTADO"`, `CambioImei.SEPARADO = "SEPARADO"` (caben en `Pedido_fila_cambio.TIPO VARCHAR(20)`).

- [ ] **Step 1: el test que falla**

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Pedido_junto (spec 2026-11-20 §4): la migracion y crear_bd.sql definen la misma tabla; la migracion es aditiva y repetible. */
class DdlJuntoTest {

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String crear(String sql) {
        String junto = sql.replaceAll("(?m)^--.*$", "").replace("IF NOT EXISTS ", "").replaceAll("\\s+", " ").strip();
        int desde = junto.indexOf("CREATE TABLE Pedido_junto (");
        assertTrue(desde >= 0, "falta CREATE TABLE Pedido_junto");
        return junto.substring(desde, junto.indexOf(");", desde) + 2);
    }

    @Test void laTablaEsLaMismaEnLaMigracionYEnCrearBd() throws Exception {
        assertEquals(crear(fichero("crear_bd.sql")), crear(fichero("migracion-pedidos-juntar.sql")));
    }

    @Test void laTablaTieneLasColumnasYLaClaveDeLaSpec() throws Exception {
        String t = crear(fichero("migracion-pedidos-juntar.sql"));
        for (String c : new String[]{"CANAL VARCHAR(20) NOT NULL", "PEDIDO VARCHAR(60) NOT NULL", "PEDIDO_PRINCIPAL VARCHAR(60) NOT NULL",
                "JUNTADO_EN DATETIME NOT NULL", "JUNTADO_POR INT NULL", "PRIMARY KEY (CANAL, PEDIDO)"})
            assertTrue(t.contains(c), c);
    }

    @Test void laMigracionEsAditivaYRepetible() throws Exception {
        String sql = fichero("migracion-pedidos-juntar.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS Pedido_junto ("));
        assertTrue(sql.contains("USE gestion_reparaciones;") && sql.contains("SET NAMES utf8mb4;"));
        assertTrue(sql.contains("SET SESSION lock_wait_timeout = 10;"));
        assertTrue(sql.contains("AS TABLA_YA") && sql.contains("AS TABLA_NUEVA"));
        String sinComentarios = sql.replaceAll("(?m)^--.*$", "").toUpperCase();
        for (String peligro : new String[]{"DROP ", "DELETE ", "TRUNCATE ", "ALTER TABLE PEDIDO_FILA "})
            assertFalse(sinComentarios.contains(peligro), peligro);
    }

    @Test void crearBdBorraLaTablaAlEmpezar() throws Exception {
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Pedido_junto;"));
    }
}
```

- [ ] **Step 2: verlo fallar**

Run: `mvn -q test -Dtest=DdlJuntoTest`
Expected: FAIL (`NoSuchFileException: sql/migracion-pedidos-juntar.sql`).

- [ ] **Step 3: la migración** (`sql/migracion-pedidos-juntar.sql`, mismo patrón que `migracion-pedidos-salida.sql`)

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-juntar.sql: «Juntar pedidos» (dos pedidos web en una sola caja)
-- (spec docs/superpowers/specs/2026-11-20-pedidos-juntar-design.md). PRODUCCION Y PREPROD: el mismo fichero.
-- Crea la tabla Pedido_junto: un pedido juntado (A) y el pedido principal (B) con cuya etiqueta sale la caja. Los apuntes nuevos del
-- historial de la fila (tipos JUNTADO y SEPARADO) caben en Pedido_fila_cambio tal como esta (TIPO es VARCHAR(20)).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor: el servidor nuevo lee Pedido_junto en cada
-- lectura de ShippyPro y al servir las filas, y sin ella falla.
-- Aditiva e idempotente (CREATE TABLE IF NOT EXISTS): se puede relanzar entera. El servidor anterior no lee la tabla: funciona con ella
-- puesta. No lanzarla mientras corre la copia nocturna.
-- 1) Vista previa: TABLA_YA debe dar 0 (primera vez) o 1 (ya aplicada).
-- 2) Crea la tabla.
-- 3) Comprobacion final: TABLA_NUEVA debe dar 1 (y FILAS, 0 la primera vez); si no, NO desplegar el servidor.
-- Vuelta atras: el servidor anterior funciona con la tabla puesta; no hace falta quitar nada (o DROP TABLE Pedido_junto).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS TABLA_YA FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_junto';

-- Si algo tiene en uso Usuario (la FK de JUNTADO_POR), no esperar mas de 10 s por el bloqueo de metadatos: falla y se relanza.
SET SESSION lock_wait_timeout = 10;

CREATE TABLE IF NOT EXISTS Pedido_junto (
    CANAL            VARCHAR(20) NOT NULL COMMENT 'La tienda web (DISTRIBUIDORES, PARTICULARES o BACKIPHONE)',
    PEDIDO           VARCHAR(60) NOT NULL COMMENT 'El pedido juntado (A): sale en la caja del principal',
    PEDIDO_PRINCIPAL VARCHAR(60) NOT NULL COMMENT 'El pedido (B) con cuya etiqueta sale la caja',
    JUNTADO_EN       DATETIME    NOT NULL COMMENT 'Cuando se junto (hora de Madrid)',
    JUNTADO_POR      INT         NULL COMMENT 'Quien lo junto',
    PRIMARY KEY (CANAL, PEDIDO),
    KEY idx_junto_principal (CANAL, PEDIDO_PRINCIPAL),
    CONSTRAINT fk_junto_usuario FOREIGN KEY (JUNTADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobacion final (no modifica nada): TABLA_NUEVA = 1; si no, NO desplegar el servidor.
SELECT COUNT(*) AS TABLA_NUEVA FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_junto';
SELECT COUNT(*) AS FILAS FROM Pedido_junto;
```

- [ ] **Step 4: `crear_bd.sql`** — en el bloque de `DROP` del principio, justo antes de `DROP TABLE IF EXISTS Pedido_asociacion_revisar;`, añade
  `DROP TABLE IF EXISTS Pedido_junto;`. Detrás del `CREATE TABLE Pedido_lectura (...);` (busca `CREATE TABLE Pedido_lectura (`), añade la
  tabla **sin** `IF NOT EXISTS` y con exactamente las mismas columnas, comentarios, clave, índice y FK que la migración:

```sql
CREATE TABLE Pedido_junto (
    CANAL            VARCHAR(20) NOT NULL COMMENT 'La tienda web (DISTRIBUIDORES, PARTICULARES o BACKIPHONE)',
    PEDIDO           VARCHAR(60) NOT NULL COMMENT 'El pedido juntado (A): sale en la caja del principal',
    PEDIDO_PRINCIPAL VARCHAR(60) NOT NULL COMMENT 'El pedido (B) con cuya etiqueta sale la caja',
    JUNTADO_EN       DATETIME    NOT NULL COMMENT 'Cuando se junto (hora de Madrid)',
    JUNTADO_POR      INT         NULL COMMENT 'Quien lo junto',
    PRIMARY KEY (CANAL, PEDIDO),
    KEY idx_junto_principal (CANAL, PEDIDO_PRINCIPAL),
    CONSTRAINT fk_junto_usuario FOREIGN KEY (JUNTADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

  No cambies el `COMMENT` de `Pedido_fila_cambio.TIPO` (otros tests comparan ese DDL entre ficheros).

- [ ] **Step 5: `CambioImei`** — junto a las demás constantes:

```java
    /** Se junto el pedido de la fila con otro (spec 2026-11-20): detalle = el pedido principal, con cuya etiqueta sale la caja. */
    public static final String JUNTADO = "JUNTADO";
    /** Se separo: detalle = el pedido principal, o «<principal> — <pedido> ya no está en ShippyPro» si lo separo la lectura (sin usuario). */
    public static final String SEPARADO = "SEPARADO";
```

  y en el javadoc del record añade `JUNTADO` y `SEPARADO` a la lista de tipos y de detalles.

- [ ] **Step 6: `docs/schema.md`** — detrás de la línea de `Pedido_lectura`:

```markdown
- `Pedido_junto` («Juntar pedidos», spec 2026-11-20: un pedido web juntado (`PEDIDO`) que sale en la caja de `PEDIDO_PRINCIPAL`, en la misma tienda `CANAL`; `JUNTADO_EN`/`JUNTADO_POR`: cuándo y quién; PK `CANAL`+`PEDIDO`; ver `sql/migracion-pedidos-juntar.sql`. Juntar y separar se apuntan en `Pedido_fila_cambio` con `TIPO` `JUNTADO` y `SEPARADO`)
```

- [ ] **Step 7: verde**

Run: `mvn -q test -Dtest='DdlJuntoTest,DdlAsociacionTest,AnchosPedidosTest'`
Expected: PASS.

- [ ] **Step 8: commit**

```bash
git add sql/migracion-pedidos-juntar.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java docs/schema.md src/test/java/com/reparaciones/servidor/pedidos/DdlJuntoTest.java
git commit -m "feat: tabla Pedido_junto para juntar pedidos web en una sola caja (migracion aditiva)"
```

---

### Task 2: Servidor — reglas puras `Juntos`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/Juntos.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/JuntosTest.java`

**Interfaces:**
- Consumes: `FilaPedido` (`viva()`, `enviada()`, `impresaEn()`, `fecha()`, `id()`, `canal()`, `pedido()`, `origen()`),
  `SincroShippyPro.ClavePedido(Canal canal, String pedido)`, `Canal.esWeb()`.
- Produces (usados por las Tasks 3-7):
  - `record Juntos.Junto(Canal canal, String pedido, String principal, LocalDateTime juntadoEn, Integer juntadoPor)`
  - `enum Juntos.EstadoPedido { PENDIENTE, GENERADA, ENVIADO, NO_PENDIENTE }`
  - `record Juntos.Decision(String principal, String juntado, String motivo, boolean yaEstaba)` con `boolean hecha()`
  - `record Juntos.Grupo(String principal, List<String> juntados)`; `record Juntos.Deshacer(Canal canal, String pedido, String motivo)`
  - `static EstadoPedido estado(List<FilaPedido>)`
  - `static Decision decidir(Canal canal, String a, String b, List<FilaPedido> filas, List<Junto> juntos)`
  - `static Map<ClavePedido, ClavePedido> mapa(List<Junto>)` (juntado → principal)
  - `static Map<ClavePedido, Grupo> grupos(List<Junto>)` (principal y cada juntado → su grupo)
  - `static List<Deshacer> aDeshacer(List<Junto>, List<FilaPedido>)`
  - `static String desaparecido(String pedido)` = «<pedido> ya no está en ShippyPro»

- [ ] **Step 1: el test que falla**

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** «Juntar pedidos» (spec 2026-11-20 §1 y §2): reglas puras. Datos inventados. */
class JuntosTest {

    private static final Canal WEB = Canal.PARTICULARES;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 10, 0);

    private static FilaPedido viva(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", WEB, 1, null);
    }

    /** Etiqueta generada y sin imprimir. */
    private static FilaPedido generada(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", WEB, 1, AHORA, AHORA, "UPSv2", "1Z0099", null, 555L, null);
    }

    private static FilaPedido impresa(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", WEB, 1, AHORA, AHORA, "UPSv2", "1Z0099", null, 555L, AHORA);
    }

    /** Ya no esta en ShippyPro: cerrada sin envio. */
    private static FilaPedido cerrada(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", WEB, 1, AHORA);
    }

    private static Juntos.Junto junto(String pedido, String principal) {
        return new Juntos.Junto(WEB, pedido, principal, AHORA, 9);
    }

    private static String motivo(String a, String b, List<FilaPedido> filas, List<Juntos.Junto> juntos) {
        return Juntos.decidir(WEB, a, b, filas, juntos).motivo();
    }

    @Test void estadoDeUnPedidoPorSusFilas() {
        assertEquals(Juntos.EstadoPedido.PENDIENTE, Juntos.estado(List.of(viva(1, "90101", 14), cerrada(2, "90101", 14))));
        assertEquals(Juntos.EstadoPedido.GENERADA, Juntos.estado(List.of(generada(1, "90101", 14))));
        assertEquals(Juntos.EstadoPedido.ENVIADO, Juntos.estado(List.of(generada(1, "90101", 14), impresa(2, "90101", 14))));
        assertEquals(Juntos.EstadoPedido.NO_PENDIENTE, Juntos.estado(List.of(cerrada(1, "90101", 14))));
    }

    @Test void dosPendientesSeJuntanYElPrincipalEsElMasRecienteEmpieceQuienEmpiece() {
        List<FilaPedido> filas = List.of(viva(1, "90101", 14), viva(2, "90108", 18), viva(3, "90108", 18));
        Juntos.Decision d = Juntos.decidir(WEB, "90101", "90108", filas, List.of());
        assertTrue(d.hecha());
        assertEquals("90108", d.principal());
        assertEquals("90101", d.juntado());
        assertFalse(d.yaEstaba());
        Juntos.Decision alReves = Juntos.decidir(WEB, "90108", "90101", filas, List.of());
        assertEquals("90108", alReves.principal());
        assertEquals("90101", alReves.juntado());
    }

    @Test void aIgualFechaElPrincipalEsElDeLaFilaMasNueva() {
        List<FilaPedido> filas = List.of(viva(7, "90101", 18), viva(4, "90102", 18));
        assertEquals("90101", Juntos.decidir(WEB, "90102", "90101", filas, List.of()).principal());
    }

    @Test void elQueYaTieneEtiquetaEsElPrincipalAunqueSeaElMasAntiguo() {
        List<FilaPedido> filas = List.of(generada(1, "90101", 14), viva(2, "90108", 18));
        Juntos.Decision d = Juntos.decidir(WEB, "90108", "90101", filas, List.of());
        assertEquals("90101", d.principal());
        assertEquals("90108", d.juntado());
    }

    @Test void losMotivosParaNoJuntar() {
        List<FilaPedido> base = new ArrayList<>(List.of(viva(1, "90101", 14), viva(2, "90108", 18)));
        assertEquals("es el mismo pedido", motivo("90101", "90101", base, List.of()));
        assertEquals("Amazon: cada pedido lleva su propio seguimiento",
                Juntos.decidir(Canal.AMAZON, "90101", "90108", base, List.of()).motivo());
        assertEquals("no hay ningún pedido 90199 en esa tienda", motivo("90101", "90199", base, List.of()));
        assertEquals("90102 ya no está en ShippyPro: solo se juntan pedidos que siguen allí",
                motivo("90101", "90102", List.of(viva(1, "90101", 14), cerrada(3, "90102", 13)), List.of()));
        assertEquals("90102 ya salió: esa caja ya no se puede abrir",
                motivo("90101", "90102", List.of(viva(1, "90101", 14), impresa(3, "90102", 15)), List.of()));
        assertEquals("los dos tienen su propia etiqueta: son dos envíos",
                motivo("90101", "90102", List.of(generada(1, "90101", 14), generada(3, "90102", 15)), List.of()));
    }

    @Test void sinCadenas() {
        List<FilaPedido> filas = List.of(viva(1, "90101", 14), viva(2, "90108", 18), viva(3, "90110", 19), viva(4, "90099", 12));
        // 90101 ya va con 90110: no se puede juntar con otro, ni como A ni como B.
        assertEquals("90101 ya va con 90110", motivo("90101", "90108", filas, List.of(junto("90101", "90110"))));
        assertEquals("90101 ya va con 90110", motivo("90108", "90101", filas, List.of(junto("90101", "90110"))));
        // 90108 es principal de 90101; juntarlo con 90110 (mas reciente) lo haria juntado: no.
        assertEquals("90108 ya es el principal de otro envío", motivo("90108", "90110", filas, List.of(junto("90101", "90108"))));
        // Un principal puede llevar varios: 90099 (mas antiguo) entra en la caja de 90108.
        Juntos.Decision d = Juntos.decidir(WEB, "90099", "90108", filas, List.of(junto("90101", "90108")));
        assertTrue(d.hecha());
        assertEquals("90108", d.principal());
    }

    @Test void repetirLaMismaJuntaNoEsUnErrorNiEscribe() {
        List<FilaPedido> filas = List.of(viva(1, "90101", 14), viva(2, "90108", 18));
        Juntos.Decision d = Juntos.decidir(WEB, "90108", "90101", filas, List.of(junto("90101", "90108")));
        assertTrue(d.hecha());
        assertTrue(d.yaEstaba());
        assertEquals("90108", d.principal());
        assertEquals("90101", d.juntado());
    }

    @Test void mapaYGrupos() {
        List<Juntos.Junto> js = List.of(junto("90103", "90108"), junto("90101", "90108"));
        SincroShippyPro.ClavePedido a = new SincroShippyPro.ClavePedido(WEB, "90101");
        SincroShippyPro.ClavePedido p = new SincroShippyPro.ClavePedido(WEB, "90108");
        Map<SincroShippyPro.ClavePedido, SincroShippyPro.ClavePedido> mapa = Juntos.mapa(js);
        assertEquals(p, mapa.get(a));
        assertNull(mapa.get(p));
        Map<SincroShippyPro.ClavePedido, Juntos.Grupo> grupos = Juntos.grupos(js);
        Juntos.Grupo g = new Juntos.Grupo("90108", List.of("90101", "90103"));
        assertEquals(g, grupos.get(p));
        assertEquals(g, grupos.get(a));
        assertNull(grupos.get(new SincroShippyPro.ClavePedido(Canal.DISTRIBUIDORES, "90101")));
    }

    @Test void seDeshaceSiElPrincipalOElJuntadoYaNoTienenFilasVivasNiEnvio() {
        List<Juntos.Junto> js = List.of(junto("90101", "90108"), junto("90102", "90109"), junto("90103", "90110"), junto("90104", "90111"));
        List<FilaPedido> filas = List.of(
                viva(1, "90101", 14), cerrada(2, "90108", 18),         // el principal desaparecio sin etiqueta
                cerrada(3, "90102", 14), viva(4, "90109", 18),         // el juntado desaparecio antes de la etiqueta
                generada(5, "90103", 14), generada(6, "90110", 18),    // ya salieron juntos: se queda
                viva(7, "90104", 14), viva(8, "90111", 18));           // los dos siguen: se queda
        assertEquals(List.of(new Juntos.Deshacer(WEB, "90101", "90108 ya no está en ShippyPro"),
                new Juntos.Deshacer(WEB, "90102", "90102 ya no está en ShippyPro")), Juntos.aDeshacer(js, filas));
    }
}
```

- [ ] **Step 2: verlo fallar**

Run: `mvn -q test -Dtest=JuntosTest`
Expected: FAIL de compilación (`cannot find symbol: class Juntos`).

- [ ] **Step 3: la clase**

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** «Juntar pedidos» (spec 2026-11-20): dos o mas pedidos web de la misma tienda salen en una sola caja con la etiqueta del principal.
 *  Reglas puras: el estado de un pedido por sus filas, quien es el principal, si se pueden juntar (con el motivo en palabras), los grupos
 *  que sirve la tabla y los que la lectura de ShippyPro deshace sola. No toca la base de datos. La web repite estas reglas en
 *  {@code pedidos/juntar.ts}: si cambias un motivo o el orden de las comprobaciones, cambialo alli tambien. */
public final class Juntos {

    private Juntos() {}

    /** Una fila de Pedido_junto: {@code pedido} (A) sale en la caja de {@code principal} (B), en el mismo canal. */
    public record Junto(Canal canal, String pedido, String principal, LocalDateTime juntadoEn, Integer juntadoPor) {}

    /** PENDIENTE: alguna fila viva y ninguna con envio; GENERADA: con etiqueta y sin imprimir; ENVIADO: alguna etiqueta impresa (ya
     *  salio); NO_PENDIENTE: ya no esta en ShippyPro (todas cerradas, ninguna con envio). */
    public enum EstadoPedido { PENDIENTE, GENERADA, ENVIADO, NO_PENDIENTE }

    /** Resultado de {@link #decidir}: con {@code motivo} no se junta; sin el, {@code juntado} va en la caja de {@code principal}.
     *  {@code yaEstaba}: los dos ya iban juntos (repetir la peticion no escribe nada). */
    public record Decision(String principal, String juntado, String motivo, boolean yaEstaba) {
        static Decision no(String motivo) {
            return new Decision(null, null, motivo, false);
        }

        public boolean hecha() {
            return motivo == null;
        }
    }

    /** Un grupo para la tabla: el principal y sus juntados (ordenados). */
    public record Grupo(String principal, List<String> juntados) {}

    /** Un grupo que la lectura deshace sola: {@code pedido} (A) deja de ir con su principal porque {@code motivo}. */
    public record Deshacer(Canal canal, String pedido, String motivo) {}

    static final String MISMO = "es el mismo pedido";
    static final String AMAZON = "Amazon: cada pedido lleva su propio seguimiento";
    static final String DOS_ETIQUETAS = "los dos tienen su propia etiqueta: son dos envíos";

    static String noHay(String pedido) {
        return "no hay ningún pedido " + pedido + " en esa tienda";
    }

    static String yaVa(String pedido, String principal) {
        return pedido + " ya va con " + principal;
    }

    static String yaNoEsta(String pedido) {
        return pedido + " ya no está en ShippyPro: solo se juntan pedidos que siguen allí";
    }

    static String yaSalio(String pedido) {
        return pedido + " ya salió: esa caja ya no se puede abrir";
    }

    static String esPrincipal(String pedido) {
        return pedido + " ya es el principal de otro envío";
    }

    /** El motivo con que la lectura deshace un grupo. */
    public static String desaparecido(String pedido) {
        return pedido + " ya no está en ShippyPro";
    }

    public static EstadoPedido estado(List<FilaPedido> filas) {
        if (filas.stream().anyMatch(f -> f.enviada() && f.impresaEn() != null)) return EstadoPedido.ENVIADO;
        if (filas.stream().anyMatch(FilaPedido::enviada)) return EstadoPedido.GENERADA;
        if (filas.stream().anyMatch(FilaPedido::viva)) return EstadoPedido.PENDIENTE;
        return EstadoPedido.NO_PENDIENTE;
    }

    /** ¿Se pueden juntar los pedidos {@code a} y {@code b} del canal? {@code filas}: las de ShippyPro de los dos (pueden venir mas);
     *  {@code juntos}: los grupos en los que estan {@code a} o {@code b} (como juntado o como principal). Orden de las comprobaciones (spec §1):
     *  mismo pedido; Amazon; ya juntos entre ellos (no es un error); alguno no existe; alguno ya va con otro; alguno ya no esta en
     *  ShippyPro; alguno ya salio; los dos con etiqueta; y, decidido el principal, que el juntado no sea principal de otros (sin cadenas). */
    public static Decision decidir(Canal canal, String a, String b, List<FilaPedido> filas, List<Junto> juntos) {
        if (a.equals(b)) return Decision.no(MISMO);
        if (!canal.esWeb()) return Decision.no(AMAZON);
        for (Junto j : juntos)
            if (j.canal() == canal && ((j.pedido().equals(a) && j.principal().equals(b)) || (j.pedido().equals(b) && j.principal().equals(a))))
                return new Decision(j.principal(), j.pedido(), null, true);
        List<FilaPedido> fa = dePedido(filas, canal, a);
        List<FilaPedido> fb = dePedido(filas, canal, b);
        if (fa.isEmpty()) return Decision.no(noHay(a));
        if (fb.isEmpty()) return Decision.no(noHay(b));
        for (String p : List.of(a, b)) {
            String con = principalDe(juntos, canal, p);
            if (con != null) return Decision.no(yaVa(p, con));
        }
        EstadoPedido ea = estado(fa);
        EstadoPedido eb = estado(fb);
        if (ea == EstadoPedido.NO_PENDIENTE) return Decision.no(yaNoEsta(a));
        if (eb == EstadoPedido.NO_PENDIENTE) return Decision.no(yaNoEsta(b));
        if (ea == EstadoPedido.ENVIADO) return Decision.no(yaSalio(a));
        if (eb == EstadoPedido.ENVIADO) return Decision.no(yaSalio(b));
        if (ea == EstadoPedido.GENERADA && eb == EstadoPedido.GENERADA) return Decision.no(DOS_ETIQUETAS);
        String principal = principal(a, fa, ea, b, fb, eb);
        String juntado = principal.equals(a) ? b : a;
        for (Junto j : juntos)
            if (j.canal() == canal && j.principal().equals(juntado)) return Decision.no(esPrincipal(juntado));
        return new Decision(principal, juntado, null, false);
    }

    /** El principal: el que ya tiene etiqueta; si ninguno, el mas reciente por la fecha de entrada (la FECHA mas antigua de sus filas)
     *  y, a igual fecha, el de la fila mas nueva (el id mas alto de la primera fila de cada uno). */
    static String principal(String a, List<FilaPedido> fa, EstadoPedido ea, String b, List<FilaPedido> fb, EstadoPedido eb) {
        if (ea == EstadoPedido.GENERADA) return a;
        if (eb == EstadoPedido.GENERADA) return b;
        Comparator<List<FilaPedido>> entrada = Comparator
                .comparing((List<FilaPedido> fs) -> fs.stream().map(FilaPedido::fecha).min(Comparator.naturalOrder()).orElseThrow())
                .thenComparingInt(fs -> fs.stream().mapToInt(FilaPedido::id).min().orElseThrow());
        return entrada.compare(fa, fb) > 0 ? a : b;
    }

    /** Juntado -> principal. */
    public static Map<SincroShippyPro.ClavePedido, SincroShippyPro.ClavePedido> mapa(List<Junto> juntos) {
        Map<SincroShippyPro.ClavePedido, SincroShippyPro.ClavePedido> m = new HashMap<>();
        for (Junto j : juntos)
            m.put(new SincroShippyPro.ClavePedido(j.canal(), j.pedido()), new SincroShippyPro.ClavePedido(j.canal(), j.principal()));
        return m;
    }

    /** El grupo de cada pedido juntado y de cada principal. */
    public static Map<SincroShippyPro.ClavePedido, Grupo> grupos(List<Junto> juntos) {
        Map<SincroShippyPro.ClavePedido, List<String>> porPrincipal = new LinkedHashMap<>();
        for (Junto j : juntos)
            porPrincipal.computeIfAbsent(new SincroShippyPro.ClavePedido(j.canal(), j.principal()), k -> new ArrayList<>()).add(j.pedido());
        Map<SincroShippyPro.ClavePedido, Grupo> r = new HashMap<>();
        porPrincipal.forEach((p, js) -> {
            Grupo g = new Grupo(p.pedido(), js.stream().sorted().toList());
            r.put(p, g);
            for (String a : g.juntados()) r.put(new SincroShippyPro.ClavePedido(p.canal(), a), g);
        });
        return r;
    }

    /** Los grupos que se deshacen solos (spec §2): el principal ya no tiene filas vivas ni envio (desaparecio de ShippyPro sin etiqueta),
     *  o el juntado tampoco (desaparecio antes de la etiqueta). Primero se mira el principal. */
    public static List<Deshacer> aDeshacer(List<Junto> juntos, List<FilaPedido> filas) {
        List<Deshacer> r = new ArrayList<>();
        for (Junto j : juntos) {
            if (!sigue(dePedido(filas, j.canal(), j.principal()))) r.add(new Deshacer(j.canal(), j.pedido(), desaparecido(j.principal())));
            else if (!sigue(dePedido(filas, j.canal(), j.pedido()))) r.add(new Deshacer(j.canal(), j.pedido(), desaparecido(j.pedido())));
        }
        return r;
    }

    private static boolean sigue(List<FilaPedido> filas) {
        return filas.stream().anyMatch(f -> f.viva() || f.enviada());
    }

    private static String principalDe(List<Junto> juntos, Canal canal, String pedido) {
        for (Junto j : juntos) if (j.canal() == canal && j.pedido().equals(pedido)) return j.principal();
        return null;
    }

    private static List<FilaPedido> dePedido(List<FilaPedido> filas, Canal canal, String pedido) {
        return filas.stream().filter(f -> f.canal() == canal && pedido.equals(f.pedido()) && SincroShippyPro.ORIGEN.equals(f.origen())).toList();
    }
}
```

- [ ] **Step 4: verde**

Run: `mvn -q test -Dtest=JuntosTest`
Expected: PASS (9 tests).

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/Juntos.java src/test/java/com/reparaciones/servidor/pedidos/JuntosTest.java
git commit -m "feat: reglas para juntar pedidos web (principal, motivos, grupos y grupos que se deshacen solos)"
```

---

### Task 3: Servidor — `FilasDAO`: leer, juntar y separar

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/JuntarDAOTest.java`

**Interfaces:**
- Consumes: `Juntos.*` (Task 2), `CambioImei.JUNTADO/SEPARADO` (Task 1), `SincroShippyPro.MarcaEnvio`, `FilasDAO.marcarEnviadas`,
  `FilasDAO.SQL_CAMBIO_DETALLE`, `FilasDAO.SEP_DETALLE`, `FilasDAO.Resultado`.
- Produces:
  - `List<Juntos.Junto> juntos()` — toda la tabla (es pequeña).
  - `List<FilaPedido> deShippyProDePedidos(Collection<SincroShippyPro.ClavePedido>)` — filas de ShippyPro (vivas o cerradas) de esos pedidos.
  - `Juntos.Decision juntar(Canal canal, String pedido, String con, Integer idUsu, LocalDateTime ahora)` — transaccional.
  - `Resultado separar(Canal canal, String pedido, Integer idUsu, LocalDateTime ahora, String motivo)` — transaccional: `SIN_CAMBIO` (no
    estaba juntado), `NO_MARCABLE` (sus filas ya tienen el envío), `PUESTO`.
  - constantes `SQL_JUNTOS`, `SQL_FILAS_JUNTAR_BLOQUEADAS`, `SQL_JUNTOS_BLOQUEADOS`, `SQL_INSERTAR_JUNTO`, `SQL_FILAS_PEDIDO_BLOQUEADAS`,
    `SQL_JUNTO_BLOQUEADO`, `SQL_BORRAR_JUNTO`, `SQL_DE_PEDIDOS_SHIPPYPRO` (package-private, los usan los tests).

- [ ] **Step 1: el test que falla**

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Juntar y separar en el DAO (JdbcTemplate simulado; datos inventados). */
@SuppressWarnings("unchecked")
class JuntarDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime ENVIO = LocalDateTime.of(2026, 11, 20, 9, 0);
    private static final Timestamp T = Timestamp.valueOf(AHORA);
    private static final String C = "PARTICULARES";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private static FilaPedido viva(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", Canal.PARTICULARES, id, null);
    }

    private static FilaPedido cerradaSinEnvio(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", Canal.PARTICULARES, id, AHORA.minusHours(1));
    }

    private static FilaPedido generada(int id, String pedido, int dia) {
        return new FilaPedido(id, LocalDate.of(2026, 11, dia), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, dia),
                "SHIPPYPRO", Canal.PARTICULARES, id, ENVIO, ENVIO, "UPSv2", "1Z0099000001", null, 555L, null);
    }

    private void filas(String a, String b, List<FilaPedido> filas, List<Juntos.Junto> juntos) {
        doReturn(filas).when(jdbc).query(eq(FilasDAO.SQL_FILAS_JUNTAR_BLOQUEADAS), any(RowMapper.class), eq(C), eq(a), eq(b));
        doReturn(juntos).when(jdbc).query(eq(FilasDAO.SQL_JUNTOS_BLOQUEADOS), any(RowMapper.class), eq(C), eq(a), eq(b), eq(a), eq(b));
    }

    @Test void juntarDosPendientesGuardaElGrupoYApuntaEnLasFilasDelJuntado() {
        filas("90101", "90108", List.of(viva(1, "90101", 14), viva(2, "90101", 14), viva(3, "90108", 18)), List.of());
        Juntos.Decision d = dao.juntar(Canal.PARTICULARES, "90101", "90108", 9, AHORA);
        assertEquals("90108", d.principal());
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(FilasDAO.SQL_FILAS_JUNTAR_BLOQUEADAS), any(RowMapper.class), eq(C), eq("90101"), eq("90108"));
        orden.verify(jdbc).query(eq(FilasDAO.SQL_JUNTOS_BLOQUEADOS), any(RowMapper.class), eq(C), eq("90101"), eq("90108"), eq("90101"), eq("90108"));
        orden.verify(jdbc).update(FilasDAO.SQL_INSERTAR_JUNTO, C, "90101", "90108", T, 9);
        orden.verify(jdbc).batchUpdate(eq(FilasDAO.SQL_CAMBIO_DETALLE), argThat((List<Object[]> l) -> l.size() == 2
                && Arrays.equals(l.get(0), new Object[]{1, 9, T, "JUNTADO", "90108"})
                && Arrays.equals(l.get(1), new Object[]{2, 9, T, "JUNTADO", "90108"})));
        // El principal no tiene etiqueta: nadie recibe envio todavia.
        verify(jdbc, never()).batchUpdate(eq(FilasDAO.SQL_MARCAR_ENVIO), anyList());
    }

    @Test void conElPrincipalYaEtiquetadoLasFilasVivasDelJuntadoRecibenSuEnvioAlMomento() {
        filas("90108", "90101", List.of(viva(1, "90108", 18), cerradaSinEnvio(2, "90108", 18), generada(3, "90101", 14)), List.of());
        Juntos.Decision d = dao.juntar(Canal.PARTICULARES, "90108", "90101", 9, AHORA);
        assertEquals("90101", d.principal());
        assertEquals("90108", d.juntado());
        // Solo la viva (1): la cerrada sin envio (2) es un movil que ya no va en el pedido.
        verify(jdbc).batchUpdate(eq(FilasDAO.SQL_MARCAR_ENVIO), argThat((List<Object[]> l) -> l.size() == 1
                && Arrays.equals(l.get(0), new Object[]{Timestamp.valueOf(ENVIO), "UPSv2", "1Z0099000001", T, 555L, null, 1})));
    }

    @Test void siNoSePuedeNoEscribeNada() {
        filas("90101", "90108", List.of(viva(1, "90101", 14)), List.of());
        Juntos.Decision d = dao.juntar(Canal.PARTICULARES, "90101", "90108", 9, AHORA);
        assertEquals("no hay ningún pedido 90108 en esa tienda", d.motivo());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test void repetirLaJuntaNoEscribeNada() {
        filas("90101", "90108", List.of(viva(1, "90101", 14), viva(3, "90108", 18)),
                List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA.minusMinutes(5), 9)));
        assertTrue(dao.juntar(Canal.PARTICULARES, "90101", "90108", 9, AHORA).yaEstaba());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    private void separable(List<FilaPedido> filasA, List<Juntos.Junto> junto) {
        doReturn(filasA).when(jdbc).query(eq(FilasDAO.SQL_FILAS_PEDIDO_BLOQUEADAS), any(RowMapper.class), eq(C), eq("90101"));
        doReturn(junto).when(jdbc).query(eq(FilasDAO.SQL_JUNTO_BLOQUEADO), any(RowMapper.class), eq(C), eq("90101"));
    }

    @Test void separarBorraElGrupoYApuntaConElPrincipal() {
        separable(List.of(viva(1, "90101", 14)), List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA, 9)));
        assertEquals(FilasDAO.Resultado.PUESTO, dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null));
        verify(jdbc).update(FilasDAO.SQL_BORRAR_JUNTO, C, "90101");
        verify(jdbc).batchUpdate(eq(FilasDAO.SQL_CAMBIO_DETALLE), argThat((List<Object[]> l) -> l.size() == 1
                && Arrays.equals(l.get(0), new Object[]{1, 7, T, "SEPARADO", "90108"})));
    }

    @Test void laLecturaSeparaSinUsuarioYConElMotivo() {
        separable(List.of(viva(1, "90101", 14)), List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA, 9)));
        dao.separar(Canal.PARTICULARES, "90101", null, AHORA, "90108 ya no está en ShippyPro");
        verify(jdbc).batchUpdate(eq(FilasDAO.SQL_CAMBIO_DETALLE), argThat((List<Object[]> l) ->
                Arrays.equals(l.get(0), new Object[]{1, null, T, "SEPARADO", "90108 — 90108 ya no está en ShippyPro"})));
    }

    @Test void separarConElEnvioYaPuestoNoSePuedeYSinGrupoNoHaceNada() {
        separable(List.of(generada(1, "90101", 14)), List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA, 9)));
        assertEquals(FilasDAO.Resultado.NO_MARCABLE, dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null));
        separable(List.of(viva(1, "90101", 14)), List.of());
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    /** Dos «juntar» a la vez sobre el mismo pedido: el segundo espera al bloqueo de las filas y despues ve el grupo ya guardado
     *  (READ COMMITTED), asi que da «ya va con» o «ya estaba». Aqui se comprueba que las dos transacciones bloquean y su aislamiento;
     *  la carrera real se prueba en el E2E de preprod. */
    @Test void juntarYSepararBloqueanLasFilasConReadCommitted() {
        for (String nombre : List.of("juntar", "separar")) {
            Method m = Arrays.stream(FilasDAO.class.getMethods()).filter(x -> x.getName().equals(nombre)).findFirst().orElseThrow();
            assertEquals(Isolation.READ_COMMITTED, m.getAnnotation(Transactional.class).isolation(), nombre);
        }
        for (String sql : List.of(FilasDAO.SQL_FILAS_JUNTAR_BLOQUEADAS, FilasDAO.SQL_JUNTOS_BLOQUEADOS, FilasDAO.SQL_FILAS_PEDIDO_BLOQUEADAS,
                FilasDAO.SQL_JUNTO_BLOQUEADO))
            assertTrue(sql.endsWith(" FOR UPDATE"), sql);
    }
}
```

- [ ] **Step 2: verlo fallar**

Run: `mvn -q test -Dtest=JuntarDAOTest`
Expected: FAIL de compilación (faltan `juntar`, `separar` y las constantes).

- [ ] **Step 3: el DAO** — en `FilasDAO.java`, junto a las demás constantes SQL:

```java
    // ---------------------------------------------------------------- pedidos juntados (spec 2026-11-20)
    static final String SQL_JUNTOS = "SELECT CANAL, PEDIDO, PEDIDO_PRINCIPAL, JUNTADO_EN, JUNTADO_POR FROM Pedido_junto";
    /** Las filas de ShippyPro de los dos pedidos, bloqueadas: dos «juntar» que comparten un pedido se hacen uno detras de otro. */
    static final String SQL_FILAS_JUNTAR_BLOQUEADAS = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND PEDIDO IN (?, ?)"
            + " ORDER BY ID_FILA FOR UPDATE";
    /** Los grupos en los que estan los dos pedidos, como juntado o como principal. */
    static final String SQL_JUNTOS_BLOQUEADOS = SQL_JUNTOS + " WHERE CANAL = ? AND (PEDIDO IN (?, ?) OR PEDIDO_PRINCIPAL IN (?, ?)) FOR UPDATE";
    static final String SQL_INSERTAR_JUNTO = "INSERT INTO Pedido_junto (CANAL, PEDIDO, PEDIDO_PRINCIPAL, JUNTADO_EN, JUNTADO_POR)"
            + " VALUES (?, ?, ?, ?, ?)";
    static final String SQL_FILAS_PEDIDO_BLOQUEADAS = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND PEDIDO = ?"
            + " ORDER BY ID_FILA FOR UPDATE";
    static final String SQL_JUNTO_BLOQUEADO = SQL_JUNTOS + " WHERE CANAL = ? AND PEDIDO = ? FOR UPDATE";
    static final String SQL_BORRAR_JUNTO = "DELETE FROM Pedido_junto WHERE CANAL = ? AND PEDIDO = ?";
    static final String SQL_DE_PEDIDOS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND (%s)";
```

  y los métodos (por ejemplo detrás de `sincronizar`):

```java
    /** Todos los grupos de pedidos juntados (la tabla es pequeña: un apunte por pedido juntado). */
    public List<Juntos.Junto> juntos() {
        return jdbc.query(SQL_JUNTOS, FilasDAO::junto);
    }

    /** Las filas de ShippyPro (vivas o cerradas) de esos pedidos, en tandas. */
    public List<FilaPedido> deShippyProDePedidos(Collection<SincroShippyPro.ClavePedido> pedidos) {
        return deShippyPro(SQL_DE_PEDIDOS_SHIPPYPRO, pedidos);
    }

    /** Junta los pedidos {@code pedido} y {@code con} del canal (spec 2026-11-20 §1 y §2) en una transaccion: READ COMMITTED, primero las
     *  filas de los dos pedidos bloqueadas y despues sus grupos; las reglas las decide {@link Juntos#decidir} con todo bloqueado. Si se
     *  puede: guarda el grupo, apunta JUNTADO (detalle = el principal) en todas las filas del juntado y, si el principal ya tiene etiqueta,
     *  pone su envio (el mas reciente de sus filas) en las filas VIVAS del juntado, que se cierran como enviadas. Si no se puede, o los dos
     *  ya iban juntos, no escribe nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Juntos.Decision juntar(Canal canal, String pedido, String con, Integer idUsu, LocalDateTime ahora) {
        List<FilaPedido> filas = jdbc.query(SQL_FILAS_JUNTAR_BLOQUEADAS, FilasDAO::fila, canal.name(), pedido, con);
        List<Juntos.Junto> juntos = jdbc.query(SQL_JUNTOS_BLOQUEADOS, FilasDAO::junto, canal.name(), pedido, con, pedido, con);
        Juntos.Decision d = Juntos.decidir(canal, pedido, con, filas, juntos);
        if (!d.hecha() || d.yaEstaba()) return d;
        Timestamp t = Timestamp.valueOf(ahora);
        jdbc.update(SQL_INSERTAR_JUNTO, canal.name(), d.juntado(), d.principal(), t, idUsu);
        List<FilaPedido> delJuntado = filas.stream().filter(f -> d.juntado().equals(f.pedido())).toList();
        List<Object[]> apuntes = new ArrayList<>();
        for (FilaPedido f : delJuntado) apuntes.add(new Object[]{f.id(), idUsu, t, CambioImei.JUNTADO, d.principal()});
        jdbc.batchUpdate(SQL_CAMBIO_DETALLE, apuntes);
        Optional<FilaPedido> envio = filas.stream().filter(f -> d.principal().equals(f.pedido()) && f.enviada())
                .max(java.util.Comparator.comparing(FilaPedido::envioFecha));
        if (envio.isPresent()) {
            FilaPedido e = envio.get();
            marcarEnviadas(delJuntado.stream().filter(FilaPedido::viva).map(f -> new SincroShippyPro.MarcaEnvio(f.id(), e.envioFecha(),
                    e.transportista(), e.seguimiento(), e.envioId(), e.impresaEn())).toList(), ahora);
        }
        return d;
    }

    /** Separa el pedido juntado {@code pedido} de su principal (spec §1: mientras sus filas no tengan el envio) y apunta SEPARADO en todas
     *  sus filas: detalle = el principal, o «principal — motivo» cuando lo separa la lectura ({@code motivo} no nulo, sin usuario). Misma
     *  transaccion que {@link #juntar}. SIN_CAMBIO si no estaba juntado (no escribe nada); NO_MARCABLE si alguna fila ya tiene el envio;
     *  si no, PUESTO. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado separar(Canal canal, String pedido, Integer idUsu, LocalDateTime ahora, String motivo) {
        List<FilaPedido> filas = jdbc.query(SQL_FILAS_PEDIDO_BLOQUEADAS, FilasDAO::fila, canal.name(), pedido);
        List<Juntos.Junto> junto = jdbc.query(SQL_JUNTO_BLOQUEADO, FilasDAO::junto, canal.name(), pedido);
        if (junto.isEmpty()) return Resultado.SIN_CAMBIO;
        if (filas.stream().anyMatch(FilaPedido::enviada)) return Resultado.NO_MARCABLE;
        jdbc.update(SQL_BORRAR_JUNTO, canal.name(), pedido);
        String principal = junto.get(0).principal();
        String detalle = motivo == null ? principal : principal + SEP_DETALLE + motivo;
        Timestamp t = Timestamp.valueOf(ahora);
        List<Object[]> apuntes = new ArrayList<>();
        for (FilaPedido f : filas) apuntes.add(new Object[]{f.id(), idUsu, t, CambioImei.SEPARADO, detalle});
        if (!apuntes.isEmpty()) jdbc.batchUpdate(SQL_CAMBIO_DETALLE, apuntes);
        return Resultado.PUESTO;
    }
```

  y el mapeador, en la sección de ayudas:

```java
    private static Juntos.Junto junto(ResultSet rs, int n) throws SQLException {
        int por = rs.getInt("JUNTADO_POR");
        Integer juntadoPor = rs.wasNull() ? null : por;
        return new Juntos.Junto(Canal.valueOf(rs.getString("CANAL")), rs.getString("PEDIDO"), rs.getString("PEDIDO_PRINCIPAL"),
                hora(rs, "JUNTADO_EN"), juntadoPor);
    }
```

  Actualiza el javadoc de `Resultado`: `SIN_CAMBIO` y `NO_MARCABLE` también los da `separar`.

- [ ] **Step 4: verde**

Run: `mvn -q test -Dtest='JuntarDAOTest,FaltaEnviarDAOTest,FilasDAOTest,MarcaDAOTest'`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java src/test/java/com/reparaciones/servidor/pedidos/JuntarDAOTest.java
git commit -m "feat: juntar y separar pedidos en el DAO con las filas bloqueadas y su historial"
```

---

### Task 4: Servidor — `JuntarController` (rutas, roles y contrato)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/JuntarController.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/JuntarControllerTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/RolesPedidosTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (test `elContratoPublicaLosPedidosComoLaHoja`)
- Modify: `docs/api_contract.md`, `docs/autorizacion_endpoints.md`

**Interfaces:**
- Consumes: `FilasDAO.juntar/separar` (Task 3), `SincroShippyPro.numeroGuardado(String)`.
- Produces: `POST /api/pedidos/juntar` cuerpo `JuntarPeticion {canal, pedido, con}` → `200 JuntarRespuesta {principal, juntado}`; 400
  (canal desconocido o números vacíos); 409 «No se puede juntar X con Y: <motivo>». `DELETE /api/pedidos/juntar/{canal}/{pedido}` → `204`
  (también si no estaba juntado); 409 «Este pedido ya tiene el envío de su principal: no se puede separar». Esquemas OpenAPI
  `JuntarPeticion` y `JuntarRespuesta` (records anidados: `OpenApiConfig` les antepone «Juntar»).

- [ ] **Step 1: el test que falla** (`JuntarControllerTest.java`)

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST y DELETE /api/pedidos/juntar: validacion, 409 con el motivo y llamada al DAO con el usuario y la hora. */
class JuntarControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 10, 0);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final JuntarController controller = new JuntarController(dao, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void juntarDevuelveElPrincipalYElJuntado() {
        when(dao.juntar(Canal.PARTICULARES, "90101", "90108", 7, AHORA)).thenReturn(new Juntos.Decision("90108", "90101", null, false));
        JuntarController.Respuesta r = controller.juntar(new JuntarController.Peticion("PARTICULARES", " 90101 ", "90108"), super_);
        assertEquals(new JuntarController.Respuesta("90108", "90101"), r);
    }

    @Test void siNoSePuedeEsUn409ConElMotivoEnPalabras() {
        when(dao.juntar(any(), any(), any(), any(), any())).thenReturn(new Juntos.Decision(null, null, "90108 ya salió: esa caja ya no se puede abrir", false));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.juntar(new JuntarController.Peticion("PARTICULARES", "90101", "90108"), super_));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("No se puede juntar 90101 con 90108: 90108 ya salió: esa caja ya no se puede abrir", e.getReason());
    }

    @Test void canalDesconocidoONumerosVaciosSon400SinLlegarAlDao() {
        assertEquals(400, estado(() -> controller.juntar(new JuntarController.Peticion("TIENDA", "90101", "90108"), super_)));
        assertEquals(400, estado(() -> controller.juntar(new JuntarController.Peticion(null, "90101", "90108"), super_)));
        assertEquals(400, estado(() -> controller.juntar(new JuntarController.Peticion("PARTICULARES", " ", "90108"), super_)));
        assertEquals(400, estado(() -> controller.juntar(new JuntarController.Peticion("PARTICULARES", "90101", null), super_)));
        assertEquals(400, estado(() -> controller.separar("TIENDA", "90101", super_)));
        verifyNoInteractions(dao);
    }

    @Test void separarEs204AunqueNoEstuvieraJuntadoY409SiYaTieneElEnvio() {
        when(dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null)).thenReturn(FilasDAO.Resultado.PUESTO);
        assertEquals(204, controller.separar("PARTICULARES", "90101", super_).getStatusCode().value());
        when(dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null)).thenReturn(FilasDAO.Resultado.SIN_CAMBIO);
        assertEquals(204, controller.separar("PARTICULARES", "90101", super_).getStatusCode().value());
        when(dao.separar(Canal.PARTICULARES, "90101", 7, AHORA, null)).thenReturn(FilasDAO.Resultado.NO_MARCABLE);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.separar("PARTICULARES", "90101", super_));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("Este pedido ya tiene el envío de su principal: no se puede separar", e.getReason());
    }
}
```

- [ ] **Step 2: verlo fallar** — `mvn -q test -Dtest=JuntarControllerTest` → no compila (falta la clase).

- [ ] **Step 3: el controller**

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

/** «Juntar pedidos» (spec 2026-11-20 §4): dos pedidos web de la misma tienda en una sola caja. Los mismos roles que ponen IMEI en
 *  Pedidos: SUPERTECNICO y ADMIN. Las reglas se deciden en la transaccion del DAO, con las filas bloqueadas. */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class JuntarController {

    static final String MSG_PETICION = "Indica la tienda y los dos números de pedido";
    static final String MSG_CON_ENVIO = "Este pedido ya tiene el envío de su principal: no se puede separar";

    private final FilasDAO dao;
    private final Clock clock;

    @Autowired
    public JuntarController(FilasDAO dao) {
        this(dao, Clock.system(ImportadorNsys.MADRID));
    }

    JuntarController(FilasDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** canal: el del enum Canal (DISTRIBUIDORES, PARTICULARES o BACKIPHONE; Amazon da 409); pedido y con: los dos numeros, en cualquier
     *  orden (el ERP decide el principal). */
    public record Peticion(@Schema(nullable = true) String canal, @Schema(nullable = true) String pedido, @Schema(nullable = true) String con) {}

    /** principal: el pedido con cuya etiqueta sale la caja; juntado: el que va en ella. */
    public record Respuesta(String principal, String juntado) {}

    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Respuesta.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    @PostMapping("/juntar")
    public Respuesta juntar(@RequestBody Peticion req, @AuthenticationPrincipal UsuarioPrincipal principal) {
        Canal canal = canal(req.canal());
        String pedido = numero(req.pedido());
        String con = numero(req.con());
        Juntos.Decision d = dao.juntar(canal, pedido, con, principal.getIdUsu(), LocalDateTime.now(clock));
        if (!d.hecha())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede juntar " + pedido + " con " + con + ": " + d.motivo());
        return new Respuesta(d.principal(), d.juntado());
    }

    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    @DeleteMapping("/juntar/{canal}/{pedido}")
    public ResponseEntity<Void> separar(@PathVariable String canal, @PathVariable String pedido,
                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        Canal c = canal(canal);
        String p = numero(pedido);
        FilasDAO.Resultado r = dao.separar(c, p, principal.getIdUsu(), LocalDateTime.now(clock), null);
        if (r == FilasDAO.Resultado.NO_MARCABLE) throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_CON_ENVIO);
        return ResponseEntity.noContent().build();
    }

    private static Canal canal(String texto) {
        try {
            return Canal.valueOf(texto == null ? "" : texto.strip());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_PETICION);
        }
    }

    /** El numero como lo guarda la lectura de ShippyPro (sin espacios, cortado al ancho). */
    private static String numero(String texto) {
        if (texto == null || texto.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_PETICION);
        return SincroShippyPro.numeroGuardado(texto);
    }
}
```

- [ ] **Step 4: roles** — en `RolesPedidosTest.java` añade los imports `delete`/`post` de `MockMvcRequestBuilders`, `verify`/`anyString`
  si faltan, y este test:

```java
    @Test void juntarYSepararSonDeSupertecnicoYAdmin() throws Exception {
        java.util.function.Function<String, MockHttpServletRequestBuilder> juntar = t -> post("/api/pedidos/juntar").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"canal\":\"PARTICULARES\",\"pedido\":\"90101\",\"con\":\"90108\"}");
        java.util.function.Function<String, MockHttpServletRequestBuilder> separar = t -> delete("/api/pedidos/juntar/PARTICULARES/90101")
                .header("Authorization", t);
        mvc.perform(juntar.apply(tecnico())).andExpect(status().isForbidden());
        mvc.perform(separar.apply(tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(dao);
        when(dao.juntar(any(), any(), any(), any(), any())).thenReturn(new com.reparaciones.servidor.pedidos.Juntos.Decision("90108", "90101", null, false));
        when(dao.separar(any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        for (String token : List.of(supertecnico(), admin())) {
            mvc.perform(juntar.apply(token)).andExpect(status().isOk());
            mvc.perform(separar.apply(token)).andExpect(status().isNoContent());
        }
    }
```

  (`Juntos.Decision` es pública con constructor público de record: se puede crear desde el paquete `controller`.)

- [ ] **Step 5: contrato OpenAPI** — en `OpenApiContractTest.elContratoPublicaLosPedidosComoLaHoja`, añade a la lista de operaciones
  `"post /api/pedidos/juntar"` y `"delete /api/pedidos/juntar/{canal}/{pedido}"`; a la de esquemas, `"JuntarPeticion"` y `"JuntarRespuesta"`;
  y al final:

```java
        assertNullable(esquemas, "JuntarPeticion", "canal", "pedido", "con");
        assertNoNullable(esquemas, "JuntarRespuesta", "principal", "juntado");
```

- [ ] **Step 6: docs** — en `docs/api_contract.md`, detrás del punto de `PUT /api/pedidos/filas/{id}/falta-enviar`:

```markdown
  - `POST /api/pedidos/juntar` con `JuntarPeticion` `{canal, pedido, con}` → `200` `JuntarRespuesta` `{principal, juntado}` («Juntar
    pedidos», spec 2026-11-20): dos pedidos web (`DISTRIBUIDORES`, `PARTICULARES` o `BACKIPHONE`) de la misma tienda salen en una sola caja
    con la etiqueta del principal. El ERP decide el principal: el que ya tiene etiqueta; si ninguno, el más reciente (fecha de entrada; a
    igual fecha, la fila más nueva). Se puede juntar un pedido pendiente sin etiqueta con otro pendiente o con la etiqueta generada y sin
    imprimir; un principal puede llevar varios; no hay cadenas. Si no se puede: `409` «No se puede juntar X con Y: <motivo>» (es el mismo
    pedido; Amazon; no hay ningún pedido; ya va con otro; ya no está en ShippyPro; ya salió; los dos tienen su propia etiqueta; ya es el
    principal de otro envío). Repetir la misma petición devuelve `200` sin escribir nada. `400` si el canal no existe o falta un número.
    Comprobación y escritura atómicas, con las filas de los dos pedidos bloqueadas. Si el principal ya tiene etiqueta, las filas vivas
    del juntado reciben su envío al momento (pasan a `ENVIADA`). Cada fila del juntado apunta `JUNTADO` (detalle = el principal).
    SUPERTECNICO y ADMIN.
  - `DELETE /api/pedidos/juntar/{canal}/{pedido}` → `204`: separa el pedido juntado de su principal (también `204` si no estaba
    juntado). `409` «Este pedido ya tiene el envío de su principal: no se puede separar» si sus filas ya tienen el envío. Apunta
    `SEPARADO` (detalle = el principal) en sus filas. La lectura de ShippyPro separa sola, sin usuario y con detalle «<principal> —
    <pedido> ya no está en ShippyPro», los grupos cuyo principal o juntado ya no tiene filas vivas ni envío. SUPERTECNICO y ADMIN.
```

  En la descripción de `FilaVista` de ese mismo fichero (busca `fechaSalida`), añade: «`juntoPrincipal` (nullable): en un pedido juntado o
  en un principal, el pedido principal del grupo; `juntados` (lista, vacía si no hay grupo): los pedidos juntados en esa caja, ordenados».
  En `docs/autorizacion_endpoints.md`, en la línea de «Pedidos como la hoja» (la que lista los `PUT /api/pedidos/filas/{id}/...`), añade
  «juntar y separar pedidos (`POST /api/pedidos/juntar`, `DELETE /api/pedidos/juntar/{canal}/{pedido}`)» a la lista de SUPERTECNICO y ADMIN.
  (Los campos de `FilaVista` llegan en la Task 5; la doc va ya aquí para no tocarla dos veces.)

- [ ] **Step 7: verde**

Run: `mvn -q test -Dtest='JuntarControllerTest,RolesPedidosTest,OpenApiContractTest'`
Expected: PASS.

- [ ] **Step 8: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/JuntarController.java src/test/java/com/reparaciones/servidor/pedidos/JuntarControllerTest.java src/test/java/com/reparaciones/servidor/controller/RolesPedidosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java docs/api_contract.md docs/autorizacion_endpoints.md
git commit -m "feat: rutas para juntar y separar pedidos web, con el motivo en palabras si no se puede"
```

---

### Task 5: Servidor — las filas dicen su grupo (`juntoPrincipal`, `juntados`)

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java`
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java` (método `vistas`)
- Modify: `src/test/java/com/reparaciones/servidor/pedidos/VistaFilasTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `FilasDAO.juntos()` (Task 3), `Juntos.grupos` (Task 2).
- Produces: `FilaVista` con dos componentes más al final: `@Schema(nullable = true) String juntoPrincipal`, `List<String> juntados` (nunca
  nulo). El constructor de 32 argumentos que hay hoy sigue existiendo (pone `null` y `List.of()`), así no se tocan los tests que lo usan.

- [ ] **Step 1: el test que falla** — en `VistaFilasTest.java` (añade `import java.util.List;` si falta):

```java
    private static FilaPedido web(int id, String pedido) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 18), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, 18),
                "SHIPPYPRO", Canal.PARTICULARES, id, null);
    }

    @Test void lasFilasDeUnGrupoLlevanElPrincipalYLosJuntados() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(web(1, "90101"), web(2, "90108"), web(3, "90108"), web(4, "90120")));
        when(dao.juntos()).thenReturn(List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA, 9)));
        List<FilaVista> filas = vista.filas(null, null, null).filas();
        for (int i = 0; i < 3; i++) {
            assertEquals("90108", filas.get(i).juntoPrincipal(), "fila " + filas.get(i).idFila());
            assertEquals(List.of("90101"), filas.get(i).juntados());
        }
        assertNull(filas.get(3).juntoPrincipal());
        assertEquals(List.of(), filas.get(3).juntados());
    }

    @Test void sinGruposNadieLlevaPrincipal() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(web(1, "90101")));
        FilaVista f = vista.filas(null, null, null).filas().get(0);
        assertNull(f.juntoPrincipal());
        assertEquals(List.of(), f.juntados());
    }
```

  (Usa los imports estáticos de Mockito que ya tiene el fichero; si `any` no está importado, `import static org.mockito.ArgumentMatchers.any;`.)

- [ ] **Step 2: verlo fallar** — `mvn -q test -Dtest=VistaFilasTest` → no compila (`juntoPrincipal()` no existe).

- [ ] **Step 3: `FilaVista`** — añade los dos componentes al final del record canónico:

```java
                        @Schema(nullable = true) LocalDate fechaSalida, @Schema(nullable = true) String pais,
                        @Schema(nullable = true) String juntoPrincipal, List<String> juntados) {

    /** Una fila que no esta en ningun grupo de pedidos juntados. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado, LocalDate fechaSalida, String pais) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, fechaSalida, pais, null, List.of());
    }
```

  (`import java.util.List;`). El constructor «Una fila sin fecha de salida ni pais» llama hoy al canónico con 32 argumentos: déjalo
  llamando a este nuevo constructor de 32 (no cambia nada en su cuerpo). Añade al javadoc: «juntoPrincipal: en un pedido juntado o en un
  principal (spec 2026-11-20), el pedido principal del grupo, nulo si no hay grupo; juntados: los pedidos juntados en esa caja, ordenados
  (vacía sin grupo)».

- [ ] **Step 4: `VistaFilas.vistas`** — antes del bucle que crea las vistas:

```java
        // Los grupos de pedidos juntados (spec 2026-11-20 §4): la tabla los pinta pegados sin otra llamada.
        Map<SincroShippyPro.ClavePedido, Juntos.Grupo> grupos = Juntos.grupos(dao.juntos());
```

  y dentro del bucle, antes de `vistas.add(...)`:

```java
            Juntos.Grupo grupo = HISTORICO.equals(estado) || f.canal() == null || f.pedido() == null ? null
                    : grupos.get(new SincroShippyPro.ClavePedido(f.canal(), f.pedido()));
```

  y en el `new FilaVista(...)` añade al final `, grupo == null ? null : grupo.principal(), grupo == null ? List.of() : grupo.juntados()`
  (ojo: la variable `estado` se calcula antes de esta línea en el bucle actual; si no, muévela arriba).

- [ ] **Step 5: contrato** — en `OpenApiContractTest.elContratoPublicaLosPedidosComoLaHoja`:

```java
        assertNullable(esquemas, "FilaVista", "juntoPrincipal");
        assertNoNullable(esquemas, "FilaVista", "juntados");
```

- [ ] **Step 6: verde**

Run: `mvn -q test -Dtest='VistaFilasTest,OpenApiContractTest,RolesPedidosTest,FilasControllerTest'`
Expected: PASS; `target/openapi.json` trae `juntoPrincipal`, `juntados`, `/api/pedidos/juntar`.

- [ ] **Step 7: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java src/test/java/com/reparaciones/servidor/pedidos/VistaFilasTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: cada fila de pedidos dice su pedido principal y los juntados de su caja"
```

---

### Task 6: Servidor — la lectura de ShippyPro pone el envío del principal en los juntados

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/SincroShippyPro.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/SincroShippyProTest.java`

**Interfaces:**
- Consumes: `Map<ClavePedido, ClavePedido> juntos` (juntado → principal, de `Juntos.mapa`).
- Produces (las firmas actuales siguen, delegando con `Map.of()`):
  - `marcarEnviadas(enviados, canales, pendientes, candidatas, juntos)`
  - `completarEtiquetas(enviados, canales, pendientes, candidatas, juntos)`
  - `clavesEnviadas(enviados, canales, juntos)`
  - `de(pendientes, canales, acepta, vivas, cerradas, hoy, juntos)`

  Reglas (spec §2): un pedido juntado sin envío propio toma el del principal; mientras el juntado sigue en ShippyPro (la web aún no lo ha
  completado) cuenta como «no pendiente» si su principal ya no lo está; el envío del principal solo entra en las filas **vivas** del
  juntado; una fila cerrada del juntado con envío no se reabre aunque el juntado siga en ShippyPro, salvo que el principal vuelva a estar
  pendiente.

- [ ] **Step 1: el test que falla** — en `SincroShippyProTest.java` (CANALES ya tiene `"200" → PARTICULARES`):

```java
    // ---------------------------------------------------------------- pedidos juntados (spec 2026-11-20 §2)

    private static final SincroShippyPro.ClavePedido A = new SincroShippyPro.ClavePedido(Canal.PARTICULARES, "90101");
    private static final SincroShippyPro.ClavePedido P = new SincroShippyPro.ClavePedido(Canal.PARTICULARES, "90108");
    private static final Map<SincroShippyPro.ClavePedido, SincroShippyPro.ClavePedido> JUNTOS = Map.of(A, P);

    private static FilaPedido vivaWeb(int id, String pedido, int posicion) {
        return new FilaPedido(id, HOY, "Cliente Web A", "i13128AzulA", null, pedido, HOY, "SHIPPYPRO", Canal.PARTICULARES, posicion, null);
    }

    @Test void lasFilasVivasDelJuntadoRecibenElEnvioDelPrincipalAunqueElJuntadoSigaPendiente() {
        FilaPedido cerradaDelJuntado = new FilaPedido(3, HOY, "Cliente Web A", "i13128AzulA", null, "90101", HOY, "SHIPPYPRO",
                Canal.PARTICULARES, 2, HOY.atStartOfDay());
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(List.of(envio("90108", "200", 19, "1Z0099000001")), CANALES,
                Set.of(A), List.of(vivaWeb(1, "90101", 1), cerradaDelJuntado, vivaWeb(2, "90108", 1)), JUNTOS);
        assertEquals(List.of(1, 2), m.stream().map(SincroShippyPro.MarcaEnvio::fila).toList());
        assertEquals("1Z0099000001", m.get(0).seguimiento());
    }

    @Test void conElPrincipalAunPendienteElJuntadoNoRecibeNada() {
        assertTrue(SincroShippyPro.marcarEnviadas(List.of(envio("90108", "200", 19, "1Z0099000001")), CANALES, Set.of(A, P),
                List.of(vivaWeb(1, "90101", 1)), JUNTOS).isEmpty());
    }

    @Test void sinGruposNadaCambia() {
        assertTrue(SincroShippyPro.marcarEnviadas(List.of(envio("90108", "200", 19, "1Z0099000001")), CANALES, Set.of(A),
                List.of(vivaWeb(1, "90101", 1)), Map.of()).isEmpty());
    }

    @Test void laImpresionDelPrincipalLlegaTambienALasFilasDelJuntado() {
        LocalDateTime imp = LocalDateTime.of(2026, 11, 19, 15, 0);
        FilaPedido delJuntado = new FilaPedido(1, HOY, "Cliente Web A", "i13128AzulA", null, "90101", HOY, "SHIPPYPRO", Canal.PARTICULARES,
                1, HOY.atStartOfDay(), LocalDateTime.of(2026, 11, 19, 12, 0), "UPSv2", "x", null, 777L, null);
        List<SincroShippyPro.MarcaEtiqueta> m = SincroShippyPro.completarEtiquetas(
                List.of(new ClienteShippyPro.EnvioShippyPro("90108", "200", LocalDateTime.of(2026, 11, 19, 12, 0), "UPSv2", "x", 777L, imp)),
                CANALES, Set.of(A), List.of(delJuntado), JUNTOS);
        assertEquals(List.of(new SincroShippyPro.MarcaEtiqueta(1, null, imp)), m);
    }

    @Test void lasClavesEnviadasIncluyenLosJuntadosDeUnPrincipalConEnvio() {
        assertEquals(Set.of(P, A), SincroShippyPro.clavesEnviadas(List.of(envio("90108", "200", 19, "x")), CANALES, JUNTOS));
    }

    @Test void unaFilaDelJuntadoQueYaSalioNoSeReabreAunqueElJuntadoSigaEnShippyPro() {
        FilaPedido salio = new FilaPedido(1, HOY, "Cliente Web A", "i13128AzulA", null, "90101", HOY, "SHIPPYPRO", Canal.PARTICULARES, 1,
                HOY.atStartOfDay(), HOY.atStartOfDay(), "UPSv2", "x");
        PedidoShippyPro a = pedido("90101", "200", "Cliente Web A", art("i13128AzulA", 1));
        SincroShippyPro.Plan p = SincroShippyPro.de(List.of(a), CANALES, ACEPTA, List.of(), List.of(salio), HOY, JUNTOS);
        assertTrue(p.reabrir().isEmpty());
        assertTrue(p.insertar().isEmpty());
        // Si el principal vuelve a estar pendiente (etiqueta anulada), la fila del juntado se reabre como siempre.
        PedidoShippyPro b = pedido("90108", "200", "Cliente Web A", art("i13128AzulA", 1));
        assertEquals(List.of(1), SincroShippyPro.de(List.of(a, b), CANALES, ACEPTA, List.of(), List.of(salio), HOY, JUNTOS).reabrir());
    }
```

- [ ] **Step 2: verlo fallar** — `mvn -q test -Dtest=SincroShippyProTest` → no compila (faltan las sobrecargas).

- [ ] **Step 3: implementar** en `SincroShippyPro.java`:

  a) Ayudas nuevas (detrás de `enviadosPorPedido`):

```java
    /** Los envios por pedido con los de los juntados (spec 2026-11-20 §2): un pedido juntado sin envio propio toma el de su principal. */
    static Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> conJuntos(Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> propios,
                                                                    Map<ClavePedido, ClavePedido> juntos) {
        if (juntos.isEmpty()) return propios;
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> todos = new HashMap<>(propios);
        juntos.forEach((a, p) -> {
            ClienteShippyPro.EnvioShippyPro e = propios.get(p);
            if (e != null) todos.putIfAbsent(a, e);
        });
        return todos;
    }

    /** ¿Sigue pendiente a efectos del envio? Un juntado que usa el envio de su principal sigue en ShippyPro hasta que la web lo completa:
     *  cuenta lo que diga su principal. */
    static boolean pendiente(ClavePedido clave, Set<ClavePedido> pendientes, Map<ClavePedido, ClavePedido> juntos,
                             Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> propios) {
        ClavePedido principal = juntos.get(clave);
        if (principal != null && !propios.containsKey(clave)) return pendientes.contains(principal);
        return pendientes.contains(clave);
    }
```

  b) `marcarEnviadas`: la firma actual pasa a delegar `return marcarEnviadas(enviados, canales, pendientes, candidatas, Map.of());` y la
  nueva (con el javadoc de antes más «Pedidos juntados: ver {@link #conJuntos}; el envio del principal solo entra en las filas vivas del
  juntado»):

```java
    public static List<MarcaEnvio> marcarEnviadas(List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales,
                                                  Set<ClavePedido> pendientes, List<FilaPedido> candidatas,
                                                  Map<ClavePedido, ClavePedido> juntos) {
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> propios = enviadosPorPedido(enviados, canales);
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> recientes = conJuntos(propios, juntos);
        List<MarcaEnvio> marcas = new ArrayList<>();
        for (FilaPedido f : candidatas) {
            if (f.envioFecha() != null || f.canal() == null || f.pedido() == null) continue;
            ClavePedido clave = new ClavePedido(f.canal(), f.pedido());
            ClienteShippyPro.EnvioShippyPro e = recientes.get(clave);
            if (e == null || pendiente(clave, pendientes, juntos, propios)) continue;
            // Una fila cerrada del juntado (un movil que ya no va en el pedido) no recibe el envio del principal.
            if (juntos.containsKey(clave) && !propios.containsKey(clave) && !f.viva()) continue;
            if (f.reabiertaEn() != null && !e.fecha().isAfter(f.reabiertaEn())) continue;
            marcas.add(new MarcaEnvio(f.id(), e.fecha(), e.transportista(), e.seguimiento(), e.idEnvio(), e.impresaEn()));
        }
        return marcas;
    }
```

  c) `completarEtiquetas`: igual (la de 4 argumentos delega con `Map.of()`); en la nueva, las dos primeras líneas son
  `propios = enviadosPorPedido(...)` y `recientes = conJuntos(propios, juntos)`, y `pendientes.contains(clave)` pasa a
  `pendiente(clave, pendientes, juntos, propios)`. El resto del cuerpo, igual.

  d) `clavesEnviadas`: la actual delega con `Map.of()`; la nueva:

```java
    public static Set<ClavePedido> clavesEnviadas(List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales,
                                                  Map<ClavePedido, ClavePedido> juntos) {
        return new LinkedHashSet<>(conJuntos(enviadosPorPedido(enviados, canales), juntos).keySet());
    }
```

  e) `de(...)`: la firma actual delega `return de(pendientes, canales, acepta, vivas, cerradas, hoy, Map.of());`. En la nueva (mismo cuerpo
  salvo lo marcado):

```java
    public static Plan de(List<PedidoShippyPro> pendientes, Map<String, Canal> canales, AceptaSku acepta,
                          List<FilaPedido> vivas, List<FilaPedido> cerradas, LocalDate hoy, Map<ClavePedido, ClavePedido> juntos) {
        Set<ClavePedido> clavesPendientes = clavesPendientes(pendientes, canales);
        Set<String> existentes = new HashSet<>();
        for (FilaPedido v : vivas) existentes.add(clave(v.canal(), v.pedido(), v.posicion()));
        Map<String, FilaPedido> cerradasPorClave = new HashMap<>();
        for (FilaPedido c : cerradas) cerradasPorClave.put(clave(c.canal(), c.pedido(), c.posicion()), c);
        // ... igual que ahora hasta el bloque de la fila cerrada, que queda asi:
                    FilaPedido cerrada = cerradasPorClave.get(clavePosicion);
                    if (cerrada != null) {
                        ClavePedido principal = juntos.get(new ClavePedido(canal, numero));
                        // Pedido juntado (spec 2026-11-20 §2): su fila ya salio con el envio del principal; el pedido sigue en ShippyPro
                        // hasta que la web lo completa y no se reabre, salvo que el principal vuelva a estar pendiente.
                        if (cerrada.enviada() && principal != null && !clavesPendientes.contains(principal)) {
                            existentes.add(clavePosicion);
                            continue;
                        }
                        // Ya tuvo fila y se cerro: se reabre con su IMEI (insertarla chocaria con la clave unica).
                        reabrir.add(cerrada.id());
                        existentes.add(clavePosicion);
                        continue;
                    }
        // ... el resto igual.
```

- [ ] **Step 4: verde**

Run: `mvn -q test -Dtest=SincroShippyProTest`
Expected: PASS (los de antes y los 6 nuevos).

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/SincroShippyPro.java src/test/java/com/reparaciones/servidor/pedidos/SincroShippyProTest.java
git commit -m "feat: la lectura pone el envio del principal en las filas vivas de sus pedidos juntados"
```

---

### Task 7: Servidor — `LecturaFilas` usa los grupos y deshace los que toca

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/LecturaFilas.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/LecturaFilasTest.java`

**Interfaces:**
- Consumes: `FilasDAO.juntos()`, `deShippyProDePedidos`, `separar` (Task 3); `Juntos.mapa/aDeshacer` (Task 2); sobrecargas de Task 6.
- Produces: `LecturaFilas.AVISO_JUNTOS = "No se pudieron revisar los pedidos juntados"`; aviso «N pedidos juntados separados solos» en el
  mensaje de la lectura.

- [ ] **Step 1: el test que falla** — en `LecturaFilasTest.java`:

```java
    private static PedidoShippyPro pedidoWeb(String numero) {
        return new PedidoShippyPro(numero, "200", AHORA.minusDays(1), "Cliente Web A", "ES",
                List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)));
    }

    private static FilaPedido vivaWeb(int id, String pedido) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 9), "Cliente Web A", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, 9),
                "SHIPPYPRO", Canal.PARTICULARES, 1, null);
    }

    @Test void elEnvioDelPrincipalLlegaALasFilasVivasDelJuntadoAunqueSigaPendiente() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedidoWeb("90101")), List.of()));
        when(shippy.enviados(any(), any())).thenReturn(List.of(new ClienteShippyPro.EnvioShippyPro("90108", "200", AHORA.minusHours(1),
                "UPSv2", "1Z0099000001")));
        when(dao.juntos()).thenReturn(List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA.minusDays(1), 9)));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(vivaWeb(1, "90101")));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(vivaWeb(1, "90101")));
        when(dao.deShippyProDePedidos(any())).thenReturn(List.of(vivaWeb(1, "90101"), vivaWeb(2, "90108")));

        lectura("200:PARTICULARES").pasada();

        verify(dao).porMarcarDeShippyPro(argThat(c -> c.contains(new SincroShippyPro.ClavePedido(Canal.PARTICULARES, "90101"))));
        verify(dao).marcarEnviadas(argThat(l -> l.size() == 1 && l.get(0).fila() == 1 && "1Z0099000001".equals(l.get(0).seguimiento())), any());
        verify(dao, never()).separar(any(), any(), any(), any(), any());
    }

    @Test void unGrupoCuyoPrincipalDesaparecioSinEtiquetaSeDeshaceSoloYLoApunta() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedidoWeb("90101")), List.of()));
        when(shippy.enviados(any(), any())).thenReturn(List.of());
        when(dao.juntos()).thenReturn(List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA.minusDays(1), 9)));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(vivaWeb(1, "90101")));
        FilaPedido principalCerrado = new FilaPedido(2, LocalDate.of(2026, 11, 9), "Cliente Web A", "i13128AzulA", null, "90108",
                LocalDate.of(2026, 11, 9), "SHIPPYPRO", Canal.PARTICULARES, 1, AHORA.minusHours(2));
        when(dao.deShippyProDePedidos(any())).thenReturn(List.of(vivaWeb(1, "90101"), principalCerrado));
        when(dao.separar(any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);

        lectura("200:PARTICULARES").pasada();

        verify(dao).separar(Canal.PARTICULARES, "90101", null, AHORA, "90108 ya no está en ShippyPro");
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), any(), argThat(m -> m != null && m.contains("1 pedidos juntados separados solos")), eq(AHORA));
    }

    @Test void sinLeerLosEnviosNoSeDeshaceNada() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedidoWeb("90101")), List.of()));
        when(shippy.enviados(any(), any())).thenThrow(new IOException("caido"));
        when(dao.juntos()).thenReturn(List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA.minusDays(1), 9)));

        lectura("200:PARTICULARES").pasada();

        verify(dao, never()).deShippyProDePedidos(any());
        verify(dao, never()).separar(any(), any(), any(), any(), any());
    }

    @Test void siFallaRevisarLosGruposLaLecturaSigueBuenaConUnAviso() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedidoWeb("90101")), List.of()));
        when(shippy.enviados(any(), any())).thenReturn(List.of());
        when(dao.juntos()).thenReturn(List.of(new Juntos.Junto(Canal.PARTICULARES, "90101", "90108", AHORA.minusDays(1), 9)));
        when(dao.deShippyProDePedidos(any())).thenThrow(new org.springframework.dao.QueryTimeoutException("lenta"));

        lectura("200:PARTICULARES").pasada();

        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), any(), argThat(m -> m != null && m.contains(LecturaFilas.AVISO_JUNTOS)), eq(AHORA));
    }
```

- [ ] **Step 2: verlo fallar** — `mvn -q test -Dtest=LecturaFilasTest` → FAIL (`AVISO_JUNTOS` no existe; los `verify` no se cumplen).

- [ ] **Step 3: implementar** en `LecturaFilas.java`:
  - constante: `static final String AVISO_JUNTOS = "No se pudieron revisar los pedidos juntados";`
  - en `vuelta()`, justo detrás de `List<FilaPedido> vivas = dao.vivasDeShippyPro();`:

```java
            // Los pedidos juntados (spec 2026-11-20 §2): sin ellos la lectura reabriria las filas del juntado que ya salieron.
            List<Juntos.Junto> listaJuntos = dao.juntos();
            Map<SincroShippyPro.ClavePedido, SincroShippyPro.ClavePedido> juntos = Juntos.mapa(listaJuntos);
```

  - las cuatro llamadas a `SincroShippyPro` pasan `juntos` como último argumento: `clavesEnviadas(enviados, canales, juntos)`,
    `marcarEnviadas(enviados, canales, claves, porMarcar, juntos)`, `completarEtiquetas(enviados, canales, claves, porMarcar, juntos)` y
    `SincroShippyPro.de(leidos.pedidos(), canales, acepta, vivas, cerradas, LocalDateTime.now(clock).toLocalDate(), juntos)`.
  - detrás del bloque `try { ... dao.ponerSalidas(salidas); } catch (...) { ... }`:

```java
            // Los grupos cuyo principal o juntado ya no tiene filas vivas ni envio se deshacen solos (spec §2). Solo con todo leido: con
            // pedidos descartados no se cierra nada, y sin los envios un principal recien etiquetado pareceria desaparecido.
            if (!hayDescartados && enviosLeidos && !listaJuntos.isEmpty()) {
                try {
                    int separados = deshacerJuntos(listaJuntos);
                    if (separados > 0) avisos.add(separados + " pedidos juntados separados solos");
                } catch (RuntimeException e) {
                    log.warn("Pedidos: no se pudieron revisar los pedidos juntados: {}", e.getClass().getSimpleName());
                    avisos.add(AVISO_JUNTOS);
                }
            }
```

  - el método:

```java
    /** Deshace los grupos que toca (ver {@link Juntos#aDeshacer}) con su apunte en el historial, sin usuario. Devuelve cuantos. */
    private int deshacerJuntos(List<Juntos.Junto> juntos) {
        Set<SincroShippyPro.ClavePedido> claves = new java.util.LinkedHashSet<>();
        for (Juntos.Junto j : juntos) {
            claves.add(new SincroShippyPro.ClavePedido(j.canal(), j.pedido()));
            claves.add(new SincroShippyPro.ClavePedido(j.canal(), j.principal()));
        }
        int n = 0;
        for (Juntos.Deshacer d : Juntos.aDeshacer(juntos, dao.deShippyProDePedidos(claves)))
            if (dao.separar(d.canal(), d.pedido(), null, LocalDateTime.now(clock), d.motivo()) == FilasDAO.Resultado.PUESTO) n++;
        return n;
    }
```

  Actualiza el javadoc de la clase: «…y deshace los pedidos juntados cuyo principal o juntado desaparece sin envío».

- [ ] **Step 4: verde y suite entera del servidor**

Run: `mvn -q test -Dtest=LecturaFilasTest` → PASS. Después `mvn -q test` → BUILD SUCCESS, ~2.350 tests (2317 + los nuevos), 0 fallos.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/LecturaFilas.java src/test/java/com/reparaciones/servidor/pedidos/LecturaFilasTest.java
git commit -m "feat: la lectura de ShippyPro usa los pedidos juntados y separa sola los que desaparecen"
```

---

### Task 8: Web — tipos y lógica pura de juntar (`juntar.ts`)

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados)
- Modify: `src/modules/telefonos/test/fabrica.ts` (función `fila`)
- Modify: `src/modules/telefonos/pedidos/salida.ts`, `src/modules/telefonos/pedidos/salida.test.ts`
- Create: `src/modules/telefonos/pedidos/juntar.ts`, `src/modules/telefonos/pedidos/juntar.test.ts`

**Interfaces:**
- Consumes: `FilaVista.juntoPrincipal: string | null`, `FilaVista.juntados: string[]` (Task 5); `estadoDe`, `esPendiente`, `sinAsociar`,
  `esCanalWeb` de `./filas`.
- Produces (los usan las Tasks 9-11):
  - `textoSalidaPlano(fecha: string, hoy: string): string` en `salida.ts`
  - `type EstadoPedido = 'PENDIENTE' | 'GENERADA' | 'ENVIADO' | 'NO_PENDIENTE'`; `TEXTO_ESTADO_PEDIDO: Record<EstadoPedido, string>`
  - `type Pedido = { clave; canal; pedido; cliente; filas: FilaVista[]; estado: EstadoPedido; entro: string; primerId: number; fechaSalida: string | null; principal: string | null; juntados: string[] }`
  - `clavePedido(canal, pedido)`, `pedidosDe(filas): Map<string, Pedido>`, `pedidoDeFila(f, pedidos): Pedido | null`,
    `moviles(p): string` («1 móvil», «3 móviles»)
  - `esJuntado(p)`, `esPedidoWeb(p)`, `motivoNoJuntar(a, b): string | null`,
    `principalDe(a, b): { principal: Pedido; juntado: Pedido; porque: 'es el que ya tiene etiqueta' | 'es el más reciente' }`
  - `candidatos(origen, pedidos): Pedido[]`, `sugerencia(origen, pedidos): Pedido | null`, `puedeJuntarse(p)`, `puedeSeparar(p)`
  - `type InfoGrupo = { numero: string; principal: string; juntados: string[]; esPrincipal: boolean }`
  - `ordenarConJuntos(filas): { filas: FilaVista[]; grupos: Map<number, InfoGrupo> }`
  - `avisoFechas(a, b, hoy): { titulo: string; texto: string } | null`

- [ ] **Step 1: regenerar tipos**

```bash
cd /c/Users/dev/Documents/wt/servidor-pedidos-juntar && export JAVA_HOME=/c/Users/dev/tools/jdk-17 PATH=/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH && mvn -q test -Dtest=OpenApiContractTest
cp /c/Users/dev/Documents/wt/servidor-pedidos-juntar/target/openapi.json /c/Users/dev/Documents/wt/web-pedidos-juntar/api/openapi.json
cd /c/Users/dev/Documents/wt/web-pedidos-juntar && npm run api:types:offline
grep -n "juntoPrincipal\|/api/pedidos/juntar" src/shared/api/schema.d.ts | head
```

Expected: el `grep` encuentra `juntoPrincipal`, `juntados`, `"/api/pedidos/juntar"` y `"/api/pedidos/juntar/{canal}/{pedido}"`. Si el
diff de `schema.d.ts` trae cambios ajenos (rutas de otra rama que aún no están), revisa que vienen del servidor de esta rama y déjalos.

- [ ] **Step 2: fábrica** — en `test/fabrica.ts`, función `fila`, añade `juntoPrincipal: null, juntados: [],` delante de `...p`.
  Run: `npx tsc -b --noEmit` → sin errores.

- [ ] **Step 3: los tests que fallan**

  En `salida.test.ts`, añade:

```ts
import { textoSalidaPlano } from './salida'

describe('textoSalidaPlano', () => {
  it('dice en minúscula cuándo sale, para frases', () => {
    const HOY = '2026-11-20' // viernes
    expect(textoSalidaPlano('2026-11-19', HOY)).toBe('va con retraso')
    expect(textoSalidaPlano('2026-11-20', HOY)).toBe('sale hoy')
    expect(textoSalidaPlano('2026-11-21', HOY)).toBe('sale mañana')
    expect(textoSalidaPlano('2026-11-23', HOY)).toBe('sale el lun 23/11')
  })
})
```

  `juntar.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { fila } from '../test/fabrica'
import {
  avisoFechas, candidatos, motivoNoJuntar, ordenarConJuntos, pedidosDe, principalDe, puedeJuntarse, puedeSeparar, sugerencia,
  clavePedido, type Pedido,
} from './juntar'

// «Juntar pedidos» (spec 2026-11-20): espejo en la web de las reglas del servidor. Datos inventados.
const HOY = '2026-11-20'
const W = { canal: 'PARTICULARES', plataforma: 'Cliente Web A' }
const pend = (idFila: number, pedido: string, fecha: string, extra = {}) => fila({ idFila, ...W, pedido, fecha, ...extra })
const generada = (idFila: number, pedido: string, fecha: string) =>
  fila({ idFila, ...W, pedido, fecha, estado: 'ENVIADA', envioFecha: `${fecha}T12:00:00`, tieneEtiqueta: true, transportista: 'UPSv2', seguimiento: '1Z0099' })
const impresa = (idFila: number, pedido: string, fecha: string) => ({ ...generada(idFila, pedido, fecha), impresaEn: `${fecha}T13:00:00` })
const noEsta = (idFila: number, pedido: string, fecha: string) => fila({ idFila, ...W, pedido, fecha, estado: 'NO_PENDIENTE' })
const de = (filas: ReturnType<typeof fila>[]) => pedidosDe(filas)
const p = (m: Map<string, Pedido>, pedido: string, canal = 'PARTICULARES') => m.get(clavePedido(canal, pedido))!

describe('pedidosDe', () => {
  it('agrupa por tienda y pedido, con estado, entrada, primera fila y la salida más temprana', () => {
    const m = de([pend(3, '90101', '2026-11-14', { fechaSalida: '2026-11-18' }), pend(2, '90101', '2026-11-15', { fechaSalida: '2026-11-17' }), generada(5, '90102', '2026-11-16')])
    expect(p(m, '90101')).toMatchObject({ estado: 'PENDIENTE', entro: '2026-11-14', primerId: 2, fechaSalida: '2026-11-17', cliente: 'Cliente Web A' })
    expect(p(m, '90102').estado).toBe('GENERADA')
    expect(de([impresa(1, '90103', '2026-11-16')]).get(clavePedido('PARTICULARES', '90103'))!.estado).toBe('ENVIADO')
    expect(de([noEsta(1, '90104', '2026-11-16')]).get(clavePedido('PARTICULARES', '90104'))!.estado).toBe('NO_PENDIENTE')
  })
  it('deja fuera el histórico y Amazon sin asociar', () => {
    const m = de([fila({ idFila: 1, canal: null, estado: 'HISTORICO', editable: false, pedido: '90001' }), fila({ idFila: 2, canal: 'AMAZON', pedido: '402-0099' })])
    expect(m.size).toBe(0)
  })
})

describe('motivoNoJuntar y principalDe', () => {
  const m = de([
    pend(1, '90101', '2026-11-14'), pend(2, '90108', '2026-11-18'), generada(3, '90110', '2026-11-19'), generada(4, '90111', '2026-11-19'),
    impresa(5, '90102', '2026-11-14'), noEsta(6, '90103', '2026-11-13'),
    fila({ idFila: 7, canal: 'DISTRIBUIDORES', plataforma: 'Cliente Web A', pedido: '90120', fecha: '2026-11-18' }),
    fila({ idFila: 8, canal: 'AMAZON', plataforma: 'AMZ', pedido: '402-0099', asociadaEn: '2026-11-18T10:00:00', fecha: '2026-11-18' }),
  ])
  it('da el mismo motivo que el servidor', () => {
    expect(motivoNoJuntar(p(m, '90101'), p(m, '90101'))).toBe('es el mismo pedido')
    expect(motivoNoJuntar(p(m, '90101'), p(m, '402-0099', 'AMAZON'))).toBe('Amazon: cada pedido lleva su propio seguimiento')
    expect(motivoNoJuntar(p(m, '90101'), p(m, '90120', 'DISTRIBUIDORES'))).toBe('otra tienda')
    expect(motivoNoJuntar(p(m, '90101'), p(m, '90103'))).toBe('90103 ya no está en ShippyPro: solo se juntan pedidos que siguen allí')
    expect(motivoNoJuntar(p(m, '90101'), p(m, '90102'))).toBe('90102 ya salió: esa caja ya no se puede abrir')
    expect(motivoNoJuntar(p(m, '90110'), p(m, '90111'))).toBe('los dos tienen su propia etiqueta: son dos envíos')
    expect(motivoNoJuntar(p(m, '90101'), p(m, '90108'))).toBeNull()
  })
  it('sin cadenas: un juntado no se junta con otro y un principal no pasa a juntado', () => {
    const g = de([
      pend(1, '90101', '2026-11-14', { juntoPrincipal: '90108', juntados: ['90101'] }), pend(2, '90108', '2026-11-18', { juntoPrincipal: '90108', juntados: ['90101'] }),
      pend(3, '90109', '2026-11-19'), pend(4, '90099', '2026-11-12'),
    ])
    expect(motivoNoJuntar(p(g, '90109'), p(g, '90101'))).toBe('90101 ya va con 90108')
    expect(motivoNoJuntar(p(g, '90108'), p(g, '90109'))).toBe('90108 ya es el principal de otro envío')
    expect(motivoNoJuntar(p(g, '90099'), p(g, '90108'))).toBeNull()
  })
  it('el principal es el que ya tiene etiqueta o, si ninguno, el más reciente (a igual fecha, la fila más nueva)', () => {
    expect(principalDe(p(m, '90101'), p(m, '90108'))).toMatchObject({ principal: { pedido: '90108' }, juntado: { pedido: '90101' }, porque: 'es el más reciente' })
    expect(principalDe(p(m, '90108'), p(m, '90101')).principal.pedido).toBe('90108')
    expect(principalDe(p(m, '90108'), p(m, '90110'))).toMatchObject({ principal: { pedido: '90110' }, porque: 'es el que ya tiene etiqueta' })
    const igual = de([pend(7, '90105', '2026-11-18'), pend(4, '90106', '2026-11-18')])
    expect(principalDe(p(igual, '90106'), p(igual, '90105')).principal.pedido).toBe('90105')
  })
})

describe('sugerencia, candidatos, puedeJuntarse y puedeSeparar', () => {
  it('sugiere al mismo cliente sin distinguir mayúsculas ni espacios, y de vuelta', () => {
    const m = de([pend(1, '90105', '2026-11-16'), fila({ idFila: 2, canal: 'PARTICULARES', plataforma: '  cliente web a ', pedido: '90111', fecha: '2026-11-19' }), pend(3, '90112', '2026-11-19', { plataforma: 'Cliente Web B' })])
    expect(sugerencia(p(m, '90105'), m)?.pedido).toBe('90111')
    expect(sugerencia(p(m, '90111'), m)?.pedido).toBe('90105')
    expect(candidatos(p(m, '90105'), m).map((c) => c.pedido)).toEqual(['90111'])
  })
  it('no se juntan Amazon, los que ya no están, los enviados ni los juntados; con etiqueta sí', () => {
    const m = de([impresa(1, '90102', '2026-11-14'), noEsta(2, '90103', '2026-11-13'), generada(3, '90110', '2026-11-19'),
      pend(4, '90101', '2026-11-14', { juntoPrincipal: '90108', juntados: ['90101'] }), pend(5, '90108', '2026-11-18', { juntoPrincipal: '90108', juntados: ['90101'] })])
    expect(puedeJuntarse(p(m, '90102'))).toBe(false)
    expect(puedeJuntarse(p(m, '90103'))).toBe(false)
    expect(puedeJuntarse(p(m, '90110'))).toBe(true)
    expect(puedeJuntarse(p(m, '90101'))).toBe(false)
    expect(puedeJuntarse(p(m, '90108'))).toBe(false)
    expect(puedeSeparar(p(m, '90101'))).toBe(true)
    expect(puedeSeparar(p(m, '90108'))).toBe(false)
    const conEnvio = de([{ ...generada(4, '90101', '2026-11-14'), juntoPrincipal: '90108', juntados: ['90101'] }])
    expect(puedeSeparar(p(conEnvio, '90101'))).toBe(false)
  })
})

describe('ordenarConJuntos (vista A)', () => {
  const G = { juntoPrincipal: '90108', juntados: ['90101'] }
  it('pega el grupo en el sitio de su fila más antigua, con el principal arriba, numerado y con el «Sale…» más temprano', () => {
    const filas = [
      pend(3, '90101', '2026-11-14', { ...G, fechaSalida: '2026-11-18' }),
      pend(4, '90103', '2026-11-15', { plataforma: 'Cliente Web B', fechaSalida: '2026-11-19' }),
      pend(10, '90108', '2026-11-18', { ...G, fechaSalida: '2026-11-23' }),
      pend(11, '90108', '2026-11-18', { ...G, fechaSalida: '2026-11-23' }),
    ]
    const { filas: orden, grupos } = ordenarConJuntos(filas)
    expect(orden.map((f) => f.idFila)).toEqual([10, 11, 3, 4])
    expect([10, 11, 3].map((id) => grupos.get(id)!.numero)).toEqual(['1/3', '2/3', '3/3'])
    expect(grupos.get(10)).toMatchObject({ principal: '90108', juntados: ['90101'], esPrincipal: true })
    expect(grupos.get(3)!.esPrincipal).toBe(false)
    expect(grupos.has(4)).toBe(false)
    expect(orden.slice(0, 3).map((f) => f.fechaSalida)).toEqual(['2026-11-18', '2026-11-18', '2026-11-18'])
    expect(orden[3]).toBe(filas[1])
  })
  it('sin grupos devuelve las mismas filas (misma referencia)', () => {
    const filas = [pend(1, '90101', '2026-11-14')]
    expect(ordenarConJuntos(filas).filas).toBe(filas)
  })
})

describe('avisoFechas', () => {
  it('avisa si juntar hace salir más tarde al que tiene que salir antes', () => {
    const m = de([pend(1, '90105', '2026-11-16', { fechaSalida: '2026-11-20' }), pend(2, '90111', '2026-11-19', { fechaSalida: '2026-11-23' })])
    expect(avisoFechas(p(m, '90105'), p(m, '90111'), HOY)).toEqual({
      titulo: '90105 saldrá más tarde de lo que toca',
      texto: '90105 entró el 16/11 y sale hoy; 90111 entró el 19/11 y sale el lun 23/11. Si los juntas, 90105 saldrá con 90111.',
    })
    expect(avisoFechas(p(m, '90111'), p(m, '90105'), HOY)?.titulo).toBe('90105 saldrá más tarde de lo que toca')
  })
  it('no avisa si el principal ya tiene etiqueta o si no hay retraso', () => {
    const m = de([pend(1, '90105', '2026-11-16', { fechaSalida: '2026-11-20' }), pend(2, '90111', '2026-11-19', { fechaSalida: '2026-11-20' })])
    expect(avisoFechas(p(m, '90105'), p(m, '90111'), HOY)).toBeNull()
    const e = de([pend(1, '90105', '2026-11-16', { fechaSalida: '2026-11-20' }), generada(2, '90111', '2026-11-19')])
    expect(avisoFechas(p(e, '90105'), p(e, '90111'), HOY)).toBeNull()
  })
})
```

- [ ] **Step 4: verlos fallar** — `npx vitest run src/modules/telefonos/pedidos/juntar.test.ts src/modules/telefonos/pedidos/salida.test.ts`
  → FAIL (`Failed to resolve import "./juntar"`, `textoSalidaPlano is not a function`).

- [ ] **Step 5: implementar**

  En `salida.ts`, al final:

```ts
/** Lo mismo que `textoSalida`, en minúscula y con «el» para meterlo en una frase («sale hoy», «sale el lun 23/11», «va con retraso»). */
export function textoSalidaPlano(fecha: string, hoy: string): string {
  if (fecha < hoy) return 'va con retraso'
  if (fecha === hoy) return 'sale hoy'
  if (fecha === siguiente(hoy)) return 'sale mañana'
  const d = new Date(`${fecha}T12:00:00Z`)
  return `sale el ${DIAS[d.getUTCDay()]} ${fecha.slice(8, 10)}/${fecha.slice(5, 7)}`
}
```

  `juntar.ts`:

```ts
import type { FilaVista } from '@/shared/api/client'
import { esCanalWeb, esPendiente, estadoDe, sinAsociar } from './filas'
import { textoSalidaPlano } from './salida'

/** «Juntar pedidos» (spec 2026-11-20): dos o más pedidos web de la misma tienda salen en una sola caja con la etiqueta del principal.
 *  Espejo de `Juntos.java` del servidor (mismos motivos y mismo orden de comprobaciones): la web los usa para enseñar qué se puede
 *  juntar; quien decide es el servidor. */

export type EstadoPedido = 'PENDIENTE' | 'GENERADA' | 'ENVIADO' | 'NO_PENDIENTE'
export const TEXTO_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'pendiente', GENERADA: 'etiqueta generada', ENVIADO: 'enviado', NO_PENDIENTE: 'ya no está en ShippyPro',
}

/** Un pedido de las filas recibidas: su tienda y número, el cliente (columna Plataforma), sus filas en orden, cuándo entró (la fecha
 *  más antigua de sus filas), su primera fila (el id más bajo), la salida más temprana y su grupo de juntados, si tiene. */
export type Pedido = {
  clave: string; canal: string; pedido: string; cliente: string; filas: FilaVista[]; estado: EstadoPedido
  entro: string; primerId: number; fechaSalida: string | null; principal: string | null; juntados: string[]
}

export const clavePedido = (canal: string, pedido: string): string => `${canal}|${pedido}`

/** ENVIADO si alguna etiqueta se imprimió; GENERADA si alguna fila tiene envío; PENDIENTE si alguna sigue pendiente; si no, ya no está. */
export function estadoDePedido(filas: FilaVista[]): EstadoPedido {
  if (filas.some((f) => f.envioFecha && f.impresaEn)) return 'ENVIADO'
  if (filas.some((f) => f.envioFecha)) return 'GENERADA'
  if (filas.some((f) => estadoDe(f) === 'PENDIENTE')) return 'PENDIENTE'
  return 'NO_PENDIENTE'
}

/** Los pedidos de las filas recibidas (sin histórico ni Amazon sin asociar), en una pasada más una por pedido. */
export function pedidosDe(filas: FilaVista[]): Map<string, Pedido> {
  const m = new Map<string, Pedido>()
  for (const f of filas) {
    if (estadoDe(f) === 'HISTORICO' || !f.canal || !f.pedido || sinAsociar(f)) continue
    const k = clavePedido(f.canal, f.pedido)
    const p = m.get(k)
    if (p) { p.filas.push(f); continue }
    m.set(k, { clave: k, canal: f.canal, pedido: f.pedido, cliente: f.plataforma, filas: [f], estado: 'PENDIENTE', entro: f.fecha,
      primerId: f.idFila, fechaSalida: null, principal: f.juntoPrincipal ?? null, juntados: f.juntados ?? [] })
  }
  for (const p of m.values()) {
    p.estado = estadoDePedido(p.filas)
    for (const f of p.filas) {
      if (f.fecha < p.entro) p.entro = f.fecha
      if (f.idFila < p.primerId) p.primerId = f.idFila
      if (f.fechaSalida && (!p.fechaSalida || f.fechaSalida < p.fechaSalida)) p.fechaSalida = f.fechaSalida
    }
  }
  return m
}

/** «1 móvil», «3 móviles». */
export const moviles = (p: Pedido): string => `${p.filas.length} ${p.filas.length === 1 ? 'móvil' : 'móviles'}`

export const pedidoDeFila = (f: FilaVista, pedidos: Map<string, Pedido>): Pedido | null =>
  (f.canal && f.pedido ? pedidos.get(clavePedido(f.canal, f.pedido)) ?? null : null)

export const esJuntado = (p: Pedido): boolean => p.principal != null && p.principal !== p.pedido
export const esPedidoWeb = (p: Pedido): boolean => esCanalWeb(p.filas[0])
const mismoCliente = (a: Pedido, b: Pedido) => a.canal === b.canal && a.cliente.trim().toLowerCase() === b.cliente.trim().toLowerCase()

type Decision = { principal: Pedido; juntado: Pedido; porque: 'es el que ya tiene etiqueta' | 'es el más reciente' }

/** El principal: el que ya tiene etiqueta; si ninguno, el más reciente por la fecha de entrada y, a igual fecha, el de la fila más nueva. */
export function principalDe(a: Pedido, b: Pedido): Decision {
  if (a.estado === 'GENERADA') return { principal: a, juntado: b, porque: 'es el que ya tiene etiqueta' }
  if (b.estado === 'GENERADA') return { principal: b, juntado: a, porque: 'es el que ya tiene etiqueta' }
  const aMasReciente = a.entro !== b.entro ? a.entro > b.entro : a.primerId > b.primerId
  return aMasReciente ? { principal: a, juntado: b, porque: 'es el más reciente' } : { principal: b, juntado: a, porque: 'es el más reciente' }
}

/** Por qué no se pueden juntar `a` y `b` (null si se puede), en el orden del servidor; «otra tienda» solo lo dice la web. */
export function motivoNoJuntar(a: Pedido, b: Pedido): string | null {
  if (a.clave === b.clave) return 'es el mismo pedido'
  if (!esPedidoWeb(a) || !esPedidoWeb(b)) return 'Amazon: cada pedido lleva su propio seguimiento'
  if (a.canal !== b.canal) return 'otra tienda'
  for (const x of [a, b]) if (esJuntado(x)) return `${x.pedido} ya va con ${x.principal}`
  for (const x of [a, b]) if (x.estado === 'NO_PENDIENTE') return `${x.pedido} ya no está en ShippyPro: solo se juntan pedidos que siguen allí`
  for (const x of [a, b]) if (x.estado === 'ENVIADO') return `${x.pedido} ya salió: esa caja ya no se puede abrir`
  if (a.estado === 'GENERADA' && b.estado === 'GENERADA') return 'los dos tienen su propia etiqueta: son dos envíos'
  const { juntado } = principalDe(a, b)
  if (juntado.juntados.length > 0) return `${juntado.pedido} ya es el principal de otro envío`
  return null
}

/** Los otros pedidos del mismo cliente y tienda, del más antiguo al más reciente. */
export function candidatos(origen: Pedido, pedidos: Map<string, Pedido>): Pedido[] {
  return [...pedidos.values()].filter((p) => p.clave !== origen.clave && mismoCliente(p, origen))
    .sort((x, y) => (x.entro === y.entro ? x.primerId - y.primerId : x.entro < y.entro ? -1 : 1))
}

/** Sale «Juntar con otro pedido…»: web, pendiente o con la etiqueta sin imprimir, y sin grupo todavía. */
export const puedeJuntarse = (p: Pedido): boolean =>
  esPedidoWeb(p) && (p.estado === 'PENDIENTE' || p.estado === 'GENERADA') && !esJuntado(p) && p.juntados.length === 0

/** Un juntado se puede separar mientras sus filas no tengan el envío del principal. */
export const puedeSeparar = (p: Pedido): boolean => esJuntado(p) && !p.filas.some((f) => f.envioFecha)

/** El primer pedido del mismo cliente y tienda con el que se puede juntar (para el recuadro del panel). */
export function sugerencia(origen: Pedido, pedidos: Map<string, Pedido>): Pedido | null {
  if (!puedeJuntarse(origen)) return null
  return candidatos(origen, pedidos).find((c) => motivoNoJuntar(origen, c) === null) ?? null
}

/** Cómo va una fila en su grupo: «1/3» (móviles de la caja), el principal, los juntados y si la fila es del principal. */
export type InfoGrupo = { numero: string; principal: string; juntados: string[]; esPrincipal: boolean }

/** Vista A del prototipo: los pedidos juntados salen pegados en el sitio de su fila más antigua (la primera de la lista), con las filas del
 *  principal arriba; mientras estén pendientes, todas enseñan el «Sale…» más temprano del grupo (se sustituye su `fechaSalida`: así lo ven
 *  también los contadores y el filtro «Sale hoy»). Sin grupos devuelve las mismas filas. */
export function ordenarConJuntos(filas: FilaVista[]): { filas: FilaVista[]; grupos: Map<number, InfoGrupo> } {
  const grupos = new Map<number, InfoGrupo>()
  const claveGrupo = (f: FilaVista) => (f.juntoPrincipal && f.canal && f.pedido && estadoDe(f) !== 'HISTORICO' ? `${f.canal}|${f.juntoPrincipal}` : null)
  const miembros = new Map<string, FilaVista[]>()
  for (const f of filas) {
    const k = claveGrupo(f)
    if (!k) continue
    const g = miembros.get(k)
    if (g) g.push(f); else miembros.set(k, [f])
  }
  if (miembros.size === 0) return { filas, grupos }
  for (const [k, g] of miembros) {
    const principal = k.slice(k.indexOf('|') + 1)
    const orden = [...g.filter((f) => f.pedido === principal), ...g.filter((f) => f.pedido !== principal)]
    let salida: string | null = null
    for (const f of orden) if (esPendiente(f) && f.fechaSalida && (!salida || f.fechaSalida < salida)) salida = f.fechaSalida
    const nuevas = orden.map((f) => (salida && esPendiente(f) && f.fechaSalida !== salida ? { ...f, fechaSalida: salida } : f))
    nuevas.forEach((f, i) => grupos.set(f.idFila, { numero: `${i + 1}/${nuevas.length}`, principal, juntados: f.juntados ?? [], esPrincipal: f.pedido === principal }))
    miembros.set(k, nuevas)
  }
  const out: FilaVista[] = []
  const puestos = new Set<string>()
  for (const f of filas) {
    const k = claveGrupo(f)
    if (!k) { out.push(f); continue }
    if (puestos.has(k)) continue
    puestos.add(k)
    out.push(...miembros.get(k)!)
  }
  return { filas: out, grupos }
}

const ddmm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`

/** Si juntar hace salir más tarde al que tiene que salir antes (decide logística): con el principal aún pendiente y la salida del juntado
 *  anterior a la del principal. */
export function avisoFechas(a: Pedido, b: Pedido, hoy: string): { titulo: string; texto: string } | null {
  const { principal: p, juntado: j } = principalDe(a, b)
  if (p.estado !== 'PENDIENTE' || !j.fechaSalida || !p.fechaSalida || j.fechaSalida >= p.fechaSalida) return null
  return {
    titulo: `${j.pedido} saldrá más tarde de lo que toca`,
    texto: `${j.pedido} entró el ${ddmm(j.entro)} y ${textoSalidaPlano(j.fechaSalida, hoy)}; ${p.pedido} entró el ${ddmm(p.entro)} y ${textoSalidaPlano(p.fechaSalida, hoy)}. Si los juntas, ${j.pedido} saldrá con ${p.pedido}.`,
  }
}
```

- [ ] **Step 6: verde**

Run: `npx vitest run src/modules/telefonos/pedidos/juntar.test.ts src/modules/telefonos/pedidos/salida.test.ts && npx tsc -b --noEmit && npx eslint src/modules/telefonos/pedidos/juntar.ts src/modules/telefonos/pedidos/salida.ts`
Expected: PASS y sin errores.

- [ ] **Step 7: commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/telefonos/test/fabrica.ts src/modules/telefonos/pedidos/salida.ts src/modules/telefonos/pedidos/salida.test.ts src/modules/telefonos/pedidos/juntar.ts src/modules/telefonos/pedidos/juntar.test.ts
git commit -m "feat: reglas de juntar pedidos en la web (principal, motivos, sugerencia, orden de la tabla y aviso de fechas)"
```

---

### Task 9: Web — la tabla pinta los grupos (vista A)

**Files:**
- Modify: `src/modules/telefonos/pedidos/TablaFilas.tsx`
- Modify: `src/modules/telefonos/pedidos/PedidosPage.tsx`
- Create: `src/modules/telefonos/pedidos/PedidosJuntar.test.tsx` (lo amplían las Tasks 10 y 11)

**Interfaces:**
- Consumes: `ordenarConJuntos`, `InfoGrupo`, `pedidosDe`, `Pedido` (Task 8).
- Produces: `TablaFilas` acepta `grupos?: Map<number, InfoGrupo>`; `FilaTabla` acepta `grupo?: InfoGrupo` y pinta `data-junto` en el `tr`;
  `PedidosPage` calcula `pedidos` (para el panel, Task 10) y desplaza la tabla a la fila elegida cuando cambia desde fuera («Ver»).

- [ ] **Step 1: el test que falla** (`PedidosJuntar.test.tsx`)

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { maquetarJsdom } from '@/test/layoutJsdom'
import { hoyMadrid } from '@/shared/lib/fechas'
import type { FilaVista } from '@/shared/api/client'
import { estadoNsys, fila, fuente } from '../test/fabrica'
import { estadoPedidos } from './estado'
import { PedidosPage } from './PedidosPage'

// «Juntar pedidos» (spec 2026-11-20): vista A, panel y ventana. Datos inventados.
const hoy = hoyMadrid()
const dia = (n: number) => { const d = new Date(`${hoy}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10) }
const W = { canal: 'DISTRIBUIDORES', plataforma: 'Cliente Web A' }
const G = { juntoPrincipal: '90108', juntados: ['90101'] }
const GRUPO: FilaVista[] = [
  fila({ idFila: 3, ...W, pedido: '90101', fecha: dia(-6), fechaSalida: hoy, ...G }),
  fila({ idFila: 4, canal: 'DISTRIBUIDORES', plataforma: 'Cliente Web B', pedido: '90103', fecha: dia(-5), fechaSalida: dia(2) }),
  fila({ idFila: 10, ...W, pedido: '90108', fecha: dia(-2), fechaSalida: dia(3), ...G }),
  fila({ idFila: 11, ...W, pedido: '90108', fecha: dia(-2), fechaSalida: dia(3), ...G }),
]

let respuesta: FilaVista[] = GRUPO
let peticiones: { metodo: string; url: string; cuerpo: unknown }[] = []

beforeEach(() => {
  respuesta = GRUPO
  peticiones = []
  estadoPedidos.reset()
  maquetarJsdom()
  server.use(
    http.get('*/api/pedidos/filas', () => HttpResponse.json({ filas: respuesta, hayMas: false })),
    http.get('*/api/pedidos/fuentes', () => HttpResponse.json([fuente({ fuente: 'SHIPPYPRO' })])),
    http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
    http.get('*/api/pedidos/asociaciones', () => HttpResponse.json({ canales: [] })),
    http.get('*/api/pedidos/filas/:id/cambios', () => HttpResponse.json([])),
    http.get('*/api/pedidos/filas/:id/recomendados', () => HttpResponse.json([])),
  )
})

const abrir = () => renderConProviders(<PedidosPage />, { sesion: SESION_SUPER, ruta: '/telefonos/pedidos' })
const tabla = () => screen.getByRole('region', { name: 'Tabla de pedidos' })
const pedidosEnOrden = () => within(tabla()).getAllByRole('button', { name: /^90\d{3}$/ }).map((b) => b.textContent)

describe('Juntar pedidos: la tabla (vista A)', () => {
  it('pega el grupo en el sitio de su fila más antigua, con el principal arriba, «+»/«→», la fracción de la caja y «con 90108»', async () => {
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    expect(pedidosEnOrden()).toEqual(['90108', '90108', '90101', '90103'])
    expect(within(tabla()).getAllByText('+ 90101')).toHaveLength(2)
    expect(within(tabla()).getAllByText('→ 90108')).toHaveLength(1)
    expect(within(tabla()).getAllByText('con 90108')).toHaveLength(1)
    const fracciones = [...tabla().querySelectorAll('[data-numero-pedido]')].map((e) => e.textContent)
    expect(fracciones).toEqual(['1/3', '2/3', '3/3'])
    expect(tabla().querySelectorAll('tr[data-junto="90108"]')).toHaveLength(3)
  })

  it('mientras está pendiente, el grupo entero enseña el «Sale…» del pedido más antiguo', async () => {
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    expect(within(tabla()).getAllByText('Sale hoy', { selector: 'span' })).toHaveLength(3)
    expect(screen.getByRole('button', { name: '3 salen hoy' })).toBeInTheDocument()
  })

  it('sin grupos la tabla queda como siempre', async () => {
    respuesta = GRUPO.map((f) => ({ ...f, juntoPrincipal: null, juntados: [] }))
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    expect(pedidosEnOrden()).toEqual(['90101', '90103', '90108', '90108'])
    expect(within(tabla()).queryByText('con 90108')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: verlo fallar** — `npx vitest run src/modules/telefonos/pedidos/PedidosJuntar.test.tsx` → FAIL (orden sin agrupar, no hay chips).

- [ ] **Step 3: `TablaFilas.tsx`**
  - import: `import type { InfoGrupo } from './juntar'`.
  - `PropsFila` gana `/** Su sitio en un grupo de pedidos juntados (vista A), si lo tiene. */ grupo?: InfoGrupo`, y `FilaTabla` lo
    desestructura.
  - constantes junto a `SOMBRA_RAYA`:

```tsx
/** La raya morada del borde izquierdo de las filas de un grupo de pedidos juntados; la azul de «la fila en la que estás» gana. */
const SOMBRA_JUNTO = 'shadow-[inset_4px_0_0_#7c3aed]'
const CHIP_JUNTO = 'ml-1.5 inline-block rounded px-1 font-sans text-[10.5px] font-bold bg-[#efe7fd] text-[#5b21b6]'
```

  - en el `<tr ...>` añade `data-junto={grupo?.principal}`.
  - primera celda: `<td className={cn(td, marcada ? SOMBRA_RAYA : grupo && SOMBRA_JUNTO)}>{dia(f.fecha)}</td>`.
  - celda del pedido, detrás del `</button>`:
    `{grupo && <span data-junto-chip className={CHIP_JUNTO}>{grupo.esPrincipal ? `+ ${grupo.juntados.join(', ')}` : `→ ${grupo.principal}`}</span>}`
  - celda «Enviado»: crea antes del `return` del componente
    `const conQuien = grupo && !grupo.esPrincipal ? <span className="ml-1.5 font-sans text-[11px] font-bold text-[#5b21b6]">con {grupo.principal}</span> : null`
    y ponlo detrás de cada `<EtiquetaEstado ... />` de esa celda (en la rama con marca, dentro del primer `<span>`).
  - `TablaFilas` acepta la prop `grupos` (documentada: «Por `idFila`, el sitio de cada fila en su grupo de pedidos juntados (ver
    `ordenarConJuntos`)») y en los dos bucles pasa `grupo={grupos?.get(f.idFila)}` a `FilaTabla`.
  - desplazarse a la fila elegida desde fuera («Ver 90108» del panel cambia `filaElegida` sin pulsar la fila). Detrás del
    `useLayoutEffect` del salto:

```tsx
  // «Ver 90108» del panel elige otra fila sin pulsarla: se la trae a la vista. Pulsar una fila visible no mueve nada (borde más cercano).
  const elegidaPrevia = useRef(filaElegida)
  useLayoutEffect(() => {
    if (filaElegida === elegidaPrevia.current) return
    elegidaPrevia.current = filaElegida
    if (filaElegida === null) return
    const j = indice.get(filaElegida)
    if (j === undefined) return
    if (ventana) virtualizador.scrollToIndex(j, { align: 'auto' })
    else contenedor.current?.querySelector<HTMLElement>('tr[data-aqui]')?.scrollIntoView({ block: 'nearest' })
  }, [filaElegida, indice, ventana, virtualizador])
```

    (Ojo: este efecto va **después** de la línea `const [{ filaAqui, filaElegida, fallosImei }] = useStore(estadoPedidos)`.)

- [ ] **Step 4: `PedidosPage.tsx`**
  - imports: `import { ordenarConJuntos, pedidosDe } from './juntar'`.
  - sustituye `const filas = useMemo(() => lista.data?.filas ?? [], [lista.data])` por:

```tsx
  const recibidas = useMemo(() => lista.data?.filas ?? [], [lista.data])
  // Vista A de «Juntar pedidos»: los grupos pegados y con el «Sale…» del más antiguo; todo lo de abajo (contadores, filtros, panel) ve esto.
  const { filas, grupos } = useMemo(() => ordenarConJuntos(recibidas), [recibidas])
  const pedidos = useMemo(() => pedidosDe(recibidas), [recibidas])
```

  - `numeros` pasa a: `useMemo(() => { const n = numerosDePedido(filas); for (const [id, g] of grupos) n.set(id, g.numero); return n }, [filas, grupos])`.
  - `<TablaFilas ...>` gana `grupos={grupos}`. (`pedidos` lo usa la Task 10; si eslint se queja de variable sin usar, añádela ya al
    `PanelFila` como en la Task 10, Step 4.)

- [ ] **Step 5: verde**

Run: `npx vitest run src/modules/telefonos/pedidos && npx tsc -b --noEmit && npx eslint src/modules/telefonos/pedidos`
Expected: PASS (los tests de Pedidos de antes siguen verdes: sin grupos nada cambia).

- [ ] **Step 6: commit**

```bash
git add src/modules/telefonos/pedidos/TablaFilas.tsx src/modules/telefonos/pedidos/PedidosPage.tsx src/modules/telefonos/pedidos/PedidosJuntar.test.tsx
git commit -m "feat: la tabla de pedidos pinta pegados los pedidos juntados con su caja y el principal arriba"
```

---

### Task 10: Web — historial, hooks y el apartado «Envío» del panel

**Files:**
- Modify: `src/modules/telefonos/pedidos/historial.ts`, `src/modules/telefonos/pedidos/historial.test.ts`
- Modify: `src/modules/telefonos/api.ts`
- Create: `src/modules/telefonos/pedidos/SeccionJuntar.tsx`
- Modify: `src/modules/telefonos/pedidos/PanelFila.tsx`, `src/modules/telefonos/pedidos/PedidosPage.tsx`
- Test: `src/modules/telefonos/pedidos/PedidosJuntar.test.tsx`

**Interfaces:**
- Consumes: `POST /api/pedidos/juntar`, `DELETE /api/pedidos/juntar/{canal}/{pedido}` (Task 4); `juntar.ts` (Task 8); `JuntarDialog` (Task 11:
  en esta task se crea un esqueleto mínimo para que compile y la Task 11 lo completa).
- Produces: `useJuntar()` → `mutate({ canal, pedido, con })`; `useSeparar()` → `mutate({ canal, pedido })` (los dos con
  `meta: { silenciarError: true }` y recarga de `CLAVE_FILAS` al acabar); `SeccionJuntar({ fila, pedidos, hoy })`;
  `PanelFila` acepta `pedidos?: Map<string, Pedido>` y `hoy?: string`.

- [ ] **Step 1: tests que fallan**

  En `historial.test.ts`:

```ts
  it('juntar y separar salen en el historial con el pedido principal', () => {
    const f = fila({ idFila: 3, fecha: '2026-11-14' })
    const e = construirHistorial(f, [
      { usuario: null, fecha: '2026-11-20T09:00:00', imeiAnterior: null, imeiNuevo: null, tipo: 'SEPARADO', detalle: '90108 — 90108 ya no está en ShippyPro' },
      { usuario: 'Supertécnico 1', fecha: '2026-11-19T12:00:00', imeiAnterior: null, imeiNuevo: null, tipo: 'SEPARADO', detalle: '90108' },
      { usuario: 'Supertécnico 1', fecha: '2026-11-19T10:00:00', imeiAnterior: null, imeiNuevo: null, tipo: 'JUNTADO', detalle: '90108' },
    ])
    expect(e.slice(0, 3).map((x) => (x.tipo === 'marca' ? [x.texto, x.quien] : x.tipo))).toEqual([
      ['Se separó de 90108: 90108 ya no está en ShippyPro', ''],
      ['Separado de 90108', 'Supertécnico 1'],
      ['Juntado: va en la caja de 90108', 'Supertécnico 1'],
    ])
  })
```

  (usa los imports que ya tenga el fichero: `construirHistorial`, `fila`.)

  En `PedidosJuntar.test.tsx`, añade:

```tsx
const panel = () => screen.getByRole('region', { name: 'Detalle del pedido' })
const pulsarFila = async (user: ReturnType<typeof userEvent.setup>, pedido: string) =>
  user.click(within(tabla()).getAllByRole('button', { name: pedido })[0])

const P = { canal: 'PARTICULARES', plataforma: 'Cliente P' }
const SUELTOS: FilaVista[] = [
  fila({ idFila: 20, ...P, pedido: '90105', fecha: dia(-4), fechaSalida: hoy }),
  fila({ idFila: 21, canal: 'PARTICULARES', plataforma: '  cliente p ', pedido: '90111', fecha: dia(-1), fechaSalida: dia(3) }),
  fila({ idFila: 22, canal: 'AMAZON', plataforma: 'AMZ', pedido: '402-0099000-0000001', asociadaEn: `${dia(-1)}T10:00:00`, fecha: dia(-1) }),
  fila({ idFila: 23, ...P, pedido: '90099', fecha: dia(-9), estado: 'NO_PENDIENTE' }),
]

describe('Juntar pedidos: el panel', () => {
  it('sugiere juntar con el mismo cliente y tienda; «Ver» salta al otro, que dice lo mismo de vuelta', async () => {
    respuesta = SUELTOS
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '90105')
    expect(within(panel()).getByText('Se puede juntar con 90111')).toBeInTheDocument()
    expect(within(panel()).getByRole('button', { name: 'Juntar con otro pedido…' })).toBeInTheDocument()
    await user.click(within(panel()).getByRole('button', { name: 'Ver 90111' }))
    await waitFor(() => expect(within(panel()).getByText('Se puede juntar con 90105')).toBeInTheDocument())
    expect(tabla().querySelector('tr[data-aqui]')).toHaveTextContent('90111')
  })

  it('Amazon y los que ya no están en ShippyPro no ofrecen juntar', async () => {
    respuesta = SUELTOS
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '402-0099000-0000001')
    expect(within(panel()).queryByRole('button', { name: 'Juntar con otro pedido…' })).not.toBeInTheDocument()
    await pulsarFila(user, '90099')
    expect(within(panel()).queryByRole('button', { name: 'Juntar con otro pedido…' })).not.toBeInTheDocument()
    expect(within(panel()).queryByText(/Se puede juntar/)).not.toBeInTheDocument()
  })

  it('el juntado dice en qué caja va y se separa; el principal lista lo que lleva', async () => {
    server.use(http.delete('*/api/pedidos/juntar/:canal/:pedido', ({ request }) => {
      peticiones.push({ metodo: 'DELETE', url: new URL(request.url).pathname, cuerpo: null })
      return new HttpResponse(null, { status: 204 })
    }))
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '90101')
    expect(within(panel()).getByText('Va en la caja del pedido 90108')).toBeInTheDocument()
    await user.click(within(panel()).getByRole('button', { name: 'Separar de 90108' }))
    await waitFor(() => expect(peticiones).toEqual([{ metodo: 'DELETE', url: '/api/pedidos/juntar/DISTRIBUIDORES/90101', cuerpo: null }]))
    await pulsarFila(user, '90108')
    expect(within(panel()).getByText('Envío juntado: lleva también 90101')).toBeInTheDocument()
    expect(within(panel()).getByRole('button', { name: 'Separar' })).toBeInTheDocument()
  })

  it('con el envío ya puesto no se puede separar; un 409 al separar se ve en el panel', async () => {
    respuesta = [
      { ...GRUPO[0], estado: 'ENVIADA', envioFecha: `${hoy}T09:00:00`, tieneEtiqueta: true },
      GRUPO[2], GRUPO[3],
    ]
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '3 filas' })
    await pulsarFila(user, '90101')
    expect(within(panel()).getByText('Ya tiene el envío de 90108: no se puede separar.')).toBeInTheDocument()
    expect(within(panel()).queryByRole('button', { name: 'Separar de 90108' })).not.toBeInTheDocument()
  })

  it('un 409 al separar sale en el panel con el texto del servidor', async () => {
    server.use(http.delete('*/api/pedidos/juntar/:canal/:pedido', () =>
      HttpResponse.json({ message: 'Este pedido ya tiene el envío de su principal: no se puede separar' }, { status: 409 })))
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '90101')
    await user.click(within(panel()).getByRole('button', { name: 'Separar de 90108' }))
    expect(await within(panel()).findByRole('alert')).toHaveTextContent('Este pedido ya tiene el envío de su principal: no se puede separar')
  })
})
```

- [ ] **Step 2: verlos fallar** — `npx vitest run src/modules/telefonos/pedidos/historial.test.ts src/modules/telefonos/pedidos/PedidosJuntar.test.tsx`
  → FAIL (el historial da tipo `imei`; el panel no tiene el apartado).

- [ ] **Step 3: implementar**

  a) `historial.ts`, dentro del `map`, antes del `return { tipo: 'imei', ... }` final:

```ts
    // «Juntar pedidos» (spec 2026-11-20): detalle = el principal; al separarlo la lectura, «principal — motivo» y sin usuario.
    if (cambio.tipo === 'JUNTADO') {
      return { tipo: 'marca', clave: `i${i}`, cuando, conHora: true, texto: `Juntado: va en la caja de ${cambio.detalle ?? ''}`.trim(), quien: cambio.usuario ?? '' }
    }
    if (cambio.tipo === 'SEPARADO') {
      const [principal, motivo] = (cambio.detalle ?? '').split(' — ')
      return { tipo: 'marca', clave: `i${i}`, cuando, conHora: true, texto: motivo ? `Se separó de ${principal}: ${motivo}` : `Separado de ${principal}`, quien: cambio.usuario ?? '' }
    }
```

  b) `api.ts`, detrás de `useFaltaEnviar`:

```ts
/** POST /api/pedidos/juntar (spec 2026-11-20 §4): junta dos pedidos web de la misma tienda en una sola caja; el servidor decide el principal
 *  y responde quién es. Un 409 (no se puede, con el motivo en palabras) no abre el diálogo global: lo enseña la ventana. Al acabar, bien o
 *  mal, recarga las filas (y su historial). */
export function useJuntar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (p: { canal: string; pedido: string; con: string }) => {
      const { data } = await api.POST('/api/pedidos/juntar', { body: p })
      if (!data) throw new Error('El servidor no respondió')
      return data
    },
    meta: { silenciarError: true },
    onSettled: () => { void qc.invalidateQueries({ queryKey: CLAVE_FILAS }) },
  })
}

/** DELETE /api/pedidos/juntar/{canal}/{pedido}: separa un pedido juntado de su principal. Un 409 (ya tiene el envío) lo enseña el panel. */
export function useSeparar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ canal, pedido }: { canal: string; pedido: string }) => {
      await api.DELETE('/api/pedidos/juntar/{canal}/{pedido}', { params: { path: { canal, pedido } } })
    },
    meta: { silenciarError: true },
    onSettled: () => { void qc.invalidateQueries({ queryKey: CLAVE_FILAS }) },
  })
}
```

  c) Esqueleto de `JuntarDialog.tsx` (lo completa la Task 11; así compila ya):

```tsx
import type { Pedido } from './juntar'

export type PropsJuntar = { origen: Pedido; preelegido: Pedido | null; pedidos: Map<string, Pedido>; hoy: string; onCerrar: () => void }

/** La ventana «Juntar el pedido X con…» (Task 11). */
export function JuntarDialog(props: PropsJuntar) {
  void props
  return null
}
```

  d) `SeccionJuntar.tsx`:

```tsx
import { useState, type ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import { useSeparar } from '../api'
import type { FilaVista } from '@/shared/api/client'
import { estadoPedidos } from './estado'
import { JuntarDialog } from './JuntarDialog'
import { TEXTO_ESTADO_PEDIDO, clavePedido, esJuntado, esPedidoWeb, moviles, pedidoDeFila, puedeJuntarse, puedeSeparar, sugerencia, type Pedido } from './juntar'

const CAJA = 'mt-1 rounded-md border border-[#c9b5f5] bg-[#f6f2ff] px-2.5 py-2 text-[12.5px]'
const CAJA_AMBAR = 'mt-1 rounded-md border border-[#e6c46a] bg-[#fffbef] px-2.5 py-2 text-[12.5px]'
const BOTON = 'h-7 cursor-pointer rounded-md border border-borde-input bg-superficie px-2.5 text-[12px] font-semibold disabled:cursor-default disabled:opacity-60'
const ddmm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`

/** Elige la primera fila de otro pedido (como pulsarla): el panel pasa a ese pedido y la tabla se desplaza hasta él. */
function verPedido(p: Pedido) {
  const id = p.filas[0].idFila
  estadoPedidos.set((s) => ({ ...s, filaElegida: id, filaAqui: id }))
}

/** La lista del grupo: el principal señalado y los juntados, con «Separar» en los que se puede (si `conSeparar`). */
function ListaGrupo({ canal, principal, juntados, pedidos, onSeparar, separando }: {
  canal: string; principal: string; juntados: string[]; pedidos: Map<string, Pedido>; onSeparar?: (p: Pedido) => void; separando: boolean
}) {
  const fila = (numero: string, esPrincipal: boolean) => {
    const p = pedidos.get(clavePedido(canal, numero))
    return (
      <li key={numero} className="flex items-center gap-1.5">
        <span className="font-mono">{numero}</span>{p && <span className="text-azul-gris">· {moviles(p)}</span>}
        {esPrincipal && <b>· principal (su etiqueta)</b>}
        {!esPrincipal && onSeparar && p && puedeSeparar(p) && (
          <button type="button" className={cn(BOTON, 'ml-auto h-6')} disabled={separando} onClick={() => onSeparar(p)}>Separar</button>
        )}
      </li>
    )
  }
  return <ul className="m-0 mt-1.5 flex list-none flex-col gap-1 p-0">{fila(principal, true)}{juntados.map((j) => fila(j, false))}</ul>
}

/** El apartado «Envío» del panel (spec 2026-11-20 §3): la sugerencia de juntar, el botón «Juntar con otro pedido…», o el grupo del pedido
 *  con «Separar». Nada en Amazon ni en los pedidos web que ya no se pueden juntar. */
export function SeccionJuntar({ fila, pedidos, hoy }: { fila: FilaVista; pedidos: Map<string, Pedido>; hoy: string }) {
  const [ventana, setVentana] = useState<{ con: Pedido | null } | null>(null)
  const separar = useSeparar()
  const p = pedidoDeFila(fila, pedidos)
  if (!p || !esPedidoWeb(p)) return null
  const pulsarSeparar = (j: Pedido) => separar.mutate({ canal: j.canal, pedido: j.pedido })
  let cuerpo: ReactNode = null
  if (esJuntado(p)) {
    cuerpo = (
      <div className={CAJA}>
        <p className="m-0 font-bold text-[#5b21b6]">Va en la caja del pedido {p.principal}</p>
        <p className="m-0 mt-0.5">Sus filas reciben la etiqueta de {p.principal} al generarse y sus móviles pasan a vendidos.</p>
        <ListaGrupo canal={p.canal} principal={p.principal!} juntados={p.juntados} pedidos={pedidos} separando={separar.isPending} />
        {puedeSeparar(p)
          ? <button type="button" className={cn(BOTON, 'mt-2')} disabled={separar.isPending} onClick={() => pulsarSeparar(p)}>Separar de {p.principal}</button>
          : <p className="m-0 mt-1.5 text-[12px] text-azul-gris">Ya tiene el envío de {p.principal}: no se puede separar.</p>}
      </div>
    )
  } else if (p.juntados.length > 0) {
    cuerpo = (
      <div className={CAJA}>
        <p className="m-0 font-bold text-[#5b21b6]">Envío juntado: lleva también {p.juntados.join(', ')}</p>
        <p className="m-0 mt-0.5">La etiqueta de este pedido sirve para todos.</p>
        <ListaGrupo canal={p.canal} principal={p.pedido} juntados={p.juntados} pedidos={pedidos} onSeparar={pulsarSeparar} separando={separar.isPending} />
      </div>
    )
  } else if (puedeJuntarse(p)) {
    const sug = sugerencia(p, pedidos)
    cuerpo = (
      <>
        {sug && (
          <div className={CAJA_AMBAR}>
            <p className="m-0 font-bold text-pill-aviso-text">Se puede juntar con {sug.pedido}</p>
            <p className="m-0 mt-0.5">Mismo cliente y tienda · {ddmm(sug.entro)} · {moviles(sug)} · {TEXTO_ESTADO_PEDIDO[sug.estado]}</p>
            <div className="mt-2 flex gap-1.5">
              <button type="button" className={BOTON} onClick={() => verPedido(sug)}>Ver {sug.pedido}</button>
              <button type="button" className={BOTON} onClick={() => setVentana({ con: sug })}>Juntar</button>
            </div>
          </div>
        )}
        <button type="button" className={cn(BOTON, 'mt-2')} onClick={() => setVentana({ con: null })}>Juntar con otro pedido…</button>
        {p.estado === 'GENERADA' && <p className="m-0 mt-1.5 text-[12px] text-azul-gris">Este pedido ya tiene etiqueta: solo puede ser el principal (otro pedido sin etiqueta va en su caja).</p>}
      </>
    )
  }
  if (!cuerpo) return null
  return (
    <>
      <h3 className="mt-3 mb-1.5 border-t border-form-fila-brd pt-2.5 text-[11px] font-bold tracking-wide text-azul-gris uppercase">Envío</h3>
      {cuerpo}
      {separar.error && <p role="alert" className="m-0 mt-1.5 text-[12px] font-bold text-texto-error">{separar.error.message}</p>}
      {ventana && <JuntarDialog origen={p} preelegido={ventana.con} pedidos={pedidos} hoy={hoy} onCerrar={() => setVentana(null)} />}
    </>
  )
}
```

  e) `PanelFila.tsx`: la firma pasa a
  `export function PanelFila({ fila: f, onCerrar, pedidos, hoy }: { fila: FilaVista; onCerrar: () => void; /** Los pedidos recibidos (para juntar); sin ellos no sale el apartado «Envío». */ pedidos?: Map<string, Pedido>; hoy?: string })`
  (imports: `import type { Pedido } from './juntar'`, `import { SeccionJuntar } from './SeccionJuntar'`, `import { hoyMadrid } from '@/shared/lib/fechas'`),
  y justo después de `<MarcaFalta key={`falta${f.idFila}`} fila={f} />`:
  `{pedidos && <SeccionJuntar key={`juntar${f.idFila}`} fila={f} pedidos={pedidos} hoy={hoy ?? hoyMadrid()} />}`.

  f) `PedidosPage.tsx`: `<PanelFila fila={presente.valor} onCerrar={cerrarPanel} pedidos={pedidos} hoy={hoy} />`, y el `elegida` del panel
  ya se busca en `filas` (las ordenadas), que es lo que quieres.

- [ ] **Step 4: verde**

Run: `npx vitest run src/modules/telefonos/pedidos && npx tsc -b --noEmit && npx eslint src/modules/telefonos`
Expected: PASS. Si algún test de panel anterior (`PedidosPanel.test.tsx`, `PedidosParcial.test.tsx`…) falla porque ahora aparece el
apartado «Envío» (sus filas de fábrica son todas web del mismo cliente), ajusta **ese test** con lo mínimo (por ejemplo, un `getAllBy…`
o un `plataforma` distinto en las filas de prueba), sin quitarle lo que comprueba.

- [ ] **Step 5: commit**

```bash
git add src/modules/telefonos/pedidos/historial.ts src/modules/telefonos/pedidos/historial.test.ts src/modules/telefonos/api.ts src/modules/telefonos/pedidos/SeccionJuntar.tsx src/modules/telefonos/pedidos/JuntarDialog.tsx src/modules/telefonos/pedidos/PanelFila.tsx src/modules/telefonos/pedidos/PedidosPage.tsx src/modules/telefonos/pedidos/PedidosJuntar.test.tsx
git commit -m "feat: el panel de pedidos sugiere juntar, ensena el grupo y separa; el historial apunta juntar y separar"
```

---

### Task 11: Web — la ventana «Juntar el pedido X con…»

**Files:**
- Modify: `src/modules/telefonos/pedidos/JuntarDialog.tsx` (sustituye el esqueleto)
- Test: `src/modules/telefonos/pedidos/PedidosJuntar.test.tsx`

**Interfaces:**
- Consumes: `useJuntar` (Task 10); `candidatos`, `motivoNoJuntar`, `principalDe`, `avisoFechas`, `TEXTO_ESTADO_PEDIDO` (Task 8); `textoTienda`
  de `./filas`; `moviles` de `./SeccionJuntar`; `Dialog*` de `@/shared/ui/dialog`; `Button` de `@/shared/ui/button`.
- Produces: `JuntarDialog(props: PropsJuntar)`.

- [ ] **Step 1: tests que fallan** — en `PedidosJuntar.test.tsx`:

```tsx
describe('Juntar pedidos: la ventana', () => {
  const ventana = () => screen.getByRole('dialog', { name: /Juntar el pedido 90105 con/ })

  it('desde la sugerencia: candidato elegido, principal decidido por el ERP, aviso de fechas y POST', async () => {
    respuesta = SUELTOS
    server.use(http.post('*/api/pedidos/juntar', async ({ request }) => {
      peticiones.push({ metodo: 'POST', url: new URL(request.url).pathname, cuerpo: await request.json() })
      return HttpResponse.json({ principal: '90111', juntado: '90105' })
    }))
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '90105')
    await user.click(within(panel()).getByRole('button', { name: 'Juntar' }))
    expect(within(ventana()).getByRole('radio', { name: /90111/ })).toBeChecked()
    expect(ventana()).toHaveTextContent('La caja lleva la etiqueta de 90111 (es el más reciente).')
    expect(within(ventana()).getByText('90105 saldrá más tarde de lo que toca')).toBeInTheDocument()
    expect(within(ventana()).getByText(/90105 entró el .* y sale hoy; 90111 entró el .* Si los juntas, 90105 saldrá con 90111\./)).toBeInTheDocument()
    await user.click(within(ventana()).getByRole('button', { name: 'Juntar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(peticiones).toEqual([{ metodo: 'POST', url: '/api/pedidos/juntar', cuerpo: { canal: 'PARTICULARES', pedido: '90105', con: '90111' } }])
  })

  it('un 409 se ve en la ventana, que sigue abierta', async () => {
    respuesta = SUELTOS
    server.use(http.post('*/api/pedidos/juntar', () =>
      HttpResponse.json({ message: 'No se puede juntar 90105 con 90111: 90111 ya salió: esa caja ya no se puede abrir' }, { status: 409 })))
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '4 filas' })
    await pulsarFila(user, '90105')
    await user.click(within(panel()).getByRole('button', { name: 'Juntar con otro pedido…' }))
    await user.click(within(ventana()).getByRole('button', { name: 'Juntar' }))
    expect(await within(ventana()).findByRole('alert')).toHaveTextContent('No se puede juntar 90105 con 90111: 90111 ya salió: esa caja ya no se puede abrir')
  })

  it('los que no se pueden salen en gris con su motivo, y se busca por número', async () => {
    respuesta = [
      ...SUELTOS,
      fila({ idFila: 24, canal: 'PARTICULARES', plataforma: 'Cliente P', pedido: '90100', fecha: dia(-3), estado: 'ENVIADA', envioFecha: `${dia(-1)}T10:00:00`, impresaEn: `${dia(-1)}T11:00:00`, tieneEtiqueta: true }),
      fila({ idFila: 25, canal: 'DISTRIBUIDORES', plataforma: 'Cliente P', pedido: '90120', fecha: dia(-2) }),
      fila({ idFila: 26, canal: 'PARTICULARES', plataforma: 'Otro nombre', pedido: '90115', fecha: dia(-2) }),
    ]
    const user = userEvent.setup()
    abrir()
    await screen.findByRole('button', { name: '7 filas' })
    await pulsarFila(user, '90105')
    await user.click(within(panel()).getByRole('button', { name: 'Juntar con otro pedido…' }))
    expect(within(ventana()).getByRole('radio', { name: /90100/ })).toBeDisabled()
    expect(ventana()).toHaveTextContent('No se puede: 90100 ya salió: esa caja ya no se puede abrir.')
    const campo = within(ventana()).getByRole('textbox', { name: 'Número de pedido' })
    await user.type(campo, '90120')
    await user.click(within(ventana()).getByRole('button', { name: 'Buscar' }))
    expect(ventana()).toHaveTextContent('No se puede juntar con 90120: otra tienda')
    await user.clear(campo)
    await user.type(campo, '99999')
    await user.click(within(ventana()).getByRole('button', { name: 'Buscar' }))
    expect(ventana()).toHaveTextContent('No hay ningún pedido 99999 en la lista cargada')
    await user.clear(campo)
    await user.type(campo, '90115')
    await user.click(within(ventana()).getByRole('button', { name: 'Buscar' }))
    expect(within(ventana()).getByRole('radio', { name: /90115/ })).toBeChecked()
  })
})
```

- [ ] **Step 2: verlo fallar** — `npx vitest run src/modules/telefonos/pedidos/PedidosJuntar.test.tsx` → FAIL (la ventana no se pinta).

- [ ] **Step 3: implementar `JuntarDialog.tsx`**

```tsx
import { useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { useJuntar } from '../api'
import { textoTienda } from './filas'
import { TEXTO_ESTADO_PEDIDO, avisoFechas, candidatos, motivoNoJuntar, moviles, principalDe, type Pedido } from './juntar'

export type PropsJuntar = { origen: Pedido; preelegido: Pedido | null; pedidos: Map<string, Pedido>; hoy: string; onCerrar: () => void }

const ddmm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`

/** La ventana «Juntar el pedido X con…» (spec 2026-11-20 §3): los pedidos del mismo cliente y tienda (los que no se pueden, en gris con su
 *  motivo), buscar por número (el cliente puede salir escrito de otra forma), el principal que decide el ERP, el aviso de fechas y qué
 *  pasa al juntar. Mientras guarda no se cierra; un 409 se queda dentro con el texto del servidor. */
export function JuntarDialog({ origen, preelegido, pedidos, hoy, onCerrar }: PropsJuntar) {
  const juntar = useJuntar()
  const base = candidatos(origen, pedidos)
  const [extra, setExtra] = useState<Pedido[]>(() => (preelegido && !base.includes(preelegido) ? [preelegido] : []))
  const lista = [...base, ...extra.filter((e) => !base.includes(e))]
  const [elegido, setElegido] = useState<string | null>(() => preelegido?.clave ?? base.find((c) => motivoNoJuntar(origen, c) === null)?.clave ?? null)
  const [buscado, setBuscado] = useState('')
  const [avisoBusqueda, setAvisoBusqueda] = useState<string | null>(null)
  const otro = lista.find((c) => c.clave === elegido) ?? null
  const decision = otro ? principalDe(origen, otro) : null
  const aviso = otro ? avisoFechas(origen, otro, hoy) : null

  const buscar = () => {
    const v = buscado.trim()
    if (!v) return
    const p = [...pedidos.values()].find((x) => x.pedido === v && x.clave !== origen.clave)
    if (!p) { setAvisoBusqueda(`No hay ningún pedido ${v} en la lista cargada`); return }
    const m = motivoNoJuntar(origen, p)
    if (m) { setAvisoBusqueda(`No se puede juntar con ${v}: ${m}`); return }
    setAvisoBusqueda(null)
    if (!lista.includes(p)) setExtra((e) => [...e, p])
    setElegido(p.clave)
  }
  const confirmar = () => {
    if (!otro || juntar.isPending) return
    juntar.mutate({ canal: origen.canal, pedido: origen.pedido, con: otro.pedido }, { onSuccess: onCerrar })
  }

  return (
    <Dialog open onOpenChange={(o) => { if (!o && !juntar.isPending) onCerrar() }}>
      <DialogContent className="max-w-[min(540px,calc(100%-2rem))] gap-2.5 p-5">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-bold text-azul-noche">Juntar el pedido <span className="font-mono">{origen.pedido}</span> con…</DialogTitle>
          <DialogDescription className="text-[12.5px] text-azul-medio">
            {origen.cliente} · {textoTienda(origen.canal)} · {moviles(origen)} · {TEXTO_ESTADO_PEDIDO[origen.estado]}
          </DialogDescription>
        </DialogHeader>
        <h3 className="m-0 text-[12px] font-bold text-azul-gris">Pedidos del mismo cliente en {textoTienda(origen.canal)}</h3>
        {lista.length === 0
          ? <p className="m-0 text-[12px] text-azul-gris">No hay otros pedidos de este cliente en esta tienda.</p>
          : (
            <div role="radiogroup" aria-label="Pedido con el que juntar" className="flex max-h-[40vh] flex-col gap-1 overflow-y-auto">
              {lista.map((c) => {
                const m = motivoNoJuntar(origen, c)
                return (
                  <label key={c.clave} className={`flex items-start gap-2 rounded-md border px-2 py-1.5 text-[12.5px] ${m ? 'border-form-fila-brd text-azul-gris' : 'cursor-pointer border-borde-input'} ${elegido === c.clave ? 'border-[#7c3aed] bg-[#f6f2ff]' : ''}`}>
                    <input type="radio" name="juntar-con" checked={elegido === c.clave} disabled={!!m} onChange={() => setElegido(c.clave)} className="mt-0.5" />
                    <span>
                      <span className="font-mono font-bold">{c.pedido}</span> · {ddmm(c.entro)} · {moviles(c)} · {TEXTO_ESTADO_PEDIDO[c.estado]}
                      {m && <span className="block text-[11.5px]">No se puede: {m}.</span>}
                    </span>
                  </label>
                )
              })}
            </div>
          )}
        <div className="flex gap-1.5">
          <input aria-label="Número de pedido" placeholder="¿Otro? Escribe el número de pedido (si el cliente sale con otro nombre)" value={buscado}
            onChange={(e) => setBuscado(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscar() } }}
            className="h-8 min-w-0 flex-1 rounded-md border border-borde-input bg-superficie px-2 text-[12.5px]" />
          <Button variant="outline" onClick={buscar}>Buscar</Button>
        </div>
        {avisoBusqueda && <p role="status" className="m-0 text-[12px] font-bold text-pill-aviso-text">{avisoBusqueda}</p>}
        {aviso && (
          <div className="rounded-md border border-[#e6c46a] bg-[#fffbef] px-2.5 py-2 text-[12.5px]">
            <p className="m-0 font-bold text-pill-aviso-text">{aviso.titulo}</p>
            <p className="m-0 mt-0.5">{aviso.texto}</p>
          </div>
        )}
        {decision && (
          <p className="m-0 text-[12.5px]">La caja lleva la etiqueta de <b className="font-mono">{decision.principal.pedido}</b> ({decision.porque}).</p>
        )}
        {decision && (
          <div className="rounded-md border border-[#c9b5f5] bg-[#f6f2ff] px-2.5 py-2 text-[12.5px]">
            <p className="m-0 font-bold">Qué pasa al juntar</p>
            <ul className="m-0 mt-1 list-disc pl-5">
              <li>Los dos salen pegados en la tabla y con una sola etiqueta: la de {decision.principal.pedido}.</li>
              {decision.principal.estado === 'PENDIENTE' && <li>Cuando se genere la etiqueta de {decision.principal.pedido}, las filas de {decision.juntado.pedido} pasan a «Etiqueta generada» con ella.</li>}
              {decision.principal.estado === 'GENERADA' && <li>{decision.principal.pedido} ya tiene etiqueta: las filas de {decision.juntado.pedido} pasan ya a «Etiqueta generada» con ella.</li>}
              <li>Los móviles de {decision.juntado.pedido} pasan a vendidos en cuanto tienen la etiqueta.</li>
              <li>Se puede separar mientras {decision.juntado.pedido} no tenga el envío.</li>
            </ul>
          </div>
        )}
        {juntar.error && <p role="alert" className="m-0 text-[12px] font-bold text-texto-error">{juntar.error.message}</p>}
        <DialogFooter className="gap-2">
          <Button variant="outline" disabled={juntar.isPending} onClick={onCerrar}>Cancelar</Button>
          <Button disabled={!otro || juntar.isPending} onClick={confirmar}>Juntar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: verde**

Run: `npx vitest run src/modules/telefonos/pedidos && npx tsc -b --noEmit && npx eslint src/modules/telefonos`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
git add src/modules/telefonos/pedidos/JuntarDialog.tsx src/modules/telefonos/pedidos/PedidosJuntar.test.tsx
git commit -m "feat: ventana para juntar un pedido con otro del mismo cliente, con el principal y el aviso de fechas"
```

---

### Task 12: Web — E2E en preprod (`pedidos-juntar.spec.ts`)

**Files:**
- Create: `tests/e2e/pedidos-juntar.spec.ts`

**Interfaces:**
- Consumes: las rutas de la Task 4 y la pantalla de las Tasks 9-11, desplegadas en **preprod** (lo despliega Imad; ver Task 13).

Escribe el spec ahora; **córrelo solo cuando Imad confirme que la rama está desplegada en preprod** (con la migración aplicada). No toca
ShippyPro: junta dos pedidos pendientes reales de una web (sin etiqueta: así nadie recibe envío) y los separa al acabar. Quedan en preprod
los apuntes JUNTADO/SEPARADO del historial de esas filas (es un entorno de pruebas: se acepta; dilo en el informe).

- [ ] **Step 1: el spec**

```ts
import { expect, test, type BrowserContext, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// pedidos-juntar.spec.ts (spec 2026-11-20 «Juntar pedidos»), solo contra PREPRODUCCIÓN (se niega sin el letrero).
//   - Busca dos pedidos web PENDIENTES (sin etiqueta) del mismo cliente y tienda que no estén en ningún grupo; si no hay, se salta.
//   - Los junta por la API (dos peticiones a la vez: el bloqueo debe dejar un solo grupo), mira la tabla (pegados, «+»/«→») y el historial,
//     comprueba un 409 (Amazon) y los separa. `afterAll` los separa si algo falló a medias y avisa si no pudo.
//   - No toca ShippyPro. Quedan los apuntes JUNTADO y SEPARADO en el historial de esas filas.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

type Fila = {
  idFila: number; canal: string | null; pedido: string | null; plataforma: string; fecha: string; estado: string
  juntoPrincipal: string | null; juntados: string[]
}

test.describe.configure({ mode: 'serial' })

let contexto: BrowserContext
let page: Page
let token = ''
let rutaFilas = ''
let par: { canal: string; principal: string; juntado: string } | null = null
let juntados = false

async function api(metodo: 'GET' | 'POST' | 'DELETE', ruta: string, cuerpo?: unknown): Promise<{ status: number; json: unknown }> {
  return page.evaluate(async ([m, r, c, t]) => {
    const cab: Record<string, string> = { Authorization: `Bearer ${t}` }
    if (c) cab['Content-Type'] = 'application/json'
    const res = await fetch(r as string, { method: m as string, headers: cab, body: (c as string | undefined) ?? undefined })
    return { status: res.status, json: await res.json().catch(() => null) }
  }, [metodo, ruta, cuerpo === undefined ? undefined : JSON.stringify(cuerpo), token] as const)
}
const filas = async (): Promise<Fila[]> => ((await api('GET', rutaFilas)).json as { filas: Fila[] }).filas

test.beforeAll(async ({ browser }, info) => {
  test.setTimeout(120_000)
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  const base = info.project.use.baseURL as string
  contexto = await browser.newContext({
    baseURL: base,
    storageState: { cookies: [], origins: [{ origin: new URL(base).origin, localStorage: [{ name: 'panelCarga', value: JSON.stringify({ estado: 'cerrado' }) }] }] },
    viewport: { width: 1600, height: 900 },
  })
  page = await contexto.newPage()
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
  token = await page.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }).token ?? '')
  const respuesta = page.waitForResponse((r) => /\/api\/pedidos\/filas\?/.test(r.url()) && r.request().method() === 'GET')
  await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: 'Pedidos', exact: true }).click()
  const r = await respuesta
  const url = new URL(r.url())
  rutaFilas = url.pathname + url.search
  const lista = ((await r.json()) as { filas: Fila[] }).filas
  // Pedidos web con TODAS sus filas pendientes y sin grupo, agrupados por tienda y cliente.
  const porPedido = new Map<string, Fila[]>()
  for (const f of lista) {
    if (!f.canal || !f.pedido || !['DISTRIBUIDORES', 'PARTICULARES', 'BACKIPHONE'].includes(f.canal)) continue
    const k = `${f.canal}|${f.pedido}`
    porPedido.set(k, [...(porPedido.get(k) ?? []), f])
  }
  const libres = [...porPedido.values()].filter((fs) => fs.every((f) => f.estado === 'PENDIENTE' && !f.juntoPrincipal))
  const porCliente = new Map<string, Fila[][]>()
  for (const fs of libres) {
    const k = `${fs[0].canal}|${fs[0].plataforma.trim().toLowerCase()}`
    porCliente.set(k, [...(porCliente.get(k) ?? []), fs])
  }
  const dos = [...porCliente.values()].find((v) => v.length >= 2)
  if (dos) {
    const entro = (fs: Fila[]) => [fs.map((f) => f.fecha).sort()[0], Math.min(...fs.map((f) => f.idFila))] as const
    const [a, b] = dos
    const [ea, ia] = entro(a)
    const [eb, ib] = entro(b)
    const aMasReciente = ea !== eb ? ea > eb : ia > ib
    par = { canal: a[0].canal!, principal: (aMasReciente ? a : b)[0].pedido!, juntado: (aMasReciente ? b : a)[0].pedido! }
  }
  console.log(`filas ${lista.length} · par ${par ? `${par.juntado} → ${par.principal} (${par.canal})` : 'ninguno'}`)
})

test.afterAll(async () => {
  if (!page || !token) return
  const fallos: string[] = []
  if (par && juntados) {
    const r = await api('DELETE', `/api/pedidos/juntar/${par.canal}/${par.juntado}`)
    if (r.status !== 204) fallos.push(`no se pudo separar ${par.juntado} (HTTP ${r.status} ${JSON.stringify(r.json)})`)
  }
  await contexto?.close()
  if (fallos.length) throw new Error(`HAY QUE LIMPIAR A MANO: ${fallos.join(' | ')}`)
})

test('1. juntar dos veces a la vez deja un solo grupo con el principal que decide el ERP', async () => {
  test.skip(!par, 'no hay dos pedidos web pendientes del mismo cliente y tienda')
  const cuerpo = { canal: par!.canal, pedido: par!.juntado, con: par!.principal }
  const [r1, r2] = await Promise.all([api('POST', '/api/pedidos/juntar', cuerpo), api('POST', '/api/pedidos/juntar', cuerpo)])
  juntados = true
  expect([r1.status, r2.status]).toEqual([200, 200])
  expect(r1.json).toEqual({ principal: par!.principal, juntado: par!.juntado })
  const fs = await filas()
  const delGrupo = fs.filter((f) => f.canal === par!.canal && (f.pedido === par!.juntado || f.pedido === par!.principal))
  expect(delGrupo.every((f) => f.juntoPrincipal === par!.principal && f.juntados.includes(par!.juntado))).toBe(true)
  const deA = delGrupo.find((f) => f.pedido === par!.juntado)!
  const cambios = (await api('GET', `/api/pedidos/filas/${deA.idFila}/cambios`)).json as { tipo: string; detalle: string | null }[]
  expect(cambios.filter((c) => c.tipo === 'JUNTADO' && c.detalle === par!.principal)).toHaveLength(1)
})

test('2. la tabla los pinta pegados con «+» y «→»', async () => {
  test.skip(!par || !juntados, 'sin grupo')
  await page.reload()
  await page.getByPlaceholder(/Buscar en todo el historial/).fill(par!.principal)
  const tabla = page.getByRole('region', { name: 'Tabla de pedidos' })
  await expect(tabla.getByText(`+ ${par!.juntado}`).first()).toBeVisible()
  await page.getByPlaceholder(/Buscar en todo el historial/).fill(par!.juntado)
  await expect(tabla.getByText(`→ ${par!.principal}`).first()).toBeVisible()
  await page.getByPlaceholder(/Buscar en todo el historial/).fill('')
})

test('3. Amazon da 409 con el motivo y no escribe nada', async () => {
  const r = await api('POST', '/api/pedidos/juntar', { canal: 'AMAZON', pedido: '0099-E2E-1', con: '0099-E2E-2' })
  expect(r.status).toBe(409)
  expect(JSON.stringify(r.json)).toContain('Amazon: cada pedido lleva su propio seguimiento')
})

test('4. separar deja los dos pedidos sueltos y lo apunta', async () => {
  test.skip(!par || !juntados, 'sin grupo')
  const r = await api('DELETE', `/api/pedidos/juntar/${par!.canal}/${par!.juntado}`)
  expect(r.status).toBe(204)
  juntados = false
  const fs = await filas()
  const delGrupo = fs.filter((f) => f.canal === par!.canal && (f.pedido === par!.juntado || f.pedido === par!.principal))
  expect(delGrupo.every((f) => f.juntoPrincipal === null && f.juntados.length === 0)).toBe(true)
  const deA = delGrupo.find((f) => f.pedido === par!.juntado)!
  const cambios = (await api('GET', `/api/pedidos/filas/${deA.idFila}/cambios`)).json as { tipo: string; detalle: string | null }[]
  expect(cambios.some((c) => c.tipo === 'SEPARADO' && c.detalle === par!.principal)).toBe(true)
})
```

- [ ] **Step 2: que compile** — `npx tsc -b --noEmit && npx eslint tests/e2e/pedidos-juntar.spec.ts` → sin errores. **No lo lances** contra
  nada ahora.

- [ ] **Step 3: commit**

```bash
git add tests/e2e/pedidos-juntar.spec.ts
git commit -m "test: E2E de juntar pedidos en preprod (juntar a la vez, tabla, 409 y separar con limpieza)"
```

- [ ] **Step 4 (cuando Imad diga que está en preprod):** con las credenciales de `~/.env.e2e` cargadas y la URL de preprod,
  `E2E_BASE_URL=<url de preprod> npx playwright test tests/e2e/pedidos-juntar.spec.ts --project=chromium` (mira `playwright.config.ts` por
  el nombre del proyecto). Expected: 4 passed (o 1 y 3 skipped si no hay pareja; dilo). Si `afterAll` lanza «HAY QUE LIMPIAR A MANO», para y
  avisa con el texto.

---

### Task 13: Traer lo nuevo de `dev_pedidos_tabla` y cierre

**Files:** los que traiga el merge; ninguno nuevo.

- [ ] **Step 1: ¿dónde está ahora la base?**

```bash
cd /c/Users/dev/Documents/wt/servidor-pedidos-juntar && git fetch origin
git merge-base --is-ancestor origin/dev_pedidos_tabla origin/main && echo "YA EN MAIN" || echo "AUN NO EN MAIN"
git log --oneline HEAD..origin/dev_pedidos_tabla | head -30
```

  Haz lo mismo en `/c/Users/dev/Documents/wt/web-pedidos-juntar`. Si `dev_pedidos_tabla` ya se fusionó en `main` (0.9.37), la base a traer
  es `origin/main`; si no, `origin/dev_pedidos_tabla`.

- [ ] **Step 2: traerla** (en cada worktree, con la base que toque):

```bash
git merge --no-ff origin/dev_pedidos_tabla -m "chore: trae lo nuevo de dev_pedidos_tabla a dev_pedidos_juntar"
```

  (o `origin/main` con «trae lo nuevo de main»). Conflictos probables: `FilasDAO.java`, `VistaFilas.java`, `FilaVista.java`,
  `LecturaFilas.java`, `SincroShippyPro.java`, `TablaFilas.tsx`, `PedidosPage.tsx`, `PanelFila.tsx`, `api.ts`, `schema.d.ts`/`openapi.json`.
  Resuelve conservando lo de las dos ramas. En `openapi.json`/`schema.d.ts` **no edites a mano**: tras resolver el servidor, regenera
  (`mvn -q test -Dtest=OpenApiContractTest`, copia `target/openapi.json` a la web y `npm run api:types:offline`) y añade el resultado al
  merge. Nunca `git push`.

- [ ] **Step 3: todo en verde**

```bash
cd /c/Users/dev/Documents/wt/servidor-pedidos-juntar && export JAVA_HOME=/c/Users/dev/tools/jdk-17 PATH=/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH && mvn -q test
cd /c/Users/dev/Documents/wt/web-pedidos-juntar && npm run check
```

  Expected: servidor sin fallos (anota el recuento, ~2.350); web `npm run check` sin errores (anota el recuento, ~3.050). Si el merge trajo
  cambios, commit de lo regenerado: `git commit -m "chore: openapi y tipos de la web al dia tras traer dev_pedidos_tabla"`.

- [ ] **Step 4: revisión contra la spec** — repasa la spec apartado por apartado (§1 la regla, §2 qué pasa al juntar, §3 pantalla, §4
  servidor, «Pruebas») y apunta para cada punto el commit y el test que lo cubre; lista lo que no.

- [ ] **Step 5: informe a Imad** (sin push, sin merge a main, sin tags, sin desplegar):
  - commits de las dos ramas (`git log --oneline origin/dev_pedidos_tabla..HEAD` en cada worktree) y recuentos de tests;
  - para preprod/producción: aplicar **antes** `sql/migracion-pedidos-juntar.sql` (vista previa TABLA_YA, comprobación TABLA_NUEVA = 1);
    el servidor nuevo sin la tabla falla al leer filas y en la lectura de ShippyPro;
  - el E2E `tests/e2e/pedidos-juntar.spec.ts`, listo para cuando esté en preprod (Task 12, Step 4);
  - las dudas abiertas de abajo.

---

## Dudas abiertas (para Imad; el plan sigue la spec tal cual)

1. **A desaparece antes de la etiqueta de B.** La spec dice que si A sale de ShippyPro antes de la etiqueta, el grupo se deshace. En la
   práctica la web completa A precisamente porque va en la caja de B: si lo completa más de dos lecturas (~10 min) antes de generar la
   etiqueta de B, el ERP cerrará las filas de A sin envío y deshará el grupo (A acaba «Ya no está en ShippyPro» y lo cubre la regla del
   bot de la 0.9.37). Confirmar con logística el orden real (¿etiqueta de B y luego completar A?).
2. **Búsqueda por número en la ventana** solo mira los pedidos cargados en la pantalla (el periodo elegido + los pendientes). Un pedido con
   etiqueta generada de hace más de un mes no saldría (raro). El servidor decide igual al juntar.
3. **Contadores y filtro «Sale hoy»** ven el «Sale…» del grupo (el del más antiguo) en todas sus filas, para que lo que se ve y lo que se
   cuenta coincidan.
4. La fracción «1/3» del grupo sale donde hoy sale la de los pedidos de varios móviles (junto al SKU), no en la columna Pedido como dice
   la spec; si la otra sesión la mueve a Pedido, sigue valiendo.
```
