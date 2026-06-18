# Revisión logística por IMEI — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir una columna "Revisión" con ToggleSwitch en la vista agrupada de IMEIs, persistida en BD, con reset automático al crear asignaciones y edición exclusiva para SuperTécnico.

**Architecture:** Se añade `REVISION_LOGISTICA TINYINT(1)` a la tabla `Telefono`. El servidor expone un `PUT /api/telefonos/{imei}/revision-logistica` con validación de concurrencia (409 si hay asignaciones activas). `HISTORIAL_SELECT` se enriquece con `REVISION_LOGISTICA` y una subquery `TIENE_ASIGNACIONES`. El cliente lo muestra como `ToggleButton` estilizado: solo editable por SuperTécnico y solo cuando no hay asignaciones activas.

**Tech Stack:** MariaDB, Spring Boot (servidor), JavaFX + Gson (cliente), JdbcTemplate.

## Global Constraints

- Columna BD: `REVISION_LOGISTICA TINYINT(1) NOT NULL DEFAULT 0` en tabla `Telefono`
- Endpoint: `PUT /api/telefonos/{imei}/revision-logistica`, rol `SUPERTECNICO`
- 409 → `StaleDataException` en cliente (comportamiento existente de `ApiClient`)
- Solo Admin y SuperTécnico ven la columna (Técnico no tiene vista agrupada)
- Solo SuperTécnico puede editar el toggle; Admin solo lectura
- El toggle se muestra siempre OFF y deshabilitado si `tieneAsignaciones = true`
- "Asignación activa" = `Reparacion WHERE ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND FECHA_FIN IS NULL`
- Rutas servidor: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/`
- Rutas cliente: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/`

---

## Mapa de ficheros

| Fichero | Acción |
|---|---|
| BD `Telefono` | `ALTER TABLE` — nueva columna `REVISION_LOGISTICA` |
| `servidor/dao/TelefonoDAO.java` | Añadir `tieneAsignacionesActivas()` y `actualizarRevisionLogistica()` |
| `servidor/dao/ReparacionDAO.java` | `HISTORIAL_SELECT` + `RESUMEN_MAPPER` + `insertarAsignacion()` |
| `servidor/model/ReparacionResumen.java` | Añadir campos `revisionLogistica` y `tieneAsignaciones` |
| `servidor/controller/TelefonoController.java` | Nuevo endpoint `PUT /{imei}/revision-logistica` |
| `cliente/models/ReparacionResumen.java` | Añadir campos `revisionLogistica` y `tieneAsignaciones` + setters |
| `cliente/models/GrupoImei.java` | Añadir campos derivados `revisionLogistica` y `tieneAsignaciones` |
| `cliente/dao/TelefonoDAO.java` | Añadir `actualizarRevisionLogistica()` |
| `views/ReparacionViewAdmin.fxml` | Nueva `TableColumn fx:id="colRevision"` |
| `views/ReparacionViewSuperTecnico.fxml` | Nueva `TableColumn fx:id="colRevision"` |
| `controllers/ReparacionControllerAdmin.java` | Configurar `colRevision` (read-only badge) |
| `controllers/ReparacionControllerSuperTecnico.java` | Configurar `colRevision` (ToggleButton editable) + CSV |

---

## Task 1: Migración BD y capa de datos servidor

**Files:**
- Modify: `servidor/dao/TelefonoDAO.java`
- Modify: `servidor/model/ReparacionResumen.java`

**Interfaces:**
- Produces:
  - `TelefonoDAO.tieneAsignacionesActivas(String imei): boolean`
  - `TelefonoDAO.actualizarRevisionLogistica(String imei, boolean revisado): void`
  - `ReparacionResumen.isRevisionLogistica(): boolean` / `setRevisionLogistica(boolean)`
  - `ReparacionResumen.isTieneAsignaciones(): boolean` / `setTieneAsignaciones(boolean)`

- [ ] **Step 1: Ejecutar migración SQL en la BD**

Conectar a MariaDB y ejecutar:
```sql
ALTER TABLE Telefono
  ADD COLUMN REVISION_LOGISTICA TINYINT(1) NOT NULL DEFAULT 0;
```

Verificar:
```sql
DESCRIBE Telefono;
-- Debe aparecer: REVISION_LOGISTICA | tinyint(1) | NO | | 0 |
```

- [ ] **Step 2: Añadir campos a `servidor/model/ReparacionResumen.java`**

Tras el campo `urgente` (línea ~30), añadir:
```java
private boolean revisionLogistica;
private boolean tieneAsignaciones;
```

Al final de los getters/setters (tras `setUrgente`), añadir:
```java
public boolean isRevisionLogistica()              { return revisionLogistica; }
public void    setRevisionLogistica(boolean v)    { this.revisionLogistica = v; }
public boolean isTieneAsignaciones()              { return tieneAsignaciones; }
public void    setTieneAsignaciones(boolean v)    { this.tieneAsignaciones = v; }
```

- [ ] **Step 3: Añadir métodos a `servidor/dao/TelefonoDAO.java`**

Al final de la clase, antes del cierre `}`:
```java
public boolean tieneAsignacionesActivas(String imei) {
    Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM Reparacion" +
            " WHERE IMEI = ? AND ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND FECHA_FIN IS NULL",
            Integer.class, imei);
    return count != null && count > 0;
}

public void actualizarRevisionLogistica(String imei, boolean revisado) {
    jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = ? WHERE IMEI = ?",
            revisado ? 1 : 0, imei);
}
```

- [ ] **Step 4: Commit**

```bash
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java
git -C gestion-reparaciones-servidor commit -m "feat: añadir revision_logistica — modelo y DAO Telefono"
```

---

## Task 2: Enriquecer historial y reset al crear asignación

**Files:**
- Modify: `servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Consumes: `ReparacionResumen.setRevisionLogistica()`, `ReparacionResumen.setTieneAsignaciones()` (Task 1)
- Produces: `getHistorial()` devuelve `revisionLogistica` y `tieneAsignaciones` en cada `ReparacionResumen`; `insertarAsignacion()` resetea `REVISION_LOGISTICA = 0`

- [ ] **Step 1: Añadir columnas a `HISTORIAL_SELECT`**

En `ReparacionDAO.java`, localizar la constante `HISTORIAL_SELECT`. La línea que empieza con `" r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION,"` es la penúltima del SELECT (antes de los FROM/JOIN). Añadir las dos nuevas columnas al final del bloque SELECT, antes de `" FROM Reparacion r"`:

El fragmento actual termina así:
```java
" r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION," +
" tel.OBSERVACION AS OBSERVACION_TELEFONO" +
" FROM Reparacion r"
```

Cambiarlo a:
```java
" r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION," +
" tel.OBSERVACION AS OBSERVACION_TELEFONO," +
" COALESCE(tel.REVISION_LOGISTICA, 0) AS REVISION_LOGISTICA," +
" (SELECT COUNT(*) FROM Reparacion r2" +
"  WHERE r2.IMEI = r.IMEI AND r2.ID_REP LIKE 'A%'" +
"  AND r2.ID_REP NOT LIKE 'AP%' AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES" +
" FROM Reparacion r"
```

- [ ] **Step 2: Leer los nuevos campos en `RESUMEN_MAPPER`**

En `RESUMEN_MAPPER`, tras la línea `try { rr.setUrgente(rs.getBoolean("URGENTE")); } catch (Exception ignored) {}`, añadir:
```java
try { rr.setRevisionLogistica(rs.getBoolean("REVISION_LOGISTICA")); } catch (Exception ignored) {}
try { rr.setTieneAsignaciones(rs.getInt("TIENE_ASIGNACIONES") > 0); } catch (Exception ignored) {}
```

El try-catch sigue el patrón existente para `URGENTE` — las queries de asignaciones no incluyen estas columnas y no deben fallar.

- [ ] **Step 3: Resetear `REVISION_LOGISTICA` en `insertarAsignacion()`**

Localizar el método `insertarAsignacion()` (línea ~292 aprox.):
```java
@Transactional
public String insertarAsignacion(String imei, int idTec, String comentario) {
    ensureTelefono(imei);
    String idRep = nextId("A");
    jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION) VALUES (?,?,?,NOW(),?)",
            idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null);
    return idRep;
}
```

Añadir el reset justo antes del `return idRep;`:
```java
@Transactional
public String insertarAsignacion(String imei, int idTec, String comentario) {
    ensureTelefono(imei);
    String idRep = nextId("A");
    jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION) VALUES (?,?,?,NOW(),?)",
            idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null);
    jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
    return idRep;
}
```

- [ ] **Step 4: Verificar manualmente que el historial devuelve los nuevos campos**

Arrancar el servidor y hacer:
```
GET /api/reparaciones/historial
```
El JSON de cada elemento debe incluir `"revisionLogistica": false` y `"tieneAsignaciones": false` (o true según el estado real).

- [ ] **Step 5: Commit**

```bash
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git -C gestion-reparaciones-servidor commit -m "feat: historial incluye revision_logistica y tiene_asignaciones; reset al crear asignacion"
```

---

## Task 3: Endpoint PUT revision-logistica

**Files:**
- Modify: `servidor/controller/TelefonoController.java`

**Interfaces:**
- Consumes: `TelefonoDAO.tieneAsignacionesActivas()`, `TelefonoDAO.actualizarRevisionLogistica()` (Task 1)
- Produces: `PUT /api/telefonos/{imei}/revision-logistica` — `204 No Content` si ok, `409 Conflict` si hay asignaciones activas

- [ ] **Step 1: Añadir import en `TelefonoController.java`**

Al principio del fichero, añadir si no está ya:
```java
import org.springframework.web.server.ResponseStatusException;
```

- [ ] **Step 2: Añadir el nuevo record de request y el endpoint**

Al final de `TelefonoController.java`, antes del cierre `}` de la clase, añadir:

```java
@PutMapping("/{imei}/revision-logistica")
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("hasRole('SUPERTECNICO')")
public void actualizarRevisionLogistica(@PathVariable String imei,
                                        @RequestBody RevisionLogisticaRequest req) {
    if (req.revisado() && dao.tieneAsignacionesActivas(imei)) {
        throw new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "El IMEI tiene asignaciones activas");
    }
    dao.actualizarRevisionLogistica(imei, req.revisado());
}

private record RevisionLogisticaRequest(boolean revisado) {}
```

> Nota: solo se valida el 409 cuando se intenta poner a `true`. Poner a `false` siempre está permitido (aunque en la práctica el UI no lo permite tampoco si hay asignaciones, esta defensa protege ante llamadas directas a la API).

- [ ] **Step 3: Verificar el endpoint manualmente**

Con el servidor arrancado, probar con un IMEI sin asignaciones:
```
PUT /api/telefonos/{imei}/revision-logistica
Body: {"revisado": true}
→ 204 No Content
```

Crear una asignación para ese IMEI y volver a intentarlo:
```
PUT /api/telefonos/{imei}/revision-logistica
Body: {"revisado": true}
→ 409 Conflict con body: "El IMEI tiene asignaciones activas"
```

- [ ] **Step 4: Commit**

```bash
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/controller/TelefonoController.java
git -C gestion-reparaciones-servidor commit -m "feat: endpoint PUT revision-logistica con validacion 409"
```

---

## Task 4: Modelos cliente

**Files:**
- Modify: `cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java`
- Modify: `cliente/src/main/java/com/reparaciones/models/GrupoImei.java`
- Modify: `cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java`

**Interfaces:**
- Produces:
  - `ReparacionResumen.isRevisionLogistica(): boolean`
  - `ReparacionResumen.isTieneAsignaciones(): boolean`
  - `GrupoImei.isRevisionLogistica(): boolean`
  - `GrupoImei.isTieneAsignaciones(): boolean`
  - `TelefonoDAO.actualizarRevisionLogistica(String imei, boolean revisado): void throws SQLException`

- [ ] **Step 1: Añadir campos a `cliente/models/ReparacionResumen.java`**

Tras el campo `urgente` (línea ~41), añadir:
```java
private boolean revisionLogistica;
private boolean tieneAsignaciones;
```

Al final de los getters/setters (tras `setUrgente`/`isUrgente`), añadir:
```java
public boolean isRevisionLogistica()           { return revisionLogistica; }
public void    setRevisionLogistica(boolean v) { this.revisionLogistica = v; }
public boolean isTieneAsignaciones()           { return tieneAsignaciones; }
public void    setTieneAsignaciones(boolean v) { this.tieneAsignaciones = v; }
```

> Gson deserializa por nombre de campo, así que estos campos se rellenan automáticamente con los valores JSON del servidor.

- [ ] **Step 2: Añadir campos a `cliente/models/GrupoImei.java`**

Añadir dos campos al bloque de fields existente (tras `countIncAbiertas`):
```java
private final boolean revisionLogistica;
private final boolean tieneAsignaciones;
```

En el constructor `GrupoImei(String imei, List<ReparacionResumen> reparaciones)`, al final del bloque de inicialización (tras `this.countIncAbiertas = ...`):
```java
ReparacionResumen primero = reparaciones.isEmpty() ? null : reparaciones.get(0);
this.revisionLogistica = primero != null && primero.isRevisionLogistica();
this.tieneAsignaciones = primero != null && primero.isTieneAsignaciones();
```

Al final de los getters (tras `getCountIncAbiertas()`):
```java
public boolean isRevisionLogistica() { return revisionLogistica; }
public boolean isTieneAsignaciones() { return tieneAsignaciones; }
```

- [ ] **Step 3: Añadir método a `cliente/dao/TelefonoDAO.java`**

Al final de la clase, antes del cierre `}`:
```java
public void actualizarRevisionLogistica(String imei, boolean revisado) throws SQLException {
    ApiClient.put("/api/telefonos/" + imei + "/revision-logistica",
            Map.of("revisado", revisado));
}
```

El import de `Map` ya existe en este fichero.

- [ ] **Step 4: Commit**

```bash
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/models/ReparacionResumen.java
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/models/GrupoImei.java
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/dao/TelefonoDAO.java
git -C gestion-reparaciones-cliente commit -m "feat: modelos cliente y DAO con revision_logistica y tiene_asignaciones"
```

---

## Task 5: Vista Admin — columna Revisión (solo lectura)

**Files:**
- Modify: `cliente/src/main/resources/views/ReparacionViewAdmin.fxml`
- Modify: `cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

**Interfaces:**
- Consumes: `GrupoImei.isRevisionLogistica()`, `GrupoImei.isTieneAsignaciones()` (Task 4)

- [ ] **Step 1: Añadir `colRevision` al FXML de Admin**

En `ReparacionViewAdmin.fxml`, localizar el bloque `<columns>`. Añadir la nueva columna tras `colObservacionTelefono`:
```xml
<TableColumn fx:id="colRevision" minWidth="90" prefWidth="100" maxWidth="120" text="Revisión"/>
```

- [ ] **Step 2: Declarar `colRevision` en `ReparacionControllerAdmin.java`**

Localizar el bloque de `@FXML` fields con las demás columnas y añadir:
```java
@FXML private TableColumn<Object, Void> colRevision;
```

- [ ] **Step 3: Configurar la celda de `colRevision` en Admin (solo lectura)**

En el método de inicialización de columnas (el mismo donde se configuran `colImei`, `colEstado`, etc.), añadir:

```java
colRevision.setCellFactory(col -> new TableCell<>() {
    private final Label badge = new Label();
    {
        badge.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                       "-fx-font-size: 11px; -fx-font-weight: bold;");
        setAlignment(javafx.geometry.Pos.CENTER);
    }
    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
            setGraphic(null); return;
        }
        Object row = getTableView().getItems().get(getIndex());
        if (row instanceof GrupoImei grupo) {
            boolean efectivo = grupo.isRevisionLogistica() && !grupo.isTieneAsignaciones();
            if (efectivo) {
                badge.setText("OK");
                badge.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                               "-fx-font-size: 11px; -fx-font-weight: bold; " +
                               "-fx-background-color: #2E7D32; -fx-text-fill: white;");
            } else {
                badge.setText("—");
                badge.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                               "-fx-font-size: 11px; -fx-font-weight: bold; " +
                               "-fx-background-color: #9E9E9E; -fx-text-fill: white;");
            }
            setGraphic(badge);
        } else {
            setGraphic(null);
        }
    }
});
```

- [ ] **Step 4: Ocultar `colRevision` en modo Detalle, mostrar en modo Maestro**

Localizar donde se gestionan las visibilidades de columnas al cambiar de modo Maestro ↔ Detalle. En el método que activa el modo Maestro, añadir:
```java
colRevision.setVisible(true);
```
En el método que activa el modo Detalle, añadir:
```java
colRevision.setVisible(false);
```

- [ ] **Step 5: Añadir `colRevision` al método de exportación CSV**

Localizar el bloque donde se construyen `cabeceras` y `filas` para el CSV (buscar `"IMEI", "Modelo"`). Añadir "Revisión logística" a las cabeceras:
```java
List<String> cabeceras = List.of(
    "IMEI", "Modelo", "Técnico", "Primera reparación", "Última reparación",
    "Nº reparaciones", "Inc. abiertas", "Observación", "Revisión logística");
```

Y en el loop de filas, añadir el valor al final de la lista de cada fila:
```java
filas.add(List.of(
    com.reparaciones.utils.CsvExporter.textoForzado(g.getImei()),
    // ... campos existentes ...,
    (g.isRevisionLogistica() && !g.isTieneAsignaciones()) ? "Sí" : "No"
));
```

- [ ] **Step 6: Verificar visualmente**

Arrancar el cliente con rol Admin, ir a la pestaña de historial, activar vista agrupada. Confirmar que:
- Aparece la columna "Revisión"
- Muestra badge gris "—" para todos los IMEIs (aún sin revisar)
- El badge es solo visual, no interactivo
- En modo Detalle, la columna desaparece
- El CSV exportado incluye la columna "Revisión logística"

- [ ] **Step 7: Commit**

```bash
git -C gestion-reparaciones-cliente add src/main/resources/views/ReparacionViewAdmin.fxml
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java
git -C gestion-reparaciones-cliente commit -m "feat: columna Revision logistica en vista agrupada Admin (solo lectura)"
```

---

## Task 6: Vista SuperTécnico — columna Revisión (editable)

**Files:**
- Modify: `cliente/src/main/resources/views/ReparacionViewSuperTecnico.fxml`
- Modify: `cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`

**Interfaces:**
- Consumes: `GrupoImei.isRevisionLogistica()`, `GrupoImei.isTieneAsignaciones()` (Task 4); `TelefonoDAO.actualizarRevisionLogistica()` (Task 4)

- [ ] **Step 1: Añadir `colRevision` al FXML de SuperTécnico**

En `ReparacionViewSuperTecnico.fxml`, localizar el bloque `<columns>`. Añadir tras `colObservacionTelefono`:
```xml
<TableColumn fx:id="colRevision" minWidth="90" prefWidth="100" maxWidth="120" text="Revisión"/>
```

- [ ] **Step 2: Declarar `colRevision` y el DAO en el controller**

En `ReparacionControllerSuperTecnico.java`, añadir el field FXML:
```java
@FXML private TableColumn<Object, Void> colRevision;
```

Añadir el DAO de Telefono si no existe ya en este controller:
```java
private final com.reparaciones.dao.TelefonoDAO telefonoDAO = new com.reparaciones.dao.TelefonoDAO();
```

- [ ] **Step 3: Configurar la celda editable de `colRevision` en SuperTécnico**

En el método de inicialización de columnas:

```java
colRevision.setCellFactory(col -> new TableCell<>() {
    private final ToggleButton toggle = new ToggleButton();
    {
        toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                        "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        setAlignment(javafx.geometry.Pos.CENTER);

        toggle.setOnAction(e -> {
            if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
            Object row = getTableView().getItems().get(getIndex());
            if (!(row instanceof GrupoImei grupo)) return;

            boolean nuevoValor = toggle.isSelected();
            // Snapshot del estado anterior para revertir si hay error
            boolean estadoAnterior = !nuevoValor;

            new Thread(() -> {
                try {
                    telefonoDAO.actualizarRevisionLogistica(grupo.getImei(), nuevoValor);
                    // Recargar el grupo desde el servidor para sincronizar tieneAsignaciones
                    javafx.application.Platform.runLater(this::actualizarVista);
                } catch (com.reparaciones.utils.StaleDataException ex) {
                    javafx.application.Platform.runLater(() -> {
                        toggle.setSelected(estadoAnterior);
                        aplicarEstiloToggle(estadoAnterior);
                        new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.WARNING,
                                "Este IMEI tiene asignaciones activas. No se puede marcar como revisado.")
                                .showAndWait();
                        actualizarVista();
                    });
                } catch (java.sql.SQLException ex) {
                    javafx.application.Platform.runLater(() -> {
                        toggle.setSelected(estadoAnterior);
                        aplicarEstiloToggle(estadoAnterior);
                        new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR,
                                "Error al guardar: " + ex.getMessage())
                                .showAndWait();
                    });
                }
            }).start();
        });
    }

    private void aplicarEstiloToggle(boolean on) {
        if (on) {
            toggle.setText("OK");
            toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-background-color: #2E7D32; -fx-text-fill: white;");
        } else {
            toggle.setText("—");
            toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        }
    }

    private void actualizarVista() {
        cargarDatos();
    }

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
            setGraphic(null); return;
        }
        Object row = getTableView().getItems().get(getIndex());
        if (row instanceof GrupoImei grupo) {
            // Si hay asignaciones, siempre OFF y deshabilitado
            boolean efectivo = grupo.isRevisionLogistica() && !grupo.isTieneAsignaciones();
            toggle.setSelected(efectivo);
            aplicarEstiloToggle(efectivo);
            toggle.setDisable(grupo.isTieneAsignaciones());
            if (grupo.isTieneAsignaciones()) {
                toggle.setStyle(toggle.getStyle().replace("-fx-cursor: hand;", "-fx-cursor: default;") +
                                " -fx-opacity: 0.5;");
            }
            setGraphic(toggle);
        } else {
            setGraphic(null);
        }
    }
});
```

> **Nota:** `cargarDatos()` es el método existente en `ReparacionControllerSuperTecnico` que llama a `reparacionDAO.getReparacionesResumen()` y aplica filtros.

- [ ] **Step 4: Ocultar `colRevision` en modo Detalle, mostrar en Maestro**

En los métodos de cambio de modo (igual que en Task 5):
```java
// Al activar modo Maestro:
colRevision.setVisible(true);
// Al activar modo Detalle:
colRevision.setVisible(false);
```

- [ ] **Step 5: Verificar visualmente**

Arrancar el cliente con rol SuperTécnico, ir al historial agrupado. Confirmar:
- La columna "Revisión" aparece con toggle gris "—"
- Para un IMEI sin asignaciones: el toggle es clickable, al pulsar pasa a verde "OK"
- Al recargar, mantiene el estado "OK"
- Para un IMEI con asignación activa: toggle OFF y semitransparente, no responde a clics
- Si se crea una asignación para un IMEI que estaba OK: al recargar la vista, el toggle pasa a OFF y bloqueado
- Si se intenta el toggle con asignación activa desde otra sesión: aparece alerta con el mensaje correcto

- [ ] **Step 6: Commit**

```bash
git -C gestion-reparaciones-cliente add src/main/resources/views/ReparacionViewSuperTecnico.fxml
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
git -C gestion-reparaciones-cliente commit -m "feat: columna Revision logistica editable en vista SuperTecnico"
```
