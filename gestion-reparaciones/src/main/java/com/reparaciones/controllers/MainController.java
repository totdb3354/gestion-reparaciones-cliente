package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contenedor;
    @FXML private Button    btnReparaciones;
    @FXML private Button    btnStock;
    @FXML private Label     lblUsuario;
    @FXML private Button    btnLogout;

    @FXML
    public void initialize() {
        lblUsuario.setText("Hola, " + Sesion.getUsuario().getNombreUsuario());
        mostrarReparaciones();
    }

    @FXML
    private void mostrarReparaciones() {
        String vista = Sesion.esAdmin()
                ? "/views/ReparacionViewAdmin.fxml"
                : "/views/ReparacionViewTecnico.fxml";
        mostrarVista(vista, btnReparaciones, btnStock);
    }

    @FXML
    private void mostrarStock() {
        mostrarVista("/views/StockView.fxml", btnStock, btnReparaciones);
    }

    @FXML
    private void cerrarSesion() {
        try {
            Sesion.cerrar();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/LoginView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnLogout.getScene().getWindow();

            stage.setMaximized(false);
            stage.setResizable(false);
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setScene(new Scene(root));
            stage.setTitle("Gestión de Reparaciones — Login");
            stage.sizeToScene();
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mostrarVista(String ruta, Button activo, Button inactivo) {
        btnReparaciones.setDisable(true);
        btnStock.setDisable(true);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(ruta));
            Node vista = loader.load();
            contenedor.getChildren().setAll(vista);
            setActivo(activo, inactivo);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            btnReparaciones.setDisable(false);
            btnStock.setDisable(false);
        }
    }

    private void setActivo(Button activo, Button inactivo) {
        activo.getStyleClass().remove("nav-btn");
        if (!activo.getStyleClass().contains("nav-btn-active"))
            activo.getStyleClass().add("nav-btn-active");
        inactivo.getStyleClass().remove("nav-btn-active");
        if (!inactivo.getStyleClass().contains("nav-btn"))
            inactivo.getStyleClass().add("nav-btn");
    }
}