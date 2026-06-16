package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.Alertas;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
    @FXML private TextField                            txtBuscadorLogs;
    @FXML private DatePicker                            dpLogsDesde;
    @FXML private DatePicker                            dpLogsHasta;

    private final LogDAO logDAO = new LogDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObservableList<LogActividad> logsMaster = FXCollections.observableArrayList();

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

        FilteredList<LogActividad> logsFiltrados = new FilteredList<>(logsMaster, l -> true);
        Runnable aplicarFiltrosLogs = () -> logsFiltrados.setPredicate(log ->
                coincideFiltro(log, txtBuscadorLogs.getText(), dpLogsDesde.getValue(), dpLogsHasta.getValue()));

        txtBuscadorLogs.textProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.getEditor().setDisable(true);
        dpLogsDesde.getEditor().setOpacity(1.0);
        dpLogsHasta.getEditor().setDisable(true);
        dpLogsHasta.getEditor().setOpacity(1.0);

        tablaLogs.setItems(logsFiltrados);

        cargarLogs();
    }

    @FXML
    private void cargarLogs() {
        try {
            List<LogActividad> logs = logDAO.getAll();
            logsMaster.setAll(logs);
        } catch (SQLException e) {
            Alertas.mostrarError("Error al cargar los logs: " + e.getMessage());
        }
    }

    @FXML
    private void limpiarFiltrosLogs() {
        txtBuscadorLogs.clear();
        dpLogsDesde.setValue(null);
        dpLogsHasta.setValue(null);
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
