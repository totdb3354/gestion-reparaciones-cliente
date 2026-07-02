package com.reparaciones.controllers;

import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.dao.GlassDAO;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.FechaUtils;
import com.reparaciones.utils.TipoTrabajo;
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
    @FXML private TableColumn<ReparacionResumen, Void>   cTipo;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cCliente;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private TableColumn<ReparacionResumen, String> cAsignadoPor;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;
    @FXML private javafx.scene.control.Button btnAsignar;
    private boolean soloLectura = false;
    @FXML private TextField  filtroImei;
    @FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private MenuButton filtroTipo;
    @FXML private MenuButton filtroSolicitud;

    private final ReparacionDAO  reparacionDAO = new ReparacionDAO();
    private final GlassDAO       glassDAO      = new GlassDAO();
    private final PulidoDAO      pulidoDAO     = new PulidoDAO();
    private final TecnicoDAO     tecnicoDAO    = new TecnicoDAO();
    private final TelefonoDAO    telefonoDAO   = new TelefonoDAO();
    private final ClienteDAO     clienteDAO    = new ClienteDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;
    private Runnable onActualizar;
    /** IMEI → nº de técnicos distintos con asignación pendiente (sub-indicador "N asignados"). */
    private java.util.Map<String,Integer> conteoTecnicosPorImei = java.util.Map.of();

    @FXML private Label  lblUltimaActualizacion;
    @FXML private Label  lblContador;

    private CheckBox cbTipoReparacion;
    private CheckBox cbTipoGlass;
    private CheckBox cbTipoPulido;
    private CheckBox cbSoloSolicitudes;
    private CheckBox cbSoloIncidencias;
    private CheckBox cbSoloAsignaciones;
    private final Set<Integer>   idsTecFiltro  = new HashSet<>();
    private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
    private final List<Tecnico>         tecnicos         = new ArrayList<>();

    /** Una entrada del lote de asignación: un IMEI con su configuración local (aún no en BD). */
    private static final class EntradaAsignacion {
        final String imei;
        TipoTrabajo tipo = TipoTrabajo.REPARACION;             // reparación (A) o glass (AG); fijado por el selector al escanear
        String modeloCode;                       // código interno del modelo, o null si falta
        final List<Tecnico> tecnicos = new ArrayList<>();
        Cliente cliente;                         // cliente del IMEI (por entrada), o null
        boolean sinCliente;                      // true = el usuario eligió explícitamente "— Sin cliente —"
        String comentario = "";
        boolean asignada;                        // true = verde (configurada y movida); false = rojo (pendiente)
        boolean modeloBuscado;                   // true si ya se lanzó el lookup (no repetir)
        boolean buscando;                        // true mientras el lookup está en vuelo
        long seq;                                // orden de la última acción (escaneo/asignación): mayor = más reciente = más arriba

        EntradaAsignacion(String imei) { this.imei = imei; }

        boolean tieneModelo() { return modeloCode != null && !modeloCode.isEmpty(); }
    }

    /** Una entrada del lote de pulido: IMEI + técnico + comentario + cliente (editable por fila). */
    private static final class FilaPulido {
        final String imei;
        Tecnico tecnico;
        String  comentario;
        com.reparaciones.models.Cliente cliente;   // por IMEI, no se arrastra (como rep/glass)
        boolean sinCliente;                        // true = "— Sin cliente —" explícito
        FilaPulido(String imei, Tecnico tecnico, String comentario) {
            this.imei = imei; this.tecnico = tecnico; this.comentario = comentario;
        }
    }

    /** Propaga cliente/sin-cliente de un IMEI a las colas del modal. */
    @FunctionalInterface
    private interface TriConsumerCliente {
        void accept(String imei, com.reparaciones.models.Cliente cliente, boolean sinCliente);
    }

    /** Deriva el tipo de trabajo del prefijo del {@code ID_REP}. Delega en {@link TipoTrabajo#desde}. */
    static TipoTrabajo tipoDe(String idRep) {
        return TipoTrabajo.desde(idRep);
    }

    @FXML
    public void initialize() {
        tablaPendientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaPendientes.getColumns().forEach(c -> c.setSortable(false));   // el orden lo llevan los filtros/prioridad, no el clic en la cabecera


        cId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cTipo.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                TipoTrabajo tipo = tipoDe(getTableView().getItems().get(getIndex()).getIdRep());
                badge.setText(tipo.etiqueta());
                badge.setStyle(tipo.estiloBadge());
                setGraphic(badge);
            }
        });
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
                    if (sel == null || sel.getIdTec() == rep.getIdTec()) return;
                    // Guardado directo: reasignar el técnico ya en BD.
                    String com = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                    try {
                        if (tipoDe(rep.getIdRep()) == TipoTrabajo.PULIDO)
                            pulidoDAO.actualizarAsignacionPulido(rep.getIdRep(), sel.getIdTec(), com, rep.getUpdatedAt());
                        else   // reparación y glass operan por ID en ReparacionDAO
                            reparacionDAO.actualizarAsignacion(rep.getIdRep(), sel.getIdTec(), com, rep.getUpdatedAt());
                        cargar();
                        if (onActualizar != null) onActualizar.run();   // refresca el badge del sidebar
                    } catch (com.reparaciones.utils.StaleDataException ex) {
                        Alertas.mostrarError("La asignación fue modificada por otro usuario. Se recargan los datos.");
                        cargar();
                    } catch (SQLException ex) {
                        Alertas.mostrarError(ex.getMessage());
                        cargar();
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                if (cb.isShowing()) return;
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                    setStyle("");
                    return;
                }
                actualizando = true;
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                if (soloLectura) {
                    setText(rep.getNombreTecnico() != null ? rep.getNombreTecnico() : "");
                    setGraphic(null);
                    actualizando = false;
                    setStyle("");
                    return;
                }
                cb.getItems().setAll(tecnicos);
                Tecnico mostrar = tecnicos.stream().filter(t -> t.getIdTec() == rep.getIdTec()).findFirst().orElse(null);
                cb.setValue(mostrar);
                actualizando = false;
                setStyle("");
                setGraphic(cb);
            }
        });
        cImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final Label lblAsignados = new Label();
            private final javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(1, lbl, lblAsignados);
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> aplicarEstilos(sel);
            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                lblAsignados.setVisible(false); lblAsignados.setManaged(false);
                aplicarEstilos(false);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(selListener);
                    if (newRow != null) newRow.selectedProperty().addListener(selListener);
                });
            }
            private void aplicarEstilos(boolean sel) {
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
                lblAsignados.setStyle("-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: " + (sel ? "white" : "#9AA0AA") + ";");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                String imei = getTableView().getItems().get(getIndex()).getImei();
                lbl.setText(imei);
                int n = conteoTecnicosPorImei.getOrDefault(imei, 1);
                boolean varios = n >= 2;
                lblAsignados.setText(varios ? n + " asignados" : "");
                lblAsignados.setVisible(varios); lblAsignados.setManaged(varios);
                aplicarEstilos(getTableRow() != null && getTableRow().isSelected());
                setGraphic(box);
            }
        });
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cCliente.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCliente() != null ? d.getValue().getCliente() : ""));
        cFecha.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                FechaUtils.formatear(d.getValue().getFechaAsig(), FMT)));
        cComentario.setCellValueFactory(d -> {
            ReparacionResumen rep = d.getValue();
            String texto = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
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
                setStyle("");
            }
        });
        cAsignadoPor.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getNombreTecnicoAsigna() != null ? d.getValue().getNombreTecnicoAsigna() : "—"));

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPendientes.setItems(datosFiltrados);
        datosFiltrados.addListener((javafx.collections.ListChangeListener<ReparacionResumen>) c -> actualizarContador());
        actualizarContador();
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
                    String actual = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                    PendientesSuperTecnicoController.this.abrirEditorComentario(rep, actual);
                });
                menu.getItems().add(editarComentario);
                MenuItem editarModelo = new MenuItem("Editar modelo");   // solo pulido (no pasa por el modal de piezas)
                ImageView ivEditarMod = new ImageView(imgEditar);
                ivEditarMod.setFitWidth(14); ivEditarMod.setFitHeight(14); ivEditarMod.setPreserveRatio(true);
                editarModelo.setGraphic(ivEditarMod);
                editarModelo.setOnAction(e -> { if (getItem() != null) abrirSelectorModelo(getItem()); });
                menu.getItems().add(editarModelo);
                MenuItem toggleUrgente = new MenuItem();
                toggleUrgente.setOnAction(e -> {
                    if (getItem() == null) return;
                    ReparacionResumen rep = getItem();
                    boolean nuevoEstado = !rep.isUrgente();
                    try {
                        reparacionDAO.actualizarUrgente(rep.getIdRep(), nuevoEstado);
                        cargar();
                    } catch (java.sql.SQLException ex) { mostrarError(ex); }
                });
                MenuItem editarCliente = new MenuItem("Editar cliente");
                ImageView ivEditarCli = new ImageView(imgEditar);
                ivEditarCli.setFitWidth(14); ivEditarCli.setFitHeight(14); ivEditarCli.setPreserveRatio(true);
                editarCliente.setGraphic(ivEditarCli);
                editarCliente.setOnAction(e -> {
                    if (getItem() == null) return;
                    ReparacionResumen rep = getItem();
                    try {
                        java.util.List<com.reparaciones.models.Cliente> activos = clienteDAO.getActivos();
                        Integer idActual = activos.stream()
                                .filter(c -> c.getNombre().equals(rep.getCliente()))
                                .map(com.reparaciones.models.Cliente::getIdCli).findFirst().orElse(null);
                        java.util.Optional<Integer> sel = com.reparaciones.utils.SelectorClienteDialog.elegir(activos, idActual);
                        if (sel.isEmpty()) return;
                        Integer idCli = (sel.get() == -1) ? null : sel.get();
                        telefonoDAO.actualizarCliente(rep.getImei(), idCli, rep.getTelefonoUpdatedAt());
                        cargar();
                    } catch (com.reparaciones.utils.StaleDataException ex) {
                        Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
                        cargar();
                    } catch (java.sql.SQLException ex) { mostrarError(ex); }
                });
                menu.setOnShowing(e -> {
                    // Modo solo lectura (admin): solo "Copiar celda"; se ocultan las acciones de escritura.
                    boolean esPulido = getItem() != null && tipoDe(getItem().getIdRep()) == TipoTrabajo.PULIDO;
                    editarComentario.setVisible(!soloLectura);
                    editarModelo.setVisible(!soloLectura && esPulido);          // pulido: edita modelo (rep/glass van por el modal de piezas)
                    toggleUrgente.setVisible(!soloLectura && !esPulido);
                    editarCliente.setVisible(!soloLectura);   // cliente aplica a cualquier tipo, pulido incluido
                    if (getItem() != null)
                        toggleUrgente.setText(getItem().isUrgente() ? "Quitar urgente" : "Marcar urgente");
                });
                menu.getItems().add(toggleUrgente);
                menu.getItems().add(editarCliente);
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    // Selecciona la fila clicada para que el guardado directo nunca caiga en otra.
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size())
                        getTableView().getSelectionModel().select(getIndex());
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
                                TipoTrabajo tipo = tipoDe(rep.getIdRep());
                                try {
                                    if (tipo == TipoTrabajo.PULIDO)
                                        pulidoDAO.eliminarAsignacionPulido(rep.getIdRep());
                                    else if (rep.isEsIncidencia())
                                        reparacionDAO.borrarIncidenciaPorImei(rep.getImei(), tipo == TipoTrabajo.GLASS ? "G" : "R");
                                    else
                                        reparacionDAO.eliminarAsignacion(rep.getIdRep());   // sirve para A y AG
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

    /** Activa el modo solo lectura (admin): oculta acciones de escritura. Default false (supertécnico no afectado). */
    public void setSoloLectura(boolean soloLectura) {
        this.soloLectura = soloLectura;
        if (soloLectura) {
            cAccion.setVisible(false);
            if (btnAsignar != null) { btnAsignar.setVisible(false); btnAsignar.setManaged(false); }
            tablaPendientes.refresh();
        }
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

        // Filtro por tipo de trabajo (Reparación / Glass / Pulido)
        cbTipoReparacion = new CheckBox("Reparación");
        cbTipoGlass      = new CheckBox("Glass");
        cbTipoPulido     = new CheckBox("Pulido");
        for (CheckBox cb : new CheckBox[]{cbTipoReparacion, cbTipoGlass, cbTipoPulido}) {
            cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
            cb.selectedProperty().addListener((obs, o, n) -> { actualizarTextoFiltroTipo(); aplicarFiltros(); });
        }
        filtroTipo.getItems().addAll(
                new CustomMenuItem(cbTipoReparacion, false),
                new CustomMenuItem(cbTipoGlass, false),
                new CustomMenuItem(cbTipoPulido, false));

        // Filtro estado (categoría de fila)
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

    private void actualizarTextoFiltroTipo() {
        boolean rep = cbTipoReparacion.isSelected();
        boolean gla = cbTipoGlass.isSelected();
        boolean pul = cbTipoPulido.isSelected();
        long total = java.util.stream.Stream.of(rep, gla, pul).filter(Boolean::booleanValue).count();
        if      (total == 0) filtroTipo.setText("Tipo");
        else if (total == 3) filtroTipo.setText("Todos");
        else if (total == 1) filtroTipo.setText(rep ? "Reparación" : gla ? "Glass" : "Pulido");
        else                 filtroTipo.setText(total + " tipos");
    }

    private void actualizarTextoFiltroSolicitud() {
        boolean sol  = cbSoloSolicitudes.isSelected();
        boolean inc  = cbSoloIncidencias.isSelected();
        boolean asig = cbSoloAsignaciones.isSelected();
        long total = java.util.stream.Stream.of(sol, inc, asig).filter(Boolean::booleanValue).count();
        if      (total == 0) filtroSolicitud.setText("Estado");
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
        java.util.EnumSet<TipoTrabajo> tiposSelec = java.util.EnumSet.noneOf(TipoTrabajo.class);
        if (cbTipoReparacion.isSelected()) tiposSelec.add(TipoTrabajo.REPARACION);
        if (cbTipoGlass.isSelected())      tiposSelec.add(TipoTrabajo.GLASS);
        if (cbTipoPulido.isSelected())     tiposSelec.add(TipoTrabajo.PULIDO);

        String imeiStr = filtroImei != null ? filtroImei.getText().trim() : "";
        java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(imeiStr);
        datosFiltrados.setPredicate(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            if (!tiposSelec.isEmpty() && !tiposSelec.contains(tipoDe(rep.getIdRep()))) return false;
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
        cbTipoReparacion.setSelected(false);
        cbTipoGlass.setSelected(false);
        cbTipoPulido.setSelected(false);
        filtroTipo.setText("Tipo");
        cbSoloSolicitudes.setSelected(false);
        cbSoloIncidencias.setSelected(false);
        cbSoloAsignaciones.setSelected(false);
        filtroSolicitud.setText("Estado");
        aplicarFiltros();
    }

    public void setOnActualizar(Runnable onActualizar) { this.onActualizar = onActualizar; }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    // ─── Carga ────────────────────────────────────────────────────────────────

    public void cargar() {
        try {
            tablaPendientes.getSelectionModel().clearSelection();
            // Tabla unificada: reparación (A) + glass (AG) + pulido (AP). El tipo se deriva del prefijo del ID.
            List<ReparacionResumen> asignaciones = new ArrayList<>();
            asignaciones.addAll(reparacionDAO.getAsignaciones());
            asignaciones.addAll(glassDAO.getAsignacionesGlass());
            asignaciones.addAll(pulidoDAO.getAsignacionesPulido());
            conteoTecnicosPorImei = contarTecnicosPorImei(asignaciones);
            datos.setAll(asignaciones);
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

    /**
     * Cuenta técnicos distintos (idTec) por IMEI a partir de las asignaciones cargadas.
     * Las filas con IMEI {@code null} se ignoran. Puro y testeable (sin estado ni JavaFX).
     */
    static java.util.Map<String,Integer> contarTecnicosPorImei(List<ReparacionResumen> filas) {
        java.util.Map<String, Set<Integer>> tecnicosPorImei = new java.util.HashMap<>();
        for (ReparacionResumen r : filas) {
            if (r.getImei() == null) continue;
            tecnicosPorImei.computeIfAbsent(r.getImei(), k -> new HashSet<>()).add(r.getIdTec());
        }
        java.util.Map<String,Integer> conteo = new java.util.HashMap<>();
        tecnicosPorImei.forEach((imei, tecnicos) -> conteo.put(imei, tecnicos.size()));
        return conteo;
    }

    /** Técnicos (idTec) que ya tienen una asignación pendiente de ese IMEI en esa categoría,
     *  calculado desde la tabla unificada ya cargada ({@link #datos}) — per-categoría, sin HTTP extra. */
    private java.util.Set<Integer> tecnicosOcupados(String imei, TipoTrabajo tipo) {
        java.util.Set<Integer> ids = new HashSet<>();
        for (ReparacionResumen r : datos)
            if (imei.equals(r.getImei()) && tipoDe(r.getIdRep()) == tipo) ids.add(r.getIdTec());
        return ids;
    }

    /** Construye una fila de la pila para {@code e}. onClick = cargar en el formulario; onRemove = quitar de la pila. */
    private HBox crearFilaPila(EntradaAsignacion e, boolean seleccionada, Runnable onClick, Runnable onRemove) {
        Label lblImei = new Label(e.imei);
        lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        lblImei.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);   // nunca se recorta el IMEI

        Label badgeTipo = new Label(e.tipo == TipoTrabajo.GLASS ? "Glass" : "Rep");
        badgeTipo.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        badgeTipo.setStyle("-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 1 6 1 6;"
                + (e.tipo == TipoTrabajo.GLASS
                   ? " -fx-background-color: #E0F2F1; -fx-text-fill: #00796B;"
                   : " -fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;"));

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
        estado.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        // El contenido (IMEI + modelo) crece y se RECORTA limpio (sin "...") si no cabe;
        // la ✕ va fuera del recorte, pegada a la derecha → siempre visible.
        HBox contenido = new HBox(8, lblImei, badgeTipo, estado);
        contenido.setAlignment(Pos.CENTER_LEFT);
        contenido.setMinWidth(0);
        HBox.setHgrow(contenido, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(contenido.widthProperty());
        clip.heightProperty().bind(contenido.heightProperty());
        contenido.setClip(clip);

        Label x = new Label("✕");
        String xBase = "-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
        String xHover = "-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
        x.setStyle(xBase);
        x.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        x.setOnMouseEntered(ev -> x.setStyle(xHover));
        x.setOnMouseExited(ev -> x.setStyle(xBase));
        x.setOnMouseClicked(ev -> { ev.consume(); onRemove.run(); });

        HBox fila = new HBox(8, contenido, x);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (seleccionada) {
            fila.setStyle("-fx-padding: 7 9 7 5; -fx-background-color: #EAF1FF;"
                    + " -fx-border-color: transparent transparent #EEF1F5 #2C3B54; -fx-border-width: 0 0 1 4; -fx-cursor: hand;");
        } else {
            fila.setStyle("-fx-padding: 7 9 7 9; -fx-border-color: transparent transparent #EEF1F5 transparent;"
                    + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
        }
        fila.setOnMouseClicked(ev -> onClick.run());
        return fila;
    }

    /**
     * Construye el sub-panel ligero de alta de pulido (técnico + comentario por defecto,
     * escaneo con pegado de lote, lista editable por fila). Rellena {@code lote};
     * {@code onChange} refresca la barra de guardar del modal unificado.
     */
    private VBox construirPulidoPane(List<FilaPulido> lote, List<Tecnico> tecnicosModal, Runnable onChange,
                                     java.util.function.Consumer<FilaPulido> onClienteCambiado,
                                     List<Runnable> refrescadoresCliente) {
        Label lblTec = new Label("Técnico por defecto");
        lblTec.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<Tecnico> cbTec = new ComboBox<>();
        cbTec.setMaxWidth(Double.MAX_VALUE);
        cbTec.setVisibleRowCount(8);
        cbTec.getItems().addAll(tecnicosModal);
        cbTec.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
            @Override public Tecnico fromString(String s) { return null; }
        });

        Label lblCom = new Label("Comentario por defecto (opcional)");
        lblCom.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea taCom = new TextArea();
        taCom.setWrapText(true); taCom.setPrefRowCount(2);
        taCom.setPromptText("Instrucciones para el técnico...");
        taCom.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        Label lblScan = new Label("Escanear IMEI → pulido");
        lblScan.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
        tfScan.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-padding: 11; -fx-text-fill: #2C3B54; -fx-font-size: 14px;");
        Label lblErr = new Label();
        String errStyle = "-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;";
        String okStyle  = "-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-min-height: 15;";
        lblErr.setStyle(errStyle);

        Label lblTitulo = new Label("Nada añadido aún");
        VBox listaItems = new VBox(0);
        listaItems.setStyle("-fx-background-color: white;");
        ScrollPane scroll = new ScrollPane(listaItems);
        scroll.setFitToWidth(true); scroll.setMaxHeight(260);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: #C2C8D0;"
                + " -fx-border-radius: 6; -fx-border-width: 1;");

        Runnable actualizarTitulo = () -> {
            int n = lote.size();
            lblTitulo.setText(n == 0 ? "Nada añadido aún" : (n + (n == 1 ? " en pulido" : " en pulido")));
            lblTitulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: " + (n == 0 ? "#586376" : "#2E7D32") + ";");
        };
        actualizarTitulo.run();

        final List<com.reparaciones.models.Cliente> clientesActivos = new ArrayList<>();   // para el selector
        final List<com.reparaciones.models.Cliente> clientesTodos   = new ArrayList<>();   // para precargar (incluye inactivos)
        try {
            clientesActivos.addAll(clienteDAO.getActivos());
            clientesTodos.addAll(clienteDAO.getAll());
        } catch (SQLException ex) { /* no crítico: el pane sigue funcionando */ }

        java.util.function.Consumer<String> agregar = imei -> {
            FilaPulido fila = new FilaPulido(imei, cbTec.getValue(), taCom.getText().trim());
            lote.add(fila);
            Label lblImei = new Label(imei);
            lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
            lblImei.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            ComboBox<Tecnico> cbRow = new ComboBox<>();
            cbRow.getItems().addAll(tecnicosModal);
            cbRow.setValue(fila.tecnico);
            cbRow.setPrefWidth(150);
            cbRow.setStyle("-fx-font-size: 11px;");
            cbRow.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
                @Override public Tecnico fromString(String s) { return null; }
            });
            cbRow.valueProperty().addListener((o2, a, b) -> fila.tecnico = b);
            Button btnCli = new Button(fila.cliente != null ? fila.cliente.getNombre() : "Cliente");
            btnCli.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            btnCli.setStyle("-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #C2C8D0;"
                    + " -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4 8 4 8; -fx-cursor: hand;");
            btnCli.setOnAction(ev -> {
                Integer idActual = fila.cliente != null ? fila.cliente.getIdCli() : null;
                java.util.Optional<Integer> sel = com.reparaciones.utils.SelectorClienteDialog.elegir(clientesActivos, idActual);
                if (sel.isEmpty()) return;
                Integer idCli = sel.get() == -1 ? null : sel.get();
                fila.sinCliente = (sel.get() == -1);   // -1 → sin cliente explícito
                fila.cliente = idCli == null ? null
                        : clientesActivos.stream().filter(c -> c.getIdCli() == idCli).findFirst().orElse(null);
                btnCli.setText(fila.sinCliente ? "— Sin cliente —"
                        : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente"));
                onClienteCambiado.accept(fila);
            });
            refrescadoresCliente.add(() -> btnCli.setText(fila.sinCliente ? "— Sin cliente —"
                    : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente")));
            TextField tfRow = new TextField(fila.comentario);
            tfRow.setPromptText("Comentario...");
            HBox.setHgrow(tfRow, javafx.scene.layout.Priority.ALWAYS);
            tfRow.setStyle("-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #E1E5EC;"
                    + " -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 5;");
            tfRow.textProperty().addListener((o2, a, b) -> fila.comentario = b.trim());
            Label btnX = new Label("✕");
            String xBase = "-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
            btnX.setStyle(xBase);
            btnX.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            btnX.setOnMouseEntered(ev -> btnX.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
            btnX.setOnMouseExited(ev -> btnX.setStyle(xBase));
            HBox row = new HBox(8, lblImei, cbRow, btnCli, tfRow, btnX);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 6 8 6 8; -fx-border-color: transparent transparent #EEF1F5 transparent; -fx-border-width: 0 0 1 0;");
            btnX.setOnMouseClicked(ev -> { lote.remove(fila); listaItems.getChildren().remove(row); actualizarTitulo.run(); onChange.run(); });
            listaItems.getChildren().add(row);
            actualizarTitulo.run();
            onChange.run();

            // Precargar el cliente que el IMEI ya tuviera en BD (paridad con rep/glass), en segundo plano.
            new Thread(() -> {
                Integer idCli = null;
                try { idCli = telefonoDAO.getClienteId(imei); } catch (Exception ignore) {}
                final Integer idCliRes = idCli;
                javafx.application.Platform.runLater(() -> {
                    if (idCliRes != null && fila.cliente == null && !fila.sinCliente) {
                        com.reparaciones.models.Cliente existente = clientesTodos.stream()
                                .filter(c -> c.getIdCli() == idCliRes).findFirst().orElse(null);
                        if (existente != null) { fila.cliente = existente; btnCli.setText(existente.getNombre()); }
                    }
                });
            }, "pulido-precarga-cliente").start();
        };

        Runnable intentar = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            if (cbTec.getValue() == null) { lblErr.setStyle(errStyle); lblErr.setText("Selecciona un técnico por defecto primero."); return; }
            if (lote.stream().anyMatch(f -> f.imei.equals(imei))) { lblErr.setStyle(errStyle); lblErr.setText("Ese IMEI ya está en la lista de pulido."); return; }
            lblErr.setText("");
            agregar.accept(imei);
            javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) { String solo = n.replaceAll("[^\\d]", ""); javafx.application.Platform.runLater(() -> tfScan.setText(solo)); return; }
            if (n.length() > 15) {
                com.reparaciones.utils.ImeiUtils.ResultadoPegado res = com.reparaciones.utils.ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == com.reparaciones.utils.ImeiUtils.TipoPegado.CORRUPTO) {
                    javafx.application.Platform.runLater(() -> { tfScan.clear(); lblErr.setStyle(errStyle); lblErr.setText("Algún IMEI del pegado está corrupto."); });
                    return;
                }
                if (cbTec.getValue() == null) {
                    javafx.application.Platform.runLater(() -> { tfScan.clear(); lblErr.setStyle(errStyle); lblErr.setText("Selecciona un técnico por defecto primero."); });
                    return;
                }
                int add = 0, dup = 0;
                for (String imei : res.imeis()) { if (lote.stream().anyMatch(f -> f.imei.equals(imei))) { dup++; continue; } agregar.accept(imei); add++; }
                final String resumen = add + " IMEIs añadidos" + (dup > 0 ? " · " + dup + " ya estaban." : ".");
                javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); lblErr.setStyle(okStyle); lblErr.setText(resumen); });
                return;
            }
            lblErr.setStyle(errStyle);
            lblErr.setText("");
            if (n.length() == 15) intentar.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) intentar.run(); });

        return new VBox(8, lblTec, cbTec, lblCom, taCom, new Separator(), lblScan, tfScan, lblErr, new Separator(), lblTitulo, scroll);
    }

    @FXML
    private void abrirFormularioAsignacion() {
        // ── Estado del lote ──────────────────────────────────────────────────
        List<EntradaAsignacion> pilaRep   = new ArrayList<>();
        List<EntradaAsignacion> pilaGlass = new ArrayList<>();
        EntradaAsignacion[] actual = { null };
        boolean[] editandoVerde = { false };
        List<Tecnico> defTecnicos = new ArrayList<>();
        long[] seqCounter = { 0 };
        TipoTrabajo[] tipoActual = { TipoTrabajo.REPARACION };   // tipo por defecto de los IMEIs que se escaneen (lo fija el selector)
        // Cola de la categoría activa (rep/glass); pulido va aparte en lotePulido.
        java.util.function.Supplier<List<EntradaAsignacion>> pilaActiva =
                () -> tipoActual[0] == TipoTrabajo.GLASS ? pilaGlass : pilaRep;
        List<FilaPulido> lotePulido = new ArrayList<>();   // sub-lote de pulido (AP), independiente de la pila rica

        List<Tecnico> tecnicosModal = new ArrayList<>();
        try { tecnicosModal.addAll(tecnicoDAO.getAllActivos()); }
        catch (SQLException ex) { mostrarError(ex); }

        List<Cliente> clientesModal = new ArrayList<>();
        try { clientesModal.addAll(new ClienteDAO().getActivos()); }
        catch (SQLException ex) { mostrarError(ex); }

        // ── Cabecera + escaneo ───────────────────────────────────────────────
        Label lblTitulo = new Label("Asignar trabajos");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label lblSub = new Label("Elige el tipo, escanea IMEIs y configúralos. Técnicos y comentario se mantienen entre IMEIs. Se guardan todos al final.");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");

        // ── Selector de tipo (fija el tipo por defecto de los IMEIs que se escaneen) ──
        // El listener se enlaza más abajo, cuando ya existen los paneles que muestra/oculta.
        ToggleButton tbRep    = new ToggleButton("Reparación");
        ToggleButton tbGlass  = new ToggleButton("Glass");
        ToggleButton tbPulido = new ToggleButton("Pulido");
        tbRep.getStyleClass().add("toggle-pill-left");
        tbGlass.getStyleClass().add("toggle-pill-mid");
        tbPulido.getStyleClass().add("toggle-pill-right");
        tbRep.setSelected(true);
        javafx.scene.control.ToggleGroup tgTipo = new javafx.scene.control.ToggleGroup();
        tbRep.setToggleGroup(tgTipo);
        tbGlass.setToggleGroup(tgTipo);
        tbPulido.setToggleGroup(tgTipo);
        HBox selectorTipo = new HBox(0, tbRep, tbGlass, tbPulido);
        selectorTipo.setAlignment(Pos.CENTER_LEFT);

        Label lblScan = new Label("Escanear IMEI → pendiente de asignar");
        lblScan.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
        tfScan.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-padding: 11; -fx-text-fill: #2C3B54; -fx-font-size: 14px;");
        Label lblScanErr = new Label();
        lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");

        // ── Secciones de la pila (cada una con su propio scroll) ─────────────
        Label lblRojo = new Label("Pendiente de asignar (0)");
        lblRojo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
        VBox boxRojo = new VBox(0);
        boxRojo.setStyle("-fx-background-color: white;");
        ScrollPane scrollRojo = new ScrollPane(boxRojo);
        scrollRojo.setFitToWidth(true);
        scrollRojo.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRojo.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollRojo.setMaxHeight(220);
        scrollRojo.setStyle("-fx-background-color: white; -fx-background: white;"
                + " -fx-border-color: #EFC4C0; -fx-border-radius: 6; -fx-border-width: 1;");

        Label lblVerde = new Label("Asignados (0) · sin guardar");
        lblVerde.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2E7D32; -fx-padding: 10 0 0 0;");
        VBox boxVerde = new VBox(0);
        boxVerde.setStyle("-fx-background-color: white;");
        ScrollPane scrollVerde = new ScrollPane(boxVerde);
        scrollVerde.setFitToWidth(true);
        scrollVerde.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollVerde.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollVerde.setMaxHeight(220);
        scrollVerde.setStyle("-fx-background-color: white; -fx-background: white;"
                + " -fx-border-color: #BFE0C2; -fx-border-radius: 6; -fx-border-width: 1;");

        VBox pilaBox = new VBox(6, lblRojo, scrollRojo, lblVerde, scrollVerde);
        pilaBox.setMinWidth(300);
        pilaBox.setPrefWidth(300);

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

        // ── Maquinaria de cliente (buscador) ─────────────────────────────────
        Label lblCliente = new Label("Cliente (opcional)");
        lblCliente.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        javafx.collections.ObservableList<Cliente> todosClientes =
                FXCollections.observableArrayList(clientesModal);
        // Opción "sin cliente" (id -1) arriba del desplegable, como en SelectorClienteDialog.
        final Cliente SIN_CLIENTE = new Cliente(-1, "— Sin cliente —", true, null);
        todosClientes.add(0, SIN_CLIENTE);
        FilteredList<Cliente> clientesFiltrados = new FilteredList<>(todosClientes, c -> true);
        TextField tfCliente = new TextField();
        tfCliente.setPromptText("Escribe cliente...");
        tfCliente.setMaxWidth(Double.MAX_VALUE);
        tfCliente.setStyle(
                "-fx-background-color: #001232; -fx-background-radius: 24;" +
                "-fx-border-color: transparent; -fx-border-radius: 24; -fx-border-width: 0;" +
                "-fx-text-fill: #FAFAFA; -fx-prompt-text-fill: rgba(255,255,255,0.45);" +
                "-fx-font-size: 12px; -fx-font-weight: bold;" +
                "-fx-padding: 4 12 4 12;");
        ListView<Cliente> listaClientes = new ListView<>(clientesFiltrados);
        listaClientes.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");
        listaClientes.setFixedCellSize(30);
        listaClientes.setPrefWidth(344);
        listaClientes.setCellFactory(lv -> new ListCell<>() {
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
            @Override protected void updateItem(Cliente cli, boolean empty) {
                super.updateItem(cli, empty);
                if (empty || cli == null) { setText(null); setStyle(""); }
                else { setText(cli.getNombre());
                    setStyle("-fx-background-color: white; -fx-text-fill: #001232;" +
                            "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12 6 12;"); }
            }
        });
        javafx.stage.Popup popupCliente = new javafx.stage.Popup();
        popupCliente.setAutoHide(true);
        popupCliente.getContent().add(listaClientes);
        Cliente[] clienteSel = { null };
        boolean[] actualizandoCliente = { false };
        boolean[] sinClienteSel = { false };   // estado comprometido: "sin cliente" explícito

        Runnable mostrarPopupCliente = () -> {
            if (clientesFiltrados.isEmpty() || tfCliente.getScene() == null) { popupCliente.hide(); return; }
            listaClientes.setPrefHeight(Math.min(clientesFiltrados.size(), 6) * 30 + 4);
            if (!popupCliente.isShowing()) {
                javafx.geometry.Bounds b = tfCliente.localToScreen(tfCliente.getBoundsInLocal());
                if (b != null) popupCliente.show(tfCliente, b.getMinX(), b.getMaxY() + 1);
            }
        };
        java.util.function.Consumer<Cliente> confirmarCliente = cli -> {
            boolean sin = cli.getIdCli() == -1;
            clienteSel[0] = sin ? null : cli;
            sinClienteSel[0] = sin;
            actualizandoCliente[0] = true;
            tfCliente.setText(cli.getNombre());   // el nombre del sentinel ya es "— Sin cliente —"
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
            popupCliente.hide();
        };

        tfCliente.textProperty().addListener((obs, oldText, newText) -> {
            if (actualizandoCliente[0]) return;
            String lower = newText == null ? "" : newText.trim().toLowerCase();
            clientesFiltrados.setPredicate(c -> lower.isEmpty()
                    || c.getNombre().toLowerCase().contains(lower));
            mostrarPopupCliente.run();
        });
        tfCliente.setOnAction(e -> {
            if (!clientesFiltrados.isEmpty()) confirmarCliente.accept(clientesFiltrados.get(0));
        });
        tfCliente.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) javafx.application.Platform.runLater(() -> {
                popupCliente.hide();
                String texto = tfCliente.getText() == null ? "" : tfCliente.getText().trim();
                // Coincidencia exacta (cliente real o el sentinel "— Sin cliente —") → confirmar.
                Cliente exacto = todosClientes.stream()
                        .filter(c -> c.getNombre().equalsIgnoreCase(texto))
                        .findFirst().orElse(null);
                if (exacto != null) { confirmarCliente.accept(exacto); return; }
                // Texto a medias / borrado / no coincide → restaurar el estado comprometido.
                actualizandoCliente[0] = true;
                tfCliente.setText(clienteSel[0] != null ? clienteSel[0].getNombre()
                        : (sinClienteSel[0] ? "— Sin cliente —" : ""));
                clientesFiltrados.setPredicate(c -> true);
                actualizandoCliente[0] = false;
            });
        });
        listaClientes.setOnMouseClicked(e -> {
            Cliente sel = listaClientes.getSelectionModel().getSelectedItem();
            if (sel != null) confirmarCliente.accept(sel);
        });
        listaClientes.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Cliente sel = listaClientes.getSelectionModel().getSelectedItem();
                if (sel != null) confirmarCliente.accept(sel);
            }
        });

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
        HBox accionesForm = new HBox(10, btnAsignar);

        VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, lblModelo, tfModelo,
                headerTecnicos, scrollTecnicos, lblNotaPersist, lblCliente, tfCliente,
                lblComentario, tfComentario, accionesForm);
        formBox.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-border-width: 1; -fx-padding: 16;");
        HBox.setHgrow(formBox, javafx.scene.layout.Priority.ALWAYS);
        formBox.setDisable(true);

        // ── Barra final ──────────────────────────────────────────────────────
        Label lblProg = new Label("");
        lblProg.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        javafx.scene.layout.Region spacerBarra = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerBarra, javafx.scene.layout.Priority.ALWAYS);
        Button btnGuardar = new Button("Guardar (0)");
        btnGuardar.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 14px;"
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
        Runnable[] recomputeOcupados = new Runnable[1];

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
            java.util.Comparator<EntradaAsignacion> porSeqDesc = (a, b) -> Long.compare(b.seq, a.seq);
            List<EntradaAsignacion> activa = pilaActiva.get();
            List<EntradaAsignacion> rojos  = activa.stream().filter(x -> !x.asignada).sorted(porSeqDesc).collect(java.util.stream.Collectors.toList());
            List<EntradaAsignacion> verdes = activa.stream().filter(x ->  x.asignada).sorted(porSeqDesc).collect(java.util.stream.Collectors.toList());
            int nRojo = rojos.size(), nVerde = verdes.size();
            for (EntradaAsignacion e : rojos) {
                Runnable onClick = () -> cargarEntrada[0].accept(e);
                Runnable onRemove = () -> {
                    activa.remove(e);
                    if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
                    renderPila[0].run();
                };
                boxRojo.getChildren().add(crearFilaPila(e, e == actual[0], onClick, onRemove));
            }
            for (EntradaAsignacion e : verdes) {
                Runnable onClick = () -> cargarEntrada[0].accept(e);
                Runnable onRemove = () -> {
                    activa.remove(e);
                    if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
                    renderPila[0].run();
                };
                boxVerde.getChildren().add(crearFilaPila(e, e == actual[0], onClick, onRemove));
            }
            lblRojo.setText("Pendiente de asignar (" + nRojo + ")");
            lblVerde.setText("Asignados (" + nVerde + ") · sin guardar");
            scrollRojo.setPrefHeight(nRojo == 0 ? 34 : Math.min(nRojo, 5) * 39 + 4);
            scrollVerde.setPrefHeight(nVerde == 0 ? 34 : Math.min(nVerde, 5) * 39 + 4);
            // Totales globales (rep + glass) para la barra inferior y el botón Guardar.
            int nRojoGlobal  = (int) (pilaRep.stream().filter(x -> !x.asignada).count()
                                    + pilaGlass.stream().filter(x -> !x.asignada).count());
            int nVerdeGlobal = (int) (pilaRep.stream().filter(x -> x.asignada).count()
                                    + pilaGlass.stream().filter(x -> x.asignada).count());
            int sinModelo    = (int) (pilaRep.stream().filter(e -> !e.asignada && !e.tieneModelo()).count()
                                    + pilaGlass.stream().filter(e -> !e.asignada && !e.tieneModelo()).count());
            int nPul = lotePulido.size();
            lblProg.setText(nVerdeGlobal + " configurados · " + nRojoGlobal + " pendientes"
                    + (nPul > 0 ? " · " + nPul + " pulido" : "")
                    + (sinModelo > 0 ? " · " + sinModelo + " sin modelo" : ""));
            btnGuardar.setText("Guardar (" + (nVerdeGlobal + nPul) + ")");
            btnGuardar.setDisable(nRojoGlobal != 0 || (nVerdeGlobal + nPul) == 0);
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
                Integer idCli = null;
                try { modelo = telefonoDAO.getModelo(e.imei); } catch (Exception ignore) {}
                try { idCli = telefonoDAO.getClienteId(e.imei); } catch (Exception ignore) {}
                String res = modelo;
                Integer idCliRes = idCli;
                javafx.application.Platform.runLater(() -> {
                    e.buscando = false;
                    if (res != null && !res.isEmpty()) {
                        e.modeloCode = res;
                        if (actual[0] == e) confirmarModelo.accept(res);
                    } else if (actual[0] == e) {
                        tfModelo.setPromptText("No encontrado — selecciona manualmente");
                    }
                    // Precargar el cliente que el IMEI ya tuviera en BD (prevalece sobre el arrastre)
                    if (idCliRes != null && e.cliente == null && !e.sinCliente) {
                        Cliente existente = todosClientes.stream()
                                .filter(c -> c.getIdCli() == idCliRes).findFirst().orElse(null);
                        if (existente != null) {
                            e.cliente = existente;
                            if (actual[0] == e) {
                                clienteSel[0] = existente;
                                actualizandoCliente[0] = true;
                                tfCliente.setText(existente.getNombre());
                                clientesFiltrados.setPredicate(c -> true);
                                actualizandoCliente[0] = false;
                            }
                        }
                    }
                    renderPila[0].run();
                });
            });
            t.setDaemon(true);
            t.start();
        };

        recomputeOcupados[0] = () -> {
            EntradaAsignacion e = actual[0];
            if (e == null) return;
            java.util.Set<Integer> ocupados = tecnicosOcupados(e.imei, e.tipo);   // per-categoría, desde la tabla cargada
            for (int i = 0; i < tecnicosModal.size(); i++) {
                boolean ocup = ocupados.contains(tecnicosModal.get(i).getIdTec());
                checkboxes.get(i).setDisable(ocup);
                if (ocup) checkboxes.get(i).setSelected(false);
            }
            long n = checkboxes.stream().filter(CheckBox::isDisabled).count();
            pillAsignados.setText(n + (n == 1 ? " asignado" : " asignados"));
            pillAsignados.setVisible(n >= 1);
            pillAsignados.setManaged(n >= 1);
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
            tfComentario.setText(e.comentario);   // el comentario NO se mantiene entre IMEIs (se resetea)
            // Cliente: por IMEI, NO se arrastra (a diferencia de los técnicos). Parte de lo que
            // tenga la entrada; si es nueva, vacío (y la precarga de BD lo rellenará si el IMEI ya tenía uno).
            clienteSel[0] = e.cliente;
            sinClienteSel[0] = e.sinCliente;
            actualizandoCliente[0] = true;
            tfCliente.setText(e.cliente != null ? e.cliente.getNombre()
                    : (e.sinCliente ? "— Sin cliente —" : ""));
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
            recomputeOcupados[0].run();   // greying per-categoría según e.tipo
            btnAsignar.setText(e.asignada ? "Guardar cambios" : "Asignar →");
            if (!e.asignada) lanzarLookup[0].run();
            validarForm.run();
            renderPila[0].run();   // re-pinta la pila para mover el indicador de selección a la fila cargada
        };

        Runnable cargarSiguienteRojo = () -> {
            EntradaAsignacion sig = pilaActiva.get().stream().filter(x -> !x.asignada)
                    .max(java.util.Comparator.comparingLong(x -> x.seq)).orElse(null);
            if (sig != null) cargarEntrada[0].accept(sig);
            else { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
        };

        List<Runnable> refrescadoresClientePulido = new ArrayList<>();
        // Propaga el cliente (o "sin cliente") a todas las entradas/filas del mismo IMEI en las 3 colas.
        TriConsumerCliente propagarCliente = (imei, cliente, sin) -> {
            for (EntradaAsignacion x : pilaRep)   if (x.imei.equals(imei)) { x.cliente = cliente; x.sinCliente = sin; }
            for (EntradaAsignacion x : pilaGlass) if (x.imei.equals(imei)) { x.cliente = cliente; x.sinCliente = sin; }
            for (FilaPulido f : lotePulido)       if (f.imei.equals(imei))  { f.cliente = cliente; f.sinCliente = sin; }
            refrescadoresClientePulido.forEach(Runnable::run);
            renderPila[0].run();
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
            e.cliente = clienteSel[0];
            e.sinCliente = sinClienteSel[0];
            propagarCliente.accept(e.imei, e.cliente, e.sinCliente);
            e.asignada = true;
            // seq NO cambia al asignar: rojo y verde se ordenan por orden de escaneo → mismo orden en ambas
            defTecnicos.clear(); defTecnicos.addAll(sel);   // solo los técnicos se mantienen entre IMEIs
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
            if (pilaActiva.get().stream().anyMatch(x -> x.imei.equals(imei))) {
                lblScanErr.setText("Ese IMEI ya está en la cola (" + tipoActual[0].etiqueta() + ")."); return; }
            lblScanErr.setText("");
            EntradaAsignacion e = new EntradaAsignacion(imei);
            e.tipo = tipoActual[0];
            e.seq = ++seqCounter[0];
            pilaActiva.get().add(e);
            renderPila[0].run();
            cargarEntrada[0].accept(e);
            // clear()/requestFocus en runLater: hacerlo síncrono dentro del listener de texto corrompe el caret
            javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) {
                String solo = n.replaceAll("[^\\d]", "");
                javafx.application.Platform.runLater(() -> tfScan.setText(solo));
                return;
            }
            if (n.length() > 15) {
                com.reparaciones.utils.ImeiUtils.ResultadoPegado res =
                        com.reparaciones.utils.ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == com.reparaciones.utils.ImeiUtils.TipoPegado.CORRUPTO) {
                    javafx.application.Platform.runLater(() -> {
                        tfScan.clear();
                        lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                                + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");
                        lblScanErr.setText("Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.");
                    });
                    return;
                }
                int anadidos = 0, duplicados = 0;
                for (String imei : res.imeis()) {
                    if (pilaActiva.get().stream().anyMatch(x -> x.imei.equals(imei))) { duplicados++; continue; }
                    EntradaAsignacion en = new EntradaAsignacion(imei);
                    en.tipo = tipoActual[0];
                    en.seq = ++seqCounter[0];
                    pilaActiva.get().add(en);
                    anadidos++;
                }
                renderPila[0].run();
                final String resumen = anadidos + " IMEIs añadidos"
                        + (duplicados > 0 ? " · " + duplicados + " ya estaban en la lista." : ".");
                javafx.application.Platform.runLater(() -> {
                    tfScan.clear();
                    tfScan.requestFocus();
                    lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-min-height: 15;");
                    lblScanErr.setText(resumen);
                });
                return;
            }
            lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");
            lblScanErr.setText("");
            if (n.length() == 15) intentarAnadir.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) intentarAnadir.run(); });

        checkboxes.forEach(cb -> cb.selectedProperty().addListener((obs, o, n) -> validarForm.run()));
        btnAsignar.setOnAction(ev -> asignarActual.run());

        // ── Layout + ventana ─────────────────────────────────────────────────
        HBox cols = new HBox(18, pilaBox, formBox);
        VBox richArea = new VBox(12, lblScan, tfScan, lblScanErr, new Separator(), cols);   // reparación + glass
        VBox pulidoPane = construirPulidoPane(lotePulido, tecnicosModal,
                () -> { if (renderPila[0] != null) renderPila[0].run(); },
                fila -> propagarCliente.accept(fila.imei, fila.cliente, fila.sinCliente),
                refrescadoresClientePulido);
        pulidoPane.setVisible(false); pulidoPane.setManaged(false);
        javafx.scene.layout.StackPane centro = new javafx.scene.layout.StackPane(richArea, pulidoPane);
        javafx.scene.layout.StackPane.setAlignment(richArea, Pos.TOP_LEFT);
        javafx.scene.layout.StackPane.setAlignment(pulidoPane, Pos.TOP_LEFT);

        // Selector 3-way: rep/glass → pila rica; pulido → sub-pila ligera. Ambos lotes persisten.
        tgTipo.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o == null ? tbRep : (ToggleButton) o).setSelected(true); return; }  // no permitir deselección
            boolean pulido = (n == tbPulido);
            richArea.setVisible(!pulido);  richArea.setManaged(!pulido);
            pulidoPane.setVisible(pulido); pulidoPane.setManaged(pulido);
            if (!pulido) tipoActual[0] = (n == tbGlass) ? TipoTrabajo.GLASS : TipoTrabajo.REPARACION;
            // El detalle cargado pertenece a la cola vieja: al cambiar de cola, se limpia.
            actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—");
            if (renderPila[0] != null) renderPila[0].run();
        });

        VBox contenido = new VBox(12, lblTitulo, lblSub, selectorTipo, centro, barraFinal);
        contenido.setPadding(new Insets(26));
        contenido.setPrefWidth(720);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ventana.setResizable(true);
        ventana.setMinWidth(720);
        ventana.setMinHeight(560);
        ventana.setTitle("Asignar trabajos");

        btnGuardar.setOnAction(ev -> {
            List<String> conflictos = new ArrayList<>();
            try {
                List<EntradaAsignacion> todas = new ArrayList<>(pilaRep);
                todas.addAll(pilaGlass);
                for (EntradaAsignacion e : todas) {
                    if (!e.asignada) continue;
                    String categoria = (e.tipo == TipoTrabajo.GLASS) ? "G" : "R";
                    Integer idCli = e.cliente != null ? e.cliente.getIdCli() : null;
                    telefonoDAO.insertar(e.imei, e.modeloCode, idCli, e.sinCliente);
                    String com = e.comentario.isEmpty() ? null : e.comentario;
                    boolean urgente = false;   // el urgente ya no se automatiza al asignar (lo hace el job por vencimiento)
                    for (Tecnico t : e.tecnicos) {
                        if (reparacionDAO.existeAsignacionParaTecnico(e.imei, t.getIdTec(), categoria)) {
                            conflictos.add("• " + e.imei + " → " + t.getNombre() + " (ya asignado · " + e.tipo.etiqueta() + ")");
                            continue;
                        }
                        if (e.tipo == TipoTrabajo.GLASS)
                            glassDAO.insertarAsignacionGlass(e.imei, t.getIdTec(), com, urgente);
                        else
                            reparacionDAO.insertarAsignacion(e.imei, t.getIdTec(), com, urgente);
                    }
                }
                for (FilaPulido f : lotePulido) {
                    if (f.tecnico == null) { conflictos.add("• " + f.imei + " → (sin técnico)"); continue; }
                    if (reparacionDAO.existeAsignacionParaTecnico(f.imei, f.tecnico.getIdTec(), "P")) {
                        conflictos.add("• " + f.imei + " → " + f.tecnico.getNombre() + " (ya asignado · Pulido)");
                        continue;
                    }
                    telefonoDAO.insertar(f.imei, null, f.cliente != null ? f.cliente.getIdCli() : null, f.sinCliente);
                    pulidoDAO.insertarAsignacionPulido(f.imei, f.tecnico.getIdTec(),
                            (f.comentario == null || f.comentario.isEmpty()) ? null : f.comentario);
                }
            } catch (SQLException ex) { mostrarError(ex); return; }
            ventana.close();
            cargar();
            if (!conflictos.isEmpty())
                new Alert(Alert.AlertType.WARNING, "Algunas asignaciones no se crearon:\n\n" + String.join("\n", conflictos)).showAndWait();
        });

        ventana.setOnCloseRequest(ev -> {
            int total = pilaRep.size() + pilaGlass.size() + lotePulido.size();
            if (total == 0) return;
            ev.consume();
            ConfirmDialog.mostrar("Descartar", "Se descartarán los " + total + " IMEIs escaneados.",
                    "Descartar", ventana::close);
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        renderPila[0].run();
        javafx.application.Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }

    /** Conservado para el caller externo (cambio de pestaña): ya no hay staging, solo recarga. */
    public void resetearCambios() {
        cargar();
    }

    private void actualizarContador() {
        if (lblContador == null || datosFiltrados == null) return;
        int n = datosFiltrados.size();
        lblContador.setText((n > 999 ? "999+" : String.valueOf(n)) + (n == 1 ? " asignación" : " asignaciones"));
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
            try {
                if (tipoDe(rep.getIdRep()) == TipoTrabajo.PULIDO)
                    pulidoDAO.actualizarAsignacionPulido(rep.getIdRep(), rep.getIdTec(), nuevoTexto, rep.getUpdatedAt());
                else
                    reparacionDAO.actualizarAsignacion(rep.getIdRep(), rep.getIdTec(), nuevoTexto, rep.getUpdatedAt());
                ventana.close();
                cargar();
            } catch (com.reparaciones.utils.StaleDataException ex) {
                Alertas.mostrarError("La asignación fue modificada por otro usuario. Se recargan los datos.");
                ventana.close();
                cargar();
            } catch (SQLException ex) {
                Alertas.mostrarError(ex.getMessage());
            }
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        javafx.application.Platform.runLater(ta::requestFocus);
        ventana.showAndWait();
    }

    /** Selector de modelo para una asignación de pulido (no pasa por el modal de piezas). */
    private void abrirSelectorModelo(ReparacionResumen rep) {
        javafx.collections.ObservableList<String> todos =
                javafx.collections.FXCollections.observableArrayList(
                        FormularioReparacionController.MODELOS_ORDENADOS);
        FilteredList<String> filtrados = new FilteredList<>(todos, s -> true);

        TextField tfFiltro = new TextField();
        tfFiltro.setPromptText("Filtrar modelo…");
        tfFiltro.textProperty().addListener((obs, o, n) -> {
            String lower = n == null ? "" : n.trim().toLowerCase();
            filtrados.setPredicate(c -> lower.isEmpty()
                    || FormularioReparacionController.traducirModelo(c).toLowerCase().contains(lower));
        });

        ListView<String> lista = new ListView<>(filtrados);
        lista.setPrefHeight(220);
        lista.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                setText((empty || m == null) ? null : FormularioReparacionController.traducirModelo(m));
            }
        });
        if (rep.getModelo() != null && !rep.getModelo().isEmpty()) {
            lista.getSelectionModel().select(rep.getModelo());
            lista.scrollTo(rep.getModelo());
        }

        Button btnConfirmar = new Button("Guardar");
        Button btnCancelar  = new Button("Cancelar");
        btnConfirmar.disableProperty().bind(lista.getSelectionModel().selectedItemProperty().isNull());

        HBox botones = new HBox(10, btnCancelar, btnConfirmar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox contenido = new VBox(10,
                new Label("Selecciona el modelo:"), tfFiltro, lista, botones);
        contenido.setPadding(new Insets(20));
        contenido.setPrefWidth(320);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.setTitle("Editar modelo");
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);

        btnCancelar.setOnAction(ev -> ventana.close());
        btnConfirmar.setOnAction(ev -> {
            String codigoInterno = lista.getSelectionModel().getSelectedItem();
            if (codigoInterno == null) return;
            try {
                telefonoDAO.insertar(rep.getImei(), codigoInterno);
                ventana.close();
                cargar();
            } catch (SQLException ex) { mostrarError(ex); }
        });

        ventana.showAndWait();
    }

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
