package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.dao.GlassDAO;
import com.reparaciones.dao.LoteDAO;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.ReparacionComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.models.Lote;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.models.TelefonoInventario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.utils.FiltroImei;
import com.reparaciones.utils.MultiSelectComboBox;
import com.reparaciones.utils.MultiSelectDropdown;
import com.reparaciones.utils.SelectorClienteDialog;
import com.reparaciones.utils.TipoTrabajo;
import com.reparaciones.utils.UbicacionTexto;

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
import java.util.List;
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
    @FXML private MultiSelectComboBox<String>  filtroEstado;
    @FXML private MultiSelectComboBox<String>  filtroUbicacion;
    @FXML private MultiSelectComboBox<Lote>    filtroLote;
    @FXML private MultiSelectComboBox<String>  filtroModelo;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private MenuButton filtroIncidencias;

    @FXML private TableView<Object> tabla;
    @FXML private TableColumn<Object, Void>   colTipo;
    @FXML private TableColumn<Object, String> colIdRep;
    @FXML private TableColumn<Object, String> colImei;
    @FXML private TableColumn<Object, String> colModelo;
    @FXML private TableColumn<Object, String> colStorage;
    @FXML private TableColumn<Object, String> colColor;
    @FXML private TableColumn<Object, String> colGrado;
    @FXML private TableColumn<Object, String> colReparador;
    @FXML private TableColumn<Object, String> colAsignadoPor;
    @FXML private TableColumn<Object, String> colFecha;
    @FXML private TableColumn<Object, String> colComponente;
    @FXML private TableColumn<Object, String> colObservaciones;
    @FXML private TableColumn<Object, Void>   colEstado;
    @FXML private TableColumn<Object, String> colUbicacion;
    @FXML private TableColumn<Object, String> colLote;
    @FXML private TableColumn<Object, Void>   colIncidencia;
    @FXML private TableColumn<Object, String> colIdAnterior;
    @FXML private TableColumn<Object, String> colObservacionTelefono;
    @FXML private TableColumn<Object, String> colCliente;
    @FXML private TableColumn<Object, Void>   colRevision;
    @FXML private Button btnImportar;
    @FXML private Button btnAltaManual;

    // ── DAOs ────────────────────────────────────────────────────────────────
    private final ReparacionDAO           reparacionDAO           = new ReparacionDAO();
    private final GlassDAO                glassDAO                = new GlassDAO();
    private final PulidoDAO               pulidoDAO               = new PulidoDAO();
    private final TelefonoDAO             telefonoDAO             = new TelefonoDAO();
    private final ClienteDAO              clienteDAO              = new ClienteDAO();
    private final TecnicoDAO              tecnicoDAO              = new TecnicoDAO();
    private final LoteDAO                 loteDAO                 = new LoteDAO();
    private final ReparacionComponenteDAO reparacionComponenteDAO = new ReparacionComponenteDAO();

    // ── Config de rol ───────────────────────────────────────────────────────
    private Rol rol = Rol.SUPERTECNICO;
    private boolean esSuper = true;

    // ── Datos ───────────────────────────────────────────────────────────────
    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private final ObservableList<TelefonoInventario> inventario = FXCollections.observableArrayList();
    private final ObservableList<Object> tablaItems = FXCollections.observableArrayList();

    // ── Drill-down ──────────────────────────────────────────────────────────
    private enum Modo { MAESTRO, DETALLE }
    private Modo   modoActual  = Modo.MAESTRO;
    private String imeiDetalle = null;
    private String imeiARestaurar = null;
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

    private final Set<String>    estadosFiltro   = new HashSet<>();
    private final StringProperty etiquetaEstado  = new SimpleStringProperty("Estado");
    private MultiSelectDropdown.Handle filtroEstadoHandle;

    private final Set<String>    ubicacionesFiltro = new HashSet<>();
    private final StringProperty etiquetaUbicacion = new SimpleStringProperty("Ubicación");
    private MultiSelectDropdown.Handle filtroUbicacionHandle;

    private final Set<Integer>   idsLoteFiltro  = new HashSet<>();
    private final StringProperty etiquetaLote   = new SimpleStringProperty("Lote");
    private MultiSelectDropdown.Handle filtroLoteHandle;
    private final List<Lote>     lotesLista     = new ArrayList<>();

    private final Set<String>    modelosFiltro  = new HashSet<>();
    private final StringProperty etiquetaModelo = new SimpleStringProperty("Modelo");
    private MultiSelectDropdown.Handle filtroModeloHandle;

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
        btnImportar.setVisible(esSuper);   btnImportar.setManaged(esSuper);
        btnAltaManual.setVisible(esSuper); btnAltaManual.setManaged(esSuper);
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
            inventario.setAll(telefonoDAO.getInventario());   // inventario completo, todos los roles (consulta)
            lotesLista.clear();
            lotesLista.addAll(loteDAO.getAll());
            filtroLoteHandle = MultiSelectDropdown.setup(
                filtroLote, lotesLista, Lote::toString,
                l -> idsLoteFiltro.contains(l.getIdLote()),
                (l, checked) -> { if (checked) idsLoteFiltro.add(l.getIdLote()); else idsLoteFiltro.remove(l.getIdLote());
                                  actualizarTextoFiltroLote(); aplicarFiltros(); },
                etiquetaLote);
            poblarFiltrosMaestro();
            aplicarFiltros();
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    @FXML private void importarLote()   { ImportadorLoteDialog.abrir(raiz.getScene().getWindow(), this::cargar); }
    @FXML private void altaManualLote() { AltaManualLoteDialog.abrir(raiz.getScene().getWindow(), this::cargar); }

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
        colStorage.setVisible(true); colColor.setVisible(true); colGrado.setVisible(true);
        colUbicacion.setVisible(true); colLote.setVisible(true);
        colObservacionTelefono.setVisible(true); colCliente.setVisible(true);
        colRevision.setVisible(true);   // visible para todos; solo el supertécnico puede editarla
        colFecha.setText("Última actividad");
        colComponente.setText("Trabajos");
    }

    private void aplicarColumnasDetalle() {
        colTipo.setVisible(true);
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
        colObservaciones.setVisible(true); colIncidencia.setVisible(true); colIdAnterior.setVisible(true);
        colStorage.setVisible(false); colColor.setVisible(false); colGrado.setVisible(false);
        colUbicacion.setVisible(false); colLote.setVisible(false);
        colObservacionTelefono.setVisible(false); colCliente.setVisible(false);
        colRevision.setVisible(false);
        colFecha.setText("Fechas");
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
                if (row instanceof TelefonoInventario t) {
                    String baseStyle = "-fx-font-size: 12px; -fx-font-weight: bold; ";
                    lbl.setUserData(baseStyle);
                    lbl.setText(t.getImei());
                    lbl.setStyle(baseStyle + "-fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                    ivHist.setVisible(true); ivHist.setManaged(true);
                    ivHist.setOnMouseClicked(e -> { e.consume(); mostrarDetalle(t.getImei()); });
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
            if (row instanceof TelefonoInventario t) m = t.getModelo();
            else if (row instanceof ReparacionResumen rep) m = rep.getModelo();
            return new SimpleStringProperty((m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });

        colStorage.setCellValueFactory(d -> {
            Object row = d.getValue();
            if (row instanceof TelefonoInventario t)
                return new SimpleStringProperty(t.getStorageGb() == null ? "" : t.getStorageGb() + " GB");
            return new SimpleStringProperty("");
        });

        colColor.setCellValueFactory(d -> {
            Object row = d.getValue();
            if (row instanceof TelefonoInventario t)
                return new SimpleStringProperty(t.getColor() != null ? t.getColor() : "");
            return new SimpleStringProperty("");
        });

        colGrado.setCellFactory(col -> new TableCell<>() {
            private final Label lblPropio = new Label();
            private final Label lblProv   = new Label();
            private final VBox  box       = new VBox(1, lblPropio, lblProv);
            {
                actualizarColores(false);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (newRow != null)
                        newRow.selectedProperty().addListener((o, wasSelected, isSelected) -> actualizarColores(isSelected));
                });
            }
            private void actualizarColores(boolean selected) {
                lblPropio.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (selected ? "white" : "#2C3B54") + ";");
                lblProv.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (selected ? "white" : "#9AA0AA") + ";");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof TelefonoInventario t) {
                    lblPropio.setText(t.getGradoPropio() != null && !t.getGradoPropio().isEmpty() ? t.getGradoPropio() : "—");
                    boolean tieneProv = t.getGradoProveedor() != null && !t.getGradoProveedor().isEmpty();
                    lblProv.setText(tieneProv ? "prov: " + t.getGradoProveedor() : "");
                    lblProv.setVisible(tieneProv); lblProv.setManaged(tieneProv);
                    actualizarColores(getTableRow() != null && getTableRow().isSelected());
                    setGraphic(box);
                } else {
                    setGraphic(null);
                }
            }
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
                if (row instanceof TelefonoInventario t) {
                    lblInicio.setVisible(false); lblInicio.setManaged(false);
                    lblFin.setText(t.getUltimaActividad() != null ? FechaUtils.formatear(t.getUltimaActividad(), FORMATO_FECHA) : "—");
                } else if (row instanceof ReparacionResumen rep) {
                    lblInicio.setVisible(true); lblInicio.setManaged(true);
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
                if (row instanceof TelefonoInventario t) {
                    String txt = t.getResumenTipos();
                    if (t.getTrabajosAbiertos() > 0) txt += " · " + t.getTrabajosAbiertos() + " abiertos";
                    setText(txt);
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
                if (row instanceof TelefonoInventario t) obs = t.getObservacion();
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
                if (row instanceof TelefonoInventario t) cli = t.getCliente();
                else if (row instanceof ReparacionResumen rep) cli = rep.getCliente();
                setGraphic(labelExpandible("Cliente", cli));
            }
        });

        colUbicacion.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof TelefonoInventario t)
                    setGraphic(labelExpandible("Ubicación", UbicacionTexto.ubicacion(t)));
                else
                    setGraphic(null);
            }
        });

        colLote.setCellFactory(col -> new TableCell<>() {
            private final Label lblBatch = new Label();
            private final Label lblProv  = new Label();
            private final VBox  box      = new VBox(1, lblBatch, lblProv);
            {
                actualizarColores(false);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (newRow != null)
                        newRow.selectedProperty().addListener((o, wasSelected, isSelected) -> actualizarColores(isSelected));
                });
            }
            private void actualizarColores(boolean selected) {
                lblBatch.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (selected ? "white" : "#2C3B54") + ";");
                lblProv.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (selected ? "white" : "#9AA0AA") + ";");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                Object row = getTableView().getItems().get(getIndex());
                if (row instanceof TelefonoInventario t) {
                    lblBatch.setText(t.getBatchNumber() != null && !t.getBatchNumber().isEmpty() ? t.getBatchNumber() : "—");
                    boolean tieneProv = t.getProveedor() != null && !t.getProveedor().isEmpty();
                    lblProv.setText(tieneProv ? t.getProveedor() : "");
                    lblProv.setVisible(tieneProv); lblProv.setManaged(tieneProv);
                    actualizarColores(getTableRow() != null && getTableRow().isSelected());
                    setGraphic(box);
                } else {
                    setGraphic(null);
                }
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
                if (row instanceof TelefonoInventario t) {
                    String est = UbicacionTexto.estado(t);
                    badge.setText(est);
                    switch (est) {
                        case "Recibido"      -> badge.setStyle(base + "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;");
                        case "En reparación" -> badge.setStyle(base + "-fx-background-color: " + Colores.FILA_INCIDENCIA_BG + "; -fx-text-fill: " + Colores.FILA_INCIDENCIA_BRD + ";");
                        default              -> badge.setStyle(base + "-fx-background-color: #E8EAF0; -fx-text-fill: #586376;");
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
                    if (!(row instanceof TelefonoInventario t)) return;
                    if (t.isTieneAsignaciones()) { toggle.setSelected(false); return; }
                    boolean nuevoValor = toggle.isSelected();
                    boolean estadoAnterior = !nuevoValor;
                    new Thread(() -> {
                        try {
                            telefonoDAO.actualizarRevisionLogistica(t.getImei(), nuevoValor, t.getTelefonoUpdatedAt());
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
                if (row instanceof TelefonoInventario t) {
                    boolean efectivo = t.isRevisionLogistica() && !t.isTieneAsignaciones();
                    toggle.setSelected(efectivo);
                    aplicarEstiloToggle(efectivo);
                    if (t.isTieneAsignaciones()) {
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
                MenuItem editarAtr   = new MenuItem("Editar atributos");

                copiar.setOnAction(e -> {
                    Object rowItem = getItem();
                    if (rowItem == null || colRightClick[0] == null) return;
                    TableColumn<?, ?> col = colRightClick[0];
                    String texto = textoDeCelda(rowItem, col);
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
                editar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) FormularioReparacionController.abrirEditar(rep.getIdRep(), AgrupadoController.this::cargar); });
                borrar     .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarReparacion(rep); });
                aniadirInc .setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) abrirDialogoIncidencia(rep); });
                cancelarInc.setOnAction(e -> { if (getItem() instanceof ReparacionResumen rep) borrarIncidencia(rep); });
                editarObs  .setOnAction(e -> { if (getItem() instanceof TelefonoInventario t) abrirDialogoObservacionTelefono(t); });
                editarCli  .setOnAction(e -> {
                    if (!(getItem() instanceof TelefonoInventario t)) return;
                    try {
                        List<Cliente> activos = clienteDAO.getActivos();
                        Integer idActual = activos.stream()
                                .filter(c -> c.getNombre().equals(t.getCliente()))
                                .map(Cliente::getIdCli).findFirst().orElse(null);
                        java.util.Optional<Integer> sel = SelectorClienteDialog.elegir(activos, idActual);
                        if (sel.isEmpty()) return;
                        Integer idCli = (sel.get() == -1) ? null : sel.get();
                        telefonoDAO.actualizarCliente(t.getImei(), idCli, t.getTelefonoUpdatedAt());
                        cargar();
                    } catch (com.reparaciones.utils.StaleDataException ex) {
                        Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                        cargar();
                    } catch (SQLException ex) { mostrarError(ex); }
                });
                editarAtr  .setOnAction(e -> { if (getItem() instanceof TelefonoInventario t) abrirDialogoAtributos(t); });
                menu.getItems().addAll(editar, borrar, new SeparatorMenuItem(), copiar, new SeparatorMenuItem(),
                        aniadirInc, cancelarInc, new SeparatorMenuItem(), editarObs, new SeparatorMenuItem(), editarCli, new SeparatorMenuItem(), editarAtr);
                menu.setOnShowing(e -> {
                    boolean esGrupo = getItem() instanceof TelefonoInventario;
                    if (!(getItem() instanceof ReparacionResumen rep)) {
                        editar.setVisible(false); borrar.setVisible(false);
                        aniadirInc.setVisible(false); cancelarInc.setVisible(false);
                        editarObs.setVisible(esSuper && esGrupo);
                        editarCli.setVisible(esSuper && esGrupo && modoActual == Modo.MAESTRO);
                        editarAtr.setVisible(esSuper && esGrupo && modoActual == Modo.MAESTRO);
                        return;
                    }
                    boolean tieneInc = rep.isEsIncidencia() && !rep.isEsResuelto();
                    editar      .setVisible(esSuper && rep.getIdRep() != null && (rep.getIdRep().startsWith("R") || rep.getIdRep().startsWith("G")));
                    borrar      .setVisible(esSuper);
                    aniadirInc  .setVisible(esSuper && !rep.isEsIncidencia());
                    cancelarInc .setVisible(esSuper && tieneInc);
                    editarObs   .setVisible(false);
                    editarCli   .setVisible(false);
                    editarAtr   .setVisible(false);
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
                    if (!isEmpty() && getItem() instanceof TelefonoInventario t
                            && e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && e.getClickCount() == 2) {
                        mostrarDetalle(t.getImei());
                    }
                });

                selectedProperty().addListener((obs, wasSelected, isSelected) -> aplicarEstilo(getItem(), isEmpty()));
            }

            private void aplicarEstilo(Object item, boolean empty) {
                if (empty || item == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (item instanceof TelefonoInventario t) {
                    if (isSelected()) {
                        setStyle("-fx-background-color: " + Colores.AZUL_MEDIO + ";" +
                                "-fx-border-color: transparent transparent " + Colores.FILA_SELECTED_BRD + " transparent;" +
                                "-fx-border-width: 0 0 1 4; -fx-border-insets: 1 0 0 0;");
                    } else {
                        String brd = t.getIncAbiertas() > 0 ? Colores.FILA_INCIDENCIA_BRD : "#2C3B54";
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
        if (row instanceof TelefonoInventario t) {
            if (col == colImei)       return t.getImei();
            if (col == colModelo)     { String m = t.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
            if (col == colStorage)    return t.getStorageGb() == null ? "" : t.getStorageGb() + " GB";
            if (col == colColor)      return t.getColor() != null ? t.getColor() : "";
            if (col == colGrado)      return t.getGradoPropio() != null ? t.getGradoPropio() : "";
            if (col == colFecha)      return t.getUltimaActividad() != null ? FechaUtils.formatear(t.getUltimaActividad(), FORMATO_FECHA) : "—";
            if (col == colComponente) { String txt = t.getResumenTipos(); if (t.getTrabajosAbiertos() > 0) txt += " · " + t.getTrabajosAbiertos() + " abiertos"; return txt; }
            if (col == colUbicacion)  return UbicacionTexto.ubicacion(t);
            if (col == colLote)       return t.getBatchNumber() != null ? t.getBatchNumber() : "";
            if (col == colObservacionTelefono) return t.getObservacion();
            if (col == colCliente)    return t.getCliente();
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

        filtroEstadoHandle = MultiSelectDropdown.setup(
            filtroEstado, List.of("Recibido", "En reparación", "Histórico"), java.util.function.Function.identity(),
            est -> estadosFiltro.contains(est),
            (est, checked) -> { if (checked) estadosFiltro.add(est); else estadosFiltro.remove(est);
                                actualizarTextoFiltroEstado(); aplicarFiltros(); },
            etiquetaEstado);

        filtroUbicacionHandle = MultiSelectDropdown.setup(
            filtroUbicacion, List.of("Almacén", "Reparaciones", UbicacionTexto.FUERA), java.util.function.Function.identity(),
            ub -> ubicacionesFiltro.contains(ub),
            (ub, checked) -> { if (checked) ubicacionesFiltro.add(ub); else ubicacionesFiltro.remove(ub);
                               actualizarTextoFiltroUbicacion(); aplicarFiltros(); },
            etiquetaUbicacion);
    }

    private void poblarFiltrosMaestro() {
        List<String> clientes = inventario.stream()
            .map(t -> { String c = t.getCliente(); return (c == null || c.isEmpty()) ? SIN_CLIENTE : c; })
            .distinct().sorted().collect(Collectors.toList());
        if (!clientes.contains(SIN_CLIENTE) && inventario.stream().anyMatch(t -> t.getCliente() == null || t.getCliente().isEmpty()))
            clientes.add(0, SIN_CLIENTE);
        filtroCliHandle = MultiSelectDropdown.setup(
            filtroCliente, clientes, java.util.function.Function.identity(),
            cli -> clientesFiltro.contains(cli),
            (cli, checked) -> { if (checked) clientesFiltro.add(cli); else clientesFiltro.remove(cli);
                                actualizarTextoFiltroCliente(); aplicarFiltros(); },
            etiquetaCli);

        List<String> modelos = inventario.stream()
            .map(TelefonoInventario::getModelo)
            .filter(m -> m != null && !m.isEmpty())
            .map(FormularioReparacionController::traducirModelo)
            .distinct().sorted().collect(Collectors.toList());
        filtroModeloHandle = MultiSelectDropdown.setup(
            filtroModelo, modelos, java.util.function.Function.identity(),
            m -> modelosFiltro.contains(m),
            (m, checked) -> { if (checked) modelosFiltro.add(m); else modelosFiltro.remove(m);
                              actualizarTextoFiltroModelo(); aplicarFiltros(); },
            etiquetaModelo);
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

    private void actualizarTextoFiltroEstado() {
        long sel = estadosFiltro.size();
        if (sel == 0) etiquetaEstado.set("Estado");
        else if (sel == 1) etiquetaEstado.set(estadosFiltro.iterator().next());
        else etiquetaEstado.set(sel + " estados");
    }

    private void actualizarTextoFiltroUbicacion() {
        long sel = ubicacionesFiltro.size();
        if (sel == 0) etiquetaUbicacion.set("Ubicación");
        else if (sel == 1) etiquetaUbicacion.set(ubicacionesFiltro.iterator().next());
        else etiquetaUbicacion.set(sel + " ubicaciones");
    }

    private void actualizarTextoFiltroLote() {
        long sel = idsLoteFiltro.size();
        if (sel == 0) etiquetaLote.set("Lote");
        else if (sel == 1) {
            int id = idsLoteFiltro.iterator().next();
            etiquetaLote.set(lotesLista.stream().filter(l -> l.getIdLote() == id).findFirst().map(Lote::toString).orElse("Lote"));
        } else etiquetaLote.set(sel + " lotes");
    }

    private void actualizarTextoFiltroModelo() {
        long sel = modelosFiltro.size();
        if (sel == 0) etiquetaModelo.set("Modelo");
        else if (sel == 1) etiquetaModelo.set(modelosFiltro.iterator().next());
        else etiquetaModelo.set(sel + " modelos");
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
        filtroEstado.setVisible(true); filtroEstado.setManaged(true);
        filtroUbicacion.setVisible(true); filtroUbicacion.setManaged(true);
        filtroLote.setVisible(true); filtroLote.setManaged(true);
        filtroModelo.setVisible(true); filtroModelo.setManaged(true);
        // El técnico solo carga sus propios trabajos: el filtro por técnico no aporta nada en su perfil.
        boolean ocultarTecnico = (rol == Rol.TECNICO);
        filtroTecnico.setVisible(!ocultarTecnico); filtroTecnico.setManaged(!ocultarTecnico);
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
        filtroEstado.setVisible(false); filtroEstado.setManaged(false);
        filtroUbicacion.setVisible(false); filtroUbicacion.setManaged(false);
        filtroLote.setVisible(false); filtroLote.setManaged(false);
        filtroModelo.setVisible(false); filtroModelo.setManaged(false);
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
        estadosFiltro.clear();
        if (filtroEstadoHandle != null) filtroEstadoHandle.refresh();
        etiquetaEstado.set("Estado");
        ubicacionesFiltro.clear();
        if (filtroUbicacionHandle != null) filtroUbicacionHandle.refresh();
        etiquetaUbicacion.set("Ubicación");
        idsLoteFiltro.clear();
        if (filtroLoteHandle != null) filtroLoteHandle.refresh();
        etiquetaLote.set("Lote");
        modelosFiltro.clear();
        if (filtroModeloHandle != null) filtroModeloHandle.refresh();
        etiquetaModelo.set("Modelo");
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
        Set<String> imeisFiltro = FiltroImei.imeisValidos(imeiStr);
        // Filtro por técnico: IMEIs con algún trabajo de los técnicos marcados (los datos ya están cargados)
        Set<String> imeisDeTecnicos = idsTecFiltro.isEmpty() ? null
                : datos.stream().filter(r -> idsTecFiltro.contains(r.getIdTec()))
                       .map(ReparacionResumen::getImei).collect(Collectors.toSet());
        List<TelefonoInventario> filtrados = inventario.stream().filter(t -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(t.getImei())) return false;
            if (imeisDeTecnicos != null && !imeisDeTecnicos.contains(t.getImei())) return false;
            if (desde != null || hasta != null) {
                if (t.getUltimaActividad() == null) return false;
                LocalDate f = FechaUtils.toLocalDate(t.getUltimaActividad());
                if (desde != null && f.isBefore(desde)) return false;
                if (hasta != null && f.isAfter(hasta))  return false;
            }
            if (!clientesFiltro.isEmpty()) {
                String cli = t.getCliente();
                boolean sin = (cli == null || cli.isEmpty());
                if (!((sin && clientesFiltro.contains(SIN_CLIENTE)) || (!sin && clientesFiltro.contains(cli)))) return false;
            }
            if (!estadosFiltro.isEmpty() && !estadosFiltro.contains(UbicacionTexto.estado(t))) return false;
            if (!ubicacionesFiltro.isEmpty() && !ubicacionesFiltro.contains(UbicacionTexto.padre(t))) return false;
            if (!idsLoteFiltro.isEmpty() && !idsLoteFiltro.contains(t.getIdLote())) return false;
            if (!modelosFiltro.isEmpty()) {
                String m = t.getModelo();
                String traducido = (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "";
                if (!modelosFiltro.contains(traducido)) return false;
            }
            boolean filtrarInc    = cbIncidenciasAbiertas != null && cbIncidenciasAbiertas.isSelected();
            boolean filtrarNormal = cbNormales != null && cbNormales.isSelected();
            if (filtrarInc || filtrarNormal) {
                boolean tieneInc = t.getIncAbiertas() > 0;
                if (!((filtrarInc && tieneInc) || (filtrarNormal && !tieneInc))) return false;
            }
            return true;
        }).sorted(Comparator.comparing(TelefonoInventario::getUltimaActividad,
                Comparator.nullsLast(Comparator.reverseOrder()))).collect(Collectors.toList());
        tablaItems.setAll(filtrados);
        restaurarSeleccion();
        int n = tablaItems.size();
        lblContador.setText(n + (n == 1 ? " teléfono" : " teléfonos"));
        lblContador.setVisible(true); lblContador.setManaged(true);
    }

    /** Si venimos de un detalle, re-selecciona el teléfono y hace scroll hasta él. */
    private void restaurarSeleccion() {
        if (imeiARestaurar == null) return;
        int idx = TelefonoInventario.indiceDe(tablaItems, imeiARestaurar);
        imeiARestaurar = null;
        if (idx < 0) return;
        tabla.getSelectionModel().clearAndSelect(idx);
        tabla.scrollTo(Math.max(0, idx - 3));
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

    private void mostrarDetalle(String imei) {
        modoActual  = Modo.DETALLE;
        imeiDetalle = imei;

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
        imeiARestaurar = imeiDetalle;
        resetarModo();
        aplicarFiltros();
    }

    /** ¿Está el drill-down en modo detalle? (para el botón del sub-sidebar) */
    public boolean enDetalle() { return modoActual == Modo.DETALLE; }

    /** Vuelve al maestro restaurando selección y scroll (equivale a «← Volver»). */
    public void volverAlMaestro() { if (enDetalle()) volverAGrupos(); }

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

    private void abrirDialogoObservacionTelefono(TelefonoInventario t) {
        TextArea tfObs = new TextArea(t.getObservacion() != null ? t.getObservacion() : "");
        tfObs.setPromptText("Observación del teléfono...");
        tfObs.setWrapText(true);
        tfObs.setPrefRowCount(4);
        tfObs.setStyle("-fx-background-color: white; -fx-border-color: " + Colores.GRIS_BORDE + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");

        Button btnConfirmar = new Button("Guardar");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        btnConfirmar.setStyle("-fx-background-color: " + Colores.FILA_REPARADO_ICO + "; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;");

        VBox form = new VBox(8, new Label("Observación — IMEI " + t.getImei()), tfObs, btnConfirmar);
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
                telefonoDAO.actualizarObservacion(t.getImei(), tfObs.getText().trim(), t.getTelefonoUpdatedAt());
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

    private void abrirDialogoAtributos(TelefonoInventario t) {
        final String[] modeloSel = { t.getModelo() };
        Label lblModelo = new Label(t.getModelo() != null && !t.getModelo().isEmpty()
                ? FormularioReparacionController.traducirModelo(t.getModelo()) : "—");
        Button btnModelo = new Button("Cambiar…");
        btnModelo.setOnAction(e -> SelectorModeloDialog.elegir(modeloSel[0]).ifPresent(m -> {
            modeloSel[0] = m;
            lblModelo.setText(FormularioReparacionController.traducirModelo(m));
        }));
        HBox filaModelo = new HBox(8, new Label("Modelo:"), lblModelo, btnModelo);
        filaModelo.setAlignment(Pos.CENTER_LEFT);

        TextField tfStorage = new TextField(t.getStorageGb() == null ? "" : String.valueOf(t.getStorageGb()));
        tfStorage.setPromptText("GB (vacío = sin dato)");
        tfStorage.textProperty().addListener((obs, o, n) -> { if (!n.matches("\\d*")) tfStorage.setText(o); });
        TextField tfColor = new TextField(t.getColor() != null ? t.getColor() : "");
        TextField tfGradoProv = new TextField(t.getGradoProveedor() != null ? t.getGradoProveedor() : "");
        ComboBox<String> cbGradoPropio = new ComboBox<>(FXCollections.observableArrayList("—", "C", "B", "A-", "A", "A+"));
        cbGradoPropio.setValue(t.getGradoPropio() != null ? t.getGradoPropio() : "—");

        Button btnGuardar = new Button("Guardar");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);
        btnGuardar.setStyle("-fx-background-color: " + Colores.FILA_REPARADO_ICO +
                "; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;");

        VBox form = new VBox(8, filaModelo,
                new Label("Storage (GB)"), tfStorage,
                new Label("Color"), tfColor,
                new Label("Grado proveedor"), tfGradoProv,
                new Label("Grado propio (chasis)"), cbGradoPropio,
                btnGuardar);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
        form.setPrefWidth(420);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Editar atributos — IMEI " + t.getImei());
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        btnGuardar.setOnAction(e -> {
            try {
                Integer storage = tfStorage.getText().isBlank() ? null : Integer.valueOf(tfStorage.getText().trim());
                String gradoPropio = "—".equals(cbGradoPropio.getValue()) ? null : cbGradoPropio.getValue();
                telefonoDAO.actualizarAtributos(t.getImei(), modeloSel[0], storage,
                        tfColor.getText().isBlank() ? null : tfColor.getText().trim(),
                        tfGradoProv.getText().isBlank() ? null : tfGradoProv.getText().trim(),
                        gradoPropio, t.getTelefonoUpdatedAt());
                dialog.close();
                cargar();
            } catch (NumberFormatException ex) {
                Alertas.mostrarError("Storage no válido: " + tfStorage.getText());
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
                    "IMEI", "Modelo", "Storage", "Color", "Grado propio", "Grado proveedor",
                    "Estado", "Ubicación", "Lote", "Proveedor", "Última actividad",
                    "Reparaciones", "Glass", "Pulidos", "Abiertos", "Inc. abiertas",
                    "Observación", "Cliente", "Revisión logística");
            List<List<String>> filas = new ArrayList<>();
            for (Object o : tablaItems) {
                if (!(o instanceof TelefonoInventario t)) continue;
                String modelo = t.getModelo();
                filas.add(java.util.Arrays.asList(
                        com.reparaciones.utils.CsvExporter.textoForzado(t.getImei()),
                        (modelo != null && !modelo.isEmpty()) ? FormularioReparacionController.traducirModelo(modelo) : "",
                        t.getStorageGb() == null ? "" : String.valueOf(t.getStorageGb()),
                        t.getColor() != null ? t.getColor() : "",
                        t.getGradoPropio() != null ? t.getGradoPropio() : "",
                        t.getGradoProveedor() != null ? t.getGradoProveedor() : "",
                        UbicacionTexto.estado(t),
                        UbicacionTexto.ubicacion(t),
                        t.getBatchNumber() != null ? t.getBatchNumber() : "",
                        t.getProveedor() != null ? t.getProveedor() : "",
                        FechaUtils.formatear(t.getUltimaActividad(), fmt),
                        String.valueOf(t.getRepHechas()),
                        String.valueOf(t.getGlassHechas()),
                        String.valueOf(t.getPulHechos()),
                        String.valueOf(t.getTrabajosAbiertos()),
                        String.valueOf(t.getIncAbiertas()),
                        t.getObservacion() != null ? t.getObservacion() : "",
                        t.getCliente() != null ? t.getCliente() : "",
                        (t.isRevisionLogistica() && !t.isTieneAsignaciones()) ? "Sí" : "No"));
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
