# 0.9.8 — Cliente guardado en cada trabajo: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada trabajo guarda al cerrarse el cliente que tenía su teléfono, el Historial lee ese valor (los abiertos siguen al teléfono), la vista IMEIs muestra el cliente actual del teléfono y lo ya cerrado se rellena desde el registro de actividad con un análisis antes del `COMMIT`.

**Architecture:** Servidor: columna nueva `Reparacion.ID_CLI` (clave ajena a `Cliente`); los cinco cierres de `ReparacionDAO` copian `Telefono.ID_CLI` en la misma sentencia y la reapertura la vacía; las seis consultas de trabajos unen `Cliente` con una sola regla (`JOIN_CLIENTES`) y devuelven además `CLIENTE_TELEFONO`; `ClienteDAO.tieneTelefonos` cuenta también los trabajos. Web: la vista IMEIs agrupa y filtra por `clienteTelefono`. Datos: migración aditiva y un script de relleno en consola (bloques, análisis, transacción con `COMMIT` a mano).

**Tech Stack:** Spring Boot 3.3 + JdbcTemplate + MariaDB 11 (servidor, JUnit 5 + Mockito 5); React + TypeScript (web, Vitest + Testing Library + MSW); openapi-typescript.

**Spec:** `docs/superpowers/specs/2026-10-09-v098-cliente-por-trabajo-design.md` (raíz). Después de esta versión, en cada entorno, la limpieza `docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md`.

## Global Constraints

- Versión **0.9.8**: servidor (`main` `d5b3812`) y web (`main` `bcaa321`, `package.json` en `0.9.7`) se etiquetan juntos al final, **solo con OK del usuario**. La 0.9.7 (con su añadido del selector de color) va a producción **antes** que la 0.9.8.
- La web tiene **otra sesión en curso** en `feature/selector-color` (0.9.7) en el clon normal. La 0.9.8 se trabaja en **worktrees** para no tocar ese clon:
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add /c/Users/dev/Documents/wt/servidor-098 -b feature/cliente-por-trabajo main`
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add /c/Users/dev/Documents/wt/web-098 -b feature/cliente-por-trabajo main`
  y `npm ci` dentro de `wt/web-098`. Todas las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` de este plan se refieren a esos worktrees.
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**. Commits en español sin tildes, prefijo `feat:` / `test:` / `docs:` / `chore:`, **sin** línea `Co-Authored-By`.
- Repos **públicos**: nada de IPs, nombres reales ni datos del taller en código, tests ni docs. Datos de tests sintéticos.
- **Regla de lectura** (una sola, en todas las consultas): `LEFT JOIN Cliente cli ON cli.ID_CLI = CASE WHEN r.FECHA_FIN IS NOT NULL THEN r.ID_CLI ELSE tel.ID_CLI END`. Con `IS NOT NULL` **a propósito**: `getAsignacionesCompletadasHoy` hace `ASIGNACION_SELECT.replace("r.FECHA_FIN IS NULL", "r.FECHA_FIN >= ?")` y un `IS NULL` dentro de la regla metería un segundo `?`.
- **Escritura al cerrar:** `(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = ?)` en los `INSERT`, `(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = Reparacion.IMEI)` en los `UPDATE`. Reabrir: `ID_CLI = NULL`.
- Nombre del campo nuevo de la API: **`clienteTelefono`** (columna SQL `CLIENTE_TELEFONO`), required y nullable como el resto de `ReparacionResumen`.
- Servidor, comandos Maven (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y luego `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro del worktree del servidor.
- Web: `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint` dentro del worktree de la web.
- **Claude no hace SSH**: en preprod y producción prepara los guiones en `Apuntes/` y el usuario ejecuta y pega la salida.

---

### Task 1: Servidor — columna `ID_CLI` y escritura al cerrar

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-cliente-por-trabajo.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql:166-187` (`CREATE TABLE Reparacion`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (constantes tras `FMT_ID`; `insertar`, `insertarCompleta`, `guardarFilaIndividual`, `completar`, `eliminar`, `completarPulido`)
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOClienteTest.java`

**Interfaces:**
- Produces: constantes privadas `CLIENTE_DEL_IMEI` y `CLIENTE_DE_LA_FILA` en `ReparacionDAO` (la Task 2 añade sus constantes justo después); columna `Reparacion.ID_CLI INT NULL` con `fk_reparacion_cliente`.

- [ ] **Step 1: Escribir el test que falla**

Crear `ReparacionDAOClienteTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Cliente guardado en cada trabajo (spec 0.9.8 §4): cada cierre copia el cliente del teléfono; reabrir lo vacía. */
class ReparacionDAOClienteTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final String DEL_IMEI = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = ?)";
    private static final String DE_LA_FILA = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = Reparacion.IMEI)";

    /** Deja pasar las comprobaciones previas; sin solicitudes pendientes, así que la asignación se cierra. */
    private static JdbcTemplate jdbcQueDejaPasar() {
        return mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            Object[] args = invocacion.getArguments();
            String sql = args.length > 0 ? String.valueOf(args[0]) : "";
            if (metodo.equals("queryForObject"))
                return sql.startsWith("SELECT COUNT(*) FROM Reparacion_componente") ? 0 : 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            if (metodo.equals("queryForList") && sql.startsWith("SELECT IMEI, ID_TEC, COMENTARIO_ASIGNACION"))
                return List.of(Map.of("IMEI", IMEI, "ID_TEC", 4));
            return RETURNS_DEFAULTS.answer(invocacion);
        });
    }

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static FilaReparacion uso(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        return f;
    }

    /** Cada update emitido como [sql, p1, p2…] (varargs ya expandidos). */
    private static List<List<Object>> updates(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> Arrays.asList(i.getArguments()))
                .toList();
    }

    /** El único update cuyo SQL empieza por {@code prefijo}. */
    private static List<Object> unico(JdbcTemplate jdbc, String prefijo) {
        List<List<Object>> u = updates(jdbc).stream()
                .filter(a -> String.valueOf(a.get(0)).startsWith(prefijo)).toList();
        assertEquals(1, u.size(), () -> "updates que empiezan por «" + prefijo + "»: " + updates(jdbc));
        return u.get(0);
    }

    @Test void completarGuardaElClienteEnLaPiezaYEnLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(101)), IMEI, 4, null, ASIG, null);
        List<Object> pieza = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(pieza.get(0)).contains("ENTREGADO_POR, ID_CLI)"), pieza.toString());
        assertTrue(String.valueOf(pieza.get(0)).endsWith(DEL_IMEI + ")"), pieza.toString());
        assertEquals(IMEI, pieza.get(pieza.size() - 1));
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA + " WHERE ID_REP = ?", ASIG),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void guardarFilaGuardaElClienteEnLaPieza() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(101)), IMEI, 4, null, ASIG);
        List<Object> pieza = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(pieza.get(0)).endsWith(DEL_IMEI + ")"), pieza.toString());
        assertEquals(IMEI, pieza.get(pieza.size() - 1));
    }

    @Test void terminarAsignacionGuardaElCliente() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).completar(ASIG);
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA
                        + " WHERE ID_REP = ? AND FECHA_FIN IS NULL", ASIG),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void pulidoHechoGuardaElClienteEnElPYEnSuAP() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).completarPulido("AP20260916_1");
        List<Object> p = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(p.get(0)).endsWith(DEL_IMEI + ")"), p.toString());
        assertEquals(IMEI, p.get(p.size() - 1));
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA + " WHERE ID_REP = ?",
                        "AP20260916_1"),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void altaAntiguaGuardaElCliente() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertar(IMEI, 4, LocalDateTime.of(2026, 9, 16, 8, 0), LocalDateTime.of(2026, 9, 16, 9, 0));
        List<Object> r = unico(jdbc, "INSERT INTO Reparacion (");
        assertEquals("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, FECHA_FIN, ID_CLI)"
                + " VALUES (?,?,?,?,?," + DEL_IMEI + ")", r.get(0));
        assertEquals(IMEI, r.get(r.size() - 1));
    }

    @SuppressWarnings("unchecked")
    @Test void reabrirVaciaElClienteGuardado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String idRep = "R20260721_1";
        String idRepOrig = "A20260721_1";
        when(jdbc.query(eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ?"), any(RowMapper.class), eq(idRep)))
                .thenReturn(List.of(IMEI));
        when(jdbc.query(contains("ID_REP_ANTERIOR IS NOT NULL"), any(RowMapper.class), eq(idRep)))
                .thenReturn(List.of(idRepOrig));
        when(jdbc.queryForObject(contains("ID_REP_ANTERIOR = ? AND ID_REP LIKE ?"), eq(Integer.class),
                eq(idRepOrig), eq("R%"), eq(idRep)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(1);

        dao(jdbc).eliminar(idRep);

        verify(jdbc).update("UPDATE Reparacion SET FECHA_FIN = NULL, ID_CLI = NULL"
                + " WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NOT NULL", idRepOrig);
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `mvn -q test -Dtest=ReparacionDAOClienteTest`
Expected: FAIL en los seis tests (los SQL aún no llevan `ID_CLI`).

- [ ] **Step 3: Constantes de escritura**

En `ReparacionDAO.java`, justo después de
`    private static final DateTimeFormatter FMT_ID = DateTimeFormatter.ofPattern("yyyyMMdd");` añadir:

```java

    /** Spec 0.9.8 §4: al cerrar un trabajo se guarda el cliente que tiene su teléfono en ese momento. */
    private static final String CLIENTE_DEL_IMEI   = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = ?)";
    private static final String CLIENTE_DE_LA_FILA = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = Reparacion.IMEI)";
```

- [ ] **Step 4: Los cinco cierres y la reapertura**

En `insertar`, sustituir

```java
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, FECHA_FIN) VALUES (?,?,?,?,?)",
                idRep, imei, idTec, fechaAsig, fechaFin);
```

por

```java
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, FECHA_FIN, ID_CLI)"
                        + " VALUES (?,?,?,?,?," + CLIENTE_DEL_IMEI + ")",
                idRep, imei, idTec, fechaAsig, fechaFin, imei);
```

En `insertarCompleta` **y** en `guardarFilaIndividual` (el bloque es idéntico en los dos: reemplazar las dos apariciones), sustituir

```java
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA, ENTREGADO_AT, ENTREGADO_POR)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?,?,?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna, entrega[0], entrega[1]);
```

por

```java
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA, ENTREGADO_AT, ENTREGADO_POR, ID_CLI)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?,?,?," + CLIENTE_DEL_IMEI + ")",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna, entrega[0], entrega[1], imei);
```

En `insertarCompleta`, sustituir

```java
                jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ?", idAsignacion);
```

por

```java
                jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + CLIENTE_DE_LA_FILA + " WHERE ID_REP = ?", idAsignacion);
```

En `completar`, sustituir

```java
                "UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ? AND FECHA_FIN IS NULL", idRep);
```

por

```java
                "UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + CLIENTE_DE_LA_FILA + " WHERE ID_REP = ? AND FECHA_FIN IS NULL", idRep);
```

En `eliminar`, sustituir

```java
                        "UPDATE Reparacion SET FECHA_FIN = NULL" +
```

por

```java
                        "UPDATE Reparacion SET FECHA_FIN = NULL, ID_CLI = NULL" +
```

En `completarPulido`, sustituir

```java
                "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,?,NOW(),NOW(),?,?)",
                idP, row.get("IMEI"), row.get("ID_TEC"), idAP, row.get("COMENTARIO_ASIGNACION"), row.get("ID_TEC_ASIGNA"));
        jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ?", idAP);
```

por

```java
                "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA, ID_CLI)" +
                " VALUES (?,?,?,?,NOW(),NOW(),?,?," + CLIENTE_DEL_IMEI + ")",
                idP, row.get("IMEI"), row.get("ID_TEC"), idAP, row.get("COMENTARIO_ASIGNACION"), row.get("ID_TEC_ASIGNA"), row.get("IMEI"));
        jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + CLIENTE_DE_LA_FILA + " WHERE ID_REP = ?", idAP);
```

Comprobar que no queda ningún cierre sin cliente:

Run: `grep -n "SET FECHA_FIN = NOW()\|NOW(),NOW()" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
Expected: 6 líneas y todas con `CLIENTE_DEL_IMEI` o `CLIENTE_DE_LA_FILA` (las líneas `VALUES` de las dos `INSERT` de pieza y de la del pulido, y los `UPDATE` de `insertarCompleta`, `completar` y `completarPulido`).

- [ ] **Step 5: Ejecutar el test y la suite**

Run: `mvn -q test -Dtest=ReparacionDAOClienteTest`
Expected: PASS (6 tests).
Run: `mvn -q test`
Expected: todo en verde (los tests de chasis, entrega de glass, urgente y eliminar no comprueban esos SQL literalmente).

- [ ] **Step 6: Migración y `crear_bd.sql`**

Crear `sql/migracion-cliente-por-trabajo.sql`:

```sql
-- 0.9.8: cliente guardado en cada trabajo (spec 2026-10-09-v098-cliente-por-trabajo §3).
-- Aditivo: el servidor 0.9.7 sigue funcionando sobre este esquema (no nombra la columna).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.8. Antes de aplicar, hacer una copia de la base.
-- DESPUÉS del arranque del 0.9.8: relleno-cliente-por-trabajo.sql, en consola, con su análisis y COMMIT a mano.
-- REEJECUCIÓN: idempotente (IF NOT EXISTS de MariaDB).
-- La clave ajena puede bloquear la escritura en Reparacion unos segundos: aplicarla en un momento sin actividad.
USE gestion_reparaciones;

ALTER TABLE Reparacion
    ADD COLUMN IF NOT EXISTS ID_CLI INT NULL,
    ADD CONSTRAINT fk_reparacion_cliente FOREIGN KEY IF NOT EXISTS (ID_CLI) REFERENCES Cliente (ID_CLI);

-- Verificación post: SELECT COUNT(*) FROM Reparacion WHERE ID_CLI IS NOT NULL;  -- 0
```

En `sql/crear_bd.sql`, dentro de `CREATE TABLE Reparacion`, tras `    ENTREGADO_POR        INT          NULL,` añadir

```sql
    ID_CLI               INT          NULL COMMENT 'Cliente con el que se cerró el trabajo (0.9.8); abierto: NULL',
```

y sustituir

```sql
    CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico  (ID_TEC)
);
```

por

```sql
    CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico  (ID_TEC),
    CONSTRAINT fk_reparacion_cliente FOREIGN KEY (ID_CLI)       REFERENCES Cliente  (ID_CLI)
);
```

(`Cliente` se crea antes que `Reparacion` en ese fichero.)

- [ ] **Step 7: Commit**

```bash
git add sql/migracion-cliente-por-trabajo.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOClienteTest.java
git commit -m "feat: cada trabajo guarda al cerrarse el cliente de su telefono"
```

---

### Task 2: Servidor — lectura con la regla, `clienteTelefono` y borrado de clientes

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (constantes de lectura; las seis consultas; `GROUP BY`; `RESUMEN_MAPPER`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java:38` y `:125-126`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ClienteDAO.java` (`tieneTelefonos`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ClienteController.java` (mensaje del 409 de `borrar`)
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (nullabilidad de `ReparacionResumen`)
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOClienteLecturaTest.java`
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ClienteDAOTieneTelefonosTest.java`

**Interfaces:**
- Consumes: `CLIENTE_DEL_IMEI` / `CLIENTE_DE_LA_FILA` (Task 1), para colocar detrás las constantes nuevas.
- Produces: `ReparacionResumen.getClienteTelefono()` / `setClienteTelefono(String)`; propiedad `clienteTelefono` (required, nullable) en el contrato; `target/openapi.json` regenerado para la Task 3.

- [ ] **Step 1: Escribir los tests que fallan**

Crear `ReparacionDAOClienteLecturaTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Regla de lectura (spec 0.9.8 §5): abierto → cliente del teléfono; cerrado → el guardado. Y el del teléfono aparte. */
class ReparacionDAOClienteLecturaTest {

    private static final String JOIN =
            " LEFT JOIN Cliente cli ON cli.ID_CLI = CASE WHEN r.FECHA_FIN IS NOT NULL THEN r.ID_CLI ELSE tel.ID_CLI END" +
            " LEFT JOIN Cliente cliTel ON cliTel.ID_CLI = tel.ID_CLI";
    private static final Timestamp CORTE = Timestamp.valueOf("2026-10-09 00:00:00");

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    /** SQL de cada jdbc.query emitido, en orden. */
    private static List<String> consultas(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query"))
                .map(i -> (String) i.getArgument(0))
                .toList();
    }

    @Test void todasLasConsultasDeTrabajosAplicanLaReglaYDevuelvenElClienteDelTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getHistorial(null);
        d.getAsignaciones(null);
        d.getAsignacionesCompletadasHoy(CORTE);   // dos consultas: A y AG
        d.getAsignacionesGlass(null);
        d.getHistorialGlass(null);
        d.getAsignacionesPulido(null);
        d.getHistorialPulido(null);
        List<String> qs = consultas(jdbc);
        assertEquals(8, qs.size());
        for (String q : qs) {
            assertTrue(q.contains(JOIN), q);
            assertTrue(q.contains(" cli.NOMBRE AS CLIENTE, cliTel.NOMBRE AS CLIENTE_TELEFONO"), q);
            assertFalse(q.contains("LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI"), q);
        }
    }

    @Test void completadasHoySigueConUnSoloParametro() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignacionesCompletadasHoy(CORTE);
        List<String> qs = consultas(jdbc);
        assertEquals(2, qs.size());
        for (String q : qs) assertEquals(1, q.chars().filter(c -> c == '?').count(), q);
    }

    @Test void lasConsultasAgrupadasAgrupanTambienPorElClienteDelTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getAsignaciones(null);
        d.getAsignacionesGlass(null);
        d.getAsignacionById("A20261009_1");
        d.getAsignacionGlassById("AG20261009_1");
        for (String q : consultas(jdbc)) assertTrue(q.contains("ta.NOMBRE, cli.NOMBRE, cliTel.NOMBRE"), q);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void elMapperLeeElClienteDelTrabajoYElDelTelefono() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getHistorial(null);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture());
        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf("2026-10-09 08:00:00");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getString("CLIENTE")).thenReturn("Cliente A");
        when(rs.getString("CLIENTE_TELEFONO")).thenReturn("Cliente B");
        ReparacionResumen rr = (ReparacionResumen) mapper.getValue().mapRow(rs, 0);
        assertEquals("Cliente A", rr.getCliente());
        assertEquals("Cliente B", rr.getClienteTelefono());
    }
}
```

Crear `ClienteDAOTieneTelefonosTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Spec 0.9.8 §5: un cliente con teléfonos o con trabajos que lo guardaron no se puede borrar, solo desactivar. */
class ClienteDAOTieneTelefonosTest {

    @Test void cuentaTelefonosYTrabajosConElClienteGuardado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(5), eq(5))).thenReturn(0, 2);
        ClienteDAO dao = new ClienteDAO(jdbc);
        assertFalse(dao.tieneTelefonos(5));
        assertTrue(dao.tieneTelefonos(5));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(sql.capture(), eq(Integer.class), eq(5), eq(5));
        assertTrue(sql.getValue().contains("FROM Telefono WHERE ID_CLI = ?"), sql.getValue());
        assertTrue(sql.getValue().contains("FROM Reparacion WHERE ID_CLI = ?"), sql.getValue());
    }
}
```

En `OpenApiContractTest.java`, tras la línea
`        assertTrue(resumen.path("properties").path("glassEntregadoPor").path("nullable").asBoolean(false), "glassEntregadoPor nullable");`
añadir:

```java
        assertTrue(resumen.path("properties").path("clienteTelefono").path("nullable").asBoolean(false),
                "clienteTelefono nullable (spec 0.9.8 §5)");
```

- [ ] **Step 2: Ejecutar y comprobar que fallan**

Run: `mvn -q test -Dtest='ReparacionDAOClienteLecturaTest,ClienteDAOTieneTelefonosTest'`
Expected: no compila (`getClienteTelefono` no existe). Tras el Step 3 del modelo, los tests fallan por los SQL.

- [ ] **Step 3: Modelo**

En `ReparacionResumen.java`, tras `    @Schema(nullable = true) private String        cliente;` añadir:

```java
    /** Cliente actual del teléfono (spec 0.9.8 §5): la vista IMEIs es por teléfono. {@code cliente} es el del trabajo. */
    @Schema(nullable = true) private String        clienteTelefono;
```

y tras `    public void          setCliente(String cliente)           { this.cliente = cliente; }` añadir:

```java
    public String        getClienteTelefono()                 { return clienteTelefono; }
    public void          setClienteTelefono(String v)         { this.clienteTelefono = v; }
```

- [ ] **Step 4: Constantes de lectura, consultas y mapper**

En `ReparacionDAO.java`, justo después de la constante `CLIENTE_DE_LA_FILA` (Task 1), añadir:

```java

    /** Spec 0.9.8 §2: el cliente de un trabajo abierto es el de su teléfono; el de uno cerrado, el guardado al cerrarlo.
     *  Con IS NOT NULL a propósito: getAsignacionesCompletadasHoy reescribe "r.FECHA_FIN IS NULL" en ASIGNACION_SELECT. */
    private static final String JOIN_CLIENTES =
            " LEFT JOIN Cliente cli ON cli.ID_CLI = CASE WHEN r.FECHA_FIN IS NOT NULL THEN r.ID_CLI ELSE tel.ID_CLI END" +
            " LEFT JOIN Cliente cliTel ON cliTel.ID_CLI = tel.ID_CLI";
    private static final String COLUMNAS_CLIENTE = " cli.NOMBRE AS CLIENTE, cliTel.NOMBRE AS CLIENTE_TELEFONO";
```

(Tienen que ir antes de `HISTORIAL_SELECT`: Java no deja usar una constante estática antes de declararla.)

Tres reemplazos en todo el fichero:
1. `" cli.NOMBRE AS CLIENTE" +` → `COLUMNAS_CLIENTE +` (**6** apariciones: `HISTORIAL_SELECT`, `ASIGNACION_SELECT`, `ASIGNACION_PULIDO_SELECT`, `HISTORIAL_PULIDO_SELECT`, `GLASS_ASIGNACION_SELECT`, `GLASS_HISTORIAL_SELECT`).
2. `" LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +` → `JOIN_CLIENTES +` (**6** apariciones, las mismas consultas).
3. `ta.NOMBRE, cli.NOMBRE` → `ta.NOMBRE, cli.NOMBRE, cliTel.NOMBRE` (**8** apariciones: los `GROUP BY` de `getAsignaciones`, `getAsignacionesCompletadasHoy` ×2, `getAsignacionById`, `getAsignacionesPorImei`, `getAsignacionesGlass`, `getAsignacionesGlassPorImei`, `getAsignacionGlassById`).

Run: `grep -c "COLUMNAS_CLIENTE +\|JOIN_CLIENTES +" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java; grep -c "cli.NOMBRE, cliTel.NOMBRE" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java; grep -c "tel.ID_CLI = cli.ID_CLI" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
Expected: `12`, `8`, `0`.

En `RESUMEN_MAPPER`, tras `        rr.setCliente(rs.getString("CLIENTE"));` añadir:

```java
        rr.setClienteTelefono(rs.getString("CLIENTE_TELEFONO"));
```

(Todas las consultas que usan el mapper salen de esas seis constantes, así que la columna siempre está.)

- [ ] **Step 5: Borrado de clientes**

En `ClienteDAO.java`, sustituir

```java
    public boolean tieneTelefonos(int idCli) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Telefono WHERE ID_CLI = ?", Integer.class, idCli);
        return count != null && count > 0;
    }
```

por

```java
    /** Teléfonos con este cliente o trabajos que lo guardaron al cerrarse (spec 0.9.8 §5): con cualquiera de los dos
     *  no se puede borrar (claves ajenas), solo desactivar. La ruta conserva su nombre (tiene-telefonos). */
    public boolean tieneTelefonos(int idCli) {
        Integer count = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM Telefono WHERE ID_CLI = ?) + (SELECT COUNT(*) FROM Reparacion WHERE ID_CLI = ?)",
                Integer.class, idCli, idCli);
        return count != null && count > 0;
    }
```

En `ClienteController.java`, sustituir

```java
                    "El cliente tiene teléfonos asociados; desactívalo en lugar de borrarlo");
```

por

```java
                    "El cliente tiene teléfonos o trabajos asociados; desactívalo en lugar de borrarlo");
```

- [ ] **Step 6: Ejecutar los tests y la suite**

Run: `mvn -q test -Dtest='ReparacionDAOClienteLecturaTest,ClienteDAOTieneTelefonosTest,ClienteControllerTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde, incluido `OpenApiContractTest`; `target/openapi.json` regenerado.
Run: `node -e "const d=require('./target/openapi.json');const p=d.components.schemas.ReparacionResumen;console.log(JSON.stringify(p.properties.clienteTelefono), p.required.includes('clienteTelefono'))"`
Expected: `{"type":"string","nullable":true} true`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java src/main/java/com/reparaciones/servidor/dao/ClienteDAO.java src/main/java/com/reparaciones/servidor/controller/ClienteController.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOClienteLecturaTest.java src/test/java/com/reparaciones/servidor/dao/ClienteDAOTieneTelefonosTest.java
git commit -m "feat: el historial lee el cliente guardado en cada trabajo y la api da el del telefono aparte"
```

---

### Task 3: Web — la vista IMEIs usa el cliente actual del teléfono

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json` y `gestion-reparaciones-web/src/shared/api/schema.d.ts` (regenerados)
- Modify: `gestion-reparaciones-web/src/modules/taller/test/fabrica.ts` (`resumen`)
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/grupoImei.ts:36`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/agrupacion.ts:15-33`
- Test: `gestion-reparaciones-web/src/modules/taller/lib/grupoImei.test.ts`
- Test: `gestion-reparaciones-web/src/modules/taller/imeis/agrupacion.test.ts`

**Interfaces:**
- Consumes: `target/openapi.json` del worktree del servidor (Task 2) con `ReparacionResumen.clienteTelefono: string | null`.
- Produces: `GrupoImei.cliente` = cliente actual del teléfono; `agruparVisibles` y `opcionesCliente` filtran y listan por `clienteTelefono`.

- [ ] **Step 1: Regenerar los tipos**

```bash
node -e "const fs=require('fs');const j=JSON.parse(fs.readFileSync('/c/Users/dev/Documents/wt/servidor-098/target/openapi.json','utf8'));fs.writeFileSync('api/openapi.json',JSON.stringify(j,null,2)+'\n')"
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
grep -n "clienteTelefono" src/shared/api/schema.d.ts
```

Expected: cambian solo esos dos ficheros; `schema.d.ts` gana `clienteTelefono: string | null;` dentro de `ReparacionResumen`. Si el `diff --stat` del `openapi.json` es enorme por formato, comparar las claves de `paths` con `node -e` y seguir (el formato lo fija el `JSON.stringify`).

- [ ] **Step 2: Fábrica de tests**

En `src/modules/taller/test/fabrica.ts`, sustituir el principio de `resumen`

```ts
export function resumen(parcial: Partial<ReparacionResumen> = {}): ReparacionResumen {
  return {
```

por

```ts
export function resumen(parcial: Partial<ReparacionResumen> = {}): ReparacionResumen {
  // Por defecto el cliente del teléfono coincide con el del trabajo, como en un trabajo abierto (spec 0.9.8 §2).
  const cliente = parcial.cliente !== undefined ? parcial.cliente : 'AMAZON'
  return {
```

y en el objeto devuelto sustituir `cliente: 'AMAZON',` por `cliente, clienteTelefono: cliente,`.

Run: `npx tsc -b`
Expected: sin errores (todas las filas de tests salen de esta fábrica).

- [ ] **Step 3: Escribir los tests que fallan**

En `src/modules/taller/lib/grupoImei.test.ts`, dentro del `describe`, tras el test «modelo, observación y cliente son el primer valor no vacío…», añadir:

```ts
  it('el cliente del grupo es el actual del teléfono, no el guardado en cada trabajo (0.9.8)', () => {
    const [g] = agruparPorImei([
      rr('R1', { cliente: 'Incidencias A', clienteTelefono: 'WEB' }),
      rr('R2', { cliente: null, clienteTelefono: 'WEB' }),
    ])
    expect(g.cliente).toBe('WEB')
  })
```

En `src/modules/taller/imeis/agrupacion.test.ts`, dentro del primer `describe`, tras el test de `opcionesCliente`, añadir:

```ts
  it('el filtro y las opciones de cliente usan el cliente actual del teléfono (0.9.8)', () => {
    const t = [
      resumen({ idRep: 'R20260901_1', imei: A, cliente: 'Incidencias A', clienteTelefono: 'WEB', fechaAsig: '2026-09-01T08:00:00', fechaFin: '2026-09-01T09:00:00' }),
      resumen({ idRep: 'R20260902_1', imei: B, cliente: 'WEB', clienteTelefono: null, fechaAsig: '2026-09-02T08:00:00', fechaFin: '2026-09-02T09:00:00' }),
    ]
    expect(agruparVisibles(t, f({ clientes: new Set(['WEB']) })).map((g) => g.imei)).toEqual([A])
    expect(agruparVisibles(t, f({ clientes: new Set([SIN_CLIENTE]) })).map((g) => g.imei)).toEqual([B])
    expect(opcionesCliente(t)).toEqual([SIN_CLIENTE, 'WEB'])
  })
```

Run: `npx vitest run src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/agrupacion.test.ts`
Expected: FAIL en los dos tests nuevos.

- [ ] **Step 4: Implementación**

En `src/modules/taller/lib/grupoImei.ts`, sustituir

```ts
    cliente: primero(trabajos.map((t) => t.cliente)),
```

por

```ts
    // La vista IMEIs es por teléfono: su cliente es el actual (el que cambia «Editar cliente»), no el de cada trabajo.
    cliente: primero(trabajos.map((t) => t.clienteTelefono)),
```

En `src/modules/taller/imeis/agrupacion.ts`, sustituir

```ts
  const previos = trabajos.filter((t) => pasaImeis(t.imei, imeis) && pasaFechas(t, f.desde, f.hasta) && pasaCliente(t.cliente, f.clientes))
```

por

```ts
  const previos = trabajos.filter((t) => pasaImeis(t.imei, imeis) && pasaFechas(t, f.desde, f.hasta) && pasaCliente(t.clienteTelefono, f.clientes))
```

y

```ts
/** Clientes presentes en los trabajos cargados, alfabéticos, con "(Sin cliente)" delante si hay trabajos sin cliente. */
export function opcionesCliente(trabajos: ReparacionResumen[]): string[] {
  const nombres = new Set<string>()
  let sinCliente = false
  for (const t of trabajos) {
    if (t.cliente) nombres.add(t.cliente)
    else sinCliente = true
  }
```

por

```ts
/** Clientes actuales de los teléfonos cargados (spec 0.9.8 §5), alfabéticos, con "(Sin cliente)" delante si alguno no
 *  tiene. */
export function opcionesCliente(trabajos: ReparacionResumen[]): string[] {
  const nombres = new Set<string>()
  let sinCliente = false
  for (const t of trabajos) {
    if (t.clienteTelefono) nombres.add(t.clienteTelefono)
    else sinCliente = true
  }
```

- [ ] **Step 5: Ejecutar los tests, tipos, lint y suite**

Run: `npx vitest run src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/agrupacion.test.ts src/modules/taller/imeis/ImeisPage.test.tsx`
Expected: PASS.
Run: `npx tsc -b && npm run lint && npx vitest run`
Expected: todo en verde.

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/taller/test/fabrica.ts src/modules/taller/lib/grupoImei.ts src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/agrupacion.ts src/modules/taller/imeis/agrupacion.test.ts
git commit -m "feat: la vista imeis muestra y filtra por el cliente actual del telefono"
```

---

### Task 4: Script de relleno con análisis

**Files:**
- Create: `gestion-reparaciones-servidor/sql/relleno-cliente-por-trabajo.sql`

**Interfaces:**
- Consumes: la columna de la Task 1 y el servidor 0.9.8 arrancado (su hora de arranque es `@corte`).
- Produces: el script que usan las Tasks 6 y 7, con estas cifras: 0.1 `COLUMNA` 1 y `YA_CON_CLIENTE` 0; 1.1 `FORMATO_RARO` 0; 2.1 por `FUENTE` (`TRABAJOS`, `CON_CLIENTE`, `SIN_CLIENTE`); 3.1 `Changed` = suma de `CON_CLIENTE`; 3.2 `CERRADAS_CON_CLIENTE` = esa misma suma.

- [ ] **Step 1: Crear el script**

Contenido completo de `sql/relleno-cliente-por-trabajo.sql`:

```sql
-- 0.9.8: relleno de Reparacion.ID_CLI en los trabajos ya cerrados (spec 2026-10-09-v098-cliente-por-trabajo §6).
-- Regla: el ultimo apunte ASIGNAR_CLIENTE/CAMBIAR_CLIENTE del IMEI con FECHA <= FECHA_FIN; si el IMEI tiene apuntes
-- pero todos son posteriores, sin cliente; si no tiene ninguno, el cliente actual del telefono; si el apunte nombra un
-- cliente que ya no existe, sin cliente.
--
-- ORDEN: despues de migracion-cliente-por-trabajo.sql y del arranque del servidor 0.9.8. Copia de la base antes.
-- COMO: en la consola de MariaDB, BLOQUE A BLOQUE y en la MISMA sesion (variables y tablas temporales de sesion).
--   Bloques 0-2 solo leen (el analisis completo, sin bloquear nada). El bloque 3 escribe en una transaccion, comprueba
--   lo escrito y se termina A MANO con COMMIT; o ROLLBACK;.
-- Solo ASCII fuera de los comentarios: no depende de como envie la terminal los caracteres.

-- == Bloque 0: parametros y comprobaciones ===================================
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';   -- FECHA_FIN (DATETIME) y Log_Actividad.FECHA (TIMESTAMP) se comparan en UTC
-- Arranque del backend 0.9.8 tal como lo da: docker inspect -f '{{.State.StartedAt}}' reparaciones-backend-1
SET @corte := REPLACE(LEFT('PEGAR_STARTED_AT', 19), 'T', ' ');

-- 0.1 COLUMNA = 1; YA_CON_CLIENTE = 0 (nadie ha rellenado aun lo cerrado antes del corte)
SELECT COUNT(*) AS COLUMNA FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Reparacion' AND COLUMN_NAME = 'ID_CLI';
SELECT @corte AS CORTE, COUNT(*) AS CERRADAS_ANTES_DEL_CORTE, COALESCE(SUM(ID_CLI IS NOT NULL), 0) AS YA_CON_CLIENTE
FROM Reparacion WHERE FECHA_FIN IS NOT NULL AND FECHA_FIN < @corte;

-- == Bloque 1: apuntes de cliente del registro ===============================
DROP TEMPORARY TABLE IF EXISTS apunte_cliente, imei_con_apuntes, relleno;

CREATE TEMPORARY TABLE apunte_cliente (
    IMEI    VARCHAR(30) NOT NULL,
    FECHA   DATETIME    NOT NULL,
    ID_LOG  INT         NOT NULL,
    CLI_TXT VARCHAR(30) NOT NULL,
    ID_CLI  INT         NULL,
    KEY (IMEI, FECHA, ID_LOG)
);
INSERT INTO apunte_cliente (IMEI, FECHA, ID_LOG, CLI_TXT, ID_CLI)
SELECT a.IMEI, a.FECHA, a.ID_LOG, a.CLI_TXT, c.ID_CLI
FROM (SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(l.DETALLE, 'IMEI: ', -1), ',', 1)) AS IMEI,
             l.FECHA, l.ID_LOG,
             TRIM(SUBSTRING_INDEX(l.DETALLE, 'ID_CLI: ', -1)) AS CLI_TXT
      FROM Log_Actividad l
      WHERE l.ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE') AND l.DETALLE LIKE 'IMEI: %, ID_CLI: %') a
LEFT JOIN Cliente c ON a.CLI_TXT REGEXP '^[0-9]+$' AND c.ID_CLI = CAST(a.CLI_TXT AS UNSIGNED);

CREATE TEMPORARY TABLE imei_con_apuntes (IMEI VARCHAR(30) NOT NULL PRIMARY KEY)
SELECT DISTINCT IMEI FROM apunte_cliente;

-- 1.1 Apuntes leidos. FORMATO_RARO = 0 (si no, PARAR: hay apuntes de cliente con otro formato)
SELECT COUNT(*) AS APUNTES, COUNT(DISTINCT IMEI) AS IMEIS,
       COALESCE(SUM(CLI_TXT NOT REGEXP '^[0-9]+$'), 0) AS DEJAN_SIN_CLIENTE,
       COALESCE(SUM(CLI_TXT REGEXP '^[0-9]+$' AND ID_CLI IS NULL), 0) AS CLIENTE_BORRADO,
       MIN(FECHA) AS PRIMERO, MAX(FECHA) AS ULTIMO
FROM apunte_cliente;
SELECT COUNT(*) AS FORMATO_RARO FROM Log_Actividad
WHERE ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE') AND DETALLE NOT LIKE 'IMEI: %, ID_CLI: %';

-- == Bloque 2: propuesta y analisis (solo lectura) ===========================
CREATE TEMPORARY TABLE relleno (
    ID_REP VARCHAR(30) NOT NULL PRIMARY KEY,
    ID_CLI INT         NULL,
    FUENTE VARCHAR(10) NOT NULL
);
INSERT INTO relleno (ID_REP, ID_CLI, FUENTE)
SELECT r.ID_REP,
       CASE WHEN i.IMEI IS NULL THEN t.ID_CLI
            ELSE (SELECT a.ID_CLI FROM apunte_cliente a
                  WHERE a.IMEI = r.IMEI AND a.FECHA <= r.FECHA_FIN
                  ORDER BY a.FECHA DESC, a.ID_LOG DESC LIMIT 1) END,
       CASE WHEN i.IMEI IS NULL THEN 'TELEFONO' ELSE 'REGISTRO' END
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN imei_con_apuntes i ON i.IMEI = r.IMEI
WHERE r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN < @corte AND r.ID_CLI IS NULL;

-- 2.1 Por fuente. TRABAJOS sumados = CERRADAS_ANTES_DEL_CORTE del 0.1
SELECT FUENTE, COUNT(*) AS TRABAJOS, SUM(ID_CLI IS NOT NULL) AS CON_CLIENTE, SUM(ID_CLI IS NULL) AS SIN_CLIENTE
FROM relleno GROUP BY FUENTE;

-- 2.2 Diferencias con lo que se ve hoy (cliente actual del telefono), por pareja de clientes
SELECT COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COALESCE(ct.NOMBRE, '(sin cliente)') AS ACTUAL,
       COUNT(*) AS TRABAJOS
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
LEFT JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
WHERE NOT (x.ID_CLI <=> t.ID_CLI)
GROUP BY GUARDADO, ACTUAL ORDER BY TRABAJOS DESC;

-- 2.3 Muestra: las 25 diferencias mas recientes
SELECT x.ID_REP, r.IMEI, r.FECHA_FIN, x.FUENTE,
       COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COALESCE(ct.NOMBRE, '(sin cliente)') AS ACTUAL
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
LEFT JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
WHERE NOT (x.ID_CLI <=> t.ID_CLI)
ORDER BY r.FECHA_FIN DESC LIMIT 25;

-- 2.4 Detalle de un IMEI de la muestra (cambiar el IMEI y repetir las veces que haga falta)
SET @imei := 'IMEI_DE_LA_MUESTRA';
SELECT a.FECHA, a.CLI_TXT, COALESCE(c.NOMBRE, '(sin cliente)') AS CLIENTE
FROM apunte_cliente a LEFT JOIN Cliente c ON c.ID_CLI = a.ID_CLI
WHERE a.IMEI = @imei ORDER BY a.FECHA;
SELECT r.ID_REP, r.FECHA_FIN, x.FUENTE, COALESCE(c.NOMBRE, '(sin cliente)') AS GUARDADO
FROM Reparacion r LEFT JOIN relleno x ON x.ID_REP = r.ID_REP LEFT JOIN Cliente c ON c.ID_CLI = x.ID_CLI
WHERE r.IMEI = @imei ORDER BY r.FECHA_FIN;

-- 2.5 Telefonos de los clientes de incidencias: con que cliente quedan sus trabajos cerrados
SELECT ct.NOMBRE AS CLIENTE_TELEFONO, COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COUNT(*) AS TRABAJOS
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
WHERE ct.NOMBRE LIKE '%incidencia%'
GROUP BY CLIENTE_TELEFONO, GUARDADO ORDER BY CLIENTE_TELEFONO, TRABAJOS DESC;

-- == Bloque 3: escribir y comprobar (una transaccion) ========================
-- Pegar entero. 3.1 "Changed" = suma de CON_CLIENTE del 2.1; 3.2 = esa misma suma; 3.3 = el 2.5.
-- Terminar a mano con COMMIT; (cuadra) o ROLLBACK; (no cuadra o ha salido cualquier ERROR).
START TRANSACTION;

-- 3.1 UPDATED_AT se conserva: es un dato anadido, no una edicion
UPDATE Reparacion r
JOIN relleno x ON x.ID_REP = r.ID_REP
SET r.ID_CLI = x.ID_CLI, r.UPDATED_AT = r.UPDATED_AT
WHERE r.ID_CLI IS NULL;

-- 3.2 Cerradas antes del corte que ya tienen cliente guardado
SELECT COUNT(*) AS CERRADAS_CON_CLIENTE FROM Reparacion
WHERE FECHA_FIN IS NOT NULL AND FECHA_FIN < @corte AND ID_CLI IS NOT NULL;

-- 3.3 Lo que mostrara el Historial para los telefonos de incidencias (leido ya de Reparacion)
SELECT ct.NOMBRE AS CLIENTE_TELEFONO, COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COUNT(*) AS TRABAJOS
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
LEFT JOIN Cliente cg ON cg.ID_CLI = r.ID_CLI
WHERE r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN < @corte AND ct.NOMBRE LIKE '%incidencia%'
GROUP BY CLIENTE_TELEFONO, GUARDADO ORDER BY CLIENTE_TELEFONO, TRABAJOS DESC;

-- Si cuadra:  COMMIT;
-- Si no:      ROLLBACK;

-- == Bloque 4: despues del COMMIT ============================================
DROP TEMPORARY TABLE IF EXISTS apunte_cliente, imei_con_apuntes, relleno;
```

Por qué así (para quien revise):
- Cada tabla temporal se nombra **una sola vez por sentencia**: MariaDB no deja reabrir una tabla temporal en la misma
  consulta. Por eso `imei_con_apuntes` es aparte y la fuente se decide con ella, no con un segundo `EXISTS`.
- El análisis grande (bloque 2) va **antes** de abrir la transacción y da exactamente lo que se va a escribir (la tabla
  `relleno`), sin bloquear filas; dentro de la transacción (bloque 3) se comprueba lo escrito. Así la transacción de
  producción dura un par de minutos.
- `NOT (x.ID_CLI <=> t.ID_CLI)`: `<=>` compara también los nulos (sin cliente frente a sin cliente = igual).

- [ ] **Step 2: Revisión del script**

Comprobar contra la spec §6: la regla (tres casos y el del cliente borrado), `time_zone`, `UPDATED_AT` conservado, solo
cerrados antes del corte, transacción con `COMMIT` a mano, y que cada tabla temporal sale una vez por sentencia:

Run: `grep -nP '[^\x00-\x7F]' sql/relleno-cliente-por-trabajo.sql | grep -v ':--' || echo SOLO_ASCII`
Expected: `SOLO_ASCII`.

- [ ] **Step 3: Commit**

```bash
git add sql/relleno-cliente-por-trabajo.sql
git commit -m "chore: script de relleno del cliente guardado con analisis antes del commit"
```

---

### Task 5: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión)
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.8]`)
- Create: `docs/novedades/NOVEDADES-v0.9.8.md` (raíz, clon normal)

- [ ] **Step 1: Versión**

En `package.json`, `"version": "0.9.7"` → `"version": "0.9.8"`. En `package-lock.json`, las dos primeras apariciones de
`"version": "0.9.7"` (raíz y `packages[""]`) → `"0.9.8"`.

Run: `grep -n '"version": "0.9.8"' package.json package-lock.json`
Expected: 3 líneas.

- [ ] **Step 2: CHANGELOG**

En `CHANGELOG.md`, antes de `## [0.9.7]`, añadir:

```markdown
## [0.9.8] - 2026-10-09 — Cliente guardado en cada trabajo

- **Cada trabajo recuerda para qué cliente se hizo.** Al terminar una reparación, un glass o un pulido se guarda el cliente que tenía el teléfono en ese momento. Si después se cambia el cliente del teléfono (por ejemplo, porque se vende a otro), los trabajos ya hechos no cambian: el Historial, sus filtros y sus CSV siguen mostrando el cliente con el que se hicieron. Los trabajos pendientes siguen al cliente del teléfono, como siempre (urgente, orden de la cola, barra de Pedidos y predicción de glass sin cambios).
- **Vista IMEIs:** la columna Cliente, su filtro y su CSV muestran el cliente **actual** del teléfono (el que cambia «Editar cliente»).
- **Clientes:** un cliente que aparece en trabajos ya no ofrece «Borrar»; se desactiva.
- Requiere el servidor 0.9.8, la migración `migracion-cliente-por-trabajo.sql` (columna `ID_CLI` en `Reparacion`) antes de desplegar y, después, el relleno `relleno-cliente-por-trabajo.sql` en consola (los trabajos anteriores se completan con el registro de cambios de cliente). Sin cambios en nginx.
```

- [ ] **Step 3: Novedades**

Crear `docs/novedades/NOVEDADES-v0.9.8.md` (en la raíz):

```markdown
# 🎉 Novedades — Versión 0.9.8 (web)

Cada trabajo **recuerda para qué cliente se hizo**.

---

## 🧾 Historial: el cliente de cada trabajo

- Al terminar una reparación, un glass o un pulido, se guarda **el cliente que tenía el teléfono en ese momento**.
- Si después se cambia el cliente del teléfono (por ejemplo, porque se vende a otro), **los trabajos ya hechos no
  cambian**: el Historial sigue mostrando el cliente con el que se hicieron.
- Los trabajos **pendientes** siguen al cliente del teléfono, como siempre.
- Los trabajos anteriores a esta versión se han completado con el registro de cambios de cliente.

---

## 📱 Vista IMEIs

La columna **Cliente** muestra el cliente **actual** del teléfono, el que se cambia con «Editar cliente».

---

## 👥 Clientes

Un cliente que ya aparece en trabajos **no se puede borrar**: se **desactiva** y deja de salir para elegir.
```

- [ ] **Step 4: Suites completas**

Run (worktree del servidor): `mvn -q test`
Expected: todo en verde.
Run (worktree de la web): `npx tsc -b && npm run lint && npx vitest run && npm run build`
Expected: todo en verde y `dist/` generado.

- [ ] **Step 5: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-098 && git add package.json package-lock.json CHANGELOG.md && git commit -m "chore: version 0.9.8 con el cliente guardado en cada trabajo"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.8.md && git commit -m "docs: novedades de la 0.9.8"
```

- [ ] **Step 6: Entrega (con OK del usuario en cada paso)**

Pedir OK antes de cada uno:
1. Merge `--no-ff` de `feature/cliente-por-trabajo` a `main` en servidor y web, desde los clones normales
   (`merge: cliente guardado en cada trabajo (0.9.8)`). En la web, solo cuando su clon normal esté de vuelta en `main`
   (la sesión de `feature/selector-color` terminada y mergeada). Después `git worktree remove` de los dos worktrees y
   borrar las ramas.
2. Push de `main` en los dos repos.
3. Commit de gitlinks en la raíz (`chore: gitlinks servidor y web tras la 0.9.8 (cliente guardado en cada trabajo)`).

---

### Task 6: Preproducción

Requisito: la 0.9.7 completa ya desplegada en preproducción (como en producción después).

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\preprod\v098-preprod.md` (guion, mismo esquema que `v097-preprod.md`)
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (§Registro de sesiones)

- [ ] **Step 1: Escribir el guion `v098-preprod.md`**

Con estos bloques (una sola sesión `ssh preprod`; ante cualquier `ERROR` o cifra distinta, PARAR y pegar la salida):
1. **Estado:** clones en `main` y su último commit, contenedores `Up`, y
   `SELECT COUNT(*) AS columna_ya FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Reparacion' AND COLUMN_NAME = 'ID_CLI';`
   = **0**.
2. **Código, migración y arranque:** `git -C … pull --ff-only` de servidor y web;
   `docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < gestion-reparaciones-servidor/sql/migracion-cliente-por-trabajo.sql`;
   `docker compose up -d --build`; `Started App` sin `ERROR`/`Exception`; y
   `docker inspect -f '{{.State.StartedAt}}' reparaciones-backend-1` (es el `@corte`).
3. **Relleno en consola** (`docker exec -it … mariadb …`, la línea de «Entrar en MariaDB sin escribir la contraseña» de
   `despliegue_vdc_produccion.md`): bloques 0, 1 y 2 de `relleno-cliente-por-trabajo.sql` pegados uno a uno, con la
   salida aquí; Claude revisa el análisis con el usuario (2.4 sobre varios IMEIs de la muestra); bloque 3 y `COMMIT;`
   solo con el OK de los dos; bloque 4.
4. **`init.sql`:** se re-vuelca **después** de la limpieza de incidencias (Step 4), como en `v097-preprod.md` bloque 3
   (`cp sql/init.sql sql/init.sql.bak-antes-v098`, `mariadb-dump … > sql/init.sql`, `Dump completed`, **23**
   `CREATE TABLE`, **3** hashes, **17** anuladas).

- [ ] **Step 2: Ejecutar los bloques 1–3 (usuario) y revisar (Claude)**

Expected: `columna_ya` 0; migración sin error; contenedores `Up`; 0.1 `COLUMNA` 1 y `YA_CON_CLIENTE` 0; 1.1
`FORMATO_RARO` 0; 2.1 con `TRABAJOS` sumados = `CERRADAS_ANTES_DEL_CORTE`; 3.1 `Changed` = suma de `CON_CLIENTE`; 3.2 =
esa suma; 3.3 = 2.5.

- [ ] **Step 3: Pruebas a mano en la web de preprod (usuario, con Claude)**

- `/version.json` = 0.9.8.
- Historial: un trabajo antiguo de un teléfono que cambió de cliente muestra el **anterior** (uno de la muestra 2.3).
- Cerrar una reparación de prueba de un teléfono con cliente; cambiarle el cliente al teléfono en la vista IMEIs: el
  Historial conserva el anterior y la vista IMEIs muestra el nuevo. Deshacer después (borrar la reparación de prueba y
  devolver el cliente).
- Pestaña Clientes: un cliente con trabajos ya no ofrece «Borrar».

- [ ] **Step 4: Limpieza de incidencias en preprod**

`docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md`, su Task 2. Después, el bloque 4 de `init.sql`.

- [ ] **Step 5: Registrar**

Entrada nueva en `Apuntes/despliegue_preprod.md` §Registro de sesiones: versiones desplegadas, `@corte`, cifras de los
bloques 0–3 del relleno, decisiones del análisis, pruebas y la limpieza.

---

### Task 7: Producción

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\prod-v098.md` (guion)
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_vdc_produccion.md` (registro de sesiones)
- Modify: memoria `project_produccion_vdc.md` (y su línea de `MEMORY.md`)

- [ ] **Step 1: Escribir el guion `prod-v098.md`**

Los mismos bloques que en preprod (Task 6, Step 1, puntos 1–3) con `ssh prod`, más, al principio, la copia a mano
(`/usr/local/sbin/backup-erp.sh` y `tail -n 1 /var/log/backup-erp.log` con `OK`). Momento: **sin actividad** (al final
de la jornada): la clave ajena de la migración puede bloquear `Reparacion` unos segundos y la transacción del relleno
bloquea las filas cerradas mientras está abierta. **Vuelta atrás:** servidor y web a los commits de la 0.9.7
(`git -C … checkout <commit>` + `docker compose up -d --build`); la columna puede quedarse (el 0.9.7 no la nombra); el
relleno se deshace con `UPDATE Reparacion SET ID_CLI = NULL, UPDATED_AT = UPDATED_AT WHERE FECHA_FIN < '<corte>';`.

- [ ] **Step 2: Ejecutar (usuario) y revisar (Claude)**

Expected: las mismas cifras-regla que en preprod (Task 6, Step 2). El análisis del bloque 2 se repasa contra el de
preprod (mismo orden de magnitud; las diferencias son lo ocurrido desde el refresco de preprod) y `COMMIT;` en un par
de minutos.

- [ ] **Step 3: Comprobación ligera sin escribir datos**

`/version.json` = 0.9.8; Historial de un IMEI de la muestra 2.3 con el cliente anterior; vista IMEIs con el actual.

- [ ] **Step 4: Limpieza de incidencias en producción**

`docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md`, su Task 3.

- [ ] **Step 5: Registrar, etiquetar y memoria**

Entrada en `Apuntes/despliegue_vdc_produccion.md` (copia, commits desplegados, `@corte`, cifras del relleno, decisiones
del análisis, vuelta atrás). Tags `v0.9.8` en servidor y web **solo cuando el usuario lo diga**. Memoria
`project_produccion_vdc.md`: versión en producción, columna `Reparacion.ID_CLI` y relleno hecho.
