package com.reparaciones.utils;

import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class Alertas {

    private static Stage errorActivo = null;

    public static void mostrarError(String mensaje) {
        if (ApiClient.isLogoutEnCurso()) return;                 // no molestar durante el logout
        if (errorActivo != null && errorActivo.isShowing()) return;  // dedup
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.initModality(Modality.NONE);
        alert.setHeaderText(null);
        Window owner = ventanaEnfocada();
        if (owner != null) alert.initOwner(owner);               // pegado a su ventana, no fantasma
        alert.show();
        errorActivo = (Stage) alert.getDialogPane().getScene().getWindow();
    }

    /** Ventana actualmente enfocada (la topmost real, incluso si hay un modal abierto). */
    private static Window ventanaEnfocada() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .findFirst()
                .orElse(null);
    }
}
