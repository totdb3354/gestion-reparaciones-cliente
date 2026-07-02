# "Sin cliente" en el modal de asignación + sincronización por IMEI

Fecha: 2026-07-02
Estado: **APROBADO, sin implementar**.
Origen: detalle detectado al validar Cluster D · Fase 1 (colas rep/glass).
Depende de: Fase 1 (usa `pilaRep`/`pilaGlass` y el detalle sin `tgEnt`).

## Contexto y problema

El cliente de un teléfono vive en la fila `Telefono` (por IMEI); rep, glass y
pulido leen/escriben esa misma fila, así que a nivel de BD **ya es único y
consistente por IMEI**. Quitar el cliente (dejar "sin cliente") **ya funciona**
en "Editar cliente" de la tabla de Asignaciones, en el Agrupado y en el menú de
Pulido, todos vía `SelectorClienteDialog` (opción "— Sin cliente —", sentinel
`-1`) + `TelefonoDAO.actualizarCliente(imei, null, updatedAt)` (UPDATE directo,
NULL-capable). **Eso no se toca.**

El único sitio donde NO se puede dejar "sin cliente" es el modal **"Asignar
trabajos"** (`abrirFormularioAsignacion`), porque su guardado por lotes persiste
con el upsert:

```sql
INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = COALESCE(?, ID_CLI)
```

`COALESCE(?, ID_CLI)` con `?` = null **preserva** el cliente anterior. Efectos:
- **Detalle rep/glass:** no ofrece "sin cliente" (usa un autocompletado inline
  propio, no `SelectorClienteDialog`) y, aunque borres el texto, al guardar se
  conserva el cliente → imposible quitarlo.
- **Panel de pulido:** sí muestra "— Sin cliente —" (usa `SelectorClienteDialog`)
  pero el guardado con COALESCE ignora el null → el cliente anterior se queda en
  silencio. Bug.

## Objetivo (diferencia observable respecto a hoy)

Dentro del modal de asignación:
1. Poder dejar un IMEI **realmente "sin cliente"** en rep/glass y en pulido.
2. En rep/glass, **borrar/dejar a medias el texto no quita el cliente**: al
   perder el foco vuelve al cliente comprometido. Quitar solo ocurre eligiendo
   "— Sin cliente —".
3. El cliente de un IMEI queda **sincronizado en vivo** entre las colas del modal
   (rep/glass/pulido) cuando el mismo IMEI aparece en varias.

Fuera de alcance: la tabla, el Agrupado y el menú de pulido (ya funcionan);
cualquier cambio de la semántica de `actualizarCliente`.

## Diseño

### 1. Tri-estado del cliente por entrada

Añadir `boolean sinCliente` a `EntradaAsignacion` y a `FilaPulido`. Estados:

| Estado | `cliente` | `sinCliente` | Significado |
|---|---|---|---|
| Cliente real | ≠ null | false | asignar ese cliente |
| Sin cliente (explícito) | null | true | dejar el IMEI sin cliente |
| No tocado | null | false | no cambiar lo que haya en BD |

Invariante: si `cliente != null` ⇒ `sinCliente = false`.

### 2. Persistencia — upsert con flag explícito (toca servidor)

El upsert actual no puede fijar NULL sin pisar los clientes de las entradas "no
tocadas". Se añade una variante que fija `ID_CLI` exacto solo cuando el usuario
lo pidió.

**Servidor** (`gestion-reparaciones-servidor`, gitlink; validar arranque —
[[feedback_server_spring_startup]]):
- `TelefonoDAO.insertar(imei, modelo, idCli, boolean clienteExplicito)`:
  - `clienteExplicito=false` → `ID_CLI = COALESCE(?, ID_CLI)` (comportamiento
    actual; "no tocado" preserva).
  - `clienteExplicito=true` → `ID_CLI = ?` (fija exacto, incluido NULL → limpia).
  - `MODELO = COALESCE(?, MODELO)` en ambos casos.
- Endpoint: extender el de alta de teléfono (`TelefonoController.insertar`) con un
  campo opcional `clienteExplicito` en `ImeiRequest`, **default false**
  (retrocompatible: los clientes viejos y las llamadas sin el campo mantienen la
  semántica COALESCE).

**Cliente** (`TelefonoDAO`): método/overload equivalente que envía el flag.

**Guardado del modal** (`btnGuardar`): por cada entrada rep/glass y cada
`FilaPulido`:
- `sinCliente` → `insertar(imei, modelo, null, true)`.
- `cliente != null` → `insertar(imei, modelo, idCli, false)` (COALESCE con
  no-null fija igual).
- no tocado → `insertar(imei, modelo, null, false)` (preserva).

Descartada la alternativa client-only (reusar `actualizarCliente` con
`updatedAt`): frágil para IMEIs recién escaneados sin fila previa y con riesgo de
fallo de bloqueo optimista.

### 3. Detalle rep/glass — opción "— Sin cliente —" + restaurar al perder foco

El picker inline es `tfCliente` + `ListView<Cliente> clientesFiltrados` sobre
`todosClientes` (autocompletado con popup).

- **Sentinel:** prepend de una fila "— Sin cliente —" al desplegable (un
  `Cliente` centinela con `idCli = -1`, o manejo aparte en la celda). Siempre
  visible arriba (al menos con texto vacío). Elegirla ⇒ `clienteSel = <sin>`,
  `tfCliente` muestra "— Sin cliente —".
- **Estado comprometido:** hoy el listener de texto hace `clienteSel = null` en
  cuanto el texto no coincide, con lo que al borrar se pierde el "anterior".
  Guardar el último estado confirmado aparte (p.ej. `clienteComprometido`:
  Cliente | SIN | null) y, en el handler de foco-perdido, si el texto no resuelve
  a un cliente exacto ni al sentinel, **restaurar** el texto y `clienteSel` a
  `clienteComprometido` (en vez de dejarlo vacío). Confirmar (`confirmarCliente`)
  actualiza `clienteComprometido`.
- `cargarEntrada` pinta el estado de la entrada: cliente → nombre; `sinCliente`
  → "— Sin cliente —"; ninguno → vacío.
- `asignarActual` deriva `e.cliente`/`e.sinCliente` del estado comprometido.

### 4. Panel de pulido

`construirPulidoPane` ya usa `SelectorClienteDialog.elegir(...)` que devuelve
`-1` para "sin cliente". Al recibir `-1`: `fila.cliente = null;
fila.sinCliente = true;` y el botón muestra "— Sin cliente —". Al recibir un id
real: `fila.sinCliente = false`. El guardado usa el flag (sección 2).

### 5. Sincronización en vivo por IMEI dentro del modal

Helper `propagarCliente(String imei, Cliente cliente, boolean sinCliente)` que
recorre `pilaRep`, `pilaGlass` y `lotePulido` y aplica el mismo
`cliente`/`sinCliente` a toda entrada/fila con ese IMEI (excluida la de origen).
Se invoca al confirmar cliente en rep/glass (`asignarActual` o el confirm del
picker) y en el callback del picker de pulido. Si la entrada sincronizada es la
que está cargada en el detalle, refrescar el campo visible.

## Riesgos
- **Servidor:** cambio de wiring del endpoint/DTO → validar arranque y contrato
  retrocompatible (campo opcional, default false). Repo servidor es gitlink;
  bump del SHA al integrar.
- **Sentinel `-1` en el desplegable inline:** no debe colar como cliente real en
  la precarga (`todosClientes.filter(idCli==...)`); `-1` no matchea ids reales.
- **Restaurar-al-blur:** no romper el flujo actual de selección exacta ni la
  precarga de BD que rellena el cliente existente.
- **Propagación:** evitar bucles/recursión (aplicar sin re-disparar el confirm).

## Validación
Sin tests de UI → **smoke manual**: en el modal, (a) rep/glass elegir "— Sin
cliente —" y guardar → el IMEI queda sin cliente en BD; (b) escribir texto a
medias y perder foco → vuelve al cliente anterior; (c) pulido "— Sin cliente —"
→ realmente lo quita; (d) mismo IMEI en Rep y Glass → cambiar cliente en una se
refleja en la otra; (e) entrada "no tocada" con cliente previo en BD → se
conserva. Confirmar que la tabla/Agrupado/menú-pulido siguen igual.
Ver [[project_plan_testing]].

## Notas de proceso
- Rama propia (`feature/…`), **posterior a Fase 1** (o basada en su rama). Merge
  con OK del usuario ([[feedback_merge_confirmacion]]).
- Anclajes: guardado `btnGuardar`; picker inline `tfCliente`/`clientesFiltrados`
  (~:1119-1210); `cargarEntrada`/`asignarActual`; `construirPulidoPane` (~:829,
  picker :905-913); `SelectorClienteDialog` (util, sentinel `-1`);
  `TelefonoDAO.insertar` (cliente y servidor).
