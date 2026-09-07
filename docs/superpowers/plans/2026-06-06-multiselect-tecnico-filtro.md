# Multi-Select Filtro Técnico Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el `MenuButton filtroTecnico` de los 3 controladores de supertécnico por un multi-select que hereda al 100% el look del ComboBox existente sin CSS nuevo.

**Architecture:** Se crea `MultiSelectComboBox<T>` en `utils/`, que extiende `ComboBox<T>` y sobreescribe `show()` para mostrar un `Popup` propio con un `ListView` estilizado con `combo-box-popup`. El resultado es visualmente idéntico al selector único de técnico (mismo botón, mismo popup, mismo hover, mismo scroll) con la diferencia de que cada celda lleva un `CheckBox` y los clics togglean selección sin cerrar el popup.

**Tech Stack:** JavaFX 25, Java 21, CSS existente (cero líneas nuevas en app.css excepto 4 para el checkbox en hover), Maven.

---

## File Structure

| Acción | Ruta |
|--------|------|
| CREAR  | `src/main/java/com/reparaciones/utils/MultiSelectComboBox.java` |
| CREAR  | `src/main/resources/styles/app.css` (+4 líneas al final de sección ComboBox) |
| MODIFICAR | `src/main/resources/views/PendientesSuperTecnicoView.fxml` |
| MODIFICAR | `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` |
| MODIFICAR | `src/main/resources/views/PulidoSuperTecnicoView.fxml` |
| MODIFICAR | `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java` |
| MODIFICAR | `src/main/resources/views/HistorialPulidoView.fxml` |
| MODIFICAR | `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java` |

---

### Task 1: MultiSelectComboBox + CSS

**Files:**
- Create: `src/main/java/com/reparaciones/utils/MultiSelectComboBox.java`
- Modify: `src/main/resources/styles/app.css` (añadir al final de la sección `/* ── 7. ComboBox */`, línea ~376)

- [ ] **Step 1: Crear `MultiSelectComboBox.java`**

Contenido completo del fichero:

```java
package com.reparaciones.utils;

import javafx.scene.control.ComboBox;
import javafx.stage.Popup;

/**
 * ComboBox cuyo popup nativo es reemplazado por un Popup propio,
 * permitiendo multi-selección sin cerrar al hacer clic.
 */
public class MultiSelectComboBox<T> extends ComboBox<T> {

    private Popup customPopup;

    public void setCustomPopup(Popup popup) {
        this.customPopup = popup;
    }

    @Override
    public void show() {
        if (customPopup == null) { super.show(); return; }
        if (customPopup.isShowing()) {
            customPopup.hide();
        } else {
            javafx.geometry.Bounds b = localToScreen(getBoundsInLocal());
            customPopup.show(this, b.getMinX(), b.getMaxY() + 4);
        }
    }
}
```

- [ ] **Step 2: Añadir CSS para checkbox visible en hover/selected**

En `app.css`, inmediatamente después del bloque `.combo-box-popup .list-view .list-cell:filled:hover, .combo-box-popup .list-view .list-cell:filled:selected { ... }` (actualmente termina en línea ~376), añadir:

```css
.combo-box-popup .list-view .list-cell:filled:hover .check-box .box,
.combo-box-popup .list-view .list-cell:filled:selected .check-box .box {
    -fx-background-color: #FFFFFF;
    -fx-border-color: #FFFFFF;
}
```

Esto hace que el borde y fondo del cuadro del checkbox sean blancos cuando la celda tiene fondo navy, para que se vea el checkmark.

- [ ] **Step 3: Compilar para verificar que no hay errores**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```
Esperado: BUILD SUCCESS sin errores.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/utils/MultiSelectComboBox.java
git add src/main/resources/styles/app.css
git commit -m "feat: MultiSelectComboBox — extiende ComboBox con popup propio y CSS hover checkbox"
```

---

### Task 2: PendientesSuperTecnico — FXML + Controller

**Files:**
- Modify: `src/main/resources/views/PendientesSuperTecnicoView.fxml`
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

- [ ] **Step 1: Actualizar FXML**

En `PendientesSuperTecnicoView.fxml`, cambiar la línea del `MenuButton filtroTecnico`:

```xml
<!-- FROM: -->
<MenuButton fx:id="filtroTecnico"   text="Técnico" prefWidth="130"/>

<!-- TO: -->
<com.reparaciones.utils.MultiSelectComboBox fx:id="filtroTecnico" prefWidth="130"/>
```

- [ ] **Step 2: Actualizar imports en el controller**

En `PendientesSuperTecnicoController.java`, añadir estos imports (después de los existentes):

```java
import com.reparaciones.utils.MultiSelectComboBox;
import java.util.HashSet;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Popup;
```

- [ ] **Step 3: Cambiar el campo `filtroTecnico` y reemplazar `cbsTecnico`**

Buscar y reemplazar en los campos del controller (zona de `@FXML` y `private final List<CheckBox> cbsTecnico`):

```java
// FROM (línea ~54):
@FXML private MenuButton filtroTecnico;
// FROM (línea ~73):
private final List<CheckBox>        cbsTecnico       = new ArrayList<>();

// TO:
@FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
private final Set<Integer>   idsTecFiltro  = new HashSet<>();
private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
private ListView<Tecnico>    listaTecFiltro;
```

- [ ] **Step 4: Reemplazar el bloque filtroTecnico en `configurarFiltros()`**

En `configurarFiltros()`, el bloque actual (líneas ~424-436):

```java
// FROM:
tecnicos.addAll(tecnicoDAO.getAllActivos());
for (Tecnico t : tecnicos) {
    CheckBox cb = new CheckBox(t.getNombre());
    cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
    cb.selectedProperty().addListener((obs, o, n) -> {
        actualizarTextoFiltroTecnico();
        aplicarFiltros();
    });
    cbsTecnico.add(cb);
    CustomMenuItem item = new CustomMenuItem(cb, false);
    filtroTecnico.getItems().add(item);
}
```

```java
// TO:
tecnicos.addAll(tecnicoDAO.getAllActivos());
filtroTecnico.setButtonCell(new ListCell<>() {
    { etiquetaTec.addListener((obs, o, n) -> setText(n)); setText(etiquetaTec.get()); }
    @Override protected void updateItem(Tecnico t, boolean empty) {
        super.updateItem(t, empty); setText(etiquetaTec.get());
    }
});
listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
listaTecFiltro.setVisibleRowCount(Math.min(tecnicos.size(), 8));
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
popupContenedor.getStyleClass().add("combo-box-popup");
Popup popupTec = new Popup();
popupTec.setAutoHide(true);
popupTec.getContent().add(popupContenedor);
filtroTecnico.setCustomPopup(popupTec);
```

- [ ] **Step 5: Actualizar `actualizarTextoFiltroTecnico()`**

```java
// FROM:
private void actualizarTextoFiltroTecnico() {
    long sel = cbsTecnico.stream().filter(CheckBox::isSelected).count();
    filtroTecnico.setText(sel == 0 ? "Técnico" : sel == 1
            ? cbsTecnico.stream().filter(CheckBox::isSelected)
                    .findFirst().map(CheckBox::getText).orElse("Técnico")
            : sel + " técnicos");
}

// TO:
private void actualizarTextoFiltroTecnico() {
    List<String> nombres = tecnicos.stream()
            .filter(t -> idsTecFiltro.contains(t.getIdTec()))
            .map(Tecnico::getNombre).toList();
    etiquetaTec.set(nombres.isEmpty() ? "Técnico"
            : nombres.size() == 1 ? nombres.get(0)
            : nombres.size() + " técnicos");
}
```

- [ ] **Step 6: Actualizar `aplicarFiltros()`**

Dentro de `aplicarFiltros()`, reemplazar las dos líneas del bucle cbsTecnico:

```java
// FROM:
List<Integer> idsTecSelec = new ArrayList<>();
for (int i = 0; i < cbsTecnico.size(); i++)
    if (cbsTecnico.get(i).isSelected()) idsTecSelec.add(tecnicos.get(i).getIdTec());

// TO:
List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
```

- [ ] **Step 7: Actualizar `limpiarFiltros()`**

```java
// FROM:
cbsTecnico.forEach(cb -> cb.setSelected(false));
filtroTecnico.setText("Técnico");

// TO:
idsTecFiltro.clear();
etiquetaTec.set("Técnico");
if (listaTecFiltro != null) listaTecFiltro.refresh();
```

- [ ] **Step 8: Compilar**

```bash
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 9: Prueba manual**

Arrancar la app como supertécnico → vista "Asignaciones pendientes":
1. El botón "Técnico" tiene el mismo aspecto que otros ComboBox de la app (navy, redondeado, flecha blanca).
2. Al hacer clic abre el dropdown con la lista de técnicos.
3. Se puede seleccionar/deseleccionar varios sin que se cierre el dropdown.
4. El texto del botón cambia: "Técnico" → nombre si 1 selec. → "N técnicos" si varios.
5. "Limpiar filtros" desmarca todo y resetea el texto.
6. El hover muestra fondo navy + texto blanco, el checkbox permanece visible.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/views/PendientesSuperTecnicoView.fxml
git add src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat: multi-select filtro tecnico en PendientesSuperTecnico via ComboBox+Popup"
```

---

### Task 3: PulidoSuperTecnico — FXML + Controller

**Files:**
- Modify: `src/main/resources/views/PulidoSuperTecnicoView.fxml`
- Modify: `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`

- [ ] **Step 1: Actualizar FXML**

En `PulidoSuperTecnicoView.fxml`:

```xml
<!-- FROM: -->
<MenuButton fx:id="filtroTecnico" text="Técnico" prefWidth="130"/>

<!-- TO: -->
<com.reparaciones.utils.MultiSelectComboBox fx:id="filtroTecnico" prefWidth="130"/>
```

- [ ] **Step 2: Actualizar imports**

Añadir en `PulidoSuperTecnicoController.java`:

```java
import com.reparaciones.utils.MultiSelectComboBox;
import java.util.HashSet;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Popup;
```

- [ ] **Step 3: Cambiar campo `filtroTecnico` y `cbsTecnico`**

```java
// FROM (línea ~40):
@FXML private MenuButton filtroTecnico;
// FROM (línea ~54):
private final List<CheckBox> cbsTecnico = new ArrayList<>();

// TO:
@FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
private final Set<Integer>   idsTecFiltro  = new HashSet<>();
private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
private ListView<Tecnico>    listaTecFiltro;
```

- [ ] **Step 4: Reemplazar bloque filtroTecnico en `configurarFiltros()`**

El bloque actual (líneas ~303-316):

```java
// FROM:
tecnicos.addAll(tecnicoDAO.getAllActivos());
for (Tecnico t : tecnicos) {
    CheckBox cb = new CheckBox(t.getNombre());
    cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
    cb.selectedProperty().addListener((obs, o, n) -> {
        actualizarTextoFiltroTecnico();
        aplicarFiltros();
    });
    cbsTecnico.add(cb);
    CustomMenuItem item = new CustomMenuItem(cb, false);
    filtroTecnico.getItems().add(item);
}
```

```java
// TO:
tecnicos.addAll(tecnicoDAO.getAllActivos());
filtroTecnico.setButtonCell(new ListCell<>() {
    { etiquetaTec.addListener((obs, o, n) -> setText(n)); setText(etiquetaTec.get()); }
    @Override protected void updateItem(Tecnico t, boolean empty) {
        super.updateItem(t, empty); setText(etiquetaTec.get());
    }
});
listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
listaTecFiltro.setVisibleRowCount(Math.min(tecnicos.size(), 8));
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
popupContenedor.getStyleClass().add("combo-box-popup");
Popup popupTec = new Popup();
popupTec.setAutoHide(true);
popupTec.getContent().add(popupContenedor);
filtroTecnico.setCustomPopup(popupTec);
```

- [ ] **Step 5: Actualizar `actualizarTextoFiltroTecnico()`**

```java
// FROM:
private void actualizarTextoFiltroTecnico() {
    long sel = cbsTecnico.stream().filter(CheckBox::isSelected).count();
    filtroTecnico.setText(sel == 0 ? "Técnico" : sel == 1
            ? cbsTecnico.stream().filter(CheckBox::isSelected).findFirst().map(CheckBox::getText).orElse("Técnico")
            : sel + " técnicos");
}

// TO:
private void actualizarTextoFiltroTecnico() {
    List<String> nombres = tecnicos.stream()
            .filter(t -> idsTecFiltro.contains(t.getIdTec()))
            .map(Tecnico::getNombre).toList();
    etiquetaTec.set(nombres.isEmpty() ? "Técnico"
            : nombres.size() == 1 ? nombres.get(0)
            : nombres.size() + " técnicos");
}
```

- [ ] **Step 6: Actualizar `aplicarFiltros()`**

```java
// FROM:
List<Integer> idsTecSelec = new ArrayList<>();
for (int i = 0; i < cbsTecnico.size(); i++)
    if (cbsTecnico.get(i).isSelected()) idsTecSelec.add(tecnicos.get(i).getIdTec());

// TO:
List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
```

- [ ] **Step 7: Actualizar `limpiarFiltros()`**

```java
// FROM:
cbsTecnico.forEach(cb -> cb.setSelected(false));
filtroTecnico.setText("Técnico");

// TO:
idsTecFiltro.clear();
etiquetaTec.set("Técnico");
if (listaTecFiltro != null) listaTecFiltro.refresh();
```

- [ ] **Step 8: Compilar**

```bash
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 9: Prueba manual**

Arrancar la app → vista "Asignaciones de pulido":
- Mismo comportamiento que en Pendientes (Steps 1-6 del Task 2, Step 9).

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/views/PulidoSuperTecnicoView.fxml
git add src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "feat: multi-select filtro tecnico en PulidoSuperTecnico via ComboBox+Popup"
```

---

### Task 4: HistorialPulido — FXML + Controller

**Files:**
- Modify: `src/main/resources/views/HistorialPulidoView.fxml`
- Modify: `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java`

- [ ] **Step 1: Actualizar FXML**

En `HistorialPulidoView.fxml`:

```xml
<!-- FROM: -->
<MenuButton fx:id="filtroTecnico" text="Técnico" prefWidth="130"/>

<!-- TO: -->
<com.reparaciones.utils.MultiSelectComboBox fx:id="filtroTecnico" prefWidth="130"/>
```

- [ ] **Step 2: Actualizar imports**

Añadir en `HistorialPulidoController.java`:

```java
import com.reparaciones.utils.MultiSelectComboBox;
import java.util.HashSet;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Popup;
```

- [ ] **Step 3: Cambiar campo `filtroTecnico` y `cbsTecnico`**

```java
// FROM (línea ~31):
@FXML private MenuButton filtroTecnico;
// FROM (línea ~45):
private final List<CheckBox> cbsTecnico = new ArrayList<>();

// TO:
@FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
private final Set<Integer>   idsTecFiltro  = new HashSet<>();
private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
private ListView<Tecnico>    listaTecFiltro;
```

- [ ] **Step 4: Reemplazar bloque filtroTecnico en `configurarFiltros()`**

El bloque actual (el `try` con el `for` que añade CheckBox al MenuButton):

```java
// FROM:
tecnicos.addAll(tecnicoDAO.getAllActivos());
for (Tecnico t : tecnicos) {
    CheckBox cb = new CheckBox(t.getNombre());
    cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
    cb.selectedProperty().addListener((obs, o, n) -> {
        actualizarTextoFiltroTecnico();
        aplicarFiltros();
    });
    cbsTecnico.add(cb);
    CustomMenuItem item = new CustomMenuItem(cb, false);
    filtroTecnico.getItems().add(item);
}
```

```java
// TO:
tecnicos.addAll(tecnicoDAO.getAllActivos());
filtroTecnico.setButtonCell(new ListCell<>() {
    { etiquetaTec.addListener((obs, o, n) -> setText(n)); setText(etiquetaTec.get()); }
    @Override protected void updateItem(Tecnico t, boolean empty) {
        super.updateItem(t, empty); setText(etiquetaTec.get());
    }
});
listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
listaTecFiltro.setVisibleRowCount(Math.min(tecnicos.size(), 8));
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
popupContenedor.getStyleClass().add("combo-box-popup");
Popup popupTec = new Popup();
popupTec.setAutoHide(true);
popupTec.getContent().add(popupContenedor);
filtroTecnico.setCustomPopup(popupTec);
```

- [ ] **Step 5: Actualizar `actualizarTextoFiltroTecnico()`**

```java
// FROM:
private void actualizarTextoFiltroTecnico() {
    long sel = cbsTecnico.stream().filter(CheckBox::isSelected).count();
    filtroTecnico.setText(sel == 0 ? "Técnico" : sel == 1
            ? cbsTecnico.stream().filter(CheckBox::isSelected).findFirst().map(CheckBox::getText).orElse("Técnico")
            : sel + " técnicos");
}

// TO:
private void actualizarTextoFiltroTecnico() {
    List<String> nombres = tecnicos.stream()
            .filter(t -> idsTecFiltro.contains(t.getIdTec()))
            .map(Tecnico::getNombre).toList();
    etiquetaTec.set(nombres.isEmpty() ? "Técnico"
            : nombres.size() == 1 ? nombres.get(0)
            : nombres.size() + " técnicos");
}
```

- [ ] **Step 6: Actualizar `aplicarFiltros()`**

```java
// FROM:
List<Integer> idsTecSelec = new ArrayList<>();
for (int i = 0; i < cbsTecnico.size(); i++)
    if (cbsTecnico.get(i).isSelected()) idsTecSelec.add(tecnicos.get(i).getIdTec());

// TO:
List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
```

- [ ] **Step 7: Actualizar `limpiarFiltros()`**

```java
// FROM:
cbsTecnico.forEach(cb -> cb.setSelected(false));
filtroTecnico.setText("Técnico");

// TO:
idsTecFiltro.clear();
etiquetaTec.set("Técnico");
if (listaTecFiltro != null) listaTecFiltro.refresh();
```

- [ ] **Step 8: Compilar**

```bash
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 9: Prueba manual**

Arrancar la app → vista "Historial de pulidos":
- Mismo comportamiento que en Pendientes (Task 2, Step 9).
- Los filtros de fecha siguen funcionando independientemente del filtro de técnico.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/views/HistorialPulidoView.fxml
git add src/main/java/com/reparaciones/controllers/HistorialPulidoController.java
git commit -m "feat: multi-select filtro tecnico en HistorialPulido via ComboBox+Popup"
```
