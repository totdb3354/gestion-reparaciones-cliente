package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.UsuarioDAO;
import com.reparaciones.models.Usuario;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField campoUsuario;
    @FXML
    private PasswordField campoPassword;
    @FXML
    private Label lblError;
    @FXML
    private Button btnLogin;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            Stage stage = (Stage) campoUsuario.getScene().getWindow();
            stage.setResizable(false);
            stage.sizeToScene();
        });

        campoUsuario.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER)
                login();
        });
        campoPassword.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER)
                login();
        });
    }

    /**
     * Autentica con las credenciales introducidas.
     * Si OK → guarda en Sesion y carga MainView.
     * Si falla → muestra mensaje de error en rojo.
     */
    @FXML
    private void login() {
        String usuario = campoUsuario.getText().trim();
        String password = campoPassword.getText();

        if (usuario.isEmpty() || password.isEmpty()) {
            mostrarError("Rellena usuario y contraseña.");
            return;
        }

        try {
            Usuario u = usuarioDAO.login(usuario, password);
            if (u == null) {
                mostrarError("Usuario o contraseña incorrectos.");
                return;
            }
            Sesion.iniciar(u);
            cargarMainView();
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarError("Error de conexión con la base de datos.");
        }
    }

    /**
     * Carga MainView en la misma ventana tras login exitoso.
     * Establece tamaño mínimo y permite redimensionar.
     */
    private void cargarMainView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/MainView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setResizable(true);
            stage.setScene(new Scene(root));
            stage.setTitle("Gestión de Reparaciones");
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setMaximized(true); // ← siempre después de setScene
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
    }

    /**
     * Pide la contraseña maestra (del admin) antes de navegar al registro.
     * Si es correcta → carga RegisterView.
     */
    @FXML
    private void irARegistro() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Acceso restringido");
        dialog.setHeaderText("Introduce la contraseña maestra");
        dialog.setContentText("Contraseña:");

        // Ocultar el texto del input
        PasswordField pf = new PasswordField();
        dialog.getDialogPane().setContent(pf);

        // Poner foco en el campo al abrirse el dialog
        Platform.runLater(pf::requestFocus);

        dialog.getDialogPane().setContent(pf);

        dialog.showAndWait().ifPresent(ignored -> {
            String password = pf.getText();
            try {
                if (usuarioDAO.verificarPasswordAdmin(password)) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/RegisterView.fxml"));
                    Parent root = loader.load();
                    Stage stage = (Stage) btnLogin.getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.setTitle("Gestión de Reparaciones — Registro");
                    stage.centerOnScreen();
                } else {
                    mostrarError("Contraseña maestra incorrecta.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}