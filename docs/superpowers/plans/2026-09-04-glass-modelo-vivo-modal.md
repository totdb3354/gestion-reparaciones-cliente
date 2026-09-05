# Modelo vivo en el modal de asignación (glass, bloque 1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el modal de asignación del SuperTécnico, el modelo pertenece al IMEI: elegirlo o cambiarlo a mano en cualquier cola lo guarda en BD al instante y lo copia a todas las entradas de ese IMEI en Reparación y Glass; un IMEI ya conocido por el modal nace con modelo al escanearlo, sin volver a buscar.

**Architecture:** Se calca el mecanismo del "cliente pegajoso" del modal, solo en su parte por-IMEI: un mapa `modeloPorImei` (memoria del modal), un `decidirModelo` que envuelve al punto único de confirmación (`confirmarModelo`) en las cuatro vías manuales, un helper puro `propagarModelo` (testeado) que copia el modelo a las dos pilas, una siembra al escanear y un guardado en segundo plano con el upsert de teléfono de dos argumentos (no toca el cliente). El lookup automático no persiste ni pisa decisiones manuales. Servidor sin cambios.

**Tech Stack:** Cliente JavaFX 21 / Java 17 / JUnit 5.10 / Maven 3.9.16 (toolchain portable). Servidor Spring Boot ya desplegado (`main` `b1b1816`), **no se toca**.

Spec: `docs/superpowers/specs/2026-09-04-glass-modelo-vivo-modal-design.md`.

## Global Constraints

- **Rama**: cliente `feature/glass-modelo-vivo` desde `hotfix/0.16.2` (tip con spec + plan) en el repo raíz `C:\Users\dev\Documents\ProgramaReparaciones`. **No tocar** `main` del raíz, ni el submódulo `gestion-reparaciones-servidor` (ni su rama, ni su código: el servidor no cambia en este bloque).
- **Nunca `git add -A` / `git commit -a`** en el repo raíz: el gitlink aparece como `M gestion-reparaciones-servidor` y NO debe commitearse en tareas de feature (solo en el commit de release, fuera de este plan). Añadir ficheros **por nombre**.
- **Commits sin `Co-Authored-By`**. Mensajes en español con prefijo `feat(cliente):`, `test(cliente):`, `docs:`.
- **Maven en Bash** necesita el prefijo del toolchain portable en cada llamada:
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.
  Suite completa del cliente: `mvn -q -f gestion-reparaciones-cliente/pom.xml test` (silencio = BUILD SUCCESS; base **188 tests** verdes en `77ffbb9`). Una clase: añadir `-Dtest=PendientesSuperTecnicoControllerTest`.
- **Un solo fichero de producción**: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (2.600 líneas; el modal es `abrirFormularioAsignacion`, ~`:1600-2450`). Tests en `gestion-reparaciones-cliente/src/test/java/com/reparaciones/controllers/PendientesSuperTecnicoControllerTest.java`. Los números de línea de este plan son del tip `62af81a` y **se desplazan con cada tarea**: localizar siempre por el texto exacto (grep) antes de editar.
- **Guardado temprano SOLO con `telefonoDAO.insertar(imei, code)` de DOS argumentos** (`TelefonoDAO.java:48`: `POST /api/telefonos` con `imei` y `modelo`; el servidor hace `COALESCE` del cliente y no escribe log). Nunca la versión de 3 o 4 argumentos (tocaría el cliente del IMEI).
- **El lookup automático NO persiste** (solo Guardar lo hace, como hoy). **No hay modelo pegajoso** entre IMEIs distintos (no copiar `clienteDefaultModal`). **Pulido no participa.**
- **Guardar (`btnGuardar`), `asignarActual`, `defTecnicos`, borrar entrada de la cola: sin cambios.**
- Texto exacto del CHANGELOG en la Task 4.

---

### Task 1: Rama + helper puro `propagarModelo` (TDD)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`EntradaAsignacion` `:117`; helper nuevo tras `tipoDe`, `:155-157`)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/controllers/PendientesSuperTecnicoControllerTest.java`

**Interfaces:**
- Produces: `static int propagarModelo(String imei, String code, List<EntradaAsignacion> pilaRep, List<EntradaAsignacion> pilaGlass)` (package-private, puro: copia `code` a `modeloCode` de todas las entradas de `imei` en ambas listas, rojas y verdes; devuelve cuántas tocó; `imei == null` → 0). La usa `decidirModelo` (Task 2).
- Produces: `EntradaAsignacion` pasa de `private static final class` a `static final class` (package-private) para que el test pueda construirla; sus campos ya son package-private.

- [ ] **Step 1: Crear la rama desde `hotfix/0.16.2`**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git branch --show-current          # esperado: hotfix/0.16.2
git status --short                 # esperado: solo " M gestion-reparaciones-servidor" y untracked (.codegraph/, .superpowers/, NOVEDADES-*.pdf)
git checkout -b feature/glass-modelo-vivo
```

- [ ] **Step 2: Escribir los tests (fallan: `EntradaAsignacion` es privada y `propagarModelo` no existe)**

Añadir al final de la clase `PendientesSuperTecnicoControllerTest` (antes de la llave de cierre final):

```java
    // ── propagarModelo (modelo vivo entre las colas Reparación/Glass del modal) ──────────────

    private static PendientesSuperTecnicoController.EntradaAsignacion entrada(String imei, String modelo, boolean asignada) {
        PendientesSuperTecnicoController.EntradaAsignacion e = new PendientesSuperTecnicoController.EntradaAsignacion(imei);
        e.modeloCode = modelo;
        e.asignada = asignada;
        return e;
    }

    @Test
    void propagar_modelo_copia_a_rojas_y_verdes_del_mismo_imei_en_las_dos_pilas() {
        var rojaRep   = entrada("111111111111111", null,    false);
        var verdeRep  = entrada("111111111111111", "viejo", true);
        var rojaGlass = entrada("111111111111111", null,    false);
        var otroImei  = entrada("222222222222222", "otro",  false);
        List<PendientesSuperTecnicoController.EntradaAsignacion> rep   = List.of(rojaRep, verdeRep, otroImei);
        List<PendientesSuperTecnicoController.EntradaAsignacion> glass = List.of(rojaGlass);

        int n = PendientesSuperTecnicoController.propagarModelo("111111111111111", "nuevo", rep, glass);

        assertEquals(3, n);
        assertEquals("nuevo", rojaRep.modeloCode);
        assertEquals("nuevo", verdeRep.modeloCode);
        assertEquals("nuevo", rojaGlass.modeloCode);
        assertEquals("otro", otroImei.modeloCode);   // otro IMEI: intacto
    }

    @Test
    void propagar_modelo_sin_entradas_del_imei_devuelve_0_y_no_toca_nada() {
        var otro = entrada("222222222222222", "otro", false);
        int n = PendientesSuperTecnicoController.propagarModelo("111111111111111", "nuevo", List.of(otro), List.of());
        assertEquals(0, n);
        assertEquals("otro", otro.modeloCode);
    }

    @Test
    void propagar_modelo_con_imei_null_no_hace_nada() {
        var e = entrada("111111111111111", null, false);
        assertEquals(0, PendientesSuperTecnicoController.propagarModelo(null, "nuevo", List.of(e), List.of()));
        assertNull(e.modeloCode);
    }
```

(`List` y `Assertions.*` ya están importados en ese test.)

- [ ] **Step 3: Ejecutar y ver que falla en compilación**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=PendientesSuperTecnicoControllerTest
```

Expected: `COMPILATION ERROR` … `EntradaAsignacion has private access` / `cannot find symbol: method propagarModelo`.

- [ ] **Step 4: Abrir `EntradaAsignacion` al paquete y escribir el helper**

En `PendientesSuperTecnicoController.java`, localizar (`:117`):

```java
    /** Una entrada del lote de asignación: un IMEI con su configuración local (aún no en BD). */
    private static final class EntradaAsignacion {
```

y dejarlo así (solo cambia la visibilidad):

```java
    /** Una entrada del lote de asignación: un IMEI con su configuración local (aún no en BD).
     *  Package-private (no private) para que el test del helper puro {@link #propagarModelo} pueda construirla. */
    static final class EntradaAsignacion {
```

Localizar (`:154-157`):

```java
    /** Deriva el tipo de trabajo del prefijo del {@code ID_REP}. Delega en {@link TipoTrabajo#desde}. */
    static TipoTrabajo tipoDe(String idRep) {
        return TipoTrabajo.desde(idRep);
    }
```

y añadir justo después:

```java
    /**
     * Modelo vivo del modal: copia {@code code} a TODAS las entradas de {@code imei} en las dos pilas
     * (rojas y verdes). Devuelve cuántas entradas ha tocado. Puro (sin UI) para poder testearlo; el modal
     * lo envuelve en {@code decidirModelo} y repinta la pila después. {@code imei == null} → 0.
     */
    static int propagarModelo(String imei, String code,
                              List<EntradaAsignacion> pilaRep, List<EntradaAsignacion> pilaGlass) {
        if (imei == null) return 0;
        int n = 0;
        for (EntradaAsignacion x : pilaRep)   if (imei.equals(x.imei)) { x.modeloCode = code; n++; }
        for (EntradaAsignacion x : pilaGlass) if (imei.equals(x.imei)) { x.modeloCode = code; n++; }
        return n;
    }
```

(`List` ya está importado en el controlador.)

- [ ] **Step 5: Ejecutar la clase de test y luego la suite completa**

```bash
mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=PendientesSuperTecnicoControllerTest
mvn -q -f gestion-reparaciones-cliente/pom.xml test
```

Expected: ambos en silencio (BUILD SUCCESS). Suite = **191** (188 + 3). Comprobar la cifra con `grep -h "Tests run:" gestion-reparaciones-cliente/target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/controllers/PendientesSuperTecnicoControllerTest.java
git commit -m "feat(cliente): helper puro propagarModelo para el modelo vivo del modal de asignacion (+3 tests; EntradaAsignacion package-private)"
git status --short   # esperado: solo el gitlink M y los untracked de siempre
```

---

### Task 2: Mapa `modeloPorImei` + `decidirModelo` en las cuatro vías manuales + guard de Enter

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`clienteManual` `:1780`; `confirmarModelo` `:1985-1995`; vías manuales `:2232-2234`, `:2246-2248`, `:2256-2259`, `:2260-2265`)

**Interfaces:**
- Consumes: `propagarModelo(...)` (Task 1); `confirmarModelo` (existente, `Consumer<String>`), `actual[0]` (entrada cargada), `pilaRep`/`pilaGlass` (`:1643-1644`), `renderPila[0]` (holder), `modeloSel[0]`, `tfModelo`, `modelosFiltrados`.
- Produces: `final Map<String,String> modeloPorImei` (IMEI → código interno; memoria del modal) que usan las Tasks 3 y 4; `Consumer<String> decidirModelo` (Task 4 le añade el guardado).

- [ ] **Step 1: Declarar el mapa junto a `clienteManual`**

Localizar (`:1780`, texto único):

```java
        final java.util.Map<String, Cliente> clienteManual = new java.util.HashMap<>();
```

y dejarlo así:

```java
        final java.util.Map<String, Cliente> clienteManual = new java.util.HashMap<>();
        // Modelo vivo: último modelo conocido por IMEI en este modal (lookup con éxito o decisión manual).
        // Lo alimentan decidirModelo (put) y el lookup (putIfAbsent: nunca pisa una decisión manual); lo
        // consumen la siembra al escanear y la propagación entre las colas Reparación/Glass. Por-IMEI, sin
        // default para los siguientes IMEIs (un modelo "pegajoso" entre teléfonos distintos no tiene sentido).
        final java.util.Map<String, String> modeloPorImei = new java.util.HashMap<>();
```

- [ ] **Step 2: Definir `decidirModelo` justo después de `confirmarModelo`**

Localizar el bloque completo (`:1985-1995`):

```java
        java.util.function.Consumer<String> confirmarModelo = code -> {
            modeloSel[0] = code;
            if (actual[0] != null) actual[0].modeloCode = code;
            actualizandoModelo[0] = true;
            tfModelo.setText(FormularioReparacionController.traducirModelo(code));
            modelosFiltrados.setPredicate(s -> true);
            actualizandoModelo[0] = false;
            popupModelo.hide();
            if (renderPila[0] != null) renderPila[0].run();
            validarForm.run();
        };
```

y añadir inmediatamente después:

```java
        // ── Modelo vivo (calcado del cliente pegajoso, solo la parte por-IMEI) ─────────────────
        // Decisión MANUAL de modelo para la entrada cargada: confirma en el formulario, la recuerda para los
        // IMEIs que se escaneen después y la copia a todas las entradas del mismo IMEI en las dos colas
        // (rojas y verdes). El lookup NO pasa por aquí: sigue llamando a confirmarModelo.
        java.util.function.Consumer<String> decidirModelo = code -> {
            EntradaAsignacion e = actual[0];
            confirmarModelo.accept(code);
            if (e == null || code == null || code.isEmpty()) return;
            modeloPorImei.put(e.imei, code);
            propagarModelo(e.imei, code, pilaRep, pilaGlass);
            if (renderPila[0] != null) renderPila[0].run();
        };
```

- [ ] **Step 3: Enrutar las cuatro vías manuales por `decidirModelo` (y el guard de Enter)**

(a) Enter en el campo. Localizar (`:2232-2234`):

```java
        tfModelo.setOnAction(e -> {
            if (!modelosFiltrados.isEmpty()) confirmarModelo.accept(modelosFiltrados.get(0));
        });
```

y dejarlo así:

```java
        tfModelo.setOnAction(e -> {
            // Guard: con un modelo ya confirmado y el texto sin tocar, Enter no re-decide. Antes re-confirmaba
            // modelosFiltrados.get(0) = el PRIMER modelo de toda la lista (confirmar resetea el filtro a "todos").
            String texto = tfModelo.getText() == null ? "" : tfModelo.getText().trim();
            if (modeloSel[0] != null && FormularioReparacionController.traducirModelo(modeloSel[0]).equals(texto)) return;
            if (!modelosFiltrados.isEmpty()) decidirModelo.accept(modelosFiltrados.get(0));
        });
```

(b) Pérdida de foco con coincidencia exacta. Localizar (`:2246-2248`, texto único):

```java
                if (exacto != null) {
                    confirmarModelo.accept(exacto);
                } else {
```

y dejarlo así:

```java
                if (exacto != null) {
                    decidirModelo.accept(exacto);
                } else {
```

(c) Clic en la lista. Localizar (`:2256-2259`):

```java
        listaModelos.setOnMouseClicked(e -> {
            String sel = listaModelos.getSelectionModel().getSelectedItem();
            if (sel != null) confirmarModelo.accept(sel);
        });
```

y dejarlo así:

```java
        listaModelos.setOnMouseClicked(e -> {
            String sel = listaModelos.getSelectionModel().getSelectedItem();
            if (sel != null) decidirModelo.accept(sel);
        });
```

(d) Enter en la lista. Localizar (`:2260-2265`):

```java
        listaModelos.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String sel = listaModelos.getSelectionModel().getSelectedItem();
                if (sel != null) confirmarModelo.accept(sel);
            }
        });
```

y dejarlo así:

```java
        listaModelos.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String sel = listaModelos.getSelectionModel().getSelectedItem();
                if (sel != null) decidirModelo.accept(sel);
            }
        });
```

Verificación: `grep -n "confirmarModelo.accept" PendientesSuperTecnicoController.java` debe devolver **exactamente dos** llamadas: la del lookup (`if (actual[0] == e) confirmarModelo.accept(res);`) y la de dentro de `decidirModelo`. `grep -n "decidirModelo.accept"` debe devolver **cuatro**.

- [ ] **Step 4: Suite completa**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q -f gestion-reparaciones-cliente/pom.xml test
```

Expected: silencio, 191 tests. (La lógica de este paso son handlers JavaFX sin costura de test; la cubre el smoke de la Task 4, puntos 2 y 7.)

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(cliente): modelo vivo en el modal - mapa modeloPorImei y decidirModelo en las 4 vias manuales (propaga a Reparacion/Glass); guard de Enter con modelo ya confirmado"
```

---

### Task 3: Siembra al escanear + el lookup no pisa decisiones manuales

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`lanzarLookup` `:2058-2063`; `sembrarClienteEntrada` `:2178-2181`; `intentarAnadir` `:2277-2278`; pegado múltiple `:2308-2309`)

**Interfaces:**
- Consumes: `modeloPorImei` (Task 2); `EntradaAsignacion.tieneModelo()`; `sembrarClienteEntrada` / `aplicarClienteDefaultEntrada` (existentes, marcan el sitio de la siembra).
- Produces: `Consumer<EntradaAsignacion> sembrarModeloEntrada` (usado en las dos vías de escaneo).

- [ ] **Step 1: Definir `sembrarModeloEntrada` junto a `sembrarClienteEntrada`**

Localizar (`:2176-2181`):

```java
        // Siembra en una entrada/fila nueva la última decisión MANUAL de cliente del modal para ese IMEI,
        // para que la precarga de BD (que corre después) no la pise.
        java.util.function.Consumer<EntradaAsignacion> sembrarClienteEntrada = e -> {
            Cliente m = clienteManual.get(e.imei);
            if (m != null) { e.sinCliente = (m == SIN_CLIENTE); e.cliente = e.sinCliente ? null : m; }
        };
```

y añadir inmediatamente después:

```java
        // Modelo vivo: si el modal ya conoce el modelo de este IMEI (lookup con éxito o decisión manual en
        // cualquier cola), la entrada nace con él y lanzarLookup se la salta (tieneModelo()).
        java.util.function.Consumer<EntradaAsignacion> sembrarModeloEntrada = e -> {
            String m = modeloPorImei.get(e.imei);
            if (m != null && !m.isEmpty()) e.modeloCode = m;
        };
```

- [ ] **Step 2: Sembrar en el escaneo simple**

Localizar en `intentarAnadir` (`:2277-2278`; la variable es `e`):

```java
            sembrarClienteEntrada.accept(e);   // hereda el cliente ya decidido en el modal para este IMEI
            aplicarClienteDefaultEntrada.accept(e);   // si no hay decisión manual, pinta ya el cliente "pegajoso"
```

y dejarlo así:

```java
            sembrarClienteEntrada.accept(e);   // hereda el cliente ya decidido en el modal para este IMEI
            aplicarClienteDefaultEntrada.accept(e);   // si no hay decisión manual, pinta ya el cliente "pegajoso"
            sembrarModeloEntrada.accept(e);    // modelo vivo: nace con el modelo que el modal ya conoce (sin lookup)
```

- [ ] **Step 3: Sembrar en el pegado múltiple**

Localizar en el bucle `for (String imei : res.imeis())` (`:2308-2309`; la variable es `en`):

```java
                    sembrarClienteEntrada.accept(en);   // hereda el cliente ya decidido en el modal para este IMEI
                    aplicarClienteDefaultEntrada.accept(en);   // si no hay decisión manual, pinta ya el cliente "pegajoso"
```

y dejarlo así:

```java
                    sembrarClienteEntrada.accept(en);   // hereda el cliente ya decidido en el modal para este IMEI
                    aplicarClienteDefaultEntrada.accept(en);   // si no hay decisión manual, pinta ya el cliente "pegajoso"
                    sembrarModeloEntrada.accept(en);    // modelo vivo: nace con el modelo que el modal ya conoce (sin lookup)
```

- [ ] **Step 4: El lookup recuerda su resultado sin pisar decisiones manuales**

Localizar dentro de `lanzarLookup` (`:2058-2063`):

```java
                    if (res != null && !res.isEmpty()) {
                        e.modeloCode = res;
                        if (actual[0] == e) confirmarModelo.accept(res);
                    } else if (actual[0] == e) {
```

y dejarlo así:

```java
                    if (res != null && !res.isEmpty()) {
                        // Modelo vivo: recuerda el resultado (sin pisar una decisión manual) y aplícalo solo si la
                        // entrada sigue sin modelo (una decisión manual desde la otra cola pudo llegar en vuelo).
                        modeloPorImei.putIfAbsent(e.imei, res);
                        if (!e.tieneModelo()) {
                            e.modeloCode = res;
                            if (actual[0] == e) confirmarModelo.accept(res);
                        }
                    } else if (actual[0] == e) {
```

Nota de orden de declaración (todo son lambdas locales, deben existir antes de usarse): el mapa `modeloPorImei` (`:1780`, Task 2) precede a la asignación de `lanzarLookup[0]` (`:2043`); `sembrarModeloEntrada` (`:2178` aprox.) precede a las dos vías de escaneo (`:2268` y `:2296`). Todas las referencias de esta tarea son válidas sin mover nada.

- [ ] **Step 5: Suite completa**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q -f gestion-reparaciones-cliente/pom.xml test
```

Expected: silencio, 191 tests.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(cliente): modelo vivo en el modal - siembra al escanear (simple y pegado) y el lookup no pisa una decision manual en vuelo"
```

---

### Task 4: Guardado inmediato en segundo plano + CHANGELOG + smoke

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`decidirModelo`, Task 2)
- Modify: `CHANGELOG.md` (sección `## [Unreleased]`, `### Changed` y `### Fixed`)

**Interfaces:**
- Consumes: `decidirModelo` (Task 2); `telefonoDAO.insertar(String imei, String modelo)` (existente, `TelefonoDAO.java:48`, lanza `SQLException`).
- Produces: `BiConsumer<String,String> persistirModelo` (solo lo usa `decidirModelo`).

- [ ] **Step 1: Definir `persistirModelo` y engancharlo a `decidirModelo`**

Localizar el bloque de la Task 2:

```java
        java.util.function.Consumer<String> decidirModelo = code -> {
            EntradaAsignacion e = actual[0];
            confirmarModelo.accept(code);
            if (e == null || code == null || code.isEmpty()) return;
            modeloPorImei.put(e.imei, code);
            propagarModelo(e.imei, code, pilaRep, pilaGlass);
            if (renderPila[0] != null) renderPila[0].run();
        };
```

y dejarlo así (se define `persistirModelo` ANTES para que la lambda lo capture):

```java
        // Guardado inmediato del modelo del IMEI: upsert de teléfono de DOS argumentos (modelo sí; el cliente
        // queda intacto por COALESCE en el servidor; sin entrada de log). En hilo aparte, como el lookup.
        // Si falla: una línea en stderr y nada más — Guardar vuelve a mandar el modelo con la asignación.
        java.util.function.BiConsumer<String, String> persistirModelo = (imei, code) -> {
            Thread t = new Thread(() -> {
                try { telefonoDAO.insertar(imei, code); }
                catch (Exception ex) { System.err.println("[asignación] No se pudo guardar el modelo de " + imei + ": " + ex.getMessage()); }
            });
            t.setDaemon(true);
            t.start();
        };
        java.util.function.Consumer<String> decidirModelo = code -> {
            EntradaAsignacion e = actual[0];
            confirmarModelo.accept(code);
            if (e == null || code == null || code.isEmpty()) return;
            modeloPorImei.put(e.imei, code);
            propagarModelo(e.imei, code, pilaRep, pilaGlass);
            if (renderPila[0] != null) renderPila[0].run();
            persistirModelo.accept(e.imei, code);
        };
```

Verificación: `grep -n "telefonoDAO.insertar(" PendientesSuperTecnicoController.java` → la llamada nueva es la **única de dos argumentos** dentro del modal; las de `btnGuardar` (4 args) y la de "Editar modelo" (2 args, fuera del modal) siguen intactas.

- [ ] **Step 2: CHANGELOG**

En `CHANGELOG.md`, sección `## [Unreleased]`, añadir al final de `### Changed`:

```markdown
- **Modelo compartido en el modal de asignación**: el modelo es del IMEI, no de cada cola. Al elegirlo o cambiarlo a mano se guarda al instante y todas las entradas de ese IMEI (Reparación y Glass) lo muestran; un IMEI que el modal ya conoce nace con modelo al escanearlo, sin volver a buscar. Antes, un modelo elegido a mano en Reparación había que volver a elegirlo al asignar la glass del mismo teléfono.
```

y al final de `### Fixed`:

```markdown
- En el modal de asignación, pulsar Enter en el campo de modelo con un modelo ya confirmado re-seleccionaba el primer modelo de la lista.
```

- [ ] **Step 3: Suite completa**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q -f gestion-reparaciones-cliente/pom.xml test
```

Expected: silencio, 191 tests.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java CHANGELOG.md
git commit -m "feat(cliente): guardado inmediato del modelo al decidirlo en el modal (upsert de telefono en segundo plano, cliente intacto) + CHANGELOG"
git log --oneline hotfix/0.16.2..HEAD   # esperado: 4 commits de feature
```

- [ ] **Step 5: Smoke manual (lo ejecuta el USUARIO en preproducción, como SuperTécnico)**

Lanzar el cliente: `mvn -f gestion-reparaciones-cliente/pom.xml javafx:run` (con el prefijo del toolchain) o el ▶ de VS Code. Checklist (spec §6):

1. Cola Reparación: IMEI cuyo lookup falla → elegir modelo a mano → cola Glass: escanear el mismo IMEI → nace con el modelo, sin "Buscando…".
2. Cola Glass: cambiar el modelo → volver a Reparación → la entrada (roja o verde) muestra el nuevo.
3. Elegir a mano y cerrar el modal sin guardar → `SELECT MODELO FROM Telefono WHERE IMEI='…'` lo tiene.
4. Lookup con éxito en Reparación → Glass hereda sin segunda búsqueda; con un IMEI nuevo, no hay fila en `Telefono` hasta Guardar.
5. IMEI con cliente en BD → tras un guardado temprano, el cliente sigue.
6. Pegado múltiple con IMEIs ya conocidos por el modal → nacen con modelo.
7. Enter en el campo con el modelo ya confirmado → no cambia nada.
8. Guardar → asignaciones y modelos correctos; sin regresión en cliente pegajoso ni en pulido.
9. (review final) Asignar 2 IMEIs del cliente D (pegajoso = D), escanear en Reparación un IMEI X que en BD tiene cliente C, **no** asignarlo, pasar a Glass y escanear X → la entrada de Glass muestra **C**, no D.
10. (review final) Entrada cargada con el campo de modelo vacío (lookup fallido) → Enter → no pasa nada; sin fila nueva en `Telefono`.
11. (review final) Teclear el nombre exacto de un modelo y hacer clic en "Asignar →" → el modelo se decide y se guarda pero la entrada **no** se asigna; el segundo clic asigna (vía de pérdida de foco).
12. (review final) Elegir modelo a mano, quitar la entrada con ✕ y volver a escanear el mismo IMEI en el mismo modal → nace con modelo. Ese IMEI aparece ya en Inventario (fila de `Telefono` sin asignación): efecto aceptado, verlo una vez.
13. (review final) Elegir el modelo A e inmediatamente el B en el mismo IMEI → `SELECT MODELO` da B.

Fix wave del review final (2026-09-05, un commit tras la Task 4): brief en `.superpowers/sdd/final-fix-brief.md` — la siembra ya no salta la precarga del cliente de BD (solo la mitad de modelo), Enter con campo vacío no decide, guard de origen en la vía de pérdida de foco.

Tras el smoke: **merge `--no-ff` a `hotfix/0.16.2` solo con OK explícito del usuario** (fuera de este plan). No push, no tag.
