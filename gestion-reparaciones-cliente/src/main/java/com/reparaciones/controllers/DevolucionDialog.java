package com.reparaciones.controllers;

import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ItemDevolucion;
import com.reparaciones.utils.Alertas;
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
import javafx.scene.control.cell.TextFieldTableCell;
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
        tabla.setPrefSize(520, 300);

        TableColumn<FilaDevolucion, String> colImei = new TableColumn<>("IMEI");
        colImei.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getImei()));
        colImei.setSortable(false);
        colImei.setPrefWidth(220);

        TableColumn<FilaDevolucion, String> colMotivo = new TableColumn<>("Motivo");
        colMotivo.setCellValueFactory(c -> c.getValue().motivoProperty());
        colMotivo.setCellFactory(TextFieldTableCell.forTableColumn());
        colMotivo.setOnEditCommit(e -> e.getRowValue().motivoProperty().set(e.getNewValue()));
        colMotivo.setSortable(false);
        colMotivo.setPrefWidth(280);

        tabla.getColumns().setAll(List.of(colImei, colMotivo));

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

        // "Quitar" por fila mientras el lote no se ha registrado (tras registrar, la tabla
        // se oculta en favor de la lista de resultados y deja de tener sentido quitar filas).
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaDevolucion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || registrado[0]) { setContextMenu(null); return; }
                ContextMenu menu = new ContextMenu();
                MenuItem quitar = new MenuItem("Quitar");
                quitar.setOnAction(e -> {
                    vistos.remove(item.getImei());
                    tabla.getItems().remove(item);
                    actualizarContador.run();
                    btnRegistrar.setDisable(registrando[0] || tabla.getItems().isEmpty());
                });
                menu.getItems().add(quitar);
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
