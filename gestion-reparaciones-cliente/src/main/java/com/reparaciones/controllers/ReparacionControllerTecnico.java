package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;

import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    @FXML private TableColumn<Object, String> colAsignadoPor;
    @FXML private TableColumn<Object, String> colFecha;
    @FXML private TableColumn<Object, String> colComponente;
    @FXML private TableColumn<Object, String> colObservaciones;
    @FXML private TableColumn<Object, Void>   colEstado;
    @FXML private TableColumn<Object, Void>   colIncidencia;
    @FXML private TableColumn<Object, String> colIdAnterior;
    @FXML private TextField  filtroImei;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroCliente;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    // ── Sidebar + paneles ─────────────────────────────────────────────────────
    @FXML private Button btnTabHistorial;
    @FXML private Button btnTabMisPendientes;
    @FXML private Button btnTabAgrupado;
    @FXML private Label  lblBadgePendientes;
    @FXML private VBox   pnlHistorial;
    @FXML private VBox   pnlMisPendientes;
    @FXML private VBox   pnlAgrupado;
    @FXML private AgrupadoController agrupadoController;
    @FXML private Label  lblUltimaActualizacion;
    @FXML private PendientesTecnicoController misPendientesController;

    @FXML private javafx.scene.control.Label        lblContadorPlano;

    // ── Toggles y sub-paneles de pulido ───────────────────────────────────────
    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistGlass;
    @FXML private javafx.scene.control.ToggleButton toggleHistPul;
    @FXML private VBox pnlHistRep;
    @FXML private VBox pnlHistPul;
    @FXML private HistorialPulidoController historialPulidoController;

    @FXML private javafx.scene.control.ToggleButton togglePendRep;
    @FXML private javafx.scene.control.ToggleButton togglePendGlass;
    @FXML private javafx.scene.control.ToggleButton togglePendPul;
    @FXML private VBox pnlPendRep;
    @FXML private VBox pnlPendGlass;
    @FXML private VBox pnlPendPul;
    @FXML private PendientesTecnicoController misPendientesGlassController;
    @FXML private PulidoTecnicoController pulidoTecnicoController;
    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;
    private CustomMenuItem itemCerradas;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final com.reparaciones.dao.GlassDAO glassDAO = new com.reparaciones.dao.GlassDAO();
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Set<String> idsAjenas    = new HashSet<>();

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
                Thread t = new Thread(r, "poller-reparaciones-tecnico");
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

        tablaReparaciones.setItems(tablaItems);
        entrarModoPlano();   // el Historial es siempre plano; el agrupado vive en su apartado

        misPendientesController.setOnCerrar(() -> {
            cargarDatos();
            // (sin auto-recarga: quien dispara onCerrar ya se recargó a sí mismo)
            actualizarBadges();
        });
        misPendientesGlassController.setModoGlass();
        misPendientesGlassController.setOnCerrar(() -> {
            cargarDatos();
            // (sin auto-recarga: quien dispara onCerrar ya se recargó a sí mismo)
            actualizarBadges();
        });
        // Pulido: al completar/borrar, refrescar badge y sufijo del toggle en vivo (faltaba cablearlo)
        pulidoTecnicoController.setOnCerrar(this::actualizarBadges);

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

        // Toggle pendientes: Reparaciones | Glass | Pulidos
        javafx.scene.control.ToggleGroup tgPend = new javafx.scene.control.ToggleGroup();
        togglePendRep.setToggleGroup(tgPend);
        togglePendGlass.setToggleGroup(tgPend);
        togglePendPul.setToggleGroup(tgPend);
        tgPend.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o == null ? togglePendRep : (javafx.scene.control.ToggleButton) o).setSelected(true); return; }
            String filtro = o == togglePendGlass ? misPendientesGlassController.getFiltroImei()
                          : o == togglePendPul   ? pulidoTecnicoController.getFiltroImei()
                          :                        misPendientesController.getFiltroImei();
            pnlPendRep.setVisible(n == togglePendRep);     pnlPendRep.setManaged(n == togglePendRep);
            pnlPendGlass.setVisible(n == togglePendGlass); pnlPendGlass.setManaged(n == togglePendGlass);
            pnlPendPul.setVisible(n == togglePendPul);     pnlPendPul.setManaged(n == togglePendPul);
            if (n == togglePendRep)        { misPendientesController.setFiltroImei(filtro);      misPendientesController.cargar(); }
            else if (n == togglePendGlass) { misPendientesGlassController.setFiltroImei(filtro); misPendientesGlassController.cargar(); }
            else                           { pulidoTecnicoController.setFiltroImei(filtro);      pulidoTecnicoController.cargar(); }
        });

        misPendientesController.cargar();
        misPendientesGlassController.cargar();   // para el badge y toggles (suma rep + glass + pulido)
        pulidoTecnicoController.cargar();

        agrupadoController.configurar(AgrupadoController.Rol.TECNICO);

        com.reparaciones.utils.Poller.programarSiguiente(poller, this::recargar);
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> recargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
        actualizarBadges();
        updateBadgeStyle(lblBadgePendientes, true);
    }

    /** Detiene el poller periódico al salir de la vista. */
    @Override
    public void detenerPolling() { poller.shutdownNow(); }

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

    /** Entra en modo PLANO: todas las reparaciones propias sin agrupar, columnas estilo detalle,
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

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    /**
     * Recarga la sección visible: mis pendientes o historial.
     * Invocado por {@link MainController} cuando la ventana recupera el foco.
     */
    @Override
    public void recargar() {
        if (pnlMisPendientes.isVisible()) {
            if (togglePendPul.isSelected())        pulidoTecnicoController.cargar();
            else if (togglePendGlass.isSelected()) misPendientesGlassController.cargar();
            else                                   misPendientesController.cargar();
        } else if (pnlAgrupado.isVisible()) {
            agrupadoController.cargar();
        } else {
            if (toggleHistPul.isSelected()) historialPulidoController.cargar();
            else                            cargarDatos();
        }
        // Badge data siempre fresco (rep + glass + pulido), aunque su pestaña no esté visible
        if (!pnlMisPendientes.isVisible() || !togglePendRep.isSelected())   misPendientesController.cargar();
        if (!pnlMisPendientes.isVisible() || !togglePendGlass.isSelected()) misPendientesGlassController.cargar();
        if (!pnlMisPendientes.isVisible() || !togglePendPul.isSelected())   pulidoTecnicoController.cargar();
        actualizarBadges();
    }

    private void actualizarBadges() {
        setBadge(lblBadgePendientes,
                misPendientesController.getTotalItems() + misPendientesGlassController.getTotalItems()
                        + pulidoTecnicoController.getTotalItems());
        togglePendRep.setText(conteoPill("Reparaciones", misPendientesController.getTotalItems()));
        togglePendGlass.setText(conteoPill("Glass", misPendientesGlassController.getTotalItems()));
        togglePendPul.setText(conteoPill("Pulidos", pulidoTecnicoController.getTotalItems()));
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

    @FXML private void mostrarHistorial() {
        mostrarPanel(pnlHistorial, btnTabHistorial);
        cargarDatos();
    }

    @FXML private void mostrarAgrupado() {
        mostrarPanel(pnlAgrupado, btnTabAgrupado);
        agrupadoController.cargar();
    }

    public void irAInicio() { mostrarPanel(pnlMisPendientes, btnTabMisPendientes); }

    private void mostrarPanel(VBox panel, Button btnActivo) {
        if (pnlAgrupado.isVisible() && panel != pnlAgrupado)
            agrupadoController.resetarModo();
        pnlHistorial    .setVisible(false); pnlHistorial    .setManaged(false);
        pnlMisPendientes.setVisible(false); pnlMisPendientes.setManaged(false);
        pnlAgrupado     .setVisible(false); pnlAgrupado     .setManaged(false);
        panel.setVisible(true); panel.setManaged(true);
        for (Button b : new Button[]{btnTabHistorial, btnTabMisPendientes, btnTabAgrupado}) {
            b.getStyleClass().removeAll("stock-sidebar-btn-active", "stock-sidebar-btn");
            b.getStyleClass().add(b == btnActivo ? "stock-sidebar-btn-active" : "stock-sidebar-btn");
        }
        updateBadgeStyle(lblBadgePendientes, btnActivo == btnTabMisPendientes);
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

        colImei.setCellFactory(col -> new TableCell<>() {
            private final Label lblDetalle = new Label();
            private final javafx.beans.value.ChangeListener<Boolean> selListenerDetalle =
                (obs, o, sel) -> lblDetalle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
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
                if (row instanceof ReparacionResumen rep) {
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
            if (o instanceof ReparacionResumen rep) m = rep.getModelo();
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
                if (row instanceof ReparacionResumen rep) {
                    lblInicio.setText(rep.getFechaAsig() != null ? FechaUtils.formatear(rep.getFechaAsig(), FORMATO_FECHA) : "—");
                    lblFin   .setText("→ " + (rep.getFechaFin() != null ? FechaUtils.formatear(rep.getFechaFin(), FORMATO_FECHA) : "—"));
                    actualizarColores(selected);
                    setGraphic(box);
                } else {
                    setGraphic(null);
                }
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
                if (empty) return;
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
                selectedProperty().addListener((obs, wasSelected, isSelected) -> aplicarEstilo(getItem(), isEmpty()));
            }

            private void aplicarEstilo(Object item, boolean empty) {
                if (empty || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
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
                if (!empty && item instanceof ReparacionResumen rep) {
                    setOpacity(idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0);
                } else {
                    setOpacity(1.0);
                }
            }
        });
    }

    private void adaptarFiltrosDetalle() {
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

    private String textoDeCelda(Object item, TableColumn<?, ?> col) {
        if (!(item instanceof ReparacionResumen rep)) return null;
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
            String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
            if (!can.equals(n)) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                return;
            }
            switch (com.reparaciones.utils.FiltroImei.estado(n)) {
                case VACIO      -> filtroImei.setStyle("");
                case INCOMPLETO -> filtroImei.setStyle("-fx-background-color: #F3F3F3; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
                case VALIDO     -> filtroImei.setStyle("-fx-background-color: #F3F3F3; -fx-border-color: #8AC7AF;" +
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
        filtroCliHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            filtroCliente, clientes,
            java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli);
                                else         clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);
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

    private void actualizarTextoFiltroCliente() {
        long sel = clientesFiltro.size();
        if (sel == 0)      etiquetaCli.set("Cliente");
        else if (sel == 1) etiquetaCli.set(clientesFiltro.iterator().next());
        else               etiquetaCli.set(sel + " clientes");
    }

    private void aplicarFiltros() {
        if (cbIncidenciasAbiertas == null) return;
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        boolean filtrarAbiertas = cbIncidenciasAbiertas.isSelected();
        boolean filtrarCerradas = cbIncidenciasCerradas.isSelected();
        boolean filtrarNormales = cbNormales.isSelected();

        Integer idTec = Sesion.getIdTec();
        java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(filtroImei.getText().trim());

        java.util.function.Predicate<ReparacionResumen> predicado = rep -> {
            if (idTec == null || rep.getIdTec() != idTec) return false;
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
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
            if (!piezasFiltro.isEmpty()) {
                String cat = com.reparaciones.utils.Piezas.categoria(rep.getTipoComponente());
                if (!piezasFiltro.contains(cat)) return false;
            }
            return true;
        };

        List<ReparacionResumen> filtradas = datos.stream()
            .filter(predicado)
            .collect(Collectors.toList());
        tablaItems.setAll(filtradas);
        lblContadorPlano.setText(filtradas.size() + " reparaci" + (filtradas.size() == 1 ? "ón" : "ones"));
        lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
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
        mostrarHistorial();
        filtroFechaDesde.setValue(desde);
        filtroFechaHasta.setValue(hasta);
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
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

    @FXML
    private void abrirModalPendientes() {
        mostrarPanel(pnlMisPendientes, btnTabMisPendientes);
        if (togglePendPul.isSelected())        pulidoTecnicoController.cargar();
        else if (togglePendGlass.isSelected()) misPendientesGlassController.cargar();
        else                                   misPendientesController.cargar();
    }

    @FXML
    private void descargarHistorial() {
        exportarCSV((Stage) tablaReparaciones.getScene().getWindow());
    }

    @Override
    public void exportarCSV(Stage owner) {
        if (pnlAgrupado.isVisible()) { agrupadoController.exportarCSV(owner); return; }

        if (pnlMisPendientes.isVisible()) {
            if (togglePendPul.isSelected()) {
                exportarPulidosPendientes(owner, pulidoTecnicoController.getItemsVisibles(), false);
            } else {
                List<ReparacionResumen> items = (togglePendGlass.isSelected()
                        ? misPendientesGlassController : misPendientesController).getItemsVisibles();
                List<String> cabeceras = List.of(
                        "ID Reparación", "IMEI", "Fecha asig.", "Fecha fin",
                        "Componente", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
                List<List<String>> filas = new ArrayList<>();
                DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                for (ReparacionResumen r : items) {
                    filas.add(List.of(
                            r.getIdRep(),
                            com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()),
                            FechaUtils.formatear(r.getFechaAsig(), fmtHora),
                            FechaUtils.formatear(r.getFechaFin(), fmtHora),
                            r.getTipoComponente() != null ? r.getTipoComponente() : "",
                            r.getObservaciones()  != null ? r.getObservaciones()  : "",
                            r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No",
                            r.isEsResuelto() ? "Sí" : "No",
                            r.getIdRepAnterior() != null ? r.getIdRepAnterior() : ""
                    ));
                }
                com.reparaciones.utils.CsvExporter.exportar(owner, "mis_pendientes", cabeceras, filas);
            }
            return;
        }

        if (toggleHistPul.isSelected()) {
            exportarHistorialPulidos(owner, historialPulidoController.getItemsVisibles(), false);
            return;
        }

        List<ReparacionResumen> items = tablaItems.stream()
                .filter(o -> o instanceof ReparacionResumen)
                .map(o -> (ReparacionResumen) o)
                .collect(Collectors.toList());
        List<String> cabeceras = List.of(
                "ID Reparación", "IMEI", "Fecha asig.", "Fecha fin",
                "Componente", "Reutilizado", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (ReparacionResumen r : items) {
            filas.add(List.of(
                    r.getIdRep(),
                    com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()),
                    FechaUtils.formatear(r.getFechaAsig(), fmtHora),
                    FechaUtils.formatear(r.getFechaFin(), fmtHora),
                    r.getTipoComponente() != null ? r.getTipoComponente() : "",
                    r.isEsReutilizado() ? "Sí" : "No",
                    r.getObservaciones()  != null ? r.getObservaciones()  : "",
                    r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No",
                    r.isEsResuelto() ? "Sí" : "No",
                    r.getIdRepAnterior() != null ? r.getIdRepAnterior() : ""
            ));
        }
        com.reparaciones.utils.CsvExporter.exportar(owner,
                toggleHistGlass.isSelected() ? "mis_glass" : "mis_reparaciones", cabeceras, filas);
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
