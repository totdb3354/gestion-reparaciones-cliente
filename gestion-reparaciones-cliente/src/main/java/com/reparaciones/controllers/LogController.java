package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.dao.UsuarioDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.models.Usuario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogController {

    @FXML private TableView<LogActividad>           tablaLogs;
    @FXML private TableColumn<LogActividad, String> colFecha;
    @FXML private TableColumn<LogActividad, String> colUsuario;
    @FXML private TableColumn<LogActividad, String> colAccion;
    @FXML private TableColumn<LogActividad, String> colDetalle;
    @FXML private TextField                         txtBuscadorLogs;
    @FXML private TextField                         txtFiltroAccion;
    @FXML private TextField                         txtFiltroTecnico;
    @FXML private DatePicker                        dpLogsDesde;
    @FXML private DatePicker                        dpLogsHasta;

    private final LogDAO     logDAO     = new LogDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObservableList<LogActividad> logsMaster = FXCollections.observableArrayList();
    private String accionSeleccionada  = null;
    private String tecnicoSeleccionado = null;

    private static final List<String> TIPOS_ACCION = List.of(
        "CREAR_ASIGNACION", "ACTUALIZAR_ASIGNACION", "CAMBIAR_PRIORIDAD",
        "EDITAR_REPARACION", "COMPLETAR_REPARACION", "ELIMINAR_ASIGNACION",
        "ELIMINAR_REPARACION", "CREAR_PEDIDO", "EDITAR_PEDIDO", "RECIBIR_PEDIDO",
        "RECIBIR_PARCIAL", "CANCELAR_PEDIDO", "CONFIRMAR_PEDIDO", "BORRAR_PEDIDO",
        "CREAR_COMPONENTE", "EDITAR_COMPONENTE", "ELIMINAR_COMPONENTE",
        "CREAR_TECNICO", "ELIMINAR_TECNICO", "CREAR_USUARIO",
        "ACTIVAR_USUARIO", "DESACTIVAR_USUARIO", "ELIMINAR_USUARIO",
        "LOGIN", "CAMBIAR_PASSWORD"
    );

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

        // Buscador de texto — client-side sobre logsMaster
        FilteredList<LogActividad> logsFiltrados = new FilteredList<>(logsMaster, l -> true);
        txtBuscadorLogs.textProperty().addListener((obs, o, n) ->
                logsFiltrados.setPredicate(log -> coincideTexto(log, n)));
        tablaLogs.setItems(logsFiltrados);

        // Filtros server-side — cada cambio recarga logsMaster
        dpLogsDesde.valueProperty().addListener((obs, o, n) -> cargarLogs());
        dpLogsHasta.valueProperty().addListener((obs, o, n) -> cargarLogs());
        dpLogsDesde.getEditor().setDisable(true);
        dpLogsDesde.getEditor().setOpacity(1.0);
        dpLogsHasta.getEditor().setDisable(true);
        dpLogsHasta.getEditor().setOpacity(1.0);

        // Doble clic en fila → mostrar detalle completo con motivo
        tablaLogs.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                LogActividad sel = tablaLogs.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                String texto = sel.getDetalle() != null ? sel.getDetalle() : "";
                if (sel.getMotivo() != null && !sel.getMotivo().isBlank()) {
                    texto += "\n\nMOTIVO: " + sel.getMotivo();
                }
                ConfirmDialog.mostrarTexto("Detalle del log", texto);
            }
        });

        configurarFiltroAccion();
        configurarFiltroTecnico();
        cargarLogs();
    }

    private void configurarFiltroAccion() {
        ObservableList<String> acciones = FXCollections.observableArrayList(TIPOS_ACCION);
        FilteredList<String> accionesFiltradas = new FilteredList<>(acciones, s -> true);

        ListView<String> listaAcciones = new ListView<>(accionesFiltradas);
        listaAcciones.setFixedCellSize(28);
        listaAcciones.setPrefWidth(200);
        listaAcciones.setMaxHeight(224);
        listaAcciones.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 6, 0, 0, 2);");

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listaAcciones);

        Runnable mostrarPopup = () -> {
            if (!popup.isShowing() && txtFiltroAccion.getScene() != null) {
                javafx.geometry.Bounds b =
                        txtFiltroAccion.localToScreen(txtFiltroAccion.getBoundsInLocal());
                if (b != null) popup.show(txtFiltroAccion, b.getMinX(), b.getMaxY() + 2);
            }
        };

        txtFiltroAccion.setOnMouseClicked(e -> mostrarPopup.run());

        txtFiltroAccion.textProperty().addListener((obs, o, n) -> {
            String text = n == null ? "" : n.trim().toLowerCase();
            accionesFiltradas.setPredicate(s -> text.isEmpty() || s.toLowerCase().contains(text));
            if (text.isEmpty() && accionSeleccionada != null) {
                accionSeleccionada = null;
                cargarLogs();
            }
        });

        listaAcciones.setOnMouseClicked(e -> {
            String sel = listaAcciones.getSelectionModel().getSelectedItem();
            if (sel != null) {
                accionSeleccionada = sel;
                txtFiltroAccion.setText(sel);
                popup.hide();
                cargarLogs();
            }
        });
    }

    private void configurarFiltroTecnico() {
        ObservableList<String> todosUsuarios = FXCollections.observableArrayList();
        FilteredList<String> usuariosFiltrados = new FilteredList<>(todosUsuarios, s -> true);

        ListView<String> listaUsuarios = new ListView<>(usuariosFiltrados);
        listaUsuarios.setFixedCellSize(28);
        listaUsuarios.setPrefWidth(160);
        listaUsuarios.setMaxHeight(224);
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
                cargarLogs();
            }
        });

        listaUsuarios.setOnMouseClicked(e -> {
            String sel = listaUsuarios.getSelectionModel().getSelectedItem();
            if (sel != null) {
                tecnicoSeleccionado = sel;
                txtFiltroTecnico.setText(sel);
                popup.hide();
                cargarLogs();
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
            List<LogActividad> logs = logDAO.getAll(
                    accionSeleccionada,
                    tecnicoSeleccionado,
                    dpLogsDesde.getValue(),
                    dpLogsHasta.getValue());
            logsMaster.setAll(logs);
        } catch (SQLException e) {
            Alertas.mostrarError("Error al cargar los logs: " + e.getMessage());
        }
    }

    @FXML
    private void limpiarFiltrosLogs() {
        accionSeleccionada  = null;
        tecnicoSeleccionado = null;
        txtBuscadorLogs.clear();
        txtFiltroAccion.clear();
        txtFiltroTecnico.clear();
        dpLogsDesde.setValue(null);
        dpLogsHasta.setValue(null);
        cargarLogs();
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }

    private static boolean coincideTexto(LogActividad log, String texto) {
        if (texto == null || texto.isBlank()) return true;
        String t = texto.toLowerCase().trim();
        return contiene(log.getNombreUsuario(), t)
                || contiene(log.getAccion(), t)
                || contiene(log.getDetalle(), t);
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto);
    }
}
