package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.GlassDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.utils.TipoTrabajo;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/**
 * Controlador de la tabla de asignaciones pendientes del técnico.
 * <p>Incrustado como controlador anidado tanto en {@link ReparacionControllerTecnico}
 * (sección "Mis pendientes") como en {@link ReparacionControllerSuperTecnico}
 * (sección "Mis pendientes" del supertécnico que también repara).</p>
 * <p>Muestra las asignaciones ({@code A*}) del usuario en sesión y permite
 * finalizarlas abriendo el formulario de reparación.</p>
 *
 * @role TECNICO; SUPERTECNICO (si tiene técnico asignado)
 */
public class PendientesTecnicoController {

    @FXML private TableView<ReparacionResumen>           tablaPendientes;
    @FXML private TableColumn<ReparacionResumen, Void>   cEstado;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, Void>   cTipo;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cCliente;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private TableColumn<ReparacionResumen, String> cAsignadoPor;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;
    @FXML private TableColumn<ReparacionResumen, Void>   cBorrar;
    @FXML private MenuButton filtroSolicitud;
    @FXML private TextField  filtroImei;
    @FXML private Label      lblUltimaActualizacion;
    @FXML private Label      lblContador;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final GlassDAO      glassDAO      = new GlassDAO();
    private boolean             glass         = false;   // true → pestaña Glass (lista AG, completa como G)
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;
    private CheckBox cbSoloSolicitudes;
    private CheckBox cbSoloIncidencias;
    private CheckBox cbSoloAsignaciones;

    private Runnable onCerrar;

    @FXML
    public void initialize() {
        tablaPendientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);


        cId.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cTipo.setCellFactory(col -> TipoTrabajo.celdaTipoConChasis());
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
        cCliente.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getCliente() != null ? d.getValue().getCliente() : ""));
        cFecha.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                FechaUtils.formatear(d.getValue().getFechaAsig(), FMT)));
        cComentario.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getComentarioAsignacion() != null ? d.getValue().getComentarioAsignacion() : ""));
        cAsignadoPor.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getNombreTecnicoAsigna() != null ? d.getValue().getNombreTecnicoAsigna() : "—"));

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPendientes.setItems(datosFiltrados);
        datosFiltrados.addListener((javafx.collections.ListChangeListener<ReparacionResumen>) c -> actualizarContador());
        actualizarContador();
        tablaPendientes.setColumnResizePolicy(param -> true);
        tablaPendientes.getColumns().forEach(c -> c.setSortable(false));   // el orden lo llevan los filtros/prioridad, no el clic en la cabecera

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
                MenuItem togglePorCerrar = new MenuItem("Marcar por cerrar");
                togglePorCerrar.setOnAction(e -> {
                    ReparacionResumen rep = getItem();
                    if (rep == null) return;
                    try {
                        reparacionDAO.actualizarPorCerrar(rep.getIdRep(), !rep.isPorCerrar());
                        cargar();
                    } catch (SQLException ex) { mostrarError(ex); }
                });
                menu.getItems().add(togglePorCerrar);
                menu.setOnShowing(ev -> {
                    ReparacionResumen rep = getItem();
                    boolean esRepNormal = rep != null && !glass
                            && TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.REPARACION;
                    togglePorCerrar.setVisible(esRepNormal);
                    if (esRepNormal) togglePorCerrar.setText(rep.isPorCerrar() ? "Quitar por cerrar" : "Marcar por cerrar");
                });
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
            @Override protected void updateItem(ReparacionResumen item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });

        cEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badgeUrgente   = new Label();
            private final Label badgePorCerrar = new Label("Por cerrar");
            private final Label badge          = new Label();
            private final Label lblTipo        = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badgePorCerrar, badge, lblTipo);
            { celdaBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT); }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                              "-fx-font-size: 11px; -fx-font-weight: bold;";
                lblTipo.setText("");
                badge.setVisible(true); badge.setManaged(true);
                if (rep.isUrgente()) {
                    badgeUrgente.setText("Urgente");
                    badgeUrgente.setStyle(base +
                        "-fx-background-color: #FDDEDE; -fx-text-fill: " + com.reparaciones.utils.Colores.FILA_URGENTE_BRD + ";");
                    badgeUrgente.setVisible(true); badgeUrgente.setManaged(true);
                } else {
                    badgeUrgente.setVisible(false); badgeUrgente.setManaged(false);
                }
                if (rep.isPorCerrar()) {
                    badgePorCerrar.setStyle(base + "-fx-background-color: #E0F2F1; -fx-text-fill: #00796B;");
                    badgePorCerrar.setVisible(true); badgePorCerrar.setManaged(true);
                } else {
                    badgePorCerrar.setVisible(false); badgePorCerrar.setManaged(false);
                }
                if (rep.isEsIncidencia()) {
                    badge.setText("Incidencia");
                    badge.setStyle(base +
                        "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + ";" +
                        "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                } else if (rep.getEsSolicitud() > 0) {
                    boolean recibido  = "GESTIONADA".equals(rep.getEstadoSolicitud())
                                        && rep.getStockSolicitud() > 0;
                    boolean enCamino  = rep.isEnCaminoSolicitud();
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
                    String todos = rep.getTiposSolicitud();
                    javafx.scene.control.Tooltip.uninstall(celdaBox, null);
                    if (todos != null && !todos.isEmpty()) {
                        boolean multiples = rep.getEsSolicitud() > 1;
                        lblTipo.setText(multiples ? rep.getEsSolicitud() + " piezas" : todos);
                        lblTipo.setStyle("-fx-font-size: 10px; -fx-text-fill: #586376;");
                        javafx.scene.control.Tooltip.install(celdaBox,
                                new javafx.scene.control.Tooltip(todos));
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

        cAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().add("btn-primary");
                btn.setOnAction(e -> {
                    ReparacionResumen asig = getTableView().getItems().get(getIndex());
                    Runnable alCerrar = () -> {
                        cargar();
                        if (onCerrar != null) onCerrar.run();
                    };
                    // Glass: el modal se filtra a glass/marco/otro y genera G (el prefijo AG lo activa).
                    FormularioReparacionController.abrir(
                            asig.getImei(), null, asig.getIdRep(), alCerrar);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) btn.setText(glass ? "Añadir glass" : "Añadir reparación");
                setGraphic(empty ? null : btn);
            }
        });

        Image imgBorrar = new Image(getClass().getResourceAsStream("/images/borrar.png"));
        if (Sesion.esSuperTecnico()) {
            cBorrar.setCellFactory(col -> new TableCell<>() {
                private final ImageView ivBorrar = new ImageView(imgBorrar);
                private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(ivBorrar);
                {
                    ivBorrar.setFitWidth(25); ivBorrar.setFitHeight(25); ivBorrar.setPreserveRatio(true);
                    ivBorrar.setStyle("-fx-cursor: hand;");
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    ivBorrar.setOnMouseClicked(e -> {
                        ReparacionResumen rep = getTableView().getItems().get(getIndex());
                        String desc = "El técnico dejará de verla en su lista de pendientes" +
                                (rep.isEsIncidencia()
                                        ? " y la incidencia se marcará como no activa en la tabla principal."
                                        : ".");
                        ConfirmDialog.mostrar("Borrar asignación " + rep.getIdRep(), desc,
                                "Borrar asignación", () -> {
                                    try {
                                        if (rep.isEsIncidencia())
                                            reparacionDAO.borrarIncidenciaPorImei(rep.getImei(), glass ? "G" : "R");
                                        else
                                            reparacionDAO.eliminarAsignacion(rep.getIdRep());   // sirve para A y AG
                                        cargar();
                                        if (onCerrar != null) onCerrar.run();
                                    } catch (SQLException ex) { mostrarError(ex); }
                                });
                    });
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : box);
                }
            });
        } else {
            cBorrar.setVisible(false);
        }

        tablaPendientes.getColumns().forEach(c -> c.setReorderable(false));
        configurarFiltros();
        cargar();
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> cargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    private void configurarFiltros() {

        cbSoloSolicitudes  = new CheckBox("Solicitudes pieza");
        cbSoloIncidencias  = new CheckBox("Incidencias");
        cbSoloAsignaciones = new CheckBox("Asignaciones");

        for (CheckBox cb : new CheckBox[]{cbSoloSolicitudes, cbSoloIncidencias, cbSoloAsignaciones}) {
            cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
            cb.selectedProperty().addListener((obs, o, n) -> {
                actualizarTextoFiltroSolicitud();
                aplicarFiltros();
            });
            CustomMenuItem item = new CustomMenuItem(cb, false);
            filtroSolicitud.getItems().add(item);
        }
        if (filtroImei != null) {
            filtroImei.textProperty().addListener((obs, o, n) -> {
                String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
                if (!can.equals(n)) {
                    javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                    return;
                }
                switch (com.reparaciones.utils.FiltroImei.estado(n)) {
                    case VACIO      -> filtroImei.setStyle("");
                    case INCOMPLETO -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                    case VALIDO     -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";");
                }
                aplicarFiltros();
            });
        }
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
        boolean filtrarSol  = cbSoloSolicitudes.isSelected();
        boolean filtrarInc  = cbSoloIncidencias.isSelected();
        boolean filtrarAsig = cbSoloAsignaciones.isSelected();
        String imeiStr = filtroImei != null ? filtroImei.getText().trim() : "";
        java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(imeiStr);
        datosFiltrados.setPredicate(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!filtrarSol && !filtrarInc && !filtrarAsig) return true;
            boolean esSol  = rep.getEsSolicitud() > 0;
            boolean esInc  = rep.isEsIncidencia();
            boolean esAsig = !esSol && !esInc;
            boolean mostrar = false;
            if (filtrarSol  && esSol)  mostrar = true;
            if (filtrarInc  && esInc)  mostrar = true;
            if (filtrarAsig && esAsig) mostrar = true;
            return mostrar;
        });
    }

    private void actualizarContador() {
        if (lblContador == null || datosFiltrados == null) return;
        int n = datosFiltrados.size();
        lblContador.setText((n > 999 ? "999+" : String.valueOf(n)) + (n == 1 ? " pendiente" : " pendientes"));
    }

    @FXML
    private void limpiarFiltros() {
        if (filtroImei != null) { filtroImei.clear(); filtroImei.setStyle(""); }
        cbSoloSolicitudes.setSelected(false);
        cbSoloIncidencias.setSelected(false);
        cbSoloAsignaciones.setSelected(false);
        filtroSolicitud.setText("Tipo");
    }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    // ─── Carga ────────────────────────────────────────────────────────────────

    public void cargar() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (idTec == null) return;
            datos.setAll(glass ? glassDAO.getAsignacionesGlassPorTecnico(idTec)
                               : reparacionDAO.getAsignacionesPorTecnico(idTec));
            // Orden de prioridad: urgente (0) -> con cliente (1) -> normal (2). Estable dentro de cada grupo.
            datos.sort(java.util.Comparator.comparingInt((ReparacionResumen r) -> {
                if (r.isUrgente()) return 0;
                if (r.getCliente() != null && !r.getCliente().isEmpty()) return 1;
                return 2;
            }));
            String hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    public void setOnCerrar(Runnable onCerrar) {
        this.onCerrar = onCerrar;
    }

    /** Activa el modo Glass: lista asignaciones {@code AG} y al completar genera {@code G}. Llamar antes de {@link #cargar()}. */
    public void setModoGlass() { this.glass = true; }

    /** @return los ítems actualmente visibles en la tabla (respetando filtros activos) */
    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return tablaPendientes.getItems();
    }

    public int getTotalItems() { return datos.size(); }

    /**
     * Extrae el texto copiable de la celda seleccionada para la acción "Copiar celda".
     *
     * @param rep datos de la fila
     * @param col columna seleccionada
     * @return texto de la celda, o {@code null} si la columna no es copiable
     */
    private String textoDeCelda(ReparacionResumen rep, TableColumn<?, ?> col) {
        if (col == cId)        return rep.getIdRep();
        if (col == cImei)      return rep.getImei();
        if (col == cModelo)    { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == cFecha)     return FechaUtils.formatear(rep.getFechaAsig(), FMT);
        if (col == cComentario){ String c = rep.getComentarioAsignacion(); return c != null ? c : ""; }
        return null;
    }

    private void mostrarError(Exception e) {
        if (e instanceof com.reparaciones.utils.ConexionException
                && com.reparaciones.utils.ConexionEstado.enRefresco()) return;   // refresco: lo indica el banner
        Alertas.mostrarError(e.getMessage());
    }
}