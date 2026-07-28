package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.TelefonoInventario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.utils.SituacionRevision;
import com.reparaciones.utils.UbicacionTexto;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * F2b: panel "Revisión" de la pestaña Inventario — cola de teléfonos EN_REVISION con
 * escáner de un IMEI y acceso al masivo "A revisar". Sin endpoint propio (decisión de
 * plan nº3): reutiliza {@link TelefonoDAO#getInventario()} y filtra en cliente.
 */
public class RevisionPanelController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    @FXML private TextField tfScan;
    @FXML private Label lblScan;
    @FXML private Button btnMasivo;
    @FXML private Button btnAbrirFicha;
    @FXML private TableView<TelefonoInventario> tabla;
    @FXML private TableColumn<TelefonoInventario, String> colImei;
    @FXML private TableColumn<TelefonoInventario, String> colModelo;
    @FXML private TableColumn<TelefonoInventario, String> colLote;
    @FXML private TableColumn<TelefonoInventario, String> colSituacion;
    @FXML private TableColumn<TelefonoInventario, String> colEstetica;
    @FXML private TableColumn<TelefonoInventario, String> colFuncional;
    @FXML private TableColumn<TelefonoInventario, String> colDesde;

    /** Inventario completo (todos los estados), para resolver el escáner sin ir al servidor. */
    private List<TelefonoInventario> inventario = List.of();

    @FXML
    public void initialize() {
        configurarTabla();
        configurarEscaner();
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> btnAbrirFicha.setDisable(n == null));

        if (!Sesion.esSuperTecnico()) {
            btnMasivo.setVisible(false); btnMasivo.setManaged(false);
            tfScan.setPromptText("Enter abre la ficha (consulta)");
        }
    }

    private void configurarTabla() {
        colImei.setCellValueFactory(c -> sp(c.getValue().getImei()));
        colModelo.setCellValueFactory(c -> sp(c.getValue().getModelo() + (c.getValue().isEsEsim() ? " eSIM" : "")));
        colLote.setCellValueFactory(c -> sp(c.getValue().getBatchNumber()));
        colSituacion.setCellValueFactory(c -> sp(SituacionRevision.texto(SituacionRevision.de(c.getValue()))));
        colEstetica.setCellValueFactory(c -> sp(c.getValue().getEstFecha() != null ? "✓ " + c.getValue().getEstUsuario() : "pend."));
        colFuncional.setCellValueFactory(c -> sp(c.getValue().getFunFecha() != null ? "✓ " + c.getValue().getFunUsuario() : "pend."));
        colDesde.setCellValueFactory(c -> sp(FechaUtils.formatear(c.getValue().getRevDesde(), FMT)));

        tabla.setRowFactory(tv -> {
            TableRow<TelefonoInventario> row = new TableRow<>() {
                @Override
                protected void updateItem(TelefonoInventario item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setStyle(""); return; }
                    setStyle(SituacionRevision.de(item) == SituacionRevision.Situacion.EN_REPARACION
                            ? "-fx-opacity: 0.55;" : "");
                }
            };
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 2) abrirFicha(row.getItem());
            });
            return row;
        });
        tabla.getColumns().forEach(c -> c.setReorderable(false));
    }

    private void configurarEscaner() {
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) {
                String solo = n.replaceAll("[^\\d]", "");
                Platform.runLater(() -> tfScan.setText(solo));
                return;
            }
            if (n.length() > 15) {
                String recortado = n.substring(0, 15);
                Platform.runLater(() -> tfScan.setText(recortado));
                return;
            }
            if (n.length() == 15) buscarImei();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) buscarImei(); });
    }

    private void buscarImei() {
        String imei = tfScan.getText().trim();
        if (imei.length() != 15) return;

        TelefonoInventario t = inventario.stream()
                .filter(x -> imei.equals(x.getImei()))
                .findFirst().orElse(null);

        if (t == null) {
            lblScan.setText("No existe en el sistema");
            return;
        }
        String estado = t.getEstado();
        if ("EN_REVISION".equals(estado)) {
            tfScan.clear();
            lblScan.setText("");
            abrirFicha(t);
        } else if (estado == null) {
            lblScan.setText("Histórico — dar de alta en un lote");
        } else {
            lblScan.setText("Está " + UbicacionTexto.estado(t) + " — usar A revisar (masivo) si procede");
        }
    }

    private void abrirFicha(TelefonoInventario t) {
        FichaRevisionDialog.abrir(tabla.getScene().getWindow(), t, this::cargar);
        tfScan.requestFocus();
    }

    @FXML private void abrirMasivo() {
        ARevisarDialog.abrir(tabla.getScene().getWindow(), this::cargar);
    }

    @FXML
    private void abrirFichaSeleccion() {
        TelefonoInventario t = tabla.getSelectionModel().getSelectedItem();
        if (t != null) abrirFicha(t);
    }

    /** Recarga el inventario completo y repuebla la tabla con la cola EN_REVISION (revDesde asc, nulls al final). */
    public void cargar() {
        new Thread(() -> {
            List<TelefonoInventario> datos = null;
            String error = null;
            try {
                datos = new TelefonoDAO().getInventario();
            } catch (SQLException e) {
                error = e.getMessage();
            }
            List<TelefonoInventario> datosFinal = datos;
            String errorFinal = error;
            Platform.runLater(() -> {
                if (errorFinal != null) { Alertas.mostrarError(errorFinal); return; }
                inventario = datosFinal;
                List<TelefonoInventario> cola = inventario.stream()
                        .filter(t -> "EN_REVISION".equals(t.getEstado()))
                        .sorted(Comparator.comparing(TelefonoInventario::getRevDesde,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.toList());
                tabla.setItems(FXCollections.observableArrayList(cola));
            });
        }, "cargar-revision-panel").start();
    }

    private static SimpleStringProperty sp(String v) {
        return new SimpleStringProperty(v);
    }
}
