package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HistorialPulidoController {

    @FXML private TableView<ReparacionResumen>           tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cModelo;
    @FXML private TableColumn<ReparacionResumen, String> cTecnico;
    @FXML private TableColumn<ReparacionResumen, String> cFechaIni;
    @FXML private TableColumn<ReparacionResumen, String> cFechaFin;
    @FXML private TableColumn<ReparacionResumen, String> cComentario;
    @FXML private MenuButton filtroTecnico;
    @FXML private TextField  filtroImei;
    @FXML private DatePicker filtroFechaDesde;
    @FXML private DatePicker filtroFechaHasta;
    @FXML private Label      lblUltimaActualizacion;

    private final PulidoDAO  pulidoDAO  = new PulidoDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();
    private FilteredList<ReparacionResumen> datosFiltrados;

    private final List<CheckBox> cbsTecnico = new ArrayList<>();
    private final List<Tecnico>  tecnicos   = new ArrayList<>();

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
                if (com.reparaciones.Sesion.esSuperTecnico()) {
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
                    menu.getItems().addAll(borrar, new SeparatorMenuItem(), copiar);
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

        filtroFechaDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
        filtroFechaHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
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
        LocalDate desde = filtroFechaDesde.getValue();
        LocalDate hasta = filtroFechaHasta.getValue();
        datosFiltrados.setPredicate(rep -> {
            if (imeiStr.length() == 15 && !rep.getImei().equals(imeiStr)) return false;
            if (!idsTecSelec.isEmpty() && !idsTecSelec.contains(rep.getIdTec())) return false;
            if (desde != null && rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isBefore(desde)) return false;
            if (hasta != null && rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isAfter(hasta)) return false;
            return true;
        });
    }

    @FXML
    private void limpiarFiltros() {
        filtroImei.clear();
        cbsTecnico.forEach(cb -> cb.setSelected(false));
        filtroTecnico.setText("Técnico");
        filtroFechaDesde.setValue(null);
        filtroFechaHasta.setValue(null);
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
