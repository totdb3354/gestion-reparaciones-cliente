# Borrar desde "Pendientes" propias (solo supertécnico) — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

En la vista de "Pendientes" propias, el **supertécnico** podrá borrar una asignación directamente desde ahí, con la **misma papelera y el mismo flujo** que ya existe en el panel de Asignaciones. El **técnico normal**, que comparte ese mismo panel, **no** ve la papelera.

## Contexto

- El panel "Pendientes" propias usa `PendientesTecnicoController`, compartido por el técnico normal (su vista) y por el supertécnico (`misPendientesController`, embebido en `ReparacionViewSuperTecnico`).
- Hoy su columna de acción `cAccion` muestra solo el botón **"Añadir reparación"** (abre el formulario). No hay borrado.
- El borrado de asignaciones ya existe en `PendientesSuperTecnicoController.cAccion` (panel Asignaciones): icono `/images/borrar.png` (25×25), `ConfirmDialog.mostrar(...)` sin motivo, y según el tipo: `reparacionDAO.borrarIncidenciaPorImei(imei)` (incidencia) o `reparacionDAO.eliminarAsignacion(idRep)` (normal), seguido de `cargar()` + callback de actualización.
- Detección de rol disponible: `com.reparaciones.Sesion.esSuperTecnico()`.
- El borrado de **asignaciones** no pide motivo (decisión previa del proyecto; solo las reparaciones lo piden).

## Cambio

Archivos: `PendientesTecnicoController` + `PendientesTecnicoView.fxml`.

La papelera va en una **columna dedicada aparte** (`cBorrar`), no en la misma celda que "Añadir reparación" — igual que en el panel de Asignaciones, donde la papelera es su propia columna.
- FXML: añadir `<TableColumn fx:id="cBorrar" text="" prefWidth="45"/>` tras `cAccion`.
- Controller: declarar `cBorrar`. Si `Sesion.esSuperTecnico()`, su `cellFactory` muestra un `ImageView` con `/images/borrar.png` (25×25, cursor hand) centrado; si no es supertécnico, ocultar la columna (`cBorrar.setVisible(false)`).
- Handler del icono, replicando el de Asignaciones:
  - `desc = "El técnico dejará de verla en su lista de pendientes" + (esIncidencia ? " y la incidencia se marcará como no activa en la tabla principal." : ".")`
  - `ConfirmDialog.mostrar("Borrar asignación " + idRep, desc, "Borrar asignación", () -> { ... })`.
  - Dentro: si `rep.isEsIncidencia()` → `reparacionDAO.borrarIncidenciaPorImei(rep.getImei())`; si no → `reparacionDAO.eliminarAsignacion(rep.getIdRep())`. Luego `cargar()` y, si hay callback de actualización (`onCerrar`), invocarlo para refrescar badges del super. `catch (SQLException)` → mostrar error.
- El técnico normal (no super) ve la celda como hasta ahora (solo "Añadir reparación").

## Alcance

- Solo cliente, solo `PendientesTecnicoController`, solo pendientes de **reparación**.
- **Fuera de alcance:** pendientes de pulido (`PulidoTecnicoController`), el técnico normal (sin cambios), servidor y BD (los endpoints `eliminarAsignacion`/`borrarIncidenciaPorImei` ya existen).

## Testing

UI sin tests automáticos; validación manual:
1. Como **supertécnico**, en "Pendientes" propias: aparece la papelera junto a "Añadir reparación"; al borrar una asignación normal → confirma, se elimina, la lista y los badges se actualizan.
2. Borrar una que sea **incidencia** → usa `borrarIncidenciaPorImei` (la incidencia queda no activa).
3. Como **técnico normal**, en sus pendientes: la papelera **no** aparece; "Añadir reparación" sigue igual.

## Despliegue

Solo recompilar y distribuir el cliente. Sin servidor ni BD.
