# Separar Glass — Plan 2: Cliente, flujo glass (alta + completar)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **Estado: esqueleto** — expandir cada tarea a pasos bite-sized (con código exacto) justo antes de ejecutar, contra el estado real del código tras el Plan 1.

**Goal:** Poder crear y completar trabajos de glass de punta a punta desde el cliente: alta unificada con selector, modal de piezas filtrado y completado por IMEI.

**Depende de:** Plan 1 (API de glass: `/api/glass/*` + endpoints de reparación reutilizables por ID + dedup con `?tipo=`).

**Tech Stack:** JavaFX, Java HttpClient, Gson.

## Global Constraints
- Glass ≈ reparación: reutilizar al máximo el modal y la pila existentes.
- Selector del modal: `Reparación | Glass | Pulido`. Rep+glass comparten pila; pulido sub-pila.
- Dedup de la pila por `(IMEI, tipo)`; el alta pasa `tipo` al chequeo del servidor.
- Etiqueta de tipo en UI: "Glass".

---

### Task 1: `GlassDAO` (cliente)
**Files:** Create `…/cliente/.../dao/GlassDAO.java`
- Espejo de `PulidoDAO` para lo propio de glass: `getAsignacionesGlass(idTec?)`, `getHistorialGlass(idTec?)`, `getAsignacionesGlassPorImei`, `getHistorialGlassPorImei`, `insertarAsignacionGlass(imei, idTec, comentario, urgente)` → `POST /api/glass/asignaciones`.
- Completar/editar/borrar glass **reutilizan** `ReparacionDAO` (operan por ID `G…`/`AG…`); no duplicar.
- Ajustar la llamada de dedup para pasar `?tipo=G` donde el cliente comprueba "¿ya asignado a este técnico?".
- Entregable testeable: las listas de glass cargan desde la API.

### Task 2: Modal de reparación filtrado por tipo
**Files:** Modify `…/controllers/FormularioReparacionController.java`
- Añadir un modo/tipo (`REPARACION` | `GLASS`) al abrir el modal. En `cargarFilas` filtrar: glass → solo filas `g`, `mc`, `otro`; reparación → todas menos `g`/`mc` (mantener `otro`).
- `abrir(...)` / `abrirEditar(idRep, ...)` derivan el tipo del prefijo (`AG`/`G` → glass; `A`/`R` → reparación).
- Conservar el delimitador visual y toda la maquinaria (modelo, solicitudes, stock, borrador).
- Entregable: al completar una `AG`, el modal solo ofrece glass/marco/otro y genera `G`.

### Task 3: Asignaciones unificada (tabla + columna Tipo + filtro)
**Files:** Modify `…/controllers/PendientesSuperTecnicoController.java` + `…/views/PendientesSuperTecnicoView.fxml` (y el FXML padre `ReparacionViewSuperTecnico.fxml`: quitar el toggle `Reparaciones|Pulidos` de la sección Asignaciones).
- Cargar `A` + `AG` + `AP` en una sola tabla; añadir columna **Tipo** (badge) y filtro por tipo.
- El badge del sidebar "Asignaciones" suma los 3 tipos pendientes.
- Editar/eliminar fila operan por ID (reutilizan endpoints).
- Entregable: una tabla con los 3 tipos y su filtro.

### Task 4: Modal de alta con selector de tipo
**Files:** Modify `…/controllers/PendientesSuperTecnicoController.java` (`abrirFormularioAsignacion`)
- Añadir selector `Reparación | Glass | Pulido` arriba del modal.
- Rep y glass: **misma pila** rica (modelo/cliente/técnico/comentario); cada entrada lleva su tipo (default = selector). Al guardar, crea `A` o `AG` (vía `ReparacionDAO`/`GlassDAO`).
- Pulido: sub-pila ligera (técnico+comentario), integra el flujo de `PulidoSuperTecnicoController.abrirFormularioAsignacion`; al guardar crea `AP`.
- Dedup de la pila por `(IMEI, tipo)`.
- Entregable: un único modal da de alta los 3 tipos por lotes.

### Task 5: Pendientes 3 pestañas + completar glass
**Files:** Modify `…/controllers/PendientesTecnicoController.java` (+ panel equivalente del supertécnico) y los FXML de pendientes; añadir toggle `Reparaciones | Glass | Pulido`.
- Pestaña Glass: lista `AG` del técnico; clic → modal glass → guarda `G` (descuenta stock).
- Pulido sin cambios (lote). El badge "Pendientes" incluye `AG` propias.
- Entregable: el técnico completa glass por IMEI desde su pestaña.

---
## Self-review (al expandir)
Cubrir del spec: secciones C (alta), E (completar), F (modal). Verificar firmas con Plan 1 (`/api/glass/*`, `?tipo=`).
