# Estado "pendiente" en pedidos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar a los pedidos (compras) un estado inicial `pendiente` (anotar sin pedir); al confirmar pasa a `en_camino`. En `pendiente`: borrar (no cancelar). No cuenta como "en camino" en stock/solicitudes.

**Architecture:** Nuevo valor `pendiente` en el enum `Compra_componente.ESTADO`. Todo pedido nace `pendiente`; acciones nuevas confirmar (→`en_camino`) y borrar. Refactor de naming: lo que se llamaba "pendiente" pero significaba "en camino" se renombra. Las consultas de stock/solicitudes filtran por `'en_camino'`/`'parcial'` literales, así que `pendiente` queda excluido sin tocarlas.

**Tech Stack:** Servidor Spring Boot + MariaDB (JdbcTemplate); cliente Java 17 + JavaFX 21.

**Spec:** `docs/superpowers/specs/2026-06-12-estado-pendiente-pedidos-design.md`

---

## Notas previas

- **Sin tests automáticos.** Verificación: `mvn -q compile` por módulo + checklist manual.
  - Servidor: `mvn -q -f ../gestion-reparaciones-servidor/pom.xml compile`
  - Cliente: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile` (con la app cerrada)
- **BD viva:** el usuario aplica el `ALTER` del enum en la VM (Task S1) y redespliega el servidor antes de probar.
- **Ciclo de estados:** `pendiente → en_camino → {parcial, recibido} | cancelado`.
- **Propiedad clave:** las queries en `ComponenteDAO` (`SUM(... IN ('en_camino','parcial'))`), `ReparacionDAO` (`= 'en_camino'`) y `ReparacionComponenteDAO` (`= 'en_camino'`) **NO se tocan** — `pendiente` queda excluido automáticamente.

---

## File Structure

**Servidor:**
- `sql/crear_bd.sql` — enum corregido + ampliado.
- `sql/migracion-estado-pendiente.sql` (nuevo) — `ALTER` para la BD viva.
- `dao/CompraComponenteDAO.java` — `insertar` con `'pendiente'`; renombres; `confirmar` + `borrarPendiente`.
- `controller/CompraController.java` — renombres de endpoints; `PATCH /{id}/confirmar` + `DELETE /{id}`.

**Cliente:**
- `models/CompraComponente.java` — enum `Estado` añade `pendiente`.
- `dao/CompraComponenteDAO.java` — renombres + `confirmar`/`borrar`.
- `controllers/StockController.java` — caso `pendiente` (menú, chip, color, badge, handlers, var local).

---

## TAREAS DE SERVIDOR

### Task S1: Esquema — enum del estado

**Files:**
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql:125`
- Create: `gestion-reparaciones-servidor/sql/migracion-estado-pendiente.sql`

- [ ] **Step 1: Corregir y ampliar el enum en crear_bd.sql**

La línea 125 tiene un bug (falta `'en_camino'`, sobra el sentido de `'pendiente'`). Reemplaza:

```sql
    ESTADO               ENUM('pendiente','recibido','parcial','cancelado') NOT NULL DEFAULT 'pendiente',
```
por:
```sql
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
```

- [ ] **Step 2: Crear el script de migración para la BD viva**

Crea `gestion-reparaciones-servidor/sql/migracion-estado-pendiente.sql`:

```sql
-- Migración Item 4: añadir 'pendiente' al enum de Compra_componente.ESTADO.
-- Aplicar en la BD viva de la VM. Los valores existentes (en_camino/parcial/recibido/cancelado) se conservan.
USE gestion_reparaciones;

ALTER TABLE Compra_componente
    MODIFY COLUMN ESTADO ENUM('pendiente','en_camino','parcial','recibido','cancelado')
    NOT NULL DEFAULT 'pendiente';
```

(El enum nuevo es superconjunto del actual de la BD viva, así que no se pierde ningún dato.)

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-servidor/sql/crear_bd.sql gestion-reparaciones-servidor/sql/migracion-estado-pendiente.sql
git commit -m "feat: enum Compra_componente con estado pendiente (+ migracion)"
```

---

### Task S2: `CompraComponenteDAO` (servidor) — insertar pendiente, renombres, confirmar, borrar

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java`

- [ ] **Step 1: `insertar` crea en `'pendiente'`**

En `insertar`, cambia el literal del estado en el `VALUES`:

```java
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
```
(antes `'en_camino'`).

- [ ] **Step 2: Renombrar `getPendientes` → `getEnCamino`**

Reemplaza el método:

```java
    public List<CompraComponente> getEnCamino() {
        return jdbc.query(SELECT_BASE +
                " WHERE cc.ESTADO = 'en_camino'" +
                " ORDER BY cc.ES_URGENTE DESC, cc.FECHA_PEDIDO ASC", MAPPER);
    }
```

- [ ] **Step 3: Renombrar `getCantidadPendientePorComponente` → `getCantidadEnCaminoPorComponente`**

Reemplaza el método (el cuerpo no cambia, sigue filtrando `en_camino`/`parcial`):

```java
    public int getCantidadEnCaminoPorComponente(int idCom) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0)), 0)" +
                " FROM Compra_componente WHERE ID_COM = ? AND ESTADO IN ('en_camino','parcial')",
                Integer.class, idCom);
    }
```

- [ ] **Step 4: Añadir `confirmar` y `borrarPendiente`**

Añádelos junto a `cancelar` (siguen el patrón optimista `getCompraRow` + `checkUpdatedAt`):

```java
    public void confirmar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='en_camino' WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente");
        }
    }

    public void borrarPendiente(int idCompra) {
        int n = jdbc.update(
                "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente (no se puede borrar)");
        }
    }
```

- [ ] **Step 5: Compilar**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml compile`
Expected: BUILD FAILURE — `CompraController` aún llama a `getPendientes`/`getCantidadPendientePorComponente` (se arregla en S3). Si quieres compilar limpio, haz S3 antes de compilar; si no, salta al commit y compila tras S3.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java
git commit -m "feat: insertar pendiente, confirmar/borrarPendiente, renombrar a getEnCamino"
```

---

### Task S3: `CompraController` — renombrar endpoints + confirmar/borrar

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java`

- [ ] **Step 1: Renombrar el endpoint `/pendientes` → `/en-camino`**

Reemplaza:

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping("/pendientes")
    public List<CompraComponente> getPendientes() {
        return dao.getPendientes();
    }
```
por:
```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping("/en-camino")
    public List<CompraComponente> getEnCamino() {
        return dao.getEnCamino();
    }
```

- [ ] **Step 2: Renombrar el endpoint `/cantidad-pendiente` → `/cantidad-en-camino`**

Reemplaza:

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-pendiente/{idCom}")
    public Map<String, Object> getCantidadPendiente(@PathVariable int idCom) {
        return Map.of("value", dao.getCantidadPendientePorComponente(idCom));
    }
```
por:
```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-en-camino/{idCom}")
    public Map<String, Object> getCantidadEnCamino(@PathVariable int idCom) {
        return Map.of("value", dao.getCantidadEnCaminoPorComponente(idCom));
    }
```

- [ ] **Step 3: Añadir endpoints `confirmar` y `borrar`**

Junto al endpoint `cancelar` (`@PatchMapping("/{idCompra}/cancelar")`), añade:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar")
    public void confirmar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmar(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idCompra}")
    public void borrar(@PathVariable int idCompra,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrarPendiente(idCompra);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }
```

(`UpdatedAtRequest` ya existe como record privado del controlador; `DeleteMapping` se importa con el wildcard `org.springframework.web.bind.annotation.*` ya presente.)

- [ ] **Step 4: Compilar (servidor completo)**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java
git commit -m "feat: endpoints /en-camino, /cantidad-en-camino, confirmar y borrar pedido"
```

---

## TAREAS DE CLIENTE

### Task C1: Enum `CompraComponente.Estado`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/CompraComponente.java:26`

- [ ] **Step 1: Añadir `pendiente` al enum**

Reemplaza:

```java
    public enum Estado { en_camino, recibido, parcial, cancelado }
```
por:
```java
    public enum Estado { pendiente, en_camino, recibido, parcial, cancelado }
```

(Y actualiza el Javadoc de arriba para incluir `pendiente — pedido anotado, aún sin realizar`.)

- [ ] **Step 2: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/CompraComponente.java
git commit -m "feat: estado pendiente en el enum CompraComponente.Estado"
```

---

### Task C2: `CompraComponenteDAO` (cliente) — renombres + confirmar/borrar

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/CompraComponenteDAO.java`

- [ ] **Step 1: Renombrar `getPendientes` → `getEnCamino` (+ URL)**

Reemplaza (líneas 38-40):

```java
    public List<CompraComponente> getEnCamino() throws SQLException {
        return ApiClient.getList("/api/compras/en-camino", CompraComponente.class);
    }
```
(actualiza también el Javadoc: "pedidos en estado `en_camino`").

- [ ] **Step 2: Renombrar `getCantidadPendientePorComponente` → `getCantidadEnCaminoPorComponente` (+ URL)**

Reemplaza (líneas 49-51):

```java
    public int getCantidadEnCaminoPorComponente(int idCom) throws SQLException {
        return ApiClient.getInt("/api/compras/cantidad-en-camino/" + idCom);
    }
```

- [ ] **Step 3: `insertar` ahora crea pendiente (solo Javadoc)**

El cuerpo no cambia (el servidor pone `'pendiente'`). Actualiza el Javadoc de `insertar`: "Inserta un nuevo pedido en estado `pendiente`."

- [ ] **Step 4: Añadir `confirmar` y `borrar`**

Junto a `cancelar`, añade:

```java
    /** Confirma un pedido pendiente: pasa a {@code en_camino}. */
    public void confirmar(CompraComponente pedido) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras/" + pedido.getIdCompra() + "/confirmar",
                Map.of("updatedAt", pedido.getUpdatedAt()));
    }

    /** Borra un pedido en estado {@code pendiente}. */
    public void borrar(CompraComponente pedido) throws SQLException, StaleDataException {
        ApiClient.delete("/api/compras/" + pedido.getIdCompra());
    }
```

- [ ] **Step 5: Barrer los javadocs que dicen "pendiente" queriendo decir "en camino"**

Ahora que `pendiente` es un estado real, estos javadocs del DAO cliente pasan a ser erróneos. Corrígelos:
- `getCantidadEnCaminoPorComponente` (antes `...Pendiente...`): el javadoc dice "unidades pendientes de recibir / estado pendiente" → "unidades en camino (estados `en_camino`/`parcial`)".
- `cancelar`: "Cancela un pedido en estado **pendiente**." → "Cancela un pedido en estado **en_camino**."
- `desrecibir`: "Revierte un pedido recibido a **pendiente**..." → "...a **en_camino**...".

(Revisa con una búsqueda de "pendiente" en el archivo que no quede ninguna referencia que signifique "en camino".)

- [ ] **Step 6: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD FAILURE — `StockController` aún llama a `getCantidadPendientePorComponente` (se arregla en C3). Continúa a C3 y compila al final.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/CompraComponenteDAO.java
git commit -m "feat: cliente getEnCamino/getCantidadEnCamino + confirmar/borrar pedido (+ javadocs)"
```

---

### Task C3: `StockController` — caso `pendiente`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/StockController.java`

- [ ] **Step 1: Renombrar la variable local + la llamada en `cargarChartSku`**

En `cargarChartSku` (líneas ~524-545) renombra `pendiente` → `enCamino` y la llamada al DAO:

```java
    private void cargarChartSku(Componente c) {
        int enCamino = 0;
        if (com.reparaciones.Sesion.esAdminOSuperTecnico()) {
            try {
                enCamino = compraDAO.getCantidadEnCaminoPorComponente(c.getIdCom());
            } catch (SQLException e) {
                mostrarError(e);
                return;
            }
        }
```
Y más abajo, sustituye los usos de `pendiente` por `enCamino`:
```java
        serie.getData().add(new javafx.scene.chart.XYChart.Data<>("Pedido", enCamino));
        int maxVal = Math.max(c.getStock(), enCamino);
```

- [ ] **Step 2: Color de fila — añadir `case pendiente`**

El `switch` del rowFactory (líneas 846-853) es exhaustivo (sin `default`): al añadir `pendiente` al enum **no compila** hasta añadir el caso. Añade, dentro del `switch (item.getEstado())`, antes de `case recibido`:

```java
                    case pendiente -> barraIzq + "#C8961E;";   // ámbar: anotado, sin pedir
```

- [ ] **Step 3: Badge — añadir `case pendiente`**

En el `switch` del badge (líneas 880-887) añade antes de `case recibido`:

```java
                    case pendiente -> { bg = "#FFF3D6"; txt = "#B26A00"; }
```

- [ ] **Step 4: Chip de filtro "pendiente"**

En el array de estados (línea 919) añade `"pendiente"` al principio:

```java
        for (String estado : new String[]{"pendiente", "en camino", "parcial", "recibido", "cancelado"}) {
```

(El predicado de filtro usa `p.getEstado().name().replace('_',' ')` → para `pendiente` da `"pendiente"`, que casa con el chip.)

- [ ] **Step 5: Activar "pendiente" por defecto al navegar desde un componente**

En `navegarAPedidosDeComponente` (líneas 199-201) incluye `pendiente` en la selección:

```java
        // Activar pendiente + en camino + parcial (el pipeline activo del componente)
        cbsEstado.forEach(cb -> cb.setSelected(
                cb.getText().equals("pendiente") || cb.getText().equals("en camino") || cb.getText().equals("parcial")));
```

- [ ] **Step 6: Menú contextual — caso `pendiente`**

En `construirMenuContextual` (líneas 949-976) añade, dentro del `switch`, antes de `case en_camino`:

```java
            case pendiente -> {
                MenuItem confirmar = new MenuItem("Confirmar pedido");
                MenuItem editar    = new MenuItem("Editar");
                MenuItem borrar    = new MenuItem("Borrar");
                confirmar.setOnAction(e -> confirmarPedido());
                editar   .setOnAction(e -> editarPedido());
                borrar   .setOnAction(e -> borrarPedido());
                ctx.getItems().addAll(confirmar, new SeparatorMenuItem(), editar, borrar);
            }
```

- [ ] **Step 7: Handlers `confirmarPedido` y `borrarPedido`**

Junto a `cancelarPedido` (línea ~1105) añade:

```java
    @FXML private void confirmarPedido() {
        CompraComponente sel = tablaPedidos.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            compraDAO.confirmar(sel);
            cargarPedidos();
        } catch (com.reparaciones.utils.StaleDataException e) {
            mostrarConflicto(); cargarPedidos();
        } catch (SQLException e) { mostrarError(e); }
    }

    @FXML private void borrarPedido() {
        CompraComponente sel = tablaPedidos.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        ConfirmDialog.mostrar(
                "Borrar pedido",
                "¿Borrar el pedido pendiente #" + sel.getIdCompra() + " de " + sel.getTipoComponente() + "?",
                "Borrar",
                () -> {
                    try {
                        compraDAO.borrar(sel);
                        cargarPedidos();
                    } catch (com.reparaciones.utils.StaleDataException e) {
                        mostrarConflicto(); cargarPedidos();
                    } catch (SQLException e) { mostrarError(e); }
                });
    }
```

(`ConfirmDialog` ya está importado/usado en este archivo; `mostrarConflicto()` y `mostrarError()` existen.)

- [ ] **Step 8: Compilar (cliente completo)**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS. Si hay otro `switch` exhaustivo sobre `Estado` sin `default` (p. ej. en `cpCantidad`), el compilador lo señalará — añade el caso `pendiente` que corresponda (en `cpCantidad`, línea ~762, el `default` ya lo cubre: muestra `String.valueOf(p.getCantidad())`, correcto para pendiente).

- [ ] **Step 9: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/StockController.java
git commit -m "feat: caso pendiente en StockController (menu confirmar/editar/borrar, chip, color, badge)"
```

---

### Task C4: Verificación manual (UAT)

- [ ] **Step 1: Aplicar la migración + desplegar servidor**

El usuario aplica `sql/migracion-estado-pendiente.sql` (o el `ALTER`) en la BD de la VM y reinicia el servidor.

- [ ] **Step 2: Arrancar el cliente y probar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml javafx:run` (como supertécnico, pestaña Pedidos).

Checklist:
1. Crear un pedido nuevo → aparece como **`pendiente`** (chip "pendiente", color ámbar). **No** suma a "En camino" del stock (gráfico del componente) ni activa el badge "en camino" de una solicitud del mismo componente.
2. Menú contextual sobre un `pendiente` → **Confirmar pedido · Editar · Borrar** (sin "Cancelar").
3. **Editar** un `pendiente` (cantidad/proveedor/precio) → sigue `pendiente`.
4. **Borrar** un `pendiente` (con confirmación) → desaparece; stock intacto.
5. **Confirmar** un `pendiente` → pasa a `en_camino`; ahora **sí** suma a "En camino" y activa el badge de solicitudes.
6. Sobre un `en_camino` el menú sigue como antes (Recepción parcial/Confirmar recibido/Editar/Cancelar pedido).
7. Filtro: el chip "pendiente" filtra correctamente; navegar desde un componente muestra pendiente+en camino+parcial.
8. La columna/gráfico "En camino" del stock sigue funcionando (endpoint renombrado `/cantidad-en-camino`).

- [ ] **Step 3: Commit de ajustes (si hubo)**

```bash
git add -A && git commit -m "fix: ajustes UAT del estado pendiente en pedidos"
```

---

## Self-Review (autor del plan)

**Cobertura del spec:**
- Estado `pendiente` en enum (BD + cliente) → S1, C1. ✓
- Pedido nace pendiente → S2 Step 1. ✓
- Confirmar (pendiente→en_camino) + Borrar → S2 Step 4, S3 Step 3, C2 Step 4, C3 Steps 6-7. ✓
- Refactor naming (getEnCamino, getCantidadEnCamino, /en-camino, /cantidad-en-camino, var enCamino) → S2 Steps 2-3, S3 Steps 1-2, C2 Steps 1-2, C3 Step 1. ✓
- Stock/solicitudes sin cambios (pendiente excluido) → no se tocan ComponenteDAO/ReparacionDAO/ReparacionComponenteDAO. ✓
- UI: menú (sin Cancelar), chip activo por defecto, color/badge → C3 Steps 2-7. ✓
- FECHA_PEDIDO se mantiene (no se resetea al confirmar) → `confirmar` solo cambia ESTADO. ✓
- Migración BD (la aplica el usuario) → S1 Step 2. ✓

**Consistencia de tipos/nombres:** `getEnCamino`/`getCantidadEnCaminoPorComponente` (servidor y cliente), endpoints `/en-camino` y `/cantidad-en-camino/{idCom}`, `confirmar(idCompra, updatedAt)`/`borrarPendiente(idCompra)` (servidor) y `confirmar(pedido)`/`borrar(pedido)` (cliente), `Estado.pendiente`. Coherente entre tareas.

**Zonas a verificar al compilar:** el `switch` exhaustivo del rowFactory (846-853) obliga a añadir `case pendiente` (C3 Step 2); si hubiera otro exhaustivo, el compilador lo marca (C3 Step 8).
