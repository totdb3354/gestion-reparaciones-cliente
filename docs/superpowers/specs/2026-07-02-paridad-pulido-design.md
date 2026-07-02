# Cluster C — Paridad de columnas/acciones en pulido

Fecha: 2026-07-02
Rama: `feature/paridad-pulido` (repo padre + gitlink servidor)
Origen: backlog post-Glass, cluster C (ítems 5, 6, 7).

Objetivo transversal: elevar pulido a paridad con reparación/glass en la
columna "Asignado por", el marcado de cliente al asignar, y la edición desde
"Mis pendientes".

## Item 5 — "Asignado por" en pulido (servidor + cliente)

Hoy las queries de pulido del servidor no traen `NOMBRE_TEC_ASIGNA` (rep/glass
sí), aunque el dato (`ID_TEC_ASIGNA`) se guarda al asignar. Además la
reasignación de pulido no actualiza `ID_TEC_ASIGNA`, así que "Asignado por"
debe pasar a nombrar al último SuperTécnico que reasigna.

**Servidor** (aditivo, retrocompatible; `RESUMEN_MAPPER` ya lee `NOMBRE_TEC_ASIGNA`
en un try/catch):
- Añadir a `ASIGNACION_PULIDO_SELECT` y `HISTORIAL_PULIDO_SELECT`:
  `ta.NOMBRE AS NOMBRE_TEC_ASIGNA` + `LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC`.
- `actualizarAsignacionPulido`: nuevo parámetro **opcional** `Integer idTecAsigna`.
  Cuando el técnico cambia (reasignación) y `idTecAsigna != null`, actualizar
  `ID_TEC_ASIGNA` — igual que `actualizarAsignacion` (rep/glass). Si el cliente
  no lo envía (cliente antiguo) → null → se salta (comportamiento actual
  preservado). El controller lee `idTecAsigna` del cuerpo (campo opcional).

**Cliente:**
- `PulidoDAO.actualizarAsignacionPulido`: añadir `idTecAsigna` al cuerpo PATCH.
- `PendientesSuperTecnicoController:194` (reasignación de pulido): pasar
  `Sesion.getIdTec()` como `idTecAsigna`.
- Añadir columna "Asignado por" (ligada a `getNombreTecnicoAsigna`) en
  `PulidoTecnicoView` (Mis pulidos pendientes) y `HistorialPulidoView`. En el
  panel de Asignaciones (tabla unificada) la columna ya existe y se rellena sola
  al devolver el servidor el dato.

**Verificar en implementación:** que el registro `P%` de historial conserve
`ID_TEC_ASIGNA` al completar; si no lo conserva, la columna de historial saldría
vacía y habría que propagarlo en el INSERT de completado (fuera de alcance si no
se conserva; se documenta el hallazgo).

## Item 6 — Cliente por IMEI en el pane de pulido (cliente puro)

El pane de alta de pulido (`construirPulidoPane`) hoy solo tiene técnico +
comentario. Añadir un selector de cliente por fila/IMEI, con paridad rep/glass:
por IMEI, **no se arrastra** entre IMEIs. Reutiliza `SelectorClienteDialog` y
`clienteDAO.getActivos()`. Al guardar el lote de pulido, para cada IMEI con
cliente elegido, escribir el cliente del teléfono con
`telefonoDAO.actualizarCliente(imei, idCli, telefonoUpdatedAt)` (endpoint de
teléfono existente). Sin cambios de servidor.

## Item 7 — "Editar" en Mis pendientes de pulido (cliente, solo SuperTécnico)

`PulidoTecnicoController` (Mis pulidos pendientes) hoy no tiene menú contextual.
Añadir uno **gateado por `Sesion.esSuperTecnico()`** (oculto para el técnico
normal, consistente con el borrado de pulido añadido en cluster A y con cómo
rep/glass reservan la edición al SuperTécnico). Ítems, reutilizando diálogos y
DAOs ya usados en el panel de Asignaciones:
- **Editar comentario** (reusa el editor de comentario / `actualizarAsignacionPulido`
  con el mismo técnico → no altera `ID_TEC_ASIGNA`).
- **Editar modelo** (`abrirSelectorModelo` / `telefonoDAO.actualizarModelo`).
- **Editar cliente** (`SelectorClienteDialog` / `telefonoDAO.actualizarCliente`).
- **Copiar celda** (como en las demás tablas).

Reasignar técnico se mantiene en el panel de Asignaciones (no se añade aquí).

## Reparto y despliegue

- Servidor solo en item 5 (2 SELECT + fix de reasignación, todo aditivo/opcional
  → **retrocompatible**, se despliega antes que el cliente).
- Items 6 y 7 son cliente puro.
- Rama por repo: `feature/paridad-pulido` en el gitlink servidor y en el repo
  padre. Merge servidor primero (con OK del usuario), luego cliente.

## Verificación

- Servidor: validar arranque del contexto Spring ([feedback_server_spring_startup])
  + comprobar la query. Cambios aditivos/opcionales, riesgo de wiring nulo.
- Cliente (UI JavaFX, sin cobertura): smoke manual —
  - Columna "Asignado por" poblada en Mis pulidos, Historial y Asignaciones.
  - Tras reasignar un pulido a otro técnico, "Asignado por" muestra al
    SuperTécnico que reasignó.
  - Selector de cliente en el pane de pulido escribe el cliente del teléfono
    por IMEI.
  - Menú "Editar" (comentario/modelo/cliente) en Mis pulidos aparece solo para
    el SuperTécnico y no para el técnico normal.
