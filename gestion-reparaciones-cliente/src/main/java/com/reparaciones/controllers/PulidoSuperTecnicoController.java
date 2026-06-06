package com.reparaciones.controllers;

import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.MultiSelectComboBox;
import com.reparaciones.utils.StaleDataException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.stage.Popup;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PulidoSuperTecnicoController {

    @FXML private TableView<ReparacionResumen>           tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private TextField  filtroImei;
    @FXML private Label      lblUltimaActualizacion;
    @FXML private Button     btnConfirmarCambios;
    @FXML private Button     btnDescartarCambios;

    private final PulidoDAO   pulidoDAO   = new PulidoDAO();
    private final TecnicoDAO  tecnicoDAO  = new TecnicoDAO();
    private final TelefonoDAO telefonoDAO = new TelefonoDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;

    private final Set<Integer>   idsTecFiltro  = new HashSet<>();
    private final StringProperty etiquetaTec   = new SimpleStringProperty("Técnico");
    private ListView<Tecnico>    listaTecFiltro;
    private final List<Tecnico>  tecnicos   = new ArrayList<>();
    private record CambioPendiente(int idTec, String nombreTecnico, String comentarioAsignacion,
                                   java.time.LocalDateTime updatedAt) {}
    private final Map<String, CambioPendiente> cambiosPendientes = new HashMap<>();

    @FXML
    public void initialize() {
        cId.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));

        cTecnico.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<Tecnico> cb = new ComboBox<>();
            private boolean actualizando = false;
            private ReparacionResumen repMostrado = null;
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
                        hoverProperty().addListener((obs, o, n) -> estiloItem());
                        selectedProperty().addListener((obs, o, n) -> estiloItem());
                    }
                    private void estiloItem() {
                        if (isEmpty() || getItem() == null) { setStyle(""); return; }
                        setStyle(isSelected() || isHover()
                            ? "-fx-background-color: #001232; -fx-background-radius: 8; -fx-text-fill: #FAFAFA;"
                            : "-fx-text-fill: #001232;");
                    }
                    @Override protected void updateItem(Tecnico t, boolean empty) {
                        super.updateItem(t, empty);
                        if (empty || t == null) { setText(null); setStyle(""); return; }
                        setText(t.getNombre()); estiloItem();
                    }
                });
                cb.setOnAction(e -> {
                    if (actualizando) return;
                    if (repMostrado == null) return;
                    ReparacionResumen rep = repMostrado;
                    Tecnico sel = cb.getValue();
                    if (sel == null) return;
                    if (sel.getIdTec() != rep.getIdTec()) {
                        String comentarioActual = cambiosPendientes.containsKey(rep.getIdRep())
                            ? cambiosPendientes.get(rep.getIdRep()).comentarioAsignacion()
                            : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
                        cambiosPendientes.put(rep.getIdRep(),
                            new CambioPendiente(sel.getIdTec(), sel.getNombre(), comentarioActual, rep.getUpdatedAt()));
                    } else {
                        CambioPendiente existing = cambiosPendientes.get(rep.getIdRep());
                        if (existing != null) {
                            String repCom = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                            String extCom = existing.comentarioAsignacion() != null ? existing.comentarioAsignacion() : "";
                            if (!extCom.equals(repCom)) {
                                cambiosPendientes.put(rep.getIdRep(),
                                    new CambioPendiente(rep.getIdTec(), rep.getNombreTecnico(),
                                        existing.comentarioAsignacion(), rep.getUpdatedAt()));
                            } else {
                                cambiosPendientes.remove(rep.getIdRep());
                            }
                        }
                    }
                    actualizarVisibilidadConfirmar();
                    CambioPendiente cambioActual = cambiosPendientes.get(rep.getIdRep());
                    boolean mod = cambioActual != null && cambioActual.idTec() != rep.getIdTec();
                    setStyle(mod ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";" : "");
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                if (cb.isShowing()) return;
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    repMostrado = null; setGraphic(null); setStyle(""); return;
                }
                actualizando = true;
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                repMostrado = rep;
                cb.getItems().setAll(tecnicos);
                CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
                Tecnico mostrar = cambio != null
                    ? tecnicos.stream().filter(t -> t.getIdTec() == cambio.idTec()).findFirst().orElse(null)
                    : tecnicos.stream().filter(t -> t.getIdTec() == rep.getIdTec()).findFirst().orElse(null);
                cb.setValue(mostrar);
                actualizando = false;
                boolean modificada = cambio != null && cambio.idTec() != rep.getIdTec();
                setStyle(modificada ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";" : "");
                setGraphic(cb);
            }
        });

        cImei.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getImei()));
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cFecha.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getFechaAsig() != null ? d.getValue().getFechaAsig().format(FMT) : ""));
        cComentario.setCellValueFactory(d -> {
            ReparacionResumen rep = d.getValue();
            CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
            String texto = cambio != null ? cambio.comentarioAsignacion()
                                          : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
            return new javafx.beans.property.SimpleStringProperty(texto);
        });
        cComentario.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null); setStyle(""); return;
                }
                setText(item);
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                CambioPendiente cambio = cambiosPendientes.get(rep.getIdRep());
                String repCom    = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
                String cambioCom = cambio != null ? (cambio.comentarioAsignacion() != null ? cambio.comentarioAsignacion() : "") : repCom;
                boolean modificado = cambio != null && !cambioCom.equals(repCom);
                setStyle(modificado ? "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_MODIFICADA_BG + ";" : "");
            }
        });

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPulidos.setItems(datosFiltrados);
        tablaPulidos.setColumnResizePolicy(param -> true);

        Image imgEditar = new Image(getClass().getResourceAsStream("/images/editar.png"));
        tablaPulidos.setRowFactory(tv -> new TableRow<>() {
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
                    String actual = cambiosPendientes.containsKey(rep.getIdRep())
                        ? cambiosPendientes.get(rep.getIdRep()).comentarioAsignacion()
                        : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
                    abrirEditorComentario(rep, actual);
                });
                menu.getItems().add(editarComentario);
                MenuItem editarModelo = new MenuItem("Editar modelo");
                ImageView ivEditarModelo = new ImageView(imgEditar);
                ivEditarModelo.setFitWidth(14); ivEditarModelo.setFitHeight(14); ivEditarModelo.setPreserveRatio(true);
                editarModelo.setGraphic(ivEditarModelo);
                editarModelo.setOnAction(e -> {
                    if (getItem() == null) return;
                    abrirSelectorModelo(getItem());
                });
                menu.getItems().add(editarModelo);
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
                if (isEmpty() || getItem() == null) { setStyle(""); return; }
                if (isSelected()) {
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                            "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                            "-fx-border-width: 0 0 1 0;");
                } else {
                    setStyle("-fx-border-width: 0 0 1 0; -fx-border-color: transparent transparent " +
                            com.reparaciones.utils.Colores.FILA_SEP + " transparent;");
                }
            }
            @Override protected void updateItem(ReparacionResumen item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
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
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    ReparacionResumen rep = getTableView().getItems().get(getIndex());
                    try {
                        pulidoDAO.eliminarAsignacionPulido(rep.getIdRep());
                        cambiosPendientes.remove(rep.getIdRep());
                        actualizarVisibilidadConfirmar();
                        cargar();
                    } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tablaPulidos.getColumns().forEach(c -> c.setReorderable(false));
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
        try {
            tecnicos.addAll(tecnicoDAO.getAllActivos());
            filtroTecnico.setButtonCell(new ListCell<>() {
                { etiquetaTec.addListener((obs, o, n) -> setText(n)); setText(etiquetaTec.get()); }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty); setText(etiquetaTec.get());
                }
            });
            listaTecFiltro = new ListView<>(FXCollections.observableArrayList(tecnicos));
            listaTecFiltro.setMaxHeight(Math.min(tecnicos.size(), 8) * 30.0);
            listaTecFiltro.setCellFactory(lv -> new ListCell<>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        int id = getItem().getIdTec();
                        if (idsTecFiltro.contains(id)) idsTecFiltro.remove(id);
                        else idsTecFiltro.add(id);
                        listaTecFiltro.refresh();
                        actualizarTextoFiltroTecnico();
                        aplicarFiltros();
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) { setGraphic(null); setText(null); return; }
                    check.setSelected(idsTecFiltro.contains(t.getIdTec()));
                    setGraphic(check);
                    setText(t.getNombre());
                }
            });
            VBox popupContenedor = new VBox(listaTecFiltro);
            popupContenedor.getStyleClass().addAll("combo-box-popup", "multi-select-popup");
            popupContenedor.setMinWidth(filtroTecnico.getPrefWidth());
            Popup popupTec = new Popup();
            popupTec.setAutoHide(true);
            popupTec.getContent().add(popupContenedor);
            filtroTecnico.setCustomPopup(popupTec);
        } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            String withoutSep = n.replace(", ", ",");
            String limpio = withoutSep.replaceAll("[^\\d,]", "").replaceAll(",+", ",").replaceAll("^,", "");
            if (!limpio.equals(withoutSep)) { String can = limpio.replace(",", ", "); javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); }); return; }
            String[] partes = n.split(",", -1);
            if (partes[partes.length - 1].trim().length() == 15 && !n.endsWith(", ") && !n.endsWith(",")) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(n + ", "); filtroImei.positionCaret(filtroImei.getText().length()); }); return;
            }
            boolean hayIncompleto = java.util.Arrays.stream(n.split(",", -1))
                    .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(s -> s.length() < 15);
            boolean hayValido = !parsearImeis(n).isEmpty();
            if (n.trim().isEmpty())
                filtroImei.setStyle("");
            else if (hayIncompleto)
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else if (hayValido)
                filtroImei.setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.FONDO_INPUT + "; -fx-border-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";" +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 10; -fx-font-size: 12px;");
            else
                filtroImei.setStyle("");
            aplicarFiltros();
        });
    }

    private void actualizarTextoFiltroTecnico() {
        List<String> nombres = tecnicos.stream()
                .filter(t -> idsTecFiltro.contains(t.getIdTec()))
                .map(Tecnico::getNombre).toList();
        etiquetaTec.set(nombres.isEmpty() ? "Técnico"
                : nombres.size() == 1 ? nombres.get(0)
                : nombres.size() + " técnicos");
    }

    private void aplicarFiltros() {
        if (datosFiltrados == null) return;
        List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
        java.util.Set<String> imeisFiltro = parsearImeis(filtroImei.getText().trim());
        datosFiltrados.setPredicate(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            return true;
        });
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        idsTecFiltro.clear();
        if (listaTecFiltro != null) listaTecFiltro.refresh();
        etiquetaTec.set("Técnico");
        aplicarFiltros();
    }

    private static java.util.Set<String> parsearImeis(String texto) {
        if (texto == null || texto.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ─── Carga ────────────────────────────────────────────────────────────────

    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return datosFiltrados != null ? new java.util.ArrayList<>(datosFiltrados) : java.util.List.of();
    }

    public int getTotalItems() { return datos.size(); }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    public void cargar() {
        try {
            tablaPulidos.getSelectionModel().clearSelection();
            datos.setAll(pulidoDAO.getAsignacionesPulido());
            String hora = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    // ─── Nueva asignación ─────────────────────────────────────────────────────

    @FXML
    private void abrirFormularioAsignacion() {
        Image imgEditar = new Image(getClass().getResourceAsStream("/images/editar.png"));

        // ── Cabecera ──────────────────────────────────────────────────────────
        Label lblTitulo = new Label("Asignar pulidos");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label lblSub = new Label("Los valores por defecto se aplican al escanear. Edítalos individualmente en la lista.");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");

        // ── Técnico (por defecto) ─────────────────────────────────────────────
        Label lblTecnico = new Label("Técnico por defecto");
        lblTecnico.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<Tecnico> cbTecnico = new ComboBox<>();
        cbTecnico.setMaxWidth(Double.MAX_VALUE);
        cbTecnico.setVisibleRowCount(8);
        cbTecnico.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
            @Override public Tecnico fromString(String s) { return null; }
        });
        try { cbTecnico.getItems().addAll(tecnicoDAO.getAllActivos()); }
        catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }

        // ── Comentario (por defecto) ──────────────────────────────────────────
        Label lblComentario = new Label("Comentario por defecto (opcional)");
        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea tfComentario = new TextArea();
        tfComentario.setWrapText(true);
        tfComentario.setPrefRowCount(2);
        tfComentario.setPromptText("Instrucciones para el técnico...");
        tfComentario.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        // ── IMEI ──────────────────────────────────────────────────────────────
        Label lblImei = new Label("IMEI");
        lblImei.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfImei = new TextField();
        tfImei.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
        String IMEI_STYLE_BASE = "-fx-background-color: white; -fx-border-radius: 4; -fx-background-radius: 4;" +
                " -fx-padding: 10; -fx-text-fill: #2C3B54; -fx-font-size: 14px;";
        tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: #C2C8D0;");
        Label lblError = new Label();
        lblError.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR +
                "; -fx-wrap-text: true;");
        lblError.setMaxWidth(424);

        // ── Lista en vivo ─────────────────────────────────────────────────────
        Label lblListaTitulo = new Label("Nada añadido aún");
        lblListaTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #586376;");
        Label lblPlaceholder = new Label("Los IMEIs escaneados aparecerán aquí");
        lblPlaceholder.setStyle("-fx-font-size: 11px; -fx-text-fill: #B0B8C8; -fx-font-style: italic;" +
                " -fx-padding: 10 0 10 10;");
        VBox listaItems = new VBox(0, lblPlaceholder);
        listaItems.setStyle("-fx-background-color: white;");
        ScrollPane scroll = new ScrollPane(listaItems);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(200);
        scroll.setStyle("-fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-border-width: 1;" +
                " -fx-background: white; -fx-background-color: white;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        Button btnGuardar = new Button("Guardar");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.disableProperty().bind(cbTecnico.valueProperty().isNull());

        // ── Datos del lote ────────────────────────────────────────────────────
        record ImeiConf(String imei, Tecnico[] tec, String[] com) {}
        List<ImeiConf> lote = new ArrayList<>();
        int[] contador = {0};

        // ── Lógica de envío ───────────────────────────────────────────────────
        Runnable intentarEnviar = () -> {
            String imei = tfImei.getText().trim();
            if (imei.length() != 15) return;
            if (lote.stream().anyMatch(e -> e.imei().equals(imei))) {
                lblError.setText("Este IMEI ya está en la lista."); return;
            }
            Tecnico tec = cbTecnico.getValue();
            if (tec == null) { lblError.setText("Selecciona un técnico primero."); return; }

            Tecnico[] tecSel = {tec};
            String[] comSel = {tfComentario.getText().trim()};
            ImeiConf entry = new ImeiConf(imei, tecSel, comSel);
            lote.add(entry);
            contador[0]++;
            if (contador[0] == 1) listaItems.getChildren().remove(lblPlaceholder);

            // ── Fila con editor expandible por IMEI ───────────────────────────
            boolean[] expandido = {false};
            double EDITOR_H = 120.0;

            Label lblImeiRow = new Label(imei);
            lblImeiRow.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3B54; -fx-font-weight: bold;" +
                    " -fx-font-family: monospace;");

            Label lblTecPill = new Label(tecSel[0].getNombre());
            lblTecPill.setStyle("-fx-background-color: #001232; -fx-text-fill: white;" +
                    " -fx-font-size: 10px; -fx-padding: 2 8 2 8; -fx-background-radius: 20;");

            String snipIni = comSel[0].isEmpty() ? "Sin comentario" :
                    (comSel[0].length() > 22 ? comSel[0].substring(0, 19) + "..." : comSel[0]);
            Label lblComSnip = new Label(snipIni);
            lblComSnip.setStyle(comSel[0].isEmpty()
                    ? "-fx-font-size: 10px; -fx-text-fill: #B0B8C8; -fx-font-style: italic;"
                    : "-fx-font-size: 10px; -fx-text-fill: #586376;");

            ImageView ivEditRow = new ImageView(imgEditar);
            ivEditRow.setFitWidth(12); ivEditRow.setFitHeight(12); ivEditRow.setPreserveRatio(true);
            Button btnEditRow = new Button();
            btnEditRow.setGraphic(ivEditRow);
            btnEditRow.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4;");
            btnEditRow.setOnMouseEntered(e -> btnEditRow.setStyle(
                    "-fx-background-color: #EEF1F5; -fx-cursor: hand; -fx-padding: 4; -fx-background-radius: 4;"));
            btnEditRow.setOnMouseExited(e -> btnEditRow.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4;"));

            Button btnX = new Button("✕");
            btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: #B0B8C8;" +
                    " -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
            btnX.setOnMouseEntered(e -> btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: " +
                    com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD +
                    "; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
            btnX.setOnMouseExited(e -> btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: #B0B8C8;" +
                    " -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));

            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            HBox cabecera = new HBox(8, lblImeiRow, lblTecPill, spacer, lblComSnip, btnEditRow, btnX);
            cabecera.setAlignment(Pos.CENTER_LEFT);
            cabecera.setPrefHeight(42);
            cabecera.setStyle("-fx-padding: 0 8 0 8;");

            // Editor
            ComboBox<Tecnico> cbEdit = new ComboBox<>();
            cbEdit.getItems().addAll(tecnicos);
            cbEdit.setValue(tecSel[0]);
            cbEdit.setMaxWidth(Double.MAX_VALUE);
            cbEdit.setVisibleRowCount(8);
            cbEdit.setStyle("-fx-font-size: 12px;");
            cbEdit.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
                @Override public Tecnico fromString(String s) { return null; }
            });

            TextField tfComEdit = new TextField(comSel[0]);
            tfComEdit.setPromptText("Comentario...");
            tfComEdit.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                    "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 12px; -fx-padding: 6;");

            Button btnAplicar = new Button("Aplicar");
            btnAplicar.getStyleClass().add("btn-primary");
            Button btnCancelEdit = new Button("Cancelar");
            btnCancelEdit.getStyleClass().add("btn-secondary");
            HBox botonesEdit = new HBox(8, btnCancelEdit, btnAplicar);
            botonesEdit.setAlignment(Pos.CENTER_RIGHT);

            VBox editor = new VBox(6, cbEdit, tfComEdit, botonesEdit);
            editor.setStyle("-fx-background-color: #EEF1F5; -fx-padding: 8 12 8 12;");
            editor.setPrefHeight(0);
            editor.setMinHeight(0);
            editor.setMaxHeight(0);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(10000, 0);
            editor.setClip(clip);

            Runnable toggleEditor = () -> {
                if (!expandido[0]) {
                    expandido[0] = true;
                    editor.setMaxHeight(EDITOR_H);
                    new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(200),
                            new javafx.animation.KeyValue(editor.prefHeightProperty(), EDITOR_H,
                                    javafx.animation.Interpolator.EASE_BOTH),
                            new javafx.animation.KeyValue(clip.heightProperty(), EDITOR_H,
                                    javafx.animation.Interpolator.EASE_BOTH))
                    ).play();
                } else {
                    expandido[0] = false;
                    javafx.animation.Timeline t2 = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(150),
                            new javafx.animation.KeyValue(editor.prefHeightProperty(), 0,
                                    javafx.animation.Interpolator.EASE_BOTH),
                            new javafx.animation.KeyValue(clip.heightProperty(), 0,
                                    javafx.animation.Interpolator.EASE_BOTH))
                    );
                    t2.setOnFinished(ev2 -> editor.setMaxHeight(0));
                    t2.play();
                }
            };

            btnEditRow.setOnAction(e -> toggleEditor.run());

            btnAplicar.setOnAction(e -> {
                Tecnico nuevoTec = cbEdit.getValue();
                if (nuevoTec == null) return;
                tecSel[0] = nuevoTec;
                comSel[0] = tfComEdit.getText().trim();
                lblTecPill.setText(tecSel[0].getNombre());
                String snip = comSel[0].isEmpty() ? "Sin comentario" :
                        (comSel[0].length() > 22 ? comSel[0].substring(0, 19) + "..." : comSel[0]);
                lblComSnip.setText(snip);
                lblComSnip.setStyle(comSel[0].isEmpty()
                        ? "-fx-font-size: 10px; -fx-text-fill: #B0B8C8; -fx-font-style: italic;"
                        : "-fx-font-size: 10px; -fx-text-fill: #586376;");
                toggleEditor.run();
            });

            btnCancelEdit.setOnAction(e -> {
                cbEdit.setValue(tecSel[0]);
                tfComEdit.setText(comSel[0]);
                toggleEditor.run();
            });

            VBox[] filaRef = {null};
            btnX.setOnAction(ev -> {
                lote.remove(entry);
                listaItems.getChildren().remove(filaRef[0]);
                contador[0]--;
                if (contador[0] == 0) listaItems.getChildren().add(0, lblPlaceholder);
                actualizarTituloLista(lblListaTitulo, contador[0]);
            });

            VBox filaContenedor = new VBox(0, cabecera, editor);
            filaContenedor.setStyle("-fx-background-color: white;" +
                    " -fx-border-color: transparent transparent #E8EAF0 transparent;" +
                    " -fx-border-width: 0 0 1 0;");
            filaRef[0] = filaContenedor;

            listaItems.getChildren().add(filaContenedor);
            javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0));

            actualizarTituloLista(lblListaTitulo, contador[0]);
            lblError.setText("");
            javafx.application.Platform.runLater(() -> {
                tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: #8AC7AF;");
                tfImei.clear();
                tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: #C2C8D0;");
                tfImei.requestFocus();
            });
        };

        tfImei.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) {
                String solo = n.replaceAll("[^\\d]", "");
                javafx.application.Platform.runLater(() -> tfImei.setText(solo));
                return;
            }
            if (n.length() > 15) {
                String recortado = n.substring(0, 15);
                javafx.application.Platform.runLater(() -> tfImei.setText(recortado));
                return;
            }
            lblError.setText("");
            if (n.length() == 15) intentarEnviar.run();
        });
        tfImei.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) intentarEnviar.run();
        });

        // ── Layout ────────────────────────────────────────────────────────────
        VBox contenido = new VBox(12,
                lblTitulo, lblSub,
                lblTecnico, cbTecnico,
                lblComentario, tfComentario,
                new javafx.scene.control.Separator(),
                lblImei, tfImei, lblError,
                new javafx.scene.control.Separator(),
                lblListaTitulo, scroll,
                btnGuardar);
        contenido.setPadding(new Insets(28));
        contenido.setPrefWidth(480);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ventana.setResizable(true);
        ventana.setMinWidth(480);
        ventana.setMinHeight(540);
        ventana.setTitle("Asignar pulidos");
        btnGuardar.setOnAction(ev -> {
            if (lote.isEmpty()) { ventana.close(); return; }
            try {
                for (ImeiConf e : lote) {
                    telefonoDAO.insertar(e.imei());
                    pulidoDAO.insertarAsignacionPulido(e.imei(), e.tec()[0].getIdTec(),
                            (e.com()[0] == null || e.com()[0].isEmpty()) ? null : e.com()[0]);
                }
                ventana.close();
                cargar();
            } catch (SQLException ex) {
                lblError.setText(ex.getMessage());
            }
        });
        ventana.setOnCloseRequest(ev -> {
            if (lote.isEmpty()) return;
            ev.consume();
            ConfirmDialog.mostrar(
                    "Descartar cambios",
                    "Se descartarán los " + lote.size() + " IMEI" + (lote.size() == 1 ? "" : "s") + " escaneados.",
                    "Descartar",
                    () -> ventana.close());
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        javafx.application.Platform.runLater(cbTecnico::requestFocus);
        ventana.showAndWait();
    }

    private void actualizarTituloLista(Label lbl, int n) {
        if (n == 0) {
            lbl.setText("Nada añadido aún");
            lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #586376;");
        } else {
            lbl.setText(n + " añadido" + (n == 1 ? "" : "s"));
            lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        }
    }

    // ─── Confirmar / Descartar ────────────────────────────────────────────────

    @FXML
    private void confirmarCambios() {
        List<String> conflictos = new ArrayList<>();
        for (Map.Entry<String, CambioPendiente> entry : new ArrayList<>(cambiosPendientes.entrySet())) {
            String idRep = entry.getKey();
            CambioPendiente cambio = entry.getValue();
            ReparacionResumen rep = datos.stream()
                    .filter(r -> r.getIdRep().equals(idRep)).findFirst().orElse(null);
            if (rep == null) continue;
            try {
                pulidoDAO.actualizarAsignacionPulido(idRep, cambio.idTec(),
                        cambio.comentarioAsignacion(), cambio.updatedAt());
                rep.setIdTec(cambio.idTec());
                rep.setNombreTecnico(cambio.nombreTecnico());
                rep.setComentarioAsignacion(cambio.comentarioAsignacion());
                cambiosPendientes.remove(idRep);
            } catch (StaleDataException e) {
                conflictos.add("• " + idRep + ": fue modificada por otro usuario.");
            } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
        }
        if (!conflictos.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                    "Los siguientes cambios no se pudieron guardar:\n\n" + String.join("\n", conflictos) +
                    "\n\nLos datos se han recargado.").showAndWait();
        }
        actualizarVisibilidadConfirmar();
        cargar();
    }

    @FXML
    public void resetearCambios() {
        cambiosPendientes.clear();
        actualizarVisibilidadConfirmar();
        cargar();
    }

    private void actualizarVisibilidadConfirmar() {
        boolean hay = !cambiosPendientes.isEmpty();
        if (btnConfirmarCambios != null) { btnConfirmarCambios.setVisible(hay); btnConfirmarCambios.setManaged(hay); }
        if (btnDescartarCambios != null) { btnDescartarCambios.setVisible(hay); btnDescartarCambios.setManaged(hay); }
    }

    private void abrirEditorComentario(ReparacionResumen rep, String textoActual) {
        Label lblTitulo = new Label("Comentario de asignación");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        TextArea ta = new TextArea(textoActual);
        ta.setWrapText(true);
        ta.setPrefRowCount(4);
        ta.setPrefWidth(340);
        ta.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");
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
            CambioPendiente existing = cambiosPendientes.get(rep.getIdRep());
            int idTecActual      = existing != null ? existing.idTec() : rep.getIdTec();
            String nomTecActual  = existing != null ? existing.nombreTecnico() : rep.getNombreTecnico();
            String repComentario = rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "";
            if (idTecActual == rep.getIdTec() && nuevoTexto.equals(repComentario)) {
                cambiosPendientes.remove(rep.getIdRep());
            } else {
                cambiosPendientes.put(rep.getIdRep(),
                    new CambioPendiente(idTecActual, nomTecActual, nuevoTexto, rep.getUpdatedAt()));
            }
            actualizarVisibilidadConfirmar();
            tablaPulidos.refresh();
            ventana.close();
        });
        javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        javafx.application.Platform.runLater(ta::requestFocus);
        ventana.showAndWait();
    }

    private String textoDeCelda(ReparacionResumen rep, TableColumn<?, ?> col) {
        if (col == cId)        return rep.getIdRep();
        if (col == cImei)      return rep.getImei();
        if (col == cModelo)    { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == cFecha)     return rep.getFechaAsig() != null ? rep.getFechaAsig().format(FMT) : "";
        if (col == cComentario){ CambioPendiente c = cambiosPendientes.get(rep.getIdRep());
            return c != null ? c.comentarioAsignacion() : (rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : ""); }
        return null;
    }

    private void abrirSelectorModelo(ReparacionResumen rep) {
        javafx.collections.ObservableList<String> todos =
                javafx.collections.FXCollections.observableArrayList(
                        com.reparaciones.controllers.FormularioReparacionController.MODELOS_ORDENADOS);
        javafx.collections.transformation.FilteredList<String> filtrados =
                new javafx.collections.transformation.FilteredList<>(todos, s -> true);

        javafx.scene.control.TextField tfFiltro = new javafx.scene.control.TextField();
        tfFiltro.setPromptText("Filtrar modelo…");
        tfFiltro.textProperty().addListener((obs, o, n) -> {
            String lower = n == null ? "" : n.trim().toLowerCase();
            filtrados.setPredicate(c -> lower.isEmpty()
                    || com.reparaciones.controllers.FormularioReparacionController
                            .traducirModelo(c).toLowerCase().contains(lower));
        });

        javafx.scene.control.ListView<String> lista = new javafx.scene.control.ListView<>(filtrados);
        lista.setPrefHeight(220);
        lista.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                setText((empty || m == null) ? null
                        : com.reparaciones.controllers.FormularioReparacionController.traducirModelo(m));
            }
        });
        if (rep.getModelo() != null && !rep.getModelo().isEmpty()) {
            lista.getSelectionModel().select(rep.getModelo());
            lista.scrollTo(rep.getModelo());
        }

        javafx.scene.control.Button btnConfirmar = new javafx.scene.control.Button("Guardar");
        javafx.scene.control.Button btnCancelar  = new javafx.scene.control.Button("Cancelar");
        btnConfirmar.disableProperty().bind(lista.getSelectionModel().selectedItemProperty().isNull());

        javafx.scene.layout.HBox botones = new javafx.scene.layout.HBox(10, btnCancelar, btnConfirmar);
        botones.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        javafx.scene.layout.VBox contenido = new javafx.scene.layout.VBox(10,
                new javafx.scene.control.Label("Selecciona el modelo:"),
                tfFiltro, lista, botones);
        contenido.setPadding(new javafx.geometry.Insets(20));
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
                new com.reparaciones.dao.TelefonoDAO().insertar(rep.getImei(), codigoInterno);
                ventana.close();
                cargar();
            } catch (java.sql.SQLException ex) {
                com.reparaciones.utils.Alertas.mostrarError(ex.getMessage());
            }
        });

        ventana.showAndWait();
    }
}
