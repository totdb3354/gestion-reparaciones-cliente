package com.reparaciones.controllers;

import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.ReparacionResumen;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PulidoAdminController {

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

    private final PulidoDAO  pulidoDAO  = new PulidoDAO();
    private final TecnicoDAO tecnicoDAO = new TecnicoDAO();

    private final ObservableList<ReparacionResumen> datos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        tablaPulidos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaPulidos.setItems(datos);
    }

    public void cargar() {
        // TODO
    }

    @FXML private void limpiarFiltros() {}
    @FXML private void abrirFormularioAsignacion() {}
    @FXML private void confirmarCambios() {}
    @FXML private void resetearCambios() {}
}
