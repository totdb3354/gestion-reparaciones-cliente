package com.reparaciones.utils;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
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

    private static final int MAX_VISIBLE_ROWS = 8;

    /** Alto fijo de fila. Sin fixedCellSize el VirtualFlow estima alturas y, en el
     *  fondo de la lista, cada pasada de layout (p. ej. el hover) recoloca la vista
     *  una fila arriba ocultando la última. */
    private static final double ALTURA_FILA = 30;

    /** Padding vertical (4+4) + borde (1+1) del list-view en app.css. */
    private static final double RELLENO_LISTA = 10;

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
            // Solo en la variante estándar: la de cellFactory custom (Estadísticas)
            // tiene una fila separadora de 8px que un alto fijo global rompería.
            listView.setFixedCellSize(ALTURA_FILA);
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
        listView.setMaxHeight(Math.min(items.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
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
        listView.setMaxHeight(Math.min(items.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
        listView.refresh();
        return new Handle(listView);
    }

    // ── Overload custom + buscador (Estadísticas por puntos) ──────────────────

    /** Igual que el overload custom, con un TextField de filtro sobre la lista.
     *  {@code textoFiltro} extrae el texto buscable del item; item null (separador) siempre visible. */
    public static <T> Handle setup(
            MultiSelectComboBox<T> combo,
            List<T> items,
            Callback<ListView<T>, ListCell<T>> cellFactory,
            StringProperty etiqueta,
            Function<T, String> textoFiltro) {

        ListView<T> listView = getOrCreate(combo);
        @SuppressWarnings("unchecked")
        List<T> maestros = (List<T>) listView.getProperties()
                .computeIfAbsent("maestros", k -> new java.util.ArrayList<T>());
        maestros.clear();
        maestros.addAll(items);

        if (combo.getUserData() == null) {
            combo.setUserData(listView);
            // Mismo fix que la variante estándar (bug "la lista sube sola al llegar al
            // fondo"): sin fixedCellSize el VirtualFlow estima alturas y cada pasada de
            // layout recoloca la vista una fila arriba. La fila separadora de Estadísticas
            // dibuja ahora su línea centrada dentro del alto fijo (ver su cellFactory).
            listView.setFixedCellSize(ALTURA_FILA);
            listView.setCellFactory(cellFactory);

            TextField buscador = new TextField();
            buscador.setPromptText("Buscar…");
            buscador.textProperty().addListener((obs, o, texto) -> {
                String t = texto == null ? "" : texto.trim().toLowerCase();
                List<T> filtrados = t.isEmpty() ? maestros : maestros.stream()
                        .filter(it -> it == null
                                || textoFiltro.apply(it).toLowerCase().contains(t))
                        .collect(java.util.stream.Collectors.toList());
                listView.getItems().setAll(filtrados);
                listView.setMaxHeight(Math.min(filtrados.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
            });
            setupPopupAndButton(combo, listView, etiqueta, buscador);
        }

        listView.getItems().setAll(maestros);
        listView.setMaxHeight(Math.min(maestros.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
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
        setupPopupAndButton(combo, listView, etiqueta, null);
    }

    private static <T> void setupPopupAndButton(
            MultiSelectComboBox<T> combo, ListView<T> listView, StringProperty etiqueta,
            TextField buscador) {

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

        VBox contenedor = buscador != null ? new VBox(4, buscador, listView) : new VBox(listView);
        contenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
        contenedor.setPrefWidth(combo.getPrefWidth());
        contenedor.setMaxWidth(combo.getPrefWidth());

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(contenedor);
        combo.setCustomPopup(popup);
    }
}
