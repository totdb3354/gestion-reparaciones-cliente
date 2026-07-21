package com.reparaciones.controllers;

import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.Alertas;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;

/**
 * Diálogo modal para editar nombre, divisa y comentario de un {@link Proveedor} existente.
 * <p>Extraído de {@code StockController.editarProveedor()} para poder reutilizarlo también
 * en el panel Suppliers de Inventario (spec suppliers de teléfonos §4): ambos apartados
 * editan el mismo modelo {@code Proveedor}, solo cambia el tipo con el que se listan y crean.</p>
 */
public final class EditarProveedorDialog {

    private EditarProveedorDialog() {}

    /**
     * Abre el diálogo modal de edición y, si se confirma, guarda los cambios y ejecuta
     * {@code onGuardado}.
     *
     * @param owner      ventana propietaria del diálogo modal (puede ser {@code null})
     * @param sel        proveedor a editar (no {@code null})
     * @param onGuardado se ejecuta tras guardar con éxito (p.ej. recargar la tabla)
     */
    public static void abrir(Window owner, Proveedor sel, Runnable onGuardado) {
        Label lblTitulo = new Label("Editar proveedor");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

        Label lblNombreLabel = new Label("Nombre");
        lblNombreLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfNombre = new TextField(sel.getNombre());
        tfNombre.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8;" +
                "-fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        Label lblDivisaLabel = new Label("Divisa");
        lblDivisaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<String> cmbDiv = new ComboBox<>();
        cmbDiv.getItems().setAll("EUR", "USD");
        cmbDiv.setValue(sel.getDivisa());
        cmbDiv.setMaxWidth(Double.MAX_VALUE);

        Label lblComentarioLabel = new Label("Comentario");
        lblComentarioLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea taComentario = new TextArea(sel.getComentario() != null ? sel.getComentario() : "");
        taComentario.setPrefRowCount(3);
        taComentario.setWrapText(true);
        taComentario.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6;" +
                "-fx-font-size: 13px; -fx-text-fill: #2C3B54;");

        Label lblError = new Label();
        lblError.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + ";");
        lblError.setVisible(false);

        Button btnConfirmar = new Button("Confirmar");
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
                lblComentarioLabel, taComentario,
                lblError, botones);
        contenido.setPadding(new Insets(28));
        contenido.setPrefWidth(360);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("Editar proveedor — " + sel.getNombre());

        btnCancelar.setOnAction(ev -> ventana.close());
        btnConfirmar.setOnAction(ev -> {
            String nombre = tfNombre.getText().trim();
            if (nombre.isBlank()) {
                lblError.setText("El nombre no puede estar vacío.");
                lblError.setVisible(true);
                return;
            }
            try {
                new ProveedorDAO().editar(sel.getIdProv(), nombre, cmbDiv.getValue(),
                        taComentario.getText().trim());
                ventana.close();
                onGuardado.run();
            } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
        });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(EditarProveedorDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfNombre::requestFocus);
        ventana.showAndWait();
    }
}
