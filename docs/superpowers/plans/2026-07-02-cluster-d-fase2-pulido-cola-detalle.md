# Cluster D · Fase 2 — Pulido master-detail — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar `construirPulidoPane` a un layout lista-izquierda + detalle-derecha (master-detail), editando la fila seleccionada en el detalle, sin rojo/verde ni "Asignar".

**Architecture:** Reescritura de un único método JavaFX (`construirPulidoPane` en `PendientesSuperTecnicoController.java`). Mantiene el modelo `FilaPulido`, el guardado, el escaneo y toda la lógica de cliente sin-cliente/sync. Sustituye las filas autoeditables por filas-resumen seleccionables (izquierda) y un panel de detalle único (derecha) que edita la `FilaPulido` seleccionada en vivo.

**Tech Stack:** Java 17, JavaFX (UI en código), Maven. Sin tests de UI → verificación por **compilación** (`mvn -o -q compile`) + **smoke manual** (`mvn -o javafx:run`).

## Global Constraints

- **Solo cliente.** No tocar servidor, DAOs, ni el modelo `FilaPulido`. El guardado (`btnGuardar`, fuera de este método) sigue recorriendo `lotePulido` igual.
- **Firma de `construirPulidoPane` sin cambios** (mismos 6 parámetros); el único call site no se toca.
- **Sin paso "Asignar" ni rojo/verde** (decisión aprobada): la fila escaneada queda lista; solo cambia dónde se edita.
- **1 técnico** por fila (multi-técnico/modelo es Fase 3).
- Cliente en el detalle: **botón → `SelectorClienteDialog`** (ya trae "— Sin cliente —" y respeta el mapa por-IMEI). No reimplementar el autocompletado inline de rep/glass.
- Debe seguir llamando `onClienteCambiado.accept(fila)` al cambiar cliente (sync por-IMEI) y `sembrarCliente.accept(fila)` al crear una fila (heredar decisión del modal).
- Ejecutar Maven por **Bash** (`mvn -o …`). No `Co-Authored-By` en commits. No push sin OK.
- Fichero: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`. Comandos desde `.../gestion-reparaciones-cliente`.

---

### Task 1: Reescribir `construirPulidoPane` a master-detail

Sustituye el cuerpo entero del método por la versión lista+detalle. Es un cambio atómico (el método compila como unidad y se valida por smoke).

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `FilaPulido` (campos `imei`, `tecnico`, `comentario`, `cliente`, `sinCliente`); params del método (`lote`, `tecnicosModal`, `onChange`, `onClienteCambiado`, `refrescadoresCliente`, `sembrarCliente`); `SelectorClienteDialog.elegir`, `telefonoDAO.getClienteId`, `clienteDAO.getActivos/getAll`, `ImeiUtils`.
- Produces: mismo `VBox` de retorno; comportamiento de `lotePulido` idéntico para el guardado.

- [ ] **Step 1: Reemplazar el método completo `construirPulidoPane`**

Sustituye **todo** el método (desde la firma `private VBox construirPulidoPane(...)` hasta su `}` de cierre — actualmente el `return new VBox(8, lblTec, ... , scroll);` seguido de `}`) por esta versión completa:

```java
    private VBox construirPulidoPane(List<FilaPulido> lote, List<Tecnico> tecnicosModal, Runnable onChange,
                                     java.util.function.Consumer<FilaPulido> onClienteCambiado,
                                     List<Runnable> refrescadoresCliente,
                                     java.util.function.Consumer<FilaPulido> sembrarCliente) {
        // ── Cabecera: técnico/comentario por defecto + escaneo ──────────────
        Label lblTec = new Label("Técnico por defecto");
        lblTec.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<Tecnico> cbTec = new ComboBox<>();
        cbTec.setMaxWidth(Double.MAX_VALUE);
        cbTec.setVisibleRowCount(8);
        cbTec.getItems().addAll(tecnicosModal);
        cbTec.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
            @Override public Tecnico fromString(String s) { return null; }
        });

        Label lblCom = new Label("Comentario por defecto (opcional)");
        lblCom.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea taCom = new TextArea();
        taCom.setWrapText(true); taCom.setPrefRowCount(2);
        taCom.setPromptText("Instrucciones para el técnico...");
        taCom.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        Label lblScan = new Label("Escanear IMEI → pulido");
        lblScan.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPromptText("Escanea o escribe el IMEI (15 dígitos)...");
        tfScan.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-padding: 11; -fx-text-fill: #2C3B54; -fx-font-size: 14px;");
        Label lblErr = new Label();
        String errStyle = "-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;";
        String okStyle  = "-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-min-height: 15;";
        lblErr.setStyle(errStyle);

        Label lblTitulo = new Label("Nada añadido aún");
        VBox listaItems = new VBox(0);
        listaItems.setStyle("-fx-background-color: white;");
        ScrollPane scroll = new ScrollPane(listaItems);
        scroll.setFitToWidth(true); scroll.setMaxHeight(300); scroll.setPrefWidth(300); scroll.setMinWidth(280);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: #C2C8D0;"
                + " -fx-border-radius: 6; -fx-border-width: 1;");

        Runnable actualizarTitulo = () -> {
            int n = lote.size();
            lblTitulo.setText(n == 0 ? "Nada añadido aún" : (n + " en pulido"));
            lblTitulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: " + (n == 0 ? "#586376" : "#2E7D32") + ";");
        };

        final List<com.reparaciones.models.Cliente> clientesActivos = new ArrayList<>();
        final List<com.reparaciones.models.Cliente> clientesTodos   = new ArrayList<>();
        try {
            clientesActivos.addAll(clienteDAO.getActivos());
            clientesTodos.addAll(clienteDAO.getAll());
        } catch (SQLException ex) { /* no crítico: el pane sigue funcionando */ }

        // ── Estado de selección + orquestación ──────────────────────────────
        FilaPulido[] seleccionada = { null };
        boolean[] cargando = { false };
        Runnable[] render = new Runnable[1];

        // ── Detalle (derecha): edita la fila seleccionada, en vivo ───────────
        Label lblImeiDetCap = new Label("IMEI en curso");
        lblImeiDetCap.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        Label lblImeiDet = new Label("—");
        lblImeiDet.setStyle("-fx-font-family: monospace; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label lblTecDet = new Label("Técnico");
        lblTecDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ComboBox<Tecnico> cbTecDet = new ComboBox<>();
        cbTecDet.setMaxWidth(Double.MAX_VALUE);
        cbTecDet.setVisibleRowCount(8);
        cbTecDet.getItems().addAll(tecnicosModal);
        cbTecDet.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Tecnico t) { return t == null ? "" : t.getNombre(); }
            @Override public Tecnico fromString(String s) { return null; }
        });
        Label lblCliDet = new Label("Cliente");
        lblCliDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        Button btnCliDet = new Button("Cliente");
        btnCliDet.setMaxWidth(Double.MAX_VALUE);
        btnCliDet.setStyle("-fx-font-size: 12px; -fx-background-color: white; -fx-border-color: #C2C8D0;"
                + " -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 10 6 10; -fx-cursor: hand;");
        Label lblComDet = new Label("Comentario");
        lblComDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea tfComDet = new TextArea();
        tfComDet.setWrapText(true); tfComDet.setPrefRowCount(2);
        tfComDet.setPromptText("Instrucciones para el técnico...");
        tfComDet.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");
        VBox detalleBox = new VBox(8, lblImeiDetCap, lblImeiDet, lblTecDet, cbTecDet, lblCliDet, btnCliDet, lblComDet, tfComDet);
        detalleBox.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-border-width: 1; -fx-padding: 16;");
        HBox.setHgrow(detalleBox, javafx.scene.layout.Priority.ALWAYS);
        detalleBox.setDisable(true);

        java.util.function.Consumer<FilaPulido> cargarDetalle = fila -> {
            seleccionada[0] = fila;
            cargando[0] = true;
            detalleBox.setDisable(fila == null);
            lblImeiDet.setText(fila == null ? "—" : fila.imei);
            cbTecDet.setValue(fila == null ? null : fila.tecnico);
            btnCliDet.setText(fila == null ? "Cliente"
                    : (fila.sinCliente ? "— Sin cliente —" : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente")));
            tfComDet.setText(fila == null || fila.comentario == null ? "" : fila.comentario);
            cargando[0] = false;
            if (render[0] != null) render[0].run();
        };

        cbTecDet.valueProperty().addListener((o, a, b) -> {
            if (cargando[0] || seleccionada[0] == null) return;
            seleccionada[0].tecnico = b;
            render[0].run();
        });
        tfComDet.textProperty().addListener((o, a, b) -> {
            if (cargando[0] || seleccionada[0] == null) return;
            seleccionada[0].comentario = b.trim();
        });
        btnCliDet.setOnAction(ev -> {
            if (seleccionada[0] == null) return;
            FilaPulido fila = seleccionada[0];
            Integer idActual = fila.cliente != null ? fila.cliente.getIdCli() : null;
            java.util.Optional<Integer> sel = com.reparaciones.utils.SelectorClienteDialog.elegir(clientesActivos, idActual);
            if (sel.isEmpty()) return;
            Integer idCli = sel.get() == -1 ? null : sel.get();
            fila.sinCliente = (sel.get() == -1);
            fila.cliente = idCli == null ? null
                    : clientesActivos.stream().filter(c -> c.getIdCli() == idCli).findFirst().orElse(null);
            btnCliDet.setText(fila.sinCliente ? "— Sin cliente —"
                    : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente"));
            onClienteCambiado.accept(fila);
            render[0].run();
        });

        // ── Lista (izquierda): resumen por fila, seleccionable ──────────────
        render[0] = () -> {
            listaItems.getChildren().clear();
            for (FilaPulido fila : lote) {
                Label lblImei = new Label(fila.imei);
                lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
                String tec = fila.tecnico != null ? fila.tecnico.getNombre() : "(sin técnico)";
                String cli = fila.sinCliente ? "sin cliente" : (fila.cliente != null ? fila.cliente.getNombre() : "—");
                Label lblResumen = new Label(tec + " · " + cli);
                lblResumen.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");
                VBox info = new VBox(1, lblImei, lblResumen);
                HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
                Label btnX = new Label("✕");
                String xBase = "-fx-text-fill: #c2b3b3; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;";
                btnX.setStyle(xBase);
                btnX.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                btnX.setOnMouseEntered(ev -> btnX.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"));
                btnX.setOnMouseExited(ev -> btnX.setStyle(xBase));
                btnX.setOnMouseClicked(ev -> {
                    lote.remove(fila);
                    if (seleccionada[0] == fila) cargarDetalle.accept(null);
                    actualizarTitulo.run(); render[0].run(); onChange.run();
                });
                HBox row = new HBox(8, info, btnX);
                row.setAlignment(Pos.CENTER_LEFT);
                boolean sel = (fila == seleccionada[0]);
                row.setStyle("-fx-padding: 8; -fx-cursor: hand; -fx-border-color: transparent transparent #EEF1F5 transparent;"
                        + " -fx-border-width: 0 0 1 0;" + (sel ? " -fx-background-color: #E8EEF7;" : ""));
                row.setOnMouseClicked(ev -> { if (ev.getTarget() != btnX) cargarDetalle.accept(fila); });
                listaItems.getChildren().add(row);
            }
            actualizarTitulo.run();
        };

        // Refresco global de cliente (lo llama propagarCliente al sincronizar por IMEI)
        refrescadoresCliente.add(() -> {
            if (seleccionada[0] != null) {
                FilaPulido f = seleccionada[0];
                btnCliDet.setText(f.sinCliente ? "— Sin cliente —" : (f.cliente != null ? f.cliente.getNombre() : "Cliente"));
            }
            render[0].run();
        });

        // ── Alta de fila ────────────────────────────────────────────────────
        java.util.function.Consumer<String> agregar = imei -> {
            FilaPulido fila = new FilaPulido(imei, cbTec.getValue(), taCom.getText().trim());
            lote.add(fila);
            sembrarCliente.accept(fila);   // hereda el cliente ya decidido en el modal para este IMEI
            render[0].run();
            onChange.run();
            cargarDetalle.accept(fila);    // auto-selecciona la recién escaneada
            // Precargar el cliente que el IMEI ya tuviera en BD (si no hay decisión en el modal), en 2º plano.
            new Thread(() -> {
                Integer idCli = null;
                try { idCli = telefonoDAO.getClienteId(imei); } catch (Exception ignore) {}
                final Integer idCliRes = idCli;
                javafx.application.Platform.runLater(() -> {
                    if (idCliRes != null && fila.cliente == null && !fila.sinCliente) {
                        com.reparaciones.models.Cliente existente = clientesTodos.stream()
                                .filter(c -> c.getIdCli() == idCliRes).findFirst().orElse(null);
                        if (existente != null) {
                            fila.cliente = existente;
                            if (seleccionada[0] == fila) btnCliDet.setText(existente.getNombre());
                            render[0].run();
                        }
                    }
                });
            }, "pulido-precarga-cliente").start();
        };

        Runnable intentar = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            if (cbTec.getValue() == null) { lblErr.setStyle(errStyle); lblErr.setText("Selecciona un técnico por defecto primero."); return; }
            if (lote.stream().anyMatch(f -> f.imei.equals(imei))) { lblErr.setStyle(errStyle); lblErr.setText("Ese IMEI ya está en la lista de pulido."); return; }
            lblErr.setText("");
            agregar.accept(imei);
            javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) { String solo = n.replaceAll("[^\\d]", ""); javafx.application.Platform.runLater(() -> tfScan.setText(solo)); return; }
            if (n.length() > 15) {
                com.reparaciones.utils.ImeiUtils.ResultadoPegado res = com.reparaciones.utils.ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == com.reparaciones.utils.ImeiUtils.TipoPegado.CORRUPTO) {
                    javafx.application.Platform.runLater(() -> { tfScan.clear(); lblErr.setStyle(errStyle); lblErr.setText("Algún IMEI del pegado está corrupto."); });
                    return;
                }
                if (cbTec.getValue() == null) {
                    javafx.application.Platform.runLater(() -> { tfScan.clear(); lblErr.setStyle(errStyle); lblErr.setText("Selecciona un técnico por defecto primero."); });
                    return;
                }
                int add = 0, dup = 0;
                for (String imei : res.imeis()) { if (lote.stream().anyMatch(f -> f.imei.equals(imei))) { dup++; continue; } agregar.accept(imei); add++; }
                final String resumen = add + " IMEIs añadidos" + (dup > 0 ? " · " + dup + " ya estaban." : ".");
                javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); lblErr.setStyle(okStyle); lblErr.setText(resumen); });
                return;
            }
            lblErr.setStyle(errStyle);
            lblErr.setText("");
            if (n.length() == 15) intentar.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) intentar.run(); });

        render[0].run();

        HBox cols = new HBox(18, scroll, detalleBox);
        return new VBox(8, lblTec, cbTec, lblCom, taCom, new Separator(), lblScan, tfScan, lblErr,
                new Separator(), lblTitulo, cols);
    }
```

Notas de por qué compila/funciona:
- Firma idéntica → el call site (~1646) no cambia.
- `render`, `seleccionada`, `cargando` son holders `[]` (patrón ya usado en el fichero) para fwd-ref entre `render[0]`, `cargarDetalle` y los listeners.
- `sembrarCliente` y `onClienteCambiado` se siguen invocando (heredar decisión / sync por-IMEI).
- `refrescadoresCliente` recibe **un** refrescador global (antes uno por fila); `propagarCliente` lo llama y re-renderiza + refresca el detalle.
- La precarga de BD respeta el mapa (`fila.cliente == null && !fila.sinCliente`), igual que hoy.

- [ ] **Step 2: Compilar**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente`: `mvn -o -q compile`
Expected: BUILD SUCCESS. (Pueden quedar warnings de imports preexistentes `HashMap`/`Map`/`Popup`, no introducidos aquí.)

- [ ] **Step 3: Smoke manual**

Run: `mvn -o javafx:run`. Login SuperTécnico → "Asignar trabajos" → pestaña **Pulido**. Verificar:
- Fija "Técnico por defecto" y escanea 2-3 IMEIs → aparecen en la **lista izquierda** (resumen IMEI · técnico · cliente); la última queda **seleccionada** y cargada en el **detalle derecho**.
- Selecciona una fila (clic) → se resalta y el detalle muestra sus valores.
- Cambia **técnico**/**comentario** en el detalle → el resumen de la fila se actualiza.
- Botón **cliente** en el detalle → `SelectorClienteDialog`; elige uno y "— Sin cliente —"; el resumen y el botón reflejan el estado.
- **✕** en una fila → la quita; si era la seleccionada, el detalle se deshabilita ("—").
- **Sync por-IMEI:** mismo IMEI en pulido y en rep/glass → cambiar cliente en una cola se refleja en pulido (resumen + detalle) tras el pick.
- **Guardar** persiste las filas de pulido (técnico/cliente/comentario) como antes; sin traza de error en consola.

Si algo falla, depurar antes de commitear (superpowers:systematic-debugging).

- [ ] **Step 4: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(pulido): panel de pulido a master-detail (lista + detalle) en el modal de asignacion"
```

---

## Notas de cierre
- Rama `feature/…`; al terminar, merge con OK del usuario ([[feedback_merge_confirmacion]]) y actualizar [[project_backlog_mejoras_asignaciones]].
- Fase 3 (paridad de detalle: modelo y/o multi-técnico) queda aparte.
