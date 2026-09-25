# Filtros en la ventana de Log de actividad — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir un buscador de texto libre y un filtro de fecha (Desde/Hasta) a la ventana de Log de actividad (solo ADMIN), filtrando en memoria sobre los logs ya cargados, sin tocar el servidor.

**Architecture:** Se sigue el patrón ya existente en `StockController` (filtros de "Pedidos"): un `ObservableList` maestro + un `FilteredList` envolviéndolo enganchado a la `TableView`, con listeners en los controles de filtro que recalculan el predicado en vivo. La lógica de coincidencia del predicado se extrae a un método estático puro y testeable (`coincideFiltro`) para poder cubrirla con JUnit sin necesitar el toolkit de JavaFX.

**Tech Stack:** Java 17, JavaFX (FXML), Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-06-16-filtros-log-actividad-design.md`

---

### Task 1: Constructor en `LogActividad` para poder construir instancias en tests

**Files:**
- Modify: `src/main/java/com/reparaciones/models/LogActividad.java`

- [ ] **Step 1: Añadir un constructor con todos los campos**

Edita el archivo para que quede así (se mantiene el constructor vacío que usa Gson para deserializar la respuesta del servidor; se añade uno con argumentos para poder construir instancias en tests):

```java
package com.reparaciones.models;

import java.time.LocalDateTime;

public class LogActividad {

    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;

    public LogActividad() {}

    public LogActividad(int idLog, LocalDateTime fecha, String nombreUsuario,
                         String accion, String detalle) {
        this.idLog = idLog;
        this.fecha = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion = accion;
        this.detalle = detalle;
    }

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
}
```

- [ ] **Step 2: Compilar para verificar que no hay errores**

Run: `mvn -q -pl . compile`
Expected: termina sin errores (sin salida es éxito en modo `-q`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/models/LogActividad.java
git commit -m "feat: añadir constructor con argumentos a LogActividad para tests"
```

---

### Task 2: TDD de la lógica de filtrado (`coincideFiltro`)

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/LogController.java`
- Create: `src/test/java/com/reparaciones/controllers/LogControllerTest.java`

- [ ] **Step 1: Escribir los tests (deben fallar porque `coincideFiltro` no existe todavía)**

Crea el archivo `src/test/java/com/reparaciones/controllers/LogControllerTest.java`:

```java
package com.reparaciones.controllers;

import com.reparaciones.models.LogActividad;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LogControllerTest {

    private LogActividad log(LocalDateTime fecha, String usuario, String accion, String detalle) {
        return new LogActividad(1, fecha, usuario, accion, detalle);
    }

    private LogActividad logDeEjemplo() {
        return log(LocalDateTime.of(2026, 6, 10, 12, 30),
                "jperez", "CREAR_REPARACION", "ID_REP: 5, IMEI: 123456789012345, ID_TEC: 2");
    }

    // --- texto ---

    @Test
    void coincideFiltro_textoVacio_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "", null, null));
    }

    @Test
    void coincideFiltro_textoNulo_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnUsuario_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "JPEREZ", null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnAccion_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "crear_reparacion", null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnDetalle_porImei_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "123456789012345", null, null));
    }

    @Test
    void coincideFiltro_textoNoCoincideEnNingunCampo_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "no_existe", null, null));
    }

    // --- fecha ---

    @Test
    void coincideFiltro_fechaDesdeAnteriorALaDelLog_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, LocalDate.of(2026, 6, 1), null));
    }

    @Test
    void coincideFiltro_fechaDesdePosteriorALaDelLog_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), null, LocalDate.of(2026, 6, 11), null));
    }

    @Test
    void coincideFiltro_fechaHastaPosteriorALaDelLog_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, LocalDate.of(2026, 6, 30)));
    }

    @Test
    void coincideFiltro_fechaHastaAnteriorALaDelLog_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), null, null, LocalDate.of(2026, 6, 9)));
    }

    @Test
    void coincideFiltro_fechaMismoDiaDesdeYHasta_devuelveTrue() {
        LocalDate dia = LocalDate.of(2026, 6, 10);
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, dia, dia));
    }

    // --- combinación ---

    @Test
    void coincideFiltro_textoCoincideYFechaFueraDeRango_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "jperez",
                null, LocalDate.of(2026, 6, 9)));
    }

    @Test
    void coincideFiltro_textoNoCoincideYFechaDentroDeRango_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "no_existe",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }
}
```

- [ ] **Step 2: Ejecutar los tests y comprobar que fallan al compilar**

Run: `mvn -q -Dtest=LogControllerTest test`
Expected: FALLO de compilación, `cannot find symbol: method coincideFiltro` (el método no existe todavía en `LogController`).

- [ ] **Step 3: Implementar `coincideFiltro` en `LogController`**

Abre `src/main/java/com/reparaciones/controllers/LogController.java` y añade los imports `java.time.LocalDate` junto al `DateTimeFormatter` ya existente, y el método estático al final de la clase (antes de la última llave de cierre):

```java
import java.time.LocalDate;
```

```java
    static boolean coincideFiltro(LogActividad log, String texto, LocalDate desde, LocalDate hasta) {
        boolean coincideTexto = texto == null || texto.isBlank()
                || contiene(log.getNombreUsuario(), texto)
                || contiene(log.getAccion(), texto)
                || contiene(log.getDetalle(), texto);

        LocalDate fecha = log.getFecha() != null ? log.getFecha().toLocalDate() : null;
        boolean coincideDesde = desde == null || fecha == null || !fecha.isBefore(desde);
        boolean coincideHasta = hasta == null || fecha == null || !fecha.isAfter(hasta);

        return coincideTexto && coincideDesde && coincideHasta;
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto.toLowerCase().trim());
    }
```

- [ ] **Step 4: Ejecutar los tests y comprobar que pasan**

Run: `mvn -q -Dtest=LogControllerTest test`
Expected: PASA, sin salida en modo `-q` (los 13 tests pasan).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/LogController.java src/test/java/com/reparaciones/controllers/LogControllerTest.java
git commit -m "feat: lógica de filtrado de logs (coincideFiltro) con tests"
```

---

### Task 3: Controles de filtro en `LogView.fxml`

**Files:**
- Modify: `src/main/resources/views/LogView.fxml`

- [ ] **Step 1: Añadir la fila de filtros entre el título y la tabla**

En el `VBox` del bloque `<center>`, justo antes del `TableView`, añade:

```xml
            <HBox alignment="CENTER_LEFT" spacing="10">
                <TextField fx:id="txtBuscadorLogs" promptText="Buscar..."
                           styleClass="buscador" prefWidth="220"/>
                <Label text="Desde:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
                <DatePicker fx:id="dpLogsDesde" prefWidth="130"/>
                <Label text="Hasta:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
                <DatePicker fx:id="dpLogsHasta" prefWidth="130"/>
                <Button fx:id="btnLimpiarFiltrosLogs" text="Limpiar filtros"
                        styleClass="btn-secondary" onAction="#limpiarFiltrosLogs"/>
            </HBox>
```

El bloque `<center>` completo queda así:

```xml
    <center>
        <VBox spacing="10" style="-fx-background-color: #EFEFEF;">
            <padding><Insets top="8" right="48" bottom="0" left="48"/></padding>
            <HBox alignment="CENTER_LEFT" spacing="10">
                <TextField fx:id="txtBuscadorLogs" promptText="Buscar..."
                           styleClass="buscador" prefWidth="220"/>
                <Label text="Desde:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
                <DatePicker fx:id="dpLogsDesde" prefWidth="130"/>
                <Label text="Hasta:" style="-fx-font-size:13px; -fx-text-fill:#2C3B54;"/>
                <DatePicker fx:id="dpLogsHasta" prefWidth="130"/>
                <Button fx:id="btnLimpiarFiltrosLogs" text="Limpiar filtros"
                        styleClass="btn-secondary" onAction="#limpiarFiltrosLogs"/>
            </HBox>
            <TableView fx:id="tablaLogs" VBox.vgrow="ALWAYS" styleClass="tabla-reparaciones"
                       stylesheets="@../styles/app.css">
                <columns>
                    <TableColumn fx:id="colFecha"   text="Fecha"    prefWidth="150"/>
                    <TableColumn fx:id="colUsuario" text="Usuario"  prefWidth="80"/>
                    <TableColumn fx:id="colAccion"  text="Acción"   prefWidth="180"/>
                    <TableColumn fx:id="colDetalle" text="Detalle"  prefWidth="400"/>
                </columns>
                <columnResizePolicy>
                    <TableView fx:constant="CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN"/>
                </columnResizePolicy>
            </TableView>
        </VBox>
    </center>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/views/LogView.fxml
git commit -m "feat: controles de filtro en LogView.fxml"
```

(El `fx:id` `btnLimpiarFiltrosLogs` y el `onAction="#limpiarFiltrosLogs"` no resolverán todavía contra el controller — eso se conecta en la Task 4. FXML no se compila ni se testea por separado en este proyecto; se valida junto con el controller en la Task 4.)

---

### Task 4: Conectar el `FilteredList` y los controles en `LogController`

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/LogController.java`

- [ ] **Step 1: Sustituir el contenido del controller**

Reemplaza todo el archivo `src/main/java/com/reparaciones/controllers/LogController.java` por:

```java
package com.reparaciones.controllers;

import com.reparaciones.dao.LogDAO;
import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.Alertas;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogController {

    @FXML private TableView<LogActividad>              tablaLogs;
    @FXML private TableColumn<LogActividad, String>    colFecha;
    @FXML private TableColumn<LogActividad, String>    colUsuario;
    @FXML private TableColumn<LogActividad, String>    colAccion;
    @FXML private TableColumn<LogActividad, String>    colDetalle;
    @FXML private TextField                            txtBuscadorLogs;
    @FXML private DatePicker                            dpLogsDesde;
    @FXML private DatePicker                            dpLogsHasta;

    private final LogDAO logDAO = new LogDAO();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObservableList<LogActividad> logsMaster = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : ""));
        colUsuario.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        colAccion.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getAccion()));
        colDetalle.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getDetalle()));

        tablaLogs.getColumns().forEach(c -> c.setReorderable(false));

        FilteredList<LogActividad> logsFiltrados = new FilteredList<>(logsMaster, l -> true);
        Runnable aplicarFiltrosLogs = () -> logsFiltrados.setPredicate(log ->
                coincideFiltro(log, txtBuscadorLogs.getText(), dpLogsDesde.getValue(), dpLogsHasta.getValue()));

        txtBuscadorLogs.textProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsHasta.valueProperty().addListener((obs, o, n) -> aplicarFiltrosLogs.run());
        dpLogsDesde.getEditor().setDisable(true);
        dpLogsDesde.getEditor().setOpacity(1.0);
        dpLogsHasta.getEditor().setDisable(true);
        dpLogsHasta.getEditor().setOpacity(1.0);

        tablaLogs.setItems(logsFiltrados);

        cargarLogs();
    }

    @FXML
    private void cargarLogs() {
        try {
            List<LogActividad> logs = logDAO.getAll();
            logsMaster.setAll(logs);
        } catch (SQLException e) {
            Alertas.mostrarError("Error al cargar los logs: " + e.getMessage());
        }
    }

    @FXML
    private void limpiarFiltrosLogs() {
        txtBuscadorLogs.clear();
        dpLogsDesde.setValue(null);
        dpLogsHasta.setValue(null);
    }

    @FXML
    private void cerrar() {
        ((Stage) tablaLogs.getScene().getWindow()).close();
    }

    static boolean coincideFiltro(LogActividad log, String texto, LocalDate desde, LocalDate hasta) {
        boolean coincideTexto = texto == null || texto.isBlank()
                || contiene(log.getNombreUsuario(), texto)
                || contiene(log.getAccion(), texto)
                || contiene(log.getDetalle(), texto);

        LocalDate fecha = log.getFecha() != null ? log.getFecha().toLocalDate() : null;
        boolean coincideDesde = desde == null || fecha == null || !fecha.isBefore(desde);
        boolean coincideHasta = hasta == null || fecha == null || !fecha.isAfter(hasta);

        return coincideTexto && coincideDesde && coincideHasta;
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toLowerCase().contains(texto.toLowerCase().trim());
    }
}
```

Nota: `cargarLogs()` ya no crea una lista nueva — hace `logsMaster.setAll(logs)`, así el `FilteredList` ya enganchado a la tabla se mantiene sincronizado y los filtros activos no se pierden al pulsar "Actualizar". La llamada a `cargarLogs()` al final de `initialize()` reproduce el mismo comportamiento que ya existía (la carga inicial al abrir la ventana no cambia).

- [ ] **Step 2: Ejecutar los tests existentes para comprobar que nada se rompe**

Run: `mvn -q -Dtest=LogControllerTest test`
Expected: PASA (los 13 tests siguen verdes; `coincideFiltro` no cambió de comportamiento, solo de ubicación final en el archivo).

- [ ] **Step 3: Compilar el módulo completo**

Run: `mvn -q compile`
Expected: termina sin errores.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "feat: enganchar FilteredList y controles de filtro en LogController"
```

---

### Task 5: Verificación manual end-to-end

**Files:** ninguno (solo verificación, no hay TestFX configurado en este proyecto para automatizar interacción de UI).

- [ ] **Step 1: Arrancar la aplicación cliente**

Run: `mvn -q javafx:run` (o el comando habitual de arranque del proyecto)

- [ ] **Step 2: Iniciar sesión como ADMIN y abrir la ventana de Log de actividad**

Comprobar visualmente:
- Aparecen los controles: campo "Buscar...", "Desde:", "Hasta:", botón "Limpiar filtros".
- La tabla muestra los logs igual que antes.

- [ ] **Step 3: Probar el filtro de texto**

Escribir un IMEI conocido (de un log de tipo `CREAR_REPARACION`) en el buscador y comprobar que la tabla se reduce en vivo a las filas que lo contienen en Usuario, Acción o Detalle. Borrar el texto y comprobar que vuelven a aparecer todas las filas.

- [ ] **Step 4: Probar el filtro de fecha**

Seleccionar una fecha "Desde" posterior a la fecha de algún log y comprobar que esa fila desaparece. Seleccionar una fecha "Hasta" anterior y comprobar lo mismo. Combinar con el texto y comprobar que el resultado es la intersección (AND).

- [ ] **Step 5: Probar "Limpiar filtros" y "Actualizar"**

Pulsar "Limpiar filtros" y comprobar que el texto y las fechas se vacían y vuelven a verse todas las filas. Aplicar un filtro, pulsar "Actualizar" y comprobar que el filtro sigue activo tras la recarga.

- [ ] **Step 6: Reportar resultado**

Si todo lo anterior se comporta como se describe, la feature está verificada manualmente. Si algo falla, anotar el paso exacto y el comportamiento observado antes de continuar.
