# Rediseño de la fila "Otro" — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) o superpowers:executing-plans para ejecutar tarea por tarea. Los pasos usan checkbox (`- [ ]`).

**Goal:** Convertir la fila "Otro" del modal de reparación en una sección de "Otras acciones": varias acciones de texto libre (descripción obligatoria), sin contador/reutilizado/stock, cada una guardada como un `R*` con el componente `otroi<modelo>` y `cantidad = 0`.

**Architecture:** Solo cliente, en `FormularioReparacionController` + su FXML. Se quita la `FilaUI` del grupo "otro" y se añade una nueva clase interna `OtrasAccionesUI` (lista compacta + scroll + botón añadir). El guardado emite una `FilaReparacion` por acción con `cantidad = 0` (stock neutro vía el endpoint existente `insertarCompleta`). La edición desde el historial detecta el "otro" y muestra un editor de texto. Sin cambios de servidor.

**Tech Stack:** Java 17 + JavaFX 21. Sin tests automatizados de UI; verificación por `mvn compile -q` + checklist manual.

**Nota sobre TDD:** No hay banco de pruebas para controladores JavaFX. Cada tarea se verifica con `mvn compile -q` (sin salida = OK) y al final con un checklist manual. No se escriben tests unitarios (no hay infraestructura).

---

## File Structure

- `src/main/resources/views/FormularioReparacionView.fxml` — añadir un contenedor `<VBox fx:id="contenedorOtros"/>` para la sección, y un `fx:id` a la cabecera de columnas (para ocultarla al editar un "otro").
- `src/main/java/com/reparaciones/controllers/FormularioReparacionController.java` — nueva clase interna `OtrasAccionesUI`; integración en `cargarFilas`, en el listener de modelo, en `actualizarBoton`, en `ejecutarGuardarNueva`; flujo de edición de "otro" en `initEditar` + `ejecutarGuardarEdicion`.

Sin cambios de servidor (el `cantidad = 0` deja las operaciones de stock en no-op en los endpoints existentes).

**Referencia — constructor de `FilaReparacion`** (8 args, el último siempre `null`):
`new FilaReparacion(int idCom, int cantidad, boolean reutilizado, String observacion, String prefijo, boolean esSolicitud, String descripcionSolicitud, null)`

**Referencia — helper existente** `extraerModelo(String tipo, String prefijo)`: para un componente "otroi15" con prefijo "otro" devuelve "15".

---

## Task 1: Rama

- [ ] **Step 1: Crear la rama**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente"
git checkout main
git checkout -b feat/rediseno-fila-otro
```

---

## Task 2: FXML — contenedor de la sección y fx:id de la cabecera

**Files:** `src/main/resources/views/FormularioReparacionView.fxml`

- [ ] **Step 1: Dar fx:id a la cabecera de columnas**

Localizar el `<HBox>` de la cabecera de columnas (el que contiene los labels "Unit (+/-)", "Componente", "SKU"…). Añadirle `fx:id="cabeceraColumnas"`:
```xml
<HBox fx:id="cabeceraColumnas" style="-fx-background-color: #BCC2CB;
             -fx-border-color: transparent transparent #A8AEB7 transparent;
             -fx-border-width: 0 0 1 0;">
```

- [ ] **Step 2: Añadir el contenedor de "Otras acciones"**

Insertar, justo DESPUÉS de `<VBox fx:id="contenedorFilas"/>` y ANTES de `<HBox fx:id="zonaGuardar" ...>`:
```xml
    <!-- Sección "Otras acciones" (se rellena en el controlador) -->
    <VBox fx:id="contenedorOtros"/>
```

- [ ] **Step 3: Compilar**

Run: `mvn compile -q`
Expected: sin salida.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/views/FormularioReparacionView.fxml
git commit -m "feat: contenedor FXML para la seccion Otras acciones"
```

---

## Task 3: Clase `OtrasAccionesUI` e integración en el modal

**Files:** `src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

- [ ] **Step 1: Campos @FXML y de estado**

Junto a los demás campos `@FXML` (cerca de `@FXML private VBox contenedorFilas;`), añadir:
```java
    @FXML private VBox contenedorOtros;
    @FXML private javafx.scene.layout.HBox cabeceraColumnas;
```
Y junto a `private final List<FilaUI> filasUI = new ArrayList<>();`, añadir:
```java
    private OtrasAccionesUI otrasAcciones;
```

- [ ] **Step 2: Añadir la clase interna `OtrasAccionesUI`**

Añadir esta clase interna estática al final de la clase `FormularioReparacionController` (junto a la clase `FilaUI`):
```java
    // ─── OtrasAccionesUI ──────────────────────────────────────────────────────
    /** Sección de "Otras acciones": varias acciones de texto libre (sin pieza/stock).
     *  Cada acción se guarda como un R* con el componente otroi<modelo> y cantidad 0. */
    static class OtrasAccionesUI {
        private final VBox root;
        private final VBox listaLineas = new VBox(5);
        private final Label badge = new Label("0");
        private final List<Componente> otroComponentes;
        private final Image imgBorrar;
        private Componente otroSel = null;   // otroi<modelo> del modelo actual
        private Runnable onCambio;

        OtrasAccionesUI(List<Componente> otroComponentes, Image imgBorrar) {
            this.otroComponentes = otroComponentes;
            this.imgBorrar = imgBorrar;

            Label titulo = new Label("OTRAS ACCIONES");
            titulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #5B3FA0;");
            badge.setStyle("-fx-background-color: #5B3FA0; -fx-text-fill: white; -fx-font-size: 10px;" +
                    "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;");
            Label sub = new Label("no descuentan stock");
            sub.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #8A7FA8;");
            HBox header = new HBox(8, titulo, badge, sub);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-background-color: #EDE7F6; -fx-padding: 7 14 7 14;");

            javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(listaLineas);
            scroll.setFitToWidth(true);
            scroll.setMaxHeight(120);
            scroll.setStyle("-fx-background-color: white; -fx-border-color: #D9CFEC; -fx-border-radius: 6; -fx-background-radius: 6;");
            listaLineas.setStyle("-fx-padding: 5;");

            Button btnAdd = new Button("+ Añadir acción");
            btnAdd.setStyle("-fx-background-color: #5B3FA0; -fx-text-fill: white; -fx-font-size: 11.5px;" +
                    "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12 6 12;");
            btnAdd.setOnAction(e -> agregarLinea(""));

            Label hint = new Label("La descripción es obligatoria: una acción vacía no se guarda.");
            hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #8A7FA8;");

            VBox cuerpo = new VBox(8, scroll, btnAdd, hint);
            cuerpo.setStyle("-fx-background-color: #F7F4FB; -fx-padding: 8 14 12 14;");

            root = new VBox(header, cuerpo);
            root.setVisible(false); root.setManaged(false);
        }

        /** Selecciona el otroi<modelo> según el modelo elegido; muestra la sección si existe. */
        void setModelo(String modelo) {
            otroSel = (modelo == null) ? null : otroComponentes.stream()
                    .filter(c -> extraerModelo(c.getTipo(), "otro").equals(modelo))
                    .findFirst().orElse(null);
            boolean disponible = otroSel != null;
            root.setVisible(disponible); root.setManaged(disponible);
        }

        private void agregarLinea(String texto) {
            TextField tf = new TextField(texto);
            tf.setPromptText("Describe la acción (ej. limpiar cámara)");
            tf.setStyle("-fx-font-size: 12px;");
            HBox.setHgrow(tf, Priority.ALWAYS);
            ImageView iv = new ImageView(imgBorrar);
            iv.setFitWidth(18); iv.setFitHeight(18); iv.setPreserveRatio(true);
            Button btnDel = new Button();
            btnDel.setGraphic(iv);
            btnDel.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
            HBox linea = new HBox(8, tf, btnDel);
            linea.setAlignment(Pos.CENTER_LEFT);
            btnDel.setOnAction(e -> { listaLineas.getChildren().remove(linea); actualizar(); });
            tf.textProperty().addListener((o, a, b) -> actualizar());
            listaLineas.getChildren().add(linea);
            tf.requestFocus();
            actualizar();
        }

        private void actualizar() {
            badge.setText(String.valueOf(getDescripciones().size()));
            if (onCambio != null) onCambio.run();
        }

        /** Descripciones no vacías (trim) de las líneas. */
        List<String> getDescripciones() {
            List<String> out = new ArrayList<>();
            for (javafx.scene.Node n : listaLineas.getChildren()) {
                if (n instanceof HBox h && !h.getChildren().isEmpty()
                        && h.getChildren().get(0) instanceof TextField tf) {
                    String t = tf.getText() == null ? "" : tf.getText().trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
            return out;
        }

        int getIdComOtro() { return otroSel != null ? otroSel.getIdCom() : -1; }
        boolean hayAccion() { return otroSel != null && !getDescripciones().isEmpty(); }
        boolean esOtro(int idCom) { return otroComponentes.stream().anyMatch(c -> c.getIdCom() == idCom); }
        void setOnCambio(Runnable r) { this.onCambio = r; }
        VBox getRoot() { return root; }
    }
```

- [ ] **Step 3: Integrar en `cargarFilas()` (omitir el grupo "otro" y crear la sección)**

En `cargarFilas()`, dentro del bucle `for (Map.Entry<String, List<Componente>> entry : grupos.entrySet())`, justo después de `if (entry.getValue().isEmpty()) continue;`, añadir:
```java
                if (entry.getKey().equals("otro")) {
                    otrasAcciones = new OtrasAccionesUI(entry.getValue(), imgBorrar);
                    otrasAcciones.setOnCambio(this::actualizarBoton);
                    contenedorOtros.getChildren().add(otrasAcciones.getRoot());
                    continue;
                }
```

- [ ] **Step 4: Mostrar/actualizar la sección al cambiar el modelo**

En `configurarFiltroModelo()`, dentro del listener `cbFiltroModelo.valueProperty().addListener((obs, o, n) -> { ... })`, después del bucle `for (FilaUI fila : filasUI) { fila.aplicarFiltroModelo(n); }`, añadir:
```java
            if (otrasAcciones != null) otrasAcciones.setModelo(n);
```

- [ ] **Step 5: Compilar**

Run: `mvn compile -q`
Expected: sin salida.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: seccion Otras acciones (OtrasAccionesUI) en el modal de reparacion"
```

---

## Task 4: Guardado y validación de las acciones "otro"

**Files:** `src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

- [ ] **Step 1: Validación del botón Guardar**

En `actualizarBoton()`, en la rama `else` (no edición), sustituir:
```java
            boolean activa = filasUI.stream().anyMatch(FilaUI::isActiva);
```
por:
```java
            boolean activa = filasUI.stream().anyMatch(FilaUI::isActiva)
                    || (otrasAcciones != null && otrasAcciones.hayAccion());
```

- [ ] **Step 2: Emitir las acciones "otro" al guardar**

En `ejecutarGuardarNueva()`, justo DESPUÉS del bucle que rellena `filasActivas` (el `for (FilaUI fila : filasUI) { ... }` que termina antes del comentario `// Si solo había filas agotadas...`), añadir:
```java
        // Acciones "otro": una FilaReparacion por descripción, cantidad 0 (stock neutro)
        if (otrasAcciones != null && otrasAcciones.getIdComOtro() != -1) {
            int otroIdCom = otrasAcciones.getIdComOtro();
            for (String desc : otrasAcciones.getDescripciones()) {
                filasActivas.add(new FilaReparacion(otroIdCom, 0, false, desc, "otro", false, null, null));
            }
        }
```

- [ ] **Step 3: Compilar**

Run: `mvn compile -q`
Expected: sin salida.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: guardar acciones Otro como R* con cantidad 0 (stock neutro) + validacion"
```

---

## Task 5: Edición de una acción "otro" desde el historial

**Files:** `src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

Contexto: al "Editar" un "otro" desde el historial se abre este modal vía `abrirEditar(idRep)` → `initEditar(idRep)`, que llama a `reparacionDAO.getDetalleEdicion(idRep)` (devuelve `d.idCom`, `d.observacion`, `d.updatedAt`). Como ya no existe la `FilaUI` "otro", hay que detectar el caso y mostrar un editor de texto.

- [ ] **Step 1: Campos para el modo edición-otro**

Junto a los campos de edición (cerca de `private LocalDateTime updatedAtEdicion;`), añadir:
```java
    private TextArea taEditarOtro;
    private int idComOtroEditar = -1;
```

- [ ] **Step 2: Detectar el "otro" en `initEditar` y mostrar el editor**

En `initEditar(...)`, justo después de:
```java
            this.imeiEditar      = d.imei;
            this.idTecEditar     = d.idTec;
            this.updatedAtEdicion = d.updatedAt;
```
añadir (antes de `lblImei.setText(...)`):
```java
            cargarFilas(); // crea otrasAcciones (necesario para detectar si idCom es "otro")
            if (otrasAcciones != null && otrasAcciones.esOtro(d.idCom)) {
                iniciarEdicionOtro(d);
                return;
            }
```
IMPORTANTE: como `cargarFilas()` se llama aquí, hay que ELIMINAR la llamada `cargarFilas();` que ya existe más abajo en `initEditar` (la que está antes de `Set<Integer> yaReparados = ...`), para no crear las filas dos veces. Localizar esa segunda `cargarFilas();` y borrarla.

- [ ] **Step 3: Método `iniciarEdicionOtro`**

Añadir este método (junto a `initEditar`):
```java
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
```

- [ ] **Step 4: Guardar la edición del "otro"**

En `ejecutarGuardarEdicion()`, al principio del método (antes del `try` existente / del resto de la lógica), añadir:
```java
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
```

- [ ] **Step 5: Compilar**

Run: `mvn compile -q`
Expected: sin salida.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: editar una accion Otro desde el historial (editor de texto)"
```

---

## Task 6: Verificación manual

**Files:** ninguno.

- [ ] **Step 1: Compilación limpia**

Run: `mvn compile -q`
Expected: sin salida.

- [ ] **Step 2: Checklist funcional**

1. Abrir una asignación, seleccionar modelo → aparece la sección "Otras acciones".
2. Añadir varias acciones con texto → "+ Añadir acción" agrega líneas; el badge cuenta; con muchas, hay scroll interno y el modal no se desborda.
3. Dejar una línea vacía → al guardar se descarta (no se guarda); no bloquea.
4. Guardar SOLO con acciones "otro" (sin componente) → la asignación se cierra; cada acción aparece en el historial con Componente = `otroi<modelo>`, Observaciones = el texto.
5. El **stock** del `otroi<modelo>` y de los demás componentes **no cambia** tras guardar acciones "otro".
6. Guardar con componentes normales + acciones "otro" mezclados → todo se guarda correctamente.
7. En el historial, **borrar** una acción "otro" → desaparece; el stock no cambia.
8. En el historial, **editar** una acción "otro" → se abre el editor de texto; cambiar el texto y guardar → se actualiza; el stock no cambia.
9. Exportar CSV del historial → la acción sale con Componente = `otroi<modelo>`, Observaciones = el texto.

- [ ] **Step 3: Finalizar la rama**

Usar superpowers:finishing-a-development-branch para decidir merge/cierre.

---

## Self-Review (cobertura del spec)

- "Otro" = acción libre con otroi<modelo> + descripción en OBSERVACIONES → Task 3 (OtrasAccionesUI) + Task 4 (guardado). ✓
- Varias acciones por modal, descripción obligatoria, sin contador/reutilizado/stock → Task 3 (UI) + Task 4 (cantidad 0). ✓
- UI distinta + compacta + scroll acotado → Task 3 (OtrasAccionesUI: header morado, ScrollPane maxHeight 120, badge). ✓
- Validación del Guardar (acción con texto = activa; vacías descartadas; cerrar solo con otros) → Task 4. ✓
- Stock neutro (crear/editar/borrar) → cantidad 0 en Task 4 y Task 5; borrar usa la lógica existente (no-op con cantidad 0). ✓
- Historial opción B (Componente=otroi, Observaciones=texto) → automático (se guarda con idCom=otroi + OBSERVACIONES); sin cambios en los controllers de historial. ✓
- Editar/borrar desde historial → Task 5 (editar) + borrar existente. ✓
- CSV → automático (misma estructura de columnas). ✓
- Sin cambios de servidor → confirmado (cantidad 0 = no-op en insertarCompleta/editarReparacion/eliminar). ✓
