# Separar Glass — Plan 1: Backend (servidor)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar soporte en el servidor a Glass como tercer tipo de trabajo (prefijos `AG`/`G`), reutilizando el flujo de reparación, con separación correcta de las asignaciones, revisión logística unificada, validación de duplicados por categoría, incidencias de glass, estadísticas y logs propios.

**Architecture:** Glass vive en la tabla `Reparacion` con prefijos de ID nuevos: `AG` (asignación pendiente) y `G` (terminado). No hay cambio de esquema. La lectura y el alta tienen métodos/endpoint propios (`GlassController` + queries glass en `ReparacionDAO`); completar/editar/borrar **reutilizan** los endpoints de reparación (operan por `ID_REP`), derivando el prefijo del resultado (`AG`→`G`) y la acción de log por el prefijo del ID.

**Tech Stack:** MariaDB, Spring Boot (JdbcTemplate), Maven, Java 17+.

## Global Constraints

- **Sin cambios de esquema** (no `ALTER TABLE`, no tablas/columnas nuevas): solo prefijos nuevos en `ID_REP` (`VARCHAR(30)`).
- **Prefijos:** `AG` = asignación de glass pendiente; `G` = glass terminado. `A` (no `AP`, no `AG`) = reparación; `AP`/`P` = pulido.
- **Prefijo del resultado al completar:** `A`→`R`, `AG`→`G` (derivado del prefijo de la asignación).
- **Revisión logística:** crear cualquier asignación (`A`/`AG`/`AP`) hace `UPDATE Telefono SET REVISION_LOGISTICA = 0`; `TIENE_ASIGNACIONES` cuenta los 3 tipos.
- **Validación de duplicados:** por `(IMEI, técnico, categoría)`.
- **Estadísticas:** cuentan `R` + `G` (pulido fuera).
- **Logs de glass:** acciones propias (`CREAR_ASIGNACION_GLASS`, `COMPLETAR_GLASS`, `EDITAR_GLASS`, `ELIMINAR_GLASS`, `ELIMINAR_ASIGNACION_GLASS`, `GUARDAR_FILA_INDIVIDUAL_GLASS`, `MARCAR_INCIDENCIA_GLASS`), enriquecidas con IMEI.
- Servidor y cliente se despliegan juntos; pruebas en preproducción (único entorno).
- El servidor **no tiene test de contexto Spring**: tras cambios de wiring, validar el arranque manualmente.
- No añadir librerías nuevas.

Todos los cambios de este plan están en `gestion-reparaciones-servidor/`. Atajo de ruta usado abajo: `…/servidor/` = `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/`.

---

## Task 1: Lectura de glass en ReparacionDAO

**Files:**
- Modify: `…/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Produces: `List<ReparacionResumen> getAsignacionesGlass(Integer idTecFilter)`
- Produces: `List<ReparacionResumen> getHistorialGlass(Integer idTecFilter)`
- Produces: `List<ReparacionResumen> getAsignacionesGlassPorImei(String imei)`
- Produces: `List<ReparacionResumen> getHistorialGlassPorImei(String imei)`

- [ ] **Step 1: Añadir la constante `GLASS_ASIGNACION_SELECT`**

En `ReparacionDAO.java`, junto a `ASIGNACION_SELECT`, crear una constante que es **copia literal del cuerpo de `ASIGNACION_SELECT`** (todas las columnas, subqueries de solicitud y JOINs idénticos: glass mantiene el join a `Reparacion_componente` para las solicitudes de pieza, igual que reparación) y **solo** cambia la cláusula `WHERE` final por:
```java
        " WHERE r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL";
```
(Escribirla a mano, no derivarla con `.replace(...)` sobre `ASIGNACION_SELECT`, para no acoplarla al cambio de `WHERE` de la Task 2.)

- [ ] **Step 2: Añadir la constante `GLASS_HISTORIAL_SELECT`**

Copia de `HISTORIAL_SELECT` con el `WHERE` cambiado. **Importante:** la subquery `TIENE_ASIGNACIONES` dentro de `HISTORIAL_SELECT` se ajusta en la Task 2; aquí copia el cuerpo y deja la subquery igual que quede tras la Task 2.
```java
    private static final String GLASS_HISTORIAL_SELECT =
        /* … copiar el cuerpo de HISTORIAL_SELECT (columnas, JOINs, subquery) … */
        " WHERE r.ID_REP LIKE 'G%'";
```

- [ ] **Step 3: Añadir los métodos de lectura**

Junto a `getAsignacionesPulido`/`getHistorialPulido`, añadir:
```java
    public List<ReparacionResumen> getAsignacionesGlass(Integer idTecFilter) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
                " tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, ta.NOMBRE, cli.NOMBRE ORDER BY r.FECHA_ASIG ASC";
        if (idTecFilter != null)
            return jdbc.query(GLASS_ASIGNACION_SELECT + " AND r.ID_TEC = ?" + groupBy, RESUMEN_MAPPER, idTecFilter);
        return jdbc.query(GLASS_ASIGNACION_SELECT + groupBy, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getHistorialGlass(Integer idTecFilter) {
        if (idTecFilter != null)
            return jdbc.query(GLASS_HISTORIAL_SELECT + " AND r.ID_TEC = ?" + ORDER_HISTORIAL, RESUMEN_MAPPER, idTecFilter);
        return jdbc.query(GLASS_HISTORIAL_SELECT + ORDER_HISTORIAL, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getAsignacionesGlassPorImei(String imei) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
                " tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, ta.NOMBRE, cli.NOMBRE ORDER BY r.FECHA_ASIG ASC";
        return jdbc.query(GLASS_ASIGNACION_SELECT + " AND r.IMEI = ?" + groupBy, RESUMEN_MAPPER, imei);
    }

    public List<ReparacionResumen> getHistorialGlassPorImei(String imei) {
        return jdbc.query(GLASS_HISTORIAL_SELECT + " AND r.IMEI = ?" + ORDER_HISTORIAL, RESUMEN_MAPPER, imei);
    }
```
(El `groupBy` se copia del de `getAsignaciones`; la columna agregada `ES_SOLICITUD = COUNT(rc.ID_RC)` exige el GROUP BY.)

- [ ] **Step 4: Compilar**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(glass): lectura de asignaciones e historial de glass (AG/G) en ReparacionDAO"
```

---

## Task 2: Separar AG de reparación + revisión logística + validación por categoría

**Files:**
- Modify: `…/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Modifies signature: `boolean existeAsignacionParaTecnico(String imei, int idTec, String categoria)` (antes 2 parámetros; nuevo `categoria` ∈ `{"R","G","P"}` que mapea a prefijos `A`/`AG`/`AP`).
- Produces (comportamiento): `ASIGNACION_SELECT` excluye `AG`; `TIENE_ASIGNACIONES` cuenta `A`+`AG`+`AP`.

- [ ] **Step 1: Excluir `AG` de `ASIGNACION_SELECT`**

En `ASIGNACION_SELECT`, en la cláusula `WHERE` final, cambiar:
```java
" WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL";
```
por:
```java
" WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.ID_REP NOT LIKE 'AG%' AND r.FECHA_FIN IS NULL";
```

- [ ] **Step 2: `TIENE_ASIGNACIONES` cuenta los 3 tipos**

En la subquery `TIENE_ASIGNACIONES` dentro de `HISTORIAL_SELECT`, cambiar:
```java
" (SELECT COUNT(*) FROM Reparacion r2" +
"  WHERE r2.IMEI = r.IMEI AND r2.ID_REP LIKE 'A%'" +
"  AND r2.ID_REP NOT LIKE 'AP%' AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES," +
```
por (cualquier asignación abierta bloquea la revisión logística):
```java
" (SELECT COUNT(*) FROM Reparacion r2" +
"  WHERE r2.IMEI = r.IMEI AND r2.ID_REP LIKE 'A%'" +
"  AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES," +
```

- [ ] **Step 3: `existeAsignacionParaTecnico` por categoría**

Reemplazar el método:
```java
    public boolean existeAsignacionParaTecnico(String imei, int idTec, String categoria) {
        // categoria: "R" -> reparación (A, no AP/AG), "G" -> glass (AG), "P" -> pulido (AP)
        String filtro = switch (categoria) {
            case "G" -> " AND ID_REP LIKE 'AG%'";
            case "P" -> " AND ID_REP LIKE 'AP%'";
            default  -> " AND ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND ID_REP NOT LIKE 'AG%'";
        };
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_TEC = ?" +
                filtro + " AND FECHA_FIN IS NULL",
                Integer.class, imei, idTec);
        return count != null && count > 0;
    }
```

- [ ] **Step 4: Compilar (fallará en `ReparacionController`)**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: FAIL — `existeAsignacionParaTecnico(String,int)` ya no existe (la llamada del controller se arregla en la Task 5). Es esperado; se resuelve al añadir el parámetro `tipo` en el endpoint en la Task 5. Si prefieres compilar verde aquí, deja temporalmente un overload de 2 args que delegue con `"R"`; se elimina en la Task 5.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(glass): separar AG de reparacion, logistica unificada y dedup por categoria"
```

---

## Task 3: Alta de asignación de glass + reset de logística unificado

**Files:**
- Modify: `…/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Produces: `String insertarAsignacionGlass(String imei, int idTec, String comentario, boolean urgente, Integer idTecAsigna)` (devuelve el `AG…`).

- [ ] **Step 1: `insertarAsignacionGlass`**

Copia de `insertarAsignacion` con prefijo `AG`:
```java
    @Transactional
    public String insertarAsignacionGlass(String imei, int idTec, String comentario, boolean urgente, Integer idTecAsigna) {
        ensureTelefono(imei);
        String idRep = nextId("AG");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA, URGENTE) VALUES (?,?,?,NOW(),?,?,?)",
                idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna, urgente);
        jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
        return idRep;
    }
```

- [ ] **Step 2: Reset de logística en el alta de pulido**

En `insertarAsignacionPulido`, tras el `INSERT`, añadir (hoy falta; corrige la incoherencia):
```java
        jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
```

- [ ] **Step 3: Compilar**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: BUILD SUCCESS (o el mismo fallo pendiente de la Task 2 hasta la Task 5).

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(glass): alta de asignacion de glass + reset de logistica tambien en pulido"
```

---

## Task 4: Completar AG → G + incidencias de glass (prefijo del resultado)

**Files:**
- Modify: `…/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Produces (comportamiento): completar una asignación `AG` genera filas con prefijo `G`; una incidencia sobre una `G` se reasigna como `AG`.

- [ ] **Step 1: Helper de prefijo de resultado**

En `ReparacionDAO`, junto a `nextId`, añadir:
```java
    /** Prefijo de la fila terminada según el prefijo de la asignación: AG->G, resto->R. */
    private static String resultPrefix(String idAsignacion) {
        return (idAsignacion != null && idAsignacion.startsWith("AG")) ? "G" : "R";
    }
```

- [ ] **Step 2: Usar el prefijo en `insertarCompleta`**

En `insertarCompleta`, en el bucle, cambiar la línea:
```java
                String idRep = nextId("R");
```
por:
```java
                String idRep = nextId(resultPrefix(idAsignacion));
```
(Con `idAsignacion == null`, `resultPrefix` devuelve `"R"` → no afecta a inserciones directas de reparación.)

- [ ] **Step 3: Usar el prefijo en `guardarFilaIndividual`**

Misma sustitución en `guardarFilaIndividual`:
```java
                String idRep = nextId(resultPrefix(idAsignacion));
```

- [ ] **Step 4: Incidencia de glass se reasigna como `AG`**

En `marcarIncidenciaYAsignar`, cambiar:
```java
        String idAsig = nextId("A");
```
por (si la reparación origen es glass, la reasignación es glass):
```java
        String idAsig = nextId(idRep.startsWith("G") ? "AG" : "A");
```

- [ ] **Step 5: Rastro de incidencia/reincidencia por tipo**

En `getIncidenciaActivaPorImei`, hoy filtra `r.ID_REP LIKE 'R%'`. Para que las incidencias de glass sean independientes, parametrizar por categoría:
```java
    public String getIncidenciaActivaPorImei(String imei, String categoria) {
        String like = "G".equals(categoria) ? "G%" : "R%";
        List<String> result = jdbc.query(
                "SELECT r.ID_REP FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ?" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0 LIMIT 1",
                (rs, row) -> rs.getString(1), imei, like);
        return result.isEmpty() ? null : result.get(0);
    }
```
En `eliminar`, la subquery de revertir incidencia usa `ID_REP LIKE 'R%'` (cuenta otras reparaciones que resuelvan la incidencia) y `ID_REP LIKE 'A%'` (reabrir la asignación). Generalizar para que glass funcione igual: la cuenta de "restantes que resuelven" debe mirar el mismo prefijo que `idRep` (`G` o `R`), y el reabrir-asignación mira `LIKE 'A%'` (cubre `A` y `AG`, válido). Ajustar la primera:
```java
            String mismoPrefijo = idRep.startsWith("G") ? "G%" : "R%";
            Integer restantes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE ? AND ID_REP != ?",
                    Integer.class, idRepOrig, mismoPrefijo, idRep);
```
En `borrarIncidenciaPorImei`, hoy `r.ID_REP LIKE 'R%'` (limpia incidencias de reparación) y borra asignaciones `LIKE 'A%'`. Parametrizar por categoría para no cruzar tipos:
```java
    public void borrarIncidenciaPorImei(String imei, String categoria) {
        String likeRep = "G".equals(categoria) ? "G%" : "R%";
        String likeAsig = "G".equals(categoria) ? "AG%" : "A%";
        jdbc.update(
                "UPDATE Reparacion_componente rc JOIN Reparacion r ON rc.ID_REP = r.ID_REP" +
                " SET rc.ES_INCIDENCIA = 0, rc.INCIDENCIA = NULL" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ?" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0", imei, likeRep);
        List<String> asigs = jdbc.query(
                "SELECT ID_REP FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE ?" +
                (("G".equals(categoria)) ? "" : " AND ID_REP NOT LIKE 'AP%' AND ID_REP NOT LIKE 'AG%'") +
                " AND FECHA_FIN IS NULL",
                (rs, row) -> rs.getString(1), imei, likeAsig);
        for (String idAsig : asigs) { /* … resto idéntico al actual … */ }
    }
```
(El `likeAsig` para reparación = `A%` excluyendo `AP`/`AG`; para glass = `AG%`.)

- [ ] **Step 6: Compilar**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: FAIL en `ReparacionController` por las firmas nuevas de `getIncidenciaActivaPorImei`/`borrarIncidenciaPorImei` (se arreglan en la Task 5).

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(glass): completar AG->G e incidencias de glass por tipo"
```

---

## Task 5: GlassController + endpoints reutilizados + logs de glass

**Files:**
- Create: `…/servidor/controller/GlassController.java`
- Modify: `…/servidor/controller/ReparacionController.java`

**Interfaces:**
- Produces endpoints: `GET /api/glass/asignaciones`, `GET /api/glass/historial`, `POST /api/glass/asignaciones`.
- Consumes: `ReparacionDAO.getAsignacionesGlass/getHistorialGlass/insertarAsignacionGlass` (Tasks 1, 3).

- [ ] **Step 1: Crear `GlassController`** (espejo de `PulidoController`, pero con `URGENTE` y asignador como reparación)

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/glass")
public class GlassController {

    private final ReparacionDAO dao;
    private final LogDAO logDao;
    private final TelefonoDAO telefonoDAO;
    private final ImeiLookupService imeiLookupService;

    public GlassController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ImeiLookupService imeiLookupService) {
        this.dao = dao; this.logDao = logDao; this.telefonoDAO = telefonoDAO; this.imeiLookupService = imeiLookupService;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(@RequestParam(required = false) Integer tecnico) {
        return dao.getAsignacionesGlass(tecnico);
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(@RequestParam(required = false) Integer tecnico) {
        return dao.getHistorialGlass(tecnico);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertarAsignacion(@RequestBody GlassAsignacionRequest req,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String modeloExistente = telefonoDAO.getModelo(req.imei());
        if (modeloExistente == null || modeloExistente.isBlank()) {
            String modelo = imeiLookupService.lookupModeloInterno(req.imei());
            if (modelo != null) telefonoDAO.insertar(req.imei(), modelo);
        }
        String idRep = dao.insertarAsignacionGlass(req.imei(), req.idTec(), req.comentario(), req.urgente(), principal.getIdTec());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo + ", TECNICO: " + tecnico
                + (req.urgente() ? ", URGENTE: true" : ""));
        return Map.of("value", idRep);
    }

    private record GlassAsignacionRequest(String imei, int idTec, String comentario, boolean urgente) {}
}
```

- [ ] **Step 2: Endpoints de completar/editar/borrar reutilizados — derivar acción de log por prefijo**

En `ReparacionController`, añadir un helper privado:
```java
    private static boolean esGlass(String idRep) { return idRep != null && idRep.startsWith("G"); }
    private static boolean esGlassAsig(String idAsig) { return idAsig != null && idAsig.startsWith("AG"); }
```
Y branquear la acción de log:
- En `insertarCompleta`: `String accion = esGlassAsig(req.idAsignacion()) ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION";` y usar `accion` en `logDao.insertar`.
- En `completar`: `String accion = esGlassAsig(idRep) ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION";`.
- En `guardarFilaIndividual`: `String accion = esGlassAsig(idAsignacion) ? "GUARDAR_FILA_INDIVIDUAL_GLASS" : "GUARDAR_FILA_INDIVIDUAL";`.
- En `editarReparacion`: `String accion = esGlass(idRep) ? "EDITAR_GLASS" : "EDITAR_REPARACION";`.
- En `eliminar`: `String accion = esGlass(idRep) ? "ELIMINAR_GLASS" : "ELIMINAR_REPARACION";`.
- En `eliminarAsignacion`: `String accion = esGlassAsig(idAsig) ? "ELIMINAR_ASIGNACION_GLASS" : "ELIMINAR_ASIGNACION";`.
- En `marcarIncidenciaYAsignar`: `String accion = esGlass(idRep) ? "MARCAR_INCIDENCIA_GLASS" : "MARCAR_INCIDENCIA";`.

- [ ] **Step 3: Ajustar las llamadas a las firmas nuevas del DAO (Tasks 2 y 4)**

- `existeAsignacionParaTecnico`: el endpoint `GET /imei/{imei}/tiene-asignacion` añade `@RequestParam(defaultValue="R") String tipo` y llama `dao.existeAsignacionParaTecnico(imei, tecnico, tipo)`.
- `getIncidenciaActivaPorImei`: el endpoint añade `@RequestParam(defaultValue="R") String tipo` y llama `dao.getIncidenciaActivaPorImei(imei, tipo)`.
- `borrarIncidenciaPorImei`: el endpoint `DELETE /imei/{imei}/incidencia-activa` añade `@RequestParam(defaultValue="R") String tipo` y llama `dao.borrarIncidenciaPorImei(imei, tipo)`.

- [ ] **Step 4: Compilar (verde)**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/GlassController.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat(glass): GlassController + logs propios de glass por prefijo de ID"
```

---

## Task 6: Estadísticas cuentan R + G

**Files:**
- Modify: `…/servidor/dao/ReparacionDAO.java`

- [ ] **Step 1: Incluir glass en `getEstadisticasPorTecnico`**

En `getEstadisticasPorTecnico`, cambiar:
```java
                " WHERE r.ID_REP LIKE 'R%' AND r.FECHA_FIN IS NOT NULL" +
```
por:
```java
                " WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL" +
```

- [ ] **Step 2: Compilar**

Run: `cd gestion-reparaciones-servidor && mvn -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(glass): estadisticas cuentan reparaciones + glass (R + G)"
```

---

## Task 7: Validación de arranque + smoke test de la API

**Files:** ninguno (verificación).

- [ ] **Step 1: Compilar y empaquetar**

Run: `cd gestion-reparaciones-servidor && mvn -q -o package -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Ejecutar la suite existente**

Run: `cd gestion-reparaciones-servidor && mvn -q -o test`
Expected: PASS (no debe romperse `UrgenteAutomaticoJobTest` ni `ImeiLookupServiceTest`).

- [ ] **Step 3: Arrancar el servidor (no hay test de contexto Spring)**

Arrancar la app contra la BD de preproducción y confirmar que el contexto levanta sin error de wiring (el bean nuevo `GlassController` se registra). Verificar en logs de arranque que no hay excepción.

- [ ] **Step 4: Smoke test manual de la API** (con un token SUPERTECNICO)

- `POST /api/glass/asignaciones` con `{imei, idTec, comentario, urgente:false}` → 201, devuelve `value: "AG…"`.
- `GET /api/glass/asignaciones` → incluye la nueva.
- `GET /api/reparaciones/asignaciones` → **NO** incluye la `AG` (verifica la separación).
- Completar la `AG` vía `POST /api/reparaciones/completa` con `idAsignacion: "AG…"` y una fila de pieza glass → se crea `G…`; `GET /api/glass/historial` la muestra y `GET /api/reparaciones/historial` no.
- Revisar `Log_Actividad`: aparecen `CREAR_ASIGNACION_GLASS` y `COMPLETAR_GLASS` con IMEI en el detalle.
- Revisar revisión logística: tras crear la `AG`, el teléfono tiene `REVISION_LOGISTICA = 0`.

- [ ] **Step 5: Commit (si hubo ajustes)**

```bash
git commit -am "test(glass): validacion de arranque y smoke de la API de glass" --allow-empty
```

---

## Self-review (cobertura del spec, sección Backend)

- [x] Prefijos `AG/G`, sin esquema → Tasks 1, 3.
- [x] Tabla query-por-query (`ASIGNACION_SELECT`, `TIENE_ASIGNACIONES`, `existeAsignacionParaTecnico`, `borrarIncidenciaPorImei`, urgentes/`getTecnicosConAsignacionActiva` sin cambios) → Tasks 2, 4.
- [x] Resultado `AG→G` → Task 4.
- [x] Reset logística en las 3 altas + bloqueo → Tasks 2, 3.
- [x] Validación por categoría → Tasks 2, 5.
- [x] Incidencias de glass por tipo → Task 4.
- [x] Estadísticas `R+G` → Task 6.
- [x] Logs propios de glass → Task 5.
- [x] Arranque Spring → Task 7.

**Pendiente para Plan 2/3 (cliente):** `GlassDAO`, modal filtrado, Asignaciones unificada + modal selector, Pendientes 3 pestañas, Historial 3-way, apartado Agrupado, filtro `TIPOS_ACCION` del visor de logs.
