package com.reparaciones.controllers;

import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ResultadoARevisar;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;
import com.reparaciones.utils.TextoResultadoARevisar;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * F2b: escaneo masivo "A revisar" — cada IMEI escaneado se envía al servidor, que lo
 * clasifica (tabla de reglas spec §4) y pasa a EN_REVISION los que tocan. Una línea
 * de resultado por IMEI; contador abajo.
 */
public final class ARevisarDialog {

    private ARevisarDialog() {}

    public static void abrir(Window owner, Runnable onCambios) {
        TelefonoDAO dao = new TelefonoDAO();
        Set<String> vistos = new LinkedHashSet<>();
        int[] contadores = new int[3];                 // pasados, avisos, errores
        boolean[] huboCambios = { false };

        Label lblTitulo = new Label("Escanear IMEI:");
        lblTitulo.setStyle("-fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPrefWidth(190);
        tfScan.setPromptText("Enter añade y limpia");
        Label lblScan = new Label();

        ListView<String> lista = new ListView<>();
        lista.setPrefSize(520, 300);
        Label lblContador = new Label("0 pasados a revisión · 0 avisos · 0 errores");

        Runnable actualizarContador = () -> lblContador.setText(
                contadores[0] + " pasados a revisión · " + contadores[1] + " avisos · " + contadores[2] + " errores");

        // Procesa un puñado (1..n IMEIs) contra el servidor en hilo aparte.
        java.util.function.Consumer<List<String>> procesar = imeis -> {
            List<String> nuevos = new ArrayList<>();
            for (String im : imeis) if (vistos.add(im)) nuevos.add(im);
            if (nuevos.isEmpty()) { lblScan.setText("Ya escaneado(s) en esta sesión."); return; }
            lblScan.setText("");
            new Thread(() -> {
                try {
                    List<ResultadoARevisar> res = dao.pasarARevisar(nuevos);
                    Platform.runLater(() -> {
                        for (ResultadoARevisar r : res) {
                            String texto = TextoResultadoARevisar.texto(r.getResultado());
                            lista.getItems().add(r.getImei() + "  ·  " + texto);
                            if (TextoResultadoARevisar.esPasado(r.getResultado())) { contadores[0]++; huboCambios[0] = true; }
                            else if ("NO_EXISTE".equals(r.getResultado()))          contadores[2]++;
                            else                                                     contadores[1]++;
                        }
                        actualizarContador.run();
                        lista.scrollTo(lista.getItems().size() - 1);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        nuevos.forEach(vistos::remove);   // reintentables
                        Alertas.mostrarError(e.getMessage());
                    });
                }
            }, "a-revisar").start();
        };

        Runnable intentarAnadir = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            procesar.accept(List.of(imei));
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
                        lblScan.setText("Algún IMEI del pegado está corrupto.");
                    });
                    return;
                }
                procesar.accept(res.imeis());
                Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
                return;
            }
            if (n.length() == 15) intentarAnadir.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) intentarAnadir.run(); });

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.getStyleClass().add("btn-primary");

        HBox fila = new HBox(8, lblTitulo, tfScan, lblScan);
        fila.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pie = new HBox(8, lblContador, spacer, btnCerrar);
        pie.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(10, fila, lista, pie);
        contenido.setPadding(new Insets(14));

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("A revisar — escaneo masivo");
        btnCerrar.setOnAction(ev -> ventana.close());
        ventana.setOnHidden(ev -> { if (huboCambios[0] && onCambios != null) onCambios.run(); });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(ARevisarDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }
}
