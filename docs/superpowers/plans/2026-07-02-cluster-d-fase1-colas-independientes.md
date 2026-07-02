# Cluster D · Fase 1 — Colas Rep/Glass independientes · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el modal `abrirFormularioAsignacion()`, separar Reparación y Glass en dos colas independientes (dos listas) y quitar el toggle de tipo por-entrada del detalle, sin cambiar el resultado de datos del guardado.

**Architecture:** Refactor local de un único método (`abrirFormularioAsignacion` en `PendientesSuperTecnicoController.java`). Se sustituye la lista mixta `pila` por `pilaRep` + `pilaGlass` con un accesor `pilaActiva` derivado de `tipoActual[0]`. Contadores por-cola junto a cada lista; total y bloqueo de Guardar globales (rep+glass+pulido). Se elimina el toggle `tgEnt`.

**Tech Stack:** Java 17, JavaFX (UI construida en código, sin FXML para este modal). Build con Maven. Sin cobertura de tests de UI → verificación por **compilación** (`mvn -o compile`) + **smoke manual** (`mvn -o javafx:run`).

## Global Constraints

- **Solo cliente.** No tocar servidor, DAOs ni contratos de endpoints. El guardado debe producir exactamente las mismas asignaciones que hoy.
- **Ejecutar Maven por Bash** primero (`mvn -o …`); PowerShell solo si Bash no puede.
- **No** añadir `Co-Authored-By` de Claude en los commits.
- **No** hacer merge/push/tags sin OK explícito del usuario.
- `EntradaAsignacion.tipo` se conserva y se fija solo al escanear (= `tipoActual[0]`); nunca se muta después.
- Fichero único a tocar: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`.
- Comandos desde `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente`.

---

### Task 1: Quitar el toggle de tipo por-entrada (`tgEnt`) del detalle

Elimina el control `Reparación | Glass` del formulario de detalle y toda su maquinaria. Tras esta tarea, el tipo de cada entrada queda fijado al escanear (ya era así); el modal sigue funcionando con la pila mixta actual. Independiente y revisable por sí sola.

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: nada de tareas previas.
- Produces: `formBox` sin `lblTipoEnt`/`tipoEntradaBox`; desaparecen las variables `tbEntRep`, `tbEntGlass`, `tgEnt`, `actualizandoTipoEnt`. La Task 2 no depende de ellas.

- [ ] **Step 1: Eliminar la construcción del toggle de tipo (bloque `lblTipoEnt`…`tipoEntradaBox`)**

En [PendientesSuperTecnicoController.java](../../../gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java) borra el bloque completo (actualmente líneas 1263-1276):

```java
        // Tipo de la entrada en curso (editable por fila); el handler se enlaza más abajo.
        Label lblTipoEnt = new Label("Tipo");
        lblTipoEnt.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;");
        ToggleButton tbEntRep   = new ToggleButton("Reparación");
        ToggleButton tbEntGlass = new ToggleButton("Glass");
        tbEntRep.getStyleClass().add("toggle-pill-left");
        tbEntGlass.getStyleClass().add("toggle-pill-right");
        tbEntRep.setSelected(true);
        javafx.scene.control.ToggleGroup tgEnt = new javafx.scene.control.ToggleGroup();
        tbEntRep.setToggleGroup(tgEnt);
        tbEntGlass.setToggleGroup(tgEnt);
        boolean[] actualizandoTipoEnt = { false };
        HBox tipoEntradaBox = new HBox(0, tbEntRep, tbEntGlass);
        tipoEntradaBox.setAlignment(Pos.CENTER_LEFT);
```

Déjalo eliminado por completo (no queda nada en su lugar).

- [ ] **Step 2: Quitar `lblTipoEnt` y `tipoEntradaBox` de los hijos de `formBox`**

Sustituye la construcción de `formBox` (actualmente línea 1284):

```java
        VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, lblTipoEnt, tipoEntradaBox, lblModelo, tfModelo,
                headerTecnicos, scrollTecnicos, lblNotaPersist, lblCliente, tfCliente,
                lblComentario, tfComentario, accionesForm);
```

por:

```java
        VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, lblModelo, tfModelo,
                headerTecnicos, scrollTecnicos, lblNotaPersist, lblCliente, tfCliente,
                lblComentario, tfComentario, accionesForm);
```

- [ ] **Step 3: Quitar la sincronización del toggle en `cargarEntrada`**

En `cargarEntrada[0]` (actualmente líneas 1456-1459) borra estas cuatro líneas:

```java
            // Sincroniza el toggle de tipo de la entrada en curso sin disparar su handler
            actualizandoTipoEnt[0] = true;
            (e.tipo == TipoTrabajo.GLASS ? tbEntGlass : tbEntRep).setSelected(true);
            actualizandoTipoEnt[0] = false;
```

La línea siguiente (`recomputeOcupados[0].run();`) se mantiene: sigue usando `e.tipo`.

- [ ] **Step 4: Quitar el listener de `tgEnt`**

Borra el listener completo (actualmente líneas 1602-1610):

```java
        tgEnt.selectedToggleProperty().addListener((obs, o, n) -> {
            if (actualizandoTipoEnt[0]) return;
            if (n == null) { (o == null ? tbEntRep : (ToggleButton) o).setSelected(true); return; }   // no deselección
            if (actual[0] == null) return;
            actual[0].tipo = (n == tbEntGlass) ? TipoTrabajo.GLASS : TipoTrabajo.REPARACION;
            recomputeOcupados[0].run();   // recalcula técnicos ocupados para la nueva categoría
            renderPila[0].run();          // actualiza el badge de la fila
            validarForm.run();
        });
```

La línea anterior (`checkboxes.forEach(cb -> cb.selectedProperty()...)`) y la siguiente (`btnAsignar.setOnAction(...)`) se mantienen.

- [ ] **Step 5: Compilar**

Run: `mvn -o -q compile`
Expected: BUILD SUCCESS, sin errores de referencias a `tbEntRep`/`tbEntGlass`/`tgEnt`/`actualizandoTipoEnt`/`lblTipoEnt`/`tipoEntradaBox`.

Si el compilador reporta alguna referencia superviviente a esos símbolos, elimínala (no debería quedar ninguna).

- [ ] **Step 6: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "refactor(asignaciones): quitar toggle de tipo por-entrada (tgEnt) del detalle"
```

---

### Task 2: Separar `pila` en `pilaRep` + `pilaGlass` con accesor `pilaActiva`

Sustituye la lista mixta por dos listas independientes. La cola visible es la del tipo activo; los contadores junto a cada lista son por-cola; el total y el bloqueo de Guardar son globales (rep+glass+pulido). Es un cambio atómico: los Steps 1-8 deben aplicarse **todos** antes de compilar (el árbol no compila con `pila` a medio migrar).

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `formBox` ya sin `tgEnt` (Task 1). `EntradaAsignacion.tipo` (campo existente), `tipoActual[0]` (`TipoTrabajo[]`), `TipoTrabajo.GLASS`/`REPARACION`, `crearFilaPila(EntradaAsignacion, boolean, Runnable, Runnable)`, `lotePulido` (`List<FilaPulido>`).
- Produces: modal con dos colas; sin `pila`. Fin de Fase 1.

- [ ] **Step 1: Reemplazar la declaración de `pila` por dos listas**

Sustituye (actualmente línea 988):

```java
        List<EntradaAsignacion> pila = new ArrayList<>();
```

por:

```java
        List<EntradaAsignacion> pilaRep   = new ArrayList<>();
        List<EntradaAsignacion> pilaGlass = new ArrayList<>();
```

- [ ] **Step 2: Añadir el accesor `pilaActiva` tras `tipoActual`**

Inmediatamente después de la declaración de `tipoActual` (actualmente línea 993), añade:

```java
        // Cola de la categoría activa (rep/glass); pulido va aparte en lotePulido.
        java.util.function.Supplier<List<EntradaAsignacion>> pilaActiva =
                () -> tipoActual[0] == TipoTrabajo.GLASS ? pilaGlass : pilaRep;
```

- [ ] **Step 3: Reescribir `renderPila` (cola activa + totales globales)**

Sustituye el cuerpo de `renderPila[0]` (actualmente líneas 1337-1373) por:

```java
        renderPila[0] = () -> {
            boxRojo.getChildren().clear();
            boxVerde.getChildren().clear();
            java.util.Comparator<EntradaAsignacion> porSeqDesc = (a, b) -> Long.compare(b.seq, a.seq);
            List<EntradaAsignacion> activa = pilaActiva.get();
            List<EntradaAsignacion> rojos  = activa.stream().filter(x -> !x.asignada).sorted(porSeqDesc).collect(java.util.stream.Collectors.toList());
            List<EntradaAsignacion> verdes = activa.stream().filter(x ->  x.asignada).sorted(porSeqDesc).collect(java.util.stream.Collectors.toList());
            int nRojo = rojos.size(), nVerde = verdes.size();
            for (EntradaAsignacion e : rojos) {
                Runnable onClick = () -> cargarEntrada[0].accept(e);
                Runnable onRemove = () -> {
                    activa.remove(e);
                    if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
                    renderPila[0].run();
                };
                boxRojo.getChildren().add(crearFilaPila(e, e == actual[0], onClick, onRemove));
            }
            for (EntradaAsignacion e : verdes) {
                Runnable onClick = () -> cargarEntrada[0].accept(e);
                Runnable onRemove = () -> {
                    activa.remove(e);
                    if (actual[0] == e) { actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—"); }
                    renderPila[0].run();
                };
                boxVerde.getChildren().add(crearFilaPila(e, e == actual[0], onClick, onRemove));
            }
            lblRojo.setText("Pendiente de asignar (" + nRojo + ")");
            lblVerde.setText("Asignados (" + nVerde + ") · sin guardar");
            scrollRojo.setPrefHeight(nRojo == 0 ? 34 : Math.min(nRojo, 5) * 39 + 4);
            scrollVerde.setPrefHeight(nVerde == 0 ? 34 : Math.min(nVerde, 5) * 39 + 4);
            // Totales globales (rep + glass) para la barra inferior y el botón Guardar.
            int nRojoGlobal  = (int) (pilaRep.stream().filter(x -> !x.asignada).count()
                                    + pilaGlass.stream().filter(x -> !x.asignada).count());
            int nVerdeGlobal = (int) (pilaRep.stream().filter(x -> x.asignada).count()
                                    + pilaGlass.stream().filter(x -> x.asignada).count());
            int sinModelo    = (int) (pilaRep.stream().filter(e -> !e.asignada && !e.tieneModelo()).count()
                                    + pilaGlass.stream().filter(e -> !e.asignada && !e.tieneModelo()).count());
            int nPul = lotePulido.size();
            lblProg.setText(nVerdeGlobal + " configurados · " + nRojoGlobal + " pendientes"
                    + (nPul > 0 ? " · " + nPul + " pulido" : "")
                    + (sinModelo > 0 ? " · " + sinModelo + " sin modelo" : ""));
            btnGuardar.setText("Guardar (" + (nVerdeGlobal + nPul) + ")");
            btnGuardar.setDisable(nRojoGlobal != 0 || (nVerdeGlobal + nPul) == 0);
        };
```

- [ ] **Step 4: Actualizar `cargarSiguienteRojo` a la cola activa**

Sustituye (actualmente línea 1468):

```java
            EntradaAsignacion sig = pila.stream().filter(x -> !x.asignada)
```

por:

```java
            EntradaAsignacion sig = pilaActiva.get().stream().filter(x -> !x.asignada)
```

- [ ] **Step 5: Actualizar el escaneo simple (`intentarAnadir`) — dedup por imei + add a cola activa**

Sustituye el bloque de dedup y alta (actualmente líneas 1545-1551):

```java
            if (pila.stream().anyMatch(x -> x.imei.equals(imei) && x.tipo == tipoActual[0])) {
                lblScanErr.setText("Ese IMEI ya está en la pila (" + tipoActual[0].etiqueta() + ")."); return; }
            lblScanErr.setText("");
            EntradaAsignacion e = new EntradaAsignacion(imei);
            e.tipo = tipoActual[0];
            e.seq = ++seqCounter[0];
            pila.add(e);
```

por:

```java
            if (pilaActiva.get().stream().anyMatch(x -> x.imei.equals(imei))) {
                lblScanErr.setText("Ese IMEI ya está en la cola (" + tipoActual[0].etiqueta() + ")."); return; }
            lblScanErr.setText("");
            EntradaAsignacion e = new EntradaAsignacion(imei);
            e.tipo = tipoActual[0];
            e.seq = ++seqCounter[0];
            pilaActiva.get().add(e);
```

- [ ] **Step 6: Actualizar el pegado múltiple — dedup por imei + add a cola activa**

Sustituye (actualmente líneas 1577-1581):

```java
                    if (pila.stream().anyMatch(x -> x.imei.equals(imei) && x.tipo == tipoActual[0])) { duplicados++; continue; }
                    EntradaAsignacion en = new EntradaAsignacion(imei);
                    en.tipo = tipoActual[0];
                    en.seq = ++seqCounter[0];
                    pila.add(en);
```

por:

```java
                    if (pilaActiva.get().stream().anyMatch(x -> x.imei.equals(imei))) { duplicados++; continue; }
                    EntradaAsignacion en = new EntradaAsignacion(imei);
                    en.tipo = tipoActual[0];
                    en.seq = ++seqCounter[0];
                    pilaActiva.get().add(en);
```

- [ ] **Step 7: Limpiar el detalle al conmutar de categoría en el selector**

En el listener de `tgTipo` (actualmente líneas 1624-1631), sustituye:

```java
        tgTipo.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o == null ? tbRep : (ToggleButton) o).setSelected(true); return; }  // no permitir deselección
            boolean pulido = (n == tbPulido);
            richArea.setVisible(!pulido);  richArea.setManaged(!pulido);
            pulidoPane.setVisible(pulido); pulidoPane.setManaged(pulido);
            if (!pulido) tipoActual[0] = (n == tbGlass) ? TipoTrabajo.GLASS : TipoTrabajo.REPARACION;
            if (renderPila[0] != null) renderPila[0].run();
        });
```

por:

```java
        tgTipo.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { (o == null ? tbRep : (ToggleButton) o).setSelected(true); return; }  // no permitir deselección
            boolean pulido = (n == tbPulido);
            richArea.setVisible(!pulido);  richArea.setManaged(!pulido);
            pulidoPane.setVisible(pulido); pulidoPane.setManaged(pulido);
            if (!pulido) tipoActual[0] = (n == tbGlass) ? TipoTrabajo.GLASS : TipoTrabajo.REPARACION;
            // El detalle cargado pertenece a la cola vieja: al cambiar de cola, se limpia.
            actual[0] = null; formBox.setDisable(true); lblImeiCurso.setText("—");
            if (renderPila[0] != null) renderPila[0].run();
        });
```

- [ ] **Step 8: Actualizar el guardado y el aviso de cierre**

Sustituye la cabecera del bucle de guardado (actualmente línea 1648):

```java
                for (EntradaAsignacion e : pila) {
```

por (recorre ambas colas):

```java
                List<EntradaAsignacion> todas = new ArrayList<>(pilaRep);
                todas.addAll(pilaGlass);
                for (EntradaAsignacion e : todas) {
```

Y sustituye el cálculo del total en `onCloseRequest` (actualmente línea 1684):

```java
            int total = pila.size() + lotePulido.size();
```

por:

```java
            int total = pilaRep.size() + pilaGlass.size() + lotePulido.size();
```

- [ ] **Step 9: Compilar**

Run: `mvn -o -q compile`
Expected: BUILD SUCCESS. **No debe quedar ninguna referencia a `pila`** (variable). Si el compilador reporta "cannot find symbol: variable pila", localiza y migra la referencia superviviente (todas están listadas en los Steps 3-8).

- [ ] **Step 10: Smoke manual**

Run: `mvn -o javafx:run`
Login como SuperTécnico → abrir "Asignar trabajos". Comprobar:
- En **Reparación**: escanear 2 IMEIs → aparecen en "Pendiente de asignar"; el detalle **no** tiene el toggle `Reparación | Glass`.
- Cambiar a **Glass**: la lista muestra 0 (no arrastra los de rep); el detalle se limpia (IMEI "—", formulario deshabilitado). Escanear 1 IMEI glass → aparece solo aquí.
- Volver a **Reparación**: siguen los 2 de rep, no aparece el de glass.
- El **mismo IMEI** puede añadirse en Rep y en Glass sin choque de duplicado.
- La barra inferior (`lblProg`) suma **ambas** colas + pulido; "Guardar (N)" refleja el total global.
- Con algún rojo en cualquier cola, **Guardar sigue deshabilitado**; al asignar todo (rep, glass) y quedar sin rojos, se habilita.
- Guardar → se crean las asignaciones (rep = categoría R, glass = G) y la tabla se recarga. Revisar que no hay traza de error en consola.

Si algún punto falla, es un bug de esta tarea → depurar antes de commitear (ver superpowers:systematic-debugging).

- [ ] **Step 11: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(asignaciones): colas rep/glass independientes en el modal (dos listas, cola activa)"
```

---

## Notas de cierre

- Al terminar ambas tareas, actualizar la memoria [[project_backlog_mejoras_asignaciones]] (marcar Fase 1 hecha, pendiente merge/push con OK) y decidir con el usuario la integración (rama `feature/…`, merge `--no-ff`) — ver superpowers:finishing-a-development-branch y [[feedback_merge_confirmacion]].
- Fases 2 (pulido al esqueleto cola+detalle) y 3 (paridad de detalle) quedan en el doc de descomposición del cluster D; no entran aquí.
