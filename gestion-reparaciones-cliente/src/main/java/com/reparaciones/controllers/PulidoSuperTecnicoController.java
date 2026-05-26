package com.reparaciones.controllers;

import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.StaleDataException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PulidoSuperTecnicoController {

    @FXML private TableView<ReparacionResumen>           tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private MenuButton filtroTecnico;
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

    private final List<CheckBox> cbsTecnico = new ArrayList<>();
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
            {
                cb.setMaxWidth(Double.MAX_VALUE);
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
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    ReparacionResumen rep = getTableView().getItems().get(getIndex());
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
                    setGraphic(null); setStyle(""); return;
                }
                actualizando = true;
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
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
                    String texto = textoDeCelda(getItem(), colRightClick[0]);
                    if (texto == null || texto.isEmpty()) return;
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(texto);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
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
                    ConfirmDialog.mostrar("Borrar asignación " + rep.getIdRep(),
                            "El técnico dejará de verla en su lista de pendientes.",
                            "Borrar asignación", () -> {
                                try {
                                    pulidoDAO.eliminarAsignacionPulido(rep.getIdRep());
                                    cambiosPendientes.remove(rep.getIdRep());
                                    actualizarVisibilidadConfirmar();
                                    cargar();
                                } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
                            });
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
    }

    // ─── Filtros ──────────────────────────────────────────────────────────────

    private void configurarFiltros() {
        try {
            tecnicos.addAll(tecnicoDAO.getAllActivos());
            for (Tecnico t : tecnicos) {
                CheckBox cb = new CheckBox(t.getNombre());
                cb.setStyle("-fx-font-size: 12px; -fx-padding: 2 4 2 4;");
                cb.selectedProperty().addListener((obs, o, n) -> {
                    actualizarTextoFiltroTecnico();
                    aplicarFiltros();
                });
                cbsTecnico.add(cb);
                CustomMenuItem item = new CustomMenuItem(cb, false);
                filtroTecnico.getItems().add(item);
            }
        } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }

        filtroImei.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) filtroImei.setText(n.replaceAll("[^\\d]", ""));
            if (filtroImei.getText().length() > 15)
                filtroImei.setText(filtroImei.getText().substring(0, 15));
            aplicarFiltros();
        });
    }

    private void actualizarTextoFiltroTecnico() {
        long sel = cbsTecnico.stream().filter(CheckBox::isSelected).count();
        filtroTecnico.setText(sel == 0 ? "Técnico" : sel == 1
                ? cbsTecnico.stream().filter(CheckBox::isSelected).findFirst().map(CheckBox::getText).orElse("Técnico")
                : sel + " técnicos");
    }

    private void aplicarFiltros() {
        if (datosFiltrados == null) return;
        List<Integer> idsTecSelec = new ArrayList<>();
        for (int i = 0; i < cbsTecnico.size(); i++)
            if (cbsTecnico.get(i).isSelected()) idsTecSelec.add(tecnicos.get(i).getIdTec());
        String imeiStr = filtroImei.getText().trim();
        datosFiltrados.setPredicate(rep -> {
            if (imeiStr.length() == 15 && !rep.getImei().equals(imeiStr)) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            return true;
        });
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        cbsTecnico.forEach(cb -> cb.setSelected(false));
        filtroTecnico.setText("Técnico");
    }

    // ─── Carga ────────────────────────────────────────────────────────────────

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
        // ── Cabecera ──────────────────────────────────────────────────────────
        Label lblTitulo = new Label("Asignar pulidos");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label lblSub = new Label("El técnico y comentario se mantienen entre escaneos.");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");

        // ── Técnico ───────────────────────────────────────────────────────────
        Label lblTecnico = new Label("Técnico a asignar");
        lblTecnico.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<Tecnico> cbTecnico = new ComboBox<>();
        cbTecnico.setMaxWidth(Double.MAX_VALUE);
        cbTecnico.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
            @Override public Tecnico fromString(String s) { return null; }
        });
        try { cbTecnico.getItems().addAll(tecnicoDAO.getAllActivos()); }
        catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }

        // ── Comentario (opcional, sticky) ────────────────────────────────────
        Label lblComentario = new Label("Comentario (opcional)");
        lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea tfComentario = new TextArea();
        tfComentario.setWrapText(true);
        tfComentario.setPrefRowCount(2);
        tfComentario.setPromptText("Instrucciones para el técnico...");
        tfComentario.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        // ── IMEI (auto-envío) ─────────────────────────────────────────────────
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
        lblError.setMaxWidth(384);

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
        scroll.setPrefHeight(160);
        scroll.setStyle("-fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-border-width: 1;" +
                " -fx-background: white; -fx-background-color: white;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        Button btnGuardar = new Button("Guardar");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);
        btnGuardar.getStyleClass().add("btn-primary");

        // ── Lógica de envío ───────────────────────────────────────────────────
        int[] contador = {0};
        boolean[] enviando = {false};
        List<String> idAPs = new ArrayList<>();
        Runnable intentarEnviar = () -> {
            if (enviando[0]) return;
            String imei = tfImei.getText().trim();
            if (imei.length() != 15) return;
            Tecnico tec = cbTecnico.getValue();
            if (tec == null) { lblError.setText("Selecciona un técnico primero."); return; }
            enviando[0] = true;
            try {
                String comentario = tfComentario.getText().trim();
                telefonoDAO.insertar(imei);
                String idAP = pulidoDAO.insertarAsignacionPulido(imei, tec.getIdTec(),
                        comentario.isEmpty() ? null : comentario);
                idAPs.add(idAP);
                contador[0]++;
                if (contador[0] == 1) listaItems.getChildren().remove(lblPlaceholder);

                Label lblImeiItem = new Label(imei);
                lblImeiItem.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3B54;");
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                Button btnX = new Button("✕");
                btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: #B0B8C8;" +
                        " -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
                btnX.setOnMouseEntered(e -> btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: " +
                        com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD +
                        "; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
                btnX.setOnMouseExited(e -> btnX.setStyle("-fx-background-color: transparent; -fx-text-fill: #B0B8C8;" +
                        " -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
                HBox fila = new HBox(8, lblImeiItem, spacer, btnX);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.setStyle("-fx-padding: 5 8 5 8; -fx-border-color: transparent transparent #E8EAF0 transparent;" +
                        " -fx-border-width: 0 0 1 0;");
                btnX.setOnAction(ev -> {
                    try {
                        if (idAP != null) { pulidoDAO.eliminarAsignacionPulido(idAP); idAPs.remove(idAP); }
                        listaItems.getChildren().remove(fila);
                        contador[0]--;
                        if (contador[0] == 0) listaItems.getChildren().add(0, lblPlaceholder);
                        actualizarTituloLista(lblListaTitulo, contador[0]);
                    } catch (SQLException ex) { lblError.setText(ex.getMessage()); }
                });
                listaItems.getChildren().add(fila);
                javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0));

                actualizarTituloLista(lblListaTitulo, contador[0]);
                lblError.setText("");
                javafx.application.Platform.runLater(() -> {
                    tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: #8AC7AF;");
                    tfImei.clear();
                    tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: #C2C8D0;");
                    tfImei.requestFocus();
                    enviando[0] = false;
                });
            } catch (SQLException ex) {
                lblError.setText(ex.getMessage());
                tfImei.setStyle(IMEI_STYLE_BASE + "-fx-border-color: " +
                        com.reparaciones.utils.Colores.FILA_INCIDENCIA_BRD + ";");
                enviando[0] = false;
            }
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
        contenido.setPrefWidth(440);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        javafx.stage.Stage ventana = new javafx.stage.Stage();
        ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ventana.setResizable(true);
        ventana.setMinWidth(440);
        ventana.setMinHeight(500);
        ventana.setTitle("Asignar pulidos");
        btnGuardar.setOnAction(ev -> { ventana.close(); cargar(); });
        ventana.setOnCloseRequest(ev -> {
            if (contador[0] == 0) return;
            ev.consume();
            ConfirmDialog.mostrar(
                    "Descartar cambios",
                    "Se eliminarán los " + contador[0] + " IMEI" + (contador[0] == 1 ? "" : "s") + " escaneados.",
                    "Descartar",
                    () -> {
                        for (String id : new ArrayList<>(idAPs)) {
                            try { pulidoDAO.eliminarAsignacionPulido(id); }
                            catch (SQLException ex) { /* ignorar errores individuales al descartar */ }
                        }
                        ventana.close();
                    });
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
}
