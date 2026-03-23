package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.models.ReparacionResumen;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PendientesTecnicoController {

    @FXML private TableView<ReparacionResumen>           tablaPendientes;
    @FXML private TableColumn<ReparacionResumen, String> cId;
    @FXML private TableColumn<ReparacionResumen, String> cImei;
    @FXML private TableColumn<ReparacionResumen, String> cFecha;
    @FXML private TableColumn<ReparacionResumen, Void>   cAccion;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private Runnable onCerrar;

    @FXML
    public void initialize() {
        tablaPendientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        cId.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().getIdRep()));
        cImei.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getImei())));
        cFecha.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                d.getValue().getFechaAsig() != null
                    ? d.getValue().getFechaAsig().format(FMT) : ""));

        // Colorear filas con incidencia activa
        tablaPendientes.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(ReparacionResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null && item.isEsIncidencia()) {
                    setStyle("-fx-background-color: rgba(251,136,136,0.16);" +
                             "-fx-border-color: transparent transparent #FB8888 transparent;" +
                             "-fx-border-width: 0 0 0.2 0;");
                } else setStyle("");
            }
        });

        cAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Añadir reparación");
            {
                btn.setStyle("-fx-background-color: #8AC7AF; -fx-text-fill: white;" +
                             "-fx-font-size: 11px; -fx-font-weight: bold;" +
                             "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
                btn.setOnAction(e -> {
                    ReparacionResumen asig = getTableView().getItems().get(getIndex());
                    // Cerrar pendientes primero
                    Stage stage = (Stage) tablaPendientes.getScene().getWindow();
                    stage.close();
                    // Abrir formulario — Platform.runLater interno en abrir()
                    FormularioReparacionController.abrir(
                            asig.getImei(), null, asig.getIdRep(), onCerrar);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        cargar();
    }

    private void cargar() {
        try {
            Integer idTec = Sesion.getIdTec();
            if (idTec == null) return;
            List<ReparacionResumen> lista = reparacionDAO.getAsignacionesPorTecnico(idTec);
            tablaPendientes.setItems(FXCollections.observableArrayList(lista));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setOnCerrar(Runnable onCerrar) {
        this.onCerrar = onCerrar;
    }

    // ─── Apertura del Stage desde fuera ──────────────────────────────────────

    public static void abrir(Runnable onCerrar) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    PendientesTecnicoController.class.getResource(
                            "/views/PendientesTecnicoView.fxml"));
            Parent root = loader.load();
            PendientesTecnicoController ctrl = loader.getController();
            ctrl.setOnCerrar(onCerrar);

            Stage stage = new Stage();
            stage.setTitle("Pendientes");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            // show en vez de showAndWait — no bloquea el hilo
            // onCerrar lo llama el formulario al guardar, no aquí
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}