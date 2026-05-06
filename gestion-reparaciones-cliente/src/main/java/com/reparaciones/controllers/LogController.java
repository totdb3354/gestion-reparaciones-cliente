package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.models.LogActividad;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogController {

    @FXML private TableView<LogActividad>              tablaLogs;
    @FXML private TableColumn<LogActividad, String>    colFecha;
    @FXML private TableColumn<LogActividad, String>    colUsuario;
    @FXML private TableColumn<LogActividad, String>    colAccion;
    @FXML private TableColumn<LogActividad, String>    colDetalle;

    private final LogDAO logDAO = new LogDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : ""));
        colUsuario.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        colAccion.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getAccion()));
        colDetalle.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getDetalle()));

        cargarLogs();
    }

    @FXML
    private void cargarLogs() {
        try {
            List<LogActividad> logs = logDAO.getAll();
            tablaLogs.setItems(FXCollections.observableArrayList(logs));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Error al cargar los logs: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }
}
