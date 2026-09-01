package com.reparaciones.controllers;

import com.reparaciones.dao.ComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.models.Componente;
import com.reparaciones.models.PuntoEstadisticaPuntos;
import com.reparaciones.models.Tecnico;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.PuntosEstadistica;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controlador de la vista de estadísticas.
 * <p>Muestra un gráfico de líneas ({@code LineChart}) con los puntos de dificultad
 * conseguidos por técnico a lo largo del tiempo, con granularidades: día, semana, mes y año.</p>
 *
 * <p><b>Funcionalidades principales:</b></p>
 * <ul>
 *   <li>Radio "Puntos" / "Puntos/día" — métrica activa del gráfico.</li>
 *   <li>Checkbox "Equipo (suma)" — línea negra con la suma de todos los técnicos.</li>
 *   <li>Checkbox "Ver medias" — muestra/oculta las líneas de media discontinuas por técnico.</li>
 *   <li>Línea "Promedio" (naranja, discontinua, siempre visible) — promedio de ventana de la
 *       métrica activa sobre los técnicos (nunca sobre la serie "Equipo").</li>
 *   <li>Flechas de navegación de ventana — limitan los periodos visibles cuando hay muchos.</li>
 *   <li>Clic en vértice — abre un popover con el desglose del periodo y un botón
 *       "Ver en Historial" que navega con el filtro de fecha y técnico pre-aplicado,
 *       vía callback {@link com.reparaciones.utils.Navegable}.</li>
 *   <li>Los técnicos inactivos no tienen línea individual pero sí cuentan en "Equipo".</li>
 * </ul>
 *
 * <p>El doble {@code Platform.runLater} en el flujo de renderizado garantiza que el eje Y
 * y las líneas de media se dibujen con las coordenadas correctas tras los dos pases de layout.</p>
 *
 * @role ADMIN
 */
public class EstadisticasController implements com.reparaciones.utils.Recargable {

    @FXML private Button   btnTabReparaciones;
    @FXML private Button   btnTabStock;

    @FXML private VBox     pnlReparaciones;
    @FXML private VBox     pnlStock;

    @FXML private ComboBox<String> cmbGranularidad;
    @FXML private DatePicker       dpDesde;
    @FXML private DatePicker       dpHasta;
    @FXML private LineChart<String, Number> chartReparaciones;
    @FXML private CategoryAxis     ejeX;
    @FXML private NumberAxis       ejeY;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<Tecnico> menuTecnicos;
    @FXML private RadioButton      rbPuntos;
    @FXML private RadioButton      rbPuntosDia;
    @FXML private CheckBox         chkEquipo;
    @FXML private CheckBox         chkMedia;
    @FXML private Label            lblSinDatos;
    @FXML private HBox             hboxNavVentana;
    @FXML private Button           btnVentanaAnterior;
    @FXML private Button           btnVentanaSiguiente;
    @FXML private Label            lblRangoVentana;

    @FXML private Label            lblCardPuntosTitulo;
    @FXML private Label            lblCardPuntosValor;
    @FXML private Label            lblCardPuntosDelta;
    @FXML private Label            lblCardDiaTitulo;
    @FXML private Label            lblCardDiaValor;
    @FXML private Label            lblCardDiaDelta;

    // nombres de técnicos actualmente visibles en el gráfico
    private final Set<String>           nombresSeleccionadosTec = new LinkedHashSet<>();
    // nombre → color hex fijo por ID_TEC
    private final Map<String, String>   coloresPorNombre = new LinkedHashMap<>();
    // label del botón MultiSelectComboBox
    private final StringProperty        etiquetaTecs = new SimpleStringProperty("+ Técnicos");
    // ListView del popup de técnicos (null hasta que se carga)
    private com.reparaciones.utils.MultiSelectDropdown.Handle filtroTecHandle;
    // todos los técnicos cargados (activos + inactivos, null = separador)
    private final java.util.List<Tecnico> todosLosTecnicos = new java.util.ArrayList<>();
    // Serie actualmente resaltada (null = ninguna)
    private XYChart.Series<String, Number> serieResaltada = null;
    // True mientras el ratón esté sobre una línea de media (evita que onMouseMoved la deshaga)
    private boolean sobreLineaMedia = false;
    // Líneas de media por técnico (para limpiarlas entre renders)
    private final java.util.List<Node> lineasMedia      = new java.util.ArrayList<>();
    // Nodos de la línea de referencia del equipo (separados para control independiente)
    private final java.util.List<Node> lineasReferencia = new java.util.ArrayList<>();
    // Nodo visual de la línea de referencia (para participar en el sistema de highlight)
    private javafx.scene.shape.Line lineaRefVisual = null;
    // Mapa serie → línea discontinua para poder afectarla en hover
    private final Map<XYChart.Series<String, Number>, javafx.scene.shape.Line> lineaMediaPorSerie = new LinkedHashMap<>();
    // Mapa serie → valor medio (para tooltip)
    private final Map<XYChart.Series<String, Number>, Double> mediaPorSerie = new LinkedHashMap<>();
    // Tooltip reutilizable para la media
    private final Tooltip tooltipMedia = new Tooltip();

    // Todos los puntos cargados de BD (sin filtrar por checkbox)
    private List<PuntoEstadisticaPuntos> todosPuntos   = List.of();
    // Periodos únicos ordenados
    private List<String>           todosPeriodos = List.of();
    // Tamaño de la ventana visible según granularidad
    private int ventanaTamanio = 30;
    // Offset de la ventana actualmente visible (0 = más antigua)
    private int ventanaOffset = 0;

    // Ventanas por granularidad
    private static final int VENTANA_DIA    = 30;
    private static final int VENTANA_SEMANA = 16;
    private static final int VENTANA_MES    = 12;
    private static final int VENTANA_ANO    =  5;

    // Color fijo para la serie "Equipo" (suma de todos los técnicos)
    private static final String COLOR_EQUIPO      = "#000000";
    // Color de la línea de referencia (Promedio del equipo)
    private static final String COLOR_REFERENCIA  = "#C07800";

    // Callback de navegación inyectado por MainController
    private com.reparaciones.utils.Navegable navegacion;

    // Nombre del técnico en sesión (null si es admin)
    private String nombreTecnicoSesion;

    // ─── Stock fields ──────────────────────────────────────────────────────────

    @FXML private javafx.scene.chart.BarChart<String, Number> chartStock;
    @FXML private CategoryAxis    ejeXStock;
    @FXML private NumberAxis      ejeYStock;
    @FXML private ComboBox<String> cmbModeloFiltro;
    @FXML private Label           lblSinDatosStock;

    private List<Componente> todosComponentesGestionados = List.of();
    private int numCategoriasStock = 1;

    @FXML
    public void initialize() {
        cmbGranularidad.setItems(FXCollections.observableArrayList("Día", "Semana", "Mes", "Año"));
        cmbGranularidad.setValue("Semana");
        dpDesde.setValue(null);
        dpHasta.setValue(null);

        ToggleGroup grupoMetrica = new ToggleGroup();
        rbPuntos.setToggleGroup(grupoMetrica);
        rbPuntosDia.setToggleGroup(grupoMetrica);
        grupoMetrica.selectedToggleProperty().addListener((obs, o, n) -> renderVentana(ventanaOffset));

        tooltipMedia.setShowDelay(Duration.ZERO);
        tooltipMedia.setShowDuration(Duration.INDEFINITE);
        tooltipMedia.setHideDelay(Duration.millis(100));

        dpDesde.getEditor().setDisable(true);
        dpDesde.getEditor().setOpacity(1.0);
        dpHasta.getEditor().setDisable(true);
        dpHasta.getEditor().setOpacity(1.0);
        dpDesde.valueProperty().addListener((obs, o, n) -> recargarDatos());
        dpHasta.valueProperty().addListener((obs, o, n) -> recargarDatos());

        cargarTecnicos();

        // Registrar color de "Equipo" para que todo el sistema de colores/hover funcione automáticamente
        coloresPorNombre.put("Equipo", COLOR_EQUIPO);
        chkEquipo.setSelected(true);
        chkEquipo.selectedProperty().addListener((obs, o, n) -> renderVentana(ventanaOffset));

        chkMedia.setSelected(false);
        chkMedia.selectedProperty().addListener((obs, o, n) -> actualizarVisibilidadMedia());

        recargarDatos();
        cargarTarjetas();

        // ── Stock ──────────────────────────────────────────────────────────────
        cmbModeloFiltro.valueProperty().addListener((obs, o, n) -> renderStockActual());
        chartStock.widthProperty().addListener((obs, o, w) -> ajustarAnchoBarras(w.doubleValue()));
        poblarFiltrosComponente();
    }

    /** Carga los técnicos de la BD, configura el MultiSelectComboBox con colores y separador. */
    private void cargarTecnicos() {
        List<Tecnico> tecnicos;
        try {
            tecnicos = new TecnicoDAO().getAll();
        } catch (SQLException e) {
            mostrarError(e);
            return;
        }

        Integer idTecSesion = com.reparaciones.Sesion.getIdTec();
        if (idTecSesion != null)
            nombreTecnicoSesion = tecnicos.stream()
                    .filter(t -> t.getIdTec() == idTecSesion)
                    .map(Tecnico::getNombre).findFirst().orElse(null);

        List<Tecnico> activos   = tecnicos.stream().filter(Tecnico::isActivo).collect(Collectors.toList());
        List<Tecnico> inactivos = tecnicos.stream().filter(t -> !t.isActivo()).collect(Collectors.toList());

        for (Tecnico t : activos) {
            coloresPorNombre.put(t.getNombre(), generarColor(t.getIdTec()));
            todosLosTecnicos.add(t);
        }
        if (!inactivos.isEmpty()) {
            todosLosTecnicos.add(null); // separador
            for (Tecnico t : inactivos) {
                coloresPorNombre.put(t.getNombre(), generarColor(t.getIdTec()));
                todosLosTecnicos.add(t);
            }
        }

        if (!com.reparaciones.Sesion.esAdminOSuperTecnico()) {
            if (nombreTecnicoSesion != null) nombresSeleccionadosTec.add(nombreTecnicoSesion);
            menuTecnicos.setVisible(false);
            menuTecnicos.setManaged(false);
            return; // sin desplegable: no montar el MultiSelectDropdown
        }

        filtroTecHandle = com.reparaciones.utils.MultiSelectDropdown.setup(
            menuTecnicos,
            todosLosTecnicos,
            lv -> new ListCell<Tecnico>() {
                private final CheckBox check = new CheckBox();
                {
                    check.setMouseTransparent(true);
                    check.setFocusTraversable(false);
                    setOnMouseClicked(e -> {
                        if (getItem() == null) return;
                        String nombre = getItem().getNombre();
                        if (nombresSeleccionadosTec.contains(nombre)) nombresSeleccionadosTec.remove(nombre);
                        else nombresSeleccionadosTec.add(nombre);
                        filtroTecHandle.refresh();
                        actualizarTextoMenuTecnicos();
                        renderVentana(ventanaOffset);
                    });
                }
                @Override protected void updateItem(Tecnico t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty) { setGraphic(null); setText(null); setStyle(""); return; }
                    if (t == null) {
                        setGraphic(null); setText(null);
                        setMouseTransparent(true);
                        setStyle("-fx-border-color: transparent transparent #AAAAAA transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 0 0; -fx-pref-height: 8;");
                        return;
                    }
                    setMouseTransparent(false);
                    boolean activo = t.isActivo();
                    check.setSelected(nombresSeleccionadosTec.contains(t.getNombre()));
                    setGraphic(check);
                    String colorHex = coloresPorNombre.getOrDefault(t.getNombre(), "#888888");
                    if (activo) {
                        setText(t.getNombre());
                        setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
                    } else {
                        setText(t.getNombre() + " (inactivo)");
                        setStyle("-fx-text-fill: #9A9A9A; -fx-font-style: italic;");
                    }
                }
            },
            etiquetaTecs,
            t -> t == null ? "" : t.getNombre());

        actualizarTextoMenuTecnicos();
    }

    /**
     * Genera un color hex determinista para un técnico a partir de su ID.
     * Usa el ángulo áureo (137.508°) sobre el espacio HSB para maximizar
     * la separación perceptual entre técnicos consecutivos.
     */
    private String generarColor(int idTec) {
        double hue = (idTec * 137.508) % 360;
        Color c = Color.hsb(hue, 0.75, 0.72);
        return String.format("#%02X%02X%02X",
                (int) (c.getRed()   * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue()  * 255));
    }

    private boolean metricaPorDia() { return rbPuntosDia.isSelected(); }

    /** Valor del punto según la métrica activa. */
    private double valorDe(PuntoEstadisticaPuntos p) {
        return metricaPorDia()
                ? PuntosEstadistica.puntosDia(p.getPuntos(), p.getPeriodo(),
                        cmbGranularidad.getValue(), java.time.LocalDate.now())
                : p.getPuntos();
    }

    /** Recarga datos de BD y reinicia la ventana. Llamado al cambiar fechas o granularidad. */
    @FXML
    private void recargarGrafico() {
        recargarDatos();
    }

    private void recargarDatos() {
        // Sin fechas: usar todo el rango disponible (1900-01-01 → 2999-12-31)
        java.time.LocalDate desde = dpDesde.getValue() != null
                ? dpDesde.getValue() : java.time.LocalDate.of(1900, 1, 1);
        java.time.LocalDate hasta = dpHasta.getValue() != null
                ? dpHasta.getValue() : java.time.LocalDate.of(2999, 12, 31);

        String granularidad = switch (cmbGranularidad.getValue()) {
            case "Día"  -> "dia";
            case "Mes"  -> "mes";
            case "Año"  -> "ano";
            default     -> "semana";
        };

        ventanaTamanio = switch (granularidad) {
            case "dia"    -> VENTANA_DIA;
            case "semana" -> VENTANA_SEMANA;
            case "ano"    -> VENTANA_ANO;
            default       -> VENTANA_MES;
        };

        try {
            todosPuntos = new ReparacionDAO().getEstadisticasPuntos(
                    granularidad, desde, hasta);
        } catch (SQLException e) {
            mostrarError(e);
            return;
        }

        // Periodos únicos ordenados (de todos los técnicos)
        todosPeriodos = todosPuntos.stream()
                .map(PuntoEstadisticaPuntos::getPeriodo)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        configurarVentana();
    }

    /** Configura la navegación de ventana y renderiza la más reciente. */
    private void configurarVentana() {
        int maxOffset = Math.max(0, todosPeriodos.size() - ventanaTamanio);
        boolean hayNavegacion = maxOffset > 0;
        hboxNavVentana.setVisible(hayNavegacion);
        hboxNavVentana.setManaged(hayNavegacion);
        ventanaOffset = maxOffset;                 // lo más reciente
        renderVentana(ventanaOffset);
    }

    @FXML private void ventanaAnterior()  { renderVentana(Math.max(0, ventanaOffset - 1)); }
    @FXML private void ventanaSiguiente() {
        renderVentana(Math.min(Math.max(0, todosPeriodos.size() - ventanaTamanio), ventanaOffset + 1));
    }

    /** Renderiza la ventana de periodos que empieza en `offset`. */
    private void renderVentana(int offset) {
        ventanaOffset = offset;
        if (todosPeriodos.isEmpty()) {
            chartReparaciones.getData().clear();
            lblSinDatos.setVisible(true);
            return;
        }
        lblSinDatos.setVisible(false);

        int tamanio = Math.min(ventanaTamanio, todosPeriodos.size());
        int inicio  = Math.max(0, Math.min(offset, todosPeriodos.size() - tamanio));
        int fin     = inicio + tamanio;

        Set<String> periodosVisibles = new LinkedHashSet<>(todosPeriodos.subList(inicio, fin));

        // Actualizar navegación de ventana
        int maxOffset = Math.max(0, todosPeriodos.size() - ventanaTamanio);
        btnVentanaAnterior.setDisable(inicio == 0);
        btnVentanaSiguiente.setDisable(offset >= maxOffset);
        lblRangoVentana.setText(tamanio + " periodos · "
                + todosPeriodos.get(inicio) + " — " + todosPeriodos.get(fin - 1));

        Set<String> seleccionados = new LinkedHashSet<>(nombresSeleccionadosTec);

        Map<String, XYChart.Series<String, Number>> series = new LinkedHashMap<>();
        for (PuntoEstadisticaPuntos p : todosPuntos) {
            if (!seleccionados.contains(p.getNombreTecnico())) continue;
            if (!periodosVisibles.contains(p.getPeriodo()))    continue;
            series.computeIfAbsent(p.getNombreTecnico(), nombre -> {
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName(nombre);
                return s;
            }).getData().add(new XYChart.Data<>(p.getPeriodo(), valorDe(p)));
        }

        // Serie "Equipo": suma de TODOS los técnicos por periodo (independiente de checkboxes)
        List<XYChart.Series<String, Number>> listaFinal = new java.util.ArrayList<>(series.values());
        if (chkEquipo.isSelected()) {
            Map<String, Double> sumaPorPeriodo = new java.util.LinkedHashMap<>();
            for (String p : periodosVisibles) sumaPorPeriodo.put(p, 0.0);
            for (PuntoEstadisticaPuntos p : todosPuntos) {
                if (periodosVisibles.contains(p.getPeriodo()))
                    sumaPorPeriodo.merge(p.getPeriodo(), valorDe(p), Double::sum);
            }
            XYChart.Series<String, Number> serieEquipo = new XYChart.Series<>();
            serieEquipo.setName("Equipo");
            sumaPorPeriodo.forEach((periodo, valor) ->
                    serieEquipo.getData().add(new XYChart.Data<>(periodo, valor)));
            listaFinal.add(serieEquipo);
        }
        chartReparaciones.getData().setAll(listaFinal);

        // Eje Y: upper = máximo visible + 1 de margen (mínimo 5)
        double maxVisible = todosPuntos.stream()
                .filter(p -> seleccionados.contains(p.getNombreTecnico())
                          && periodosVisibles.contains(p.getPeriodo()))
                .mapToDouble(this::valorDe)
                .max().orElse(0);
        if (chkEquipo.isSelected()) {
            Map<String, Double> sumaPorPeriodo = new java.util.HashMap<>();
            for (PuntoEstadisticaPuntos p : todosPuntos) {
                if (periodosVisibles.contains(p.getPeriodo()))
                    sumaPorPeriodo.merge(p.getPeriodo(), valorDe(p), Double::sum);
            }
            double maxEquipo = sumaPorPeriodo.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            maxVisible = Math.max(maxVisible, maxEquipo);
        }
        ejeY.setUpperBound(Math.max(5, Math.ceil(maxVisible) + 1));
        ejeY.setLabel(metricaPorDia() ? "Puntos/día" : "Puntos");

        Runnable render = () -> {
            chartReparaciones.applyCss();
            chartReparaciones.layout();
            aplicarColores();
            chartReparaciones.applyCss();
            chartReparaciones.layout();
            // Las líneas de media necesitan un pulso completo de escena para que el eje
            // haya recalculado su escala con el nuevo upperBound antes de getDisplayPosition()
            final List<XYChart.Series<String, Number>> ts =
                    new java.util.ArrayList<>(chartReparaciones.getData());
            Platform.runLater(() -> {
                chartReparaciones.applyCss();
                chartReparaciones.layout();
                dibujarLineasMedia(periodosVisibles, ts);
            });

            for (javafx.scene.Node item : chartReparaciones.lookupAll(".chart-legend-item")) {
                if (!(item instanceof Label lbl)) continue;
                String nombre = lbl.getText();
                boolean esTecnico = !"Equipo".equals(nombre);
                lbl.setStyle(esTecnico ? "-fx-cursor: hand;" : "");
                lbl.setOnMouseClicked(e -> {
                    if (!esTecnico) return;
                    nombresSeleccionadosTec.remove(nombre);
                    if (filtroTecHandle != null) filtroTecHandle.refresh();
                    actualizarTextoMenuTecnicos();
                    renderVentana(ventanaOffset);
                });
            }
        };
        if (chartReparaciones.getScene() != null) render.run();
        else Platform.runLater(render);
    }

    private void dibujarLineasMedia(Set<String> periodosVisibles, List<XYChart.Series<String, Number>> todasSeries) {
        Node bg = chartReparaciones.lookup(".chart-plot-background");
        if (bg == null || !(bg.getParent() instanceof javafx.scene.layout.Pane)) return;
        javafx.scene.layout.Pane plotArea = (javafx.scene.layout.Pane) bg.getParent();

        // Quitar líneas anteriores
        plotArea.getChildren().removeAll(lineasMedia);
        plotArea.getChildren().removeAll(lineasReferencia);
        lineasMedia.clear();
        lineasReferencia.clear();
        lineaRefVisual = null;
        lineaMediaPorSerie.clear();
        mediaPorSerie.clear();

        // Precomputar suma total por periodo (reutilizado en "Equipo")
        Map<String, Double> sumaPorPeriodo = new java.util.HashMap<>();
        for (PuntoEstadisticaPuntos p : todosPuntos) {
            if (periodosVisibles.contains(p.getPeriodo()))
                sumaPorPeriodo.merge(p.getPeriodo(), valorDe(p), Double::sum);
        }

        for (XYChart.Series<String, Number> serie : chartReparaciones.getData()) {
            String color = coloresPorNombre.getOrDefault(serie.getName(), "#888888");

            double media;
            if ("Equipo".equals(serie.getName())) {
                media = sumaPorPeriodo.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            } else {
                media = todosPuntos.stream()
                        .filter(p -> p.getNombreTecnico().equals(serie.getName())
                                  && periodosVisibles.contains(p.getPeriodo()))
                        .mapToDouble(this::valorDe)
                        .average().orElse(0);
            }

            if (media <= 0) continue;

            double yEnEje   = ejeY.getDisplayPosition(media);
            double yEnScene = ejeY.localToScene(0, yEnEje).getY();
            double y        = plotArea.sceneToLocal(0, yEnScene).getY();

            // Usar los límites reales del área de trazado (chart-plot-background),
            // no los del pane padre que incluye el espacio de las etiquetas del eje Y
            double x0 = bg.getBoundsInParent().getMinX();
            double x1 = bg.getBoundsInParent().getMaxX();

            javafx.scene.shape.Line linea = new javafx.scene.shape.Line(x0, y, x1, y);
            linea.setStroke(javafx.scene.paint.Color.web(color));
            linea.setStrokeWidth(1.2);
            linea.getStrokeDashArray().addAll(8.0, 5.0);
            linea.setOpacity(0.6);
            linea.setMouseTransparent(true);
            bg.boundsInParentProperty().addListener((obs, o, b) -> {
                linea.setStartX(b.getMinX()); linea.setEndX(b.getMaxX());
            });

            // Línea invisible ancha para detección del ratón (la visual es de 1.2px, imposible de clicar)
            final XYChart.Series<String, Number> serieRef = serie;
            javafx.scene.shape.Line hitLinea = new javafx.scene.shape.Line(x0, y, x1, y);
            hitLinea.setStroke(javafx.scene.paint.Color.color(0, 0, 0, 0.01));
            hitLinea.setStrokeWidth(12);
            bg.boundsInParentProperty().addListener((obs, o, b) -> {
                hitLinea.setStartX(b.getMinX()); hitLinea.setEndX(b.getMaxX());
            });
            hitLinea.setOnMouseEntered(e -> { sobreLineaMedia = true;  serieResaltada = serieRef; resaltarSerie(serieRef, todasSeries); });
            hitLinea.setOnMouseExited (e -> { sobreLineaMedia = false; serieResaltada = null;     restaurarSeries(todasSeries); });

            Label lbl = new Label("x̄ " + PuntosEstadistica.formatearPuntos(media));
            lbl.setStyle("-fx-font-size:10px; -fx-text-fill:" + color +
                         "; -fx-background-color:white; -fx-padding:0 2 0 2;");
            lbl.setLayoutX(x0 + 4);
            lbl.setLayoutY(y - 14);
            lbl.setMouseTransparent(true);

            mediaPorSerie.put(serie, media);
            String unidadMedia = metricaPorDia() ? " puntos/día" : " puntos";
            Tooltip tipMedia = new Tooltip("Media " + serie.getName() + ": "
                    + PuntosEstadistica.formatearPuntos(media) + unidadMedia);
            tipMedia.setShowDelay(Duration.ZERO);
            tipMedia.setShowDuration(Duration.INDEFINITE);
            tipMedia.setHideDelay(Duration.millis(100));
            Tooltip.install(hitLinea, tipMedia);

            plotArea.getChildren().addAll(linea, lbl, hitLinea);
            lineasMedia.add(linea);
            lineasMedia.add(lbl);
            lineasMedia.add(hitLinea);
            lineaMediaPorSerie.put(serie, linea);
        }

        // Línea "Promedio": promedio de ventana de la métrica activa sobre los técnicos
        // (nunca sobre la serie "Equipo"); siempre visible salvo que no haya actividad.
        Map<String, Map<String, Double>> datosVentana = new LinkedHashMap<>();
        for (PuntoEstadisticaPuntos p : todosPuntos) {
            datosVentana.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                        .put(p.getPeriodo(), valorDe(p));
        }
        double refMedia = PuntosEstadistica.promedioVentana(
                datosVentana, new java.util.ArrayList<>(periodosVisibles));

        if (refMedia > 0) { // sin actividad → no se dibuja
            double x0ref = bg.getBoundsInParent().getMinX();
            double x1ref = bg.getBoundsInParent().getMaxX();
            double yRef  = plotArea.sceneToLocal(0,
                    ejeY.localToScene(0, ejeY.getDisplayPosition(refMedia)).getY()).getY();

            javafx.scene.shape.Line lineaRef = new javafx.scene.shape.Line(x0ref, yRef, x1ref, yRef);
            lineaRef.setStroke(javafx.scene.paint.Color.web(COLOR_REFERENCIA));
            lineaRef.setStrokeWidth(1.8);
            lineaRef.getStrokeDashArray().addAll(10.0, 4.0, 2.0, 4.0);
            lineaRef.setOpacity(0.85);
            lineaRef.setMouseTransparent(true);
            bg.boundsInParentProperty().addListener((obs, o, b) -> {
                lineaRef.setStartX(b.getMinX()); lineaRef.setEndX(b.getMaxX());
            });

            javafx.scene.shape.Line hitRef = new javafx.scene.shape.Line(x0ref, yRef, x1ref, yRef);
            hitRef.setStroke(javafx.scene.paint.Color.color(0, 0, 0, 0.01));
            hitRef.setStrokeWidth(12);
            bg.boundsInParentProperty().addListener((obs, o, b) -> {
                hitRef.setStartX(b.getMinX()); hitRef.setEndX(b.getMaxX());
            });
            List<String> porEncima = todosLosTecnicos.stream().filter(t -> t != null).map(Tecnico::getNombre)
                    .filter(nombre -> {
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && periodosVisibles.contains(p.getPeriodo()))
                                .mapToDouble(this::valorDe)
                                .average().orElse(0);
                        return media > refMedia;
                    }).collect(Collectors.toList());
            List<String> porDebajo = todosLosTecnicos.stream().filter(t -> t != null).map(Tecnico::getNombre)
                    .filter(nombre -> {
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && periodosVisibles.contains(p.getPeriodo()))
                                .mapToDouble(this::valorDe)
                                .average().orElse(0);
                        return media > 0 && media <= refMedia;
                    }).collect(Collectors.toList());

            String encimaTxt = porEncima.isEmpty() ? "—" : String.join(", ", porEncima);
            String debajTxt  = porDebajo.isEmpty() ? "—" : String.join(", ", porDebajo);
            String unidadRef = metricaPorDia() ? "puntos/día" : "puntos";
            Tooltip tipRef = new Tooltip(String.format(
                    "Promedio del equipo: %s %s%n" +
                    "Por encima: %s%n" +
                    "Por debajo: %s",
                    PuntosEstadistica.formatearPuntos(refMedia), unidadRef, encimaTxt, debajTxt));
            tipRef.setShowDelay(Duration.ZERO);
            tipRef.setShowDuration(Duration.INDEFINITE);
            tipRef.setHideDelay(Duration.millis(100));
            Tooltip.install(hitRef, tipRef);

            Label lblRef = new Label("Promedio " + PuntosEstadistica.formatearPuntos(refMedia));
            lblRef.setStyle("-fx-font-size:10px; -fx-text-fill:" + COLOR_REFERENCIA +
                            "; -fx-background-color:white; -fx-padding:0 2 0 2;");
            lblRef.setLayoutX(x0ref + 4);
            lblRef.setLayoutY(yRef - 14);
            lblRef.setMouseTransparent(true);

            lineaRefVisual = lineaRef;
            hitRef.setOnMouseEntered(e -> {
                sobreLineaMedia = true;
                resaltarReferencia(new java.util.ArrayList<>(chartReparaciones.getData()));
            });
            hitRef.setOnMouseExited(e -> {
                sobreLineaMedia = false;
                restaurarSeries(new java.util.ArrayList<>(chartReparaciones.getData()));
            });

            plotArea.getChildren().addAll(lineaRef, lblRef, hitRef);
            lineasReferencia.add(lineaRef);
            lineasReferencia.add(lblRef);
            lineasReferencia.add(hitRef);
        }

        actualizarVisibilidadMedia();
        actualizarVisibilidadReferencia();
    }

    private void actualizarVisibilidadReferencia() {
        lineasReferencia.forEach(n -> n.setVisible(true));
    }

    private void actualizarVisibilidadMedia() {
        boolean visible = chkMedia.isSelected();
        lineasMedia.forEach(n -> n.setVisible(visible));
    }

    /** Nº de trabajos (normales + glass + pulidos) de un técnico (o "Equipo") en un periodo. */
    private int trabajosDe(String tecnico, String periodo) {
        return todosPuntos.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && ("Equipo".equals(tecnico) || p.getNombreTecnico().equals(tecnico)))
                .mapToInt(p -> p.getnNormales() + p.getnGlass() + p.getnPulidos()).sum();
    }

    /** Aplica el color fijo de cada técnico a su línea, puntos y símbolo de leyenda. */
    private void aplicarColores() {
        List<XYChart.Series<String, Number>> todasSeries = new java.util.ArrayList<>(chartReparaciones.getData());

        for (XYChart.Series<String, Number> serie : todasSeries) {
            String color = coloresPorNombre.getOrDefault(serie.getName(), "#888888");

            // Línea visual
            Node lineaNodo = serie.getNode();
            if (lineaNodo != null)
                lineaNodo.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2px;");

            // Puntos: siempre visibles en vista anual o si la serie tiene un solo punto;
            // ocultos por defecto en el resto de granularidades (se revelan al hover)
            boolean puntosVisibles = "Año".equals(cmbGranularidad.getValue())
                    || serie.getData().size() == 1;
            for (XYChart.Data<String, Number> d : serie.getData()) {
                Node nodo = d.getNode();
                if (nodo == null) continue;

                nodo.setStyle(puntosVisibles
                        ? "-fx-background-color: " + color + ", white;"
                        : "-fx-background-color: transparent, transparent;");

                Tooltip tip = new Tooltip(PuntosEstadistica.textoTooltip(d.getXValue(), d.getYValue().doubleValue(),
                        metricaPorDia(), trabajosDe(serie.getName(), d.getXValue())));
                tip.setShowDelay(Duration.ZERO);
                tip.setShowDuration(Duration.INDEFINITE);
                tip.setHideDelay(Duration.millis(100));
                Tooltip.install(nodo, tip);

                boolean navegable = navegacion != null &&
                        (com.reparaciones.Sesion.esAdminOSuperTecnico() ||
                         serie.getName().equals(nombreTecnicoSesion));
                nodo.setOnMouseEntered(e -> {
                    String cursor = navegable ? "; -fx-cursor: hand;" : ";";
                    nodo.setStyle("-fx-background-color: " + color + ", white" + cursor);
                    if (serieResaltada != serie) { serieResaltada = serie; resaltarSerie(serie, todasSeries); }
                });
                nodo.setOnMouseExited(e -> {
                    nodo.setStyle(puntosVisibles
                            ? "-fx-background-color: " + color + ", white;"
                            : "-fx-background-color: transparent, transparent;");
                });
                if (navegable) {
                    final XYChart.Series<String, Number> serieClick = serie;
                    nodo.setOnMouseClicked(e ->
                            mostrarPopoverDesglose(nodo, serieClick.getName(), d.getXValue()));
                }
            }
        }

        // Hover sobre la línea continua: detectar serie más cercana (umbral 8px)
        chartReparaciones.setOnMouseMoved(e -> {
            if (sobreLineaMedia) return;

            // Si el ratón está encima de un nodo de dato, no mostrar tooltip de media
            boolean sobrePunto = todasSeries.stream()
                    .flatMap(s -> s.getData().stream())
                    .map(XYChart.Data::getNode)
                    .filter(n -> n != null)
                    .anyMatch(n -> n.contains(n.sceneToLocal(e.getSceneX(), e.getSceneY())));

            XYChart.Series<String, Number> cercana = serieMasCercana(e.getX(), e.getY(), todasSeries, 8);
            if (cercana != serieResaltada) {
                serieResaltada = cercana;
                tooltipMedia.hide();
                if (cercana != null) {
                    resaltarSerie(cercana, todasSeries);
                    if (!sobrePunto) {
                        Double m = mediaPorSerie.get(cercana);
                        if (m != null && m > 0) {
                            String unidad = metricaPorDia() ? " puntos/día" : " puntos";
                            tooltipMedia.setText("Media " + cercana.getName() + ": "
                                    + PuntosEstadistica.formatearPuntos(m) + unidad);
                            tooltipMedia.show(chartReparaciones, e.getScreenX() + 12, e.getScreenY() - 20);
                        }
                    }
                } else {
                    restaurarSeries(todasSeries);
                }
            } else if (sobrePunto) {
                tooltipMedia.hide();
            }
        });
        chartReparaciones.setOnMouseExited(e -> {
            serieResaltada = null;
            sobreLineaMedia = false;
            tooltipMedia.hide();
            restaurarSeries(todasSeries);
        });

        // Símbolos de leyenda
        chartReparaciones.lookupAll(".chart-legend-item").forEach(item -> {
            if (item instanceof Label lbl) {
                String color = coloresPorNombre.get(lbl.getText());
                if (color == null) return;
                Node simbolo = lbl.lookup(".chart-legend-item-symbol");
                if (simbolo != null)
                    simbolo.setStyle("-fx-background-color: " + color + ", white;");
            }
        });
    }

    /** Popover con el desglose del punto y salto opcional al Historial (spec §4). */
    private void mostrarPopoverDesglose(javafx.scene.Node ancla, String nombreSerie, String periodo) {
        boolean esEquipo = "Equipo".equals(nombreSerie);
        PuntoEstadisticaPuntos datos = todosPuntos.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && (esEquipo || p.getNombreTecnico().equals(nombreSerie)))
                .reduce((a, b) -> new PuntoEstadisticaPuntos(nombreSerie, periodo,
                        a.getPuntos() + b.getPuntos(),
                        a.getPuntosNormales() + b.getPuntosNormales(),
                        a.getPuntosGlass() + b.getPuntosGlass(),
                        a.getPuntosPulidos() + b.getPuntosPulidos(),
                        a.getnNormales() + b.getnNormales(),
                        a.getnGlass() + b.getnGlass(),
                        a.getnPulidos() + b.getnPulidos(),
                        a.getnSinPiezas() + b.getnSinPiezas()))
                .orElse(null);
        if (datos == null) return;

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        Label titulo = new Label(nombreSerie + " — " + periodo);
        titulo.setStyle("-fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label cuerpo = new Label(PuntosEstadistica.textoPopover(datos));
        cuerpo.setStyle("-fx-text-fill: #2C3B54; -fx-font-size: 12px;");
        Button verHistorial = new Button("Ver en Historial");
        verHistorial.getStyleClass().add("btn-secondary");
        verHistorial.setOnAction(ev -> {
            popup.hide();
            java.time.LocalDate[] rango =
                    PuntosEstadistica.periodoAFechas(periodo, cmbGranularidad.getValue());
            navegacion.navegarAReparaciones(rango[0], rango[1], esEquipo ? null : nombreSerie);
        });

        VBox caja = new VBox(6, titulo, cuerpo, verHistorial);
        caja.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;"
                + " -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 12;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 2);");
        popup.getContent().add(caja);
        var b = ancla.localToScreen(ancla.getBoundsInLocal());
        popup.show(ancla, b.getMaxX() + 6, b.getMinY() - 10);
    }

    /**
     * Devuelve la serie cuya línea está más cerca del punto (mx, my) en coordenadas
     * locales del chart, o null si ninguna está dentro del umbral de píxeles dado.
     */
    private XYChart.Series<String, Number> serieMasCercana(
            double mx, double my,
            List<XYChart.Series<String, Number>> series, double umbral) {

        XYChart.Series<String, Number> mejor = null;
        double menorDist = umbral;

        for (XYChart.Series<String, Number> serie : series) {
            Node nodo = serie.getNode();
            if (!(nodo instanceof javafx.scene.shape.Path path)) continue;

            // Convertir punto del chart al sistema local del Path
            javafx.geometry.Point2D pt = path.sceneToLocal(
                    chartReparaciones.localToScene(mx, my));
            double px = pt.getX(), py = pt.getY();

            javafx.scene.shape.PathElement[] elems =
                    path.getElements().toArray(new javafx.scene.shape.PathElement[0]);
            double ax = 0, ay = 0;
            for (javafx.scene.shape.PathElement el : elems) {
                double bx, by;
                if (el instanceof javafx.scene.shape.MoveTo m) { ax = m.getX(); ay = m.getY(); continue; }
                else if (el instanceof javafx.scene.shape.LineTo l) { bx = l.getX(); by = l.getY(); }
                else continue;

                // Distancia punto-segmento
                double dx = bx - ax, dy = by - ay;
                double len2 = dx * dx + dy * dy;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
                double cx = ax + t * dx, cy = ay + t * dy;
                double dist = Math.hypot(px - cx, py - cy);
                if (dist < menorDist) { menorDist = dist; mejor = serie; }
                ax = bx; ay = by;
            }
        }
        return mejor;
    }

    private void resaltarSerie(XYChart.Series<String, Number> activa,
                               List<XYChart.Series<String, Number>> todas) {
        for (XYChart.Series<String, Number> s : todas) {
            String color = coloresPorNombre.getOrDefault(s.getName(), "#888888");
            boolean estaActiva = s == activa;
            double opacidad = estaActiva ? 1 : 0.25;
            if (s.getNode() != null)
                s.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: " +
                        (estaActiva ? "3px" : "1.5px") + "; -fx-opacity: " + opacidad + ";");
            javafx.scene.shape.Line ld = lineaMediaPorSerie.get(s);
            if (ld != null) ld.setOpacity(estaActiva ? 0.9 : 0.15);
        }
        if (lineaRefVisual != null) lineaRefVisual.setOpacity(0.15);
    }

    private void resaltarReferencia(List<XYChart.Series<String, Number>> todas) {
        for (XYChart.Series<String, Number> s : todas) {
            String color = coloresPorNombre.getOrDefault(s.getName(), "#888888");
            if (s.getNode() != null)
                s.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 1.5px; -fx-opacity: 0.25;");
            javafx.scene.shape.Line ld = lineaMediaPorSerie.get(s);
            if (ld != null) ld.setOpacity(0.15);
        }
        if (lineaRefVisual != null) lineaRefVisual.setOpacity(0.9);
    }

    private void restaurarSeries(List<XYChart.Series<String, Number>> todas) {
        for (XYChart.Series<String, Number> s : todas) {
            String color = coloresPorNombre.getOrDefault(s.getName(), "#888888");
            if (s.getNode() != null)
                s.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2px; -fx-opacity: 1;");
            javafx.scene.shape.Line ld = lineaMediaPorSerie.get(s);
            if (ld != null) ld.setOpacity(0.6);
        }
        if (lineaRefVisual != null) lineaRefVisual.setOpacity(0.85);
    }

    private void actualizarTextoMenuTecnicos() {
        long total = todosLosTecnicos.stream().filter(t -> t != null).count();
        long sel   = nombresSeleccionadosTec.size();
        if (sel == 0)
            etiquetaTecs.set("+ Técnicos");
        else if (sel == total)
            etiquetaTecs.set("Técnicos");
        else if (sel == 1)
            etiquetaTecs.set(nombresSeleccionadosTec.iterator().next());
        else
            etiquetaTecs.set(sel + " técnicos");
    }

    @FXML
    private void limpiarFiltros() {
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        nombresSeleccionadosTec.clear();
        // Arranque limpio: admin/supertécnico quedan sin selección (solo Equipo + Promedio);
        // técnico raso siempre vuelve a verse solo a sí mismo, nunca al resto del equipo.
        if (!com.reparaciones.Sesion.esAdminOSuperTecnico() && nombreTecnicoSesion != null)
            nombresSeleccionadosTec.add(nombreTecnicoSesion);
        if (filtroTecHandle != null) filtroTecHandle.refresh();
        chkEquipo.setSelected(true);
        chkMedia.setSelected(false);
        actualizarTextoMenuTecnicos();
        recargarDatos();
    }

    @FXML
    private void mostrarReparaciones() {
        pnlReparaciones.setVisible(true);  pnlReparaciones.setManaged(true);
        pnlStock.setVisible(false);        pnlStock.setManaged(false);
        setActivo(btnTabReparaciones, btnTabStock);
    }

    @FXML
    private void mostrarStock() {
        pnlStock.setVisible(true);         pnlStock.setManaged(true);
        pnlReparaciones.setVisible(false); pnlReparaciones.setManaged(false);
        setActivo(btnTabStock, btnTabReparaciones);
    }

    private void setActivo(Button activo, Button inactivo) {
        activo.getStyleClass().setAll("stock-sidebar-btn-active");
        inactivo.getStyleClass().setAll("stock-sidebar-btn");
    }

    @Override
    public void recargar() {
        if (pnlStock.isVisible()) renderStockActual();
        else {
            recargarDatos();
            cargarTarjetas();
        }
    }

    @Override
    public void detenerPolling() { /* sin poller */ }

    /** Inyectado por MainController para permitir navegación a la vista de reparaciones. */
    public void setNavegacion(com.reparaciones.utils.Navegable navegacion) {
        this.navegacion = navegacion;
    }

    // ─── Stock methods ─────────────────────────────────────────────────────────

    private static final java.util.LinkedHashMap<String, String> PREFIJO_TIPO;
    static {
        PREFIJO_TIPO = new java.util.LinkedHashMap<>();
        PREFIJO_TIPO.put("bat", "Batería");
        PREFIJO_TIPO.put("cha", "Chasis");
        PREFIJO_TIPO.put("cam", "Cámara");
        PREFIJO_TIPO.put("lcd", "LCD");
        PREFIJO_TIPO.put("mc",  "Marco");
        PREFIJO_TIPO.put("g",   "Pantalla");
    }

    private static String tipoDeComponente(String sku) {
        String lower = sku.toLowerCase();
        for (var entry : PREFIJO_TIPO.entrySet()) {
            if (lower.startsWith(entry.getKey())) return entry.getValue();
        }
        return "Otro";
    }

    private static String modeloDeComponente(String sku) {
        String lower = sku.toLowerCase();
        for (String prefijo : PREFIJO_TIPO.keySet()) {
            if (lower.startsWith(prefijo)) {
                String raw = FormularioReparacionController.extraerModelo(lower, prefijo);
                return raw.isEmpty() ? sku : FormularioReparacionController.traducirModelo(raw);
            }
        }
        return sku;
    }

    private static String labelComponente(String sku) {
        String tipo  = tipoDeComponente(sku);
        String lower = sku.toLowerCase();
        for (String prefijo : PREFIJO_TIPO.keySet()) {
            if (lower.startsWith(prefijo)) {
                String raw   = FormularioReparacionController.extraerModelo(lower, prefijo);
                String resto = lower.substring(prefijo.length());
                if (resto.startsWith("i")) resto = resto.substring(1);
                String color = raw.isEmpty() ? resto : resto.substring(raw.length());
                if (!color.isEmpty()) return tipo + " (" + color + ")";
                break;
            }
        }
        return tipo;
    }

    private void poblarFiltrosComponente() {
        try {
            todosComponentesGestionados = new ComponenteDAO().getAllGestionados().stream()
                    .filter(com.reparaciones.models.Componente::isActivo)
                    .collect(java.util.stream.Collectors.toList());
        } catch (SQLException e) {
            mostrarError(e);
            return;
        }
        java.util.Set<String> rawModelos = new java.util.HashSet<>();
        for (Componente c : todosComponentesGestionados) {
            String lower = c.getTipo().toLowerCase();
            for (String prefijo : PREFIJO_TIPO.keySet()) {
                if (lower.startsWith(prefijo)) {
                    String raw = FormularioReparacionController.extraerModelo(lower, prefijo);
                    if (!raw.isEmpty()) rawModelos.add(raw);
                    break;
                }
            }
        }
        List<String> modelos = FormularioReparacionController.MODELOS_ORDENADOS.stream()
                .filter(rawModelos::contains)
                .map(FormularioReparacionController::traducirModelo)
                .collect(Collectors.toList());
        cmbModeloFiltro.setItems(FXCollections.observableArrayList(modelos));
        if (!modelos.isEmpty()) cmbModeloFiltro.setValue(modelos.get(0));
    }

    @FXML
    private void filtrarComponentes() {
        renderStockActual();
    }

    private void renderStockActual() {
        String modelo = cmbModeloFiltro.getValue();
        if (modelo == null) {
            chartStock.getData().clear();
            lblSinDatosStock.setVisible(true);
            return;
        }

        List<Componente> filtrados = todosComponentesGestionados.stream()
                .filter(c -> modeloDeComponente(c.getTipo()).equals(modelo))
                .collect(Collectors.toList());

        if (filtrados.isEmpty()) {
            chartStock.getData().clear();
            lblSinDatosStock.setVisible(true);
            return;
        }
        lblSinDatosStock.setVisible(false);

        XYChart.Series<String, Number> serieStock    = new XYChart.Series<>();
        XYChart.Series<String, Number> serieEnCamino = new XYChart.Series<>();
        serieStock.setName("Stock actual");
        serieEnCamino.setName("En camino");

        Map<String, Componente> porLabel = new LinkedHashMap<>();
        for (Componente c : filtrados) {
            String label = labelComponente(c.getTipo());
            porLabel.put(label, c);
            serieStock.getData().add(new XYChart.Data<>(label, c.getStock()));
            serieEnCamino.getData().add(new XYChart.Data<>(label, c.getEnCamino()));
        }

        numCategoriasStock = filtrados.size();
        chartStock.getData().setAll(serieStock, serieEnCamino);

        int maxY = filtrados.stream()
                .mapToInt(c -> c.getStock() + c.getEnCamino())
                .max().orElse(10);
        ejeYStock.setUpperBound(Math.max(10, maxY + 2));
        ejeYStock.setTickUnit(Math.max(1, (maxY + 2) / 10));

        Runnable render = () -> {
            chartStock.applyCss();
            chartStock.layout();
            colorearBarrasStock(serieStock, serieEnCamino, porLabel);
            Platform.runLater(() -> {
                chartStock.applyCss();
                chartStock.layout();
                ajustarAnchoBarras(chartStock.getWidth());
                corregirLeyendaStock();
            });
        };
        if (chartStock.getScene() != null) render.run();
        else Platform.runLater(render);
    }

    private void colorearBarrasStock(XYChart.Series<String, Number> serieStock,
                                      XYChart.Series<String, Number> serieEnCamino,
                                      Map<String, Componente> porLabel) {
        for (XYChart.Data<String, Number> d : serieStock.getData()) {
            if (d.getNode() == null) continue;
            Componente c = porLabel.get(d.getXValue());
            boolean bajo = c != null && c.getStock() <= c.getStockMinimo();
            String color = bajo ? com.reparaciones.utils.Colores.FILA_SOLICITUD_BRD
                                : com.reparaciones.utils.Colores.VERDE_OK;
            d.getNode().setStyle("-fx-bar-fill: " + color + ";");

            String tipTxt = d.getXValue() + "\nStock: " + d.getYValue().intValue() + " uds.";
            if (c != null) tipTxt += "\nMínimo: " + c.getStockMinimo() + " uds.";
            Tooltip tip = new Tooltip(tipTxt);
            tip.setShowDelay(Duration.ZERO);
            tip.setShowDuration(Duration.INDEFINITE);
            tip.setHideDelay(Duration.millis(100));
            Tooltip.install(d.getNode(), tip);
        }
        for (XYChart.Data<String, Number> d : serieEnCamino.getData()) {
            if (d.getNode() == null) continue;
            if (d.getYValue().intValue() == 0) {
                d.getNode().setStyle("-fx-bar-fill: transparent;");
            } else {
                d.getNode().setStyle("-fx-bar-fill: #90CAF9;");
                Tooltip tip = new Tooltip(
                        d.getXValue() + "\nEn camino: " + d.getYValue().intValue() + " uds.");
                tip.setShowDelay(Duration.ZERO);
                tip.setShowDuration(Duration.INDEFINITE);
                tip.setHideDelay(Duration.millis(100));
                Tooltip.install(d.getNode(), tip);
            }
        }
    }

    private void ajustarAnchoBarras(double chartWidth) {
        if (numCategoriasStock <= 0 || chartWidth <= 0) return;
        double available  = chartWidth - 80;
        double targetPair = 60.0;
        double gap = Math.max(10, (available - numCategoriasStock * targetPair) / (numCategoriasStock + 1));
        chartStock.setCategoryGap(gap);
    }

    private void corregirLeyendaStock() {
        java.util.Map<String, String> colores = java.util.Map.of(
                "Stock actual", com.reparaciones.utils.Colores.VERDE_OK,
                "En camino",    "#90CAF9");
        chartStock.lookupAll(".chart-legend-item").forEach(item -> {
            if (item instanceof Label lbl) {
                String color = colores.get(lbl.getText());
                if (color == null) return;
                Node simbolo = lbl.lookup(".bar-legend-symbol");
                if (simbolo == null) simbolo = lbl.lookup(".chart-legend-item-symbol");
                if (simbolo != null)
                    simbolo.setStyle("-fx-background-color: " + color + ";");
            }
        });
    }

    // ─── Navigation helpers ────────────────────────────────────────────────────

    /** Tarjetas del mes en curso (equipo, o el propio técnico si el rol es TECNICO). */
    private void cargarTarjetas() {
        java.time.YearMonth mes = java.time.YearMonth.now();
        List<PuntoEstadisticaPuntos> filas;
        try {
            filas = new ReparacionDAO().getEstadisticasPuntos("mes",
                    mes.minusMonths(1).atDay(1), mes.atEndOfMonth());
        } catch (SQLException e) { mostrarError(e); return; }
        String tecnico = com.reparaciones.Sesion.esAdminOSuperTecnico() ? null : nombreTecnicoSesion;
        var t = PuntosEstadistica.calcularTarjetas(filas, mes, java.time.LocalDate.now(), tecnico);
        String quien = tecnico == null ? "equipo" : "tú";
        lblCardPuntosTitulo.setText("Puntos · " + t.mesLabel() + " · " + quien);
        lblCardPuntosValor.setText(PuntosEstadistica.formatearPuntos(t.puntos()));
        pintarDelta(lblCardPuntosDelta, t.deltaPuntosPct());
        lblCardDiaTitulo.setText("Puntos/día · " + t.mesLabel() + " · " + quien);
        lblCardDiaValor.setText(PuntosEstadistica.formatearPuntos(t.puntosDia()));
        pintarDelta(lblCardDiaDelta, t.deltaPuntosDiaPct());
    }

    private void pintarDelta(Label lbl, Double pct) {
        if (pct == null) { lbl.setText(""); return; }
        boolean sube = pct >= 0;
        lbl.setText((sube ? "▲ +" : "▼ ") + String.format("%.0f", pct) + "%");
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (sube ? "#2E7D32" : "#C62828") + ";");
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
