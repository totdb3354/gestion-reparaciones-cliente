# Sub-proyecto 4b — Pedidos, formularios de pedido y campana

**Fecha:** 2026-09-25
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra). Partición y decisiones comunes del sub-proyecto 4: [Almacén, Inventario y Revisión](2026-09-24-web-almacen-programa-design.md) (D1-D16, vinculantes aquí; en especial D3, D9, D10, D13 y D15). Antecesor directo: [Stock actual y Proveedores (4a)](2026-09-24-web-almacen-stock-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-pedidos`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`).

## 1. Objetivo

Sustituir el "Pendiente de migrar" de `/stock/pedidos` por la pestaña **Pedidos** del JavaFX: el toggle **Componentes | Otros**, la barra de filtros, las dos tablas con sus colores y badges por estado, el menú contextual con las transiciones de estado y sus diálogos, el CSV, y los cuatro formularios (**Nuevo pedido**, **Nuevo otro pedido**, **Editar pedido** de componentes y de otros). Con ello se activan los tres botones de pedir de la campana ("Pedir" en una alerta, "Pedir todas las piezas" y "Pedir piezas") y el ítem "Pedir" del menú de Stock actual abre el formulario en el sitio.

En el servidor sube lo que hoy solo aplica el cliente (D3): la máquina de estados y los rangos de recepción, la validación de alta y edición, el cálculo de `precioEur` con la tasa en el sentido correcto, dos endpoints de alta por lotes con `Idempotency-Key` que además marcan las solicitudes gestionadas (D10), y los nullables del contrato.

Con esto el supertécnico gestiona los pedidos de componentes de punta a punta desde la web. Entrega: web **v0.7.0**.

Por fuera queda como el JavaFX; las diferencias son las de la §10. Referencia: `hotfix/0.16.3` (solo lectura), con paridad verificada contra las capturas de la pestaña y el inventario de su código, guardados fuera del repo porque llevan datos reales del taller. Para Pedidos solo hay hoy cinco capturas de referencia; el resto se toma antes de la comparación (§9).

## 2. Fuera de alcance

- Inventario de teléfonos, lotes, envíos, suppliers, importador y Revisión: 4c, 4d y 4e.
- Cerrar por rol `GET /api/tipo-cambio/{divisa}` y `GET /api/componentes/gestionados` (D6): SP7.
- `ORDER BY` de cancelados al final en el servidor (inventario §23): el orden se hace en la web, como hoy en el cliente; el servidor no cambia sus listas.
- `GET /api/compras/en-camino`: sin consumidor en el JavaFX; no se usa.
- Alta y borrado de componente, mínimo master/slave, lock de slaves (D14, D8): backlog.
- Corregir el histórico de `precioEur`: comprobado contra la API de producción el 2026-09-25, no hay ningún pedido de componentes ni de otros en una divisa distinta de EUR (solo existe un proveedor en USD, sin pedidos). No hay nada que corregir.
- Marcado de solicitudes urgentes o preventivas desde fuera del lote (los PATCH sueltos de la campana siguen como en el sub-proyecto 2).

## 3. Decisiones de este sub-proyecto

| # | Decisión | Por qué |
|---|---|---|
| P1 | **El formulario de alta es un modal encima de la vista actual.** Un store en `shared/lib/formularioPedido.ts` guarda "qué formulario está abierto y con qué precarga"; un host `FormulariosPedido` montado en el shell lo pinta. Stock actual ("Pedir"), Pedidos ("Nuevo pedido" / "Nuevo otro pedido") y la campana (tres botones) lo abren llamando al store. El parámetro `?componente=` que 4a dejó en "Pedir" desaparece | Calco del `Stage` modal del JavaFX, que no saca al usuario de su vista; la campana vive en `taller` y no puede importar de `almacen` (regla de lint), así que el punto de entrada es compartido |
| P2 | **Editar un pedido recibido:** la web precarga la cantidad **pedida** y el servidor responde 422 si la cantidad cambia en un pedido `recibido`; proveedor, urgente, precio y divisa siguen editables | Hoy el cliente precarga la recibida bajo la etiqueta "Cantidad:" y la escribe en `CANTIDAD` sin tocar el stock: se pierde la pedida. En producción no hay ningún pedido con recibida distinta de la pedida, así que el JavaFX no dispara el 422 |
| P3 | **`precioEur` lo calcula el servidor en todas las escrituras** (POST y PUT sueltos de compras y de otros, y los dos lotes): `precioUnidad / tasa`, redondeado a 2 decimales; EUR → el propio precio. El campo `precioEur` de las peticiones se conserva en el contrato y se ignora | Frankfurter con `from=EUR&to=USD` devuelve USD por 1 EUR (1,1367 el 2026-09-25): el cliente multiplica donde debe dividir. Con el cálculo en el servidor el JavaFX 0.16.x queda corregido sin tocarlo; aditivo |
| P4 | **Toggle Componentes \| Otros = `TogglePill` por rutas** `/stock/pedidos` y `/stock/pedidos/otros`, una sola `PedidosPage` con prop `tipo`. Los cuatro filtros viven en un store compartido por los dos toggles; la selección de fila es un store por toggle y sobrevive al refresco (S4) | D9; el JavaFX comparte los controles de filtro entre las dos tablas |
| P5 | **Lotes:** `POST /api/compras/lote` y `POST /api/compras-otros/lote`, `Idempotency-Key` obligatoria (400 sin ella), una transacción por lote, la tasa se consulta antes de entrar en la transacción, la divisa de cada línea es la del proveedor y la respuesta devuelve los ids creados. El lote de compras acepta las solicitudes que originan las líneas y las marca `GESTIONADA` en la misma transacción (D10) | Hoy N líneas son hasta 2N peticiones sin transacción y el marcado son N PATCH sueltos; un fallo a mitad duplica al reintentar. Patrón de `POST /api/asignaciones/lote` |
| P6 | **Diálogos nativos → diálogos propios** (S5, D13): "Recepción parcial" y "Recibir unidades" sobre `DialogoAlmacen` con el error inline; los avisos `Alert WARNING` de los formularios ("Línea i: …", "Selecciona un proveedor.", 409 al guardar) pasan a una línea de error inline dentro del propio formulario, que sigue abierto | La web no tiene diálogos nativos; mismo patrón que 4a |
| P7 | **Textos demostrablemente falsos corregidos** (D13): la cabecera de "Recibir unidades" dice "Si introduces {restante}, el pedido se cerrará como recibido." (el cliente bloquea "o más"); la etiqueta de tasa del editor dice `"(1 {DIV} = {1/tasa:%.4f} €)"`; "Actualizado HH:mm" recarga la tabla visible (hoy siempre la de componentes); mientras no hay tasa el Total EUR muestra "—" en vez de calcular como si fuera EUR | Los demás textos se calcan tal cual, incluidos los que parecen raros (badge `en_camino`, "Cancelar pedido" / "Cancelar") |
| P8 | **Celdas de la tabla de líneas siempre editables** (inputs visibles) en vez del baile `Label` → `TextField` del `TableView`; el resto del formulario se calca (tamaños, colores, popup de componentes de 6 filas, símbolo `$`/`€`) | El "clic sobre la fila seleccionada para editar" es un artefacto de JavaFX sin equivalente en HTML |
| P9 | **`crearClavesIdempotencia` se mueve a `shared/lib`** y lo usan formulario de reparación y formularios de pedido | Lo mismo que se hizo con `useInteraccionesAbiertas` en 4a |
| P10 | **Ordenación por cabecera bloqueada** en las dos tablas (`ordenacion={false}`), como en 4a (S9) | D13 |

## 4. Servidor (rama `feature/web-pedidos`)

Todo es **aditivo**: ninguna respuesta que el JavaFX consuma cambia de forma; los guards nuevos coinciden con lo que el cliente ya aplica antes de llamar.

### 4.1 Guards de estado (409) en `CompraComponenteDAO` y `CompraOtroDAO`

Cada transición pasa a un `UPDATE … WHERE ID = ? AND ESTADO = '<estado exigido>'` y responde **409** si no actualiza ninguna fila, con el patrón y el estilo del `confirmar` actual ("El pedido ya no está pendiente"). El `checkUpdatedAt` se mantiene antes.

| Endpoint | Estado exigido | Mensaje del 409 |
|---|---|---|
| `PATCH …/confirmar` | `pendiente` | "El pedido ya no está pendiente" (existente) |
| `PATCH …/confirmar-recibido`, `…/confirmar-parcial`, `…/cancelar` | `en_camino` | "El pedido ya no está en camino" |
| `PATCH …/recibir-resto`, `…/confirmar-alterado` | `parcial` | "El pedido ya no está en recepción parcial" |
| `PATCH …/desrecibir` | `recibido` | "El pedido ya no está recibido" (además del 409 de stock insuficiente, que se mantiene) |
| `PUT /{id}` | `pendiente`, `en_camino` o `recibido` | "El pedido no se puede editar en su estado actual" |
| `DELETE /{id}` | `pendiente` | existente |

Las escrituras de `CompraComponenteDAO` que tocan stock siguen siendo `@Transactional`; `confirmarAlterado` gana el log `CONFIRMAR_ALTERADO` (hoy no loguea) en las dos entidades.

### 4.2 Rangos y validaciones (422)

Mensajes idénticos a los del cliente cuando existen:

- `confirmar-parcial`: `0 < cantidadRecibida < cantidad` → "La cantidad debe ser mayor que 0 y menor que {cantidad}."
- `recibir-resto`: `cantidadExtra > 0` → "La cantidad debe ser mayor que 0."; `(recibida ?? 0) + extra <= cantidad` → "No puedes recibir más de lo pedido. Faltan {restante} unidad(es)."
- `POST` y `PUT` de compras y de otros: `cantidad > 0` → "Cantidad no válida (debe ser > 0)."; `precioUnidad >= 0` → "Precio no válido."; concepto en blanco (otros) → "El concepto no puede estar vacío."; divisa fuera de `EUR`/`USD` → "Divisa no válida (EUR o USD)." (texto de 4a); componente inexistente o inactivo → "El componente no está activo."; proveedor inexistente o inactivo → "El proveedor no está activo."
- `PUT` de compras y de otros en `recibido` con `cantidad` distinta de la guardada → "No se puede cambiar la cantidad de un pedido recibido." (P2).
- Lotes (§4.4): "Añade al menos una línea."; por línea `i` (1-based) y en este orden: "Línea {i}: selecciona un componente." (componente nulo o inexistente) / "Línea {i}: el concepto no puede estar vacío." (otros), "Línea {i}: selecciona un proveedor.", "Línea {i}: el componente está desactivado.", "Línea {i}: el proveedor está desactivado.", "Línea {i}: la cantidad debe ser mayor que 0.", "Línea {i}: el precio no puede ser negativo."; una solicitud cuyo componente (resuelto al master) no tiene línea en el lote → "La solicitud no corresponde a ninguna línea del pedido."

Un 422 nunca escribe ni registra log.

### 4.3 `precioEur` y tasa (P3)

Servicio `ConversionEur` (`aEuros(precioUnidad, divisa)`): EUR → el precio; otra divisa → `precioUnidad / TipoCambioDAO.getTasa(divisa)`, `BigDecimal` con `HALF_UP` a 2 decimales. Lo usan `insertar` y `editar` de los dos controladores y los dos lotes; el `precioEur` de la petición se ignora. Si Frankfurter falla, `TipoCambioDAO` deja de lanzar `RuntimeException` (500) y responde **503** "No se pudo obtener el tipo de cambio de {DIV}. Inténtalo de nuevo." en `GET /api/tipo-cambio/{divisa}` y en las escrituras que lo necesiten (para el JavaFX sigue siendo un 5xx: "El servidor no está disponible…").

### 4.4 Lotes (P5)

`POST /api/compras/lote` y `POST /api/compras-otros/lote`, `@PreAuthorize("hasRole('SUPERTECNICO')")`, cabecera `Idempotency-Key` obligatoria (400 "Falta la clave de idempotencia", como asignaciones), `RegistroIdempotencia.ejecutar(idUsu, "compras-lote" | "compras-otros-lote", clave, peticion, escritura, trasEscribir)`.

- Petición de compras: `{ lineas: [{ idCom, idProv, cantidad, esUrgente, precioUnidad }], solicitudes: { urgentes: [idRc], preventivas: [idSol] } }` (`solicitudes` puede venir vacío). Petición de otros: `{ lineas: [{ idProv, concepto, cantidad, esUrgente, precioUnidad }] }`. Sin `divisa` ni `precioEur`: la divisa es la del proveedor y el EUR lo calcula el servidor.
- Respuesta de las dos: `{ idsCreados: [int] }` (en el orden de las líneas).
- Orden de trabajo: validar (§4.2) → resolver la divisa de cada proveedor y la tasa de cada divisa distinta (fuera de la transacción, como el lookup de IMEI en asignaciones) → `CompraLoteService.guardar` `@Transactional`: un `insertar` por línea (con el `resolveToMasterId` de siempre) y el marcado de cada solicitud con los métodos actuales de `ReparacionComponenteDAO` y `SolicitudStockDAO` (`GESTIONADA`) → `trasEscribir`: logs `CREAR_PEDIDO` / `CREAR_PEDIDO_OTRO` por línea con el texto de hoy y `GESTIONAR_SOLICITUD` / `GESTIONAR_SOLICITUD_STOCK` por solicitud.
- Cualquier excepción deshace el lote entero; el reintento con la misma clave y la misma petición devuelve la respuesta guardada sin escribir.

### 4.5 Contrato

`@Schema(nullable = true)` en `cantidadRecibida` y `fechaLlegada` de `CompraComponente` y `CompraOtro`; `precioEur` de `CompraInsertarRequest`, `CompraEditarRequest`, `CompraOtroInsertarRequest` y `CompraOtroEditarRequest` pasa a `Double` nullable (ignorado). Rutas y esquemas nuevos (`LoteCompras*`, `LoteComprasOtros*`) en `OpenApiContractTest`; `schema.d.ts` regenerado por el flujo offline.

### 4.6 Lo que ya existe y se reutiliza

`GET /api/compras`, `GET /api/compras-otros` (los tres roles), los siete `PATCH` y el `PUT`/`DELETE` de cada entidad (SUPERTECNICO), `GET /api/compras/cantidad-en-camino/{idCom}`, `GET /api/tipo-cambio/{divisa}` (`{value}`), `GET /api/proveedores?tipo=COMPONENTES` (la web ya lo consulta; los formularios filtran activos en cliente y no usan `/activos`), `GET /api/componentes/gestionados`, `GET /api/solicitudes?estado=PENDIENTE` y `GET /api/solicitudes-stock?estado=PENDIENTE`, `RegistroIdempotencia`, `ReparacionComponenteDAO.actualizarEstadoSolicitud`, `SolicitudStockDAO.actualizarEstado`. Cada uno se verifica contra el contrato antes de planificarlo.

## 5. Web

```
app/router.tsx                         /stock/pedidos → PedidosPage tipo="componentes"; /stock/pedidos/otros → tipo="otros"
app/shell/AppLayout.tsx                monta <FormulariosPedido />
shared/lib/formularioPedido.ts (+test) store: { tipo: 'compra' | 'otro'; precarga } | null; abrirNuevoPedido(precarga), abrirNuevoOtroPedido(), cerrar()
shared/lib/clavesIdempotencia.ts       movido desde modules/taller/formulario/
shared/lib/importes.ts (+test)         formatearImporte(n, simbolo): "12,50 €" (es-ES, 2 decimales); simboloDivisa
shared/lib/fechas.ts                   patrón 'dd/MM/yy HH:mm'
shared/styles/tokens.css               fila-pendiente-brd #C8961E, badge-pendiente-bg #FFF3D6, badge-pendiente-text #B26A00
modules/almacen/
├── estado.ts                          ultimaRutaStock: también lo fijan las dos rutas de Pedidos
├── stock/StockPage.tsx, filtros.ts    "Pedir" abre el modal en el sitio; ?componente=<id> → filtrosDesdePedidos + selección + scroll
└── pedidos/
    ├── PedidosPage.tsx                título, toggle, filtros, tabla, pie; consume ?estados&buscar (S8)
    ├── api.ts (+test)                 useCompras(tipo, {activo}); mutaciones de transición, editar y lote; invalidaciones
    ├── filtros.ts (+test)             FiltrosPedidos, ordenarCanceladosAlFinal, aplicarFiltrosPedidos, FILTROS_VACIOS
    ├── estado.ts                      stores: filtros (compartido), selección por toggle
    ├── reglas.ts (+test)              textoCantidad, unidadesFila, totalFila, marcaPrecioCero, entradasMenu(estado), validarParcial, validarResto, claseFila, estiloBadge
    ├── columnas.tsx (+test)           columnas de componentes y de otros, enlace Componente, CSV de cada tabla
    ├── BadgeEstadoPedido.tsx          badge radio 12, padding 3 10, "⚠" al lado
    ├── MenuPedido.tsx                 menú por estado (SUPERTECNICO)
    ├── CantidadDialog.tsx (+test)     "Recepción parcial" y "Recibir unidades"
    ├── confirmaciones.ts (+test)      títulos y textos de Cancelar, Borrar y Revertir (componentes y otros)
    ├── tasa.ts (+test)                useTasa(divisa): ['tipo-cambio', divisa], EUR = 1 sin llamada, staleTime 1 h
    └── formulario/
        ├── lineas.ts (+test)          reductor de líneas: añadir, quitar, cambiar campo; precargas (componente, alertas, solicitudes); validarLineas; cuerpoLote
        ├── conversion.ts (+test)      aEuros(precio, tasa) = precio / tasa; etiquetaTasa(divisa, tasa)
        ├── NuevoPedidoDialog.tsx      tabla de líneas de componentes
        ├── NuevoOtroPedidoDialog.tsx  tabla de líneas con Concepto
        ├── EditarPedidoDialog.tsx     editor de un pedido de componente
        ├── EditarOtroPedidoDialog.tsx editor de un otro pedido
        └── FormulariosPedido.tsx      host del shell: pinta el diálogo de alta que diga el store
modules/taller/notificaciones/         PanelNotificaciones y TarjetaAlerta: los tres botones activos; TOOLTIP_ALMACEN y BotonAlmacen desaparecen
```

Se reutilizan `DataTable` (`filaClase`, `menuFila`, `getRowId`, `seleccionada`, `onSeleccionar`, `pedirDesplazamiento`, `ordenacion`), `TogglePill`, `MultiSelect`/`textoMultiSelect`, `RangoFechas`, `ConfirmDialog`, `DialogoAlmacen`, `useErrorServidor`, `ComboNavy`, `CampoAutocompletar`, `EtiquetaActualizado`, `Checkbox`, `useRegistrarExportable` + `descargarCsv`, `useIntervaloRefresco`, `useInteraccionesAbiertas`, `AlertaProvider` y `mensajeDeError`, `crearStore`/`useStore`, `alertasOrdenadas` (campana). Tipos `CompraComponente` y `CompraOtro` exportados de `shared/api/client.ts` con `cantidadRecibida: number | null` y `fechaLlegada: string | null` (del contrato regenerado). Antes de cada tarea se busca en el repo lo que va a crear.

**Datos al abrir:** `['compras','componentes']` o `['compras','otros']` según el toggle (solo la visible se sondea); `['proveedores','COMPONENTES']` (la misma consulta que la pestaña Proveedores) para el filtro y los combos; `['componentes','gestionados']` para el autocompletar del formulario; `['tipo-cambio', divisa]` por divisa distinta de EUR que aparezca en las líneas.

## 6. Comportamiento

El inventario del código es la referencia de detalle. Aquí va lo que define la vista.

**Cabecera.** Título **"Pedidos"**; a la derecha el toggle **"Componentes" | "Otros"** (`TogglePill`, Componentes por defecto). Barra de filtros: **"Estado"** (`MultiSelect` con los cinco checks **"pendiente", "en camino", "parcial", "recibido", "cancelado"**, ninguno marcado al entrar; botón "Estado", el nombre del único marcado o **"N estados"**), **"Proveedor"** (`MultiSelect` con solo los proveedores activos; "Proveedor", el nombre o **"N proveedores"**), buscador con placeholder **"Buscar componente…"** en los dos toggles (calco), **"Desde:"** / **"Hasta:"** (`RangoFechas`), **"Limpiar filtros"** (desmarca estados, vacía proveedor, buscador y fechas; no toca el toggle ni la selección), y **"Nuevo pedido"** o **"Nuevo otro pedido"** según el toggle, solo para SUPERTECNICO. Los filtros se combinan con AND y sobreviven al cambio de toggle, de sección y a la vuelta desde Reparaciones (S2).

**Tabla de componentes.** Columnas **Pedido** (`dd/MM/yy HH:mm`, Madrid), **Componente** (enlace azul subrayado al pasar → `/stock?componente=<idCom>`), **Proveedor**, **Cant.** (`parcial` → "recibida/cantidad" o solo cantidad si la recibida es nula; `recibido` → la recibida si no es nula, si no la cantidad; resto → cantidad), **P.Unit.** (`"12,50 €"`, `$` en USD, el código en cualquier otra divisa; **"!"** ámbar negrita si `recibido` con precio 0), **EUR** (total = unidades × `precioEur`, con unidades = recibida en `recibido` si no es nula; **"!"** si `recibido` y total 0) y **Estado** (badge con `estado` tal cual: `pendiente`, `en_camino`, `parcial`, `recibido`, `cancelado`; **"⚠"** a la derecha si urgente y en_camino o parcial). Sin columna Div. (oculta en el JavaFX). Orden: el del servidor (fecha desc) con los cancelados al final, estable. Placeholder **"Sin pedidos"**. **Tabla de otros:** igual con **Concepto** (texto, sin enlace) en vez de Componente y placeholder **"Sin otros pedidos"**.

**Colores.** Barra izquierda de 8 px: `pendiente` ámbar `#C8961E`; `en_camino` urgente `#C07800`, normal sin barra; `recibido` verde `#3A7D44`; `parcial` violeta `#7B5EA7`; `cancelado` sin barra y opacidad 0,45 en toda la fila; seleccionada navy con textos claros (la opacidad de cancelado prevalece, como la de desactivado en Stock). Badges: `pendiente` `#B26A00` sobre `#FFF3D6`; `en_camino` urgente `#C07800` sobre `#FDEBC8`, normal `#586376` sobre `#E8EAF0`; `recibido` `#3A7D44` sobre `#C8E6C9`; `parcial` `#7B5EA7` sobre `#E8E0F7`; `cancelado` `#9E9E9E` sobre `#E0E0E0`; radio 12, padding 3/10, 11 px negrita.

**Pie.** **"Actualizado HH:mm"** a la derecha, clicable: recarga la tabla visible (P7).

**Llegada desde Stock (S8).** `/stock/pedidos?estados=pendiente,en camino,parcial&buscar=<tipo>`: al montar, Pedidos marca esos estados, pone el tipo en el buscador (proveedor y fechas intactos), selecciona la primera fila filtrada, la desplaza a la vista y limpia los parámetros de la URL con `replace`. Siempre en el toggle Componentes (§10).

**Vuelta a Stock.** El enlace Componente navega a `/stock?componente=<idCom>`: Stock desmarca OK, Bajo y Sin stock (conserva Desactivado), vacía el buscador, selecciona la fila del componente, la desplaza a la vista y limpia la URL.

**Menú contextual** (solo SUPERTECNICO; ADMIN y TECNICO sin menú), por estado y en este orden:
- `pendiente`: **"Confirmar pedido"** · ─ · **"Editar"** · **"Borrar"**
- `en_camino`: **"Recepción parcial"** · **"Confirmar recibido"** · ─ · **"Editar"** · **"Cancelar pedido"**
- `parcial`: **"Recibir resto"** · **"Cerrar sin resto"**
- `recibido`: **"Revertir a En camino"** · ─ · **"Editar"**
- `cancelado`: sin menú.

**Acciones sin diálogo:** Confirmar pedido, Confirmar recibido y Cerrar sin resto escriben al pulsar.

**Diálogos de cantidad** (`DialogoAlmacen`, P6):
- **"Recepción parcial"**: subtítulo **`"Pedido #{id} — {componente} ({cantidad} pedidas)"`**, etiqueta **"Cantidad recibida ahora:"**, campo vacío, botón "Confirmar". No numérico → **"Cantidad no válida."**; fuera de `0 < cant < cantidad` → **"La cantidad debe ser mayor que 0 y menor que {cantidad}."**.
- **"Recibir unidades"**: subtítulo **`"Pedido #{id} — {componente} (recibidas: {recibida}/{cantidad})"`** y, en segunda línea, **"Si introduces {restante}, el pedido se cerrará como recibido."** (P7); etiqueta **"Cantidad que llega ahora:"**, campo precargado con `restante`. `cant <= 0` → **"La cantidad debe ser mayor que 0."**; `recibidas + cant > cantidad` → **"No puedes recibir más de lo pedido. Faltan {restante} unidad(es)."**.
En otros, `{componente}` es el concepto.

**Confirmaciones** (`ConfirmDialog`):
- **"Cancelar pedido"** / **`"¿Cancelar el pedido #{id} de {componente}?"`** / botón **"Cancelar pedido"** (y el "Cancelar" de siempre: calco).
- **"Borrar pedido"** / **`"¿Borrar el pedido pendiente #{id} de {componente}?"`** / botón **"Borrar"**.
- **"Revertir a En camino"** / componentes: **`"¿Revertir el pedido #{id} de {componente} a En camino?"`** + **"Se descontarán {n} unidad(es) del stock."** + **"Recuerda revisar el stock tras la operación."** con `n = recibida ?? cantidad`; otros: solo la primera frase / botón **"Revertir a En camino"**.

**Formulario "Nuevo pedido"** (modal de 700 px, título **"Nuevo pedido"** en los tres modos; el título de ventana del JavaFX no existe en la web): tabla de líneas con columnas **Componente** (`CampoAutocompletar` sobre los componentes activos, incluidos los slaves de SKU compartido, placeholder **"Escribe componente..."**, filtro "contiene", Enter elige el primero, 6 filas visibles), **Proveedor** (`ComboNavy` con los activos, 8 filas; al elegir fija la divisa de la línea), **Cant.** (empieza en 1; solo enteros > 0), **P.Unit.** (empieza en `0,00`; coma o punto; ≥ 0; símbolo `$` si el proveedor es USD, `€` en otro caso), **Urg.** (checkbox), **Total EUR** (`precio / tasa × cantidad`, "—" mientras no hay tasa o falla) y la papelera que quita la línea sin confirmar. Placeholder **"Añade al menos una línea"**. Botones **"+ Añadir línea"** (añade, selecciona y desplaza), **"Cancelar"** (cierra sin preguntar) y **"Confirmar pedido"**. Validación en la línea de error del formulario, parando en el primer fallo: **"Añade al menos una línea."**, **"Línea {i}: selecciona un componente."**, **"Línea {i}: selecciona un proveedor."**, **"Línea {i}: la cantidad debe ser mayor que 0."**, **"Línea {i}: el precio no puede ser negativo."**. Guardado: un `POST /api/compras/lote` con `Idempotency-Key` (clave por cuerpo, `'compras:lote'`, `hecha` al terminar bien); éxito → cierra y recarga compras, stock y campana; 422 y 409 → su mensaje inline; otro error → **"Error al guardar: " + mensaje**, formulario abierto.

Modos de precarga (`abrirNuevoPedido`):
- **vacío** ("Nuevo pedido" de la pestaña): sin líneas.
- **componente** ("Pedir" de Stock actual, "Pedir" de una alerta): una línea con ese componente, cantidad 1, sin proveedor; si está desactivado, la línea va vacía (calco).
- **alertas** ("Pedir todas las piezas"): una línea por alerta en el orden de la campana, cantidad 1.
- **solicitudes** ("Pedir piezas"): relee las PENDIENTE urgentes y preventivas; con las dos listas vacías no abre nada (calco); agrupa por `idCom` (urgentes primero, en su orden; luego preventivas), cantidad = nº de solicitudes del componente; los componentes desactivados se omiten y el formulario avisa en su línea de información **"{n} solicitud(es) de componentes desactivados no se han añadido y siguen pendientes."** (D10). El lote lleva las solicitudes de los componentes que sigan teniendo línea al confirmar; si el usuario cancela, todas siguen PENDIENTE.

**Formulario "Nuevo otro pedido"**: igual con **Concepto** (texto libre, placeholder **"Escribe concepto..."**) en vez de Componente, sin autocompletar; validación **"Línea {i}: el concepto no puede estar vacío."** antes de proveedor, cantidad y precio; `POST /api/compras-otros/lote`; tras guardar recarga otros.

**Formulario "Editar pedido #{id}"** (520 px): **"Componente:"** (solo lectura) o **"Concepto:"** (texto, placeholder **"Descripción del pedido"**), **"Proveedor:"** (`ComboNavy` con los activos; si el del pedido está inactivo queda vacío, calco), **"Cantidad:"** (placeholder **"Ej. 10"**, precarga la cantidad **pedida**, P2), **"Urgente:"** (solo componentes; otros conserva el valor, calco), **"Precio unidad:"** + combo de divisa **EUR/USD** (independiente del proveedor, calco), **"Total EUR:"** = `precio / tasa × cantidad`; con divisa distinta de EUR añade **`"  (1 {DIV} = {1/tasa:%.4f} €)"`** (P7); mientras llega la tasa **"Obteniendo tasa…"**, si falla **"Error al obtener tasa"**; si precio o cantidad no parsean, "—". Botones **"Cancelar"** y **"Guardar"**. Validación inline en orden: **"El concepto no puede estar vacío."** (otros), **"Selecciona un proveedor."**, **"Cantidad no válida (debe ser > 0)."**, **"Precio no válido."**. `PUT` con `{idProv, cantidad, esUrgente, precioUnidad, divisa, updatedAt}` (y `concepto` en otros). 409 → **"El pedido fue modificado por otro usuario. Cierra y recarga los datos."** inline, formulario abierto; 422 → su mensaje inline; otro → "Error al guardar: …". Éxito → cierra y recarga.

**Campana.** "→ Ir a pedidos" y "Ver Stock Completo" siguen como en 4a. **"Pedir"** de una alerta, **"Pedir todas las piezas"** y **"Pedir piezas"** cierran el panel y abren el formulario con su modo. `TOOLTIP_ALMACEN` y `BotonAlmacen` desaparecen.

**Roles.** La pestaña, las dos tablas, los filtros, el CSV, los enlaces y "Actualizado" son de los tres roles; "Nuevo pedido", "Nuevo otro pedido", el menú, los formularios y los botones de la campana solo SUPERTECNICO (la campana ya es solo suya). Sin guard de ruta, como en 4a.

## 7. Guardado y recargas

Cada transición es una mutación con el endpoint de §4.6 y el cuerpo de hoy (`{updatedAt}`, `{cantidadRecibida, updatedAt}`, `{cantidadExtra, updatedAt}`; `DELETE` sin cuerpo). Al terminar, con éxito o error, se invalidan `['compras']`, `['componentes']` y la clave de la campana (`['notificaciones','componentes']`, literal como en 4a): el JavaFX recarga stock solo en algunas acciones; la web recarga siempre las tres (diferencia inocua a favor, §10). Los lotes y el `PUT` recargan lo mismo al cerrar. Con un menú, un diálogo o un formulario abiertos el refresco automático se congela (`useInteraccionesAbiertas`; el host de formularios avisa a través del store). El formulario de reparación no cambia de comportamiento al mover `crearClavesIdempotencia`.

## 8. Errores y refresco

- **409 en una transición** (salvo desrecibir): aviso **"Este pedido fue modificado por otro usuario. Los datos se han recargado."** y recarga (calco; incluye el 409 de Borrar y los 409 nuevos de estado).
- **409 en desrecibir:** el mensaje del servidor (stock insuficiente o estado) y recarga.
- **422 de parcial o resto:** inline en el diálogo, que sigue abierto (la web valida antes con los mismos textos).
- **422 y 409 de los formularios:** inline (§6).
- **503 de tasa:** en la vista previa, "—" / "Error al obtener tasa"; al guardar, el mensaje del servidor inline. Para ello el clasificador de errores de la web trata un **503 con mensaje** como error de negocio (solo lo emite nuestro backend); un 503 sin cuerpo (nginx) sigue siendo "sin conexión".
- **Fallo de carga de la lista:** política general (aviso, banner si es conexión).
- **Refresco:** 60 s conectado, 5 s con banner; recarga al volver a la pestaña; congelado con algo abierto. La selección se mantiene (S4).

## 9. Tests y verificación

- **Servidor:** matriz de estados de los siete PATCH, el PUT y el DELETE en las dos entidades (cada transición permitida escribe; cada prohibida da 409 sin tocar stock); rangos y validaciones de §4.2 (422 sin log); `ConversionEur` (EUR, USD divide y redondea, 503); lotes (400 sin clave, 422 por línea, atómico ante fallo en la segunda línea, misma clave misma petición → respuesta guardada sin insertar, marcado de solicitudes en la transacción, solicitud sin línea → 422, logs una sola vez); contrato con las dos rutas, los esquemas nuevos y los nullables. Suite completa con `mvn -q test` (`OpenApiContractTest` levanta el contexto).
- **Web:** `reglas.test`, `filtros.test` (AND de los cuatro, cancelados al final, textos de botón), `lineas.test` (añadir/quitar/cambiar, las cuatro precargas, agrupación de solicitudes, inactivos omitidos y avisados, validación en orden, cuerpo del lote y solicitudes que quedan), `conversion.test`, `importes.test`, `columnas.test` (formatos, "!", "⚠", enlace, dos CSV con sus cabeceras exactas, "Sí"/"No", coma decimal), `PedidosPage.test` (roles, toggle, menú por estado, diálogos y confirmaciones con sus textos, 409 genérico y de desrecibir, llegada con `?estados&buscar`, selección tras refresco, congelación), los cuatro formularios (precargas, validación, símbolo y total, tasa cargando/fallando, lote con clave, 422/409 inline, reintento con la misma clave), `formularioPedido.test` (store), campana (los tres botones abren el modo correcto; "Pedir piezas" sin pendientes no abre), `StockPage.test` ("Pedir" abre el modal en el sitio; `?componente=` aplica `filtrosDesdePedidos` y selecciona), `clavesIdempotencia` movido sin cambiar tests.
- **Trazabilidad:** el plan enlaza cada regla del inventario de Pedidos con el test que la cubre.
- **Smoke** Playwright contra producción, en serie, solo con OK del usuario y tras desplegar el servidor: entrar en Pedidos, crear un pedido de una línea con `E2E_SKU_PRUEBA`, precio 0 y proveedor de prueba creado por API, editarlo (cantidad 2) y borrarlo por el id que devuelve el lote; lo mismo con un otro pedido de concepto sintético; borrar el proveedor de prueba. No se confirma ni se recibe nada: el smoke no toca stock ni deja filas.
- **Capturas del JavaFX:** antes de la comparación, el usuario toma las de la §26 del inventario con el worktree `_ref-hotfix-0163` recreado y los scripts de `Apuntes/herramientas/paridad-capturas/`, anotando en `CAPTURAS-4b.md` las no reproducibles; los pedidos que cree para ello quedan en la lista de limpieza de la VDC.
- **Paridad antes del tag:** ficha `docs/paridad/pedidos.md` marcada contra las capturas `pedidos-*` y `form-*` (citadas solo por nombre) y **capturas de la web comparadas lado a lado antes de `v0.7.0`** (lección de 4a: antes del merge).

## 10. Diferencias y calcos

| Asunto | Decisión |
|---|---|
| Formulario de alta como modal en el sitio desde Stock, Pedidos y campana | **Calco** (P1); el `?componente=` de 4a se retira |
| Título de ventana "Nuevo pedido — alertas de stock" / "— solicitudes pendientes" | **Diferencia**: no hay título de ventana; el interno siempre "Nuevo pedido" (calco) |
| Editar en recibido: precarga la pedida y 422 si cambia | **Diferencia** (P2) |
| `precioEur` calculado en el servidor, tasa dividida | **Diferencia** (P3); corrige también al JavaFX |
| Alta por lotes con clave; marcado de solicitudes en la transacción | **Diferencia** (P5, D10) |
| Componente desactivado en "Pedir piezas": se avisa y su solicitud no se marca | **Diferencia** (D10) |
| Diálogos de cantidad y avisos de formulario propios e inline | **Diferencia** (P6) |
| Cabecera de Recibir unidades, etiqueta de tasa, "Actualizado" recarga la visible, "—" sin tasa | **Diferencia** (P7) |
| Celdas de líneas siempre editables | **Diferencia** (P8) |
| Ordenación por cabecera bloqueada | **Diferencia** (P10) |
| Selección mantenida en el refresco y refresco congelado con formulario abierto | **Diferencia** (S4, D4) |
| Recarga de compras, stock y campana entera tras cualquier acción | **Diferencia** inocua |
| El panel de la campana se cierra al pedir (el JavaFX lo deja abierto) | **Diferencia** inocua |
| "Pedir todas las piezas" sin alertas no hace nada; "Pedir piezas" se deshabilita mientras relee | **Calco** |
| Enter no confirma en los formularios de alta (sí en los editores) | **Diferencia** inocua |
| Un proveedor desactivado después del pedido hace que editarlo dé 422 aunque no se toque el proveedor | **Diferencia** (§4.2) |
| Llegada desde "En Camino" siempre al toggle Componentes | **Diferencia** (el JavaFX se queda en Otros si estaba ahí) |
| Filtro Estado y Proveedor como `MultiSelect` sin buscador | **Diferencia** ya aceptada en 4a |
| Fechas de "Desde/Hasta" tecleables | **Diferencia** ya aceptada en Historial |
| Chips de estado sin marcar por defecto; badge `en_camino` con guion bajo; "⚠" solo en camino/parcial | **Calco** |
| "Cancelar pedido" / "Cancelar" en la confirmación | **Calco** |
| Placeholder "Buscar componente…" también en Otros; filtro Proveedor solo activos | **Calco** |
| Divisa del editor independiente del proveedor; proveedor inactivo deja el combo vacío | **Calco** (inocuo: el EUR lo calcula el servidor) |
| Editor de otros sin "Urgente" | **Calco** |
| "Pedir piezas" con las dos listas vacías no hace nada | **Calco** |
| "Cancelar" del formulario cierra sin preguntar | **Calco** |
| Slaves de SKU compartido en el autocompletar (el servidor los resuelve al master) | **Calco** |
| CSV de otros (sin Urgente); "Cantidad" = pedida; Estado con guion bajo; coma decimal | **Calco** |
| TECNICO ve Pedidos sin acciones; `/tipo-cambio` sin rol | **Calco** (D6: SP7) |
| Columna Div. | **Calco** (oculta) |

Cualquier diferencia nueva que aparezca al comparar capturas se decide con el usuario, no sobre la marcha.

## 11. Criterios de cierre

1. `/stock/pedidos` y `/stock/pedidos/otros` muestran la pestaña como la ficha; "En Camino", el enlace Componente y "Pedir" de Stock funcionan en los dos sentidos.
2. Menú, diálogos, confirmaciones y los cuatro formularios se comportan como el inventario, con las diferencias de la §10 y ninguna más.
3. El servidor aplica la máquina de estados y los rangos (409/422), valida alta y edición, calcula `precioEur` con la tasa dividida en todas las escrituras y ofrece los dos lotes atómicos con clave y marcado de solicitudes.
4. Los cinco botones de la campana funcionan.
5. Suites en verde en los tres repos (el cliente JavaFX sin tocar) y smoke en verde.
6. Ficha marcada y capturas comparadas lado a lado antes del tag.
7. Desplegado en la VDC, servidor antes que web, con el contrato publicado idéntico al que consume la web. Push, merge, tag `v0.7.0` y despliegue, cada uno con el OK del usuario.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un guard de estado nuevo rompe un flujo del JavaFX | Los guards son exactamente las transiciones que ofrece el menú del cliente (inventario §7); la matriz de tests cubre las diez transiciones en las dos entidades |
| El 422 de P2 alcanza al JavaFX con un pedido recibido de recepción parcial | Hoy no existe ninguno en producción; si aparece, el JavaFX muestra "Error al guardar: …" y no corrompe la cantidad |
| Frankfurter caído bloquea las altas en USD | Solo afecta a líneas en USD; EUR no llama; el 503 tiene mensaje claro y el reintento con la misma clave vuelve a intentarlo (la entrada se borra al fallar) |
| El host del formulario en el shell se monta en rutas donde no procede | El host solo pinta cuando el store tiene valor; los tests de campana y Stock lo cubren; la sesión cerrada reinicia el store |
| `precioEur` ignorado confunde a quien lea el contrato | Campo nullable y documentado como ignorado en el `@Schema`; la web no lo manda |
| Pocas capturas del JavaFX de Pedidos hoy | La toma de capturas es un paso del cierre antes de la comparación, con la lista de §26 del inventario |
