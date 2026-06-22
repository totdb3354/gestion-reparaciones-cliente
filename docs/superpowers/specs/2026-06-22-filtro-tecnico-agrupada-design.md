# Filtro de técnico en la vista agrupada del historial — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

En el historial de admin y supertécnico, el filtro de técnico pasa a estar disponible en la **vista agrupada** (modo MAESTRO), hoy oculto ahí. Al filtrar por uno o varios técnicos, se muestran los IMEIs donde **al menos uno** de ellos intervino (OR). Al abrir un IMEI (modo DETALLE), se muestran **todas** las reparaciones del IMEI: arriba las de los técnicos filtrados y, debajo y atenuadas, las del resto de técnicos.

Es el mismo patrón "destacado arriba / resto opaco" que ya tiene la vista del técnico, pero con la selección guiada por el **filtro de técnico** en vez de por el usuario logueado.

## Por qué solo admin y supertécnico

El técnico solo ve sus propias reparaciones, así que un filtro "por técnico" no le aporta nada (ya tiene el opaco fijo de "lo mío vs lo de otros" de la feature anterior). El admin y el supertécnico ven el historial de todos los técnicos, por lo que el filtro es lo que les permite acotar por persona.

## Estado actual

- `ReparacionControllerSuperTecnico` y `ReparacionControllerAdmin` ya tienen `MultiSelectComboBox<Tecnico> filtroTecnico` y `Set<Integer> idsTecFiltro`.
- `aplicarFiltros()` tiene ramas por modo (MAESTRO, DETALLE, PLANO):
  - **PLANO**: ya aplica `idsTecFiltro` (muestra solo las reparaciones del técnico). **No cambia.**
  - **DETALLE**: hoy aplica `idsTecFiltro` y muestra **solo** las del técnico filtrado. **Cambia.**
  - **MAESTRO**: hoy **no** aplica `idsTecFiltro`, y el filtro está oculto (`adaptarFiltrosMaestro()` hace `filtroTecnico.setVisible(false)`). **Cambia.**
- Ningún rowFactory de Super/Admin tiene mecanismo de opacidad. La vista del técnico sí: un `Set<String> idsAjenas` + `setOpacity(idsAjenas.contains(id) ? 0.45 : 1.0)`.

## Cambios

Aplican por igual a `ReparacionControllerSuperTecnico` y `ReparacionControllerAdmin`.

### 1. Vista agrupada (MAESTRO)
- Mostrar el filtro de técnico: en `adaptarFiltrosMaestro()` poner `filtroTecnico` visible/gestionado (quitar el `setVisible(false)`/`setManaged(false)`).
- Filtrar a nivel de grupo: al construir los grupos (`buildTablaItems`), si `idsTecFiltro` no está vacío, incluir solo los IMEIs cuyo grupo tenga **al menos una** reparación con `idTec ∈ idsTecFiltro`. El grupo conserva **todas** sus reparaciones (no se descartan las de otros técnicos).

### 2. Vista detalle (DETALLE)
- Dejar de filtrar las reparaciones por `idsTecFiltro`: mostrar **todas** las del IMEI (`imeiDetalle`).
- Reordenar: primero las reparaciones con `idTec ∈ idsTecFiltro`, luego el resto.
- Atenuar el resto: registrar los IDs de las reparaciones del resto en un `Set<String> idsAjenas` nuevo y aplicar `setOpacity(0.45)` en el rowFactory (añadir el mecanismo, replicando el de la vista del técnico).
- Si `idsTecFiltro` está vacío: mostrar todas normales, sin reordenar ni atenuar (comportamiento actual).
- Contador (`lblNavCount`): "X de filtrados + Y de otros" cuando hay filtro y hay ajenas; "X reparaciones" cuando no.

### 3. Mecanismo de opacidad
- Añadir `Set<String> idsAjenas` (campo) en ambos controllers.
- En el `rowFactory` de `tablaReparaciones`, en `updateItem`: cuando la fila es una `ReparacionResumen`, aplicar `setOpacity(idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0)`; en otros casos (GrupoImei, vacío) opacidad 1.0.
- Limpiar `idsAjenas` al resetar modo y al recomputar el detalle.

## Alcance

- **Solo cliente**, controllers de admin y supertécnico. Sin servidor ni BD (`datos` ya contiene todo el historial).
- **Fuera de alcance:** vista PLANA (sin cambios); vista del técnico (ya hecha).

## Testing

UI sin tests automáticos; validación manual como admin y supertécnico:
1. En vista agrupada, el filtro de técnico **aparece**.
2. Seleccionar un técnico → solo IMEIs donde intervino; varios → IMEIs donde intervino alguno (OR).
3. Abrir un IMEI filtrado → arriba las de los técnicos filtrados, debajo y atenuadas las de otros (si las hay); contador "X de filtrados + Y de otros".
4. Sin filtro de técnico → agrupada y detalle como ahora (todas normales).
5. Vista plana → sin cambios.
6. Regresión: con filtro vacío, todo el historial se comporta igual que antes.

## Despliegue

Solo recompilar y distribuir el cliente.
