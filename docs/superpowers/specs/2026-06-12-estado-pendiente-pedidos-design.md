# Estado "pendiente" en pedidos — Diseño

**Fecha:** 2026-06-12
**Item de backlog:** Item 4 (UAT junio 2026).

## Objetivo

Dar a los pedidos (compras de componentes) un **estado inicial antes de "en camino"**: el supertécnico va anotando durante el día los pedidos que hará, y al final del día los **confirma** (uno a uno) para que pasen a "en camino" y entren en el flujo normal de recepción. Mientras está sin confirmar, el pedido **no cuenta como "en camino"** en ninguna lógica de stock ni de solicitudes, y se puede **borrar** (no "cancelar", porque aún no se pidió nada).

## Estado actual (contexto)

Hoy un pedido **nace directamente `en_camino`** (`CompraComponenteDAO.insertar` fija `ESTADO='en_camino'`). El enum de estados es `{ en_camino, recibido, parcial, cancelado }`. El ciclo de vida y las acciones (menú contextual, solo supertécnico) son:

- `en_camino`: Recepción parcial · Confirmar recibido · Editar · Cancelar pedido
- `parcial`: Recibir resto · Cerrar sin resto
- `recibido`: Revertir a En camino · Editar
- `cancelado`: sin acciones

La cantidad "en camino" del stock y el badge "en camino" de las solicitudes se calculan en consultas que filtran por `ESTADO IN ('en_camino','parcial')` o `ESTADO = 'en_camino'`:
- `ComponenteDAO` (columna "En camino" del stock): `SUM(CASE WHEN cc.ESTADO IN ('en_camino','parcial') ...)`.
- `CompraComponenteDAO.getCantidadPendientePorComponente`: `SUM(... ) WHERE ESTADO IN ('en_camino','parcial')`.
- `ReparacionDAO` (badge "en camino" de solicitudes en el listado): `WHERE cc.ESTADO = 'en_camino'`.
- `ReparacionComponenteDAO` (indicador "en camino" de la fila en el modal): `WHERE cc.ESTADO = 'en_camino'`.

**Incoherencia de naming a corregir:** en la API/DAO la palabra "pendiente" se usa hoy con el sentido de **"pendiente de llegar" = en tránsito** (`getPendientes()` devuelve `en_camino`; `getCantidadPendientePorComponente()` suma `en_camino`+`parcial`; endpoints `/pendientes` y `/cantidad-pendiente`). Eso choca con el concepto nuevo. Se renombra (ver más abajo) para dejar "pendiente" libre para el estado nuevo.

> Nota: mi `crear_bd.sql` reconstruido en un commit anterior puso por error `'pendiente'` en el enum de `Compra_componente` en lugar del real `'en_camino'`. Este diseño lo corrige.

## Diseño

### 1. Naming — refactor de coherencia

Se renombra lo que hoy se llama "pendiente" pero significa "en camino", para que `pendiente` pase a designar el estado nuevo. Queda un ciclo intuitivo: **`pendiente → en_camino → {parcial, recibido} | cancelado`**.

| Hoy (significa *en camino*) | Renombrado |
|---|---|
| `CompraComponenteDAO.getPendientes()` (servidor) | `getEnCamino()` |
| `CompraComponenteDAO.getCantidadPendientePorComponente()` (servidor) | `getCantidadEnCaminoPorComponente()` |
| `CompraController.getPendientes()` + `@GetMapping("/pendientes")` | `getEnCamino()` + `@GetMapping("/en-camino")` |
| `CompraController.getCantidadPendiente()` + `@GetMapping("/cantidad-pendiente/{idCom}")` | `getCantidadEnCamino()` + `@GetMapping("/cantidad-en-camino/{idCom}")` |
| `CompraComponenteDAO.getPendientes()` (cliente, **sin usar**) | `getEnCamino()` + URL `/api/compras/en-camino` |
| `CompraComponenteDAO.getCantidadPendientePorComponente()` (cliente) | `getCantidadEnCaminoPorComponente()` + URL `/api/compras/cantidad-en-camino/` |
| Variable local `pendiente` en `StockController` (~líneas 524-541) | `enCamino` |

El cuerpo de los métodos NO cambia (siguen filtrando por `en_camino`/`parcial`); solo cambia el nombre. El cliente solo tiene **un llamador real** (`StockController` para la columna/gráfico "En camino"); el otro método cliente está sin usar.

Las consultas internas de `ComponenteDAO`, `ReparacionDAO` y `ReparacionComponenteDAO` usan los literales `'en_camino'`/`'parcial'` y **no cambian**: el nuevo estado `pendiente` queda excluido de toda la lógica de stock/solicitudes automáticamente.

### 2. Modelo de estados

Nuevo valor `pendiente` en el enum, primero en el ciclo:

- **BD** (`Compra_componente.ESTADO`): `ENUM('pendiente','en_camino','parcial','recibido','cancelado')`.
- **Cliente** (`CompraComponente.Estado`): `{ pendiente, en_camino, recibido, parcial, cancelado }`.

### 3. Ciclo de vida y operaciones

- **Crear**: `insertar` pasa a fijar `ESTADO='pendiente'` (antes `'en_camino'`). Todo pedido nuevo nace `pendiente`. No cambia nada más del formulario de creación.
- **Confirmar pedido** (`pendiente → en_camino`): operación nueva. Entra en el flujo normal de recepción.
- **Borrar** (solo `pendiente`): DELETE de la fila. Sin impacto en stock (un pedido nunca toca stock hasta que se recibe).
- **Editar** (cantidad/proveedor/precio): se reutiliza el formulario de edición existente; el estado sigue `pendiente`.
- **Cancelar**: NO aplica a `pendiente` (no se pidió nada). Sigue existiendo solo para `en_camino`.

**Fecha del pedido:** se **mantiene `FECHA_PEDIDO` = fecha de creación del borrador** (no se resetea al confirmar). Es lo más simple y conserva la coherencia con el tipo de cambio (`PRECIO_EUR`) capturado al crear. Como el flujo es anotar y confirmar el mismo día, en la práctica coinciden.

### 4. Lógica de stock / solicitudes — sin cambios

Como `pendiente` no aparece en ninguno de los conjuntos `('en_camino')` / `('en_camino','parcial')`, un pedido `pendiente`:
- **No** suma a la cantidad "en camino" del stock.
- **No** dispara el badge/indicador "en camino" de las solicitudes (ni en el listado ni en la fila del modal).

Solo al **confirmarlo** (pasa a `en_camino`) empieza a contar. No hay que modificar ninguna de esas consultas.

### 5. UI (StockController, solo supertécnico)

- **Menú contextual, caso `pendiente`**: `Confirmar pedido` · `Editar` · `Borrar` (este último con diálogo de confirmación). Sin "Cancelar".
- **Chip de filtro nuevo "pendiente"**, activo por defecto junto a "en camino" y "parcial" (para que los pedidos pendientes sean visibles y se puedan confirmar). Se añade `"pendiente"` al array de chips y a la selección por defecto.
- **Color de fila y badge propios** para `pendiente` (estilo "borrador": ámbar suave, para distinguirlo del gris de `en_camino`). Se añaden constantes `FILA_PEDIDO_PENDIENTE_BG` / `FILA_PEDIDO_PENDIENTE_BRD` a `Colores`. El `switch` exhaustivo del `rowFactory` obliga a añadir el caso `pendiente` (no compila sin él).
- El indicador ⚠ de urgente se muestra también para pedidos `pendiente` urgentes.

### 6. Componentes afectados

**Servidor:**
- `sql/crear_bd.sql` — enum de `Compra_componente.ESTADO` a `('pendiente','en_camino','parcial','recibido','cancelado')` (corrigiendo además el `'pendiente'`↔`'en_camino'` mal puesto).
- `sql/` — script de migración para la BD viva: `ALTER TABLE Compra_componente MODIFY COLUMN ESTADO ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL;` (añade `pendiente`; no afecta filas existentes).
- `CompraComponenteDAO` — `insertar` con `'pendiente'`; renombres del refactor; métodos nuevos `confirmar(idCompra, updatedAt)` y `borrarPendiente(idCompra)`.
- `CompraController` — renombres de endpoints; endpoints nuevos `PATCH /api/compras/{id}/confirmar` y `DELETE /api/compras/{id}` (rol `SUPERTECNICO`, con log `CONFIRMAR_PEDIDO` / `BORRAR_PEDIDO`).
- `docs/api_contract` — reflejar los renombres y los endpoints nuevos.

**Cliente:**
- `CompraComponente.Estado` — añadir `pendiente`.
- `CompraComponenteDAO` — renombres del refactor; métodos nuevos `confirmar(pedido)` y `borrar(pedido)` (HTTP; 409 → `StaleDataException`).
- `StockController` — caso `pendiente` en menú contextual, colores, badge y chips; handlers `confirmarPedido()` y `borrarPedido()`; renombre de la variable local.
- `Colores` — constantes `FILA_PEDIDO_PENDIENTE_BG/_BRD`.

### 7. Detalle de las operaciones nuevas (servidor)

- `confirmar(idCompra, updatedAt)`:
  ```sql
  UPDATE Compra_componente SET ESTADO='en_camino'
   WHERE ID_COMPRA=? AND ESTADO='pendiente'
  ```
  Con check optimista (`checkUpdatedAt`, patrón existente) y comprobación de filas afectadas: 0 filas → 409 (el pedido ya cambió de estado).
- `borrarPendiente(idCompra)`:
  ```sql
  DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'
  ```
  0 filas afectadas → 409 (ya no era `pendiente`; p. ej. otro usuario lo confirmó). El guard `AND ESTADO='pendiente'` impide borrar un pedido ya en curso.

## Riesgos

- **Migración del enum en la BD viva**: el `ALTER` debe incluir todos los valores actuales además de `pendiente`, o se perderían datos. Mitigación: el enum nuevo es superconjunto del actual.
- **Carrera confirmar/borrar**: dos supertécnicos sobre el mismo pedido. Mitigación: guard por `ESTADO='pendiente'` + filas afectadas → 409.
- **Despliegue coordinado**: el refactor cambia rutas de endpoints; cliente y servidor deben actualizarse juntos (ya es el caso). El endpoint `/pendientes` está sin usar en el cliente, así que el riesgo real se limita a `/cantidad-pendiente` → `/cantidad-en-camino`.

## Pruebas (manual)

1. Crear un pedido nuevo → aparece como `pendiente` (chip "pendiente", color ámbar), **no** suma a "En camino" del stock ni activa el badge "en camino" de una solicitud del mismo componente.
2. Editar el pedido `pendiente` (cantidad/proveedor/precio) → sigue `pendiente`.
3. Borrar el pedido `pendiente` (con confirmación) → desaparece; stock intacto.
4. Confirmar el pedido `pendiente` → pasa a `en_camino`; ahora **sí** suma a "En camino" y activa el badge "en camino" de la solicitud.
5. Sobre un `en_camino` el menú NO ofrece "Confirmar pedido" ni "Borrar" (sí "Cancelar", "Recibir…", etc.), como hasta ahora.
6. Intentar borrar/confirmar un pedido que otro usuario ya confirmó → conflicto (409) controlado, refresco de la tabla.
7. Columna/gráfico "En camino" del stock sigue funcionando tras el renombre del endpoint (`/cantidad-en-camino`).

## Fuera de alcance

- Confirmar o borrar **en bloque** / multiselección (por ahora solo menú contextual, una a una).
- Recalcular el tipo de cambio (`PRECIO_EUR`) al confirmar un pedido creado en un día distinto.
- Cualquier cambio en el flujo de `en_camino`/`parcial`/`recibido`/`cancelado` más allá de los renombres.
