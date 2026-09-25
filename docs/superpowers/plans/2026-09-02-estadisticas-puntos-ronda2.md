# Estadísticas por puntos — ronda 2 (0.16.2) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tarjetas resumen en formato objetivo, selector Puntos/Puntos·día deshabilitado en Día, etiqueta de ventana honesta, y exclusión de técnicos de estadísticas (BD + PATCH + panel 👥), según `docs/superpowers/specs/2026-09-02-estadisticas-puntos-ronda2-design.md`.

**Architecture:** Servidor: columna aditiva `ES_ESTADISTICA` en `Tecnico`, flag en `GET /api/tecnicos`, dos PATCH calcados de activar/desactivar con `@PreAuthorize(ADMIN)` + log. Cliente: filtrado de exclusión en cliente (helper puro `sinExcluidos`), tarjetas con `% de <mes anterior> (total)` vía `calcularTarjetas` reescrito, modal 👥 junto a ⚙ Valores, toggle de métrica gris en Día. Lógica pura y testeada en `PuntosEstadistica`; `EstadisticasController` solo pinta.

**Tech Stack:** Java 17, JavaFX (cliente), Spring Boot + JdbcTemplate (servidor), JUnit 5 + Mockito, Maven.

## Global Constraints

- **Repos y ramas — CRÍTICO:** cliente = repo raíz `c:/Users/dev/Documents/ProgramaReparaciones`, rama **`feature/estadisticas-puntos`** (ya existente, checkout hecho). Servidor = submódulo `gestion-reparaciones-servidor`, rama **`feature/estadisticas-puntos-r2`** (se crea en la Task 1 desde `main` del servidor). **Las tasks de servidor NUNCA tocan el repo raíz** (ni branch, ni commit, ni checkout — incidente T1 de la ronda 1). Las tasks de cliente nunca tocan el submódulo.
- **NO mergear, NO pushear, NO taggear** — solo con OK explícito del usuario, fuera de este plan.
- Commits **sin** trailer `Co-Authored-By`.
- Comandos por **Bash**; Maven necesita el toolchain portable:
  `export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH"`
- Textos de UI en español, con los colores ya usados: gris `#7A8A9A`, verde `#2E7D32`, **nunca rojo** en las tarjetas.
- Suites actuales verdes de partida: cliente 174 tests, servidor 119. Cada task deja su suite verde.
- La migración SQL la aplica **el usuario** (con vista previa); el arranque del servidor se valida a mano al final (no hay test de contexto Spring).

---

### Task 1: Servidor — rama r2, migración y flag `ES_ESTADISTICA` en el GET

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-estadisticas-exclusion.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (tabla `Tecnico`, ~línea 52)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Tecnico.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java`
- Modify: `gestion-reparaciones-servidor/docs/schema.md` (tabla Tecnico)

**Interfaces:**
- Produces: modelo `Tecnico` servidor con `isEsEstadistica()` (serializa como `esEstadistica` en el JSON de `GET /api/tecnicos` y `/activos`). Constructor pasa a 4 args: `Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica)`.

- [ ] **Step 1: Crear la rama del servidor (SOLO en el submódulo)**

```bash
cd gestion-reparaciones-servidor && git checkout main && git checkout -b feature/estadisticas-puntos-r2 && git branch --show-current
```
Esperado: `feature/estadisticas-puntos-r2`. NO tocar el repo raíz.

- [ ] **Step 2: Escribir la migración** (patrón de `migracion-estadisticas-puntos.sql`)

Contenido completo de `sql/migracion-estadisticas-exclusion.sql`:

```sql
-- Migración exclusión de técnicos de estadísticas (spec 2026-09-02-estadisticas-puntos-ronda2-design).
USE gestion_reparaciones;

-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Tecnico'
--      AND COLUMN_NAME = 'ES_ESTADISTICA';  -- esperado: 0

ALTER TABLE Tecnico ADD COLUMN ES_ESTADISTICA BOOLEAN NOT NULL DEFAULT TRUE;

-- Verificación post: SELECT ID_TEC, NOMBRE, ES_ESTADISTICA FROM Tecnico;  -- todos a 1
```

- [ ] **Step 3: Sincronizar `crear_bd.sql`** — la tabla queda:

```sql
CREATE TABLE Tecnico (
    ID_TEC         INT          NOT NULL AUTO_INCREMENT,
    NOMBRE         VARCHAR(100) NOT NULL,
    ACTIVO         BOOLEAN      NOT NULL DEFAULT TRUE,
    ES_ESTADISTICA BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (ID_TEC)
);
```

- [ ] **Step 4: Modelo `Tecnico` del servidor** — añadir el campo y pasar el constructor a 4 args (el único caller es el MAPPER del DAO, se actualiza en el Step 5):

```java
public class Tecnico {
    private int idTec;
    private String nombre;
    private boolean activo;
    private boolean esEstadistica;

    public Tecnico() {}

    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
    }

    public int     getIdTec()         { return idTec; }
    public String  getNombre()        { return nombre; }
    public boolean isActivo()         { return activo; }
    public boolean isEsEstadistica()  { return esEstadistica; }
}
```

- [ ] **Step 5: `TecnicoDAO` servidor** — MAPPER y las dos queries seleccionan la columna nueva:

```java
    private static final RowMapper<Tecnico> MAPPER = (rs, row) -> new Tecnico(
            rs.getInt("ID_TEC"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO"),
            rs.getBoolean("ES_ESTADISTICA")
    );
```

En `getAll()` y `getAllActivos()`, el SELECT pasa de `SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO` a `SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO, t.ES_ESTADISTICA` (resto de cada query intacto).

- [ ] **Step 6: `docs/schema.md`** — en la tabla `Tecnico`, añadir la fila de la columna nueva imitando el formato de `ACTIVO`: `ES_ESTADISTICA BOOLEAN NOT NULL DEFAULT TRUE — si cuenta en la vista de estadísticas (exclusión ronda 2, spec 2026-09-02)`.

- [ ] **Step 7: Suite del servidor verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-servidor && mvn -q test
```
Esperado: BUILD SUCCESS, 119 tests.

- [ ] **Step 8: Commit (en el submódulo)**

```bash
cd gestion-reparaciones-servidor && git add sql/migracion-estadisticas-exclusion.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/model/Tecnico.java src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java docs/schema.md && git commit -m "feat: columna ES_ESTADISTICA en Tecnico (migracion aditiva) y flag en GET /api/tecnicos"
```

---

### Task 2: Servidor — PATCH excluir/incluir-estadisticas con log y tests

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java` (tras `desactivarTecnico`, ~línea 78)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java` (tras `desactivarTecnico`, ~línea 82)
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/UsuarioControllerExclusionTest.java`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md` y `docs/autorizacion_endpoints.md`

**Interfaces:**
- Consumes: columna `ES_ESTADISTICA` (Task 1).
- Produces: `PATCH /api/usuarios/tecnicos/{idTec}/excluir-estadisticas` y `.../incluir-estadisticas` (204, solo ADMIN), acciones de log `EXCLUIR_ESTADISTICAS` / `INCLUIR_ESTADISTICAS` con detalle `ID_TEC: x, NOMBRE: y`. Métodos DAO `excluirEstadisticas(int)` / `incluirEstadisticas(int)`.

- [ ] **Step 1: Test que falla** — crear `UsuarioControllerExclusionTest.java` (patrón exacto de `DificultadControllerTest`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class UsuarioControllerExclusionTest {

    private final UsuarioDAO dao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioController ctl = new UsuarioController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void excluirActualizaYLoguea() {
        when(dao.getNombreByIdTec(7)).thenReturn("Laura");
        ctl.excluirEstadisticas(7, admin);
        verify(dao).excluirEstadisticas(7);
        verify(logDao).insertar(1, "EXCLUIR_ESTADISTICAS", "ID_TEC: 7, NOMBRE: Laura");
    }

    @Test void incluirActualizaYLoguea() {
        when(dao.getNombreByIdTec(7)).thenReturn("Laura");
        ctl.incluirEstadisticas(7, admin);
        verify(dao).incluirEstadisticas(7);
        verify(logDao).insertar(1, "INCLUIR_ESTADISTICAS", "ID_TEC: 7, NOMBRE: Laura");
    }
}
```

Nota: si el constructor de `UsuarioPrincipal` no casa con `(int, String, String, String, Integer)`, copiar la instanciación exacta de `DificultadControllerTest` (línea 21).

- [ ] **Step 2: Verificar que falla (no compila: métodos inexistentes)**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-servidor && mvn -q test -Dtest=UsuarioControllerExclusionTest
```
Esperado: FAIL (compilación: `excluirEstadisticas` no existe).

- [ ] **Step 3: DAO** — en `UsuarioDAO`, tras `desactivarTecnico`:

```java
    // Solo actualiza Tecnico.ES_ESTADISTICA — espejo de activar/desactivar
    public void excluirEstadisticas(int idTec) {
        jdbc.update("UPDATE Tecnico SET ES_ESTADISTICA = 0 WHERE ID_TEC = ?", idTec);
    }

    public void incluirEstadisticas(int idTec) {
        jdbc.update("UPDATE Tecnico SET ES_ESTADISTICA = 1 WHERE ID_TEC = ?", idTec);
    }
```

- [ ] **Step 4: Controller** — en `UsuarioController`, tras `desactivarTecnico`:

```java
    @PatchMapping("/tecnicos/{idTec}/excluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.excluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "EXCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/incluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void incluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.incluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "INCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }
```

- [ ] **Step 5: Test en verde + suite entera**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-servidor && mvn -q test
```
Esperado: BUILD SUCCESS, 121 tests (119 + 2).

- [ ] **Step 6: Docs de contrato** — en `docs/api_contract.md`, localizar el bloque de `PATCH /api/usuarios/tecnicos/{idTec}/desactivar` y añadir debajo, con el mismo formato, las dos entradas nuevas (204, sin body, solo ADMIN, log `EXCLUIR_ESTADISTICAS`/`INCLUIR_ESTADISTICAS`); en la respuesta de `GET /api/tecnicos` añadir el campo `esEstadistica` (boolean). En `docs/autorizacion_endpoints.md`, añadir las dos rutas nuevas con rol ADMIN junto a activar/desactivar.

- [ ] **Step 7: Commit (en el submódulo)**

```bash
cd gestion-reparaciones-servidor && git add -A && git commit -m "feat: PATCH excluir/incluir-estadisticas (ADMIN) con log, espejo de activar/desactivar"
```

---

### Task 3: Cliente — modelo `Tecnico` y `UsuarioDAO` con los PATCH

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/Tecnico.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/UsuarioDAO.java` (tras `activarTecnico`, ~línea 91)

**Interfaces:**
- Produces: `Tecnico.isEsEstadistica()` (Gson rellena `esEstadistica` del JSON; el constructor de 3 args mantiene compatibilidad con `true` por defecto). `UsuarioDAO.excluirEstadisticas(int)` / `incluirEstadisticas(int)`.

- [ ] **Step 1: Modelo** — en `models/Tecnico.java` añadir campo, constructor de 4 args (el de 3 delega) y getter:

```java
    /** {@code true} si cuenta en la vista de estadísticas (exclusión ronda 2, spec 2026-09-02). */
    private boolean esEstadistica;
```

```java
    public Tecnico(int idTec, String nombre, boolean activo) {
        this(idTec, nombre, activo, true);
    }

    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
    }
```

```java
    /** @return {@code true} si cuenta en la vista de estadísticas */
    public boolean isEsEstadistica() { return esEstadistica; }
```

- [ ] **Step 2: DAO** — en `dao/UsuarioDAO.java`, tras `activarTecnico` (mismo estilo javadoc):

```java
    /**
     * Excluye al técnico de la vista de estadísticas (Promedio, Equipo, tarjetas).
     *
     * @param idTec ID del técnico a excluir
     * @throws SQLException si falla la llamada al servidor
     */
    public void excluirEstadisticas(int idTec) throws SQLException {
        ApiClient.patch("/api/usuarios/tecnicos/" + idTec + "/excluir-estadisticas", null);
    }

    /**
     * Vuelve a incluir al técnico en la vista de estadísticas.
     *
     * @param idTec ID del técnico a incluir
     * @throws SQLException si falla la llamada al servidor
     */
    public void incluirEstadisticas(int idTec) throws SQLException {
        ApiClient.patch("/api/usuarios/tecnicos/" + idTec + "/incluir-estadisticas", null);
    }
```

- [ ] **Step 3: Suite del cliente verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test
```
Esperado: BUILD SUCCESS, 174 tests.

- [ ] **Step 4: Commit (en el repo raíz)**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/Tecnico.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/UsuarioDAO.java && git commit -m "feat(cliente): esEstadistica en Tecnico y DAOs de excluir/incluir estadisticas"
```

---

### Task 4: Cliente — helper `sinExcluidos` y filtrado de la vista (Equipo, Promedio, desplegable)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java` (`cargarTecnicos` ~284, `renderVentana` ~510-541, `promedioVentanaActual` ~586)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java`

**Interfaces:**
- Consumes: `Tecnico.isEsEstadistica()` (Task 3).
- Produces: `PuntosEstadistica.sinExcluidos(List<PuntoEstadisticaPuntos>, Set<String>) → List<PuntoEstadisticaPuntos>`; campo `nombresExcluidos` (`Set<String>`) en `EstadisticasController`; `cargarTecnicos()` re-entrante (Task 6 lo re-llama tras guardar el modal).

- [ ] **Step 1: Tests que fallan** — añadir a `PuntosEstadisticaTest` (usa el helper `fila(...)` existente al final de la clase; añadir `import java.util.Set;` si falta):

```java
    @Test void sinExcluidosFiltraSoloLosExcluidos() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Laura", "2026-09", 10));
        List<PuntoEstadisticaPuntos> resultado = PuntosEstadistica.sinExcluidos(filas, Set.of("Laura"));
        assertEquals(1, resultado.size());
        assertEquals("Marcos", resultado.get(0).getNombreTecnico());
    }

    @Test void sinExcluidosConSetVacioDevuelveLaMismaLista() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Marcos", "2026-09", 50));
        assertSame(filas, PuntosEstadistica.sinExcluidos(filas, Set.of()));
    }
```

- [ ] **Step 2: Verificar que fallan (no compila: `sinExcluidos` no existe)**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest
```
Esperado: FAIL de compilación.

- [ ] **Step 3: Helper en `PuntosEstadistica`** (junto a `promedioVentana`; añadir `import java.util.Set;` y `import java.util.stream.Collectors;` si faltan):

```java
    /** Filas sin los técnicos excluidos de estadísticas (ES_ESTADISTICA = 0). Set vacío → misma lista. */
    public static List<PuntoEstadisticaPuntos> sinExcluidos(List<PuntoEstadisticaPuntos> filas,
                                                            Set<String> nombresExcluidos) {
        if (nombresExcluidos.isEmpty()) return filas;
        return filas.stream()
                .filter(f -> !nombresExcluidos.contains(f.getNombreTecnico()))
                .collect(Collectors.toList());
    }
```

- [ ] **Step 4: Tests en verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest
```
Esperado: PASS.

- [ ] **Step 5: Wiring del controller.** En `EstadisticasController`:

(a) Campo nuevo junto a `nombresSeleccionadosTec` (línea ~104):

```java
    /** Técnicos con ES_ESTADISTICA=0: fuera de Promedio, Equipo, tarjetas y desplegable. */
    private final Set<String> nombresExcluidos = new LinkedHashSet<>();
```

(b) `cargarTecnicos()` pasa a ser re-entrante y a poblar exclusiones. Tras el `try/catch` del fetch, sustituir el cuerpo desde `Integer idTecSesion...` hasta el bucle de `inactivos` inclusive por:

```java
        // Re-entrante: el modal 👥 lo re-llama tras guardar exclusiones
        todosLosTecnicos.clear();
        nombresExcluidos.clear();
        tecnicos.stream().filter(t -> !t.isEsEstadistica())
                .map(Tecnico::getNombre).forEach(nombresExcluidos::add);

        Integer idTecSesion = com.reparaciones.Sesion.getIdTec();
        if (idTecSesion != null)
            nombreTecnicoSesion = tecnicos.stream()
                    .filter(t -> t.getIdTec() == idTecSesion)
                    .map(Tecnico::getNombre).findFirst().orElse(null);

        // Colores para TODOS (un excluido sigue viendo su propia serie con su color)
        for (Tecnico t : tecnicos)
            coloresPorNombre.put(t.getNombre(), generarColor(t.getIdTec()));

        // El desplegable solo lista a los que cuentan en estadísticas
        List<Tecnico> activos   = tecnicos.stream()
                .filter(Tecnico::isActivo).filter(Tecnico::isEsEstadistica)
                .collect(Collectors.toList());
        List<Tecnico> inactivos = tecnicos.stream()
                .filter(t -> !t.isActivo()).filter(Tecnico::isEsEstadistica)
                .collect(Collectors.toList());

        todosLosTecnicos.addAll(activos);
        if (!inactivos.isEmpty()) {
            todosLosTecnicos.add(null); // separador
            todosLosTecnicos.addAll(inactivos);
        }
```

El resto del método (branch no-admin, `MultiSelectDropdown.setup`, `actualizarTextoMenuTecnicos`) queda igual. Nota: `MultiSelectDropdown.setup` ya es re-entrante (re-sincroniza la lista "maestros" y no re-instala popup/cellFactory, guard `combo.getUserData() == null`), así que re-llamar `cargarTecnicos()` refresca el desplegable sin duplicar wiring.

(c) En `renderVentana`, justo después de `Set<String> seleccionados = ...` (línea ~485), añadir:

```java
        List<PuntoEstadisticaPuntos> puntosEquipo =
                PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos);
```

y en los DOS bucles de la serie Equipo (suma, línea ~515, y maxEquipo, línea ~535) cambiar `for (PuntoEstadisticaPuntos p : todosPuntos)` por `for (PuntoEstadisticaPuntos p : puntosEquipo)`. El resto (series por técnico, maxVisible de seleccionados) sigue sobre `todosPuntos` — así el excluido conserva su propia serie en su vista.

(d) En `promedioVentanaActual` (línea ~586), cambiar el bucle a la lista filtrada:

```java
        for (PuntoEstadisticaPuntos p : PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)) {
```

- [ ] **Step 6: Suite entera verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test
```
Esperado: BUILD SUCCESS, 176 tests (174 + 2).

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java && git commit -m "feat(cliente): exclusion de tecnicos en la vista de estadisticas (Equipo, Promedio y desplegable)"
```

---

### Task 5: Cliente — tarjetas en formato objetivo

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java` (record `Tarjetas` + `calcularTarjetas`, líneas ~134-169)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java` (`cargarTarjetas` ~1288, `pintarDelta` ~1306)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java` (sustituye los 2 tests de tarjetas, líneas ~115-140)

**Interfaces:**
- Consumes: `sinExcluidos` y `nombresExcluidos` (Task 4).
- Produces: `record Tarjetas(String mesLabel, String mesAnteriorLabel, double puntos, double puntosDia, Integer pctPuntos, Double puntosAnterior, Integer pctDia, Double diaAnterior)`; `calcularTarjetas(List, YearMonth, LocalDate, String tecnicoONull, Set<String> excluidos)`; `textoObjetivo(int pct, String mesAnterior, double valorAnterior) → "46% de agosto (890,0)"`.

- [ ] **Step 1: Sustituir los tests de tarjetas.** Borrar `tarjetasDelEquipoConDeltas` (o nombre equivalente, líneas ~115-130) y `tarjetasDeUnTecnicoYSinMesAnterior` (~132-140) y escribir en su lugar:

```java
    @Test void tarjetasDelEquipoConObjetivo() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Zara", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Zara", "2026-09", 60));
        // hoy = 15/09/2026 → 11 laborables transcurridos; agosto 2026: 21 laborables
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of());
        assertEquals("septiembre", t.mesLabel());
        assertEquals("agosto", t.mesAnteriorLabel());
        assertEquals(110.0, t.puntos(), 0.001);
        assertEquals(10.0, t.puntosDia(), 0.001);           // 110 / 11
        assertEquals(55, t.pctPuntos());                     // 110/200
        assertEquals(200.0, t.puntosAnterior(), 0.001);
        assertEquals(105, t.pctDia());                       // 10 / (200/21 = 9,52)
        assertEquals(200.0 / 21, t.diaAnterior(), 0.001);
    }

    @Test void tarjetasDeUnTecnicoYSinMesAnterior() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Zara", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Marcos", Set.of());
        assertEquals(50.0, t.puntos(), 0.001);
        assertNull(t.pctPuntos());
        assertNull(t.puntosAnterior());
        assertNull(t.pctDia());
        assertNull(t.diaAnterior());
    }

    @Test void tarjetasEquipoIgnoranALosExcluidos() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Laura", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Laura", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of("Laura"));
        assertEquals(50.0, t.puntos(), 0.001);
        assertEquals(100.0, t.puntosAnterior(), 0.001);
        assertEquals(50, t.pctPuntos());
    }

    @Test void tarjetaPersonalDelExcluidoNoSeFiltra() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Laura", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Laura", Set.of("Laura"));
        assertEquals(60.0, t.puntos(), 0.001);
    }

    @Test void tarjetasConMesActualACeroDanCeroPorCiento() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Marcos", "2026-08", 100));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of());
        assertEquals(0, t.pctPuntos());
        assertEquals(100.0, t.puntosAnterior(), 0.001);
    }

    @Test void textoObjetivoFormatea() {
        assertEquals("46% de agosto (890,0)", PuntosEstadistica.textoObjetivo(46, "agosto", 890.0));
    }
```

- [ ] **Step 2: Verificar que fallan (no compilan: record y firma nuevos)**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest
```
Esperado: FAIL de compilación.

- [ ] **Step 3: Reescribir el bloque de tarjetas en `PuntosEstadistica`** (sustituye record + `calcularTarjetas` actuales, líneas ~136-169):

```java
    public record Tarjetas(String mesLabel, String mesAnteriorLabel, double puntos, double puntosDia,
                           Integer pctPuntos, Double puntosAnterior, Integer pctDia, Double diaAnterior) {}

    /**
     * Tarjetas en formato objetivo: % del mes anterior alcanzado (nunca delta rojo).
     *
     * @param filasMensuales resultado del endpoint con granularidad mes cubriendo mes anterior y actual
     * @param tecnicoONull   null = equipo (sin excluidos); nombre = solo ese técnico (los excluidos
     *                       se ignoran: la tarjeta personal del excluido sigue funcionando)
     * @param excluidos      técnicos con ES_ESTADISTICA=0 (solo aplica a las tarjetas de equipo)
     */
    public static Tarjetas calcularTarjetas(List<PuntoEstadisticaPuntos> filasMensuales,
                                            YearMonth mesActual, LocalDate hoy,
                                            String tecnicoONull, Set<String> excluidos) {
        List<PuntoEstadisticaPuntos> filas = tecnicoONull == null
                ? sinExcluidos(filasMensuales, excluidos) : filasMensuales;
        YearMonth anterior = mesActual.minusMonths(1);
        String pActual = mesActual.toString();     // "2026-09"
        String pAnterior = anterior.toString();

        double puntosActual = 0, puntosAnterior = 0;
        boolean hayAnterior = false;
        for (PuntoEstadisticaPuntos f : filas) {
            if (tecnicoONull != null && !tecnicoONull.equals(f.getNombreTecnico())) continue;
            if (pActual.equals(f.getPeriodo()))   puntosActual   += f.getPuntos();
            if (pAnterior.equals(f.getPeriodo())) { puntosAnterior += f.getPuntos(); hayAnterior = true; }
        }

        double diaActual = puntosActual
                / Math.max(1, diasLaborables(mesActual.atDay(1),
                        mesActual.atEndOfMonth().isAfter(hoy) ? hoy : mesActual.atEndOfMonth()));
        Integer pctPuntos = null, pctDia = null;
        Double totalAnterior = null, tasaAnterior = null;
        if (hayAnterior && puntosAnterior > 0) {
            double diaAnterior = puntosAnterior
                    / Math.max(1, diasLaborables(anterior.atDay(1), anterior.atEndOfMonth()));
            pctPuntos = (int) Math.round(puntosActual / puntosAnterior * 100);
            pctDia    = (int) Math.round(diaActual / diaAnterior * 100);
            totalAnterior = puntosAnterior;
            tasaAnterior  = diaAnterior;
        }
        Locale es = new Locale("es", "ES");
        return new Tarjetas(
                mesActual.getMonth().getDisplayName(TextStyle.FULL, es),
                anterior.getMonth().getDisplayName(TextStyle.FULL, es),
                puntosActual, diaActual, pctPuntos, totalAnterior, pctDia, tasaAnterior);
    }

    /** "46% de agosto (890,0)" — la línea de objetivo de las tarjetas. */
    public static String textoObjetivo(int pct, String mesAnterior, double valorAnterior) {
        return pct + "% de " + mesAnterior + " (" + formatearPuntos(valorAnterior) + ")";
    }
```

- [ ] **Step 4: Adaptar el controller.** Sustituir `cargarTarjetas` y `pintarDelta` (líneas ~1288-1311) por:

```java
    /** Tarjetas del mes en curso (equipo, o el propio técnico si el rol es TECNICO). */
    private void cargarTarjetas() {
        java.time.YearMonth mes = java.time.YearMonth.now();
        List<PuntoEstadisticaPuntos> filas;
        try {
            filas = new ReparacionDAO().getEstadisticasPuntos("mes",
                    mes.minusMonths(1).atDay(1), mes.atEndOfMonth());
        } catch (SQLException e) { mostrarError(e); return; }
        String tecnico = com.reparaciones.Sesion.esAdminOSuperTecnico() ? null : nombreTecnicoSesion;
        var t = PuntosEstadistica.calcularTarjetas(
                filas, mes, java.time.LocalDate.now(), tecnico, nombresExcluidos);
        String quien = tecnico == null ? "equipo" : "tú";
        lblCardPuntosTitulo.setText("Puntos · " + t.mesLabel() + " · " + quien);
        lblCardPuntosValor.setText(PuntosEstadistica.formatearPuntos(t.puntos()));
        pintarObjetivo(lblCardPuntosDelta, t.pctPuntos(), t.mesAnteriorLabel(), t.puntosAnterior());
        lblCardDiaTitulo.setText("Puntos/día · " + t.mesLabel() + " · " + quien);
        lblCardDiaValor.setText(PuntosEstadistica.formatearPuntos(t.puntosDia()));
        pintarObjetivo(lblCardDiaDelta, t.pctDia(), t.mesAnteriorLabel(), t.diaAnterior());
    }

    /** Línea de objetivo: "46% de agosto (890,0)" — gris hasta el 100%, verde al alcanzarlo. Nunca rojo. */
    private void pintarObjetivo(Label lbl, Integer pct, String mesAnterior, Double valorAnterior) {
        if (pct == null) { lbl.setText(""); lbl.setStyle(""); return; }
        lbl.setText(PuntosEstadistica.textoObjetivo(pct, mesAnterior, valorAnterior));
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (pct >= 100 ? "#2E7D32" : "#7A8A9A") + ";");
    }
```

(`pintarDelta` desaparece; de paso resuelve el minor diferido "pintarDelta con pct null no resetea el color".)

- [ ] **Step 5: Suite entera verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test
```
Esperado: BUILD SUCCESS, 180 tests (176 − 2 + 6).

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java && git commit -m "feat(cliente): tarjetas resumen en formato objetivo (% del mes anterior, sin rojos)"
```

---

### Task 6: Cliente — modal 👥 Técnicos + botón + filtro del Log

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml` (header, tras `btnValores` ~línea 38)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java` (campo FXML ~96, `initialize` ~171, método nuevo junto a `abrirModalValores` ~227)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java` (lista de acciones, línea ~56)

**Interfaces:**
- Consumes: `UsuarioDAO.excluirEstadisticas/incluirEstadisticas` (Task 3), `cargarTecnicos()` re-entrante y `nombresExcluidos` (Task 4), `cargarTarjetas()` (Task 5).

- [ ] **Step 1: FXML** — tras el botón `btnValores` añadir:

```xml
                    <Button fx:id="btnTecnicosEstadistica" text="👥 Técnicos" styleClass="btn-secondary"
                            onAction="#abrirModalTecnicos"/>
```

- [ ] **Step 2: Campo y visibilidad.** Campo junto a `btnValores` (buscar `@FXML private Button btnValores;`):

```java
    @FXML private Button btnTecnicosEstadistica;
```

En `initialize()`, junto a las dos líneas de `btnValores`:

```java
        btnTecnicosEstadistica.setVisible(com.reparaciones.Sesion.esAdmin());
        btnTecnicosEstadistica.setManaged(com.reparaciones.Sesion.esAdmin());
```

- [ ] **Step 3: Modal.** Método nuevo tras `abrirModalValores` (mismo patrón Dialog + event filter en OK):

```java
    /** Modal 👥: quién cuenta en la vista de estadísticas (spec ronda 2 §4). Solo ADMIN. */
    @FXML
    private void abrirModalTecnicos() {
        List<Tecnico> tecnicos;
        try {
            tecnicos = new TecnicoDAO().getAll();
        } catch (SQLException e) { mostrarError(e); return; }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Técnicos en estadísticas");
        dialog.setHeaderText("Quién cuenta en la vista de estadísticas");
        javafx.scene.layout.VBox caja = new javafx.scene.layout.VBox(6);
        java.util.Map<Integer, CheckBox> checks = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Boolean> estadoInicial = new java.util.HashMap<>();
        List<Tecnico> orden = new java.util.ArrayList<>();
        tecnicos.stream().filter(Tecnico::isActivo).forEach(orden::add);
        tecnicos.stream().filter(t -> !t.isActivo()).forEach(orden::add);
        for (Tecnico t : orden) {
            CheckBox cb = new CheckBox(t.isActivo() ? t.getNombre() : t.getNombre() + " (inactivo)");
            cb.setSelected(t.isEsEstadistica());
            checks.put(t.getIdTec(), cb);
            estadoInicial.put(t.getIdTec(), t.isEsEstadistica());
            caja.getChildren().add(cb);
        }
        Label aviso = new Label("Los desmarcados no cuentan en Promedio, Equipo ni tarjetas.");
        aviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A8A9A;");
        caja.getChildren().add(aviso);
        dialog.getDialogPane().setContent(caja);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.control.Button ok =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                var usuarioDao = new com.reparaciones.dao.UsuarioDAO();
                for (var e : checks.entrySet()) {
                    boolean marcado = e.getValue().isSelected();
                    if (marcado == estadoInicial.get(e.getKey())) continue; // solo cambios
                    if (marcado) usuarioDao.incluirEstadisticas(e.getKey());
                    else         usuarioDao.excluirEstadisticas(e.getKey());
                }
            } catch (SQLException ex) { ev.consume(); mostrarError(ex); return; }
            cargarTecnicos(); // re-entrante: reconstruye desplegable y nombresExcluidos
            nombresSeleccionadosTec.removeAll(nombresExcluidos);
            if (filtroTecHandle != null) filtroTecHandle.refresh();
            actualizarTextoMenuTecnicos();
            renderVentana(ventanaOffset);
            cargarTarjetas();
        });
        dialog.showAndWait();
    }
```

- [ ] **Step 4: Filtro del Log.** En `LogController`, línea ~56, la entrada de usuarios queda:

```java
        "ACTIVAR_USUARIO", "DESACTIVAR_USUARIO", "ELIMINAR_USUARIO",
        "EXCLUIR_ESTADISTICAS", "INCLUIR_ESTADISTICAS",
```

- [ ] **Step 5: Suite entera verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test
```
Esperado: BUILD SUCCESS, 180 tests.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java && git commit -m "feat(cliente): modal Tecnicos en estadisticas (solo admin) y acciones nuevas en el filtro del Log"
```

---

### Task 7: Cliente — selector deshabilitado en Día + etiqueta "días con actividad"

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml` (radios, líneas ~33-36)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java` (`recargarDatos` ~398, `renderVentana` etiqueta ~482)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java`

**Interfaces:**
- Produces: `PuntosEstadistica.etiquetaVentana(int n, String granularidad) → "30 días con actividad" / "16 semanas" / "12 meses" / "5 años"`.

- [ ] **Step 1: Test que falla**

```java
    @Test void etiquetaVentanaPorGranularidad() {
        assertEquals("30 días con actividad", PuntosEstadistica.etiquetaVentana(30, "Día"));
        assertEquals("1 día con actividad",   PuntosEstadistica.etiquetaVentana(1, "Día"));
        assertEquals("16 semanas", PuntosEstadistica.etiquetaVentana(16, "Semana"));
        assertEquals("1 semana",   PuntosEstadistica.etiquetaVentana(1, "Semana"));
        assertEquals("12 meses",   PuntosEstadistica.etiquetaVentana(12, "Mes"));
        assertEquals("5 años",     PuntosEstadistica.etiquetaVentana(5, "Año"));
    }
```

- [ ] **Step 2: Verificar que falla (no compila)**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest
```
Esperado: FAIL de compilación.

- [ ] **Step 3: Helper** (junto a `etiquetaVentana` no hay vecino natural; ponerlo tras `textoTooltip`):

```java
    /** Etiqueta de la barra de navegación: la unidad según la granularidad de la UI.
     *  En Día el eje solo tiene días en los que alguien trabajó, de ahí "con actividad". */
    public static String etiquetaVentana(int n, String granularidad) {
        String unidad = switch (granularidad) {
            case "Día"    -> n == 1 ? "día con actividad" : "días con actividad";
            case "Semana" -> n == 1 ? "semana" : "semanas";
            case "Mes"    -> n == 1 ? "mes" : "meses";
            default       -> n == 1 ? "año" : "años"; // "Año"
        };
        return n + " " + unidad;
    }
```

- [ ] **Step 4: Test en verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest
```
Esperado: PASS.

- [ ] **Step 5: Usar la etiqueta.** En `renderVentana` (línea ~482) sustituir:

```java
        lblRangoVentana.setText(tamanio + " periodos · "
                + todosPeriodos.get(inicio) + " — " + todosPeriodos.get(fin - 1));
```

por:

```java
        lblRangoVentana.setText(PuntosEstadistica.etiquetaVentana(tamanio, cmbGranularidad.getValue())
                + " · " + todosPeriodos.get(inicio) + " — " + todosPeriodos.get(fin - 1));
```

- [ ] **Step 6: FXML — envolver los radios** (el tooltip no salta en controles disabled; se instala en el HBox padre, que sigue enabled). Sustituir las líneas de los dos RadioButton por:

```xml
                    <HBox fx:id="boxMetrica" spacing="12" alignment="CENTER_LEFT">
                        <RadioButton fx:id="rbPuntos" text="Puntos" selected="true"
                                     style="-fx-font-weight: bold;"/>
                        <RadioButton fx:id="rbPuntosDia" text="Puntos/día"
                                     style="-fx-font-weight: bold;"/>
                    </HBox>
```

- [ ] **Step 7: Deshabilitar en Día.** En el controller: campo nuevo junto a `tooltipMedia` (buscar `tooltipMedia`):

```java
    @FXML private HBox boxMetrica;
    private final Tooltip tooltipMetrica = new Tooltip("En granularidad Día ambas métricas coinciden");
```

Al principio de `recargarDatos()` (se ejecuta al arrancar y con cada cambio de granularidad/fechas):

```java
        // En Día ambas métricas coinciden: toggle gris, selección conservada (spec ronda 2 §3)
        boolean esDia = "Día".equals(cmbGranularidad.getValue());
        rbPuntos.setDisable(esDia);
        rbPuntosDia.setDisable(esDia);
        if (esDia) Tooltip.install(boxMetrica, tooltipMetrica);
        else       Tooltip.uninstall(boxMetrica, tooltipMetrica);
```

- [ ] **Step 8: Suite entera verde**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test
```
Esperado: BUILD SUCCESS, 181 tests (180 + 1).

- [ ] **Step 9: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java && git commit -m "feat(cliente): toggle de metrica deshabilitado en Dia con tooltip y etiqueta de ventana por granularidad"
```

---

### Task 8: Cierre — CHANGELOG, suites finales y estado de la spec

**Files:**
- Modify: `CHANGELOG.md` (sección `[Unreleased]`)
- Modify: `docs/superpowers/specs/2026-09-02-estadisticas-puntos-ronda2-design.md` (línea de Estado)

**Interfaces:**
- Consumes: todo lo anterior.

- [ ] **Step 1: CHANGELOG.** En `[Unreleased]`, añadir al final de `### Added`:

```markdown
- **👥 Técnicos en estadísticas** (solo admin, junto a ⚙ Valores): panel para excluir de la vista de Técnicos a quienes no reparan a jornada completa; el excluido sale de Promedio, Equipo, tarjetas y desplegable (pero sigue viéndose a sí mismo). Acciones `EXCLUIR_ESTADISTICAS` / `INCLUIR_ESTADISTICAS` en el log.
```

y crear (antes de `### Fixed`) una sección `### Changed` con:

```markdown
### Changed
- **Tarjetas resumen en formato objetivo**: en vez del delta ▲/▼ (que salía rojo enorme a principios de mes), muestran "% de <mes anterior> (total)" — gris hasta el 100%, verde al superarlo, nunca rojo.
- El selector **Puntos / Puntos·día** se deshabilita en granularidad Día (ambas métricas coinciden); la selección se conserva al cambiar de granularidad.
- La etiqueta de la ventana dice la unidad real: "30 días con actividad", "16 semanas", "12 meses", "5 años" (antes "N periodos").
```

- [ ] **Step 2: Estado de la spec.** En la línea 4 de la spec ronda 2, cambiar `**spec aprobada en brainstorm — pendiente plan de implementación**` por `**IMPLEMENTADA en feature/estadisticas-puntos (cliente) y feature/estadisticas-puntos-r2 (servidor) — pendiente migración preprod, smoke y merges (OK del usuario)**`.

- [ ] **Step 3: Suites finales de ambos repos**

```bash
export JAVA_HOME="/c/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH" && cd gestion-reparaciones-cliente && mvn -q test && cd ../gestion-reparaciones-servidor && mvn -q test
```
Esperado: BUILD SUCCESS ×2 (cliente 181, servidor 121).

- [ ] **Step 4: Commit (repo raíz)**

```bash
git add CHANGELOG.md docs/superpowers/specs/2026-09-02-estadisticas-puntos-ronda2-design.md && git commit -m "docs: CHANGELOG ronda 2 estadisticas y estado de la spec"
```

---

## Después del plan (pasos del usuario, NO tasks)

1. Migración `migracion-estadisticas-exclusion.sql` en preprod (vista previa incluida en el script). MER al día: columna `ES_ESTADISTICA` en la tabla `Tecnico` de `Apuntes/Tabla BBDD(Corregido).drawio` (como se hizo con `Dificultad_puntos` en la ronda 1).
2. OK merge servidor `feature/estadisticas-puntos-r2 → main` + deploy VM + **validar arranque** (sin test de contexto Spring; el wiring nuevo es solo métodos en beans existentes, riesgo bajo).
3. Smoke spec ronda 2 §8 (+ lo pendiente de la ronda 1: Valores 6/6b, roles, cliente 0.16.1, etiquetas Stock).
4. OK merge cliente `feature/estadisticas-puntos → hotfix/0.16.2`.
5. Brainstorm de "facilitar la asignación de glass" (misma release 0.16.2). El tag espera.
