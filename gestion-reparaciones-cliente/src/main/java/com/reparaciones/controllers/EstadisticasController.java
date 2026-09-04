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
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
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
 *   <li>Checkbox "Ocultar medias" — las líneas x̄ discontinuas se ven por defecto (ajuste 2026-09-04).</li>
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
    @FXML private Button   btnValores;
    @FXML private Button   btnTecnicosEstadistica;

    @FXML private VBox     pnlReparaciones;
    @FXML private VBox     pnlStock;

    @FXML private ComboBox<String> cmbGranularidad;
    @FXML private DatePicker       dpDesde;
    @FXML private DatePicker       dpHasta;
    @FXML private LineChart<String, Number> chartReparaciones;
    @FXML private CategoryAxis     ejeX;
    @FXML private NumberAxis       ejeY;
    @FXML private com.reparaciones.utils.MultiSelectComboBox<Tecnico> menuTecnicos;
    @FXML private HBox             boxMetrica;
    @FXML private RadioButton      rbPuntos;
    @FXML private RadioButton      rbPuntosDia;
    @FXML private CheckBox         chkEquipo;
    @FXML private CheckBox         chkMedia;
    @FXML private Label            lblSinDatos;
    @FXML private HBox             hboxNavVentana;
    @FXML private Label            lblRangoVentana;

    @FXML private Label            lblCardPuntosTitulo;
    @FXML private Label            lblCardPuntosValor;
    @FXML private Label            lblCardPuntosDelta;
    @FXML private Label            lblCardDiaTitulo;
    @FXML private Label            lblCardDiaValor;
    @FXML private Label            lblCardDiaDelta;

    // nombres de técnicos actualmente visibles en el gráfico
    private final Set<String>           nombresSeleccionadosTec = new LinkedHashSet<>();
    /** Técnicos con ES_ESTADISTICA=0: fuera de Promedio, Equipo, tarjetas y desplegable. */
    private final Set<String> nombresExcluidos = new LinkedHashSet<>();
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
    // Crosshair del hover sobre un vértice (guías a los ejes + fecha resaltada)
    private final java.util.List<Node> lineasGuia       = new java.util.ArrayList<>();
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
    // Tooltip del toggle de métrica cuando está deshabilitado en Día
    private final Tooltip tooltipMetrica = new Tooltip("En granularidad Día ambas métricas coinciden");

    // Todos los puntos cargados de BD (sin filtrar por checkbox)
    private List<PuntoEstadisticaPuntos> todosPuntos   = List.of();
    // Periodos únicos ordenados
    private List<String>           todosPeriodos = List.of();
    // Tamaño de la ventana visible según granularidad
    private int ventanaTamanio = 30;
    // Offset de la ventana actualmente visible (0 = más antigua)
    private int ventanaOffset = 0;

    /** Rango de la vara del Promedio (ajuste smoke 2026-09-03): fijo durante la navegación —
     *  el rango del filtro de fechas si lo hay; sin filtro, la última ventana estándar. */
    private List<String> periodosReferencia = List.of();

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
        btnValores.setVisible(com.reparaciones.Sesion.esAdmin());
        btnValores.setManaged(com.reparaciones.Sesion.esAdmin());
        btnTecnicosEstadistica.setVisible(com.reparaciones.Sesion.esAdmin());
        btnTecnicosEstadistica.setManaged(com.reparaciones.Sesion.esAdmin());

        cmbGranularidad.setItems(FXCollections.observableArrayList("Día", "Semana", "Mes", "Año"));
        cmbGranularidad.setValue("Día"); // por defecto Día (ajuste smoke 2026-09-01)
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
        chkEquipo.setSelected(false); // apagada por defecto (ajuste smoke 2026-09-01)
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

    /** Etiquetas legibles de las claves de Dificultad_puntos, en orden de mostrado. */
    private static final java.util.LinkedHashMap<String, String> ETIQUETA_CLAVE = new java.util.LinkedHashMap<>();
    static {
        ETIQUETA_CLAVE.put("pantalla", "Pantalla");
        ETIQUETA_CLAVE.put("bateria",  "Batería");
        ETIQUETA_CLAVE.put("chasis",   "Chasis");
        ETIQUETA_CLAVE.put("camara",   "Cámara");
        ETIQUETA_CLAVE.put("glass",    "Glass");
        ETIQUETA_CLAVE.put("marco",    "Marco");
        ETIQUETA_CLAVE.put("otro",     "Otro / sin piezas");
        ETIQUETA_CLAVE.put("pulido",   "Pulido");
    }

    @FXML
    private void abrirModalValores() {
        List<com.reparaciones.models.ValorDificultad> valores;
        try {
            valores = new com.reparaciones.dao.ValoresDificultadDAO().getAll();
        } catch (SQLException e) { mostrarError(e); return; }
        java.util.Map<String, Double> porClave = new java.util.HashMap<>();
        valores.forEach(v -> porClave.put(v.getClave(), v.getPuntos()));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Valores de dificultad");
        dialog.setHeaderText("Puntos por tipo de trabajo");
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(8);
        java.util.Map<String, TextField> campos = new java.util.LinkedHashMap<>();
        int fila = 0;
        for (var e : ETIQUETA_CLAVE.entrySet()) {
            if (!porClave.containsKey(e.getKey())) continue;
            grid.add(new Label(e.getValue()), 0, fila);
            TextField tf = new TextField(PuntosEstadistica.formatearPuntosEdicion(porClave.get(e.getKey())));
            tf.setPrefWidth(80);
            campos.put(e.getKey(), tf);
            grid.add(tf, 1, fila++);
        }
        Label aviso = new Label("Cambiar un valor re-valora también las estadísticas pasadas.");
        aviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A8A9A;");
        grid.add(aviso, 0, fila, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validar sin cerrar el diálogo si hay campos inválidos
        javafx.scene.control.Button ok =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            List<com.reparaciones.models.ValorDificultad> nuevos = new java.util.ArrayList<>();
            boolean hayError = false;
            for (var e : campos.entrySet()) {
                var parsed = PuntosEstadistica.parsePuntos(e.getValue().getText());
                if (parsed.isEmpty()) {
                    e.getValue().setStyle("-fx-border-color: #C62828;");
                    hayError = true;
                } else {
                    e.getValue().setStyle("");
                    nuevos.add(new com.reparaciones.models.ValorDificultad(e.getKey(), parsed.get()));
                }
            }
            if (hayError) { ev.consume(); return; }
            try {
                new com.reparaciones.dao.ValoresDificultadDAO().guardar(nuevos);
            } catch (SQLException ex) { ev.consume(); mostrarError(ex); return; }
            recargarDatos();
            cargarTarjetas();
        });
        dialog.showAndWait();
    }

    /** Modal 👥: quién cuenta en la vista de estadísticas (spec ronda 2 §4). Solo ADMIN. */
    @FXML
    private void abrirModalTecnicos() {
        List<Tecnico> tecnicos;
        try {
            tecnicos = new TecnicoDAO().getAll();
        } catch (SQLException e) { mostrarError(e); return; }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Técnicos en estadísticas");
        dialog.setHeaderText("Quién cuenta en la vista de estadísticas");
        javafx.scene.layout.VBox caja = new javafx.scene.layout.VBox(6);
        java.util.Map<Integer, CheckBox> checks = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Boolean> estadoInicial = new java.util.HashMap<>();
        List<Tecnico> orden = new java.util.ArrayList<>();
        tecnicos.stream().filter(Tecnico::isActivo).forEach(orden::add);
        tecnicos.stream().filter(t -> !t.isActivo()).forEach(orden::add);
        for (Tecnico t : orden) {
            CheckBox cb = new CheckBox(t.isActivo() ? t.getNombre() : t.getNombre() + " (inactivo)");
            cb.setSelected(t.isEsEstadistica());
            checks.put(t.getIdTec(), cb);
            estadoInicial.put(t.getIdTec(), t.isEsEstadistica());
            caja.getChildren().add(cb);
        }
        Label aviso = new Label("Los desmarcados no cuentan en Promedio, Equipo ni tarjetas.");
        aviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A8A9A;");
        caja.getChildren().add(aviso);
        dialog.getDialogPane().setContent(caja);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.control.Button ok =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                var usuarioDao = new com.reparaciones.dao.UsuarioDAO();
                for (var e : checks.entrySet()) {
                    boolean marcado = e.getValue().isSelected();
                    if (marcado == estadoInicial.get(e.getKey())) continue; // solo cambios
                    if (marcado) usuarioDao.incluirEstadisticas(e.getKey());
                    else         usuarioDao.excluirEstadisticas(e.getKey());
                }
            } catch (SQLException ex) { ev.consume(); mostrarError(ex); return; }
            cargarTecnicos(); // re-entrante: reconstruye desplegable y nombresExcluidos
            nombresSeleccionadosTec.removeAll(nombresExcluidos);
            if (filtroTecHandle != null) filtroTecHandle.refresh();
            actualizarTextoMenuTecnicos();
            renderVentana(ventanaOffset);
            cargarTarjetas();
        });
        dialog.showAndWait();
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

        // Re-entrante: el modal 👥 lo re-llama tras guardar exclusiones
        todosLosTecnicos.clear();
        nombresExcluidos.clear();
        tecnicos.stream().filter(t -> !t.isEsEstadistica())
                .map(Tecnico::getNombre).forEach(nombresExcluidos::add);

        Integer idTecSesion = com.reparaciones.Sesion.getIdTec();
        if (idTecSesion != null)
            nombreTecnicoSesion = tecnicos.stream()
                    .filter(t -> t.getIdTec() == idTecSesion)
                    .map(Tecnico::getNombre).findFirst().orElse(null);

        // Colores para TODOS (un excluido sigue viendo su propia serie con su color)
        for (Tecnico t : tecnicos)
            coloresPorNombre.put(t.getNombre(), generarColor(t.getIdTec()));

        // El desplegable solo lista a los que cuentan en estadísticas
        List<Tecnico> activos   = tecnicos.stream()
                .filter(Tecnico::isActivo).filter(Tecnico::isEsEstadistica)
                .collect(Collectors.toList());
        List<Tecnico> inactivos = tecnicos.stream()
                .filter(t -> !t.isActivo()).filter(Tecnico::isEsEstadistica)
                .collect(Collectors.toList());

        todosLosTecnicos.addAll(activos);
        if (!inactivos.isEmpty()) {
            todosLosTecnicos.add(null); // separador
            todosLosTecnicos.addAll(inactivos);
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
        // En Día ambas métricas coinciden: toggle gris, selección conservada (spec ronda 2 §3)
        boolean esDia = "Día".equals(cmbGranularidad.getValue());
        rbPuntos.setDisable(esDia);
        rbPuntosDia.setDisable(esDia);
        if (esDia) Tooltip.install(boxMetrica, tooltipMetrica);
        else       Tooltip.uninstall(boxMetrica, tooltipMetrica);

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

        // Sin flechas de navegación (ajuste smoke 2026-09-04): lo mostrado ES el rango
        // elegido. Con filtro de fechas se pinta el rango filtrado ENTERO (para rangos
        // largos en Día, la lectura cómoda es subir la granularidad); sin filtro, la
        // última ventana estándar de la granularidad (los 30 días / 16 sem... recientes).
        boolean hayFiltroFechas = dpDesde.getValue() != null || dpHasta.getValue() != null;
        if (!todosPeriodos.isEmpty())
            ventanaTamanio = hayFiltroFechas ? todosPeriodos.size()
                                             : Math.min(ventanaTamanio, todosPeriodos.size());

        // La vara (Promedio, x̄, Por encima/Por debajo) es SIEMPRE la del rango mostrado.
        periodosReferencia = List.copyOf(todosPeriodos.subList(
                Math.max(0, todosPeriodos.size() - ventanaTamanio), todosPeriodos.size()));

        configurarVentana();
    }

    /** Muestra el rango elegido (el tramo más reciente disponible; con filtro, entero). */
    private void configurarVentana() {
        hboxNavVentana.setVisible(true);
        hboxNavVentana.setManaged(true);
        ventanaOffset = Math.max(0, todosPeriodos.size() - ventanaTamanio);
        renderVentana(ventanaOffset);
    }

    /** Renderiza la ventana de periodos que empieza en `offset`. */
    private void renderVentana(int offset) {
        ventanaOffset = offset;
        ocultarGuias(); // que un re-render no deje guías de crosshair huérfanas
        if (todosPeriodos.isEmpty()) {
            chartReparaciones.getData().clear();
            lblSinDatos.setVisible(true);
            lblRangoVentana.setText("");
            return;
        }
        lblSinDatos.setVisible(false);

        int tamanio = Math.min(ventanaTamanio, todosPeriodos.size());
        int inicio  = Math.max(0, Math.min(offset, todosPeriodos.size() - tamanio));
        int fin     = inicio + tamanio;

        Set<String> periodosVisibles = new LinkedHashSet<>(todosPeriodos.subList(inicio, fin));

        lblRangoVentana.setText(PuntosEstadistica.etiquetaVentana(tamanio, cmbGranularidad.getValue())
                + " · " + todosPeriodos.get(inicio) + " — " + todosPeriodos.get(fin - 1));

        Set<String> seleccionados = new LinkedHashSet<>(nombresSeleccionadosTec);
        List<PuntoEstadisticaPuntos> puntosEquipo =
                PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos);

        // Valores visibles por técnico. Cada serie se construye recorriendo periodosVisibles
        // EN ORDEN y rellenando con 0 los periodos sin actividad (vacaciones, ausencias):
        // así el eje de categorías queda siempre ordenado (sin esto, un técnico con hueco
        // colaba sus fechas de vuelta en medio del eje) y un día sin trabajo se lee como 0,
        // igual que en la serie Equipo. (El Promedio NO cuenta los huecos: mide el ritmo
        // por periodo trabajado — ajuste smoke 2026-09-03.)
        Map<String, Map<String, Double>> valorPorTecnico = new LinkedHashMap<>();
        for (PuntoEstadisticaPuntos p : todosPuntos) {
            if (!seleccionados.contains(p.getNombreTecnico())) continue;
            if (!periodosVisibles.contains(p.getPeriodo()))    continue;
            valorPorTecnico.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                           .put(p.getPeriodo(), valorDe(p));
        }
        Map<String, XYChart.Series<String, Number>> series = new LinkedHashMap<>();
        for (String nombre : seleccionados) {
            Map<String, Double> porPeriodo = valorPorTecnico.get(nombre);
            if (porPeriodo == null) continue; // sin actividad en la ventana: no se pinta
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(nombre);
            for (String periodo : periodosVisibles)
                s.getData().add(new XYChart.Data<>(periodo, porPeriodo.getOrDefault(periodo, 0.0)));
            series.put(nombre, s);
        }

        // Serie "Equipo": suma de TODOS los técnicos por periodo (independiente de checkboxes)
        List<XYChart.Series<String, Number>> listaFinal = new java.util.ArrayList<>(series.values());
        if (chkEquipo.isSelected()) {
            Map<String, Double> sumaPorPeriodo = new java.util.LinkedHashMap<>();
            for (String p : periodosVisibles) sumaPorPeriodo.put(p, 0.0);
            for (PuntoEstadisticaPuntos p : puntosEquipo) {
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
            for (PuntoEstadisticaPuntos p : puntosEquipo) {
                if (periodosVisibles.contains(p.getPeriodo()))
                    sumaPorPeriodo.merge(p.getPeriodo(), valorDe(p), Double::sum);
            }
            double maxEquipo = sumaPorPeriodo.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            maxVisible = Math.max(maxVisible, maxEquipo);
        }
        // La línea de Promedio también debe caber dentro del eje, no solo las series visibles
        double promedioRef = promedioVentanaActual(periodosReferencia);
        ejeY.setUpperBound(Math.max(5, Math.ceil(Math.max(maxVisible, promedioRef)) + 1));
        ejeY.setLabel(metricaPorDia() ? "Puntos/día" : "Puntos");

        // IMEIs distintos del último periodo visible, para el sufijo de la leyenda
        // (campo aditivo del servidor; con un servidor sin él no se muestra sufijo)
        String ultimoPeriodo = todosPeriodos.get(fin - 1);
        boolean servidorConImeis = todosPuntos.stream().anyMatch(p -> p.getnImeis() != null);
        Map<String, Integer> imeisUltimoPeriodo = new java.util.HashMap<>();
        for (PuntoEstadisticaPuntos p : todosPuntos)
            if (ultimoPeriodo.equals(p.getPeriodo()) && p.getnImeis() != null)
                imeisUltimoPeriodo.put(p.getNombreTecnico(), p.getnImeis());

        // Referencia del chip: IMEIs típicos por técnico-periodo trabajado del rango de
        // referencia (misma fórmula que el Promedio de puntos, sin excluidos). El chip
        // queda "javi · 12/13 IMEIs" y en verde al alcanzarla.
        double mediaImeisTmp = 0;
        if (servidorConImeis) {
            Map<String, Map<String, Double>> datosImeis = new LinkedHashMap<>();
            for (PuntoEstadisticaPuntos p : PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos))
                if (p.getnImeis() != null)
                    datosImeis.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                              .put(p.getPeriodo(), p.getnImeis().doubleValue());
            mediaImeisTmp = PuntosEstadistica.promedioVentana(
                    datosImeis, new java.util.ArrayList<>(periodosReferencia));
        }
        final double mediaImeis = mediaImeisTmp;

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

            // Leyenda: clic para quitar técnico (solo ADMIN/SUPERTECNICO) y sufijo con los
            // IMEIs del último periodo visible (hoy / esta semana / este mes — ajuste smoke
            // 2026-09-04). El nombre se captura ANTES de tocar el texto: el clic y los
            // colores (que corren antes, en aplicarColores) siguen viendo el nombre limpio.
            boolean puedeQuitar = com.reparaciones.Sesion.esAdminOSuperTecnico();
            for (javafx.scene.Node item : chartReparaciones.lookupAll(".chart-legend-item")) {
                if (!(item instanceof Label lbl)) continue;
                String nombre = lbl.getText();
                boolean esTecnico = !"Equipo".equals(nombre);
                if (puedeQuitar) {
                    lbl.setStyle(esTecnico ? "-fx-cursor: hand;" : "");
                    lbl.setOnMouseClicked(e -> {
                        if (!esTecnico) return;
                        nombresSeleccionadosTec.remove(nombre);
                        if (filtroTecHandle != null) filtroTecHandle.refresh();
                        actualizarTextoMenuTecnicos();
                        renderVentana(ventanaOffset);
                    });
                }
                if (esTecnico && servidorConImeis) {
                    int lleva = imeisUltimoPeriodo.getOrDefault(nombre, 0);
                    if (mediaImeis > 0) {
                        lbl.setText(nombre + " · " + lleva + "/" + Math.round(mediaImeis) + " IMEIs");
                        if (lleva >= mediaImeis)
                            lbl.setStyle(lbl.getStyle() + "-fx-text-fill: #2E7D32;");
                    } else {
                        lbl.setText(nombre + " · " + lleva + " IMEIs");
                    }
                }
            }
        };
        if (chartReparaciones.getScene() != null) render.run();
        else Platform.runLater(render);
    }

    /** Ámbito de las varas (Promedio, x̄ y Por encima/Por debajo): filtro de fechas o última ventana. */
    private String ambitoReferencia() {
        return (dpDesde.getValue() != null || dpHasta.getValue() != null)
                ? "rango filtrado"
                : PuntosEstadistica.etiquetaVentana(periodosReferencia.size(), cmbGranularidad.getValue());
    }

    /** Promedio por periodo TRABAJADO del rango dado (técnicos que cuentan, métrica activa). */
    private double promedioVentanaActual(java.util.Collection<String> periodos) {
        Map<String, Map<String, Double>> datosVentana = new LinkedHashMap<>();
        for (PuntoEstadisticaPuntos p : PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)) {
            datosVentana.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                        .put(p.getPeriodo(), valorDe(p));
        }
        return PuntosEstadistica.promedioVentana(datosVentana, new java.util.ArrayList<>(periodos));
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

        // Precomputar suma total por periodo (reutilizado en "Equipo"): excluye a los
        // técnicos con ES_ESTADISTICA=0, igual que la propia serie "Equipo" del gráfico.
        // Sobre el RANGO DE REFERENCIA, como el Promedio y el Por encima/Por debajo:
        // las x̄ no bailan al navegar (ajuste smoke 2026-09-04).
        Map<String, Double> sumaPorPeriodo = new java.util.HashMap<>();
        for (PuntoEstadisticaPuntos p : PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)) {
            if (periodosReferencia.contains(p.getPeriodo()))
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
                                  && periodosReferencia.contains(p.getPeriodo()))
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
            Tooltip tipMedia = new Tooltip("Media " + serie.getName() + " (" + ambitoReferencia() + "): "
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

        // Línea "Promedio": vara FIJA sobre el rango de referencia (filtro de fechas, o la
        // última ventana estándar sin filtro) — no se mueve al navegar con las flechas
        // (ajuste smoke 2026-09-03). Nunca incluye la serie "Equipo".
        double refMedia = promedioVentanaActual(periodosReferencia);

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
            // Mismo rango de referencia que la línea: quién supera la vara, estable al navegar
            List<String> porEncima = todosLosTecnicos.stream().filter(t -> t != null).map(Tecnico::getNombre)
                    .filter(nombre -> {
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && periodosReferencia.contains(p.getPeriodo()))
                                .mapToDouble(this::valorDe)
                                .average().orElse(0);
                        return media > refMedia;
                    }).collect(Collectors.toList());
            List<String> porDebajo = todosLosTecnicos.stream().filter(t -> t != null).map(Tecnico::getNombre)
                    .filter(nombre -> {
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && periodosReferencia.contains(p.getPeriodo()))
                                .mapToDouble(this::valorDe)
                                .average().orElse(0);
                        return media > 0 && media <= refMedia;
                    }).collect(Collectors.toList());

            String encimaTxt = porEncima.isEmpty() ? "—" : String.join(", ", porEncima);
            String debajTxt  = porDebajo.isEmpty() ? "—" : String.join(", ", porDebajo);
            String unidadRef = metricaPorDia() ? "puntos/día" : "puntos";
            String ambitoRef = ambitoReferencia();
            Tooltip tipRef = new Tooltip(String.format(
                    "Promedio del equipo (%s): %s %s%n" +
                    "Por encima: %s%n" +
                    "Por debajo: %s",
                    ambitoRef, PuntosEstadistica.formatearPuntos(refMedia), unidadRef, encimaTxt, debajTxt));
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
        boolean visible = !chkMedia.isSelected(); // "Ocultar medias": marcado = ocultas
        lineasMedia.forEach(n -> n.setVisible(visible));
    }

    /**
     * Nº de trabajos (normales + glass + pulidos) de un técnico (o "Equipo") en un periodo.
     * Para "Equipo" se excluye a los técnicos con ES_ESTADISTICA=0 (exclusión total de la
     * vista); un técnico individual siempre cuenta sus propios trabajos, esté excluido o no.
     */
    private int trabajosDe(String tecnico, String periodo) {
        boolean esEquipo = "Equipo".equals(tecnico);
        List<PuntoEstadisticaPuntos> base = esEquipo
                ? PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)
                : todosPuntos;
        return base.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && (esEquipo || p.getNombreTecnico().equals(tecnico)))
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
                    mostrarGuias(nodo, d.getXValue(), color);
                });
                nodo.setOnMouseExited(e -> {
                    nodo.setStyle(puntosVisibles
                            ? "-fx-background-color: " + color + ", white;"
                            : "-fx-background-color: transparent, transparent;");
                    ocultarGuias();
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
                            tooltipMedia.setText("Media " + cercana.getName() + " (" + ambitoReferencia() + "): "
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

    /**
     * Popover con el desglose del punto y salto opcional al Historial (spec §4). Para "Equipo"
     * se excluye a los técnicos con ES_ESTADISTICA=0, igual que el resto de agregados de Equipo.
     */
    private void mostrarPopoverDesglose(javafx.scene.Node ancla, String nombreSerie, String periodo) {
        boolean esEquipo = "Equipo".equals(nombreSerie);
        List<PuntoEstadisticaPuntos> base = esEquipo
                ? PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)
                : todosPuntos;
        PuntoEstadisticaPuntos datos = base.stream()
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
            navegacion.navegarAReparaciones(rango[0], rango[1], esEquipo ? null : nombreSerie, false);
        });
        Button verImeis = new Button("Ver IMEIs");
        verImeis.getStyleClass().add("btn-secondary");
        verImeis.setOnAction(ev -> {
            popup.hide();
            java.time.LocalDate[] rango =
                    PuntosEstadistica.periodoAFechas(periodo, cmbGranularidad.getValue());
            navegacion.navegarAReparaciones(rango[0], rango[1], esEquipo ? null : nombreSerie, true);
        });
        HBox botones = new HBox(8, verImeis, verHistorial);

        VBox caja = new VBox(6, titulo, cuerpo, botones);
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
        chkEquipo.setSelected(false);
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
        PREFIJO_TIPO.put("lcd", "Pantalla");
        PREFIJO_TIPO.put("mc",  "Marco");
        PREFIJO_TIPO.put("g",   "Glass");
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
            // Granularidad DÍA: la tarjeta Puntos/día necesita las medias por día de
            // semana del mes anterior para igualar la mezcla de días (ajuste 2026-09-03)
            filas = new ReparacionDAO().getEstadisticasPuntos("dia",
                    mes.minusMonths(1).atDay(1), mes.atEndOfMonth());
        } catch (SQLException e) { mostrarError(e); return; }
        String tecnico = com.reparaciones.Sesion.esAdminOSuperTecnico() ? null : nombreTecnicoSesion;
        var t = PuntosEstadistica.calcularTarjetas(
                filas, mes, java.time.LocalDate.now(), tecnico, nombresExcluidos);
        String quien = tecnico == null ? "equipo" : "tú";
        lblCardPuntosTitulo.setText("Puntos · " + t.mesLabel() + " · " + quien);
        lblCardPuntosValor.setText(PuntosEstadistica.formatearPuntos(t.puntos()));
        pintarObjetivo(lblCardPuntosDelta, t.pctPuntos(), t.mesAnteriorLabel(), t.puntosAnterior());
        lblCardDiaTitulo.setText("Puntos · hoy, " + t.diaHoyLabel() + " · " + quien);
        lblCardDiaValor.setText(PuntosEstadistica.formatearPuntos(t.puntosHoy()));
        pintarObjetivo(lblCardDiaDelta, t.pctHoy(),
                "un " + t.diaHoyLabel() + " de " + t.mesAnteriorLabel(), t.objetivoHoy());
    }

    /** Línea de objetivo: "46% de agosto (890,0)" — gris hasta el 100%, verde al alcanzarlo. Nunca rojo. */
    private void pintarObjetivo(Label lbl, Integer pct, String mesAnterior, Double valorAnterior) {
        if (pct == null) { lbl.setText(""); lbl.setStyle(""); return; }
        lbl.setText(PuntosEstadistica.textoObjetivo(pct, mesAnterior, valorAnterior));
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (pct >= 100 ? "#2E7D32" : "#7A8A9A") + ";");
    }

    /**
     * Crosshair del hover sobre un vértice: guías punteadas del punto a ambos ejes
     * y la fecha correspondiente del eje X en negrita con el color de la serie,
     * para leer las coordenadas de un vistazo (ajuste smoke 2026-09-02).
     */
    private void mostrarGuias(Node nodo, String periodo, String color) {
        ocultarGuias();
        Node bg = chartReparaciones.lookup(".chart-plot-background");
        if (bg == null || !(bg.getParent() instanceof javafx.scene.layout.Pane plotArea)) return;

        javafx.geometry.Bounds nb = plotArea.sceneToLocal(nodo.localToScene(nodo.getBoundsInLocal()));
        double cx = (nb.getMinX() + nb.getMaxX()) / 2;
        double cy = (nb.getMinY() + nb.getMaxY()) / 2;
        javafx.geometry.Bounds pb = bg.getBoundsInParent();

        javafx.scene.shape.Line vertical   = new javafx.scene.shape.Line(cx, cy, cx, pb.getMaxY());
        javafx.scene.shape.Line horizontal = new javafx.scene.shape.Line(pb.getMinX(), cy, cx, cy);
        for (javafx.scene.shape.Line guia : java.util.List.of(vertical, horizontal)) {
            guia.setStroke(javafx.scene.paint.Color.web(color));
            guia.setStrokeWidth(1);
            guia.getStrokeDashArray().addAll(4.0, 4.0);
            guia.setOpacity(0.7);
            guia.setMouseTransparent(true);
            plotArea.getChildren().add(guia);
            lineasGuia.add(guia);
        }

        // Fecha del eje X resaltada (los tick labels del eje son nodos Text con el texto del periodo)
        for (Node n : ejeX.getChildrenUnmodifiable()) {
            if (n instanceof javafx.scene.text.Text t && periodo.equals(t.getText())) {
                t.getProperties().put("guia-fill", t.getFill());
                t.setFill(javafx.scene.paint.Color.web(color));
                t.setStyle("-fx-font-weight: bold;");
                lineasGuia.add(t);
                break;
            }
        }
    }

    /** Retira las guías del crosshair y restaura el estilo del tick label del eje X. */
    private void ocultarGuias() {
        for (Node n : lineasGuia) {
            if (n instanceof javafx.scene.text.Text t) {
                Object fill = t.getProperties().remove("guia-fill");
                if (fill instanceof javafx.scene.paint.Paint p) t.setFill(p);
                t.setStyle("");
            } else if (n.getParent() instanceof javafx.scene.layout.Pane p) {
                p.getChildren().remove(n);
            }
        }
        lineasGuia.clear();
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
