# Urgente por IMEI — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un IMEI con trabajos abiertos tiene UN solo estado de urgencia: marcar/quitar/heredar se propaga a todas sus asignaciones abiertas — según la spec `docs/superpowers/specs/2026-07-21-urgente-por-imei-design.md`.

**Architecture:** Opción B de la spec — sin ALTER: el flag sigue en `Reparacion.URGENTE`, pero las tres puertas de escritura (PATCH manual, altas de asignación, job diario) pasan a operar por IMEI sobre las filas abiertas (`FECHA_FIN IS NULL`). Cliente: solo visibilidad del menú en pulido.

**Tech Stack:** Java 17 · Spring Boot + JdbcTemplate (servidor) · JUnit 5 + Mockito (servidor) · JavaFX 21 (cliente).

## Global Constraints

- **Dos repos**: raíz `c:\Users\info\Documents\ProgramaReparaciones` y servidor `...\gestion-reparaciones-servidor` (repo git propio). Ramas: **`feature/urgente-por-imei`** en cada repo — la del servidor la crea T1 desde su main `9b54f78+`; la de raíz la crea T2 desde main `ac6853e+`.
- **Commits SIN `Co-Authored-By`** ni trailers; mensajes `feat/fix(ámbito): descripción en español`.
- **Merge, push y tags: SOLO con OK explícito del usuario.** Sin ALTER ni migración; orden de despliegue libre (servidor primero por costumbre).
- **Comandos por Bash.** Suites: servidor `cd gestion-reparaciones-servidor && mvn -q test`; cliente `cd gestion-reparaciones-cliente && mvn -q test`.
- **Contratos intactos**: la URL `PATCH /api/reparaciones/asignaciones/{idRep}/urgente` y todos los `@PreAuthorize` NO cambian; los SELECT de lectura no se tocan.
- Invariante de la spec: "abiertas" = `FECHA_FIN IS NULL`; propagación siempre server-side.

## Contexto imprescindible (leído del código real)

- **Servidor `dao/ReparacionDAO.java`**: `actualizarUrgente(String idRep, boolean)` L603-605 (UPDATE por ID_REP — a sustituir); `marcarUrgentesClienteVencidas(Timestamp)` L617-627 (UPDATE por fila — a sustituir); altas: `insertarAsignacion` L390-397 (prefijo A, columna URGENTE en el INSERT), `insertarAsignacionGlass` L401-408 (AG, URGENTE en el INSERT), `insertarAsignacionPulido` L954-963 (AP, SIN columna URGENTE), `marcarIncidenciaYAsignar` L680-691 (A/AG por reincidencia, SIN columna URGENTE). Todas `@Transactional`, todas hacen `ensureTelefono(imei)` + `nextId(...)`. Existe `getImeiByIdRep(String)` (usado por el controller L231). El constructor del DAO inyecta `JdbcTemplate` y colaboradores — **verificar la firma real** antes de escribir el test y calcar la instanciación.
- **Servidor `controller/ReparacionController.java`**: PATCH urgente L225-234 (delega en `dao.actualizarUrgente` y loguea `MARCAR_URGENTE` con IMEI) — NO cambia.
- **Servidor `job/UrgenteAutomaticoJob.java`**: cron 00:00 Madrid, llama `marcarUrgentesClienteVencidas(cutoff)` — NO cambia (cambia el SQL del DAO). Test existente `UrgenteAutomaticoJobTest` (mockea el DAO) — sigue verde sin tocar.
- **Cliente `controllers/PendientesSuperTecnicoController.java`**: menú contextual L370-379 (`toggleUrgente` → `reparacionDAO.actualizarUrgente(idRep, nuevo)` + `cargar()`); gating L418 `toggleUrgente.setVisible(!soloLectura && !esPulido)`. `cargar()` recarga la tabla entera → todas las filas del IMEI se refrescan coherentes sin más cambios.
- **Cliente `dao/ReparacionDAO.java`**: `actualizarUrgente` L429-431 (PATCH — no cambia de firma ni URL).

## Estructura de ficheros

**Servidor — Create:** `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOUrgenteTest.java`.
**Servidor — Modify:** `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`.
**Cliente — Modify:** `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (1 línea), `src/main/java/com/reparaciones/dao/ReparacionDAO.java` (javadoc).

---

### Task 1: Servidor — propagación por IMEI en `ReparacionDAO` (TDD Mockito)

**Files:**
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOUrgenteTest.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Produces (internas al DAO, package-private): `boolean tieneAbiertaUrgente(String imei)`, `int propagarUrgente(String imei, boolean urgente)`.
- Produces (públicas, firma intacta pero semántica nueva): `actualizarUrgente(String idRep, boolean urgente)` propaga a todas las abiertas del IMEI; `marcarUrgentesClienteVencidas(Timestamp)` marca todas las abiertas de los teléfonos que cualifican; las 4 altas heredan/propagan.

- [ ] **Paso 1: Crear la rama del servidor** — `git checkout main && git checkout -b feature/urgente-por-imei` (main en `9b54f78` o posterior).

- [ ] **Paso 2: Test que falla** — `ReparacionDAOUrgenteTest` (Mockito puro, patrón `ProveedorDAOTest`; ANTES verificar el constructor real de `ReparacionDAO` —qué colaboradores inyecta además de `JdbcTemplate`— y calcar la instanciación mockeando lo que haga falta; si `getImeiByIdRep` no contiene literalmente `"SELECT IMEI"`, ajustar el matcher a su SQL real — el comportamiento afirmado no cambia):

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReparacionDAOUrgenteTest {

    private static final String IMEI = "351111112222333";

    @Test void propagarUrgenteActualizaTodasLasAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc);   // ajustar al constructor real
        dao.propagarUrgente(IMEI, true);
        verify(jdbc).update("UPDATE Reparacion SET URGENTE = ?, UPDATED_AT = UPDATED_AT WHERE IMEI = ? AND FECHA_FIN IS NULL",
                true, IMEI);
    }

    @Test void tieneAbiertaUrgenteConsultaSoloAbiertasUrgentes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(2);
        ReparacionDAO dao = new ReparacionDAO(jdbc);
        assertTrue(dao.tieneAbiertaUrgente(IMEI));
    }

    @Test void actualizarUrgenteResuelveElImeiYPropaga() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("SELECT IMEI"), eq(String.class), eq("A20260721_1")))
                .thenReturn(IMEI);
        ReparacionDAO dao = new ReparacionDAO(jdbc);
        dao.actualizarUrgente("A20260721_1", false);
        verify(jdbc).update(contains("WHERE IMEI = ? AND FECHA_FIN IS NULL"), eq(false), eq(IMEI));
    }

    @Test void marcarUrgentesClienteVencidasMarcaTodoElTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc);
        java.sql.Timestamp cutoff = java.sql.Timestamp.valueOf("2026-07-21 00:00:00");
        dao.marcarUrgentesClienteVencidas(cutoff);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(cutoff));
        String s = sql.getValue();
        assertTrue(s.contains("SELECT DISTINCT r2.IMEI"));                       // cualifica por teléfono
        assertTrue(s.contains("r2.ID_REP LIKE 'A%' AND r2.ID_REP NOT LIKE 'AP%'")); // disparador: rep/glass
        assertTrue(s.contains("t.ID_CLI IS NOT NULL"));
        assertTrue(s.contains("WHERE r.FECHA_FIN IS NULL AND r.URGENTE = FALSE"));  // marca TODAS las abiertas
    }
}
```

- [ ] **Paso 3: Ver que falla** — `cd gestion-reparaciones-servidor && mvn -q test -Dtest=ReparacionDAOUrgenteTest` → no compila (`propagarUrgente`/`tieneAbiertaUrgente` no existen).

- [ ] **Paso 4: Implementar en `ReparacionDAO`**
  1. Primitivas nuevas (junto a `actualizarUrgente`, L603):

```java
/** ¿Tiene el IMEI alguna asignación abierta marcada urgente? */
boolean tieneAbiertaUrgente(String imei) {
    Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND FECHA_FIN IS NULL AND URGENTE = TRUE",
            Integer.class, imei);
    return n != null && n > 0;
}

/** Fija URGENTE en TODAS las asignaciones abiertas del IMEI (invariante: un estado por teléfono). */
int propagarUrgente(String imei, boolean urgente) {
    return jdbc.update(
            "UPDATE Reparacion SET URGENTE = ?, UPDATED_AT = UPDATED_AT WHERE IMEI = ? AND FECHA_FIN IS NULL",
            urgente, imei);
}
```

  2. `actualizarUrgente` (L603-605) pasa a resolver el IMEI y propagar (añadir `@Transactional`):

```java
@Transactional
public void actualizarUrgente(String idRep, boolean urgente) {
    String imei = getImeiByIdRep(idRep);
    if (imei == null) return;   // id inexistente: no-op (hoy, 0 filas actualizadas)
    propagarUrgente(imei, urgente);
}
```

  3. Herencia + propagación inversa en las 4 altas (todas ya `@Transactional`):
     - `insertarAsignacion` L390: antes del INSERT, `boolean urgenteFinal = urgente || tieneAbiertaUrgente(imei);`; el INSERT usa `urgenteFinal`; tras el INSERT, `if (urgenteFinal) propagarUrgente(imei, true);`.
     - `insertarAsignacionGlass` L401: idéntico.
     - `insertarAsignacionPulido` L955 y `marcarIncidenciaYAsignar` L681 (sin checkbox — heredan en silencio): tras su INSERT, `if (tieneAbiertaUrgente(imei)) propagarUrgente(imei, true);` (la propagación incluye la fila recién creada).
     - Los INSERT con `FECHA_FIN` en la propia alta (`insertar` L381, `insertarCompleta`, completadas de glass/pulido) NO se tocan: nacen cerradas, la urgencia no aplica.
  4. `marcarUrgentesClienteVencidas` (L617-627) — mismo disparador, efecto por teléfono:

```java
/** Marca URGENTE=true en TODAS las asignaciones abiertas (pulido incluido) de los
 *  teléfonos que cualifican: alguna rep/glass pendiente no urgente, con cliente,
 *  cuya FECHA_ASIG es anterior al cutoff (inicio de hoy en Madrid). Devuelve nº de filas. */
public int marcarUrgentesClienteVencidas(java.sql.Timestamp cutoffUtc) {
    return jdbc.update(
        "UPDATE Reparacion r JOIN (" +
        "  SELECT DISTINCT r2.IMEI FROM Reparacion r2" +
        "  JOIN Telefono t ON t.IMEI = r2.IMEI" +
        "  WHERE r2.ID_REP LIKE 'A%' AND r2.ID_REP NOT LIKE 'AP%'" +
        "    AND r2.FECHA_FIN IS NULL AND t.ID_CLI IS NOT NULL" +
        "    AND r2.URGENTE = FALSE AND r2.FECHA_ASIG < ?" +
        ") q ON q.IMEI = r.IMEI " +
        "SET r.URGENTE = TRUE, r.UPDATED_AT = r.UPDATED_AT " +
        "WHERE r.FECHA_FIN IS NULL AND r.URGENTE = FALSE",
        cutoffUtc);
}
```

- [ ] **Paso 5: Verde + suite completa** — `mvn -q test -Dtest=ReparacionDAOUrgenteTest` verde → `mvn -q test` (24 existentes + 4 nuevos; `UrgenteAutomaticoJobTest` debe seguir verde sin tocarlo).

- [ ] **Paso 6: Commit (repo SERVIDOR)**

```bash
git add src && git commit -m "feat(urgente): propagación por IMEI — marcar/heredar/job actúan sobre todas las asignaciones abiertas del teléfono"
```

---

### Task 2: Cliente — menú urgente visible en pulido + javadoc

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (L418), `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` (javadoc L429)

**Interfaces:**
- Consumes: el PATCH propagado de T1 (misma URL/firma).

- [ ] **Paso 1: Crear la rama de raíz** — `git checkout main && git checkout -b feature/urgente-por-imei` (main en `ac6853e+`).

- [ ] **Paso 2: Visibilidad** — en `PendientesSuperTecnicoController` L418, el toggle deja de excluir pulido (actúa sobre el teléfono):

```java
                    toggleUrgente.setVisible(!soloLectura);
```

  (La línea hoy es `toggleUrgente.setVisible(!soloLectura && !esPulido);`. El resto del menú —`editarModelo`, `toggleChasis`, textos L421/424— NO cambia; `esPulido` sigue usándose en L417.)

- [ ] **Paso 3: Javadoc del DAO cliente** — en `dao/ReparacionDAO.actualizarUrgente` (L429), documentar la semántica nueva:

```java
    /** Marca/desmarca urgente. Desde 2026-07 el servidor propaga el valor a TODAS
     *  las asignaciones abiertas del mismo IMEI (urgencia por teléfono, spec 2026-07-21). */
```

- [ ] **Paso 4: Suite + commit (repo RAÍZ)** — `cd gestion-reparaciones-cliente && mvn -q test` → verde (164). Stagear SOLO `gestion-reparaciones-cliente/src` (NUNCA el gitlink del submódulo).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(urgente): toggle de urgente visible también en pulido — actúa sobre el teléfono completo"
```

---

### Task 3: Cierre — suites, review final, smoke y merges con OK

- [ ] Suites finales verdes en ambos repos (`mvn -q test`).
- [ ] Review final de rama con `review-package` en ambos repos (merge-base con main).
- [ ] Smoke del usuario (spec §7): marcar urgente en una fila → todas las del IMEI (incl. pulido) lucen el badge; asignar 2º técnico a un IMEI urgente → nace urgente; crear asignación con checkbox sobre IMEI con abiertas normales → todas pasan a urgentes; quitar urgente → todas se apagan; cerrar el último trabajo → la siguiente asignación del IMEI nace normal. Regresión: pendientes/CSV idénticos en columnas, badge como siempre.
- [ ] Merges SOLO con OK: servidor primero (merge --no-ff + push + build VM), luego cliente (merge --no-ff + push) + bump gitlink. Checkbox en `Apuntes/plan-futuro.md` si el usuario lo pide + memoria.

---

## Self-review (hecho al escribir el plan)

- **Cobertura de spec**: §3.1 PATCH propagado (T1.4.2) ✓ · §3.2 herencia + propagación inversa en las 4 altas, pulido/incidencia sin checkbox heredan (T1.4.3) ✓ · §3.3 job por teléfono mismo disparador (T1.4.4) ✓ · §4 ciclo híbrido emerge de operar solo sobre abiertas ✓ · §5 menú visible en pulido (T2.2), badges/CSV sin cambios ✓ · §6 sin ALTER, URL intacta, compat ✓ · §7 tests Mockito (T1.2) + smoke (T3) ✓ · §8 fuera de alcance respetado (incidencia solo hereda) ✓.
- **Consistencia de tipos**: `tieneAbiertaUrgente(String)`/`propagarUrgente(String, boolean)` idénticos en test (T1.2), implementación (T1.4.1) y wiring (T1.4.3); `actualizarUrgente(String, boolean)` conserva firma pública (controller y cliente intactos).
- **Sin placeholders**: los dos "verificar en código real" (constructor del DAO para el test, SQL exacto de `getImeiByIdRep` para el matcher) son lecturas obligadas del implementador, no huecos de diseño.
- **Nota**: el conteo que loguea el job ahora incluye las filas de pulido arrastradas — mensaje del log sin cambios, semántica del número ligeramente más amplia (aceptado).
