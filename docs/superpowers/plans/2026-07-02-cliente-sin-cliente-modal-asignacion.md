# "Sin cliente" en el modal de asignación + sync por IMEI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir dejar un IMEI realmente "sin cliente" desde el modal "Asignar trabajos" (rep/glass y pulido), sin borrados accidentales al teclear, y con el cliente sincronizado entre colas del modal.

**Architecture:** El upsert de `Telefono` gana un flag `clienteExplicito` que, cuando es true, fija `ID_CLI` exacto (incluido NULL) en vez de `COALESCE`. En cliente se añade un tri-estado (`sinCliente`) por entrada; el detalle rep/glass gana una opción "— Sin cliente —" con restauración al perder foco; el panel de pulido marca el flag; y una propagación por IMEI mantiene coherentes las colas del modal.

**Tech Stack:** Java 17, Spring Boot + JDBC/MariaDB (servidor), JavaFX (cliente), Maven. Sin tests de UI ni de contexto Spring → verificación por compilación + smoke manual.

## Global Constraints

- **Dos repos.** Cliente = subdirectorio del monorepo (`git` raíz en `C:/Users/info/Documents/ProgramaReparaciones`). Servidor = **gitlink** con repo propio en `gestion-reparaciones-servidor` (rama `main`). Los commits del servidor se hacen DENTRO de `gestion-reparaciones-servidor`; al integrar se bumpea el SHA del gitlink en el monorepo (`git add gestion-reparaciones-servidor`). **No** hacer push ni bump sin OK del usuario.
- **Retrocompatibilidad del servidor:** el campo `clienteExplicito` es opcional (default false). Clientes/llamadas que no lo envíen mantienen la semántica `COALESCE` actual. El servidor **no** cambia wiring de beans; validar arranque/contexto igualmente ([[feedback_server_spring_startup]]).
- **Fuera de alcance:** "Editar cliente" de la tabla, el Agrupado y el menú de Pulido (ya permiten quitar cliente vía `actualizarCliente`); no se tocan. No se cambia `actualizarCliente`.
- **Solo SUPERTECNICO** puede cambiar cliente (asignar o quitar): la autorización del endpoint cubre ambos.
- Ejecutar Maven por **Bash** (`mvn -o …`). No añadir `Co-Authored-By` en commits.
- Sentinel de "sin cliente": id `-1`, texto exacto **`— Sin cliente —`** (idéntico al de `SelectorClienteDialog`).
- Tri-estado de cliente por entrada: `cliente!=null` (real) · `cliente==null && sinCliente` (sin cliente explícito) · `cliente==null && !sinCliente` (no tocado → preservar). Invariante: `cliente!=null ⇒ sinCliente=false`.
- Ficheros cliente: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/{controllers/PendientesSuperTecnicoController.java, dao/TelefonoDAO.java}`.
- **Depende de Fase 1** (rama `feature/cluster-d-fase1-colas`): usa `pilaRep`/`pilaGlass` y el detalle sin `tgEnt`. Ejecutar sobre una rama basada en esa.
- **Nota de smoke end-to-end:** el cliente apunta al servidor de preproducción (`api.url=https://api.fonestore.es`). Verificar que el NULL se persiste **de verdad** requiere el cambio de servidor (Task 1) **desplegado** en preproducción (acción del usuario). El comportamiento de UI (opción visible, restaurar-al-blur, sync entre colas) se puede smoke-ear antes del deploy.

---

### Task 1: Servidor — flag `clienteExplicito` en el upsert de Telefono

Permite fijar `ID_CLI` exacto (incluido NULL) cuando el cliente se cambió explícitamente, sin pisar los "no tocados". Server-only, aditivo, retrocompatible.

**Files (repo `gestion-reparaciones-servidor`):**
- Modify: `src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`

**Interfaces:**
- Produces: `TelefonoDAO.insertar(String imei, String modelo, Integer idCli, boolean clienteExplicito)`; `ImeiRequest` con campo extra `Boolean clienteExplicito`; endpoint `POST /api/telefonos` acepta `clienteExplicito` (opcional). El cliente (Task 2) enviará ese campo.

- [ ] **Step 1: Añadir el overload con flag en `TelefonoDAO`**

En `TelefonoDAO.java`, sustituye el método `insertar(String imei, String modelo, Integer idCli)` (actualmente):

```java
    public void insertar(String imei, String modelo, Integer idCli) {
        String m = (modelo == null || modelo.isBlank()) ? null : modelo;
        jdbc.update(
                "INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = COALESCE(?, ID_CLI)",
                imei, m, idCli, m, idCli);
    }
```

por:

```java
    public void insertar(String imei, String modelo, Integer idCli) {
        insertar(imei, modelo, idCli, false);
    }

    /**
     * Upsert de teléfono. Si {@code clienteExplicito} es true, fija ID_CLI al valor
     * dado (incluido NULL → deja el IMEI sin cliente); si es false, usa COALESCE
     * (un idCli null preserva el cliente actual). MODELO siempre con COALESCE.
     */
    public void insertar(String imei, String modelo, Integer idCli, boolean clienteExplicito) {
        String m = (modelo == null || modelo.isBlank()) ? null : modelo;
        String sql = clienteExplicito
                ? "INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = ?"
                : "INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = COALESCE(?, ID_CLI)";
        jdbc.update(sql, imei, m, idCli, m, idCli);
    }
```

- [ ] **Step 2: Añadir el campo `clienteExplicito` al record `ImeiRequest`**

En `TelefonoController.java` sustituye (actualmente línea ~115):

```java
    private record ImeiRequest(String imei, String modelo, Integer idCli) {}
```

por:

```java
    private record ImeiRequest(String imei, String modelo, Integer idCli, Boolean clienteExplicito) {}
```

- [ ] **Step 3: Usar el flag en el endpoint `insertar` (autorización + log de quitar)**

En `TelefonoController.java` sustituye el método `insertar` (actualmente):

```java
    public void insertar(@RequestBody ImeiRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (req.idCli() != null && !"SUPERTECNICO".equals(principal.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERTECNICO puede asignar cliente");
        }
        dao.insertar(req.imei(), req.modelo(), req.idCli());
        if (req.idCli() != null) {
            logDao.insertar(principal.getIdUsu(), "ASIGNAR_CLIENTE",
                    "IMEI: " + req.imei() + ", ID_CLI: " + req.idCli());
        }
    }
```

por:

```java
    public void insertar(@RequestBody ImeiRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean explicito = Boolean.TRUE.equals(req.clienteExplicito());
        boolean cambiaCliente = req.idCli() != null || explicito;   // asignar o quitar
        if (cambiaCliente && !"SUPERTECNICO".equals(principal.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERTECNICO puede cambiar cliente");
        }
        dao.insertar(req.imei(), req.modelo(), req.idCli(), explicito);
        if (req.idCli() != null) {
            logDao.insertar(principal.getIdUsu(), "ASIGNAR_CLIENTE",
                    "IMEI: " + req.imei() + ", ID_CLI: " + req.idCli());
        } else if (explicito) {
            logDao.insertar(principal.getIdUsu(), "QUITAR_CLIENTE", "IMEI: " + req.imei());
        }
    }
```

- [ ] **Step 4: Compilar el servidor**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor`: `mvn -o -q compile`
Expected: BUILD SUCCESS. (No hay tests de contexto Spring; el cambio no añade/rewirea beans — solo un campo de record, un overload de DAO y el cuerpo del endpoint —, así que el contexto no se altera.)

- [ ] **Step 5: Commit (en el repo del servidor)**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor commit -m "feat(telefono): flag clienteExplicito en upsert para poder dejar sin cliente"
```

No hacer push ni bumpear el gitlink en el monorepo todavía (se hace al integrar, con OK del usuario).

---

### Task 2: Cliente — persistencia con flag + "sin cliente" de pulido end-to-end

Añade el overload de DAO en cliente, el campo `sinCliente` a los dos modelos, usa el flag en el guardado y hace que el panel de pulido marque "sin cliente" de verdad. Al terminar, **el "sin cliente" de pulido funciona end-to-end** (rep/glass aún no puede fijarlo, así que su `sinCliente` es siempre false → sin cambio de comportamiento en rep/glass todavía).

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: endpoint `POST /api/telefonos` con `clienteExplicito` (Task 1).
- Produces: `TelefonoDAO.insertar(imei, modelo, idCli, boolean clienteExplicito)` (cliente); campos `boolean sinCliente` en `EntradaAsignacion` y `FilaPulido`; guardado que pasa el flag. Task 3 y 4 los consumen.

- [ ] **Step 1: Overload de `insertar` con flag en el `TelefonoDAO` de cliente**

En `dao/TelefonoDAO.java` sustituye el método `insertar(String imei, String modelo, Integer idCli)` (actualmente):

```java
    public void insertar(String imei, String modelo, Integer idCli) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("imei", imei);
        body.put("modelo", modelo != null ? modelo : "");
        body.put("idCli", idCli); // puede ser null
        ApiClient.post("/api/telefonos", body);
    }
```

por:

```java
    public void insertar(String imei, String modelo, Integer idCli) throws SQLException {
        insertar(imei, modelo, idCli, false);
    }

    /**
     * Alta/actualización de teléfono. Si {@code clienteExplicito} es true, el servidor
     * fija ID_CLI al valor dado (incluido null → sin cliente); si es false, un idCli
     * null preserva el cliente actual (COALESCE).
     */
    public void insertar(String imei, String modelo, Integer idCli, boolean clienteExplicito) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("imei", imei);
        body.put("modelo", modelo != null ? modelo : "");
        body.put("idCli", idCli); // puede ser null
        body.put("clienteExplicito", clienteExplicito);
        ApiClient.post("/api/telefonos", body);
    }
```

- [ ] **Step 2: Añadir `sinCliente` a `EntradaAsignacion`**

En `PendientesSuperTecnicoController.java`, en la clase `EntradaAsignacion`, tras el campo `cliente` (actualmente línea 109 `Cliente cliente; ...`), añade:

```java
        boolean sinCliente;                      // true = el usuario eligió explícitamente "— Sin cliente —"
```

- [ ] **Step 3: Añadir `sinCliente` a `FilaPulido`**

En la clase `FilaPulido`, tras el campo `cliente` (actualmente línea 126 `com.reparaciones.models.Cliente cliente; ...`), añade:

```java
        boolean sinCliente;                        // true = "— Sin cliente —" explícito
```

- [ ] **Step 4: Guardado rep/glass — pasar el flag**

En `abrirFormularioAsignacion`, en el bucle de guardado de rep/glass, sustituye (actualmente líneas 1638-1639):

```java
                    Integer idCli = e.cliente != null ? e.cliente.getIdCli() : null;
                    telefonoDAO.insertar(e.imei, e.modeloCode, idCli);
```

por:

```java
                    Integer idCli = e.cliente != null ? e.cliente.getIdCli() : null;
                    telefonoDAO.insertar(e.imei, e.modeloCode, idCli, e.sinCliente);
```

- [ ] **Step 5: Guardado pulido — pasar el flag**

Sustituye (actualmente línea 1659):

```java
                    telefonoDAO.insertar(f.imei, null, f.cliente != null ? f.cliente.getIdCli() : null);
```

por:

```java
                    telefonoDAO.insertar(f.imei, null, f.cliente != null ? f.cliente.getIdCli() : null, f.sinCliente);
```

- [ ] **Step 6: Panel de pulido — marcar `sinCliente` y reflejarlo en el botón**

En `construirPulidoPane`, en el handler del botón de cliente, sustituye (actualmente líneas 909-912):

```java
                Integer idCli = sel.get() == -1 ? null : sel.get();
                fila.cliente = idCli == null ? null
                        : clientesActivos.stream().filter(c -> c.getIdCli() == idCli).findFirst().orElse(null);
                btnCli.setText(fila.cliente != null ? fila.cliente.getNombre() : "Cliente");
```

por:

```java
                Integer idCli = sel.get() == -1 ? null : sel.get();
                fila.sinCliente = (sel.get() == -1);   // -1 → sin cliente explícito
                fila.cliente = idCli == null ? null
                        : clientesActivos.stream().filter(c -> c.getIdCli() == idCli).findFirst().orElse(null);
                btnCli.setText(fila.sinCliente ? "— Sin cliente —"
                        : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente"));
```

- [ ] **Step 7: Precarga pulido — no pisar un "sin cliente" explícito**

En `construirPulidoPane`, en la precarga en segundo plano, sustituye (actualmente línea 940):

```java
                    if (idCliRes != null && fila.cliente == null) {
```

por:

```java
                    if (idCliRes != null && fila.cliente == null && !fila.sinCliente) {
```

- [ ] **Step 8: Precarga rep/glass — no pisar un "sin cliente" explícito**

En `abrirFormularioAsignacion` (`lanzarLookup`), sustituye (actualmente línea 1394):

```java
                    if (idCliRes != null && e.cliente == null) {
```

por:

```java
                    if (idCliRes != null && e.cliente == null && !e.sinCliente) {
```

- [ ] **Step 9: Compilar**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente`: `mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(asignaciones): persistir sin cliente (flag) y activarlo en el panel de pulido"
```

---

### Task 3: Cliente — opción "— Sin cliente —" y restaurar-al-blur en el detalle rep/glass

Da al detalle rep/glass la opción de dejar sin cliente y arregla el borrado accidental: al perder foco con texto no resuelto, vuelve al estado comprometido. Al terminar, **rep/glass "sin cliente" + restaurar-al-blur funciona**.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `EntradaAsignacion.sinCliente` (Task 2). El sentinel es un `Cliente(-1, "— Sin cliente —", true, null)`.
- Produces: holder `boolean[] sinClienteSel`; `confirmarCliente` que reconoce el sentinel; `cargarEntrada`/`asignarActual` que leen/escriben `e.sinCliente`. Task 4 los usa.

- [ ] **Step 1: Añadir el sentinel "— Sin cliente —" al desplegable**

En `abrirFormularioAsignacion`, tras construir `todosClientes` (actualmente líneas 1117-1118):

```java
        javafx.collections.ObservableList<Cliente> todosClientes =
                FXCollections.observableArrayList(clientesModal);
```

añade justo debajo:

```java
        // Opción "sin cliente" (id -1) arriba del desplegable, como en SelectorClienteDialog.
        final Cliente SIN_CLIENTE = new Cliente(-1, "— Sin cliente —", true, null);
        todosClientes.add(0, SIN_CLIENTE);
```

- [ ] **Step 2: Añadir el holder `sinClienteSel`**

Tras `boolean[] actualizandoCliente = { false };` (actualmente línea 1159) añade:

```java
        boolean[] sinClienteSel = { false };   // estado comprometido: "sin cliente" explícito
```

- [ ] **Step 3: `confirmarCliente` reconoce el sentinel (tri-estado)**

Sustituye `confirmarCliente` (actualmente líneas 1169-1176):

```java
        java.util.function.Consumer<Cliente> confirmarCliente = cli -> {
            clienteSel[0] = cli;
            actualizandoCliente[0] = true;
            tfCliente.setText(cli.getNombre());
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
            popupCliente.hide();
        };
```

por:

```java
        java.util.function.Consumer<Cliente> confirmarCliente = cli -> {
            boolean sin = cli.getIdCli() == -1;
            clienteSel[0] = sin ? null : cli;
            sinClienteSel[0] = sin;
            actualizandoCliente[0] = true;
            tfCliente.setText(cli.getNombre());   // el nombre del sentinel ya es "— Sin cliente —"
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
            popupCliente.hide();
        };
```

- [ ] **Step 4: El listener de texto ya no borra el estado comprometido**

Sustituye el listener de `tfCliente.textProperty()` (actualmente líneas 1178-1186):

```java
        tfCliente.textProperty().addListener((obs, oldText, newText) -> {
            if (actualizandoCliente[0]) return;
            if (clienteSel[0] != null && clienteSel[0].getNombre().equals(newText)) return;
            clienteSel[0] = null;
            String lower = newText == null ? "" : newText.trim().toLowerCase();
            clientesFiltrados.setPredicate(c -> lower.isEmpty()
                    || c.getNombre().toLowerCase().contains(lower));
            mostrarPopupCliente.run();
        });
```

por (solo filtra; el estado comprometido cambia únicamente al confirmar o restaurar):

```java
        tfCliente.textProperty().addListener((obs, oldText, newText) -> {
            if (actualizandoCliente[0]) return;
            String lower = newText == null ? "" : newText.trim().toLowerCase();
            clientesFiltrados.setPredicate(c -> lower.isEmpty()
                    || c.getNombre().toLowerCase().contains(lower));
            mostrarPopupCliente.run();
        });
```

- [ ] **Step 5: Restaurar al perder foco (texto no resuelto → estado comprometido)**

Sustituye el listener de `tfCliente.focusedProperty()` (actualmente líneas 1190-1210):

```java
        tfCliente.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) javafx.application.Platform.runLater(() -> {
                popupCliente.hide();
                String texto = tfCliente.getText() == null ? "" : tfCliente.getText().trim();
                if (clienteSel[0] != null && clienteSel[0].getNombre().equals(texto)) {
                    clientesFiltrados.setPredicate(c -> true);
                    return;
                }
                Cliente exacto = todosClientes.stream()
                        .filter(c -> c.getNombre().equalsIgnoreCase(texto))
                        .findFirst().orElse(null);
                if (exacto != null) {
                    confirmarCliente.accept(exacto);
                } else {
                    actualizandoCliente[0] = true;
                    tfCliente.setText(clienteSel[0] != null ? clienteSel[0].getNombre() : "");
                    clientesFiltrados.setPredicate(c -> true);
                    actualizandoCliente[0] = false;
                }
            });
        });
```

por:

```java
        tfCliente.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) javafx.application.Platform.runLater(() -> {
                popupCliente.hide();
                String texto = tfCliente.getText() == null ? "" : tfCliente.getText().trim();
                // Coincidencia exacta (cliente real o el sentinel "— Sin cliente —") → confirmar.
                Cliente exacto = todosClientes.stream()
                        .filter(c -> c.getNombre().equalsIgnoreCase(texto))
                        .findFirst().orElse(null);
                if (exacto != null) { confirmarCliente.accept(exacto); return; }
                // Texto a medias / borrado / no coincide → restaurar el estado comprometido.
                actualizandoCliente[0] = true;
                tfCliente.setText(clienteSel[0] != null ? clienteSel[0].getNombre()
                        : (sinClienteSel[0] ? "— Sin cliente —" : ""));
                clientesFiltrados.setPredicate(c -> true);
                actualizandoCliente[0] = false;
            });
        });
```

- [ ] **Step 6: `cargarEntrada` pinta el tri-estado**

En `cargarEntrada`, sustituye (actualmente líneas 1447-1451, el bloque de cliente):

```java
            clienteSel[0] = e.cliente;
            actualizandoCliente[0] = true;
            tfCliente.setText(e.cliente != null ? e.cliente.getNombre() : "");
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
```

por:

```java
            clienteSel[0] = e.cliente;
            sinClienteSel[0] = e.sinCliente;
            actualizandoCliente[0] = true;
            tfCliente.setText(e.cliente != null ? e.cliente.getNombre()
                    : (e.sinCliente ? "— Sin cliente —" : ""));
            clientesFiltrados.setPredicate(c -> true);
            actualizandoCliente[0] = false;
```

- [ ] **Step 7: `asignarActual` guarda el tri-estado en la entrada**

En `asignarActual`, sustituye (actualmente línea 1476):

```java
            e.cliente = clienteSel[0];
```

por:

```java
            e.cliente = clienteSel[0];
            e.sinCliente = sinClienteSel[0];
```

- [ ] **Step 8: Compilar**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente`: `mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(asignaciones): opcion sin cliente y restaurar-al-blur en el detalle rep/glass"
```

---

### Task 4: Cliente — sincronización del cliente por IMEI entre colas del modal

Al confirmar cliente/sin-cliente de un IMEI, propaga el mismo estado a todas las entradas/filas de ese IMEI en `pilaRep`, `pilaGlass` y `lotePulido`, refrescando la vista.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `EntradaAsignacion.sinCliente`/`cliente` (Task 2/3), `FilaPulido.sinCliente`/`cliente` (Task 2), `pilaRep`/`pilaGlass`/`lotePulido` (Fase 1), `asignarActual`, `renderPila`.
- Produces: `propagarCliente(String, Cliente, boolean)`; `construirPulidoPane` con 2 params nuevos (callback + lista de refrescadores).

- [ ] **Step 1: Cambiar la firma de `construirPulidoPane` para exponer cambios de cliente y refrescos**

Sustituye la firma (actualmente línea 830):

```java
    private VBox construirPulidoPane(List<FilaPulido> lote, List<Tecnico> tecnicosModal, Runnable onChange) {
```

por:

```java
    private VBox construirPulidoPane(List<FilaPulido> lote, List<Tecnico> tecnicosModal, Runnable onChange,
                                     java.util.function.Consumer<FilaPulido> onClienteCambiado,
                                     List<Runnable> refrescadoresCliente) {
```

- [ ] **Step 2: En el panel de pulido, registrar el refresco del botón y notificar el cambio**

En `construirPulidoPane`, en el handler del botón de cliente (tras el `btnCli.setText(...)` que dejó el Task 2 Step 6), añade al final del handler, antes de cerrar el `setOnAction`:

```java
                onClienteCambiado.accept(fila);
```

Y tras crear `btnCli` (después de su `setStyle`, antes de construir la fila), registra un refrescador que reaplique el texto desde el modelo:

```java
            refrescadoresCliente.add(() -> btnCli.setText(fila.sinCliente ? "— Sin cliente —"
                    : (fila.cliente != null ? fila.cliente.getNombre() : "Cliente")));
```

- [ ] **Step 3: Declarar la lista de refrescadores y `propagarCliente` ANTES de `asignarActual`**

`asignarActual` (Task 4 Step 5) referenciará `propagarCliente`, así que ambos deben declararse antes de su definición. En `abrirFormularioAsignacion`, justo antes de `Runnable asignarActual = () -> {` (actualmente línea 1474), añade:

```java
        List<Runnable> refrescadoresClientePulido = new ArrayList<>();
        // Propaga el cliente (o "sin cliente") a todas las entradas/filas del mismo IMEI en las 3 colas.
        TriConsumerCliente propagarCliente = (imei, cliente, sin) -> {
            for (EntradaAsignacion x : pilaRep)   if (x.imei.equals(imei)) { x.cliente = cliente; x.sinCliente = sin; }
            for (EntradaAsignacion x : pilaGlass) if (x.imei.equals(imei)) { x.cliente = cliente; x.sinCliente = sin; }
            for (FilaPulido f : lotePulido)       if (f.imei.equals(imei))  { f.cliente = cliente; f.sinCliente = sin; }
            refrescadoresClientePulido.forEach(Runnable::run);
            renderPila[0].run();
        };
```

Todo lo que referencia (`pilaRep`/`pilaGlass`/`lotePulido` :988-994, `renderPila` :1305) está declarado antes de la línea 1474. `refrescadoresClientePulido` se pasa vacía y la llena `construirPulidoPane` más abajo (Step 4); `propagarCliente` la recorre en tiempo de invocación, cuando ya está poblada.

Y añade una interfaz funcional privada a la clase `PendientesSuperTecnicoController` (junto a las otras clases internas, p.ej. tras `FilaPulido`):

```java
    /** Propaga cliente/sin-cliente de un IMEI a las colas del modal. */
    @FunctionalInterface
    private interface TriConsumerCliente {
        void accept(String imei, com.reparaciones.models.Cliente cliente, boolean sinCliente);
    }
```

- [ ] **Step 4: Pasar los nuevos argumentos a `construirPulidoPane`**

Sustituye la llamada (actualmente líneas 1599-1600):

```java
        VBox pulidoPane = construirPulidoPane(lotePulido, tecnicosModal,
                () -> { if (renderPila[0] != null) renderPila[0].run(); });
```

por:

```java
        VBox pulidoPane = construirPulidoPane(lotePulido, tecnicosModal,
                () -> { if (renderPila[0] != null) renderPila[0].run(); },
                fila -> propagarCliente.accept(fila.imei, fila.cliente, fila.sinCliente),
                refrescadoresClientePulido);
```

- [ ] **Step 5: Propagar al asignar en rep/glass**

En `asignarActual`, tras `e.sinCliente = sinClienteSel[0];` (Task 3 Step 7), añade:

```java
            propagarCliente.accept(e.imei, e.cliente, e.sinCliente);
```

(`propagarCliente` ya está declarada antes de `asignarActual` por el Step 3, así que es visible aquí.)

- [ ] **Step 6: Compilar**

Run desde `/c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente`: `mvn -o -q compile`
Expected: BUILD SUCCESS. Si hay error de orden de declaración (variable usada antes de declararse), reordena para que `refrescadoresClientePulido` y `propagarCliente` se declaren antes de `asignarActual` y de la construcción de `pulidoPane`.

- [ ] **Step 7: Smoke manual (UI; el clear real en BD requiere Task 1 desplegado)**

Run: `mvn -o javafx:run`. Login SuperTécnico → "Asignar trabajos". Verificar:
- **rep/glass:** el desplegable de cliente muestra "— Sin cliente —" arriba; elegirla deja el campo en "— Sin cliente —".
- **Restaurar-al-blur:** escribe texto a medias que no coincide y haz clic fuera → el campo vuelve al cliente/estado que tenías (no queda vacío).
- **pulido:** el botón de cliente permite "— Sin cliente —" y queda reflejado.
- **Sync por IMEI:** añade el mismo IMEI en Rep y en Glass; asigna cliente A en Rep; ve a Glass y carga ese IMEI → aparece A. Ponlo "sin cliente" en una cola → se refleja en la otra (y en pulido si el IMEI también está allí).
- **Guardar** un lote con una entrada "sin cliente" no lanza error en consola.
- **Verificación de persistencia real (tras desplegar Task 1 en preproducción):** guardar "— Sin cliente —" para un IMEI que tenía cliente → al reabrir, el IMEI queda sin cliente. Y una entrada "no tocada" con cliente previo lo conserva.

Si algo falla, depurar antes de commitear (superpowers:systematic-debugging).

- [ ] **Step 8: Commit**

```bash
git -C /c/Users/info/Documents/ProgramaReparaciones add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C /c/Users/info/Documents/ProgramaReparaciones commit -m "feat(asignaciones): sincronizar cliente por IMEI entre colas del modal"
```

---

## Notas de cierre

- Integración: rama cliente `feature/…` (sobre Fase 1) con merge `--no-ff` y **bump del gitlink** del servidor al SHA nuevo, todo con OK del usuario ([[feedback_merge_confirmacion]]). El servidor necesita **desplegarse en preproducción** para que el clear surta efecto end-to-end ([[project_preproduccion]]).
- Al terminar, actualizar [[project_backlog_mejoras_asignaciones]].
