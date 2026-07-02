package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.FechaUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PulidoTecnicoController {

    @FXML private TableView<ReparacionResumen>            tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, Boolean> cCheck;
    @FXML private TableColumn<ReparacionResumen, String>  cId;
    @FXML private TableColumn<ReparacionResumen, String>  cImei;
    @FXML private TableColumn<ReparacionResumen, String>  cModelo;
    @FXML private TableColumn<ReparacionResumen, String>  cFecha;
    @FXML private TableColumn<ReparacionResumen, String>  cComentario;
    @FXML private TableColumn<ReparacionResumen, String>  cAsignadoPor;
    @FXML private TableColumn<ReparacionResumen, Void>    cBorrar;
    @FXML private TextField filtroImei;
    @FXML private Button    btnCompletarSeleccionados;
    @FXML private Label     lblUltimaActualizacion;
    @FXML private Label     lblContador;

    private final PulidoDAO pulidoDAO = new PulidoDAO();
    private final com.reparaciones.dao.TelefonoDAO telefonoDAO = new com.reparaciones.dao.TelefonoDAO();
    private final com.reparaciones.dao.ClienteDAO  clienteDAO  = new com.reparaciones.dao.ClienteDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;
    private final Set<String> seleccionados = new HashSet<>();
    private Runnable onCerrar;

    public void setOnCerrar(Runnable onCerrar) { this.onCerrar = onCerrar; }

    @FXML
    public void initialize() {
        cCheck.setCellValueFactory(d ->
            new javafx.beans.property.SimpleBooleanProperty(seleccionados.contains(d.getValue().getIdRep())));
        cCheck.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setOnAction(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    String id = getTableView().getItems().get(getIndex()).getIdRep();
                    if (cb.isSelected()) seleccionados.add(id);
                    else seleccionados.remove(id);
                    actualizarBotonCompletar();
                });
            }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                cb.setSelected(seleccionados.contains(getTableView().getItems().get(getIndex()).getIdRep()));
                setGraphic(cb);
            }
        });

        cId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cImei.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getImei()));
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cFecha.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            FechaUtils.formatear(d.getValue().getFechaAsig(), FMT)));
        cComentario.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getComentarioAsignacion() != null ? d.getValue().getComentarioAsignacion() : ""));
        cAsignadoPor.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getNombreTecnicoAsigna() != null ? d.getValue().getNombreTecnicoAsigna() : "—"));

        // Borrar asignación de pulido: solo SuperTécnico (para el Técnico normal la columna se oculta).
        if (Sesion.esSuperTecnico()) {
            javafx.scene.image.Image imgBorrar = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/borrar.png"));
            cBorrar.setCellFactory(col -> new TableCell<>() {
                private final javafx.scene.image.ImageView ivBorrar = new javafx.scene.image.ImageView(imgBorrar);
                private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(ivBorrar);
                {
                    ivBorrar.setFitWidth(25); ivBorrar.setFitHeight(25); ivBorrar.setPreserveRatio(true);
                    ivBorrar.setStyle("-fx-cursor: hand;");
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    ivBorrar.setOnMouseClicked(e -> {
                        ReparacionResumen rep = getTableView().getItems().get(getIndex());
                        com.reparaciones.utils.ConfirmDialog.mostrar("Borrar asignación " + rep.getIdRep(),
                                "El pulido dejará de estar asignado y desaparecerá de tus pendientes.",
                                "Borrar asignación", () -> {
                                    try {
                                        pulidoDAO.eliminarAsignacionPulido(rep.getIdRep());
                                        cargar();
                                        if (onCerrar != null) onCerrar.run();
                                    } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
                                });
                    });
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : box);
                }
            });
        } else {
            cBorrar.setVisible(false);
        }

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPulidos.setItems(datosFiltrados);
        datosFiltrados.addListener((javafx.collections.ListChangeListener<ReparacionResumen>) c -> actualizarContador());
        actualizarContador();
        tablaPulidos.setColumnResizePolicy(param -> true);
        tablaPulidos.getColumns().forEach(c -> c.setSortable(false));   // el orden lo llevan los filtros, no el clic en la cabecera

        tablaPulidos.setRowFactory(tv -> new TableRow<>() {
            {
                selectedProperty().addListener((obs, o, sel) -> actualizarEstilo());

                ContextMenu menu = new ContextMenu();
                TableColumn<?, ?>[] colRightClick = {null};
                MenuItem copiar = new MenuItem("📋  Copiar celda");
                copiar.setOnAction(e -> {
                    if (getItem() == null || colRightClick[0] == null) return;
                    String texto = textoDeCelda(getItem(), colRightClick[0]);
                    if (texto == null || texto.isEmpty()) return;
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(texto);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                });
                // Edición solo para SuperTécnico (como el borrado; el técnico normal solo copia).
                if (Sesion.esSuperTecnico()) {
                    MenuItem edCom = new MenuItem("Editar comentario");
                    edCom.setOnAction(e -> { if (getItem() != null) editarComentario(getItem()); });
                    MenuItem edMod = new MenuItem("Editar modelo");
                    edMod.setOnAction(e -> { if (getItem() != null) editarModelo(getItem()); });
                    MenuItem edCli = new MenuItem("Editar cliente");
                    edCli.setOnAction(e -> { if (getItem() != null) editarCliente(getItem()); });
                    menu.getItems().addAll(edCom, edMod, edCli, new SeparatorMenuItem(), copiar);
                } else {
                    menu.getItems().add(copiar);
                }
                setContextMenu(menu);
                setOnContextMenuRequested(e -> {
                    double x = e.getX(); double offset = 0;
                    for (TableColumn<?, ?> c : tv.getVisibleLeafColumns()) {
                        offset += c.getWidth();
                        if (x < offset) { colRightClick[0] = c; break; }
                    }
                });
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

        tablaPulidos.getColumns().forEach(c -> c.setReorderable(false));
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
            aplicarFiltro();
        });
        cargar();
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setCursor(javafx.scene.Cursor.HAND);
            lblUltimaActualizacion.setOnMouseClicked(e -> cargar());
            lblUltimaActualizacion.setOnMouseEntered(e -> lblUltimaActualizacion.setUnderline(true));
            lblUltimaActualizacion.setOnMouseExited(e -> lblUltimaActualizacion.setUnderline(false));
        }
    }

    private void aplicarFiltro() {
        if (datosFiltrados == null) return;
        java.util.Set<String> imeisFiltro = com.reparaciones.utils.FiltroImei.imeisValidos(filtroImei.getText().trim());
        datosFiltrados.setPredicate(rep -> imeisFiltro.isEmpty() || imeisFiltro.contains(rep.getImei()));
    }

    private void actualizarContador() {
        if (lblContador == null || datosFiltrados == null) return;
        int n = datosFiltrados.size();
        lblContador.setText((n > 999 ? "999+" : String.valueOf(n)) + (n == 1 ? " pendiente" : " pendientes"));
    }

    @FXML private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        aplicarFiltro();
    }

    @FXML
    private void seleccionarTodo() {
        if (seleccionados.size() == datos.size() && !datos.isEmpty()) {
            seleccionados.clear();
        } else {
            datos.forEach(r -> seleccionados.add(r.getIdRep()));
        }
        tablaPulidos.refresh();
        actualizarBotonCompletar();
    }

    @FXML
    private void completarSeleccionados() {
        if (seleccionados.isEmpty()) return;
        List<String> ids = List.copyOf(seleccionados);
        try {
            pulidoDAO.completarPulidoLote(ids);
            seleccionados.clear();
            cargar();
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    private void actualizarBotonCompletar() {
        if (btnCompletarSeleccionados != null)
            btnCompletarSeleccionados.setDisable(seleccionados.isEmpty());
    }

    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return datosFiltrados != null ? new java.util.ArrayList<>(datosFiltrados) : java.util.List.of();
    }

    public int getTotalItems() { return datos.size(); }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    // ── Menú contextual (SuperTécnico) ────────────────────────────────────────

    private String textoDeCelda(ReparacionResumen rep, TableColumn<?, ?> col) {
        if (col == cId)          return rep.getIdRep();
        if (col == cImei)        return rep.getImei();
        if (col == cModelo)      { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == cFecha)       return FechaUtils.formatear(rep.getFechaAsig(), FMT);
        if (col == cComentario)  { String c = rep.getComentarioAsignacion(); return c != null ? c : ""; }
        if (col == cAsignadoPor) { String a = rep.getNombreTecnicoAsigna(); return a != null ? a : ""; }
        return null;
    }

    private void editarComentario(ReparacionResumen rep) {
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(
                rep.getComentarioAsignacion() != null ? rep.getComentarioAsignacion() : "");
        ta.setWrapText(true); ta.setPrefRowCount(4); ta.setPrefWidth(340);
        ta.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");
        javafx.scene.control.Button guardar = new javafx.scene.control.Button("Guardar");
        guardar.getStyleClass().add("btn-primary");
        javafx.scene.control.Button cancelar = new javafx.scene.control.Button("Cancelar");
        cancelar.getStyleClass().add("btn-secondary");
        javafx.scene.layout.HBox botones = new javafx.scene.layout.HBox(10, cancelar, guardar);
        botones.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        javafx.scene.layout.VBox cont = new javafx.scene.layout.VBox(12,
                new Label("Comentario de asignación"), ta, botones);
        cont.setPadding(new javafx.geometry.Insets(24));
        cont.setStyle("-fx-background-color: #DDE1E7;");
        javafx.stage.Stage v = new javafx.stage.Stage();
        v.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        v.setTitle("Editar comentario");
        javafx.scene.Scene sc = new javafx.scene.Scene(cont);
        sc.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        v.setScene(sc);
        cancelar.setOnAction(e -> v.close());
        guardar.setOnAction(e -> {
            try {
                // Mismo técnico: no altera "Asignado por" (ID_TEC_ASIGNA).
                pulidoDAO.actualizarAsignacionPulido(rep.getIdRep(), rep.getIdTec(), ta.getText().trim(), rep.getUpdatedAt());
                v.close(); cargar(); if (onCerrar != null) onCerrar.run();
            } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
        });
        v.showAndWait();
    }

    private void editarModelo(ReparacionResumen rep) {
        SelectorModeloDialog.elegir(rep.getModelo()).ifPresent(codigo -> {
            try {
                telefonoDAO.insertar(rep.getImei(), codigo);
                cargar(); if (onCerrar != null) onCerrar.run();
            } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
        });
    }

    private void editarCliente(ReparacionResumen rep) {
        try {
            java.util.List<com.reparaciones.models.Cliente> activos = clienteDAO.getActivos();
            Integer idActual = activos.stream()
                    .filter(c -> c.getNombre().equals(rep.getCliente()))
                    .map(com.reparaciones.models.Cliente::getIdCli).findFirst().orElse(null);
            java.util.Optional<Integer> sel = com.reparaciones.utils.SelectorClienteDialog.elegir(activos, idActual);
            if (sel.isEmpty()) return;
            Integer idCli = (sel.get() == -1) ? null : sel.get();
            telefonoDAO.actualizarCliente(rep.getImei(), idCli, rep.getTelefonoUpdatedAt());
            cargar(); if (onCerrar != null) onCerrar.run();
        } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
    }

    public void cargar() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (idTec == null) return;
            seleccionados.clear();
            datos.setAll(pulidoDAO.getAsignacionesPulidoPorTecnico(idTec));
            tablaPulidos.refresh();
            actualizarBotonCompletar();
            String hora = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            if (!(e instanceof com.reparaciones.utils.ConexionException
                    && com.reparaciones.utils.ConexionEstado.enRefresco()))
                Alertas.mostrarError(e.getMessage());
        }
    }
}
