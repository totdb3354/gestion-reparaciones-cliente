# Guardado individual por fila en el modal de reparación — Diseño

**Fecha:** 2026-06-16
**Depende de:** [[2026-06-12-borrador-persistente-modal-design]] (Item 2 del backlog UAT, ya implementado)

## Objetivo

Hoy, al guardar el modal de reparación por componente, **todas** las filas activas se insertan de golpe (`insertarCompleta`) con `FECHA_ASIG = FECHA_FIN = NOW()` — aunque el técnico haya ido completando filas en días distintos (gracias al borrador persistente, que permite dejar el modal a medias y seguir otro día). Resultado: una reparación hecha ayer queda fechada con la fecha de hoy si el guardado final se hace hoy.

Se añade un botón **"Guardar fila"** por fila: confirma esa reparación concreta ya, con su fecha real, sin esperar a que el resto de la asignación esté lista. La fila queda bloqueada **de forma definitiva** — guardar una fila es una reparación real, no un borrador; no hay "deshacer" para el técnico. La asignación **no se cierra** hasta pulsar el botón final — eso permite mantenerla abierta varios días mientras se completan las demás filas. La única forma de eliminar una fila ya guardada es el flujo de borrado restringido a `SUPERTECNICO` desde el historial, que ya existe hoy sin cambios.

## Estado actual relevante

- `FormularioReparacionController` ya gestiona un borrador (`Reparacion_borrador`, JSON) con los inputs no guardados — ver spec del Item 2.
- `ReparacionDAO.insertarCompleta` (servidor) inserta todas las filas activas de una asignación en una transacción: crea `R*` con `NOW()`, descuenta stock real, resuelve solicitudes pendientes que coincidan, y si no quedan solicitudes bloqueantes, **cierra la asignación** (`FECHA_FIN = NOW()` en la fila `A*`) y marca resuelta la incidencia encadenada si la hay.
- `ReparacionDAO.eliminar(idRep)` (servidor) ya existe y borra una reparación individual: restaura el stock, borra `Reparacion_componente`/`Reparacion`, revierte la cadena de incidencia si esta `R*` era la que la resolvía. Hoy solo es accesible vía `DELETE /api/reparaciones/{idRep}`, restringido a `SUPERTECNICO` (se usa para borrado desde el historial) — **este diseño no lo toca ni añade un endpoint paralelo**: sigue siendo la única vía para borrar una fila ya guardada individualmente.
- `GET /api/reparaciones/imei/{imei}` (`getByImei`) ya existe y devuelve todas las reparaciones (`Reparacion`, con su `ID_REP`) de un IMEI — se reutiliza para detectar si una fila marcada "guardada" en el borrador fue borrada por un `SUPERTECNICO` (ver "Rol del borrador" más abajo).
- El hueco de la derecha de cada fila (`btnSolicitud` en `FilaUI`) ya se reutiliza hoy para tres estados visuales: "⚠ Solicitud pieza", "⚠ En camino", "✓ Recibido" — es el mismo hueco donde aparecerá "Guardar fila"/"✓ Guardada".
- `FilaUI.isActiva()` decide hoy si una fila "cuenta" para el guardado global: `cantidad > 0 || reutilizado || solicitudNuevaEnEstaSesion || (agotadoConfirmado && !solicitudActiva)`. Ese último término (`esAgotadoNuevo()`) es un caso especial: una fila marcada como agotada **sin** uso real no se manda a `insertarCompleta` — hoy se excluye de ese bucle y se manda aparte a `agotarComponente()` (crea una solicitud de reposición, no un `R*`). Por tanto el criterio para mostrar "Guardar fila" es **`isActiva() && !esAgotadoNuevo()`**: cantidad>0, reutilizado marcado, o solicitud nueva acompañada de uso real — nunca un agotado puro sin componente usado, porque ahí no hay ningún `R*` que crear todavía.

## Diseño

### Comportamiento de "Guardar fila"

Solo visible cuando `fila.isActiva()` es `true` y la fila no tiene una solicitud pendiente/en camino bloqueante (en ese caso no hay nada que guardar todavía).

**Importante — `btnSolicitud` está oculto por defecto hoy** (solo se muestra cuando `activarSolicitud()` carga una solicitud ya existente en BD, o queda deshabilitado tras confirmar un agotado). No reacciona hoy a cambios de `cantidad`/`reutilizado`. Hace falta un método nuevo, p. ej. `actualizarBotonGuardarFila()`, enganchado a los mismos listeners que ya llaman a `actualizarSubFilaAgotado()`/`notificar()` (`btnMas`, `btnMenos`, `chkReutilizado`), que muestre/oculte/renombre `btnSolicitud` según `isActiva() && !esAgotadoNuevo()` cada vez que cambian esos inputs. Casos cubiertos:
- **Sin stock desde el principio**: oculto hasta marcar `reutilizado` → en ese momento aparece como "Guardar fila".
- **Stock agotado a medio reparar**: visible como "Guardar fila" desde que `cantidad>0` (antes de tocar el aviso de agotado). En cuanto se confirma el diálogo de agotado, además del `setDisable(true)` que ya hace el código hoy (línea 1380), se añade `setVisible(false)`/`setManaged(false)` para que desaparezca del todo, no solo se vea deshabilitado.
- **Pieza recibida al reabrir**: carga como "✓ Recibido" (deshabilitado); en cuanto se introduce uso real (`cantidad`/`reutilizado`), el mismo recálculo lo sustituye por "Guardar fila".

Al pulsarlo:

1. El cliente llama a un endpoint nuevo que inserta **solo esa fila** como `R*` real: mismo cuerpo que el bucle de `insertarCompleta` para una fila no-solicitud (crear `R*` con `NOW()`, insertar `Reparacion_componente`, descontar stock, resolver la solicitud de ese `idCom` si la había) — pero **sin** el bloque de cierre de asignación (líneas 340-374 de `ReparacionDAO.insertarCompleta` hoy). La asignación (`A*`) se queda abierta sí o sí, tenga o no solicitudes pendientes. Caso especial: si la fila tiene uso real **y además** marca una solicitud nueva a la vez (p. ej. "uso la última que quedaba y pido más"), hoy eso genera dos `FilaReparacion` en `ejecutarGuardarNueva()` (una normal + una `esSolicitud=true`); el endpoint nuevo hace lo mismo en una sola llamada: inserta el `R*` de uso real y registra la solicitud, ambos atados a la misma fila guardada.
2. El servidor devuelve el `idRep` generado.
3. El cliente bloquea la fila (mismos `setDisable` que ya usa `deshabilitarYaReparado()`/el estado de solicitud activa) y sustituye el hueco de la derecha por **"✓ Guardada [hora]"**, mismo verde (`#2E7D32`/`#E8F5E9`) que "✓ Recibido". No hay botón "Deshacer": guardar una fila es definitivo para el técnico.
4. El cliente anota en el borrador (ver abajo) que esa fila ya está guardada, con su `idRep` y fecha — para que si se cierra el modal y se reabre (incluso otro día), la fila siga apareciendo bloqueada y no se duplique.

Mismo mecanismo para las acciones "Otro": cada acción de la lista compacta (`OtrasAccionesUI`) lleva su propio "Guardar", insertando un `R*` individual con `cantidad = 0` (igual que hoy, stock neutro).

### Eliminar una fila ya guardada (sin cambios: solo `SUPERTECNICO` desde el historial)

No se añade ningún endpoint de "deshacer". Si una fila guardada individualmente resulta ser un error, se borra exactamente igual que cualquier otra reparación del historial hoy: `DELETE /api/reparaciones/{idRep}`, restringido a `SUPERTECNICO`, que ya hace todo lo necesario (`eliminar(idRep)`: restaura stock, revierte incidencia, borra la fila).

La única cosa que añade este diseño es la consecuencia en el modal: si esa fila pertenecía a una asignación que sigue abierta, el técnico la verá bloqueada como "✓ Guardada" la próxima vez que abra el modal — hasta que se detecta el borrado (ver siguiente sección) y se desbloquea sola.

### Rol del borrador (cambia de propósito, mismo mecanismo)

`Reparacion_borrador` deja de ser solo "lo que falta por escribir" y pasa a registrar también, por fila, si ya se guardó de verdad:

```json
{
  "modelo": "...",
  "filas": [
    { "prefijo": "pantalla", "idCom": 12, "cantidad": 1, "reutilizado": false,
      "guardada": true, "idRepGenerado": "R00042", "fechaGuardado": "2026-06-15T18:32:00" },
    { "prefijo": "bateria", "idCom": 7, "cantidad": 0, "reutilizado": false, "guardada": false }
  ],
  "otros": [
    { "descripcion": "Limpiar cámara", "guardada": false }
  ]
}
```

- `BorradorContenido.Fila` gana `guardada`, `idRepGenerado`, `fechaGuardado`.
- `BorradorContenido.otros` pasa de `List<String>` a una lista de objetos con el mismo patrón (`descripcion`, `guardada`, `idRepGenerado`, `fechaGuardado`).
- Al recuperar el borrador en `init(...)`, si hay alguna fila con `guardada=true`, el cliente llama una vez a `GET /api/reparaciones/imei/{imei}` (ya existe) y comprueba que cada `idRepGenerado` siga en esa lista:
  - **Si sigue existiendo**: la fila se pinta directamente en estado bloqueado ("✓ Guardada [fecha]"), no se reaplican sus inputs como editables.
  - **Si ya no existe** (un `SUPERTECNICO` la borró desde el historial mientras la asignación seguía abierta): la fila se desbloquea sola — se limpian `guardada`/`idRepGenerado`/`fechaGuardado` y vuelve a su estado editable normal, como si nunca se hubiera guardado. Se reescribe el borrador con ese ajuste.
- El auto-guardado debounced y el flush al cerrar siguen funcionando igual — ahora simplemente incluyen también este nuevo estado.
- El borrador sigue borrándose por completo en los mismos tres casos que ya existen (guardado final con éxito, borrado de la asignación por cascade, reasignación a otro técnico). Importante: reasignar a otro técnico borra el borrador, pero **no** revierte las filas ya guardadas de verdad (son `R*` reales, ya en el historial) — el nuevo técnico simplemente no las vuelve a ver como pendientes.

### El botón final

Sigue llamando a `insertarCompleta`, sin cambios de comportamiento — pero ahora solo viaja con las filas que **todavía no se guardaron individualmente** (se filtran las marcadas `guardada=true` antes de construir la lista). Si nunca se usó "Guardar fila", el comportamiento es exactamente el de hoy: todo de golpe, cierra la asignación. Si se guardó alguna fila individualmente y el resto se completa ahora, el botón final guarda lo que falta y cierra.

### Historial y logs

- Las filas guardadas individualmente aparecen ya en el historial del IMEI aunque la asignación siga abierta — coherente con que hoy el historial ya convive con asignaciones abiertas de otras incidencias del mismo IMEI.
- El endpoint nuevo de "guardar fila individual" registra en `LogDAO`, igual que ya hacen `insertarCompleta` (`COMPLETAR_REPARACION`), `eliminar` (`ELIMINAR_REPARACION`) o `agotarComponente` (`AGOTAR_COMPONENTE`). Acción propuesta: `GUARDAR_FILA_INDIVIDUAL`. El borrado de una fila guardada sigue pasando por `eliminar`/`ELIMINAR_REPARACION`, sin cambios.

## Componentes afectados

**Servidor:**
- `ReparacionDAO` — nuevo método (p. ej. `guardarFilaIndividual(FilaReparacion fila, String imei, int idTec, String idAsignacion)` → `String idRep`): misma lógica de inserción de una fila no-solicitud que ya existe en `insertarCompleta`, sin el bloque de cierre de asignación. No se toca `eliminar(idRep)` ni su autorización.
- `ReparacionController` — un endpoint nuevo (p. ej. `POST /api/reparaciones/{idAsignacion}/filas`), con su `logDao.insertar(...)`.

**Cliente:**
- `FormularioReparacionController` / `FilaUI` — botón "Guardar fila" (visible solo si `isActiva()` y sin solicitud bloqueante), estado bloqueado con "✓ Guardada [hora]", llamada al endpoint nuevo. Al recuperar el borrador, verificación contra `getByImei(imei)` para desbloquear filas cuya reparación fue borrada por un `SUPERTECNICO`.
- `OtrasAccionesUI` — mismo botón por acción, mismo flujo.
- `BorradorContenido` — campos nuevos en `Fila` (`guardada`, `idRepGenerado`, `fechaGuardado`); `otros` pasa de `List<String>` a una lista de objetos con los mismos campos.
- `ejecutarGuardarNueva()` — filtra las filas ya `guardada=true` antes de construir la lista para `insertarCompleta`.

## Riesgos / casos límite

- **Carrera entre "Guardar fila" y borrado/reasignación de la asignación**: igual que hoy hace `insertarCompleta` (líneas 296-305), el nuevo método bloquea la fila de la asignación (`FOR UPDATE`) antes de insertar, para que un `DELETE`/reasignación concurrente no pise el guardado.
- **Reapertura con borrador desfasado**: si el `idCom` de una fila ya guardada deja de existir o cambia de SKU maestro, el indicador "✓ Guardada" se sigue mostrando igual (ya es un hecho consumado en BD); solo afecta a la restauración de filas *no* guardadas, igual que ya gestiona el borrador hoy.
- **Doble clic / reintento de red en "Guardar fila"**: mismo patrón que ya usa el resto de la app (deshabilitar el botón nada más pulsarlo, hasta recibir respuesta).
- **Coste de la verificación al reabrir**: la llamada a `getByImei(imei)` para detectar filas borradas solo se hace si el borrador tiene alguna `guardada=true` — no añade tráfico en el caso normal (modal sin guardados individuales todavía).

## Pruebas

1. Completar una fila, pulsar "Guardar fila" → aparece ya en el historial del IMEI con la hora real, el stock baja ya, la asignación sigue en pendientes.
2. Cerrar el modal y reabrir la misma asignación (mismo día u otro) → la fila guardada aparece bloqueada con su fecha, no se puede volver a guardar.
3. Cerrar la app, completar el resto de filas otro día, pulsar el botón final → solo inserta las filas pendientes, con `NOW()` de ese día; la asignación se cierra.
4. Fila con solicitud pendiente/en camino → no aparece botón "Guardar fila" hasta que la pieza está recibida y se configura cantidad/reutilizado.
5. Reasignar la asignación a otro técnico tras haber guardado alguna fila individualmente → el borrador se borra, las filas ya guardadas siguen en el historial tal cual, el nuevo técnico no las ve como pendientes.
6. Revisar `LogActividad` tras guardar una fila individual → aparece la entrada nueva.
7. Un `SUPERTECNICO` borra desde el historial una fila guardada individualmente mientras la asignación sigue abierta → al reabrir el modal, esa fila aparece de nuevo editable (no bloqueada), sin rastro de "guardada" en el borrador.

## Fuera de alcance

- Cambiar la autorización del `DELETE /api/reparaciones/{idRep}` general (sigue solo `SUPERTECNICO`, sin relación con este flujo).
- Botón de "guardar fila" en el flujo de **edición** de una reparación ya completada (`initEditar`) — no lleva borrador, no cambia.
- **Deshacer accesible al técnico**: guardar una fila es definitivo desde su punto de vista; solo `SUPERTECNICO` puede revertirlo, y solo a través del borrado general del historial (sin endpoint nuevo).
- **Guardado individual para filas agotado-solo** (sin uso real, `esAgotadoNuevo()` true): no tienen botón propio en `subFilaAgotado`, se siguen resolviendo solo en el guardado final (`agotarComponente()`), igual que hoy. Razón: `agotarComponente` no crea un `R*` propio (añade una fila de solicitud sobre la `A*`), así que no hay nada equivalente que borrar vía el historial — y en cuanto la solicitud queda `PENDIENTE`, otro rol (admin/compras) puede empezar a gestionarla.
