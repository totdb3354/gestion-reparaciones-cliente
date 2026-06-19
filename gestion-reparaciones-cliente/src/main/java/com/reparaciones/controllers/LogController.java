package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.dao.UsuarioDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.models.Usuario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.FechaUtils;
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
    @FXML private TextField                            txtFiltroTecnico;
    @FXML private DatePicker                           dpLogsDesde;
    @FXML private DatePicker                           dpLogsHasta;

    private final LogDAO     logDAO     = new LogDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObservableList<LogActividad> logsMaster = FXCollections.observableArrayList();
    private String tecnicoSeleccionado = null;

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        FechaUtils.formatear(c.getValue().getFecha(), FMT)));
        colUsuario.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        colAccion.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getAccion()));
        colDetalle.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getDetalle()));

        tablaLogs.getColumns().forEach(c -> c.setReorderable(false));

        FilteredList<LogActividad> logsFiltrados = new FilteredList<>(logsMaster, l -> true);
        Runnable aplicarFiltrosLogs = () -> logsFiltrados.setPredicate(log ->
                coincideFiltro(log, txtBuscadorLogs.getText(), dpLogsDesde.getValue(),
                               dpLogsHasta.getValue(), tecnicoSeleccionado));

        txtBuscadorLogs.textProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.getEditor().setDisable(true);
        dpLogsDesde.getEditor().setOpacity(1.0);
        dpLogsHasta.getEditor().setDisable(true);
        dpLogsHasta.getEditor().setOpacity(1.0);

        tablaLogs.setItems(logsFiltrados);

        configurarFiltroTecnico(aplicarFiltrosLogs);
        cargarLogs();
    }

    private void configurarFiltroTecnico(Runnable aplicarFiltrosLogs) {
        ObservableList<String> todosUsuarios = FXCollections.observableArrayList();
        FilteredList<String> usuariosFiltrados = new FilteredList<>(todosUsuarios, s -> true);

        ListView<String> listaUsuarios = new ListView<>(usuariosFiltrados);
        listaUsuarios.setFixedCellSize(28);
        listaUsuarios.setPrefWidth(160);
        listaUsuarios.setMaxHeight(224); // 8 ítems visibles, luego scroll
        listaUsuarios.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 6, 0, 0, 2);");

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listaUsuarios);

        Runnable mostrarPopup = () -> {
            if (!popup.isShowing() && txtFiltroTecnico.getScene() != null) {
                javafx.geometry.Bounds b =
                        txtFiltroTecnico.localToScreen(txtFiltroTecnico.getBoundsInLocal());
                if (b != null) popup.show(txtFiltroTecnico, b.getMinX(), b.getMaxY() + 2);
            }
        };

        txtFiltroTecnico.setOnMouseClicked(e -> mostrarPopup.run());

        txtFiltroTecnico.textProperty().addListener((obs, o, n) -> {
            String text = n == null ? "" : n.trim().toLowerCase();
            usuariosFiltrados.setPredicate(s -> text.isEmpty() || s.toLowerCase().contains(text));
            if (text.isEmpty() && tecnicoSeleccionado != null) {
                tecnicoSeleccionado = null;
                aplicarFiltrosLogs.run();
            }
        });

        listaUsuarios.setOnMouseClicked(e -> {
            String sel = listaUsuarios.getSelectionModel().getSelectedItem();
            if (sel != null) {
                tecnicoSeleccionado = sel;
                txtFiltroTecnico.setText(sel);
                popup.hide();
                aplicarFiltrosLogs.run();
            }
        });

        try {
            List<Usuario> usuarios = usuarioDAO.getUsuariosTecnicos();
            todosUsuarios.setAll(usuarios.stream()
                    .map(Usuario::getNombreUsuario)
                    .sorted()
                    .toList());
        } catch (SQLException e) {
            // silencioso — dropdown vacío si falla la carga
        }
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
        tecnicoSeleccionado = null;
        txtFiltroTecnico.clear();
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }

    static boolean coincideFiltro(LogActividad log, String texto, LocalDate desde, LocalDate hasta,
                                   String tecnico) {
        boolean coincideTexto = texto == null || texto.isBlank()
                || contiene(log.getNombreUsuario(), texto)
                || contiene(log.getAccion(), texto)
                || contiene(log.getDetalle(), texto);

        boolean coincideTecnico = tecnico == null || tecnico.isBlank()
                || tecnico.equalsIgnoreCase(log.getNombreUsuario());

        LocalDate fecha = FechaUtils.toLocalDate(log.getFecha());
        boolean coincideDesde = desde == null || fecha == null || !fecha.isBefore(desde);
        boolean coincideHasta = hasta == null || fecha == null || !fecha.isAfter(hasta);

        return coincideTexto && coincideTecnico && coincideDesde && coincideHasta;
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto.toLowerCase().trim());
    }
}
