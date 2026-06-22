# Reparaciones de otros técnicos por defecto en el detalle — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

En la vista DETALLE de un IMEI del técnico, las reparaciones de otros técnicos se muestran **por defecto** (atenuadas), eliminando el botón que hoy hay que pulsar para verlas. Las reparaciones propias del técnico siguen arriba; las ajenas, debajo y con opacidad reducida.

## Contexto actual

En `ReparacionControllerTecnico`, modo `DETALLE`:
- Las reparaciones propias (`idTec == Sesion.getIdTec()`) se muestran siempre, primero.
- Existe un `ToggleButton btnOtrosTecnicos` en la barra de navegación del detalle. Al activarlo, se añaden las reparaciones ajenas (`idTec != sesión`) al final y se registran en `idsAjenas`.
- El `rowFactory` ya atenúa las filas ajenas: `setOpacity(idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0)`.
- El contador muestra "X propia(s) + Y de otros" cuando el botón está activo, o "X reparaciones" cuando no.
- `datos` ya contiene las reparaciones ajenas del IMEI (se cargan para el contador agrupado), así que mostrarlas no requiere ninguna carga ni cambio de servidor.

## Cambio

Mostrar las ajenas siempre en DETALLE y eliminar el botón:

- En el modo `DETALLE` de `aplicarFiltros()`: incluir siempre las reparaciones ajenas (quitar la condición `if (btnOtrosTecnicos.isSelected())`), manteniendo propias arriba + ajenas abajo y poblando `idsAjenas` igual que ahora.
- Eliminar por completo `btnOtrosTecnicos`: el campo, su creación, el `selectedProperty` listener, su inserción en la barra de navegación y la referencia en el reset de modo.
- Contador: siempre "X propia(s) + Y de otros" cuando hay ajenas; "X reparaciones" cuando el IMEI solo tiene propias.
- El estilo atenuado (`idsAjenas`, opacity 0.45) se conserva sin cambios.

## Alcance

- **Solo cliente, solo `ReparacionControllerTecnico`, solo modo DETALLE.**
- **Fuera de alcance:** vista PLANO del técnico (sigue mostrando solo las suyas — filtra `idTec == sesión`); supertécnico y admin (ya ven todas); servidor y BD.

## Testing

UI sin tests automáticos (patrón del proyecto); validación manual:
1. Como técnico, entrar en el detalle de un IMEI con reparaciones de varios técnicos → se ven las propias arriba y las ajenas atenuadas debajo, sin botón.
2. El contador refleja "X propia(s) + Y de otros".
3. IMEI sin reparaciones ajenas → solo propias, contador "X reparaciones", sin atenuar nada.
4. Vista PLANO del técnico → sigue mostrando solo sus reparaciones (sin cambios).

## Despliegue

Solo recompilar y distribuir el cliente. Sin cambios de servidor ni BD.
