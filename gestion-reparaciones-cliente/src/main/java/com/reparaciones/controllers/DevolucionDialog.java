package com.reparaciones.controllers;

import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ItemDevolucion;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;
import com.reparaciones.utils.TextoResultadoDevolucion;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F2c: registro masivo de devoluciones a almacén — escaneo local (como {@link EnvioDialog},
 * a diferencia de {@link ARevisarDialog} aquí NO se llama al servidor por cada IMEI: solo se
 * acumulan en una tabla con dedupe) con motivo por teléfono (editable, inicializado con el
 * motivo común de cabecera en el momento de añadir la fila) y confirmación única que envía
 * todo el lote de una vez ({@code TelefonoDAO#registrarDevoluciones}). Patrón de
 * ventana/hilos (Stage APPLICATION_MODAL + Thread + Platform.runLater) calcado de
 * {@link EnvioDialog}.
 */
public final class DevolucionDialog {

    private DevolucionDialog() {}

    /** Fila de la tabla de escaneo: IMEI fijo + motivo editable (copia del común al añadir). */
    private static final class FilaDevolucion {
        private final String imei;
        private final StringProperty motivo;

        FilaDevolucion(String imei, String motivoInicial) {
            this.imei = imei;
            this.motivo = new SimpleStringProperty(motivoInicial == null ? "" : motivoInicial);
        }

        String getImei() { return imei; }
        StringProperty motivoProperty() { return motivo; }
        String getMotivo() { return motivo.get(); }
    }

    /** IMEI de una fila de la lista de resultados: el texto es "imei  ·  <texto servidor>". */
    private static String imeiDeFila(String item) {
        int idx = item.indexOf("  ·  ");
        return idx < 0 ? item : item.substring(0, idx);
    }

    private static void copiarAlPortapapeles(String texto) {
        ClipboardContent content = new ClipboardContent();
        content.putString(texto);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static void abrir(Window owner, Runnable onCambios) {
        TelefonoDAO telefonoDAO = new TelefonoDAO();
        Set<String> vistos = new LinkedHashSet<>();
        boolean[] huboCambios = { false };
        boolean[] registrado = { false };    // true tras confirmar con éxito: la tabla pasa a modo resultados
        boolean[] registrando = { false };   // true mientras la petición está en vuelo: bloquea reabrir el guard doble-click

        // ── Cabecera: motivo común ───────────────────────────────────────────
        TextField tfMotivoComun = new TextField();
        tfMotivoComun.setPromptText("Motivo común (se copia a cada teléfono)");
        tfMotivoComun.setPrefWidth(320);
        HBox cabecera = new HBox(8, tfMotivoComun);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        // ── Escáner (calco del listener de EnvioDialog; sin llamada al servidor) ──
        Label lblTitulo = new Label("Escanear IMEI:");
        lblTitulo.setStyle("-fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPrefWidth(190);
        tfScan.setPromptText("Enter añade y limpia");
        Label lblScan = new Label();

        TableView<FilaDevolucion> tabla = new TableView<>();
        tabla.setEditable(true);
        tabla.setPlaceholder(new Label("Sin teléfonos escaneados"));
        tabla.setPrefSize(554, 300);   // +34 de la columna de acción "✕" frente al ancho previo

        TableColumn<FilaDevolucion, String> colImei = new TableColumn<>("IMEI");
        colImei.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getImei()));
        colImei.setSortable(false);
        colImei.setPrefWidth(220);

        TableColumn<FilaDevolucion, String> colMotivo = new TableColumn<>("Motivo");
        colMotivo.setCellValueFactory(c -> c.getValue().motivoProperty());
        // Celda editable a medida: el TextFieldTableCell de fábrica solo confirma con Enter
        // (su TextField llama a commitEdit únicamente desde setOnAction); al hacer clic fuera
        // (p.ej. en "Registrar") el foco se mueve sin confirmar y la edición en curso se pierde
        // en silencio. Calco del patrón de FormularioCompraController (colCantidad/colPrecio):
        // un listener de focusedProperty fuerza el commit también al perder el foco.
        colMotivo.setCellFactory(col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setOnAction(e -> commitEdit(tf.getText()));
                tf.focusedProperty().addListener((obs, was, focused) -> {
                    if (!focused && isEditing()) commitEdit(tf.getText());
                });
            }
            @Override
            public void startEdit() {
                super.startEdit();
                tf.setText(getItem() == null ? "" : getItem());
                setText(null);
                setGraphic(tf);
                tf.selectAll();
                tf.requestFocus();
            }
            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            @Override
            public void commitEdit(String newValue) {
                super.commitEdit(newValue);
                setText(newValue);
                setGraphic(null);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); setGraphic(null); return; }
                if (isEditing()) { setText(null); setGraphic(tf); }
                else { setText(item); setGraphic(null); }
            }
        });
        colMotivo.setOnEditCommit(e -> e.getRowValue().motivoProperty().set(e.getNewValue()));
        colMotivo.setSortable(false);
        colMotivo.setPrefWidth(280);

        // Columna de acción con el botón "✕" por fila (calco de AltaManualLoteDialog): sustituye
        // el antiguo "Quitar" del menú contextual, que era indescubrible. Sin cabecera, estrecha
        // y fija (no ordenable ni reordenable): solo aloja el botón.
        TableColumn<FilaDevolucion, Void> colQuitar = new TableColumn<>("");
        colQuitar.setSortable(false);
        colQuitar.setReorderable(false);
        colQuitar.setResizable(false);
        colQuitar.setPrefWidth(34);
        colQuitar.setMinWidth(34);
        colQuitar.setMaxWidth(34);

        tabla.getColumns().setAll(List.of(colImei, colMotivo, colQuitar));

        // Tras registrar, la tabla deja paso a una lista de resultados (más simple que
        // reescribir la columna Motivo con el texto de resultado; calco de EnvioDialog).
        ListView<String> listaResultados = new ListView<>();
        listaResultados.setPrefSize(520, 300);
        listaResultados.setVisible(false);
        listaResultados.setManaged(false);

        Label lblContador = new Label("0 devoluciones");

        Runnable actualizarContador = () -> lblContador.setText(tabla.getItems().size() + " devoluciones");

        Button btnRegistrar = new Button("Registrar");
        btnRegistrar.getStyleClass().add("btn-primary");
        btnRegistrar.setDisable(true);
        Button btnCerrar = new Button("Cerrar");

        java.util.function.Consumer<List<String>> anadir = imeis -> {
            List<String> nuevos = new ArrayList<>();
            for (String im : imeis) if (vistos.add(im)) nuevos.add(im);
            if (nuevos.isEmpty()) {
                if (!imeis.isEmpty()) { lblScan.setStyle(""); lblScan.setText("Ya añadido a este lote."); }
                return;
            }
            lblScan.setStyle(""); lblScan.setText("");
            String motivoInicial = tfMotivoComun.getText();
            for (String im : nuevos) tabla.getItems().add(new FilaDevolucion(im, motivoInicial));
            tabla.scrollTo(tabla.getItems().size() - 1);
            actualizarContador.run();
            btnRegistrar.setDisable(registrando[0] || tabla.getItems().isEmpty());
        };

        Runnable intentarAnadir = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            anadir.accept(List.of(imei));
            Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) {
                String solo = n.replaceAll("[^\\d]", "");
                Platform.runLater(() -> tfScan.setText(solo));
                return;
            }
            if (n.length() > 15) {
                ResultadoPegado res = ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == TipoPegado.CORRUPTO) {
                    Platform.runLater(() -> {
                        tfScan.clear();
                        lblScan.setStyle("");
                        lblScan.setText("Algún IMEI del pegado está corrupto.");
                    });
                    return;
                }
                anadir.accept(res.imeis());
                Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
                return;
            }
            if (n.length() == 15) intentarAnadir.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) intentarAnadir.run(); });

        // Botón "✕" por fila mientras el lote no se ha registrado ni está en vuelo (calco de
        // AltaManualLoteDialog); deshabilitado durante el registro (cierra el hueco que antes
        // permitía quitar filas locales mientras el servidor procesaba el registro). Tras
        // registrar, la tabla entera se oculta en favor de la lista de resultados, así que la
        // columna deja de verse con ella.
        colQuitar.setCellFactory(col -> new TableCell<>() {
            private final Button btnQuitar = new Button("✕");
            {
                btnQuitar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Colores.AZUL_GRIS
                        + "; -fx-cursor: hand; -fx-font-size: 12px;");
                btnQuitar.setOnAction(e -> {
                    FilaDevolucion fila = getTableRow() == null ? null : getTableRow().getItem();
                    if (fila == null) return;
                    vistos.remove(fila.getImei());
                    tabla.getItems().remove(fila);
                    actualizarContador.run();
                    btnRegistrar.setDisable(registrando[0] || tabla.getItems().isEmpty());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                btnQuitar.setDisable(registrando[0]);
                btnQuitar.setVisible(!registrando[0]);
                setGraphic(btnQuitar);
            }
        });

        // "Copiar IMEI" sustituye el antiguo "Quitar" del menú contextual de la tabla (indescubrible).
        tabla.setRowFactory(tv -> new TableRow<>() {
            private final ContextMenu menu = new ContextMenu();
            private final MenuItem copiarImei = new MenuItem("Copiar IMEI");
            {
                menu.getItems().add(copiarImei);
            }
            @Override
            protected void updateItem(FilaDevolucion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setContextMenu(null); return; }
                copiarImei.setOnAction(e -> copiarAlPortapapeles(item.getImei()));
                setContextMenu(menu);
            }
        });

        // La lista de resultados no tenía menú contextual: se añade "Copiar IMEI" (extrae el
        // IMEI del texto "imei  ·  <texto servidor>").
        listaResultados.setCellFactory(lv -> new ListCell<>() {
            private final ContextMenu menu = new ContextMenu();
            private final MenuItem copiarImei = new MenuItem("Copiar IMEI");
            {
                menu.getItems().add(copiarImei);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setContextMenu(null); return; }
                setText(item);
                copiarImei.setOnAction(e -> copiarAlPortapapeles(imeiDeFila(item)));
                setContextMenu(menu);
            }
        });

        Runnable confirmar = () -> {
            String comun = tfMotivoComun.getText() == null ? "" : tfMotivoComun.getText().trim();
            List<Map<String, String>> items = new ArrayList<>();
            for (FilaDevolucion fila : tabla.getItems()) {
                String motivoFila = fila.getMotivo() == null ? "" : fila.getMotivo().trim();
                String motivoFinal = motivoFila.isEmpty() ? comun : motivoFila;
                items.add(Map.of("imei", fila.getImei(), "motivo", motivoFinal));
            }

            registrando[0] = true;
            btnRegistrar.setDisable(true);   // guard doble-click
            tfScan.setDisable(true);         // evita colar IMEIs nuevos mientras la petición está en vuelo
            tfMotivoComun.setDisable(true);
            tabla.refresh();                 // deshabilita el botón ✕ de cada fila mientras la petición está en vuelo
            new Thread(() -> {
                try {
                    List<ItemDevolucion> res = telefonoDAO.registrarDevoluciones(items);
                    Platform.runLater(() -> {
                        registrado[0] = true;
                        int devueltas = 0, rechazadas = 0;
                        listaResultados.getItems().clear();
                        for (ItemDevolucion it : res) {
                            listaResultados.getItems().add(it.getImei() + "  ·  "
                                    + TextoResultadoDevolucion.texto(it.getResultado(), it.getEnvio()));
                            if (TextoResultadoDevolucion.esDevuelto(it.getResultado())) { devueltas++; huboCambios[0] = true; }
                            else rechazadas++;
                        }
                        lblContador.setText(devueltas + " devueltas · " + rechazadas + " rechazadas");
                        tabla.setVisible(false); tabla.setManaged(false);
                        listaResultados.setVisible(true); listaResultados.setManaged(true);
                        // El lote es uno: para otro, cerrar y reabrir el diálogo.
                        btnRegistrar.setVisible(false); btnRegistrar.setManaged(false);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        Alertas.mostrarError(e.getMessage());
                        registrando[0] = false;
                        btnRegistrar.setDisable(false);
                        tfScan.setDisable(false);
                        tfMotivoComun.setDisable(false);
                        tabla.refresh();   // vuelve a habilitar el botón ✕ de cada fila
                    });
                }
            }, "devolucion-confirmar").start();
        };
        btnRegistrar.setOnAction(e -> confirmar.run());

        HBox filaScan = new HBox(8, lblTitulo, tfScan, lblScan);
        filaScan.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pie = new HBox(8, lblContador, spacer, btnRegistrar, btnCerrar);
        pie.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(10, cabecera, filaScan, tabla, listaResultados, pie);
        contenido.setPadding(new Insets(14));

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("Registrar devolución");
        btnCerrar.setOnAction(ev -> ventana.close());
        ventana.setOnHidden(ev -> { if (huboCambios[0] && onCambios != null) onCambios.run(); });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(DevolucionDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }
}
