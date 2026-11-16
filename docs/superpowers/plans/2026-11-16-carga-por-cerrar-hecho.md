# 0.9.36 — Carga de técnicos: «por cerrar» cuenta como hecho al marcar, y tope de normales a 20: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el «✓ hecho» del panel de carga cuente los 11/12 de una asignación el día en que se marca «por cerrar» (entera si además se cierra ese día), y que una reparación normal pese 1/20 de la jornada de 9 h, no 1/25.

**Architecture:** columna nueva `Reparacion.POR_CERRAR_AT` (la pone y la quita el PATCH de «por cerrar»). `CargaTecnicos` gana un `reparto(...)` que devuelve `[hecho, pendiente]` de cada asignación usando el inicio de hoy en Madrid, que `CargaAsignacionesService` ya calcula para las completadas hoy y ahora también devuelve. `calcularDia` y `PrediccionGlass` usan ese reparto; sus firmas antiguas se quedan como sobrecargas sin corte (comportamiento de antes), para no tocar los tests que ya hay.

**Tech Stack:** Java 17, Spring Boot 3.3.4, JUnit 5 + Mockito, Maven, MariaDB. Solo `gestion-reparaciones-servidor` (más el número en la web al final, cuando lo diga el usuario).

**Spec:** `docs/superpowers/specs/2026-11-16-carga-por-cerrar-hecho-design.md`

## Global Constraints

- Versión **0.9.36** (la 0.9.35 es «Validar SKU» de Pedidos, otra sesión, sin publicar).
- Migración aditiva: `ALTER TABLE Reparacion ADD COLUMN POR_CERRAR_AT DATETIME NULL AFTER POR_CERRAR`. La web y el servidor viejos la ignoran.
- `POR_CERRAR_AT` **no** sale en la API (`@JsonIgnore`): la web no cambia ni regenera tipos.
- «Marcada hoy» = `POR_CERRAR_AT` no anterior al inicio de hoy en Madrid, convertido igual que el corte de `FECHA_FIN` (`cutoff.toLocalDateTime()`). Sin fecha → como antes.
- Solo asignaciones de reparación (`TipoTrabajo.REPARACION`); glass y pulido no cambian.
- `TOPE_NORMALES_9H = 20`.
- Commits en español, estilo `feat: … (0.9.36)`, **sin** `Co-Authored-By`.
- No hacer merge, push ni tags: los decide el usuario. Claude no entra por SSH.
- Maven por Bash, con el entorno delante de cada orden:
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`

## Ficheros

Bajo `gestion-reparaciones-servidor/` (en el worktree de la Task 0):

| Fichero | Qué |
|---|---|
| `sql/migracion-por-cerrar-at.sql` | Nuevo. El `ALTER`. |
| `sql/crear_bd.sql` | La columna en `Reparacion`. |
| `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java` | Campo `porCerrarAt` con `@JsonIgnore`. |
| `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` | Select + group by de asignaciones, mapper y `actualizarPorCerrar`. |
| `src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java` | Tope 20, `reparto`, `marcadaHoy`, `calcularDia` con corte. |
| `src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java` | `elegir` y `carga` con corte. |
| `src/main/java/com/reparaciones/servidor/service/CargaAsignacionesService.java` | `Estado.inicioHoy`. |
| `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` | `getCargaTecnicos` pasa el corte. |
| `src/main/java/com/reparaciones/servidor/controller/GlassController.java` | La predicción pasa el corte. |
| `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOPorCerrarTest.java` | Nuevo. |
| `src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java` | Tests nuevos. |
| `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java` | Un test nuevo; comentarios «1/25» → «1/20». |
| `src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java` | Un test nuevo. |

En la raíz: `docs/novedades/NOVEDADES-v0.9.36.md` (nuevo).

---

### Task 0: worktree y rama

- [ ] **Step 1: traer el remoto y comprobar que `main` está al día**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch
git status -sb | head -1
```

Expected: `## main...origin/main` sin `[behind …]`. Si está por detrás, parar y avisar al usuario (puede haber otra sesión trabajando).

- [ ] **Step 2: crear el worktree**

```bash
git worktree add /c/Users/dev/Documents/wt/servidor-0936-carga-por-cerrar -b dev_v0936_carga_por_cerrar origin/main
```

Todo lo que sigue se hace en `/c/Users/dev/Documents/wt/servidor-0936-carga-por-cerrar`.

---

### Task 1: tope de normales a 20

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java:42-53`
- Modify: `src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java` (solo comentarios)

**Interfaces:**
- Produces: `CargaTecnicos.TOPE_NORMALES_9H == 20`.

- [ ] **Step 1: test que falla**

Añadir a `CargaTecnicosTest` (antes de `formateaPctEntero`):

```java
    // Tope de normales = 20 (decisión del usuario 2026-07-24): una normal un lunes (9h) = 5%
    @Test void unaNormalEnLunesEsUnCincoPorCiento() {
        assertEquals(20, CargaTecnicos.TOPE_NORMALES_9H);
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_1", 1, "WEB", false, false)),
                List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(5.0, dia.get(1).pctPendiente(), 1e-9);
    }
```

- [ ] **Step 2: comprobar que falla**

```bash
cd /c/Users/dev/Documents/wt/servidor-0936-carga-por-cerrar
export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q test -Dtest=CargaTecnicosTest
```

Expected: FAIL en `unaNormalEnLunesEsUnCincoPorCiento` (`expected: <20> but was: <25>`).

- [ ] **Step 3: cambiar el tope**

En `CargaTecnicos.java`, sustituir el bloque de comentario y constantes (líneas 42-53, desde `// ── Capacidad diaria` hasta `TOPE_NORMALES_9H = 25;`) por:

```java
    // ── Capacidad diaria (spec 2026-07-09-carga-capacidad-diaria) ────────────
    // Techo de jornada larga (9h) por tipo — el 100% del modelo (spec 2026-07-09-carga-capacidad-diaria).
    // chasis 8 y glass 17: medidos por el usuario en el taller (2026-07-09), "si SOLO haces eso en el día".
    // normales 20: bajado por el usuario de 25 a 20 (2026-07-24, cliente JavaFX e90d9a6), en línea con el dato
    // de BD (mejor día real escalado a 9h = 20,3). Al portar la carga al servidor (8e3ada8, 22/09) se copió
    // el 25 antiguo por error; corregido en la 0.9.36. El glass validado: mejor día del técnico de glass = 18
    // escalado vs techo 17.
    // Futuro apuntado: tabla de BD configurable y topes por técnico (F4).
    public static final int TOPE_CHASIS_9H   = 8;
    public static final int TOPE_GLASS_9H    = 17;
    public static final int TOPE_NORMALES_9H = 20;
```

En `PrediccionGlassTest.java`, cambiar en los comentarios de las líneas 59, 128 y 136 `1/25` por `1/20` y `(1/25 = 0,04)` por `(1/20 = 0,05)`. Los asserts usan la constante y no cambian.

- [ ] **Step 4: comprobar que pasa**

```bash
mvn -q test -Dtest='CargaTecnicosTest,PrediccionGlassTest,CargaTecnicosControllerTest,PrediccionGlassControllerTest'
```

Expected: BUILD SUCCESS. (En el test de `PrediccionGlassTest` de la línea 128, una glass verde (1/17 ≈ 0,0588) sigue pesando más que una reparación verde (1/20 = 0,05), así que gana el mismo técnico.)

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java
git commit -m "fix: tope de normales de la carga de tecnicos a 20, como se decidio el 24/07 (0.9.36)"
```

---

### Task 2: fecha de la marca «por cerrar» (`POR_CERRAR_AT`)

**Files:**
- Create: `sql/migracion-por-cerrar-at.sql`
- Modify: `sql/crear_bd.sql:194`
- Modify: `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (`ASIGNACION_SELECT` línea 113, los tres `GROUP BY` de las líneas 219, 235 y 253, `RESUMEN_MAPPER` línea 171, `actualizarPorCerrar` línea 743)
- Create: `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOPorCerrarTest.java`

**Interfaces:**
- Produces: `ReparacionResumen.getPorCerrarAt(): LocalDateTime` (null si no hay marca o es anterior a la 0.9.36) y `setPorCerrarAt(LocalDateTime)`. `getAsignaciones`, `getAsignacionesCompletadasHoy` (la consulta de `A%`) y `getAsignacionById` lo rellenan. `actualizarPorCerrar(idRep, porCerrar)` pone `NOW()` al marcar y `NULL` al quitar.

- [ ] **Step 1: tests que fallan**

Crear `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOPorCerrarTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Fecha de la marca «por cerrar» (spec 2026-11-16-carga-por-cerrar-hecho §1). */
class ReparacionDAOPorCerrarTest {

    private static final String SQL_POR_CERRAR =
            "UPDATE Reparacion SET POR_CERRAR = ?, POR_CERRAR_AT = CASE WHEN ? THEN NOW() END," +
            " UPDATED_AT = UPDATED_AT WHERE ID_REP = ?";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static List<String> consultas(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query"))
                .map(i -> (String) i.getArgument(0))
                .toList();
    }

    @Test void marcarPoneLaHoraYQuitarLaBorra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).actualizarPorCerrar("A20261116_1", true);
        dao(jdbc).actualizarPorCerrar("A20261116_1", false);
        verify(jdbc).update(SQL_POR_CERRAR, true, true, "A20261116_1");
        verify(jdbc).update(SQL_POR_CERRAR, false, false, "A20261116_1");
    }

    @Test void lasConsultasDeAsignacionesDeReparacionTraenYAgrupanLaFecha() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getAsignaciones(null);
        d.getAsignacionesCompletadasHoy(Timestamp.valueOf("2026-11-16 00:00:00"));   // la 1ª es la de A%
        d.getAsignacionById("A20261116_1");
        List<String> qs = consultas(jdbc);
        for (String q : List.of(qs.get(0), qs.get(1), qs.get(3))) {
            assertTrue(q.contains("r.POR_CERRAR, r.POR_CERRAR_AT, (SELECT COUNT(*) FROM Reparacion g"), q);  // select
            assertTrue(q.contains("r.ES_CHASIS, r.POR_CERRAR, r.POR_CERRAR_AT, ta.NOMBRE"), q);  // group by
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void elMapperLeeLaFecha() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignaciones(null);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture());
        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf("2026-11-16 08:00:00");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getTimestamp("POR_CERRAR_AT")).thenReturn(Timestamp.valueOf("2026-11-16 10:30:00"));
        ReparacionResumen rr = (ReparacionResumen) mapper.getValue().mapRow(rs, 0);
        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 30), rr.getPorCerrarAt());

        when(rs.getTimestamp("POR_CERRAR_AT")).thenReturn(null);
        assertNull(((ReparacionResumen) mapper.getValue().mapRow(rs, 0)).getPorCerrarAt());
    }
}
```

- [ ] **Step 2: comprobar que falla**

```bash
mvn -q test -Dtest=ReparacionDAOPorCerrarTest
```

Expected: no compila (`getPorCerrarAt` no existe).

- [ ] **Step 3: modelo**

En `ReparacionResumen.java`, debajo de `private boolean       porCerrar;` (línea 34):

```java
    /** Cuándo se marcó «por cerrar» (0.9.36); null sin marca o marcada antes. Solo para la carga: no sale en la API. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private LocalDateTime porCerrarAt;
```

y debajo de `setPorCerrar` (línea 129):

```java
    @com.fasterxml.jackson.annotation.JsonIgnore
    public LocalDateTime getPorCerrarAt()                  { return porCerrarAt; }
    public void          setPorCerrarAt(LocalDateTime v)   { this.porCerrarAt = v; }
```

- [ ] **Step 4: DAO**

En `ReparacionDAO.java`:

1. `ASIGNACION_SELECT` (línea 113): `r.URGENTE, r.ES_CHASIS, r.POR_CERRAR," +` → `r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, r.POR_CERRAR_AT," +`
2. En los tres `GROUP BY` de `getAsignaciones` (219), `getAsignacionesCompletadasHoy` (235, el de `A%`, no el de glass) y `getAsignacionById` (253): `r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE` → `r.ES_CHASIS, r.POR_CERRAR, r.POR_CERRAR_AT, ta.NOMBRE`
3. `RESUMEN_MAPPER`, debajo de la línea de `POR_CERRAR` (171):

```java
        try { Timestamp pc = rs.getTimestamp("POR_CERRAR_AT"); rr.setPorCerrarAt(pc != null ? pc.toLocalDateTime() : null); } catch (Exception ignored) {}
```

4. `actualizarPorCerrar` (línea 743):

```java
    /** La marca y su hora (spec 2026-11-16 §1): marcar pone NOW(), también al volver a marcar; quitar la borra. */
    public void actualizarPorCerrar(String idRep, boolean porCerrar) {
        jdbc.update("UPDATE Reparacion SET POR_CERRAR = ?, POR_CERRAR_AT = CASE WHEN ? THEN NOW() END," +
                " UPDATED_AT = UPDATED_AT WHERE ID_REP = ?", porCerrar, porCerrar, idRep);
    }
```

- [ ] **Step 5: SQL**

Crear `sql/migracion-por-cerrar-at.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-por-cerrar-at.sql — Hora de la marca «Por cerrar» (0.9.36)
-- La aplica el usuario a mano en gestion_reparaciones (orden: ALTER → servidor).
-- Aditiva: el servidor anterior la ignora. Las marcas de antes quedan sin hora.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Cuándo se marcó «por cerrar»: ese día los 11/12 de la asignación cuentan como
-- hechos en la carga de técnicos (spec 2026-11-16-carga-por-cerrar-hecho).
ALTER TABLE Reparacion
    ADD COLUMN POR_CERRAR_AT DATETIME NULL AFTER POR_CERRAR;
```

En `sql/crear_bd.sql`, debajo de `POR_CERRAR           BOOLEAN      NOT NULL DEFAULT FALSE,` (línea 194):

```sql
    POR_CERRAR_AT        DATETIME     NULL COMMENT 'Cuándo se marcó «por cerrar» (0.9.36); NULL sin marca',
```

- [ ] **Step 6: comprobar que pasa**

```bash
mvn -q test -Dtest='ReparacionDAOPorCerrarTest,ReparacionDAOClienteLecturaTest,ReparacionDAOChasisTest'
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: commit**

```bash
git add sql/migracion-por-cerrar-at.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOPorCerrarTest.java
git commit -m "feat: hora de la marca por cerrar en las asignaciones (POR_CERRAR_AT) (0.9.36)"
```

---

### Task 3: el reparto hecho/pendiente y su uso en la carga y la predicción

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java`
- Modify: `src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java`
- Modify: `src/main/java/com/reparaciones/servidor/service/CargaAsignacionesService.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java:142-151`
- Modify: `src/main/java/com/reparaciones/servidor/controller/GlassController.java:97-103`
- Modify: `src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java`

**Interfaces:**
- Consumes: `ReparacionResumen.getPorCerrarAt()` (Task 2).
- Produces:
  - `CargaTecnicos.reparto(ReparacionResumen r, boolean esAbierta, boolean soloPedidos, LocalDateTime inicioHoy): double[]` → `{hecho, pendiente}` en fracción de 9h.
  - `CargaTecnicos.marcadaHoy(ReparacionResumen r, LocalDateTime inicioHoy): boolean`.
  - `CargaTecnicos.calcularDia(abiertas, cerradasHoy, DayOfWeek dia, boolean soloPedidos, LocalDateTime inicioHoy)`; la de 4 argumentos llama a esta con `null`.
  - `PrediccionGlass.elegir(..., boolean conCliente, LocalDateTime inicioHoy)` y `PrediccionGlass.carga(..., boolean soloPedidos, LocalDateTime inicioHoy)`; las de antes llaman a estas con `null`.
  - `CargaAsignacionesService.Estado(List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, LocalDateTime inicioHoy)`.

- [ ] **Step 1: tests que fallan en `CargaTecnicosTest`**

Añadir arriba de la clase, debajo de los helpers `asig`:

```java
    /** Inicio de hoy (lunes 16/11/2026) tal como lo pasa CargaAsignacionesService. */
    private static final java.time.LocalDateTime HOY = java.time.LocalDateTime.of(2026, 11, 16, 0, 0);
    private static final java.time.DayOfWeek LUNES = java.time.DayOfWeek.MONDAY;   // 9h: pct = fracción × 100

    private ReparacionResumen porCerrar(String idRep, boolean chasis, java.time.LocalDateTime marcada) {
        ReparacionResumen r = asig(idRep, 1, "WEB", chasis, true);
        r.setPorCerrarAt(marcada);
        return r;
    }
```

Y los tests (antes de `formateaPctEntero`):

```java
    // ── «Por cerrar» cuenta como hecho al marcar (spec 2026-11-16-carga-por-cerrar-hecho §2) ──

    @Test void marcadaHoyYAbiertaLlevaOnceDoceavosAHecho() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, HOY.plusHours(10))),
                List.of(), LUNES, false, HOY);
        assertEquals(5.0 * 11 / 12, dia.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(1, dia.get(1).hecho().porCerrar());          // sale en los dos tramos
        assertEquals(1, dia.get(1).pendiente().porCerrar());
    }

    @Test void marcadaHoyYAbiertaDeChasisUsaElPesoDeChasis() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", true, HOY.plusHours(10))),
                List.of(), LUNES, false, HOY);
        assertEquals(12.5 * 11 / 12, dia.get(1).pctHecho(), 1e-9);
        assertEquals(12.5 / 12, dia.get(1).pctPendiente(), 1e-9);
    }

    @Test void marcadaHoyYCerradaHoyCuentaEntera() {
        var dia = CargaTecnicos.calcularDia(List.of(),
                List.of(porCerrar("A_1", false, HOY.plusHours(10)), porCerrar("A_2", true, HOY.plusHours(11))),
                LUNES, false, HOY);
        assertEquals(5.0 + 12.5, dia.get(1).pctHecho(), 1e-9);
        assertEquals(0.0, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(2, dia.get(1).hecho().porCerrar());
    }

    @Test void marcadaOtroDiaSigueComoAntes() {
        var ayer = HOY.minusSeconds(1);
        var abierta = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, ayer)), List.of(), LUNES, false, HOY);
        assertEquals(0.0, abierta.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, abierta.get(1).pctPendiente(), 1e-9);
        var cerrada = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, ayer)), LUNES, false, HOY);
        assertEquals(5.0 / 12, cerrada.get(1).pctHecho(), 1e-9);
    }

    @Test void sinFechaSigueComoAntes() {
        var abierta = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, null)), List.of(), LUNES, false, HOY);
        assertEquals(0.0, abierta.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, abierta.get(1).pctPendiente(), 1e-9);
        var cerrada = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, null)), LUNES, false, HOY);
        assertEquals(5.0 / 12, cerrada.get(1).pctHecho(), 1e-9);
    }

    @Test void justoALaMedianocheYaEsHoy() {
        var dia = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, HOY)), LUNES, false, HOY);
        assertEquals(5.0, dia.get(1).pctHecho(), 1e-9);
    }

    @Test void marcadaHoyEnEsperaDePiezaNoSumaNada() {
        ReparacionResumen r = asig("A_1", 1, "WEB", false, true, null, 1, "PENDIENTE", 0);
        r.setPorCerrarAt(HOY.plusHours(10));
        var dia = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, false, HOY);
        assertEquals(0.0, dia.get(1).pctHecho(), 1e-9);
        assertEquals(0.0, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(0, dia.get(1).hecho().porCerrar());
        assertEquals(1, dia.get(1).pendiente().enEsperaPieza());
    }

    @Test void marcadaHoySinClienteNoCuentaEnPedidos() {
        ReparacionResumen r = asig("A_1", 1, null, false, true);
        r.setPorCerrarAt(HOY.plusHours(10));
        var pedidos = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, true, HOY);
        assertNull(pedidos.get(1));
        var total = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, false, HOY);
        assertEquals(5.0 * 11 / 12, total.get(1).pctHecho(), 1e-9);
    }

    @Test void sinCorteNadaCuentaComoMarcadoHoy() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, HOY.plusHours(10))),
                List.of(), LUNES, false);
        assertEquals(0.0, dia.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, dia.get(1).pctPendiente(), 1e-9);
    }

    @Test void laGlassNoTieneMarcaPorCerrar() {
        ReparacionResumen g = asig("AG_1", 1, "WEB", false, true);   // no debería pasar, pero no cambia nada
        g.setPorCerrarAt(HOY.plusHours(10));
        assertFalse(CargaTecnicos.marcadaHoy(g, HOY));
    }
```

- [ ] **Step 2: tests que fallan en `PrediccionGlassTest` y `CargaTecnicosControllerTest`**

En `PrediccionGlassTest`, al final de la clase:

```java
    // Una «por cerrar» marcada hoy y abierta pesa entera (11/12 hecho + 1/12 pendiente), como en calcularDia.
    @Test void unaPorCerrarMarcadaHoyPesaEntera() {
        java.time.LocalDateTime hoy = java.time.LocalDateTime.of(2026, 11, 16, 0, 0);
        ReparacionResumen r = asig("A1", "222222222222222", 1, "WEB");
        r.setPorCerrar(true);
        r.setPorCerrarAt(hoy.plusHours(10));
        assertEquals(1.0 / CargaTecnicos.TOPE_NORMALES_9H,
                PrediccionGlass.carga(1, List.of(r), List.of(), List.of(), true, hoy), 1e-12);
        assertEquals(1.0 / CargaTecnicos.TOPE_NORMALES_9H / 12,
                PrediccionGlass.carga(1, List.of(r), List.of(), List.of(), true), 1e-12);   // sin corte: como antes
    }
```

En `CargaTecnicosControllerTest`, al final de la clase (los recuentos del desglose no dependen del día de la semana; `LocalDateTime.now()` siempre es posterior al inicio de hoy en Madrid convertido a la zona de la JVM):

```java
    /** El endpoint pasa el inicio de hoy: una «por cerrar» marcada ahora y abierta sale en los dos tramos. */
    @Test void unaPorCerrarMarcadaAhoraSaleEnHechoYEnPendiente() throws Exception {
        mockearTecnicosActivos();
        ReparacionResumen r = asig("A_1", 1, "CLI");
        r.setPorCerrar(true);
        r.setPorCerrarAt(java.time.LocalDateTime.now());
        when(dao.getAsignaciones(null)).thenReturn(List.of(r));
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(List.of());

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos[0].hecho.porCerrar").value(1))
                .andExpect(jsonPath("$.pedidos[0].pendiente.porCerrar").value(1));
    }
```

- [ ] **Step 3: comprobar que fallan**

```bash
mvn -q test -Dtest='CargaTecnicosTest,PrediccionGlassTest,CargaTecnicosControllerTest'
```

Expected: no compila (`calcularDia` de 5 argumentos, `marcadaHoy` y `carga` de 6 no existen).

- [ ] **Step 4: `CargaTecnicos`**

Añadir `import java.time.LocalDateTime;` y, debajo de `fraccion9h`, los dos métodos:

```java
    /** «Por cerrar» de reparación marcada desde el inicio de hoy (spec 2026-11-16 §2). Sin corte o sin fecha: no. */
    static boolean marcadaHoy(ReparacionResumen r, LocalDateTime inicioHoy) {
        return inicioHoy != null && r.isPorCerrar() && r.getPorCerrarAt() != null
                && TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.REPARACION
                && !r.getPorCerrarAt().isBefore(inicioHoy);
    }

    /**
     * {hecho, pendiente} de una asignación, en fracción de jornada de 9h (spec 2026-11-16 §2). Una «por cerrar»
     * marcada hoy recupera lo hecho: abierta, 11/12 a hecho y 1/12 a pendiente; cerrada hoy, entera a hecho. El resto
     * como siempre: lo abierto es pendiente y lo cerrado hoy, hecho. Package-private: la usa {@link PrediccionGlass}.
     */
    static double[] reparto(ReparacionResumen r, boolean esAbierta, boolean soloPedidos, LocalDateTime inicioHoy) {
        double f = fraccion9h(r, esAbierta, soloPedidos);
        if (f > 0 && marcadaHoy(r, inicioHoy)) {
            double entera = f / PESO_POR_CERRAR;
            return esAbierta ? new double[]{entera - f, f} : new double[]{entera, 0};
        }
        return esAbierta ? new double[]{0, f} : new double[]{f, 0};
    }
```

Sustituir `calcularDia` entero por:

```java
    /** Sin corte: nada cuenta como marcado «por cerrar» hoy (el cálculo de antes de la 0.9.36). */
    public static java.util.Map<Integer, DiaTecnico> calcularDia(
            List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
            java.time.DayOfWeek dia, boolean soloPedidos) {
        return calcularDia(abiertas, cerradasHoy, dia, soloPedidos, null);
    }

    /** @param inicioHoy inicio de hoy en Madrid, en la zona de la JVM (el mismo corte que las completadas hoy). */
    public static java.util.Map<Integer, DiaTecnico> calcularDia(
            List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
            java.time.DayOfWeek dia, boolean soloPedidos, LocalDateTime inicioHoy) {
        int horas = JORNADA_HORAS.getOrDefault(dia, 0);
        boolean sinJornada = horas == 0;
        double factor = sinJornada ? 0 : 9.0 / horas;   // pct del día = fracción9h × 9/horas × 100
        java.util.Map<Integer, double[]> acum = new java.util.HashMap<>();      // [hecho, pendiente]
        java.util.Map<Integer, Desglose[]> desg = new java.util.HashMap<>();    // [hecho, pendiente]
        java.util.function.BiConsumer<ReparacionResumen, Boolean> suma = (r, esAbierta) -> {
            if (TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.PULIDO) return;
            if (soloPedidos && (r.getCliente() == null || r.getCliente().isEmpty())) return;
            double[] p = reparto(r, esAbierta, soloPedidos, inicioHoy);
            double[] a = acum.computeIfAbsent(r.getIdTec(), k -> new double[2]);
            Desglose[] d = desg.computeIfAbsent(r.getIdTec(),
                    k -> new Desglose[]{ new Desglose(0,0,0,0,0,0), new Desglose(0,0,0,0,0,0) });
            if (esAbierta) {
                // Una «por cerrar» marcada hoy y abierta cuenta en los dos tramos (spec 2026-11-16 §2, desglose).
                if (p[0] > 0) { a[0] += p[0]; d[0] = sumarDesglose(d[0], r, false, p[0]); }
                a[1] += p[1];
                d[1] = sumarDesglose(d[1], r, true, p[1]);
            } else {
                a[0] += p[0];
                d[0] = sumarDesglose(d[0], r, false, p[0]);
            }
        };
        cerradasHoy.forEach(r -> suma.accept(r, false));
        abiertas.forEach(r -> suma.accept(r, true));
        java.util.Map<Integer, DiaTecnico> out = new java.util.HashMap<>();
        boolean finalSin = sinJornada;
        acum.forEach((idTec, a) -> out.put(idTec, new DiaTecnico(
                a[0] * factor * 100, a[1] * factor * 100,
                desg.get(idTec)[0], desg.get(idTec)[1], finalSin)));
        return out;
    }
```

- [ ] **Step 5: `PrediccionGlass`**

Añadir `import java.time.LocalDateTime;`. Renombrar la firma de `elegir` a la de 7 argumentos y añadir la de 6 delante:

```java
    /** Sin corte: nada cuenta como marcado «por cerrar» hoy (el cálculo de antes de la 0.9.36). */
    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<VerdeEnModal> verdesModal,
                                 String imei, boolean conCliente) {
        return elegir(tecnicos, abiertas, cerradasHoy, verdesModal, imei, conCliente, null);
    }

    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<VerdeEnModal> verdesModal,
                                 String imei, boolean conCliente, LocalDateTime inicioHoy) {
```

Dentro de esa `elegir`, la línea de la clave pasa a:

```java
            long clave = Math.round(carga(t.getIdTec(), abiertas, cerradasHoy, verdesModal, conCliente, inicioHoy) * 1e9);
```

(y añadir `@param inicioHoy inicio de hoy en Madrid; con él una «por cerrar» marcada hoy pesa entera` al javadoc). Sustituir `carga` por:

```java
    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<VerdeEnModal> verdesModal, boolean soloPedidos) {
        return carga(idTec, abiertas, cerradasHoy, verdesModal, soloPedidos, null);
    }

    /** Fracción de jornada de 9h del técnico: hecho hoy + pendiente en BD + entradas verdes del modal, con alcance
     *  Pedidos/Total. Suma los dos tramos de {@link CargaTecnicos#reparto}, así que da el mismo total que calcularDia. */
    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<VerdeEnModal> verdesModal, boolean soloPedidos, LocalDateTime inicioHoy) {
        double f = 0;
        for (ReparacionResumen r : cerradasHoy)
            if (r.getIdTec() == idTec) { double[] p = CargaTecnicos.reparto(r, false, soloPedidos, inicioHoy); f += p[0] + p[1]; }
        for (ReparacionResumen r : abiertas)
            if (r.getIdTec() == idTec) { double[] p = CargaTecnicos.reparto(r, true, soloPedidos, inicioHoy); f += p[0] + p[1]; }
        for (VerdeEnModal g : verdesModal)
            if (g.idTec() == idTec && (!soloPedidos || g.conCliente())) f += g.fraccion9h();
        return f;
    }
```

- [ ] **Step 6: `CargaAsignacionesService` y controladores**

En `CargaAsignacionesService.java`, añadir `import java.sql.Timestamp;` e `import java.time.LocalDateTime;`, y:

```java
    /** Abiertas (las tres categorías) + completadas hoy; cerradasHoy vacío si esa consulta falla. inicioHoy: el corte
     *  de las completadas hoy en la zona de la JVM, para saber qué «por cerrar» se marcó hoy (spec 2026-11-16 §2). */
    public record Estado(List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, LocalDateTime inicioHoy) {}
```

En `cargar()`, calcular el corte una vez antes del `try` y usarlo en los dos sitios:

```java
        Timestamp corte = UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock.system(ZoneId.of("Europe/Madrid")));
        List<ReparacionResumen> cerradasHoy;
        try {
            cerradasHoy = dao.getAsignacionesCompletadasHoy(corte);
        } catch (RuntimeException e) {
            // (comentario de siempre, sin cambios)
            log.warn("getAsignacionesCompletadasHoy() falló; la carga degrada a solo lo abierto", e);
            cerradasHoy = List.of();
        }
        return new Estado(abiertas, cerradasHoy, corte.toLocalDateTime());
```

En `ReparacionController.getCargaTecnicos()`:

```java
        return new CargaTecnicosRespuesta(
                filas(CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, true, estado.inicioHoy()), dia, tecnicos),
                filas(CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, false, estado.inicioHoy()), dia, tecnicos));
```

En `GlassController` (predicción):

```java
        Tecnico t = PrediccionGlass.elegir(tecnicoDao.getAllActivos(), estado.abiertas(), estado.cerradasHoy(), verdes,
                req.imei(), req.conCliente(), estado.inicioHoy());
```

- [ ] **Step 7: comprobar que pasa**

```bash
mvn -q test -Dtest='CargaTecnicosTest,CargaTecnicosZonaTest,PrediccionGlassTest,CargaTecnicosControllerTest,PrediccionGlassControllerTest'
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: commit**

```bash
git add src/main/java/com/reparaciones/servidor/service src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/main/java/com/reparaciones/servidor/controller/GlassController.java src/test/java/com/reparaciones/servidor/service src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java
git commit -m "feat: la carga de tecnicos cuenta como hecho los 11/12 de una por cerrar el dia que se marca (0.9.36)"
```

---

### Task 4: todos los tests y las novedades

**Files:**
- Create (repo raíz, `c:\Users\dev\Documents\ProgramaReparaciones`): `docs/novedades/NOVEDADES-v0.9.36.md`

- [ ] **Step 1: pasar todos los tests del servidor**

```bash
cd /c/Users/dev/Documents/wt/servidor-0936-carga-por-cerrar
export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q test
```

Expected: BUILD SUCCESS (incluye `OpenApiContractTest`, que arranca el contexto). Si algún test esperaba el peso de una normal con 25 escrito a mano, se corrige a 20.

- [ ] **Step 2: escribir las novedades**

`docs/novedades/NOVEDADES-v0.9.36.md`:

```markdown
# Novedades de la versión 0.9.36

---

## 🔧 Carga de técnicos

- Marcar una asignación **«Por cerrar»** ya cuenta como trabajo hecho: ese día, casi toda la asignación (11/12) suma al
  **✓ hecho** del técnico, y el poco que queda (cerrar el móvil) sigue en pendiente hasta que se cierra. Si se marca y se
  cierra el mismo día, cuenta entera. Antes, lo hecho antes de marcar no salía como hecho ningún día.
- Las asignaciones marcadas «Por cerrar» antes de esta versión siguen contando como antes.
- Una reparación normal vuelve a pesar **1/20** de la jornada de 9 h, como se decidió en julio: la web contaba 1/25.
```

- [ ] **Step 3: commit en la raíz**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/novedades/NOVEDADES-v0.9.36.md
git commit -m "docs: novedades de la 0.9.36 (por cerrar cuenta como hecho al marcar y tope de normales a 20)"
```

- [ ] **Step 4: parar y avisar al usuario**

La rama `dev_v0936_carga_por_cerrar` queda lista en el worktree, sin merge ni push. El usuario decide merge (`--no-ff`), cuándo sale la 0.9.35 y el despliegue en preprod. El número 0.9.36 de la web (`package.json`, `package-lock.json` y `CHANGELOG.md`, como en la 0.9.29) se hace cuando el usuario lo pida, encima de la 0.9.35.

---

### Task 5: comprobación en preprod (la ejecuta el usuario)

Claude no entra por SSH: prepara los bloques y el usuario los pega. Tras aplicar `migracion-por-cerrar-at.sql` y desplegar la rama en preprod (guía `Apuntes/despliegue_preprod.md`), con una cuenta de supertécnico de `~/.env.e2e`:

1. Marcar «Por cerrar» una asignación de reparación normal con cliente de un técnico de prueba. Comprobar en la BD que tiene `POR_CERRAR_AT` con la hora de ahora.
2. En el panel de carga («Pedidos» y «Total»), el ✓ de ese técnico sube 11/12 del peso de una normal (lunes o martes: +4,6 puntos) y el tooltip enseña la «por cerrar» en «Hecho hoy» y en «Pendiente».
3. Quitar la marca: `POR_CERRAR_AT` vuelve a NULL y el panel vuelve a lo de antes.
4. Marcar otra vez y terminar la asignación: el ✓ sube el peso entero de una normal y no queda nada pendiente de ella.
5. Limpiar los datos de prueba.
