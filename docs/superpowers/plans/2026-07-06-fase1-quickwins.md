# Fase 1 Quick Wins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Los 4 quick wins de la Fase 1: selección persistente en la vista IMEIs, fix de la celda «reasignar» fantasma, editor de pedidos de otros componentes, y flag de chasis en asignaciones de reparación.

**Architecture:** Cliente JavaFX (`gestion-reparaciones-cliente`) + servidor Spring Boot (`gestion-reparaciones-servidor`) + MariaDB. Ítems 1-3 son solo cliente (el PUT de otros ya existe en el servidor); el ítem 4 añade la columna `ES_CHASIS` a la tabla `Reparacion` y recorre el mismo camino que el flag `URGENTE` existente (BD → DAO servidor → resumen → modelo cliente → UI).

**Tech Stack:** Java 17+, JavaFX (FXML), Spring Boot + JdbcTemplate, MariaDB, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-07-06-fase1-quickwins-design.md`

## Global Constraints

- Textos de UI en español, mismo tono que los existentes.
- Commits SIN trailer `Co-Authored-By` (preferencia del usuario).
- NO hacer merge/push/tag sin confirmación explícita del usuario.
- Comandos por Bash (Maven incluido); rutas del cliente relativas a `gestion-reparaciones-cliente/`, las del servidor a `gestion-reparaciones-servidor/`.
- El cliente no tiene tests de UI: donde no haya lógica pura, la verificación es arranque + smoke manual (documentado en cada tarea).
- El servidor NO tiene test de contexto Spring: tras tocarlo hay que validar el arranque real.
- Los números de línea son orientativos (código de 2026-07-06); localizar por el fragmento citado.

---

### Task 1: Rama + helper `GrupoImei.indiceDe` (TDD)

**Files:**
- Modify: `src/main/java/com/reparaciones/models/GrupoImei.java`
- Test: `src/test/java/com/reparaciones/models/GrupoImeiTest.java`

**Interfaces:**
- Produces: `public static int GrupoImei.indiceDe(List<?> items, String imei)` — índice del `GrupoImei` con ese IMEI en una lista mixta de items de tabla, o `-1`. Lo consume la Task 2.

- [ ] **Step 1: Crear la rama en el repo cliente**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente
git checkout -b feature/fase1-quickwins
```

- [ ] **Step 2: Escribir el test que falla**

Añadir a `GrupoImeiTest.java` (seguir el estilo de los tests existentes del fichero):

```java
@Test
void indiceDeEncuentraElGrupoPorImei() {
    GrupoImei g1 = new GrupoImei("111111111111111", java.util.List.of());
    GrupoImei g2 = new GrupoImei("222222222222222", java.util.List.of());
    java.util.List<Object> items = java.util.List.of(g1, "otraCosa", g2);

    org.junit.jupiter.api.Assertions.assertEquals(0, GrupoImei.indiceDe(items, "111111111111111"));
    org.junit.jupiter.api.Assertions.assertEquals(2, GrupoImei.indiceDe(items, "222222222222222"));
    org.junit.jupiter.api.Assertions.assertEquals(-1, GrupoImei.indiceDe(items, "999999999999999"));
    org.junit.jupiter.api.Assertions.assertEquals(-1, GrupoImei.indiceDe(items, null));
}
```

- [ ] **Step 3: Verificar que falla**

Run: `mvn -q test -Dtest=GrupoImeiTest`
Expected: error de compilación «cannot find symbol: method indiceDe».

- [ ] **Step 4: Implementar el helper**

En `GrupoImei.java`, junto al resto de métodos públicos:

```java
/**
 * Índice del grupo con ese IMEI dentro de una lista mixta de items de tabla
 * (la vista maestra mezcla tipos), o {@code -1} si no está.
 */
public static int indiceDe(java.util.List<?> items, String imei) {
    if (imei == null) return -1;
    for (int i = 0; i < items.size(); i++)
        if (items.get(i) instanceof GrupoImei g && imei.equals(g.getImei())) return i;
    return -1;
}
```

- [ ] **Step 5: Verificar que pasa**

Run: `mvn -q test -Dtest=GrupoImeiTest`
Expected: BUILD SUCCESS, todos los tests verdes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/models/GrupoImei.java src/test/java/com/reparaciones/models/GrupoImeiTest.java
git commit -m "feat(agrupado): helper GrupoImei.indiceDe para restaurar seleccion del maestro"
```

---

### Task 2: Selección persistente en la vista IMEIs

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/AgrupadoController.java` (:112, :995-1020, :1070-1073)
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` (:303-305)
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java` (:306-309)
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java` (:181-188)

**Interfaces:**
- Consumes: `GrupoImei.indiceDe(List<?>, String)` (Task 1).
- Produces: `public boolean AgrupadoController.enDetalle()` y `public void AgrupadoController.volverAlMaestro()` — los consumen los 3 controladores padre.

Contexto: `volverAGrupos()` hoy es `resetarModo(); aplicarFiltros();` y pierde selección/scroll. El botón «IMEIs» del sub-sidebar (`mostrarAgrupado`) no distingue si ya estás dentro en modo detalle.

- [ ] **Step 1: Campo + volver con restauración en `AgrupadoController`**

Tras `private String imeiDetalle = null;` (:112) añadir:

```java
private String imeiARestaurar = null;
```

Sustituir `volverAGrupos()` (:1070-1073) por:

```java
private void volverAGrupos() {
    imeiARestaurar = imeiDetalle;
    resetarModo();
    aplicarFiltros();
}

/** ¿Está el drill-down en modo detalle? (para el botón del sub-sidebar) */
public boolean enDetalle() { return modoActual == Modo.DETALLE; }

/** Vuelve al maestro restaurando selección y scroll (equivale a «← Volver»). */
public void volverAlMaestro() { if (enDetalle()) volverAGrupos(); }
```

- [ ] **Step 2: Restaurar selección al repoblar el maestro**

En `buildTablaItems()` (:995), tras la última línea `tablaItems.setAll(grupos);` (:1019) añadir `restaurarSeleccion();` y este método privado a continuación:

```java
/** Si venimos de un detalle, re-selecciona el grupo y hace scroll hasta él. */
private void restaurarSeleccion() {
    if (imeiARestaurar == null) return;
    int idx = GrupoImei.indiceDe(tablaItems, imeiARestaurar);
    imeiARestaurar = null;
    if (idx < 0) return;
    tabla.getSelectionModel().clearAndSelect(idx);
    tabla.scrollTo(Math.max(0, idx - 3));
}
```

- [ ] **Step 3: Botón «IMEIs» del sub-sidebar en los 3 roles**

`ReparacionControllerSuperTecnico` (:303-305) — sustituir:

```java
@FXML private void mostrarAgrupado() {
    if (pnlAgrupado.isVisible() && agrupadoController.enDetalle()) {
        agrupadoController.volverAlMaestro();
        return;
    }
    mostrarPanel(pnlAgrupado, btnTabAgrupado);
}
```

`ReparacionControllerTecnico` (:306-309) — sustituir:

```java
@FXML private void mostrarAgrupado() {
    if (pnlAgrupado.isVisible() && agrupadoController.enDetalle()) {
        agrupadoController.volverAlMaestro();
        return;
    }
    mostrarPanel(pnlAgrupado, btnTabAgrupado);
    agrupadoController.cargar();
}
```

`ReparacionControllerAdmin` (:181-188) — sustituir:

```java
@FXML
private void mostrarAgrupado() {
    if (pnlAgrupado.isVisible() && agrupadoController.enDetalle()) {
        agrupadoController.volverAlMaestro();
        return;
    }
    pnlAgrupado.setVisible(true);      pnlAgrupado.setManaged(true);
    pnlHistorial.setVisible(false);    pnlHistorial.setManaged(false);
    pnlAsignaciones.setVisible(false); pnlAsignaciones.setManaged(false);
    estiloSidebar(btnTabAgrupado);
    agrupadoController.cargar();
}
```

- [ ] **Step 4: Compilar y test**

Run: `mvn -q test`
Expected: BUILD SUCCESS (113+ tests verdes).

- [ ] **Step 5: Smoke manual**

Arrancar el cliente contra preproducción. Con cada rol (basta supertécnico + admin):
1. IMEIs → scroll hasta media tabla → entrar al detalle de un IMEI (icono) → «← Volver» ⇒ la fila queda seleccionada y visible (no arriba del todo).
2. Entrar al detalle → clic en «IMEIs» del sub-sidebar ⇒ mismo efecto que «← Volver».
3. Estando en el maestro, clic en «IMEIs» ⇒ recarga normal (comportamiento previo).
4. Entrar al detalle → ir a Historial → volver a IMEIs ⇒ maestro sin selección (comportamiento previo, correcto).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/com/reparaciones/controllers/
git commit -m "feat(agrupado): seleccion y scroll persistentes al volver del detalle (boton y sub-sidebar)"
```

---

### Task 3: Fix celda «reasignar» fantasma

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (:217-226)
- Modify: `src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java` (:120-125)

**Diagnóstico (verificar en el repro antes de fijar):** en ambas celdas de técnico, `updateItem` tiene `if (cb.isShowing()) return;` como PRIMERA línea. Si la celda se recicla a una fila vacía mientras el desplegable está abierto (justo lo que pasa al reasignar: `onAction` → `cargar()` → repoblado), sale antes de limpiar y el combo queda pintado en una fila vacía del fondo. Por eso solo se ve cuando la tabla no está llena.

- [ ] **Step 1: Reproducir**

En preproducción, como supertécnico: Asignaciones con pocas filas (que queden filas vacías visibles debajo) → abrir el desplegable de técnico de una fila → elegir otro técnico. Expected: aparece un combo suelto en una fila vacía del fondo. Anotar si el repro coincide con el diagnóstico; si NO se reproduce así, parar y re-diagnosticar antes de tocar código (systematic-debugging).

- [ ] **Step 2: Fix en `PendientesSuperTecnicoController`**

Sustituir el arranque de `updateItem` (:217-226):

```java
@Override
protected void updateItem(String item, boolean empty) {
    boolean vacia = empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size();
    if (cb.isShowing() && !vacia) return;   // no pisar el desplegable abierto de una fila viva
    super.updateItem(item, empty);
    if (vacia) {
        setGraphic(null);
        setText(null);
        setStyle("");
        return;
    }
```

(el resto del método queda igual).

- [ ] **Step 3: Fix gemelo en `PulidoSuperTecnicoController`**

Sustituir el arranque de `updateItem` (:120-125):

```java
@Override protected void updateItem(String item, boolean empty) {
    boolean vacia = empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size();
    if (cb.isShowing() && !vacia) return;   // no pisar el desplegable abierto de una fila viva
    super.updateItem(item, empty);
    if (vacia) {
        repMostrado = null; setGraphic(null); setText(null); setStyle(""); return;
    }
```

(el resto igual).

- [ ] **Step 4: Compilar + verificar**

Run: `mvn -q test` → BUILD SUCCESS.
Smoke: repetir el repro del Step 1 en Asignaciones y en Pulidos ⇒ ya no aparece la celda fantasma; reasignar sigue funcionando y el desplegable abierto no parpadea al refrescar filas vivas.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "fix(pendientes): celda de reasignar fantasma en filas vacias (guard isShowing no limpiaba)"
```

---

### Task 4: Editar pedidos de otros componentes

**Files:**
- Create: `src/main/resources/views/FormularioOtroPedidoEditar.fxml`
- Create: `src/main/java/com/reparaciones/controllers/FormularioOtroPedidoEditarController.java`
- Modify: `src/main/java/com/reparaciones/controllers/StockController.java` (:1235-1269 + acciones ~:1284)

**Interfaces:**
- Consumes: `CompraOtroDAO.editar(CompraOtro p, int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur)` — ya existe (PUT `/api/compras-otros/{id}`, servidor verificado: `EditarRequest` + log `EDITAR_PEDIDO_OTRO`).
- Produces: `public static void FormularioOtroPedidoEditarController.abrir(CompraOtro pedido, Runnable onGuardado)`.

- [ ] **Step 1: FXML del editor**

Crear `src/main/resources/views/FormularioOtroPedidoEditar.fxml` (calco de `FormularioCompraEditar.fxml` con Concepto editable en vez del componente de solo lectura):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>
<?import javafx.geometry.*?>

<VBox xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.reparaciones.controllers.FormularioOtroPedidoEditarController"
      styleClass="vista-container"
      stylesheets="@../styles/app.css"
      spacing="20" prefWidth="520">
    <padding><Insets top="28" right="28" bottom="28" left="28"/></padding>

    <Label fx:id="lblTitulo" text="Editar pedido" styleClass="vista-titulo"/>

    <GridPane hgap="14" vgap="12">
        <columnConstraints>
            <ColumnConstraints minWidth="130" maxWidth="130"/>
            <ColumnConstraints hgrow="ALWAYS"/>
        </columnConstraints>

        <!-- Concepto -->
        <Label text="Concepto:" GridPane.rowIndex="0" GridPane.columnIndex="0" styleClass="form-label"/>
        <TextField fx:id="txtConcepto" GridPane.rowIndex="0" GridPane.columnIndex="1"
                   styleClass="buscador" promptText="Descripción del pedido"/>

        <!-- Proveedor -->
        <Label text="Proveedor:" GridPane.rowIndex="1" GridPane.columnIndex="0" styleClass="form-label"/>
        <ComboBox fx:id="cmbProveedor" GridPane.rowIndex="1" GridPane.columnIndex="1" maxWidth="Infinity"/>

        <!-- Cantidad -->
        <Label text="Cantidad:" GridPane.rowIndex="2" GridPane.columnIndex="0" styleClass="form-label"/>
        <TextField fx:id="txtCantidad" GridPane.rowIndex="2" GridPane.columnIndex="1"
                   styleClass="buscador" promptText="Ej. 10"/>

        <!-- Urgente -->
        <Label text="Urgente:" GridPane.rowIndex="3" GridPane.columnIndex="0" styleClass="form-label"/>
        <CheckBox fx:id="chkUrgente" GridPane.rowIndex="3" GridPane.columnIndex="1"/>

        <!-- Precio + divisa -->
        <Label text="Precio unidad:" GridPane.rowIndex="4" GridPane.columnIndex="0" styleClass="form-label"/>
        <HBox GridPane.rowIndex="4" GridPane.columnIndex="1" spacing="8" alignment="CENTER_LEFT">
            <TextField fx:id="txtPrecio" styleClass="buscador" promptText="0.00" HBox.hgrow="ALWAYS"/>
            <ComboBox fx:id="cmbDivisa" prefWidth="84"/>
        </HBox>

        <!-- Total EUR (calculado) -->
        <Label text="Total EUR:" GridPane.rowIndex="5" GridPane.columnIndex="0" styleClass="form-label"/>
        <Label fx:id="lblTotalEur" text="—" GridPane.rowIndex="5" GridPane.columnIndex="1"
               style="-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#2C3B54;"/>
    </GridPane>

    <!-- Botones -->
    <HBox spacing="10" alignment="CENTER_RIGHT">
        <Button text="Cancelar"  styleClass="btn-secondary" onAction="#cancelar"/>
        <Button text="Guardar"   styleClass="btn-primary"   onAction="#confirmar"/>
    </HBox>

</VBox>
```

- [ ] **Step 2: Controlador del editor**

Crear `FormularioOtroPedidoEditarController.java` completo:

```java
package com.reparaciones.controllers;

import com.reparaciones.dao.CompraOtroDAO;
import com.reparaciones.dao.ProveedorDAO;
import com.reparaciones.dao.TipoCambioDAO;
import com.reparaciones.models.CompraOtro;
import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.Alertas;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del formulario de edición de un pedido de otros componentes.
 * <p>Permite modificar concepto, proveedor, cantidad, urgencia, precio y divisa de un
 * pedido en estado {@code pendiente}, {@code en_camino} o {@code recibido}. A diferencia
 * de los pedidos de componentes, aquí no hay ajuste de stock (no son inventario).</p>
 *
 * <p>Usa control de concurrencia optimista ({@link com.reparaciones.utils.StaleDataException})
 * y convierte el precio a EUR vía {@link com.reparaciones.dao.TipoCambioDAO}.</p>
 *
 * @role SUPERTECNICO
 */
public class FormularioOtroPedidoEditarController {

    @FXML private Label            lblTitulo;
    @FXML private TextField        txtConcepto;
    @FXML private ComboBox<Proveedor> cmbProveedor;
    @FXML private TextField        txtCantidad;
    @FXML private CheckBox         chkUrgente;
    @FXML private TextField        txtPrecio;
    @FXML private ComboBox<String> cmbDivisa;
    @FXML private Label            lblTotalEur;

    private final ProveedorDAO  proveedorDAO  = new ProveedorDAO();
    private final TipoCambioDAO tipoCambioDAO = new TipoCambioDAO();
    private final CompraOtroDAO compraOtroDAO = new CompraOtroDAO();

    private CompraOtro pedidoEditar;
    private Runnable   onGuardado;
    private volatile double tasaActual = 1.0;

    /** Abre el editor en un diálogo modal. */
    public static void abrir(CompraOtro pedido, Runnable onGuardado) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        FormularioOtroPedidoEditarController.class.getResource("/views/FormularioOtroPedidoEditar.fxml"));
                Parent root = loader.load();
                FormularioOtroPedidoEditarController ctrl = loader.getController();
                Stage stage = new Stage();
                stage.setTitle("Editar pedido #" + pedido.getIdCompraOtro());
                stage.setScene(new Scene(root));
                stage.setResizable(false);
                stage.initModality(Modality.APPLICATION_MODAL);
                ctrl.init(pedido, onGuardado);
                stage.show();
            } catch (Exception e) {
                Alertas.mostrarError("No se pudo abrir el formulario: " + e.getMessage());
            }
        });
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    public void init(CompraOtro pedido, Runnable onGuardado) {
        this.pedidoEditar = pedido;
        this.onGuardado   = onGuardado;

        lblTitulo.setText("Editar pedido #" + pedido.getIdCompraOtro());
        txtConcepto.setText(pedido.getConcepto() != null ? pedido.getConcepto() : "");

        try {
            cmbProveedor.getItems().setAll(proveedorDAO.getActivos());
        } catch (SQLException e) {
            mostrarError(e);
        }
        cmbProveedor.setVisibleRowCount(8);

        cmbDivisa.getItems().setAll(List.of("EUR", "USD"));

        txtPrecio  .textProperty().addListener((obs, o, n) -> calcularTotal());
        txtCantidad.textProperty().addListener((obs, o, n) -> calcularTotal());
        cmbDivisa.valueProperty().addListener((obs, o, n) -> { if (n != null) fetchTasaAsync(n); });

        // Rellenar valores del pedido
        cmbProveedor.getItems().stream()
                .filter(p -> p.getIdProv() == pedido.getIdProv())
                .findFirst().ifPresent(cmbProveedor::setValue);
        int cantMostrar = pedido.getCantidadRecibida() != null ? pedido.getCantidadRecibida() : pedido.getCantidad();
        txtCantidad.setText(String.valueOf(cantMostrar));
        chkUrgente.setSelected(pedido.isEsUrgente());
        txtPrecio.setText(String.format("%.2f", pedido.getPrecioUnidadPedido()));
        cmbDivisa.setValue(pedido.getDivisa());
    }

    // ─── Tasa ─────────────────────────────────────────────────────────────────

    private void fetchTasaAsync(String divisa) {
        if ("EUR".equalsIgnoreCase(divisa)) {
            tasaActual = 1.0;
            calcularTotal();
            return;
        }
        lblTotalEur.setText("Obteniendo tasa…");
        new Thread(() -> {
            try {
                double t = tipoCambioDAO.getTasa(divisa);
                Platform.runLater(() -> { tasaActual = t; calcularTotal(); });
            } catch (SQLException e) {
                Platform.runLater(() -> lblTotalEur.setText("Error al obtener tasa"));
            }
        }, "tasa-fetch").start();
    }

    private void calcularTotal() {
        String divisa = cmbDivisa.getValue() != null ? cmbDivisa.getValue() : "EUR";
        boolean esEur = "EUR".equalsIgnoreCase(divisa);
        String infoTasa = esEur ? "" : String.format("  (1 %s = %.4f €)", divisa, tasaActual);
        try {
            double precio = Double.parseDouble(txtPrecio.getText().trim().replace(",", "."));
            int    cant   = Integer.parseInt(txtCantidad.getText().trim());
            double total  = precio * tasaActual * cant;
            lblTotalEur.setText(String.format("%.2f €%s", total, infoTasa));
        } catch (NumberFormatException e) {
            lblTotalEur.setText(esEur ? "—" : infoTasa.trim());
        }
    }

    // ─── Confirmar ────────────────────────────────────────────────────────────

    @FXML private void confirmar() {
        String concepto = txtConcepto.getText().trim();
        if (concepto.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "El concepto no puede estar vacío.").showAndWait();
            return;
        }
        Proveedor prov = cmbProveedor.getValue();
        if (prov == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona un proveedor.").showAndWait();
            return;
        }
        int cant;
        try {
            cant = Integer.parseInt(txtCantidad.getText().trim());
            if (cant <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Cantidad no válida (debe ser > 0).").showAndWait();
            return;
        }
        double precio;
        try {
            precio = Double.parseDouble(txtPrecio.getText().trim().replace(",", "."));
            if (precio < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Precio no válido.").showAndWait();
            return;
        }
        String divisa = cmbDivisa.getValue();
        try {
            double tasa      = tipoCambioDAO.getTasa(divisa);
            double precioEur = precio * tasa;
            compraOtroDAO.editar(pedidoEditar, prov.getIdProv(), concepto, cant,
                    chkUrgente.isSelected(), precio, divisa, precioEur);
            onGuardado.run();
            cerrarVentana();
        } catch (com.reparaciones.utils.StaleDataException e) {
            new Alert(Alert.AlertType.WARNING,
                    "El pedido fue modificado por otro usuario. Cierra y recarga los datos.")
                    .showAndWait();
        } catch (SQLException e) {
            Alertas.mostrarError("Error al guardar: " + e.getMessage());
        }
    }

    @FXML private void cancelar() { cerrarVentana(); }

    private void cerrarVentana() {
        ((Stage) cmbProveedor.getScene().getWindow()).close();
    }

    private void mostrarError(Exception e) {
        Alertas.mostrarError(e.getMessage());
    }
}
```

Nota: si `CompraOtro` no tuviera el getter `isEsUrgente()` con ese nombre exacto, usar el que exista (comprobar en `models/CompraOtro.java` ~:131).

- [ ] **Step 3: Gesto de entrada en `StockController`**

En `construirMenuContextualOtros` (:1235-1269) añadir «Editar» en las mismas ramas que el menú de pedidos normales (`construirMenuContextual` :968):

Rama `pendiente` — sustituir por:

```java
case pendiente -> {
    MenuItem confirmar = new MenuItem("Confirmar pedido");
    MenuItem editar    = new MenuItem("Editar");
    MenuItem borrar    = new MenuItem("Borrar");
    confirmar.setOnAction(e -> confirmarPedidoOtro());
    editar   .setOnAction(e -> editarPedidoOtro());
    borrar   .setOnAction(e -> borrarPedidoOtro());
    ctx.getItems().addAll(confirmar, new SeparatorMenuItem(), editar, borrar);
}
```

Rama `en_camino` — sustituir por:

```java
case en_camino -> {
    MenuItem parcial   = new MenuItem("Recepción parcial");
    MenuItem confirmar = new MenuItem("Confirmar recibido");
    MenuItem editar    = new MenuItem("Editar");
    MenuItem cancelar  = new MenuItem("Cancelar pedido");
    parcial  .setOnAction(e -> confirmarParcialOtro());
    confirmar.setOnAction(e -> confirmarRecibidoOtro());
    editar   .setOnAction(e -> editarPedidoOtro());
    cancelar .setOnAction(e -> cancelarPedidoOtro());
    ctx.getItems().addAll(parcial, confirmar, new SeparatorMenuItem(), editar, cancelar);
}
```

Rama `recibido` — sustituir por:

```java
case recibido -> {
    MenuItem desrecibir = new MenuItem("Revertir a En camino");
    MenuItem editar     = new MenuItem("Editar");
    desrecibir.setOnAction(e -> desrecibirPedidoOtro());
    editar    .setOnAction(e -> editarPedidoOtro());
    ctx.getItems().addAll(desrecibir, new SeparatorMenuItem(), editar);
}
```

Y en la sección «Acciones otros pedidos» (~:1284), junto a `confirmarPedidoOtro()`:

```java
private void editarPedidoOtro() {
    CompraOtro sel = tablaOtros.getSelectionModel().getSelectedItem();
    if (sel == null) return;
    FormularioOtroPedidoEditarController.abrir(sel, () -> cargarOtros());
}
```

- [ ] **Step 4: Compilar + smoke**

Run: `mvn -q test` → BUILD SUCCESS.
Smoke (supertécnico, Stock → toggle Otros):
1. Pedido `pendiente`: clic derecho → Editar ⇒ modal con valores precargados; cambiar concepto y proveedor → Guardar ⇒ tabla refleja los cambios.
2. Pedido `recibido`: Editar ⇒ guarda sin tocar stock.
3. Cancelado/parcial: sin opción Editar.
4. Comprobar en Logs la entrada `EDITAR_PEDIDO_OTRO`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/views/FormularioOtroPedidoEditar.fxml src/main/java/com/reparaciones/controllers/FormularioOtroPedidoEditarController.java src/main/java/com/reparaciones/controllers/StockController.java
git commit -m "feat(otros): editar pedidos de otros componentes (concepto, proveedor, cantidad, urgente, precio, divisa)"
```

---

### Task 5: Chasis — BD + servidor

**Files (repo `gestion-reparaciones-servidor`):**
- Create: `sql/migracion-chasis.sql`
- Modify: `sql/crear_bd.sql` (:91, tabla `Reparacion`)
- Modify: `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (:97, :138, :191, :201, :208, :364-368, :577, :839, :881, :896, :1057)
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (:173-185, :217-225, :393)

**Interfaces:**
- Produces (los consume la Task 6 desde el cliente):
  - `POST /api/reparaciones/asignaciones` acepta `esChasis` (boolean) en el body.
  - `PATCH /api/reparaciones/asignaciones/{idRep}/chasis` con body `{"esChasis": true|false}` → 204, log `MARCAR_CHASIS`.
  - Los resúmenes de asignaciones incluyen `esChasis` en el JSON.

- [ ] **Step 1: Rama en el repo servidor**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout -b feature/fase1-chasis
```

- [ ] **Step 2: Migración SQL + esquema**

Crear `sql/migracion-chasis.sql`:

```sql
-- Migración: flag de chasis en asignaciones de reparación (Fase 1 quick wins, 2026-07).
-- Se fija al crear la asignación (solo tipo Reparación) y se conserva al completar;
-- la UI solo lo usa mientras la fila es asignación pendiente (A%).
ALTER TABLE Reparacion
    ADD COLUMN ES_CHASIS BOOLEAN NOT NULL DEFAULT FALSE AFTER URGENTE;
```

En `sql/crear_bd.sql`, tabla `Reparacion` (:83-97), tras la línea `URGENTE ... DEFAULT FALSE,` añadir:

```sql
    ES_CHASIS            BOOLEAN      NOT NULL DEFAULT FALSE,
```

- [ ] **Step 3: Modelo servidor**

En `servidor/model/ReparacionResumen.java`, junto al campo `urgente` y sus accesores, añadir:

```java
private boolean esChasis;
```

```java
public boolean isEsChasis()                  { return esChasis; }
public void    setEsChasis(boolean esChasis) { this.esChasis = esChasis; }
```

- [ ] **Step 4: DAO servidor**

En `servidor/dao/ReparacionDAO.java`:

a) En las listas de columnas SELECT y cláusulas GROUP BY que incluyen `r.URGENTE` (líneas :97, :191, :201, :208, :839, :881, :896, :1057), añadir `r.ES_CHASIS` justo después de `r.URGENTE` (p. ej. `... r.URGENTE, r.ES_CHASIS,` / `... r.URGENTE, r.ES_CHASIS, ta.NOMBRE, ...`). **NO tocar** el UPDATE de :578 ni el UPDATE/WHERE de :586-590.

b) En `RESUMEN_MAPPER` (:108-144), tras la línea de urgente (:138) añadir:

```java
try { rr.setEsChasis(rs.getBoolean("ES_CHASIS")); } catch (Exception ignored) {}
```

c) `insertarAsignacion` (:364-368): añadir el parámetro y la columna (solo reparación; el de glass :375 NO se toca):

```java
public String insertarAsignacion(String imei, int idTec, String comentario, boolean urgente, boolean esChasis, Integer idTecAsigna) {
```

y en su `jdbc.update`:

```java
jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA, URGENTE, ES_CHASIS) VALUES (?,?,?,NOW(),?,?,?,?)",
        idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna, urgente, esChasis);
```

(el resto del cuerpo — generación de `idRep`, return — queda igual). Si hay otros llamadores del método en el servidor, pasarles `false`.

d) Junto a `actualizarUrgente` (:577), añadir:

```java
public void actualizarChasis(String idRep, boolean esChasis) {
    jdbc.update("UPDATE Reparacion SET ES_CHASIS = ?, UPDATED_AT = UPDATED_AT WHERE ID_REP = ?", esChasis, idRep);
}
```

- [ ] **Step 5: Controller servidor**

En `servidor/controller/ReparacionController.java`:

a) Record (:393) — sustituir:

```java
private record AsignacionRequest(String imei, int idTec, String comentario, boolean urgente, boolean esChasis) {}
```

b) POST `/asignaciones` (:173-185) — pasar el flag y loguearlo:

```java
String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario(), req.urgente(), req.esChasis(), principal.getIdTec());
```

y tras `if (req.urgente()) detalleLog += ", URGENTE: true";`:

```java
if (req.esChasis()) detalleLog += ", CHASIS: true";
```

c) Nuevo PATCH junto a `actualizarUrgente` (:217-225):

```java
@PreAuthorize("hasRole('SUPERTECNICO')")
@PatchMapping("/asignaciones/{idRep}/chasis")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void actualizarChasis(@PathVariable String idRep, @RequestBody ChasisRequest req,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.actualizarChasis(idRep, req.esChasis());
    String imei = dao.getImeiByIdRep(idRep);
    logDao.insertar(principal.getIdUsu(), "MARCAR_CHASIS",
            "ID_REP: " + idRep + (imei != null ? ", IMEI: " + imei : "") + ", CHASIS: " + req.esChasis());
}
```

y junto a los records privados del final:

```java
private record ChasisRequest(boolean esChasis) {}
```

- [ ] **Step 6: Tests + arranque del servidor**

Run: `mvn -q test` → BUILD SUCCESS.
Aplicar `sql/migracion-chasis.sql` en la BD de **preproducción** (⚠️ coordinar con el usuario antes de ejecutar el ALTER). Después arrancar el servidor (`mvn -q spring-boot:run` o el método habitual) y comprobar en el log `Started ...Application` sin errores de contexto (no hay test de contexto Spring que lo cubra).

- [ ] **Step 7: Commit (repo servidor)**

```bash
git add sql/migracion-chasis.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/
git commit -m "feat(chasis): columna ES_CHASIS en Reparacion, alta en asignacion y PATCH de toggle con log"
```

---

### Task 6: Chasis — cliente: modelo, DAO, modal y guardado

**Files (repo cliente):**
- Modify: `src/main/java/com/reparaciones/models/ReparacionResumen.java` (:42, :173-174)
- Modify: `src/main/java/com/reparaciones/dao/ReparacionDAO.java` (:327-349, :409-412)
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (:104-120, :1421-1447, :1600-1629, :1659-1679, :1832-1854)

**Interfaces:**
- Consumes: endpoints de la Task 5.
- Produces: `ReparacionResumen.isEsChasis()`, `ReparacionDAO.insertarAsignacion(imei, idTec, comentario, urgente, esChasis)`, `ReparacionDAO.actualizarChasis(idRep, esChasis)` — los consume la Task 7.

- [ ] **Step 1: Modelo cliente**

En `models/ReparacionResumen.java`, tras `private boolean urgente;` (:42):

```java
private boolean esChasis;
```

y junto a los accesores de urgente (:173-174):

```java
public boolean isEsChasis()                  { return esChasis; }
public void    setEsChasis(boolean esChasis) { this.esChasis = esChasis; }
```

(Gson lo bindea por nombre de campo — debe coincidir con el JSON del servidor: `esChasis`.)

- [ ] **Step 2: DAO cliente**

En `dao/ReparacionDAO.java`, sustituir el `insertarAsignacion` de 4 args (:341-349) por la versión con flag, y delegar desde las cortas:

```java
public String insertarAsignacion(String imei, int idTec, String comentario, boolean urgente) throws SQLException {
    return insertarAsignacion(imei, idTec, comentario, urgente, false);
}

/**
 * Crea una nueva asignación pendiente y devuelve su ID.
 *
 * @param esChasis {@code true} si la asignación es una reparación de chasis
 */
public String insertarAsignacion(String imei, int idTec, String comentario, boolean urgente, boolean esChasis) throws SQLException {
    Map<String, Object> body = new HashMap<>();
    body.put("imei", imei);
    body.put("idTec", idTec);
    if (comentario != null && !comentario.isBlank()) body.put("comentario", comentario);
    body.put("urgente", urgente);
    body.put("esChasis", esChasis);
    JsonObject resp = ApiClient.post("/api/reparaciones/asignaciones", body, JsonObject.class);
    return resp != null ? resp.get("value").getAsString() : null;
}
```

Y junto a `actualizarUrgente` (:409-412):

```java
public void actualizarChasis(String idRep, boolean esChasis) throws SQLException {
    ApiClient.patch("/api/reparaciones/asignaciones/" + idRep + "/chasis",
            Map.of("esChasis", esChasis));
}
```

- [ ] **Step 3: Modal de asignación**

En `PendientesSuperTecnicoController`:

a) `EntradaAsignacion` (:104-120), tras `String comentario = "";`:

```java
boolean esChasis;                        // solo aplica a tipo Reparación; se resetea entre IMEIs
```

b) Tras el bloque de `tfComentario` (:1424-1430) declarar el check:

```java
CheckBox chkChasis = new CheckBox("Reparación de chasis");
chkChasis.setStyle("-fx-font-size: 12px; -fx-text-fill: #586376;");
```

c) En la construcción de `formBox` (:1442-1444) insertar `chkChasis` entre `tfComentario` y `accionesForm`:

```java
VBox formBox = new VBox(8, lblImeiCursoCap, lblImeiCurso, lblModelo, tfModelo,
        headerTecnicos, scrollTecnicos, lblNotaPersist, lblCliente, tfCliente,
        lblComentario, tfComentario, chkChasis, accionesForm);
```

d) En `cargarEntrada[0]` (:1600-1629), justo después de `tfComentario.setText(e.comentario);` (:1614):

```java
boolean esRep = (e.tipo == TipoTrabajo.REPARACION);
chkChasis.setVisible(esRep); chkChasis.setManaged(esRep);
chkChasis.setSelected(e.esChasis);   // entrada nueva = false → el check NO persiste entre IMEIs
```

e) En `asignarActual` (:1659-1679), tras `e.comentario = tfComentario.getText().trim();`:

```java
e.esChasis = (e.tipo == TipoTrabajo.REPARACION) && chkChasis.isSelected();
```

f) En el guardado (`btnGuardar.setOnAction`, :1849-1852), pasar el flag en la rama de reparación:

```java
if (e.tipo == TipoTrabajo.GLASS)
    glassDAO.insertarAsignacionGlass(e.imei, t.getIdTec(), com, urgente);
else
    reparacionDAO.insertarAsignacion(e.imei, t.getIdTec(), com, urgente, e.esChasis);
```

- [ ] **Step 4: Compilar + smoke**

Run: `mvn -q test` → BUILD SUCCESS.
Smoke (con el servidor de la Task 5 corriendo): abrir el modal de asignar, cola Reparación → el check aparece bajo el comentario; marcarlo en un IMEI, Asignar → el siguiente IMEI sale desmarcado; cola Glass → el check no está; Guardar → verificar en el log `CREAR_ASIGNACION ... CHASIS: true` y en la BD `ES_CHASIS=1` en la fila `A…`.

- [ ] **Step 5: Commit (repo cliente)**

```bash
git add src/main/java/com/reparaciones/models/ReparacionResumen.java src/main/java/com/reparaciones/dao/ReparacionDAO.java src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(chasis): check en el modal de asignacion (solo reparacion, sin persistir entre IMEIs)"
```

---

### Task 7: Chasis — marcas visuales, columna Tipo en Mis pendientes y menú contextual

**Files (repo cliente):**
- Modify: `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (:152-163 badge, :344-405 menú)
- Modify: `src/main/resources/views/PendientesTecnicoView.fxml` (:25-34)
- Modify: `src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java` (:33-43, :62-110)

**Interfaces:**
- Consumes: `ReparacionResumen.isEsChasis()`, `ReparacionDAO.actualizarChasis(...)` (Task 6), `TipoTrabajo` (existente).

- [ ] **Step 1: Marca bajo la píldora en Asignaciones (supertécnico)**

En `PendientesSuperTecnicoController`, sustituir el cell factory de `cTipo` (:152-163) por:

```java
cTipo.setCellFactory(col -> new TableCell<>() {
    private final Label badge = new Label();
    private final Label lblChasis = new Label("Chasis");
    private final javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(1, badge, lblChasis);
    {
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        lblChasis.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A94A6;");
    }
    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
        ReparacionResumen rep = getTableView().getItems().get(getIndex());
        TipoTrabajo tipo = tipoDe(rep.getIdRep());
        badge.setText(tipo.etiqueta());
        badge.setStyle(tipo.estiloBadge());
        boolean chasis = rep.isEsChasis() && tipo == TipoTrabajo.REPARACION;
        lblChasis.setVisible(chasis); lblChasis.setManaged(chasis);
        setGraphic(box);
    }
});
```

- [ ] **Step 2: Columna Tipo en Mis pendientes (vista compartida técnico/supertécnico)**

En `PendientesTecnicoView.fxml`, tras la columna `cId` (:25) añadir:

```xml
<TableColumn fx:id="cTipo"   text="Tipo"             prefWidth="90"/>
```

En `PendientesTecnicoController`: añadir el campo junto a los demás (:33-43):

```java
@FXML private TableColumn<ReparacionResumen, Void>   cTipo;
```

el import junto a los existentes:

```java
import com.reparaciones.utils.TipoTrabajo;
```

y en `initialize()`, tras el `cId.setCellValueFactory(...)` (:67-68), el mismo cell factory con píldora + marca:

```java
cTipo.setCellFactory(col -> new TableCell<>() {
    private final Label badge = new Label();
    private final Label lblChasis = new Label("Chasis");
    private final javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(1, badge, lblChasis);
    {
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        lblChasis.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A94A6;");
    }
    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
        ReparacionResumen rep = getTableView().getItems().get(getIndex());
        TipoTrabajo tipo = TipoTrabajo.desde(rep.getIdRep());
        badge.setText(tipo.etiqueta());
        badge.setStyle(tipo.estiloBadge());
        boolean chasis = rep.isEsChasis() && tipo == TipoTrabajo.REPARACION;
        lblChasis.setVisible(chasis); lblChasis.setManaged(chasis);
        setGraphic(box);
    }
});
```

Nota: la vista se instancia dos veces (toggle Reparación/Glass), así que la píldora será constante por instancia — es lo acordado con el usuario (paridad visual con la tabla del supertécnico).

- [ ] **Step 3: Menú contextual «Marcar/Quitar chasis» (asignaciones del supertécnico)**

En el `rowFactory` de `tablaPendientes` (`PendientesSuperTecnicoController`), junto a `toggleUrgente` (:362-371) añadir:

```java
MenuItem toggleChasis = new MenuItem();
toggleChasis.setOnAction(e -> {
    if (getItem() == null) return;
    ReparacionResumen rep = getItem();
    boolean nuevoEstado = !rep.isEsChasis();
    try {
        reparacionDAO.actualizarChasis(rep.getIdRep(), nuevoEstado);
        cargar();
    } catch (java.sql.SQLException ex) { mostrarError(ex); }
});
```

En `menu.setOnShowing` (:394-403), junto a las líneas de `toggleUrgente` añadir:

```java
boolean esRep = getItem() != null && tipoDe(getItem().getIdRep()) == TipoTrabajo.REPARACION;
toggleChasis.setVisible(!soloLectura && esRep);
if (getItem() != null)
    toggleChasis.setText(getItem().isEsChasis() ? "Quitar chasis" : "Marcar chasis");
```

Y registrar el item tras `menu.getItems().add(toggleUrgente);` (:405):

```java
menu.getItems().add(toggleChasis);
```

Nota (desviación menor de la spec, comentar al usuario en el resumen): la spec decía «SUPERTECNICO/ADMIN», pero en esta tabla el admin es solo-lectura (`soloLectura`) y todas las acciones de escritura, urgente incluido, se le ocultan. El toggle de chasis sigue esa misma regla por paridad.

- [ ] **Step 4: Compilar + smoke**

Run: `mvn -q test` → BUILD SUCCESS.
Smoke:
1. Supertécnico → Asignaciones: fila marcada al asignar muestra «Chasis» en gris bajo la píldora azul; clic derecho → «Quitar chasis» ⇒ marca desaparece; «Marcar chasis» la devuelve; en filas Glass/Pulido no aparece la opción.
2. Técnico → Mis pendientes: columna Tipo nueva con píldora; la asignación de chasis muestra la marca; el técnico NO tiene el toggle.
3. Admin → Asignaciones: ve la marca, sin toggle (solo lectura).
4. Logs: entrada `MARCAR_CHASIS` con ID_REP e IMEI.
5. Completar la reparación de una asignación con chasis ⇒ en Historial/IMEIs no aparece rastro del flag (correcto según spec).

- [ ] **Step 5: Commit (repo cliente)**

```bash
git add src/main/java/com/reparaciones/controllers/ src/main/resources/views/PendientesTecnicoView.fxml
git commit -m "feat(chasis): marca bajo la pildora en asignaciones y mis pendientes (columna Tipo nueva) + toggle por menu contextual"
```

---

### Task 8: Verificación final de la fase

- [ ] **Step 1: Suites completas**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente && mvn -q test
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q test
```

Expected: BUILD SUCCESS en ambos.

- [ ] **Step 2: Smoke transversal por roles**

Repasar en preproducción los smokes de las Tasks 2, 3, 4, 6 y 7 con los 3 roles (supertécnico, técnico, admin). Verificar además que el arranque del servidor sigue limpio.

- [ ] **Step 3: Resumen al usuario y esperar OK**

Reportar resultados (tests + smoke + desviación del toggle admin de la Task 7). **NO hacer merge ni push sin confirmación del usuario.** Tras su OK: merge `--no-ff` de `feature/fase1-quickwins` a `main` (cliente) y de `feature/fase1-chasis` a `main` (servidor), y actualizar la referencia del submódulo si aplica.
