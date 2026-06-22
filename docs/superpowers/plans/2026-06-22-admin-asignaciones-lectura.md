# Admin ve asignaciones en solo lectura — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar al admin, en la vista de reparaciones, un sidebar con Historial y Asignaciones (generales, reparación y pulido) en modo solo lectura.

**Architecture:** Se reutilizan los paneles del supertécnico (`PendientesSuperTecnicoView`/`Controller` y `PulidoSuperTecnicoView`/`Controller`) añadiéndoles un flag `setSoloLectura(boolean)` (default `false`). La vista admin pasa de `VBox` a `BorderPane` con sidebar, embebe esos paneles vía `fx:include` y activa el modo lectura. Solo cliente, sin servidor ni BD.

**Tech Stack:** JavaFX 17, FXML, controladores `@FXML`.

## Global Constraints

- **Solo cliente.** El servidor y la BD no cambian (`GET /api/reparaciones/asignaciones` ya está abierto a cualquier autenticado).
- **Quirúrgico:** `setSoloLectura` por defecto `false`; **solo el admin lo activa**. El supertécnico nunca lo llama → su comportamiento queda idéntico. Regresión del supertécnico = fallo.
- En modo lectura se apaga TODA acción de escritura: papelera (borrar), botón crear, reasignar inline (ComboBox de técnico) y menú contextual (editar comentario, marcar urgente).
- El admin no ve el panel "Pendientes".
- UI sin tests automáticos (patrón del proyecto): verificación por compilación + pruebas manuales.

---

## Task 1: Modo solo lectura en PendientesSuperTecnicoController

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Produces: `PendientesSuperTecnicoController.setSoloLectura(boolean)` — usado en Task 3

- [ ] **Step 1: Declarar el campo `btnAsignar` y el flag**

El FXML ya tiene `<Button fx:id="btnAsignar" .../>` pero el controller no lo referencia. Añadir junto a la declaración de `cAccion` (campo `@FXML private TableColumn<ReparacionResumen, Void> cAccion;`):
```java
    @FXML private javafx.scene.control.Button btnAsignar;
    private boolean soloLectura = false;
```

- [ ] **Step 2: Añadir el método `setSoloLectura`**

Añadir como método público (p.ej. justo después de `initialize()`):
```java
    /** Activa el modo solo lectura (admin): oculta acciones de escritura. Default false (supertécnico no afectado). */
    public void setSoloLectura(boolean soloLectura) {
        this.soloLectura = soloLectura;
        if (soloLectura) {
            cAccion.setVisible(false);
            if (btnAsignar != null) { btnAsignar.setVisible(false); btnAsignar.setManaged(false); }
            tablaPendientes.refresh();
        }
    }
```

- [ ] **Step 3: Guardar el menú contextual tras el flag**

En el `setOnContextMenuRequested` del `setRowFactory` (el handler que empieza en la línea con `setOnContextMenuRequested(e -> {`), añadir como primera instrucción dentro del lambda:
```java
                    if (soloLectura) { e.consume(); return; }
```
Queda:
```java
                setOnContextMenuRequested(e -> {
                    if (soloLectura) { e.consume(); return; }
                    // Selecciona la fila clicada para que el guardado directo nunca caiga en otra.
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size())
                        getTableView().getSelectionModel().select(getIndex());
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });
```

- [ ] **Step 4: Mostrar el técnico como texto (no ComboBox) en solo lectura**

En el `cTecnico.setCellFactory`, dentro de `updateItem`, justo después de la línea `ReparacionResumen rep = getTableView().getItems().get(getIndex());` (la que precede a `cb.getItems().setAll(tecnicos);`), insertar:
```java
                if (soloLectura) {
                    setText(rep.getNombreTecnico() != null ? rep.getNombreTecnico() : "");
                    setGraphic(null);
                    actualizando = false;
                    setStyle("");
                    return;
                }
```
(Así el admin ve el nombre del técnico como texto plano, sin el desplegable que reasigna.)

- [ ] **Step 5: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(admin-lectura): setSoloLectura en PendientesSuperTecnicoController (default false)"
```

---

## Task 2: Modo solo lectura en PulidoSuperTecnicoController

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`

**Interfaces:**
- Produces: `PulidoSuperTecnicoController.setSoloLectura(boolean)` — usado en Task 3

- [ ] **Step 1: Declarar el campo `btnAsignar` y el flag**

Junto a la declaración de `cAccion` (`@FXML private TableColumn<ReparacionResumen, Void> cAccion;`):
```java
    @FXML private javafx.scene.control.Button btnAsignar;
    private boolean soloLectura = false;
```

- [ ] **Step 2: Añadir el método `setSoloLectura`**

Después de `initialize()`:
```java
    /** Activa el modo solo lectura (admin): oculta acciones de escritura. Default false (supertécnico no afectado). */
    public void setSoloLectura(boolean soloLectura) {
        this.soloLectura = soloLectura;
        if (soloLectura) {
            cAccion.setVisible(false);
            if (btnAsignar != null) { btnAsignar.setVisible(false); btnAsignar.setManaged(false); }
            tablaPulidos.refresh();
        }
    }
```

- [ ] **Step 3: Guardar el menú contextual tras el flag**

En el `setOnContextMenuRequested` del `setRowFactory`, añadir como primera instrucción dentro del lambda:
```java
                    if (soloLectura) { e.consume(); return; }
```

- [ ] **Step 4: Mostrar el técnico como texto (no ComboBox) en solo lectura**

En el `cTecnico.setCellFactory`, dentro de `updateItem`, justo después de obtener el `ReparacionResumen rep` de la fila (la línea `ReparacionResumen rep = getTableView().getItems().get(getIndex());`, antes de `cb.getItems().setAll(tecnicos);`), insertar:
```java
                if (soloLectura) {
                    setText(rep.getNombreTecnico() != null ? rep.getNombreTecnico() : "");
                    setGraphic(null);
                    actualizando = false;
                    setStyle("");
                    return;
                }
```

- [ ] **Step 5: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "feat(admin-lectura): setSoloLectura en PulidoSuperTecnicoController (default false)"
```

---

## Task 3: Sidebar + panel de asignaciones en la vista admin

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewAdmin.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

**Interfaces:**
- Consumes: `PendientesSuperTecnicoController.setSoloLectura(boolean)` (Task 1), `PulidoSuperTecnicoController.setSoloLectura(boolean)` (Task 2)

- [ ] **Step 1: Reestructurar el FXML admin a BorderPane con sidebar**

Leer `ReparacionViewAdmin.fxml` completo. Reestructurarlo así:
- La raíz pasa de `<VBox ...>` a `<BorderPane styleClass="vista-container" stylesheets="@../styles/app.css" xmlns:fx=... xmlns=... fx:controller="com.reparaciones.controllers.ReparacionControllerAdmin">`.
- Añadir import `<?import javafx.scene.layout.BorderPane?>`.
- `<left>`: sidebar (mismo estilo que el supertécnico):
```xml
    <left>
        <VBox styleClass="stock-sidebar" spacing="0">
            <Button fx:id="btnTabAsignaciones" text="Asignaciones" maxWidth="Infinity"
                    styleClass="stock-sidebar-btn" onAction="#mostrarAsignaciones"/>
            <Button fx:id="btnTabHistorial" text="Historial" maxWidth="Infinity"
                    styleClass="stock-sidebar-btn-active" onAction="#mostrarHistorial"/>
        </VBox>
    </left>
```
- `<center>`: un `VBox` con `<padding><Insets bottom="40" left="40" right="40" top="40"/></padding>` que contiene un `StackPane VBox.vgrow="ALWAYS"` con DOS paneles:
  - **Panel Historial** `<VBox fx:id="pnlHistorial" visible="true" managed="true" VBox.vgrow="ALWAYS">`: dentro va EXACTAMENTE el contenido actual del admin (el `HBox` del toggle `toggleHistRep`/`toggleHistPul` + el `StackPane` con `pnlHistRep` y `pnlHistPul`), movido sin cambios.
  - **Panel Asignaciones** `<VBox fx:id="pnlAsignaciones" visible="false" managed="false" VBox.vgrow="ALWAYS">`:
```xml
        <VBox fx:id="pnlAsignaciones" visible="false" managed="false" VBox.vgrow="ALWAYS">
            <HBox spacing="0" alignment="CENTER_LEFT">
                <VBox.margin><Insets bottom="8"/></VBox.margin>
                <ToggleButton fx:id="togglePendRep" text="Reparaciones" selected="true" styleClass="toggle-pill-left"/>
                <ToggleButton fx:id="togglePendPul" text="Pulidos" styleClass="toggle-pill-right"/>
            </HBox>
            <StackPane VBox.vgrow="ALWAYS">
                <VBox fx:id="pnlPendRep" VBox.vgrow="ALWAYS">
                    <fx:include source="PendientesSuperTecnicoView.fxml" fx:id="pendientesSuperTecnico"/>
                </VBox>
                <VBox fx:id="pnlPendPul" visible="false" managed="false" VBox.vgrow="ALWAYS">
                    <fx:include source="PulidoSuperTecnicoView.fxml" fx:id="pulidoSuperTecnico"/>
                </VBox>
            </StackPane>
        </VBox>
```
- Mantener el resto de imports existentes (ToggleButton, StackPane, etc.) y añadir `<?import javafx.scene.control.ToggleButton?>` si no estuviera (ya está).

- [ ] **Step 2: Declarar los campos nuevos en el controller admin**

En `ReparacionControllerAdmin.java`, junto a los demás `@FXML`, añadir:
```java
    @FXML private javafx.scene.control.Button btnTabAsignaciones;
    @FXML private javafx.scene.control.Button btnTabHistorial;
    @FXML private VBox pnlHistorial;
    @FXML private VBox pnlAsignaciones;
    @FXML private javafx.scene.control.ToggleButton togglePendRep;
    @FXML private javafx.scene.control.ToggleButton togglePendPul;
    @FXML private VBox pnlPendRep;
    @FXML private VBox pnlPendPul;
    @FXML private PendientesSuperTecnicoController pendientesSuperTecnicoController;
    @FXML private PulidoSuperTecnicoController     pulidoSuperTecnicoController;
```
(El nombre de los controllers inyectados por `fx:include` es `<fx:id>` + `Controller`: `pendientesSuperTecnico` → `pendientesSuperTecnicoController`.)

- [ ] **Step 3: Activar solo lectura y el toggle de asignaciones en `initialize()`**

Al final de `initialize()` de `ReparacionControllerAdmin`, añadir:
```java
        // Modo solo lectura para el admin en los paneles de asignaciones
        pendientesSuperTecnicoController.setSoloLectura(true);
        pulidoSuperTecnicoController.setSoloLectura(true);

        // Toggle Reparaciones/Pulidos dentro de Asignaciones
        javafx.scene.control.ToggleGroup tgPend = new javafx.scene.control.ToggleGroup();
        togglePendRep.setToggleGroup(tgPend);
        togglePendPul.setToggleGroup(tgPend);
        tgPend.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { togglePendRep.setSelected(true); return; }
            boolean rep = (n == togglePendRep);
            pnlPendRep.setVisible(rep);  pnlPendRep.setManaged(rep);
            pnlPendPul.setVisible(!rep); pnlPendPul.setManaged(!rep);
            if (rep) pendientesSuperTecnicoController.cargar();
            else     pulidoSuperTecnicoController.cargar();
        });
```

- [ ] **Step 4: Añadir los métodos de navegación**

Añadir como métodos `@FXML` en `ReparacionControllerAdmin`:
```java
    @FXML
    private void mostrarHistorial() {
        pnlHistorial.setVisible(true);   pnlHistorial.setManaged(true);
        pnlAsignaciones.setVisible(false); pnlAsignaciones.setManaged(false);
        btnTabHistorial.getStyleClass().setAll("stock-sidebar-btn-active");
        btnTabAsignaciones.getStyleClass().setAll("stock-sidebar-btn");
    }

    @FXML
    private void mostrarAsignaciones() {
        pnlAsignaciones.setVisible(true);  pnlAsignaciones.setManaged(true);
        pnlHistorial.setVisible(false);    pnlHistorial.setManaged(false);
        btnTabAsignaciones.getStyleClass().setAll("stock-sidebar-btn-active");
        btnTabHistorial.getStyleClass().setAll("stock-sidebar-btn");
        if (togglePendPul.isSelected()) pulidoSuperTecnicoController.cargar();
        else                            pendientesSuperTecnicoController.cargar();
    }
```

- [ ] **Step 5: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS. Si la carga FXML fallara en runtime por un `fx:id`/método ausente, revisar que los nombres coinciden.

- [ ] **Step 6: Test manual**

Arrancar servidor + cliente, entrar como **admin**:
1. La vista de reparaciones muestra sidebar con **Asignaciones** y **Historial** (sin "Pendientes").
2. **Asignaciones**: tabla de asignaciones generales con toggle Reparaciones/Pulidos. NO hay papelera, NI botón crear; la columna Técnico es texto (no desplegable); clic derecho no abre menú de editar/urgente.
3. **Historial**: funciona igual que antes (toggle Reparaciones/Pulidos, agrupado/plano, etc.).

Y como **supertécnico** (regresión crítica):
4. Su panel de asignaciones sigue idéntico: papelera, crear, reasignar (desplegable de técnico), editar comentario y marcar urgente, todo operativo.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewAdmin.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java
git commit -m "feat(admin-lectura): sidebar Historial/Asignaciones en vista admin, asignaciones en solo lectura"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Task |
|---|---|
| `setSoloLectura(boolean)` default false en PendientesSuperTecnicoController | Task 1 |
| Oculta papelera (cAccion) | Task 1 |
| Oculta botón crear (btnAsignar) | Task 1 |
| Desactiva menú contextual (editar comentario, urgente) | Task 1 (guard) |
| Desactiva reasignar inline (ComboBox técnico → texto) | Task 1 |
| Lo mismo en PulidoSuperTecnicoController | Task 2 |
| Sidebar Historial/Asignaciones en vista admin (sin "Pendientes") | Task 3 |
| Asignaciones embebidas (rep. + pulido) con toggle | Task 3 |
| Admin activa setSoloLectura(true) en ambos sub-controllers | Task 3 |
| Historial del admin sin cambios funcionales | Task 3 (se mueve íntegro a pnlHistorial) |
| Supertécnico intacto (no llama setSoloLectura) | Tasks 1, 2 (flag default false) |
| Solo cliente, sin servidor/BD | Todo el plan |

### Notas
- Sin TDD (UI); verificación por compilación + manual, incluida la **regresión del supertécnico** (Task 3 Step 6 punto 4), que es el riesgo principal del cambio quirúrgico.
- El orden en `initialize` del admin importa: `setSoloLectura(true)` se llama antes de la primera `cargar()` (disparada por la navegación), de modo que las filas se crean ya en modo lectura.
