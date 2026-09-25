# MultiSelectDropdown Helper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crear la clase utilitaria `MultiSelectDropdown` que centraliza el boilerplate de los 8 dropdowns MultiSelectComboBox y corrige el bug del estado `:selected` persistente en todos ellos.

**Architecture:** Clase estática `MultiSelectDropdown` en `utils/` con dos overloads de `setup()` (estándar y custom cell factory) y una clase `Handle` que expone `refresh()`. Idempotente via `combo.getUserData()`. El bug `:selected` se corrige con un event filter en `MOUSE_RELEASED` + `Platform.runLater`. Los controladores mantienen su estado (`Set`, `StringProperty`) y solo delegan la infraestructura UI al helper.

**Tech Stack:** JavaFX 17, `java.util.function` (`Function`, `Predicate`, `BiConsumer`), `javafx.util.Callback`.

---

## Files

| Fichero | Cambio |
|---------|--------|
| `src/main/java/com/reparaciones/utils/MultiSelectDropdown.java` | **Crear** |
| `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/EstadisticasController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/StockController.java` | Modificar |

`MultiSelectComboBox.java` NO se modifica.

---

### Task 1: Crear `MultiSelectDropdown.java`

**Files:**
- Create: `src/main/java/com/reparaciones/utils/MultiSelectDropdown.java`

**Contexto:**  
La clase centraliza:
1. ButtonCell que muestra el `StringProperty etiqueta`
2. `ListView<T>` + `VBox` con clases CSS `combo-box-popup` y `multi-select-popup`
3. `Popup` con `autoHide`
4. Cell factory estándar (CheckBox + toggle via `setOnMouseClicked`)
5. Event filter en `MOUSE_RELEASED` que limpia la selección del ListView (fix del bug visual del estado `:selected` persistente)
6. Idempotencia: la primera vez crea la infraestructura, las siguientes solo actualiza ítems + `maxHeight` + `refresh`. El pivote de idempotencia es `combo.getUserData()`: nulo = primera vez, instancia de `ListView` = ya inicializado.

- [ ] **Step 1: Crear el fichero**

Crea `src/main/java/com/reparaciones/utils/MultiSelectDropdown.java` con el siguiente contenido exacto:

```java
package com.reparaciones.utils;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Callback;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class MultiSelectDropdown {

    private MultiSelectDropdown() {}

    // ── Handle ────────────────────────────────────────────────────────────────

    public static final class Handle {
        private final ListView<?> listView;
        Handle(ListView<?> lv) { this.listView = lv; }
        public void refresh() { listView.refresh(); }
    }

    // ── Overload estándar (checkbox automático) ───────────────────────────────

    public static <T> Handle setup(
            MultiSelectComboBox<T> combo,
            List<T> items,
            Function<T, String> displayText,
            Predicate<T> isSelected,
            BiConsumer<T, Boolean> onToggle,
            StringProperty etiqueta) {

        ListView<T> listView = getOrCreate(combo);

        if (combo.getUserData() == null) {
            combo.setUserData(listView);
            listView.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        T item = getItem();
                        if (item == null) return;
                        boolean nowChecked = !isSelected.test(item);
                        onToggle.accept(item, nowChecked);
                        listView.refresh();
                    });
                }
                @Override protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(isSelected.test(item));
                    setGraphic(check);
                    setText(displayText.apply(item));
                }
            });
            setupPopupAndButton(combo, listView, etiqueta);
        }

        listView.getItems().setAll(items);
        listView.setMaxHeight(items.size() * 30.0 + 4);
        listView.refresh();
        return new Handle(listView);
    }

    // ── Overload custom cell factory (EstadisticasController) ─────────────────

    public static <T> Handle setup(
            MultiSelectComboBox<T> combo,
            List<T> items,
            Callback<ListView<T>, ListCell<T>> cellFactory,
            StringProperty etiqueta) {

        ListView<T> listView = getOrCreate(combo);

        if (combo.getUserData() == null) {
            combo.setUserData(listView);
            listView.setCellFactory(cellFactory);
            setupPopupAndButton(combo, listView, etiqueta);
        }

        listView.getItems().setAll(items);
        listView.setMaxHeight(items.size() * 30.0 + 4);
        listView.refresh();
        return new Handle(listView);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> ListView<T> getOrCreate(MultiSelectComboBox<T> combo) {
        if (combo.getUserData() instanceof ListView<?> lv) {
            return (ListView<T>) lv;
        }
        ListView<T> listView = new ListView<>();
        // Fix bug :selected persistente — limpia la selección tras cada clic
        listView.addEventFilter(MouseEvent.MOUSE_RELEASED,
                e -> Platform.runLater(() -> listView.getSelectionModel().clearSelection()));
        return listView;
    }

    private static <T> void setupPopupAndButton(
            MultiSelectComboBox<T> combo, ListView<T> listView, StringProperty etiqueta) {

        combo.setButtonCell(new ListCell<>() {
            {
                etiqueta.addListener((obs, o, n) -> Platform.runLater(() -> setText(n)));
                Platform.runLater(() -> setText(etiqueta.get()));
            }
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, false);
                Platform.runLater(() -> setText(etiqueta.get()));
            }
        });

        VBox contenedor = new VBox(listView);
        contenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
        contenedor.setPrefWidth(combo.getPrefWidth());
        contenedor.setMaxWidth(combo.getPrefWidth());

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(contenedor);
        combo.setCustomPopup(popup);
    }
}
```

- [ ] **Step 2: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 3: Commit**

```
git add src/main/java/com/reparaciones/utils/MultiSelectDropdown.java
git commit -m "feat: clase utilitaria MultiSelectDropdown — centraliza boilerplate y corrige bug :selected"
```

---

### Task 2: Migrar `PendientesSuperTecnicoController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Contexto:**  
Hay un único `MultiSelectComboBox<Tecnico> filtroTecnico`. La lista de técnicos es el campo `tecnicos` (ya poblado con `tecnicoDAO.getAllActivos()` en `configurarFiltros()`).

- [ ] **Step 1: Cambiar campo `listaTecFiltro` → `filtroTecHandle`**

Localiza (línea ~81):
```java
private ListView<Tecnico>    listaTecFiltro;
```
Sustitúyelo por:
```java
private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `configurarFiltros()`**

Localiza el bloque exacto (líneas ~434–472) dentro del `try`:
```java
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTec.get())); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, false); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
            listaTecFiltro.setMaxHeight(Math.min(tecnicos.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setPrefWidth(filtroTecnico.getPrefWidth());
            popupContenedor.setMaxWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicos,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
```

- [ ] **Step 3: Actualizar `limpiarFiltros()`**

Localiza (línea ~588):
```java
        if (listaTecFiltro != null) listaTecFiltro.refresh();
```
Sustitúyelo por:
```java
        if (filtroTecHandle != null) filtroTecHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "refactor: migrar filtroTecnico de PendientesSuperTecnicoController a MultiSelectDropdown"
```

---

### Task 3: Migrar `PulidoSuperTecnicoController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`

**Contexto:**  
Patrón idéntico a Task 2. Lista de técnicos: campo `tecnicos`.

- [ ] **Step 1: Cambiar campo `listaTecFiltro` → `filtroTecHandle`**

Localiza (línea ~62):
```java
private ListView<Tecnico>    listaTecFiltro;
```
Sustitúyelo por:
```java
private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `configurarFiltros()`**

Localiza el bloque exacto (líneas ~314–352) dentro del `try`:
```java
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTec.get())); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, false); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
            listaTecFiltro.setMaxHeight(Math.min(tecnicos.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setPrefWidth(filtroTecnico.getPrefWidth());
            popupContenedor.setMaxWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicos,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
```

- [ ] **Step 3: Actualizar `limpiarFiltros()`**

Localiza (línea ~405):
```java
        if (listaTecFiltro != null) listaTecFiltro.refresh();
```
Sustitúyelo por:
```java
        if (filtroTecHandle != null) filtroTecHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "refactor: migrar filtroTecnico de PulidoSuperTecnicoController a MultiSelectDropdown"
```

---

### Task 4: Migrar `HistorialPulidoController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java`

**Contexto:**  
Patrón idéntico a Task 2. Lista de técnicos: campo `tecnicos`.

- [ ] **Step 1: Cambiar campo `listaTecFiltro` → `filtroTecHandle`**

Localiza (línea ~55):
```java
private ListView<Tecnico>      listaTecFiltro;
```
Sustitúyelo por:
```java
private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `configurarFiltros()`**

Localiza el bloque exacto (líneas ~175–213) dentro del `try`:
```java
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTec.get())); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, false); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
            listaTecFiltro.setMaxHeight(Math.min(tecnicos.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setPrefWidth(filtroTecnico.getPrefWidth());
            popupContenedor.setMaxWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicos,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
```

- [ ] **Step 3: Actualizar `limpiarFiltros()`**

Localiza (línea ~278):
```java
        if (listaTecFiltro != null) listaTecFiltro.refresh();
```
Sustitúyelo por:
```java
        if (filtroTecHandle != null) filtroTecHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/HistorialPulidoController.java
git commit -m "refactor: migrar filtroTecnico de HistorialPulidoController a MultiSelectDropdown"
```

---

### Task 5: Migrar `ReparacionControllerAdmin`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

**Contexto:**  
Patrón idéntico a Task 2, con una diferencia: la lista de técnicos se llama `tecnicosLista` (no `tecnicos`) y se popula con `tecnicoDAO.getAll()` (incluye inactivos — es historial).

- [ ] **Step 1: Cambiar campo `listaTecFiltro` → `filtroTecHandle`**

Localiza (línea ~103):
```java
private ListView<Tecnico>    listaTecFiltro;
```
Sustitúyelo por:
```java
private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `configurarFiltros()`**

Localiza el bloque exacto (líneas ~558–596) dentro del `try`:
```java
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTec.get())); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, false); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicosLista));
            listaTecFiltro.setMaxHeight(Math.min(tecnicosLista.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setPrefWidth(filtroTecnico.getPrefWidth());
            popupContenedor.setMaxWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicosLista,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
```

- [ ] **Step 3: Actualizar `limpiarFiltros()`**

Localiza (línea ~851):
```java
        if (listaTecFiltro != null) listaTecFiltro.refresh();
```
Sustitúyelo por:
```java
        if (filtroTecHandle != null) filtroTecHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java
git commit -m "refactor: migrar filtroTecnico de ReparacionControllerAdmin a MultiSelectDropdown"
```

---

### Task 6: Migrar `ReparacionControllerSuperTecnico`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`

**Contexto:**  
Idéntico a Task 5. Lista: `tecnicosLista`. DAO: `tecnicoDAO.getAll()`.

- [ ] **Step 1: Cambiar campo `listaTecFiltro` → `filtroTecHandle`**

Localiza (línea ~133):
```java
private ListView<Tecnico>    listaTecFiltro;
```
Sustitúyelo por:
```java
private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `configurarFiltros()`**

Localiza el bloque exacto (líneas ~805–843) dentro del `try`:
```java
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTec.get())); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, false); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicosLista));
            listaTecFiltro.setMaxHeight(Math.min(tecnicosLista.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setPrefWidth(filtroTecnico.getPrefWidth());
            popupContenedor.setMaxWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicosLista,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
```

- [ ] **Step 3: Actualizar `limpiarFiltros()`**

Localiza (línea ~1117):
```java
        if (listaTecFiltro != null) listaTecFiltro.refresh();
```
Sustitúyelo por:
```java
        if (filtroTecHandle != null) filtroTecHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
git commit -m "refactor: migrar filtroTecnico de ReparacionControllerSuperTecnico a MultiSelectDropdown"
```

---

### Task 7: Migrar `EstadisticasController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/EstadisticasController.java`

**Contexto:**  
Este controlador usa el overload **custom cell factory** porque sus celdas tienen colores por técnico y un separador `null` entre activos e inactivos. La lista es `todosLosTecnicos` (contiene `null` como separador). El campo de selección es `nombresSeleccionadosTec` (`Set<String>`), no `idsTecFiltro`.

Importante: el `setOnMouseClicked` dentro de la cell factory actualmente llama a `listaTecnicos.refresh()`. Después del refactor, deberá usar `filtroTecHandle.refresh()`. Como `filtroTecHandle` es un campo de instancia que se asigna antes de que el usuario pueda hacer clic, la referencia es segura.

- [ ] **Step 1: Cambiar campo `listaTecnicos` → `filtroTecHandle`**

Localiza (línea ~97):
```java
    private ListView<Tecnico>           listaTecnicos;
```
Sustitúyelo por:
```java
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
```

- [ ] **Step 2: Reemplazar el bloque de setup en `cargarTecnicos()`**

Localiza el bloque exacto (líneas ~229–286):
```java
        menuTecnicos.setButtonCell(new ListCell<>() {
            { etiquetaTecs.addListener((obs, o, n) -> setText(n)); javafx.application.Platform.runLater(() -> setText(etiquetaTecs.get())); }
            @Override protected void updateItem(Tecnico t, boolean empty) {
                super.updateItem(t, false); setText(etiquetaTecs.get());
            }
        });

        listaTecnicos = new ListView<>(FXCollections.observableArrayList(todosLosTecnicos));
        listaTecnicos.setMaxHeight(Math.min(todosLosTecnicos.size(), 10) * 30.0);
        listaTecnicos.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setMouseTransparent(true);
                check.setFocusTraversable(false);
                setOnMouseClicked(e -> {
                    if (getItem() == null) return;
                    String nombre = getItem().getNombre();
                    if (nombresSeleccionadosTec.contains(nombre)) nombresSeleccionadosTec.remove(nombre);
                    else nombresSeleccionadosTec.add(nombre);
                    listaTecnicos.refresh();
                    actualizarTextoMenuTecnicos();
                    renderVentana((int) sliderVentana.getValue());
                });
            }
            @Override protected void updateItem(Tecnico t, boolean empty) {
                super.updateItem(t, empty);
                if (empty) { setGraphic(null); setText(null); setStyle(""); return; }
                if (t == null) {
                    // separador entre activos e inactivos
                    setGraphic(null);
                    setText(null);
                    setMouseTransparent(true);
                    setStyle("-fx-border-color: transparent transparent #AAAAAA transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 0 0; -fx-pref-height: 8;");
                    return;
                }
                setMouseTransparent(false);
                boolean activo = t.isActivo();
                check.setSelected(nombresSeleccionadosTec.contains(t.getNombre()));
                setGraphic(check);
                String colorHex = coloresPorNombre.getOrDefault(t.getNombre(), "#888888");
                if (activo) {
                    setText(t.getNombre());
                    setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
                } else {
                    setText(t.getNombre() + " (inactivo)");
                    setStyle("-fx-text-fill: #9A9A9A; -fx-font-style: italic;");
                }
            }
        });

        VBox popupContenedor = new VBox(listaTecnicos);
        popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
        popupContenedor.setPrefWidth(menuTecnicos.getPrefWidth());
        popupContenedor.setMaxWidth(menuTecnicos.getPrefWidth());
        Popup popupTec = new Popup();
        popupTec.setAutoHide(true);
        popupTec.getContent().add(popupContenedor);
        menuTecnicos.setCustomPopup(popupTec);
```
Sustitúyelo por:
```java
        filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            menuTecnicos,
            todosLosTecnicos,
            lv -> new ListCell<Tecnico>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        String nombre = getItem().getNombre();
                        if (nombresSeleccionadosTec.contains(nombre)) nombresSeleccionadosTec.remove(nombre);
                        else nombresSeleccionadosTec.add(nombre);
                        filtroTecHandle.refresh();
                        actualizarTextoMenuTecnicos();
                        renderVentana((int) sliderVentana.getValue());
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty) { setGraphic(null); setText(null); setStyle(""); return; }
                    if (t == null) {
                        setGraphic(null); setText(null);
                        setMouseTransparent(true);
                        setStyle("-fx-border-color: transparent transparent #AAAAAA transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 0 0; -fx-pref-height: 8;");
                        return;
                    }
                    setMouseTransparent(false);
                    boolean activo = t.isActivo();
                    check.setSelected(nombresSeleccionadosTec.contains(t.getNombre()));
                    setGraphic(check);
                    String colorHex = coloresPorNombre.getOrDefault(t.getNombre(), "#888888");
                    if (activo) {
                        setText(t.getNombre());
                        setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
                    } else {
                        setText(t.getNombre() + " (inactivo)");
                        setStyle("-fx-text-fill: #9A9A9A; -fx-font-style: italic;");
                    }
                }
            },
            etiquetaTecs);
```

- [ ] **Step 3: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 4: Commit**

```
git add src/main/java/com/reparaciones/controllers/EstadisticasController.java
git commit -m "refactor: migrar menuTecnicos de EstadisticasController a MultiSelectDropdown (custom cell)"
```

---

### Task 8: Migrar `StockController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/StockController.java`

**Contexto:**  
Hay dos `MultiSelectComboBox<Proveedor>`: `menuFiltroProveedores` y `menuFiltroProveedorPedidos`. El método `poblarFiltrosProveedor()` se llama en cada recarga (cada 60 segundos). El helper es idempotente, por lo que el `if (listaProvStock == null)` desaparece. Los campos `listaProvStock` y `listaProvPedidos` se eliminan; se añaden `filtroProvStockHandle` y `filtroProvPedidosHandle`. La selección vive en `seleccionadosProv` y `seleccionadosPedidos` (campos `Set<String>` existentes, no se modifican).

- [ ] **Step 1: Sustituir campos `listaProvStock` y `listaProvPedidos`**

Localiza (líneas ~112–113):
```java
    private javafx.scene.control.ListView<Proveedor> listaProvStock;
    private javafx.scene.control.ListView<Proveedor> listaProvPedidos;
```
Sustitúyelo por:
```java
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroProvStockHandle;
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroProvPedidosHandle;
```

- [ ] **Step 2: Reemplazar el cuerpo de `poblarFiltrosProveedor()`**

Localiza el método completo (líneas ~1230–1322):
```java
    private void poblarFiltrosProveedor() {
        java.util.List<Proveedor> activos = datosProveedores.stream()
                .filter(Proveedor::isActivo)
                .collect(java.util.stream.Collectors.toList());

        if (listaProvStock == null) {
            // ── Setup menuFiltroProveedores ──────────────────────────────────
            listaProvStock = new javafx.scene.control.ListView<>();
            listaProvStock.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                private final CheckBox cb = new CheckBox();
                { setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY); }
                @Override protected void updateItem(Proveedor p, boolean empty) {
                    super.updateItem(p, empty);
                    if (empty || p == null) { setGraphic(null); return; }
                    cb.setText(p.getNombre());
                    cb.setSelected(seleccionadosProv.contains(p.getNombre()));
                    cb.setOnAction(e -> {
                        if (cb.isSelected()) seleccionadosProv.add(p.getNombre());
                        else                 seleccionadosProv.remove(p.getNombre());
                        actualizarTextoFiltroProveedor(etiquetaProvStock, seleccionadosProv);
                        aplicarFiltroProveedores();
                    });
                    setGraphic(cb);
                }
            });
            menuFiltroProveedores.setButtonCell(new javafx.scene.control.ListCell<>() {
                {
                    etiquetaProvStock.addListener((obs, o, n) ->
                            javafx.application.Platform.runLater(() -> setText(n)));
                    javafx.application.Platform.runLater(() -> setText(etiquetaProvStock.get()));
                }
                @Override protected void updateItem(Proveedor p, boolean empty) {
                    super.updateItem(p, empty);
                    javafx.application.Platform.runLater(() -> setText(etiquetaProvStock.get()));
                }
            });
            javafx.scene.layout.VBox contenedorProv = new javafx.scene.layout.VBox(listaProvStock);
            contenedorProv.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            contenedorProv.setPrefWidth(menuFiltroProveedores.getPrefWidth());
            contenedorProv.setMaxWidth(menuFiltroProveedores.getPrefWidth());
            javafx.stage.Popup popupProv = new javafx.stage.Popup();
            popupProv.setAutoHide(true);
            popupProv.getContent().add(contenedorProv);
            menuFiltroProveedores.setCustomPopup(popupProv);

            // ── Setup menuFiltroProveedorPedidos ─────────────────────────────
            listaProvPedidos = new javafx.scene.control.ListView<>();
            listaProvPedidos.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                private final CheckBox cb = new CheckBox();
                { setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY); }
                @Override protected void updateItem(Proveedor p, boolean empty) {
                    super.updateItem(p, empty);
                    if (empty || p == null) { setGraphic(null); return; }
                    cb.setText(p.getNombre());
                    cb.setSelected(seleccionadosPedidos.contains(p.getNombre()));
                    cb.setOnAction(e -> {
                        if (cb.isSelected()) seleccionadosPedidos.add(p.getNombre());
                        else                 seleccionadosPedidos.remove(p.getNombre());
                        actualizarTextoFiltroProveedor(etiquetaProvPedidos, seleccionadosPedidos);
                        aplicarFiltroPedidosProveedor();
                    });
                    setGraphic(cb);
                }
            });
            menuFiltroProveedorPedidos.setButtonCell(new javafx.scene.control.ListCell<>() {
                {
                    etiquetaProvPedidos.addListener((obs, o, n) ->
                            javafx.application.Platform.runLater(() -> setText(n)));
                    javafx.application.Platform.runLater(() -> setText(etiquetaProvPedidos.get()));
                }
                @Override protected void updateItem(Proveedor p, boolean empty) {
                    super.updateItem(p, empty);
                    javafx.application.Platform.runLater(() -> setText(etiquetaProvPedidos.get()));
                }
            });
            javafx.scene.layout.VBox contenedorPedidos = new javafx.scene.layout.VBox(listaProvPedidos);
            contenedorPedidos.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            contenedorPedidos.setPrefWidth(menuFiltroProveedorPedidos.getPrefWidth());
            contenedorPedidos.setMaxWidth(menuFiltroProveedorPedidos.getPrefWidth());
            javafx.stage.Popup popupPedidos = new javafx.stage.Popup();
            popupPedidos.setAutoHide(true);
            popupPedidos.getContent().add(contenedorPedidos);
            menuFiltroProveedorPedidos.setCustomPopup(popupPedidos);
        }

        listaProvStock.getItems().setAll(activos);
        listaProvStock.setMaxHeight(activos.size() * 30.0 + 4);
        listaProvStock.refresh();

        listaProvPedidos.getItems().setAll(activos);
        listaProvPedidos.setMaxHeight(activos.size() * 30.0 + 4);
        listaProvPedidos.refresh();
    }
```
Sustitúyelo por:
```java
    private void poblarFiltrosProveedor() {
        java.util.List<Proveedor> activos = datosProveedores.stream()
                .filter(Proveedor::isActivo)
                .collect(java.util.stream.Collectors.toList());

        filtroProvStockHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            menuFiltroProveedores, activos,
            Proveedor::getNombre,
            p -> seleccionadosProv.contains(p.getNombre()),
            (p, checked) -> { if (checked) seleccionadosProv.add(p.getNombre());
                              else         seleccionadosProv.remove(p.getNombre());
                              actualizarTextoFiltroProveedor(etiquetaProvStock, seleccionadosProv);
                              aplicarFiltroProveedores(); },
            etiquetaProvStock);

        filtroProvPedidosHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            menuFiltroProveedorPedidos, activos,
            Proveedor::getNombre,
            p -> seleccionadosPedidos.contains(p.getNombre()),
            (p, checked) -> { if (checked) seleccionadosPedidos.add(p.getNombre());
                              else         seleccionadosPedidos.remove(p.getNombre());
                              actualizarTextoFiltroProveedor(etiquetaProvPedidos, seleccionadosPedidos);
                              aplicarFiltroPedidosProveedor(); },
            etiquetaProvPedidos);
    }
```

- [ ] **Step 3: Actualizar `limpiarFiltrosPedidos()`**

Localiza (línea ~251):
```java
        if (listaProvPedidos != null) listaProvPedidos.refresh();
```
Sustitúyelo por:
```java
        if (filtroProvPedidosHandle != null) filtroProvPedidosHandle.refresh();
```

- [ ] **Step 4: Compilar**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/reparaciones/controllers/StockController.java
git commit -m "refactor: migrar filtros proveedor de StockController a MultiSelectDropdown"
```
