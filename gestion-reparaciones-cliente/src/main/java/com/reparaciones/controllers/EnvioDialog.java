package com.reparaciones.controllers;

import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.models.ItemEnvio;
import com.reparaciones.models.ResultadoEnvioLote;
import com.reparaciones.models.TelefonoInventario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;
import com.reparaciones.utils.TextoResultadoEnvio;
import com.reparaciones.utils.UbicacionTexto;
import javafx.application.Platform;
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
import java.util.stream.Collectors;

/**
 * F2c: remesa de salida — escaneo local (a diferencia de {@link ARevisarDialog}, aquí NO se
 * llama al servidor por cada IMEI: solo se acumulan en una lista con dedupe) y confirmación
 * única que envía todo el lote de una vez ({@code TelefonoDAO#enviarTelefonos}). Patrón de
 * ventana/hilos (Stage APPLICATION_MODAL + Thread + Platform.runLater) calcado de
 * {@link ARevisarDialog}. Cada IMEI escaneado se clasifica en vivo contra el snapshot de
 * inventario cargado al abrir (ajuste smoke F2c #4); el servidor sigue siendo la autoridad
 * final al confirmar, así que esta clasificación puede quedar desfasada si el inventario
 * cambió entre cargar la tabla y escanear.
 */
public final class EnvioDialog {

    private EnvioDialog() {}

    /** Centinela "sin cliente" en el combo de cabecera, mismo patrón que SelectorClienteDialog/PendientesSuperTecnicoController. */
    private static final Cliente SIN_CLIENTE = new Cliente(-1, "— sin cliente —", true, null);

    /**
     * Sufijo de clasificación en vivo de un IMEI contra el snapshot de inventario cargado al
     * abrir el diálogo. {@code "OK"} es el único sufijo enviable; el resto son motivos de
     * rechazo (ausente, histórico u otro estado), redactados en la misma familia de textos
     * que {@link TextoResultadoEnvio} usa para el resultado real del servidor.
     */
    private static String sufijoClasificacion(String imei, Map<String, TelefonoInventario> porImei) {
        TelefonoInventario t = porImei.get(imei);
        if (t == null) return "no existe";
        String estado = UbicacionTexto.estado(t);
        if ("OK".equals(estado)) return "OK";
        if ("Histórico".equals(estado)) return "histórico — dar de alta en un lote";
        return "está " + estado;
    }

    /** IMEI de una fila de la lista: en modo resultados el texto es "imei  ·  <texto servidor>". */
    private static String imeiDeFila(String item) {
        int idx = item.indexOf("  ·  ");
        return idx < 0 ? item : item.substring(0, idx);
    }

    private static void copiarAlPortapapeles(String texto) {
        ClipboardContent content = new ClipboardContent();
        content.putString(texto);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static void abrir(Window owner, List<TelefonoInventario> preseleccion,
                              List<TelefonoInventario> inventarioCompleto, Runnable onCambios) {
        TelefonoDAO telefonoDAO = new TelefonoDAO();
        ClienteDAO clienteDAO = new ClienteDAO();
        Map<String, TelefonoInventario> porImei = inventarioCompleto == null ? Map.of() : inventarioCompleto.stream()
                .filter(t -> t.getImei() != null)
                .collect(Collectors.toMap(TelefonoInventario::getImei, java.util.function.Function.identity(), (a, b) -> a));
        Set<String> vistos = new LinkedHashSet<>();
        boolean[] huboCambios = { false };
        boolean[] enviado = { false };    // true tras confirmar con éxito: la lista pasa a modo resultados
        boolean[] enviando = { false };   // true mientras la petición está en vuelo: bloquea reabrir el guard doble-click

        // ── Cabecera de remesa ───────────────────────────────────────────────
        Label lblCliente = new Label("Cliente:");
        ComboBox<Cliente> cmbCliente = new ComboBox<>();
        cmbCliente.setPrefWidth(200);
        cmbCliente.getItems().add(SIN_CLIENTE);
        cmbCliente.setValue(SIN_CLIENTE);
        cmbCliente.setDisable(true);   // se habilita cuando termine de cargar la lista de activos

        TextField tfDestino = new TextField();
        tfDestino.setPromptText("Destino libre (mayorista/plataforma)");
        tfDestino.setPrefWidth(220);

        TextField tfReferencia = new TextField();
        tfReferencia.setPromptText("Referencia (albarán/tracking)");
        tfReferencia.setPrefWidth(200);

        HBox cabecera = new HBox(8, lblCliente, cmbCliente, tfDestino, tfReferencia);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        new Thread(() -> {
            try {
                List<Cliente> activos = clienteDAO.getActivos();
                Platform.runLater(() -> {
                    cmbCliente.getItems().addAll(activos);
                    cmbCliente.setDisable(false);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> Alertas.mostrarError("No se pudieron cargar los clientes: " + e.getMessage()));
            }
        }, "envio-clientes").start();

        // ── Escáner (calco del listener de ARevisarDialog; sin llamada al servidor) ──
        Label lblTitulo = new Label("Escanear IMEI:");
        lblTitulo.setStyle("-fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPrefWidth(190);
        tfScan.setPromptText("Enter añade y limpia");
        Label lblScan = new Label();

        ListView<String> lista = new ListView<>();
        lista.setPrefSize(520, 300);
        Label lblContador = new Label("0 teléfonos (0 enviables)");

        // Tras confirmar, la lista pasa a modo resultados (textos del servidor); el contador
        // deja de recalcularse por esta vía (se fija directamente al recibir la respuesta).
        Runnable actualizarContador = () -> {
            long enviables = lista.getItems().stream()
                    .filter(imei -> "OK".equals(sufijoClasificacion(imei, porImei))).count();
            lblContador.setText(lista.getItems().size() + " teléfonos (" + enviables + " enviables)");
        };

        Button btnEnviar = new Button("Enviar remesa");
        btnEnviar.getStyleClass().add("btn-primary");
        btnEnviar.setDisable(true);
        Button btnCerrar = new Button("Cerrar");

        java.util.function.Consumer<List<String>> anadir = imeis -> {
            List<String> nuevos = new ArrayList<>();
            for (String im : imeis) if (vistos.add(im)) nuevos.add(im);
            if (nuevos.isEmpty()) {
                if (!imeis.isEmpty()) { lblScan.setStyle(""); lblScan.setText("Ya añadido a esta remesa."); }
                return;
            }
            lblScan.setStyle(""); lblScan.setText("");
            lista.getItems().addAll(nuevos);
            lista.scrollTo(lista.getItems().size() - 1);
            actualizarContador.run();
            btnEnviar.setDisable(enviando[0] || lista.getItems().isEmpty());
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

        // Preselección (Enviar seleccionados desde la tabla): puebla la lista al abrir.
        if (preseleccion != null && !preseleccion.isEmpty()) {
            List<String> imeisPre = new ArrayList<>();
            for (TelefonoInventario t : preseleccion) if (t.getImei() != null) imeisPre.add(t.getImei());
            anadir.accept(imeisPre);
        }

        // Botón "✕" visible por fila mientras la remesa no se ha enviado ni está en vuelo (calco
        // de AltaManualLoteDialog): sustituye el antiguo "Quitar" del menú contextual, que era
        // indescubrible. Tras enviar, la lista pasa a modo resultados y deja de tener sentido
        // quitar filas; mientras la petición está en vuelo el botón se oculta (cierra el hueco
        // que antes permitía quitar filas locales mientras el servidor procesaba el envío).
        // Pre-envío, cada fila muestra además la clasificación en vivo (imei · sufijo, sufijo
        // coloreado); en modo resultados el texto es el literal que devuelve el servidor, sin
        // clasificación ni color añadidos. El menú contextual "Copiar IMEI" está disponible en
        // ambos modos (copia el IMEI, sin el sufijo/texto de resultado).
        lista.setCellFactory(lv -> new ListCell<>() {
            private final Label lblImei = new Label();
            private final Label lblSufijo = new Label();
            private final Region spacer = new Region();
            private final Button btnQuitar = new Button("✕");
            private final HBox caja = new HBox(0, lblImei, lblSufijo, spacer, btnQuitar);
            private final ContextMenu menu = new ContextMenu();
            private final MenuItem copiarImei = new MenuItem("Copiar IMEI");
            {
                caja.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                btnQuitar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Colores.AZUL_GRIS
                        + "; -fx-cursor: hand; -fx-font-size: 12px;");
                menu.getItems().add(copiarImei);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setContextMenu(null); return; }
                copiarImei.setOnAction(e -> copiarAlPortapapeles(imeiDeFila(item)));
                setContextMenu(menu);
                if (enviado[0]) {
                    setGraphic(null);
                    setText(item);
                    return;
                }
                String sufijo = sufijoClasificacion(item, porImei);
                boolean enviable = "OK".equals(sufijo);
                lblImei.setText(item + "  ·  ");
                lblSufijo.setText(sufijo);
                lblSufijo.setStyle("-fx-text-fill: " + (enviable ? Colores.VERDE_OK : Colores.TEXTO_ERROR) + ";");
                btnQuitar.setVisible(!enviando[0]);
                btnQuitar.setManaged(!enviando[0]);
                btnQuitar.setOnAction(e -> {
                    vistos.remove(item);
                    lista.getItems().remove(item);
                    actualizarContador.run();
                    btnEnviar.setDisable(enviando[0] || lista.getItems().isEmpty());
                });
                setText(null);
                setGraphic(caja);
            }
        });

        Runnable confirmar = () -> {
            Cliente cli = cmbCliente.getValue();
            Integer idCli = (cli == null || cli.getIdCli() == -1) ? null : cli.getIdCli();
            String destino = tfDestino.getText() == null ? "" : tfDestino.getText().trim();
            if (idCli == null && destino.isEmpty()) {
                lblScan.setStyle("-fx-text-fill: " + Colores.TEXTO_ERROR + ";");
                lblScan.setText("El envío necesita destino (cliente o texto)");
                return;
            }
            lblScan.setStyle(""); lblScan.setText("");
            String referencia = tfReferencia.getText() == null ? null : tfReferencia.getText().trim();
            if (referencia != null && referencia.isEmpty()) referencia = null;
            String destinoTexto = destino.isEmpty() ? null : destino;
            List<String> imeis = new ArrayList<>(lista.getItems());

            enviando[0] = true;
            btnEnviar.setDisable(true);   // guard doble-click
            tfScan.setDisable(true);      // evita colar IMEIs nuevos mientras la petición está en vuelo
            lista.refresh();              // oculta el botón ✕ de cada fila mientras la petición está en vuelo
            String referenciaFinal = referencia;
            new Thread(() -> {
                try {
                    ResultadoEnvioLote res = telefonoDAO.enviarTelefonos(idCli, destinoTexto, referenciaFinal, imeis);
                    Platform.runLater(() -> {
                        enviado[0] = true;
                        int ok = 0, rechazados = 0;
                        lista.getItems().clear();
                        for (ItemEnvio it : res.getItems()) {
                            lista.getItems().add(it.getImei() + "  ·  " + TextoResultadoEnvio.texto(it.getResultado(), it.getEstado()));
                            if (TextoResultadoEnvio.esEnviado(it.getResultado())) { ok++; huboCambios[0] = true; }
                            else rechazados++;
                        }
                        lblContador.setText(ok + " enviados · " + rechazados + " rechazados");
                        btnEnviar.setVisible(false); btnEnviar.setManaged(false);
                        // La remesa es una: para otra, cerrar y reabrir el diálogo.
                        tfScan.setDisable(true);
                        cmbCliente.setDisable(true);
                        tfDestino.setDisable(true);
                        tfReferencia.setDisable(true);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        Alertas.mostrarError(e.getMessage());
                        enviando[0] = false;
                        btnEnviar.setDisable(false);
                        tfScan.setDisable(false);
                        lista.refresh();   // vuelve a mostrar el botón ✕ de cada fila
                    });
                }
            }, "envio-confirmar").start();
        };
        btnEnviar.setOnAction(e -> confirmar.run());

        HBox filaScan = new HBox(8, lblTitulo, tfScan, lblScan);
        filaScan.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pie = new HBox(8, lblContador, spacer, btnEnviar, btnCerrar);
        pie.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(10, cabecera, filaScan, lista, pie);
        contenido.setPadding(new Insets(14));

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("Enviar teléfonos — remesa");
        btnCerrar.setOnAction(ev -> ventana.close());
        ventana.setOnHidden(ev -> { if (huboCambios[0] && onCambios != null) onCambios.run(); });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(EnvioDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }
}
