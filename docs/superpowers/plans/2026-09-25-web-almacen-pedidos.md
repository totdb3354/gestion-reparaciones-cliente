# Sub-proyecto 4b — Pedidos, formularios de pedido y campana: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el "Pendiente de migrar" de `/stock/pedidos` por la pestaña Pedidos del JavaFX (toggle Componentes | Otros, filtros, tablas con colores y badges, menú de transiciones con sus diálogos, CSV) y los cuatro formularios de pedido abiertos como modal desde Pedidos, Stock actual y la campana, subiendo al servidor la máquina de estados, los rangos, la validación, el cálculo de `precioEur` con la tasa en el sentido correcto y dos endpoints de alta por lotes con `Idempotency-Key` que marcan las solicitudes gestionadas.

**Architecture:** El servidor gana guards y validaciones en los DAO y controladores de compras existentes, un servicio `ConversionEur`, un servicio transaccional `CompraLoteService` con dos endpoints de lote sobre `RegistroIdempotencia`, y nullables en el contrato. La web gana `modules/almacen/pedidos/` (página, helpers puros con tests, api, columnas, menú, diálogos de cantidad y los cuatro formularios) sobre `DataTable`, `TogglePill`, `MultiSelect`, `RangoFechas`, `ConfirmDialog`, `DialogoAlmacen`, `ComboNavy` y `CampoAutocompletar`; el formulario de alta se abre desde un store compartido (`shared/lib/formularioPedido.ts`) y lo pinta un host montado en el shell, para que Stock actual, Pedidos y la campana (módulo `taller`) lo abran sin importar de `almacen`.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + JUnit 5 + Mockito + MockMvc. Web React 19 + TypeScript + TanStack Query + openapi-fetch + Vitest + Testing Library + MSW + Playwright.

**Spec:** [`docs/superpowers/specs/2026-09-25-web-almacen-pedidos-design.md`](../specs/2026-09-25-web-almacen-pedidos-design.md) (decisiones P1-P10 vinculantes) y [`2026-09-24-web-almacen-programa-design.md`](../specs/2026-09-24-web-almacen-programa-design.md) (D1-D16). **Referencia de detalle:** el inventario `inventario-pedidos.md`, guardado fuera del repo (lo tiene el controlador de la sesión; si una regla de este plan no cuadra con el JavaFX, manda el JavaFX y se consulta).

## Global Constraints

- **Ramas:** `feature/web-pedidos` en `gestion-reparaciones-web` y en `gestion-reparaciones-servidor`, creadas desde `main` (T1 y T7). El repo raíz se queda en `main`.
- **Nunca** `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Commits sin `Co-Authored-By`.** Mensajes en español, en minúscula tras el prefijo. Antes de cada commit de un subagente, `git cat-file -p HEAD | tail -1` no puede contener `Co-Authored-By` (lección del 4a).
- **Los tres repos son públicos:** ningún dato real (SKUs reales, proveedores reales, nombres de técnicos, dominios, IPs) en código, tests, comentarios ni documentación. SKUs sintéticos (`lcd-x-negro`, `bat-x`, `bat-y`), proveedores "Proveedor A", "Proveedor B", "ACME", conceptos "Cinta de embalar".
- **El cliente JavaFX NO se toca.** Se consulta en solo lectura con `git show hotfix/0.16.3:gestion-reparaciones-cliente/<ruta>` desde el raíz.
- **Todo el trabajo de servidor es aditivo:** ninguna respuesta que el JavaFX consuma cambia de forma; los guards de estado son exactamente las transiciones que ofrece el menú del cliente; `precioEur` de las peticiones se conserva en el contrato (nullable, ignorado).
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`. Comandos por Bash antes que PowerShell.
- **Antes de cada tarea, buscar en el repo lo que va a crear** (lección del 3a, 3b y 4a): si ya existe, se reutiliza y se anota la desviación.
- **Cada petición se verifica contra el contrato OpenAPI real** (`src/shared/api/schema.d.ts`, regenerado en T7) antes de escribir el código que la usa. Si un nombre de esquema no coincide con este plan, manda el contrato.
- **Textos visibles exactos** (se copian tal cual de la spec §6): ninguno se reescribe "mejorado". Los que la spec corrige (P7) están marcados.
- **Un módulo no importa de otro módulo** (regla de lint del repo): lo compartido entre `taller` y `almacen` va a `src/shared` (`formularioPedido.ts`, `clavesIdempotencia.ts`, `importes.ts`).
- **Un fichero que exporta un componente no exporta helpers** (react-refresh): helpers a `.ts` propios.
- **Los revisores comprueban `npm run lint`** en cada tarea de web; un warning "preexistente" se demuestra con `git stash`, no se afirma.
- **Testing Library no normaliza el texto buscado:** para textos con espacios múltiples (`'  (1 USD = 0,8797 €)'`, subtítulos con `   ·   `) usar `getByText(texto, { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })`.
- **El puerto 5173** no lo puede ocupar un dev server viejo al sacar capturas o correr el smoke (lección del 4a).
- **Capturas de la web lado a lado con las del JavaFX ANTES del merge** de la rama web (lección del 4a).
- **Tests con timeout intermitente (>5 s) cuando hay varios procesos node:** relanzar el fichero aislado antes de diagnosticar.

---

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor`, rutas bajo `src/main/java/com/reparaciones/servidor/` y `src/test/java/com/reparaciones/servidor/`)

| Fichero | Responsabilidad |
|---|---|
| `dao/CompraComponenteDAO.java`, `dao/CompraOtroDAO.java` | *Modificar.* Transiciones con `UPDATE … AND ESTADO=?` y 409 (T1); `insertar` devuelve el id generado (T4, T5) |
| `controller/CompraController.java`, `controller/CompraOtroController.java` | *Modificar.* Log `CONFIRMAR_ALTERADO` (T1); validaciones 422 (T2); `precioEur` por `ConversionEur` (T3); requests con `precioEur` nullable e ignorado (T6) |
| `controller/ValidacionPedidos.java` | *Crear.* Rangos, validación de alta/edición y de líneas de lote, mensajes exactos (T2, T4) |
| `dao/ComponenteDAO.java` (`Basico`, `getBasico`), `dao/ProveedorDAO.java` (`getById`) | *Modificar.* Lecturas para saber si existen y están activos, y la divisa del proveedor (T2) |
| `service/ConversionEur.java` | *Crear.* `aEuros(precioUnidad, divisa)` = precio / tasa, HALF_UP 2 decimales (T3) |
| `dao/TipoCambioDAO.java` | *Modificar.* Fallo de Frankfurter → 503 con mensaje; constructor de test con `HttpClient` (T3) |
| `model/LoteCompras.java`, `model/LoteComprasOtros.java` | *Crear.* Records de petición y respuesta de los lotes (T4, T5) |
| `service/CompraLoteService.java` | *Crear.* Transacción de cada lote: inserciones y marcado de solicitudes (T4, T5) |
| `controller/CompraLoteController.java` | *Crear.* `POST /api/compras/lote` y `/api/compras-otros/lote` con `Idempotency-Key`, validación, tasa y logs (T4, T5) |
| `dao/ReparacionComponenteDAO.java` (`getIdComDeSolicitud`), `dao/SolicitudStockDAO.java` (`getIdCom`) | *Modificar.* El componente de cada solicitud, para el 422 de "sin línea" (T4) |
| `model/CompraComponente.java`, `model/CompraOtro.java` | *Modificar.* `@Schema(nullable = true)` en `cantidadRecibida` y `fechaLlegada` (T6) |
| `OpenApiContractTest.java` | *Modificar.* Rutas y esquemas de los lotes, nullables (T4, T5, T6) |
| Tests nuevos | `dao/CompraComponenteDAOTransicionesTest`, `dao/CompraOtroDAOTransicionesTest`, `controller/CompraControllerTest`, `controller/CompraOtroControllerTest` (T1, T2, T3); `dao/ComponenteDAOBasicoTest`, `dao/ProveedorDAOGetByIdTest` (T2); `service/ConversionEurTest`, `dao/TipoCambioDAOTest` (T3); `dao/CompraComponenteDAOInsertarTest`, `dao/ReparacionComponenteDAOSolicitudTest`, `dao/SolicitudStockDAOTest`, `service/CompraLoteServiceTest`, `service/CompraLoteServiceTransaccionTest`, `controller/CompraLoteControllerTest`, `controller/RolesCompraLoteTest` (T4, T5); `dao/CompraOtroDAOInsertarTest` (T5) |

**Web** (`gestion-reparaciones-web`)

| Fichero | Responsabilidad |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | *Regenerar* con el contrato de la rama del servidor (T7) |
| `src/shared/api/client.ts` | *Modificar.* Alias `CompraComponente`, `CompraOtro` (T7) |
| `src/shared/api/errors.ts` (+ test) | *Modificar.* 503 con mensaje = `ReglaNegocioError` (T7) |
| `src/shared/lib/importes.ts` (+ test) | *Crear.* Formato es-ES con coma, símbolo de divisa, parseo de decimales y enteros (T7) |
| `src/shared/lib/fechas.ts` (+ test) | *Modificar.* Patrón `dd/MM/yy HH:mm` y `FMT_FECHA_PEDIDO` (T7) |
| `src/shared/lib/clavesIdempotencia.ts` (+ test) | *Mover* desde `modules/taller/formulario/` (P9); imports en `useGuardado.ts` y `AsignarTrabajosDialog.tsx` (T7) |
| `src/shared/styles/tokens.css` | *Modificar.* `fila-pendiente-brd`, `badge-pendiente-bg`, `badge-pendiente-text` (T7) |
| `src/shared/lib/formularioPedido.ts` (+ test) | *Crear.* Store del formulario abierto y funciones de apertura (P1) (T8) |
| `src/modules/almacen/pedidos/reglas.ts`, `filtros.ts`, `confirmaciones.ts` (+ tests), `estado.ts`, `tasa.ts` (+ test) | *Crear.* Helpers puros de la pestaña, stores y hook de tasa (T9) |
| `src/modules/almacen/pedidos/api.ts` (+ test) | *Crear.* Consultas de las dos tablas, transiciones, edición, lotes y recarga (T10) |
| `src/modules/almacen/pedidos/columnas.tsx` (+ test), `BadgeEstadoPedido.tsx` | *Crear.* Columnas de las dos tablas, clase de fila, dos CSV, badge con "⚠" (T11) |
| `src/modules/almacen/pedidos/CantidadDialog.tsx`, `MenuPedido.tsx` (+ tests) | *Crear.* Diálogos de recepción parcial y resto; menú contextual por estado (T12) |
| `src/modules/almacen/pedidos/PedidosPage.tsx` (+ test) | *Crear.* La pestaña: toggle, filtros, tabla, pie, acciones, llegada desde Stock (T13) |
| `src/shared/ui/DataTable.tsx`, `src/shared/ui/ConfirmDialog.tsx` (+ tests) | *Modificar.* `menuFila` que devuelve `null` no pone menú; `whitespace-pre-line` en la descripción (T13) |
| `src/app/router.tsx` | *Modificar.* `/stock/pedidos` y `/stock/pedidos/otros` con `key` (T13) |
| `src/modules/almacen/pedidos/formulario/lineas.ts`, `conversion.ts` (+ tests), `datosPrueba.ts` | *Crear.* Líneas del formulario (precargas, validación, cuerpos de lote), conversión y datos sintéticos de test (T14) |
| `src/modules/almacen/pedidos/formulario/errores.ts` (+ test), `DialogoLineas.tsx`, `NuevoPedidoDialog.tsx` (+ test), `FormulariosPedido.tsx` (+ test) | *Crear.* Texto de error de guardado, armazón de los formularios de alta, "Nuevo pedido" y el host del shell (T15) |
| `src/app/shell/AppLayout.tsx` (+ test) | *Modificar.* Monta `FormulariosPedido` dentro de los providers (T15) |
| `src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.tsx` (+ test) | *Crear.* "Nuevo otro pedido" y su rama en el host (T16) |
| `src/modules/almacen/pedidos/formulario/edicion.ts` (+ test), `EditarPedidoDialog.tsx`, `EditarOtroPedidoDialog.tsx` (+ tests), `pedidos/PedidosPage.editar.test.tsx` | *Crear.* Los dos editores y su cableado desde el menú (T17) |
| `src/modules/almacen/ui/DialogoAlmacen.tsx` (+ test) | *Modificar.* Prop `ancho?: 360 \| 520` (T17) |
| `src/modules/taller/notificaciones/api.ts`, `TarjetaAlerta.tsx`, `PanelNotificaciones.tsx` (+ tests), `docs/paridad/notificaciones.md` | *Modificar.* Los tres botones de pedir activos; `pedirPendientes` (T18) |
| `src/modules/taller/lib/textos.ts` | *Borrar.* Solo tenía `TOOLTIP_ALMACEN` (T18) |
| `src/modules/almacen/stock/filtros.ts` (+ test), `StockPage.tsx` (+ test), `docs/paridad/stock.md` | *Modificar.* "Pedir" abre el modal; `?componente=` al volver desde Pedidos; `filtrosDesdePedidos` (T19) |
| `tests/e2e/pedidos.spec.ts`, `.env.e2e.example`, `README.md` | *Crear / modificar.* Smoke contra producción (T20) |
| `docs/paridad/pedidos.md`, `CHANGELOG.md`, `package.json`, `package-lock.json` | *Crear / modificar.* Ficha de paridad, 0.7.0 (T21) |

---

## Task 1: Servidor — guards de estado (409) en las dos DAO de pedidos y log de `confirmar-alterado`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` (clase completa: `editar` :86-94, `confirmarRecibido` :96-104, `confirmarParcial` :106-116, `recibirResto` :118-132, `confirmarAlterado` :134-138, `cancelar` :140-144, `confirmar` :146-156, `desrecibir` :168-184, `CompraRow` :195 pasa a package-private)
- Modify: `src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java` (clase completa: `editar` :64-72, `confirmarRecibido` :82-86, `confirmarParcial` :88-94, `recibirResto` :96-107, `confirmarAlterado` :109-113, `cancelar` :115-119, `desrecibir` :121-127, `Row` :137 pasa a package-private)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraController.java:105-109` (`confirmarAlterado` con log) y `:143-150` (records package-private)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java:94-98` (`confirmarAlterado` con log) y `:123-129` (records package-private)
- Create: `src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOTransicionesTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOTransicionesTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java`

**Interfaces:**
- Consumes: `CompraComponenteDAO.getCompraRow(int)` (:197-207) y `checkUpdatedAt` (:209-213), `CompraOtroDAO.getRow(int)` (:139-147) y `checkUpdatedAt` (:149-153), `LogDAO.insertar(int idUsu, String accion, String detalle)` (`LogDAO.java:30`).
- Produces (409 `CONFLICT`, `ResponseStatusException`, textos EXACTOS):
  - `PATCH …/confirmar` exige `pendiente` → `"El pedido ya no está pendiente"` (ya existía).
  - `PATCH …/confirmar-recibido`, `…/confirmar-parcial`, `…/cancelar` exigen `en_camino` → `"El pedido ya no está en camino"`.
  - `PATCH …/recibir-resto`, `…/confirmar-alterado` exigen `parcial` → `"El pedido ya no está en recepción parcial"`.
  - `PATCH …/desrecibir` exige `recibido` → `"El pedido ya no está recibido"`; en compras de componentes, además, el 409 de stock insuficiente de siempre.
  - `PUT /{id}` exige `pendiente`, `en_camino` o `recibido` (en `recibido`, solo con la misma `CANTIDAD`: carrera con el 422 de P2 de la Task 2) → `"El pedido no se puede editar en su estado actual"`.
  - `DELETE /{id}` exige `pendiente` → `"El pedido ya no está pendiente (no se puede borrar)"` (ya existía).
  - Orden real de comprobaciones: se lee la fila (`getCompraRow`/`getRow`), `checkUpdatedAt` (409 `"Dato modificado por otro usuario"`) y después el `UPDATE … AND ESTADO=…` (409 si `n == 0`). En `desrecibir` de componentes: `UPDATE` condicionado del pedido → lectura de stock → 409 de stock (la transacción `@Transactional` deshace el `UPDATE` del pedido) → resta de stock.
  - Logs nuevos: `CONFIRMAR_ALTERADO` con `"ID_COMPRA: {id}"` y `CONFIRMAR_ALTERADO_OTRO` con `"ID_COMPRA_OTRO: {id}"`, después de escribir (un 409 no registra log).
  - Los records de petición de los dos controladores pasan de `private` a package-private (los tests los construyen); springdoc los sigue publicando con el mismo nombre (`CompraUpdatedAtRequest`, `CompraOtroUpdatedAtRequest`…), el contrato no cambia.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git checkout -b feature/web-pedidos
```

- [ ] **Step 2: Test de la matriz de `CompraComponenteDAO` (falla)**

`src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOTransicionesTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Matriz de estados del servidor (spec 4b §4.1): cada transición es un UPDATE condicionado al estado que exige y
 *  responde 409 si no toca ninguna fila; las que suman o restan stock no lo tocan si el estado no cuadra. El lock
 *  de updatedAt se sigue comprobando antes. Un UPDATE sin stub devuelve 0 filas: es el caso "estado incorrecto". */
class CompraComponenteDAOTransicionesTest {

    private static final int ID = 5;
    private static final int ID_COM = 10;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0, 0);

    private static final String SUMAR_STOCK  = "UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?";
    private static final String RESTAR_STOCK = "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?";
    private static final String CONFIRMAR = "UPDATE Compra_componente SET ESTADO='en_camino' WHERE ID_COMPRA=? AND ESTADO='pendiente'";
    private static final String RECIBIDO = "UPDATE Compra_componente SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String PARCIAL = "UPDATE Compra_componente SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String RESTO_COMPLETO = "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String RESTO_PARCIAL = "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String ALTERADO = "UPDATE Compra_componente SET ESTADO='recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String CANCELAR = "UPDATE Compra_componente SET ESTADO='cancelado' WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String DESRECIBIR = "UPDATE Compra_componente SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA=? AND ESTADO='recibido'";
    private static final String EDITAR = "UPDATE Compra_componente SET ID_PROV=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))";
    private static final String BORRAR = "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraComponenteDAO dao = new CompraComponenteDAO(jdbc);

    /** La fila que lee getCompraRow: componente, cantidad pedida, recibida y el updatedAt guardado (= AT). */
    private void fila(int cantidad, Integer recibida) {
        when(jdbc.queryForObject(contains("FROM Compra_componente WHERE ID_COMPRA = ?"), any(RowMapper.class), eq(ID)))
                .thenReturn(new CompraComponenteDAO.CompraRow(ID_COM, cantidad, recibida, AT));
    }

    private void stockActual(int stock) {
        when(jdbc.queryForObject(contains("SELECT STOCK FROM Componente"), eq(Integer.class), eq(ID_COM))).thenReturn(stock);
    }

    private static void conflicto(String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    private void sinTocarStock() {
        verify(jdbc, never()).update(eq(SUMAR_STOCK), any(Object[].class));
        verify(jdbc, never()).update(eq(RESTAR_STOCK), any(Object[].class));
    }

    // ── confirmar (pendiente → en_camino) ──
    @Test void confirmarDesdePendientePasaAEnCamino() {
        fila(3, null);
        when(jdbc.update(CONFIRMAR, ID)).thenReturn(1);
        dao.confirmar(ID, AT);
        verify(jdbc).update(CONFIRMAR, ID);
        sinTocarStock();
    }

    @Test void confirmarFueraDePendienteEs409() {
        fila(3, null);
        conflicto("El pedido ya no está pendiente", () -> dao.confirmar(ID, AT));
    }

    // ── confirmar-recibido (en_camino → recibido, + cantidad pedida) ──
    @Test void recibirDesdeEnCaminoSumaLaCantidadPedida() {
        fila(3, null);
        when(jdbc.update(RECIBIDO, ID)).thenReturn(1);
        dao.confirmarRecibido(ID, AT);
        verify(jdbc).update(SUMAR_STOCK, 3, ID_COM);
    }

    @Test void recibirFueraDeEnCaminoEs409SinTocarStock() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarRecibido(ID, AT));
        sinTocarStock();
    }

    // ── confirmar-parcial (en_camino → parcial, + recibida) ──
    @Test void parcialDesdeEnCaminoSumaLoRecibido() {
        fila(5, null);
        when(jdbc.update(PARCIAL, 2, ID)).thenReturn(1);
        dao.confirmarParcial(ID, 2, AT);
        verify(jdbc).update(SUMAR_STOCK, 2, ID_COM);
    }

    @Test void parcialFueraDeEnCaminoEs409SinTocarStock() {
        fila(5, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarParcial(ID, 2, AT));
        sinTocarStock();
    }

    // ── recibir-resto (parcial → recibido si completa; si no, sigue parcial; + extra) ──
    @Test void restoQueCompletaPasaARecibidoYSuma() {
        fila(5, 2);
        when(jdbc.update(RESTO_COMPLETO, 3, ID)).thenReturn(1);
        dao.recibirResto(ID, 3, AT);
        verify(jdbc).update(SUMAR_STOCK, 3, ID_COM);
    }

    @Test void restoQueNoCompletaSigueParcialYSuma() {
        fila(5, 2);
        when(jdbc.update(RESTO_PARCIAL, 1, ID)).thenReturn(1);
        dao.recibirResto(ID, 1, AT);
        verify(jdbc).update(SUMAR_STOCK, 1, ID_COM);
    }

    @Test void restoFueraDeParcialEs409SinTocarStock() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.recibirResto(ID, 3, AT));
        sinTocarStock();
    }

    // ── confirmar-alterado (parcial → recibido, sin stock) ──
    @Test void alteradoDesdeParcialPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(ALTERADO, ID)).thenReturn(1);
        dao.confirmarAlterado(ID, AT);
        verify(jdbc).update(ALTERADO, ID);
        sinTocarStock();
    }

    @Test void alteradoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.confirmarAlterado(ID, AT));
    }

    // ── cancelar (en_camino → cancelado) ──
    @Test void cancelarDesdeEnCaminoCancela() {
        fila(3, null);
        when(jdbc.update(CANCELAR, ID)).thenReturn(1);
        dao.cancelar(ID, AT);
        verify(jdbc).update(CANCELAR, ID);
        sinTocarStock();
    }

    @Test void cancelarFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.cancelar(ID, AT));
    }

    // ── desrecibir (recibido → en_camino, − recibida ?? pedida) ──
    @Test void desrecibirDesdeRecibidoRestaLoRecibido() {
        fila(5, 4);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        stockActual(10);
        dao.desrecibir(ID, AT);
        verify(jdbc).update(RESTAR_STOCK, 4, ID_COM);
    }

    @Test void desrecibirFueraDeRecibidoEs409SinTocarStock() {
        fila(5, null);
        conflicto("El pedido ya no está recibido", () -> dao.desrecibir(ID, AT));
        sinTocarStock();
    }

    /** El 409 de stock se mantiene; el UPDATE del pedido ya hecho lo deshace la transacción (@Transactional). */
    @Test void desrecibirConStockInsuficienteSigueSiendo409SinRestar() {
        fila(5, null);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        stockActual(2);
        conflicto("Stock insuficiente para deshacer la recepción (stock actual: 2, a descontar: 5)",
                () -> dao.desrecibir(ID, AT));
        sinTocarStock();
    }

    // ── editar (pendiente, en_camino o recibido con la misma cantidad) ──
    @Test void editarEnUnEstadoEditableEscribe() {
        fila(4, null);
        when(jdbc.update(EDITAR, 2, 4, true, 10.0, "USD", 8.8, ID, 4)).thenReturn(1);
        dao.editar(ID, 2, 4, true, 10.0, "USD", 8.8, AT);
        verify(jdbc).update(EDITAR, 2, 4, true, 10.0, "USD", 8.8, ID, 4);
    }

    @Test void editarEnUnEstadoNoEditableEs409() {
        fila(4, null);
        conflicto("El pedido no se puede editar en su estado actual",
                () -> dao.editar(ID, 2, 4, true, 10.0, "USD", 8.8, AT));
    }

    // ── borrar (pendiente; sin updatedAt, como hoy) ──
    @Test void borrarUnPendienteBorra() {
        when(jdbc.update(BORRAR, ID)).thenReturn(1);
        dao.borrarPendiente(ID);
        verify(jdbc).update(BORRAR, ID);
    }

    @Test void borrarFueraDePendienteEs409() {
        conflicto("El pedido ya no está pendiente (no se puede borrar)", () -> dao.borrarPendiente(ID));
    }

    // ── orden: updatedAt antes que el estado ──
    @Test void unUpdatedAtViejoEs409AntesDeTocarNada() {
        fila(3, null);
        conflicto("Dato modificado por otro usuario", () -> dao.confirmarRecibido(ID, AT.minusSeconds(1)));
        verify(jdbc, never()).update(RECIBIDO, ID);
        sinTocarStock();
    }
}
```

- [ ] **Step 3: Test de la matriz de `CompraOtroDAO` (falla)**

`src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOTransicionesTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** La misma matriz que CompraComponenteDAOTransicionesTest para "otros pedidos" (spec 4b §4.1): sin stock, pero con
 *  los mismos estados exigidos y los mismos 409. */
class CompraOtroDAOTransicionesTest {

    private static final int ID = 5;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0, 0);

    private static final String CONFIRMAR = "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'";
    private static final String RECIBIDO = "UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String PARCIAL = "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String RESTO_COMPLETO = "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
    private static final String ALTERADO = "UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
    private static final String CANCELAR = "UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String DESRECIBIR = "UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA_OTRO=? AND ESTADO='recibido'";
    private static final String EDITAR = "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))";
    private static final String BORRAR = "DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraOtroDAO dao = new CompraOtroDAO(jdbc);

    private void fila(int cantidad, Integer recibida) {
        when(jdbc.queryForObject(contains("FROM Compra_otro WHERE ID_COMPRA_OTRO = ?"), any(RowMapper.class), eq(ID)))
                .thenReturn(new CompraOtroDAO.Row(cantidad, recibida, AT));
    }

    private static void conflicto(String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    @Test void confirmarDesdePendientePasaAEnCamino() {
        fila(3, null);
        when(jdbc.update(CONFIRMAR, ID)).thenReturn(1);
        dao.confirmar(ID, AT);
        verify(jdbc).update(CONFIRMAR, ID);
    }

    @Test void confirmarFueraDePendienteEs409() {
        fila(3, null);
        conflicto("El pedido ya no está pendiente", () -> dao.confirmar(ID, AT));
    }

    @Test void recibirDesdeEnCaminoPasaARecibidoSinStock() {
        fila(3, null);
        when(jdbc.update(RECIBIDO, ID)).thenReturn(1);
        dao.confirmarRecibido(ID, AT);
        verify(jdbc).update(RECIBIDO, ID);
        verify(jdbc, never()).update(startsWith("UPDATE Componente"), any(Object[].class));
    }

    @Test void recibirFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarRecibido(ID, AT));
    }

    @Test void parcialDesdeEnCaminoGuardaLoRecibido() {
        fila(5, null);
        when(jdbc.update(PARCIAL, 2, ID)).thenReturn(1);
        dao.confirmarParcial(ID, 2, AT);
        verify(jdbc).update(PARCIAL, 2, ID);
    }

    @Test void parcialFueraDeEnCaminoEs409() {
        fila(5, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarParcial(ID, 2, AT));
    }

    @Test void restoQueCompletaPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(RESTO_COMPLETO, 3, ID)).thenReturn(1);
        dao.recibirResto(ID, 3, AT);
        verify(jdbc).update(RESTO_COMPLETO, 3, ID);
    }

    @Test void restoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.recibirResto(ID, 3, AT));
    }

    @Test void alteradoDesdeParcialPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(ALTERADO, ID)).thenReturn(1);
        dao.confirmarAlterado(ID, AT);
        verify(jdbc).update(ALTERADO, ID);
    }

    @Test void alteradoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.confirmarAlterado(ID, AT));
    }

    @Test void cancelarDesdeEnCaminoCancela() {
        fila(3, null);
        when(jdbc.update(CANCELAR, ID)).thenReturn(1);
        dao.cancelar(ID, AT);
        verify(jdbc).update(CANCELAR, ID);
    }

    @Test void cancelarFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.cancelar(ID, AT));
    }

    @Test void desrecibirDesdeRecibidoVuelveAEnCamino() {
        fila(5, 5);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        dao.desrecibir(ID, AT);
        verify(jdbc).update(DESRECIBIR, ID);
    }

    @Test void desrecibirFueraDeRecibidoEs409() {
        fila(5, null);
        conflicto("El pedido ya no está recibido", () -> dao.desrecibir(ID, AT));
    }

    @Test void editarEnUnEstadoEditableEscribe() {
        fila(4, null);
        when(jdbc.update(EDITAR, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, ID, 4)).thenReturn(1);
        dao.editar(ID, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, AT);
        verify(jdbc).update(EDITAR, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, ID, 4);
    }

    @Test void editarEnUnEstadoNoEditableEs409() {
        fila(4, null);
        conflicto("El pedido no se puede editar en su estado actual",
                () -> dao.editar(ID, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, AT));
    }

    @Test void borrarUnPendienteBorra() {
        when(jdbc.update(BORRAR, ID)).thenReturn(1);
        dao.borrarPendiente(ID);
        verify(jdbc).update(BORRAR, ID);
    }

    @Test void borrarFueraDePendienteEs409() {
        conflicto("El pedido ya no está pendiente (no se puede borrar)", () -> dao.borrarPendiente(ID));
    }

    @Test void unUpdatedAtViejoEs409AntesDeTocarNada() {
        fila(3, null);
        conflicto("Dato modificado por otro usuario", () -> dao.cancelar(ID, AT.minusSeconds(1)));
        verify(jdbc, never()).update(CANCELAR, ID);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraComponenteDAOTransicionesTest,CompraOtroDAOTransicionesTest'`
Expected: FAIL de compilación (`CompraComponenteDAO.CompraRow` y `CompraOtroDAO.Row` tienen acceso privado).

- [ ] **Step 5: `CompraComponenteDAO` con guards**

`src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` completo (los SQL de las transiciones pasan a literal de una línea para que el test los compare tal cual; `getAll`, `getEnCamino`, `getCantidadEnCaminoPorComponente`, `insertar`, `borrarPendiente`, `resolveToMasterId`, `getCompraRow`, `checkUpdatedAt` y `getById` no cambian):

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraComponente;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class CompraComponenteDAO {

    /** 409 de estado (spec 4b §4.1). Los comparte CompraOtroDAO. Hasta el 4b el servidor solo protegía "pendiente"
     *  en confirmar y borrar: con un GET fresco y una llamada directa se podía recibir dos veces (sumando stock). */
    static final String MSG_NO_PENDIENTE = "El pedido ya no está pendiente";
    static final String MSG_NO_EN_CAMINO = "El pedido ya no está en camino";
    static final String MSG_NO_PARCIAL   = "El pedido ya no está en recepción parcial";
    static final String MSG_NO_RECIBIDO  = "El pedido ya no está recibido";
    static final String MSG_NO_EDITABLE  = "El pedido no se puede editar en su estado actual";

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT cc.ID_COMPRA, cc.ID_COM, c.TIPO, cc.ID_PROV, p.NOMBRE AS NOMBRE_PROV," +
            " cc.CANTIDAD, cc.CANTIDAD_RECIBIDA, cc.ES_URGENTE," +
            " cc.FECHA_PEDIDO, cc.FECHA_LLEGADA," +
            " cc.PRECIO_UNIDAD_PEDIDO, cc.DIVISA, cc.PRECIO_EUR," +
            " cc.ESTADO, cc.UPDATED_AT" +
            " FROM Compra_componente cc" +
            " JOIN Componente c ON cc.ID_COM = c.ID_COM" +
            " JOIN Proveedor p ON cc.ID_PROV = p.ID_PROV";

    private static final RowMapper<CompraComponente> MAPPER = (rs, row) -> {
        Timestamp tsLlegada = rs.getTimestamp("FECHA_LLEGADA");
        return new CompraComponente(
                rs.getInt("ID_COMPRA"),
                rs.getInt("ID_COM"),
                rs.getString("TIPO"),
                rs.getInt("ID_PROV"),
                rs.getString("NOMBRE_PROV"),
                rs.getInt("CANTIDAD"),
                rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                rs.getBoolean("ES_URGENTE"),
                rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime(),
                tsLlegada != null ? tsLlegada.toLocalDateTime() : null,
                rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
                rs.getString("DIVISA"),
                rs.getDouble("PRECIO_EUR"),
                rs.getString("ESTADO"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        );
    };

    public CompraComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompraComponente> getAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY cc.FECHA_PEDIDO DESC", MAPPER);
    }

    public List<CompraComponente> getEnCamino() {
        return jdbc.query(SELECT_BASE +
                " WHERE cc.ESTADO = 'en_camino'" +
                " ORDER BY cc.ES_URGENTE DESC, cc.FECHA_PEDIDO ASC", MAPPER);
    }

    /** Cantidad pendiente de llegar del SKU, resuelta al master del grupo compartido: las compras se insertan
     *  siempre en el master, así que preguntar por un slave sin resolver devolvía 0 (sub-proyecto 4a). */
    public int getCantidadEnCaminoPorComponente(int idCom) {
        idCom = resolveToMasterId(idCom);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0)), 0)" +
                " FROM Compra_componente WHERE ID_COM = ? AND ESTADO IN ('en_camino','parcial')",
                Integer.class, idCom);
    }

    public void insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) {
        idCom = resolveToMasterId(idCom);
        jdbc.update(
                "INSERT INTO Compra_componente" +
                " (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                idCom, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur);
    }

    /** Editable en pendiente, en_camino y recibido; en recibido solo si la cantidad no cambia (el 422 de P2 lo da el
     *  controlador antes; esta condición cubre la carrera). El WHERE se evalúa sobre la fila de antes del SET. */
    public void editar(int idCompra, int idProv, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ID_PROV=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))",
                idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, idCompra, cantidad);
        exigir(n, MSG_NO_EDITABLE);
    }

    @Transactional
    public void confirmarRecibido(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                row.cantidad(), row.idCom());
    }

    @Transactional
    public void confirmarParcial(int idCompra, int cantidadRecibida, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                cantidadRecibida, idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadRecibida, row.idCom());
    }

    @Transactional
    public void recibirResto(int idCompra, int cantidadExtra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int nuevaRecibida = (row.cantidadRecibida() != null ? row.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= row.cantidad();
        String sql = completo
                ? "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'"
                : "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA=? AND ESTADO='parcial'";
        int n = jdbc.update(sql, cantidadExtra, idCompra);
        exigir(n, MSG_NO_PARCIAL);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadExtra, row.idCom());
    }

    public void confirmarAlterado(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_componente SET ESTADO='recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'",
                idCompra);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void cancelar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_componente SET ESTADO='cancelado' WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    /** Confirma un pedido pendiente: pasa a 'en_camino' (entra en el flujo de recepción). */
    public void confirmar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='en_camino' WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        exigir(n, MSG_NO_PENDIENTE);
    }

    /** Borra un pedido en estado 'pendiente' (aún no se pidió nada). */
    public void borrarPendiente(int idCompra) {
        int n = jdbc.update(
                "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente (no se puede borrar)");
        }
    }

    /** Primero el UPDATE condicionado a 'recibido' (409 si ya no lo está); después el 409 de stock de siempre, que
     *  deshace ese UPDATE porque el método es @Transactional; por último la resta. */
    @Transactional
    public void desrecibir(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int cantidadARevertir = row.cantidadRecibida() != null ? row.cantidadRecibida() : row.cantidad();
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA=? AND ESTADO='recibido'",
                idCompra);
        exigir(n, MSG_NO_RECIBIDO);
        Integer stockActual = jdbc.queryForObject(
                "SELECT STOCK FROM Componente WHERE ID_COM = ?", Integer.class, row.idCom());
        if (stockActual == null || stockActual < cantidadARevertir) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente para deshacer la recepción (" +
                    "stock actual: " + stockActual + ", a descontar: " + cantidadARevertir + ")");
        }
        jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?",
                cantidadARevertir, row.idCom());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int resolveToMasterId(int idCom) {
        Integer master = jdbc.queryForObject(
                "SELECT COALESCE(ID_COM_MASTER, ID_COM) FROM Componente WHERE ID_COM = ?",
                Integer.class, idCom);
        return master != null ? master : idCom;
    }

    /** Package-private: los tests de la matriz de estados la devuelven desde el JdbcTemplate mockeado. */
    record CompraRow(int idCom, int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

    private CompraRow getCompraRow(int idCompra) {
        return jdbc.queryForObject(
                "SELECT ID_COM, CANTIDAD, CANTIDAD_RECIBIDA, UPDATED_AT" +
                " FROM Compra_componente WHERE ID_COMPRA = ?",
                (rs, row) -> new CompraRow(
                        rs.getInt("ID_COM"),
                        rs.getInt("CANTIDAD"),
                        rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idCompra);
    }

    private void checkUpdatedAt(LocalDateTime bdAt, LocalDateTime clientAt) {
        if (!clientAt.truncatedTo(ChronoUnit.SECONDS).equals(bdAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    /** Guard de estado: el UPDATE condicionado no tocó ninguna fila → el pedido ya no está en el estado exigido. */
    static void exigir(int filas, String mensaje) {
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
        }
    }

    public java.util.Optional<CompraComponente> getById(int idCompra) {
        List<CompraComponente> rows = jdbc.query(
                SELECT_BASE + " WHERE cc.ID_COMPRA = ?", MAPPER, idCompra);
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }
}
```

- [ ] **Step 6: `CompraOtroDAO` con guards**

`src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java` completo (reutiliza los mensajes y `exigir` de `CompraComponenteDAO`, mismo paquete):

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraOtro;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.reparaciones.servidor.dao.CompraComponenteDAO.*;

@Repository
public class CompraOtroDAO {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT co.ID_COMPRA_OTRO, co.ID_PROV, p.NOMBRE AS NOMBRE_PROVEEDOR, co.CONCEPTO," +
            " co.CANTIDAD, co.CANTIDAD_RECIBIDA, co.ES_URGENTE, co.FECHA_PEDIDO, co.FECHA_LLEGADA," +
            " co.PRECIO_UNIDAD_PEDIDO, co.DIVISA, co.PRECIO_EUR, co.ESTADO, co.UPDATED_AT" +
            " FROM Compra_otro co JOIN Proveedor p ON co.ID_PROV = p.ID_PROV";

    private static final RowMapper<CompraOtro> MAPPER = (rs, row) -> new CompraOtro(
            rs.getInt("ID_COMPRA_OTRO"),
            rs.getInt("ID_PROV"),
            rs.getString("NOMBRE_PROVEEDOR"),
            rs.getString("CONCEPTO"),
            rs.getInt("CANTIDAD"),
            rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
            rs.getBoolean("ES_URGENTE"),
            rs.getTimestamp("FECHA_PEDIDO") != null ? rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime() : null,
            rs.getTimestamp("FECHA_LLEGADA") != null ? rs.getTimestamp("FECHA_LLEGADA").toLocalDateTime() : null,
            rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
            rs.getString("DIVISA"),
            rs.getDouble("PRECIO_EUR"),
            rs.getString("ESTADO"),
            rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS));

    public CompraOtroDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompraOtro> getAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY co.FECHA_PEDIDO DESC", MAPPER);
    }

    public Optional<CompraOtro> getById(int id) {
        List<CompraOtro> rows = jdbc.query(SELECT_BASE + " WHERE co.ID_COMPRA_OTRO = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public void insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) {
        jdbc.update(
                "INSERT INTO Compra_otro" +
                " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur);
    }

    public void editar(int id, int idProv, String concepto, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur, id, cantidad);
        exigir(n, MSG_NO_EDITABLE);
    }

    public void confirmar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        exigir(n, MSG_NO_PENDIENTE);
    }

    public void confirmarRecibido(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'", id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    public void confirmarParcial(int id, int cantidadRecibida, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'",
                cantidadRecibida, id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    public void recibirResto(int id, int cantidadExtra, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int nuevaRecibida = (r.cantidadRecibida() != null ? r.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= r.cantidad();
        String sql = completo
                ? "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'"
                : "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
        int n = jdbc.update(sql, cantidadExtra, id);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void confirmarAlterado(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'", id);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void cancelar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'", id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    /** Revierte a 'en_camino' desde 'recibido'. Sin stock que descontar -> no hay chequeo de stock. */
    public void desrecibir(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA_OTRO=? AND ESTADO='recibido'", id);
        exigir(n, MSG_NO_RECIBIDO);
    }

    public void borrarPendiente(int id) {
        int n = jdbc.update("DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El pedido ya no está pendiente (no se puede borrar)");
    }

    // -- helpers --

    /** Package-private: los tests de la matriz de estados la devuelven desde el JdbcTemplate mockeado. */
    record Row(int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

    private Row getRow(int id) {
        return jdbc.queryForObject(
                "SELECT CANTIDAD, CANTIDAD_RECIBIDA, UPDATED_AT FROM Compra_otro WHERE ID_COMPRA_OTRO = ?",
                (rs, row) -> new Row(
                        rs.getInt("CANTIDAD"),
                        rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                id);
    }

    private void checkUpdatedAt(LocalDateTime bdAt, LocalDateTime clientAt) {
        if (!clientAt.truncatedTo(ChronoUnit.SECONDS).equals(bdAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }
}
```

- [ ] **Step 7: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraComponenteDAOTransicionesTest,CompraOtroDAOTransicionesTest,CompraComponenteDAOEnCaminoTest'`
Expected: PASS, 42 tests (21 + 19 + los 2 de en camino).

- [ ] **Step 8: Tests del log de `confirmar-alterado` (fallan)**

`src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** CompraController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraController ctl = new CompraController(dao, logDao, componenteDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO", "ID_COMPRA: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** CompraOtroController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraOtroControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final CompraOtroDAO dao = mock(CompraOtroDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraOtroController ctl = new CompraOtroController(dao, logDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }
}
```

- [ ] **Step 9: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraControllerTest,CompraOtroControllerTest'`
Expected: FAIL de compilación (`UpdatedAtRequest` es privado; `confirmarAlterado` no recibe el principal).

- [ ] **Step 10: Log en los dos controladores y records package-private**

En `CompraController.java`, sustituir `confirmarAlterado` (:105-109) por:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(idCompra, req.updatedAt());
        // Hasta el 4b era la única transición sin log (inventario de Pedidos §8)
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO", "ID_COMPRA: " + idCompra);
    }
```

y los records (:143-150) por (los tests los construyen; springdoc los publica con el mismo nombre):

```java
    record InsertarRequest(int idCom, int idProv, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa, double precioEur) {}
    record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur,
                         LocalDateTime updatedAt) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
```

En `CompraOtroController.java`, sustituir `confirmarAlterado` (:94-98) por:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: " + id);
    }
```

y los records (:123-129) por:

```java
    record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa, double precioEur) {}
    record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
```

- [ ] **Step 11: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'`
Expected: `mvn` sin fallos y el recuento imprime `330` (286 + 44: 21 + 19 de las DAO, 2 + 2 de los controladores). `OpenApiContractTest` sigue en verde: los records renombrados de visibilidad conservan su nombre de esquema.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java src/main/java/com/reparaciones/servidor/controller/CompraController.java src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOTransicionesTest.java src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOTransicionesTest.java src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java
git commit -m "feat(compras): 409 de estado en todas las transiciones de pedidos y log de confirmar alterado"
```

---

## Task 2: Servidor — validaciones de alta, edición y recepción (422) y cantidad fija en recibido (P2)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/controller/ValidacionPedidos.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java` (record `Basico` y `getBasico`, junto a `getTipoById` :278-281)
- Modify: `src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java` (`getById`, junto a `getNombreById` :77-80; import `java.util.Optional`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraController.java` (clase completa: `insertar` :53-64, `editar` :66-73, `confirmarParcial` :88-95, `recibirResto` :97-103)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java` (clase completa: `insertar` :36-46, `editar` :48-55, `confirmarParcial` :77-84, `recibirResto` :86-92)
- Create: `src/test/java/com/reparaciones/servidor/dao/ComponenteDAOBasicoTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/ProveedorDAOGetByIdTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java` (fichero completo)
- Modify: `src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java` (fichero completo)

**Interfaces:**
- Consumes: `CompraComponenteDAO.getById(int)` (`Optional<CompraComponente>`, :215-219) y `CompraOtroDAO.getById(int)` (`Optional<CompraOtro>`, :50-53), que ya existen y traen `estado`, `cantidad` y `cantidadRecibida`; `CompraComponente.getEstado()/getCantidad()/getCantidadRecibida()`, ídem `CompraOtro`; `Proveedor.isActivo()`.
- Pieza existente que se duplica: `ProveedorController` ya tiene `DIVISAS`, `MSG_DIVISA` y `divisaValida` (mismo texto "Divisa no válida (EUR o USD)." y misma normalización), pero son `private`; `ValidacionPedidos` los duplica para no tocar `ProveedorController`.
- Produces:
  - `controller/ValidacionPedidos` (package-private, `final`, métodos estáticos) con los textos EXACTOS de la spec §4.2; la reutilizan `CompraController`, `CompraOtroController` y, desde la Task 4, `CompraLoteController`.
  - `ComponenteDAO.Basico(int idCom, int idMaster, String tipo, boolean activo)` y `Optional<Basico> ComponenteDAO.getBasico(int idCom)` (vacío si no existe; `idMaster = COALESCE(ID_COM_MASTER, ID_COM)`; el `ACTIVO` de un slave es el del grupo porque `setActivo` activa y desactiva master y slaves a la vez, `ComponenteDAO.java:201-206`).
  - `Optional<Proveedor> ProveedorDAO.getById(int idProv)` (vacío si no existe).
  - `POST /api/compras` y `POST /api/compras-otros` → 422, en este orden: `"Cantidad no válida (debe ser > 0)."` (`cantidad <= 0`), `"Precio no válido."` (`precioUnidad < 0` o NaN), `"El concepto no puede estar vacío."` (solo otros: nulo o en blanco), `"Divisa no válida (EUR o USD)."` (tras `trim().toUpperCase()`; la normalizada es la que se guarda), `"El componente no está activo."` (solo compras: inexistente o inactivo), `"El proveedor no está activo."` (inexistente o inactivo).
  - `PUT /api/compras/{id}` y `PUT /api/compras-otros/{id}` → las mismas (sin componente, que no se edita) y después, si la fila está en `recibido` y `cantidad` difiere de la guardada, `"No se puede cambiar la cantidad de un pedido recibido."` (P2).
  - `PATCH …/confirmar-parcial` → `"La cantidad debe ser mayor que 0 y menor que {cantidad}."` si no se cumple `0 < cantidadRecibida < cantidad`.
  - `PATCH …/recibir-resto` → `"La cantidad debe ser mayor que 0."` si `cantidadExtra <= 0`; `"No puedes recibir más de lo pedido. Faltan {restante} unidad(es)."` si `cantidadExtra > cantidad − (recibida ?? 0)`.
  - Los 422 se lanzan en el controlador antes de llamar a la DAO: nunca escriben ni registran log. Los rangos y P2 se miden contra la fila fresca de `getById`; si el id no existe, se deja pasar y la DAO responde como hoy. Orden resultante: 422 de rango/P2 → 409 de `updatedAt` → 409 de estado.

- [ ] **Step 1: Tests de las lecturas nuevas de DAO (fallan)**

`src/test/java/com/reparaciones/servidor/dao/ComponenteDAOBasicoTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lectura mínima de un componente para validar pedidos (spec 4b §4.2): existe, está activo y cuál es su master. */
class ComponenteDAOBasicoTest {

    @Test void devuelveIdMasterTipoYActivoLeyendoSusColumnas() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_COM")).thenReturn(12);
        when(rs.getInt("ID_MASTER")).thenReturn(3);
        when(rs.getString("TIPO")).thenReturn("lcd-y-negro");
        when(rs.getBoolean("ACTIVO")).thenReturn(true);
        when(jdbc.query(contains("COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER"), any(RowMapper.class), eq(12)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        assertEquals(Optional.of(new ComponenteDAO.Basico(12, 3, "lcd-y-negro", true)),
                new ComponenteDAO(jdbc).getBasico(12));
    }

    @Test void unIdInexistenteDevuelveVacio() {
        assertEquals(Optional.empty(), new ComponenteDAO(mock(JdbcTemplate.class)).getBasico(99));
    }
}
```

`src/test/java/com/reparaciones/servidor/dao/ProveedorDAOGetByIdTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Proveedor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lectura de un proveedor por id para validar pedidos y resolver su divisa (spec 4b §4.2 y §4.4). */
class ProveedorDAOGetByIdTest {

    @Test void devuelveElProveedorConSuDivisaYSuEstado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_PROV")).thenReturn(3);
        when(rs.getString("NOMBRE")).thenReturn("Proveedor B");
        when(rs.getBoolean("ACTIVO")).thenReturn(false);
        when(rs.getString("DIVISA")).thenReturn("USD");
        when(rs.getString("TIPO")).thenReturn("COMPONENTES");
        when(jdbc.query(eq("SELECT ID_PROV, NOMBRE, ACTIVO, DIVISA, COMENTARIO, TIPO FROM Proveedor WHERE ID_PROV = ?"),
                any(RowMapper.class), eq(3)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        Proveedor p = new ProveedorDAO(jdbc).getById(3).orElseThrow();
        assertEquals(3, p.getIdProv());
        assertEquals("Proveedor B", p.getNombre());
        assertFalse(p.isActivo());
        assertEquals("USD", p.getDivisa());
    }

    @Test void unIdInexistenteDevuelveVacio() {
        assertTrue(new ProveedorDAO(mock(JdbcTemplate.class)).getById(99).isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='ComponenteDAOBasicoTest,ProveedorDAOGetByIdTest'`
Expected: FAIL de compilación (`ComponenteDAO.Basico`, `getBasico` y `ProveedorDAO.getById` no existen).

- [ ] **Step 3: Implementar las dos lecturas**

En `ComponenteDAO.java`, justo antes de `getTipoById` (:278):

```java
    /** Lo mínimo para validar una línea de pedido (sub-proyecto 4b): si existe, si está activo y su master.
     *  El ACTIVO de un slave es el de su grupo (setActivo cambia master y slaves a la vez). */
    public record Basico(int idCom, int idMaster, String tipo, boolean activo) {}

    public Optional<Basico> getBasico(int idCom) {
        return jdbc.query(
                "SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER, TIPO, ACTIVO FROM Componente WHERE ID_COM = ?",
                (rs, row) -> new Basico(rs.getInt("ID_COM"), rs.getInt("ID_MASTER"), rs.getString("TIPO"),
                        rs.getBoolean("ACTIVO")),
                idCom).stream().findFirst();
    }
```

(`ComponenteDAO` ya importa `java.util.*`, :17.)

En `ProveedorDAO.java`, añadir `import java.util.Optional;` y, justo antes de `getNombreById` (:77):

```java
    /** Vacío si no existe (getNombreById lanza; CompraController depende de que lance, así que no se toca). */
    public Optional<Proveedor> getById(int idProv) {
        return jdbc.query(SELECT_BASE + " WHERE ID_PROV = ?", MAPPER, idProv).stream().findFirst();
    }
```

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='ComponenteDAOBasicoTest,ProveedorDAOGetByIdTest,ProveedorDAOTest'`
Expected: PASS (4 nuevos + los de `ProveedorDAOTest`).

- [ ] **Step 5: Tests de controlador (fallan)**

`src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java` completo (conserva los dos tests de la Task 1):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** CompraController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraController ctl = new CompraController(dao, logDao, componenteDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraControllerTest() {
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(componenteDao.getBasico(9)).thenReturn(Optional.of(new ComponenteDAO.Basico(9, 9, "bat-x", false)));
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(8)).thenReturn(Optional.of(new Proveedor(8, "ACME", false, "EUR", null, "COMPONENTES")));
        when(componenteDao.getTipoById(1)).thenReturn("lcd-x-negro");
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    /** precioEur = precioUnidad: con EUR es lo que calcula el servidor desde la Task 3, así que estos tests no cambian. */
    private static CompraController.InsertarRequest alta(int idCom, int idProv, int cantidad, double precio, String divisa) {
        return new CompraController.InsertarRequest(idCom, idProv, cantidad, false, precio, divisa, precio);
    }

    private static CompraController.EditarRequest edicion(int idProv, int cantidad, double precio, String divisa) {
        return new CompraController.EditarRequest(idProv, cantidad, false, precio, divisa, precio, AT);
    }

    private static CompraComponente pedido(String estado, int cantidad, Integer recibida) {
        return new CompraComponente(5, 1, "lcd-x-negro", 2, "Proveedor A", cantidad, recibida, false,
                AT, null, 10.0, "EUR", 10.0, estado, AT);
    }

    private static String falla422(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaEscritoNiRegistrado() {
        verify(dao, never()).insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());
        verify(dao, never()).editar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any());
        verify(dao, never()).confirmarParcial(anyInt(), anyInt(), any());
        verify(dao, never()).recibirResto(anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    // ── Task 1: log de confirmar-alterado ──
    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO", "ID_COMPRA: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }

    // ── Task 2: alta ──
    @Test void altaConCantidadNoPositivaEs422() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(1, 2, 0, 10.0, "EUR"), super7)));
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(1, 2, -1, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConPrecioNegativoEs422() {
        assertEquals("Precio no válido.", falla422(() -> ctl.insertar(alta(1, 2, 3, -0.01, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConDivisaDesconocidaEs422() {
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(1, 2, 3, 10.0, "GBP"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(1, 2, 3, 10.0, null), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConComponenteInactivoOInexistenteEs422() {
        assertEquals("El componente no está activo.", falla422(() -> ctl.insertar(alta(9, 2, 3, 10.0, "EUR"), super7)));
        assertEquals("El componente no está activo.", falla422(() -> ctl.insertar(alta(99, 2, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConProveedorInactivoOInexistenteEs422() {
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(1, 8, 3, 10.0, "EUR"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(1, 99, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaValidaNormalizaLaDivisaYEscribe() {
        ctl.insertar(alta(1, 2, 3, 0.0, " eur "), super7);
        verify(dao).insertar(1, 2, 3, false, 0.0, "EUR", 0.0);
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 3");
    }

    // ── Task 2: edición ──
    @Test void editarValidaCantidadPrecioDivisaYProveedor() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.editar(5, edicion(2, 0, 10.0, "EUR"), super7)));
        assertEquals("Precio no válido.", falla422(() -> ctl.editar(5, edicion(2, 3, -1.0, "EUR"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.editar(5, edicion(2, 3, 10.0, "CNY"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.editar(5, edicion(8, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoCambiandoLaCantidadEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        assertEquals("No se puede cambiar la cantidad de un pedido recibido.",
                falla422(() -> ctl.editar(5, edicion(2, 5, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoSinCambiarLaCantidadEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        ctl.editar(5, edicion(2, 4, 12.5, "EUR"), super7);
        verify(dao).editar(5, 2, 4, false, 12.5, "EUR", 12.5, AT);
        verify(logDao).insertar(7, "EDITAR_PEDIDO", "ID_COMPRA: 5");
    }

    // ── Task 2: recepción parcial y resto ──
    @Test void parcialFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        for (int cantidad : new int[]{0, -1, 5, 6}) {
            assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                    ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(cantidad, AT), super7)));
        }
        nadaEscritoNiRegistrado();
    }

    @Test void parcialDentroDeRangoEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(1, AT), super7);
        ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(4, AT), super7);
        verify(dao).confirmarParcial(5, 1, AT);
        verify(dao).confirmarParcial(5, 4, AT);
    }

    @Test void restoConCeroONegativoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(0, AT), super7)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(-2, AT), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void restoQueSuperaLoPedidoEs422YElJustoEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("No puedes recibir más de lo pedido. Faltan 3 unidad(es).", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(4, AT), super7)));
        nadaEscritoNiRegistrado();
        ctl.recibirResto(5, new CompraController.RecibirRestoRequest(3, AT), super7);
        verify(dao).recibirResto(5, 3, AT);
        verify(logDao).insertar(7, "RECIBIR_RESTO", "ID_COMPRA: 5");
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java` completo (conserva los dos tests de la Task 1):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** CompraOtroController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraOtroControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);
    private static final String CINTA = "Cinta de embalar";

    private final CompraOtroDAO dao = mock(CompraOtroDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraOtroController ctl = new CompraOtroController(dao, logDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraOtroControllerTest() {
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(8)).thenReturn(Optional.of(new Proveedor(8, "ACME", false, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    private static CompraOtroController.InsertarRequest alta(int idProv, String concepto, int cantidad, double precio, String divisa) {
        return new CompraOtroController.InsertarRequest(idProv, concepto, cantidad, false, precio, divisa, precio);
    }

    private static CompraOtroController.EditarRequest edicion(int idProv, String concepto, int cantidad, double precio) {
        return new CompraOtroController.EditarRequest(idProv, concepto, cantidad, false, precio, "EUR", precio, AT);
    }

    private static CompraOtro pedido(String estado, int cantidad, Integer recibida) {
        return new CompraOtro(5, 2, "Proveedor A", CINTA, cantidad, recibida, false, AT, null, 1.5, "EUR", 1.5, estado, AT);
    }

    private static String falla422(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaEscritoNiRegistrado() {
        verify(dao, never()).insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());
        verify(dao, never()).editar(anyInt(), anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any());
        verify(dao, never()).confirmarParcial(anyInt(), anyInt(), any());
        verify(dao, never()).recibirResto(anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    // ── Task 1: log de confirmar-alterado ──
    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }

    // ── Task 2 ──
    @Test void altaConConceptoVacioEs422() {
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, null, 3, 1.5, "EUR"), super7)));
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, "", 3, 1.5, "EUR"), super7)));
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, "   ", 3, 1.5, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConCantidadPrecioODivisaNoValidosEs422() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(2, CINTA, 0, 1.5, "EUR"), super7)));
        assertEquals("Precio no válido.", falla422(() -> ctl.insertar(alta(2, CINTA, 3, -1.0, "EUR"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(2, CINTA, 3, 1.5, "CNY"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConProveedorInactivoOInexistenteEs422() {
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(8, CINTA, 3, 1.5, "EUR"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(99, CINTA, 3, 1.5, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaValidaEscribeYRegistra() {
        ctl.insertar(alta(2, CINTA, 3, 1.5, "EUR"), super7);
        verify(dao).insertar(2, CINTA, 3, false, 1.5, "EUR", 1.5);
        verify(logDao).insertar(7, "CREAR_PEDIDO_OTRO", "CONCEPTO: Cinta de embalar, PROVEEDOR: Proveedor A, CANT: 3");
    }

    @Test void editarValidaConceptoYProveedor() {
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.editar(5, edicion(2, " ", 3, 1.5), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.editar(5, edicion(8, CINTA, 3, 1.5), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoCambiandoLaCantidadEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        assertEquals("No se puede cambiar la cantidad de un pedido recibido.",
                falla422(() -> ctl.editar(5, edicion(2, CINTA, 2, 1.5), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void parcialFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                ctl.confirmarParcial(5, new CompraOtroController.ConfirmarParcialRequest(0, AT), super7)));
        assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                ctl.confirmarParcial(5, new CompraOtroController.ConfirmarParcialRequest(5, AT), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void restoFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraOtroController.RecibirRestoRequest(0, AT), super7)));
        assertEquals("No puedes recibir más de lo pedido. Faltan 3 unidad(es).", falla422(() ->
                ctl.recibirResto(5, new CompraOtroController.RecibirRestoRequest(4, AT), super7)));
        nadaEscritoNiRegistrado();
    }
}
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraControllerTest,CompraOtroControllerTest'`
Expected: FAIL: 11 de 15 en `CompraControllerTest` y 7 de 10 en `CompraOtroControllerTest`. Los de 422 fallan con `AssertionFailedError: Expected ResponseStatusException to be thrown, but nothing was thrown` (el controlador escribe sin validar); `altaValidaNormalizaLaDivisaYEscribe` falla por argumentos distintos (`" eur "` frente a `"EUR"`); `parcialDentroDeRangoEscribe`, `editarUnRecibidoSinCambiarLaCantidadEscribe` y `CompraOtroControllerTest.altaValidaEscribeYRegistra` (no son de 422) ya pasan, igual que los dos de la Task 1.

- [ ] **Step 7: `ValidacionPedidos`**

`src/main/java/com/reparaciones/servidor/controller/ValidacionPedidos.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** Reglas de alta, edición y recepción de pedidos que hasta el 4b solo aplicaba el cliente JavaFX (spec 4b §4.2).
 *  Mismos textos que el cliente cuando existen. Un 422 se lanza siempre antes de escribir y de registrar log.
 *  La usan CompraController, CompraOtroController y CompraLoteController. */
final class ValidacionPedidos {

    private ValidacionPedidos() {}

    /** Las dos divisas del combo del cliente (mismo criterio que ProveedorController, sub-proyecto 4a). */
    static final Set<String> DIVISAS = Set.of("EUR", "USD");

    static final String MSG_CANTIDAD            = "Cantidad no válida (debe ser > 0).";
    static final String MSG_PRECIO              = "Precio no válido.";
    static final String MSG_CONCEPTO            = "El concepto no puede estar vacío.";
    static final String MSG_DIVISA              = "Divisa no válida (EUR o USD).";
    static final String MSG_COMPONENTE_INACTIVO = "El componente no está activo.";
    static final String MSG_PROVEEDOR_INACTIVO  = "El proveedor no está activo.";
    static final String MSG_CANTIDAD_RECIBIDO   = "No se puede cambiar la cantidad de un pedido recibido.";
    static final String MSG_RESTO_CERO          = "La cantidad debe ser mayor que 0.";

    static void cantidadPositiva(int cantidad) {
        if (cantidad <= 0) throw regla(MSG_CANTIDAD);
    }

    /** {@code !(precio >= 0)} rechaza también NaN. */
    static void precioNoNegativo(double precio) {
        if (!(precio >= 0)) throw regla(MSG_PRECIO);
    }

    static void conceptoInformado(String concepto) {
        if (concepto == null || concepto.isBlank()) throw regla(MSG_CONCEPTO);
    }

    /** Devuelve la divisa normalizada (recortada y en mayúsculas), que es la que se guarda. */
    static String divisaValida(String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (!DIVISAS.contains(d)) throw regla(MSG_DIVISA);
        return d;
    }

    static ComponenteDAO.Basico componenteActivo(ComponenteDAO dao, int idCom) {
        ComponenteDAO.Basico c = dao.getBasico(idCom).orElse(null);
        if (c == null || !c.activo()) throw regla(MSG_COMPONENTE_INACTIVO);
        return c;
    }

    static Proveedor proveedorActivo(ProveedorDAO dao, int idProv) {
        Proveedor p = dao.getById(idProv).orElse(null);
        if (p == null || !p.isActivo()) throw regla(MSG_PROVEEDOR_INACTIVO);
        return p;
    }

    /** P2: en un pedido recibido la cantidad pedida no se toca (el stock ya se sumó con ella). */
    static void cantidadEditable(String estado, int cantidadGuardada, int cantidadNueva) {
        if ("recibido".equals(estado) && cantidadNueva != cantidadGuardada) throw regla(MSG_CANTIDAD_RECIBIDO);
    }

    static void rangoParcial(int cantidadRecibida, int cantidad) {
        if (cantidadRecibida <= 0 || cantidadRecibida >= cantidad) {
            throw regla("La cantidad debe ser mayor que 0 y menor que " + cantidad + ".");
        }
    }

    static void rangoResto(int cantidadExtra, Integer recibida, int cantidad) {
        if (cantidadExtra <= 0) throw regla(MSG_RESTO_CERO);
        int restante = cantidad - (recibida == null ? 0 : recibida);
        if (cantidadExtra > restante) {
            throw regla("No puedes recibir más de lo pedido. Faltan " + restante + " unidad(es).");
        }
    }

    static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
```

- [ ] **Step 8: `CompraController` validado**

`src/main/java/com/reparaciones/servidor/controller/CompraController.java` completo:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.ValorEntero;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraComponenteDAO dao;
    private final LogDAO              logDao;
    private final ComponenteDAO       componenteDao;
    private final ProveedorDAO        proveedorDao;

    public CompraController(CompraComponenteDAO dao, LogDAO logDao,
                            ComponenteDAO componenteDao, ProveedorDAO proveedorDao) {
        this.dao           = dao;
        this.logDao        = logDao;
        this.componenteDao = componenteDao;
        this.proveedorDao  = proveedorDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<CompraComponente> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping("/en-camino")
    public List<CompraComponente> getEnCamino() {
        return dao.getEnCamino();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-en-camino/{idCom}")
    public ValorEntero getCantidadEnCamino(@PathVariable int idCom) {
        return new ValorEntero(dao.getCantidadEnCaminoPorComponente(idCom));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.componenteActivo(componenteDao, req.idCom());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, req.precioEur());
        String tipo = componenteDao.getTipoById(req.idCom());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO",
                "COMPONENTE: " + tipo + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCompra}")
    public void editar(@PathVariable int idCompra, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, req.precioEur(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        CompraComponente compra = dao.getById(idCompra).orElse(null);
        dao.confirmarRecibido(idCompra, req.updatedAt());
        String detalle = compra != null
                ? "ID_COMPRA: " + idCompra + ", COMPONENTE: " + compra.getTipoComponente() +
                  ", CANT: " + compra.getCantidad()
                : "ID_COMPRA: " + idCompra;
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int idCompra, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.rangoParcial(req.cantidadRecibida(), c.getCantidad()));
        dao.confirmarParcial(idCompra, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL",
                "ID_COMPRA: " + idCompra + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/recibir-resto")
    public void recibirResto(@PathVariable int idCompra, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.rangoResto(req.cantidadExtra(), c.getCantidadRecibida(), c.getCantidad()));
        dao.recibirResto(idCompra, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(idCompra, req.updatedAt());
        // Hasta el 4b era la única transición sin log (inventario de Pedidos §8)
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/cancelar")
    public void cancelar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

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

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/desrecibir")
    public void desrecibir(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "DESRECIBIR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    record InsertarRequest(int idCom, int idProv, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa, double precioEur) {}
    record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur,
                         LocalDateTime updatedAt) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
}
```

- [ ] **Step 9: `CompraOtroController` validado**

`src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java` completo:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/compras-otros")
public class CompraOtroController {

    private final CompraOtroDAO dao;
    private final LogDAO        logDao;
    private final ProveedorDAO  proveedorDao;

    public CompraOtroController(CompraOtroDAO dao, LogDAO logDao, ProveedorDAO proveedorDao) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.proveedorDao = proveedorDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<CompraOtro> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.insertar(req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, req.precioEur());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO_OTRO",
                "CONCEPTO: " + req.concepto() + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{id}")
    public void editar(@PathVariable int id, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.getById(id).ifPresent(c ->
                ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        dao.editar(id, req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, req.precioEur(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar")
    public void confirmar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        CompraOtro c = dao.getById(id).orElse(null);
        dao.confirmarRecibido(id, req.updatedAt());
        String detalle = c != null
                ? "ID_COMPRA_OTRO: " + id + ", CONCEPTO: " + c.getConcepto() + ", CANT: " + c.getCantidad()
                : "ID_COMPRA_OTRO: " + id;
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO_OTRO", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int id, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(id).ifPresent(c -> ValidacionPedidos.rangoParcial(req.cantidadRecibida(), c.getCantidad()));
        dao.confirmarParcial(id, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL_OTRO",
                "ID_COMPRA_OTRO: " + id + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/recibir-resto")
    public void recibirResto(@PathVariable int id, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(id).ifPresent(c ->
                ValidacionPedidos.rangoResto(req.cantidadExtra(), c.getCantidadRecibida(), c.getCantidad()));
        dao.recibirResto(id, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/desrecibir")
    public void desrecibir(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "DESRECIBIR ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/cancelar")
    public void cancelar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{id}")
    public void borrar(@PathVariable int id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrarPendiente(id);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa, double precioEur) {}
    record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
}
```

- [ ] **Step 10: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraControllerTest,CompraOtroControllerTest,CompraControllerEnCaminoTest'`
Expected: PASS, 26 tests (15 + 10 + 1).

- [ ] **Step 11: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'`
Expected: sin fallos y `355` (330 + 25: 2 + 2 de DAO, 13 de `CompraControllerTest` y 8 de `CompraOtroControllerTest`).

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ValidacionPedidos.java src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java src/main/java/com/reparaciones/servidor/controller/CompraController.java src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java src/test/java/com/reparaciones/servidor/dao/ComponenteDAOBasicoTest.java src/test/java/com/reparaciones/servidor/dao/ProveedorDAOGetByIdTest.java src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java
git commit -m "feat(compras): 422 de alta, edicion y recepcion de pedidos con los textos del cliente y cantidad fija en recibido"
```

---

## Task 3: Servidor — `precioEur` lo calcula el servidor (P3) y 503 cuando no hay tasa

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/service/ConversionEur.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/TipoCambioDAO.java` (clase completa: constructor :24-29 y `fetchFromFrankfurter` :52-84)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraController.java` (campo y constructor :22-33, `insertar` y `editar`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java` (campo y constructor :20-28, `insertar` y `editar`)
- Create: `src/test/java/com/reparaciones/servidor/service/ConversionEurTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/TipoCambioDAOTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java`, `CompraOtroControllerTest.java`, `CompraControllerEnCaminoTest.java` (constructor con `ConversionEur`)

**Interfaces:**
- Consumes: `TipoCambioDAO.getTasa(String)` (:31-50): EUR → 1.0 sin consulta; si no, caché del día en `TipoCambio` y, si falta, Frankfurter `from=EUR&to={DIV}`, que devuelve **unidades de la divisa por 1 EUR** (1,1367 USD por euro el 2026-09-25).
- Produces:
  - `service.ConversionEur` (`@Service`): `public double aEuros(double precioUnidad, String divisa)`. EUR (o nula/en blanco) → `precioUnidad` tal cual, sin llamar a `getTasa`; otra → `BigDecimal.valueOf(precioUnidad).divide(BigDecimal.valueOf(tasa), 2, RoundingMode.HALF_UP).doubleValue()` con la divisa recortada y en mayúsculas. Con tasa 1.1367, 10 USD → 8.80.
  - `TipoCambioDAO`: cualquier fallo de Frankfurter (red, estado, cuerpo vacío, sin `rates`, sin la divisa) → `ResponseStatusException(SERVICE_UNAVAILABLE, "No se pudo obtener el tipo de cambio de {DIV}. Inténtalo de nuevo.")` con la causa encadenada (antes `RuntimeException` → 500). Afecta a `GET /api/tipo-cambio/{divisa}` (misma forma de respuesta) y a toda escritura con divisa distinta de EUR. Constructor package-private `TipoCambioDAO(JdbcTemplate, HttpClient)` para los tests; el público lleva `@Autowired` (con dos constructores Spring no elegiría ninguno).
  - `insertar` y `editar` de `CompraController` y `CompraOtroController` guardan `conversion.aEuros(req.precioUnidad(), divisa)` (con la divisa ya validada por la Task 2) e ignoran `req.precioEur()`; el cálculo va después de las validaciones y antes de escribir: un 503 no escribe ni registra log. Para el JavaFX 0.16.x (que multiplicaba) queda corregido sin tocarlo.

- [ ] **Step 1: Tests de `ConversionEur` y `TipoCambioDAO` (fallan)**

`src/test/java/com/reparaciones/servidor/service/ConversionEurTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.TipoCambioDAO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** P3 (spec 4b §4.3): Frankfurter da unidades de divisa por 1 EUR, así que el importe en euros es precio / tasa,
 *  redondeado a 2 decimales HALF_UP (el cliente JavaFX multiplicaba). */
class ConversionEurTest {

    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final ConversionEur conversion = new ConversionEur(tipoCambio);

    @Test void enEurosDevuelveElPrecioSinConsultarLaTasa() {
        assertEquals(12.345, conversion.aEuros(12.345, "EUR"));
        assertEquals(12.345, conversion.aEuros(12.345, " eur "));
        verifyNoInteractions(tipoCambio);
    }

    @Test void enDolaresDivideEntreLaTasaYRedondeaADosDecimales() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        assertEquals(8.80, conversion.aEuros(10.0, "USD"));   // 8.7974… → 8.80
        assertEquals(0.0, conversion.aEuros(0.0, "USD"));
    }

    @Test void elRedondeoEsHalfUpYLaDivisaSeNormaliza() {
        when(tipoCambio.getTasa("USD")).thenReturn(2.0);
        assertEquals(0.13, conversion.aEuros(0.25, " usd "));  // 0.125 → 0.13 (HALF_EVEN daría 0.12)
        verify(tipoCambio).getTasa("USD");
    }

    @Test void sinTasaPropagaEl503() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> conversion.aEuros(10.0, "USD"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }
}
```

`src/test/java/com/reparaciones/servidor/dao/TipoCambioDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Si Frankfurter falla, 503 con un mensaje que el usuario entiende (spec 4b §4.3); antes era un 500 genérico. */
class TipoCambioDAOTest {

    private static final String MSG_USD = "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HttpClient http = mock(HttpClient.class);
    private final TipoCambioDAO dao = new TipoCambioDAO(jdbc, http);

    @SuppressWarnings("unchecked")
    private void frankfurterResponde(String cuerpo) throws Exception {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body()).thenReturn(cuerpo);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
    }

    private void falla503() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.getTasa("USD"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals(MSG_USD, e.getReason());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test void eurEsUnoSinConsultarNada() {
        assertEquals(1.0, dao.getTasa("EUR"));
        verifyNoInteractions(jdbc, http);
    }

    @Test void conLaTasaDeHoyEnCacheNoLlamaAFrankfurter() {
        when(jdbc.query(contains("FROM TipoCambio"), any(RowMapper.class), eq("USD"), any())).thenReturn(List.of(1.1367));
        assertEquals(1.1367, dao.getTasa("USD"));
        verifyNoInteractions(http);
    }

    @Test void frankfurterBienGuardaLaTasaDelDia() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{\"USD\":1.1367}}");
        assertEquals(1.1367, dao.getTasa("usd"));
        verify(jdbc).update(contains("INSERT INTO TipoCambio"), eq("USD"), any(), eq(1.1367), eq(1.1367));
    }

    @Test void frankfurterCaidoEs503ConElMensaje() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("sin red"));
        falla503();
    }

    @Test void respuestaSinLaDivisaEs503() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{}}");
        falla503();
    }
}
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='ConversionEurTest,TipoCambioDAOTest'`
Expected: FAIL de compilación (`ConversionEur` no existe; `TipoCambioDAO` no tiene el constructor con `HttpClient`).

- [ ] **Step 3: `ConversionEur`**

`src/main/java/com/reparaciones/servidor/service/ConversionEur.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.TipoCambioDAO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Importe en euros de un precio en su divisa (spec 4b §4.3, P3). La tasa de TipoCambioDAO es la de Frankfurter con
 *  from=EUR: unidades de la divisa por 1 EUR, así que se divide. Lo usan los POST/PUT sueltos de compras y de otros y
 *  los dos lotes; el precioEur que manda el cliente se ignora (el JavaFX 0.16.x multiplicaba). */
@Service
public class ConversionEur {

    private final TipoCambioDAO tipoCambio;

    public ConversionEur(TipoCambioDAO tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    /** EUR (o sin divisa) → el propio precio, sin consultar la tasa. Otra divisa → precio / tasa a 2 decimales
     *  HALF_UP. Si no hay tasa, propaga el 503 de TipoCambioDAO. */
    public double aEuros(double precioUnidad, String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (d.isEmpty() || "EUR".equals(d)) return precioUnidad;
        double tasa = tipoCambio.getTasa(d);
        return BigDecimal.valueOf(precioUnidad)
                .divide(BigDecimal.valueOf(tasa), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
```

- [ ] **Step 4: `TipoCambioDAO` con 503**

`src/main/java/com/reparaciones/servidor/dao/TipoCambioDAO.java` completo (`getTasa` no cambia):

```java
package com.reparaciones.servidor.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Repository
public class TipoCambioDAO {

    /** 503 con un texto que el usuario entiende (sub-proyecto 4b); antes cualquier fallo era un 500 genérico. */
    static final String MSG_SIN_TASA = "No se pudo obtener el tipo de cambio de %s. Inténtalo de nuevo.";

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TipoCambioDAO(JdbcTemplate jdbc) {
        // followRedirects: Frankfurter redirige HTTP→HTTPS; sin esto la petición falla con respuesta vacía
        this(jdbc, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build());
    }

    /** Paquete: para que los tests sustituyan la red por un HttpClient mockeado. */
    TipoCambioDAO(JdbcTemplate jdbc, HttpClient httpClient) {
        this.jdbc = jdbc;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public double getTasa(String divisa) {
        if ("EUR".equalsIgnoreCase(divisa)) return 1.0;

        LocalDate today = LocalDate.now();
        List<Double> cached = jdbc.query(
                "SELECT TASA FROM TipoCambio WHERE DIVISA = ? AND FECHA = ?",
                (rs, row) -> rs.getDouble("TASA"),
                divisa.toUpperCase(), Date.valueOf(today));

        if (!cached.isEmpty()) return cached.get(0);

        double tasa = fetchFromFrankfurter(divisa.toUpperCase());

        jdbc.update(
                "INSERT INTO TipoCambio (DIVISA, FECHA, TASA) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE TASA = ?",
                divisa.toUpperCase(), Date.valueOf(today), tasa, tasa);

        return tasa;
    }

    private double fetchFromFrankfurter(String divisa) {
        try {
            String url = "https://api.frankfurter.app/latest?from=EUR&to=" + divisa;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "gestion-reparaciones/1.0") // algunas APIs rechazan peticiones sin User-Agent
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Validaciones explícitas: el detalle queda en la causa del 503 para los logs del servidor
            if (body == null || body.isEmpty()) {
                throw new IllegalStateException("Frankfurter respuesta vacia (status " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode rates = root.get("rates");
            if (rates == null) {
                throw new IllegalStateException("No hay 'rates' en respuesta: " + body);
            }
            JsonNode value = rates.get(divisa);
            if (value == null) {
                throw new IllegalStateException("Divisa " + divisa + " no encontrada: " + body);
            }
            return value.asDouble();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw sinTasa(divisa, e);
        } catch (Exception e) {
            throw sinTasa(divisa, e);
        }
    }

    private static ResponseStatusException sinTasa(String divisa, Throwable causa) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, String.format(MSG_SIN_TASA, divisa), causa);
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='ConversionEurTest,TipoCambioDAOTest'`
Expected: PASS, 9 tests.

- [ ] **Step 6: Tests de controlador (fallan)**

En `CompraControllerTest.java`:
- imports nuevos: `import com.reparaciones.servidor.dao.TipoCambioDAO;` y `import com.reparaciones.servidor.service.ConversionEur;`
- sustituir la línea del controlador por estas dos (el mock de la tasa va antes para que exista al construir):

```java
    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final CompraController ctl = new CompraController(dao, logDao, componenteDao, proveedorDao,
            new ConversionEur(tipoCambio));
```

- añadir al final de la clase:

```java
    // ── Task 3: precioEur lo calcula el servidor ──
    @Test void altaEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.insertar(new CompraController.InsertarRequest(1, 2, 3, false, 10.0, "USD", 999.0), super7);
        verify(dao).insertar(1, 2, 3, false, 10.0, "USD", 8.8);
    }

    @Test void editarEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.editar(5, new CompraController.EditarRequest(2, 4, false, 10.0, "usd", 999.0, AT), super7);
        verify(dao).editar(5, 2, 4, false, 10.0, "USD", 8.8, AT);
    }

    @Test void sinTasaEs503SinEscribirNiRegistrar() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.insertar(new CompraController.InsertarRequest(1, 2, 3, false, 10.0, "USD", 999.0), super7));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        nadaEscritoNiRegistrado();
    }
```

En `CompraOtroControllerTest.java`:
- mismos dos imports;
- sustituir la línea del controlador por:

```java
    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final CompraOtroController ctl = new CompraOtroController(dao, logDao, proveedorDao,
            new ConversionEur(tipoCambio));
```

- añadir al final de la clase:

```java
    // ── Task 3: precioEur lo calcula el servidor ──
    @Test void altaEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.insertar(new CompraOtroController.InsertarRequest(2, CINTA, 3, false, 10.0, "USD", 999.0), super7);
        verify(dao).insertar(2, CINTA, 3, false, 10.0, "USD", 8.8);
    }

    @Test void editarEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.editar(5, new CompraOtroController.EditarRequest(2, CINTA, 4, false, 10.0, "USD", 999.0, AT), super7);
        verify(dao).editar(5, 2, CINTA, 4, false, 10.0, "USD", 8.8, AT);
    }
```

En `CompraControllerEnCaminoTest.java`, añadir `import com.reparaciones.servidor.service.ConversionEur;` y sustituir la construcción por:

```java
        CompraController ctl = new CompraController(dao, mock(LogDAO.class), mock(ComponenteDAO.class),
                mock(ProveedorDAO.class), mock(ConversionEur.class));
```

- [ ] **Step 7: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraControllerTest,CompraOtroControllerTest,CompraControllerEnCaminoTest'`
Expected: FAIL de compilación (los constructores no reciben `ConversionEur`).

- [ ] **Step 8: Los dos controladores calculan el EUR**

En `CompraController.java`: import `com.reparaciones.servidor.service.ConversionEur`; campos y constructor (:22-33) por:

```java
    private final CompraComponenteDAO dao;
    private final LogDAO              logDao;
    private final ComponenteDAO       componenteDao;
    private final ProveedorDAO        proveedorDao;
    private final ConversionEur       conversion;

    public CompraController(CompraComponenteDAO dao, LogDAO logDao,
                            ComponenteDAO componenteDao, ProveedorDAO proveedorDao,
                            ConversionEur conversion) {
        this.dao           = dao;
        this.logDao        = logDao;
        this.componenteDao = componenteDao;
        this.proveedorDao  = proveedorDao;
        this.conversion    = conversion;
    }
```

y `insertar` y `editar` por:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.componenteActivo(componenteDao, req.idCom());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        // P3: el importe en euros lo calcula el servidor; req.precioEur() se ignora
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String tipo = componenteDao.getTipoById(req.idCom());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO",
                "COMPONENTE: " + tipo + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCompra}")
    public void editar(@PathVariable int idCompra, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }
```

En `CompraOtroController.java`: import `com.reparaciones.servidor.service.ConversionEur`; campos y constructor (:20-28) por:

```java
    private final CompraOtroDAO dao;
    private final LogDAO        logDao;
    private final ProveedorDAO  proveedorDao;
    private final ConversionEur conversion;

    public CompraOtroController(CompraOtroDAO dao, LogDAO logDao, ProveedorDAO proveedorDao,
                                ConversionEur conversion) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.proveedorDao = proveedorDao;
        this.conversion   = conversion;
    }
```

y `insertar` y `editar` por:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        // P3: el importe en euros lo calcula el servidor; req.precioEur() se ignora
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.insertar(req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO_OTRO",
                "CONCEPTO: " + req.concepto() + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{id}")
    public void editar(@PathVariable int id, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.getById(id).ifPresent(c ->
                ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.editar(id, req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }
```

Comprobar que nadie más construye estos dos controladores: `grep -rn "new CompraController\|new CompraOtroController" src/test` (solo los tres ficheros de test tocados en el Step 6).

- [ ] **Step 9: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraControllerTest,CompraOtroControllerTest,CompraControllerEnCaminoTest,ConversionEurTest,TipoCambioDAOTest'`
Expected: PASS, 40 tests (18 + 12 + 1 + 4 + 5).

- [ ] **Step 10: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'`
Expected: sin fallos y `369` (355 + 14: 4 + 5 + 3 + 2). `OpenApiContractTest` levanta el contexto: valida el `@Autowired` del constructor de `TipoCambioDAO` y el wiring de `ConversionEur`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/service/ConversionEur.java src/main/java/com/reparaciones/servidor/dao/TipoCambioDAO.java src/main/java/com/reparaciones/servidor/controller/CompraController.java src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java src/test/java/com/reparaciones/servidor/service/ConversionEurTest.java src/test/java/com/reparaciones/servidor/dao/TipoCambioDAOTest.java src/test/java/com/reparaciones/servidor/controller/CompraControllerTest.java src/test/java/com/reparaciones/servidor/controller/CompraOtroControllerTest.java src/test/java/com/reparaciones/servidor/controller/CompraControllerEnCaminoTest.java
git commit -m "fix(compras): el servidor calcula el importe en euros dividiendo por la tasa y responde 503 sin tipo de cambio"
```

---

## Task 4: Servidor — `POST /api/compras/lote` (P5): lote de compras transaccional con clave y marcado de solicitudes

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/LoteCompras.java`
- Create: `src/main/java/com/reparaciones/servidor/service/CompraLoteService.java`
- Create: `src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ValidacionPedidos.java` (textos de lote)
- Modify: `src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` (`insertar` :76-84 devuelve el id; imports)
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionComponenteDAO.java` (`getIdComDeSolicitud`, junto a `actualizarEstadoSolicitud` :158)
- Modify: `src/main/java/com/reparaciones/servidor/dao/SolicitudStockDAO.java` (`getIdCom`, junto a `actualizarEstado` :57)
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java:274-279` (la ruta nueva declara `Idempotency-Key`)
- Create: `src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOInsertarTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/ReparacionComponenteDAOSolicitudTest.java`
- Create: `src/test/java/com/reparaciones/servidor/dao/SolicitudStockDAOTest.java`
- Create: `src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTest.java`
- Create: `src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTransaccionTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/CompraLoteControllerTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/RolesCompraLoteTest.java`

**Interfaces:**
- Consumes: `RegistroIdempotencia.ejecutar(int idUsuario, String operacion, String clave, Object peticion, Supplier<T> escritura, Consumer<T> trasEscribir)` (`RegistroIdempotencia.java:64`, la sobrecarga de seis argumentos; la de cinco está en :47; con excepción en `escritura` libera la entrada, :104-110; el repetido con la misma petición devuelve el resultado guardado sin ejecutar nada, :94-98) y `RegistroIdempotencia.CABECERA` (:25); `AsignacionController.MSG_SIN_CLAVE` (`AsignacionController.java:27`, package-private, mismo paquete); `ReparacionComponenteDAO.actualizarEstadoSolicitud(int idRc, String estado)` (`ReparacionComponenteDAO.java:158-175`, con `"GESTIONADA"` solo actúa si no lo estaba ya) y `SolicitudStockDAO.actualizarEstado(int idSol, String estado)` (`SolicitudStockDAO.java:57-68`, ídem); `ComponenteDAO.getBasico` y `ProveedorDAO.getById` (Task 2); `ConversionEur.aEuros` (Task 3).
- Produces:
  - `model.LoteCompras` con los records anidados EXACTOS de el documento de interfaces del reparto (fuera del repo): `Peticion(List<Linea> lineas, Solicitudes solicitudes)`, `Linea(@Schema(nullable = true) Integer idCom, @Schema(nullable = true) Integer idProv, int cantidad, boolean esUrgente, double precioUnidad)`, `Solicitudes(List<Integer> urgentes, List<Integer> preventivas)`, `Respuesta(List<Integer> idsCreados)`. springdoc (`OpenApiConfig.NombreEsquemaAnidado`, prefija la envolvente) los publica como `LoteComprasPeticion`, `LoteComprasLinea`, `LoteComprasSolicitudes`, `LoteComprasRespuesta`.
  - `int CompraComponenteDAO.insertar(...)`: misma firma de entrada, devuelve el `ID_COMPRA` generado (`GeneratedKeyHolder` + `Statement.RETURN_GENERATED_KEYS`); sigue resolviendo al master. El `POST /api/compras` suelto lo ignora (su respuesta no cambia: 201 sin cuerpo).
  - `Integer ReparacionComponenteDAO.getIdComDeSolicitud(int idRc)` y `Integer SolicitudStockDAO.getIdCom(int idSol)`: el `ID_COM` de la solicitud o `null` si no existe (o, en urgentes, si no tiene componente).
  - `service.CompraLoteService` (`@Service`): `public record LineaCompra(int idCom, int idProv, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur)` y `@Transactional public LoteCompras.Respuesta guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes, List<Integer> preventivas)`: un `insertar` por línea en orden (ids en ese orden) y después `actualizarEstadoSolicitud(idRc, "GESTIONADA")` por urgente y `actualizarEstado(idSol, "GESTIONADA")` por preventiva. Cualquier excepción deshace todo. El constructor recibe ya `CompraOtroDAO`, que usa `guardarOtros` en la Task 5.
  - `POST /api/compras/lote` (`CompraLoteController`, `@PreAuthorize("hasRole('SUPERTECNICO')")`), cabecera `Idempotency-Key` declarada opcional en el contrato (como asignaciones) pero exigida: sin ella o en blanco → 400 `"Falta la clave de idempotencia"`. Orden de trabajo: 400 → 422 de líneas → 422 de solicitudes → `idempotencia.ejecutar(idUsu, "compras-lote", clave, req, escritura, trasEscribir)`; dentro de `escritura`, primero la divisa (`Proveedor.DIVISA`, nula o en blanco → `EUR`) y el EUR de cada línea con `ConversionEur` (fuera de la transacción: un 503 no escribe y libera la clave) y después `servicio.guardarCompras`. `trasEscribir`: `CREAR_PEDIDO` por línea con `"COMPONENTE: {tipo}, PROVEEDOR: {nombre}, CANT: {n}"` (tipo del id pedido, como el POST suelto), `GESTIONAR_SOLICITUD` `"ID_RC: {id}, ESTADO: GESTIONADA"` por urgente y `GESTIONAR_SOLICITUD_STOCK` `"ID_SOL: {id}, ESTADO: GESTIONADA"` por preventiva. Respuesta 200 `LoteCompras.Respuesta`.
  - La tasa se resuelve por línea (`conversion.aEuros` en cada una, también en la Task 5), no una vez por divisa distinta como dice la spec §4.4: inocuo, porque la primera línea en USD consulta Frankfurter y guarda la tasa del día en `TipoCambio` y las siguientes la leen de BD (N lecturas en vez de una).
  - 422 del lote, textos EXACTOS: `"Añade al menos una línea."` (lista nula o vacía); por línea `i` (1-based), en este orden: `"Línea {i}: selecciona un componente."` (idCom nulo o inexistente, o línea nula), `"Línea {i}: selecciona un proveedor."` (idProv nulo o inexistente), `"Línea {i}: el componente está desactivado."`, `"Línea {i}: el proveedor está desactivado."`, `"Línea {i}: la cantidad debe ser mayor que 0."`, `"Línea {i}: el precio no puede ser negativo."`; después, `"La solicitud no corresponde a ninguna línea del pedido."` si el componente de una solicitud (resuelto al master) no es el master de ninguna línea (también si la solicitud no existe o es un id nulo). `solicitudes` nula = sin solicitudes.
  - La operación de idempotencia `"compras-lote"` y la ruta en `OpenApiContractTest.lasEscriturasDelFormularioAdmitenClaveDeIdempotencia`.

- [ ] **Step 1: Tests de las DAO (fallan)**

`src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOInsertarTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** El lote de pedidos devuelve los ids creados (spec 4b §4.4): insertar pasa a devolver la clave generada. */
class CompraComponenteDAOInsertarTest {

    @Test void insertaEnElMasterYDevuelveElIdGenerado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 41));
            return 1;
        });

        int id = new CompraComponenteDAO(jdbc).insertar(12, 2, 3, true, 10.0, "USD", 8.8);

        assertEquals(41, id);
        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(contains("INSERT INTO Compra_componente"), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 3);            // el master, no el slave 12
        verify(ps).setInt(2, 2);
        verify(ps).setInt(3, 3);
        verify(ps).setBoolean(4, true);
        verify(ps).setDouble(5, 10.0);
        verify(ps).setString(6, "USD");
        verify(ps).setDouble(7, 8.8);
    }
}
```

`src/test/java/com/reparaciones/servidor/dao/ReparacionComponenteDAOSolicitudTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El lote de pedidos casa cada solicitud urgente con su línea por el componente (spec 4b §4.4). */
class ReparacionComponenteDAOSolicitudTest {

    @Test void devuelveElComponenteDeLaSolicitudONullSiNoExiste() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("ID_COM", Integer.class)).thenReturn(4);
        when(jdbc.query(eq("SELECT ID_COM FROM Reparacion_componente WHERE ID_RC = ?"), any(RowMapper.class), eq(11)))
                .thenAnswer(inv -> Collections.singletonList(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        ReparacionComponenteDAO dao = new ReparacionComponenteDAO(jdbc);
        assertEquals(Integer.valueOf(4), dao.getIdComDeSolicitud(11));
        assertNull(dao.getIdComDeSolicitud(99));
    }
}
```

`src/test/java/com/reparaciones/servidor/dao/SolicitudStockDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El lote de pedidos casa cada solicitud preventiva con su línea por el componente (spec 4b §4.4). */
class SolicitudStockDAOTest {

    @Test void devuelveElComponenteDeLaSolicitudONullSiNoExiste() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("ID_COM", Integer.class)).thenReturn(1);
        when(jdbc.query(eq("SELECT ID_COM FROM Solicitud_Stock WHERE ID_SOL = ?"), any(RowMapper.class), eq(21)))
                .thenAnswer(inv -> Collections.singletonList(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        SolicitudStockDAO dao = new SolicitudStockDAO(jdbc);
        assertEquals(Integer.valueOf(1), dao.getIdCom(21));
        assertNull(dao.getIdCom(99));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraComponenteDAOInsertarTest,ReparacionComponenteDAOSolicitudTest,SolicitudStockDAOTest'`
Expected: FAIL de compilación (`insertar` es `void`; `getIdComDeSolicitud` y `getIdCom` no existen).

- [ ] **Step 3: Implementar las DAO**

En `CompraComponenteDAO.java`, imports nuevos:

```java
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
```

y `insertar` (:76-84) por:

```java
    /** Devuelve el ID_COMPRA generado (el lote lo devuelve a la web, sub-proyecto 4b; el POST suelto lo ignora).
     *  El pedido se guarda siempre en el master del SKU compartido. */
    public int insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur) {
        int idMaster = resolveToMasterId(idCom);
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_componente" +
                    " (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idMaster);
            ps.setInt(2, idProv);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA del pedido insertado");
        return id.intValue();
    }
```

En `ReparacionComponenteDAO.java`, antes de `actualizarEstadoSolicitud` (:158):

```java
    /** ID_COM de una solicitud urgente, o null si no existe o no tiene componente. El lote de pedidos lo usa para
     *  casar la solicitud con su línea (sub-proyecto 4b). */
    public Integer getIdComDeSolicitud(int idRc) {
        List<Integer> filas = jdbc.query("SELECT ID_COM FROM Reparacion_componente WHERE ID_RC = ?",
                (rs, row) -> rs.getObject("ID_COM", Integer.class), idRc);
        return filas.isEmpty() ? null : filas.get(0);
    }
```

En `SolicitudStockDAO.java`, antes de `actualizarEstado` (:57):

```java
    /** ID_COM de una solicitud preventiva, o null si no existe (sub-proyecto 4b, lote de pedidos). */
    public Integer getIdCom(int idSol) {
        List<Integer> filas = jdbc.query("SELECT ID_COM FROM Solicitud_Stock WHERE ID_SOL = ?",
                (rs, row) -> rs.getObject("ID_COM", Integer.class), idSol);
        return filas.isEmpty() ? null : filas.get(0);
    }
```

(Las dos DAO ya importan `java.util.List`.)

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraComponenteDAOInsertarTest,ReparacionComponenteDAOSolicitudTest,SolicitudStockDAOTest,CompraControllerTest'`
Expected: PASS (1 + 1 + 1 + los 18 de `CompraControllerTest`, que siguen verificando `insertar` sobre el mock).

- [ ] **Step 5: Tests del servicio (fallan)**

`src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.LoteCompras;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas del guardado por lotes de pedidos con las DAO mockeadas (sin transacción real; la frontera transaccional
 *  la prueba CompraLoteServiceTransaccionTest). */
class CompraLoteServiceTest {

    private final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    private final CompraOtroDAO compraOtroDao = mock(CompraOtroDAO.class);
    private final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);
    private final CompraLoteService servicio =
            new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao);

    private static CompraLoteService.LineaCompra linea(int idCom, int cantidad) {
        return new CompraLoteService.LineaCompra(idCom, 2, cantidad, false, 10.0, "USD", 8.8);
    }

    @Test void insertaCadaLineaEnOrdenYDevuelveSusIds() {
        when(compraDao.insertar(1, 2, 3, false, 10.0, "USD", 8.8)).thenReturn(41);
        when(compraDao.insertar(7, 2, 1, false, 10.0, "USD", 8.8)).thenReturn(42);

        LoteCompras.Respuesta r = servicio.guardarCompras(List.of(linea(1, 3), linea(7, 1)), List.of(), List.of());

        assertEquals(List.of(41, 42), r.idsCreados());
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).insertar(1, 2, 3, false, 10.0, "USD", 8.8);
        orden.verify(compraDao).insertar(7, 2, 1, false, 10.0, "USD", 8.8);
        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao, compraOtroDao);
    }

    @Test void marcaLasSolicitudesComoGestionadasDespuesDeInsertar() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble())).thenReturn(41);

        servicio.guardarCompras(List.of(linea(1, 2)), List.of(11, 12), List.of(21));

        InOrder orden = inOrder(compraDao, reparacionComponenteDao, solicitudStockDao);
        orden.verify(compraDao).insertar(1, 2, 2, false, 10.0, "USD", 8.8);
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(11, "GESTIONADA");
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(12, "GESTIONADA");
        orden.verify(solicitudStockDao).actualizarEstado(21, "GESTIONADA");
    }

    @Test void unFalloEnLaSegundaLineaSePropagaSinMarcarSolicitudes() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(41)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarCompras(List.of(linea(1, 1), linea(7, 1)), List.of(11), List.of(21)));

        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao);
    }
}
```

`src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTransaccionTest.java` (mismo montaje que `AsignacionLoteServiceTransaccionTest`, ver su javadoc: los DAO NO son beans para que el auto-proxy no los envuelva):

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frontera transaccional REAL de CompraLoteService (spec 4b §4.4: un lote es todo o nada): servicio real detrás del
 *  proxy @Transactional, DataSource mock y Connection mock para asertar commit/rollback. Si se quita @Transactional,
 *  unFalloEnLaSegundaLineaHaceRollbackYNuncaCommit falla porque rollback() nunca se invoca. */
@SpringJUnitConfig(CompraLoteServiceTransaccionTest.Config.class)
class CompraLoteServiceTransaccionTest {

    private static final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    private static final CompraOtroDAO compraOtroDao = mock(CompraOtroDAO.class);
    private static final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private static final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        CompraLoteService compraLoteService() {
            return new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao);
        }
    }

    @Autowired CompraLoteService servicio;
    @Autowired DataSource dataSource;

    private Connection connection;

    @BeforeEach
    void resetMocks() throws SQLException {
        reset(dataSource, compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    private static CompraLoteService.LineaCompra linea(int idCom) {
        return new CompraLoteService.LineaCompra(idCom, 2, 1, false, 0.0, "EUR", 0.0);
    }

    @Test void unFalloEnLaSegundaLineaHaceRollbackYNuncaCommit() throws SQLException {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(41)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarCompras(List.of(linea(1), linea(7)), List.of(11), List.of()));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verifyNoInteractions(reparacionComponenteDao);
    }

    @Test void elCaminoFelizHaceCommitYNuncaRollback() throws SQLException {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(41);

        servicio.guardarCompras(List.of(linea(1)), List.of(11), List.of(21));

        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteServiceTest,CompraLoteServiceTransaccionTest'`
Expected: FAIL de compilación (`CompraLoteService` y `LoteCompras` no existen).

- [ ] **Step 7: `LoteCompras` y `CompraLoteService`**

`src/main/java/com/reparaciones/servidor/model/LoteCompras.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Alta por lotes del formulario "Nuevo pedido" (spec 4b §4.4, P5). Anidados: springdoc los publica como
 *  LoteComprasPeticion, LoteComprasLinea, LoteComprasSolicitudes y LoteComprasRespuesta. Sin divisa ni precioEur:
 *  la divisa es la del proveedor y el importe en euros lo calcula el servidor. */
public final class LoteCompras {
    private LoteCompras() {}

    /** solicitudes: las urgentes (ID_RC) y preventivas (ID_SOL) que originan las líneas; se marcan GESTIONADA en la
     *  misma transacción. Si llega null se trata como vacía. */
    public record Peticion(List<Linea> lineas, Solicitudes solicitudes) {}

    /** idCom e idProv nulos = sin elegir en el formulario (422 "Línea i: selecciona …"). */
    public record Linea(@Schema(nullable = true) Integer idCom, @Schema(nullable = true) Integer idProv,
                        int cantidad, boolean esUrgente, double precioUnidad) {}

    public record Solicitudes(List<Integer> urgentes, List<Integer> preventivas) {}

    /** Ids creados, en el orden de las líneas. También es la respuesta del lote de otros pedidos. */
    public record Respuesta(List<Integer> idsCreados) {}
}
```

`src/main/java/com/reparaciones/servidor/service/CompraLoteService.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.LoteCompras;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** La transacción de los dos lotes de pedidos (spec 4b §4.4, P5): todas las altas del lote y el marcado de sus
 *  solicitudes juntos; cualquier excepción deshace el lote entero. Recibe líneas ya resueltas (divisa del proveedor
 *  y precioEur calculados por el controlador ANTES de entrar aquí, como el lookup de IMEI en asignaciones). */
@Service
public class CompraLoteService {

    static final String GESTIONADA = "GESTIONADA";

    public record LineaCompra(int idCom, int idProv, int cantidad, boolean esUrgente,
                              double precioUnidad, String divisa, double precioEur) {}

    private final CompraComponenteDAO compraDao;
    private final CompraOtroDAO compraOtroDao;   // lo usa guardarOtros (Task 5)
    private final ReparacionComponenteDAO reparacionComponenteDao;
    private final SolicitudStockDAO solicitudStockDao;

    public CompraLoteService(CompraComponenteDAO compraDao, CompraOtroDAO compraOtroDao,
                             ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao) {
        this.compraDao = compraDao;
        this.compraOtroDao = compraOtroDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
    }

    /** Un alta por línea (insertar resuelve al master) y después cada solicitud a GESTIONADA con los mismos métodos
     *  que los PATCH de la campana (solo actúan si no estaba ya gestionada). */
    @Transactional
    public LoteCompras.Respuesta guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes,
                                                List<Integer> preventivas) {
        List<Integer> ids = new ArrayList<>();
        for (LineaCompra l : lineas) {
            ids.add(compraDao.insertar(l.idCom(), l.idProv(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur()));
        }
        for (Integer idRc : urgentes) reparacionComponenteDao.actualizarEstadoSolicitud(idRc, GESTIONADA);
        for (Integer idSol : preventivas) solicitudStockDao.actualizarEstado(idSol, GESTIONADA);
        return new LoteCompras.Respuesta(ids);
    }
}
```

- [ ] **Step 8: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteServiceTest,CompraLoteServiceTransaccionTest'`
Expected: PASS, 5 tests.

- [ ] **Step 9: Tests del controlador (fallan)**

`src/test/java/com/reparaciones/servidor/controller/CompraLoteControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.dao.TipoCambioDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import com.reparaciones.servidor.service.ConversionEur;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST /api/compras/lote (spec 4b §4.4, P5): clave obligatoria, 422 por línea en el orden de la spec, solicitudes
 *  casadas por master, divisa del proveedor y EUR resueltos antes del servicio, reintento idempotente y logs. */
class CompraLoteControllerTest {

    private static final String CLAVE = "clave-1";

    private final CompraLoteService servicio = mock(CompraLoteService.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);
    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final CompraLoteController ctl = new CompraLoteController(servicio, componenteDao, proveedorDao,
            reparacionComponenteDao, solicitudStockDao, new ConversionEur(tipoCambio), logDao, new RegistroIdempotencia());
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraLoteControllerTest() {
        // Componentes: 1 master activo, 4 slave de 1, 5 desactivado
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(componenteDao.getBasico(4)).thenReturn(Optional.of(new ComponenteDAO.Basico(4, 1, "lcd-y-negro", true)));
        when(componenteDao.getBasico(5)).thenReturn(Optional.of(new ComponenteDAO.Basico(5, 5, "bat-x", false)));
        // Proveedores: 2 en EUR, 3 en USD, 6 desactivado
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(3)).thenReturn(Optional.of(new Proveedor(3, "Proveedor B", true, "USD", null, "COMPONENTES")));
        when(proveedorDao.getById(6)).thenReturn(Optional.of(new Proveedor(6, "ACME", false, "EUR", null, "COMPONENTES")));
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41)));
    }

    private static LoteCompras.Linea linea(Integer idCom, Integer idProv, int cantidad, double precio) {
        return new LoteCompras.Linea(idCom, idProv, cantidad, false, precio);
    }

    private static LoteCompras.Peticion lote(LoteCompras.Linea... lineas) {
        return new LoteCompras.Peticion(Arrays.asList(lineas), null);
    }

    private static LoteCompras.Peticion conSolicitudes(List<Integer> urgentes, List<Integer> preventivas,
                                                       LoteCompras.Linea... lineas) {
        return new LoteCompras.Peticion(Arrays.asList(lineas), new LoteCompras.Solicitudes(urgentes, preventivas));
    }

    private String falla422(LoteCompras.Peticion peticion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteCompras(peticion, super7, CLAVE));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaGuardadoNiRegistrado() {
        verifyNoInteractions(servicio, logDao);
    }

    @Test void sinClaveEs400() {
        for (String clave : new String[]{null, "", "  "}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, clave));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertEquals("Falta la clave de idempotencia", e.getReason());
        }
        nadaGuardadoNiRegistrado();
    }

    @Test void sinLineasEs422() {
        assertEquals("Añade al menos una línea.", falla422(lote()));
        assertEquals("Añade al menos una línea.", falla422(new LoteCompras.Peticion(null, null)));
        nadaGuardadoNiRegistrado();
    }

    @Test void lineaSinComponenteOConUnoInexistenteEs422ConSuNumero() {
        assertEquals("Línea 2: selecciona un componente.", falla422(lote(linea(1, 2, 1, 0.0), linea(null, 2, 1, 0.0))));
        assertEquals("Línea 2: selecciona un componente.", falla422(lote(linea(1, 2, 1, 0.0), linea(99, 2, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void lineaSinProveedorOConUnoInexistenteEs422() {
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(1, null, 1, 0.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(1, 99, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    /** Orden de la spec: componente, proveedor, componente desactivado, proveedor desactivado, cantidad, precio. */
    @Test void losErroresDeUnaLineaSalenEnElOrdenDeLaSpec() {
        assertEquals("Línea 1: selecciona un componente.", falla422(lote(linea(null, null, 0, -1.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(5, null, 0, -1.0))));
        assertEquals("Línea 1: el componente está desactivado.", falla422(lote(linea(5, 6, 0, -1.0))));
        assertEquals("Línea 1: el proveedor está desactivado.", falla422(lote(linea(1, 6, 0, -1.0))));
        assertEquals("Línea 1: la cantidad debe ser mayor que 0.", falla422(lote(linea(1, 2, 0, -1.0))));
        assertEquals("Línea 1: el precio no puede ser negativo.", falla422(lote(linea(1, 2, 1, -1.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void unaSolicitudSinLineaEs422() {
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(5);     // bat-x: ninguna línea lo pide
        when(solicitudStockDao.getIdCom(21)).thenReturn(5);
        when(reparacionComponenteDao.getIdComDeSolicitud(12)).thenReturn(null);  // no existe
        String msg = "La solicitud no corresponde a ninguna línea del pedido.";
        assertEquals(msg, falla422(conSolicitudes(List.of(11), List.of(), linea(1, 2, 1, 0.0))));
        assertEquals(msg, falla422(conSolicitudes(List.of(), List.of(21), linea(1, 2, 1, 0.0))));
        assertEquals(msg, falla422(conSolicitudes(List.of(12), List.of(), linea(1, 2, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void laSolicitudDeUnSlaveCasaConLaLineaDelMasterYSePasaAlServicio() {
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(4);    // slave de 1
        when(solicitudStockDao.getIdCom(21)).thenReturn(1);
        ctl.guardarLoteCompras(conSolicitudes(List.of(11), List.of(21), linea(1, 2, 2, 0.0)), super7, CLAVE);
        verify(servicio).guardarCompras(anyList(), eq(List.of(11)), eq(List.of(21)));
    }

    @Test void laDivisaEsLaDelProveedorYElEurSeCalculaAntesDelServicio() {
        ctl.guardarLoteCompras(lote(linea(1, 3, 2, 10.0), linea(4, 2, 1, 5.5)), super7, CLAVE);
        verify(servicio).guardarCompras(eq(List.of(
                new CompraLoteService.LineaCompra(1, 3, 2, false, 10.0, "USD", 8.8),
                new CompraLoteService.LineaCompra(4, 2, 1, false, 5.5, "EUR", 5.5))), eq(List.of()), eq(List.of()));
    }

    @Test void laMismaClaveYLaMismaPeticionDevuelvenLaRespuestaGuardadaSinInsertar() {
        LoteCompras.Respuesta primera = ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, CLAVE);
        LoteCompras.Respuesta segunda = ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, CLAVE);
        assertSame(primera, segunda);
        assertEquals(List.of(41), segunda.idsCreados());
        verify(servicio, times(1)).guardarCompras(anyList(), anyList(), anyList());
        verify(logDao, times(1)).insertar(eq(7), eq("CREAR_PEDIDO"), anyString());
    }

    @Test void registraUnLogPorLineaYOtroPorSolicitudUnaSolaVez() {
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41, 42)));
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(1);
        when(solicitudStockDao.getIdCom(21)).thenReturn(4);
        ctl.guardarLoteCompras(conSolicitudes(List.of(11), List.of(21),
                linea(1, 2, 3, 0.0), linea(4, 3, 1, 10.0)), super7, CLAVE);
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 3");
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-y-negro, PROVEEDOR: Proveedor B, CANT: 1");
        verify(logDao).insertar(7, "GESTIONAR_SOLICITUD", "ID_RC: 11, ESTADO: GESTIONADA");
        verify(logDao).insertar(7, "GESTIONAR_SOLICITUD_STOCK", "ID_SOL: 21, ESTADO: GESTIONADA");
        verifyNoMoreInteractions(logDao);
    }

    @Test void sinTasaEs503SinLlamarAlServicioYLaClaveSePuedeReintentar() {
        when(tipoCambio.getTasa("USD"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."))
                .thenReturn(1.1367);
        LoteCompras.Peticion peticion = lote(linea(1, 3, 1, 10.0));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteCompras(peticion, super7, CLAVE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(servicio, logDao);
        // La escritura no llegó a hacerse: RegistroIdempotencia liberó la clave y el reintento sí guarda
        ctl.guardarLoteCompras(peticion, super7, CLAVE);
        verify(servicio).guardarCompras(anyList(), anyList(), anyList());
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/RolesCompraLoteTest.java` (cadena de seguridad real, mismo montaje que `RolesAsignacionLoteTest`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los lotes de pedidos (spec 4b §4.4): solo SUPERTECNICO, clave obligatoria y wiring del controlador y el servicio
 *  nuevos con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesCompraLoteTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean CompraLoteService servicio;
    @MockBean ComponenteDAO componenteDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final String CUERPO_COMPRAS = """
            {"lineas":[{"idCom":1,"idProv":2,"cantidad":1,"esUrgente":false,"precioUnidad":0.0}],
             "solicitudes":{"urgentes":[],"preventivas":[]}}""";

    private ResultActions lote(String ruta, String cuerpo, String token, String clave) throws Exception {
        var peticion = post(ruta).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (clave != null) peticion = peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion);
    }

    private void catalogo() {
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
    }

    @Test void comprasTecnicoYAdminReciben403() throws Exception {
        lote("/api/compras/lote", CUERPO_COMPRAS, tecnico(), "c1").andExpect(status().isForbidden());
        lote("/api/compras/lote", CUERPO_COMPRAS, admin(), "c2").andExpect(status().isForbidden());
    }

    @Test void comprasSupertecnicoGuardaYDevuelveLosIds() throws Exception {
        catalogo();
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41)));
        lote("/api/compras/lote", CUERPO_COMPRAS, supertecnico(), "c3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idsCreados[0]").value(41));
    }

    @Test void comprasSinClaveEs400() throws Exception {
        catalogo();
        lote("/api/compras/lote", CUERPO_COMPRAS, supertecnico(), null).andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 10: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteControllerTest,RolesCompraLoteTest'`
Expected: FAIL de compilación (`CompraLoteController` no existe).

- [ ] **Step 11: Textos del lote en `ValidacionPedidos`**

En `ValidacionPedidos.java`, tras `MSG_RESTO_CERO`:

```java
    // ── Lotes (spec 4b §4.2 y §4.4): los mismos avisos que el formulario del cliente, ahora en el servidor ──
    static final String MSG_SIN_LINEAS          = "Añade al menos una línea.";
    static final String MSG_SOLICITUD_SIN_LINEA = "La solicitud no corresponde a ninguna línea del pedido.";
    static final String L_COMPONENTE            = "selecciona un componente.";
    static final String L_CONCEPTO              = "el concepto no puede estar vacío.";
    static final String L_PROVEEDOR             = "selecciona un proveedor.";
    static final String L_COMPONENTE_OFF        = "el componente está desactivado.";
    static final String L_PROVEEDOR_OFF         = "el proveedor está desactivado.";
    static final String L_CANTIDAD              = "la cantidad debe ser mayor que 0.";
    static final String L_PRECIO                = "el precio no puede ser negativo.";
```

y, antes de `regla`:

```java
    /** "Línea {n}: {texto}" con n 1-based, como el aviso del formulario. */
    static ResponseStatusException enLinea(int numero, String texto) {
        return regla("Línea " + numero + ": " + texto);
    }
```

- [ ] **Step 12: `CompraLoteController`**

`src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import com.reparaciones.servidor.service.ConversionEur;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.reparaciones.servidor.controller.ValidacionPedidos.*;

/** Alta por lotes de los formularios "Nuevo pedido" y "Nuevo otro pedido" (spec 4b §4.4, P5): una petición, una
 *  transacción y una clave de idempotencia por guardado. Los POST sueltos de CompraController y CompraOtroController
 *  siguen para el JavaFX. Controlador propio para no tocar los constructores de los otros dos. */
@RestController
@RequestMapping("/api")
public class CompraLoteController {

    static final String OP_COMPRAS = "compras-lote";

    private final CompraLoteService servicio;
    private final ComponenteDAO componenteDao;
    private final ProveedorDAO proveedorDao;
    private final ReparacionComponenteDAO reparacionComponenteDao;
    private final SolicitudStockDAO solicitudStockDao;
    private final ConversionEur conversion;
    private final LogDAO logDao;
    private final RegistroIdempotencia idempotencia;

    public CompraLoteController(CompraLoteService servicio, ComponenteDAO componenteDao, ProveedorDAO proveedorDao,
                                ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao,
                                ConversionEur conversion, LogDAO logDao, RegistroIdempotencia idempotencia) {
        this.servicio = servicio;
        this.componenteDao = componenteDao;
        this.proveedorDao = proveedorDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
        this.conversion = conversion;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras/lote")
    public LoteCompras.Respuesta guardarLoteCompras(@RequestBody LoteCompras.Peticion req,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal,
                                                    @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                    String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        List<LineaValidada> lineas = validarLineasCompra(req.lineas());
        List<Integer> urgentes = lista(req.solicitudes() == null ? null : req.solicitudes().urgentes());
        List<Integer> preventivas = lista(req.solicitudes() == null ? null : req.solicitudes().preventivas());
        validarSolicitudes(lineas, urgentes, preventivas);
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_COMPRAS, claveIdempotencia, req,
                // La tasa (Frankfurter: lenta y externa) se resuelve aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardarCompras(resolverCompras(lineas), urgentes, preventivas),
                r -> registrarLogsCompras(lineas, urgentes, preventivas, idUsu));
    }

    // ── validación ───────────────────────────────────────────────────────────

    static void exigirClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AsignacionController.MSG_SIN_CLAVE);
        }
    }

    private record LineaValidada(LoteCompras.Linea linea, ComponenteDAO.Basico componente, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: componente, proveedor, desactivados, cantidad, precio. */
    private List<LineaValidada> validarLineasCompra(List<LoteCompras.Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) throw regla(MSG_SIN_LINEAS);
        List<LineaValidada> validadas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int n = i + 1;
            LoteCompras.Linea l = lineas.get(i);
            ComponenteDAO.Basico c = l == null || l.idCom() == null ? null : componenteDao.getBasico(l.idCom()).orElse(null);
            if (c == null) throw enLinea(n, L_COMPONENTE);
            Proveedor p = proveedor(l.idProv());
            if (p == null) throw enLinea(n, L_PROVEEDOR);
            if (!c.activo()) throw enLinea(n, L_COMPONENTE_OFF);
            if (!p.isActivo()) throw enLinea(n, L_PROVEEDOR_OFF);
            if (l.cantidad() <= 0) throw enLinea(n, L_CANTIDAD);
            if (!(l.precioUnidad() >= 0)) throw enLinea(n, L_PRECIO);
            validadas.add(new LineaValidada(l, c, p));
        }
        return validadas;
    }

    private Proveedor proveedor(Integer idProv) {
        return idProv == null ? null : proveedorDao.getById(idProv).orElse(null);
    }

    /** Cada solicitud tiene que pedir el componente (resuelto al master) de alguna línea; si no existe, tampoco casa. */
    private void validarSolicitudes(List<LineaValidada> lineas, List<Integer> urgentes, List<Integer> preventivas) {
        Set<Integer> masters = new HashSet<>();
        for (LineaValidada v : lineas) masters.add(v.componente().idMaster());
        for (Integer idRc : urgentes) {
            Integer idCom = idRc == null ? null : reparacionComponenteDao.getIdComDeSolicitud(idRc);
            if (!masters.contains(masterDe(idCom))) throw regla(MSG_SOLICITUD_SIN_LINEA);
        }
        for (Integer idSol : preventivas) {
            Integer idCom = idSol == null ? null : solicitudStockDao.getIdCom(idSol);
            if (!masters.contains(masterDe(idCom))) throw regla(MSG_SOLICITUD_SIN_LINEA);
        }
    }

    private Integer masterDe(Integer idCom) {
        if (idCom == null) return null;
        return componenteDao.getBasico(idCom).map(ComponenteDAO.Basico::idMaster).orElse(null);
    }

    private static List<Integer> lista(List<Integer> ids) {
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    // ── resolución (fuera de la transacción) ─────────────────────────────────

    /** Divisa del proveedor (nula o en blanco = EUR, el DEFAULT de la columna) y EUR con ConversionEur. */
    private List<CompraLoteService.LineaCompra> resolverCompras(List<LineaValidada> lineas) {
        List<CompraLoteService.LineaCompra> resueltas = new ArrayList<>();
        for (LineaValidada v : lineas) {
            String divisa = divisaDe(v.proveedor());
            LoteCompras.Linea l = v.linea();
            resueltas.add(new CompraLoteService.LineaCompra(v.componente().idCom(), v.proveedor().getIdProv(),
                    l.cantidad(), l.esUrgente(), l.precioUnidad(), divisa, conversion.aEuros(l.precioUnidad(), divisa)));
        }
        return resueltas;
    }

    static String divisaDe(Proveedor p) {
        String d = p.getDivisa();
        return d == null || d.isBlank() ? "EUR" : d.trim().toUpperCase();
    }

    // ── logs (trasEscribir: una sola vez, tras la transacción) ───────────────

    /** Los mismos textos que POST /api/compras y los PATCH de estado de las dos solicitudes. */
    private void registrarLogsCompras(List<LineaValidada> lineas, List<Integer> urgentes, List<Integer> preventivas,
                                      int idUsu) {
        for (LineaValidada v : lineas) {
            logDao.insertar(idUsu, "CREAR_PEDIDO", "COMPONENTE: " + v.componente().tipo()
                    + ", PROVEEDOR: " + v.proveedor().getNombre() + ", CANT: " + v.linea().cantidad());
        }
        for (Integer idRc : urgentes) {
            logDao.insertar(idUsu, "GESTIONAR_SOLICITUD", "ID_RC: " + idRc + ", ESTADO: GESTIONADA");
        }
        for (Integer idSol : preventivas) {
            logDao.insertar(idUsu, "GESTIONAR_SOLICITUD_STOCK", "ID_SOL: " + idSol + ", ESTADO: GESTIONADA");
        }
    }
}
```

- [ ] **Step 13: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteControllerTest,RolesCompraLoteTest'`
Expected: PASS, 14 tests (11 + 3).

- [ ] **Step 14: La ruta nueva declara la clave en el contrato**

En `OpenApiContractTest.lasEscriturasDelFormularioAdmitenClaveDeIdempotencia` (:274-279), el conjunto pasa a:

```java
        Set<String> conCabecera = Set.of(
                "post /api/reparaciones/completa",
                "post /api/reparaciones/{idAsignacion}/filas",
                "post /api/reparaciones/{idAsignacion}/agotar-componente",
                "put /api/reparaciones/{idRep}",
                "post /api/asignaciones/lote",
                "post /api/compras/lote");
```

(Sin esto el test falla: comprueba que ninguna otra operación declara `Idempotency-Key`.)

- [ ] **Step 15: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'`
Expected: sin fallos y `391` (369 + 22: 3 de DAO, 3 + 2 del servicio, 11 del controlador y 3 de roles).

- [ ] **Step 16: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/LoteCompras.java src/main/java/com/reparaciones/servidor/service/CompraLoteService.java src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java src/main/java/com/reparaciones/servidor/controller/ValidacionPedidos.java src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/ReparacionComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/SolicitudStockDAO.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOInsertarTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionComponenteDAOSolicitudTest.java src/test/java/com/reparaciones/servidor/dao/SolicitudStockDAOTest.java src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTest.java src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTransaccionTest.java src/test/java/com/reparaciones/servidor/controller/CompraLoteControllerTest.java src/test/java/com/reparaciones/servidor/controller/RolesCompraLoteTest.java
git commit -m "feat(compras): lote de pedidos transaccional con clave de idempotencia, 422 por linea y solicitudes gestionadas"
```

---

## Task 5: Servidor — `POST /api/compras-otros/lote` (P5): lote de otros pedidos

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/LoteComprasOtros.java`
- Modify: `src/main/java/com/reparaciones/servidor/service/CompraLoteService.java` (`LineaOtro` y `guardarOtros`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java` (endpoint, validación, resolución y logs de otros)
- Modify: `src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java` (`insertar` :55-62 devuelve el id; imports)
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (conjunto `conCabecera`)
- Create: `src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOInsertarTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTest.java`, `CompraLoteServiceTransaccionTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/CompraLoteControllerTest.java`, `RolesCompraLoteTest.java`

**Interfaces:**
- Consumes: todo lo de la Task 4 (`CompraLoteController.exigirClave`, `proveedor(Integer)`, `divisaDe(Proveedor)`, `ValidacionPedidos.enLinea` y `L_*`, `RegistroIdempotencia.ejecutar`, `ConversionEur.aEuros`).
- Produces:
  - `model.LoteComprasOtros` con los records EXACTOS de el documento de interfaces del reparto (fuera del repo): `Peticion(List<Linea> lineas)` y `Linea(@Schema(nullable = true) Integer idProv, @Schema(nullable = true) String concepto, int cantidad, boolean esUrgente, double precioUnidad)` → `LoteComprasOtrosPeticion`, `LoteComprasOtrosLinea`. La respuesta es el mismo `LoteCompras.Respuesta` (`LoteComprasRespuesta`).
  - `int CompraOtroDAO.insertar(...)`: devuelve el `ID_COMPRA_OTRO` generado; el `POST /api/compras-otros` suelto lo ignora.
  - `CompraLoteService.LineaOtro(int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur)` y `@Transactional LoteCompras.Respuesta guardarOtros(List<LineaOtro> lineas)`.
  - `POST /api/compras-otros/lote` (SUPERTECNICO, `Idempotency-Key` obligatoria → 400 `"Falta la clave de idempotencia"`), operación `"compras-otros-lote"`. 422 por línea `i` en este orden: `"Línea {i}: el concepto no puede estar vacío."` (nulo, en blanco o línea nula), `"Línea {i}: selecciona un proveedor."`, `"Línea {i}: el proveedor está desactivado."`, `"Línea {i}: la cantidad debe ser mayor que 0."`, `"Línea {i}: el precio no puede ser negativo."`; `"Añade al menos una línea."` si no hay. El concepto se guarda tal cual llega (como el POST suelto). Log `CREAR_PEDIDO_OTRO` por línea con `"CONCEPTO: {concepto}, PROVEEDOR: {nombre}, CANT: {n}"`.

- [ ] **Step 1: Test de la DAO (falla)**

`src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOInsertarTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** El lote de otros pedidos devuelve los ids creados (spec 4b §4.4). */
class CompraOtroDAOInsertarTest {

    @Test void insertaYDevuelveElIdGenerado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 51));
            return 1;
        });

        int id = new CompraOtroDAO(jdbc).insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5);

        assertEquals(51, id);
        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(contains("INSERT INTO Compra_otro"), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 2);
        verify(ps).setString(2, "Cinta de embalar");
        verify(ps).setInt(3, 3);
        verify(ps).setBoolean(4, false);
        verify(ps).setDouble(5, 1.5);
        verify(ps).setString(6, "EUR");
        verify(ps).setDouble(7, 1.5);
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest=CompraOtroDAOInsertarTest`
Expected: FAIL de compilación (`insertar` es `void`).

- [ ] **Step 3: `CompraOtroDAO.insertar` devuelve el id**

En `CompraOtroDAO.java`, imports nuevos:

```java
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
```

y `insertar` (:55-62) por:

```java
    /** Devuelve el ID_COMPRA_OTRO generado (el lote lo devuelve a la web, sub-proyecto 4b; el POST suelto lo ignora). */
    public int insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_otro" +
                    " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idProv);
            ps.setString(2, concepto);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA_OTRO del pedido insertado");
        return id.intValue();
    }
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraOtroDAOInsertarTest,CompraOtroControllerTest'`
Expected: PASS (1 + 12).

- [ ] **Step 5: Tests del servicio (fallan)**

En `CompraLoteServiceTest.java`, añadir al final de la clase:

```java
    // ── Task 5: otros pedidos ──
    private static CompraLoteService.LineaOtro otro(String concepto, int cantidad) {
        return new CompraLoteService.LineaOtro(2, concepto, cantidad, false, 1.5, "EUR", 1.5);
    }

    @Test void guardarOtrosInsertaCadaLineaEnOrdenYDevuelveSusIds() {
        when(compraOtroDao.insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5)).thenReturn(51);
        when(compraOtroDao.insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5)).thenReturn(52);

        LoteCompras.Respuesta r = servicio.guardarOtros(List.of(otro("Cinta de embalar", 3), otro("Bolsas", 1)));

        assertEquals(List.of(51, 52), r.idsCreados());
        InOrder orden = inOrder(compraOtroDao);
        orden.verify(compraOtroDao).insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5);
        orden.verify(compraOtroDao).insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5);
        verifyNoInteractions(compraDao, reparacionComponenteDao, solicitudStockDao);
    }

    @Test void guardarOtrosPropagaUnFalloEnLaSegundaLinea() {
        when(compraOtroDao.insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(51)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarOtros(List.of(otro("Cinta de embalar", 1), otro("Bolsas", 1))));
    }
```

En `CompraLoteServiceTransaccionTest.java`, añadir al final de la clase:

```java
    @Test void unFalloEnElLoteDeOtrosHaceRollbackYNuncaCommit() throws SQLException {
        when(compraOtroDao.insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(51)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class, () -> servicio.guardarOtros(List.of(
                new CompraLoteService.LineaOtro(2, "Cinta de embalar", 1, false, 0.0, "EUR", 0.0),
                new CompraLoteService.LineaOtro(2, "Bolsas", 1, false, 0.0, "EUR", 0.0))));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteServiceTest,CompraLoteServiceTransaccionTest'`
Expected: FAIL de compilación (`LineaOtro` y `guardarOtros` no existen).

- [ ] **Step 7: `LoteComprasOtros` y `guardarOtros`**

`src/main/java/com/reparaciones/servidor/model/LoteComprasOtros.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Alta por lotes del formulario "Nuevo otro pedido" (spec 4b §4.4, P5). Anidados: springdoc los publica como
 *  LoteComprasOtrosPeticion y LoteComprasOtrosLinea; la respuesta es LoteCompras.Respuesta. Sin divisa ni precioEur:
 *  la divisa es la del proveedor y el importe en euros lo calcula el servidor. */
public final class LoteComprasOtros {
    private LoteComprasOtros() {}

    public record Peticion(List<Linea> lineas) {}

    /** idProv nulo o concepto nulo/en blanco = sin rellenar en el formulario (422 "Línea i: …"). */
    public record Linea(@Schema(nullable = true) Integer idProv, @Schema(nullable = true) String concepto,
                        int cantidad, boolean esUrgente, double precioUnidad) {}
}
```

En `CompraLoteService.java`, tras el record `LineaCompra`:

```java
    public record LineaOtro(int idProv, String concepto, int cantidad, boolean esUrgente,
                            double precioUnidad, String divisa, double precioEur) {}
```

y al final de la clase:

```java
    /** Un alta por línea, en orden; sin solicitudes (los otros pedidos no tienen). */
    @Transactional
    public LoteCompras.Respuesta guardarOtros(List<LineaOtro> lineas) {
        List<Integer> ids = new ArrayList<>();
        for (LineaOtro l : lineas) {
            ids.add(compraOtroDao.insertar(l.idProv(), l.concepto(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur()));
        }
        return new LoteCompras.Respuesta(ids);
    }
```

y quitar el comentario `// lo usa guardarOtros (Task 5)` del campo `compraOtroDao`.

- [ ] **Step 8: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteServiceTest,CompraLoteServiceTransaccionTest'`
Expected: PASS, 8 tests (5 + 3).

- [ ] **Step 9: Tests del controlador y de roles (fallan)**

En `CompraLoteControllerTest.java`: import `com.reparaciones.servidor.model.LoteComprasOtros`; al final del constructor `CompraLoteControllerTest()`:

```java
        when(servicio.guardarOtros(anyList())).thenReturn(new LoteCompras.Respuesta(List.of(51)));
```

y al final de la clase:

```java
    // ── Task 5: POST /api/compras-otros/lote ──
    private static final String CINTA = "Cinta de embalar";

    private static LoteComprasOtros.Linea otro(Integer idProv, String concepto, int cantidad, double precio) {
        return new LoteComprasOtros.Linea(idProv, concepto, cantidad, false, precio);
    }

    private static LoteComprasOtros.Peticion loteOtros(LoteComprasOtros.Linea... lineas) {
        return new LoteComprasOtros.Peticion(Arrays.asList(lineas));
    }

    private String falla422Otros(LoteComprasOtros.Peticion peticion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteOtros(peticion, super7, CLAVE));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    @Test void otrosSinClaveEs400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteOtros(loteOtros(otro(2, CINTA, 1, 0.0)), super7, null));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Falta la clave de idempotencia", e.getReason());
        nadaGuardadoNiRegistrado();
    }

    @Test void otrosSinLineasEs422() {
        assertEquals("Añade al menos una línea.", falla422Otros(loteOtros()));
        assertEquals("Añade al menos una línea.", falla422Otros(new LoteComprasOtros.Peticion(null)));
        nadaGuardadoNiRegistrado();
    }

    @Test void otrosLineaSinConceptoEs422ConSuNumero() {
        assertEquals("Línea 2: el concepto no puede estar vacío.",
                falla422Otros(loteOtros(otro(2, CINTA, 1, 0.0), otro(2, null, 1, 0.0))));
        assertEquals("Línea 2: el concepto no puede estar vacío.",
                falla422Otros(loteOtros(otro(2, CINTA, 1, 0.0), otro(2, "   ", 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void otrosLineaSinProveedorOConUnoInexistenteEs422() {
        assertEquals("Línea 1: selecciona un proveedor.", falla422Otros(loteOtros(otro(null, CINTA, 1, 0.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422Otros(loteOtros(otro(99, CINTA, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    /** Orden de la spec: concepto, proveedor, proveedor desactivado, cantidad, precio. */
    @Test void otrosLosErroresDeUnaLineaSalenEnElOrdenDeLaSpec() {
        assertEquals("Línea 1: el concepto no puede estar vacío.", falla422Otros(loteOtros(otro(null, "", 0, -1.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422Otros(loteOtros(otro(null, CINTA, 0, -1.0))));
        assertEquals("Línea 1: el proveedor está desactivado.", falla422Otros(loteOtros(otro(6, CINTA, 0, -1.0))));
        assertEquals("Línea 1: la cantidad debe ser mayor que 0.", falla422Otros(loteOtros(otro(2, CINTA, 0, -1.0))));
        assertEquals("Línea 1: el precio no puede ser negativo.", falla422Otros(loteOtros(otro(2, CINTA, 1, -1.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void otrosLaDivisaEsLaDelProveedorYElEurSeCalculaAntesDelServicio() {
        ctl.guardarLoteOtros(loteOtros(otro(3, CINTA, 2, 10.0), otro(2, "Bolsas", 1, 1.5)), super7, CLAVE);
        verify(servicio).guardarOtros(eq(List.of(
                new CompraLoteService.LineaOtro(3, CINTA, 2, false, 10.0, "USD", 8.8),
                new CompraLoteService.LineaOtro(2, "Bolsas", 1, false, 1.5, "EUR", 1.5))));
    }

    @Test void otrosLaMismaClaveYLaMismaPeticionDevuelvenLaRespuestaGuardada() {
        LoteCompras.Respuesta primera = ctl.guardarLoteOtros(loteOtros(otro(2, CINTA, 1, 0.0)), super7, CLAVE);
        LoteCompras.Respuesta segunda = ctl.guardarLoteOtros(loteOtros(otro(2, CINTA, 1, 0.0)), super7, CLAVE);
        assertSame(primera, segunda);
        assertEquals(List.of(51), segunda.idsCreados());
        verify(servicio, times(1)).guardarOtros(anyList());
        verify(logDao, times(1)).insertar(eq(7), eq("CREAR_PEDIDO_OTRO"), anyString());
    }

    @Test void otrosRegistraUnLogPorLinea() {
        when(servicio.guardarOtros(anyList())).thenReturn(new LoteCompras.Respuesta(List.of(51, 52)));
        ctl.guardarLoteOtros(loteOtros(otro(2, CINTA, 3, 0.0), otro(3, "Bolsas", 1, 10.0)), super7, CLAVE);
        verify(logDao).insertar(7, "CREAR_PEDIDO_OTRO", "CONCEPTO: Cinta de embalar, PROVEEDOR: Proveedor A, CANT: 3");
        verify(logDao).insertar(7, "CREAR_PEDIDO_OTRO", "CONCEPTO: Bolsas, PROVEEDOR: Proveedor B, CANT: 1");
        verifyNoMoreInteractions(logDao);
    }

    @Test void otrosSinTasaEs503SinLlamarAlServicio() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteOtros(loteOtros(otro(3, CINTA, 1, 10.0)), super7, CLAVE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(servicio, logDao);
    }
```

En `RolesCompraLoteTest.java`, tras `CUERPO_COMPRAS`:

```java
    private static final String CUERPO_OTROS = """
            {"lineas":[{"idProv":2,"concepto":"Cinta de embalar","cantidad":1,"esUrgente":false,"precioUnidad":0.0}]}""";
```

y al final de la clase:

```java
    @Test void otrosTecnicoYAdminReciben403() throws Exception {
        lote("/api/compras-otros/lote", CUERPO_OTROS, tecnico(), "o1").andExpect(status().isForbidden());
        lote("/api/compras-otros/lote", CUERPO_OTROS, admin(), "o2").andExpect(status().isForbidden());
    }

    @Test void otrosSupertecnicoGuardaYDevuelveLosIds() throws Exception {
        catalogo();
        when(servicio.guardarOtros(anyList())).thenReturn(new LoteCompras.Respuesta(List.of(51)));
        lote("/api/compras-otros/lote", CUERPO_OTROS, supertecnico(), "o3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idsCreados[0]").value(51));
    }

    @Test void otrosSinClaveEs400() throws Exception {
        catalogo();
        lote("/api/compras-otros/lote", CUERPO_OTROS, supertecnico(), null).andExpect(status().isBadRequest());
    }
```

- [ ] **Step 10: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteControllerTest,RolesCompraLoteTest'`
Expected: FAIL de compilación (`guardarLoteOtros` no existe).

- [ ] **Step 11: El endpoint de otros en `CompraLoteController`**

En `CompraLoteController.java`: import `com.reparaciones.servidor.model.LoteComprasOtros`; junto a `OP_COMPRAS`:

```java
    static final String OP_OTROS   = "compras-otros-lote";
```

y, tras `guardarLoteCompras`:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras-otros/lote")
    public LoteCompras.Respuesta guardarLoteOtros(@RequestBody LoteComprasOtros.Peticion req,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal,
                                                  @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                  String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        List<OtroValidado> lineas = validarLineasOtro(req.lineas());
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_OTROS, claveIdempotencia, req,
                () -> servicio.guardarOtros(resolverOtros(lineas)),
                r -> registrarLogsOtros(lineas, idUsu));
    }

    private record OtroValidado(LoteComprasOtros.Linea linea, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: concepto, proveedor, proveedor desactivado, cantidad, precio. */
    private List<OtroValidado> validarLineasOtro(List<LoteComprasOtros.Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) throw regla(MSG_SIN_LINEAS);
        List<OtroValidado> validadas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int n = i + 1;
            LoteComprasOtros.Linea l = lineas.get(i);
            if (l == null || l.concepto() == null || l.concepto().isBlank()) throw enLinea(n, L_CONCEPTO);
            Proveedor p = proveedor(l.idProv());
            if (p == null) throw enLinea(n, L_PROVEEDOR);
            if (!p.isActivo()) throw enLinea(n, L_PROVEEDOR_OFF);
            if (l.cantidad() <= 0) throw enLinea(n, L_CANTIDAD);
            if (!(l.precioUnidad() >= 0)) throw enLinea(n, L_PRECIO);
            validadas.add(new OtroValidado(l, p));
        }
        return validadas;
    }

    private List<CompraLoteService.LineaOtro> resolverOtros(List<OtroValidado> lineas) {
        List<CompraLoteService.LineaOtro> resueltas = new ArrayList<>();
        for (OtroValidado v : lineas) {
            String divisa = divisaDe(v.proveedor());
            LoteComprasOtros.Linea l = v.linea();
            resueltas.add(new CompraLoteService.LineaOtro(v.proveedor().getIdProv(), l.concepto(), l.cantidad(),
                    l.esUrgente(), l.precioUnidad(), divisa, conversion.aEuros(l.precioUnidad(), divisa)));
        }
        return resueltas;
    }

    /** El mismo texto que POST /api/compras-otros. */
    private void registrarLogsOtros(List<OtroValidado> lineas, int idUsu) {
        for (OtroValidado v : lineas) {
            logDao.insertar(idUsu, "CREAR_PEDIDO_OTRO", "CONCEPTO: " + v.linea().concepto()
                    + ", PROVEEDOR: " + v.proveedor().getNombre() + ", CANT: " + v.linea().cantidad());
        }
    }
```

- [ ] **Step 12: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='CompraLoteControllerTest,RolesCompraLoteTest'`
Expected: PASS, 26 tests (20 + 6).

- [ ] **Step 13: La ruta nueva declara la clave en el contrato**

En `OpenApiContractTest.lasEscriturasDelFormularioAdmitenClaveDeIdempotencia`, añadir `"post /api/compras-otros/lote"` al conjunto:

```java
        Set<String> conCabecera = Set.of(
                "post /api/reparaciones/completa",
                "post /api/reparaciones/{idAsignacion}/filas",
                "post /api/reparaciones/{idAsignacion}/agotar-componente",
                "put /api/reparaciones/{idRep}",
                "post /api/asignaciones/lote",
                "post /api/compras/lote",
                "post /api/compras-otros/lote");
```

- [ ] **Step 14: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'`
Expected: sin fallos y `407` (391 + 16: 1 de DAO, 2 + 1 del servicio, 9 del controlador y 3 de roles).

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/LoteComprasOtros.java src/main/java/com/reparaciones/servidor/service/CompraLoteService.java src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java src/test/java/com/reparaciones/servidor/dao/CompraOtroDAOInsertarTest.java src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTest.java src/test/java/com/reparaciones/servidor/service/CompraLoteServiceTransaccionTest.java src/test/java/com/reparaciones/servidor/controller/CompraLoteControllerTest.java src/test/java/com/reparaciones/servidor/controller/RolesCompraLoteTest.java
git commit -m "feat(compras): lote de otros pedidos transaccional con clave de idempotencia y 422 por linea"
```

---

## Task 6: Servidor — contrato: nulos de pedidos, `precioEur` ignorado, lotes en `OpenApiContractTest` y `target/openapi.json`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/model/CompraComponente.java:12,15` (`cantidadRecibida`, `fechaLlegada`)
- Modify: `src/main/java/com/reparaciones/servidor/model/CompraOtro.java:11,14` (`cantidadRecibida`, `fechaLlegada`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraController.java` (records `InsertarRequest` y `EditarRequest`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java` (records `InsertarRequest` y `EditarRequest`)
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (test nuevo `elContratoPublicaLosLotesYLosNulosDePedidos`)

**Interfaces:**
- Consumes: los helpers de `OpenApiContractTest` (`refDelCuerpo` :331-342, `refDeLaRespuesta` :345-356, `assertNullable` :358-364, `assertNoNullable` :366-374, `nombres` :376-380); `OpenApiConfig.todasLasPropiedadesRequeridas` (todas las propiedades `required`, la nulabilidad va campo a campo).
- Produces (contrato; ninguna respuesta del JavaFX cambia de forma):
  - `CompraComponente` y `CompraOtro`: `cantidadRecibida` (`integer`) y `fechaLlegada` (`string date-time`) con `nullable: true` → en la web `number | null` y `string | null`.
  - `CompraInsertarRequest`, `CompraEditarRequest`, `CompraOtroInsertarRequest`, `CompraOtroEditarRequest`: `precioEur` pasa a `Double` con `@Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")` → `number | null` en la web, que manda `null`. El JavaFX sigue mandando un número y se ignora (Task 3).
  - Rutas `POST /api/compras/lote` (cuerpo `LoteComprasPeticion`, 200 `LoteComprasRespuesta`) y `POST /api/compras-otros/lote` (cuerpo `LoteComprasOtrosPeticion`, 200 `LoteComprasRespuesta`), las dos con `Idempotency-Key` de cabecera opcional (Tasks 4 y 5); esquemas `LoteComprasPeticion`, `LoteComprasLinea`, `LoteComprasSolicitudes`, `LoteComprasRespuesta`, `LoteComprasOtrosPeticion`, `LoteComprasOtrosLinea`, con `idCom`/`idProv`/`concepto` de las líneas `nullable`.
  - `target/openapi.json` (lo escribe `elContratoPublicaLosEsquemasDeLaWeb`, :253-256, con los códigos de respuesta ordenados) listo para que la web lo copie a `api/openapi.json` (Task 7).

- [ ] **Step 1: Test del contrato (falla)**

En `OpenApiContractTest.java`, tras `elContratoPublicaLosEsquemasDeLaWeb` (:257):

```java
    /**
     * Sub-proyecto 4b (spec §4.5): los dos lotes de pedidos con sus esquemas, los nulos que el servidor ya mandaba en
     * los pedidos (el tipo generado mentía: inventario de Pedidos §22) y el precioEur ignorado de las cuatro peticiones.
     */
    @Test void elContratoPublicaLosLotesYLosNulosDePedidos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/compras/lote", "/api/compras-otros/lote")) {
            assertTrue(paths.path(ruta).has("post"), () -> "falta POST " + ruta + " en el contrato");
        }
        for (String esquema : List.of("LoteComprasPeticion", "LoteComprasLinea", "LoteComprasSolicitudes",
                "LoteComprasRespuesta", "LoteComprasOtrosPeticion", "LoteComprasOtrosLinea")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }
        assertTrue(refDelCuerpo(paths, "/api/compras/lote", "post").endsWith("/LoteComprasPeticion"));
        assertTrue(refDelCuerpo(paths, "/api/compras-otros/lote", "post").endsWith("/LoteComprasOtrosPeticion"));
        assertTrue(refDeLaRespuesta(paths, "/api/compras/lote", "post", "200").endsWith("/LoteComprasRespuesta"));
        assertTrue(refDeLaRespuesta(paths, "/api/compras-otros/lote", "post", "200").endsWith("/LoteComprasRespuesta"));

        assertNullable(esquemas, "LoteComprasLinea", "idCom", "idProv");
        assertNoNullable(esquemas, "LoteComprasLinea", "cantidad", "esUrgente", "precioUnidad");
        assertNoNullable(esquemas, "LoteComprasPeticion", "lineas", "solicitudes");
        assertNoNullable(esquemas, "LoteComprasSolicitudes", "urgentes", "preventivas");
        assertNoNullable(esquemas, "LoteComprasRespuesta", "idsCreados");
        assertEquals("integer", esquemas.path("LoteComprasRespuesta").path("properties").path("idsCreados")
                .path("items").path("type").asText(), "idsCreados debe ser una lista de enteros");
        assertNullable(esquemas, "LoteComprasOtrosLinea", "idProv", "concepto");
        assertNoNullable(esquemas, "LoteComprasOtrosLinea", "cantidad", "esUrgente", "precioUnidad");
        assertNoNullable(esquemas, "LoteComprasOtrosPeticion", "lineas");

        assertNullable(esquemas, "CompraComponente", "cantidadRecibida", "fechaLlegada");
        assertNullable(esquemas, "CompraOtro", "cantidadRecibida", "fechaLlegada");
        assertNoNullable(esquemas, "CompraComponente", "idCompra", "cantidad", "estado", "precioEur", "updatedAt");
        assertNoNullable(esquemas, "CompraOtro", "idCompraOtro", "concepto", "cantidad", "estado", "precioEur");

        for (String peticion : List.of("CompraInsertarRequest", "CompraEditarRequest",
                "CompraOtroInsertarRequest", "CompraOtroEditarRequest")) {
            assertNullable(esquemas, peticion, "precioEur");
            assertEquals("number", esquemas.path(peticion).path("properties").path("precioEur").path("type").asText(),
                    () -> peticion + ".precioEur debe seguir siendo number");
        }
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest=OpenApiContractTest`
Expected: FAIL en `elContratoPublicaLosLotesYLosNulosDePedidos` con `CompraComponente.cantidadRecibida debe ser nullable` (rutas y esquemas de lote ya existen desde las Tasks 4 y 5; los otros dos tests de la clase siguen en verde).

- [ ] **Step 3: Nulos en los modelos**

En `CompraComponente.java`, import `io.swagger.v3.oas.annotations.media.Schema;` y las líneas :12 y :15 por:

```java
    @Schema(nullable = true) private Integer       cantidadRecibida;
```

```java
    @Schema(nullable = true) private LocalDateTime fechaLlegada;
```

En `CompraOtro.java`, el mismo import y las líneas :11 y :14 por:

```java
    @Schema(nullable = true) private Integer       cantidadRecibida;
```

```java
    @Schema(nullable = true) private LocalDateTime fechaLlegada;
```

(Mismo patrón que `Componente.ultimoPedido`, `Componente.java:16`.)

- [ ] **Step 4: `precioEur` ignorado y nullable en las cuatro peticiones**

En `CompraController.java`, import `io.swagger.v3.oas.annotations.media.Schema;` y los dos primeros records por:

```java
    record InsertarRequest(int idCom, int idProv, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa,
                           @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                           Double precioEur) {}
    record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa,
                         @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                         Double precioEur,
                         LocalDateTime updatedAt) {}
```

En `CompraOtroController.java`, el mismo import y los dos primeros records por:

```java
    record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa,
                           @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                           Double precioEur) {}
    record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa,
                         @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                         Double precioEur,
                         LocalDateTime updatedAt) {}
```

Desde la Task 3 ningún código de producción lee `req.precioEur()`: comprobarlo con `grep -rn "req.precioEur()" src/main/java/com/reparaciones/servidor/controller | grep -v "//"` (no debe salir ninguna línea: sin el `grep -v` salen solo las dos líneas de comentario `// P3: …` que añade la Task 3 en `CompraController` y `CompraOtroController`; los `l.precioEur()` de `CompraLoteService` están en `service/` y son de otro record). Los tests que construyen estas peticiones con un `double` siguen compilando por autoboxing.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='OpenApiContractTest,CompraControllerTest,CompraOtroControllerTest'`
Expected: PASS (4 + 18 + 12).

- [ ] **Step 6: Suite completa y contrato para la web**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[ ,]+' '{s+=$3} END {print s}'; grep -c '"LoteComprasPeticion"\|"LoteComprasOtrosPeticion"\|"/api/compras/lote"\|"/api/compras-otros/lote"' target/openapi.json`
Expected: sin fallos; el recuento imprime `408` (407 + 1); el `grep -c` imprime un número mayor que 0 (`target/openapi.json` regenerado con las rutas y esquemas nuevos). Comprobar además a ojo el bloque de `CompraComponente` en `target/openapi.json`: `cantidadRecibida` y `fechaLlegada` con `"nullable" : true`. Ese fichero es el que la Task 7 copia a `gestion-reparaciones-web/api/openapi.json`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/CompraComponente.java src/main/java/com/reparaciones/servidor/model/CompraOtro.java src/main/java/com/reparaciones/servidor/controller/CompraController.java src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat(contrato): nulos de pedidos, precioEur ignorado y lotes de pedidos en el contrato openapi"
```

(Ni `push` ni `merge`: los pide el usuario al cierre, Task 21.)

---

---

## Task 7: Web — rama, contrato regenerado, tipos de compras, importes, patrón de fecha de Pedidos, claves de idempotencia en `shared` y tokens

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados por el flujo offline)
- Modify: `src/shared/api/client.ts` (alias `CompraComponente` y `CompraOtro` tras `Proveedor`, l.17-18; `onResponse` l.64-79, 503 de negocio y banner en el Step 15), `src/shared/api/client.test.ts`
- Create: `src/shared/lib/importes.ts`, `src/shared/lib/importes.test.ts`
- Modify: `src/shared/lib/fechas.ts` (`Patron` l.4, constante tras `FMT_FECHA_ASIGNACION` l.8, `Partes` l.20, `partesMadrid` l.22-28, regex de `formatear` l.42), `src/shared/lib/fechas.test.ts`
- Move: `src/modules/taller/formulario/clavesIdempotencia.ts` → `src/shared/lib/clavesIdempotencia.ts` (y su `.test.ts`)
- Modify: `src/modules/taller/formulario/useGuardado.ts:6`, `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.tsx:10` (imports)
- Modify: `src/shared/styles/tokens.css` (final del bloque `@theme`, tras `--color-badge-sin-stock-bg` l.137)

**Interfaces:**
- Consumes: `target/openapi.json` de `gestion-reparaciones-servidor` en la rama `feature/web-pedidos` con las Tasks 1-6 hechas.
- Produces:
  - `export type CompraComponente = components['schemas']['CompraComponente']`, `export type CompraOtro = components['schemas']['CompraOtro']` en `@/shared/api/client`.
  - `@/shared/lib/importes`: `formatearNumero(n: number, decimales = 2): string`, `formatearImporte(n: number, simbolo: string): string`, `simboloDivisa(divisa: string): string`, `simboloFormulario(divisa: string): string`, `parsearDecimal(texto: string): number | null`, `parsearEntero(texto: string): number | null`.
  - `@/shared/lib/fechas`: `Patron` gana `'dd/MM/yy HH:mm'`; `export const FMT_FECHA_PEDIDO: Patron = 'dd/MM/yy HH:mm'`.
  - `@/shared/lib/clavesIdempotencia`: `crearClavesIdempotencia`, `ClavesIdempotencia`, `Operacion` (mismo contenido).
  - Tokens: `--color-fila-pendiente-brd: #C8961E`, `--color-badge-pendiente-bg: #FFF3D6`, `--color-badge-pendiente-text: #B26A00` (utilidades `border-l-fila-pendiente-brd`, `bg-badge-pendiente-bg`, `text-badge-pendiente-text`).

> Búsqueda previa: `grep -rn "Intl.NumberFormat\|toLocaleString" src --include=*.ts --include=*.tsx | grep -v test` sale vacío (inventario §15: la web no tiene formato numérico con coma) y `src/shared/lib/importes.ts` no existe. `clavesIdempotencia` solo lo importan `useGuardado.ts:6`, `AsignarTrabajosDialog.tsx:10` y su test.

- [ ] **Step 1: Rama**

```bash
cd gestion-reparaciones-web
git checkout main && git pull --ff-only
git checkout -b feature/web-pedidos
```

- [ ] **Step 2: Contrato de la rama del servidor**

Flujo offline del README de la web (l.16-21), igual que la Task 4 del plan 4a: el `OpenApiContractTest` del servidor deja el contrato en `target/openapi.json`.

```bash
# en gestion-reparaciones-servidor, rama feature/web-pedidos con las Tasks 1-6 hechas (Maven en Bash, ver Global Constraints)
mvn -q test -Dtest=OpenApiContractTest
cp target/openapi.json ../gestion-reparaciones-web/api/openapi.json
# en gestion-reparaciones-web
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
grep -n -A30 '^        CompraComponente: {' src/shared/api/schema.d.ts | grep -n 'cantidadRecibida\|fechaLlegada'
grep -n -A30 '^        CompraOtro: {' src/shared/api/schema.d.ts | grep -n 'cantidadRecibida\|fechaLlegada'
grep -n -A14 '^        CompraEditarRequest: {\|^        CompraOtroEditarRequest: {' src/shared/api/schema.d.ts | grep precioEur
grep -n '"/api/compras/lote"\|"/api/compras-otros/lote"' src/shared/api/schema.d.ts
grep -n '^        LoteCompras[A-Za-z]*: {' src/shared/api/schema.d.ts
```

Expected:
- `CompraComponente` y `CompraOtro`: `cantidadRecibida: number | null;` y `fechaLlegada: string | null;` (antes `number` y `string`, `schema.d.ts:3041-3091`).
- `CompraEditarRequest`, `CompraOtroEditarRequest` (y los dos `InsertarRequest`): `precioEur: number | null;`. Si el generador lo deja opcional (`precioEur?: number | null`), la Task 10 no lo manda y se anota.
- Rutas `"/api/compras/lote"` y `"/api/compras-otros/lote"` con `post: operations[...]` cuyo `parameters.header` es `{ "Idempotency-Key"?: string }` (como `guardarLote`, `schema.d.ts:4556-4562`).
- Esquemas `LoteComprasPeticion`, `LoteComprasLinea`, `LoteComprasSolicitudes`, `LoteComprasRespuesta`, `LoteComprasOtrosPeticion`, `LoteComprasOtrosLinea`. Si los nombres difieren, manda el contrato: se anotan y se usan en la Task 10.

Cualquier otro cambio del diff viene de `main` del servidor posterior al último `api:types` y se revisa antes de seguir. Las rutas de "otros" usan `{id}` como parámetro de ruta (`/api/compras-otros/{id}/…`) y las de componentes `{idCompra}`: la Task 10 se escribe contra eso.

- [ ] **Step 3: Alias del contrato**

En `src/shared/api/client.ts`, tras el alias `Proveedor` (l.17-18):

```ts
/** Pedidos (sub-proyecto 4b): pedidos de componentes y "otros pedidos". `cantidadRecibida` y `fechaLlegada` vienen a null
 *  hasta la recepción (nullables del contrato desde la Task 6 del servidor). */
export type CompraComponente = components['schemas']['CompraComponente']
export type CompraOtro = components['schemas']['CompraOtro']
```

- [ ] **Step 4: Test de importes (falla)**

`src/shared/lib/importes.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { formatearImporte, formatearNumero, parsearDecimal, parsearEntero, simboloDivisa, simboloFormulario } from './importes'

/** Calco de `String.format("%.2f …")` con el Locale por defecto de un Windows en español (inventario de Pedidos §15):
 *  coma decimal y sin separador de miles. Los campos de edición aceptan coma o punto (`replace(",", ".")`). */
describe('importes', () => {
  it('formatearNumero: coma decimal, sin separador de miles y 2 decimales por defecto', () => {
    expect(formatearNumero(12.5)).toBe('12,50')
    expect(formatearNumero(0)).toBe('0,00')
    expect(formatearNumero(1234567.891)).toBe('1234567,89')
    expect(formatearNumero(-3)).toBe('-3,00')
    expect(formatearNumero(1 / 1.1367, 4)).toBe('0,8797')
  })
  it('formatearImporte: el número y el símbolo separados por un espacio', () => {
    expect(formatearImporte(12.5, '€')).toBe('12,50 €')
    expect(formatearImporte(10, '$')).toBe('10,00 $')
  })
  it('simboloDivisa (tabla de Pedidos): EUR → €, USD → $, cualquier otra → el propio código', () => {
    expect(simboloDivisa('EUR')).toBe('€')
    expect(simboloDivisa('USD')).toBe('$')
    expect(simboloDivisa('GBP')).toBe('GBP')
  })
  it('simboloFormulario (celda P.Unit. de los formularios de alta): USD → $, cualquier otra → €', () => {
    expect(simboloFormulario('USD')).toBe('$')
    expect(simboloFormulario('EUR')).toBe('€')
    expect(simboloFormulario('GBP')).toBe('€')
  })
  it('parsearDecimal acepta coma o punto y devuelve null si no es un número', () => {
    expect(parsearDecimal('12,5')).toBe(12.5)
    expect(parsearDecimal(' 12.50 ')).toBe(12.5)
    expect(parsearDecimal('0')).toBe(0)
    expect(parsearDecimal('-1,5')).toBe(-1.5)
    expect(parsearDecimal('abc')).toBeNull()
    expect(parsearDecimal('')).toBeNull()
    expect(parsearDecimal('1,2,3')).toBeNull()
  })
  it('parsearEntero: solo dígitos tras recortar', () => {
    expect(parsearEntero('3')).toBe(3)
    expect(parsearEntero(' 12 ')).toBe(12)
    expect(parsearEntero('3.5')).toBeNull()
    expect(parsearEntero('-1')).toBeNull()
    expect(parsearEntero('')).toBeNull()
    expect(parsearEntero('abc')).toBeNull()
  })
})
```

- [ ] **Step 5: Ejecutar y ver que falla**

Run: `npx vitest run src/shared/lib/importes.test.ts`
Expected: FAIL, `Failed to resolve import "./importes"`.

- [ ] **Step 6: Implementar**

`src/shared/lib/importes.ts`:

```ts
/** Importes y cantidades con el formato del JavaFX (inventario de Pedidos §15): `String.format("%.2f")` con el Locale de un
 *  Windows en español, es decir, coma decimal y sin separador de miles. Un formateador por número de decimales. */
const formatos = new Map<number, Intl.NumberFormat>()

function formato(decimales: number): Intl.NumberFormat {
  let f = formatos.get(decimales)
  if (!f) {
    f = new Intl.NumberFormat('es-ES', { minimumFractionDigits: decimales, maximumFractionDigits: decimales, useGrouping: false })
    formatos.set(decimales, f)
  }
  return f
}

/** '12,50' (2 decimales por defecto; la tasa del editor usa 4). */
export function formatearNumero(n: number, decimales = 2): string {
  return formato(decimales).format(n)
}

/** '12,50 €': calco de `"%.2f %s"`. */
export function formatearImporte(n: number, simbolo: string): string {
  return `${formatearNumero(n)} ${simbolo}`
}

/** Símbolo de la columna P.Unit. de las tablas de Pedidos (StockController :810-814): EUR → €, USD → $, otra → el código. */
export function simboloDivisa(divisa: string): string {
  if (divisa === 'EUR') return '€'
  if (divisa === 'USD') return '$'
  return divisa
}

/** Símbolo de la celda P.Unit. de los formularios de alta (inventario §10): USD → $, cualquier otra → €. */
export function simboloFormulario(divisa: string): string {
  return divisa === 'USD' ? '$' : '€'
}

const DECIMAL = /^[+-]?(\d+(\.\d*)?|\.\d+)$/
const ENTERO = /^\d+$/

/** Calco de `Double.parseDouble(texto.trim().replace(",", "."))`: coma o punto; null si no es un número finito. */
export function parsearDecimal(texto: string): number | null {
  const t = texto.trim().replace(/,/g, '.')
  if (!DECIMAL.test(t)) return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

/** Entero sin signo: solo dígitos tras recortar (las cantidades de los formularios de pedido). */
export function parsearEntero(texto: string): number | null {
  const t = texto.trim()
  return ENTERO.test(t) ? Number(t) : null
}
```

- [ ] **Step 7: Ejecutar y ver que pasa**

Run: `npx vitest run src/shared/lib/importes.test.ts`
Expected: PASS, 6 tests.

- [ ] **Step 8: Test del patrón de Pedidos (falla)**

En `src/shared/lib/fechas.test.ts`, cambiar el import de la l.2 por:

```ts
import { FMT_FECHA_PEDIDO, fechaLocal, formatear, horaLocal, hoyMadrid, marcaFichero, parsearUtc } from './fechas'
```

y añadir al final del `describe` (antes del `})` de la l.31):

```ts
  it('patrón de la columna Pedido (dd/MM/yy HH:mm, StockController FMT :153): dos cifras del año, sin romper yyyy', () => {
    expect(FMT_FECHA_PEDIDO).toBe('dd/MM/yy HH:mm')
    expect(formatear('2026-08-28T08:42:00', FMT_FECHA_PEDIDO)).toBe('28/08/26 10:42')
    // 23:30 UTC del 31/12 = 00:30 del 1/1 en Madrid (CET): cambian el día, el mes y las dos cifras del año.
    expect(formatear('2026-12-31T23:30:00', FMT_FECHA_PEDIDO)).toBe('01/01/27 00:30')
    expect(formatear('2026-08-28T08:42:00', 'dd/MM/yyyy HH:mm')).toBe('28/08/2026 10:42')
  })
```

- [ ] **Step 9: Ejecutar y ver que falla**

Run: `npx vitest run src/shared/lib/fechas.test.ts`
Expected: FAIL: `FMT_FECHA_PEDIDO` es `undefined` y `formatear(…, 'dd/MM/yy HH:mm')` deja `yy` sin sustituir.

- [ ] **Step 10: Implementar**

En `src/shared/lib/fechas.ts`:

Sustituir la l.4 por:

```ts
export type Patron = 'yyyy/MM/dd HH:mm' | 'yyyy/MM/dd' | 'dd/MM HH:mm' | 'dd/MM' | 'HH:mm' | 'dd/MM/yyyy' | 'dd/MM/yyyy HH:mm' | 'dd/MM/yy HH:mm'
```

Tras `FMT_FECHA_ASIGNACION` (l.8):

```ts

/** Patrón de la columna "Pedido" de las dos tablas de Pedidos (StockController `FMT` :153). */
export const FMT_FECHA_PEDIDO: Patron = 'dd/MM/yy HH:mm'
```

Sustituir `Partes` y `partesMadrid` (l.20-28) por:

```ts
type Partes = Record<'yyyy' | 'yy' | 'MM' | 'dd' | 'HH' | 'mm', string>

function partesMadrid(d: Date): Partes {
  const p: Record<string, string> = {}
  for (const parte of FMT.formatToParts(d)) p[parte.type] = parte.value
  // Algunos motores devuelven "24" a medianoche con hour12: false
  const HH = p.hour === '24' ? '00' : p.hour
  return { yyyy: p.year, yy: p.year.slice(-2), MM: p.month, dd: p.day, HH, mm: p.minute }
}
```

Y en `formatear` (l.42), la regex (`yyyy` antes que `yy` para que la alternancia no parta el año de cuatro cifras):

```ts
  return patron.replace(/yyyy|yy|MM|dd|HH|mm/g, (t) => p[t as keyof Partes])
```

- [ ] **Step 11: Ejecutar y ver que pasa**

Run: `npx vitest run src/shared/lib/fechas.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 12: Mover `clavesIdempotencia` a `shared/lib` (P9)**

```bash
git mv src/modules/taller/formulario/clavesIdempotencia.ts src/shared/lib/clavesIdempotencia.ts
git mv src/modules/taller/formulario/clavesIdempotencia.test.ts src/shared/lib/clavesIdempotencia.test.ts
```

- `src/modules/taller/formulario/useGuardado.ts:6`: `import { crearClavesIdempotencia } from './clavesIdempotencia'` → `import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'`.
- `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.tsx:10`: `import { crearClavesIdempotencia } from '../../formulario/clavesIdempotencia'` → `import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'`.
- El test movido importa `./clavesIdempotencia`, que sigue valiendo. El `import { claveUnica } from '@/shared/lib/claveUnica'` del fichero movido también.
- En `src/shared/lib/clavesIdempotencia.ts`, sustituir el comentario de `Operacion` (l.3-4) por:

```ts
/** Formulario de reparación: 'fila:<prefijo>' | 'accion:<id>' | 'agotar:<prefijo>' | 'completa' | 'editarAccion' |
 *  'editarFila' | 'completaFilas' | 'completaAcciones'. Formularios de pedido (sub-proyecto 4b): 'compras:lote' |
 *  'compras-otros:lote'. Vive en shared desde el 4b porque lo usan el taller y el almacén (spec 4b, P9). */
```

Run: `npx vitest run src/shared/lib/clavesIdempotencia.test.ts src/modules/taller/formulario src/modules/taller/asignaciones/modal`
Expected: PASS, los mismos tests que antes del movimiento (ninguno nuevo ni perdido).

Comprobar que no queda ningún import al sitio viejo:

```bash
grep -rn "formulario/clavesIdempotencia\|'./clavesIdempotencia'" src --include=*.ts --include=*.tsx
```

Expected: solo `src/shared/lib/clavesIdempotencia.test.ts:2`.

- [ ] **Step 13: Test del 503 con mensaje (falla)**

Un 503 con `message` solo lo emite nuestro backend (T3: `TipoCambioDAO` cuando Frankfurter falla, spec §4.3 y §8); los 503 de nginx llegan sin cuerpo JSON (vacíos, o con texto plano o una página HTML). Hoy `clasificar` (`src/shared/api/errors.ts:59`) convierte cualquier 5xx en `ConexionError` y el `MutationCache` abre "Sin conexión con el servidor: HTTP 503" aunque la mutación esté silenciada (`src/shared/api/queryClient.ts:65-72`), así que el mensaje de la tasa nunca llegaría a la línea de error de los formularios (T15, T17). Además `client.ts` llama a `reportarFallo()` para todo `status >= 500` antes de clasificar, así que el banner "Sin conexión" se encendería igualmente. Regla nueva y acotada: **503 con el JSON `{message}` de nuestro backend = `ReglaNegocioError`**; 503 sin cuerpo, con texto plano o con HTML sigue siendo sin conexión, y el banner solo se enciende si el error clasificado es `ConexionError`.

En `src/shared/api/errors.test.ts`, dentro del `describe('clasificar (port de ApiClient.clasificar)')`, tras el caso `'5xx → error de conexión con el detalle HTTP para el diálogo'`:

```ts
  it('503 con mensaje del servidor (tipo de cambio no disponible) es un error de negocio, no de conexión', () => {
    const e = clasificar(503, 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.')
    expect(e).toBeInstanceOf(ReglaNegocioError)
    expect(e.status).toBe(503)
    expect(e.message).toBe('No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.')
  })
  it('503 sin mensaje (nginx) sigue siendo sin conexión', () => {
    expect(clasificar(503, null)).toBeInstanceOf(ConexionError)
    expect(clasificar(503, '')).toBeInstanceOf(ConexionError)
  })
```

- [ ] **Step 14: Ejecutar y ver que falla**

```bash
npx vitest run src/shared/api/errors.test.ts
```

Expected: FAIL, `expected ConexionError to be an instance of ReglaNegocioError`.

- [ ] **Step 15: Implementar**

En `src/shared/api/errors.ts`, la rama `default` de `clasificar` (l.58-60) queda:

```ts
    default:
      // Un 503 con mensaje solo lo emite nuestro backend (p. ej. el tipo de cambio no disponible, sub-proyecto 4b):
      // es un error de negocio que la vista enseña inline. client.ts solo pasa el mensaje de un 503 si el cuerpo
      // es JSON {message}; los de nginx (vacíos, texto o HTML) llegan con msg null y siguen siendo "sin conexión".
      if (status === 503 && msg) return new ReglaNegocioError(503, msg)
      if (status >= 500) return new ConexionError(status, MSG_SIN_CONEXION, `HTTP ${status}`)
      return new ApiError(status, msg ?? `Error del servidor (${status}).`)
```

Y en el javadoc de `esErrorGestionadoGlobalmente` (l.80-82) se añade: `Un 503 con mensaje es ReglaNegocioError y NO se gestiona globalmente.`

En `src/shared/api/client.ts`, dentro de `onResponse` (l.68-76), quitar la línea `if (response.status >= 500) reportarFallo()` y sustituir la de `clasificar` (l.76) por:

```ts
    // Un 503 solo es de negocio con el JSON {message} de nuestro backend (sub-proyecto 4b); el texto plano o el HTML
    // de nginx sigue siendo "sin conexión".
    const msg = response.status === 503 && typeof body === 'string' ? null : extraerMensaje(body)
    const err = clasificar(response.status, msg)
    if (err instanceof ConexionError) reportarFallo()
    else if (response.status >= 500) reportarExito()
```

(el `if (err instanceof SesionExpiradaError) dispararSesionExpirada()` y el `throw err` siguen igual). Los mocks existentes `HttpResponse.text('boom', { status: 503 })` de `client.test.ts`, `queryClient.test.tsx`, `taller/formulario/api.test.tsx` y `PulidosPendientesPage.test.tsx` NO se cambian: siguen siendo sin conexión.

En `src/shared/api/client.test.ts`, antes de `it('un 4xx no toca el estado de conexión (y cura el banner)'`:

```ts
  it('un 503 con {message} de nuestro backend es de negocio y no enciende el banner', async () => {
    server.use(http.get('*/api/clientes', () => HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })))
    const err: unknown = await api.GET('/api/clientes').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ReglaNegocioError)
    expect(estaConectado()).toBe(true)
  })
```

- [ ] **Step 16: Ejecutar y ver que pasa**

```bash
npx vitest run src/shared/api/errors.test.ts src/shared/api/client.test.ts src/shared/api/queryClient.test.tsx
```

Expected: PASS (los tres casos nuevos y los anteriores; `clasificar(503, null)` sigue siendo `ConexionError` y los 503 de texto de los mocks existentes siguen encendiendo el banner).

- [ ] **Step 17: Tokens**

En `src/shared/styles/tokens.css`, al final del bloque `@theme` (tras `--color-badge-sin-stock-bg: #F9E0E3;`, l.137):

```css

  /* Pedidos (sub-proyecto 4b), calco de StockController :869 y :904 (colores a mano, sin constante en Colores.java):
     barra ámbar del pendiente y su badge. El resto reutiliza fila-solicitud (en camino urgente), fila-recibido,
     fila-parcial, fila-cancelado y badge-neutro/azul-gris (en camino normal). */
  --color-fila-pendiente-brd: #C8961E;
  --color-badge-pendiente-bg: #FFF3D6;
  --color-badge-pendiente-text: #B26A00;
```

- [ ] **Step 18: Comprobar**

```bash
npm run check
```

Expected: lint y typecheck en verde; **1129 tests** (1119 + 6 de `importes` + 1 de `fechas` + 2 de `errors` + 1 de `client`).

- [ ] **Step 19: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/api/client.test.ts src/shared/api/errors.ts src/shared/api/errors.test.ts src/shared/lib/importes.ts src/shared/lib/importes.test.ts src/shared/lib/fechas.ts src/shared/lib/fechas.test.ts src/shared/lib/clavesIdempotencia.ts src/shared/lib/clavesIdempotencia.test.ts src/modules/taller/formulario/useGuardado.ts src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.tsx src/shared/styles/tokens.css
git commit -m "chore(web): contrato con lotes de compras y nullables, tipos de pedidos, importes con coma, fecha de pedidos, 503 con json de mensaje como error de negocio sin banner, claves de idempotencia en shared y tokens"
```

---

## Task 8: Web — store `formularioPedido` (P1)

**Files:**
- Create: `src/shared/lib/formularioPedido.ts`, `src/shared/lib/formularioPedido.test.ts`

**Interfaces:**
- Consumes: `crearStore` de `./store` (registra el store para `reiniciarStores`, `store.ts:52-73`); `SolicitudResumen`, `SolicitudStock` de `@/shared/api/client` (`client.ts:30-31`).
- Produces (firmas exactas de el documento de interfaces del reparto (fuera del repo)):

```ts
export type PrecargaPedido =
  | { modo: 'vacio' }
  | { modo: 'componentes'; idsCom: number[] }
  | { modo: 'solicitudes'; urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] }
export type FormularioPedidoAbierto = { tipo: 'compra'; precarga: PrecargaPedido } | { tipo: 'otro' }
export const formularioPedido: Store<FormularioPedidoAbierto | null>
export function abrirNuevoPedido(precarga: PrecargaPedido): void
export function abrirNuevoOtroPedido(): void
export function cerrarFormularioPedido(): void
```

> Búsqueda previa: `grep -rn "formularioPedido\|abrirNuevoPedido" src` sale vacío.

- [ ] **Step 1: Test (falla)**

`src/shared/lib/formularioPedido.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { abrirNuevoOtroPedido, abrirNuevoPedido, cerrarFormularioPedido, formularioPedido } from './formularioPedido'
import { reiniciarStores } from './store'

/** Spec 4b P1: Stock actual, Pedidos y la campana abren el formulario de alta llamando al store; el host del shell
 *  (FormulariosPedido, Task 15) pinta el que diga. */
describe('formularioPedido', () => {
  it('empieza cerrado', () => {
    expect(formularioPedido.get()).toBeNull()
  })
  it('abrirNuevoPedido abre el formulario de compra con su precarga', () => {
    abrirNuevoPedido({ modo: 'componentes', idsCom: [11, 12] })
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'componentes', idsCom: [11, 12] } })
    abrirNuevoPedido({ modo: 'solicitudes', urgentes: [], preventivas: [] })
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'solicitudes', urgentes: [], preventivas: [] } })
    abrirNuevoPedido({ modo: 'vacio' })
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'vacio' } })
  })
  it('abrirNuevoOtroPedido abre el de otros y cerrarFormularioPedido lo cierra', () => {
    abrirNuevoOtroPedido()
    expect(formularioPedido.get()).toEqual({ tipo: 'otro' })
    cerrarFormularioPedido()
    expect(formularioPedido.get()).toBeNull()
  })
  it('reiniciarStores (cierre de sesión) lo deja cerrado', () => {
    abrirNuevoPedido({ modo: 'vacio' })
    reiniciarStores()
    expect(formularioPedido.get()).toBeNull()
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/shared/lib/formularioPedido.test.ts`
Expected: FAIL, `Failed to resolve import "./formularioPedido"`.

- [ ] **Step 3: Implementar**

`src/shared/lib/formularioPedido.ts`:

```ts
import type { SolicitudResumen, SolicitudStock } from '@/shared/api/client'
import { crearStore } from './store'

/** Con qué se abre "Nuevo pedido" (spec 4b §6, modos de precarga):
 *  - 'vacio': el botón "Nuevo pedido" de la pestaña, sin líneas.
 *  - 'componentes': "Pedir" de Stock actual o de una alerta (un id) y "Pedir todas las piezas" (un id por alerta, en el
 *    orden de la campana); cantidad 1 por línea.
 *  - 'solicitudes': "Pedir piezas", con las listas PENDIENTE recién leídas; el formulario agrupa por componente. */
export type PrecargaPedido =
  | { modo: 'vacio' }
  | { modo: 'componentes'; idsCom: number[] }
  | { modo: 'solicitudes'; urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] }

export type FormularioPedidoAbierto = { tipo: 'compra'; precarga: PrecargaPedido } | { tipo: 'otro' }

/** Qué formulario de alta está abierto (spec 4b, P1): calco del `Stage` modal del JavaFX, que no saca al usuario de su vista.
 *  Vive en shared porque la campana (taller) y Stock y Pedidos (almacén) lo abren y un módulo no importa de otro. Con
 *  valor, las vistas que sondean congelan el refresco; se cierra al cerrar sesión (reiniciarStores). */
export const formularioPedido = crearStore<FormularioPedidoAbierto | null>(null)

export function abrirNuevoPedido(precarga: PrecargaPedido): void {
  formularioPedido.set({ tipo: 'compra', precarga })
}

export function abrirNuevoOtroPedido(): void {
  formularioPedido.set({ tipo: 'otro' })
}

export function cerrarFormularioPedido(): void {
  formularioPedido.set(null)
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/shared/lib/formularioPedido.test.ts`
Expected: PASS, 4 tests (acumulado: 1133).

- [ ] **Step 5: Commit**

```bash
git add src/shared/lib/formularioPedido.ts src/shared/lib/formularioPedido.test.ts
git commit -m "feat(shared): store del formulario de pedido con sus modos de precarga"
```

---
## Task 9: Web — reglas, filtros, stores, confirmaciones y tasa de Pedidos

**Files:**
- Create: `src/modules/almacen/pedidos/reglas.ts`, `src/modules/almacen/pedidos/reglas.test.ts`
- Create: `src/modules/almacen/pedidos/filtros.ts`, `src/modules/almacen/pedidos/filtros.test.ts`
- Create: `src/modules/almacen/pedidos/estado.ts`
- Create: `src/modules/almacen/pedidos/confirmaciones.ts`, `src/modules/almacen/pedidos/confirmaciones.test.ts`
- Create: `src/modules/almacen/pedidos/tasa.ts`, `src/modules/almacen/pedidos/tasa.test.tsx`

**Interfaces:**
- Consumes: `CompraComponente`, `CompraOtro`, `api` de `@/shared/api/client` (Task 7); `fechaLocal` de `@/shared/lib/fechas` (`fechas.ts:46-51` en `main`; unas líneas más abajo tras la T7); `crearStore`, `Store` de `@/shared/lib/store`.
- Produces (firmas exactas de el documento de interfaces del reparto (fuera del repo)):

```ts
// reglas.ts
export type EstadoPedido = 'pendiente' | 'en_camino' | 'parcial' | 'recibido' | 'cancelado'
export const ESTADOS_PEDIDO: EstadoPedido[]
export type TipoPedido = 'componentes' | 'otros'
export type Pedido = CompraComponente | CompraOtro
export function esCompra(p: Pedido): p is CompraComponente
export function idPedido(p: Pedido): number
export function nombrePedido(p: Pedido): string
export function chipDeEstado(e: EstadoPedido): string
export function estadoDeChip(chip: string): EstadoPedido | null
export function textoCantidad(p: Pedido): string
export function unidadesFila(p: Pedido): number
export function totalFila(p: Pedido): number
export function marcaPrecioCero(p: Pedido): boolean
export function marcaTotalCero(p: Pedido): boolean
export function llevaAviso(p: Pedido): boolean
export type AccionMenu = 'confirmar' | 'editar' | 'borrar' | 'parcial' | 'recibido' | 'cancelar' | 'resto' | 'cerrarSinResto' | 'revertir'
export type EntradaMenu = { accion: AccionMenu; texto: string } | 'separador'
export function entradasMenu(estado: string): EntradaMenu[]
export type Validacion = { ok: true; valor: number } | { ok: false; error: string }
export function validarParcial(texto: string, cantidad: number): Validacion
export function validarResto(texto: string, recibida: number | null, cantidad: number): Validacion
export function restante(p: Pedido): number
export function cantidadARevertir(p: Pedido): number
// filtros.ts
export type FiltrosPedidos = { estados: Set<EstadoPedido>; proveedores: Set<string>; buscador: string; desde: string; hasta: string }
export const FILTROS_PEDIDOS_VACIOS: FiltrosPedidos
export function ordenarCanceladosAlFinal<T extends { estado: string }>(lista: T[]): T[]
export function aplicarFiltrosPedidos<T extends Pedido>(lista: T[], f: FiltrosPedidos): T[]
export function filtrosDesdeStock(search: URLSearchParams): Partial<Pick<FiltrosPedidos, 'estados' | 'buscador'>> | null
// estado.ts
export const filtrosPedidos: Store<FiltrosPedidos>
export const seleccionPedidos: Record<TipoPedido, Store<string | null>>
// confirmaciones.ts
export type Confirmacion = { titulo: string; descripcion: string; textoAccion: string }
export function confirmacionDe(accion: 'cancelar' | 'borrar' | 'revertir', p: Pedido): Confirmacion
// tasa.ts
export type EstadoTasa = { tasa: number | null; cargando: boolean; error: boolean }
export function useTasas(divisas: string[]): Record<string, EstadoTasa>
export function useTasa(divisa: string | null): EstadoTasa
```

> Búsqueda previa: `src/modules/almacen/pedidos/` no existe. El filtro de fechas reutiliza la idea de `pasaFechas` del taller (`modules/taller/lib/filtros.ts:27-34`), que no se puede importar (regla de módulos), con una diferencia de calco: aquí una fecha nula **pasa** (StockController :954-957). Los textos del parcial y del resto salen de `StockController` :1500-1560 (`hotfix/0.16.3`): el no numérico es `"Cantidad no válida."` en los dos y `Integer.parseInt` admite signo, así que `"-3"` cae en el rango, no en "no válida".

- [ ] **Step 1: Test de las reglas (falla)**

`src/modules/almacen/pedidos/reglas.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import {
  cantidadARevertir, chipDeEstado, entradasMenu, esCompra, estadoDeChip, ESTADOS_PEDIDO, idPedido, llevaAviso, marcaPrecioCero,
  marcaTotalCero, nombrePedido, restante, textoCantidad, totalFila, unidadesFila, validarParcial, validarResto,
} from './reglas'

const compra = (o: Partial<CompraComponente> = {}): CompraComponente => ({
  idCompra: 2, idCom: 12, tipoComponente: 'bat-x', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 10, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR', precioEur: 12.5,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})
const otro = (o: Partial<CompraOtro> = {}): CompraOtro => ({
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})

describe('estados y chips', () => {
  it('los cinco estados en el orden de los checks; el chip "en camino" lleva espacio y el estado guion bajo', () => {
    expect(ESTADOS_PEDIDO).toEqual(['pendiente', 'en_camino', 'parcial', 'recibido', 'cancelado'])
    expect(ESTADOS_PEDIDO.map(chipDeEstado)).toEqual(['pendiente', 'en camino', 'parcial', 'recibido', 'cancelado'])
    expect(estadoDeChip('en camino')).toBe('en_camino')
    expect(estadoDeChip('recibido')).toBe('recibido')
    expect(estadoDeChip('en_camino')).toBeNull()
    expect(estadoDeChip('otro')).toBeNull()
  })
  it('esCompra, idPedido y nombrePedido sirven para las dos tablas', () => {
    expect(esCompra(compra())).toBe(true)
    expect(esCompra(otro())).toBe(false)
    expect(idPedido(compra())).toBe(2)
    expect(idPedido(otro())).toBe(7)
    expect(nombrePedido(compra())).toBe('bat-x')
    expect(nombrePedido(otro())).toBe('Cinta de embalar')
  })
})

describe('columna Cant. (StockController :782-794)', () => {
  it('parcial: "recibida/cantidad", o solo la cantidad si la recibida es nula', () => {
    expect(textoCantidad(compra({ estado: 'parcial', cantidadRecibida: 3 }))).toBe('3/10')
    expect(textoCantidad(compra({ estado: 'parcial', cantidadRecibida: null }))).toBe('10')
  })
  it('recibido: la recibida si no es nula, si no la cantidad; resto de estados: la cantidad', () => {
    expect(textoCantidad(compra({ estado: 'recibido', cantidadRecibida: 8 }))).toBe('8')
    expect(textoCantidad(compra({ estado: 'recibido', cantidadRecibida: null }))).toBe('10')
    expect(textoCantidad(compra({ estado: 'en_camino', cantidadRecibida: 4 }))).toBe('10')
    expect(textoCantidad(otro({ estado: 'cancelado' }))).toBe('3')
  })
})

describe('columnas P.Unit., EUR y Estado (StockController :797-849, :885-919)', () => {
  it('unidades = recibida en recibido (si no es nula), si no la cantidad; total = unidades × precioEur', () => {
    expect(unidadesFila(compra({ estado: 'recibido', cantidadRecibida: 2 }))).toBe(2)
    expect(totalFila(compra({ estado: 'recibido', cantidadRecibida: 2 }))).toBe(25)
    expect(unidadesFila(compra({ estado: 'recibido', cantidadRecibida: null }))).toBe(10)
    expect(unidadesFila(compra({ estado: 'parcial', cantidadRecibida: 3 }))).toBe(10)
    expect(totalFila(otro({ precioEur: 1.5 }))).toBe(4.5)
  })
  it('"!" solo en recibido: precio 0 en P.Unit.; total 0 en EUR', () => {
    const gratis = compra({ estado: 'recibido', precioUnidadPedido: 0, precioEur: 0 })
    expect(marcaPrecioCero(gratis)).toBe(true)
    expect(marcaTotalCero(gratis)).toBe(true)
    const sinEuros = compra({ estado: 'recibido', precioUnidadPedido: 5, precioEur: 0 })
    expect(marcaPrecioCero(sinEuros)).toBe(false)
    expect(marcaTotalCero(sinEuros)).toBe(true)
    expect(marcaTotalCero(compra({ estado: 'recibido', cantidadRecibida: 0 }))).toBe(true)
    expect(marcaPrecioCero(compra({ estado: 'pendiente', precioUnidadPedido: 0 }))).toBe(false)
    expect(marcaTotalCero(compra({ estado: 'en_camino', precioEur: 0 }))).toBe(false)
  })
  it('"⚠" solo si urgente y en camino o parcial (un pendiente urgente no lo lleva)', () => {
    expect(llevaAviso(compra({ esUrgente: true, estado: 'en_camino' }))).toBe(true)
    expect(llevaAviso(compra({ esUrgente: true, estado: 'parcial' }))).toBe(true)
    expect(llevaAviso(compra({ esUrgente: true, estado: 'pendiente' }))).toBe(false)
    expect(llevaAviso(compra({ esUrgente: true, estado: 'recibido' }))).toBe(false)
    expect(llevaAviso(compra({ esUrgente: false, estado: 'en_camino' }))).toBe(false)
  })
})

describe('menú contextual por estado (spec §6, StockController :970-1010)', () => {
  it.each([
    ['pendiente', [{ accion: 'confirmar', texto: 'Confirmar pedido' }, 'separador', { accion: 'editar', texto: 'Editar' }, { accion: 'borrar', texto: 'Borrar' }]],
    ['en_camino', [{ accion: 'parcial', texto: 'Recepción parcial' }, { accion: 'recibido', texto: 'Confirmar recibido' }, 'separador', { accion: 'editar', texto: 'Editar' }, { accion: 'cancelar', texto: 'Cancelar pedido' }]],
    ['parcial', [{ accion: 'resto', texto: 'Recibir resto' }, { accion: 'cerrarSinResto', texto: 'Cerrar sin resto' }]],
    ['recibido', [{ accion: 'revertir', texto: 'Revertir a En camino' }, 'separador', { accion: 'editar', texto: 'Editar' }]],
    ['cancelado', []],
  ])('%s', (estado, entradas) => {
    expect(entradasMenu(estado)).toEqual(entradas)
  })
})

describe('diálogos de cantidad (StockController :1500-1560)', () => {
  it('validarParcial: entero con signo como Integer.parseInt; 0 < cant < cantidad', () => {
    expect(validarParcial('3', 10)).toEqual({ ok: true, valor: 3 })
    expect(validarParcial(' 9 ', 10)).toEqual({ ok: true, valor: 9 })
    expect(validarParcial('abc', 10)).toEqual({ ok: false, error: 'Cantidad no válida.' })
    expect(validarParcial('', 10)).toEqual({ ok: false, error: 'Cantidad no válida.' })
    expect(validarParcial('2.5', 10)).toEqual({ ok: false, error: 'Cantidad no válida.' })
    expect(validarParcial('0', 10)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0 y menor que 10.' })
    expect(validarParcial('10', 10)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0 y menor que 10.' })
    expect(validarParcial('-3', 10)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0 y menor que 10.' })
    expect(validarParcial('1', 1)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0 y menor que 1.' })
  })
  it('validarResto: > 0 y sin pasar de lo pedido; "Faltan" con el restante', () => {
    expect(validarResto('7', 3, 10)).toEqual({ ok: true, valor: 7 })
    expect(validarResto('2', null, 2)).toEqual({ ok: true, valor: 2 })
    expect(validarResto('x', 3, 10)).toEqual({ ok: false, error: 'Cantidad no válida.' })
    expect(validarResto('0', 3, 10)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0.' })
    expect(validarResto('-1', 3, 10)).toEqual({ ok: false, error: 'La cantidad debe ser mayor que 0.' })
    expect(validarResto('8', 3, 10)).toEqual({ ok: false, error: 'No puedes recibir más de lo pedido. Faltan 7 unidad(es).' })
  })
  it('restante = cantidad − (recibida ?? 0); a revertir = recibida ?? cantidad', () => {
    expect(restante(compra({ estado: 'parcial', cantidadRecibida: 3 }))).toBe(7)
    expect(restante(compra({ cantidadRecibida: null }))).toBe(10)
    expect(cantidadARevertir(compra({ estado: 'recibido', cantidadRecibida: 2 }))).toBe(2)
    expect(cantidadARevertir(compra({ estado: 'recibido', cantidadRecibida: null }))).toBe(10)
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/reglas.test.ts`
Expected: FAIL, `Failed to resolve import "./reglas"`.

- [ ] **Step 3: Implementar las reglas**

`src/modules/almacen/pedidos/reglas.ts`:

```ts
import type { CompraComponente, CompraOtro } from '@/shared/api/client'

/** `CompraComponente.Estado` del JavaFX; el servidor lo manda tal cual (`name()`), con guion bajo en `en_camino`. */
export type EstadoPedido = 'pendiente' | 'en_camino' | 'parcial' | 'recibido' | 'cancelado'
/** En el orden de los cinco checks del menú "Estado" (StockController :943-949). */
export const ESTADOS_PEDIDO: EstadoPedido[] = ['pendiente', 'en_camino', 'parcial', 'recibido', 'cancelado']

/** Toggle "Componentes" | "Otros" (spec 4b, P4). */
export type TipoPedido = 'componentes' | 'otros'
export type Pedido = CompraComponente | CompraOtro

export function esCompra(p: Pedido): p is CompraComponente {
  return 'idCompra' in p
}

export function idPedido(p: Pedido): number {
  return esCompra(p) ? p.idCompra : p.idCompraOtro
}

/** Lo que va en `{componente}` de los textos y en el buscador: el tipo del componente o, en otros, el concepto. */
export function nombrePedido(p: Pedido): string {
  return esCompra(p) ? p.tipoComponente : p.concepto
}

/** Texto del check del filtro: `estado.name().replace('_', ' ')` (StockController :925-933). */
export function chipDeEstado(e: EstadoPedido): string {
  return e.replace('_', ' ')
}

export function estadoDeChip(chip: string): EstadoPedido | null {
  return ESTADOS_PEDIDO.find((e) => chipDeEstado(e) === chip) ?? null
}

/** Columna "Cant." (StockController :782-794). */
export function textoCantidad(p: Pedido): string {
  if (p.estado === 'parcial') return p.cantidadRecibida != null ? `${p.cantidadRecibida}/${p.cantidad}` : String(p.cantidad)
  if (p.estado === 'recibido') return String(p.cantidadRecibida ?? p.cantidad)
  return String(p.cantidad)
}

/** Unidades del total de la columna EUR y del CSV (:825-849, :1968-1970): la recibida en `recibido` si no es nula. */
export function unidadesFila(p: Pedido): number {
  return p.estado === 'recibido' && p.cantidadRecibida != null ? p.cantidadRecibida : p.cantidad
}

export function totalFila(p: Pedido): number {
  return unidadesFila(p) * p.precioEur
}

/** "!" ámbar de P.Unit. (:815-819). */
export function marcaPrecioCero(p: Pedido): boolean {
  return p.estado === 'recibido' && p.precioUnidadPedido === 0
}

/** "!" ámbar de EUR (:841-845). */
export function marcaTotalCero(p: Pedido): boolean {
  return p.estado === 'recibido' && totalFila(p) === 0
}

/** "⚠" junto al badge (:901, :915): urgente y en camino o parcial; un pendiente urgente no lo lleva (calco). */
export function llevaAviso(p: Pedido): boolean {
  return p.esUrgente && (p.estado === 'en_camino' || p.estado === 'parcial')
}

export type AccionMenu = 'confirmar' | 'editar' | 'borrar' | 'parcial' | 'recibido' | 'cancelar' | 'resto' | 'cerrarSinResto' | 'revertir'
export type EntradaMenu = { accion: AccionMenu; texto: string } | 'separador'

const EDITAR: EntradaMenu = { accion: 'editar', texto: 'Editar' }
const MENU: Record<EstadoPedido, EntradaMenu[]> = {
  pendiente: [{ accion: 'confirmar', texto: 'Confirmar pedido' }, 'separador', EDITAR, { accion: 'borrar', texto: 'Borrar' }],
  en_camino: [{ accion: 'parcial', texto: 'Recepción parcial' }, { accion: 'recibido', texto: 'Confirmar recibido' }, 'separador', EDITAR, { accion: 'cancelar', texto: 'Cancelar pedido' }],
  parcial: [{ accion: 'resto', texto: 'Recibir resto' }, { accion: 'cerrarSinResto', texto: 'Cerrar sin resto' }],
  recibido: [{ accion: 'revertir', texto: 'Revertir a En camino' }, 'separador', EDITAR],
  cancelado: [],
}

/** Menú contextual por estado, idéntico en las dos tablas (StockController :970-1010 y :1237-1277). Cancelado o un estado
 *  desconocido: sin entradas. */
export function entradasMenu(estado: string): EntradaMenu[] {
  return (ESTADOS_PEDIDO as string[]).includes(estado) ? MENU[estado as EstadoPedido] : []
}

export type Validacion = { ok: true; valor: number } | { ok: false; error: string }

const MSG_NO_VALIDA = 'Cantidad no válida.'
const ENTERO_CON_SIGNO = /^[+-]?\d+$/

/** `Integer.parseInt(texto.trim())`: admite signo, así que "-3" es un número (y cae en el rango, no en "no válida"). */
function parsearEnteroJava(texto: string): number | null {
  const t = texto.trim()
  return ENTERO_CON_SIGNO.test(t) ? Number(t) : null
}

/** "Recepción parcial" (:1510-1521). */
export function validarParcial(texto: string, cantidad: number): Validacion {
  const n = parsearEnteroJava(texto)
  if (n === null) return { ok: false, error: MSG_NO_VALIDA }
  if (n <= 0 || n >= cantidad) return { ok: false, error: `La cantidad debe ser mayor que 0 y menor que ${cantidad}.` }
  return { ok: true, valor: n }
}

/** "Recibir unidades" (:1546-1561). */
export function validarResto(texto: string, recibida: number | null, cantidad: number): Validacion {
  const n = parsearEnteroJava(texto)
  if (n === null) return { ok: false, error: MSG_NO_VALIDA }
  if (n <= 0) return { ok: false, error: 'La cantidad debe ser mayor que 0.' }
  const yaRecibidas = recibida ?? 0
  if (yaRecibidas + n > cantidad) return { ok: false, error: `No puedes recibir más de lo pedido. Faltan ${cantidad - yaRecibidas} unidad(es).` }
  return { ok: true, valor: n }
}

/** Unidades que faltan (:1535): valor inicial de "Recibir unidades". */
export function restante(p: Pedido): number {
  return p.cantidad - (p.cantidadRecibida ?? 0)
}

/** Unidades que "Revertir a En camino" descuenta del stock (:1622). */
export function cantidadARevertir(p: Pedido): number {
  return p.cantidadRecibida ?? p.cantidad
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/reglas.test.ts`
Expected: PASS, 15 tests.

- [ ] **Step 5: Test de filtros (falla)**

`src/modules/almacen/pedidos/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { aplicarFiltrosPedidos, FILTROS_PEDIDOS_VACIOS, filtrosDesdeStock, ordenarCanceladosAlFinal, type FiltrosPedidos } from './filtros'

const compra = (o: Partial<CompraComponente>): CompraComponente => ({
  idCompra: 1, idCom: 11, tipoComponente: 'lcd-x-negro', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 2, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR', precioEur: 12.5,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})
const otro = (o: Partial<CompraOtro>): CompraOtro => ({
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})

const lista = [
  compra({ idCompra: 1, tipoComponente: 'lcd-x-negro', estado: 'pendiente', nombreProveedor: 'Proveedor A', fechaPedido: '2026-09-20T08:30:00' }),
  // 22:30 UTC del 19 = 00:30 del 20 en Madrid (CEST): cuenta como día 20.
  compra({ idCompra: 2, tipoComponente: 'bat-x', estado: 'en_camino', nombreProveedor: 'Proveedor B', fechaPedido: '2026-09-19T22:30:00' }),
  compra({ idCompra: 3, tipoComponente: 'bat-y', estado: 'parcial', nombreProveedor: 'Proveedor A', fechaPedido: '2026-09-18T10:00:00' }),
  compra({ idCompra: 4, tipoComponente: 'cam-x', estado: 'cancelado', nombreProveedor: 'ACME', fechaPedido: '2026-09-17T10:00:00' }),
]
const ids = (l: CompraComponente[]) => l.map((p) => p.idCompra)
const f = (o: Partial<FiltrosPedidos>): FiltrosPedidos => ({ ...FILTROS_PEDIDOS_VACIOS, ...o })

describe('aplicarFiltrosPedidos (StockController :934-957, AND de los cuatro)', () => {
  it('sin filtros muestra todos', () => {
    expect(ids(aplicarFiltrosPedidos(lista, FILTROS_PEDIDOS_VACIOS))).toEqual([1, 2, 3, 4])
  })
  it('Estado: varios chips se combinan con O sobre el estado del servidor', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ estados: new Set(['en_camino', 'parcial'] as const) })))).toEqual([2, 3])
  })
  it('Proveedor: por nombre', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ proveedores: new Set(['Proveedor A']) })))).toEqual([1, 3])
  })
  it('buscador: "contiene", sin mayúsculas y recortado, sobre el componente o el concepto', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ buscador: '  BAT ' })))).toEqual([2, 3])
    const otros = [otro({ idCompraOtro: 7, concepto: 'Cinta de embalar' }), otro({ idCompraOtro: 8, concepto: 'Bolsas' })]
    expect(aplicarFiltrosPedidos(otros, f({ buscador: 'cinta' })).map((p) => p.idCompraOtro)).toEqual([7])
  })
  it('Desde/Hasta: día de Madrid de la fecha de pedido, inclusivos', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ desde: '2026-09-20', hasta: '2026-09-20' })))).toEqual([1, 2])
    expect(ids(aplicarFiltrosPedidos(lista, f({ desde: '2026-09-18' })))).toEqual([1, 2, 3])
    expect(ids(aplicarFiltrosPedidos(lista, f({ hasta: '2026-09-18' })))).toEqual([3, 4])
  })
  it('los cuatro filtros se combinan con Y', () => {
    const todos = f({ estados: new Set(['pendiente', 'parcial'] as const), proveedores: new Set(['Proveedor A']), buscador: 'bat', desde: '2026-09-18' })
    expect(ids(aplicarFiltrosPedidos(lista, todos))).toEqual([3])
  })
})

describe('ordenarCanceladosAlFinal (StockController :1016)', () => {
  it('lleva los cancelados al final sin reordenar dentro de cada grupo (orden del servidor)', () => {
    const l = [compra({ idCompra: 1, estado: 'cancelado' }), compra({ idCompra: 2, estado: 'pendiente' }), compra({ idCompra: 3, estado: 'cancelado' }), compra({ idCompra: 4, estado: 'recibido' })]
    expect(ids(ordenarCanceladosAlFinal(l))).toEqual([2, 4, 1, 3])
    expect(ids(l)).toEqual([1, 2, 3, 4])
  })
})

describe('filtrosDesdeStock (llegada desde "En Camino", S8)', () => {
  it('lee ?estados y ?buscar con el formato de parametrosPedidos (codificado o no)', () => {
    const esperado = { estados: new Set(['pendiente', 'en_camino', 'parcial']), buscador: 'bat-x' }
    expect(filtrosDesdeStock(new URLSearchParams('estados=pendiente%2Cen+camino%2Cparcial&buscar=bat-x'))).toEqual(esperado)
    expect(filtrosDesdeStock(new URLSearchParams('estados=pendiente,en camino,parcial&buscar=bat-x'))).toEqual(esperado)
  })
  it('sin parámetros devuelve null; un chip desconocido se ignora; solo lo que venga', () => {
    expect(filtrosDesdeStock(new URLSearchParams(''))).toBeNull()
    expect(filtrosDesdeStock(new URLSearchParams('componente=12'))).toBeNull()
    expect(filtrosDesdeStock(new URLSearchParams('estados=parcial,otro'))).toEqual({ estados: new Set(['parcial']) })
    expect(filtrosDesdeStock(new URLSearchParams('buscar=lcd'))).toEqual({ buscador: 'lcd' })
  })
})
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/filtros.test.ts`
Expected: FAIL, `Failed to resolve import "./filtros"`.

- [ ] **Step 7: Implementar filtros y stores**

`src/modules/almacen/pedidos/filtros.ts`:

```ts
import { fechaLocal } from '@/shared/lib/fechas'
import { estadoDeChip, nombrePedido, type EstadoPedido, type Pedido } from './reglas'

/** Los cuatro filtros de la barra, compartidos por los dos toggles (StockController :946-953 y :1219-1225, spec 4b P4).
 *  `proveedores` son nombres (MultiSelectComboBox de nombres); las fechas, 'yyyy-MM-dd' o '' como en RangoFechas. */
export type FiltrosPedidos = { estados: Set<EstadoPedido>; proveedores: Set<string>; buscador: string; desde: string; hasta: string }
export const FILTROS_PEDIDOS_VACIOS: FiltrosPedidos = { estados: new Set(), proveedores: new Set(), buscador: '', desde: '', hasta: '' }

/** Calco de :1016 y :1283: el servidor ordena por fecha desc y el cliente manda los cancelados al final con un sort estable. */
export function ordenarCanceladosAlFinal<T extends { estado: string }>(lista: T[]): T[] {
  return [...lista].sort((a, b) => Number(a.estado === 'cancelado') - Number(b.estado === 'cancelado'))
}

/** `!fecha.isBefore(desde) && !fecha.isAfter(hasta)` sobre el día de Madrid (:954-957); una fecha nula pasa (calco). */
function pasaFechas(fechaPedido: string | null, desde: string, hasta: string): boolean {
  if (desde === '' && hasta === '') return true
  const dia = fechaLocal(fechaPedido)
  if (dia === null) return true
  if (desde !== '' && dia < desde) return false
  if (hasta !== '' && dia > hasta) return false
  return true
}

/** Predicado del FilteredList (:934-957): cada filtro vacío no filtra; entre ellos, Y. El buscador es "contiene" sin
 *  mayúsculas sobre el componente (o el concepto en Otros, :1211-1212). */
export function aplicarFiltrosPedidos<T extends Pedido>(lista: T[], f: FiltrosPedidos): T[] {
  const texto = f.buscador.trim().toLowerCase()
  return lista.filter(
    (p) =>
      (f.estados.size === 0 || f.estados.has(p.estado as EstadoPedido)) &&
      (f.proveedores.size === 0 || f.proveedores.has(p.nombreProveedor)) &&
      (texto === '' || nombrePedido(p).toLowerCase().includes(texto)) &&
      pasaFechas(p.fechaPedido, f.desde, f.hasta),
  )
}

/** Llegada desde "En Camino" de Stock actual (spec 4b §6, S8): `?estados=pendiente,en camino,parcial&buscar=<tipo>`
 *  (Stock los monta con `parametrosPedidos`, `stock/columnas.tsx:31`). Devuelve solo lo que venga; null si no viene
 *  ninguno de los dos. Los chips que no son de un estado se ignoran. */
export function filtrosDesdeStock(search: URLSearchParams): Partial<Pick<FiltrosPedidos, 'estados' | 'buscador'>> | null {
  const estados = search.get('estados')
  const buscar = search.get('buscar')
  if (estados === null && buscar === null) return null
  const filtros: Partial<Pick<FiltrosPedidos, 'estados' | 'buscador'>> = {}
  if (estados !== null) {
    filtros.estados = new Set(estados.split(',').map((c) => estadoDeChip(c.trim())).filter((e): e is EstadoPedido => e !== null))
  }
  if (buscar !== null) filtros.buscador = buscar
  return filtros
}
```

`src/modules/almacen/pedidos/estado.ts`:

```ts
import { crearStore, type Store } from '@/shared/lib/store'
import { FILTROS_PEDIDOS_VACIOS, type FiltrosPedidos } from './filtros'
import type { TipoPedido } from './reglas'

/** Caché de vista del JavaFX (spec 4a S2, 4b P4): los filtros son uno para los dos toggles y sobreviven al cambio de toggle,
 *  de sección y a la vuelta desde Reparaciones; la fila seleccionada es una por toggle (id como texto, como DataTable).
 *  Se reinician al cerrar sesión (reiniciarStores). */
export const filtrosPedidos = crearStore<FiltrosPedidos>(FILTROS_PEDIDOS_VACIOS)
export const seleccionPedidos: Record<TipoPedido, Store<string | null>> = {
  componentes: crearStore<string | null>(null),
  otros: crearStore<string | null>(null),
}
```

- [ ] **Step 8: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/filtros.test.ts`
Expected: PASS, 9 tests.

- [ ] **Step 9: Test de confirmaciones (falla)**

`src/modules/almacen/pedidos/confirmaciones.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { confirmacionDe } from './confirmaciones'

const compra: CompraComponente = {
  idCompra: 4, idCom: 14, tipoComponente: 'cam-x', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 5, cantidadRecibida: 2,
  esUrgente: false, fechaPedido: '2026-09-16T08:00:00', fechaLlegada: '2026-09-18T08:00:00', precioUnidadPedido: 12.5, divisa: 'EUR',
  precioEur: 12.5, estado: 'recibido', updatedAt: '2026-09-18T08:00:00',
}
const otro: CompraOtro = {
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2,
  estado: 'recibido', updatedAt: '2026-09-20T08:30:00',
}

/** Textos de ConfirmDialog.mostrar en StockController :1576-1640 (componentes) y :1302-1457 (otros). */
describe('confirmacionDe', () => {
  it('Cancelar pedido: título y botón iguales (calco)', () => {
    expect(confirmacionDe('cancelar', compra)).toEqual({ titulo: 'Cancelar pedido', descripcion: '¿Cancelar el pedido #4 de cam-x?', textoAccion: 'Cancelar pedido' })
  })
  it('Borrar pedido', () => {
    expect(confirmacionDe('borrar', otro)).toEqual({ titulo: 'Borrar pedido', descripcion: '¿Borrar el pedido pendiente #7 de Cinta de embalar?', textoAccion: 'Borrar' })
  })
  it('Revertir a En camino de componentes: tres líneas con las unidades a descontar (recibida ?? cantidad)', () => {
    expect(confirmacionDe('revertir', compra)).toEqual({
      titulo: 'Revertir a En camino',
      descripcion: '¿Revertir el pedido #4 de cam-x a En camino?\nSe descontarán 2 unidad(es) del stock.\nRecuerda revisar el stock tras la operación.',
      textoAccion: 'Revertir a En camino',
    })
    expect(confirmacionDe('revertir', { ...compra, cantidadRecibida: null }).descripcion).toContain('Se descontarán 5 unidad(es) del stock.')
  })
  it('Revertir a En camino de otros: solo la primera frase (no hay stock)', () => {
    expect(confirmacionDe('revertir', otro)).toEqual({ titulo: 'Revertir a En camino', descripcion: '¿Revertir el pedido #7 de Cinta de embalar a En camino?', textoAccion: 'Revertir a En camino' })
  })
})
```

- [ ] **Step 10: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/confirmaciones.test.ts`
Expected: FAIL, `Failed to resolve import "./confirmaciones"`.

- [ ] **Step 11: Implementar**

`src/modules/almacen/pedidos/confirmaciones.ts`:

```ts
import { cantidadARevertir, esCompra, idPedido, nombrePedido, type Pedido } from './reglas'

export type Confirmacion = { titulo: string; descripcion: string; textoAccion: string }

/** Textos de las tres confirmaciones (spec 4b §6). "Cancelar pedido" / "Cancelar" se calca tal cual. La descripción de
 *  revertir en componentes lleva saltos de línea: ConfirmDialog los respeta (whitespace-pre-line, Task 13). */
export function confirmacionDe(accion: 'cancelar' | 'borrar' | 'revertir', p: Pedido): Confirmacion {
  const id = idPedido(p)
  const nombre = nombrePedido(p)
  switch (accion) {
    case 'cancelar':
      return { titulo: 'Cancelar pedido', descripcion: `¿Cancelar el pedido #${id} de ${nombre}?`, textoAccion: 'Cancelar pedido' }
    case 'borrar':
      return { titulo: 'Borrar pedido', descripcion: `¿Borrar el pedido pendiente #${id} de ${nombre}?`, textoAccion: 'Borrar' }
    case 'revertir': {
      const primera = `¿Revertir el pedido #${id} de ${nombre} a En camino?`
      const descripcion = esCompra(p)
        ? `${primera}\nSe descontarán ${cantidadARevertir(p)} unidad(es) del stock.\nRecuerda revisar el stock tras la operación.`
        : primera
      return { titulo: 'Revertir a En camino', descripcion, textoAccion: 'Revertir a En camino' }
    }
  }
}
```

- [ ] **Step 12: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/confirmaciones.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 13: Test de la tasa (falla)**

`src/modules/almacen/pedidos/tasa.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { crearQueryClient } from '@/shared/api/queryClient'
import { server } from '@/test/server'
import { useTasa, useTasas } from './tasa'

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

describe('tasa de cambio (GET /api/tipo-cambio/{divisa}, spec 4b §5)', () => {
  it('EUR: tasa 1 al momento, sin consulta ni petición', () => {
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useTasas(['EUR']), { wrapper })
    expect(result.current).toEqual({ EUR: { tasa: 1, cargando: false, error: false } })
    expect(qc.getQueryCache().findAll({ queryKey: ['tipo-cambio'] })).toHaveLength(0)
  })
  it('USD: lee `value` con una sola petición aunque la divisa se repita', async () => {
    let peticiones = 0
    server.use(http.get('*/api/tipo-cambio/USD', () => { peticiones += 1; return HttpResponse.json({ value: 1.1367 }) }))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useTasas(['USD', 'EUR', 'USD']), { wrapper })
    expect(result.current.USD).toEqual({ tasa: null, cargando: true, error: false })
    await waitFor(() => expect(result.current.USD).toEqual({ tasa: 1.1367, cargando: false, error: false }))
    expect(result.current.EUR).toEqual({ tasa: 1, cargando: false, error: false })
    expect(peticiones).toBe(1)
  })
  it('si falla (503 del servidor), error: true y sin tasa; no reintenta', async () => {
    let peticiones = 0
    server.use(http.get('*/api/tipo-cambio/USD', () => { peticiones += 1; return HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 }) }))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useTasas(['USD']), { wrapper })
    await waitFor(() => expect(result.current.USD).toEqual({ tasa: null, cargando: false, error: true }))
    expect(peticiones).toBe(1)
  })
  it('useTasa: null cuenta como EUR; con divisa, el estado de esa divisa', async () => {
    server.use(http.get('*/api/tipo-cambio/USD', () => HttpResponse.json({ value: 1.1367 })))
    const { wrapper } = envoltorio()
    const { result: sinDivisa } = renderHook(() => useTasa(null), { wrapper })
    expect(sinDivisa.current).toEqual({ tasa: 1, cargando: false, error: false })
    const { result: usd } = renderHook(() => useTasa('USD'), { wrapper })
    await waitFor(() => expect(usd.current.tasa).toBe(1.1367))
  })
})
```

- [ ] **Step 14: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/tasa.test.tsx`
Expected: FAIL, `Failed to resolve import "./tasa"`.

- [ ] **Step 15: Implementar**

`src/modules/almacen/pedidos/tasa.ts`:

```ts
import { useQueries } from '@tanstack/react-query'
import { api } from '@/shared/api/client'

/** Tasa de cambio para la vista previa del Total EUR de los formularios (spec 4b §6-§8): `tasa` = unidades de la divisa
 *  por 1 EUR (Frankfurter `from=EUR`), así que el importe en euros es `precio / tasa`. */
export type EstadoTasa = { tasa: number | null; cargando: boolean; error: boolean }

const TASA_EUR: EstadoTasa = { tasa: 1, cargando: false, error: false }
const UNA_HORA_MS = 3_600_000

async function pedirTasa(divisa: string): Promise<number> {
  const { data } = await api.GET('/api/tipo-cambio/{divisa}', { params: { path: { divisa } } })
  const tasa = data?.value
  if (typeof tasa !== 'number' || !(tasa > 0)) throw new Error(`Tasa no válida para ${divisa}`)
  return tasa
}

/** Una consulta por divisa distinta de EUR (`['tipo-cambio', divisa]`, 1 h de vida, sin reintentos); EUR es 1 sin
 *  petición. Silenciada: el formulario pinta "—" o "Error al obtener tasa" en su sitio, sin diálogo global. */
export function useTasas(divisas: string[]): Record<string, EstadoTasa> {
  const distintas = [...new Set(divisas)].filter((d) => d !== 'EUR')
  const consultas = useQueries({
    queries: distintas.map((divisa) => ({
      queryKey: ['tipo-cambio', divisa] as const,
      queryFn: () => pedirTasa(divisa),
      staleTime: UNA_HORA_MS,
      retry: false,
      meta: { silenciarError: true },
    })),
  })
  const resultado: Record<string, EstadoTasa> = {}
  if (divisas.includes('EUR')) resultado.EUR = TASA_EUR
  distintas.forEach((divisa, i) => {
    const c = consultas[i]
    resultado[divisa] = { tasa: c.data ?? null, cargando: c.isPending, error: c.isError }
  })
  return resultado
}

/** Atajo de una divisa (editor de pedido): null cuenta como EUR. */
export function useTasa(divisa: string | null): EstadoTasa {
  const tasas = useTasas(divisa === null ? [] : [divisa])
  return divisa === null ? TASA_EUR : tasas[divisa]
}
```

- [ ] **Step 16: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/tasa.test.tsx`
Expected: PASS, 4 tests.

- [ ] **Step 17: Lint y suite**

```bash
npm run lint && npx vitest run src/modules/almacen/pedidos
```

Expected: lint sin errores; PASS, 32 tests (acumulado: 1165).

- [ ] **Step 18: Commit**

```bash
git add src/modules/almacen/pedidos/reglas.ts src/modules/almacen/pedidos/reglas.test.ts src/modules/almacen/pedidos/filtros.ts src/modules/almacen/pedidos/filtros.test.ts src/modules/almacen/pedidos/estado.ts src/modules/almacen/pedidos/confirmaciones.ts src/modules/almacen/pedidos/confirmaciones.test.ts src/modules/almacen/pedidos/tasa.ts src/modules/almacen/pedidos/tasa.test.tsx
git commit -m "feat(pedidos): reglas de cantidad, importes y menu por estado, filtros, stores, confirmaciones y tasa de cambio"
```

---
## Task 10: Web — `api.ts` de Pedidos

**Files:**
- Create: `src/modules/almacen/pedidos/api.ts`, `src/modules/almacen/pedidos/api.test.tsx`

**Interfaces:**
- Consumes: `api`, `CompraComponente`, `CompraOtro` de `@/shared/api/client`; `useIntervaloRefresco` de `@/shared/api/refresco`; `ordenarCanceladosAlFinal` de `./filtros`; `esCompra`, `Pedido`, `TipoPedido` de `./reglas` (Task 9). Rutas del contrato regenerado (Task 7): `/api/compras/{idCompra}/…` y `/api/compras-otros/{id}/…` (el parámetro de ruta de otros se llama `id`, `schema.d.ts:119` y `:1111-1220`), `PUT`/`DELETE` en `/api/compras/{idCompra}` y `/api/compras-otros/{id}`, `POST /api/compras/lote` y `/api/compras-otros/lote` con `params.header['Idempotency-Key']` (mismo patrón que `useGuardarLote`, `modules/taller/asignaciones/modal/api.ts:52-68`).
- Produces (firmas de el documento de interfaces del reparto (fuera del repo); ver la desviación 2 sobre `habilitada`):

```ts
export const CLAVE_COMPRAS = ['compras'] as const
export function claveCompras(tipo: TipoPedido): readonly ['compras', TipoPedido]
export function useCompras({ activo, habilitada }: { activo: boolean; habilitada?: boolean }): UseQueryResult<CompraComponente[]>
export function useComprasOtros({ activo, habilitada }: { activo: boolean; habilitada?: boolean }): UseQueryResult<CompraOtro[]>
export type AccionTransicion = 'confirmar' | 'confirmar-recibido' | 'confirmar-parcial' | 'recibir-resto' | 'confirmar-alterado' | 'cancelar' | 'desrecibir' | 'borrar'
export function useTransicionPedido(tipo: TipoPedido): UseMutationResult<unknown, unknown, { accion: AccionTransicion; pedido: Pedido; cantidad?: number }>
export type CuerpoEditarCompra = { idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number; divisa: string; updatedAt: string }
export type CuerpoEditarOtro = CuerpoEditarCompra & { concepto: string }
export function useEditarCompra(): UseMutationResult<unknown, unknown, { idCompra: number; cuerpo: CuerpoEditarCompra }>
export function useEditarOtro(): UseMutationResult<unknown, unknown, { idCompraOtro: number; cuerpo: CuerpoEditarOtro }>
export type CuerpoLoteCompras = { lineas: { idCom: number; idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number }[]; solicitudes: { urgentes: number[]; preventivas: number[] } }
export type CuerpoLoteOtros = { lineas: { idProv: number; concepto: string; cantidad: number; esUrgente: boolean; precioUnidad: number }[] }
export function useGuardarLoteCompras(): UseMutationResult<{ idsCreados: number[] }, unknown, { cuerpo: CuerpoLoteCompras; clave: string }>
export function useGuardarLoteOtros(): UseMutationResult<{ idsCreados: number[] }, unknown, { cuerpo: CuerpoLoteOtros; clave: string }>
export function useRecargaPedidos(): () => void
```

Todas las mutaciones llevan `meta: { silenciarError: true }` (el 409, el 422 y el resto los traduce la vista o el formulario; el corte de conexión lo sigue avisando el `MutationCache`, `queryClient.ts:59-69`). Transiciones y `PUT` recargan en `onSettled` (con éxito o error, spec §7); los lotes solo en `onSuccess` (el formulario enseña el error inline y sigue abierto). La recarga invalida `['compras']` (las dos tablas), `['componentes']` (Stock actual y cualquier consulta bajo ese prefijo) y `['notificaciones']` (la campana entera: contador, solicitudes que el lote marca GESTIONADA y alertas), literales porque un módulo no importa de otro.

> Búsqueda previa: no hay consultas de compras en la web (`grep -rn "/api/compras" src --include=*.ts --include=*.tsx | grep -v schema.d.ts` solo da `stock/api.ts:25`, la cantidad en camino).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/pedidos/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { crearQueryClient } from '@/shared/api/queryClient'
import { server } from '@/test/server'
import {
  useCompras, useComprasOtros, useEditarCompra, useEditarOtro, useGuardarLoteCompras, useGuardarLoteOtros, useRecargaPedidos,
  useTransicionPedido, type AccionTransicion,
} from './api'

const U = '2026-09-19T08:00:00'
const compra = (o: Partial<CompraComponente> = {}): CompraComponente => ({
  idCompra: 2, idCom: 12, tipoComponente: 'bat-x', idProv: 2, nombreProveedor: 'Proveedor B', cantidad: 5, cantidadRecibida: null,
  esUrgente: false, fechaPedido: U, fechaLlegada: null, precioUnidadPedido: 10, divisa: 'USD', precioEur: 8.8, estado: 'en_camino',
  updatedAt: U, ...o,
})
const otro = (o: Partial<CompraOtro> = {}): CompraOtro => ({
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: U, fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2, estado: 'en_camino',
  updatedAt: U, ...o,
})

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

type Peticion = { metodo: string; ruta: string; cuerpo: unknown; clave: string | null }
/** Registra método, ruta, cuerpo JSON (null sin cuerpo) y cabecera Idempotency-Key de cada petición que casa con `patron`. */
function registrar(patron: string, respuesta: () => Response = () => new HttpResponse(null, { status: 200 })) {
  const peticiones: Peticion[] = []
  server.use(
    http.all(patron, async ({ request }) => {
      const texto = await request.text()
      peticiones.push({ metodo: request.method, ruta: new URL(request.url).pathname, cuerpo: texto ? JSON.parse(texto) : null, clave: request.headers.get('Idempotency-Key') })
      return respuesta()
    }),
  )
  return peticiones
}

describe('consultas', () => {
  it('useCompras lee GET /api/compras y lleva los cancelados al final', async () => {
    server.use(http.get('*/api/compras', () => HttpResponse.json([compra({ idCompra: 9, estado: 'cancelado' }), compra({ idCompra: 2 })])))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useCompras({ activo: true }), { wrapper })
    await waitFor(() => expect(result.current.data).toBeDefined())
    expect(result.current.data?.map((p) => p.idCompra)).toEqual([2, 9])
  })
  it('useComprasOtros lee GET /api/compras-otros; con habilitada: false no pide nada', async () => {
    let peticiones = 0
    server.use(http.get('*/api/compras-otros', () => { peticiones += 1; return HttpResponse.json([otro({ idCompraOtro: 8, estado: 'cancelado' }), otro()]) }))
    const { wrapper } = envoltorio()
    const { result: apagada } = renderHook(() => useComprasOtros({ activo: false, habilitada: false }), { wrapper })
    expect(apagada.current.fetchStatus).toBe('idle')
    const { result } = renderHook(() => useComprasOtros({ activo: true }), { wrapper })
    await waitFor(() => expect(result.current.data?.map((p) => p.idCompraOtro)).toEqual([7, 8]))
    expect(peticiones).toBe(1)
  })
})

describe('useTransicionPedido', () => {
  it.each([
    ['confirmar', undefined, 'PATCH', '/api/compras/2/confirmar', { updatedAt: U }],
    ['confirmar-recibido', undefined, 'PATCH', '/api/compras/2/confirmar-recibido', { updatedAt: U }],
    ['confirmar-parcial', 1, 'PATCH', '/api/compras/2/confirmar-parcial', { cantidadRecibida: 1, updatedAt: U }],
    ['recibir-resto', 4, 'PATCH', '/api/compras/2/recibir-resto', { cantidadExtra: 4, updatedAt: U }],
    ['confirmar-alterado', undefined, 'PATCH', '/api/compras/2/confirmar-alterado', { updatedAt: U }],
    ['cancelar', undefined, 'PATCH', '/api/compras/2/cancelar', { updatedAt: U }],
    ['desrecibir', undefined, 'PATCH', '/api/compras/2/desrecibir', { updatedAt: U }],
    ['borrar', undefined, 'DELETE', '/api/compras/2', null],
  ] as const)('%s de componentes: %s → %s %s con el cuerpo de hoy', async (accion, cantidad, metodo, ruta, cuerpo) => {
    const peticiones = registrar('*/api/compras/*')
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useTransicionPedido('componentes'), { wrapper })
    await result.current.mutateAsync({ accion: accion as AccionTransicion, pedido: compra(), cantidad })
    expect(peticiones).toEqual([{ metodo, ruta, cuerpo, clave: null }])
  })
  it('en otros va a /api/compras-otros/{id}/…', async () => {
    const peticiones = registrar('*/api/compras-otros/*')
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useTransicionPedido('otros'), { wrapper })
    await result.current.mutateAsync({ accion: 'recibir-resto', pedido: otro({ estado: 'parcial', cantidadRecibida: 1 }), cantidad: 2 })
    await result.current.mutateAsync({ accion: 'borrar', pedido: otro({ estado: 'pendiente' }) })
    expect(peticiones).toEqual([
      { metodo: 'PATCH', ruta: '/api/compras-otros/7/recibir-resto', cuerpo: { cantidadExtra: 2, updatedAt: U }, clave: null },
      { metodo: 'DELETE', ruta: '/api/compras-otros/7', cuerpo: null, clave: null },
    ])
  })
  it('también con error (409) recarga compras, componentes y campana', async () => {
    registrar('*/api/compras/*', () => HttpResponse.json({ message: 'El pedido ya no está pendiente' }, { status: 409 }))
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['compras', 'componentes'], [])
    qc.setQueryData(['componentes', 'gestionados'], [])
    qc.setQueryData(['notificaciones', 'contador'], 0)
    qc.setQueryData(['proveedores', 'COMPONENTES'], [])
    const { result } = renderHook(() => useTransicionPedido('componentes'), { wrapper })
    await expect(result.current.mutateAsync({ accion: 'confirmar', pedido: compra({ estado: 'pendiente' }) })).rejects.toBeInstanceOf(StaleDataError)
    expect(qc.getQueryState(['compras', 'componentes'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['componentes', 'gestionados'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['notificaciones', 'contador'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['proveedores', 'COMPONENTES'])?.isInvalidated).toBe(false)
  })
})

describe('editar (PUT, spec 4b §6 y P3)', () => {
  const cuerpo = { idProv: 2, cantidad: 3, esUrgente: true, precioUnidad: 10, divisa: 'USD', updatedAt: U }
  it('useEditarCompra manda el cuerpo con precioEur: null (lo calcula el servidor) y recarga', async () => {
    const peticiones = registrar('*/api/compras/*')
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['compras', 'componentes'], [])
    const { result } = renderHook(() => useEditarCompra(), { wrapper })
    await result.current.mutateAsync({ idCompra: 2, cuerpo })
    expect(peticiones).toEqual([{ metodo: 'PUT', ruta: '/api/compras/2', cuerpo: { ...cuerpo, precioEur: null }, clave: null }])
    expect(qc.getQueryState(['compras', 'componentes'])?.isInvalidated).toBe(true)
  })
  it('useEditarOtro manda también el concepto a /api/compras-otros/{id}', async () => {
    const peticiones = registrar('*/api/compras-otros/*')
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useEditarOtro(), { wrapper })
    await result.current.mutateAsync({ idCompraOtro: 7, cuerpo: { ...cuerpo, concepto: 'Cinta de embalar' } })
    expect(peticiones).toEqual([{ metodo: 'PUT', ruta: '/api/compras-otros/7', cuerpo: { ...cuerpo, concepto: 'Cinta de embalar', precioEur: null }, clave: null }])
  })
})

describe('lotes (spec 4b P5)', () => {
  it('useGuardarLoteCompras: POST /api/compras/lote con Idempotency-Key, devuelve los ids creados y recarga', async () => {
    const peticiones = registrar('*/api/compras/lote', () => HttpResponse.json({ idsCreados: [21, 22] }))
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['notificaciones', 'solicitudes'], {})
    const { result } = renderHook(() => useGuardarLoteCompras(), { wrapper })
    const cuerpo = {
      lineas: [
        { idCom: 11, idProv: 1, cantidad: 2, esUrgente: false, precioUnidad: 12.5 },
        { idCom: 12, idProv: 2, cantidad: 1, esUrgente: true, precioUnidad: 10 },
      ],
      solicitudes: { urgentes: [31], preventivas: [41] },
    }
    expect(await result.current.mutateAsync({ cuerpo, clave: 'clave-1' })).toEqual({ idsCreados: [21, 22] })
    expect(peticiones).toEqual([{ metodo: 'POST', ruta: '/api/compras/lote', cuerpo, clave: 'clave-1' }])
    expect(qc.getQueryState(['notificaciones', 'solicitudes'])?.isInvalidated).toBe(true)
  })
  it('useGuardarLoteOtros: POST /api/compras-otros/lote con Idempotency-Key', async () => {
    const peticiones = registrar('*/api/compras-otros/lote', () => HttpResponse.json({ idsCreados: [31] }))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useGuardarLoteOtros(), { wrapper })
    const cuerpo = { lineas: [{ idProv: 1, concepto: 'Cinta de embalar', cantidad: 3, esUrgente: false, precioUnidad: 2 }] }
    expect(await result.current.mutateAsync({ cuerpo, clave: 'clave-2' })).toEqual({ idsCreados: [31] })
    expect(peticiones).toEqual([{ metodo: 'POST', ruta: '/api/compras-otros/lote', cuerpo, clave: 'clave-2' }])
  })
  it('un lote rechazado (422) no recarga: el formulario sigue abierto con su error', async () => {
    registrar('*/api/compras-otros/lote', () => HttpResponse.json({ message: 'Línea 1: el proveedor está desactivado.' }, { status: 422 }))
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['compras', 'otros'], [])
    const { result } = renderHook(() => useGuardarLoteOtros(), { wrapper })
    const cuerpo = { lineas: [{ idProv: 3, concepto: 'Bolsas', cantidad: 1, esUrgente: false, precioUnidad: 0 }] }
    await expect(result.current.mutateAsync({ cuerpo, clave: 'clave-3' })).rejects.toBeInstanceOf(ReglaNegocioError)
    expect(qc.getQueryState(['compras', 'otros'])?.isInvalidated).toBe(false)
  })
})

describe('useRecargaPedidos', () => {
  it('invalida compras, componentes y campana; no toca proveedores', () => {
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['compras', 'otros'], [])
    qc.setQueryData(['componentes', 'gestionados'], [])
    qc.setQueryData(['notificaciones', 'componentes'], [])
    qc.setQueryData(['proveedores', 'COMPONENTES'], [])
    const { result } = renderHook(() => useRecargaPedidos(), { wrapper })
    result.current()
    expect(qc.getQueryState(['compras', 'otros'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['componentes', 'gestionados'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['notificaciones', 'componentes'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['proveedores', 'COMPONENTES'])?.isInvalidated).toBe(false)
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/api.test.tsx`
Expected: FAIL, `Failed to resolve import "./api"`.

- [ ] **Step 3: Implementar**

`src/modules/almacen/pedidos/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query'
import { useCallback } from 'react'
import { api, type CompraComponente, type CompraOtro } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'
import { ordenarCanceladosAlFinal } from './filtros'
import { esCompra, type Pedido, type TipoPedido } from './reglas'

export const CLAVE_COMPRAS = ['compras'] as const
export function claveCompras(tipo: TipoPedido): readonly ['compras', TipoPedido] {
  return ['compras', tipo] as const
}

type OpcionesConsulta = { activo: boolean; habilitada?: boolean }

/** GET /api/compras (los tres roles). `activo = false` congela el sondeo y el refetch por foco con un menú, un diálogo o un
 *  formulario abiertos; `habilitada = false` no la pide (la página solo consulta la tabla del toggle visible, spec §5). */
export function useCompras({ activo, habilitada = true }: OpcionesConsulta): UseQueryResult<CompraComponente[]> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: claveCompras('componentes'),
    queryFn: async () => ordenarCanceladosAlFinal((await api.GET('/api/compras')).data ?? []),
    enabled: habilitada,
    refetchInterval: intervalo,
    refetchOnWindowFocus: activo,
  })
}

/** GET /api/compras-otros (los tres roles), con las mismas reglas que useCompras. */
export function useComprasOtros({ activo, habilitada = true }: OpcionesConsulta): UseQueryResult<CompraOtro[]> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: claveCompras('otros'),
    queryFn: async () => ordenarCanceladosAlFinal((await api.GET('/api/compras-otros')).data ?? []),
    enabled: habilitada,
    refetchInterval: intervalo,
    refetchOnWindowFocus: activo,
  })
}

/** Recarga tras escribir (spec 4b §7): las dos tablas de Pedidos, Stock actual (`['componentes', …]`) y la campana entera
 *  (`['notificaciones', …]`: contador, solicitudes que el lote marca GESTIONADA y alertas de stock). Literales: un módulo no
 *  importa de otro. El JavaFX recarga stock solo en algunas acciones; aquí siempre (diferencia inocua, §10). */
export function useRecargaPedidos(): () => void {
  const qc = useQueryClient()
  return useCallback(() => {
    void qc.invalidateQueries({ queryKey: CLAVE_COMPRAS })
    void qc.invalidateQueries({ queryKey: ['componentes'] })
    void qc.invalidateQueries({ queryKey: ['notificaciones'] })
  }, [qc])
}

export type AccionTransicion = 'confirmar' | 'confirmar-recibido' | 'confirmar-parcial' | 'recibir-resto' | 'confirmar-alterado' | 'cancelar' | 'desrecibir' | 'borrar'

function exigirCantidad(cantidad: number | undefined): number {
  if (cantidad === undefined) throw new Error('Falta la cantidad de la recepción')
  return cantidad
}

/** Cuerpos de hoy (inventario §7.1): `{updatedAt}`, `{cantidadRecibida, updatedAt}`, `{cantidadExtra, updatedAt}`;
 *  el DELETE va sin cuerpo (ni updatedAt). Una llamada por ruta literal para que openapi-fetch tipe cada cuerpo. */
async function transicionCompra(accion: AccionTransicion, p: CompraComponente, cantidad: number | undefined) {
  const params = { path: { idCompra: p.idCompra } }
  const updatedAt = p.updatedAt
  switch (accion) {
    case 'confirmar':
      return api.PATCH('/api/compras/{idCompra}/confirmar', { params, body: { updatedAt } })
    case 'confirmar-recibido':
      return api.PATCH('/api/compras/{idCompra}/confirmar-recibido', { params, body: { updatedAt } })
    case 'confirmar-parcial':
      return api.PATCH('/api/compras/{idCompra}/confirmar-parcial', { params, body: { cantidadRecibida: exigirCantidad(cantidad), updatedAt } })
    case 'recibir-resto':
      return api.PATCH('/api/compras/{idCompra}/recibir-resto', { params, body: { cantidadExtra: exigirCantidad(cantidad), updatedAt } })
    case 'confirmar-alterado':
      return api.PATCH('/api/compras/{idCompra}/confirmar-alterado', { params, body: { updatedAt } })
    case 'cancelar':
      return api.PATCH('/api/compras/{idCompra}/cancelar', { params, body: { updatedAt } })
    case 'desrecibir':
      return api.PATCH('/api/compras/{idCompra}/desrecibir', { params, body: { updatedAt } })
    case 'borrar':
      return api.DELETE('/api/compras/{idCompra}', { params })
  }
}

async function transicionOtro(accion: AccionTransicion, p: CompraOtro, cantidad: number | undefined) {
  const params = { path: { id: p.idCompraOtro } }
  const updatedAt = p.updatedAt
  switch (accion) {
    case 'confirmar':
      return api.PATCH('/api/compras-otros/{id}/confirmar', { params, body: { updatedAt } })
    case 'confirmar-recibido':
      return api.PATCH('/api/compras-otros/{id}/confirmar-recibido', { params, body: { updatedAt } })
    case 'confirmar-parcial':
      return api.PATCH('/api/compras-otros/{id}/confirmar-parcial', { params, body: { cantidadRecibida: exigirCantidad(cantidad), updatedAt } })
    case 'recibir-resto':
      return api.PATCH('/api/compras-otros/{id}/recibir-resto', { params, body: { cantidadExtra: exigirCantidad(cantidad), updatedAt } })
    case 'confirmar-alterado':
      return api.PATCH('/api/compras-otros/{id}/confirmar-alterado', { params, body: { updatedAt } })
    case 'cancelar':
      return api.PATCH('/api/compras-otros/{id}/cancelar', { params, body: { updatedAt } })
    case 'desrecibir':
      return api.PATCH('/api/compras-otros/{id}/desrecibir', { params, body: { updatedAt } })
    case 'borrar':
      return api.DELETE('/api/compras-otros/{id}', { params })
  }
}

/** Las ocho transiciones del menú. El 409 (genérico o el de desrecibir) y el 422 de parcial/resto los traduce la página,
 *  así que silencia el diálogo global; recarga siempre, también con error (calco: el 409 "recarga los datos"). */
export function useTransicionPedido(tipo: TipoPedido): UseMutationResult<unknown, unknown, { accion: AccionTransicion; pedido: Pedido; cantidad?: number }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ accion, pedido, cantidad }: { accion: AccionTransicion; pedido: Pedido; cantidad?: number }) => {
      if (tipo === 'componentes') {
        if (!esCompra(pedido)) throw new Error('El pedido no es de componentes')
        return transicionCompra(accion, pedido, cantidad)
      }
      if (esCompra(pedido)) throw new Error('El pedido no es de otros')
      return transicionOtro(accion, pedido, cantidad)
    },
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export type CuerpoEditarCompra = { idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number; divisa: string; updatedAt: string }
export type CuerpoEditarOtro = CuerpoEditarCompra & { concepto: string }

/** PUT del editor (spec 4b §6). `precioEur` va a null: el servidor lo calcula e ignora el de la petición (P3). */
export function useEditarCompra(): UseMutationResult<unknown, unknown, { idCompra: number; cuerpo: CuerpoEditarCompra }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ idCompra, cuerpo }: { idCompra: number; cuerpo: CuerpoEditarCompra }) =>
      api.PUT('/api/compras/{idCompra}', { params: { path: { idCompra } }, body: { ...cuerpo, precioEur: null } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export function useEditarOtro(): UseMutationResult<unknown, unknown, { idCompraOtro: number; cuerpo: CuerpoEditarOtro }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ idCompraOtro, cuerpo }: { idCompraOtro: number; cuerpo: CuerpoEditarOtro }) =>
      api.PUT('/api/compras-otros/{id}', { params: { path: { id: idCompraOtro } }, body: { ...cuerpo, precioEur: null } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export type CuerpoLoteCompras = {
  lineas: { idCom: number; idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number }[]
  solicitudes: { urgentes: number[]; preventivas: number[] }
}
export type CuerpoLoteOtros = { lineas: { idProv: number; concepto: string; cantidad: number; esUrgente: boolean; precioUnidad: number }[] }

/** Alta por lotes (P5): una petición con `Idempotency-Key` (la da el formulario con crearClavesIdempotencia; la misma clave
 *  y el mismo cuerpo devuelven la respuesta guardada sin escribir). Recarga solo si sale bien. */
export function useGuardarLoteCompras(): UseMutationResult<{ idsCreados: number[] }, unknown, { cuerpo: CuerpoLoteCompras; clave: string }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: async ({ cuerpo, clave }: { cuerpo: CuerpoLoteCompras; clave: string }) =>
      (await api.POST('/api/compras/lote', { params: { header: { 'Idempotency-Key': clave } }, body: cuerpo })).data ?? { idsCreados: [] },
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}

export function useGuardarLoteOtros(): UseMutationResult<{ idsCreados: number[] }, unknown, { cuerpo: CuerpoLoteOtros; clave: string }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: async ({ cuerpo, clave }: { cuerpo: CuerpoLoteOtros; clave: string }) =>
      (await api.POST('/api/compras-otros/lote', { params: { header: { 'Idempotency-Key': clave } }, body: cuerpo })).data ?? { idsCreados: [] },
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}
```

Comprobar contra el contrato regenerado antes de ejecutar: `grep -n -A6 '^        LoteComprasPeticion: {\|^        LoteComprasOtrosPeticion: {\|^        LoteComprasRespuesta: {' src/shared/api/schema.d.ts`. Si `solicitudes` es `LoteComprasSolicitudes | null` o `idCom`/`idProv` de la línea son `number | null`, `CuerpoLoteCompras` sigue siendo asignable. Si `precioEur` de las `EditarRequest` quedó opcional, quitar `precioEur: null` de los dos `PUT` y de sus dos expectativas del test, y anotarlo.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/api.test.tsx`
Expected: PASS, 18 tests (acumulado: 1183).

- [ ] **Step 5: Lint y tipos**

```bash
npm run lint && npm run typecheck
```

Expected: sin errores (`typecheck` valida las 16 rutas literales contra `schema.d.ts`).

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/api.ts src/modules/almacen/pedidos/api.test.tsx
git commit -m "feat(pedidos): consultas de compras y otros, transiciones, edicion, lotes con clave de idempotencia y recargas"
```

---

## Task 11: Web — columnas de las dos tablas, `BadgeEstadoPedido`, clase de fila y CSV

**Files:**
- Create: `src/modules/almacen/pedidos/BadgeEstadoPedido.tsx`
- Create: `src/modules/almacen/pedidos/columnas.tsx`, `src/modules/almacen/pedidos/columnas.test.tsx`

**Interfaces:**
- Consumes: `formatear`, `FMT_FECHA_PEDIDO` de `@/shared/lib/fechas`; `formatearImporte`, `formatearNumero`, `simboloDivisa` de `@/shared/lib/importes` (Task 7); `textoCantidad`, `totalFila`, `marcaPrecioCero`, `marcaTotalCero`, `llevaAviso`, `Pedido` de `./reglas` (Task 9); tokens de la Task 7.
- Produces:

```ts
export const ANCHOS_PEDIDOS: { fecha: 115; componente: 190; proveedor: 130; cantidad: 50; precio: 68; eur: 68; estado: 110 }
export const ANCHOS_OTROS: { fecha: 115; concepto: 220; proveedor: 130; cantidad: 60; precio: 80; eur: 80; estado: 110 }
export function crearColumnasPedidos({ onComponente }: { onComponente: (p: CompraComponente) => void }): ColumnDef<CompraComponente>[]
export function crearColumnasOtros(): ColumnDef<CompraOtro>[]
export function claseFilaPedido(p: Pedido): string
export const CABECERAS_CSV_PEDIDOS: string[]
export function filaCsvPedido(p: CompraComponente): string[]
export const CABECERAS_CSV_OTROS: string[]
export function filaCsvOtro(p: CompraOtro): string[]
export function BadgeEstadoPedido({ pedido }: { pedido: Pedido }): JSX.Element   // en BadgeEstadoPedido.tsx
```

Anchos: prefWidth de `StockView.fxml:141-163` (inventario §4). Sin columna Div. (oculta en el JavaFX). La franja izquierda de 8 px va, como en Stock y en el resto de tablas (`stock/columnas.tsx:20-27`, `taller/asignaciones/columnas.tsx:40`), en la clase del `<tr>` (`border-l-8` + color); `DataTable` la vuelve transparente en la fila seleccionada (`DataTable.tsx:324`, `data-[state=selected]:border-l-transparent`) y el `opacity-45` del cancelado sigue aplicando sobre el navy, como la desactivada de Stock (`stock/columnas.test.tsx:63-70`). El badge y sus clases van en su propio fichero porque un `.tsx` de módulo no puede exportar componente y helpers a la vez (react-refresh, como `BadgeEstadoStock`).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/pedidos/columnas.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { DataTable } from '@/shared/ui/DataTable'
import { BadgeEstadoPedido } from './BadgeEstadoPedido'
import { CABECERAS_CSV_OTROS, CABECERAS_CSV_PEDIDOS, claseFilaPedido, crearColumnasOtros, crearColumnasPedidos, filaCsvOtro, filaCsvPedido } from './columnas'

const compra = (o: Partial<CompraComponente> = {}): CompraComponente => ({
  idCompra: 1, idCom: 11, tipoComponente: 'lcd-x-negro', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 5, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR', precioEur: 12.5,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})
const otro = (o: Partial<CompraOtro> = {}): CompraOtro => ({
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2,
  estado: 'recibido', updatedAt: '2026-09-20T08:30:00', ...o,
})

function montarPedidos(filas: CompraComponente[], onComponente = vi.fn()) {
  const r = render(<DataTable columns={crearColumnasPedidos({ onComponente })} data={filas} vacio="Sin pedidos" getRowId={(p) => String(p.idCompra)} filaClase={claseFilaPedido} />)
  return { ...r, onComponente }
}
const celdas = (container: HTMLElement, columna: string) => Array.from(container.querySelectorAll(`[data-columna="${columna}"]`)).map((c) => c.textContent)

describe('tabla de componentes', () => {
  it('siete cabeceras en orden, sin Div., con los anchos del FXML', () => {
    const { container } = montarPedidos([compra()])
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Pedido', 'Componente', 'Proveedor', 'Cant.', 'P.Unit.', 'EUR', 'Estado'])
    const cols = container.querySelectorAll('col')
    expect(cols[0]).toHaveStyle({ width: '115px' })
    expect(cols[1]).toHaveStyle({ width: '190px' })
    expect(cols[6]).toHaveStyle({ width: '110px' })
  })
  it('Pedido en dd/MM/yy HH:mm de Madrid y Cant. según el estado', () => {
    const { container } = montarPedidos([
      compra({ idCompra: 1, estado: 'parcial', cantidad: 10, cantidadRecibida: 3 }),
      compra({ idCompra: 2, estado: 'parcial', cantidad: 10, cantidadRecibida: null, fechaPedido: '2026-01-15T10:00:00' }),
      compra({ idCompra: 3, estado: 'recibido', cantidadRecibida: 2 }),
      compra({ idCompra: 4, estado: 'recibido', cantidadRecibida: null }),
      compra({ idCompra: 5, estado: 'en_camino' }),
    ])
    expect(celdas(container, 'fecha')).toEqual(['20/09/26 10:30', '15/01/26 11:00', '20/09/26 10:30', '20/09/26 10:30', '20/09/26 10:30'])
    expect(celdas(container, 'cantidad')).toEqual(['3/10', '10', '2', '5', '5'])
  })
  it('P.Unit. con el símbolo de su divisa y coma decimal; EUR = unidades × precioEur en euros', () => {
    const { container } = montarPedidos([
      compra({ idCompra: 1 }),
      compra({ idCompra: 2, divisa: 'USD', precioUnidadPedido: 10, precioEur: 8.8 }),
      compra({ idCompra: 3, divisa: 'GBP', precioUnidadPedido: 3, precioEur: 3.45 }),
      compra({ idCompra: 4, estado: 'recibido', cantidadRecibida: 2 }),
    ])
    expect(celdas(container, 'precio')).toEqual(['12,50 €', '10,00 $', '3,00 GBP', '12,50 €'])
    expect(celdas(container, 'eur')).toEqual(['62,50 €', '44,00 €', '17,25 €', '25,00 €'])
  })
  it('"!" ámbar negrita en P.Unit. y EUR solo en un recibido con precio o total 0', () => {
    montarPedidos([compra({ idCompra: 1, estado: 'recibido', precioUnidadPedido: 0, precioEur: 0 }), compra({ idCompra: 2, estado: 'pendiente', precioUnidadPedido: 0, precioEur: 0, tipoComponente: 'bat-x' })])
    const marcados = screen.getAllByText('0,00 €')
    expect(marcados).toHaveLength(4)
    expect(marcados[0]).toHaveClass('font-bold', 'text-fila-solicitud-brd')
    expect(marcados[1]).toHaveClass('font-bold', 'text-fila-solicitud-brd')
    expect(marcados[2]).not.toHaveClass('font-bold')
    const avisos = screen.getAllByText('!')
    expect(avisos).toHaveLength(2)
    for (const a of avisos) expect(a).toHaveClass('text-[12px]', 'font-bold', 'text-fila-solicitud-brd')
  })
  it('Componente es un enlace con la clase de "En Camino" de Stock y avisa con el pedido', async () => {
    const { onComponente } = montarPedidos([compra({ idCompra: 2, idCom: 12, tipoComponente: 'bat-x' })])
    const enlace = screen.getByRole('button', { name: 'bat-x' })
    expect(enlace).toHaveClass('cursor-pointer', 'text-texto-accion', 'hover:underline')
    await userEvent.click(enlace)
    expect(onComponente).toHaveBeenCalledWith(expect.objectContaining({ idCompra: 2, idCom: 12 }))
  })
})

describe('tabla de otros', () => {
  it('Concepto en vez de Componente, como texto sin enlace, con los anchos del FXML', () => {
    const { container } = render(<DataTable columns={crearColumnasOtros()} data={[otro()]} vacio="Sin otros pedidos" getRowId={(p) => String(p.idCompraOtro)} filaClase={claseFilaPedido} />)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Pedido', 'Concepto', 'Proveedor', 'Cant.', 'P.Unit.', 'EUR', 'Estado'])
    expect(screen.getByText('Cinta de embalar').tagName).not.toBe('BUTTON')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    const cols = container.querySelectorAll('col')
    expect(cols[1]).toHaveStyle({ width: '220px' })
    expect(cols[3]).toHaveStyle({ width: '60px' })
    expect(cols[4]).toHaveStyle({ width: '80px' })
    expect(celdas(container, 'eur')).toEqual(['6,00 €'])
  })
})

describe('badge, "⚠" y clase de fila', () => {
  it('badge con el estado tal cual y los colores de StockController :903-914; "⚠" solo si urgente en camino o parcial', () => {
    const casos: [Partial<CompraComponente>, string[]][] = [
      [{ estado: 'pendiente' }, ['bg-badge-pendiente-bg', 'text-badge-pendiente-text']],
      [{ estado: 'en_camino', esUrgente: true }, ['bg-fila-solicitud-bg', 'text-fila-solicitud-brd']],
      [{ estado: 'en_camino' }, ['bg-badge-neutro-bg', 'text-azul-gris']],
      [{ estado: 'recibido' }, ['bg-fila-recibido-bg', 'text-fila-recibido-brd']],
      [{ estado: 'parcial', esUrgente: true }, ['bg-fila-parcial-bg', 'text-fila-parcial-brd']],
      [{ estado: 'cancelado' }, ['bg-fila-cancelado-bg', 'text-fila-cancelado-text']],
    ]
    for (const [o, clases] of casos) {
      const { unmount } = render(<BadgeEstadoPedido pedido={compra(o)} />)
      const badge = screen.getByText(String(o.estado))
      expect(badge).toHaveClass('rounded-[12px]', 'px-2.5', 'py-[3px]', 'text-[11px]', 'font-bold', ...clases)
      const conAviso = o.esUrgente === true && (o.estado === 'en_camino' || o.estado === 'parcial')
      if (conAviso) expect(screen.getByText('⚠')).toHaveClass('text-[13px]', 'text-fila-solicitud-brd')
      else expect(screen.queryByText('⚠')).not.toBeInTheDocument()
      unmount()
    }
    render(<BadgeEstadoPedido pedido={compra({ estado: 'pendiente', esUrgente: true })} />)
    expect(screen.queryByText('⚠')).not.toBeInTheDocument()
  })
  it('clase de fila: barra de 8 px por estado; en camino solo si es urgente; cancelado sin barra y con opacidad', () => {
    expect(claseFilaPedido(compra({ estado: 'pendiente' }))).toBe('border-l-8 border-l-fila-pendiente-brd')
    expect(claseFilaPedido(compra({ estado: 'en_camino', esUrgente: true }))).toBe('border-l-8 border-l-fila-solicitud-brd')
    expect(claseFilaPedido(compra({ estado: 'en_camino' }))).toBe('border-l-8 border-l-transparent')
    expect(claseFilaPedido(compra({ estado: 'recibido' }))).toBe('border-l-8 border-l-fila-recibido-brd')
    expect(claseFilaPedido(otro({ estado: 'parcial' }))).toBe('border-l-8 border-l-fila-parcial-brd')
    expect(claseFilaPedido(otro({ estado: 'cancelado' }))).toBe('border-l-8 border-l-transparent opacity-45')
  })
})

describe('CSV (StockController :1961-2006)', () => {
  it('componentes: cabeceras exactas, fecha con año completo, cantidad pedida, Sí/No, coma decimal, total con recibidas y estado con guion bajo', () => {
    expect(CABECERAS_CSV_PEDIDOS).toEqual(['Fecha pedido', 'Componente', 'Cantidad', 'Urgente', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado'])
    expect(filaCsvPedido(compra({ estado: 'recibido', cantidadRecibida: 2, esUrgente: true }))).toEqual(['20/09/2026 10:30', 'lcd-x-negro', '5', 'Sí', 'Proveedor A', '12,50', 'EUR', '25,00', 'recibido'])
    expect(filaCsvPedido(compra({ estado: 'en_camino', divisa: 'USD', precioUnidadPedido: 10, precioEur: 8.8 }))).toEqual(['20/09/2026 10:30', 'lcd-x-negro', '5', 'No', 'Proveedor A', '10,00', 'USD', '44,00', 'en_camino'])
  })
  it('otros: sin Urgente', () => {
    expect(CABECERAS_CSV_OTROS).toEqual(['Fecha pedido', 'Concepto', 'Cantidad', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado'])
    expect(filaCsvOtro(otro())).toEqual(['20/09/2026 10:30', 'Cinta de embalar', '3', 'Proveedor A', '2,00', 'EUR', '6,00', 'recibido'])
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/columnas.test.tsx`
Expected: FAIL, `Failed to resolve import "./columnas"`.

- [ ] **Step 3: Implementar el badge**

`src/modules/almacen/pedidos/BadgeEstadoPedido.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { llevaAviso, type Pedido } from './reglas'

const NEUTRO = 'bg-badge-neutro-bg text-azul-gris'
const CLASES: Record<string, string> = {
  pendiente: 'bg-badge-pendiente-bg text-badge-pendiente-text',
  recibido: 'bg-fila-recibido-bg text-fila-recibido-brd',
  parcial: 'bg-fila-parcial-bg text-fila-parcial-brd',
  cancelado: 'bg-fila-cancelado-bg text-fila-cancelado-text',
}

function clasesBadge(p: Pedido): string {
  if (p.estado === 'en_camino') return p.esUrgente ? 'bg-fila-solicitud-bg text-fila-solicitud-brd' : NEUTRO
  return CLASES[p.estado] ?? NEUTRO
}

/** Calco del badge de cpEstado (StockController :885-919): el estado tal cual (`en_camino` con guion bajo), radio 12,
 *  padding 3 10, 11 px negrita; "⚠" de 13 px a 6 px a la derecha si es urgente y está en camino o parcial. Conserva sus
 *  colores en la fila seleccionada, como en el JavaFX. */
export function BadgeEstadoPedido({ pedido }: { pedido: Pedido }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('inline-block rounded-[12px] px-2.5 py-[3px] text-[11px] font-bold', clasesBadge(pedido))}>{pedido.estado}</span>
      {llevaAviso(pedido) && <span className="text-[13px] text-fila-solicitud-brd">⚠</span>}
    </span>
  )
}
```

- [ ] **Step 4: Implementar las columnas**

`src/modules/almacen/pedidos/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { FMT_FECHA_PEDIDO, formatear } from '@/shared/lib/fechas'
import { formatearImporte, formatearNumero, simboloDivisa } from '@/shared/lib/importes'
import { cn } from '@/shared/lib/utils'
import { BadgeEstadoPedido } from './BadgeEstadoPedido'
import { marcaPrecioCero, marcaTotalCero, textoCantidad, totalFila, type Pedido } from './reglas'

/** prefWidth de StockView.fxml :141-149 (componentes) y :156-163 (otros). */
export const ANCHOS_PEDIDOS = { fecha: 115, componente: 190, proveedor: 130, cantidad: 50, precio: 68, eur: 68, estado: 110 } as const
export const ANCHOS_OTROS = { fecha: 115, concepto: 220, proveedor: 130, cantidad: 60, precio: 80, eur: 80, estado: 110 } as const

/** Celdas P.Unit. y EUR (:797-849): el importe y, si hay marca, el importe en ámbar negrita y un "!" de 12 px a 4 px. */
function celdaImporte(texto: string, marca: boolean) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className={cn(marca && 'font-bold text-fila-solicitud-brd')}>{texto}</span>
      {marca && <span className="text-[12px] font-bold text-fila-solicitud-brd">!</span>}
    </span>
  )
}

const precio = (p: Pedido) => celdaImporte(formatearImporte(p.precioUnidadPedido, simboloDivisa(p.divisa)), marcaPrecioCero(p))
const eur = (p: Pedido) => celdaImporte(formatearImporte(totalFila(p), '€'), marcaTotalCero(p))
const fecha = (p: Pedido) => formatear(p.fechaPedido, FMT_FECHA_PEDIDO)

export function crearColumnasPedidos({ onComponente }: { onComponente: (p: CompraComponente) => void }): ColumnDef<CompraComponente>[] {
  return [
    { id: 'fecha', header: 'Pedido', size: ANCHOS_PEDIDOS.fecha, accessorFn: fecha },
    {
      id: 'componente', header: 'Componente', size: ANCHOS_PEDIDOS.componente,
      // Calco del Label con TEXTO_ACCION, cursor mano y subrayado al pasar (:762-780), con la clase del enlace "En Camino"
      // de Stock (stock/columnas.tsx:50): lleva a Stock actual con la fila del componente seleccionada.
      cell: ({ row }) => (
        <button type="button" onClick={(e) => { e.stopPropagation(); onComponente(row.original) }} className="cursor-pointer text-texto-accion hover:underline">
          {row.original.tipoComponente}
        </button>
      ),
    },
    { id: 'proveedor', header: 'Proveedor', size: ANCHOS_PEDIDOS.proveedor, accessorFn: (p) => p.nombreProveedor },
    { id: 'cantidad', header: 'Cant.', size: ANCHOS_PEDIDOS.cantidad, accessorFn: textoCantidad },
    { id: 'precio', header: 'P.Unit.', size: ANCHOS_PEDIDOS.precio, cell: ({ row }) => precio(row.original) },
    { id: 'eur', header: 'EUR', size: ANCHOS_PEDIDOS.eur, cell: ({ row }) => eur(row.original) },
    { id: 'estado', header: 'Estado', size: ANCHOS_PEDIDOS.estado, cell: ({ row }) => <BadgeEstadoPedido pedido={row.original} /> },
  ]
}

/** Tabla de otros (:1046-1126): Concepto es un Label sin enlace. */
export function crearColumnasOtros(): ColumnDef<CompraOtro>[] {
  return [
    { id: 'fecha', header: 'Pedido', size: ANCHOS_OTROS.fecha, accessorFn: fecha },
    { id: 'concepto', header: 'Concepto', size: ANCHOS_OTROS.concepto, accessorFn: (p) => p.concepto },
    { id: 'proveedor', header: 'Proveedor', size: ANCHOS_OTROS.proveedor, accessorFn: (p) => p.nombreProveedor },
    { id: 'cantidad', header: 'Cant.', size: ANCHOS_OTROS.cantidad, accessorFn: textoCantidad },
    { id: 'precio', header: 'P.Unit.', size: ANCHOS_OTROS.precio, cell: ({ row }) => precio(row.original) },
    { id: 'eur', header: 'EUR', size: ANCHOS_OTROS.eur, cell: ({ row }) => eur(row.original) },
    { id: 'estado', header: 'Estado', size: ANCHOS_OTROS.estado, cell: ({ row }) => <BadgeEstadoPedido pedido={row.original} /> },
  ]
}

/** Calco del rowFactory (:853-882): barra izquierda de 8 px por estado (pendiente ámbar a mano, en camino solo si urgente,
 *  recibido verde, parcial violeta) y el cancelado sin barra con opacidad 0.45 en toda la fila. La seleccionada la pinta
 *  DataTable (navy, texto crema, barra transparente); la opacidad del cancelado prevalece, como la desactivada de Stock. */
export function claseFilaPedido(p: Pedido): string {
  switch (p.estado) {
    case 'pendiente':
      return 'border-l-8 border-l-fila-pendiente-brd'
    case 'en_camino':
      return p.esUrgente ? 'border-l-8 border-l-fila-solicitud-brd' : 'border-l-8 border-l-transparent'
    case 'recibido':
      return 'border-l-8 border-l-fila-recibido-brd'
    case 'parcial':
      return 'border-l-8 border-l-fila-parcial-brd'
    case 'cancelado':
      return 'border-l-8 border-l-transparent opacity-45'
    default:
      return 'border-l-8 border-l-transparent'
  }
}

/** Calco de exportarPedidos (:1961-1983): las filas filtradas en el orden mostrado; Cantidad = pedida; total con la regla
 *  de unidades de la columna EUR; importes con `%.2f` (coma, sin símbolo); Estado = `name()`. Fecha en Madrid con año
 *  completo. */
export const CABECERAS_CSV_PEDIDOS = ['Fecha pedido', 'Componente', 'Cantidad', 'Urgente', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado']
export function filaCsvPedido(p: CompraComponente): string[] {
  return [
    formatear(p.fechaPedido, 'dd/MM/yyyy HH:mm'), p.tipoComponente, String(p.cantidad), p.esUrgente ? 'Sí' : 'No', p.nombreProveedor,
    formatearNumero(p.precioUnidadPedido), p.divisa, formatearNumero(totalFila(p)), p.estado,
  ]
}

/** Calco de exportarOtros (:1985-2006): sin Urgente. */
export const CABECERAS_CSV_OTROS = ['Fecha pedido', 'Concepto', 'Cantidad', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado']
export function filaCsvOtro(p: CompraOtro): string[] {
  return [
    formatear(p.fechaPedido, 'dd/MM/yyyy HH:mm'), p.concepto, String(p.cantidad), p.nombreProveedor,
    formatearNumero(p.precioUnidadPedido), p.divisa, formatearNumero(totalFila(p)), p.estado,
  ]
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/columnas.test.tsx`
Expected: PASS, 10 tests (acumulado: 1193).

- [ ] **Step 6: Lint y typecheck**

Run: `npm run lint && npm run typecheck`
Expected: sin errores ni avisos de `react-refresh/only-export-components` (`BadgeEstadoPedido.tsx` solo exporta el componente; `columnas.tsx` no exporta componentes); `tsc -b` limpio (el `tsconfig.app.json` no lleva `DOM.Iterable`: un `[...NodeList]` daría TS2488, por eso los tests usan `Array.from`).

- [ ] **Step 7: Commit**

```bash
git add src/modules/almacen/pedidos/BadgeEstadoPedido.tsx src/modules/almacen/pedidos/columnas.tsx src/modules/almacen/pedidos/columnas.test.tsx
git commit -m "feat(pedidos): columnas de componentes y otros, enlace al componente, marcas de precio cero, badge con aviso, clase de fila y csv"
```

---
## Task 12: Web — `CantidadDialog` y `MenuPedido`

**Files:**
- Create: `src/modules/almacen/pedidos/CantidadDialog.tsx`, `src/modules/almacen/pedidos/CantidadDialog.test.tsx`
- Create: `src/modules/almacen/pedidos/MenuPedido.tsx`, `src/modules/almacen/pedidos/MenuPedido.test.tsx`

**Interfaces:**
- Consumes: `DialogoAlmacen` (`ui/DialogoAlmacen.tsx:21-40`: título, `subtitulo` con `whitespace-pre`, error en `role="alert"`, "Cancelar" y la acción; Enter confirma) y `useErrorServidor` (`ui/useErrorServidor.ts:6-15`); `Input`, `Label`; `ContextMenuItem`, `ContextMenuSeparator` de `@/shared/ui/context-menu`; de `./reglas`: `idPedido`, `nombrePedido`, `restante`, `validarParcial`, `validarResto`, `entradasMenu`, `AccionMenu`, `Pedido` (Task 9).
- Produces:

```ts
export function CantidadDialog({ modo, pedido, errorServidor, enviando, onConfirmar, onCancelar }: { modo: 'parcial' | 'resto'; pedido: Pedido | null; errorServidor: string | null; enviando: boolean; onConfirmar: (cantidad: number) => void; onCancelar: () => void }): JSX.Element
export function MenuPedido({ pedido, onAccion, onInteraccion }: { pedido: Pedido; onAccion: (accion: AccionMenu, pedido: Pedido) => void; onInteraccion?: (abierto: boolean) => void }): JSX.Element | null
```

`onInteraccion` es opcional y se añade a la firma de el documento de interfaces del reparto (fuera del repo) (desviación 3): es el mismo aviso de montar/desmontar que usa `MenuComponente` (`stock/MenuComponente.tsx:13-24`) para congelar el sondeo con el menú abierto; sin él la página no se entera de que el menú está abierto.

Textos (spec §6, P6/P7): "Recepción parcial" con subtítulo `Pedido #{id} — {componente} ({cantidad} pedidas)`, etiqueta "Cantidad recibida ahora:", campo vacío; "Recibir unidades" con subtítulo `Pedido #{id} — {componente} (recibidas: {recibida}/{cantidad})` + salto + `Si introduces {restante}, el pedido se cerrará como recibido.`, etiqueta "Cantidad que llega ahora:", campo con el restante. Botón "Confirmar" en los dos (la spec lo fija para el parcial; el resto va igual, desviación 4). En otros `{componente}` es el concepto (`nombrePedido`).

- [ ] **Step 1: Test del diálogo (falla)**

`src/modules/almacen/pedidos/CantidadDialog.test.tsx`:

```tsx
import { getDefaultNormalizer, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { renderConProviders } from '@/test/render'
import { CantidadDialog } from './CantidadDialog'

const sinColapsar = { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) }
const enCamino: CompraComponente = {
  idCompra: 2, idCom: 12, tipoComponente: 'bat-x', idProv: 2, nombreProveedor: 'Proveedor B', cantidad: 10, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-19T08:00:00', fechaLlegada: null, precioUnidadPedido: 10, divisa: 'USD', precioEur: 8.8,
  estado: 'en_camino', updatedAt: '2026-09-19T08:00:00',
}
const parcial: CompraOtro = {
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 10, cantidadRecibida: 3,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2,
  estado: 'parcial', updatedAt: '2026-09-20T08:30:00',
}

function montar(modo: 'parcial' | 'resto', pedido: CompraComponente | CompraOtro | null, errorServidor: string | null = null) {
  const onConfirmar = vi.fn()
  renderConProviders(<CantidadDialog modo={modo} pedido={pedido} errorServidor={errorServidor} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
  return onConfirmar
}

describe('CantidadDialog (spec 4b §6, P6)', () => {
  it('"Recepción parcial": subtítulo con las pedidas, etiqueta y campo vacío con foco; confirma el entero', async () => {
    const onConfirmar = montar('parcial', enCamino)
    const dlg = within(screen.getByRole('dialog', { name: 'Recepción parcial' }))
    expect(dlg.getByText('Pedido #2 — bat-x (10 pedidas)')).toBeInTheDocument()
    const campo = dlg.getByLabelText('Cantidad recibida ahora:')
    expect(campo).toHaveValue('')
    expect(campo).toHaveFocus()
    expect(dlg.getByRole('button', { name: 'Confirmar' })).toBeInTheDocument()
    await userEvent.type(campo, '4{Enter}')
    expect(onConfirmar).toHaveBeenCalledWith(4)
  })
  it('"Recepción parcial": "Cantidad no válida." si no es un número y el rango con la cantidad pedida; no confirma', async () => {
    const onConfirmar = montar('parcial', enCamino)
    const campo = screen.getByLabelText('Cantidad recibida ahora:')
    await userEvent.type(campo, 'abc{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Cantidad no válida.')
    await userEvent.clear(campo)
    await userEvent.type(campo, '10{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('La cantidad debe ser mayor que 0 y menor que 10.')
    expect(onConfirmar).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: 'Recepción parcial' })).toBeInTheDocument()
  })
  it('"Recibir unidades": subtítulo en dos líneas con el concepto en otros y campo precargado con el restante', async () => {
    const onConfirmar = montar('resto', parcial)
    const dlg = within(screen.getByRole('dialog', { name: 'Recibir unidades' }))
    expect(dlg.getByText('Pedido #7 — Cinta de embalar (recibidas: 3/10)\nSi introduces 7, el pedido se cerrará como recibido.', sinColapsar)).toBeInTheDocument()
    const campo = dlg.getByLabelText('Cantidad que llega ahora:')
    expect(campo).toHaveValue('7')
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(onConfirmar).toHaveBeenCalledWith(7)
  })
  it('"Recibir unidades": mayor que 0 y sin pasar de lo pedido', async () => {
    const onConfirmar = montar('resto', parcial)
    const campo = screen.getByLabelText('Cantidad que llega ahora:')
    await userEvent.clear(campo)
    await userEvent.type(campo, '0{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('La cantidad debe ser mayor que 0.')
    await userEvent.clear(campo)
    await userEvent.type(campo, '8{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('No puedes recibir más de lo pedido. Faltan 7 unidad(es).')
    expect(onConfirmar).not.toHaveBeenCalled()
  })
  it('un 422 del servidor se pinta inline y se oculta al teclear', async () => {
    montar('resto', parcial, 'No puedes recibir más de lo pedido. Faltan 7 unidad(es).')
    expect(screen.getByRole('alert')).toHaveTextContent('No puedes recibir más de lo pedido. Faltan 7 unidad(es).')
    await userEvent.type(screen.getByLabelText('Cantidad que llega ahora:'), '1')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/CantidadDialog.test.tsx`
Expected: FAIL, `Failed to resolve import "./CantidadDialog"`.

- [ ] **Step 3: Implementar el diálogo**

`src/modules/almacen/pedidos/CantidadDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { useErrorServidor } from '../ui/useErrorServidor'
import { idPedido, nombrePedido, restante, validarParcial, validarResto, type Pedido } from './reglas'

type Props = {
  modo: 'parcial' | 'resto'
  pedido: Pedido | null
  errorServidor: string | null
  enviando: boolean
  onConfirmar: (cantidad: number) => void
  onCancelar: () => void
}

/** Cabecera del TextInputDialog (StockController :1506-1507 y :1539-1541). La de "Recibir unidades" corrige el "o más"
 *  que el cliente no permite (P7); una recibida nula (no debería en `parcial`) se pinta 0. */
function subtitulo(modo: 'parcial' | 'resto', p: Pedido): string {
  const cabecera = `Pedido #${idPedido(p)} — ${nombrePedido(p)}`
  if (modo === 'parcial') return `${cabecera} (${p.cantidad} pedidas)`
  return `${cabecera} (recibidas: ${p.cantidadRecibida ?? 0}/${p.cantidad})\nSi introduces ${restante(p)}, el pedido se cerrará como recibido.`
}

/** "Recepción parcial" y "Recibir unidades" (spec 4b §6, P6): los TextInputDialog y los Alert WARNING del JavaFX pasan a
 *  DialogoAlmacen con el error en su línea; el diálogo sigue abierto hasta que la cantidad vale. Un 422 del servidor
 *  (`errorServidor`) se pinta en la misma línea si no hay error local y se oculta al teclear. */
export function CantidadDialog({ modo, pedido, errorServidor, enviando, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  const [error, setError] = useState<string | null>(null)
  const servidor = useErrorServidor(errorServidor)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el campo al abrir con otro pedido (patrón de EditarStockDialog)
    if (pedido) { setTexto(modo === 'resto' ? String(restante(pedido)) : ''); setError(null) }
  }, [pedido, modo])
  function confirmar() {
    if (!pedido) return
    const r = modo === 'parcial' ? validarParcial(texto, pedido.cantidad) : validarResto(texto, pedido.cantidadRecibida, pedido.cantidad)
    if (!r.ok) { setError(r.error); return }
    setError(null)
    onConfirmar(r.valor)
  }
  const id = `cantidad-pedido-${modo}`
  return (
    <DialogoAlmacen
      abierto={pedido !== null}
      titulo={modo === 'parcial' ? 'Recepción parcial' : 'Recibir unidades'}
      subtitulo={pedido ? subtitulo(modo, pedido) : undefined}
      error={error ?? servidor.error}
      textoAccion="Confirmar"
      enviando={enviando}
      onConfirmar={confirmar}
      onCancelar={onCancelar}
    >
      <Label htmlFor={id} className="text-[12px] font-bold text-azul-gris">{modo === 'parcial' ? 'Cantidad recibida ahora:' : 'Cantidad que llega ahora:'}</Label>
      <Input id={id} value={texto} onChange={(e) => { setTexto(e.target.value); servidor.ocultar() }} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/CantidadDialog.test.tsx`
Expected: PASS, 5 tests.

- [ ] **Step 5: Test del menú (falla)**

`src/modules/almacen/pedidos/MenuPedido.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import { describe, expect, it, vi } from 'vitest'
import type { CompraComponente } from '@/shared/api/client'
import { DataTable } from '@/shared/ui/DataTable'
import { MenuPedido } from './MenuPedido'
import { idPedido, nombrePedido, type AccionMenu, type Pedido } from './reglas'

const pedido = (o: Partial<CompraComponente> = {}): CompraComponente => ({
  idCompra: 1, idCom: 11, tipoComponente: 'lcd-x-negro', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 5, cantidadRecibida: null,
  esUrgente: false, fechaPedido: '2026-09-20T08:30:00', fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR', precioEur: 12.5,
  estado: 'pendiente', updatedAt: '2026-09-20T08:30:00', ...o,
})
const COLUMNAS: ColumnDef<Pedido>[] = [{ id: 'nombre', header: 'Nombre', accessorFn: nombrePedido }]

function montar(p: Pedido, onAccion: (a: AccionMenu, p: Pedido) => void = vi.fn(), onInteraccion?: (abierto: boolean) => void) {
  render(<DataTable columns={COLUMNAS} data={[p]} vacio="" getRowId={(x) => String(idPedido(x))} menuFila={(x) => <MenuPedido pedido={x} onAccion={onAccion} onInteraccion={onInteraccion} />} />)
}
const abrir = () => userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('lcd-x-negro') })

describe('MenuPedido (spec 4b §6, StockController :970-1010)', () => {
  it.each([
    ['pendiente', ['Confirmar pedido', 'Editar', 'Borrar'], 1],
    ['en_camino', ['Recepción parcial', 'Confirmar recibido', 'Editar', 'Cancelar pedido'], 1],
    ['parcial', ['Recibir resto', 'Cerrar sin resto'], 0],
    ['recibido', ['Revertir a En camino', 'Editar'], 1],
  ])('%s: entradas en orden y separadores', async (estado, textos, separadores) => {
    montar(pedido({ estado }))
    await abrir()
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(textos)
    expect(screen.queryAllByRole('separator')).toHaveLength(separadores)
  })
  it('cancelado: sin entradas', async () => {
    montar(pedido({ estado: 'cancelado' }))
    await abrir()
    expect(screen.queryAllByRole('menuitem')).toHaveLength(0)
  })
  it('elegir una entrada avisa con la acción y el pedido', async () => {
    const onAccion = vi.fn()
    const p = pedido({ estado: 'en_camino' })
    montar(p, onAccion)
    await abrir()
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recepción parcial' }))
    expect(onAccion).toHaveBeenCalledWith('parcial', p)
  })
  it('abrir y cerrar el menú avisa a onInteraccion (congela el sondeo de la página)', async () => {
    const onInteraccion = vi.fn()
    montar(pedido(), vi.fn(), onInteraccion)
    await abrir()
    expect(onInteraccion).toHaveBeenLastCalledWith(true)
    await userEvent.keyboard('{Escape}')
    expect(onInteraccion).toHaveBeenLastCalledWith(false)
  })
})
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/MenuPedido.test.tsx`
Expected: FAIL, `Failed to resolve import "./MenuPedido"`.

- [ ] **Step 7: Implementar el menú**

`src/modules/almacen/pedidos/MenuPedido.tsx`:

```tsx
import { useEffect } from 'react'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import { entradasMenu, type AccionMenu, type Pedido } from './reglas'

type Props = {
  pedido: Pedido
  onAccion: (accion: AccionMenu, pedido: Pedido) => void
  /** Aviso de menú abierto/cerrado para congelar el sondeo (D4 del 3a), como MenuComponente. Estable entre renders. */
  onInteraccion?: (abierto: boolean) => void
}

/** Contenido del menú contextual de una fila de Pedidos (solo SUPERTECNICO; la página no pasa menuFila a ADMIN ni TECNICO).
 *  Entradas y separadores por estado de `entradasMenu`; cancelado no tiene ninguna. */
export function MenuPedido({ pedido, onAccion, onInteraccion }: Props) {
  // Radix monta el contenido solo mientras el menú está abierto: montar/desmontar = abrir/cerrar.
  useEffect(() => {
    if (!onInteraccion) return
    onInteraccion(true)
    return () => onInteraccion(false)
  }, [onInteraccion])
  const entradas = entradasMenu(pedido.estado)
  if (entradas.length === 0) return null
  return (
    <>
      {entradas.map((e, i) =>
        e === 'separador' ? (
          <ContextMenuSeparator key={`sep-${i}`} />
        ) : (
          <ContextMenuItem key={e.accion} onSelect={() => onAccion(e.accion, pedido)}>{e.texto}</ContextMenuItem>
        ),
      )}
    </>
  )
}
```

- [ ] **Step 8: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/MenuPedido.test.tsx`
Expected: PASS, 7 tests.

- [ ] **Step 9: Lint**

Run: `npm run lint`
Expected: sin errores.

- [ ] **Step 10: Commit**

```bash
git add src/modules/almacen/pedidos/CantidadDialog.tsx src/modules/almacen/pedidos/CantidadDialog.test.tsx src/modules/almacen/pedidos/MenuPedido.tsx src/modules/almacen/pedidos/MenuPedido.test.tsx
git commit -m "feat(pedidos): dialogos de recepcion parcial y recibir unidades con error inline, y menu contextual por estado"
```

Acumulado: 1205 tests.

---
## Task 13: Web — `PedidosPage` y rutas `/stock/pedidos` y `/stock/pedidos/otros`

**Files:**
- Modify: `src/shared/ui/DataTable.tsx:31-32` (doc de `menuFila`) y `:353-360` (una fila cuyo `menuFila` devuelve `null` no lleva menú), `src/shared/ui/DataTable.test.tsx` (test tras el de la l.191-202)
- Modify: `src/shared/ui/ConfirmDialog.tsx:45` (`whitespace-pre-line` en la descripción), `src/shared/ui/ConfirmDialog.test.tsx` (import de la l.1 y un test al final)
- Create: `src/modules/almacen/pedidos/PedidosPage.tsx`, `src/modules/almacen/pedidos/PedidosPage.test.tsx`
- Modify: `src/app/router.tsx:2-3` (import) y `:70` (rutas)

**Interfaces:**
- Consumes: todo lo de las Tasks 7-12; `useProveedoresComponentes` de `../proveedores/api` (`proveedores/api.ts:9-17`, clave `['proveedores','COMPONENTES']`, mismo módulo); `ultimaRutaStock` de `../estado`; `formularioPedido`, `abrirNuevoPedido`, `abrirNuevoOtroPedido` de `@/shared/lib/formularioPedido` (Task 8); `useInteraccionesAbiertas`, `useStore`, `descargarCsv`, `useRegistrarExportable`, `DataTable` (`menuFila`, `filaClase`, `getRowId`, `seleccionada`, `onSeleccionar`, `pedirDesplazamiento`), `TogglePill`, `MultiSelect`, `RangoFechas`, `ConfirmDialog`, `EtiquetaActualizado`, `Input`, `BotonPrimario`/`BotonSecundario`; `mensajeDeError`, `esErrorGestionadoGlobalmente`, `ReglaNegocioError`, `StaleDataError` de `@/shared/api/errors` (tras la T7: `mensajeDeError` en `errors.ts:79-82` y `esErrorGestionadoGlobalmente` en `:88-90`).
- Produces:
  - `export function PedidosPage({ tipo }: { tipo: TipoPedido }): JSX.Element`.
  - Rutas `{ path: '/stock/pedidos', element: <PedidosPage key="componentes" tipo="componentes" /> }` y `{ path: '/stock/pedidos/otros', element: <PedidosPage key="otros" tipo="otros" /> }` (con `key`, desviación 5).
  - Hueco de T17: `const [editando, setEditando] = useState<Pedido | null>(null)` y `case 'editar': setEditando(pedido)` con el comentario `// T17: editores`. `editando` ya cuenta como diálogo abierto (congela el sondeo), así que T17 solo tiene que pintar `EditarPedidoDialog`/`EditarOtroPedidoDialog` con `pedido={editando}` (según `tipo`) y `onCerrar={() => setEditando(null)}`.
  - `DataTable`: si `menuFila` devuelve `null`, esa fila no se envuelve en `ContextMenu` (sin menú vacío). `ConfirmDialog`: la descripción respeta los saltos de línea.

Comportamiento (spec §6-§8): cabecera con "Pedidos" y el toggle; barra de filtros; tabla del toggle visible (la otra no se consulta); menú solo SUPERTECNICO; "Confirmar pedido", "Confirmar recibido" y "Cerrar sin resto" escriben al pulsar; "Recepción parcial" y "Recibir resto" abren `CantidadDialog` (un 422 va inline y el diálogo sigue abierto; un 409 lo cierra y avisa); "Cancelar pedido", "Borrar" y "Revertir a En camino" piden `ConfirmDialog` con `confirmacionDe`; un 409 de cualquier transición avisa **"Este pedido fue modificado por otro usuario. Los datos se han recargado."** salvo desrecibir, que enseña el mensaje del servidor (StockController :1635); todas recargan (Task 10). Llegada con `?estados&buscar`: aplica al store, selecciona la primera fila filtrada, pide el desplazamiento y limpia la URL con `replace`. "Actualizado HH:mm" recarga la tabla visible (P7). CSV `pedidos` / `pedidos_otros`.

> Búsqueda previa: `grep -rn "PedidosPage" src` sale vacío; `/stock/pedidos` es hoy `<PendienteDeMigrar nombre="Pedidos" />` (`router.tsx:70`). El enlace "Pedidos" del lateral (`almacen/rutas.ts:9`) no lleva `end`, así que sigue activo en `/stock/pedidos/otros`. La franja de 8 px sigue el patrón de `border-l-8` en el `<tr>` de todas las tablas (desviación 6).

- [ ] **Step 1: `DataTable` sin menú en las filas cuyo `menuFila` devuelve `null` (test que falla)**

En `src/shared/ui/DataTable.test.tsx`, tras el test "el menú contextual recibe la columna pulsada y puede resaltar la celda" (l.191-202):

```tsx
  it('una fila cuyo menuFila devuelve null no lleva menú contextual (p. ej. un pedido cancelado)', async () => {
    render(
      <DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id}
        menuFila={(f) => (f.id === '2' ? null : <ContextMenuItem>Acción</ContextMenuItem>)} />,
    )
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('b') })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('a') })
    expect(await screen.findByRole('menuitem', { name: 'Acción' })).toBeInTheDocument()
  })
```

Run: `npx vitest run src/shared/ui/DataTable.test.tsx`
Expected: FAIL en el test nuevo: la fila "OTRO" abre un menú vacío (`role="menu"` presente).

- [ ] **Step 2: Implementar**

En `src/shared/ui/DataTable.tsx`, doc de la prop (l.31):

```ts
  /** Menú contextual de la fila (equivale al ContextMenu del TableView). Si devuelve null, esa fila no lleva menú: el
   *  TableView no muestra un ContextMenu sin entradas (p. ej. un pedido cancelado). */
```

y sustituir las l.353-360 por:

```tsx
    if (!menuFila) return tr
    const celda: CeldaPulsada = { columnaId: columnaPulsada, resaltar: () => setResaltada({ fila: id, columna: columnaPulsada }) }
    const contenido = menuFila(row.original, celda)
    if (contenido === null) return tr
    return (
      <ContextMenu key={id}>
        <ContextMenuTrigger asChild>{tr}</ContextMenuTrigger>
        <ContextMenuContent>{contenido}</ContextMenuContent>
      </ContextMenu>
    )
```

Run: `npx vitest run src/shared/ui/DataTable.test.tsx`
Expected: PASS, 45 tests (44 + 1).

- [ ] **Step 3: `ConfirmDialog` con saltos de línea (test que falla)**

En `src/shared/ui/ConfirmDialog.test.tsx`, la l.1 pasa a:

```tsx
import { getDefaultNormalizer, render, screen, waitFor, within } from '@testing-library/react'
```

y, antes del `})` final del `describe`:

```tsx
  it('la descripción respeta los saltos de línea (Revertir a En camino de Pedidos, tres líneas)', () => {
    const texto = '¿Revertir el pedido #4 de cam-x a En camino?\nSe descontarán 2 unidad(es) del stock.\nRecuerda revisar el stock tras la operación.'
    render(<ConfirmDialog abierto titulo="Revertir a En camino" descripcion={texto} textoAccion="Revertir a En camino" onConfirmar={vi.fn()} onCancelar={vi.fn()} />)
    expect(screen.getByText(texto, { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })).toHaveClass('whitespace-pre-line')
  })
```

Run: `npx vitest run src/shared/ui/ConfirmDialog.test.tsx`
Expected: FAIL, la descripción no tiene `whitespace-pre-line`.

En `src/shared/ui/ConfirmDialog.tsx:45`:

```tsx
          <DialogDescription className="whitespace-pre-line text-[13px] text-azul-medio">{descripcion}</DialogDescription>
```

(`pre-line` conserva los `\n` y sigue colapsando los espacios: las descripciones de una línea de hoy se ven igual.)

Run: `npx vitest run src/shared/ui/ConfirmDialog.test.tsx`
Expected: PASS, 6 tests.

- [ ] **Step 4: Test de la página (falla)**

`src/modules/almacen/pedidos/PedidosPage.test.tsx`:

```tsx
import { act, getDefaultNormalizer, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
// eslint-disable-next-line no-restricted-imports -- "Descargar CSV" vive en el menú de usuario del AppLayout real (patrón de StockPage.test.tsx).
import { AppLayout } from '@/app/shell/AppLayout'
import type { CompraComponente, CompraOtro, Proveedor } from '@/shared/api/client'
import { INTERVALO_CONECTADO_MS } from '@/shared/api/refresco'
import * as csv from '@/shared/lib/csv'
import { abrirNuevoPedido, cerrarFormularioPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import type { Sesion } from '@/shared/session/storage'
import { renderConProviders, renderConRouter, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { ultimaRutaStock } from '../estado'
import { PedidosPage } from './PedidosPage'

const U1 = '2026-09-20T08:30:00'
const U2 = '2026-09-19T08:00:00'
const MSG_MODIFICADO = 'Este pedido fue modificado por otro usuario. Los datos se han recargado.'
const sinColapsar = { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) }

const base: CompraComponente = {
  idCompra: 1, idCom: 11, tipoComponente: 'lcd-x-negro', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 2, cantidadRecibida: null,
  esUrgente: false, fechaPedido: U1, fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR', precioEur: 12.5, estado: 'pendiente',
  updatedAt: U1,
}
const compra = (o: Partial<CompraComponente>): CompraComponente => ({ ...base, ...o })
/** Orden del servidor (fecha desc) con un cancelado en medio: la página lo lleva al final. */
const compras: CompraComponente[] = [
  compra({ idCompra: 1 }),
  compra({ idCompra: 2, idCom: 12, tipoComponente: 'bat-x', idProv: 2, nombreProveedor: 'Proveedor B', estado: 'en_camino', esUrgente: true, divisa: 'USD', precioUnidadPedido: 10, precioEur: 8.8, fechaPedido: U2, updatedAt: U2 }),
  compra({ idCompra: 5, idCom: 15, tipoComponente: 'mc-x', estado: 'cancelado', fechaPedido: '2026-09-18T08:00:00' }),
  compra({ idCompra: 3, idCom: 13, tipoComponente: 'bat-y', estado: 'parcial', cantidad: 10, cantidadRecibida: 3, fechaPedido: '2026-09-17T08:00:00' }),
  compra({ idCompra: 4, idCom: 14, tipoComponente: 'cam-x', estado: 'recibido', cantidadRecibida: 2, fechaPedido: '2026-09-16T08:00:00' }),
]
const otroBase: CompraOtro = {
  idCompraOtro: 7, idProv: 1, nombreProveedor: 'Proveedor A', concepto: 'Cinta de embalar', cantidad: 3, cantidadRecibida: null,
  esUrgente: false, fechaPedido: U1, fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR', precioEur: 2, estado: 'recibido', updatedAt: U1,
}
const otros: CompraOtro[] = [otroBase, { ...otroBase, idCompraOtro: 8, concepto: 'Bolsas', estado: 'pendiente' }]
const proveedor = (idProv: number, nombre: string, activo: boolean, divisa = 'EUR'): Proveedor => ({ idProv, nombre, activo, divisa, comentario: '', tipo: 'COMPONENTES' })
const proveedores = [proveedor(1, 'Proveedor A', true), proveedor(2, 'Proveedor B', true, 'USD'), proveedor(3, 'ACME', false)]
const cargas = { compras: 0, otros: 0 }

beforeEach(() => {
  cargas.compras = 0
  cargas.otros = 0
  server.use(
    http.get('*/api/compras', () => { cargas.compras += 1; return HttpResponse.json(compras) }),
    http.get('*/api/compras-otros', () => { cargas.otros += 1; return HttpResponse.json(otros) }),
    http.get('*/api/proveedores', () => HttpResponse.json(proveedores)),
  )
})
afterEach(() => vi.useRealTimers())

function montar(sesion: Sesion = SESION_SUPER, ruta = '/stock/pedidos') {
  return renderConRouter(
    [
      { path: '/stock/pedidos', element: <PedidosPage key="componentes" tipo="componentes" /> },
      { path: '/stock/pedidos/otros', element: <PedidosPage key="otros" tipo="otros" /> },
      { path: '/stock', element: <p data-testid="stock">Stock</p> },
    ],
    { sesion, ruta },
  )
}
/** Por texto y no por rol: con un diálogo abierto Radix deja el resto aria-hidden. */
const filaDe = (texto: string) => screen.getByText(texto).closest('tr') as HTMLElement
const abrirMenu = (texto: string) => userEvent.pointer({ keys: '[MouseRight]', target: filaDe(texto) })
const nombresEnTabla = () => screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[1].textContent)
/** Registra "id acción cuerpo" de cada PATCH de transición y responde con `respuesta`. */
function registrarPatch(ruta: string, respuesta: () => Response = () => new HttpResponse(null, { status: 200 })) {
  const llamadas: string[] = []
  server.use(http.patch(ruta, async ({ params, request }) => { llamadas.push(`${String(params.id)} ${String(params.accion)} ${JSON.stringify(await request.json())}`); return respuesta() }))
  return llamadas
}
/** Peticiones del AppLayout (campana y lateral) en los tests de CSV; los de taller no se importan (regla de módulos). */
function handlersLayout() {
  server.use(
    http.get('*/api/solicitudes/count', () => HttpResponse.json({ value: 0 })),
    http.get('*/api/solicitudes-stock/count', () => HttpResponse.json({ value: 0 })),
    http.get('*/api/solicitudes', () => HttpResponse.json([])),
    http.get('*/api/solicitudes-stock', () => HttpResponse.json([])),
    http.get('*/api/componentes/gestionados', () => HttpResponse.json([])),
  )
}

describe('PedidosPage: vista', () => {
  it('título, toggle en Componentes, columnas, cancelados al final, pie "Actualizado" y última pestaña de Stock', async () => {
    ultimaRutaStock.set('/stock')
    montar()
    expect(await screen.findByRole('heading', { name: 'Pedidos' })).toBeInTheDocument()
    await screen.findByText('lcd-x-negro')
    expect(screen.getByRole('link', { name: 'Componentes' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Otros' })).not.toHaveAttribute('aria-current')
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Pedido', 'Componente', 'Proveedor', 'Cant.', 'P.Unit.', 'EUR', 'Estado'])
    expect(nombresEnTabla()).toEqual(['lcd-x-negro', 'bat-x', 'bat-y', 'cam-x', 'mc-x'])
    expect(within(filaDe('bat-x')).getByText('en_camino')).toBeInTheDocument()
    expect(within(filaDe('bat-x')).getByText('⚠')).toBeInTheDocument()
    expect(within(filaDe('bat-x')).getByText('10,00 $')).toBeInTheDocument()
    expect(within(filaDe('bat-x')).getByText('19/09/26 10:00')).toBeInTheDocument()
    expect(within(filaDe('bat-y')).getByText('3/10')).toBeInTheDocument()
    expect(filaDe('lcd-x-negro')).toHaveClass('border-l-8', 'border-l-fila-pendiente-brd')
    expect(filaDe('mc-x')).toHaveClass('opacity-45')
    expect(screen.getByRole('button', { name: /^Actualizado \d\d:\d\d$/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nuevo pedido' })).toBeInTheDocument()
    expect(ultimaRutaStock.get()).toBe('/stock/pedidos')
    // Solo se consulta la tabla visible.
    expect(cargas.otros).toBe(0)
  })
  it('el toggle "Otros" cambia de ruta y de tabla, conserva los filtros y fija la última pestaña', async () => {
    const { router } = montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'bat')
    await userEvent.click(screen.getByRole('link', { name: 'Otros' }))
    expect(router.state.location.pathname).toBe('/stock/pedidos/otros')
    expect(screen.getByPlaceholderText('Buscar componente…')).toHaveValue('bat')
    expect(await screen.findByText('Sin otros pedidos')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Pedido', 'Concepto', 'Proveedor', 'Cant.', 'P.Unit.', 'EUR', 'Estado'])
    expect(screen.getByRole('button', { name: 'Nuevo otro pedido' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nuevo pedido' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Otros' })).toHaveAttribute('aria-current', 'page')
    expect(ultimaRutaStock.get()).toBe('/stock/pedidos/otros')
    await userEvent.clear(screen.getByPlaceholderText('Buscar componente…'))
    expect(await screen.findByText('Cinta de embalar')).toBeInTheDocument()
    expect(cargas.otros).toBe(1)
  })
  it.each([['ADMIN', SESION_ADMIN], ['TECNICO', SESION_TEC]] as const)('%s ve la tabla sin "Nuevo pedido" ni menú contextual', async (_rol, sesion) => {
    montar(sesion)
    await screen.findByText('lcd-x-negro')
    expect(screen.queryByRole('button', { name: 'Nuevo pedido' })).not.toBeInTheDocument()
    await abrirMenu('lcd-x-negro')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('filtros: Estado con los cinco chips, Proveedor solo con activos, Desde, buscador y "Limpiar filtros"', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.click(screen.getByRole('button', { name: 'Estado' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['pendiente', 'en camino', 'parcial', 'recibido', 'cancelado'])
    await userEvent.click(screen.getByRole('checkbox', { name: 'en camino' }))
    expect(screen.getByRole('button', { name: 'en camino' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'parcial' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: '2 estados' })).toBeInTheDocument()
    expect(nombresEnTabla()).toEqual(['bat-x', 'bat-y'])
    await userEvent.click(screen.getByRole('button', { name: 'Proveedor' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['Proveedor A', 'Proveedor B'])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Proveedor B' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Proveedor B' })).toBeInTheDocument()
    expect(nombresEnTabla()).toEqual(['bat-x'])
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByRole('button', { name: 'Estado' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Proveedor' })).toBeInTheDocument()
    expect(nombresEnTabla()).toHaveLength(5)
    await userEvent.type(screen.getByLabelText('Desde:'), '2026-09-19')
    expect(nombresEnTabla()).toEqual(['lcd-x-negro', 'bat-x'])
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'zzz')
    expect(screen.getByText('Sin pedidos')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByLabelText('Desde:')).toHaveValue('')
    expect(screen.getByPlaceholderText('Buscar componente…')).toHaveValue('')
  })
  it('el enlace Componente navega a Stock con ?componente=<idCom>', async () => {
    const { router } = montar()
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByRole('button', { name: 'bat-x' }))
    expect(await screen.findByTestId('stock')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/stock')
    expect(router.state.location.search).toBe('?componente=12')
  })
  it('llegada desde "En Camino" de Stock: marca los estados, rellena el buscador, selecciona la primera fila filtrada y limpia la URL', async () => {
    server.use(http.get('*/api/compras', () => HttpResponse.json([compra({ idCompra: 6, idCom: 12, tipoComponente: 'bat-x', estado: 'recibido', fechaPedido: '2026-09-21T08:00:00' }), ...compras])))
    const { router } = montar(SESION_TEC, '/stock/pedidos?estados=pendiente%2Cen+camino%2Cparcial&buscar=bat-x')
    await waitFor(() => expect(document.querySelector('tr[aria-selected="true"]')).toHaveTextContent('19/09/26 10:00'))
    expect(document.querySelector('tr[aria-selected="true"]')).toHaveTextContent('bat-x')
    expect(router.state.location.pathname).toBe('/stock/pedidos')
    expect(router.state.location.search).toBe('')
    expect(screen.getByPlaceholderText('Buscar componente…')).toHaveValue('bat-x')
    expect(screen.getByRole('button', { name: '3 estados' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(2)
  })
})

describe('PedidosPage: menú y acciones (SUPERTECNICO)', () => {
  it('menú por estado; un cancelado no tiene menú', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await abrirMenu('lcd-x-negro')
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Confirmar pedido', 'Editar', 'Borrar'])
    expect(screen.getAllByRole('separator')).toHaveLength(1)
    await userEvent.keyboard('{Escape}')
    await abrirMenu('bat-x')
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Recepción parcial', 'Confirmar recibido', 'Editar', 'Cancelar pedido'])
    await userEvent.keyboard('{Escape}')
    await abrirMenu('mc-x')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('"Confirmar pedido" escribe al pulsar, sin diálogo, y recarga', async () => {
    const llamadas = registrarPatch('*/api/compras/:id/:accion')
    montar()
    await screen.findByText('lcd-x-negro')
    await abrirMenu('lcd-x-negro')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Confirmar pedido' }))
    await waitFor(() => expect(llamadas).toEqual([`1 confirmar {"updatedAt":"${U1}"}`]))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(cargas.compras).toBe(2))
  })
  it('"Confirmar recibido" y "Cerrar sin resto" también escriben al pulsar', async () => {
    const llamadas = registrarPatch('*/api/compras/:id/:accion')
    montar()
    await screen.findByText('bat-x')
    await abrirMenu('bat-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Confirmar recibido' }))
    await waitFor(() => expect(llamadas).toHaveLength(1))
    await abrirMenu('bat-y')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cerrar sin resto' }))
    await waitFor(() => expect(llamadas).toEqual([`2 confirmar-recibido {"updatedAt":"${U2}"}`, `3 confirmar-alterado {"updatedAt":"${U1}"}`]))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('"Recepción parcial": diálogo con sus textos, validación local y PATCH con la cantidad; se cierra al terminar', async () => {
    const llamadas = registrarPatch('*/api/compras/:id/:accion')
    montar()
    await screen.findByText('bat-x')
    await abrirMenu('bat-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recepción parcial' }))
    const dlg = screen.getByRole('dialog', { name: 'Recepción parcial' })
    expect(within(dlg).getByText('Pedido #2 — bat-x (2 pedidas)')).toBeInTheDocument()
    const campo = within(dlg).getByLabelText('Cantidad recibida ahora:')
    await userEvent.type(campo, 'abc{Enter}')
    expect(within(dlg).getByRole('alert')).toHaveTextContent('Cantidad no válida.')
    await userEvent.clear(campo)
    await userEvent.type(campo, '2{Enter}')
    expect(within(dlg).getByRole('alert')).toHaveTextContent('La cantidad debe ser mayor que 0 y menor que 2.')
    expect(llamadas).toEqual([])
    await userEvent.clear(campo)
    await userEvent.type(campo, '1{Enter}')
    await waitFor(() => expect(llamadas).toEqual([`2 confirmar-parcial {"cantidadRecibida":1,"updatedAt":"${U2}"}`]))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('"Recibir resto": un 422 del servidor se pinta inline y el diálogo sigue abierto, sin aviso global', async () => {
    const msg = 'No puedes recibir más de lo pedido. Faltan 7 unidad(es).'
    registrarPatch('*/api/compras/:id/:accion', () => HttpResponse.json({ message: msg }, { status: 422 }))
    montar()
    await screen.findByText('bat-y')
    await abrirMenu('bat-y')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recibir resto' }))
    const dlg = screen.getByRole('dialog', { name: 'Recibir unidades' })
    expect(within(dlg).getByLabelText('Cantidad que llega ahora:')).toHaveValue('7')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Confirmar' }))
    expect(await within(dlg).findByRole('alert')).toHaveTextContent(msg)
    expect(screen.getByRole('dialog', { name: 'Recibir unidades' })).toBeInTheDocument()
    expect(screen.getAllByText(msg)).toHaveLength(1)
  })
  it('"Cancelar pedido": confirmación con sus textos (dos botones que empiezan por "Cancelar") y PATCH', async () => {
    const llamadas = registrarPatch('*/api/compras/:id/:accion')
    montar()
    await screen.findByText('bat-x')
    await abrirMenu('bat-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cancelar pedido' }))
    const dlg = screen.getByRole('dialog', { name: 'Cancelar pedido' })
    expect(within(dlg).getByText('¿Cancelar el pedido #2 de bat-x?')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Cancelar' })).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Cancelar pedido' }))
    await waitFor(() => expect(llamadas).toEqual([`2 cancelar {"updatedAt":"${U2}"}`]))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('"Borrar": confirmación y DELETE sin cuerpo', async () => {
    const borrados: string[] = []
    server.use(http.delete('*/api/compras/:id', ({ params }) => { borrados.push(String(params.id)); return new HttpResponse(null, { status: 200 }) }))
    montar()
    await screen.findByText('lcd-x-negro')
    await abrirMenu('lcd-x-negro')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Borrar' }))
    const dlg = screen.getByRole('dialog', { name: 'Borrar pedido' })
    expect(within(dlg).getByText('¿Borrar el pedido pendiente #1 de lcd-x-negro?')).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(borrados).toEqual(['1']))
  })
  it('"Revertir a En camino" de componentes: tres líneas; un 409 enseña el mensaje del servidor y recarga', async () => {
    const msg = 'Stock insuficiente para deshacer la recepción (stock actual: 1, a descontar: 2)'
    const llamadas = registrarPatch('*/api/compras/:id/:accion', () => HttpResponse.json({ message: msg }, { status: 409 }))
    montar()
    await screen.findByText('cam-x')
    const antes = cargas.compras
    await abrirMenu('cam-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Revertir a En camino' }))
    const dlg = screen.getByRole('dialog', { name: 'Revertir a En camino' })
    expect(within(dlg).getByText('¿Revertir el pedido #4 de cam-x a En camino?\nSe descontarán 2 unidad(es) del stock.\nRecuerda revisar el stock tras la operación.', sinColapsar)).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Revertir a En camino' }))
    expect(await screen.findByText(msg)).toBeInTheDocument()
    expect(llamadas).toEqual([`4 desrecibir {"updatedAt":"${U1}"}`])
    expect(screen.queryByText(MSG_MODIFICADO)).not.toBeInTheDocument()
    await waitFor(() => expect(cargas.compras).toBeGreaterThan(antes))
  })
  it('"Revertir a En camino" de otros: una sola línea y la ruta de otros', async () => {
    const llamadas = registrarPatch('*/api/compras-otros/:id/:accion')
    montar(SESION_SUPER, '/stock/pedidos/otros')
    await screen.findByText('Cinta de embalar')
    await abrirMenu('Cinta de embalar')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Revertir a En camino' }))
    const dlg = screen.getByRole('dialog', { name: 'Revertir a En camino' })
    expect(within(dlg).getByText('¿Revertir el pedido #7 de Cinta de embalar a En camino?', sinColapsar)).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Revertir a En camino' }))
    await waitFor(() => expect(llamadas).toEqual([`7 desrecibir {"updatedAt":"${U1}"}`]))
  })
  it('un 409 en cualquier otra transición avisa "Este pedido fue modificado por otro usuario…" y recarga', async () => {
    registrarPatch('*/api/compras/:id/:accion', () => HttpResponse.json({ message: 'El pedido ya no está pendiente' }, { status: 409 }))
    montar()
    await screen.findByText('lcd-x-negro')
    await abrirMenu('lcd-x-negro')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Confirmar pedido' }))
    expect(await screen.findByText(MSG_MODIFICADO)).toBeInTheDocument()
    expect(screen.queryByText('El pedido ya no está pendiente')).not.toBeInTheDocument()
    await waitFor(() => expect(cargas.compras).toBe(2))
  })
  it('"Nuevo pedido" y "Nuevo otro pedido" abren el formulario de alta en el sitio (store)', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo pedido' }))
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'vacio' } })
    act(() => cerrarFormularioPedido())
    await userEvent.click(screen.getByRole('link', { name: 'Otros' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo otro pedido' }))
    expect(formularioPedido.get()).toEqual({ tipo: 'otro' })
  })
})

describe('PedidosPage: refresco y CSV', () => {
  it('la selección sobrevive al refresco de 60 s', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('cam-x')
    // Clic en Proveedor: en Componente está el enlace, que navega.
    await userEvent.click(within(filaDe('cam-x')).getAllByRole('cell')[2])
    expect(filaDe('cam-x')).toHaveAttribute('aria-selected', 'true')
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS) })
    await waitFor(() => expect(cargas.compras).toBe(2))
    expect(filaDe('cam-x')).toHaveAttribute('aria-selected', 'true')
  })
  it('con un diálogo abierto el sondeo se congela', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('bat-x')
    await abrirMenu('bat-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recepción parcial' }))
    expect(screen.getByRole('dialog', { name: 'Recepción parcial' })).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS * 2) })
    expect(cargas.compras).toBe(1)
  })
  it('con el formulario de alta abierto el sondeo se congela y vuelve al cerrarlo', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('lcd-x-negro')
    act(() => abrirNuevoPedido({ modo: 'vacio' }))
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS * 2) })
    expect(cargas.compras).toBe(1)
    act(() => cerrarFormularioPedido())
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS) })
    await waitFor(() => expect(cargas.compras).toBeGreaterThan(1))
  })
  it('"Actualizado HH:mm" recarga la tabla visible (Otros), no la de componentes (P7)', async () => {
    montar(SESION_TEC, '/stock/pedidos/otros')
    await screen.findByText('Cinta de embalar')
    expect(cargas.otros).toBe(1)
    await userEvent.click(screen.getByRole('button', { name: /^Actualizado \d\d:\d\d$/ }))
    await waitFor(() => expect(cargas.otros).toBe(2))
    expect(cargas.compras).toBe(0)
  })
  it('Descargar CSV en Componentes exporta la lista filtrada con el nombre y las cabeceras del JavaFX', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    handlersLayout()
    renderConProviders(<PedidosPage key="componentes" tipo="componentes" />, { sesion: SESION_SUPER, ruta: '/stock/pedidos', layout: <AppLayout /> })
    await screen.findByText('lcd-x-negro')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'bat')
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [nombre, cabeceras, filas] = descargar.mock.calls[0]
    expect(nombre).toBe('pedidos')
    expect(cabeceras).toEqual(['Fecha pedido', 'Componente', 'Cantidad', 'Urgente', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado'])
    expect(filas).toEqual([
      ['19/09/2026 10:00', 'bat-x', '2', 'Sí', 'Proveedor B', '10,00', 'USD', '17,60', 'en_camino'],
      ['17/09/2026 10:00', 'bat-y', '10', 'No', 'Proveedor A', '12,50', 'EUR', '125,00', 'parcial'],
    ])
    descargar.mockRestore()
  })
  it('Descargar CSV en Otros exporta pedidos_otros sin Urgente', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    handlersLayout()
    renderConProviders(<PedidosPage key="otros" tipo="otros" />, { sesion: SESION_SUPER, ruta: '/stock/pedidos/otros', layout: <AppLayout /> })
    await screen.findByText('Cinta de embalar')
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [nombre, cabeceras, filas] = descargar.mock.calls[0]
    expect(nombre).toBe('pedidos_otros')
    expect(cabeceras).toEqual(['Fecha pedido', 'Concepto', 'Cantidad', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado'])
    expect(filas).toEqual([
      ['20/09/2026 10:30', 'Cinta de embalar', '3', 'Proveedor A', '2,00', 'EUR', '6,00', 'recibido'],
      ['20/09/2026 10:30', 'Bolsas', '3', 'Proveedor A', '2,00', 'EUR', '6,00', 'pendiente'],
    ])
    descargar.mockRestore()
  })
})
```

- [ ] **Step 5: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.test.tsx`
Expected: FAIL, `Failed to resolve import "./PedidosPage"`.

- [ ] **Step 6: Implementar la página**

`src/modules/almacen/pedidos/PedidosPage.tsx`:

```tsx
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { abrirNuevoOtroPedido, abrirNuevoPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { Input } from '@/shared/ui/input'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { TogglePill } from '@/shared/ui/TogglePill'
import { ultimaRutaStock } from '../estado'
import { useProveedoresComponentes } from '../proveedores/api'
import { useCompras, useComprasOtros, useTransicionPedido, type AccionTransicion } from './api'
import { CantidadDialog } from './CantidadDialog'
import { CABECERAS_CSV_OTROS, CABECERAS_CSV_PEDIDOS, claseFilaPedido, crearColumnasOtros, crearColumnasPedidos, filaCsvOtro, filaCsvPedido } from './columnas'
import { confirmacionDe } from './confirmaciones'
import { filtrosPedidos, seleccionPedidos } from './estado'
import { aplicarFiltrosPedidos, FILTROS_PEDIDOS_VACIOS, filtrosDesdeStock } from './filtros'
import { MenuPedido } from './MenuPedido'
import { chipDeEstado, entradasMenu, ESTADOS_PEDIDO, idPedido, type AccionMenu, type EstadoPedido, type Pedido, type TipoPedido } from './reglas'

/** mostrarConflicto() de StockController :1929-1933. */
const MSG_MODIFICADO = 'Este pedido fue modificado por otro usuario. Los datos se han recargado.'
const OPCIONES_TOGGLE = [
  { to: '/stock/pedidos', etiqueta: 'Componentes' },
  { to: '/stock/pedidos/otros', etiqueta: 'Otros' },
]
const RUTA: Record<TipoPedido, string> = { componentes: '/stock/pedidos', otros: '/stock/pedidos/otros' }

type AccionDirecta = 'confirmar' | 'recibido' | 'cerrarSinResto'
type AccionConfirmada = 'cancelar' | 'borrar' | 'revertir'
/** Entrada del menú → endpoint (inventario §7.1). */
const TRANSICION: Record<AccionDirecta | AccionConfirmada, AccionTransicion> = {
  confirmar: 'confirmar',
  recibido: 'confirmar-recibido',
  cerrarSinResto: 'confirmar-alterado',
  cancelar: 'cancelar',
  borrar: 'borrar',
  revertir: 'desrecibir',
}

type DialogoCantidad = { modo: 'parcial' | 'resto'; pedido: Pedido } | null
type Confirmar = { accion: AccionConfirmada; pedido: Pedido } | null

/** Pestaña "Pedidos" de StockView.fxml (spec 4b §6): toggle Componentes | Otros por rutas (P4), cuatro filtros compartidos,
 *  la tabla del toggle visible, menú de transiciones (SUPERTECNICO), CSV y "Actualizado". */
export function PedidosPage({ tipo }: { tipo: TipoPedido }) {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const navigate = useNavigate()
  const location = useLocation()
  const { mostrarError } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [formulario] = useStore(formularioPedido)
  const [filtros, setFiltros] = useStore(filtrosPedidos)
  const [seleccionada, setSeleccionada] = useStore(seleccionPedidos[tipo])
  const [dialogoCantidad, setDialogoCantidad] = useState<DialogoCantidad>(null)
  const [confirmar, setConfirmar] = useState<Confirmar>(null)
  // T17: editores (EditarPedidoDialog / EditarOtroPedidoDialog se pintan con este estado; ya congela el sondeo)
  const [editando, setEditando] = useState<Pedido | null>(null)
  // Texto de un 422 del servidor para el diálogo de cantidad abierto (spec §8): se pinta dentro, que sigue abierto.
  const [errorServidor, setErrorServidor] = useState<string | null>(null)
  const [peticionDesplazamiento, setPeticionDesplazamiento] = useState(0)

  // Sondeo congelado con un menú, un desplegable, un diálogo, un editor o el formulario de alta abiertos (spec §7-§8, D4).
  const activo = !hayAlguna && formulario === null
  const compras = useCompras({ activo: activo && tipo === 'componentes', habilitada: tipo === 'componentes' })
  const otros = useComprasOtros({ activo: activo && tipo === 'otros', habilitada: tipo === 'otros' })
  // Proveedores del filtro: la misma consulta que la pestaña Proveedores; aquí no sondea.
  const proveedores = useProveedoresComponentes({ activo: false })
  const transicion = useTransicionPedido(tipo)

  // Última pestaña de Stock para el botón de la barra superior (caché de vista del JavaFX, S2).
  useEffect(() => { ultimaRutaStock.set(RUTA[tipo]) }, [tipo])
  const hayModal = dialogoCantidad !== null || confirmar !== null || editando !== null
  useEffect(() => {
    if (!hayModal) return
    marcar(true)
    return () => marcar(false)
  }, [hayModal, marcar])

  // Llegada desde "En Camino" de Stock (S8, navegarAPedidosDeComponente :217-231): los parámetros se leen una vez al montar,
  // se aplican al store (proveedor y fechas intactos), se limpia la URL con replace y, en cuanto hay datos, se selecciona la
  // primera fila filtrada y se desplaza a ella. Los refs evitan repetirlo si el efecto vuelve a correr.
  const [llegada] = useState(() => filtrosDesdeStock(new URLSearchParams(location.search)))
  const llegadaAplicada = useRef(false)
  const primeraPendiente = useRef(llegada !== null)
  useEffect(() => {
    if (llegada === null || llegadaAplicada.current) return
    llegadaAplicada.current = true
    filtrosPedidos.set((f) => ({ ...f, ...llegada }))
    navigate(location.pathname, { replace: true })
  }, [llegada, navigate, location.pathname])
  const datos: Pedido[] | undefined = tipo === 'componentes' ? compras.data : otros.data
  useEffect(() => {
    if (!primeraPendiente.current || datos === undefined) return
    primeraPendiente.current = false
    // Se filtra con el store (no con `filtros` del render): si los datos ya estaban en caché, este efecto corre en el mismo
    // commit que el de arriba, antes de que el render vea los filtros nuevos.
    const primera = aplicarFiltrosPedidos(datos, filtrosPedidos.get())[0]
    if (!primera) return
    setSeleccionada(String(idPedido(primera)))
    // eslint-disable-next-line react-hooks/set-state-in-effect -- pide a la tabla desplazarse a la fila recién elegida (select + scrollTo del JavaFX)
    setPeticionDesplazamiento((n) => n + 1)
  }, [datos, setSeleccionada])

  const irAStock = useCallback((p: CompraComponente) => navigate(`/stock?componente=${p.idCom}`), [navigate])
  const columnasPedidos = useMemo(() => crearColumnasPedidos({ onComponente: irAStock }), [irAStock])
  const columnasOtros = useMemo(() => crearColumnasOtros(), [])
  const visiblesCompras = useMemo(() => aplicarFiltrosPedidos(compras.data ?? [], filtros), [compras.data, filtros])
  const visiblesOtros = useMemo(() => aplicarFiltrosPedidos(otros.data ?? [], filtros), [otros.data, filtros])
  // Calco de :1729-1751: el filtro Proveedor solo ofrece los activos.
  const activos = useMemo(() => (proveedores.data ?? []).filter((p) => p.activo), [proveedores.data])

  // Calco de exportarPedidos/exportarOtros (:1961-2006): la tabla visible, filtrada y en el orden mostrado.
  useRegistrarExportable(() => {
    if (tipo === 'componentes') descargarCsv('pedidos', CABECERAS_CSV_PEDIDOS, visiblesCompras.map(filaCsvPedido))
    else descargarCsv('pedidos_otros', CABECERAS_CSV_OTROS, visiblesOtros.map(filaCsvOtro))
  })

  /** 409 → aviso genérico, salvo desrecibir, que enseña el mensaje del servidor (stock insuficiente o estado, :1635). Lo que
   *  gestiona el mecanismo global (401, sin conexión) no se repite. La recarga la hace el onSettled de la mutación. */
  function avisarFallo(accion: AccionTransicion, e: unknown) {
    if (esErrorGestionadoGlobalmente(e)) return
    mostrarError(accion === 'desrecibir' ? mensajeDeError(e) : mensajeDeError(e, { staleData: MSG_MODIFICADO }))
  }

  function transicionar(accion: AccionTransicion, pedido: Pedido) {
    transicion.mutate({ accion, pedido }, { onError: (e) => avisarFallo(accion, e) })
  }

  function alElegir(accion: AccionMenu, pedido: Pedido) {
    switch (accion) {
      case 'confirmar':
      case 'recibido':
      case 'cerrarSinResto':
        transicionar(TRANSICION[accion], pedido)
        return
      case 'parcial':
      case 'resto':
        setErrorServidor(null)
        setDialogoCantidad({ modo: accion, pedido })
        return
      case 'cancelar':
      case 'borrar':
      case 'revertir':
        setConfirmar({ accion, pedido })
        return
      case 'editar':
        // T17: editores
        setEditando(pedido)
        return
    }
  }

  function cerrarCantidad() {
    setDialogoCantidad(null)
    setErrorServidor(null)
  }

  /** 422 → inline con el diálogo abierto; 409 → se cierra y avisa; otro error → diálogo abierto y aviso (patrón de Stock). */
  function confirmarCantidad(valor: number) {
    if (!dialogoCantidad) return
    const { modo, pedido } = dialogoCantidad
    const accion: AccionTransicion = modo === 'parcial' ? 'confirmar-parcial' : 'recibir-resto'
    setErrorServidor(null)
    transicion.mutate({ accion, pedido, cantidad: valor }, {
      onSuccess: cerrarCantidad,
      onError: (e) => {
        if (e instanceof ReglaNegocioError) { setErrorServidor(e.message); return }
        if (e instanceof StaleDataError) cerrarCantidad()
        avisarFallo(accion, e)
      },
    })
  }

  function confirmarAccion() {
    if (!confirmar) return
    const { accion, pedido } = confirmar
    setConfirmar(null)
    transicionar(TRANSICION[accion], pedido)
  }

  // Menú solo para el SUPERTECNICO (:961-966); un cancelado no lleva menú (DataTable no envuelve la fila si devuelve null).
  const menuFila = puedeEditar
    ? (p: Pedido) => (entradasMenu(p.estado).length > 0 ? <MenuPedido pedido={p} onAccion={alElegir} onInteraccion={marcar} /> : null)
    : undefined
  const propsTabla = {
    getRowId: (p: Pedido) => String(idPedido(p)),
    filaClase: claseFilaPedido,
    seleccionada,
    onSeleccionar: setSeleccionada,
    pedirDesplazamiento: peticionDesplazamiento,
    menuFila,
  }
  const textosConfirmar = confirmar ? confirmacionDe(confirmar.accion, confirmar.pedido) : null

  return (
    <div className="p-5">
      <div className="mb-3 flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-azul-medio">Pedidos</h1>
        <TogglePill opciones={OPCIONES_TOGGLE} />
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {/* Calco del MenuButton "Estado" con CustomMenuItem (no cierra al marcar) y del MultiSelectComboBox de proveedores
            (140 px, StockView.fxml:122-126): abrirlos congela el refresco (onOpenChange → marcar). */}
        <MultiSelect
          opciones={ESTADOS_PEDIDO}
          clave={(e) => e}
          etiqueta={chipDeEstado}
          seleccion={filtros.estados}
          onChange={(s) => setFiltros((f) => ({ ...f, estados: s as Set<EstadoPedido> }))}
          textoVacio="Estado"
          textoPlural={(n) => `${n} estados`}
          onOpenChange={marcar}
          className="min-w-[140px]"
        />
        <MultiSelect
          opciones={activos}
          clave={(p) => p.nombre}
          etiqueta={(p) => p.nombre}
          seleccion={filtros.proveedores}
          onChange={(s) => setFiltros((f) => ({ ...f, proveedores: s }))}
          textoVacio="Proveedor"
          textoPlural={(n) => `${n} proveedores`}
          onOpenChange={marcar}
          className="min-w-[140px]"
        />
        {/* "Buscar componente…" también en Otros (calco, inventario §1). */}
        <Input value={filtros.buscador} onChange={(e) => setFiltros((f) => ({ ...f, buscador: e.target.value }))} placeholder="Buscar componente…" className="w-[180px] bg-superficie" />
        <RangoFechas desde={filtros.desde} hasta={filtros.hasta} onChange={(desde, hasta) => setFiltros((f) => ({ ...f, desde, hasta }))} />
        {/* No toca el toggle ni la selección (:270-278). */}
        <BotonSecundario onClick={() => setFiltros({ ...FILTROS_PEDIDOS_VACIOS, estados: new Set(), proveedores: new Set() })}>Limpiar filtros</BotonSecundario>
        {puedeEditar &&
          (tipo === 'componentes' ? (
            <BotonPrimario className="ml-6" onClick={() => abrirNuevoPedido({ modo: 'vacio' })}>Nuevo pedido</BotonPrimario>
          ) : (
            <BotonPrimario className="ml-6" onClick={() => abrirNuevoOtroPedido()}>Nuevo otro pedido</BotonPrimario>
          ))}
      </div>
      {tipo === 'componentes' ? (
        <DataTable<CompraComponente> columns={columnasPedidos} data={visiblesCompras} vacio="Sin pedidos" {...propsTabla} />
      ) : (
        <DataTable<CompraOtro> columns={columnasOtros} data={visiblesOtros} vacio="Sin otros pedidos" {...propsTabla} />
      )}
      {/* P7: recarga la tabla visible (el JavaFX recarga siempre la de componentes). */}
      <EtiquetaActualizado
        actualizadoEn={tipo === 'componentes' ? compras.dataUpdatedAt : otros.dataUpdatedAt}
        onRecargar={() => (tipo === 'componentes' ? compras.refetch({ throwOnError: true }) : otros.refetch({ throwOnError: true }))}
      />

      <CantidadDialog
        modo="parcial"
        pedido={dialogoCantidad?.modo === 'parcial' ? dialogoCantidad.pedido : null}
        errorServidor={errorServidor}
        enviando={transicion.isPending}
        onConfirmar={confirmarCantidad}
        onCancelar={cerrarCantidad}
      />
      <CantidadDialog
        modo="resto"
        pedido={dialogoCantidad?.modo === 'resto' ? dialogoCantidad.pedido : null}
        errorServidor={errorServidor}
        enviando={transicion.isPending}
        onConfirmar={confirmarCantidad}
        onCancelar={cerrarCantidad}
      />
      <ConfirmDialog
        abierto={confirmar !== null}
        titulo={textosConfirmar?.titulo ?? ''}
        descripcion={textosConfirmar?.descripcion ?? ''}
        textoAccion={textosConfirmar?.textoAccion ?? ''}
        onConfirmar={confirmarAccion}
        onCancelar={() => setConfirmar(null)}
      />
    </div>
  )
}
```

- [ ] **Step 7: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.test.tsx`
Expected: PASS, 24 tests.

- [ ] **Step 8: Rutas**

En `src/app/router.tsx`, añadir como nueva l.2, antes del import de `ProveedoresPage` (orden alfabético: `pedidos/` antes que `proveedores/`):

```tsx
import { PedidosPage } from '@/modules/almacen/pedidos/PedidosPage'
```

y sustituir la antigua l.70 (`{ path: '/stock/pedidos', element: <PendienteDeMigrar nombre="Pedidos" /> },`, ahora l.71) por:

```tsx
          // `key` por toggle: cada ruta monta su propia instancia (estado local, diálogos) en vez de reutilizar la anterior.
          { path: '/stock/pedidos', element: <PedidosPage key="componentes" tipo="componentes" /> },
          { path: '/stock/pedidos/otros', element: <PedidosPage key="otros" tipo="otros" /> },
```

`PendienteDeMigrar` sigue importado: lo usan Estadísticas, técnicos, logs y contraseña (l.72-76).

- [ ] **Step 9: Comprobar**

```bash
npm run check
```

Expected: lint, typecheck y **1231 tests** en verde (1205 + 1 de `DataTable` + 1 de `ConfirmDialog` + 24 de `PedidosPage`). Si `react-hooks/set-state-in-effect` no señala el `setPeticionDesplazamiento` (y ESLint avisa de la directiva sin uso), quitar el comentario `eslint-disable-next-line`; si señala también el `setSeleccionada` (store), mover el comentario a esa línea.

- [ ] **Step 10: Commit**

```bash
git add src/shared/ui/DataTable.tsx src/shared/ui/DataTable.test.tsx src/shared/ui/ConfirmDialog.tsx src/shared/ui/ConfirmDialog.test.tsx src/modules/almacen/pedidos/PedidosPage.tsx src/modules/almacen/pedidos/PedidosPage.test.tsx src/app/router.tsx
git commit -m "feat(pedidos): pestaña pedidos con toggle componentes y otros, filtros, menu de transiciones, dialogos, llegada desde stock, csv y refresco congelado"
```

---

---

## Task 14: Web — `lineas.ts` y `conversion.ts` de los formularios de pedido

**Files:**
- Create: `src/modules/almacen/pedidos/formulario/datosPrueba.ts` (fixtures sintéticas de los tests de T14-T17)
- Create: `src/modules/almacen/pedidos/formulario/lineas.ts`, `src/modules/almacen/pedidos/formulario/lineas.test.ts`
- Create: `src/modules/almacen/pedidos/formulario/conversion.ts`, `src/modules/almacen/pedidos/formulario/conversion.test.ts`

**Interfaces:**
- Consumes: `Componente`, `SolicitudResumen`, `SolicitudStock`, `CompraComponente`, `CompraOtro`, `Proveedor` de `@/shared/api/client` (T7); `parsearEntero`, `parsearDecimal`, `formatearNumero` de `@/shared/lib/importes` (T7); `PrecargaPedido` de `@/shared/lib/formularioPedido` (T8); `CuerpoLoteCompras`, `CuerpoLoteOtros` de `../api` (T10).
- Produces (firmas de el documento de interfaces del reparto (fuera del repo), exactas, más los helpers marcados como *extra*):

```ts
// formulario/lineas.ts
export type LineaCompra = { id: number; idCom: number | null; idProv: number | null; cantidad: string; precio: string; urgente: boolean }
export type LineaOtro = { id: number; concepto: string; idProv: number | null; cantidad: string; precio: string; urgente: boolean }
export type LineaBase = { id: number; idProv: number | null; cantidad: string; precio: string; urgente: boolean }   // extra: lo común (DialogoLineas, T15)
export const MSG_SIN_LINEAS = 'Añade al menos una línea.'                                                           // extra
export function lineaCompraVacia(id: number, idCom?: number | null): LineaCompra   // cantidad '1', precio '0,00', urgente false
export function lineaOtroVacia(id: number): LineaOtro
export function precargarComponentes(idsCom: number[], activos: Componente[]): LineaCompra[]
export function precargarSolicitudes(urgentes: SolicitudResumen[], preventivas: SolicitudStock[], activos: Componente[]): { lineas: LineaCompra[]; omitidas: number }
export function precargaInicial(precarga: PrecargaPedido, activos: Componente[]): { lineas: LineaCompra[]; omitidas: number }   // extra
export function preseleccionDe(precarga: PrecargaPedido, activos: Componente[]): number | null                                 // extra
export function avisoOmitidas(n: number): string | null
export function validarLineasCompra(lineas: LineaCompra[]): string | null
export function validarLineasOtro(lineas: LineaOtro[]): string | null
export function cuerpoLoteCompras(lineas: LineaCompra[], origen: { urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] } | null): CuerpoLoteCompras
export function cuerpoLoteOtros(lineas: LineaOtro[]): CuerpoLoteOtros
export function cambiarLinea<T extends { id: number }>(lineas: T[], id: number, cambio: Partial<T>): T[]   // extra
export function quitarLinea<T extends { id: number }>(lineas: T[], id: number): T[]                          // extra
export function siguienteId(lineas: { id: number }[]): number                                                // extra
// formulario/conversion.ts
export function aEuros(precio: number, tasa: number): number
export function totalLinea(precio: number | null, tasa: number | null, cantidad: number | null): number | null
export function etiquetaTasa(divisa: string, tasa: number): string
```

Reglas (inventario §10, §11, §14; spec §6 y D10): la línea guarda `cantidad` y `precio` como texto (lo que teclea el usuario, P8: celdas siempre editables); una cantidad no entera o `<= 0` da el error de cantidad y un precio no numérico o `< 0` el de precio (en el JavaFX un texto no numérico no llegaba al modelo; aquí sí, y cae en el mismo mensaje). La agrupación de solicitudes es el `LinkedHashMap` del cliente (FC :606-621): urgentes en su orden, luego preventivas; los componentes no activos se omiten **y se cuentan** (D10). El "reductor" de la spec §5 se implementa como helpers puros (`cambiarLinea`, `quitarLinea`, `siguienteId`) sobre `useState`, que es lo que necesitan los dos diálogos.

- [ ] **Step 1: Fixtures sintéticas**

`src/modules/almacen/pedidos/formulario/datosPrueba.ts`:

```ts
import type { Componente, CompraComponente, CompraOtro, Proveedor, SolicitudResumen, SolicitudStock } from '@/shared/api/client'

/** Datos SINTÉTICOS de los tests de los formularios de pedido (T14-T17). Nunca datos del taller. */
const base = { fechaRegistro: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:00', ultimoPedido: null, enCamino: 0, stock: 0, stockMinimo: 1 }

/** Orden del servidor (ORDER BY TIPO no importa aquí): un inactivo (4) y un slave de SKU compartido (5, master 2). */
export const COMPONENTES: Componente[] = [
  { ...base, idCom: 1, tipo: 'lcd-x-negro', activo: true, idComMaster: null },
  { ...base, idCom: 2, tipo: 'bat-x', activo: true, idComMaster: null },
  { ...base, idCom: 4, tipo: 'mc-x', activo: false, idComMaster: null },
  { ...base, idCom: 5, tipo: 'bat-y', activo: true, idComMaster: 2 },
]

export const PROVEEDORES: Proveedor[] = [
  { idProv: 1, nombre: 'ACME', activo: true, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' },
  { idProv: 2, nombre: 'Proveedor B', activo: true, divisa: 'USD', comentario: '', tipo: 'COMPONENTES' },
  { idProv: 3, nombre: 'Proveedor A', activo: false, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' },
]

export function urgente(idRc: number, idCom: number): SolicitudResumen {
  return { idRc, idRep: `R${idRc}`, imei: '111111111111111', nombreTecnico: 'tecnico1', idCom, tipoComponente: null, descripcion: null, estado: 'PENDIENTE', fechaSolicitud: '2026-09-20T10:00:00' }
}

export function preventiva(idSol: number, idCom: number): SolicitudStock {
  return { idSol, idCom, tipoComponente: `com-${idCom}`, idUsu: 7, nombreUsuario: 'tecnico1', descripcion: null, estado: 'PENDIENTE', fecha: '2026-09-20T10:00:00' }
}

export const COMPRA: CompraComponente = {
  idCompra: 7, idCom: 2, tipoComponente: 'bat-x', idProv: 1, nombreProveedor: 'ACME', cantidad: 3, cantidadRecibida: null,
  esUrgente: true, fechaPedido: '2026-09-20T10:00:00', fechaLlegada: null, precioUnidadPedido: 12.5, divisa: 'EUR',
  precioEur: 12.5, estado: 'pendiente', updatedAt: '2026-09-20T10:00:00',
}

export const OTRO: CompraOtro = {
  idCompraOtro: 9, idProv: 1, nombreProveedor: 'ACME', concepto: 'Cinta de embalar', cantidad: 4, cantidadRecibida: null,
  esUrgente: true, fechaPedido: '2026-09-20T10:00:00', fechaLlegada: null, precioUnidadPedido: 2, divisa: 'EUR',
  precioEur: 2, estado: 'pendiente', updatedAt: '2026-09-20T10:00:00',
}
```

`cantidadRecibida: null` y `fechaLlegada: null` compilan porque T6/T7 dejan esos campos `number | null` / `string | null` en `schema.d.ts`; si `npm run typecheck` falla aquí, el contrato no se regeneró bien en T7 (se corrige allí, no aquí).

- [ ] **Step 2: Test de `lineas.ts` (falla)**

`src/modules/almacen/pedidos/formulario/lineas.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { COMPONENTES, preventiva, urgente } from './datosPrueba'
import {
  avisoOmitidas, cambiarLinea, cuerpoLoteCompras, cuerpoLoteOtros, lineaCompraVacia, lineaOtroVacia, precargaInicial,
  precargarComponentes, precargarSolicitudes, preseleccionDe, quitarLinea, siguienteId, validarLineasCompra, validarLineasOtro,
  type LineaCompra, type LineaOtro,
} from './lineas'

const completa = (cambio: Partial<LineaCompra> = {}): LineaCompra => ({ id: 1, idCom: 2, idProv: 1, cantidad: '2', precio: '12,5', urgente: false, ...cambio })
const otra = (cambio: Partial<LineaOtro> = {}): LineaOtro => ({ id: 1, concepto: 'Cinta de embalar', idProv: 1, cantidad: '3', precio: '1,5', urgente: false, ...cambio })

describe('líneas vacías', () => {
  it('cantidad 1, precio 0,00, sin proveedor ni urgente; la de compra admite un componente', () => {
    expect(lineaCompraVacia(3)).toEqual({ id: 3, idCom: null, idProv: null, cantidad: '1', precio: '0,00', urgente: false })
    expect(lineaCompraVacia(4, 2)).toEqual({ id: 4, idCom: 2, idProv: null, cantidad: '1', precio: '0,00', urgente: false })
    expect(lineaOtroVacia(1)).toEqual({ id: 1, concepto: '', idProv: null, cantidad: '1', precio: '0,00', urgente: false })
  })
})

describe('precargas', () => {
  it('precargarComponentes: una línea por id, en orden, con el componente y cantidad 1', () => {
    expect(precargarComponentes([2, 1, 5], COMPONENTES)).toEqual([lineaCompraVacia(1, 2), lineaCompraVacia(2, 1), lineaCompraVacia(3, 5)])
  })
  it('precargarComponentes: inactivo o desconocido → línea vacía (calco)', () => {
    expect(precargarComponentes([4, 99], COMPONENTES)).toEqual([lineaCompraVacia(1), lineaCompraVacia(2)])
  })
  it('preseleccionDe: solo con un único componente activo ("Pedir" de Stock o de una alerta)', () => {
    expect(preseleccionDe({ modo: 'componentes', idsCom: [2] }, COMPONENTES)).toBe(2)
    expect(preseleccionDe({ modo: 'componentes', idsCom: [4] }, COMPONENTES)).toBeNull()
    expect(preseleccionDe({ modo: 'componentes', idsCom: [1, 2] }, COMPONENTES)).toBeNull()
    expect(preseleccionDe({ modo: 'vacio' }, COMPONENTES)).toBeNull()
    expect(preseleccionDe({ modo: 'solicitudes', urgentes: [urgente(10, 2)], preventivas: [] }, COMPONENTES)).toBeNull()
  })
  it('precargarSolicitudes: agrupa por componente, urgentes primero y en su orden, cantidad = nº de solicitudes; cuenta las omitidas', () => {
    const r = precargarSolicitudes(
      [urgente(10, 2), urgente(11, 1), urgente(12, 2), urgente(13, 4)],
      [preventiva(20, 5), preventiva(21, 1), preventiva(22, 4)],
      COMPONENTES,
    )
    expect(r.lineas).toEqual([
      { ...lineaCompraVacia(1, 2), cantidad: '2' },
      { ...lineaCompraVacia(2, 1), cantidad: '2' },
      { ...lineaCompraVacia(3, 5), cantidad: '1' },
    ])
    expect(r.omitidas).toBe(2)
  })
  it('precargarSolicitudes: sin inactivos no omite nada; listas vacías → sin líneas', () => {
    expect(precargarSolicitudes([urgente(10, 1)], [], COMPONENTES)).toEqual({ lineas: [lineaCompraVacia(1, 1)], omitidas: 0 })
    expect(precargarSolicitudes([], [], COMPONENTES)).toEqual({ lineas: [], omitidas: 0 })
  })
  it('precargaInicial: vacío sin líneas; componentes y solicitudes delegan', () => {
    expect(precargaInicial({ modo: 'vacio' }, COMPONENTES)).toEqual({ lineas: [], omitidas: 0 })
    expect(precargaInicial({ modo: 'componentes', idsCom: [1] }, COMPONENTES)).toEqual({ lineas: [lineaCompraVacia(1, 1)], omitidas: 0 })
    expect(precargaInicial({ modo: 'solicitudes', urgentes: [urgente(10, 4)], preventivas: [preventiva(20, 2)] }, COMPONENTES))
      .toEqual({ lineas: [lineaCompraVacia(1, 2)], omitidas: 1 })
  })
  it('avisoOmitidas: texto de D10 con n > 0; null con 0', () => {
    expect(avisoOmitidas(2)).toBe('2 solicitud(es) de componentes desactivados no se han añadido y siguen pendientes.')
    expect(avisoOmitidas(0)).toBeNull()
  })
})

describe('validarLineasCompra', () => {
  it('sin líneas: "Añade al menos una línea."', () => {
    expect(validarLineasCompra([])).toBe('Añade al menos una línea.')
  })
  it('por línea, en orden: componente → proveedor → cantidad → precio', () => {
    const mala: LineaCompra = { id: 1, idCom: null, idProv: null, cantidad: '0', precio: '-1', urgente: false }
    expect(validarLineasCompra([mala])).toBe('Línea 1: selecciona un componente.')
    expect(validarLineasCompra([{ ...mala, idCom: 2 }])).toBe('Línea 1: selecciona un proveedor.')
    expect(validarLineasCompra([{ ...mala, idCom: 2, idProv: 1 }])).toBe('Línea 1: la cantidad debe ser mayor que 0.')
    expect(validarLineasCompra([{ ...mala, idCom: 2, idProv: 1, cantidad: '2' }])).toBe('Línea 1: el precio no puede ser negativo.')
    expect(validarLineasCompra([completa()])).toBeNull()
  })
  it('cantidad: "0", "abc", "" y "1,5" fallan; precio: "-1" y "abc" fallan, "0" y "12,5" pasan', () => {
    for (const cantidad of ['0', 'abc', '', '1,5']) expect(validarLineasCompra([completa({ cantidad })])).toBe('Línea 1: la cantidad debe ser mayor que 0.')
    for (const precio of ['-1', 'abc']) expect(validarLineasCompra([completa({ precio })])).toBe('Línea 1: el precio no puede ser negativo.')
    for (const precio of ['0', '12,5', '12.5', '0,00']) expect(validarLineasCompra([completa({ precio })])).toBeNull()
  })
  it('para en la primera línea que falla, con su número (1-based)', () => {
    expect(validarLineasCompra([completa(), completa({ id: 2, idProv: null }), completa({ id: 3, idCom: null })])).toBe('Línea 2: selecciona un proveedor.')
  })
})

describe('validarLineasOtro', () => {
  it('concepto en blanco (tras trim) primero; después proveedor, cantidad y precio con los textos de siempre', () => {
    expect(validarLineasOtro([])).toBe('Añade al menos una línea.')
    expect(validarLineasOtro([otra({ concepto: '   ', idProv: null })])).toBe('Línea 1: el concepto no puede estar vacío.')
    expect(validarLineasOtro([otra({ idProv: null })])).toBe('Línea 1: selecciona un proveedor.')
    expect(validarLineasOtro([otra({ cantidad: '0' })])).toBe('Línea 1: la cantidad debe ser mayor que 0.')
    expect(validarLineasOtro([otra({ precio: '-0,5' })])).toBe('Línea 1: el precio no puede ser negativo.')
    expect(validarLineasOtro([otra()])).toBeNull()
  })
})

describe('cuerpos de lote', () => {
  it('cuerpoLoteCompras: convierte los textos ("12,5" → 12.5, "3" → 3) y lleva solo las solicitudes cuyo componente tiene línea', () => {
    const lineas = [completa({ idCom: 2, cantidad: '3', urgente: true }), completa({ id: 2, idCom: 5, idProv: 2, cantidad: '1', precio: '0' })]
    const origen = { urgentes: [urgente(10, 2), urgente(11, 1), urgente(12, 2)], preventivas: [preventiva(20, 5), preventiva(21, 1)] }
    expect(cuerpoLoteCompras(lineas, origen)).toEqual({
      lineas: [
        { idCom: 2, idProv: 1, cantidad: 3, esUrgente: true, precioUnidad: 12.5 },
        { idCom: 5, idProv: 2, cantidad: 1, esUrgente: false, precioUnidad: 0 },
      ],
      solicitudes: { urgentes: [10, 12], preventivas: [20] },
    })
  })
  it('cuerpoLoteCompras: sin origen, solicitudes vacías', () => {
    expect(cuerpoLoteCompras([completa()], null).solicitudes).toEqual({ urgentes: [], preventivas: [] })
  })
  it('cuerpoLoteOtros: concepto recortado y números convertidos', () => {
    expect(cuerpoLoteOtros([otra({ concepto: '  Cinta de embalar  ', urgente: true })])).toEqual({
      lineas: [{ idProv: 1, concepto: 'Cinta de embalar', cantidad: 3, esUrgente: true, precioUnidad: 1.5 }],
    })
  })
})

describe('edición de la lista', () => {
  it('cambiarLinea cambia solo esa línea; quitarLinea la quita; siguienteId = máximo + 1 (1 con la lista vacía)', () => {
    const ls = [lineaCompraVacia(1), lineaCompraVacia(3)]
    expect(cambiarLinea(ls, 3, { idProv: 2 })).toEqual([lineaCompraVacia(1), { ...lineaCompraVacia(3), idProv: 2 }])
    expect(quitarLinea(ls, 1)).toEqual([lineaCompraVacia(3)])
    expect(siguienteId(ls)).toBe(4)
    expect(siguienteId([])).toBe(1)
  })
})
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run (desde `gestion-reparaciones-web`): `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: FAIL, `Failed to resolve import "./lineas"`.

- [ ] **Step 4: Implementar `lineas.ts`**

`src/modules/almacen/pedidos/formulario/lineas.ts`:

```ts
import type { Componente, SolicitudResumen, SolicitudStock } from '@/shared/api/client'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { parsearDecimal, parsearEntero } from '@/shared/lib/importes'
import type { CuerpoLoteCompras, CuerpoLoteOtros } from '../api'

/** Línea de "Nuevo pedido" (LineaCompra del JavaFX). Cantidad y precio van como texto: las celdas son inputs siempre
 *  editables (P8) y el texto se valida al confirmar. */
export type LineaCompra = { id: number; idCom: number | null; idProv: number | null; cantidad: string; precio: string; urgente: boolean }
/** Línea de "Nuevo otro pedido" (LineaOtro, FO :46-73). */
export type LineaOtro = { id: number; concepto: string; idProv: number | null; cantidad: string; precio: string; urgente: boolean }
/** Lo común a las dos: las columnas Proveedor, Cant., P.Unit., Urg., Total EUR y la papelera (DialogoLineas). */
export type LineaBase = { id: number; idProv: number | null; cantidad: string; precio: string; urgente: boolean }

export const MSG_SIN_LINEAS = 'Añade al menos una línea.'

/** Cantidad 1, precio 0 (mostrado "0,00"), sin proveedor, sin urgente (LineaCompra del JavaFX). */
export function lineaCompraVacia(id: number, idCom: number | null = null): LineaCompra {
  return { id, idCom, idProv: null, cantidad: '1', precio: '0,00', urgente: false }
}

export function lineaOtroVacia(id: number): LineaOtro {
  return { id, concepto: '', idProv: null, cantidad: '1', precio: '0,00', urgente: false }
}

function idsActivos(activos: Componente[]): Set<number> {
  return new Set(activos.filter((c) => c.activo).map((c) => c.idCom))
}

/** "Pedir" (1 id) y "Pedir todas las piezas" (N ids, orden de la campana): una línea por id con cantidad 1. Un id que no
 *  está entre los activos deja la línea vacía, como el JavaFX (buscaba en `componentesDisponibles` y no rellenaba). */
export function precargarComponentes(idsCom: number[], activos: Componente[]): LineaCompra[] {
  const validos = idsActivos(activos)
  return idsCom.map((idCom, i) => lineaCompraVacia(i + 1, validos.has(idCom) ? idCom : null))
}

/** "Pedir piezas" (initConSolicitudes, FC :606-621): agrupa por componente con el orden de un LinkedHashMap (urgentes en su
 *  orden, luego preventivas) y la cantidad es el número de solicitudes. Diferencia D10: las de un componente no activo no
 *  generan línea y se cuentan en `omitidas` para avisar (el JavaFX las omitía en silencio y aun así las marcaba). */
export function precargarSolicitudes(urgentes: SolicitudResumen[], preventivas: SolicitudStock[], activos: Componente[]): { lineas: LineaCompra[]; omitidas: number } {
  const validos = idsActivos(activos)
  const porComponente = new Map<number, number>()
  let omitidas = 0
  for (const idCom of [...urgentes.map((s) => s.idCom), ...preventivas.map((s) => s.idCom)]) {
    if (!validos.has(idCom)) {
      omitidas += 1
      continue
    }
    porComponente.set(idCom, (porComponente.get(idCom) ?? 0) + 1)
  }
  const lineas = Array.from(porComponente, ([idCom, n], i) => ({ ...lineaCompraVacia(i + 1, idCom), cantidad: String(n) }))
  return { lineas, omitidas }
}

/** Líneas con las que abre el formulario según el modo del store (T8). */
export function precargaInicial(precarga: PrecargaPedido, activos: Componente[]): { lineas: LineaCompra[]; omitidas: number } {
  switch (precarga.modo) {
    case 'vacio':
      return { lineas: [], omitidas: 0 }
    case 'componentes':
      return { lineas: precargarComponentes(precarga.idsCom, activos), omitidas: 0 }
    case 'solicitudes':
      return precargarSolicitudes(precarga.urgentes, precarga.preventivas, activos)
  }
}

/** Componente con el que "+ Añadir línea" rellena la línea nueva (añadirFila :496-513, `preselect`): solo cuando se abrió
 *  con un único componente ("Pedir") y ese componente está activo. */
export function preseleccionDe(precarga: PrecargaPedido, activos: Componente[]): number | null {
  if (precarga.modo !== 'componentes' || precarga.idsCom.length !== 1) return null
  const id = precarga.idsCom[0]
  return idsActivos(activos).has(id) ? id : null
}

/** Línea de información del formulario en modo solicitudes (spec §6, D10). */
export function avisoOmitidas(n: number): string | null {
  return n > 0 ? `${n} solicitud(es) de componentes desactivados no se han añadido y siguen pendientes.` : null
}

/** Proveedor → cantidad → precio (FC :528-538, FO :452-466). */
function errorComun(l: LineaBase, n: number): string | null {
  if (l.idProv === null) return `Línea ${n}: selecciona un proveedor.`
  const cantidad = parsearEntero(l.cantidad)
  if (cantidad === null || cantidad <= 0) return `Línea ${n}: la cantidad debe ser mayor que 0.`
  const precio = parsearDecimal(l.precio)
  if (precio === null || precio < 0) return `Línea ${n}: el precio no puede ser negativo.`
  return null
}

/** Calco de confirmar (FC :517-540): primer error o null. */
export function validarLineasCompra(lineas: LineaCompra[]): string | null {
  if (lineas.length === 0) return MSG_SIN_LINEAS
  for (const [i, l] of lineas.entries()) {
    const n = i + 1
    if (l.idCom === null) return `Línea ${n}: selecciona un componente.`
    const error = errorComun(l, n)
    if (error !== null) return error
  }
  return null
}

/** Calco de confirmar de otros (FO :444-468): el concepto, recortado, va antes que lo común. */
export function validarLineasOtro(lineas: LineaOtro[]): string | null {
  if (lineas.length === 0) return MSG_SIN_LINEAS
  for (const [i, l] of lineas.entries()) {
    const n = i + 1
    if (l.concepto.trim() === '') return `Línea ${n}: el concepto no puede estar vacío.`
    const error = errorComun(l, n)
    if (error !== null) return error
  }
  return null
}

/** Cuerpo de POST /api/compras/lote. Solo se llama con `validarLineasCompra(lineas) === null` (por eso los `as number`).
 *  Las solicitudes que viajan son las de componentes que siguen teniendo línea al confirmar (spec §6): si el usuario quitó
 *  la línea o le cambió el componente, esas solicitudes siguen PENDIENTE. */
export function cuerpoLoteCompras(lineas: LineaCompra[], origen: { urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] } | null): CuerpoLoteCompras {
  const conLinea = new Set(lineas.map((l) => l.idCom))
  return {
    lineas: lineas.map((l) => ({
      idCom: l.idCom as number,
      idProv: l.idProv as number,
      cantidad: parsearEntero(l.cantidad) as number,
      esUrgente: l.urgente,
      precioUnidad: parsearDecimal(l.precio) as number,
    })),
    solicitudes: origen === null
      ? { urgentes: [], preventivas: [] }
      : {
          urgentes: origen.urgentes.filter((s) => conLinea.has(s.idCom)).map((s) => s.idRc),
          preventivas: origen.preventivas.filter((s) => conLinea.has(s.idCom)).map((s) => s.idSol),
        },
  }
}

/** Cuerpo de POST /api/compras-otros/lote; concepto recortado (FO :470-478). Solo con `validarLineasOtro(lineas) === null`. */
export function cuerpoLoteOtros(lineas: LineaOtro[]): CuerpoLoteOtros {
  return {
    lineas: lineas.map((l) => ({
      idProv: l.idProv as number,
      concepto: l.concepto.trim(),
      cantidad: parsearEntero(l.cantidad) as number,
      esUrgente: l.urgente,
      precioUnidad: parsearDecimal(l.precio) as number,
    })),
  }
}

export function cambiarLinea<T extends { id: number }>(lineas: T[], id: number, cambio: Partial<T>): T[] {
  return lineas.map((l) => (l.id === id ? { ...l, ...cambio } : l))
}

export function quitarLinea<T extends { id: number }>(lineas: T[], id: number): T[] {
  return lineas.filter((l) => l.id !== id)
}

export function siguienteId(lineas: { id: number }[]): number {
  return lineas.reduce((max, l) => Math.max(max, l.id), 0) + 1
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: PASS, 17 tests.

- [ ] **Step 6: Test de `conversion.ts` (falla)**

`src/modules/almacen/pedidos/formulario/conversion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { aEuros, etiquetaTasa, totalLinea } from './conversion'

describe('conversión a euros (tasa = divisa por 1 EUR, P3: se divide)', () => {
  it('aEuros divide por la tasa', () => {
    expect(aEuros(10, 1.1367)).toBeCloseTo(8.7974, 4)
    expect(aEuros(10, 1)).toBe(10)
  })
  it('totalLinea = precio / tasa × cantidad; null si falta cualquiera de los tres o la tasa no es positiva', () => {
    expect(totalLinea(10, 1.1367, 2)).toBeCloseTo(17.5948, 4)
    expect(totalLinea(12.5, 1, 3)).toBe(37.5)
    expect(totalLinea(null, 1, 3)).toBeNull()
    expect(totalLinea(12.5, null, 3)).toBeNull()
    expect(totalLinea(12.5, 1, null)).toBeNull()
    expect(totalLinea(12.5, 0, 3)).toBeNull()
  })
  it('etiquetaTasa: dos espacios delante, 1/tasa con 4 decimales y coma (P7); vacía en EUR', () => {
    expect(etiquetaTasa('USD', 1.1367)).toBe('  (1 USD = 0,8797 €)')
    expect(etiquetaTasa('EUR', 1)).toBe('')
  })
})
```

- [ ] **Step 7: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/conversion.test.ts`
Expected: FAIL, `Failed to resolve import "./conversion"`.

- [ ] **Step 8: Implementar `conversion.ts`**

`src/modules/almacen/pedidos/formulario/conversion.ts`:

```ts
import { formatearNumero } from '@/shared/lib/importes'

/** Frankfurter (`from=EUR&to={DIV}`) da unidades de la divisa por 1 EUR: el precio en euros es precio / tasa (P3). El
 *  JavaFX multiplicaba. El servidor calcula el `precioEur` que se guarda; esto es solo la vista previa del formulario. */
export function aEuros(precio: number, tasa: number): number {
  return precio / tasa
}

/** Total EUR de una línea o del editor. null si falta el precio, la cantidad o la tasa (aún no ha llegado o falló): la
 *  celda pinta "—" en vez de calcular como si fuera EUR (P7). */
export function totalLinea(precio: number | null, tasa: number | null, cantidad: number | null): number | null {
  if (precio === null || tasa === null || cantidad === null || tasa <= 0) return null
  return aEuros(precio, tasa) * cantidad
}

/** Etiqueta del editor (FCE :99-111) corregida (P7): "(1 {DIV} = {1/tasa} €)" con cuatro decimales, dos espacios delante.
 *  EUR no lleva etiqueta. */
export function etiquetaTasa(divisa: string, tasa: number): string {
  if (divisa.toUpperCase() === 'EUR') return ''
  return `  (1 ${divisa} = ${formatearNumero(1 / tasa, 4)} €)`
}
```

- [ ] **Step 9: Ejecutar, lint y tipos**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts src/modules/almacen/pedidos/formulario/conversion.test.ts`
Expected: PASS, 20 tests.

Run: `npm run lint && npm run typecheck`
Expected: sin errores ni warnings.

- [ ] **Step 10: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/datosPrueba.ts src/modules/almacen/pedidos/formulario/lineas.ts src/modules/almacen/pedidos/formulario/lineas.test.ts src/modules/almacen/pedidos/formulario/conversion.ts src/modules/almacen/pedidos/formulario/conversion.test.ts
git commit -m "feat(pedidos): lineas del formulario de pedido: precargas, validacion, cuerpos de lote y conversion a euros"
```

Recuento: **+20 tests** (17 de `lineas.test.ts`, 3 de `conversion.test.ts`).

---

## Task 15: Web — `NuevoPedidoDialog` y el host `FormulariosPedido` en el shell

**Files:**
- Create: `src/modules/almacen/pedidos/formulario/errores.ts`, `errores.test.ts`
- Create: `src/modules/almacen/pedidos/formulario/DialogoLineas.tsx` (armazón común de los dos formularios de alta; lo reutiliza T16)
- Create: `src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`, `NuevoPedidoDialog.test.tsx`
- Create: `src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx`, `FormulariosPedido.test.tsx`
- Modify: `src/app/shell/AppLayout.tsx` (monta `<FormulariosPedido />`)
- Create: `src/app/shell/AppLayout.test.tsx`

**Interfaces:**
- Consumes: T14 (`lineas.ts`, `conversion.ts`, `datosPrueba.ts`); `formularioPedido`, `cerrarFormularioPedido`, `abrirNuevoPedido`, `PrecargaPedido` de `@/shared/lib/formularioPedido` (T8); `useTasas` de `../tasa` (T9); `useGuardarLoteCompras` de `../api` (T10); `crearClavesIdempotencia` de `@/shared/lib/clavesIdempotencia` (T7); `formatearImporte`, `parsearDecimal`, `parsearEntero`, `simboloFormulario` de `@/shared/lib/importes` (T7); `useComponentesStock` de `../../stock/api` y `useProveedoresComponentes` de `../../proveedores/api` (4a); `CampoAutocompletar`, `ComboNavy`, `Checkbox`, `Dialog*`, `Table*`, `BotonPrimario`/`BotonSecundario` de `@/shared/ui`; `mensajeDeError`, `esErrorGestionadoGlobalmente`, `ReglaNegocioError`, `StaleDataError` de `@/shared/api/errors`.
- Produces:

```ts
// NuevoPedidoDialog.tsx
export function NuevoPedidoDialog({ precarga, onCerrar }: { precarga: PrecargaPedido; onCerrar: () => void }): JSX.Element
// FormulariosPedido.tsx
export function FormulariosPedido(): JSX.Element | null
// DialogoLineas.tsx (extra; lo consume T16)
export function DialogoLineas<L extends LineaBase>(props: {
  titulo: string
  primera: { cabecera: string; ancho: number; celda: (linea: L, n: number) => ReactNode }
  lineas: L[]
  proveedores: Proveedor[]          // solo activos
  info: string | null               // línea de información (aviso de omitidas)
  error: string | null              // línea de error (validación o servidor)
  bloqueado: boolean                // deshabilita "+ Añadir línea" y "Confirmar pedido"
  enviando: boolean                 // además deshabilita "Cancelar" e impide cerrar
  onCambiar: (id: number, cambio: Partial<LineaBase>) => void
  onQuitar: (id: number) => void
  onAnadir: () => number            // devuelve el id de la línea nueva (se selecciona y se desplaza a ella)
  onConfirmar: () => void
  onCerrar: () => void
}): JSX.Element
// errores.ts (extra; lo consumen T15-T17)
export function mensajeErrorGuardado(e: unknown, opciones?: { staleData?: string }): string | null
```

**Hook de componentes (decisión):** `useComponentesStock({ activo: false })` de `../../stock/api` (`src/modules/almacen/stock/api.ts:13-21`): es la clave `['componentes','gestionados']` que pide la spec §5 y la misma caché que "Stock actual" (desde Stock el formulario abre con los datos ya en memoria y los refresca al montar, `staleTime: 0`); `activo: false` = sin sondeo ni refetch por foco mientras el modal está abierto. `useComponentesGestionados` de `modules/taller/notificaciones/api.ts:48` queda descartado: está en otro módulo (regla de lint `no-restricted-imports`, `eslint.config.js`) y usa la clave de la campana. `ordenarStock` solo manda los inactivos al final de forma estable (`stock/filtros.ts:9-11`), así que tras filtrar activos las opciones quedan en el orden del servidor, como en el JavaFX. Proveedores: `useProveedoresComponentes({ activo: false })` (`proveedores/api.ts:9-17`) filtrado por `activo` en cliente (spec §4.6).

**Maquetación (inventario §10, `FormularioCompraView.fxml`):** `VBox` 700 × padding 28 × spacing 16 sobre `vista-container` (`#DDE1E7` = `bg-fondo-vista`); título `vista-titulo` 24 px negrita `#2C3B54` (`text-2xl font-bold text-azul-medio`, como los `<h1>` de las páginas); tabla con columnas Componente 175, Proveedor 155, Cant. 55, P.Unit. 80, Urg. 45, Total EUR 90 y la papelera 40 (sin cabecera), placeholder "Añade al menos una línea"; debajo "+ Añadir línea" (`btn-secondary`) sola; y en otra fila, a la derecha, "Cancelar" (`btn-secondary`) y "Confirmar pedido" (`btn-primary`). Fila seleccionada: fondo `#2C3B54` (`bg-azul-medio`), borde inferior `#3D5070` (`border-b-fila-selected-brd`), textos claros (`text-crema`), igual que `DataTable` (`src/shared/ui/DataTable.tsx:324`). Celda Componente: `CampoAutocompletar` (ya es la píldora navy `#001232` de 12 px negrita, popup blanco de 6 filas de 30 px, filtro "contiene", Enter = primero; `src/shared/ui/CampoAutocompletar.tsx:15-16, 44-67`), con el anillo `rgba(255,255,255,.35)` en la fila seleccionada. Cant. y P.Unit.: caja blanca con borde `#C2C8D0`, radio 3, padding 5/8 (`ESTILO_EDITABLE`). Papelera: `/borrar.png` de 25 px con cursor de mano (el mismo fichero de `public/` que `modules/taller/componentes/BotonPapelera.tsx:5`, que no se puede importar desde `almacen`).

**Dos detalles de DOM comprobados:**
- `Table` de `src/shared/ui/table.tsx:6-19` envuelve la tabla en un `div` con `overflow-x-auto`, que recortaría el popup `absolute` de `CampoAutocompletar`. Se usa un `<table>` propio con `TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell` (que no añaden overflow), sin contenedor con scroll: el que desplaza es el `DialogContent` (`max-h-[calc(100vh-24px)] overflow-y-auto`, patrón de `AsignarTrabajosDialog.tsx:80`), con alto mínimo de 220 px para la tabla.
- Los anchos van en `<colgroup>` como porcentaje de la suma (patrón de `DataTable.tsx:387-390`): el "Nuevo otro pedido" suma 665 px con Concepto 200 y el ancho útil es 644 (700 − 2 × 28); así cabe sin scroll horizontal.

**Inicialización de las líneas sin `useEffect`:** la lista de activos llega asíncrona. `lineas` arranca en `[]` en modo `vacio` y en `null` en los otros; cuando la consulta de componentes termina (bien o mal) y `lineas === null`, se calcula `precargaInicial` **una vez**, ajustando el estado durante el render (patrón "adjusting state when a prop changes" que ya usan `CampoAutocompletar.tsx:32-36` y `useErrorServidor.ts:6-12`, y que no dispara `react-hooks/set-state-in-effect`). Si la consulta falla, las líneas precargadas van vacías (calco: un id que no está en la lista deja la línea vacía) y el diálogo global del `QueryCache` avisa del fallo.

**Errores al guardar (spec §6, §8):** `useGuardarLoteCompras` lleva `meta: { silenciarError: true }` (T10). `mensajeErrorGuardado`: 422 (`ReglaNegocioError`) y 409 (`StaleDataError`) → su mensaje en la línea de error; 401/5xx sin mensaje/red (`esErrorGestionadoGlobalmente`) → `null` (el `MutationCache` ya abre el diálogo de conexión y el banner, `src/shared/api/queryClient.ts:65-72`; el formulario sigue abierto y el reintento reutiliza la clave); el **503 con mensaje** del tipo de cambio llega como `ReglaNegocioError` desde T7 (Step 13-16) y se pinta inline como un 422; cualquier otro (403, 404, 400) → `"Error al guardar: " + mensaje`. Cualquier cambio en las líneas borra la línea de error.

- [ ] **Step 1: Test de `errores.ts` (falla)**

`src/modules/almacen/pedidos/formulario/errores.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { ConexionError, PermisoError, ReglaNegocioError, SesionExpiradaError, StaleDataError } from '@/shared/api/errors'
import { mensajeErrorGuardado } from './errores'

describe('mensajeErrorGuardado', () => {
  it('lo que ya avisa un mecanismo global (conexión, sesión) no va a la línea de error', () => {
    expect(mensajeErrorGuardado(new ConexionError(503, 'Sin conexión con el servidor.', 'HTTP 503'))).toBeNull()
    expect(mensajeErrorGuardado(new SesionExpiradaError(401, 'x'))).toBeNull()
  })
  it('409: el mensaje del servidor, o el propio de la vista si se pasa staleData', () => {
    expect(mensajeErrorGuardado(new StaleDataError(409, 'El pedido ya no está pendiente'))).toBe('El pedido ya no está pendiente')
    expect(mensajeErrorGuardado(new StaleDataError(409, 'Dato modificado por otro usuario'), { staleData: 'Modificado.' })).toBe('Modificado.')
  })
  it('422: el mensaje del servidor tal cual', () => {
    expect(mensajeErrorGuardado(new ReglaNegocioError(422, 'Línea 1: el proveedor está desactivado.'))).toBe('Línea 1: el proveedor está desactivado.')
  })
  it('cualquier otro: "Error al guardar: " + mensaje (FC :554)', () => {
    expect(mensajeErrorGuardado(new PermisoError(403, 'No tienes permisos para realizar esta acción.'))).toBe('Error al guardar: No tienes permisos para realizar esta acción.')
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/errores.test.ts`
Expected: FAIL, `Failed to resolve import "./errores"`.

- [ ] **Step 3: Implementar `errores.ts`**

`src/modules/almacen/pedidos/formulario/errores.ts`:

```ts
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'

/** Texto de la línea de error de un formulario de pedido tras un guardado fallido (spec §6 y §8, P6). null = no se pinta
 *  nada: el aviso ya lo da el mecanismo global (banner y diálogo de conexión, o la vuelta al login). `staleData` sustituye
 *  el texto de cualquier 409 (editores: "El pedido fue modificado por otro usuario…", FCE :145-148). */
export function mensajeErrorGuardado(e: unknown, opciones?: { staleData?: string }): string | null {
  if (esErrorGestionadoGlobalmente(e)) return null
  if (e instanceof StaleDataError) return opciones?.staleData ?? e.message
  if (e instanceof ReglaNegocioError) return e.message
  return 'Error al guardar: ' + mensajeDeError(e)
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/errores.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 5: Test de `NuevoPedidoDialog` (falla)**

`src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { COMPONENTES, PROVEEDORES, preventiva, urgente } from './datosPrueba'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

let lotes: { cuerpo: unknown; clave: string | null }[]
let respuestaLote: () => Response
let tasasPedidas: string[]

beforeEach(() => {
  lotes = []
  tasasPedidas = []
  respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(COMPONENTES)),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', ({ params }) => { tasasPedidas.push(String(params.divisa)); return HttpResponse.json({ value: 1.1367 }) }),
    http.post('*/api/compras/lote', async ({ request }) => {
      lotes.push({ cuerpo: await request.json(), clave: request.headers.get('Idempotency-Key') })
      return respuestaLote()
    }),
  )
})

function abrir(precarga: PrecargaPedido = { modo: 'vacio' }) {
  const onCerrar = vi.fn()
  renderConProviders(<NuevoPedidoDialog precarga={precarga} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

/** Por etiqueta y no por rol: si un aviso global abre su diálogo encima, el formulario queda aria-hidden y los *ByRole
 *  dejarían de verlo. */
const filaDe = (n: number) => screen.getByLabelText(`Cantidad línea ${n}`).closest('tr') as HTMLElement
const confirmar = () => userEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }))

async function elegirComponente(n: number, texto: string, opcion: string) {
  await userEvent.type(screen.getByRole('combobox', { name: `Componente línea ${n}` }), texto)
  await userEvent.click(await screen.findByRole('option', { name: opcion }))
}

/** ComboNavy: disparador role="combobox" con aria-label; lista role="listbox" con el mismo nombre; cada opción es un
 *  <li role="option"> con un <button> dentro, que es quien recibe el clic (ComboNavy.tsx:79-118). */
async function elegirProveedor(n: number, nombre: string) {
  await userEvent.click(screen.getByRole('combobox', { name: `Proveedor línea ${n}` }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: `Proveedor línea ${n}` })).findByRole('button', { name: nombre }))
}

async function escribir(etiqueta: string, valor: string) {
  const campo = screen.getByRole('textbox', { name: etiqueta })
  await userEvent.clear(campo)
  await userEvent.type(campo, valor)
}

describe('NuevoPedidoDialog', () => {
  it('vacío: título de 24 px, siete columnas, placeholder y los tres botones', async () => {
    abrir()
    const dlg = within(await screen.findByRole('dialog', { name: 'Nuevo pedido' }))
    expect(dlg.getByRole('heading', { name: 'Nuevo pedido' })).toHaveClass('text-2xl', 'font-bold', 'text-azul-medio')
    expect(screen.getByRole('dialog')).toHaveClass('w-[700px]', 'bg-fondo-vista', 'p-7')
    expect(dlg.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Componente', 'Proveedor', 'Cant.', 'P.Unit.', 'Urg.', 'Total EUR', ''])
    expect(dlg.getByText('Añade al menos una línea')).toBeInTheDocument()
    // La ✕ de DialogContent ("Close", sr-only) va la última.
    expect(dlg.getAllByRole('button').map((b) => b.textContent)).toEqual(['+ Añadir línea', 'Cancelar', 'Confirmar pedido', 'Close'])
  })

  it('sin líneas: "Añade al menos una línea." y no envía nada', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar pedido' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Añade al menos una línea.')
    expect(screen.getByRole('alert')).toHaveClass('text-[11px]', 'text-texto-error')
    expect(lotes).toHaveLength(0)
  })

  it('"Pedir" (un componente): una línea con él, cantidad 1, precio 0,00, sin proveedor ni urgente; "+ Añadir línea" lo repite y selecciona la nueva', async () => {
    abrir({ modo: 'componentes', idsCom: [2] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x')
    expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' }).textContent).toBe('')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 1' })).toHaveValue('1')
    expect(screen.getByRole('textbox', { name: 'Precio línea 1' })).toHaveValue('0,00')
    expect(screen.getByRole('checkbox', { name: 'Urgente línea 1' })).not.toBeChecked()
    expect(within(filaDe(1)).getByText('0,00 €')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('bat-x')
    expect(filaDe(2)).toHaveAttribute('data-state', 'selected')
    expect(filaDe(1)).not.toHaveAttribute('data-state')
  })

  it('componente desactivado o desconocido: la línea va vacía (calco), con su placeholder', async () => {
    abrir({ modo: 'componentes', idsCom: [4, 99] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('')
    const primera = screen.getByRole('combobox', { name: 'Componente línea 1' })
    expect(primera).toHaveValue('')
    expect(primera).toHaveAttribute('placeholder', 'Escribe componente...')
  })

  it('"Pedir todas las piezas" (N componentes): una línea por componente en orden; "+ Añadir línea" añade una vacía', async () => {
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('lcd-x-negro')
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('bat-x')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 2' })).toHaveValue('1')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    expect(screen.getByRole('combobox', { name: 'Componente línea 3' })).toHaveValue('')
  })

  it('"Pedir piezas": agrupa por componente con urgentes primero, cantidad = nº de solicitudes, y avisa de las omitidas', async () => {
    abrir({ modo: 'solicitudes', urgentes: [urgente(10, 2), urgente(11, 4), urgente(12, 1)], preventivas: [preventiva(20, 2), preventiva(21, 4)] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 1' })).toHaveValue('2')
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('lcd-x-negro')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 2' })).toHaveValue('1')
    expect(screen.queryByRole('combobox', { name: 'Componente línea 3' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('2 solicitud(es) de componentes desactivados no se han añadido y siguen pendientes.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('autocompletar: solo activos (con los slaves de SKU compartido), filtro "contiene" y Enter elige el primero', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    const campo = screen.getByRole('combobox', { name: 'Componente línea 1' })
    await userEvent.type(campo, 'x')
    await waitFor(() => expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['lcd-x-negro', 'bat-x']))
    await userEvent.clear(campo)
    await userEvent.type(campo, 'bat')
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['bat-x', 'bat-y'])
    await userEvent.type(campo, '{Enter}')
    expect(campo).toHaveValue('bat-x')
  })

  it('validación en la línea de error, en orden y parando en el primer fallo; cambiar una línea la borra', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: selecciona un componente.')
    await elegirComponente(1, 'bat', 'bat-x')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: selecciona un proveedor.')
    await elegirProveedor(1, 'ACME')
    await escribir('Cantidad línea 1', '0')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: la cantidad debe ser mayor que 0.')
    await escribir('Cantidad línea 1', '2')
    await escribir('Precio línea 1', '-1')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: el precio no puede ser negativo.')
    expect(lotes).toHaveLength(0)
  })

  it('proveedor en USD: solo activos en el combo, símbolo $ y Total EUR = precio / tasa × cantidad; en EUR, € sin pedir tasa', async () => {
    abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor línea 1' }))
    const lista = screen.getByRole('listbox', { name: 'Proveedor línea 1' })
    await within(lista).findByRole('button', { name: 'ACME' })
    expect(within(lista).getAllByRole('button').map((b) => b.textContent)).toEqual(['ACME', 'Proveedor B'])
    await userEvent.click(within(lista).getByRole('button', { name: 'Proveedor B' }))
    await escribir('Precio línea 1', '10')
    await escribir('Cantidad línea 1', '2')
    expect(within(filaDe(1)).getByText('$')).toBeInTheDocument()
    expect(await within(filaDe(1)).findByText('17,59 €')).toBeInTheDocument()
    await elegirProveedor(1, 'ACME')
    expect(within(filaDe(1)).getByText('€')).toBeInTheDocument()
    expect(within(filaDe(1)).getByText('20,00 €')).toBeInTheDocument()
    expect(tasasPedidas).toEqual(['USD'])
  })

  it('mientras llega la tasa el Total EUR es "—" (P7)', async () => {
    server.use(http.get('*/api/tipo-cambio/:divisa', async () => { await delay('infinite'); return HttpResponse.json({ value: 1.1367 }) }))
    abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'Proveedor B')
    expect(within(filaDe(1)).getByText('—')).toBeInTheDocument()
  })

  it('si la tasa falla, el Total EUR se queda en "—" (nunca calcula como si fuera EUR)', async () => {
    let pedidas = 0
    server.use(http.get('*/api/tipo-cambio/:divisa', () => {
      pedidas += 1
      return HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })
    }))
    abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'Proveedor B')
    await waitFor(() => expect(pedidas).toBe(1))
    expect(within(filaDe(1)).getByText('—')).toBeInTheDocument()
    expect(within(filaDe(1)).queryByText('0,00 €')).not.toBeInTheDocument()
  })

  it('"Pedir piezas" → un lote con clave y el cuerpo exacto (solo las solicitudes que siguen teniendo línea); cierra', async () => {
    const onCerrar = abrir({ modo: 'solicitudes', urgentes: [urgente(10, 2), urgente(11, 1)], preventivas: [preventiva(20, 2)] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('button', { name: 'Quitar línea 2' }))
    expect(screen.queryByRole('combobox', { name: 'Componente línea 2' })).not.toBeInTheDocument()
    await elegirProveedor(1, 'ACME')
    await escribir('Precio línea 1', '12,5')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Urgente línea 1' }))
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(1)
    expect(lotes[0].clave).toMatch(/^[0-9a-f-]{36}$/)
    expect(lotes[0].cuerpo).toEqual({
      lineas: [{ idCom: 2, idProv: 1, cantidad: 2, esUrgente: true, precioUnidad: 12.5 }],
      solicitudes: { urgentes: [10], preventivas: [20] },
    })
  })

  it('422: su mensaje en la línea de error con el formulario abierto; el reintento usa la MISMA clave', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'Línea 1: el proveedor está desactivado.' }, { status: 422 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'ACME')
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Línea 1: el proveedor está desactivado.')
    expect(screen.getByRole('dialog', { name: 'Nuevo pedido' })).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
    respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(2)
    expect(lotes[1].clave).toBeTruthy()
    expect(lotes[1].clave).toBe(lotes[0].clave)
  })

  it('otro error (403): "Error al guardar: …" en la línea de error, formulario abierto', async () => {
    respuestaLote = () => new HttpResponse(null, { status: 403 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'ACME')
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Error al guardar: No tienes permisos para realizar esta acción.')
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('503 del tipo de cambio al guardar: el mensaje del servidor inline, sin aviso global y con el formulario abierto', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'ACME')
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.')
    expect(screen.queryByText('Sin conexión con el servidor: HTTP 503')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('500 sin mensaje al guardar: aviso global de conexión, sin línea de error y con el formulario abierto', async () => {
    respuestaLote = () => new HttpResponse(null, { status: 500 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await elegirProveedor(1, 'ACME')
    await confirmar()
    expect(await screen.findByText('Sin conexión con el servidor: HTTP 500')).toBeInTheDocument()
    expect(screen.queryByText(/^Error al guardar/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('"Cancelar" cierra sin preguntar aunque haya líneas (calco) y no envía nada', async () => {
    const onCerrar = abrir({ modo: 'componentes', idsCom: [1, 2] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(lotes).toHaveLength(0)
  })

  it('enfocar cualquier campo selecciona su fila (navy); la papelera quita la línea sin confirmar', async () => {
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('textbox', { name: 'Cantidad línea 2' }))
    expect(filaDe(2)).toHaveAttribute('data-state', 'selected')
    expect(filaDe(2)).toHaveClass('data-[state=selected]:bg-azul-medio', 'data-[state=selected]:text-crema')
    expect(filaDe(1)).not.toHaveAttribute('data-state')
    await userEvent.click(screen.getByRole('combobox', { name: 'Componente línea 1' }))
    expect(filaDe(1)).toHaveAttribute('data-state', 'selected')
    const papelera = screen.getByRole('button', { name: 'Quitar línea 1' })
    expect(papelera.querySelector('img')).toHaveAttribute('src', '/borrar.png')
    await userEvent.click(papelera)
    expect(screen.getByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x')
    expect(screen.queryByRole('combobox', { name: 'Componente línea 2' })).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`
Expected: FAIL, `Failed to resolve import "./NuevoPedidoDialog"`.

- [ ] **Step 7: Implementar `DialogoLineas.tsx`**

`src/modules/almacen/pedidos/formulario/DialogoLineas.tsx`:

```tsx
import { useState, type ReactNode } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { formatearImporte, parsearDecimal, parsearEntero, simboloFormulario } from '@/shared/lib/importes'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { Checkbox } from '@/shared/ui/checkbox'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { useTasas } from '../tasa'
import { totalLinea } from './conversion'
import type { LineaBase } from './lineas'

/** Columnas tras la primera (FormularioCompraView.fxml:19-25): Proveedor 155 · Cant. 55 · P.Unit. 80 · Urg. 45 ·
 *  Total EUR 90 · papelera 40 sin cabecera. */
const COLUMNAS = [
  { cabecera: 'Proveedor', ancho: 155 },
  { cabecera: 'Cant.', ancho: 55 },
  { cabecera: 'P.Unit.', ancho: 80 },
  { cabecera: 'Urg.', ancho: 45 },
  { cabecera: 'Total EUR', ancho: 90 },
  { cabecera: '', ancho: 40 },
]
/** ESTILO_EDITABLE del JavaFX (FC :321-379): caja blanca, borde #C2C8D0, radio 3, padding 5 8. */
const CLASE_EDITABLE = 'h-[26px] w-full min-w-0 rounded-[3px] border border-fila-sep bg-superficie px-2 text-[12px] text-azul-medio outline-none focus-visible:ring-2 focus-visible:ring-ring/50'
/** Ancho del combo de proveedor dentro de su celda (155 px menos el padding; cabe también con el reparto proporcional de
 *  "Nuevo otro pedido"). */
const ANCHO_COMBO_PROVEEDOR = 140

type Props<L extends LineaBase> = {
  titulo: string
  primera: { cabecera: string; ancho: number; celda: (linea: L, n: number) => ReactNode }
  lineas: L[]
  proveedores: Proveedor[]
  info: string | null
  error: string | null
  bloqueado: boolean
  enviando: boolean
  onCambiar: (id: number, cambio: Partial<LineaBase>) => void
  onQuitar: (id: number) => void
  onAnadir: () => number
  onConfirmar: () => void
  onCerrar: () => void
}

/** Armazón de "Nuevo pedido" y "Nuevo otro pedido" (FormularioCompraView.fxml / FormularioOtroPedidoView.fxml): modal de
 *  700 px, título de 24 px, tabla de líneas con celdas siempre editables (P8), "+ Añadir línea", línea de información,
 *  línea de error (P6) y "Cancelar" / "Confirmar pedido". La divisa de cada línea es la de su proveedor; sin proveedor la
 *  línea se calcula como EUR (tasa 1.0 por defecto de LineaCompra). Sin atajos de teclado (el JavaFX no tenía). */
export function DialogoLineas<L extends LineaBase>({ titulo, primera, lineas, proveedores, info, error, bloqueado, enviando, onCambiar, onQuitar, onAnadir, onConfirmar, onCerrar }: Props<L>) {
  const [seleccionada, setSeleccionada] = useState<number | null>(null)
  const [anadida, setAnadida] = useState<number | null>(null)
  const porId = new Map(proveedores.map((p) => [p.idProv, p]))
  const divisaDe = (l: LineaBase): string => (l.idProv === null ? undefined : porId.get(l.idProv)?.divisa) ?? 'EUR'
  const tasas = useTasas(Array.from(new Set(lineas.map(divisaDe))))
  const opciones = proveedores.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre }))
  const anchos = [primera.ancho, ...COLUMNAS.map((c) => c.ancho)]
  const suma = anchos.reduce((a, b) => a + b, 0)

  function totalDe(l: L): string {
    const total = totalLinea(parsearDecimal(l.precio), tasas[divisaDe(l)]?.tasa ?? null, parsearEntero(l.cantidad))
    return total === null ? '—' : formatearImporte(total, '€')
  }
  function seleccionar(id: number) {
    setSeleccionada(id)
    setAnadida(null)
  }
  /** "+ Añadir línea" (añadirFila :496-513): añade, selecciona y desplaza hasta la línea nueva. */
  function anadir() {
    const id = onAnadir()
    setSeleccionada(id)
    setAnadida(id)
  }

  return (
    <Dialog open onOpenChange={(abierto) => { if (!abierto && !enviando) onCerrar() }}>
      <DialogContent aria-describedby={undefined} className="max-h-[calc(100vh-24px)] w-[700px] max-w-[min(700px,calc(100%-2rem))] gap-4 overflow-y-auto bg-fondo-vista p-7 sm:max-w-[min(700px,calc(100%-2rem))]">
        <DialogTitle className="text-2xl font-bold text-azul-medio">{titulo}</DialogTitle>
        {/* Sin contenedor con overflow: recortaría el popup del autocompletar. Quien desplaza es el DialogContent. */}
        <div className="min-h-[220px] rounded-md bg-superficie">
          <table className="w-full table-fixed text-sm">
            <colgroup>
              {anchos.map((a, i) => <col key={i} style={{ width: `${(a / suma) * 100}%` }} />)}
            </colgroup>
            <TableHeader className="bg-crema">
              <TableRow className="hover:bg-transparent">
                {[primera.cabecera, ...COLUMNAS.map((c) => c.cabecera)].map((c, i) => (
                  <TableHead key={i} className="h-8 px-1 text-[12px] font-bold text-azul-medio">{c}</TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {lineas.length === 0 && (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={anchos.length} className="py-8 text-center text-azul-gris">Añade al menos una línea</TableCell>
                </TableRow>
              )}
              {lineas.map((l, i) => {
                const n = i + 1
                return (
                  <TableRow
                    key={l.id}
                    ref={l.id === anadida ? (el: HTMLTableRowElement | null) => { el?.scrollIntoView({ block: 'nearest' }) } : undefined}
                    data-state={l.id === seleccionada ? 'selected' : undefined}
                    // Enfocar cualquier campo de la fila la selecciona (FC :203-205); el foco burbujea en React.
                    onFocus={() => seleccionar(l.id)}
                    onClick={() => seleccionar(l.id)}
                    className="group border-b border-fila-sep hover:bg-transparent data-[state=selected]:border-b-fila-selected-brd data-[state=selected]:bg-azul-medio data-[state=selected]:text-crema"
                  >
                    <TableCell className="px-1 py-1">{primera.celda(l, n)}</TableCell>
                    <TableCell className="px-1 py-1">
                      <ComboNavy valor={l.idProv === null ? null : String(l.idProv)} opciones={opciones} onChange={(v) => onCambiar(l.id, { idProv: Number(v) })} textoVacio="" ancho={ANCHO_COMBO_PROVEEDOR} visibles={8} aria-label={`Proveedor línea ${n}`} />
                    </TableCell>
                    <TableCell className="px-1 py-1">
                      <input aria-label={`Cantidad línea ${n}`} value={l.cantidad} onChange={(e) => onCambiar(l.id, { cantidad: e.target.value })} className={CLASE_EDITABLE} />
                    </TableCell>
                    <TableCell className="px-1 py-1">
                      <div className="flex items-center gap-1">
                        <input aria-label={`Precio línea ${n}`} value={l.precio} onChange={(e) => onCambiar(l.id, { precio: e.target.value })} className={CLASE_EDITABLE} />
                        <span className="shrink-0 text-[12px]">{simboloFormulario(divisaDe(l))}</span>
                      </div>
                    </TableCell>
                    <TableCell className="px-1 py-1 text-center">
                      <Checkbox aria-label={`Urgente línea ${n}`} checked={l.urgente} onCheckedChange={(v) => onCambiar(l.id, { urgente: v === true })} className="bg-superficie" />
                    </TableCell>
                    <TableCell className="px-1 py-1 text-[12px]">{totalDe(l)}</TableCell>
                    <TableCell className="px-1 py-1 text-center">
                      <button type="button" aria-label={`Quitar línea ${n}`} onClick={(e) => { e.stopPropagation(); onQuitar(l.id) }} className="cursor-pointer bg-transparent">
                        <img src="/borrar.png" alt="" className="h-[25px] w-[25px]" />
                      </button>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </table>
        </div>
        <div>
          <BotonSecundario type="button" disabled={bloqueado} onClick={anadir}>+ Añadir línea</BotonSecundario>
        </div>
        {info !== null && <p role="status" className="text-[11px] text-azul-gris">{info}</p>}
        {error !== null && <p role="alert" className="text-[11px] text-texto-error">{error}</p>}
        <div className="flex justify-end gap-2.5">
          <BotonSecundario type="button" disabled={enviando} onClick={onCerrar}>Cancelar</BotonSecundario>
          <BotonPrimario type="button" disabled={bloqueado} onClick={onConfirmar}>Confirmar pedido</BotonPrimario>
        </div>
      </DialogContent>
    </Dialog>
  )
}
```

`TableRow` recibe `ref` como prop (React 19; `src/shared/ui/table.tsx:47-58` hace `{...props}` sobre el `<tr>`). `scrollIntoView` ya está simulado en `src/test/setup.ts:18`.

- [ ] **Step 8: Implementar `NuevoPedidoDialog.tsx`**

`src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { useProveedoresComponentes } from '../../proveedores/api'
import { useComponentesStock } from '../../stock/api'
import { useGuardarLoteCompras } from '../api'
import { DialogoLineas } from './DialogoLineas'
import { mensajeErrorGuardado } from './errores'
import {
  avisoOmitidas, cambiarLinea, cuerpoLoteCompras, lineaCompraVacia, precargaInicial, preseleccionDe, quitarLinea, siguienteId,
  validarLineasCompra, type LineaCompra,
} from './lineas'

const OPERACION = 'compras:lote'

type Props = { precarga: PrecargaPedido; onCerrar: () => void }

/** "Nuevo pedido" (FormularioCompraController, spec §6): modal en el sitio (P1) con la precarga del store. Un único
 *  POST /api/compras/lote con Idempotency-Key (P5): misma clave mientras el cuerpo no cambie, nueva tras un éxito. */
export function NuevoPedidoDialog({ precarga, onCerrar }: Props) {
  const componentes = useComponentesStock({ activo: false })
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  const activos = useMemo(() => (componentes.data ?? []).filter((c) => c.activo), [componentes.data])
  const proveedoresActivos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const opciones = useMemo(() => activos.map((c) => ({ clave: String(c.idCom), etiqueta: c.tipo })), [activos])
  const [lineas, setLineas] = useState<LineaCompra[] | null>(precarga.modo === 'vacio' ? [] : null)
  const [omitidas, setOmitidas] = useState(0)
  const [error, setError] = useState<string | null>(null)
  // Una instancia por apertura: el host remonta el diálogo en cada apertura (key), así que no sobrevive a un éxito.
  const [claves] = useState(() => crearClavesIdempotencia())
  const guardar = useGuardarLoteCompras()

  // Precarga una sola vez, cuando la lista de componentes termina de cargar (bien o mal): patrón "ajustar el estado
  // durante el render" (CampoAutocompletar, useErrorServidor), sin useEffect + setState.
  if (lineas === null && (componentes.isSuccess || componentes.isError)) {
    const inicial = precargaInicial(precarga, activos)
    setLineas(inicial.lineas)
    setOmitidas(inicial.omitidas)
  }
  const preseleccion = preseleccionDe(precarga, activos)

  function cambiar(id: number, cambio: Partial<LineaCompra>) {
    setLineas((ls) => cambiarLinea(ls ?? [], id, cambio))
    setError(null)
  }
  function quitar(id: number) {
    setLineas((ls) => quitarLinea(ls ?? [], id))
    setError(null)
  }
  function anadir(): number {
    const id = siguienteId(lineas ?? [])
    setLineas((ls) => [...(ls ?? []), lineaCompraVacia(id, preseleccion)])
    setError(null)
    return id
  }

  async function confirmar() {
    if (lineas === null) return
    const fallo = validarLineasCompra(lineas)
    if (fallo !== null) {
      setError(fallo)
      return
    }
    const origen = precarga.modo === 'solicitudes' ? { urgentes: precarga.urgentes, preventivas: precarga.preventivas } : null
    const cuerpo = cuerpoLoteCompras(lineas, origen)
    setError(null)
    try {
      await guardar.mutateAsync({ cuerpo, clave: claves.para(OPERACION, cuerpo) })
      claves.hecha(OPERACION)
      onCerrar()
    } catch (e) {
      setError(mensajeErrorGuardado(e))
    }
  }

  return (
    <DialogoLineas<LineaCompra>
      titulo="Nuevo pedido"
      primera={{
        cabecera: 'Componente',
        ancho: 175,
        celda: (l, n) => (
          // Fila seleccionada: el campo navy lleva el borde rgba(255,255,255,0.35) del JavaFX (FC :459-473).
          <div className="rounded-full group-data-[state=selected]:ring-1 group-data-[state=selected]:ring-white/35">
            <CampoAutocompletar valor={l.idCom === null ? null : String(l.idCom)} opciones={opciones} onElegir={(c) => cambiar(l.id, { idCom: Number(c) })} placeholder="Escribe componente..." aria-label={`Componente línea ${n}`} />
          </div>
        ),
      }}
      lineas={lineas ?? []}
      proveedores={proveedoresActivos}
      info={avisoOmitidas(omitidas)}
      error={error}
      bloqueado={guardar.isPending || lineas === null}
      enviando={guardar.isPending}
      onCambiar={cambiar}
      onQuitar={quitar}
      onAnadir={anadir}
      onConfirmar={() => void confirmar()}
      onCerrar={onCerrar}
    />
  )
}
```

- [ ] **Step 9: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`
Expected: PASS, 18 tests.

- [ ] **Step 10: Test del host y del montaje en el shell (falla)**

`src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx`:

```tsx
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { abrirNuevoPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { COMPONENTES, PROVEEDORES } from './datosPrueba'
import { FormulariosPedido } from './FormulariosPedido'

beforeEach(() => {
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(COMPONENTES)),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
  )
})

describe('FormulariosPedido', () => {
  it('sin formulario abierto no pinta nada', () => {
    renderConProviders(<FormulariosPedido />, { sesion: SESION_SUPER })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('abrirNuevoPedido pinta "Nuevo pedido"; "Cancelar" vacía el store y lo quita', async () => {
    renderConProviders(<FormulariosPedido />, { sesion: SESION_SUPER })
    act(() => abrirNuevoPedido({ modo: 'vacio' }))
    expect(await screen.findByRole('dialog', { name: 'Nuevo pedido' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(formularioPedido.get()).toBeNull()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('una apertura nueva con el formulario abierto lo remonta con su precarga (key por apertura)', async () => {
    renderConProviders(<FormulariosPedido />, { sesion: SESION_SUPER })
    act(() => abrirNuevoPedido({ modo: 'componentes', idsCom: [2] }))
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x')
    act(() => abrirNuevoPedido({ modo: 'componentes', idsCom: [1] }))
    expect(await screen.findByDisplayValue('lcd-x-negro')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('bat-x')).not.toBeInTheDocument()
  })
})
```

`src/app/shell/AppLayout.test.tsx`:

```tsx
import { act, screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { abrirNuevoPedido } from '@/shared/lib/formularioPedido'
import { renderConProviders, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { AppLayout } from './AppLayout'

describe('AppLayout', () => {
  // SESION_TEC: el layout no pide nada más con ese rol (patrón de ConnectionBanner.test.tsx); el host no mira el rol,
  // quien abre el formulario sí (campana, Stock y Pedidos solo lo ofrecen al SUPERTECNICO).
  it('monta el host de los formularios de pedido: abrirNuevoPedido pinta el modal sobre la vista (P1)', async () => {
    server.use(
      http.get('*/api/componentes/gestionados', () => HttpResponse.json([])),
      http.get('*/api/proveedores', () => HttpResponse.json([])),
    )
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    act(() => abrirNuevoPedido({ modo: 'vacio' }))
    expect(await screen.findByRole('dialog', { name: 'Nuevo pedido' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 11: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx src/app/shell/AppLayout.test.tsx`
Expected: FAIL, `Failed to resolve import "./FormulariosPedido"` y, en `AppLayout.test.tsx`, `Unable to find role="dialog"`.

- [ ] **Step 12: Implementar el host y montarlo**

`src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx`:

```tsx
import { useState } from 'react'
import { cerrarFormularioPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { useStore } from '@/shared/lib/store'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

/** Host del shell (P1): pinta el formulario de alta que diga el store compartido. Stock actual, Pedidos y la campana (que
 *  vive en `taller` y no puede importar de `almacen`) solo llaman a `abrirNuevoPedido` / `abrirNuevoOtroPedido`. */
export function FormulariosPedido() {
  const [abierto] = useStore(formularioPedido)
  // Una apertura = un montaje: cada apertura guarda un objeto nuevo en el store, así que si se abre otro formulario con
  // uno ya abierto, `apertura` cambia, la `key` también y el diálogo empieza de cero (líneas, clave, errores).
  const [visto, setVisto] = useState(abierto)
  const [apertura, setApertura] = useState(0)
  if (abierto !== visto) {
    setVisto(abierto)
    setApertura((n) => n + 1)
  }
  if (abierto === null) return null
  if (abierto.tipo === 'compra') return <NuevoPedidoDialog key={apertura} precarga={abierto.precarga} onCerrar={cerrarFormularioPedido} />
  return null
}
```

`src/app/shell/AppLayout.tsx` (fichero completo; el host va dentro de `ExportableProvider`, fuera del `<main>`, y bajo los providers de `main.tsx`: `QueryClientProvider`, `SessionProvider`, `AlertaProvider` y el router):

```tsx
import { Outlet } from 'react-router'
import { FormulariosPedido } from '@/modules/almacen/pedidos/formulario/FormulariosPedido'
import { ExportableProvider } from '@/shared/ui/exportable'
import { TopBar } from './TopBar'
import { ConnectionBanner } from './ConnectionBanner'
import { SubNav } from './SubNav'

export function AppLayout() {
  return (
    <ExportableProvider>
      <div className="flex min-h-screen flex-col bg-fondo-vista">
        <TopBar />
        <ConnectionBanner />
        <div className="flex flex-1">
          <SubNav />
          <main className="flex-1">
            <Outlet />
          </main>
        </div>
      </div>
      {/* Formularios de alta de pedido (P1): modal sobre la vista actual, abierto desde el store compartido. */}
      <FormulariosPedido />
    </ExportableProvider>
  )
}
```

(`app` puede importar de `@/modules/...`: la regla de fronteras de `eslint.config.js` solo restringe `shared` y `modules`; `TopBar.tsx:3` ya importa `Campana` así.)

- [ ] **Step 13: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario src/app/shell`
Expected: PASS; los ficheros nuevos de esta tarea suman 26 tests (4 + 18 + 3 + 1) y los de `src/app/shell` que ya existían siguen en verde.

- [ ] **Step 14: Lint y tipos**

Run: `npm run lint && npm run typecheck`
Expected: sin errores ni warnings (`FormulariosPedido.tsx`, `NuevoPedidoDialog.tsx` y `DialogoLineas.tsx` exportan solo un componente; los helpers están en `.ts`).

- [ ] **Step 15: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/errores.ts src/modules/almacen/pedidos/formulario/errores.test.ts src/modules/almacen/pedidos/formulario/DialogoLineas.tsx src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx src/app/shell/AppLayout.tsx src/app/shell/AppLayout.test.tsx
git commit -m "feat(pedidos): formulario nuevo pedido como modal del shell, con lote idempotente, tasa por divisa y errores inline"
```

Recuento: **+26 tests** (4 `errores`, 18 `NuevoPedidoDialog`, 3 `FormulariosPedido`, 1 `AppLayout`).

---

## Task 16: Web — `NuevoOtroPedidoDialog` y su rama en el host

**Files:**
- Create: `src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.tsx`, `NuevoOtroPedidoDialog.test.tsx`
- Modify: `src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx` (rama `tipo === 'otro'`), `FormulariosPedido.test.tsx` (+1 test)

**Interfaces:**
- Consumes: `DialogoLineas` y `mensajeErrorGuardado` (T15); `lineaOtroVacia`, `validarLineasOtro`, `cuerpoLoteOtros`, `cambiarLinea`, `quitarLinea`, `siguienteId`, `LineaOtro` (T14); `useGuardarLoteOtros` de `../api` (T10); `abrirNuevoOtroPedido` (T8); `crearClavesIdempotencia` (T7); `useProveedoresComponentes` (4a).
- Produces:

```ts
export function NuevoOtroPedidoDialog({ onCerrar }: { onCerrar: () => void }): JSX.Element
```

Calco de `FormularioOtroPedidoController` (inventario §11): igual que "Nuevo pedido" con **Concepto** (200 px, `TextField` navy de texto libre, placeholder **"Escribe concepto..."**, sin popup) en lugar de Componente; solo carga proveedores (no pide `/api/componentes/gestionados`); título **"Nuevo otro pedido"**; validación con el concepto primero; `POST /api/compras-otros/lote`, operación `'compras-otros:lote'`; tras guardar, T10 recarga `['compras']`.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { PROVEEDORES } from './datosPrueba'
import { NuevoOtroPedidoDialog } from './NuevoOtroPedidoDialog'

let lotes: { cuerpo: unknown; clave: string | null }[]
let respuestaLote: () => Response
let pedidasComponentes: number

beforeEach(() => {
  lotes = []
  pedidasComponentes = 0
  respuestaLote = () => HttpResponse.json({ idsCreados: [201] })
  server.use(
    http.get('*/api/componentes/gestionados', () => { pedidasComponentes += 1; return HttpResponse.json([]) }),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    http.post('*/api/compras-otros/lote', async ({ request }) => {
      lotes.push({ cuerpo: await request.json(), clave: request.headers.get('Idempotency-Key') })
      return respuestaLote()
    }),
  )
})

function abrir() {
  const onCerrar = vi.fn()
  renderConProviders(<NuevoOtroPedidoDialog onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

const filaDe = (n: number) => screen.getByLabelText(`Cantidad línea ${n}`).closest('tr') as HTMLElement
const confirmar = () => userEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }))

async function elegirProveedor(n: number, nombre: string) {
  await userEvent.click(screen.getByRole('combobox', { name: `Proveedor línea ${n}` }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: `Proveedor línea ${n}` })).findByRole('button', { name: nombre }))
}

async function escribir(etiqueta: string, valor: string) {
  const campo = screen.getByRole('textbox', { name: etiqueta })
  await userEvent.clear(campo)
  await userEvent.type(campo, valor)
}

describe('NuevoOtroPedidoDialog', () => {
  it('título "Nuevo otro pedido", columna Concepto en vez de Componente, placeholder y sin pedir componentes', async () => {
    abrir()
    const dlg = within(await screen.findByRole('dialog', { name: 'Nuevo otro pedido' }))
    expect(dlg.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Concepto', 'Proveedor', 'Cant.', 'P.Unit.', 'Urg.', 'Total EUR', ''])
    expect(dlg.getByText('Añade al menos una línea')).toBeInTheDocument()
    expect(pedidasComponentes).toBe(0)
  })

  it('"+ Añadir línea": concepto navy vacío con su placeholder, cantidad 1, precio 0,00', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    const concepto = screen.getByRole('textbox', { name: 'Concepto línea 1' })
    expect(concepto).toHaveValue('')
    expect(concepto).toHaveAttribute('placeholder', 'Escribe concepto...')
    expect(concepto).toHaveClass('rounded-full', 'bg-azul-noche', 'text-crema')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 1' })).toHaveValue('1')
    expect(screen.getByRole('textbox', { name: 'Precio línea 1' })).toHaveValue('0,00')
    expect(filaDe(1)).toHaveAttribute('data-state', 'selected')
  })

  it('validación en orden: concepto en blanco → proveedor → cantidad → precio', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await escribir('Concepto línea 1', '   ')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: el concepto no puede estar vacío.')
    await escribir('Concepto línea 1', 'Cinta de embalar')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: selecciona un proveedor.')
    await elegirProveedor(1, 'ACME')
    await escribir('Cantidad línea 1', 'abc')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: la cantidad debe ser mayor que 0.')
    await escribir('Cantidad línea 1', '1')
    await escribir('Precio línea 1', '-2')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: el precio no puede ser negativo.')
    expect(lotes).toHaveLength(0)
  })

  it('lote con clave y cuerpo exacto (concepto recortado); USD con $ y total convertido; cierra', async () => {
    const onCerrar = abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await escribir('Concepto línea 1', '  Cinta de embalar  ')
    await elegirProveedor(1, 'Proveedor B')
    await escribir('Cantidad línea 1', '3')
    await escribir('Precio línea 1', '1,5')
    expect(within(filaDe(1)).getByText('$')).toBeInTheDocument()
    expect(await within(filaDe(1)).findByText('3,96 €')).toBeInTheDocument()
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(1)
    expect(lotes[0].clave).toMatch(/^[0-9a-f-]{36}$/)
    expect(lotes[0].cuerpo).toEqual({ lineas: [{ idProv: 2, concepto: 'Cinta de embalar', cantidad: 3, esUrgente: false, precioUnidad: 1.5 }] })
  })

  it('422 inline con el formulario abierto; el reintento usa la MISMA clave', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'Línea 1: el proveedor está desactivado.' }, { status: 422 })
    const onCerrar = abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await escribir('Concepto línea 1', 'Cinta de embalar')
    await elegirProveedor(1, 'ACME')
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Línea 1: el proveedor está desactivado.')
    expect(onCerrar).not.toHaveBeenCalled()
    respuestaLote = () => HttpResponse.json({ idsCreados: [201] })
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(2)
    expect(lotes[1].clave).toBe(lotes[0].clave)
  })

  it('"Cancelar" cierra sin preguntar', async () => {
    const onCerrar = abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(lotes).toHaveLength(0)
  })
})
```

Añadir al `describe` de `src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx` (y `abrirNuevoOtroPedido` al import de `@/shared/lib/formularioPedido`):

```tsx
  it('abrirNuevoOtroPedido pinta "Nuevo otro pedido"', async () => {
    renderConProviders(<FormulariosPedido />, { sesion: SESION_SUPER })
    act(() => abrirNuevoOtroPedido())
    expect(await screen.findByRole('dialog', { name: 'Nuevo otro pedido' })).toBeInTheDocument()
  })
```

con el import completo:

```tsx
import { abrirNuevoOtroPedido, abrirNuevoPedido, formularioPedido } from '@/shared/lib/formularioPedido'
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx`
Expected: FAIL, `Failed to resolve import "./NuevoOtroPedidoDialog"`.

- [ ] **Step 3: Implementar**

`src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import { useProveedoresComponentes } from '../../proveedores/api'
import { useGuardarLoteOtros } from '../api'
import { DialogoLineas } from './DialogoLineas'
import { mensajeErrorGuardado } from './errores'
import { cambiarLinea, cuerpoLoteOtros, lineaOtroVacia, quitarLinea, siguienteId, validarLineasOtro, type LineaOtro } from './lineas'

const OPERACION = 'compras-otros:lote'

type Props = { onCerrar: () => void }

/** "Nuevo otro pedido" (FormularioOtroPedidoController, inventario §11): la tabla de "Nuevo pedido" con Concepto de texto
 *  libre (FO :178-182, sin popup) en vez de Componente. Solo carga proveedores. Un POST /api/compras-otros/lote. */
export function NuevoOtroPedidoDialog({ onCerrar }: Props) {
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  const proveedoresActivos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const [lineas, setLineas] = useState<LineaOtro[]>([])
  const [error, setError] = useState<string | null>(null)
  const [claves] = useState(() => crearClavesIdempotencia())
  const guardar = useGuardarLoteOtros()

  function cambiar(id: number, cambio: Partial<LineaOtro>) {
    setLineas((ls) => cambiarLinea(ls, id, cambio))
    setError(null)
  }
  function quitar(id: number) {
    setLineas((ls) => quitarLinea(ls, id))
    setError(null)
  }
  function anadir(): number {
    const id = siguienteId(lineas)
    setLineas((ls) => [...ls, lineaOtroVacia(id)])
    setError(null)
    return id
  }

  async function confirmar() {
    const fallo = validarLineasOtro(lineas)
    if (fallo !== null) {
      setError(fallo)
      return
    }
    const cuerpo = cuerpoLoteOtros(lineas)
    setError(null)
    try {
      await guardar.mutateAsync({ cuerpo, clave: claves.para(OPERACION, cuerpo) })
      claves.hecha(OPERACION)
      onCerrar()
    } catch (e) {
      setError(mensajeErrorGuardado(e))
    }
  }

  return (
    <DialogoLineas<LineaOtro>
      titulo="Nuevo otro pedido"
      primera={{
        cabecera: 'Concepto',
        ancho: 200,
        celda: (l, n) => (
          <input
            aria-label={`Concepto línea ${n}`}
            value={l.concepto}
            placeholder="Escribe concepto..."
            onChange={(e) => cambiar(l.id, { concepto: e.target.value })}
            className="w-full rounded-full bg-azul-noche px-3 py-1 text-[12px] font-bold text-crema outline-none placeholder:text-crema/45 group-data-[state=selected]:ring-1 group-data-[state=selected]:ring-white/35"
          />
        ),
      }}
      lineas={lineas}
      proveedores={proveedoresActivos}
      info={null}
      error={error}
      bloqueado={guardar.isPending}
      enviando={guardar.isPending}
      onCambiar={cambiar}
      onQuitar={quitar}
      onAnadir={anadir}
      onConfirmar={() => void confirmar()}
      onCerrar={onCerrar}
    />
  )
}
```

`src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx` (fichero completo):

```tsx
import { useState } from 'react'
import { cerrarFormularioPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { useStore } from '@/shared/lib/store'
import { NuevoOtroPedidoDialog } from './NuevoOtroPedidoDialog'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

/** Host del shell (P1): pinta el formulario de alta que diga el store compartido. Stock actual, Pedidos y la campana (que
 *  vive en `taller` y no puede importar de `almacen`) solo llaman a `abrirNuevoPedido` / `abrirNuevoOtroPedido`. */
export function FormulariosPedido() {
  const [abierto] = useStore(formularioPedido)
  // Una apertura = un montaje: cada apertura guarda un objeto nuevo en el store, así que si se abre otro formulario con
  // uno ya abierto, `apertura` cambia, la `key` también y el diálogo empieza de cero (líneas, clave, errores).
  const [visto, setVisto] = useState(abierto)
  const [apertura, setApertura] = useState(0)
  if (abierto !== visto) {
    setVisto(abierto)
    setApertura((n) => n + 1)
  }
  if (abierto === null) return null
  if (abierto.tipo === 'otro') return <NuevoOtroPedidoDialog key={apertura} onCerrar={cerrarFormularioPedido} />
  return <NuevoPedidoDialog key={apertura} precarga={abierto.precarga} onCerrar={cerrarFormularioPedido} />
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx`
Expected: PASS, 10 tests (6 + 4).

- [ ] **Step 5: Lint y tipos**

Run: `npm run lint && npm run typecheck`
Expected: sin errores ni warnings.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.tsx src/modules/almacen/pedidos/formulario/NuevoOtroPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.tsx src/modules/almacen/pedidos/formulario/FormulariosPedido.test.tsx
git commit -m "feat(pedidos): formulario nuevo otro pedido con concepto libre y lote idempotente"
```

Recuento: **+7 tests** (6 `NuevoOtroPedidoDialog`, 1 `FormulariosPedido`).

---

## Task 17: Web — "Editar pedido" de componentes y de otros, y su cableado en `PedidosPage`

**Files:**
- Modify: `src/modules/almacen/ui/DialogoAlmacen.tsx` (prop `ancho?: 360 | 520`), `src/modules/almacen/ui/DialogoAlmacen.test.tsx` (+2 tests)
- Create: `src/modules/almacen/pedidos/formulario/edicion.ts`, `edicion.test.ts`
- Create: `src/modules/almacen/pedidos/formulario/EditarPedidoDialog.tsx`, `EditarPedidoDialog.test.tsx`
- Create: `src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.tsx`, `EditarOtroPedidoDialog.test.tsx`
- Modify: `src/modules/almacen/pedidos/PedidosPage.tsx` (T13: pinta los dos editores con `editando`)
- Create: `src/modules/almacen/pedidos/PedidosPage.editar.test.tsx`

**Interfaces:**
- Consumes: `DialogoAlmacen` (4a); `useEditarCompra`, `useEditarOtro` de `../api` (T10; `precioEur: null` lo pone T10); `useTasa`, `EstadoTasa` de `../tasa` (T9); `esCompra` de `../reglas` (T9); `totalLinea`, `etiquetaTasa` (T14); `mensajeErrorGuardado` (T15); `formatearNumero`, `formatearImporte`, `parsearDecimal`, `parsearEntero` (T7); `useProveedoresComponentes` (4a); `ComboNavy`, `Checkbox`, `Input`, `Label`; en `PedidosPage` el `const [editando, setEditando] = useState<Pedido | null>(null)` y el `case 'editar': setEditando(pedido)` que deja T13.
- Produces:

```ts
// DialogoAlmacen.tsx (cambio mínimo)
type Props = { …; ancho?: 360 | 520 }   // 360 por defecto (título 20 px); 520 = editores de pedido (título 24 px, vista-titulo)
// edicion.ts (extra)
export const DIVISAS_EDICION: OpcionCombo[]                    // EUR, USD (FCE :63)
export const MSG_PEDIDO_MODIFICADO = 'El pedido fue modificado por otro usuario. Cierra y recarga los datos.'
export const CLASE_ETIQUETA: string, CLASE_CAMPO: string
export type CamposEdicion = { concepto?: string; idProv: number | null; cantidad: string; precio: string }
export type EdicionValida = { concepto: string | null; idProv: number; cantidad: number; precioUnidad: number }
export function validarEdicion(c: CamposEdicion): { ok: true; valor: EdicionValida } | { ok: false; error: string }
export function textoTotalEdicion(precio: string, cantidad: string, divisa: string, tasa: EstadoTasa): string
// EditarPedidoDialog.tsx / EditarOtroPedidoDialog.tsx
export function EditarPedidoDialog({ pedido, onCerrar }: { pedido: CompraComponente | null; onCerrar: () => void }): JSX.Element
export function EditarOtroPedidoDialog({ pedido, onCerrar }: { pedido: CompraOtro | null; onCerrar: () => void }): JSX.Element
```

Calco de `FormularioCompraEditar.fxml` y `FormularioOtroPedidoEditar.fxml` (leídos en `hotfix/0.16.3`): `VBox` 520 × padding 28 sobre `vista-container`, título `vista-titulo` (24 px) **"Editar pedido #{id}"** (también en otros), `GridPane` con columna de etiquetas de 130 px (`form-label`: 12 px negrita `#586376`), hgap 14 y vgap 12; "Componente:" en `Label` 12 px negrita `#2C3B54`; "Precio unidad:" = campo (placeholder **"0.00"**) + combo de divisa de 84 px; "Total EUR:" 13 px negrita `#2C3B54`; botones **"Cancelar"** y **"Guardar"**. `DialogoAlmacen` (`src/modules/almacen/ui/DialogoAlmacen.tsx:20-38`) no admite ancho (fija `w-[360px]` y título de 20 px): se añade `ancho?: 360 | 520`; con 520 el título pasa a `text-2xl` (24 px), el tamaño de `vista-titulo`. Enter en un campo confirma (el `form` de `DialogoAlmacen`), diferencia menor ya aceptada en 4a (`EditarProveedorDialog.tsx:17-19`).

Reglas (spec §6 "Editar pedido", P2, P7; inventario §12, §13):
- Proveedor: `ComboNavy` con los activos (8 filas); si el del pedido está inactivo queda vacío (calco). Se evalúa en cada render contra la lista (que puede llegar después de abrir).
- Cantidad: precarga la **pedida** (`cantidad`), nunca la recibida (P2).
- Urgente: solo componentes; en otros se conserva `esUrgente` del pedido (calco).
- Precio: `formatearNumero(precioUnidadPedido)` ("12,50"); divisa: combo EUR/USD precargado con la del pedido, independiente del proveedor (calco); una divisa fuera de la lista se ve igual (texto del combo), como el `ComboBox` del JavaFX.
- Total EUR (`textoTotalEdicion`): "Obteniendo tasa…" mientras llega; "Error al obtener tasa" si falla; "—" si precio o cantidad no parsean; si no, `formatearImporte(total, '€')` + `etiquetaTasa(divisa, tasa)`.
- Validación (línea de error de `DialogoAlmacen`, en orden): "El concepto no puede estar vacío." (otros), "Selecciona un proveedor.", "Cantidad no válida (debe ser > 0).", "Precio no válido.".
- Guardar: `PUT` con `{ idProv, cantidad, esUrgente, precioUnidad, divisa, updatedAt }` (+ `concepto` recortado en otros) vía T10. 409 → "El pedido fue modificado por otro usuario. Cierra y recarga los datos." inline, formulario abierto; 422 → su mensaje; otro → "Error al guardar: …" (`mensajeErrorGuardado` con `staleData`). Éxito → `onCerrar()` (T10 recarga en `onSettled`).
- La precarga al abrir usa `useLayoutEffect` con `// eslint-disable-next-line react-hooks/set-state-in-effect`, el mismo patrón de `EditarStockDialog.tsx:19-22` y `EditarProveedorDialog.tsx:26-29`: `PedidosPage` guarda `editando` en estado, así que la referencia no cambia con el sondeo (que además está congelado).

- [ ] **Step 1: Test de `DialogoAlmacen` con `ancho` (falla)**

Añadir al `describe('DialogoAlmacen')` de `src/modules/almacen/ui/DialogoAlmacen.test.tsx`:

```tsx
  it('por defecto: caja de 360 px y título de 20 px', () => {
    montar()
    expect(screen.getByRole('dialog')).toHaveClass('w-[360px]')
    expect(screen.getByRole('heading', { name: 'Editar stock' })).toHaveClass('text-[20px]')
  })
  it('ancho 520 (Editar pedido): caja de 520 px y título de 24 px (vista-titulo)', () => {
    montar({ ancho: 520 })
    expect(screen.getByRole('dialog')).toHaveClass('w-[520px]')
    expect(screen.getByRole('dialog')).not.toHaveClass('w-[360px]')
    expect(screen.getByRole('heading', { name: 'Editar stock' })).toHaveClass('text-2xl')
  })
```

Run: `npx vitest run src/modules/almacen/ui/DialogoAlmacen.test.tsx`
Expected: FAIL en "ancho 520" (error de tipos en `montar({ ancho: 520 })` no bloquea Vitest; falla la aserción `toHaveClass('w-[520px]')`).

- [ ] **Step 2: Implementar el `ancho`**

`src/modules/almacen/ui/DialogoAlmacen.tsx` (fichero completo):

```tsx
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Props = {
  abierto: boolean
  titulo: string
  subtitulo?: string
  error: string | null
  textoAccion: string
  enviando?: boolean
  /** 360 (por defecto): ventanas de StockController, título de 20 px. 520: editores de pedido
   *  (FormularioCompraEditar.fxml, FormularioOtroPedidoEditar.fxml), título `vista-titulo` de 24 px. */
  ancho?: 360 | 520
  onConfirmar: () => void
  onCancelar: () => void
  children: ReactNode
}

const ESTILO_ANCHO: Record<360 | 520, { caja: string; titulo: string }> = {
  360: { caja: 'w-[360px] max-w-[min(360px,calc(100%-2rem))] sm:max-w-[min(360px,calc(100%-2rem))]', titulo: 'text-[20px]' },
  520: { caja: 'w-[520px] max-w-[min(520px,calc(100%-2rem))] sm:max-w-[min(520px,calc(100%-2rem))]', titulo: 'text-2xl' },
}

/** Calco de las ventanas propias de StockController (editarStock, solicitarPieza, editarProveedor): VBox de 360 px con
 *  padding 28 y fondo #DDE1E7, título de 20 px, subtítulo de 12 px gris, campos, error de 11 px rojo y los botones
 *  "Cancelar" (btn-secondary) y la acción (btn-primary) a la derecha. Los TextInputDialog nativos del JavaFX ("Stock
 *  mínimo", "Nuevo proveedor") también pasan por aquí (spec 4a, S5). Enter confirma porque los campos van en un form.
 *  Con `ancho={520}` sirve a los editores de pedido (spec 4b §6). */
export function DialogoAlmacen({ abierto, titulo, subtitulo, error, textoAccion, enviando = false, ancho = 360, onConfirmar, onCancelar, children }: Props) {
  const estilo = ESTILO_ANCHO[ancho]
  return (
    <Dialog open={abierto} onOpenChange={(o) => { if (!o && !enviando) onCancelar() }}>
      <DialogContent {...(subtitulo ? {} : { 'aria-describedby': undefined })} className={cn(estilo.caja, 'gap-3 bg-fondo-vista p-7')}>
        <form onSubmit={(e) => { e.preventDefault(); if (!enviando) onConfirmar() }} className="flex flex-col gap-3">
          <DialogHeader>
            <DialogTitle className={cn(estilo.titulo, 'font-bold text-azul-medio')}>{titulo}</DialogTitle>
            {subtitulo ? <DialogDescription className="whitespace-pre text-[12px] text-azul-gris">{subtitulo}</DialogDescription> : null}
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

Run: `npx vitest run src/modules/almacen/ui/DialogoAlmacen.test.tsx src/modules/almacen/stock src/modules/almacen/proveedores`
Expected: PASS; `DialogoAlmacen.test.tsx` con 8 tests (6 + 2) y los tests de Stock y Proveedores sin cambios (las clases por defecto son las de antes).

- [ ] **Step 3: Test de `edicion.ts` (falla)**

`src/modules/almacen/pedidos/formulario/edicion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { textoTotalEdicion, validarEdicion } from './edicion'

const TASA_EUR = { tasa: 1, cargando: false, error: false }
const TASA_USD = { tasa: 1.1367, cargando: false, error: false }

describe('validarEdicion', () => {
  it('componentes: proveedor → cantidad → precio (FCE :116-136); válido devuelve los números', () => {
    expect(validarEdicion({ idProv: null, cantidad: '0', precio: 'x' })).toEqual({ ok: false, error: 'Selecciona un proveedor.' })
    for (const cantidad of ['0', '-1', 'abc', '1,5', ''])
      expect(validarEdicion({ idProv: 1, cantidad, precio: 'x' })).toEqual({ ok: false, error: 'Cantidad no válida (debe ser > 0).' })
    for (const precio of ['-1', 'abc', ''])
      expect(validarEdicion({ idProv: 1, cantidad: '3', precio })).toEqual({ ok: false, error: 'Precio no válido.' })
    expect(validarEdicion({ idProv: 1, cantidad: ' 3 ', precio: '12,5' })).toEqual({ ok: true, valor: { concepto: null, idProv: 1, cantidad: 3, precioUnidad: 12.5 } })
    expect(validarEdicion({ idProv: 1, cantidad: '3', precio: '0' })).toEqual({ ok: true, valor: { concepto: null, idProv: 1, cantidad: 3, precioUnidad: 0 } })
  })
  it('otros: el concepto (tras trim) va primero (FOE :139-165) y se devuelve recortado', () => {
    expect(validarEdicion({ concepto: '  ', idProv: null, cantidad: '0', precio: 'x' })).toEqual({ ok: false, error: 'El concepto no puede estar vacío.' })
    expect(validarEdicion({ concepto: ' Cinta ancha ', idProv: 1, cantidad: '4', precio: '2' })).toEqual({ ok: true, valor: { concepto: 'Cinta ancha', idProv: 1, cantidad: 4, precioUnidad: 2 } })
  })
})

describe('textoTotalEdicion', () => {
  it('tasa cargando o con error (P7: no calcula como si fuera EUR)', () => {
    expect(textoTotalEdicion('10', '2', 'USD', { tasa: null, cargando: true, error: false })).toBe('Obteniendo tasa…')
    expect(textoTotalEdicion('10', '2', 'USD', { tasa: null, cargando: false, error: true })).toBe('Error al obtener tasa')
  })
  it('EUR: total sin etiqueta; "—" si precio o cantidad no parsean', () => {
    expect(textoTotalEdicion('12,50', '3', 'EUR', TASA_EUR)).toBe('37,50 €')
    expect(textoTotalEdicion('abc', '3', 'EUR', TASA_EUR)).toBe('—')
    expect(textoTotalEdicion('12,50', '', 'EUR', TASA_EUR)).toBe('—')
  })
  it('USD: precio / tasa × cantidad y la etiqueta con 1/tasa', () => {
    expect(textoTotalEdicion('12,50', '3', 'USD', TASA_USD)).toBe('32,99 €  (1 USD = 0,8797 €)')
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/edicion.test.ts`
Expected: FAIL, `Failed to resolve import "./edicion"`.

- [ ] **Step 4: Implementar `edicion.ts`**

`src/modules/almacen/pedidos/formulario/edicion.ts`:

```ts
import { formatearImporte, parsearDecimal, parsearEntero } from '@/shared/lib/importes'
import type { OpcionCombo } from '@/shared/ui/ComboNavy'
import type { EstadoTasa } from '../tasa'
import { etiquetaTasa, totalLinea } from './conversion'

/** Solo EUR y USD, como el combo del editor (FCE :63), independiente de la divisa del proveedor (calco). */
export const DIVISAS_EDICION: OpcionCombo[] = [{ valor: 'EUR', etiqueta: 'EUR' }, { valor: 'USD', etiqueta: 'USD' }]
export const MSG_PEDIDO_MODIFICADO = 'El pedido fue modificado por otro usuario. Cierra y recarga los datos.'
export const MSG_CONCEPTO_VACIO = 'El concepto no puede estar vacío.'
export const MSG_SIN_PROVEEDOR = 'Selecciona un proveedor.'
export const MSG_CANTIDAD_NO_VALIDA = 'Cantidad no válida (debe ser > 0).'
export const MSG_PRECIO_NO_VALIDO = 'Precio no válido.'

/** `form-label` del GridPane (12 px negrita #586376) y el campo `buscador` (como los diálogos de Stock). */
export const CLASE_ETIQUETA = 'text-[12px] font-bold text-azul-gris'
export const CLASE_CAMPO = 'bg-superficie text-[13px] text-azul-medio'

export type CamposEdicion = { concepto?: string; idProv: number | null; cantidad: string; precio: string }
export type EdicionValida = { concepto: string | null; idProv: number; cantidad: number; precioUnidad: number }

/** Validación de "Guardar" de los dos editores, parando en el primer fallo. `concepto` solo viene en otros. */
export function validarEdicion(c: CamposEdicion): { ok: true; valor: EdicionValida } | { ok: false; error: string } {
  if (c.concepto !== undefined && c.concepto.trim() === '') return { ok: false, error: MSG_CONCEPTO_VACIO }
  if (c.idProv === null) return { ok: false, error: MSG_SIN_PROVEEDOR }
  const cantidad = parsearEntero(c.cantidad)
  if (cantidad === null || cantidad <= 0) return { ok: false, error: MSG_CANTIDAD_NO_VALIDA }
  const precio = parsearDecimal(c.precio)
  if (precio === null || precio < 0) return { ok: false, error: MSG_PRECIO_NO_VALIDO }
  return { ok: true, valor: { concepto: c.concepto === undefined ? null : c.concepto.trim(), idProv: c.idProv, cantidad, precioUnidad: precio } }
}

/** "Total EUR:" del editor (calcularTotal, FCE :99-111, con P7): tasa dividida, etiqueta "(1 {DIV} = {1/tasa} €)" y "—"
 *  si precio o cantidad no parsean (también con divisa distinta de EUR). */
export function textoTotalEdicion(precio: string, cantidad: string, divisa: string, tasa: EstadoTasa): string {
  if (tasa.cargando) return 'Obteniendo tasa…'
  if (tasa.error) return 'Error al obtener tasa'
  const total = totalLinea(parsearDecimal(precio), tasa.tasa, parsearEntero(cantidad))
  if (total === null || tasa.tasa === null) return '—'
  return formatearImporte(total, '€') + etiquetaTasa(divisa, tasa.tasa)
}
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/edicion.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 5: Tests de los dos editores (fallan)**

`src/modules/almacen/pedidos/formulario/EditarPedidoDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompraComponente } from '@/shared/api/client'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { COMPRA, PROVEEDORES } from './datosPrueba'
import { EditarPedidoDialog } from './EditarPedidoDialog'

let puts: { id: string; cuerpo: unknown }[]
let respuestaPut: () => Response

beforeEach(() => {
  puts = []
  respuestaPut = () => new HttpResponse(null, { status: 200 })
  server.use(
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    http.put('*/api/compras/:id', async ({ request, params }) => {
      puts.push({ id: String(params.id), cuerpo: await request.json() })
      return respuestaPut()
    }),
  )
})

function abrir(pedido: CompraComponente | null = COMPRA) {
  const onCerrar = vi.fn()
  renderConProviders(<EditarPedidoDialog pedido={pedido} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

const total = () => screen.getByTestId('total-eur').textContent
const guardar = () => userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

async function escribir(etiqueta: string, valor: string) {
  const campo = screen.getByLabelText(etiqueta)
  await userEvent.clear(campo)
  if (valor !== '') await userEvent.type(campo, valor)
}

async function elegirDivisa(divisa: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Divisa' }))
  await userEvent.click(within(screen.getByRole('listbox', { name: 'Divisa' })).getByRole('button', { name: divisa }))
}

describe('EditarPedidoDialog', () => {
  it('pedido null: no pinta nada', () => {
    abrir(null)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('precarga: título, etiquetas en orden, componente de solo lectura, proveedor, cantidad, urgente, precio, divisa y total', async () => {
    abrir()
    const dlg = within(await screen.findByRole('dialog', { name: 'Editar pedido #7' }))
    expect(screen.getByRole('dialog')).toHaveClass('w-[520px]')
    expect(dlg.getByRole('heading', { name: 'Editar pedido #7' })).toHaveClass('text-2xl')
    expect(dlg.getAllByText(/:$/).map((e) => e.textContent)).toEqual(['Componente:', 'Proveedor:', 'Cantidad:', 'Urgente:', 'Precio unidad:', 'Total EUR:'])
    expect(dlg.getByText('bat-x')).toHaveClass('text-[12px]', 'font-bold', 'text-azul-medio')
    expect(await dlg.findByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('ACME')
    expect(dlg.getByLabelText('Cantidad:')).toHaveValue('3')
    expect(dlg.getByLabelText('Cantidad:')).toHaveAttribute('placeholder', 'Ej. 10')
    expect(dlg.getByRole('checkbox', { name: 'Urgente:' })).toBeChecked()
    expect(dlg.getByLabelText('Precio unidad:')).toHaveValue('12,50')
    expect(dlg.getByLabelText('Precio unidad:')).toHaveAttribute('placeholder', '0.00')
    expect(dlg.getByRole('combobox', { name: 'Divisa' })).toHaveTextContent('EUR')
    expect(total()).toBe('37,50 €')
    expect(dlg.getAllByRole('button').map((b) => b.textContent)).toContain('Cancelar')
    expect(dlg.getByRole('button', { name: 'Guardar' })).toBeInTheDocument()
  })

  it('recibido con recepción parcial: precarga la cantidad PEDIDA, no la recibida (P2)', async () => {
    abrir({ ...COMPRA, estado: 'recibido', cantidadRecibida: 2 })
    expect(await screen.findByLabelText('Cantidad:')).toHaveValue('3')
  })

  it('proveedor inactivo: el combo queda vacío (calco) y Guardar pide "Selecciona un proveedor."', async () => {
    abrir({ ...COMPRA, idProv: 3, nombreProveedor: 'Proveedor A' })
    const combo = await screen.findByRole('combobox', { name: 'Proveedor' })
    await waitFor(() => expect(combo.textContent).toBe(''))
    await guardar()
    expect(screen.getByRole('alert')).toHaveTextContent('Selecciona un proveedor.')
    expect(puts).toHaveLength(0)
  })

  it('validación en orden: cantidad y luego precio', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await escribir('Cantidad:', '0')
    await escribir('Precio unidad:', '-1')
    await guardar()
    expect(screen.getByRole('alert')).toHaveTextContent('Cantidad no válida (debe ser > 0).')
    await escribir('Cantidad:', '3')
    await guardar()
    expect(screen.getByRole('alert')).toHaveTextContent('Precio no válido.')
    expect(puts).toHaveLength(0)
  })

  it('precio que no parsea: Total EUR "—"', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await escribir('Precio unidad:', 'abc')
    expect(total()).toBe('—')
  })

  it('divisa USD: total con la tasa dividida y la etiqueta "(1 USD = 0,8797 €)" (P7)', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirDivisa('USD')
    await waitFor(() => expect(total()).toBe('32,99 €  (1 USD = 0,8797 €)'))
  })

  it('mientras llega la tasa: "Obteniendo tasa…"', async () => {
    server.use(http.get('*/api/tipo-cambio/:divisa', async () => { await delay('infinite'); return HttpResponse.json({ value: 1.1367 }) }))
    abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirDivisa('USD')
    expect(total()).toBe('Obteniendo tasa…')
  })

  it('si la tasa falla: "Error al obtener tasa"', async () => {
    server.use(http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })))
    abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirDivisa('USD')
    await waitFor(() => expect(total()).toBe('Error al obtener tasa'))
  })

  it('Guardar: PUT con proveedor, cantidad, urgente, precio, divisa y updatedAt; cierra', async () => {
    const onCerrar = abrir()
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await screen.findByText('ACME')
    await escribir('Cantidad:', '5')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Urgente:' }))
    await escribir('Precio unidad:', '10,5')
    await guardar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(puts).toHaveLength(1)
    expect(puts[0].id).toBe('7')
    expect(puts[0].cuerpo).toMatchObject({ idProv: 1, cantidad: 5, esUrgente: false, precioUnidad: 10.5, divisa: 'EUR', updatedAt: '2026-09-20T10:00:00' })
  })

  it('409: "El pedido fue modificado por otro usuario. Cierra y recarga los datos." inline, formulario abierto', async () => {
    respuestaPut = () => HttpResponse.json({ message: 'Dato modificado por otro usuario' }, { status: 409 })
    const onCerrar = abrir()
    await screen.findByText('ACME')
    await guardar()
    expect(await screen.findByRole('alert')).toHaveTextContent('El pedido fue modificado por otro usuario. Cierra y recarga los datos.')
    expect(screen.getByRole('dialog', { name: 'Editar pedido #7' })).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('422: el mensaje del servidor inline', async () => {
    respuestaPut = () => HttpResponse.json({ message: 'No se puede cambiar la cantidad de un pedido recibido.' }, { status: 422 })
    const onCerrar = abrir({ ...COMPRA, estado: 'recibido', cantidadRecibida: 3 })
    await screen.findByText('ACME')
    await escribir('Cantidad:', '4')
    await guardar()
    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede cambiar la cantidad de un pedido recibido.')
    expect(onCerrar).not.toHaveBeenCalled()
  })
})
```

`src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompraOtro } from '@/shared/api/client'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { OTRO, PROVEEDORES } from './datosPrueba'
import { EditarOtroPedidoDialog } from './EditarOtroPedidoDialog'

let puts: { id: string; cuerpo: unknown }[]
let respuestaPut: () => Response

beforeEach(() => {
  puts = []
  respuestaPut = () => new HttpResponse(null, { status: 200 })
  server.use(
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.put('*/api/compras-otros/:id', async ({ request, params }) => {
      puts.push({ id: String(params.id), cuerpo: await request.json() })
      return respuestaPut()
    }),
  )
})

function abrir(pedido: CompraOtro | null = OTRO) {
  const onCerrar = vi.fn()
  renderConProviders(<EditarOtroPedidoDialog pedido={pedido} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

async function escribir(etiqueta: string, valor: string) {
  const campo = screen.getByLabelText(etiqueta)
  await userEvent.clear(campo)
  if (valor !== '') await userEvent.type(campo, valor)
}

describe('EditarOtroPedidoDialog', () => {
  it('precarga: mismo título "Editar pedido #{id}", "Concepto:" editable con su placeholder y sin "Urgente:"', async () => {
    abrir()
    const dlg = within(await screen.findByRole('dialog', { name: 'Editar pedido #9' }))
    expect(dlg.getAllByText(/:$/).map((e) => e.textContent)).toEqual(['Concepto:', 'Proveedor:', 'Cantidad:', 'Precio unidad:', 'Total EUR:'])
    expect(dlg.getByLabelText('Concepto:')).toHaveValue('Cinta de embalar')
    expect(dlg.getByLabelText('Concepto:')).toHaveAttribute('placeholder', 'Descripción del pedido')
    expect(dlg.getByLabelText('Cantidad:')).toHaveValue('4')
    expect(dlg.getByLabelText('Precio unidad:')).toHaveValue('2,00')
    expect(dlg.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.getByTestId('total-eur').textContent).toBe('8,00 €')
  })

  it('el concepto vacío va antes que el proveedor', async () => {
    abrir({ ...OTRO, idProv: 3 })
    await screen.findByRole('dialog', { name: 'Editar pedido #9' })
    await escribir('Concepto:', '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('El concepto no puede estar vacío.')
    await escribir('Concepto:', 'Cinta ancha')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Selecciona un proveedor.')
    expect(puts).toHaveLength(0)
  })

  it('Guardar: PUT /api/compras-otros/{id} con el concepto recortado y el urgente del pedido conservado; cierra', async () => {
    const onCerrar = abrir()
    await screen.findByText('ACME')
    await escribir('Concepto:', '  Cinta ancha  ')
    await guardarYEsperar(onCerrar)
    expect(puts).toEqual([{ id: '9', cuerpo: expect.objectContaining({ idProv: 1, concepto: 'Cinta ancha', cantidad: 4, esUrgente: true, precioUnidad: 2, divisa: 'EUR', updatedAt: '2026-09-20T10:00:00' }) }])
  })

  it('409: el aviso de modificado inline, formulario abierto', async () => {
    respuestaPut = () => HttpResponse.json({ message: 'El pedido no se puede editar en su estado actual' }, { status: 409 })
    const onCerrar = abrir()
    await screen.findByText('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El pedido fue modificado por otro usuario. Cierra y recarga los datos.')
    expect(onCerrar).not.toHaveBeenCalled()
  })
})

async function guardarYEsperar(onCerrar: ReturnType<typeof vi.fn>) {
  await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
}
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/EditarPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.test.tsx`
Expected: FAIL, `Failed to resolve import "./EditarPedidoDialog"` y `"./EditarOtroPedidoDialog"`.

- [ ] **Step 7: Implementar los dos editores**

`src/modules/almacen/pedidos/formulario/EditarPedidoDialog.tsx`:

```tsx
import { useLayoutEffect, useMemo, useState } from 'react'
import type { CompraComponente } from '@/shared/api/client'
import { formatearNumero } from '@/shared/lib/importes'
import { cn } from '@/shared/lib/utils'
import { Checkbox } from '@/shared/ui/checkbox'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { useProveedoresComponentes } from '../../proveedores/api'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useEditarCompra } from '../api'
import { useTasa } from '../tasa'
import { CLASE_CAMPO, CLASE_ETIQUETA, DIVISAS_EDICION, MSG_PEDIDO_MODIFICADO, textoTotalEdicion, validarEdicion } from './edicion'
import { mensajeErrorGuardado } from './errores'

type Props = { pedido: CompraComponente | null; onCerrar: () => void }

/** "Editar pedido #{id}" de componentes (FormularioCompraEditarController, inventario §12, spec §6). `pedido === null` =
 *  cerrado. La cantidad se precarga con la PEDIDA (P2); el servidor rechaza cambiarla en un recibido (422). */
export function EditarPedidoDialog({ pedido, onCerrar }: Props) {
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  const activos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const opciones = useMemo(() => activos.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre })), [activos])
  const [idProv, setIdProv] = useState<number | null>(null)
  const [cantidad, setCantidad] = useState('')
  const [urgente, setUrgente] = useState(false)
  const [precio, setPrecio] = useState('')
  const [divisa, setDivisa] = useState('EUR')
  const [error, setError] = useState<string | null>(null)
  const editar = useEditarCompra()
  const tasa = useTasa(pedido ? divisa : null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga al abrir con otro pedido (patrón de EditarStockDialog)
    if (pedido) { setIdProv(pedido.idProv); setCantidad(String(pedido.cantidad)); setUrgente(pedido.esUrgente); setPrecio(formatearNumero(pedido.precioUnidadPedido)); setDivisa(pedido.divisa); setError(null) }
  }, [pedido])
  // Proveedor inactivo (o lista aún sin llegar) → combo vacío y "Selecciona un proveedor." al guardar (calco, FCE :70-72).
  const proveedor = idProv !== null && activos.some((p) => p.idProv === idProv) ? idProv : null

  async function guardar() {
    if (!pedido) return
    const v = validarEdicion({ idProv: proveedor, cantidad, precio })
    if (!v.ok) {
      setError(v.error)
      return
    }
    setError(null)
    try {
      await editar.mutateAsync({
        idCompra: pedido.idCompra,
        cuerpo: { idProv: v.valor.idProv, cantidad: v.valor.cantidad, esUrgente: urgente, precioUnidad: v.valor.precioUnidad, divisa, updatedAt: pedido.updatedAt },
      })
      onCerrar()
    } catch (e) {
      setError(mensajeErrorGuardado(e, { staleData: MSG_PEDIDO_MODIFICADO }))
    }
  }

  return (
    <DialogoAlmacen abierto={pedido !== null} ancho={520} titulo={pedido ? `Editar pedido #${pedido.idCompra}` : ''} error={error} textoAccion="Guardar" enviando={editar.isPending} onConfirmar={() => void guardar()} onCancelar={onCerrar}>
      <div className="grid grid-cols-[130px_1fr] items-center gap-x-3.5 gap-y-3">
        <span className={CLASE_ETIQUETA}>Componente:</span>
        <span className="text-[12px] font-bold text-azul-medio">{pedido?.tipoComponente}</span>
        <span className={CLASE_ETIQUETA}>Proveedor:</span>
        <ComboNavy valor={proveedor === null ? null : String(proveedor)} opciones={opciones} onChange={(v) => { setIdProv(Number(v)); setError(null) }} textoVacio="" ancho={320} visibles={8} aria-label="Proveedor" />
        <Label htmlFor="editar-pedido-cantidad" className={CLASE_ETIQUETA}>Cantidad:</Label>
        <Input id="editar-pedido-cantidad" value={cantidad} placeholder="Ej. 10" onChange={(e) => { setCantidad(e.target.value); setError(null) }} className={CLASE_CAMPO} />
        <Label htmlFor="editar-pedido-urgente" className={CLASE_ETIQUETA}>Urgente:</Label>
        <Checkbox id="editar-pedido-urgente" checked={urgente} onCheckedChange={(v) => { setUrgente(v === true); setError(null) }} className="justify-self-start bg-superficie" />
        <Label htmlFor="editar-pedido-precio" className={CLASE_ETIQUETA}>Precio unidad:</Label>
        <div className="flex items-center gap-2">
          <Input id="editar-pedido-precio" value={precio} placeholder="0.00" onChange={(e) => { setPrecio(e.target.value); setError(null) }} className={cn(CLASE_CAMPO, 'flex-1')} />
          <ComboNavy valor={divisa} opciones={DIVISAS_EDICION} onChange={(d) => { setDivisa(d); setError(null) }} textoVacio={divisa} ancho={84} aria-label="Divisa" />
        </div>
        <span className={CLASE_ETIQUETA}>Total EUR:</span>
        <span data-testid="total-eur" className="whitespace-pre text-[13px] font-bold text-azul-medio">{textoTotalEdicion(precio, cantidad, divisa, tasa)}</span>
      </div>
    </DialogoAlmacen>
  )
}
```

`src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.tsx`:

```tsx
import { useLayoutEffect, useMemo, useState } from 'react'
import type { CompraOtro } from '@/shared/api/client'
import { formatearNumero } from '@/shared/lib/importes'
import { cn } from '@/shared/lib/utils'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { useProveedoresComponentes } from '../../proveedores/api'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useEditarOtro } from '../api'
import { useTasa } from '../tasa'
import { CLASE_CAMPO, CLASE_ETIQUETA, DIVISAS_EDICION, MSG_PEDIDO_MODIFICADO, textoTotalEdicion, validarEdicion } from './edicion'
import { mensajeErrorGuardado } from './errores'

type Props = { pedido: CompraOtro | null; onCerrar: () => void }

/** "Editar pedido #{id}" de otros (FormularioOtroPedidoEditarController, inventario §13): Concepto editable, sin
 *  "Urgente" (se conserva el del pedido, calco), mismo título que el de componentes. */
export function EditarOtroPedidoDialog({ pedido, onCerrar }: Props) {
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  const activos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const opciones = useMemo(() => activos.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre })), [activos])
  const [concepto, setConcepto] = useState('')
  const [idProv, setIdProv] = useState<number | null>(null)
  const [cantidad, setCantidad] = useState('')
  const [precio, setPrecio] = useState('')
  const [divisa, setDivisa] = useState('EUR')
  const [error, setError] = useState<string | null>(null)
  const editar = useEditarOtro()
  const tasa = useTasa(pedido ? divisa : null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga al abrir con otro pedido (patrón de EditarStockDialog)
    if (pedido) { setConcepto(pedido.concepto); setIdProv(pedido.idProv); setCantidad(String(pedido.cantidad)); setPrecio(formatearNumero(pedido.precioUnidadPedido)); setDivisa(pedido.divisa); setError(null) }
  }, [pedido])
  const proveedor = idProv !== null && activos.some((p) => p.idProv === idProv) ? idProv : null

  async function guardar() {
    if (!pedido) return
    const v = validarEdicion({ concepto, idProv: proveedor, cantidad, precio })
    if (!v.ok) {
      setError(v.error)
      return
    }
    setError(null)
    try {
      await editar.mutateAsync({
        idCompraOtro: pedido.idCompraOtro,
        cuerpo: { idProv: v.valor.idProv, concepto: v.valor.concepto ?? '', cantidad: v.valor.cantidad, esUrgente: pedido.esUrgente, precioUnidad: v.valor.precioUnidad, divisa, updatedAt: pedido.updatedAt },
      })
      onCerrar()
    } catch (e) {
      setError(mensajeErrorGuardado(e, { staleData: MSG_PEDIDO_MODIFICADO }))
    }
  }

  return (
    <DialogoAlmacen abierto={pedido !== null} ancho={520} titulo={pedido ? `Editar pedido #${pedido.idCompraOtro}` : ''} error={error} textoAccion="Guardar" enviando={editar.isPending} onConfirmar={() => void guardar()} onCancelar={onCerrar}>
      <div className="grid grid-cols-[130px_1fr] items-center gap-x-3.5 gap-y-3">
        <Label htmlFor="editar-otro-concepto" className={CLASE_ETIQUETA}>Concepto:</Label>
        <Input id="editar-otro-concepto" value={concepto} placeholder="Descripción del pedido" onChange={(e) => { setConcepto(e.target.value); setError(null) }} className={CLASE_CAMPO} />
        <span className={CLASE_ETIQUETA}>Proveedor:</span>
        <ComboNavy valor={proveedor === null ? null : String(proveedor)} opciones={opciones} onChange={(v) => { setIdProv(Number(v)); setError(null) }} textoVacio="" ancho={320} visibles={8} aria-label="Proveedor" />
        <Label htmlFor="editar-otro-cantidad" className={CLASE_ETIQUETA}>Cantidad:</Label>
        <Input id="editar-otro-cantidad" value={cantidad} placeholder="Ej. 10" onChange={(e) => { setCantidad(e.target.value); setError(null) }} className={CLASE_CAMPO} />
        <Label htmlFor="editar-otro-precio" className={CLASE_ETIQUETA}>Precio unidad:</Label>
        <div className="flex items-center gap-2">
          <Input id="editar-otro-precio" value={precio} placeholder="0.00" onChange={(e) => { setPrecio(e.target.value); setError(null) }} className={cn(CLASE_CAMPO, 'flex-1')} />
          <ComboNavy valor={divisa} opciones={DIVISAS_EDICION} onChange={(d) => { setDivisa(d); setError(null) }} textoVacio={divisa} ancho={84} aria-label="Divisa" />
        </div>
        <span className={CLASE_ETIQUETA}>Total EUR:</span>
        <span data-testid="total-eur" className="whitespace-pre text-[13px] font-bold text-azul-medio">{textoTotalEdicion(precio, cantidad, divisa, tasa)}</span>
      </div>
    </DialogoAlmacen>
  )
}
```

- [ ] **Step 8: Ejecutar y ver que pasan**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/EditarPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.test.tsx`
Expected: PASS, 16 tests (12 + 4).

- [ ] **Step 9: Test del cableado en `PedidosPage` (falla)**

Antes de escribirlo, abrir `src/modules/almacen/pedidos/PedidosPage.test.tsx` (T13) y copiar a este `beforeEach` cualquier otro handler GET que declare allí (el setup de `src/test/setup.ts:22` usa `onUnhandledRequest: 'error'`); los de abajo son los que la página necesita según el documento de interfaces del reparto (fuera del repo).

`src/modules/almacen/pedidos/PedidosPage.editar.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { COMPRA, OTRO, PROVEEDORES } from './formulario/datosPrueba'
import { PedidosPage } from './PedidosPage'

let puts: string[]

beforeEach(() => {
  puts = []
  server.use(
    http.get('*/api/compras', () => HttpResponse.json([COMPRA])),
    http.get('*/api/compras-otros', () => HttpResponse.json([OTRO])),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    http.put('*/api/compras/:id', ({ params }) => { puts.push(`compras/${String(params.id)}`); return new HttpResponse(null, { status: 200 }) }),
    http.put('*/api/compras-otros/:id', ({ params }) => { puts.push(`compras-otros/${String(params.id)}`); return new HttpResponse(null, { status: 200 }) }),
  )
})

describe('PedidosPage: "Editar" abre el editor de su tabla', () => {
  it('componentes: "Editar" → "Editar pedido #7"; Guardar escribe y lo cierra', async () => {
    renderConProviders(<PedidosPage tipo="componentes" />, { sesion: SESION_SUPER, ruta: '/stock/pedidos' })
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByRole('row', { name: /bat-x/ }) })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar' }))
    expect(await screen.findByRole('dialog', { name: 'Editar pedido #7' })).toBeInTheDocument()
    expect(screen.getByText('Componente:')).toBeInTheDocument()
    // Dentro del diálogo: la celda "Proveedor" de la tabla también dice ACME.
    await within(screen.getByRole('dialog', { name: 'Editar pedido #7' })).findByText('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Editar pedido #7' })).not.toBeInTheDocument())
    expect(puts).toEqual(['compras/7'])
  })

  it('otros: "Editar" → el editor de otros (con "Concepto:"); Cancelar lo cierra sin escribir', async () => {
    renderConProviders(<PedidosPage tipo="otros" />, { sesion: SESION_SUPER, ruta: '/stock/pedidos/otros' })
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByRole('row', { name: /Cinta de embalar/ }) })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar' }))
    expect(await screen.findByRole('dialog', { name: 'Editar pedido #9' })).toBeInTheDocument()
    expect(screen.getByLabelText('Concepto:')).toHaveValue('Cinta de embalar')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(puts).toEqual([])
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.editar.test.tsx`
Expected: FAIL, `Unable to find role="dialog" and name "Editar pedido #7"` (T13 solo guarda `editando`).

- [ ] **Step 10: Cablear los editores en `PedidosPage`**

En `src/modules/almacen/pedidos/PedidosPage.tsx` (T13):

1. Imports (junto a los relativos que ya tiene): añadir `esCompra` al `import { chipDeEstado, entradasMenu, … } from './reglas'` que ya trae T13 (no un import suelto del mismo módulo), y:

```tsx
import { EditarOtroPedidoDialog } from './formulario/EditarOtroPedidoDialog'
import { EditarPedidoDialog } from './formulario/EditarPedidoDialog'
```

2. Quitar el comentario `// T17: editores` de la rama `case 'editar'` del `switch` (el `setEditando(pedido)` y el `return` de T13 se quedan como están).

No hace falta ningún efecto de congelado: T13 ya incluye `editando` en `hayModal`.

3. En el JSX, justo después del último diálogo que pinta T13 (antes del cierre del contenedor raíz):

```tsx
      <EditarPedidoDialog pedido={tipo === 'componentes' && editando !== null && esCompra(editando) ? editando : null} onCerrar={() => setEditando(null)} />
      <EditarOtroPedidoDialog pedido={tipo === 'otros' && editando !== null && !esCompra(editando) ? editando : null} onCerrar={() => setEditando(null)} />
```

(`esCompra` es un type guard `p is CompraComponente`: en la rama verdadera `editando` queda como `CompraComponente`, y con `!esCompra` como `CompraOtro`; el `editando ? editando : null` sin guard de la tarea no compila contra las props tipadas.)

- [ ] **Step 11: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.editar.test.tsx src/modules/almacen/pedidos/PedidosPage.test.tsx`
Expected: PASS; `PedidosPage.editar.test.tsx` con 2 tests y `PedidosPage.test.tsx` (T13) sin cambios.

- [ ] **Step 12: Suite de la carpeta, lint y tipos**

Run: `npx vitest run src/modules/almacen`
Expected: PASS, todos.

Run: `npm run lint && npm run typecheck`
Expected: sin errores ni warnings (los dos `eslint-disable-next-line react-hooks/set-state-in-effect` llevan su motivo, como los de 4a).

- [ ] **Step 13: Commit**

```bash
git add src/modules/almacen/ui/DialogoAlmacen.tsx src/modules/almacen/ui/DialogoAlmacen.test.tsx src/modules/almacen/pedidos/formulario/edicion.ts src/modules/almacen/pedidos/formulario/edicion.test.ts src/modules/almacen/pedidos/formulario/EditarPedidoDialog.tsx src/modules/almacen/pedidos/formulario/EditarPedidoDialog.test.tsx src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.tsx src/modules/almacen/pedidos/formulario/EditarOtroPedidoDialog.test.tsx src/modules/almacen/pedidos/PedidosPage.tsx src/modules/almacen/pedidos/PedidosPage.editar.test.tsx
git commit -m "feat(pedidos): editores de pedido de componentes y de otros con tasa dividida, cantidad pedida y 409 inline"
```

Recuento: **+25 tests** (2 `DialogoAlmacen`, 5 `edicion`, 12 `EditarPedidoDialog`, 4 `EditarOtroPedidoDialog`, 2 `PedidosPage.editar`).

---

---

## Task 18: Web — la campana abre el formulario de pedido

**Files:**
- Modify: `src/modules/taller/notificaciones/api.ts:2` (import de tipos), `:21-22` (se añade `pedirPendientes` debajo)
- Modify: `src/modules/taller/notificaciones/TarjetaAlerta.tsx` (fichero completo)
- Modify: `src/modules/taller/notificaciones/PanelNotificaciones.tsx` (fichero completo: fuera `BotonAlmacen` `:27-36` y el import `:4`)
- Delete: `src/modules/taller/lib/textos.ts` (solo contiene `TOOLTIP_ALMACEN`, `textos.ts:1-3`; ningún otro fichero lo importa: `grep -rn "lib/textos" src` da solo los tres de la campana)
- Test: `src/modules/taller/notificaciones/PanelNotificaciones.test.tsx:7` (import), `:202-217` (test sustituido), `describe` nuevo al final del fichero
- Test: `src/modules/taller/notificaciones/api.test.tsx:13-16` (import), caso nuevo al final del `describe`
- Modify: `docs/paridad/notificaciones.md:27, 30, 47, 55, 56, 71`

**Interfaces:**
- Consume (T8): `abrirNuevoPedido(precarga: PrecargaPedido): void` y `formularioPedido` (store, solo en tests) de `@/shared/lib/formularioPedido`.
- Produce: `export async function pedirPendientes(): Promise<{ urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] }>` en `notificaciones/api.ts`; `TarjetaAlerta({ alerta, alterna, onPedir }: { alerta: AlertaStock; alterna: boolean; onPedir: () => void })`.

Comportamiento (calco de `MainController` de `hotfix/0.16.3`, leído con `git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/.../controllers/MainController.java`):
- "Pedir" de una tarjeta (`:699-704`): `FormularioCompraController.abrir(c, …)` → `abrirNuevoPedido({ modo: 'componentes', idsCom: [idCom] })`.
- "Pedir todas las piezas" (`:275-278`): `if (alertasCriticas.isEmpty()) return;` y después una línea por alerta en el orden de la campana → `abrirNuevoPedido({ modo: 'componentes', idsCom })`; **sin alertas no hace nada** (el el documento de interfaces del reparto (fuera del repo) no lo decía: ver "Desviaciones").
- "Pedir piezas" (`:334-351`): relee las `PENDIENTE` de urgentes y preventivas; con las dos vacías `return` sin aviso; si falla, `mostrarError`; si no, abre con las listas.
- Los tres cierran el panel antes de abrir el formulario (spec §6 "Campana"). El JavaFX **no** cierra la ventana de notificaciones (solo "Ver Stock Completo" hace `ventana.close()`, `:279`); la diferencia va a la ficha de T21.

- [ ] **Step 1: Test de `pedirPendientes` (falla)**

En `api.test.tsx` (`guardarSesion`, `SESION_SUPER`, `solicitudUrgente` y `solicitudPreventiva` ya están importados, `:8-12`), ampliar el import de `./api` (`:13-16`):

```tsx
import {
  CLAVE_NOTIF, CLAVE_NOTIF_COMPONENTES, CLAVE_NOTIF_CONTADOR, CLAVE_NOTIF_SOLICITUDES, pedirPendientes, useCambiarEstadoSolicitud,
  useComponentesGestionados, useContadorNotificaciones, useQuitarSolicitud, useRechazarTodo, useSolicitudesPanel,
} from './api'
```

y, como último caso del `describe('api de notificaciones', …)`:

```tsx
  it('pedirPendientes relee solo las PENDIENTE de urgentes y preventivas, sin caché (calco de "Pedir piezas", MainController :336-337)', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones({ ...TRES_PENDIENTES, urgRech: [solicitudUrgente({ idRc: 503 })], prevRech: [solicitudPreventiva({ idSol: 702 })] }))
    guardarSesion(SESION_SUPER)
    const { urgentes, preventivas } = await pedirPendientes()
    expect(urgentes.map((s) => s.idRc)).toEqual([501, 502])
    expect(preventivas.map((s) => s.idSol)).toEqual([701])
    expect([...gets].sort()).toEqual(['/api/solicitudes-stock?estado=PENDIENTE', '/api/solicitudes?estado=PENDIENTE'])
    // Una segunda llamada vuelve a pedir: no hay caché de por medio.
    await pedirPendientes()
    expect(gets).toHaveLength(4)
  })
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/taller/notificaciones/api.test.tsx`
Expected: FAIL (`pedirPendientes` no está exportado: "does not provide an export named 'pedirPendientes'" o `TypeError: pedirPendientes is not a function`).

- [ ] **Step 3: Implementar `pedirPendientes`**

En `api.ts`, la línea 2 pasa a:

```ts
import { api, type Componente, type SolicitudResumen, type SolicitudStock } from '@/shared/api/client'
```

y justo debajo de `pedirPreventivas` (`:22`):

```ts
/** "Pedir piezas" de la campana (calco de MainController :334-351): relee en ese momento las urgentes y las preventivas
 *  PENDIENTE, sin pasar por la caché del panel, para abrir "Nuevo pedido" con ellas (sub-proyecto 4b, D10). Un fallo se
 *  propaga: lo muestra quien llama. */
export async function pedirPendientes(): Promise<{ urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] }> {
  const [urgentes, preventivas] = await Promise.all([pedirUrgentes('PENDIENTE'), pedirPreventivas('PENDIENTE')])
  return { urgentes, preventivas }
}
```

Run: `npx vitest run src/modules/taller/notificaciones/api.test.tsx`
Expected: PASS (11 tests).

- [ ] **Step 4: Tests del panel (fallan)**

En `PanelNotificaciones.test.tsx`:

1. Sustituir la línea 7 (`import { TOOLTIP_ALMACEN } from '../lib/textos'`) por:

```tsx
import { MSG_SIN_PERMISOS } from '@/shared/api/errors'
import { formularioPedido } from '@/shared/lib/formularioPedido'
```

(ordenados con el resto de imports `@/`: van entre `msw`/`vitest` y `@/test/render`.)

2. Sustituir el test de `:202-217` (`'"Pedir piezas", "Pedir" y "Pedir todas las piezas" deshabilitados con tooltip; "Rechazar todo" habilitado'`) por:

```tsx
  it('"Pedir piezas", "Pedir" y "Pedir todas las piezas" habilitados, sin tooltip y con su estilo; "Rechazar todo" habilitado', async () => {
    await abrirPanel()
    expect(screen.getByRole('button', { name: 'Rechazar todo' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Rechazar todo' })).toHaveClass('bg-notif-rechazar-bg', 'text-notif-rechazar-text', 'text-[13px]', 'font-bold', 'rounded-[20px]', 'p-[11px]')
    const pedirPiezas = screen.getByRole('button', { name: 'Pedir piezas' })
    expect(pedirPiezas).toBeEnabled()
    expect(pedirPiezas).toHaveClass('flex-1', 'cursor-pointer', 'bg-azul-medio', 'text-superficie', 'text-[13px]', 'font-bold', 'rounded-[20px]', 'p-[11px]')
    expect(pedirPiezas.closest('[title]')).toBeNull()
    await userEvent.click(screen.getByRole('tab', { name: 'Alertas' }))
    const pedir = await screen.findAllByRole('button', { name: 'Pedir' })
    expect(pedir).toHaveLength(2)
    for (const boton of pedir) {
      expect(boton).toBeEnabled()
      expect(boton).toHaveClass('cursor-pointer', 'rounded-[20px]', 'bg-azul-medio', 'px-4', 'py-1.5', 'text-[11px]', 'text-superficie')
      expect(boton.closest('[title]')).toBeNull()
    }
    const todas = screen.getByRole('button', { name: 'Pedir todas las piezas' })
    expect(todas).toBeEnabled()
    expect(todas).toHaveClass('flex-1', 'cursor-pointer', 'bg-azul-medio', 'text-superficie', 'text-[13px]', 'font-bold')
    expect(todas.closest('[title]')).toBeNull()
  })
```

3. Añadir al final del fichero (después del `describe('refresco …')`):

```tsx
describe('botones de pedir (sub-proyecto 4b, spec §6 "Campana")', () => {
  it('"Pedir" de una alerta cierra el panel y abre "Nuevo pedido" con ese componente', async () => {
    await abrirPanel(ESCENARIO, 'Alertas')
    expect(formularioPedido.get()).toBeNull()
    await userEvent.click(within(await screen.findByTestId('tarjeta-alerta-101')).getByRole('button', { name: 'Pedir' }))
    await waitFor(() => expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument())
    expect(screen.getByTestId('campana')).toBeInTheDocument()
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'componentes', idsCom: [101] } })
  })
  it('"Pedir todas las piezas" cierra el panel y abre "Nuevo pedido" con una línea por alerta, en el orden de la campana', async () => {
    await abrirPanel(ESCENARIO, 'Alertas')
    await screen.findByTestId('tarjeta-alerta-102')
    await userEvent.click(screen.getByRole('button', { name: 'Pedir todas las piezas' }))
    await waitFor(() => expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument())
    // Sin stock primero (102, stock 0) y después bajo mínimo (101); 112 no está en alerta.
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'componentes', idsCom: [102, 101] } })
  })
  it('"Pedir todas las piezas" sin alertas no hace nada: ni abre el formulario ni cierra el panel (calco de MainController :276)', async () => {
    await abrirPanel({ gestionados: [componente({ stock: 5, stockMinimo: 2 })] }, 'Alertas')
    expect(await screen.findByText('Sin alertas de stock')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Pedir todas las piezas' }))
    expect(formularioPedido.get()).toBeNull()
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
  })
  it('"Pedir piezas" relee las PENDIENTE de las dos listas, cierra el panel y abre "Nuevo pedido" con las solicitudes', async () => {
    const gets = espiarGets()
    const { queryClient } = await abrirPanel()
    await tarjeta('U-501')
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    const antes = { urgentes: cuantas(gets, '/api/solicitudes?estado=PENDIENTE'), preventivas: cuantas(gets, '/api/solicitudes-stock?estado=PENDIENTE') }
    await userEvent.click(screen.getByRole('button', { name: 'Pedir piezas' }))
    await waitFor(() => expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument())
    expect(cuantas(gets, '/api/solicitudes?estado=PENDIENTE')).toBe(antes.urgentes + 1)
    expect(cuantas(gets, '/api/solicitudes-stock?estado=PENDIENTE')).toBe(antes.preventivas + 1)
    expect(formularioPedido.get()).toEqual({
      tipo: 'compra',
      precarga: {
        modo: 'solicitudes',
        urgentes: [expect.objectContaining({ idRc: 501 }), expect.objectContaining({ idRc: 502 })],
        preventivas: [expect.objectContaining({ idSol: 701 })],
      },
    })
  })
  it('"Pedir piezas" con las dos listas PENDIENTE vacías no hace nada: ni abre el formulario ni cierra el panel (calco)', async () => {
    const gets = espiarGets()
    const { queryClient } = await abrirPanel({ urgRech: [solicitudUrgente({ idRc: 503 })] })
    await tarjeta('U-503')
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    const antes = cuantas(gets, '/api/solicitudes-stock?estado=PENDIENTE')
    const boton = screen.getByRole('button', { name: 'Pedir piezas' })
    await userEvent.click(boton)
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes-stock?estado=PENDIENTE')).toBe(antes + 1))
    // Deshabilitado mientras relee: vuelve a habilitarse cuando la relectura ha terminado.
    await waitFor(() => expect(boton).toBeEnabled())
    expect(formularioPedido.get()).toBeNull()
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
  })
  it('"Pedir piezas": si falla la relectura avisa con el mensaje, no abre el formulario y el panel sigue abierto', async () => {
    const { queryClient } = await abrirPanel()
    await tarjeta('U-501')
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    // Después de abrirPanel: MSW antepone cada server.use, así que este 403 gana a los handlers del escenario.
    server.use(http.get('*/api/solicitudes-stock', () => HttpResponse.json({ message: 'no' }, { status: 403 })))
    await userEvent.click(screen.getByRole('button', { name: 'Pedir piezas' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent(MSG_SIN_PERMISOS)
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(formularioPedido.get()).toBeNull()
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
  })
})
```

`formularioPedido` vuelve a `null` entre tests por `reiniciarStores()` de `src/test/setup.ts:26` (T8 lo crea con `crearStore`).

- [ ] **Step 5: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/taller/notificaciones/PanelNotificaciones.test.tsx`
Expected: FAIL (los botones siguen `disabled`; "Pedir piezas" no está habilitado; `formularioPedido.get()` sigue `null`).

- [ ] **Step 6: `TarjetaAlerta` con `onPedir`**

`src/modules/taller/notificaciones/TarjetaAlerta.tsx` completo:

```tsx
import { cn } from '@/shared/lib/utils'
import type { AlertaStock } from './alertas'

/** Tarjeta de una alerta de stock. El mínimo no se muestra; sin forma singular ("1 unid. restantes"); sin menú contextual.
 *  "Pedir" abre "Nuevo pedido" con ese componente (calco de MainController :699-704; sub-proyecto 4b): la acción la
 *  decide el panel, que además se cierra. */
export function TarjetaAlerta({ alerta, alterna, onPedir }: { alerta: AlertaStock; alterna: boolean; onPedir: () => void }) {
  const sinStock = alerta.nivel === 'sinStock'
  return (
    <div data-testid={`tarjeta-alerta-${alerta.componente.idCom}`} className={cn('flex items-center gap-3 rounded-md p-3', alterna ? 'bg-notif-tarjeta-alt' : 'bg-superficie')}>
      <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[14px] font-bold text-superficie', sinStock ? 'bg-notif-sin-stock' : 'bg-notif-stock-bajo')}>
        {sinStock ? '✕' : '!'}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[14px] font-bold text-azul-medio">{alerta.componente.tipo}</p>
        <p className="flex items-center gap-1.5 text-[11px]">
          <span className={cn('font-bold', sinStock ? 'text-notif-sin-stock' : 'text-notif-stock-bajo')}>{sinStock ? 'Sin Stock' : 'Stock Bajo'}</span>
          <span className="text-texto-fecha-inicio">{sinStock ? 'Sin unidades' : `${alerta.componente.stock} unid. restantes`}</span>
        </p>
      </div>
      <button type="button" onClick={onPedir} className="cursor-pointer rounded-[20px] bg-azul-medio px-4 py-1.5 text-[11px] text-superficie">
        Pedir
      </button>
    </div>
  )
}
```

- [ ] **Step 7: `PanelNotificaciones` con los tres botones activos**

`src/modules/taller/notificaciones/PanelNotificaciones.tsx` completo:

```tsx
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react'
import { useNavigate } from 'react-router'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { abrirNuevoPedido } from '@/shared/lib/formularioPedido'
import { cn } from '@/shared/lib/utils'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import type { AlertaStock } from './alertas'
import { pedirPendientes, useCambiarEstadoSolicitud, useQuitarSolicitud, useRechazarTodo, useSolicitudesPanel } from './api'
import { firma, type ListasSolicitudes, type TarjetaDatos } from './solicitudes'
import { TarjetaAlerta } from './TarjetaAlerta'
import { TarjetaSolicitud } from './TarjetaSolicitud'

type Pestana = 'solicitudes' | 'alertas'
// `alertas` llega ya calculada por la campana: es la misma suscripción a `/api/componentes/gestionados` que sondea el
// pulso; así abrir el panel no añade una segunda petición para la misma clave de consulta.
type Props = { pestanaInicial: Pestana; anclaRef: RefObject<HTMLElement | null>; onCerrar: () => void; alertas: AlertaStock[] }

const ANCHO = 480
const SEPARACION = 6
const VACIAS: ListasSolicitudes = { pendientes: [], rechazadas: [] }
/** Capas que viven en un portal fuera del panel: un clic o un Escape dentro de ellas no es "fuera del panel". */
const CAPAS = '[data-slot="context-menu-content"], [role="dialog"], [data-slot="dialog-overlay"]'
const PESTANAS: { clave: Pestana; texto: string }[] = [
  { clave: 'solicitudes', texto: 'Solicitudes' },
  { clave: 'alertas', texto: 'Alertas' },
]
const BOTON_INFERIOR = 'w-full rounded-[20px] p-[11px] text-[13px] font-bold'

/** Panel flotante de la campana: 480 px, sin cabecera ni botón de cerrar, no modal (sin overlay ni trampa de foco). Borde
 *  derecho alineado con el de la campana y 6 px por debajo; se recoloca al cambiar el tamaño de la ventana. Se cierra al
 *  pulsar fuera del panel y de la campana, y con Escape. */
export function PanelNotificaciones({ pestanaInicial, anclaRef, onCerrar, alertas }: Props) {
  const navigate = useNavigate()
  const { mostrarError } = useAlerta()
  const panelRef = useRef<HTMLDivElement>(null)
  const [pestana, setPestana] = useState<Pestana>(pestanaInicial)
  // Copia que pintan las tarjetas: solo se sustituye cuando cambia el conjunto de identificadores con su grupo y clase.
  const [pintadas, setPintadas] = useState<ListasSolicitudes>(VACIAS)
  // "Pedir piezas" relee las pendientes antes de abrir el formulario: mientras tanto el botón no admite otro clic (el
  // JavaFX lo hace en el hilo de la interfaz, donde un segundo clic no es posible).
  const [pidiendo, setPidiendo] = useState(false)
  const solicitudes = useSolicitudesPanel(true)
  const cambiarEstado = useCambiarEstadoSolicitud()
  const quitar = useQuitarSolicitud()
  const rechazarTodo = useRechazarTodo()

  // Ajuste de estado durante el render: un sondeo que solo trae otra descripción, técnico o fecha no repinta.
  if (solicitudes.data && firma(solicitudes.data) !== firma(pintadas)) setPintadas(solicitudes.data)
  // Las alertas llegan por prop (misma suscripción que el pulso de la campana) y se repintan siempre.

  useLayoutEffect(() => {
    function colocar() {
      const ancla = anclaRef.current
      const panel = panelRef.current
      if (!ancla || !panel) return
      const r = ancla.getBoundingClientRect()
      panel.style.top = `${r.bottom + SEPARACION}px`
      panel.style.left = `${r.right - ANCHO}px`
    }
    colocar()
    window.addEventListener('resize', colocar)
    return () => window.removeEventListener('resize', colocar)
  }, [anclaRef])

  useEffect(() => {
    function alPulsar(e: PointerEvent) {
      const destino = e.target
      if (!(destino instanceof Element)) return
      if (panelRef.current?.contains(destino) || anclaRef.current?.contains(destino)) return
      if (destino.closest(CAPAS)) return
      onCerrar()
    }
    function alTeclear(e: KeyboardEvent) {
      if (e.key !== 'Escape') return
      // Con un menú contextual o un aviso encima, Escape es suyo.
      if (document.querySelector(CAPAS)) return
      onCerrar()
    }
    document.addEventListener('pointerdown', alPulsar)
    document.addEventListener('keydown', alTeclear)
    return () => {
      document.removeEventListener('pointerdown', alPulsar)
      document.removeEventListener('keydown', alTeclear)
    }
  }, [anclaRef, onCerrar])

  /** Calco de mostrarStockEnPedidos / mostrarStockEnActual (MainController): cierra el panel y abre la pestaña de Stock
   *  correspondiente, sin aplicar filtros (sub-proyecto 4a). */
  function irA(ruta: '/stock' | '/stock/pedidos') {
    onCerrar()
    navigate(ruta)
  }

  /** "Pedir" de una alerta y "Pedir todas las piezas" (calco de MainController :699-704 y :275-278): "Nuevo pedido" con una
   *  línea por componente, cantidad 1, en el orden de la campana. El formulario es un modal sobre la vista actual (P1)
   *  y el panel se cierra antes de abrirlo (spec §6 "Campana"). */
  function pedirComponentes(idsCom: number[]) {
    onCerrar()
    abrirNuevoPedido({ modo: 'componentes', idsCom })
  }

  /** "Pedir todas las piezas" sin alertas no hace nada (calco de MainController :276). */
  function pedirTodas() {
    if (alertas.length === 0) return
    pedirComponentes(alertas.map((a) => a.componente.idCom))
  }

  /** "Pedir piezas" (calco de MainController :334-351): relee las PENDIENTE; con las dos listas vacías no hace nada, sin
   *  aviso; si no, "Nuevo pedido" con las solicitudes, que el lote marcará GESTIONADA al guardar (D10). Un fallo de la
   *  relectura se avisa (mostrarError del JavaFX) y el panel sigue abierto. */
  async function pedirPiezas() {
    setPidiendo(true)
    try {
      const { urgentes, preventivas } = await pedirPendientes()
      if (urgentes.length === 0 && preventivas.length === 0) return
      onCerrar()
      abrirNuevoPedido({ modo: 'solicitudes', urgentes, preventivas })
    } catch (e) {
      if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e))
    } finally {
      setPidiendo(false)
    }
  }

  const tarjeta = (t: TarjetaDatos, i: number) => (
    <TarjetaSolicitud
      key={`${t.clase}-${t.id}`}
      datos={t}
      alterna={i % 2 === 1}
      onRechazar={() => cambiarEstado.mutate({ clase: t.clase, id: t.id, estado: 'RECHAZADA' })}
      onRecuperar={() => cambiarEstado.mutate({ clase: t.clase, id: t.id, estado: 'PENDIENTE' })}
      onQuitar={() => quitar.mutate({ clase: t.clase, id: t.id })}
    />
  )

  return (
    <div ref={panelRef} data-testid="panel-notificaciones" className="fixed z-40 flex w-[480px] flex-col gap-3 border border-notif-panel-brd bg-fondo-vista p-5">
      <div className="flex items-center">
        <div role="tablist" className="flex rounded-[20px] border border-notif-segmento-brd bg-superficie p-[3px]">
          {PESTANAS.map((p) => (
            <button
              key={p.clave}
              type="button"
              role="tab"
              aria-selected={pestana === p.clave}
              onClick={() => setPestana(p.clave)}
              className={cn('cursor-pointer px-[18px] py-[7px] text-[12px]', pestana === p.clave ? 'rounded-[17px] bg-azul-medio font-bold text-superficie' : 'text-azul-gris')}
            >
              {p.texto}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <button type="button" onClick={() => irA('/stock/pedidos')} className="cursor-pointer text-[12px] font-bold text-azul-noche hover:underline">→ Ir a pedidos</button>
      </div>

      {pestana === 'solicitudes' ? (
        <>
          <div role="tabpanel" aria-label="Solicitudes" className="h-[370px] overflow-x-hidden overflow-y-auto bg-fondo-vista">
            <h2 className="mb-1.5 text-[14px] font-bold text-azul-medio">Solicitudes de pieza</h2>
            <div className="flex flex-col gap-1.5">{pintadas.pendientes.map(tarjeta)}</div>
            <h3 className="mt-3 mb-1.5 text-[12px] font-bold text-texto-fecha-inicio">Rechazadas</h3>
            <div className="flex flex-col gap-1.5">{pintadas.rechazadas.map(tarjeta)}</div>
          </div>
          <div className="flex gap-2">
            <button type="button" disabled={pidiendo} onClick={() => void pedirPiezas()} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-azul-medio text-superficie')}>
              Pedir piezas
            </button>
            <button type="button" onClick={() => rechazarTodo.mutate()} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-notif-rechazar-bg text-notif-rechazar-text')}>
              Rechazar todo
            </button>
          </div>
        </>
      ) : (
        <>
          <div role="tabpanel" aria-label="Alertas" className="h-[320px] overflow-x-hidden overflow-y-auto bg-fondo-vista">
            <h2 className="mb-1.5 text-[16px] font-bold text-azul-medio">Alertas de Stock</h2>
            {alertas.length === 0 ? (
              <p className="text-[13px] text-texto-fecha-inicio">Sin alertas de stock</p>
            ) : (
              <div className="flex flex-col gap-1.5">
                {alertas.map((a, i) => (
                  <TarjetaAlerta key={a.componente.idCom} alerta={a} alterna={i % 2 === 1} onPedir={() => pedirComponentes([a.componente.idCom])} />
                ))}
              </div>
            )}
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={pedirTodas} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-azul-medio text-superficie')}>Pedir todas las piezas</button>
            <button type="button" onClick={() => irA('/stock')} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-azul-medio text-superficie')}>Ver Stock Completo</button>
          </div>
        </>
      )}
    </div>
  )
}
```

`useAlerta` exige `AlertaProvider`: la campana vive en el `AppLayout`, dentro de los providers de `main.tsx`, y todos los tests del panel y de la campana montan con `renderConProviders`/`renderConRouter` (`src/test/render.tsx`), que lo incluyen. `Campana.test.tsx` no cambia.

- [ ] **Step 8: Borrar `textos.ts`**

```bash
git rm src/modules/taller/lib/textos.ts
grep -rn "TOOLTIP_ALMACEN\|lib/textos\|BotonAlmacen" src
```

Expected: el `grep` no devuelve nada.

- [ ] **Step 9: Ejecutar**

Run: `npx vitest run src/modules/taller/notificaciones && npm run lint`
Expected: PASS (los ficheros de `notificaciones/`, con los 6 casos nuevos del panel y el de `api.test.tsx`); lint sin errores ni warnings nuevos.

- [ ] **Step 10: Ficha `notificaciones.md`**

En `docs/paridad/notificaciones.md` (quedó sin actualizar en el 4a para "→ Ir a pedidos" y "Ver Stock Completo"):

- `:27` → `- [x] También se cierra al pulsar en cualquier punto de la página fuera del panel y de la campana, y con "→ Ir a pedidos", "Ver Stock Completo", "Pedir", "Pedir todas las piezas" y "Pedir piezas" (este último solo si hay pendientes). No se cierra por perder el foco la ventana.`
- `:30` → `- [x] A la derecha de esa fila, enlace "→ Ir a pedidos" (12 px negrita, #001232, cursor de mano, sin subrayado), visible en las dos pestañas: cierra el panel y abre Stock en la pestaña Pedidos, sin filtros (sub-proyecto 4a).`
- `:47` → `- [x] "Pedir piezas": relee las solicitudes PENDIENTE urgentes y preventivas; si no hay ninguna no hace nada (sin aviso); si las hay, cierra el panel y abre "Nuevo pedido" con una línea por componente y cantidad = número de solicitudes; al confirmar el pedido, las solicitudes de los componentes con línea pasan a GESTIONADA en el mismo guardado (sub-proyecto 4b, D10). Si falla la relectura, aviso con el mensaje y el panel sigue abierto.`
- `:55` → `- [x] Botón "Pedir" de cada tarjeta (fondo #2C3B54, texto blanco, 11 px, radio 20, padding 6 16): cierra el panel y abre "Nuevo pedido" con ese componente (sub-proyecto 4b).`
- `:56` → `- [x] Botones inferiores, mitad y mitad, los dos con el formato oscuro de "Pedir piezas": "Pedir todas las piezas" (cierra el panel y abre "Nuevo pedido" con una línea por alerta, cantidad 1, en el orden de la campana; sin alertas no hace nada) y "Ver Stock Completo" (cierra el panel y abre Stock en "Stock actual").`
- `:71` → `- Los botones de pedir cierran el panel antes de abrir el formulario; en el JavaFX la ventana de notificaciones queda abierta detrás del formulario modal (sub-proyecto 4b).`

- [ ] **Step 11: Commit**

```bash
git add src/modules/taller/notificaciones/api.ts src/modules/taller/notificaciones/api.test.tsx src/modules/taller/notificaciones/TarjetaAlerta.tsx src/modules/taller/notificaciones/PanelNotificaciones.tsx src/modules/taller/notificaciones/PanelNotificaciones.test.tsx src/modules/taller/lib/textos.ts docs/paridad/notificaciones.md
git commit -m "feat(campana): pedir, pedir todas las piezas y pedir piezas abren el formulario de pedido"
git cat-file -p HEAD | tail -1
```

Expected: la última línea es el mensaje, sin `Co-Authored-By`.

Recuento: **+7 tests** (6 en `PanelNotificaciones.test.tsx`, 1 en `api.test.tsx`; el test de los botones deshabilitados se sustituye, no suma).

---

## Task 19: Web — Stock actual: "Pedir" abre el formulario en el sitio y vuelta desde Pedidos

**Files:**
- Modify: `src/modules/almacen/stock/filtros.ts` (final del fichero: `filtrosDesdePedidos`; el import de `EstadoStock` ya está en la l.2)
- Test: `src/modules/almacen/stock/filtros.test.ts:3` (import) y `describe` nuevo al final
- Modify: `src/modules/almacen/stock/StockPage.tsx` (fichero completo; cambian `:1-2`, `:7`, `:24`, `:42-47`, `:150` y el `DataTable`)
- Test: `src/modules/almacen/stock/StockPage.test.tsx:1-13` (imports), `:161-175` (test sustituido), casos nuevos al final del `describe`
- Modify: `docs/paridad/stock.md:63, 122, 194`

**Interfaces:**
- Consume (T8): `abrirNuevoPedido`, `cerrarFormularioPedido` (tests) y `formularioPedido` de `@/shared/lib/formularioPedido`; `useStore` de `@/shared/lib/store`. `pedirDesplazamiento` de `DataTable` (`src/shared/ui/DataTable.tsx:52` y `:245-282`: **es un contador**, no un índice; cada valor nuevo es una petición que se atiende una sola vez, esté o no la fila, y DataTable busca por sí mismo el índice de `seleccionada` en sus filas filtradas).
- Produce: `export function filtrosDesdePedidos(f: FiltrosStock): FiltrosStock` en `stock/filtros.ts`. Los literales reales de `EstadoStock` son `'OK' | 'Bajo' | 'Sin stock' | 'Desactivado'` (`src/shared/lib/semaforoStock.ts:4`), no los de el documento de interfaces del reparto (fuera del repo).

Comportamiento (calco de `navegarAComponente`, `StockController :233-246`, inventario §2 y spec §6 "Vuelta a Stock"): desmarca OK, Bajo y Sin stock (conserva Desactivado, esté o no marcado), vacía el buscador, abre Stock actual y selecciona la fila del componente con scroll **si la encuentra**. La web consume `?componente=<id>` cuando ya hay datos (`isSuccess`): si la petición de desplazamiento llegara antes que las filas, DataTable la daría por atendida sin desplazar (`DataTable.tsx:256-258`).

- [ ] **Step 1: Test de `filtrosDesdePedidos` (falla)**

En `filtros.test.ts`, la línea 3 pasa a:

```ts
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, filtrosDesdePedidos, nombreComponente, ordenarStock, textoDesactivados } from './filtros'
```

añadir `import type { EstadoStock } from '@/shared/lib/semaforoStock'` tras el import de `Componente`, y al final del fichero:

```ts
describe('filtrosDesdePedidos', () => {
  it('desmarca OK, Bajo y Sin stock, conserva Desactivado y vacía el buscador (calco de navegarAComponente :233-246)', () => {
    const f = filtrosDesdePedidos({ estados: new Set<EstadoStock>(['OK', 'Bajo', 'Sin stock', 'Desactivado']), buscador: 'lcd' })
    expect([...f.estados]).toEqual(['Desactivado'])
    expect(f.buscador).toBe('')
  })
  it('sin Desactivado marcado deja los estados vacíos (todos) y no modifica los filtros de entrada', () => {
    const antes = { estados: new Set<EstadoStock>(['Bajo']), buscador: 'bat' }
    const f = filtrosDesdePedidos(antes)
    expect(f.estados.size).toBe(0)
    expect(f.buscador).toBe('')
    expect([...antes.estados]).toEqual(['Bajo'])
    expect(antes.buscador).toBe('bat')
  })
})
```

Run: `npx vitest run src/modules/almacen/stock/filtros.test.ts`
Expected: FAIL (`filtrosDesdePedidos` no existe).

- [ ] **Step 2: Implementar `filtrosDesdePedidos`**

Al final de `src/modules/almacen/stock/filtros.ts`:

```ts
/** Llegada a Stock actual desde el enlace Componente de Pedidos (calco de navegarAComponente, StockController :233-246):
 *  desmarca OK, Bajo y Sin stock, conserva "Desactivado" tal como estuviera y vacía el buscador. Devuelve filtros nuevos
 *  (el store no se muta en sitio). */
export function filtrosDesdePedidos(f: FiltrosStock): FiltrosStock {
  return { estados: new Set([...f.estados].filter((e) => e === 'Desactivado')), buscador: '' }
}
```

Run: `npx vitest run src/modules/almacen/stock/filtros.test.ts`
Expected: PASS.

- [ ] **Step 3: Tests de `StockPage` (fallan)**

En `StockPage.test.tsx`, añadir a los imports (`:1-13`):

```tsx
import { cerrarFormularioPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import type { EstadoStock } from '@/shared/lib/semaforoStock'
import { filtrosStock, seleccionStock } from './estado'
```

(`@/shared/lib/…` junto a `@/shared/lib/csv`; `./estado` tras `../estado`.)

Sustituir el test `'"Pedir" del menú navega a Pedidos filtrado por el componente'` (`:161-175`) por:

```tsx
  it('"Pedir" del menú abre "Nuevo pedido" en el sitio con ese componente, sin navegar (4b, P1)', async () => {
    const { router } = renderConRouter(
      [
        { path: '/stock', element: <StockPage /> },
        { path: '/stock/pedidos', element: <p data-testid="pedidos">Pedidos</p> },
      ],
      { sesion: SESION_SUPER, ruta: '/stock' },
    )
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Pedir' }))
    expect(formularioPedido.get()).toEqual({ tipo: 'compra', precarga: { modo: 'componentes', idsCom: [1] } })
    expect(router.state.location.pathname).toBe('/stock')
    expect(router.state.location.search).toBe('')
    expect(screen.queryByTestId('pedidos')).not.toBeInTheDocument()
  })
```

y, al final del `describe('StockPage', …)`:

```tsx
  it('?componente=<id> al llegar desde Pedidos: desmarca OK, Bajo y Sin stock, vacía el buscador, selecciona y desplaza hasta la fila y limpia la URL', async () => {
    filtrosStock.set({ estados: new Set<EstadoStock>(['OK', 'Bajo']), buscador: 'lcd' })
    const { router } = renderConRouter([{ path: '/stock', element: <StockPage /> }], { sesion: SESION_SUPER, ruta: '/stock?componente=2' })
    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(router.state.location.pathname).toBe('/stock')
    expect(filtrosStock.get().estados.size).toBe(0)
    expect(screen.getByPlaceholderText('Buscar componente…')).toHaveValue('')
    expect(seleccionStock.get()).toBe('2')
    await waitFor(() => expect(filaDe('bat-x')).toHaveAttribute('aria-selected', 'true'))
    // La petición de desplazamiento encontró la fila: DataTable desplaza y enfoca su contenedor (DataTable.test.tsx,
    // "cada petición de desplazamiento…"), calco de select(i) + scrollTo(i) de navegarAComponente.
    await waitFor(() => expect(screen.getByRole('table').parentElement).toHaveFocus())
    // La selección alimenta el gráfico por SKU como un clic.
    expect(await screen.findByRole('img', { name: 'Stock 2, Pedido 4' })).toBeInTheDocument()
  })
  it('?componente= conserva "Desactivado" marcado (calco) y selecciona una fila desactivada', async () => {
    filtrosStock.set({ estados: new Set<EstadoStock>(['Bajo', 'Desactivado']), buscador: 'zzz' })
    const { router } = renderConRouter([{ path: '/stock', element: <StockPage /> }], { sesion: SESION_SUPER, ruta: '/stock?componente=4' })
    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect([...filtrosStock.get().estados]).toEqual(['Desactivado'])
    expect(filtrosStock.get().buscador).toBe('')
    await waitFor(() => expect(filaDe('mc-x')).toHaveAttribute('aria-selected', 'true'))
    expect(screen.queryByText('bat-x')).not.toBeInTheDocument()
  })
  it('?componente= de un componente que no está en la lista aplica los filtros y limpia la URL sin tocar la selección; un id no numérico solo limpia la URL', async () => {
    seleccionStock.set('1')
    filtrosStock.set({ estados: new Set<EstadoStock>(['OK']), buscador: 'lcd' })
    const primero = renderConRouter([{ path: '/stock', element: <StockPage /> }], { sesion: SESION_SUPER, ruta: '/stock?componente=99' })
    await waitFor(() => expect(primero.router.state.location.search).toBe(''))
    expect(filtrosStock.get().estados.size).toBe(0)
    expect(filtrosStock.get().buscador).toBe('')
    expect(seleccionStock.get()).toBe('1')
    primero.unmount()

    filtrosStock.set({ estados: new Set<EstadoStock>(['OK']), buscador: 'lcd' })
    const segundo = renderConRouter([{ path: '/stock', element: <StockPage /> }], { sesion: SESION_SUPER, ruta: '/stock?componente=abc' })
    await waitFor(() => expect(segundo.router.state.location.search).toBe(''))
    expect([...filtrosStock.get().estados]).toEqual(['OK'])
    expect(filtrosStock.get().buscador).toBe('lcd')
  })
  it('con el formulario de pedido abierto el sondeo se congela; al cerrarlo se reanuda', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Pedir' }))
    expect(formularioPedido.get()).not.toBeNull()
    // El menú ya se cerró: lo único abierto es el formulario (que en la app pinta el host del shell).
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS * 2) })
    expect(cargas.n).toBe(1)
    act(() => cerrarFormularioPedido())
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS) })
    await waitFor(() => expect(cargas.n).toBeGreaterThan(1))
  })
```

- [ ] **Step 4: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/almacen/stock/StockPage.test.tsx`
Expected: FAIL ("Pedir" navega a `/stock/pedidos?componente=1`; `?componente=` no se consume; el sondeo sigue con el formulario abierto).

- [ ] **Step 5: Implementar en `StockPage`**

`src/modules/almacen/stock/StockPage.tsx` completo:

```tsx
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import type { Componente } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { abrirNuevoPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { ESTADOS_STOCK, type EstadoStock } from '@/shared/lib/semaforoStock'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin, esAdminOSuperTecnico, esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonSecundario } from '@/shared/ui/Botones'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { Input } from '@/shared/ui/input'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { ultimaRutaStock } from '../estado'
import { AjustarMinimoDialog } from './AjustarMinimoDialog'
import { pedirCantidadEnCamino, useAjustarMinimo, useComponentesStock, useEditarStock, useSetActivoComponente, useSolicitarPieza } from './api'
import { CABECERAS_CSV_STOCK, claseFilaStock, crearColumnasStock, filaCsvStock, parametrosPedidos } from './columnas'
import { EditarStockDialog } from './EditarStockDialog'
import { filtrosStock, seleccionStock } from './estado'
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, filtrosDesdePedidos, textoDesactivados } from './filtros'
import { GraficoEstado } from './GraficoEstado'
import { GraficoSku } from './GraficoSku'
import { conteosDonut } from './graficos'
import { MenuComponente } from './MenuComponente'
import { SolicitarPiezaDialog } from './SolicitarPiezaDialog'

const MSG_MODIFICADO = 'El componente fue modificado mientras editabas. Recarga los datos.'

type Dialogo = { tipo: 'stock' | 'minimo' | 'solicitar'; c: Componente } | null
type Grafico = { componente: Componente; enCamino: number }

/** Pestaña "Stock actual" de StockView.fxml (spec 4a §6): una sola consulta para tabla, filtros, donut y pie. */
export function StockPage() {
  const { sesion } = useSession()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [params] = useSearchParams()
  const { mostrarError } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [filtros, setFiltros] = useStore(filtrosStock)
  const [seleccionada, setSeleccionada] = useStore(seleccionStock)
  // El formulario de pedido (P1 de 4b) es un modal del shell: mientras esté abierto, el sondeo se congela como con un
  // diálogo propio (spec 4b §7).
  const [formulario] = useStore(formularioPedido)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  // Texto de un 422 del servidor para el diálogo abierto (spec §8): se pinta dentro del diálogo, que sigue abierto.
  const [errorServidor, setErrorServidor] = useState<string | null>(null)
  // Cada llegada desde Pedidos pide a la tabla desplazarse hasta la fila seleccionada (contador, DataTable.tsx:52).
  const [peticionDesplazamiento, setPeticionDesplazamiento] = useState(0)
  const { data = [], dataUpdatedAt, refetch, isSuccess } = useComponentesStock({ activo: !hayAlguna && formulario === null })
  const editarStock = useEditarStock()
  const ajustarMinimo = useAjustarMinimo()
  const setActivo = useSetActivoComponente()
  const solicitar = useSolicitarPieza()

  // Última pestaña de Stock para el botón de la barra superior (caché de vista del JavaFX, S2).
  useEffect(() => { ultimaRutaStock.set('/stock') }, [])
  // El diálogo cuenta como interacción abierta: el sondeo se congela mientras esté abierto (D4 del 3a).
  useEffect(() => {
    if (!dialogo) return
    marcar(true)
    return () => marcar(false)
  }, [dialogo, marcar])

  // Vuelta desde Pedidos (calco de navegarAComponente, StockController :233-246; spec 4b §6 "Vuelta a Stock"): con los
  // datos ya cargados, desmarca OK, Bajo y Sin stock (conserva Desactivado), vacía el buscador y, si el componente está
  // en la lista, lo selecciona y pide a la tabla que se desplace hasta él. Después limpia la URL con replace. Un id que
  // no es un entero positivo solo limpia la URL.
  const componenteUrl = params.get('componente')
  useEffect(() => {
    if (componenteUrl === null || !isSuccess) return
    const id = Number(componenteUrl)
    if (Number.isInteger(id) && id > 0) {
      setFiltros(filtrosDesdePedidos)
      if (data.some((c) => c.idCom === id)) {
        setSeleccionada(String(id))
        // eslint-disable-next-line react-hooks/set-state-in-effect -- la llegada desde Pedidos se consume una sola vez, cuando ya hay datos; la petición de desplazamiento es la única forma de pedirle el scroll a DataTable
        setPeticionDesplazamiento((n) => n + 1)
      }
    }
    void navigate(pathname, { replace: true })
  }, [componenteUrl, isSuccess, data, navigate, pathname, setFiltros, setSeleccionada])

  const visibles = useMemo(() => aplicarFiltrosStock(data, filtros), [data, filtros])
  const conteos = useMemo(() => conteosDonut(data), [data])
  const nDesactivados = useMemo(() => data.filter((c) => !c.activo).length, [data])
  const seleccionado = useMemo(() => data.find((c) => String(c.idCom) === seleccionada) ?? null, [data, seleccionada])

  // Gráfico por SKU (spec §8): la barra "Pedido" solo la piden ADMIN y SUPERTECNICO (:547). El gráfico solo cambia cuando
  // llega la cantidad en camino; si la petición falla se avisa y el gráfico conserva el anterior, título incluido.
  // `seleccionado` mantiene la referencia entre sondeos si no cambia (structural sharing de TanStack Query): el refresco
  // no vuelve a pedir la cantidad.
  const veEnCamino = esAdminOSuperTecnico(sesion)
  const [grafico, setGrafico] = useState<Grafico | null>(null)
  useEffect(() => {
    if (!veEnCamino || !seleccionado) return
    // Guard de carrera: si cambia la selección antes de que llegue la respuesta, la de la fila anterior se descarta.
    let vigente = true
    pedirCantidadEnCamino(seleccionado.idCom).then(
      (enCamino) => { if (vigente) setGrafico({ componente: seleccionado, enCamino }) },
      (e: unknown) => { if (vigente && !esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e)) },
    )
    return () => { vigente = false }
  }, [seleccionado, veEnCamino, mostrarError])
  // Sin selección, "Selecciona un componente"; el TECNICO no pide la cantidad y su barra "Pedido" va a 0 al momento.
  const graficoSku: Grafico | null = !seleccionado ? null : veEnCamino ? grafico : { componente: seleccionado, enCamino: 0 }

  const irAPedidos = useCallback((c: Componente) => navigate(`/stock/pedidos?${parametrosPedidos(c)}`), [navigate])
  const columnas = useMemo(() => crearColumnasStock({ onEnCamino: irAPedidos }), [irAPedidos])

  useRegistrarExportable(() => descargarCsv('stock_actual', CABECERAS_CSV_STOCK, visibles.map(filaCsvStock)))

  function cerrarDialogo() {
    setDialogo(null)
    setErrorServidor(null)
  }

  /** Spec §8: un 422 del servidor se pinta dentro del diálogo, que sigue abierto. Devuelve true si lo ha gestionado. */
  function errorEnDialogo(e: unknown): boolean {
    if (!(e instanceof ReglaNegocioError)) return false
    setErrorServidor(e.message)
    return true
  }

  /** Editar stock: el 409 es el aviso de modificado (calco de :661-665) y cierra el diálogo; cualquier otro error
   *  (403/404/5xx/red) deja el diálogo abierto y pasa por el mapeo común, igual que "Ajustar mínimo".
   *  Las dos mutaciones con diálogo silencian el diálogo global (meta.silenciarError), así que el aviso sale una vez. */
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
        {/* Calco del MenuButton "Estado" con CustomMenuItem(hideOnClick=false) con el MultiSelect compartido (spec §5,
            decisión 9): marcar no cierra el desplegable y abrirlo congela el refresco (onOpenChange → marcar). */}
        <MultiSelect
          opciones={estadosMenu}
          clave={(e) => e}
          etiqueta={(e) => e}
          seleccion={filtros.estados}
          onChange={(estados) => setFiltros({ ...filtros, estados: estados as Set<EstadoStock> })}
          textoVacio="Estado"
          textoPlural={(n) => `${n} estados`}
          onOpenChange={marcar}
          className="min-w-[130px]"
        />
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
            pedirDesplazamiento={peticionDesplazamiento}
            filaClase={claseFilaStock}
            altoFila={35}
            menuFila={rol ? (c) => (
              <MenuComponente
                c={c}
                rol={rol}
                // "Pedir" abre "Nuevo pedido" como modal encima de Stock actual (calco del Stage modal, spec 4b P1): el
                // host del shell lo pinta; la línea va vacía si el componente está desactivado (calco, T14).
                onPedir={(x) => abrirNuevoPedido({ modo: 'componentes', idsCom: [x.idCom] })}
                onEditarStock={(x) => setDialogo({ tipo: 'stock', c: x })}
                onAjustarMinimo={(x) => setDialogo({ tipo: 'minimo', c: x })}
                onToggleActivo={(x) => setActivo.mutate({ idCom: x.idCom, activo: !x.activo })}
                onSolicitar={(x) => setDialogo({ tipo: 'solicitar', c: x })}
                onInteraccion={marcar}
              />
            ) : undefined}
          />
          {/* Pie en una línea (FXML :56-64): "N desactivados" a la izquierda y "Actualizado" a la derecha, sin estirar el botón. */}
          <div className="mt-1 flex items-center justify-between gap-4">
            <span className="text-[10px] whitespace-nowrap text-texto-vacio">{pieDesactivados ?? ''}</span>
            <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} className="mt-0 w-auto shrink-0 whitespace-nowrap" />
          </div>
        </div>
        {/* Tarjeta de 240 px fijos (FXML :66-106): donut arriba, separador, gráfico por SKU abajo. */}
        <aside className="flex w-[240px] shrink-0 flex-col gap-3 rounded-md border border-fila-sep bg-superficie p-4">
          <GraficoEstado conteos={conteos} />
          <hr className="border-fila-sep" />
          <GraficoSku componente={graficoSku?.componente ?? null} enCamino={graficoSku?.enCamino ?? 0} />
        </aside>
      </div>

      <EditarStockDialog
        componente={dialogo?.tipo === 'stock' ? dialogo.c : null}
        enviando={editarStock.isPending}
        errorServidor={errorServidor}
        onCancelar={cerrarDialogo}
        onConfirmar={(stock) => {
          if (dialogo?.tipo !== 'stock') return
          const c = dialogo.c
          setErrorServidor(null)
          editarStock.mutate({ c, stock }, {
            onSuccess: cerrarDialogo,
            // 422 → inline con el diálogo abierto; 409 → se cierra y avisa (recarga por el onSettled del hook);
            // cualquier otro error (403/404/5xx/red) deja el diálogo abierto y avisa igual que "Ajustar mínimo".
            onError: (e) => {
              if (errorEnDialogo(e)) return
              if (e instanceof StaleDataError) cerrarDialogo()
              alFallarEdicion(e)
            },
          })
        }}
      />
      <AjustarMinimoDialog
        componente={dialogo?.tipo === 'minimo' ? dialogo.c : null}
        enviando={ajustarMinimo.isPending}
        errorServidor={errorServidor}
        onCancelar={cerrarDialogo}
        onConfirmar={(stockMinimo) => {
          if (dialogo?.tipo !== 'minimo') return
          setErrorServidor(null)
          ajustarMinimo.mutate({ idCom: dialogo.c.idCom, stockMinimo }, {
            onSuccess: cerrarDialogo,
            onError: (e) => { if (errorEnDialogo(e)) return; if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e)) },
          })
        }}
      />
      <SolicitarPiezaDialog
        componente={dialogo?.tipo === 'solicitar' ? dialogo.c : null}
        enviando={solicitar.isPending}
        onCancelar={cerrarDialogo}
        onConfirmar={(descripcion) => {
          if (dialogo?.tipo !== 'solicitar') return
          solicitar.mutate({ idCom: dialogo.c.idCom, descripcion }, { onSuccess: cerrarDialogo })
        }}
      />
    </div>
  )
}
```

Notas: `setFiltros`/`setSeleccionada` son `store.set` (`src/shared/lib/store.ts:23-26`), estables y con forma funcional, así que `setFiltros(filtrosDesdePedidos)` es válido. Si `npm run lint` no marca `react-hooks/set-state-in-effect` en esa línea y avisa de directiva sin uso, se quita el comentario `eslint-disable` (se comprueba, no se supone).

- [ ] **Step 6: Ejecutar**

Run: `npx vitest run src/modules/almacen/stock && npm run lint`
Expected: PASS (con los 4 casos nuevos de `StockPage.test.tsx` y los 2 de `filtros.test.ts`); lint limpio.

- [ ] **Step 7: Ficha `stock.md`**

En `docs/paridad/stock.md`:

- `:63` → `- [x] "Pedidos" lleva a `/stock/pedidos` (la pestaña llega en 4b: ficha `pedidos.md`). (verificado por test: `SubNav.test.tsx` "en Stock pinta las tres entradas del sidebar del JavaFX, para cualquier rol, con la activa marcada")`
- `:122` → `- [x] "Pedir" abre el formulario "Nuevo pedido" como modal encima de Stock actual, con una línea de ese componente (vacía si está desactivado); desde 4b ya no navega a `/stock/pedidos?componente=` (`stock-pedir-desde-componente`, `stock-pedir-desde-desactivado`; ficha `pedidos.md`). (verificado por test: `StockPage.test.tsx` "\"Pedir\" del menú abre \"Nuevo pedido\" en el sitio con ese componente, sin navegar (4b, P1)")`
- `:194` → `- [x] Los botones de pedir de la campana abren el formulario de pedido desde 4b (`campana-panel-alertas`; ficha `notificaciones.md`).`

Y, al final de la sección "## Tabla de stock", una línea nueva:

`- [x] Al volver desde el enlace Componente de Pedidos: desmarca OK, Bajo y Sin stock (conserva Desactivado), vacía el buscador, selecciona la fila con scroll y limpia la URL (`stock-desde-pedidos`, sub-proyecto 4b). (verificado por test: `StockPage.test.tsx` "?componente=<id> al llegar desde Pedidos: …" y "?componente= conserva \"Desactivado\" marcado (calco) …")`

- [ ] **Step 8: Commit**

```bash
git add src/modules/almacen/stock/filtros.ts src/modules/almacen/stock/filtros.test.ts src/modules/almacen/stock/StockPage.tsx src/modules/almacen/stock/StockPage.test.tsx docs/paridad/stock.md
git commit -m "feat(stock): pedir abre el formulario en el sitio y la vuelta desde pedidos selecciona el componente"
git cat-file -p HEAD | tail -1
```

Recuento: **+6 tests** (4 en `StockPage.test.tsx` y 2 en `filtros.test.ts`; el test de la navegación de "Pedir" se sustituye).

---

## Task 20: Web — smoke Playwright de Pedidos

**Files:**
- Create: `tests/e2e/pedidos.spec.ts`
- Modify: `.env.e2e.example` (bloque nuevo al final), `README.md` (sección "Smoke e2e", tras el párrafo de `stock.spec.ts`)

**Interfaces:**
- Consume: `credenciales` de `tests/e2e/credenciales.ts`; el login de `tests/e2e/stock.spec.ts:22-29`; la forma `LoteComprasRespuesta { idsCreados }` del contrato (repetida a mano: el e2e no importa código de la app, patrón de `asignar.spec.ts:4-9`).
- Supuestos sobre la interfaz de T13 y T15-T17 (se comprueban contra el código real antes de escribir el test; si no cuadran, manda el código y se anota en la ejecución):
  - Pedidos: título `heading` "Pedidos"; toggle por rutas con los enlaces "Componentes" y "Otros" (`TogglePill` usa `NavLink`, `src/shared/ui/TogglePill.tsx:1,12`); botones "Nuevo pedido" / "Nuevo otro pedido"; buscador con placeholder "Buscar componente…" en los dos toggles; columnas en el orden Pedido · Componente/Concepto · Proveedor · Cant. · … (la celda de "Cant." es la cuarta, índice 3).
  - Formularios de alta: `dialog` con nombre "Nuevo pedido" / "Nuevo otro pedido"; botón "+ Añadir línea"; el campo Componente es el `CampoAutocompletar` (`input role="combobox"`, placeholder "Escribe componente..."), el Concepto un input con placeholder "Escribe concepto..."; el proveedor de la línea es el primer `ComboNavy` del diálogo (`button role="combobox"`, `ComboNavy.tsx:78-80`; la lista va en un portal con `li role="option"` y un `button` dentro, `:98-101`); botón "Confirmar pedido".
  - Editor: `dialog` "Editar pedido #{id}", campo con etiqueta "Cantidad:" asociada (`getByLabel`), botón "Guardar".
  - Menú: ítems "Editar" y "Borrar"; confirmación `dialog` "Borrar pedido" con el botón "Borrar".

Variables: las de siempre (`E2E_USER`/`E2E_PASS` de un SUPERTECNICO) y `E2E_SKU_PRUEBA` (la misma de `stock.spec.ts`: el `tipo` exacto de un componente de prueba activo y master). Sin ella, el test se salta. El proveedor de prueba se crea **por API** con nombre sintético `e2e-proveedor-<marca>` (así los combos del formulario lo cargan al entrar en Pedidos) y su id se obtiene del listado por el nombre exacto (el alta responde 201 sin cuerpo, `ProveedorController.java:56-58`). Los pedidos se identifican por el id que devuelve el propio lote (`page.waitForResponse`), nunca por texto. El `DELETE /api/compras/{id}` responde 200 sin cuerpo (`CompraController.java:127-133`, `void` sin `@ResponseStatus`), igual el de otros. No se confirma ni se recibe nada: el smoke no toca stock.

- [ ] **Step 1: El test**

`tests/e2e/pedidos.spec.ts`:

```ts
import { expect, test, type Page, type Response } from '@playwright/test'
import { credenciales } from './credenciales.ts'

/** Forma de la respuesta de los dos lotes (schema.d.ts: LoteComprasRespuesta). Se repite aquí a mano porque el e2e no
 *  importa código de la app. */
type RespuestaLote = { idsCreados: number[] }

async function entrar(page: Page) {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

/** Petición a la API con el token de la sesión de la página (misma origin que la app; patrón de stock.spec.ts:12-20). */
async function llamarApi(page: Page, metodo: 'GET' | 'POST' | 'DELETE', ruta: string, cuerpo?: unknown): Promise<{ status: number; json: unknown }> {
  return page.evaluate(
    async ({ metodo, ruta, cuerpo }) => {
      const sesion = JSON.parse(sessionStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
      const headers: Record<string, string> = { Authorization: `Bearer ${sesion.token}` }
      if (cuerpo !== undefined) headers['Content-Type'] = 'application/json'
      const r = await fetch(ruta, { method: metodo, headers, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) })
      const texto = await r.text()
      return { status: r.status, json: texto === '' ? null : (JSON.parse(texto) as unknown) }
    },
    { metodo, ruta, cuerpo },
  )
}

/** Alta del proveedor de prueba por API y su id por el nombre EXACTO y único. Si no aparece exactamente uno, el test falla
 *  aquí sin limpiar nada (mejor basura de test que borrar una fila ajena, como asignar.spec.ts). */
async function crearProveedorDePrueba(page: Page, nombre: string): Promise<number> {
  const alta = await llamarApi(page, 'POST', '/api/proveedores', { nombre, divisa: 'EUR', tipo: 'COMPONENTES' })
  expect(alta.status, `POST /api/proveedores respondió ${alta.status}`).toBe(201)
  const lista = await llamarApi(page, 'GET', '/api/proveedores?tipo=COMPONENTES')
  expect(lista.status).toBe(200)
  const creados = (lista.json as { idProv: number; nombre: string }[]).filter((p) => p.nombre === nombre)
  expect(creados).toHaveLength(1)
  return creados[0].idProv
}

/** Solo deja pasar el DELETE de `rutaPermitida` bajo `patron`: si la fila o el menú apuntaran a otro pedido, se aborta
 *  (patrón de stock.spec.ts:85-89). */
async function soloBorrar(page: Page, patron: string, rutaPermitida: string) {
  await page.route(patron, (route) => {
    const req = route.request()
    if (req.method() === 'DELETE' && new URL(req.url()).pathname !== rutaPermitida) return route.abort()
    return route.continue()
  })
}

const esRespuesta = (ruta: string, metodo: string) => (r: Response) => new URL(r.url()).pathname === ruta && r.request().method() === metodo

/**
 * ESCRIBE en el entorno de destino: crea un proveedor de prueba por API, un pedido de componentes de una línea con
 * E2E_SKU_PRUEBA y precio 0 y un otro pedido con un concepto sintético; edita cada uno (cantidad 2) y los borra por el id
 * que devolvió su lote; al final borra el proveedor. No confirma ni recibe nada: no toca stock ni deja filas.
 */
test('supertécnico: pedido y otro pedido creados, editados y borrados con un proveedor de prueba', async ({ page }) => {
  const sku = process.env.E2E_SKU_PRUEBA
  test.skip(!sku, 'Falta E2E_SKU_PRUEBA')
  const marca = Date.now()
  const nombreProv = `e2e-proveedor-${marca}`
  const concepto = `e2e-concepto-${marca}`
  await entrar(page)

  const idProv = await crearProveedorDePrueba(page, nombreProv)
  // Rutas de los pedidos creados que siguen existiendo: se vacían al borrarlos por la interfaz; lo que quede se borra
  // por API al final.
  const porBorrar = new Set<string>()
  try {
    await page.getByRole('link', { name: 'Stock', exact: true }).click()
    await page.getByRole('link', { name: 'Pedidos', exact: true }).click()
    await expect(page).toHaveURL(/\/stock\/pedidos$/)
    await expect(page.getByRole('heading', { name: 'Pedidos' })).toBeVisible()
    await expect(page.getByText(/^Actualizado [0-9][0-9]:[0-9][0-9]$/)).toBeVisible()

    await test.step('pedido de componentes: crear, editar y borrar', async () => {
      await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
      const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
      await expect(dlg.getByText('Añade al menos una línea')).toBeVisible()
      await dlg.getByRole('button', { name: '+ Añadir línea' }).click()
      await dlg.getByPlaceholder('Escribe componente...').fill(sku!)
      await dlg.getByRole('option', { name: sku!, exact: true }).click()
      await dlg.locator('button[role="combobox"]').first().click()
      await page.getByRole('option', { name: nombreProv, exact: true }).getByRole('button').click()

      // La espera se registra ANTES del clic: waitForResponse solo atrapa respuestas posteriores al registro.
      const respuestaLote = page.waitForResponse(esRespuesta('/api/compras/lote', 'POST'))
      await dlg.getByRole('button', { name: 'Confirmar pedido' }).click()
      const respuesta = await respuestaLote
      expect(respuesta.ok(), `POST /api/compras/lote respondió ${respuesta.status()}`).toBe(true)
      const peticion = respuesta.request()
      expect(peticion.headers()['idempotency-key']).toBeTruthy()
      expect(peticion.postDataJSON()).toEqual({
        lineas: [{ idCom: expect.any(Number), idProv, cantidad: 1, esUrgente: false, precioUnidad: 0 }],
        solicitudes: { urgentes: [], preventivas: [] },
      })
      const { idsCreados } = (await respuesta.json()) as RespuestaLote
      expect(idsCreados).toHaveLength(1)
      const id = idsCreados[0]
      porBorrar.add(`/api/compras/${id}`)
      await expect(dlg).toBeHidden()

      await page.getByPlaceholder('Buscar componente…').fill(sku!)
      // El proveedor es nuevo y solo tiene este pedido: su nombre identifica la fila.
      const fila = page.getByRole('row').filter({ hasText: nombreProv })
      await expect(fila).toHaveCount(1)
      await expect(fila.getByText('pendiente', { exact: true })).toBeVisible()
      await expect(fila.getByRole('cell').nth(3)).toHaveText('1')

      await fila.click({ button: 'right' })
      await page.getByRole('menuitem', { name: 'Editar' }).click()
      const editor = page.getByRole('dialog', { name: `Editar pedido #${id}` })
      await expect(editor.getByLabel('Cantidad:')).toHaveValue('1')
      await editor.getByLabel('Cantidad:').fill('2')
      const guardado = page.waitForResponse(esRespuesta(`/api/compras/${id}`, 'PUT'))
      await editor.getByRole('button', { name: 'Guardar' }).click()
      expect((await guardado).ok()).toBe(true)
      await expect(editor).toBeHidden()
      await expect(fila.getByRole('cell').nth(3)).toHaveText('2')

      await soloBorrar(page, '**/api/compras/*', `/api/compras/${id}`)
      const borrado = page.waitForResponse(esRespuesta(`/api/compras/${id}`, 'DELETE'))
      await fila.click({ button: 'right' })
      await page.getByRole('menuitem', { name: 'Borrar' }).click()
      await page.getByRole('dialog', { name: 'Borrar pedido' }).getByRole('button', { name: 'Borrar' }).click()
      expect((await borrado).ok()).toBe(true)
      porBorrar.delete(`/api/compras/${id}`)
      await expect(fila).toHaveCount(0)
    })

    await test.step('otro pedido: crear, editar y borrar', async () => {
      await page.getByRole('link', { name: 'Otros', exact: true }).click()
      await expect(page).toHaveURL(/\/stock\/pedidos\/otros$/)
      await page.getByRole('button', { name: 'Nuevo otro pedido' }).click()
      const dlg = page.getByRole('dialog', { name: 'Nuevo otro pedido' })
      await dlg.getByRole('button', { name: '+ Añadir línea' }).click()
      await dlg.getByPlaceholder('Escribe concepto...').fill(concepto)
      await dlg.locator('button[role="combobox"]').first().click()
      await page.getByRole('option', { name: nombreProv, exact: true }).getByRole('button').click()

      const respuestaLote = page.waitForResponse(esRespuesta('/api/compras-otros/lote', 'POST'))
      await dlg.getByRole('button', { name: 'Confirmar pedido' }).click()
      const respuesta = await respuestaLote
      expect(respuesta.ok(), `POST /api/compras-otros/lote respondió ${respuesta.status()}`).toBe(true)
      expect(respuesta.request().headers()['idempotency-key']).toBeTruthy()
      expect(respuesta.request().postDataJSON()).toEqual({ lineas: [{ idProv, concepto, cantidad: 1, esUrgente: false, precioUnidad: 0 }] })
      const { idsCreados } = (await respuesta.json()) as RespuestaLote
      expect(idsCreados).toHaveLength(1)
      const id = idsCreados[0]
      porBorrar.add(`/api/compras-otros/${id}`)
      await expect(dlg).toBeHidden()

      // Los filtros son compartidos por los dos toggles: el buscador aún tiene el SKU.
      await page.getByPlaceholder('Buscar componente…').fill(concepto)
      const fila = page.getByRole('row').filter({ hasText: nombreProv })
      await expect(fila).toHaveCount(1)
      await expect(fila.getByText('pendiente', { exact: true })).toBeVisible()

      await fila.click({ button: 'right' })
      await page.getByRole('menuitem', { name: 'Editar' }).click()
      const editor = page.getByRole('dialog', { name: `Editar pedido #${id}` })
      await expect(editor.getByLabel('Cantidad:')).toHaveValue('1')
      await editor.getByLabel('Cantidad:').fill('2')
      const guardado = page.waitForResponse(esRespuesta(`/api/compras-otros/${id}`, 'PUT'))
      await editor.getByRole('button', { name: 'Guardar' }).click()
      expect((await guardado).ok()).toBe(true)
      await expect(editor).toBeHidden()
      await expect(fila.getByRole('cell').nth(3)).toHaveText('2')

      await soloBorrar(page, '**/api/compras-otros/*', `/api/compras-otros/${id}`)
      const borrado = page.waitForResponse(esRespuesta(`/api/compras-otros/${id}`, 'DELETE'))
      await fila.click({ button: 'right' })
      await page.getByRole('menuitem', { name: 'Borrar' }).click()
      await page.getByRole('dialog', { name: 'Borrar pedido' }).getByRole('button', { name: 'Borrar' }).click()
      expect((await borrado).ok()).toBe(true)
      porBorrar.delete(`/api/compras-otros/${id}`)
      await expect(fila).toHaveCount(0)
    })
  } finally {
    // Limpieza por id: primero los pedidos que queden (solo los creados por este test, siguen pendientes), después el
    // proveedor (un pedido que siga vivo le daría 409). Los fallos de limpieza se señalan sin tapar el fallo original.
    await page.unrouteAll({ behavior: 'ignoreErrors' })
    for (const ruta of porBorrar) {
      const r = await llamarApi(page, 'DELETE', ruta)
      expect.soft(r.status, `limpieza DELETE ${ruta}`).toBe(200)
    }
    const r = await llamarApi(page, 'DELETE', `/api/proveedores/${idProv}`)
    expect.soft(r.status, `limpieza DELETE /api/proveedores/${idProv}`).toBe(204)
  }
})
```

- [ ] **Step 2: `.env.e2e.example` y README**

Al final de `.env.e2e.example`:

```
# pedidos.spec.ts ESCRIBE: con E2E_USER crea por API un proveedor "e2e-proveedor-<marca>", un pedido de una línea del
# SKU E2E_SKU_PRUEBA (precio 0) y un otro pedido "e2e-concepto-<marca>"; edita los dos (cantidad 2) y los borra por el id
# que devuelve su lote; después borra el proveedor. No confirma ni recibe nada (no toca stock). Usa E2E_SKU_PRUEBA.
```

En `README.md`, sección "Smoke e2e", tras la frase que termina en "Usa siempre un SKU de prueba, nunca uno real." (antes de "Sin credenciales (o sin …"):

```
`pedidos.spec.ts` también **escribe**: con `E2E_USER` crea por API un proveedor de prueba `e2e-proveedor-<marca>`, crea
desde "Nuevo pedido" un pedido de una línea del SKU `E2E_SKU_PRUEBA` con precio 0 y desde "Nuevo otro pedido" uno con un
concepto sintético, edita los dos (cantidad 2) y los borra por el id que devuelve su propio lote; al final borra el
proveedor (lo que no se haya borrado por la interfaz se borra por API, siempre por id). No confirma ni recibe ningún
pedido, así que no toca stock.
```

y en la frase final, `o sin E2E_SKU_PRUEBA en el de stock.spec.ts` pasa a `o sin E2E_SKU_PRUEBA en el de stock.spec.ts y pedidos.spec.ts`.

- [ ] **Step 3: Comprobaciones locales sin escribir**

```bash
npm run lint
npx playwright test --list
```

Expected: lint limpio (`eslint .` cubre `tests/e2e`); `--list` compila el fichero y enumera el test nuevo junto a los seis anteriores. **El smoke no se ejecuta aquí**: escribe en producción y necesita el servidor de la rama desplegado (los lotes no existen antes). Se lanza en T21, paso del usuario U4, con su OK.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/pedidos.spec.ts .env.e2e.example README.md
git commit -m "test(e2e): smoke de pedidos y otros pedidos con proveedor de prueba y limpieza por id"
git cat-file -p HEAD | tail -1
```

Recuento: **+0 tests unitarios** (+1 test e2e).

---

## Task 21: Ficha de paridad, versión, verificación final y cierre

**Files:**
- Create: `docs/paridad/pedidos.md` (web)
- Modify: `CHANGELOG.md`, `package.json`, `package-lock.json` (web, versión `0.7.0`; la versión que pinta la web sale solo de `package.json` vía `__APP_VERSION__`, `vite.config.ts:8,14` y `src/shared/lib/version.ts:1`)
- Modify (raíz): este plan, sección "Ejecución y cierre"
- Fuera de los repos (privados, sin commit): `Apuntes/paridad-capturas/almacen/CAPTURAS-4b.md`, `Apuntes/paridad-capturas/almacen/COMPARACION-4b.md`, `Apuntes/plan-futuro.md`, memoria del proyecto

**Interfaces:** consume todo lo anterior; no produce código.

- [ ] **Step 1: La ficha**

`docs/paridad/pedidos.md` (web), completa:

```md
# Ficha de paridad — Pedidos y formularios de pedido (StockController, FormularioCompra*, FormularioOtroPedido*)

Referencia: línea hotfix del cliente JavaFX (`hotfix/0.16.3`, la que usa la tienda), la pestaña "Pedidos" de la vista Stock de `StockController` (toggle Componentes | Otros) y los formularios `FormularioCompra`, `FormularioCompraEditar`, `FormularioOtroPedido` y `FormularioOtroPedidoEditar`, con los puntos de contacto de Stock actual y de la campana. Los combos de proveedor se comparan con `?tipo=COMPONENTES` de `main`, porque el hotfix pide los proveedores sin tipo. Spec de este sub-proyecto: raíz `docs/superpowers/specs/2026-09-25-web-almacen-pedidos-design.md`.

Capturas de referencia (documentación privada, fuera del repo; llevan datos reales del taller y se citan solo por nombre): `almacen/pedidos-*.png`, `almacen/form-*.png` y `almacen/stock-desde-pedidos.png`, más las cinco de referencia tomadas antes del plan (`pedidos-vista-referencia-4b`, `pedidos-menu-pendiente-referencia-4b`, `pedidos-menu-en-camino-referencia-4b`, `pedidos-cancelar-confirm-referencia-4b`, `pedidos-nuevo-combo-proveedor-referencia-4b`). Las de la web llevan el prefijo `web-`. Las situaciones que no se puedan reproducir en la toma se anotan como tales y se comprueban con la web o por test.

Los ejemplos de componente ("bat-x", "lcd-x-negro"), de proveedor ("Proveedor A", "ACME") y de concepto ("Cinta de embalar") son sintéticos.

## Diferencias deliberadas respecto al JavaFX

Las de la spec §10:

- **El formulario de alta es un modal encima de la vista actual** desde Stock actual ("Pedir"), Pedidos ("Nuevo pedido" / "Nuevo otro pedido") y la campana (P1). Calco del `Stage` modal; el parámetro `?componente=` que 4a dejó en "Pedir" desaparece.
- **Sin título de ventana**: el JavaFX titula la ventana "Nuevo pedido — alertas de stock" o "— solicitudes pendientes"; la web no tiene título de ventana y el interno dice siempre "Nuevo pedido", como el `lblTitulo` del JavaFX.
- **Editar un pedido recibido precarga la cantidad pedida** y el servidor responde 422 "No se puede cambiar la cantidad de un pedido recibido." si cambia (P2); el JavaFX precargaba la recibida y la escribía en la pedida.
- **`precioEur` lo calcula el servidor** en todas las escrituras con `precio / tasa` redondeado a 2 decimales (P3); el JavaFX multiplicaba. Corrige también al JavaFX 0.16.x, que sigue mandando su valor y el servidor lo ignora.
- **Alta por lotes** con clave de idempotencia y una sola transacción; las solicitudes que originan las líneas se marcan GESTIONADA en el mismo guardado (P5, D10). En el JavaFX eran N altas y N marcados sueltos, que duplicaban al reintentar.
- **Componente desactivado en "Pedir piezas"**: se omite, el formulario avisa "N solicitud(es) de componentes desactivados no se han añadido y siguen pendientes." y su solicitud no se marca (D10); el JavaFX lo omitía en silencio y lo marcaba GESTIONADA.
- **"Recepción parcial" y "Recibir unidades" son diálogos propios** con el error inline, y los avisos de los formularios ("Línea i: …", "Selecciona un proveedor.", 409 y 422 al guardar) van en una línea de error dentro del formulario, que sigue abierto (P6).
- **Textos corregidos** (P7): la cabecera de "Recibir unidades" dice "Si introduces {restante}, el pedido se cerrará como recibido." (sin "o más", que el cliente bloqueaba); la etiqueta de tasa del editor dice "(1 {DIV} = {1/tasa} €)"; "Actualizado HH:mm" recarga la tabla visible (el JavaFX, siempre la de componentes); sin tasa, el Total EUR dice "—" en vez de calcular como si fuera EUR.
- **Celdas de la tabla de líneas siempre editables** (P8), en vez del paso `Label` → `TextField` del `TableView`.
- **Ordenación por cabecera bloqueada** en las dos tablas (P10).
- **La selección se mantiene en el refresco y el refresco se congela** con un menú, un diálogo o un formulario abiertos (S4, D4); el JavaFX perdía la selección y seguía recargando detrás del formulario.
- **Llegada desde "En Camino" siempre al toggle Componentes**; el JavaFX se quedaba en Otros si estaba ahí.
- **Filtros Estado y Proveedor como `MultiSelect`** sin el buscador "Buscar…" del desplegable de proveedores (aceptado en 4a) y **fechas "Desde/Hasta" tecleables** (aceptado en Historial).
- **Guards de estado y rangos en el servidor**: una transición no permitida responde 409 y una cantidad fuera de rango 422, con los textos del cliente; el JavaFX 0.16.x no los dispara porque su menú y sus validaciones ya lo impiden.
- **Editar un pedido cuyo proveedor se desactivó después** responde 422 "El proveedor no está activo." aunque no se cambie el proveedor (spec §4.2); el JavaFX no lo bloqueaba, y es la única regla nueva que el JavaFX 0.16.x puede disparar al editar un pedido existente. El componente no se valida en la edición (el `PUT` no lo lleva).

Inocuas:

- **Recarga de compras, stock y campana tras cualquier acción**; el JavaFX recargaba stock solo en algunas.
- **Los botones de pedir de la campana cierran el panel** antes de abrir el formulario (spec §6); en el JavaFX la ventana de notificaciones quedaba abierta detrás del formulario modal.
- **"Pedir piezas" queda deshabilitado mientras relee** las pendientes; en el JavaFX la relectura bloqueaba la interfaz.
- **El congelado del refresco con el formulario de pedido abierto aplica a Stock actual y Pedidos** (las vistas que leen el store del formulario); una vista del taller desde la que se abra el formulario por la campana sigue sondeando debajo del modal, sin tocar nada que el usuario esté editando.
- **Clic derecho en una fila cancelada** muestra el menú nativo del navegador (no hay `preventDefault` sin menú propio), igual que hoy en las filas sin menú de ADMIN y TECNICO; el JavaFX no muestra nada.
Decididas durante la ejecución y la comparación de capturas: se añaden aquí, cada una con la decisión del usuario.

Internas, sin efecto visible: `crearClavesIdempotencia` vive en `shared/lib` (P9); el formulario de alta se abre con el store `shared/lib/formularioPedido.ts` y lo pinta un host en el shell; los helpers puros van en `.ts` propios por la regla de React Refresh.

## Calcos

- Chips de estado sin marcar al entrar; badge con el estado tal cual (`en_camino` con guion bajo); "⚠" solo en urgentes en camino o parciales (no en pendientes).
- Confirmación de cancelar con los botones "Cancelar pedido" y "Cancelar".
- Placeholder "Buscar componente…" también en Otros; el filtro Proveedor solo ofrece activos.
- En el editor, la divisa es independiente del proveedor (EUR/USD) y un proveedor inactivo deja el combo vacío; el editor de otros no tiene "Urgente" y conserva el valor.
- "Pedir piezas" con las dos listas vacías y "Pedir todas las piezas" sin alertas no hacen nada.
- "Cancelar" del formulario cierra sin preguntar aunque haya líneas.
- El autocompletar de componentes incluye los slaves de SKU compartido; el servidor los resuelve al master.
- CSV de otros sin "Urgente"; "Cantidad" es la pedida; Estado con guion bajo; coma decimal.
- El TECNICO y el ADMIN ven Pedidos sin botones "Nuevo…" ni menú; `/api/tipo-cambio` sin rol (D6, SP7).
- Columna "Div." oculta; símbolo en P.Unit. de la tabla: `€`, `$` o el código de la divisa; en los formularios de alta, `$` para USD y `€` para el resto.

## Pendiente de decidir

Sin puntos al escribir la ficha.

## Pestaña, rutas y toggle

- [ ] "Pedidos" en la columna lateral de Stock abre `/stock/pedidos`; título "Pedidos" y, a la derecha, el toggle "Componentes" | "Otros" con Componentes marcado (`pedidos-vista-referencia-4b`, `pedidos-con-datos`).
- [ ] "Otros" abre `/stock/pedidos/otros` con su tabla y el botón "Nuevo otro pedido" (`pedidos-otros-con-datos`).
- [ ] El botón "Stock" de la barra superior vuelve a la última pestaña de Stock, también desde Pedidos.
- [ ] Los tres roles ven la pestaña; ADMIN y TECNICO sin "Nuevo…" ni menú (`pedidos-admin`, `pedidos-tecnico`).

## Tabla de componentes

- [ ] Columnas Pedido (`dd/MM/yy HH:mm`, hora de Madrid), Componente (enlace), Proveedor, Cant., P.Unit., EUR y Estado; sin "Div." (`pedidos-con-datos`).
- [ ] Cant.: "recibida/cantidad" en parcial, la recibida en recibido, la cantidad en el resto (`pedidos-cant-parcial`).
- [ ] P.Unit. "12,50 €" con coma decimal; `$` en USD; total EUR = unidades × importe en euros (`pedidos-usd`).
- [ ] "!" ámbar en P.Unit. y EUR de un recibido con precio o total 0 (`pedidos-precio-cero`).
- [ ] Orden del servidor (fecha descendente) con los cancelados al final (`pedidos-con-datos`).
- [ ] Placeholder "Sin pedidos" con la barra de filtros completa (`pedidos-vacio`).
- [ ] Enlace Componente azul, subrayado al pasar.

## Tabla de otros

- [ ] Concepto como texto sin enlace en vez de Componente; mismas reglas de Cant., P.Unit., EUR y Estado (`pedidos-otros-con-datos`).
- [ ] Placeholder "Sin otros pedidos" (`pedidos-otros-vacio`).

## Colores y badges

- [ ] Barra izquierda de 8 px: pendiente ámbar, en camino urgente naranja y normal sin barra, recibido verde, parcial violeta; cancelado sin barra y atenuado al 45 % (`pedidos-con-datos`).
- [ ] Badges: pendiente, en camino urgente y normal, recibido, parcial y cancelado con sus colores; radio 12, 11 px negrita; "⚠" a la derecha en urgentes en camino o parciales (`pedidos-con-datos`).
- [ ] Fila seleccionada navy con textos claros; la opacidad del cancelado prevalece (`pedidos-fila-seleccionada`).

## Filtros

- [ ] "Estado" con "pendiente", "en camino", "parcial", "recibido" y "cancelado", ninguno marcado al entrar; el botón dice el único marcado o "N estados" (`pedidos-filtro-estado-abierto`).
- [ ] "Proveedor" con solo los activos; "N proveedores" con varios (`pedidos-filtro-proveedor-abierto`).
- [ ] Buscador "Buscar componente…" en los dos toggles: "contiene", sin mayúsculas, sobre el componente o el concepto.
- [ ] "Desde:" / "Hasta:" inclusivos sobre la fecha del pedido en hora de Madrid (`pedidos-filtro-fechas`).
- [ ] Los cuatro filtros se combinan con Y y se conservan al cambiar de toggle y al volver desde Reparaciones.
- [ ] "Limpiar filtros" desmarca estados y vacía proveedor, buscador y fechas, sin tocar el toggle ni la selección.

## Pie y refresco

- [ ] "Actualizado HH:mm" a la derecha, subrayado al pasar, recarga la tabla visible (diferencia, ver arriba) (`pedidos-actualizado-hover`).
- [ ] Refresco cada 60 s (5 s con el banner), solo de la tabla visible, con la selección mantenida.

## Llegada desde Stock y vuelta a Stock

- [ ] "En Camino" de Stock actual abre Pedidos con "pendiente", "en camino" y "parcial" marcados, el componente en el buscador y la primera fila seleccionada, sin tocar proveedor ni fechas (`pedidos-desde-en-camino`).
- [ ] El enlace Componente vuelve a Stock actual con OK, Bajo y Sin stock desmarcados (Desactivado se conserva), el buscador vacío y la fila del componente seleccionada con scroll (`stock-desde-pedidos`).
- [ ] "Pedir" de Stock actual abre "Nuevo pedido" encima de Stock con la línea del componente (`form-nuevo-desde-stock`).

## Menú contextual (solo SUPERTECNICO)

- [ ] pendiente: "Confirmar pedido" · separador · "Editar" · "Borrar" (`pedidos-menu-pendiente`, `pedidos-menu-pendiente-referencia-4b`).
- [ ] en camino: "Recepción parcial" · "Confirmar recibido" · separador · "Editar" · "Cancelar pedido" (`pedidos-menu-en-camino`, `pedidos-menu-en-camino-referencia-4b`).
- [ ] parcial: "Recibir resto" · "Cerrar sin resto" (`pedidos-menu-parcial`).
- [ ] recibido: "Revertir a En camino" · separador · "Editar" (`pedidos-menu-recibido`).
- [ ] cancelado: sin menú (`pedidos-menu-cancelado`).

## Acciones sin diálogo

- [ ] "Confirmar pedido", "Confirmar recibido" y "Cerrar sin resto" escriben al pulsar y recargan.

## Recepción parcial y Recibir unidades

- [ ] "Recepción parcial": "Pedido #{id} — {componente} ({cantidad} pedidas)", "Cantidad recibida ahora:", campo vacío, "Confirmar" (`pedidos-dialogo-parcial`).
- [ ] Fuera de rango: "La cantidad debe ser mayor que 0 y menor que {cantidad}." inline (`pedidos-error-parcial-rango`).
- [ ] "Recibir unidades": "Pedido #{id} — {componente} (recibidas: {recibida}/{cantidad})" y "Si introduces {restante}, el pedido se cerrará como recibido.", "Cantidad que llega ahora:" precargada con el restante (diferencia de texto, ver arriba) (`pedidos-dialogo-resto`).
- [ ] Exceso: "No puedes recibir más de lo pedido. Faltan {restante} unidad(es)." inline (`pedidos-error-resto-exceso`).

## Confirmaciones

- [ ] "Cancelar pedido" / "¿Cancelar el pedido #{id} de {componente}?" con "Cancelar pedido" y "Cancelar" (`pedidos-confirm-cancelar`, `pedidos-cancelar-confirm-referencia-4b`).
- [ ] "Borrar pedido" / "¿Borrar el pedido pendiente #{id} de {componente}?" con "Borrar" (`pedidos-confirm-borrar`).
- [ ] "Revertir a En camino" de componentes con las tres líneas y "Se descontarán {n} unidad(es) del stock." (`pedidos-confirm-revertir`).
- [ ] "Revertir a En camino" de otros con una sola línea (`pedidos-otros-confirm-revertir`).

## Errores de las acciones

- [ ] 409 en una transición: "Este pedido fue modificado por otro usuario. Los datos se han recargado." y recarga (`pedidos-conflicto`).
- [ ] 409 al revertir: el mensaje del servidor (stock insuficiente) y recarga (`pedidos-error-revertir-stock`).

## Nuevo pedido

- [ ] Modal de 700 px, título "Nuevo pedido", placeholder "Añade al menos una línea", "+ Añadir línea", "Cancelar" y "Confirmar pedido" (`form-nuevo-vacio`).
- [ ] Línea: Componente (campo navy, "Escribe componente..."), Proveedor (combo navy), Cant. 1, P.Unit. "0,00" con `€`/`$`, Urg., Total EUR y papelera (diferencia de celdas editables, ver arriba) (`form-nuevo-linea`).
- [ ] Autocompletar "contiene", hasta 6 filas visibles; Enter elige la primera (`form-nuevo-popup-componente`).
- [ ] Cant. y P.Unit. editables (`form-nuevo-editando-cantidad`).
- [ ] Proveedor en USD: `$` en P.Unit. y Total EUR convertido con `precio / tasa` (`form-nuevo-usd`).
- [ ] "+ Añadir línea" añade, selecciona y desplaza hasta la línea nueva (`form-nuevo-varias-lineas`).
- [ ] Avisos inline: "Añade al menos una línea.", "Línea {i}: selecciona un componente.", "Línea {i}: selecciona un proveedor.", "Línea {i}: la cantidad debe ser mayor que 0.", "Línea {i}: el precio no puede ser negativo." (`form-nuevo-error-sin-lineas`, `form-nuevo-error-sin-componente`, `form-nuevo-error-sin-proveedor`, `form-nuevo-error-cantidad`, `form-nuevo-error-precio`).
- [ ] Guardar cierra y recarga pedidos, stock y campana; "Cancelar" cierra sin preguntar.

## Precargas del Nuevo pedido

- [ ] Desde "Pedir" de Stock actual o de una alerta: una línea con el componente, cantidad 1, sin proveedor; vacía si está desactivado (`form-nuevo-desde-stock`, `stock-pedir-desde-desactivado`).
- [ ] Desde "Pedir todas las piezas": una línea por alerta, cantidad 1, en el orden de la campana (`form-nuevo-desde-alertas`).
- [ ] Desde "Pedir piezas": una línea por componente con cantidad = número de solicitudes, urgentes primero (`form-nuevo-desde-solicitudes`).

## Nuevo otro pedido

- [ ] Título "Nuevo otro pedido", columna Concepto con "Escribe concepto..." (`form-otro-vacio`, `form-otro-linea`).
- [ ] "Línea {i}: el concepto no puede estar vacío." antes de proveedor, cantidad y precio (`form-otro-error-concepto`).

## Editar pedido

- [ ] "Editar pedido #{id}" (520 px): "Componente:" de solo lectura, "Proveedor:", "Cantidad:" ("Ej. 10"), "Urgente:", "Precio unidad:" con EUR/USD, "Total EUR:", "Cancelar" y "Guardar" (`form-editar-pendiente`).
- [ ] Divisa distinta de EUR: "Obteniendo tasa…" y después el total con "  (1 USD = x,xxxx €)" (diferencia de sentido, ver arriba) (`form-editar-usd`).
- [ ] Recibido: la cantidad precargada es la pedida (diferencia, ver arriba) (`form-editar-recibido`).
- [ ] Proveedor inactivo: combo vacío (calco) (`form-editar-proveedor-inactivo`).
- [ ] Avisos inline "Selecciona un proveedor.", "Cantidad no válida (debe ser > 0).", "Precio no válido." (`form-editar-error-proveedor`, `form-editar-error-cantidad`, `form-editar-error-precio`).
- [ ] 409: "El pedido fue modificado por otro usuario. Cierra y recarga los datos." inline, formulario abierto (`form-editar-conflicto`).

## Editar otro pedido

- [ ] "Concepto:" arriba ("Descripción del pedido"), sin "Urgente" (`form-editar-otro`).
- [ ] "El concepto no puede estar vacío." antes que el resto (`form-editar-otro-error-concepto`).

## Campana

- [ ] "Pedir" de una alerta, "Pedir todas las piezas" y "Pedir piezas" cierran el panel y abren "Nuevo pedido" con su precarga (`form-nuevo-desde-alertas`, `form-nuevo-desde-solicitudes`).
- [ ] "→ Ir a pedidos" y "Ver Stock Completo" siguen como en 4a.

## CSV

- [ ] Componentes: `pedidos_<fecha>_<hora>.csv` con `Fecha pedido;Componente;Cantidad;Urgente;Proveedor;Precio unidad;Divisa;Total EUR;Estado`, "Sí"/"No", coma decimal, filas filtradas en el orden de pantalla (`pedidos-csv`).
- [ ] Otros: `pedidos_otros_<fecha>_<hora>.csv` sin "Urgente".

## Comprobado por tests

Lo que no se ve en una captura o no se puede provocar en la toma:

- [ ] Máquina de estados en el servidor: cada transición permitida escribe y cada prohibida responde 409 sin tocar stock, en componentes y en otros (`CompraComponenteDAOTransicionesTest`, `CompraOtroDAOTransicionesTest`).
- [ ] Rangos de parcial y resto, validación de alta y edición y 422 de cantidad en un recibido, sin escribir ni registrar log (`CompraControllerTest` / `CompraOtroControllerTest` (validaciones de `ValidacionPedidos`)).
- [ ] `precioEur` = `precio / tasa` con 2 decimales; EUR sin llamada; 503 con mensaje si falla el tipo de cambio (`ConversionEurTest`).
- [ ] Lotes: 400 sin clave, 422 por línea, atómicos, reintento con la misma clave sin volver a insertar, solicitudes marcadas en la transacción, solicitud sin línea 422 (`CompraLoteControllerTest`, `CompraLoteControllerTest` (casos de otros)).
- [ ] Contrato con las dos rutas nuevas, sus esquemas y los nullables (`OpenApiContractTest`).
- [ ] Reintento del formulario con la misma clave tras un fallo (`NuevoPedidoDialog.test.tsx`, `NuevoOtroPedidoDialog.test.tsx`).
- [ ] Congelación del refresco con menú, diálogo o formulario abiertos (`PedidosPage.test.tsx`, `StockPage.test.tsx` "con el formulario de pedido abierto el sondeo se congela; al cerrarlo se reanuda").
- [ ] Selección tras el refresco (`PedidosPage.test.tsx`).
- [ ] Componentes desactivados omitidos y avisados en "Pedir piezas" (`lineas.test.ts`, `NuevoPedidoDialog.test.tsx`).
- [ ] "Pedir piezas" sin pendientes y "Pedir todas las piezas" sin alertas no hacen nada (`PanelNotificaciones.test.tsx`).
```

- [ ] **Step 2: Versión y CHANGELOG**

```bash
npm version 0.7.0 --no-git-tag-version
node -p "require('./package.json').version"
```

Expected: `v0.7.0` y después `0.7.0`; `git diff --stat` muestra `package.json` y `package-lock.json` (las dos líneas `"version"` de `package-lock.json:3,9`).

`CHANGELOG.md`, entrada nueva encima de `## [0.6.0]`:

```md
## [0.7.0] - 2026-09-XX — Pedidos

- Pestaña Pedidos en `/stock/pedidos` con el conmutador Componentes | Otros: tablas con fecha, componente o concepto, proveedor, cantidad, precio por unidad, total en euros y estado, con sus colores, "!" en los recibidos a precio cero y "⚠" en los urgentes en camino; filtros de estado, proveedor, buscador y fechas compartidos por las dos tablas, "Limpiar filtros" y CSV de cada tabla.
- Menú del supertécnico según el estado del pedido: confirmar pedido, recepción parcial, confirmar recibido, recibir resto, cerrar sin resto, cancelar, borrar, revertir a En camino y editar, con sus diálogos y confirmaciones.
- Formularios "Nuevo pedido" y "Nuevo otro pedido" como ventana encima de la vista en la que estás, con varias líneas y el total en euros de cada una; "Editar pedido" para pedidos de componentes y de otros.
- Stock actual: "Pedir" abre el formulario sin salir de Stock; el componente de un pedido lleva a su fila de Stock actual y "En Camino" lleva a sus pedidos.
- La campana: "Pedir" de una alerta, "Pedir todas las piezas" y "Pedir piezas" abren el formulario; al confirmar, las solicitudes pedidas quedan gestionadas en el mismo guardado y las de componentes desactivados se avisan y siguen pendientes.
- Servidor: los cambios de estado y las cantidades de recepción se validan también en el servidor; el importe en euros lo calcula el servidor con el tipo de cambio en el sentido correcto, también para el programa de escritorio; el alta de varios pedidos se guarda de una vez, sin quedar a medias ni duplicarse al reintentar.
- Diferencias aceptadas respecto al programa de escritorio: `docs/paridad/pedidos.md`.
```

La fecha `XX` se fija el día del tag (U10).

- [ ] **Step 3: Verificación completa de los tres repos**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git branch --show-current && mvn -q test
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git branch --show-current && npm run lint && npx tsc -b && npx vitest run && npm run build
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente && git status --short && mvn -q test
node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/comparar-openapi.mjs \
  /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor/target/openapi.json \
  /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/api/openapi.json
grep -l '0\.7\.0' /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/dist/assets/*.js
```

Expected: las dos ramas `feature/web-pedidos`; servidor en verde con **408 tests** (286 + 122 de T1-T6); web: lint y `tsc -b` limpios, Vitest en verde con **1322 tests** (1119 + 112 de T7-T13 + 78 de T14-T17 + 13 de T18-T19) y build correcto; cliente JavaFX con `git status` vacío y sus 284 tests en verde; `comparar-openapi.mjs` sin diferencias salvo `servers`; el bundle contiene `0.7.0`.

- [ ] **Step 4: Commit de cierre en la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git add docs/paridad/pedidos.md CHANGELOG.md package.json package-lock.json
git commit -m "docs(web): ficha de pedidos sin marcar, CHANGELOG y version 0.7.0"
git cat-file -p HEAD | tail -1
```

- [ ] **Step 5: "Ejecución y cierre" en el raíz**

Añadir al final de este plan la sección **"## Ejecución y cierre (2026-09-XX)"** con el mismo esquema que la del 4a (`docs/superpowers/plans/2026-09-24-web-almacen-stock.md:3807-3861`): estado ("código terminado; pendiente de smoke, capturas y OK del usuario"), rama y head de cada repo con la lista de commits, suites (Step 3), desviaciones respecto al plan, backlog menor de las revisiones y la lista "Pendiente, del usuario y uno a uno" con los pasos U1-U13 de abajo. Commit en `main` del raíz:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/superpowers/plans/<este plan>.md
git commit -m "docs(plan): ejecucion del sub-proyecto 4b (pendiente de smoke, capturas y OK del usuario)"
```

- [ ] **Step 6: Lista privada de capturas del JavaFX**

Crear `C:\Users\dev\Documents\Apuntes\paridad-capturas\almacen\CAPTURAS-4b.md` (fuera del repo; formato de `CAPTURAS-4a.md`):

```md
# Capturas del JavaFX para el sub-proyecto 4b — Pedidos y formularios de pedido

Tomar en el JavaFX **`hotfix/0.16.3`** lanzado desde el worktree `Documents/_ref-hotfix-0163`, con `config.properties`
apuntando a **producción** (BD de pruebas; con el servidor de 4b ya desplegado), sesión SUPERTECNICO salvo que se indique.
Guardar en esta carpeta con el nombre exacto. Las que no sean reproducibles se anotan como "no reproducible", no se inventan.
Los pedidos que se creen para las capturas se anotan con su id en "Datos creados" (van a la limpieza de la VDC).

Marcar `[x]` al tomar cada una.

## Datos creados para las capturas (ids)

- Pedidos de componentes: …
- Otros pedidos: …
- Recepciones que han sumado stock (revertir al terminar o anotar el SKU y las unidades): …

## Pestaña Pedidos

- [ ] `pedidos-vacio.png` — Buscador sin coincidencias: "Sin pedidos" y la barra de filtros completa.
- [ ] `pedidos-con-datos.png` — Componentes con una fila de cada estado (pendiente, en_camino normal, en_camino urgente, parcial, recibido, cancelado): barras, badges, "⚠", opacidad y cancelados al final.
- [ ] `pedidos-fila-seleccionada.png` — Una fila seleccionada.
- [ ] `pedidos-precio-cero.png` — Un recibido con precio 0: "!" en P.Unit. y EUR.
- [ ] `pedidos-cant-parcial.png` — Cant. de un parcial ("3/10") y de un recibido con recibida < pedida.
- [ ] `pedidos-usd.png` — Fila de proveedor USD: "12,50 $" y total en "€".
- [ ] `pedidos-filtro-estado-abierto.png` — "Estado" desplegado con dos marcados y el botón "2 estados".
- [ ] `pedidos-filtro-proveedor-abierto.png` — Desplegable Proveedor y "N proveedores".
- [ ] `pedidos-filtro-fechas.png` — Desde/Hasta con el calendario abierto.
- [ ] `pedidos-desde-en-camino.png` — Tras "En camino" de Stock actual: tres chips marcados, buscador con el componente y primera fila seleccionada.
- [ ] `stock-desde-pedidos.png` — Tras el enlace Componente de Pedidos: Stock actual con la fila del componente seleccionada.
- [ ] `pedidos-menu-pendiente.png`, `pedidos-menu-en-camino.png`, `pedidos-menu-parcial.png`, `pedidos-menu-recibido.png` — Menú de cada estado.
- [ ] `pedidos-menu-cancelado.png` — Clic derecho en un cancelado: sin menú.
- [ ] `pedidos-dialogo-parcial.png` — "Recepción parcial" con cabecera y campo.
- [ ] `pedidos-error-parcial-rango.png` — "La cantidad debe ser mayor que 0 y menor que N."
- [ ] `pedidos-dialogo-resto.png` — "Recibir unidades" con el valor por defecto y la cabecera de dos líneas.
- [ ] `pedidos-error-resto-exceso.png` — "No puedes recibir más de lo pedido. Faltan N unidad(es)."
- [ ] `pedidos-confirm-cancelar.png` — Confirmación "Cancelar pedido" con los dos botones.
- [ ] `pedidos-confirm-borrar.png` — Confirmación "Borrar pedido".
- [ ] `pedidos-confirm-revertir.png` — "Revertir a En camino" de componentes (tres líneas).
- [ ] `pedidos-error-revertir-stock.png` — 409 de revertir con stock insuficiente (mensaje del servidor).
- [ ] `pedidos-conflicto.png` — "Este pedido fue modificado por otro usuario…" (provocar desde otra sesión).
- [ ] `pedidos-otros-con-datos.png` — Toggle en Otros con varios estados y "Nuevo otro pedido".
- [ ] `pedidos-otros-vacio.png` — "Sin otros pedidos".
- [ ] `pedidos-otros-confirm-revertir.png` — Revertir en otros (una línea).
- [ ] `pedidos-admin.png` / `pedidos-tecnico.png` — ADMIN y TECNICO: sin "Nuevo…" ni menú.
- [ ] `pedidos-actualizado-hover.png` — "Actualizado HH:mm" subrayado.
- [ ] `pedidos-csv.png` — Nombre `pedidos_yyyy-MM-dd_HH-mm.csv` y el CSV abierto ("Sí/No", coma decimal).

## Formularios

- [ ] `form-nuevo-vacio.png` — "Nuevo pedido" desde el botón: "Añade al menos una línea".
- [ ] `form-nuevo-linea.png` — Una línea seleccionada: campo navy, combo proveedor, Cant., P.Unit. y Total EUR.
- [ ] `form-nuevo-popup-componente.png` — Popup filtrando (≤ 6 filas).
- [ ] `form-nuevo-editando-cantidad.png` — Cant. en edición.
- [ ] `form-nuevo-usd.png` — Línea USD ("$" y total convertido) y otra EUR.
- [ ] `form-nuevo-varias-lineas.png` — 4-5 líneas con scroll.
- [ ] `form-nuevo-error-sin-lineas.png`, `form-nuevo-error-sin-componente.png`, `form-nuevo-error-sin-proveedor.png`, `form-nuevo-error-cantidad.png`, `form-nuevo-error-precio.png` — Los cinco avisos.
- [ ] `form-nuevo-desde-stock.png` — Desde "Pedir" de Stock actual.
- [ ] `form-nuevo-desde-alertas.png` — Desde "Pedir todas las piezas".
- [ ] `form-nuevo-desde-solicitudes.png` — Desde "Pedir piezas".
- [ ] `form-otro-vacio.png` / `form-otro-linea.png` — "Nuevo otro pedido".
- [ ] `form-otro-error-concepto.png` — "Línea 1: el concepto no puede estar vacío."
- [ ] `form-editar-pendiente.png` — "Editar pedido #N" de un pendiente EUR.
- [ ] `form-editar-usd.png` — Editar un USD: "Obteniendo tasa…" (si se pilla) y el total con la tasa.
- [ ] `form-editar-recibido.png` — Editar un recibido con recibida < pedida.
- [ ] `form-editar-proveedor-inactivo.png` — Proveedor desactivado: combo vacío.
- [ ] `form-editar-error-proveedor.png`, `form-editar-error-cantidad.png`, `form-editar-error-precio.png` — Los tres avisos.
- [ ] `form-editar-conflicto.png` — "El pedido fue modificado por otro usuario. Cierra y recarga los datos."
- [ ] `form-editar-otro.png` — Editor de otros (Concepto arriba, sin Urgente).
- [ ] `form-editar-otro-error-concepto.png` — "El concepto no puede estar vacío."
```

- [ ] **Step 7: Parar y pedir OK al usuario**

**No hacer push, merge, tag ni despliegue.** Presentar qué se ha hecho, el estado de los tres repos y la lista de pasos que requieren su OK **uno a uno**. Claude no hace SSH a las VMs: los comandos de la VDC se le preparan y los ejecuta el usuario. Orden (las capturas de la web se comparan **antes del merge de la web**: spec §9 y Global Constraints):

**U1. Push de las dos ramas** (copia de seguridad; con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor push -u origin feature/web-pedidos
git -C gestion-reparaciones-web push -u origin feature/web-pedidos
```

**U2. Merge del servidor en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-pedidos
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q test
git push origin main
```

Expected: suite en verde en `main` antes del push.

**U3. Despliegue del servidor en la VDC y contrato** (lo ejecuta el usuario; guía privada `Apuntes/despliegue_vdc_produccion.md` §P8). Solo el backend: la web 0.6.0 desplegada sigue funcionando con el servidor nuevo porque todo es aditivo.

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-servidor pull && git -C gestion-reparaciones-servidor log --oneline -1
docker compose up -d --build backend
docker compose logs --tail=80 backend | grep -E "Started|ERROR"
exit
```

Expected: el `log -1` muestra el merge de U2 y los logs, `Started App`. Después, en el PC (Git Bash, desde la web, credenciales de `~/.env.e2e` exportadas y sin escribirlas en la línea de comandos):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
cp api/openapi.json "$TMPDIR/openapi-rama.json"
API_URL="$E2E_BASE_URL" API_USER="$E2E_USER" API_PASS="$E2E_PASS" node scripts/fetch-openapi.mjs
node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/comparar-openapi.mjs "$TMPDIR/openapi-rama.json" api/openapi.json
git checkout api/openapi.json
```

Expected: sin diferencias salvo `servers` (lección del 4a); `git checkout` devuelve el snapshot determinista. `node scripts/fetch-openapi.mjs` (no `npm run api:types`) para no regenerar `schema.d.ts`.

**U4. Smoke contra producción con la web de la rama en local** (escribe en la BD de pruebas; con OK). `.env.local` con `VITE_API_PROXY_TARGET` apuntando a la API de producción (lección del 3b) y el puerto 5173 libre:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout feature/web-pedidos
npm run dev   # en otra terminal
set -a; . ~/.env.e2e; set +a
E2E_BASE_URL=http://localhost:5173 npx playwright test tests/e2e/pedidos.spec.ts
E2E_BASE_URL=http://localhost:5173 npx playwright test
```

Expected: `pedidos.spec.ts` en verde; después la suite completa en serie (`workers: 1`) con `stock`, `asignar`, `clientes` y `taller` en verde (`formulario.spec.ts` depende de su precondición de datos, como en 4a). Si `pedidos.spec.ts` falla en la limpieza, sus `expect.soft` nombran la ruta que quedó: se anota en la limpieza de la VDC. Respetar el límite de 5 logins por minuto: espaciar las dos ejecuciones.

**U5. Capturas del JavaFX** (el usuario, con Claude manejando los scripts de `Apuntes/herramientas/paridad-capturas/` sin pulsar nada que escriba). Worktree recreado desde el raíz, que se queda en `main`:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git worktree add ../_ref-hotfix-0163 hotfix/0.16.3
cp gestion-reparaciones-cliente/src/main/resources/config.properties ../_ref-hotfix-0163/gestion-reparaciones-cliente/src/main/resources/config.properties
```

Editar esa copia para que apunte a producción (como en 4a; el fichero está ignorado por git), y lanzar:

```bash
cd /c/Users/dev/Documents/_ref-hotfix-0163/gestion-reparaciones-cliente
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q javafx:run
```

Comprobar con `Get-NetTCPConnection -OwningProcess <pid>` que solo conecta con producción. Recorrer `CAPTURAS-4b.md` una a una; los pedidos de prueba en cada estado los crea el usuario desde el JavaFX (las escrituras nunca las pulsa el script) y apunta sus ids en "Datos creados". Al terminar: `git worktree remove ../_ref-hotfix-0163` desde el raíz.

**U6. Capturas de la web y comparación lado a lado** (antes del merge de la web). Con la web de la rama en local contra producción, tomar las mismas situaciones con el prefijo `web-` en `Apuntes/paridad-capturas/almacen/` y anotar pareja a pareja en `COMPARACION-4b.md` (formato de `COMPARACION-4a.md`): diferencia deliberada confirmada, calco, no comparable por datos o diferencia nueva. **Cada diferencia nueva se decide con el usuario**, no sobre la marcha; las que se corrijan van a `feature/web-pedidos` con su test y su commit, y las aceptadas a la ficha ("Decididas durante la ejecución y la comparación de capturas").

**U7. Ficha marcada** contra las capturas y los tests; commit en la rama:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git add docs/paridad/pedidos.md
git commit -m "docs(web): ficha de pedidos marcada tras comparar capturas"
git push origin feature/web-pedidos
```

**U8. Merge de la web en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-pedidos
npm run check && npm run build
git push origin main
```

**U9. Despliegue de la web en la VDC** (lo ejecuta el usuario):

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-web pull && git -C gestion-reparaciones-web log --oneline -1
docker compose up -d --build nginx
exit
```

Verificación del bundle desde el PC: el `index-*.js` que sirve producción es el del build local de U8.

```bash
set -a; . ~/.env.e2e; set +a
curl -s "$E2E_BASE_URL/" | grep -o 'assets/index-[^"]*\.js'
grep -o 'assets/index-[^"]*\.js' /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/dist/index.html
```

Expected: el mismo nombre en las dos líneas. Si difiere con commits nuevos, la variante `--no-cache` de §P8 de la guía.

**U10. Tag `v0.7.0`** (con OK): fijar antes la fecha de la entrada del CHANGELOG (commit `docs(web): fecha de la 0.7.0` en `main` y push) y después:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git tag -a v0.7.0 -m "v0.7.0: pedidos, formularios de pedido y campana"
git push origin v0.7.0
```

**U11. Gitlinks en el raíz** (con OK para el push):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-servidor gestion-reparaciones-web docs/superpowers/plans/<este plan>.md
git commit -m "chore: gitlinks servidor y web tras el sub-proyecto 4b (web v0.7.0) y cierre del plan"
git push origin main
```

Antes, completar en este plan un "### Cierre final" con el esquema del 4a (`2026-09-24-web-almacen-stock.md:3863-3869`): merges, despliegues, smoke, comparación, tag y suites finales.

**U12. `Apuntes/plan-futuro.md`, §9** (privado, sin commit): marcar `[x]` la casilla **4b** con el mismo nivel de detalle que la del 4a (commits de `main`, tag, tests, despliegue, smoke, capturas comparadas), y añadir debajo:

- `[ ] Limpieza en la VDC del 4b (se suma a las anteriores)`: los pedidos de componentes y de otros creados para las capturas (ids en `CAPTURAS-4b.md`, "Datos creados"), las unidades de stock que sumaron sus recepciones (revertir o corregir el SKU), el proveedor USD si se le crearon pedidos, lo que el smoke no pudiera limpiar (sus `expect.soft` nombran la ruta) y los logs `CREAR_PEDIDO`, `EDITAR_PEDIDO`, `BORRAR_PEDIDO`, `*_OTRO` y de proveedor del smoke.
- `[ ] Backlog 4b → web / servidor`: lo triado como backlog en las revisiones de las tareas y en la revisión final.

**U13. Memoria** (Claude, con OK): actualizar `project_migracion_web_programa.md` (4b cerrado, web v0.7.0, siguiente 4c) y su línea en `MEMORY.md`.

---



## Trazabilidad: reglas del inventario de Pedidos → test

| Regla (inventario §) | Test |
|---|---|
| §1 título "Pedidos", toggle Componentes \| Otros por rutas, Componentes por defecto, no deseleccionable | `PedidosPage.test.tsx` "título Pedidos y toggle Componentes \| Otros por rutas; Componentes marcado al entrar en /stock/pedidos" |
| §1 barra de filtros completa y "Nuevo pedido" / "Nuevo otro pedido" según el toggle, solo SUPERTECNICO | `PedidosPage.test.tsx` "barra de filtros y botón Nuevo… según el toggle; ADMIN y TECNICO sin botón" |
| §1 placeholders "Sin pedidos" y "Sin otros pedidos" | `columnas.test.tsx` (textos de vacío exportados), `PedidosPage.test.tsx` "tabla vacía por los filtros pinta Sin pedidos / Sin otros pedidos" |
| §1 "Actualizado HH:mm" recarga la tabla visible (P7) | `PedidosPage.test.tsx` "Actualizado recarga la tabla del toggle visible" |
| §2 "En Camino" de Stock → Pedidos con tres estados, buscador, primera fila seleccionada, URL limpia, siempre Componentes | `filtros.test.ts` "filtrosDesdeStock lee estados y buscar; null sin parámetros", `PedidosPage.test.tsx` "llegada con ?estados&buscar marca los estados, rellena el buscador, selecciona la primera fila y limpia la URL" |
| §2 enlace Componente → Stock actual con filtros de vuelta, selección y scroll | `columnas.test.tsx` "el enlace Componente llama a onComponente con el pedido", `PedidosPage.test.tsx` "el enlace Componente navega a /stock?componente=<idCom>", `filtros.test.ts` (stock) "desmarca OK, Bajo y Sin stock, conserva Desactivado…", `StockPage.test.tsx` "?componente=<id> al llegar desde Pedidos: …", "?componente= conserva \"Desactivado\" marcado (calco)…", "?componente= de un componente que no está en la lista…" |
| §2 "Pedir" de Stock actual abre el formulario con el componente (P1) | `StockPage.test.tsx` "\"Pedir\" del menú abre \"Nuevo pedido\" en el sitio con ese componente, sin navegar (4b, P1)", `formularioPedido.test.ts` "abrirNuevoPedido guarda la precarga; cerrar vuelve a null" |
| §2 campana: "→ Ir a pedidos" sin filtros | `PanelNotificaciones.test.tsx` "\"→ Ir a pedidos\" cierra el panel y navega a /stock/pedidos desde las dos pestañas" (4a) |
| §2 campana: "Pedir" de una alerta y "Pedir todas las piezas" (sin alertas no hace nada) | `PanelNotificaciones.test.tsx` "\"Pedir\" de una alerta cierra el panel…", "\"Pedir todas las piezas\" cierra el panel y abre… en el orden de la campana", "\"Pedir todas las piezas\" sin alertas no hace nada…" |
| §2 campana: "Pedir piezas" relee PENDIENTE, vacías no hace nada, error avisa | `api.test.tsx` "pedirPendientes relee solo las PENDIENTE…", `PanelNotificaciones.test.tsx` "\"Pedir piezas\" relee las PENDIENTE…", "…con las dos listas PENDIENTE vacías no hace nada…", "…si falla la relectura avisa…" |
| §2 título de ventana del formulario → título interno siempre "Nuevo pedido" | `NuevoPedidoDialog.test.tsx` "título Nuevo pedido en los tres modos de precarga" |
| §4 columnas, `dd/MM/yy HH:mm` en Madrid, sin Div. | `columnas.test.tsx` "siete columnas de componentes con sus cabeceras; fecha dd/MM/yy HH:mm en hora de Madrid; sin Div." |
| §4 Cant. según estado | `reglas.test.ts` "textoCantidad: parcial recibida/cantidad o cantidad; recibido la recibida o la cantidad; resto la cantidad" |
| §4 P.Unit. con símbolo y "!" en recibido a precio 0 | `reglas.test.ts` "marcaPrecioCero solo en recibido con precio 0", `columnas.test.tsx` "P.Unit. 12,50 € / $ / código y ! ámbar en recibido a precio 0" |
| §4 EUR = unidades × precioEur, "!" con total 0 | `reglas.test.ts` "unidadesFila y totalFila usan la recibida en recibido", "marcaTotalCero…", `columnas.test.tsx` "EUR con coma decimal y ! con total 0 en recibido" |
| §4 tabla de otros con Concepto sin enlace | `columnas.test.tsx` "columnas de otros: Concepto como texto, mismas reglas" |
| §5 barra izquierda por estado y opacidad de cancelado | `columnas.test.tsx` "claseFilaPedido: barra por estado, en camino urgente naranja y normal sin barra, cancelado atenuado" |
| §5 badges por estado, texto tal cual, "⚠" solo en camino/parcial urgente | `reglas.test.ts` "llevaAviso solo urgente en en_camino o parcial", `columnas.test.tsx` "BadgeEstadoPedido: colores por estado, en_camino con guion bajo y ⚠ a la derecha" |
| §6 filtro Estado con cinco chips sin marcar, texto del botón | `reglas.test.ts` "chipDeEstado y estadoDeChip son inversos", `PedidosPage.test.tsx` "filtro Estado: cinco casillas sin marcar, texto del botón, filtra" |
| §6 filtro Proveedor solo activos, por nombre, "N proveedores" | `PedidosPage.test.tsx` "filtro Proveedor solo con activos y N proveedores", `filtros.test.ts` "aplicarFiltrosPedidos filtra por nombre de proveedor" |
| §6 buscador sobre componente o concepto; fechas inclusivas en Madrid; AND | `filtros.test.ts` "buscador contiene sin mayúsculas sobre nombrePedido", "fechas inclusivas sobre la fecha local de Madrid; fecha nula pasa", "los cuatro filtros se combinan con Y" |
| §6 cancelados al final, estable | `filtros.test.ts` "ordenarCanceladosAlFinal es estable" |
| §6 filtros compartidos por los dos toggles; "Limpiar filtros" | `PedidosPage.test.tsx` "los filtros sobreviven al cambio de toggle y de sección", "Limpiar filtros vacía los cuatro sin tocar toggle ni selección" |
| §7 menú por estado con textos y separadores; cancelado sin menú; solo SUPERTECNICO | `reglas.test.ts` "entradasMenu por estado con textos exactos y separadores; [] en cancelado", `PedidosPage.test.tsx` "menú del supertécnico por estado; ADMIN y TECNICO sin menú" |
| §7 acciones sin diálogo (confirmar, confirmar recibido, cerrar sin resto) | `api.test.ts` (pedidos) "useTransicionPedido manda {updatedAt} a confirmar, confirmar-recibido y confirmar-alterado", `PedidosPage.test.tsx` "Confirmar pedido, Confirmar recibido y Cerrar sin resto escriben al pulsar y recargan" |
| §7.1 Recepción parcial: textos, validación, PATCH | `CantidadDialog.test.tsx` "Recepción parcial: subtítulo, etiqueta, campo vacío; Cantidad no válida. y rango", `reglas.test.ts` "validarParcial", `api.test.ts` "confirmar-parcial manda {cantidadRecibida, updatedAt}" |
| §7.1 Recibir unidades: cabecera P7, precarga restante, validación | `CantidadDialog.test.tsx` "Recibir unidades: dos líneas de cabecera, precarga el restante y valida exceso", `reglas.test.ts` "validarResto", "restante" |
| §7.1 Cancelar, Borrar y Revertir: títulos, textos y botones | `confirmaciones.test.ts` "cancelar, borrar y revertir con sus textos; revertir de otros con una línea", `PedidosPage.test.tsx` "Cancelar, Borrar y Revertir piden confirmación con sus textos" |
| §7.1 409 genérico y 409 de desrecibir con el mensaje del servidor | `PedidosPage.test.tsx` "409 en una transición avisa Este pedido fue modificado… y recarga", "409 al revertir muestra el mensaje del servidor" |
| §7.1 recargas tras cada acción (compras, componentes, campana) | `api.test.ts` (pedidos) "useRecargaPedidos invalida compras, componentes y notificaciones" |
| §9 guards de estado en el servidor (todas las transiciones, las dos entidades) | `CompraComponenteDAOTransicionesTest` (matriz de las diez transiciones: permitida escribe, prohibida 409 sin tocar stock), `CompraOtroDAOTransicionesTest` |
| §9 rangos de parcial y resto; validación de alta y edición; activos | `CompraControllerTest` / `CompraOtroControllerTest` (validaciones de `ValidacionPedidos`) "parcial fuera de rango 422", "resto con exceso 422", "alta con cantidad 0, precio negativo, concepto vacío, divisa desconocida, componente o proveedor inactivo 422 sin log", "PUT de recibido con otra cantidad 422" |
| §9 `precioEur` en servidor (P3) | `ConversionEurTest` "EUR devuelve el precio sin llamar", "USD divide y redondea HALF_UP a 2 decimales", "fallo del tipo de cambio 503 con mensaje" |
| §9 divisa de la línea = la del proveedor (lotes) | `CompraLoteControllerTest` "la divisa de cada línea es la del proveedor" |
| §10 formulario Nuevo pedido: columnas, placeholders, botones | `NuevoPedidoDialog.test.tsx` "tabla de líneas con sus columnas, placeholder Añade al menos una línea y botones" |
| §10 autocompletar "contiene", activos incluidos slaves, Enter elige el primero | `NuevoPedidoDialog.test.tsx` "el autocompletar ofrece solo activos (con slaves) y filtra contiene" |
| §10 símbolo `$`/`€` en P.Unit. y Total EUR con tasa; "—" sin tasa | `conversion.test.ts` "aEuros divide por la tasa", "totalLinea null si falta algo", `NuevoPedidoDialog.test.tsx` "símbolo $ con proveedor USD y total convertido; — mientras carga o falla la tasa" |
| §10 añadir, quitar sin confirmar y cambiar línea | `lineas.test.ts` "añadir, quitar y cambiar campo", `NuevoPedidoDialog.test.tsx` "+ Añadir línea añade y selecciona; la papelera quita sin confirmar" |
| §10 validación en orden con los cinco textos | `lineas.test.ts` "validarLineasCompra para en el primer error con los textos exactos", `NuevoPedidoDialog.test.tsx` "el error se pinta inline y el formulario sigue abierto" |
| §10 precargas: componente, alertas y solicitudes (agrupación, cantidad = nº, inactivos omitidos y avisados) | `lineas.test.ts` "precargarComponentes: una línea por id, vacía si no está activo", "precargarSolicitudes agrupa por idCom, urgentes primero, cantidad = número de solicitudes", "omite y cuenta los desactivados", "avisoOmitidas", `NuevoPedidoDialog.test.tsx` "precarga de solicitudes con aviso de omitidas" |
| §10 guardado en lote con clave, cuerpo y solicitudes que quedan; 422/409 inline; reintento con la misma clave | `lineas.test.ts` "cuerpoLoteCompras solo lleva las solicitudes con línea", `api.test.ts` (pedidos) "useGuardarLoteCompras manda Idempotency-Key", `NuevoPedidoDialog.test.tsx` "guarda con Idempotency-Key, cierra y recarga", "422 y 409 inline con el formulario abierto", "reintento tras fallo con la misma clave", `CompraLoteControllerTest` "400 sin clave", "422 por línea", "atómico ante fallo en la segunda línea", "misma clave y misma petición devuelve la respuesta guardada sin insertar", "marca las solicitudes GESTIONADA en la transacción", "solicitud sin línea 422", "logs una sola vez" |
| §10 "Cancelar" cierra sin preguntar | `NuevoPedidoDialog.test.tsx` "Cancelar cierra sin preguntar aunque haya líneas" |
| §11 Nuevo otro pedido: Concepto, validación del concepto primero, lote de otros | `lineas.test.ts` "validarLineasOtro valida el concepto antes que el resto", "cuerpoLoteOtros recorta el concepto", `NuevoOtroPedidoDialog.test.tsx` "columna Concepto con Escribe concepto...", "guarda el lote de otros con clave y recarga", `CompraLoteControllerTest` (casos de otros) |
| §12 Editar pedido: campos, precarga de la pedida (P2), proveedor inactivo vacío, divisa EUR/USD, total con etiqueta de tasa, validación, 409 y 422 inline | `EditarPedidoDialog.test.tsx` "precarga la cantidad pedida también en recibido", "proveedor inactivo deja el combo vacío", "Obteniendo tasa…, Error al obtener tasa y etiqueta (1 USD = x,xxxx €)", "validación en orden", "409 inline con El pedido fue modificado…", "PUT con precioEur null", `conversion.test.ts` "etiquetaTasa con dos espacios y 1/tasa a 4 decimales", `CompraControllerTest` / `CompraOtroControllerTest` (validaciones de `ValidacionPedidos`) "PUT de recibido con otra cantidad 422" |
| §13 Editar otro pedido: Concepto primero, sin Urgente (conserva el valor) | `EditarOtroPedidoDialog.test.tsx` "Concepto arriba y validado primero; sin Urgente, conserva esUrgente en el PUT" |
| §14 conversión de divisa: EUR sin llamada, caché por divisa, tasa dividida | `tasa.test.ts` "EUR = 1 sin llamada; una consulta por divisa con staleTime 1 h", `conversion.test.ts`, `ConversionEurTest` |
| §16 refresco 60/5 s de la tabla visible, congelado con menú, diálogo o formulario; selección tras refresco | `PedidosPage.test.tsx` "solo sondea la tabla visible", "congela el sondeo con menú, diálogo o formulario abiertos", "la selección sobrevive al refresco", `StockPage.test.tsx` "con el formulario de pedido abierto el sondeo se congela; al cerrarlo se reanuda" |
| §17 errores: 409 de acción, de desrecibir, de edición; "Error al guardar: …" | `PedidosPage.test.tsx` (409 de acción y de revertir), `EditarPedidoDialog.test.tsx` "otro error: Error al guardar: …", `NuevoPedidoDialog.test.tsx` "otro error: Error al guardar: …" |
| §18 CSV de componentes y de otros con cabeceras, "Sí"/"No", coma decimal, filas filtradas | `columnas.test.tsx` "CABECERAS_CSV_PEDIDOS y filaCsvPedido", "CABECERAS_CSV_OTROS sin Urgente y filaCsvOtro", `PedidosPage.test.tsx` "Descargar CSV exporta la tabla visible filtrada con su nombre" |
| §19 roles: pestaña para los tres; "Nuevo…", menú, formularios y campana solo SUPERTECNICO | `PedidosPage.test.tsx` "ADMIN y TECNICO ven las tablas sin botones ni menú", `Campana.test.tsx` (4a: campana solo SUPERTECNICO), `CompraLoteControllerTest` "los lotes exigen SUPERTECNICO" |
| §22 contrato: rutas y esquemas nuevos, nullables | `OpenApiContractTest` "lotes de compras y de otros, esquemas LoteCompras* y nullables de cantidadRecibida y fechaLlegada" |

---

## Autorrevisión del plan

Contrastado contra la spec §1-§12 y el código real por lectura; **nada compilado ni ejecutado**. Cada bloque lo redactó un subagente distinto sobre un documento de interfaces común; estos son los puntos cruzados que se reconciliaron al ensamblar (las notas originales de cada bloque están al final, en "Desviaciones respecto al reparto inicial de interfaces"):

1. **503 del tipo de cambio al guardar (spec §8).** El clasificador de la web convertía cualquier 5xx en "sin conexión" y el mensaje del servidor no llegaba a la línea de error. Resuelto en T7 (Steps 13-16): un 503 **con mensaje** es `ReglaNegocioError` y se pinta inline (T15 lo testea; T17 lo hereda por `mensajeErrorGuardado`); un 503 sin cuerpo JSON (vacío, texto plano o el HTML de nginx) sigue siendo sin conexión, y `client.ts` solo enciende el banner si el error clasificado es `ConexionError`. La spec §8 queda como estaba.
2. **`useTasas` silenciado.** T9 ya lleva `meta: { silenciarError: true }` en la consulta de la tasa, así que un fallo no abre el diálogo global además de "Error al obtener tasa" / "—" (lo pedía T15).
3. **"Editar" del menú.** T13 crea `editando` y congela el sondeo con él; T17 solo pinta los dos editores con el type guard `esCompra` y no añade ningún efecto.
4. **Rutas de otros.** El contrato real usa `{id}` en `/api/compras-otros/{id}/…`; T10 lo respeta (`useEditarOtro` recibe `idCompraOtro` y lo pasa como `path: { id }`).
5. **`solicitudes` del lote.** El servidor la publica obligatoria (T4, desviación 2) y la web la manda siempre (`cuerpoLoteCompras` devuelve `{ urgentes: [], preventivas: [] }` sin origen): coherentes.
6. **Recarga de la campana.** La web invalida `['notificaciones']` entero (contador, solicitudes marcadas y alertas), no solo `['notificaciones','componentes']` como dice la spec §7: ampliación inocua, anotada en la ficha.
7. **Nombres de tests del servidor en la trazabilidad** alineados con los que crea el bloque servidor (`*TransicionesTest`, `CompraControllerTest`/`CompraOtroControllerTest` para las validaciones, `CompraLoteControllerTest` para los dos lotes).
8. **Literales del semáforo de Stock** en `filtrosDesdePedidos` (T19): `'OK' | 'Bajo' | 'Sin stock' | 'Desactivado'`, los reales de `shared/lib/semaforoStock.ts`.
9. **`pedirDesplazamiento`** es un contador, no un índice (T13 y T19 lo usan igual): `DataTable` localiza la fila por `seleccionada`; la llegada desde Stock y la vuelta a Stock solo se consumen cuando la consulta tiene datos.
10. **Recuentos:** servidor 286 → 408 (T1 +44, T2 +25, T3 +14, T4 +22, T5 +16, T6 +1); web 1119 → 1322 (T7 +10, T8 +4, T9 +32, T10 +18, T11 +10, T12 +12, T13 +26, T14 +20, T15 +26, T16 +7, T17 +25, T18 +7, T19 +6). Se comprueban en ejecución; son orientativos.
11. **Orden del cierre (T21):** capturas de la web comparadas con las del JavaFX **antes del merge** de la rama web (Global Constraints, lección del 4a), y toma de las capturas del JavaFX de Pedidos como paso del usuario antes de esa comparación.
12. **Diferencias nuevas detectadas al redactar, para la ficha y la spec §10:** "Pedir todas las piezas" sin alertas no hace nada (calco); el panel de la campana se cierra al pedir (el JavaFX lo deja abierto: diferencia inocua); "Pedir piezas" se deshabilita mientras relee y avisa si la relectura falla (calco); una línea sin proveedor se calcula como EUR; en los formularios de alta Enter no confirma (en los editores sí); las solicitudes del lote se filtran por `idCom` literal (el servidor resuelve al master); un proveedor desactivado después del pedido hace que su `PUT` dé 422 aunque no se toque el proveedor (spec §4.2).
13. **Puntos a vigilar en ejecución** (marcados en cada tarea): el `verify(jdbc, never()).update(eq(SQL), any(), any())` de T1 compilaba contra la sobrecarga `update(String, Object[], int[])` y no comprobaba nada; corregido en la revisión previa con `any(Object[].class)` (resuelve a `update(String, Object...)`); `react-hooks/set-state-in-effect` en la llegada desde Stock (T13, T19); los nombres de esquema que produzca springdoc para los records anidados (T6, T7); los nombres accesibles que el smoke (T20) supone de T13 y T15-T17.

**Revisión previa antes de la Task 1** (lección del 4a): tres subagentes aplican el código del plan en copias (servidor T1-T6; web T7-T13; web T14-T19), ejecutan las suites y devuelven los desajustes; se corrige el plan antes de despachar nada.

## Desviaciones respecto al reparto inicial de interfaces

Cada bloque del plan lo redactó un subagente contra el código real a partir de un documento de interfaces común (fuera del repo). Lo que no cuadró con el código se adaptó y se anotó aquí; la "Autorrevisión del plan" de arriba dice cómo queda cada punto cruzado.

### Bloque servidor (T1-T6)

1. **Los dos lotes viven en un controlador nuevo, `controller/CompraLoteController`** (`@RequestMapping("/api")`, `@PostMapping("/compras/lote")` y `@PostMapping("/compras-otros/lote")`), no dentro de `CompraController`/`CompraOtroController`. Rutas idénticas a las previstas; evita meter cinco dependencias más en dos constructores que ya tocan las Tasks 2 y 3 y en los tests que los construyen. Nombres de operación `guardarLoteCompras`/`guardarLoteOtros` (no chocan con el `guardarLote` de asignaciones, que springdoc renombraría a `guardarLote_1`). La web solo usa rutas y esquemas: sin efecto en T7/T10.
2. **`LoteComprasPeticion.solicitudes` se publica como required y NO nullable** (el documento de interfaces del reparto (fuera del repo) decía `| null`). Es un `$ref` y swagger-core con OAS 3.0 no garantiza `nullable` junto a un `$ref` (ningún `$ref` del contrato actual lo lleva). El servidor sigue tolerando `null` (se trata como vacía); la web ya lo manda siempre (`CuerpoLoteCompras.solicitudes` no es opcional en T10). En el tipo generado será `LoteComprasSolicitudes`, no `| null`.
3. **P2 y los rangos de parcial/resto (422) se comprueban en el controlador con `getById`** (público, ya existe en las dos DAO y trae `estado`, `cantidad` y `cantidadRecibida`), no extendiendo `getCompraRow`. Así todos los 422 están en `ValidacionPedidos` y las DAO solo lanzan 409. La carrera "pasa a recibido entre la lectura y el UPDATE" la cubre el `WHERE` de `editar`: `… AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))` → 409 "El pedido no se puede editar en su estado actual". Consecuencia de orden: un 422 de rango/P2 sale antes que el 409 de `updatedAt` o de estado (la fila que se mide es la fresca de BD).
4. **`desrecibir` de componentes cambia el orden interno**: primero el `UPDATE … AND ESTADO='recibido'` del pedido (409 "El pedido ya no está recibido"), después la lectura de stock y su 409 de siempre (que deshace el `UPDATE` anterior por ser `@Transactional`) y por último la resta. Hoy se miraba el stock primero; así, con el estado equivocado gana el mensaje de estado y no uno de stock engañoso.
5. **La tasa se resuelve dentro de la `escritura` de `idempotencia.ejecutar`, antes de llamar al servicio** (igual que `conModeloDePulido` en `AsignacionController:60`): sigue fuera de la transacción, un reintento con la clave ya hecha no vuelve a consultar Frankfurter y un 503 libera la clave. Firmas del servicio: `guardarCompras(List<LineaCompra>, List<Integer> urgentes, List<Integer> preventivas)` y `guardarOtros(List<LineaOtro>)` (el documento de interfaces del reparto (fuera del repo) no fijaba la firma). `CompraLoteService` recibe `CompraOtroDAO` desde la Task 4 para no cambiar su constructor en la 5.
6. **Lecturas de DAO nuevas que no estaban previstas** (no había forma de obtener estos datos): `ComponenteDAO.Basico(idCom, idMaster, tipo, activo)` + `getBasico(int)`, `ProveedorDAO.getById(int)`, `ReparacionComponenteDAO.getIdComDeSolicitud(int idRc)` y `SolicitudStockDAO.getIdCom(int idSol)`. Firmas reales comprobadas para el marcado: `ReparacionComponenteDAO.actualizarEstadoSolicitud(int, String)` y `SolicitudStockDAO.actualizarEstado(int, String)` (coinciden con el documento de interfaces del reparto (fuera del repo)).
7. **Divisa de los POST/PUT sueltos**: se valida tras `trim().toUpperCase()` y se guarda la normalizada (criterio de `ProveedorController` en 4a). El JavaFX manda `EUR`/`USD` en mayúsculas: sin cambio para él. En los lotes, divisa nula o en blanco del proveedor → `EUR` (DEFAULT de la columna).
8. **PUT de un pedido cuyo proveedor se desactivó después** → 422 "El proveedor no está activo." aunque no se cambie el proveedor (lo pide la spec §4.2 tal cual). Anotarlo en la ficha de paridad: el JavaFX no lo bloqueaba.
9. **El concepto no se recorta** (ni en los sueltos ni en el lote): se valida "en blanco tras trim" pero se guarda tal cual llega, como hoy.
10. **Los records de petición de `CompraController` y `CompraOtroController` pasan de `private` a package-private** (los tests los construyen, como hizo 4a con `ProveedorController`); springdoc los publica con los mismos nombres. `TipoCambioDAO` gana un constructor package-private `(JdbcTemplate, HttpClient)` para los tests y `@Autowired` en el público (con dos constructores Spring no elegiría).
11. **`Idempotency-Key` se declara `required = false` en el contrato y se exige en el código** (400), igual que `/api/asignaciones/lote`; el tipo generado será `header?: { 'Idempotency-Key'?: string }`. `OpenApiContractTest.lasEscriturasDelFormularioAdmitenClaveDeIdempotencia` gana las dos rutas (Tasks 4 y 5): sin eso fallaría, porque exige que ninguna otra operación declare la cabecera.
12. **Validar antes de `ejecutar` (como asignaciones)** implica que un reintento con una clave ya hecha, después de que cambie el catálogo (p. ej. componente desactivado entre medias), da 422 en vez de la respuesta guardada. Mismo comportamiento que `/api/asignaciones/lote`.
13. **Recuento**: `mvn -q` no imprime el resumen de surefire, así que cada tarea suma `Tests run` de `target/surefire-reports/*.txt`. Acumulado: T1 330 (+44), T2 355 (+25), T3 369 (+14), T4 391 (+22), T5 407 (+16), T6 408 (+1). En los tests de controlador de los lotes, los 422 de "desactivado", cantidad y precio van juntos en un test de orden (`losErroresDeUnaLineaSalenEnElOrdenDeLaSpec`), no uno por mensaje.

### Bloque web: cimientos y página (T7-T13)

1. **Rutas de "otros" con `{id}`.** El contrato real nombra el parámetro de ruta de `/api/compras-otros/{id}/…` `id` (`schema.d.ts:119`, `:1111-1220`), no `idCompraOtro`. `useEditarOtro` conserva la variable `{ idCompraOtro }` de la interfaz y la pasa como `path: { id: idCompraOtro }`.
2. **`useCompras`/`useComprasOtros` con `habilitada?: boolean` (opcional, por defecto `true`).** Sin ella, la página (que llama a los dos hooks: las reglas de hooks no permiten llamarlos según el toggle) pediría también la tabla oculta al montar; la spec §5 dice que solo se consulta y sondea la visible. Superconjunto compatible de la firma: quien llame `useCompras({ activo })` sigue igual.
3. **`MenuPedido` con `onInteraccion?: (abierto: boolean) => void`.** Mismo aviso de montar/desmontar que `MenuComponente` (`stock/MenuComponente.tsx:13-24`); sin él, el menú abierto no congela el sondeo (§7). Opcional.
4. **Botón "Confirmar" también en "Recibir unidades".** La spec lo fija solo para el parcial; el resto usa el mismo `DialogoAlmacen` con el mismo texto de acción. Y una recibida nula en la cabecera de "Recibir unidades" se pinta `0` (el JavaFX concatenaría `null`; en `parcial` no debería darse).
5. **`key` en las dos rutas.** `<PedidosPage key="componentes" tipo="componentes" />` / `key="otros"`: con el mismo tipo de elemento en la misma posición, React Router reutilizaría la instancia al cambiar de toggle y arrastraría el estado local (diálogos, `editando`, refs de la llegada). La interfaz decía el elemento sin `key`.
6. **Franja izquierda de 8 px en el `<tr>`, no con `box-shadow` ni pseudo-elemento.** El encargo supone que el fix de paridad del 4a movió la barra a la primera celda; el código real no: `claseFilaStock` (`stock/columnas.tsx:20-27`), Proveedores, Clientes, Historial, Pendientes y Asignaciones siguen con `border-l-8 border-l-<token>` en el `<tr>`, y el fix del 4a (`a1c7079`) solo cambió que el cuerpo quite el borde inferior de la última fila (`stock/columnas.test.tsx:71-79`). `claseFilaPedido` sigue ese patrón.
7. **`DataTable` y `ConfirmDialog` se tocan en la Task 13.** `DataTable`: si `menuFila` devuelve `null` la fila no lleva `ContextMenu` (sin esto, un cancelado abría un menú vacío; el JavaFX no lo muestra). `ConfirmDialog`: `whitespace-pre-line` en la descripción para las tres líneas de "Revertir a En camino" de componentes. Los dos cambios son compatibles con todos sus usos actuales y llevan test.
8. **Recarga de la campana con `['notificaciones']` entero.** La spec §7 cita `['notificaciones','componentes']`; la interfaz de la Task 10 dice `['notificaciones']`, que es lo que se implementa (cubre también las solicitudes que el lote marca GESTIONADA y el contador).
9. **`BadgeEstadoPedido` sin tipo de retorno `JSX.Element` explícito** (React 19 ya no expone el namespace global `JSX`; se infiere). Mismo contrato de uso.
10. **503 de la tasa (resuelto en T7 Steps 13-16):** un 503 con el JSON `{message}` del servidor ("No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.") llega a los formularios como `ReglaNegocioError` y se pinta inline, sin banner (spec §8); un 503 con texto plano o HTML (nginx) sigue siendo `ConexionError` con el banner. `useTasa` lo trata como `error: true` (correcto para la vista previa).
11. **Nombres de esquema del lote.** Esta parte no usa los nombres `LoteCompras*` directamente (los cuerpos van tipados por la ruta de openapi-fetch); si el generador produce otros, solo cambia la comprobación del Step 2 de la Task 7 y el `grep` del Step 3 de la Task 10.

### Bloque web: formularios (T14-T17)

1. **Exports extra en `lineas.ts`** (T14): `LineaBase`, `MSG_SIN_LINEAS`, `precargaInicial`, `preseleccionDe`, `cambiarLinea`, `quitarLinea`, `siguienteId`. El "reductor de líneas" de la spec §5 queda como helpers puros sobre `useState` (no `useReducer`). Las firmas de el documento de interfaces del reparto (fuera del repo) se mantienen exactas (`lineaCompraVacia(id, idCom?)` se implementa con valor por defecto `= null`, equivalente).
2. **Ficheros extra:** `formulario/datosPrueba.ts` (fixtures sintéticas compartidas por los tests de T14-T17; nombre elegido para no chocar con un posible `test/fabrica.ts` de T9-T13), `formulario/DialogoLineas.tsx` (armazón común de los dos formularios de alta, T15, reutilizado en T16), `formulario/errores.ts` (+test; mapeo de errores de guardado común a los cuatro formularios), `formulario/edicion.ts` (+test; validación y Total EUR de los editores), `src/app/shell/AppLayout.test.tsx` (no existía) y `pedidos/PedidosPage.editar.test.tsx` (para no reescribir el test de T13).
3. **`DialogoAlmacen` gana `ancho?: 360 | 520`** (no admitía ancho): con 520 el título pasa de 20 a 24 px, el `vista-titulo` de `FormularioCompraEditar.fxml`. Por defecto no cambia nada para Stock y Proveedores.
4. **Hook de componentes:** `useComponentesStock({ activo: false })` de `../../stock/api` (clave `['componentes','gestionados']`); el de `notificaciones/api.ts` está en `taller` (lint). Proveedores: `useProveedoresComponentes({ activo: false })` + filtro de activos en cliente.
5. **Cableado en `PedidosPage`:** el `editando ? editando : null` de la tarea no compila (props tipadas `CompraComponente` / `CompraOtro`); se usa el type guard `esCompra(editando)` de T9. T13 ya congela el sondeo con `editando` (`hayModal`), así que T17 no añade ningún efecto.
6. **503 al guardar (spec §8 "al guardar, el mensaje del servidor inline"): resuelto en T7 (Steps 13-16).** Un 503 con el JSON `{message}` del servidor llega como `ReglaNegocioError` y se pinta inline por `mensajeErrorGuardado`, sin diálogo global ni banner; T15 lo testea ("503 del tipo de cambio al guardar…") y T17 lo hereda. No hay que tocar `clasificar` ni `client.ts` en estas tareas.
7. **`useTasas`/`useTasa` silenciados: ya presente en T9** (`meta: { silenciarError: true }`, `retry: false`), así que una tasa fallida no abre el diálogo global además del "—" / "Error al obtener tasa" (spec §8). No hay que añadir nada en T15/T17.
8. **Línea sin proveedor:** se calcula como EUR (tasa 1, símbolo "€"), calco de la tasa 1.0 por defecto de `LineaCompra`; el "—" de P7 aplica cuando la divisa del proveedor aún no tiene tasa o falló.
9. **Preselección de "+ Añadir línea":** solo en modo `componentes` con un único id activo. "Pedir todas las piezas" con exactamente una alerta se comporta como "Pedir" (el store no distingue los dos orígenes; en el JavaFX `initConComponentes` no preseleccionaba). Inocuo.
10. **Filtrado de solicitudes del lote por `idCom` literal**, sin resolver al master: si el usuario cambia la línea de un componente al master/slave del solicitado, esas solicitudes no viajan y siguen PENDIENTE (conservador; el servidor sí las aceptaría).
11. **Textos no numéricos:** en el alta, una cantidad no entera o un precio no numérico caen en "la cantidad debe ser mayor que 0." / "el precio no puede ser negativo." (en el JavaFX la celda los rechazaba al teclear y nunca llegaban a validarse).
12. **Maquetación de la tabla de líneas:** `<table>` propio sin el contenedor `overflow-x-auto` de `Table` (recortaría el popup de `CampoAutocompletar`); anchos en `colgroup` proporcionales (el "Nuevo otro pedido" suma 665 px para 644 útiles); el modal desplaza (`max-h` + `overflow-y-auto`). Sin Enter para confirmar en los formularios de alta (calco: no había atajos); en los editores Enter confirma (form de `DialogoAlmacen`, diferencia ya aceptada en 4a).
13. **La línea de error se borra al cambiar cualquier campo** (en el JavaFX era un `Alert` que se cerraba a mano).
14. **Tipos `CompraComponente`/`CompraOtro` con nullables:** las fixtures usan `cantidadRecibida: null` y `fechaLlegada: null`; dependen de que T6/T7 dejen esos campos `number | null` / `string | null` en `schema.d.ts`.

### Bloque web: campana, Stock, smoke y cierre (T18-T21)

1. **Literales de `EstadoStock`** (T19): son `'OK' | 'Bajo' | 'Sin stock' | 'Desactivado'` (`src/shared/lib/semaforoStock.ts:4`), no `'ok' | 'bajo' | 'sinStock'`. `filtrosDesdePedidos` conserva `'Desactivado'`.
2. **`pedirDesplazamiento` es un contador, no un índice** (T19): `DataTable.tsx:52` recibe "cada valor nuevo cuenta como una petición" y busca él mismo el índice de `seleccionada` en sus filas filtradas (`:263`). `StockPage` sube un contador con `useState`. Además la llegada se consume solo con `isSuccess`, porque una petición cuya fila aún no está se da por atendida sin desplazar (`:256-258`).
3. **La vuelta desde Pedidos selecciona solo si el componente está en la lista** (T19, calco de `navegarAComponente`): los filtros se aplican siempre con un id válido; la selección y el scroll, solo si el componente existe. Un `?componente=` no numérico solo limpia la URL.
4. **`react-hooks/set-state-in-effect`** (T19): el `setPeticionDesplazamiento` dentro del efecto lleva `eslint-disable-next-line` con motivo, patrón de los diálogos de 4a (`AjustarMinimoDialog.tsx:22`, `EditarStockDialog.tsx:20`); si la regla no lo marca, se quita el comentario.
5. **"Pedir todas las piezas" sin alertas no hace nada** (T18, calco de `MainController :276`, `if (alertasCriticas.isEmpty()) return;`): el documento de interfaces del reparto (fuera del repo) solo decía `alertas.map(...)`.
6. **Los botones de pedir cierran el panel** (T18) como dice la spec §6, pero el JavaFX no cierra la ventana de notificaciones al pedir (solo "Ver Stock Completo" hace `ventana.close()`, `MainController :279`). Va a la ficha como diferencia inocua y a `notificaciones.md`.
7. **"Pedir piezas" se deshabilita mientras relee y avisa si falla** (T18; `mostrarError` del JavaFX en `:350`): estado `pidiendo` en el panel y `useAlerta().mostrarError` salvo errores gestionados globalmente (sesión caducada o conexión, `errors.ts:83-85`).
8. **`textos.ts` se borra entero** (T18): solo contenía `TOOLTIP_ALMACEN`. También se ponen al día líneas de `docs/paridad/notificaciones.md` que el 4a dejó diciendo "deshabilitado" para "→ Ir a pedidos" y "Ver Stock Completo".
9. **Orden de los pasos de cierre** (T21): la comparación de capturas de la web va **antes** del merge de la web (U6-U8), como piden la spec §9 ("antes de `v0.7.0` (lección de 4a: antes del merge)") y las Global Constraints de la cabecera. El merge y el despliegue del servidor van primero (U2-U3), porque el smoke y las capturas necesitan los lotes en producción. Si el usuario prefiere el orden del encargo (merge de la web antes del smoke), U6-U7 pasan detrás de U9 y las correcciones de paridad irían en una rama `fix/web-pedidos-paridad`, como en 4a.
10. **Smoke** (T20): el proveedor de prueba se crea por API, no por la interfaz, para que el combo del formulario ya lo tenga al entrar en Pedidos; su nombre es `e2e-proveedor-<marca>` (no `E2E <timestamp>` como en `stock.spec.ts`). Se reutiliza `E2E_SKU_PRUEBA` sin variables nuevas. `DELETE /api/compras/{id}` y `/api/compras-otros/{id}` responden **200** (métodos `void` sin `@ResponseStatus`), no 204. El test depende de nombres accesibles que producen T13 y T15-T17 (listados en "Supuestos" de T20): el proveedor de la línea se localiza como el primer `button[role="combobox"]` del diálogo, que no depende del `aria-label` que elija T15.
11. **Versión** (T21): `npm version 0.7.0 --no-git-tag-version` actualiza `package.json` y `package-lock.json` a la vez; la versión visible sale solo de `package.json` (`vite.config.ts:8,14`), no hay otro sitio que tocar.
12. **Recuento de T18**: +7 (no +6): el caso de "Pedir todas las piezas" sin alertas sale de la desviación 5.

## Revisión previa (2026-09-25)

Tres subagentes aplicaron el código del plan, copiado literalmente de los Steps, en copias locales de los repos (servidor T1-T6; web T7-T13; web T14-T19), sin push, y ejecutaron las suites. Resultados: servidor **408** tests en verde con los recuentos del plan; web **1231** tras T13 y **1321** tras T19 (**1322** con el test nuevo de `client.test.ts`), lint, `tsc -b` y build limpios tras las correcciones.

Decisiones del usuario:

1. **503 de negocio:** solo es `ReglaNegocioError` con el JSON `{message}`; texto plano o HTML sigue siendo `ConexionError`, y `client.ts` solo enciende el banner con `ConexionError` (T7 Steps 13-16, +1 test). Los mocks `text('boom', 503)` existentes no se cambian.
2. **Spec §4.2:** "El componente no está activo." solo en el alta (POST y lotes); el PUT no lleva componente. Se mantiene el 422 del proveedor desactivado en el PUT (desviación 8 del servidor), anotado en la ficha de T21.
3. **Spec §7:** el congelado del refresco con el formulario abierto aplica a Stock actual y Pedidos; una vista del taller sigue sondeando debajo del modal (diferencia inocua en la ficha y en la spec §10).

Correcciones aplicadas: T1 `never()` con `any(Object[].class)` (antes era una aserción vacía); T2 Step 6 (fallos reales 11/15 y 7/10) y duplicado de la validación de divisa de `ProveedorController`; T4 referencias de `RegistroIdempotencia` y tasa por línea; T6 Step 4 `grep`; T7 líneas de `fechas.ts`, `client.ts` y su test; T11 `Array.from` (TS2488), typecheck en el Step 6 y mensaje del Step 2; comentarios y referencias de línea desfasados (`filtros.ts`, `columnas.tsx`, `fechaLocal`, `DialogoAlmacen`, `useErrorServidor`, `errors.ts`, `rutas.ts`); T15 18 tests (+26); T17 `within` en el test del cableado, `esCompra` en el import existente y Steps 10.2-10.3 simplificados; T19 "Files" y `[x]` en `stock.md`; desviaciones web 10 y formularios 6-7 marcadas como resueltas; acumulados, Autorrevisión 10 y 13; timeouts intermitentes en las Global Constraints; ficha de T21 (proveedor desactivado en el PUT, sondeo de las vistas del taller, clic derecho en cancelados).

## Ejecución y cierre (2026-09-25)

**Código terminado; pendiente de smoke, capturas y OK del usuario.** Las veinte tareas de código se ejecutaron con un implementador y una revisión por tarea (todas "Approved"), más una revisión final por repo con su ronda de arreglos y una re-revisión ("Ready to merge" en los dos). Nada está pusheado, mergeado ni etiquetado. El raíz sigue en `main`.

**Servidor**, rama `feature/web-pedidos` desde `main` `c042b5b`, head `5153e54` (READY TO MERGE):

- `ed31d0f` feat(compras): 409 de estado en todas las transiciones de pedidos y log de confirmar alterado (T1)
- `65cb5c2` feat(compras): 422 de alta, edicion y recepcion de pedidos con los textos del cliente y cantidad fija en recibido (T2)
- `c953845` fix(compras): el servidor calcula el importe en euros dividiendo por la tasa y responde 503 sin tipo de cambio (T3)
- `6aea242` feat(compras): lote de pedidos transaccional con clave de idempotencia, 422 por linea y solicitudes gestionadas (T4)
- `31f1507` feat(compras): lote de otros pedidos transaccional con clave de idempotencia y 422 por linea (T5)
- `68fd681` feat(contrato): nulos de pedidos, precioEur ignorado y lotes de pedidos en el contrato openapi (T6)
- `5153e54` fix(compras): 503 con tasa no válida sin cachear, precio infinito rechazado y tests del put de otros, del 503 y del alcance de p2 (arreglo tras la revisión final)

**Web**, rama `feature/web-pedidos` desde `main` `02cc928`, head `5377a1d` (versión `0.7.0`; código READY TO MERGE en `e39769b`):

- `b5ff469` chore(web): contrato con lotes de compras y nullables, tipos de pedidos, importes con coma, fecha de pedidos, 503 con json de mensaje como error de negocio sin banner, claves de idempotencia en shared y tokens (T7)
- `e085c10` feat(shared): store del formulario de pedido con sus modos de precarga (T8)
- `80783bb` feat(pedidos): reglas de cantidad, importes y menu por estado, filtros, stores, confirmaciones y tasa de cambio (T9)
- `ccae589` feat(pedidos): consultas de compras y otros, transiciones, edicion, lotes con clave de idempotencia y recargas (T10)
- `18467b0` feat(pedidos): columnas de componentes y otros, enlace al componente, marcas de precio cero, badge con aviso, clase de fila y csv (T11)
- `ce71a23` feat(pedidos): dialogos de recepcion parcial y recibir unidades con error inline, y menu contextual por estado (T12)
- `3764797` feat(pedidos): pestaña pedidos con toggle componentes y otros, filtros, menu de transiciones, dialogos, llegada desde stock, csv y refresco congelado (T13)
- `c5e7216` feat(pedidos): lineas del formulario de pedido: precargas, validacion, cuerpos de lote y conversion a euros (T14)
- `0ce94f1` feat(pedidos): formulario nuevo pedido como modal del shell, con lote idempotente, tasa por divisa y errores inline (T15)
- `f23e9b9` feat(pedidos): formulario nuevo otro pedido con concepto libre y lote idempotente (T16)
- `0d3cc80` feat(pedidos): editores de pedido de componentes y de otros con tasa dividida, cantidad pedida y 409 inline (T17)
- `2be0781` feat(campana): pedir, pedir todas las piezas y pedir piezas abren el formulario de pedido (T18)
- `73264a8` feat(stock): pedir abre el formulario en el sitio y la vuelta desde pedidos selecciona el componente (T19)
- `ccec333` test(e2e): smoke de pedidos y otros pedidos con proveedor de prueba y limpieza por id (T20)
- `c4191cb` fix(e2e): registrar los ids del lote antes de las aserciones y limpieza resistente (T20, arreglo de la revisión)
- `e39769b` fix(pedidos): aviso de omitidas solo con lista cargada, test del 409 en recepcion parcial y calcos del enlace, del cancelado seleccionado y de la vuelta a stock (arreglo tras la revisión final)
- `5377a1d` docs(web): ficha de pedidos sin marcar, CHANGELOG y version 0.7.0 (T21)

**Suites (Task 21, Step 3).** Servidor **421** tests en verde (286 + 122 de T1-T6 + 13 del arreglo final). Web: lint y `tsc -b` limpios, **1327** tests en verde (1119 + 203 de T7-T19 + 5 del arreglo final) y build correcto. Cliente JavaFX sin cambios, **284** tests esperados en verde. El contrato `target/openapi.json` del servidor es idéntico a `api/openapi.json` de la web. El smoke `tests/e2e/pedidos.spec.ts` **no se ha ejecutado** (solo `npx playwright test --list`); va en U4.

Desviaciones respecto al plan:

- **Recuentos.** Servidor 421 (no 408): el arreglo tras la revisión final añadió 13 tests. Web 1327 (no 1322): T7 sumó +10 (el test del 503 con JSON de la decisión D-503) y el arreglo final +5.
- **Servidor, arreglo final `5153e54`.** Una tasa ≤ 0 o no numérica de Frankfurter es 503 y no se guarda en la caché del día (antes se cacheaba y `ConversionEur` dividía por cero: 500 en todas las escrituras en USD hasta el día siguiente); `Double.isFinite` en los precios (un `1e400` pasaba la validación); tests del `PUT` de otros, del 503 en los `editar` e `insertar` que faltaban y del alcance de P2 (`en_camino` sí puede cambiar la cantidad).
- **Web, T20.** El combo de proveedor de la línea se localiza por el nombre accesible `Proveedor línea 1`, no como el primer `combobox` del diálogo (el primero es el autocompletar de componente). Arreglo `c4191cb`: los ids del lote se registran antes de las aserciones y la limpieza es resistente a fallos por llamada.
- **Web, arreglo final `e39769b`.** Sin aviso de omitidas si la lista de componentes no se pudo leer (el aviso D10 era falso en ese camino); test del 409 dentro de "Recepción parcial"; y los tres calcos decididos por el usuario (abajo).
- **Paridad.** La ficha `docs/paridad/pedidos.md` (web) está **sin marcar** (85 casillas) y recoge las diferencias y calcos decididos durante la ejecución; "Pendiente de decidir" vacío.

Decisiones del usuario durante la ejecución:

1. **D-503** (revisión previa): un 503 es de negocio solo con el JSON `{message}`; el banner se enciende solo con `ConexionError`.
2. **Spec §4.2, "componente: solo alta"** (revisión previa): "El componente no está activo." solo en POST y lotes; se mantiene el 422 del proveedor desactivado en el `PUT` (desviación 8 del servidor).
3. **Sondeo del taller** (revisión previa): las vistas del taller siguen sondeando debajo de un formulario abierto desde la campana; solo Stock actual y Pedidos lo congelan.
4. **Tras las revisiones finales:**
   - El enlace Componente ya no para la propagación: pulsarlo selecciona la fila (calco).
   - Una fila `cancelado` seleccionada es navy **sin** la opacidad 0,45 (calco del JavaFX). Contradecía la spec §6, que decía que la opacidad prevalecía: §6 y §10 corregidas en este commit.
   - La vuelta a Stock con `?componente=` selecciona la fila solo si queda visible tras los filtros (calco).
   - **P2 frente a "Cerrar sin resto" del JavaFX**: editar desde el JavaFX un `recibido` cerrado con recibida < pedida da 422 "No se puede cambiar la cantidad de un pedido recibido." salvo que se teclee la pedida. Se anota en la ficha y en la spec §10, se añade una nota a las NOVEDADES del JavaFX en su próxima versión (pendiente; el cliente no se toca en este sub-proyecto) y al backlog del servidor tolerar `cantidad == cantidadRecibida` como "sin cambio".
   - Las ~30 líneas casi idénticas entre `EditarPedidoDialog` y `EditarOtroPedidoDialog`: al backlog.
5. **Otras diferencias anotadas en la ficha** (de las revisiones): `PUT` con 422 "El proveedor no está activo." si el proveedor se desactivó después; "Pedir" de Stock sobre un componente desactivado da 422 "El componente no está activo." al guardar (el JavaFX lo permitía, y el JavaFX 0.16.x ahora recibe el 422); clic derecho en un cancelado muestra el menú nativo del navegador; en una fila navy el enlace, el importe ámbar y el "!" conservan su color (a comprobar con captura); con solo "Desactivado" marcado la vuelta desde Pedidos aplica los filtros sin seleccionar; el panel de la campana se cierra al pedir; Enter no confirma en los formularios de alta; 503 de la tasa inline en los formularios; `['notificaciones']` se invalida entero.

Backlog menor (de las revisiones de tarea y finales; va a `Apuntes/plan-futuro.md` al cerrar, U12):

- **Servidor** (revisión final "Ready to merge" tras `5153e54`): `UPDATED_AT` en el `WHERE` de los guards (hoy `checkUpdatedAt` lee y luego escribe); 404 para ids inexistentes (hoy 500, preexistente); deduplicar los ids de solicitud del lote (logs `GESTIONAR_SOLICITUD` repetidos); `getIdComDeSolicitud` filtrando por `ES_SOLICITUD = 1` (nunca por estado, rompería el reintento); reutilizar `Basico`/`Proveedor` ya leídos en los logs de alta (dos lecturas redundantes); duplicaciones (`checkUpdatedAt`, `GeneratedKeyHolder`, `DIVISAS`/`MSG_DIVISA` de `ProveedorController`); comprobar el status HTTP de Frankfurter; traza de `DataAccessResourceFailureException` en `CompraLoteServiceTransaccionTest`; tolerancia de P2 (`cantidad == cantidadRecibida` como sin cambio).
- **Web** (revisión final "Ready to merge" tras `c4191cb` + `e39769b`): tests de cliente (`reportarExito` tras un 503 de negocio, 503 JSON sin `message`, fallo al leer el cuerpo de un 5xx que ya no llama a `reportarFallo`); `FILTROS_PEDIDOS_VACIOS` como fábrica; identidad estable de `useTasas`; `setError(null)` al teclear en `CantidadDialog`; guard `isPending` ante una acción directa repetida (409 falso de "modificado por otro usuario"); `scrollIntoView` del `ref` de `DialogoLineas` como efecto; líneas duplicadas de los diálogos de alta y de los editores; aserciones de deshabilitado en T18 ("Pedir piezas" durante la relectura y tras el error); comentario de "por referencia" en `PrecargaPedido`; `EditarPedidoDialog` con una divisa distinta de EUR/USD sin test; alinear con los nombres reales los tests citados en la tabla de trazabilidad; el test del cancelado seleccionado comprueba la clase, no una selección real; un `json()` que lanza con 2xx deja pedidos del smoke sin registrar (casi imposible).

### Pendiente, del usuario y uno a uno

No hacer push, merge, tag ni despliegue sin OK. Claude no hace SSH a las VMs: los comandos de la VDC se preparan y los ejecuta el usuario. Las capturas de la web se comparan **antes del merge de la web** (spec §9 y Global Constraints).

**U1. Push de las dos ramas** (copia de seguridad; con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor push -u origin feature/web-pedidos
git -C gestion-reparaciones-web push -u origin feature/web-pedidos
```

**U2. Merge del servidor en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-pedidos
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q test
git push origin main
```

Expected: suite en verde en `main` antes del push.

**U3. Despliegue del servidor en la VDC y contrato** (lo ejecuta el usuario; guía privada `Apuntes/despliegue_vdc_produccion.md` §P8). Solo el backend: la web 0.6.0 desplegada sigue funcionando con el servidor nuevo porque todo es aditivo.

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-servidor pull && git -C gestion-reparaciones-servidor log --oneline -1
docker compose up -d --build backend
docker compose logs --tail=80 backend | grep -E "Started|ERROR"
exit
```

Expected: el `log -1` muestra el merge de U2 y los logs, `Started App`. Después, en el PC (Git Bash, desde la web, credenciales de `~/.env.e2e` exportadas y sin escribirlas en la línea de comandos):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
cp api/openapi.json "$TMPDIR/openapi-rama.json"
API_URL="$E2E_BASE_URL" API_USER="$E2E_USER" API_PASS="$E2E_PASS" node scripts/fetch-openapi.mjs
node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/comparar-openapi.mjs "$TMPDIR/openapi-rama.json" api/openapi.json
git checkout api/openapi.json
```

Expected: sin diferencias salvo `servers` (lección del 4a); `git checkout` devuelve el snapshot determinista. `node scripts/fetch-openapi.mjs` (no `npm run api:types`) para no regenerar `schema.d.ts`.

**U4. Smoke contra producción con la web de la rama en local** (escribe en la BD de pruebas; con OK). `.env.local` con `VITE_API_PROXY_TARGET` apuntando a la API de producción (lección del 3b) y el puerto 5173 libre:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout feature/web-pedidos
npm run dev   # en otra terminal
set -a; . ~/.env.e2e; set +a
E2E_BASE_URL=http://localhost:5173 npx playwright test tests/e2e/pedidos.spec.ts
E2E_BASE_URL=http://localhost:5173 npx playwright test
```

Expected: `pedidos.spec.ts` en verde; después la suite completa en serie (`workers: 1`) con `stock`, `asignar`, `clientes` y `taller` en verde (`formulario.spec.ts` depende de su precondición de datos, como en 4a). Si `pedidos.spec.ts` falla en la limpieza, sus `expect.soft` nombran la ruta que quedó: se anota en la limpieza de la VDC. Respetar el límite de 5 logins por minuto: espaciar las dos ejecuciones.

**U5. Capturas del JavaFX** (el usuario, con Claude manejando los scripts de `Apuntes/herramientas/paridad-capturas/` sin pulsar nada que escriba). Worktree recreado desde el raíz, que se queda en `main`:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git worktree add ../_ref-hotfix-0163 hotfix/0.16.3
cp gestion-reparaciones-cliente/src/main/resources/config.properties ../_ref-hotfix-0163/gestion-reparaciones-cliente/src/main/resources/config.properties
```

Editar esa copia para que apunte a producción (como en 4a; el fichero está ignorado por git), y lanzar:

```bash
cd /c/Users/dev/Documents/_ref-hotfix-0163/gestion-reparaciones-cliente
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q javafx:run
```

Comprobar con `Get-NetTCPConnection -OwningProcess <pid>` que solo conecta con producción. Recorrer `CAPTURAS-4b.md` una a una; los pedidos de prueba en cada estado los crea el usuario desde el JavaFX (las escrituras nunca las pulsa el script) y apunta sus ids en "Datos creados". Al terminar: `git worktree remove ../_ref-hotfix-0163` desde el raíz.

**U6. Capturas de la web y comparación lado a lado** (antes del merge de la web). Con la web de la rama en local contra producción, tomar las mismas situaciones con el prefijo `web-` en `Apuntes/paridad-capturas/almacen/` y anotar pareja a pareja en `COMPARACION-4b.md` (formato de `COMPARACION-4a.md`): diferencia deliberada confirmada, calco, no comparable por datos o diferencia nueva. **Cada diferencia nueva se decide con el usuario**, no sobre la marcha; las que se corrijan van a `feature/web-pedidos` con su test y su commit, y las aceptadas a la ficha ("Decididas durante la ejecución y la comparación de capturas").

**U7. Ficha marcada** contra las capturas y los tests; commit en la rama:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git add docs/paridad/pedidos.md
git commit -m "docs(web): ficha de pedidos marcada tras comparar capturas"
git push origin feature/web-pedidos
```

**U8. Merge de la web en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-pedidos
npm run check && npm run build
git push origin main
```

**U9. Despliegue de la web en la VDC** (lo ejecuta el usuario):

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-web pull && git -C gestion-reparaciones-web log --oneline -1
docker compose up -d --build nginx
exit
```

Verificación del bundle desde el PC: el `index-*.js` que sirve producción es el del build local de U8.

```bash
set -a; . ~/.env.e2e; set +a
curl -s "$E2E_BASE_URL/" | grep -o 'assets/index-[^"]*\.js'
grep -o 'assets/index-[^"]*\.js' /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/dist/index.html
```

Expected: el mismo nombre en las dos líneas. Si difiere con commits nuevos, la variante `--no-cache` de §P8 de la guía.

**U10. Tag `v0.7.0`** (con OK): fijar antes la fecha de la entrada del CHANGELOG (commit `docs(web): fecha de la 0.7.0` en `main` y push) y después:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git tag -a v0.7.0 -m "v0.7.0: pedidos, formularios de pedido y campana"
git push origin v0.7.0
```

**U11. Gitlinks en el raíz** (con OK para el push):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-servidor gestion-reparaciones-web docs/superpowers/plans/2026-09-25-web-almacen-pedidos.md
git commit -m "chore: gitlinks servidor y web tras el sub-proyecto 4b (web v0.7.0) y cierre del plan"
git push origin main
```

Antes, completar en este plan un "### Cierre final" con el esquema del 4a (`2026-09-24-web-almacen-stock.md:3863-3869`): merges, despliegues, smoke, comparación, tag y suites finales.

**U12. `Apuntes/plan-futuro.md`, §9** (privado, sin commit): marcar `[x]` la casilla **4b** con el mismo nivel de detalle que la del 4a (commits de `main`, tag, tests, despliegue, smoke, capturas comparadas), y añadir debajo:

- `[ ] Limpieza en la VDC del 4b (se suma a las anteriores)`: los pedidos de componentes y de otros creados para las capturas (ids en `CAPTURAS-4b.md`, "Datos creados"), las unidades de stock que sumaron sus recepciones (revertir o corregir el SKU), el proveedor USD si se le crearon pedidos, lo que el smoke no pudiera limpiar (sus `expect.soft` nombran la ruta) y los logs `CREAR_PEDIDO`, `EDITAR_PEDIDO`, `BORRAR_PEDIDO`, `*_OTRO` y de proveedor del smoke.
- `[ ] Backlog 4b → web / servidor`: lo triado como backlog en las revisiones de las tareas y en la revisión final.

**U13. Memoria** (Claude, con OK): actualizar `project_migracion_web_programa.md` (4b cerrado, web v0.7.0, siguiente 4c) y su línea en `MEMORY.md`.
