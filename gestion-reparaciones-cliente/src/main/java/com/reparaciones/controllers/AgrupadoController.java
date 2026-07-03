package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.dao.GlassDAO;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.ReparacionComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.models.GrupoImei;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.utils.FiltroImei;
import com.reparaciones.utils.MultiSelectComboBox;
import com.reparaciones.utils.MultiSelectDropdown;
import com.reparaciones.utils.SelectorClienteDialog;
import com.reparaciones.utils.TipoTrabajo;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Apartado "Agrupado por IMEI" — componente compartido por los tres roles (fx:include).
 * Vista maestro por IMEI (agrega Reparación + Glass + Pulido) con drill-down a detalle
 * cronológico. Parametrizado por {@link Rol}: el supertécnico ve la columna de revisión
 * logística y el menú de edición; admin y técnico son de solo lectura (el técnico solo
 * ve sus propios trabajos).
 */
public class AgrupadoController {

    /** Rol que hospeda el componente: define origen de datos y acciones disponibles. */
    public enum Rol { SUPERTECNICO, ADMIN, TECNICO }

    // ── FXML ────────────────────────────────────────────────────────────────
    @FXML private VBox raiz;
    @FXML private Label lblContador;
    @FXML private TextField filtroImei;
    @FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private MultiSelectComboBox<String>  filtroCliente;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    @FXML private TableView<Object> tabla;
    @FXML private TableColumn<Object, Void>   colTipo;
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

    // ── DAOs ────────────────────────────────────────────────────────────────
    private final ReparacionDAO           reparacionDAO           = new ReparacionDAO();
    private final GlassDAO                glassDAO                = new GlassDAO();
    private final PulidoDAO               pulidoDAO               = new PulidoDAO();
    private final TelefonoDAO             telefonoDAO             = new TelefonoDAO();
    private final ClienteDAO              clienteDAO              = new ClienteDAO();
    private final TecnicoDAO              tecnicoDAO              = new TecnicoDAO();
    private final ReparacionComponenteDAO reparacionComponenteDAO = new ReparacionComponenteDAO();

    // ── Config de rol ───────────────────────────────────────────────────────
    private Rol rol = Rol.SUPERTECNICO;
    private boolean esSuper = true;

    // ── Datos ───────────────────────────────────────────────────────────────
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private List<ReparacionResumen> datosFiltrados = new ArrayList<>();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();

    // ── Drill-down ──────────────────────────────────────────────────────────
    private enum Modo { MAESTRO, DETALLE }
    private Modo   modoActual  = Modo.MAESTRO;
    private String imeiDetalle = null;
    private HBox   barraNavegacion;
    private Label  lblNavImei;
    private Label  lblNavModelo;
    private Label  lblNavCount;

    // ── Filtros ─────────────────────────────────────────────────────────────
    private final Set<Integer>   idsTecFiltro  = new HashSet<>();
    private final Set<String>    idsAjenas     = new HashSet<>();
    private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
    private MultiSelectDropdown.Handle filtroTecHandle;
    private final List<Tecnico>  tecnicosLista = new ArrayList<>();

    private static final String SIN_CLIENTE = "(Sin cliente)";
    private final Set<String>    clientesFiltro = new HashSet<>();
    private final StringProperty etiquetaCli    = new SimpleStringProperty("Cliente");
    private MultiSelectDropdown.Handle filtroCliHandle;

    private CheckBox cbIncidenciasAbiertas;
    private CheckBox cbIncidenciasCerradas;
    private CheckBox cbNormales;
    private CustomMenuItem itemCerradas;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    // ── Ciclo de vida ───────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        tabla.setColumnResizePolicy(param -> true);
        tabla.setFixedCellSize(44);
        tabla.getColumns().forEach(c -> c.setSortable(false));
        tabla.getColumns().forEach(c -> c.setReorderable(false));

        configurarColumnas();
        configurarFilas();
        configurarFiltros();
        crearBarraNavegacion();
        tabla.setItems(tablaItems);
    }

    /** Configura el componente para el rol anfitrión. Debe llamarse tras cargar el FXML. */
    public void configurar(Rol rol) {
        this.rol     = rol;
        this.esSuper = (rol == Rol.SUPERTECNICO);
        resetarModo();
    }

    /** Recarga los datos (los 3 tipos) según el rol y reaplica los filtros. */
    public void cargar() {
        try {
            List<ReparacionResumen> merge = new ArrayList<>();
            if (rol == Rol.TECNICO) {
                Integer idTec = Sesion.getIdTec();
                if (idTec != null) {
                    merge.addAll(reparacionDAO.getReparacionesPorTecnico(idTec));
                    merge.addAll(glassDAO.getHistorialGlassPorTecnico(idTec));
                    merge.addAll(pulidoDAO.getHistorialPulidoPorTecnico(idTec));
                }
            } else {
                merge.addAll(reparacionDAO.getReparacionesResumen());
                merge.addAll(glassDAO.getHistorialGlass());
                merge.addAll(pulidoDAO.getHistorialPulido());
            }
            datos.setAll(merge);
            poblarFiltroCliente();
            aplicarFiltros();
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    /** Vuelve a modo maestro sin recargar (útil al ocultar el panel). */
    public void resetarModo() {
        modoActual  = Modo.MAESTRO;
        imeiDetalle = null;
        if (barraNavegacion != null) {
            barraNavegacion.setVisible(false); barraNavegacion.setManaged(false);
            filtroImei.setVisible(true); filtroImei.setManaged(true);
        }
        aplicarColumnasMaestro();
        adaptarFiltrosMaestro();
        javafx.application.Platform.runLater(() -> {
            colImei.setPrefWidth(180); colModelo.setPrefWidth(150);
            colFecha.setPrefWidth(130); colComponente.setPrefWidth(160); colEstado.setPrefWidth(130);
        });
    }

    /** Anchos del modo detalle, escalados al ancho de la tabla (mismo criterio que el Historial + columna Tipo). */
    private void aplicarAnchosDetalle() {
        double w = tabla.getWidth();
        if (w <= 0) return;
        double u = w / 1470.0;   // 1370 del Historial + ~100 de la columna Tipo nueva
        colTipo         .setPrefWidth(Math.max(100, 100 * u));
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

    // ── Columnas ────────────────────────────────────────────────────────────

    private void aplicarColumnasMaestro() {
        colTipo.setVisible(false);
        colIdRep.setVisible(false); colReparador.setVisible(false); colAsignadoPor.setVisible(false);
        colObservaciones.setVisible(false); colIncidencia.setVisible(false); colIdAnterior.setVisible(false);
        colObservacionTelefono.setVisible(true); colCliente.setVisible(true);
        colRevision.setVisible(true);   // visible para todos; solo el supertécnico puede editarla
        colComponente.setText("Trabajos");
    }

    private void aplicarColumnasDetalle() {
        colTipo.setVisible(true);
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true); colIdAnterior.setVisible(true);
        colObservacionTelefono.setVisible(false); colCliente.setVisible(false);
        colRevision.setVisible(false);
        colComponente.setText("Componente");
    }

    private void configurarColumnas() {
        colTipo.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof ReparacionResumen rep) {
                    TipoTrabajo tipo = TipoTrabajo.desde(rep.getIdRep());
                    badge.setText(tipo.etiqueta());
                    badge.setStyle(tipo.estiloBadge());
                    setGraphic(badge);
                } else {
                    setGraphic(null);
                }
            }
        });

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
                ivHist.setFitWidth(25); ivHist.setFitHeight(25); ivHist.setPreserveRatio(true);
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
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
            return new SimpleStringProperty((m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });

        colReparador.setCellValueFactory(d -> {
            Object o = d.getValue();
            if (o instanceof ReparacionResumen rep)
                return new SimpleStringProperty(rep.getNombreTecnico() != null ? rep.getNombreTecnico() : "");
            return new SimpleStringProperty("");
        });
        colReparador.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(empty || item == null || item.isEmpty() ? null : item);
            }
        });
        colAsignadoPor.setCellValueFactory(d -> {
            Object o = d.getValue();
            if (o instanceof ReparacionResumen rep)
                return new SimpleStringProperty(rep.getNombreTecnicoAsigna() != null ? rep.getNombreTecnicoAsigna() : "—");
            return new SimpleStringProperty("");
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
                    setText(grupo.getResumenTipos());
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

        colObservacionTelefono.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String obs = null;
                if (row instanceof GrupoImei grupo) obs = grupo.getObservacion();
                else if (row instanceof ReparacionResumen rep) obs = rep.getObservacionTelefono();
                setGraphic(labelExpandible("Observación", obs));
            }
        });

        colCliente.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String cli = null;
                if (row instanceof GrupoImei grupo) cli = grupo.getCliente();
                else if (row instanceof ReparacionResumen rep) cli = rep.getCliente();
                setGraphic(labelExpandible("Cliente", cli));
            }
        });

        colIdAnterior.setCellFactory(col -> new TableCell<>() {
            private final Label lblLink = new Label();
            {
                lblLink.setStyle("-fx-text-fill: " + Colores.TEXTO_ACCION + "; -fx-cursor: hand;");
                lblLink.setTextOverrun(OverrunStyle.ELLIPSIS);
                lblLink.setMaxWidth(Double.MAX_VALUE);
                lblLink.setOnMouseEntered(e -> lblLink.setStyle("-fx-text-fill: " + Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: true;"));
                lblLink.setOnMouseExited(e -> lblLink.setStyle("-fx-text-fill: " + Colores.TEXTO_ACCION + "; -fx-cursor: hand; -fx-underline: false;"));
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
                // El pulido guarda en ID_REP_ANTERIOR el id de su asignación (enlace interno P→AP),
                // no una reincidencia: no tiene sentido mostrarlo aquí.
                if (row instanceof ReparacionResumen rep && rep.getIdRepAnterior() != null
                        && TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.PULIDO) {
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
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold;";
                if (row instanceof GrupoImei grupo) {
                    if (grupo.getCountIncAbiertas() > 0) {
                        badge.setText("Incidencia");
                        badge.setStyle(base + "-fx-background-color: " + Colores.FILA_INCIDENCIA_BG + "; -fx-text-fill: " + Colores.FILA_INCIDENCIA_BRD + ";");
                    } else {
                        badge.setText("Normal");
                        badge.setStyle(base + "-fx-background-color: #E8EAF0; -fx-text-fill: #586376;");
                    }
                    setGraphic(badge);
                } else if (row instanceof ReparacionResumen rep) {
                    if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                        badge.setText("Incidencia");
                        badge.setStyle(base + "-fx-background-color: " + Colores.FILA_INCIDENCIA_BG + "; -fx-text-fill: " + Colores.FILA_INCIDENCIA_BRD + ";");
                    } else if (rep.isEsIncidencia()) {
                        badge.setText("Resuelta");
                        badge.setStyle(base + "-fx-background-color: " + Colores.FILA_REPARADO_BG + "; -fx-text-fill: " + Colores.FILA_REPARADO_ICO + ";");
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
                lblComentario.setTextOverrun(OverrunStyle.ELLIPSIS);
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
                        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";" + (!texto.isEmpty() ? " -fx-cursor: hand;" : ""));
                        lblComentario.setOnMouseClicked(texto.isEmpty() ? null :
                                e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ConfirmDialog.mostrarTexto("Incidencia", texto); });
                        setStyle(rep.isEsResuelto() ? "-fx-background-color: " + Colores.FILA_REPARADO_BG + ";" : "");
                        setGraphic(lblComentario);
                    } else {
                        setStyle(""); setGraphic(lblSin);
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
                toggle.setStyle("-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
                setAlignment(Pos.CENTER);
                toggle.setMouseTransparent(!esSuper);   // admin/técnico: solo lectura del estado
                toggle.setOnAction(e -> {
                    if (!esSuper) return;
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    Object row = getTableView().getItems().get(getIndex());
                    if (!(row instanceof GrupoImei grupo)) return;
                    if (grupo.isTieneAsignaciones()) { toggle.setSelected(false); return; }
                    boolean nuevoValor = toggle.isSelected();
                    boolean estadoAnterior = !nuevoValor;
                    new Thread(() -> {
                        try {
                            telefonoDAO.actualizarRevisionLogistica(grupo.getImei(), nuevoValor, grupo.getTelefonoUpdatedAt());
                            javafx.application.Platform.runLater(AgrupadoController.this::cargar);
                        } catch (com.reparaciones.utils.StaleDataException ex) {
                            javafx.application.Platform.runLater(() -> {
                                toggle.setSelected(estadoAnterior);
                                aplicarEstiloToggle(estadoAnterior);
                                new Alert(Alert.AlertType.WARNING, "El teléfono fue modificado por otro usuario. Se recargan los datos.").showAndWait();
                                cargar();
                            });
                        } catch (SQLException ex) {
                            javafx.application.Platform.runLater(() -> {
                                toggle.setSelected(estadoAnterior);
                                aplicarEstiloToggle(estadoAnterior);
                                new Alert(Alert.AlertType.ERROR, "Error al guardar: " + ex.getMessage()).showAndWait();
                            });
                        }
                    }).start();
                });
            }
            private void aplicarEstiloToggle(boolean on) {
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; ";
                if (on) {
                    toggle.setText("OK");
                    toggle.setStyle(base + "-fx-background-color: #2E7D32; -fx-text-fill: white;");
                } else {
                    toggle.setText("—");
                    toggle.setStyle(base + "-fx-background-color: #9E9E9E; -fx-text-fill: white;");
                }
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof GrupoImei grupo) {
                    boolean efectivo = grupo.isRevisionLogistica() && !grupo.isTieneAsignaciones();
                    toggle.setSelected(efectivo);
                    aplicarEstiloToggle(efectivo);
                    if (grupo.isTieneAsignaciones()) {
                        toggle.setStyle(toggle.getStyle().replace("-fx-cursor: hand;", "-fx-cursor: default;") + " -fx-opacity: 0.5;");
                    }
                    setGraphic(toggle);
                } else {
                    setGraphic(null);
                }
            }
        });
    }

    private void configurarFilas() {
        tabla.setRowFactory(tv -> new TableRow<>() {
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

                copiar.setOnAction(e -> {
                    Object rowItem = getItem();
                    if (rowItem == null || colRightClick[0] == null) return;
                    String texto = textoDeCelda(rowItem, colRightClick[0]);
                    if (texto == null || texto.isEmpty()) return;
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(texto);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                });
                editar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) FormularioReparacionController.abrirEditar(rep.getIdRep(), AgrupadoController.this::cargar); });
                borrar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarReparacion(rep); });
                aniadirInc .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) abrirDialogoIncidencia(rep); });
                cancelarInc.setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarIncidencia(rep); });
                editarObs  .setOnAction(e -> { if (getItem() instanceof GrupoImei grupo) abrirDialogoObservacionTelefono(grupo); });
                editarCli  .setOnAction(e -> {
                    if (!(getItem() instanceof GrupoImei grupo)) return;
                    try {
                        List<Cliente> activos = clienteDAO.getActivos();
                        Integer idActual = activos.stream()
                                .filter(c -> c.getNombre().equals(grupo.getCliente()))
                                .map(Cliente::getIdCli).findFirst().orElse(null);
                        java.util.Optional<Integer> sel = SelectorClienteDialog.elegir(activos, idActual);
                        if (sel.isEmpty()) return;
                        Integer idCli = (sel.get() == -1) ? null : sel.get();
                        telefonoDAO.actualizarCliente(grupo.getImei(), idCli, grupo.getTelefonoUpdatedAt());
                        cargar();
                    } catch (com.reparaciones.utils.StaleDataException ex) {
                        Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                        cargar();
                    } catch (SQLException ex) { mostrarError(ex); }
                });
                menu.getItems().addAll(editar, borrar, new SeparatorMenuItem(), copiar, new SeparatorMenuItem(),
                        aniadirInc, cancelarInc, new SeparatorMenuItem(), editarObs, new SeparatorMenuItem(), editarCli);
                menu.setOnShowing(e -> {
                    boolean esGrupo = getItem() instanceof GrupoImei;
                    if (!(getItem() instanceof ReparacionResumen rep)) {
                        editar.setVisible(false); borrar.setVisible(false);
                        aniadirInc.setVisible(false); cancelarInc.setVisible(false);
                        editarObs.setVisible(esSuper && esGrupo);
                        editarCli.setVisible(esSuper && esGrupo && modoActual == Modo.MAESTRO);
                        return;
                    }
                    boolean tieneInc = rep.isEsIncidencia() && !rep.isEsResuelto();
                    editar      .setVisible(esSuper && rep.getIdRep() != null && (rep.getIdRep().startsWith("R") || rep.getIdRep().startsWith("G")));
                    borrar      .setVisible(esSuper);
                    aniadirInc  .setVisible(esSuper && !rep.isEsIncidencia());
                    cancelarInc .setVisible(esSuper && tieneInc);
                    editarObs   .setVisible(false);
                    editarCli   .setVisible(false);
                });
                setContextMenu(menu);
                setOnContextMenuRequested(ev -> {
                    double x = ev.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });

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
                        setStyle("-fx-background-color: " + Colores.AZUL_MEDIO + ";" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SELECTED_BRD + " transparent;" +
                                "-fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;");
                    } else {
                        String brd = g.getCountIncAbiertas() > 0 ? Colores.FILA_INCIDENCIA_BRD : "#2C3B54";
                        setStyle("-fx-background-color: #EEF0F5;" +
                                "-fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SEP + " " + brd + ";" +
                                "-fx-cursor: hand;");
                    }
                } else if (item instanceof ReparacionResumen rep) {
                    if (isSelected()) {
                        setStyle("-fx-background-color: " + Colores.AZUL_MEDIO + ";" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SELECTED_BRD + " transparent;" +
                                "-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;");
                    } else if (rep.isEsIncidencia() && !rep.isEsResuelto()) {
                        setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SEP + " " + Colores.FILA_INCIDENCIA_BRD + ";");
                    } else if (rep.isEsIncidencia()) {
                        setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SEP + " " + Colores.FILA_REPARADO_BRD + ";");
                    } else {
                        setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + Colores.FILA_SEP + " transparent;");
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
            if (col == colComponente) return g.getResumenTipos();
            if (col == colObservacionTelefono) return g.getObservacion();
            if (col == colCliente)    return g.getCliente();
            return null;
        }
        if (!(row instanceof ReparacionResumen rep)) return null;
        if (col == colTipo)          return TipoTrabajo.desde(rep.getIdRep()).etiqueta();
        if (col == colIdRep)         return rep.getIdRep();
        if (col == colImei)          return rep.getImei();
        if (col == colModelo)        { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == colReparador)     return rep.getNombreTecnico();
        if (col == colAsignadoPor)   return rep.getNombreTecnicoAsigna();
        if (col == colFecha)         return FechaUtils.formatear(rep.getFechaFin(), FORMATO_FECHA);
        if (col == colComponente)    return rep.getTipoComponente();
        if (col == colObservaciones) return rep.getObservaciones();
        if (col == colIncidencia)    return rep.getIncidencia();
        if (col == colIdAnterior)    return rep.getIdRepAnterior();
        return null;
    }

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

    // ── Filtros ─────────────────────────────────────────────────────────────

    private void configurarFiltros() {
        try {
            tecnicosLista.addAll(tecnicoDAO.getAll());
            filtroTecHandle = MultiSelectDropdown.setup(
                filtroTecnico, tecnicosLista, Tecnico::getNombre,
                t -> idsTecFiltro.contains(t.getIdTec()),
                (t, checked) -> { if (checked) idsTecFiltro.add(t.getIdTec()); else idsTecFiltro.remove(t.getIdTec());
                                  actualizarTextoFiltroTecnico(); aplicarFiltros(); },
                etiquetaTec);
        } catch (SQLException e) {
            mostrarError(e);
        }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            String can = FiltroImei.canonicalizar(n);
            if (!can.equals(n)) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                return;
            }
            switch (FiltroImei.estado(n)) {
                case VACIO      -> filtroImei.setStyle("");
                case INCOMPLETO -> filtroImei.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-border-color: " + Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
                case VALIDO     -> filtroImei.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-border-color: " + Colores.FILA_REPARADO_ICO + ";" +
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

        filtroCliHandle = MultiSelectDropdown.setup(
            filtroCliente, new ArrayList<>(), java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli); else clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);
    }

    private void poblarFiltroCliente() {
        List<String> clientes = datos.stream()
            .map(r -> { String c = r.getCliente(); return (c == null || c.isEmpty()) ? SIN_CLIENTE : c; })
            .distinct().sorted().collect(Collectors.toList());
        if (!clientes.contains(SIN_CLIENTE) && datos.stream().anyMatch(r -> r.getCliente() == null || r.getCliente().isEmpty()))
            clientes.add(0, SIN_CLIENTE);
        filtroCliHandle = MultiSelectDropdown.setup(
            filtroCliente, clientes, java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli); else clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);
    }

    private void actualizarTextoFiltroTecnico() {
        long sel = idsTecFiltro.size();
        if (sel == 0) etiquetaTec.set("Técnico");
        else if (sel == 1) {
            int id = idsTecFiltro.iterator().next();
            etiquetaTec.set(tecnicosLista.stream().filter(t -> t.getIdTec() == id).findFirst().map(Tecnico::getNombre).orElse("Técnico"));
        } else etiquetaTec.set(sel + " técnicos");
    }

    private void actualizarTextoFiltroCliente() {
        long sel = clientesFiltro.size();
        if (sel == 0) etiquetaCli.set("Cliente");
        else if (sel == 1) etiquetaCli.set(clientesFiltro.iterator().next());
        else etiquetaCli.set(sel + " clientes");
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

    private void adaptarFiltrosMaestro() {
        filtroCliente.setVisible(true); filtroCliente.setManaged(true);
        actualizarTextoFiltroTecnico();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        cbIncidenciasAbiertas.setText("Incidencia");
        cbIncidenciasCerradas.setSelected(false);
        if (itemCerradas != null) itemCerradas.setVisible(false);
        cbNormales.setText("Normal");
        actualizarTextoFiltroIncidencias();
    }

    private void adaptarFiltrosDetalle() {
        filtroCliente.setVisible(false); filtroCliente.setManaged(false);
        cbIncidenciasAbiertas.setText("Abiertas");
        if (itemCerradas != null) itemCerradas.setVisible(true);
        cbNormales.setText("Sin incidencia");
        actualizarTextoFiltroIncidencias();
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
        filtroFechaDesde.setValue(null);
        filtroFechaHasta.setValue(null);
        cbIncidenciasAbiertas.setSelected(false);
        cbIncidenciasCerradas.setSelected(false);
        cbNormales.setSelected(false);
        filtroIncidencias.setText("Incidencias");
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        boolean filtrarAbiertas = cbIncidenciasAbiertas.isSelected();
        boolean filtrarCerradas = cbIncidenciasCerradas.isSelected();
        boolean filtrarNormales = cbNormales.isSelected();
        idsAjenas.clear();

        if (modoActual == Modo.DETALLE) {
            lblContador.setVisible(false); lblContador.setManaged(false);
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
                        if (filtrarNormales && !rep.isEsIncidencia())                       mostrar = true;
                        if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto()) mostrar = true;
                        if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto()) mostrar = true;
                        if (!mostrar) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(ReparacionResumen::getFechaAsig, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

            if (idsTecFiltro.isEmpty()) {
                tablaItems.setAll(todas);
                lblNavCount.setText("  •  " + todas.size() + " trabajo" + (todas.size() == 1 ? "" : "s"));
            } else {
                List<ReparacionResumen> delFiltro = todas.stream().filter(r -> idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                List<ReparacionResumen> otras = todas.stream().filter(r -> !idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                otras.forEach(r -> idsAjenas.add(r.getIdRep()));
                List<ReparacionResumen> resultado = new ArrayList<>(delFiltro);
                resultado.addAll(otras);
                tablaItems.setAll(resultado);
                int nO = otras.size();
                lblNavCount.setText("  •  " + delFiltro.size() + " de filtrados" + (nO > 0 ? " + " + nO + " de otros" : ""));
            }
            return;
        }

        String imeiStr = filtroImei.getText().trim();
        datosFiltrados = datos.stream().filter(rep -> {
            Set<String> imeisFiltro = FiltroImei.imeisValidos(imeiStr);
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
                boolean coincide = (sin && clientesFiltro.contains(SIN_CLIENTE)) || (!sin && clientesFiltro.contains(cli));
                if (!coincide) return false;
            }
            return true;
        }).collect(Collectors.toList());
        buildTablaItems();
        int nImeis = tablaItems.size();
        lblContador.setText(nImeis + (nImeis == 1 ? " IMEI" : " IMEIs"));
        lblContador.setVisible(true); lblContador.setManaged(true);
    }

    private void buildTablaItems() {
        LinkedHashMap<String, List<ReparacionResumen>> porImei = new LinkedHashMap<>();
        for (ReparacionResumen rep : datosFiltrados)
            porImei.computeIfAbsent(rep.getImei(), k -> new ArrayList<>()).add(rep);

        boolean filtrarInc    = cbIncidenciasAbiertas != null && cbIncidenciasAbiertas.isSelected();
        boolean filtrarNormal = cbNormales != null && cbNormales.isSelected();

        List<GrupoImei> grupos = new ArrayList<>();
        for (Map.Entry<String, List<ReparacionResumen>> e : porImei.entrySet()) {
            GrupoImei grupo = new GrupoImei(e.getKey(), e.getValue());
            if (!idsTecFiltro.isEmpty() && e.getValue().stream().noneMatch(r -> idsTecFiltro.contains(r.getIdTec())))
                continue;
            if (filtrarInc || filtrarNormal) {
                boolean tieneInc = grupo.getCountIncAbiertas() > 0;
                boolean ok = (filtrarInc && tieneInc) || (filtrarNormal && !tieneInc);
                if (!ok) continue;
            }
            grupos.add(grupo);
        }
        // Actividad más reciente arriba, cuente la categoría que cuente (rep, glass o pulido).
        // Sin esto, el orden era el del merge (todas las R primero) y un IMEI solo-glass caía al fondo.
        grupos.sort(Comparator.comparing(GrupoImei::getFechaMasReciente,
                Comparator.nullsLast(Comparator.reverseOrder())));
        tablaItems.setAll(grupos);
    }

    // ── Drill-down ──────────────────────────────────────────────────────────

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

        barraNavegacion = new HBox(12, btnVolver, new Separator(Orientation.VERTICAL), lblNavImei, lblNavModelo, lblNavCount);
        barraNavegacion.setAlignment(Pos.CENTER_LEFT);
        barraNavegacion.setPadding(new Insets(6, 0, 6, 0));
        barraNavegacion.setVisible(false);
        barraNavegacion.setManaged(false);

        // Insertar justo antes de la tabla
        int idxTabla = raiz.getChildren().indexOf(tabla);
        raiz.getChildren().add(idxTabla, barraNavegacion);
    }

    private void mostrarDetalle(GrupoImei grupo) {
        modoActual  = Modo.DETALLE;
        imeiDetalle = grupo.getImei();

        String modelo = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .map(ReparacionResumen::getModelo)
                .filter(m -> m != null && !m.isEmpty())
                .findFirst().orElse("");
        lblNavImei  .setText("IMEI: " + imeiDetalle);
        lblNavModelo.setText(!modelo.isEmpty() ? "  •  " + FormularioReparacionController.traducirModelo(modelo) : "");

        filtroImei.setVisible(false); filtroImei.setManaged(false);
        barraNavegacion.setVisible(true); barraNavegacion.setManaged(true);
        aplicarColumnasDetalle();
        adaptarFiltrosDetalle();
        javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> {
            aplicarAnchosDetalle();
            tabla.refresh();
        }));
        aplicarFiltros();
    }

    private void volverAGrupos() {
        resetarModo();
        aplicarFiltros();
    }

    // ── Diálogos (solo supertécnico) ────────────────────────────────────────

    private void abrirDialogoIncidencia(ReparacionResumen rep) {
        Label lblComentario = new Label("Comentario de incidencia");
        String textoInicial = rep.getIncidencia() != null ? rep.getIncidencia() : "";
        TextArea tfComentario = new TextArea(textoInicial);
        tfComentario.setPromptText("Describe la incidencia...");
        tfComentario.setWrapText(true);
        tfComentario.setPrefRowCount(4);
        tfComentario.setStyle("-fx-background-color: white; -fx-border-color: " + Colores.GRIS_BORDE + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");

        Label lblTecnico = new Label("Técnico asignado");
        ComboBox<Tecnico> cbTecnico = new ComboBox<>();
        cbTecnico.setMaxWidth(Double.MAX_VALUE);
        cbTecnico.setVisibleRowCount(8);
        cbTecnico.setStyle("-fx-background-color: white; -fx-border-color: " + Colores.GRIS_BORDE + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        try {
            List<Tecnico> tecnicos = tecnicoDAO.getAllActivos();
            cbTecnico.getItems().addAll(tecnicos);
            tecnicos.stream().filter(t -> t.getIdTec() == rep.getIdTec()).findFirst().ifPresent(cbTecnico::setValue);
        } catch (SQLException ex) { mostrarError(ex); }
        cbTecnico.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Tecnico t, boolean empty) { super.updateItem(t, empty); setText(empty || t == null ? null : t.getNombre()); }
        });
        cbTecnico.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Tecnico t, boolean empty) { super.updateItem(t, empty); setText(empty || t == null ? "Selecciona técnico" : t.getNombre()); }
        });

        Button btnConfirmar = new Button("Añadir incidencia y asignar");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        Runnable validar = () -> {
            boolean ok = !tfComentario.getText().trim().isEmpty() && cbTecnico.getValue() != null;
            btnConfirmar.setDisable(!ok);
            btnConfirmar.setStyle(ok
                    ? "-fx-background-color: " + Colores.FILA_REPARADO_ICO + "; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;"
                    : "-fx-background-color: " + Colores.GRIS_DISABLED + "; -fx-text-fill: " + Colores.GRIS_BORDE + "; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8;");
        };
        validar.run();
        tfComentario.textProperty().addListener((obs, o, n) -> validar.run());
        cbTecnico.valueProperty().addListener((obs, o, n) -> validar.run());

        VBox form = new VBox(8, lblComentario, tfComentario, lblTecnico, cbTecnico, btnConfirmar);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
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
                cargar();
            } catch (SQLException ex) {
                Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
            }
        });
        dialog.showAndWait();
    }

    private void abrirDialogoObservacionTelefono(GrupoImei grupo) {
        TextArea tfObs = new TextArea(grupo.getObservacion() != null ? grupo.getObservacion() : "");
        tfObs.setPromptText("Observación del teléfono...");
        tfObs.setWrapText(true);
        tfObs.setPrefRowCount(4);
        tfObs.setStyle("-fx-background-color: white; -fx-border-color: " + Colores.GRIS_BORDE + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");

        Button btnConfirmar = new Button("Guardar");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        btnConfirmar.setStyle("-fx-background-color: " + Colores.FILA_REPARADO_ICO + "; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;");

        VBox form = new VBox(8, new Label("Observación — IMEI " + grupo.getImei()), tfObs, btnConfirmar);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
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
                cargar();
            } catch (com.reparaciones.utils.StaleDataException ex) {
                Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                dialog.close();
                cargar();
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
                        cargar();
                    } catch (SQLException e) { mostrarError(e); }
                });
    }

    private void borrarReparacion(ReparacionResumen rep) {
        try {
            String ref = reparacionDAO.getReferenciadora(rep.getIdRep());
            if (ref != null) {
                Alert alerta = new Alert(Alert.AlertType.WARNING);
                alerta.setTitle("No se puede borrar");
                alerta.setHeaderText("Este trabajo está siendo referenciado");
                alerta.setContentText("La reparación " + ref + " apunta a esta. Bórrala primero.");
                alerta.showAndWait();
                return;
            }
            ConfirmDialog.mostrarConMotivo(
                    "Borrar trabajo",
                    "Se borrará " + rep.getIdRep() + ". Los componentes usados volverán a stock y, si resolvía una incidencia, esta quedará activa de nuevo. Escribe el motivo.",
                    "Borrar trabajo",
                    motivo -> {
                        try {
                            reparacionDAO.eliminar(rep.getIdRep(), motivo);
                            cargar();
                        } catch (SQLException e) { mostrarError(e); }
                    });
        } catch (SQLException e) { mostrarError(e); }
    }

    // ── Exportación CSV ─────────────────────────────────────────────────────

    /** Exporta el contenido visible del apartado (maestro por IMEI o detalle de un IMEI). */
    public void exportarCSV(javafx.stage.Stage owner) {
        DateTimeFormatter fmt     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (modoActual == Modo.MAESTRO) {
            List<String> cab = List.of(
                    "IMEI", "Modelo", "Primera", "Última",
                    "Reparaciones", "Glass", "Pulidos", "Inc. abiertas",
                    "Observación", "Cliente", "Revisión logística");
            List<List<String>> filas = new ArrayList<>();
            for (Object o : tablaItems) {
                if (!(o instanceof GrupoImei g)) continue;
                String modelo = g.getModelo();
                filas.add(java.util.Arrays.asList(
                        com.reparaciones.utils.CsvExporter.textoForzado(g.getImei()),
                        (modelo != null && !modelo.isEmpty()) ? FormularioReparacionController.traducirModelo(modelo) : "",
                        FechaUtils.formatear(g.getFechaMasAntigua(), fmt),
                        FechaUtils.formatear(g.getFechaMasReciente(), fmt),
                        String.valueOf(g.getCountRep()),
                        String.valueOf(g.getCountGlass()),
                        String.valueOf(g.getCountPul()),
                        String.valueOf(g.getCountIncAbiertas()),
                        g.getObservacion() != null ? g.getObservacion() : "",
                        g.getCliente() != null ? g.getCliente() : "",
                        (g.isRevisionLogistica() && !g.isTieneAsignaciones()) ? "Sí" : "No"));
            }
            com.reparaciones.utils.CsvExporter.exportar(owner, "agrupado_resumen", cab, filas);
            return;
        }

        // DETALLE: recorrido cronológico del IMEI (R + G + P) con columna Tipo
        List<String> cab = List.of(
                "Tipo", "ID", "IMEI", "Técnico", "Fecha asig.", "Fecha fin",
                "Componente", "Reutilizado", "Observaciones", "Incidencia", "Resuelto", "ID Rep. anterior");
        List<List<String>> filas = new ArrayList<>();
        for (Object o : tablaItems) {
            if (!(o instanceof ReparacionResumen r)) continue;
            TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
            String idAnt = (tipo == TipoTrabajo.PULIDO || r.getIdRepAnterior() == null) ? "" : r.getIdRepAnterior();
            filas.add(java.util.Arrays.asList(
                    tipo.etiqueta(),
                    r.getIdRep(),
                    com.reparaciones.utils.CsvExporter.textoForzado(r.getImei()),
                    r.getNombreTecnico() != null ? r.getNombreTecnico() : "",
                    FechaUtils.formatear(r.getFechaAsig(), fmtHora),
                    FechaUtils.formatear(r.getFechaFin(), fmtHora),
                    r.getTipoComponente() != null ? r.getTipoComponente() : "",
                    r.isEsReutilizado() ? "Sí" : "No",
                    r.getObservaciones() != null ? r.getObservaciones() : "",
                    r.isEsIncidencia() ? (r.getIncidencia() != null ? r.getIncidencia() : "Sí") : "No",
                    r.isEsResuelto() ? "Sí" : "No",
                    idAnt));
        }
        com.reparaciones.utils.CsvExporter.exportar(owner,
                "agrupado_" + (imeiDetalle != null ? imeiDetalle : "detalle"), cab, filas);
    }

    private void mostrarError(Exception e) {
        if (e instanceof com.reparaciones.utils.ConexionException && com.reparaciones.utils.ConexionEstado.enRefresco()) return;
        Alertas.mostrarError(e.getMessage());
    }
}
