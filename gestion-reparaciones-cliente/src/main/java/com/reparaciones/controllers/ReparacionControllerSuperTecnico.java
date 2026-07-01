package com.reparaciones.controllers;

import com.reparaciones.dao.ReparacionComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.models.GrupoImei;
import com.reparaciones.models.ReparacionResumen;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

/**
 * Controlador de la vista de reparaciones para el rol SUPERTECNICO.
 * <p>El historial opera en dos modos:</p>
 * <ul>
 *   <li><b>Maestro</b>: una fila por IMEI con datos de resumen (rango de fechas,
 *       número de reparaciones, incidencias abiertas). Clicar una fila entra en modo detalle.</li>
 *   <li><b>Detalle</b>: muestra todas las reparaciones del IMEI seleccionado con los
 *       mismos detalles de columna actuales. Una barra de navegación permite volver al maestro.</li>
 * </ul>
 *
 * @role SUPERTECNICO
 */
public class ReparacionControllerSuperTecnico implements com.reparaciones.utils.Recargable, com.reparaciones.utils.Exportable {

    @FXML private TableView<Object>         tablaReparaciones;
    @FXML private TableColumn<Object, String> colIdRep;
    @FXML private TableColumn<Object, String> colImei;
    @FXML private TableColumn<Object, String> colModelo;
    @FXML private TableColumn<Object, String> colReparador;
    @FXML private TableColumn<Object, String> colAsignadoPor;
    @FXML private TableColumn<Object, String> colFecha;
    @FXML private TableColumn<Object, String> colComponente;
    @FXML private TableColumn<Object, String> colObservaciones;
    @FXML private TableColumn<Object, Void>   colEstado;
    @FXML private TableColumn<Object, Void>   colIncidencia;
    @FXML private TableColumn<Object, String> colIdAnterior;
    @FXML private TableColumn<Object, String> colObservacionTelefono;
    @FXML private TableColumn<Object, String> colCliente;
    @FXML private TableColumn<Object, Void>   colRevision;
    @FXML private TextField  filtroImei;
    @FXML private javafx.scene.control.ToggleButton toggleAgrupar;
    @FXML private javafx.scene.control.ToggleButton toggleDesagrupar;
    @FXML private javafx.scene.control.Label        lblContadorPlano;
    @FXML private Label      lblUltimaActualizacion;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroCliente;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    // ── Sidebar + paneles ─────────────────────────────────────────────────────
    @FXML private Button btnTabHistorial;
    @FXML private Button btnTabPendientes;
    @FXML private Button btnTabMisPendientes;
    @FXML private Label  lblBadgeAsignaciones;
    @FXML private Label  lblBadgePendientes;
    @FXML private VBox   pnlHistorial;
    @FXML private VBox   pnlPendientes;
    @FXML private VBox   pnlMisPendientes;
    @FXML private PendientesSuperTecnicoController pendientesSuperTecnicoController;
    @FXML private PendientesTecnicoController      misPendientesController;

    // ── Toggles y sub-paneles de pulido ───────────────────────────────────────
    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistPul;
    @FXML private VBox pnlHistRep;
    @FXML private VBox pnlHistPul;
    @FXML private HistorialPulidoController historialPulidoController;

    @FXML private javafx.scene.control.ToggleButton toggleMisPendRep;
    @FXML private javafx.scene.control.ToggleButton toggleMisPendGlass;
    @FXML private javafx.scene.control.ToggleButton toggleMisPendPul;
    @FXML private VBox pnlMisPendRep;
    @FXML private VBox pnlMisPendGlass;
    @FXML private VBox pnlMisPendPul;
    @FXML private PendientesTecnicoController misPendientesGlassController;
    @FXML private PulidoTecnicoController misPulidosTecnicoController;

    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;
    private CustomMenuItem itemCerradas;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final ReparacionComponenteDAO reparacionComponenteDAO = new ReparacionComponenteDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();
    private final com.reparaciones.dao.TelefonoDAO telefonoDAO = new com.reparaciones.dao.TelefonoDAO();
    private final com.reparaciones.dao.ClienteDAO clienteDAO = new com.reparaciones.dao.ClienteDAO();

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private List<ReparacionResumen> datosFiltrados = new ArrayList<>();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();

    // ── Drill-down ────────────────────────────────────────────────────────────
    private enum Modo { MAESTRO, DETALLE, PLANO }
    private Modo   modoActual  = Modo.MAESTRO;
    private String imeiDetalle = null;
    private HBox   barraNavegacion;
    private Label  lblNavImei;
    private Label  lblNavModelo;
    private Label  lblNavCount;

    private final Set<Integer>   idsTecFiltro  = new HashSet<>();
    private final Set<String>    idsAjenas     = new HashSet<>();
    private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
    private final List<Tecnico>  tecnicosLista  = new ArrayList<>();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final java.util.Set<String> clientesFiltro = new java.util.HashSet<>();
    private static final String SIN_CLIENTE = "(Sin cliente)";
    private final javafx.beans.property.StringProperty etiquetaCli = new javafx.beans.property.SimpleStringProperty("Cliente");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroCliHandle;

    @FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroPieza;
    private final java.util.Set<String> piezasFiltro = new java.util.HashSet<>();
    private final javafx.beans.property.StringProperty etiquetaPieza = new javafx.beans.property.SimpleStringProperty("Pieza");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroPiezaHandle;

    private final java.util.concurrent.ScheduledExecutorService poller =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "poller-reparaciones-supertecnico");
                t.setDaemon(true);
                return t;
            });

    @FXML
    public void initialize() {
        tablaReparaciones.setColumnResizePolicy(param -> true);
        tablaReparaciones.setFixedCellSize(44);
        tablaReparaciones.getColumns().forEach(c -> c.setSortable(false));   // el orden lo llevan los filtros, no el clic en la cabecera

        configurarColumnas();
        tablaReparaciones.getColumns().forEach(c -> c.setReorderable(false));
        configurarFilas();
        configurarFiltros();

        lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
        javafx.scene.control.ToggleGroup tgAgrupar = new javafx.scene.control.ToggleGroup();
        toggleAgrupar.setToggleGroup(tgAgrupar);
        toggleDesagrupar.setToggleGroup(tgAgrupar);
        tgAgrupar.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { toggleAgrupar.setSelected(true); return; }  // no permitir deselección
            if (n == toggleDesagrupar) {                                  // pasar a plano
                entrarModoPlano();
            } else {                                                      // volver a agrupado
                lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
                resetarModo();
                aplicarFiltros();
            }
        });

        pendientesSuperTecnicoController.setOnActualizar(() -> {
            cargarDatos();
            pendientesSuperTecnicoController.cargar();
            misPendientesController.cargar();
            misPendientesGlassController.cargar();
            actualizarBadges();
        });
        misPendientesController.setOnCerrar(() -> {
            cargarDatos();
            misPendientesController.cargar();
            actualizarBadges();
        });
        misPendientesGlassController.setModoGlass();
        misPendientesGlassController.cargar();   // recarga como AG (el initialize del include cargó A por defecto)
        misPendientesGlassController.setOnCerrar(() -> {
            cargarDatos();
            misPendientesGlassController.cargar();
            actualizarBadges();
        });

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
                if (modoActual == Modo.DETALLE) resetarModo();
                historialPulidoController.setFiltroImei(filtroImei.getText());
                historialPulidoController.cargar();
            } else {
                filtroImei.setText(historialPulidoController.getFiltroImei());
                cargarDatos();
            }
        });

        // Asignaciones: tabla unificada (reparación + glass + pulido). Sin toggle.

        // Toggle mis pendientes: Reparaciones | Glass | Pulidos
        javafx.scene.control.ToggleGroup tgMisPend = new javafx.scene.control.ToggleGroup();
        toggleMisPendRep.setToggleGroup(tgMisPend);
        toggleMisPendGlass.setToggleGroup(tgMisPend);
        toggleMisPendPul.setToggleGroup(tgMisPend);
        tgMisPend.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o == null ? toggleMisPendRep : (javafx.scene.control.ToggleButton) o).setSelected(true); return; }
            String filtro = o == toggleMisPendGlass ? misPendientesGlassController.getFiltroImei()
                          : o == toggleMisPendPul   ? misPulidosTecnicoController.getFiltroImei()
                          :                           misPendientesController.getFiltroImei();
            pnlMisPendRep.setVisible(n == toggleMisPendRep);     pnlMisPendRep.setManaged(n == toggleMisPendRep);
            pnlMisPendGlass.setVisible(n == toggleMisPendGlass); pnlMisPendGlass.setManaged(n == toggleMisPendGlass);
            pnlMisPendPul.setVisible(n == toggleMisPendPul);     pnlMisPendPul.setManaged(n == toggleMisPendPul);
            if (n == toggleMisPendRep)        { misPendientesController.setFiltroImei(filtro);      misPendientesController.cargar(); }
            else if (n == toggleMisPendGlass) { misPendientesGlassController.setFiltroImei(filtro); misPendientesGlassController.cargar(); }
            else                              { misPulidosTecnicoController.setFiltroImei(filtro);  misPulidosTecnicoController.cargar(); }
        });

        crearBarraNavegacion();
        tablaReparaciones.setItems(tablaItems);
        colIdRep.setVisible(false); colReparador.setVisible(false); colAsignadoPor.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false);
        colIdAnterior.setVisible(false); colObservacionTelefono.setVisible(true); colCliente.setVisible(true);
        colRevision.setVisible(true);
        colComponente.setText("Reparaciones");
        adaptarFiltrosMaestro();
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });

        mostrarPanel(pnlPendientes, btnTabPendientes);

        com.reparaciones.utils.Poller.programarSiguiente(poller, this::recargar);
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> recargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
        actualizarBadges();
        updateBadgeStyle(lblBadgeAsignaciones, false);
        updateBadgeStyle(lblBadgePendientes,   false);
    }

    @Override
    public void detenerPolling() { poller.shutdownNow(); }

    @Override
    public void recargar() {
        if (pnlPendientes.isVisible()) {
            pendientesSuperTecnicoController.cargar();
        } else if (pnlMisPendientes.isVisible()) {
            if (toggleMisPendPul.isSelected())        misPulidosTecnicoController.cargar();
            else if (toggleMisPendGlass.isSelected()) misPendientesGlassController.cargar();
            else                                      misPendientesController.cargar();
        } else {
            if (toggleHistPul.isSelected()) historialPulidoController.cargar();
            else                            cargarDatos();
        }
        // Badge data siempre fresco (rep + glass), independiente del panel visible
        if (!pnlPendientes.isVisible())                                        pendientesSuperTecnicoController.cargar();
        if (!pnlMisPendientes.isVisible() || !toggleMisPendRep.isSelected())   misPendientesController.cargar();
        if (!pnlMisPendientes.isVisible() || !toggleMisPendGlass.isSelected()) misPendientesGlassController.cargar();
        actualizarBadges();
    }

    private void actualizarBadges() {
        setBadge(lblBadgeAsignaciones, pendientesSuperTecnicoController.getTotalItems());
        setBadge(lblBadgePendientes,
                misPendientesController.getTotalItems() + misPendientesGlassController.getTotalItems());
    }

    private void setBadge(Label lbl, int count) {
        javafx.scene.layout.StackPane pane = (javafx.scene.layout.StackPane) lbl.getParent();
        if (count <= 0) { pane.setVisible(false); pane.setManaged(false); return; }
        lbl.setText(count > 9 ? "9+" : String.valueOf(count));
        pane.setVisible(true); pane.setManaged(true);
    }

    private void updateBadgeStyle(Label lbl, boolean active) {
        javafx.scene.layout.StackPane outer = (javafx.scene.layout.StackPane) lbl.getParent().getParent();
        outer.getStyleClass().removeAll("sidebar-item-active", "sidebar-item-inactive");
        outer.getStyleClass().add(active ? "sidebar-item-active" : "sidebar-item-inactive");
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    @FXML private void mostrarHistorial() {
        if (modoActual == Modo.DETALLE) { volverAGrupos(); return; }
        mostrarPanel(pnlHistorial, btnTabHistorial);
    }

    public void irAInicio() {
        mostrarPanel(pnlPendientes, btnTabPendientes);
    }

    private void mostrarPanel(VBox panel, Button btnActivo) {
        // Al salir del historial en modo detalle, resetear a maestro
        if (pnlHistorial.isVisible() && panel != pnlHistorial && modoActual == Modo.DETALLE)
            resetarModo();

        if (pnlPendientes.isVisible() && panel != pnlPendientes)
            pendientesSuperTecnicoController.resetearCambios();
        pnlHistorial    .setVisible(false); pnlHistorial    .setManaged(false);
        pnlPendientes   .setVisible(false); pnlPendientes   .setManaged(false);
        pnlMisPendientes.setVisible(false); pnlMisPendientes.setManaged(false);
        panel.setVisible(true); panel.setManaged(true);
        for (Button b : new Button[]{btnTabHistorial, btnTabPendientes, btnTabMisPendientes}) {
            b.getStyleClass().removeAll("stock-sidebar-btn-active", "stock-sidebar-btn");
            b.getStyleClass().add(b == btnActivo ? "stock-sidebar-btn-active" : "stock-sidebar-btn");
        }
        updateBadgeStyle(lblBadgeAsignaciones, btnActivo == btnTabPendientes);
        updateBadgeStyle(lblBadgePendientes,   btnActivo == btnTabMisPendientes);
        if (panel == pnlHistorial) {
            if (toggleHistPul.isSelected()) historialPulidoController.cargar();
            else                            cargarDatos();
        } else if (panel == pnlPendientes) {
            pendientesSuperTecnicoController.cargar();
        } else {
            if (toggleMisPendPul.isSelected())        misPulidosTecnicoController.cargar();
            else if (toggleMisPendGlass.isSelected()) misPendientesGlassController.cargar();
            else                                      misPendientesController.cargar();
        }
    }

    // ─── Label expandible (click abre popup de lectura) ───────────────────────

    private Label labelExpandible(String titulo, String texto) {
        Label lbl = new Label(texto != null ? texto : "");
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setText(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                setText(row instanceof ReparacionResumen rep ? rep.getIdRep() : null);
            }
        });

        Image imgHistorial = new Image(getClass().getResourceAsStream("/images/Historial.png"));
        colImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final ImageView ivHist = new ImageView(imgHistorial);
            private final HBox contenedor = new HBox(6, lbl, ivHist);
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> lbl.setStyle(lbl.getUserData() + "-fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
                ivHist.setFitWidth(25);
                ivHist.setFitHeight(25);
                ivHist.setPreserveRatio(true);
                ivHist.setStyle("-fx-cursor: hand;");
                contenedor.setAlignment(Pos.CENTER_LEFT);
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
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei grupo) {
                    String baseStyle = "-fx-font-size: 12px; -fx-font-weight: bold; ";
                    lbl.setUserData(baseStyle);
                    lbl.setText(grupo.getImei());
                    lbl.setStyle(baseStyle + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    ivHist.setVisible(true); ivHist.setManaged(true);
                    ivHist.setOnMouseClicked(e -> { e.consume(); mostrarDetalle(grupo); });
                    setGraphic(contenedor);
                } else if (row instanceof ReparacionResumen rep) {
                    String baseStyle = "-fx-font-size: 12px; ";
                    lbl.setUserData(baseStyle);
                    lbl.setText(rep.getImei());
                    lbl.setStyle(baseStyle + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    ivHist.setVisible(false); ivHist.setManaged(false);
                    setGraphic(contenedor);
                }
            }
        });

        colModelo.setCellValueFactory(d -> {
            Object row = d.getValue();
            String m = null;
            if (row instanceof GrupoImei grupo) m = grupo.getModelo();
            else if (row instanceof ReparacionResumen rep) m = rep.getModelo();
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
        colAsignadoPor.setCellValueFactory(d -> {
            Object o = d.getValue();
            if (o instanceof ReparacionResumen rep)
                return new javafx.beans.property.SimpleStringProperty(
                    rep.getNombreTecnicoAsigna() != null ? rep.getNombreTecnicoAsigna() : "—");
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
            private final javafx.scene.control.Label lblInicio = new javafx.scene.control.Label();
            private final javafx.scene.control.Label lblFin    = new javafx.scene.control.Label();
            private final javafx.scene.layout.VBox   box       = new javafx.scene.layout.VBox(1, lblInicio, lblFin);
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
                super.updateItem(item, empty); setText(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei grupo) {
                    lblInicio.setText(grupo.getFechaMasAntigua()  != null ? FechaUtils.formatear(grupo.getFechaMasAntigua(), FORMATO_FECHA)  : "—");
                    lblFin.setText("→ " + (grupo.getFechaMasReciente() != null ? FechaUtils.formatear(grupo.getFechaMasReciente(), FORMATO_FECHA) : "—"));
                } else if (row instanceof ReparacionResumen rep) {
                    lblInicio.setText(rep.getFechaAsig() != null ? FechaUtils.formatear(rep.getFechaAsig(), FORMATO_FECHA) : "—");
                    lblFin.setText("→ " + (rep.getFechaFin() != null ? FechaUtils.formatear(rep.getFechaFin(), FORMATO_FECHA) : "—"));
                } else { setGraphic(null); return; }
                actualizarColores(getTableRow() != null && getTableRow().isSelected());
                setGraphic(box);
            }
        });

        colComponente.setCellFactory(col -> new TableCell<>() {
            private final Label lblTipo = new Label();
            private final Label lblReut = new Label("Reutilizado");
            private final VBox  box     = new VBox(1, lblTipo, lblReut);
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> actualizarColores(sel);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(selListener);
                    if (newRow != null) { newRow.selectedProperty().addListener(selListener); actualizarColores(newRow.isSelected()); }
                });
            }
            private void actualizarColores(boolean sel) {
                lblTipo.setStyle("-fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
                lblReut.setStyle("-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: " + (sel ? "white" : "#9AA0AA") + ";");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei grupo) {
                    setText(grupo.getReparaciones().size() + " reparaciones");
                } else if (row instanceof ReparacionResumen rep) {
                    lblTipo.setText(rep.getTipoComponente() != null ? rep.getTipoComponente() : "");
                    lblReut.setVisible(rep.isEsReutilizado()); lblReut.setManaged(rep.isEsReutilizado());
                    actualizarColores(getTableRow() != null && getTableRow().isSelected());
                    setGraphic(box);
                }
            }
        });

        colObservaciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep)
                    setGraphic(labelExpandible("Observaciones", rep.getObservaciones()));
                else
                    setGraphic(null);
            }
        });

        colObservacionTelefono.setCellValueFactory(d -> {
            Object row = d.getValue();
            String obs = null;
            if (row instanceof com.reparaciones.models.GrupoImei grupo) obs = grupo.getObservacion();
            else if (row instanceof ReparacionResumen rep) obs = rep.getObservacionTelefono();
            return new javafx.beans.property.SimpleStringProperty(obs != null ? obs : "");
        });
        colObservacionTelefono.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String obs = null;
                if (row instanceof com.reparaciones.models.GrupoImei grupo) obs = grupo.getObservacion();
                else if (row instanceof ReparacionResumen rep) obs = rep.getObservacionTelefono();
                setGraphic(labelExpandible("Observación", obs));
            }
        });

        colCliente.setCellValueFactory(d -> {
            Object row = d.getValue();
            String cli = null;
            if (row instanceof com.reparaciones.models.GrupoImei grupo) cli = grupo.getCliente();
            else if (row instanceof ReparacionResumen rep) cli = rep.getCliente();
            return new javafx.beans.property.SimpleStringProperty(cli != null ? cli : "");
        });
        colCliente.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String cli = null;
                if (row instanceof com.reparaciones.models.GrupoImei grupo) cli = grupo.getCliente();
                else if (row instanceof ReparacionResumen rep) cli = rep.getCliente();
                setGraphic(labelExpandible("Cliente", cli));
            }
        });

        colIdAnterior.setCellFactory(col -> new TableCell<>() {
            private final Label lblLink = new Label();
            {
                lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand;");
                lblLink.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
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
                        Object candidate = getTableView().getItems().get(i);
                        if (candidate instanceof ReparacionResumen r && idAnterior.equals(r.getIdRep())) {
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
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
        configurarColRevision();
    }

    private void configurarColEstado() {
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                    "-fx-font-size: 11px; -fx-font-weight: bold;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                              "-fx-font-size: 11px; -fx-font-weight: bold;";
                if (row instanceof GrupoImei grupo) {
                    if (grupo.getCountIncAbiertas() > 0) {
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
                        badge.setStyle(base + "-fx-background-color: #E8EAF0; -fx-text-fill: #586376;");
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
                lblComentario.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                lblSin.setStyle("-fx-font-size: 12px; -fx-text-fill: #A0A0A0; -fx-font-style: italic;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); setStyle(""); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep) {
                    if (rep.isEsIncidencia()) {
                        String texto = rep.getIncidencia() != null ? rep.getIncidencia() : "";
                        lblComentario.setText(texto);
                        String color = rep.isEsResuelto() ? "#A9A9A9" : "#000000";
                        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";" +
                                (!texto.isEmpty() ? " -fx-cursor: hand;" : ""));
                        lblComentario.setOnMouseClicked(texto.isEmpty() ? null :
                                e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto("Incidencia", texto); });
                        setStyle(rep.isEsResuelto()
                                ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + ";"
                                : "");
                        setGraphic(lblComentario);
                    } else {
                        setStyle("");
                        setGraphic(lblSin);
                    }
                } else {
                    setStyle(""); setGraphic(null);
                }
            }
        });
    }

    private void configurarColRevision() {
        colRevision.setCellFactory(col -> new TableCell<>() {
            private final ToggleButton toggle = new ToggleButton();
            {
                toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
                setAlignment(javafx.geometry.Pos.CENTER);

                toggle.setOnAction(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    Object row = getTableView().getItems().get(getIndex());
                    if (!(row instanceof GrupoImei grupo)) return;
                    if (grupo.isTieneAsignaciones()) { toggle.setSelected(false); return; }

                    boolean nuevoValor = toggle.isSelected();
                    boolean estadoAnterior = !nuevoValor;

                    new Thread(() -> {
                        try {
                            telefonoDAO.actualizarRevisionLogistica(grupo.getImei(), nuevoValor, grupo.getTelefonoUpdatedAt());
                            javafx.application.Platform.runLater(this::actualizarVista);
                        } catch (com.reparaciones.utils.StaleDataException ex) {
                            javafx.application.Platform.runLater(() -> {
                                toggle.setSelected(estadoAnterior);
                                aplicarEstiloToggle(estadoAnterior);
                                new javafx.scene.control.Alert(
                                        javafx.scene.control.Alert.AlertType.WARNING,
                                        "El teléfono fue modificado por otro usuario. Se recargan los datos.")
                                        .showAndWait();
                                actualizarVista();
                            });
                        } catch (java.sql.SQLException ex) {
                            javafx.application.Platform.runLater(() -> {
                                toggle.setSelected(estadoAnterior);
                                aplicarEstiloToggle(estadoAnterior);
                                new javafx.scene.control.Alert(
                                        javafx.scene.control.Alert.AlertType.ERROR,
                                        "Error al guardar: " + ex.getMessage())
                                        .showAndWait();
                            });
                        }
                    }).start();
                });
            }

            private void aplicarEstiloToggle(boolean on) {
                if (on) {
                    toggle.setText("OK");
                    toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                                    "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; " +
                                    "-fx-background-color: #2E7D32; -fx-text-fill: white;");
                } else {
                    toggle.setText("—");
                    toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; " +
                                    "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; " +
                                    "-fx-background-color: #9E9E9E; -fx-text-fill: white;");
                }
            }

            private void actualizarVista() {
                cargarDatos();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei grupo) {
                    boolean efectivo = grupo.isRevisionLogistica() && !grupo.isTieneAsignaciones();
                    toggle.setSelected(efectivo);
                    aplicarEstiloToggle(efectivo);
                    if (grupo.isTieneAsignaciones()) {
                        toggle.setStyle(toggle.getStyle().replace("-fx-cursor: hand;", "-fx-cursor: default;") +
                                        " -fx-opacity: 0.5;");
                    }
                    setGraphic(toggle);
                } else {
                    setGraphic(null);
                }
            }
        });
    }

    private void configurarFilas() {
        tablaReparaciones.setRowFactory(tv -> new TableRow<>() {
            {
                ContextMenu menu = new ContextMenu();
                TableColumn<?, ?>[] colRightClick = {null};
                MenuItem copiar      = new MenuItem("📋  Copiar celda");
                MenuItem editar      = new MenuItem("Editar");
                MenuItem borrar      = new MenuItem("Borrar");
                MenuItem aniadirInc  = new MenuItem("Añadir incidencia");
                MenuItem cancelarInc = new MenuItem("Cancelar incidencia");
                MenuItem editarObs   = new MenuItem("Editar observación");
                MenuItem editarCli   = new MenuItem("Editar cliente");
                Image imgEditarCtx = new Image(getClass().getResourceAsStream("/images/editar.png"));
                ImageView ivEditarCli = new ImageView(imgEditarCtx);
                ivEditarCli.setFitWidth(14); ivEditarCli.setFitHeight(14); ivEditarCli.setPreserveRatio(true);
                editarCli.setGraphic(ivEditarCli);
                ImageView ivEditarObs = new ImageView(imgEditarCtx);
                ivEditarObs.setFitWidth(14); ivEditarObs.setFitHeight(14); ivEditarObs.setPreserveRatio(true);
                editarObs.setGraphic(ivEditarObs);

                copiar.setOnAction(e -> {
                    Object rowItem = getItem();
                    if (rowItem == null || colRightClick[0] == null) return;
                    String texto = textoDeCelda(rowItem, colRightClick[0]);
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
                editar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) FormularioReparacionController.abrirEditar(rep.getIdRep(), ReparacionControllerSuperTecnico.this::cargarDatos); });
                borrar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarReparacion(rep); });
                aniadirInc .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) abrirDialogoIncidencia(rep); });
                cancelarInc.setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarIncidencia(rep); });
                editarObs  .setOnAction(e -> { if (getItem() instanceof com.reparaciones.models.GrupoImei grupo) ReparacionControllerSuperTecnico.this.abrirDialogoObservacionTelefono(grupo); });
                editarCli  .setOnAction(e -> {
                    if (!(getItem() instanceof com.reparaciones.models.GrupoImei grupo)) return;
                    try {
                        java.util.List<com.reparaciones.models.Cliente> activos = clienteDAO.getActivos();
                        Integer idActual = activos.stream()
                                .filter(c -> c.getNombre().equals(grupo.getCliente()))
                                .map(com.reparaciones.models.Cliente::getIdCli).findFirst().orElse(null);
                        java.util.Optional<Integer> sel = com.reparaciones.utils.SelectorClienteDialog.elegir(activos, idActual);
                        if (sel.isEmpty()) return;
                        Integer idCli = (sel.get() == -1) ? null : sel.get();
                        telefonoDAO.actualizarCliente(grupo.getImei(), idCli, grupo.getTelefonoUpdatedAt());
                        cargarDatos();
                    } catch (com.reparaciones.utils.StaleDataException ex) {
                        Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                        cargarDatos();
                    } catch (java.sql.SQLException ex) { mostrarError(ex); }
                });
                menu.getItems().addAll(editar, borrar, new SeparatorMenuItem(), copiar, new SeparatorMenuItem(), aniadirInc, cancelarInc, new SeparatorMenuItem(), editarObs, new SeparatorMenuItem(), editarCli);
                menu.setOnShowing(e -> {
                    boolean esGrupo = getItem() instanceof com.reparaciones.models.GrupoImei;
                    if (!(getItem() instanceof ReparacionResumen rep)) {
                        editar.setVisible(false); borrar.setVisible(false);
                        aniadirInc.setVisible(false); cancelarInc.setVisible(false);
                        editarObs.setVisible(esGrupo);
                        editarCli.setVisible(esGrupo && modoActual == Modo.MAESTRO);
                        return;
                    }
                    boolean tieneInc = rep.isEsIncidencia() && !rep.isEsResuelto();
                    editar      .setVisible(rep.getIdRep().startsWith("R"));
                    borrar      .setVisible(true);
                    aniadirInc  .setVisible(!rep.isEsIncidencia());
                    cancelarInc .setVisible(tieneInc);
                    editarObs   .setVisible(false);
                    editarCli   .setVisible(false);
                });
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });

                // Drill-down al hacer doble clic en fila de grupo (clic simple solo selecciona)
                setOnMouseClicked(e -> {
                    if (!isEmpty() && getItem() instanceof GrupoImei grupo
                            && e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && e.getClickCount() == 2) {
                        mostrarDetalle(grupo);
                    }
                });

                selectedProperty().addListener((obs, wasSelected, isSelected) -> aplicarEstilo(getItem(), isEmpty()));
            }

            private void aplicarEstilo(Object item, boolean empty) {
                if (empty || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (item instanceof GrupoImei g) {
                    if (isSelected()) {
                        setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                                "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                                "-fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;");
                    } else {
                        String brd = g.getCountIncAbiertas() > 0 ? com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD : "#2C3B54";
                        setStyle("-fx-background-color: #EEF0F5;" +
                                "-fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;" +
                                "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + brd + ";" +
                                "-fx-cursor: hand;");
                    }
                } else if (item instanceof ReparacionResumen rep) {
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
            }

            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
                setOpacity(item instanceof ReparacionResumen rep && idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0);
            }
        });
    }

    private String textoDeCelda(Object row, TableColumn<?, ?> col) {
        if (row instanceof GrupoImei g) {
            if (col == colImei)       return g.getImei();
            if (col == colModelo)     { String m = g.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
            if (col == colFecha)      return (g.getFechaMasAntigua() != null ? FechaUtils.formatear(g.getFechaMasAntigua(), FORMATO_FECHA) : "—")
                                            + " → " + (g.getFechaMasReciente() != null ? FechaUtils.formatear(g.getFechaMasReciente(), FORMATO_FECHA) : "—");
            if (col == colComponente) return g.getReparaciones().size() + " reparaciones";
            if (col == colEstado)     return g.getCountIncAbiertas() > 0 ? "Incidencia" : "Normal";
            return null;
        }
        if (!(row instanceof ReparacionResumen rep)) return null;
        if (col == colIdRep)         return rep.getIdRep();
        if (col == colImei)          return rep.getImei();
        if (col == colModelo)        { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == colReparador)     return rep.getNombreTecnico();
        if (col == colFecha)         return FechaUtils.formatear(rep.getFechaFin(), FORMATO_FECHA);
        if (col == colComponente)    return rep.getTipoComponente();
        if (col == colObservaciones) return rep.getObservaciones();
        if (col == colIncidencia)    return rep.getIncidencia();
        if (col == colIdAnterior)    return rep.getIdRepAnterior();
        return null;
    }

    private void cargarDatos() {
        try {
            datos.setAll(reparacionDAO.getReparacionesResumen());
            poblarFiltroCliente();
            poblarFiltroPieza();
            aplicarFiltros();
            lblUltimaActualizacion.setText("Actualizado " +
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    private void configurarFiltros() {
        try {
            tecnicosLista.addAll(tecnicoDAO.getAll());
            filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
                filtroTecnico, tecnicosLista,
                Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec());
                                  else         idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
        } catch (SQLException e) {
            mostrarError(e);
        }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
            if (!can.equals(n)) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                return;
            }
            switch (com.reparaciones.utils.FiltroImei.estado(n)) {
                case VACIO      -> filtroImei.setStyle("");
                case INCOMPLETO -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
                case VALIDO     -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            }
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
        cbIncidenciasAbiertas.selectedProperty().addListener((obs, o, n) -> { actualizarTextoFiltroIncidencias(); aplicarFiltros(); });
        cbIncidenciasCerradas = new CheckBox("Cerradas");
        cbIncidenciasCerradas.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbIncidenciasCerradas.selectedProperty().addListener((obs, o, n) -> { actualizarTextoFiltroIncidencias(); aplicarFiltros(); });
        cbNormales = new CheckBox("Sin incidencia");
        cbNormales.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
        cbNormales.selectedProperty().addListener((obs, o, n) -> { actualizarTextoFiltroIncidencias(); aplicarFiltros(); });
        itemCerradas = new CustomMenuItem(cbIncidenciasCerradas, false);
        filtroIncidencias.getItems().addAll(
            new CustomMenuItem(cbIncidenciasAbiertas, false),
            itemCerradas,
            new CustomMenuItem(cbNormales, false));

        filtroCliHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            filtroCliente,
            new java.util.ArrayList<>(),
            java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli);
                                else         clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);

        filtroPiezaHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            filtroPieza,
            new java.util.ArrayList<>(),
            java.util.function.Function.identity(),
            pieza -> piezasFiltro.contains(pieza),
            (pieza, checked) -> { if (checked) piezasFiltro.add(pieza);
                                  else         piezasFiltro.remove(pieza);
                                  actualizarTextoFiltroPieza(); aplicarFiltros(); },
            etiquetaPieza);
    }

    private void poblarFiltroCliente() {
        java.util.List<String> clientes = datos.stream()
            .map(r -> { String c = r.getCliente(); return (c == null || c.isEmpty()) ? SIN_CLIENTE : c; })
            .distinct().sorted()
            .collect(java.util.stream.Collectors.toList());
        if (!clientes.contains(SIN_CLIENTE) && datos.stream().anyMatch(r -> r.getCliente() == null || r.getCliente().isEmpty()))
            clientes.add(0, SIN_CLIENTE);
        filtroCliHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            filtroCliente, clientes,
            java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli);
                                else         clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);
    }

    private void actualizarTextoFiltroCliente() {
        long sel = clientesFiltro.size();
        if (sel == 0)      etiquetaCli.set("Cliente");
        else if (sel == 1) etiquetaCli.set(clientesFiltro.iterator().next());
        else               etiquetaCli.set(sel + " clientes");
    }

    private void poblarFiltroPieza() {
        java.util.List<String> piezas = datos.stream()
            .map(r -> com.reparaciones.utils.Piezas.categoria(r.getTipoComponente()))
            .filter(c -> !c.isEmpty())
            .distinct().sorted()
            .collect(java.util.stream.Collectors.toList());
        filtroPiezaHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            filtroPieza, piezas,
            java.util.function.Function.identity(),
            pieza -> piezasFiltro.contains(pieza),
            (pieza, checked) -> { if (checked) piezasFiltro.add(pieza);
                                  else         piezasFiltro.remove(pieza);
                                  actualizarTextoFiltroPieza(); aplicarFiltros(); },
            etiquetaPieza);
    }

    private void actualizarTextoFiltroPieza() {
        long sel = piezasFiltro.size();
        if (sel == 0)      etiquetaPieza.set("Pieza");
        else if (sel == 1) etiquetaPieza.set(piezasFiltro.iterator().next());
        else               etiquetaPieza.set(sel + " piezas");
    }

    private void aplicarFiltros() {
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        boolean filtrarAbiertas = cbIncidenciasAbiertas.isSelected();
        boolean filtrarCerradas = cbIncidenciasCerradas.isSelected();
        boolean filtrarNormales = cbNormales.isSelected();
        idsAjenas.clear();
        if (modoActual == Modo.PLANO) {
            java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(filtroImei.getText().trim());
            List<ReparacionResumen> filtradas = datos.stream()
                .filter(rep -> {
                    if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
                    if (!idsTecFiltro.isEmpty() && !idsTecFiltro.contains(rep.getIdTec())) return false;
                    if (desde != null || hasta != null) {
                        if (rep.getFechaFin() == null) return false;
                        LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
                        if (desde != null && fechaFin.isBefore(desde)) return false;
                        if (hasta != null && fechaFin.isAfter(hasta))  return false;
                    }
                    if (filtrarAbiertas || filtrarCerradas || filtrarNormales) {
                        boolean mostrar = false;
                        if (filtrarNormales && !rep.isEsIncidencia())                        mostrar = true;
                        if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto())  mostrar = true;
                        if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto())  mostrar = true;
                        if (!mostrar) return false;
                    }
                    if (!piezasFiltro.isEmpty()) {
                        String cat = com.reparaciones.utils.Piezas.categoria(rep.getTipoComponente());
                        if (!piezasFiltro.contains(cat)) return false;
                    }
                    return true;
                }).collect(java.util.stream.Collectors.toList());
            tablaItems.setAll(filtradas);
            lblContadorPlano.setText(filtradas.size() + " reparaci" + (filtradas.size() == 1 ? "ón" : "ones"));
            lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
            return;
        }
        if (modoActual == Modo.DETALLE) {
            lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
            List<ReparacionResumen> todas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(rep -> {
                    if (desde != null || hasta != null) {
                        if (rep.getFechaFin() == null) return false;
                        LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
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

            if (idsTecFiltro.isEmpty()) {
                tablaItems.setAll(todas);
                lblNavCount.setText("  •  " + todas.size() + " reparaci" + (todas.size() == 1 ? "ón" : "ones"));
            } else {
                List<ReparacionResumen> delFiltro = todas.stream()
                    .filter(r -> idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                List<ReparacionResumen> otras = todas.stream()
                    .filter(r -> !idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                otras.forEach(r -> idsAjenas.add(r.getIdRep()));
                List<ReparacionResumen> resultado = new ArrayList<>(delFiltro);
                resultado.addAll(otras);
                tablaItems.setAll(resultado);
                int nO = otras.size();
                lblNavCount.setText("  •  " + delFiltro.size() + " de filtrados"
                    + (nO > 0 ? " + " + nO + " de otros" : ""));
            }
            return;
        }

        String imeiStr = filtroImei.getText().trim();
        datosFiltrados = datos.stream().filter(rep -> {
            java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(imeiStr);
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (desde != null || hasta != null) {
                if (rep.getFechaFin() == null) return false;
                LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
                if (desde != null && fechaFin.isBefore(desde)) return false;
                if (hasta != null && fechaFin.isAfter(hasta))  return false;
            }
            if (!clientesFiltro.isEmpty()) {
                String cli = rep.getCliente();
                boolean sin = (cli == null || cli.isEmpty());
                boolean coincide = (sin && clientesFiltro.contains(SIN_CLIENTE))
                                || (!sin && clientesFiltro.contains(cli));
                if (!coincide) return false;
            }
            return true;
        }).collect(Collectors.toList());
        buildTablaItems();
        int nImeis = tablaItems.size();
        lblContadorPlano.setText(nImeis + (nImeis == 1 ? " IMEI" : " IMEIs"));
        lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
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
            if (!idsTecFiltro.isEmpty()
                    && e.getValue().stream().noneMatch(r -> idsTecFiltro.contains(r.getIdTec())))
                continue;
            if (filtrarInc || filtrarNormal) {
                boolean tieneInc = grupo.getCountIncAbiertas() > 0;
                boolean ok = (filtrarInc && tieneInc) || (filtrarNormal && !tieneInc);
                if (!ok) continue;
            }
            tablaItems.add(grupo);
        }
    }

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

        // Insertar entre filtros (índice 0) y tabla (índice 1)
        pnlHistRep.getChildren().add(1, barraNavegacion);
    }

    private void mostrarDetalle(GrupoImei grupo) {
        mostrarDetalleParaImei(grupo.getImei());
    }

    private void mostrarDetalleParaImei(String imei) {
        modoActual  = Modo.DETALLE;
        imeiDetalle = imei;

        String modelo = datos.stream()
                .filter(r -> r.getImei().equals(imei))
                .map(ReparacionResumen::getModelo)
                .filter(m -> m != null && !m.isEmpty())
                .findFirst().orElse("");
        lblNavImei  .setText("IMEI: " + imei);
        lblNavModelo.setText(!modelo.isEmpty() ? "  •  " + FormularioReparacionController.traducirModelo(modelo) : "");

        filtroImei     .setVisible(false); filtroImei     .setManaged(false);
        barraNavegacion.setVisible(true);  barraNavegacion.setManaged(true);
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true);
        colIdAnterior.setVisible(true); colObservacionTelefono.setVisible(false); colCliente.setVisible(false);
        colRevision.setVisible(false);
        colComponente.setText("Componente");
        adaptarFiltrosDetalle();
        javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> {
            aplicarAnchosDetalle();
            tablaReparaciones.refresh();
        }));
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

    /** Resetea el estado a MAESTRO sin reconstruir la lista (útil cuando el panel se va a ocultar). */
    private void resetarModo() {
        modoActual  = Modo.MAESTRO;
        imeiDetalle = null;
        if (barraNavegacion != null) {
            barraNavegacion.setVisible(false); barraNavegacion.setManaged(false);
            filtroImei     .setVisible(true);  filtroImei     .setManaged(true);
        }
        colIdRep.setVisible(false); colReparador.setVisible(false); colAsignadoPor.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false);
        colIdAnterior.setVisible(false); colObservacionTelefono.setVisible(true); colCliente.setVisible(true);
        colRevision.setVisible(true);
        colComponente.setText("Reparaciones");
        adaptarFiltrosMaestro();
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });
    }

    /** Entra en modo PLANO: todas las reparaciones sin agrupar, columnas estilo detalle,
     *  filtro de IMEI visible y sin barra de navegación. */
    private void entrarModoPlano() {
        modoActual  = Modo.PLANO;
        imeiDetalle = null;
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true);
        colIdAnterior.setVisible(true); colObservacionTelefono.setVisible(false); colCliente.setVisible(false);
        colRevision.setVisible(false);
        colComponente.setText("Componente");
        filtroImei.setVisible(true); filtroImei.setManaged(true);
        if (barraNavegacion != null) { barraNavegacion.setVisible(false); barraNavegacion.setManaged(false); }
        adaptarFiltrosDetalle();
        lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
        javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> {
            aplicarAnchosDetalle();
            tablaReparaciones.refresh();
        }));
        aplicarFiltros();
    }

    // ─── Helpers de filtro ────────────────────────────────────────────────────

    private void adaptarFiltrosMaestro() {
        filtroTecnico.setVisible(true); filtroTecnico.setManaged(true);
        filtroCliente.setVisible(true); filtroCliente.setManaged(true);
        filtroPieza.setVisible(false); filtroPieza.setManaged(false);
        actualizarTextoFiltroTecnico();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        cbIncidenciasAbiertas.setText("Incidencia");
        cbIncidenciasCerradas.setSelected(false);
        if (itemCerradas != null) itemCerradas.setVisible(false);
        cbNormales.setText("Normal");
        actualizarTextoFiltroIncidencias();
    }

    private void adaptarFiltrosDetalle() {
        filtroTecnico.setVisible(true); filtroTecnico.setManaged(true);
        filtroCliente.setVisible(false); filtroCliente.setManaged(false);
        filtroPieza.setVisible(modoActual == Modo.PLANO); filtroPieza.setManaged(modoActual == Modo.PLANO);
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

    private void actualizarTextoFiltroTecnico() {
        long sel = idsTecFiltro.size();
        if (sel == 0) {
            etiquetaTec.set("Técnico");
        } else if (sel == 1) {
            int id = idsTecFiltro.iterator().next();
            String nombre = tecnicosLista.stream()
                    .filter(t -> t.getIdTec() == id)
                    .findFirst().map(Tecnico::getNombre).orElse("Técnico");
            etiquetaTec.set(nombre);
        } else {
            etiquetaTec.set(sel + " técnicos");
        }
    }

    public void setFiltroInicial(java.time.LocalDate desde, java.time.LocalDate hasta, String tecnico) {
        mostrarHistorial();
        if (modoActual == Modo.DETALLE) volverAGrupos();
        if (tecnico != null) {
            idsTecFiltro.clear();
            tecnicosLista.stream().filter(t -> t.getNombre().equals(tecnico))
                    .findFirst().ifPresent(t -> idsTecFiltro.add(t.getIdTec()));
            if (filtroTecHandle != null) filtroTecHandle.refresh();
            actualizarTextoFiltroTecnico();
        }
        filtroFechaDesde.setValue(desde);
        filtroFechaHasta.setValue(hasta);
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        idsTecFiltro.clear();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        etiquetaTec.set("Técnico");
        clientesFiltro.clear();
        if (filtroCliHandle != null) filtroCliHandle.refresh();
        etiquetaCli.set("Cliente");
        piezasFiltro.clear();
        if (filtroPiezaHandle != null) filtroPiezaHandle.refresh();
        etiquetaPieza.set("Pieza");
        filtroFechaDesde.setValue(null);
        filtroFechaHasta.setValue(null);
        cbIncidenciasAbiertas.setSelected(false);
        cbIncidenciasCerradas.setSelected(false);
        cbNormales.setSelected(false);
        filtroIncidencias.setText("Incidencias");
        aplicarFiltros();
    }

    // ─── Modal pendientes ─────────────────────────────────────────────────────

    @FXML
    private void abrirModalPendientes() {
        mostrarPanel(pnlPendientes, btnTabPendientes);
    }

    @FXML
    private void abrirMisPendientes() {
        mostrarPanel(pnlMisPendientes, btnTabMisPendientes);
    }

    // ─── Incidencias ──────────────────────────────────────────────────────────

    private void abrirDialogoIncidencia(ReparacionResumen rep) {

        Label lblComentario = new Label("Comentario de incidencia");
        String textoInicial = rep.getIncidencia() != null ? rep.getIncidencia() : "";
        TextArea tfComentario = new TextArea(textoInicial);
        tfComentario.setPromptText("Describe la incidencia...");
        tfComentario.setWrapText(true);
        tfComentario.setPrefRowCount(4);
        tfComentario.setStyle("-fx-background-color: white; -fx-border-color: " + com.reparaciones.utils.Colores.GRIS_BORDE + ";" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");

        Label lblTecnico = new Label("Técnico asignado");
        ComboBox<Tecnico> cbTecnico = new ComboBox<>();
        cbTecnico.setMaxWidth(Double.MAX_VALUE);
        cbTecnico.setVisibleRowCount(8);
        cbTecnico.setStyle("-fx-background-color: white; -fx-border-color: " + com.reparaciones.utils.Colores.GRIS_BORDE + ";" +
                "-fx-border-radius: 4; -fx-background-radius: 4;");

        try {
            List<Tecnico> tecnicos = tecnicoDAO.getAllActivos();
            cbTecnico.getItems().addAll(tecnicos);
            tecnicos.stream()
                    .filter(t -> t.getIdTec() == rep.getIdTec())
                    .findFirst()
                    .ifPresent(cbTecnico::setValue);
        } catch (SQLException ex) {
            mostrarError(ex);
        }

        cbTecnico.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Tecnico t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.getNombre());
            }
        });
        cbTecnico.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Tecnico t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? "Selecciona técnico" : t.getNombre());
            }
        });

        Button btnConfirmar = new Button("Añadir incidencia y asignar");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        btnConfirmar.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.GRIS_DISABLED + "; -fx-text-fill: " + com.reparaciones.utils.Colores.GRIS_BORDE + ";" +
                "-fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8;");

        Runnable validar = () -> {
            boolean ok = !tfComentario.getText().trim().isEmpty() && cbTecnico.getValue() != null;
            btnConfirmar.setDisable(!ok);
            btnConfirmar.setStyle(ok
                    ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + "; -fx-text-fill: white; -fx-font-size: 12px;" +
                            "-fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;"
                    : "-fx-background-color: " + com.reparaciones.utils.Colores.GRIS_DISABLED + "; -fx-text-fill: " + com.reparaciones.utils.Colores.GRIS_BORDE + "; -fx-font-size: 12px;" +
                            "-fx-background-radius: 4; -fx-padding: 8;");
        };
        validar.run();
        tfComentario.textProperty().addListener((obs, o, n) -> validar.run());
        cbTecnico.valueProperty().addListener((obs, o, n) -> validar.run());

        VBox form = new VBox(8, lblComentario, tfComentario, lblTecnico, cbTecnico, btnConfirmar);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
        form.setPrefWidth(480);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Añadir incidencia");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        btnConfirmar.setOnAction(e -> {
            String comentario = tfComentario.getText().trim();
            int idTec = cbTecnico.getValue().getIdTec();
            try {
                reparacionDAO.marcarIncidenciaYAsignar(rep.getIdRep(), comentario, rep.getImei(), idTec);
                dialog.close();
                cargarDatos();
            } catch (SQLException ex) {
                Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
            }
        });

        dialog.showAndWait();
    }

    private void abrirDialogoObservacionTelefono(com.reparaciones.models.GrupoImei grupo) {
        TextArea tfObs = new TextArea(grupo.getObservacion() != null ? grupo.getObservacion() : "");
        tfObs.setPromptText("Observación del teléfono...");
        tfObs.setWrapText(true);
        tfObs.setPrefRowCount(4);
        tfObs.setStyle("-fx-background-color: white; -fx-border-color: " + com.reparaciones.utils.Colores.GRIS_BORDE + ";" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");

        Button btnConfirmar = new Button("Guardar");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        btnConfirmar.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + "; -fx-text-fill: white;" +
                " -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;");

        VBox form = new VBox(8, new Label("Observación — IMEI " + grupo.getImei()), tfObs, btnConfirmar);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
        form.setPrefWidth(480);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Observación del teléfono");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        btnConfirmar.setOnAction(e -> {
            try {
                telefonoDAO.actualizarObservacion(grupo.getImei(), tfObs.getText().trim(), grupo.getTelefonoUpdatedAt());
                dialog.close();
                cargarDatos();
            } catch (com.reparaciones.utils.StaleDataException ex) {
                Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                dialog.close();
                cargarDatos();
            } catch (SQLException ex) {
                Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
            }
        });

        dialog.showAndWait();
    }

    private void borrarIncidencia(ReparacionResumen rep) {
        ConfirmDialog.mostrar(
                "Borrar incidencia",
                "Esta acción solo es válida si fue un error al añadirla.",
                "Borrar incidencia",
                () -> {
                    try {
                        reparacionComponenteDAO.borrarIncidencia(rep.getIdRep());
                        cargarDatos();
                    } catch (SQLException e) {
                        mostrarError(e);
                    }
                }
        );
    }

    private void borrarReparacion(ReparacionResumen rep) {
        try {
            String ref = reparacionDAO.getReferenciadora(rep.getIdRep());
            if (ref != null) {
                Alert alerta = new Alert(Alert.AlertType.WARNING);
                alerta.setTitle("No se puede borrar");
                alerta.setHeaderText("Esta reparación está siendo referenciada");
                alerta.setContentText("La reparación " + ref + " apunta a esta. Bórrala primero.");
                alerta.showAndWait();
                return;
            }
            ConfirmDialog.mostrarConMotivo(
                    "Borrar reparación",
                    "Se borrará " + rep.getIdRep() + ". Los componentes usados volverán a stock y, si resolvía una incidencia, esta quedará activa de nuevo. Escribe el motivo.",
                    "Borrar reparación",
                    motivo -> {
                        try {
                            reparacionDAO.eliminar(rep.getIdRep(), motivo);
                            cargarDatos();
                        } catch (SQLException e) {
                            mostrarError(e);
                        }
                    }
            );
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    @FXML
    private void descargarHistorial() {
        exportarCSV((Stage) tablaReparaciones.getScene().getWindow());
    }

    @Override
    public void exportarCSV(Stage owner) {
        DateTimeFormatter fmt     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (pnlPendientes.isVisible()) {
            // Tabla unificada (reparación + glass + pulido): exporta todo lo visible.
            List<ReparacionResumen> items = pendientesSuperTecnicoController.getItemsVisibles();
            List<String> cabeceras = List.of(
                    "ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                    "Componente", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
            List<List<String>> filas = new ArrayList<>();
            for (ReparacionResumen r : items) filas.add(filaReparacion(r, fmtHora));
            com.reparaciones.utils.CsvExporter.exportar(owner, "reparaciones_pendientes", cabeceras, filas);
            return;
        }

        if (pnlMisPendientes.isVisible()) {
            if (toggleMisPendPul.isSelected()) {
                exportarPulidosPendientes(owner, misPulidosTecnicoController.getItemsVisibles(), false);
                return;
            }
            List<ReparacionResumen> items = (toggleMisPendGlass.isSelected()
                    ? misPendientesGlassController : misPendientesController).getItemsVisibles();
            List<String> cabeceras = List.of(
                    "ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                    "Componente", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
            List<List<String>> filas = new ArrayList<>();
            for (ReparacionResumen r : items) filas.add(filaReparacion(r, fmtHora));
            com.reparaciones.utils.CsvExporter.exportar(owner, "mis_pendientes", cabeceras, filas);
            return;
        }

        if (toggleHistPul.isSelected()) {
            exportarHistorialPulidos(owner, historialPulidoController.getItemsVisibles(), true);
            return;
        }

        if (modoActual == Modo.MAESTRO) {
            List<String> cabeceras = List.of(
                    "IMEI", "Modelo", "Técnico", "Primera reparación", "Última reparación",
                    "Nº reparaciones", "Inc. abiertas", "Observación", "Revisión logística");
            List<List<String>> filas = new ArrayList<>();
            for (Object o : tablaItems) {
                if (!(o instanceof GrupoImei g)) continue;
                String modelo  = g.getModelo();
                String tecnico = g.getReparaciones().isEmpty() ? "" :
                        (g.getReparaciones().get(0).getNombreTecnico() != null ? g.getReparaciones().get(0).getNombreTecnico() : "");
                filas.add(List.of(
                        com.reparaciones.utils.CsvExporter.textoForzado(g.getImei()),
                        (modelo != null && !modelo.isEmpty()) ? FormularioReparacionController.traducirModelo(modelo) : "",
                        tecnico,
                        FechaUtils.formatear(g.getFechaMasAntigua(), fmt),
                        FechaUtils.formatear(g.getFechaMasReciente(), fmt),
                        String.valueOf(g.getReparaciones().size()),
                        String.valueOf(g.getCountIncAbiertas()),
                        g.getObservacion() != null ? g.getObservacion() : "",
                        (g.isRevisionLogistica() && !g.isTieneAsignaciones()) ? "Sí" : "No"
                ));
            }
            com.reparaciones.utils.CsvExporter.exportar(owner, "historial_reparaciones", cabeceras, filas);
            return;
        }

        // DETALLE
        List<ReparacionResumen> items = tablaItems.stream()
                .filter(o -> o instanceof ReparacionResumen)
                .map(o -> (ReparacionResumen) o)
                .collect(Collectors.toList());
        List<String> cabeceras = List.of(
                "ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                "Componente", "Reutilizado", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        for (ReparacionResumen r : items) filas.add(filaReparacion(r, fmtHora));
        com.reparaciones.utils.CsvExporter.exportar(owner, "historial_reparaciones", cabeceras, filas);
    }

    private List<String> filaReparacion(ReparacionResumen r, DateTimeFormatter fmt) {
        List<String> fila = new ArrayList<>();
        fila.add(r.getIdRep());
        fila.add(com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()));
        fila.add(r.getNombreTecnico() != null ? r.getNombreTecnico() : "");
        fila.add(FechaUtils.formatear(r.getFechaAsig(), fmt));
        fila.add(FechaUtils.formatear(r.getFechaFin(), fmt));
        fila.add(r.getTipoComponente() != null ? r.getTipoComponente() : "");
        fila.add(r.isEsReutilizado() ? "Sí" : "No");
        fila.add(r.getObservaciones()  != null ? r.getObservaciones()  : "");
        fila.add(r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No");
        fila.add(r.isEsResuelto() ? "Sí" : "No");
        fila.add(r.getIdRepAnterior() != null ? r.getIdRepAnterior() : "");
        return fila;
    }

    private void exportarPulidosPendientes(Stage owner, List<ReparacionResumen> items, boolean conTecnico) {
        List<String> cabeceras = conTecnico
                ? List.of("ID", "IMEI", "Modelo", "Técnico", "Fecha asig.", "Comentario")
                : List.of("ID", "IMEI", "Modelo", "Fecha asig.", "Comentario");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<List<String>> filas = new ArrayList<>();
        for (ReparacionResumen r : items) {
            List<String> fila = new ArrayList<>();
            fila.add(r.getIdRep());
            fila.add(com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()));
            String m = r.getModelo();
            fila.add((m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
            if (conTecnico) fila.add(r.getNombreTecnico() != null ? r.getNombreTecnico() : "");
            fila.add(FechaUtils.formatear(r.getFechaAsig(), fmt));
            fila.add(r.getComentarioAsignacion() != null ? r.getComentarioAsignacion() : "");
            filas.add(fila);
        }
        com.reparaciones.utils.CsvExporter.exportar(owner, "pulidos_pendientes", cabeceras, filas);
    }

    private void exportarHistorialPulidos(Stage owner, List<ReparacionResumen> items, boolean conTecnico) {
        List<String> cabeceras = conTecnico
                ? List.of("ID", "IMEI", "Modelo", "Técnico", "Fecha inicio", "Fecha fin", "Comentario")
                : List.of("ID", "IMEI", "Modelo", "Fecha inicio", "Fecha fin", "Comentario");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<List<String>> filas = new ArrayList<>();
        for (ReparacionResumen r : items) {
            List<String> fila = new ArrayList<>();
            fila.add(r.getIdRep());
            fila.add(com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()));
            String m = r.getModelo();
            fila.add((m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
            if (conTecnico) fila.add(r.getNombreTecnico() != null ? r.getNombreTecnico() : "");
            fila.add(FechaUtils.formatear(r.getFechaAsig(), fmt));
            fila.add(FechaUtils.formatear(r.getFechaFin(), fmt));
            fila.add(r.getComentarioAsignacion() != null ? r.getComentarioAsignacion() : "");
            filas.add(fila);
        }
        com.reparaciones.utils.CsvExporter.exportar(owner, "historial_pulidos", cabeceras, filas);
    }

    private void mostrarError(Exception e) {
        if (e instanceof com.reparaciones.utils.ConexionException
                && com.reparaciones.utils.ConexionEstado.enRefresco()) return;   // refresco: lo indica el banner
        Alertas.mostrarError(e.getMessage());
    }
}
