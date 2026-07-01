# Clúster A — Contadores/badges de pendientes entre las 3 categorías

Fecha: 2026-07-01
Rama: `feature/contadores-asignaciones`
Origen: backlog post-Glass, clúster A (ítems 1-3).

Se implementan de golpe los 3 ítems del clúster A (todos cliente, pequeños).

## Ítem 1 — Badge de pendientes: cap 99+ e incluir pulidos

El badge de "Mis pendientes" (a) cortaba el conteo en `9+` y (b) sumaba solo
reparaciones + glass. Ahora cap `99+` y **suma también los pulidos**.

Afecta a los 2 controladores de rol con badge de pendientes
(`ReparacionControllerTecnico`, `ReparacionControllerSuperTecnico`; ADMIN no
tiene "mis pendientes"). Los controladores de pulido ya están inyectados y
exponen `getTotalItems()`: `pulidoTecnicoController` (Tecnico),
`misPulidosTecnicoController` (SuperTecnico).

Cambios (idénticos salvo el nombre del controlador de pulido):

1. **Cap 99+** — en `setBadge`: `count > 9 ? "9+"` → `count > 99 ? "99+"`.
2. **Sumar pulidos** — en `actualizarBadges`, añadir `+ <ctrlPulido>.getTotalItems()`.
3. **Frescura** — en el bloque "Badge data siempre fresco" de `recargar()`,
   añadir línea que fuerce `<ctrlPulido>.cargar()` cuando su pestaña no esté
   visible, en paralelo a rep y glass. Y cargar pulidos también en el init
   (antes solo se cargaban rep+glass), para que el conteo sea correcto desde el
   arranque.

## Ítem 2 — Conteo por toggle (Reparaciones/Glass/Pulidos)

Cada toggle de "Mis pendientes" muestra su propio conteo. Enfoque elegido:
**sufijo en el texto del toggle** (p.ej. `Reparaciones (5)`), sin cambios de
FXML. Se decidió frente a un badge circular real por rapidez y bajo riesgo.

En `actualizarBadges` de ambos controladores se fija el texto de cada toggle
(`togglePendRep/Glass/Pul` en Tecnico; `toggleMisPendRep/Glass/Pul` en
SuperTecnico) con `base + " (" + n + ")"`, cap `99+` vía helper `conteoPill`.
Se muestra siempre el número, incluido `(0)`.

## Ítem 3 — Label "N asignados" cross-categoría — YA FUNCIONA (verificado)

El anchor del backlog pedía **verificar** que el label "N asignados" de
Asignaciones cuenta entre categorías. Verificado: `conteoTecnicosPorImei` se
calcula en `PendientesSuperTecnicoController.cargar()` sobre la lista unificada
rep+glass+pulido, y `contarTecnicosPorImei` cuenta técnicos distintos por IMEI
sin filtrar por categoría. **No requiere cambio.** (El `pillAsignados` del modal
de asignación sí es per-categoría, pero es intencional y no es este label.)

## Fuera de alcance

- Alerta de asignación cruzada (clúster B) y consistencia de columnas/acciones
  (clúster C), asignación masiva (clúster D): ítems posteriores del backlog.

## Verificación

`setBadge`/`actualizarBadges` y el texto de toggles son UI privada de
controladores JavaFX, no unit-testeables sin TestFX (la suite actual no los
cubre). Validación:

- Compila (`mvn -q -o compile`).
- Smoke manual en los 2 roles: el badge suma rep+glass+pulido y muestra `99+`
  al pasar de 99; cada toggle muestra su conteo; el label "N asignados" sigue
  correcto.
