# Asignación por componente en lote ("pila") — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convertir el modal de asignación por componente (un IMEI) en un modal de lote ("pila") donde se escanean varios IMEIs, se configuran (modelo + técnicos + comentario) y se guardan todos al final.

**Architecture:** Reescritura del método `@FXML abrirFormularioAsignacion()` en `PendientesSuperTecnicoController` al patrón "lote" del modal de pulidos (`PulidoSuperTecnicoController.abrirFormularioAsignacion`), pero con **modelo obligatorio por IMEI** y **varios técnicos por IMEI**. Estado del lote en memoria (lista de `EntradaAsignacion`); nada se escribe en BD hasta un `Guardar` final que recorre las entradas verdes. Solo cliente; reutiliza DAOs existentes; sin cambios de servidor ni de FXML.

**Tech Stack:** Java 17 + JavaFX 21, Maven. Sin tests automáticos (JavaFX) → verificación por `mvn compile -q` + checklist manual (igual que Items 1 y 3).

**Spec:** `docs/superpowers/specs/2026-06-12-asignacion-lote-pila-design.md`

---

## Notas previas para el implementador

- **Un único fichero:** `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`.
- El método actual `abrirFormularioAsignacion()` está en las **líneas 585-930**. Mantén el nombre y la anotación `@FXML` (lo invoca el FXML; no tocar FXML).
- **Reutiliza tal cual** (cópialo del método actual, adaptándolo a operar sobre la "entrada en curso"):
  - **Maquinaria de modelo** (buscador + `Popup` + `tfModelo` + `listaModelos` + `confirmarModelo` + listeners): líneas **606-862**.
  - **Checkboxes de técnicos + validación de ocupados** (`getTecnicosConAsignacionActiva`, pill "N asignados", deshabilitar): líneas **670-748** y **817**.
  - **Lookup async del modelo** (hilo daemon + `telefonoDAO.getModelo` + `Platform.runLater`): líneas **770-816**.
  - **Scaffolding del modal** (`Stage` modal, `Scene`, `app.css`, `showAndWait`): líneas **877-929**.
- **DAOs ya disponibles en el controlador** (campos `telefonoDAO`, `reparacionDAO`, `tecnicoDAO`):
  - `telefonoDAO.insertar(String imei, String modeloCode)` — registra teléfono↔modelo.
  - `reparacionDAO.insertarAsignacion(String imei, int idTec, String comentario)` — crea una asignación (`comentario` puede ser `null`).
  - `reparacionDAO.getTecnicosConAsignacionActiva(String imei)` → `List<Integer>` (idTec ocupados para ese IMEI).
  - `telefonoDAO.getModelo(String imei)` → `String` código de modelo o `null`.
  - `tecnicoDAO.getAllActivos()` → `List<Tecnico>`.
- **Modelos/utilidades:** `FormularioReparacionController.MODELOS_ORDENADOS`, `FormularioReparacionController.traducirModelo(String code)`, `Tecnico.getNombre()`, `Tecnico.getIdTec()`, `com.reparaciones.utils.ConfirmDialog.mostrar(titulo, mensaje, textoBoton, Runnable)`, `com.reparaciones.utils.Colores`, `mostrarError(Exception)`.
- **Estilo:** usa los mismos estilos inline del método actual (navy `#2C3B54`, labels `#586376`, inputs blancos borde `#C2C8D0`, pill técnico fondo `#001232`). Colores nuevos para la pila: rojo (pendiente) y verde (asignado) — usa literales como en el mockup (`#C0392B`/`#FDF1F0`/`#D9534F` para rojo; `#2E7D32`/`#F0F8F1`/`#46A04B` para verde) salvo que exista ya una constante en `Colores`.
- **Compilación con la app cerrada** (un `mvn compile` con la app abierta puede fallar por PNG bloqueados). Comando: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`.

---

## File Structure

Solo se modifica `PendientesSuperTecnicoController.java`:
- **Nueva clase interna** `EntradaAsignacion` — modelo de una entrada del lote (un IMEI con su config local).
- **Nuevo helper** `crearFilaPila(...)` — construye el `Node` de una fila de la pila (IMEI + estado modelo + pills técnico + ✕), con callbacks de click y de borrado.
- **Reescritura** de `abrirFormularioAsignacion()` — ensambla el modal de lote y orquesta el estado.

---

### Task 1: Clase interna `EntradaAsignacion`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

- [ ] **Step 1: Añadir la clase interna**

Insértala justo después del `record CambioPendiente(...)` (línea 83) y el `Map cambiosPendientes` (línea 84):

```java
/** Una entrada del lote de asignación: un IMEI con su configuración local (aún no en BD). */
private static final class EntradaAsignacion {
    final String imei;
    String modeloCode;                       // código interno del modelo, o null si falta
    final List<Tecnico> tecnicos = new ArrayList<>();
    String comentario = "";
    boolean asignada;                        // true = verde (configurada y movida); false = rojo (pendiente)
    boolean modeloBuscado;                   // true si ya se lanzó el lookup (no repetir)
    boolean buscando;                        // true mientras el lookup está en vuelo

    EntradaAsignacion(String imei) { this.imei = imei; }

    boolean tieneModelo() { return modeloCode != null && !modeloCode.isEmpty(); }
}
```

- [ ] **Step 2: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS (la clase compila aunque aún no se use).

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat: modelo EntradaAsignacion para lote de asignacion"
```

---

### Task 2: Helper `crearFilaPila(...)`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

Construye la fila visual de una entrada en la pila. La usan tanto la sección roja como la verde. Recibe la entrada y dos callbacks (cargar al pulsar la fila, quitar al pulsar la ✕).

- [ ] **Step 1: Añadir el helper**

Insértalo como método privado del controlador (p. ej. justo antes de `abrirFormularioAsignacion`):

```java
/** Construye una fila de la pila para {@code e}. onClick = cargar en el formulario; onRemove = quitar de la pila. */
private HBox crearFilaPila(EntradaAsignacion e, Runnable onClick, Runnable onRemove) {
    Label lblImei = new Label(e.imei);
    lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");

    Label estado = new Label();
    if (e.tieneModelo()) {
        estado.setText(FormularioReparacionController.traducirModelo(e.modeloCode));
        estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #1B5E20;"
                + " -fx-background-color: #E3F1E4; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
    } else if (e.buscando) {
        estado.setText("Buscando…");
        estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #586376;"
                + " -fx-background-color: #EEF1F5; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
    } else {
        estado.setText("⚠ falta modelo");
        estado.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #9A6B00;"
                + " -fx-background-color: #FCE7C3; -fx-background-radius: 6; -fx-padding: 2 8 2 8;");
    }

    HBox pills = new HBox(4);
    pills.setAlignment(Pos.CENTER_LEFT);
    for (Tecnico t : e.tecnicos) {
        Label p = new Label(t.getNombre());
        p.setStyle("-fx-font-size: 9.5px; -fx-text-fill: white; -fx-background-color: #001232;"
                + " -fx-background-radius: 20; -fx-padding: 1 7 1 7;");
        pills.getChildren().add(p);
    }

    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

    Label x = new Label("✕");
    x.setStyle("-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
    x.setOnMouseEntered(ev -> x.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
    x.setOnMouseExited(ev -> x.setStyle("-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
    x.setOnMouseClicked(ev -> { ev.consume(); onRemove.run(); });

    HBox fila = new HBox(8, lblImei, estado, pills, spacer, x);
    fila.setAlignment(Pos.CENTER_LEFT);
    fila.setStyle("-fx-padding: 7 9 7 9; -fx-border-color: transparent transparent #EEF1F5 transparent; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
    fila.setOnMouseClicked(ev -> onClick.run());
    return fila;
}
```

- [ ] **Step 2: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS (helper sin usar aún).

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat: helper crearFilaPila para la pila de asignacion"
```

---

### Task 3: Reescribir `abrirFormularioAsignacion()` al modelo de lote

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java:585-930`

Esta es la tarea principal. Reemplaza **todo el cuerpo** del método actual (585-930) por el nuevo. Construye la estructura, conecta el escaneo, el formulario por-IMEI (reutilizando la maquinaria existente), las dos secciones de la pila, y el guardado final.

> **Estrategia de reutilización:** abre el método actual (585-930) en el editor. La parte de **modelo** (tfModelo + popup + listaModelos + `confirmarModelo` + listeners de modelo, 606-862), la de **técnicos+ocupados** (670-748, 817) y el **lookup async** (770-816) se copian con cambios mínimos: en vez de operar sobre variables sueltas (`imei`, `modeloSel`), operan sobre la **entrada en curso** `actual[0]`. El **scaffolding** del Stage (877-929) se reusa para el `ventana`.

- [ ] **Step 1: Estado del lote y técnicos disponibles**

Cuerpo nuevo del método — bloque inicial:

```java
@FXML
private void abrirFormularioAsignacion() {
    // ── Estado del lote ──
    List<EntradaAsignacion> pila = new ArrayList<>();
    EntradaAsignacion[] actual = { null };          // entrada cargada en el formulario
    boolean[] editandoVerde = { false };            // true si se está editando una verde
    List<Tecnico> defTecnicos = new ArrayList<>();  // selección que se mantiene entre IMEIs
    String[] defComentario = { "" };

    List<Tecnico> tecnicosModal = new ArrayList<>();
    try { tecnicosModal.addAll(tecnicoDAO.getAllActivos()); }
    catch (SQLException ex) { mostrarError(ex); }
```

- [ ] **Step 2: Cabecera y campo de escaneo**

```java
    Label lblTitulo = new Label("Asignar reparaciones");
    lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
    Label lblSub = new Label("Escanea IMEIs y configúralos. Técnicos y comentario se mantienen entre IMEIs. Se guardan todos al final.");
    lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");

    Label lblScan = new Label("Escanear IMEI → pendiente de asignar");
    lblScan.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
    TextField tfScan = new TextField();
    tfScan.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
    tfScan.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
            + " -fx-background-radius: 4; -fx-padding: 11; -fx-text-fill: #2C3B54; -fx-font-size: 14px;");
    Label lblScanErr = new Label();
    lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");
```

- [ ] **Step 3: Secciones de la pila (roja/verde)**

```java
    Label lblRojo = new Label("Pendiente de asignar (0)");
    lblRojo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
    VBox boxRojo = new VBox(0);
    boxRojo.setStyle("-fx-background-color: white; -fx-border-color: #EFC4C0; -fx-border-radius: 6; -fx-border-width: 1;");

    Label lblVerde = new Label("Asignados (0) · sin guardar");
    lblVerde.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2E7D32; -fx-padding: 10 0 0 0;");
    VBox boxVerde = new VBox(0);
    boxVerde.setStyle("-fx-background-color: white; -fx-border-color: #BFE0C2; -fx-border-radius: 6; -fx-border-width: 1;");

    VBox pilaBox = new VBox(6, lblRojo, boxRojo, lblVerde, boxVerde);
    ScrollPane scrollPila = new ScrollPane(pilaBox);
    scrollPila.setFitToWidth(true);
    scrollPila.setPrefViewportWidth(250);
    scrollPila.setMinWidth(250);
    scrollPila.setPrefHeight(330);
    scrollPila.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    scrollPila.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
```

- [ ] **Step 4: Formulario por-IMEI (modelo + técnicos + comentario)**

Construye el formulario reutilizando la maquinaria del método antiguo. **Copia de las líneas 606-862** el bloque de modelo (`tfModelo`, `modelosFiltrados`, `listaModelos`, `popupModelo`, `modeloSel`, `actualizandoModelo`, `mostrarPopupModelo`, `confirmarModelo` y los listeners de `tfModelo`/`listaModelos`) **sin cambios** salvo que, al confirmar un modelo, además guardes en la entrada en curso:

```java
    // dentro de confirmarModelo, tras "modeloSel[0] = code;" añade:
    if (actual[0] != null) actual[0].modeloCode = code;
```

Construye los técnicos (adaptado de 670-748): un `CheckBox` por técnico de `tecnicosModal`, en un `VBox` dentro de `ScrollPane` (maxHeight 150), más la `Label pillAsignados` (pill "N asignados"). Guarda las listas:

```java
    Label lblTecnicos = new Label("Técnicos a asignar");
    lblTecnicos.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
    Label pillAsignados = new Label();
    pillAsignados.setStyle("-fx-background-color: #FCE7C3; -fx-text-fill: #9A6B00; -fx-font-size: 10.5px;"
            + " -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 2 9 2 9;");
    pillAsignados.setVisible(false); pillAsignados.setManaged(false);
    HBox headerTecnicos = new HBox(8, lblTecnicos, pillAsignados);
    headerTecnicos.setAlignment(Pos.CENTER_LEFT);

    List<CheckBox> checkboxes = new ArrayList<>();
    VBox cbContainer = new VBox(6);
    cbContainer.setStyle("-fx-background-color: white; -fx-padding: 8;");
    for (Tecnico t : tecnicosModal) {
        CheckBox cb = new CheckBox(t.getNombre());
        cb.setStyle("-fx-font-size: 12px;");
        checkboxes.add(cb);
        cbContainer.getChildren().add(cb);
    }
    ScrollPane scrollTec = new ScrollPane(cbContainer);
    scrollTec.setFitToWidth(true);
    scrollTec.setMaxHeight(150);
    scrollTec.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-background-radius: 4;");

    Label lblNotaPersist = new Label("↳ Se mantienen del IMEI anterior; cámbialos solo si hace falta.");
    lblNotaPersist.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #586376; -fx-font-style: italic;");

    Label lblComentario = new Label("Comentario (opcional)");
    lblComentario.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
    TextArea tfComentario = new TextArea();
    tfComentario.setWrapText(true);
    tfComentario.setPrefRowCount(2);
    tfComentario.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
            + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

    Label lblImeiCurso = new Label("—");
    lblImeiCurso.setStyle("-fx-font-family: monospace; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
    Label lblImeiCursoCap = new Label("IMEI en curso");
    lblImeiCursoCap.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376; -fx-font-weight: bold;");

    Button btnAsignar = new Button("Asignar →");
    btnAsignar.getStyleClass().add("btn-primary");
    btnAsignar.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(btnAsignar, javafx.scene.layout.Priority.ALWAYS);
    Button btnSaltar = new Button("Saltar");
    btnSaltar.getStyleClass().add("btn-secondary");
    HBox accionesForm = new HBox(10, btnSaltar, btnAsignar);

    VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, new Label("Modelo"), tfModelo,
            headerTecnicos, scrollTec, lblNotaPersist, lblComentario, tfComentario, accionesForm);
    formBox.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-border-width: 1; -fx-padding: 16;");
    HBox.setHgrow(formBox, javafx.scene.layout.Priority.ALWAYS);
    formBox.setDisable(true);   // hasta que haya una entrada cargada
```

(El `new Label("Modelo")` puede sustituirse por una `Label` estilada como las demás; basta con que el `tfModelo` reutilizado quede en el `formBox`.)

- [ ] **Step 5: Barra final (Guardar)**

```java
    Label lblProg = new Label("");
    lblProg.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
    javafx.scene.layout.Region spacerBarra = new javafx.scene.layout.Region();
    HBox.setHgrow(spacerBarra, javafx.scene.layout.Priority.ALWAYS);
    Button btnGuardar = new Button("Guardar (0)");
    btnGuardar.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-font-size: 14px;"
            + " -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 11 22 11 22;");
    btnGuardar.setDisable(true);
    HBox barraFinal = new HBox(12, lblProg, spacerBarra, btnGuardar);
    barraFinal.setAlignment(Pos.CENTER_LEFT);
    barraFinal.setStyle("-fx-padding: 14 0 0 0; -fx-border-color: #C2C8D0 transparent transparent transparent; -fx-border-width: 1 0 0 0;");
```

- [ ] **Step 6: Helpers de orquestación (`renderPila`, `cargarEntrada`, `lanzarLookup`, `validarForm`, `asignarActual`, `cargarSiguienteRojo`)**

```java
    Runnable[] renderPila = new Runnable[1];
    java.util.function.Consumer<EntradaAsignacion>[] cargarEntrada = new java.util.function.Consumer[1];
    Runnable[] lanzarLookup = new Runnable[1];
    Runnable validarForm = () -> {
        boolean modeloOk = modeloSel[0] != null;
        boolean algunoSel = checkboxes.stream().anyMatch(cb -> cb.isSelected() && !cb.isDisabled());
        btnAsignar.setDisable(!(modeloOk && algunoSel));
    };

    renderPila[0] = () -> {
        boxRojo.getChildren().clear();
        boxVerde.getChildren().clear();
        int nRojo = 0, nVerde = 0;
        for (EntradaAsignacion e : pila) {
            Runnable onClick = () -> cargarEntrada[0].accept(e);
            Runnable onRemove = () -> { pila.remove(e); if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); } renderPila[0].run(); };
            HBox fila = crearFilaPila(e, onClick, onRemove);
            if (e.asignada) { boxVerde.getChildren().add(fila); nVerde++; }
            else            { boxRojo.getChildren().add(fila);  nRojo++; }
        }
        lblRojo.setText("Pendiente de asignar (" + nRojo + ")");
        lblVerde.setText("Asignados (" + nVerde + ") · sin guardar");
        int sinModelo = (int) pila.stream().filter(e -> !e.asignada && !e.tieneModelo()).count();
        lblProg.setText(nVerde + " configurados · " + nRojo + " pendientes" + (sinModelo > 0 ? " · " + sinModelo + " sin modelo" : ""));
        btnGuardar.setText("Guardar (" + nVerde + ")");
        btnGuardar.setDisable(nRojo != 0 || nVerde == 0);   // solo con la pila roja vacía
    };

    lanzarLookup[0] = () -> {
        EntradaAsignacion e = actual[0];
        if (e == null || e.tieneModelo() || e.modeloBuscado) return;
        e.modeloBuscado = true;
        e.buscando = true;
        tfModelo.setPromptText("Buscando...");
        renderPila[0].run();
        Thread t = new Thread(() -> {
            String modelo = null;
            try { modelo = telefonoDAO.getModelo(e.imei); } catch (Exception ignore) {}
            String res = modelo;
            javafx.application.Platform.runLater(() -> {
                e.buscando = false;
                if (res != null && !res.isEmpty()) {
                    e.modeloCode = res;
                    if (actual[0] == e) confirmarModelo.accept(res);   // confirmarModelo viene de la maquinaria de modelo
                } else if (actual[0] == e) {
                    tfModelo.setPromptText("No encontrado — selecciona manualmente");
                }
                renderPila[0].run();
            });
        });
        t.setDaemon(true);
        t.start();
    };

    cargarEntrada[0] = (EntradaAsignacion e) -> {
        actual[0] = e;
        editandoVerde[0] = e.asignada;
        formBox.setDisable(false);
        lblImeiCurso.setText(e.imei);
        // modelo en el form
        actualizandoModelo[0] = true;
        modeloSel[0] = e.modeloCode;
        tfModelo.setText(e.tieneModelo() ? FormularioReparacionController.traducirModelo(e.modeloCode) : "");
        modelosFiltrados.setPredicate(s -> true);
        actualizandoModelo[0] = false;
        // técnicos: de la entrada (verde/edición) o del default (rojo recién escaneado)
        List<Tecnico> base = e.asignada || !e.tecnicos.isEmpty() ? e.tecnicos : defTecnicos;
        java.util.Set<Integer> ids = base.stream().map(Tecnico::getIdTec).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < tecnicosModal.size(); i++) checkboxes.get(i).setSelected(ids.contains(tecnicosModal.get(i).getIdTec()));
        // comentario
        tfComentario.setText(e.asignada || !e.comentario.isEmpty() ? e.comentario : defComentario[0]);
        // ocupados para ESTE imei
        try {
            List<Integer> ocupados = reparacionDAO.getTecnicosConAsignacionActiva(e.imei);
            for (int i = 0; i < tecnicosModal.size(); i++) {
                boolean ocup = ocupados.contains(tecnicosModal.get(i).getIdTec());
                checkboxes.get(i).setDisable(ocup);
                if (ocup) checkboxes.get(i).setSelected(false);
            }
            long n = checkboxes.stream().filter(CheckBox::isDisabled).count();
            pillAsignados.setText(n + (n == 1 ? " asignado" : " asignados"));
            pillAsignados.setVisible(n >= 1); pillAsignados.setManaged(n >= 1);
        } catch (SQLException ex) { /* silencioso */ }
        btnAsignar.setText(e.asignada ? "Guardar cambios" : "Asignar →");
        if (!e.asignada) lanzarLookup[0].run();
        validarForm.run();
    };

    Runnable cargarSiguienteRojo = () -> {
        EntradaAsignacion sig = pila.stream().filter(x -> !x.asignada).findFirst().orElse(null);
        if (sig != null) cargarEntrada[0].accept(sig);
        else { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
    };

    Runnable asignarActual = () -> {
        EntradaAsignacion e = actual[0];
        if (e == null || modeloSel[0] == null) return;
        List<Tecnico> sel = new ArrayList<>();
        for (int i = 0; i < tecnicosModal.size(); i++)
            if (checkboxes.get(i).isSelected() && !checkboxes.get(i).isDisabled()) sel.add(tecnicosModal.get(i));
        if (sel.isEmpty()) return;
        e.modeloCode = modeloSel[0];
        e.tecnicos.clear(); e.tecnicos.addAll(sel);
        e.comentario = tfComentario.getText().trim();
        e.asignada = true;
        // actualizar "default" que se mantiene
        defTecnicos.clear(); defTecnicos.addAll(sel);
        defComentario[0] = e.comentario;
        renderPila[0].run();
        if (editandoVerde[0]) { editandoVerde[0] = false; actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
        else cargarSiguienteRojo.run();
    };
```

- [ ] **Step 7: Escaneo (añadir a la pila con dedup) y listeners del formulario**

```java
    Runnable intentarAnadir = () -> {
        String imei = tfScan.getText().trim();
        if (imei.length() != 15) return;
        if (pila.stream().anyMatch(x -> x.imei.equals(imei))) { lblScanErr.setText("Ese IMEI ya está en la pila."); return; }
        lblScanErr.setText("");
        EntradaAsignacion e = new EntradaAsignacion(imei);
        pila.add(e);
        renderPila[0].run();
        tfScan.clear();
        cargarEntrada[0].accept(e);             // lo carga en el form y dispara su lookup
        javafx.application.Platform.runLater(tfScan::requestFocus);
    };
    tfScan.textProperty().addListener((obs, o, n) -> {
        if (!n.matches("\\d*")) { tfScan.setText(n.replaceAll("[^\\d]", "")); return; }
        if (n.length() > 15) { tfScan.setText(n.substring(0, 15)); return; }
        lblScanErr.setText("");
        if (n.length() == 15) intentarAnadir.run();
    });
    tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) intentarAnadir.run(); });

    checkboxes.forEach(cb -> cb.selectedProperty().addListener((obs, o, n) -> validarForm.run()));
    btnAsignar.setOnAction(ev -> asignarActual.run());
    btnSaltar.setOnAction(ev -> cargarSiguienteRojo.run());
```

> El listener de `tfModelo` (copiado de 819-828) debe llamar a `validarForm.run()` al final (sustituye la llamada a `validar.run()` del original). Igual el `confirmarModelo` (debe terminar con `validarForm.run()`).

- [ ] **Step 8: Layout, ventana, guardar y cierre**

```java
    HBox cols = new HBox(18, scrollPila, formBox);
    VBox contenido = new VBox(12, lblTitulo, lblSub, lblScan, tfScan, lblScanErr, new Separator(), cols, barraFinal);
    contenido.setPadding(new Insets(26));
    contenido.setPrefWidth(680);
    contenido.setStyle("-fx-background-color: #DDE1E7;");

    javafx.stage.Stage ventana = new javafx.stage.Stage();
    ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
    ventana.setResizable(true);
    ventana.setMinWidth(680);
    ventana.setMinHeight(560);
    ventana.setTitle("Asignar reparaciones");

    btnGuardar.setOnAction(ev -> {
        List<String> conflictos = new ArrayList<>();
        try {
            for (EntradaAsignacion e : pila) {
                if (!e.asignada) continue;
                List<Integer> ocupados = reparacionDAO.getTecnicosConAsignacionActiva(e.imei);
                telefonoDAO.insertar(e.imei, e.modeloCode);
                for (Tecnico t : e.tecnicos) {
                    if (ocupados.contains(t.getIdTec())) { conflictos.add("• " + e.imei + " → " + t.getNombre() + " (ya asignado)"); continue; }
                    reparacionDAO.insertarAsignacion(e.imei, t.getIdTec(),
                            e.comentario.isEmpty() ? null : e.comentario);
                }
            }
        } catch (SQLException ex) { mostrarError(ex); return; }
        ventana.close();
        cargar();
        if (!conflictos.isEmpty())
            new Alert(Alert.AlertType.WARNING, "Algunas asignaciones no se crearon:\n\n" + String.join("\n", conflictos)).showAndWait();
    });

    ventana.setOnCloseRequest(ev -> {
        if (pila.isEmpty()) return;
        ev.consume();
        ConfirmDialog.mostrar("Descartar", "Se descartarán los " + pila.size() + " IMEIs de la pila.",
                "Descartar", ventana::close);
    });

    javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
    scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
    ventana.setScene(scene);
    renderPila[0].run();
    javafx.application.Platform.runLater(tfScan::requestFocus);
    ventana.showAndWait();
}
```

- [ ] **Step 9: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS. Si falla, suele ser por la maquinaria de modelo copiada (asegúrate de haber copiado `tfModelo`, `modelosFiltrados`, `listaModelos`, `popupModelo`, `modeloSel`, `actualizandoModelo`, `confirmarModelo`, `mostrarPopupModelo` y sus listeners de 606-862) y de que todos los nombres usados existen.

- [ ] **Step 10: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat: asignacion por componente en lote (pila roja/verde, guardado al final)"
```

---

### Task 4: Verificación manual (UAT) y ajustes

**Files:**
- Modify (si hace falta): `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

No hay tests automáticos. Arranca la app y recorre el checklist del spec.

- [ ] **Step 1: Arrancar la app**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml javafx:run`
(Inicia sesión como supertécnico y abre el formulario de asignación.)

- [ ] **Step 2: Checklist manual**

Verifica cada punto; si algo falla, anótalo y corrígelo en el controlador:
1. Escanear 3 IMEIs → caen en **rojo**; el primero se carga en el formulario.
2. Configurar (técnicos + modelo auto) y pulsar **Asignar →** → pasa a **verde**; los técnicos/comentario se mantienen al cargar el siguiente rojo.
3. Un IMEI con lookup fallido → `⚠ falta modelo`, sigue rojo; ponerle modelo a mano → al asignar pasa a verde.
4. **Guardar** deshabilitado mientras quede algún rojo (incl. sin modelo); habilitado solo con la pila roja vacía y ≥1 verde.
5. **✕** en un rojo y en un verde → se quitan de la pila (nada en BD).
6. Click en una **verde** → carga su config (técnicos/modelo/comentario), botón "Guardar cambios"; cambiarla se refleja.
7. Click en una **roja** distinta → la carga (saltar orden).
8. Escanear un IMEI ya presente → "Ese IMEI ya está en la pila", no se añade.
9. Técnico ya asignado a ese IMEI (de antes, en BD) → checkbox deshabilitado + pill "N asignados".
10. **Guardar** con varios técnicos por IMEI → tras recargar la tabla aparecen las asignaciones (una por técnico de cada IMEI).
11. Cerrar con la pila no vacía → confirma descartar.

- [ ] **Step 3: Commit de ajustes (si hubo)**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "fix: ajustes UAT del lote de asignacion"
```

---

## Self-Review (rellenado por el autor del plan)

**Cobertura del spec:**
- Escaneo a pila roja + dedup → Task 3 Step 7. ✓
- Pila 2 secciones roja/verde + ✕ → Tasks 2 y 3 (Steps 3, 6). ✓
- Formulario por-IMEI: modelo (lookup + manual, obligatorio), técnicos (persisten + ocupados deshabilitados), comentario (persiste) → Task 3 Steps 4, 6. ✓
- Click rojo = cargar; click verde = editar (local) → Task 3 Step 6 (`cargarEntrada`). ✓
- Asignar → mueve a verde (requiere modelo + ≥1 técnico) → Step 6 (`asignarActual`, `validarForm`). ✓
- Guardar solo con pila roja vacía; commit en bloque; revalida conflictos; reporta → Step 6 (`renderPila` habilitado) + Step 8 (`btnGuardar`). ✓
- Cerrar con pila no vacía → confirmar descartar → Step 8 (`setOnCloseRequest`). ✓
- Solo cliente, sin servidor ni FXML → ningún otro fichero tocado. ✓

**Consistencia de tipos/nombres:** `EntradaAsignacion` (campos `imei/modeloCode/tecnicos/comentario/asignada/modeloBuscado/buscando`), `crearFilaPila(EntradaAsignacion, Runnable, Runnable)`, y las variables del método (`pila`, `actual[0]`, `modeloSel`, `confirmarModelo`, `checkboxes`, `tecnicosModal`) se usan igual en todas las tareas. Los nombres de la maquinaria de modelo (`modelosFiltrados`, `actualizandoModelo`, `mostrarPopupModelo`) deben coincidir con los que copies de 606-862 — si los renombras, renómbralos en todo Step 6/7.

**Dependencia de copia:** Task 3 depende de copiar fielmente la maquinaria de modelo (606-862), técnicos/ocupados (670-748) y el scaffolding (877-929) del método actual. Por eso Task 3 se ejecuta con el fichero original a la vista.
