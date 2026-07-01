# Separar Glass — Plan 3: Cliente, vistas (Historial 3-way + Agrupado)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **Estado: esqueleto** — expandir cada tarea a pasos bite-sized (con código exacto) justo antes de ejecutar.

**Goal:** Reorganizar las vistas: Historial plano con toggle de 3, nuevo apartado "Agrupado" por IMEI (3 tipos) como componente compartido, y filtro de logs completo.

**Depende de:** Plan 1 (API) y Plan 2 (`GlassDAO`, modal filtrado).

**Tech Stack:** JavaFX, FXML.

## Progreso (actualizado 2026-07-01)
- **Task 4 — Filtro de logs:** ✅ HECHA (commit `c16dd40`; compila + LogControllerTest verde). `TIPOS_ACCION` incluye las 7 acciones glass + las 5 de pulido que faltaban (verificadas contra los literales del servidor).
- **Tasks 1-2-3 (Historial 3-way + Agrupado compartido):** ⬜ PENDIENTES. Bloque acoplado y grande (Task 2 = extraer `GrupoImei`/maestro-detalle de los 3 controladores de rol a `AgrupadoController`/`AgrupadoView` compartidos). Recomendado hacerlo en una tanda enfocada.
- **Task 5 — Exports/contadores:** ⬜ PENDIENTE (cierre).

Plan 2 completo y **validado en runtime** (smoke 3 roles OK, 2026-07-01). Matices menores de UI/UX de Plan 2 pendientes de pulir al final.

## Global Constraints
- Historial: toggle `Reparaciones | Glass | Pulido`, **siempre plano** (quitar `Agrupado|Plano`).
- "Agrupado": nueva entrada del sidebar en **los 3 roles**, componente único `fx:include`.
- Detalle del Agrupado: R + G + P del IMEI por orden cronológico, con columna Tipo.
- Nombre del apartado: "Agrupado".

---

### Task 1: Historial 3-way plano
**Files:** Modify los 3 FXML de rol (`ReparacionViewSuperTecnico.fxml`, `…Admin.fxml`, `…Tecnico.fxml`) y los 3 controladores.
- Toggle de pulido (2) → toggle de 3 (`Reparaciones | Glass | Pulido`); panel Glass reutiliza la tabla/columnas de Reparaciones cargando `getHistorialGlass`.
- Quitar el toggle `Agrupado | Plano` del Historial (la agrupada se va al apartado nuevo); el Historial queda en modo plano.
- Mantener filtros (técnico, cliente, pieza→glass/marco/otro en glass, fechas, incidencias).
- Entregable: Historial con 3 pestañas planas.

### Task 2: Componente compartido `Agrupado`
**Files:** Create `…/views/AgrupadoView.fxml` + `…/controllers/AgrupadoController.java`; Modify `…/models/GrupoImei.java`.
- Extraer de los 3 controladores la lógica de agrupado por IMEI (`GrupoImei`, modos `MAESTRO/DETALLE`, drill-down) a un controlador único.
- `GrupoImei` agrega los 3 tipos del IMEI (cargando reparación + glass + pulido); resumen por tipo en la fila maestro; detalle = R+G+P por orden cronológico con columna Tipo.
- Parametrizar por origen de datos (todos vs solo del técnico) para servir a los 3 roles.
- Mantener la columna "Revisión logística" (solo supertécnico) en el maestro.
- Entregable: componente Agrupado funcionando aislado.

### Task 3: Integrar "Agrupado" en el sidebar de los 3 roles
**Files:** Modify los 3 FXML de rol (añadir 4ª entrada de sidebar `Agrupado` + panel con `fx:include AgrupadoView`) y los 3 controladores (mostrar/ocultar panel, badge/landing sin cambios).
- Orden del sidebar: Asignaciones · Pendientes · Historial · Agrupado.
- Entregable: los 3 roles tienen el apartado.

### Task 4: Filtro de logs completo
**Files:** Modify `…/controllers/LogController.java` (`TIPOS_ACCION`).
- Añadir las acciones de glass (`CREAR_ASIGNACION_GLASS`, `COMPLETAR_GLASS`, `EDITAR_GLASS`, `ELIMINAR_GLASS`, `ELIMINAR_ASIGNACION_GLASS`, `MARCAR_INCIDENCIA_GLASS`) **y** las de pulido que hoy faltan (`CREAR_ASIGNACION_PULIDO`, `COMPLETAR_PULIDO_LOTE`, `ACTUALIZAR_ASIGNACION_PULIDO`, `ELIMINAR_ASIGNACION_PULIDO`, `ELIMINAR_PULIDO`).
- Entregable: el filtro del visor lista todas las acciones.

### Task 5: Exports y contadores con glass
**Files:** Modify lo necesario en los controladores (CSV de cada vista, contadores/badges).
- CSV de Glass y Agrupado exportan su contenido visible.
- Contadores ("N pendientes", etc.) incluyen glass donde proceda.
- Entregable: paridad de glass con reparación/pulido en exports y contadores.

---
## Self-review (al expandir)
Cubrir del spec: secciones D (historial + agrupado), H (logs), casos borde (CSV, filtro pieza). Verificar que el agrupado compartido no rompe el comportamiento por rol.
