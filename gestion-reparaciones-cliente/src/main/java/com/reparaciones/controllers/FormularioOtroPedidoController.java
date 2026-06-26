package com.reparaciones.controllers;

import com.reparaciones.dao.CompraOtroDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.Alertas;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

public class FormularioOtroPedidoController {

    @FXML private TextField           txtConcepto;
    @FXML private ComboBox<Proveedor> cmbProveedor;
    @FXML private TextField           txtCantidad;
    @FXML private CheckBox            chkUrgente;
    @FXML private TextField           txtPrecio;
    @FXML private ComboBox<String>    cmbDivisa;
    @FXML private Label               lblTotalEur;

    private final ProveedorDAO  proveedorDAO  = new ProveedorDAO();
    private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
    private final CompraOtroDAO compraOtroDAO = new CompraOtroDAO();

    private Runnable        onGuardado;
    private volatile double tasaActual = 1.0;

    public void init(Runnable onGuardado) {
        this.onGuardado = onGuardado;
        try {
            cmbProveedor.getItems().setAll(proveedorDAO.getActivos());
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
        cmbProveedor.setVisibleRowCount(8);
        cmbDivisa.getItems().setAll(List.of("EUR", "USD"));
        cmbDivisa.setValue("EUR");

        txtPrecio  .textProperty().addListener((obs, o, n) -> calcularTotal());
        txtCantidad.textProperty().addListener((obs, o, n) -> calcularTotal());
        cmbDivisa.valueProperty().addListener((obs, o, n) -> { if (n != null) fetchTasaAsync(n); });
        cmbProveedor.valueProperty().addListener((obs, o, n) -> { if (n != null) cmbDivisa.setValue(n.getDivisa()); });
    }

    private void fetchTasaAsync(String divisa) {
        if ("EUR".equalsIgnoreCase(divisa)) {
            tasaActual = 1.0;
            calcularTotal();
            return;
        }
        lblTotalEur.setText("Obteniendo tasa…");
        new Thread(() -> {
            try {
                double t = tipoCambioDAO.getTasa(divisa);
                Platform.runLater(() -> { tasaActual = t; calcularTotal(); });
            } catch (SQLException e) {
                Platform.runLater(() -> lblTotalEur.setText("Error al obtener tasa"));
            }
        }, "tasa-fetch").start();
    }

    private void calcularTotal() {
        String divisa = cmbDivisa.getValue() != null ? cmbDivisa.getValue() : "EUR";
        boolean esEur = "EUR".equalsIgnoreCase(divisa);
        String infoTasa = esEur ? "" : String.format("  (1 %s = %.4f €)", divisa, tasaActual);
        try {
            double precio = Double.parseDouble(txtPrecio.getText().trim().replace(",", "."));
            int    cant   = Integer.parseInt(txtCantidad.getText().trim());
            double total  = precio * tasaActual * cant;
            lblTotalEur.setText(String.format("%.2f €%s", total, infoTasa));
        } catch (NumberFormatException e) {
            lblTotalEur.setText(esEur ? "—" : infoTasa.trim());
        }
    }

    @FXML private void confirmar() {
        String concepto = txtConcepto.getText() == null ? "" : txtConcepto.getText().trim();
        if (concepto.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Indica un concepto.").showAndWait();
            return;
        }
        Proveedor prov = cmbProveedor.getValue();
        if (prov == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona un proveedor.").showAndWait();
            return;
        }
        int cant;
        try {
            cant = Integer.parseInt(txtCantidad.getText().trim());
            if (cant <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Cantidad no válida (debe ser > 0).").showAndWait();
            return;
        }
        double precio;
        try {
            precio = Double.parseDouble(txtPrecio.getText().trim().replace(",", "."));
            if (precio < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Precio no válido.").showAndWait();
            return;
        }
        String divisa = cmbDivisa.getValue();
        try {
            double tasa      = tipoCambioDAO.getTasa(divisa);
            double precioEur = precio * tasa;
            compraOtroDAO.insertar(prov.getIdProv(), concepto, cant,
                    chkUrgente.isSelected(), precio, divisa, precioEur);
            onGuardado.run();
            cerrarVentana();
        } catch (SQLException e) {
            Alertas.mostrarError("Error al guardar: " + e.getMessage());
        }
    }

    @FXML private void cancelar() { cerrarVentana(); }

    private void cerrarVentana() {
        ((Stage) txtConcepto.getScene().getWindow()).close();
    }

    public static void abrir(Runnable onGuardado) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        FormularioOtroPedidoController.class.getResource("/views/FormularioOtroPedidoView.fxml"));
                Parent root = loader.load();
                FormularioOtroPedidoController ctrl = loader.getController();
                Stage stage = new Stage();
                stage.setTitle("Nuevo otro pedido");
                stage.setScene(new Scene(root));
                stage.setResizable(false);
                stage.initModality(Modality.APPLICATION_MODAL);
                ctrl.init(onGuardado);
                stage.show();
            } catch (Exception e) {
                Alertas.mostrarError("No se pudo abrir el formulario: " + e.getMessage());
            }
        });
    }
}
