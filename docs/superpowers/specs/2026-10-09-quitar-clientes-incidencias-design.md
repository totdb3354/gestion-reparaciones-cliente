# Quitar los clientes ficticios de incidencias

Fecha: 2026-10-09. **Solo datos**: sin código, sin despliegue y sin versión. Independiente de la 0.9.7.

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

Una devolución debe tratarse como **trabajo normal**. Además, el cliente vive en el **teléfono** (uno por IMEI), no en
el trabajo: si el teléfono devuelto se vende después y se le pone el cliente real, todo su historial pasa a mostrar el
cliente nuevo.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Dónde se apunta una devolución | En el **Comentario de la asignación**, a mano, a partir de ahora | La devolución es del trabajo, no del teléfono. La Observación del teléfono es por IMEI y se quedaría pegada al venderlo |
| Flujo de incidencias del historial | **No** se usa para esto | «Marcar incidencia» significa «falló una reparación nuestra» y se la imputa a la reparación anterior; una devolución no siempre implica eso. Cuando sí lo implique, se puede marcar además, como hasta ahora |
| Módulo propio de devoluciones | **No ahora** | La vida del teléfono (dónde está en cada momento) es un módulo pendiente con el flujo ya diseñado; las devoluciones encajan ahí |
| Los dos clientes | Se **borran** | No son clientes. El derivador de ubicaciones de `main` también entiende «tiene cliente = es pedido» |
| Teléfonos que los tienen | Quedan **sin cliente** | Para poder borrar los clientes (clave ajena `fk_telefono_cliente`) |
| Asignaciones abiertas de esos teléfonos | El nombre del cliente pasa **delante** del comentario: `Incidencias Amazon · <comentario>`, o solo el nombre si estaba vacío; si el comentario ya lo contiene, no se toca | Se conserva la etiqueta donde los técnicos la ven. Solo las abiertas: el Historial de reparación y glass no enseña el comentario |
| Trabajos ya cerrados | Pierden la etiqueta | Aceptado por el usuario |
| Urgente de esas asignaciones abiertas | Se **quita a todas** (reparación, glass y pulido) | Lo puso el urgente automático por tener cliente. El urgente es por teléfono: se quita en las tres categorías a la vez. El supertécnico vuelve a marcar a mano la que sea urgente de verdad |
| Rastro en el registro de actividad | Una línea **`CAMBIAR_CLIENTE` por teléfono**, con el mismo detalle que pone el programa (`IMEI: x, ID_CLI: —`) y un motivo que explica la limpieza, a nombre del usuario que la ejecuta | La búsqueda por IMEI del registro la encuentra igual que un cambio de cliente hecho desde la web |
| Borrado de los dos clientes | Desde **Gestión → Clientes**, con el botón de siempre | Queda `BORRAR_CLIENTE` con el usuario, y el propio programa comprueba que ya no les queda ningún teléfono |

## 3. Operación

### 3.1 Comprobación (solo lectura)

1. Los clientes cuyo nombre contiene «incidencia», con su `ID_CLI` y su nombre exacto.
2. Cuántos teléfonos tiene cada uno, y cuántos de ellos tienen asignaciones abiertas.
3. Las asignaciones abiertas de esos teléfonos (`ID_REP LIKE 'A%' AND FECHA_FIN IS NULL`: reparación, glass y
   pulido) con IMEI, técnico, urgente y comentario actual.
4. Las filas de `Envio` con esos clientes. Debe salir **0**; si no, se para y se replantea (clave ajena
   `fk_envio_cliente`).

### 3.2 Cambio (una transacción)

En este orden, porque el comentario necesita saber el cliente antes de quitarlo:

1. Comentario de las asignaciones abiertas de esos teléfonos (regla de §2).
2. `URGENTE = FALSE` en esas mismas asignaciones.
3. Una línea `CAMBIAR_CLIENTE` por teléfono en `Log_Actividad`.
4. `Telefono.ID_CLI = NULL` en esos teléfonos.

Si falla cualquier paso, no se aplica nada. `UPDATED_AT` se actualiza como en cualquier cambio: si un técnico tiene
abierto el editor de comentario de una de esas filas, al guardar recibe «Dato modificado por otro usuario» en vez de
pisar el cambio.

### 3.3 Después

1. Borrar los dos clientes en Gestión → Clientes.
2. Avisar a los técnicos: las devoluciones van en el Comentario de la asignación, y deben recargar la página para que
   los dos clientes desaparezcan de sus listas.

## 4. Verificación

- **En preproducción primero**: crear los dos clientes con los mismos nombres, ponérselos a unos pocos teléfonos con
  asignaciones abiertas (con y sin comentario, alguna urgente, alguna con el nombre ya en el comentario) y alguno sin
  asignaciones abiertas. Ejecutar la comprobación y el cambio, revisar el resultado y borrar los clientes desde la web.
- **En producción**: copia de seguridad antes. Al terminar, la comprobación debe devolver 0 teléfonos con esos clientes,
  las asignaciones con el comentario esperado y sin urgente, y el borrado desde Gestión → Clientes debe funcionar.
- Al día siguiente: el urgente automático de las 00:00 ya no marca esos teléfonos.

## 5. Fuera de alcance

- El módulo de ubicaciones y devoluciones del teléfono (pendiente; ver `Apuntes/plan-futuro.md`).
- Cualquier cambio de código en el urgente automático, la cola, la carga o la predicción.
