# 0.9.22 pieza 3 — «Descartar» en «No está en NSYS» y avisos con barra y × · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un enlace «descartar» (con motivo obligatorio) junto a «Hecho» en las filas de «No está en NSYS», con «Ver descartados» y «deshacer»; y todos los avisos temporales de abajo de la web con la barra que se consume y una × para cerrarlos.

**Architecture:** Servidor: tabla nueva `Taller_descarte` (migración aditiva propia), `DescarteDAO`, reglas puras en `TarjetasNsys` (un descarte vale si no es anterior a la última asignación del IMEI), `PorPasarNsys` las aplica a las tarjetas y a la plantilla de altas, dos rutas nuevas (`PUT`/`DELETE …/por-pasar/descarte`) y un repaso tras cada importación que borra los descartes de IMEI que NSYS ya conoce. Web: el contrato regenerado, dos mutaciones, el enlace en `CeldaHecho`, una ventana `DescartarAltaDialog`, «Ver descartados» al pie de «No está en NSYS», y la × (y la barra donde faltaba) en `AvisoBreveHost` y `useAccionConDeshacer`.

**Tech Stack:** Java 17, Spring Boot 3.3, JdbcTemplate, MariaDB 11, JUnit 5 + Mockito + MockMvc; React 19 + TypeScript, TanStack Query 5, openapi-fetch, Vitest + Testing Library + MSW, Playwright (E2E contra preprod).

**Spec:** `docs/superpowers/specs/2026-10-27-v0922-descartar-y-avisos-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-10-27-v0922-prototipo-descartar.html` (v2).

## Global Constraints

- **Ramas y worktrees:** servidor `/c/Users/dev/Documents/wt/servidor-0922-descartar` y web `/c/Users/dev/Documents/wt/web-0922-descartar`, rama `dev_v0922_descartar_nsys` desde `origin/main` (servidor `373f150`, web `577bf6b`). Raíz (`/c/Users/dev/Documents/ProgramaReparaciones`, rama `main`) solo para los documentos que diga una tarea. No tocar los clones normales (`ProgramaReparaciones/gestion-reparaciones-*`) ni otros worktrees (`wt/*-0922-plantillas`, `wt/*-0922-nombres`).
- **Commits** en castellano **sin tildes**, sin línea Co-Authored-By. **Nunca** push, merge ni tags (los hace el controlador con el OK del usuario). En la raíz hay ficheros sin seguimiento que no son de nadie (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Maven** por Bash (no PowerShell): `cd <worktree servidor> && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH" && mvn -q -o test -Dtest=ClaseA,ClaseB` — **comas** en `-Dtest` (surefire 3.2.5 no acepta `+`). Sin `-Dtest`: la suite entera (**1513** pruebas al empezar).
- **Web:** `cd <worktree web> && npx vitest run <ficheros>`, `npx tsc -b`, `npm run lint`, `npm run check`. `npm ci` una vez al crear el worktree.
- Java/TS con `\\` (regex) **solo con Write/Edit**, nunca con heredoc o sed de Bash (Bash en Windows junta las barras dobles).
- **Datos de prueba inventados** (repos públicos): IMEIs `3500000000000xx`, «SIN-IMEI-CAM», «Supertécnico 1», «Técnico 2».
- **Textos exactos** (spec §4.3 y §5):
  - enlace **«descartar»**, `title` «No va a NSYS: quitarlo de la lista (con motivo)», `aria-label` «Descartar <imei>»;
  - ventana: título **«Descartar de «No está en NSYS»»**; texto «<imei> deja de salir aquí y no va en la plantilla de altas. Las importaciones de NSYS no lo devuelven; solo sale otra vez si el taller le hace un trabajo nuevo.» (el IMEI en negrita); etiqueta «Motivo (obligatorio)»; placeholder «Por qué no va a NSYS»; debajo «Ejemplos: «no es un móvil, es una cámara», «móvil de un cliente, no es stock».»; botones «Cancelar» y «Descartar»;
  - avisos: «Descartado «<imei>»» y ««<imei>» vuelve a la lista»;
  - «Ver descartados (n)» / «Ocultar descartados»; tabla `aria-label` «Descartados», columnas IMEI, Modelo, Último trabajo, Descartado, Motivo; «deshacer» con `aria-label` «Deshacer descarte de <imei>»;
  - fila que vuelve: «Salió otra vez: trabajo nuevo después de que <quién> lo descartara el dd/mm/aaaa hh:mm («<motivo>»)»;
  - al final de la ayuda de «No está en NSYS»: «Si una fila no debería estar aquí (no es un móvil, es de un cliente…), «descartar» la quita con un motivo; solo vuelve si el taller le hace un trabajo nuevo.»;
  - servidor, 400: «Falta el IMEI», «Un IMEI no puede pasar de 40 caracteres», «Falta el motivo», «El motivo no puede pasar de 500 caracteres»;
  - registro de actividad: `DESCARTAR_ALTA_NSYS` (detalle `IMEI: <imei>`, motivo = el motivo) y `DESHACER_DESCARTE_NSYS` (detalle `IMEI: <imei>`);
  - log del servidor: `NSYS: descartes de «No está en NSYS» quitados: N (ya en NSYS)`;
  - la × de los avisos: `aria-label` «Cerrar aviso», `title` «Cerrar».
- **Contrato:** los campos nuevos van **al final** de sus registros; las rutas viejas no cambian (la web anterior sigue funcionando). Esquemas `FilaDescartadaNsys`, `PorPasarPeticionDescarte`, `PorPasarPeticionDeshacerDescarte`.
- **Transacciones:** las escrituras de descartar/deshacer, en `@Transactional(isolation = Isolation.READ_COMMITTED)` (lección del error 1020 de MariaDB con «Revisado»).
- El límite de 15 s por petición no se sube. Las E2E, **solo contra preprod** (se niegan sin el letrero).

---

## Mapa de ficheros

**Servidor** (`S/` = `src/main/java/com/reparaciones/servidor/nsys/`, `T/` = `src/test/java/com/reparaciones/servidor/nsys/`):

| Fichero | Qué |
|---|---|
| `sql/migracion-descartar-nsys.sql` (nuevo) | `Taller_descarte` con vista previa y comprobación |
| `sql/crear_bd.sql`, `sql/reset-uat.sql` | la tabla, su DROP y su DELETE |
| `T/DdlDescarteTest.java` (nuevo) | migración = crear_bd |
| `S/DescarteTaller.java`, `S/DescarteDAO.java` (nuevos) + `T/DescarteDAOTest.java` | acceso a la tabla |
| `S/AltaTaller.java` | `ultimoEn` (la última asignación) |
| `S/FilaAltaNsys.java`, `S/FilaDescartadaNsys.java` (nuevo), `S/TarjetasPorPasar.java` | contrato |
| `S/TarjetasNsys.java` + `T/TarjetasNsysTest.java` | reglas del descarte |
| `S/PorPasarNsys.java` + `T/PorPasarNsysTest.java` | tarjetas, plantilla, descartar, deshacer, repaso |
| `S/PorPasarController.java`, `S/ImportadorNsys.java` | rutas y repaso tras importar |
| `…/controller/RolesStockTelefonosTest.java`, `…/OpenApiContractTest.java`, `T/ImportadorNsysTest.java` | pruebas |

**Web** (`W/` = raíz del worktree web):

| Fichero | Qué |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | contrato regenerado |
| `src/shared/api/client.ts` | alias de tipos |
| `src/modules/telefonos/api.ts` + `api.test.tsx` | `useDescartarAlta`, `useDeshacerDescarte` |
| `src/modules/telefonos/test/fabrica.ts`, `src/test/server.ts` | datos de prueba |
| `src/modules/telefonos/stock/porPasar.ts` + `porPasar.test.ts` | textos del descarte |
| `src/modules/telefonos/stock/DescartarAltaDialog.tsx` (nuevo) | la ventana |
| `src/modules/telefonos/stock/CeldaHecho.tsx`, `ListasPorPasar.tsx`, `PorPasarNsys.tsx` + `PorPasarNsys.test.tsx` | enlace, «Ver descartados», aviso |
| `src/shared/ui/AvisoBreve.tsx` + test, `src/modules/taller/asignaciones/useAccionConDeshacer.tsx` + test | barra y × |
| `tests/e2e/descartar-nsys.spec.ts` (nuevo) | E2E en preprod |
| `CHANGELOG.md` (web), `docs/novedades/NOVEDADES-v0.9.22.md` (raíz) | documentación |

---

### Task 1: Migración y DDL de `Taller_descarte`

**Files:**
- Create: `sql/migracion-descartar-nsys.sql`
- Modify: `sql/crear_bd.sql` (DROP en la línea ~15 y CREATE tras `Taller_hecho`, línea ~611), `sql/reset-uat.sql` (línea ~27)
- Test: `T/DdlDescarteTest.java` (nuevo)

**Interfaces:**
- Produces: tabla `Taller_descarte (IMEI_SN VARCHAR(40) PK, ID_USU INT NULL FK Usuario, FECHA DATETIME NOT NULL, MOTIVO VARCHAR(500) NOT NULL)`.

- [ ] **Step 0: Worktree del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q origin && git worktree add -b dev_v0922_descartar_nsys /c/Users/dev/Documents/wt/servidor-0922-descartar origin/main && git -C /c/Users/dev/Documents/wt/servidor-0922-descartar log --oneline -1
```
Expected: `373f150 merge: 0.9.22, Revisado en READ COMMITTED…`.

- [ ] **Step 1: La prueba (falla: no existe la migración)**

`T/DdlDescarteTest.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** La base de la 0.9.22 pieza 3 (spec «descartar» §3): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen lo
 *  mismo de Taller_descarte. Se comparan con los espacios juntados (crear_bd.sql alinea las columnas). */
class DdlDescarteTest {

    private static final String MIGRACION = "migracion-descartar-nsys.sql";

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

    @Test void laTablaDeLosDescartesEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_descarte");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_descarte"));
        assertTrue(migracion.contains("MOTIVO VARCHAR(500) NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN)"), migracion);
        assertTrue(migracion.contains("FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL"), migracion);
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLosDescartesYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_descarte;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_descarte;"));
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `mvn -q -o test -Dtest=DdlDescarteTest` (con el `export` de las Global Constraints)
Expected: FAIL (`NoSuchFileException … migracion-descartar-nsys.sql`).

- [ ] **Step 3: La migración**

`sql/migracion-descartar-nsys.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-descartar-nsys.sql — 0.9.22 pieza 3, «descartar» en «No está en NSYS»
-- (spec docs/superpowers/specs/2026-10-27-v0922-descartar-y-avisos-design.md §3, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor de la pieza 3 (después de las migraciones de las piezas 1 y 2).
-- Aditiva: el servidor anterior no lee la tabla.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_descarte.
-- 3) Comprobación final: CREADAS debe dar 1.
-- Vuelta atrás: el servidor anterior funciona con la tabla puesta; no hace falta quitarla.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_descarte';

-- «descartar» de «No está en NSYS» (spec §3): un IMEI del taller que no va a NSYS (no es un móvil, es de un cliente…), con quién,
-- cuándo y por qué. Vale mientras el taller no le haga un trabajo nuevo; se borra sola cuando NSYS ya conoce el IMEI.
CREATE TABLE Taller_descarte (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del taller, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se descartó (hora de Madrid)',
    MOTIVO   VARCHAR(500) NOT NULL,
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_descarte_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobación final (no modifica nada): debe dar 1; si no, NO desplegar el servidor de la pieza 3 (lee la tabla).
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_descarte';
```

- [ ] **Step 4: `crear_bd.sql` y `reset-uat.sql`**

En `sql/crear_bd.sql`, justo encima de `DROP TABLE IF EXISTS Taller_hecho;` (línea ~15):

```sql
DROP TABLE IF EXISTS Taller_descarte;
```

Y debajo del `CREATE TABLE Taller_hecho (…);` (tras la línea ~611), con una línea en blanco antes:

```sql
-- 0.9.22 pieza 3 («descartar» en «No está en NSYS»): un IMEI del taller que no va a NSYS, con quién, cuándo y por qué. Vale
-- mientras el taller no le haga un trabajo nuevo; el repaso tras cada importación lo borra cuando NSYS ya conoce el IMEI.
CREATE TABLE Taller_descarte (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del taller, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se descartó (hora de Madrid)',
    MOTIVO   VARCHAR(500) NOT NULL,
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_descarte_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

En `sql/reset-uat.sql`, justo encima de `DELETE FROM Taller_hecho;` (línea ~27):

```sql
DELETE FROM Taller_descarte;
```

- [ ] **Step 5: Ver que pasa**

Run: `mvn -q -o test -Dtest=DdlDescarteTest,DdlPlantillasTest,DdlTallerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-descartar && git add sql/migracion-descartar-nsys.sql sql/crear_bd.sql sql/reset-uat.sql src/test/java/com/reparaciones/servidor/nsys/DdlDescarteTest.java && git commit -m "feat: migracion de Taller_descarte para descartar en No esta en NSYS (0.9.22, pieza 3)"
```

---

### Task 2: `DescarteTaller` y `DescarteDAO`

**Files:**
- Create: `S/DescarteTaller.java`, `S/DescarteDAO.java`
- Test: `T/DescarteDAOTest.java` (nuevo)

**Interfaces:**
- Consumes: la tabla de la Task 1.
- Produces:
  - `record DescarteTaller(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha, String motivo)`;
  - `DescarteDAO`: `List<DescarteTaller> todos()`, `void descartar(String imeiSn, int idUsu, String motivo, LocalDateTime ahora)`, `int deshacer(String imeiSn)`, `void quitar(List<String> imeis)`; constantes `SQL_TODOS`, `SQL_DESCARTAR`, `SQL_QUITAR`.

- [ ] **Step 1: La prueba (falla: no compila)**

`T/DescarteDAOTest.java`:

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

/** Taller_descarte con el JdbcTemplate mockeado, sin base de datos (como HechoDAOTest). */
class DescarteDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DescarteDAO dao = new DescarteDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losDescartesConQuienPorSuNombre() {
        assertTrue(dao.todos().isEmpty());
        verify(jdbc).query(eq(DescarteDAO.SQL_TODOS), any(RowMapper.class));
        assertEquals("SELECT d.IMEI_SN, d.ID_USU, u.NOMBRE_USUARIO, d.FECHA, d.MOTIVO FROM Taller_descarte d"
                + " LEFT JOIN Usuario u ON u.ID_USU = d.ID_USU", DescarteDAO.SQL_TODOS);
    }

    @Test void descartarOtraVezPoneQuienCuandoYElMotivoDeAhora() {
        dao.descartar("SIN-IMEI-CAM", 7, "no es un móvil", AHORA);
        verify(jdbc).update(DescarteDAO.SQL_DESCARTAR, "SIN-IMEI-CAM", 7, Timestamp.valueOf(AHORA), "no es un móvil");
        assertEquals("INSERT INTO Taller_descarte (IMEI_SN, ID_USU, FECHA, MOTIVO) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), MOTIVO = VALUES(MOTIVO)",
                DescarteDAO.SQL_DESCARTAR);
    }

    @Test void deshacerDiceCuantosQuito() {
        when(jdbc.update(DescarteDAO.SQL_QUITAR, "SIN-IMEI-CAM")).thenReturn(1);
        assertEquals(1, dao.deshacer("SIN-IMEI-CAM"));
        assertEquals(0, dao.deshacer("350000000000088"));
        assertEquals("DELETE FROM Taller_descarte WHERE IMEI_SN = ?", DescarteDAO.SQL_QUITAR);
    }

    @SuppressWarnings("unchecked")
    @Test void quitarEsUnLoteYSinNadaNoEscribe() {
        dao.quitar(List.of("350000000000088", "350000000000099"));
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(DescarteDAO.SQL_QUITAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000088"}, lote.getValue().get(0));
        clearInvocations(jdbc);
        dao.quitar(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerDescarte() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("SIN-IMEI-CAM");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        when(rs.getString("MOTIVO")).thenReturn("no es un móvil");
        assertEquals(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA, "no es un móvil"), DescarteDAO.descarte(rs, 0));
        when(rs.wasNull()).thenReturn(true);
        assertNull(DescarteDAO.descarte(rs, 0).idUsu(), "el usuario ya no existe");
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `mvn -q -o test -Dtest=DescarteDAOTest` — Expected: error de compilación (`DescarteDAO` no existe).

- [ ] **Step 3: El código**

`S/DescarteTaller.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_descarte (spec 0.9.22 pieza 3 §3): un IMEI de «No está en NSYS» que no va a NSYS, quién ({@code usuario}:
 *  su nombre, null si ya no existe), cuándo (hora de Madrid) y por qué. */
public record DescarteTaller(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha, String motivo) {}
```

`S/DescarteDAO.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Acceso a Taller_descarte (spec 0.9.22 pieza 3 §3): los IMEI descartados de «No está en NSYS», uno por IMEI. Son unos pocos. */
@Repository
public class DescarteDAO {

    static final String SQL_TODOS = "SELECT d.IMEI_SN, d.ID_USU, u.NOMBRE_USUARIO, d.FECHA, d.MOTIVO FROM Taller_descarte d"
            + " LEFT JOIN Usuario u ON u.ID_USU = d.ID_USU";
    /** Descartar otra vez el mismo IMEI pone quién, cuándo y el motivo de ahora. */
    static final String SQL_DESCARTAR = "INSERT INTO Taller_descarte (IMEI_SN, ID_USU, FECHA, MOTIVO) VALUES (?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), MOTIVO = VALUES(MOTIVO)";
    static final String SQL_QUITAR = "DELETE FROM Taller_descarte WHERE IMEI_SN = ?";

    private final JdbcTemplate jdbc;

    public DescarteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DescarteTaller> todos() {
        return jdbc.query(SQL_TODOS, DescarteDAO::descarte);
    }

    public void descartar(String imeiSn, int idUsu, String motivo, LocalDateTime ahora) {
        jdbc.update(SQL_DESCARTAR, imeiSn, idUsu, Timestamp.valueOf(ahora), motivo);
    }

    /** «deshacer» de «Ver descartados»: cuántos quitó (0 si no estaba descartado). */
    public int deshacer(String imeiSn) {
        return jdbc.update(SQL_QUITAR, imeiSn);
    }

    /** El repaso tras una importación: fuera los de estos IMEI (ya en NSYS), en un lote. Sin ninguno no escribe. */
    public void quitar(List<String> imeis) {
        if (imeis.isEmpty()) return;
        jdbc.batchUpdate(SQL_QUITAR, imeis.stream().map(i -> new Object[]{i}).toList());
    }

    static DescarteTaller descarte(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        return new DescarteTaller(rs.getString("IMEI_SN"), usuario, rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("MOTIVO"));
    }
}
```

- [ ] **Step 4: Ver que pasa** — `mvn -q -o test -Dtest=DescarteDAOTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/DescarteTaller.java src/main/java/com/reparaciones/servidor/nsys/DescarteDAO.java src/test/java/com/reparaciones/servidor/nsys/DescarteDAOTest.java && git commit -m "feat: acceso a los descartes de No esta en NSYS (0.9.22, pieza 3)"
```

---

### Task 3: Las reglas del descarte (`AltaTaller.ultimoEn`, `TarjetasNsys`, contrato)

**Files:**
- Modify: `S/AltaTaller.java`, `S/FilaAltaNsys.java`, `S/TarjetasPorPasar.java`, `S/TarjetasNsys.java`, `S/PorPasarNsys.java` (solo para que compile)
- Create: `S/FilaDescartadaNsys.java`
- Test: `T/AltaTallerTest.java`, `T/TarjetasNsysTest.java`, `T/PorPasarNsysTest.java` (solo constructores)

**Interfaces:**
- Consumes: `DescarteTaller` (Task 2).
- Produces:
  - `record AltaTaller(String imei, String modelo, String ultimo, boolean abierto, String tecnico, String cliente, int trabajos, LocalDateTime desde, LocalDateTime ultimoEn)`;
  - `FilaAltaNsys` con tres campos al final: `String descartadoAntesPor, LocalDateTime descartadoAntesEn, String descartadoAntesMotivo` (nullables);
  - `record FilaDescartadaNsys(String imeiSn, String modelo, String ultimoTrabajo, String categoria, String descartadoPor, LocalDateTime descartadoEn, String motivo)`;
  - `TarjetasPorPasar(color, grado, altas, revisar, List<FilaDescartadaNsys> descartadas)`;
  - `TarjetasNsys`: `static Map<String, DescarteTaller> descartesPorClave(List<DescarteTaller>)`, `static boolean descartada(AltaTaller, Map<String, DescarteTaller>)`, `static List<FilaAltaNsys> altas(List<AltaTaller>, Map<String, HechoTaller>, Map<String, DescarteTaller>)` (sustituye a la de dos argumentos), `static List<FilaDescartadaNsys> descartadas(List<AltaTaller>, Map<String, DescarteTaller>)`.

- [ ] **Step 1: Las pruebas (fallan: no compila)**

En `T/AltaTallerTest.java`, la primera prueba espera ahora la última asignación (la `A20261010_4`, cuatro días después):

```java
        assertEquals(List.of(new AltaTaller("350000000000088", "14", "A20261010_4", true, "Técnico 1", null, 2, DIA,
                DIA.plusDays(4))), altas);
```

En `T/TarjetasNsysTest.java`:

1. En `lasAltasLlevanSuCategoriaYSuMarca`, el `AltaTaller` lleva al final `LocalDateTime.of(2026, 10, 6, 9, 0)` (otra vez, su última asignación) y la llamada pasa a `TarjetasNsys.altas(List.of(a), TarjetasNsys.porClave(List.of(marca("350000000000088", TarjetaHecho.ALTA, HOY, null))), Map.of())`.
2. En `lasFilasDelRepasoSonLasDeColorGradoYAltas`, el `AltaTaller` lleva al final `HOY.minusDays(19)`.
3. Al final de la clase, estas pruebas nuevas:

```java
    private static DescarteTaller descarte(String imei, LocalDateTime fecha) {
        return new DescarteTaller(imei, 7, "Supertécnico 1", fecha, "no es un móvil");
    }

    private static AltaTaller alta(String imei, LocalDateTime ultimoEn) {
        return new AltaTaller(imei, "14", "R20261006_57", false, "Técnico 2", null, 1, HOY.minusDays(19), ultimoEn);
    }

    @Test void unDescarteNoAnteriorALaUltimaAsignacionQuitaLaFilaYVaADescartadas() {
        AltaTaller a = alta("350000000000088", HOY.minusDays(1));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)));
        assertTrue(TarjetasNsys.altas(List.of(a), Map.of(), d).isEmpty());
        FilaDescartadaNsys f = TarjetasNsys.descartadas(List.of(a), d).get(0);
        assertEquals("350000000000088", f.imeiSn());
        assertEquals("14", f.modelo());
        assertEquals("R20261006_57", f.ultimoTrabajo());
        assertEquals("REPARACION", f.categoria());
        assertEquals("Supertécnico 1", f.descartadoPor());
        assertEquals(HOY, f.descartadoEn());
        assertEquals("no es un móvil", f.motivo());
    }

    @Test void conLaMismaHoraQueLaUltimaAsignacionTambienVale() {
        assertTrue(TarjetasNsys.descartada(alta("350000000000088", HOY),
                TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)))));
    }

    @Test void sinFechaDeAsignacionElDescarteVale() {
        assertTrue(TarjetasNsys.descartada(alta("350000000000088", null),
                TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)))));
    }

    @Test void unTrabajoNuevoDespuesDelDescarteLaSacaOtraVezConQuienLaDescarto() {
        AltaTaller a = alta("350000000000088", HOY.plusHours(1));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)));
        FilaAltaNsys f = TarjetasNsys.altas(List.of(a), Map.of(), d).get(0);
        assertEquals("Supertécnico 1", f.descartadoAntesPor());
        assertEquals(HOY, f.descartadoAntesEn());
        assertEquals("no es un móvil", f.descartadoAntesMotivo());
        assertTrue(TarjetasNsys.descartadas(List.of(a), d).isEmpty());
    }

    @Test void elDescarteCruzaElImeiSinMirarMayusculasNiEspacios() {
        assertTrue(TarjetasNsys.descartada(alta("f2lxabc123", HOY.minusDays(1)),
                TarjetasNsys.descartesPorClave(List.of(descarte(" F2LXABC123 ", HOY)))));
    }

    @Test void sinDescarteLaFilaNoLlevaNadaDelDescarte() {
        FilaAltaNsys f = TarjetasNsys.altas(List.of(alta("350000000000088", HOY)), Map.of(), Map.of()).get(0);
        assertNull(f.descartadoAntesPor());
        assertNull(f.descartadoAntesEn());
        assertNull(f.descartadoAntesMotivo());
        assertTrue(TarjetasNsys.descartadas(List.of(alta("350000000000088", HOY)), Map.of()).isEmpty());
    }

    @Test void losDescartadosVanDelMasRecienteAlMasAntiguo() {
        AltaTaller uno = alta("350000000000088", HOY.minusDays(3));
        AltaTaller dos = alta("350000000000099", HOY.minusDays(3));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY.minusDays(1)),
                descarte("350000000000099", HOY)));
        assertEquals(List.of("350000000000099", "350000000000088"),
                TarjetasNsys.descartadas(List.of(uno, dos), d).stream().map(FilaDescartadaNsys::imeiSn).toList());
    }
```

En `T/PorPasarNsysTest.java`, los dos `new AltaTaller(…, AHORA.minusDays(19))` (líneas ~65 y ~227) llevan un último argumento más, `AHORA.minusDays(19)`.

- [ ] **Step 2: Ver que falla** — `mvn -q -o test -Dtest=AltaTallerTest,TarjetasNsysTest` → error de compilación (constructor de `AltaTaller`, `DescarteTaller`/`FilaDescartadaNsys`/métodos que no existen).

- [ ] **Step 3: `AltaTaller`**

En `S/AltaTaller.java`, el javadoc de la clase acaba en «…cuántos trabajos tiene, desde cuándo (su primera asignación) y cuándo se asignó el último ({@code ultimoEn}, para los descartes de la pieza 3). Horas de Madrid.»; el registro gana `LocalDateTime ultimoEn` al final:

```java
public record AltaTaller(String imei, String modelo, String ultimo, boolean abierto, String tecnico, String cliente,
                         int trabajos, LocalDateTime desde, LocalDateTime ultimoEn) {
```

y en `de(...)`:

```java
            altas.add(new AltaTaller(imei, ultimo.modelo(), ultimo.idRep(), ultimo.esAsignacion() && ultimo.terminado() == null,
                    ultimo.tecnico(), ultimo.cliente(), cuenta, primero.asignado(), ultimo.asignado()));
```

- [ ] **Step 4: Los registros del contrato**

`S/FilaAltaNsys.java` (todo el fichero):

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fila de «No está en NSYS» (spec 0.9.22 §3.4): el modelo del taller (código del ERP), el último trabajo con su
 *  {@code categoria} (REPARACION, GLASS o PULIDO) y si es una asignación abierta, su técnico y su cliente, cuántos trabajos, desde
 *  cuándo (el primero) y la marca «Hecho» que vale, como en FilaColorNsys. Si alguien la descartó y después el taller le hizo un
 *  trabajo nuevo, quién, cuándo y por qué (pieza 3 §5.4; null si no). */
public record FilaAltaNsys(String imeiSn, @Schema(nullable = true) String modelo, String ultimoTrabajo, String categoria,
                           boolean abierto, @Schema(nullable = true) String tecnico, @Schema(nullable = true) String cliente,
                           int trabajos, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                           @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio,
                           @Schema(nullable = true) String descartadoAntesPor,
                           @Schema(nullable = true) LocalDateTime descartadoAntesEn,
                           @Schema(nullable = true) String descartadoAntesMotivo) {}
```

`S/FilaDescartadaNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un IMEI descartado de «No está en NSYS» (spec 0.9.22 pieza 3 §4.2), para «Ver descartados»: lo de su fila (como FilaAltaNsys)
 *  y quién ({@code descartadoPor}: su nombre, null si ya no existe), cuándo (hora de Madrid) y por qué lo descartó. */
public record FilaDescartadaNsys(String imeiSn, @Schema(nullable = true) String modelo, String ultimoTrabajo, String categoria,
                                 @Schema(nullable = true) String descartadoPor, LocalDateTime descartadoEn, String motivo) {}
```

`S/TarjetasPorPasar.java` (todo el fichero):

```java
package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, y aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {}
```

- [ ] **Step 5: Las reglas en `TarjetasNsys`**

Sustituye el método `altas(List<AltaTaller>, Map<String, HechoTaller>)` por estos (el javadoc de la clase puede quedar igual):

```java
    /** Los descartes por {@link NsysDAO#claveCruce} de su IMEI. */
    static Map<String, DescarteTaller> descartesPorClave(List<DescarteTaller> descartes) {
        Map<String, DescarteTaller> mapa = new HashMap<>();
        for (DescarteTaller d : descartes) mapa.put(NsysDAO.claveCruce(d.imeiSn()), d);
        return mapa;
    }

    /** El descarte de una fila de «No está en NSYS», valga o no; null si no tiene. */
    private static DescarteTaller descarte(AltaTaller a, Map<String, DescarteTaller> descartes) {
        return descartes.get(NsysDAO.claveCruce(a.imei()));
    }

    /** Descartada (spec pieza 3 §4.1): con un descarte no anterior a su última asignación; sin fecha de asignación, vale. */
    static boolean descartada(AltaTaller a, Map<String, DescarteTaller> descartes) {
        DescarteTaller d = descarte(a, descartes);
        return d != null && (a.ultimoEn() == null || !d.fecha().isBefore(a.ultimoEn()));
    }

    /** «No está en NSYS» (§3.4), sin las descartadas; la que tiene un descarte superado por un trabajo nuevo lleva quién, cuándo y
     *  por qué la descartaron (pieza 3 §5.4). */
    static List<FilaAltaNsys> altas(List<AltaTaller> altas, Map<String, HechoTaller> marcas, Map<String, DescarteTaller> descartes) {
        List<FilaAltaNsys> filas = new ArrayList<>();
        for (AltaTaller a : altas) {
            if (descartada(a, descartes)) continue;
            Marca h = marca(marcas, TarjetaHecho.ALTA, a.imei(), a.desde());
            DescarteTaller antes = descarte(a, descartes);
            filas.add(new FilaAltaNsys(a.imei(), a.modelo(), a.ultimo(), Rastro.categoria(a.ultimo()), a.abierto(), a.tecnico(),
                    a.cliente(), a.trabajos(), a.desde(), h.por(), h.en(), h.volvio(),
                    antes == null ? null : antes.usuario(), antes == null ? null : antes.fecha(),
                    antes == null ? null : antes.motivo()));
        }
        filas.sort(Comparator.comparing(FilaAltaNsys::desde, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FilaAltaNsys::imeiSn));
        return filas;
    }

    /** «Ver descartados» (pieza 3 §4.2): las filas descartadas, del descarte más reciente al más antiguo. */
    static List<FilaDescartadaNsys> descartadas(List<AltaTaller> altas, Map<String, DescarteTaller> descartes) {
        List<FilaDescartadaNsys> filas = new ArrayList<>();
        for (AltaTaller a : altas) {
            if (!descartada(a, descartes)) continue;
            DescarteTaller d = descarte(a, descartes);
            filas.add(new FilaDescartadaNsys(a.imei(), a.modelo(), a.ultimo(), Rastro.categoria(a.ultimo()), d.usuario(),
                    d.fecha(), d.motivo()));
        }
        filas.sort(Comparator.comparing(FilaDescartadaNsys::descartadoEn, Comparator.reverseOrder())
                .thenComparing(FilaDescartadaNsys::imeiSn));
        return filas;
    }
```

- [ ] **Step 6: Que `PorPasarNsys` compile (la Task 4 lo completa)**

En `S/PorPasarNsys.java`:
- en `tarjetas()`, `TarjetasNsys.altas(altas(), marcas)` pasa a `TarjetasNsys.altas(altas(), marcas, Map.of())` y el `new TarjetasPorPasar(...)` gana un último argumento `List.of()`;
- en `plantillaAltas()`, `TarjetasNsys.altas(altas(), TarjetasNsys.porClave(hechoDao.todas()))` pasa a `TarjetasNsys.altas(altas(), TarjetasNsys.porClave(hechoDao.todas()), Map.of())`.

- [ ] **Step 7: Ver que pasa**

Run: `mvn -q -o test -Dtest=AltaTallerTest,TarjetasNsysTest,PorPasarNsysTest,PlantillaNsysTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/AltaTaller.java src/main/java/com/reparaciones/servidor/nsys/FilaAltaNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaDescartadaNsys.java src/main/java/com/reparaciones/servidor/nsys/TarjetasPorPasar.java src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/AltaTallerTest.java src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java && git commit -m "feat: reglas de los descartes de No esta en NSYS con la ultima asignacion (0.9.22, pieza 3)"
```

---

### Task 4: `PorPasarNsys`: tarjetas, plantilla, descartar, deshacer y repaso

**Files:**
- Modify: `S/PorPasarNsys.java`
- Test: `T/PorPasarNsysTest.java`

**Interfaces:**
- Consumes: `DescarteDAO` (Task 2), reglas de `TarjetasNsys` (Task 3).
- Produces (usadas por la Task 5):
  - constructores `PorPasarNsys(TallerNsys, TallerDAO, NsysDAO, HechoDAO, DescarteDAO, LogDAO)` (`@Autowired`) y `PorPasarNsys(…, DescarteDAO, LogDAO, Clock)`;
  - `void descartar(String imeiSn, String motivo, int idUsu)`, `void deshacerDescarte(String imeiSn, int idUsu)`, `void repasarDescartes()`;
  - constantes `ACCION_DESCARTAR = "DESCARTAR_ALTA_NSYS"`, `ACCION_DESHACER_DESCARTE = "DESHACER_DESCARTE_NSYS"`, `MAX_MOTIVO = 500`.

- [ ] **Step 1: Las pruebas (fallan: no compila)**

En `T/PorPasarNsysTest.java`:
1. Junto a los otros mocks: `private final DescarteDAO descartes = mock(DescarteDAO.class);` y el servicio pasa a `new PorPasarNsys(taller, tallerDao, nsys, hechos, descartes, registro, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));`.
2. En `sinImeisFueraDeNsysNiAvisosNoSeLeeNadaMas`, al final: `verify(descartes, never()).todos();`.
3. Al final de la clase:

```java
    private static AltaTaller alta(String imei, LocalDateTime ultimoEn) {
        return new AltaTaller(imei, "14", "R20261006_57", false, "Técnico 2", null, 1, AHORA.minusDays(19), ultimoEn);
    }

    @Test void lasTarjetasSeparanLosDescartados() {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088", "SIN-IMEI-CAM"));
        when(nsys.altas(List.of("350000000000088", "SIN-IMEI-CAM"))).thenReturn(List.of(
                alta("350000000000088", AHORA.minusDays(2)), alta("SIN-IMEI-CAM", AHORA.minusDays(2))));
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("sin-imei-cam", 7, "Supertécnico 1", AHORA.minusDays(1),
                "no es un móvil")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of("350000000000088"), t.altas().stream().map(FilaAltaNsys::imeiSn).toList());
        assertEquals(List.of("SIN-IMEI-CAM"), t.descartadas().stream().map(FilaDescartadaNsys::imeiSn).toList());
    }

    @Test void laPlantillaDeAltasNoLlevaLosDescartados() {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088", "SIN-IMEI-CAM"));
        when(nsys.altas(List.of("350000000000088", "SIN-IMEI-CAM"))).thenReturn(List.of(
                alta("350000000000088", AHORA.minusDays(2)), alta("SIN-IMEI-CAM", AHORA.minusDays(2))));
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA.minusDays(1),
                "no es un móvil")));
        assertEquals(1, servicio.plantillaAltas().filas());
    }

    @Test void descartarGuardaQuitaLaMarcaYLoApuntaConElMotivo() {
        servicio.descartar(" SIN-IMEI-CAM ", "  no es un móvil, es una cámara ", 7);
        verify(descartes).descartar("SIN-IMEI-CAM", 7, "no es un móvil, es una cámara", AHORA);
        verify(hechos).desmarcar(TarjetaHecho.ALTA, "SIN-IMEI-CAM");
        verify(registro).insertar(7, "DESCARTAR_ALTA_NSYS", "IMEI: SIN-IMEI-CAM", "no es un móvil, es una cámara");
    }

    @Test void descartarRechazaLoQueNoVale() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar(null, "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar(" ", "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("9".repeat(41), "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", "   ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", "x".repeat(501), 7)));
        verifyNoInteractions(descartes, hechos, registro);
        servicio.descartar("SIN-IMEI-CAM", "x".repeat(500), 7);
        verify(descartes).descartar("SIN-IMEI-CAM", 7, "x".repeat(500), AHORA);
    }

    @Test void losMensajesDeLos400() {
        assertEquals("Falta el IMEI", assertThrows(ResponseStatusException.class, () -> servicio.descartar(" ", "m", 7)).getReason());
        assertEquals("Un IMEI no puede pasar de 40 caracteres",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("9".repeat(41), "m", 7)).getReason());
        assertEquals("Falta el motivo",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("SIN-IMEI-CAM", " ", 7)).getReason());
        assertEquals("El motivo no puede pasar de 500 caracteres",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("SIN-IMEI-CAM", "x".repeat(501), 7)).getReason());
    }

    @Test void deshacerElDescarteSoloApuntaSiHabiaUno() {
        when(descartes.deshacer("SIN-IMEI-CAM")).thenReturn(1);
        servicio.deshacerDescarte(" SIN-IMEI-CAM ", 7);
        verify(registro).insertar(7, "DESHACER_DESCARTE_NSYS", "IMEI: SIN-IMEI-CAM", null);
        clearInvocations(registro);
        servicio.deshacerDescarte("350000000000088", 7);
        verify(descartes).deshacer("350000000000088");
        verifyNoInteractions(registro);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.deshacerDescarte(" ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.deshacerDescarte("9".repeat(41), 7)));
    }

    /** Como «Revisado»: escritura y línea del registro en la misma transacción, en READ COMMITTED (error 1020 de MariaDB). */
    @Test void lasEscriturasDelDescarteVanEnReadCommitted() throws NoSuchMethodException {
        for (var m : List.of(PorPasarNsys.class.getMethod("descartar", String.class, String.class, int.class),
                PorPasarNsys.class.getMethod("deshacerDescarte", String.class, int.class))) {
            Transactional t = m.getAnnotation(Transactional.class);
            assertNotNull(t, m.getName());
            assertEquals(Isolation.READ_COMMITTED, t.isolation(), m.getName());
        }
    }

    @Test void elRepasoQuitaLosDescartesDeImeisQueNsysYaConoce() {
        when(descartes.todos()).thenReturn(List.of(
                new DescarteTaller("sin-imei-cam", 7, "Supertécnico 1", AHORA, "no es un móvil"),
                new DescarteTaller("350000000000088", 7, "Supertécnico 1", AHORA, "móvil de un cliente")));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of("SIN-IMEI-CAM"));
        servicio.repasarDescartes();
        verify(descartes).quitar(List.of("sin-imei-cam"));
    }

    @Test void sinDescartesElRepasoNoLeeElEspejo() {
        servicio.repasarDescartes();
        verify(nsys, never()).clavesDelEspejo();
        verify(descartes, never()).quitar(any());
    }

    @Test void siNsysNoConoceNingunoElRepasoNoEscribe() {
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA, "no es un móvil")));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of("350000000000001"));
        servicio.repasarDescartes();
        verify(descartes, never()).quitar(any());
    }
```

- [ ] **Step 2: Ver que falla** — `mvn -q -o test -Dtest=PorPasarNsysTest` → error de compilación (constructor, métodos).

- [ ] **Step 3: El servicio**

En `S/PorPasarNsys.java`:

1. Constantes, junto a `MAX_IMEI`:

```java
    static final String ACCION_DESCARTAR = "DESCARTAR_ALTA_NSYS";
    static final String ACCION_DESHACER_DESCARTE = "DESHACER_DESCARTE_NSYS";
    /** El largo de Taller_descarte.MOTIVO. */
    static final int MAX_MOTIVO = 500;
```

2. Campo `private final DescarteDAO descarteDao;` (tras `hechoDao`) y constructores:

```java
    @Autowired
    public PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, DescarteDAO descarteDao,
                        LogDAO logDao) {
        this(taller, tallerDao, nsysDao, hechoDao, descarteDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, DescarteDAO descarteDao, LogDAO logDao,
                 Clock clock) {
        this.taller = taller;
        this.tallerDao = tallerDao;
        this.nsysDao = nsysDao;
        this.hechoDao = hechoDao;
        this.descarteDao = descarteDao;
        this.logDao = logDao;
        this.clock = clock;
    }
```

3. `tarjetas()` y un auxiliar:

```java
    /** Las cuatro tarjetas (§3) y los descartados de «No está en NSYS» (pieza 3), con una lectura de cada cosa. */
    public TarjetasPorPasar tarjetas() {
        List<TallerNsys.MovilPorPasar> moviles = movilesEnEmpresa();
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        List<AltaTaller> altas = altas();
        Map<String, DescarteTaller> descartes = descartes(altas);
        return new TarjetasPorPasar(TarjetasNsys.color(moviles, marcas), TarjetasNsys.grado(moviles, marcas),
                TarjetasNsys.altas(altas, marcas, descartes), TarjetasNsys.revisar(avisos, espejo(avisos)),
                TarjetasNsys.descartadas(altas, descartes));
    }

    /** Los descartes por IMEI (pieza 3); sin filas en «No está en NSYS» no los lee. */
    private Map<String, DescarteTaller> descartes(List<AltaTaller> altas) {
        return altas.isEmpty() ? Map.of() : TarjetasNsys.descartesPorClave(descarteDao.todos());
    }
```

4. `plantillaAltas()`:

```java
    /** La plantilla de altas (spec §4): los IMEI de «No está en NSYS» sin marca «Hecho» que valga ni descarte, con el modelo del
     *  taller. */
    public Plantilla plantillaAltas() {
        List<AltaTaller> altas = altas();
        List<PlantillaNsys.FilaAlta> filas = TarjetasNsys.altas(altas, TarjetasNsys.porClave(hechoDao.todas()), descartes(altas))
                .stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()))
                .map(f -> new PlantillaNsys.FilaAlta(f.imeiSn(), PlantillaNsys.nombreModelo(f.modelo()))).toList();
        return new Plantilla("NSYS_altas_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.altas(filas), filas.size());
    }
```

5. El IMEI de una petición en un sitio (y `desmarcar` lo usa):

```java
    /** El IMEI de una petición, sin espacios a los lados: 400 si falta o pasa de {@value #MAX_IMEI} caracteres. */
    private static String imei(String imeiSn) {
        if (imeiSn == null || imeiSn.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el IMEI");
        String limpio = imeiSn.strip();
        if (limpio.length() > MAX_IMEI)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un IMEI no puede pasar de " + MAX_IMEI + " caracteres");
        return limpio;
    }

    /** «deshacer» de una marca. */
    public void desmarcar(String tarjeta, String imeiSn) {
        TarjetaHecho t = tarjeta(tarjeta);
        hechoDao.desmarcar(t, imei(imeiSn));
    }
```

(Ojo con el orden de `desmarcar`: hoy valida la tarjeta antes que el IMEI; se conserva.)

6. Descartar, deshacer y el repaso:

```java
    /** «descartar» de «No está en NSYS» (pieza 3 §4.3): guarda el descarte (o sustituye el que hubiera), quita la marca «Hecho» de
     *  esa fila y lo apunta en el registro de actividad con el motivo. READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void descartar(String imeiSn, String motivo, int idUsu) {
        String imei = imei(imeiSn);
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el motivo");
        if (limpio.length() > MAX_MOTIVO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
        descarteDao.descartar(imei, idUsu, limpio, ahora());
        hechoDao.desmarcar(TarjetaHecho.ALTA, imei);
        logDao.insertar(idUsu, ACCION_DESCARTAR, "IMEI: " + imei, limpio);
    }

    /** «deshacer» de «Ver descartados»: quita el descarte y, si había uno, lo apunta. READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deshacerDescarte(String imeiSn, int idUsu) {
        String imei = imei(imeiSn);
        if (descarteDao.deshacer(imei) > 0) logDao.insertar(idUsu, ACCION_DESHACER_DESCARTE, "IMEI: " + imei, null);
    }

    /** Tras cada importación ACTUAL que acaba en OK (pieza 3 §4.4): fuera los descartes de los IMEI que NSYS ya conoce. Los
     *  superados por un trabajo nuevo se quedan (dan el aviso de la fila). Sin descartes no lee el espejo. */
    public void repasarDescartes() {
        List<DescarteTaller> descartes = descarteDao.todos();
        if (descartes.isEmpty()) return;
        Set<String> espejo = nsysDao.clavesDelEspejo();
        List<String> enNsys = descartes.stream().map(DescarteTaller::imeiSn).filter(i -> espejo.contains(NsysDAO.claveCruce(i))).toList();
        if (enNsys.isEmpty()) return;
        descarteDao.quitar(enNsys);
        log.info("NSYS: descartes de «No está en NSYS» quitados: {} (ya en NSYS)", enNsys.size());
    }
```

7. El javadoc de la clase: «…«Hecho», «Revisado», el repaso de las marcas tras cada importación y los descartes de «No está en NSYS» (pieza 3). …».

- [ ] **Step 4: Ver que pasa** — `mvn -q -o test -Dtest=PorPasarNsysTest,TarjetasNsysTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java && git commit -m "feat: descartar y deshacer en No esta en NSYS, sin descartados en la plantilla, y su repaso (0.9.22, pieza 3)"
```

---

### Task 5: Rutas, repaso tras importar, permisos y contrato

**Files:**
- Modify: `S/PorPasarController.java`, `S/ImportadorNsys.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java`, `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`, `T/ImportadorNsysTest.java`

**Interfaces:**
- Consumes: `PorPasarNsys.descartar/deshacerDescarte/repasarDescartes` (Task 4).
- Produces: `PUT` y `DELETE /api/stock-telefonos/por-pasar/descarte` (204), esquemas `PorPasarPeticionDescarte {imeiSn, motivo}` y `PorPasarPeticionDeshacerDescarte {imeiSn}`; `target/openapi.json` para la Task 6.

- [ ] **Step 1: Las pruebas (fallan)**

En `RolesStockTelefonosTest`:
1. Junto a los otros: `@MockBean DescarteDAO descarteDao;` (import `com.reparaciones.servidor.nsys.DescarteDAO` si el fichero importa las clases de nsys una a una).
2. Prueba nueva:

```java
    /** 0.9.22 pieza 3: «descartar» y su «deshacer», con la misma guarda que el resto. */
    @Test void elDescarteSoloParaSupertecnicoYAdmin() throws Exception {
        String descartar = "{\"imeiSn\":\"SIN-IMEI-CAM\",\"motivo\":\"no es un móvil\"}";
        String deshacer = "{\"imeiSn\":\"SIN-IMEI-CAM\"}";
        mvc.perform(put("/api/stock-telefonos/por-pasar/descarte").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(descartar)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/descarte").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(deshacer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/stock-telefonos/por-pasar/descarte")
                .contentType(MediaType.APPLICATION_JSON).content(descartar)).andExpect(status().isForbidden());
        verifyNoInteractions(descarteDao);

        mvc.perform(put("/api/stock-telefonos/por-pasar/descarte").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON).content(descartar)).andExpect(status().isNoContent());
        verify(descarteDao).descartar(eq("SIN-IMEI-CAM"), eq(7), eq("no es un móvil"), any());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/descarte").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(deshacer)).andExpect(status().isNoContent());
        verify(descarteDao).deshacer("SIN-IMEI-CAM");
        mvc.perform(put("/api/stock-telefonos/por-pasar/descarte").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"imeiSn\":\"SIN-IMEI-CAM\",\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
```

(Sin token da 403, como en las otras pruebas de este fichero; con un token que no es un JWT, 401.)

En `OpenApiContractTest`, dentro del bloque de la 0.9.21/0.9.22 (el de `elContratoPublicaElStockDeTelefonos`):
- añade `"/api/stock-telefonos/por-pasar/descarte"` a la lista de rutas;
- añade `"FilaDescartadaNsys", "PorPasarPeticionDescarte", "PorPasarPeticionDeshacerDescarte"` a la lista de esquemas;
- y al final:

```java
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/por-pasar/descarte", "put").endsWith("/PorPasarPeticionDescarte"));
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/por-pasar/descarte", "delete").endsWith("/PorPasarPeticionDeshacerDescarte"));
        assertNoNullable(esquemas, "PorPasarPeticionDescarte", "imeiSn", "motivo");
        assertNoNullable(esquemas, "PorPasarPeticionDeshacerDescarte", "imeiSn");
        assertNullable(esquemas, "FilaAltaNsys", "descartadoAntesPor", "descartadoAntesEn", "descartadoAntesMotivo");
        assertNullable(esquemas, "FilaDescartadaNsys", "modelo", "descartadoPor");
        assertNoNullable(esquemas, "FilaDescartadaNsys", "imeiSn", "ultimoTrabajo", "categoria", "descartadoEn", "motivo");
        assertNoNullable(esquemas, "TarjetasPorPasar", "descartadas");
```

En `T/ImportadorNsysTest.java`:
- en `trasCerrarLoDelTallerSeRepasanLasMarcasConLaHoraDelFichero`, al final: `orden.verify(porPasar).repasarDescartes();`;
- en `siElCierreFallaNoSeRepasanLasMarcasYLaImportacionSigueOk` y en `unaImportacionRechazadaNoRepasaLasMarcas`: `verify(porPasar, never()).repasarDescartes();`;
- prueba nueva (junto a `siFallaElRepasoLaPasadaSigue`):

```java
    @Test void siFallaElRepasoDeLosDescartesLaPasadaSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        doThrow(new IllegalStateException("fallo")).when(porPasar).repasarDescartes();

        importador.pasada();

        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
        verify(dao, times(2)).aplicar(any(), anyInt());
    }
```

- [ ] **Step 2: Ver que falla** — `mvn -q -o test -Dtest=RolesStockTelefonosTest,ImportadorNsysTest,OpenApiContractTest` → FAIL (404/405 en las rutas, `repasarDescartes` nunca llamado, esquemas que faltan).

- [ ] **Step 3: Las rutas**

En `S/PorPasarController.java`, tras `revisado(...)`:

```java
    /** «descartar» de una fila de «No está en NSYS» (pieza 3 §4.3), con motivo. */
    @PutMapping("/descarte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@RequestBody PeticionDescarte peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.descartar(peticion.imeiSn(), peticion.motivo(), principal.getIdUsu());
    }

    /** «deshacer» de un descarte («Ver descartados»). */
    @DeleteMapping("/descarte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerDescarte(@RequestBody PeticionDeshacerDescarte peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.deshacerDescarte(peticion.imeiSn(), principal.getIdUsu());
    }
```

y al final, junto a los otros registros:

```java
    /** Esquema PorPasarPeticionDescarte. */
    record PeticionDescarte(String imeiSn, String motivo) {}

    /** Esquema PorPasarPeticionDeshacerDescarte. */
    record PeticionDeshacerDescarte(String imeiSn) {}
```

El javadoc de la clase: «…las listas, «Hecho», «Revisado», los descartes de «No está en NSYS» (pieza 3) y las plantillas…».

- [ ] **Step 4: El repaso tras importar**

En `S/ImportadorNsys.java`, la línea `if (cerrarTaller()) repasarHechos(f.fecha());` pasa a:

```java
            if (cerrarTaller()) {
                repasarHechos(f.fecha());
                repasarDescartes();
            }
```

y tras `repasarHechos(...)`:

```java
    /** Tras el repaso de las marcas, el de los descartes de «No está en NSYS» (spec 0.9.22 pieza 3 §4.4): fuera los de IMEI que
     *  NSYS ya conoce. Con la misma condición (cierre bueno); si falla, se avisa en el registro del servidor y se repite con el
     *  siguiente fichero. */
    private void repasarDescartes() {
        try {
            porPasar.repasarDescartes();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron repasar los descartes tras la importación", e);
        }
    }
```

- [ ] **Step 5: Ver que pasa** — `mvn -q -o test -Dtest=RolesStockTelefonosTest,ImportadorNsysTest,OpenApiContractTest,PorPasarNsysTest` → PASS. Después la suite entera: `mvn -q -o test` → todo verde (**1513 + las nuevas**); apunta el total. Deja `target/openapi.json` (lo usa la Task 6).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/PorPasarController.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java && git commit -m "feat: rutas de descartar y deshacer en No esta en NSYS y su repaso tras cada importacion (0.9.22, pieza 3)"
```

---

### Task 6: Web: contrato, llamadas y datos de prueba

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (generado), `src/shared/api/client.ts`, `src/modules/telefonos/api.ts`, `src/modules/telefonos/api.test.tsx`, `src/modules/telefonos/test/fabrica.ts`, `src/test/server.ts`, `src/modules/telefonos/stock/PorPasarNsys.tsx` (solo `VACIAS`)

**Interfaces:**
- Consumes: `target/openapi.json` del servidor (Task 5).
- Produces:
  - tipos `FilaDescartadaNsys`, `PeticionDescarte`, `PeticionDeshacerDescarte` en `@/shared/api/client`; `TarjetasPorPasar.descartadas`; `FilaAltaNsys.descartadoAntesPor/En/Motivo`;
  - `useDescartarAlta()` (mutación `{ imeiSn, motivo }`) y `useDeshacerDescarte()` (mutación `{ imeiSn }`) en `src/modules/telefonos/api.ts`;
  - `filaDescartada(p?)` en `src/modules/telefonos/test/fabrica.ts`; `filaAlta()` con los tres campos a null; `tarjetas()` con `descartadas: []`.

- [ ] **Step 0: Worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q origin && git worktree add -b dev_v0922_descartar_nsys /c/Users/dev/Documents/wt/web-0922-descartar origin/main && cd /c/Users/dev/Documents/wt/web-0922-descartar && npm ci
```
Expected: `577bf6b` y `npm ci` sin errores.

- [ ] **Step 1: El contrato**

```bash
cp /c/Users/dev/Documents/wt/servidor-0922-descartar/target/openapi.json /c/Users/dev/Documents/wt/web-0922-descartar/api/openapi.json && cd /c/Users/dev/Documents/wt/web-0922-descartar && npm run api:types:offline && git diff --stat
```
Expected: cambian `api/openapi.json` y `src/shared/api/schema.d.ts` (rutas `/api/stock-telefonos/por-pasar/descarte`, esquemas nuevos).

- [ ] **Step 2: La prueba de las llamadas (falla)**

En `src/modules/telefonos/api.test.tsx`, importa `useDescartarAlta, useDeshacerDescarte` desde `./api` y añade:

```tsx
  it('«descartar» y su «deshacer» mandan el IMEI y el motivo (spec 0.9.22 pieza 3)', async () => {
    const pedidos: unknown[] = []
    server.use(
      http.put('*/api/stock-telefonos/por-pasar/descarte', async ({ request }) => {
        pedidos.push(['PUT', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
      http.delete('*/api/stock-telefonos/por-pasar/descarte', async ({ request }) => {
        pedidos.push(['DELETE', await request.json()])
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const wrapper = envoltorio()
    const descartar = renderHook(() => useDescartarAlta(), { wrapper })
    await descartar.result.current.mutateAsync({ imeiSn: 'SIN-IMEI-CAM', motivo: 'no es un móvil' })
    const deshacer = renderHook(() => useDeshacerDescarte(), { wrapper })
    await deshacer.result.current.mutateAsync({ imeiSn: 'SIN-IMEI-CAM' })
    expect(pedidos).toEqual([
      ['PUT', { imeiSn: 'SIN-IMEI-CAM', motivo: 'no es un móvil' }],
      ['DELETE', { imeiSn: 'SIN-IMEI-CAM' }],
    ])
  })
```

Run: `npx vitest run src/modules/telefonos/api.test.tsx` → FAIL (`useDescartarAlta` no existe).

- [ ] **Step 3: Tipos y llamadas**

En `src/shared/api/client.ts`, tras `PeticionDeshacerHecho`:

```ts
/** 0.9.22 pieza 3: los descartados de «No está en NSYS» («Ver descartados») y las peticiones de «descartar» (con motivo) y de su
 *  «deshacer». Una fila de «No está en NSYS» que vuelve tras un trabajo nuevo lleva `descartadoAntesPor/En/Motivo`. */
export type FilaDescartadaNsys = components['schemas']['FilaDescartadaNsys']
export type PeticionDescarte = components['schemas']['PorPasarPeticionDescarte']
export type PeticionDeshacerDescarte = components['schemas']['PorPasarPeticionDeshacerDescarte']
```

En `src/modules/telefonos/api.ts`, añade `type PeticionDescarte, type PeticionDeshacerDescarte` al import de `@/shared/api/client` y, tras `useRevisarAviso`:

```ts
/** PUT /api/stock-telefonos/por-pasar/descarte (spec 0.9.22 pieza 3 §4.3): «descartar» de «No está en NSYS», con motivo. */
export function useDescartarAlta() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionDescarte) => api.PUT('/api/stock-telefonos/por-pasar/descarte', { body: cuerpo }),
    onSettled: recargar,
  })
}

/** DELETE /api/stock-telefonos/por-pasar/descarte: «deshacer» desde «Ver descartados». */
export function useDeshacerDescarte() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionDeshacerDescarte) => api.DELETE('/api/stock-telefonos/por-pasar/descarte', { body: cuerpo }),
    onSettled: recargar,
  })
}
```

- [ ] **Step 4: Datos de prueba**

En `src/modules/telefonos/test/fabrica.ts`: añade `FilaDescartadaNsys` al import de tipos; en `filaAlta`, tras `hechoVolvio: null,` añade `descartadoAntesPor: null, descartadoAntesEn: null, descartadoAntesMotivo: null,`; tras `filaAlta`:

```ts
/** Un IMEI descartado de «No está en NSYS»: una cámara sin IMEI, descartada por «Supertécnico 1» con su motivo. */
export function filaDescartada(p: Partial<FilaDescartadaNsys> = {}): FilaDescartadaNsys {
  return {
    imeiSn: 'SIN-IMEI-CAM', modelo: null, ultimoTrabajo: 'R20260803_14', categoria: 'REPARACION', descartadoPor: 'Supertécnico 1',
    descartadoEn: '2026-10-26T12:31:00', motivo: 'no es un móvil, es una cámara', ...p,
  }
}
```

y `tarjetas()` devuelve `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], ...p }`.

En `src/test/server.ts`, el manejador de `*/api/stock-telefonos/por-pasar/tarjetas` devuelve `{ color: [], grado: [], altas: [], revisar: [], descartadas: [] }`.

En `src/modules/telefonos/stock/PorPasarNsys.tsx`: `const VACIAS: TarjetasPorPasar = { color: [], grado: [], altas: [], revisar: [], descartadas: [] }`.

- [ ] **Step 5: Ver que pasa** — `npx vitest run src/modules/telefonos` y `npx tsc -b` → verde.

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/api.ts src/modules/telefonos/api.test.tsx src/modules/telefonos/test/fabrica.ts src/test/server.ts src/modules/telefonos/stock/PorPasarNsys.tsx && git commit -m "feat: contrato y llamadas de descartar en No esta en NSYS (0.9.22, pieza 3)"
```

---

### Task 7: Web: los textos del descarte

**Files:**
- Modify: `src/modules/telefonos/stock/porPasar.ts`
- Test: `src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Produces (en `porPasar.ts`): `TITULO_DESCARTAR`, `EXPLICA_DESCARTAR`, `EJEMPLOS_MOTIVO`, `MAX_MOTIVO = 500`, `avisoDescartado(imei)`, `avisoVuelve(imei)`, `textoVerDescartados(n, abierto)`, `textoDescartadoAntes(f: FilaAltaNsys): string | null`, `textoDescartado(f: FilaDescartadaNsys)`; `textoUltimoTrabajo` y `modeloDelTaller` aceptan también una `FilaDescartadaNsys`; `AYUDA.altas` con la frase nueva.

- [ ] **Step 1: Las pruebas (fallan)**

En `porPasar.test.ts` (importa lo nuevo y `filaAlta`, `filaDescartada` de `../test/fabrica`):

```ts
describe('«descartar» en «No está en NSYS» (spec 0.9.22 pieza 3 §5)', () => {
  it('los textos de la ventana y de los avisos', () => {
    expect(TITULO_DESCARTAR).toBe('Descartar de «No está en NSYS»')
    expect(EXPLICA_DESCARTAR).toBe('deja de salir aquí y no va en la plantilla de altas. Las importaciones de NSYS no lo devuelven; solo sale otra vez si el taller le hace un trabajo nuevo.')
    expect(EJEMPLOS_MOTIVO).toBe('Ejemplos: «no es un móvil, es una cámara», «móvil de un cliente, no es stock».')
    expect(MAX_MOTIVO).toBe(500)
    expect(avisoDescartado('SIN-IMEI-CAM')).toBe('Descartado «SIN-IMEI-CAM»')
    expect(avisoVuelve('SIN-IMEI-CAM')).toBe('«SIN-IMEI-CAM» vuelve a la lista')
    expect(textoVerDescartados(2, false)).toBe('Ver descartados (2)')
    expect(textoVerDescartados(2, true)).toBe('Ocultar descartados')
  })

  it('la fila que vuelve dice quién la descartó, cuándo y por qué; sin descarte, nada', () => {
    expect(textoDescartadoAntes(filaAlta({ descartadoAntesPor: 'Supertécnico 1', descartadoAntesEn: '2026-10-26T12:31:00', descartadoAntesMotivo: 'no es un móvil' })))
      .toBe('Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo descartara el 26/10/2026 12:31 («no es un móvil»)')
    expect(textoDescartadoAntes(filaAlta())).toBeNull()
  })

  it('«Ver descartados»: quién y cuándo, el último trabajo y el modelo del taller', () => {
    expect(textoDescartado(filaDescartada())).toBe('Supertécnico 1 · 26/10/2026 12:31')
    expect(textoDescartado(filaDescartada({ descartadoPor: null }))).toBe('— · 26/10/2026 12:31')
    expect(textoUltimoTrabajo(filaDescartada())).toBe('R20260803_14 · Reparación')
    expect(modeloDelTaller(filaDescartada())).toBe('—')
    expect(modeloDelTaller(filaDescartada({ modelo: '14' }))).toBe('iPhone 14')
  })

  it('la ayuda de «No está en NSYS» habla de «descartar»', () => {
    expect(AYUDA.altas.endsWith('Si una fila no debería estar aquí (no es un móvil, es de un cliente…), «descartar» la quita con un motivo; solo vuelve si el taller le hace un trabajo nuevo.')).toBe(true)
  })
})
```

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts` → FAIL.

- [ ] **Step 2: El código**

En `porPasar.ts`:
1. Import de tipos: añade `FilaDescartadaNsys`.
2. `AYUDA.altas` acaba en «… Cada fila se va sola cuando la importación los ve en NSYS. Si una fila no debería estar aquí (no es un móvil, es de un cliente…), «descartar» la quita con un motivo; solo vuelve si el taller le hace un trabajo nuevo.»
3. `textoUltimoTrabajo` y `modeloDelTaller`:

```ts
/** «R20261006_57 · Reparación», con « · asignado» si es una asignación sin terminar (las descartadas no lo dicen). */
export const textoUltimoTrabajo = (f: { ultimoTrabajo: string; categoria: string; abierto?: boolean }): string =>
  `${f.ultimoTrabajo} · ${CATEGORIA[f.categoria] ?? f.categoria}${f.abierto ? ' · asignado' : ''}`

/** El modelo del taller de «No está en NSYS» («iPhone 14»): el código del ERP traducido. */
export const modeloDelTaller = (f: { modelo: string | null }): string => (f.modelo ? nombreModeloNsys(f.modelo, null) : '—')
```

4. Al final:

```ts
// ---------------------------------------------------------------- 0.9.22 pieza 3: «descartar» en «No está en NSYS»

export const TITULO_DESCARTAR = 'Descartar de «No está en NSYS»'
/** Lo que va tras el IMEI (en negrita) en la ventana. */
export const EXPLICA_DESCARTAR =
  'deja de salir aquí y no va en la plantilla de altas. Las importaciones de NSYS no lo devuelven; solo sale otra vez si el taller le hace un trabajo nuevo.'
export const EJEMPLOS_MOTIVO = 'Ejemplos: «no es un móvil, es una cámara», «móvil de un cliente, no es stock».'
/** El largo de Taller_descarte.MOTIVO (el servidor da 400 por encima). */
export const MAX_MOTIVO = 500

export const avisoDescartado = (imei: string): string => `Descartado «${imei}»`
export const avisoVuelve = (imei: string): string => `«${imei}» vuelve a la lista`
export const textoVerDescartados = (n: number, abierto: boolean): string => (abierto ? 'Ocultar descartados' : `Ver descartados (${n})`)

/** «Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo descartara el 26/10/2026 12:31 («no es un móvil»)»; sin un
 *  descarte superado, null. */
export function textoDescartadoAntes(f: FilaAltaNsys): string | null {
  if (!f.descartadoAntesEn) return null
  return `Salió otra vez: trabajo nuevo después de que ${f.descartadoAntesPor ?? '—'} lo descartara el ${textoDesde(f.descartadoAntesEn)} («${f.descartadoAntesMotivo ?? ''}»)`
}

/** «Supertécnico 1 · 26/10/2026 12:31» de «Ver descartados». */
export const textoDescartado = (f: FilaDescartadaNsys): string => `${f.descartadoPor ?? '—'} · ${textoDesde(f.descartadoEn)}`
```

- [ ] **Step 3: Ver que pasa** — `npx vitest run src/modules/telefonos/stock` y `npx tsc -b` → verde.

- [ ] **Step 4: Commit**

```bash
git add src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts && git commit -m "feat: textos de descartar en No esta en NSYS (0.9.22, pieza 3)"
```

---

### Task 8: Web: el enlace, la ventana y «Ver descartados»

**Files:**
- Create: `src/modules/telefonos/stock/DescartarAltaDialog.tsx`
- Modify: `src/modules/telefonos/stock/CeldaHecho.tsx`, `src/modules/telefonos/stock/ListasPorPasar.tsx`, `src/modules/telefonos/stock/PorPasarNsys.tsx`
- Test: `src/modules/telefonos/stock/PorPasarNsys.test.tsx`

**Interfaces:**
- Consumes: `useDescartarAlta`, `useDeshacerDescarte` (Task 6); textos (Task 7).
- Produces: `DescartarAltaDialog({ imeiSn: string | null; enviando: boolean; onDescartar: (motivo: string) => void; onCerrar: () => void })`; `CeldaHecho` con prop opcional `onDescartar?: () => void`; `ListaAltas` con `descartadas`, `onDescartar(imeiSn)`, `onRecuperar(imeiSn)`.

- [ ] **Step 1: Las pruebas (fallan)**

En `PorPasarNsys.test.tsx` importa `filaDescartada` de `../test/fabrica` y añade dentro del `describe`:

```tsx
  it('«descartar» solo en «No está en NSYS» y en las filas sin marca', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ color: [filaColor()], altas: [filaAlta(), filaAlta({ imeiSn: '350000000000099', ...HECHO })] }))
    expect(screen.queryAllByRole('button', { name: /^Descartar / })).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    const enlace = screen.getByRole('button', { name: 'Descartar 350000000000088' })
    expect(enlace).toHaveTextContent('descartar')
    expect(enlace).toHaveAttribute('title', 'No va a NSYS: quitarlo de la lista (con motivo)')
    expect(screen.queryByRole('button', { name: 'Descartar 350000000000099' })).not.toBeInTheDocument()
  })

  it('la ventana pide el motivo, lo manda limpio, se cierra y avisa', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(http.put('*/api/stock-telefonos/por-pasar/descarte', async ({ request }) => {
      pedidos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    pintar(tarjetas({ altas: [filaAlta({ imeiSn: 'SIN-IMEI-CAM', modelo: null })] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    await user.click(screen.getByRole('button', { name: 'Descartar SIN-IMEI-CAM' }))
    const ventana = screen.getByRole('dialog', { name: 'Descartar de «No está en NSYS»' })
    expect(ventana).toHaveTextContent('SIN-IMEI-CAM deja de salir aquí y no va en la plantilla de altas. Las importaciones de NSYS no lo devuelven; solo sale otra vez si el taller le hace un trabajo nuevo.')
    expect(ventana).toHaveTextContent('Ejemplos: «no es un móvil, es una cámara», «móvil de un cliente, no es stock».')
    const motivo = within(ventana).getByLabelText('Motivo (obligatorio)')
    expect(motivo).toHaveAttribute('placeholder', 'Por qué no va a NSYS')
    const boton = within(ventana).getByRole('button', { name: 'Descartar' })
    expect(boton).toBeDisabled()
    await user.type(motivo, '   ')
    expect(boton).toBeDisabled()
    await user.type(motivo, 'no es un móvil, es una cámara ')
    expect(boton).toBeEnabled()
    await user.click(boton)
    await waitFor(() => expect(pedidos).toEqual([{ imeiSn: 'SIN-IMEI-CAM', motivo: 'no es un móvil, es una cámara' }]))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Descartar de «No está en NSYS»' })).not.toBeInTheDocument())
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Descartado «SIN-IMEI-CAM»')
  })

  it('«Cancelar» y Esc cierran sin mandar nada', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(http.put('*/api/stock-telefonos/por-pasar/descarte', async ({ request }) => {
      pedidos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    pintar(tarjetas({ altas: [filaAlta()] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    await user.click(screen.getByRole('button', { name: 'Descartar 350000000000088' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog', { name: 'Descartar de «No está en NSYS»' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Descartar 350000000000088' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Descartar de «No está en NSYS»' })).not.toBeInTheDocument()
    expect(pedidos).toEqual([])
  })

  it('si el servidor falla, la ventana sigue abierta con lo escrito; un doble clic manda una sola vez', async () => {
    const user = userEvent.setup()
    let veces = 0
    server.use(http.put('*/api/stock-telefonos/por-pasar/descarte', () => {
      veces++
      return HttpResponse.json({ message: 'Falta el motivo' }, { status: 400 })
    }))
    pintar(tarjetas({ altas: [filaAlta()] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    await user.click(screen.getByRole('button', { name: 'Descartar 350000000000088' }))
    const ventana = screen.getByRole('dialog', { name: 'Descartar de «No está en NSYS»' })
    await user.type(within(ventana).getByLabelText('Motivo (obligatorio)'), 'móvil de un cliente')
    await user.dblClick(within(ventana).getByRole('button', { name: 'Descartar' }))
    await waitFor(() => expect(veces).toBe(1))
    // El diálogo «Error» de la app (AlertaProvider) es modal y esconde el resto a las consultas por rol: se mira por texto.
    expect(await screen.findByRole('dialog', { name: 'Error' })).toBeInTheDocument()
    expect(screen.getByText('Descartar de «No está en NSYS»')).toBeInTheDocument()
    expect(screen.getByDisplayValue('móvil de un cliente')).toBeInTheDocument()
    expect(veces).toBe(1)
  })

  it('«Ver descartados» los enseña en gris con su motivo y «deshacer» los devuelve', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(http.delete('*/api/stock-telefonos/por-pasar/descarte', async ({ request }) => {
      pedidos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    pintar(tarjetas({ altas: [filaAlta()], descartadas: [filaDescartada()] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    expect(screen.queryByRole('table', { name: 'Descartados' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver descartados (1)' }))
    const tabla = screen.getByRole('table', { name: 'Descartados' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Último trabajo', 'Descartado', 'Motivo', ''])
    const [fila] = filas(tabla)
    expect(fila).toHaveTextContent('SIN-IMEI-CAM')
    expect(fila).toHaveTextContent('R20260803_14 · Reparación')
    expect(fila).toHaveTextContent('Supertécnico 1 · 26/10/2026 12:31')
    expect(fila).toHaveTextContent('«no es un móvil, es una cámara»')
    await user.click(screen.getByRole('button', { name: 'Deshacer descarte de SIN-IMEI-CAM' }))
    await waitFor(() => expect(pedidos).toEqual([{ imeiSn: 'SIN-IMEI-CAM' }]))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('«SIN-IMEI-CAM» vuelve a la lista')
    await user.click(screen.getByRole('button', { name: 'Ocultar descartados' }))
    expect(screen.queryByRole('table', { name: 'Descartados' })).not.toBeInTheDocument()
  })

  it('sin descartados no sale «Ver descartados»; la fila que vuelve dice quién la descartó', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ altas: [filaAlta({ descartadoAntesPor: 'Supertécnico 1', descartadoAntesEn: '2026-10-26T12:31:00', descartadoAntesMotivo: 'no es un móvil' })] }))
    await user.click(screen.getByRole('button', { name: 'No está en NSYS' }))
    expect(screen.queryByRole('button', { name: /^Ver descartados/ })).not.toBeInTheDocument()
    expect(screen.getByText('Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo descartara el 26/10/2026 12:31 («no es un móvil»)')).toBeInTheDocument()
  })
```

Run: `npx vitest run src/modules/telefonos/stock/PorPasarNsys.test.tsx` → FAIL.

- [ ] **Step 2: La ventana**

`src/modules/telefonos/stock/DescartarAltaDialog.tsx`:

```tsx
import { useLayoutEffect, useRef, useState } from 'react'
import { useCerrojoEnvio } from '@/shared/lib/useCerrojoEnvio'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { EJEMPLOS_MOTIVO, EXPLICA_DESCARTAR, MAX_MOTIVO, TITULO_DESCARTAR } from './porPasar'

type Props = { imeiSn: string | null; enviando: boolean; onDescartar: (motivo: string) => void; onCerrar: () => void }

/** La ventana de «descartar» de «No está en NSYS» (spec 0.9.22 pieza 3 §5.2): explica qué pasa y pide el motivo (obligatorio, hasta
 *  500). Como DialogoIncidencia: un doble clic no envía dos veces (useCerrojoEnvio); mientras guarda no se cierra y, si falla, sigue
 *  abierta con lo escrito (quien la abre la cierra cuando va bien). Abre con el foco en el motivo. */
export function DescartarAltaDialog({ imeiSn, enviando, onDescartar, onCerrar }: Props) {
  const [motivo, setMotivo] = useState('')
  const campo = useRef<HTMLTextAreaElement>(null)
  const enviar = useCerrojoEnvio({ abierto: imeiSn !== null, enviando })
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- vacía el motivo al abrir con otra fila
    if (imeiSn) setMotivo('')
  }, [imeiSn])
  const listo = motivo.trim() !== ''
  return (
    <Dialog open={imeiSn !== null} onOpenChange={(o) => { if (!o && !enviando) onCerrar() }}>
      <DialogContent className="max-w-[min(470px,calc(100%-2rem))] gap-2.5 p-5"
        onOpenAutoFocus={(e) => { e.preventDefault(); campo.current?.focus() }}>
        <DialogHeader>
          <DialogTitle className="text-[15px] font-bold text-azul-noche">{TITULO_DESCARTAR}</DialogTitle>
          <DialogDescription className="text-[12.5px] leading-snug text-azul-medio">
            <b className="font-mono">{imeiSn}</b>{` ${EXPLICA_DESCARTAR}`}
          </DialogDescription>
        </DialogHeader>
        <Label htmlFor="descartar-motivo" className="text-[12px] font-bold">Motivo (obligatorio)</Label>
        <textarea id="descartar-motivo" ref={campo} value={motivo} maxLength={MAX_MOTIVO} rows={3}
          placeholder="Por qué no va a NSYS" onChange={(e) => setMotivo(e.target.value)}
          className="w-full resize-y rounded border border-borde-input bg-superficie p-2 text-[13px] text-azul-medio" />
        <p className="text-[11px] text-azul-gris">{EJEMPLOS_MOTIVO}</p>
        <DialogFooter className="gap-2">
          <Button variant="outline" disabled={enviando} onClick={onCerrar}>Cancelar</Button>
          <Button disabled={!listo || enviando} onClick={() => enviar(() => onDescartar(motivo.trim()))}
            className="bg-rojo-accion text-crema hover:bg-rojo-accion/90">Descartar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 3: El enlace en `CeldaHecho`**

En `src/modules/telefonos/stock/CeldaHecho.tsx`: `Props` gana `onDescartar?: () => void` y la rama de la fila sin marca queda:

```tsx
  const volvio = textoVolvio(fila)
  return (
    <>
      <button type="button" disabled={ocupado} aria-label={`Hecho ${imeiSn}`} onClick={onMarcar} className={BOTON}>Hecho</button>
      {onDescartar && (
        <button type="button" disabled={ocupado} aria-label={`Descartar ${imeiSn}`} title="No va a NSYS: quitarlo de la lista (con motivo)"
          onClick={onDescartar} className="ml-2 cursor-pointer text-[11px] text-texto-suave hover:text-fila-urgente-brd disabled:cursor-not-allowed disabled:opacity-40">
          descartar
        </button>
      )}
      {volvio && <span className="mt-0.5 block max-w-[220px] text-[11px] font-bold whitespace-normal text-fila-urgente-brd">{volvio}</span>}
    </>
  )
```

(Y el javadoc: «… si la importación vio NSYS igual, el botón otra vez con el aviso en rojo. En «No está en NSYS», al lado, el enlace «descartar» (pieza 3).»)

- [ ] **Step 4: `ListasPorPasar`**

En `src/modules/telefonos/stock/ListasPorPasar.tsx`:
1. Imports: `useState` de `react` (junto a `type ReactNode`), `FilaDescartadaNsys` en los tipos, y de `./porPasar` también `textoDescartado, textoDescartadoAntes, textoVerDescartados`.
2. `TablaConHecho` acepta `onDescartar?: (imeiSn: string) => void` (en su tipo de props) y lo pasa a cada `CeldaHecho`: `onDescartar={onDescartar ? () => onDescartar(f.imeiSn) : undefined}`.
3. Antes de `ListaAltas`:

```tsx
/** «Ver descartados (n)» de «No está en NSYS» (pieza 3 §5.3): plegado al entrar; desplegado, en gris, con «deshacer». Solo se
 *  pinta con descartados (sin ellos se desmonta y vuelve plegado). */
function Descartados({ filas, ocupado, onRecuperar }: { filas: FilaDescartadaNsys[]; ocupado: boolean; onRecuperar: (imeiSn: string) => void }) {
  const [abierto, setAbierto] = useState(false)
  const columnas = ['IMEI', 'Modelo', 'Último trabajo', 'Descartado', 'Motivo', '']
  return (
    <div className="shrink-0">
      <button type="button" aria-expanded={abierto} onClick={() => setAbierto(!abierto)}
        className="cursor-pointer text-[12px] text-azul-gris hover:underline">{textoVerDescartados(filas.length, abierto)}</button>
      {abierto && (
        <div className="mt-1.5 max-h-[220px] overflow-auto rounded-lg border border-fila-sep">
          <table aria-label="Descartados" className="w-full border-collapse bg-badge-neutro-bg text-[12.5px] text-texto-suave">
            <thead>
              <tr>{columnas.map((c, i) => <th key={i} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.imeiSn} className="border-t border-fila-sep">
                  <td className={cn(CELDA, 'font-mono text-[11.5px] whitespace-nowrap')}>{f.imeiSn}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDelTaller(f)}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{textoUltimoTrabajo(f)}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{textoDescartado(f)}</td>
                  <td className={cn(CELDA, 'italic')}>{`«${f.motivo}»`}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>
                    <button type="button" disabled={ocupado} aria-label={`Deshacer descarte de ${f.imeiSn}`} onClick={() => onRecuperar(f.imeiSn)}
                      className="cursor-pointer text-[11px] text-azul-noche hover:underline disabled:opacity-40">deshacer</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
```

4. `ListaAltas` queda:

```tsx
type AltasProps = ConHechoProps<FilaAltaNsys> & {
  descartadas: FilaDescartadaNsys[]; onDescartar: (imeiSn: string) => void; onRecuperar: (imeiSn: string) => void
}

/** «No está en NSYS» (§3.4), con «descartar» en cada fila sin marca y «Ver descartados» al pie (pieza 3). */
export function ListaAltas({ onAbrirImei, descartadas, onDescartar, onRecuperar, ...props }: AltasProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <TablaConHecho {...props} nombre="No está en NSYS" onDescartar={onDescartar}
        columnas={['IMEI', 'Modelo', 'Último trabajo', 'Técnico', 'Cliente', 'Trabajos', 'Desde']}
        celdas={(f) => (
          <>
            <td className={CELDA}>
              <Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} />
              {textoDescartadoAntes(f) && <span className="mt-0.5 block max-w-[260px] text-[11px] whitespace-normal text-ambar-grafico">{textoDescartadoAntes(f)}</span>}
            </td>
            <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDelTaller(f)}<span className={GRIS}>el del taller</span></td>
            <td className={cn(CELDA, 'whitespace-nowrap')}>{textoUltimoTrabajo(f)}</td>
            <td className={cn(CELDA, 'whitespace-nowrap')}>{f.tecnico ?? '—'}</td>
            <td className={cn(CELDA, 'whitespace-nowrap')}>{f.cliente ?? '—'}</td>
            <td className={cn(CELDA, 'text-right whitespace-nowrap')}>{f.trabajos}</td>
            <td className={cn(CELDA, 'whitespace-nowrap')}>{diaNsys(f.desde)}</td>
          </>
        )} />
      {descartadas.length > 0 && <Descartados filas={descartadas} ocupado={props.ocupado} onRecuperar={onRecuperar} />}
    </div>
  )
}
```

- [ ] **Step 5: `PorPasarNsys`**

En `src/modules/telefonos/stock/PorPasarNsys.tsx`:
1. Imports: `useDescartarAlta, useDeshacerDescarte` de `../api`; `DescartarAltaDialog` de `./DescartarAltaDialog`; `avisoDescartado, avisoVuelve` de `./porPasar`.
2. Dentro del componente:

```tsx
  const descartar = useDescartarAlta()
  const recuperar = useDeshacerDescarte()
  const [aDescartar, setADescartar] = useState<string | null>(null)
  const ocupado = marcar.isPending || deshacer.isPending || descartar.isPending || recuperar.isPending
```

(sustituye la línea de `ocupado` que hay).
3. La lista de altas y la ventana (la ventana, al final de la `section`):

```tsx
      {elegida === 'altas' && (
        <ListaAltas filas={t.altas} {...conHecho('altas')} descartadas={t.descartadas} onDescartar={setADescartar}
          onRecuperar={(imeiSn) => recuperar.mutate({ imeiSn }, { onSuccess: () => avisar({ texto: avisoVuelve(imeiSn), tipo: 'ok' }) })} />
      )}
```

```tsx
      <DescartarAltaDialog imeiSn={aDescartar} enviando={descartar.isPending} onCerrar={() => setADescartar(null)}
        onDescartar={(motivo) => {
          const imeiSn = aDescartar
          if (imeiSn === null) return
          descartar.mutate({ imeiSn, motivo }, {
            onSuccess: () => { setADescartar(null); avisar({ texto: avisoDescartado(imeiSn), tipo: 'ok' }) },
          })
        }} />
```

- [ ] **Step 6: Ver que pasa** — `npx vitest run src/modules/telefonos`, `npx tsc -b`, `npm run lint` → verde.

- [ ] **Step 7: Commit**

```bash
git add src/modules/telefonos/stock/DescartarAltaDialog.tsx src/modules/telefonos/stock/CeldaHecho.tsx src/modules/telefonos/stock/ListasPorPasar.tsx src/modules/telefonos/stock/PorPasarNsys.tsx src/modules/telefonos/stock/PorPasarNsys.test.tsx && git commit -m "feat: descartar con motivo y Ver descartados en No esta en NSYS (0.9.22, pieza 3)"
```

---

### Task 9: Web: barra y × en los avisos de abajo

**Files:**
- Modify: `src/shared/ui/AvisoBreve.tsx`, `src/modules/taller/asignaciones/useAccionConDeshacer.tsx`
- Test: `src/shared/ui/AvisoBreve.test.tsx`, `src/modules/taller/asignaciones/useAccionConDeshacer.test.tsx`

**Interfaces:**
- Produces: la × (`aria-label` «Cerrar aviso», `title` «Cerrar») en los dos avisos; la barra `.aviso-barra` en el de «Deshacer», con `animationDuration` = `MS_DESHACER` ms.

- [ ] **Step 1: Las pruebas (fallan)**

En `AvisoBreve.test.tsx`, dentro del `describe`:

```tsx
  it('lleva la barra que se consume y una × que lo cierra antes', async () => {
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Descartado «SIN-IMEI-CAM»', tipo: 'ok' }))
    expect(screen.getByTestId('aviso-breve').querySelector('.aviso-barra')).not.toBeNull()
    const x = screen.getByRole('button', { name: 'Cerrar aviso' })
    expect(x).toHaveAttribute('title', 'Cerrar')
    await userEvent.click(x)
    expect(screen.queryByTestId('aviso-breve')).not.toBeInTheDocument()
  })
```

En `useAccionConDeshacer.test.tsx`:

```tsx
  it('lleva la barra de 8 s y una × que cierra sin deshacer; un clic en el texto no lo cierra', async () => {
    const a = accion()
    render(<Banco accion={a} />)
    fireEvent.click(screen.getByText('Actuar'))
    await screen.findByText(TEXTO)
    const barra = document.querySelector('.aviso-barra') as HTMLElement | null
    expect(barra).not.toBeNull()
    expect(barra?.style.animationDuration).toBe(`${MS_DESHACER}ms`)
    fireEvent.click(screen.getByText(TEXTO))
    expect(screen.getByText(TEXTO)).toBeInTheDocument()
    const x = screen.getByRole('button', { name: 'Cerrar aviso' })
    expect(x).toHaveAttribute('title', 'Cerrar')
    fireEvent.click(x)
    expect(screen.queryByText(TEXTO)).not.toBeInTheDocument()
    expect(a.deshacer).not.toHaveBeenCalled()
  })
```

Run: `npx vitest run src/shared/ui/AvisoBreve.test.tsx src/modules/taller/asignaciones/useAccionConDeshacer.test.tsx` → FAIL.

- [ ] **Step 2: `AvisoBreveHost`**

En `src/shared/ui/AvisoBreve.tsx`, tras `<span>{actual.texto}</span>` y antes de la barra:

```tsx
      <button type="button" aria-label="Cerrar aviso" title="Cerrar" onClick={(e) => { e.stopPropagation(); setActual(null) }}
        className="ml-auto flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded-md text-[16px] leading-none text-crema/70 hover:bg-superficie/10 hover:text-crema">
        ×
      </button>
```

y el javadoc: «… Una barra amarilla encoge en 4 s y al acabar se cierra; un clic (o la × de la derecha) lo cierra antes; …».

- [ ] **Step 3: El aviso de «Deshacer»**

En `src/modules/taller/asignaciones/useAccionConDeshacer.tsx`:
1. `type Pendiente = { texto: string; deshacer: () => Promise<unknown>; clave: number }` y en `ejecutar`: `setPendiente({ texto, deshacer, clave: numero })` (así la barra vuelve a empezar con cada aviso).
2. La clase del contenedor con aviso pasa a: `'fixed bottom-6 left-1/2 z-50 flex -translate-x-1/2 items-center gap-4 overflow-hidden rounded-lg border border-fila-sep bg-azul-noche px-4 pt-2.5 pb-[13px] text-[12px] font-bold text-crema shadow-md'`.
3. Dentro del fragmento, tras el botón «Deshacer»:

```tsx
          <button type="button" aria-label="Cerrar aviso" title="Cerrar" onClick={cerrar}
            className="-mr-1 flex h-6 w-6 cursor-pointer items-center justify-center rounded-md text-[16px] leading-none text-crema/70 hover:bg-azul-noche-hover hover:text-crema">
            ×
          </button>
          <div className="absolute inset-x-0 bottom-0 h-1 bg-superficie/10">
            <i key={pendiente.clave} className="aviso-barra block h-full bg-amarillo" style={{ animationDuration: `${MS_DESHACER}ms` }} />
          </div>
```

(La barra respeta «reducir movimiento»: la regla de `globals.css` quita la animación y el temporizador de JS sigue cerrando el aviso a los 8 s.)
4. En el comentario del aviso: «… con la barra que se consume en los 8 s y la × para cerrarlo sin deshacer (pieza 3 de la 0.9.22).»

- [ ] **Step 4: Ver que pasa** — `npx vitest run src/shared/ui src/modules/taller/asignaciones`, `npx tsc -b`, `npm run lint` → verde. Después `npm run check` entero (apunta el total; un timeout suelto que pase solo, como hasta ahora, se apunta).

- [ ] **Step 5: Commit**

```bash
git add src/shared/ui/AvisoBreve.tsx src/shared/ui/AvisoBreve.test.tsx src/modules/taller/asignaciones/useAccionConDeshacer.tsx src/modules/taller/asignaciones/useAccionConDeshacer.test.tsx && git commit -m "feat: todos los avisos de abajo con la barra que se consume y una x para cerrarlos (0.9.22, pieza 3)"
```

---

### Task 10: E2E en preprod, CHANGELOG y novedades

**Files:**
- Create: `tests/e2e/descartar-nsys.spec.ts` (web)
- Modify: `CHANGELOG.md` (web); `docs/novedades/NOVEDADES-v0.9.22.md` (raíz)

**Interfaces:**
- Consumes: la app de las tasks 1-9 desplegada en preprod (la corre el controlador después del merge, no esta tarea).

- [ ] **Step 1: La E2E**

`tests/e2e/descartar-nsys.spec.ts` (con Write):

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'
import { leerXlsx } from './xlsx.ts'

// descartar-nsys.spec.ts (0.9.22 pieza 3, spec «descartar» §5): con un IMEI INVENTADO que NSYS no conoce (por defecto
// 350000000009923; otro con E2E_IMEI_ALTA), el supertécnico le crea por la API una asignación para que salga en «No está en NSYS»,
// lo descarta con motivo (sale de la lista y de la plantilla de altas, y está en «Ver descartados»), lo deshace, lo vuelve a
// descartar y, con un trabajo nuevo, sale otra vez con el aviso. Solo contra PREPRODUCCIÓN (se niega sin el letrero) y lo deja
// como estaba: sin asignaciones, sin teléfono y sin descarte (las líneas del registro de actividad las borra el guion de preprod).
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const IMEI = process.env.E2E_IMEI_ALTA ?? '350000000009923'
const MOTIVO = 'E2E 0.9.22: no es un móvil'

type Respuesta = { estado: number; cuerpo: unknown }
type Tarjetas = { altas: { imeiSn: string }[]; descartadas: { imeiSn: string; motivo: string }[] }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Una petición a la API con el token de la sesión de la página (patrón de sku-taller-reparacion.spec.ts). */
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

const tarjetasApi = async (page: Page) => (await pedir(page, 'GET', '/api/stock-telefonos/por-pasar/tarjetas')).cuerpo as Tarjetas

/** Una asignación de reparación del propio supertécnico para el IMEI de prueba (crea el teléfono si no existe). */
async function asignar(page: Page) {
  const idTec = await page.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { idTec?: number }).idTec)
  expect(typeof idTec, 'idTec del supertécnico en la sesión').toBe('number')
  const lote = await pedir(page, 'POST', '/api/asignaciones/lote', {
    telefonos: [{ imei: IMEI, modelo: '14', idCli: null, clienteExplicito: false }],
    asignaciones: [{ imei: IMEI, categoria: 'R', idTec, comentario: MOTIVO, esChasis: false }],
  }, { 'Idempotency-Key': `e2e-descartar-${Date.now()}` })
  expect(lote.estado, 'POST /api/asignaciones/lote').toBe(200)
}

/** Fuera lo del IMEI de prueba: sus asignaciones y reparaciones (las R… antes), el teléfono y el descarte. */
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
  const d = await pedir(page, 'DELETE', '/api/stock-telefonos/por-pasar/descarte', { imeiSn: IMEI })
  expect.soft(d.estado, 'limpieza del descarte').toBe(204)
}

async function abrirNoEsta(page: Page) {
  await page.goto('/telefonos/stock')
  await page.getByRole('button', { name: /^Por pasar a NSYS/ }).click()
  await page.getByRole('button', { name: 'No está en NSYS' }).click()
}

test.describe('0.9.22 pieza 3: «descartar» en «No está en NSYS»', () => {
  test.describe.configure({ mode: 'serial' })
  // Una sola sesión: el entorno limita los inicios de sesión por minuto.
  let pagina: Page
  // Solo true tras comprobar el letrero de preprod: sin eso la limpieza no corre.
  let enPreprod = false

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
    enPreprod = true
    expect((await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).estado,
      `el IMEI de prueba ${IMEI} no puede estar en NSYS`).toBe(404)
    await limpiar(pagina)
    await asignar(pagina)
  })

  test.afterAll(async () => {
    try {
      if (pagina && enPreprod) await limpiar(pagina)
    } finally {
      await pagina?.close()
    }
  })

  test('sale en «No está en NSYS» y «descartar» lo quita con su motivo, también de la plantilla', async () => {
    await abrirNoEsta(pagina)
    const tabla = pagina.getByRole('table', { name: 'No está en NSYS' })
    await expect(tabla.getByRole('row').filter({ hasText: IMEI })).toHaveCount(1)
    await pagina.getByRole('button', { name: `Descartar ${IMEI}` }).click()
    const ventana = pagina.getByRole('dialog', { name: 'Descartar de «No está en NSYS»' })
    await expect(ventana.getByRole('button', { name: 'Descartar' })).toBeDisabled()
    await ventana.getByLabel('Motivo (obligatorio)').fill(MOTIVO)
    await ventana.getByRole('button', { name: 'Descartar' }).click()
    await expect(pagina.getByTestId('aviso-breve')).toContainText(`Descartado «${IMEI}»`)
    await expect(tabla.getByRole('row').filter({ hasText: IMEI })).toHaveCount(0)
    const t = await tarjetasApi(pagina)
    expect(t.altas.map((f) => f.imeiSn)).not.toContain(IMEI)
    expect(t.descartadas.find((f) => f.imeiSn === IMEI)?.motivo).toBe(MOTIVO)
    const token = await pagina.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }).token)
    const plantilla = await pagina.request.get('/api/stock-telefonos/por-pasar/plantilla-altas', { headers: { Authorization: `Bearer ${token}` } })
    expect(plantilla.status()).toBe(200)
    const x = leerXlsx(await plantilla.body())
    expect(x.filasConDatos(7, 'A').map((n) => x.celda(`A${n}`).valor), 'la plantilla de altas no lo lleva').not.toContain(IMEI)
  })

  test('«Ver descartados» lo enseña con su motivo y «deshacer» lo devuelve', async () => {
    await abrirNoEsta(pagina)
    await pagina.getByRole('button', { name: /^Ver descartados \(\d+\)$/ }).click()
    const descartados = pagina.getByRole('table', { name: 'Descartados' })
    await expect(descartados.getByRole('row').filter({ hasText: IMEI })).toContainText(`«${MOTIVO}»`)
    await pagina.getByRole('button', { name: `Deshacer descarte de ${IMEI}` }).click()
    await expect(pagina.getByTestId('aviso-breve')).toContainText(`«${IMEI}» vuelve a la lista`)
    await expect(pagina.getByRole('table', { name: 'No está en NSYS' }).getByRole('row').filter({ hasText: IMEI })).toHaveCount(1)
  })

  test('descartado otra vez, un trabajo nuevo lo saca con el aviso de quién lo descartó', async () => {
    const d = await pedir(pagina, 'PUT', '/api/stock-telefonos/por-pasar/descarte', { imeiSn: IMEI, motivo: MOTIVO })
    expect(d.estado, 'PUT descarte').toBe(204)
    // El trabajo nuevo tiene que ser posterior al descarte (las fechas van al segundo): fuera la asignación y el teléfono, y
    // otra asignación nueva (que vuelve a crear el teléfono).
    const r = await pedir(pagina, 'GET', `/api/reparaciones/imei/${IMEI}`)
    for (const { idRep } of r.cuerpo as { idRep: string }[]) {
      expect((await pedir(pagina, 'DELETE', `/api/reparaciones/${idRep}`, { motivo: 'E2E: trabajo nuevo' })).estado).toBe(204)
    }
    expect([204, 404]).toContain((await pedir(pagina, 'DELETE', `/api/telefonos/${IMEI}`)).estado)
    await pagina.waitForTimeout(1_100)
    await asignar(pagina)
    await abrirNoEsta(pagina)
    const fila = pagina.getByRole('table', { name: 'No está en NSYS' }).getByRole('row').filter({ hasText: IMEI })
    await expect(fila).toContainText('Salió otra vez: trabajo nuevo después de que')
    await expect(fila).toContainText(`(«${MOTIVO}»)`)
  })
})
```

- [ ] **Step 2: Comprobar que compila** — `npx tsc -b` y `npm run lint` → verde. **No se corre** aquí (va contra preprod tras el merge; lo hace el controlador).

- [ ] **Step 3: CHANGELOG (web)**

En `CHANGELOG.md`, sección `## [0.9.22] - (pendiente) …`, tras la línea de «compruébalo»:

```markdown
- **«descartar»** en «No está en NSYS» (pieza 3): un enlace discreto junto a «Hecho» quita una fila que no va a NSYS (no es un móvil, es de un cliente…) con un **motivo obligatorio**. No vuelve con las importaciones; solo si el taller le hace un trabajo nuevo (con el aviso de quién lo descartó). **«Ver descartados»** al pie, con «deshacer». Queda en el registro de actividad.
- **Avisos de abajo:** todos los temporales con la **barra que se consume** y una **×** para cerrarlos antes (también el de «Deshacer» de Asignaciones, que no tenía barra).
```

y la línea de las migraciones pasa a: «- Servidor con tres migraciones aditivas: `migracion-nombres-y-coste-nsys.sql` (pieza 1), `migracion-plantillas-nsys.sql` (tabla `Taller_hecho` y «Revisado» en `Taller_aviso`) y `migracion-descartar-nsys.sql` (tabla `Taller_descarte`, pieza 3).»

- [ ] **Step 4: Commit de la web**

```bash
git add tests/e2e/descartar-nsys.spec.ts CHANGELOG.md && git commit -m "test: E2E de descartar en No esta en NSYS y changelog de la 0.9.22 (pieza 3)"
```

- [ ] **Step 5: Novedades (raíz)**

En `/c/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.22.md`, en «📦 Stock de teléfonos», tras la línea de «Hecho»:

```markdown
- **«descartar»** en «No está en NSYS»: si una fila no debería estar (no es un móvil, es de un cliente…), el enlace «descartar»
  la quita con un motivo. Solo vuelve si el taller le hace un trabajo nuevo. «Ver descartados» al pie, con «deshacer».
```

y una sección nueva antes del pie:

```markdown
## 🔔 Avisos

- Los avisos de abajo llevan una **×** para cerrarlos antes y la barra amarilla que se consume (también el de «Deshacer» de
  Asignaciones).
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.22.md && git commit -m "docs: novedades de la 0.9.22 (pieza 3, descartar y avisos con barra y cierre)"
```

---

## Después del plan (fuera de las tareas)

- **Revisión final de la rama** (servidor y web) y resumen; **merge `--no-ff` en `main` y push solo con el OK del usuario** (antes, `git fetch` y comparar con `origin`); gitlinks de la raíz en un commit aparte. La raíz tiene sin subir `0aaf8a9` (spec) y el commit del plan.
- **Preprod** (añadir una «pieza 3» al guion `Apuntes/preprod/v0922-p2-preprod.md`):
  - `migracion-descartar-nsys.sql` (`YA_EXISTEN` 0 → `CREADAS` 1), servidor y web;
  - E2E `descartar-nsys` (con `$env:WEB = "/c/Users/dev/Documents/wt/web-0922-descartar"` y `e2e-0921.sh`), luego `plantillas-nsys`, `sku-taller`, `sku-taller-reparacion`, `stock-telefonos`, `roles-stock`, `formulario`;
  - limpieza del registro: `DELETE FROM Log_Actividad WHERE ACCION IN ('DESCARTAR_ALTA_NSYS','DESHACER_DESCARTE_NSYS') AND DETALLE = 'IMEI: 350000000009923';` (y comprobar que no queda nada del IMEI de prueba en `Reparacion`, `Telefono` ni `Taller_descarte`);
  - a mano en preprod: descartar «SIN-IMEI-CAM» de verdad no hace falta (la E2E lo cubre); mirar que la × y la barra se ven en un aviso de Asignaciones.
- **Producción:** `Apuntes/prod-v0922-p2.md` pasa a **tres migraciones** (añadir la de la pieza 3 tras la de la pieza 2, `CREADAS` 1, y la tabla 35) y los commits nuevos de servidor y web.
