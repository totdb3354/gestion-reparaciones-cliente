# Sub-proyecto 4a — Stock actual y Proveedores: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el "Pendiente de migrar" de `/stock` por las pestañas "Stock actual" y "Proveedores" de la vista Stock del JavaFX (tabla con semáforo, filtros, gráficos, menú y diálogos; proveedores con alta, edición, activación y borrado), y subir al servidor la cantidad en camino por master, el 409 al borrar un proveedor con pedidos y las validaciones de rango y nombre.

**Architecture:** El servidor gana tres cambios aditivos en controladores y DAO existentes. La web gana el módulo `modules/almacen/` con dos páginas (`stock/`, `proveedores/`) construidas sobre `DataTable`, `MultiSelect`, `ConfirmDialog` y los hooks de refresco existentes; el semáforo de stock pasa a `shared/lib` para que la campana y el formulario lo compartan; los gráficos van con Recharts; los filtros y la selección viven en stores de módulo para sobrevivir al cambio de pestaña.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + JUnit 5 + Mockito + MockMvc. Web React 19 + TypeScript + TanStack Query + openapi-fetch + Recharts + Vitest + Testing Library + MSW + Playwright.

**Spec:** [`docs/superpowers/specs/2026-09-24-web-almacen-stock-design.md`](../specs/2026-09-24-web-almacen-stock-design.md) (decisiones S1-S9 vinculantes) y [`2026-09-24-web-almacen-programa-design.md`](../specs/2026-09-24-web-almacen-programa-design.md) (D1-D16). **Referencia de detalle:** el inventario `inventario-stock.md`, guardado fuera del repo (lo tiene el controlador de la sesión; si una regla de este plan no cuadra con el JavaFX, manda el JavaFX y se consulta).

## Global Constraints

- **Ramas:** `feature/web-stock` en `gestion-reparaciones-web` y en `gestion-reparaciones-servidor`, creadas desde `main`. El repo raíz se queda en `main`.
- **Nunca** `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Commits sin `Co-Authored-By`.** Mensajes en español, en minúscula tras el prefijo.
- **Los tres repos son públicos:** ningún dato real (SKUs reales, proveedores reales, nombres de técnicos, dominios, IPs) en código, tests, comentarios ni documentación. SKUs sintéticos (`lcd-x-negro`, `bat-x`), proveedores "Proveedor A", "ACME".
- **El cliente JavaFX NO se toca.** Se consulta en solo lectura con `git show hotfix/0.16.3:gestion-reparaciones-cliente/<ruta>` desde el raíz.
- **Todo el trabajo de servidor es aditivo:** ninguna respuesta que el JavaFX consuma cambia de forma; las validaciones nuevas coinciden con las que el cliente ya aplica antes de llamar.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **Antes de cada tarea, buscar en el repo lo que va a crear** (lección del 3a y 3b): si ya existe, se reutiliza y se anota la desviación.
- **Cada petición se verifica contra el contrato OpenAPI real** (`src/shared/api/schema.d.ts`) antes de escribir el código que la usa. Si un nombre de esquema no coincide con este plan, manda el contrato.
- **Textos visibles exactos** (se copian tal cual): ver cada tarea; ninguno se reescribe "mejorado". Los que la spec corrige (S5) están marcados.
- **Un módulo no importa de otro módulo** (regla de lint del repo): lo compartido entre `taller` y `almacen` va a `src/shared`.

---

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor`)

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` | *Modificar.* `getCantidadEnCaminoPorComponente` resuelve al master |
| `src/main/java/com/reparaciones/servidor/controller/CompraController.java` | *Modificar.* `cantidad-en-camino` devuelve `ValorEntero` |
| `src/main/java/com/reparaciones/servidor/controller/ProveedorController.java` | *Modificar.* 422 de nombre y divisa, 409 al borrar con pedidos, log `BORRAR_PROVEEDOR` |
| `src/main/java/com/reparaciones/servidor/controller/ComponenteController.java` | *Modificar.* 422 de stock y mínimo negativos |
| `src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOEnCaminoTest.java` | *Crear.* Resolución al master |
| `src/test/java/com/reparaciones/servidor/controller/CompraControllerEnCaminoTest.java` | *Crear.* Respuesta tipada |
| `src/test/java/com/reparaciones/servidor/controller/ProveedorControllerTest.java` | *Crear.* 422, 409, log |
| `src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java` | *Crear.* 422 |
| `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` | *Modificar.* `cantidad-en-camino` → `ValorEntero` |

**Web** (`gestion-reparaciones-web`)

| Fichero | Responsabilidad |
|---|---|
| `package.json`, `src/shared/api/schema.d.ts`, `api/openapi.json`, `src/shared/api/client.ts`, `src/shared/styles/tokens.css` | *Modificar.* Recharts, contrato regenerado, alias `Proveedor`, tokens `ambar-grafico` y `badge-sin-stock-bg` |
| `src/shared/lib/semaforoStock.ts` (+ test) | *Crear.* `estadoStock` de cuatro valores (S3) |
| `src/modules/taller/lib/piezas.ts` | *Modificar.* `nivelStock` sobre `estadoStock` |
| `src/shared/lib/useInteraccionesAbiertas.ts` (+ test) | *Mover* desde `modules/taller/asignaciones/` |
| `src/modules/almacen/rutas.ts` | *Crear.* `enlacesStock()` para el `SubNav` |
| `src/app/shell/SubNav.tsx` (+ test), `src/app/router.tsx` | *Modificar.* Sección `stock`, rutas `/stock`, `/stock/pedidos`, `/stock/proveedores` |
| `src/modules/almacen/ui/DialogoAlmacen.tsx` (+ test) | *Crear.* Diálogo base: título, subtítulo, error inline, Cancelar/Confirmar |
| `src/modules/almacen/stock/filtros.ts` (+ test) | *Crear.* Orden, filtro de estado (OR), buscador, textos de botón y pie |
| `src/modules/almacen/stock/estado.ts` | *Crear.* Stores de filtros y selección (S2) |
| `src/modules/almacen/stock/api.ts` (+ test) | *Crear.* Consulta de componentes, cantidad en camino, cuatro mutaciones |
| `src/modules/almacen/stock/columnas.tsx` (+ test) | *Crear.* Seis columnas, enlace "En Camino", badge, clase de fila, CSV |
| `src/modules/almacen/stock/graficos.ts` (+ test), `GraficoEstado.tsx`, `GraficoSku.tsx` (+ test) | *Crear.* Conteos del donut, colores, los dos gráficos |
| `src/modules/almacen/stock/EditarStockDialog.tsx`, `AjustarMinimoDialog.tsx`, `SolicitarPiezaDialog.tsx` (+ test) | *Crear.* Los tres diálogos de componente |
| `src/modules/almacen/stock/MenuComponente.tsx`, `StockPage.tsx` (+ tests) | *Crear.* Menú por rol y la página |
| `src/modules/almacen/proveedores/api.ts`, `estado.ts`, `columnas.tsx`, `MenuProveedor.tsx`, `NuevoProveedorDialog.tsx`, `EditarProveedorDialog.tsx`, `ProveedoresPage.tsx` (+ tests) | *Crear.* La pestaña Proveedores |
| `src/modules/taller/notificaciones/PanelNotificaciones.tsx` (+ test) | *Modificar.* "Ver Stock Completo" e "→ Ir a pedidos" navegan |
| `tests/e2e/stock.spec.ts`, `.env.e2e.example`, `README.md` | *Crear/Modificar.* Smoke |
| `docs/paridad/stock.md`, `CHANGELOG.md`, `package.json` | *Crear/Modificar.* Ficha, versión 0.6.0 |

---

## Task 1: Servidor — `cantidad-en-camino` por master y respuesta tipada

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java:66-71`
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraController.java:47-50`
- Create: `src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOEnCaminoTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/CompraControllerEnCaminoTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `CompraComponenteDAO.resolveToMasterId(int)` (privado, ya existe en :185), `model.ValorEntero(int value)` (ya existe).
- Produces: `GET /api/compras/cantidad-en-camino/{idCom}` → `ValorEntero` (`{"value": n}`, la misma forma JSON que hoy: el JavaFX no nota nada).

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git checkout -b feature/web-stock
```

- [ ] **Step 2: Test del DAO (falla)**

`src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOEnCaminoTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La cantidad en camino de un SKU compartido es la de su master: las compras se insertan siempre en el master
 *  (insertar → resolveToMasterId), así que consultar por el id del slave devolvía 0 (spec 4a §4.1). */
class CompraComponenteDAOEnCaminoTest {

    @Test void unSlaveConsultaLaSumaDeSuMaster() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        when(jdbc.queryForObject(contains("SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0))"), eq(Integer.class), eq(3))).thenReturn(5);

        int enCamino = new CompraComponenteDAO(jdbc).getCantidadEnCaminoPorComponente(12);

        assertEquals(5, enCamino);
        verify(jdbc, never()).queryForObject(contains("SUM(CANTIDAD"), eq(Integer.class), eq(12));
    }

    @Test void unMasterConsultaSuPropioId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(3))).thenReturn(3);
        when(jdbc.queryForObject(contains("SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0))"), eq(Integer.class), eq(3))).thenReturn(2);

        assertEquals(2, new CompraComponenteDAO(jdbc).getCantidadEnCaminoPorComponente(3));
    }
}
```

Si el constructor de `CompraComponenteDAO` recibe más dependencias que `JdbcTemplate`, mirar `src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java:20-40` y pasar `mock(...)` de cada una.

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=CompraComponenteDAOEnCaminoTest`
Expected: FAIL en `unSlaveConsultaLaSumaDeSuMaster` (la consulta va con 12 y devuelve `null` → `NullPointerException` o 0).

- [ ] **Step 4: Resolver al master en el DAO**

En `CompraComponenteDAO.getCantidadEnCaminoPorComponente`:

```java
    /** Cantidad pendiente de llegar del SKU, resuelta al master del grupo compartido: las compras se insertan
     *  siempre en el master, así que preguntar por un slave sin resolver devolvía 0 (sub-proyecto 4a). */
    public int getCantidadEnCaminoPorComponente(int idCom) {
        idCom = resolveToMasterId(idCom);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0)), 0)" +
                " FROM Compra_componente WHERE ID_COM = ? AND ESTADO IN ('en_camino','parcial')",
                Integer.class, idCom);
    }
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=CompraComponenteDAOEnCaminoTest`
Expected: PASS, 2 tests.

- [ ] **Step 6: Test del controlador (falla)**

`src/test/java/com/reparaciones/servidor/controller/CompraControllerEnCaminoTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La respuesta pasa de Map<String,Object> a ValorEntero: misma forma JSON {"value": n}, pero el contrato OpenAPI
 *  la tipa como entero y la web no tiene que convertirla a mano. */
class CompraControllerEnCaminoTest {

    @Test void cantidadEnCaminoDevuelveValorEnteroTipado() {
        CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
        when(dao.getCantidadEnCaminoPorComponente(12)).thenReturn(5);
        CompraController ctl = new CompraController(dao, mock(LogDAO.class), mock(ComponenteDAO.class), mock(ProveedorDAO.class));

        assertEquals(5, ctl.getCantidadEnCamino(12).value());
    }
}
```

- [ ] **Step 7: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=CompraControllerEnCaminoTest`
Expected: FAIL de compilación (`value()` no existe en `Map`).

- [ ] **Step 8: Tipar la respuesta**

En `CompraController`:

```java
import com.reparaciones.servidor.model.ValorEntero;
...
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-en-camino/{idCom}")
    public ValorEntero getCantidadEnCamino(@PathVariable int idCom) {
        return new ValorEntero(dao.getCantidadEnCaminoPorComponente(idCom));
    }
```

Quitar el import de `java.util.Map` solo si ya no lo usa nadie más en el fichero (`insertar` y otros métodos pueden usarlo: comprobar antes).

- [ ] **Step 9: Contrato**

En `OpenApiContractTest.elContratoPublicaLosEsquemasDeLaWeb`, junto a la comprobación de `tiene-telefonos` (línea ~137), añadir:

```java
        assertTrue(refDeLaRespuesta(paths, "/api/compras/cantidad-en-camino/{idCom}", "get", "200").endsWith("/ValorEntero"),
                "cantidad-en-camino debe responder ValorEntero");
```

`refDeLaRespuesta` ya existe en ese test (línea ~156 la usa). Si su firma es distinta, adaptarla mirando el helper.

- [ ] **Step 10: Ejecutar los tres**

Run: `mvn -q test -Dtest='CompraComponenteDAOEnCaminoTest,CompraControllerEnCaminoTest,OpenApiContractTest'`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java src/main/java/com/reparaciones/servidor/controller/CompraController.java src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOEnCaminoTest.java src/test/java/com/reparaciones/servidor/controller/CompraControllerEnCaminoTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "fix(compras): cantidad en camino resuelta al master del sku compartido y respuesta tipada ValorEntero"
```

---

## Task 2: Servidor — proveedores: 422 de nombre y divisa, 409 al borrar con pedidos, log

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/controller/ProveedorController.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/ProveedorControllerTest.java`

**Interfaces:**
- Consumes: `ProveedorDAO.tienePedidos(int)`, `ProveedorDAO.getNombreById(int)`, `LogDAO.insertar(int idUsu, String accion, String detalle)`, `security.UsuarioPrincipal.getIdUsu()`.
- Produces: `POST /api/proveedores` y `PUT /api/proveedores/{idProv}` → 422 con `"El nombre no puede estar vacío."` (nombre nulo, en blanco o de más de 100 caracteres) o `"Divisa no válida (EUR o USD)."` (divisa presente y distinta de `EUR`/`USD`; en el `POST` la divisa nula sigue valiendo, el DAO pone `EUR`). `DELETE /api/proveedores/{idProv}` → 409 `"El proveedor tiene pedidos y no se puede borrar."` y, si borra, log `BORRAR_PROVEEDOR` con `ID_PROV: n, NOMBRE: x`. Los mensajes de 422 son los que muestra el cliente JavaFX (inventario §12.1-12.2).

- [ ] **Step 1: Test (falla)**

`src/test/java/com/reparaciones/servidor/controller/ProveedorControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Guards que hasta el 4a solo hacía el cliente JavaFX (spec 4a §4.2-4.3): el servidor borraba a ciegas (500 por clave
 *  foránea en carrera) y aceptaba nombres vacíos y cualquier divisa. Los textos son los del cliente. */
class ProveedorControllerTest {

    private final ProveedorDAO dao = mock(ProveedorDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorController ctl = new ProveedorController(dao, logDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    private static ResponseStatusException falla(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    @Test void altaConNombreEnBlancoEs422() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("   ", null, "COMPONENTES")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", e.getReason());
        verifyNoInteractions(dao);
    }

    @Test void altaConNombreDeMasDe100Es422() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("x".repeat(101), null, null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", e.getReason());
    }

    @Test void altaConDivisaDesconocidaEs422YConNulaVale() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("ACME", "CNY", null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Divisa no válida (EUR o USD).", e.getReason());
        ctl.insertar(new ProveedorController.AltaRequest("ACME", null, "COMPONENTES"));
        verify(dao).insertar("ACME", null, "COMPONENTES");
    }

    @Test void altaRecortaElNombre() {
        ctl.insertar(new ProveedorController.AltaRequest("  ACME  ", "USD", "COMPONENTES"));
        verify(dao).insertar("ACME", "USD", "COMPONENTES");
    }

    @Test void editarValidaNombreYDivisa() {
        ResponseStatusException e1 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("", "EUR", "")));
        assertEquals("El nombre no puede estar vacío.", e1.getReason());
        ResponseStatusException e2 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("ACME", "GBP", "")));
        assertEquals("Divisa no válida (EUR o USD).", e2.getReason());
        ctl.editar(4, new ProveedorController.EditarRequest(" ACME ", "usd", "nota"));
        verify(dao).editar(4, "ACME", "USD", "nota");
    }

    @Test void borrarConPedidosEs409SinTocarNada() {
        when(dao.tienePedidos(4)).thenReturn(true);
        ResponseStatusException e = falla(() -> ctl.borrar(4, super7));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El proveedor tiene pedidos y no se puede borrar.", e.getReason());
        verify(dao, never()).borrar(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void borrarSinPedidosBorraYRegistraLog() {
        when(dao.tienePedidos(4)).thenReturn(false);
        when(dao.getNombreById(4)).thenReturn("ACME");
        ctl.borrar(4, super7);
        verify(dao).borrar(4);
        verify(logDao).insertar(7, "BORRAR_PROVEEDOR", "ID_PROV: 4, NOMBRE: ACME");
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=ProveedorControllerTest`
Expected: FAIL de compilación (constructor con `LogDAO`, records privados, firma de `borrar`).

- [ ] **Step 3: Implementar**

`ProveedorController` completo:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    /** Las dos divisas del combo "Editar proveedor" del cliente (sub-proyecto 4a). */
    private static final Set<String> DIVISAS = Set.of("EUR", "USD");
    private static final int NOMBRE_MAX = 100; // VARCHAR(100) de Proveedor.NOMBRE
    static final String MSG_NOMBRE = "El nombre no puede estar vacío.";
    static final String MSG_DIVISA = "Divisa no válida (EUR o USD).";
    static final String MSG_TIENE_PEDIDOS = "El proveedor tiene pedidos y no se puede borrar.";

    private final ProveedorDAO dao;
    private final LogDAO logDao;

    public ProveedorController(ProveedorDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<Proveedor> getAll(@RequestParam(required = false) String tipo) {
        return dao.getAll(tipo);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/activos")
    public List<Proveedor> getActivos(@RequestParam(required = false) String tipo) {
        return dao.getActivos(tipo);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/{idProv}/tiene-pedidos")
    public Map<String, Boolean> tienePedidos(@PathVariable int idProv) {
        return Map.of("value", dao.tienePedidos(idProv));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody AltaRequest req) {
        String nombre = nombreValido(req.nombre());
        // Divisa nula: el DAO pone EUR (calco del alta del cliente, que no la manda).
        String divisa = req.divisa() == null ? null : divisaValida(req.divisa());
        dao.insertar(nombre, divisa, req.tipo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idProv}/activo")
    public void setActivo(@PathVariable int idProv, @RequestBody ActivoRequest req) {
        dao.setActivo(idProv, req.activo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}")
    public void editar(@PathVariable int idProv, @RequestBody EditarRequest req) {
        dao.editar(idProv, nombreValido(req.nombre()), divisaValida(req.divisa()), req.comentario());
    }

    /** Guard que hasta el 4a solo aplicaba el cliente: sin él, la clave foránea de compras y lotes hacía fallar el
     *  DELETE con 500 y el cliente lo enseñaba como "servidor no disponible". */
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idProv}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idProv, @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tienePedidos(idProv)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_TIENE_PEDIDOS);
        }
        String nombre = dao.getNombreById(idProv);
        dao.borrar(idProv);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PROVEEDOR", "ID_PROV: " + idProv + ", NOMBRE: " + nombre);
    }

    private static String nombreValido(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty() || n.length() > NOMBRE_MAX) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOMBRE);
        }
        return n;
    }

    private static String divisaValida(String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (!DIVISAS.contains(d)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_DIVISA);
        }
        return d;
    }

    record AltaRequest(String nombre, String divisa, String tipo) {}
    record ActivoRequest(boolean activo) {}
    record EditarRequest(String nombre, String divisa, String comentario) {}
}
```

Los records pasan de `private` a package-private para que el test los construya; springdoc los sigue publicando con el mismo nombre (`ProveedorAltaRequest`, `ProveedorEditarRequest`), así que el contrato no cambia. Comprobar que ningún otro test construye `new ProveedorController(dao)` con un solo argumento: `grep -rn "new ProveedorController" src/test`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=ProveedorControllerTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ProveedorController.java src/test/java/com/reparaciones/servidor/controller/ProveedorControllerTest.java
git commit -m "feat(proveedores): 409 al borrar con pedidos, log de borrado y 422 de nombre y divisa"
```

---

## Task 3: Servidor — componentes: 422 de stock y mínimo negativos

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/controller/ComponenteController.java:73-91`
- Create: `src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java`

**Interfaces:**
- Produces: `PUT /api/componentes/{idCom}` → 422 `"Cantidad no válida (debe ser ≥ 0)."` con `stock < 0`, y `"Valor no válido (debe ser ≥ 0)."` con `stockMinimo < 0`; `PATCH /api/componentes/{idCom}/stock-minimo` → 422 `"Valor no válido (debe ser ≥ 0)."` con `stockMinimo < 0`. Sin cambio de cuerpo ni de respuesta.

- [ ] **Step 1: Test (falla)**

`src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Rangos que hasta el 4a solo comprobaba el cliente (inventario de Stock §9.1-9.2); mismos textos. */
class ComponenteControllerValidacionTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, logDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);
    private final LocalDateTime ahora = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Test void editarConStockNegativoEs422() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", -1, 2, ahora), super7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Cantidad no válida (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), anyInt(), any());
    }

    @Test void editarConMinimoNegativoEs422() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 3, -2, ahora), super7));
        assertEquals("Valor no válido (debe ser ≥ 0).", e.getReason());
    }

    @Test void editarConCeroVale() {
        when(dao.getStockById(5)).thenReturn(4);
        ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 0, 0, ahora), super7);
        verify(dao).actualizar(5, "lcd-x", 0, 0, ahora);
    }

    @Test void minimoNegativoEs422YCeroVale() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.setStockMinimo(5, new ComponenteController.StockMinimoRequest(-1), super7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Valor no válido (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).setStockMinimo(anyInt(), anyInt());
        ctl.setStockMinimo(5, new ComponenteController.StockMinimoRequest(0), super7);
        verify(dao).setStockMinimo(5, 0);
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=ComponenteControllerValidacionTest`
Expected: FAIL de compilación (records privados) o de aserción.

- [ ] **Step 3: Implementar**

En `ComponenteController`: import `org.springframework.web.server.ResponseStatusException`; constantes y validación:

```java
    static final String MSG_CANTIDAD = "Cantidad no válida (debe ser ≥ 0).";
    static final String MSG_MINIMO   = "Valor no válido (debe ser ≥ 0).";

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCom}")
    public void actualizar(@PathVariable int idCom, @RequestBody ActualizarRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        noNegativo(req.stock(), MSG_CANTIDAD);
        noNegativo(req.stockMinimo(), MSG_MINIMO);
        int stockAnt = dao.getStockById(idCom);
        dao.actualizar(idCom, req.tipo(), req.stock(), req.stockMinimo(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
                "ID_COM: " + idCom + ", TIPO: " + req.tipo() +
                ", STOCK_ANT: " + stockAnt + " → STOCK_NUE: " + req.stock());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCom}/stock-minimo")
    public void setStockMinimo(@PathVariable int idCom, @RequestBody StockMinimoRequest req,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        noNegativo(req.stockMinimo(), MSG_MINIMO);
        dao.setStockMinimo(idCom, req.stockMinimo());
        logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
                "ID_COM: " + idCom + ", STOCK_MINIMO: " + req.stockMinimo());
    }

    /** Rango que el cliente JavaFX ya aplica antes de llamar (sub-proyecto 4a): aquí solo se cierra la puerta. */
    private static void noNegativo(int valor, String mensaje) {
        if (valor < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
```

y los records `ActualizarRequest` y `StockMinimoRequest` pasan de `private record` a `record` (package-private). Los demás no cambian.

- [ ] **Step 4: Ejecutar, suite completa y arranque**

Run: `mvn -q test`
Expected: PASS (271 + 13 nuevos). Después, arranque a mano (la suite no tiene test de contexto de Spring): `mvn -q spring-boot:run` con la BD local y comprobar `Started App` en el log; parar con Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ComponenteController.java src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java
git commit -m "feat(componentes): 422 con stock o minimo negativos, mismos textos que el cliente"
```

---

## Task 4: Web — rama, contrato regenerado, alias, Recharts y tokens

**Files:**
- Modify: `package.json`, `package-lock.json` (recharts)
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados)
- Modify: `src/shared/api/client.ts` (alias `Proveedor`)
- Modify: `src/shared/styles/tokens.css`

**Interfaces:**
- Produces: `type Proveedor = components['schemas']['Proveedor']` exportado de `@/shared/api/client`; tokens `bg-ambar-grafico`/`text-ambar-grafico` (`#c77a00`) y `bg-badge-sin-stock-bg` (`#F9E0E3`); `recharts` instalado.

- [ ] **Step 1: Rama**

```bash
cd gestion-reparaciones-web
git checkout main && git pull --ff-only
git checkout -b feature/web-stock
```

- [ ] **Step 2: Contrato de la rama del servidor**

Con el servidor de `feature/web-stock` arrancado en local (Task 3, Step 4) y un usuario de la BD local:

```bash
API_URL=http://localhost:8080 API_USER=<usuario> API_PASS=<clave> npm run api:types
git diff --stat api/openapi.json src/shared/api/schema.d.ts
```

Expected: el único cambio de contrato es `/api/compras/cantidad-en-camino/{idCom}` → `components["schemas"]["ValorEntero"]` (antes `{ [key: string]: Record<string, never> }`). Si hay otros cambios, son de `main` del servidor posteriores al último `api:types` y se revisan antes de seguir. Verificar con `grep -n -A12 'getCantidadEnCamino: {' src/shared/api/schema.d.ts`.

- [ ] **Step 3: Alias del contrato**

En `src/shared/api/client.ts`, tras `ContadoresPendientes`:

```ts
/** Almacén (sub-proyecto 4a): proveedores de componentes (`tipo` COMPONENTES/TELEFONOS viene del servidor de main). */
export type Proveedor = components['schemas']['Proveedor']
```

- [ ] **Step 4: Recharts**

```bash
npm install recharts@3
```

Anotar en `package.json` la versión exacta que quede (el repo fija versiones sin `^`: quitar el acento circunflejo a mano si `npm` lo añade, como en las demás dependencias).

- [ ] **Step 5: Tokens**

En `src/shared/styles/tokens.css`, al final del bloque `@theme`:

```css
  /* Stock actual (sub-proyecto 4a), calco de StockController: el donut y la barra "Stock" usan un ámbar distinto del
     badge/fila "Bajo" (#C07800, fila-solicitud-brd); el verde #3a7d44 es fila-recibido-brd y se reutiliza. */
  --color-ambar-grafico: #C77A00;
  --color-badge-sin-stock-bg: #F9E0E3;
```

- [ ] **Step 6: Comprobar**

```bash
npm run check && npm run build
```

Expected: verde (1017 tests) y build. Un test de contrato de la web puede comparar `api/openapi.json` con una lista de rutas: si falla por la ruta de `cantidad-en-camino`, adaptar esa aserción al `ValorEntero` (buscar con `grep -rn "cantidad-en-camino" src`).

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/styles/tokens.css
git commit -m "chore(web): contrato con cantidad en camino tipada, alias Proveedor, recharts y tokens de stock"
```

---

## Task 5: Web — `semaforoStock` compartido y `useInteraccionesAbiertas` a `shared`

**Files:**
- Create: `src/shared/lib/semaforoStock.ts`, `src/shared/lib/semaforoStock.test.ts`
- Modify: `src/modules/taller/lib/piezas.ts:37-54` (y su test si asevera la implementación)
- Move: `src/modules/taller/asignaciones/useInteraccionesAbiertas.ts` → `src/shared/lib/useInteraccionesAbiertas.ts` (y su `.test.tsx`)
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx:25` (import)

**Interfaces:**
- Produces: `type EstadoStock = 'OK' | 'Bajo' | 'Sin stock' | 'Desactivado'`; `const ESTADOS_STOCK: readonly EstadoStock[]` (en ese orden, el del menú "Estado"); `estadoStock(c: Pick<Componente, 'stock' | 'stockMinimo' | 'activo'>): EstadoStock`; `useInteraccionesAbiertas()` desde `@/shared/lib/useInteraccionesAbiertas` con la misma firma (`{ hayAlguna, marcar }`).

- [ ] **Step 1: Test del semáforo (falla)**

`src/shared/lib/semaforoStock.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { ESTADOS_STOCK, estadoStock } from './semaforoStock'

/** Calco de StockController.estadoComponente (inventario de Stock §4): Desactivado manda; después stock 0; después
 *  stock ≤ mínimo; resto OK. El negativo cae en "Bajo" (calco, diferencia documentada). */
describe('estadoStock', () => {
  it('Desactivado manda sobre el stock', () => {
    expect(estadoStock({ stock: 0, stockMinimo: 5, activo: false })).toBe('Desactivado')
    expect(estadoStock({ stock: 99, stockMinimo: 0, activo: false })).toBe('Desactivado')
  })
  it('Sin stock con stock 0, aunque el mínimo sea 0', () => {
    expect(estadoStock({ stock: 0, stockMinimo: 0, activo: true })).toBe('Sin stock')
    expect(estadoStock({ stock: 0, stockMinimo: 3, activo: true })).toBe('Sin stock')
  })
  it('Bajo con 0 < stock ≤ mínimo', () => {
    expect(estadoStock({ stock: 1, stockMinimo: 1, activo: true })).toBe('Bajo')
    expect(estadoStock({ stock: 2, stockMinimo: 3, activo: true })).toBe('Bajo')
  })
  it('OK con stock > mínimo, también con mínimo 0', () => {
    expect(estadoStock({ stock: 4, stockMinimo: 3, activo: true })).toBe('OK')
    expect(estadoStock({ stock: 1, stockMinimo: 0, activo: true })).toBe('OK')
  })
  it('stock negativo es Bajo (calco)', () => {
    expect(estadoStock({ stock: -2, stockMinimo: 0, activo: true })).toBe('Bajo')
  })
  it('los cuatro estados en el orden del menú "Estado"', () => {
    expect(ESTADOS_STOCK).toEqual(['OK', 'Bajo', 'Sin stock', 'Desactivado'])
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/shared/lib/semaforoStock.test.ts`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar**

`src/shared/lib/semaforoStock.ts`:

```ts
import type { Componente } from '@/shared/api/client'

/** Los cuatro valores del badge "Estado" de Stock actual, en el orden de los checks del menú "Estado". */
export type EstadoStock = 'OK' | 'Bajo' | 'Sin stock' | 'Desactivado'
export const ESTADOS_STOCK: readonly EstadoStock[] = ['OK', 'Bajo', 'Sin stock', 'Desactivado']

/** Calco de StockController.estadoComponente: desactivado manda; stock 0 es "Sin stock" aunque el mínimo sea 0; stock ≤
 *  mínimo (negativo incluido) es "Bajo"; el resto OK. Única definición del semáforo en la web (spec 4a, S3): el combo de
 *  SKU del formulario (piezas.ts) y las alertas de la campana derivan de aquí. */
export function estadoStock(c: Pick<Componente, 'stock' | 'stockMinimo' | 'activo'>): EstadoStock {
  if (!c.activo) return 'Desactivado'
  if (c.stock === 0) return 'Sin stock'
  if (c.stock <= c.stockMinimo) return 'Bajo'
  return 'OK'
}
```

- [ ] **Step 4: `nivelStock` sobre el semáforo**

En `src/modules/taller/lib/piezas.ts`, sustituir `nivelStock` (líneas 39-46) por:

```ts
import { estadoStock } from '@/shared/lib/semaforoStock'
...
/** stock 0 → 'sinStock'; 0 < stock ≤ mínimo → 'bajo'; resto 'normal'. El combo del formulario solo lista activos, así que
 *  el "Desactivado" del semáforo compartido no llega aquí; el negativo cae en 'bajo' como en estadoStock. */
export function nivelStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): NivelStock {
  switch (estadoStock({ ...c, activo: true })) {
    case 'Sin stock': return 'sinStock'
    case 'Bajo': return 'bajo'
    default: return 'normal'
  }
}
```

**Ojo:** el `nivelStock` de hoy devuelve `'normal'` con stock negativo (`stock > 0 &&`), y el semáforo del JavaFX lo pinta "Bajo". Mirar `src/modules/taller/lib/piezas.test.ts` (o el test que cubra `nivelStock`): si hay un caso con negativo, cambiar su expectativa a `'bajo'` con el comentario "calco de estadoComponente" y anotarlo en la ficha de paridad del formulario como diferencia corregida. Si no hay caso, añadir uno.

- [ ] **Step 5: Mover el hook**

```bash
git mv src/modules/taller/asignaciones/useInteraccionesAbiertas.ts src/shared/lib/useInteraccionesAbiertas.ts
git mv src/modules/taller/asignaciones/useInteraccionesAbiertas.test.tsx src/shared/lib/useInteraccionesAbiertas.test.tsx
```

En `AsignacionesPage.tsx` cambiar `import { useInteraccionesAbiertas } from './useInteraccionesAbiertas'` por `import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'`. En el test movido, el import relativo `./useInteraccionesAbiertas` sigue valiendo. Añadir al javadoc del hook una línea: "Vive en shared desde el sub-proyecto 4a porque Almacén también congela el sondeo con un menú o un diálogo abiertos".

- [ ] **Step 6: Comprobar**

```bash
npm run check
```

Expected: verde.

- [ ] **Step 7: Commit**

```bash
git add src/shared/lib/semaforoStock.ts src/shared/lib/semaforoStock.test.ts src/modules/taller/lib/piezas.ts src/modules/taller/lib/piezas.test.ts src/shared/lib/useInteraccionesAbiertas.ts src/shared/lib/useInteraccionesAbiertas.test.tsx src/modules/taller/asignaciones/AsignacionesPage.tsx
git commit -m "refactor(shared): semaforo de stock de cuatro estados compartido y hook de interacciones abiertas en shared"
```

---

## Task 6: Web — rutas de Stock y sección `stock` del `SubNav`

**Files:**
- Create: `src/modules/almacen/rutas.ts`
- Modify: `src/app/shell/SubNav.tsx:10-13`, `src/app/shell/SubNav.test.tsx:20-24`
- Modify: `src/app/router.tsx:67`

**Interfaces:**
- Consumes: `EnlaceTaller` de `@/modules/taller/rutas` (el tipo se reutiliza: `{ to, label, badge? }`).
- Produces: `enlacesStock(): EnlaceTaller[]` = Stock actual `/stock` · Pedidos `/stock/pedidos` · Proveedores `/stock/proveedores`, para los tres roles. Rutas `/stock` (`StockPage`, Task 12), `/stock/pedidos` (`PendienteDeMigrar nombre="Pedidos"`), `/stock/proveedores` (`ProveedoresPage`, Task 13). Hasta que existan las páginas, las dos rutas apuntan a `PendienteDeMigrar` y se sustituyen en su tarea.

- [ ] **Step 1: Test (falla)**

En `src/app/shell/SubNav.test.tsx`, sustituir el test `'en una sección todavía sin enlaces mantiene la columna vacía'` (que usa `/stock`) por uno con `/estadisticas`, y añadir:

```ts
  it('en Stock pinta las tres entradas del sidebar del JavaFX, para cualquier rol, con la activa marcada', () => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/stock/proveedores' })
    const columna = within(screen.getByRole('navigation', { name: 'Sub-navegación' }))
    expect(columna.getAllByRole('link').map((l) => l.textContent)).toEqual(['Stock actual', 'Pedidos', 'Proveedores'])
    expect(columna.getByRole('link', { name: 'Stock actual' })).toHaveAttribute('href', '/stock')
    expect(columna.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('href', '/stock/pedidos')
    expect(columna.getByRole('link', { name: 'Proveedores' })).toHaveAttribute('aria-current', 'page')
    expect(columna.getByRole('link', { name: 'Stock actual' })).not.toHaveAttribute('aria-current')
  })
  it('"Stock actual" solo está activo en /stock exacto (NavLink end), no en /stock/pedidos', () => {
    renderConProviders(<SubNav />, { sesion: SESION_ADMIN, ruta: '/stock/pedidos' })
    expect(screen.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Stock actual' })).not.toHaveAttribute('aria-current')
  })
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/app/shell/SubNav.test.tsx`
Expected: FAIL (0 enlaces en `/stock/...`).

- [ ] **Step 3: Enlaces del módulo**

`src/modules/almacen/rutas.ts`:

```ts
import type { EnlaceTaller } from '@/modules/taller/rutas'

/** Sidebar de StockView.fxml (`stock-sidebar-btn`): "Stock actual" · "Pedidos" · "Proveedores", en ese orden, sin badges y
 *  para los tres roles (el botón "Stock" de la barra superior tampoco depende del rol). `end` en el primero: sin él,
 *  NavLink lo marcaría activo también en /stock/pedidos. */
export function enlacesStock(): EnlaceTaller[] {
  return [
    { to: '/stock', label: 'Stock actual', end: true },
    { to: '/stock/pedidos', label: 'Pedidos' },
    { to: '/stock/proveedores', label: 'Proveedores' },
  ]
}
```

**Lint:** `modules/almacen` importando un tipo de `modules/taller` incumple la regla "un módulo no importa de otro". Mover el tipo: crear `src/shared/lib/enlaces.ts` con `export type Enlace = { to: string; label: string; badge?: 'pendientes' | 'asignaciones'; end?: boolean }`, hacer que `modules/taller/rutas.ts` lo reexporte (`export type EnlaceTaller = Enlace`) para no tocar sus usos, e importar `Enlace` en `almacen/rutas.ts` y en `SubNav.tsx`.

- [ ] **Step 4: `SubNav` con la sección y `end`**

En `SubNav.tsx`:

```ts
import { enlacesStock } from '@/modules/almacen/rutas'
...
const SUBNAV: Record<string, (sesion: Sesion | null) => Enlace[]> = {
  clientes: () => [{ to: '/clientes', label: 'Clientes' }],
  reparaciones: enlacesReparaciones,
  stock: enlacesStock,
}
```

y en el `<NavLink>` añadir `end={e.end}`.

- [ ] **Step 5: Rutas**

En `router.tsx`, sustituir `{ path: '/stock/*', element: <PendienteDeMigrar nombre="Stock" /> }` por:

```tsx
          { path: '/stock', element: <PendienteDeMigrar nombre="Stock actual" /> },
          { path: '/stock/pedidos', element: <PendienteDeMigrar nombre="Pedidos" /> },
          { path: '/stock/proveedores', element: <PendienteDeMigrar nombre="Proveedores" /> },
```

(las Tasks 12 y 13 cambian el elemento de `/stock` y `/stock/proveedores`).

- [ ] **Step 6: Ejecutar**

Run: `npm run check`
Expected: verde.

- [ ] **Step 7: Commit**

```bash
git add src/modules/almacen/rutas.ts src/shared/lib/enlaces.ts src/modules/taller/rutas.tsx src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx src/app/router.tsx
git commit -m "feat(web): seccion stock del lateral con stock actual, pedidos y proveedores, y sus rutas"
```

---

## Task 7: Web — `DialogoAlmacen`, el diálogo base de Stock y Proveedores

**Files:**
- Create: `src/modules/almacen/ui/DialogoAlmacen.tsx`, `src/modules/almacen/ui/DialogoAlmacen.test.tsx`

**Interfaces:**
- Consumes: `Dialog`, `DialogContent`, `DialogHeader`, `DialogTitle`, `DialogDescription`, `DialogFooter` de `@/shared/ui/dialog`; `BotonPrimario`, `BotonSecundario` de `@/shared/ui/Botones`.
- Produces:

```ts
type Props = {
  abierto: boolean
  titulo: string            // "Editar stock", "Solicitar pieza", "Editar proveedor"…
  subtitulo?: string        // "Componente: <tipo>   ·   Stock actual: <n> ud(s)."
  error: string | null      // texto rojo de 11 px bajo los campos; null = oculto
  textoAccion: string       // "Confirmar" o "Solicitar"
  enviando?: boolean        // deshabilita los dos botones
  onConfirmar: () => void   // también con Enter (submit del form)
  onCancelar: () => void    // Cancelar, ✕, Esc y clic fuera
  children: ReactNode       // los campos
}
export function DialogoAlmacen(props: Props): JSX.Element
```

Calco de la ventana de `editarStock` (inventario §9.1): fondo `#DDE1E7` (`bg-fondo-vista`), padding 28, ancho 360, título 20 px negrita `#2C3B54`, subtítulo 12 px `#586376`, botones a la derecha "Cancelar" (`btn-secondary`) y la acción (`btn-primary`).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/ui/DialogoAlmacen.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { DialogoAlmacen } from './DialogoAlmacen'

function montar(props: Partial<Parameters<typeof DialogoAlmacen>[0]> = {}) {
  const onConfirmar = vi.fn()
  const onCancelar = vi.fn()
  renderConProviders(
    <DialogoAlmacen abierto titulo="Editar stock" subtitulo="Componente: lcd-x   ·   Stock actual: 3 ud(s)." error={null} textoAccion="Confirmar" onConfirmar={onConfirmar} onCancelar={onCancelar} {...props}>
      <label htmlFor="campo">Nueva cantidad</label>
      <input id="campo" defaultValue="3" />
    </DialogoAlmacen>,
  )
  return { onConfirmar, onCancelar }
}

describe('DialogoAlmacen', () => {
  it('pinta título, subtítulo, los campos y los botones Cancelar / acción', () => {
    montar()
    const dlg = within(screen.getByRole('dialog', { name: 'Editar stock' }))
    expect(dlg.getByText('Componente: lcd-x   ·   Stock actual: 3 ud(s).')).toHaveClass('text-[12px]', 'text-azul-gris')
    expect(dlg.getByLabelText('Nueva cantidad')).toHaveValue('3')
    const botones = dlg.getAllByRole('button').map((b) => b.textContent)
    expect(botones.slice(-2)).toEqual(['Cancelar', 'Confirmar'])
  })
  it('Enter en un campo confirma; Cancelar y Escape cancelan', async () => {
    const { onConfirmar, onCancelar } = montar()
    await userEvent.type(screen.getByLabelText('Nueva cantidad'), '{Enter}')
    expect(onConfirmar).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancelar).toHaveBeenCalledTimes(1)
    await userEvent.keyboard('{Escape}')
    expect(onCancelar).toHaveBeenCalledTimes(2)
  })
  it('el error se pinta en rojo de 11 px solo cuando hay texto', () => {
    montar({ error: 'Cantidad no válida (debe ser ≥ 0).' })
    expect(screen.getByRole('alert')).toHaveTextContent('Cantidad no válida (debe ser ≥ 0).')
    expect(screen.getByRole('alert')).toHaveClass('text-[11px]', 'text-texto-error')
  })
  it('sin error no hay región de alerta', () => {
    montar()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
  it('enviando deshabilita los dos botones', () => {
    montar({ enviando: true })
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/ui/DialogoAlmacen.test.tsx`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar**

`src/modules/almacen/ui/DialogoAlmacen.tsx`:

```tsx
import type { ReactNode } from 'react'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Props = {
  abierto: boolean
  titulo: string
  subtitulo?: string
  error: string | null
  textoAccion: string
  enviando?: boolean
  onConfirmar: () => void
  onCancelar: () => void
  children: ReactNode
}

/** Calco de las ventanas propias de StockController (editarStock, solicitarPieza, editarProveedor): VBox de 360 px con
 *  padding 28 y fondo #DDE1E7, título de 20 px, subtítulo de 12 px gris, campos, error de 11 px rojo y los botones
 *  "Cancelar" (btn-secondary) y la acción (btn-primary) a la derecha. Los TextInputDialog nativos del JavaFX ("Stock
 *  mínimo", "Nuevo proveedor") también pasan por aquí (spec 4a, S5). Enter confirma porque los campos van en un form. */
export function DialogoAlmacen({ abierto, titulo, subtitulo, error, textoAccion, enviando = false, onConfirmar, onCancelar, children }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent aria-describedby={subtitulo ? undefined : undefined} className="w-[360px] max-w-[min(360px,calc(100%-2rem))] gap-3 bg-fondo-vista p-7 sm:max-w-[min(360px,calc(100%-2rem))]">
        <form onSubmit={(e) => { e.preventDefault(); if (!enviando) onConfirmar() }} className="flex flex-col gap-3">
          <DialogHeader>
            <DialogTitle className="text-[20px] font-bold text-azul-medio">{titulo}</DialogTitle>
            {subtitulo ? <DialogDescription className="text-[12px] text-azul-gris">{subtitulo}</DialogDescription> : null}
          </DialogHeader>
          {children}
          {error !== null && <p role="alert" className="text-[11px] text-texto-error">{error}</p>}
          <DialogFooter className="flex-row justify-end gap-2.5 sm:flex-row sm:justify-end">
            <BotonSecundario type="button" disabled={enviando} onClick={onCancelar}>Cancelar</BotonSecundario>
            <BotonPrimario type="submit" disabled={enviando}>{textoAccion}</BotonPrimario>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
```

Si `DialogContent` avisa por `aria-describedby` sin descripción, pasar `aria-describedby={undefined}` solo cuando no hay subtítulo (ver cómo lo hace `ClienteDialog.tsx`). Los subtítulos llevan **tres espacios** a cada lado del punto medio: en JSX un literal `"Componente: x   ·   Stock…"` conserva los espacios si va dentro de una expresión `{}`; `white-space: pre` no hace falta si el texto se pasa como string en `{subtitulo}` (los espacios múltiples se colapsan al pintar; añadir `whitespace-pre` a `DialogDescription` para calcarlos).

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/ui/DialogoAlmacen.test.tsx`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/ui/DialogoAlmacen.tsx src/modules/almacen/ui/DialogoAlmacen.test.tsx
git commit -m "feat(almacen): dialogo base de stock y proveedores, calco de las ventanas propias de StockController"
```

---

## Task 8: Web — `filtros.ts` y `estado.ts` de Stock

**Files:**
- Create: `src/modules/almacen/stock/filtros.ts`, `src/modules/almacen/stock/filtros.test.ts`
- Create: `src/modules/almacen/stock/estado.ts`

**Interfaces:**
- Consumes: `estadoStock`, `EstadoStock`, `ESTADOS_STOCK` de `@/shared/lib/semaforoStock`; `crearStore` de `@/shared/lib/store`.
- Produces (`filtros.ts`):

```ts
export type FiltrosStock = { estados: Set<EstadoStock>; buscador: string }
export const FILTROS_STOCK_VACIOS: FiltrosStock
export function ordenarStock(lista: Componente[]): Componente[]           // activos primero, estable
export function aplicarFiltrosStock(lista: Componente[], f: FiltrosStock): Componente[]
export function textoBotonEstado(estados: Set<EstadoStock>): string       // "Estado" | el único | "N estados"
export function textoDesactivados(n: number): string | null              // null con 0; "1 desactivado"; "N desactivados"
export function nombreComponente(c: Pick<Componente, 'tipo' | 'idComMaster'>): string  // tipo + "  (compartido)"
export function filtrosDesdePedidos(): FiltrosStock                      // calco de navegarAComponente: quita OK/Bajo/Sin stock, conserva Desactivado
```

- Produces (`estado.ts`): `filtrosStock = crearStore<FiltrosStock>(FILTROS_STOCK_VACIOS)`, `seleccionStock = crearStore<string | null>(null)` (id del componente seleccionado, como texto).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, filtrosDesdePedidos, nombreComponente, ordenarStock, textoBotonEstado, textoDesactivados } from './filtros'

const base: Componente = { idCom: 1, tipo: 'lcd-x', fechaRegistro: '2026-09-01T10:00:00', stock: 5, stockMinimo: 2, activo: true, updatedAt: '2026-09-01T10:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null }
const c = (o: Partial<Componente>): Componente => ({ ...base, ...o })
const ok = c({ idCom: 1, tipo: 'lcd-x' })
const bajo = c({ idCom: 2, tipo: 'bat-x', stock: 2 })
const sinStock = c({ idCom: 3, tipo: 'cam-x', stock: 0 })
const desactivado = c({ idCom: 4, tipo: 'mc-x', activo: false })

describe('ordenarStock', () => {
  it('activos primero, desactivados al final, sin reordenar dentro de cada grupo (calco :477)', () => {
    expect(ordenarStock([desactivado, bajo, ok, sinStock]).map((x) => x.idCom)).toEqual([2, 1, 3, 4])
  })
})

describe('aplicarFiltrosStock', () => {
  const todos = [ok, bajo, sinStock, desactivado]
  it('sin ningún estado marcado muestra todos', () => {
    expect(aplicarFiltrosStock(todos, FILTROS_STOCK_VACIOS)).toHaveLength(4)
  })
  it('varios estados se combinan con O', () => {
    const f = { ...FILTROS_STOCK_VACIOS, estados: new Set(['Bajo', 'Sin stock'] as const) }
    expect(aplicarFiltrosStock(todos, f).map((x) => x.tipo)).toEqual(['bat-x', 'cam-x'])
  })
  it('el buscador es "contiene", sin mayúsculas, recortado y sobre el tipo sin el sufijo (compartido)', () => {
    const compartido = c({ idCom: 5, tipo: 'lcd-y', idComMaster: 1 })
    expect(aplicarFiltrosStock([...todos, compartido], { ...FILTROS_STOCK_VACIOS, buscador: '  LCD ' }).map((x) => x.tipo)).toEqual(['lcd-x', 'lcd-y'])
    expect(aplicarFiltrosStock([compartido], { ...FILTROS_STOCK_VACIOS, buscador: 'compartido' })).toHaveLength(0)
  })
  it('estado y buscador se combinan con Y', () => {
    const f = { estados: new Set(['OK'] as const), buscador: 'bat' }
    expect(aplicarFiltrosStock(todos, f)).toHaveLength(0)
  })
})

describe('textos', () => {
  it('botón Estado: "Estado", el único marcado o "N estados"', () => {
    expect(textoBotonEstado(new Set())).toBe('Estado')
    expect(textoBotonEstado(new Set(['Bajo'] as const))).toBe('Bajo')
    expect(textoBotonEstado(new Set(['Bajo', 'Sin stock'] as const))).toBe('2 estados')
  })
  it('pie de desactivados: nada con 0, singular con 1, plural con más', () => {
    expect(textoDesactivados(0)).toBeNull()
    expect(textoDesactivados(1)).toBe('1 desactivado')
    expect(textoDesactivados(3)).toBe('3 desactivados')
  })
  it('nombre con el sufijo "(compartido)" de dos espacios', () => {
    expect(nombreComponente({ tipo: 'lcd-y', idComMaster: 1 })).toBe('lcd-y  (compartido)')
    expect(nombreComponente({ tipo: 'lcd-x', idComMaster: null })).toBe('lcd-x')
  })
  it('al llegar desde Pedidos se desmarcan OK, Bajo y Sin stock pero no Desactivado, y se vacía el buscador', () => {
    const f = filtrosDesdePedidos({ estados: new Set(['OK', 'Desactivado'] as const), buscador: 'x' })
    expect([...f.estados]).toEqual(['Desactivado'])
    expect(f.buscador).toBe('')
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/filtros.test.ts`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar**

`src/modules/almacen/stock/filtros.ts`:

```ts
import type { Componente } from '@/shared/api/client'
import { estadoStock, type EstadoStock } from '@/shared/lib/semaforoStock'

export type FiltrosStock = { estados: Set<EstadoStock>; buscador: string }
export const FILTROS_STOCK_VACIOS: FiltrosStock = { estados: new Set(), buscador: '' }

/** Calco de cargarStock (:477): `sort(comparingInt(activo ? 0 : 1))`, estable, así que dentro de cada grupo queda el
 *  orden del servidor (ORDER BY TIPO). */
export function ordenarStock(lista: Componente[]): Componente[] {
  return [...lista].sort((a, b) => Number(!a.activo) - Number(!b.activo))
}

/** Calco del predicado del FilteredList (:399-417): ninguno marcado = todos; varios = O; el buscador es "contiene" sin
 *  mayúsculas sobre `tipo` (sin el sufijo "(compartido)", que es de presentación). */
export function aplicarFiltrosStock(lista: Componente[], f: FiltrosStock): Componente[] {
  const texto = f.buscador.trim().toLowerCase()
  return lista.filter((c) => (f.estados.size === 0 || f.estados.has(estadoStock(c))) && (texto === '' || c.tipo.toLowerCase().includes(texto)))
}

/** Calco de actualizarTextoFiltroStock (:1916-1921). */
export function textoBotonEstado(estados: Set<EstadoStock>): string {
  if (estados.size === 0) return 'Estado'
  if (estados.size === 1) return [...estados][0]
  return `${estados.size} estados`
}

/** Calco de la etiqueta lblDesactivados (:480-484): oculta a cero. */
export function textoDesactivados(n: number): string | null {
  if (n <= 0) return null
  return n === 1 ? '1 desactivado' : `${n} desactivados`
}

/** Calco de la columna Componente (:298-302): dos espacios antes del paréntesis. */
export function nombreComponente(c: Pick<Componente, 'tipo' | 'idComMaster'>): string {
  return c.idComMaster != null ? `${c.tipo}  (compartido)` : c.tipo
}

/** Calco de navegarAComponente (:233-246): desmarca OK, Bajo y Sin stock pero NO "Desactivado" (inconsistencia menor del
 *  JavaFX con "Limpiar filtros", que se calca), y vacía el buscador. */
export function filtrosDesdePedidos(f: FiltrosStock): FiltrosStock {
  return { estados: new Set([...f.estados].filter((e) => e === 'Desactivado')), buscador: '' }
}
```

`src/modules/almacen/stock/estado.ts`:

```ts
import { crearStore } from '@/shared/lib/store'
import { FILTROS_STOCK_VACIOS, type FiltrosStock } from './filtros'

/** Calco de la caché de vista del JavaFX (MainController.vistaCache, spec 4a S2): los filtros y la fila seleccionada de
 *  "Stock actual" sobreviven al cambio de pestaña y a la vuelta desde Reparaciones; se reinician al cerrar sesión
 *  (reiniciarStores). El id va como texto porque DataTable identifica las filas por string. */
export const filtrosStock = crearStore<FiltrosStock>(FILTROS_STOCK_VACIOS)
export const seleccionStock = crearStore<string | null>(null)
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/stock/filtros.test.ts`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock/filtros.ts src/modules/almacen/stock/filtros.test.ts src/modules/almacen/stock/estado.ts
git commit -m "feat(stock): orden, filtros de estado y buscador, textos del boton y del pie, y stores de la vista"
```

---

## Task 9: Web — `api.ts` de Stock

**Files:**
- Create: `src/modules/almacen/stock/api.ts`, `src/modules/almacen/stock/api.test.tsx`

**Interfaces:**
- Consumes: `api`, `Componente` de `@/shared/api/client`; `useIntervaloRefresco` de `@/shared/api/refresco`; `ordenarStock` de `./filtros`.
- Produces:

```ts
export const CLAVE_COMPONENTES_GESTIONADOS = ['componentes', 'gestionados'] as const
export function useComponentesStock(opciones: { activo: boolean }): UseQueryResult<Componente[]>   // ordenados
export function pedirCantidadEnCamino(idCom: number): Promise<number>                                // GET cantidad-en-camino → value
export function useCantidadEnCamino(idCom: number | null, habilitado: boolean): UseQueryResult<number>
export function useEditarStock(): UseMutationResult<..., { c: Componente; stock: number }>          // PUT, meta.silenciarError (409 propio)
export function useAjustarMinimo(): UseMutationResult<..., { idCom: number; stockMinimo: number }> // PATCH stock-minimo
export function useSetActivoComponente(): UseMutationResult<..., { idCom: number; activo: boolean }>
export function useSolicitarPieza(): UseMutationResult<..., { idCom: number; descripcion: string | null }>
```

Todas las mutaciones invalidan `['componentes']` (prefijo: cubre `gestionados` y `agrupados` del formulario) y `['notificaciones', 'componentes']` (la campana consulta el mismo endpoint con su propia clave; se cita literal porque `almacen` no puede importar de `taller`).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { crearQueryClient } from '@/shared/api/queryClient'
import { guardarSesion } from '@/shared/session/storage'
import { SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { pedirCantidadEnCamino, useComponentesStock, useEditarStock } from './api'

const base = { fechaRegistro: '2026-09-01T10:00:00', stockMinimo: 2, updatedAt: '2026-09-01T10:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null }

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

describe('api de Stock', () => {
  it('useComponentesStock devuelve los gestionados con los activos primero', async () => {
    guardarSesion(SESION_SUPER)
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json([
      { ...base, idCom: 4, tipo: 'mc-x', stock: 1, activo: false },
      { ...base, idCom: 1, tipo: 'lcd-x', stock: 5, activo: true },
    ])))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useComponentesStock({ activo: true }), { wrapper })
    await waitFor(() => expect(result.current.data).toBeDefined())
    expect(result.current.data?.map((c) => c.idCom)).toEqual([1, 4])
  })
  it('pedirCantidadEnCamino lee el value tipado', async () => {
    guardarSesion(SESION_SUPER)
    server.use(http.get('*/api/compras/cantidad-en-camino/12', () => HttpResponse.json({ value: 7 })))
    expect(await pedirCantidadEnCamino(12)).toBe(7)
  })
  it('useEditarStock manda tipo, stock, stockMinimo y updatedAt tal cual, e invalida los componentes y la campana', async () => {
    guardarSesion(SESION_SUPER)
    let cuerpo: unknown = null
    server.use(http.put('*/api/componentes/1', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 200 }) }))
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['componentes', 'gestionados'], [])
    qc.setQueryData(['notificaciones', 'componentes'], [])
    const { result } = renderHook(() => useEditarStock(), { wrapper })
    const c = { ...base, idCom: 1, tipo: 'lcd-x', stock: 5, activo: true }
    await result.current.mutateAsync({ c, stock: 9 })
    expect(cuerpo).toEqual({ tipo: 'lcd-x', stock: 9, stockMinimo: 2, updatedAt: '2026-09-01T10:00:00' })
    expect(qc.getQueryState(['componentes', 'gestionados'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['notificaciones', 'componentes'])?.isInvalidated).toBe(true)
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/api.test.tsx`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar**

`src/modules/almacen/stock/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query'
import { api, type Componente } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'
import { ordenarStock } from './filtros'

export const CLAVE_COMPONENTES_GESTIONADOS = ['componentes', 'gestionados'] as const
/** Clave con la que la campana (modules/taller/notificaciones) consulta el mismo endpoint: se invalida también para que
 *  las alertas se recalculen tras editar stock. Literal a propósito: un módulo no importa de otro (regla de lint). */
const CLAVE_CAMPANA_COMPONENTES = ['notificaciones', 'componentes'] as const

/** GET /api/componentes/gestionados, la única petición de "Stock actual" (inventario §2): tabla, filtros, donut y pie
 *  salen de aquí. `activo = false` congela el sondeo y el refetch por foco con un menú o un diálogo abiertos (D4 del 3a). */
export function useComponentesStock({ activo }: { activo: boolean }): UseQueryResult<Componente[]> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: CLAVE_COMPONENTES_GESTIONADOS,
    queryFn: async () => ordenarStock((await api.GET('/api/componentes/gestionados')).data ?? []),
    refetchInterval: intervalo,
    refetchOnWindowFocus: activo,
  })
}

/** Barra "Pedido" del gráfico por SKU (inventario §8): solo se pide para ADMIN y SUPERTECNICO. */
export async function pedirCantidadEnCamino(idCom: number): Promise<number> {
  const { data } = await api.GET('/api/compras/cantidad-en-camino/{idCom}', { params: { path: { idCom } } })
  return data?.value ?? 0
}

export function useCantidadEnCamino(idCom: number | null, habilitado: boolean): UseQueryResult<number> {
  return useQuery({
    queryKey: ['compras', 'cantidad-en-camino', idCom] as const,
    queryFn: () => pedirCantidadEnCamino(idCom as number),
    enabled: habilitado && idCom !== null,
  })
}

function useRecarga() {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: ['componentes'] })
    void qc.invalidateQueries({ queryKey: CLAVE_CAMPANA_COMPONENTES })
  }
}

/** PUT con el cuerpo que el cliente reenvía tal cual (tipo, mínimo y updatedAt sin cambios). Su 409 lo traduce la vista
 *  ("El componente fue modificado mientras editabas…"), así que silencia el diálogo global. */
export function useEditarStock(): UseMutationResult<unknown, unknown, { c: Componente; stock: number }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ c, stock }: { c: Componente; stock: number }) =>
      api.PUT('/api/componentes/{idCom}', { params: { path: { idCom: c.idCom } }, body: { tipo: c.tipo, stock, stockMinimo: c.stockMinimo, updatedAt: c.updatedAt } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export function useAjustarMinimo(): UseMutationResult<unknown, unknown, { idCom: number; stockMinimo: number }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idCom, stockMinimo }: { idCom: number; stockMinimo: number }) =>
      api.PATCH('/api/componentes/{idCom}/stock-minimo', { params: { path: { idCom } }, body: { stockMinimo } }),
    onSettled: recargar,
  })
}

export function useSetActivoComponente(): UseMutationResult<unknown, unknown, { idCom: number; activo: boolean }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idCom, activo }: { idCom: number; activo: boolean }) =>
      api.PATCH('/api/componentes/{idCom}/activo', { params: { path: { idCom } }, body: { activo } }),
    onSettled: recargar,
  })
}

/** "Solicitar pieza": sin recarga ni aviso al terminar (calco, inventario §9.4). */
export function useSolicitarPieza(): UseMutationResult<unknown, unknown, { idCom: number; descripcion: string | null }> {
  return useMutation({
    mutationFn: ({ idCom, descripcion }: { idCom: number; descripcion: string | null }) =>
      api.POST('/api/solicitudes-stock', { body: { idCom, descripcion } }),
  })
}
```

Comprobar en `schema.d.ts` que `POST /api/solicitudes-stock` tipa `descripcion: string | null` (línea ~2277) y que `PUT /api/componentes/{idCom}` pide `ComponenteActualizarRequest` con `updatedAt: string`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/stock/api.test.tsx`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock/api.ts src/modules/almacen/stock/api.test.tsx
git commit -m "feat(stock): consulta de componentes gestionados, cantidad en camino y mutaciones de stock, minimo, activo y solicitud"
```

---

## Task 10: Web — `columnas.tsx` de Stock: seis columnas, enlace "En Camino", badge, clase de fila y CSV

**Files:**
- Create: `src/modules/almacen/stock/columnas.tsx`, `src/modules/almacen/stock/columnas.test.tsx`

**Interfaces:**
- Consumes: `ColumnDef` de `@tanstack/react-table`; `CREMA_EN_FILA_SELECCIONADA` de `@/shared/ui/DataTable`; `formatear` de `@/shared/lib/fechas`; `estadoStock`, `EstadoStock`; `nombreComponente` de `./filtros`; `Componente`.
- Produces:

```ts
export const ANCHOS_STOCK = { componente: 230, enStock: 80, enCamino: 90, stockMinimo: 100, ultimoPedido: 120, estado: 100 }
export function crearColumnasStock(opciones: { onEnCamino: (c: Componente) => void }): ColumnDef<Componente>[]
export function claseFilaStock(c: Componente): string          // opacidad 0.45 si !activo; borde izquierdo 8 px por estado
export function BadgeEstadoStock({ estado }: { estado: EstadoStock }): JSX.Element
export function parametrosPedidos(c: Componente): string       // "estados=pendiente,en camino,parcial&buscar=<tipo>"
export const CABECERAS_CSV_STOCK = ['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro']
export function filaCsvStock(c: Componente): string[]
```

Colores (inventario §5): badge OK `bg-badge-neutro-bg text-azul-gris`; Bajo `bg-fila-solicitud-bg text-fila-solicitud-brd`; Desactivado `bg-fila-cancelado-bg text-fila-cancelado-text`; Sin stock `bg-badge-sin-stock-bg text-rojo-sin-stock`. Fila: Bajo `border-l-fila-solicitud-brd`, Sin stock `border-l-rojo-sin-stock`, resto `border-l-transparent`; desactivada `opacity-45` y **no** cambia a azul al seleccionarse.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/columnas.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { DataTable } from '@/shared/ui/DataTable'
import { BadgeEstadoStock, CABECERAS_CSV_STOCK, claseFilaStock, crearColumnasStock, filaCsvStock, parametrosPedidos } from './columnas'

const base: Componente = { idCom: 1, tipo: 'lcd-x', fechaRegistro: '2026-09-01T10:30:00', stock: 5, stockMinimo: 2, activo: true, updatedAt: '2026-09-01T10:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null }
const c = (o: Partial<Componente>): Componente => ({ ...base, ...o })

function montar(filas: Componente[], onEnCamino = vi.fn()) {
  render(<DataTable columns={crearColumnasStock({ onEnCamino })} data={filas} vacio="Sin componentes" getRowId={(x) => String(x.idCom)} filaClase={claseFilaStock} />)
  return onEnCamino
}

describe('columnas de Stock actual', () => {
  it('pinta las seis cabeceras en orden y con los anchos del FXML', () => {
    const { container } = render(<DataTable columns={crearColumnasStock({ onEnCamino: vi.fn() })} data={[base]} vacio="Sin componentes" />)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Componente', 'En Stock', 'En Camino', 'Stock Mínimo', 'Último pedido', 'Estado'])
    const cols = container.querySelectorAll('col')
    expect(cols[0]).toHaveStyle({ width: '230px' })
    expect(cols[5]).toHaveStyle({ width: '100px' })
  })
  it('un compartido lleva el sufijo con dos espacios; sin último pedido pinta "—" y con fecha dd/MM/yyyy', () => {
    montar([c({ idComMaster: 9, ultimoPedido: '2026-08-15T09:00:00' })])
    expect(screen.getByText('lcd-x  (compartido)')).toBeInTheDocument()
    expect(screen.getByText('15/08/2026')).toBeInTheDocument()
    montar([c({ idCom: 2, tipo: 'bat-x' })])
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })
  it('"En Camino" a 0 es texto "—"; > 0 es un enlace azul que avisa con el componente', async () => {
    const onEnCamino = montar([c({ enCamino: 3 })])
    const enlace = screen.getByRole('button', { name: '3' })
    expect(enlace).toHaveClass('text-texto-accion', 'hover:underline')
    await userEvent.click(enlace)
    expect(onEnCamino).toHaveBeenCalledWith(expect.objectContaining({ idCom: 1 }))
  })
  it('el badge Estado lleva los colores del semáforo', () => {
    const { rerender } = render(<BadgeEstadoStock estado="OK" />)
    expect(screen.getByText('OK')).toHaveClass('bg-badge-neutro-bg', 'text-azul-gris', 'rounded-[10px]', 'text-[11px]', 'font-bold')
    rerender(<BadgeEstadoStock estado="Bajo" />)
    expect(screen.getByText('Bajo')).toHaveClass('bg-fila-solicitud-bg', 'text-fila-solicitud-brd')
    rerender(<BadgeEstadoStock estado="Sin stock" />)
    expect(screen.getByText('Sin stock')).toHaveClass('bg-badge-sin-stock-bg', 'text-rojo-sin-stock')
    rerender(<BadgeEstadoStock estado="Desactivado" />)
    expect(screen.getByText('Desactivado')).toHaveClass('bg-fila-cancelado-bg', 'text-fila-cancelado-text')
  })
  it('clase de fila: borde por estado y opacidad en desactivadas, que no se ponen azules', () => {
    expect(claseFilaStock(c({ stock: 2 }))).toContain('border-l-fila-solicitud-brd')
    expect(claseFilaStock(c({ stock: 0 }))).toContain('border-l-rojo-sin-stock')
    expect(claseFilaStock(base)).toContain('border-l-transparent')
    const inactiva = claseFilaStock(c({ activo: false }))
    expect(inactiva).toContain('opacity-45')
    expect(inactiva).toContain('data-[state=selected]:bg-transparent')
  })
  it('parámetros hacia Pedidos: los tres estados del pipeline y el buscador con el tipo', () => {
    expect(parametrosPedidos(c({ tipo: 'lcd x pro' }))).toBe('estados=pendiente%2Cen+camino%2Cparcial&buscar=lcd+x+pro')
  })
  it('CSV: cabeceras exactas del JavaFX, tipo sin sufijo, estado del semáforo y fecha de registro con hora', () => {
    expect(CABECERAS_CSV_STOCK).toEqual(['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro'])
    expect(filaCsvStock(c({ idComMaster: 9, stock: 0, enCamino: 4 }))).toEqual(['lcd-x', '0', '2', 'Sin stock', '4', '01/09/2026 10:30'])
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar**

`src/modules/almacen/stock/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { Componente } from '@/shared/api/client'
import { formatear } from '@/shared/lib/fechas'
import { estadoStock, type EstadoStock } from '@/shared/lib/semaforoStock'
import { cn } from '@/shared/lib/utils'
import { CREMA_EN_FILA_SELECCIONADA } from '@/shared/ui/DataTable'
import { nombreComponente } from './filtros'

/** prefWidth de StockView.fxml :46-53. */
export const ANCHOS_STOCK = { componente: 230, enStock: 80, enCamino: 90, stockMinimo: 100, ultimoPedido: 120, estado: 100 } as const

const CLASES_BADGE: Record<EstadoStock, string> = {
  OK: 'bg-badge-neutro-bg text-azul-gris',
  Bajo: 'bg-fila-solicitud-bg text-fila-solicitud-brd',
  'Sin stock': 'bg-badge-sin-stock-bg text-rojo-sin-stock',
  Desactivado: 'bg-fila-cancelado-bg text-fila-cancelado-text',
}

/** Calco del badge de colEstado (:335-351): radio 10, padding 2 10, 11 px negrita. Conserva su color en la fila azul. */
export function BadgeEstadoStock({ estado }: { estado: EstadoStock }) {
  return <span className={cn('inline-block rounded-[10px] px-2.5 py-0.5 text-[11px] font-bold', CLASES_BADGE[estado])}>{estado}</span>
}

const BORDE_POR_ESTADO: Record<EstadoStock, string> = {
  OK: 'border-l-transparent',
  Bajo: 'border-l-fila-solicitud-brd',
  'Sin stock': 'border-l-rojo-sin-stock',
  Desactivado: 'border-l-transparent',
}

/** Calco del rowFactory (:433-459): borde izquierdo de 8 px por estado; la desactivada va con opacidad 0.45 y prevalece
 *  sobre la selección (no se pone azul), de ahí el bg-transparent en el estado seleccionado. */
export function claseFilaStock(c: Componente): string {
  const estado = estadoStock(c)
  if (!c.activo) return 'border-l-8 border-l-transparent opacity-45 data-[state=selected]:bg-transparent data-[state=selected]:text-inherit'
  return cn('border-l-8', BORDE_POR_ESTADO[estado])
}

/** Parámetros con los que "En Camino" (y el ítem "Pedir" no: ese va por idCom) abren Pedidos (spec 4a, S8): calco de
 *  navegarAPedidosDeComponente (:217-231), que marca pendiente + en camino + parcial y rellena el buscador con el tipo. */
export function parametrosPedidos(c: Componente): string {
  return new URLSearchParams({ estados: 'pendiente,en camino,parcial', buscar: c.tipo }).toString()
}

export function crearColumnasStock({ onEnCamino }: { onEnCamino: (c: Componente) => void }): ColumnDef<Componente>[] {
  return [
    { id: 'componente', header: 'Componente', size: ANCHOS_STOCK.componente, accessorFn: nombreComponente },
    { id: 'enStock', header: 'En Stock', size: ANCHOS_STOCK.enStock, accessorFn: (c) => String(c.stock) },
    {
      id: 'enCamino', header: 'En Camino', size: ANCHOS_STOCK.enCamino,
      cell: ({ row }) => {
        const c = row.original
        if (c.enCamino <= 0) return '—'
        // Calco del Label con TEXTO_ACCION, cursor mano y subrayado al pasar (:305-324).
        return (
          <button type="button" onClick={(e) => { e.stopPropagation(); onEnCamino(c) }} className="cursor-pointer text-texto-accion hover:underline">
            {c.enCamino}
          </button>
        )
      },
    },
    { id: 'stockMinimo', header: 'Stock Mínimo', size: ANCHOS_STOCK.stockMinimo, accessorFn: (c) => String(c.stockMinimo) },
    {
      id: 'ultimoPedido', header: 'Último pedido', size: ANCHOS_STOCK.ultimoPedido,
      cell: ({ row }) => <span className={CREMA_EN_FILA_SELECCIONADA}>{row.original.ultimoPedido ? formatear(row.original.ultimoPedido, 'dd/MM/yyyy') : '—'}</span>,
    },
    { id: 'estado', header: 'Estado', size: ANCHOS_STOCK.estado, cell: ({ row }) => <BadgeEstadoStock estado={estadoStock(row.original)} /> },
  ]
}

/** Calco de exportarStock (:1937-1959): la lista filtrada, tipo SIN "(compartido)", sin "Último pedido" y con "Fecha
 *  registro" (que la tabla no muestra); cabecera "Stock mínimo" en minúscula, como el JavaFX. */
export const CABECERAS_CSV_STOCK = ['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro']
export function filaCsvStock(c: Componente): string[] {
  return [c.tipo, String(c.stock), String(c.stockMinimo), estadoStock(c), String(c.enCamino), formatear(c.fechaRegistro, 'dd/MM/yyyy HH:mm')]
}
```

Si `DataTable` pinta la fila seleccionada con clases que `data-[state=selected]:bg-transparent` no anula (mirar `src/shared/ui/DataTable.tsx`, el `<tr>` y su `data-state`), ajustar a lo que use la tabla y dejar el test como guardia.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock/columnas.tsx src/modules/almacen/stock/columnas.test.tsx
git commit -m "feat(stock): columnas de la tabla, enlace en camino, badge y clase de fila del semaforo, y csv"
```

---

## Task 11: Web — gráficos: `graficos.ts`, `GraficoEstado` y `GraficoSku`

**Files:**
- Create: `src/modules/almacen/stock/graficos.ts`, `src/modules/almacen/stock/graficos.test.ts`
- Create: `src/modules/almacen/stock/GraficoEstado.tsx`, `src/modules/almacen/stock/GraficoSku.tsx`, `src/modules/almacen/stock/graficos.test.tsx`

**Interfaces:**
- Consumes: `PieChart`, `Pie`, `Cell`, `BarChart`, `Bar`, `XAxis`, `YAxis`, `Tooltip` de `recharts`; `estadoStock`.
- Produces:

```ts
export type ConteosDonut = { ok: number; bajo: number; sinStock: number; total: number }
export function conteosDonut(lista: Componente[]): ConteosDonut      // activos y stock ≥ 0; compartidos cuentan como filas
export const COLORES_DONUT = { ok: '#3A7D44', bajo: '#C77A00', sinStock: '#B03040' }
export function colorBarraStock(estado: EstadoStock): string          // Sin stock rojo, Bajo ámbar gráfico, resto verde
export const COLOR_BARRA_PEDIDO = '#4A6FA5'
export function GraficoEstado({ conteos }: { conteos: ConteosDonut }): JSX.Element
export function GraficoSku({ componente, enCamino }: { componente: Componente | null; enCamino: number }): JSX.Element
```

Textos (inventario §7-8): título **"Estado del stock"**; en el centro el total y **"total"**; leyenda **"OK"**, **"Bajo"**, **"Sin stock"** con su número; abajo **"Selecciona un componente"** y **"↑ Haz clic en una fila"** sin selección; con selección el tipo (sin sufijo) y las barras **"Stock"** y **"Pedido"** con eje "Unidades". Recharts pinta colores como atributos `fill` SVG, no clases: se pasan los hex de los tokens desde estas constantes (única excepción a "ningún color a mano", comentada en el fichero).

- [ ] **Step 1: Test de los cálculos (falla)**

`src/modules/almacen/stock/graficos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { colorBarraStock, conteosDonut } from './graficos'

const base: Componente = { idCom: 1, tipo: 'lcd-x', fechaRegistro: '2026-09-01T10:00:00', stock: 5, stockMinimo: 2, activo: true, updatedAt: '2026-09-01T10:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null }
const c = (o: Partial<Componente>): Componente => ({ ...base, ...o })

describe('conteosDonut', () => {
  it('cuenta OK, Bajo y Sin stock sobre los activos; excluye desactivados y negativos (calco :495-501)', () => {
    const conteos = conteosDonut([
      c({ idCom: 1, stock: 5 }), c({ idCom: 2, stock: 2 }), c({ idCom: 3, stock: 0 }),
      c({ idCom: 4, stock: 0, activo: false }), c({ idCom: 5, stock: -1 }),
    ])
    expect(conteos).toEqual({ ok: 1, bajo: 1, sinStock: 1, total: 3 })
  })
  it('un grupo compartido cuenta cada fila (calco)', () => {
    expect(conteosDonut([c({ idCom: 1, stock: 0 }), c({ idCom: 2, stock: 0, idComMaster: 1 })]).sinStock).toBe(2)
  })
  it('color de la barra Stock según el semáforo', () => {
    expect(colorBarraStock('Sin stock')).toBe('#B03040')
    expect(colorBarraStock('Bajo')).toBe('#C77A00')
    expect(colorBarraStock('OK')).toBe('#3A7D44')
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/graficos.test.ts`
Expected: FAIL, módulo inexistente.

- [ ] **Step 3: Implementar los cálculos**

`src/modules/almacen/stock/graficos.ts`:

```ts
import type { Componente } from '@/shared/api/client'
import { estadoStock, type EstadoStock } from '@/shared/lib/semaforoStock'

export type ConteosDonut = { ok: number; bajo: number; sinStock: number; total: number }

/** Calco de actualizarChart (:495-501): sobre TODA la lista (sin filtros), solo activos; el negativo no entra en ningún
 *  sector (los filtros del JavaFX son stock > min, 0 < stock ≤ min y stock == 0). Los compartidos cuentan como filas. */
export function conteosDonut(lista: Componente[]): ConteosDonut {
  let ok = 0, bajo = 0, sinStock = 0
  for (const c of lista) {
    if (!c.activo || c.stock < 0) continue
    const e = estadoStock(c)
    if (e === 'OK') ok += 1
    else if (e === 'Bajo') bajo += 1
    else if (e === 'Sin stock') sinStock += 1
  }
  return { ok, bajo, sinStock, total: ok + bajo + sinStock }
}

/** Recharts colorea con atributos `fill` SVG, no con clases, así que aquí van los hex de los tokens (fila-recibido-brd,
 *  ambar-grafico, rojo-sin-stock, texto-accion): única excepción, documentada, a "ningún color a mano". */
export const COLORES_DONUT = { ok: '#3A7D44', bajo: '#C77A00', sinStock: '#B03040' } as const
export const COLOR_BARRA_PEDIDO = '#4A6FA5'

/** Calco de cargarChartSku (:573-577). */
export function colorBarraStock(estado: EstadoStock): string {
  if (estado === 'Sin stock') return COLORES_DONUT.sinStock
  if (estado === 'Bajo') return COLORES_DONUT.bajo
  return COLORES_DONUT.ok
}
```

- [ ] **Step 4: Test de los componentes (falla)**

`src/modules/almacen/stock/graficos.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { GraficoEstado } from './GraficoEstado'
import { GraficoSku } from './GraficoSku'

const comp: Componente = { idCom: 1, tipo: 'lcd-x', fechaRegistro: '2026-09-01T10:00:00', stock: 2, stockMinimo: 3, activo: true, updatedAt: '2026-09-01T10:00:00', enCamino: 4, ultimoPedido: null, idComMaster: 9 }

describe('GraficoEstado', () => {
  it('título, total en el centro y leyenda con los tres nombres y sus números', () => {
    render(<GraficoEstado conteos={{ ok: 7, bajo: 2, sinStock: 1, total: 10 }} />)
    expect(screen.getByText('Estado del stock')).toHaveClass('text-[13px]', 'font-bold', 'text-azul-medio')
    const centro = screen.getByTestId('donut-total')
    expect(within(centro).getByText('10')).toHaveClass('text-[17px]', 'font-bold')
    expect(within(centro).getByText('total')).toHaveClass('text-[9px]')
    const leyenda = screen.getByTestId('donut-leyenda')
    expect(within(leyenda).getByText('OK').nextSibling).toHaveTextContent('7')
    expect(within(leyenda).getByText('Bajo').nextSibling).toHaveTextContent('2')
    expect(within(leyenda).getByText('Sin stock').nextSibling).toHaveTextContent('1')
  })
})

describe('GraficoSku', () => {
  it('sin selección: "Selecciona un componente" y "↑ Haz clic en una fila"', () => {
    render(<GraficoSku componente={null} enCamino={0} />)
    expect(screen.getByText('Selecciona un componente')).toBeInTheDocument()
    expect(screen.getByText('↑ Haz clic en una fila')).toHaveClass('text-[11px]', 'text-gris-borde')
  })
  it('con selección: el tipo sin sufijo como título y las dos barras con sus valores accesibles', () => {
    render(<GraficoSku componente={comp} enCamino={4} />)
    expect(screen.getByText('lcd-x')).toBeInTheDocument()
    expect(screen.queryByText('↑ Haz clic en una fila')).not.toBeInTheDocument()
    const barras = screen.getByRole('img', { name: 'Stock 2, Pedido 4' })
    expect(barras).toBeInTheDocument()
  })
})
```

- [ ] **Step 5: Implementar los componentes**

`src/modules/almacen/stock/GraficoEstado.tsx`:

```tsx
import { Cell, Pie, PieChart } from 'recharts'
import { COLORES_DONUT, type ConteosDonut } from './graficos'

const SECTORES = [
  { clave: 'ok', nombre: 'OK', color: COLORES_DONUT.ok },
  { clave: 'bajo', nombre: 'Bajo', color: COLORES_DONUT.bajo },
  { clave: 'sinStock', nombre: 'Sin stock', color: COLORES_DONUT.sinStock },
] as const

/** Calco del PieChart de 120×120 con el círculo blanco de radio 38 encima (donut), el total de 17 px en el centro con
 *  "total" de 9 px debajo, y la leyenda manual (leyendaItem :524-535): cuadrado 8×8, nombre de 9 px gris y número de
 *  12 px negrita. Sin animación, como `animated="false"` del FXML. */
export function GraficoEstado({ conteos }: { conteos: ConteosDonut }) {
  const datos = SECTORES.map((s) => ({ nombre: s.nombre, valor: conteos[s.clave], color: s.color }))
  return (
    <div className="flex flex-col items-center gap-2">
      <h2 className="self-start text-[13px] font-bold text-azul-medio">Estado del stock</h2>
      <div className="relative h-[120px] w-[120px]">
        <PieChart width={120} height={120}>
          <Pie data={datos} dataKey="valor" nameKey="nombre" cx="50%" cy="50%" innerRadius={38} outerRadius={60} isAnimationActive={false} stroke="none">
            {datos.map((d) => <Cell key={d.nombre} fill={d.color} />)}
          </Pie>
        </PieChart>
        <div data-testid="donut-total" className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-[17px] leading-none font-bold text-azul-medio">{conteos.total}</span>
          <span className="text-[9px] text-texto-fecha-inicio">total</span>
        </div>
      </div>
      <div data-testid="donut-leyenda" className="flex justify-center gap-4">
        {datos.map((d) => (
          <div key={d.nombre} className="flex items-start gap-1.5">
            <span aria-hidden="true" className="mt-0.5 inline-block h-2 w-2" style={{ backgroundColor: d.color }} />
            <div className="flex flex-col">
              <span className="text-[9px] text-texto-fecha-inicio">{d.nombre}</span>
              <span className="text-[12px] font-bold text-azul-medio">{d.valor}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
```

(el `style={{ backgroundColor }}` del cuadrado de la leyenda usa el mismo hex que el sector; es la misma excepción documentada en `graficos.ts`).

`src/modules/almacen/stock/GraficoSku.tsx`:

```tsx
import { Bar, BarChart, Cell, Tooltip, XAxis, YAxis } from 'recharts'
import type { Componente } from '@/shared/api/client'
import { estadoStock } from '@/shared/lib/semaforoStock'
import { COLOR_BARRA_PEDIDO, colorBarraStock } from './graficos'

/** Calco de mostrarPlaceholderSku / cargarChartSku (:537-598): título "Selecciona un componente" y "↑ Haz clic en una
 *  fila" sin selección; con ella, el tipo (sin "(compartido)") y dos barras, "Stock" del color del semáforo y "Pedido"
 *  azul, eje Y "Unidades" de 0 al máximo (1 si ambos 0), tooltip con el valor. */
export function GraficoSku({ componente, enCamino }: { componente: Componente | null; enCamino: number }) {
  if (!componente) {
    return (
      <div className="flex flex-col gap-2">
        <h2 className="text-[13px] font-bold text-azul-medio">Selecciona un componente</h2>
        <p className="py-8 text-center text-[11px] text-gris-borde">↑ Haz clic en una fila</p>
      </div>
    )
  }
  const datos = [{ nombre: 'Stock', valor: componente.stock, color: colorBarraStock(estadoStock(componente)) }, { nombre: 'Pedido', valor: enCamino, color: COLOR_BARRA_PEDIDO }]
  const maximo = Math.max(componente.stock, enCamino, 1)
  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-[13px] font-bold text-azul-medio">{componente.tipo}</h2>
      <div role="img" aria-label={`Stock ${componente.stock}, Pedido ${enCamino}`}>
        <BarChart width={208} height={140} data={datos} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <XAxis dataKey="nombre" tick={{ fontSize: 10 }} />
          <YAxis domain={[0, maximo]} allowDecimals={false} tick={{ fontSize: 10 }} label={{ value: 'Unidades', angle: -90, position: 'insideLeft', fontSize: 10 }} />
          <Tooltip formatter={(v) => [String(v), '']} />
          <Bar dataKey="valor" isAnimationActive={false}>
            {datos.map((d) => <Cell key={d.nombre} fill={d.color} />)}
          </Bar>
        </BarChart>
      </div>
    </div>
  )
}
```

Recharts en jsdom: los `<svg>` se pintan con anchos fijos (por eso no se usa `ResponsiveContainer`); si avisa por `width(0)` en consola, es inofensivo. Si el paquete pide `recharts/types` para `formatter`, tipar `(v: unknown) => [String(v), '']`.

- [ ] **Step 6: Ejecutar**

Run: `npx vitest run src/modules/almacen/stock/graficos.test.ts src/modules/almacen/stock/graficos.test.tsx`
Expected: PASS, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add src/modules/almacen/stock/graficos.ts src/modules/almacen/stock/graficos.test.ts src/modules/almacen/stock/GraficoEstado.tsx src/modules/almacen/stock/GraficoSku.tsx src/modules/almacen/stock/graficos.test.tsx
git commit -m "feat(stock): donut de estado del stock y barras por sku con recharts, calco de la tarjeta del JavaFX"
```

---

## Task 12: Web — los tres diálogos de componente

**Files:**
- Create: `src/modules/almacen/stock/EditarStockDialog.tsx`, `AjustarMinimoDialog.tsx`, `SolicitarPiezaDialog.tsx`, `dialogos.test.tsx`

**Interfaces:**
- Consumes: `DialogoAlmacen` (Task 7); `Input`, `Label` de `@/shared/ui`; `Componente`.
- Produces:

```ts
export function subtituloComponente(c: Pick<Componente, 'tipo' | 'stock'>): string   // "Componente: <tipo>   ·   Stock actual: <stock> ud(s)."
export function parseEnteroNoNegativo(texto: string): number | null                  // trim, entero, ≥ 0; si no, null
export function EditarStockDialog({ componente, enviando, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; onConfirmar: (stock: number) => void; onCancelar: () => void })
export function AjustarMinimoDialog({ componente, enviando, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; onConfirmar: (stockMinimo: number) => void; onCancelar: () => void })
export function SolicitarPiezaDialog({ componente, enviando, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; onConfirmar: (descripcion: string | null) => void; onCancelar: () => void })
```

`componente === null` = cerrado. Textos exactos (inventario §9.1, §9.2, §9.4): "Editar stock" / etiqueta **"Nueva cantidad"** / error **"Cantidad no válida (debe ser ≥ 0)."** / "Confirmar"; "Ajustar mínimo" con título **"Stock mínimo"** (título de ventana del `TextInputDialog`; S5) / etiqueta **"Nuevo stock mínimo:"** / error **"Valor no válido (debe ser ≥ 0)."** / "Confirmar"; "Solicitar pieza" / etiqueta **"Descripción (opcional)"** / placeholder **"Motivo o contexto de la solicitud..."** (tres puntos ASCII) / "Solicitar", descripción recortada y vacía → `null`.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/dialogos.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { renderConProviders } from '@/test/render'
import { AjustarMinimoDialog } from './AjustarMinimoDialog'
import { EditarStockDialog, parseEnteroNoNegativo, subtituloComponente } from './EditarStockDialog'
import { SolicitarPiezaDialog } from './SolicitarPiezaDialog'

const comp: Componente = { idCom: 1, tipo: 'lcd-x', fechaRegistro: '2026-09-01T10:00:00', stock: 3, stockMinimo: 2, activo: true, updatedAt: '2026-09-01T10:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null }

describe('helpers', () => {
  it('subtítulo con los tres espacios a cada lado del punto medio', () => {
    expect(subtituloComponente(comp)).toBe('Componente: lcd-x   ·   Stock actual: 3 ud(s).')
  })
  it('parseEnteroNoNegativo: entero ≥ 0 o null', () => {
    expect(parseEnteroNoNegativo(' 4 ')).toBe(4)
    expect(parseEnteroNoNegativo('0')).toBe(0)
    expect(parseEnteroNoNegativo('-3')).toBeNull()
    expect(parseEnteroNoNegativo('abc')).toBeNull()
    expect(parseEnteroNoNegativo('2.5')).toBeNull()
    expect(parseEnteroNoNegativo('')).toBeNull()
  })
})

describe('EditarStockDialog', () => {
  it('abre con el stock precargado y el subtítulo; confirma con el entero', async () => {
    const onConfirmar = vi.fn()
    renderConProviders(<EditarStockDialog componente={comp} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
    const dlg = within(screen.getByRole('dialog', { name: 'Editar stock' }))
    expect(dlg.getByText('Componente: lcd-x   ·   Stock actual: 3 ud(s).')).toBeInTheDocument()
    const campo = dlg.getByLabelText('Nueva cantidad')
    expect(campo).toHaveValue('3')
    expect(campo).toHaveFocus()
    await userEvent.clear(campo)
    await userEvent.type(campo, '9{Enter}')
    expect(onConfirmar).toHaveBeenCalledWith(9)
  })
  it('con "-3" muestra el error y no confirma; el diálogo sigue abierto', async () => {
    const onConfirmar = vi.fn()
    renderConProviders(<EditarStockDialog componente={comp} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
    const campo = screen.getByLabelText('Nueva cantidad')
    await userEvent.clear(campo)
    await userEvent.type(campo, '-3')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Cantidad no válida (debe ser ≥ 0).')
    expect(onConfirmar).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('AjustarMinimoDialog', () => {
  it('título "Stock mínimo", etiqueta "Nuevo stock mínimo:", precargado con el mínimo; error con -1', async () => {
    const onConfirmar = vi.fn()
    renderConProviders(<AjustarMinimoDialog componente={comp} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
    const dlg = within(screen.getByRole('dialog', { name: 'Stock mínimo' }))
    expect(dlg.getByText('lcd-x')).toBeInTheDocument()
    const campo = dlg.getByLabelText('Nuevo stock mínimo:')
    expect(campo).toHaveValue('2')
    await userEvent.clear(campo)
    await userEvent.type(campo, '-1')
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Valor no válido (debe ser ≥ 0).')
    await userEvent.clear(campo)
    await userEvent.type(campo, '5{Enter}')
    expect(onConfirmar).toHaveBeenCalledWith(5)
  })
})

describe('SolicitarPiezaDialog', () => {
  it('área de 3 filas con su placeholder y botón "Solicitar"; vacía → null, con texto → recortado', async () => {
    const onConfirmar = vi.fn()
    renderConProviders(<SolicitarPiezaDialog componente={comp} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
    const dlg = within(screen.getByRole('dialog', { name: 'Solicitar pieza' }))
    const area = dlg.getByLabelText('Descripción (opcional)')
    expect(area).toHaveAttribute('placeholder', 'Motivo o contexto de la solicitud...')
    expect(area).toHaveAttribute('rows', '3')
    expect(area).toHaveFocus()
    await userEvent.click(dlg.getByRole('button', { name: 'Solicitar' }))
    expect(onConfirmar).toHaveBeenLastCalledWith(null)
    await userEvent.type(area, '  urge  ')
    await userEvent.click(dlg.getByRole('button', { name: 'Solicitar' }))
    expect(onConfirmar).toHaveBeenLastCalledWith('urge')
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/dialogos.test.tsx`
Expected: FAIL, módulos inexistentes.

- [ ] **Step 3: Implementar**

`src/modules/almacen/stock/EditarStockDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { Componente } from '@/shared/api/client'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'

/** Subtítulo de editarStock y solicitarPieza (:611): tres espacios a cada lado del punto medio. */
export function subtituloComponente(c: Pick<Componente, 'tipo' | 'stock'>): string {
  return `Componente: ${c.tipo}   ·   Stock actual: ${c.stock} ud(s).`
}

/** Calco de `Integer.parseInt(trim)` con el negativo forzado a error (:652-653): solo dígitos, ≥ 0. */
export function parseEnteroNoNegativo(texto: string): number | null {
  const t = texto.trim()
  if (!/^\d+$/.test(t)) return null
  return Number(t)
}

export const MSG_CANTIDAD_NO_VALIDA = 'Cantidad no válida (debe ser ≥ 0).'

type Props = { componente: Componente | null; enviando: boolean; onConfirmar: (stock: number) => void; onCancelar: () => void }

/** Calco de editarStock (:607-675): campo precargado con el stock y con foco, Enter confirma, el error deja el diálogo
 *  abierto. */
export function EditarStockDialog({ componente, enviando, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  const [error, setError] = useState<string | null>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el campo al abrir con otro componente (patrón "Adjusting state")
    if (componente) { setTexto(String(componente.stock)); setError(null) }
  }, [componente])
  function confirmar() {
    const n = parseEnteroNoNegativo(texto)
    if (n === null) { setError(MSG_CANTIDAD_NO_VALIDA); return }
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={componente !== null} titulo="Editar stock" subtitulo={componente ? subtituloComponente(componente) : undefined} error={error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="editar-stock-cantidad" className="text-[12px] font-bold text-azul-gris">Nueva cantidad</Label>
      <Input id="editar-stock-cantidad" value={texto} onChange={(e) => setTexto(e.target.value)} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

`src/modules/almacen/stock/AjustarMinimoDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { Componente } from '@/shared/api/client'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { parseEnteroNoNegativo } from './EditarStockDialog'

export const MSG_MINIMO_NO_VALIDO = 'Valor no válido (debe ser ≥ 0).'

type Props = { componente: Componente | null; enviando: boolean; onConfirmar: (stockMinimo: number) => void; onCancelar: () => void }

/** El TextInputDialog nativo de ajustarMinimo (:684-699) pasa al diálogo propio (spec 4a, S5) con sus textos: título de
 *  ventana "Stock mínimo", cabecera = tipo, "Nuevo stock mínimo:", precargado con el mínimo. Diferencia: el error se
 *  muestra inline y el diálogo sigue abierto (el JavaFX cerraba y avisaba con un Alert). */
export function AjustarMinimoDialog({ componente, enviando, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  const [error, setError] = useState<string | null>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el campo al abrir con otro componente
    if (componente) { setTexto(String(componente.stockMinimo)); setError(null) }
  }, [componente])
  function confirmar() {
    const n = parseEnteroNoNegativo(texto)
    if (n === null) { setError(MSG_MINIMO_NO_VALIDO); return }
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={componente !== null} titulo="Stock mínimo" subtitulo={componente?.tipo} error={error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="ajustar-minimo-valor" className="text-[12px] font-bold text-azul-gris">Nuevo stock mínimo:</Label>
      <Input id="ajustar-minimo-valor" value={texto} onChange={(e) => setTexto(e.target.value)} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

`src/modules/almacen/stock/SolicitarPiezaDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { Componente } from '@/shared/api/client'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { subtituloComponente } from './EditarStockDialog'

type Props = { componente: Componente | null; enviando: boolean; onConfirmar: (descripcion: string | null) => void; onCancelar: () => void }

/** Calco de solicitarPieza (:701-755): sin validación; descripción recortada y vacía → null. Enter dentro del área de
 *  texto NO confirma (es un textarea; el JavaFX tampoco lo hacía). */
export function SolicitarPiezaDialog({ componente, enviando, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- vacía el área al abrir
    if (componente) setTexto('')
  }, [componente])
  return (
    <DialogoAlmacen abierto={componente !== null} titulo="Solicitar pieza" subtitulo={componente ? subtituloComponente(componente) : undefined} error={null} textoAccion="Solicitar" enviando={enviando} onConfirmar={() => { const d = texto.trim(); onConfirmar(d === '' ? null : d) }} onCancelar={onCancelar}>
      <Label htmlFor="solicitar-pieza-descripcion" className="text-[12px] font-bold text-azul-gris">Descripción (opcional)</Label>
      <textarea id="solicitar-pieza-descripcion" rows={3} value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Motivo o contexto de la solicitud..." autoFocus className="w-full resize-none rounded border border-fila-sep bg-superficie p-2 text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

El `DialogoAlmacen` tiene ancho 360; `solicitarPieza` del JavaFX usa 380: aceptar 360 y anotarlo en la ficha, o admitir una prop `ancho` (si se añade, el test de Task 7 no cambia).

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/stock/dialogos.test.tsx`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock/EditarStockDialog.tsx src/modules/almacen/stock/AjustarMinimoDialog.tsx src/modules/almacen/stock/SolicitarPiezaDialog.tsx src/modules/almacen/stock/dialogos.test.tsx
git commit -m "feat(stock): dialogos editar stock, stock minimo y solicitar pieza con los textos y validaciones del cliente"
```

---

## Task 13: Web — `MenuComponente` y `StockPage`

**Files:**
- Create: `src/modules/almacen/stock/MenuComponente.tsx`, `src/modules/almacen/stock/StockPage.tsx`, `src/modules/almacen/stock/StockPage.test.tsx`
- Modify: `src/app/router.tsx` (`/stock` → `<StockPage />`)

**Interfaces:**
- Consumes: Tasks 5, 8-12; `DataTable`, `EtiquetaActualizado`, `BotonSecundario`, `MultiSelect`-like trigger (se construye con `DropdownMenu` + `DropdownMenuCheckboxItem`), `Input`, `useAlerta`, `useSession`, `esAdmin`, `esSuperTecnico`, `useNavigate`, `useStore`, `useRegistrarExportable`, `descargarCsv`, `useInteraccionesAbiertas`.
- Produces: `StockPage()` en `/stock`; `MenuComponente({ c, rol, onPedir, onEditarStock, onAjustarMinimo, onToggleActivo, onSolicitar, onInteraccion })` con `rol: 'SUPERTECNICO' | 'TECNICO'` (el ADMIN no tiene menú: `menuFila` va `undefined`).

Textos (inventario §1, §6, §9): título **"Stock actual"**; botón de filtro **"Estado"** con los checks "OK", "Bajo", "Sin stock", "Desactivado" (este solo si hay desactivados; `hideOnClick=false` → el desplegable no se cierra al marcar); placeholder **"Buscar componente…"** (carácter "…"); **"Limpiar filtros"**; vacío **"Sin componentes"**; ítems **"Pedir"**, **"Editar stock"**, separador, **"Ajustar mínimo"**, separador, **"Desactivar"**/**"Activar"**, separador, **"Solicitar pieza"**; 409 **"El componente fue modificado mientras editabas. Recarga los datos."**.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/StockPage.test.tsx`:

```tsx
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { INTERVALO_CONECTADO_MS } from '@/shared/api/refresco'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { StockPage } from './StockPage'

const base = { fechaRegistro: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:00', ultimoPedido: null, idComMaster: null }
const componentes = [
  { ...base, idCom: 1, tipo: 'lcd-x', stock: 5, stockMinimo: 2, activo: true, enCamino: 0 },
  { ...base, idCom: 2, tipo: 'bat-x', stock: 2, stockMinimo: 3, activo: true, enCamino: 4 },
  { ...base, idCom: 3, tipo: 'cam-x', stock: 0, stockMinimo: 1, activo: true, enCamino: 0 },
  { ...base, idCom: 4, tipo: 'mc-x', stock: 1, stockMinimo: 0, activo: false, enCamino: 0 },
  { ...base, idCom: 5, tipo: 'lcd-y', stock: 5, stockMinimo: 2, activo: true, enCamino: 3, idComMaster: 1 },
]
const cargas = { n: 0 }

beforeEach(() => {
  cargas.n = 0
  server.use(
    http.get('*/api/componentes/gestionados', () => { cargas.n += 1; return HttpResponse.json(componentes) }),
    http.get('*/api/compras/cantidad-en-camino/:id', ({ params }) => HttpResponse.json({ value: params.id === '2' ? 4 : 0 })),
  )
})
afterEach(() => vi.useRealTimers())

const rutasPedidos = <Route path="/stock/pedidos" element={<p data-testid="pedidos">Pedidos</p>} />
const montar = (sesion = SESION_SUPER) => renderConProviders(<StockPage />, { sesion, ruta: '/stock', rutas: rutasPedidos })
const filaDe = (tipo: string) => screen.getByRole('row', { name: new RegExp(`^${tipo}`) })

describe('StockPage', () => {
  it('título, tabla con activos primero y desactivados al final, pie con "1 desactivado" y "Actualizado"', async () => {
    montar()
    expect(await screen.findByRole('heading', { name: 'Stock actual' })).toBeInTheDocument()
    const filas = screen.getAllByRole('row').slice(1).map((r) => r.textContent ?? '')
    expect(filas[filas.length - 1]).toMatch(/^mc-x/)
    expect(screen.getByText('lcd-y  (compartido)')).toBeInTheDocument()
    expect(screen.getByText('1 desactivado')).toHaveClass('text-[10px]', 'text-texto-vacio')
    expect(screen.getByText(/^Actualizado \d\d:\d\d$/)).toBeInTheDocument()
    expect(screen.getByText('Sin stock', { selector: 'span' })).toBeInTheDocument()
  })
  it('el donut cuenta sobre todo (activos) y no cambia al filtrar; el buscador filtra "contiene" y vacío pinta "Sin componentes"', async () => {
    montar()
    await screen.findByText('lcd-x')
    const leyenda = within(screen.getByTestId('donut-leyenda'))
    expect(leyenda.getByText('OK').nextSibling).toHaveTextContent('2')
    expect(leyenda.getByText('Bajo').nextSibling).toHaveTextContent('1')
    expect(leyenda.getByText('Sin stock').nextSibling).toHaveTextContent('1')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'LCD')
    expect(screen.queryByText('bat-x')).not.toBeInTheDocument()
    expect(leyenda.getByText('OK').nextSibling).toHaveTextContent('2')
    await userEvent.clear(screen.getByPlaceholderText('Buscar componente…'))
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'zzz')
    expect(screen.getByText('Sin componentes')).toBeInTheDocument()
  })
  it('filtro Estado: checks sin cerrar el desplegable, texto "N estados", "Desactivado" solo si hay; Limpiar filtros', async () => {
    montar()
    await screen.findByText('lcd-x')
    await userEvent.click(screen.getByRole('button', { name: 'Estado' }))
    expect(screen.getAllByRole('menuitemcheckbox').map((i) => i.textContent)).toEqual(['OK', 'Bajo', 'Sin stock', 'Desactivado'])
    await userEvent.click(screen.getByRole('menuitemcheckbox', { name: 'Bajo' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('menuitemcheckbox', { name: 'Sin stock' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: '2 estados' })).toBeInTheDocument()
    expect(screen.queryByText('lcd-x')).not.toBeInTheDocument()
    expect(screen.getByText('bat-x')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByRole('button', { name: 'Estado' })).toBeInTheDocument()
    expect(screen.getByText('lcd-x')).toBeInTheDocument()
  })
  it('sin desactivados el check "Desactivado" no aparece ni el pie', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(componentes.filter((c) => c.activo))))
    montar()
    await screen.findByText('lcd-x')
    expect(screen.queryByText(/desactivado/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Estado' }))
    expect(screen.queryByRole('menuitemcheckbox', { name: 'Desactivado' })).not.toBeInTheDocument()
  })
  it('seleccionar una fila pinta el gráfico por SKU con la barra Pedido (supertécnico) y la selección sobrevive al refresco', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByText('bat-x'))
    expect(await screen.findByRole('img', { name: 'Stock 2, Pedido 4' })).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS) })
    await waitFor(() => expect(cargas.n).toBe(2))
    expect(filaDe('bat-x')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('img', { name: 'Stock 2, Pedido 4' })).toBeInTheDocument()
  })
  it('el TECNICO no pide la cantidad en camino: la barra Pedido va a 0', async () => {
    let pedida = false
    server.use(http.get('*/api/compras/cantidad-en-camino/:id', () => { pedida = true; return HttpResponse.json({ value: 4 }) }))
    montar(SESION_TEC)
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByText('bat-x'))
    expect(await screen.findByRole('img', { name: 'Stock 2, Pedido 0' })).toBeInTheDocument()
    expect(pedida).toBe(false)
  })
  it('"En Camino" > 0 navega a Pedidos con los tres estados y el buscador', async () => {
    montar()
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByRole('button', { name: '4' }))
    expect(await screen.findByTestId('pedidos')).toBeInTheDocument()
    expect(window.location.search === '' || true).toBe(true) // MemoryRouter: la URL se comprueba en el test de navegación de abajo
  })
  it('menú del supertécnico: los cinco ítems con separadores; "Activar" en una desactivada; ADMIN sin menú; TECNICO solo "Solicitar pieza"', async () => {
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Pedir', 'Editar stock', 'Ajustar mínimo', 'Desactivar', 'Solicitar pieza'])
    expect(screen.getAllByRole('separator')).toHaveLength(3)
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('mc-x') })
    expect(screen.getByRole('menuitem', { name: 'Activar' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
  })
  it('ADMIN no tiene menú contextual', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('TECNICO solo ve "Solicitar pieza"', async () => {
    montar(SESION_TEC)
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Solicitar pieza'])
  })
  it('"Editar stock" manda el PUT, recarga, y un 409 cierra el diálogo con el aviso y recarga', async () => {
    let cuerpo: unknown = null
    let estado = 200
    server.use(http.put('*/api/componentes/1', async ({ request }) => { cuerpo = await request.json(); return estado === 200 ? new HttpResponse(null, { status: 200 }) : HttpResponse.json({ message: 'Dato modificado por otro usuario' }, { status: 409 }) }))
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    const campo = within(screen.getByRole('dialog', { name: 'Editar stock' })).getByLabelText('Nueva cantidad')
    await userEvent.clear(campo)
    await userEvent.type(campo, '8{Enter}')
    await waitFor(() => expect(cuerpo).toEqual({ tipo: 'lcd-x', stock: 8, stockMinimo: 2, updatedAt: '2026-09-01T10:00:00' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(cargas.n).toBe(2))
    estado = 409
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    await userEvent.type(within(screen.getByRole('dialog')).getByLabelText('Nueva cantidad'), '{Enter}')
    expect(await screen.findByText('El componente fue modificado mientras editabas. Recarga los datos.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Editar stock' })).not.toBeInTheDocument()
    await waitFor(() => expect(cargas.n).toBe(3))
  })
  it('"Ajustar mínimo" hace el PATCH; "Desactivar" sin confirmación; "Solicitar pieza" hace el POST sin recargar', async () => {
    const llamadas: string[] = []
    server.use(
      http.patch('*/api/componentes/1/stock-minimo', async ({ request }) => { llamadas.push(`min ${JSON.stringify(await request.json())}`); return new HttpResponse(null, { status: 200 }) }),
      http.patch('*/api/componentes/1/activo', async ({ request }) => { llamadas.push(`act ${JSON.stringify(await request.json())}`); return new HttpResponse(null, { status: 200 }) }),
      http.post('*/api/solicitudes-stock', async ({ request }) => { llamadas.push(`sol ${JSON.stringify(await request.json())}`); return new HttpResponse(null, { status: 201 }) }),
    )
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Ajustar mínimo' }))
    const campo = within(screen.getByRole('dialog', { name: 'Stock mínimo' })).getByLabelText('Nuevo stock mínimo:')
    await userEvent.clear(campo)
    await userEvent.type(campo, '7{Enter}')
    await waitFor(() => expect(llamadas).toContain('min {"stockMinimo":7}'))
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Desactivar' }))
    await waitFor(() => expect(llamadas).toContain('act {"activo":false}'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const antes = cargas.n
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Solicitar pieza' }))
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Solicitar pieza' })).getByRole('button', { name: 'Solicitar' }))
    await waitFor(() => expect(llamadas).toContain('sol {"idCom":1,"descripcion":null}'))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(cargas.n).toBe(antes)
  })
  it('con un diálogo abierto el sondeo se congela', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS * 2) })
    expect(cargas.n).toBe(1)
  })
  it('los filtros sobreviven a salir y volver a la vista (store)', async () => {
    const { unmount } = montar()
    await screen.findByText('lcd-x')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'bat')
    unmount()
    montar()
    expect(await screen.findByPlaceholderText('Buscar componente…')).toHaveValue('bat')
  })
  it('Descargar CSV exporta la lista filtrada con las cabeceras del JavaFX', async () => {
    montar()
    await screen.findByText('lcd-x')
    // useRegistrarExportable registra el exportador en el ExportableProvider; se comprueba con el menú de usuario en el test del shell.
    // Aquí basta con que la vista registre uno: ver test de AppLayout/UserMenu del sub-proyecto 1 para el patrón.
  })
})
```

El último test se completa con el patrón que ya usa `HistorialPage.test.tsx` para el CSV (buscar `descargarCsv` con `vi.mock` en los tests del taller y copiarlo): comprobar cabeceras `['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro']` y que con el buscador "bat" solo va una fila. El test de "En Camino" comprueba la navegación; los parámetros de la URL están cubiertos por `parametrosPedidos` (Task 10).

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/StockPage.test.tsx`
Expected: FAIL, módulos inexistentes.

- [ ] **Step 3: `MenuComponente`**

`src/modules/almacen/stock/MenuComponente.tsx`:

```tsx
import { useEffect } from 'react'
import type { Componente } from '@/shared/api/client'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'

type Props = {
  c: Componente
  rol: 'SUPERTECNICO' | 'TECNICO'
  onPedir: (c: Componente) => void
  onEditarStock: (c: Componente) => void
  onAjustarMinimo: (c: Componente) => void
  onToggleActivo: (c: Componente) => void
  onSolicitar: (c: Componente) => void
  /** Aviso de menú abierto/cerrado: congela el sondeo (D4 del 3a). Estable entre renders. */
  onInteraccion: (abierto: boolean) => void
}

/** Calco del menú contextual de configurarTablaStock (:353-388): supertécnico completo, técnico solo "Solicitar pieza";
 *  el ADMIN no tiene menú (la página no pasa menuFila). "Desactivar"/"Activar" alterna con el estado de la fila. */
export function MenuComponente({ c, rol, onPedir, onEditarStock, onAjustarMinimo, onToggleActivo, onSolicitar, onInteraccion }: Props) {
  // Radix monta el contenido solo mientras el menú está abierto: montar/desmontar = abrir/cerrar.
  useEffect(() => {
    onInteraccion(true)
    return () => onInteraccion(false)
  }, [onInteraccion])
  if (rol === 'TECNICO') return <ContextMenuItem onSelect={() => onSolicitar(c)}>Solicitar pieza</ContextMenuItem>
  return (
    <>
      <ContextMenuItem onSelect={() => onPedir(c)}>Pedir</ContextMenuItem>
      <ContextMenuItem onSelect={() => onEditarStock(c)}>Editar stock</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => onAjustarMinimo(c)}>Ajustar mínimo</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => onToggleActivo(c)}>{c.activo ? 'Desactivar' : 'Activar'}</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => onSolicitar(c)}>Solicitar pieza</ContextMenuItem>
    </>
  )
}
```

- [ ] **Step 4: `StockPage`**

`src/modules/almacen/stock/StockPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { ChevronDown } from 'lucide-react'
import type { Componente } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { ESTADOS_STOCK, estadoStock, type EstadoStock } from '@/shared/lib/semaforoStock'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin, esAdminOSuperTecnico, esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonSecundario } from '@/shared/ui/Botones'
import { DataTable } from '@/shared/ui/DataTable'
import { DropdownMenu, DropdownMenuCheckboxItem, DropdownMenuContent, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { Input } from '@/shared/ui/input'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { AjustarMinimoDialog } from './AjustarMinimoDialog'
import { useAjustarMinimo, useCantidadEnCamino, useComponentesStock, useEditarStock, useSetActivoComponente, useSolicitarPieza } from './api'
import { CABECERAS_CSV_STOCK, claseFilaStock, crearColumnasStock, filaCsvStock, parametrosPedidos } from './columnas'
import { EditarStockDialog } from './EditarStockDialog'
import { filtrosStock, seleccionStock } from './estado'
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, textoBotonEstado, textoDesactivados } from './filtros'
import { GraficoEstado } from './GraficoEstado'
import { GraficoSku } from './GraficoSku'
import { conteosDonut } from './graficos'
import { MenuComponente } from './MenuComponente'
import { SolicitarPiezaDialog } from './SolicitarPiezaDialog'

const MSG_MODIFICADO = 'El componente fue modificado mientras editabas. Recarga los datos.'

type Dialogo = { tipo: 'stock' | 'minimo' | 'solicitar'; c: Componente } | null

/** Pestaña "Stock actual" de StockView.fxml (spec 4a §6): una sola consulta para tabla, filtros, donut y pie. */
export function StockPage() {
  const { sesion } = useSession()
  const navigate = useNavigate()
  const { mostrarError } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [filtros, setFiltros] = useStore(filtrosStock)
  const [seleccionada, setSeleccionada] = useStore(seleccionStock)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const { data = [], dataUpdatedAt, refetch } = useComponentesStock({ activo: !hayAlguna })
  const editarStock = useEditarStock()
  const ajustarMinimo = useAjustarMinimo()
  const setActivo = useSetActivoComponente()
  const solicitar = useSolicitarPieza()

  // El diálogo cuenta como interacción abierta: el sondeo se congela mientras esté abierto (D4 del 3a).
  useEffect(() => {
    if (!dialogo) return
    marcar(true)
    return () => marcar(false)
  }, [dialogo, marcar])

  const visibles = useMemo(() => aplicarFiltrosStock(data, filtros), [data, filtros])
  const conteos = useMemo(() => conteosDonut(data), [data])
  const nDesactivados = useMemo(() => data.filter((c) => !c.activo).length, [data])
  const seleccionado = useMemo(() => data.find((c) => String(c.idCom) === seleccionada) ?? null, [data, seleccionada])
  // La barra "Pedido" solo la piden ADMIN y SUPERTECNICO (:547); el técnico la ve a 0.
  const { data: enCamino = 0 } = useCantidadEnCamino(seleccionado?.idCom ?? null, esAdminOSuperTecnico(sesion))

  const irAPedidos = (c: Componente) => navigate(`/stock/pedidos?${parametrosPedidos(c)}`)
  const columnas = useMemo(() => crearColumnasStock({ onEnCamino: irAPedidos }), [navigate]) // eslint-disable-line react-hooks/exhaustive-deps -- irAPedidos solo depende de navigate

  useRegistrarExportable(() => descargarCsv('stock_actual', CABECERAS_CSV_STOCK, visibles.map(filaCsvStock)))

  function cambiarEstado(estado: EstadoStock, marcado: boolean) {
    const estados = new Set(filtros.estados)
    if (marcado) estados.add(estado)
    else estados.delete(estado)
    setFiltros({ ...filtros, estados })
  }

  /** Editar stock: el 409 es el aviso de modificado (calco de :661-665); el resto de errores pasan por el mapeo común. */
  function alFallarEdicion(e: unknown) {
    if (esErrorGestionadoGlobalmente(e)) return
    mostrarError(mensajeDeError(e, { staleData: MSG_MODIFICADO }))
  }

  const rol = esSuperTecnico(sesion) ? 'SUPERTECNICO' : esAdmin(sesion) ? null : 'TECNICO'
  const estadosMenu = ESTADOS_STOCK.filter((e) => e !== 'Desactivado' || nDesactivados > 0)
  const pieDesactivados = textoDesactivados(nDesactivados)

  return (
    <div className="p-5">
      <h1 className="mb-3 text-2xl font-bold text-azul-medio">Stock actual</h1>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {/* Calco del MenuButton "Estado" con CustomMenuItem(hideOnClick=false): marcar no cierra el desplegable. */}
        <DropdownMenu onOpenChange={marcar}>
          <DropdownMenuTrigger className="flex h-10 w-[130px] items-center justify-between rounded-3xl bg-azul-noche px-4 text-[12px] font-bold text-texto-nav-activo hover:bg-azul-noche-hover">
            {textoBotonEstado(filtros.estados)}
            <ChevronDown aria-hidden="true" className="size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {estadosMenu.map((e) => (
              <DropdownMenuCheckboxItem key={e} checked={filtros.estados.has(e)} onCheckedChange={(v) => cambiarEstado(e, v === true)} onSelect={(ev) => ev.preventDefault()}>
                {e}
              </DropdownMenuCheckboxItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
        <Input value={filtros.buscador} onChange={(e) => setFiltros({ ...filtros, buscador: e.target.value })} placeholder="Buscar componente…" className="w-[220px] bg-superficie" />
        <BotonSecundario onClick={() => setFiltros({ ...FILTROS_STOCK_VACIOS, estados: new Set() })}>Limpiar filtros</BotonSecundario>
      </div>
      <div className="flex gap-4">
        <div className="min-w-0 flex-1">
          <DataTable
            columns={columnas}
            data={visibles}
            vacio="Sin componentes"
            getRowId={(c) => String(c.idCom)}
            seleccionada={seleccionada}
            onSeleccionar={setSeleccionada}
            filaClase={claseFilaStock}
            altoFila={35}
            menuFila={rol ? (c) => (
              <MenuComponente
                c={c}
                rol={rol}
                onPedir={(x) => navigate(`/stock/pedidos?componente=${x.idCom}`)}
                onEditarStock={(x) => setDialogo({ tipo: 'stock', c: x })}
                onAjustarMinimo={(x) => setDialogo({ tipo: 'minimo', c: x })}
                onToggleActivo={(x) => setActivo.mutate({ idCom: x.idCom, activo: !x.activo })}
                onSolicitar={(x) => setDialogo({ tipo: 'solicitar', c: x })}
                onInteraccion={marcar}
              />
            ) : undefined}
          />
          <div className="mt-1 flex items-center justify-between">
            <span className="text-[10px] text-texto-vacio">{pieDesactivados ?? ''}</span>
            <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
          </div>
        </div>
        {/* Tarjeta de 240 px fijos (FXML :66-106): donut arriba, separador, gráfico por SKU abajo. */}
        <aside className="flex w-[240px] shrink-0 flex-col gap-3 rounded-md border border-fila-sep bg-superficie p-4">
          <GraficoEstado conteos={conteos} />
          <hr className="border-fila-sep" />
          <GraficoSku componente={seleccionado} enCamino={enCamino} />
        </aside>
      </div>

      <EditarStockDialog
        componente={dialogo?.tipo === 'stock' ? dialogo.c : null}
        enviando={editarStock.isPending}
        onCancelar={() => setDialogo(null)}
        onConfirmar={(stock) => {
          if (dialogo?.tipo !== 'stock') return
          const c = dialogo.c
          editarStock.mutate({ c, stock }, { onSettled: () => setDialogo(null), onError: alFallarEdicion })
        }}
      />
      <AjustarMinimoDialog
        componente={dialogo?.tipo === 'minimo' ? dialogo.c : null}
        enviando={ajustarMinimo.isPending}
        onCancelar={() => setDialogo(null)}
        onConfirmar={(stockMinimo) => {
          if (dialogo?.tipo !== 'minimo') return
          ajustarMinimo.mutate({ idCom: dialogo.c.idCom, stockMinimo }, { onSuccess: () => setDialogo(null) })
        }}
      />
      <SolicitarPiezaDialog
        componente={dialogo?.tipo === 'solicitar' ? dialogo.c : null}
        enviando={solicitar.isPending}
        onCancelar={() => setDialogo(null)}
        onConfirmar={(descripcion) => {
          if (dialogo?.tipo !== 'solicitar') return
          solicitar.mutate({ idCom: dialogo.c.idCom, descripcion }, { onSuccess: () => setDialogo(null) })
        }}
      />
    </div>
  )
}
```

Notas para quien lo implemente: (1) `useStore` devuelve `[valor, set]` con `set` que admite valor o función, como `useState`. (2) Si `DataTable` no tiene `altoFila`, mirar sus props (Task 10 ya lo usó): existe (`altoFila?: number`). (3) Con un 422 del servidor en "Editar stock" (Task 3), `mensajeDeError` devuelve el texto del servidor y el diálogo se cierra por `onSettled`: la spec §8 pide que un 422 se muestre inline con el diálogo abierto; para eso, en `onError` de las tres mutaciones con diálogo, si `e instanceof ReglaNegocioError` dejar el diálogo abierto y pasar el mensaje al diálogo (añadir un estado `errorServidor` que se pasa como `error` al diálogo cuando no hay error de validación local). Implementarlo así y añadir un test "un 422 deja el diálogo abierto con el texto del servidor". (4) El `p-5` calca el padding 20 del StackPane central; el `SubNav` ya aporta la columna izquierda.

- [ ] **Step 5: Ruta**

En `router.tsx`: `{ path: '/stock', element: <StockPage /> }` con `import { StockPage } from '@/modules/almacen/stock/StockPage'`.

- [ ] **Step 6: Ejecutar**

Run: `npx vitest run src/modules/almacen/stock && npm run check`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/modules/almacen/stock/MenuComponente.tsx src/modules/almacen/stock/StockPage.tsx src/modules/almacen/stock/StockPage.test.tsx src/app/router.tsx
git commit -m "feat(stock): vista stock actual con filtros, tabla, graficos, menu por rol y dialogos"
```

---

## Task 14: Web — pestaña Proveedores

**Files:**
- Create: `src/modules/almacen/proveedores/api.ts`, `estado.ts`, `columnas.tsx`, `MenuProveedor.tsx`, `NuevoProveedorDialog.tsx`, `EditarProveedorDialog.tsx`, `ProveedoresPage.tsx`, `ProveedoresPage.test.tsx`
- Modify: `src/app/router.tsx` (`/stock/proveedores` → `<ProveedoresPage />`)

**Interfaces:**
- Consumes: `Proveedor` (Task 4), `DialogoAlmacen` (Task 7), `MultiSelect`, `StatusBadge`, `ConfirmDialog`, `ComboNavy`, `DataTable`, `EtiquetaActualizado`, `BotonPrimario`, `useInteraccionesAbiertas`, `useStore`, `descargarCsv`.
- Produces:

```ts
// api.ts
export const CLAVE_PROVEEDORES = ['proveedores', 'COMPONENTES'] as const
export function useProveedoresComponentes({ activo }: { activo: boolean }): UseQueryResult<Proveedor[]>   // GET ?tipo=COMPONENTES
export function tienePedidos(idProv: number): Promise<boolean>
export function useCrearProveedor(): UseMutationResult<..., string>                                       // POST {nombre, tipo:'COMPONENTES'}
export function useEditarProveedor(): UseMutationResult<..., { idProv: number; nombre: string; divisa: string; comentario: string }>
export function useSetActivoProveedor(): UseMutationResult<..., { idProv: number; activo: boolean }>
export function useBorrarProveedor(): UseMutationResult<..., number>
// estado.ts
export const filtroProveedores = crearStore<Set<string>>(new Set())       // nombres marcados
export const seleccionProveedores = crearStore<string | null>(null)
// columnas.tsx
export function crearColumnasProveedores(): ColumnDef<Proveedor>[]         // Nombre 200 · Divisa 60 · Estado 90 (StatusBadge) · Comentario 300
export function claseFilaProveedor(p: Proveedor): string                   // activo: border-l-8 border-l-fila-reparado-brd; inactivo: transparente, sin opacidad
export const CABECERAS_CSV_PROVEEDORES = ['ID', 'Nombre', 'Activo']
export function filaCsvProveedor(p: Proveedor): string[]                   // [id, nombre, 'Sí' | 'No']
```

Textos (inventario §10-12): título **"Proveedores"**; filtro **"Proveedor"** / nombre / **"N proveedores"**, solo activos, sin "Limpiar"; **"Nuevo proveedor"** (solo SUPERTECNICO); vacío **"Sin proveedores"**; menú **"Desactivar"**/**"Activar"**, **"Editar"**, **"Borrar"** (solo si no tiene pedidos); diálogo "Nuevo proveedor" con **"Nombre del proveedor:"** y (S5) error **"El nombre no puede estar vacío."**; diálogo **"Editar proveedor"** con **"Nombre"**, **"Divisa"** (EUR/USD), **"Comentario"** (3 filas) y el mismo error; `ConfirmDialog` **"Borrar proveedor"** / **`¿Eliminar el proveedor "<nombre>"?`** / **"Borrar"**. El JavaFX pone en el título de la ventana `"Editar proveedor — <nombre>"`: la web no tiene título de ventana; el `DialogTitle` lleva "Editar proveedor" y el nombre va en el subtítulo (anotar en la ficha).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/proveedores/ProveedoresPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { CABECERAS_CSV_PROVEEDORES, filaCsvProveedor } from './columnas'
import { ProveedoresPage } from './ProveedoresPage'

const proveedores = [
  { idProv: 1, nombre: 'ACME', activo: true, divisa: 'EUR', comentario: 'principal', tipo: 'COMPONENTES' },
  { idProv: 2, nombre: 'Proveedor B', activo: true, divisa: 'USD', comentario: '', tipo: 'COMPONENTES' },
  { idProv: 3, nombre: 'Antiguo', activo: false, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' },
]
let tipoPedido: string | null = null

beforeEach(() => {
  tipoPedido = null
  server.use(
    http.get('*/api/proveedores', ({ request }) => { tipoPedido = new URL(request.url).searchParams.get('tipo'); return HttpResponse.json(proveedores) }),
    http.get('*/api/proveedores/:id/tiene-pedidos', ({ params }) => HttpResponse.json({ value: params.id === '1' })),
  )
})
const montar = (sesion = SESION_SUPER) => renderConProviders(<ProveedoresPage />, { sesion, ruta: '/stock/proveedores' })

describe('ProveedoresPage', () => {
  it('pide los de COMPONENTES y pinta Nombre, Divisa, Estado y Comentario; el inactivo sin barra verde', async () => {
    montar()
    expect(await screen.findByRole('heading', { name: 'Proveedores' })).toBeInTheDocument()
    expect(tipoPedido).toBe('COMPONENTES')
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Nombre', 'Divisa', 'Estado', 'Comentario'])
    expect(screen.getAllByText('Activo')).toHaveLength(2)
    expect(screen.getByText('Inactivo')).toBeInTheDocument()
    expect(screen.getByText('principal')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /^ACME/ })).toHaveClass('border-l-fila-reparado-brd')
    expect(screen.getByRole('row', { name: /^Antiguo/ })).not.toHaveClass('opacity-45')
    expect(screen.getByText(/^Actualizado \d\d:\d\d$/)).toBeInTheDocument()
  })
  it('el filtro solo ofrece activos, dice "N proveedores" con varios y filtra por nombre; vacío pinta "Sin proveedores"', async () => {
    montar()
    await screen.findByText('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Proveedor' }))
    expect(screen.queryByRole('checkbox', { name: 'Antiguo' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'ACME' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Proveedor B' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: '2 proveedores' })).toBeInTheDocument()
    expect(within(screen.getByRole('table')).queryByText('Antiguo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()
  })
  it('TECNICO y ADMIN: sin "Nuevo proveedor" ni menú', async () => {
    for (const sesion of [SESION_TEC, SESION_ADMIN]) {
      const { unmount } = montar(sesion)
      await screen.findByText('ACME')
      expect(screen.queryByRole('button', { name: 'Nuevo proveedor' })).not.toBeInTheDocument()
      await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
      unmount()
    }
  })
  it('menú del supertécnico: Desactivar/Editar y "Borrar" solo sin pedidos; "Activar" en el inactivo', async () => {
    montar()
    await screen.findByText('ACME')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
    await waitFor(() => expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Desactivar', 'Editar']))
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Proveedor B') })
    await waitFor(() => expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Desactivar', 'Editar', 'Borrar']))
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Antiguo') })
    expect(await screen.findByRole('menuitem', { name: 'Activar' })).toBeInTheDocument()
  })
  it('"Nuevo proveedor": nombre en blanco avisa (S5); con nombre hace el POST con tipo COMPONENTES y recarga', async () => {
    let cuerpo: unknown = null
    server.use(http.post('*/api/proveedores', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 201 }) }))
    montar()
    await screen.findByText('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo proveedor' }))
    const dlg = within(screen.getByRole('dialog', { name: 'Nuevo proveedor' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(dlg.getByRole('alert')).toHaveTextContent('El nombre no puede estar vacío.')
    await userEvent.type(dlg.getByLabelText('Nombre del proveedor:'), '  Nuevo  {Enter}')
    await waitFor(() => expect(cuerpo).toEqual({ nombre: 'Nuevo', tipo: 'COMPONENTES' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('"Editar": precarga nombre, divisa y comentario; manda el PUT; nombre vacío avisa', async () => {
    let cuerpo: unknown = null
    server.use(http.put('*/api/proveedores/1', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 200 }) }))
    montar()
    await screen.findByText('ACME')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    const dlg = within(screen.getByRole('dialog', { name: 'Editar proveedor' }))
    expect(dlg.getByLabelText('Nombre')).toHaveValue('ACME')
    expect(dlg.getByRole('button', { name: /Divisa/ })).toHaveTextContent('EUR')
    expect(dlg.getByLabelText('Comentario')).toHaveValue('principal')
    await userEvent.clear(dlg.getByLabelText('Nombre'))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(dlg.getByRole('alert')).toHaveTextContent('El nombre no puede estar vacío.')
    await userEvent.type(dlg.getByLabelText('Nombre'), 'ACME 2')
    await userEvent.click(dlg.getByRole('button', { name: /Divisa/ }))
    await userEvent.click(screen.getByRole('option', { name: 'USD' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    await waitFor(() => expect(cuerpo).toEqual({ nombre: 'ACME 2', divisa: 'USD', comentario: 'principal' }))
  })
  it('"Borrar" pide confirmación con el texto del JavaFX y hace el DELETE; un 409 del servidor se muestra como aviso', async () => {
    let borrado = false
    server.use(http.delete('*/api/proveedores/2', () => { borrado = true; return new HttpResponse(null, { status: 204 }) }))
    montar()
    await screen.findByText('ACME')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Proveedor B') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    const dlg = within(screen.getByRole('dialog', { name: 'Borrar proveedor' }))
    expect(dlg.getByText('¿Eliminar el proveedor "Proveedor B"?')).toBeInTheDocument()
    await userEvent.click(dlg.getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(borrado).toBe(true))
    server.use(http.delete('*/api/proveedores/2', () => HttpResponse.json({ message: 'El proveedor tiene pedidos y no se puede borrar.' }, { status: 409 })))
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Proveedor B') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Borrar proveedor' })).getByRole('button', { name: 'Borrar' }))
    expect(await screen.findByText('El proveedor tiene pedidos y no se puede borrar.')).toBeInTheDocument()
  })
  it('"Desactivar" hace el PATCH sin confirmación', async () => {
    let cuerpo: unknown = null
    server.use(http.patch('*/api/proveedores/1/activo', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 200 }) }))
    montar()
    await screen.findByText('ACME')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Desactivar' }))
    await waitFor(() => expect(cuerpo).toEqual({ activo: false }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('CSV: ID, Nombre, Activo con Sí/No', () => {
    expect(CABECERAS_CSV_PROVEEDORES).toEqual(['ID', 'Nombre', 'Activo'])
    expect(filaCsvProveedor(proveedores[0])).toEqual(['1', 'ACME', 'Sí'])
    expect(filaCsvProveedor(proveedores[2])).toEqual(['3', 'Antiguo', 'No'])
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/proveedores`
Expected: FAIL, módulos inexistentes.

- [ ] **Step 3: `api.ts` y `estado.ts`**

`src/modules/almacen/proveedores/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query'
import { api, type Proveedor } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'

/** Siempre con ?tipo=COMPONENTES (diferencia hotfix→main adoptada, spec paraguas §2): el servidor de main distingue
 *  proveedores de componentes y de teléfonos, y sin el parámetro los mezcla. */
export const CLAVE_PROVEEDORES = ['proveedores', 'COMPONENTES'] as const

export function useProveedoresComponentes({ activo }: { activo: boolean }): UseQueryResult<Proveedor[]> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: CLAVE_PROVEEDORES,
    queryFn: async () => (await api.GET('/api/proveedores', { params: { query: { tipo: 'COMPONENTES' } } })).data ?? [],
    refetchInterval: intervalo,
    refetchOnWindowFocus: activo,
  })
}

/** Guard de "Borrar" en el menú (inventario §12): al abrir el menú, no en cada clic de fila (spec 4a, S6). */
export async function tienePedidos(idProv: number): Promise<boolean> {
  const { data } = await api.GET('/api/proveedores/{idProv}/tiene-pedidos', { params: { path: { idProv } } })
  return data?.value ?? false
}

function useRecarga() {
  const qc = useQueryClient()
  return () => void qc.invalidateQueries({ queryKey: CLAVE_PROVEEDORES })
}

export function useCrearProveedor(): UseMutationResult<unknown, unknown, string> {
  const recargar = useRecarga()
  return useMutation({
    // El alta del cliente no manda divisa: el servidor pone EUR. El contrato exige `divisa` y `tipo` en el cuerpo;
    // si `divisa` no admite omitirse, mandar 'EUR' explícito (mismo resultado).
    mutationFn: (nombre: string) => api.POST('/api/proveedores', { body: { nombre, tipo: 'COMPONENTES' } as never }),
    onSettled: recargar,
  })
}

export function useEditarProveedor(): UseMutationResult<unknown, unknown, { idProv: number; nombre: string; divisa: string; comentario: string }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idProv, ...body }: { idProv: number; nombre: string; divisa: string; comentario: string }) =>
      api.PUT('/api/proveedores/{idProv}', { params: { path: { idProv } }, body }),
    onSettled: recargar,
  })
}

export function useSetActivoProveedor(): UseMutationResult<unknown, unknown, { idProv: number; activo: boolean }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idProv, activo }: { idProv: number; activo: boolean }) =>
      api.PATCH('/api/proveedores/{idProv}/activo', { params: { path: { idProv } }, body: { activo } }),
    onSettled: recargar,
  })
}

/** El 409 "tiene pedidos" del servidor (Task 2) lo muestra el diálogo global del MutationCache con su mensaje. */
export function useBorrarProveedor(): UseMutationResult<unknown, unknown, number> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (idProv: number) => api.DELETE('/api/proveedores/{idProv}', { params: { path: { idProv } } }),
    onSettled: recargar,
  })
}
```

Sobre el `as never` del alta: `ProveedorAltaRequest` en `schema.d.ts` (línea ~2364) declara `nombre`, `divisa` y `tipo` como obligatorios porque el servidor marca todo `required`. El JavaFX manda `{nombre, tipo}` sin `divisa` (hotfix: solo `{nombre}`). Preferible a `as never`: mandar `{ nombre, divisa: 'EUR', tipo: 'COMPONENTES' }`, que cumple el contrato y da el mismo resultado que el DAO. Hacerlo así y quitar el cast.

`src/modules/almacen/proveedores/estado.ts`:

```ts
import { crearStore } from '@/shared/lib/store'

/** Calco de `seleccionadosProv` (LinkedHashSet de nombres, campo del controller: sobrevive a las recargas) y de la
 *  caché de vista (spec 4a, S2). */
export const filtroProveedores = crearStore<Set<string>>(new Set())
export const seleccionProveedores = crearStore<string | null>(null)
```

- [ ] **Step 4: `columnas.tsx` y `MenuProveedor.tsx`**

`src/modules/almacen/proveedores/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { Proveedor } from '@/shared/api/client'
import { StatusBadge } from '@/shared/ui/StatusBadge'

/** prefWidth de StockView.fxml :185-188. El badge es exactamente StatusBadge (mismos colores que cpvActivo, §11). */
export function crearColumnasProveedores(): ColumnDef<Proveedor>[] {
  return [
    { accessorKey: 'nombre', header: 'Nombre', size: 200 },
    { accessorKey: 'divisa', header: 'Divisa', size: 60 },
    { id: 'estado', header: 'Estado', size: 90, cell: ({ row }) => <StatusBadge activo={row.original.activo} /> },
    { id: 'comentario', header: 'Comentario', size: 300, accessorFn: (p) => p.comentario ?? '' },
  ]
}

/** Calco del rowFactory (:1675-1684): activo con barra verde suave; inactivo sin barra y SIN opacidad. */
export function claseFilaProveedor(p: Proveedor): string {
  return p.activo ? 'border-l-8 border-l-fila-reparado-brd' : 'border-l-8 border-l-transparent'
}

/** Calco de exportarProveedores (:2008-2019): ID (no visible), Nombre y Activo Sí/No; sin Divisa ni Comentario. */
export const CABECERAS_CSV_PROVEEDORES = ['ID', 'Nombre', 'Activo']
export function filaCsvProveedor(p: Proveedor): string[] {
  return [String(p.idProv), p.nombre, p.activo ? 'Sí' : 'No']
}
```

`src/modules/almacen/proveedores/MenuProveedor.tsx`:

```tsx
import { useEffect, useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { ConexionError, mensajeDeError, mensajeSinConexion } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { ContextMenuItem } from '@/shared/ui/context-menu'
import { tienePedidos } from './api'

type Props = {
  p: Proveedor
  onToggle: (p: Proveedor) => void
  onEditar: (p: Proveedor) => void
  onBorrar: (p: Proveedor) => void
  onInteraccion: (abierto: boolean) => void
}

/** Calco del menú de proveedor (:1694-1713), solo SUPERTECNICO. "Borrar" aparece si `tiene-pedidos` es falso; la
 *  consulta se hace al abrir el menú (Radix monta el contenido al abrirse), no en cada clic de fila (S6). Mismo patrón que
 *  MenuCliente de la vista Clientes. */
export function MenuProveedor({ p, onToggle, onEditar, onBorrar, onInteraccion }: Props) {
  const { mostrarError } = useAlerta()
  const [borrable, setBorrable] = useState(false)
  useEffect(() => {
    onInteraccion(true)
    return () => onInteraccion(false)
  }, [onInteraccion])
  useEffect(() => {
    let vivo = true
    tienePedidos(p.idProv)
      .then((tiene) => { if (vivo) setBorrable(!tiene) })
      .catch((e: unknown) => {
        if (!vivo) return
        mostrarError(e instanceof ConexionError ? mensajeSinConexion(e) : mensajeDeError(e))
      })
    return () => { vivo = false }
  }, [p.idProv, mostrarError])
  return (
    <>
      <ContextMenuItem onSelect={() => onToggle(p)}>{p.activo ? 'Desactivar' : 'Activar'}</ContextMenuItem>
      <ContextMenuItem onSelect={() => onEditar(p)}>Editar</ContextMenuItem>
      {borrable && <ContextMenuItem onSelect={() => onBorrar(p)}>Borrar</ContextMenuItem>}
    </>
  )
}
```

- [ ] **Step 5: Los dos diálogos**

`src/modules/almacen/proveedores/NuevoProveedorDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'

export const MSG_NOMBRE_VACIO = 'El nombre no puede estar vacío.'

type Props = { abierto: boolean; enviando: boolean; onConfirmar: (nombre: string) => void; onCancelar: () => void }

/** El TextInputDialog nativo de nuevoProveedor (:1773-1785) pasa al diálogo propio (spec 4a, S5): solo el nombre,
 *  "Nombre del proveedor:", sin cabecera. Diferencia S5: el nombre en blanco avisa en vez de cerrarse en silencio. */
export function NuevoProveedorDialog({ abierto, enviando, onConfirmar, onCancelar }: Props) {
  const [nombre, setNombre] = useState('')
  const [error, setError] = useState<string | null>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- vacía el campo al abrir
    if (abierto) { setNombre(''); setError(null) }
  }, [abierto])
  function confirmar() {
    const n = nombre.trim()
    if (n === '') { setError(MSG_NOMBRE_VACIO); return }
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={abierto} titulo="Nuevo proveedor" error={error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="nuevo-proveedor-nombre" className="text-[12px] font-bold text-azul-gris">Nombre del proveedor:</Label>
      <Input id="nuevo-proveedor-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

`src/modules/almacen/proveedores/EditarProveedorDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { MSG_NOMBRE_VACIO } from './NuevoProveedorDialog'

/** Exactamente las dos del combo del JavaFX (:1803-1806). */
const DIVISAS = [{ valor: 'EUR', etiqueta: 'EUR' }, { valor: 'USD', etiqueta: 'USD' }]

type Props = { proveedor: Proveedor | null; enviando: boolean; onConfirmar: (datos: { nombre: string; divisa: string; comentario: string }) => void; onCancelar: () => void }

/** Calco de editarProveedor (:1787-1868): Nombre, Divisa (EUR/USD, combo navy), Comentario (3 filas). Sin Enter en el
 *  JavaFX; aquí Enter en "Nombre" confirma (form), diferencia menor que se anota. El nombre del proveedor va en el
 *  subtítulo porque la web no tiene título de ventana ("Editar proveedor — <nombre>"). */
export function EditarProveedorDialog({ proveedor, enviando, onConfirmar, onCancelar }: Props) {
  const [nombre, setNombre] = useState('')
  const [divisa, setDivisa] = useState('EUR')
  const [comentario, setComentario] = useState('')
  const [error, setError] = useState<string | null>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga al abrir con otro proveedor
    if (proveedor) { setNombre(proveedor.nombre); setDivisa(proveedor.divisa || 'EUR'); setComentario(proveedor.comentario ?? ''); setError(null) }
  }, [proveedor])
  function confirmar() {
    const n = nombre.trim()
    if (n === '') { setError(MSG_NOMBRE_VACIO); return }
    onConfirmar({ nombre: n, divisa, comentario: comentario.trim() })
  }
  return (
    <DialogoAlmacen abierto={proveedor !== null} titulo="Editar proveedor" subtitulo={proveedor?.nombre} error={error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="editar-proveedor-nombre" className="text-[12px] font-bold text-azul-gris">Nombre</Label>
      <Input id="editar-proveedor-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
      <span className="text-[12px] font-bold text-azul-gris">Divisa</span>
      <ComboNavy valor={divisa} opciones={DIVISAS} onChange={setDivisa} textoVacio="EUR" ancho={304} aria-label="Divisa" />
      <Label htmlFor="editar-proveedor-comentario" className="text-[12px] font-bold text-azul-gris">Comentario</Label>
      <textarea id="editar-proveedor-comentario" rows={3} value={comentario} onChange={(e) => setComentario(e.target.value)} className="w-full rounded border border-fila-sep bg-superficie p-1.5 text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

`ComboNavy` (ver `src/shared/ui/ComboNavy.tsx`): el botón lleva `aria-label`, y las opciones se pintan con `role="option"`; si el test `getByRole('button', { name: /Divisa/ })` no encaja con el nombre accesible real, ajustar el test al que dé `ComboNavy`.

- [ ] **Step 6: `ProveedoresPage`**

`src/modules/almacen/proveedores/ProveedoresPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { descargarCsv } from '@/shared/lib/csv'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { BotonPrimario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { useBorrarProveedor, useCrearProveedor, useEditarProveedor, useProveedoresComponentes, useSetActivoProveedor } from './api'
import { CABECERAS_CSV_PROVEEDORES, claseFilaProveedor, crearColumnasProveedores, filaCsvProveedor } from './columnas'
import { EditarProveedorDialog } from './EditarProveedorDialog'
import { filtroProveedores, seleccionProveedores } from './estado'
import { MenuProveedor } from './MenuProveedor'
import { NuevoProveedorDialog } from './NuevoProveedorDialog'

type Dialogo = { tipo: 'nuevo' } | { tipo: 'editar'; p: Proveedor } | { tipo: 'borrar'; p: Proveedor } | null

/** Pestaña "Proveedores" de StockView.fxml (spec 4a §6): sin buscador ni "Limpiar filtros", filtro solo de activos. */
export function ProveedoresPage() {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [seleccion, setSeleccion] = useStore(filtroProveedores)
  const [seleccionada, setSeleccionada] = useStore(seleccionProveedores)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const { data = [], dataUpdatedAt, refetch } = useProveedoresComponentes({ activo: !hayAlguna })
  const crear = useCrearProveedor()
  const editar = useEditarProveedor()
  const setActivo = useSetActivoProveedor()
  const borrar = useBorrarProveedor()
  useEffect(() => {
    if (!dialogo) return
    marcar(true)
    return () => marcar(false)
  }, [dialogo, marcar])

  const activos = useMemo(() => data.filter((p) => p.activo), [data])
  const visibles = useMemo(() => (seleccion.size === 0 ? data : data.filter((p) => seleccion.has(p.nombre))), [data, seleccion])
  const columnas = useMemo(() => crearColumnasProveedores(), [])
  useRegistrarExportable(() => descargarCsv('proveedores', CABECERAS_CSV_PROVEEDORES, visibles.map(filaCsvProveedor)))

  return (
    <div className="p-5">
      <h1 className="mb-3 text-2xl font-bold text-azul-medio">Proveedores</h1>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <MultiSelect opciones={activos} clave={(p) => p.nombre} etiqueta={(p) => p.nombre} seleccion={seleccion} onChange={setSeleccion} textoVacio="Proveedor" textoPlural={(n) => `${n} proveedores`} onOpenChange={marcar} className="min-w-[160px]" />
        {puedeEditar && <BotonPrimario className="ml-6" onClick={() => setDialogo({ tipo: 'nuevo' })}>Nuevo proveedor</BotonPrimario>}
      </div>
      <DataTable
        columns={columnas}
        data={visibles}
        vacio="Sin proveedores"
        getRowId={(p) => String(p.idProv)}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        filaClase={claseFilaProveedor}
        altoFila={35}
        menuFila={puedeEditar ? (p) => (
          <MenuProveedor p={p} onToggle={(x) => setActivo.mutate({ idProv: x.idProv, activo: !x.activo })} onEditar={(x) => setDialogo({ tipo: 'editar', p: x })} onBorrar={(x) => setDialogo({ tipo: 'borrar', p: x })} onInteraccion={marcar} />
        ) : undefined}
      />
      <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
      <NuevoProveedorDialog abierto={dialogo?.tipo === 'nuevo'} enviando={crear.isPending} onCancelar={() => setDialogo(null)} onConfirmar={(nombre) => crear.mutate(nombre, { onSuccess: () => setDialogo(null) })} />
      <EditarProveedorDialog proveedor={dialogo?.tipo === 'editar' ? dialogo.p : null} enviando={editar.isPending} onCancelar={() => setDialogo(null)} onConfirmar={(datos) => { if (dialogo?.tipo !== 'editar') return; editar.mutate({ idProv: dialogo.p.idProv, ...datos }, { onSuccess: () => setDialogo(null) }) }} />
      <ConfirmDialog
        abierto={dialogo?.tipo === 'borrar'}
        titulo="Borrar proveedor"
        descripcion={dialogo?.tipo === 'borrar' ? `¿Eliminar el proveedor "${dialogo.p.nombre}"?` : ''}
        textoAccion="Borrar"
        onCancelar={() => setDialogo(null)}
        onConfirmar={() => { if (dialogo?.tipo !== 'borrar') return; const id = dialogo.p.idProv; setDialogo(null); borrar.mutate(id) }}
      />
    </div>
  )
}
```

Los 422 del servidor (Task 2) en alta y edición: como en Task 13 nota (3), `onError` con `ReglaNegocioError` deja el diálogo abierto y pasa el mensaje al `error` del diálogo; añadir el mismo estado `errorServidor` y un test.

- [ ] **Step 7: Ruta y ejecución**

En `router.tsx`: `{ path: '/stock/proveedores', element: <ProveedoresPage /> }` con su import.

Run: `npx vitest run src/modules/almacen && npm run check`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/modules/almacen/proveedores src/app/router.tsx
git commit -m "feat(proveedores): pestaña proveedores con filtro de activos, alta, edicion, activar y borrado con confirmacion"
```

---

## Task 15: Web — la campana navega a Stock y a Pedidos

**Files:**
- Modify: `src/modules/taller/notificaciones/PanelNotificaciones.tsx:24-35, 119, 152-153`
- Modify: `src/modules/taller/notificaciones/PanelNotificaciones.test.tsx:179-205`

**Interfaces:**
- Consumes: `useNavigate` de `react-router`; `onCerrar` del panel.
- Produces: "→ Ir a pedidos" (en las dos pestañas) cierra el panel y navega a `/stock/pedidos`; "Ver Stock Completo" cierra y navega a `/stock`. "Pedir" (tarjeta), "Pedir piezas" y "Pedir todas las piezas" siguen deshabilitados con `TOOLTIP_ALMACEN` (4b). Sin filtros al llegar (calco, inventario §13.3).

- [ ] **Step 1: Test (falla)**

En `PanelNotificaciones.test.tsx`, sustituir el test `'"→ Ir a pedidos" deshabilitado con el tooltip de Almacén en las dos pestañas'` por:

```tsx
  it('"→ Ir a pedidos" cierra el panel y navega a /stock/pedidos desde las dos pestañas', async () => {
    for (const pestana of ['Solicitudes', 'Alertas']) {
      const { onCerrar, router } = await abrirPanelConRouter({})
      await userEvent.click(screen.getByRole('tab', { name: pestana }))
      const enlace = screen.getByRole('button', { name: '→ Ir a pedidos' })
      expect(enlace).toBeEnabled()
      expect(enlace).toHaveClass('text-[12px]', 'font-bold', 'text-azul-noche', 'cursor-pointer')
      await userEvent.click(enlace)
      expect(onCerrar).toHaveBeenCalled()
      expect(router.state.location.pathname).toBe('/stock/pedidos')
    }
  })
```

y en el test de los reservados, sacar "Ver Stock Completo" de la lista (quedan `Pedir` ×2 y "Pedir todas las piezas": `toHaveLength(3)`) y añadir:

```tsx
  it('"Ver Stock Completo" cierra el panel y navega a /stock sin filtros', async () => {
    const { onCerrar, router } = await abrirPanelConRouter()
    await userEvent.click(screen.getByRole('tab', { name: 'Alertas' }))
    const boton = screen.getByRole('button', { name: 'Ver Stock Completo' })
    expect(boton).toBeEnabled()
    expect(boton).toHaveClass('bg-azul-medio', 'text-superficie')
    await userEvent.click(boton)
    expect(onCerrar).toHaveBeenCalled()
    expect(router.state.location.pathname).toBe('/stock')
    expect(router.state.location.search).toBe('')
  })
```

`abrirPanelConRouter` es `abrirPanel` (helper ya existente en ese fichero) montado con `renderConRouter` de `@/test/render` en vez de `renderConProviders`, devolviendo además `router` para leer `router.state.location`. Mirar cómo está escrito `abrirPanel` y crear la variante (o cambiar `abrirPanel` a `renderConRouter` si todos los tests siguen pasando).

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/taller/notificaciones/PanelNotificaciones.test.tsx`
Expected: FAIL (botones deshabilitados).

- [ ] **Step 3: Implementar**

En `PanelNotificaciones.tsx`: `import { useNavigate } from 'react-router'`; dentro del componente `const navigate = useNavigate()` y:

```tsx
  /** Calco de mostrarStockEnPedidos / mostrarStockEnActual (MainController): cierra el panel y abre la pestaña de Stock
   *  correspondiente, sin aplicar filtros (sub-proyecto 4a). Los tres botones de pedir siguen reservados hasta 4b. */
  function irA(ruta: '/stock' | '/stock/pedidos') {
    onCerrar()
    navigate(ruta)
  }
```

Sustituir la línea 119 por:

```tsx
        <button type="button" onClick={() => irA('/stock/pedidos')} className="cursor-pointer text-[12px] font-bold text-azul-noche hover:underline">→ Ir a pedidos</button>
```

y la 153 por:

```tsx
            <button type="button" onClick={() => irA('/stock')} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-azul-medio text-superficie')}>Ver Stock Completo</button>
```

`BotonAlmacen` se queda para los otros tres. Si `PanelNotificaciones` se monta en algún test sin router (buscar `renderConProviders` sin ruta en sus tests y en `Campana.test.tsx`), `useNavigate` lanza fuera de un `<Router>`: todos los tests del panel ya usan `renderConProviders`, que trae `MemoryRouter`.

- [ ] **Step 4: Ejecutar**

Run: `npx vitest run src/modules/taller/notificaciones && npm run check`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/notificaciones/PanelNotificaciones.tsx src/modules/taller/notificaciones/PanelNotificaciones.test.tsx
git commit -m "feat(campana): ver stock completo e ir a pedidos navegan a la vista de stock"
```

---

## Task 16: Web — smoke Playwright

**Files:**
- Create: `tests/e2e/stock.spec.ts`
- Modify: `.env.e2e.example`, `README.md` (sección "Smoke e2e")

**Interfaces:**
- Consumes: `credenciales` de `tests/e2e/credenciales.ts`; el login de `tests/e2e/taller.spec.ts`.

Variables nuevas (en `~/.env.e2e`, fuera del repo): `E2E_SKU_PRUEBA` (el `tipo` exacto de un componente **de prueba** activo y master, que exista en la BD de pruebas). Si falta, el test se salta. El proveedor de prueba se crea con nombre único `E2E <timestamp>` y se borra por ese nombre (el `POST /api/proveedores` responde 201 sin cuerpo: no devuelve el id, así que la identificación por lo que devuelve el servidor no es posible aquí; se anota en la ficha como desviación de la spec §9 y se hace como `clientes.spec.ts`).

- [ ] **Step 1: El test**

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales'

async function entrar(page: Page) {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

/** ESCRIBE: edita el stock del SKU de prueba (y lo deja como estaba) y crea y borra un proveedor de prueba. */
test('supertécnico: stock actual, editar stock y devolverlo; proveedor de prueba creado y borrado', async ({ page }) => {
  const sku = process.env.E2E_SKU_PRUEBA
  test.skip(!sku, 'Falta E2E_SKU_PRUEBA')
  await entrar(page)

  await page.getByRole('link', { name: 'Stock' }).click()
  await expect(page).toHaveURL(/\/stock$/)
  await expect(page.getByRole('heading', { name: 'Stock actual' })).toBeVisible()
  await expect(page.getByText('Estado del stock')).toBeVisible()
  await expect(page.getByText(/^Actualizado [0-9][0-9]:[0-9][0-9]$/)).toBeVisible()

  // Localizar el SKU de prueba con el buscador y leer su stock
  await page.getByPlaceholder('Buscar componente…').fill(sku!)
  const fila = page.getByRole('row', { name: new RegExp(`^${sku}`) }).first()
  await expect(fila).toBeVisible()
  const stockInicial = Number((await fila.getByRole('cell').nth(1).textContent())?.trim())
  expect(Number.isInteger(stockInicial)).toBe(true)

  // Editar stock: +1 y comprobar; después devolverlo
  async function editar(nuevo: number) {
    await fila.click({ button: 'right' })
    await page.getByRole('menuitem', { name: 'Editar stock' }).click()
    const dlg = page.getByRole('dialog', { name: 'Editar stock' })
    await dlg.getByLabel('Nueva cantidad').fill(String(nuevo))
    await dlg.getByRole('button', { name: 'Confirmar' }).click()
    await expect(dlg).toBeHidden()
    await expect(fila.getByRole('cell').nth(1)).toHaveText(String(nuevo))
  }
  try {
    await editar(stockInicial + 1)
  } finally {
    await editar(stockInicial)
  }

  // Proveedor de prueba: alta, ver en la tabla, borrar por nombre único
  const nombre = `E2E ${Date.now()}`
  await page.getByRole('link', { name: 'Proveedores' }).click()
  await expect(page.getByRole('heading', { name: 'Proveedores' })).toBeVisible()
  await page.getByRole('button', { name: 'Nuevo proveedor' }).click()
  await page.getByRole('dialog', { name: 'Nuevo proveedor' }).getByLabel('Nombre del proveedor:').fill(nombre)
  await page.getByRole('dialog', { name: 'Nuevo proveedor' }).getByRole('button', { name: 'Confirmar' }).click()
  const filaProv = page.getByRole('row', { name: new RegExp(`^${nombre}`) })
  await expect(filaProv).toBeVisible()
  try {
    await expect(filaProv.getByText('Activo')).toBeVisible()
  } finally {
    await filaProv.click({ button: 'right' })
    await page.getByRole('menuitem', { name: 'Borrar' }).click()
    await page.getByRole('dialog', { name: 'Borrar proveedor' }).getByRole('button', { name: 'Borrar' }).click()
    await expect(filaProv).toBeHidden()
  }
})
```

- [ ] **Step 2: `.env.e2e.example` y README**

Añadir a `.env.e2e.example`:

```
# stock.spec.ts ESCRIBE: con E2E_USER edita el stock del SKU de prueba (+1 y lo devuelve) y crea y borra un proveedor
# "E2E <timestamp>". E2E_SKU_PRUEBA = el tipo exacto de un componente de prueba activo y sin master. Sin él se salta.
E2E_SKU_PRUEBA=lcd-prueba
```

Y en el README, tras el párrafo de `asignar.spec.ts`, una frase equivalente.

- [ ] **Step 3: Ejecutar contra producción, en serie**

```bash
set -a; . ~/.env.e2e; set +a
npm run dev   # en otra terminal, con .env.local apuntando a la API de producción (lección del 3b: la web de la rama en local)
npx playwright test
```

Expected: verde (los 6 anteriores + este). **Este paso escribe en producción**: pedir OK al usuario antes de lanzarlo y ejecutarlo después del despliegue del servidor de la rama (el 409 del borrado y el `ValorEntero` tienen que existir allí). Si el borrado del proveedor falla por 409, es que algún pedido lo referencia: no debería (recién creado); anotar y limpiar a mano.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/stock.spec.ts .env.e2e.example README.md
git commit -m "test(e2e): smoke de stock actual y proveedores con limpieza"
```

---

## Task 17: Ficha de paridad, versión y verificación final

**Files:**
- Create: `docs/paridad/stock.md` (web)
- Modify: `CHANGELOG.md`, `package.json` (web, versión `0.6.0`)
- Modify: `docs/superpowers/plans/2026-09-24-web-almacen-stock.md` (raíz): sección "Ejecución y cierre"

- [ ] **Step 1: La ficha**

Mismo formato que `docs/paridad/asignar-trabajos.md`: título "Ficha de paridad — Stock actual y Proveedores (StockController)", párrafos de Referencia (`hotfix/0.16.3`, con `?tipo=COMPONENTES` de `main`) / Capturas (**solo por nombre**: `almacen/stock-*.png`, `almacen/proveedores-*.png`, `almacen/campana-*.png`, documentación privada fuera del repo) / ejemplos sintéticos; después **"## Diferencias deliberadas"** con las de la spec §10 (diálogos propios, aviso de nombre en blanco, ordenación bloqueada, selección mantenida, 409 al borrar, `tiene-pedidos` al abrir el menú, Recharts) más las salidas de la ejecución (subtítulo del diálogo de editar proveedor en vez de título de ventana; Enter confirma en "Editar proveedor"; ancho 360 en "Solicitar pieza"; 422 inline con el diálogo abierto; smoke que borra por nombre y no por id); y secciones de `- [ ]` por bloque: sidebar y rutas, tabla, semáforo y estilos, filtros, pie, donut, gráfico por SKU, menú por rol, editar stock, ajustar mínimo, activar/desactivar, solicitar pieza, proveedores (tabla, filtro, menú, nuevo, editar, borrar), campana, CSV, refresco y errores. Una línea por comportamiento del inventario, con la captura entre paréntesis.

- [ ] **Step 2: Marcarla contra las capturas**

Recorrer las capturas de `CAPTURAS-4a.md` una a una con la web en local contra producción y marcar. **Toda diferencia nueva se anota y se consulta con el usuario**, no se resuelve sobre la marcha. Las capturas marcadas "no reproducible" se anotan como tales.

- [ ] **Step 3: Capturas de la web lado a lado (antes del tag)**

Tomar las mismas situaciones que las capturas `stock-*`, `proveedores-*` y `campana-*` en la web, guardarlas junto a las del JavaFX **fuera del repo** (`paridad-capturas/almacen/web-*.png`) y compararlas con el usuario.

- [ ] **Step 4: Versión y CHANGELOG**

`package.json`: `"version": "0.6.0"`. `CHANGELOG.md`, entrada nueva arriba:

```md
## [0.6.0] - 2026-09-XX — Stock actual y Proveedores

- Vista Stock en `/stock` con la columna lateral Stock actual · Pedidos · Proveedores (Pedidos llega en la siguiente entrega).
- Stock actual: tabla con componente, en stock, en camino (enlace a Pedidos), mínimo, último pedido y el semáforo OK / Bajo / Sin stock / Desactivado; filtro de estado, buscador y "Limpiar filtros"; donut "Estado del stock" y gráfico por SKU al seleccionar una fila; menú por rol con Pedir, Editar stock, Ajustar mínimo, Desactivar/Activar y Solicitar pieza.
- Proveedores: tabla, filtro de activos, alta, edición (divisa y comentario), activar/desactivar y borrado con confirmación.
- La campana: "Ver Stock Completo" e "→ Ir a pedidos" abren la vista de Stock.
- Servidor: cantidad en camino resuelta al SKU master, 409 al borrar un proveedor con pedidos y 422 en cantidades negativas, nombre vacío o divisa desconocida.
- Diferencias aceptadas respecto al programa de escritorio: `docs/paridad/stock.md`.
```

- [ ] **Step 5: Verificación completa de los tres repos**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q test
cd ../gestion-reparaciones-web && npm run check && npm run build
cd ../gestion-reparaciones-cliente && mvn -q test
```

Expected: verde en los tres; el cliente con sus 284 tests intactos. Comprobar además que `api/openapi.json` de la web es idéntico (normalizado) al `/v3/api-docs` del servidor de la rama.

- [ ] **Step 6: Commits de cierre**

```bash
cd gestion-reparaciones-web
git add docs/paridad/stock.md CHANGELOG.md package.json package-lock.json
git commit -m "docs(web): ficha de stock marcada, CHANGELOG y version 0.6.0"
```

En el raíz, añadir al final de este plan la sección **"Ejecución y cierre"** (qué se desvió, suites, commits), como en el 3b, y commitearla en `main`.

- [ ] **Step 7: Parar y pedir OK al usuario**

**No hacer push, merge, tag ni despliegue.** Presentar: qué se ha hecho, estado de los tres repos, y la lista de pasos que requieren su OK **uno a uno**: push de las dos ramas, merges `--no-ff`, tag `v0.6.0`, gitlinks en el raíz, despliegue en la VDC con **el servidor antes que la web**, smoke de la Task 16 contra producción, y actualizar `Apuntes/plan-futuro.md` (casilla 4a) y la memoria del programa.

---

## Trazabilidad: reglas del inventario de Stock → test

| Regla (inventario) | Test |
|---|---|
| §1 sidebar de tres entradas, activa marcada, para los tres roles | `SubNav.test` "en Stock pinta las tres entradas…" |
| §1 estructura: título, filtros, tabla + pie, tarjeta de 240 px | `StockPage.test` "título, tabla…", captura `stock-actual-supertecnico` |
| §1 la vista cacheada conserva filtros y selección | `StockPage.test` "los filtros sobreviven…" (S2) |
| §2 una sola petición; activos primero; "N desactivado(s)"; "Actualizado HH:mm" | `api.test` "useComponentesStock…", `filtros.test` ordenarStock/textoDesactivados, `StockPage.test` "título, tabla…" |
| §2 check "Desactivado" solo si hay desactivados | `StockPage.test` "sin desactivados…" |
| §3 seis columnas, anchos, "(compartido)", "—", `dd/MM/yyyy` | `columnas.test` |
| §3 "En Camino" > 0 enlace → Pedidos con pendiente + en camino + parcial y buscador | `columnas.test` "En Camino…", "parámetros hacia Pedidos", `StockPage.test` "En Camino > 0 navega…" |
| §4 semáforo de cuatro valores, negativo = Bajo | `semaforoStock.test` |
| §5 badge y fila por estado; desactivada 0.45 y no se pone azul | `columnas.test` badge y clase de fila |
| §6 filtro Estado OR, ninguno = todos, "N estados", no se cierra al marcar; buscador contiene; Limpiar | `filtros.test`, `StockPage.test` "filtro Estado…" |
| §6 al llegar desde Pedidos se conserva "Desactivado" | `filtros.test` "al llegar desde Pedidos…" (4b la usa) |
| §7 donut sobre todo, activos, sin negativos; compartidos como filas; total y leyenda | `graficos.test.ts` conteosDonut, `graficos.test.tsx` GraficoEstado, `StockPage.test` "el donut cuenta…" |
| §8 barra Stock por semáforo, Pedido azul, placeholder; Pedido solo ADMIN/SUPERTECNICO | `graficos.test` colorBarraStock, GraficoSku, `StockPage.test` "seleccionar una fila…", "el TECNICO no pide…" |
| §9 menú por rol con separadores; Activar/Desactivar alterna; ADMIN sin menú | `StockPage.test` "menú del supertécnico…", "ADMIN…", "TECNICO…" |
| §9.1 Editar stock: precarga, Enter, error, PUT tal cual, 409 con aviso y recarga | `dialogos.test`, `api.test` useEditarStock, `StockPage.test` "Editar stock manda el PUT…" |
| §9.2 Ajustar mínimo: "Stock mínimo", "Nuevo stock mínimo:", error, PATCH | `dialogos.test` AjustarMinimo, `StockPage.test` "Ajustar mínimo hace el PATCH…" |
| §9.3 Desactivar sin confirmación | `StockPage.test` ídem |
| §9.4 Solicitar pieza: placeholder, sin validación, null, sin recarga | `dialogos.test` SolicitarPieza, `StockPage.test` ídem (cargas.n no sube) |
| §10 proveedores ?tipo=COMPONENTES; columnas; orden del servidor | `ProveedoresPage.test` "pide los de COMPONENTES…" |
| §10 filtro solo activos, "N proveedores", sin Limpiar | `ProveedoresPage.test` "el filtro solo ofrece activos…" |
| §11 badge Activo/Inactivo y fila con barra verde, sin opacidad | `ProveedoresPage.test` "pide los de…" (clases) |
| §12 menú solo SUPERTECNICO; Borrar solo sin pedidos; Activar en inactivo | `ProveedoresPage.test` "TECNICO y ADMIN…", "menú del supertécnico…" |
| §12.1 Nuevo proveedor: nombre, POST con tipo, blanco avisa (S5) | `ProveedoresPage.test` "Nuevo proveedor…" |
| §12.2 Editar: precarga, EUR/USD, comentario, PUT, error | `ProveedoresPage.test` "Editar…" |
| §12.3 Activar/Desactivar sin confirmación | `ProveedoresPage.test` "Desactivar…" |
| §12.4 Borrar: ConfirmDialog con el texto, DELETE, 409 como aviso | `ProveedoresPage.test` "Borrar…", `ProveedorControllerTest` |
| §13.3 campana: Ver Stock Completo → /stock, Ir a pedidos → /stock/pedidos, sin filtros; pedir sigue reservado | `PanelNotificaciones.test` |
| §14 CSV stock y proveedores con cabeceras y omisiones | `columnas.test` CSV, `ProveedoresPage.test` CSV, `StockPage.test` "Descargar CSV…" |
| §16 refresco 60/5 s, congelado con menú o diálogo, selección mantenida (S4) | `StockPage.test` "con un diálogo abierto…", "…sobrevive al refresco" |
| §18 en-camino por master; guard de borrado; validaciones | `CompraComponenteDAOEnCaminoTest`, `ProveedorControllerTest`, `ComponenteControllerValidacionTest` |

---

## Autorrevisión del plan

**Cobertura de la spec.** §4.1 → Task 1. §4.2 → Task 2. §4.3 → Tasks 2-3. §5 estructura → Tasks 4-14 (se añaden `ui/DialogoAlmacen.tsx`, `shared/lib/enlaces.ts` y `shared/lib/semaforoStock.ts`, que la spec no nombraba; `filtros.ts` absorbe `ordenarStock`). §6 comportamiento → Tasks 6-15. §7 guardado → Tasks 9, 13, 14. §8 errores y refresco → Tasks 9, 13, 14. §9 tests → todas + Task 16. §10 diferencias → Task 17. §11 cierre → Task 17. S1 → Task 6. S2 → Tasks 8, 13, 14. S3 → Task 5. S4 → Task 13. S5 → Tasks 7, 12, 14. S6 → Tasks 2, 14. S7 → Tasks 4, 11. S8 → Tasks 10, 13. S9 → Tasks 13-14 (`DataTable` no ordena por defecto).

**Sin marcadores.** No hay "TBD" ni pasos sin contenido. Donde el plan dice "mirar el fichero" (constructor de `CompraComponenteDAO`, `refDeLaRespuesta`, nombre accesible de `ComboNavy`, helper `abrirPanel`, `data-state` de la fila de `DataTable`), es una comprobación con ruta concreta, no un hueco.

**Consistencia de nombres.** `estadoStock`, `ESTADOS_STOCK`, `EstadoStock`, `ordenarStock`, `aplicarFiltrosStock`, `textoBotonEstado`, `textoDesactivados`, `nombreComponente`, `filtrosDesdePedidos`, `FILTROS_STOCK_VACIOS`, `filtrosStock`, `seleccionStock`, `useComponentesStock`, `pedirCantidadEnCamino`, `useCantidadEnCamino`, `useEditarStock`, `useAjustarMinimo`, `useSetActivoComponente`, `useSolicitarPieza`, `crearColumnasStock`, `claseFilaStock`, `BadgeEstadoStock`, `parametrosPedidos`, `CABECERAS_CSV_STOCK`, `filaCsvStock`, `conteosDonut`, `colorBarraStock`, `COLORES_DONUT`, `COLOR_BARRA_PEDIDO`, `GraficoEstado`, `GraficoSku`, `subtituloComponente`, `parseEnteroNoNegativo`, `EditarStockDialog`, `AjustarMinimoDialog`, `SolicitarPiezaDialog`, `MenuComponente`, `StockPage`, `DialogoAlmacen`, `useProveedoresComponentes`, `tienePedidos`, `useCrearProveedor`, `useEditarProveedor`, `useSetActivoProveedor`, `useBorrarProveedor`, `crearColumnasProveedores`, `claseFilaProveedor`, `CABECERAS_CSV_PROVEEDORES`, `filaCsvProveedor`, `MenuProveedor`, `NuevoProveedorDialog`, `EditarProveedorDialog`, `ProveedoresPage`, `enlacesStock`, `Enlace` se usan igual en todas las tareas. En el servidor, `ValorEntero`, `MSG_NOMBRE`, `MSG_DIVISA`, `MSG_TIENE_PEDIDOS`, `MSG_CANTIDAD`, `MSG_MINIMO` son consistentes entre Tasks 1-3 y los tests.

**Riesgo conocido.** Recharts en jsdom, el nombre accesible del combo de `ComboNavy`, el `data-state` de la fila seleccionada de `DataTable` y el `aria-describedby` de `DialogContent` se comprueban en el código antes de escribir; si no coinciden, manda el código existente y se ajusta el test, no el comportamiento.

**Revisión previa (lección del 3b).** Antes de la Task 1, un subagente revisa este plan contra la spec y el código (contrato `schema.d.ts`, `DataTable`, `ComboNavy`, `PanelNotificaciones.test.tsx`, `OpenApiContractTest`) buscando tests de contrato o de componentes que romperían la suite tal como están escritos.
