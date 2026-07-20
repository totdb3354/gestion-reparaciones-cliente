package com.reparaciones.controllers;

import com.reparaciones.dao.LoteDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.Importacion;
import com.reparaciones.models.Proveedor;
import com.reparaciones.models.VerificacionImei;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;
import com.reparaciones.utils.LookupModelosImeis;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
                List<Proveedor> proveedores = new ProveedorDAO().getActivos(ProveedorDAO.TIPO_TELEFONOS);
                Platform.runLater(() -> new Sesion(owner, proveedores, onImportado).mostrar());
            } catch (SQLException e) {
                Platform.runLater(() -> Alertas.mostrarError("No se pudieron cargar los suppliers: " + e.getMessage()));
            }
        }, "alta-manual-lote-carga").start();
    }

    private static final class Sesion {

        private static final String ESTILO_ERROR = "-fx-font-size: 11px; -fx-text-fill: " + Colores.TEXTO_ERROR + ";";
        private static final String ESTILO_OK    = "-fx-font-size: 11px; -fx-text-fill: #2E7D32;";

        /** Entrada centinela de creación inline, siempre la última del combo de supplier. */
        private static final Proveedor CREAR = new Proveedor(-1, "➕ Crear supplier…", true, "EUR", null);

        private final Window owner;
        private final List<Proveedor> proveedores;
        private final Runnable onImportado;
        /** true mientras se cambia la selección del combo de supplier por código (evita reentradas del onAction). */
        private boolean sincronizandoProveedor;

        private final LoteDAO loteDAO = new LoteDAO();
        private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
        private final Map<String, Double> tasasCache = new ConcurrentHashMap<>();

        private final ObservableList<String> imeis = FXCollections.observableArrayList();
        private String modeloInterno;   // puede quedar null: el alta manual permite teléfono sin modelo

        /** Modelo detectado por fila (BD vía loteDAO.verificar o API vía colaLookup), IMEI → modelo interno. */
        private final Map<String, String> modelosDetectados = new ConcurrentHashMap<>();
        /** Modelo editado a mano por fila (menú contextual "Editar modelo…"), IMEI → modelo interno. */
        private final Map<String, String> modelosManuales = new ConcurrentHashMap<>();
        /** IMEIs con lookup de API en curso; solo se toca desde el hilo de JavaFX. */
        private final Set<String> lookupsPendientes = new HashSet<>();
        private final TelefonoDAO telefonoDAO = new TelefonoDAO();
        private final LookupModelosImeis colaLookup =
                new LookupModelosImeis(this::lookupModelo, this::esperar, this::onModeloDetectado);
        /** Hilo actual de {@link LookupModelosImeis#procesarPendientes()}; se relanza si no está vivo. */
        private Thread hiloLookup;
        /** true tras cerrar el diálogo: los callbacks de red en vuelo se vuelven no-op. */
        private boolean cerrado;

        private Stage stage;
        private TextField tfBatch;
        private ComboBox<Proveedor> comboProveedor;
        private Button btnModelo;
        private Label lblModelo;
        private ComboBox<Integer> comboStorage;
        private ComboBox<String> comboColor;
        private TextField tfGrado;
        private CheckBox cbEsim;
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
            this.proveedores = new ArrayList<>(proveedores);
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

            Label lblProveedor = new Label("Supplier:");
            comboProveedor = new ComboBox<>(FXCollections.observableArrayList(itemsProveedorConCentinela()));
            comboProveedor.setPromptText("Selecciona supplier…");
            comboProveedor.setMaxWidth(Double.MAX_VALUE);
            comboProveedor.setVisibleRowCount(8);
            comboProveedor.setOnAction(e -> {
                if (sincronizandoProveedor) return;
                Proveedor prov = comboProveedor.getValue();
                if (prov == CREAR) {
                    // Vuelve la selección a null PRIMERO: que lblDivisaPrecio no herede el EUR del centinela.
                    sincronizandoProveedor = true;
                    comboProveedor.setValue(null);
                    sincronizandoProveedor = false;
                    lblDivisaPrecio.setText("");
                    actualizarBotonCrear();
                    actualizarPrecioEur();
                    NuevoSupplierDialog.abrir(stage, null, this::onSupplierCreado);
                    return;
                }
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
                repoblarStorage(comboStorage.getValue());
                repoblarColor(comboColor.getValue());
            }));
            HBox filaModelo = new HBox(10, btnModelo, lblModelo);
            filaModelo.setAlignment(Pos.CENTER_LEFT);

            comboStorage = new ComboBox<>();
            comboStorage.setEditable(false);
            comboStorage.setMaxWidth(Double.MAX_VALUE);
            comboStorage.setButtonCell(celdaStorage());
            comboStorage.setCellFactory(lv -> celdaStorage());
            repoblarStorage(null);

            comboColor = new ComboBox<>();
            comboColor.setEditable(false);
            comboColor.setMaxWidth(Double.MAX_VALUE);
            comboColor.setButtonCell(celdaColor());
            comboColor.setCellFactory(lv -> celdaColor());
            repoblarColor(null);

            tfGrado = new TextField();
            tfGrado.setPromptText("Grado proveedor");
            cbEsim = new CheckBox("eSIM");
            HBox filaGrado = new HBox(10, tfGrado, cbEsim);
            filaGrado.setAlignment(Pos.CENTER_LEFT);

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
                    new Label("Storage GB:"), comboStorage,
                    new Label("Color:"), comboColor,
                    new Label("Grado proveedor:"), filaGrado,
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
                    if (empty || imei == null) { setText(null); setGraphic(null); setContextMenu(null); return; }
                    Label lbl = new Label(imei);
                    lbl.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
                    String manualActual = modelosManuales.get(imei);
                    boolean manual = manualActual != null && !manualActual.isBlank();
                    Label lblModeloFila = new Label(textoModeloFila(imei));
                    lblModeloFila.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Colores.AZUL_GRIS
                            + (manual ? "; -fx-font-weight: bold;" : ";"));
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Button btnQuitar = new Button("✕");
                    btnQuitar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Colores.AZUL_GRIS
                            + "; -fx-cursor: hand; -fx-font-size: 12px;");
                    btnQuitar.setOnAction(e -> quitarFila(imei));
                    HBox fila = new HBox(8, lbl, lblModeloFila, spacer, btnQuitar);
                    fila.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(fila);

                    ContextMenu menu = new ContextMenu();
                    MenuItem editarModelo = new MenuItem("Editar modelo…");
                    editarModelo.setOnAction(e -> editarModeloFila(imei));
                    menu.getItems().add(editarModelo);
                    setContextMenu(menu);
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
            // Cierre (cancelar o X, o tras crear el lote): los callbacks de red en vuelo pasan a no-op
            // y la cola de lookup deja de encadenar peticiones (no seguir gastando cupo de API por un diálogo cerrado).
            stage.setOnHidden(e -> { cerrado = true; colaLookup.detener(); });

            // ── Escaneo (patrón EXACTO de PendientesSuperTecnicoController: pegado masivo) ──
            Runnable intentarAnadir = () -> {
                String imei = tfScan.getText().trim();
                if (imei.length() != 15) return;
                if (imeis.contains(imei)) { lblErrorScan.setStyle(ESTILO_ERROR); lblErrorScan.setText("Ese IMEI ya está en la lista."); return; }
                lblErrorScan.setText("");
                imeis.add(imei);
                verificarNuevos(List.of(imei));
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
                    List<String> nuevosPegado = new ArrayList<>();
                    for (String im : res.imeis()) {
                        if (imeis.contains(im)) { duplicados++; continue; }
                        imeis.add(im);
                        nuevosPegado.add(im);
                        anadidos++;
                    }
                    verificarNuevos(nuevosPegado);
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

        // ─── Combo de supplier: centinela "Crear supplier…" y creación inline ────

        /** Items del combo de supplier: los activos + el centinela {@link #CREAR} siempre al final. */
        private List<Proveedor> itemsProveedorConCentinela() {
            List<Proveedor> items = new ArrayList<>(proveedores);
            items.add(CREAR);
            return items;
        }

        private Proveedor buscarProveedorPorNombre(String nombre) {
            if (nombre == null) return null;
            String n = nombre.trim();
            return proveedores.stream()
                    .filter(p -> p.getNombre() != null && p.getNombre().trim().equalsIgnoreCase(n))
                    .findFirst().orElse(null);
        }

        /**
         * Callback de {@link NuevoSupplierDialog#abrir} tras crear un supplier desde el centinela
         * del combo: relanza la carga de suppliers activos y, en el hilo de JavaFX, repuebla el
         * combo (centinela de nuevo al final) seleccionando el recién creado por nombre.
         */
        private void onSupplierCreado(String nombreCreado) {
            new Thread(() -> {
                try {
                    List<Proveedor> nuevos = new ProveedorDAO().getActivos(ProveedorDAO.TIPO_TELEFONOS);
                    Platform.runLater(() -> {
                        proveedores.clear();
                        proveedores.addAll(nuevos);
                        Proveedor creado = buscarProveedorPorNombre(nombreCreado);
                        comboProveedor.setItems(FXCollections.observableArrayList(itemsProveedorConCentinela()));
                        comboProveedor.setValue(creado);
                    });
                } catch (SQLException ex) {
                    Platform.runLater(() -> Alertas.mostrarError("No se pudieron recargar los suppliers: " + ex.getMessage()));
                }
            }, "alta-manual-recarga-proveedores").start();
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

        // ─── Detección de modelo por fila (BD vía verificar + API en cola) ───────

        /**
         * Verifica los IMEIs recién añadidos contra la BD (una llamada por evento de alta: scan
         * único o pegado masivo). Los que la BD ya conoce con modelo se resuelven al momento;
         * el resto se encola para lookup por API. Si la verificación falla por red, se encolan
         * todos los nuevos igualmente (la cola tolera fallos por fila, spec §6).
         */
        private void verificarNuevos(List<String> nuevos) {
            if (nuevos.isEmpty()) return;
            new Thread(() -> {
                List<VerificacionImei> verificaciones;
                boolean falloRed;
                try {
                    verificaciones = loteDAO.verificar(nuevos);
                    falloRed = false;
                } catch (SQLException e) {
                    verificaciones = List.of();
                    falloRed = true;
                }
                List<VerificacionImei> resultado = verificaciones;
                boolean fallo = falloRed;
                Platform.runLater(() -> procesarVerificacion(nuevos, resultado, fallo));
            }, "alta-manual-lote-verificar").start();
        }

        private void procesarVerificacion(List<String> nuevos, List<VerificacionImei> verificaciones, boolean falloRed) {
            if (cerrado) return;
            List<String> paraLookup = new ArrayList<>();
            if (falloRed) {
                for (String imei : nuevos) if (imeis.contains(imei)) paraLookup.add(imei);
            } else {
                Map<String, VerificacionImei> porImei = new HashMap<>();
                for (VerificacionImei v : verificaciones) porImei.put(v.getImei(), v);
                for (String imei : nuevos) {
                    if (!imeis.contains(imei)) continue;   // fila ya quitada mientras se verificaba
                    VerificacionImei v = porImei.get(imei);
                    if (v != null && v.getModelo() != null && !v.getModelo().isBlank()) {
                        modelosDetectados.put(imei, v.getModelo());
                    } else {
                        paraLookup.add(imei);
                    }
                }
            }
            if (!paraLookup.isEmpty()) {
                lookupsPendientes.addAll(paraLookup);
                colaLookup.encolar(paraLookup);
                asegurarHiloLookup();
            }
            listaImeis.refresh();
        }

        /** Relanza el hilo de proceso de la cola de lookup si no está vivo (solo desde el hilo de JavaFX). */
        private void asegurarHiloLookup() {
            if (hiloLookup == null || !hiloLookup.isAlive()) {
                hiloLookup = new Thread(colaLookup::procesarPendientes, "alta-manual-lookup");
                hiloLookup.setDaemon(true);
                hiloLookup.start();
            }
        }

        /** Adapta {@link TelefonoDAO#getModelo(String)} (checked) a la {@code Function} sin checked de la cola. */
        private String lookupModelo(String imei) {
            try {
                return telefonoDAO.getModelo(imei);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private void esperar(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** Callback de {@link LookupModelosImeis}: llega en un hilo de fondo, salta al de JavaFX. */
        private void onModeloDetectado(LookupModelosImeis.Resultado r) {
            Platform.runLater(() -> {
                if (cerrado) return;
                lookupsPendientes.remove(r.imei());
                if (!imeis.contains(r.imei())) return;   // fila quitada mientras se buscaba
                if (r.modelo() != null && !r.modelo().isBlank()) {
                    modelosDetectados.put(r.imei(), r.modelo());
                }
                listaImeis.refresh();
            });
        }

        /** Texto de modelo junto al IMEI en la lista (spec §3: manual > detectado > "—"). */
        private String textoModeloFila(String imei) {
            String manual = modelosManuales.get(imei);
            if (manual != null && !manual.isBlank()) return FormularioReparacionController.traducirModelo(manual);
            String detectado = modelosDetectados.get(imei);
            if (detectado != null && !detectado.isBlank()) return FormularioReparacionController.traducirModelo(detectado);
            if (lookupsPendientes.contains(imei)) return "detectando…";
            return "—";
        }

        private void editarModeloFila(String imei) {
            String actual = modelosManuales.getOrDefault(imei, modelosDetectados.get(imei));
            SelectorModeloDialog.elegir(actual).ifPresent(elegido -> {
                modelosManuales.put(imei, elegido);
                listaImeis.refresh();
            });
        }

        private void quitarFila(String imei) {
            imeis.remove(imei);
            colaLookup.descartar(imei);
            modelosManuales.remove(imei);
            modelosDetectados.remove(imei);
            lookupsPendientes.remove(imei);
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

        // ─── Selectores por modelo (storage / color) ─────────────────────────────

        /** Repuebla el combo de storage según el modelo común; conserva la selección si sigue siendo válida. */
        private void repoblarStorage(Integer seleccionActual) {
            List<Integer> capacidades = modeloInterno == null
                    ? CatalogoAtributos.CAPACIDADES_TODAS
                    : CatalogoAtributos.capacidadesDe(modeloInterno);
            if (capacidades.isEmpty()) capacidades = CatalogoAtributos.CAPACIDADES_TODAS;
            List<Integer> items = new ArrayList<>();
            items.add(null);
            items.addAll(capacidades);
            comboStorage.setItems(FXCollections.observableArrayList(items));
            comboStorage.setValue(items.contains(seleccionActual) ? seleccionActual : null);
        }

        /** Repuebla el combo de color según el modelo común; conserva la selección si sigue siendo válida. */
        private void repoblarColor(String seleccionActual) {
            List<String> colores = modeloInterno == null
                    ? CatalogoAtributos.COLORES_TODOS
                    : CatalogoAtributos.coloresDe(modeloInterno);
            if (colores.isEmpty()) colores = CatalogoAtributos.COLORES_TODOS;
            List<String> items = new ArrayList<>();
            items.add(null);
            items.addAll(colores);
            comboColor.setItems(FXCollections.observableArrayList(items));
            comboColor.setValue(items.contains(seleccionActual) ? seleccionActual : null);
        }

        private static ListCell<Integer> celdaStorage() {
            return new ListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : (item == null ? "—" : item + " GB"));
                }
            };
        }

        private static ListCell<String> celdaColor() {
            return new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : (item == null ? "—" : item));
                }
            };
        }

        // ─── Parseo de atributos opcionales ──────────────────────────────────────

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
            comboStorage.setDisable(enVuelo);
            comboColor.setDisable(enVuelo);
            tfGrado.setDisable(enVuelo);
            cbEsim.setDisable(enVuelo);
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

            Integer storageGb = comboStorage.getValue();
            String color = comboColor.getValue();
            String grado = textoOrNull(tfGrado.getText());
            BigDecimal precio = parsePrecio();
            String divisa = proveedor.getDivisa();
            int idProv = proveedor.getIdProv();
            boolean esEsim = cbEsim.isSelected();

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
                        String modeloFila = LookupModelosImeis.modeloParaFila(
                                modelosManuales.get(imei), modelosDetectados.get(imei), modeloInterno);
                        telefonos.add(new Importacion.TelefonoImport(imei, modeloFila, storageGb, color, grado,
                                precio, divisa, precioEur, esEsim));
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
