package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.StaleDataException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.sql.SQLException;
import java.util.Optional;

public class ClientesController {

    @FXML private TableView<Cliente> tablaClientes;
    @FXML private TableColumn<Cliente, String> cNombre;
    @FXML private TableColumn<Cliente, String> cEstado;
    @FXML private TableColumn<Cliente, Void>   cAcciones;
    @FXML private TextField filtroNombre;
    @FXML private Button    btnNuevo;

    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final ObservableList<Cliente> datos = FXCollections.observableArrayList();
    private FilteredList<Cliente> filtrados;
    private final boolean soloLectura = !(Sesion.esSuperTecnico() || Sesion.esAdmin());

    @FXML
    public void initialize() {
        cNombre.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNombre()));
        cEstado.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().isActivo() ? "Activo" : "Inactivo"));
        configurarAcciones();

        filtrados = new FilteredList<>(datos, c -> true);
        tablaClientes.setItems(filtrados);
        tablaClientes.setColumnResizePolicy(param -> true);
        filtroNombre.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.trim().toLowerCase();
            filtrados.setPredicate(c -> q.isEmpty() || c.getNombre().toLowerCase().contains(q));
        });

        btnNuevo.setVisible(!soloLectura);
        btnNuevo.setManaged(!soloLectura);
        cargar();
    }

    private void configurarAcciones() {
        cAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button bEditar = new Button("✎");
            private final Button bToggle = new Button();
            private final HBox box = new HBox(6, bEditar, bToggle);
            {
                bEditar.setOnAction(e -> editar(getTableView().getItems().get(getIndex())));
                bToggle.setOnAction(e -> toggleActivo(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || soloLectura) { setGraphic(null); return; }
                Cliente c = getTableView().getItems().get(getIndex());
                bToggle.setText(c.isActivo() ? "Desactivar" : "Activar");
                setGraphic(box);
            }
        });
    }

    private void cargar() {
        try {
            datos.setAll(clienteDAO.getAll());
        } catch (SQLException e) {
            Alertas.mostrarError(e.getMessage());
        }
    }

    @FXML
    private void nuevoCliente() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Nuevo cliente");
        dlg.setHeaderText(null);
        dlg.setContentText("Nombre:");
        Optional<String> r = dlg.showAndWait();
        r.map(String::trim).filter(s -> !s.isEmpty()).ifPresent(nombre -> {
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
        boolean nuevo = !c.isActivo();
        try {
            if (!nuevo && clienteDAO.tieneTelefonos(c.getIdCli())) {
                ConfirmDialog.mostrar(
                        "Desactivar cliente",
                        "El cliente tiene teléfonos asociados. Se desactivará pero seguirá visible en históricos.",
                        "Desactivar",
                        () -> aplicarSetActivo(c, nuevo));
            } else {
                aplicarSetActivo(c, nuevo);
            }
        } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
    }

    private void aplicarSetActivo(Cliente c, boolean nuevo) {
        try {
            clienteDAO.setActivo(c.getIdCli(), nuevo, c.getUpdatedAt());
            cargar();
        } catch (StaleDataException ex) {
            Alertas.mostrarError("El cliente fue modificado por otro usuario. Se recargan los datos.");
            cargar();
        } catch (SQLException ex) { Alertas.mostrarError(ex.getMessage()); }
    }
}
