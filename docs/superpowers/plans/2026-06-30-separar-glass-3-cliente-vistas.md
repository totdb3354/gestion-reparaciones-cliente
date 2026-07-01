# Separar Glass — Plan 3: Cliente, vistas (Historial 3-way + Agrupado)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **Estado: esqueleto** — expandir cada tarea a pasos bite-sized (con código exacto) justo antes de ejecutar.

**Goal:** Reorganizar las vistas: Historial plano con toggle de 3, nuevo apartado "Agrupado" por IMEI (3 tipos) como componente compartido, y filtro de logs completo.

**Depende de:** Plan 1 (API) y Plan 2 (`GlassDAO`, modal filtrado).

**Tech Stack:** JavaFX, FXML.

## Progreso (actualizado 2026-07-01)
- **Task 4 — Filtro de logs:** ✅ HECHA (commit `c16dd40`; compila + LogControllerTest verde). `TIPOS_ACCION` incluye las 7 acciones glass + las 5 de pulido que faltaban (verificadas contra los literales del servidor).
- **Task 2 — Componente Agrupado compartido:** ✅ HECHA (compila + 113 tests verdes; smoke runtime PENDIENTE hasta integrar Task 3). Desglose:
  - `utils/TipoTrabajo` nuevo: enum (Rep/Glass/Pul) + `desde(idRep)` + etiqueta/estilo de badge. Migrado `PendientesSuperTecnicoController` y su test (antes tenían el enum/`tipoDe` privados). Commit refactor aparte.
  - `models/GrupoImei` ampliado: agrega los 3 tipos, `getCountRep/Glass/Pul` + `getResumenTipos()` ("2 Rep · 1 Glass", omite ceros). Test unitario nuevo (`GrupoImeiTest`).
  - `controllers/AgrupadoController` + `views/AgrupadoView.fxml`: maestro/detalle portado de SuperTécnico, SIN modo plano ni filtro de pieza. Detalle cronológico (orden `fechaAsig`) con columna Tipo. Parametrizado por `Rol`: SUPER = todos los datos + revisión logística + menú edición (editar/borrar/incidencia + obs/cliente); ADMIN = todos, solo lectura; TECNICO = solo sus trabajos (vía endpoints `PorTecnico` con `Sesion.getIdTec()`), solo lectura. Carga = unión de `getReparacionesResumen`/`getHistorialGlass`/`getHistorialPulido` (o sus `PorTecnico`).
  - API pública del componente para Task 3: `configurar(Rol)`, `cargar()`, `resetarModo()`.
- **Smoke runtime del Agrupado (Tasks 2+3):** ✅ OK en preproducción (3 roles, 0 excepciones, 2026-07-01). Ajustes post-smoke commiteados: ocultar "Id Rep. Anterior" en pulido (es el id de la asignación AP, no reincidencia); Revisión logística **visible en los 3 roles, editable solo supertécnico**; anchos de columna igualados al Historial.
- **Task 3 (integrar Agrupado en sidebar de los 3 roles):** ✅ HECHA (compila + 113 tests; smoke OK). 4ª entrada "Agrupado" con `fx:include AgrupadoView` en los 3 FXML; cada controlador cablea el include: `configurar(Rol.X)` en `initialize`, `cargar()` al mostrar el panel, `resetarModo()` al salir, refresco en `recargar()` cuando visible. Roles: SuperTecnico→SUPERTECNICO, Admin→ADMIN, Tecnico→TECNICO. Nota: el Agrupado nuevo **coexiste** con el toggle `Agrupado|Plano` viejo del Historial hasta que Task 1 lo quite.
- **Task 1 (Historial 3-way plano):** ⬜ PENDIENTE (quitar toggle Agrupado|Plano; toggle Rep|Glass|Pul; el panel Glass reutiliza tabla cargando `getHistorialGlass`).
- **Task 5 — Exports/contadores:** ⬜ PENDIENTE (cierre; incluye CSV del Agrupado, que quedó fuera del componente por ahora).

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
