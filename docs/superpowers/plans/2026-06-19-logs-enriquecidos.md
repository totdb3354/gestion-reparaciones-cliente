# Logs de Auditoría Enriquecidos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enriquecer los logs de auditoría con valores antes/después, nombres legibles, motivo obligatorio en borrados, filtros server-side y detalle completo por doble clic.

**Architecture:** Servidor añade columna MOTIVO a Log_Actividad, nuevo overload en LogDAO con 4 args, filtrado dinámico con WHERE opcional. Cliente adapta diálogos de borrado para pedir motivo, filtra logs con llamadas server-side, y muestra popup de detalle al hacer doble clic.

**Tech Stack:** MariaDB, Spring Boot (JdbcTemplate), JavaFX 17, Java HttpClient, Gson.

**Importante:** Este plan debe ejecutarse DESPUÉS del plan de prioridad "Muy urgente" (`2026-06-19-prioridad-muy-urgente.md`) porque ese plan migra `URGENTE → PRIORIDAD` en los modelos. Si se ejecutan en orden inverso habrá conflictos en `ReparacionResumen` y `ReparacionDAO`.

## Global Constraints

- No añadir librerías externas nuevas — solo las ya existentes en el proyecto
- No cambiar contratos REST existentes excepto los explicitamente modificados en este plan (`eliminarAsignacion` y `eliminar` aceptan body opcional; `getAll` de logs acepta query params)
- Los registros históricos de logs no se modifican — MOTIVO queda NULL para ellos
- La tabla de logs no se limpia — el formato antiguo convive con el nuevo (sin breaking changes en la BD)
- Servidor y cliente deben desplegarse juntos

---

## Task 1: SQL migration + Server LogActividad model + Server LogDAO

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/resources/db/migracion-logs.sql`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/LogActividad.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/LogDAO.java`

**Interfaces:**
- Produces: `LogDAO.insertar(int, String, String, String)` — usado en Tasks 7, 8, 9
- Produces: `LogDAO.getFiltered(String, String, LocalDate, LocalDate)` — usado en Task 2
- Produces: `LogActividad.getMotivo()` — usado en Task 10 (cliente)

- [ ] **Step 1: Crear script SQL de migración**

Crear `gestion-reparaciones-servidor/src/main/resources/db/migracion-logs.sql`:
```sql
ALTER TABLE Log_Actividad ADD COLUMN MOTIVO TEXT NULL;
```

- [ ] **Step 2: Añadir campo motivo al modelo LogActividad del servidor**

Modificar `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/LogActividad.java`:

```java
package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class LogActividad {
    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;
    private String motivo;

    public LogActividad() {}

    public LogActividad(int idLog, LocalDateTime fecha, String nombreUsuario,
                        String accion, String detalle, String motivo) {
        this.idLog         = idLog;
        this.fecha         = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion        = accion;
        this.detalle       = detalle;
        this.motivo        = motivo;
    }

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
    public String        getMotivo()        { return motivo; }
}
```

- [ ] **Step 3: Ejecutar el script SQL en la BD de desarrollo**

```bash
mysql -u root -p gestion_reparaciones < gestion-reparaciones-servidor/src/main/resources/db/migracion-logs.sql
```

Verificar que la columna existe:
```sql
SHOW COLUMNS FROM Log_Actividad LIKE 'MOTIVO';
```
Expected: `MOTIVO | text | YES | | NULL |`

- [ ] **Step 4: Actualizar LogDAO — nuevo overload con motivo y getFiltered**

Reemplazar `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/LogDAO.java` completo:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.LogActividad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LogDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<LogActividad> MAPPER = (rs, row) -> new LogActividad(
            rs.getInt("ID_LOG"),
            rs.getTimestamp("FECHA").toLocalDateTime(),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("ACCION"),
            rs.getString("DETALLE"),
            rs.getString("MOTIVO")
    );

    public LogDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(int idUsu, String accion, String detalle) {
        insertar(idUsu, accion, detalle, null);
    }

    public void insertar(int idUsu, String accion, String detalle, String motivo) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, ACCION, DETALLE, MOTIVO) VALUES (?, ?, ?, ?)",
                idUsu, accion, detalle, motivo);
    }

    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta) {
        StringBuilder sql = new StringBuilder(
                "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE, l.MOTIVO " +
                "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (accion != null && !accion.isBlank()) {
            sql.append(" AND l.ACCION = ?");
            params.add(accion);
        }
        if (tecnico != null && !tecnico.isBlank()) {
            sql.append(" AND u.NOMBRE_USUARIO = ?");
            params.add(tecnico);
        }
        if (desde != null) {
            sql.append(" AND DATE(l.FECHA) >= ?");
            params.add(desde.toString());
        }
        if (hasta != null) {
            sql.append(" AND DATE(l.FECHA) <= ?");
            params.add(hasta.toString());
        }
        sql.append(" ORDER BY l.FECHA DESC");
        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }
}
```

- [ ] **Step 5: Compilar el servidor**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Expected: BUILD SUCCESS sin errores.

- [ ] **Step 6: Commit**

```
git add gestion-reparaciones-servidor/src/main/resources/db/migracion-logs.sql
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/LogActividad.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/LogDAO.java
git commit -m "feat(logs): añadir columna MOTIVO y LogDAO.getFiltered con WHERE dinámico"
```

---

## Task 2: Server LogController — filtros server-side

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/LogController.java`

**Interfaces:**
- Consumes: `LogDAO.getFiltered(String, String, LocalDate, LocalDate)` de Task 1
- Produces: `GET /api/logs?accion=X&tecnico=Y&desde=2026-01-01&hasta=2026-06-19`

- [ ] **Step 1: Actualizar LogController**

Modificar el método `getAll` para aceptar 4 query params opcionales:

```java
// En LogController.java — sustituir el método getAll:
@GetMapping
@PreAuthorize("hasRole('ADMIN')")
public List<LogActividad> getAll(
        @RequestParam(required = false) String accion,
        @RequestParam(required = false) String tecnico,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    return logDao.getFiltered(accion, tecnico, desde, hasta);
}
```

Añadir import si falta:
```java
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
```

- [ ] **Step 2: Compilar y arrancar el servidor**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
mvn spring-boot:run
```

- [ ] **Step 3: Verificar manualmente con curl**

Sin filtros (devuelve todos):
```bash
curl -s -H "Authorization: Bearer <token>" http://localhost:8080/api/logs | head -c 200
```

Con filtro accion:
```bash
curl -s -H "Authorization: Bearer <token>" "http://localhost:8080/api/logs?accion=LOGIN" | head -c 200
```

Expected: en ambos casos JSON array de LogActividad con campo `motivo` (null para registros históricos).

- [ ] **Step 4: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/LogController.java
git commit -m "feat(logs): LogController acepta filtros accion/tecnico/desde/hasta server-side"
```

---

## Task 3: Eliminación de código muerto (actualizarTecnico)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`

**Interfaces:**
- El endpoint `PATCH /api/reparaciones/asignaciones/{idRep}/tecnico` desaparece
- El método `ReparacionDAO.actualizarTecnico()` del cliente desaparece

- [ ] **Step 1: Eliminar endpoint actualizarTecnico del servidor**

En `ReparacionController.java`, eliminar el método completo `actualizarTecnico` y el record `TecnicoRequest`:

Eliminar estas líneas (buscar y borrar):
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PatchMapping("/asignaciones/{idRep}/tecnico")
public void actualizarTecnico(@PathVariable String idRep, @RequestBody TecnicoRequest req,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.actualizarTecnico(idRep, req.idTec(), req.updatedAt());
    logDao.insertar(principal.getIdUsu(), "REASIGNAR_TECNICO",
            "ID_REP: " + idRep + ", ID_TEC_NUEVO: " + req.idTec());
}
```

Y eliminar el record:
```java
private record TecnicoRequest(int idTec, LocalDateTime updatedAt) {}
```

- [ ] **Step 2: Eliminar método actualizarTecnico del cliente ReparacionDAO**

En `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`, buscar y eliminar el método `actualizarTecnico` completo (buscar por nombre del método).

- [ ] **Step 3: Compilar ambos proyectos**

```bash
cd gestion-reparaciones-servidor && mvn compile -q
cd ../gestion-reparaciones-cliente && mvn compile -q
```

Expected: BUILD SUCCESS. Si hay errores de compilación en el cliente por referencias a `actualizarTecnico`, buscar con grep:
```bash
grep -r "actualizarTecnico" gestion-reparaciones-cliente/src/
```
Si aparece algún resultado, eliminar esa llamada también.

- [ ] **Step 4: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java
git commit -m "refactor: eliminar endpoint y método REASIGNAR_TECNICO (código muerto)"
```

---

## Task 4: Cliente — LogActividad, LogDAO, ApiClient.deleteWithBody, ReparacionDAO

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/LogActividad.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/LogDAO.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`

**Interfaces:**
- Produces: `LogActividad.getMotivo()` — usado en Task 10
- Produces: `LogDAO.getAll(String, String, LocalDate, LocalDate)` — usado en Task 10
- Produces: `ApiClient.deleteWithBody(String, Object)` — usado aquí y en Task 6
- Produces: `ReparacionDAO.eliminarAsignacion(String, String)` y `eliminar(String, String)` — usados en Task 6

- [ ] **Step 1: Añadir campo motivo a LogActividad del cliente**

Reemplazar `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/LogActividad.java`:

```java
package com.reparaciones.models;

import java.time.LocalDateTime;

public class LogActividad {

    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;
    private String motivo;

    public LogActividad() {}

    public LogActividad(int idLog, LocalDateTime fecha, String nombreUsuario,
                         String accion, String detalle) {
        this.idLog = idLog;
        this.fecha = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion = accion;
        this.detalle = detalle;
    }

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
    public String        getMotivo()        { return motivo; }
}
```

(Gson rellena `motivo` automáticamente desde el JSON del servidor vía el setter implícito de campo.)

- [ ] **Step 2: Actualizar client LogDAO para pasar filtros**

Reemplazar `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/LogDAO.java`:

```java
package com.reparaciones.dao;

import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class LogDAO {

    public List<LogActividad> getAll(String accion, String tecnico,
                                     LocalDate desde, LocalDate hasta) throws SQLException {
        StringBuilder path = new StringBuilder("/api/logs?_=1");
        if (accion  != null && !accion.isBlank())  path.append("&accion=").append(accion);
        if (tecnico != null && !tecnico.isBlank()) path.append("&tecnico=").append(tecnico);
        if (desde   != null) path.append("&desde=").append(desde);
        if (hasta   != null) path.append("&hasta=").append(hasta);
        return ApiClient.getList(path.toString(), LogActividad.class);
    }
}
```

- [ ] **Step 3: Añadir ApiClient.deleteWithBody**

En `ApiClient.java`, añadir este método justo después del `delete(String path)` existente (en la sección `// ── DELETE`):

```java
/**
 * DELETE con body JSON opcional. Necesario para enviar motivo en borrados.
 */
public static void deleteWithBody(String path, Object body) throws SQLException {
    HttpRequest.BodyPublisher publisher = body != null
            ? HttpRequest.BodyPublishers.ofString(GSON.toJson(body))
            : HttpRequest.BodyPublishers.noBody();
    HttpResponse<String> response = send(builder(path)
            .method("DELETE", publisher)
            .build());
    handleErrors(response);
}
```

- [ ] **Step 4: Actualizar ReparacionDAO del cliente — eliminar con motivo**

En `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`, buscar los métodos `eliminarAsignacion` y `eliminar` y reemplazarlos:

Buscar:
```java
public void eliminarAsignacion(String idAsig) throws SQLException {
    ApiClient.delete("/api/reparaciones/asignaciones/" + idAsig);
}
```
Reemplazar por:
```java
public void eliminarAsignacion(String idAsig, String motivo) throws SQLException {
    ApiClient.deleteWithBody("/api/reparaciones/asignaciones/" + idAsig,
            java.util.Map.of("motivo", motivo));
}
```

Buscar:
```java
public void eliminar(String idRep) throws SQLException {
    ApiClient.delete("/api/reparaciones/" + idRep);
}
```
Reemplazar por:
```java
public void eliminar(String idRep, String motivo) throws SQLException {
    ApiClient.deleteWithBody("/api/reparaciones/" + idRep,
            java.util.Map.of("motivo", motivo));
}
```

- [ ] **Step 5: Compilar el cliente**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS. Si hay errores en llamadas a `eliminarAsignacion()` o `eliminar()` sin motivo, se resuelven en Task 6.

- [ ] **Step 6: Commit**

```
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/LogActividad.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/LogDAO.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java
git commit -m "feat(logs): cliente LogActividad+LogDAO filtros, ApiClient.deleteWithBody, ReparacionDAO con motivo"
```

---

## Task 5: Cliente — ConfirmDialog.mostrarConMotivo

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConfirmDialog.java`

**Interfaces:**
- Produces: `ConfirmDialog.mostrarConMotivo(String, String, String, Consumer<String>)` — usado en Task 6

- [ ] **Step 1: Añadir método mostrarConMotivo a ConfirmDialog**

Añadir el siguiente método al final de `ConfirmDialog.java`, antes del cierre de la clase `}`:

```java
/**
 * Diálogo de confirmación que requiere un motivo de texto (obligatorio).
 * El botón de acción queda deshabilitado hasta que se escribe texto.
 *
 * @param titulo      texto del encabezado
 * @param descripcion texto explicativo
 * @param textoAccion etiqueta del botón de confirmación
 * @param onConfirm   callback con el motivo introducido (nunca vacío)
 */
public static void mostrarConMotivo(String titulo, String descripcion,
                                    String textoAccion, java.util.function.Consumer<String> onConfirm) {
    Stage ventana = new Stage();
    ventana.initModality(Modality.APPLICATION_MODAL);
    ventana.initStyle(StageStyle.UNDECORATED);
    ventana.setResizable(false);

    Label lblTitulo = new Label(titulo);
    lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + Colores.TEXTO_ERROR + ";");
    lblTitulo.setWrapText(true);

    Label lblX = new Label("✕");
    lblX.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
    lblX.setOnMouseClicked(e -> ventana.close());

    HBox spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox barraTop = new HBox(lblTitulo, spacer, lblX);
    barraTop.setAlignment(Pos.CENTER_LEFT);
    barraTop.setPadding(new Insets(0, 0, 8, 0));

    Label lblDesc = new Label(descripcion);
    lblDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
    lblDesc.setWrapText(true);
    lblDesc.setMaxWidth(352);

    TextArea txtMotivo = new TextArea();
    txtMotivo.setPromptText("Escribe el motivo del borrado...");
    txtMotivo.setWrapText(true);
    txtMotivo.setPrefRowCount(3);
    txtMotivo.setMaxWidth(Double.MAX_VALUE);
    txtMotivo.setStyle("-fx-font-size: 13px; -fx-background-color: white;" +
            "-fx-border-color: #C2C8D0; -fx-border-width: 1;");

    Button btnAccion = new Button(textoAccion);
    btnAccion.setMaxWidth(Double.MAX_VALUE);
    btnAccion.setDisable(true);
    btnAccion.setStyle(
            "-fx-background-color: " + Colores.ROJO_ACCION + "; -fx-text-fill: " + Colores.CREMA + ";" +
            "-fx-background-radius: 4; -fx-font-size: 12px;" +
            "-fx-padding: 10; -fx-cursor: hand;");

    txtMotivo.textProperty().addListener((obs, o, n) ->
            btnAccion.setDisable(n == null || n.isBlank()));

    btnAccion.setOnAction(e -> {
        ventana.close();
        onConfirm.accept(txtMotivo.getText().trim());
    });

    Button btnCancelar = new Button("Cancelar");
    btnCancelar.setMaxWidth(Double.MAX_VALUE);
    btnCancelar.setStyle(
            "-fx-background-color: " + Colores.CREMA + "; -fx-text-fill: " + Colores.AZUL_GRIS + ";" +
            "-fx-border-color: " + Colores.AZUL_GRIS + "; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-font-size: 12px;" +
            "-fx-padding: 10; -fx-cursor: hand;");
    btnCancelar.setOnAction(e -> ventana.close());

    VBox contenido = new VBox(10, barraTop, lblDesc, txtMotivo, btnAccion, btnCancelar);
    contenido.setPadding(new Insets(24));
    contenido.setPrefWidth(400);
    contenido.setStyle(ESTILO_CONTENEDOR);

    final double[] drag = new double[2];
    contenido.setOnMousePressed(e  -> { drag[0] = e.getSceneX(); drag[1] = e.getSceneY(); });
    contenido.setOnMouseDragged(e  -> {
        ventana.setX(e.getScreenX() - drag[0]);
        ventana.setY(e.getScreenY() - drag[1]);
    });

    Scene scene = new Scene(contenido);
    scene.setFill(Color.web(Colores.CREMA));
    ventana.setScene(scene);
    ventana.setOnCloseRequest(e -> {});
    ventana.showAndWait();
}
```

- [ ] **Step 2: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Test manual**

Arrancar la app, ir a cualquier vista con botón de borrar y verificar que el nuevo diálogo aún no aparece (se conecta en Task 6).

- [ ] **Step 4: Commit**

```
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConfirmDialog.java
git commit -m "feat(logs): ConfirmDialog.mostrarConMotivo con TextArea obligatorio"
```

---

## Task 6: Cliente — diálogos de borrado usan mostrarConMotivo

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`

**Interfaces:**
- Consumes: `ConfirmDialog.mostrarConMotivo` de Task 5
- Consumes: `ReparacionDAO.eliminarAsignacion(String, String)` y `eliminar(String, String)` de Task 4

- [ ] **Step 1: Localizar el borrado de asignaciones en PendientesSuperTecnicoController**

Buscar con grep la llamada a `ConfirmDialog.mostrar` que llama a `reparacionDAO.eliminarAsignacion`:
```bash
grep -n "eliminarAsignacion\|CONFIRMAR_BORRAR_ASIGNACION\|Eliminar asignacion" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
```

Leer las líneas identificadas para ver el contexto exacto.

- [ ] **Step 2: Sustituir ConfirmDialog.mostrar → mostrarConMotivo en PendientesSuperTecnicoController**

Buscar el bloque que tiene la forma:
```java
ConfirmDialog.mostrar("...", "...", "...", () -> {
    try {
        reparacionDAO.eliminarAsignacion(rep.getIdRep());
        ...
    } catch ...
});
```

Sustituirlo por:
```java
ConfirmDialog.mostrarConMotivo(
    "Eliminar asignación",
    "Esta acción eliminará la asignación permanentemente. Escribe el motivo.",
    "Eliminar",
    motivo -> {
        try {
            reparacionDAO.eliminarAsignacion(rep.getIdRep(), motivo);
            // (mantener el resto de la lógica post-borrado igual que antes)
        } catch (java.sql.SQLException ex) {
            com.reparaciones.utils.Alertas.mostrarError("Error al eliminar: " + ex.getMessage());
        }
    }
);
```

Nota: mantener exactamente el mismo código dentro del bloque catch y cualquier actualización de UI que existía antes.

- [ ] **Step 3: Localizar y sustituir en ReparacionControllerSuperTecnico**

```bash
grep -n "reparacionDAO.eliminar\|ConfirmDialog.mostrar" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
```

Sustituir el `ConfirmDialog.mostrar(...)` que llama a `reparacionDAO.eliminar(idRep)`:
```java
ConfirmDialog.mostrarConMotivo(
    "Eliminar reparación",
    "Esta acción eliminará la reparación del historial permanentemente. Escribe el motivo.",
    "Eliminar",
    motivo -> {
        try {
            reparacionDAO.eliminar(idRep, motivo);
            // (mantener el resto de la lógica post-borrado igual)
        } catch (java.sql.SQLException ex) {
            com.reparaciones.utils.Alertas.mostrarError("Error al eliminar: " + ex.getMessage());
        }
    }
);
```

- [ ] **Step 4: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS. Si quedan errores en otras llamadas a `eliminarAsignacion()` o `eliminar()` sin motivo, buscarlos y adaptarlos.

- [ ] **Step 5: Test manual**

Arrancar servidor + cliente, intentar borrar una asignación y una reparación. Verificar:
- El diálogo `mostrarConMotivo` aparece
- El botón Eliminar está deshabilitado hasta escribir texto
- Al confirmar, la operación se ejecuta y el log en BD incluye el motivo

- [ ] **Step 6: Commit**

```
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
git commit -m "feat(logs): borrados de asignación y reparación requieren motivo obligatorio"
```

---

## Task 7: Servidor — ReparacionController borrados enriquecidos + helper DAO methods

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

**Interfaces:**
- Consumes: `LogDAO.insertar(int, String, String, String)` de Task 1
- Produces: `ReparacionDAO.getHistorialById(String)`, `getResumenById(String)` — usados en Task 8

- [ ] **Step 1: Añadir helpers a server ReparacionDAO**

Añadir estos métodos al final de la clase `ReparacionDAO`, antes del último `}`:

```java
/** Devuelve ReparacionResumen de un registro de historial (R*) por ID. */
public java.util.Optional<ReparacionResumen> getHistorialById(String idRep) {
    List<ReparacionResumen> rows = jdbc.query(
            HISTORIAL_SELECT + " AND r.ID_REP = ?" + ORDER_HISTORIAL,
            RESUMEN_MAPPER, idRep);
    return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
}

/** Devuelve ReparacionResumen de cualquier reparación (A* o R*) por ID. */
public java.util.Optional<ReparacionResumen> getResumenById(String idRep) {
    return idRep.startsWith("A") ? getAsignacionById(idRep) : getHistorialById(idRep);
}

/** Nombre del técnico por ID — para enriquecer logs. */
public String getNombreTecnicoById(int idTec) {
    return jdbc.queryForObject(
            "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
}

/** Tipo del componente por ID — para enriquecer logs. */
public String getTipoComponenteById(int idCom) {
    return jdbc.queryForObject(
            "SELECT TIPO FROM Componente WHERE ID_COM = ?", String.class, idCom);
}

/** Modelo del teléfono por IMEI — para enriquecer logs. Devuelve "?" si no existe. */
public String getModeloByImei(String imei) {
    List<String> rows = jdbc.query(
            "SELECT COALESCE(MODELO,'?') FROM Telefono WHERE IMEI = ?",
            (rs, i) -> rs.getString(1), imei);
    return rows.isEmpty() ? "?" : rows.get(0);
}
```

Nota: `HISTORIAL_SELECT` y `ORDER_HISTORIAL` son constantes privadas ya definidas en la clase. El mapper `RESUMEN_MAPPER` también ya existe.

- [ ] **Step 2: Actualizar eliminarAsignacion en ReparacionController**

En `ReparacionController.java`, añadir `MotivoRequest` record y actualizar el endpoint `eliminarAsignacion`:

Buscar el record list al final del archivo y añadir:
```java
private record MotivoRequest(String motivo) {}
```

Reemplazar el método `eliminarAsignacion`:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@DeleteMapping("/asignaciones/{idAsig}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void eliminarAsignacion(@PathVariable String idAsig,
                               @RequestBody(required = false) MotivoRequest req,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
    ReparacionResumen rep = dao.getAsignacionById(idAsig).orElse(null);
    dao.eliminarAsignacion(idAsig);
    String detalle = rep != null
            ? "ID_REP: " + idAsig + ", IMEI: " + rep.getImei() +
              ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
              ", TECNICO: " + rep.getNombreTecnico()
            : "ID_REP: " + idAsig;
    String motivo = req != null ? req.motivo() : null;
    logDao.insertar(principal.getIdUsu(), "ELIMINAR_ASIGNACION", detalle, motivo);
}
```

- [ ] **Step 3: Actualizar eliminar (reparación) en ReparacionController**

Reemplazar el método `eliminar`:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@DeleteMapping("/{idRep}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void eliminar(@PathVariable String idRep,
                     @RequestBody(required = false) MotivoRequest req,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
    ReparacionResumen rep = dao.getResumenById(idRep).orElse(null);
    dao.eliminar(idRep);
    String detalle = rep != null
            ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
              ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
              ", TECNICO: " + rep.getNombreTecnico()
            : "ID_REP: " + idRep;
    String motivo = req != null ? req.motivo() : null;
    logDao.insertar(principal.getIdUsu(), "ELIMINAR_REPARACION", detalle, motivo);
}
```

Añadir `@RequestBody` import si falta:
```java
import org.springframework.web.bind.annotation.RequestBody;
```

- [ ] **Step 4: Compilar el servidor**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Test manual de borrado con motivo**

Arrancar servidor + cliente. Borrar una asignación con un motivo escrito. Verificar en BD:
```sql
SELECT ID_LOG, ACCION, DETALLE, MOTIVO FROM Log_Actividad ORDER BY FECHA DESC LIMIT 3;
```

Expected: fila con `ACCION = 'ELIMINAR_ASIGNACION'`, `DETALLE` con IMEI y MODELO, `MOTIVO` con el texto escrito.

- [ ] **Step 6: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat(logs): ELIMINAR_ASIGNACION y ELIMINAR_REPARACION con IMEI, MODELO y MOTIVO"
```

---

## Task 8: Servidor — ReparacionController creaciones/ediciones enriquecidas

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

**Interfaces:**
- Consumes: helpers `getNombreTecnicoById`, `getTipoComponenteById`, `getModeloByImei`, `getResumenById` de Task 7
- Consumes: `LogDAO.insertar(int, String, String)` — versión sin motivo (delega a la de 4 args)

- [ ] **Step 1: Enriquecer CREAR_REPARACION (insertar)**

Reemplazar el método `insertar`:
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Map<String, Object> insertar(@RequestBody InsertarRequest req,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
    String idRep = dao.insertar(req.imei(), req.idTec(), req.fechaAsig(), req.fechaFin());
    String modelo = dao.getModeloByImei(req.imei());
    String tecnico = dao.getNombreTecnicoById(req.idTec());
    logDao.insertar(principal.getIdUsu(), "CREAR_REPARACION",
            "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
            ", TECNICO: " + tecnico);
    return Map.of("value", idRep);
}
```

- [ ] **Step 2: Enriquecer CREAR_ASIGNACION (insertarAsignacion)**

Reemplazar el método `insertarAsignacion`:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PostMapping("/asignaciones")
@ResponseStatus(HttpStatus.CREATED)
public Map<String, Object> insertarAsignacion(@RequestBody AsignacionRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
    String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario());
    String modelo = dao.getModeloByImei(req.imei());
    String tecnico = dao.getNombreTecnicoById(req.idTec());
    logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION",
            "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
            ", TECNICO: " + tecnico);
    return Map.of("value", idRep);
}
```

- [ ] **Step 3: Enriquecer COMPLETAR_REPARACION (insertarCompleta)**

Reemplazar el método `insertarCompleta`:
```java
@PostMapping("/completa")
@ResponseStatus(HttpStatus.CREATED)
public void insertarCompleta(@RequestBody InsertarCompletaRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.insertarCompleta(req.filas(), req.imei(), req.idTec(),
            req.idRepAnterior(), req.idAsignacion());
    String modelo = dao.getModeloByImei(req.imei());
    String tecnico = dao.getNombreTecnicoById(req.idTec());
    logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION",
            "ID_REP: " + req.idAsignacion() + ", IMEI: " + req.imei() +
            ", MODELO: " + modelo + ", TECNICO: " + tecnico);
}
```

- [ ] **Step 4: Enriquecer COMPLETAR_REPARACION (completar)**

Reemplazar el método `completar`:
```java
@PatchMapping("/{idRep}/completar")
public void completar(@PathVariable String idRep,
                      @AuthenticationPrincipal UsuarioPrincipal principal) {
    ReparacionResumen rep = dao.getAsignacionById(idRep).orElse(null);
    dao.completar(idRep);
    String detalle = rep != null
            ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
              ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
              ", TECNICO: " + rep.getNombreTecnico()
            : "ID_REP: " + idRep;
    logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION", detalle);
}
```

- [ ] **Step 5: Enriquecer ACTUALIZAR_ASIGNACION**

Reemplazar el método `actualizarAsignacion`:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PatchMapping("/asignaciones/{idRep}")
public void actualizarAsignacion(@PathVariable String idRep,
                                  @RequestBody ActualizarAsignacionRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
    ReparacionResumen ant = dao.getAsignacionById(idRep).orElse(null);
    dao.actualizarAsignacion(idRep, req.idTec(), req.comentarioAsignacion(), req.updatedAt());

    List<String> cambios = new ArrayList<>();
    cambios.add("ID_REP: " + idRep);
    if (ant != null) {
        if (ant.getIdTec() != req.idTec()) {
            String nomAnt = dao.getNombreTecnicoById(ant.getIdTec());
            String nomNue = dao.getNombreTecnicoById(req.idTec());
            cambios.add("TEC_ANT: " + nomAnt + " → TEC_NUE: " + nomNue);
        }
        String comAnt = ant.getComentarioAsignacion() != null ? ant.getComentarioAsignacion() : "";
        String comNue = req.comentarioAsignacion() != null ? req.comentarioAsignacion() : "";
        if (!comAnt.equals(comNue)) {
            cambios.add("COM_ANT: '" + comAnt + "' → COM_NUE: '" + comNue + "'");
        }
    }
    logDao.insertar(principal.getIdUsu(), "ACTUALIZAR_ASIGNACION",
            String.join(", ", cambios));
}
```

Añadir import si falta: `import java.util.ArrayList;`

- [ ] **Step 6: Enriquecer EDITAR_REPARACION**

Reemplazar el método `editarReparacion`:
```java
@PutMapping("/{idRep}")
public void editarReparacion(@PathVariable String idRep, @RequestBody EditarRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
    ReparacionResumen ant = dao.getResumenById(idRep).orElse(null);
    dao.editarReparacion(idRep, req.idComNuevo(), req.esReutilizadoNuevo(),
            req.observacionNueva(), req.nNuevas(), req.updatedAt());

    List<String> cambios = new ArrayList<>();
    cambios.add("ID_REP: " + idRep);
    if (ant != null) {
        String comAnt = ant.getTipoComponente() != null ? ant.getTipoComponente() : "";
        String comNue = req.idComNuevo() > 0 ? dao.getTipoComponenteById(req.idComNuevo()) : comAnt;
        if (!comAnt.equals(comNue)) {
            cambios.add("COM_ANT: " + comAnt + " → COM_NUE: " + comNue);
        }
        String obsAnt = ant.getObservaciones() != null ? ant.getObservaciones() : "";
        String obsNue = req.observacionNueva() != null ? req.observacionNueva() : "";
        if (!obsAnt.equals(obsNue)) {
            cambios.add("OBS_ANT: '" + obsAnt + "' → OBS_NUE: '" + obsNue + "'");
        }
    }
    logDao.insertar(principal.getIdUsu(), "EDITAR_REPARACION",
            String.join(", ", cambios));
}
```

- [ ] **Step 7: Enriquecer MARCAR_INCIDENCIA**

Reemplazar el método `marcarIncidenciaYAsignar`:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PostMapping("/{idRep}/incidencia")
@ResponseStatus(HttpStatus.CREATED)
public void marcarIncidenciaYAsignar(@PathVariable String idRep,
                                      @RequestBody IncidenciaRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec());
    String modelo = dao.getModeloByImei(req.imei());
    String tecnicoNue = dao.getNombreTecnicoById(req.idTec());
    logDao.insertar(principal.getIdUsu(), "MARCAR_INCIDENCIA",
            "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
            ", TECNICO_NUE: " + tecnicoNue);
}
```

- [ ] **Step 8: Enriquecer GUARDAR_FILA_INDIVIDUAL**

Reemplazar el método `guardarFilaIndividual`:
```java
@PostMapping("/{idAsignacion}/filas")
@ResponseStatus(HttpStatus.CREATED)
public Map<String, String> guardarFilaIndividual(@PathVariable String idAsignacion,
                                                 @RequestBody GuardarFilaRequest req,
                                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
    String idRep = dao.guardarFilaIndividual(req.filas(), req.imei(), req.idTec(),
            req.idRepAnterior(), idAsignacion);
    String tecnico = dao.getNombreTecnicoById(req.idTec());
    logDao.insertar(principal.getIdUsu(), "GUARDAR_FILA_INDIVIDUAL",
            "ID_REP: " + idRep + ", ID_ASIG: " + idAsignacion +
            ", IMEI: " + req.imei() + ", TECNICO: " + tecnico);
    return Map.of("value", idRep);
}
```

- [ ] **Step 9: Enriquecer AGOTAR_COMPONENTE**

Reemplazar el método `agotarComponente`:
```java
@PostMapping("/{idAsignacion}/agotar-componente")
@ResponseStatus(HttpStatus.CREATED)
public void agotarComponente(@PathVariable String idAsignacion,
                              @RequestBody AgotarRequest req,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.agotarComponente(idAsignacion, req.idCom(), req.cantidad(), req.descripcion());
    String tipo = dao.getTipoComponenteById(req.idCom());
    logDao.insertar(principal.getIdUsu(), "AGOTAR_COMPONENTE",
            "ID_ASIG: " + idAsignacion + ", TIPO: " + tipo + ", CANT: " + req.cantidad());
}
```

- [ ] **Step 10: Compilar**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Test manual de creación de asignación**

Arrancar servidor + cliente. Crear una nueva asignación. Verificar en BD:
```sql
SELECT ACCION, DETALLE FROM Log_Actividad ORDER BY FECHA DESC LIMIT 1;
```

Expected: `ACCION = 'CREAR_ASIGNACION'`, `DETALLE` contiene IMEI, MODELO y TECNICO nombre (no IDs).

- [ ] **Step 12: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat(logs): ReparacionController — todos los logs con valores legibles y ANT→NUE"
```

---

## Task 9: Servidor — ComponenteController, TecnicoController, UsuarioController, CompraController

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TecnicoController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java`

- [ ] **Step 1: Añadir helpers a ComponenteDAO**

Añadir al final de `ComponenteDAO.java`, antes del `}` de cierre:

```java
public String getTipoById(int idCom) {
    return jdbc.queryForObject(
            "SELECT TIPO FROM Componente WHERE ID_COM = ?", String.class, idCom);
}

public int getStockById(int idCom) {
    return jdbc.queryForObject(
            "SELECT STOCK FROM Componente WHERE ID_COM = ?", Integer.class, idCom);
}
```

- [ ] **Step 2: Añadir getNombreById a TecnicoDAO**

Añadir al final de `TecnicoDAO.java`, antes del `}` de cierre:

```java
public String getNombreById(int idTec) {
    return jdbc.queryForObject(
            "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
}
```

- [ ] **Step 3: Añadir getNombreByIdTec a UsuarioDAO**

Añadir al final de `UsuarioDAO.java`, antes del `}` de cierre:

```java
public String getNombreByIdTec(int idTec) {
    return jdbc.queryForObject(
            "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
}
```

- [ ] **Step 4: Añadir getNombreById a ProveedorDAO**

Añadir al final de `ProveedorDAO.java`, antes del `}` de cierre:

```java
public String getNombreById(int idProv) {
    return jdbc.queryForObject(
            "SELECT NOMBRE FROM Proveedor WHERE ID_PROV = ?", String.class, idProv);
}
```

- [ ] **Step 5: Enriquecer ComponenteController**

En `ComponenteController.java`, reemplazar el método `actualizar` (EDITAR_COMPONENTE):
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PutMapping("/{idCom}")
public void actualizar(@PathVariable int idCom, @RequestBody ActualizarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
    int stockAnt = dao.getStockById(idCom);
    dao.actualizar(idCom, req.tipo(), req.stock(), req.stockMinimo(), req.updatedAt());
    logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
            "ID_COM: " + idCom + ", TIPO: " + req.tipo() +
            ", STOCK_ANT: " + stockAnt + " → STOCK_NUE: " + req.stock());
}
```

Reemplazar el método `eliminar` (ELIMINAR_COMPONENTE):
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@DeleteMapping("/{idCom}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void eliminar(@PathVariable int idCom,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
    String tipo = dao.getTipoById(idCom);
    dao.eliminar(idCom);
    logDao.insertar(principal.getIdUsu(), "ELIMINAR_COMPONENTE",
            "ID_COM: " + idCom + ", TIPO: " + tipo);
}
```

- [ ] **Step 6: Enriquecer TecnicoController**

En `TecnicoController.java`, reemplazar el método `eliminar`:
```java
@DeleteMapping("/{idTec}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void eliminar(@PathVariable int idTec,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
    String nombre = dao.getNombreById(idTec);
    dao.eliminar(idTec);
    logDao.insertar(principal.getIdUsu(), "ELIMINAR_TECNICO",
            "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
}
```

- [ ] **Step 7: Enriquecer UsuarioController**

En `UsuarioController.java`, reemplazar los métodos de activar/desactivar/eliminar:

```java
@PatchMapping("/tecnicos/{idTec}/activar")
@PreAuthorize("hasRole('ADMIN')")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void activarTecnico(@PathVariable int idTec,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
    String nombre = dao.getNombreByIdTec(idTec);
    dao.activarTecnico(idTec);
    logDao.insertar(principal.getIdUsu(), "ACTIVAR_USUARIO",
            "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
}

@PatchMapping("/tecnicos/{idTec}/desactivar")
@PreAuthorize("hasRole('ADMIN')")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void desactivarTecnico(@PathVariable int idTec,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
    String nombre = dao.getNombreByIdTec(idTec);
    dao.desactivarTecnico(idTec);
    logDao.insertar(principal.getIdUsu(), "DESACTIVAR_USUARIO",
            "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
}

@DeleteMapping("/tecnicos/{idTec}")
@PreAuthorize("hasRole('ADMIN')")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void eliminarTecnico(@PathVariable int idTec, @RequestParam int idUsu,
                            @AuthenticationPrincipal UsuarioPrincipal principal) {
    String nombre = dao.getNombreByIdTec(idTec);
    dao.eliminarTecnico(idTec, idUsu);
    logDao.insertar(principal.getIdUsu(), "ELIMINAR_USUARIO",
            "ID_TEC: " + idTec + ", ID_USU: " + idUsu + ", NOMBRE: " + nombre);
}
```

- [ ] **Step 8: Enriquecer CompraController — CREAR_PEDIDO y RECIBIR_PEDIDO**

`CompraController` necesita acceso a `ComponenteDAO` y `ProveedorDAO`. Modificar el constructor:

Añadir imports al inicio del archivo:
```java
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
```

Añadir campos y modificar constructor:
```java
private final CompraComponenteDAO dao;
private final LogDAO              logDao;
private final ComponenteDAO       componenteDao;
private final ProveedorDAO        proveedorDao;

public CompraController(CompraComponenteDAO dao, LogDAO logDao,
                        ComponenteDAO componenteDao, ProveedorDAO proveedorDao) {
    this.dao          = dao;
    this.logDao       = logDao;
    this.componenteDao = componenteDao;
    this.proveedorDao  = proveedorDao;
}
```

Reemplazar el método `insertar` (CREAR_PEDIDO):
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public void insertar(@RequestBody InsertarRequest req,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
            req.precioUnidad(), req.divisa(), req.precioEur());
    String tipo = componenteDao.getTipoById(req.idCom());
    String proveedor = proveedorDao.getNombreById(req.idProv());
    logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO",
            "COMPONENTE: " + tipo + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
}
```

Reemplazar el método `confirmarRecibido` (RECIBIR_PEDIDO):
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PatchMapping("/{idCompra}/confirmar-recibido")
public void confirmarRecibido(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
    // Fetch compra info before confirming to get component details
    List<com.reparaciones.servidor.model.CompraComponente> compras = dao.getAll();
    com.reparaciones.servidor.model.CompraComponente compra = compras.stream()
            .filter(c -> c.getIdCompra() == idCompra)
            .findFirst().orElse(null);
    dao.confirmarRecibido(idCompra, req.updatedAt());
    String detalle = "ID_COMPRA: " + idCompra;
    if (compra != null) {
        String tipo = componenteDao.getTipoById(compra.getIdCom());
        detalle = "ID_COMPRA: " + idCompra + ", COMPONENTE: " + tipo +
                  ", CANT: " + compra.getCantidad();
    }
    logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO", detalle);
}
```

Nota: `CompraComponente` debe tener `getIdCompra()`, `getIdCom()` y `getCantidad()`. Si esos getters no existen con esos nombres exactos, ajustar según los getters reales del modelo.

Reemplazar el método `confirmarParcial` (RECIBIR_PARCIAL) para renombrar el campo CANTIDAD → CANT_RECIBIDA:
```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PatchMapping("/{idCompra}/confirmar-parcial")
public void confirmarParcial(@PathVariable int idCompra, @RequestBody ConfirmarParcialRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.confirmarParcial(idCompra, req.cantidadRecibida(), req.updatedAt());
    logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL",
            "ID_COMPRA: " + idCompra + ", CANT_RECIBIDA: " + req.cantidadRecibida());
}
```

- [ ] **Step 9: Compilar**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Expected: BUILD SUCCESS. Si `CompraComponente` no tiene los getters usados en Step 8, leer el modelo y ajustar los nombres.

- [ ] **Step 10: Test manual rápido**

Arrancar servidor + cliente. Editar el stock de un componente. Verificar en BD:
```sql
SELECT ACCION, DETALLE FROM Log_Actividad ORDER BY FECHA DESC LIMIT 1;
```

Expected: `ACCION = 'EDITAR_COMPONENTE'`, `DETALLE` con TIPO, STOCK_ANT, STOCK_NUE.

- [ ] **Step 11: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TecnicoDAO.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TecnicoController.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java
git commit -m "feat(logs): enriquecer logs de Componente, Tecnico, Usuario y Compra con nombres legibles"
```

---

## Task 10: Cliente — LogController refactor (filtros server-side + filtro por acción + doble clic) + FXML

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/LogView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java`

**Interfaces:**
- Consumes: `LogDAO.getAll(String, String, LocalDate, LocalDate)` de Task 4
- Consumes: `LogActividad.getMotivo()` de Task 4
- Consumes: `ConfirmDialog.mostrarTexto(String, String)` — ya existe en ConfirmDialog

- [ ] **Step 1: Añadir txtFiltroAccion al FXML**

En `LogView.fxml`, añadir `txtFiltroAccion` en el HBox de filtros, antes de `txtFiltroTecnico`:

```xml
<HBox alignment="CENTER_LEFT" spacing="10">
    <TextField fx:id="txtBuscadorLogs" promptText="Buscar..."
               styleClass="buscador" prefWidth="220"/>
    <TextField fx:id="txtFiltroAccion" promptText="Acción..."
               prefWidth="150"/>
    <TextField fx:id="txtFiltroTecnico" promptText="Técnico..."
               prefWidth="150"/>
    <Label text="Desde:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
    <DatePicker fx:id="dpLogsDesde" prefWidth="130"/>
    <Label text="Hasta:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
    <DatePicker fx:id="dpLogsHasta" prefWidth="130"/>
    <Button fx:id="btnLimpiarFiltrosLogs" text="Limpiar filtros"
            styleClass="btn-secondary" onAction="#limpiarFiltrosLogs"/>
</HBox>
```

- [ ] **Step 2: Reescribir LogController completo**

Reemplazar `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java`:

```java
package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.dao.UsuarioDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.models.Usuario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogController {

    @FXML private TableView<LogActividad>           tablaLogs;
    @FXML private TableColumn<LogActividad, String> colFecha;
    @FXML private TableColumn<LogActividad, String> colUsuario;
    @FXML private TableColumn<LogActividad, String> colAccion;
    @FXML private TableColumn<LogActividad, String> colDetalle;
    @FXML private TextField                         txtBuscadorLogs;
    @FXML private TextField                         txtFiltroAccion;
    @FXML private TextField                         txtFiltroTecnico;
    @FXML private DatePicker                        dpLogsDesde;
    @FXML private DatePicker                        dpLogsHasta;

    private final LogDAO     logDAO     = new LogDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObservableList<LogActividad> logsMaster = FXCollections.observableArrayList();
    private String accionSeleccionada = null;
    private String tecnicoSeleccionado = null;

    private static final List<String> TIPOS_ACCION = List.of(
        "CREAR_ASIGNACION", "ACTUALIZAR_ASIGNACION", "CAMBIAR_PRIORIDAD",
        "EDITAR_REPARACION", "COMPLETAR_REPARACION", "ELIMINAR_ASIGNACION",
        "ELIMINAR_REPARACION", "CREAR_PEDIDO", "EDITAR_PEDIDO", "RECIBIR_PEDIDO",
        "RECIBIR_PARCIAL", "CANCELAR_PEDIDO", "CONFIRMAR_PEDIDO", "BORRAR_PEDIDO",
        "CREAR_COMPONENTE", "EDITAR_COMPONENTE", "ELIMINAR_COMPONENTE",
        "CREAR_TECNICO", "ELIMINAR_TECNICO", "CREAR_USUARIO",
        "ACTIVAR_USUARIO", "DESACTIVAR_USUARIO", "ELIMINAR_USUARIO",
        "LOGIN", "CAMBIAR_PASSWORD"
    );

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : ""));
        colUsuario.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        colAccion.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getAccion()));
        colDetalle.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getDetalle()));
        tablaLogs.getColumns().forEach(c -> c.setReorderable(false));

        // Buscador de texto — client-side sobre logsMaster
        FilteredList<LogActividad> logsFiltrados = new FilteredList<>(logsMaster, l -> true);
        txtBuscadorLogs.textProperty().addListener((obs, o, n) ->
                logsFiltrados.setPredicate(log -> coincideTexto(log, n)));
        tablaLogs.setItems(logsFiltrados);

        // Filtros server-side — cada cambio recarga logsMaster
        dpLogsDesde.valueProperty().addListener((obs, o, n) -> cargarLogs());
        dpLogsHasta.valueProperty().addListener((obs, o, n) -> cargarLogs());
        dpLogsDesde.getEditor().setDisable(true);
        dpLogsDesde.getEditor().setOpacity(1.0);
        dpLogsHasta.getEditor().setDisable(true);
        dpLogsHasta.getEditor().setOpacity(1.0);

        // Doble clic en fila → mostrar detalle completo
        tablaLogs.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                LogActividad sel = tablaLogs.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                String texto = sel.getDetalle() != null ? sel.getDetalle() : "";
                if (sel.getMotivo() != null && !sel.getMotivo().isBlank()) {
                    texto += "\n\nMOTIVO: " + sel.getMotivo();
                }
                ConfirmDialog.mostrarTexto("Detalle del log", texto);
            }
        });

        configurarFiltroAccion();
        configurarFiltroTecnico();
        cargarLogs();
    }

    private void configurarFiltroAccion() {
        ObservableList<String> acciones = FXCollections.observableArrayList(TIPOS_ACCION);
        FilteredList<String> accionesFiltradas = new FilteredList<>(acciones, s -> true);

        ListView<String> listaAcciones = new ListView<>(accionesFiltradas);
        listaAcciones.setFixedCellSize(28);
        listaAcciones.setPrefWidth(200);
        listaAcciones.setMaxHeight(224);
        listaAcciones.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 6, 0, 0, 2);");

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listaAcciones);

        Runnable mostrarPopup = () -> {
            if (!popup.isShowing() && txtFiltroAccion.getScene() != null) {
                javafx.geometry.Bounds b =
                        txtFiltroAccion.localToScreen(txtFiltroAccion.getBoundsInLocal());
                if (b != null) popup.show(txtFiltroAccion, b.getMinX(), b.getMaxY() + 2);
            }
        };

        txtFiltroAccion.setOnMouseClicked(e -> mostrarPopup.run());

        txtFiltroAccion.textProperty().addListener((obs, o, n) -> {
            String text = n == null ? "" : n.trim().toLowerCase();
            accionesFiltradas.setPredicate(s -> text.isEmpty() || s.toLowerCase().contains(text));
            if (text.isEmpty() && accionSeleccionada != null) {
                accionSeleccionada = null;
                cargarLogs();
            }
        });

        listaAcciones.setOnMouseClicked(e -> {
            String sel = listaAcciones.getSelectionModel().getSelectedItem();
            if (sel != null) {
                accionSeleccionada = sel;
                txtFiltroAccion.setText(sel);
                popup.hide();
                cargarLogs();
            }
        });
    }

    private void configurarFiltroTecnico() {
        ObservableList<String> todosUsuarios = FXCollections.observableArrayList();
        FilteredList<String> usuariosFiltrados = new FilteredList<>(todosUsuarios, s -> true);

        ListView<String> listaUsuarios = new ListView<>(usuariosFiltrados);
        listaUsuarios.setFixedCellSize(28);
        listaUsuarios.setPrefWidth(160);
        listaUsuarios.setMaxHeight(224);
        listaUsuarios.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 6, 0, 0, 2);");

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listaUsuarios);

        Runnable mostrarPopup = () -> {
            if (!popup.isShowing() && txtFiltroTecnico.getScene() != null) {
                javafx.geometry.Bounds b =
                        txtFiltroTecnico.localToScreen(txtFiltroTecnico.getBoundsInLocal());
                if (b != null) popup.show(txtFiltroTecnico, b.getMinX(), b.getMaxY() + 2);
            }
        };

        txtFiltroTecnico.setOnMouseClicked(e -> mostrarPopup.run());

        txtFiltroTecnico.textProperty().addListener((obs, o, n) -> {
            String text = n == null ? "" : n.trim().toLowerCase();
            usuariosFiltrados.setPredicate(s -> text.isEmpty() || s.toLowerCase().contains(text));
            if (text.isEmpty() && tecnicoSeleccionado != null) {
                tecnicoSeleccionado = null;
                cargarLogs();
            }
        });

        listaUsuarios.setOnMouseClicked(e -> {
            String sel = listaUsuarios.getSelectionModel().getSelectedItem();
            if (sel != null) {
                tecnicoSeleccionado = sel;
                txtFiltroTecnico.setText(sel);
                popup.hide();
                cargarLogs();
            }
        });

        try {
            List<Usuario> usuarios = usuarioDAO.getUsuariosTecnicos();
            todosUsuarios.setAll(usuarios.stream()
                    .map(Usuario::getNombreUsuario)
                    .sorted()
                    .toList());
        } catch (SQLException e) {
            // silencioso — dropdown vacío si falla la carga
        }
    }

    @FXML
    private void cargarLogs() {
        try {
            List<LogActividad> logs = logDAO.getAll(
                    accionSeleccionada,
                    tecnicoSeleccionado,
                    dpLogsDesde.getValue(),
                    dpLogsHasta.getValue());
            logsMaster.setAll(logs);
        } catch (SQLException e) {
            Alertas.mostrarError("Error al cargar los logs: " + e.getMessage());
        }
    }

    @FXML
    private void limpiarFiltrosLogs() {
        accionSeleccionada = null;
        tecnicoSeleccionado = null;
        txtBuscadorLogs.clear();
        txtFiltroAccion.clear();
        txtFiltroTecnico.clear();
        dpLogsDesde.setValue(null);
        dpLogsHasta.setValue(null);
        cargarLogs();
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }

    private static boolean coincideTexto(LogActividad log, String texto) {
        if (texto == null || texto.isBlank()) return true;
        String t = texto.toLowerCase().trim();
        return contiene(log.getNombreUsuario(), t)
                || contiene(log.getAccion(), t)
                || contiene(log.getDetalle(), t);
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto);
    }
}
```

- [ ] **Step 3: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Test manual completo de la vista de logs**

Arrancar servidor + cliente. Abrir la vista de logs. Verificar:
1. Los filtros de técnico y fecha recargan al seleccionar (llaman al servidor)
2. El filtro por acción muestra el dropdown con los 25 tipos
3. Seleccionar "ELIMINAR_ASIGNACION" filtra la tabla
4. Buscador de texto funciona sobre los resultados ya filtrados
5. Limpiar filtros recarga sin filtros
6. Doble clic en una fila con motivo muestra popup con DETALLE + "MOTIVO: ..."
7. Doble clic en fila sin motivo muestra solo DETALLE

- [ ] **Step 5: Commit**

```
git add gestion-reparaciones-cliente/src/main/resources/views/LogView.fxml
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "feat(logs): LogController con filtros server-side, filtro por acción y doble clic para detalle"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Task que lo implementa |
|---|---|
| `ALTER TABLE Log_Actividad ADD COLUMN MOTIVO TEXT NULL` | Task 1 |
| `LogDAO.insertar(idUsu, accion, detalle, motivo)` overload | Task 1 |
| `LogDAO.getFiltered(accion, tecnico, desde, hasta)` con WHERE dinámico | Task 1 |
| `LogController` acepta 4 query params opcionales | Task 2 |
| Eliminar `REASIGNAR_TECNICO` endpoint + `actualizarTecnico` cliente | Task 3 |
| Cliente `LogActividad.getMotivo()` | Task 4 |
| Cliente `LogDAO.getAll(accion, tecnico, desde, hasta)` con query params | Task 4 |
| `ApiClient.deleteWithBody` | Task 4 |
| Cliente `ReparacionDAO.eliminarAsignacion(idAsig, motivo)` y `eliminar(idRep, motivo)` | Task 4 |
| `ConfirmDialog.mostrarConMotivo` con TextArea obligatorio | Task 5 |
| Dialogo de borrado de asignación pide motivo | Task 6 |
| Diálogo de borrado de reparación pide motivo | Task 6 |
| `ELIMINAR_ASIGNACION` con IMEI, MODELO, TECNICO y MOTIVO en log | Task 7 |
| `ELIMINAR_REPARACION` con IMEI, MODELO, TECNICO y MOTIVO en log | Task 7 |
| `CREAR_ASIGNACION` con MODELO y TECNICO nombre | Task 8 |
| `ACTUALIZAR_ASIGNACION` solo campos que cambian, ANT→NUE | Task 8 |
| `EDITAR_REPARACION` con COM_ANT→COM_NUE y OBS_ANT→OBS_NUE | Task 8 |
| `COMPLETAR_REPARACION` con IMEI, MODELO, TECNICO | Task 8 |
| `MARCAR_INCIDENCIA` con MODELO, TECNICO_NUE | Task 8 |
| `GUARDAR_FILA_INDIVIDUAL` con TECNICO nombre | Task 8 |
| `AGOTAR_COMPONENTE` con TIPO en lugar de ID_COM | Task 8 |
| `EDITAR_COMPONENTE` con STOCK_ANT→STOCK_NUE | Task 9 |
| `ELIMINAR_COMPONENTE` con TIPO | Task 9 |
| `ELIMINAR_TECNICO` con NOMBRE | Task 9 |
| `ACTIVAR/DESACTIVAR/ELIMINAR_USUARIO` con NOMBRE | Task 9 |
| `CREAR_PEDIDO` con COMPONENTE y PROVEEDOR nombre | Task 9 |
| `RECIBIR_PEDIDO` con COMPONENTE y CANT | Task 9 |
| `RECIBIR_PARCIAL` con CANT_RECIBIDA (renombrado) | Task 9 |
| Filtros (accion, tecnico, desde, hasta) → server-side | Task 10 |
| Nuevo filtro por tipo de acción con dropdown de 25 tipos | Task 10 |
| Doble clic en fila → popup con DETALLE + MOTIVO | Task 10 |
| Columna DETALLE con elipsis (sin nueva columna para MOTIVO) | Task 10 (comportamiento por defecto JavaFX) |

### Posibles issues a verificar durante implementación

1. **Task 8, Step 8 (RECIBIR_PEDIDO)**: `dao.getAll()` para encontrar la compra es ineficiente. Si `CompraComponenteDAO` tiene un `getById(int idCompra)`, usar ese directamente. Si no existe, añadirlo.

2. **Task 9, Step 8 (CompraController)**: verificar que `CompraComponente` tiene `getIdCom()` y `getCantidad()`. Si los getters tienen otro nombre, ajustar.

3. **Task 7, Step 1**: `HISTORIAL_SELECT` es una constante privada de `ReparacionDAO`. Verificar que está definida como `private static final String HISTORIAL_SELECT` para poder usarla en el nuevo método `getHistorialById`.

4. **Task 8, Step 6 (EDITAR_REPARACION)**: si `req.idComNuevo() == 0` indica "sin cambio de componente", la lógica de comparación debe ignorar ese campo. Ajustar el `if (req.idComNuevo() > 0)` según lo que el servidor interpreta como "no cambiar componente".

5. **Tasks 7 y 8**: las consultas hechas ANTES del DELETE/UPDATE añaden latencia. Si el rendimiento es crítico, estas consultas son de solo lectura y rápidas (por PK) — debería ser aceptable.
