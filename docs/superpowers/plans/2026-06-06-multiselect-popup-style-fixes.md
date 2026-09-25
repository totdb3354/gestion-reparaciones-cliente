# MultiSelectComboBox Popup Style Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the MultiSelectComboBox popup look identical to the existing MenuButton dropdowns (Estado, Tipo, Incidencias) — same font weight, same top/bottom padding inside the popup, same checkbox box behavior on hover, and minimum width matching the button.

**Architecture:** Two changes: (1) add a `.multi-select-popup` CSS class in `app.css` that overrides font weight, inner padding, and checkbox box style — scoped specifically to our popup without touching `.combo-box-popup` rules used by single-select ComboBoxes; (2) add this class to the VBox popup container in all 3 controllers and pin the container min-width to the button's preferred width. The VBox will end up with both `combo-box-popup` (for border/shadow/hover color) and `multi-select-popup` (for the overrides), with `.multi-select-popup` rules placed AFTER `.combo-box-popup` in the CSS file so they win the specificity tie.

**Tech Stack:** JavaFX CSS (declaration-order tiebreaking), JavaFX `VBox.getStyleClass().addAll()`, JavaFX `Region.setMinWidth()`.

---

## Files

| File | Change |
|------|--------|
| `src/main/resources/styles/app.css` | Remove misplaced checkbox-box rule; add `.multi-select-popup` block |
| `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` | `addAll(…, "multi-select-popup")` + `setMinWidth` |
| `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java` | Same |
| `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java` | Same |

---

### Task 1: CSS — añadir bloque `.multi-select-popup`

**Files:**
- Modify: `src/main/resources/styles/app.css`

**Context:** The existing `.combo-box-popup` block ends around line 382. There is currently a rule for `.combo-box-popup .list-view .list-cell:filled:hover .check-box .box` — this was added specifically for our feature but it's scoped too broadly. We will remove it and replace it with a properly scoped `.multi-select-popup` version along with the other two overrides.

The reference for the desired behavior is the `MenuButton` + `CustomMenuItem` + `CheckBox` pattern used in `StockController` (`menuFiltroEstado`) and `ReparacionControllerAdmin` (`filtroIncidencias`):
- Font weight: normal (old `cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;")` had no bold)
- Inner padding: matches `.context-menu { -fx-padding: 4 0 4 0 }` — applied to the ListView, not the VBox outer wrapper
- CheckBox box on hover: matches `.custom-menu-item:hover .check-box .box { background: transparent; border: white }`

- [ ] **Step 1: Abre `src/main/resources/styles/app.css` y localiza el bloque a modificar**

Busca estas líneas (~378–382):
```css
.combo-box-popup .list-view .list-cell:filled:hover .check-box .box,
.combo-box-popup .list-view .list-cell:filled:selected .check-box .box {
    -fx-background-color: #FFFFFF;
    -fx-border-color: #FFFFFF;
}
```

- [ ] **Step 2: Elimina esas 4 líneas** (el bloque completo de arriba)

El final de la sección 7 debe quedar así tras el borrado:
```css
.combo-box-popup .list-view .list-cell:filled:hover,
.combo-box-popup .list-view .list-cell:filled:selected {
    -fx-background-color: #001232;
    -fx-background-radius: 8;
    -fx-background-insets: 2 6 2 6;
    -fx-text-fill: #FFFFFF;
}


/* ── 8. DatePicker ─────── ...
```

- [ ] **Step 3: Añade el bloque `.multi-select-popup` justo antes de la sección 8**

Inserta este bloque en el espacio en blanco entre el cierre de `.combo-box-popup .list-cell:filled:hover` y el comentario `/* ── 8. DatePicker`:

```css
/* MultiSelectComboBox popup — iguala el estilo visual del MenuButton/ContextMenu */
.multi-select-popup .list-view {
    -fx-padding: 4 0 4 0;
}

.multi-select-popup .list-view .list-cell {
    -fx-font-weight: normal;
}

.multi-select-popup .list-view .list-cell:filled:hover .check-box .box,
.multi-select-popup .list-view .list-cell:filled:selected .check-box .box {
    -fx-background-color: transparent;
    -fx-border-color: #FFFFFF;
}
```

- [ ] **Step 4: Compila para verificar que no hay errores de sintaxis CSS**

```
mvn compile -q
```
Expected: sin output (salida limpia).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/styles/app.css
git commit -m "style: popup multi-select — padding, peso normal y box transparente"
```

---

### Task 2: Controllers — clase `multi-select-popup` y ancho mínimo

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`
- Modify: `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`
- Modify: `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java`

**Context:** En cada controlador, dentro de `configurarFiltros()`, hay estas dos líneas:
```java
VBox popupContenedor = new VBox(listaTecFiltro);
popupContenedor.getStyleClass().add("combo-box-popup");
```

Hay que añadir la clase `multi-select-popup` (activa el bloque CSS del Task 1) y fijar el ancho mínimo del contenedor al `prefWidth` del botón (130 px), para que el popup nunca sea más estrecho que el botón.

La sustitución en los tres ficheros es idéntica:

**Antes:**
```java
        VBox popupContenedor = new VBox(listaTecFiltro);
        popupContenedor.getStyleClass().add("combo-box-popup");
```

**Después:**
```java
        VBox popupContenedor = new VBox(listaTecFiltro);
        popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
        popupContenedor.setMinWidth(filtroTecnico.getPrefWidth());
```

- [ ] **Step 1: Actualiza `PendientesSuperTecnicoController.java`**

Busca en `configurarFiltros()` el bloque exacto:
```java
        VBox popupContenedor = new VBox(listaTecFiltro);
        popupContenedor.getStyleClass().add("combo-box-popup");
```
Sustitúyelo por:
```java
        VBox popupContenedor = new VBox(listaTecFiltro);
        popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
        popupContenedor.setMinWidth(filtroTecnico.getPrefWidth());
```

- [ ] **Step 2: Aplica el mismo cambio en `PulidoSuperTecnicoController.java`**

Misma sustitución exacta que Step 1.

- [ ] **Step 3: Aplica el mismo cambio en `HistorialPulidoController.java`**

Misma sustitución exacta que Step 1.

- [ ] **Step 4: Compila**

```
mvn compile -q
```
Expected: sin output.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java src/main/java/com/reparaciones/controllers/HistorialPulidoController.java
git commit -m "style: clase multi-select-popup y ancho minimo en los tres controladores"
```
