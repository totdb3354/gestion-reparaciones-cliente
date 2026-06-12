package com.reparaciones.controllers;

import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import com.reparaciones.utils.MultiSelectComboBox;
import java.util.HashSet;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Popup;

/**
 * Controlador de la tabla de asignaciones pendientes (vista del supertécnico).
 * <p>Incrustado como controlador anidado en {@link ReparacionControllerSuperTecnico}.
 * Muestra todas las asignaciones ({@code A*}) con estado pendiente y permite:</p>
 * <ul>
 *   <li>Crear nuevas asignaciones para cualquier técnico y IMEI.</li>
 *   <li>Reasignar asignaciones a otro técnico.</li>
 *   <li>Eliminar asignaciones sin reparación asociada.</li>
 *   <li>Filtrar por IMEI, técnico y solicitudes pendientes de componente.</li>
 * </ul>
 *
 * @role SUPERTECNICO
 */
public class PendientesSuperTecnicoController {

    @FXML private TableView<ReparacionResumen>           tablaPendientes;
    @FXML private TableColumn<ReparacionResumen, Void>   cEstado;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;
    @FXML private TextField  filtroImei;
    @FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private MenuButton filtroSolicitud;

    private final ReparacionDAO  reparacionDAO = new ReparacionDAO();
    private final TecnicoDAO     tecnicoDAO    = new TecnicoDAO();
    private final TelefonoDAO    telefonoDAO   = new TelefonoDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;
    private Runnable onActualizar;

    @FXML private Button btnConfirmarCambios;
    @FXML private Button btnDescartarCambios;
    @FXML private Label  lblUltimaActualizacion;

    private CheckBox cbSoloSolicitudes;
    private CheckBox cbSoloIncidencias;
    private CheckBox cbSoloAsignaciones;
    private final Set<Integer>   idsTecFiltro  = new HashSet<>();
    private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
    private final List<Tecnico>         tecnicos         = new ArrayList<>();
    private record CambioPendiente(int idTec, String nombreTecnico, String comentarioAsignacion, java.time.LocalDateTime updatedAt) {}
    private final Map<String, CambioPendiente> cambiosPendientes = new HashMap<>();

    /** Una entrada del lote de asignación: un IMEI con su configuración local (aún no en BD). */
    private static final class EntradaAsignacion {
        final String imei;
        String modeloCode;                       // código interno del modelo, o null si falta
        final List<Tecnico> tecnicos = new ArrayList<>();
        String comentario = "";
        boolean asignada;                        // true = verde (configurada y movida); false = rojo (pendiente)
        boolean modeloBuscado;                   // true si ya se lanzó el lookup (no repetir)
        boolean buscando;                        // true mientras el lookup está en vuelo

        EntradaAsignacion(String imei) { this.imei = imei; }

        boolean tieneModelo() { return modeloCode != null && !modeloCode.isEmpty(); }
    }

    @FXML
    public void initialize() {
        tablaPendientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);


        cId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cTecnico.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<Tecnico> cb = new ComboBox<>();
            private boolean actualizando = false;
            {
                cb.setMaxWidth(Double.MAX_VALUE);
                cb.setVisibleRowCount(8);
                cb.setStyle("-fx-font-size: 11px;");
                cb.setConverter(new javafx.util.StringConverter<>() {
                    @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
                    @Override public Tecnico fromString(String s) { return null; }
                });
                cb.setCellFactory(lv -> new ListCell<>() {
                    {
                        hoverProperty().addListener((obs, o, n) -> actualizarEstiloItem());
                        selectedProperty().addListener((obs, o, n) -> actualizarEstiloItem());
                    }
                    private void actualizarEstiloItem() {
                        if (isEmpty() || getItem() == null) { setStyle(""); return; }
                        setStyle(isSelected() || isHover()
                            ? "-fx-background-color: #001232; -fx-background-radius: 8; -fx-text-fill: #FAFAFA;"
                            : "-fx-text-fill: #001232;");
                    }
                    @Override protected void updateItem(Tecnico t, boolean empty) {
                        super.updateItem(t, empty);
                        if (empty || t == null) { setText(null); setStyle(""); return; }
                        setText(t.getNombre());
                        actualizarEstiloItem();
                    }
                });
                cb.setOnAction(e -> {
                    if (actualizando) return;
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    ReparacionResumen rep = getTableView().getItems().get(getIndex());
                    Tecnico sel = cb.getValue();
                    if (sel == null) return;
                    if (sel.getIdTec() != rep.getIdTec()) {
                        String comentarioActual = cambiosPendientes.containsKey(rep.getIdRep())
                            ? cambiosPendientes.get(rep.getIdRep()).comentarioAsignacion()
                            : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
                        cambiosPendientes.put(rep.getIdRep(),
                            new CambioPendiente(sel.getIdTec(), sel.getNombre(), comentarioActual, rep.getUpdatedAt()));
                    } else {
                        CambioPendiente existing = cambiosPendientes.get(rep.getIdRep());
                        if (existing != null) {
                            String repComentario  = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                            String extComentario  = existing.comentarioAsignacion() != null ? existing.comentarioAsignacion() : "";
                            if (!extComentario.equals(repComentario)) {
                                cambiosPendientes.put(rep.getIdRep(),
                                    new CambioPendiente(rep.getIdTec(), rep.getNombreTecnico(), existing.comentarioAsignacion(), rep.getUpdatedAt()));
                            } else {
                                cambiosPendientes.remove(rep.getIdRep());
                            }
                        }
                    }
                    actualizarVisibilidadConfirmar();
                    CambioPendiente cambioActual = cambiosPendientes.get(rep.getIdRep());
                    boolean mod = cambioActual != null && cambioActual.idTec() != rep.getIdTec();
                    setStyle(mod ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";" : "");
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                if (cb.isShowing()) return;
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                actualizando = true;
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                cb.getItems().setAll(tecnicos);
                CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
                Tecnico mostrar = cambio != null
                    ? tecnicos.stream().filter(t -> t.getIdTec() == cambio.idTec()).findFirst().orElse(null)
                    : tecnicos.stream().filter(t -> t.getIdTec() == rep.getIdTec()).findFirst().orElse(null);
                cb.setValue(mostrar);
                actualizando = false;
                boolean modificada = cambio != null && cambio.idTec() != rep.getIdTec();
                setStyle(modificada
                        ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";"
                        : "");
                setGraphic(cb);
            }
        });
        cImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3B54;");
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(selListener);
                    if (newRow != null) newRow.selectedProperty().addListener(selListener);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                lbl.setText(getTableView().getItems().get(getIndex()).getImei());
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                setGraphic(lbl);
            }
        });
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cFecha.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getFechaAsig() != null ? d.getValue().getFechaAsig().format(FMT) : ""));
        cComentario.setCellValueFactory(d -> {
            ReparacionResumen rep = d.getValue();
            CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
            String texto = cambio != null ? cambio.comentarioAsignacion()
                                          : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
            return new javafx.beans.property.SimpleStringProperty(texto);
        });
        cComentario.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null); setStyle(""); return;
                }
                setText(item);
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
                String repComentario    = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                String cambioComentario = cambio != null ? (cambio.comentarioAsignacion() != null ? cambio.comentarioAsignacion() : "") : repComentario;
                boolean modificado = cambio != null && !cambioComentario.equals(repComentario);
                setStyle(modificado ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";" : "");
            }
        });

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPendientes.setItems(datosFiltrados);
        tablaPendientes.setColumnResizePolicy(param -> true);

        Image imgEditar = new Image(getClass().getResourceAsStream("/images/editar.png"));
        tablaPendientes.setRowFactory(tv -> new TableRow<>() {
            {
                ContextMenu menu = new ContextMenu();
                TableColumn<?, ?>[] colRightClick = {null};
                MenuItem copiar = new MenuItem("📋  Copiar celda");
                copiar.setOnAction(e -> {
                    if (getItem() == null || colRightClick[0] == null) return;
                    TableColumn<?, ?> col = colRightClick[0];
                    String texto = textoDeCelda(getItem(), col);
                    if (texto == null || texto.isEmpty()) return;
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(texto);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    getChildrenUnmodifiable().stream()
                        .filter(n -> n instanceof TableCell && ((TableCell<?, ?>) n).getTableColumn() == col)
                        .findFirst()
                        .ifPresent(cell -> {
                            javafx.beans.property.DoubleProperty flashAlpha = new javafx.beans.property.SimpleDoubleProperty(1.0);
                            flashAlpha.addListener((obs2, o2, n2) -> {
                                double a = n2.doubleValue();
                                if (a <= 0.02) cell.setStyle("");
                                else cell.setStyle(String.format(java.util.Locale.US,
                                    "-fx-background-color: rgba(224,247,250,%.2f);", a));
                            });
                            cell.setStyle("-fx-background-color: rgba(224,247,250,1.0);");
                            new javafx.animation.Timeline(
                                new javafx.animation.KeyFrame(javafx.util.Duration.millis(600),
                                    new javafx.animation.KeyValue(flashAlpha, 0.0))
                            ).play();
                        });
                });
                menu.getItems().add(copiar);
                MenuItem editarComentario = new MenuItem("Editar comentario");
                ImageView ivEditar = new ImageView(imgEditar);
                ivEditar.setFitWidth(14); ivEditar.setFitHeight(14); ivEditar.setPreserveRatio(true);
                editarComentario.setGraphic(ivEditar);
                editarComentario.setOnAction(e -> {
                    if (getItem() == null) return;
                    ReparacionResumen rep = getItem();
                    String actual = cambiosPendientes.containsKey(rep.getIdRep())
                        ? cambiosPendientes.get(rep.getIdRep()).comentarioAsignacion()
                        : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
                    PendientesSuperTecnicoController.this.abrirEditorComentario(rep, actual);
                });
                menu.getItems().add(editarComentario);
                MenuItem toggleUrgente = new MenuItem();
                toggleUrgente.setOnAction(e -> {
                    if (getItem() == null) return;
                    ReparacionResumen rep = getItem();
                    boolean nuevoEstado = !rep.isUrgente();
                    try {
                        reparacionDAO.actualizarUrgente(rep.getIdRep(), nuevoEstado);
                        rep.setUrgente(nuevoEstado);
                        tablaPendientes.refresh();
                    } catch (java.sql.SQLException ex) { mostrarError(ex); }
                });
                menu.setOnShowing(e -> {
                    if (getItem() != null)
                        toggleUrgente.setText(getItem().isUrgente() ? "Quitar urgente" : "Marcar urgente");
                });
                menu.getItems().add(toggleUrgente);
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });
                selectedProperty().addListener((obs, o, sel) -> actualizarEstilo());
            }
            private void actualizarEstilo() {
                ReparacionResumen item = getItem();
                if (isEmpty() || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (isSelected()) {
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                            "-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;");
                    return;
                }
                if (item.getEsSolicitud() > 0) {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_SOLICITUD_BRD + ";");
                } else if (item.isEsIncidencia()) {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                } else {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " transparent;");
                }
            }
            @Override
            protected void updateItem(ReparacionResumen item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });

        cEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badgeUrgente = new Label();
            private final Label badge        = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badge);
            { celdaBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT); }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                              "-fx-font-size: 11px; -fx-font-weight: bold;";
                badge.setVisible(true); badge.setManaged(true);
                if (rep.isUrgente()) {
                    badgeUrgente.setText("Urgente");
                    badgeUrgente.setStyle(base +
                        "-fx-background-color: #FDDEDE; -fx-text-fill: " + com.reparaciones.utils.Colores.FILA_URGENTE_BRD + ";");
                    badgeUrgente.setVisible(true); badgeUrgente.setManaged(true);
                } else {
                    badgeUrgente.setVisible(false); badgeUrgente.setManaged(false);
                }
                if (rep.isEsIncidencia()) {
                    badge.setText("Incidencia");
                    badge.setStyle(base +
                        "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + ";" +
                        "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                } else if (rep.getEsSolicitud() > 0) {
                    boolean recibido = "GESTIONADA".equals(rep.getEstadoSolicitud())
                                       && rep.getStockSolicitud() > 0;
                    boolean enCamino = rep.isEnCaminoSolicitud();
                    if (recibido) {
                        badge.setText("Recibido");
                        badge.setStyle(base +
                            "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32;");
                    } else if (enCamino) {
                        badge.setText("En camino");
                        badge.setStyle(base +
                            "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;");
                    } else {
                        badge.setText("Solicitud");
                        badge.setStyle(base +
                            "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_SOLICITUD_BG + ";" +
                            "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_SOLICITUD_BRD + ";");
                    }
                } else if (!rep.isUrgente()) {
                    badge.setText("Normal");
                    badge.setStyle(base +
                        "-fx-background-color: #E8EAF0;" +
                        "-fx-text-fill: #586376;");
                } else {
                    badge.setVisible(false); badge.setManaged(false);
                }
                setGraphic(celdaBox);
            }
        });

        Image imgBorrar = new Image(getClass().getResourceAsStream("/images/borrar.png"));
        cAccion.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv  = new ImageView(imgBorrar);
            private final HBox      box = new HBox(iv);
            {
                iv.setFitWidth(25); iv.setFitHeight(25); iv.setPreserveRatio(true);
                iv.setStyle("-fx-cursor: hand;");
                box.setAlignment(Pos.CENTER);
                iv.setOnMouseClicked(e -> {
                    ReparacionResumen rep = getTableView().getItems().get(getIndex());
                    String desc = "El técnico dejará de verla en su lista de pendientes" +
                            (rep.isEsIncidencia()
                                    ? " y la incidencia se marcará como no activa en la tabla principal."
                                    : ".");
                    ConfirmDialog.mostrar("Borrar asignación " + rep.getIdRep(), desc,
                            "Borrar asignación", () -> {
                                try {
                                    if (rep.isEsIncidencia())
                                        reparacionDAO.borrarIncidenciaPorImei(rep.getImei());
                                    else
                                        reparacionDAO.eliminarAsignacion(rep.getIdRep());
                                    cargar();
                                    if (onActualizar != null) onActualizar.run();
                                } catch (SQLException ex) { mostrarError(ex); }
                            });
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tablaPendientes.getColumns().forEach(c -> c.setReorderable(false));
        configurarFiltros();
        cargar();
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    private void configurarFiltros() {
        // Filtro técnico
        try {
            tecnicos.addAll(tecnicoDAO.getAllActivos());
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicos,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
        } catch (SQLException e) { mostrarError(e); }

        // Filtro tipo
        cbSoloSolicitudes = new CheckBox("Solicitudes pieza");
        cbSoloSolicitudes.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbSoloSolicitudes.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroSolicitud();
            aplicarFiltros();
        });
        cbSoloIncidencias = new CheckBox("Incidencias");
        cbSoloIncidencias.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbSoloIncidencias.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroSolicitud();
            aplicarFiltros();
        });
        cbSoloAsignaciones = new CheckBox("Asignaciones");
        cbSoloAsignaciones.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbSoloAsignaciones.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroSolicitud();
            aplicarFiltros();
        });
        CustomMenuItem itemSol  = new CustomMenuItem(cbSoloSolicitudes, false);
        CustomMenuItem itemInc  = new CustomMenuItem(cbSoloIncidencias, false);
        CustomMenuItem itemAsig = new CustomMenuItem(cbSoloAsignaciones, false);
        filtroSolicitud.getItems().addAll(itemSol, itemInc, itemAsig);

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
            aplicarFiltros();
        });
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> cargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
    }

    private void actualizarTextoFiltroTecnico() {
        long sel = idsTecFiltro.size();
        if (sel == 0) {
            etiquetaTec.set("Técnico");
        } else if (sel == 1) {
            int id = idsTecFiltro.iterator().next();
            String nombre = tecnicos.stream()
                    .filter(t -> t.getIdTec() == id)
                    .findFirst().map(Tecnico::getNombre).orElse("Técnico");
            etiquetaTec.set(nombre);
        } else {
            etiquetaTec.set(sel + " técnicos");
        }
        // The button cell observes etiquetaTec and updates its own text automatically.
    }

    private void actualizarTextoFiltroSolicitud() {
        boolean sol  = cbSoloSolicitudes.isSelected();
        boolean inc  = cbSoloIncidencias.isSelected();
        boolean asig = cbSoloAsignaciones.isSelected();
        long total = java.util.stream.Stream.of(sol, inc, asig).filter(Boolean::booleanValue).count();
        if      (total == 0) filtroSolicitud.setText("Tipo");
        else if (total == 3) filtroSolicitud.setText("Todas");
        else if (total == 1) filtroSolicitud.setText(sol ? "Solicitudes pieza" : inc ? "Incidencias" : "Asignaciones");
        else                 filtroSolicitud.setText(total + " filtros");
    }

    private void aplicarFiltros() {
        if (datosFiltrados == null) return;
        List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
        boolean filtrarSol  = cbSoloSolicitudes.isSelected();
        boolean filtrarInc  = cbSoloIncidencias.isSelected();
        boolean filtrarAsig = cbSoloAsignaciones.isSelected();

        String imeiStr = filtroImei != null ? filtroImei.getText().trim() : "";
        java.util.Set<String> imeisFiltro = parsearImeis(imeiStr);
        datosFiltrados.setPredicate(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            if (filtrarSol || filtrarInc || filtrarAsig) {
                boolean esSol  = rep.getEsSolicitud() > 0;
                boolean esInc  = rep.isEsIncidencia();
                boolean esAsig = !esSol && !esInc;
                boolean mostrar = false;
                if (filtrarSol  && esSol)  mostrar = true;
                if (filtrarInc  && esInc)  mostrar = true;
                if (filtrarAsig && esAsig) mostrar = true;
                if (!mostrar) return false;
            }
            return true;
        });
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        idsTecFiltro.clear();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        etiquetaTec.set("Técnico");
        cbSoloSolicitudes.setSelected(false);
        cbSoloIncidencias.setSelected(false);
        cbSoloAsignaciones.setSelected(false);
        filtroSolicitud.setText("Tipo");
        aplicarFiltros();
    }

    public void setOnActualizar(Runnable onActualizar) { this.onActualizar = onActualizar; }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    // ─── Carga ────────────────────────────────────────────────────────────────

    public void cargar() {
        try {
            tablaPendientes.getSelectionModel().clearSelection();
            datos.setAll(reparacionDAO.getAsignaciones());
            String hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    /** Construye una fila de la pila para {@code e}. onClick = cargar en el formulario; onRemove = quitar de la pila. */
    private HBox crearFilaPila(EntradaAsignacion e, Runnable onClick, Runnable onRemove) {
        Label lblImei = new Label(e.imei);
        lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

        Label estado = new Label();
        if (e.tieneModelo()) {
            estado.setText(FormularioReparacionController.traducirModelo(e.modeloCode));
            estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #1B5E20;"
                    + " -fx-background-color: #E3F1E4; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
        } else if (e.buscando) {
            estado.setText("Buscando…");
            estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #586376;"
                    + " -fx-background-color: #EEF1F5; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
        } else {
            estado.setText("⚠ falta modelo");
            estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #9A6B00;"
                    + " -fx-background-color: #FCE7C3; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
        }

        HBox pills = new HBox(4);
        pills.setAlignment(Pos.CENTER_LEFT);
        for (Tecnico t : e.tecnicos) {
            Label p = new Label(t.getNombre());
            p.setStyle("-fx-font-size: 9.5px; -fx-text-fill: white; -fx-background-color: #001232;"
                    + " -fx-background-radius: 20; -fx-padding: 1 7 1 7;");
            pills.getChildren().add(p);
        }

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label x = new Label("✕");
        String xBase = "-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
        String xHover = "-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
        x.setStyle(xBase);
        x.setOnMouseEntered(ev -> x.setStyle(xHover));
        x.setOnMouseExited(ev -> x.setStyle(xBase));
        x.setOnMouseClicked(ev -> { ev.consume(); onRemove.run(); });

        HBox fila = new HBox(8, lblImei, estado, pills, spacer, x);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 7 9 7 9; -fx-border-color: transparent transparent #EEF1F5 transparent;"
                + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
        fila.setOnMouseClicked(ev -> onClick.run());
        return fila;
    }

    @FXML
    private void abrirFormularioAsignacion() {
        // ── Estado del lote ──────────────────────────────────────────────────
        List<EntradaAsignacion> pila = new ArrayList<>();
        EntradaAsignacion[] actual = { null };
        boolean[] editandoVerde = { false };
        List<Tecnico> defTecnicos = new ArrayList<>();
        String[] defComentario = { "" };

        List<Tecnico> tecnicosModal = new ArrayList<>();
        try { tecnicosModal.addAll(tecnicoDAO.getAllActivos()); }
        catch (SQLException ex) { mostrarError(ex); }

        // ── Cabecera + escaneo ───────────────────────────────────────────────
        Label lblTitulo = new Label("Asignar reparaciones");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label lblSub = new Label("Escanea IMEIs y configúralos. Técnicos y comentario se mantienen entre IMEIs. Se guardan todos al final.");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");

        Label lblScan = new Label("Escanear IMEI → pendiente de asignar");
        lblScan.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
        tfScan.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-padding: 11; -fx-text-fill: #2C3B54; -fx-font-size: 14px;");
        Label lblScanErr = new Label();
        lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");

        // ── Secciones de la pila ─────────────────────────────────────────────
        Label lblRojo = new Label("Pendiente de asignar (0)");
        lblRojo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
        VBox boxRojo = new VBox(0);
        boxRojo.setStyle("-fx-background-color: white; -fx-border-color: #EFC4C0; -fx-border-radius: 6; -fx-border-width: 1;");
        Label lblVerde = new Label("Asignados (0) · sin guardar");
        lblVerde.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2E7D32; -fx-padding: 10 0 0 0;");
        VBox boxVerde = new VBox(0);
        boxVerde.setStyle("-fx-background-color: white; -fx-border-color: #BFE0C2; -fx-border-radius: 6; -fx-border-width: 1;");
        VBox pilaBox = new VBox(6, lblRojo, boxRojo, lblVerde, boxVerde);
        ScrollPane scrollPila = new ScrollPane(pilaBox);
        scrollPila.setFitToWidth(true);
        scrollPila.setPrefViewportWidth(250);
        scrollPila.setMinWidth(250);
        scrollPila.setPrefHeight(330);
        scrollPila.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPila.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ── Maquinaria de modelo (buscador) ──────────────────────────────────
        Label lblModelo = new Label("Modelo de iPhone");
        lblModelo.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        javafx.collections.ObservableList<String> todosModelos =
                FXCollections.observableArrayList(FormularioReparacionController.MODELOS_ORDENADOS);
        FilteredList<String> modelosFiltrados = new FilteredList<>(todosModelos, s -> true);
        TextField tfModelo = new TextField();
        tfModelo.setPromptText("Escribe modelo...");
        tfModelo.setMaxWidth(Double.MAX_VALUE);
        tfModelo.setStyle(
                "-fx-background-color: #001232; -fx-background-radius: 24;" +
                "-fx-border-color: transparent; -fx-border-radius: 24; -fx-border-width: 0;" +
                "-fx-text-fill: #FAFAFA; -fx-font-size: 12px; -fx-font-weight: bold;" +
                "-fx-padding: 4 12 4 12;");
        ListView<String> listaModelos = new ListView<>(modelosFiltrados);
        listaModelos.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");
        listaModelos.setFixedCellSize(30);
        listaModelos.setPrefWidth(344);
        listaModelos.setCellFactory(lv -> new ListCell<>() {
            {
                setOnMouseEntered(e -> { if (!isEmpty() && getItem() != null)
                    setStyle("-fx-background-color: #001232; -fx-background-radius: 8;" +
                            "-fx-background-insets: 2 6 2 6;" +
                            "-fx-text-fill: white; -fx-font-size: 12px;" +
                            "-fx-font-weight: bold; -fx-padding: 6 12 6 12;"); });
                setOnMouseExited(e -> { if (!isEmpty() && getItem() != null)
                    setStyle("-fx-background-color: white; -fx-text-fill: #001232;" +
                            "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12 6 12;"); });
            }
            @Override protected void updateItem(String code, boolean empty) {
                super.updateItem(code, empty);
                if (empty || code == null) { setText(null); setStyle(""); }
                else { setText(FormularioReparacionController.traducirModelo(code));
                    setStyle("-fx-background-color: white; -fx-text-fill: #001232;" +
                            "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12 6 12;"); }
            }
        });
        javafx.stage.Popup popupModelo = new javafx.stage.Popup();
        popupModelo.setAutoHide(true);
        popupModelo.getContent().add(listaModelos);
        String[] modeloSel = { null };
        boolean[] actualizandoModelo = { false };

        // ── Técnicos ─────────────────────────────────────────────────────────
        Label lblTecnicos = new Label("Técnicos a asignar");
        lblTecnicos.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        Label pillAsignados = new Label();
        pillAsignados.setStyle(
                "-fx-background-color: #FCE7C3; -fx-text-fill: #9A6B00;" +
                "-fx-font-size: 10.5px; -fx-font-weight: bold;" +
                "-fx-background-radius: 20; -fx-padding: 2 9 2 9;");
        pillAsignados.setVisible(false);
        pillAsignados.setManaged(false);
        HBox headerTecnicos = new HBox(8, lblTecnicos, pillAsignados);
        headerTecnicos.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        List<CheckBox> checkboxes = new ArrayList<>();
        VBox cbContainer = new VBox(6);
        cbContainer.setStyle("-fx-background-color: white; -fx-padding: 8;");
        for (Tecnico t : tecnicosModal) {
            CheckBox cb = new CheckBox(t.getNombre());
            cb.setStyle("-fx-font-size: 12px;");
            checkboxes.add(cb);
            cbContainer.getChildren().add(cb);
        }
        ScrollPane scrollTecnicos = new ScrollPane(cbContainer);
        scrollTecnicos.setFitToWidth(true);
        scrollTecnicos.setMaxHeight(150);
        scrollTecnicos.setPrefHeight(Math.min(150, tecnicosModal.size() * 30 + 16));
        scrollTecnicos.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4;");
        Label lblNotaPersist = new Label("↳ Se mantienen del IMEI anterior; cámbialos solo si hace falta.");
        lblNotaPersist.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #586376; -fx-font-style: italic;");

        // ── Comentario + cabecera del IMEI en curso ──────────────────────────
        Label lblComentario = new Label("Comentario (opcional)");
        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea tfComentario = new TextArea();
        tfComentario.setWrapText(true);
        tfComentario.setPrefRowCount(2);
        tfComentario.setPromptText("Instrucciones para el técnico...");
        tfComentario.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4;" +
                "-fx-text-fill: #2C3B54; -fx-font-size: 13px;");
        Label lblImeiCursoCap = new Label("IMEI en curso");
        lblImeiCursoCap.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        Label lblImeiCurso = new Label("—");
        lblImeiCurso.setStyle("-fx-font-family: monospace; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

        Button btnAsignar = new Button("Asignar →");
        btnAsignar.getStyleClass().add("btn-primary");
        btnAsignar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnAsignar, javafx.scene.layout.Priority.ALWAYS);
        Button btnSaltar = new Button("Saltar");
        btnSaltar.getStyleClass().add("btn-secondary");
        HBox accionesForm = new HBox(10, btnSaltar, btnAsignar);

        VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, lblModelo, tfModelo,
                headerTecnicos, scrollTecnicos, lblNotaPersist, lblComentario, tfComentario, accionesForm);
        formBox.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-border-width: 1; -fx-padding: 16;");
        HBox.setHgrow(formBox, javafx.scene.layout.Priority.ALWAYS);
        formBox.setDisable(true);

        // ── Barra final ──────────────────────────────────────────────────────
        Label lblProg = new Label("");
        lblProg.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        javafx.scene.layout.Region spacerBarra = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerBarra, javafx.scene.layout.Priority.ALWAYS);
        Button btnGuardar = new Button("Guardar (0)");
        btnGuardar.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-font-size: 14px;"
                + " -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 11 22 11 22;");
        btnGuardar.setDisable(true);
        HBox barraFinal = new HBox(12, lblProg, spacerBarra, btnGuardar);
        barraFinal.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        barraFinal.setStyle("-fx-padding: 14 0 0 0; -fx-border-color: #C2C8D0 transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        // ── Helpers de orquestación ──────────────────────────────────────────
        Runnable[] renderPila = new Runnable[1];
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<EntradaAsignacion>[] cargarEntrada = new java.util.function.Consumer[1];
        Runnable[] lanzarLookup = new Runnable[1];

        Runnable validarForm = () -> {
            boolean modeloOk = modeloSel[0] != null;
            boolean algunoSel = checkboxes.stream().anyMatch(cb -> cb.isSelected() && !cb.isDisabled());
            btnAsignar.setDisable(!(modeloOk && algunoSel));
        };

        Runnable mostrarPopupModelo = () -> {
            if (modelosFiltrados.isEmpty() || tfModelo.getScene() == null) { popupModelo.hide(); return; }
            listaModelos.setPrefHeight(Math.min(modelosFiltrados.size(), 6) * 28 + 4);
            if (!popupModelo.isShowing()) {
                javafx.geometry.Bounds b = tfModelo.localToScreen(tfModelo.getBoundsInLocal());
                if (b != null) popupModelo.show(tfModelo, b.getMinX(), b.getMaxY() + 1);
            }
        };
        java.util.function.Consumer<String> confirmarModelo = code -> {
            modeloSel[0] = code;
            if (actual[0] != null) actual[0].modeloCode = code;
            actualizandoModelo[0] = true;
            tfModelo.setText(FormularioReparacionController.traducirModelo(code));
            modelosFiltrados.setPredicate(s -> true);
            actualizandoModelo[0] = false;
            popupModelo.hide();
            if (renderPila[0] != null) renderPila[0].run();
            validarForm.run();
        };

        renderPila[0] = () -> {
            boxRojo.getChildren().clear();
            boxVerde.getChildren().clear();
            int nRojo = 0, nVerde = 0;
            for (EntradaAsignacion e : pila) {
                Runnable onClick = () -> cargarEntrada[0].accept(e);
                Runnable onRemove = () -> {
                    pila.remove(e);
                    if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
                    renderPila[0].run();
                };
                HBox fila = crearFilaPila(e, onClick, onRemove);
                if (e.asignada) { boxVerde.getChildren().add(fila); nVerde++; }
                else            { boxRojo.getChildren().add(fila);  nRojo++; }
            }
            lblRojo.setText("Pendiente de asignar (" + nRojo + ")");
            lblVerde.setText("Asignados (" + nVerde + ") · sin guardar");
            int sinModelo = (int) pila.stream().filter(e -> !e.asignada && !e.tieneModelo()).count();
            lblProg.setText(nVerde + " configurados · " + nRojo + " pendientes" + (sinModelo > 0 ? " · " + sinModelo + " sin modelo" : ""));
            btnGuardar.setText("Guardar (" + nVerde + ")");
            btnGuardar.setDisable(nRojo != 0 || nVerde == 0);
        };

        lanzarLookup[0] = () -> {
            EntradaAsignacion e = actual[0];
            if (e == null || e.tieneModelo() || e.modeloBuscado) return;
            e.modeloBuscado = true;
            e.buscando = true;
            tfModelo.setPromptText("Buscando...");
            renderPila[0].run();
            Thread t = new Thread(() -> {
                String modelo = null;
                try { modelo = telefonoDAO.getModelo(e.imei); } catch (Exception ignore) {}
                String res = modelo;
                javafx.application.Platform.runLater(() -> {
                    e.buscando = false;
                    if (res != null && !res.isEmpty()) {
                        e.modeloCode = res;
                        if (actual[0] == e) confirmarModelo.accept(res);
                    } else if (actual[0] == e) {
                        tfModelo.setPromptText("No encontrado — selecciona manualmente");
                    }
                    renderPila[0].run();
                });
            });
            t.setDaemon(true);
            t.start();
        };

        cargarEntrada[0] = (EntradaAsignacion e) -> {
            actual[0] = e;
            editandoVerde[0] = e.asignada;
            formBox.setDisable(false);
            lblImeiCurso.setText(e.imei);
            actualizandoModelo[0] = true;
            modeloSel[0] = e.modeloCode;
            tfModelo.setText(e.tieneModelo() ? FormularioReparacionController.traducirModelo(e.modeloCode) : "");
            modelosFiltrados.setPredicate(s -> true);
            actualizandoModelo[0] = false;
            List<Tecnico> base = (e.asignada || !e.tecnicos.isEmpty()) ? e.tecnicos : defTecnicos;
            java.util.Set<Integer> ids = base.stream().map(Tecnico::getIdTec).collect(java.util.stream.Collectors.toSet());
            for (int i = 0; i < tecnicosModal.size(); i++)
                checkboxes.get(i).setSelected(ids.contains(tecnicosModal.get(i).getIdTec()));
            tfComentario.setText((e.asignada || !e.comentario.isEmpty()) ? e.comentario : defComentario[0]);
            try {
                List<Integer> ocupados = reparacionDAO.getTecnicosConAsignacionActiva(e.imei);
                for (int i = 0; i < tecnicosModal.size(); i++) {
                    boolean ocup = ocupados.contains(tecnicosModal.get(i).getIdTec());
                    checkboxes.get(i).setDisable(ocup);
                    if (ocup) checkboxes.get(i).setSelected(false);
                }
                long n = checkboxes.stream().filter(CheckBox::isDisabled).count();
                pillAsignados.setText(n + (n == 1 ? " asignado" : " asignados"));
                pillAsignados.setVisible(n >= 1);
                pillAsignados.setManaged(n >= 1);
            } catch (SQLException ex) { /* silencioso */ }
            btnAsignar.setText(e.asignada ? "Guardar cambios" : "Asignar →");
            if (!e.asignada) lanzarLookup[0].run();
            validarForm.run();
        };

        Runnable cargarSiguienteRojo = () -> {
            EntradaAsignacion sig = pila.stream().filter(x -> !x.asignada).findFirst().orElse(null);
            if (sig != null) cargarEntrada[0].accept(sig);
            else { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
        };

        Runnable asignarActual = () -> {
            EntradaAsignacion e = actual[0];
            if (e == null || modeloSel[0] == null) return;
            List<Tecnico> sel = new ArrayList<>();
            for (int i = 0; i < tecnicosModal.size(); i++)
                if (checkboxes.get(i).isSelected() && !checkboxes.get(i).isDisabled()) sel.add(tecnicosModal.get(i));
            if (sel.isEmpty()) return;
            e.modeloCode = modeloSel[0];
            e.tecnicos.clear(); e.tecnicos.addAll(sel);
            e.comentario = tfComentario.getText().trim();
            e.asignada = true;
            defTecnicos.clear(); defTecnicos.addAll(sel);
            defComentario[0] = e.comentario;
            renderPila[0].run();
            if (editandoVerde[0]) { editandoVerde[0] = false; actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
            else cargarSiguienteRojo.run();
        };

        // ── Listeners modelo ─────────────────────────────────────────────────
        tfModelo.textProperty().addListener((obs, oldText, newText) -> {
            if (actualizandoModelo[0]) return;
            if (modeloSel[0] != null && FormularioReparacionController.traducirModelo(modeloSel[0]).equals(newText)) return;
            modeloSel[0] = null;
            if (actual[0] != null) actual[0].modeloCode = null;
            String lower = newText == null ? "" : newText.trim().toLowerCase();
            modelosFiltrados.setPredicate(c -> lower.isEmpty()
                    || FormularioReparacionController.traducirModelo(c).toLowerCase().contains(lower));
            mostrarPopupModelo.run();
            if (renderPila[0] != null) renderPila[0].run();
            validarForm.run();
        });
        tfModelo.setOnAction(e -> {
            if (!modelosFiltrados.isEmpty()) confirmarModelo.accept(modelosFiltrados.get(0));
        });
        tfModelo.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) javafx.application.Platform.runLater(() -> {
                popupModelo.hide();
                String texto = tfModelo.getText() == null ? "" : tfModelo.getText().trim();
                if (modeloSel[0] != null && FormularioReparacionController.traducirModelo(modeloSel[0]).equals(texto)) {
                    modelosFiltrados.setPredicate(s -> true);
                    return;
                }
                String exacto = todosModelos.stream()
                        .filter(c -> FormularioReparacionController.traducirModelo(c).equalsIgnoreCase(texto))
                        .findFirst().orElse(null);
                if (exacto != null) {
                    confirmarModelo.accept(exacto);
                } else {
                    actualizandoModelo[0] = true;
                    tfModelo.setText(modeloSel[0] != null ? FormularioReparacionController.traducirModelo(modeloSel[0]) : "");
                    modelosFiltrados.setPredicate(s -> true);
                    actualizandoModelo[0] = false;
                }
            });
        });
        listaModelos.setOnMouseClicked(e -> {
            String sel = listaModelos.getSelectionModel().getSelectedItem();
            if (sel != null) confirmarModelo.accept(sel);
        });
        listaModelos.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String sel = listaModelos.getSelectionModel().getSelectedItem();
                if (sel != null) confirmarModelo.accept(sel);
            }
        });

        // ── Escaneo (añadir a la pila con dedup) ─────────────────────────────
        Runnable intentarAnadir = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            if (pila.stream().anyMatch(x -> x.imei.equals(imei))) { lblScanErr.setText("Ese IMEI ya está en la pila."); return; }
            lblScanErr.setText("");
            EntradaAsignacion e = new EntradaAsignacion(imei);
            pila.add(e);
            renderPila[0].run();
            tfScan.clear();
            cargarEntrada[0].accept(e);
            javafx.application.Platform.runLater(tfScan::requestFocus);
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) { tfScan.setText(n.replaceAll("[^\\d]", "")); return; }
            if (n.length() > 15) { tfScan.setText(n.substring(0, 15)); return; }
            lblScanErr.setText("");
            if (n.length() == 15) intentarAnadir.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) intentarAnadir.run(); });

        checkboxes.forEach(cb -> cb.selectedProperty().addListener((obs, o, n) -> validarForm.run()));
        btnAsignar.setOnAction(ev -> asignarActual.run());
        btnSaltar.setOnAction(ev -> cargarSiguienteRojo.run());

        // ── Layout + ventana ─────────────────────────────────────────────────
        HBox cols = new HBox(18, scrollPila, formBox);
        VBox contenido = new VBox(12, lblTitulo, lblSub, lblScan, tfScan, lblScanErr,
                new Separator(), cols, barraFinal);
        contenido.setPadding(new Insets(26));
        contenido.setPrefWidth(680);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ventana.setResizable(true);
        ventana.setMinWidth(680);
        ventana.setMinHeight(560);
        ventana.setTitle("Asignar reparaciones");

        btnGuardar.setOnAction(ev -> {
            List<String> conflictos = new ArrayList<>();
            try {
                for (EntradaAsignacion e : pila) {
                    if (!e.asignada) continue;
                    List<Integer> ocupados = reparacionDAO.getTecnicosConAsignacionActiva(e.imei);
                    telefonoDAO.insertar(e.imei, e.modeloCode);
                    for (Tecnico t : e.tecnicos) {
                        if (ocupados.contains(t.getIdTec())) { conflictos.add("• " + e.imei + " → " + t.getNombre() + " (ya asignado)"); continue; }
                        reparacionDAO.insertarAsignacion(e.imei, t.getIdTec(),
                                e.comentario.isEmpty() ? null : e.comentario);
                    }
                }
            } catch (SQLException ex) { mostrarError(ex); return; }
            ventana.close();
            cargar();
            if (!conflictos.isEmpty())
                new Alert(Alert.AlertType.WARNING, "Algunas asignaciones no se crearon:\n\n" + String.join("\n", conflictos)).showAndWait();
        });

        ventana.setOnCloseRequest(ev -> {
            if (pila.isEmpty()) return;
            ev.consume();
            ConfirmDialog.mostrar("Descartar", "Se descartarán los " + pila.size() + " IMEIs de la pila.",
                    "Descartar", ventana::close);
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        renderPila[0].run();
        javafx.application.Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }

    public void resetearCambios() {
        cambiosPendientes.clear();
        actualizarVisibilidadConfirmar();
        cargar();
    }

    private void actualizarVisibilidadConfirmar() {
        boolean hay = !cambiosPendientes.isEmpty();
        btnConfirmarCambios.setVisible(hay);
        btnConfirmarCambios.setManaged(hay);
        btnDescartarCambios.setVisible(hay);
        btnDescartarCambios.setManaged(hay);
    }

    @FXML
    private void confirmarCambiosTecnico() {
        List<String> conflictos = new ArrayList<>();
        for (Map.Entry<String, CambioPendiente> entry : new java.util.ArrayList<>(cambiosPendientes.entrySet())) {
            String          idRep   = entry.getKey();
            CambioPendiente cambio  = entry.getValue();
            ReparacionResumen rep = datos.stream()
                    .filter(r -> r.getIdRep().equals(idRep)).findFirst().orElse(null);
            if (rep == null) continue;
            try {
                reparacionDAO.actualizarAsignacion(idRep, cambio.idTec(), cambio.comentarioAsignacion(), cambio.updatedAt());
                rep.setIdTec(cambio.idTec());
                rep.setNombreTecnico(cambio.nombreTecnico());
                rep.setComentarioAsignacion(cambio.comentarioAsignacion());
                cambiosPendientes.remove(idRep);
            } catch (com.reparaciones.utils.StaleDataException e) {
                conflictos.add(mensajeConflicto(idRep, cambio.idTec()));
            } catch (SQLException e) { mostrarError(e); }
        }
        if (!conflictos.isEmpty()) {
            String detalle = String.join("\n", conflictos);
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                    "Los siguientes cambios no se pudieron guardar:\n\n" + detalle +
                    "\n\nLos datos se han recargado.")
                    .showAndWait();
            cargar();
        }
        actualizarVisibilidadConfirmar();
        cargar();
        if (onActualizar != null) onActualizar.run();
    }

    private String mensajeConflicto(String idRep, int idTecIntentado) {
        try {
            java.util.Optional<ReparacionResumen> actual = reparacionDAO.getAsignacionById(idRep);
            if (actual.isEmpty())
                return "• " + idRep + ": ya no está pendiente (fue completada por otro usuario).";
            ReparacionResumen rep = actual.get();
            if (rep.getIdTec() != idTecIntentado)
                return "• " + idRep + ": fue reasignada a " + rep.getNombreTecnico() + " por otro usuario.";
            return "• " + idRep + ": fue modificada por otro usuario.";
        } catch (SQLException e) {
            return "• " + idRep + ": fue modificada por otro usuario.";
        }
    }

    /** @return los ítems actualmente visibles en la tabla (respetando filtros activos) */
    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return tablaPendientes.getItems();
    }

    public int getTotalItems() { return datos.size(); }

    private void abrirEditorComentario(ReparacionResumen rep, String textoActual) {
        Label lblTitulo = new Label("Comentario de asignación");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

        TextArea ta = new TextArea(textoActual);
        ta.setWrapText(true);
        ta.setPrefRowCount(4);
        ta.setPrefWidth(340);
        ta.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4;" +
                "-fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        Button btnGuardar = new Button("Guardar");
        btnGuardar.getStyleClass().add("btn-primary");
        Button btnCerrar = new Button("Cancelar");
        btnCerrar.getStyleClass().add("btn-secondary");

        HBox botones = new HBox(10, btnCerrar, btnGuardar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox contenido = new VBox(12, lblTitulo, ta, botones);
        contenido.setPadding(new Insets(24));
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ventana.setResizable(false);
        ventana.setTitle("Comentario");

        btnCerrar.setOnAction(e -> ventana.close());
        btnGuardar.setOnAction(e -> {
            String nuevoTexto = ta.getText();
            CambioPendiente existing = cambiosPendientes.get(rep.getIdRep());
            int idTecActual = existing != null ? existing.idTec() : rep.getIdTec();
            String nombreTecActual = existing != null ? existing.nombreTecnico() : rep.getNombreTecnico();
            String repComentario = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
            if (idTecActual == rep.getIdTec() && nuevoTexto.equals(repComentario)) {
                cambiosPendientes.remove(rep.getIdRep());
            } else {
                cambiosPendientes.put(rep.getIdRep(),
                    new CambioPendiente(idTecActual, nombreTecActual, nuevoTexto, rep.getUpdatedAt()));
            }
            actualizarVisibilidadConfirmar();
            tablaPendientes.refresh();
            ventana.close();
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        javafx.application.Platform.runLater(ta::requestFocus);
        ventana.showAndWait();
    }

    private String textoDeCelda(ReparacionResumen rep, TableColumn<?, ?> col) {
        if (col == cId)        return rep.getIdRep();
        if (col == cImei)      return rep.getImei();
        if (col == cModelo)    { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == cFecha)     return rep.getFechaAsig() != null ? rep.getFechaAsig().format(FMT) : "";
        if (col == cComentario){ String c = rep.getComentarioAsignacion(); return c != null ? c : ""; }
        return null;
    }

    private static java.util.Set<String> parsearImeis(String texto) {
        if (texto == null || texto.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
