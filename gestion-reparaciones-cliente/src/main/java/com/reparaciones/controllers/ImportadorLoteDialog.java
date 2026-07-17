package com.reparaciones.controllers;

import com.reparaciones.dao.ColorEquivalenciaDAO;
import com.reparaciones.dao.EquivalenciaModeloDAO;
import com.reparaciones.dao.LoteDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.Importacion;
import com.reparaciones.models.Proveedor;
import com.reparaciones.models.VerificacionImei;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ClasificadorImportacion;
import com.reparaciones.utils.ClasificadorImportacion.Destino;
import com.reparaciones.utils.ClasificadorImportacion.FilaClasificada;
import com.reparaciones.utils.ClasificadorImportacion.LotePlan;
import com.reparaciones.utils.ClasificadorImportacion.Plan;
import com.reparaciones.utils.ColorMapper;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.LoteXlsxParser;
import com.reparaciones.utils.LoteXlsxParser.Fila;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Diálogo de importación en bloque de un xlsx de la otra plataforma de stock (spec F2 §3).
 * <p>Abre un {@link FileChooser}, parsea el fichero, cruza los datos con los mapeos de modelo
 * y las verificaciones de IMEI del servidor, y muestra una vista previa editable (proveedor por
 * lote, resolución de modelos sin mapear) antes de confirmar la importación.</p>
 * <p>Todo el acceso a red ocurre en hilos secundarios; las actualizaciones de la interfaz se
 * hacen siempre en el hilo de JavaFX vía {@link Platform#runLater}.</p>
 */
public final class ImportadorLoteDialog {

    private ImportadorLoteDialog() {}

    /**
     * Abre el {@link FileChooser}, parsea el fichero elegido y muestra la vista previa.
     *
     * @param owner       ventana propietaria del selector de fichero y del diálogo modal
     * @param onImportado se ejecuta tras una importación confirmada con éxito
     */
    public static void abrir(Window owner, Runnable onImportado) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar lote");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel (*.xlsx)", "*.xlsx"));
        File archivo = chooser.showOpenDialog(owner);
        if (archivo == null) return;

        new Thread(() -> {
            try {
                LoteXlsxParser.Resultado resultado;
                try (InputStream in = new FileInputStream(archivo)) {
                    resultado = LoteXlsxParser.parsear(in);
                }
                EquivalenciaModeloDAO equivalenciaDAO = new EquivalenciaModeloDAO();
                Map<String, String> equivalencias = equivalenciaDAO.getAll();
                ColorEquivalenciaDAO colorEquivalenciaDAO = new ColorEquivalenciaDAO();
                Map<String, String> equivalenciasColor = colorEquivalenciaDAO.getAll();
                ProveedorDAO proveedorDAO = new ProveedorDAO();
                List<Proveedor> proveedores = proveedorDAO.getActivos(ProveedorDAO.TIPO_TELEFONOS);

                List<String> imeis = resultado.filas().stream()
                        .map(ImportadorLoteDialog::imeiLimpio)
                        .filter(s -> s.length() == 15)
                        .distinct()
                        .collect(Collectors.toList());
                LoteDAO loteDAO = new LoteDAO();
                List<VerificacionImei> verificaciones = imeis.isEmpty() ? List.of() : loteDAO.verificar(imeis);

                List<Fila> filas = resultado.filas();
                Platform.runLater(() -> new Sesion(owner, archivo.getName(), filas, equivalencias, equivalenciasColor,
                        proveedores, verificaciones, onImportado).mostrar());
            } catch (Exception e) {
                Platform.runLater(() -> Alertas.mostrarError("No se pudo leer el fichero: " + e.getMessage()));
            }
        }, "importador-lote-carga").start();
    }

    /** IMEI con solo dígitos (limpio de separadores), o cadena vacía si no hay IMEI. */
    private static String imeiLimpio(Fila f) {
        return f.imei() == null ? "" : f.imei().replaceAll("\\D", "");
    }

    /**
     * Modelo mostrado en las tablas: nombre del catálogo con sufijo eSIM si procede, o el
     * texto crudo del fichero si no hay modelo interno (el crudo ya trae su propia marca eSIM).
     */
    private static String modeloMostrado(FilaClasificada fc) {
        if (fc.modeloInterno() == null) {
            return fc.fila().modeloTexto() == null ? "" : fc.fila().modeloTexto();
        }
        String modelo = FormularioReparacionController.traducirModelo(fc.modeloInterno());
        if (ModeloMapper.esEsim(fc.fila().modeloTexto())) modelo += " eSIM";
        return modelo;
    }

    /** "Nuevo" verde / "Re-entrada" azul — únicos destinos que llegan a las tablas de lote. */
    private static String etiquetaDestinoImportable(Destino d) {
        return d == Destino.NUEVO ? "Nuevo" : "Re-entrada";
    }

    /** Etiqueta legible de cualquier destino, usada en la tabla de excluidas. */
    private static String etiquetaDestino(Destino d) {
        return switch (d) {
            case NUEVO -> "Nuevo";
            case REENTRADA -> "Re-entrada";
            case CONFLICTO -> "Conflicto";
            case STATUS_DISTINTO -> "Status distinto";
            case INVALIDO -> "Inválido";
            case DUPLICADO_FICHERO -> "Duplicado en fichero";
            case MODELO_SIN_MAPEAR -> "Modelo sin mapear";
            case COLOR_SIN_MAPEAR -> "Color sin mapear";
        };
    }

    /**
     * Estado y UI de una vista previa de importación abierta sobre un fichero ya parseado.
     * Una instancia por fichero abierto: guarda las equivalencias y selecciones de proveedor
     * para poder repintar tras resolver un modelo sin mapear (método {@link #reclasificar()}).
     */
    private static final class Sesion {

        private record FilaLote(String imei, String modeloInterno, Integer storageGb, String color,
                                 String grado, BigDecimal precioCompra, boolean esEsim) {}

        private record DatosLote(String batchNumber, Proveedor proveedor, List<FilaLote> filas) {}

        private final Window owner;
        private final String nombreFichero;
        private final List<Fila> filas;
        private final Map<String, VerificacionImei> existentesPorImei = new HashMap<>();
        private final List<Proveedor> proveedores;
        private final Runnable onImportado;

        private final EquivalenciaModeloDAO equivalenciaDAO = new EquivalenciaModeloDAO();
        private final ColorEquivalenciaDAO colorEquivalenciaDAO = new ColorEquivalenciaDAO();
        private final LoteDAO loteDAO = new LoteDAO();
        private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();

        private Map<String, String> equivalencias;
        private Map<String, String> equivalenciasColor;
        private Plan plan;
        private final Map<String, Proveedor> seleccionProveedorPorLote = new HashMap<>();
        /** Combo de proveedor por lote (clave batchNumber), espejo de {@link #seleccionProveedorPorLote};
         *  permite refrescar todos los combos tras crear un supplier inline sin reconstruir los panes. */
        private final Map<String, ComboBox<Proveedor>> comboPorLote = new HashMap<>();
        private final Map<String, Double> tasasCache = new ConcurrentHashMap<>();

        private Stage stage;
        private Label lblBadges;
        private VBox bloqueModelosSinMapear;
        private VBox bloqueColoresSinMapear;
        private VBox contenedorLotes;
        private TableView<FilaClasificada> tablaExcluidas;
        private TitledPane paneExcluidas;
        private CheckBox cbReentradas;
        private Button btnCancelar;
        private Button btnImportar;
        /** true mientras la importación está en vuelo: bloquea reimportar, cancelar y editar. */
        private boolean importando;

        Sesion(Window owner, String nombreFichero, List<Fila> filas, Map<String, String> equivalencias,
               Map<String, String> equivalenciasColor, List<Proveedor> proveedores,
               List<VerificacionImei> verificaciones, Runnable onImportado) {
            this.owner = owner;
            this.nombreFichero = nombreFichero;
            this.filas = filas;
            this.equivalencias = new HashMap<>(equivalencias);
            this.equivalenciasColor = new HashMap<>(equivalenciasColor);
            this.proveedores = new ArrayList<>(proveedores);
            this.onImportado = onImportado;
            for (VerificacionImei v : verificaciones) existentesPorImei.put(v.getImei(), v);
        }

        void mostrar() {
            construirUi();
            stage.showAndWait();
        }

        // ─── Clasificación ──────────────────────────────────────────────────────

        private void calcular() {
            List<String> textosModelo = filas.stream().map(Fila::modeloTexto).distinct().toList();
            Map<String, String> mapeoModelos = ModeloMapper.mapear(textosModelo, equivalencias);
            plan = ClasificadorImportacion.clasificar(filas, mapeoModelos, equivalenciasColor, existentesPorImei);
        }

        /** Re-mapea y re-clasifica TODO tras resolver un modelo sin mapear, y repinta el diálogo. */
        private void reclasificar() {
            calcular();
            repintarContenido();
        }

        // ─── Construcción de la UI ──────────────────────────────────────────────

        private void construirUi() {
            Label lblArchivo = new Label(nombreFichero);
            lblArchivo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");

            lblBadges = new Label();
            lblBadges.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");

            HBox espaciador = new HBox();
            HBox.setHgrow(espaciador, Priority.ALWAYS);
            HBox cabecera = new HBox(14, lblArchivo, espaciador, lblBadges);
            cabecera.setAlignment(Pos.CENTER_LEFT);

            bloqueModelosSinMapear = new VBox(6);
            bloqueModelosSinMapear.setPadding(new Insets(10));
            bloqueModelosSinMapear.setStyle("-fx-background-color: " + Colores.FILA_SOLICITUD_BG + "; -fx-background-radius: 6;");

            bloqueColoresSinMapear = new VBox(6);
            bloqueColoresSinMapear.setPadding(new Insets(10));
            bloqueColoresSinMapear.setStyle("-fx-background-color: " + Colores.FILA_SOLICITUD_BG + "; -fx-background-radius: 6;");

            contenedorLotes = new VBox(10);

            tablaExcluidas = crearTablaExcluidas();
            tablaExcluidas.setPrefHeight(200);
            paneExcluidas = new TitledPane("No entran (0)", tablaExcluidas);
            paneExcluidas.setExpanded(false);

            cbReentradas = new CheckBox("Incluir re-entradas (0)");
            cbReentradas.setSelected(true);
            cbReentradas.setOnAction(e -> actualizarBotonImportar());

            btnCancelar = new Button("Cancelar");
            btnCancelar.setOnAction(e -> stage.close());

            btnImportar = new Button("Importar 0 teléfonos");
            btnImportar.setOnAction(e -> confirmar());

            HBox botones = new HBox(10, btnCancelar, btnImportar);
            botones.setAlignment(Pos.CENTER_RIGHT);

            VBox contenidoScroll = new VBox(14, bloqueModelosSinMapear, bloqueColoresSinMapear, contenedorLotes, paneExcluidas);
            ScrollPane scroll = new ScrollPane(contenidoScroll);
            scroll.setFitToWidth(true);
            VBox.setVgrow(scroll, Priority.ALWAYS);

            VBox raiz = new VBox(14, cabecera, scroll, cbReentradas, botones);
            raiz.setPadding(new Insets(16));

            stage = new Stage();
            stage.setTitle("Importar lote");
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);
            Scene scene = new Scene(raiz, 900, 640);
            scene.getStylesheets().add(ImportadorLoteDialog.class.getResource("/styles/app.css").toExternalForm());
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> { if (importando) e.consume(); });

            calcular();
            repintarContenido();
        }

        /** Regenera las tablas y contadores del diálogo a partir de {@link #plan} vigente. */
        private void repintarContenido() {
            long nuevos = plan.lotes().stream().flatMap(l -> l.filas().stream())
                    .filter(fc -> fc.destino() == Destino.NUEVO).count();
            long reentradas = plan.lotes().stream().flatMap(l -> l.filas().stream())
                    .filter(fc -> fc.destino() == Destino.REENTRADA).count();
            long conflictos = plan.excluidas().stream().filter(fc -> fc.destino() == Destino.CONFLICTO).count();
            long avisos = plan.excluidas().stream().filter(fc -> fc.destino() != Destino.CONFLICTO).count();
            lblBadges.setText(nuevos + " nuevos · " + reentradas + " re-entradas · " + conflictos + " conflictos · " + avisos + " avisos");

            actualizarBloqueModelosSinMapear();
            actualizarBloqueColoresSinMapear();

            contenedorLotes.getChildren().clear();
            for (LotePlan lote : plan.lotes()) contenedorLotes.getChildren().add(crearPaneLote(lote));

            tablaExcluidas.setItems(FXCollections.observableArrayList(plan.excluidas()));
            paneExcluidas.setText("No entran (" + plan.excluidas().size() + ")");

            cbReentradas.setText("Incluir re-entradas (" + reentradas + ")");

            actualizarBotonImportar();
        }

        // ─── Bloque "Modelos sin mapear" ────────────────────────────────────────

        private void actualizarBloqueModelosSinMapear() {
            Map<String, Long> conteoPorTexto = plan.excluidas().stream()
                    .filter(fc -> fc.destino() == Destino.MODELO_SIN_MAPEAR)
                    .collect(Collectors.groupingBy(
                            fc -> fc.fila().modeloTexto() == null ? "" : fc.fila().modeloTexto(),
                            LinkedHashMap::new, Collectors.counting()));

            bloqueModelosSinMapear.getChildren().clear();
            if (conteoPorTexto.isEmpty()) {
                bloqueModelosSinMapear.setVisible(false);
                bloqueModelosSinMapear.setManaged(false);
                return;
            }
            bloqueModelosSinMapear.setVisible(true);
            bloqueModelosSinMapear.setManaged(true);

            Label titulo = new Label("Modelos sin mapear");
            titulo.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Colores.TEXTO_ERROR + ";");
            bloqueModelosSinMapear.getChildren().add(titulo);

            for (Map.Entry<String, Long> entry : conteoPorTexto.entrySet()) {
                String texto = entry.getKey();
                long n = entry.getValue();
                String textoMostrado = texto.isEmpty() ? "(vacío)" : texto;
                Label lbl = new Label("\"" + textoMostrado + "\" (" + n + (n == 1 ? " fila)" : " filas)"));
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
                Button btnElegir = new Button("Elegir modelo…");
                btnElegir.setOnAction(e -> resolverModeloSinMapear(texto));
                HBox fila = new HBox(10, lbl, btnElegir);
                fila.setAlignment(Pos.CENTER_LEFT);
                bloqueModelosSinMapear.getChildren().add(fila);
            }
        }

        private void resolverModeloSinMapear(String texto) {
            SelectorModeloDialog.elegir(null).ifPresent(interno -> {
                String normalizado = ModeloMapper.normalizar(texto);
                if (interno == null || normalizado == null) return;
                new Thread(() -> {
                    try {
                        equivalenciaDAO.guardar(normalizado, interno);
                    } catch (SQLException ex) {
                        Platform.runLater(() -> Alertas.mostrarError("No se pudo guardar la equivalencia: " + ex.getMessage()));
                        return;
                    }
                    Platform.runLater(() -> {
                        equivalencias.put(normalizado, interno);
                        reclasificar();
                    });
                }, "guardar-equivalencia-modelo").start();
            });
        }

        // ─── Bloque "Colores sin mapear" ────────────────────────────────────────

        private void actualizarBloqueColoresSinMapear() {
            Map<String, List<FilaClasificada>> filasPorTexto = plan.excluidas().stream()
                    .filter(fc -> fc.destino() == Destino.COLOR_SIN_MAPEAR)
                    .collect(Collectors.groupingBy(
                            fc -> fc.fila().color() == null ? "" : fc.fila().color(),
                            LinkedHashMap::new, Collectors.toList()));

            bloqueColoresSinMapear.getChildren().clear();
            if (filasPorTexto.isEmpty()) {
                bloqueColoresSinMapear.setVisible(false);
                bloqueColoresSinMapear.setManaged(false);
                return;
            }
            bloqueColoresSinMapear.setVisible(true);
            bloqueColoresSinMapear.setManaged(true);

            Label titulo = new Label("Colores sin mapear");
            titulo.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Colores.TEXTO_ERROR + ";");
            bloqueColoresSinMapear.getChildren().add(titulo);

            for (Map.Entry<String, List<FilaClasificada>> entry : filasPorTexto.entrySet()) {
                String texto = entry.getKey();
                List<FilaClasificada> afectadas = entry.getValue();
                int n = afectadas.size();
                String textoMostrado = texto.isEmpty() ? "(vacío)" : texto;
                Label lbl = new Label("\"" + textoMostrado + "\" (" + n + (n == 1 ? " fila)" : " filas)"));
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
                HBox fila = new HBox(10, lbl);
                fila.setAlignment(Pos.CENTER_LEFT);
                if (!ColorMapper.normalizar(texto).isEmpty()) {
                    // Guard de texto vacío: no ofrecer resolver (lección F2a: nunca guardar una
                    // equivalencia para un texto que normaliza a vacío, aplicaría a cualquier fila sin color).
                    String modeloInterno = afectadas.get(0).modeloInterno();
                    Button btnElegir = new Button("Elegir color…");
                    btnElegir.setOnAction(e -> resolverColorSinMapear(texto, modeloInterno));
                    fila.getChildren().add(btnElegir);
                }
                bloqueColoresSinMapear.getChildren().add(fila);
            }
        }

        private void resolverColorSinMapear(String texto, String modeloInterno) {
            if (texto == null || texto.isBlank()) return;
            List<String> paleta = CatalogoAtributos.coloresDe(modeloInterno);
            if (paleta.isEmpty()) paleta = CatalogoAtributos.COLORES_TODOS;
            ChoiceDialog<String> dialogo = new ChoiceDialog<>(null, paleta);
            dialogo.setTitle("Elegir color");
            dialogo.setHeaderText(null);
            dialogo.setContentText("Color oficial para \"" + texto + "\":");
            dialogo.showAndWait().ifPresent(oficial -> {
                String normalizado = ColorMapper.normalizar(texto);
                if (normalizado.isEmpty()) return;
                new Thread(() -> {
                    try {
                        colorEquivalenciaDAO.guardar(normalizado, oficial);
                    } catch (SQLException ex) {
                        Platform.runLater(() -> Alertas.mostrarError("No se pudo guardar la equivalencia: " + ex.getMessage()));
                        return;
                    }
                    Platform.runLater(() -> {
                        equivalenciasColor.put(normalizado, oficial);
                        reclasificar();
                    });
                }, "guardar-equivalencia-color").start();
            });
        }

        // ─── Panes de lote ──────────────────────────────────────────────────────

        private TitledPane crearPaneLote(LotePlan lote) {
            Proveedor seleccionActual;
            if (seleccionProveedorPorLote.containsKey(lote.batchNumber())) {
                seleccionActual = seleccionProveedorPorLote.get(lote.batchNumber());
            } else {
                seleccionActual = buscarProveedorPorNombre(lote.proveedorNombre());
                seleccionProveedorPorLote.put(lote.batchNumber(), seleccionActual);
            }

            ComboBox<Proveedor> comboProveedor = new ComboBox<>(FXCollections.observableArrayList(proveedores));
            comboProveedor.setValue(seleccionActual);
            comboProveedor.setPromptText("Selecciona supplier…");
            comboProveedor.setVisibleRowCount(8);
            comboPorLote.put(lote.batchNumber(), comboProveedor);

            Label lblAvisoProveedor = new Label("Elige supplier");
            lblAvisoProveedor.setStyle("-fx-text-fill: " + Colores.TEXTO_ERROR + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            lblAvisoProveedor.setVisible(seleccionActual == null);
            lblAvisoProveedor.setManaged(seleccionActual == null);

            Hyperlink crearSupplier = new Hyperlink();
            crearSupplier.setText("Crear supplier \"" + lote.proveedorNombre() + "\"");
            boolean mostrarCrear = seleccionActual == null
                    && lote.proveedorNombre() != null && !lote.proveedorNombre().isBlank();
            crearSupplier.setVisible(mostrarCrear);
            crearSupplier.setManaged(mostrarCrear);
            crearSupplier.setOnAction(e -> NuevoSupplierDialog.abrir(stage, lote.proveedorNombre(),
                    nombreCreado -> recargarProveedoresTrasCreacion()));

            Label lblTotal = new Label();
            actualizarTotalLote(lblTotal, lote, seleccionActual);

            comboProveedor.setOnAction(e -> {
                Proveedor val = comboProveedor.getValue();
                seleccionProveedorPorLote.put(lote.batchNumber(), val);
                lblAvisoProveedor.setVisible(val == null);
                lblAvisoProveedor.setManaged(val == null);
                boolean mostrar = val == null
                        && lote.proveedorNombre() != null && !lote.proveedorNombre().isBlank();
                crearSupplier.setVisible(mostrar);
                crearSupplier.setManaged(mostrar);
                actualizarTotalLote(lblTotal, lote, val);
                actualizarBotonImportar();
            });

            HBox filaProveedor = new HBox(10, new Label("Proveedor:"), comboProveedor, lblAvisoProveedor, crearSupplier);
            filaProveedor.setAlignment(Pos.CENTER_LEFT);

            TableView<FilaClasificada> tabla = crearTablaImportables();
            tabla.setItems(FXCollections.observableArrayList(lote.filas()));
            tabla.setPrefHeight(Math.min(Math.max(lote.filas().size(), 1), 6) * 28 + 40);

            VBox contenido = new VBox(8, filaProveedor, tabla, lblTotal);
            contenido.setPadding(new Insets(10));

            TitledPane pane = new TitledPane(
                    "Lote " + lote.batchNumber() + " — " + lote.proveedorNombre() + " — " + lote.filas().size() + " teléfonos",
                    contenido);
            pane.setExpanded(true);
            return pane;
        }

        private Proveedor buscarProveedorPorNombre(String nombre) {
            if (nombre == null) return null;
            String n = nombre.trim();
            return proveedores.stream()
                    .filter(p -> p.getNombre() != null && p.getNombre().trim().equalsIgnoreCase(n))
                    .findFirst().orElse(null);
        }

        /**
         * Tras crear un supplier inline (Hyperlink "Crear supplier…" de un lote sin selección):
         * relanza la carga de suppliers activos y, en el hilo de JavaFX, refresca los items de
         * TODOS los combos de lote conservando su selección, reintenta el match por nombre en los
         * lotes que aún no tengan proveedor (cubre varios lotes del mismo xlsx con el mismo supplier
         * nuevo) y recalcula avisos/hyperlinks/botón reutilizando el propio manejador de cada combo.
         */
        private void recargarProveedoresTrasCreacion() {
            new Thread(() -> {
                try {
                    List<Proveedor> nuevos = new ProveedorDAO().getActivos(ProveedorDAO.TIPO_TELEFONOS);
                    Platform.runLater(() -> {
                        proveedores.clear();
                        proveedores.addAll(nuevos);

                        for (ComboBox<Proveedor> combo : comboPorLote.values()) {
                            Proveedor seleccionPrevia = combo.getValue();
                            combo.setItems(FXCollections.observableArrayList(proveedores));
                            combo.setValue(seleccionPrevia);
                        }

                        for (LotePlan lote : plan.lotes()) {
                            if (seleccionProveedorPorLote.get(lote.batchNumber()) != null) continue;
                            Proveedor match = buscarProveedorPorNombre(lote.proveedorNombre());
                            if (match == null) continue;
                            ComboBox<Proveedor> combo = comboPorLote.get(lote.batchNumber());
                            if (combo != null) combo.setValue(match);
                        }

                        for (ComboBox<Proveedor> combo : comboPorLote.values()) {
                            if (combo.getOnAction() != null) combo.getOnAction().handle(new ActionEvent());
                        }
                        actualizarBotonImportar();
                    });
                } catch (SQLException ex) {
                    Platform.runLater(() -> Alertas.mostrarError("No se pudieron recargar los suppliers: " + ex.getMessage()));
                }
            }, "importador-recarga-proveedores").start();
        }

        private void actualizarTotalLote(Label lblTotal, LotePlan lote, Proveedor proveedor) {
            if (proveedor == null) {
                lblTotal.setText("Elige un proveedor para ver el total.");
                lblTotal.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
                return;
            }
            BigDecimal totalDivisa = lote.filas().stream()
                    .map(fc -> fc.fila().precioCompra())
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String divisa = proveedor.getDivisa();
            Double tasaCacheada = "EUR".equalsIgnoreCase(divisa) ? Double.valueOf(1.0) : tasasCache.get(divisa);
            if (tasaCacheada != null) {
                pintarTotalLote(lblTotal, totalDivisa, divisa, tasaCacheada);
                return;
            }
            lblTotal.setText("Calculando total…");
            lblTotal.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
            new Thread(() -> {
                try {
                    double t = tipoCambioDAO.getTasa(divisa);
                    tasasCache.put(divisa, t);
                    Platform.runLater(() -> pintarTotalLote(lblTotal, totalDivisa, divisa, t));
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        lblTotal.setText("No se pudo calcular el total (" + e.getMessage() + ")");
                        lblTotal.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.TEXTO_ERROR + ";");
                    });
                }
            }, "tasa-lote-" + lote.batchNumber()).start();
        }

        private void pintarTotalLote(Label lblTotal, BigDecimal totalDivisa, String divisa, double tasa) {
            BigDecimal totalEur = totalDivisa.multiply(BigDecimal.valueOf(tasa)).setScale(2, RoundingMode.HALF_UP);
            lblTotal.setText(String.format(Locale.US, "Total: %.2f %s ≈ %.2f €", totalDivisa, divisa, totalEur));
            lblTotal.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
        }

        // ─── Tablas ──────────────────────────────────────────────────────────────

        private TableView<FilaClasificada> crearTablaImportables() {
            TableView<FilaClasificada> tabla = new TableView<>();

            TableColumn<FilaClasificada, String> cImei = new TableColumn<>("IMEI");
            cImei.setCellValueFactory(d -> new SimpleStringProperty(imeiLimpio(d.getValue().fila())));

            TableColumn<FilaClasificada, String> cModelo = new TableColumn<>("Modelo");
            cModelo.setCellValueFactory(d -> new SimpleStringProperty(modeloMostrado(d.getValue())));

            TableColumn<FilaClasificada, String> cStorage = new TableColumn<>("Storage");
            cStorage.setCellValueFactory(d -> {
                Integer gb = d.getValue().fila().storageGb();
                return new SimpleStringProperty(gb == null ? "-" : gb + " GB");
            });

            TableColumn<FilaClasificada, String> cColor = new TableColumn<>("Color");
            cColor.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().colorOficial() == null ? "" : d.getValue().colorOficial()));

            TableColumn<FilaClasificada, String> cGrado = new TableColumn<>("Grado");
            cGrado.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().fila().grado() == null ? "" : d.getValue().fila().grado()));

            TableColumn<FilaClasificada, String> cPrecio = new TableColumn<>("Precio");
            cPrecio.setCellValueFactory(d -> {
                BigDecimal precio = d.getValue().fila().precioCompra();
                return new SimpleStringProperty(precio == null ? "-" : precio.setScale(2, RoundingMode.HALF_UP).toPlainString());
            });

            TableColumn<FilaClasificada, Destino> cDestino = new TableColumn<>("Destino");
            cDestino.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().destino()));
            cDestino.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Destino item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setGraphic(null); return; }
                    Label badge = new Label(etiquetaDestinoImportable(item));
                    String base = "-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold;";
                    if (item == Destino.NUEVO) {
                        badge.setStyle(base + "-fx-background-color: " + Colores.FILA_REPARADO_BG + "; -fx-text-fill: " + Colores.FILA_REPARADO_ICO + ";");
                    } else {
                        badge.setStyle(base + "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;");
                    }
                    setGraphic(badge);
                }
            });

            TableColumn<FilaClasificada, String> cDetalle = new TableColumn<>("Detalle");
            cDetalle.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().detalle() == null ? "" : d.getValue().detalle()));

            tabla.getColumns().add(cImei);
            tabla.getColumns().add(cModelo);
            tabla.getColumns().add(cStorage);
            tabla.getColumns().add(cColor);
            tabla.getColumns().add(cGrado);
            tabla.getColumns().add(cPrecio);
            tabla.getColumns().add(cDestino);
            tabla.getColumns().add(cDetalle);
            tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tabla.getColumns().forEach(c -> c.setReorderable(false));
            return tabla;
        }

        private TableView<FilaClasificada> crearTablaExcluidas() {
            TableView<FilaClasificada> tabla = new TableView<>();

            TableColumn<FilaClasificada, Number> cFilaExcel = new TableColumn<>("Fila Excel");
            cFilaExcel.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().fila().numFila()));

            TableColumn<FilaClasificada, String> cImei = new TableColumn<>("IMEI");
            cImei.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().fila().imei() == null ? "" : d.getValue().fila().imei()));

            TableColumn<FilaClasificada, String> cModelo = new TableColumn<>("Modelo");
            cModelo.setCellValueFactory(d -> new SimpleStringProperty(modeloMostrado(d.getValue())));

            TableColumn<FilaClasificada, String> cDestino = new TableColumn<>("Destino");
            cDestino.setCellValueFactory(d -> new SimpleStringProperty(etiquetaDestino(d.getValue().destino())));

            TableColumn<FilaClasificada, String> cDetalle = new TableColumn<>("Detalle");
            cDetalle.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().detalle() == null ? "" : d.getValue().detalle()));

            tabla.getColumns().add(cFilaExcel);
            tabla.getColumns().add(cImei);
            tabla.getColumns().add(cModelo);
            tabla.getColumns().add(cDestino);
            tabla.getColumns().add(cDetalle);
            tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tabla.getColumns().forEach(c -> c.setReorderable(false));
            return tabla;
        }

        // ─── Botón Importar ──────────────────────────────────────────────────────

        /** Bloquea/desbloquea la edición del diálogo mientras la importación está en vuelo. */
        private void setImportando(boolean enVuelo) {
            importando = enVuelo;
            btnCancelar.setDisable(enVuelo);
            cbReentradas.setDisable(enVuelo);
            contenedorLotes.setDisable(enVuelo);          // ComboBox de proveedor de cada lote
            bloqueModelosSinMapear.setDisable(enVuelo);   // botones "Elegir modelo…"
            bloqueColoresSinMapear.setDisable(enVuelo);   // botones "Elegir color…"
            actualizarBotonImportar();
        }

        private void actualizarBotonImportar() {
            if (importando) { btnImportar.setDisable(true); return; }
            boolean faltaProveedor = plan.lotes().stream()
                    .anyMatch(l -> seleccionProveedorPorLote.get(l.batchNumber()) == null);
            boolean incluirReentradas = cbReentradas.isSelected();
            long total = plan.lotes().stream()
                    .filter(l -> seleccionProveedorPorLote.get(l.batchNumber()) != null)
                    .flatMap(l -> l.filas().stream())
                    .filter(fc -> fc.destino() == Destino.NUEVO || (incluirReentradas && fc.destino() == Destino.REENTRADA))
                    .count();
            btnImportar.setText("Importar " + total + " teléfonos");
            btnImportar.setDisable(faltaProveedor || total == 0);
        }

        // ─── Confirmar ───────────────────────────────────────────────────────────

        private void confirmar() {
            boolean incluirReentradas = cbReentradas.isSelected();
            List<DatosLote> datosLotes = new ArrayList<>();
            for (LotePlan lote : plan.lotes()) {
                Proveedor proveedor = seleccionProveedorPorLote.get(lote.batchNumber());
                if (proveedor == null) continue;
                List<FilaLote> filasLote = new ArrayList<>();
                for (FilaClasificada fc : lote.filas()) {
                    if (fc.destino() == Destino.REENTRADA && !incluirReentradas) continue;
                    Fila f = fc.fila();
                    filasLote.add(new FilaLote(imeiLimpio(f), fc.modeloInterno(), f.storageGb(), fc.colorOficial(),
                            f.grado(), f.precioCompra(), ModeloMapper.esEsim(f.modeloTexto())));
                }
                if (!filasLote.isEmpty()) datosLotes.add(new DatosLote(lote.batchNumber(), proveedor, filasLote));
            }
            if (datosLotes.isEmpty()) return;

            setImportando(true);
            new Thread(() -> {
                try {
                    List<Importacion.LoteImport> loteImports = new ArrayList<>();
                    for (DatosLote dl : datosLotes) {
                        double tasa = obtenerTasa(dl.proveedor().getDivisa());
                        List<Importacion.TelefonoImport> telefonos = new ArrayList<>();
                        for (FilaLote fl : dl.filas()) {
                            BigDecimal precioEur = fl.precioCompra() == null ? null
                                    : fl.precioCompra().multiply(BigDecimal.valueOf(tasa)).setScale(2, RoundingMode.HALF_UP);
                            telefonos.add(new Importacion.TelefonoImport(fl.imei(), fl.modeloInterno(), fl.storageGb(),
                                    fl.color(), fl.grado(), fl.precioCompra(), dl.proveedor().getDivisa(), precioEur, fl.esEsim()));
                        }
                        loteImports.add(new Importacion.LoteImport(dl.batchNumber(), dl.proveedor().getIdProv(), null, telefonos));
                    }
                    Importacion.Respuesta resp = loteDAO.importar(new Importacion.Request(loteImports));
                    Platform.runLater(() -> {
                        importando = false;
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
                        Alertas.mostrarError("No se pudo importar: " + e.getMessage());
                    });
                }
            }, "importar-lote").start();
        }

        private double obtenerTasa(String divisa) throws SQLException {
            if ("EUR".equalsIgnoreCase(divisa)) return 1.0;
            Double cached = tasasCache.get(divisa);
            if (cached != null) return cached;
            double t = tipoCambioDAO.getTasa(divisa);
            tasasCache.put(divisa, t);
            return t;
        }

        private void mostrarResultado(Importacion.Respuesta resp) {
            StringBuilder sb = new StringBuilder();
            sb.append(resp.telefonos()).append(" teléfonos importados en ").append(resp.lotes()).append(" lotes");
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
