package com.reparaciones.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Diálogo modal para elegir un modelo de teléfono de una lista filtrable.
 * Devuelve el código interno del modelo seleccionado, o vacío si se cancela.
 * En el paquete {@code controllers} para acceder a {@code MODELOS_ORDENADOS} y
 * {@code traducirModelo} de {@link FormularioReparacionController}. Reutilizable
 * entre las vistas que editan modelo (pulido, historial…).
 */
public final class SelectorModeloDialog {

    private SelectorModeloDialog() {}

    public static Optional<String> elegir(String modeloActual) {
        ObservableList<String> todos =
                FXCollections.observableArrayList(FormularioReparacionController.MODELOS_ORDENADOS);
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
        if (modeloActual != null && !modeloActual.isEmpty()) {
            lista.getSelectionModel().select(modeloActual);
            lista.scrollTo(modeloActual);
        }

        Button btnConfirmar = new Button("Guardar");
        Button btnCancelar  = new Button("Cancelar");
        btnConfirmar.disableProperty().bind(lista.getSelectionModel().selectedItemProperty().isNull());

        HBox botones = new HBox(10, btnCancelar, btnConfirmar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox contenido = new VBox(10, new Label("Selecciona el modelo:"), tfFiltro, lista, botones);
        contenido.setPadding(new Insets(20));
        contenido.setPrefWidth(320);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        Stage ventana = new Stage();
        ventana.setTitle("Editar modelo");
        ventana.initModality(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(SelectorModeloDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);

        final String[] result = {null};
        btnCancelar.setOnAction(ev -> ventana.close());
        btnConfirmar.setOnAction(ev -> { result[0] = lista.getSelectionModel().getSelectedItem(); ventana.close(); });
        ventana.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
