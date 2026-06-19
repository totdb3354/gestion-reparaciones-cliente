package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.FechaUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PulidoTecnicoController {

    @FXML private TableView<ReparacionResumen>            tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, Boolean> cCheck;
    @FXML private TableColumn<ReparacionResumen, String>  cId;
    @FXML private TableColumn<ReparacionResumen, String>  cImei;
    @FXML private TableColumn<ReparacionResumen, String>  cModelo;
    @FXML private TableColumn<ReparacionResumen, String>  cFecha;
    @FXML private TableColumn<ReparacionResumen, String>  cComentario;
    @FXML private TextField filtroImei;
    @FXML private Button    btnCompletarSeleccionados;
    @FXML private Label     lblUltimaActualizacion;
    @FXML private Label     lblContador;

    private final PulidoDAO pulidoDAO = new PulidoDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;
    private final Set<String> seleccionados = new HashSet<>();

    @FXML
    public void initialize() {
        cCheck.setCellValueFactory(d ->
            new javafx.beans.property.SimpleBooleanProperty(seleccionados.contains(d.getValue().getIdRep())));
        cCheck.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setOnAction(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    String id = getTableView().getItems().get(getIndex()).getIdRep();
                    if (cb.isSelected()) seleccionados.add(id);
                    else seleccionados.remove(id);
                    actualizarBotonCompletar();
                });
            }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                cb.setSelected(seleccionados.contains(getTableView().getItems().get(getIndex()).getIdRep()));
                setGraphic(cb);
            }
        });

        cId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cImei.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getImei()));
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cFecha.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            FechaUtils.formatear(d.getValue().getFechaAsig(), FMT)));
        cComentario.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getComentarioAsignacion() != null ? d.getValue().getComentarioAsignacion() : ""));

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPulidos.setItems(datosFiltrados);
        datosFiltrados.addListener((javafx.collections.ListChangeListener<ReparacionResumen>) c -> actualizarContador());
        actualizarContador();
        tablaPulidos.setColumnResizePolicy(param -> true);

        tablaPulidos.setRowFactory(tv -> new TableRow<>() {
            {
                selectedProperty().addListener((obs, o, sel) -> actualizarEstilo());
            }
            private void actualizarEstilo() {
                if (isEmpty() || getItem() == null) { setStyle(""); return; }
                if (isSelected()) {
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                            "-fx-border-width: 0 0 1 0;");
                } else {
                    setStyle("-fx-border-width: 0 0 1 0; -fx-border-color: transparent transparent " +
                            com.reparaciones.utils.Colores.FILA_SEP + " transparent;");
                }
            }
            @Override protected void updateItem(ReparacionResumen item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });

        tablaPulidos.getColumns().forEach(c -> c.setReorderable(false));
        filtroImei.textProperty().addListener((obs, o, n) -> {
            String withoutSep = n.replace(", ", ",");
            String limpio = withoutSep.replaceAll("[^\\d,]", "").replaceAll(",+", ",").replaceAll("^,", "");
            if (!limpio.equals(withoutSep)) { String can = limpio.replace(",", ", "); javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); }); return; }
            String[] partes = n.split(",", -1);
            if (partes[partes.length - 1].trim().length() == 15 && !n.endsWith(", ") && !n.endsWith(",")) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(n + ", "); filtroImei.positionCaret(filtroImei.getText().length()); }); return;
            }
            boolean hayIncompleto = java.util.Arrays.stream(n.split(",", -1))
                    .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(s -> s.length() < 15);
            boolean hayValido = !parsearImeis(n).isEmpty();
            if (n.trim().isEmpty())
                filtroImei.setStyle("");
            else if (hayIncompleto)
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else if (hayValido)
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else
                filtroImei.setStyle("");
            aplicarFiltro();
        });
        cargar();
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> cargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
    }

    private void aplicarFiltro() {
        if (datosFiltrados == null) return;
        java.util.Set<String> imeisFiltro = parsearImeis(filtroImei.getText().trim());
        datosFiltrados.setPredicate(rep -> imeisFiltro.isEmpty() || imeisFiltro.contains(rep.getImei()));
    }

    private void actualizarContador() {
        if (lblContador == null || datosFiltrados == null) return;
        int n = datosFiltrados.size();
        lblContador.setText((n > 999 ? "999+" : String.valueOf(n)) + (n == 1 ? " pendiente" : " pendientes"));
    }

    @FXML private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        aplicarFiltro();
    }

    private static java.util.Set<String> parsearImeis(String texto) {
        if (texto == null || texto.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(java.util.stream.Collectors.toSet());
    }

    @FXML
    private void seleccionarTodo() {
        if (seleccionados.size() == datos.size() && !datos.isEmpty()) {
            seleccionados.clear();
        } else {
            datos.forEach(r -> seleccionados.add(r.getIdRep()));
        }
        tablaPulidos.refresh();
        actualizarBotonCompletar();
    }

    @FXML
    private void completarSeleccionados() {
        if (seleccionados.isEmpty()) return;
        List<String> ids = List.copyOf(seleccionados);
        try {
            pulidoDAO.completarPulidoLote(ids);
            seleccionados.clear();
            cargar();
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    private void actualizarBotonCompletar() {
        if (btnCompletarSeleccionados != null)
            btnCompletarSeleccionados.setDisable(seleccionados.isEmpty());
    }

    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return datosFiltrados != null ? new java.util.ArrayList<>(datosFiltrados) : java.util.List.of();
    }

    public int getTotalItems() { return datos.size(); }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    public void cargar() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (idTec == null) return;
            seleccionados.clear();
            datos.setAll(pulidoDAO.getAsignacionesPulidoPorTecnico(idTec));
            tablaPulidos.refresh();
            actualizarBotonCompletar();
            String hora = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }
}
