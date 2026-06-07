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

    private static final int MAX_VISIBLE_ROWS = 8;

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
        listView.setMaxHeight(Math.min(items.size(), MAX_VISIBLE_ROWS) * 30.0 + 4);
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
        listView.setMaxHeight(Math.min(items.size(), MAX_VISIBLE_ROWS) * 30.0 + 4);
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
