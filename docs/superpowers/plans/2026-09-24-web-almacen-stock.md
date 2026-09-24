# Sub-proyecto 4a — Stock actual y Proveedores: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el "Pendiente de migrar" de `/stock` por las pestañas "Stock actual" y "Proveedores" de la vista Stock del JavaFX (tabla con semáforo, filtros, gráficos, menú y diálogos; proveedores con alta, edición, activación y borrado), y subir al servidor la cantidad en camino por master, el 409 al borrar un proveedor con pedidos y las validaciones de rango y nombre.

**Architecture:** El servidor gana tres cambios aditivos en controladores y DAO existentes. La web gana el módulo `modules/almacen/` con dos páginas (`stock/`, `proveedores/`) construidas sobre `DataTable`, `MultiSelect`, `ConfirmDialog` y los hooks de refresco existentes; el semáforo de stock pasa a `shared/lib` para que la campana y el formulario lo compartan; los gráficos van con Recharts; los filtros y la selección viven en stores de módulo para sobrevivir al cambio de pestaña.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + JUnit 5 + Mockito + MockMvc. Web React 19 + TypeScript + TanStack Query + openapi-fetch + Recharts + Vitest + Testing Library + MSW + Playwright.

**Spec:** [`docs/superpowers/specs/2026-09-24-web-almacen-stock-design.md`](../specs/2026-09-24-web-almacen-stock-design.md) (decisiones S1-S9 vinculantes) y [`2026-09-24-web-almacen-programa-design.md`](../specs/2026-09-24-web-almacen-programa-design.md) (D1-D16). **Referencia de detalle:** el inventario `inventario-stock.md`, guardado fuera del repo (lo tiene el controlador de la sesión; si una regla de este plan no cuadra con el JavaFX, manda el JavaFX y se consulta).

## Revisión previa (2026-09-24)

Tres subagentes revisaron el plan contra spec y código; el código de T1-T3 y T4-T9 se aplicó en copias y pasó (285 tests servidor, 1042 web). Correcciones aplicadas:

- Servidor C1: recuento de la suite, 271 + 14 nuevos = 285 (Task 3); con el test `borrarInexistenteEs204SinLog` de la decisión 2, 271 + 15 = 286.
- Servidor C3: nota de que `cantidad-en-camino` con `idCom` inexistente da 500 por `resolveToMasterId`, como `insertar` (Task 1, a la ficha).
- Servidor D1: `editarConMinimoNegativoEs422` aserta el 422 y `verify(dao, never()).actualizar(...)` (Task 3).
- Servidor D4: el assert del contrato va al bloque "Las seis respuestas que dejan de ser Map" (Task 1).
- Servidor E1: fuera la frase condicional sobre el constructor del DAO, que es `CompraComponenteDAO(JdbcTemplate)` (Task 1).
- Servidor E3: quitar `import java.util.Map` de `CompraController.java` (Task 1).
- Servidor E9: `OpenApiContractTest` ya valida el arranque; el `spring-boot:run` manual sale del plan (decisión 5, Task 3).
- Web A1 (web-1 y web-2): `getDefaultNormalizer({ collapseWhitespace: false })` en los `getByText` con espacios múltiples (T7, T10, T12, T13) y regla en Global Constraints.
- Web-1 A2: en T7 los botones del diálogo son `['Cancelar', 'Confirmar', 'Close']`; aviso de la ✕ en T12 y T14.
- Web-1 A3: tipo `Enlace` en `src/shared/lib/enlaces.ts`, `EnlaceTaller = Enlace`; `almacen` no importa de `taller` (T6).
- Web-1 A4: `npm install recharts@3.10.1 react-is@19.3.0`, `react-is` directa y fijada (T4).
- Web-1 A5: contrato por el flujo offline (`OpenApiContractTest` → `api/openapi.json` → `api:types:offline`) (T4).
- Web-1 B3: fuera la nota de quitar el `^` a mano (`.npmrc` con `save-exact`) (T4).
- Web-1 B4: fuera la frase del test de contrato de la web, que no existe (T4).
- Web-1 C1 + web-2 A8: `meta.silenciarError` en `useAjustarMinimo`, `useCrearProveedor` y `useEditarProveedor`; prop `errorServidor` (hook `useErrorServidor`) en los cuatro diálogos; 422 inline con el diálogo abierto y el resto por `mostrarError`; tests por diálogo (T9, T12, T13, T14).
- Web-1 C2: `aria-describedby` solo cuando no hay subtítulo, por spread (T7).
- Web-1 C3: `whitespace-pre` en la `DialogDescription` (T7).
- Web-1 C5: el gráfico por SKU solo cambia cuando llega la cantidad en camino y conserva el anterior si falla (spec §8); fuera `useCantidadEnCamino`, sin consumidor (T9, T13).
- Web-1 C6: desviación anotada, `useInteraccionesAbiertas` en `shared/lib` y no en `shared/api` (T5, a la ficha).
- Web-1 C7: fuera del javadoc de `semaforoStock` que las alertas de la campana derivan de ahí (T5).
- Web-1 C8: comentario corregido; invalidar `['componentes']` no cubre los agrupados del formulario (T9).
- Web-1 C9: líneas citadas corregidas (`piezas.ts:41-46`, `SubNav.test.tsx:18-22`) (T5, T6).
- Web-1 D1: caso nuevo de stock negativo en `piezas.test.ts` (T5).
- Web-1 D2: `toHaveAccessibleDescription` en el test del subtítulo (T7).
- Web-2 A2: la fecha del CSV se espera en hora de Madrid (`12:30`) (T10).
- Web-2 A3: clic derecho sobre `filaDe(...)` en vez de `getByText` tras seleccionar (T13).
- Web-2 A4: "Sin stock" buscado dentro de la tabla (T13).
- Web-2 A5: `ComboNavy` como `combobox`/`listbox` con botón de opción (T14).
- Web-2 A6: test de la campana con `renderConRouter` en ruta `*`, testid del panel, `unmount()` por vuelta y test renombrado (T15).
- Web-2 A7: el alta de proveedor manda y espera `divisa: 'EUR'`, sin `as never` (T14).
- Web-2 A9: test del CSV de StockPage completo con el patrón de `HistorialPage.test.tsx` (T13).
- Web-2 A10: `useCallback` para `irAPedidos` y `useMemo` sin disable (T13).
- Web-2 C3: el smoke obtiene el id del proveedor por GET y nombre exacto y solo deja pasar el DELETE de ese id (T16).
- Web-2 C6: encabezado de la ficha `## Diferencias deliberadas respecto al JavaFX` (T17).
- Web-2 D1: la navegación de "En Camino" comprueba `router.state.location.search` (T13).
- Web-2 D3: `antes = cargas.n` se toma tras la recarga de "Desactivar" (T13).
- Web-2 D5: la celda "Último pedido" de una fila desactivada no lleva la crema de selección (T10).
- Web-2 D6: import `'./credenciales.ts'` en el smoke (T16).
- Web-2 D7: celda exacta con `escaparRegex` en el smoke (T16).
- Web-2 E: el "Riesgo conocido" de la autorrevisión pasa a valores comprobados.

Decisiones del usuario (2026-09-24), aplicadas:

1. Servidor C4: el `PUT /api/proveedores/{idProv}` mantiene el 422 "Divisa no válida (EUR o USD)."; antes del merge del servidor, el usuario ejecuta en preprod y prod `SELECT DIVISA, COUNT(*) FROM Proveedor GROUP BY DIVISA;` y normaliza cualquier divisa distinta de EUR/USD (Task 17, Step 7).
2. Servidor C2: `DELETE` de un proveedor inexistente → 204 sin log, como hoy; el nombre se lee de forma tolerante en el controlador (captura `EmptyResultDataAccessException`, el DAO no se toca), `borrar` sigue siendo no-op y el log solo va si hay nombre; test `borrarInexistenteEs204SinLog` (Task 2; suite 286).
3. Servidor C6: nombre de más de 100 caracteres → 422 `MSG_NOMBRE_LARGO` "El nombre no puede superar los 100 caracteres."; el blanco sigue con "El nombre no puede estar vacío." (Task 2).
4. Servidor C5: en el `POST` la divisa nula o en blanco pasa `null` al DAO (EUR); solo se valida si viene informada; el `PUT` sigue estricto (Task 2).
5. Servidor E9: Task 3 Step 4 = suite completa `mvn -q test` (286 tests; `OpenApiContractTest` levanta el contexto), sin arranque manual.
6. Web-2 C1: "Ajustar mínimo" con título "Ajustar mínimo" y subtítulo `subtituloComponente(c)`, como Editar stock (Tasks 12, 13, trazabilidad y ficha).
7. Web-2 C2: "Editar proveedor" se queda como en el plan (nombre en el subtítulo); anotado en la ficha (Task 17).
8. Web-2 C5: "Último pedido" en hora de Madrid con `formatear`; diferencia con el JavaFX anotada en la ficha; el test de T10 usa 09:00 UTC, que no cruza medianoche (Tasks 10, 17).
9. Web-2 B1: el filtro "Estado" es el `MultiSelect` compartido; fuera `textoBotonEstado` (lo cubre `textoMultiSelect`) y las clases copiadas; tests con `checkbox` (Tasks 8, 13).
10. Web-1 C4 / web-2 C4: `filtrosDesdePedidos` sale de 4a y pasa a 4b (Task 8, consistencia y trazabilidad).

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
- **Testing Library no normaliza el texto buscado:** para textos con espacios múltiples (`'lcd-x  (compartido)'`, el subtítulo `Componente: …   ·   …`) usar `getByText(texto, { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })` con `import { getDefaultNormalizer } from '@testing-library/react'`.

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
- Nota (revisión previa, C3): con un `idCom` inexistente la respuesta pasa de `{"value":0}` a 500 por `resolveToMasterId` (`queryForObject`), mismo patrón que `insertar`; anotar en la ficha.

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

Quitar `import java.util.Map` de `CompraController.java` (solo lo usaba `cantidad-en-camino`; comprobado en la revisión previa, E3).

- [ ] **Step 9: Contrato**

En `OpenApiContractTest.elContratoPublicaLosEsquemasDeLaWeb`, al final del bloque comentado "Las seis respuestas que dejan de ser Map" (junto a las demás respuestas que pasaron de `Map` a un record tipado), añadir:

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
- Produces: `POST /api/proveedores` y `PUT /api/proveedores/{idProv}` → 422 con `"El nombre no puede estar vacío."` (nombre nulo o en blanco), `"El nombre no puede superar los 100 caracteres."` (más de 100 tras recortar; decisión 3) o `"Divisa no válida (EUR o USD)."` (divisa distinta de `EUR`/`USD`; en el `POST` la divisa nula o en blanco sigue valiendo y se pasa `null` al DAO, que pone `EUR` (decisión 4); el `PUT` es estricto (decisión 1), el JavaFX siempre manda la del combo). `DELETE /api/proveedores/{idProv}` → 409 `"El proveedor tiene pedidos y no se puede borrar."` y, si borra, log `BORRAR_PROVEEDOR` con `ID_PROV: n, NOMBRE: x`; con un id inexistente, 204 sin log como hoy (decisión 2: el nombre se lee de forma tolerante en el controlador, `borrar` sigue siendo un no-op). Los mensajes de 422 de nombre vacío y divisa son los que muestra el cliente JavaFX (inventario §12.1-12.2).

- [ ] **Step 1: Test (falla)**

`src/test/java/com/reparaciones/servidor/controller/ProveedorControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
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
        assertEquals("El nombre no puede superar los 100 caracteres.", e.getReason());
        verifyNoInteractions(dao);
        // 100 justos valen
        ctl.insertar(new ProveedorController.AltaRequest("x".repeat(100), null, null));
        verify(dao).insertar("x".repeat(100), null, null);
    }

    @Test void altaConDivisaDesconocidaEs422YConNulaOEnBlancoVale() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("ACME", "CNY", null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Divisa no válida (EUR o USD).", e.getReason());
        // Nula o en blanco: se pasa null y el DAO pone EUR (como hoy)
        ctl.insertar(new ProveedorController.AltaRequest("ACME", null, "COMPONENTES"));
        ctl.insertar(new ProveedorController.AltaRequest("ACME", "", "COMPONENTES"));
        ctl.insertar(new ProveedorController.AltaRequest("ACME", "  ", "COMPONENTES"));
        verify(dao, times(3)).insertar("ACME", null, "COMPONENTES");
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
        // El PUT es estricto: sin divisa también es 422 (el JavaFX siempre manda la del combo)
        ResponseStatusException e3 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("ACME", null, "")));
        assertEquals("Divisa no válida (EUR o USD).", e3.getReason());
        ResponseStatusException e4 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("x".repeat(101), "EUR", "")));
        assertEquals("El nombre no puede superar los 100 caracteres.", e4.getReason());
        verify(dao, never()).editar(anyInt(), any(), any(), any());
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

    /** Id inexistente: 204 sin log, como antes del 4a (decisión 2). getNombreById usa queryForObject y lanza
     *  EmptyResultDataAccessException; el controlador lo tolera y borrar(99) sigue siendo un no-op. */
    @Test void borrarInexistenteEs204SinLog() {
        when(dao.tienePedidos(99)).thenReturn(false);
        when(dao.getNombreById(99)).thenThrow(new EmptyResultDataAccessException(1));
        ctl.borrar(99, super7);
        verify(dao).borrar(99);
        verifyNoInteractions(logDao);
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
import org.springframework.dao.EmptyResultDataAccessException;
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
    static final String MSG_NOMBRE_LARGO = "El nombre no puede superar los 100 caracteres.";
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
        // Divisa nula o en blanco: se pasa null y el DAO pone EUR (calco del alta del cliente, que no la manda).
        // Solo se valida si viene informada (decisión 4).
        String divisa = req.divisa() == null || req.divisa().isBlank() ? null : divisaValida(req.divisa());
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
        String nombre = nombreOnull(idProv);
        dao.borrar(idProv); // con un id inexistente es un no-op: 204 como antes del 4a
        if (nombre != null) {
            logDao.insertar(principal.getIdUsu(), "BORRAR_PROVEEDOR", "ID_PROV: " + idProv + ", NOMBRE: " + nombre);
        }
    }

    /** getNombreById usa queryForObject, que lanza con un id inexistente; aquí se tolera para no dar 500 (decisión 2).
     *  El DAO no se toca porque CompraController y CompraOtroController dependen de que lance. */
    private String nombreOnull(int idProv) {
        try {
            return dao.getNombreById(idProv);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static String nombreValido(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOMBRE);
        }
        if (n.length() > NOMBRE_MAX) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOMBRE_LARGO);
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
Expected: PASS, 8 tests.

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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Valor no válido (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), anyInt(), any());
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

- [ ] **Step 4: Ejecutar la suite completa**

Run: `mvn -q test`
Expected: PASS, 286 tests (271 + 15 nuevos: T1 2+1, T2 8, T3 4). `OpenApiContractTest` es `@SpringBootTest` y levanta el contexto completo de Spring, así que la suite valida también el arranque con los cambios de wiring (constructor de `ProveedorController` con `LogDAO`); no hace falta arranque manual (decisión 5).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ComponenteController.java src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java
git commit -m "feat(componentes): 422 con stock o minimo negativos, mismos textos que el cliente"
```

---

## Task 4: Web — rama, contrato regenerado, alias, Recharts y tokens

**Files:**
- Modify: `package.json`, `package-lock.json` (recharts y react-is)
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

Flujo offline del README de la web (sin arrancar el servidor ni BD): el `OpenApiContractTest` del servidor deja el contrato en `target/openapi.json`.

```bash
# en gestion-reparaciones-servidor, rama feature/web-stock con las Tasks 1-3 hechas (Maven en Bash, ver Global Constraints)
mvn -q test -Dtest=OpenApiContractTest
cp target/openapi.json ../gestion-reparaciones-web/api/openapi.json
# en gestion-reparaciones-web
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
```

Expected: `schema.d.ts` cambia SOLO en la respuesta 200 de `/api/compras/cantidad-en-camino/{idCom}` → `components["schemas"]["ValorEntero"]` (antes `{ [key: string]: Record<string, never> }`; el esquema `ValorEntero` ya existía en el contrato). Si hay otros cambios, son de `main` del servidor posteriores al último `api:types` y se revisan antes de seguir. Verificar con `grep -n -A12 'getCantidadEnCamino: {' src/shared/api/schema.d.ts`. La Task 9 (`data?.value`) no compila sin este paso.

- [ ] **Step 3: Alias del contrato**

En `src/shared/api/client.ts`, tras `ContadoresPendientes`:

```ts
/** Almacén (sub-proyecto 4a): proveedores de componentes (`tipo` COMPONENTES/TELEFONOS viene del servidor de main). */
export type Proveedor = components['schemas']['Proveedor']
```

- [ ] **Step 4: Recharts**

```bash
npm install recharts@3.10.1 react-is@19.3.0
```

`react-is` va como dependencia directa y fijada a la versión de React: es peer de Recharts 3 y el lockfile ya trae `react-is@17.0.2` en la raíz de `node_modules` (dependencia indirecta de las librerías de test), así que npm no instalaría la 19; con la 17, Recharts no reconoce los hijos (`<Cell>`, `<Label>`) de los elementos de React 19 (`$$typeof` distinto). `.npmrc` tiene `save-exact=true`, así que las versiones quedan sin `^`.

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

Expected: verde (1017 tests) y build.

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/styles/tokens.css
git commit -m "chore(web): contrato con cantidad en camino tipada, alias Proveedor, recharts y tokens de stock"
```

---

## Task 5: Web — `semaforoStock` compartido y `useInteraccionesAbiertas` a `shared`

**Files:**
- Create: `src/shared/lib/semaforoStock.ts`, `src/shared/lib/semaforoStock.test.ts`
- Modify: `src/modules/taller/lib/piezas.ts:41-46` (`nivelStock`; el tipo `NivelStock` está en la l.39), `src/modules/taller/lib/piezas.test.ts` (caso nuevo de stock negativo)
- Move: `src/modules/taller/asignaciones/useInteraccionesAbiertas.ts` → `src/shared/lib/useInteraccionesAbiertas.ts` (y su `.test.tsx`)
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx:25` (import)

> **Desviación deliberada (C6, va a la ficha):** la spec §5 dice que `useInteraccionesAbiertas` se mueve a `shared/api`; el plan lo mueve a `shared/lib` porque no es API. Solo lo importan `AsignacionesPage.tsx` y su test.

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
 *  mínimo (negativo incluido) es "Bajo"; el resto OK. Semáforo compartido de la web (spec 4a, S3): el combo de SKU del
 *  formulario (piezas.ts) deriva de aquí. */
export function estadoStock(c: Pick<Componente, 'stock' | 'stockMinimo' | 'activo'>): EstadoStock {
  if (!c.activo) return 'Desactivado'
  if (c.stock === 0) return 'Sin stock'
  if (c.stock <= c.stockMinimo) return 'Bajo'
  return 'OK'
}
```

- [ ] **Step 4: `nivelStock` sobre el semáforo**

En `src/modules/taller/lib/piezas.ts`, sustituir `nivelStock` (líneas 41-46) por:

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

**Ojo:** el `nivelStock` de hoy devuelve `'normal'` con stock negativo (`stock > 0 &&`), y el JavaFX lo pinta en ámbar (`FormularioReparacionController.aplicarColorStock`: `== 0` rojo, `<= mínimo` ámbar). `piezas.test.ts` no tiene ningún caso con negativo: AÑADIR uno al `describe('nivelStock y claseStock (color del SKU)', …)` y anotarlo en la ficha de paridad del formulario como diferencia corregida:

```ts
  it('stock negativo → bajo, en ámbar (calco de estadoComponente / aplicarColorStock)', () => {
    expect(nivelStock({ stock: -1, stockMinimo: 0 })).toBe('bajo')
    expect(claseStock({ stock: -1, stockMinimo: 2 })).toBe('text-fila-solicitud-brd')
  })
```

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
- Create: `src/shared/lib/enlaces.ts`, `src/modules/almacen/rutas.ts`
- Modify: `src/modules/taller/rutas.tsx:8` (`EnlaceTaller = Enlace`)
- Modify: `src/app/shell/SubNav.tsx:2,10-13`, `src/app/shell/SubNav.test.tsx:18-22`
- Modify: `src/app/router.tsx:67`

**Interfaces:**
- Produces: `export type Enlace = { to: string; label: string; badge?: 'pendientes' | 'asignaciones'; end?: boolean }` en `@/shared/lib/enlaces` (los campos de `EnlaceTaller` de `taller/rutas.tsx` más `end`). `taller/rutas.tsx` pasa a `export type EnlaceTaller = Enlace` para no tocar sus usos. Nada de `almacen` importa de `taller` (regla de lint `eslint.config.js:52-68`).
- Produces: `enlacesStock(): Enlace[]` = Stock actual `/stock` · Pedidos `/stock/pedidos` · Proveedores `/stock/proveedores`, para los tres roles. Rutas `/stock` (`StockPage`, Task 12), `/stock/pedidos` (`PendienteDeMigrar nombre="Pedidos"`), `/stock/proveedores` (`ProveedoresPage`, Task 13). Hasta que existan las páginas, las dos rutas apuntan a `PendienteDeMigrar` y se sustituyen en su tarea.

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

- [ ] **Step 3: Tipo compartido y enlaces del módulo**

`src/shared/lib/enlaces.ts`:

```ts
/** Entrada de la columna lateral (SubNav). Vive en shared desde el sub-proyecto 4a porque la usan taller y almacén, y un
 *  módulo no importa de otro. `end`: NavLink solo activo en la ruta exacta. */
export type Enlace = { to: string; label: string; badge?: 'pendientes' | 'asignaciones'; end?: boolean }
```

En `src/modules/taller/rutas.tsx`, sustituir la línea 8 (`export type EnlaceTaller = { to: string; label: string; badge?: … }`) por:

```ts
import type { Enlace } from '@/shared/lib/enlaces'
...
export type EnlaceTaller = Enlace
```

`src/modules/almacen/rutas.ts`:

```ts
import type { Enlace } from '@/shared/lib/enlaces'

/** Sidebar de StockView.fxml (`stock-sidebar-btn`): "Stock actual" · "Pedidos" · "Proveedores", en ese orden, sin badges y
 *  para los tres roles (el botón "Stock" de la barra superior tampoco depende del rol). `end` en el primero: sin él,
 *  NavLink lo marcaría activo también en /stock/pedidos. */
export function enlacesStock(): Enlace[] {
  return [
    { to: '/stock', label: 'Stock actual', end: true },
    { to: '/stock/pedidos', label: 'Pedidos' },
    { to: '/stock/proveedores', label: 'Proveedores' },
  ]
}
```

- [ ] **Step 4: `SubNav` con la sección y `end`**

En `SubNav.tsx` (el import de la l.2 deja de traer `type EnlaceTaller`):

```ts
import { enlacesStock } from '@/modules/almacen/rutas'
import { enlacesReparaciones } from '@/modules/taller/rutas'
import type { Enlace } from '@/shared/lib/enlaces'
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
import { getDefaultNormalizer, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { DialogoAlmacen } from './DialogoAlmacen'

const SIN_COLAPSAR = { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) }

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
    expect(dlg.getByText('Componente: lcd-x   ·   Stock actual: 3 ud(s).', SIN_COLAPSAR)).toHaveClass('text-[12px]', 'text-azul-gris', 'whitespace-pre')
    // El subtítulo es la descripción accesible (Radix la enlaza sola; ver el spread de aria-describedby).
    expect(screen.getByRole('dialog')).toHaveAccessibleDescription(/^Componente: lcd-x\s+·\s+Stock actual: 3 ud\(s\)\.$/)
    expect(dlg.getByLabelText('Nueva cantidad')).toHaveValue('3')
    // La ✕ de DialogContent ("Close", sr-only) se pinta después de los children: va la última.
    const botones = dlg.getAllByRole('button').map((b) => b.textContent)
    expect(botones).toEqual(['Cancelar', 'Confirmar', 'Close'])
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
      <DialogContent {...(subtitulo ? {} : { 'aria-describedby': undefined })} className="w-[360px] max-w-[min(360px,calc(100%-2rem))] gap-3 bg-fondo-vista p-7 sm:max-w-[min(360px,calc(100%-2rem))]">
        <form onSubmit={(e) => { e.preventDefault(); if (!enviando) onConfirmar() }} className="flex flex-col gap-3">
          <DialogHeader>
            <DialogTitle className="text-[20px] font-bold text-azul-medio">{titulo}</DialogTitle>
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

`aria-describedby`: Radix Dialog calcula el suyo cuando hay `DialogDescription`; un `aria-describedby={undefined}` explícito lo pisaría y el diálogo perdería la descripción. Por eso el prop solo se pasa (a `undefined`, como `ClienteDialog.tsx:30`) cuando no hay subtítulo. Los subtítulos llevan **tres espacios** a cada lado del punto medio; el navegador los colapsaría al pintar, de ahí `whitespace-pre` en `DialogDescription`. En los tests, `getByText` no normaliza el texto buscado: usar `SIN_COLAPSAR` (ver Global Constraints).

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
export function textoDesactivados(n: number): string | null              // null con 0; "1 desactivado"; "N desactivados"
export function nombreComponente(c: Pick<Componente, 'tipo' | 'idComMaster'>): string  // tipo + "  (compartido)"
```

El texto del botón "Estado" ("Estado" / el único marcado / "N estados") no tiene función propia: lo da `textoMultiSelect` dentro del `MultiSelect` compartido (decisión 9, Task 13), cubierto por `MultiSelect.test.tsx` y por el test del botón de `StockPage.test`. `filtrosDesdePedidos` (calco de `navegarAComponente`) sale de 4a y se hace en 4b, que es quien la usa (decisión 10).

- Produces (`estado.ts`): `filtrosStock = crearStore<FiltrosStock>(FILTROS_STOCK_VACIOS)`, `seleccionStock = crearStore<string | null>(null)` (id del componente seleccionado, como texto).

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, nombreComponente, ordenarStock, textoDesactivados } from './filtros'

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
  it('pie de desactivados: nada con 0, singular con 1, plural con más', () => {
    expect(textoDesactivados(0)).toBeNull()
    expect(textoDesactivados(1)).toBe('1 desactivado')
    expect(textoDesactivados(3)).toBe('3 desactivados')
  })
  it('nombre con el sufijo "(compartido)" de dos espacios', () => {
    expect(nombreComponente({ tipo: 'lcd-y', idComMaster: 1 })).toBe('lcd-y  (compartido)')
    expect(nombreComponente({ tipo: 'lcd-x', idComMaster: null })).toBe('lcd-x')
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

/** Calco de la etiqueta lblDesactivados (:480-484): oculta a cero. */
export function textoDesactivados(n: number): string | null {
  if (n <= 0) return null
  return n === 1 ? '1 desactivado' : `${n} desactivados`
}

/** Calco de la columna Componente (:298-302): dos espacios antes del paréntesis. */
export function nombreComponente(c: Pick<Componente, 'tipo' | 'idComMaster'>): string {
  return c.idComMaster != null ? `${c.tipo}  (compartido)` : c.tipo
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
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock/filtros.ts src/modules/almacen/stock/filtros.test.ts src/modules/almacen/stock/estado.ts
git commit -m "feat(stock): orden, filtros de estado y buscador, texto del pie y stores de la vista"
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
export function pedirCantidadEnCamino(idCom: number): Promise<number>                                // GET cantidad-en-camino → value (T13 lo llama en un efecto)
export function useEditarStock(): UseMutationResult<..., { c: Componente; stock: number }>          // PUT, meta.silenciarError (409 propio)
export function useAjustarMinimo(): UseMutationResult<..., { idCom: number; stockMinimo: number }> // PATCH stock-minimo, meta.silenciarError (422 inline)
export function useSetActivoComponente(): UseMutationResult<..., { idCom: number; activo: boolean }>
export function useSolicitarPieza(): UseMutationResult<..., { idCom: number; descripcion: string | null }>
```

Todas las mutaciones invalidan `['componentes']` (prefijo: cubre `gestionados` y cualquier otra consulta bajo `['componentes', …]`; NO cubre los `agrupados` del formulario, que viven en `['formulario', 'nuevo' | 'editar', id]` (`modules/taller/formulario/api.ts:10-11`) y se recargan al abrir el formulario) y `['notificaciones', 'componentes']` (la campana consulta el mismo endpoint con su propia clave; se cita literal porque `almacen` no puede importar de `taller`).

> **Revisión previa (C5):** no hay hook `useCantidadEnCamino`. La spec §8 pide que, si falla la cantidad en camino, el gráfico por SKU conserve lo anterior; una consulta con clave por `idCom` pintaría 0 al cambiar de fila. La Task 13 llama a `pedirCantidadEnCamino` desde un efecto y guarda el último gráfico bueno en estado; el hook se quitó por quedarse sin consumidor.

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

/** Invalida `['componentes', …]` (tabla de Stock) y la clave de la campana. Los agrupados del formulario van con otra
 *  clave (`['formulario', …]`) y no se tocan: el formulario los recarga al abrirse. */
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

/** Su 422 se pinta dentro del diálogo (spec §8) y el resto de errores los muestra la vista a mano: silencia el diálogo
 *  global para que el aviso no salga dos veces. */
export function useAjustarMinimo(): UseMutationResult<unknown, unknown, { idCom: number; stockMinimo: number }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idCom, stockMinimo }: { idCom: number; stockMinimo: number }) =>
      api.PATCH('/api/componentes/{idCom}/stock-minimo', { params: { path: { idCom } }, body: { stockMinimo } }),
    meta: { silenciarError: true },
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
import { getDefaultNormalizer, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { CREMA_EN_FILA_SELECCIONADA, DataTable } from '@/shared/ui/DataTable'
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
    // 09:00 UTC = 11:00 en Madrid: la hora no cruza medianoche al convertir, así que el día es el mismo en UTC y en Madrid
    // (formatear pasa a Madrid, decisión 8; entre las 22:00 y las 24:00 UTC la web pintaría el día siguiente).
    montar([c({ idComMaster: 9, ultimoPedido: '2026-08-15T09:00:00' })])
    expect(screen.getByText('lcd-x  (compartido)', { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })).toBeInTheDocument()
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
  it('en una fila desactivada "Último pedido" no lleva la crema de la fila seleccionada (no se pone azul)', () => {
    montar([c({ activo: false, ultimoPedido: '2026-08-15T09:00:00' })])
    expect(screen.getByText('15/08/2026')).not.toHaveClass(CREMA_EN_FILA_SELECCIONADA)
  })
  it('parámetros hacia Pedidos: los tres estados del pipeline y el buscador con el tipo', () => {
    expect(parametrosPedidos(c({ tipo: 'lcd x pro' }))).toBe('estados=pendiente%2Cen+camino%2Cparcial&buscar=lcd+x+pro')
  })
  // formatear lee el ISO sin zona como UTC y lo pinta en Europe/Madrid, como FechaUtils.formatear en exportarStock: 10:30 UTC = 12:30 CEST.
  it('CSV: cabeceras exactas del JavaFX, tipo sin sufijo, estado del semáforo y fecha de registro con hora (Madrid)', () => {
    expect(CABECERAS_CSV_STOCK).toEqual(['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro'])
    expect(filaCsvStock(c({ idComMaster: 9, stock: 0, enCamino: 4 }))).toEqual(['lcd-x', '0', '2', 'Sin stock', '4', '01/09/2026 12:30'])
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
      // La desactivada no se pone azul al seleccionarse (claseFilaStock): sin la crema, que sobre blanco con opacidad 0.45 no se leería.
      // formatear pasa de UTC a hora de Madrid, como el resto de la web y el CSV del JavaFX; la tabla del JavaFX pinta el día UTC
      // sin convertir (StockController:328-330). Diferencia aceptada (decisión 8), anotada en la ficha.
      cell: ({ row }) => <span className={row.original.activo ? CREMA_EN_FILA_SELECCIONADA : undefined}>{row.original.ultimoPedido ? formatear(row.original.ultimoPedido, 'dd/MM/yyyy') : '—'}</span>,
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
Expected: PASS, 8 tests.

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
- Create: `src/modules/almacen/ui/useErrorServidor.ts` (lo usan también los diálogos de proveedor de la Task 14)

**Interfaces:**
- Consumes: `DialogoAlmacen` (Task 7); `Input`, `Label` de `@/shared/ui`; `Componente`.
- Produces:

```ts
export function subtituloComponente(c: Pick<Componente, 'tipo' | 'stock'>): string   // "Componente: <tipo>   ·   Stock actual: <stock> ud(s)."
export function parseEnteroNoNegativo(texto: string): number | null                  // trim, entero, ≥ 0; si no, null
export function EditarStockDialog({ componente, enviando, errorServidor, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; errorServidor?: string | null; onConfirmar: (stock: number) => void; onCancelar: () => void })
export function AjustarMinimoDialog({ componente, enviando, errorServidor, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; errorServidor?: string | null; onConfirmar: (stockMinimo: number) => void; onCancelar: () => void })
// ui/useErrorServidor.ts
export function useErrorServidor(errorServidor: string | null | undefined): { error: string | null; ocultar: () => void }
export function SolicitarPiezaDialog({ componente, enviando, onConfirmar, onCancelar }: { componente: Componente | null; enviando: boolean; onConfirmar: (descripcion: string | null) => void; onCancelar: () => void })
```

`componente === null` = cerrado. `errorServidor` = texto de un 422 del servidor (spec §8): se pinta en la línea de error del diálogo si no hay error de validación local y se oculta al teclear; la vista lo pone a `null` antes de cada envío y al cerrar (Task 13). Textos exactos (inventario §9.1, §9.2, §9.4): "Editar stock" / etiqueta **"Nueva cantidad"** / error **"Cantidad no válida (debe ser ≥ 0)."** / "Confirmar"; "Ajustar mínimo" con título **"Ajustar mínimo"** y subtítulo `subtituloComponente(c)` como "Editar stock" (spec §6 y S5, decisión 6; el título de ventana "Stock mínimo" del `TextInputDialog` no se usa) / etiqueta **"Nuevo stock mínimo:"** / error **"Valor no válido (debe ser ≥ 0)."** / "Confirmar"; "Solicitar pieza" / etiqueta **"Descripción (opcional)"** / placeholder **"Motivo o contexto de la solicitud..."** (tres puntos ASCII) / "Solicitar", descripción recortada y vacía → `null`.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/dialogos.test.tsx`:

```tsx
import { getDefaultNormalizer, screen, within } from '@testing-library/react'
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
    expect(dlg.getByText('Componente: lcd-x   ·   Stock actual: 3 ud(s).', { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })).toBeInTheDocument()
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
  it('un 422 del servidor se muestra inline y el diálogo sigue abierto; se oculta al teclear', async () => {
    renderConProviders(<EditarStockDialog componente={comp} enviando={false} errorServidor="Cantidad no válida (debe ser ≥ 0)." onConfirmar={vi.fn()} onCancelar={vi.fn()} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Cantidad no válida (debe ser ≥ 0).')
    expect(screen.getByRole('dialog', { name: 'Editar stock' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Nueva cantidad'), '1')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('AjustarMinimoDialog', () => {
  it('título "Ajustar mínimo", subtítulo de componente, etiqueta "Nuevo stock mínimo:", precargado con el mínimo; error con -1', async () => {
    const onConfirmar = vi.fn()
    renderConProviders(<AjustarMinimoDialog componente={comp} enviando={false} onConfirmar={onConfirmar} onCancelar={vi.fn()} />)
    const dlg = within(screen.getByRole('dialog', { name: 'Ajustar mínimo' }))
    expect(dlg.getByText('Componente: lcd-x   ·   Stock actual: 3 ud(s).', { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })).toBeInTheDocument()
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
  it('un 422 del servidor se muestra inline y el diálogo sigue abierto; se oculta al teclear', async () => {
    renderConProviders(<AjustarMinimoDialog componente={comp} enviando={false} errorServidor="Valor no válido (debe ser ≥ 0)." onConfirmar={vi.fn()} onCancelar={vi.fn()} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Valor no válido (debe ser ≥ 0).')
    expect(screen.getByRole('dialog', { name: 'Ajustar mínimo' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Nuevo stock mínimo:'), '1')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
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

`src/modules/almacen/ui/useErrorServidor.ts`:

```ts
import { useState } from 'react'

/** Error de un 422 del servidor para un diálogo abierto (spec 4a §8): se muestra en la línea de error del diálogo y se
 *  oculta en cuanto el usuario teclea. La vista lo pone a null antes de cada envío, así que dos 422 seguidos con el mismo
 *  texto vuelven a verse (null → texto es un cambio). Patrón "ajustar estado al cambiar una prop" durante el render. */
export function useErrorServidor(errorServidor: string | null | undefined): { error: string | null; ocultar: () => void } {
  const actual = errorServidor ?? null
  const [visto, setVisto] = useState(actual)
  const [oculto, setOculto] = useState(false)
  if (actual !== visto) {
    setVisto(actual)
    setOculto(false)
  }
  return { error: oculto ? null : actual, ocultar: () => setOculto(true) }
}
```

`src/modules/almacen/stock/EditarStockDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { Componente } from '@/shared/api/client'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { useErrorServidor } from '../ui/useErrorServidor'

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

type Props = { componente: Componente | null; enviando: boolean; errorServidor?: string | null; onConfirmar: (stock: number) => void; onCancelar: () => void }

/** Calco de editarStock (:607-675): campo precargado con el stock y con foco, Enter confirma, el error deja el diálogo
 *  abierto. Un 422 del servidor (`errorServidor`) se pinta en la misma línea si no hay error local. */
export function EditarStockDialog({ componente, enviando, errorServidor, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  const [error, setError] = useState<string | null>(null)
  const servidor = useErrorServidor(errorServidor)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el campo al abrir con otro componente (patrón "Adjusting state")
    if (componente) { setTexto(String(componente.stock)); setError(null) }
  }, [componente])
  function confirmar() {
    const n = parseEnteroNoNegativo(texto)
    if (n === null) { setError(MSG_CANTIDAD_NO_VALIDA); return }
    setError(null)
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={componente !== null} titulo="Editar stock" subtitulo={componente ? subtituloComponente(componente) : undefined} error={error ?? servidor.error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="editar-stock-cantidad" className="text-[12px] font-bold text-azul-gris">Nueva cantidad</Label>
      <Input id="editar-stock-cantidad" value={texto} onChange={(e) => { setTexto(e.target.value); servidor.ocultar() }} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
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
import { useErrorServidor } from '../ui/useErrorServidor'
import { parseEnteroNoNegativo, subtituloComponente } from './EditarStockDialog'

export const MSG_MINIMO_NO_VALIDO = 'Valor no válido (debe ser ≥ 0).'

type Props = { componente: Componente | null; enviando: boolean; errorServidor?: string | null; onConfirmar: (stockMinimo: number) => void; onCancelar: () => void }

/** El TextInputDialog nativo de ajustarMinimo (:684-699) pasa al diálogo propio con el mismo estilo que Editar stock
 *  (spec 4a §6 y S5, decisión 6): título "Ajustar mínimo" (en vez del título de ventana "Stock mínimo") y subtítulo
 *  "Componente: <tipo>   ·   Stock actual: <stock> ud(s).". Se conservan "Nuevo stock mínimo:" y el campo precargado con el
 *  mínimo. Diferencia: el error se muestra inline y el diálogo sigue abierto (el JavaFX cerraba y avisaba con un Alert). */
export function AjustarMinimoDialog({ componente, enviando, errorServidor, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState('')
  const [error, setError] = useState<string | null>(null)
  const servidor = useErrorServidor(errorServidor)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el campo al abrir con otro componente
    if (componente) { setTexto(String(componente.stockMinimo)); setError(null) }
  }, [componente])
  function confirmar() {
    const n = parseEnteroNoNegativo(texto)
    if (n === null) { setError(MSG_MINIMO_NO_VALIDO); return }
    setError(null)
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={componente !== null} titulo="Ajustar mínimo" subtitulo={componente ? subtituloComponente(componente) : undefined} error={error ?? servidor.error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="ajustar-minimo-valor" className="text-[12px] font-bold text-azul-gris">Nuevo stock mínimo:</Label>
      <Input id="ajustar-minimo-valor" value={texto} onChange={(e) => { setTexto(e.target.value); servidor.ocultar() }} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
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

**Ojo en tests:** `getAllByRole('button')` dentro de un diálogo incluye la ✕ de `DialogContent` (nombre accesible "Close", la última).

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/almacen/stock/dialogos.test.tsx`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/ui/useErrorServidor.ts src/modules/almacen/stock/EditarStockDialog.tsx src/modules/almacen/stock/AjustarMinimoDialog.tsx src/modules/almacen/stock/SolicitarPiezaDialog.tsx src/modules/almacen/stock/dialogos.test.tsx
git commit -m "feat(stock): dialogos editar stock, stock minimo y solicitar pieza con los textos y validaciones del cliente"
```

---

## Task 13: Web — `MenuComponente` y `StockPage`

**Files:**
- Create: `src/modules/almacen/stock/MenuComponente.tsx`, `src/modules/almacen/stock/StockPage.tsx`, `src/modules/almacen/stock/StockPage.test.tsx`
- Modify: `src/app/router.tsx` (`/stock` → `<StockPage />`)

**Interfaces:**
- Consumes: Tasks 5, 8-12 (de la 9, `pedirCantidadEnCamino`); `DataTable`, `EtiquetaActualizado`, `BotonSecundario`, `MultiSelect` de `@/shared/ui/MultiSelect` (filtro "Estado", decisión 9: el texto del botón lo da su `textoMultiSelect`), `Input`, `useAlerta`, `useSession`, `esAdmin`, `esSuperTecnico`, `useNavigate`, `useStore`, `useRegistrarExportable`, `descargarCsv`, `useInteraccionesAbiertas`, `ReglaNegocioError`, `mensajeDeError`, `esErrorGestionadoGlobalmente` de `@/shared/api/errors`.
- Produces: `StockPage()` en `/stock`; `MenuComponente({ c, rol, onPedir, onEditarStock, onAjustarMinimo, onToggleActivo, onSolicitar, onInteraccion })` con `rol: 'SUPERTECNICO' | 'TECNICO'` (el ADMIN no tiene menú: `menuFila` va `undefined`).

Textos (inventario §1, §6, §9): título **"Stock actual"**; botón de filtro **"Estado"** con las casillas "OK", "Bajo", "Sin stock", "Desactivado" (esta solo si hay desactivados; calco de `hideOnClick=false`: el `MultiSelect` no se cierra al marcar), texto del botón "Estado" / el único marcado / "N estados"; placeholder **"Buscar componente…"** (carácter "…"); **"Limpiar filtros"**; vacío **"Sin componentes"**; ítems **"Pedir"**, **"Editar stock"**, separador, **"Ajustar mínimo"**, separador, **"Desactivar"**/**"Activar"**, separador, **"Solicitar pieza"**; 409 **"El componente fue modificado mientras editabas. Recarga los datos."**.

- [ ] **Step 1: Test (falla)**

`src/modules/almacen/stock/StockPage.test.tsx`:

```tsx
import { act, getDefaultNormalizer, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
// eslint-disable-next-line no-restricted-imports -- "Descargar CSV" vive en el menú de usuario del AppLayout real (patrón de HistorialPage.test.tsx).
import { AppLayout } from '@/app/shell/AppLayout'
import { INTERVALO_CONECTADO_MS } from '@/shared/api/refresco'
import * as csv from '@/shared/lib/csv'
import { renderConProviders, renderConRouter, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
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
    expect(screen.getByText('lcd-y  (compartido)', { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })).toBeInTheDocument()
    expect(screen.getByText('1 desactivado')).toHaveClass('text-[10px]', 'text-texto-vacio')
    expect(screen.getByText(/^Actualizado \d\d:\d\d$/)).toBeInTheDocument()
    // "Sin stock" también está en la leyenda del donut: se busca el badge dentro de la tabla.
    expect(within(screen.getByRole('table')).getByText('Sin stock')).toBeInTheDocument()
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
  it('filtro Estado: casillas sin cerrar el desplegable, texto "N estados", "Desactivado" solo si hay; Limpiar filtros', async () => {
    montar()
    await screen.findByText('lcd-x')
    await userEvent.click(screen.getByRole('button', { name: 'Estado' }))
    // MultiSelect pinta cada opción como checkbox con aria-label = etiqueta (MultiSelect.tsx)
    expect(screen.getAllByRole('checkbox').map((i) => i.getAttribute('aria-label'))).toEqual(['OK', 'Bajo', 'Sin stock', 'Desactivado'])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Bajo' }))
    // marcar no cierra el desplegable (calco de hideOnClick=false)
    expect(screen.getByRole('checkbox', { name: 'Sin stock' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bajo' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Sin stock' }))
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
    expect(screen.getByRole('checkbox', { name: 'Sin stock' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Desactivado' })).not.toBeInTheDocument()
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
  it('si falla la cantidad en camino, el gráfico por SKU conserva el anterior (título incluido) y avisa', async () => {
    montar()
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByText('bat-x'))
    expect(await screen.findByRole('img', { name: 'Stock 2, Pedido 4' })).toBeInTheDocument()
    server.use(http.get('*/api/compras/cantidad-en-camino/:id', () => new HttpResponse(null, { status: 404 })))
    await userEvent.click(within(screen.getByRole('table')).getByText('cam-x'))
    expect(await screen.findByText('Recurso no encontrado.')).toBeInTheDocument()
    // El aviso es un diálogo modal: Radix marca aria-hidden el resto, de ahí hidden: true.
    expect(screen.getByRole('img', { name: 'Stock 2, Pedido 4', hidden: true })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'bat-x', hidden: true })).toBeInTheDocument()
  })
  it('"En Camino" > 0 navega a Pedidos con los tres estados y el buscador', async () => {
    const { router } = renderConRouter(
      [
        { path: '/stock', element: <StockPage /> },
        { path: '/stock/pedidos', element: <p data-testid="pedidos">Pedidos</p> },
      ],
      { sesion: SESION_SUPER, ruta: '/stock' },
    )
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByRole('button', { name: '4' }))
    expect(await screen.findByTestId('pedidos')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/stock/pedidos')
    // URLSearchParams codifica la coma como %2C y el espacio como +.
    expect(router.state.location.search).toBe('?estados=pendiente%2Cen+camino%2Cparcial&buscar=bat-x')
  })
  it('menú del supertécnico: los cinco ítems con separadores; "Activar" en una desactivada; ADMIN sin menú; TECNICO solo "Solicitar pieza"', async () => {
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Pedir', 'Editar stock', 'Ajustar mínimo', 'Desactivar', 'Solicitar pieza'])
    expect(screen.getAllByRole('separator')).toHaveLength(3)
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('mc-x') })
    expect(screen.getByRole('menuitem', { name: 'Activar' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
  })
  it('ADMIN no tiene menú contextual', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('TECNICO solo ve "Solicitar pieza"', async () => {
    montar(SESION_TEC)
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Solicitar pieza'])
  })
  it('"Editar stock" manda el PUT, recarga, y un 409 cierra el diálogo con el aviso y recarga', async () => {
    let cuerpo: unknown = null
    let estado = 200
    server.use(http.put('*/api/componentes/1', async ({ request }) => { cuerpo = await request.json(); return estado === 200 ? new HttpResponse(null, { status: 200 }) : HttpResponse.json({ message: 'Dato modificado por otro usuario' }, { status: 409 }) }))
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    const campo = within(screen.getByRole('dialog', { name: 'Editar stock' })).getByLabelText('Nueva cantidad')
    await userEvent.clear(campo)
    await userEvent.type(campo, '8{Enter}')
    await waitFor(() => expect(cuerpo).toEqual({ tipo: 'lcd-x', stock: 8, stockMinimo: 2, updatedAt: '2026-09-01T10:00:00' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(cargas.n).toBe(2))
    estado = 409
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    await userEvent.type(within(screen.getByRole('dialog')).getByLabelText('Nueva cantidad'), '{Enter}')
    expect(await screen.findByText('El componente fue modificado mientras editabas. Recarga los datos.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Editar stock' })).not.toBeInTheDocument()
    await waitFor(() => expect(cargas.n).toBe(3))
  })
  it('un 422 del servidor se muestra inline y el diálogo sigue abierto (Editar stock y Ajustar mínimo), sin aviso global', async () => {
    server.use(
      http.put('*/api/componentes/1', () => HttpResponse.json({ message: 'Cantidad no válida (debe ser ≥ 0).' }, { status: 422 })),
      http.patch('*/api/componentes/1/stock-minimo', () => HttpResponse.json({ message: 'Valor no válido (debe ser ≥ 0).' }, { status: 422 })),
    )
    montar()
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar stock' }))
    const editar = screen.getByRole('dialog', { name: 'Editar stock' })
    await userEvent.type(within(editar).getByLabelText('Nueva cantidad'), '{Enter}')
    expect(await within(editar).findByRole('alert')).toHaveTextContent('Cantidad no válida (debe ser ≥ 0).')
    expect(screen.getByRole('dialog', { name: 'Editar stock' })).toBeInTheDocument()
    expect(screen.getAllByText('Cantidad no válida (debe ser ≥ 0).')).toHaveLength(1)
    await userEvent.click(within(editar).getByRole('button', { name: 'Cancelar' }))
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Ajustar mínimo' }))
    const minimo = screen.getByRole('dialog', { name: 'Ajustar mínimo' })
    await userEvent.type(within(minimo).getByLabelText('Nuevo stock mínimo:'), '{Enter}')
    expect(await within(minimo).findByRole('alert')).toHaveTextContent('Valor no válido (debe ser ≥ 0).')
    expect(screen.getByRole('dialog', { name: 'Ajustar mínimo' })).toBeInTheDocument()
    expect(screen.getAllByText('Valor no válido (debe ser ≥ 0).')).toHaveLength(1)
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
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    await userEvent.click(screen.getByRole('menuitem', { name: 'Ajustar mínimo' }))
    const campo = within(screen.getByRole('dialog', { name: 'Ajustar mínimo' })).getByLabelText('Nuevo stock mínimo:')
    await userEvent.clear(campo)
    await userEvent.type(campo, '7{Enter}')
    await waitFor(() => expect(llamadas).toContain('min {"stockMinimo":7}'))
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    const cargasAntesDeDesactivar = cargas.n
    await userEvent.click(screen.getByRole('menuitem', { name: 'Desactivar' }))
    await waitFor(() => expect(llamadas).toContain('act {"activo":false}'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    // La invalidación del PATCH recarga en diferido: se espera a esa recarga antes de tomar la referencia.
    await waitFor(() => expect(cargas.n).toBeGreaterThan(cargasAntesDeDesactivar))
    const antes = cargas.n
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
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
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
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
  it('Descargar CSV exporta la lista filtrada con el nombre y las cabeceras del JavaFX', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    // <AppLayout/> monta la campana y el SubNav: sus peticiones van con handlers propios (no se importan los de taller,
    // regla de módulos). Si onUnhandledRequest:'error' señala otra, se añade aquí. No se pisa el de `gestionados`.
    server.use(
      http.get('*/api/solicitudes/count', () => HttpResponse.json({ value: 0 })),
      http.get('*/api/solicitudes-stock/count', () => HttpResponse.json({ value: 0 })),
      http.get('*/api/solicitudes', () => HttpResponse.json([])),
      http.get('*/api/solicitudes-stock', () => HttpResponse.json([])),
    )
    renderConProviders(<StockPage />, { sesion: SESION_SUPER, ruta: '/stock', layout: <AppLayout /> })
    await screen.findByText('lcd-x')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'bat')
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [nombre, cabeceras, filas] = descargar.mock.calls[0]
    expect(nombre).toBe('stock_actual')
    expect(cabeceras).toEqual(['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro'])
    // fechaRegistro 10:00 UTC → 12:00 en Madrid (formatear, como el CSV del JavaFX).
    expect(filas).toEqual([['bat-x', '2', '3', 'Bajo', '4', '01/09/2026 12:00']])
    descargar.mockRestore()
  })
})
```

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
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import type { Componente } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
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
import { AjustarMinimoDialog } from './AjustarMinimoDialog'
import { pedirCantidadEnCamino, useAjustarMinimo, useComponentesStock, useEditarStock, useSetActivoComponente, useSolicitarPieza } from './api'
import { CABECERAS_CSV_STOCK, claseFilaStock, crearColumnasStock, filaCsvStock, parametrosPedidos } from './columnas'
import { EditarStockDialog } from './EditarStockDialog'
import { filtrosStock, seleccionStock } from './estado'
import { aplicarFiltrosStock, FILTROS_STOCK_VACIOS, textoDesactivados } from './filtros'
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
  const { mostrarError } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [filtros, setFiltros] = useStore(filtrosStock)
  const [seleccionada, setSeleccionada] = useStore(seleccionStock)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  // Texto de un 422 del servidor para el diálogo abierto (spec §8): se pinta dentro del diálogo, que sigue abierto.
  const [errorServidor, setErrorServidor] = useState<string | null>(null)
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

  /** Editar stock: el 409 es el aviso de modificado (calco de :661-665); el resto de errores pasan por el mapeo común.
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
            // 422 → inline con el diálogo abierto; 409 y resto → se cierra y avisa (el 409 recarga por el onSettled del hook).
            onError: (e) => { if (errorEnDialogo(e)) return; cerrarDialogo(); alFallarEdicion(e) },
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

Notas para quien lo implemente: (1) `useStore` devuelve `[valor, set]` con `set` que admite valor o función, como `useState`. (2) Si `DataTable` no tiene `altoFila`, mirar sus props (Task 10 ya lo usó): existe (`altoFila?: number`). (3) Un 422 del servidor (Task 3) en "Editar stock" o "Ajustar mínimo" se pinta dentro del diálogo, que sigue abierto (spec §8): `errorEnDialogo` lo pasa a `errorServidor` y el diálogo lo muestra si no hay error de validación local (`useErrorServidor`, Task 12); `useEditarStock` y `useAjustarMinimo` llevan `meta: { silenciarError: true }` (Task 9) para que el diálogo global no lo repita. El 409 de "Editar stock" cierra y avisa como antes; el resto de errores pasa por `mostrarError(mensajeDeError(e))` salvo `esErrorGestionadoGlobalmente`. "Solicitar pieza" no cambia (sin 422 en su endpoint). (4) El `p-5` calca el padding 20 del StackPane central; el `SubNav` ya aporta la columna izquierda.

- [ ] **Step 5: Ruta**

En `router.tsx`: `{ path: '/stock', element: <StockPage /> }` con `import { StockPage } from '@/modules/almacen/stock/StockPage'`.

- [ ] **Step 6: Ejecutar**

**Ojo en tests:** tras un clic (o clic derecho) la fila queda seleccionada y `GraficoSku` pinta el tipo en su `<h2>`: `getByText('lcd-x')` encuentra dos nodos. Para apuntar a la fila se usa `filaDe(tipo)` o `within(screen.getByRole('table'))`.

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
- Consumes: `Proveedor` (Task 4), `DialogoAlmacen` (Task 7), `useErrorServidor` (Task 12), `ReglaNegocioError`/`mensajeDeError`/`esErrorGestionadoGlobalmente`, `MultiSelect`, `StatusBadge`, `ConfirmDialog`, `ComboNavy`, `DataTable`, `EtiquetaActualizado`, `BotonPrimario`, `useInteraccionesAbiertas`, `useStore`, `descargarCsv`.
- Produces:

```ts
// api.ts
export const CLAVE_PROVEEDORES = ['proveedores', 'COMPONENTES'] as const
export function useProveedoresComponentes({ activo }: { activo: boolean }): UseQueryResult<Proveedor[]>   // GET ?tipo=COMPONENTES
export function tienePedidos(idProv: number): Promise<boolean>
export function useCrearProveedor(): UseMutationResult<..., string>                                       // POST {nombre, divisa:'EUR', tipo:'COMPONENTES'}, meta.silenciarError (422 inline)
export function useEditarProveedor(): UseMutationResult<..., { idProv: number; nombre: string; divisa: string; comentario: string }> // meta.silenciarError (422 inline)
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
    await waitFor(() => expect(cuerpo).toEqual({ nombre: 'Nuevo', divisa: 'EUR', tipo: 'COMPONENTES' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('"Nuevo proveedor": un 422 del servidor se muestra inline y el diálogo sigue abierto, sin aviso global', async () => {
    server.use(http.post('*/api/proveedores', () => HttpResponse.json({ message: 'El nombre no puede estar vacío.' }, { status: 422 })))
    montar()
    await screen.findByText('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo proveedor' }))
    const dlg = within(screen.getByRole('dialog', { name: 'Nuevo proveedor' }))
    await userEvent.type(dlg.getByLabelText('Nombre del proveedor:'), 'Nuevo{Enter}')
    expect(await dlg.findByRole('alert')).toHaveTextContent('El nombre no puede estar vacío.')
    expect(screen.getByRole('dialog', { name: 'Nuevo proveedor' })).toBeInTheDocument()
    expect(screen.getAllByText('El nombre no puede estar vacío.')).toHaveLength(1)
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
    // ComboNavy: el disparador es role="combobox" con aria-label; cada opción es un <li role="option"> con un <button>
    // dentro, que es quien recibe el clic (patrón de ComboNavy.test.tsx).
    expect(dlg.getByRole('combobox', { name: 'Divisa' })).toHaveTextContent('EUR')
    expect(dlg.getByLabelText('Comentario')).toHaveValue('principal')
    await userEvent.clear(dlg.getByLabelText('Nombre'))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(dlg.getByRole('alert')).toHaveTextContent('El nombre no puede estar vacío.')
    await userEvent.type(dlg.getByLabelText('Nombre'), 'ACME 2')
    await userEvent.click(dlg.getByRole('combobox', { name: 'Divisa' }))
    await userEvent.click(within(screen.getByRole('listbox', { name: 'Divisa' })).getByRole('button', { name: 'USD' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    await waitFor(() => expect(cuerpo).toEqual({ nombre: 'ACME 2', divisa: 'USD', comentario: 'principal' }))
  })
  it('"Editar": un 422 del servidor se muestra inline y el diálogo sigue abierto, sin aviso global', async () => {
    server.use(http.put('*/api/proveedores/1', () => HttpResponse.json({ message: 'Divisa no válida (EUR o USD).' }, { status: 422 })))
    montar()
    await screen.findByText('ACME')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    const dlg = within(screen.getByRole('dialog', { name: 'Editar proveedor' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar' }))
    expect(await dlg.findByRole('alert')).toHaveTextContent('Divisa no válida (EUR o USD).')
    expect(screen.getByRole('dialog', { name: 'Editar proveedor' })).toBeInTheDocument()
    expect(screen.getAllByText('Divisa no válida (EUR o USD).')).toHaveLength(1)
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

**Ojo en tests:** `getAllByRole('button')` dentro de un diálogo incluye la ✕ de `DialogContent` (nombre accesible "Close", la última).

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

/** El alta del cliente no manda divisa y el DAO pone EUR; el contrato (`ProveedorAltaRequest`) exige `nombre`, `divisa`
 *  y `tipo`, así que se manda 'EUR' explícito: mismo resultado. Su 422 lo pinta el diálogo: silencia el global. */
export function useCrearProveedor(): UseMutationResult<unknown, unknown, string> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (nombre: string) => api.POST('/api/proveedores', { body: { nombre, divisa: 'EUR', tipo: 'COMPONENTES' } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

/** Su 422 (nombre o divisa, Task 2) lo pinta el diálogo: silencia el global. */
export function useEditarProveedor(): UseMutationResult<unknown, unknown, { idProv: number; nombre: string; divisa: string; comentario: string }> {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idProv, ...body }: { idProv: number; nombre: string; divisa: string; comentario: string }) =>
      api.PUT('/api/proveedores/{idProv}', { params: { path: { idProv } }, body }),
    meta: { silenciarError: true },
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

Sobre el alta: `ProveedorAltaRequest` en `schema.d.ts` (línea ~2364) declara `nombre`, `divisa` y `tipo` como obligatorios porque el servidor marca todo `required`. El JavaFX manda solo `{nombre}` (hotfix); la web manda `{ nombre, divisa: 'EUR', tipo: 'COMPONENTES' }`, que cumple el contrato sin casts y da el mismo resultado que el DAO (anotar en la ficha).

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
import { useErrorServidor } from '../ui/useErrorServidor'

export const MSG_NOMBRE_VACIO = 'El nombre no puede estar vacío.'

type Props = { abierto: boolean; enviando: boolean; errorServidor?: string | null; onConfirmar: (nombre: string) => void; onCancelar: () => void }

/** El TextInputDialog nativo de nuevoProveedor (:1773-1785) pasa al diálogo propio (spec 4a, S5): solo el nombre,
 *  "Nombre del proveedor:", sin cabecera. Diferencia S5: el nombre en blanco avisa en vez de cerrarse en silencio. */
export function NuevoProveedorDialog({ abierto, enviando, errorServidor, onConfirmar, onCancelar }: Props) {
  const [nombre, setNombre] = useState('')
  const [error, setError] = useState<string | null>(null)
  const servidor = useErrorServidor(errorServidor)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- vacía el campo al abrir
    if (abierto) { setNombre(''); setError(null) }
  }, [abierto])
  function confirmar() {
    const n = nombre.trim()
    if (n === '') { setError(MSG_NOMBRE_VACIO); return }
    setError(null)
    onConfirmar(n)
  }
  return (
    <DialogoAlmacen abierto={abierto} titulo="Nuevo proveedor" error={error ?? servidor.error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="nuevo-proveedor-nombre" className="text-[12px] font-bold text-azul-gris">Nombre del proveedor:</Label>
      <Input id="nuevo-proveedor-nombre" value={nombre} onChange={(e) => { setNombre(e.target.value); servidor.ocultar() }} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
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
import { useErrorServidor } from '../ui/useErrorServidor'
import { MSG_NOMBRE_VACIO } from './NuevoProveedorDialog'

/** Exactamente las dos del combo del JavaFX (:1803-1806). */
const DIVISAS = [{ valor: 'EUR', etiqueta: 'EUR' }, { valor: 'USD', etiqueta: 'USD' }]

type Props = { proveedor: Proveedor | null; enviando: boolean; errorServidor?: string | null; onConfirmar: (datos: { nombre: string; divisa: string; comentario: string }) => void; onCancelar: () => void }

/** Calco de editarProveedor (:1787-1868): Nombre, Divisa (EUR/USD, combo navy), Comentario (3 filas). Sin Enter en el
 *  JavaFX; aquí Enter en "Nombre" confirma (form), diferencia menor que se anota. El nombre del proveedor va en el
 *  subtítulo porque la web no tiene título de ventana ("Editar proveedor — <nombre>"). */
export function EditarProveedorDialog({ proveedor, enviando, errorServidor, onConfirmar, onCancelar }: Props) {
  const [nombre, setNombre] = useState('')
  const [divisa, setDivisa] = useState('EUR')
  const [comentario, setComentario] = useState('')
  const [error, setError] = useState<string | null>(null)
  const servidor = useErrorServidor(errorServidor)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga al abrir con otro proveedor
    if (proveedor) { setNombre(proveedor.nombre); setDivisa(proveedor.divisa || 'EUR'); setComentario(proveedor.comentario ?? ''); setError(null) }
  }, [proveedor])
  function confirmar() {
    const n = nombre.trim()
    if (n === '') { setError(MSG_NOMBRE_VACIO); return }
    setError(null)
    onConfirmar({ nombre: n, divisa, comentario: comentario.trim() })
  }
  return (
    <DialogoAlmacen abierto={proveedor !== null} titulo="Editar proveedor" subtitulo={proveedor?.nombre} error={error ?? servidor.error} textoAccion="Confirmar" enviando={enviando} onConfirmar={confirmar} onCancelar={onCancelar}>
      <Label htmlFor="editar-proveedor-nombre" className="text-[12px] font-bold text-azul-gris">Nombre</Label>
      <Input id="editar-proveedor-nombre" value={nombre} onChange={(e) => { setNombre(e.target.value); servidor.ocultar() }} autoFocus className="bg-superficie text-[13px] text-azul-medio" />
      <span className="text-[12px] font-bold text-azul-gris">Divisa</span>
      <ComboNavy valor={divisa} opciones={DIVISAS} onChange={(d) => { setDivisa(d); servidor.ocultar() }} textoVacio="EUR" ancho={304} aria-label="Divisa" />
      <Label htmlFor="editar-proveedor-comentario" className="text-[12px] font-bold text-azul-gris">Comentario</Label>
      <textarea id="editar-proveedor-comentario" rows={3} value={comentario} onChange={(e) => { setComentario(e.target.value); servidor.ocultar() }} className="w-full rounded border border-fila-sep bg-superficie p-1.5 text-[13px] text-azul-medio" />
    </DialogoAlmacen>
  )
}
```

`ComboNavy` (`src/shared/ui/ComboNavy.tsx:79-118`, comprobado): el disparador es `role="combobox"` con `aria-label` ("Divisa"); la lista es `role="listbox"` con el mismo nombre; cada opción es un `<li role="option">` con un `<button>` dentro, que es quien recibe el clic. Los tests eligen con `within(listbox).getByRole('button', { name: 'USD' })`, como `ComboNavy.test.tsx:19-26`.

- [ ] **Step 6: `ProveedoresPage`**

`src/modules/almacen/proveedores/ProveedoresPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
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
  const { mostrarError } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [seleccion, setSeleccion] = useStore(filtroProveedores)
  const [seleccionada, setSeleccionada] = useStore(seleccionProveedores)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  // Texto de un 422 del servidor para el diálogo de alta o edición abierto (spec §8): se pinta dentro, que sigue abierto.
  const [errorServidor, setErrorServidor] = useState<string | null>(null)
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

  function cerrarDialogo() {
    setDialogo(null)
    setErrorServidor(null)
  }

  /** Alta y edición (silencian el diálogo global): el 422 va al diálogo, que sigue abierto; el resto se avisa con el
   *  mapeo común, salvo lo que ya gestiona el mecanismo global (401, sin conexión). */
  function alFallarDialogo(e: unknown) {
    if (e instanceof ReglaNegocioError) { setErrorServidor(e.message); return }
    if (esErrorGestionadoGlobalmente(e)) return
    mostrarError(mensajeDeError(e))
  }

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
      <NuevoProveedorDialog
        abierto={dialogo?.tipo === 'nuevo'}
        enviando={crear.isPending}
        errorServidor={errorServidor}
        onCancelar={cerrarDialogo}
        onConfirmar={(nombre) => { setErrorServidor(null); crear.mutate(nombre, { onSuccess: cerrarDialogo, onError: alFallarDialogo }) }}
      />
      <EditarProveedorDialog
        proveedor={dialogo?.tipo === 'editar' ? dialogo.p : null}
        enviando={editar.isPending}
        errorServidor={errorServidor}
        onCancelar={cerrarDialogo}
        onConfirmar={(datos) => {
          if (dialogo?.tipo !== 'editar') return
          setErrorServidor(null)
          editar.mutate({ idProv: dialogo.p.idProv, ...datos }, { onSuccess: cerrarDialogo, onError: alFallarDialogo })
        }}
      />
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

Los 422 del servidor (Task 2) en alta y edición se pintan dentro del diálogo, que sigue abierto (spec §8), igual que en la Task 13: `useCrearProveedor` y `useEditarProveedor` llevan `meta: { silenciarError: true }`, la página guarda `errorServidor` y los diálogos lo muestran con `useErrorServidor` (Task 12). Cubierto por los dos tests "un 422 del servidor se muestra inline…".

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
- Modify: `src/modules/taller/notificaciones/PanelNotificaciones.test.tsx:5` (import de `renderConRouter`), `:55-62` (helper nuevo junto a `abrirPanel`), `:179-205`

**Interfaces:**
- Consumes: `useNavigate` de `react-router`; `onCerrar` del panel.
- Produces: "→ Ir a pedidos" (en las dos pestañas) cierra el panel y navega a `/stock/pedidos`; "Ver Stock Completo" cierra y navega a `/stock`. "Pedir" (tarjeta), "Pedir piezas" y "Pedir todas las piezas" siguen deshabilitados con `TOOLTIP_ALMACEN` (4b). Sin filtros al llegar (calco, inventario §13.3).

- [ ] **Step 1: Test (falla)**

`abrirPanel` (`PanelNotificaciones.test.tsx:55-62`) monta `<Campana />` (no el panel) con `renderConProviders`, pulsa `data-testid="campana"` y la pestaña, y devuelve `{ ...render, llamadas }`: no hay `onCerrar` espiable (lo gestiona la Campana). El panel es `data-testid="panel-notificaciones"`. Añadir `renderConRouter` al import de `@/test/render` y, junto a `abrirPanel`, la variante con data router. La Campana va en la ruta comodín `*` para seguir montada tras navegar (con `path: '/'`, ir a `/stock/pedidos` la desmontaría y "se cerró" pasaría aunque nadie cerrara el panel):

```tsx
/** Como abrirPanel, pero con un data router para leer la ruta tras navegar (sub-proyecto 4a). La Campana va en la ruta
 *  comodín para seguir montada después de ir a /stock o /stock/pedidos. */
async function abrirPanelConRouter(escenario: EscenarioNotificaciones = ESCENARIO, pestana: 'Solicitudes' | 'Alertas' = 'Solicitudes') {
  const registro = conRegistroNotificaciones(escenario)
  server.use(...registro.handlers)
  const resultado = renderConRouter([{ path: '*', element: <Campana /> }], { sesion: SESION_SUPER })
  await userEvent.click(screen.getByTestId('campana'))
  await userEvent.click(screen.getByRole('tab', { name: pestana }))
  return { ...resultado, llamadas: registro.llamadas }
}
```

Sustituir el test `'"→ Ir a pedidos" deshabilitado con el tooltip de Almacén en las dos pestañas'` por (una montura por pestaña, desmontada al final de cada vuelta para no dejar dos campanas):

```tsx
  it('"→ Ir a pedidos" cierra el panel y navega a /stock/pedidos desde las dos pestañas', async () => {
    for (const pestana of ['Solicitudes', 'Alertas'] as const) {
      const { router, unmount } = await abrirPanelConRouter({}, pestana)
      const enlace = screen.getByRole('button', { name: '→ Ir a pedidos' })
      expect(enlace).toBeEnabled()
      expect(enlace).toHaveClass('text-[12px]', 'font-bold', 'text-azul-noche', 'cursor-pointer')
      await userEvent.click(enlace)
      await waitFor(() => expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument())
      expect(screen.getByTestId('campana')).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/stock/pedidos')
      unmount()
    }
  })
```

En el test de los reservados (`:190`), renombrarlo a `'"Pedir piezas", "Pedir" y "Pedir todas las piezas" deshabilitados con tooltip; "Rechazar todo" habilitado'`, sacar "Ver Stock Completo" de la lista (quedan `Pedir` ×2 y "Pedir todas las piezas": `toHaveLength(3)`) y añadir:

```tsx
  it('"Ver Stock Completo" cierra el panel y navega a /stock sin filtros', async () => {
    const { router } = await abrirPanelConRouter(ESCENARIO, 'Alertas')
    const boton = screen.getByRole('button', { name: 'Ver Stock Completo' })
    expect(boton).toBeEnabled()
    expect(boton).toHaveClass('bg-azul-medio', 'text-superficie')
    await userEvent.click(boton)
    await waitFor(() => expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/stock')
    expect(router.state.location.search).toBe('')
  })
```

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

Variables nuevas (en `~/.env.e2e`, fuera del repo): `E2E_SKU_PRUEBA` (el `tipo` exacto de un componente **de prueba** activo y master, que exista en la BD de pruebas). Si falta, el test se salta. El proveedor de prueba se crea con nombre único `E2E <timestamp>`. El `POST /api/proveedores` responde 201 sin cuerpo, así que tras el alta el id se obtiene con `GET /api/proveedores?tipo=COMPONENTES` buscando el nombre EXACTO (debe haber uno y solo uno) y el borrado se hace por ese id: el `DELETE` a cualquier otro id se aborta con `page.route`. Nunca se borra por nombre a ciegas; si no se obtiene el id, el test falla SIN limpiar (mejor basura de test que borrar una fila ajena, como `asignar.spec.ts`).

- [ ] **Step 1: El test**

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

const escaparRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** La fila cuya primera celda es EXACTAMENTE `texto` (patrón de asignar.spec.ts:18-24): "lcd-x" no casa con "lcd-x-pro". */
function filaExacta(page: Page, texto: string) {
  const exacto = new RegExp(`^\\s*${escaparRegex(texto)}\\s*$`)
  return page.getByRole('row').filter({ has: page.getByRole('cell').first().filter({ hasText: exacto }) })
}

/** GET /api/proveedores?tipo=COMPONENTES con el token de la sesión de la página (misma origin que la app). */
async function proveedoresComponentes(page: Page): Promise<{ idProv: number; nombre: string }[]> {
  return page.evaluate(async () => {
    const sesion = JSON.parse(sessionStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const r = await fetch('/api/proveedores?tipo=COMPONENTES', { headers: { Authorization: `Bearer ${sesion.token}` } })
    if (!r.ok) throw new Error(`GET /api/proveedores: ${r.status}`)
    return (await r.json()) as { idProv: number; nombre: string }[]
  })
}

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
  const fila = filaExacta(page, sku!)
  await expect(fila).toHaveCount(1)
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

  // Proveedor de prueba: alta, id por el nombre exacto, ver en la tabla y borrar SOLO ese id
  const nombre = `E2E ${Date.now()}`
  await page.getByRole('link', { name: 'Proveedores' }).click()
  await expect(page.getByRole('heading', { name: 'Proveedores' })).toBeVisible()
  await page.getByRole('button', { name: 'Nuevo proveedor' }).click()
  await page.getByRole('dialog', { name: 'Nuevo proveedor' }).getByLabel('Nombre del proveedor:').fill(nombre)
  await page.getByRole('dialog', { name: 'Nuevo proveedor' }).getByRole('button', { name: 'Confirmar' }).click()
  await expect(page.getByRole('dialog', { name: 'Nuevo proveedor' })).toBeHidden()
  // El alta responde 201 sin cuerpo: el id sale del listado, con el nombre exacto y único. Si no aparece exactamente
  // uno, el test falla aquí sin limpiar nada.
  const creados = (await proveedoresComponentes(page)).filter((p) => p.nombre === nombre)
  expect(creados).toHaveLength(1)
  const idProv = creados[0].idProv
  const filaProv = filaExacta(page, nombre)
  await expect(filaProv).toHaveCount(1)
  try {
    await expect(filaProv.getByText('Activo')).toBeVisible()
  } finally {
    // Solo pasa el DELETE de ese id: si la fila o el menú apuntaran a otro proveedor, la petición se aborta.
    await page.route('**/api/proveedores/*', (route) => {
      const req = route.request()
      if (req.method() === 'DELETE' && new URL(req.url()).pathname !== `/api/proveedores/${idProv}`) return route.abort()
      return route.continue()
    })
    const borrado = page.waitForResponse((r) => r.request().method() === 'DELETE' && new URL(r.url()).pathname === `/api/proveedores/${idProv}`)
    await filaProv.click({ button: 'right' })
    await page.getByRole('menuitem', { name: 'Borrar' }).click()
    await page.getByRole('dialog', { name: 'Borrar proveedor' }).getByRole('button', { name: 'Borrar' }).click()
    expect((await borrado).status()).toBe(204)
    await expect(filaProv).toHaveCount(0)
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

Mismo formato que `docs/paridad/asignar-trabajos.md`: título "Ficha de paridad — Stock actual y Proveedores (StockController)", párrafos de Referencia (`hotfix/0.16.3`, con `?tipo=COMPONENTES` de `main`) / Capturas (**solo por nombre**: `almacen/stock-*.png`, `almacen/proveedores-*.png`, `almacen/campana-*.png`, documentación privada fuera del repo) / ejemplos sintéticos; después **"## Diferencias deliberadas respecto al JavaFX"** con las de la spec §10 (diálogos propios, aviso de nombre en blanco, ordenación bloqueada, selección mantenida, 409 al borrar, `tiene-pedidos` al abrir el menú, Recharts) más las salidas de la ejecución (en "Editar proveedor" el nombre del proveedor va en el subtítulo en vez de en el título de ventana: la web no tiene título de ventana y así queda coherente con "Editar stock" (decisión 7); "Último pedido": el JavaFX pinta la fecha UTC sin convertir y la web la pasa a hora de Madrid con `formatear`, como el resto de la web y el CSV del JavaFX; solo difiere con pedidos entre las 22:00 y las 24:00 UTC, diferencia aceptada (decisión 8); "Ajustar mínimo" con título "Ajustar mínimo" y el subtítulo de componente en vez del título de ventana "Stock mínimo" del `TextInputDialog` (decisión 6, spec §6/S5); el filtro "Estado" es el `MultiSelect` compartido (casillas en un popover) en vez del `MenuButton` del JavaFX (decisión 9, spec §5); nombre de más de 100 caracteres → 422 "El nombre no puede superar los 100 caracteres." (decisión 3); Enter confirma en "Editar proveedor"; ancho 360 en "Solicitar pieza"; 422 inline con el diálogo abierto; el alta de proveedor manda `divisa: 'EUR'` explícita; el smoke obtiene el id del proveedor de prueba con un GET por nombre exacto porque el alta no lo devuelve; `useInteraccionesAbiertas` en `shared/lib` y no en `shared/api` como decía la spec §5; `cantidad-en-camino` con un `idCom` inexistente responde 500 en vez de `{"value":0}`, como `insertar`; el negativo del combo de SKU del formulario pasa a ámbar); y secciones de `- [ ]` por bloque: sidebar y rutas, tabla, semáforo y estilos, filtros, pie, donut, gráfico por SKU, menú por rol, editar stock, ajustar mínimo, activar/desactivar, solicitar pieza, proveedores (tabla, filtro, menú, nuevo, editar, borrar), campana, CSV, refresco y errores. Una línea por comportamiento del inventario, con la captura entre paréntesis.

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
- Servidor: cantidad en camino resuelta al SKU master, 409 al borrar un proveedor con pedidos y 422 en cantidades negativas, nombre vacío o de más de 100 caracteres o divisa desconocida.
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

**No hacer push, merge, tag ni despliegue.** Presentar: qué se ha hecho, estado de los tres repos, y la lista de pasos que requieren su OK **uno a uno**: push de las dos ramas; **antes del merge del servidor, paso del usuario (decisión 1):** ejecutar en preprod y en prod `SELECT DIVISA, COUNT(*) FROM Proveedor GROUP BY DIVISA;` y normalizar a `EUR`/`USD` cualquier otra divisa (el `PUT /api/proveedores/{idProv}` responde 422 "Divisa no válida (EUR o USD)." y el JavaFX precarga la divisa guardada, así que un proveedor con otra divisa dejaría de poder editarse); merges `--no-ff`, tag `v0.6.0`, gitlinks en el raíz, despliegue en la VDC con **el servidor antes que la web**, smoke de la Task 16 contra producción, y actualizar `Apuntes/plan-futuro.md` (casilla 4a) y la memoria del programa.

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
| §6 filtro Estado OR, ninguno = todos, "N estados", no se cierra al marcar; buscador contiene; Limpiar | `filtros.test`, `MultiSelect.test` (texto del botón), `StockPage.test` "filtro Estado…" |
| §6 al llegar desde Pedidos se conserva "Desactivado" | 4b (`filtrosDesdePedidos` sale de 4a, decisión 10) |
| §7 donut sobre todo, activos, sin negativos; compartidos como filas; total y leyenda | `graficos.test.ts` conteosDonut, `graficos.test.tsx` GraficoEstado, `StockPage.test` "el donut cuenta…" |
| §8 barra Stock por semáforo, Pedido azul, placeholder; Pedido solo ADMIN/SUPERTECNICO | `graficos.test` colorBarraStock, GraficoSku, `StockPage.test` "seleccionar una fila…", "el TECNICO no pide…" |
| §9 menú por rol con separadores; Activar/Desactivar alterna; ADMIN sin menú | `StockPage.test` "menú del supertécnico…", "ADMIN…", "TECNICO…" |
| §9.1 Editar stock: precarga, Enter, error, PUT tal cual, 409 con aviso y recarga | `dialogos.test`, `api.test` useEditarStock, `StockPage.test` "Editar stock manda el PUT…" |
| §9.2 Ajustar mínimo: título "Ajustar mínimo" y subtítulo de componente (decisión 6), "Nuevo stock mínimo:" precargado, error, PATCH | `dialogos.test` AjustarMinimo, `StockPage.test` "Ajustar mínimo hace el PATCH…" |
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

**Consistencia de nombres.** `estadoStock`, `ESTADOS_STOCK`, `EstadoStock`, `ordenarStock`, `aplicarFiltrosStock`, `textoDesactivados`, `nombreComponente`, `FILTROS_STOCK_VACIOS`, `filtrosStock`, `seleccionStock`, `useComponentesStock`, `pedirCantidadEnCamino`, `useEditarStock`, `useAjustarMinimo`, `useSetActivoComponente`, `useSolicitarPieza`, `crearColumnasStock`, `claseFilaStock`, `BadgeEstadoStock`, `parametrosPedidos`, `CABECERAS_CSV_STOCK`, `filaCsvStock`, `conteosDonut`, `colorBarraStock`, `COLORES_DONUT`, `COLOR_BARRA_PEDIDO`, `GraficoEstado`, `GraficoSku`, `subtituloComponente`, `parseEnteroNoNegativo`, `EditarStockDialog`, `AjustarMinimoDialog`, `SolicitarPiezaDialog`, `MenuComponente`, `StockPage`, `DialogoAlmacen`, `useProveedoresComponentes`, `tienePedidos`, `useCrearProveedor`, `useEditarProveedor`, `useSetActivoProveedor`, `useBorrarProveedor`, `crearColumnasProveedores`, `claseFilaProveedor`, `CABECERAS_CSV_PROVEEDORES`, `filaCsvProveedor`, `MenuProveedor`, `NuevoProveedorDialog`, `EditarProveedorDialog`, `ProveedoresPage`, `enlacesStock`, `Enlace` se usan igual en todas las tareas. En el servidor, `ValorEntero`, `MSG_NOMBRE`, `MSG_NOMBRE_LARGO`, `MSG_DIVISA`, `MSG_TIENE_PEDIDOS`, `MSG_CANTIDAD`, `MSG_MINIMO` son consistentes entre Tasks 1-3 y los tests.

**Comprobado en la revisión previa** (antes "riesgo conocido"): `ComboNavy` es `role="combobox"` con `aria-label`, lista `role="listbox"` con el mismo nombre y opciones `<li role="option">` con un `<button>` que recibe el clic; `DataTable` pone `data-state="selected"` solo en la fila seleccionada y `aria-selected` solo si recibe `onSeleccionar`, y las clases de `filaClase` van después en `cn` (ganan en tailwind-merge); `abrirPanel` de `PanelNotificaciones.test.tsx` monta `<Campana />`, no el panel; los dos gráficos de Recharts usan tamaños fijos (sin `ResponsiveContainer`) y los tests solo afirman sobre HTML propio (testids y el `div role="img"`), nunca sobre el SVG; `test/setup.ts` ya define un stub de `ResizeObserver`; `DialogContent` pinta la ✕ ("Close", `sr-only`) después de los children y Radix enlaza sola la `DialogDescription` como descripción accesible.

**Revisión previa (lección del 3b).** Antes de la Task 1, un subagente revisa este plan contra la spec y el código (contrato `schema.d.ts`, `DataTable`, `ComboNavy`, `PanelNotificaciones.test.tsx`, `OpenApiContractTest`) buscando tests de contrato o de componentes que romperían la suite tal como están escritos.
