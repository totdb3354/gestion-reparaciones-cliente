# 🎉 Novedades — Versión 0.13.0

¡Hola! Esta es una actualización grande: **Glass pasa a ser un tipo de trabajo propio** (con su asignación, su pestaña y su historial), el **modal de asignación se reorganiza en 3 colas**, y ahora puedes **gestionar el cliente de un IMEI** mucho mejor (incluido dejarlo "sin cliente"). Esto es lo que vas a notar:

---

## 🪟 Glass, ahora con vida propia

- El glass **deja de completarse junto a las reparaciones normales**: ahora se **asigna como Glass** y el técnico lo ve en su **pestaña Glass** de Mis pendientes, con su propio contador.
- El modal de completar un glass ofrece **solo lo suyo** (glass, marco y "otro"); el de reparación normal ya no mezcla piezas de glass.
- **Historial con selector Reparaciones | Glass | Pulidos** en los tres roles, y **exports CSV** para Glass.
- Las **incidencias de glass y de reparación son independientes** entre sí.
- El visor de **logs** filtra también acciones de glass y pulido.

## 🗂️ Vista "IMEIs" (antes "Agrupado")

- El apartado se llama ahora **IMEIs** y agrupa **todos los trabajos de un dispositivo** (reparaciones, glass y pulidos) con contadores por tipo.
- **Ordenada por actividad más reciente**: lo último que se ha trabajado sale arriba, da igual si fue una reparación, un glass o un pulido.

## 🧰 Modal de asignación: 3 colas independientes

- Al asignar trabajos, **Reparación, Glass y Pulido son colas separadas**: cada pestaña muestra solo lo suyo (el mismo IMEI puede estar en dos colas a la vez).
- **Pulido se rediseña por completo**: lista a la izquierda + detalle a la derecha (como rep/glass), técnico arriba que se aplica a lo que escaneas (editable por fila), y **no deja guardar si alguna fila está sin técnico**.
- El aviso de **asignación cruzada** te lista todas las asignaciones activas del IMEI en cualquier categoría.

## 👤 Cliente por IMEI, en serio

- En el modal de asignación puedes dejar un IMEI **"— Sin cliente —"** de verdad (rep/glass y pulido).
- Si borras el texto del cliente a medias, **al salir del campo se restaura** el que estaba — quitar el cliente solo pasa eligiendo "— Sin cliente —".
- El cliente de un IMEI se **sincroniza entre las colas** del modal: lo cambias en una y se refleja en las demás. El último cambio manual manda sobre lo que hubiera en la base de datos.

## ✏️ Edición más completa

- **Editar reparaciones de glass** desde el historial, igual que las normales.
- **Editar una acción "otro"** abre ahora el **modal completo**: la acción se edita en su sección, las filas ya reparadas salen bloqueadas en verde (también las acciones anteriores, "✓ Ya reparada"), y puedes **añadir reparaciones o acciones nuevas** en la misma edición.
- Lo que crees editando un glass **nace como glass** (antes se colaba como reparación normal).

## ✨ Pulido al día

- **"Asignado por"** y **columna Cliente** también en pulido; el modelo y el comentario se editan desde Asignaciones/Historial.
- **Borrar un pulido completado pide ahora un motivo obligatorio** (queda en los logs), y ya no se borra sin confirmación.
- El **badge y el contador de pulidos se actualizan al momento** al completar.
- El badge de pendientes muestra hasta **99+** y **suma también los pulidos**.

## 🔧 Otros arreglos

- Menú contextual de Asignaciones reordenado (los "Editar…" juntos).
- Scroll fiable a la última fila añadida en los modales de pedidos.
- Refrescos de listas más ágiles al completar/borrar (menos esperas).

---

## 🚀 Nota de despliegue (IMPORTANTE)

Esta versión **cambia el flujo del glass**, así que al repartirla:

1. **Drenar el glass pendiente**: los técnicos que hacen glass completan lo que tengan asignado **antes** de actualizar.
2. El SuperTécnico **no asigna glass** hasta que **él y los técnicos de glass** tengan la 0.13.0.
3. Actualizar **juntos** (misma franja): SuperTécnicos + técnicos de glass.
4. El **resto de técnicos** puede actualizarse en cualquier momento (su flujo de reparaciones normales no cambia).
5. Si queda algún glass asignado "a la antigua" (como reparación normal), el SuperTécnico lo borra y lo reasigna como Glass.

_Requiere el **servidor actualizado** (ya desplegado en preproducción)._
