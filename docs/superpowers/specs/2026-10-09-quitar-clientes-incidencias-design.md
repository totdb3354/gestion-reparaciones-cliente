# Quitar los clientes ficticios de incidencias

Fecha: 2026-10-09. **Solo datos**: sin código, sin despliegue y sin versión.
**Requisito:** en cada entorno, la 0.9.8 (cliente guardado en cada trabajo,
`2026-10-09-v098-cliente-por-trabajo-design.md`) desplegada y su relleno confirmado.

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. El script, los datos de las
máquinas y el registro de la ejecución en producción viven fuera de git, en `Apuntes/`.

## 1. Problema

Los técnicos dieron de alta a mano dos «clientes» que no lo son: **Incidencias Amazon** e **Incidencias profesionales**.
Los usan como etiqueta para distinguir los teléfonos **devueltos** (por un comprador de Amazon o por un cliente
profesional). Para el programa, tener cliente significa **ser un pedido**, y eso activa cuatro cosas:

1. **Urgente automático**: a las 00:00 se marcan urgentes todas las asignaciones abiertas de los teléfonos con cliente
   asignados antes de hoy (`ReparacionDAO.marcarUrgentesClienteVencidas`).
2. **Orden de la cola** en Pendientes y Asignaciones: urgente → con cliente → resto.
3. **Barra «Pedidos»** de la carga diaria: solo cuenta el trabajo con cliente (`CargaTecnicos`).
4. **Predicción del técnico de glass** en alcance Pedidos (`PrediccionGlass`).

Una devolución debe tratarse como **trabajo normal**.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Dónde se apunta una devolución | En el **Comentario de la asignación**, a mano, a partir de ahora | La devolución es del trabajo, no del teléfono. La Observación del teléfono es por IMEI y se quedaría pegada al venderlo |
| Flujo de incidencias del historial | **No** se usa para esto | «Marcar incidencia» significa «falló una reparación nuestra» y se la imputa a la reparación anterior; una devolución no siempre implica eso. Cuando sí lo implique, se puede marcar además, como hasta ahora |
| Módulo propio de devoluciones | **No ahora** | La vida del teléfono (dónde está en cada momento) es un módulo pendiente con el flujo ya diseñado; las devoluciones encajan ahí |
| Orden respecto a la 0.9.8 | **Después** del relleno de la 0.9.8 | Con el relleno hecho, los trabajos cerrados de esas devoluciones conservan la etiqueta en el Historial; después de quitar el cliente de los teléfonos ya no habría de dónde sacarla |
| Teléfonos que los tienen | Quedan **sin cliente** | Para que dejen de comportarse como pedido |
| Asignaciones abiertas de esos teléfonos | El nombre del cliente pasa **delante** del comentario: `Incidencias Amazon · <comentario>`, o solo el nombre si estaba vacío; si el comentario ya lo contiene, no se toca | Se conserva la etiqueta donde los técnicos la ven mientras el trabajo está abierto |
| Trabajos ya cerrados | **Conservan** la etiqueta en el Historial (su cliente guardado, 0.9.8) | No se tocan |
| Urgente de esas asignaciones abiertas | Se **quita a todas** (reparación, glass y pulido) | Lo puso el urgente automático por tener cliente. El urgente es por teléfono: se quita en las tres categorías a la vez. El supertécnico vuelve a marcar a mano la que sea urgente de verdad |
| Rastro en el registro de actividad | Una línea **`CAMBIAR_CLIENTE` por teléfono**, con el mismo detalle que pone el programa (`IMEI: x, ID_CLI: —`) y un motivo que explica la limpieza, a nombre del usuario que la ejecuta | La búsqueda por IMEI del registro la encuentra igual que un cambio de cliente hecho desde la web |
| Los dos clientes | Se **desactivan** desde la pestaña Clientes de la web, con el interruptor de siempre (no se borran) | El historial los sigue nombrando (clave ajena de la 0.9.8). Desactivados no salen para elegir. Queda `BAJA_CLIENTE` con el usuario |
| Confirmación | Todo en una transacción con **análisis antes del `COMMIT`**; se confirma a mano | Decisión del usuario: ver primero qué IMEIs se quedan sin cliente y qué se podría perder |

## 3. Operación

### 3.1 Comprobación (solo lectura)

1. El relleno de la 0.9.8 está hecho en este entorno: hay trabajos cerrados con `Reparacion.ID_CLI` relleno.
2. Los clientes cuyo nombre contiene «incidencia», con su `ID_CLI` y su nombre exacto.
3. Cuántos teléfonos tiene cada uno, y cuántos de ellos tienen asignaciones abiertas.
4. Las asignaciones abiertas de esos teléfonos (`ID_REP LIKE 'A%' AND FECHA_FIN IS NULL`: reparación, glass y
   pulido) con IMEI, técnico, urgente y comentario actual.

### 3.2 Cambio y análisis (una transacción)

En este orden, porque el comentario necesita saber el cliente antes de quitarlo:

1. Comentario de las asignaciones abiertas de esos teléfonos (regla de §2).
2. `URGENTE = FALSE` en esas mismas asignaciones.
3. Una línea `CAMBIAR_CLIENTE` por teléfono en `Log_Actividad`.
4. `Telefono.ID_CLI = NULL` en esos teléfonos.
5. **Análisis, antes de confirmar:**
   - la lista de IMEIs que se quedan sin cliente, con el cliente que tenían, cuántas asignaciones abiertas tienen y
     cuántos trabajos cerrados;
   - sus trabajos cerrados por cliente guardado (cuántos conservan «Incidencias …», cuántos sin cliente, cuántos con
     otro): es lo que el Historial seguirá mostrando;
   - sus asignaciones abiertas tal como quedan (comentario y urgente).
6. `COMMIT` a mano si el análisis cuadra; si no, `ROLLBACK`.

Si falla cualquier paso, no se aplica nada. El comentario y el teléfono actualizan `UPDATED_AT` como cualquier cambio:
si un técnico tiene abierto el editor de comentario de una de esas filas, al guardar recibe «Dato modificado por otro
usuario» en vez de pisar el cambio. Quitar el urgente **conserva** `UPDATED_AT`, igual que hace la web al cambiarlo
(`ReparacionDAO.propagarUrgente`). Mientras la transacción está abierta, las filas de esos teléfonos quedan bloqueadas:
en producción, el análisis ya revisado en preproducción se repasa en un par de minutos.

### 3.3 Después

1. Desactivar los dos clientes en la pestaña Clientes.
2. Avisar a los técnicos: las devoluciones van en el Comentario de la asignación, y deben recargar la página para que
   los dos clientes desaparezcan de sus listas.

## 4. Verificación

- **En preproducción primero**, con la 0.9.8 y su relleno ya hechos sobre la copia de producción: comprobación,
  cambio con su análisis, revisión en la web (comentario con `·` y acentos bien, sin urgente, Historial con la
  etiqueta en los cerrados, línea del registro buscable por IMEI) y desactivación de los clientes.
- **En producción**: copia de seguridad antes. Al terminar, 0 teléfonos con esos clientes, las asignaciones con el
  comentario esperado y sin urgente, los cerrados con su etiqueta en el Historial, y los dos clientes desactivados.
- Al día siguiente: el urgente automático de las 00:00 ya no marca esos teléfonos.

## 5. Fuera de alcance

- El módulo de ubicaciones y devoluciones del teléfono (pendiente; ver `Apuntes/plan-futuro.md`).
- Cualquier cambio de código en el urgente automático, la cola, la carga o la predicción.
