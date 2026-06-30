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

## Progreso (actualizado 2026-06-30)

- **Task 1 — GlassDAO:** ✅ HECHA. Commit `a7b093b` (repo ProgramaReparaciones, rama `feature/separar-glass`). Compila.
- **Task 2 — Modal filtrado por tipo:** ✅ HECHA. Commit `6db20f6`. `FormularioReparacionController.cargarFilas` filtra por glass (deriva el tipo de `idAsignacion` `AG..` / `idRepEditar` `G..`); se eliminó el delimitador (glass y reparación ya no coexisten); `ReparacionDAO.getIncidenciaActivaPorImei` tiene overload con `tipo`. Compila.
- **Task 3 — Asignaciones unificada:** ✅ HECHA (compila + 109 tests verdes; pendiente smoke en preproducción). Tabla única A+AG+AP en `PendientesSuperTecnicoController` con columna **Tipo** (badge) y dispatch por tipo (reasignar/borrar/comentario/editar-modelo); filtros **Tipo** (Rep/Glass/Pulido) + **Estado** (renombrado); overload `ReparacionDAO.borrarIncidenciaPorImei(imei,"G")` para incidencias glass; helper testeable `tipoDe(idRep)`. Quitado el toggle `Reparaciones|Pulidos` y el panel de pulido de Asignaciones en **ambos** padres (SuperTécnico + **Admin**, que comparte el componente en modo solo-lectura). `PulidoSuperTecnicoController/View` quedan huérfanos hasta Task 4.
- **Task 4 — Modal con selector:** ⬜ PENDIENTE. (Devuelve el alta de pulido, ahora sin botón propio; añade selector `Reparación | Glass | Pulido`.)
- **Task 5 — Pendientes 3 pestañas:** ⬜ PENDIENTE.

Backend (Plan 1) ✅ HECHO, validado en preproducción (smoke 6/6 contra `api.fonestore.es`), mergeado a `main` y pusheado en el repo del servidor.

Para retomar: Task 4 (modal de alta con selector de tipo) en `PendientesSuperTecnicoController.abrirFormularioAsignacion`, reutilizando la pila rica (rep+glass) e integrando la sub-pila ligera de pulido (`PulidoSuperTecnicoController.abrirFormularioAsignacion`).

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
**Files (expandido contra el código real 2026-06-30):**
- `dao/ReparacionDAO.java`: añadir overload `borrarIncidenciaPorImei(imei, categoria)` (el server ya acepta `?tipo=`, "G"=glass; el cliente solo tenía la versión sin tipo → borraría la incidencia de reparación al borrar una de glass).
- `controllers/PendientesSuperTecnicoController.java`: enum `Tipo` + `tipoDe(idRep)` (AG→glass, AP→pulido, A→reparación); `GlassDAO`/`PulidoDAO`; `cargar()` une A+AG+AP; columna **Tipo** (badge); dispatch por tipo en reasignar técnico (pulido→`PulidoDAO`, rep/glass→`ReparacionDAO` por ID), borrar (pulido→`eliminarAsignacionPulido`; incidencia glass→`borrarIncidenciaPorImei(imei,"G")`), menú contextual (rep/glass: comentario+urgente+cliente; pulido: comentario+editar modelo), filtro **Tipo** nuevo + renombrar el filtro existente a **Estado**.
- `views/PendientesSuperTecnicoView.fxml`: columna Tipo + `MenuButton` filtroTipo + renombrar `filtroSolicitud` a "Estado".
- **Padres (super + admin)** — el componente `PendientesSuperTecnicoView` está incluido en `ReparacionViewSuperTecnico.fxml` **y** `ReparacionViewAdmin.fxml`: quitar el toggle `Reparaciones|Pulidos` y el sub-panel de pulido de Asignaciones en **ambos** FXML + limpiar el wiring (`togglePend*`, `pnlPend*`, `pulidoSuperTecnicoController`, `tgPend`) en `ReparacionControllerSuperTecnico.java` y `ReparacionControllerAdmin.java`. (Historial y Mis Pendientes conservan su toggle de pulido — son Plan 3 / Task 5.)
- El badge del sidebar "Asignaciones" suma los 3 tipos automáticamente (`getTotalItems()` ya cuenta la tabla unificada).
- **Nota intermedia:** al quitar el panel de pulido desaparece el botón "Nueva asignación de pulido"; el alta de pulido vuelve en Task 4 (modal con selector). El botón "Asignar reparación" se mantiene sin cambios en Task 3.
- Entregable: una tabla con los 3 tipos, columna Tipo y filtros Tipo+Estado.

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
