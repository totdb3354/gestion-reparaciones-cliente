package com.reparaciones.controllers;

import com.reparaciones.dao.ReparacionComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.models.ReparacionResumen;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    @FXML private TextField  filtroImei;
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
    @FXML private Button btnTabAgrupado;
    @FXML private Label  lblBadgeAsignaciones;
    @FXML private Label  lblBadgePendientes;
    @FXML private VBox   pnlHistorial;
    @FXML private VBox   pnlPendientes;
    @FXML private VBox   pnlMisPendientes;
    @FXML private VBox   pnlAgrupado;
    @FXML private AgrupadoController agrupadoController;
    @FXML private PendientesSuperTecnicoController pendientesSuperTecnicoController;
    @FXML private PendientesTecnicoController      misPendientesController;

    // ── Toggles y sub-paneles de pulido ───────────────────────────────────────
    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistGlass;
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
    private final com.reparaciones.dao.GlassDAO glassDAO = new com.reparaciones.dao.GlassDAO();
    private final ReparacionComponenteDAO reparacionComponenteDAO = new ReparacionComponenteDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();

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

        pendientesSuperTecnicoController.setOnActualizar(() -> {
            cargarDatos();
            pendientesSuperTecnicoController.cargar();
            misPendientesController.cargar();
            misPendientesGlassController.cargar();
            misPulidosTecnicoController.cargar();
            actualizarBadges();
        });
        misPendientesController.setOnCerrar(() -> {
            cargarDatos();
            // (sin misPendientesController.cargar(): quien dispara onCerrar ya se recargó a sí mismo)
            pendientesSuperTecnicoController.cargar();   // el badge de Asignaciones sale de aquí
            actualizarBadges();
        });
        misPendientesGlassController.setModoGlass();
        misPendientesGlassController.cargar();   // recarga como AG (el initialize del include cargó A por defecto)
        misPulidosTecnicoController.cargar();     // para el badge y toggles (suma rep + glass + pulido)
        misPendientesGlassController.setOnCerrar(() -> {
            cargarDatos();
            // (sin auto-recarga: quien dispara onCerrar ya se recargó a sí mismo)
            pendientesSuperTecnicoController.cargar();   // el badge de Asignaciones sale de aquí
            actualizarBadges();
        });
        misPulidosTecnicoController.setOnCerrar(() -> {
            cargarDatos();
            // (sin auto-recarga: quien dispara onCerrar ya se recargó a sí mismo)
            pendientesSuperTecnicoController.cargar();   // el badge de Asignaciones sale de aquí
            actualizarBadges();
        });

        // Toggle historial: Reparaciones | Glass | Pulidos (siempre plano)
        javafx.scene.control.ToggleGroup tgHist = new javafx.scene.control.ToggleGroup();
        toggleHistRep.setToggleGroup(tgHist);
        toggleHistGlass.setToggleGroup(tgHist);
        toggleHistPul.setToggleGroup(tgHist);
        tgHist.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o != null ? (javafx.scene.control.ToggleButton) o : toggleHistRep).setSelected(true); return; }
            boolean pulido = (n == toggleHistPul);
            pnlHistRep.setVisible(!pulido); pnlHistRep.setManaged(!pulido);
            pnlHistPul.setVisible(pulido);  pnlHistPul.setManaged(pulido);
            if (pulido) {
                historialPulidoController.setFiltroImei(filtroImei.getText());
                historialPulidoController.cargar();
            } else {
                if (o == toggleHistPul) filtroImei.setText(historialPulidoController.getFiltroImei());
                cargarDatos();   // rep o glass según el toggle seleccionado
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

        tablaReparaciones.setItems(tablaItems);
        entrarModoPlano();   // el Historial es siempre plano; el agrupado vive en su apartado

        agrupadoController.configurar(AgrupadoController.Rol.SUPERTECNICO);

        mostrarPanel(pnlPendientes, btnTabPendientes);

        com.reparaciones.utils.Poller.programarSiguiente(poller, this::recargar);
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> recargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
        actualizarBadges();
        // El estilo (invertido) de ambos badges lo fija ya mostrarPanel(pnlPendientes, …)
        // según la pestaña activa. No re-forzar aquí a inactivo o el badge de la pestaña
        // inicial (Asignaciones) no aparece invertido hasta cambiar de opción en el sidebar.
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
        } else if (pnlAgrupado.isVisible()) {
            agrupadoController.cargar();
        } else {
            if (toggleHistPul.isSelected()) historialPulidoController.cargar();
            else                            cargarDatos();
        }
        // Badge data siempre fresco (rep + glass + pulido), independiente del panel visible
        if (!pnlPendientes.isVisible())                                        pendientesSuperTecnicoController.cargar();
        if (!pnlMisPendientes.isVisible() || !toggleMisPendRep.isSelected())   misPendientesController.cargar();
        if (!pnlMisPendientes.isVisible() || !toggleMisPendGlass.isSelected()) misPendientesGlassController.cargar();
        if (!pnlMisPendientes.isVisible() || !toggleMisPendPul.isSelected())   misPulidosTecnicoController.cargar();
        actualizarBadges();
    }

    private void actualizarBadges() {
        setBadge(lblBadgeAsignaciones, pendientesSuperTecnicoController.getTotalItems());
        setBadge(lblBadgePendientes,
                misPendientesController.getTotalItems() + misPendientesGlassController.getTotalItems()
                        + misPulidosTecnicoController.getTotalItems());
        toggleMisPendRep.setText(conteoPill("Reparaciones", misPendientesController.getTotalItems()));
        toggleMisPendGlass.setText(conteoPill("Glass", misPendientesGlassController.getTotalItems()));
        toggleMisPendPul.setText(conteoPill("Pulidos", misPulidosTecnicoController.getTotalItems()));
    }

    /** Texto de un toggle de "Mis pendientes" con su conteo, cap 99+. */
    private static String conteoPill(String base, int n) {
        return base + " (" + (n > 99 ? "99+" : String.valueOf(n)) + ")";
    }

    private void setBadge(Label lbl, int count) {
        javafx.scene.layout.StackPane pane = (javafx.scene.layout.StackPane) lbl.getParent();
        if (count <= 0) { pane.setVisible(false); pane.setManaged(false); return; }
        lbl.setText(count > 99 ? "99+" : String.valueOf(count));
        pane.setVisible(true); pane.setManaged(true);
    }

    private void updateBadgeStyle(Label lbl, boolean active) {
        javafx.scene.layout.StackPane outer = (javafx.scene.layout.StackPane) lbl.getParent().getParent();
        outer.getStyleClass().removeAll("sidebar-item-active", "sidebar-item-inactive");
        outer.getStyleClass().add(active ? "sidebar-item-active" : "sidebar-item-inactive");
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    @FXML private void mostrarHistorial() {
        mostrarPanel(pnlHistorial, btnTabHistorial);
    }

    @FXML private void mostrarAgrupado() {
        if (pnlAgrupado.isVisible() && agrupadoController.enDetalle()) {
            agrupadoController.volverAlMaestro();
            return;
        }
        mostrarPanel(pnlAgrupado, btnTabAgrupado);
    }

    public void irAInicio() {
        mostrarPanel(pnlPendientes, btnTabPendientes);
    }

    private void mostrarPanel(VBox panel, Button btnActivo) {
        // Al salir del apartado Agrupado, volver su drill-down a maestro
        if (pnlAgrupado.isVisible() && panel != pnlAgrupado)
            agrupadoController.resetarModo();

        if (pnlPendientes.isVisible() && panel != pnlPendientes)
            pendientesSuperTecnicoController.resetearCambios();
        pnlHistorial    .setVisible(false); pnlHistorial    .setManaged(false);
        pnlPendientes   .setVisible(false); pnlPendientes   .setManaged(false);
        pnlMisPendientes.setVisible(false); pnlMisPendientes.setManaged(false);
        pnlAgrupado     .setVisible(false); pnlAgrupado     .setManaged(false);
        panel.setVisible(true); panel.setManaged(true);
        for (Button b : new Button[]{btnTabHistorial, btnTabPendientes, btnTabMisPendientes, btnTabAgrupado}) {
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
        } else if (panel == pnlAgrupado) {
            agrupadoController.cargar();
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

        colImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> lbl.setStyle(lbl.getUserData() + "-fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
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
                if (row instanceof ReparacionResumen rep) {
                    String baseStyle = "-fx-font-size: 12px; ";
                    lbl.setUserData(baseStyle);
                    lbl.setText(rep.getImei());
                    lbl.setStyle(baseStyle + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    setGraphic(lbl);
                } else {
                    setGraphic(null);
                }
            }
        });

        colModelo.setCellValueFactory(d -> {
            Object row = d.getValue();
            String m = null;
            if (row instanceof ReparacionResumen rep) m = rep.getModelo();
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
                if (row instanceof ReparacionResumen rep) {
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
                if (row instanceof ReparacionResumen rep) {
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
                if (row instanceof ReparacionResumen rep) {
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
                menu.getItems().addAll(editar, borrar, new SeparatorMenuItem(), copiar, new SeparatorMenuItem(), aniadirInc, cancelarInc);
                menu.setOnShowing(e -> {
                    if (!(getItem() instanceof ReparacionResumen rep)) {
                        editar.setVisible(false); borrar.setVisible(false);
                        aniadirInc.setVisible(false); cancelarInc.setVisible(false);
                        return;
                    }
                    boolean tieneInc = rep.isEsIncidencia() && !rep.isEsResuelto();
                    editar      .setVisible(rep.getIdRep().startsWith("R") || rep.getIdRep().startsWith("G"));   // rep y glass (como en Agrupado)
                    borrar      .setVisible(true);
                    aniadirInc  .setVisible(!rep.isEsIncidencia());
                    cancelarInc .setVisible(tieneInc);
                });
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });

                selectedProperty().addListener((obs, wasSelected, isSelected) -> aplicarEstilo(getItem(), isEmpty()));
            }

            private void aplicarEstilo(Object item, boolean empty) {
                if (empty || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (item instanceof ReparacionResumen rep) {
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
            datos.setAll(toggleHistGlass.isSelected()
                    ? glassDAO.getHistorialGlass()
                    : reparacionDAO.getReparacionesResumen());
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
    }

    // ─── Drill-down ───────────────────────────────────────────────────────────

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

    /** Entra en modo PLANO: todas las reparaciones sin agrupar, columnas estilo detalle,
     *  filtro de IMEI visible y sin barra de navegación. */
    private void entrarModoPlano() {
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true);
        colIdAnterior.setVisible(true);
        colComponente.setText("Componente");
        filtroImei.setVisible(true); filtroImei.setManaged(true);
        adaptarFiltrosDetalle();
        lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
        javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> {
            aplicarAnchosDetalle();
            tablaReparaciones.refresh();
        }));
        aplicarFiltros();
    }

    // ─── Helpers de filtro ────────────────────────────────────────────────────

    private void adaptarFiltrosDetalle() {
        filtroTecnico.setVisible(true); filtroTecnico.setManaged(true);
        filtroCliente.setVisible(false); filtroCliente.setManaged(false);
        filtroPieza.setVisible(true); filtroPieza.setManaged(true);
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
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (pnlAgrupado.isVisible()) { agrupadoController.exportarCSV(owner); return; }

        if (pnlPendientes.isVisible()) {
            // Tabla unificada (reparación + glass + pulido): exporta todo lo visible,
            // espejo de las columnas de la tabla de asignaciones (no de filaReparacion,
            // pensada para el historial).
            List<ReparacionResumen> items = pendientesSuperTecnicoController.getItemsVisibles();
            List<String> cabeceras = List.of(
                    "ID", "Tipo", "Técnico", "IMEI", "Modelo", "Fecha asignación", "Comentario",
                    "Cliente", "Asignado por", "Estado", "Urgente", "Chasis", "Por cerrar", "En espera de pieza");
            List<List<String>> filas = new ArrayList<>();
            for (ReparacionResumen r : items) filas.add(filaAsignacion(r, fmtHora));
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

        List<ReparacionResumen> items = tablaItems.stream()
                .filter(o -> o instanceof ReparacionResumen)
                .map(o -> (ReparacionResumen) o)
                .collect(Collectors.toList());
        List<String> cabeceras = List.of(
                "ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                "Componente", "Reutilizado", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        for (ReparacionResumen r : items) filas.add(filaReparacion(r, fmtHora));
        com.reparaciones.utils.CsvExporter.exportar(owner,
                toggleHistGlass.isSelected() ? "historial_glass" : "historial_reparaciones", cabeceras, filas);
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

    /** Fila de la tabla de asignaciones (pnlPendientes), espejo exacto de sus columnas
     *  (ver PendientesSuperTecnicoController: celdaTipoConChasis, cEstado, menú contextual). */
    private List<String> filaAsignacion(ReparacionResumen r, DateTimeFormatter fmt) {
        List<String> fila = new ArrayList<>();
        fila.add(r.getIdRep());
        fila.add(com.reparaciones.utils.TipoTrabajo.desde(r.getIdRep()).etiqueta());
        fila.add(r.getNombreTecnico() != null ? r.getNombreTecnico() : "");
        fila.add(com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()));
        String m = r.getModelo();
        fila.add((m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        fila.add(FechaUtils.formatear(r.getFechaAsig(), fmt));
        fila.add(r.getComentarioAsignacion() != null ? r.getComentarioAsignacion() : "");
        fila.add(r.getCliente() != null ? r.getCliente() : "");
        fila.add(r.getNombreTecnicoAsigna() != null ? r.getNombreTecnicoAsigna() : "—");

        String estado;
        if (r.isEsIncidencia()) {
            estado = "Incidencia";
        } else if (r.getEsSolicitud() > 0) {
            boolean recibido = "GESTIONADA".equals(r.getEstadoSolicitud()) && r.getStockSolicitud() > 0;
            estado = recibido ? "Recibido" : (r.isEnCaminoSolicitud() ? "En camino" : "Solicitud");
        } else {
            estado = "Normal";
        }
        fila.add(estado);

        fila.add(r.isUrgente() ? "Sí" : "No");
        fila.add(r.isEsChasis() ? "Sí" : "No");
        fila.add(r.isPorCerrar() ? "Sí" : "No");

        // Mismo criterio que CargaTecnicos.enEsperaDePieza (privado): solicitud activa
        // y aún no recibida (gestionada + stock disponible).
        boolean enEsperaDePieza = r.getEsSolicitud() > 0
                && !("GESTIONADA".equals(r.getEstadoSolicitud()) && r.getStockSolicitud() > 0);
        fila.add(enEsperaDePieza ? "Sí" : "No");

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
