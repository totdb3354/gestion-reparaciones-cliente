# MultiSelectDropdown Helper — Design Spec

**Goal:** Centralizar el boilerplate de los 8 dropdowns MultiSelectComboBox en una clase utilitaria estática, corrigiendo el bug del estado `:selected` persistente en un único lugar.

**Architecture:** Nueva clase `MultiSelectDropdown` (Option A — solo UI, estado en el controlador). El controlador sigue owning `Set<>`, `StringProperty` y la lógica de filtro. El helper gestiona ListView + VBox + Popup + ButtonCell + clearSelection fix. La clase es idempotente: segura de llamar en cada recarga.

**Tech Stack:** JavaFX, `java.util.function` (`Function`, `Predicate`, `BiConsumer`), `javafx.util.Callback`.

---

## Files

| Fichero | Cambio |
|---------|--------|
| `src/main/java/com/reparaciones/utils/MultiSelectDropdown.java` | **Crear** — clase estática con dos overloads de `setup()` y clase `Handle` |
| `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` | Modificar — reemplazar boilerplate por `setup()` |
| `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/HistorialPulidoController.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` | Modificar |
| `src/main/java/com/reparaciones/controllers/EstadisticasController.java` | Modificar — overload con cellFactory custom |
| `src/main/java/com/reparaciones/controllers/StockController.java` | Modificar — eliminar guard `if (listaProvStock == null)` |

`MultiSelectComboBox.java` no se modifica.

---

## Diseño de `MultiSelectDropdown.java`

### API pública

```java
public final class MultiSelectDropdown {

    // Caso estándar: checkbox automático por ítem
    public static <T> Handle setup(
        MultiSelectComboBox<T> combo,
        List<T>                items,
        Function<T, String>    displayText,
        Predicate<T>           isSelected,
        BiConsumer<T, Boolean> onToggle,   // (item, nuevoEstadoChecked)
        StringProperty         etiqueta
    )

    // Caso custom: cell factory propia (EstadisticasController)
    public static <T> Handle setup(
        MultiSelectComboBox<T>            combo,
        List<T>                           items,
        Callback<ListView<T>, ListCell<T>> cellFactory,
        StringProperty                     etiqueta
    )

    public static final class Handle {
        public void refresh()   // refresca el ListView sin recrear el popup
    }
}
```

### Comportamiento de `setup()`

**Primera llamada** (detección: `combo.getUserData() == null`):
1. Crea `ListView<T>`; guarda referencia en `combo.setUserData(listView)`
2. Asigna la cell factory (estándar o custom según el overload)
3. Añade event filter en el `ListView` para `MOUSE_RELEASED` que llama `Platform.runLater(() -> listView.getSelectionModel().clearSelection())` — **fix del bug `:selected`**
4. Crea `VBox contenedor = new VBox(listView)`
5. `contenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup")`
6. `contenedor.setPrefWidth(combo.getPrefWidth())` y `setMaxWidth(combo.getPrefWidth())`
7. Crea `Popup`, `setAutoHide(true)`, añade contenedor, llama `combo.setCustomPopup(popup)`
8. Configura `ButtonCell`: en `updateItem` y vía listener de `etiqueta` hace `Platform.runLater(() -> setText(etiqueta.get()))`

**Todas las llamadas** (idempotente):
- `listView.getItems().setAll(items)`
- `listView.setMaxHeight(items.size() * 30.0 + 4)`
- `listView.refresh()`
- Devuelve `new Handle(listView)`

### Cell factory estándar

```java
// Dentro del ListCell:
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
        // clearSelection se gestiona por el event filter del ListView
    });
}
@Override protected void updateItem(T item, boolean empty) {
    super.updateItem(item, empty);
    if (empty || item == null) { setGraphic(null); setText(null); return; }
    check.setSelected(isSelected.test(item));
    setGraphic(check);
    setText(displayText.apply(item));
}
```

---

## Cambio en cada controlador

### Campos (por cada MultiSelectComboBox)

Eliminar:
```java
private ListView<Tecnico> listaTecFiltro;
```

Añadir:
```java
private MultiSelectDropdown.Handle filtroTecHandle;
```

El `Set<Integer> idsTecFiltro` y el `StringProperty etiquetaTec` se mantienen en el controlador.

### En `configurarFiltros()` (o equivalente)

Antes (~45 líneas de boilerplate):
```java
listaTecFiltro = new ListView<>();
listaTecFiltro.setCellFactory(...);   // ~20 líneas
VBox contenedor = new VBox(listaTecFiltro);
contenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
contenedor.setPrefWidth(...); contenedor.setMaxWidth(...);
Popup popup = new Popup(); popup.setAutoHide(true);
popup.getContent().add(contenedor);
filtroTecnico.setCustomPopup(popup);
filtroTecnico.setButtonCell(new ListCell<>() { ... });  // ~8 líneas
```

Después (~6 líneas):
```java
filtroTecHandle = MultiSelectDropdown.setup(
    filtroTecnico, tecnicosLista,
    Tecnico::getNombre,
    t -> idsTecFiltro.contains(t.getIdTec()),
    (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                      else         idsTecFiltro.remove(t.getIdTec());
                      actualizarTextoFiltroTecnico(); aplicarFiltros(); },
    etiquetaTec);
```

### En `limpiarFiltros()`

```java
// Antes:
listaTecFiltro.refresh();
// Después:
filtroTecHandle.refresh();
```

---

## Casos especiales

### EstadisticasController

Usa el overload con `cellFactory` porque tiene colores por técnico y separador `null`. El controlador sigue gestionando su lógica de celda; delega solo la infraestructura (VBox, Popup, ButtonCell, clearSelection):

```java
filtroTecHandle = MultiSelectDropdown.setup(
    menuTecnicos, todosLosTecnicos,
    this::crearCeldaTecnico,   // Callback con lógica de color y separador
    etiquetaTecs);
```

El event filter que el helper instala en el `ListView` gestiona `clearSelection()` en ambos overloads, por lo que la cell factory custom no necesita hacerlo.

### StockController

`poblarFiltrosProveedor()` se llama en cada recarga. Gracias a la idempotencia, el guard `if (listaProvStock == null)` desaparece por completo:

```java
private void poblarFiltrosProveedor() {
    List<Proveedor> activos = datosProveedores.stream()
            .filter(Proveedor::isActivo).collect(toList());

    filtroProvStockHandle = MultiSelectDropdown.setup(
        menuFiltroProveedores, activos,
        Proveedor::getNombre,
        p -> seleccionadosProv.contains(p.getNombre()),
        (p, checked) -> { if (checked) seleccionadosProv.add(p.getNombre());
                          else         seleccionadosProv.remove(p.getNombre());
                          actualizarTextoFiltroProveedor(etiquetaProvStock, seleccionadosProv);
                          aplicarFiltroProveedores(); },
        etiquetaProvStock);

    filtroProvPedidosHandle = MultiSelectDropdown.setup(
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

Los campos `listaProvStock` y `listaProvPedidos` desaparecen; se añaden `filtroProvStockHandle` y `filtroProvPedidosHandle`.

---

## Bug fix incluido

El event filter instalado en el `ListView` por el helper corrige el bug del estado `:selected` persistente en los 8 dropdowns:

```java
listView.addEventFilter(MouseEvent.MOUSE_RELEASED, e ->
    Platform.runLater(() -> listView.getSelectionModel().clearSelection()));
```

Este fix se aplica en ambos overloads (estándar y custom), por lo que EstadisticasController también queda corregido sin tocar su cell factory.
