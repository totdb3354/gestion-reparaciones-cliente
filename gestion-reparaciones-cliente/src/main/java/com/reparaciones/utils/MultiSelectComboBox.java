package com.reparaciones.utils;

import javafx.geometry.Bounds;
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
            Bounds b = localToScreen(getBoundsInLocal());
            if (b == null) return;
            customPopup.show(this, b.getMinX(), b.getMaxY() + 4);
        }
    }
}
