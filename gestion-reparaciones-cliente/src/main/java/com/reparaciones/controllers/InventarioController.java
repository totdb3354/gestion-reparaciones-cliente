package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controlador anfitrión de la pestaña Inventario.
 * <p>Sidebar con dos apartados: "Inventario" (agrupado por IMEI, componente compartido
 * con la vista TALLER de reparaciones) y "Suppliers" (proveedores de teléfonos).</p>
 * <p>Antes de este anfitrión, {@code MainController} cargaba {@code AgrupadoView.fxml}
 * directamente para la pestaña Inventario y lo configuraba en su miss de caché; ahora
 * esa configuración inicial vive aquí, en {@link #initialize()}.</p>
 */
public class InventarioController implements com.reparaciones.utils.Recargable, com.reparaciones.utils.Exportable {

    @FXML private Button btnTabInventario;
    @FXML private Button btnTabSuppliers;
    @FXML private VBox   pnlInventario;
    @FXML private VBox   pnlSuppliers;
    @FXML private AgrupadoController agrupadoController;

    @FXML
    public void initialize() {
        agrupadoController.configurar(Sesion.esSuperTecnico() ? AgrupadoController.Rol.SUPERTECNICO : AgrupadoController.Rol.ADMIN,
                                       ConfigVistaAgrupado.Vista.INVENTARIO);
        agrupadoController.cargar();
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    @FXML private void mostrarInventarioPanel() {
        mostrarPanel(pnlInventario, btnTabInventario);
    }

    @FXML private void mostrarSuppliers() {
        mostrarPanel(pnlSuppliers, btnTabSuppliers);
    }

    private void mostrarPanel(VBox panel, Button btnActivo) {
        // Al salir del apartado Inventario, volver su drill-down a maestro
        if (pnlInventario.isVisible() && panel != pnlInventario)
            agrupadoController.resetarModo();

        pnlInventario.setVisible(false); pnlInventario.setManaged(false);
        pnlSuppliers .setVisible(false); pnlSuppliers .setManaged(false);
        panel.setVisible(true); panel.setManaged(true);
        for (Button b : new Button[]{btnTabInventario, btnTabSuppliers}) {
            b.getStyleClass().removeAll("stock-sidebar-btn-active", "stock-sidebar-btn");
            b.getStyleClass().add(b == btnActivo ? "stock-sidebar-btn-active" : "stock-sidebar-btn");
        }
        if (panel == pnlInventario) agrupadoController.cargar();
    }

    // ─── Recargable / Exportable ────────────────────────────────────────────

    @Override
    public void recargar() {
        if (pnlInventario.isVisible()) agrupadoController.cargar();
        // Suppliers: sin datos propios que recargar por ahora (T5 lo completa)
    }

    @Override
    public void detenerPolling() { /* sin poller */ }

    @Override
    public void exportarCSV(Stage owner) {
        if (pnlInventario.isVisible()) { agrupadoController.exportarCSV(owner); }
        // Suppliers no exporta CSV (fuera de alcance — spec)
    }
}
