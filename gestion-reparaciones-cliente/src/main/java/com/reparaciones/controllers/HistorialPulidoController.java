package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.MultiSelectComboBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HistorialPulidoController {

    @FXML private TableView<ReparacionResumen>           tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cFechaIni;
    @FXML private TableColumn<ReparacionResumen, String> cFechaFin;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private MultiSelectComboBox<Tecnico> filtroTecnico;
    @FXML private TextField  filtroImei;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private Label      lblUltimaActualizacion;
    @FXML private Label      lblContadorPulidos;

    private final PulidoDAO  pulidoDAO  = new PulidoDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;

    private final List<Tecnico>    tecnicos      = new ArrayList<>();
    private final Set<Integer>     idsTecFiltro  = new HashSet<>();
    private final StringProperty   etiquetaTec   = new SimpleStringProperty("Técnico");
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;

    @FXML
    public void initialize() {
        cId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cImei.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getImei()));
        cModelo.setCellValueFactory(d -> {
            String m = d.getValue().getModelo();
            return new javafx.beans.property.SimpleStringProperty(
                (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : "");
        });
        cTecnico.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().getNombreTecnico()));
        cFechaIni.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getFechaAsig() != null ? d.getValue().getFechaAsig().format(FMT) : ""));
        cFechaFin.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getFechaFin() != null ? d.getValue().getFechaFin().format(FMT) : ""));
        cComentario.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getComentarioAsignacion() != null ? d.getValue().getComentarioAsignacion() : ""));

        datosFiltrados = new FilteredList<>(datos, p -> true);
        tablaPulidos.setItems(datosFiltrados);
        tablaPulidos.setColumnResizePolicy(param -> true);
        datosFiltrados.addListener((javafx.collections.ListChangeListener<ReparacionResumen>) c -> actualizarContadorPulidos());
        actualizarContadorPulidos();

        javafx.scene.image.Image imgEditar = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/editar.png"));
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
                if (com.reparaciones.Sesion.esSuperTecnico()) {
                    MenuItem editarModelo = new MenuItem("Editar modelo");
                    javafx.scene.image.ImageView ivEditarModelo = new javafx.scene.image.ImageView(imgEditar);
                    ivEditarModelo.setFitWidth(14); ivEditarModelo.setFitHeight(14); ivEditarModelo.setPreserveRatio(true);
                    editarModelo.setGraphic(ivEditarModelo);
                    editarModelo.setOnAction(e -> {
                        if (getItem() == null) return;
                        abrirSelectorModelo(getItem());
                    });
                    MenuItem borrar = new MenuItem("Borrar");
                    borrar.setOnAction(e -> {
                        if (getItem() == null) return;
                        try {
                            pulidoDAO.eliminarPulido(getItem().getIdRep());
                            cargar();
                        } catch (java.sql.SQLException ex) {
                            Alertas.mostrarError(ex.getMessage());
                        }
                    });
                    menu.getItems().addAll(editarModelo, borrar, new SeparatorMenuItem(), copiar);
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

    private void configurarFiltros() {
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

        filtroFechaDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
        filtroFechaHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
    }

    private void actualizarTextoFiltroTecnico() {
        int sel = idsTecFiltro.size();
        if (sel == 0) {
            etiquetaTec.set("Técnico");
        } else if (sel == 1) {
            int id = idsTecFiltro.iterator().next();
            String nombre = tecnicos.stream().filter(t -> t.getIdTec() == id).findFirst().map(Tecnico::getNombre).orElse("Técnico");
            etiquetaTec.set(nombre);
        } else {
            etiquetaTec.set(sel + " técnicos");
        }
    }

    private void aplicarFiltros() {
        if (datosFiltrados == null) return;
        List<Integer> idsTecSelec = new ArrayList<>(idsTecFiltro);
        String imeiStr = filtroImei.getText().trim();
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        java.util.Set<String> imeisFiltro = parsearImeis(imeiStr);
        datosFiltrados.setPredicate(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            if (desde != null && rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isBefore(desde)) return false;
            if (hasta != null && rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isAfter(hasta)) return false;
            return true;
        });
    }

    private void actualizarContadorPulidos() {
        if (lblContadorPulidos == null || datosFiltrados == null) return;
        int n = datosFiltrados.size();
        lblContadorPulidos.setText((n > 999 ? "999+" : String.valueOf(n)) + (n == 1 ? " pulido" : " pulidos"));
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        filtroImei.setStyle("");
        idsTecFiltro.clear();
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        etiquetaTec.set("Técnico");
        filtroFechaDesde.setValue(null);
        filtroFechaHasta.setValue(null);
        aplicarFiltros();
    }

    private static java.util.Set<String> parsearImeis(String texto) {
        if (texto == null || texto.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(java.util.stream.Collectors.toSet());
    }

    public java.util.List<ReparacionResumen> getItemsVisibles() {
        return datosFiltrados != null ? new java.util.ArrayList<>(datosFiltrados) : java.util.List.of();
    }

    public String getFiltroImei() { return filtroImei.getText(); }
    public void setFiltroImei(String imei) { filtroImei.setText(imei != null ? imei : ""); }

    public void cargar() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (Sesion.esAdminOSuperTecnico()) {
                datos.setAll(pulidoDAO.getHistorialPulido());
            } else if (idTec != null) {
                datos.setAll(pulidoDAO.getHistorialPulidoPorTecnico(idTec));
            }
            String hora = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActualizacion != null) lblUltimaActualizacion.setText("Actualizado " + hora);
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    private void abrirSelectorModelo(ReparacionResumen rep) {
        javafx.collections.ObservableList<String> todos =
                javafx.collections.FXCollections.observableArrayList(
                        FormularioReparacionController.MODELOS_ORDENADOS);
        javafx.collections.transformation.FilteredList<String> filtrados =
                new javafx.collections.transformation.FilteredList<>(todos, s -> true);

        javafx.scene.control.TextField tfFiltro = new javafx.scene.control.TextField();
        tfFiltro.setPromptText("Filtrar modelo…");
        tfFiltro.textProperty().addListener((obs, o, n) -> {
            String lower = n == null ? "" : n.trim().toLowerCase();
            filtrados.setPredicate(c -> lower.isEmpty()
                    || FormularioReparacionController.traducirModelo(c).toLowerCase().contains(lower));
        });

        javafx.scene.control.ListView<String> lista = new javafx.scene.control.ListView<>(filtrados);
        lista.setPrefHeight(220);
        lista.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                setText((empty || m == null) ? null : FormularioReparacionController.traducirModelo(m));
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
                Alertas.mostrarError(ex.getMessage());
            }
        });

        ventana.showAndWait();
    }

    private String textoDeCelda(ReparacionResumen rep, TableColumn<?, ?> col) {
        if (col == cId)        return rep.getIdRep();
        if (col == cImei)      return rep.getImei();
        if (col == cModelo)    { String m = rep.getModelo(); return (m != null && !m.isEmpty()) ? FormularioReparacionController.traducirModelo(m) : ""; }
        if (col == cTecnico)   return rep.getNombreTecnico();
        if (col == cFechaIni)  return rep.getFechaAsig() != null ? rep.getFechaAsig().format(FMT) : "";
        if (col == cFechaFin)  return rep.getFechaFin() != null ? rep.getFechaFin().format(FMT) : "";
        if (col == cComentario){ String c = rep.getComentarioAsignacion(); return c != null ? c : ""; }
        return null;
    }
}
