# 0.9.8 (añadido) — Cliente en el Historial y «Corregir cliente»

Fecha: 2026-10-11. Entra en la misma **0.9.8** (aún sin tag ni producción), antes de desplegarla en producción.
Base: `2026-10-09-v098-cliente-por-trabajo-design.md` (cada trabajo guarda al cerrarse el cliente de su teléfono).

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Problema

1. La 0.9.8 guarda el cliente de cada trabajo cerrado y la API lo devuelve (`cliente`), pero el **Historial de
   reparación y glass no tiene columna Cliente** (solo la tiene el de pulidos): lo guardado no se ve.
2. Si el cliente guardado de un trabajo es **erróneo** (se puso mal en el teléfono y el trabajo se cerró con él), hoy
   solo se corrige con SQL a mano.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Columna Cliente | En la tabla de trabajos compartida (`columnasTrabajo`): **Historial de reparación, de glass y detalle de un IMEI**, después de «Modelo». Vacío = «—» | Es información que ya existe; en el detalle de un IMEI cada fila es un trabajo y se ve «julio → AMAZON, octubre → —» |
| Filtro | Desplegable **«Cliente»** en la barra del Historial (reparación y glass), como el de Asignaciones/IMEIs: opciones de lo cargado, «(Sin cliente)» delante si hay trabajos sin cliente; «Limpiar» lo vacía | Ver, por ejemplo, solo lo hecho para un cliente |
| CSV y «Copiar celda» | Llevan el cliente (Historial y detalle de un IMEI) | Coherencia con la columna |
| Corregir | Acción **«Corregir cliente»** en el menú contextual de un trabajo **cerrado** (Historial reparación/glass y detalle de un IMEI; este cubre también los pulidos). Solo supertécnico y administrador | El error típico es de un trabajo concreto o de los de un mismo cierre |
| El modal | Cliente correcto (activos + «— Sin cliente —»). Debajo, **los trabajos cerrados de ese mismo IMEI que tienen guardado el mismo cliente** que el elegido, con casillas, incluidas las asignaciones cerradas. Marcados de entrada: **el elegido y los del mismo día** (piezas de la misma reparación); los de otros días, sin marcar. Botón **«Corregir N»** | No ir fila a fila con una reparación de varias piezas, y decidir exactamente qué cambia |
| Teléfono | **No se toca**: su cliente actual se corrige donde ya se hace (IMEIs, Asignaciones, modal de asignar) | Abierto = sigue al teléfono; cerrado = historia. Son cosas distintas |
| Registro | Una línea **`CORREGIR_CLIENTE_TRABAJO`** por trabajo: `ID_REP: x, IMEI: y, ID_CLI: <anterior> → <nuevo>` (`—` = sin cliente) | Auditoría como el resto de cambios de cliente |
| `UPDATED_AT` | Se conserva | Es el dato derivado del cierre, como en el relleno; no debe dar «modificado por otro usuario» a quien edita la reparación |
| Versión | La misma **0.9.8** | No tiene tag ni está en producción |

## 3. Servidor

### 3.1 Trabajos cerrados de un IMEI (para el modal)

`GET /api/reparaciones/imei/{imei}/cerrados` (supertécnico): todos los trabajos **cerrados** del IMEI, de cualquier
tipo (`R`, `G`, `P` y asignaciones cerradas `A`/`AG`/`AP`), del más reciente al más antiguo:
`idRep`, `fechaFin`, `descripcion` (tipo de pieza en `R`/`G`; «Pulido» en `P`; «Asignación» en las `A…`), `idCli`
(guardado) y `cliente` (su nombre). El modal filtra por el `idCli` del trabajo elegido.

### 3.2 Corregir

`PATCH /api/reparaciones/cliente` (supertécnico), cuerpo `{ imei, idReps: [..], idCli: number | null }`:

- En una transacción: `UPDATE Reparacion SET ID_CLI = ?, UPDATED_AT = UPDATED_AT WHERE IMEI = ? AND FECHA_FIN IS NOT
  NULL AND ID_REP IN (…)`.
- Si las filas cambiadas no son tantas como `idReps` (alguno no existe, no es de ese IMEI o ya no está cerrado):
  **409** «Algún trabajo ya no está cerrado o no es de este teléfono» y no cambia nada.
- `idCli` que no existe: **400** «El cliente no existe». Lista vacía: **400**.
- Después, una línea `CORREGIR_CLIENTE_TRABAJO` por trabajo (§2), con el cliente anterior de cada uno.
- Responde 204.

## 4. Web

- **Columna** «Cliente» en `columnasTrabajo` (con su texto de «Copiar celda»), tras «Modelo».
- **Filtro** «Cliente» en el Historial (reparación y glass), en el estado de filtros de cada pestaña; «Limpiar» lo
  vacía. Las opciones salen del `cliente` de lo cargado (la vista IMEIs sigue con `clienteTelefono`).
- **CSV**: columna Cliente en el del Historial y en el del detalle de un IMEI.
- **Menú contextual** (`MenuHistorial`): «Corregir cliente» solo en filas cerradas y con permiso de edición.
- **Modal «Corregir cliente»**: carga §3.1, muestra el IMEI y el cliente guardado del elegido; selector de cliente
  (activos + «— Sin cliente —»); lista con casillas (tipo, id, fecha de fin, descripción) de los cerrados con el mismo
  `idCli`, marcados el elegido y los del mismo día (Madrid); «Corregir N» deshabilitado sin cliente elegido, si el
  elegido es el mismo que el guardado o sin ninguno marcado. Al terminar bien, cierra y recarga los historiales; si
  falla, muestra el mensaje del servidor.

## 5. Pruebas

- **Servidor:** la consulta de §3.1 (todos los tipos, solo cerrados, del IMEI); el `PATCH` con éxito (UPDATE con
  `UPDATED_AT` conservado y una línea de registro por trabajo con anterior → nuevo), el 409 (cuenta distinta), el 400
  (cliente inexistente y lista vacía) y el 403 a un técnico; el contrato publica las dos rutas.
- **Web:** la columna, el filtro (con «(Sin cliente)») y el CSV; el menú solo en cerradas y con permiso; el modal
  (preselección del mismo día, botón deshabilitado en los tres casos, llamada con los marcados, recarga y error).
- **Preproducción:** corregir un trabajo de prueba y ver la columna y el registro; deshacerlo.

## 6. Fuera de alcance

- Corregir desde el Historial de pulidos (se hace desde el detalle del IMEI).
- Cambiar el cliente actual del teléfono desde este modal.
