# «Abrir» automático al entregar a glass y atajo «+ Cerrar» — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al entregar un teléfono a glass (o al marcar su llegada) el servidor apunta solo una acción «otro» `Abrir` a quien lo abrió, la borra al deshacer, la web lo cuenta con un aviso breve no modal con barra que encoge, y el formulario ofrece el atajo «+ Cerrar» cuando el IMEI tiene más aperturas que cierres. Las estadísticas puntúan `abrir` 0,25 y `cerrar` 0,17.

**Architecture:**
- **Servidor:**
  - Columna `Reparacion.ID_REP_ABRIR` (solo filas `AG`): el `R…` del «Abrir» que creó la entrega, para que deshacer borre justo ese.
  - Servicio transaccional nuevo `EntregaGlassService` (entregar, marcar llegada, deshacer) que usa métodos nuevos de `ReparacionDAO` y reutiliza `guardarFilaIndividual` y `eliminar`. El controlador valida como hoy, llama al servicio, anota el log y devuelve `ResultadoEntregaGlass` (200 en vez de 204).
  - `PuntosCalculo` lee la observación de cada pieza: una acción `otro…` que empieza por `abr`/`abi` puntúa `abrir` y por `cerr` puntúa `cerrar`, con caída a `otro` si falta la clave.
- **Web:**
  - Aviso breve global en `shared/ui` (store + host montado en `main.tsx`).
  - Textos del aviso en `lib/entregaGlass.ts`; Mis pendientes los muestra al acabar cada acción.
  - El formulario carga las acciones «otro» del IMEI (reparación + glass) y decide con un selector puro si sale «+ Cerrar».

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5 y Mockito (JdbcTemplate simulado); springdoc para el contrato (`OpenApiContractTest` deja `target/openapi.json`).
- **Web:** React, TypeScript, TanStack Query, Radix, Tailwind v4. Pruebas con Vitest, Testing Library, MSW y Playwright (E2E).

**Spec:** `docs/superpowers/specs/2026-10-13-abrir-automatico-entrega-glass-design.md` (raíz). Prototipo aprobado: `docs/superpowers/specs/assets/2026-10-13-prototipo-abrir-automatico.html`. Versión **0.9.11** (la siguiente tras la 0.9.10; si el usuario decide otra, solo cambia el CHANGELOG). Base: servidor `main` `f00761c`, web `main` `343217b`.

## Global Constraints

- **Ramas y worktrees** (nuevos; hay sesiones en paralelo en `wt/web-0910` y `wt/web-099`, que **no se tocan**):
  - Servidor: rama `feature/abrir-automatico-glass` desde `main`, worktree `C:/Users/dev/Documents/wt/servidor-abrir` (lo crea la Task 1).
  - Web: rama `feature/abrir-automatico-glass` desde `main`, worktree `C:/Users/dev/Documents/wt/web-abrir` (lo crea la Task 5, con `npm ci`).
  - Las rutas de cada tarea son relativas a su worktree (servidor o web). La raíz (`ProgramaReparaciones`) solo se toca en la Task 9.
- **Comandos (Git Bash):**
  - **Servidor:** primero `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`. Después, dentro de `wt/servidor-abrir`, `mvn -q test -Dtest=Clase` o `mvn -q test`.
  - **Web:** dentro de `wt/web-abrir`, `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint`. **Un solo proceso de vitest a la vez** (con carga hay timeouts de 5 s).
  - **Contrato:** tras cambiar el servidor, en la web: `cp /c/Users/dev/Documents/wt/servidor-abrir/target/openapi.json api/openapi.json && npm run api:types:offline`. `schema.d.ts` no se edita a mano. Records de primer nivel (`model/`) conservan su nombre en el esquema.
- **Commits:** en español **sin tildes**, prefijos `feat:` / `fix:` / `test:` / `docs:` / `chore:`, **sin** línea `Co-Authored-By`. Ficheros por nombre (nunca `git add -A`). Nada de push, merge, tag ni despliegue sin OK del usuario.
- **Comentarios del código:** en español normal, con tildes.
- **Repos públicos:** datos de prueba sintéticos («Técnico L», IMEI `351111112222333`…); sin IPs, nombres reales ni secretos.
- **SSH:** Claude no entra en las VMs; prepara los comandos y los ejecuta el usuario.
- **Reglas exactas (spec §2):**
  - Texto de la acción automática: `Abrir`. Atajo: `Cerrar`.
  - Apertura = texto (recortado, sin distinguir mayúsculas) que empieza por `abr` o `abi`. Cierre = empieza por `cerr`.
  - «Ya apuntado a mano»: acción `otro` de **reparación** (`R…`) del técnico al que se apuntaría, en el IMEI, con `FECHA_FIN >=` `FECHA_ASIG` de su `A…`, que sea apertura.
  - Llegada → dueño de la reparación normal abierta **más antigua** del IMEI; sin ninguna → `SIN_NORMAL`.
  - Puntos: `abrir` 0,25 y `cerrar` 0,17; si falta la clave, valor de `otro`.
- **Contrato del servidor:** `PATCH /api/reparaciones/asignaciones/{idRep}/entrega-glass`, `PATCH` y `DELETE /api/reparaciones/asignaciones/{idRep}/llegada` responden **200** con `ResultadoEntregaGlass` `{ abrir: EstadoAbrir | null, idRepAbrir: string | null, tecnicoAbrir: string | null, idRepBorrado: string | null }`, `EstadoAbrir` = `CREADO | YA_EXISTIA | SIN_PIEZA_OTRO | SIN_NORMAL`. Validaciones 422/404/403 sin cambios.
- **Log** (sin acciones nuevas): al detalle actual se añade `, ABRIR: <R…>` (creado) o `, ABRIR: YA_EXISTIA|SIN_PIEZA_OTRO|SIN_NORMAL`; al deshacer, `, ABRIR BORRADO: <R…>` si se borró uno.
- **Textos exactos (web):**
  - Entregar: «Entregado a X · Abierto» (✓) · «Entregado a X · ya estaba abierto» (i) · «Entregado a X · sin «Abrir» (el modelo no tiene pieza otro)» (!). X = dueño de la glass, «glass» si no hay nombre.
  - Llegada: «Llegada registrada · Abierto (Y)» (✓) · «Llegada registrada · ya estaba abierto» (i) · «Llegada registrada · sin «Abrir» (el modelo no tiene pieza otro)» (!) · «Llegada registrada» (i, sin normal). Y = `tecnicoAbrir`, «técnico» si falta.
  - Deshacer: «Entrega deshecha · «Abrir» borrado» / «Llegada deshecha · «Abrir» borrado» (✓), o «Entrega deshecha» / «Llegada deshecha» (i).
  - Botón «+ Cerrar».
- **Aviso breve:** no modal, abajo al centro, fondo azul noche, texto crema, barra amarilla de 4 px que encoge en 4 s; se cierra solo a los 4 s o con un clic; uno nuevo sustituye al anterior. Icono redondo: ✓ `verde-ok`, «i» `aviso-info` (#8FA3C2), «!» `aviso-warn` (#F5B942). `data-testid="aviso-breve"`, `role="status"`.
- **Registro de subagentes:** `.superpowers/sdd/abrir-glass/` en la raíz (gitignored).

---

### Task 1: Servidor — migración y métodos del DAO

**Files:**
- Create: `sql/migracion-abrir-glass.sql`
- Modify: `sql/crear_bd.sql` (tabla `Reparacion` y semilla de `Dificultad_puntos`)
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (métodos nuevos tras `deshacerEntregaGlass`, y el SQL de `deshacerEntregaGlass`)
- Create: `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOAbrirGlassTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java` (`deshacerLimpiaTodasLasGlassAbiertasDelImei`)

**Interfaces:**
- Produces (en `ReparacionDAO`):
  - `public record NormalAbierta(String idRep, int idTec, String nombreTecnico, LocalDateTime fechaAsig, String idRepAnterior)`
  - `public record ComponenteOtro(int idCom, String tipo)`
  - `Optional<NormalAbierta> getNormalAbierta(String idRep)`
  - `Optional<NormalAbierta> getNormalAbiertaMasAntigua(String imei)`
  - `String getModeloTelefono(String imei)` (null si no hay)
  - `List<ComponenteOtro> getComponentesOtro()`
  - `boolean tieneAperturaManual(String imei, int idTec, LocalDateTime desde)`
  - `void marcarAbrir(String imei, String idRepAbrir)`
  - `List<String> getAbrirDeEntrega(String imei)`
  - `deshacerEntregaGlass(String imei)` limpia también `ID_REP_ABRIR`.

- [ ] **Step 1: Crear el worktree del servidor**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add -b feature/abrir-automatico-glass /c/Users/dev/Documents/wt/servidor-abrir main
git -C /c/Users/dev/Documents/wt/servidor-abrir log --oneline -1
```
Expected: `f00761c merge: un proveedor por pedido y solo se mueve lo vivo (0.9.9)` (o el `main` vigente si ha avanzado).

- [ ] **Step 2: Escribir la prueba del DAO (falla)**

Crear `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOAbrirGlassTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Consultas del «Abrir» automático al entregar a glass (spec 2026-10-13). */
class ReparacionDAOAbrirGlassTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void normalAbiertaMasAntiguaMiraSoloReparacionesNormalesAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getNormalAbiertaMasAntigua(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AG%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL"), q);
        assertTrue(q.endsWith(" AND r.IMEI = ? ORDER BY r.FECHA_ASIG ASC LIMIT 1"), q);
        assertTrue(q.contains("r.ID_REP_ANTERIOR"), "lleva la incidencia de la asignación");
    }

    @SuppressWarnings("unchecked")
    @Test void normalAbiertaPorIdFiltraPorEseId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getNormalAbierta("A20261013_1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("A20261013_1"));
        assertTrue(sql.getValue().endsWith(" AND r.ID_REP = ?"), sql.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test void modeloDelTelefonoNuloSiNoExiste() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq("SELECT MODELO FROM Telefono WHERE IMEI = ?"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of())
                .thenReturn(List.of("13pro"));
        assertNull(dao(jdbc).getModeloTelefono(IMEI));
        assertEquals("13pro", dao(jdbc).getModeloTelefono(IMEI));
    }

    @SuppressWarnings("unchecked")
    @Test void componentesOtroSinMirarSiEstanActivos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getComponentesOtro();
        verify(jdbc).query(eq("SELECT ID_COM, TIPO FROM Componente WHERE TIPO LIKE 'otro%'"), any(RowMapper.class));
    }

    @Test void aperturaManualCuentaAbrYAbiDelTecnicoDesdeSuAsignacion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime desde = LocalDateTime.of(2026, 10, 13, 8, 40);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI), eq(7), eq(Timestamp.valueOf(desde))))
                .thenReturn(1)
                .thenReturn(0);
        assertTrue(dao(jdbc).tieneAperturaManual(IMEI, 7, desde));
        assertFalse(dao(jdbc).tieneAperturaManual(IMEI, 7, desde));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(sql.capture(), eq(Integer.class), eq(IMEI), eq(7), eq(Timestamp.valueOf(desde)));
        String q = sql.getValue();
        assertTrue(q.contains("r.IMEI = ? AND r.ID_REP LIKE 'R%' AND r.ID_TEC = ? AND r.FECHA_FIN >= ?"), q);
        assertTrue(q.contains("c.TIPO LIKE 'otro%'"), q);
        assertTrue(q.contains("LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abr%'"), q);
        assertTrue(q.contains("LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abi%'"), q);
    }

    @Test void marcarAbrirLoApuntaEnLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).marcarAbrir(IMEI, "R20261013_101");
        verify(jdbc).update(
                "UPDATE Reparacion SET ID_REP_ABRIR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                "R20261013_101", IMEI);
    }

    @SuppressWarnings("unchecked")
    @Test void abrirDeEntregaSoloDevuelveLosQueSiguenExistiendo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAbrirDeEntrega(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("JOIN Reparacion r ON r.ID_REP = g.ID_REP_ABRIR"), q);
        assertTrue(q.contains("g.IMEI = ? AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL"), q);
    }
}
```

En `ReparacionDAOEntregaGlassTest.java`, sustituir el cuerpo de `deshacerLimpiaTodasLasGlassAbiertasDelImei` por:

```java
    @Test void deshacerLimpiaTodasLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).deshacerEntregaGlass(IMEI);
        verify(jdbc).update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, ID_REP_ABRIR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                IMEI);
    }
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest='ReparacionDAOAbrirGlassTest,ReparacionDAOEntregaGlassTest'`
Expected: FAIL de compilación (`getNormalAbiertaMasAntigua` y el resto no existen).

- [ ] **Step 4: Implementar en `ReparacionDAO`**

Cambiar el SQL de `deshacerEntregaGlass` (también limpia el «Abrir» de la entrega):

```java
    /** Deshace la entrega en TODAS las AG abiertas del IMEI (también olvida el «Abrir» que creó, spec 2026-10-13). */
    public void deshacerEntregaGlass(String imei) {
        jdbc.update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, ID_REP_ABRIR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                imei);
    }
```

Justo después de `deshacerEntregaGlass`, añadir:

```java
    // ── «Abrir» automático al entregar a glass (spec 2026-10-13) ─────────────

    /** Reparación normal abierta a la que se apunta el «Abrir» automático. */
    public record NormalAbierta(String idRep, int idTec, String nombreTecnico, LocalDateTime fechaAsig, String idRepAnterior) {}

    /** Componente del grupo «otro» (uno por modelo). */
    public record ComponenteOtro(int idCom, String tipo) {}

    private static final String NORMAL_ABIERTA_SELECT =
            "SELECT r.ID_REP, r.ID_TEC, t.NOMBRE, r.FECHA_ASIG, r.ID_REP_ANTERIOR FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AG%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL";

    private static final RowMapper<NormalAbierta> NORMAL_ABIERTA_MAPPER = (rs, row) -> new NormalAbierta(
            rs.getString("ID_REP"), rs.getInt("ID_TEC"), rs.getString("NOMBRE"),
            rs.getTimestamp("FECHA_ASIG").toLocalDateTime(), rs.getString("ID_REP_ANTERIOR"));

    /** La reparación normal abierta {@code idRep} (la del que pulsa «Entregar»). */
    public Optional<NormalAbierta> getNormalAbierta(String idRep) {
        return jdbc.query(NORMAL_ABIERTA_SELECT + " AND r.ID_REP = ?", NORMAL_ABIERTA_MAPPER, idRep).stream().findFirst();
    }

    /** La reparación normal abierta más antigua del IMEI (la de la píldora «Rep: …»): a ella va el «Abrir» de una llegada. */
    public Optional<NormalAbierta> getNormalAbiertaMasAntigua(String imei) {
        return jdbc.query(NORMAL_ABIERTA_SELECT + " AND r.IMEI = ? ORDER BY r.FECHA_ASIG ASC LIMIT 1", NORMAL_ABIERTA_MAPPER, imei)
                .stream().findFirst();
    }

    /** Código de modelo del teléfono; {@code null} si no existe o no tiene. */
    public String getModeloTelefono(String imei) {
        List<String> modelos = jdbc.query("SELECT MODELO FROM Telefono WHERE IMEI = ?", (rs, row) -> rs.getString(1), imei);
        return modelos.isEmpty() ? null : modelos.get(0);
    }

    /** Componentes del grupo «otro», sin mirar ACTIVO (igual que el formulario). */
    public List<ComponenteOtro> getComponentesOtro() {
        return jdbc.query("SELECT ID_COM, TIPO FROM Componente WHERE TIPO LIKE 'otro%'",
                (rs, row) -> new ComponenteOtro(rs.getInt("ID_COM"), rs.getString("TIPO")));
    }

    /** ¿El técnico ya apuntó a mano una apertura («abr…»/«abi…») en el IMEI desde que se le asignó la normal? (spec §2.3) */
    public boolean tieneAperturaManual(String imei, int idTec, LocalDateTime desde) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP" +
                " JOIN Componente c ON c.ID_COM = rc.ID_COM" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'R%' AND r.ID_TEC = ? AND r.FECHA_FIN >= ?" +
                " AND c.TIPO LIKE 'otro%'" +
                " AND (LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abr%' OR LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abi%')",
                Integer.class, imei, idTec, Timestamp.valueOf(desde));
        return n != null && n > 0;
    }

    /** Apunta en las AG abiertas del IMEI el «Abrir» que creó su entrega, para que deshacer borre justo ese. */
    public void marcarAbrir(String imei, String idRepAbrir) {
        jdbc.update(
                "UPDATE Reparacion SET ID_REP_ABRIR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                idRepAbrir, imei);
    }

    /** «Abrir» creados por la entrega de las AG abiertas del IMEI que siguen existiendo (uno borrado a mano no sale). */
    public List<String> getAbrirDeEntrega(String imei) {
        return jdbc.query(
                "SELECT DISTINCT g.ID_REP_ABRIR FROM Reparacion g JOIN Reparacion r ON r.ID_REP = g.ID_REP_ABRIR" +
                " WHERE g.IMEI = ? AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL",
                (rs, row) -> rs.getString(1), imei);
    }
```

(`Timestamp`, `LocalDateTime`, `RowMapper`, `List` y `Optional` ya están importados en `ReparacionDAO`.)

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest='ReparacionDAOAbrirGlassTest,ReparacionDAOEntregaGlassTest'`
Expected: PASS.

- [ ] **Step 6: Migración y `crear_bd.sql`**

Crear `sql/migracion-abrir-glass.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-abrir-glass.sql — «Abrir» automático al entregar a glass (spec 2026-10-13)
-- Columna ID_REP_ABRIR en Reparacion (solo filas AG: el R… del «Abrir» que creó su
-- entrega, para que deshacer borre justo ese) y claves de puntos abrir (0,25) y
-- cerrar (0,17). Solo añade: el servidor y la web actuales siguen funcionando.
-- El ALTER NO es idempotente (relanzar = error de columna existente); el INSERT sí.
-- 1) Vista previa: EXISTE_COLUMNA = 0 y CLAVES = 0.
-- 2) Ejecutar la migración.
-- 3) Comprobación: EXISTE_COLUMNA = 1 y CLAVES = 2.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- 1) Vista previa (no modifica nada) ──────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME  = 'ID_REP_ABRIR';
SELECT COUNT(*) AS CLAVES FROM Dificultad_puntos WHERE CLAVE IN ('abrir', 'cerrar');

-- 2) Migración ────────────────────────────────────────────────────────────────
ALTER TABLE Reparacion
    ADD COLUMN ID_REP_ABRIR VARCHAR(30) NULL AFTER ENTREGADO_POR;

INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('abrir',  0.25),
    ('cerrar', 0.17);

-- 3) Comprobación ─────────────────────────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME  = 'ID_REP_ABRIR';
SELECT CLAVE, PUNTOS FROM Dificultad_puntos WHERE CLAVE IN ('abrir', 'cerrar') ORDER BY CLAVE;
```

En `sql/crear_bd.sql`, tabla `Reparacion`, tras la línea `    ENTREGADO_POR        INT          NULL,` añadir:

```sql
    ID_REP_ABRIR         VARCHAR(30)  NULL COMMENT 'Solo filas AG: el R… del «Abrir» que creó su entrega (2026-10-13)',
```

y en la semilla de `Dificultad_puntos` sustituir `    ('pulido',   0.25);` por:

```sql
    ('pulido',   0.25),
    ('abrir',    0.25),
    ('cerrar',   0.17);
```

- [ ] **Step 7: Suite completa y commit**

Run: `mvn -q test`
Expected: PASS (todo en verde).

```bash
git add sql/migracion-abrir-glass.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOAbrirGlassTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java
git commit -m "feat: columna ID_REP_ABRIR y consultas del abrir automatico al entregar a glass"
```

---

### Task 2: Servidor — `EntregaGlassService` y `ResultadoEntregaGlass`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/EstadoAbrir.java`
- Create: `src/main/java/com/reparaciones/servidor/model/ResultadoEntregaGlass.java`
- Create: `src/main/java/com/reparaciones/servidor/service/EntregaGlassService.java`
- Create: `src/test/java/com/reparaciones/servidor/service/EntregaGlassServiceTest.java`

**Interfaces:**
- Consumes (Task 1): `ReparacionDAO.NormalAbierta`, `ComponenteOtro`, `getNormalAbierta`, `getNormalAbiertaMasAntigua`, `getModeloTelefono`, `getComponentesOtro`, `tieneAperturaManual`, `marcarAbrir`, `getAbrirDeEntrega`; existentes `entregarGlass(String, int)`, `deshacerEntregaGlass(String)`, `guardarFilaIndividual(List<FilaReparacion>, String imei, int idTec, String idRepAnterior, String idAsignacion)` → `String`, `eliminar(String)`; `CatalogoPiezas.modelo(String tipo, String prefijo)`.
- Produces:
  - `enum EstadoAbrir { CREADO, YA_EXISTIA, SIN_PIEZA_OTRO, SIN_NORMAL }`
  - `record ResultadoEntregaGlass(EstadoAbrir abrir, String idRepAbrir, String tecnicoAbrir, String idRepBorrado)` con `creado(idRep, tecnico)`, `sinCrear(motivo, tecnico)`, `deshecho(idRepBorrado)`.
  - `EntregaGlassService`: `ResultadoEntregaGlass entregar(String imei, int idTecEntrega, String idAsigNormal)`, `ResultadoEntregaGlass marcarLlegada(String imei, int idTecGlass)`, `ResultadoEntregaGlass deshacer(String imei)`; constante `TEXTO_ABRIR = "Abrir"`.

- [ ] **Step 1: Escribir la prueba del servicio (falla)**

Crear `src/test/java/com/reparaciones/servidor/service/EntregaGlassServiceTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Abrir» automático al entregar a glass (spec 2026-10-13 §2 y §4.1). */
class EntregaGlassServiceTest {

    private static final String IMEI = "351111112222333";
    private static final LocalDateTime ASIG = LocalDateTime.of(2026, 10, 13, 8, 40);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final EntregaGlassService servicio = new EntregaGlassService(dao);
    private final ReparacionDAO.NormalAbierta normal =
            new ReparacionDAO.NormalAbierta("A20261013_1", 7, "Técnico L", ASIG, null);

    private void conPiezaOtro() {
        when(dao.getModeloTelefono(IMEI)).thenReturn("13pro");
        when(dao.getComponentesOtro()).thenReturn(List.of(
                new ReparacionDAO.ComponenteOtro(500, "otroi13"),
                new ReparacionDAO.ComponenteOtro(501, "otroi13pro")));
    }

    @SuppressWarnings("unchecked")
    @Test void entregarSellaYApuntaElAbrirAQuienEntrega() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"))).thenReturn("R20261013_101");

        assertEquals(ResultadoEntregaGlass.creado("R20261013_101", "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));

        verify(dao).entregarGlass(IMEI, 7);
        ArgumentCaptor<List<FilaReparacion>> filas = ArgumentCaptor.forClass(List.class);
        verify(dao).guardarFilaIndividual(filas.capture(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"));
        assertEquals(1, filas.getValue().size());
        FilaReparacion f = filas.getValue().get(0);
        assertEquals(501, f.idCom);            // la pieza «otro» del 13 Pro, no la del 13
        assertEquals(0, f.cantidad);           // neutra de stock
        assertFalse(f.reutilizado);
        assertFalse(f.esSolicitud);
        assertEquals("Abrir", f.observacion);
        assertEquals("otro", f.prefijo);
        verify(dao).marcarAbrir(IMEI, "R20261013_101");
    }

    @Test void conIncidenciaElAbrirLlevaElIdRepAnteriorDeLaAsignacion() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(
                new ReparacionDAO.NormalAbierta("A20261013_1", 7, "Técnico L", ASIG, "R20261001_4")));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), eq("R20261001_4"), eq("A20261013_1"))).thenReturn("R20261013_102");
        assertEquals(EstadoAbrir.CREADO, servicio.entregar(IMEI, 7, "A20261013_1").abrir());
    }

    @Test void conAperturaManualPreviaNoDuplica() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        when(dao.tieneAperturaManual(IMEI, 7, ASIG)).thenReturn(true);
        conPiezaOtro();
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.YA_EXISTIA, "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));
        verify(dao).entregarGlass(IMEI, 7);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
        verify(dao, never()).marcarAbrir(anyString(), anyString());
    }

    @Test void sinModeloOSinPiezaOtroDelModeloEntregaIgualSinAbrir() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        when(dao.getModeloTelefono(IMEI)).thenReturn(null);
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));
        when(dao.getModeloTelefono(IMEI)).thenReturn("7");
        when(dao.getComponentesOtro()).thenReturn(List.of(new ReparacionDAO.ComponenteOtro(500, "otroi13")));
        assertEquals(EstadoAbrir.SIN_PIEZA_OTRO, servicio.entregar(IMEI, 7, "A20261013_1").abrir());
        verify(dao, times(2)).entregarGlass(IMEI, 7);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
    }

    @Test void llegadaFirmaElDeGlassYApuntaElAbrirAlDuenoDeLaNormalMasAntigua() {
        when(dao.getNormalAbiertaMasAntigua(IMEI)).thenReturn(Optional.of(normal));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"))).thenReturn("R20261013_103");
        assertEquals(ResultadoEntregaGlass.creado("R20261013_103", "Técnico L"), servicio.marcarLlegada(IMEI, 9));
        verify(dao).entregarGlass(IMEI, 9);
        verify(dao).marcarAbrir(IMEI, "R20261013_103");
    }

    @Test void llegadaSinNormalAbiertaNoApuntaANadie() {
        when(dao.getNormalAbiertaMasAntigua(IMEI)).thenReturn(Optional.empty());
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null), servicio.marcarLlegada(IMEI, 9));
        verify(dao).entregarGlass(IMEI, 9);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
    }

    @Test void deshacerBorraElAbrirDeLaEntregaAntesDeLimpiarla() {
        when(dao.getAbrirDeEntrega(IMEI)).thenReturn(List.of("R20261013_101"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20261013_101"), servicio.deshacer(IMEI));
        InOrder orden = inOrder(dao);
        orden.verify(dao).eliminar("R20261013_101");
        orden.verify(dao).deshacerEntregaGlass(IMEI);
    }

    @Test void deshacerSinAbrirSoloLimpiaLaEntrega() {
        when(dao.getAbrirDeEntrega(IMEI)).thenReturn(List.of());
        assertEquals(ResultadoEntregaGlass.deshecho(null), servicio.deshacer(IMEI));
        verify(dao, never()).eliminar(anyString());
        verify(dao).deshacerEntregaGlass(IMEI);
    }

    @Test void lasTresOperacionesSonTransaccionales() {
        for (String nombre : List.of("entregar", "marcarLlegada", "deshacer")) {
            Method m = Arrays.stream(EntregaGlassService.class.getMethods())
                    .filter(x -> x.getName().equals(nombre)).findFirst().orElseThrow();
            assertNotNull(m.getAnnotation(Transactional.class), nombre);
        }
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=EntregaGlassServiceTest`
Expected: FAIL de compilación (`EntregaGlassService`, `ResultadoEntregaGlass` y `EstadoAbrir` no existen).

- [ ] **Step 3: Crear los modelos**

`src/main/java/com/reparaciones/servidor/model/EstadoAbrir.java`:

```java
package com.reparaciones.servidor.model;

/** Qué pasó con el «Abrir» automático de una entrega o llegada a glass (spec 2026-10-13 §2.3). */
public enum EstadoAbrir { CREADO, YA_EXISTIA, SIN_PIEZA_OTRO, SIN_NORMAL }
```

`src/main/java/com/reparaciones/servidor/model/ResultadoEntregaGlass.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de entregar o deshacer la entrega y de marcar o deshacer la llegada (spec 2026-10-13 §4.1). Al entregar o marcar la
 * llegada: {@code abrir} dice qué pasó con el «Abrir», {@code idRepAbrir} es el creado y {@code tecnicoAbrir} a quién se apuntó.
 * Al deshacer: {@code abrir}, {@code idRepAbrir} y {@code tecnicoAbrir} nulos e {@code idRepBorrado} el «Abrir» borrado (nulo si
 * no había).
 */
public record ResultadoEntregaGlass(@Schema(nullable = true) EstadoAbrir abrir,
                                    @Schema(nullable = true) String idRepAbrir,
                                    @Schema(nullable = true) String tecnicoAbrir,
                                    @Schema(nullable = true) String idRepBorrado) {

    public static ResultadoEntregaGlass creado(String idRepAbrir, String tecnico) {
        return new ResultadoEntregaGlass(EstadoAbrir.CREADO, idRepAbrir, tecnico, null);
    }

    public static ResultadoEntregaGlass sinCrear(EstadoAbrir motivo, String tecnico) {
        return new ResultadoEntregaGlass(motivo, null, tecnico, null);
    }

    public static ResultadoEntregaGlass deshecho(String idRepBorrado) {
        return new ResultadoEntregaGlass(null, null, null, idRepBorrado);
    }
}
```

- [ ] **Step 4: Crear el servicio**

`src/main/java/com/reparaciones/servidor/service/EntregaGlassService.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import com.reparaciones.servidor.util.CatalogoPiezas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entrega del teléfono a glass con su «Abrir» automático (spec 2026-10-13): sellar la entrega, apuntar la acción «otro»
 * «Abrir» a quien abrió el teléfono y, al deshacer, borrarla. Cada operación va en una transacción: o todo o nada.
 * Las validaciones (tipo, existencia, dueño, firma) las hace el controlador antes de llamar.
 */
@Service
public class EntregaGlassService {

    /** Texto exacto de la acción automática. */
    public static final String TEXTO_ABRIR = "Abrir";

    private final ReparacionDAO dao;

    public EntregaGlassService(ReparacionDAO dao) {
        this.dao = dao;
    }

    /** «Entregar a X»: sella la entrega firmada por {@code idTecEntrega} y apunta el «Abrir» a su reparación normal. */
    @Transactional
    public ResultadoEntregaGlass entregar(String imei, int idTecEntrega, String idAsigNormal) {
        dao.entregarGlass(imei, idTecEntrega);
        return dao.getNormalAbierta(idAsigNormal)
                .map(normal -> registrarAbrir(imei, normal))
                .orElseGet(() -> ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
    }

    /** «Marcar que llegó»: sella la entrega firmada por el de glass y apunta el «Abrir» al dueño de la normal más antigua. */
    @Transactional
    public ResultadoEntregaGlass marcarLlegada(String imei, int idTecGlass) {
        dao.entregarGlass(imei, idTecGlass);
        return dao.getNormalAbiertaMasAntigua(imei)
                .map(normal -> registrarAbrir(imei, normal))
                .orElseGet(() -> ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
    }

    /** «Deshacer entrega» / «Deshacer llegada»: borra el «Abrir» que creó la entrega (si sigue existiendo) y la limpia. */
    @Transactional
    public ResultadoEntregaGlass deshacer(String imei) {
        List<String> abrir = dao.getAbrirDeEntrega(imei);
        for (String idRep : abrir) dao.eliminar(idRep);
        dao.deshacerEntregaGlass(imei);
        return ResultadoEntregaGlass.deshecho(abrir.isEmpty() ? null : abrir.get(0));
    }

    private ResultadoEntregaGlass registrarAbrir(String imei, ReparacionDAO.NormalAbierta normal) {
        if (dao.tieneAperturaManual(imei, normal.idTec(), normal.fechaAsig()))
            return ResultadoEntregaGlass.sinCrear(EstadoAbrir.YA_EXISTIA, normal.nombreTecnico());
        Integer idCom = idComOtro(imei);
        if (idCom == null)
            return ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, normal.nombreTecnico());
        FilaReparacion fila = new FilaReparacion();
        fila.idCom = idCom;
        fila.cantidad = 0;          // neutra de stock, como las acciones del formulario
        fila.reutilizado = false;
        fila.observacion = TEXTO_ABRIR;
        fila.prefijo = "otro";
        String idRep = dao.guardarFilaIndividual(List.of(fila), imei, normal.idTec(), normal.idRepAnterior(), normal.idRep());
        dao.marcarAbrir(imei, idRep);
        return ResultadoEntregaGlass.creado(idRep, normal.nombreTecnico());
    }

    /** Componente «otro» del modelo del teléfono (mismo criterio que el formulario); {@code null} si no hay. */
    private Integer idComOtro(String imei) {
        String modelo = dao.getModeloTelefono(imei);
        if (modelo == null || modelo.isBlank()) return null;
        return dao.getComponentesOtro().stream()
                .filter(c -> modelo.equals(CatalogoPiezas.modelo(c.tipo(), "otro")))
                .map(ReparacionDAO.ComponenteOtro::idCom)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=EntregaGlassServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/EstadoAbrir.java src/main/java/com/reparaciones/servidor/model/ResultadoEntregaGlass.java src/main/java/com/reparaciones/servidor/service/EntregaGlassService.java src/test/java/com/reparaciones/servidor/service/EntregaGlassServiceTest.java
git commit -m "feat: servicio de entrega a glass con abrir automatico y su deshacer"
```

---

### Task 3: Servidor — el controlador usa el servicio, responde 200 y anota el «Abrir»

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (constructor, `actualizarEntregaGlass`, `marcarLlegadaGlass`, `deshacerLlegadaGlass`, helper `detalleAbrir`)
- Modify (reescribir): `src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java`
- Modify (constructor): `FiltroTecnicoControllersTest.java`, `IdempotenciaReparacionControllerTest.java`, `PropiedadAsignacionControllersTest.java`, `ReparacionControllerContadoresTest.java`, `ReparacionControllerCorregirClienteTest.java`, `ReparacionControllerEliminarTest.java` (todos en `src/test/java/com/reparaciones/servidor/controller/`)

**Interfaces:**
- Consumes (Task 2): `EntregaGlassService.entregar/marcarLlegada/deshacer`, `ResultadoEntregaGlass` y sus factorías, `EstadoAbrir`.
- Produces: los tres endpoints devuelven `ResultadoEntregaGlass` con 200; `target/openapi.json` con el esquema `ResultadoEntregaGlass` (lo usa la Task 6).

- [ ] **Step 1: Reescribir la prueba del controlador (falla)**

Sustituir el contenido de `ReparacionControllerEntregaGlassTest.java` por:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import com.reparaciones.servidor.service.EntregaGlassService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReparacionControllerEntregaGlassTest {

    private static final String IMEI = "351111112222333";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final EntregaGlassService servicio = mock(EntregaGlassService.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class),
            mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class), servicio);
    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(42, "tecnico_j", "x", "TECNICO", 7);

    private ReparacionResumen asigDe(int idTec) {
        ReparacionResumen a = mock(ReparacionResumen.class);
        when(a.getIdTec()).thenReturn(idTec);
        when(a.getImei()).thenReturn(IMEI);
        return a;
    }

    /** Invoca y devuelve el status del ResponseStatusException lanzado. */
    private int statusDe(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        return ex.getStatusCode().value();
    }

    private static ReparacionController.EntregaGlassRequest req(boolean entregado) {
        return new ReparacionController.EntregaGlassRequest(entregado);
    }

    /** Asignación A… propia con una glass abierta de «Técnico H». */
    private ReparacionResumen propiaConGlass() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Técnico H")));
        return asig;
    }

    @Test void rechazaIdsQueNoSonReparacionNormal() {
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AG20260828_1", req(true), tecnico)));
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AP20260828_1", req(true), tecnico)));
        verifyNoInteractions(logDao, servicio);
    }

    @Test void asignacionInexistenteOCerradaEs404() {
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico)));
    }

    @Test void soloElDuenoPuedeEntregar() {
        ReparacionResumen asig = asigDe(99);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico)));
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), admin)));
        verifyNoInteractions(servicio);
    }

    @Test void sinGlassAbiertaEs422YNoEscribe() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("Sin glass abierta para este IMEI", ex.getReason());
        verifyNoInteractions(logDao, servicio);
    }

    @Test void entregarLlamaAlServicioDevuelveSuResultadoYAnotaElAbrir() {
        propiaConGlass();
        ResultadoEntregaGlass creado = ResultadoEntregaGlass.creado("R20260828_9", "Técnico J");
        when(servicio.entregar(IMEI, 7, "A20260828_1")).thenReturn(creado);
        assertEquals(creado, ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico));
        verify(servicio, never()).deshacer(anyString());
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR: R20260828_9");
    }

    @Test void entregarSinAbrirAnotaElMotivo() {
        propiaConGlass();
        when(servicio.entregar(IMEI, 7, "A20260828_1"))
                .thenReturn(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, "Técnico J"));
        ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR: SIN_PIEZA_OTRO");
    }

    @Test void deshacerLlamaAlServicioYAnotaElAbrirBorrado() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(7);
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho("R20260828_9"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20260828_9"), ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico));
        verify(servicio, never()).entregar(anyString(), anyInt(), anyString());
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR BORRADO: R20260828_9");
    }

    @Test void deshacerSinAbrirQueBorrarDejaElLogComoAntes() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(7);
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho(null));
        ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico);
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H");
    }

    @Test void deshacerConFirmaAjenaEs403() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(9);   // la marcó el de glass
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerSinEntregaEs422() {
        propiaConGlass();
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerLlegadaSoloConFirmaPropiaYAnotaElAbrirBorrado() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho("R20260828_9"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20260828_9"), ctl.deshacerLlegadaGlass("AG20260828_3", tecnico));
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA deshecha por el tecnico de glass, ABRIR BORRADO: R20260828_9");
    }

    @Test void deshacerLlegadaConFirmaAjenaEs403() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(3);   // la entregó el de rep
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        assertEquals(403, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_3", tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerLlegadaValidaTipoYExistencia() {
        assertEquals(422, statusDe(() -> ctl.deshacerLlegadaGlass("A20260828_1", tecnico)));
        when(dao.getAsignacionAnyById("AG20260828_9")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_9", tecnico)));
    }

    @Test void llegadaRechazaIdsQueNoSonGlass() {
        assertEquals(422, statusDe(() -> ctl.marcarLlegadaGlass("A20260828_1", tecnico)));
        verifyNoInteractions(logDao, servicio);
    }

    @Test void llegadaInexistenteEs404() {
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico)));
    }

    @Test void llegadaSoloDelDueno() {
        ReparacionResumen ajena = asigDe(99);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(ajena));
        assertEquals(403, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void llegadaYaEntregadaEs422() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("La entrega ya está registrada", ex.getReason());
        verifyNoInteractions(servicio);
    }

    @Test void llegadaFirmaElPropioTecnicoYAnotaElAbrir() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResultadoEntregaGlass creado = ResultadoEntregaGlass.creado("R20260828_9", "Técnico L");
        when(servicio.marcarLlegada(IMEI, 7)).thenReturn(creado);
        assertEquals(creado, ctl.marcarLlegadaGlass("AG20260828_3", tecnico));
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass, ABRIR: R20260828_9");
    }

    @Test void llegadaSinNormalAnotaSinNormal() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        when(servicio.marcarLlegada(IMEI, 7)).thenReturn(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
        ctl.marcarLlegadaGlass("AG20260828_3", tecnico);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass, ABRIR: SIN_NORMAL");
    }
}
```

En los otros seis ficheros de prueba, añadir el servicio al constructor de `ReparacionController` (solo a ese: en `FiltroTecnicoControllersTest` el `GlassController` de la línea siguiente también acaba en `mock(CargaAsignacionesService.class));` y no se toca) y su import:

```bash
cd /c/Users/dev/Documents/wt/servidor-abrir/src/test/java/com/reparaciones/servidor/controller
for f in FiltroTecnicoControllersTest IdempotenciaReparacionControllerTest PropiedadAsignacionControllersTest ReparacionControllerContadoresTest ReparacionControllerCorregirClienteTest ReparacionControllerEliminarTest; do
  perl -0pi -e 's/(new ReparacionController\((?:(?!\);).)*?mock\(CargaAsignacionesService\.class\))\);/$1, mock(EntregaGlassService.class));/s' $f.java
  perl -pi -e 's/^(import com\.reparaciones\.servidor\.service\.CargaAsignacionesService;)$/$1\nimport com.reparaciones.servidor.service.EntregaGlassService;/' $f.java
done
grep -c "mock(EntregaGlassService.class)" *.java | grep -v ":0"
```
Expected: cada uno de los 6 ficheros con `1` (y `ReparacionControllerEntregaGlassTest.java` no aparece porque usa el campo `servicio`).

- [ ] **Step 2: Ejecutar y ver que falla**

Run (desde la raíz del worktree): `mvn -q test -Dtest='ReparacionController*Test,FiltroTecnicoControllersTest,IdempotenciaReparacionControllerTest,PropiedadAsignacionControllersTest'`
Expected: FAIL de compilación (el constructor de `ReparacionController` no acepta el servicio).

- [ ] **Step 3: Cambiar el controlador**

En `ReparacionController.java`:

1. Imports: añadir `import com.reparaciones.servidor.service.EntregaGlassService;` junto a `CargaAsignacionesService` (`EstadoAbrir` y `ResultadoEntregaGlass` entran por `com.reparaciones.servidor.model.*`).
2. Campo y constructor: añadir `private final EntregaGlassService entregaGlass;` tras `cargaAsignaciones`; el constructor recibe `EntregaGlassService entregaGlass` como **último** parámetro (tras `CargaAsignacionesService cargaAsignaciones`) y hace `this.entregaGlass = entregaGlass;`.
3. `actualizarEntregaGlass`: quitar `@ResponseStatus(HttpStatus.NO_CONTENT)`, devolver `ResultadoEntregaGlass` y sustituir desde `if (req.entregado()) dao.entregarGlass(...)` hasta el final del método por:

```java
        ResultadoEntregaGlass resultado = req.entregado()
                ? entregaGlass.entregar(asig.getImei(), principal.getIdTec(), idRep)
                : entregaGlass.deshacer(asig.getImei());

        String ids  = glass.stream().map(ReparacionDAO.GlassAbierta::idRep)
                .collect(java.util.stream.Collectors.joining(","));
        String tecs = glass.stream().map(ReparacionDAO.GlassAbierta::nombreTecnico).distinct()
                .collect(java.util.stream.Collectors.joining(","));
        logDao.insertar(principal.getIdUsu(),
                req.entregado() ? "ENTREGAR_GLASS" : "DESHACER_ENTREGA_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", GLASS: " + ids + ", TECNICO_GLASS: " + tecs
                        + detalleAbrir(resultado));
        return resultado;
    }
```

   y añadir a su Javadoc: «Al entregar apunta el «Abrir» automático a quien entrega y al deshacer lo borra (spec 2026-10-13); responde con lo que pasó.»
4. `marcarLlegadaGlass`: quitar `@ResponseStatus(HttpStatus.NO_CONTENT)`, devolver `ResultadoEntregaGlass` y sustituir sus dos últimas sentencias (`dao.entregarGlass(...)` y el `logDao.insertar`) por:

```java
        ResultadoEntregaGlass resultado = entregaGlass.marcarLlegada(asig.getImei(), principal.getIdTec());
        logDao.insertar(principal.getIdUsu(), "ENTREGAR_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", LLEGADA registrada por el tecnico de glass"
                        + detalleAbrir(resultado));
        return resultado;
```

5. `deshacerLlegadaGlass`: quitar `@ResponseStatus(HttpStatus.NO_CONTENT)`, devolver `ResultadoEntregaGlass` y sustituir sus dos últimas sentencias por:

```java
        ResultadoEntregaGlass resultado = entregaGlass.deshacer(asig.getImei());
        logDao.insertar(principal.getIdUsu(), "DESHACER_ENTREGA_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", LLEGADA deshecha por el tecnico de glass"
                        + detalleAbrir(resultado));
        return resultado;
```

6. Tras `deshacerLlegadaGlass`, añadir:

```java
    /** Coletilla del log con lo que pasó con el «Abrir» automático (spec 2026-10-13 §4.1). */
    private static String detalleAbrir(ResultadoEntregaGlass r) {
        if (r.abrir() == null) return r.idRepBorrado() != null ? ", ABRIR BORRADO: " + r.idRepBorrado() : "";
        return ", ABRIR: " + (r.abrir() == EstadoAbrir.CREADO ? r.idRepAbrir() : r.abrir().name());
    }
```

- [ ] **Step 4: Ejecutar y ver que pasa; suite completa**

Run: `mvn -q test`
Expected: PASS, y `target/openapi.json` regenerado. Comprobar el esquema:

```bash
grep -n '"ResultadoEntregaGlass"' -A 30 target/openapi.json | head -40
```
Expected: `ResultadoEntregaGlass` con `abrir` (enum `CREADO`, `YA_EXISTIA`, `SIN_PIEZA_OTRO`, `SIN_NORMAL`, nullable), `idRepAbrir`, `tecnicoAbrir`, `idRepBorrado`, todas required; y las tres rutas con respuesta `200` → `ResultadoEntregaGlass`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java src/test/java/com/reparaciones/servidor/controller/IdempotenciaReparacionControllerTest.java src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerContadoresTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerCorregirClienteTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEliminarTest.java
git commit -m "feat: entregar y marcar la llegada a glass apuntan el abrir y responden con su resultado"
```

---

### Task 4: Servidor — puntos `abrir` y `cerrar`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java` (`FilaPuntos`, `Pieza`, `claveDePieza`, `puntosDeReparacion`, `agregar`)
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (`getEstadisticasPuntos`)
- Modify: `src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEstadisticasPuntosTest.java`

**Interfaces:**
- Produces: `PuntosCalculo.claveDePieza(String tipo, String observacion)` → `String`; `FilaPuntos(..., boolean enJornada, String observacion)` (8 componentes, con los constructores de 6 y 7 de antes); `Pieza(String tipo, int cantidad, String observacion)` (con el de 2 de antes).

- [ ] **Step 1: Escribir las pruebas (fallan)**

Al final de `PuntosCalculoTest.java` (antes de la última `}`) añadir:

```java
    // ── abrir / cerrar (spec 2026-10-13 §2.8) ────────────────────────────────
    private static final Map<String, Double> CON_ABRIR_CERRAR =
            Map.of("otro", 0.25, "abrir", 0.25, "cerrar", 0.17, "bateria", 1.00);

    @Test void unaAccionOtroPuntuaAbrirOCerrarSegunSuTexto() {
        assertEquals("abrir",   PuntosCalculo.claveDePieza("otroi13pro", "Abrir"));
        assertEquals("abrir",   PuntosCalculo.claveDePieza("otroi13pro", " abierto para glass"));
        assertEquals("cerrar",  PuntosCalculo.claveDePieza("otroi13pro", "CERRAR"));
        assertEquals("otro",    PuntosCalculo.claveDePieza("otroi13pro", "Limpieza de conector"));
        assertEquals("otro",    PuntosCalculo.claveDePieza("otroi13pro", null));
        assertEquals("otro",    PuntosCalculo.claveDePieza(null, "Abrir"));      // sin pieza: no es una acción «otro»
        assertEquals("bateria", PuntosCalculo.claveDePieza("bati13", "Abrir"));  // solo las acciones «otro» miran el texto
        assertEquals("otro",    PuntosCalculo.claveDePieza("xyz13", "Abrir"));   // SKU no reconocido: sigue siendo «otro»
    }

    @Test void abrirValeCeroVeinticincoYCerrarCeroDiecisiete() {
        double p = PuntosCalculo.puntosDeReparacion("R20261013_1", List.of(
                new PuntosCalculo.Pieza("otroi13pro", 0, "Abrir"),
                new PuntosCalculo.Pieza("otroi13pro", 0, "Cerrar")), CON_ABRIR_CERRAR);
        assertEquals(0.42, p, 0.001);
    }

    @Test void sinLaClaveAbrirOCerrarPuntuanComoOtro() {
        Map<String, Double> soloOtro = Map.of("otro", 0.25);
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("R1",
                List.of(new PuntosCalculo.Pieza("otroi13", 0, "Cerrar")), soloOtro), 0.001);
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("R1",
                List.of(new PuntosCalculo.Pieza("otroi13", 0, "Abrir")), soloOtro), 0.001);
    }

    @Test void agregarLlevaLaObservacionDeCadaFilaAlCalculo() {
        LocalDate d = LocalDate.of(2026, 10, 13);
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Técnico L", d, "R20261013_1", "111", "otroi13pro", 0, true, "Abrir"),
                new PuntosCalculo.FilaPuntos("Técnico L", d, "R20261013_2", "111", "otroi13pro", 0, true, "Cerrar"));
        PuntoEstadisticaPuntos p = PuntosCalculo.agregar(filas, CON_ABRIR_CERRAR, LocalDate::toString).get(0);
        assertEquals(0.42, p.getPuntos(), 0.001);
    }
```

Crear `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEstadisticasPuntosTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReparacionDAOEstadisticasPuntosTest {

    @SuppressWarnings("unchecked")
    @Test void laConsultaDePuntosLeeLaObservacionDeCadaPieza() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class))
                .getEstadisticasPuntos("Mes", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Map.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(), any());
        assertTrue(sql.getValue().contains("c.TIPO, rc.CANTIDAD, rc.OBSERVACIONES"), sql.getValue());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest='PuntosCalculoTest,ReparacionDAOEstadisticasPuntosTest'`
Expected: FAIL (compilación: `claveDePieza`, `Pieza` de 3 y `FilaPuntos` de 8 no existen).

- [ ] **Step 3: Implementar en `PuntosCalculo`**

Sustituir el record `Pieza` y el record `FilaPuntos` (con sus constructores) por:

```java
    /** Pieza de una reparación; {@code observacion} es el texto de la fila (en las acciones «otro», lo que se hizo). */
    public record Pieza(String tipo, int cantidad, String observacion) {
        public Pieza(String tipo, int cantidad) {
            this(tipo, cantidad, null);
        }
    }

    /** Fila cruda de la query (una por pieza; reparación sin piezas = una fila con tipoPieza null).
     *  {@code fecha} es la del cierre en Madrid; {@code enJornada} = cierre dentro del horario
     *  ({@link Jornada}), la misma para todas las filas de una reparación; {@code observacion}, el texto de la fila. */
    public record FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                             String tipoPieza, Integer cantidad, boolean enJornada, String observacion) {
        /** Sin observación (compatibilidad: usos previos al «Abrir» automático). */
        public FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                          String tipoPieza, Integer cantidad, boolean enJornada) {
            this(tecnico, fecha, idRep, imei, tipoPieza, cantidad, enJornada, null);
        }
        /** Fila en jornada (compatibilidad: tests previos y usos sin horario). */
        public FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                          String tipoPieza, Integer cantidad) {
            this(tecnico, fecha, idRep, imei, tipoPieza, cantidad, true, null);
        }
    }
```

(si el Javadoc original de `FilaPuntos` estaba encima del record, se sustituye por el de arriba). Tras `claveDeTipo`, añadir:

```java
    /**
     * Clave de una pieza (spec 2026-10-13 §2.8): la de su tipo, salvo en las acciones «otro» (SKU {@code otro…}), donde el
     * texto decide: empieza por «abr»/«abi» → {@code abrir}; por «cerr» → {@code cerrar}; cualquier otro → {@code otro}.
     */
    public static String claveDePieza(String tipo, String observacion) {
        String clave = claveDeTipo(tipo);
        if (tipo == null || observacion == null || !tipo.toLowerCase(Locale.ROOT).startsWith("otro")) return clave;
        String texto = observacion.trim().toLowerCase(Locale.ROOT);
        if (texto.startsWith("abr") || texto.startsWith("abi")) return "abrir";
        if (texto.startsWith("cerr")) return "cerrar";
        return clave;
    }

    /** Valor de una pieza; {@code abrir} y {@code cerrar} sin fila en la tabla puntúan como {@code otro}, nunca 0. */
    private static double valorDePieza(Map<String, Double> valores, Pieza p) {
        String clave = claveDePieza(p.tipo(), p.observacion());
        if (("abrir".equals(clave) || "cerrar".equals(clave)) && !valores.containsKey(clave)) clave = "otro";
        return valor(valores, clave);
    }
```

En `puntosDeReparacion`, sustituir `.mapToDouble(p -> valor(valores, claveDeTipo(p.tipo())))` por `.mapToDouble(p -> valorDePieza(valores, p))`. En `agregar`, sustituir `lista.add(new Pieza(f.tipoPieza(), f.cantidad() == null ? 1 : f.cantidad()));` por `lista.add(new Pieza(f.tipoPieza(), f.cantidad() == null ? 1 : f.cantidad(), f.observacion()));`. Si `java.util.Locale` no está importado, añadir `import java.util.Locale;`.

- [ ] **Step 4: Leer la observación en `getEstadisticasPuntos`**

En `ReparacionDAO.getEstadisticasPuntos`, la primera línea del SQL pasa a:

```java
                "SELECT t.NOMBRE, r.FECHA_FIN, r.ID_REP, r.IMEI, c.TIPO, rc.CANTIDAD, rc.OBSERVACIONES" +
```

y el `new PuntosCalculo.FilaPuntos(...)` del mapper pasa a:

```java
                    return new PuntosCalculo.FilaPuntos(
                            rs.getString("NOMBRE"),
                            cierre.toLocalDate(),
                            rs.getString("ID_REP"),
                            rs.getString("IMEI"),
                            rs.getString("TIPO"),
                            (Integer) rs.getObject("CANTIDAD"),
                            Jornada.enJornada(cierre),
                            rs.getString("OBSERVACIONES"));
```

- [ ] **Step 5: Ejecutar y ver que pasa; suite completa**

Run: `mvn -q test -Dtest='PuntosCalculoTest,ReparacionDAOEstadisticasPuntosTest'` y después `mvn -q test`
Expected: PASS ambos.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEstadisticasPuntosTest.java
git commit -m "feat: las acciones abrir y cerrar puntuan con su propia clave"
```

---

### Task 5: Web — aviso breve no modal con barra que encoge

**Files:**
- Create: `src/shared/ui/avisoBreve.ts`
- Create: `src/shared/ui/AvisoBreve.tsx`
- Create: `src/shared/ui/AvisoBreve.test.tsx`
- Modify: `src/shared/styles/tokens.css` (dos tokens al final del bloque `@theme`)
- Modify: `src/shared/styles/globals.css` (keyframes y clase al final)
- Modify: `src/main.tsx` (montar el host)

**Interfaces:**
- Produces: `type TipoAviso = 'ok' | 'info' | 'warn'`, `type AvisoBreve = { texto: string; tipo: TipoAviso }`, `avisar(aviso: AvisoBreve): void`, `onAviso(oyente): () => void` (en `@/shared/ui/avisoBreve`); `AvisoBreveHost` y `DURACION_AVISO_MS = 4000` (en `@/shared/ui/AvisoBreve`).

- [ ] **Step 1: Crear el worktree de la web**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add -b feature/abrir-automatico-glass /c/Users/dev/Documents/wt/web-abrir main
cd /c/Users/dev/Documents/wt/web-abrir && npm ci
```
Expected: worktree en `343217b` (o el `main` vigente) y `npm ci` sin errores.

- [ ] **Step 2: Escribir la prueba (falla)**

`src/shared/ui/AvisoBreve.test.tsx`:

```tsx
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AvisoBreveHost, DURACION_AVISO_MS } from './AvisoBreve'
import { avisar } from './avisoBreve'

describe('AvisoBreve (spec 2026-10-13 §2.7)', () => {
  afterEach(() => vi.useRealTimers())

  it('no pinta nada hasta que llega un aviso', () => {
    render(<AvisoBreveHost />)
    expect(screen.queryByTestId('aviso-breve')).not.toBeInTheDocument()
  })

  it('enseña el texto con su icono y no es un diálogo', () => {
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Entregado a Técnico H · Abierto', tipo: 'ok' }))
    const aviso = screen.getByRole('status')
    expect(aviso).toHaveTextContent('Entregado a Técnico H · Abierto')
    expect(aviso).toHaveAttribute('data-tipo', 'ok')
    expect(aviso).toHaveTextContent('✓')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('«i» para informativo y «!» para el caso de seguridad', () => {
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Entregado a Técnico H · ya estaba abierto', tipo: 'info' }))
    expect(screen.getByTestId('aviso-breve')).toHaveTextContent(/^i/)
    act(() => avisar({ texto: 'Entregado a Técnico H · sin «Abrir» (el modelo no tiene pieza otro)', tipo: 'warn' }))
    expect(screen.getByTestId('aviso-breve')).toHaveTextContent(/^!/)
  })

  it('se cierra solo a los 4 s', () => {
    vi.useFakeTimers()
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Llegada registrada', tipo: 'info' }))
    act(() => vi.advanceTimersByTime(DURACION_AVISO_MS - 1))
    expect(screen.getByTestId('aviso-breve')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(1))
    expect(screen.queryByTestId('aviso-breve')).not.toBeInTheDocument()
  })

  it('uno nuevo sustituye al anterior y vuelve a contar 4 s', () => {
    vi.useFakeTimers()
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Primero', tipo: 'ok' }))
    act(() => vi.advanceTimersByTime(3000))
    act(() => avisar({ texto: 'Segundo', tipo: 'warn' }))
    expect(screen.getAllByTestId('aviso-breve')).toHaveLength(1)
    expect(screen.getByTestId('aviso-breve')).toHaveTextContent('Segundo')
    act(() => vi.advanceTimersByTime(3000))
    expect(screen.getByTestId('aviso-breve')).toHaveTextContent('Segundo')
    act(() => vi.advanceTimersByTime(1000))
    expect(screen.queryByTestId('aviso-breve')).not.toBeInTheDocument()
  })

  it('un clic lo cierra', async () => {
    render(<AvisoBreveHost />)
    act(() => avisar({ texto: 'Entrega deshecha', tipo: 'info' }))
    await userEvent.click(screen.getByTestId('aviso-breve'))
    expect(screen.queryByTestId('aviso-breve')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `npx vitest run src/shared/ui/AvisoBreve.test.tsx`
Expected: FAIL (no existe `./AvisoBreve`).

- [ ] **Step 4: Implementar**

`src/shared/ui/avisoBreve.ts`:

```ts
export type TipoAviso = 'ok' | 'info' | 'warn'
export type AvisoBreve = { texto: string; tipo: TipoAviso }

const oyentes = new Set<(aviso: AvisoBreve) => void>()

/** Aviso breve no modal (spec 2026-10-13 §2.7): lo pinta <AvisoBreveHost />. Se puede llamar desde fuera de React. */
export function avisar(aviso: AvisoBreve) {
  oyentes.forEach((oyente) => oyente(aviso))
}

export function onAviso(oyente: (aviso: AvisoBreve) => void): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}
```

`src/shared/ui/AvisoBreve.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { onAviso, type AvisoBreve, type TipoAviso } from './avisoBreve'

/** Lo que dura el aviso: la barra encoge en este tiempo (clase .aviso-barra, globals.css) y al acabar se cierra. */
export const DURACION_AVISO_MS = 4000

const ICONO: Record<TipoAviso, { simbolo: string; clase: string }> = {
  ok: { simbolo: '✓', clase: 'bg-verde-ok text-superficie' },
  info: { simbolo: 'i', clase: 'bg-aviso-info text-azul-noche' },
  warn: { simbolo: '!', clase: 'bg-aviso-warn text-azul-noche' },
}

/**
 * Aviso breve abajo al centro (spec 2026-10-13 §2.7). No es un modal: no oscurece, no roba el foco y no hay que pulsar nada.
 * Una barra amarilla encoge en 4 s y al acabar se cierra; un clic lo cierra antes; uno nuevo sustituye al anterior y, al
 * cambiar la clave, la barra vuelve a empezar. Los errores siguen yendo por AlertaProvider.
 */
export function AvisoBreveHost() {
  const [actual, setActual] = useState<(AvisoBreve & { clave: number }) | null>(null)
  useEffect(() => onAviso((aviso) => setActual((previo) => ({ ...aviso, clave: (previo?.clave ?? 0) + 1 }))), [])
  useEffect(() => {
    if (actual === null) return
    const temporizador = setTimeout(() => setActual(null), DURACION_AVISO_MS)
    return () => clearTimeout(temporizador)
  }, [actual])
  if (actual === null) return null
  const icono = ICONO[actual.tipo]
  return (
    <div
      key={actual.clave}
      role="status"
      aria-live="polite"
      data-testid="aviso-breve"
      data-tipo={actual.tipo}
      onClick={() => setActual(null)}
      className="fixed bottom-[26px] left-1/2 z-[90] flex max-w-[min(560px,92vw)] min-w-[320px] -translate-x-1/2 cursor-pointer items-center gap-2.5 overflow-hidden rounded-[10px] bg-azul-noche py-3 pr-[18px] pb-[15px] pl-3.5 text-[13px] font-semibold text-crema shadow-lg"
    >
      <span aria-hidden="true" className={`flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full text-[12px] font-extrabold ${icono.clase}`}>
        {icono.simbolo}
      </span>
      <span>{actual.texto}</span>
      <div className="absolute inset-x-0 bottom-0 h-1 bg-superficie/10">
        <i className="aviso-barra block h-full bg-amarillo" />
      </div>
    </div>
  )
}
```

En `src/shared/styles/tokens.css`, antes de la `}` final del bloque `@theme`:

```css
  /* Aviso breve (spec 2026-10-13 §2.7): iconos «i» (informativo) y «!» (ámbar, caso de seguridad). El «✓» usa verde-ok. */
  --color-aviso-info: #8FA3C2;
  --color-aviso-warn: #F5B942;
```

Al final de `src/shared/styles/globals.css`:

```css
/* Aviso breve (spec 2026-10-13 §2.7): la barra amarilla encoge hacia la izquierda en los 4 s que dura el aviso
   (DURACION_AVISO_MS en AvisoBreve.tsx). Cambiar uno obliga a cambiar el otro. */
@keyframes encoge-aviso {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}
.aviso-barra {
  transform-origin: left center;
  animation: encoge-aviso 4s linear forwards;
}
```

En `src/main.tsx`: importar `import { AvisoBreveHost } from '@/shared/ui/AvisoBreve'` y montar el host junto al router:

```tsx
          <AlertaProvider>
            <RouterProvider router={router} />
            <AvisoBreveHost />
          </AlertaProvider>
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `npx vitest run src/shared/ui/AvisoBreve.test.tsx` y después `npx tsc -b` y `npm run lint`
Expected: PASS, sin errores de tipos ni de lint.

- [ ] **Step 6: Commit**

```bash
git add src/shared/ui/avisoBreve.ts src/shared/ui/AvisoBreve.tsx src/shared/ui/AvisoBreve.test.tsx src/shared/styles/tokens.css src/shared/styles/globals.css src/main.tsx
git commit -m "feat: aviso breve no modal con barra que encoge"
```

---

### Task 6: Web — contrato nuevo y avisos de entrega y llegada en Mis pendientes

**Files:**
- Modify (generados): `api/openapi.json`, `src/shared/api/schema.d.ts`
- Modify: `src/shared/api/client.ts` (tipo `ResultadoEntregaGlass`)
- Modify: `src/modules/taller/lib/entregaGlass.ts` y `entregaGlass.test.ts` (textos del aviso)
- Modify: `src/modules/taller/api.ts` y `api.test.tsx` (`useEntregaGlass`, `useMarcarLlegada`, `useDeshacerLlegada`)
- Modify: `src/modules/taller/pendientes/PendientesPage.tsx` y `PendientesPage.test.tsx`
- Modify: `docs/paridad/pendientes.md`

**Interfaces:**
- Consumes: Task 3 (`target/openapi.json` con `ResultadoEntregaGlass`); Task 5 (`avisar`, `AvisoBreve`, `AvisoBreveHost`).
- Produces: `avisoEntrega(r, glassTecnico)`, `avisoLlegada(r)`, `avisoDeshacer(r, llegada)` → `AvisoBreve`; los tres hooks devuelven `ResultadoEntregaGlass | null` (null si el servidor aún responde 204).

- [ ] **Step 1: Regenerar el contrato**

```bash
cp /c/Users/dev/Documents/wt/servidor-abrir/target/openapi.json api/openapi.json && npm run api:types:offline
grep -n "ResultadoEntregaGlass: {" -A 12 src/shared/api/schema.d.ts
```
Expected: `ResultadoEntregaGlass` con `abrir: "CREADO" | "YA_EXISTIA" | "SIN_PIEZA_OTRO" | "SIN_NORMAL" | null`, `idRepAbrir: string | null`, `tecnicoAbrir: string | null`, `idRepBorrado: string | null`. Si `abrir` saliera como referencia a un esquema `EstadoAbrir` aparte, no cambia nada del resto del plan (los literales son los mismos).

En `src/shared/api/client.ts`, junto a `export type ReparacionResumen = …`, añadir:

```ts
/** Respuesta de entregar/deshacer la entrega y de marcar/deshacer la llegada a glass (spec 2026-10-13 §4.1). */
export type ResultadoEntregaGlass = components['schemas']['ResultadoEntregaGlass']
```

- [ ] **Step 2: Escribir las pruebas de los textos (fallan)**

Al final de `src/modules/taller/lib/entregaGlass.test.ts` añadir (y `avisoDeshacer, avisoEntrega, avisoLlegada` al import de `./entregaGlass`, más `import type { ResultadoEntregaGlass } from '@/shared/api/client'`):

```ts
const res = (p: Partial<ResultadoEntregaGlass>): ResultadoEntregaGlass => ({
  abrir: null, idRepAbrir: null, tecnicoAbrir: null, idRepBorrado: null, ...p,
})

describe('avisos del «Abrir» automático (spec 2026-10-13 §2.1–2.4)', () => {
  it('entregar: creado, ya abierto y sin pieza otro', () => {
    expect(avisoEntrega(res({ abrir: 'CREADO', idRepAbrir: 'R20261013_101', tecnicoAbrir: 'Técnico J' }), 'Técnico H'))
      .toEqual({ texto: 'Entregado a Técnico H · Abierto', tipo: 'ok' })
    expect(avisoEntrega(res({ abrir: 'YA_EXISTIA', tecnicoAbrir: 'Técnico J' }), 'Técnico H'))
      .toEqual({ texto: 'Entregado a Técnico H · ya estaba abierto', tipo: 'info' })
    expect(avisoEntrega(res({ abrir: 'SIN_PIEZA_OTRO', tecnicoAbrir: 'Técnico J' }), 'Técnico H'))
      .toEqual({ texto: 'Entregado a Técnico H · sin «Abrir» (el modelo no tiene pieza otro)', tipo: 'warn' })
    expect(avisoEntrega(res({ abrir: 'CREADO' }), null).texto).toBe('Entregado a glass · Abierto')
  })
  it('llegada: dice a quién se apuntó el «Abrir»; sin normal, solo la llegada', () => {
    expect(avisoLlegada(res({ abrir: 'CREADO', idRepAbrir: 'R20261013_102', tecnicoAbrir: 'Técnico L' })))
      .toEqual({ texto: 'Llegada registrada · Abierto (Técnico L)', tipo: 'ok' })
    expect(avisoLlegada(res({ abrir: 'YA_EXISTIA', tecnicoAbrir: 'Técnico L' })))
      .toEqual({ texto: 'Llegada registrada · ya estaba abierto', tipo: 'info' })
    expect(avisoLlegada(res({ abrir: 'SIN_PIEZA_OTRO', tecnicoAbrir: 'Técnico L' })))
      .toEqual({ texto: 'Llegada registrada · sin «Abrir» (el modelo no tiene pieza otro)', tipo: 'warn' })
    expect(avisoLlegada(res({ abrir: 'SIN_NORMAL' }))).toEqual({ texto: 'Llegada registrada', tipo: 'info' })
  })
  it('deshacer: con y sin «Abrir» que borrar', () => {
    expect(avisoDeshacer(res({ idRepBorrado: 'R20261013_101' }), false)).toEqual({ texto: 'Entrega deshecha · «Abrir» borrado', tipo: 'ok' })
    expect(avisoDeshacer(res({}), false)).toEqual({ texto: 'Entrega deshecha', tipo: 'info' })
    expect(avisoDeshacer(res({ idRepBorrado: 'R20261013_101' }), true)).toEqual({ texto: 'Llegada deshecha · «Abrir» borrado', tipo: 'ok' })
    expect(avisoDeshacer(res({}), true)).toEqual({ texto: 'Llegada deshecha', tipo: 'info' })
  })
})
```

En `src/modules/taller/api.test.tsx`, añadir `useDeshacerLlegada, useEntregaGlass, useMarcarLlegada` al import de `./api` y, dentro del `describe('api del taller', …)`:

```tsx
  it('entrega, llegada y deshacer devuelven el resultado del «Abrir»; null con un servidor que aún responde 204', async () => {
    const creado = { abrir: 'CREADO', idRepAbrir: 'R20261013_101', tecnicoAbrir: 'Técnico J', idRepBorrado: null }
    server.use(
      http.patch('*/api/reparaciones/asignaciones/:idRep/entrega-glass', ({ params }) =>
        params.idRep === 'A1' ? HttpResponse.json(creado) : new HttpResponse(null, { status: 204 })),
      http.patch('*/api/reparaciones/asignaciones/:idRep/llegada', () => HttpResponse.json(creado)),
      http.delete('*/api/reparaciones/asignaciones/:idRep/llegada', () =>
        HttpResponse.json({ abrir: null, idRepAbrir: null, tecnicoAbrir: null, idRepBorrado: 'R20261013_101' })),
    )
    const wrapper = envoltorio(SESION_TEC)
    const entrega = renderHook(() => useEntregaGlass(), { wrapper })
    await act(async () => { expect(await entrega.result.current.mutateAsync({ idRep: 'A1', entregado: true })).toEqual(creado) })
    await act(async () => { expect(await entrega.result.current.mutateAsync({ idRep: 'A2', entregado: true })).toBeNull() })
    const llegada = renderHook(() => useMarcarLlegada(), { wrapper })
    await act(async () => { expect(await llegada.result.current.mutateAsync('AG1')).toEqual(creado) })
    const deshacer = renderHook(() => useDeshacerLlegada(), { wrapper })
    await act(async () => { expect((await deshacer.result.current.mutateAsync('AG1'))?.idRepBorrado).toBe('R20261013_101') })
  })
```

- [ ] **Step 3: Ejecutar y ver que fallan**

Run: `npx vitest run src/modules/taller/lib/entregaGlass.test.ts src/modules/taller/api.test.tsx`
Expected: FAIL (`avisoEntrega` no existe; los hooks devuelven el objeto de openapi-fetch, no el resultado).

- [ ] **Step 4: Implementar textos y hooks**

En `src/modules/taller/lib/entregaGlass.ts`: cambiar el import de tipos a `import type { ReparacionResumen, ResultadoEntregaGlass } from '@/shared/api/client'`, añadir `import type { AvisoBreve } from '@/shared/ui/avisoBreve'` y, al final del fichero:

```ts
const SIN_PIEZA_OTRO = 'sin «Abrir» (el modelo no tiene pieza otro)'

/** Aviso tras «Entregar a X» (spec 2026-10-13 §2.1 y §2.3); X es el dueño de la glass, «glass» si no hay nombre. */
export function avisoEntrega(r: ResultadoEntregaGlass, glassTecnico: string | null | undefined): AvisoBreve {
  const base = `Entregado a ${nombre(glassTecnico)}`
  switch (r.abrir) {
    case 'CREADO': return { texto: `${base} · Abierto`, tipo: 'ok' }
    case 'YA_EXISTIA': return { texto: `${base} · ya estaba abierto`, tipo: 'info' }
    case 'SIN_PIEZA_OTRO': return { texto: `${base} · ${SIN_PIEZA_OTRO}`, tipo: 'warn' }
    default: return { texto: base, tipo: 'info' }
  }
}

/** Aviso tras «Marcar que llegó» (§2.2 y §2.3): el «Abrir» va al dueño de la reparación normal. */
export function avisoLlegada(r: ResultadoEntregaGlass): AvisoBreve {
  const base = 'Llegada registrada'
  switch (r.abrir) {
    case 'CREADO': return { texto: `${base} · Abierto (${nombreRep(r.tecnicoAbrir)})`, tipo: 'ok' }
    case 'YA_EXISTIA': return { texto: `${base} · ya estaba abierto`, tipo: 'info' }
    case 'SIN_PIEZA_OTRO': return { texto: `${base} · ${SIN_PIEZA_OTRO}`, tipo: 'warn' }
    default: return { texto: base, tipo: 'info' }
  }
}

/** Aviso tras «Deshacer entrega» (llegada = false) o «Deshacer llegada» (§2.4). */
export function avisoDeshacer(r: ResultadoEntregaGlass, llegada: boolean): AvisoBreve {
  const base = llegada ? 'Llegada deshecha' : 'Entrega deshecha'
  return r.idRepBorrado ? { texto: `${base} · «Abrir» borrado`, tipo: 'ok' } : { texto: base, tipo: 'info' }
}
```

En `src/modules/taller/api.ts`: añadir `type ResultadoEntregaGlass` al import de `@/shared/api/client` y sustituir los tres hooks por:

```ts
/** Entregar/deshacer la entrega y marcar/deshacer la llegada devuelven qué pasó con el «Abrir» automático (spec 2026-10-13
 *  §4.1); null contra un servidor que aún responde 204. Recargan también el Historial de reparaciones, donde aparece o
 *  desaparece el «Abrir». */
const PENDIENTES_E_HISTORIAL_REP = [...PENDIENTES_AMBAS, claveHistorial('REPARACION')] as const

export function useEntregaGlass() {
  const recargar = useRecargarSinEsperar(...PENDIENTES_E_HISTORIAL_REP)
  return useMutation({
    mutationFn: async ({ idRep, entregado }: { idRep: string; entregado: boolean }): Promise<ResultadoEntregaGlass | null> =>
      (await api.PATCH('/api/reparaciones/asignaciones/{idRep}/entrega-glass', { params: { path: { idRep } }, body: { entregado } })).data ?? null,
    onSettled: recargar,
  })
}
export function useMarcarLlegada() {
  const recargar = useRecargarSinEsperar(...PENDIENTES_E_HISTORIAL_REP)
  return useMutation({
    mutationFn: async (idRep: string): Promise<ResultadoEntregaGlass | null> =>
      (await api.PATCH('/api/reparaciones/asignaciones/{idRep}/llegada', { params: { path: { idRep } } })).data ?? null,
    onSettled: recargar,
  })
}
export function useDeshacerLlegada() {
  const recargar = useRecargarSinEsperar(...PENDIENTES_E_HISTORIAL_REP)
  return useMutation({
    mutationFn: async (idRep: string): Promise<ResultadoEntregaGlass | null> =>
      (await api.DELETE('/api/reparaciones/asignaciones/{idRep}/llegada', { params: { path: { idRep } } })).data ?? null,
    onSettled: recargar,
  })
}
```

(`PENDIENTES_E_HISTORIAL_REP` va tras la definición de `PENDIENTES_AMBAS` y `HISTORIALES`, antes de `useEntregaGlass`.)

- [ ] **Step 5: Ejecutar y ver que pasan**

Run: `npx vitest run src/modules/taller/lib/entregaGlass.test.ts src/modules/taller/api.test.tsx`
Expected: PASS.

- [ ] **Step 6: Prueba de Mis pendientes (falla)**

En `src/modules/taller/pendientes/PendientesPage.test.tsx`, importar `import { AvisoBreveHost } from '@/shared/ui/AvisoBreve'` y añadir dentro del `describe` principal:

```tsx
  it('tras «Entregar a» y «Marcar que llegó» sale el aviso breve con lo que pasó con el «Abrir»', async () => {
    server.use(
      http.patch('*/api/reparaciones/asignaciones/A20260916_12/entrega-glass', () =>
        HttpResponse.json({ abrir: 'CREADO', idRepAbrir: 'R20260916_99', tecnicoAbrir: 'Técnico A', idRepBorrado: null })),
    )
    const { unmount } = renderConProviders(<><PendientesPage tipo="REPARACION" /><AvisoBreveHost /></>, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes' })
    await screen.findByText('A20260915_29')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('A20260916_12') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Entregar a Técnico H' }))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Entregado a Técnico H · Abierto')
    unmount()

    const bloqueada = { ...glass(null), idRep: 'AG20260916_1', imei: '351111111111111', normalAbierta: true, normalTecnicoNombre: 'Técnico J' }
    server.use(
      http.get('*/api/glass/asignaciones', () => HttpResponse.json([bloqueada])),
      http.patch('*/api/reparaciones/asignaciones/AG20260916_1/llegada', () =>
        HttpResponse.json({ abrir: 'CREADO', idRepAbrir: 'R20260916_98', tecnicoAbrir: 'Técnico J', idRepBorrado: null })),
    )
    renderConProviders(<><PendientesPage tipo="GLASS" /><AvisoBreveHost /></>, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByText('AG20260916_1') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Marcar que llegó' }))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Llegada registrada · Abierto (Técnico J)')
  })
```

Run: `npx vitest run src/modules/taller/pendientes/PendientesPage.test.tsx`
Expected: FAIL (la página aún no avisa).

- [ ] **Step 7: Avisar desde Mis pendientes**

En `PendientesPage.tsx`: importar `import { avisar } from '@/shared/ui/avisoBreve'` y añadir `avisoDeshacer, avisoEntrega, avisoLlegada` al import de `../lib/entregaGlass`. Sustituir en `acciones` las entradas `entrega`, `llegada` y `deshacerLlegada` por:

```tsx
    // Sin entrega → entregar; con entrega → deshacer. `!` y no `=== null`: una clave ausente (undefined) también es "sin entrega".
    // Al acabar, el aviso breve cuenta qué pasó con el «Abrir» (spec 2026-10-13); con un servidor que aún responde 204 no hay aviso.
    entrega: (r: ReparacionResumen) => {
      const entregar = !r.glassEntregadoAt
      entrega.mutate({ idRep: r.idRep, entregado: entregar }, {
        onSuccess: (res) => { if (res) avisar(entregar ? avisoEntrega(res, r.glassTecnicoNombre) : avisoDeshacer(res, false)) },
      })
    },
    llegada: (r: ReparacionResumen) => llegada.mutate(r.idRep, { onSuccess: (res) => { if (res) avisar(avisoLlegada(res)) } }),
    deshacerLlegada: (r: ReparacionResumen) =>
      deshacerLlegada.mutate(r.idRep, { onSuccess: (res) => { if (res) avisar(avisoDeshacer(res, true)) } }),
```

En `docs/paridad/pendientes.md`, tras la línea del «Menú contextual, además de "Copiar celda"…», añadir:

```markdown
- [x] Tras entregar, marcar la llegada o deshacer, **aviso breve** abajo al centro (web 0.9.11, sin equivalente en el JavaFX): no es un diálogo; barra amarilla que encoge en 4 s y se cierra sola, o con un clic. Textos: «Entregado a X · Abierto» / «· ya estaba abierto» / «· sin «Abrir» (el modelo no tiene pieza otro)»; «Llegada registrada · Abierto (Y)» / «· ya estaba abierto» / «· sin «Abrir» …» / «Llegada registrada»; «Entrega deshecha · «Abrir» borrado» / «Llegada deshecha · «Abrir» borrado» o sin la segunda parte. Contra un servidor que aún responde 204, sin aviso. Las tres acciones recargan también el Historial de reparaciones.
```

- [ ] **Step 8: Ejecutar, tipos, lint y commit**

Run: `npx vitest run src/modules/taller/pendientes/PendientesPage.test.tsx src/modules/taller/lib/entregaGlass.test.ts src/modules/taller/api.test.tsx`, después `npx tsc -b` y `npm run lint`
Expected: PASS y sin errores.

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/taller/lib/entregaGlass.ts src/modules/taller/lib/entregaGlass.test.ts src/modules/taller/api.ts src/modules/taller/api.test.tsx src/modules/taller/pendientes/PendientesPage.tsx src/modules/taller/pendientes/PendientesPage.test.tsx docs/paridad/pendientes.md
git commit -m "feat: aviso breve al entregar, marcar la llegada o deshacer con lo que paso con el abrir"
```

---

### Task 7: Web — atajo «+ Cerrar» en Otras acciones

**Files:**
- Modify: `src/modules/taller/formulario/estado.ts` (tipos, `estadoBase`, `inicialNuevo`, acción `ANADIR_CERRAR`, selectores)
- Modify: `src/modules/taller/formulario/api.ts` (`cargarNuevo`)
- Modify: `src/modules/taller/formulario/OtrasAcciones.tsx`
- Create: `src/modules/taller/formulario/estado.cerrar.test.ts`
- Modify: `src/modules/taller/formulario/OtrasAcciones.test.tsx`, `src/modules/taller/formulario/api.test.tsx`
- Modify: `docs/paridad/formulario.md`, `CHANGELOG.md`

**Interfaces:**
- Produces: `DatosNuevo.accionesImei?: string[]`; `EstadoFormulario.accionesImei: string[]`; `TEXTO_CERRAR = 'Cerrar'`; `esApertura(t: string): boolean`; `esCierre(t: string): boolean`; `atajoCerrarVisible(e: EstadoFormulario): boolean`; acción `{ tipo: 'ANADIR_CERRAR' }`.

- [ ] **Step 1: Escribir las pruebas del estado (fallan)**

`src/modules/taller/formulario/estado.cerrar.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados } from '../test/fabrica'
import { atajoCerrarVisible, esApertura, esCierre, estadoInicial, reducir, type DatosNuevo, type EstadoFormulario } from './estado'

const datos = (accionesImei?: string[]): DatosNuevo => ({
  modo: 'nuevo', idAsignacion: 'A20261013_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: null,
  modeloTelefono: null, accionesImei,
})
/** Formulario con el modelo 13 (tiene componente «otro» en la fábrica): la sección Otras acciones se ve. */
const con13 = (accionesImei?: string[]): EstadoFormulario => reducir(estadoInicial(datos(accionesImei)), { tipo: 'CAMBIAR_MODELO', modelo: '13' })

describe('atajo «+ Cerrar» (spec 2026-10-13 §2.6)', () => {
  it('apertura = «abr»/«abi», cierre = «cerr», sin mirar mayúsculas ni espacios', () => {
    expect(['Abrir', ' abrir telefono', 'Abierto para glass', 'ABRIR'].every(esApertura)).toBe(true)
    expect(['Cerrar', ' cerrado', 'CERRAR teléfono'].every(esCierre)).toBe(true)
    expect(esApertura('Cambio de tornillos')).toBe(false)
    expect(esCierre('Abrir')).toBe(false)
  })
  it('sale con más aperturas que cierres en el IMEI y no con las mismas', () => {
    expect(atajoCerrarVisible(con13(['Abrir']))).toBe(true)
    expect(atajoCerrarVisible(con13(['Abrir', 'Cerrar']))).toBe(false)
    expect(atajoCerrarVisible(con13(['Abrir', 'Cerrar', 'Abrir']))).toBe(true)
    expect(atajoCerrarVisible(con13([]))).toBe(false)
    expect(atajoCerrarVisible(con13(undefined))).toBe(false)
  })
  it('sin la sección Otras acciones (sin modelo) y en edición no sale', () => {
    expect(atajoCerrarVisible(estadoInicial(datos(['Abrir'])))).toBe(false)
    expect(atajoCerrarVisible({ ...con13(['Abrir']), modo: 'editar' })).toBe(false)
  })
  it('«+ Cerrar» añade la línea «Cerrar» y el atajo deja de salir; quitarla lo devuelve', () => {
    let s = reducir(con13(['Abrir']), { tipo: 'ANADIR_CERRAR' })
    expect(s.otros.map((a) => [a.texto, a.origen])).toEqual([['Cerrar', 'nueva']])
    expect(atajoCerrarVisible(s)).toBe(false)
    expect(reducir(s, { tipo: 'ANADIR_CERRAR' })).toBe(s)
    s = reducir(s, { tipo: 'QUITAR_ACCION', id: s.otros[0].id })
    expect(atajoCerrarVisible(s)).toBe(true)
  })
  it('una línea escrita a mano que empieza por «cerr» también cuenta como cierre', () => {
    let s = reducir(con13(['Abrir']), { tipo: 'ANADIR_ACCION' })
    s = reducir(s, { tipo: 'ESCRIBIR_ACCION', id: s.otros[0].id, texto: 'cerrado y probado' })
    expect(atajoCerrarVisible(s)).toBe(false)
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/taller/formulario/estado.cerrar.test.ts`
Expected: FAIL (`atajoCerrarVisible`, `esApertura`, `esCierre` no existen; `accionesImei` no está en `DatosNuevo`).

- [ ] **Step 3: Implementar en `estado.ts`**

1. En `EstadoFormulario`, tras `otros: OtraAccion[]`:

```ts
  /** Textos de las acciones «otro» ya guardadas del IMEI en reparación y glass al abrir: deciden el atajo «+ Cerrar»
   *  (spec 2026-10-13 §2.6). Vacío en edición. */
  accionesImei: string[]
```

2. En `DatosNuevo`, tras `modeloTelefono`:

```ts
  accionesImei?: string[] // acciones «otro» guardadas del IMEI (reparación + glass), para «+ Cerrar»; sin el campo, []
```

3. En `AccionFormulario`, tras `| { tipo: 'ANADIR_ACCION' }`:

```ts
  | { tipo: 'ANADIR_CERRAR' } // atajo «+ Cerrar»: línea nueva con el texto «Cerrar»
```

4. En el objeto que devuelve `estadoBase`, tras `otros: [],`, añadir `accionesImei: [],`. En el `return` de `inicialNuevo`, tras `filas,`, añadir `accionesImei: datos.accionesImei ?? [],`.
5. En el reductor, tras el `case 'ANADIR_ACCION'`:

```ts
    case 'ANADIR_CERRAR':
      return anadirCerrar(estado)
```

6. Tras la función `anadirAccion`:

```ts
/** Atajo «+ Cerrar» (spec 2026-10-13 §2.6): como «+ Añadir acción», pero la línea nace con el texto «Cerrar». */
function anadirCerrar(estado: EstadoFormulario): EstadoFormulario {
  if (!atajoCerrarVisible(estado)) return estado
  const linea: OtraAccion = { id: estado.siguienteIdAccion, texto: TEXTO_CERRAR, origen: 'nueva', guardada: null, confirmando: false, guardando: false }
  return { ...estado, otros: [...estado.otros, linea], siguienteIdAccion: estado.siguienteIdAccion + 1, revision: estado.revision + 1 }
}
```

7. Tras `anadirAccionHabilitado` (sección de selectores de otras acciones):

```ts
export const TEXTO_CERRAR = 'Cerrar'
const normalizada = (texto: string) => texto.trim().toLowerCase()
/** Apertura = acción que empieza por «abr» o «abi»; cierre = por «cerr» (mismo criterio que el servidor, spec 2026-10-13). */
export const esApertura = (texto: string) => /^(abr|abi)/.test(normalizada(texto))
export const esCierre = (texto: string) => normalizada(texto).startsWith('cerr')

/** «+ Cerrar» sale al completar (no al editar), con la sección Otras acciones visible, si el IMEI tiene más aperturas que
 *  cierres: cuentan las acciones guardadas al abrir (reparación + glass) y, como cierres, las líneas nuevas del formulario
 *  que empiezan por «cerr» (guardadas o no). */
export function atajoCerrarVisible(e: EstadoFormulario): boolean {
  if (e.modo === 'editar' || !otrasAccionesVisible(e)) return false
  const aperturas = e.accionesImei.filter(esApertura).length
  const cierres = e.accionesImei.filter(esCierre).length + e.otros.filter((a) => a.origen === 'nueva' && esCierre(a.texto)).length
  return aperturas > cierres
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/taller/formulario/estado.cerrar.test.ts`
Expected: PASS.

- [ ] **Step 5: Carga de las acciones del IMEI (prueba que falla)**

En `src/modules/taller/formulario/api.test.tsx`, en el test «compone DatosNuevo con el imei de la asignación», el objeto esperado de `toEqual` pasa a llevar `accionesImei: []` tras `modeloTelefono: '14',`. Y, dentro del `describe('cargarNuevo', …)`, añadir:

```tsx
  it('trae las acciones «otro» del IMEI de reparación y de glass para el atajo «+ Cerrar»; si fallan, []', async () => {
    guardarSesion(SESION_TEC)
    const categorias: string[] = []
    server.use(...handlersFormulario())
    server.use(
      http.get('*/api/reparaciones/imei/:imei/acciones', ({ request }) => {
        const categoria = new URL(request.url).searchParams.get('categoria') ?? ''
        categorias.push(categoria)
        return HttpResponse.json(categoria === 'G' ? ['Cerrar'] : ['Abrir', 'Cambio de tornillos'])
      }),
    )
    expect((await cargarNuevo('A20260916_1')).datos.accionesImei).toEqual(['Abrir', 'Cambio de tornillos', 'Cerrar'])
    expect(categorias.sort()).toEqual(['G', 'R'])
    server.use(http.get('*/api/reparaciones/imei/:imei/acciones', () => HttpResponse.json({ message: 'fallo' }, { status: 422 })))
    expect((await cargarNuevo('A20260916_1')).datos.accionesImei).toEqual([])
  })
```

Run: `npx vitest run src/modules/taller/formulario/api.test.tsx`
Expected: FAIL (`accionesImei` no llega en `datos`).

- [ ] **Step 6: Implementar la carga**

En `src/modules/taller/formulario/api.ts`, tras `pedirAgrupados`:

```ts
async function accionesDelImei(imei: string, categoria: 'R' | 'G'): Promise<string[]> {
  return (await api.GET('/api/reparaciones/imei/{imei}/acciones', { params: { path: { imei }, query: { categoria } } })).data ?? []
}
```

En `cargarNuevo`: añadir `accionesImei` al final del destructuring del `Promise.all` y, como último elemento del array:

```ts
    // Acciones «otro» del IMEI en reparación y glass, para el atajo «+ Cerrar» (spec 2026-10-13 §2.6); en silencio → [].
    Promise.all([accionesDelImei(imei, 'R'), accionesDelImei(imei, 'G')])
      .then(([rep, glass]) => [...rep, ...glass])
      .catch((): string[] => []),
```

y en `datos` añadir `accionesImei` tras `modeloTelefono`. Añadir al Javadoc de `cargarNuevo`, en la lista de lo que se pide en silencio: «, acciones «otro» del IMEI en reparación y glass (→ [])».

Run: `npx vitest run src/modules/taller/formulario/api.test.tsx`
Expected: PASS.

- [ ] **Step 7: Botón «+ Cerrar» (prueba que falla)**

En `src/modules/taller/formulario/OtrasAcciones.test.tsx`, tras `Arnes`, añadir un arnés con acciones del IMEI:

```tsx
/** Arnés con modelo 13 y las acciones «otro» ya guardadas del IMEI que trae la carga. */
function ArnesCerrar({ acciones }: { acciones: string[] }) {
  const [estado, dispatch] = useReducer(reducir, { ...DATOS, accionesImei: acciones }, (d) => conModelo('13')(estadoInicial(d)))
  return <OtrasAcciones estado={estado} dispatch={dispatch} onGuardarAccion={() => {}} />
}
```

y dentro del `describe`:

```tsx
  it('«+ Cerrar» solo con más aperturas que cierres en el IMEI: añade «Cerrar» enfocada y se oculta', async () => {
    const { unmount } = renderConProviders(<ArnesCerrar acciones={['Abrir', 'Cerrar']} />)
    expect(screen.queryByRole('button', { name: '+ Cerrar' })).not.toBeInTheDocument()
    unmount()
    renderConProviders(<ArnesCerrar acciones={['Abrir']} />)
    await userEvent.click(screen.getByRole('button', { name: '+ Cerrar' }))
    const campo = screen.getByLabelText('Descripción de la acción 1')
    expect(campo).toHaveValue('Cerrar')
    expect(campo).toHaveFocus()
    expect(screen.queryByRole('button', { name: '+ Cerrar' })).not.toBeInTheDocument()
  })
```

Run: `npx vitest run src/modules/taller/formulario/OtrasAcciones.test.tsx`
Expected: FAIL (no hay botón «+ Cerrar»).

- [ ] **Step 8: Implementar el botón**

En `OtrasAcciones.tsx`: añadir `atajoCerrarVisible` al import de `./estado` y sustituir el botón «+ Añadir acción» por:

```tsx
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={!anadirAccionHabilitado(estado)}
            onClick={() => { enfocarNueva.current = true; dispatch({ tipo: 'ANADIR_ACCION' }) }}
            className="cursor-pointer rounded-[6px] bg-azul-medio px-3 py-1.5 text-[11.5px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
          >
            + Añadir acción
          </button>
          {/* Atajo «+ Cerrar» (spec 2026-10-13 §2.6): solo si el IMEI tiene un «Abrir» sin su «Cerrar». */}
          {atajoCerrarVisible(estado) && (
            <button
              type="button"
              onClick={() => { enfocarNueva.current = true; dispatch({ tipo: 'ANADIR_CERRAR' }) }}
              className="cursor-pointer rounded-[6px] border border-azul-medio bg-superficie px-3 py-[5px] text-[11.5px] font-bold text-azul-medio hover:bg-fila-edicion-bg"
            >
              + Cerrar
            </button>
          )}
        </div>
```

Run: `npx vitest run src/modules/taller/formulario/OtrasAcciones.test.tsx`
Expected: PASS.

- [ ] **Step 9: Documentación**

En `docs/paridad/formulario.md`, sección «Otras acciones», tras la línea de «"+ Añadir acción" añade una línea y le da el foco…», añadir:

```markdown
- [x] Atajo **«+ Cerrar»** (web 0.9.11, sin equivalente en el JavaFX): junto a «+ Añadir acción», solo al completar (no en edición) y si el IMEI tiene más acciones «otro» que empiezan por «abr»/«abi» que por «cerr» (las guardadas de reparación y glass al abrir; las líneas nuevas del formulario que empiezan por «cerr» cuentan como cierre). Añade una línea «Cerrar» enfocada, lista para «✓ Guardar». Borde 1 px #2C3B54, fondo blanco, texto #2C3B54 11,5 px negrita, radio 6, padding 5 12. Llamadas: al abrir, `GET /api/reparaciones/imei/{imei}/acciones?categoria=R` y `?categoria=G` (en silencio → sin atajo).
```

En `CHANGELOG.md`, antes de `## [0.9.9] …` (o de la versión más alta que haya), añadir:

```markdown
## [0.9.11] - 2026-10-XX — «Abrir» automático al entregar a glass

- **«Abrir» automático.** «Entregar a …» (Mis pendientes, Reparación) apunta sola una acción «otro» «Abrir» a quien entrega, en su reparación. Si la llegada la marca el de glass («Marcar que llegó»), el «Abrir» va al dueño de la reparación normal. «Deshacer entrega» y «Deshacer llegada» lo borran. Si ya había un «abrir» apuntado a mano no se duplica; si el modelo no tiene pieza «otro», la entrega se hace igual, sin «Abrir».
- **Aviso breve** abajo al centro tras entregar, marcar la llegada o deshacer («Entregado a … · Abierto», …): no es un diálogo; una barra amarilla encoge en 4 s y se cierra solo, o con un clic.
- **Atajo «+ Cerrar»** en Otras acciones (reparación y glass, al completar): añade la línea «Cerrar» lista para «✓ Guardar». Solo sale si el IMEI tiene más «Abrir» que «Cerrar».
- Requiere el servidor con `migracion-abrir-glass.sql` (columna `ID_REP_ABRIR` y puntos `abrir` 0,25 / `cerrar` 0,17).
```

- [ ] **Step 10: Suite del formulario, tipos, lint y commit**

Run: `npx vitest run src/modules/taller/formulario`, después `npx tsc -b` y `npm run lint`
Expected: PASS y sin errores.

```bash
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/api.ts src/modules/taller/formulario/OtrasAcciones.tsx src/modules/taller/formulario/estado.cerrar.test.ts src/modules/taller/formulario/OtrasAcciones.test.tsx src/modules/taller/formulario/api.test.tsx docs/paridad/formulario.md CHANGELOG.md
git commit -m "feat: atajo Cerrar en otras acciones cuando el imei tiene un abrir sin cerrar"
```

---

### Task 8: Web — E2E `abrir-glass.spec.ts`

**Files:**
- Create: `tests/e2e/abrir-glass.spec.ts`
- Modify: `.env.e2e.example` (comentario del fichero nuevo)

**Interfaces:**
- Consumes: Tasks 3, 6 y 7 desplegadas en preproducción (se ejecuta en la Task 9); variables `E2E_BASE_URL`, `E2E_USER`/`E2E_PASS` (supertécnico) y `TEC_USER`/`TEC_PASS` (técnico).

- [ ] **Step 1: Escribir el E2E**

`tests/e2e/abrir-glass.spec.ts`:

```ts
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

/**
 * «Abrir» automático al entregar a glass y atajo «+ Cerrar» (spec raíz 2026-10-13). ESCRIBE: con E2E_USER crea por la API una
 * Reparación y una Glass de un IMEI sintético, las dos para TEC_USER (así el mismo técnico entrega, marca la llegada y cierra).
 * afterAll borra todo lo que cuelga de ese IMEI: asignaciones, «Abrir», «Cerrar» y teléfono.
 */
test.describe.configure({ mode: 'serial' })

/** Forma de la respuesta de POST /api/asignaciones/lote (se repite a mano: el e2e no importa código de la app). */
type RespuestaLote = { creadas: { idRep: string; imei: string; idTec: number; categoria: string }[]; conflictos: unknown[] }

let api: { ctx: APIRequestContext; headers: Record<string, string> } | null = null
let creada: { imei: string; idRep: string; idGlass: string } | null = null

const imeiSintetico = () => `000000${String(Date.now()).slice(-9)}`

/** Códigos de modelo del catálogo, como los guarda el servidor (se repiten a mano). */
const MODELOS = [
  '6s', '6splus', '7', '7plus', '8', '8plus', 'se2020', 'x', 'xr', 'xs', 'xsmax', '11', '11pro', '11promax',
  '12', '12mini', '12pro', '12promax', '13', '13mini', '13pro', '13promax', '14', '14plus', '14pro', '14promax',
  '15', '15plus', '15pro', '15promax', '16', '16e', '16plus', '16pro', '16promax', '17', 'air', '17pro', '17promax',
]

function modeloDeSku(sku: string, prefijo: string): string | null {
  let resto = sku.toLowerCase().slice(prefijo.length)
  if (resto.startsWith('i')) resto = resto.slice(1)
  let mejor: string | null = null
  for (const m of MODELOS) if (resto.startsWith(m) && (mejor === null || m.length > mejor.length)) mejor = m
  return mejor
}

test.beforeAll(async ({ playwright }, testInfo) => {
  const usuario = process.env.E2E_USER
  const clave = process.env.E2E_PASS
  if (!usuario || !clave || !process.env.TEC_USER || !process.env.TEC_PASS) return // los tests se saltan con credenciales()
  const ctx = await playwright.request.newContext({ baseURL: testInfo.project.use.baseURL })
  const login = await ctx.post('/api/auth/login', { data: { usuario, password: clave } })
  expect(login.status(), 'POST /api/auth/login (supertécnico, por API)').toBe(200)
  const { token } = (await login.json()) as { token: string }
  api = { ctx, headers: { Authorization: `Bearer ${token}` } }
})

/** Primer modelo con componente «otro» (sin él no hay «Abrir»). */
async function modeloConOtro(): Promise<string> {
  const r = await api!.ctx.get('/api/componentes/agrupados', { headers: api!.headers })
  expect(r.status(), 'GET /api/componentes/agrupados').toBe(200)
  const agrupados = (await r.json()) as Record<string, { tipo: string }[]>
  const modelo = (agrupados.otro ?? []).map((c) => modeloDeSku(c.tipo, 'otro')).find((m): m is string => m !== null)
  if (!modelo) throw new Error('Ningún modelo tiene componente «otro»')
  return modelo
}

/** Crea por la API una Reparación y una Glass del mismo IMEI sintético para el técnico con la sesión abierta en `page`. */
async function crearTrabajos(page: Page): Promise<{ imei: string; idRep: string; idGlass: string }> {
  expect(api, 'sesión de API del supertécnico (beforeAll)').not.toBeNull()
  const idTec = await page.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { idTec?: number }).idTec)
  expect(typeof idTec, 'idTec del técnico en la sesión').toBe('number')
  const imei = imeiSintetico()
  const modelo = await modeloConOtro()
  const respuesta = await api!.ctx.post('/api/asignaciones/lote', {
    headers: { ...api!.headers, 'Idempotency-Key': `e2e-abrir-glass-${imei}` },
    data: {
      telefonos: [{ imei, modelo, idCli: null, clienteExplicito: false }],
      asignaciones: [
        { imei, categoria: 'R', idTec, comentario: 'e2e abrir glass', esChasis: false },
        { imei, categoria: 'G', idTec, comentario: 'e2e abrir glass', esChasis: false },
      ],
    },
  })
  expect(respuesta.status(), 'POST /api/asignaciones/lote').toBe(200)
  const lote = (await respuesta.json()) as RespuestaLote
  const rep = lote.creadas.find((c) => c.categoria === 'R')
  const gla = lote.creadas.find((c) => c.categoria === 'G')
  if (!rep || !gla) throw new Error(`El lote no creó la Reparación y la Glass del IMEI de prueba: ${JSON.stringify(lote)}`)
  creada = { imei, idRep: rep.idRep, idGlass: gla.idRep }
  test.info().annotations.push({ type: 'e2e-creado', description: `${rep.idRep} + ${gla.idRep} / IMEI ${imei} / modelo ${modelo}` })
  return creada
}

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

test('técnico: entregar apunta el «Abrir», deshacer lo borra, la llegada lo vuelve a apuntar y sale «+ Cerrar»', async ({ page }) => {
  credenciales('E2E_USER', 'E2E_PASS')
  const { usuario, clave } = credenciales('TEC_USER', 'TEC_PASS')
  await entrar(page, usuario, clave)
  await expect(page).toHaveURL(/\/reparaciones\/pendientes$/)
  const { imei, idRep } = await crearTrabajos(page)
  await page.reload()

  const accionesRep = async () => {
    const r = await api!.ctx.get(`/api/reparaciones/imei/${imei}/acciones?categoria=R`, { headers: api!.headers })
    expect(r.status(), 'GET acciones del IMEI').toBe(200)
    return (await r.json()) as string[]
  }
  const aviso = page.getByTestId('aviso-breve')
  const fila = page.getByRole('row').filter({ hasText: imei })

  // 1) Entregar → «Abrir» a nombre del técnico, en su reparación
  await fila.getByText(imei).click({ button: 'right' })
  await page.getByRole('menuitem', { name: /^Entregar a / }).click()
  await expect(aviso).toContainText('· Abierto')
  await expect.poll(accionesRep).toEqual(['Abrir'])

  // 2) Deshacer entrega → el «Abrir» se borra
  await fila.getByText(imei).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Deshacer entrega' }).click()
  await expect(aviso).toContainText('Entrega deshecha · «Abrir» borrado')
  await expect.poll(accionesRep).toEqual([])

  // 3) Pestaña Glass: «Marcar que llegó» → el «Abrir» va al dueño de la normal (el mismo técnico)
  await page.goto('/reparaciones/pendientes/glass')
  const filaGlass = page.getByRole('row').filter({ hasText: imei })
  await filaGlass.getByText(imei).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Marcar que llegó' }).click()
  await expect(aviso).toContainText('Llegada registrada · Abierto (')
  await expect.poll(accionesRep).toEqual(['Abrir'])

  // 4) Formulario de la normal: «+ Cerrar» → guardar → el atajo ya no sale
  await page.goto('/reparaciones/pendientes')
  await page.getByRole('row').filter({ hasText: imei }).getByRole('button', { name: 'Añadir reparación' }).click()
  await expect(page).toHaveURL(new RegExp(`/reparaciones/pendientes/reparar/${idRep}$`))
  const formulario = page.getByRole('dialog', { name: /^Nueva reparación — IMEI / })
  const seccion = formulario.getByTestId('otras-acciones')
  const cerrar = seccion.getByRole('button', { name: '+ Cerrar' })
  await expect(cerrar).toBeVisible()
  await cerrar.click()
  await expect(seccion.getByLabel('Descripción de la acción 1')).toHaveValue('Cerrar')
  await expect(cerrar).toBeHidden()
  await seccion.getByRole('button', { name: '✓ Guardar' }).click()
  await seccion.getByRole('button', { name: '✓ Confirmar' }).click()
  await expect(seccion.getByText(/^✓ Guardada /)).toBeVisible()
  await expect.poll(accionesRep).toEqual(['Abrir', 'Cerrar'])
  await formulario.getByRole('button', { name: 'Cerrar formulario' }).click()
})

test.afterAll(async () => {
  const sesion = api
  const restante = creada
  api = null
  creada = null
  if (!sesion) return
  try {
    if (!restante) return
    const motivo = { motivo: 'Limpieza del smoke e2e' }
    for (const id of [restante.idRep, restante.idGlass]) {
      const ruta = `/api/reparaciones/asignaciones/${id}`
      const abierta = await sesion.ctx.get(ruta, { headers: sesion.headers })
      if (abierta.status() === 404) continue
      expect.soft(abierta.status(), `limpieza GET ${ruta}`).toBe(200)
      if (abierta.ok()) {
        const r = await sesion.ctx.delete(ruta, { headers: sesion.headers, data: motivo })
        expect.soft(r.status(), `limpieza DELETE ${ruta}`).toBe(204)
      }
    }
    const rutaImei = `/api/reparaciones/imei/${restante.imei}`
    const delImei = await sesion.ctx.get(rutaImei, { headers: sesion.headers })
    expect.soft(delImei.status(), `limpieza GET ${rutaImei}`).toBe(200)
    if (delImei.ok()) {
      for (const { idRep } of (await delImei.json()) as { idRep: string }[]) {
        const rutaRep = `/api/reparaciones/${idRep}`
        const r = await sesion.ctx.delete(rutaRep, { headers: sesion.headers, data: motivo })
        expect.soft(r.status(), `limpieza DELETE ${rutaRep}`).toBe(204)
      }
      const despues = await sesion.ctx.get(rutaImei, { headers: sesion.headers })
      expect.soft(despues.ok() ? await despues.json() : null, `limpieza: sin reparaciones del IMEI ${restante.imei}`).toEqual([])
    }
    const rutaTelefono = `/api/telefonos/${restante.imei}`
    const borrado = await sesion.ctx.delete(rutaTelefono, { headers: sesion.headers })
    expect.soft(borrado.status(), `limpieza DELETE ${rutaTelefono}`).toBe(204)
  } finally {
    await sesion.ctx.dispose()
  }
})
```

En `.env.e2e.example`, tras el bloque de `formulario.spec.ts`, añadir:

```
# abrir-glass.spec.ts ESCRIBE: con E2E_USER crea por API una Reparación y una Glass de un IMEI sintético para TEC_USER, entrega,
# deshace, marca la llegada y guarda un «Cerrar»; al final borra asignaciones, acciones y teléfono de ese IMEI. Necesita un
# servidor con la migración del «Abrir» automático (0.9.11).
```

- [ ] **Step 2: Comprobar que compila y se lista**

Run: `npx playwright test tests/e2e/abrir-glass.spec.ts --list` y `npx tsc -b`
Expected: lista 1 test, sin errores de tipos. (La ejecución real va en la Task 9, contra preproducción.)

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/abrir-glass.spec.ts .env.e2e.example
git commit -m "test: e2e del abrir automatico al entregar a glass y del atajo cerrar"
```

---

### Task 9: Cierre — documentación, suites completas y preproducción

**Files:**
- Modify (raíz): `gestion-reparaciones-cliente/docs/metricas-estadisticas.md`
- Modify (fuera de los repos): `C:/Users/dev/Documents/Apuntes/plan-futuro.md`

**Interfaces:**
- Consumes: todo lo anterior.

- [ ] **Step 1: Diccionario de métricas (raíz)**

En `gestion-reparaciones-cliente/docs/metricas-estadisticas.md`, en la tabla de claves, tras la fila de `otro`, añadir:

```markdown
| `abrir` | 0,25 | acción "otro" cuyo texto empieza por «abr»/«abi» (el «Abrir» automático al entregar a glass, 2026-10-13) |
| `cerrar` | 0,17 | acción "otro" cuyo texto empieza por «cerr» (5 min con 1 punto = 30 min) |
```

y tras la línea «- **Acciones "otro"** del formulario: …» añadir:

```markdown
- **«Abrir» y «Cerrar»** (2026-10-13): una acción "otro" (SKU `otro…`) puntúa `abrir` si su texto (recortado, sin distinguir mayúsculas) empieza por «abr» o «abi», y `cerrar` si empieza por «cerr»; el resto, `otro`. Si falta la clave `abrir` o `cerrar` en la tabla, puntúa como `otro` (nunca 0). Como siempre, aplica también al pasado: los «cerrar» antiguos pasan de 0,25 a 0,17.
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/docs/metricas-estadisticas.md
git commit -m "docs: claves abrir y cerrar en el diccionario de metricas"
```

- [ ] **Step 2: Nota en el plan futuro (fuera de los repos)**

En `C:/Users/dev/Documents/Apuntes/plan-futuro.md`, bajo la línea que empieza por `- [ ] 5 Estadísticas (puntos y promedios en servidor, filtrado por rol)`, añadir:

```markdown
  - [ ] Modal de valores: incluir las claves `abrir` (0,25) y `cerrar` (0,17) del «Abrir» automático (spec raíz 2026-10-13); hasta entonces se cambian por SQL en `Dificultad_puntos`.
```

En el MER `C:/Users/dev/Documents/Apuntes/Tabla BBDD(Corregido).drawio`, tabla `Reparacion`, tras la línea `ENTREGADO_POR: INT (FK Tecnico, nullable)`, añadir una línea con el mismo estilo: `ID_REP_ABRIR: VARCHAR(30) (nullable, solo filas AG)`. Es XML: localizar la celda de `ENTREGADO_POR` con `grep -n "ENTREGADO_POR" "…drawio"`, duplicar ese `mxCell` con un `id` nuevo y el texto nuevo, y bajar su `y` la altura de una fila (la misma diferencia que hay entre `ENTREGADO_AT` y `ENTREGADO_POR`); si la tabla tiene alto fijo, sumarle esa altura.

- [ ] **Step 3: Suites completas**

```bash
cd /c/Users/dev/Documents/wt/servidor-abrir && mvn -q test
cd /c/Users/dev/Documents/wt/web-abrir && npx vitest run && npx tsc -b && npm run lint
```
Expected: todo en verde. Apuntar el número de tests de cada suite para el resumen.

- [ ] **Step 4: Revisión del código**

Usar `superpowers:requesting-code-review` sobre las dos ramas (`git diff main...feature/abrir-automatico-glass` en cada worktree) y aplicar lo que salga con `superpowers:receiving-code-review`. Cada arreglo, con su commit.

- [ ] **Step 5: Preproducción (lo ejecuta el usuario)**

Preparar para el usuario, siguiendo `Apuntes/despliegue_preprod.md`, los comandos para:
1. Copiar `sql/migracion-abrir-glass.sql` a la VM de preproducción y ejecutarlo por partes: vista previa (0 y 0) → migración → comprobación (1 y las dos claves).
2. Desplegar el servidor de `feature/abrir-automatico-glass` y la web de su rama (avisar de que `docker compose up --build` tarda ~1 min sin salida).

Claude **no** hace SSH. Antes de dar los comandos, pedir OK para usar las ramas sin mergear en preproducción (o mergear primero, también con OK) y **preguntar si preproducción está libre**: hay otros desarrollos en paralelo (0.9.10 columna lateral, 0.9.9 varios proveedores) y solo hay una preproducción; desplegar esta rama sustituye lo que otra sesión tenga desplegado. La migración (solo añade) no molesta a las otras versiones.

- [ ] **Step 6: E2E contra preproducción**

```bash
cd /c/Users/dev/Documents/wt/web-abrir && set -a && . ~/.env.e2e && set +a && npx playwright test tests/e2e/abrir-glass.spec.ts
```
Expected: 1 passed, y la limpieza sin avisos `expect.soft`. Comprobar a mano en preproducción, con el aviso a la vista, que la barra encoge y que el aviso no tapa nada importante.

- [ ] **Step 7: Cierre**

Resumen al usuario: commits de cada repo, tests (antes → después), resultado del E2E y lo pendiente (merge `--no-ff` a `main` de servidor y web, gitlink y NOVEDADES en la raíz, tag `v0.9.11`, producción en horario si cumple el criterio). Nada de eso sin su OK. Recordarle que, antes de producción, avise al equipo de que el «abrir» ya es automático (que no lo apunten a mano) y del atajo «+ Cerrar» (spec §8). Actualizar la memoria `project_abrir_automatico_glass.md` con el estado.
