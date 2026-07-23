package com.reparaciones.controllers;

import com.reparaciones.Sesion;
import com.reparaciones.dao.ClienteDAO;
import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.Cliente;
import com.reparaciones.models.RevisionTelefono;
import com.reparaciones.models.TelefonoInventario;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.Colores;
import com.reparaciones.utils.ConfirmDialog;
import com.reparaciones.utils.SelectorClienteDialog;
import com.reparaciones.utils.StaleDataException;
import com.reparaciones.utils.UbicacionTexto;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F2b: ficha de revisión de un teléfono (spec §5, layout A aprobado en brainstorm).
 * <p>Dos columnas independientes — estética y funcional — cada una con su propio botón
 * "Guardar" y chip de estado ("guardada · autor · fecha" / "pendiente"); cada parte se
 * guarda sola, sin exigir la otra. Debajo de las columnas, {@link #zonaVeredicto} queda
 * vacía para que T9 la pueble con el banner de veredicto; en el pie, los botones de
 * acción ({@link #btnOk}, {@link #btnAsignar}, {@link #btnBloquear},
 * {@link #btnDesbloquear}, {@link #btnDesguace}) se crean deshabilitados — T9 los
 * cablea.</p>
 * <p>Editable solo si {@code Sesion.esSuperTecnico() && "EN_REVISION".equals(t.getEstado())};
 * en cualquier otro caso todos los controles quedan deshabilitados (consulta).</p>
 */
public final class FichaRevisionDialog {

    private static final DateTimeFormatter FMT_CHIP = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private static final String ESTILO_CHIP_VERDE =
            "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32; -fx-font-size: 11px;" +
            "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;";
    private static final String ESTILO_CHIP_AMBAR =
            "-fx-background-color: " + Colores.FILA_SOLICITUD_BG + "; -fx-text-fill: " + Colores.FILA_SOLICITUD_BRD + ";" +
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;";
    private static final String ESTILO_COLUMNA =
            "-fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: white;";
    private static final String ESTILO_INPUT =
            "-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 6; -fx-text-fill: #2C3B54; -fx-font-size: 12px;";

    private final TelefonoDAO dao = new TelefonoDAO();
    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final TelefonoInventario t;
    private final Runnable onCambios;
    private final boolean editable;

    private final List<Node> controlesEditables = new ArrayList<>();

    private Stage ventana;
    private boolean huboCambios = false;
    private boolean guardandoEst = false;
    private boolean guardandoFun = false;

    /** Revisión vigente cargada del servidor; accesible para T9 (veredicto). */
    RevisionTelefono revisionActual;

    private Label lblAviso;

    // ── Columna estética ────────────────────────────────────────────────────
    private Label lblChipEst;
    private ToggleGroup grupoGrado;
    private ToggleGroup grupoPant;
    private Button btnGuardarEst;

    // ── Columna funcional ───────────────────────────────────────────────────
    private Label lblChipFun;
    private TextField tfBateria;
    private CheckBox chkPantTactil, chkPantQuemada, chkPantMal;
    private CheckBox chkCamMancha, chkCamLente;
    private CheckBox chkAltSup, chkAltInf, chkMic;
    private CheckBox chkFaceId;
    private CheckBox chkMs;
    private TextField tfMsTexto;
    private CheckBox chkBloqueoOp;
    private TextField tfObservacion;
    private Button btnGuardarFun;

    // ── Pie ──────────────────────────────────────────────────────────────────
    private Hyperlink lnkCliente;

    /** Zona vacía bajo las columnas; T9 la puebla con el banner de veredicto. */
    VBox zonaVeredicto;

    /** Acciones de estado; creados deshabilitados aquí, cableados en T9. */
    Button btnOk, btnAsignar, btnBloquear, btnDesbloquear, btnDesguace;

    private FichaRevisionDialog(TelefonoInventario t, Runnable onCambios) {
        this.t = t;
        this.onCambios = onCambios;
        this.editable = Sesion.esSuperTecnico() && "EN_REVISION".equals(t.getEstado());
    }

    /**
     * Abre la ficha de revisión del teléfono dado.
     *
     * @param owner     ventana propietaria del diálogo modal (puede ser {@code null})
     * @param t         fila de inventario/cola sobre la que se abre la ficha
     * @param onCambios se ejecuta en el hilo de JavaFX al cerrar, si hubo algún guardado
     */
    public static void abrir(Window owner, TelefonoInventario t, Runnable onCambios) {
        new FichaRevisionDialog(t, onCambios).mostrar(owner);
    }

    // ── Construcción ─────────────────────────────────────────────────────────

    private void mostrar(Window owner) {
        Label cabecera = construirCabecera();

        lblAviso = new Label();
        lblAviso.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.TEXTO_ERROR + "; -fx-font-weight: bold;");
        lblAviso.setVisible(false);
        lblAviso.setManaged(false);

        VBox colEstetica = construirColumnaEstetica();
        VBox colFuncional = construirColumnaFuncional();
        HBox columnas = new HBox(16, colEstetica, colFuncional);
        HBox.setHgrow(colEstetica, Priority.ALWAYS);
        HBox.setHgrow(colFuncional, Priority.ALWAYS);

        zonaVeredicto = new VBox();

        HBox pie = construirPie();

        VBox contenido = new VBox(14, cabecera, lblAviso, columnas, zonaVeredicto, pie);
        contenido.setPadding(new Insets(20));
        contenido.setPrefWidth(760);
        contenido.setStyle("-fx-background-color: #DDE1E7;");

        // Deshabilitado hasta que termine la carga inicial (evita interacción con datos a medio poblar).
        controlesEditables.forEach(n -> n.setDisable(true));

        ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("Ficha de revisión — " + t.getImei());
        ventana.setOnHidden(ev -> { if (huboCambios && onCambios != null) onCambios.run(); });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(FichaRevisionDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);

        cargar();
        ventana.showAndWait();
    }

    /** Cabecera de una sola línea: modelo(+eSIM) · storage · color · grado prov. · lote (proveedor) · estado. */
    private Label construirCabecera() {
        List<String> partes = new ArrayList<>();
        if (t.getModelo() != null) partes.add(t.getModelo() + (t.isEsEsim() ? " eSIM" : ""));
        if (t.getStorageGb() != null) partes.add(t.getStorageGb() + " GB");
        if (t.getColor() != null) partes.add(t.getColor());
        if (t.getGradoProveedor() != null) partes.add("Grado prov. " + t.getGradoProveedor());
        if (t.getBatchNumber() != null) {
            String lote = "Lote " + t.getBatchNumber();
            if (t.getProveedor() != null) lote += " (" + t.getProveedor() + ")";
            partes.add(lote);
        }
        partes.add("Estado: " + UbicacionTexto.estado(t));

        Label lbl = new Label(String.join(" · ", partes));
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Colores.AZUL_MEDIO + "; -fx-font-weight: bold;" +
                "-fx-background-color: #F1F5F9; -fx-padding: 8 12 8 12; -fx-background-radius: 4;");
        lbl.setWrapText(true);
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private VBox construirColumnaEstetica() {
        Label lblTitulo = new Label("Estética");
        lblTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
        lblChipEst = new Label("pendiente");
        lblChipEst.setStyle(ESTILO_CHIP_AMBAR);
        HBox filaTitulo = new HBox(8, lblTitulo, lblChipEst);
        filaTitulo.setAlignment(Pos.CENTER_LEFT);

        ToggleButton tbGradoC = new ToggleButton("C");
        ToggleButton tbGradoB = new ToggleButton("B");
        ToggleButton tbGradoAmenos = new ToggleButton("A-");
        ToggleButton tbGradoA = new ToggleButton("A");
        tbGradoC.getStyleClass().add("toggle-pill-left");
        tbGradoB.getStyleClass().add("toggle-pill-mid");
        tbGradoAmenos.getStyleClass().add("toggle-pill-mid");
        tbGradoA.getStyleClass().add("toggle-pill-right");
        tbGradoC.setUserData("C");
        tbGradoB.setUserData("B");
        tbGradoAmenos.setUserData("A-");
        tbGradoA.setUserData("A");
        grupoGrado = new ToggleGroup();
        for (ToggleButton tb : new ToggleButton[]{tbGradoC, tbGradoB, tbGradoAmenos, tbGradoA}) {
            tb.setToggleGroup(grupoGrado);
            hacerDeseleccionable(tb, grupoGrado);
            controlesEditables.add(tb);
        }
        HBox filaGradoToggles = new HBox(tbGradoC, tbGradoB, tbGradoAmenos, tbGradoA);
        Label lblGrado = new Label("Grado");
        lblGrado.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
        HBox filaGrado = new HBox(8, lblGrado, filaGradoToggles);
        filaGrado.setAlignment(Pos.CENTER_LEFT);

        ToggleButton tbPantNo = new ToggleButton("—");
        ToggleButton tbPantP = new ToggleButton("P");
        ToggleButton tbPantG = new ToggleButton("G");
        tbPantNo.getStyleClass().add("toggle-pill-left");
        tbPantP.getStyleClass().add("toggle-pill-mid");
        tbPantG.getStyleClass().add("toggle-pill-right");
        tbPantNo.setUserData(null); // "—" ≡ null
        tbPantP.setUserData("P");
        tbPantG.setUserData("G");
        grupoPant = new ToggleGroup();
        for (ToggleButton tb : new ToggleButton[]{tbPantNo, tbPantP, tbPantG}) {
            tb.setToggleGroup(grupoPant);
            controlesEditables.add(tb);
        }
        tbPantNo.setSelected(true);
        HBox filaPantToggles = new HBox(tbPantNo, tbPantP, tbPantG);
        Label lblPant = new Label("PANT");
        lblPant.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
        HBox filaPant = new HBox(8, lblPant, filaPantToggles);
        filaPant.setAlignment(Pos.CENTER_LEFT);

        btnGuardarEst = new Button("Guardar estética");
        btnGuardarEst.getStyleClass().add("btn-primary");
        btnGuardarEst.setOnAction(e -> guardarEstetica());
        controlesEditables.add(btnGuardarEst);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        HBox filaBoton = new HBox(btnGuardarEst);
        filaBoton.setAlignment(Pos.BOTTOM_RIGHT);

        VBox col = new VBox(10, filaTitulo, filaGrado, filaPant, spacer, filaBoton);
        col.setStyle(ESTILO_COLUMNA);
        col.setPadding(new Insets(12));
        return col;
    }

    private VBox construirColumnaFuncional() {
        Label lblTitulo = new Label("Funcional");
        lblTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
        lblChipFun = new Label("pendiente");
        lblChipFun.setStyle(ESTILO_CHIP_AMBAR);
        HBox filaTitulo = new HBox(8, lblTitulo, lblChipFun);
        filaTitulo.setAlignment(Pos.CENTER_LEFT);

        Label lblBateria = new Label("Batería");
        lblBateria.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
        tfBateria = new TextField();
        tfBateria.setPrefWidth(50);
        tfBateria.setStyle(ESTILO_INPUT);
        tfBateria.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) { tfBateria.setText(n.replaceAll("[^\\d]", "")); return; }
            if (n.length() > 3) tfBateria.setText(n.substring(0, 3));
        });
        HBox filaBateria = new HBox(8, lblBateria, tfBateria, new Label("%"));
        filaBateria.setAlignment(Pos.CENTER_LEFT);
        controlesEditables.add(tfBateria);

        chkPantTactil = new CheckBox("Táctil");
        chkPantQuemada = new CheckBox("Quemada");
        chkPantMal = new CheckBox("Mal");
        HBox filaPantalla = new HBox(10, etiqueta("Pantalla"), chkPantTactil, chkPantQuemada, chkPantMal);
        filaPantalla.setAlignment(Pos.CENTER_LEFT);

        chkCamMancha = new CheckBox("Mancha");
        chkCamLente = new CheckBox("Lente");
        HBox filaCamara = new HBox(10, etiqueta("Cámara"), chkCamMancha, chkCamLente);
        filaCamara.setAlignment(Pos.CENTER_LEFT);

        chkAltSup = new CheckBox("Alt. sup");
        chkAltInf = new CheckBox("Alt. inf");
        chkMic = new CheckBox("Micro");
        HBox filaSonido = new HBox(10, etiqueta("Sonido"), chkAltSup, chkAltInf, chkMic);
        filaSonido.setAlignment(Pos.CENTER_LEFT);

        chkFaceId = new CheckBox("Face ID");
        chkMs = new CheckBox("MS");
        tfMsTexto = new TextField();
        tfMsTexto.setPromptText("Texto MS");
        tfMsTexto.setStyle(ESTILO_INPUT);
        tfMsTexto.setDisable(true); // habilitado solo con MS marcado (y ficha editable)
        chkMs.selectedProperty().addListener((obs, o, sel) -> tfMsTexto.setDisable(!sel || !editable));
        HBox filaFaceMs = new HBox(10, chkFaceId, chkMs, tfMsTexto);
        filaFaceMs.setAlignment(Pos.CENTER_LEFT);

        chkBloqueoOp = new CheckBox("Bloqueo operador");
        HBox filaBloqueo = new HBox(chkBloqueoOp);

        Label lblObs = etiqueta("Observ.");
        tfObservacion = new TextField();
        tfObservacion.setStyle(ESTILO_INPUT);
        HBox.setHgrow(tfObservacion, Priority.ALWAYS);
        HBox filaObs = new HBox(8, lblObs, tfObservacion);
        filaObs.setAlignment(Pos.CENTER_LEFT);

        for (CheckBox c : new CheckBox[]{chkPantTactil, chkPantQuemada, chkPantMal, chkCamMancha, chkCamLente,
                chkAltSup, chkAltInf, chkMic, chkFaceId, chkMs, chkBloqueoOp}) {
            controlesEditables.add(c);
        }
        controlesEditables.add(tfMsTexto);
        controlesEditables.add(tfObservacion);

        btnGuardarFun = new Button("Guardar funcional");
        btnGuardarFun.getStyleClass().add("btn-primary");
        btnGuardarFun.setOnAction(e -> guardarFuncional());
        controlesEditables.add(btnGuardarFun);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        HBox filaBoton = new HBox(btnGuardarFun);
        filaBoton.setAlignment(Pos.BOTTOM_RIGHT);

        VBox col = new VBox(8, filaTitulo, filaBateria, filaPantalla, filaCamara, filaSonido, filaFaceMs,
                filaBloqueo, filaObs, spacer, filaBoton);
        col.setStyle(ESTILO_COLUMNA);
        col.setPadding(new Insets(12));
        return col;
    }

    private Label etiqueta(String texto) {
        Label lbl = new Label(texto);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
        return lbl;
    }

    private HBox construirPie() {
        Label lblCliente = new Label("Cliente:");
        lblCliente.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Colores.AZUL_GRIS + "; -fx-font-weight: bold;");
        lnkCliente = new Hyperlink(t.getCliente() != null ? t.getCliente() : "— sin cliente —");
        lnkCliente.setOnAction(e -> editarCliente());
        controlesEditables.add(lnkCliente);

        Region spacerPie = new Region();
        HBox.setHgrow(spacerPie, Priority.ALWAYS);

        btnOk = new Button("Marcar OK");
        btnOk.setStyle("-fx-background-color: " + Colores.VERDE_OK + "; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-background-radius: 24; -fx-font-size: 12px; -fx-padding: 8 16 8 16; -fx-cursor: hand;");
        btnOk.setDisable(true);

        btnAsignar = new Button("Asignar trabajos…");
        btnAsignar.getStyleClass().add("btn-secondary");
        btnAsignar.setDisable(true);

        btnBloquear = new Button("Bloquear");
        btnBloquear.getStyleClass().add("btn-secondary");
        btnBloquear.setDisable(true);

        btnDesbloquear = new Button("Desbloquear");
        btnDesbloquear.getStyleClass().add("btn-secondary");
        btnDesbloquear.setDisable(true);

        btnDesguace = new Button("Desguace");
        btnDesguace.getStyleClass().add("btn-secondary");
        btnDesguace.setDisable(true);

        HBox pie = new HBox(10, lblCliente, lnkCliente, spacerPie,
                btnOk, btnAsignar, btnBloquear, btnDesbloquear, btnDesguace);
        pie.setAlignment(Pos.CENTER_LEFT);
        return pie;
    }

    /** Permite deseleccionar un {@link ToggleButton} de un {@link ToggleGroup} haciendo clic sobre él ya seleccionado. */
    private void hacerDeseleccionable(ToggleButton btn, ToggleGroup grupo) {
        btn.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (btn.isSelected()) {
                e.consume();
                grupo.selectToggle(null);
            }
        });
    }

    // ── Cliente (pie) — calco de AgrupadoController#editarCli (L871-887) ────

    private void editarCliente() {
        try {
            List<Cliente> activos = clienteDAO.getActivos();
            Integer idActual = activos.stream()
                    .filter(c -> c.getNombre().equals(t.getCliente()))
                    .map(Cliente::getIdCli).findFirst().orElse(null);
            Optional<Integer> sel = SelectorClienteDialog.elegir(activos, idActual);
            if (sel.isEmpty()) return;
            Integer idCli = (sel.get() == -1) ? null : sel.get();
            dao.actualizarCliente(t.getImei(), idCli, t.getTelefonoUpdatedAt());
            String nombreNuevo = (idCli == null) ? null : activos.stream()
                    .filter(c -> c.getIdCli() == idCli).map(Cliente::getNombre).findFirst().orElse(null);
            t.setCliente(nombreNuevo);
            lnkCliente.setText(nombreNuevo != null ? nombreNuevo : "— sin cliente —");
            huboCambios = true;
            refrescarTelefonoUpdatedAt();
        } catch (StaleDataException ex) {
            // No hay aquí un "cargar()" de tabla al que recargar: se avisa y se cierra la ficha
            // para que el llamador refresque con datos frescos (onCambios, vía setOnHidden).
            Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
            huboCambios = true;
            ventana.close();
        } catch (SQLException ex) {
            Alertas.mostrarError(ex.getMessage());
        }
    }

    /**
     * Tras reasignar cliente, el servidor bumpa {@code Telefono.UPDATED_AT}
     * (ON UPDATE CURRENT_TIMESTAMP), pero la copia local de {@code t} no se entera solo
     * con {@code setCliente}. Sin refrescarla, una segunda reasignación en la misma
     * ficha abierta enviaría el timestamp caducado y provocaría un 409 falso (cierre de
     * la ficha por "StaleDataException" sin que nadie más la haya tocado). Se relee el
     * inventario en segundo plano y se copia la fila fresca sobre {@code t}; si el IMEI
     * no aparece (caso raro), se deja el estado actual — un conflicto real se verá en el
     * siguiente guardado, legítimamente.
     */
    private void refrescarTelefonoUpdatedAt() {
        String imei = t.getImei();
        new Thread(() -> {
            List<TelefonoInventario> inventario;
            try {
                inventario = dao.getInventario();
            } catch (SQLException ex) {
                return;
            }
            TelefonoInventario fresco = inventario.stream()
                    .filter(ti -> imei.equals(ti.getImei()))
                    .findFirst().orElse(null);
            if (fresco == null) return;
            Platform.runLater(() -> {
                t.setTelefonoUpdatedAt(fresco.getTelefonoUpdatedAt());
                t.setCliente(fresco.getCliente());
                lnkCliente.setText(fresco.getCliente() != null ? fresco.getCliente() : "— sin cliente —");
            });
        }, "refrescar-updated-at").start();
    }

    // ── Carga inicial ─────────────────────────────────────────────────────────

    private void cargar() {
        new Thread(() -> {
            RevisionTelefono r = null;
            String error = null;
            try {
                r = dao.getRevision(t.getImei());
            } catch (SQLException e) {
                error = e.getMessage();
            }
            RevisionTelefono rCargada = r;
            String errFinal = error;
            Platform.runLater(() -> {
                if (errFinal != null) Alertas.mostrarError(errFinal);
                boolean sinRevision = (rCargada == null);
                revisionActual = (rCargada != null) ? rCargada : new RevisionTelefono();
                poblarDesdeRevision();
                recargarChips();
                // Null solo debería darse consultando un teléfono que nunca pasó por revisión
                // (si estuviera EN_REVISION, la fila Revision ya existe desde el escaneo).
                if (sinRevision && !"EN_REVISION".equals(t.getEstado())) {
                    lblAviso.setText("Sin revisión");
                    lblAviso.setVisible(true);
                    lblAviso.setManaged(true);
                }
                aplicarEditable();
            });
        }, "cargar-revision").start();
    }

    private void poblarDesdeRevision() {
        seleccionarGrado(revisionActual.getEstGrado());
        seleccionarPant(revisionActual.getEstPant());

        Integer bat = revisionActual.getFunBateriaPct();
        tfBateria.setText(bat != null ? String.valueOf(bat) : "");
        chkPantTactil.setSelected(revisionActual.isFunPantTactil());
        chkPantQuemada.setSelected(revisionActual.isFunPantQuemada());
        chkPantMal.setSelected(revisionActual.isFunPantMal());
        chkCamMancha.setSelected(revisionActual.isFunCamMancha());
        chkCamLente.setSelected(revisionActual.isFunCamLente());
        chkAltSup.setSelected(revisionActual.isFunAltSup());
        chkAltInf.setSelected(revisionActual.isFunAltInf());
        chkMic.setSelected(revisionActual.isFunMic());
        chkFaceId.setSelected(revisionActual.isFunFaceId());
        chkMs.setSelected(revisionActual.isFunMs());
        tfMsTexto.setText(revisionActual.getFunMsTexto() != null ? revisionActual.getFunMsTexto() : "");
        tfMsTexto.setDisable(!editable || !chkMs.isSelected());
        chkBloqueoOp.setSelected(revisionActual.isFunBloqueoOp());
        tfObservacion.setText(revisionActual.getFunObservacion() != null ? revisionActual.getFunObservacion() : "");
    }

    private void seleccionarGrado(String grado) {
        if (grado == null) { grupoGrado.selectToggle(null); return; }
        for (Toggle tg : grupoGrado.getToggles()) {
            if (grado.equals(tg.getUserData())) { grupoGrado.selectToggle(tg); return; }
        }
        grupoGrado.selectToggle(null);
    }

    private void seleccionarPant(String pant) {
        for (Toggle tg : grupoPant.getToggles()) {
            Object ud = tg.getUserData();
            if ((pant == null && ud == null) || (pant != null && pant.equals(ud))) {
                grupoPant.selectToggle(tg);
                return;
            }
        }
    }

    /** Chips de estado de cada parte; recalculados desde {@link #revisionActual}. Usado también por T9. */
    void recargarChips() {
        aplicarChip(lblChipEst, revisionActual != null ? revisionActual.getEstFecha() : null,
                revisionActual != null ? revisionActual.getEstUsuario() : null);
        aplicarChip(lblChipFun, revisionActual != null ? revisionActual.getFunFecha() : null,
                revisionActual != null ? revisionActual.getFunUsuario() : null);
    }

    private void aplicarChip(Label lbl, LocalDateTime fecha, String usuario) {
        if (fecha != null) {
            lbl.setText("guardada · " + usuario + " · " + fecha.format(FMT_CHIP));
            lbl.setStyle(ESTILO_CHIP_VERDE);
        } else {
            lbl.setText("pendiente");
            lbl.setStyle(ESTILO_CHIP_AMBAR);
        }
    }

    private void aplicarEditable() {
        for (Node n : controlesEditables) n.setDisable(!editable);
        tfMsTexto.setDisable(!editable || !chkMs.isSelected());
    }

    // ── Guardado por parte ────────────────────────────────────────────────────

    private void guardarEstetica() {
        if (guardandoEst) return;
        Toggle selGrado = grupoGrado.getSelectedToggle();
        String grado = selGrado != null ? (String) selGrado.getUserData() : null;
        Toggle selPant = grupoPant.getSelectedToggle();
        String pant = selPant != null ? (String) selPant.getUserData() : null;
        String imei = t.getImei();

        guardandoEst = true;
        btnGuardarEst.setDisable(true);
        new Thread(() -> {
            try {
                dao.guardarRevisionEstetica(imei, grado, pant);
                RevisionTelefono actualizada = dao.getRevision(imei);
                Platform.runLater(() -> {
                    revisionActual = actualizada;
                    recargarChips();
                    huboCambios = true;
                    guardandoEst = false;
                    btnGuardarEst.setDisable(!editable);
                });
            } catch (SQLException ex) {
                Platform.runLater(() -> {
                    guardandoEst = false;
                    btnGuardarEst.setDisable(!editable);
                    Alertas.mostrarError(ex.getMessage());
                });
            }
        }, "guardar-revision-estetica").start();
    }

    private void guardarFuncional() {
        RevisionTelefono f = armarFuncionalDesdeControles();
        if (f.isFunBloqueoOp()) {
            ConfirmDialog.mostrar("Bloquear teléfono",
                    "El check de bloqueo de operador está marcado: al guardar, el teléfono pasa a BLOQUEADO.",
                    "Guardar y bloquear",
                    () -> enviarFuncional(f));
        } else {
            enviarFuncional(f);
        }
    }

    private RevisionTelefono armarFuncionalDesdeControles() {
        RevisionTelefono f = new RevisionTelefono();
        String bat = tfBateria.getText();
        f.setFunBateriaPct((bat == null || bat.isBlank()) ? null : Integer.valueOf(bat.trim()));
        f.setFunPantTactil(chkPantTactil.isSelected());
        f.setFunPantQuemada(chkPantQuemada.isSelected());
        f.setFunPantMal(chkPantMal.isSelected());
        f.setFunCamMancha(chkCamMancha.isSelected());
        f.setFunCamLente(chkCamLente.isSelected());
        f.setFunAltSup(chkAltSup.isSelected());
        f.setFunAltInf(chkAltInf.isSelected());
        f.setFunMic(chkMic.isSelected());
        f.setFunFaceId(chkFaceId.isSelected());
        f.setFunMs(chkMs.isSelected());
        f.setFunMsTexto(tfMsTexto.getText());
        f.setFunBloqueoOp(chkBloqueoOp.isSelected());
        f.setFunObservacion(tfObservacion.getText());
        return f;
    }

    private void enviarFuncional(RevisionTelefono f) {
        if (guardandoFun) return;
        boolean bloqueo = f.isFunBloqueoOp();
        String imei = t.getImei();

        guardandoFun = true;
        btnGuardarFun.setDisable(true);
        new Thread(() -> {
            try {
                dao.guardarRevisionFuncional(imei, f);
                RevisionTelefono actualizada = dao.getRevision(imei);
                Platform.runLater(() -> {
                    revisionActual = actualizada;
                    recargarChips();
                    huboCambios = true;
                    guardandoFun = false;
                    if (bloqueo) {
                        ventana.close(); // salió de la cola: cierra y notifica onCambios (setOnHidden)
                    } else {
                        btnGuardarFun.setDisable(!editable);
                    }
                });
            } catch (SQLException ex) {
                Platform.runLater(() -> {
                    guardandoFun = false;
                    btnGuardarFun.setDisable(!editable);
                    Alertas.mostrarError(ex.getMessage());
                });
            }
        }, "guardar-revision-funcional").start();
    }
}
