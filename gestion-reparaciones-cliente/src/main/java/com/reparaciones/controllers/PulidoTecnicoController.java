package com.reparaciones.controllers;

import com.reparaciones.dao.PulidoDAO;
import com.reparaciones.models.ReparacionResumen;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PulidoTecnicoController {

    @FXML private TableView<ReparacionResumen>            tablaPulidos;
    @FXML private TableColumn<ReparacionResumen, Boolean> cCheck;
    @FXML private TableColumn<ReparacionResumen, String>  cId;
    @FXML private TableColumn<ReparacionResumen, String>  cImei;
    @FXML private TableColumn<ReparacionResumen, String>  cModelo;
    @FXML private TableColumn<ReparacionResumen, String>  cFecha;
    @FXML private TableColumn<ReparacionResumen, String>  cComentario;
    @FXML private TextField filtroImei;
    @FXML private Button    btnCompletarSeleccionados;
    @FXML private Label     lblUltimaActualizacion;

    private final PulidoDAO pulidoDAO = new PulidoDAO();

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
    @FXML private void seleccionarTodo() {}
    @FXML private void completarSeleccionados() {}
}
