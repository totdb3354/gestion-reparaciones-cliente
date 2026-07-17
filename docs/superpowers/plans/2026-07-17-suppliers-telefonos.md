# Suppliers de teléfonos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separar proveedores de componentes de suppliers de teléfonos con un campo `TIPO` en `Proveedor` (opción A): combos filtrados por contexto, creación inline de suppliers en importador/alta manual, y panel "Suppliers" en una sidebar nueva de la pestaña Inventario — según la spec `docs/superpowers/specs/2026-07-17-suppliers-telefonos-design.md`.

**Architecture:** BD: `ALTER Proveedor ADD TIPO ENUM('COMPONENTES','TELEFONOS')` + migración auto-por-uso con vista previa. Servidor: `tipo` en modelo/JSON, `?tipo=` opcional en los dos GET, alta con `divisa`/`tipo` opcionales (defaults EUR/COMPONENTES → compat total con cliente viejo). Cliente: overloads con tipo en `ProveedorDAO`, contextos de piezas piden `COMPONENTES` y los de lotes `TELEFONOS`; la pestaña Inventario pasa a un host `InventarioView` con sidebar (calco del patrón de Reparaciones) con el agrupado intacto + panel Suppliers; diálogo compartido `NuevoSupplierDialog` para el alta explícita (panel, importador inline, alta manual).

**Tech Stack:** Java 17 · JavaFX 21 · Spring Boot (servidor) · JUnit 5 + Mockito (servidor) · MariaDB.

## Global Constraints

- **Dos repos**: raíz `c:\Users\info\Documents\ProgramaReparaciones` (cliente en `gestion-reparaciones-cliente/`) y servidor `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor` (submódulo, repo git propio). Ramas: **`feature/suppliers`** en cada repo (la de raíz YA existe con este plan; la del servidor la crea T1 desde su main local `81c0426+`).
- **Commits SIN `Co-Authored-By`**; mensajes `feat/fix(ámbito): descripción en español`.
- **Merge, push y tags: SOLO con OK explícito del usuario.** Orden de despliegue: ALTER (usuario, vista previa primero) → servidor → cliente.
- **Comandos por Bash.** Suites: cliente `cd gestion-reparaciones-cliente && mvn -q test`; servidor `cd gestion-reparaciones-servidor && mvn -q test`.
- **Regresión cero** en: el agrupado en modo INVENTARIO (columnas/filtros/botones/CSV 19), la vista "IMEIs" TALLER, y el panel Proveedores de Stock (mismas pantallas, ahora solo COMPONENTES).
- Valores de tipo: strings **`COMPONENTES`** y **`TELEFONOS`** — constantes `TIPO_COMPONENTES`/`TIPO_TELEFONOS` en cada `ProveedorDAO` (servidor y cliente). Sin `tipo` ⇒ el servidor devuelve/crea como hoy (compat cliente viejo: alta default `COMPONENTES`, GETs sin filtrar).
- Etiqueta **"Supplier"** SOLO en los diálogos de lotes y el panel nuevo. La columna "Proveedor" del inventario agrupado y su CSV **no se tocan**.
- La API key de imeicheck y credenciales viven solo en la VM — nada de eso aparece aquí.

## Contexto imprescindible (leído del código real)

- **Servidor** — `model/Proveedor.java`: inmutable, constructor 5 args `(int idProv, String nombre, boolean activo, String divisa, String comentario)` (L12-18), solo getters. `dao/ProveedorDAO.java`: `MAPPER` L15-21 lee `ID_PROV, NOMBRE, ACTIVO, DIVISA, COMENTARIO`; `getAll()` L27, `getActivos()` L33, `insertar(String nombre)` L47 = `INSERT INTO Proveedor (NOMBRE, ACTIVO, DIVISA) VALUES (?, 1, 'EUR')` (divisa/activo literales), `editar` L55 sí toca divisa+comentario. `controller/ProveedorController.java` (`/api/proveedores`): GET `""` (roles SUPERTECNICO/ADMIN/TECNICO, L22-24), GET `/activos` (SUPERTECNICO, L28-30), POST `""` body `NombreRequest(String nombre)` (L40-45), PATCH `/{id}/activo`, PUT `/{id}` `EditarRequest(nombre,divisa,comentario)`, DELETE. Seguridad por `@PreAuthorize` (no hay reglas de ruta en `SecurityConfig`). **No existen tests de proveedores**; patrón de test de la casa = JUnit 5 + Mockito puro (ej. `UrgenteAutomaticoJobTest`: `mock(...)`, `verify(...)`), sin MockMvc ni H2.
- **Servidor SQL** — `sql/crear_bd.sql` L130-137 `CREATE TABLE Proveedor` (NOMBRE, ACTIVO, DIVISA default 'EUR', COMENTARIO). Molde de script con vista previa: `sql/renombrar-chasis-colores.sql` (banner `═`, `USE gestion_reparaciones;`, bloque `-- Vista previa (no modifica nada)`, bloque de cambios, bloque `-- Verificación`).
- **Cliente** — `models/Proveedor.java`: POJO Gson por reflexión (campos `idProv,nombre,activo,divisa,comentario`), setters presentes, `toString()` = nombre (los combos pintan eso). `dao/ProveedorDAO.java` (40 líneas, `ApiClient` estático): `getAll()`→GET `/api/proveedores`, `getActivos()`→GET `/api/proveedores/activos`, `insertar(String)`→POST body `Map.of("nombre",...)`, `editar`, `setActivo`, `borrar`, `tienePedidos`; todos `throws SQLException`.
- **Cliente, call-sites de `getActivos()`**: compras síncronas en hilo FX — `FormularioCompraController` L82/L571/L602/L652, `FormularioCompraEditarController` L57, `FormularioOtroPedidoController` L115, `FormularioOtroPedidoEditarController` L82. Lotes en background — `ImportadorLoteDialog` L100 (hilo `importador-lote-carga`), `AltaManualLoteDialog` L77 (hilo `alta-manual-lote-carga`). `StockController.cargarProveedores()` L1719 usa `getAll()`.
- **Cliente, Stock** — panel proveedores en `views/StockView.fxml` L173-192 (`pnlProveedores`, `menuFiltroProveedores`, `btnNuevoProveedor`, `tablaProveedores` + columnas `cpvNombre/cpvDivisa/cpvActivo/cpvComentario`, `lblUltimaActProveedores`). `StockController`: `configurarTablaProveedores()` L1643-1715 (menú contextual solo super: activar/editar/borrar con guard `tienePedidos`), `nuevoProveedor()` L1773-1785 (`TextInputDialog` solo nombre → `insertar`), `editarProveedor()` L1787-1868 (Stage modal a mano: nombre + ComboBox divisa EUR/USD + comentario → `editar(...)` L1856), `borrarProveedor()` L1879-1892 (usa `ConfirmDialog.mostrar` L1882).
- **Cliente, importador** — `ImportadorLoteDialog`: combo por lote en `crearPaneLote` L444-490 (`comboProveedor` L453, preselección L445-451, guarda en `seleccionProveedorPorLote` L468), aviso `Label "Elige proveedor"` L458-461/L469-470, `buscarProveedorPorNombre` L492-498 (match trim+equalsIgnoreCase), `confirmar()` L663-713 usa `seleccionProveedorPorLote` L667 y `actualizarBotonImportar()` L648-658 deshabilita si falta proveedor.
- **Cliente, alta manual** — `AltaManualLoteDialog`: `comboProveedor` L117/L157-168 (el `setOnAction` L162-167 actualiza `lblDivisaPrecio` con `prov.getDivisa()`), `confirmar()` L602-613 lee `proveedor.getDivisa()`/`getIdProv()`.
- **Cliente, patrón host** — `ReparacionViewSuperTecnico.fxml`: `BorderPane` con `<left><VBox styleClass="stock-sidebar">` L25-49 (botones `btnTabX` con `onAction`), `<center>` StackPane con paneles `VBox visible/managed` (agrupado: `pnlAgrupado` L120 + `<fx:include source="AgrupadoView.fxml" fx:id="agrupado"/>` L121 → controller inyectado `agrupadoController`). `ReparacionControllerSuperTecnico`: `mostrarPanel(VBox, Button)` L315-345 (al salir del agrupado `agrupadoController.resetarModo()` L317-318; al mostrarlo `agrupadoController.cargar()` L339), delegado CSV L1174 `if (pnlAgrupado.isVisible()) { agrupadoController.exportarCSV(owner); return; }`.
- **Cliente, MainController (wiring actual a mover)** — `mostrarInventario()` L780-784 carga `/views/AgrupadoView.fxml` directo; bloque miss de caché `instanceof AgrupadoController` L989-996 (configurar+cargar); asignación `controladorActivo` L1001-1002; revisita `controladorActivo.recargar()` L1017; `descargarCSV()` L844-848 vía `Exportable`; visibilidad `btnInventario` L118-121. Interfaces: `utils/Exportable` (`void exportarCSV(Stage)`), `utils/Recargable` (`void recargar()`, `void detenerPolling()`).
- **Cliente, confirmaciones** — `utils/ConfirmDialog.mostrar(String titulo, String descripcion, String textoAccion, Runnable onConfirm)` (no existe `Alertas.confirmar`; `Alertas.mostrarError(String)` sí).
- **`AgrupadoView.fxml`** raíz: `<VBox fx:id="raiz" ...>`.

## Estructura de ficheros

**Servidor — Create:** `sql/migracion-suppliers.sql`, `src/test/java/com/reparaciones/servidor/dao/ProveedorDAOTest.java`.
**Servidor — Modify:** `sql/crear_bd.sql`, `model/Proveedor.java`, `dao/ProveedorDAO.java`, `controller/ProveedorController.java`.
**Cliente — Create:** `views/InventarioView.fxml`, `controllers/InventarioController.java`, `controllers/NuevoSupplierDialog.java`, `controllers/EditarProveedorDialog.java` (extraído de Stock).
**Cliente — Modify:** `models/Proveedor.java`, `dao/ProveedorDAO.java`, `controllers/StockController.java`, los 4 formularios de compras, `controllers/ImportadorLoteDialog.java`, `controllers/AltaManualLoteDialog.java`, `controllers/MainController.java`.

---

### Task 1: Servidor — script `migracion-suppliers.sql` + `crear_bd.sql` en sync

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-suppliers.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (L130-137)

**Interfaces:**
- Produces: columna `Proveedor.TIPO ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES'` (T2 la lee; el usuario aplica el script a mano en la VM en el cierre).

- [ ] **Paso 1: Crear la rama del servidor** — en el repo servidor: `git checkout main && git checkout -b feature/suppliers` (main local debe estar en `81c0426` o posterior).

- [ ] **Paso 2: Escribir `sql/migracion-suppliers.sql`** (molde de `renombrar-chasis-colores.sql`):

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-suppliers.sql — suppliers de teléfonos: TIPO en Proveedor (spec 2026-07-17)
-- 1) Ejecutar los SELECT de vista previa y revisar la clasificación propuesta.
-- 2) Si (b) sale vacío y (a)/(c) cuadran, ejecutar el ALTER y el UPDATE. NO idempotente.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada) ─────────────────────────────────────────────
-- (a) Proveedores que pasarán a TELEFONOS (referenciados por algún lote)
SELECT p.ID_PROV, p.NOMBRE, COUNT(l.ID_LOTE) AS LOTES
  FROM Proveedor p
  JOIN Lote l ON l.ID_PROV = p.ID_PROV
 GROUP BY p.ID_PROV, p.NOMBRE;

-- (b) CONFLICTOS: usados en lotes Y en compras — DEBE SALIR VACÍO; si no, PARAR
--     y decidir a mano fila a fila con el usuario antes de ejecutar el UPDATE
SELECT p.ID_PROV, p.NOMBRE,
       (SELECT COUNT(*) FROM Lote l  WHERE l.ID_PROV  = p.ID_PROV) AS LOTES,
       (SELECT COUNT(*) FROM Compra_componente cc WHERE cc.ID_PROV = p.ID_PROV) AS COMPRAS_COMPONENTE,
       (SELECT COUNT(*) FROM Compra_otro co       WHERE co.ID_PROV = p.ID_PROV) AS COMPRAS_OTRO
  FROM Proveedor p
 WHERE EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV)
   AND (EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_PROV = p.ID_PROV)
     OR EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_PROV = p.ID_PROV));

-- (c) Recuento resultante propuesto
SELECT CASE WHEN EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV)
            THEN 'TELEFONOS' ELSE 'COMPONENTES' END AS TIPO_PROPUESTO,
       COUNT(*) AS N
  FROM Proveedor p
 GROUP BY TIPO_PROPUESTO;

-- ALTER + migración (ejecutar tras validar la vista previa) ───────────────────
ALTER TABLE Proveedor
    ADD COLUMN TIPO ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES';

UPDATE Proveedor p
   SET p.TIPO = 'TELEFONOS'
 WHERE EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV);

-- Verificación: debe cuadrar con la vista previa (c) ──────────────────────────
SELECT TIPO, COUNT(*) AS N FROM Proveedor GROUP BY TIPO;
```

- [ ] **Paso 3: `crear_bd.sql` en sync** — en el `CREATE TABLE Proveedor` (L130-137), añadir tras la línea de `DIVISA`:

```sql
    TIPO       ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES',
```

- [ ] **Paso 4: Commit (repo SERVIDOR)**

```bash
git add sql && git commit -m "feat(suppliers): TIPO en Proveedor — migración auto por uso con vista previa y crear_bd en sync"
```

---

### Task 2: Servidor — `tipo` en modelo, DAO y controller (TDD con Mockito)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Proveedor.java`, `.../dao/ProveedorDAO.java`, `.../controller/ProveedorController.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ProveedorDAOTest.java`

**Interfaces:**
- Produces (consumido por T3 vía HTTP): JSON de proveedor con campo `tipo`; `GET /api/proveedores?tipo=X` y `GET /api/proveedores/activos?tipo=X` (sin param = todos, como hoy); `POST /api/proveedores` body `{nombre, divisa?, tipo?}` (ausentes ⇒ `EUR`/`COMPONENTES`). Rutas y `@PreAuthorize` NO cambian.
- Produces (interno): `ProveedorDAO.TIPO_COMPONENTES = "COMPONENTES"`, `TIPO_TELEFONOS = "TELEFONOS"`; `getAll(String tipo)`, `getActivos(String tipo)` (null = sin filtro), `insertar(String nombre, String divisa, String tipo)`.

- [ ] **Paso 1: Test que falla** — `ProveedorDAOTest` (patrón `UrgenteAutomaticoJobTest`: Mockito puro; verificar cómo se inyecta `JdbcTemplate` en el DAO real —constructor— y calcar):

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProveedorDAOTest {

    @Test void insertarSinDivisaNiTipoAplicaDefaults() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).insertar("ACME", null, null);
        verify(jdbc).update("INSERT INTO Proveedor (NOMBRE, ACTIVO, DIVISA, TIPO) VALUES (?, 1, ?, ?)",
                "ACME", "EUR", "COMPONENTES");
    }

    @Test void insertarConDivisaYTipoLosRespeta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).insertar("Hy5", "USD", "TELEFONOS");
        verify(jdbc).update(anyString(), eq("Hy5"), eq("USD"), eq("TELEFONOS"));
    }

    @Test void getActivosConTipoFiltraYSinTipoNo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProveedorDAO dao = new ProveedorDAO(jdbc);
        dao.getActivos("TELEFONOS");
        verify(jdbc).query(contains("ACTIVO = 1 AND TIPO = ?"), any(RowMapper.class), eq("TELEFONOS"));
        dao.getActivos(null);
        verify(jdbc).query(contains("ACTIVO = 1 ORDER BY"), any(RowMapper.class));
    }

    @Test void getAllConTipoFiltra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).getAll("COMPONENTES");
        verify(jdbc).query(contains("WHERE TIPO = ?"), any(RowMapper.class), eq("COMPONENTES"));
    }
}
```

(Si las firmas de `JdbcTemplate.query` con varargs hacen fallar los matchers, ajustar los `verify` a la sobrecarga real — el comportamiento afirmado no cambia.)

- [ ] **Paso 2: Ver que falla** — `cd gestion-reparaciones-servidor && mvn -q test -Dtest=ProveedorDAOTest` → no compila (no existen las firmas).

- [ ] **Paso 3: Implementar**
  1. `Proveedor`: campo `String tipo` + getter, constructor pasa a 6 args `(idProv, nombre, activo, divisa, comentario, tipo)`. Actualizar el `MAPPER` del DAO (única construcción).
  2. `ProveedorDAO`: constantes `TIPO_COMPONENTES`/`TIPO_TELEFONOS`; `SELECT` base con `TIPO` incluido; `getAll(String tipo)` y `getActivos(String tipo)` con rama `tipo == null` (SQL de hoy) vs `... TIPO = ?`; `insertar(String nombre, String divisa, String tipo)` con defaults en Java (`divisa` null/blank → `"EUR"`, `tipo` null/blank → `TIPO_COMPONENTES`) y el INSERT del test. Eliminar las firmas viejas sin parámetro (el controller es el único caller).
  3. `ProveedorController`: `getAll(@RequestParam(required = false) String tipo)` y `getActivos(@RequestParam(required = false) String tipo)` delegando; el record `NombreRequest` pasa a `AltaRequest(String nombre, String divisa, String tipo)` y el POST llama `dao.insertar(req.nombre(), req.divisa(), req.tipo())`. `{"nombre":"X"}` del cliente viejo sigue deserializando (divisa/tipo null → defaults). PATCH/PUT/DELETE/tiene-pedidos y todos los `@PreAuthorize` intactos.

- [ ] **Paso 4: Verde + suite completa + commit (repo SERVIDOR)** — `mvn -q test` → verde.

```bash
git add src && git commit -m "feat(suppliers): tipo de proveedor en modelo, DAO y API (?tipo= y alta con divisa/tipo, defaults compatibles)"
```

---

### Task 3: Cliente — `tipo` en modelo/DAO y contextos de piezas a COMPONENTES

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/Proveedor.java`, `.../dao/ProveedorDAO.java`, `.../controllers/StockController.java`, `.../controllers/FormularioCompraController.java`, `.../controllers/FormularioCompraEditarController.java`, `.../controllers/FormularioOtroPedidoController.java`, `.../controllers/FormularioOtroPedidoEditarController.java`

**Interfaces:**
- Consumes: API de T2.
- Produces (consumido por T5/T6): `ProveedorDAO.TIPO_COMPONENTES`/`TIPO_TELEFONOS`; `getAll(String tipo)`, `getActivos(String tipo)`, `insertar(String nombre, String divisa, String tipo)` (null-safe: sin tipo/divisa el body no lleva esas claves).

- [ ] **Paso 1: Crear la rama de raíz si no existe** — ya existe `feature/suppliers` con el plan; trabajar en ella.

- [ ] **Paso 2: Implementar**
  1. `models/Proveedor.java`: campo `String tipo` + getter/setter (Gson mapea por nombre; el constructor no lo necesita — verificar si algún `new Proveedor(...)` requiere el 6º arg y ajustar).
  2. `dao/ProveedorDAO.java`:

```java
public static final String TIPO_COMPONENTES = "COMPONENTES";
public static final String TIPO_TELEFONOS   = "TELEFONOS";

public List<Proveedor> getAll(String tipo) throws SQLException {
    return ApiClient.getList("/api/proveedores" + (tipo == null ? "" : "?tipo=" + tipo), Proveedor.class);
}

public List<Proveedor> getActivos(String tipo) throws SQLException {
    return ApiClient.getList("/api/proveedores/activos" + (tipo == null ? "" : "?tipo=" + tipo), Proveedor.class);
}

public void insertar(String nombre, String divisa, String tipo) throws SQLException {
    Map<String, Object> body = new HashMap<>();
    body.put("nombre", nombre);
    if (divisa != null) body.put("divisa", divisa);
    if (tipo != null) body.put("tipo", tipo);
    ApiClient.post("/api/proveedores", body);
}
```

  Sustituyen a `getAll()`, `getActivos()` e `insertar(String)` (actualizar TODOS los call-sites en este mismo task; el resto de métodos del DAO no cambian).
  3. Contextos de piezas → `TIPO_COMPONENTES`: `FormularioCompraController` L82/L571/L602/L652, `FormularioCompraEditarController` L57, `FormularioOtroPedidoController` L115, `FormularioOtroPedidoEditarController` L82 (`getActivos(ProveedorDAO.TIPO_COMPONENTES)`); `StockController.cargarProveedores()` L1719 → `getAll(ProveedorDAO.TIPO_COMPONENTES)`; `StockController.nuevoProveedor()` L1781 → `insertar(nombre.trim(), null, ProveedorDAO.TIPO_COMPONENTES)`. Los diálogos de lotes (`ImportadorLoteDialog` L100, `AltaManualLoteDialog` L77) se tocan en T6, NO aquí — mientras tanto compilan si se les deja `getActivos(null)`; hacer ese cambio mecánico mínimo (null = comportamiento de hoy).

- [ ] **Paso 3: Suite + commit (repo RAÍZ)** — `cd gestion-reparaciones-cliente && mvn -q test` → verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(suppliers): tipo en Proveedor del cliente y contextos de piezas filtrados a COMPONENTES"
```

---

### Task 4: Cliente — host `InventarioView` con sidebar y wiring movido desde `MainController`

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/resources/views/InventarioView.fxml`, `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/InventarioController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/MainController.java` (L780-784, L989-996)

**Interfaces:**
- Consumes: `AgrupadoController.configurar(Rol, ConfigVistaAgrupado.Vista)`, `.cargar()`, `.resetarModo()`, `.exportarCSV(Stage)`, `.enDetalle()/volverAlMaestro()` (existentes); interfaces `Recargable`/`Exportable`; patrón `mostrarPanel` de `ReparacionControllerSuperTecnico` L315-345.
- Produces (consumido por T5): `InventarioController` con `pnlSuppliers` vacío listo para poblar y métodos `mostrarInventarioPanel()/mostrarSuppliers()`.

- [ ] **Paso 1: `InventarioView.fxml`** — calco estructural de `ReparacionViewSuperTecnico.fxml` reducido a 2 apartados: `BorderPane` raíz con `fx:controller="com.reparaciones.controllers.InventarioController"`; `<left>` `VBox styleClass="stock-sidebar"` con `btnTabInventario` (texto "Inventario", `onAction="#mostrarInventarioPanel"`, clase activa inicial — copiar el styleClass exacto del botón activo de ReparacionViewSuperTecnico L45) y `btnTabSuppliers` (texto "Suppliers", `onAction="#mostrarSuppliers"`); `<center>` `StackPane` con `pnlInventario` (`VBox visible="true"` + `<fx:include source="AgrupadoView.fxml" fx:id="agrupado"/>`) y `pnlSuppliers` (`VBox visible="false" managed="false"`, en este task solo un `Label "Suppliers"` placeholder que T5 sustituye).

- [ ] **Paso 2: `InventarioController`** — `implements Recargable, Exportable`:
  - `initialize()`: `agrupadoController.configurar(Sesion.esSuperTecnico() ? AgrupadoController.Rol.SUPERTECNICO : AgrupadoController.Rol.ADMIN, ConfigVistaAgrupado.Vista.INVENTARIO)` y `agrupadoController.cargar()` (equivale al miss de caché actual de MainController L989-996).
  - `mostrarPanel(VBox, Button)` calco de ReparacionControllerSuperTecnico L315-345 reducido a 2 paneles, incluida la llamada `agrupadoController.resetarModo()` al salir del agrupado y `agrupadoController.cargar()` al volver a mostrarlo.
  - `recargar()`: si `pnlInventario.isVisible()` → `agrupadoController.cargar()`; rama suppliers queda no-op hasta T5. `detenerPolling()`: no-op comentado `/* sin poller */` (precedente EstadisticasController).
  - `exportarCSV(Stage owner)`: calco del delegado L1174 — `if (pnlInventario.isVisible()) { agrupadoController.exportarCSV(owner); }` (suppliers sin CSV — spec, fuera de alcance).
- [ ] **Paso 3: `MainController`** — `mostrarInventario()` L783 pasa a cargar `"/views/InventarioView.fxml"`; ELIMINAR el bloque `instanceof AgrupadoController` L989-996 (el host se configura solo; la ruta genérica `Recargable` L1001-1018 y `descargarCSV()` L844-848 ya cubren revisitas y CSV). Nada más cambia (visibilidad del botón L118-121 intacta).

- [ ] **Paso 4: Suite + commit (repo RAÍZ)** — regresión cero del agrupado (mismas llamadas `configurar`/`cargar`, ahora desde el host). Sin test propio (wiring FXML; precedente T3 reubicación).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(suppliers): pestaña Inventario como host con sidebar (Inventario | Suppliers) y agrupado intacto"
```

---

### Task 5: Cliente — panel Suppliers + diálogos compartidos (`NuevoSupplierDialog`, `EditarProveedorDialog`)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/NuevoSupplierDialog.java`, `.../controllers/EditarProveedorDialog.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/InventarioView.fxml`, `.../controllers/InventarioController.java`, `.../controllers/StockController.java` (L1787-1868)

**Interfaces:**
- Consumes: `ProveedorDAO` de T3, `ConfirmDialog.mostrar(titulo, descripcion, textoAccion, Runnable)`, `Alertas.mostrarError(String)`.
- Produces (consumido por T6): `NuevoSupplierDialog.abrir(Window owner, String nombreInicial, Consumer<String> onCreado)` — modal con nombre editable (prefill), `ComboBox` divisa EUR/USD default EUR, botón "Crear supplier"; al confirmar hace `new ProveedorDAO().insertar(nombre, divisa, ProveedorDAO.TIPO_TELEFONOS)` en hilo `"crear-supplier"` y notifica `onCreado.accept(nombre)` en el hilo FX; error → `Alertas.mostrarError` y el diálogo sigue abierto. Nunca crea en silencio.

- [ ] **Paso 1: Extraer `EditarProveedorDialog`** — mover el Stage modal de `StockController.editarProveedor()` L1787-1868 a `EditarProveedorDialog.abrir(Window owner, Proveedor sel, Runnable onGuardado)` SIN cambios de comportamiento (nombre + divisa EUR/USD + comentario → `proveedorDAO.editar(...)`); `StockController.editarProveedor()` queda en una llamada. (Evita duplicar el bloque al reutilizarlo en el panel Suppliers.)

- [ ] **Paso 2: `NuevoSupplierDialog`** — clase nueva con la firma del bloque Interfaces, construida al estilo del Stage de editar (mismos paddings/botonera); Javadoc en español citando la spec §3.

- [ ] **Paso 3: Panel Suppliers en `InventarioView.fxml`** — sustituir el placeholder de `pnlSuppliers` por el calco del panel de Stock L173-192 con fx:ids propios: `btnNuevoSupplier` ("Nuevo supplier"), `tablaSuppliers` (columnas `cspNombre/cspDivisa/cspActivo/cspComentario`, placeholder "Sin suppliers"), `lblUltimaActSuppliers`. (Sin `MultiSelectComboBox` de filtro: YAGNI con pocos suppliers; se añadiría luego si crece.)

- [ ] **Paso 4: `InventarioController`** — calco de los métodos de Stock acotado a TELEFONOS:
  - `configurarTablaSuppliers()` (valueFactories, badge Activo/Inactivo, menú contextual solo SUPERTECNICO con Activar/Desactivar → `setActivo`, "Editar…" → `EditarProveedorDialog.abrir(...)`, "Borrar" condicionado a `!tienePedidos` → `ConfirmDialog.mostrar` → `borrar`; ADMIN ve la tabla sin menú — mismo gating que Stock L1694-1713).
  - `cargarSuppliers()` → `proveedorDAO.getAll(ProveedorDAO.TIPO_TELEFONOS)` en el patrón de carga de Stock.
  - `btnNuevoSupplier` → `NuevoSupplierDialog.abrir(owner, null, nombre -> cargarSuppliers())`; visible solo SUPERTECNICO (calco Stock L171-174).
  - `mostrarSuppliers()` llama `cargarSuppliers()` al mostrar; `recargar()` gana la rama suppliers.

- [ ] **Paso 5: Suite + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(suppliers): panel Suppliers en Inventario con alta/edición/activo y diálogos compartidos"
```

---

### Task 6: Cliente — combos de lotes a TELEFONOS, etiqueta "Supplier" y creación inline

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ImportadorLoteDialog.java`, `.../controllers/AltaManualLoteDialog.java`

**Interfaces:**
- Consumes: `ProveedorDAO.getActivos(TIPO_TELEFONOS)` (T3), `NuevoSupplierDialog.abrir(owner, nombreInicial, onCreado)` (T5), `buscarProveedorPorNombre` L492-498 (existente).

- [ ] **Paso 1: Importador** (`ImportadorLoteDialog`):
  1. L100 → `getActivos(ProveedorDAO.TIPO_TELEFONOS)`.
  2. Textos: prompt L455 → `"Selecciona supplier…"`; aviso L458 → `"Elige supplier"`.
  3. **Crear inline**: junto al aviso, un `Hyperlink crearSupplier` con texto `"Crear supplier \"" + lote.proveedorNombre() + "\""`, visible/managed SOLO cuando `seleccionActual == null && lote.proveedorNombre() != null && !lote.proveedorNombre().isBlank()`. Acción → `NuevoSupplierDialog.abrir(ventana, lote.proveedorNombre(), nombreCreado -> ...)`. El callback (hilo FX): relanza `getActivos(TIPO_TELEFONOS)` en un hilo `"importador-recarga-proveedores"` y en `Platform.runLater`: actualiza la lista `proveedores` de la sesión, refresca los items de TODOS los combos de lote conservando su selección, y para cada lote AÚN sin selección re-ejecuta `buscarProveedorPorNombre(lote.proveedorNombre())` auto-seleccionando en su combo si ahora matchea (cubre varios lotes del mismo xlsx con el mismo supplier nuevo); recalcula avisos/hyperlinks y `actualizarBotonImportar()`. Para poder iterar los combos, guardar `Map<String, ComboBox<Proveedor>> comboPorLote` (clave batchNumber) al crearlos en `crearPaneLote` — espejo del `seleccionProveedorPorLote` existente.
  4. `confirmar()` L663-713 NO cambia.

- [ ] **Paso 2: Alta manual** (`AltaManualLoteDialog`):
  1. L77 → `getActivos(ProveedorDAO.TIPO_TELEFONOS)`.
  2. Textos: label L157 `"Supplier:"`, prompt L159 → `"Selecciona supplier…"`.
  3. **Entrada "Crear supplier…"** como ÚLTIMA opción del combo: centinela `Proveedor CREAR = new Proveedor(-1, "➕ Crear supplier…", true, "EUR", null)` añadido al final de los items. En el `setOnAction` L162-167, si el valor es el centinela: volver la selección a `null` PRIMERO (que `lblDivisaPrecio` no herede el EUR del centinela) y abrir `NuevoSupplierDialog.abrir(stage, null, nombreCreado -> ...)`; el callback relanza `getActivos(TIPO_TELEFONOS)` en hilo `"alta-manual-recarga-proveedores"` y en `Platform.runLater` repobla el combo (centinela de nuevo al final) y selecciona el creado por nombre (mismo criterio trim+equalsIgnoreCase). `confirmar()` L602+ NO cambia (el guard de `null` existente cubre el caso cancelar).

- [ ] **Paso 3: Suite + commit (repo RAÍZ)** — pegamento UI sin test propio (precedente); la suite completa debe seguir verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(suppliers): combos de lotes filtrados a suppliers y creación inline en importador y alta manual"
```

---

### Task 7: Cierre — suites, review final, ALTER + smoke con el usuario y merges

- [ ] Suites finales verdes en ambos repos (`mvn -q test` en cada uno).
- [ ] Review final de rama con `review-package` en ambos repos (raíz: merge-base con main; servidor: ídem).
- [ ] Usuario aplica `migracion-suppliers.sql` en la VM (vista previa → si (b) vacío, ALTER+UPDATE) y valida el **arranque del servidor** con la rama (regla de la memoria: wiring de beans puede romper el arranque sin que la suite lo pille).
- [ ] Smoke con el usuario (cliente en rama, servidor en rama en la VM o local):
  - Combos: compras de piezas y "otros" solo COMPONENTES; importador y alta manual solo TELEFONOS con etiqueta "Supplier".
  - Importador con xlsx cuyo Supplier Name no exista → "Crear supplier" → confirmar con divisa → queda seleccionado (y auto-seleccionado en otros lotes del mismo nombre si los hay).
  - Alta manual → "➕ Crear supplier…" → crear y confirmar un lote con él.
  - Panel Suppliers: listado solo teléfonos, alta, editar (divisa/comentario), activar/desactivar, borrar bloqueado si tiene lotes… **ojo**: `tienePedidos` solo cuenta compras — ver nota abajo.
  - Stock → Proveedores: idéntico a antes, solo piezas; agrupado INVENTARIO intacto (columnas/filtros/CSV 19); "IMEIs" TALLER intacto; TECNICO sin pestaña.
- [ ] **Nota para review final** (decisión consciente, verificar en smoke): el guard de borrado `tienePedidos()` cuenta `Compra_componente`+`Compra_otro` pero NO `Lote` — borrar un supplier con lotes fallaría por FK `fk_lote_proveedor` con error feo. Si molesta en smoke: ampliar `tienePedidos` en servidor para contar también `Lote` (1 línea SQL) — decidir con el usuario.
- [ ] Merges SOLO con OK: servidor primero (merge --no-ff + push + build VM), luego cliente (merge --no-ff + push) + bump gitlink del submódulo en raíz. Checkboxes en `Apuntes/plan-futuro.md` (sección suppliers) y decisión del usuario: **tag v0.17.0** (con suppliers cerrado, la tanda queda redonda).

---

## Self-review (hecho al escribir el plan)

- **Cobertura de spec**: §1 BD/servidor (T1 script+sync, T2 API compat con defaults) ✓ · §2 combos filtrados + etiqueta Supplier solo en diálogos de lote (T3 piezas, T6 lotes; inventario/CSV intactos = Global Constraint) ✓ · §3 creación inline con confirmación explícita y divisa default EUR (T5 diálogo compartido, T6 wiring importador+alta manual) ✓ · §4 host con sidebar + panel Suppliers + Stock solo piezas + wiring movido (T4+T5, delegación calco L1174) ✓ · §5 testing (T2 TDD Mockito patrón de la casa; pegamento con smoke; arranque Spring en T7) ✓ · §6 riesgos (regresión cero como constraint + smoke; compat ventanas en T2; UNIQUE de Lote intocado) ✓.
- **Consistencia de tipos**: `getAll(String)/getActivos(String)/insertar(String,String,String)` idénticos en T2 (servidor), T3 (cliente) y sus consumidores T5/T6; `NuevoSupplierDialog.abrir(Window, String, Consumer<String>)` igual en T5 (produce) y T6 (consume); constantes `TIPO_*` definidas en ambos DAOs y usadas en T3/T5/T6.
- **Sin placeholders**: los "verificar en código real" restantes (inyección de JdbcTemplate, sobrecarga de varargs en verify, styleClass del botón activo) son lecturas obligadas del implementador, no huecos de diseño.
- **Hallazgo propio del plan**: guard `tienePedidos` sin contar `Lote` — apuntado en T7 como decisión con el usuario, no se arregla en silencio.
- **Nota tooling**: títulos `### Task N:` compatibles con `scripts/task-brief`.
