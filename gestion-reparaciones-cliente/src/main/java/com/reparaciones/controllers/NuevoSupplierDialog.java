package com.reparaciones.controllers;

import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.utils.Alertas;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Diálogo modal de alta de un supplier de teléfonos (spec suppliers de teléfonos §3:
 * creación inline). Construido al estilo de {@link EditarProveedorDialog} (mismos
 * paddings/botonera).
 * <p>El nombre puede venir prellenado (p.ej. desde el nombre sin matchear de un xlsx
 * importado o desde el combo de alta manual) pero siempre queda editable. La divisa
 * por defecto es EUR. Nunca crea en silencio: al confirmar, el alta se hace en un hilo
 * secundario ({@code "crear-supplier"}); si falla, se muestra el error con
 * {@link Alertas#mostrarError} y el diálogo permanece abierto para reintentar; si tiene
 * éxito, se notifica {@code onCreado} en el hilo de JavaFX.</p>
 */
public final class NuevoSupplierDialog {

    private NuevoSupplierDialog() {}

    /**
     * Abre el diálogo de alta de supplier.
     *
     * @param owner         ventana propietaria del diálogo modal (puede ser {@code null})
     * @param nombreInicial nombre con el que prellenar el campo (puede ser {@code null} o vacío)
     * @param onCreado      se ejecuta en el hilo de JavaFX con el nombre creado, tras el alta con éxito
     */
    public static void abrir(Window owner, String nombreInicial, Consumer<String> onCreado) {
        Label lblTitulo = new Label("Nuevo supplier");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

        Label lblNombreLabel = new Label("Nombre");
        lblNombreLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfNombre = new TextField(nombreInicial != null ? nombreInicial : "");
        tfNombre.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8;" +
                "-fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        Label lblDivisaLabel = new Label("Divisa");
        lblDivisaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<String> cmbDiv = new ComboBox<>();
        cmbDiv.getItems().setAll("EUR", "USD");
        cmbDiv.setValue("EUR");
        cmbDiv.setMaxWidth(Double.MAX_VALUE);

        Label lblError = new Label();
        lblError.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + ";");
        lblError.setVisible(false);

        Button btnConfirmar = new Button("Crear supplier");
        btnConfirmar.setMaxWidth(Double.MAX_VALUE);
        btnConfirmar.getStyleClass().add("btn-primary");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setMaxWidth(Double.MAX_VALUE);
        btnCancelar.getStyleClass().add("btn-secondary");

        HBox botones = new HBox(10, btnCancelar, btnConfirmar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox contenido = new VBox(10,
                lblTitulo, lblNombreLabel, tfNombre,
                lblDivisaLabel, cmbDiv,
                lblError, botones);
        contenido.setPadding(new Insets(28));
        contenido.setPrefWidth(360);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("Nuevo supplier");

        btnCancelar.setOnAction(ev -> ventana.close());
        btnConfirmar.setOnAction(ev -> {
            String nombre = tfNombre.getText().trim();
            if (nombre.isBlank()) {
                lblError.setText("El nombre no puede estar vacío.");
                lblError.setVisible(true);
                return;
            }
            lblError.setVisible(false);
            String divisa = cmbDiv.getValue();
            btnConfirmar.setDisable(true);
            new Thread(() -> {
                try {
                    new ProveedorDAO().insertar(nombre, divisa, ProveedorDAO.TIPO_TELEFONOS);
                    Platform.runLater(() -> {
                        ventana.close();
                        onCreado.accept(nombre);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        btnConfirmar.setDisable(false);
                        Alertas.mostrarError(e.getMessage());
                    });
                }
            }, "crear-supplier").start();
        });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(NuevoSupplierDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfNombre::requestFocus);
        ventana.showAndWait();
    }
}
