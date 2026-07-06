package com.reparaciones.controllers;

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
 * Controlador de la vista de reparaciones para el rol ADMIN.
 * <p>El historial opera en dos modos:</p>
 * <ul>
 *   <li><b>Maestro</b>: una fila por IMEI con datos de resumen. Solo lectura.</li>
 *   <li><b>Detalle</b>: muestra todas las reparaciones del IMEI seleccionado. Barra de navegación para volver.</li>
 * </ul>
 *
 * @role ADMIN
 */
public class ReparacionControllerAdmin implements com.reparaciones.utils.Recargable, com.reparaciones.utils.Exportable {

    @FXML private TableView<Object>           tablaReparaciones;
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
    @FXML private Label      lblUltimaActualizacion;
    @FXML private javafx.scene.control.Label        lblContadorPlano;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroCliente;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistGlass;
    @FXML private javafx.scene.control.ToggleButton toggleHistPul;
    @FXML private VBox pnlHistRep;
    @FXML private VBox pnlHistPul;
    @FXML private HistorialPulidoController historialPulidoController;

    @FXML private javafx.scene.control.Button btnTabAsignaciones;
    @FXML private javafx.scene.control.Button btnTabHistorial;
    @FXML private javafx.scene.control.Button btnTabAgrupado;
    @FXML private VBox pnlHistorial;
    @FXML private VBox pnlAsignaciones;
    @FXML private VBox pnlAgrupado;
    @FXML private AgrupadoController agrupadoController;
    @FXML private PendientesSuperTecnicoController pendientesSuperTecnicoController;

    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;
    private CustomMenuItem itemCerradas;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final com.reparaciones.dao.GlassDAO glassDAO = new com.reparaciones.dao.GlassDAO();
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

        tablaReparaciones.setItems(tablaItems);
        entrarModoPlano();   // el Historial es siempre plano; el agrupado vive en su apartado

        cargarDatos();
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> recargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }

        // Modo solo lectura para el admin en la tabla unificada de asignaciones
        pendientesSuperTecnicoController.setSoloLectura(true);

        agrupadoController.configurar(AgrupadoController.Rol.ADMIN);
    }

    @FXML
    private void mostrarHistorial() {
        if (pnlAgrupado.isVisible()) agrupadoController.resetarModo();
        pnlHistorial.setVisible(true);   pnlHistorial.setManaged(true);
        pnlAsignaciones.setVisible(false); pnlAsignaciones.setManaged(false);
        pnlAgrupado.setVisible(false);   pnlAgrupado.setManaged(false);
        estiloSidebar(btnTabHistorial);
    }

    @FXML
    private void mostrarAsignaciones() {
        if (pnlAgrupado.isVisible()) agrupadoController.resetarModo();
        pnlAsignaciones.setVisible(true);  pnlAsignaciones.setManaged(true);
        pnlHistorial.setVisible(false);    pnlHistorial.setManaged(false);
        pnlAgrupado.setVisible(false);     pnlAgrupado.setManaged(false);
        estiloSidebar(btnTabAsignaciones);
        pendientesSuperTecnicoController.cargar();
    }

    @FXML
    private void mostrarAgrupado() {
        if (pnlAgrupado.isVisible() && agrupadoController.enDetalle()) {
            agrupadoController.volverAlMaestro();
            return;
        }
        pnlAgrupado.setVisible(true);      pnlAgrupado.setManaged(true);
        pnlHistorial.setVisible(false);    pnlHistorial.setManaged(false);
        pnlAsignaciones.setVisible(false); pnlAsignaciones.setManaged(false);
        estiloSidebar(btnTabAgrupado);
        agrupadoController.cargar();
    }

    private void estiloSidebar(javafx.scene.control.Button activo) {
        for (javafx.scene.control.Button b : new javafx.scene.control.Button[]{btnTabAsignaciones, btnTabHistorial, btnTabAgrupado})
            b.getStyleClass().setAll(b == activo ? "stock-sidebar-btn-active" : "stock-sidebar-btn");
    }

    @Override
    public void detenerPolling() { /* sin polling */ }

    @Override
    public void recargar() {
        if (pnlAgrupado.isVisible()) { agrupadoController.cargar(); return; }
        if (toggleHistPul.isSelected()) historialPulidoController.cargar();
        else                            cargarDatos();
    }

    public void irAInicio() { /* historial siempre visible */ }

    // ─── Label expandible ────────────────────────────────────────────────────

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
                super.updateItem(item, empty); setGraphic(null);
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep) {
                    String base = "-fx-font-size: 12px; ";
                    lbl.setUserData(base);
                    lbl.setText(rep.getImei());
                    lbl.setStyle(base + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
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
                super.updateItem(item, empty); setGraphic(null);
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
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setText(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep) setText(rep.getTipoComponente());
                else setText(null);
            }
        });

        colObservaciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                setGraphic(row instanceof ReparacionResumen rep ? labelExpandible("Observaciones", rep.getObservaciones()) : null);
            }
        });

        colIdAnterior.setCellFactory(col -> new TableCell<>() {
            private final Label lblLink = new Label();
            {
                lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand;");
                lblLink.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                lblLink.setMaxWidth(Double.MAX_VALUE);
                lblLink.setOnMouseEntered(e -> lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: true;"));
                lblLink.setOnMouseExited(e -> lblLink.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: false;"));
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
                    lblLink.setText(rep.getIdRepAnterior()); setGraphic(lblLink);
                } else { setGraphic(null); }
            }
        });

        configurarColEstado();
        configurarColIncidencia();
    }

    private void configurarColEstado() {
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold;"); }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold;";
                if (row instanceof ReparacionResumen rep) {
                    if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                        badge.setText("Incidencia");
                        badge.setStyle(base + "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + "; -fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                    } else if (rep.isEsIncidencia()) {
                        badge.setText("Resuelta");
                        badge.setStyle(base + "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + "; -fx-text-fill: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";");
                    } else {
                        badge.setText("Normal");
                        badge.setStyle(base + "-fx-background-color: #E8EAF0; -fx-text-fill: #586376;");
                    }
                    setGraphic(badge);
                } else { setGraphic(null); }
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
                        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (rep.isEsResuelto() ? "#A9A9A9" : "#000000") + ";" + (!texto.isEmpty() ? " -fx-cursor: hand;" : ""));
                        lblComentario.setOnMouseClicked(texto.isEmpty() ? null :
                                e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto("Incidencia", texto); });
                        setStyle(rep.isEsResuelto() ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + ";" : "");
                        setGraphic(lblComentario);
                    } else { setStyle(""); setGraphic(lblSin); }
                } else { setStyle(""); setGraphic(null); }
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
                                else cell.setStyle(String.format(java.util.Locale.US, "-fx-background-color: rgba(224,247,250,%.2f);", a));
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
                if (item instanceof ReparacionResumen rep) {
                    if (isSelected()) {
                        setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + "; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent; -fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;");
                    } else if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                        setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                    } else if (rep.isEsIncidencia()) {
                        setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_REPARADO_BRD + ";");
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
        } catch (SQLException e) { mostrarError(e); }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
            if (!can.equals(n)) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                return;
            }
            switch (com.reparaciones.utils.FiltroImei.estado(n)) {
                case VACIO      -> filtroImei.setStyle("");
                case INCOMPLETO -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
                case VALIDO     -> filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            }
            aplicarFiltros();
        });
        filtroFechaDesde.getEditor().setDisable(true); filtroFechaDesde.getEditor().setOpacity(1.0);
        filtroFechaHasta.getEditor().setDisable(true); filtroFechaHasta.getEditor().setOpacity(1.0);
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
                    if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto()) mostrar = true;
                    if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto()) mostrar = true;
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
        boolean a = cbIncidenciasAbiertas.isSelected(), c = cbIncidenciasCerradas.isSelected(), n = cbNormales.isSelected();
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
        filtroImei.clear(); filtroImei.setStyle("");
        idsTecFiltro.clear();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        etiquetaTec.set("Técnico");
        clientesFiltro.clear();
        if (filtroCliHandle != null) filtroCliHandle.refresh();
        etiquetaCli.set("Cliente");
        piezasFiltro.clear();
        if (filtroPiezaHandle != null) filtroPiezaHandle.refresh();
        etiquetaPieza.set("Pieza");
        filtroFechaDesde.setValue(null); filtroFechaHasta.setValue(null);
        cbIncidenciasAbiertas.setSelected(false);
        cbIncidenciasCerradas.setSelected(false);
        cbNormales.setSelected(false);
        filtroIncidencias.setText("Incidencias");
        aplicarFiltros();
    }

    @Override
    public void exportarCSV(Stage owner) {
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (pnlAgrupado.isVisible()) { agrupadoController.exportarCSV(owner); return; }

        List<ReparacionResumen> items = tablaItems.stream()
                .filter(o -> o instanceof ReparacionResumen)
                .map(o -> (ReparacionResumen) o)
                .collect(Collectors.toList());
        List<String> cabeceras = List.of(
                "ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                "Componente", "Reutilizado", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        for (ReparacionResumen r : items) {
            List<String> fila = new ArrayList<>();
            fila.add(r.getIdRep());
            fila.add(com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()));
            fila.add(r.getNombreTecnico() != null ? r.getNombreTecnico() : "");
            fila.add(FechaUtils.formatear(r.getFechaAsig(), fmtHora));
            fila.add(FechaUtils.formatear(r.getFechaFin(), fmtHora));
            fila.add(r.getTipoComponente() != null ? r.getTipoComponente() : "");
            fila.add(r.isEsReutilizado() ? "Sí" : "No");
            fila.add(r.getObservaciones()  != null ? r.getObservaciones()  : "");
            fila.add(r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No");
            fila.add(r.isEsResuelto() ? "Sí" : "No");
            fila.add(r.getIdRepAnterior() != null ? r.getIdRepAnterior() : "");
            filas.add(fila);
        }
        com.reparaciones.utils.CsvExporter.exportar(owner,
                toggleHistGlass.isSelected() ? "historial_glass" : "historial_reparaciones", cabeceras, filas);
    }

    private void mostrarError(Exception e) { Alertas.mostrarError(e.getMessage()); }
}
