package com.reparaciones.controllers;

import com.reparaciones.dao.LoteDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.Importacion;
import com.reparaciones.models.Proveedor;
import com.reparaciones.models.VerificacionImei;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diálogo de alta manual de lote (spec F2a §3): mismo flujo de importación que
 * {@link ImportadorLoteDialog} pero con el lote creado a mano (batch + proveedor +
 * atributos comunes) y pegado masivo de IMEIs, sin fichero xlsx de por medio.
 * <p>Todo el acceso a red ocurre en hilos secundarios; las actualizaciones de la interfaz se
 * hacen siempre en el hilo de JavaFX vía {@link Platform#runLater}.</p>
 */
public final class AltaManualLoteDialog {

    private AltaManualLoteDialog() {}

    /**
     * Carga los proveedores activos y abre el diálogo de alta manual.
     *
     * @param owner       ventana propietaria del diálogo modal
     * @param onImportado se ejecuta tras una importación confirmada con éxito
     */
    public static void abrir(Window owner, Runnable onImportado) {
        new Thread(() -> {
            try {
                List<Proveedor> proveedores = new ProveedorDAO().getActivos();
                Platform.runLater(() -> new Sesion(owner, proveedores, onImportado).mostrar());
            } catch (SQLException e) {
                Platform.runLater(() -> Alertas.mostrarError("No se pudieron cargar los proveedores: " + e.getMessage()));
            }
        }, "alta-manual-lote-carga").start();
    }

    private static final class Sesion {

        private static final String ESTILO_ERROR = "-fx-font-size: 11px; -fx-text-fill: " + Colores.TEXTO_ERROR + ";";
        private static final String ESTILO_OK    = "-fx-font-size: 11px; -fx-text-fill: #2E7D32;";

        private final Window owner;
        private final List<Proveedor> proveedores;
        private final Runnable onImportado;

        private final LoteDAO loteDAO = new LoteDAO();
        private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
        private final Map<String, Double> tasasCache = new ConcurrentHashMap<>();

        private final ObservableList<String> imeis = FXCollections.observableArrayList();
        private String modeloInterno;   // puede quedar null: el alta manual permite teléfono sin modelo

        private Stage stage;
        private TextField tfBatch;
        private ComboBox<Proveedor> comboProveedor;
        private Button btnModelo;
        private Label lblModelo;
        private TextField tfStorage;
        private TextField tfColor;
        private TextField tfGrado;
        private TextField tfPrecio;
        private Label lblDivisaPrecio;
        private Label lblPrecioEur;
        private TextField tfScan;
        private Label lblErrorScan;
        private Label lblContadorImeis;
        private ListView<String> listaImeis;
        private Button btnCancelar;
        private Button btnCrear;

        /** true mientras la creación del lote está en vuelo: bloquea reenviar, cancelar y editar. */
        private boolean importando;

        Sesion(Window owner, List<Proveedor> proveedores, Runnable onImportado) {
            this.owner = owner;
            this.proveedores = proveedores;
            this.onImportado = onImportado;
        }

        void mostrar() {
            construirUi();
            stage.showAndWait();
        }

        // ─── Construcción de la UI ──────────────────────────────────────────────

        private void construirUi() {
            Label lblBatch = new Label("Batch number:");
            tfBatch = new TextField();
            tfBatch.setPromptText("p. ej. MANUAL-2026-07-08");
            tfBatch.textProperty().addListener((obs, o, n) -> actualizarBotonCrear());
            VBox boxBatch = new VBox(4, lblBatch, tfBatch);

            Label lblProveedor = new Label("Proveedor:");
            comboProveedor = new ComboBox<>(FXCollections.observableArrayList(proveedores));
            comboProveedor.setPromptText("Selecciona proveedor…");
            comboProveedor.setMaxWidth(Double.MAX_VALUE);
            comboProveedor.setVisibleRowCount(8);
            comboProveedor.setOnAction(e -> {
                Proveedor prov = comboProveedor.getValue();
                lblDivisaPrecio.setText(prov == null ? "" : prov.getDivisa());
                actualizarBotonCrear();
                actualizarPrecioEur();
            });
            VBox boxProveedor = new VBox(4, lblProveedor, comboProveedor);

            Label lblAtributos = new Label("Atributos comunes (opcionales, se aplican a todos los IMEIs):");
            lblAtributos.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");

            btnModelo = new Button("Modelo…");
            lblModelo = new Label("Sin modelo");
            lblModelo.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
            btnModelo.setOnAction(e -> SelectorModeloDialog.elegir(null).ifPresent(interno -> {
                modeloInterno = interno;
                lblModelo.setText(FormularioReparacionController.traducirModelo(interno));
            }));
            HBox filaModelo = new HBox(10, btnModelo, lblModelo);
            filaModelo.setAlignment(Pos.CENTER_LEFT);

            tfStorage = new TextField();
            tfStorage.setPromptText("Storage GB");
            tfStorage.textProperty().addListener((obs, o, n) -> {
                if (!n.matches("\\d*")) {
                    String solo = n.replaceAll("\\D", "");
                    Platform.runLater(() -> tfStorage.setText(solo));
                }
            });

            tfColor = new TextField();
            tfColor.setPromptText("Color");

            tfGrado = new TextField();
            tfGrado.setPromptText("Grado proveedor");

            tfPrecio = new TextField();
            tfPrecio.setPromptText("Precio/unidad");
            tfPrecio.textProperty().addListener((obs, o, n) -> actualizarPrecioEur());
            lblDivisaPrecio = new Label();
            lblDivisaPrecio.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
            lblPrecioEur = new Label();
            lblPrecioEur.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
            HBox filaPrecio = new HBox(8, tfPrecio, lblDivisaPrecio, lblPrecioEur);
            filaPrecio.setAlignment(Pos.CENTER_LEFT);

            VBox boxAtributos = new VBox(6, lblAtributos, filaModelo,
                    new Label("Storage GB:"), tfStorage,
                    new Label("Color:"), tfColor,
                    new Label("Grado proveedor:"), tfGrado,
                    new Label("Precio/unidad:"), filaPrecio);

            VBox contenidoScroll = new VBox(14, boxBatch, boxProveedor, boxAtributos);
            ScrollPane scroll = new ScrollPane(contenidoScroll);
            scroll.setFitToWidth(true);
            VBox.setVgrow(scroll, Priority.ALWAYS);

            Label lblScanTitulo = new Label("Escanear IMEIs:");
            lblScanTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
            tfScan = new TextField();
            tfScan.setPromptText("Escanea o pega IMEIs (15 dígitos cada uno)…");
            lblErrorScan = new Label();
            lblErrorScan.setStyle(ESTILO_ERROR);

            lblContadorImeis = new Label("0 IMEIs");
            lblContadorImeis.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");

            listaImeis = new ListView<>(imeis);
            listaImeis.setPrefHeight(160);
            listaImeis.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(String imei, boolean empty) {
                    super.updateItem(imei, empty);
                    if (empty || imei == null) { setText(null); setGraphic(null); return; }
                    Label lbl = new Label(imei);
                    lbl.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Button btnQuitar = new Button("✕");
                    btnQuitar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Colores.AZUL_GRIS
                            + "; -fx-cursor: hand; -fx-font-size: 12px;");
                    btnQuitar.setOnAction(e -> imeis.remove(imei));
                    HBox fila = new HBox(8, lbl, spacer, btnQuitar);
                    fila.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(fila);
                }
            });
            imeis.addListener((ListChangeListener<String>) c -> actualizarContadorImeis());

            btnCancelar = new Button("Cancelar");
            btnCancelar.setOnAction(e -> stage.close());

            btnCrear = new Button("Crear lote (0 teléfonos)");
            btnCrear.getStyleClass().add("btn-primary");
            btnCrear.setOnAction(e -> confirmar());

            HBox botones = new HBox(10, btnCancelar, btnCrear);
            botones.setAlignment(Pos.CENTER_RIGHT);

            VBox raiz = new VBox(12, scroll, new Separator(), lblScanTitulo, tfScan, lblErrorScan,
                    lblContadorImeis, listaImeis, botones);
            raiz.setPadding(new Insets(16));

            stage = new Stage();
            stage.setTitle("Alta manual de lote");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);
            Scene scene = new Scene(raiz, 520, 640);
            scene.getStylesheets().add(AltaManualLoteDialog.class.getResource("/styles/app.css").toExternalForm());
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> { if (importando) e.consume(); });

            // ── Escaneo (patrón EXACTO de PendientesSuperTecnicoController: pegado masivo) ──
            Runnable intentarAnadir = () -> {
                String imei = tfScan.getText().trim();
                if (imei.length() != 15) return;
                if (imeis.contains(imei)) { lblErrorScan.setStyle(ESTILO_ERROR); lblErrorScan.setText("Ese IMEI ya está en la lista."); return; }
                lblErrorScan.setText("");
                imeis.add(imei);
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
                            lblErrorScan.setStyle(ESTILO_ERROR);
                            lblErrorScan.setText("Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.");
                        });
                        return;
                    }
                    int anadidos = 0, duplicados = 0;
                    for (String im : res.imeis()) {
                        if (imeis.contains(im)) { duplicados++; continue; }
                        imeis.add(im);
                        anadidos++;
                    }
                    final String resumen = anadidos + " IMEIs añadidos"
                            + (duplicados > 0 ? " · " + duplicados + " ya estaban en la lista." : ".");
                    Platform.runLater(() -> {
                        tfScan.clear();
                        tfScan.requestFocus();
                        lblErrorScan.setStyle(ESTILO_OK);
                        lblErrorScan.setText(resumen);
                    });
                    return;
                }
                lblErrorScan.setStyle(ESTILO_ERROR);
                lblErrorScan.setText("");
                if (n.length() == 15) intentarAnadir.run();
            });
            tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) intentarAnadir.run(); });

            actualizarBotonCrear();
        }

        // ─── Contador / botón ────────────────────────────────────────────────────

        private void actualizarContadorImeis() {
            lblContadorImeis.setText(imeis.size() + " IMEIs");
            actualizarBotonCrear();
        }

        private void actualizarBotonCrear() {
            if (importando) { btnCrear.setDisable(true); return; }   // en vuelo: corta cualquier recálculo
            boolean batchVacio = tfBatch.getText() == null || tfBatch.getText().trim().isEmpty();
            boolean sinProveedor = comboProveedor.getValue() == null;
            int n = imeis.size();
            btnCrear.setText("Crear lote (" + n + " teléfonos)");
            btnCrear.setDisable(batchVacio || sinProveedor || n == 0);
        }

        // ─── ≈ € del precio/unidad ───────────────────────────────────────────────

        private void actualizarPrecioEur() {
            Proveedor prov = comboProveedor.getValue();
            BigDecimal precio = parsePrecio();
            if (prov == null || precio == null) { lblPrecioEur.setText(""); return; }
            String divisa = prov.getDivisa();
            if ("EUR".equalsIgnoreCase(divisa)) { pintarPrecioEur(precio, 1.0); return; }
            Double cached = tasasCache.get(divisa);
            if (cached != null) { pintarPrecioEur(precio, cached); return; }
            lblPrecioEur.setText("Calculando…");
            new Thread(() -> {
                try {
                    double t = tipoCambioDAO.getTasa(divisa);
                    tasasCache.put(divisa, t);
                    Platform.runLater(this::actualizarPrecioEur);
                } catch (SQLException e) {
                    Platform.runLater(() -> lblPrecioEur.setText("No se pudo calcular (" + e.getMessage() + ")"));
                }
            }, "tasa-alta-manual-lote").start();
        }

        private void pintarPrecioEur(BigDecimal precio, double tasa) {
            BigDecimal eur = precio.multiply(BigDecimal.valueOf(tasa)).setScale(2, RoundingMode.HALF_UP);
            lblPrecioEur.setText(String.format(Locale.US, "≈ %.2f €", eur));
        }

        private double obtenerTasa(String divisa) throws SQLException {
            if ("EUR".equalsIgnoreCase(divisa)) return 1.0;
            Double cached = tasasCache.get(divisa);
            if (cached != null) return cached;
            double t = tipoCambioDAO.getTasa(divisa);
            tasasCache.put(divisa, t);
            return t;
        }

        // ─── Parseo de atributos opcionales ──────────────────────────────────────

        private Integer parseStorageGb() {
            String texto = tfStorage.getText() == null ? "" : tfStorage.getText().trim();
            if (texto.isEmpty()) return null;
            try { return Integer.valueOf(texto); } catch (NumberFormatException e) { return null; }
        }

        private BigDecimal parsePrecio() {
            String texto = tfPrecio.getText() == null ? "" : tfPrecio.getText().trim();
            if (texto.isEmpty()) return null;
            try { return new BigDecimal(texto.replace(",", ".")); } catch (NumberFormatException e) { return null; }
        }

        private static String textoOrNull(String s) {
            if (s == null) return null;
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }

        // ─── Bloqueo en vuelo ─────────────────────────────────────────────────────

        /** Bloquea/desbloquea la edición del diálogo mientras la creación del lote está en vuelo. */
        private void setImportando(boolean enVuelo) {
            importando = enVuelo;
            btnCancelar.setDisable(enVuelo);
            tfBatch.setDisable(enVuelo);
            comboProveedor.setDisable(enVuelo);
            btnModelo.setDisable(enVuelo);
            tfStorage.setDisable(enVuelo);
            tfColor.setDisable(enVuelo);
            tfGrado.setDisable(enVuelo);
            tfPrecio.setDisable(enVuelo);
            tfScan.setDisable(enVuelo);
            listaImeis.setDisable(enVuelo);
            actualizarBotonCrear();
        }

        // ─── Confirmar ───────────────────────────────────────────────────────────

        private void confirmar() {
            String batch = tfBatch.getText().trim();
            Proveedor proveedor = comboProveedor.getValue();
            List<String> imeisSnapshot = new ArrayList<>(imeis);
            if (batch.isEmpty() || proveedor == null || imeisSnapshot.isEmpty()) return;

            String modelo = modeloInterno;
            Integer storageGb = parseStorageGb();
            String color = textoOrNull(tfColor.getText());
            String grado = textoOrNull(tfGrado.getText());
            BigDecimal precio = parsePrecio();
            String divisa = proveedor.getDivisa();
            int idProv = proveedor.getIdProv();

            setImportando(true);
            new Thread(() -> {
                try {
                    List<VerificacionImei> verificaciones = loteDAO.verificar(imeisSnapshot);
                    List<String> activos = verificaciones.stream()
                            .filter(VerificacionImei::esActivo)
                            .map(VerificacionImei::getImei)
                            .toList();
                    List<String> imeisFinal = imeisSnapshot.stream()
                            .filter(imei -> !activos.contains(imei))
                            .toList();

                    if (imeisFinal.isEmpty()) {
                        Platform.runLater(() -> {
                            imeis.removeAll(activos);
                            setImportando(false);
                            mostrarAvisoActivos(activos);
                        });
                        return;
                    }

                    double tasa = obtenerTasa(divisa);
                    BigDecimal precioEur = precio == null ? null
                            : precio.multiply(BigDecimal.valueOf(tasa)).setScale(2, RoundingMode.HALF_UP);
                    List<Importacion.TelefonoImport> telefonos = new ArrayList<>();
                    for (String imei : imeisFinal) {
                        telefonos.add(new Importacion.TelefonoImport(imei, modelo, storageGb, color, grado,
                                precio, divisa, precioEur, false));
                    }
                    Importacion.LoteImport loteImport = new Importacion.LoteImport(batch, idProv, null, telefonos);
                    Importacion.Respuesta resp = loteDAO.importar(new Importacion.Request(List.of(loteImport)));

                    Platform.runLater(() -> {
                        importando = false;
                        if (!activos.isEmpty()) {
                            imeis.removeAll(activos);
                            mostrarAvisoActivos(activos);
                        }
                        stage.close();
                        // Diferir un pulso: mostrar el alert en el mismo pulso en que se cierra
                        // el stage modal lo deja en blanco en Windows (sin pase de render).
                        Platform.runLater(() -> {
                            mostrarResultado(resp);
                            onImportado.run();
                        });
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        setImportando(false);
                        Alertas.mostrarError("No se pudo crear el lote: " + e.getMessage());
                    });
                }
            }, "alta-manual-lote-importar").start();
        }

        private void mostrarAvisoActivos(List<String> activos) {
            String cuerpo = (activos.size() == 1 ? "1 IMEI ya está activo" : activos.size() + " IMEIs ya están activos")
                    + " (en ciclo o con trabajo abierto) y se han excluido del alta:\n\n" + String.join("\n", activos);
            Alert alert = new Alert(Alert.AlertType.WARNING, cuerpo);
            alert.setHeaderText(null);
            alert.initOwner(stage);
            alert.showAndWait();
        }

        private void mostrarResultado(Importacion.Respuesta resp) {
            StringBuilder sb = new StringBuilder();
            sb.append(resp.telefonos()).append(" teléfonos importados en ").append(resp.lotes()).append(" lote(s)");
            if (resp.conflictosOmitidos() != null && !resp.conflictosOmitidos().isEmpty()) {
                sb.append("\n\n").append(resp.conflictosOmitidos().size()).append(" conflictos omitidos:\n")
                  .append(String.join("\n", resp.conflictosOmitidos()));
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION, sb.toString());
            alert.setHeaderText(null);
            if (owner != null) alert.initOwner(owner);
            alert.showAndWait();
        }
    }
}
