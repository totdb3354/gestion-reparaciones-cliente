# Auto-revisión al reparar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al guardar filas de reparación con SKU de batería o chasis, la revisión vigente se actualiza sola (batería→100, grado→A), con marca "(auto)" y rastro en el log.

**Architecture:** Solo servidor (Spring Boot + JdbcTemplate). Un método nuevo en `RevisionDAO` que aplica las reglas sobre la pasada vigente y devuelve el sufijo de log; los 2 endpoints con filas de `ReparacionController` lo llaman reutilizando los tipos que ya derivan para el log COMPONENTES. Cero cliente, cero migraciones.

**Tech Stack:** Java 21, Spring Boot 3, JdbcTemplate, JUnit 5 + Mockito (a mano, patrón de la casa).

**Spec:** `docs/superpowers/specs/2026-07-30-auto-revision-reparar-design.md`.

## Global Constraints

- Commits **sin** trailer `Co-Authored-By`. Merge/push SOLO con OK explícito del usuario.
- Comandos por **Bash** (Maven incluido): `mvn -q test` en el repo servidor.
- Rama servidor: `feature/auto-revision` (la crea la Task 1 desde main `6b30768`). La raíz NO tiene rama (spec/plan van en main local; gitlink se bumpea al cierre con OK).
- Prefijos de SKU EXACTOS (convención real de `Componente.TIPO`, minúsculas): batería = `startsWith("bat")`, chasis = `startsWith("cha")`.
- **Solo valores, nunca autoría/fechas de parte**: el UPDATE no toca `EST_ID_USU`, `FUN_ID_USU`, `EST_FECHA`, `FUN_FECHA` (spec §3.1 — los estados derivados siguen honestos).
- Textos EXACTOS: marcador batería `"Batería 100% (auto: cambio de batería)"`; marcador grado `"Grado A (auto: chasis nuevo)"`; separador de anexo `" · "`; sufijos de log `", AUTO: batería 100"` / `", AUTO: grado A"` / ambos `", AUTO: batería 100, grado A"`.
- `FUN_OBSERVACION` es VARCHAR(500): si el anexo supera 500 chars, truncar a 499 + `"…"`.
- `PATCH /{idRep}/completar` NO se toca (sin filas, spec §2). Glass/pulido sin hooks (verificación en T2 Step 3).
- TDD: test RED antes de la implementación.

---

### Task 1: Servidor — RevisionDAO#aplicarAutoPorComponentes

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/RevisionDAOAutoTest.java` (nuevo)

**Interfaces:**
- Consumes: `getVigente(String imei)` (existente, L114: query `"SELECT r.*, ... FROM Revision r ... ORDER BY r.ID_REVISION DESC LIMIT 1"`), modelo `Revision` (getters `getIdRevision()`, `getFunBateriaPct() → Integer`, `getEstGrado() → String`, `getFunObservacion() → String`).
- Produces (para T2): `public String aplicarAutoPorComponentes(String imei, List<String> tipos)` → sufijo de log (`""` si no escribió nada). NOTA: difiere de la firma indicativa de la spec §6 (sin `idUsu` — el autor queda en el log del controller; devuelve el sufijo para ese log).

- [ ] **Step 1: Rama servidor**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only && git checkout -b feature/auto-revision
```
Verificar: `git log --oneline -1` muestra `6b30768` (o descendiente en main).

- [ ] **Step 2: Test RED**

`RevisionDAOAutoTest.java` (patrón Mockito a mano de `RevisionDAOEstadoTest`):

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOAutoTest {

    private static final String IMEI = "351111112222333";

    /** jdbc con revisión vigente stubbeada (id 7, batería y grado dados, obs dada). */
    @SuppressWarnings("unchecked")
    private JdbcTemplate conVigente(Integer bateria, String grado, String obs) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Revision r = new Revision();
        r.setIdRevision(7);
        r.setImei(IMEI);
        r.setFunBateriaPct(bateria);
        r.setEstGrado(grado);
        r.setFunObservacion(obs);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(r));
        return jdbc;
    }

    private RevisionDAO dao(JdbcTemplate jdbc) {
        return new RevisionDAO(jdbc, mock(MovimientoDAO.class));
    }

    @Test void bateriaNuevaEscribe100YAnexaMarcador() {
        JdbcTemplate jdbc = conVigente(78, "B", null);
        String sufijo = dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("bat13", "otro13tornillos"));
        assertEquals(", AUTO: batería 100", sufijo);
        verify(jdbc).update("UPDATE Revision SET FUN_BATERIA_PCT = 100, FUN_OBSERVACION = ? WHERE ID_REVISION = ?",
                "Batería 100% (auto: cambio de batería)", 7);
    }

    @Test void chasisNuevoEscribeGradoAYAnexaSobreObsExistente() {
        JdbcTemplate jdbc = conVigente(90, "B", "rayas leves");
        String sufijo = dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("cha13negra"));
        assertEquals(", AUTO: grado A", sufijo);
        verify(jdbc).update("UPDATE Revision SET EST_GRADO = 'A', FUN_OBSERVACION = ? WHERE ID_REVISION = ?",
                "rayas leves · Grado A (auto: chasis nuevo)", 7);
    }

    @Test void bateriaYChasisJuntosUnSoloUpdate() {
        JdbcTemplate jdbc = conVigente(60, "C", null);
        String sufijo = dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("bat13", "cha13negra"));
        assertEquals(", AUTO: batería 100, grado A", sufijo);
        verify(jdbc).update("UPDATE Revision SET FUN_BATERIA_PCT = 100, EST_GRADO = 'A', FUN_OBSERVACION = ? WHERE ID_REVISION = ?",
                "Batería 100% (auto: cambio de batería) · Grado A (auto: chasis nuevo)", 7);
    }

    @Test void idempotenteSiYaEstaba() {
        JdbcTemplate jdbc = conVigente(100, "A", "ya tocado");
        assertEquals("", dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("bat13", "cha13negra")));
        verify(jdbc, never()).update(anyString(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void sinPasadaVigenteNoEscribe() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        assertEquals("", dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("bat13")));
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test void sinSkuRelevanteNiConsultaLaVigente() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertEquals("", dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("g13negra", "lcd13", "?")));
        assertEquals("", dao(jdbc).aplicarAutoPorComponentes(IMEI, null));
        assertEquals("", dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of()));
        verifyNoInteractions(jdbc);
    }

    @Test void obsLargaSeTruncaA500() {
        String obsLarga = "x".repeat(495);
        JdbcTemplate jdbc = conVigente(50, "A", obsLarga);
        dao(jdbc).aplicarAutoPorComponentes(IMEI, List.of("bat13"));
        org.mockito.ArgumentCaptor<String> obs = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("FUN_BATERIA_PCT = 100"), obs.capture(), eq(7));
        assertEquals(500, obs.getValue().length());
        assertTrue(obs.getValue().endsWith("…"));
    }
}
```

- [ ] **Step 3: RED**

```bash
mvn -q test -Dtest=RevisionDAOAutoTest
```
Expected: FAIL de compilación (`aplicarAutoPorComponentes` no existe).

- [ ] **Step 4: Implementar en RevisionDAO**

Añadir al final de la clase (antes del cierre), junto con el import `java.util.List` si falta:

```java
    /**
     * Auto-revisión al reparar (spec 2026-07-30): si el trabajo consumió batería (SKU "bat…")
     * o chasis ("cha…"), la pasada vigente gana batería=100 / grado=A. SOLO valores — nunca
     * autoría ni fechas de parte (la funcional/estética siguen pendientes si no se guardaron).
     * Idempotente: no escribe si el valor ya estaba. Devuelve el sufijo para el log ("" si nada).
     */
    public String aplicarAutoPorComponentes(String imei, List<String> tipos) {
        if (tipos == null || tipos.isEmpty()) return "";
        boolean bat = tipos.stream().anyMatch(t -> t != null && t.startsWith("bat"));
        boolean cha = tipos.stream().anyMatch(t -> t != null && t.startsWith("cha"));
        if (!bat && !cha) return "";
        Revision v = getVigente(imei);
        if (v == null) return "";
        StringBuilder set = new StringBuilder();
        List<String> partesLog = new java.util.ArrayList<>();
        String obs = v.getFunObservacion();
        if (bat && !Integer.valueOf(100).equals(v.getFunBateriaPct())) {
            set.append("FUN_BATERIA_PCT = 100, ");
            obs = anexarObs(obs, "Batería 100% (auto: cambio de batería)");
            partesLog.add("batería 100");
        }
        if (cha && !"A".equals(v.getEstGrado())) {
            set.append("EST_GRADO = 'A', ");
            obs = anexarObs(obs, "Grado A (auto: chasis nuevo)");
            partesLog.add("grado A");
        }
        if (set.length() == 0) return "";
        jdbc.update("UPDATE Revision SET " + set + "FUN_OBSERVACION = ? WHERE ID_REVISION = ?",
                obs, v.getIdRevision());
        return ", AUTO: " + String.join(", ", partesLog);
    }

    /** Anexa el marcador "(auto)" a la observación, con separador " · " y tope VARCHAR(500). */
    private static String anexarObs(String obs, String marcador) {
        String out = (obs == null || obs.isBlank()) ? marcador : obs + " · " + marcador;
        return out.length() > 500 ? out.substring(0, 499) + "…" : out;
    }
```

- [ ] **Step 5: GREEN + suite completa**

```bash
mvn -q test -Dtest=RevisionDAOAutoTest && mvn -q test
```
Expected: PASS; suite completa verde (79 previos + 7 nuevos = 86).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/test/java/com/reparaciones/servidor/dao/RevisionDAOAutoTest.java
git commit -m "feat(auto-revision): bateria 100 y grado A automaticos en la pasada vigente"
```

---

### Task 2: Servidor — wiring en los 2 endpoints con filas

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (constructor + `/completa` ~L199 + `/{idAsignacion}/filas` ~L366 + helper `componentesDe` ~L459 — localizar por contenido, no por línea)

**Interfaces:**
- Consumes: `RevisionDAO#aplicarAutoPorComponentes(String imei, List<String> tipos)` (T1); helper existente `componentesDe(List<FilaReparacion>)` y su derivación de tipos por `componenteDao.getTipoById(f.idCom)`.
- Produces: detalle de log de `/completa` y `/filas` con sufijo `", AUTO: …"` cuando el automatismo escribió.

- [ ] **Step 1: Refactor del helper — tipos reutilizables**

Sustituir el helper actual (que deriva y une en un paso) por dos:

```java
    /** Tipos de los componentes consumidos (por idCom; "?" si la consulta falla). */
    private List<String> tiposDe(List<FilaReparacion> filas) {
        if (filas == null) return List.of();
        return filas.stream()
                .filter(f -> f.idCom > 0)
                .map(f -> { try { return componenteDao.getTipoById(f.idCom); } catch (Exception e) { return "?"; } })
                .collect(java.util.stream.Collectors.toList());
    }

    /** Detalle de log de los componentes consumidos ("" si no hay filas con pieza). */
    private String componentesDe(List<String> tipos) {
        return tipos.isEmpty() ? "" : ", COMPONENTES: " + String.join(", ", tipos);
    }
```

(La firma de `componentesDe` cambia de `List<FilaReparacion>` a `List<String>` — actualizar sus 2 call sites, ver Steps 2. La salida del log queda byte-idéntica.)

- [ ] **Step 2: Inyectar RevisionDAO y llamar desde los 2 endpoints**

1. Campo + constructor: añadir `private final RevisionDAO revisionDao;` junto a los campos existentes y el parámetro correspondiente al constructor (calcar el patrón de `componenteDao`, añadido en F2c).
2. `/completa` (`insertarCompleta`): tras `dao.insertarCompleta(...)`:

```java
        List<String> tipos = tiposDe(req.filas());
        String auto = revisionDao.aplicarAutoPorComponentes(req.imei(), tipos);
```

y el detalle del log pasa de `... + componentesDe(req.filas())` a `... + componentesDe(tipos) + auto`.
3. `/{idAsignacion}/filas` (`guardarFilaIndividual`): ídem — tras `dao.guardarFilaIndividual(...)`:

```java
        List<String> tipos = tiposDe(req.filas());
        String auto = revisionDao.aplicarAutoPorComponentes(req.imei(), tipos);
```

y el log `... + componentesDe(tipos) + auto`.
4. `PATCH /{idRep}/completar` NO se toca.

- [ ] **Step 3: Verificación glass/pulido sin hooks**

```bash
grep -n "componentesDe\|aplicarAutoPorComponentes" src/main/java/com/reparaciones/servidor/controller/*.java
```
Expected: solo `ReparacionController` (2 call sites de cada). Verificar además que `GlassController`/`PulidoController` no insertan filas con `idCom` de tipos `bat`/`cha` por sus flujos (leer sus endpoints de completar; si alguno guardara filas con componentes arbitrarios, PARAR y reportar BLOCKED — la spec asume que no).

- [ ] **Step 4: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat(auto-revision): wiring en completa y guardar-fila con sufijo AUTO en el log"
```
Expected: BUILD SUCCESS, 86 tests (sin tests nuevos: wiring fino, cubierto por T1 + review + smoke).

---

### Task 3: Cierre — suites, review final y pasos del usuario

**Files:** ninguno nuevo (verificación + operativa).

- [ ] **Step 1: Suite final del servidor**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q test
```
Expected: BUILD SUCCESS (anotar cifra real, esperado 86).

- [ ] **Step 2: Review final de rama** (superpowers:requesting-code-review) — contra la spec: regla solo-valores (grep que el UPDATE no menciona `_ID_USU` ni `_FECHA`), idempotencia, textos exactos de marcadores/sufijos, truncado 500, glass/pulido sin hooks, `PATCH /completar` intacto.

- [ ] **Step 3: Pasos del usuario (en orden, cada uno con su OK):**

1. Arranque Spring local con el jar de la rama (contexto limpio; sin migraciones que aplicar).
2. OK merge servidor (`--no-ff`) + push → `git pull` + build + restart systemd en la VM (usuario).
3. Smoke corto (cliente main, sin cambios): (a) teléfono con revisión completa y batería <85 → OK vetado; cambiarle batería (fila con SKU `bat…`) → ficha muestra 100 + observación "(auto)" → OK habilitado; (b) reparación de chasis → ficha grado A + "(auto)"; (c) teléfono con batería ya 100 → re-completar no duplica marcador; (d) log con `", AUTO: …"`; (e) teléfono histórico/sin pasada → completar no rompe.
4. OK bump gitlink en raíz main + push.
5. Ledger + plan-futuro checkbox mini-fase → SUPER SMOKE desde v0.16 → decisión tag v0.17.0.

- [ ] **Step 4: Ledger** — anotar cierre en `.superpowers/sdd/progress.md`.
