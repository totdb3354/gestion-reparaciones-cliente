# Clúster B — Aviso de asignación cruzada entre categorías

Fecha: 2026-07-01
Rama: `feature/aviso-asignacion-cruzada`
Origen: backlog post-Glass, clúster B (ítem 4).

## Problema

En el formulario de alta de reparación/glass (`FormularioReparacionController`,
compartido por ambos modos) hay un aviso "⚠ Este IMEI también está asignado a:
…". Hoy es **parcial**: llama a `reparacionDAO.getAsignacionesPorImei(imei)`,
cuyo endpoint servidor filtra **solo reparaciones**
(`ID_REP LIKE 'A%' AND NOT LIKE 'AP%' AND NOT LIKE 'AG%'`). Nunca considera
glass ni pulido, y por eso el aviso aparece de forma inconsistente entre el modo
reparación y el modo glass.

## Comportamiento objetivo

En el formulario (modo reparación **y** modo glass, bidireccional), listar
**todas** las asignaciones activas del IMEI en **cualquier** categoría distinta
de la asignación que se está editando (`idAsignacion`), agrupadas por categoría
y marcando "(tú)" cuando la asignación es del técnico logueado.

- Se incluyen asignaciones del propio técnico en otra categoría (máxima info de
  coordinación); solo se excluye la asignación en edición.
- Categorías: `A…` (no AG/AP) → Reparación, `AG…` → Glass, `AP…` → Pulido.

Formato del mensaje (una línea, en el `lblConflictoTecnico` existente):

> ⚠ Este IMEI también está asignado a — Reparación: Juan (tú) · Glass: Ana · Pulido: Luis

Reglas de formato:
- Solo aparecen las categorías con al menos una asignación.
- Dentro de una categoría, nombres separados por coma; categorías separadas por " · ".
- "(tú)" tras el nombre cuyo `idTec == Sesion.getIdTec()`.
- Si tras excluir la asignación en edición no queda ninguna, la fila del aviso
  se oculta (como hoy).

## Arquitectura (enfoque A)

Las asignaciones de las 3 categorías viven en la misma tabla `Reparacion`
(prefijos `A`/`AG`/`AP` en `ID_REP`), lo que permite una única query.

**Servidor** (`gestion-reparaciones-servidor`):
- Nuevo método DAO ligero `getAsignacionesActivasPorImei(imei)` en
  `ReparacionDAO`: `SELECT r.ID_REP, t.NOMBRE, r.ID_TEC FROM Reparacion r JOIN
  Tecnico t ON r.ID_TEC = t.ID_TEC WHERE r.IMEI = ? AND r.ID_REP LIKE 'A%' AND
  r.FECHA_FIN IS NULL`. **No** reutiliza `ASIGNACION_SELECT` (constante
  compartida por 3 métodos y rep-only; no se toca).
- Devuelve una lista de un DTO mínimo `AsignacionActiva(idRep, nombreTecnico, idTec)`.
- Nuevo endpoint GET en `ReparacionController`:
  `/api/reparaciones/imei/{imei}/asignaciones-activas`.
- Sin cambios de wiring de beans → riesgo de arranque mínimo, pero se
  **valida arranque/contexto** igualmente ([feedback_server_spring_startup]).

**Cliente** (`gestion-reparaciones-cliente`):
- Nuevo DTO/record espejo `AsignacionActiva` (o reutilizar uno mínimo) y método
  `ReparacionDAO.getAsignacionesActivasPorImei(imei)` que llama al endpoint.
- `FormularioReparacionController` (:130-147): sustituir la consulta actual por
  la nueva; filtrar `idAsignacion`; agrupar por categoría según prefijo de
  `idRep`; construir el mensaje con "(tú)" vía `Sesion.getIdTec()`.

## Fuera de alcance

- El modal de asignación del SuperTécnico (`PendientesSuperTecnicoController`)
  ya tiene su propio greying per-categoría (`tecnicosOcupados`) + pill
  "N asignados"; no se toca en este clúster.
- Clústeres C (columnas/acciones rep-glass-pulido) y D (asignación masiva).

## Verificación

- Servidor: test unitario de la query si encaja con los tests existentes;
  **arranque del contexto Spring** validado (la suite no cubre el wiring).
- Cliente: es UI JavaFX (sin cobertura). Smoke manual:
  - IMEI con asignaciones en varias categorías → el aviso las lista todas,
    agrupadas y con "(tú)" donde corresponde, tanto en modo reparación como
    glass.
  - IMEI sin otras asignaciones → la fila del aviso no aparece.
