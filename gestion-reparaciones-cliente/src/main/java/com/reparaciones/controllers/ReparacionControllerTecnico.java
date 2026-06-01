package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.models.GrupoImei;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;

import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador de la vista de reparaciones para el rol TECNICO.
 * <p>Presenta dos secciones accesibles desde el sidebar:</p>
 * <ul>
 *   <li><b>Historial</b> — tabla con las reparaciones propias del técnico, filtrable
 *       por IMEI, rango de fechas e incidencias. Solo lectura (sin edición ni eliminación).
 *       Soporta modo maestro (agrupado por IMEI) y detalle (drill-down).</li>
 *   <li><b>Mis pendientes</b> — asignaciones del técnico, gestionadas por
 *       {@link PendientesTecnicoController} (incrustado como controlador anidado).</li>
 * </ul>
 * <p>Incluye un poller periódico que recarga en segundo plano para detectar nuevas
 * asignaciones sin que el técnico tenga que navegar manualmente.</p>
 * <p>Implementa {@link com.reparaciones.utils.Recargable} y
 * {@link com.reparaciones.utils.Exportable}.</p>
 *
 * @role TECNICO
 */
public class ReparacionControllerTecnico implements com.reparaciones.utils.Recargable, com.reparaciones.utils.Exportable {

    @FXML private TableView<Object>         tablaReparaciones;
    @FXML private TableColumn<Object, String> colIdRep;
    @FXML private TableColumn<Object, String> colImei;
    @FXML private TableColumn<Object, String> colModelo;
    @FXML private TableColumn<Object, String> colReparador;
    @FXML private TableColumn<Object, String> colFecha;
    @FXML private TableColumn<Object, String> colComponente;
    @FXML private TableColumn<Object, String> colObservaciones;
    @FXML private TableColumn<Object, Void>   colEstado;
    @FXML private TableColumn<Object, Void>   colIncidencia;
    @FXML private TableColumn<Object, String> colIdAnterior;
    @FXML private TextField  filtroImei;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    // ── Sidebar + paneles ─────────────────────────────────────────────────────
    @FXML private Button btnTabHistorial;
    @FXML private Button btnTabMisPendientes;
    @FXML private VBox   pnlHistorial;
    @FXML private VBox   pnlMisPendientes;
    @FXML private Label  lblUltimaActualizacion;
    @FXML private PendientesTecnicoController misPendientesController;

    // ── Toggles y sub-paneles de pulido ───────────────────────────────────────
    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistPul;
    @FXML private VBox pnlHistRep;
    @FXML private VBox pnlHistPul;
    @FXML private HistorialPulidoController historialPulidoController;

    @FXML private javafx.scene.control.ToggleButton togglePendRep;
    @FXML private javafx.scene.control.ToggleButton togglePendPul;
    @FXML private VBox pnlPendRep;
    @FXML private VBox pnlPendPul;
    @FXML private PulidoTecnicoController pulidoTecnicoController;
    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;
    private CustomMenuItem itemCerradas;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private List<ReparacionResumen> datosFiltrados = new ArrayList<>();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // ── Drill-down ────────────────────────────────────────────────────────────
    private enum Modo { MAESTRO, DETALLE }
    private Modo   modoActual    = Modo.MAESTRO;
    private String imeiDetalle   = null;
    private HBox   barraNavegacion;
    private Label  lblNavImei;
    private Label  lblNavModelo;
    private Label  lblNavCount;

    private final java.util.concurrent.ScheduledExecutorService poller =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "poller-reparaciones-tecnico");
                t.setDaemon(true);
                return t;
            });

    @FXML
    public void initialize() {
        tablaReparaciones.setColumnResizePolicy(param -> true);
        tablaReparaciones.setFixedCellSize(44);

        configurarColumnas();
        tablaReparaciones.getColumns().forEach(c -> c.setReorderable(false));
        configurarFilas();
        configurarFiltros();

        crearBarraNavegacion();
        tablaReparaciones.setItems(tablaItems);
        colIdRep.setVisible(false); colReparador.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false);
        colIdAnterior.setVisible(false);
        colComponente.setText("Reparaciones");
        adaptarFiltrosMaestro();
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });

        misPendientesController.setOnCerrar(this::cargarDatos);

        // Toggle historial: Reparaciones ↔ Pulidos
        javafx.scene.control.ToggleGroup tgHist = new javafx.scene.control.ToggleGroup();
        toggleHistRep.setToggleGroup(tgHist);
        toggleHistPul.setToggleGroup(tgHist);
        tgHist.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { toggleHistRep.setSelected(true); return; }
            boolean rep = (n == toggleHistRep);
            pnlHistRep.setVisible(rep);  pnlHistRep.setManaged(rep);
            pnlHistPul.setVisible(!rep); pnlHistPul.setManaged(!rep);
            if (!rep) {
                resetarModo();
                historialPulidoController.setFiltroImei(filtroImei.getText());
                historialPulidoController.cargar();
            } else {
                filtroImei.setText(historialPulidoController.getFiltroImei());
                cargarDatos();
            }
        });

        // Toggle pendientes: Reparaciones ↔ Pulidos
        javafx.scene.control.ToggleGroup tgPend = new javafx.scene.control.ToggleGroup();
        togglePendRep.setToggleGroup(tgPend);
        togglePendPul.setToggleGroup(tgPend);
        tgPend.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { togglePendRep.setSelected(true); return; }
            boolean rep = (n == togglePendRep);
            pnlPendRep.setVisible(rep);  pnlPendRep.setManaged(rep);
            pnlPendPul.setVisible(!rep); pnlPendPul.setManaged(!rep);
            if (!rep) { pulidoTecnicoController.setFiltroImei(misPendientesController.getFiltroImei()); pulidoTecnicoController.cargar(); }
            else      { misPendientesController.setFiltroImei(pulidoTecnicoController.getFiltroImei()); misPendientesController.cargar(); }
        });

        misPendientesController.cargar();

        poller.scheduleAtFixedRate(
                () -> javafx.application.Platform.runLater(this::recargar),
                60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Detiene el poller periódico al salir de la vista. */
    @Override
    public void detenerPolling() { poller.shutdownNow(); }

    // ─── Drill-down ───────────────────────────────────────────────────────────

    private void crearBarraNavegacion() {
        Button btnVolver = new Button("← Volver");
        btnVolver.getStyleClass().add("btn-secondary");
        btnVolver.setOnAction(e -> volverAGrupos());

        lblNavImei   = new Label();
        lblNavModelo = new Label();
        lblNavCount  = new Label();
        lblNavImei  .setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        lblNavModelo.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376;");
        lblNavCount .setStyle("-fx-font-size: 12px; -fx-text-fill: #586376;");

        barraNavegacion = new HBox(12, btnVolver,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                lblNavImei, lblNavModelo, lblNavCount);
        barraNavegacion.setAlignment(Pos.CENTER_LEFT);
        barraNavegacion.setPadding(new Insets(6, 0, 6, 0));
        barraNavegacion.setVisible(false);
        barraNavegacion.setManaged(false);

        pnlHistRep.getChildren().add(1, barraNavegacion);
    }

    private void buildTablaItems() {
        LinkedHashMap<String, List<ReparacionResumen>> porImei = new LinkedHashMap<>();
        for (ReparacionResumen rep : datosFiltrados)
            porImei.computeIfAbsent(rep.getImei(), k -> new ArrayList<>()).add(rep);

        boolean filtrarInc    = cbIncidenciasAbiertas != null && cbIncidenciasAbiertas.isSelected();
        boolean filtrarNormal = cbNormales != null && cbNormales.isSelected();

        tablaItems.clear();
        for (Map.Entry<String, List<ReparacionResumen>> e : porImei.entrySet()) {
            GrupoImei grupo = new GrupoImei(e.getKey(), e.getValue());
            if (filtrarInc || filtrarNormal) {
                boolean tieneInc = grupo.getCountIncAbiertas() > 0;
                boolean ok = (filtrarInc && tieneInc) || (filtrarNormal && !tieneInc);
                if (!ok) continue;
            }
            tablaItems.add(grupo);
        }
    }

    private void mostrarDetalle(GrupoImei grupo) {
        mostrarDetalleParaImei(grupo.getImei());
    }

    private void mostrarDetalleParaImei(String imei) {
        modoActual  = Modo.DETALLE;
        imeiDetalle = imei;

        String modelo = datos.stream().filter(r -> r.getImei().equals(imei))
                .map(ReparacionResumen::getModelo)
                .filter(m -> m != null && !m.isEmpty()).findFirst().orElse("");
        lblNavImei.setText("IMEI: " + imei);
        lblNavModelo.setText(!modelo.isEmpty()
                ? "  •  " + FormularioReparacionController.traducirModelo(modelo) : "");

        filtroImei     .setVisible(false); filtroImei     .setManaged(false);
        barraNavegacion.setVisible(true);  barraNavegacion.setManaged(true);
        colIdRep.setVisible(true); colReparador.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true);
        colIdAnterior.setVisible(true);
        colComponente.setText("Componente");
        adaptarFiltrosDetalle();
        javafx.application.Platform.runLater(this::aplicarAnchosDetalle);
        aplicarFiltros();
    }

    private void aplicarAnchosDetalle() {
        double w = tablaReparaciones.getWidth();
        if (w <= 0) return;
        double u = w / 1370.0;
        colIdRep        .setPrefWidth(Math.max(110, 110 * u));
        colImei         .setPrefWidth(Math.max(130, 130 * u));
        colModelo       .setPrefWidth(Math.max(100, 100 * u));
        colReparador    .setPrefWidth(Math.max(100, 100 * u));
        colFecha        .setPrefWidth(Math.max(110, 110 * u));
        colComponente   .setPrefWidth(Math.max(150, 150 * u));
        colObservaciones.setPrefWidth(Math.max(200, 200 * u));
        colEstado       .setPrefWidth(Math.max(120, 120 * u));
        colIncidencia   .setPrefWidth(Math.max(200, 200 * u));
        colIdAnterior   .setPrefWidth(Math.max(150, 150 * u));
    }

    private void volverAGrupos() {
        resetarModo();
        cargarDatos();
    }

    private void resetarModo() {
        modoActual  = Modo.MAESTRO;
        imeiDetalle = null;
        if (barraNavegacion != null) {
            barraNavegacion.setVisible(false); barraNavegacion.setManaged(false);
            filtroImei     .setVisible(true);  filtroImei     .setManaged(true);
        }
        colIdRep.setVisible(false); colReparador.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false);
        colIdAnterior.setVisible(false);
        colComponente.setText("Reparaciones");
        adaptarFiltrosMaestro();
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    /**
     * Recarga la sección visible: mis pendientes o historial.
     * Invocado por {@link MainController} cuando la ventana recupera el foco.
     */
    @Override
    public void recargar() {
        if (pnlMisPendientes.isVisible()) {
            if (togglePendPul.isSelected()) pulidoTecnicoController.cargar();
            else                            misPendientesController.cargar();
        } else {
            if (toggleHistPul.isSelected()) historialPulidoController.cargar();
            else                            cargarDatos();
        }
    }

    @FXML private void mostrarHistorial() {
        mostrarPanel(pnlHistorial, btnTabHistorial);
        cargarDatos();
    }

    public void irAInicio() { mostrarPanel(pnlMisPendientes, btnTabMisPendientes); }

    private void mostrarPanel(VBox panel, Button btnActivo) {
        if (pnlHistorial.isVisible() && panel != pnlHistorial && modoActual == Modo.DETALLE)
            resetarModo();
        pnlHistorial    .setVisible(false); pnlHistorial    .setManaged(false);
        pnlMisPendientes.setVisible(false); pnlMisPendientes.setManaged(false);
        panel.setVisible(true); panel.setManaged(true);
        for (Button b : new Button[]{btnTabHistorial, btnTabMisPendientes}) {
            b.getStyleClass().removeAll("stock-sidebar-btn-active", "stock-sidebar-btn");
            b.getStyleClass().add(b == btnActivo ? "stock-sidebar-btn-active" : "stock-sidebar-btn");
        }
    }

    // ─── Label expandible (click abre popup de lectura) ───────────────────────

    private Label labelExpandible(String titulo, String texto) {
        Label lbl = new Label(texto != null ? texto : "");
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        if (texto != null && !texto.isEmpty()) {
            lbl.setStyle("-fx-cursor: hand;");
            lbl.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto(titulo, texto); });
        }
        return lbl;
    }

    // ─── Columnas ─────────────────────────────────────────────────────────────

    private void configurarColumnas() {

        colIdRep.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                if (empty) { setText(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep) setText(rep.getIdRep());
                else setText(null);
            }
        });

        Image imgHistorial = new Image(getClass().getResourceAsStream("/images/Historial.png"));
        colImei.setCellFactory(col -> new TableCell<>() {
            // Para GrupoImei: IMEI en negrita + icono drill-down
            private final Label lblGrupo = new Label();
            private final ImageView ivDrill = new ImageView(imgHistorial);
            private final HBox hboxGrupo = new HBox(6, lblGrupo, ivDrill);
            // Para ReparacionResumen: solo IMEI
            private final Label lblDetalle = new Label();
            private final javafx.beans.value.ChangeListener<Boolean> selListenerDetalle =
                (obs, o, sel) -> lblDetalle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
                lblGrupo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
                ivDrill.setFitWidth(25); ivDrill.setFitHeight(25); ivDrill.setPreserveRatio(true);
                ivDrill.setStyle("-fx-cursor: hand;");
                hboxGrupo.setAlignment(Pos.CENTER_LEFT);
                lblDetalle.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3B54;");
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(selListenerDetalle);
                    if (newRow != null) newRow.selectedProperty().addListener(selListenerDetalle);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei g) {
                    lblGrupo.setText(g.getImei());
                    ivDrill.setOnMouseClicked(e -> {
                        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                            e.consume();
                            mostrarDetalle(g);
                        }
                    });
                    setGraphic(hboxGrupo);
                } else if (row instanceof ReparacionResumen rep) {
                    lblDetalle.setText(rep.getImei());
                    lblDetalle.setStyle("-fx-font-size: 12px; -fx-text-fill: " +
                        (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    setGraphic(lblDetalle);
                } else {
                    setGraphic(null);
                }
            }
        });

        colModelo.setCellValueFactory(d -> {
            Object o = d.getValue();
            String m = null;
            if (o instanceof GrupoImei g)         m = g.getModelo();
            else if (o instanceof ReparacionResumen rep) m = rep.getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });

        colReparador.setCellValueFactory(d -> {
            Object o = d.getValue();
            if (o instanceof ReparacionResumen rep)
                return new javafx.beans.property.SimpleStringProperty(
                    rep.getNombreTecnico() != null ? rep.getNombreTecnico() : "");
            return new javafx.beans.property.SimpleStringProperty("");
        });
        colReparador.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(empty || item == null || item.isEmpty() ? null : item);
            }
        });

        colFecha.setCellFactory(col -> new TableCell<>() {
            private final Label lblInicio = new Label();
            private final Label lblFin    = new Label();
            private final VBox  box       = new VBox(1, lblInicio, lblFin);
            {
                actualizarColores(false);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (newRow != null)
                        newRow.selectedProperty().addListener((o, wasSelected, isSelected) -> actualizarColores(isSelected));
                });
            }
            private void actualizarColores(boolean selected) {
                lblInicio.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (selected ? "white" : "#9AA0AA") + ";");
                lblFin.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (selected ? "white" : "#2C3B54") + ";");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                boolean selected = getTableRow() != null && getTableRow().isSelected();
                if (row instanceof GrupoImei g) {
                    lblInicio.setText(g.getFechaMasAntigua()  != null ? g.getFechaMasAntigua().format(FORMATO_FECHA)  : "—");
                    lblFin   .setText("→ " + (g.getFechaMasReciente() != null ? g.getFechaMasReciente().format(FORMATO_FECHA) : "—"));
                    actualizarColores(selected);
                    setGraphic(box);
                } else if (row instanceof ReparacionResumen rep) {
                    lblInicio.setText(rep.getFechaAsig() != null ? rep.getFechaAsig().format(FORMATO_FECHA) : "—");
                    lblFin   .setText("→ " + (rep.getFechaFin() != null ? rep.getFechaFin().format(FORMATO_FECHA) : "—"));
                    actualizarColores(selected);
                    setGraphic(box);
                } else {
                    setGraphic(null);
                }
            }
        });

        colComponente.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                if (empty) { setText(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei g) setText(g.getReparaciones().size() + " reparaciones");
                else if (row instanceof ReparacionResumen rep) setText(rep.getTipoComponente());
                else setText(null);
            }
        });

        colObservaciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep)
                    setGraphic(labelExpandible("Observaciones", rep.getObservaciones()));
                else
                    setGraphic(null);
            }
        });

        colIdAnterior.setCellFactory(col -> new TableCell<>() {
            private final Label lblLink = new Label();
            {
                lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand;");
                lblLink.setTextOverrun(OverrunStyle.ELLIPSIS);
                lblLink.setMaxWidth(Double.MAX_VALUE);
                lblLink.setOnMouseEntered(
                        e -> lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: true;"));
                lblLink.setOnMouseExited(
                        e -> lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: false;"));
                lblLink.setOnMouseClicked(e -> {
                    Object row = getTableView().getItems().get(getIndex());
                    if (!(row instanceof ReparacionResumen rep)) return;
                    String idAnterior = rep.getIdRepAnterior();
                    if (idAnterior == null) return;
                    for (int i = 0; i < getTableView().getItems().size(); i++) {
                        Object o = getTableView().getItems().get(i);
                        if (o instanceof ReparacionResumen r && idAnterior.equals(r.getIdRep())) {
                            getTableView().getSelectionModel().select(i);
                            getTableView().scrollTo(i);
                            getTableView().requestFocus();
                            break;
                        }
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep && rep.getIdRepAnterior() != null) {
                    lblLink.setText(rep.getIdRepAnterior());
                    setGraphic(lblLink);
                } else {
                    setGraphic(null);
                }
            }
        });

        configurarColEstado();
        configurarColIncidencia();
    }

    private void configurarColEstado() {
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                              "-fx-font-size: 11px; -fx-font-weight: bold;";
                if (row instanceof GrupoImei g) {
                    if (g.getCountIncAbiertas() > 0) {
                        badge.setText("Incidencia");
                        badge.setStyle(base +
                            "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + ";" +
                            "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                    } else {
                        badge.setText("Normal");
                        badge.setStyle(base + "-fx-background-color: #E8EAF0; -fx-text-fill: #586376;");
                    }
                    setGraphic(badge);
                } else if (row instanceof ReparacionResumen rep) {
                    if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                        badge.setText("Incidencia");
                        badge.setStyle(base +
                            "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + ";" +
                            "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                    } else if (rep.isEsIncidencia()) {
                        badge.setText("Resuelta");
                        badge.setStyle(base +
                            "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + ";" +
                            "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";");
                    } else {
                        badge.setText("Normal");
                        badge.setStyle(base +
                            "-fx-background-color: #E8EAF0;" +
                            "-fx-text-fill: #586376;");
                    }
                    setGraphic(badge);
                } else {
                    setGraphic(null);
                }
            }
        });
    }

    private void configurarColIncidencia() {
        colIncidencia.setCellFactory(col -> new TableCell<>() {
            private final Label lblComentario = new Label();
            private final Label lblSin = new Label("Sin incidencia");
            {
                lblComentario.setMaxWidth(Double.MAX_VALUE);
                lblComentario.setTextOverrun(OverrunStyle.ELLIPSIS);
                lblSin.setStyle("-fx-font-size: 12px; -fx-text-fill: #A0A0A0; -fx-font-style: italic;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setStyle(""); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei) {
                    setGraphic(null); setStyle(""); return;
                }
                if (!(row instanceof ReparacionResumen rep)) { setGraphic(null); setStyle(""); return; }
                if (!rep.isEsIncidencia()) {
                    setGraphic(lblSin);
                    setStyle("");
                } else if (!rep.isEsResuelto()) {
                    String texto = rep.getIncidencia() != null ? rep.getIncidencia() : "";
                    lblComentario.setText(texto);
                    lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #000000;" +
                            (!texto.isEmpty() ? " -fx-cursor: hand;" : ""));
                    lblComentario.setOnMouseClicked(texto.isEmpty() ? null :
                            e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto("Incidencia", texto); });
                    setStyle("");
                    setGraphic(lblComentario);
                } else {
                    String texto = rep.getIncidencia() != null ? rep.getIncidencia() : "";
                    lblComentario.setText(texto);
                    lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #A9A9A9;" +
                            (!texto.isEmpty() ? " -fx-cursor: hand;" : ""));
                    lblComentario.setOnMouseClicked(texto.isEmpty() ? null :
                            e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto("Incidencia", texto); });
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + ";");
                    setGraphic(lblComentario);
                }
            }
        });
    }

    private void configurarFilas() {
        tablaReparaciones.setRowFactory(tv -> new TableRow<>() {
            {
                ContextMenu menu = new ContextMenu();
                TableColumn<?, ?>[] colRightClick = {null};
                MenuItem copiar = new MenuItem("📋  Copiar celda");
                copiar.setOnAction(e -> {
                    if (getItem() == null || colRightClick[0] == null) return;
                    String texto = textoDeCelda(getItem(), colRightClick[0]);
                    if (texto == null || texto.isEmpty()) return;
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(texto);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    getChildrenUnmodifiable().stream()
                        .filter(n -> n instanceof TableCell && ((TableCell<?, ?>) n).getTableColumn() == colRightClick[0])
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
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });
                setOnMouseClicked(e -> {
                    if (!isEmpty() && getItem() instanceof GrupoImei grupo
                            && e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && e.getClickCount() == 1) {
                        mostrarDetalle(grupo);
                    }
                });
                selectedProperty().addListener((obs, wasSelected, isSelected) -> aplicarEstilo(getItem(), isEmpty()));
            }

            private void aplicarEstilo(Object item, boolean empty) {
                if (empty || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (item instanceof GrupoImei g) {
                    String brd = g.getCountIncAbiertas() > 0 ? com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD : "#2C3B54";
                    setStyle("-fx-background-color: #EEF0F5;" +
                             "-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                             "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + brd + ";" +
                             "-fx-cursor: hand;");
                    return;
                }
                if (!(item instanceof ReparacionResumen rep)) return;
                if (isSelected()) {
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                            "-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;");
                } else if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                } else if (rep.isEsIncidencia()) {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_REPARADO_BRD + ";");
                } else {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " transparent;");
                }
            }

            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
            }
        });
    }

    private void adaptarFiltrosMaestro() {
        cbIncidenciasAbiertas.setText("Incidencia");
        cbIncidenciasCerradas.setSelected(false);
        if (itemCerradas != null) itemCerradas.setVisible(false);
        cbNormales.setText("Normal");
        actualizarTextoFiltroIncidencias();
    }

    private void adaptarFiltrosDetalle() {
        cbIncidenciasAbiertas.setText("Abiertas");
        if (itemCerradas != null) itemCerradas.setVisible(true);
        cbNormales.setText("Sin incidencia");
        actualizarTextoFiltroIncidencias();
    }

    private void actualizarTextoFiltroIncidencias() {
        boolean a = cbIncidenciasAbiertas.isSelected();
        boolean c = cbIncidenciasCerradas.isSelected();
        boolean n = cbNormales.isSelected();
        long total = java.util.stream.Stream.of(a, c, n).filter(Boolean::booleanValue).count();
        if      (total == 0) filtroIncidencias.setText("Incidencias");
        else if (total == 3) filtroIncidencias.setText("Todas");
        else if (total == 1) filtroIncidencias.setText(a ? cbIncidenciasAbiertas.getText() : c ? cbIncidenciasCerradas.getText() : cbNormales.getText());
        else                 filtroIncidencias.setText(total + " filtros");
    }

    private String textoDeCelda(Object item, TableColumn<?, ?> col) {
        if (item instanceof GrupoImei g) {
            if (col == colImei)       return g.getImei();
            if (col == colModelo)     { String m = g.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
            if (col == colFecha)      return (g.getFechaMasAntigua() != null ? g.getFechaMasAntigua().format(FORMATO_FECHA) : "—")
                                            + " → " + (g.getFechaMasReciente() != null ? g.getFechaMasReciente().format(FORMATO_FECHA) : "—");
            if (col == colComponente) return g.getReparaciones().size() + " reparaciones";
            if (col == colEstado)     return g.getCountIncAbiertas() > 0 ? "Incidencia" : "Normal";
            return null;
        }
        if (!(item instanceof ReparacionResumen rep)) return null;
        if (col == colIdRep)         return rep.getIdRep();
        if (col == colImei)          return rep.getImei();
        if (col == colModelo)        { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == colReparador)     return rep.getNombreTecnico();
        if (col == colFecha)         return rep.getFechaFin() != null ? rep.getFechaFin().format(FORMATO_FECHA) : "";
        if (col == colComponente)    return rep.getTipoComponente();
        if (col == colObservaciones) return rep.getObservaciones();
        if (col == colIncidencia)    return rep.getIncidencia();
        if (col == colIdAnterior)    return rep.getIdRepAnterior();
        return null;
    }

    private void cargarDatos() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (idTec == null) return;
            datos.setAll(reparacionDAO.getReparacionesPorTecnico(idTec));
            aplicarFiltros();
            String hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null)
                lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    private void configurarFiltros() {
        filtroImei.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) filtroImei.setText(n.replaceAll("[^\\d]", ""));
            if (filtroImei.getText().length() > 15)
                filtroImei.setText(filtroImei.getText().substring(0, 15));
            String val = filtroImei.getText();
            if (val.isEmpty())
                filtroImei.setStyle("");
            else if (val.length() < 15)
                filtroImei.setStyle("-fx-background-color: #F3F3F3; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else
                filtroImei.setStyle("-fx-background-color: #F3F3F3; -fx-border-color: #8AC7AF;" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            aplicarFiltros();
        });
        filtroFechaDesde.getEditor().setDisable(true);
        filtroFechaDesde.getEditor().setOpacity(1.0);
        filtroFechaHasta.getEditor().setDisable(true);
        filtroFechaHasta.getEditor().setOpacity(1.0);
        filtroFechaDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
        filtroFechaHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
        cbIncidenciasAbiertas = new CheckBox("Abiertas");
        cbIncidenciasAbiertas.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbIncidenciasAbiertas.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroIncidencias();
            aplicarFiltros();
        });
        cbIncidenciasCerradas = new CheckBox("Cerradas");
        cbIncidenciasCerradas.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbIncidenciasCerradas.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroIncidencias();
            aplicarFiltros();
        });
        cbNormales = new CheckBox("Sin incidencia");
        cbNormales.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbNormales.selectedProperty().addListener((obs, o, n) -> {
            actualizarTextoFiltroIncidencias();
            aplicarFiltros();
        });
        itemCerradas = new CustomMenuItem(cbIncidenciasCerradas, false);
        filtroIncidencias.getItems().addAll(
            new CustomMenuItem(cbIncidenciasAbiertas, false),
            itemCerradas,
            new CustomMenuItem(cbNormales, false));
    }

    private void aplicarFiltros() {
        if (cbIncidenciasAbiertas == null) return;
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        boolean filtrarAbiertas = cbIncidenciasAbiertas.isSelected();
        boolean filtrarCerradas = cbIncidenciasCerradas.isSelected();
        boolean filtrarNormales = cbNormales.isSelected();

        if (modoActual == Modo.DETALLE) {
            List<ReparacionResumen> filtradas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(rep -> {
                    if (desde != null || hasta != null) {
                        if (rep.getFechaFin() == null) return false;
                        LocalDate fechaFin = rep.getFechaFin().toLocalDate();
                        if (desde != null && fechaFin.isBefore(desde)) return false;
                        if (hasta != null && fechaFin.isAfter(hasta))  return false;
                    }
                    if (filtrarAbiertas || filtrarCerradas || filtrarNormales) {
                        boolean mostrar = false;
                        if (filtrarNormales && !rep.isEsIncidencia())                        mostrar = true;
                        if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto()) mostrar = true;
                        if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto()) mostrar = true;
                        if (!mostrar) return false;
                    }
                    return true;
                }).collect(Collectors.toList());
            tablaItems.setAll(filtradas);
            lblNavCount.setText("  •  " + filtradas.size() + " reparaciones");
            return;
        }

        String imeiStr = filtroImei.getText().trim();
        datosFiltrados = datos.stream().filter(rep -> {
            if (imeiStr.length() == 15 && !rep.getImei().equals(imeiStr))
                return false;
            if (desde != null || hasta != null) {
                if (rep.getFechaFin() == null) return false;
                LocalDate fechaFin = rep.getFechaFin().toLocalDate();
                if (desde != null && fechaFin.isBefore(desde)) return false;
                if (hasta != null && fechaFin.isAfter(hasta))  return false;
            }
            return true;
        }).collect(Collectors.toList());
        buildTablaItems();
    }

    /**
     * Aplica un filtro inicial de fecha.
     * <p>Llamado por {@link MainController} al navegar desde la vista de estadísticas
     * (clic en un vértice del gráfico). Navega al historial antes de aplicar el filtro.</p>
     *
     * @param desde fecha de inicio del filtro
     * @param hasta fecha de fin del filtro
     */
    public void setFiltroInicial(java.time.LocalDate desde, java.time.LocalDate hasta) {
        if (modoActual == Modo.DETALLE) volverAGrupos();
        mostrarHistorial();
        filtroFechaDesde.setValue(desde);
        filtroFechaHasta.setValue(hasta);
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroFechaDesde.setValue(null);
        filtroFechaHasta.setValue(null);
        cbIncidenciasAbiertas.setSelected(false);
        cbIncidenciasCerradas.setSelected(false);
        cbNormales.setSelected(false);
        filtroIncidencias.setText("Incidencias");
        filtroImei.setStyle("");
    }

    @FXML
    private void abrirModalPendientes() {
        mostrarPanel(pnlMisPendientes, btnTabMisPendientes);
        misPendientesController.cargar();
    }

    @FXML
    private void descargarHistorial() {
        exportarCSV((Stage) tablaReparaciones.getScene().getWindow());
    }

    @Override
    public void exportarCSV(Stage owner) {
        List<ReparacionResumen> items;
        String nombre;
        if (pnlMisPendientes.isVisible()) {
            items  = misPendientesController.getItemsVisibles();
            nombre = "mis_pendientes";
        } else {
            if (modoActual == Modo.MAESTRO) {
                items = new ArrayList<>(datosFiltrados);
            } else {
                items = tablaItems.stream()
                    .filter(o -> o instanceof ReparacionResumen)
                    .map(o -> (ReparacionResumen) o)
                    .collect(Collectors.toList());
            }
            nombre = "mis_reparaciones";
        }

        List<String> cabeceras = List.of(
                "ID Reparación", "IMEI", "Fecha asig.", "Fecha fin",
                "Componente", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (ReparacionResumen r : items) {
            filas.add(List.of(
                    r.getIdRep(),
                    com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()),
                    r.getFechaAsig() != null ? r.getFechaAsig().format(fmt) : "",
                    r.getFechaFin()  != null ? r.getFechaFin().format(fmt)  : "",
                    r.getTipoComponente() != null ? r.getTipoComponente() : "",
                    r.getObservaciones()  != null ? r.getObservaciones()  : "",
                    r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No",
                    r.isEsResuelto() ? "Sí" : "No",
                    r.getIdRepAnterior() != null ? r.getIdRepAnterior() : ""
            ));
        }
        com.reparaciones.utils.CsvExporter.exportar(owner, nombre, cabeceras, filas);
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
