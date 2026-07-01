# Ítem 1 clúster A — Badge de pendientes: cap 99+ e incluir pulidos

Fecha: 2026-07-01
Rama: `feature/contadores-asignaciones`
Origen: backlog post-Glass, clúster A ítem 1.

## Objetivo

El badge de "Mis pendientes" hoy (a) corta el conteo en `9+` y (b) suma solo
reparaciones + glass. Se quiere subir el cap a `99+` y **sumar también los
pulidos**, para que el badge refleje la carga real de las tres categorías.

## Alcance

Afecta a los **2 controladores de rol con badge de pendientes**:

- `ReparacionControllerTecnico`
- `ReparacionControllerSuperTecnico`

(ADMIN no tiene "mis pendientes"; queda fuera.)

Los controladores de pulido ya están inyectados y exponen `getTotalItems()`:
`pulidoTecnicoController` (Tecnico) y `misPulidosTecnicoController` (SuperTecnico).

## Cambios

Idénticos en ambos controladores salvo el nombre del controlador de pulido:

1. **Cap 99+** — en `setBadge(Label, int)`:
   `lbl.setText(count > 9 ? "9+" : ...)` → `count > 99 ? "99+" : ...`.

2. **Sumar pulidos** — en `actualizarBadges()`, añadir
   `+ <ctrlPulido>.getTotalItems()` al conteo de `lblBadgePendientes`.

3. **Frescura del dato de pulidos** — en el bloque "Badge data siempre fresco"
   de `recargar()`, añadir una línea que fuerce `<ctrlPulido>.cargar()` cuando su
   pestaña/toggle no esté visible, en paralelo a las que ya existen para rep y
   glass. Sin esto, el conteo de pulidos del badge quedaría obsoleto salvo cuando
   la pestaña de pulidos está abierta.

## Fuera de alcance

- Badge por toggle Reparaciones/Glass/Pulidos (ítem 2 del clúster).
- Label "N técnicos asignados" cross-categoría (ítem 3).
- Badge de Asignaciones del SuperTecnico (`lblBadgeAsignaciones`, suma
  `pendientesSuperTecnicoController`) — es otra métrica, no se toca.

## Verificación

`setBadge`/`actualizarBadges` son métodos privados de controladores JavaFX, no
unit-testeables sin TestFX (la suite actual no los cubre). Validación:

- Compila (`mvn -q compile`).
- Smoke manual en los 2 roles: el badge suma rep+glass+pulido y muestra `99+`
  cuando el total supera 99.
