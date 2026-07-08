# Marca "Por cerrar" + % de carga por técnico — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la spec `docs/superpowers/specs/2026-07-08-por-cerrar-carga-design.md`: marca manual "Por cerrar" en asignaciones de reparación normal (badge verde-glass, marcan técnico y supertécnico) y porcentaje de carga por técnico (asignaciones con cliente, pesos 1/2/0,083, visible solo para supertécnico y admin en la vista Asignaciones y el modal de asignar).

**Architecture:** Columna `POR_CERRAR` en `Reparacion` + PATCH clonado del patrón urgente/chasis (con permisos: técnico solo sus asignaciones). El % de carga es cálculo **puro en cliente** (`CargaTecnicos`, testeada) sobre las asignaciones ya cargadas; la UI lo pinta en una franja de chips y en el modal de asignar, y se recalcula en cada `cargar()`.

**Tech Stack:** Java 17 · JavaFX 21 · Spring Boot 3.3.4 + JdbcTemplate · MariaDB · JUnit 5.

## Restricciones globales

- **Rama `feature/por-cerrar-carga` YA CREADA en ambos repos** (raíz `c:\Users\info\Documents\ProgramaReparaciones` y servidor anidado `gestion-reparaciones-servidor`) — verificar con `git branch --show-current` antes de tocar nada; NO cambiar de rama. La F2a está aparcada en otras ramas: **NO tocar** `AgrupadoController`, `TelefonoDAO`, ni el esquema de `Telefono`.
- Commits SIN `Co-Authored-By`. Mensajes `feat(ámbito): descripción en español`. Los commits de servidor con cwd en `gestion-reparaciones-servidor` (repo propio); los de cliente en el repo raíz.
- **Migraciones SQL: NUNCA ejecutarlas** — solo escribir el fichero en `gestion-reparaciones-servidor/sql/`; las aplica el usuario.
- Comandos por Bash: cliente `cd gestion-reparaciones-cliente && mvn -q test`; servidor `cd gestion-reparaciones-servidor && mvn -q test`.
- JSON cliente↔servidor por nombre de campo exacto (Gson por campo, Jackson por getter/record): el flag se llama **`porCerrar`** en ambos lados.
- Merge/push/tag: SOLO con OK del usuario. El plan termina con la rama local lista.
- Pesos de carga (spec §3, exactos): normal = 1 · chasis = 2 · por cerrar = **1.0/12** (≈0,083; "por cerrar" MANDA sobre chasis) · glass = 1 · pulido y asignaciones sin cliente NO cuentan. % = carga técnico / carga total × 100, redondeado a entero con `Math.round`.
- Paleta badge "Por cerrar" (= badge Glass): fondo `#E0F2F1`, texto `#00796B`.

## Contexto imprescindible (leído del código real)

- **Servidor** `ReparacionController` (`src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`): los PATCH a clonar están en las líneas ~217-237: `actualizarUrgente` (`/asignaciones/{idRep}/urgente`, record `UrgenteRequest`) y `actualizarChasis` (`/asignaciones/{idRep}/chasis`, record `ChasisRequest`), ambos `@PreAuthorize("hasRole('SUPERTECNICO')")`, sin updatedAt, con log `"MARCAR_URGENTE"`/`"MARCAR_CHASIS"` usando `dao.getImeiByIdRep(idRep)`. `dao.getAsignacionAnyById(idRep)` devuelve `Optional<ReparacionResumen>` (con `getIdTec()`). `UsuarioPrincipal` tiene `getIdUsu()`, `getRol()` (String `"SUPERTECNICO"`/`"TECNICO"`/`"ADMIN"`) y `getIdTec()` (Integer, null si no es técnico).
- **Servidor** `ReparacionDAO` (`dao/ReparacionDAO.java`): `actualizarUrgente` está en la línea ~403 (UPDATE simple sin lock); `getAsignaciones` en ~168. El modelo `model/ReparacionResumen.java` tiene los flags `urgente`/`esChasis` como campos con getter/setter fuera del constructor grande (líneas ~29-34); se rellenan en el/los RowMapper de asignaciones — buscar dónde se hace `setUrgente(`/`ES_CHASIS` en el DAO y clonar.
- **Cliente** `dao/ReparacionDAO.java` líneas 419-427: `actualizarUrgente`/`actualizarChasis` → `ApiClient.patch("/api/reparaciones/asignaciones/" + idRep + "/urgente", Map.of("urgente", urgente))`. Clonar.
- **Cliente** `models/ReparacionResumen.java`: añadir campo `porCerrar` + getter/setter basta — Gson deserializa por nombre de campo, no usa el constructor.
- **Cliente** `controllers/PendientesSuperTecnicoController.java`: componente COMPARTIDO supertécnico/admin (`soloLectura`). Menú contextual de fila con `toggleUrgente`/`toggleChasis` y su `menu.setOnShowing` en ~405-421 (visibilidad `!soloLectura && esRep`, texto dinámico). Badge stack en `cEstado` cellFactory ~461-510 (`VBox celdaBox` con `badgeUrgente` + `badge`). El controller ya tiene `reparacionDAO`, la lista `tecnicos` (List<Tecnico>) y `datos`/`cargar()`. El modal de asignación masiva se construye en código (~900 técnico por defecto, ~1200+ modal); los técnicos se pintan con `t.getNombre()` en ComboBox/ListCell.
- **Cliente** `controllers/PendientesTecnicoController.java`: menú contextual de fila SOLO con "Copiar celda" (`menu.getItems().add(copiar)` línea ~147, dentro del rowFactory ~116). Badge stack en `cEstado` ~183-249 (`celdaBox` = VBox con `badgeUrgente`, `badge`, `lblTipo`). Tiene flag `glass` (el mismo controller sirve la vista de pendientes glass) y campo `reparacionDAO`; las filas son SUS asignaciones. `Sesion.getIdTec()` da el id del técnico logueado.
- **Cliente** `controllers/LogController.java` líneas 42-59: lista `TIPOS_ACCION` (hay `LogControllerTest` — comprobar si asserta la lista).
- **FXML** `views/PendientesSuperTecnicoView.fxml`: VBox raíz → HBox título (L14-17) → FlowPane filtros (L18-31) → TableView (L33). La franja de carga se inserta ENTRE el FlowPane y el TableView.
- `ConfirmDialog.mostrarTexto(String titulo, String texto)` existe (se usa para observaciones/incidencias).
- `TipoTrabajo.desde(idRep)`: `AG`/`G`→GLASS, `AP`/`P`→PULIDO, resto→REPARACION.

## Estructura de ficheros

**Servidor:** Create `sql/migracion-por-cerrar.sql` · Modify `sql/crear_bd.sql`, `model/ReparacionResumen.java`, `dao/ReparacionDAO.java`, `controller/ReparacionController.java`.
**Cliente:** Create `utils/CargaTecnicos.java` + `src/test/java/com/reparaciones/utils/CargaTecnicosTest.java` · Modify `models/ReparacionResumen.java`, `dao/ReparacionDAO.java`, `controllers/LogController.java`, `controllers/PendientesSuperTecnicoController.java`, `controllers/PendientesTecnicoController.java`, `views/PendientesSuperTecnicoView.fxml`.

---

### Tarea 1: SQL + servidor completo (columna, DAO, endpoint con permisos, log)

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-por-cerrar.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (tabla `Reparacion`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

**Interfaces:**
- Produces (lo consumen T2/T4): campo JSON **`porCerrar`** (boolean) en los `ReparacionResumen` que devuelven `GET /api/reparaciones/asignaciones` (y variantes por técnico/imei/id); endpoint `PATCH /api/reparaciones/asignaciones/{idRep}/por-cerrar` body `{"porCerrar": true|false}` → 204; 404 si no existe; **422** si el ID no es de reparación normal (`A%` sí, `AG%`/`AP%` no); **403** si el rol no puede (TECNICO sobre asignación ajena, o ADMIN).

- [ ] **Paso 1: Verificar rama** — `cd gestion-reparaciones-servidor && git branch --show-current` → `feature/por-cerrar-carga`. Si no, PARAR y reportar BLOCKED.

- [ ] **Paso 2: `sql/migracion-por-cerrar.sql`**

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-por-cerrar.sql — Marca "Por cerrar" en asignaciones de reparación
-- La aplica el usuario a mano en gestion_reparaciones (orden: ALTER → servidor → cliente).
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Asignación de reparación normal mayoritariamente hecha: solo queda cerrar el
-- móvil (habitualmente esperando el glass de otro técnico). Marca manual.
ALTER TABLE Reparacion
    ADD COLUMN POR_CERRAR BOOLEAN NOT NULL DEFAULT FALSE AFTER ES_CHASIS;
```

- [ ] **Paso 3: `crear_bd.sql`** — en `CREATE TABLE Reparacion`, añadir tras la línea de `ES_CHASIS`:

```sql
    POR_CERRAR           BOOLEAN      NOT NULL DEFAULT FALSE,
```

(alineado con el estilo de columnas de esa tabla).

- [ ] **Paso 4: Modelo servidor** — en `model/ReparacionResumen.java`, junto a `urgente`/`esChasis` (líneas ~29-34): añadir campo `private boolean porCerrar;` y, junto a sus getters/setters, `public boolean isPorCerrar() { return porCerrar; }` y `public void setPorCerrar(boolean v) { this.porCerrar = v; }`.

- [ ] **Paso 5: DAO servidor** — en `dao/ReparacionDAO.java`:
  1. Localizar dónde los mappers de asignaciones rellenan `setUrgente(...)`/leen `ES_CHASIS` (grep `setUrgente\|ES_CHASIS` en el fichero). En CADA query de asignaciones que exponga esos flags (`getAsignaciones`, `getAsignacionesPorImei`, `getAsignacionAnyById`, y la de por-técnico si es otra), añadir `r.POR_CERRAR` al SELECT y `resumen.setPorCerrar(rs.getBoolean("POR_CERRAR"));` al mapper — mismo tratamiento exacto que `URGENTE`.
  2. Junto a `actualizarUrgente` (~403), añadir:

```java
public void actualizarPorCerrar(String idRep, boolean porCerrar) {
    jdbc.update("UPDATE Reparacion SET POR_CERRAR = ? WHERE ID_REP = ?", porCerrar, idRep);
}
```

- [ ] **Paso 6: Endpoint** — en `controller/ReparacionController.java`, tras `actualizarChasis` (~237):

```java
/** Marca "por cerrar": técnico solo SUS asignaciones de reparación normal; supertécnico cualquiera. */
@PatchMapping("/asignaciones/{idRep}/por-cerrar")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void actualizarPorCerrar(@PathVariable String idRep, @RequestBody PorCerrarRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
    boolean esRepNormal = idRep != null && idRep.startsWith("A")
            && !idRep.startsWith("AG") && !idRep.startsWith("AP");
    if (!esRepNormal) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Solo aplica a asignaciones de reparación");
    }
    ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
    boolean esSuper = "SUPERTECNICO".equals(principal.getRol());
    boolean esSuya  = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
    if (!esSuper && !esSuya) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo puedes marcar tus propias asignaciones");
    }
    dao.actualizarPorCerrar(idRep, req.porCerrar());
    logDao.insertar(principal.getIdUsu(),
            req.porCerrar() ? "MARCAR_POR_CERRAR" : "QUITAR_POR_CERRAR",
            "ID_REP: " + idRep + ", IMEI: " + asig.getImei());
}
```

Y junto a los records existentes (`UrgenteRequest`, `ChasisRequest` — buscarlos al final del fichero): `private record PorCerrarRequest(boolean porCerrar) {}`. OJO: SIN `@PreAuthorize` — el permiso fino va en el cuerpo (el ADMIN cae en 403 porque no es super ni tiene idTec propio de la asignación).

- [ ] **Paso 7: Compilar** — `cd gestion-reparaciones-servidor && mvn -q test` → BUILD SUCCESS (la suite del servidor está vacía o con los tests de F2a NO presentes en esta rama — lo que haya debe quedar verde).

- [ ] **Paso 8: Commits (repo SERVIDOR)**

```bash
git add sql/ && git commit -m "feat(sql): columna POR_CERRAR en Reparacion — marca 'por cerrar' de asignaciones"
git add src/main/java && git commit -m "feat(servidor): PATCH por-cerrar con permisos por rol y flag en las queries de asignaciones"
```

---

### Tarea 2: Cliente — modelo, DAO y acciones de log

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` (junto a líneas 419-427)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java` (líneas 42-59)
- Test (solo si asserta la lista): `src/test/java/com/reparaciones/controllers/LogControllerTest.java`

**Interfaces:**
- Consumes: endpoint y campo JSON de T1.
- Produces (lo consumen T3/T4/T5): `ReparacionResumen.isPorCerrar()` / `setPorCerrar(boolean)`; `ReparacionDAO.actualizarPorCerrar(String idRep, boolean porCerrar) throws SQLException`.

- [ ] **Paso 1: Verificar rama** — `cd "c:/Users/info/Documents/ProgramaReparaciones" && git branch --show-current` → `feature/por-cerrar-carga`.

- [ ] **Paso 2: Modelo** — en `models/ReparacionResumen.java`, junto a `urgente`/`esChasis`: campo `private boolean porCerrar;` + `public boolean isPorCerrar() { return porCerrar; }` + `public void setPorCerrar(boolean v) { this.porCerrar = v; }` (Gson rellena por nombre de campo; el constructor grande NO se toca).

- [ ] **Paso 3: DAO** — en `dao/ReparacionDAO.java`, tras `actualizarChasis` (línea ~424):

```java
public void actualizarPorCerrar(String idRep, boolean porCerrar) throws SQLException {
    ApiClient.patch("/api/reparaciones/asignaciones/" + idRep + "/por-cerrar",
            Map.of("porCerrar", porCerrar));
}
```

- [ ] **Paso 4: Log** — en `LogController.TIPOS_ACCION`, tras `"MARCAR_REVISION", "QUITAR_REVISION",` añadir `"MARCAR_POR_CERRAR", "QUITAR_POR_CERRAR",`. Comprobar `LogControllerTest`: si asserta el contenido de la lista, actualizarlo en el mismo cambio.

- [ ] **Paso 5: Suite** — `cd gestion-reparaciones-cliente && mvn -q test` → verde.

- [ ] **Paso 6: Commit (repo RAÍZ)**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones" && git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): flag por-cerrar en modelo y DAO del cliente + acciones en el log"
```

---

### Tarea 3: Cliente — `CargaTecnicos` (TDD, cálculo puro)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CargaTecnicos.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/CargaTecnicosTest.java`

**Interfaces:**
- Consumes: `ReparacionResumen` (con `isPorCerrar()` de T2, `isEsChasis()`, `getCliente()`, `getIdTec()`, `getIdRep()`, `getFechaFin()`), `TipoTrabajo.desde(idRep)`.
- Produces (lo consume T5): `CargaTecnicos.Desglose(int normales, int chasis, int porCerrar, int glass, double carga)`; `CargaTecnicos.calcular(List<ReparacionResumen>)` → `Map<Integer,Desglose>` por idTec (solo técnicos con carga > 0); `CargaTecnicos.porcentajes(Map<Integer,Desglose>)` → `Map<Integer,Integer>` (enteros, `Math.round`); constante `CargaTecnicos.PESO_POR_CERRAR = 1.0/12`.

- [ ] **Paso 1: Test que falla**

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CargaTecnicosTest {

    /** Asignación abierta con los campos que usa el cálculo. */
    private ReparacionResumen asig(String idRep, int idTec, String cliente,
                                   boolean chasis, boolean porCerrar) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep(idRep);
        r.setIdTec(idTec);
        r.setCliente(cliente);
        r.setEsChasis(chasis);
        r.setPorCerrar(porCerrar);
        return r;
    }

    @Test void pesaNormalChasisPorCerrarYGlass() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, "WEB", false, false),   // 1
                asig("A20260708_2", 1, "WEB", true,  false),   // 2
                asig("A20260708_3", 1, "WEB", false, true),    // 0,083
                asig("AG20260708_1", 1, "WEB", false, false)));// 1 (glass)
        assertEquals(4.083, cargas.get(1).carga(), 0.001);
        assertEquals(1, cargas.get(1).normales());
        assertEquals(1, cargas.get(1).chasis());
        assertEquals(1, cargas.get(1).porCerrar());
        assertEquals(1, cargas.get(1).glass());
    }

    @Test void porCerrarMandaSobreChasis() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, "WEB", true, true)));
        assertEquals(CargaTecnicos.PESO_POR_CERRAR, cargas.get(1).carga(), 0.0001);
    }

    @Test void sinClienteNoCuentaYPulidoTampoco() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, null, false, false),
                asig("A20260708_2", 1, "",   false, false),
                asig("AP20260708_1", 1, "WEB", false, false)));
        assertTrue(cargas.isEmpty());
    }

    @Test void porcentajesRelativosQueSumanCien() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A1", 1, "WEB", false, false),
                asig("A2", 1, "WEB", false, false),
                asig("A3", 1, "WEB", false, false),
                asig("A4", 2, "WEB", false, false)));
        Map<Integer, Integer> pct = CargaTecnicos.porcentajes(cargas);
        assertEquals(75, pct.get(1));
        assertEquals(25, pct.get(2));
    }

    @Test void listaVaciaDaMapasVacios() {
        assertTrue(CargaTecnicos.calcular(List.of()).isEmpty());
        assertTrue(CargaTecnicos.porcentajes(Map.of()).isEmpty());
    }
}
```

NOTA: si `ReparacionResumen` (cliente) no tiene setters para `idRep`/`idTec`/`cliente`/`esChasis`, añadir los que falten en T3 (junto a los existentes, mismo estilo) — Gson no los necesita pero el test sí; comprobar primero si existen.

- [ ] **Paso 2: Ver que falla** — `cd gestion-reparaciones-cliente && mvn -q test -Dtest=CargaTecnicosTest` → no compila (`CargaTecnicos` no existe).

- [ ] **Paso 3: Implementación**

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga de trabajo por técnico (spec por-cerrar-carga §3). Cuentan solo las
 * asignaciones abiertas CON cliente, de reparación normal y glass (pulido no).
 * Pesos: normal 1 · chasis 2 · por cerrar 1/12 (manda sobre chasis) · glass 1.
 * El porcentaje es relativo al total (entre todos suman ~100). Cálculo puro:
 * la UI lo repinta en cada carga de datos.
 */
public final class CargaTecnicos {

    private CargaTecnicos() {}

    public static final double PESO_POR_CERRAR = 1.0 / 12;   // ≈ 0,083

    public record Desglose(int normales, int chasis, int porCerrar, int glass, double carga) {
        Desglose sumar(int n, int c, int p, int g, double peso) {
            return new Desglose(normales + n, chasis + c, porCerrar + p, glass + g, carga + peso);
        }
    }

    /** Carga por idTec; solo técnicos con alguna asignación que cuente. */
    public static Map<Integer, Desglose> calcular(List<ReparacionResumen> asignaciones) {
        Map<Integer, Desglose> out = new HashMap<>();
        for (ReparacionResumen r : asignaciones) {
            if (r.getFechaFin() != null) continue;                       // solo abiertas
            if (r.getCliente() == null || r.getCliente().isEmpty()) continue;   // solo con cliente
            TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
            Desglose base = out.getOrDefault(r.getIdTec(), new Desglose(0, 0, 0, 0, 0));
            Desglose nuevo = switch (tipo) {
                case GLASS      -> base.sumar(0, 0, 0, 1, 1);
                case REPARACION -> r.isPorCerrar() ? base.sumar(0, 0, 1, 0, PESO_POR_CERRAR)
                                 : r.isEsChasis()  ? base.sumar(0, 1, 0, 0, 2)
                                 :                   base.sumar(1, 0, 0, 0, 1);
                case PULIDO     -> null;
            };
            if (nuevo != null) out.put(r.getIdTec(), nuevo);
        }
        return out;
    }

    /** % relativo al total, redondeado a entero. Vacío si no hay carga. */
    public static Map<Integer, Integer> porcentajes(Map<Integer, Desglose> cargas) {
        double total = cargas.values().stream().mapToDouble(Desglose::carga).sum();
        Map<Integer, Integer> out = new HashMap<>();
        if (total <= 0) return out;
        cargas.forEach((idTec, d) -> out.put(idTec, (int) Math.round(d.carga() / total * 100)));
        return out;
    }
}
```

- [ ] **Paso 4: Verde** — `mvn -q test -Dtest=CargaTecnicosTest` → 5/5; después `mvn -q test` → suite completa verde.

- [ ] **Paso 5: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): calculo puro de carga por tecnico con pesos chasis/por-cerrar"
```

---

### Tarea 4: Cliente — menú contextual y badge en las dos vistas

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java`

**Interfaces:**
- Consumes: `ReparacionResumen.isPorCerrar()`, `ReparacionDAO.actualizarPorCerrar(idRep, porCerrar)` (T2).
- Produces: acción de marcar/desmarcar en ambas vistas + badge "Por cerrar".

- [ ] **Paso 1: Vista supertécnico — menú.** En el rowFactory de `PendientesSuperTecnicoController` (donde están `toggleUrgente`/`toggleChasis`):
  1. Declarar `MenuItem togglePorCerrar = new MenuItem();` junto a los otros.
  2. Su handler (clonar el de `toggleChasis`, que llama al DAO y recarga):

```java
togglePorCerrar.setOnAction(e -> {
    ReparacionResumen rep = getItem();
    if (rep == null) return;
    try {
        reparacionDAO.actualizarPorCerrar(rep.getIdRep(), !rep.isPorCerrar());
        cargar();
    } catch (SQLException ex) { mostrarError(ex); }
});
```

  3. En `menu.setOnShowing` (líneas ~405-421), junto a `toggleChasis`: `togglePorCerrar.setVisible(!soloLectura && esRep);` y `if (getItem() != null) togglePorCerrar.setText(getItem().isPorCerrar() ? "Quitar por cerrar" : "Marcar por cerrar");`.
  4. Añadirlo al menú junto a los toggles: `menu.getItems().add(togglePorCerrar);` (tras `toggleChasis`, línea ~421).

- [ ] **Paso 2: Vista supertécnico — badge.** En la cellFactory de `cEstado` (~461): añadir `private final Label badgePorCerrar = new Label("Por cerrar");` y meterlo en el VBox entre `badgeUrgente` y `badge`: `new VBox(2, badgeUrgente, badgePorCerrar, badge)`. En `updateItem`, tras el bloque de `badgeUrgente`:

```java
if (rep.isPorCerrar()) {
    badgePorCerrar.setStyle(base + "-fx-background-color: #E0F2F1; -fx-text-fill: #00796B;");
    badgePorCerrar.setVisible(true); badgePorCerrar.setManaged(true);
} else {
    badgePorCerrar.setVisible(false); badgePorCerrar.setManaged(false);
}
```

- [ ] **Paso 3: Vista técnico — menú.** En el rowFactory de `PendientesTecnicoController` (~116-155), donde hoy solo hay `copiar`:
  1. `MenuItem togglePorCerrar = new MenuItem("Marcar por cerrar");` con el MISMO handler del Paso 1 (usa el `reparacionDAO` del controller, `cargar()` y `mostrarError`).
  2. `menu.getItems().add(togglePorCerrar);` tras `copiar`.
  3. Añadir un `menu.setOnShowing` (no existe — crearlo antes de `setContextMenu(menu)`):

```java
menu.setOnShowing(ev -> {
    ReparacionResumen rep = getItem();
    boolean esRepNormal = rep != null && !glass
            && TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.REPARACION;
    togglePorCerrar.setVisible(esRepNormal);
    if (esRepNormal) togglePorCerrar.setText(rep.isPorCerrar() ? "Quitar por cerrar" : "Marcar por cerrar");
});
```

  (Todas las filas de esa vista son del técnico logueado: el servidor valida la propiedad de todos modos.)

- [ ] **Paso 4: Vista técnico — badge.** En la cellFactory de `cEstado` (~183): añadir `badgePorCerrar` al `celdaBox` entre `badgeUrgente` y `badge` (mismo snippet del Paso 2).

- [ ] **Paso 5: Suite + arranque visual imposible aquí** — `mvn -q test` → verde (los controladores no tienen test; la verificación visual queda para el smoke).

- [ ] **Paso 6: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): marcar/quitar 'por cerrar' con badge verde-glass en las vistas de supertecnico y tecnico"
```

---

### Tarea 5: Cliente — franja de % en Asignaciones + ⓘ ayuda + % en el modal de asignar

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/PendientesSuperTecnicoView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `CargaTecnicos.calcular/porcentajes/Desglose` (T3); lista `tecnicos` y `datos` del controller.
- Produces: franja visible para supertécnico y admin; % junto a los técnicos del modal de asignar.

- [ ] **Paso 1: FXML** — entre el `FlowPane` de filtros (línea 31) y el `TableView` (línea 33) insertar:

```xml
    <FlowPane fx:id="franjaCarga" hgap="8" vgap="4" alignment="CENTER_LEFT"
              visible="false" managed="false"/>
```

- [ ] **Paso 2: Cálculo y pintado.** En el controller: campo `@FXML private FlowPane franjaCarga;`, campo `private Map<Integer, CargaTecnicos.Desglose> cargasActuales = Map.of();` y método:

```java
/** Franja "Juan 42% · Marta 33% · …" (solo supertécnico/admin; el % es global, no del filtro). */
private void actualizarFranjaCarga() {
    cargasActuales = CargaTecnicos.calcular(datos);
    Map<Integer, Integer> pct = CargaTecnicos.porcentajes(cargasActuales);
    franjaCarga.getChildren().clear();
    boolean hay = !pct.isEmpty();
    franjaCarga.setVisible(hay); franjaCarga.setManaged(hay);
    if (!hay) return;
    tecnicos.stream()
            .filter(t -> pct.containsKey(t.getIdTec()))
            .sorted((a, b) -> pct.get(b.getIdTec()) - pct.get(a.getIdTec()))
            .forEach(t -> {
                CargaTecnicos.Desglose d = cargasActuales.get(t.getIdTec());
                Label chip = new Label(t.getNombre() + " " + pct.get(t.getIdTec()) + "%");
                chip.setStyle("-fx-background-color: #E8EAF0; -fx-text-fill: #2C3B54;" +
                        "-fx-font-size: 11px; -fx-font-weight: bold;" +
                        "-fx-background-radius: 12; -fx-padding: 3 10 3 10;");
                Tooltip tip = new Tooltip(t.getNombre() + " — " + d.normales() + " normales · "
                        + d.chasis() + " chasis · " + d.porCerrar() + " por cerrar · "
                        + d.glass() + " glass");
                tip.setShowDelay(javafx.util.Duration.ZERO);
                Tooltip.install(chip, tip);
                franjaCarga.getChildren().add(chip);
            });
    Label ayuda = new Label("ⓘ");
    ayuda.setStyle("-fx-text-fill: #586376; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
    ayuda.setOnMouseClicked(e -> ConfirmDialog.mostrarTexto("¿Cómo se mide la carga?",
            "Cuentan solo las asignaciones abiertas CON cliente, de reparación y glass (pulido no).\n\n" +
            "Cada asignación vale: normal = 1 · chasis = 2 · por cerrar = 0,083 · glass = 1.\n\n" +
            "El porcentaje de cada técnico es su parte del total: entre todos suman 100%."));
    franjaCarga.getChildren().add(ayuda);
}
```

Llamarlo al final de `cargar()` (tras rellenar `datos`). Comprobar la firma real de `ConfirmDialog.mostrarTexto` (se usa en `AgrupadoController` línea ~547 y ~799) y ajustar la llamada si difiere. La franja NO se toca en `aplicarFiltros()` (el % es global).

- [ ] **Paso 3: % en el modal de asignar.** El modal de asignación masiva pinta técnicos con `t.getNombre()` (selector de técnico por defecto ~línea 900 y combos por fila). Añadir helper en el controller:

```java
/** "Nombre (42%)" con la carga vigente; sin % si el técnico no tiene carga. */
private String etiquetaConCarga(Tecnico t) {
    Map<Integer, Integer> pct = CargaTecnicos.porcentajes(cargasActuales);
    Integer p = pct.get(t.getIdTec());
    return p == null ? t.getNombre() : t.getNombre() + " (" + p + "%)";
}
```

y usarlo en los puntos del MODAL donde se renderiza el nombre del técnico (StringConverter/ListCell del selector por defecto y de los combos por fila del lote de asignación y pulido — localizarlos con grep `getNombre()` dentro del bloque del modal, ~líneas 890-1750). NO tocar la columna `cTecnico` de la tabla principal (ahí el % no aporta y baila con cada recarga).

- [ ] **Paso 4: Suite** — `mvn -q test` → verde.

- [ ] **Paso 5: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): franja de carga por tecnico con ayuda y porcentajes en el modal de asignar"
```

---

### Tarea 6: Cierre — suites, ALTER del usuario, arranque del servidor y smoke

- [ ] **Paso 1: Suites finales** — cliente y servidor `mvn -q test` + servidor `mvn -q -DskipTests package` → todo verde/BUILD SUCCESS.
- [ ] **Paso 2: PARADA — usuario:** aplicar `sql/migracion-por-cerrar.sql` en MariaDB; arrancar el servidor y confirmar `Started App` (valida el contexto; orden de despliegue: ALTER → servidor → cliente).
- [ ] **Paso 3: Smoke corto (usuario, con guía):**
  1. Supertécnico → Asignaciones: marcar una asignación de reparación "por cerrar" → badge verde; quitar → desaparece. En glass/pulido el ítem no aparece.
  2. Técnico → Mis pendientes: marcar/desmarcar una SUYA; badge visible. (La validación de ajenas la cubre el servidor.)
  3. Franja de %: aparece para supertécnico y admin, suma ~100, tooltip con desglose, ⓘ abre la ayuda; los filtros no cambian los %; marcar "por cerrar"/asignar/completar recalcula.
  4. Modal de asignar: técnicos con "(N%)".
  5. Vista Log: aparecen MARCAR_POR_CERRAR/QUITAR_POR_CERRAR y filtran.
- [ ] **Paso 4: Review final de la rama** (requesting-code-review) y presentar al usuario para su OK de merge (finishing-a-development-branch). NO mergear sin OK.

## Self-review (hecho al escribir el plan)

- Spec §2 completa: columna+migración (T1), endpoint+permisos+log (T1), modelo/DAO cliente (T2), menú+badge en ambas vistas (T4), acciones log (T2). Spec §3 completa: pesos y fórmula exactos (T3, con test de cada regla), franja+tooltip+ⓘ solo fórmula (T5), modal (T5), en-vivo = recalcular en cargar() (T5), filtros no afectan (T5), solo ST/admin (la vista compartida lo garantiza; el técnico no recibe franja ni %).
- Tipos consistentes: `porCerrar` (JSON/campos), `actualizarPorCerrar(String, boolean)` en ambos DAOs, `Desglose`/`calcular`/`porcentajes` idénticos en T3 y T5.
- Sin placeholders: los dos puntos "localizar con grep" (mappers de asignaciones del DAO servidor, render de técnicos del modal) nombran símbolo exacto a buscar y qué hacer — decisión deliberada para no copiar 200 líneas de contexto que el implementador tiene delante.
