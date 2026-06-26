# Diseño — Pedidos "Otros" (apuntes ajenos al stock)

**Fecha:** 2026-06-26
**Estado:** aprobado para plan de implementación

## 1. Motivación

En la vista de **Pedidos** (módulo Stock) hoy solo se pueden registrar pedidos de
**componentes**, que están atados a un `Componente` y, al recibirse, **suman al
stock**. Se necesita poder anotar **pedidos ajenos al inventario** — consumibles y
material que no es una pieza: alcohol isopropílico, cajas de envío, etc.

Estos "otros pedidos":

- Reutilizan los **mismos proveedores**.
- Reutilizan **urgente**, **precio** (unidad / divisa / EUR) y la **lógica de
  recepción** (llegado, parcial, recibir resto).
- **No están relacionados con stock**: no suman ni restan nada. Son únicamente un
  **apunte** de que algo se ha pedido y en qué estado está.

## 2. Alcance

**Dentro:**
- Nueva entidad/tabla `Compra_otro`, aislada del inventario.
- CRUD + máquina de estados completa (idéntica a pedidos de componente, **sin
  efecto en stock**).
- UI: toggle "Componentes | Otros" dentro de la vista de Pedidos + formulario
  "Nuevo otro pedido".
- Auditoría con códigos de acción propios (`*_OTRO`).

**Fuera (YAGNI, confirmado):**
- Estadísticas / informes de gasto sobre "otros".
- Exportación CSV de "otros".
- Cualquier efecto en stock, "en camino" o "último pedido" del componente.

## 3. Modelo de datos

### Tabla nueva `Compra_otro`

Espeja a `Compra_componente` **quitando** `ID_COM` y **añadiendo** `CONCEPTO`.

| Columna | Tipo | Notas |
|---------|------|-------|
| `ID_COMPRA_OTRO` | INT, PK, auto_increment | |
| `ID_PROV` | INT, FK → `Proveedor(ID_PROV)`, NOT NULL | único vínculo con el resto del esquema |
| `CONCEPTO` | VARCHAR(255), NOT NULL | texto libre (p. ej. "Alcohol isopropílico 1L") |
| `CANTIDAD` | INT, NOT NULL | |
| `CANTIDAD_RECIBIDA` | INT, NULL | para recepción parcial |
| `ES_URGENTE` | TINYINT(1), NOT NULL, default 0 | |
| `FECHA_PEDIDO` | DATETIME, NOT NULL, default NOW() | |
| `FECHA_LLEGADA` | DATETIME, NULL | |
| `PRECIO_UNIDAD_PEDIDO` | DOUBLE / DECIMAL | mismo tipo que en `Compra_componente` |
| `DIVISA` | VARCHAR | hereda la del proveedor por defecto |
| `PRECIO_EUR` | DOUBLE / DECIMAL | |
| `ESTADO` | ENUM(`pendiente`,`en_camino`,`parcial`,`recibido`,`cancelado`) | NOT NULL DEFAULT `pendiente` — idéntico a `Compra_componente` |
| `UPDATED_AT` | TIMESTAMP | misma definición que `Compra_componente.UPDATED_AT` (bloqueo optimista, truncado a segundos) |

### Cardinalidades

```
Proveedor 1 ───────── N Compra_otro        (FK ID_PROV)
Componente   ⊗   Compra_otro               (SIN relación — por diseño)
Log         ┄┄   Compra_otro               (auditoría, sin FK)
```

**Única FK: a `Proveedor`.** Sin relación con `Componente` (es lo que garantiza,
por construcción, que no pueda afectar al stock) ni FK a `Usuario` (el *quién*
queda en el `Log`, igual que en los pedidos actuales).

## 4. Servidor

Paralelo a la tríada existente `CompraComponente` / `CompraComponenteDAO` /
`CompraController`, pero **más simple** (sin stock).

### 4.1 Modelo `CompraOtro`
Como `CompraComponente` pero con `concepto` (String) en lugar de
`idCom` + `tipoComponente`.

### 4.2 DAO `CompraOtroDAO`
Copia de `CompraComponenteDAO` **eliminando**:
- Todas las sentencias `UPDATE Componente SET STOCK = STOCK ± ? …`.
- `resolveToMasterId(...)` (SKU compartido — no aplica).
- `getCantidadEnCaminoPorComponente(...)` y `getEnCamino()` (conceptos de stock).

Mantiene:
- Inserción (estado inicial `pendiente`).
- `editar`, `confirmar` (→ `en_camino`), `confirmarRecibido`, `confirmarParcial`,
  `recibirResto`, `confirmarAlterado` (cerrar sin resto), `cancelar`,
  `borrarPendiente`.
- `desrecibir` → revierte a `en_camino` **sin** el chequeo de "stock suficiente"
  (no hay stock que descontar; revertir siempre se permite).
- Bloqueo optimista vía `UPDATED_AT` / `checkUpdatedAt` (409 si conflicto).

### 4.3 Controller `CompraOtroController` — `/api/compras-otros`

| Método | Ruta | Rol | Acción |
|--------|------|-----|--------|
| GET | `/api/compras-otros` | SUPERTECNICO, ADMIN, TECNICO | listar |
| POST | `/api/compras-otros` | SUPERTECNICO | crear |
| PUT | `/{id}` | SUPERTECNICO | editar |
| PATCH | `/{id}/confirmar` | SUPERTECNICO | → en_camino |
| PATCH | `/{id}/confirmar-recibido` | SUPERTECNICO | → recibido |
| PATCH | `/{id}/confirmar-parcial` | SUPERTECNICO | → parcial |
| PATCH | `/{id}/recibir-resto` | SUPERTECNICO | resto |
| PATCH | `/{id}/confirmar-alterado` | SUPERTECNICO | cerrar sin resto |
| PATCH | `/{id}/cancelar` | SUPERTECNICO | → cancelado |
| PATCH | `/{id}/desrecibir` | SUPERTECNICO | revertir a en_camino |
| DELETE | `/{id}` | SUPERTECNICO | borrar pendiente |

**No** se crean `/en-camino` ni `/cantidad-en-camino/{idCom}` (son específicos de stock).

### 4.4 Integración con `Proveedor`
`ProveedorDAO.tienePedidos(idProv)` debe contar **también** en `Compra_otro`
(hoy solo mira `Compra_componente`), para no permitir borrar/desactivar un
proveedor aún referenciado por un "otro pedido" (evita huérfanos / violación de FK).

### 4.5 Arranque Spring
Se añaden beans nuevos (`@RestController` + `@Repository`). El servidor **no tiene
test de contexto Spring**, así que tras el wiring hay que **validar el arranque**
del contexto manualmente.

## 5. Cliente

### 5.1 Modelo + DAO
- `CompraOtro` (espejo de `CompraComponente`, con `concepto`).
- `CompraOtroDAO` (HTTP) espejo de `CompraDAO` apuntando a `/api/compras-otros`,
  **sin** los métodos de stock/en-camino.
- Se **reutiliza el enum `Estado`** (pendiente, en_camino, parcial, recibido,
  cancelado) tal cual.

### 5.2 UI — toggle dentro de Pedidos
En `pnlPedidos` (StockView.fxml), encima de la barra de filtros:

```
Pedidos                                       [ Componentes | Otros ]   <- toggle a la dcha del título
[Estado v][Proveedor v][Buscar...] Desde[] Hasta[]   ·margen·  [ Nuevo (otro) pedido ]
┌───────────────────────────────────────────────────────────────┐
│ tablaPedidos  (Componentes)   |   tablaOtros  (Otros)          │   <- visible/managed swap
└───────────────────────────────────────────────────────────────┘
```

- El toggle **Componentes | Otros** va **a la derecha de la fila del título**
  (título a la izquierda, `Region HBox.hgrow="ALWAYS"` que empuja, toggle a la
  derecha), igual que el toggle **Agrupado | Plano** del historial. Mismo
  `toggle-pill-left` / `toggle-pill-right`.
- **`tablaPedidos` (Componentes) queda intacta.**
- **`tablaOtros`** nueva, columnas: `Pedido` (fecha) · **`Concepto`** · `Proveedor`
  · `Cant.` · `P.Unit` · `EUR` · `Estado` (+ `Id` oculta). Se reutilizan los
  *cell factories* de cantidad/precio/EUR/estado y el coloreado de fila por estado;
  la celda `Concepto` es un `Label` simple (sin navegación a componente).
- **Botón de acción** cambia con el toggle: "Nuevo pedido" en Componentes,
  "Nuevo otro pedido" en Otros (al final del FlowPane de filtros, con margen).
- **Barra de filtros** aplica a la tabla activa: estado / proveedor / fecha
  iguales; el **buscador filtra por `Concepto`** en Otros. Se mantiene una
  `FilteredList` por cada tabla.
- **Menú contextual** por estado: misma lógica que componentes
  (`construirMenuContextual`) parametrizada para `CompraOtro`.
- **Polling / `recargar`**: al recargar el panel de Pedidos se refresca la tabla
  activa del toggle.

### 5.3 Formulario "Nuevo otro pedido"
Nuevo `FormularioOtroPedido` (FXML + controller) modelado sobre
`FormularioCompra`, con un cambio: el **selector de componente** se sustituye por
un **campo de texto `Concepto`**. Mantiene proveedor, cantidad, urgente y
precio/divisa/EUR, **reutilizando la conversión divisa→EUR** del formulario de
compra. El callback de guardado recarga solo la tabla de Otros (no toca stock).

### 5.4 Roles en UI
Igual que pedidos de componente: TECNICO/ADMIN **ven** la pestaña Otros;
**solo SUPERTECNICO** ve "Nuevo otro pedido" y el menú contextual de gestión
(mismo patrón que `btnNuevoPedido` se oculta hoy a no-supertécnico).

## 6. Máquina de estados

Idéntica a los pedidos de componente, **sin ningún efecto en stock** (solo cambia
`ESTADO` y fechas):

```
pendiente ──confirmar──> en_camino ──confirmar-recibido──> recibido
                            │                                  │
                            ├──confirmar-parcial──> parcial ───┤ (recibir-resto / cerrar sin resto)
                            └──cancelar──> cancelado
recibido ──desrecibir──> en_camino     (sin chequeo de stock)
pendiente ──borrar──> (eliminado)
```

## 7. Logs / auditoría

Códigos de acción **propios** (opción A), para una traza nítida y filtrable
aparte de los pedidos de stock:

`CREAR_PEDIDO_OTRO`, `EDITAR_PEDIDO_OTRO`, `CONFIRMAR_PEDIDO_OTRO`,
`RECIBIR_PEDIDO_OTRO`, `RECIBIR_PARCIAL_OTRO`, `RECIBIR_RESTO_OTRO`,
`CANCELAR_PEDIDO_OTRO`, `BORRAR_PEDIDO_OTRO`.

- El **detalle** usa `CONCEPTO: <texto>, PROVEEDOR: <nombre>, CANT: <n>` (en lugar
  de `COMPONENTE: …`).
- Paridad con los pedidos actuales: `confirmar-alterado` y `desrecibir` siguen el
  mismo criterio que hoy en componentes (las que no registran log, no lo registran).
- **Visor de logs enriquecidos**: registrar las etiquetas/iconos de estos nuevos
  códigos donde se mapean las acciones a su presentación.

## 8. Casos límite

- **Conflicto de edición concurrente** → 409 (`StaleDataException`), mismo manejo
  que pedidos.
- **Borrar/desactivar proveedor** referenciado por un "otro pedido" → bloqueado
  por `tienePedidos` ampliado (§4.4).
- **Validación de formulario**: `Concepto` no vacío; `Cantidad` > 0.
- **Recepción parcial**: `cantidadRecibida` ≤ `cantidad`.
- **Revertir** (desrecibir): siempre permitido (no hay stock).

## 9. Despliegue / migración

- Crear la tabla `Compra_otro` en MariaDB **antes** de desplegar el código del
  servidor (añadir el `CREATE TABLE` + FK a los scripts SQL — encaja con la rama
  `chore/sql-scripts`).
- Validar arranque del contexto Spring tras añadir los beans nuevos (§4.5).

## 10. Testing

- Hoy no hay tests que cubran `Compra_componente` (ni cliente ni servidor), así que
  no se introduce regresión sobre cobertura existente.
- Verificación mínima: arranque del servidor + prueba manual del flujo completo
  (crear → confirmar → parcial → resto / cerrar → cancelar → revertir) comprobando
  que **el stock no se altera** en ningún paso.
- (Opcional, futuro) tests de integración sobre la rama `feature/tests-integracion`.
