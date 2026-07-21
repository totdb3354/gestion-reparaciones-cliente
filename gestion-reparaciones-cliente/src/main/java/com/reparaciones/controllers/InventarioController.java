package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;

/**
 * Controlador anfitrión de la pestaña Inventario.
 * <p>Sidebar con dos apartados: "Inventario" (agrupado por IMEI, componente compartido
 * con la vista TALLER de reparaciones) y "Suppliers" (proveedores de teléfonos).</p>
 * <p>Antes de este anfitrión, {@code MainController} cargaba {@code AgrupadoView.fxml}
 * directamente para la pestaña Inventario y lo configuraba en su miss de caché; ahora
 * esa configuración inicial vive aquí, en {@link #initialize()}.</p>
 * <p>El panel Suppliers (spec suppliers de teléfonos §4) es un calco funcional del panel
 * Proveedores de {@link StockController}, acotado a {@link ProveedorDAO#TIPO_TELEFONOS}:
 * misma tabla, mismo badge Activo/Inactivo, mismo menú contextual solo para SUPERTECNICO.
 * Sin filtro por combo (YAGNI con pocos suppliers) ni exportación CSV (fuera de alcance).</p>
 */
public class InventarioController implements com.reparaciones.utils.Recargable, com.reparaciones.utils.Exportable {

    @FXML private Button btnTabInventario;
    @FXML private Button btnTabSuppliers;
    @FXML private VBox   pnlInventario;
    @FXML private VBox   pnlSuppliers;
    @FXML private AgrupadoController agrupadoController;

    // ── Panel Suppliers ──────────────────────────────────────────────────────
    @FXML private Button btnNuevoSupplier;
    @FXML private TableView<Proveedor>             tablaSuppliers;
    @FXML private TableColumn<Proveedor, String>   cspNombre;
    @FXML private TableColumn<Proveedor, String>   cspDivisa;
    @FXML private TableColumn<Proveedor, String>   cspActivo;
    @FXML private TableColumn<Proveedor, String>   cspComentario;
    @FXML private Label lblUltimaActSuppliers;

    private final ProveedorDAO proveedorDAO = new ProveedorDAO();
    private final ObservableList<Proveedor> datosSuppliers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        agrupadoController.configurar(Sesion.esSuperTecnico() ? AgrupadoController.Rol.SUPERTECNICO : AgrupadoController.Rol.ADMIN,
                                       ConfigVistaAgrupado.Vista.INVENTARIO);
        agrupadoController.cargar();

        configurarTablaSuppliers();
        if (!Sesion.esSuperTecnico()) {
            btnNuevoSupplier.setVisible(false); btnNuevoSupplier.setManaged(false);
        }
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
        if (panel == pnlInventario)      agrupadoController.cargar();
        else if (panel == pnlSuppliers)  cargarSuppliers();
    }

    // ─── Configurar tabla Suppliers (calco de StockController.configurarTablaProveedores) ──

    private void configurarTablaSuppliers() {
        cspNombre.setCellValueFactory(c -> sp(c.getValue().getNombre()));
        cspDivisa.setCellValueFactory(c -> sp(c.getValue().getDivisa()));
        cspActivo.setCellValueFactory(c -> sp(c.getValue().isActivo() ? "Activo" : "Inactivo"));
        cspComentario.setCellValueFactory(c -> sp(c.getValue().getComentario() != null ? c.getValue().getComentario() : ""));
        cspActivo.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setGraphic(null); return; }
                String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10;" +
                              "-fx-font-size: 11px; -fx-font-weight: bold;";
                if ("Activo".equals(val)) {
                    badge.setText("Activo");
                    badge.setStyle(base +
                        "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_REPARADO_BG + ";" +
                        "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_REPARADO_ICO + ";");
                } else {
                    badge.setText("Inactivo");
                    badge.setStyle(base +
                        "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_CANCELADO_BG + ";" +
                        "-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_CANCELADO_TEXT + ";");
                }
                setGraphic(badge);
            }
        });
        tablaSuppliers.setRowFactory(tv -> new TableRow<>() {
            {
                selectedProperty().addListener((obs, o, sel) -> actualizarEstilo());
            }
            private void actualizarEstilo() {
                if (isEmpty() || getItem() == null) { setStyle("-fx-border-width: 0 0 0 8; -fx-border-color: transparent;"); return; }
                if (isSelected()) {
                    setStyle("-fx-background-color: " + com.reparaciones.utils.Colores.AZUL_MEDIO + ";" +
                             "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SELECTED_BRD + " transparent;" +
                             "-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;");
                } else if (getItem().isActivo()) {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0;" +
                             "-fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " " + com.reparaciones.utils.Colores.FILA_REPARADO_BRD + ";");
                } else {
                    setStyle("-fx-border-width: 0 0 1 8; -fx-border-insets: 1 0 0 0; -fx-border-color: transparent transparent " + com.reparaciones.utils.Colores.FILA_SEP + " transparent;");
                }
            }
            @Override protected void updateItem(Proveedor item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });
        tablaSuppliers.setItems(datosSuppliers);

        if (Sesion.esSuperTecnico()) {
            ContextMenu ctxSup = new ContextMenu();
            tablaSuppliers.setContextMenu(ctxSup);
            tablaSuppliers.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                ctxSup.getItems().clear();
                if (sel == null) return;
                MenuItem itemToggle = new MenuItem(sel.isActivo() ? "Desactivar" : "Activar");
                itemToggle.setOnAction(e -> activarSupplier());
                MenuItem itemEditar = new MenuItem("Editar…");
                itemEditar.setOnAction(e -> editarSupplier());
                ctxSup.getItems().addAll(itemToggle, itemEditar);
                try {
                    if (!proveedorDAO.tienePedidos(sel.getIdProv())) {
                        MenuItem itemBorrar = new MenuItem("Borrar");
                        itemBorrar.setOnAction(e -> borrarSupplier());
                        ctxSup.getItems().add(itemBorrar);
                    }
                } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
            });
        }
        tablaSuppliers.getColumns().forEach(c -> c.setReorderable(false));
    }

    private void cargarSuppliers() {
        try {
            datosSuppliers.setAll(proveedorDAO.getAll(ProveedorDAO.TIPO_TELEFONOS));
            String hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            if (lblUltimaActSuppliers != null) lblUltimaActSuppliers.setText("Actualizado " + hora);
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    // ── Acciones suppliers ──────────────────────────────────────────────────

    @FXML private void nuevoSupplier() {
        NuevoSupplierDialog.abrir(tablaSuppliers.getScene().getWindow(), null, nombre -> cargarSuppliers());
    }

    private void editarSupplier() {
        Proveedor sel = tablaSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        EditarProveedorDialog.abrir(tablaSuppliers.getScene().getWindow(), sel, this::cargarSuppliers);
    }

    private void activarSupplier() {
        Proveedor sel = tablaSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            proveedorDAO.setActivo(sel.getIdProv(), !sel.isActivo());
            cargarSuppliers();
        } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
    }

    private void borrarSupplier() {
        Proveedor sel = tablaSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        ConfirmDialog.mostrar(
                "Borrar supplier",
                "¿Eliminar el supplier \"" + sel.getNombre() + "\"?",
                "Borrar",
                () -> {
                    try {
                        proveedorDAO.borrar(sel.getIdProv());
                        cargarSuppliers();
                    } catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
                });
    }

    private static javafx.beans.property.SimpleStringProperty sp(String v) {
        return new javafx.beans.property.SimpleStringProperty(v);
    }

    // ─── Recargable / Exportable ────────────────────────────────────────────

    @Override
    public void recargar() {
        if (pnlInventario.isVisible())      agrupadoController.cargar();
        else if (pnlSuppliers.isVisible())  cargarSuppliers();
    }

    @Override
    public void detenerPolling() { /* sin poller */ }

    @Override
    public void exportarCSV(Stage owner) {
        if (pnlInventario.isVisible()) { agrupadoController.exportarCSV(owner); }
        // Suppliers no exporta CSV (fuera de alcance — spec)
    }
}
