# Cluster D · Fase 2 — Pulido master-detail (revisado) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reescribir `construirPulidoPane` a lista-izquierda + detalle-derecha (master-detail, sin Asignar), con cliente **autocompletado inline** (como rep/glass), sin defaults ni arrastre, y **bloquear "Guardar"** si alguna fila de pulido no tiene técnico.

**Architecture:** Reescritura del método JavaFX `construirPulidoPane` + un ajuste en `renderPila` (dentro de `abrirFormularioAsignacion`) para el bloqueo de Guardar. Mantiene modelo `FilaPulido`, guardado, escaneo y la lógica de cliente sin-cliente/sync (vía `onClienteCambiado`/`sembrarCliente`). El control de cliente inline se replica del de rep/glass, operando sobre la fila seleccionada.

**Tech Stack:** Java 17, JavaFX, Maven. Sin tests de UI → verificación por **compilación** + **smoke manual**.

## Global Constraints
- **Solo cliente.** No tocar servidor/DAOs/`FilaPulido`. Guardado (`btnGuardar`) recorre `lotePulido` igual; solo cambia su `disable`.
- Firma de `construirPulidoPane` **sin cambios** (6 params); el call site no se toca.
- **Sin rojo/verde ni "Asignar".** Una sola cola; filas nacen listas.
- **Sin sección de defaults** y **sin arrastre**: cada IMEI nace **sin técnico** y **sin comentario**; cliente por precarga/mapa.
- **Cliente = autocompletado inline** (sentinel "— Sin cliente —" id `-1`, restaurar-al-blur), llamando `onClienteCambiado.accept(fila)` al confirmar.
- **Bloqueo estricto:** fila sin técnico → "(sin técnico)" en rojo en la lista y **Guardar deshabilitado** hasta que todas tengan técnico. El detalle llama `onChange` al cambiar técnico para recalcular.
- 1 técnico por fila. Maven por Bash. No `Co-Authored-By`. No push sin OK.
- Fichero: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`.

---

### Task 1: Reescribir `construirPulidoPane` + bloqueo de Guardar por sin-técnico

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `FilaPulido`, params del método, `SelectorClienteDialog` (ya NO se usa aquí — se sustituye por inline), `telefonoDAO.getClienteId`, `clienteDAO.getActivos/getAll`, `ImeiUtils`, `Cliente(-1,...)` sentinel.
- Produces: mismo `VBox`; `lotePulido` con filas `sin técnico` posibles (validadas por Guardar).

- [ ] **Step 1: Reemplazar el método completo `construirPulidoPane`**

Sustituye **todo** el método (firma hasta su `}` de cierre) por:

```java
    private VBox construirPulidoPane(List<FilaPulido> lote, List<Tecnico> tecnicosModal, Runnable onChange,
                                     java.util.function.Consumer<FilaPulido> onClienteCambiado,
                                     List<Runnable> refrescadoresCliente,
                                     java.util.function.Consumer<FilaPulido> sembrarCliente) {
        // ── Escaneo ─────────────────────────────────────────────────────────
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

        // ── Lista (izquierda), estilo "Asignados/verde" ─────────────────────
        Label lblTitulo = new Label("Nada añadido aún");
        VBox listaItems = new VBox(0);
        listaItems.setStyle("-fx-background-color: white;");
        ScrollPane scroll = new ScrollPane(listaItems);
        scroll.setFitToWidth(true); scroll.setMaxHeight(300); scroll.setPrefWidth(300); scroll.setMinWidth(280);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: #BFE0C2;"
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
        } catch (SQLException ex) { /* no crítico */ }

        // ── Estado ──────────────────────────────────────────────────────────
        FilaPulido[] seleccionada = { null };
        boolean[] cargando = { false };
        Runnable[] render = new Runnable[1];

        // ── Detalle (derecha) ───────────────────────────────────────────────
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

        // Cliente: autocompletado inline (replica del de rep/glass), sobre la fila seleccionada.
        Label lblCliDet = new Label("Cliente (opcional)");
        lblCliDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        final com.reparaciones.models.Cliente SIN_CLIENTE = new com.reparaciones.models.Cliente(-1, "— Sin cliente —", true, null);
        javafx.collections.ObservableList<com.reparaciones.models.Cliente> todosClientes =
                FXCollections.observableArrayList(clientesActivos);
        todosClientes.add(0, SIN_CLIENTE);
        FilteredList<com.reparaciones.models.Cliente> clientesFiltrados = new FilteredList<>(todosClientes, c -> true);
        TextField tfCliente = new TextField();
        tfCliente.setPromptText("Escribe cliente...");
        tfCliente.setMaxWidth(Double.MAX_VALUE);
        tfCliente.setStyle("-fx-background-color: #001232; -fx-background-radius: 24;"
                + "-fx-border-color: transparent; -fx-border-radius: 24; -fx-border-width: 0;"
                + "-fx-text-fill: #FAFAFA; -fx-prompt-text-fill: rgba(255,255,255,0.45);"
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 12 4 12;");
        ListView<com.reparaciones.models.Cliente> listaClientes = new ListView<>(clientesFiltrados);
        listaClientes.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;"
                + "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");
        listaClientes.setFixedCellSize(30);
        listaClientes.setPrefWidth(300);
        listaClientes.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.reparaciones.models.Cliente cli, boolean empty) {
                super.updateItem(cli, empty);
                if (empty || cli == null) { setText(null); setStyle(""); }
                else { setText(cli.getNombre());
                    setStyle("-fx-background-color: white; -fx-text-fill: #001232;"
                            + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12 6 12;"); }
            }
        });
        javafx.stage.Popup popupCliente = new javafx.stage.Popup();
        popupCliente.setAutoHide(true);
        popupCliente.getContent().add(listaClientes);
        boolean[] actualizandoCli = { false };
        Runnable mostrarPopupCli = () -> {
            if (clientesFiltrados.isEmpty() || tfCliente.getScene() == null) { popupCliente.hide(); return; }
            listaClientes.setPrefHeight(Math.min(clientesFiltrados.size(), 6) * 30 + 4);
            if (!popupCliente.isShowing()) {
                javafx.geometry.Bounds b = tfCliente.localToScreen(tfCliente.getBoundsInLocal());
                if (b != null) popupCliente.show(tfCliente, b.getMinX(), b.getMaxY() + 1);
            }
        };
        java.util.function.Consumer<com.reparaciones.models.Cliente> confirmarCli = cli -> {
            if (seleccionada[0] == null) return;
            FilaPulido fila = seleccionada[0];
            boolean sin = cli.getIdCli() == -1;
            fila.sinCliente = sin;
            fila.cliente = sin ? null : cli;
            actualizandoCli[0] = true;
            tfCliente.setText(cli.getNombre());
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCli[0] = false;
            popupCliente.hide();
            onClienteCambiado.accept(fila);   // registra en el mapa por-IMEI + propaga
            render[0].run();
        };
        tfCliente.textProperty().addListener((obs, o, n) -> {
            if (actualizandoCli[0]) return;
            String lower = n == null ? "" : n.trim().toLowerCase();
            clientesFiltrados.setPredicate(c -> lower.isEmpty() || c.getNombre().toLowerCase().contains(lower));
            mostrarPopupCli.run();
        });
        tfCliente.setOnAction(e -> { if (!clientesFiltrados.isEmpty()) confirmarCli.accept(clientesFiltrados.get(0)); });
        tfCliente.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) javafx.application.Platform.runLater(() -> {
                popupCliente.hide();
                String texto = tfCliente.getText() == null ? "" : tfCliente.getText().trim();
                com.reparaciones.models.Cliente exacto = todosClientes.stream()
                        .filter(c -> c.getNombre().equalsIgnoreCase(texto)).findFirst().orElse(null);
                if (exacto != null) { confirmarCli.accept(exacto); return; }
                actualizandoCli[0] = true;
                FilaPulido fila = seleccionada[0];
                tfCliente.setText(fila == null ? "" : (fila.sinCliente ? "— Sin cliente —"
                        : (fila.cliente != null ? fila.cliente.getNombre() : "")));
                clientesFiltrados.setPredicate(c -> true);
                actualizandoCli[0] = false;
            });
        });
        listaClientes.setOnMouseClicked(e -> {
            com.reparaciones.models.Cliente sel = listaClientes.getSelectionModel().getSelectedItem();
            if (sel != null) confirmarCli.accept(sel);
        });

        Label lblComDet = new Label("Comentario");
        lblComDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        TextArea tfComDet = new TextArea();
        tfComDet.setWrapText(true); tfComDet.setPrefRowCount(2);
        tfComDet.setPromptText("Instrucciones para el técnico...");
        tfComDet.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 4;"
                + " -fx-background-radius: 4; -fx-text-fill: #2C3B54; -fx-font-size: 13px;");

        VBox detalleBox = new VBox(8, lblImeiDetCap, lblImeiDet, lblTecDet, cbTecDet, lblCliDet, tfCliente, lblComDet, tfComDet);
        detalleBox.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-border-width: 1; -fx-padding: 16;");
        HBox.setHgrow(detalleBox, javafx.scene.layout.Priority.ALWAYS);
        detalleBox.setDisable(true);

        java.util.function.Consumer<FilaPulido> cargarDetalle = fila -> {
            seleccionada[0] = fila;
            cargando[0] = true;
            detalleBox.setDisable(fila == null);
            lblImeiDet.setText(fila == null ? "—" : fila.imei);
            cbTecDet.setValue(fila == null ? null : fila.tecnico);
            actualizandoCli[0] = true;
            tfCliente.setText(fila == null ? "" : (fila.sinCliente ? "— Sin cliente —"
                    : (fila.cliente != null ? fila.cliente.getNombre() : "")));
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCli[0] = false;
            tfComDet.setText(fila == null || fila.comentario == null ? "" : fila.comentario);
            cargando[0] = false;
            if (render[0] != null) render[0].run();
        };

        cbTecDet.valueProperty().addListener((o, a, b) -> {
            if (cargando[0] || seleccionada[0] == null) return;
            seleccionada[0].tecnico = b;
            render[0].run();
            onChange.run();   // recalcula el bloqueo de Guardar (sin-técnico)
        });
        tfComDet.textProperty().addListener((o, a, b) -> {
            if (cargando[0] || seleccionada[0] == null) return;
            seleccionada[0].comentario = b.trim();
        });

        // ── Render de la lista ──────────────────────────────────────────────
        render[0] = () -> {
            listaItems.getChildren().clear();
            for (FilaPulido fila : lote) {
                Label lblImei = new Label(fila.imei);
                lblImei.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
                String cli = fila.sinCliente ? "sin cliente" : (fila.cliente != null ? fila.cliente.getNombre() : "—");
                Label lblResumen = new Label();
                if (fila.tecnico == null) {
                    lblResumen.setText("(sin técnico) · " + cli);
                    lblResumen.setStyle("-fx-font-size: 11px; -fx-text-fill: #C0392B; -fx-font-weight: bold;");
                } else {
                    lblResumen.setText(fila.tecnico.getNombre() + " · " + cli);
                    lblResumen.setStyle("-fx-font-size: 11px; -fx-text-fill: #586376;");
                }
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
                actualizandoCli[0] = true;
                tfCliente.setText(f.sinCliente ? "— Sin cliente —" : (f.cliente != null ? f.cliente.getNombre() : ""));
                actualizandoCli[0] = false;
            }
            render[0].run();
        });

        // ── Alta de fila ────────────────────────────────────────────────────
        java.util.function.Consumer<String> agregar = imei -> {
            FilaPulido fila = new FilaPulido(imei, null, "");   // sin técnico, sin comentario
            lote.add(fila);
            sembrarCliente.accept(fila);
            render[0].run();
            onChange.run();
            cargarDetalle.accept(fila);   // auto-selecciona
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
                            if (seleccionada[0] == fila) {
                                actualizandoCli[0] = true; tfCliente.setText(existente.getNombre()); actualizandoCli[0] = false;
                            }
                            render[0].run();
                        }
                    }
                });
            }, "pulido-precarga-cliente").start();
        };

        Runnable intentar = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
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
        return new VBox(8, lblScan, tfScan, lblErr, new Separator(), lblTitulo, cols);
    }
```

- [ ] **Step 2: Bloquear "Guardar" cuando haya filas de pulido sin técnico**

En `renderPila` (dentro de `abrirFormularioAsignacion`), sustituye (actualmente líneas 1468-1473):

```java
            int nPul = lotePulido.size();
```
… (dejar las líneas intermedias `lblProg`/`btnGuardar.setText` igual) …
```java
            btnGuardar.setDisable(nRojoGlobal != 0 || (nVerdeGlobal + nPul) == 0);
```

por: añade el conteo de pulido sin técnico y mételo en el `setDisable`. Es decir, cambia la línea `int nPul = lotePulido.size();` a:

```java
            int nPul = lotePulido.size();
            int pulidoSinTecnico = (int) lotePulido.stream().filter(f -> f.tecnico == null).count();
```

y cambia la línea `btnGuardar.setDisable(nRojoGlobal != 0 || (nVerdeGlobal + nPul) == 0);` a:

```java
            btnGuardar.setDisable(nRojoGlobal != 0 || pulidoSinTecnico > 0 || (nVerdeGlobal + nPul) == 0);
```

- [ ] **Step 3: Compilar**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente`: `mvn -o -q compile`
Expected: BUILD SUCCESS. (Warnings preexistentes de imports HashMap/Map/Popup admisibles.)

- [ ] **Step 4: Smoke manual**

Run: `mvn -o javafx:run`. Login SuperTécnico → "Asignar trabajos" → **Pulido**. Verificar:
- Escanea 2-3 IMEIs → caen en la lista izquierda, cada uno **"(sin técnico)" en rojo**; la última queda **seleccionada**. **"Guardar" deshabilitado**.
- En el detalle, fija **técnico** → el resumen deja de estar en rojo. Cuando **todas** las filas tienen técnico, **"Guardar" se habilita**.
- **Cliente inline** en el detalle: escribe para filtrar, elige uno, elige "— Sin cliente —"; borra a medias y pierde foco → **restaura** el cliente comprometido. El resumen refleja el cliente.
- **Comentario** se edita en el detalle.
- **✕** quita la fila; si era la seleccionada, el detalle se deshabilita ("—").
- **Sync por-IMEI:** mismo IMEI en pulido y rep/glass → cambiar cliente en una cola se refleja en pulido (resumen + campo inline).
- **Guardar** persiste las filas (técnico/cliente/comentario) como antes; sin traza de error.

Si algo falla, depurar (superpowers:systematic-debugging) antes de commitear.

- [ ] **Step 5: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(pulido): master-detail con cliente inline y bloqueo de Guardar sin tecnico"
```

---

## Notas de cierre
- Rama `feature/cluster-d-fase2-pulido` (ya existe; incluye un commit previo `923712c` del diseño anterior — al revisar el diff final usar base `cc5dca4` para ver el neto).
- Merge con OK del usuario ([[feedback_merge_confirmacion]]); actualizar [[project_backlog_mejoras_asignaciones]].
- Fase 3 (modelo / multi-técnico) aparte.
