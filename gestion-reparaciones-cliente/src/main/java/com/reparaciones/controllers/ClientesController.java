package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.MultiSelectComboBox;
import com.reparaciones.utils.StaleDataException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pantalla de gestión del catálogo de clientes.
 * <p>Calca el estilo de la pantalla de Proveedores: sidebar, badge de estado,
 * filas estilizadas, menú contextual (solo SUPERTECNICO) y filtro desplegable.</p>
 */
public class ClientesController {

    @FXML private TableView<Cliente>            tablaClientes;
    @FXML private TableColumn<Cliente, String>  cNombre;
    @FXML private TableColumn<Cliente, String>  cEstado;
    @FXML private MultiSelectComboBox<Cliente>  menuFiltroClientes;
    @FXML private Button                        btnNuevo;

    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final ObservableList<Cliente> datos = FXCollections.observableArrayList();
    private final Set<String> seleccionados = new HashSet<>();
    private final StringProperty etiquetaCliente = new SimpleStringProperty("Cliente");
    private final boolean soloLectura = !Sesion.esSuperTecnico();

    @FXML
    public void initialize() {
        configurarTabla();

        FilteredList<Cliente> filtrados = new FilteredList<>(datos, c -> true);
        tablaClientes.setItems(filtrados);

        if (soloLectura) {
            btnNuevo.setVisible(false);
            btnNuevo.setManaged(false);
        }
        cargar();
    }

    private void configurarTabla() {
        cNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "Activo" : "Inactivo"));
        cEstado.setCellFactory(col -> new TableCell<>() {
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

        tablaClientes.setRowFactory(tv -> new TableRow<>() {
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
            @Override protected void updateItem(Cliente item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });

        if (Sesion.esSuperTecnico()) {
            ContextMenu ctx = new ContextMenu();
            tablaClientes.setContextMenu(ctx);
            tablaClientes.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                ctx.getItems().clear();
                if (sel == null) return;
                MenuItem itemToggle = new MenuItem(sel.isActivo() ? "Desactivar" : "Activar");
                itemToggle.setOnAction(e -> toggleActivo(sel));
                MenuItem itemEditar = new MenuItem("Editar");
                itemEditar.setOnAction(e -> editar(sel));
                ctx.getItems().addAll(itemToggle, itemEditar);
            });
        }
        tablaClientes.getColumns().forEach(c -> c.setReorderable(false));
    }

    private void cargar() {
        try {
            datos.setAll(clienteDAO.getAll());
            poblarFiltro();
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    private void poblarFiltro() {
        List<Cliente> activos = datos.stream()
                .filter(Cliente::isActivo)
                .collect(Collectors.toList());
        com.reparaciones.utils.MultiSelectDropdown.setup(
            menuFiltroClientes, activos,
            Cliente::getNombre,
            c -> seleccionados.contains(c.getNombre()),
            (c, checked) -> { if (checked) seleccionados.add(c.getNombre());
                              else         seleccionados.remove(c.getNombre());
                              actualizarTextoFiltro();
                              aplicarFiltro(); },
            etiquetaCliente);
    }

    private void actualizarTextoFiltro() {
        if      (seleccionados.isEmpty())   etiquetaCliente.set("Cliente");
        else if (seleccionados.size() == 1) etiquetaCliente.set(seleccionados.iterator().next());
        else                                etiquetaCliente.set(seleccionados.size() + " clientes");
    }

    private void aplicarFiltro() {
        List<String> sel = new ArrayList<>(seleccionados);
        ((FilteredList<Cliente>) tablaClientes.getItems())
                .setPredicate(c -> sel.isEmpty() || sel.contains(c.getNombre()));
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    @FXML
    private void nuevoCliente() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Nuevo cliente");
        dlg.setHeaderText(null);
        dlg.setContentText("Nombre del cliente:");
        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(nombre -> {
            try { clienteDAO.insertar(nombre); cargar(); }
            catch (SQLException e) { Alertas.mostrarError(e.getMessage()); }
        });
    }

    private void editar(Cliente c) {
        TextInputDialog dlg = new TextInputDialog(c.getNombre());
        dlg.setTitle("Editar cliente");
        dlg.setHeaderText(null);
        dlg.setContentText("Nombre:");
        Optional<String> r = dlg.showAndWait();
        r.map(String::trim).filter(s -> !s.isEmpty() && !s.equals(c.getNombre())).ifPresent(nombre -> {
            try { clienteDAO.editar(c.getIdCli(), nombre, c.getUpdatedAt()); cargar(); }
            catch (StaleDataException ex) {
                Alertas.mostrarError("El cliente fue modificado por otro usuario. Se recargan los datos.");
                cargar();
            } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
        });
    }

    private void toggleActivo(Cliente c) {
        try {
            clienteDAO.setActivo(c.getIdCli(), !c.isActivo(), c.getUpdatedAt());
            cargar();
        } catch (StaleDataException ex) {
            Alertas.mostrarError("El cliente fue modificado por otro usuario. Se recargan los datos.");
            cargar();
        } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
    }
}
