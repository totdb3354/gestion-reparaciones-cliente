package com.reparaciones.controllers;

import com.reparaciones.dao.UsuarioDAO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.sql.SQLException;

public class CambiarPasswordController {

    private static final Image IMG_OJO_ACTIVAR   = new Image(
            CambiarPasswordController.class.getResourceAsStream("/images/ojo_activar.png"));
    private static final Image IMG_OJO_DESACTIVAR = new Image(
            CambiarPasswordController.class.getResourceAsStream("/images/ojo_desactivar.png"));

    @FXML private PasswordField campoActual;
    @FXML private TextField     campoActualVisible;
    @FXML private ImageView     imgOjoActual;

    @FXML private PasswordField campoNueva;
    @FXML private TextField     campoNuevaVisible;
    @FXML private ImageView     imgOjoNueva;

    @FXML private PasswordField campoConfirmar;
    @FXML private TextField     campoConfirmarVisible;
    @FXML private ImageView     imgOjoConfirmar;

    @FXML private Label  lblError;
    @FXML private Button btnGuardar;
    @FXML private Button btnCancelar;

    private boolean visActual    = false;
    private boolean visNueva     = false;
    private boolean visConfirmar = false;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML private void toggleActual() {
        visActual = !visActual;
        toggle(campoActual, campoActualVisible, imgOjoActual, visActual);
    }

    @FXML private void toggleNueva() {
        visNueva = !visNueva;
        toggle(campoNueva, campoNuevaVisible, imgOjoNueva, visNueva);
    }

    @FXML private void toggleConfirmar() {
        visConfirmar = !visConfirmar;
        toggle(campoConfirmar, campoConfirmarVisible, imgOjoConfirmar, visConfirmar);
    }

    private void toggle(PasswordField pf, TextField tf, ImageView img, boolean mostrar) {
        if (mostrar) {
            tf.setText(pf.getText());
            tf.setVisible(true);  tf.setManaged(true);
            pf.setVisible(false); pf.setManaged(false);
            img.setImage(IMG_OJO_DESACTIVAR);
        } else {
            pf.setText(tf.getText());
            pf.setVisible(true);  pf.setManaged(true);
            tf.setVisible(false); tf.setManaged(false);
            img.setImage(IMG_OJO_ACTIVAR);
        }
    }

    private String getTexto(PasswordField pf, TextField tf) {
        return pf.isVisible() ? pf.getText() : tf.getText();
    }

    @FXML private void cancelar() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }

    @FXML private void guardar() {
        ocultarError();
        String actual    = getTexto(campoActual,    campoActualVisible);
        String nueva     = getTexto(campoNueva,     campoNuevaVisible);
        String confirmar = getTexto(campoConfirmar, campoConfirmarVisible);

        if (actual.isEmpty() || nueva.isEmpty() || confirmar.isEmpty()) {
            mostrarError("Rellena todos los campos.");
            return;
        }
        if (nueva.length() < 6) {
            mostrarError("La contraseña debe tener al menos 6 caracteres.");
            return;
        }
        if (!nueva.equals(confirmar)) {
            mostrarError("Las contraseñas nuevas no coinciden.");
            return;
        }

        btnGuardar.setDisable(true);
        Stage ventana = (Stage) btnGuardar.getScene().getWindow();
        javafx.stage.Window mainWindow = ventana.getOwner();
        try {
            usuarioDAO.cambiarPassword(actual, nueva);
            ventana.close();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Contraseña cambiada correctamente.");
            if (mainWindow != null) alert.initOwner(mainWindow);
            alert.showAndWait();
        } catch (SQLException ex) {
            btnGuardar.setDisable(false);
            mostrarError(ex.getMessage() != null ? ex.getMessage() : "Error al cambiar la contraseña.");
        }
    }

    private void mostrarError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
