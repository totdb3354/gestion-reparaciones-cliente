package com.reparaciones.utils;

import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.stage.Popup;

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
            Bounds b = localToScreen(getBoundsInLocal());
            if (b == null) return;
            if (getScene() != null && !customPopup.getContent().isEmpty()
                    && customPopup.getContent().get(0) instanceof Parent p) {
                p.getStylesheets().setAll(getScene().getStylesheets());
            }
            customPopup.show(this, b.getMinX(), b.getMaxY() + 4);
        }
    }
}
