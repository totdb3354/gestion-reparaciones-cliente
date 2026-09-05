# Glass automática al asignar la reparación (glass, bloque 2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el modal de asignación del SuperTécnico, marcar "Lleva glass" en una reparación hace que al pulsar Asignar nazca en la cola Glass una entrada verde "auto" del mismo IMEI (modelo y cliente heredados) con el técnico habilitado para glass de menor carga; los habilitados viven en un flag `ES_GLASS` de `Tecnico` que el SuperTécnico edita desde Asignaciones.

**Architecture:** Servidor: columna aditiva `ES_GLASS` + campo `esGlass` en el JSON de técnicos + `PATCH /api/tecnicos/{id}/glass` (SUPERTECNICO, log). Cliente: helper puro `PrediccionGlass` (candidatos = activos con flag no ocupados en ese IMEI; gana la menor fracción de jornada de 9h sin escalar, contando BD + glass verdes del modal, con alcance Pedidos si el IMEI tiene cliente y Total si no; empate alfabético); en el modal, la casilla "Lleva glass" crea/retira la glass automática desde `asignarActual`, la pila muestra técnicos + pastilla "auto", los toggles de cola llevan contador y la lista de técnicos marca "glass" a los habilitados en la cola Glass; diálogo "Técnicos de glass" en la cabecera de Asignaciones (Admin solo lectura).

**Tech Stack:** Servidor Spring Boot 3.3.4 / Java 17 / JUnit 5 + Mockito / MariaDB. Cliente JavaFX 21 / Java 17 / JUnit 5 / Gson / Maven 3.9.16 (toolchain portable).

Spec: `docs/superpowers/specs/2026-09-05-glass-prediccion-design.md` (commit `825d080` en `hotfix/0.16.2`).

## Global Constraints

- **Dos repos, dos ramas, orden fijo.** Servidor primero (Tasks 1-3), cliente después (Tasks 4-8): sin servidor nuevo desplegado no hay habilitados y el smoke del cliente no se puede hacer.
  - Servidor: repo `C:\Users\dev\Documents\ProgramaReparaciones\gestion-reparaciones-servidor` (repo git propio; en el raíz aparece como gitlink `M`). Rama **`feature/glass-habilitados` desde `main`** (tip `b1b1816`, desplegado en preprod). **Las tareas de servidor NUNCA ejecutan git en el repo raíz** (todos los comandos git con `git -C gestion-reparaciones-servidor …` o desde dentro de esa carpeta).
  - Cliente: repo raíz `C:\Users\dev\Documents\ProgramaReparaciones`. Rama **`feature/glass-prediccion` desde `hotfix/0.16.2`** (tip con spec + plan). **No tocar `main` del raíz.** El gitlink `gestion-reparaciones-servidor` aparece como `M` y **NO se commitea** en tareas de feature (solo en el commit de release). **Nunca `git add -A` / `git commit -a`** en el raíz: añadir ficheros por nombre.
- **Commits sin `Co-Authored-By`.** Mensajes en español: `feat(servidor):`, `feat(cliente):`, `test(…):`, `docs:`.
- **Maven en Bash** necesita el prefijo del toolchain portable en cada llamada:
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.
  - Suite servidor: `mvn -q -o -f gestion-reparaciones-servidor/pom.xml test` (silencio = BUILD SUCCESS; base **123 tests** verdes en `b1b1816`). Para ver el conteo: sin `-q`, buscar `Tests run:`.
  - Suite cliente: `mvn -q -o -f gestion-reparaciones-cliente/pom.xml test` (base **191 tests** en `f04e823`). Una clase: `-Dtest=PrediccionGlassTest`.
- **Merge, push, despliegue, migración y tag: solo con OK explícito del usuario.** Claude **no hace SSH** a la VM: prepara comandos y el usuario los ejecuta (Task 3).
- **Migración aditiva** `ALTER TABLE Tecnico ADD COLUMN ES_GLASS BOOLEAN NOT NULL DEFAULT FALSE` y **se aplica ANTES de levantar el backend nuevo** (el nuevo `SELECT` la lee; el backend viejo la ignora).
- Textos exactos de UI (spec §2, §3.3): casilla **"Lleva glass"**; nota **"ya tiene glass: <técnico>"**; pastilla **"auto"**; pastilla **"glass"**; botón **"Técnicos de glass"**; diálogo título **"Técnicos de glass"**, cabecera **"A quién se le asigna la glass automáticamente"**, nota al pie **"Al marcar «Lleva glass» en una reparación, la glass va al técnico marcado aquí con menos carga de glass hoy. Si no hay ninguno, la glass queda pendiente para asignarla a mano."** Acciones de log **`HABILITAR_GLASS`** / **`DESHABILITAR_GLASS`**, detalle `ID_TEC: n, NOMBRE: <nombre>`.
- **Glass automática**: nace SOLO desde `asignarActual` de una reparación; sin comentario; `modeloBuscado = true`; no toca `defTecnicos` (pegajoso); no nace si el IMEI ya está en `pilaGlass`; se retira al desmarcar/✕ SOLO si `auto == true`; "Guardar cambios" sobre una glass la deja `auto = false`. **Pulido no participa.**
- Los números de línea de este plan son del tip **`f04e823`** (= `825d080` en código) y **se desplazan con cada tarea**: localizar siempre por el texto exacto (grep) antes de editar.

---

### Task 1: Servidor — rama, migración, modelo, DAO y docs de BD

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-glass-habilitados.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql:52-58` (tabla `Tecnico`)
- Modify: `gestion-reparaciones-servidor/docs/schema.md:36-42` (tabla `Tecnico`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Tecnico.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java:15-42`

**Interfaces:**
- Produces: `Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass)` + `boolean isEsGlass()` (JSON `"esGlass"`).
- Produces: `int TecnicoDAO.setGlass(int idTec, boolean habilitado)` → filas actualizadas (0 = técnico inexistente). La usa el endpoint de la Task 2.

- [ ] **Step 1: Crear la rama del servidor desde `main`**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor status --short        # esperado: vacío
git -C gestion-reparaciones-servidor log --oneline -1       # esperado: b1b1816 feat: nImeis ...
git -C gestion-reparaciones-servidor checkout -b feature/glass-habilitados main
git -C gestion-reparaciones-servidor branch --show-current  # feature/glass-habilitados
```

- [ ] **Step 2: Escribir la migración**

Crear `gestion-reparaciones-servidor/sql/migracion-glass-habilitados.sql`:

```sql
-- Migración técnicos habilitados para glass (spec 2026-09-05-glass-prediccion-design).
USE gestion_reparaciones;

-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Tecnico'
--      AND COLUMN_NAME = 'ES_GLASS';  -- esperado: 0

ALTER TABLE Tecnico ADD COLUMN ES_GLASS BOOLEAN NOT NULL DEFAULT FALSE;

-- Verificación post: SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico;  -- todos a 0 (nadie habilitado hasta marcarlo)
```

- [ ] **Step 3: `crear_bd.sql` y `schema.md` en sync**

En `crear_bd.sql`, dentro de `CREATE TABLE Tecnico (…)`, tras la línea `ES_ESTADISTICA BOOLEAN      NOT NULL DEFAULT TRUE,` añadir:

```sql
    ES_GLASS       BOOLEAN      NOT NULL DEFAULT FALSE,
```

En `docs/schema.md`, tabla `Tecnico`, tras la fila de `ES_ESTADISTICA` añadir:

```markdown
| ES_GLASS | BOOLEAN | habilitado para la glass automática del modal de asignación (spec 2026-09-05-glass-prediccion); default 0 |
```

- [ ] **Step 4: Modelo `Tecnico` con `esGlass`**

Sustituir el contenido de `model/Tecnico.java` por:

```java
package com.reparaciones.servidor.model;

public class Tecnico {
    private int idTec;
    private String nombre;
    private boolean activo;
    private boolean esEstadistica;
    private boolean esGlass;   // habilitado para la glass automática (spec 2026-09-05-glass-prediccion)

    public Tecnico() {}

    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
        this.esGlass       = esGlass;
    }

    public int     getIdTec()         { return idTec; }
    public String  getNombre()        { return nombre; }
    public boolean isActivo()         { return activo; }
    public boolean isEsEstadistica()  { return esEstadistica; }
    public boolean isEsGlass()        { return esGlass; }
}
```

(El único caller del constructor de 4 argumentos es `TecnicoDAO.MAPPER`; se cambia en el paso siguiente. Verificar: `grep -rn "new Tecnico(" gestion-reparaciones-servidor/src` debe listar solo el DAO.)

- [ ] **Step 5: `TecnicoDAO` lee `ES_GLASS` y expone `setGlass`**

En `dao/TecnicoDAO.java`:

```java
    private static final RowMapper<Tecnico> MAPPER = (rs, row) -> new Tecnico(
            rs.getInt("ID_TEC"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO"),
            rs.getBoolean("ES_ESTADISTICA"),
            rs.getBoolean("ES_GLASS")
    );
```

En los dos `SELECT` (`getAll` y `getAllActivos`) cambiar `SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO, t.ES_ESTADISTICA FROM Tecnico t` por:

```sql
SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO, t.ES_ESTADISTICA, t.ES_GLASS FROM Tecnico t
```

Añadir tras `eliminar`:

```java
    /** Habilita/deshabilita al técnico para la glass automática del modal de asignación
     *  (spec 2026-09-05-glass-prediccion). Devuelve filas tocadas: 0 = el técnico no existe. */
    public int setGlass(int idTec, boolean habilitado) {
        return jdbc.update("UPDATE Tecnico SET ES_GLASS = ? WHERE ID_TEC = ?", habilitado, idTec);
    }
```

- [ ] **Step 6: Compilar y pasar la suite del servidor**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -q -o -f gestion-reparaciones-servidor/pom.xml test
```

Expected: silencio (BUILD SUCCESS), 123 tests.

- [ ] **Step 7: Commit (solo en el repo del servidor)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add sql/migracion-glass-habilitados.sql sql/crear_bd.sql docs/schema.md \
        src/main/java/com/reparaciones/servidor/model/Tecnico.java \
        src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java
git commit -m "feat(servidor): flag ES_GLASS en Tecnico (migracion aditiva, modelo, DAO) para la glass automatica"
git log --oneline -1
```

---

### Task 2: Servidor — `PATCH /api/tecnicos/{idTec}/glass` (TDD) + contrato de API

**Files:**
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/TecnicoControllerGlassTest.java` (nuevo)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TecnicoController.java`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md:44-56` (sección `/api/tecnicos`)

**Interfaces:**
- Consumes: `TecnicoDAO.setGlass(int, boolean)` (Task 1), `TecnicoDAO.getNombreById(int)` (existente), `LogDAO.insertar(int idUsu, String accion, String detalle)`.
- Produces: `PATCH /api/tecnicos/{idTec}/glass`, body `{ "habilitado": true|false }`, 204; 404 si no existe; 403 si no es SUPERTECNICO (lo hace `@PreAuthorize`). Lo consume `TecnicoDAO.setGlass` del cliente (Task 4).

- [ ] **Step 1: Escribir el test (falla: no existe `setGlass` ni `GlassRequest`)**

Crear `TecnicoControllerGlassTest.java` (calcado de `UsuarioControllerExclusionTest`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PATCH /api/tecnicos/{idTec}/glass (spec 2026-09-05-glass-prediccion, §3.2). El rol lo filtra @PreAuthorize. */
class TecnicoControllerGlassTest {

    private final TecnicoDAO dao = mock(TecnicoDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final TecnicoController ctl = new TecnicoController(dao, logDao);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(2, "super", "x", "SUPERTECNICO", 9);

    @Test void habilitarActualizaYLoguea() {
        when(dao.setGlass(7, true)).thenReturn(1);
        when(dao.getNombreById(7)).thenReturn("Javi");
        ctl.setGlass(7, new TecnicoController.GlassRequest(true), supertecnico);
        verify(dao).setGlass(7, true);
        verify(logDao).insertar(2, "HABILITAR_GLASS", "ID_TEC: 7, NOMBRE: Javi");
    }

    @Test void deshabilitarActualizaYLoguea() {
        when(dao.setGlass(7, false)).thenReturn(1);
        when(dao.getNombreById(7)).thenReturn("Javi");
        ctl.setGlass(7, new TecnicoController.GlassRequest(false), supertecnico);
        verify(dao).setGlass(7, false);
        verify(logDao).insertar(2, "DESHABILITAR_GLASS", "ID_TEC: 7, NOMBRE: Javi");
    }

    @Test void tecnicoInexistenteDa404SinLog() {
        when(dao.setGlass(99, true)).thenReturn(0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.setGlass(99, new TecnicoController.GlassRequest(true), supertecnico));
        assertEquals(404, ex.getStatusCode().value());
        verify(dao, never()).getNombreById(anyInt());
        verifyNoInteractions(logDao);
    }
}
```

- [ ] **Step 2: Ejecutar el test para verlo fallar**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-servidor/pom.xml test -Dtest=TecnicoControllerGlassTest 2>&1 | grep -E "ERROR|cannot find symbol|BUILD" | head -5
```

Expected: error de compilación `cannot find symbol` (`setGlass` / `GlassRequest`), BUILD FAILURE.

- [ ] **Step 3: Implementar el endpoint**

En `TecnicoController.java` añadir imports:

```java
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
```

Añadir el método (antes de `private record NombreRequest`) y el record:

```java
    /** Habilita/deshabilita al técnico para la glass automática del modal de asignación
     *  (spec 2026-09-05-glass-prediccion, §3.2). Solo SuperTécnico: Admin ve el diálogo pero no edita (403 aquí). */
    @PatchMapping("/{idTec}/glass")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setGlass(@PathVariable int idTec, @RequestBody GlassRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        int filas = dao.setGlass(idTec, req.habilitado());
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Técnico no encontrado: " + idTec);
        }
        String nombre = dao.getNombreById(idTec);
        logDao.insertar(principal.getIdUsu(),
                req.habilitado() ? "HABILITAR_GLASS" : "DESHABILITAR_GLASS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    /** Package-private (no private) para que el test lo construya; Jackson lo deserializa igual. */
    record GlassRequest(boolean habilitado) {}
```

- [ ] **Step 4: Ejecutar el test y la suite completa**

```bash
mvn -o -f gestion-reparaciones-servidor/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD" | tail -2
```

Expected: `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS`.

- [ ] **Step 5: Contrato de API**

En `docs/api_contract.md`, sección `/api/tecnicos`:
- En la respuesta de `GET /api/tecnicos` cambiar `"esEstadistica": true }` por `"esEstadistica": true, "esGlass": false }`.
- Añadir al final de la sección (antes de la siguiente `## `):

```markdown
### PATCH `/api/tecnicos/{idTec}/glass`
Habilita o deshabilita al técnico para la glass automática del modal de asignación (spec 2026-09-05-glass-prediccion). Rol: `SUPERTECNICO` (Admin → 403).  
**Request:** `{ "habilitado": true }`  
**Response:** 204. 404 si el técnico no existe. Log `HABILITAR_GLASS` / `DESHABILITAR_GLASS` con `ID_TEC: n, NOMBRE: <nombre>`.
```

- [ ] **Step 6: Commit y estado final del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add src/main/java/com/reparaciones/servidor/controller/TecnicoController.java \
        src/test/java/com/reparaciones/servidor/controller/TecnicoControllerGlassTest.java \
        docs/api_contract.md
git commit -m "feat(servidor): PATCH /api/tecnicos/{id}/glass (SUPERTECNICO, log HABILITAR/DESHABILITAR_GLASS) + contrato"
git log --oneline main..feature/glass-habilitados      # 2 commits
git status --short                                     # vacío
```

Nota de arranque (memoria `feedback_server_spring_startup`): no hay bean nuevo ni cambio de constructores (solo un método más en un controller existente), así que el riesgo de wiring es nulo; el arranque real se valida en el despliegue (Task 3, `docker logs`).

**Fin de la parte de servidor en código. NO mergear ni pushear: pedir OK al usuario (Task 3).**

---

### Task 3: Servidor — merge, push, migración y despliegue en preprod (lo ejecuta el usuario)

**Files:** ninguno (operativa). Esta tarea la dirige el controlador de sesión, no un subagente.

- [ ] **Step 1: Pedir OK al usuario para merge `--no-ff` a `main` + push**

Con OK:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main
git merge --no-ff feature/glass-habilitados -m "Merge branch 'feature/glass-habilitados' (ES_GLASS + PATCH tecnicos/{id}/glass)"
git push origin main
git log --oneline -1          # anotar el SHA: es el que irá al gitlink en la release
```

- [ ] **Step 2: Entregar al usuario los comandos de la VM (Claude NO hace SSH). Orden: migración PRIMERO, backend después**

En la VM de preprod (`82.165.173.220`), en el directorio del clone del servidor donde vive el `docker-compose.yml` (`/opt/reparaciones`); la contraseña de root de MariaDB está en ese `docker-compose.yml`:

```bash
# 1) Vista previa (esperado: 0)
docker exec -i reparaciones-mariadb-1 mariadb -u root -p'<pass>' gestion_reparaciones -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Tecnico' AND COLUMN_NAME = 'ES_GLASS';"
# 2) Migración
docker exec -i reparaciones-mariadb-1 mariadb -u root -p'<pass>' gestion_reparaciones -e "ALTER TABLE Tecnico ADD COLUMN ES_GLASS BOOLEAN NOT NULL DEFAULT FALSE;"
# 3) Verificación (todos a 0)
docker exec -i reparaciones-mariadb-1 mariadb -u root -p'<pass>' gestion_reparaciones -e "SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico;"
# 4) Backend nuevo
cd /opt/reparaciones && git pull && docker compose build --no-cache backend && docker compose up -d backend
# 5) Arranque (esperar "Started" sin BeanCreationException)
docker logs reparaciones-backend-1 --tail 30
```

- [ ] **Step 3: Verificar el campo nuevo desde fuera (el usuario pega la salida)**

Desde el cliente en preprod (o `curl` con un token válido): `GET /api/tecnicos/activos` debe traer `"esGlass": false` en cada técnico. Con eso la Task 4 puede empezar.

- [ ] **Step 4: Anotar en `.superpowers/sdd/progress.md`** el SHA de `main` del servidor desplegado (línea `GLASS BLOQUE 2 SERVIDOR DESPLEGADO (fecha): main <sha> …`).

---

### Task 4: Cliente — rama, `Tecnico.esGlass`, `TecnicoDAO.setGlass`, acciones de log (TDD)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/Tecnico.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TecnicoDAO.java` (tras `eliminar`, `:60-62`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java:57`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/models/TecnicoTest.java`

**Interfaces:**
- Produces: `Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass)` y `boolean isEsGlass()` (`false` si el JSON no trae el campo). Los usan `PrediccionGlass` (Task 5), el modal (Task 7) y el diálogo (Task 8).
- Produces: `void TecnicoDAO.setGlass(int idTec, boolean habilitado) throws SQLException` → `PATCH /api/tecnicos/{idTec}/glass` con `{ "habilitado": … }`. Lo usa el diálogo (Task 8).

- [ ] **Step 1: Crear la rama del cliente desde `hotfix/0.16.2`**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git branch --show-current          # hotfix/0.16.2
git status --short                 # solo "M gestion-reparaciones-servidor" y untracked (.codegraph, .superpowers, PDFs)
git checkout -b feature/glass-prediccion hotfix/0.16.2
```

- [ ] **Step 2: Escribir los tests (fallan: no existe el constructor de 5 args ni `isEsGlass`)**

Añadir a `TecnicoTest.java` (dentro de la clase):

```java
    @Test
    void esGlass_porDefectoFalse() {
        assertFalse(new Tecnico(1, "Daniel García", true).isEsGlass());
        assertFalse(new Tecnico(1, "Daniel García", true, true).isEsGlass());
    }

    @Test
    void esGlass_constructorCompleto() {
        assertTrue(new Tecnico(1, "Daniel García", true, true, true).isEsGlass());
        assertFalse(new Tecnico(1, "Daniel García", true, true, false).isEsGlass());
    }

    @Test
    void esGlass_jsonSinElCampoCuentaComoNoHabilitado() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Tecnico viejo = gson.fromJson("{\"idTec\":1,\"nombre\":\"Javi\",\"activo\":true}", Tecnico.class);
        Tecnico nuevo = gson.fromJson("{\"idTec\":1,\"nombre\":\"Javi\",\"activo\":true,\"esGlass\":true}", Tecnico.class);
        assertFalse(viejo.isEsGlass());
        assertTrue(nuevo.isEsGlass());
    }
```

- [ ] **Step 3: Ejecutar y ver fallar**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-cliente/pom.xml test -Dtest=TecnicoTest 2>&1 | grep -E "cannot find symbol|BUILD" | head -3
```

Expected: `cannot find symbol` (`isEsGlass`), BUILD FAILURE.

- [ ] **Step 4: Implementar en `models/Tecnico.java`**

Tras el campo `esEstadistica` añadir:

```java
    /** {@code true} si entra en la glass automática del modal de asignación (spec 2026-09-05-glass-prediccion).
     *  Un JSON sin el campo (servidor anterior a la 0.16.2) cuenta como NO habilitado. */
    private Boolean esGlass;
```

Cambiar el constructor de 4 argumentos para que delegue y añadir el de 5:

```java
    /**
     * @param idTec         clave primaria del técnico
     * @param nombre        nombre visible
     * @param activo        {@code true} si está activo
     * @param esEstadistica {@code true} si cuenta en la vista de estadísticas
     */
    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica) {
        this(idTec, nombre, activo, esEstadistica, false);
    }

    /**
     * @param esGlass {@code true} si está habilitado para la glass automática del modal de asignación
     */
    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
        this.esGlass       = esGlass;
    }
```

Tras `isEsEstadistica()` añadir:

```java
    /** @return {@code true} si está habilitado para la glass automática; sin el campo (servidor
     *  anterior a la 0.16.2) cuenta como no habilitado. */
    public boolean isEsGlass() { return esGlass != null && esGlass; }
```

- [ ] **Step 5: `TecnicoDAO.setGlass` y acciones de log**

En `dao/TecnicoDAO.java`, tras `eliminar`:

```java
    /**
     * Habilita o deshabilita al técnico para la glass automática del modal de asignación
     * (spec 2026-09-05-glass-prediccion). Solo SuperTécnico: el servidor devuelve 403 al resto
     * y 404 si es anterior a la 0.16.2 (sin el endpoint).
     *
     * @param idTec      ID del técnico
     * @param habilitado {@code true} para habilitarlo
     * @throws SQLException si falla la llamada al servidor
     */
    public void setGlass(int idTec, boolean habilitado) throws SQLException {
        ApiClient.patch("/api/tecnicos/" + idTec + "/glass", Map.of("habilitado", habilitado));
    }
```

En `LogController.java`, en la lista de acciones, tras `"EXCLUIR_ESTADISTICAS", "INCLUIR_ESTADISTICAS",` añadir la línea:

```java
        "HABILITAR_GLASS", "DESHABILITAR_GLASS",
```

- [ ] **Step 6: Tests en verde y suite completa**

```bash
mvn -o -f gestion-reparaciones-cliente/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD" | tail -2
```

Expected: `Tests run: 194, Failures: 0, Errors: 0` y `BUILD SUCCESS`.

- [ ] **Step 7: Commit (por nombre, sin el gitlink)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/Tecnico.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TecnicoDAO.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/models/TecnicoTest.java
git commit -m "feat(cliente): Tecnico.esGlass (false si el servidor no lo manda) + TecnicoDAO.setGlass + acciones de log de glass"
git status --short | grep -v "^??"      # solo "M gestion-reparaciones-servidor"
```

---

### Task 5: Cliente — `PrediccionGlass` (helper puro, TDD)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PrediccionGlass.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CargaTecnicos.java:63-64` (`fraccion9h` pasa a package-private)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PrediccionGlassTest.java` (nuevo)

**Interfaces:**
- Consumes: `Tecnico.isEsGlass()`, `Tecnico.isActivo()`, `Tecnico.getNombre()` (Task 4); `CargaTecnicos.fraccion9h(ReparacionResumen r, boolean esAbierta, boolean soloPedidos)` (package-private tras este task); `CargaTecnicos.TOPE_GLASS_9H`; `TipoTrabajo.desde(String idRep)`.
- Produces: `public record PrediccionGlass.GlassEnModal(String imei, int idTec, boolean conCliente)` y `public static Tecnico PrediccionGlass.elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, List<GlassEnModal> verdesModal, String imei, boolean conCliente)` (devuelve `null` sin candidato). Lo usa el modal (Task 6).

- [ ] **Step 1: Escribir los tests (fallan: la clase no existe)**

Crear `PrediccionGlassTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Elección del técnico de la glass automática (spec 2026-09-05-glass-prediccion, §4). El helper no recibe
 *  el día de la semana: compara fracciones de 9h sin escalar, así que un sábado reparte igual que un martes. */
class PrediccionGlassTest {

    private static final String IMEI = "111111111111111";

    private static Tecnico tec(int id, String nombre, boolean activo, boolean esGlass) {
        return new Tecnico(id, nombre, activo, true, esGlass);
    }

    /** Asignación con los campos que usa el cálculo; el prefijo de idRep fija el tipo (A = normal, AG = glass, G = glass cerrada). */
    private static ReparacionResumen asig(String idRep, String imei, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null);
        r.setCliente(cliente);
        return r;
    }

    private static final Tecnico JAVI  = tec(1, "javi",  true, true);
    private static final Tecnico JHONA = tec(2, "jhona", true, true);
    private static final Tecnico MANU  = tec(3, "manu",  true, false);   // no habilitado

    @Test void sinHabilitadosDevuelveNull() {
        assertNull(PrediccionGlass.elegir(List.of(MANU), List.of(), List.of(), List.of(), IMEI, true));
        assertNull(PrediccionGlass.elegir(List.of(), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void inactivoConFlagQuedaFuera() {
        Tecnico baja = tec(4, "aaron", false, true);   // iría primero por alfabeto si contara
        assertSame(JAVI, PrediccionGlass.elegir(List.of(baja, JAVI), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void empateAlfabeticoSinDistinguirMayusculas() {
        Tecnico zoe = tec(5, "Zoe", true, true);
        Tecnico ana = tec(6, "ana", true, true);
        assertSame(ana, PrediccionGlass.elegir(List.of(zoe, ana), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void conGlassAbiertaDeEseImeiEnBdQuedaFuera() {
        List<ReparacionResumen> abiertas = List.of(asig("AG1", IMEI, 1, "WEB"));   // javi ya tiene glass de ese IMEI
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void unaReparacionNormalDelMismoImeiNoExcluye() {
        // javi: normal del mismo IMEI; jhona: normal de otro IMEI → misma carga (1/25) → empate → javi por alfabeto
        List<ReparacionResumen> abiertas = List.of(asig("A1", IMEI, 1, "WEB"), asig("A2", "222222222222222", 2, "WEB"));
        assertSame(JAVI, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void conGlassVerdeDeEseImeiEnElModalQuedaFuera() {
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal(IMEI, 1, true));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));
    }

    @Test void menorCargaGana() {
        List<ReparacionResumen> abiertas = List.of(
                asig("AG1", "222222222222222", 1, "WEB"), asig("AG2", "333333333333333", 1, "WEB"),   // javi: 2 glass
                asig("AG3", "444444444444444", 2, "WEB"));                                            // jhona: 1 glass
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void loCerradoHoyTambienCuenta() {
        List<ReparacionResumen> cerradas = List.of(asig("G1", "222222222222222", 1, "WEB"), asig("G2", "333333333333333", 1, "WEB"));
        List<ReparacionResumen> abiertas = List.of(asig("AG3", "444444444444444", 2, "WEB"));
        // javi 2/17 hecho hoy > jhona 1/17 pendiente
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, cerradas, List.of(), IMEI, true));
    }

    @Test void lasVerdesDelModalDesplazanLaEleccion() {
        // Sin verdes empatan a 0 → javi por alfabeto; con una verde ya a javi → jhona
        assertSame(JAVI, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), List.of(), IMEI, true));
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("222222222222222", 1, true));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));
    }

    @Test void imeiConClienteMiraSoloPedidosYSinClienteMiraTotal() {
        // javi: 3 glass de stock (sin cliente); jhona: 1 glass con cliente
        List<ReparacionResumen> abiertas = List.of(
                asig("AG1", "222222222222222", 1, null), asig("AG2", "333333333333333", 1, null), asig("AG3", "444444444444444", 1, null),
                asig("AG4", "555555555555555", 2, "WEB"));
        assertSame(JAVI,  PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));    // Pedidos: javi 0 < jhona 1/17
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, false));   // Total: jhona 1/17 < javi 3/17
    }

    @Test void verdeDelModalSinClienteSoloCuentaEnTotal() {
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("222222222222222", 1, false));
        assertSame(JAVI,  PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));    // ignorada → empate → javi
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, false));   // cuenta → jhona
    }

    @Test void laReparacionNormalDeUnHabilitadoMixtoCuenta() {
        // javi: 10 normales (10/25 = 0,40); jhona: 3 glass (3/17 ≈ 0,18) → jhona
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) abiertas.add(asig("A" + i, "22222222222222" + i, 1, "WEB"));
        for (int i = 0; i < 3; i++)  abiertas.add(asig("AG" + i, "33333333333333" + i, 2, "WEB"));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void cargaSumaFraccionesCrudasDe9h() {
        List<ReparacionResumen> abiertas = List.of(asig("AG1", "222222222222222", 1, "WEB"), asig("A2", "333333333333333", 1, "WEB"));
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("444444444444444", 1, true));
        assertEquals(2.0 / CargaTecnicos.TOPE_GLASS_9H + 1.0 / CargaTecnicos.TOPE_NORMALES_9H,
                PrediccionGlass.carga(1, abiertas, List.of(), verdes, true), 1e-12);
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-cliente/pom.xml test -Dtest=PrediccionGlassTest 2>&1 | grep -E "cannot find symbol|BUILD" | head -3
```

Expected: `cannot find symbol` (`PrediccionGlass`), BUILD FAILURE.

- [ ] **Step 3: `CargaTecnicos.fraccion9h` package-private**

En `CargaTecnicos.java` cambiar `private static double fraccion9h(ReparacionResumen r, boolean esAbierta, boolean soloPedidos) {` por:

```java
    /** Fracción de jornada de 9h que consume una asignación (0 si no computa). Package-private: la
     *  reutiliza {@link PrediccionGlass}, que compara estas fracciones sin escalarlas al día. */
    static double fraccion9h(ReparacionResumen r, boolean esAbierta, boolean soloPedidos) {
```

(y borrar el javadoc de una línea que tenía encima, `/** Fracción de jornada de 9h que consume una asignación (0 si no computa). */`, para no duplicarlo).

- [ ] **Step 4: Implementar `PrediccionGlass`**

Crear `utils/PrediccionGlass.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Elige al técnico de la glass automática (spec 2026-09-05-glass-prediccion, §4). Puro: sin UI ni red.
 * <p>Candidatos: técnicos activos con {@link Tecnico#isEsGlass()} que no tengan ya una glass abierta de
 * ese IMEI (ni en BD ni verde en el modal). Gana el de menor carga en <b>fracción de jornada de 9h sin
 * escalar</b> a las horas del día: el factor 9/horas es común a todos y en fin de semana vale 0, con lo que
 * comparar el % de pantalla mandaría siempre la glass al primero por alfabeto. La carga suma lo hecho hoy,
 * lo pendiente en BD y las glass verdes del modal que ya le tocaron (1/17 cada una), con el alcance de la
 * carga diaria: IMEI con cliente → solo lo que tiene cliente (Pedidos); sin cliente → todo (Total).
 * Empate → alfabético por nombre.</p>
 */
public final class PrediccionGlass {

    private PrediccionGlass() {}

    /** Glass verde ya configurada en el modal (sin guardar): IMEI, técnico y si tiene cliente. */
    public record GlassEnModal(String imei, int idTec, boolean conCliente) {}

    /**
     * @param tecnicos    técnicos activos del modal (se filtra por {@code isActivo() && isEsGlass()})
     * @param abiertas    asignaciones abiertas cargadas en la vista (todas las categorías)
     * @param cerradasHoy asignaciones completadas hoy
     * @param verdesModal glass verdes del modal en este momento
     * @param imei        IMEI que se asigna
     * @param conCliente  {@code true} si la reparación que se asigna tiene cliente (alcance Pedidos)
     * @return el técnico elegido, o {@code null} si no hay candidato (la glass nace roja)
     */
    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<GlassEnModal> verdesModal,
                                 String imei, boolean conCliente) {
        Set<Integer> ocupados = new HashSet<>();
        for (ReparacionResumen r : abiertas)
            if (imei.equals(r.getImei()) && TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.GLASS) ocupados.add(r.getIdTec());
        for (GlassEnModal g : verdesModal)
            if (imei.equals(g.imei())) ocupados.add(g.idTec());

        Tecnico mejor = null;
        long mejorClave = Long.MAX_VALUE;
        for (Tecnico t : tecnicos) {
            if (!t.isActivo() || !t.isEsGlass() || ocupados.contains(t.getIdTec())) continue;
            // Redondeo a 1e-9 para que dos sumas iguales en distinto orden empaten de verdad (y el orden sea transitivo).
            long clave = Math.round(carga(t.getIdTec(), abiertas, cerradasHoy, verdesModal, conCliente) * 1e9);
            if (mejor == null || clave < mejorClave
                    || (clave == mejorClave && t.getNombre().compareToIgnoreCase(mejor.getNombre()) < 0)) {
                mejor = t;
                mejorClave = clave;
            }
        }
        return mejor;
    }

    /** Fracción de jornada de 9h del técnico: hecho hoy + pendiente en BD + glass verdes del modal, con alcance Pedidos/Total. */
    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<GlassEnModal> verdesModal, boolean soloPedidos) {
        double f = 0;
        for (ReparacionResumen r : cerradasHoy) if (r.getIdTec() == idTec) f += CargaTecnicos.fraccion9h(r, false, soloPedidos);
        for (ReparacionResumen r : abiertas)    if (r.getIdTec() == idTec) f += CargaTecnicos.fraccion9h(r, true,  soloPedidos);
        for (GlassEnModal g : verdesModal)
            if (g.idTec() == idTec && (!soloPedidos || g.conCliente())) f += 1.0 / CargaTecnicos.TOPE_GLASS_9H;
        return f;
    }
}
```

- [ ] **Step 5: Tests en verde y suite completa**

```bash
mvn -o -f gestion-reparaciones-cliente/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD" | tail -2
```

Expected: `Tests run: 207, Failures: 0, Errors: 0` (194 + 13) y `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PrediccionGlass.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CargaTecnicos.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PrediccionGlassTest.java
git commit -m "feat(cliente): PrediccionGlass — tecnico de la glass automatica por menor carga (fraccion 9h, Pedidos/Total, verdes del modal)"
```

---

### Task 6: Cliente — modal: casilla "Lleva glass", nacimiento y retirada de la glass automática

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`: `EntradaAsignacion` (`:118-135`), helper nuevo junto a `tecnicosOcupados` (`:1225`), formulario (`chkChasis` `:1955`, `formBox` `:1968`), `renderPila` `onRemove` (`:2054-2058` y `:2062-2066`), `cargarEntrada` (`:2205-2207`, bloque de `chkChasis`), `asignarActual` (`:2261-2284`).

**Interfaces:**
- Consumes: `PrediccionGlass.elegir(...)` y `PrediccionGlass.GlassEnModal` (Task 5); campos del controlador `datos` (abiertas) y `cerradasHoy`; `ReparacionResumen.getNombreTecnico()`.
- Produces: `EntradaAsignacion.llevaGlass` (boolean, solo Reparación) y `EntradaAsignacion.auto` (boolean, solo Glass). Los pinta la Task 7.
- Produces: `private String tecnicoGlassAbierta(String imei)` (nombre del dueño de la primera glass abierta del IMEI en `datos`, o `null`).

- [ ] **Step 1: Campos en `EntradaAsignacion`**

Tras la línea `long seq; …` añadir:

```java
        boolean llevaGlass;                      // solo tipo Reparación: al Asignar nace (o se retira) la glass automática del IMEI
        boolean auto;                            // solo tipo Glass: nacida por predicción y aún no editada a mano ("Guardar cambios" la vuelve manual)
```

- [ ] **Step 2: Helper `tecnicoGlassAbierta` (junto a `tecnicosOcupados`)**

Tras el método `tecnicosOcupados` añadir:

```java
    /** Nombre del técnico de la primera glass abierta de ese IMEI en la tabla cargada, o {@code null} si no
     *  hay: con glass abierta en BD la casilla "Lleva glass" se deshabilita (spec 2026-09-05-glass-prediccion, regla 4). */
    private String tecnicoGlassAbierta(String imei) {
        for (ReparacionResumen r : datos)
            if (imei.equals(r.getImei()) && tipoDe(r.getIdRep()) == TipoTrabajo.GLASS) return r.getNombreTecnico();
        return null;
    }
```

- [ ] **Step 3: Casilla y nota en el formulario**

Tras `chkChasis.setStyle(...)` (`:1956`) añadir:

```java
        // Lleva glass (spec 2026-09-05-glass-prediccion): como chasis, por IMEI y sin efecto hasta Asignar.
        CheckBox chkLlevaGlass = new CheckBox("Lleva glass");
        chkLlevaGlass.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376;");
        Label lblGlassNota = new Label();
        lblGlassNota.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #586376; -fx-font-style: italic;");
        lblGlassNota.setVisible(false); lblGlassNota.setManaged(false);
        HBox filaGlass = new HBox(8, chkLlevaGlass, lblGlassNota);
        filaGlass.setAlignment(Pos.CENTER_LEFT);
```

En la construcción de `formBox` cambiar `lblComentario, tfComentario, chkChasis, accionesForm);` por:

```java
                lblComentario, tfComentario, chkChasis, filaGlass, accionesForm);
```

- [ ] **Step 4: `cargarEntrada` pinta la casilla**

Tras `chkChasis.setSelected(e.esChasis);   // entrada nueva = false → …` añadir:

```java
            // Lleva glass: solo en Reparación; deshabilitada con nota si el IMEI ya tiene glass abierta en BD.
            filaGlass.setVisible(esRep); filaGlass.setManaged(esRep);
            String glassAbierta = esRep ? tecnicoGlassAbierta(e.imei) : null;
            boolean yaTieneGlass = glassAbierta != null;
            if (yaTieneGlass) e.llevaGlass = false;
            chkLlevaGlass.setDisable(yaTieneGlass);
            chkLlevaGlass.setSelected(e.llevaGlass);
            lblGlassNota.setText(yaTieneGlass ? "ya tiene glass: " + glassAbierta : "");
            lblGlassNota.setVisible(yaTieneGlass); lblGlassNota.setManaged(yaTieneGlass);
```

- [ ] **Step 5: Nacimiento/retirada desde `asignarActual`**

Justo antes de `Runnable asignarActual = () -> {` añadir:

```java
        // Glass verdes del modal, tal como las ve PrediccionGlass (una por técnico de cada entrada verde).
        java.util.function.Supplier<List<com.reparaciones.utils.PrediccionGlass.GlassEnModal>> verdesGlassModal = () -> {
            List<com.reparaciones.utils.PrediccionGlass.GlassEnModal> out = new ArrayList<>();
            for (EntradaAsignacion x : pilaGlass)
                if (x.asignada)
                    for (Tecnico t : x.tecnicos)
                        out.add(new com.reparaciones.utils.PrediccionGlass.GlassEnModal(x.imei, t.getIdTec(), x.cliente != null));
            return out;
        };
        // Glass automática (spec 2026-09-05-glass-prediccion): al asignar una reparación con "Lleva glass" nace en la
        // cola Glass una entrada del mismo IMEI, verde con el técnico que elige PrediccionGlass (roja si no puede elegir).
        // Sin la casilla, retira la glass de ese IMEI solo si sigue siendo automática (editada a mano = del usuario).
        // No toca defTecnicos (no es una decisión del usuario) ni relanza el lookup (modelo y cliente vienen de la
        // reparación, cuyo cliente ya es decisión manual en clienteManual al llegar aquí).
        java.util.function.Consumer<EntradaAsignacion> sincronizarGlassAuto = e -> {
            EntradaAsignacion existente = pilaGlass.stream().filter(x -> x.imei.equals(e.imei)).findFirst().orElse(null);
            if (e.llevaGlass) {
                if (existente != null) return;   // ya hay glass de ese IMEI en la cola (roja o verde): no se toca
                EntradaAsignacion g = new EntradaAsignacion(e.imei);
                g.tipo = TipoTrabajo.GLASS;
                g.modeloCode = e.modeloCode;
                g.cliente = e.cliente;
                g.sinCliente = e.sinCliente;
                g.comentario = "";
                g.seq = ++seqCounter[0];
                g.modeloBuscado = true;
                g.auto = true;
                Tecnico t = com.reparaciones.utils.PrediccionGlass.elegir(
                        tecnicosModal, datos, cerradasHoy, verdesGlassModal.get(), e.imei, e.cliente != null);
                if (t != null) { g.tecnicos.add(t); g.asignada = true; }
                pilaGlass.add(g);
            } else if (existente != null && existente.auto) {
                pilaGlass.remove(existente);
                if (actual[0] == existente) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
            }
        };
```

Dentro de `asignarActual`, tras `def.clear(); def.addAll(sel);   // los técnicos se mantienen …` y antes de `renderPila[0].run();` añadir:

```java
            if (e.tipo == TipoTrabajo.REPARACION) {
                e.llevaGlass = chkLlevaGlass.isSelected() && !chkLlevaGlass.isDisabled();
                sincronizarGlassAuto.accept(e);
            } else if (e.tipo == TipoTrabajo.GLASS) {
                e.auto = false;   // "Guardar cambios" (o asignar a mano una roja): la glass pasa a ser del usuario
            }
```

- [ ] **Step 6: ✕ en la reparación retira su glass automática**

En `renderPila[0]`, en los DOS `onRemove` (bucle de `rojos` y bucle de `verdes`), tras `activa.remove(e);` añadir la misma línea:

```java
                    if (e.tipo == TipoTrabajo.REPARACION) pilaGlass.removeIf(x -> x.imei.equals(e.imei) && x.auto);
```

- [ ] **Step 7: Compilar y pasar la suite**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-cliente/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD|ERROR.*java" | tail -3
```

Expected: `Tests run: 207, Failures: 0` y `BUILD SUCCESS`. (Sin test unitario nuevo: la lógica vive en lambdas del modal, como `propagarCliente`; la elección está cubierta en la Task 5.)

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(cliente): casilla 'Lleva glass' en el modal — al Asignar nace la glass automatica (verde con tecnico por carga, roja si no hay) y se retira al desmarcar o quitar la reparacion"
```

---

### Task 7: Cliente — modal: técnicos + pastilla "auto" en la pila, contadores en los toggles, etiqueta "glass"

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`: `etiquetaConCargaNodo` (`:1156-1175`), `crearFilaPila` (`:1233-1290`), método estático nuevo `pintarContadorCola`, `renderPila` (`:2047-2089`), listener de `tgTipo` (`:2444-2453`).

**Interfaces:**
- Consumes: `EntradaAsignacion.auto`, `EntradaAsignacion.tecnicos` (Task 6); `Tecnico.isEsGlass()` (Task 4); `TipoTrabajo.GLASS.colorFondo()/colorTexto()`.
- Produces: `private HBox etiquetaConCargaNodo(Tecnico t, boolean resaltada, boolean marcarGlass)` (la de 2 args delega con `false`); `private static void pintarContadorCola(ToggleButton tb, int total, int pendientes)`.

- [ ] **Step 1: Fila de la pila con técnicos y "auto"**

En `crearFilaPila`, sustituir el bloque que va desde `HBox contenido = new HBox(8, lblImei, badgeTipo, estado);` hasta `contenido.setClip(clip);` por:

```java
        // Línea 1: IMEI + tipo + modelo (como siempre). Línea 2 (solo verdes): pastilla "auto" si la eligió el
        // programa + nombres de sus técnicos, para supervisar la glass automática de un vistazo (spec 2026-09-05).
        HBox linea1 = new HBox(8, lblImei, badgeTipo, estado);
        linea1.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(3, linea1);
        contenido.setAlignment(Pos.CENTER_LEFT);
        if (e.asignada && !e.tecnicos.isEmpty()) {
            HBox linea2 = new HBox(6);
            linea2.setAlignment(Pos.CENTER_LEFT);
            if (e.auto) {
                Label badgeAuto = new Label("auto");
                badgeAuto.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                badgeAuto.setStyle("-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 1 6 1 6;"
                        + " -fx-background-color: " + TipoTrabajo.GLASS.colorFondo() + "; -fx-text-fill: " + TipoTrabajo.GLASS.colorTexto() + ";");
                linea2.getChildren().add(badgeAuto);
            }
            Label lblTecs = new Label(e.tecnicos.stream().map(Tecnico::getNombre)
                    .collect(java.util.stream.Collectors.joining(", ")));
            lblTecs.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #586376;");
            linea2.getChildren().add(lblTecs);
            contenido.getChildren().add(linea2);
        }
        contenido.setMinWidth(0);
        HBox.setHgrow(contenido, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(contenido.widthProperty());
        clip.heightProperty().bind(contenido.heightProperty());
        contenido.setClip(clip);
```

(El resto del método — la ✕ y `HBox fila = new HBox(8, contenido, x);` — no cambia.) Las filas verdes pasan a dos líneas: en `renderPila` cambiar `scrollVerde.setPrefHeight(nVerde == 0 ? 34 : Math.min(nVerde, 5) * 39 + 4);` por:

```java
            scrollVerde.setPrefHeight(nVerde == 0 ? 34 : Math.min(nVerde, 5) * 57 + 4);   // dos líneas por fila verde (ajustar si en el smoke queda corto)
```

- [ ] **Step 2: Contadores en los toggles de cola**

Añadir un método estático al controlador (p. ej. tras `colorNivelTexto`):

```java
    /** Contador de una cola del modal en su botón de tipo: pastilla "N" a la derecha del texto, roja si hay
     *  entradas pendientes (rojas / pulido sin técnico), gris si no; sin pastilla con 0 entradas
     *  (spec 2026-09-05-glass-prediccion, regla 8: que se vea desde Reparación lo que nace en Glass). */
    private static void pintarContadorCola(ToggleButton tb, int total, int pendientes) {
        if (total == 0) { tb.setGraphic(null); return; }
        Label pill = new Label(String.valueOf(total));
        pill.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        pill.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 7 1 7;"
                + (pendientes > 0 ? " -fx-background-color: #FDE2E1; -fx-text-fill: #C0392B;"
                                  : " -fx-background-color: #E8EAF0; -fx-text-fill: #586376;"));
        tb.setGraphic(pill);
        tb.setContentDisplay(ContentDisplay.RIGHT);
        tb.setGraphicTextGap(6);
    }
```

En `renderPila[0]`, tras la línea `btnGuardar.setDisable(nRojoGlobal != 0 || pulidoSinTecnico > 0 || (nVerdeGlobal + nPul) == 0);` añadir:

```java
            pintarContadorCola(tbRep,    pilaRep.size(),   (int) pilaRep.stream().filter(x -> !x.asignada).count());
            pintarContadorCola(tbGlass,  pilaGlass.size(), (int) pilaGlass.stream().filter(x -> !x.asignada).count());
            pintarContadorCola(tbPulido, nPul,             pulidoSinTecnico);
```

(`tbRep`, `tbGlass`, `tbPulido` se declaran antes que `renderPila`, `:1687-1689`.)

- [ ] **Step 3: Etiqueta "glass" en la lista de técnicos (solo cola Glass)**

Sustituir la firma `private HBox etiquetaConCargaNodo(Tecnico t, boolean resaltada) {` y su cuerpo por:

```java
    private HBox etiquetaConCargaNodo(Tecnico t, boolean resaltada) {
        return etiquetaConCargaNodo(t, resaltada, false);
    }

    /** Variante con la pastilla "glass" (paleta del tipo Glass) tras el porcentaje: la usa el modal de
     *  asignación en la cola Glass para señalar a los habilitados de la glass automática (spec 2026-09-05, §3.3).
     *  No filtra ni marca nada: solo orienta al cambiar a mano una glass automática. */
    private HBox etiquetaConCargaNodo(Tecnico t, boolean resaltada, boolean marcarGlass) {
        double pctPedidos = diaDe(cargaDiaPedidos, t.getIdTec()).pctTotal();

        String colorNombre       = resaltada ? "#FAFAFA" : "#2C3B54";
        String colorPuntoPedidos = resaltada ? "#FAFAFA" : "#7B1FA2";
        String colorPctPedidos   = resaltada ? "#FAFAFA" : colorNivelTexto(pctPedidos);

        Label lblNombre = new Label(t.getNombre());
        lblNombre.setStyle("-fx-text-fill: " + colorNombre + ";");

        Label lblPuntoPedidos = new Label("●");
        lblPuntoPedidos.setStyle("-fx-text-fill: " + colorPuntoPedidos + "; -fx-font-size: 9px;");
        Label lblPctPedidos = new Label(CargaTecnicos.formatearPct(pctPedidos));
        lblPctPedidos.setStyle("-fx-text-fill: " + colorPctPedidos + "; -fx-font-weight: bold;");

        HBox caja = new HBox(4, lblNombre, lblPuntoPedidos, lblPctPedidos);
        caja.setAlignment(Pos.CENTER_LEFT);
        if (marcarGlass) {
            Label pillGlass = new Label("glass");
            pillGlass.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            pillGlass.setStyle("-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 1 6 1 6;"
                    + " -fx-background-color: " + TipoTrabajo.GLASS.colorFondo() + "; -fx-text-fill: " + TipoTrabajo.GLASS.colorTexto() + ";");
            caja.getChildren().add(pillGlass);
        }
        return caja;
    }
```

En el listener `tgTipo.selectedToggleProperty().addListener(...)`, tras `if (!pulido) tipoActual[0] = (n == tbGlass) ? TipoTrabajo.GLASS : TipoTrabajo.REPARACION;` añadir:

```java
            // Etiqueta "glass" junto a los habilitados, solo en la cola Glass (los checkboxes se construyen una vez).
            boolean colaGlass = !pulido && tipoActual[0] == TipoTrabajo.GLASS;
            for (int i = 0; i < tecnicosModal.size(); i++)
                checkboxes.get(i).setGraphic(etiquetaConCargaNodo(tecnicosModal.get(i), false,
                        colaGlass && tecnicosModal.get(i).isEsGlass()));
```

- [ ] **Step 4: Compilar y pasar la suite**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-cliente/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD|ERROR.*java" | tail -3
```

Expected: `Tests run: 207, Failures: 0` y `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(cliente): pila con tecnicos y pastilla 'auto', contadores en los toggles de cola y etiqueta 'glass' en la cola Glass"
```

---

### Task 8: Cliente — diálogo "Técnicos de glass", botón, solo lectura, CHANGELOG, docs y smoke

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/PendientesSuperTecnicoView.fxml:14-19` (fila del título)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (método `@FXML` nuevo junto a `abrirCargaTecnicos`, `:895`)
- Modify: `CHANGELOG.md` (`[Unreleased]`)
- Modify: `docs/superpowers/specs/2026-09-05-glass-prediccion-design.md` (§3.3, una frase)

**Interfaces:**
- Consumes: `tecnicoDAO.getAllActivos()`, `TecnicoDAO.setGlass(int, boolean)` y `Tecnico.isEsGlass()` (Task 4); campo `soloLectura` y `mostrarError(Exception)` del controlador.
- Produces: `@FXML private void abrirTecnicosGlass()`.

- [ ] **Step 1: Botón en la cabecera**

En `PendientesSuperTecnicoView.fxml`, antes de `<Button fx:id="btnCargaTecnicos" …/>` añadir:

```xml
        <Button text="Técnicos de glass" styleClass="btn-secondary" onAction="#abrirTecnicosGlass"/>
```

- [ ] **Step 2: Diálogo**

En el controlador, antes de `abrirCargaTecnicos` (`:890`), añadir:

```java
    /** Diálogo "Técnicos de glass": quién entra en la glass automática del modal de asignación (spec
     *  2026-09-05-glass-prediccion, §3.3). SuperTécnico edita (solo se mandan los cambios); Admin (soloLectura)
     *  solo mira. El modal de asignación lee los técnicos al abrirse, así que no hay nada que recargar aquí. */
    @FXML
    private void abrirTecnicosGlass() {
        List<Tecnico> lista;
        try { lista = tecnicoDAO.getAllActivos(); }
        catch (SQLException e) { mostrarError(e); return; }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Técnicos de glass");
        dialog.setHeaderText("A quién se le asigna la glass automáticamente");
        VBox caja = new VBox(6);
        Map<Integer, CheckBox> checks = new java.util.LinkedHashMap<>();
        Map<Integer, Boolean> estadoInicial = new HashMap<>();
        for (Tecnico t : lista) {
            CheckBox cb = new CheckBox(t.getNombre());
            cb.setSelected(t.isEsGlass());
            cb.setDisable(soloLectura);
            checks.put(t.getIdTec(), cb);
            estadoInicial.put(t.getIdTec(), t.isEsGlass());
            caja.getChildren().add(cb);
        }
        Label aviso = new Label("Al marcar «Lleva glass» en una reparación, la glass va al técnico marcado aquí\n"
                + "con menos carga de glass hoy. Si no hay ninguno, la glass queda pendiente para asignarla a mano.");
        aviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A8A9A;");
        caja.getChildren().add(aviso);
        dialog.getDialogPane().setContent(caja);

        if (soloLectura) {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
            return;
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                for (Map.Entry<Integer, CheckBox> e : checks.entrySet()) {
                    boolean marcado = e.getValue().isSelected();
                    if (marcado == estadoInicial.get(e.getKey())) continue;   // solo cambios
                    tecnicoDAO.setGlass(e.getKey(), marcado);
                }
            } catch (SQLException ex) { ev.consume(); mostrarError(ex); }   // p. ej. 404 con servidor anterior: el diálogo no cierra
        });
        dialog.showAndWait();
    }
```

(`Dialog`, `ButtonType`, `CheckBox`, `Button`, `Label` vienen de `javafx.scene.control.*`; `Map`, `HashMap`, `List`, `SQLException` ya están importados.)

- [ ] **Step 3: CHANGELOG**

En `CHANGELOG.md`, sección `## [Unreleased]`:

Bajo `### Added`, añadir al final de la lista:

```markdown
- **Glass automática al asignar la reparación**: en el modal de asignación, la reparación tiene una casilla **"Lleva glass"** (como "Reparación de chasis"). Al pulsar Asignar nace en la cola Glass una entrada del mismo IMEI, con su modelo y su cliente, asignada al técnico habilitado para glass con **menos carga** (Pedidos si el teléfono tiene cliente, Total si es stock; empate por nombre). Nace verde con la pastilla **"auto"**; si no hay técnico disponible nace pendiente (roja) para asignarla a mano. Desmarcar la casilla o quitar la reparación retira la glass automática (no las editadas a mano).
- **Técnicos de glass** (Asignaciones, junto a "Carga técnicos"): quién entra en la glass automática. Solo el SuperTécnico edita; el Admin lo ve. Acciones `HABILITAR_GLASS` / `DESHABILITAR_GLASS` en el log. Requiere servidor 0.16.2 (columna `ES_GLASS`); con servidor anterior nadie está habilitado y la glass nace pendiente.
```

Bajo `### Changed`, añadir al final:

```markdown
- En el modal de asignación, los botones Reparación / Glass / Pulido muestran cuántas entradas tiene cada cola (en rojo si alguna está pendiente), las filas verdes de la pila muestran sus técnicos, y en la cola Glass la lista de técnicos marca con **"glass"** a los habilitados.
```

- [ ] **Step 4: Ajuste de la spec (§3.3)**

En la spec, en §3.3, sustituir la frase `Tras aceptar, recarga los técnicos de la vista (`tecnicos`) para que el modal de asignación vea el flag nuevo.` por:

```markdown
El modal de asignación carga los técnicos al abrirse (`getAllActivos`), así que ve el flag nuevo sin recargar la vista.
```

Y en §7 (lista de JUnit), sustituir `jornada 0 (sábado) reparte igual que un martes;` por:

```markdown
(la elección no recibe el día: compara fracciones sin escalar, así que el sábado no es un caso aparte; el smoke 16 lo verifica);
```

- [ ] **Step 5: Suite completa y árbol limpio**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/ProgramaReparaciones
mvn -o -f gestion-reparaciones-cliente/pom.xml test 2>&1 | grep -E "Tests run:.*Fail|BUILD" | tail -2
git status --short | grep -v "^??"
```

Expected: `Tests run: 207, Failures: 0`, `BUILD SUCCESS`; en status solo los ficheros de esta tarea y `M gestion-reparaciones-servidor`.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/PendientesSuperTecnicoView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java \
        CHANGELOG.md docs/superpowers/specs/2026-09-05-glass-prediccion-design.md
git commit -m "feat(cliente): dialogo 'Tecnicos de glass' en Asignaciones (SuperTecnico edita, Admin ve) + CHANGELOG"
git log --oneline hotfix/0.16.2..feature/glass-prediccion     # 5 commits (Tasks 4-8)
```

- [ ] **Step 7: Smoke del usuario (preproducción, servidor de la Task 3 desplegado). Se ejecuta con el cliente desde VS Code en `feature/glass-prediccion` (`mvn -o -f gestion-reparaciones-cliente/pom.xml javafx:run` o el ▶ de VS Code). 16 puntos de la spec §7:**

1. Diálogo "Técnicos de glass": marcar dos, Aceptar, reabrir → persisten; `SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico` coincide; vista Log muestra `HABILITAR_GLASS` con el nombre. **Admin**: ve el botón y el diálogo, casillas deshabilitadas, solo Cerrar.
2. Reparación con "Lleva glass" → Asignar → en Glass hay una verde "auto" con el mismo modelo y cliente, sin comentario, al técnico de menos carga; la fila muestra su nombre; pastilla "1" en el botón Glass.
3. Seis IMEIs seguidos con glass → se reparten entre los habilitados (no van todos al mismo); el orden coincide con la carga.
4. IMEI con cliente vs sin cliente → el elegido cambia según Pedidos/Total (comprobar contra "Carga técnicos" con cada toggle). Ojo: el % de pantalla va redondeado a entero y en fin de semana vale 0 para todos; dos técnicos con el mismo % no tienen por qué empatar para la predicción.
5. Sin habilitados (desmarcar todos) → glass roja, contador rojo, Guardar bloqueado; asignarla a mano desbloquea; también quitarla con ✕.
6. IMEI con glass ya abierta en BD → casilla deshabilitada con "ya tiene glass: X"; no nace nada.
7. IMEI ya escaneado en Glass (roja) → marcar la casilla en Reparación y Asignar → no nace segunda entrada, la roja sigue intacta.
8. Editar la glass auto en Glass ("Guardar cambios") → pierde "auto"; luego desmarcar la casilla en la reparación → la glass se queda; ✕ en la reparación → la glass se queda.
9. Glass auto sin tocar → desmarcar la casilla (editar la reparación verde) → desaparece; otra vez, con ✕ en la reparación → desaparece.
10. Cambiar el cliente en la reparación con glass auto ya nacida → la glass muestra el nuevo cliente y sigue "auto".
11. Modelo elegido a mano (lookup fallido) + "Lleva glass" → la glass nace con ese modelo, sin "Buscando…".
12. Etiqueta "glass" visible en la lista de técnicos solo en la cola Glass, junto a los habilitados; puntos de carga intactos.
13. Guardar → dos asignaciones en la tabla (A y AG) con sus técnicos; regla de duplicado intacta (repetir el mismo lote → conflictos); sin regresión en pegajosos por cola, pulido ni modelo vivo.
14. Cerrar el modal con glass auto sin guardar → nada en BD (salvo el modelo manual del bloque 1).
15. Pegajoso de Glass: tras nacer una auto para javi, escanear un IMEI en Glass a mano → no propone a javi por la auto (solo el último asignado a mano en esa cola).
16. Sábado (o `JORNADA_HORAS` a 0 en un test rápido): reparte entre habilitados, no siempre al primero.
17. (review final) Quitar a mano con ✕ la glass auto en la cola Glass y después "Guardar cambios" en la reparación con la casilla aún marcada → la glass vuelve a nacer (la casilla manda). Esperado; verlo una vez.
18. (review final) Con 4+ verdes en una cola, la lista verde (filas a dos líneas) hace scroll antes; si queda corta, subir `scrollVerde.setMaxHeight(220)` a ~290.

- [ ] **Step 8: Con el smoke OK, pedir al usuario el OK para `merge --no-ff` de `feature/glass-prediccion` a `hotfix/0.16.2`** (sin push; la release 0.16.2 hará después el bump del gitlink al `main` del servidor de la Task 3 y el tag). Anotar el estado en `.superpowers/sdd/progress.md`.
