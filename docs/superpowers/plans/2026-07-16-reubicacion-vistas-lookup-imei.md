# Reubicación de vistas + lookup IMEI — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separar la vista del taller ("IMEIs" = historial por IMEI, como pre-F2a sin columna OK y con sufijo eSIM) del inventario completo (pestaña superior nueva "Inventario", solo SUPERTECNICO+ADMIN), y añadir el lookup de modelo por IMEI al alta manual de lotes — según la spec `docs/superpowers/specs/2026-07-16-reubicacion-vistas-lookup-imei-design.md`.

**Architecture:** `AgrupadoController` gana un enum `Vista { TALLER, INVENTARIO }` cuya configuración (columnas maestro, filtros, botones, filtro base "solo con trabajos", cabecera CSV) vive en una clase descriptor PURA y testeable (`ConfigVistaAgrupado`). Los tres hosts de Reparaciones pasan `TALLER`; una pestaña nueva en `MainController` carga la misma vista en `INVENTARIO`. El lookup del alta manual reutiliza `POST /api/lotes/verificar` (ya devuelve `modelo` de los IMEIs conocidos) + `GET /api/telefonos/{imei}/modelo` en cola para los desconocidos, con precedencia manual>detectado>común en un helper puro testeable.

**Tech Stack:** Java 17 · JavaFX 21 · JUnit 5. SOLO repo raíz/cliente — sin cambios de servidor ni BD.

## Global Constraints

- **Un solo repo**: raíz `c:\Users\info\Documents\ProgramaReparaciones` (cliente en `gestion-reparaciones-cliente/`). Rama: **`feature/reubicacion-vistas`** (YA existe, plan commiteado en ella; parte de main `c23afdc+`).
- **Commits SIN `Co-Authored-By`**; mensajes `feat/fix(ámbito): descripción en español`.
- **Merge, push y tags: SOLO con OK explícito del usuario.**
- **Comandos por Bash**: suite `cd gestion-reparaciones-cliente && mvn -q test`.
- **Sin cambios de servidor/BD/endpoints.** La API key de imeicheck vive solo en la VM — no aparece en ningún fichero del cliente.
- El modo `INVENTARIO` debe quedar **idéntico** a la vista actual (regresión cero: columnas, filtros, botones, CSV de 19 columnas, columna Revisión).
- Sufijo eSIM: el maestro ya lo pinta vía `modeloVisibleMaestro(...)` — TALLER lo hereda gratis, no reimplementar.
- CSV = espejo semántico de la tabla visible (convención de la casa).
- Precedencia de modelo en alta manual: **manual por fila > detectado (BD/API) > modelo común**.
- Rate limit API: cola secuencial, ~2 s entre lookups (30 req/min), solo IMEIs que la BD no conoce.

## Contexto imprescindible (leído del código real, HEAD c23afdc)

- `AgrupadoController` (1688 líneas): `enum Rol` (L63) + `configurar(Rol)` (L185-191, muestra `btnImportar`/`btnAltaManual` solo a SUPERTECNICO). Enum interno `Modo { MAESTRO, DETALLE }` (L123) es el drill-down — NO confundir con la nueva `Vista`. Columnas maestro se aplican en `aplicarColumnasMaestro()` (L265-275); filtros en `configurarFiltros()` (L970) + `poblarFiltrosMaestro()` (L1042) + `adaptarFiltrosMaestro()` (L1125, oculta `filtroTecnico` a TECNICO); datos en `cargar()` (L194-224: resúmenes rep/glass/pulido en `datos` + `telefonoDAO.getInventario()` en `inventario`); CSV en `exportarCSV(Stage)` (L1619-1682: rama maestro 19 columnas, rama detalle 12 columnas); sufijo eSIM en `modeloVisibleMaestro` (L920) usado en L361/L930/L1634.
- La vista se incrusta con `<fx:include source="AgrupadoView.fxml" fx:id="agrupado"/>` en `ReparacionViewSuperTecnico.fxml:121`, `ReparacionViewAdmin.fxml:109`, `ReparacionViewTecnico.fxml:127`; cada host llama `agrupadoController.configurar(Rol.X)` en su `initialize()` (SuperTecnico L227, Admin L159, Tecnico L197) y `agrupadoController.cargar()` al pulsar "IMEIs".
- Columnas maestro HOY: IMEI, Modelo, GB, Color, Grado, Última actividad, Trabajos, Estado, Ubicación, Lote, Observación, Cliente, Revisión. PRE-F2a (cf9df9c) eran: IMEI, Modelo, Última actividad(Fechas), Trabajos, Observación, Cliente, Revisión — y filtros solo IMEI/Técnico/Cliente/Fechas/Incidencias. Fuente de datos pre-F2a: SOLO resúmenes de trabajos (sin inventario) ⇒ solo teléfonos con trabajos.
- CSV pre-F2a maestro (11 col): `IMEI, Modelo, Primera, Última, Reparaciones, Glass, Pulidos, Inc. abiertas, Observación, Cliente, Revisión logística`. La rama detalle (12 col) es idéntica a la actual.
- `MainController`: nav superior en `MainView.fxml:30-43` (`btnReparaciones/btnStock/btnEstadisticas/btnClientes`), carga con `mostrarVista(ruta, activo, inactivos...)` (L942, con `vistaCache`); visibilidad por rol vía `Sesion.esAdmin()/esSuperTecnico()/esAdminOSuperTecnico()` (patrón campana L111-116); parámetros post-carga con `instanceof` (Estadísticas L962-969, filtros L978-985). Menú usuario "Descargar CSV" → `descargarCSV()` (L827) → `controladorActivo.exportarCSV(...)` si implementa `Exportable`; hoy el host `ReparacionControllerSuperTecnico` delega en `agrupadoController.exportarCSV(owner)` cuando `pnlAgrupado.isVisible()` (L1174).
- `AltaManualLoteDialog` (538 líneas): filas = `ObservableList<String> imeis` (L91) en `ListView<String> listaImeis` con botón ✕ por celda (L226-229); scan/pegado en el listener de `tfScan` (L270-305, bulk vía `ImeiUtils.parsearPegadoImeis`); modelo común `modeloInterno` (L92) elegido con `SelectorModeloDialog.elegir(null)` (L155); `confirmar()` (L446-514) llama `loteDAO.verificar(imeisSnapshot)` (L464, `POST /api/lotes/verificar` → `List<VerificacionImei>` con campos `imei, existe, estado, trabajosAbiertos, modelo` — HOY solo usa `esActivo()`), y construye `TelefonoImport(imei, modelo, ...)` con `modelo = modeloInterno` para TODAS las filas (L452, L487). Hilos con nombre: `alta-manual-lote-carga`, `tasa-alta-manual-lote`, `alta-manual-lote-importar`; alerts diferidos con `Platform.runLater` anidado.
- `TelefonoDAO.getModelo(String imei)` (L93-96): `GET /api/telefonos/{imei}/modelo` → `String` código interno o null (servidor hace BD primero, API imeicheck fallback). Síncrono — SIEMPRE llamarlo desde hilo de fondo.
- `SelectorModeloDialog.elegir(String actual)` → String código interno o null (cancelar).

## Estructura de ficheros

**Create:** `controllers/ConfigVistaAgrupado.java` (+test), `utils/LookupModelosImeis.java` (+test).
**Modify:** `controllers/AgrupadoController.java`, `views/AgrupadoView.fxml` (solo si hace falta fx:id nuevo), `ReparacionControllerSuperTecnico/Admin/Tecnico.java` (1 línea cada uno), `MainController.java` + `views/MainView.fxml`, `controllers/AltaManualLoteDialog.java`.

---

### Task 1: `ConfigVistaAgrupado` — descriptor puro de la configuración por vista (TDD)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ConfigVistaAgrupado.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/controllers/ConfigVistaAgrupadoTest.java`

**Interfaces:**
- Produces (consumido por T2/T3): `enum ConfigVistaAgrupado.Vista { TALLER, INVENTARIO }` y métodos estáticos:
  - `columnasMaestro(Vista)` → `List<String>` de claves lógicas de columna, en orden de la tabla.
  - `filtrosVisibles(Vista)` → `Set<String>` de claves de filtro.
  - `botonesImportacion(Vista)` → `boolean` (si la vista puede mostrar Importar/Alta manual — el gating por rol adicional lo mantiene el controller).
  - `soloConTrabajos(Vista)` → `boolean`.
  - `cabeceraCsvMaestro(Vista)` → `List<String>`.
- Claves de columna (deben mapear 1:1 a los campos `colX` del controller): `imei, modelo, storage, color, grado, ultimaActividad, trabajos, estado, ubicacion, lote, observacionTelefono, cliente, revision`.
- Claves de filtro: `imei, tecnico, cliente, estado, ubicacion, lote, modelo, fechas, incidencias`.

- [ ] **Paso 1: Test que falla**

```java
package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.controllers.ConfigVistaAgrupado.Vista.INVENTARIO;
import static com.reparaciones.controllers.ConfigVistaAgrupado.Vista.TALLER;
import static org.junit.jupiter.api.Assertions.*;

class ConfigVistaAgrupadoTest {

    @Test void inventarioConservaLaVistaActualCompleta() {
        assertEquals(List.of("imei", "modelo", "storage", "color", "grado", "ultimaActividad",
                        "trabajos", "estado", "ubicacion", "lote", "observacionTelefono", "cliente", "revision"),
                ConfigVistaAgrupado.columnasMaestro(INVENTARIO));
        assertTrue(ConfigVistaAgrupado.filtrosVisibles(INVENTARIO).containsAll(
                java.util.Set.of("imei", "tecnico", "cliente", "estado", "ubicacion", "lote", "modelo", "fechas", "incidencias")));
        assertTrue(ConfigVistaAgrupado.botonesImportacion(INVENTARIO));
        assertFalse(ConfigVistaAgrupado.soloConTrabajos(INVENTARIO));
        assertEquals(19, ConfigVistaAgrupado.cabeceraCsvMaestro(INVENTARIO).size());
    }

    @Test void tallerEsElHistorialPreF2aSinOkConEsim() {
        assertEquals(List.of("imei", "modelo", "ultimaActividad", "trabajos", "observacionTelefono", "cliente"),
                ConfigVistaAgrupado.columnasMaestro(TALLER));
        assertEquals(java.util.Set.of("imei", "tecnico", "cliente", "fechas", "incidencias"),
                ConfigVistaAgrupado.filtrosVisibles(TALLER));
        assertFalse(ConfigVistaAgrupado.botonesImportacion(TALLER));
        assertTrue(ConfigVistaAgrupado.soloConTrabajos(TALLER));
    }

    @Test void csvTallerEsEspejoSinRevision() {
        assertEquals(List.of("IMEI", "Modelo", "Última actividad", "Reparaciones", "Glass", "Pulidos",
                        "Abiertos", "Inc. abiertas", "Observación", "Cliente"),
                ConfigVistaAgrupado.cabeceraCsvMaestro(TALLER));
        assertFalse(ConfigVistaAgrupado.columnasMaestro(TALLER).contains("revision"));
    }
}
```

- [ ] **Paso 2: Ver que falla** — `mvn -q test -Dtest=ConfigVistaAgrupadoTest` → no compila.

- [ ] **Paso 3: Implementación**

```java
package com.reparaciones.controllers;

import java.util.List;
import java.util.Set;

/**
 * Configuración declarativa de las dos vistas del Agrupado por IMEI
 * (spec reubicación 2026-07-16): TALLER = historial por IMEI (pre-F2a sin
 * columna OK, con sufijo eSIM); INVENTARIO = vista completa actual.
 * Puro y testeable: el controller solo consume estas listas.
 */
public final class ConfigVistaAgrupado {

    public enum Vista { TALLER, INVENTARIO }

    private ConfigVistaAgrupado() {}

    private static final List<String> COLS_INVENTARIO = List.of(
            "imei", "modelo", "storage", "color", "grado", "ultimaActividad",
            "trabajos", "estado", "ubicacion", "lote", "observacionTelefono", "cliente", "revision");

    private static final List<String> COLS_TALLER = List.of(
            "imei", "modelo", "ultimaActividad", "trabajos", "observacionTelefono", "cliente");

    private static final List<String> CSV_INVENTARIO = List.of(
            "IMEI", "Modelo", "Storage", "Color", "Grado propio", "Grado proveedor", "Estado",
            "Ubicación", "Lote", "Proveedor", "Última actividad", "Reparaciones", "Glass",
            "Pulidos", "Abiertos", "Inc. abiertas", "Observación", "Cliente", "Revisión logística");

    private static final List<String> CSV_TALLER = List.of(
            "IMEI", "Modelo", "Última actividad", "Reparaciones", "Glass", "Pulidos",
            "Abiertos", "Inc. abiertas", "Observación", "Cliente");

    public static List<String> columnasMaestro(Vista v) {
        return v == Vista.TALLER ? COLS_TALLER : COLS_INVENTARIO;
    }

    public static Set<String> filtrosVisibles(Vista v) {
        return v == Vista.TALLER
                ? Set.of("imei", "tecnico", "cliente", "fechas", "incidencias")
                : Set.of("imei", "tecnico", "cliente", "estado", "ubicacion", "lote", "modelo", "fechas", "incidencias");
    }

    public static boolean botonesImportacion(Vista v) { return v == Vista.INVENTARIO; }

    public static boolean soloConTrabajos(Vista v) { return v == Vista.TALLER; }

    public static List<String> cabeceraCsvMaestro(Vista v) {
        return v == Vista.TALLER ? CSV_TALLER : CSV_INVENTARIO;
    }
}
```

(La cabecera CSV_INVENTARIO debe coincidir LETRA A LETRA con la actual de `exportarCSV` L1623-1654 — leerla del código real al transcribir, no de este snippet.)

- [ ] **Paso 4: Verde + suite completa + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(vistas): descriptor puro de configuración TALLER/INVENTARIO del agrupado por IMEI"
```

---

### Task 2: `AgrupadoController` consume el descriptor — modo TALLER en los tres hosts

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java`
- Modify (1 línea cada uno): `ReparacionControllerSuperTecnico.java:227`, `ReparacionControllerAdmin.java:159` (aprox), `ReparacionControllerTecnico.java:197` (aprox)

**Interfaces:**
- Consumes: `ConfigVistaAgrupado` (T1).
- Produces: `configurar(Rol rol, ConfigVistaAgrupado.Vista vista)` — firma nueva; T3 la usa con `INVENTARIO`.

- [ ] **Paso 1: Leer primero** `configurar` (L185), `aplicarColumnasMaestro` (L265), `configurarFiltros`/`poblarFiltrosMaestro` (L970/L1042), `cargar` (L194), `aplicarFiltros` (L1186), `exportarCSV` (L1619). El objetivo es que TODO lo dependiente de vista pase por el descriptor — nada de ifs sueltos.

- [ ] **Paso 2: Implementar**
  1. Campo `private ConfigVistaAgrupado.Vista vista = ConfigVistaAgrupado.Vista.INVENTARIO;` junto a `rol`. `configurar(Rol rol, ConfigVistaAgrupado.Vista vista)` sustituye a `configurar(Rol)` (actualizar los 3 call-sites de los hosts a `Vista.TALLER`): guarda ambos y aplica `btnImportar/btnAltaManual` visibles solo si `ConfigVistaAgrupado.botonesImportacion(vista) && rol == Rol.SUPERTECNICO` (el gating por rol NO cambia — ADMIN sigue sin botones, como hoy).
  2. Mapa `columnaPorClave`: `Map<String, TableColumn<?,?>>` construido una vez en `initialize()` con las 13 claves del descriptor → campos `colImei, colModelo, colStorage, colColor, colGrado, colFecha, colComponente, colEstado, colUbicacion, colLote, colObservacionTelefono, colCliente, colRevision`. `aplicarColumnasMaestro()` pasa a: visibles = `ConfigVistaAgrupado.columnasMaestro(vista)` en ese orden (mismo mecanismo actual de mostrar/ocultar).
  3. Filtros: en `configurarFiltros()`/`poblarFiltrosMaestro()`, ocultar (visible+managed=false, patrón `ocultarTecnico` L1132) los controles cuya clave NO esté en `filtrosVisibles(vista)`; `adaptarFiltrosMaestro()` (rol) se aplica DESPUÉS y solo puede ocultar más, nunca re-mostrar.
  4. Filtro base: en `aplicarFiltros()` (rama maestro), si `soloConTrabajos(vista)`, excluir los `TelefonoInventario` sin trabajos. Criterio: `t.getResumenTipos()` vacío/null Y `t.getTrabajosAbiertos() == 0` ⇒ fuera (VERIFICAR contra el modelo real: si existe un `isTieneAsignaciones()` que cubra rep+glass+pulido, usarlo y anotarlo en el informe).
  5. CSV: la rama maestro de `exportarCSV` usa `cabeceraCsvMaestro(vista)` y emite solo los valores de esas columnas (en TALLER: IMEI, Modelo con `modeloVisibleMaestro`, Última actividad, contadores Reparaciones/Glass/Pulidos/Abiertos/Inc. abiertas, Observación, Cliente — SIN Revisión). La rama detalle (12 col) queda intacta en ambas vistas. Nombre de fichero: mantener `agrupado_resumen`/`agrupado_<imei>`.
  6. En modo TALLER no debe romperse nada del drill-down (`Modo.DETALLE`): el detalle es idéntico en ambas vistas.

- [ ] **Paso 3: Compilar + suite completa** — `mvn -q test` → verde (los hosts ya pasan TALLER; los tests existentes no instancian el controller JavaFX).

- [ ] **Paso 4: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(vistas): IMEIs del taller vuelve al historial por IMEI (modo TALLER) y el inventario queda como modo propio"
```

---

### Task 3: Pestaña "Inventario" en `MainController` (solo SUPERTECNICO+ADMIN) + CSV

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/MainView.fxml` (~L30-43), `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/MainController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (implements Exportable, si no lo es ya)

**Interfaces:**
- Consumes: `configurar(Rol, Vista)` de T2; patrón `mostrarVista` (L942), `Sesion.esAdminOSuperTecnico()`, interfaz `Exportable` (la que implementa `ReparacionControllerSuperTecnico:49`).

- [ ] **Paso 1: Implementar**
  1. `MainView.fxml`: botón `btnInventario` con texto "Inventario" tras `btnStock`, `onAction="#mostrarInventario"`, `visible="false" managed="false"` por defecto (mismo patrón que la campana).
  2. `MainController.initialize()`: si `Sesion.esAdminOSuperTecnico()`, mostrar `btnInventario` (visible+managed=true) — calco del bloque campana L111-116.
  3. `mostrarInventario()`: `mostrarVista("/views/AgrupadoView.fxml", btnInventario, <resto de botones nav>)`; tras obtener el controller (patrón `instanceof` como Estadísticas L962-969 en el miss de caché): `ac.configurar(Sesion.esSuperTecnico() ? Rol.SUPERTECNICO : Rol.ADMIN, Vista.INVENTARIO)`. En CADA visualización (también con caché, patrón L978-985): `ac.cargar()` — igual que hacen los hosts de la sidebar al pulsar "IMEIs".
  4. CSV del inventario: hacer que `AgrupadoController` implemente la interfaz `Exportable` (su `exportarCSV(Stage)` ya existe con la firma correcta — verificar la interfaz en `ReparacionControllerSuperTecnico:49` y calcar). Así el menú "Descargar CSV" funciona cuando `controladorActivo` es el Agrupado de la pestaña. El delegado existente del host de Reparaciones (L1174) queda como está (exportará el CSV TALLER, espejo de esa vista — correcto por convención).
  5. Los botones nav restantes: pasar `btnInventario` también como inactivo en los otros `mostrarX()` para que el resaltado del activo funcione (leer cómo marca activo/inactivos `mostrarVista` y calcar).

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)** — sin test propio (wiring de menú; precedente F2a).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(vistas): pestaña Inventario (SUPERTECNICO+ADMIN) con la vista completa y CSV por el menú"
```

---

### Task 4: `LookupModelosImeis` — cola y precedencia (TDD, puro)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/LookupModelosImeis.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/LookupModelosImeisTest.java`

**Interfaces:**
- Produces (consumido por T5):
  - `static String modeloParaFila(String manual, String detectado, String comun)` → primera no-nula/no-blank de las tres, o null.
  - Clase instanciable `LookupModelosImeis(Function<String,String> lookup, LongConsumer espera, Consumer<Resultado> callback)` con `record Resultado(String imei, String modelo)`:
    - `encolar(Collection<String> imeis)` — añade pendientes (dedup, ignora ya resueltos/encolados).
    - `descartar(String imei)` — el usuario quitó la fila; no gastar lookup.
    - `procesarPendientes()` — SÍNCRONO: consume la cola en orden llamando `lookup` una vez por IMEI, invocando `espera.accept(2000)` ANTES de cada lookup salvo el primero (el pacing real lo inyecta T5 con `Thread.sleep`; en tests es un registrador), y notificando `callback` con cada resultado (modelo null incluido = "no resuelto").
  - El hilo/threading NO vive aquí — esta clase es síncrona y determinista; T5 la envuelve en un hilo.

- [ ] **Paso 1: Tests que fallan**

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LookupModelosImeisTest {

    @Test void precedenciaManualDetectadoComun() {
        assertEquals("13pro", LookupModelosImeis.modeloParaFila("13pro", "12", "15"));
        assertEquals("12", LookupModelosImeis.modeloParaFila(null, "12", "15"));
        assertEquals("15", LookupModelosImeis.modeloParaFila(null, null, "15"));
        assertEquals("15", LookupModelosImeis.modeloParaFila("  ", null, "15"));
        assertNull(LookupModelosImeis.modeloParaFila(null, null, null));
    }

    @Test void colaResuelveEnOrdenConEsperaEntreLookups() {
        List<String> consultados = new ArrayList<>();
        List<Long> esperas = new ArrayList<>();
        List<LookupModelosImeis.Resultado> resultados = new ArrayList<>();
        var cola = new LookupModelosImeis(
                imei -> { consultados.add(imei); return Map.of("111", "12", "222", "13").get(imei); },
                esperas::add, resultados::add);
        cola.encolar(List.of("111", "222", "333"));
        cola.procesarPendientes();
        assertEquals(List.of("111", "222", "333"), consultados);
        assertEquals(2, esperas.size()); // no espera antes del primero
        assertEquals("12", resultados.get(0).modelo());
        assertNull(resultados.get(2).modelo()); // 333 sin resolver → callback con null
    }

    @Test void dedupYDescartes() {
        List<String> consultados = new ArrayList<>();
        var cola = new LookupModelosImeis(i -> { consultados.add(i); return null; }, e -> {}, r -> {});
        cola.encolar(List.of("111", "111", "222"));
        cola.descartar("222");
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados);
        cola.encolar(List.of("111")); // ya consultado: no se re-consulta
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados);
    }
}
```

- [ ] **Paso 2: Ver que fallan** — `mvn -q test -Dtest=LookupModelosImeisTest` → no compila.

- [ ] **Paso 3: Implementación**

```java
package com.reparaciones.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

/**
 * Cola de detección de modelo por IMEI para el alta manual (spec reubicación
 * 2026-07-16). Síncrona y determinista: el hilo y el sleep real los pone el
 * diálogo. Pacing: espera de 2 s entre lookups (rate limit API 30 req/min).
 */
public class LookupModelosImeis {

    public record Resultado(String imei, String modelo) {}

    public static final long ESPERA_MS = 2000;

    private final Function<String, String> lookup;
    private final LongConsumer espera;
    private final Consumer<Resultado> callback;
    private final Deque<String> pendientes = new ArrayDeque<>();
    private final Set<String> vistos = new HashSet<>();
    private final Set<String> descartados = new HashSet<>();
    private boolean primero = true;

    public LookupModelosImeis(Function<String, String> lookup, LongConsumer espera, Consumer<Resultado> callback) {
        this.lookup = lookup;
        this.espera = espera;
        this.callback = callback;
    }

    /** Precedencia de la spec: manual por fila > detectado (BD/API) > modelo común. */
    public static String modeloParaFila(String manual, String detectado, String comun) {
        if (manual != null && !manual.isBlank()) return manual;
        if (detectado != null && !detectado.isBlank()) return detectado;
        return comun != null && !comun.isBlank() ? comun : null;
    }

    public synchronized void encolar(Collection<String> imeis) {
        for (String imei : imeis) {
            if (vistos.add(imei)) pendientes.addLast(imei);
        }
    }

    public synchronized void descartar(String imei) { descartados.add(imei); }

    public void procesarPendientes() {
        while (true) {
            String imei;
            synchronized (this) {
                imei = pendientes.pollFirst();
            }
            if (imei == null) return;
            if (descartados.contains(imei)) continue;
            if (!primero) espera.accept(ESPERA_MS);
            primero = false;
            String modelo;
            try {
                modelo = lookup.apply(imei);
            } catch (RuntimeException e) {
                modelo = null; // fallo de red/API: la fila cae al modelo común (spec §6)
            }
            callback.accept(new Resultado(imei, modelo));
        }
    }
}
```

- [ ] **Paso 4: Verde + suite completa + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(lotes): cola de detección de modelo por IMEI con pacing y precedencia (LookupModelosImeis)"
```

---

### Task 5: `AltaManualLoteDialog` — detección por fila cableada

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AltaManualLoteDialog.java`

**Interfaces:**
- Consumes: T4 (`LookupModelosImeis`), `loteDAO.verificar(List<String>)` → `List<VerificacionImei>` (campos `imei/existe/modelo`, YA existentes), `TelefonoDAO.getModelo(imei)` (síncrono — solo en hilo de fondo), `SelectorModeloDialog.elegir(actual)`.

- [ ] **Paso 1: Implementar** (leer entero el diálogo primero; respetar sus patrones de hilos y `Platform.runLater`):
  1. Estado nuevo: `Map<String,String> modelosDetectados` y `Map<String,String> modelosManuales` (claves = IMEI). `LookupModelosImeis colaLookup` con `lookup = new TelefonoDAO()::getModelo`, `espera = ms -> Thread.sleep(ms)` (envuelto en try/InterruptedException → return), `callback = r -> Platform.runLater(...)` que guarda en `modelosDetectados` y refresca la celda.
  2. Al añadir filas (scan único e `intentarAnadir`, y pegado masivo tras `parsearPegadoImeis`): lanzar UNA verificación en hilo `alta-manual-lote-verificar` con `loteDAO.verificar(nuevos)`: para cada `VerificacionImei` con `existe && modelo != null` → `modelosDetectados.put(imei, modelo)`; los que no existan → `colaLookup.encolar(...)` y (re)lanzar el hilo de proceso `alta-manual-lookup` si no está vivo (`new Thread(colaLookup::procesarPendientes, "alta-manual-lookup")`, daemon). Errores de red en verificar: silencio + esas filas a la cola API igualmente NO — mejor: si verificar falla, encolar TODAS (la cola ya tolera fallos por fila).
  3. Celdas de `listaImeis`: además del ✕ actual, mostrar junto al IMEI el modelo de la fila: `modelosManuales`→negrita normal, si no `modelosDetectados`→texto normal (traducido con `FormularioReparacionController.traducirModelo`), si no y hay lookup pendiente → "detectando…", si no → "—" (caerá al común). Menú contextual (clic derecho) "Editar modelo…" → `SelectorModeloDialog.elegir(modeloActualDeLaFila)` → `modelosManuales.put(imei, elegido)` y refrescar (patrón del clic-derecho de pulido).
  4. Al quitar una fila con ✕: `colaLookup.descartar(imei)` y limpiar sus entradas de ambos mapas.
  5. En `confirmar()` (L446+): el modelo por fila pasa de `modeloInterno` fijo a `LookupModelosImeis.modeloParaFila(modelosManuales.get(imei), modelosDetectados.get(imei), modeloInterno)`. El resto del flujo (verificar de exclusión de activos, importar, alerts diferidos) NO cambia.
  6. Cierre del diálogo con lookups en vuelo: el hilo es daemon y el callback hace no-op si la ventana ya no existe (guard con `raiz.getScene() == null` o flag `cerrado` — calcar el guard de importación en vuelo que ya tiene el diálogo).

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)** — la lógica nueva con test vive en T4; esto es pegamento UI (precedente F2a).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(lotes): alta manual con modelo detectado por fila (BD via verificar + API en cola) y edición por fila"
```

---

### Task 6: Cierre — suites, review final, smoke y merge (con el usuario)

- [ ] Suite final verde (`mvn -q test`).
- [ ] Review final de rama con paquete `review-package` desde el merge-base con main.
- [ ] Smoke con el usuario (cliente en rama):
  - Como TECNICO: "IMEIs" = historial simple (sin OK, sin GB/Color/Grado/Estado/Ubicación/Lote, sin botones, CON sufijo eSIM), drill-down intacto, CSV espejo (10 col) y CSV detalle intactos; NO ve la pestaña Inventario.
  - Como SUPERTECNICO y como ADMIN: pestaña Inventario = vista completa actual (ADMIN sin botones de importación, como hoy); CSV 19 columnas por el menú; "IMEIs" del taller también simple.
  - Alta manual: pegar lote mixto (IMEIs que la BD conoce → modelo al instante; desconocidos → "detectando…" y modelo por API; alguno irresoluble → cae al común), editar modelo de una fila a mano, confirmar e inspeccionar el lote creado. Limpieza del lote de prueba con el script de la casa (adaptar batch).
- [ ] Cliente: merge a main + push SOLO con OK. Marcar checkboxes en `Apuntes/plan-futuro.md` (secciones reubicación y lookup). Decisión del usuario: tag v0.17.0 (la tanda queda redonda salvo suppliers — su decisión).

---

## Self-review (hecho al escribir el plan)

- **Cobertura de spec**: §2.1 vista simple (T1 claves TALLER, T2 aplicación, CSV espejo sin OK) ✓ · §2.2 pestaña+roles (T3, gating en un punto, ADMIN sin botones "tal cual") ✓ · §2.3 ruta 1-vista-2-configs con bloque único (T1 descriptor + T2 consumo) ✓ · §2.4 lookup (T4 cola/precedencia TDD, T5 wiring, BD vía verificar sin endpoint nuevo, 30/min, solo desconocidos, key intocada) ✓ · §5 testing (T1/T4 puros con TDD; pegamento sin test con smoke) ✓ · §6 riesgos (INVENTARIO regresión cero = Global Constraint + smoke; controlador grande = descriptor puro; rate limit = cola con espera) ✓.
- **Consistencia de tipos**: `Vista` vive en `ConfigVistaAgrupado` (evita choque con el `Modo` interno del drill-down); `configurar(Rol, Vista)` usada igual en T2 y T3; `Resultado(imei, modelo)` y `modeloParaFila(manual, detectado, comun)` idénticos en T4 y T5.
- **Sin placeholders**: los dos únicos "verificar en código real" son lecturas obligadas del implementador (cabecera CSV actual letra a letra; criterio exacto de "con trabajos"), no huecos de diseño.
- **Nota tooling**: títulos en formato `### Task N:` — compatibles con `scripts/task-brief` (el formato "Tarea N" de la mini-fase anterior obligaba a extraer con awk).
