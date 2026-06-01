package com.reparaciones.controllers;

import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.models.GrupoImei;
import com.reparaciones.models.ReparacionResumen;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    @FXML private TableColumn<Object, String> colFecha;
    @FXML private TableColumn<Object, String> colComponente;
    @FXML private TableColumn<Object, String> colObservaciones;
    @FXML private TableColumn<Object, Void>   colEstado;
    @FXML private TableColumn<Object, Void>   colIncidencia;
    @FXML private TableColumn<Object, String> colIdAnterior;
    @FXML private TextField  filtroImei;
    @FXML private Label      lblUltimaActualizacion;
    @FXML private MenuButton filtroTecnico;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    @FXML private javafx.scene.control.ToggleButton toggleHistRep;
    @FXML private javafx.scene.control.ToggleButton toggleHistPul;
    @FXML private VBox pnlHistRep;
    @FXML private VBox pnlHistPul;
    @FXML private HistorialPulidoController historialPulidoController;

    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private List<ReparacionResumen> datosFiltrados = new ArrayList<>();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();

    // ── Drill-down ────────────────────────────────────────────────────────────
    private enum Modo { MAESTRO, DETALLE }
    private Modo   modoActual  = Modo.MAESTRO;
    private String imeiDetalle = null;
    private HBox   barraNavegacion;
    private Label  lblNavImei;
    private Label  lblNavModelo;
    private Label  lblNavCount;

    private final List<CheckBox> checksTecnico = new java.util.ArrayList<>();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @FXML
    public void initialize() {
        tablaReparaciones.setColumnResizePolicy(param -> true);
        tablaReparaciones.setFixedCellSize(44);

        configurarColumnas();
        tablaReparaciones.getColumns().forEach(c -> c.setReorderable(false));
        configurarFilas();
        configurarFiltros();

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

        crearBarraNavegacion();
        tablaReparaciones.setItems(tablaItems);
        colIdRep.setVisible(false); colReparador.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false);
        colIdAnterior.setVisible(false);
        colComponente.setText("Reparaciones");
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });

        cargarDatos();
    }

    @Override
    public void detenerPolling() { /* sin polling */ }

    @Override
    public void recargar() {
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

        Image imgHistorial = new Image(getClass().getResourceAsStream("/images/Historial.png"));
        colImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final ImageView ivHist = new ImageView(imgHistorial);
            private final HBox contenedor = new HBox(6, lbl, ivHist);
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> lbl.setStyle(lbl.getUserData() + "-fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
                ivHist.setFitWidth(25); ivHist.setFitHeight(25);
                ivHist.setPreserveRatio(true); ivHist.setStyle("-fx-cursor: hand;");
                contenedor.setAlignment(Pos.CENTER_LEFT);
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
                if (row instanceof GrupoImei grupo) {
                    String base = "-fx-font-size: 12px; -fx-font-weight: bold; ";
                    lbl.setUserData(base);
                    lbl.setText(grupo.getImei());
                    lbl.setStyle(base + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    ivHist.setVisible(true); ivHist.setManaged(true);
                    ivHist.setOnMouseClicked(e -> { e.consume(); mostrarDetalle(grupo); });
                    setGraphic(contenedor);
                } else if (row instanceof ReparacionResumen rep) {
                    String base = "-fx-font-size: 12px; ";
                    lbl.setUserData(base);
                    lbl.setText(rep.getImei());
                    lbl.setStyle(base + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
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

        colReparador.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setText(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                setText(row instanceof ReparacionResumen rep ? rep.getNombreTecnico() : null);
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
                    lblInicio.setText(grupo.getFechaMasAntigua()  != null ? grupo.getFechaMasAntigua().format(FORMATO_FECHA)  : "—");
                    lblFin.setText("→ " + (grupo.getFechaMasReciente() != null ? grupo.getFechaMasReciente().format(FORMATO_FECHA) : "—"));
                } else if (row instanceof ReparacionResumen rep) {
                    lblInicio.setText(rep.getFechaAsig() != null ? rep.getFechaAsig().format(FORMATO_FECHA) : "—");
                    lblFin.setText("→ " + (rep.getFechaFin() != null ? rep.getFechaFin().format(FORMATO_FECHA) : "—"));
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
                if (row instanceof GrupoImei grupo) setText(grupo.getReparaciones().size() + " reparaciones");
                else if (row instanceof ReparacionResumen rep) setText(rep.getTipoComponente());
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
                if (row instanceof GrupoImei grupo) {
                    if (grupo.getCountIncAbiertas() > 0) {
                        badge.setText(grupo.getCountIncAbiertas() + " incidencia" + (grupo.getCountIncAbiertas() > 1 ? "s" : ""));
                        badge.setStyle(base + "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BG + "; -fx-text-fill: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                        setGraphic(badge);
                    } else { setGraphic(null); }
                } else if (row instanceof ReparacionResumen rep) {
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

                // Drill-down al clicar en fila de grupo
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
                if (item instanceof GrupoImei) {
                    setStyle(isSelected()
                        ? "-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + "; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent; -fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;"
                        : "-fx-background-color: #EEF0F5; -fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " #2C3B54; -fx-cursor: hand;");
                } else if (item instanceof ReparacionResumen rep) {
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
            }
        });
    }

    private String textoDeCelda(Object row, TableColumn<?, ?> col) {
        if (row instanceof GrupoImei g) {
            if (col == colImei)       return g.getImei();
            if (col == colModelo)     { String m = g.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
            if (col == colFecha)      return (g.getFechaMasAntigua() != null ? g.getFechaMasAntigua().format(FORMATO_FECHA) : "—")
                                            + " → " + (g.getFechaMasReciente() != null ? g.getFechaMasReciente().format(FORMATO_FECHA) : "—");
            if (col == colComponente) return g.getReparaciones().size() + " reparaciones";
            if (col == colEstado)     return g.getCountIncAbiertas() > 0 ? g.getCountIncAbiertas() + " incidencia" + (g.getCountIncAbiertas() > 1 ? "s" : "") : "";
            return null;
        }
        if (!(row instanceof ReparacionResumen rep)) return null;
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
            datos.setAll(reparacionDAO.getReparacionesResumen());
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
            List<com.reparaciones.models.Tecnico> tecnicos = tecnicoDAO.getAll();
            for (com.reparaciones.models.Tecnico t : tecnicos) {
                CheckBox cb = new CheckBox(t.getNombre());
                cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
                cb.selectedProperty().addListener((obs, o, n) -> { actualizarTextoFiltroTecnico(); aplicarFiltros(); });
                checksTecnico.add(cb);
                filtroTecnico.getItems().add(new CustomMenuItem(cb, false));
            }
        } catch (SQLException e) { mostrarError(e); }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) filtroImei.setText(n.replaceAll("[^\\d]", ""));
            if (filtroImei.getText().length() > 15) filtroImei.setText(filtroImei.getText().substring(0, 15));
            String val = filtroImei.getText();
            if (val.isEmpty()) filtroImei.setStyle("");
            else if (val.length() < 15)
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
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
        filtroIncidencias.getItems().addAll(
            new CustomMenuItem(cbIncidenciasAbiertas, false),
            new CustomMenuItem(cbIncidenciasCerradas, false),
            new CustomMenuItem(cbNormales, false));
    }

    private void aplicarFiltros() {
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        boolean filtrarAbiertas = cbIncidenciasAbiertas.isSelected();
        boolean filtrarCerradas = cbIncidenciasCerradas.isSelected();
        boolean filtrarNormales = cbNormales.isSelected();
        List<String> tecnicosSeleccionados = checksTecnico.stream()
                .filter(CheckBox::isSelected).map(CheckBox::getText).collect(Collectors.toList());

        if (modoActual == Modo.DETALLE) {
            List<ReparacionResumen> filtradas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(rep -> {
                    if (!tecnicosSeleccionados.isEmpty() && !tecnicosSeleccionados.contains(rep.getNombreTecnico())) return false;
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
            lblNavCount.setText("  •  " + filtradas.size() + " reparaci" + (filtradas.size() == 1 ? "ón" : "ones"));
            return;
        }

        String imeiStr = filtroImei.getText().trim();
        datosFiltrados = datos.stream().filter(rep -> {
            if (imeiStr.length() == 15 && !rep.getImei().equals(imeiStr)) return false;
            if (!tecnicosSeleccionados.isEmpty() && !tecnicosSeleccionados.contains(rep.getNombreTecnico())) return false;
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
        buildTablaItems();
    }

    private void buildTablaItems() {
        LinkedHashMap<String, List<ReparacionResumen>> porImei = new LinkedHashMap<>();
        for (ReparacionResumen rep : datosFiltrados)
            porImei.computeIfAbsent(rep.getImei(), k -> new ArrayList<>()).add(rep);
        tablaItems.clear();
        for (Map.Entry<String, List<ReparacionResumen>> e : porImei.entrySet())
            tablaItems.add(new GrupoImei(e.getKey(), e.getValue()));
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
        pnlHistRep.getChildren().add(1, barraNavegacion);
    }

    private void mostrarDetalle(GrupoImei grupo) { mostrarDetalleParaImei(grupo.getImei()); }

    private void mostrarDetalleParaImei(String imei) {
        modoActual  = Modo.DETALLE;
        imeiDetalle = imei;

        String modelo = datos.stream().filter(r -> r.getImei().equals(imei))
                .map(ReparacionResumen::getModelo)
                .filter(m -> m != null && !m.isEmpty()).findFirst().orElse("");
        lblNavImei  .setText("IMEI: " + imei);
        lblNavModelo.setText(!modelo.isEmpty() ? "  •  " + FormularioReparacionController.traducirModelo(modelo) : "");

        filtroImei     .setVisible(false); filtroImei     .setManaged(false);
        barraNavegacion.setVisible(true);  barraNavegacion.setManaged(true);
        colIdRep.setVisible(true); colReparador.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true);
        colIdAnterior.setVisible(true);
        colComponente.setText("Componente");
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
        buildTablaItems();
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
        javafx.application.Platform.runLater(() -> {
            tablaReparaciones.setColumnResizePolicy(param -> true);
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });
    }

    // ─── Helpers de filtro ────────────────────────────────────────────────────

    private void actualizarTextoFiltroIncidencias() {
        boolean a = cbIncidenciasAbiertas.isSelected(), c = cbIncidenciasCerradas.isSelected(), n = cbNormales.isSelected();
        long total = java.util.stream.Stream.of(a, c, n).filter(Boolean::booleanValue).count();
        if      (total == 0) filtroIncidencias.setText("Incidencias");
        else if (total == 3) filtroIncidencias.setText("Todas");
        else if (total == 1) filtroIncidencias.setText(a ? "Abiertas" : c ? "Cerradas" : "Sin incidencia");
        else                 filtroIncidencias.setText(total + " filtros");
    }

    private void actualizarTextoFiltroTecnico() {
        List<String> sel = checksTecnico.stream().filter(CheckBox::isSelected).map(CheckBox::getText).collect(Collectors.toList());
        if (sel.isEmpty())        filtroTecnico.setText("Técnico");
        else if (sel.size() == 1) filtroTecnico.setText(sel.get(0));
        else                      filtroTecnico.setText(sel.size() + " técnicos");
    }

    public void setFiltroInicial(java.time.LocalDate desde, java.time.LocalDate hasta, String tecnico) {
        if (modoActual == Modo.DETALLE) volverAGrupos();
        if (tecnico != null) {
            checksTecnico.forEach(cb -> cb.setSelected(cb.getText().equals(tecnico)));
            actualizarTextoFiltroTecnico();
        }
        filtroFechaDesde.setValue(desde);
        filtroFechaHasta.setValue(hasta);
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear(); filtroImei.setStyle("");
        checksTecnico.forEach(cb -> cb.setSelected(false));
        filtroTecnico.setText("Técnico");
        filtroFechaDesde.setValue(null); filtroFechaHasta.setValue(null);
        cbIncidenciasAbiertas.setSelected(false);
        cbIncidenciasCerradas.setSelected(false);
        cbNormales.setSelected(false);
        filtroIncidencias.setText("Incidencias");
    }

    @Override
    public void exportarCSV(Stage owner) {
        List<ReparacionResumen> items = modoActual == Modo.DETALLE
                ? tablaItems.stream().filter(o -> o instanceof ReparacionResumen).map(o -> (ReparacionResumen) o).collect(Collectors.toList())
                : new ArrayList<>(datosFiltrados);

        List<String> cabeceras = List.of("ID Reparación", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                "Componente", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (ReparacionResumen r : items) {
            filas.add(List.of(
                    r.getIdRep(),
                    com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()),
                    r.getNombreTecnico() != null ? r.getNombreTecnico() : "",
                    r.getFechaAsig() != null ? r.getFechaAsig().format(fmt) : "",
                    r.getFechaFin()  != null ? r.getFechaFin().format(fmt)  : "",
                    r.getTipoComponente() != null ? r.getTipoComponente() : "",
                    r.getObservaciones()  != null ? r.getObservaciones()  : "",
                    r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No",
                    r.isEsResuelto() ? "Sí" : "No",
                    r.getIdRepAnterior() != null ? r.getIdRepAnterior() : ""
            ));
        }
        com.reparaciones.utils.CsvExporter.exportar(owner, "historial_reparaciones", cabeceras, filas);
    }

    private void mostrarError(Exception e) { Alertas.mostrarError(e.getMessage()); }
}
