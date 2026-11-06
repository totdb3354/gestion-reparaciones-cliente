# 0.9.27 — «ignorar» en los vendidos y de baja que salen en ámbar · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** en «Por pasar a NSYS», un enlace «ignorar» en las filas ámbar (vendido o de baja en NSYS con trabajo del taller
después) que saca el móvil de «Cambio de color», «Cambio de grado» y «Revisar a mano» hasta que NSYS lo vuelva a tener en la
empresa o el taller le haga un trabajo nuevo; «Ver ignorados (n)» con «deshacer» al pie de cada tabla.

**Architecture:**
- **Base:** tabla nueva `Taller_vuelta_ignorada` (IMEI, quién, cuándo), con su migración aditiva.
- **Servidor:** `PorPasarNsys.vueltas` ya calcula la vuelta de cada vendido o de baja; ahora la parte en tres: los que salen (en la
  empresa, los que han vuelto sin ignorado y los de ignorado superado, que llevan `ignoradoAntes*`) y los ignorados que valen (no
  salen). Con las mismas funciones de `TarjetasNsys`, aplicadas a los ignorados, se sabe en qué tarjetas saldrían
  (`TarjetasPorPasar.ignorados`). Rutas `PUT`/`DELETE /ignorado` y un repaso tras cada importación que quita los que NSYS vuelve a
  tener en la empresa.
- **Web:** reglas y textos en `porPasar.ts` (puro); `CeldaHecho` y `ListasPorPasar` pintan «ignorar», «Salió otra vez…» y
  «Ver ignorados»; `PorPasarNsys` llama a las rutas.

**Tech Stack:** Spring Boot 3 + JUnit 5 + Mockito (servidor); React 19 + TypeScript, TanStack Query, Vitest + Testing Library +
MSW, Playwright (web).

**Spec:** `docs/superpowers/specs/2026-11-06-v0927-ignorar-vendidos-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-11-06-v0927-prototipo-ignorar.html` (v3).

## Global Constraints

- **Base + servidor + web.** Migración aditiva `sql/migracion-ignorar-vuelta-nsys.sql`. Orden de despliegue: migración →
  servidor → web. Ninguna ruta vieja cambia; la plantilla de color y `GET /por-pasar` no se tocan.
- **Permisos:** SUPERTECNICO y ADMIN (la guarda de `PorPasarController`).
- **Tabla:** `Taller_vuelta_ignorada (IMEI_SN VARCHAR(40) PK, ID_USU INT NULL FK Usuario ON DELETE SET NULL, FECHA DATETIME NOT
  NULL)`; un ignorado por IMEI; ignorar otra vez pone quién y cuándo de ahora.
- **Regla (spec §4.1):** solo en un vendido o de baja que ha vuelto (la regla de la pieza 4 §6 no cambia). Vale si su `FECHA` no
  es anterior a la última asignación (A…, por fecha de asignación y luego id; sin asignaciones, el último trabajo; sin trabajos,
  vale). Vale → no sale en ninguna tarjeta. Superado → sale en ámbar con `ignoradoAntesPor`/`ignoradoAntesEn`. En la empresa → no
  se mira.
- **Textos exactos (web):**
  - enlace `ignorar`; `title` `No ha vuelto: quitarlo de las listas hasta que NSYS lo tenga otra vez en la empresa (o el taller le haga un trabajo nuevo)`;
    `aria-label` `Ignorar <imei>`; clases de «descartar»;
  - aviso al ignorar: `Ignorado «<imei>»: sale en «Ver ignorados» hasta que vuelva`;
  - pie: `Ver ignorados (n)` / `Ocultar ignorados`; tabla `aria-label` `Ignorados`, columnas `IMEI`, `Modelo`, `En NSYS`,
    `Ignorado`, `` (vacía);
  - En NSYS: `Vendido en NSYS el dd/mm/aaaa` o `De baja en NSYS desde el dd/mm/aaaa`; Ignorado: `<quién> · dd/mm/aaaa hh:mm`
    (sin nombre, `—`);
  - deshacer: texto `deshacer`, `aria-label` `Deshacer ignorar <imei>`; aviso `«<imei>» vuelve a las listas`;
  - superado: `Salió otra vez: trabajo nuevo después de que <quién> lo ignorara el dd/mm/aaaa hh:mm` (ámbar, negrita);
  - ayuda: al final de `AYUDA.color`, `AYUDA.grado` y `AYUDA.revisar`, tal cual:
    ` Un vendido o de baja que sale en ámbar tiene trabajo del taller después de la venta o de la baja: si es una devolución, apúntala en NSYS; si no ha vuelto, «ignorar» lo quita de las tres listas hasta que NSYS lo tenga otra vez en la empresa o el taller le haga un trabajo nuevo.`
- **Textos exactos (servidor):** registro de actividad `IGNORAR_VUELTA_NSYS` y `DESHACER_IGNORAR_VUELTA_NSYS`, detalle
  `IMEI: <imei>`; 400 `Falta el IMEI` y `Un IMEI no puede pasar de 40 caracteres`; log
  `NSYS: ignorados de las listas quitados: {} (otra vez en la empresa)`.
- **Fronteras de la web:** un módulo no importa de otro (`no-restricted-imports`): todo va en `src/modules/telefonos/` o en
  `src/shared`.
- **Repos públicos:** datos inventados (IMEIs `3500000000…`, «Supertécnico 1»); ni el IMEI real del caso del 06/11, ni URLs ni IPs.
- **Commits** en castellano **sin tildes** y **sin** línea `Co-Authored-By`. **Nunca** push, merge ni tags sin el OK del usuario.
- **Otras sesiones trabajan a la vez:** no se tocan los clones principales ni otros worktrees `wt/…` (en especial
  `wt/servidor-0926-pedidos` y `wt/web-0926-pedidos`). En la raíz hay ficheros sin seguimiento que no son de nadie
  (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Versión 0.9.27**; los pedidos pasan a la 0.9.28 (sus novedades de la raíz se renombran en la Task 6).
- **Toolchain:** en cada Bash del servidor, antes de `mvn`:
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.

**Dónde se trabaja** (Task 0):
- servidor: worktree `/c/Users/dev/Documents/wt/servidor-0927-ignorar`, rama `dev_v0927_ignorar_vendidos`, desde `origin/main`
  del servidor (06/11: `437b1d0`, 0.9.26);
- web: worktree `/c/Users/dev/Documents/wt/web-0927-ignorar`, rama `dev_v0927_ignorar_vendidos`, desde `origin/main` de la web
  (06/11: `eacda2f`, 0.9.26);
- raíz (spec, plan, novedades): `/c/Users/dev/Documents/ProgramaReparaciones`, en `main`, sin push.

En el servidor, `S=/c/Users/dev/Documents/wt/servidor-0927-ignorar` y el paquete
`$S/src/main/java/com/reparaciones/servidor/nsys/` (tests en `$S/src/test/java/com/reparaciones/servidor/nsys/`). En la web,
`W=/c/Users/dev/Documents/wt/web-0927-ignorar`.

---

### Task 0: Worktrees

- [ ] **Step 1: Crear los dos worktrees desde `origin/main`**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q && git worktree add -b dev_v0927_ignorar_vendidos /c/Users/dev/Documents/wt/servidor-0927-ignorar origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q && git worktree add -b dev_v0927_ignorar_vendidos /c/Users/dev/Documents/wt/web-0927-ignorar origin/main
cd /c/Users/dev/Documents/wt/web-0927-ignorar && npm ci
```

Expected: los dos worktrees en `0.9.26` (`git log --oneline -1`), `npm ci` sin errores.

---

### Task 1: La tabla y su DAO (servidor)

**Files:**
- Create: `$S/sql/migracion-ignorar-vuelta-nsys.sql`
- Modify: `$S/sql/crear_bd.sql` (línea 15 y tras el `CREATE TABLE Taller_descarte`), `$S/sql/reset-uat.sql` (tras la línea 27)
- Create: `…/nsys/VueltaIgnorada.java`, `…/nsys/VueltaIgnoradaDAO.java`
- Test: `…/nsys/DdlIgnoradoTest.java`, `…/nsys/VueltaIgnoradaDAOTest.java`

**Interfaces:**
- Produces: `record VueltaIgnorada(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha)`;
  `VueltaIgnoradaDAO`: `List<VueltaIgnorada> todos()`, `void ignorar(String imeiSn, int idUsu, LocalDateTime ahora)`,
  `int deshacer(String imeiSn)`, `void quitar(List<String> imeis)`.

- [ ] **Step 1: Tests que fallan**

`DdlIgnoradoTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La base de la 0.9.27 (spec «ignorar» §3): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen lo mismo de
 *  Taller_vuelta_ignorada. Se comparan con los espacios juntados (crear_bd.sql alinea las columnas). */
class DdlIgnoradoTest {

    private static final String MIGRACION = "migracion-ignorar-vuelta-nsys.sql";

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String junto(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    private static String crear(String sql, String tabla) {
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla);
        return junto(sql.substring(desde, sql.indexOf(");", desde) + 2));
    }

    @Test void laTablaDeLosIgnoradosEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_vuelta_ignorada");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_vuelta_ignorada"));
        assertTrue(migracion.contains("IMEI_SN VARCHAR(40) NOT NULL"), migracion);
        assertTrue(migracion.contains("FECHA DATETIME NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN)"), migracion);
        assertTrue(migracion.contains("FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL"), migracion);
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLosIgnoradosYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_vuelta_ignorada;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_vuelta_ignorada;"));
    }
}
```

`VueltaIgnoradaDAOTest.java`:

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Taller_vuelta_ignorada con el JdbcTemplate mockeado, sin base de datos (como DescarteDAOTest). */
class VueltaIgnoradaDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 6, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VueltaIgnoradaDAO dao = new VueltaIgnoradaDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losIgnoradosConQuienPorSuNombre() {
        assertTrue(dao.todos().isEmpty());
        verify(jdbc).query(eq(VueltaIgnoradaDAO.SQL_TODOS), any(RowMapper.class));
        assertEquals("SELECT i.IMEI_SN, i.ID_USU, u.NOMBRE_USUARIO, i.FECHA FROM Taller_vuelta_ignorada i"
                + " LEFT JOIN Usuario u ON u.ID_USU = i.ID_USU", VueltaIgnoradaDAO.SQL_TODOS);
    }

    @Test void ignorarOtraVezPoneQuienYCuandoDeAhora() {
        dao.ignorar("350000000000123", 7, AHORA);
        verify(jdbc).update(VueltaIgnoradaDAO.SQL_IGNORAR, "350000000000123", 7, Timestamp.valueOf(AHORA));
        assertEquals("INSERT INTO Taller_vuelta_ignorada (IMEI_SN, ID_USU, FECHA) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA)", VueltaIgnoradaDAO.SQL_IGNORAR);
    }

    @Test void deshacerDiceCuantosQuito() {
        when(jdbc.update(VueltaIgnoradaDAO.SQL_QUITAR, "350000000000123")).thenReturn(1);
        assertEquals(1, dao.deshacer("350000000000123"));
        assertEquals(0, dao.deshacer("350000000000131"));
        assertEquals("DELETE FROM Taller_vuelta_ignorada WHERE IMEI_SN = ?", VueltaIgnoradaDAO.SQL_QUITAR);
    }

    @SuppressWarnings("unchecked")
    @Test void quitarEsUnLoteYSinNadaNoEscribe() {
        dao.quitar(List.of("350000000000123", "350000000000149"));
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(VueltaIgnoradaDAO.SQL_QUITAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000123"}, lote.getValue().get(0));
        clearInvocations(jdbc);
        dao.quitar(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerVueltaIgnorada() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("350000000000123");
        when(rs.getInt("ID_USU")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn(null);
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        assertEquals(new VueltaIgnorada("350000000000123", null, null, AHORA), VueltaIgnoradaDAO.ignorado(rs, 0));
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $S && mvn -q test -Dtest='DdlIgnoradoTest,VueltaIgnoradaDAOTest' 2>&1 | tail -15`
Expected: falla la compilación (`VueltaIgnoradaDAO` no existe) / fichero de migración no encontrado.

- [ ] **Step 3: La migración**

`$S/sql/migracion-ignorar-vuelta-nsys.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-ignorar-vuelta-nsys.sql — 0.9.27, «ignorar» en los vendidos y de baja que salen en ámbar
-- (spec docs/superpowers/specs/2026-11-06-v0927-ignorar-vendidos-design.md §3, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor de la 0.9.27.
-- Aditiva: el servidor anterior no lee la tabla.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_vuelta_ignorada.
-- 3) Comprobación final: CREADAS debe dar 1.
-- Vuelta atrás: el servidor anterior funciona con la tabla puesta; no hace falta quitarla.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_vuelta_ignorada';

-- «ignorar» (spec §3): un vendido o de baja de NSYS que salía en ámbar en «Por pasar a NSYS» y no ha vuelto, con quién y cuándo.
-- Vale mientras el taller no le haga un trabajo nuevo; se borra sola cuando NSYS lo vuelve a tener en la empresa.
CREATE TABLE Taller_vuelta_ignorada (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del espejo, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se ignoró (hora de Madrid)',
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_vuelta_ignorada_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobación final (no modifica nada): debe dar 1; si no, NO desplegar el servidor de la 0.9.27 (lee la tabla).
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_vuelta_ignorada';
```

- [ ] **Step 4: `crear_bd.sql` y `reset-uat.sql`**

En `crear_bd.sql`, debajo de la línea 15 (`DROP TABLE IF EXISTS Taller_descarte;`), añadir
`DROP TABLE IF EXISTS Taller_vuelta_ignorada;`. Justo después del `CREATE TABLE Taller_descarte (...);` (línea ~616), añadir el
mismo bloque de la migración, precedido del mismo comentario:

```sql

-- «ignorar» (0.9.27, spec §3): un vendido o de baja de NSYS que salía en ámbar en «Por pasar a NSYS» y no ha vuelto, con quién
-- y cuándo. Vale mientras el taller no le haga un trabajo nuevo; se borra sola cuando NSYS lo vuelve a tener en la empresa.
CREATE TABLE Taller_vuelta_ignorada (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del espejo, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se ignoró (hora de Madrid)',
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_vuelta_ignorada_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

En `reset-uat.sql`, debajo de `DELETE FROM Taller_descarte;`, añadir `DELETE FROM Taller_vuelta_ignorada;`.

- [ ] **Step 5: El record y el DAO**

`VueltaIgnorada.java`:

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_vuelta_ignorada (spec 0.9.27 §3): un vendido o de baja que salía en ámbar y no ha vuelto, quién
 *  ({@code usuario}: su nombre, null si ya no existe) y cuándo lo ignoró (hora de Madrid). */
public record VueltaIgnorada(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha) {}
```

`VueltaIgnoradaDAO.java`:

```java
package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Acceso a Taller_vuelta_ignorada (spec 0.9.27 §3): los vendidos o de baja ignorados en «Por pasar a NSYS», uno por IMEI. Son
 *  unos pocos. */
@Repository
public class VueltaIgnoradaDAO {

    static final String SQL_TODOS = "SELECT i.IMEI_SN, i.ID_USU, u.NOMBRE_USUARIO, i.FECHA FROM Taller_vuelta_ignorada i"
            + " LEFT JOIN Usuario u ON u.ID_USU = i.ID_USU";
    /** Ignorar otra vez el mismo IMEI pone quién y cuándo de ahora. */
    static final String SQL_IGNORAR = "INSERT INTO Taller_vuelta_ignorada (IMEI_SN, ID_USU, FECHA) VALUES (?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA)";
    static final String SQL_QUITAR = "DELETE FROM Taller_vuelta_ignorada WHERE IMEI_SN = ?";

    private final JdbcTemplate jdbc;

    public VueltaIgnoradaDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VueltaIgnorada> todos() {
        return jdbc.query(SQL_TODOS, VueltaIgnoradaDAO::ignorado);
    }

    public void ignorar(String imeiSn, int idUsu, LocalDateTime ahora) {
        jdbc.update(SQL_IGNORAR, imeiSn, idUsu, Timestamp.valueOf(ahora));
    }

    /** «deshacer» de «Ver ignorados»: cuántos quitó (0 si no estaba ignorado). */
    public int deshacer(String imeiSn) {
        return jdbc.update(SQL_QUITAR, imeiSn);
    }

    /** El repaso tras una importación: fuera los de estos IMEI (otra vez en la empresa), en un lote. Sin ninguno no escribe. */
    public void quitar(List<String> imeis) {
        if (imeis.isEmpty()) return;
        jdbc.batchUpdate(SQL_QUITAR, imeis.stream().map(i -> new Object[]{i}).toList());
    }

    static VueltaIgnorada ignorado(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        return new VueltaIgnorada(rs.getString("IMEI_SN"), usuario, rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime());
    }
}
```

- [ ] **Step 6: Comprobar que pasan**

Run: `cd $S && mvn -q test -Dtest='DdlIgnoradoTest,VueltaIgnoradaDAOTest,DdlDescarteTest' 2>&1 | tail -15`
Expected: `Tests run: …, Failures: 0, Errors: 0` y BUILD SUCCESS (sin salida con `-q` si todo va bien).

- [ ] **Step 7: Commit**

```bash
cd $S && git add sql/migracion-ignorar-vuelta-nsys.sql sql/crear_bd.sql sql/reset-uat.sql src/main/java/com/reparaciones/servidor/nsys/VueltaIgnorada.java src/main/java/com/reparaciones/servidor/nsys/VueltaIgnoradaDAO.java src/test/java/com/reparaciones/servidor/nsys/DdlIgnoradoTest.java src/test/java/com/reparaciones/servidor/nsys/VueltaIgnoradaDAOTest.java
git commit -m "feat: tabla Taller_vuelta_ignorada y su DAO para ignorar los vendidos que salen en ambar (0.9.27)"
```

---

### Task 2: La regla y las tarjetas sin los ignorados (servidor)

**Files:**
- Modify: `…/nsys/VueltaNsys.java`, `…/nsys/FilaColorNsys.java`, `…/nsys/FilaGradoNsys.java`, `…/nsys/FilaRevisarNsys.java`,
  `…/nsys/FilaSinGradoNsys.java`, `…/nsys/TarjetasPorPasar.java`, `…/nsys/TarjetasNsys.java`, `…/nsys/PorPasarNsys.java`
- Create: `…/nsys/FilaIgnoradaNsys.java`
- Test: `…/nsys/VueltaNsysTest.java`, `…/nsys/TarjetasNsysTest.java`, `…/nsys/PorPasarNsysTest.java`,
  `$S/src/test/java/com/reparaciones/servidor/controller/RolesStockTelefonosTest.java` (solo el `@MockBean` nuevo)

**Interfaces:**
- Consumes: `VueltaIgnorada`, `VueltaIgnoradaDAO.todos()` (Task 1).
- Produces:
  - `VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde, String ignoradoAntesPor, LocalDateTime ignoradoAntesEn)`
    (sigue el constructor de 3); `VueltaNsys conIgnoradoAntes(VueltaIgnorada i)`;
    `static boolean vale(VueltaIgnorada ignorado, Collection<NsysDAO.TrabajoFecha> trabajos)`.
  - `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys`, `FilaSinGradoNsys`: dos campos al final,
    `@Schema(nullable = true) String ignoradoAntesPor`, `@Schema(nullable = true) LocalDateTime ignoradoAntesEn`.
  - `record FilaIgnoradaNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, boolean esEsim,
    LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde, String ignoradoPor, LocalDateTime ignoradoEn,
    List<String> tarjetas)`.
  - `TarjetasPorPasar(..., List<FilaSinGradoNsys> sinGrado, List<FilaIgnoradaNsys> ignorados)`.
  - `PorPasarNsys`: el constructor gana `VueltaIgnoradaDAO ignoradoDao` detrás de `DescarteDAO descarteDao` (en los dos).

- [ ] **Step 1: Tests de la regla (`VueltaNsysTest`)**

Añadir al final de la clase (importar `java.time.LocalDateTime` ya está; añadir `import static org.junit.jupiter.api.Assertions.*;`
si solo está `assertEquals`):

```java
    // ---------------------------------------------------------------- 0.9.27: cuándo vale un ignorado (spec §4.1)

    private static VueltaIgnorada ignorado(LocalDateTime en) {
        return new VueltaIgnorada("350000000000123", 7, "Supertécnico 1", en);
    }

    private static NsysDAO.TrabajoFecha trabajo(String id, LocalDateTime asignado, LocalDateTime terminado) {
        return new NsysDAO.TrabajoFecha("350000000000123", id, asignado, terminado);
    }

    @Test void unIgnoradoValeSiNoEsAnteriorALaUltimaAsignacion() {
        LocalDateTime asignada = LocalDateTime.of(2026, 10, 28, 9, 10);
        List<NsysDAO.TrabajoFecha> trabajos = List.of(trabajo("A20261028_5", asignada, LocalDateTime.of(2026, 10, 28, 12, 25)));
        assertTrue(VueltaNsys.vale(ignorado(LocalDateTime.of(2026, 11, 6, 12, 31)), trabajos));
        assertTrue(VueltaNsys.vale(ignorado(asignada), trabajos), "a la misma hora vale");
        assertFalse(VueltaNsys.vale(ignorado(LocalDateTime.of(2026, 10, 28, 9, 0)), trabajos));
    }

    @Test void laFilaDelCierreDeUnaAsignacionNoSuperaElIgnorado() {
        LocalDateTime ignoradoEn = LocalDateTime.of(2026, 11, 6, 12, 31);
        List<NsysDAO.TrabajoFecha> trabajos = List.of(
                trabajo("A20261106_3", LocalDateTime.of(2026, 11, 6, 11, 0), null),
                trabajo("R20261106_9", LocalDateTime.of(2026, 11, 6, 13, 40), null));
        assertTrue(VueltaNsys.vale(ignorado(ignoradoEn), trabajos));
    }

    @Test void unaAsignacionPosteriorLoSupera() {
        List<NsysDAO.TrabajoFecha> trabajos = List.of(
                trabajo("A20261028_5", LocalDateTime.of(2026, 10, 28, 9, 10), LocalDateTime.of(2026, 10, 28, 12, 25)),
                trabajo("A20261107_2", LocalDateTime.of(2026, 11, 7, 10, 0), null));
        assertFalse(VueltaNsys.vale(ignorado(LocalDateTime.of(2026, 11, 6, 12, 31)), trabajos));
    }

    @Test void sinAsignacionesMiraElUltimoTrabajoYSinTrabajosVale() {
        LocalDateTime ignoradoEn = LocalDateTime.of(2026, 11, 6, 12, 31);
        assertTrue(VueltaNsys.vale(ignorado(ignoradoEn), List.of()));
        assertTrue(VueltaNsys.vale(ignorado(ignoradoEn), List.of(trabajo("R20261105_1", LocalDateTime.of(2026, 11, 5, 9, 0), null))));
        assertFalse(VueltaNsys.vale(ignorado(ignoradoEn), List.of(trabajo("R20261107_1", LocalDateTime.of(2026, 11, 7, 9, 0), null))));
    }

    @Test void conIgnoradoAntesGuardaQuienYCuandoYLoDemasIgual() {
        VueltaNsys v = new VueltaNsys(LocalDate.of(2026, 10, 26), null, LocalDateTime.of(2026, 10, 28, 9, 10));
        VueltaNsys con = v.conIgnoradoAntes(ignorado(LocalDateTime.of(2026, 11, 6, 12, 31)));
        assertEquals(new VueltaNsys(LocalDate.of(2026, 10, 26), null, LocalDateTime.of(2026, 10, 28, 9, 10), "Supertécnico 1",
                LocalDateTime.of(2026, 11, 6, 12, 31)), con);
        assertNull(v.ignoradoAntesEn());
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $S && mvn -q test -Dtest=VueltaNsysTest 2>&1 | tail -15`
Expected: error de compilación (`vale`, `conIgnoradoAntes` y el constructor de 5 no existen).

- [ ] **Step 3: `VueltaNsys`**

Cambiar la cabecera del record y añadir lo nuevo (el resto del fichero no cambia; añadir `import java.util.Collection;` ya
existe):

```java
public record VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde, String ignoradoAntesPor,
                         LocalDateTime ignoradoAntesEn) {

    /** Un móvil en la empresa: sale tal cual. */
    public static final VueltaNsys EN_EMPRESA = new VueltaNsys(null, null, null);

    /** Sin ignorado superado (spec 0.9.27 §4.1). */
    public VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde) {
        this(vendidoEn, bajaEn, desde, null, null);
    }

    /** La misma vuelta con un ignorado superado por un trabajo nuevo: quién y cuándo (para «Salió otra vez…»). */
    VueltaNsys conIgnoradoAntes(VueltaIgnorada i) {
        return new VueltaNsys(vendidoEn, bajaEn, desde, i.usuario(), i.fecha());
    }

    private static final Comparator<NsysDAO.TrabajoFecha> POR_ASIGNACION = Comparator.comparing(NsysDAO.TrabajoFecha::asignado,
            Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(NsysDAO.TrabajoFecha::idRep, Comparator.nullsFirst(Comparator.naturalOrder()));

    /** Si un ignorado vale (spec 0.9.27 §4.1): su fecha no es anterior a la última asignación de los trabajos del móvil (A…/AG…/AP…,
     *  por fecha de asignación y luego por id; sin asignaciones, el último trabajo; sin trabajos, vale). Como los descartes
     *  ({@code AltaTaller.ultimoEn}): la R/G/P que se crea al terminar una asignación lleva la hora del cierre y no es un trabajo
     *  nuevo. */
    static boolean vale(VueltaIgnorada ignorado, Collection<NsysDAO.TrabajoFecha> trabajos) {
        Optional<NsysDAO.TrabajoFecha> ultima = trabajos.stream().filter(NsysDAO.TrabajoFecha::esAsignacion).max(POR_ASIGNACION)
                .or(() -> trabajos.stream().max(POR_ASIGNACION));
        LocalDateTime en = ultima.map(NsysDAO.TrabajoFecha::asignado).orElse(null);
        return en == null || !ignorado.fecha().isBefore(en);
    }
```

(Quitar la línea antigua `public static final VueltaNsys EN_EMPRESA = new VueltaNsys(null, null, null);` que ya había, para no
duplicarla; el `de(...)` y lo demás quedan igual. Añadir a la Javadoc de la clase: «Desde la 0.9.27, {@code ignoradoAntesPor} e
{@code ignoradoAntesEn}: el ignorado superado por un trabajo nuevo, si lo hay».)

- [ ] **Step 4: Comprobar que pasa**

Run: `cd $S && mvn -q test -Dtest=VueltaNsysTest 2>&1 | tail -15`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Las filas ganan `ignoradoAntesPor` e `ignoradoAntesEn`**

En los cuatro records se añaden al final de la cabecera:
`, @Schema(nullable = true) String ignoradoAntesPor, @Schema(nullable = true) LocalDateTime ignoradoAntesEn`, se añade a su
Javadoc «Desde la 0.9.27, {@code ignoradoAntesPor} e {@code ignoradoAntesEn}: quién y cuándo lo ignoraron, si un trabajo nuevo
lo superó (null si no).» y se conservan los constructores de antes para los tests:

- `FilaColorNsys`: el constructor corto (16 parámetros) termina en `..., null, null, null, false, null, null, null);`. Añadir el
  de 21 (el canónico de la 0.9.26):

```java
    /** Sin ignorado superado (como hasta la 0.9.26). */
    public FilaColorNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String colorDe, String colorA,
                         String productoNsys, String gradoNsys, String ponerProducto, boolean compruebalo, boolean tambienGrado,
                         List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor, LocalDateTime hechoEn,
                         LocalDateTime hechoVolvio, LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde,
                         boolean esEsim, String caja) {
        this(imeiSn, modelo, modeloMaestro, capacidad, colorDe, colorA, productoNsys, gradoNsys, ponerProducto, compruebalo,
                tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, vendidoEn, bajaEn, vueltoDesde, esEsim, caja, null, null);
    }
```

- `FilaGradoNsys`: el corto termina en `..., hechoVolvio, null, null, null, false, null, null);`. Añadir el de 17:

```java
    /** Sin ignorado superado (como hasta la 0.9.26). */
    public FilaGradoNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String gradoDe, String gradoA,
                         String productoNsys, boolean conColor, List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor,
                         LocalDateTime hechoEn, LocalDateTime hechoVolvio, LocalDate vendidoEn, LocalDate bajaEn,
                         LocalDateTime vueltoDesde, boolean esEsim) {
        this(imeiSn, modelo, modeloMaestro, capacidad, gradoDe, gradoA, productoNsys, conColor, porQue, desde, hechoPor, hechoEn,
                hechoVolvio, vendidoEn, bajaEn, vueltoDesde, esEsim, null, null);
    }
```

- `FilaRevisarNsys`: el corto termina en `..., fecha, null, null, null, false, null, null);`. Añadir el de 18:

```java
    /** Sin ignorado superado (como hasta la 0.9.26). */
    public FilaRevisarNsys(int idAviso, String imeiSn, String modelo, String modeloMaestro, String capacidad, String tipo,
                           String causa, String dato, String de, String a, String motivo, String idRep, String usuario,
                           LocalDateTime fecha, LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde, boolean esEsim) {
        this(idAviso, imeiSn, modelo, modeloMaestro, capacidad, tipo, causa, dato, de, a, motivo, idRep, usuario, fecha, vendidoEn,
                bajaEn, vueltoDesde, esEsim, null, null);
    }
```

- `FilaSinGradoNsys` (no tenía constructores): de `{}` pasa a

```java
                               @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim,
                               @Schema(nullable = true) String ignoradoAntesPor,
                               @Schema(nullable = true) LocalDateTime ignoradoAntesEn) {

    /** Sin ignorado superado (como hasta la 0.9.26). */
    public FilaSinGradoNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String gradoNsys,
                            String ultimoTrabajo, LocalDateTime ultimoTrabajoEn, String categoria, String bateriaIdRep,
                            Boolean bateriaNueva, LocalDateTime bateriaEn, LocalDate vendidoEn, LocalDate bajaEn,
                            LocalDateTime vueltoDesde, boolean esEsim) {
        this(imeiSn, modelo, modeloMaestro, capacidad, gradoNsys, ultimoTrabajo, ultimoTrabajoEn, categoria, bateriaIdRep,
                bateriaNueva, bateriaEn, vendidoEn, bajaEn, vueltoDesde, esEsim, null, null);
    }
}
```

- [ ] **Step 6: `FilaIgnoradaNsys` y `TarjetasPorPasar`**

`FilaIgnoradaNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Un móvil ignorado de «Por pasar a NSYS» (spec 0.9.27 §4.2), para «Ver ignorados»: lo de su fila para su descripción (como las
 *  demás filas), su vuelta (el día de la venta o de la baja y desde cuándo lo tiene el taller), quién ({@code ignoradoPor}: su
 *  nombre, null si ya no existe) y cuándo lo ignoró (hora de Madrid), y en qué tarjetas saldría sin el ignorado
 *  ({@code tarjetas}: COLOR, GRADO y REVISAR, en ese orden). */
public record FilaIgnoradaNsys(String imeiSn, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                               @Schema(nullable = true) String capacidad, boolean esEsim, @Schema(nullable = true) LocalDate vendidoEn,
                               @Schema(nullable = true) LocalDate bajaEn, LocalDateTime vueltoDesde,
                               @Schema(nullable = true) String ignoradoPor, LocalDateTime ignoradoEn, List<String> tarjetas) {

    /** La misma fila con estas tarjetas. */
    FilaIgnoradaNsys conTarjetas(List<String> otras) {
        return new FilaIgnoradaNsys(imeiSn, modelo, modeloMaestro, capacidad, esEsim, vendidoEn, bajaEn, vueltoDesde, ignoradoPor,
                ignoradoEn, List.copyOf(otras));
    }
}
```

`TarjetasPorPasar.java` (completo):

```java
package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo, las filas «Sin grado en NSYS» de
 *  «Revisar a mano» (pieza 4 §4.1), del trabajo más antiguo al más reciente, y los ignorados de Color, Grado y Revisar (0.9.27
 *  §4.2), del ignorado más reciente al más antiguo. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas,
                               List<FilaSinGradoNsys> sinGrado, List<FilaIgnoradaNsys> ignorados) {

    /** Sin ignorados. */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas, List<FilaSinGradoNsys> sinGrado) {
        this(color, grado, altas, revisar, descartadas, sinGrado, List.of());
    }

    /** Sin filas «Sin grado en NSYS» ni ignorados. */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {
        this(color, grado, altas, revisar, descartadas, List.of(), List.of());
    }
}
```

- [ ] **Step 7: `TarjetasNsys`: las filas llevan el ignorado superado, y la lista de ignorados**

En `TarjetasNsys.color`, `grado`, `revisar` y `sinGrado`, el `new Fila…(…)` termina con `, v.ignoradoAntesPor(), v.ignoradoAntesEn())`
(en `color`: `…, m.esEsim(), caja(m), v.ignoradoAntesPor(), v.ignoradoAntesEn())`; en `grado` y `revisar`: `…, m.esEsim(),
v.ignoradoAntesPor(), v.ignoradoAntesEn())` — en `revisar` es `m != null && m.esEsim(), …`; en `sinGrado`: `…, v.desde(),
m.esEsim(), v.ignoradoAntesPor(), v.ignoradoAntesEn())`).

Añadir detrás de `descartadas(...)`:

```java
    // ---------------------------------------------------------------- 0.9.27: los ignorados (spec «ignorar» §4.2)

    /** Las tarjetas que pueden tener ignorados, en el orden de {@link FilaIgnoradaNsys#tarjetas()}. */
    static final List<String> TARJETAS_IGNORADO = List.of("COLOR", "GRADO", "REVISAR");

    /** Los ignorados por {@link NsysDAO#claveCruce} de su IMEI. */
    static Map<String, VueltaIgnorada> ignoradosPorClave(List<VueltaIgnorada> ignorados) {
        Map<String, VueltaIgnorada> mapa = new HashMap<>();
        for (VueltaIgnorada i : ignorados) mapa.put(NsysDAO.claveCruce(i.imeiSn()), i);
        return mapa;
    }

    /** «Ver ignorados» (spec 0.9.27 §4.2): se le dan las filas que saldrían de los móviles ignorados (las mismas reglas de cada
     *  tarjeta, con su vuelta) y junta una por móvil con las tarjetas donde saldría. {@code ignorados}, por clave de cruce, solo se
     *  lee si llega alguna fila. */
    static final class Ignorados {
        private final Supplier<Map<String, VueltaIgnorada>> ignorados;
        private final Map<String, FilaIgnoradaNsys> filas = new LinkedHashMap<>();

        Ignorados(Supplier<Map<String, VueltaIgnorada>> ignorados) {
            this.ignorados = ignorados;
        }

        void color(List<FilaColorNsys> fs) {
            for (FilaColorNsys f : fs)
                anotar("COLOR", f.imeiSn(), f.modelo(), f.modeloMaestro(), f.capacidad(), f.esEsim(), f.vendidoEn(), f.bajaEn(), f.vueltoDesde());
        }

        void grado(List<FilaGradoNsys> fs) {
            for (FilaGradoNsys f : fs)
                anotar("GRADO", f.imeiSn(), f.modelo(), f.modeloMaestro(), f.capacidad(), f.esEsim(), f.vendidoEn(), f.bajaEn(), f.vueltoDesde());
        }

        void revisar(List<FilaRevisarNsys> fs) {
            for (FilaRevisarNsys f : fs)
                anotar("REVISAR", f.imeiSn(), f.modelo(), f.modeloMaestro(), f.capacidad(), f.esEsim(), f.vendidoEn(), f.bajaEn(), f.vueltoDesde());
        }

        void sinGrado(List<FilaSinGradoNsys> fs) {
            for (FilaSinGradoNsys f : fs)
                anotar("REVISAR", f.imeiSn(), f.modelo(), f.modeloMaestro(), f.capacidad(), f.esEsim(), f.vendidoEn(), f.bajaEn(), f.vueltoDesde());
        }

        private void anotar(String tarjeta, String imeiSn, String modelo, String modeloMaestro, String capacidad, boolean esEsim,
                            LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde) {
            String clave = NsysDAO.claveCruce(imeiSn);
            VueltaIgnorada i = ignorados.get().get(clave);
            if (i == null) return;
            FilaIgnoradaNsys antes = filas.get(clave);
            List<String> tarjetas = new ArrayList<>(antes == null ? List.of() : antes.tarjetas());
            if (!tarjetas.contains(tarjeta)) tarjetas.add(tarjeta);
            tarjetas.sort(Comparator.comparingInt(TARJETAS_IGNORADO::indexOf));
            filas.put(clave, antes != null ? antes.conTarjetas(tarjetas)
                    : new FilaIgnoradaNsys(imeiSn, modelo, modeloMaestro, capacidad, esEsim, vendidoEn, bajaEn, vueltoDesde,
                    i.usuario(), i.fecha(), List.copyOf(tarjetas)));
        }

        /** Del ignorado más reciente al más antiguo, y por IMEI. */
        List<FilaIgnoradaNsys> filas() {
            return filas.values().stream().sorted(Comparator.comparing(FilaIgnoradaNsys::ignoradoEn, Comparator.reverseOrder())
                    .thenComparing(FilaIgnoradaNsys::imeiSn)).toList();
        }
    }
```

(Imports de `TarjetasNsys`: añadir `java.time.LocalDate`, `java.util.LinkedHashMap` y `java.util.function.Supplier` si faltan.)

Test en `TarjetasNsysTest` (al final):

```java
    // ---------------------------------------------------------------- 0.9.27: «Ver ignorados»

    @Test void losIgnoradosJuntanUnaFilaPorMovilConSusTarjetasEnOrden() {
        LocalDateTime antes = LocalDateTime.of(2026, 11, 5, 9, 0);
        LocalDateTime despues = LocalDateTime.of(2026, 11, 6, 12, 31);
        Map<String, VueltaIgnorada> ignorados = Map.of(
                "350000000000123", new VueltaIgnorada("350000000000123", 7, "Supertécnico 1", despues),
                "350000000000149", new VueltaIgnorada("350000000000149", 7, null, antes));
        int[] lecturas = {0};
        TarjetasNsys.Ignorados ig = new TarjetasNsys.Ignorados(() -> { lecturas[0]++; return ignorados; });
        assertTrue(ig.filas().isEmpty());
        assertEquals(0, lecturas[0], "sin filas no se leen los ignorados");
        FilaRevisarNsys revisar = new FilaRevisarNsys(1, "350000000000123", "15", "15", "256GB", "SIN_COLOR", null, "COLOR", null,
                null, null, "R20261028_82", "Técnico 2", despues.minusDays(9), LocalDate.of(2026, 10, 26), null,
                LocalDateTime.of(2026, 10, 28, 9, 10), true);
        FilaGradoNsys grado = new FilaGradoNsys("350000000000123", "15", "15", "256GB", "n/a", "A100", "Apple iPhone 15 eSIM 256GB Pink",
                true, List.of(), despues.minusDays(9), null, null, null, LocalDate.of(2026, 10, 26), null,
                LocalDateTime.of(2026, 10, 28, 9, 10), true);
        FilaGradoNsys baja = new FilaGradoNsys("350000000000149", "12", "12", "64GB", "B", "A", "Apple iPhone 12 64GB Black",
                false, List.of(), antes.minusDays(16), null, null, null, null, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 20, 10, 2), false);
        ig.revisar(List.of(revisar));
        ig.grado(List.of(grado, baja));
        List<FilaIgnoradaNsys> filas = ig.filas();
        assertEquals(List.of("350000000000123", "350000000000149"), filas.stream().map(FilaIgnoradaNsys::imeiSn).toList());
        assertEquals(List.of("GRADO", "REVISAR"), filas.get(0).tarjetas());
        assertEquals("Supertécnico 1", filas.get(0).ignoradoPor());
        assertEquals(LocalDate.of(2026, 10, 26), filas.get(0).vendidoEn());
        assertTrue(filas.get(0).esEsim());
        assertEquals(List.of("GRADO"), filas.get(1).tarjetas());
        assertNull(filas.get(1).ignoradoPor());
        assertEquals(LocalDate.of(2026, 10, 15), filas.get(1).bajaEn());
    }
```

- [ ] **Step 8: `PorPasarNsys`: las vueltas con los ignorados**

1. Constructor: añadir el campo `private final VueltaIgnoradaDAO ignoradoDao;` y el parámetro `VueltaIgnoradaDAO ignoradoDao` detrás
   de `DescarteDAO descarteDao` en los dos constructores (el `@Autowired` lo pasa al otro); `this.ignoradoDao = ignoradoDao;`.
2. Sustituir `Salen`, `movilesQueSalen()`, `vueltas(...)` y `revisar(...)` por lo siguiente, y `sinGrado()` según el punto 3:

```java
    /** Los ignorados de una petición por clave de cruce, leídos una vez y solo si hace falta (spec 0.9.27 §4.1). */
    private final class Leidos {
        private Map<String, VueltaIgnorada> ignorados;

        Map<String, VueltaIgnorada> ignorados() {
            if (ignorados == null) ignorados = TarjetasNsys.ignoradosPorClave(ignoradoDao.todos());
            return ignorados;
        }
    }

    /** Las vueltas de unos móviles (pieza 4 §6), por clave de cruce: {@code salen} (en la empresa, los que han vuelto sin
     *  ignorado que valga y los de ignorado superado, con quién y cuándo) e {@code ignoradas} (los que han vuelto con un ignorado
     *  que vale: no salen; 0.9.27 §4.1). */
    record Vueltas(Map<String, VueltaNsys> salen, Map<String, VueltaNsys> ignoradas) {}

    /** Unas filas partidas en las que salen y las de los ignorados (para «Ver ignorados»). */
    private record Partido<T>(List<T> salen, List<T> ignoradas) {
        static <T> Partido<T> nada() {
            return new Partido<>(List.of(), List.of());
        }
    }

    /** Los móviles de «Cambio de color» y «Cambio de grado» que salen, con su vuelta (pieza 4 §6), y los ignorados con la suya. */
    private record Salen(List<TallerNsys.MovilPorPasar> moviles, Map<String, VueltaNsys> vueltas,
                         List<TallerNsys.MovilPorPasar> ignorados, Map<String, VueltaNsys> ignoradas) {}

    /** Los de «Por pasar a NSYS» que NSYS tiene en la empresa y los vendidos o de baja que han vuelto (pieza 4 §6) sin un ignorado
     *  que valga (0.9.27); aparte, los ignorados. Un vendido o de baja que no ha vuelto no se vuelve a subir (sería stock que no
     *  hay) ni se le cambia el grado; su cambio sigue vigente y, si vuelve, sale otra vez solo. */
    private Salen movilesQueSalen(Leidos leidos) {
        List<TallerNsys.MovilPorPasar> todos = taller.movilesPorPasar();
        Vueltas v = vueltas(todos.stream().map(TallerNsys.MovilPorPasar::movil).toList(), leidos);
        return new Salen(todos.stream().filter(p -> v.salen().containsKey(NsysDAO.claveCruce(p.movil().imeiSn()))).toList(), v.salen(),
                todos.stream().filter(p -> v.ignoradas().containsKey(NsysDAO.claveCruce(p.movil().imeiSn()))).toList(), v.ignoradas());
    }

    private Salen movilesQueSalen() {
        return movilesQueSalen(new Leidos());
    }

    Vueltas vueltas(Collection<MovilNsys> moviles) {
        return vueltas(moviles, new Leidos());
    }

    /** La vuelta de cada móvil, por clave de cruce (pieza 4 §6): los de la empresa sin leer nada; los vendidos o de baja, con sus
     *  trabajos (una consulta, solo si hay alguno) y, solo si alguno ha vuelto, los ignorados. Un vendido o de baja que no ha
     *  vuelto no está. */
    private Vueltas vueltas(Collection<MovilNsys> moviles, Leidos leidos) {
        Map<String, VueltaNsys> salen = new HashMap<>();
        Map<String, VueltaNsys> ignoradas = new HashMap<>();
        List<MovilNsys> fuera = new ArrayList<>();
        for (MovilNsys m : moviles) {
            if (m.estado().enEmpresa()) salen.put(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            else fuera.add(m);
        }
        if (fuera.isEmpty()) return new Vueltas(salen, ignoradas);
        Map<String, List<NsysDAO.TrabajoFecha>> trabajos = VueltaNsys.trabajosPorImei(
                nsysDao.trabajosDe(fuera.stream().map(MovilNsys::imeiSn).toList()));
        for (MovilNsys m : fuera) {
            String clave = NsysDAO.claveCruce(m.imeiSn());
            List<NsysDAO.TrabajoFecha> delImei = trabajos.getOrDefault(clave, List.of());
            Optional<VueltaNsys> v = VueltaNsys.de(m, delImei);
            if (v.isEmpty()) continue;
            VueltaIgnorada i = leidos.ignorados().get(clave);
            if (i == null) salen.put(clave, v.get());
            else if (VueltaNsys.vale(i, delImei)) ignoradas.put(clave, v.get());
            else salen.put(clave, v.get().conIgnoradoAntes(i));
        }
        return new Vueltas(salen, ignoradas);
    }

    /** «Revisar a mano»: los avisos de móviles que salen (en la empresa o que han vuelto sin ignorado que valga; uno que ya no está
     *  en el espejo, como antes), con su vuelta; aparte, los de los ignorados. */
    private Partido<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Leidos leidos) {
        Map<String, MovilNsys> espejo = espejo(avisos);
        Vueltas v = vueltas(espejo.values(), leidos);
        List<AvisoTaller> salen = avisos.stream().filter(a -> {
            String clave = NsysDAO.claveCruce(a.imeiSn());
            return !espejo.containsKey(clave) || v.salen().containsKey(clave);
        }).toList();
        List<AvisoTaller> ignoradas = avisos.stream().filter(a -> v.ignoradas().containsKey(NsysDAO.claveCruce(a.imeiSn()))).toList();
        return new Partido<>(TarjetasNsys.revisar(salen, espejo, v.salen()), TarjetasNsys.revisar(ignoradas, espejo, v.ignoradas()));
    }
```

(Imports: `java.util.Optional`.)

3. `sinGrado()` pasa a `Partido<FilaSinGradoNsys> sinGrado(Leidos leidos)`: los `return List.of();` pasan a
   `return Partido.nada();` y el final queda

```java
        Vueltas v = vueltas(sinLetra.values(), leidos);
        return new Partido<>(TarjetasNsys.sinGrado(porImei, sinLetra, vigentes, v.salen(), baterias),
                TarjetasNsys.sinGrado(porImei, sinLetra, vigentes, v.ignoradas(), baterias));
```

   Añadir, para los tests que lo llaman directamente: `List<FilaSinGradoNsys> sinGrado() { return sinGrado(new Leidos()).salen(); }`.
4. `tarjetas()`:

```java
    /** Las cuatro tarjetas (§3), los descartados de «No está en NSYS» (pieza 3), las filas «Sin grado en NSYS» (pieza 4) y los
     *  ignorados (0.9.27), con una lectura de cada cosa. */
    public TarjetasPorPasar tarjetas() {
        Leidos leidos = new Leidos();
        Salen salen = movilesQueSalen(leidos);
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        List<AltaTaller> altas = altas();
        Map<String, DescarteTaller> descartes = descartes(altas);
        Partido<FilaRevisarNsys> revisar = revisar(avisos, leidos);
        Partido<FilaSinGradoNsys> sinGrado = sinGrado(leidos);
        TarjetasNsys.Ignorados ignorados = new TarjetasNsys.Ignorados(leidos::ignorados);
        ignorados.color(TarjetasNsys.color(salen.ignorados(), marcas, salen.ignoradas()));
        ignorados.grado(TarjetasNsys.grado(salen.ignorados(), marcas, salen.ignoradas()));
        ignorados.revisar(revisar.ignoradas());
        ignorados.sinGrado(sinGrado.ignoradas());
        return new TarjetasPorPasar(TarjetasNsys.color(salen.moviles(), marcas, salen.vueltas()),
                TarjetasNsys.grado(salen.moviles(), marcas, salen.vueltas()), TarjetasNsys.altas(altas, marcas, descartes),
                revisar.salen(), TarjetasNsys.descartadas(altas, descartes), sinGrado.salen(), ignorados.filas());
    }
```

   `repasarHechos` y `plantillaColor` siguen con `movilesQueSalen()` (sin ignorados que valgan: sus marcas se borran en el repaso).

- [ ] **Step 9: Tests del servicio (`PorPasarNsysTest`)**

Añadir el mock y pasarlo al constructor: `private final VueltaIgnoradaDAO ignorados = mock(VueltaIgnoradaDAO.class);` y
`new PorPasarNsys(taller, tallerDao, nsys, hechos, descartes, ignorados, registro, reparaciones, Clock.fixed(...))`. Al final de la
sección de la pieza 4, añadir:

```java
    // ---------------------------------------------------------------- 0.9.27: «ignorar» (spec §4)

    private static final LocalDateTime TRABAJO = LocalDateTime.of(2026, 10, 22, 14, 6);

    private void vueltoConTrabajo(String imei) {
        when(taller.movilesPorPasar()).thenReturn(List.of(vendidoConAmbosCambios(imei, LocalDate.of(2026, 9, 14))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(imei, "A20261022_101", TRABAJO, TRABAJO.plusHours(2))));
    }

    @Test void unIgnoradoQueValeSaleDeColorYGradoYEstaEnIgnoradosConSusTarjetas() {
        String vuelto = "350000000000002";
        vueltoConTrabajo(vuelto);
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada(vuelto, 7, "Supertécnico 1", AHORA)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.color().isEmpty());
        assertTrue(t.grado().isEmpty());
        assertEquals(1, t.ignorados().size());
        FilaIgnoradaNsys f = t.ignorados().get(0);
        assertEquals(vuelto, f.imeiSn());
        assertEquals(List.of("COLOR", "GRADO"), f.tarjetas());
        assertEquals("Supertécnico 1", f.ignoradoPor());
        assertEquals(AHORA, f.ignoradoEn());
        assertEquals(LocalDate.of(2026, 9, 14), f.vendidoEn());
        assertEquals(TRABAJO, f.vueltoDesde());
    }

    @Test void unIgnoradoSuperadoPorUnaAsignacionSaleEnAmbarConQuienLoIgnoro() {
        String vuelto = "350000000000002";
        vueltoConTrabajo(vuelto);
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada(vuelto, 7, "Supertécnico 1", TRABAJO.minusDays(1))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(vuelto), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals("Supertécnico 1", t.color().get(0).ignoradoAntesPor());
        assertEquals(TRABAJO.minusDays(1), t.grado().get(0).ignoradoAntesEn());
        assertTrue(t.ignorados().isEmpty());
    }

    @Test void unIgnoradoDeUnMovilEnLaEmpresaNoHaceNadaYNiSeLee() {
        when(taller.movilesPorPasar()).thenReturn(List.of(conAmbosCambios(IMEI, EstadoMovil.STOCK)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertNull(t.color().get(0).ignoradoAntesEn());
        assertTrue(t.ignorados().isEmpty());
        verify(ignorados, never()).todos();
    }

    @Test void revisarSinLosAvisosDeUnIgnoradoQueSaleEnIgnorados() {
        String vuelto = "350000000000002";
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinColorDe(1, IMEI), sinColorDe(2, vuelto)));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B"), vendido(vuelto, "Azul", "B", LocalDate.of(2026, 9, 14))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", TRABAJO)));
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada(vuelto, 7, "Supertécnico 1", AHORA)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.revisar().stream().map(FilaRevisarNsys::imeiSn).toList());
        assertEquals(List.of("REVISAR"), t.ignorados().get(0).tarjetas());
    }

    @Test void conVariasListasLosIgnoradosSeLeenUnaSolaVez() {
        String vuelto = "350000000000002";
        vueltoConTrabajo(vuelto);
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinColorDe(2, vuelto)));
        when(nsys.porImeis(any())).thenReturn(List.of(vendido(vuelto, "Azul", "B", LocalDate.of(2026, 9, 14))));
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada(vuelto, 7, "Supertécnico 1", AHORA)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of("COLOR", "GRADO", "REVISAR"), t.ignorados().get(0).tarjetas());
        verify(ignorados, times(1)).todos();
    }

    @Test void laPlantillaDeColorTampocoLlevaUnIgnorado() {
        String vuelto = "350000000000002";
        vueltoConTrabajo(vuelto);
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada(vuelto, 7, "Supertécnico 1", AHORA)));
        assertEquals(0, servicio.plantillaColor().filas());
    }
```

En `RolesStockTelefonosTest`, junto a `@MockBean DescarteDAO descarteDao;`, añadir `@MockBean VueltaIgnoradaDAO ignoradoDao;` (con su
import `com.reparaciones.servidor.nsys.VueltaIgnoradaDAO`).

- [ ] **Step 10: Comprobar**

Run: `cd $S && mvn -q test -Dtest='VueltaNsysTest,TarjetasNsysTest,PorPasarNsysTest,RolesStockTelefonosTest' 2>&1 | tail -30`
Expected: BUILD SUCCESS. Si un test antiguo construye una fila con el canónico de antes, ya tiene su constructor; si llama a
`servicio.vueltas(...)` y esperaba un `Map`, cambiarlo a `servicio.vueltas(...).salen()`.

- [ ] **Step 11: Commit**

```bash
cd $S && git add -A src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor
git status --short   # solo ficheros de esta tarea
git commit -m "feat: un vendido o de baja ignorado sale de Color, Grado y Revisar y va en ignorados; el superado lleva quien lo ignoro (0.9.27)"
```

---

### Task 3: Rutas, repaso tras la importación y contrato (servidor)

**Files:**
- Modify: `…/nsys/PorPasarNsys.java`, `…/nsys/PorPasarController.java`, `…/nsys/ImportadorNsys.java`
- Test: `…/nsys/PorPasarNsysTest.java`, `…/nsys/ImportadorNsysTest.java`, `RolesStockTelefonosTest.java`

**Interfaces:**
- Produces: `PorPasarNsys.ignorar(String imeiSn, int idUsu)`, `deshacerIgnorado(String imeiSn, int idUsu)`,
  `repasarIgnorados()`; rutas `PUT`/`DELETE /api/stock-telefonos/por-pasar/ignorado` con cuerpo `PorPasarPeticionIgnorado
  {imeiSn}`; `target/openapi.json` para la web.

- [ ] **Step 1: Tests que fallan**

En `PorPasarNsysTest`:

```java
    @Test void ignorarGuardaQuienYCuandoYLoApunta() {
        servicio.ignorar(" 350000000000002 ", 7);
        verify(ignorados).ignorar("350000000000002", 7, AHORA);
        verify(registro).insertar(7, "IGNORAR_VUELTA_NSYS", "IMEI: 350000000000002", null);
    }

    @Test void deshacerUnIgnoradoSoloSeApuntaSiHabiaUno() {
        when(ignorados.deshacer("350000000000002")).thenReturn(1);
        servicio.deshacerIgnorado("350000000000002", 7);
        verify(registro).insertar(7, "DESHACER_IGNORAR_VUELTA_NSYS", "IMEI: 350000000000002", null);
        servicio.deshacerIgnorado("350000000000003", 7);
        verifyNoMoreInteractions(registro);
    }

    @Test void ignorarSinImeiOLargoEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.ignorar(" ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.deshacerIgnorado("3".repeat(41), 7)));
        verifyNoInteractions(ignorados);
    }

    @Test void elRepasoQuitaLosIgnoradosQueNsysVuelveATenerEnLaEmpresa() {
        when(ignorados.todos()).thenReturn(List.of(new VueltaIgnorada("350000000000002", 7, "Supertécnico 1", AHORA),
                new VueltaIgnorada("350000000000003", 7, "Supertécnico 1", AHORA)));
        when(nsys.porImeis(any())).thenReturn(List.of(movil("350000000000002", "Azul", "B"),
                vendido("350000000000003", "Azul", "B", LocalDate.of(2026, 9, 14))));
        servicio.repasarIgnorados();
        verify(ignorados).quitar(List.of("350000000000002"));
    }

    @Test void sinIgnoradosElRepasoNoLeeElEspejo() {
        servicio.repasarIgnorados();
        verify(nsys, never()).porImeis(any());
        verify(ignorados, never()).quitar(any());
    }
```

(`HttpStatus` es `org.springframework.http.HttpStatus`; si el fichero solo importa `HttpStatusCode`, añadirlo.) `movil(...)`
del ayudante `TallerDePrueba` es un móvil en stock.

En `ImportadorNsysTest`: en `siElCierreFallaNoSeRepasanLasMarcasYLaImportacionSigueOk` añadir
`verify(porPasar, never()).repasarIgnorados();`; en `trasCerrarLoDelTallerSeRepasanLasMarcasConLaHoraDelFichero`, entre
`repasarDescartes` y `usarBateriasRecordadas`, `orden.verify(porPasar).repasarIgnorados();`; y uno nuevo copiando
`siFallaElRepasoDeLosDescartesLaPasadaSigue` con `doThrow(new IllegalStateException("fallo")).when(porPasar).repasarIgnorados();`
y el nombre `siFallaElRepasoDeLosIgnoradosLaPasadaSigue`. Si el fichero tiene otro test con `never()` para `repasarDescartes`
(línea ~577), añadir allí también `verify(porPasar, never()).repasarIgnorados();`.

En `RolesStockTelefonosTest`, tras `elDescarteSoloParaSupertecnicoYAdmin`:

```java
    /** 0.9.27: «ignorar» y su «deshacer», con la misma guarda que el resto. */
    @Test void elIgnoradoSoloParaSupertecnicoYAdmin() throws Exception {
        String cuerpo = "{\"imeiSn\":\"350000000000002\"}";
        mvc.perform(put("/api/stock-telefonos/por-pasar/ignorado").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/ignorado").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/stock-telefonos/por-pasar/ignorado")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        verifyNoInteractions(ignoradoDao);

        mvc.perform(put("/api/stock-telefonos/por-pasar/ignorado").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNoContent());
        verify(ignoradoDao).ignorar(eq("350000000000002"), eq(7), any());
        mvc.perform(delete("/api/stock-telefonos/por-pasar/ignorado").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNoContent());
        verify(ignoradoDao).deshacer("350000000000002");
        mvc.perform(put("/api/stock-telefonos/por-pasar/ignorado").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"imeiSn\":\" \"}")).andExpect(status().isBadRequest());
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $S && mvn -q test -Dtest='PorPasarNsysTest,ImportadorNsysTest,RolesStockTelefonosTest' 2>&1 | tail -20`
Expected: error de compilación (`ignorar`, `deshacerIgnorado`, `repasarIgnorados` no existen).

- [ ] **Step 3: El servicio**

En `PorPasarNsys`, junto a las constantes de los descartes:

```java
    static final String ACCION_IGNORAR = "IGNORAR_VUELTA_NSYS";
    static final String ACCION_DESHACER_IGNORADO = "DESHACER_IGNORAR_VUELTA_NSYS";
```

Tras `repasarDescartes()`:

```java
    /** «ignorar» de una fila ámbar (spec 0.9.27 §4.3): guarda el ignorado (o sustituye el que hubiera) y lo apunta en el registro de
     *  actividad. No mira si el móvil está en ámbar, como «descartar»: en uno de la empresa no hace nada y lo quita el repaso.
     *  READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ignorar(String imeiSn, int idUsu) {
        String imei = imei(imeiSn);
        ignoradoDao.ignorar(imei, idUsu, ahora());
        logDao.insertar(idUsu, ACCION_IGNORAR, "IMEI: " + imei, null);
    }

    /** «deshacer» de «Ver ignorados»: quita el ignorado y, si había uno, lo apunta. READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deshacerIgnorado(String imeiSn, int idUsu) {
        String imei = imei(imeiSn);
        if (ignoradoDao.deshacer(imei) > 0) logDao.insertar(idUsu, ACCION_DESHACER_IGNORADO, "IMEI: " + imei, null);
    }

    /** Tras cada importación ACTUAL que acaba en OK (spec 0.9.27 §4.4): fuera los ignorados de los IMEI que NSYS vuelve a tener en
     *  la empresa (su fila más reciente del espejo). Los superados por un trabajo nuevo se quedan (dan «Salió otra vez…»). Sin
     *  ignorados no lee el espejo. */
    public void repasarIgnorados() {
        List<VueltaIgnorada> ignorados = ignoradoDao.todos();
        if (ignorados.isEmpty()) return;
        Map<String, MovilNsys> espejo = espejoDe(ignorados.stream().map(VueltaIgnorada::imeiSn).toList());
        List<String> enEmpresa = ignorados.stream().map(VueltaIgnorada::imeiSn).filter(i -> {
            MovilNsys m = espejo.get(NsysDAO.claveCruce(i));
            return m != null && m.estado().enEmpresa();
        }).toList();
        if (enEmpresa.isEmpty()) return;
        ignoradoDao.quitar(enEmpresa);
        log.info("NSYS: ignorados de las listas quitados: {} (otra vez en la empresa)", enEmpresa.size());
    }
```

Actualizar la Javadoc de la clase: «… y los descartes de «No está en NSYS» (pieza 3) y los ignorados de las filas ámbar (0.9.27)».

- [ ] **Step 4: El controlador**

En `PorPasarController`, tras `deshacerDescarte`:

```java
    /** «ignorar» de una fila ámbar de Color, Grado o Revisar (0.9.27 §4.3). */
    @PutMapping("/ignorado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ignorar(@RequestBody PeticionIgnorado peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.ignorar(peticion.imeiSn(), principal.getIdUsu());
    }

    /** «deshacer» de un ignorado («Ver ignorados»). */
    @DeleteMapping("/ignorado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerIgnorado(@RequestBody PeticionIgnorado peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.deshacerIgnorado(peticion.imeiSn(), principal.getIdUsu());
    }
```

y al final, junto a los otros records:

```java
    /** Esquema PorPasarPeticionIgnorado. */
    record PeticionIgnorado(String imeiSn) {}
```

- [ ] **Step 5: La importación**

En `ImportadorNsys`, tras `repasarDescartes();` (línea ~191) añadir `repasarIgnorados();`, y tras el método privado
`repasarDescartes()`:

```java
    /** Tras el repaso de los descartes, el de los ignorados (spec 0.9.27 §4.4): fuera los de IMEI que NSYS vuelve a tener en la
     *  empresa. Con la misma condición (cierre bueno); si falla, se avisa en el registro del servidor y se repite con el siguiente
     *  fichero. */
    private void repasarIgnorados() {
        try {
            porPasar.repasarIgnorados();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron repasar los ignorados tras la importación", e);
        }
    }
```

- [ ] **Step 6: Toda la suite del servidor**

Run: `cd $S && mvn test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD SUCCESS|BUILD FAILURE|ERROR\]" | tail -8`
Expected: `BUILD SUCCESS`, 0 fallos (incluye `OpenApiContractTest`, que levanta el contexto y escribe `target/openapi.json`).

- [ ] **Step 7: El contrato tiene lo nuevo**

Run: `cd $S && grep -o '"/api/stock-telefonos/por-pasar/ignorado"\|"PorPasarPeticionIgnorado"\|"FilaIgnoradaNsys"\|"ignoradoAntesEn"' target/openapi.json | sort | uniq -c`
Expected: las cuatro cadenas.

- [ ] **Step 8: Commit**

```bash
cd $S && git add src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarController.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor
git commit -m "feat: rutas PUT y DELETE /ignorado y repaso de los ignorados tras cada importacion (0.9.27)"
```

---

### Task 4: Tipos, rutas y textos (web)

**Files:**
- Modify: `$W/api/openapi.json` (copia), `$W/src/shared/api/schema.d.ts` (generado), `$W/src/shared/api/client.ts`,
  `$W/src/modules/telefonos/api.ts`, `$W/src/modules/telefonos/stock/porPasar.ts`, `$W/src/modules/telefonos/test/fabrica.ts`,
  `$W/src/test/server.ts`, `$W/src/modules/telefonos/stock/PorPasarNsys.tsx` (solo `VACIAS`)
- Test: `$W/src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: `target/openapi.json` de la Task 3.
- Produces: tipos `FilaIgnoradaNsys`, `PeticionIgnorado`; hooks `useIgnorarVuelta()`, `useDeshacerIgnorado()` (mutación con
  `{ imeiSn }`); en `porPasar.ts`: `TARJETA_IGNORADO`, `type TarjetaConIgnorados`, `TITULO_IGNORAR`, `avisoIgnorado(imei)`,
  `avisoVuelveALasListas(imei)`, `textoVerIgnorados(n, abierto)`, `ignoradosDe(t, cual)`, `textoEnNsys(f)`,
  `textoIgnorado(f)`, `textoIgnoradoAntes(f)`; `filaIgnorada(p)` en la fábrica.

- [ ] **Step 1: Regenerar los tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-0927-ignorar/target/openapi.json /c/Users/dev/Documents/wt/web-0927-ignorar/api/openapi.json
cd /c/Users/dev/Documents/wt/web-0927-ignorar && npm run api:types:offline && git diff --stat
```

Expected: cambian `api/openapi.json` y `src/shared/api/schema.d.ts` (con `FilaIgnoradaNsys`, `PorPasarPeticionIgnorado`,
`ignoradoAntesEn` y la ruta `/api/stock-telefonos/por-pasar/ignorado`). Ningún otro esquema debería cambiar; si cambia algo
ajeno, parar y mirarlo.

- [ ] **Step 2: Tipos en `client.ts`**

Tras el bloque de `FilaSinGradoNsys`:

```ts
/** 0.9.27 (spec «ignorar» §4.2): un vendido o de baja ignorado de las filas ámbar, para «Ver ignorados»; `tarjetas` dice en cuáles
 *  saldría (COLOR, GRADO, REVISAR). Las filas de Color, Grado, Revisar y Sin grado llevan `ignoradoAntesPor`/`ignoradoAntesEn` si
 *  un trabajo nuevo superó el ignorado. */
export type FilaIgnoradaNsys = components['schemas']['FilaIgnoradaNsys']
export type PeticionIgnorado = components['schemas']['PorPasarPeticionIgnorado']
```

- [ ] **Step 3: Los hooks en `api.ts`**

Añadir `type PeticionIgnorado` al import de `@/shared/api/client` y, tras `useDeshacerDescarte`:

```ts
/** PUT /api/stock-telefonos/por-pasar/ignorado (0.9.27 §4.3): «ignorar» de una fila ámbar, sin motivo. */
export function useIgnorarVuelta() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionIgnorado) => api.PUT('/api/stock-telefonos/por-pasar/ignorado', { body: cuerpo }),
    onSettled: recargar,
  })
}

/** DELETE /api/stock-telefonos/por-pasar/ignorado: «deshacer» desde «Ver ignorados». */
export function useDeshacerIgnorado() {
  const recargar = useRecargarTarjetas(false)
  return useMutation({
    mutationFn: (cuerpo: PeticionIgnorado) => api.DELETE('/api/stock-telefonos/por-pasar/ignorado', { body: cuerpo }),
    onSettled: recargar,
  })
}
```

(Si `useDescartarAlta` envuelve el error de la API de otra forma —p. ej. con un `if (error) throw`—, copiar exactamente su forma.)

- [ ] **Step 4: Tests de los textos (`porPasar.test.ts`)**

Añadir (con `filaIgnorada` y `tarjetas` importados de `../test/fabrica` y las funciones nuevas de `./porPasar`):

```ts
describe('0.9.27: «ignorar» en las filas ámbar', () => {
  it('los avisos, el pie y la ayuda', () => {
    expect(avisoIgnorado('350000000000123')).toBe('Ignorado «350000000000123»: sale en «Ver ignorados» hasta que vuelva')
    expect(avisoVuelveALasListas('350000000000123')).toBe('«350000000000123» vuelve a las listas')
    expect(textoVerIgnorados(2, false)).toBe('Ver ignorados (2)')
    expect(textoVerIgnorados(2, true)).toBe('Ocultar ignorados')
    expect(TITULO_IGNORAR).toBe('No ha vuelto: quitarlo de las listas hasta que NSYS lo tenga otra vez en la empresa (o el taller le haga un trabajo nuevo)')
    const frase = ' Un vendido o de baja que sale en ámbar tiene trabajo del taller después de la venta o de la baja: si es una devolución, apúntala en NSYS; si no ha vuelto, «ignorar» lo quita de las tres listas hasta que NSYS lo tenga otra vez en la empresa o el taller le haga un trabajo nuevo.'
    for (const t of ['color', 'grado', 'revisar'] as const) expect(AYUDA[t].endsWith(frase)).toBe(true)
    expect(AYUDA.altas).not.toContain('ignorar')
  })

  it('qué dice NSYS, quién lo ignoró y «Salió otra vez…»', () => {
    expect(textoEnNsys(filaIgnorada())).toBe('Vendido en NSYS el 26/10/2026')
    expect(textoEnNsys(filaIgnorada({ vendidoEn: null, bajaEn: '2026-10-15' }))).toBe('De baja en NSYS desde el 15/10/2026')
    expect(textoIgnorado(filaIgnorada())).toBe('Supertécnico 1 · 06/11/2026 12:31')
    expect(textoIgnorado(filaIgnorada({ ignoradoPor: null }))).toBe('— · 06/11/2026 12:31')
    expect(textoIgnoradoAntes({ ignoradoAntesPor: 'Supertécnico 1', ignoradoAntesEn: '2026-11-06T12:31:00' }))
      .toBe('Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo ignorara el 06/11/2026 12:31')
    expect(textoIgnoradoAntes({ ignoradoAntesPor: null, ignoradoAntesEn: null })).toBeNull()
    expect(textoIgnoradoAntes({} as { ignoradoAntesPor: string | null; ignoradoAntesEn: string | null })).toBeNull()
  })

  it('los ignorados de cada tarjeta, y con un servidor anterior ninguno', () => {
    const t = tarjetas({ ignorados: [filaIgnorada(), filaIgnorada({ imeiSn: '350000000000149', tarjetas: ['GRADO', 'REVISAR'] })] })
    expect(ignoradosDe(t, 'color').map((f) => f.imeiSn)).toEqual(['350000000000123'])
    expect(ignoradosDe(t, 'grado').map((f) => f.imeiSn)).toEqual(['350000000000123', '350000000000149'])
    expect(ignoradosDe(t, 'revisar').map((f) => f.imeiSn)).toEqual(['350000000000149'])
    const viejas = tarjetas()
    delete (viejas as Partial<typeof viejas>).ignorados
    expect(ignoradosDe(viejas, 'color')).toEqual([])
  })
})
```

- [ ] **Step 5: Comprobar que fallan**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/porPasar.test.ts 2>&1 | tail -15`
Expected: FAIL (no existen las funciones ni `filaIgnorada`).

- [ ] **Step 6: Textos en `porPasar.ts`**

Añadir `FilaIgnoradaNsys` al import de tipos. En `AYUDA`, concatenar `IGNORAR_AYUDA` al final de `color`, `grado` y `revisar`
(declarando la constante **antes** de `AYUDA`):

```ts
/** 0.9.27 (spec «ignorar» §5.4): la frase que ganan las ayudas de Color, Grado y Revisar. */
const IGNORAR_AYUDA = ' Un vendido o de baja que sale en ámbar tiene trabajo del taller después de la venta o de la baja: si es una devolución, apúntala en NSYS; si no ha vuelto, «ignorar» lo quita de las tres listas hasta que NSYS lo tenga otra vez en la empresa o el taller le haga un trabajo nuevo.'
```

(p. ej. `color: 'Móviles a los que … ve el color nuevo en NSYS.' + IGNORAR_AYUDA,`). Al final del fichero:

```ts
// ---------------------------------------------------------------- 0.9.27: «ignorar» en los vendidos y de baja que salen en ámbar

/** Las tarjetas que pueden tener ignorados y su nombre en `FilaIgnoradaNsys.tarjetas`. */
export const TARJETA_IGNORADO = { color: 'COLOR', grado: 'GRADO', revisar: 'REVISAR' } as const
export type TarjetaConIgnorados = keyof typeof TARJETA_IGNORADO

export const TITULO_IGNORAR = 'No ha vuelto: quitarlo de las listas hasta que NSYS lo tenga otra vez en la empresa (o el taller le haga un trabajo nuevo)'
export const avisoIgnorado = (imei: string): string => `Ignorado «${imei}»: sale en «Ver ignorados» hasta que vuelva`
export const avisoVuelveALasListas = (imei: string): string => `«${imei}» vuelve a las listas`
export const textoVerIgnorados = (n: number, abierto: boolean): string => (abierto ? 'Ocultar ignorados' : `Ver ignorados (${n})`)

/** Los ignorados que saldrían en esta tarjeta. `?? []`: un servidor anterior a la 0.9.27 no manda `ignorados`. */
export const ignoradosDe = (t: TarjetasPorPasar, cual: TarjetaConIgnorados): FilaIgnoradaNsys[] =>
  (t.ignorados ?? []).filter((f) => f.tarjetas.includes(TARJETA_IGNORADO[cual]))

/** «Vendido en NSYS el 26/10/2026» o «De baja en NSYS desde el 15/10/2026». */
export const textoEnNsys = (f: Pick<FilaIgnoradaNsys, 'vendidoEn' | 'bajaEn'>): string =>
  f.vendidoEn ? `Vendido en NSYS el ${diaNsys(f.vendidoEn)}` : `De baja en NSYS desde el ${f.bajaEn ? diaNsys(f.bajaEn) : '—'}`

/** «Supertécnico 1 · 06/11/2026 12:31». */
export const textoIgnorado = (f: Pick<FilaIgnoradaNsys, 'ignoradoPor' | 'ignoradoEn'>): string =>
  `${f.ignoradoPor ?? '—'} · ${textoDesde(f.ignoradoEn)}`

/** «Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo ignorara el 06/11/2026 12:31»; sin un ignorado superado, null.
 *  `!f.ignoradoAntesEn`: un servidor anterior a la 0.9.27 no manda el campo. */
export function textoIgnoradoAntes(f: { ignoradoAntesPor: string | null; ignoradoAntesEn: string | null }): string | null {
  if (!f.ignoradoAntesEn) return null
  return `Salió otra vez: trabajo nuevo después de que ${f.ignoradoAntesPor ?? '—'} lo ignorara el ${textoDesde(f.ignoradoAntesEn)}`
}
```

- [ ] **Step 7: La fábrica, el servidor de pruebas y `VACIAS`**

En `fabrica.ts`: `filaColor`, `filaGrado`, `filaRevisar` y `filaSinGrado` ganan `ignoradoAntesPor: null, ignoradoAntesEn: null`
antes de `...p`; `tarjetas()` devuelve `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], sinGrado: [], ignorados: [], ...p }`;
y se añade (con `FilaIgnoradaNsys` en el import de tipos):

```ts
/** 0.9.27: un iPhone 15 eSIM 256GB vendido el 26/10, que el taller tuvo el 28/10 y Supertécnico 1 ignoró el 06/11 12:31; saldría en
 *  Color y Grado. */
export function filaIgnorada(p: Partial<FilaIgnoradaNsys> = {}): FilaIgnoradaNsys {
  return {
    imeiSn: '350000000000123', modelo: '15', modeloMaestro: '15', capacidad: '256GB', esEsim: true, vendidoEn: '2026-10-26',
    bajaEn: null, vueltoDesde: '2026-10-28T09:10:00', ignoradoPor: 'Supertécnico 1', ignoradoEn: '2026-11-06T12:31:00',
    tarjetas: ['COLOR', 'GRADO'], ...p,
  }
}
```

En `src/test/server.ts` (línea 42) la respuesta de `tarjetas` gana `ignorados: []`. En `PorPasarNsys.tsx`, `VACIAS` gana
`ignorados: []`.

- [ ] **Step 8: Comprobar**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/porPasar.test.ts 2>&1 | tail -8 && npm run typecheck 2>&1 | tail -15`
Expected: los tests pasan; `typecheck` sin errores (si marca algún literal de fila o de tarjetas en otro test, añadirle los
campos nuevos con `null` / `[]`).

- [ ] **Step 9: Commit**

```bash
cd $W && git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/api.ts src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/test/fabrica.ts src/test/server.ts src/modules/telefonos/stock/PorPasarNsys.tsx
git status --short
git commit -m "feat: tipos, rutas y textos de ignorar los vendidos que salen en ambar (0.9.27)"
```

---

### Task 5: «ignorar», «Salió otra vez…» y «Ver ignorados» en la pantalla (web)

**Files:**
- Modify: `$W/src/modules/telefonos/stock/CeldaHecho.tsx`, `$W/src/modules/telefonos/stock/ListasPorPasar.tsx`,
  `$W/src/modules/telefonos/stock/PorPasarNsys.tsx`
- Test: `$W/src/modules/telefonos/stock/PorPasarNsys.test.tsx`

**Interfaces:**
- Consumes: todo lo de la Task 4.
- Produces: `CeldaHecho` con `onIgnorar?: () => void`; `ListaColor`, `ListaGrado` y `ListaRevisar` con las props
  `ignorados: FilaIgnoradaNsys[]`, `onIgnorar: (imeiSn: string) => void`, `onDeshacerIgnorado: (imeiSn: string) => void`
  (`ListaRevisar` también `ocupado` para los enlaces).

- [ ] **Step 1: Tests de la pantalla (que fallan)**

En `PorPasarNsys.test.tsx` (importar `filaIgnorada` de la fábrica), un `describe` nuevo al final:

```tsx
describe('«ignorar» en las filas ámbar (0.9.27)', () => {
  afterEach(() => vi.restoreAllMocks())
  const vuelto = { vendidoEn: '2026-10-26', bajaEn: null, vueltoDesde: '2026-10-28T09:10:00' }

  it('solo en las filas ámbar sin marca, al lado de «Hecho», y en «Revisar a mano» al lado de «Corregir…»', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({
      color: [filaColor(vuelto), filaColor({ imeiSn: '350000000000012' }), filaColor({ imeiSn: '350000000000013', ...vuelto, ...HECHO })],
      revisar: [filaRevisar(vuelto)],
    }))
    const enlace = screen.getByRole('button', { name: 'Ignorar 350000000000011' })
    expect(enlace).toHaveTextContent('ignorar')
    expect(enlace).toHaveAttribute('title', 'No ha vuelto: quitarlo de las listas hasta que NSYS lo tenga otra vez en la empresa (o el taller le haga un trabajo nuevo)')
    expect(screen.queryByRole('button', { name: 'Ignorar 350000000000012' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ignorar 350000000000013' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    const fila = filas(screen.getByRole('table', { name: 'Revisar a mano' }))[0]
    expect(within(fila).getByRole('button', { name: 'Ignorar 350000000000101' })).toBeInTheDocument()
  })

  it('un clic, sin ventana, manda el IMEI y avisa', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(http.put('*/api/stock-telefonos/por-pasar/ignorado', async ({ request }) => {
      pedidos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    pintar(tarjetas({ grado: [filaGrado(vuelto)] }))
    await user.click(screen.getByRole('button', { name: 'Cambio de grado' }))
    await user.click(screen.getByRole('button', { name: 'Ignorar 350000000000022' }))
    await waitFor(() => expect(pedidos).toEqual([{ imeiSn: '350000000000022' }]))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Ignorado «350000000000022»: sale en «Ver ignorados» hasta que vuelva')
  })

  it('«Ver ignorados» por tarjeta: en gris, qué dice NSYS, quién, y «deshacer» los devuelve', async () => {
    const user = userEvent.setup()
    const pedidos: unknown[] = []
    server.use(http.delete('*/api/stock-telefonos/por-pasar/ignorado', async ({ request }) => {
      pedidos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }))
    pintar(tarjetas({ ignorados: [filaIgnorada(), filaIgnorada({ imeiSn: '350000000000149', modelo: '12', modeloMaestro: '12',
      capacidad: '64GB', esEsim: false, vendidoEn: null, bajaEn: '2026-10-15', ignoradoPor: null, tarjetas: ['REVISAR'] })] }))
    expect(screen.queryByRole('table', { name: 'Ignorados' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver ignorados (1)' }))
    const tabla = screen.getByRole('table', { name: 'Ignorados' })
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'En NSYS', 'Ignorado', ''])
    const [fila] = filas(tabla)
    expect(fila).toHaveTextContent('350000000000123')
    expect(fila).toHaveTextContent('iPhone 15 eSIM 256GB')
    expect(fila).toHaveTextContent('Vendido en NSYS el 26/10/2026')
    expect(fila).toHaveTextContent('Supertécnico 1 · 06/11/2026 12:31')
    await user.click(screen.getByRole('button', { name: 'Deshacer ignorar 350000000000123' }))
    await waitFor(() => expect(pedidos).toEqual([{ imeiSn: '350000000000123' }]))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('«350000000000123» vuelve a las listas')
    await user.click(screen.getByRole('button', { name: 'Ocultar ignorados' }))
    expect(screen.queryByRole('table', { name: 'Ignorados' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    await user.click(screen.getByRole('button', { name: 'Ver ignorados (1)' }))
    expect(screen.getByRole('table', { name: 'Ignorados' })).toHaveTextContent('De baja en NSYS desde el 15/10/2026')
    expect(screen.getByRole('table', { name: 'Ignorados' })).toHaveTextContent('— · 06/11/2026 12:31')
  })

  it('una fila con un ignorado superado dice «Salió otra vez…» y se puede ignorar otra vez', () => {
    pintar(tarjetas({ color: [filaColor({ ...vuelto, ignoradoAntesPor: 'Supertécnico 1', ignoradoAntesEn: '2026-11-06T12:31:00' })] }))
    expect(screen.getByText('Salió otra vez: trabajo nuevo después de que Supertécnico 1 lo ignorara el 06/11/2026 12:31')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ignorar 350000000000011' })).toBeInTheDocument()
  })

  it('con un servidor anterior a la 0.9.27 (sin `ignorados` ni `ignoradoAntes*`) no sale nada nuevo salvo el enlace', () => {
    const viejas = tarjetas({ color: [filaColor(vuelto)] })
    delete (viejas as Partial<TarjetasPorPasar>).ignorados
    const fila = viejas.color[0] as Partial<FilaColorNsys>
    delete fila.ignoradoAntesPor
    delete fila.ignoradoAntesEn
    pintar(viejas)
    expect(screen.queryByRole('button', { name: /^Ver ignorados/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/^Salió otra vez/)).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/PorPasarNsys.test.tsx 2>&1 | tail -15`
Expected: FAIL en los cinco nuevos (no hay «ignorar» ni «Ver ignorados»); los de antes siguen pasando.

- [ ] **Step 3: `CeldaHecho`**

Props: `onIgnorar?: () => void`. En la rama sin marca, detrás del enlace de «descartar»:

```tsx
      {onIgnorar && (
        <button type="button" disabled={ocupado} aria-label={`Ignorar ${imeiSn}`} title={TITULO_IGNORAR}
          onClick={onIgnorar} className="ml-2 cursor-pointer text-[11px] text-texto-suave hover:text-fila-urgente-brd disabled:cursor-not-allowed disabled:opacity-40">
          ignorar
        </button>
      )}
```

(importar `TITULO_IGNORAR` de `./porPasar`; añadir a la Javadoc: «En las filas ámbar de Color y Grado, «ignorar» (0.9.27)».)

- [ ] **Step 4: `ListasPorPasar`**

1. Imports: `FilaIgnoradaNsys` en los tipos; de `./porPasar`, `textoEnNsys`, `textoIgnorado`, `textoIgnoradoAntes`,
   `textoVerIgnorados`, `TITULO_IGNORAR`.
2. `Vuelta` pinta también «Salió otra vez…»:

```tsx
/** La línea ámbar de un vendido o de baja que ha vuelto (0.9.22 pieza 4 §7) y, si un trabajo nuevo superó su ignorado, «Salió otra
 *  vez…» (0.9.27 §5.3); nada en un móvil en la empresa. */
function Vuelta({ f }: { f: { vendidoEn: string | null; bajaEn: string | null; vueltoDesde: string | null; ignoradoAntesPor: string | null; ignoradoAntesEn: string | null } }) {
  const texto = textoVuelta(f)
  const otra = textoIgnoradoAntes(f)
  return (
    <>
      {texto && <span className={AMBAR}>{texto}</span>}
      {otra && <span className={cn(AMBAR, 'font-bold')}>{otra}</span>}
    </>
  )
}
```

3. `Ignorados` (tras `Descartados`):

```tsx
/** «Ver ignorados (n)» al pie de Color, Grado y Revisar (0.9.27 §5.2): plegado al entrar; desplegado, en gris, con «deshacer». Solo
 *  se pinta con ignorados (sin ellos se desmonta y vuelve plegado). */
function Ignorados({ filas, ocupado, onDeshacer }: { filas: FilaIgnoradaNsys[]; ocupado: boolean; onDeshacer: (imeiSn: string) => void }) {
  const [abierto, setAbierto] = useState(false)
  const columnas = ['IMEI', 'Modelo', 'En NSYS', 'Ignorado', '']
  return (
    <div className="shrink-0">
      <button type="button" aria-expanded={abierto} onClick={() => setAbierto(!abierto)}
        className="cursor-pointer text-[12px] text-azul-gris hover:underline">{textoVerIgnorados(filas.length, abierto)}</button>
      {abierto && (
        <div className="mt-1.5 max-h-[220px] overflow-auto rounded-lg border border-fila-sep">
          <table aria-label="Ignorados" className="w-full border-collapse bg-badge-neutro-bg text-[12.5px] text-texto-suave">
            <thead>
              <tr>{columnas.map((c, i) => <th key={i} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.imeiSn} className="border-t border-fila-sep">
                  <td className={cn(CELDA, 'font-mono text-[11.5px] whitespace-nowrap')}>{f.imeiSn}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{textoEnNsys(f)}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>{textoIgnorado(f)}</td>
                  <td className={cn(CELDA, 'whitespace-nowrap')}>
                    <button type="button" disabled={ocupado} aria-label={`Deshacer ignorar ${f.imeiSn}`} onClick={() => onDeshacer(f.imeiSn)}
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

/** Lo de «ignorar» que reciben Color, Grado y Revisar (0.9.27). */
type ConIgnorados = { ignorados: FilaIgnoradaNsys[]; onIgnorar: (imeiSn: string) => void; onDeshacerIgnorado: (imeiSn: string) => void }
```

4. `TablaConHecho` gana `onIgnorar?: (f: T) => (() => void) | undefined` y lo pasa a `CeldaHecho` como
   `onIgnorar={onIgnorar?.(f)}`.
5. `ListaColor` y `ListaGrado` reciben `ConHechoProps<…> & ConIgnorados` y devuelven:

```tsx
export function ListaColor({ onAbrirImei, ignorados, onIgnorar, onDeshacerIgnorado, ...props }: ConHechoProps<FilaColorNsys> & ConIgnorados) {
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <TablaConHecho {...props} nombre="Cambio de color" onIgnorar={(f) => (f.vueltoDesde ? () => onIgnorar(f.imeiSn) : undefined)}
        columnas={/* igual que ahora */}
        celdas={/* igual que ahora */} />
      {ignorados.length > 0 && <Ignorados filas={ignorados} ocupado={props.ocupado} onDeshacer={onDeshacerIgnorado} />}
    </div>
  )
}
```

   (lo mismo en `ListaGrado` con `nombre="Cambio de grado"`; `columnas` y `celdas` no cambian).
6. `ListaRevisar` recibe además `ConIgnorados`; envuelve su `<div …overflow-auto…>` en
   `<div className="flex min-h-0 flex-1 flex-col gap-2">…{ignorados.length > 0 && <Ignorados filas={ignorados} ocupado={ocupado} onDeshacer={onDeshacerIgnorado} />}</div>`;
   y en la última celda de las filas «Sin grado» y de los avisos, detrás de «Corregir…», si `f.vueltoDesde`:

```tsx
{f.vueltoDesde && (
  <button type="button" disabled={ocupado} aria-label={`Ignorar ${f.imeiSn}`} title={TITULO_IGNORAR} onClick={() => onIgnorar(f.imeiSn)}
    className="ml-2 cursor-pointer text-[11px] text-texto-suave hover:text-fila-urgente-brd disabled:cursor-not-allowed disabled:opacity-40">ignorar</button>
)}
```

   (en los avisos va dentro del `<span className="flex gap-1.5">`, como hermano de «Corregir…»; ahí sin `ml-2`).

- [ ] **Step 5: `PorPasarNsys`**

Importar `useIgnorarVuelta`, `useDeshacerIgnorado` de `../api` y `avisoIgnorado`, `avisoVuelveALasListas`, `ignoradosDe` de
`./porPasar`. Dentro del componente:

```tsx
  const ignorar = useIgnorarVuelta()
  const deshacerIgnorado = useDeshacerIgnorado()
  const ocupado = marcar.isPending || deshacer.isPending || descartar.isPending || recuperar.isPending || ignorar.isPending || deshacerIgnorado.isPending
  const conIgnorados = (cual: 'color' | 'grado' | 'revisar') => ({
    ignorados: ignoradosDe(t, cual),
    onIgnorar: (imeiSn: string) => ignorar.mutate({ imeiSn }, { onSuccess: () => avisar({ texto: avisoIgnorado(imeiSn), tipo: 'ok' }) }),
    onDeshacerIgnorado: (imeiSn: string) => deshacerIgnorado.mutate({ imeiSn }, { onSuccess: () => avisar({ texto: avisoVuelveALasListas(imeiSn), tipo: 'ok' }) }),
  })
```

(sustituye la línea `const ocupado = …` que ya había) y en las listas:

```tsx
      {elegida === 'color' && <ListaColor filas={t.color} {...conHecho('color')} {...conIgnorados('color')} />}
      {elegida === 'grado' && <ListaGrado filas={t.grado} {...conHecho('grado')} {...conIgnorados('grado')} />}
```

y en `ListaRevisar` añadir `{...conIgnorados('revisar')}` y cambiar `ocupado={revisar.isPending}` por
`ocupado={revisar.isPending || ignorar.isPending || deshacerIgnorado.isPending}`.

- [ ] **Step 6: Comprobar**

Run: `cd $W && npx vitest run src/modules/telefonos 2>&1 | tail -8 && npm run lint 2>&1 | tail -8 && npm run typecheck 2>&1 | tail -8`
Expected: todo en verde, lint y typecheck sin errores.

- [ ] **Step 7: Commit**

```bash
cd $W && git add src/modules/telefonos/stock/CeldaHecho.tsx src/modules/telefonos/stock/ListasPorPasar.tsx src/modules/telefonos/stock/PorPasarNsys.tsx src/modules/telefonos/stock/PorPasarNsys.test.tsx
git commit -m "feat: ignorar en las filas ambar, Salio otra vez y Ver ignorados al pie de Color, Grado y Revisar (0.9.27)"
```

---

### Task 6: E2E, versión, changelog, paridad y novedades

**Files:**
- Create: `$W/tests/e2e/ignorar-nsys.spec.ts`
- Modify: `$W/package.json` (+ `package-lock.json`), `$W/CHANGELOG.md`, `$W/docs/paridad/imeis.md`
- Raíz: Create `docs/novedades/NOVEDADES-v0.9.27.md` (nuevo contenido); `git mv` del actual a `docs/novedades/NOVEDADES-v0.9.28.md`

- [ ] **Step 1: La E2E (solo lee y deja todo como estaba)**

`tests/e2e/ignorar-nsys.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// ignorar-nsys.spec.ts (0.9.27, spec «ignorar» §6): con los móviles que han vuelto de los datos de preprod (la copia de
// producción), el supertécnico ignora uno desde la pantalla (sale de Color, Grado y Revisar, y de sus números, y está en «Ver
// ignorados»), lo deshace y vuelve. Si no hay ningún móvil que haya vuelto, se salta. Solo contra PREPRODUCCIÓN (se niega sin el
// letrero) y lo deja como estaba: sin ignorado, también si falla (las líneas del registro de actividad las borra el guion de
// preprod). El «trabajo nuevo» lo cubren las pruebas del servidor.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

type ConVuelta = { imeiSn: string; vueltoDesde: string | null; hechoEn?: string | null; hechoVolvio?: string | null }
type Tarjetas = { color: ConVuelta[]; grado: ConVuelta[]; revisar: ConVuelta[]; sinGrado: ConVuelta[]; ignorados?: { imeiSn: string; tarjetas: string[] }[] }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

async function pedir(page: Page, metodo: string, ruta: string, cuerpo?: unknown): Promise<{ estado: number; cuerpo: unknown }> {
  return page.evaluate(async ({ metodo, ruta, cuerpo }) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(ruta, {
      method: metodo, headers: { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json' },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    const texto = await res.text()
    return { estado: res.status, cuerpo: texto ? (JSON.parse(texto) as unknown) : null }
  }, { metodo, ruta, cuerpo })
}

const tarjetasApi = async (page: Page) => (await pedir(page, 'GET', '/api/stock-telefonos/por-pasar/tarjetas')).cuerpo as Tarjetas
const enAlguna = (t: Tarjetas, imei: string) => [t.color, t.grado, t.revisar, t.sinGrado].some((l) => l.some((f) => f.imeiSn === imei))

/** Un móvil que ha vuelto de Color o Grado sin marca «Hecho» (ahí está el enlace al lado de «Hecho»), con su tarjeta. */
function elegir(t: Tarjetas): { imei: string; tarjeta: 'Cambio de color' | 'Cambio de grado' } | null {
  const libre = (f: ConVuelta) => f.vueltoDesde && (!f.hechoEn || f.hechoVolvio)
  const color = t.color.find(libre)
  if (color) return { imei: color.imeiSn, tarjeta: 'Cambio de color' }
  const grado = t.grado.find(libre)
  return grado ? { imei: grado.imeiSn, tarjeta: 'Cambio de grado' } : null
}

test.describe('0.9.27: «ignorar» en los vendidos y de baja que salen en ámbar', () => {
  test('ignorar lo quita de las tres tarjetas y de sus números; «Ver ignorados» y «deshacer» lo devuelven', async ({ page }) => {
    const { usuario, clave } = credenciales('SUPERTECNICO')
    await entrar(page, usuario, clave)
    await page.goto('/telefonos/stock')
    await page.getByRole('button', { name: /^Por pasar a NSYS/ }).click()
    const antes = await tarjetasApi(page)
    const elegido = elegir(antes)
    test.skip(!elegido, 'preprod no tiene ningún móvil que haya vuelto en Color o Grado sin marca')
    const { imei, tarjeta } = elegido!
    try {
      await page.getByRole('button', { name: tarjeta, exact: true }).click()
      await page.getByRole('button', { name: `Ignorar ${imei}` }).click()
      await expect(page.getByTestId('aviso-breve')).toContainText(`Ignorado «${imei}»`)
      await expect(page.getByRole('button', { name: `Ignorar ${imei}` })).toHaveCount(0)
      const despues = await tarjetasApi(page)
      expect(enAlguna(despues, imei)).toBe(false)
      const fila = (despues.ignorados ?? []).find((f) => f.imeiSn === imei)
      expect(fila, 'sale en ignorados').toBeTruthy()
      await page.getByRole('button', { name: /^Ver ignorados \(\d+\)$/ }).click()
      await expect(page.getByRole('table', { name: 'Ignorados' })).toContainText(imei)
      await page.getByRole('button', { name: `Deshacer ignorar ${imei}` }).click()
      await expect(page.getByTestId('aviso-breve')).toContainText(`«${imei}» vuelve a las listas`)
      await expect(page.getByRole('button', { name: `Ignorar ${imei}` })).toBeVisible()
      expect(enAlguna(await tarjetasApi(page), imei)).toBe(true)
    } finally {
      await pedir(page, 'DELETE', '/api/stock-telefonos/por-pasar/ignorado', { imeiSn: imei })
    }
  })
})
```

Antes de dar el fichero por bueno, abrir `tests/e2e/revisar-a-mano.spec.ts` y `tests/e2e/descartar-nsys.spec.ts` y copiar de
ahí, si difieren de lo de arriba: cómo se llama a `credenciales(...)`, la ruta de la pantalla del stock de teléfonos y cómo se
abre la pestaña «Por pasar a NSYS». Run: `cd $W && npx tsc --noEmit -p tsconfig.json 2>&1 | tail -5` (o `npm run typecheck`
si las E2E están dentro) y `npx playwright test --list tests/e2e/ignorar-nsys.spec.ts`.
Expected: compila y lista 1 prueba. **No** se corre aquí: se corre contra preprod en la entrega (Task 7).

- [ ] **Step 2: Versión, changelog y paridad**

`cd $W && npm version 0.9.27 --no-git-tag-version` (cambia `package.json` y `package-lock.json`). En `CHANGELOG.md`, encima de
`## [0.9.26]`:

```markdown
## [0.9.27] - 2026-11-?? — «ignorar» en los vendidos que salen en ámbar

- En «Por pasar a NSYS», las filas **ámbar** de «Cambio de color», «Cambio de grado» y «Revisar a mano» (un móvil que NSYS da por vendido o de baja y que el taller ha tenido después) ganan un enlace **«ignorar»**, al lado de «Hecho» como «descartar» (en «Revisar a mano», al lado de «Corregir…»), sin ventana: es para cuando el móvil no ha vuelto (por ejemplo, se reparó antes de enviarlo). Saca el móvil de las tres tarjetas y de sus números.
- **«Ver ignorados (n)»** al pie de cada una de esas tablas: IMEI, modelo, qué dice NSYS («Vendido en NSYS el …» o «De baja en NSYS desde el …»), quién y cuándo, y **«deshacer»**.
- Un ignorado vuelve a salir **sin ámbar** cuando NSYS lo vuelve a tener en la empresa (al apuntar la devolución), con su cambio de color o de grado pendiente, y **en ámbar** con «Salió otra vez: trabajo nuevo después de que … lo ignorara el …» si el taller le hace un trabajo nuevo.
- La ayuda de las tres tarjetas lo explica.
- Base, servidor y web: tabla nueva `Taller_vuelta_ignorada` (migración `migracion-ignorar-vuelta-nsys.sql`), rutas `PUT`/`DELETE /api/stock-telefonos/por-pasar/ignorado` y `ignorados` en las tarjetas. Con el servidor de la 0.9.26 no salen ni «ignorar» funciona: la web va después del servidor.
```

(la fecha se pone el día que se sube a producción). En `docs/paridad/imeis.md`, al final de la sección de la 0.9.22 (tras la
línea de las cajas de la 0.9.26):

```markdown
- **«ignorar» (0.9.27):** en las filas ámbar de Color, Grado y Revisar a mano (vendido o de baja con trabajo del taller después),
  «ignorar» sin ventana las saca de las tres tarjetas hasta que NSYS vuelva a tener el móvil en la empresa o el taller le haga un
  trabajo nuevo («Salió otra vez…»); «Ver ignorados (n)» con «deshacer» al pie de cada una. Tabla `Taller_vuelta_ignorada`; rutas
  `PUT`/`DELETE /api/stock-telefonos/por-pasar/ignorado`.
```

- [ ] **Step 3: Comprobación completa de la web**

Run: `cd $W && npm run check 2>&1 | tail -12`
Expected: lint, typecheck y todos los tests en verde.

- [ ] **Step 4: Commit de la web**

```bash
cd $W && git add tests/e2e/ignorar-nsys.spec.ts package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git commit -m "chore: version 0.9.27, changelog, paridad y E2E de ignorar"
```

- [ ] **Step 5: Novedades en la raíz**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git mv docs/novedades/NOVEDADES-v0.9.27.md docs/novedades/NOVEDADES-v0.9.28.md
```

En `NOVEDADES-v0.9.28.md` (los pedidos): la primera línea pasa a `# Novedades de la versión 0.9.28` y, en la última línea, `eso
llegará en la 0.9.27` pasa a `eso llegará más adelante`. Nuevo `docs/novedades/NOVEDADES-v0.9.27.md`:

```markdown
# Novedades de la versión 0.9.27

---

## 📱 Stock de teléfonos (supertécnicos y administrador)

- En «Por pasar a NSYS», un móvil que NSYS da por **vendido** o **de baja** sale en ámbar si el taller lo ha tenido después
  («Vendido en NSYS el … · el taller lo tiene desde el …: haz antes la devolución en NSYS»). Si es una devolución, apúntala en
  NSYS como siempre. Si **no ha vuelto** (por ejemplo, se reparó justo antes de enviarlo), ahora tiene un enlace **«ignorar»** al
  lado de «Hecho» (en «Revisar a mano», al lado de «Corregir…»).
- «ignorar» no pregunta nada: el móvil sale de «Cambio de color», «Cambio de grado» y «Revisar a mano» a la vez, y deja de contar
  en sus números.
- Al pie de cada una de esas listas, **«Ver ignorados»** enseña los ignorados (quién y cuándo) con **«deshacer»**, por si te
  equivocas.
- Un ignorado vuelve solo: **sin ámbar** cuando se apunta su devolución en NSYS (con su cambio de color o de grado, para subirlo
  bien), y **en ámbar** con «Salió otra vez…» si el taller le hace un trabajo nuevo.
```

```bash
git add docs/novedades/NOVEDADES-v0.9.27.md docs/novedades/NOVEDADES-v0.9.28.md
git commit -m "docs: novedades de la 0.9.27 (ignorar los vendidos en ambar); las de pedidos pasan a la 0.9.28"
```

---

### Task 7: Entrega (no hace nada fuera sin el OK del usuario)

- [ ] **Step 1: Revisión final** de los dos worktrees contra la spec (requesting-code-review o revisión propia): `git diff
  origin/main --stat` en los dos y que no haya ficheros ajenos.
- [ ] **Step 2: Apuntes fuera del repo** (`/c/Users/dev/Documents/Apuntes/`, no es público): en `plan-futuro.md`, una línea
  `- [ ]` de backlog «control propio de las devoluciones olvidadas (p. ej. marca al asignar un IMEI que NSYS da por vendido)»; y
  un guion de despliegue de la 0.9.27 (preprod y producción) siguiendo `Apuntes/despliegue_preprod.md` y
  `Apuntes/despliegue_vdc_produccion.md`: cada bloque empieza con `hostname` (y en producción con `backup-erp.sh`), migración con
  vista previa → servidor → web; avisar de que `docker compose build` tarda ~1 min sin salida.
- [ ] **Step 3: Preguntar al usuario** antes de cualquier push, merge o despliegue (no se hace SSH: los comandos los pega él).
  Tras desplegar en preprod: `E2E_BASE_URL=<preprod> npx playwright test tests/e2e/ignorar-nsys.spec.ts` (credenciales en
  `~/.env.e2e`) y, si hay tiempo, `revisar-a-mano.spec.ts` y `plantillas-nsys.spec.ts`.
- [ ] **Step 4: Memoria**: nota de proyecto de la 0.9.27 y actualizar la de pedidos (pasa a 0.9.28).
