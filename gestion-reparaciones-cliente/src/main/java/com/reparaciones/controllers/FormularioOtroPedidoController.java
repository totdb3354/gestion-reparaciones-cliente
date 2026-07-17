package com.reparaciones.controllers;

import com.reparaciones.dao.CompraOtroDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.Alertas;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador del formulario de nuevo "otro pedido" (artículos sin stock).
 * <p>Tabla editable multilínea idéntica en estructura al formulario de pedidos de
 * componentes, pero con una columna "Concepto" de texto libre en vez de "Componente".
 * No hay relación con el inventario de stock.</p>
 *
 * @role ADMIN
 */
public class FormularioOtroPedidoController {

    // ─── Modelo de línea ──────────────────────────────────────────────────────

    /**
     * Línea de un "otro pedido" multilínea antes de persistirse en BD.
     * Análogo a {@link com.reparaciones.models.LineaPedido} pero con
     * {@code concepto} (texto libre) en lugar de {@code componente}.
     */
    static class LineaOtro {
        private final StringProperty  concepto   = new SimpleStringProperty("");
        private final ObjectProperty<Proveedor> proveedor = new SimpleObjectProperty<>();
        private final IntegerProperty cantidad   = new SimpleIntegerProperty(1);
        private final DoubleProperty  precioUnidad = new SimpleDoubleProperty(0.0);
        private final BooleanProperty esUrgente  = new SimpleBooleanProperty(false);
        private double tasa = 1.0;

        public StringProperty  conceptoProperty()    { return concepto; }
        public ObjectProperty<Proveedor> proveedorProperty() { return proveedor; }
        public IntegerProperty cantidadProperty()    { return cantidad; }
        public DoubleProperty  precioUnidadProperty(){ return precioUnidad; }
        public BooleanProperty esUrgenteProperty()   { return esUrgente; }

        public String   getConcepto()    { return concepto.get(); }
        public Proveedor getProveedor()  { return proveedor.get(); }
        public int      getCantidad()    { return cantidad.get(); }
        public double   getPrecioUnidad(){ return precioUnidad.get(); }
        public boolean  isEsUrgente()   { return esUrgente.get(); }
        public double   getTasa()        { return tasa; }

        public void setConcepto(String v)    { concepto.set(v); }
        public void setProveedor(Proveedor p){ proveedor.set(p); }
        public void setCantidad(int v)       { cantidad.set(v); }
        public void setPrecioUnidad(double v){ precioUnidad.set(v); }
        public void setEsUrgente(boolean v)  { esUrgente.set(v); }
        public void setTasa(double t)        { tasa = t; }
    }

    // ─── FXML ─────────────────────────────────────────────────────────────────

    @FXML private Button                          btnConfirmar;
    @FXML private TableView<LineaOtro>            tablaLineas;
    @FXML private TableColumn<LineaOtro, String>  colConcepto;
    @FXML private TableColumn<LineaOtro, Proveedor> colProveedor;
    @FXML private TableColumn<LineaOtro, Integer> colCantidad;
    @FXML private TableColumn<LineaOtro, Double>  colPrecio;
    @FXML private TableColumn<LineaOtro, Boolean> colUrgente;
    @FXML private TableColumn<LineaOtro, String>  colTotalEur;
    @FXML private TableColumn<LineaOtro, Void>    colQuitar;

    // ─── Estilo campo editable (igual que FormularioCompraController) ─────────

    private static final String ESTILO_EDITABLE =
            "-fx-background-color: #FFFFFF;" +
            "-fx-border-color: #C2C8D0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 3;" +
            "-fx-background-radius: 3;" +
            "-fx-padding: 5 8 5 8;" +
            "-fx-text-fill: #2C3B54;";

    // ─── Dependencias ─────────────────────────────────────────────────────────

    private final ProveedorDAO  proveedorDAO  = new ProveedorDAO();
    private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
    private final CompraOtroDAO compraOtroDAO = new CompraOtroDAO();
    private final Map<String, Double> tasasCache = new HashMap<>();

    private final ObservableList<LineaOtro>  lineas               = FXCollections.observableArrayList();
    private       ObservableList<Proveedor>  proveedoresDisponibles;

    private Runnable onGuardado;

    // ─── Init ─────────────────────────────────────────────────────────────────

    public void init(Runnable onGuardado) {
        this.onGuardado = onGuardado;
        try {
            proveedoresDisponibles = FXCollections.observableArrayList(proveedorDAO.getActivos(ProveedorDAO.TIPO_COMPONENTES));
        } catch (SQLException e) {
            mostrarError(e);
        }
        configurarTabla();
        tablaLineas.setItems(lineas);
    }

    // ─── Tasa de cambio por línea ─────────────────────────────────────────────

    private void fetchTasaParaLinea(LineaOtro linea, String divisa) {
        if ("EUR".equalsIgnoreCase(divisa)) {
            linea.setTasa(1.0);
            tablaLineas.refresh();
            return;
        }
        Double cached = tasasCache.get(divisa);
        if (cached != null) {
            linea.setTasa(cached);
            tablaLineas.refresh();
            return;
        }
        new Thread(() -> {
            try {
                double t = tipoCambioDAO.getTasa(divisa);
                Platform.runLater(() -> {
                    tasasCache.put(divisa, t);
                    linea.setTasa(t);
                    tablaLineas.refresh();
                });
            } catch (SQLException e) {
                Platform.runLater(tablaLineas::refresh);
            }
        }, "tasa-fetch").start();
    }

    // ─── Tabla editable ───────────────────────────────────────────────────────

    private void configurarTabla() {
        tablaLineas.setEditable(true);

        // Concepto — TextField de texto libre (mismo look oscuro que el campo de componente)
        colConcepto.setCellValueFactory(c -> c.getValue().conceptoProperty());
        colConcepto.setCellFactory(col -> new TableCell<>() {
            private final TextField campo = new TextField();

            private static final String CAMPO_NORMAL =
                    "-fx-background-color: #001232; -fx-background-radius: 24;" +
                    "-fx-border-color: transparent; -fx-border-radius: 24; -fx-border-width: 0;" +
                    "-fx-text-fill: #FAFAFA; -fx-prompt-text-fill: rgba(255,255,255,0.45);" +
                    "-fx-font-size: 12px; -fx-font-weight: bold;" +
                    "-fx-padding: 4 12 4 12;";
            private static final String CAMPO_SELEC =
                    "-fx-background-color: #001232; -fx-background-radius: 24;" +
                    "-fx-border-color: rgba(255,255,255,0.35); -fx-border-radius: 24; -fx-border-width: 1;" +
                    "-fx-text-fill: #FAFAFA; -fx-prompt-text-fill: rgba(255,255,255,0.45);" +
                    "-fx-font-size: 12px; -fx-font-weight: bold;" +
                    "-fx-padding: 4 12 4 12;";
            {
                campo.setStyle(CAMPO_NORMAL);
                campo.setPromptText("Escribe concepto...");
                campo.setMaxWidth(Double.MAX_VALUE);

                campo.textProperty().addListener((obs, old, val) -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        getTableView().getItems().get(idx).setConcepto(val == null ? "" : val);
                });
                campo.focusedProperty().addListener((obs, o, focused) -> {
                    if (focused) {
                        int idx = getIndex();
                        if (idx >= 0 && idx < getTableView().getItems().size())
                            getTableView().getSelectionModel().select(idx);
                    }
                });
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (newRow == null) return;
                    campo.setStyle(newRow.isSelected() ? CAMPO_SELEC : CAMPO_NORMAL);
                    newRow.selectedProperty().addListener((o, old, selected) ->
                        campo.setStyle(selected ? CAMPO_SELEC : CAMPO_NORMAL));
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); }
                else {
                    String val = item != null ? item : "";
                    // Solo re-set si cambia de verdad: evita reposicionar el cursor
                    // al teclear (si no, el texto se escribiría al revés).
                    if (!campo.getText().equals(val)) campo.setText(val);
                    setGraphic(campo);
                }
            }
        });

        // Proveedor — ComboBox siempre visible, auto-fetch tasa al cambiar
        colProveedor.setCellValueFactory(c -> c.getValue().proveedorProperty());
        colProveedor.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<Proveedor> combo = new ComboBox<>(proveedoresDisponibles);
            {
                combo.setVisibleRowCount(8);
                combo.setCellFactory(lv -> new ListCell<>() {
                    {
                        setOnMouseEntered(e -> { if (!isEmpty() && getItem() != null)
                            setStyle("-fx-text-fill: #2C3B54; -fx-background-color: #DDE1E7;"); });
                        setOnMouseExited(e -> { if (!isEmpty() && getItem() != null)
                            setStyle("-fx-text-fill: #2C3B54; -fx-background-color: white;"); });
                    }
                    @Override protected void updateItem(Proveedor p, boolean empty) {
                        super.updateItem(p, empty);
                        setText(empty || p == null ? null : p.getNombre());
                        setStyle("-fx-text-fill: #2C3B54; -fx-background-color: white;");
                    }
                });
                combo.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(Proveedor p, boolean empty) {
                        super.updateItem(p, empty);
                        setText(empty || p == null ? "" : p.getNombre());
                    }
                });
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (newRow != null) {
                        combo.setStyle(newRow.isSelected() ? "-fx-border-color: rgba(255,255,255,0.35); -fx-border-width: 1;" : "");
                        newRow.selectedProperty().addListener((o, old, selected) ->
                            combo.setStyle(selected ? "-fx-border-color: rgba(255,255,255,0.35); -fx-border-width: 1;" : ""));
                    }
                });
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    Proveedor val = combo.getValue();
                    int idx = getIndex();
                    if (val != null && idx >= 0 && idx < getTableView().getItems().size()) {
                        LineaOtro linea = getTableView().getItems().get(idx);
                        linea.setProveedor(val);
                        fetchTasaParaLinea(linea, val.getDivisa());
                    }
                });
            }
            @Override protected void updateItem(Proveedor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); }
                else { combo.setValue(item); setGraphic(combo); }
            }
        });

        // Cantidad — celda personalizada
        colCantidad.setCellValueFactory(c -> c.getValue().cantidadProperty().asObject());
        colCantidad.setCellFactory(col -> new TableCell<>() {
            private final Label     lbl = new Label();
            private final TextField tf  = new TextField();
            {
                lbl.setStyle(ESTILO_EDITABLE);
                lbl.setMaxWidth(Double.MAX_VALUE);
                tf.setPrefWidth(Double.MAX_VALUE);
                tf.setOnAction(e -> commitEntero());
                tf.focusedProperty().addListener((o, was, focused) -> { if (!focused) commitEntero(); });
                tf.textProperty().addListener((obs, o, n) -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        try {
                            int v = Integer.parseInt(n.trim());
                            if (v > 0) getTableView().getItems().get(idx).setCantidad(v);
                        } catch (NumberFormatException ignored) {}
                    }
                });
                tableRowProperty().addListener((obs, or, nr) -> {
                    if (nr != null) nr.selectedProperty().addListener((o, w, s) -> refresh());
                });
            }
            private void commitEntero() {
                try { commitEdit(Integer.parseInt(tf.getText().trim())); }
                catch (NumberFormatException e) { cancelEdit(); }
            }
            private void refresh() {
                if (isEmpty() || getItem() == null) { setText(null); setGraphic(null); setStyle(""); return; }
                boolean sel = getTableRow() != null && getTableRow().isSelected();
                if (isEditing()) {
                    setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                    setText(null); setGraphic(tf);
                } else if (sel) {
                    setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                    lbl.setText(String.valueOf(getItem()));
                    setText(null); setGraphic(lbl);
                } else {
                    setStyle(""); setText(String.valueOf(getItem())); setGraphic(null);
                }
            }
            @Override public void startEdit() {
                super.startEdit();
                tf.setText(getItem() == null ? "" : String.valueOf(getItem()));
                setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                setText(null); setGraphic(tf);
                tf.selectAll(); tf.requestFocus();
            }
            @Override public void cancelEdit() { super.cancelEdit(); refresh(); getTableView().refresh(); }
            @Override protected void updateItem(Integer item, boolean empty) { super.updateItem(item, empty); refresh(); }
        });
        colCantidad.setOnEditCommit(e -> {
            if (e.getNewValue() != null && e.getNewValue() > 0)
                e.getRowValue().setCantidad(e.getNewValue());
            tablaLineas.refresh();
        });

        // Precio — celda personalizada, símbolo según divisa del proveedor de la línea
        colPrecio.setCellValueFactory(c -> c.getValue().precioUnidadProperty().asObject());
        colPrecio.setCellFactory(col -> new TableCell<>() {
            private final Label     lbl = new Label();
            private final TextField tf  = new TextField();
            {
                lbl.setStyle(ESTILO_EDITABLE);
                lbl.setMaxWidth(Double.MAX_VALUE);
                tf.setPrefWidth(Double.MAX_VALUE);
                tf.setOnAction(e -> commitDecimal());
                tf.focusedProperty().addListener((o, was, focused) -> { if (!focused) commitDecimal(); });
                tf.textProperty().addListener((obs, o, n) -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        try {
                            double v = Double.parseDouble(n.trim().replace(",", "."));
                            if (v >= 0) getTableView().getItems().get(idx).setPrecioUnidad(v);
                        } catch (NumberFormatException ignored) {}
                    }
                });
                tableRowProperty().addListener((obs, or, nr) -> {
                    if (nr != null) nr.selectedProperty().addListener((o, w, s) -> refresh());
                });
            }
            private void commitDecimal() {
                try { commitEdit(Double.parseDouble(tf.getText().trim().replace(",", "."))); }
                catch (NumberFormatException e) { cancelEdit(); }
            }
            private String simb() {
                LineaOtro l = getTableRow() != null ? getTableRow().getItem() : null;
                if (l == null || l.getProveedor() == null) return "€";
                return "USD".equals(l.getProveedor().getDivisa()) ? "$" : "€";
            }
            private void refresh() {
                if (isEmpty() || getItem() == null) { setText(null); setGraphic(null); setStyle(""); return; }
                boolean sel = getTableRow() != null && getTableRow().isSelected();
                if (isEditing()) {
                    setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                    setText(null); setGraphic(tf);
                } else if (sel) {
                    setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                    lbl.setText(String.format("%.2f %s", getItem(), simb()));
                    setText(null); setGraphic(lbl);
                } else {
                    setStyle(""); setText(String.format("%.2f %s", getItem(), simb())); setGraphic(null);
                }
            }
            @Override public void startEdit() {
                super.startEdit();
                tf.setText(getItem() == null ? "" : String.valueOf(getItem()));
                setStyle("-fx-background-color: transparent; -fx-padding: 3;");
                setText(null); setGraphic(tf);
                tf.selectAll(); tf.requestFocus();
            }
            @Override public void cancelEdit() { super.cancelEdit(); refresh(); getTableView().refresh(); }
            @Override protected void updateItem(Double item, boolean empty) { super.updateItem(item, empty); refresh(); }
        });
        colPrecio.setOnEditCommit(e -> {
            if (e.getNewValue() != null && e.getNewValue() >= 0)
                e.getRowValue().setPrecioUnidad(e.getNewValue());
            tablaLineas.refresh();
        });

        // Urgente — CheckBox
        colUrgente.setCellValueFactory(c -> c.getValue().esUrgenteProperty());
        colUrgente.setCellFactory(CheckBoxTableCell.forTableColumn(colUrgente));

        // Total EUR — usa la tasa de la línea
        colTotalEur.setCellValueFactory(c -> {
            LineaOtro l = c.getValue();
            double total = l.getPrecioUnidad() * l.getTasa() * l.getCantidad();
            return new SimpleStringProperty(String.format("%.2f €", total));
        });

        // Selección azul (igual que FormularioCompraController)
        tablaLineas.setRowFactory(tv -> new TableRow<>() {
            { selectedProperty().addListener((obs, o, sel) -> actualizarEstilo()); }
            private void actualizarEstilo() {
                if (isEmpty() || getItem() == null) { setStyle(""); return; }
                setStyle(isSelected()
                        ? "-fx-background-color: #2C3B54;" +
                          "-fx-border-color: transparent transparent #3D5070 transparent;" +
                          "-fx-border-width: 0 0 0.2 0;"
                        : "");
            }
            @Override protected void updateItem(LineaOtro item, boolean empty) {
                super.updateItem(item, empty);
                actualizarEstilo();
            }
        });

        // Quitar fila
        Image imgBorrar = new Image(getClass().getResourceAsStream("/images/borrar.png"));
        colQuitar.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv  = new ImageView(imgBorrar);
            private final HBox      box = new HBox(iv);
            {
                iv.setFitWidth(25); iv.setFitHeight(25); iv.setPreserveRatio(true);
                iv.setStyle("-fx-cursor: hand;");
                box.setAlignment(Pos.CENTER);
                iv.setOnMouseClicked(e -> lineas.remove(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
        tablaLineas.getColumns().forEach(c -> c.setReorderable(false));
    }

    // ─── Añadir fila ──────────────────────────────────────────────────────────

    @FXML private void anadirLinea() {
        LineaOtro linea = new LineaOtro();
        lineas.add(linea);
        // Diferido: el scrollTo síncrono tras añadir no lleva de forma fiable la nueva
        // última fila al viewport cuando la tabla ya necesita scroll (queda recortada/sin
        // renderizar y no se puede interactuar). Se ejecuta tras el layout de la fila.
        Platform.runLater(() -> {
            tablaLineas.getSelectionModel().selectLast();
            tablaLineas.scrollTo(lineas.size() - 1);
        });
    }

    // ─── Confirmar ────────────────────────────────────────────────────────────

    @FXML private void confirmar() {
        if (lineas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Añade al menos una línea.").showAndWait();
            return;
        }
        for (int i = 0; i < lineas.size(); i++) {
            LineaOtro l = lineas.get(i);
            String concepto = l.getConcepto() == null ? "" : l.getConcepto().trim();
            if (concepto.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Línea " + (i+1) + ": el concepto no puede estar vacío.").showAndWait();
                return;
            }
            if (l.getProveedor() == null) {
                new Alert(Alert.AlertType.WARNING, "Línea " + (i+1) + ": selecciona un proveedor.").showAndWait();
                return;
            }
            if (l.getCantidad() <= 0) {
                new Alert(Alert.AlertType.WARNING, "Línea " + (i+1) + ": la cantidad debe ser mayor que 0.").showAndWait();
                return;
            }
            if (l.getPrecioUnidad() < 0) {
                new Alert(Alert.AlertType.WARNING, "Línea " + (i+1) + ": el precio no puede ser negativo.").showAndWait();
                return;
            }
        }
        try {
            for (LineaOtro linea : lineas) {
                Proveedor prov     = linea.getProveedor();
                String    divisa   = prov.getDivisa();
                double    tasa     = tipoCambioDAO.getTasa(divisa);
                double    precioEur = linea.getPrecioUnidad() * tasa;
                compraOtroDAO.insertar(prov.getIdProv(), linea.getConcepto().trim(),
                        linea.getCantidad(), linea.isEsUrgente(),
                        linea.getPrecioUnidad(), divisa, precioEur);
            }
            onGuardado.run();
            cerrarVentana();
        } catch (SQLException e) {
            Alertas.mostrarError("Error al guardar: " + e.getMessage());
        }
    }

    @FXML private void cancelar() { cerrarVentana(); }

    private void cerrarVentana() {
        ((Stage) tablaLineas.getScene().getWindow()).close();
    }

    // ─── Apertura estática ────────────────────────────────────────────────────

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

    private static void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
