package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.Alertas;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
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

        tablaLogs.getColumns().forEach(c -> c.setReorderable(false));
        cargarLogs();
    }

    @FXML
    private void cargarLogs() {
        try {
            List<LogActividad> logs = logDAO.getAll();
            tablaLogs.setItems(FXCollections.observableArrayList(logs));
        } catch (SQLException e) {
            Alertas.mostrarError("Error al cargar los logs: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }

    static boolean coincideFiltro(LogActividad log, String texto, LocalDate desde, LocalDate hasta) {
        boolean coincideTexto = texto == null || texto.isBlank()
                || contiene(log.getNombreUsuario(), texto)
                || contiene(log.getAccion(), texto)
                || contiene(log.getDetalle(), texto);

        LocalDate fecha = log.getFecha() != null ? log.getFecha().toLocalDate() : null;
        boolean coincideDesde = desde == null || fecha == null || !fecha.isBefore(desde);
        boolean coincideHasta = hasta == null || fecha == null || !fecha.isAfter(hasta);

        return coincideTexto && coincideDesde && coincideHasta;
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto.toLowerCase().trim());
    }
}
