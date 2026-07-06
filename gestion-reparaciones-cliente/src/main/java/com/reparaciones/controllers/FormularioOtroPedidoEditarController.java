package com.reparaciones.controllers;

import com.reparaciones.dao.CompraOtroDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.CompraOtro;
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

/**
 * Controlador del formulario de edición de un pedido de otros componentes.
 * <p>Permite modificar concepto, proveedor, cantidad, urgencia, precio y divisa de un
 * pedido en estado {@code pendiente}, {@code en_camino} o {@code recibido}. A diferencia
 * de los pedidos de componentes, aquí no hay ajuste de stock (no son inventario).</p>
 *
 * <p>Usa control de concurrencia optimista ({@link com.reparaciones.utils.StaleDataException})
 * y convierte el precio a EUR vía {@link com.reparaciones.dao.TipoCambioDAO}.</p>
 *
 * @role ADMIN
 */
public class FormularioOtroPedidoEditarController {

    @FXML private Label            lblTitulo;
    @FXML private TextField        txtConcepto;
    @FXML private ComboBox<Proveedor> cmbProveedor;
    @FXML private TextField        txtCantidad;
    @FXML private CheckBox         chkUrgente;
    @FXML private TextField        txtPrecio;
    @FXML private ComboBox<String> cmbDivisa;
    @FXML private Label            lblTotalEur;

    private final ProveedorDAO  proveedorDAO  = new ProveedorDAO();
    private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
    private final CompraOtroDAO compraOtroDAO = new CompraOtroDAO();

    private CompraOtro pedidoEditar;
    private Runnable   onGuardado;
    private volatile double tasaActual = 1.0;

    /** Abre el editor en un diálogo modal. */
    public static void abrir(CompraOtro pedido, Runnable onGuardado) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        FormularioOtroPedidoEditarController.class.getResource("/views/FormularioOtroPedidoEditar.fxml"));
                Parent root = loader.load();
                FormularioOtroPedidoEditarController ctrl = loader.getController();
                Stage stage = new Stage();
                stage.setTitle("Editar pedido #" + pedido.getIdCompraOtro());
                stage.setScene(new Scene(root));
                stage.setResizable(false);
                stage.initModality(Modality.APPLICATION_MODAL);
                ctrl.init(pedido, onGuardado);
                stage.show();
            } catch (Exception e) {
                Alertas.mostrarError("No se pudo abrir el formulario: " + e.getMessage());
            }
        });
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    public void init(CompraOtro pedido, Runnable onGuardado) {
        this.pedidoEditar = pedido;
        this.onGuardado   = onGuardado;

        lblTitulo.setText("Editar pedido #" + pedido.getIdCompraOtro());
        txtConcepto.setText(pedido.getConcepto() != null ? pedido.getConcepto() : "");

        try {
            cmbProveedor.getItems().setAll(proveedorDAO.getActivos());
        } catch (SQLException e) {
            mostrarError(e);
        }
        cmbProveedor.setVisibleRowCount(8);

        cmbDivisa.getItems().setAll(List.of("EUR", "USD"));

        txtPrecio  .textProperty().addListener((obs, o, n) -> calcularTotal());
        txtCantidad.textProperty().addListener((obs, o, n) -> calcularTotal());
        cmbDivisa.valueProperty().addListener((obs, o, n) -> { if (n != null) fetchTasaAsync(n); });

        // Rellenar valores del pedido
        cmbProveedor.getItems().stream()
                .filter(p -> p.getIdProv() == pedido.getIdProv())
                .findFirst().ifPresent(cmbProveedor::setValue);
        int cantMostrar = pedido.getCantidadRecibida() != null ? pedido.getCantidadRecibida() : pedido.getCantidad();
        txtCantidad.setText(String.valueOf(cantMostrar));
        chkUrgente.setSelected(pedido.isEsUrgente());
        txtPrecio.setText(String.format("%.2f", pedido.getPrecioUnidadPedido()));
        cmbDivisa.setValue(pedido.getDivisa());
    }

    // ─── Tasa ─────────────────────────────────────────────────────────────────

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

    // ─── Confirmar ────────────────────────────────────────────────────────────

    @FXML private void confirmar() {
        String concepto = txtConcepto.getText().trim();
        if (concepto.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "El concepto no puede estar vacío.").showAndWait();
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
            compraOtroDAO.editar(pedidoEditar, prov.getIdProv(), concepto, cant,
                    chkUrgente.isSelected(), precio, divisa, precioEur);
            onGuardado.run();
            cerrarVentana();
        } catch (com.reparaciones.utils.StaleDataException e) {
            new Alert(Alert.AlertType.WARNING,
                    "El pedido fue modificado por otro usuario. Cierra y recarga los datos.")
                    .showAndWait();
        } catch (SQLException e) {
            Alertas.mostrarError("Error al guardar: " + e.getMessage());
        }
    }

    @FXML private void cancelar() { cerrarVentana(); }

    private void cerrarVentana() {
        ((Stage) cmbProveedor.getScene().getWindow()).close();
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
