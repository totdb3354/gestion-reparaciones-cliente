package com.reparaciones.utils;

import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Alertas {

    private static Stage errorActivo = null;

    public static void mostrarError(String mensaje) {
        if (errorActivo != null && errorActivo.isShowing()) return;
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.initModality(Modality.NONE);
        alert.setHeaderText(null);
        alert.show();
        errorActivo = (Stage) alert.getDialogPane().getScene().getWindow();
    }
}
