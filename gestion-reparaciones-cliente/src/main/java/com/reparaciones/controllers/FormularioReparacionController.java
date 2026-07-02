package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ComponenteDAO;
import com.reparaciones.dao.ReparacionDAO;
import com.reparaciones.dao.TecnicoDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.Componente;
import com.reparaciones.models.FilaReparacion;
import com.reparaciones.models.Tecnico;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;

import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.StaleDataException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controlador del formulario de finalización de reparación.
 * <p>Abierto desde {@link PendientesTecnicoController} al pulsar el botón de finalizar
 * de una asignación ({@code A*}). Permite al técnico seleccionar el componente utilizado,
 * indicar si es reutilizado, añadir observaciones y solicitar componentes adicionales.</p>
 *
 * <p>Agrupa los componentes en filas por tipo (batería, pantalla, etc.) y marca en verde
 * los tipos ya reparados en el mismo IMEI para evitar duplicados.</p>
 *
 * <p>Al guardar, llama a {@link com.reparaciones.dao.ReparacionDAO#insertarCompleta}
 * en transacción: crea los registros {@code R*} para las filas normales, guarda las
 * solicitudes sobre la {@code A*} y descuenta el stock.</p>
 *
 * @role TECNICO; ADMIN (si tiene técnico asignado)
 */
public class FormularioReparacionController {

    @FXML private Label lblImei;
    @FXML private Label lblIncidencia;
    @FXML private javafx.scene.layout.HBox filaIncidencia;
    @FXML private javafx.scene.layout.HBox filaConflictoTecnico;
    @FXML private Label lblConflictoTecnico;
    @FXML private Label lblSeleccionaModelo;
    @FXML private VBox contenedorFilas;
    @FXML private VBox contenedorOtros;
    @FXML private javafx.scene.layout.HBox cabeceraColumnas;
    @FXML private Button btnGuardar;
    @FXML private javafx.scene.layout.HBox zonaGuardar;
    @FXML private ComboBox<String> cbFiltroModelo;
    private boolean tieneSolicitudesIniciales = false;
    private List<FilaReparacion> solicitudesCargadas = null;
    private boolean       modoEdicion = false;
    private String        idRepEditar;
    private String        imeiEditar;
    private int           idTecEditar;
    private LocalDateTime updatedAtEdicion;
    private TextArea taEditarOtro;
    private int idComOtroEditar = -1;
    private boolean esperandoConfirmacion = false;

    private final ReparacionDAO reparacionDAO = new ReparacionDAO();
    private final ComponenteDAO componenteDAO = new ComponenteDAO();

    private String imei;
    private String idRepAnterior;
    private String idAsignacion;
    private Runnable onGuardado;

    private final List<FilaUI> filasUI = new ArrayList<>();
    private OtrasAccionesUI otrasAcciones;

    // ── Borrador persistente (solo flujo nuevo) ──
    private final com.reparaciones.dao.BorradorDAO borradorDAO = new com.reparaciones.dao.BorradorDAO();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private javafx.animation.PauseTransition autoGuardado;   // debounce
    private boolean recuperandoBorrador = false;             // evita auto-guardar mientras se aplica el borrador
    private boolean borradorDescartado = false;              // tras guardar de verdad: no re-crear el borrador

    /**
     * Lista de modelos en orden de tienda Apple.
     * Para añadir una serie futura (ej. iPhone 18): agregar sus códigos al final.
     * Los numéricos (18, 18pro, 18promax...) los traduce traducirModelo automáticamente.
     * OJO: un modelo SIN número (como "air") necesita además su propio caso en traducirModelo.
     */
    static final List<String> MODELOS_ORDENADOS = List.of(
            "6s", "6splus", "7", "7plus", "8", "8plus", "se2020",
            "x", "xr", "xs", "xsmax",
            "11", "11pro", "11promax",
            "12", "12mini", "12pro", "12promax",
            "13", "13mini", "13pro", "13promax",
            "14", "14plus", "14pro", "14promax",
            "15", "15plus", "15pro", "15promax",
            "16", "16e", "16plus", "16pro", "16promax",
            "17", "air", "17pro", "17promax"
    );

    public void init(String imei, String idRepAnterior, String idAsignacion, Runnable onGuardado) {
        this.imei = imei;
        this.idAsignacion = idAsignacion;
        this.onGuardado = onGuardado;

        this.idRepAnterior = idRepAnterior;
        if (this.idRepAnterior == null) {
            try {
                boolean esGlass = idAsignacion != null && idAsignacion.startsWith("AG");
                this.idRepAnterior = reparacionDAO.getIncidenciaActivaPorImei(imei, esGlass ? "G" : "R");
            } catch (SQLException e) {
                mostrarError(e);
            }
        }

        lblImei.setText("IMEI: " + imei);

        // Aviso si el mismo IMEI está asignado activamente en cualquier categoría
        // (reparación/glass/pulido), excluyendo la asignación en edición. Agrupado por
        // categoría y marcando "(tú)" si es el técnico logueado.
        try {
            Integer idTecActual = Sesion.getIdTec();
            List<com.reparaciones.models.AsignacionActiva> otras =
                    reparacionDAO.getAsignacionesActivasPorImei(imei).stream()
                            .filter(a -> !a.getIdRep().equals(idAsignacion))
                            .collect(Collectors.toList());
            if (!otras.isEmpty()) {
                java.util.Map<String, java.util.List<String>> porCat = new java.util.LinkedHashMap<>();
                porCat.put("Reparación", new java.util.ArrayList<>());
                porCat.put("Glass",      new java.util.ArrayList<>());
                porCat.put("Pulido",     new java.util.ArrayList<>());
                for (com.reparaciones.models.AsignacionActiva a : otras) {
                    String cat = a.getIdRep().startsWith("AG") ? "Glass"
                               : a.getIdRep().startsWith("AP") ? "Pulido"
                               :                                  "Reparación";
                    String nombre = a.getNombreTecnico()
                            + (idTecActual != null && a.getIdTec() == idTecActual ? " (tú)" : "");
                    porCat.get(cat).add(nombre);
                }
                String msg = porCat.entrySet().stream()
                        .filter(e -> !e.getValue().isEmpty())
                        .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                        .collect(Collectors.joining(" · "));
                lblConflictoTecnico.setText("⚠ Este IMEI también está asignado a — " + msg);
                filaConflictoTecnico.setVisible(true);
                filaConflictoTecnico.setManaged(true);
            }
        } catch (SQLException e) {
            // no crítico: el modal sigue funcionando aunque falle esta consulta
        }

        if (this.idRepAnterior != null) {
            lblIncidencia.setText("⚠ Resuelve incidencia: " + this.idRepAnterior);
            filaIncidencia.setVisible(true);
            filaIncidencia.setManaged(true);
        }

        cargarFilas();
        if (idAsignacion != null) {
            try {
                List<FilaReparacion> solicitudes = reparacionDAO.getSolicitudesPorAsignacion(idAsignacion);
                if (!solicitudes.isEmpty()) {
                    solicitudesCargadas = solicitudes; // todas (incluidas RECHAZADAS) para detección de modelo
                    boolean hayActivas = solicitudes.stream()
                            .anyMatch(s -> !"RECHAZADA".equals(s.getEstadoSolicitud()));
                    tieneSolicitudesIniciales = hayActivas;
                    // Primera pasada: activar solo las no-rechazadas
                    for (FilaReparacion sol : solicitudes) {
                        if ("RECHAZADA".equals(sol.getEstadoSolicitud())) continue;
                        for (FilaUI fila : filasUI)
                            fila.activarSolicitud(sol.getIdCom(), sol.getDescripcionSolicitud(),
                                    sol.getEstadoSolicitud(), sol.isEnCamino());
                    }
                }
            } catch (SQLException e) {
                mostrarError(e);
            }
        }
        configurarFiltroModelo(); // el auto-filtro de modelo dispara aplicarFiltroModelo → resetea solicitudActiva
        // Segunda pasada: re-activar solo las no-rechazadas después del reset del filtro
        if (solicitudesCargadas != null) {
            for (FilaReparacion sol : solicitudesCargadas) {
                if ("RECHAZADA".equals(sol.getEstadoSolicitud())) continue;
                for (FilaUI fila : filasUI)
                    fila.activarSolicitud(sol.getIdCom(), sol.getDescripcionSolicitud(),
                            sol.getEstadoSolicitud(), sol.isEnCamino());
            }
        }
        // Tercera pasada: solicitudes RECHAZADAS — preseleccionar el SKU concreto para que
        // el indicador de stock muestre el valor correcto (0) en lugar del primer SKU con stock.
        if (solicitudesCargadas != null) {
            for (FilaReparacion sol : solicitudesCargadas) {
                if (!"RECHAZADA".equals(sol.getEstadoSolicitud())) continue;
                for (FilaUI fila : filasUI)
                    fila.preseleccionarSku(sol.getIdCom());
            }
        }
        // Auto-completar modelo desde BD si no se detectó automáticamente por componentes
        if (cbFiltroModelo.getValue() == null && !modoEdicion) {
            try {
                String modeloBD = new TelefonoDAO().getModelo(imei);
                if (modeloBD != null && !modeloBD.isEmpty()
                        && cbFiltroModelo.getItems().contains(modeloBD)) {
                    cbFiltroModelo.setValue(modeloBD);
                    cbFiltroModelo.setDisable(true);
                }
            } catch (SQLException e) { /* silencioso */ }
        }

        // ── Recuperar borrador (solo flujo nuevo) ──
        // Se aplica DESPUÉS del estado de BD; solo sobre filas no bloqueadas por solicitud (aplicar válido, ignorar inválido).
        if (idAsignacion != null && !modoEdicion) {
            try {
                String json = borradorDAO.getBorrador(idAsignacion);
                if (json != null && !json.isBlank()) {
                    com.reparaciones.models.BorradorContenido b =
                            gson.fromJson(json, com.reparaciones.models.BorradorContenido.class);
                    if (b != null && !borradorVacio(b)) {
                        recuperandoBorrador = true;
                        try {
                            if (b.modelo != null && cbFiltroModelo.getItems().contains(b.modelo)
                                    && !cbFiltroModelo.isDisable()) {
                                cbFiltroModelo.setValue(b.modelo);
                            }
                            for (com.reparaciones.models.BorradorContenido.Fila f : b.filas) {
                                filasUI.stream()
                                        .filter(fila -> fila.getPrefijo().equals(f.prefijo))
                                        .findFirst()
                                        .ifPresent(fila -> fila.aplicarBorrador(f));
                            }
                            if (otrasAcciones != null) otrasAcciones.aplicarAcciones(b.otros);
                        } finally {
                            recuperandoBorrador = false;
                        }
                        verificarFilasGuardadasBorradas(b);
                        actualizarBoton();
                        mostrarIndicadorBorrador();
                    }
                }
            } catch (Exception ex) {
                // borrador corrupto/no disponible: se ignora, el modal abre normal
            }
        }
    }

    private void mostrarIndicadorBorrador() {
        Label aviso = new Label("✓ Borrador recuperado");
        aviso.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0; -fx-font-size: 11px;"
                + " -fx-font-weight: bold; -fx-padding: 4 16 4 16;");
        aviso.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.Parent parent = filaIncidencia.getParent();
        if (parent instanceof javafx.scene.layout.VBox vb) {
            int idx = vb.getChildren().indexOf(filaIncidencia);
            vb.getChildren().add(idx + 1, aviso);
        }
    }

    public void initEditar(String idRep, Runnable onGuardado) {
        this.modoEdicion  = true;
        this.idRepEditar  = idRep;
        this.onGuardado   = onGuardado;

        try {
            ReparacionDAO.DetalleEdicion d = reparacionDAO.getDetalleEdicion(idRep);
            if (d == null) return;
            this.imeiEditar      = d.imei;
            this.idTecEditar     = d.idTec;
            this.updatedAtEdicion = d.updatedAt;
            cargarFilas(); // crea otrasAcciones (necesario para detectar si idCom es "otro")
            for (FilaUI f : filasUI) f.setFormularioEnEdicion(true);   // edición: ninguna fila muestra "Guardar fila"
            if (otrasAcciones != null && otrasAcciones.esOtro(d.idCom)) {
                iniciarEdicionOtro(d);
                return;
            }

            lblImei.setText("IMEI: " + d.imei + "  ·  Editando " + idRep);
            btnGuardar.setText("Guardar cambios");

            List<Tecnico> tecnicos = new TecnicoDAO().getAllActivos();

            Set<Integer> yaReparados = reparacionDAO.getIdComsYaReparados(d.imei, idRep);
            // Primera pasada: activar solo la fila que tiene el componente editado,
            // para que configurarFiltroModelo pueda detectar el modelo
            for (FilaUI fila : filasUI)
                if (fila.getSkus().stream().anyMatch(c -> c.getIdCom() == d.idCom))
                    fila.activarModoEdicion(d.idCom, d.esReutilizado, d.observacion, d.cantidad);
            configurarFiltroModelo();
            // Segunda pasada: re-aplicar tras el reset del filtro de modelo
            for (FilaUI fila : filasUI) {
                if (fila.getSkus().stream().anyMatch(c -> c.getIdCom() == d.idCom))
                    fila.activarModoEdicion(d.idCom, d.esReutilizado, d.observacion, d.cantidad);
                else if (!fila.isModoEdicion() && fila.getSkus().stream()
                        .anyMatch(c -> yaReparados.contains(c.getIdCom())))
                    fila.deshabilitarYaReparado();
                else if (!fila.isModoEdicion())
                    fila.configurarTecnicoEdicion(tecnicos, d.idTec);
            }
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    /** Modo edición de una acción "otro": oculta la maquinaria de componentes y muestra
     *  un editor de texto para la descripción. */
    private void iniciarEdicionOtro(ReparacionDAO.DetalleEdicion d) {
        this.idComOtroEditar = d.idCom;
        lblImei.setText("IMEI: " + d.imei + "  ·  Editando acción " + idRepEditar);
        btnGuardar.setText("Guardar cambios");

        contenedorFilas.setVisible(false); contenedorFilas.setManaged(false);
        if (cabeceraColumnas != null) { cabeceraColumnas.setVisible(false); cabeceraColumnas.setManaged(false); }
        if (otrasAcciones != null) { otrasAcciones.getRoot().setVisible(false); otrasAcciones.getRoot().setManaged(false); }
        lblSeleccionaModelo.setVisible(false); lblSeleccionaModelo.setManaged(false);
        cbFiltroModelo.setVisible(false); cbFiltroModelo.setManaged(false);

        String original = d.observacion != null ? d.observacion : "";
        taEditarOtro = new TextArea(original);
        taEditarOtro.setWrapText(true);
        taEditarOtro.setPrefRowCount(3);
        taEditarOtro.setStyle("-fx-font-size: 13px;");
        Label lbl = new Label("Descripción de la acción:");
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        VBox box = new VBox(8, lbl, taEditarOtro);
        box.setStyle("-fx-padding: 16;");
        contenedorOtros.getChildren().add(box);

        taEditarOtro.textProperty().addListener((o, a, b) -> {
            String t = b == null ? "" : b.trim();
            boolean valido = !t.isEmpty() && !t.equals(original.trim());
            zonaGuardar.setVisible(valido); zonaGuardar.setManaged(valido);
        });
    }

    public static void abrirEditar(String idRep, Runnable onGuardado) {
        Platform.runLater(() -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        FormularioReparacionController.class.getResource(
                                "/views/FormularioReparacionView.fxml"));
                javafx.scene.Parent root = loader.load();
                FormularioReparacionController ctrl = loader.getController();

                Stage stage = new Stage();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setResizable(true);
                stage.setMinWidth(960);
                stage.setMinHeight(700);

                ctrl.initEditar(idRep, onGuardado);
                stage.setTitle("Editar reparación — " + idRep);
                stage.setOnCloseRequest(ev -> {
                    if (!ctrl.hayCambiosSinGuardar()) return;
                    ev.consume();
                    com.reparaciones.utils.ConfirmDialog.mostrar(
                        "Salir sin guardar",
                        "Tienes cambios sin guardar que se perderán si cierras el formulario.",
                        "Salir sin guardar",
                        "Cancelar",
                        stage::close);
                });
                stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                stage.show();
            } catch (Exception e) {
                Alertas.mostrarError(e.getMessage());
            }
        });
    }

    private void cargarFilas() {
        try {
            // Glass ≈ reparación, pero el modal filtra qué filas ofrece según el tipo:
            //  - Glass    (idAsignacion AG.. / idRepEditar G..): solo Glass, Marco y Otro.
            //  - Reparación (A.. / R..):                          todo menos Glass y Marco (Otro se mantiene).
            boolean esGlass = (idAsignacion != null && idAsignacion.startsWith("AG"))
                           || (idRepEditar  != null && idRepEditar.startsWith("G"));
            Map<String, List<Componente>> grupos = componenteDAO.getAgrupadosPorTipo();
            Image imgBorrar  = new Image(getClass().getResourceAsStream("/images/borrar.png"));
            Image imgEditar  = new Image(getClass().getResourceAsStream("/images/editar.png"));
            java.util.List<FilaUI> vidrioMarco = new ArrayList<>();
            for (Map.Entry<String, List<Componente>> entry : grupos.entrySet()) {
                if (entry.getValue().isEmpty())
                    continue;
                String key = entry.getKey();
                boolean esVidrioMarco = key.equals("g") || key.equals("mc");
                // Filtrado por tipo de trabajo
                if (esGlass) { if (!esVidrioMarco && !key.equals("otro")) continue; }
                else         { if (esVidrioMarco) continue; }
                if (key.equals("otro")) {
                    otrasAcciones = new OtrasAccionesUI(entry.getValue(), imgBorrar);
                    otrasAcciones.setOnCambio(this::actualizarBoton);
                    otrasAcciones.setGuardador(this::guardarAccionOtroIndividual);
                    contenedorOtros.getChildren().add(otrasAcciones.getRoot());
                    continue;
                }
                FilaUI fila = new FilaUI(key, entry.getValue(), imgBorrar, imgEditar);
                fila.setOnCambio(this::actualizarBoton);
                fila.setOnGuardarFila(() -> guardarFilaIndividual(fila));
                filasUI.add(fila);
                if (esVidrioMarco)
                    vidrioMarco.add(fila);
                else
                    contenedorFilas.getChildren().add(fila.getRoot());
            }
            for (FilaUI f : vidrioMarco) contenedorFilas.getChildren().add(f.getRoot());
        } catch (SQLException e) {
            mostrarError(e);
        }
    }

    private void configurarFiltroModelo() {
        Set<String> modelosEnBD = filasUI.stream()
                .flatMap(f -> f.getSkus().stream()
                        .filter(Componente::isActivo)     // solo modelos con alguna pieza activa
                        .map(c -> extraerModelo(c.getTipo(), f.getPrefijo())))
                .filter(m -> !m.isEmpty())
                .collect(Collectors.toSet());

        List<String> modelosFiltrados = MODELOS_ORDENADOS.stream()
                .filter(modelosEnBD::contains)
                .collect(Collectors.toList());

        cbFiltroModelo.getItems().addAll(modelosFiltrados);
        cbFiltroModelo.setPromptText("— Selecciona modelo —");

        cbFiltroModelo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                setText((empty || m == null) ? null : traducirModelo(m));
            }
        });
        cbFiltroModelo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                setText((empty || m == null) ? null : traducirModelo(m));
            }
        });

        cbFiltroModelo.valueProperty().addListener((obs, o, n) -> {
            boolean hayModelo = n != null;
            if (!tieneSolicitudesIniciales && !modoEdicion) {
                contenedorFilas.setVisible(hayModelo);
                contenedorFilas.setManaged(hayModelo);
                lblSeleccionaModelo.setVisible(!hayModelo);
                lblSeleccionaModelo.setManaged(!hayModelo);
            }
            for (FilaUI fila : filasUI) {
                fila.aplicarFiltroModelo(n);
            }
            if (otrasAcciones != null) otrasAcciones.setModelo(n);
        });

        if (solicitudesCargadas != null && !solicitudesCargadas.isEmpty()) {
            boolean hayPendientes = filasUI.stream().anyMatch(FilaUI::isSolicitud);
            solicitudesCargadas.stream()
                    .flatMap(sol -> filasUI.stream()
                        .filter(f -> f.getSkus().stream().anyMatch(c -> c.getIdCom() == sol.getIdCom()))
                        .map(f -> f.getSkus().stream()
                            .filter(c -> c.getIdCom() == sol.getIdCom())
                            .findFirst()
                            .map(c -> extraerModelo(c.getTipo(), f.getPrefijo()))
                            .orElse("")))
                    .filter(m -> !m.isEmpty() && cbFiltroModelo.getItems().contains(m))
                    .findFirst()
                    .ifPresent(modelo -> {
                        cbFiltroModelo.setValue(modelo);
                        cbFiltroModelo.setDisable(hayPendientes);
                    });
        } else if (modoEdicion) {
            // Auto-detectar modelo desde la fila en edición y bloquearlo
            filasUI.stream()
                    .filter(FilaUI::isModoEdicion)
                    .map(f -> f.getComponenteSeleccionado() != null
                            ? extraerModelo(f.getComponenteSeleccionado().getTipo(), f.getPrefijo()) : "")
                    .filter(m -> !m.isEmpty() && cbFiltroModelo.getItems().contains(m))
                    .findFirst()
                    .ifPresent(modelo -> {
                        cbFiltroModelo.setValue(modelo);
                        cbFiltroModelo.setDisable(true);
                    });
        } else {
            // Flujo normal: ocultar filas hasta que se seleccione modelo
            contenedorFilas.setVisible(false);
            contenedorFilas.setManaged(false);
            lblSeleccionaModelo.setVisible(true);
            lblSeleccionaModelo.setManaged(true);
        }
    }

    private void actualizarBoton() {
        boolean habilitado;
        if (modoEdicion) {
            boolean filaEditadaInvalida = filasUI.stream()
                    .filter(FilaUI::isModoEdicion)
                    .anyMatch(f -> f.hayCambio() && !f.tieneUso());
            boolean cambioEnEdicion = filasUI.stream()
                    .filter(FilaUI::isModoEdicion)
                    .anyMatch(f -> f.hayCambio() && f.tieneUso());
            boolean filasNuevas = filasUI.stream()
                    .filter(f -> !f.isModoEdicion()).anyMatch(FilaUI::isActiva);
            habilitado = !filaEditadaInvalida && (cambioEnEdicion || filasNuevas);
        } else {
            boolean activa = filasUI.stream().anyMatch(f -> !f.isGuardada() && f.isActiva())
                    || (otrasAcciones != null && otrasAcciones.hayAccion());
            boolean solicitudCancelada = tieneSolicitudesIniciales
                    && filasUI.stream().anyMatch(FilaUI::isSolicitudCancelada);
            boolean hayGuardadas = filasUI.stream().anyMatch(FilaUI::isGuardada)
                    || (otrasAcciones != null && otrasAcciones.hayAccionGuardada());
            habilitado = activa || solicitudCancelada || hayGuardadas;
        }
        zonaGuardar.setVisible(habilitado);
        zonaGuardar.setManaged(habilitado);
        programarAutoGuardado();
    }

    // ── Borrador: orquestación ──────────────────────────────────────────────

    private com.reparaciones.models.BorradorContenido capturarBorrador() {
        com.reparaciones.models.BorradorContenido b = new com.reparaciones.models.BorradorContenido();
        b.modelo = cbFiltroModelo.getValue();
        for (FilaUI fila : filasUI) {
            com.reparaciones.models.BorradorContenido.Fila f = fila.capturarEnBorrador();
            if (f != null) b.filas.add(f);
        }
        if (otrasAcciones != null) b.otros = otrasAcciones.capturarAcciones();
        return b;
    }

    /** Vacío = sin filas ni otras acciones (el modelo solo no cuenta, se auto-rellena). */
    private boolean borradorVacio(com.reparaciones.models.BorradorContenido b) {
        return b.filas.isEmpty() && b.otros.isEmpty();
    }

    private void programarAutoGuardado() {
        if (idAsignacion == null || recuperandoBorrador || borradorDescartado) return;   // solo flujo nuevo
        if (autoGuardado == null) {
            autoGuardado = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            autoGuardado.setOnFinished(e -> guardarBorradorAhora());
        }
        autoGuardado.playFromStart();   // reinicia el debounce
    }

    private void guardarBorradorAhora() {
        if (idAsignacion == null || recuperandoBorrador || borradorDescartado) return;
        try {
            com.reparaciones.models.BorradorContenido b = capturarBorrador();
            if (borradorVacio(b)) borradorDAO.eliminar(idAsignacion);
            else borradorDAO.guardar(idAsignacion, gson.toJson(b));
        } catch (Exception ex) {
            // silencioso: un fallo de auto-guardado no debe molestar al técnico
        }
    }

    private void verificarFilasGuardadasBorradas(com.reparaciones.models.BorradorContenido b) {
        boolean hayGuardadas = b.filas.stream().anyMatch(f -> f.guardada)
                || b.otros.stream().anyMatch(o -> o.guardada);
        if (!hayGuardadas) return;
        try {
            Set<String> idsExistentes = reparacionDAO.getByImei(imei).stream()
                    .map(com.reparaciones.models.Reparacion::getIdRep)
                    .collect(Collectors.toSet());
            boolean cambio = false;
            for (FilaUI fila : filasUI) {
                if (fila.isGuardada() && !idsExistentes.contains(fila.getIdRepGenerado())) {
                    fila.desbloquearTrasEliminacion();
                    cambio = true;
                }
            }
            if (otrasAcciones != null && otrasAcciones.desbloquearEliminadas(idsExistentes)) {
                cambio = true;
            }
            if (cambio) guardarBorradorAhora();
        } catch (SQLException ex) {
            // verificación fallida: las filas se mantienen bloqueadas, se reintenta al reabrir
        }
    }

    private void guardarFilaIndividual(FilaUI fila) {
        fila.setGuardandoEnCurso(true);
        List<FilaReparacion> payload = new ArrayList<>();
        boolean esSolicitudNueva = fila.isSolicitud() && fila.isSolicitudNueva();
        boolean tieneUso = fila.getCantidad() > 0 || fila.isReutilizado();
        if (esSolicitudNueva && tieneUso) {
            payload.add(new FilaReparacion(fila.getIdComSeleccionado(), fila.getCantidad(),
                    fila.isReutilizado(), fila.getObservacion(), fila.getPrefijo(), false, null, null));
            payload.add(new FilaReparacion(fila.getIdComSeleccionado(), 1, false, null,
                    fila.getPrefijo(), true, fila.getDescripcionSolicitud(), null));
        } else {
            payload.add(new FilaReparacion(fila.getIdComSeleccionado(), fila.getCantidad(),
                    fila.isReutilizado(), fila.getObservacion(), fila.getPrefijo(),
                    esSolicitudNueva, fila.getDescripcionSolicitud(), null));
        }
        try {
            String idRep = reparacionDAO.guardarFilaIndividual(
                    payload, imei, Sesion.getIdTec(), idRepAnterior, idAsignacion);
            String fecha = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            fila.aplicarGuardada(idRep, fecha);
            guardarBorradorAhora();
            actualizarBoton();
        } catch (StaleDataException ex) {
            fila.setGuardandoEnCurso(false);
            new Alert(Alert.AlertType.WARNING,
                    "No se pudo guardar la fila: " + ex.getMessage()).showAndWait();
        } catch (SQLException ex) {
            fila.setGuardandoEnCurso(false);
            Alertas.mostrarError("No se pudo guardar la fila: " + ex.getMessage());
        }
    }

    private String guardarAccionOtroIndividual(String descripcion) {
        if (otrasAcciones == null || otrasAcciones.getIdComOtro() == -1) return null;
        try {
            FilaReparacion fila = new FilaReparacion(
                    otrasAcciones.getIdComOtro(), 0, false, descripcion, "otro", false, null, null);
            String idRep = reparacionDAO.guardarFilaIndividual(
                    List.of(fila), imei, Sesion.getIdTec(), idRepAnterior, idAsignacion);
            guardarBorradorAhora();
            return idRep;
        } catch (StaleDataException ex) {
            new Alert(Alert.AlertType.WARNING,
                    "No se pudo guardar la acción: " + ex.getMessage()).showAndWait();
            return null;
        } catch (SQLException ex) {
            Alertas.mostrarError("No se pudo guardar la acción: " + ex.getMessage());
            return null;
        }
    }
    /** Tras un guardado real: detiene el debounce, borra el borrador y bloquea su re-creación al cerrar. */
    private void descartarBorradorTrasGuardar() {
        borradorDescartado = true;
        if (autoGuardado != null) autoGuardado.stop();
        try { if (idAsignacion != null) borradorDAO.eliminar(idAsignacion); } catch (Exception ignore) {}
    }

    @FXML
    private void guardar() {
        if (!esperandoConfirmacion) {
            activarConfirmacion();
        } else {
            ejecutarGuardar();
        }
    }

    private void activarConfirmacion() {
        esperandoConfirmacion = true;
        btnGuardar.setDisable(false);
        btnGuardar.setText("✓  Confirmar terminar");
    }

    private void ejecutarGuardar() {
        esperandoConfirmacion = false;

        if (modoEdicion) {
            ejecutarGuardarEdicion();
        } else {
            ejecutarGuardarNueva();
        }
    }

    private void ejecutarGuardarNueva() {
        // 1. Filas agotadas: descontar stock + crear solicitud, sin crear R*
        for (FilaUI fila : filasUI) {
            if (!fila.esAgotadoNuevo()) continue;
            try {
                reparacionDAO.agotarComponente(
                        idAsignacion,
                        fila.getIdComSeleccionado(),
                        fila.getCantidad(),
                        fila.getDescripcionAgotado());
            } catch (StaleDataException ex) {
                new Alert(Alert.AlertType.WARNING,
                        "No se pudo registrar componente agotado: " + ex.getMessage()).showAndWait();
                return;
            } catch (SQLException ex) {
                Alertas.mostrarError("Error al registrar componente agotado: " + ex.getMessage());
                return;
            }
        }

        // 2. Filas normales → insertarCompleta
        List<FilaReparacion> filasActivas = new ArrayList<>();
        for (FilaUI fila : filasUI) {
            if (fila.isGuardada()) continue;
            if (fila.esAgotadoNuevo()) continue;
            if (fila.isActiva()) {
                boolean esSolicitudNueva = fila.isSolicitud() && fila.isSolicitudNueva();
                boolean tieneUso = fila.getCantidad() > 0 || fila.isReutilizado();
                if (esSolicitudNueva && tieneUso) {
                    // Fila con componente usado Y aviso de solicitud: emitir dos entradas separadas
                    filasActivas.add(new FilaReparacion(
                            fila.getIdComSeleccionado(),
                            fila.getCantidad(),
                            fila.isReutilizado(),
                            fila.getObservacion(),
                            fila.getPrefijo(),
                            false,
                            null,
                            null));
                    filasActivas.add(new FilaReparacion(
                            fila.getIdComSeleccionado(),
                            1,
                            false,
                            null,
                            fila.getPrefijo(),
                            true,
                            fila.getDescripcionSolicitud(),
                            null));
                } else {
                    filasActivas.add(new FilaReparacion(
                            fila.getIdComSeleccionado(),
                            fila.getCantidad(),
                            fila.isReutilizado(),
                            fila.getObservacion(),
                            fila.getPrefijo(),
                            esSolicitudNueva,
                            fila.getDescripcionSolicitud(),
                            null));
                }
            }
        }

        // Acciones "otro": una FilaReparacion por descripción, cantidad 0 (stock neutro)
        if (otrasAcciones != null && otrasAcciones.getIdComOtro() != -1) {
            int otroIdCom = otrasAcciones.getIdComOtro();
            for (String desc : otrasAcciones.getDescripcionesPendientes()) {
                filasActivas.add(new FilaReparacion(otroIdCom, 0, false, desc, "otro", false, null, null));
            }
        }

        // Si solo había filas agotadas, cerrar sin crear reparación
        boolean soloAgotadas = filasActivas.isEmpty()
                && filasUI.stream().anyMatch(FilaUI::esAgotadoNuevo);
        if (soloAgotadas) {
            descartarBorradorTrasGuardar();
            Stage stage = (Stage) btnGuardar.getScene().getWindow();
            stage.close();
            if (onGuardado != null)
                onGuardado.run();
            return;
        }

        try {
            reparacionDAO.insertarCompleta(filasActivas, imei,
                    Sesion.getIdTec(), idRepAnterior, idAsignacion);
            descartarBorradorTrasGuardar();
            Stage stage = (Stage) btnGuardar.getScene().getWindow();
            stage.close();
            if (onGuardado != null)
                onGuardado.run();
        } catch (StaleDataException ex) {
            new Alert(Alert.AlertType.WARNING,
                    "No se pudo guardar: " + ex.getMessage() + "\n" +
                    "Cierra el formulario y comprueba el estado de la asignación.")
                    .showAndWait();
        } catch (SQLException ex) {
            Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
        }
    }

    private void ejecutarGuardarEdicion() {
        if (taEditarOtro != null) {
            try {
                reparacionDAO.editarReparacion(idRepEditar, idComOtroEditar, false,
                        taEditarOtro.getText().trim(), 0, updatedAtEdicion);
                Stage stage = (Stage) btnGuardar.getScene().getWindow();
                stage.close();
                if (onGuardado != null) onGuardado.run();
            } catch (StaleDataException ex) {
                new Alert(Alert.AlertType.WARNING,
                        "No se pudo guardar: otro usuario modificó esta reparación.").showAndWait();
            } catch (SQLException ex) {
                Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
            }
            return;
        }
        try {
            // 1. Editar la fila que está en modo edición (si cambió)
            for (FilaUI fila : filasUI) {
                if (fila.isModoEdicion() && fila.hayCambio()) {
                    reparacionDAO.editarReparacion(
                            idRepEditar,
                            fila.getIdComSeleccionado(),
                            fila.isReutilizado(),
                            fila.getObservacion(),
                            fila.getCantidad(),
                            updatedAtEdicion);
                    break;
                }
            }
            // 2. Insertar filas nuevas agrupadas por técnico
            Map<Integer, List<FilaReparacion>> porTecnico = new java.util.LinkedHashMap<>();
            for (FilaUI fila : filasUI) {
                if (!fila.isModoEdicion() && fila.isActiva()) {
                    int idTec = fila.getTecnicoId() != -1 ? fila.getTecnicoId() : idTecEditar;
                    porTecnico.computeIfAbsent(idTec, k -> new ArrayList<>()).add(
                            new FilaReparacion(
                                    fila.getIdComSeleccionado(), fila.getCantidad(),
                                    fila.isReutilizado(), fila.getObservacion(),
                                    fila.getPrefijo(), fila.isSolicitud(),
                                    fila.getDescripcionSolicitud(), null));
                }
            }
            for (Map.Entry<Integer, List<FilaReparacion>> entry : porTecnico.entrySet()) {
                reparacionDAO.insertarCompleta(entry.getValue(), imeiEditar,
                        entry.getKey(), null, null);
            }
            Stage stage = (Stage) btnGuardar.getScene().getWindow();
            stage.close();
            if (onGuardado != null)
                onGuardado.run();
        } catch (StaleDataException ex) {
            new Alert(Alert.AlertType.WARNING,
                    "No se pudo guardar: otro usuario modificó esta reparación.\n" +
                    "Cierra y vuelve a abrir el formulario para ver los cambios actuales.")
                    .showAndWait();
        } catch (SQLException ex) {
            Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
        }
    }

    public static void abrir(String imei, String idRepAnterior,
            String idAsignacion, Runnable onGuardado) {
        Platform.runLater(() -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        FormularioReparacionController.class.getResource(
                                "/views/FormularioReparacionView.fxml"));
                javafx.scene.Parent root = loader.load();
                FormularioReparacionController ctrl = loader.getController();

                Stage stage = new Stage();
                stage.setTitle("Nueva reparación — IMEI " + imei);
                stage.setScene(new javafx.scene.Scene(root));
                stage.setResizable(true);
                stage.setMinWidth(960);
                stage.setMinHeight(700);

                ctrl.init(imei, idRepAnterior, idAsignacion, onGuardado);

                stage.setOnCloseRequest(ev -> {
                    // Con borrador persistente no se pierde nada: flush del borrador y cierre sin preguntar.
                    if (ctrl.autoGuardado != null) ctrl.autoGuardado.stop();
                    ctrl.guardarBorradorAhora();
                });
                stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                stage.show();

            } catch (Exception e) {
                Alertas.mostrarError(e.getMessage());
            }
        });
    }

    public boolean hayCambiosSinGuardar() {
        return zonaGuardar != null && zonaGuardar.isVisible();
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }

    // ─── Utilidades estáticas ─────────────────────────────────────────────────

    /**
     * Extrae el modelo del SKU buscando qué modelo conocido aparece
     * al inicio del SKU tras quitar el prefijo y la 'i' de iPhone.
     * Compatible con cualquier sufijo nuevo sin tocar el código.
     */
    static String extraerModelo(String sku, String prefijo) {
        String s = sku.toLowerCase().substring(prefijo.length());
        if (s.startsWith("i"))
            s = s.substring(1);
        final String resto = s;
        return MODELOS_ORDENADOS.stream()
                .sorted((a, b) -> b.length() - a.length())
                .filter(resto::startsWith)
                .findFirst()
                .orElse("");
    }

    /**
     * Traduce el modelo interno del SKU al nombre comercial legible.
     *
     * Modelos numéricos (11, 12, 13, 14, 15, 16...):
     * Se traducen automáticamente — no hace falta añadir casos nuevos.
     * Ejemplo futuro: "17" → "iPhone 17" (automático)
     * "17plus" → "iPhone 17 Plus" (automático)
     * "17pro" → "iPhone 17 Pro" (automático)
     * "17promax" → "iPhone 17 Pro Max" (automático)
     *
     * Modelos especiales sin número (x, xs, xsmax, xr, se2020):
     * Hardcodeados porque son históricos y no van a cambiar.
     */
    static String traducirModelo(String modelo) {
        if (modelo == null || modelo.isEmpty())
            return modelo;
        return switch (modelo) {
            case "se2020" -> "iPhone SE 2020";
            case "x" -> "iPhone X";
            case "xr" -> "iPhone XR";
            case "xs" -> "iPhone XS";
            case "xsmax" -> "iPhone XS Max";
            // Casos especiales con letra sin número claro
            case "6s" -> "iPhone 6S";
            case "6splus" -> "iPhone 6S Plus";
            case "air" -> "iPhone Air";
            default -> {
                String num = modelo.replaceAll("[^0-9]", "");
                String variante = modelo.replaceAll("[0-9]", "");
                String sufijo = switch (variante) {
                    case "plus" -> " Plus";
                    case "mini" -> " Mini";
                    case "pro" -> " Pro";
                    case "promax" -> " Pro Max";
                    case "e" -> "e";
                    default -> "";
                };
                yield "iPhone " + num + sufijo;
            }
        };
    }

    // ─── FilaUI ───────────────────────────────────────────────────────────────

    static class FilaUI {

        private final String prefijo;
        private final List<Componente> skus;
        private final VBox root;

        private final Label lblContador;
        private final Button btnMas;
        private final Button btnMenos;
        private final ComboBox<Componente> cbSku;
        private final Label lblStock;
        private final CheckBox chkReutilizado;
        private final Button btnObservacion;
        private final Label lblObservacion;
        private final Button btnBorrarObs;
        private final Button btnSolicitud;

        private static final String STYLE_SOL_INACTIVA =
                "-fx-background-color: #E7E7E7; -fx-text-fill: #A9A9A9;" +
                "-fx-font-size: 11px; -fx-background-radius: 0; -fx-padding: 4 10 4 10;";
        private static final String STYLE_SOL_ACTIVA =
                "-fx-background-color: " + com.reparaciones.utils.Colores.FILA_SOLICITUD_BRD + "; -fx-text-fill: white;" +
                "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 0; -fx-padding: 4 10 4 10;";
        private static final String STYLE_SOL_RECIBIDA =
                "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32;" +
                "-fx-font-size: 11px; -fx-background-radius: 0; -fx-padding: 4 10 4 10;";
        private static final String STYLE_BTN_GUARDAR_FILA =
                "-fx-background-color: #001232; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10 4 10;";
        private static final String STYLE_SOL_EN_CAMINO =
                "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;" +
                "-fx-font-size: 11px; -fx-background-radius: 0; -fx-padding: 4 10 4 10;";

        private final HBox mainRow;

        private int cantidad = 0;
        private String observacion = null;
        private boolean solicitudActiva = false;
        private boolean solicitudFueCancelada = false;
        private boolean solicitudNuevaEnEstaSesion = false;
        private String descripcionSolicitud = null;
        private Runnable onCambio;

        // ── Guardado individual ──────────────────────────────────────────────
        private boolean guardada = false;
        private String  idRepGenerado = null;
        private String  fechaGuardado = null;
        private boolean recibidoPendienteUso = false;
        private boolean esperandoConfGuardar = false;
        private Runnable onGuardarFila = null;

        // ── Agotado ───────────────────────────────────────────────────────────
        private HBox subFilaAgotado;
        private Label lblSubAgotado;
        private Button btnSubAgotado;
        private Button btnEditarDesc;
        private boolean agotadoConfirmado = false;
        private String descripcionAgotado = null;

        // ── Edición ───────────────────────────────────────────────────────────
        private boolean modoEdicion = false;
        private boolean formularioEnEdicion = false;   // el formulario edita una reparación: ninguna fila muestra "Guardar fila"
        private int idComOriginal = -1;
        private int cantidadOriginal = 0;
        private boolean esReutilizadoOriginal = false;
        private String observacionOriginal = null;

        FilaUI(String prefijo, List<Componente> skus, Image imgBorrar, Image imgEditar) {
            this.prefijo = prefijo;
            this.skus = skus;

            lblContador = new Label("0");
            lblContador.setMinWidth(34);
            lblContador.setPrefWidth(34);
            lblContador.setAlignment(Pos.CENTER);
            lblContador.setFont(Font.font("Inter", FontWeight.BOLD, 20));
            lblContador.setStyle("-fx-text-fill: #A9A9A9;");

            btnMas = botonContador("+");
            btnMenos = botonContador("-");
            btnMenos.setDisable(true);

            VBox botonera = new VBox(btnMas, btnMenos);
            botonera.setMinWidth(35);
            botonera.setMaxWidth(35);

            HBox wrapContador = new HBox(lblContador, botonera);
            wrapContador.setMinWidth(70);
            wrapContador.setMaxWidth(70);
            wrapContador.setAlignment(Pos.CENTER_LEFT);

            Label lblNombre = new Label(traducirTipo(prefijo));
            lblNombre.setMinWidth(100);
            lblNombre.setPrefWidth(100);
            lblNombre.setMaxWidth(100);
            lblNombre.setStyle("-fx-font-size: 12px; -fx-padding: 0 10 0 10;");

            chkReutilizado = new CheckBox("Reutilizado");
            chkReutilizado.setMinWidth(110);
            chkReutilizado.setPrefWidth(110);
            chkReutilizado.setStyle("-fx-font-size: 12px; -fx-padding: 0 10 0 10;");

            cbSku = new ComboBox<>();
            cbSku.getItems().addAll(skus);
            cbSku.setMinWidth(170);
            cbSku.setPrefWidth(170);
            cbSku.setMaxWidth(170);
            cbSku.setVisibleRowCount(8);
            cbSku.setStyle("-fx-font-size: 11px;");

            cbSku.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Componente c, boolean empty) {
                    super.updateItem(c, empty);
                    if (empty || c == null) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    setText(c.getTipo());
                    aplicarColorStock(this, c);
                }
            });
            cbSku.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Componente c, boolean empty) {
                    super.updateItem(c, empty);
                    if (empty || c == null) {
                        setText("—");
                        setStyle("");
                        return;
                    }
                    setText(c.getTipo());
                    aplicarColorStock(this, c);
                }
            });

            Componente porDefecto = skus.stream()
                    .filter(c -> c.getStock() > 0)
                    .findFirst().orElse(skus.get(0));
            cbSku.setValue(porDefecto);

            lblStock = new Label(prefijo.equals("otro") ? "—" : String.valueOf(porDefecto.getStock()));
            lblStock.setMinWidth(70);
            lblStock.setPrefWidth(70);
            lblStock.setMaxWidth(70);
            lblStock.setAlignment(Pos.CENTER);
            lblStock.setStyle("-fx-font-size: 12px; -fx-padding: 0 10 0 10;");

            btnMas.setDisable(!prefijo.equals("otro") && porDefecto.getStock() <= 0);

            cbSku.valueProperty().addListener((obs, o, n) -> {
                if (n != null) {
                    lblStock.setText(prefijo.equals("otro") ? "—" : String.valueOf(n.getStock()));
                    if (!prefijo.equals("otro")) {
                        // En edición con mismo componente, el stock visible ya no incluye
                        // las piezas de esta reparación — no resetear cantidad por eso
                        boolean mismoComponenteEnEdicion = modoEdicion && n.getIdCom() == idComOriginal;
                        if (!mismoComponenteEnEdicion && cantidad > n.getStock()) {
                            cantidad = 0;
                            actualizarContador();
                            if (!chkReutilizado.isSelected())
                                chkReutilizado.setDisable(false);
                        }
                        if (!chkReutilizado.isSelected()) {
                            btnMas.setDisable(!mismoComponenteEnEdicion && cantidad >= n.getStock());
                        }
                    }
                    if (modoEdicion) actualizarStockPreview();
                    actualizarSubFilaAgotado();
                    notificar();
                }
            });

            btnObservacion = new Button("Añadir observación");
            ImageView ivObs = new ImageView(imgEditar);
            ivObs.setFitWidth(14); ivObs.setFitHeight(14); ivObs.setPreserveRatio(true);
            btnObservacion.setGraphic(ivObs);
            btnObservacion.setStyle(
                    "-fx-background-color: #888888; -fx-text-fill: #E7E7E7;" +
                            "-fx-font-size: 11px; -fx-cursor: hand;" +
                            "-fx-background-radius: 0; -fx-padding: 4 10 4 10;");
            btnObservacion.setMinHeight(27);
            btnObservacion.setMaxHeight(27);

            lblObservacion = new Label();
            lblObservacion.setStyle("-fx-font-size: 12px; -fx-text-fill: #000000;");
            lblObservacion.setMinWidth(0);
            lblObservacion.setMaxWidth(Double.MAX_VALUE);
            lblObservacion.setTextOverrun(OverrunStyle.ELLIPSIS);
            lblObservacion.setVisible(false);
            lblObservacion.setManaged(false);

            ImageView ivBorrarObs = new ImageView(imgBorrar);
            ivBorrarObs.setFitWidth(20);
            ivBorrarObs.setFitHeight(20);
            ivBorrarObs.setPreserveRatio(true);
            btnBorrarObs = new Button();
            btnBorrarObs.setGraphic(ivBorrarObs);
            btnBorrarObs.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
            btnBorrarObs.setVisible(false);
            btnBorrarObs.setManaged(false);

            HBox wrapObs = new HBox(4, btnObservacion, lblObservacion, btnBorrarObs);
            wrapObs.setAlignment(Pos.CENTER_LEFT);
            wrapObs.setMaxWidth(280);
            HBox.setHgrow(wrapObs, Priority.ALWAYS);
            HBox.setHgrow(lblObservacion, Priority.ALWAYS);

            btnSolicitud = new Button("⚠ Solicitud pieza");
            btnSolicitud.setStyle(STYLE_SOL_INACTIVA);
            btnSolicitud.setMinHeight(27);
            btnSolicitud.setMaxHeight(27);
            btnSolicitud.setOnAction(e -> abrirSolicitud());
            // Oculto siempre — solo se muestra cuando activarSolicitud() carga una solicitud existente de BD
            btnSolicitud.setVisible(false);
            btnSolicitud.setManaged(false);

            mainRow = new HBox(wrapContador, lblNombre, cbSku, lblStock, chkReutilizado, wrapObs, btnSolicitud);
            mainRow.setAlignment(Pos.CENTER_LEFT);
            mainRow.setMinHeight(37);

            // ── Sub-fila agotado ──────────────────────────────────────────────
            lblSubAgotado = new Label("⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.");
            lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A5C00;");
            btnSubAgotado = new Button("Solicitar y descontar stock");
            btnSubAgotado.setStyle(
                    "-fx-background-color: #E8A825; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10 4 10;");
            ImageView ivEditar = new ImageView(imgEditar);
            ivEditar.setFitWidth(16); ivEditar.setFitHeight(16); ivEditar.setPreserveRatio(true);
            btnEditarDesc = new Button();
            btnEditarDesc.setGraphic(ivEditar);
            btnEditarDesc.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
            btnEditarDesc.setVisible(false);
            btnEditarDesc.setManaged(false);
            btnEditarDesc.setOnAction(e -> editarDescripcionAgotado());
            subFilaAgotado = new HBox(10, lblSubAgotado, btnSubAgotado, btnEditarDesc);
            subFilaAgotado.setAlignment(Pos.CENTER_LEFT);
            subFilaAgotado.setStyle("-fx-background-color: #FFF8E0; -fx-padding: 4 8 4 70;");
            subFilaAgotado.setVisible(false);
            subFilaAgotado.setManaged(false);

            root = new VBox(mainRow, subFilaAgotado);
            root.setStyle("-fx-background-color: #F3F3F3; " +
                    "-fx-border-color: transparent transparent #E0E0E0 transparent;" +
                    "-fx-border-width: 0 0 1 0;");

            btnSubAgotado.setOnAction(e -> abrirDialogoAgotado());

            btnMas.setOnAction(e -> {
                if (prefijo.equals("otro")) {
                    cantidad++;
                    actualizarContador();
                    chkReutilizado.setDisable(true);
                    notificar();
                    return;
                }
                int stockActual = cbSku.getValue() != null ? cbSku.getValue().getStock() : 0;
                if (cantidad < stockActual) {
                    cantidad++;
                    actualizarContador();
                    btnMas.setDisable(cantidad >= stockActual);
                    chkReutilizado.setDisable(true);
                    notificar();
                }
            });

            btnMenos.setOnAction(e -> {
                if (cantidad > 0) {
                    cantidad--;
                    actualizarContador();
                    if (!prefijo.equals("otro")) {
                        int stockActual = cbSku.getValue() != null ? cbSku.getValue().getStock() : 0;
                        btnMas.setDisable(cantidad >= stockActual);
                    }
                    if (cantidad == 0)
                        chkReutilizado.setDisable(false);
                    notificar();
                }
            });

            chkReutilizado.setOnAction(e -> {
                boolean marcado = chkReutilizado.isSelected();
                btnMas.setDisable(marcado);
                btnMenos.setDisable(marcado);
                actualizarContador();
                actualizarSubFilaAgotado();
                notificar();
            });

            btnObservacion.setOnAction(e -> abrirObservacion());
            Platform.runLater(this::actualizarSubFilaAgotado);

            btnBorrarObs.setOnAction(e -> {
                observacion = null;
                lblObservacion.setText("");
                btnObservacion.setVisible(true);
                btnObservacion.setManaged(true);
                lblObservacion.setVisible(false);
                lblObservacion.setManaged(false);
                btnBorrarObs.setVisible(false);
                btnBorrarObs.setManaged(false);
                notificar();   // borrar el comentario también es un cambio: recalcular botones
            });
        }

        // ── Filtro global de modelo ───────────────────────────────────────────

        void aplicarFiltroModelo(String modelo) {
            if (guardada) return;
            agotadoConfirmado = false;
            descripcionAgotado = null;
            subFilaAgotado.setVisible(false);
            subFilaAgotado.setManaged(false);
            lblSubAgotado.setText("⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.");
            lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A5C00;");
            btnSubAgotado.setVisible(true);
            btnSubAgotado.setManaged(true);
            btnEditarDesc.setVisible(false); btnEditarDesc.setManaged(false);
            subFilaAgotado.setStyle("-fx-background-color: #FFF8E0; -fx-padding: 4 8 4 70;");

            cantidad = 0;
            actualizarContador();
            chkReutilizado.setSelected(false);
            chkReutilizado.setDisable(false);
            cbSku.setDisable(false);
            solicitudActiva = false;
            solicitudFueCancelada = false;
            solicitudNuevaEnEstaSesion = false;
            descripcionSolicitud = null;
            recibidoPendienteUso = false;
            btnSolicitud.setText("⚠ Solicitud pieza");
            btnSolicitud.setStyle(STYLE_SOL_INACTIVA);
            btnSolicitud.setDisable(false);
            observacion = null;
            lblObservacion.setText("");
            btnObservacion.setVisible(true);
            btnObservacion.setManaged(true);
            lblObservacion.setVisible(false);
            lblObservacion.setManaged(false);
            btnBorrarObs.setVisible(false);
            btnBorrarObs.setManaged(false);

            if (modelo == null) {
                List<Componente> activos = skus.stream().filter(Componente::isActivo).collect(Collectors.toList());
                cbSku.getItems().setAll(activos);
                if (activos.isEmpty()) {
                    cbSku.setValue(null);
                    cbSku.setDisable(true);
                    btnMas.setDisable(true);
                    btnMenos.setDisable(true);
                    chkReutilizado.setDisable(true);
                    btnObservacion.setDisable(true);
                    root.setOpacity(0.4);
                    return;
                }
                Componente def = activos.stream()
                        .filter(c -> c.getStock() > 0)
                        .findFirst().orElse(activos.get(0));
                cbSku.setValue(def);
                cbSku.setDisable(false);
                btnMas.setDisable(!prefijo.equals("otro") && def.getStock() <= 0);
                btnMenos.setDisable(true);
                chkReutilizado.setDisable(false);
                btnObservacion.setDisable(false);
                root.setOpacity(1.0);
                return;
            }

            List<Componente> filtrados = skus.stream()
                    .filter(c -> extraerModelo(c.getTipo(), prefijo).equals(modelo))
                    .filter(Componente::isActivo)
                    .collect(Collectors.toList());

            if (filtrados.isEmpty()) {
                cbSku.getItems().clear();
                cbSku.setValue(null);
                cbSku.setDisable(true);
                btnMas.setDisable(true);
                btnMenos.setDisable(true);
                chkReutilizado.setDisable(true);
                btnObservacion.setDisable(true);
                lblStock.setText("—");
                root.setOpacity(0.4);
            } else {
                cbSku.getItems().setAll(filtrados);
                Componente def = filtrados.stream()
                        .filter(c -> c.getStock() > 0)
                        .findFirst().orElse(filtrados.get(0));
                cbSku.setValue(def);
                cbSku.setDisable(false);
                btnMas.setDisable(!prefijo.equals("otro") && def.getStock() <= 0);
                btnMenos.setDisable(true);
                chkReutilizado.setDisable(false);
                btnObservacion.setDisable(false);
                root.setOpacity(1.0);
            }
        }

        // ── Color stock ───────────────────────────────────────────────────────

        private static void aplicarColorStock(ListCell<Componente> cell, Componente c) {
            if (c.getStock() == 0) {
                cell.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.ROJO_SIN_STOCK + ";");
            } else if (c.getStock() <= c.getStockMinimo()) {
                cell.setStyle("-fx-text-fill: " + com.reparaciones.utils.Colores.FILA_SOLICITUD_BRD + ";");
            } else {
                cell.setStyle("");
            }
        }

        // ── Observación ───────────────────────────────────────────────────────

        private void abrirObservacion() {
            TextArea ta = new TextArea(observacion != null ? observacion : "");
            ta.setWrapText(true);
            ta.setPrefRowCount(5);
            ta.setPrefWidth(400);
            ta.setStyle("-fx-font-size: 13px;");

            Button btnGuardar = new Button("Guardar");
            btnGuardar.setMaxWidth(Double.MAX_VALUE);
            btnGuardar.setStyle("-fx-background-color: #8AC7AF; -fx-text-fill: white;" +
                    "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");

            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Observación");
            dialog.setHeaderText("Observación para: " + traducirTipo(prefijo));
            dialog.getDialogPane().setContent(new VBox(8, ta, btnGuardar));
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(440);

            btnGuardar.setOnAction(e -> {
                String trimmed = ta.getText().trim();
                if (!trimmed.isEmpty()) {
                    observacion = trimmed;
                    lblObservacion.setText(observacion);
                    btnObservacion.setVisible(false);
                    btnObservacion.setManaged(false);
                    lblObservacion.setVisible(true);
                    lblObservacion.setManaged(true);
                    btnBorrarObs.setVisible(true);
                    btnBorrarObs.setManaged(true);
                    notificar();   // recalcular botones: en edición, un cambio de solo-comentario debe mostrar "Guardar cambios"
                }
                dialog.close();
            });

            dialog.showAndWait();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static Button botonContador(String texto) {
            Button btn = new Button(texto);
            btn.setMinWidth(35);
            btn.setMaxWidth(35);
            btn.setMinHeight(18);
            btn.setMaxHeight(18);
            btn.setStyle("-fx-background-color: #A9A9A9; -fx-text-fill: #E7E7E7;" +
                    "-fx-font-size: 14px; -fx-font-weight: bold;" +
                    "-fx-background-radius: 0; -fx-cursor: hand; -fx-padding: 0;");
            return btn;
        }

        private void actualizarContador() {
            lblContador.setText(String.valueOf(cantidad));
            btnMenos.setDisable(cantidad == 0);
            String color;
            if (modoEdicion && cantidad == 0 && !chkReutilizado.isSelected())
                color = "#C94040";
            else
                color = cantidad > 0 ? "#000000" : "#A9A9A9";
            lblContador.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: " + (modoEdicion && cantidad == 0 && !chkReutilizado.isSelected() ? "bold" : "normal") + ";");
            if (modoEdicion) actualizarStockPreview();
            actualizarSubFilaAgotado();
        }

        private void actualizarStockPreview() {
            if (prefijo.equals("otro")) return;
            Componente sel = cbSku.getValue();
            if (sel == null) return;

            int stockActual = sel.getStock();
            boolean mismoComponente = sel.getIdCom() == idComOriginal;
            boolean reutilizadoNuevo = chkReutilizado.isSelected();

            int devuelto = (mismoComponente && !esReutilizadoOriginal) ? cantidadOriginal : 0;
            int descontado = reutilizadoNuevo ? 0 : cantidad;
            int preview = stockActual + devuelto - descontado;

            String color;
            if      (preview < stockActual) color = "#C94040";
            else if (preview > stockActual) color = "#4CAF50";
            else                            color = "#586376";

            lblStock.setText(stockActual + " → " + preview);
            lblStock.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }

        private void notificar() {
            actualizarBotonGuardarFila();
            if (onCambio != null)
                onCambio.run();
        }

        static String traducirTipo(String prefijo) {
            return switch (prefijo) {
                case "bat" -> "Batería";
                case "cha" -> "Chasis";
                case "g" -> "Glass";
                case "cam" -> "Cámara";
                case "lcd" -> "Pantalla";
                case "mc" -> "Marco";
                case "otro" -> "Otros";
                default -> prefijo;
            };
        }

        // ── Agotado: métodos ─────────────────────────────────────────────────

        private void actualizarSubFilaAgotado() {
            if (prefijo.equals("otro") || modoEdicion) return;
            if (agotadoConfirmado) return;
            if (solicitudActiva) return;
            Componente sel = cbSku.getValue();
            if (sel == null) { subFilaAgotado.setVisible(false); subFilaAgotado.setManaged(false); return; }
            boolean sinStock = sel.getStock() == 0;
            boolean enLimite = !chkReutilizado.isSelected() && sel.getStock() > 0 && cantidad >= sel.getStock();
            boolean mostrar = sinStock || enLimite;
            if (mostrar) {
                if (sinStock) {
                    lblSubAgotado.setText("⚠  Sin stock disponible. Solicita la pieza para que el admin gestione el pedido.");
                    btnSubAgotado.setText("Solicitar pieza");
                } else {
                    lblSubAgotado.setText("⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.");
                    btnSubAgotado.setText("Solicitar y descontar stock");
                }
            }
            subFilaAgotado.setVisible(mostrar);
            subFilaAgotado.setManaged(mostrar);
        }

        private void abrirDialogoAgotado() {
            Componente sel = cbSku.getValue();
            int stock = sel != null ? sel.getStock() : 0;
            boolean sinStock = stock == 0;

            TextArea ta = new TextArea(descripcionAgotado != null ? descripcionAgotado : "");
            ta.setPromptText("Describe la pieza que necesitas (opcional)...");
            ta.setWrapText(true);
            ta.setPrefRowCount(4);
            ta.setPrefWidth(380);
            ta.setStyle("-fx-font-size: 13px;");

            String btnOkText = sinStock
                    ? "Confirmar: solicitar pieza"
                    : "Confirmar: descontar " + stock + " ud. de stock y solicitar";
            Button btnOk = new Button(btnOkText);
            btnOk.setMaxWidth(Double.MAX_VALUE);
            btnOk.setStyle("-fx-background-color: #E8A825; -fx-text-fill: white;" +
                    "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");

            String headerText = sinStock
                    ? "Sin stock disponible.\nSe creará una solicitud PENDIENTE para que el admin gestione el pedido."
                    : "Se descontarán " + stock + " unidades de stock y quedará una solicitud PENDIENTE.\nLa asignación permanecerá abierta hasta recibir la pieza.";
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Solicitar pieza — " + traducirTipo(prefijo));
            dialog.setHeaderText(headerText);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(460);
            dialog.getDialogPane().setContent(new VBox(8, ta, btnOk));

            btnOk.setOnAction(e -> {
                descripcionAgotado = ta.getText().trim().isEmpty() ? null : ta.getText().trim();
                agotadoConfirmado = true;
                btnMas.setDisable(true);
                btnMenos.setDisable(true);
                chkReutilizado.setDisable(true);
                cbSku.setDisable(true);
                observacion = null;
                lblObservacion.setText("");
                btnObservacion.setVisible(true);  btnObservacion.setManaged(true);
                lblObservacion.setVisible(false);  lblObservacion.setManaged(false);
                btnBorrarObs.setVisible(false);    btnBorrarObs.setManaged(false);
                btnObservacion.setDisable(true);
                solicitudActiva = false;
                solicitudNuevaEnEstaSesion = false;
                descripcionSolicitud = null;
                btnSolicitud.setDisable(true);
                btnSolicitud.setVisible(false);
                btnSolicitud.setManaged(false);
                actualizarLabelConfirmado(stock);
                lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                btnSubAgotado.setVisible(false); btnSubAgotado.setManaged(false);
                btnEditarDesc.setVisible(true);  btnEditarDesc.setManaged(true);
                subFilaAgotado.setStyle("-fx-background-color: #E8F5E9; -fx-padding: 4 8 4 70;");
                dialog.close();
                notificar();
            });

            dialog.showAndWait();
        }

        private void editarDescripcionAgotado() {
            TextArea ta = new TextArea(descripcionAgotado != null ? descripcionAgotado : "");
            ta.setPromptText("Describe la pieza que necesitas (opcional)...");
            ta.setWrapText(true);
            ta.setPrefRowCount(4);
            ta.setPrefWidth(380);
            ta.setStyle("-fx-font-size: 13px;");

            Button btnOk = new Button("Guardar descripción");
            btnOk.setMaxWidth(Double.MAX_VALUE);
            btnOk.setStyle("-fx-background-color: #E8A825; -fx-text-fill: white;" +
                    "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");

            Button btnBorrar = new Button("Cancelar solicitud");
            btnBorrar.setMaxWidth(Double.MAX_VALUE);
            btnBorrar.setStyle("-fx-background-color: #C94040; -fx-text-fill: white;" +
                    "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Editar descripción de solicitud");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(440);
            dialog.getDialogPane().setContent(new VBox(8, ta, btnOk, btnBorrar));

            btnOk.setOnAction(e -> {
                descripcionAgotado = ta.getText().trim().isEmpty() ? null : ta.getText().trim();
                Componente s = cbSku.getValue();
                actualizarLabelConfirmado(s != null ? s.getStock() : 0);
                dialog.close();
            });

            btnBorrar.setOnAction(e -> {
                dialog.close();
                resetearAgotado();
            });

            dialog.showAndWait();
        }

        private void actualizarLabelConfirmado(int stock) {
            String desc = descripcionAgotado != null ? " — " + descripcionAgotado : "";
            if (stock == 0) {
                lblSubAgotado.setText("✓  Solicitud de reposición pendiente" + desc);
            } else {
                lblSubAgotado.setText("✓  " + stock + " uds. se descontarán al guardar — solicitud pendiente" + desc);
            }
        }

        private void resetearAgotado() {
            agotadoConfirmado = false;
            descripcionAgotado = null;
            cantidad = 0;
            actualizarContador();
            chkReutilizado.setDisable(false);
            cbSku.setDisable(false);
            Componente sel = cbSku.getValue();
            if (sel != null && !prefijo.equals("otro"))
                btnMas.setDisable(sel.getStock() <= 0);
            lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A5C00;");
            btnSubAgotado.setVisible(true);  btnSubAgotado.setManaged(true);
            btnEditarDesc.setVisible(false); btnEditarDesc.setManaged(false);
            subFilaAgotado.setStyle("-fx-background-color: #FFF8E0; -fx-padding: 4 8 4 70;");
            btnObservacion.setDisable(false);
            btnSolicitud.setDisable(false);
            actualizarSubFilaAgotado();
            notificar();
        }

        // ── Estado público ────────────────────────────────────────────────────

        boolean isActiva() {
            return cantidad > 0 || chkReutilizado.isSelected() || solicitudNuevaEnEstaSesion || (agotadoConfirmado && !solicitudActiva);
        }

        boolean tieneUso() {
            return cantidad > 0 || chkReutilizado.isSelected();
        }

        int getIdComSeleccionado() {
            return cbSku.getValue() != null ? cbSku.getValue().getIdCom() : -1;
        }

        Componente getComponenteSeleccionado() {
            return cbSku.getValue();
        }

        int getCantidad() {
            return cantidad;
        }

        boolean isReutilizado() {
            return chkReutilizado.isSelected();
        }

        String getObservacion() {
            return observacion;
        }

        String getPrefijo() {
            return prefijo;
        }

        List<Componente> getSkus() {
            return skus;
        }

        boolean isSolicitud() {
            return solicitudActiva;
        }

        /** @return {@code true} si el técnico canceló una solicitud existente en esta sesión */
        boolean isSolicitudCancelada() {
            return solicitudFueCancelada;
        }

        /** @return {@code true} si el técnico marcó una nueva solicitud en esta sesión (no cargada de BD) */
        boolean isSolicitudNueva() {
            return solicitudNuevaEnEstaSesion;
        }

        String getDescripcionSolicitud() {
            return descripcionSolicitud;
        }

        boolean isAgotado() {
            return agotadoConfirmado;
        }

        /**
         * @return {@code true} solo si el agotado se confirmó en esta sesión.
         * Una solicitud cargada de BD ({@code solicitudActiva}) ya está persistida —
         * no debe re-enviarse a {@code agotarComponente} al guardar.
         */
        boolean esAgotadoNuevo() {
            return agotadoConfirmado && !solicitudActiva;
        }

        String getDescripcionAgotado() {
            return descripcionAgotado;
        }

        // ── Borrador: captura / restauración ──────────────────────────────────

        /** Vuelca los inputs no guardados de esta fila al DTO, o null si la fila está vacía. */
        com.reparaciones.models.BorradorContenido.Fila capturarEnBorrador() {
            if (guardada) {
                com.reparaciones.models.BorradorContenido.Fila f = new com.reparaciones.models.BorradorContenido.Fila();
                f.prefijo = prefijo;
                f.idCom = getIdComSeleccionado();
                f.cantidad = cantidad;
                f.reutilizado = isReutilizado();
                f.guardada = true;
                f.idRepGenerado = idRepGenerado;
                f.fechaGuardado = fechaGuardado;
                return f;
            }
            boolean vacia = cantidad == 0 && !isReutilizado()
                    && (observacion == null || observacion.isBlank())
                    && !isSolicitudNueva() && !esAgotadoNuevo();
            if (vacia) return null;
            com.reparaciones.models.BorradorContenido.Fila f = new com.reparaciones.models.BorradorContenido.Fila();
            f.prefijo = prefijo;
            f.idCom = getIdComSeleccionado();
            f.cantidad = cantidad;
            f.reutilizado = isReutilizado();
            f.observacion = observacion;
            f.solicitudNueva = isSolicitudNueva();
            f.descripcionSolicitud = descripcionSolicitud;
            f.agotadoConfirmado = esAgotadoNuevo();
            f.descripcionAgotado = descripcionAgotado;
            return f;
        }

        /** Restaura un borrador sobre esta fila (solo si no está ya bloqueada por una solicitud de BD). */
        void aplicarBorrador(com.reparaciones.models.BorradorContenido.Fila f) {
            if (solicitudActiva) return;   // fila ya gestionada por una solicitud de BD: ignorar borrador
            if (f.guardada) {
                if (f.idCom > 0) preseleccionarSku(f.idCom);
                if (f.cantidad > 0) { cantidad = f.cantidad; actualizarContador(); }
                if (f.reutilizado) chkReutilizado.setSelected(true);
                aplicarGuardada(f.idRepGenerado != null ? f.idRepGenerado : "?",
                                f.fechaGuardado != null ? f.fechaGuardado : "");
                return;
            }
            if (f.idCom > 0) preseleccionarSku(f.idCom);

            // Estados mutuamente excluyentes: solicitud nueva, agotado confirmado, o normal.
            if (f.solicitudNueva && !prefijo.equals("otro")) {
                descripcionSolicitud = f.descripcionSolicitud;
                solicitudActiva = true;
                solicitudNuevaEnEstaSesion = true;
                btnSolicitud.setText("⚠ Pieza pendiente");
                btnSolicitud.setStyle(STYLE_SOL_ACTIVA);
                notificar();
                return;
            }
            if (f.agotadoConfirmado && !prefijo.equals("otro")) {
                Componente sel = cbSku.getValue();
                int stock = sel != null ? sel.getStock() : 0;
                descripcionAgotado = f.descripcionAgotado;
                agotadoConfirmado = true;
                btnMas.setDisable(true); btnMenos.setDisable(true);
                chkReutilizado.setDisable(true);
                cbSku.setDisable(true);
                btnObservacion.setDisable(true);
                btnSolicitud.setDisable(true);
                btnSolicitud.setVisible(false);
                btnSolicitud.setManaged(false);
                actualizarLabelConfirmado(stock);
                lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                btnSubAgotado.setVisible(false); btnSubAgotado.setManaged(false);
                btnEditarDesc.setVisible(true);  btnEditarDesc.setManaged(true);
                subFilaAgotado.setStyle("-fx-background-color: #E8F5E9; -fx-padding: 4 8 4 70;");
                subFilaAgotado.setVisible(true);  subFilaAgotado.setManaged(true);
                notificar();
                return;
            }
            // Normal: cantidad + reutilizado + observación.
            chkReutilizado.setSelected(f.reutilizado);
            cantidad = Math.max(0, f.cantidad);
            actualizarContador();
            if (f.observacion != null && !f.observacion.isBlank()) {
                observacion = f.observacion;
                lblObservacion.setText(observacion);
                btnObservacion.setVisible(false); btnObservacion.setManaged(false);
                lblObservacion.setVisible(true);  lblObservacion.setManaged(true);
                btnBorrarObs.setVisible(true);     btnBorrarObs.setManaged(true);
            }
            notificar();
        }

        private void abrirSolicitud() {
            TextArea ta = new TextArea(descripcionSolicitud != null ? descripcionSolicitud : "");
            ta.setPromptText("Describe la pieza que falta (opcional)...");
            ta.setWrapText(true);
            ta.setPrefRowCount(4);
            ta.setPrefWidth(380);
            ta.setStyle("-fx-font-size: 13px;");

            Button btnGuardar = new Button(solicitudActiva ? "Guardar cambios" : "Marcar como solicitud");
            btnGuardar.setMaxWidth(Double.MAX_VALUE);
            btnGuardar.setStyle("-fx-background-color: #8AC7AF; -fx-text-fill: white;" +
                    "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Solicitud de pieza");
            dialog.setHeaderText("Pieza pendiente: " + traducirTipo(prefijo));
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(440);

            VBox content = new VBox(8, ta, btnGuardar);

            if (solicitudActiva) {
                Button btnCancelarSol = new Button("Cancelar solicitud");
                btnCancelarSol.setMaxWidth(Double.MAX_VALUE);
                btnCancelarSol.setStyle("-fx-background-color: #E05252; -fx-text-fill: white;" +
                        "-fx-font-size: 12px; -fx-padding: 8; -fx-cursor: hand;");
                btnCancelarSol.setOnAction(e -> {
                    solicitudActiva = false;
                    solicitudFueCancelada = true;
                    descripcionSolicitud = null;
                    btnSolicitud.setText("⚠ Solicitud pieza");
                    btnSolicitud.setStyle(STYLE_SOL_INACTIVA);
                    chkReutilizado.setDisable(false);
                    Componente sel = cbSku.getValue();
                    if (sel != null && !prefijo.equals("otro")) {
                        btnMas.setDisable(sel.getStock() <= 0);
                        btnMenos.setDisable(cantidad == 0);
                    }
                    dialog.close();
                    notificar();
                });
                content.getChildren().add(btnCancelarSol);
            }

            dialog.getDialogPane().setContent(content);

            btnGuardar.setOnAction(e -> {
                String trimmed = ta.getText().trim();
                descripcionSolicitud = trimmed.isEmpty() ? null : trimmed;
                solicitudActiva = true;
                solicitudNuevaEnEstaSesion = true;
                btnSolicitud.setText("⚠ Pieza pendiente");
                btnSolicitud.setStyle(STYLE_SOL_ACTIVA);
                dialog.close();
                notificar();
            });

            dialog.showAndWait();
        }

        void deshabilitarYaReparado() {
            cbSku.setDisable(true);
            btnMas.setDisable(true);
            btnMenos.setDisable(true);
            chkReutilizado.setDisable(true);
            btnObservacion.setDisable(true);
            btnSolicitud.setDisable(true);
            Label lblYaReparado = new Label("✓  Ya reparado");
            lblYaReparado.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-padding: 0 10 0 10;");
            mainRow.getChildren().add(lblYaReparado);
            root.setStyle("-fx-background-color: #EBF5EB; " +
                    "-fx-border-color: transparent transparent #C5E1C5 transparent;" +
                    "-fx-border-width: 0 0 1 0;");
        }

        void deshabilitarPorNoImplicada() {
            cbSku.setDisable(true);
            btnMas.setDisable(true);
            btnMenos.setDisable(true);
            chkReutilizado.setDisable(true);
            btnObservacion.setDisable(true);
            btnSolicitud.setDisable(true);
            root.setOpacity(0.4);
        }

        // Estado      | enCamino | Stock>0 | Resultado
        // GESTIONADA  |    —     |   sí    | Desbloqueado — "✓ Recibido" (verde)
        // cualquiera  |   sí     |   no    | Bloqueado    — "⚠ En camino" (azul)
        // PENDIENTE   |   no     |   no    | Bloqueado, sin badge (barra agotado lo indica)
        // RECHAZADA   |    —     |    —    | No se carga (filtrado en SQL)
        void activarSolicitud(int idCom, String descripcion, String estadoSolicitud, boolean enCamino) {
            for (Componente c : skus) {
                if (c.getIdCom() == idCom) {
                    cbSku.setValue(c);
                    if ("GESTIONADA".equals(estadoSolicitud) && c.getStock() > 0) {
                        // Pieza llegó al stock — fila desbloqueada, badge verde informativo
                        solicitudActiva = false;
                        descripcionSolicitud = null;
                        recibidoPendienteUso = true;
                        btnSolicitud.setText("✓ Recibido");
                        btnSolicitud.setStyle(STYLE_SOL_RECIBIDA);
                        btnSolicitud.setDisable(true);
                        btnSolicitud.setVisible(true);
                        btnSolicitud.setManaged(true);
                        notificar();
                    } else {
                        descripcionSolicitud = descripcion;
                        solicitudActiva = true;
                        cantidad = 0;
                        actualizarContador();
                        chkReutilizado.setSelected(false);
                        chkReutilizado.setDisable(enCamino);
                        btnMas.setDisable(true);
                        btnMenos.setDisable(true);
                        // SKU fijado al componente solicitado: al completar con reutilizado el
                        // servidor resuelve la solicitud emparejando por ID_COM exacto.
                        cbSku.setDisable(true);
                        if (enCamino) {
                            btnSolicitud.setText("⚠ En camino");
                            btnSolicitud.setStyle(STYLE_SOL_EN_CAMINO);
                            btnSolicitud.setDisable(true);
                            btnSolicitud.setVisible(true);
                            btnSolicitud.setManaged(true);
                        }
                        // Sub-fila en estado confirmado: solicitud ya registrada en BD
                        String desc = (descripcion != null && !descripcion.isBlank()) ? " — " + descripcion : "";
                        agotadoConfirmado = true;
                        descripcionAgotado = descripcion;
                        lblSubAgotado.setText("✓  Solicitud de reposición pendiente" + desc);
                        lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                        btnSubAgotado.setVisible(false); btnSubAgotado.setManaged(false);
                        btnEditarDesc.setVisible(true);  btnEditarDesc.setManaged(true);
                        subFilaAgotado.setStyle("-fx-background-color: #E8F5E9; -fx-padding: 4 8 4 70;");
                        subFilaAgotado.setVisible(true);
                        subFilaAgotado.setManaged(true);
                        notificar();
                    }
                    return;
                }
            }
        }


        void preseleccionarSku(int idCom) {
            skus.stream()
                .filter(c -> c.getIdCom() == idCom)
                .findFirst()
                .filter(c -> cbSku.getItems().contains(c))
                .ifPresent(cbSku::setValue);
        }

        // ── Modo edición ──────────────────────────────────────────────────────

        void activarModoEdicion(int idCom, boolean esReutilizado, String observacion, int cantOrig) {
            this.modoEdicion = true;
            this.idComOriginal = idCom;
            this.cantidadOriginal = cantOrig;
            this.esReutilizadoOriginal = esReutilizado;
            this.observacionOriginal = observacion;

            // Pre-seleccionar componente
            skus.stream().filter(c -> c.getIdCom() == idCom).findFirst()
                    .ifPresent(cbSku::setValue);

            cantidad = cantOrig;
            Componente sel = cbSku.getValue();
            int stock = sel != null ? sel.getStock() : 0;
            btnMas.setDisable(!prefijo.equals("otro") && stock <= 0);
            chkReutilizado.setSelected(esReutilizado);
            if (esReutilizado) {
                btnMas.setDisable(true);
                btnMenos.setDisable(true);
            } else if (cantOrig > 0) {
                chkReutilizado.setDisable(true);
            }
            actualizarContador();

            // Pre-rellenar observación
            this.observacion = observacion;
            if (observacion != null && !observacion.isBlank()) {
                lblObservacion.setText(observacion);
                btnObservacion.setVisible(false);
                btnObservacion.setManaged(false);
                lblObservacion.setVisible(true);
                lblObservacion.setManaged(true);
                btnBorrarObs.setVisible(true);
                btnBorrarObs.setManaged(true);
            }

            // Ocultar solicitud en modo edición
            btnSolicitud.setVisible(false);
            btnSolicitud.setManaged(false);

            // Fondo azul
            root.setStyle("-fx-background-color: #EBF4FF; " +
                    "-fx-border-color: transparent transparent #B3D4F5 transparent;" +
                    "-fx-border-width: 0 0 1 0;");
        }

        // ── Técnico por fila (solo modo edición, filas grises nuevas) ────────

        private ComboBox<Tecnico> cbTecnicoFila = null;
        private int idTecFila = -1;

        void configurarTecnicoEdicion(List<Tecnico> tecnicos, int idTecDefault) {
            btnSolicitud.setVisible(false);
            btnSolicitud.setManaged(false);
            idTecFila = idTecDefault;
            // Combo de técnico OCULTO por ahora. Editar el técnico de la reparación
            // existente queda como mejora futura (requiere cambio en servidor).
            // Los componentes nuevos añadidos en edición usan el técnico original (idTecDefault).
        }

        int getTecnicoId() { return idTecFila; }

        boolean isModoEdicion() { return modoEdicion; }

        boolean hayCambio() {
            if (!modoEdicion) return false;
            Componente sel = cbSku.getValue();
            int idComActual = sel != null ? sel.getIdCom() : -1;
            return cantidad != cantidadOriginal
                    || idComActual != idComOriginal
                    || chkReutilizado.isSelected() != esReutilizadoOriginal
                    || !Objects.equals(observacion, observacionOriginal);
        }

        VBox getRoot() {
            return root;
        }

        void setOnCambio(Runnable r) {
            this.onCambio = r;
        }

        private void manejarClicGuardarFila() {
            if (!esperandoConfGuardar) {
                esperandoConfGuardar = true;
                btnSolicitud.setText("✓ Confirmar");
            } else {
                esperandoConfGuardar = false;
                if (onGuardarFila != null) onGuardarFila.run();
            }
        }

        void setOnGuardarFila(Runnable r) { this.onGuardarFila = r; }
        void setFormularioEnEdicion(boolean b) { this.formularioEnEdicion = b; }
        boolean isGuardada() { return guardada; }
        String getIdRepGenerado() { return idRepGenerado; }

        void setGuardandoEnCurso(boolean enCurso) {
            btnSolicitud.setDisable(enCurso);
        }

        /** Bloquea la fila permanentemente con el badge "✓ Guardada [fecha]". */
        void aplicarGuardada(String idRep, String fecha) {
            guardada = true;
            idRepGenerado = idRep;
            fechaGuardado = fecha;
            recibidoPendienteUso = false;
            cbSku.setDisable(true);
            btnMas.setDisable(true);
            btnMenos.setDisable(true);
            chkReutilizado.setDisable(true);
            btnObservacion.setDisable(true);
            btnSolicitud.setText("✓ Guardada " + fecha);
            btnSolicitud.setStyle(STYLE_SOL_RECIBIDA);
            btnSolicitud.setDisable(true);
            btnSolicitud.setOnAction(null);
            btnSolicitud.setVisible(true);
            btnSolicitud.setManaged(true);
            root.setStyle("-fx-background-color: #F1F8F1; " +
                    "-fx-border-color: transparent transparent #C5E1C5 transparent;" +
                    "-fx-border-width: 0 0 1 0;");
        }

        /** Restaura la fila a estado editable cuando la R* guardada fue borrada por SUPERTECNICO. */
        void desbloquearTrasEliminacion() {
            guardada = false;
            idRepGenerado = null;
            fechaGuardado = null;
            cantidad = 0;
            chkReutilizado.setSelected(false);
            observacion = null;
            lblObservacion.setText("");
            btnObservacion.setVisible(true);  btnObservacion.setManaged(true);
            lblObservacion.setVisible(false); lblObservacion.setManaged(false);
            btnBorrarObs.setVisible(false);   btnBorrarObs.setManaged(false);
            cbSku.setDisable(false);
            Componente sel = cbSku.getValue();
            btnMas.setDisable(!prefijo.equals("otro") && (sel == null || sel.getStock() <= 0));
            chkReutilizado.setDisable(false);
            btnObservacion.setDisable(false);
            btnSolicitud.setOnAction(e -> abrirSolicitud());
            btnSolicitud.setVisible(false); btnSolicitud.setManaged(false);
            root.setStyle("-fx-background-color: #F3F3F3; " +
                    "-fx-border-color: transparent transparent #E0E0E0 transparent;" +
                    "-fx-border-width: 0 0 1 0;");
            actualizarContador();
            notificar();
        }

        void actualizarBotonGuardarFila() {
            if (guardada || modoEdicion || formularioEnEdicion || solicitudActiva) return;
            boolean activa = isActiva() && !esAgotadoNuevo();
            if (activa) {
                recibidoPendienteUso = false;
                esperandoConfGuardar = false;
                btnSolicitud.setText("✓ Guardar fila");
                btnSolicitud.setStyle(STYLE_BTN_GUARDAR_FILA);
                btnSolicitud.setDisable(false);
                btnSolicitud.setOnAction(e -> manejarClicGuardarFila());
                btnSolicitud.setVisible(true);
                btnSolicitud.setManaged(true);
            } else if (!recibidoPendienteUso) {
                esperandoConfGuardar = false;
                btnSolicitud.setVisible(false);
                btnSolicitud.setManaged(false);
            }
        }
    }

    // ─── OtrasAccionesUI ──────────────────────────────────────────────────────
    /** Sección de "Otras acciones": lista de acciones de texto libre con guardado individual.
     *  Cada acción se guarda como un R* con el componente otroi<modelo> y cantidad 0. */
    static class OtrasAccionesUI {
        private final VBox root;
        private final VBox listaLineas = new VBox(5);
        private final Label badge = new Label("0");
        private final List<Componente> otroComponentes;
        private final Image imgBorrar;
        private Componente otroSel = null;
        private Runnable onCambio;
        private Button btnAdd;
        private final List<LineaAccion> lineas = new ArrayList<>();
        private Function<String, String> guardador;

        OtrasAccionesUI(List<Componente> otroComponentes, Image imgBorrar) {
            this.otroComponentes = otroComponentes;
            this.imgBorrar = imgBorrar;

            Label titulo = new Label("OTRAS ACCIONES");
            titulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
            badge.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 10px;" +
                    "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;");
            HBox header = new HBox(8, titulo, badge);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-padding: 8 14 2 14;");

            javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(listaLineas);
            scroll.setFitToWidth(true);
            scroll.setMaxHeight(150);
            scroll.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6;");
            listaLineas.setStyle("-fx-padding: 5;");

            btnAdd = new Button("+ Añadir acción");
            btnAdd.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 11.5px;" +
                    "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12 6 12;");
            btnAdd.setOnAction(e -> agregarLinea(""));

            VBox cuerpo = new VBox(8, scroll, btnAdd);
            cuerpo.setStyle("-fx-padding: 2 14 12 14;");

            root = new VBox(header, cuerpo);
            root.setStyle("-fx-background-color: #F6F7F9;" +
                    "-fx-border-color: transparent transparent transparent #2C3B54; -fx-border-width: 0 0 0 4;");
            root.setVisible(false); root.setManaged(false);
        }

        /** Registra el callback del controlador que persiste una acción y devuelve su idRep. */
        void setGuardador(Function<String, String> g) { this.guardador = g; }

        /** Selecciona el otroi<modelo> según el modelo elegido; muestra la sección si existe. */
        void setModelo(String modelo) {
            otroSel = (modelo == null) ? null : otroComponentes.stream()
                    .filter(c -> extraerModelo(c.getTipo(), "otro").equals(modelo))
                    .findFirst().orElse(null);
            boolean disponible = otroSel != null;
            root.setVisible(disponible); root.setManaged(disponible);
        }

        private void agregarLinea(String texto) {
            if (hayLineaVacia()) return;
            LineaAccion la = new LineaAccion();
            la.tf = new TextField(texto);
            la.tf.setPromptText("Describe la acción");
            la.tf.setStyle("-fx-font-size: 12px; -fx-background-color: white;" +
                    "-fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-background-radius: 4;");
            HBox.setHgrow(la.tf, Priority.ALWAYS);

            ImageView iv = new ImageView(imgBorrar);
            iv.setFitWidth(18); iv.setFitHeight(18); iv.setPreserveRatio(true);
            la.btnDel = new Button();
            la.btnDel.setGraphic(iv);
            la.btnDel.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");

            la.btnGuardar = new Button("✓ Guardar");
            la.btnGuardar.setStyle("-fx-background-color: #001232; -fx-text-fill: white; -fx-font-weight: bold;" +
                    "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10 4 10;");
            la.btnGuardar.setDisable(texto.trim().isEmpty());

            la.row = new HBox(8, la.tf, la.btnGuardar, la.btnDel);
            la.row.setAlignment(Pos.CENTER_LEFT);

            la.btnDel.setOnAction(e -> {
                listaLineas.getChildren().remove(la.row);
                lineas.remove(la);
                actualizar();
            });
            la.tf.textProperty().addListener((o, a, b) -> {
                if (!la.guardada) {
                    la.btnGuardar.setDisable(b == null || b.trim().isEmpty());
                    if (la.esperandoConf) {
                        la.esperandoConf = false;
                        la.btnGuardar.setText("✓ Guardar");
                    }
                }
                actualizar();
            });
            la.btnGuardar.setOnAction(e -> guardarLinea(la));

            lineas.add(la);
            listaLineas.getChildren().add(la.row);
            la.tf.requestFocus();
            actualizar();
        }

        private void guardarLinea(LineaAccion la) {
            if (guardador == null) return;
            String texto = la.tf.getText() == null ? "" : la.tf.getText().trim();
            if (texto.isEmpty()) return;
            if (!la.esperandoConf) {
                la.esperandoConf = true;
                la.btnGuardar.setText("✓ Confirmar");
                return;
            }
            la.esperandoConf = false;
            la.btnGuardar.setDisable(true);
            String idRep = guardador.apply(texto);
            if (idRep == null) {
                la.btnGuardar.setDisable(false);
                return;
            }
            la.guardada = true;
            la.idRepGenerado = idRep;
            la.fechaGuardado = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            bloquearLinea(la);
            actualizar();
        }

        private void bloquearLinea(LineaAccion la) {
            la.tf.setDisable(true);
            la.btnGuardar.setVisible(false); la.btnGuardar.setManaged(false);
            la.btnDel.setVisible(false); la.btnDel.setManaged(false);
            Label lbl = new Label("✓ Guardada " + la.fechaGuardado);
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;" +
                    "-fx-padding: 0 4 0 4;");
            la.lblGuardada = lbl;
            la.row.getChildren().add(lbl);
        }

        private void desbloquearLinea(LineaAccion la) {
            la.guardada = false;
            la.idRepGenerado = null;
            la.fechaGuardado = null;
            la.tf.setDisable(false);
            if (la.lblGuardada != null) {
                la.row.getChildren().remove(la.lblGuardada);
                la.lblGuardada = null;
            }
            la.btnGuardar.setVisible(true); la.btnGuardar.setManaged(true);
            la.btnGuardar.setDisable(la.tf.getText() == null || la.tf.getText().trim().isEmpty());
            la.btnDel.setVisible(true); la.btnDel.setManaged(true);
        }

        private void actualizar() {
            long total = lineas.stream()
                    .filter(l -> l.guardada || (l.tf.getText() != null && !l.tf.getText().trim().isEmpty()))
                    .count();
            badge.setText(String.valueOf(total));
            btnAdd.setDisable(hayLineaVacia());
            if (onCambio != null) onCambio.run();
        }

        private boolean hayLineaVacia() {
            return lineas.stream().anyMatch(l -> !l.guardada
                    && (l.tf.getText() == null || l.tf.getText().trim().isEmpty()));
        }

        /** Descripciones pendientes (no guardadas individualmente) para el guardado final. */
        List<String> getDescripcionesPendientes() {
            List<String> out = new ArrayList<>();
            for (LineaAccion l : lineas) {
                if (l.guardada) continue;
                String t = l.tf.getText() == null ? "" : l.tf.getText().trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        /** Vuelca el estado completo de cada línea (guardada o pendiente) al borrador. */
        List<com.reparaciones.models.BorradorContenido.OtraAccion> capturarAcciones() {
            List<com.reparaciones.models.BorradorContenido.OtraAccion> out = new ArrayList<>();
            for (LineaAccion l : lineas) {
                String t = l.tf.getText() == null ? "" : l.tf.getText().trim();
                if (t.isEmpty() && !l.guardada) continue;
                com.reparaciones.models.BorradorContenido.OtraAccion a =
                        new com.reparaciones.models.BorradorContenido.OtraAccion();
                a.descripcion = t;
                a.guardada = l.guardada;
                a.idRepGenerado = l.idRepGenerado;
                a.fechaGuardado = l.fechaGuardado;
                out.add(a);
            }
            return out;
        }

        /** Restaura las acciones de un borrador: guardadas bloqueadas, pendientes editables. */
        void aplicarAcciones(List<com.reparaciones.models.BorradorContenido.OtraAccion> acciones) {
            if (acciones == null) return;
            for (com.reparaciones.models.BorradorContenido.OtraAccion a : acciones) {
                if (a == null || a.descripcion == null || a.descripcion.isBlank()) continue;
                agregarLinea(a.descripcion.trim());
                if (a.guardada) {
                    LineaAccion la = lineas.get(lineas.size() - 1);
                    la.guardada = true;
                    la.idRepGenerado = a.idRepGenerado;
                    la.fechaGuardado = a.fechaGuardado != null ? a.fechaGuardado : "";
                    bloquearLinea(la);
                }
            }
            actualizar();
        }

        /** @return true si alguna línea guardada fue desbloqueada al no existir en BD */
        boolean desbloquearEliminadas(Set<String> idsExistentes) {
            boolean cambio = false;
            for (LineaAccion l : lineas) {
                if (l.guardada && !idsExistentes.contains(l.idRepGenerado)) {
                    desbloquearLinea(l);
                    cambio = true;
                }
            }
            if (cambio) actualizar();
            return cambio;
        }

        int getIdComOtro() { return otroSel != null ? otroSel.getIdCom() : -1; }
        boolean hayAccion() { return otroSel != null && !getDescripcionesPendientes().isEmpty(); }
        boolean hayAccionGuardada() { return lineas.stream().anyMatch(l -> l.guardada); }
        boolean esOtro(int idCom) { return otroComponentes.stream().anyMatch(c -> c.getIdCom() == idCom); }
        void setOnCambio(Runnable r) { this.onCambio = r; }
        VBox getRoot() { return root; }

        private static class LineaAccion {
            TextField tf;
            HBox row;
            Button btnDel;
            Button btnGuardar;
            Label lblGuardada;
            boolean guardada = false;
            boolean esperandoConf = false;
            String idRepGenerado;
            String fechaGuardado;
        }
    }
}