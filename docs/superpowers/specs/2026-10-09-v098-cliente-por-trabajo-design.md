# 0.9.8 — Cliente guardado en cada trabajo

Fecha: 2026-10-09. Versión del producto: **0.9.8** (servidor y web etiquetados juntos), después de la 0.9.7.
Requisito de: la limpieza de los clientes ficticios de incidencias
(`2026-10-09-quitar-clientes-incidencias-design.md`), que se hace **después** de esta versión.

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Problema

El cliente solo existe en el teléfono (`Telefono.ID_CLI`, uno por IMEI). Cada consulta de trabajos pregunta «¿qué
cliente tiene este IMEI?», y la respuesta es siempre la de **hoy**: al cambiar el cliente de un teléfono, todo su
historial pasa a mostrar el cliente nuevo.

| Fecha | Qué pasa | `Telefono.ID_CLI` | Historial hoy |
|---|---|---|---|
| Enero | El IMEI X vuelve devuelto de Amazon y se repara (R1) | Incidencias Amazon | R1: Incidencias Amazon |
| Marzo | Se vende a Pepe y se le prepara (R2) | **Pepe** | R1: **Pepe** (mal), R2: Pepe |

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Dónde se guarda | Columna nueva **`Reparacion.ID_CLI`** (nula, clave ajena a `Cliente`) | El cliente es de cada trabajo («para quién se hizo»); el modelo sí es del teléfono |
| Cuándo se escribe | **Al cerrarse** la fila (nace cerrada o recibe `FECHA_FIN`), copiando `Telefono.ID_CLI` en la misma sentencia | Si el cliente cambia con el trabajo abierto, se guarda el del pedido para el que de verdad se hizo |
| Trabajos abiertos | Columna **vacía**; siguen leyendo el cliente del teléfono | Es el pedido en curso: el urgente (por teléfono), la cola, la barra de Pedidos y la predicción de glass no cambian |
| Regla de lectura | **Abierto → `Telefono.ID_CLI`; cerrado → `Reparacion.ID_CLI`** | Una sola regla en todas las consultas. Cerrado con la columna vacía = «se cerró sin cliente» |
| Reabrir | La columna se **vacía** | Vuelve a seguir al teléfono hasta que se cierre de nuevo |
| Vista IMEIs | Muestra el **cliente actual del teléfono** (campo nuevo `clienteTelefono`) | Es una vista por teléfono y su «Editar cliente» cambia el del teléfono |
| Relleno de lo ya cerrado | **Reconstruido desde el registro de actividad**; sin apuntes del IMEI, el cliente actual del teléfono | Es la única ocasión de corregir lo ya pisado; el formato de los apuntes es estable (§6) |
| Cómo se aplica el relleno | En una **transacción**, con un **análisis de lo que cambia antes del `COMMIT`**; se confirma a mano | Decisión del usuario: ver primero qué se gana y qué se pierde |
| Borrar clientes | Un cliente que aparece en el historial ya **no se puede borrar** (clave ajena): se **desactiva** | Es lo que la web ya propone («desactívalo en lugar de borrarlo»); desactivado no sale para elegir |
| Versión | **0.9.8**, aparte de la 0.9.7 | La 0.9.7 ya está en preproducción esperando producción |

## 3. Base de datos

```sql
ALTER TABLE Reparacion
    ADD COLUMN ID_CLI INT NULL,
    ADD CONSTRAINT fk_reparacion_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI);
```

Solo añade. El servidor de la 0.9.7 sigue funcionando con la columna puesta (no la nombra), así que la migración va
antes que el servidor nuevo. `crear_bd.sql` se actualiza igual.

## 4. Escritura (servidor, `ReparacionDAO`)

En cada cierre, la sentencia copia el cliente del teléfono: `(SELECT t.ID_CLI FROM Telefono t WHERE t.IMEI = …)`.

| Método | Filas que cierra |
|---|---|
| `insertarCompleta` | cada fila de pieza `R…`/`G…` (nace cerrada) y la asignación al ponerle `FECHA_FIN` |
| `guardarFilaIndividual` | cada fila de pieza `R…`/`G…` (nace cerrada) |
| `completar` | la asignación (`FECHA_FIN = NOW()`) |
| `completarPulido` | el `P…` (nace cerrado) y su `AP…` al ponerle `FECHA_FIN` |
| `insertar` (alta antigua de una `R…`) | la `R…` (si no trae `FECHA_FIN`, la regla de lectura ignora el valor) |

**Reabrir** (`eliminar` de una `R…` que resolvía una incidencia: `FECHA_FIN = NULL` en su `A…`): también
`ID_CLI = NULL`.

## 5. Lectura

- Las consultas de trabajos (`HISTORIAL_SELECT`, `ASIGNACION_SELECT`, `GLASS_HISTORIAL_SELECT`,
  `GLASS_ASIGNACION_SELECT`, `HISTORIAL_PULIDO_SELECT`, `ASIGNACION_PULIDO_SELECT` y sus variantes por id, por IMEI y
  `getAsignacionesCompletadasHoy`) unen `Cliente` con la regla de §2:
  `LEFT JOIN Cliente cli ON cli.ID_CLI = CASE WHEN r.FECHA_FIN IS NULL THEN tel.ID_CLI ELSE r.ID_CLI END`.
- Todas devuelven además **`clienteTelefono`** (nombre del cliente actual del teléfono:
  `LEFT JOIN Cliente cliTel ON cliTel.ID_CLI = tel.ID_CLI`). `ReparacionResumen` gana el campo y el contrato de la API
  lo publica.
- **Web, vista IMEIs:** la columna Cliente del grupo, su filtro, sus opciones, el CSV y la clave actual de «Editar
  cliente» usan `clienteTelefono` en vez de `cliente`.
- **Sin cambios:** Historial (reparación, glass, pulido), sus filtros y CSV siguen leyendo `cliente`, que ahora es el
  del trabajo. Urgente automático (`marcarUrgentesClienteVencidas`), orden de la cola, carga y predicción de glass
  trabajan con abiertas: siguen con el teléfono.
- Inventario (`TelefonoDAO`) es por teléfono: sin cambios.
- **Borrar un cliente:** `ClienteDAO.tieneTelefonos` (lo que mira el menú de la pestaña Clientes para ofrecer «Borrar»
  y lo que comprueba `DELETE /api/clientes/{idCli}`) cuenta también los trabajos con ese cliente guardado. Así un
  cliente con historial no ofrece «Borrar» y el servidor responde 409 («…tiene teléfonos o trabajos asociados;
  desactívalo…») en vez de un error de la clave ajena.

## 6. Relleno de lo ya cerrado

**Fuente.** El registro de actividad apunta cada asignación y cambio de cliente de un teléfono con el mismo formato
desde que existe el vínculo: `ASIGNAR_CLIENTE` y `CAMBIAR_CLIENTE` con detalle `IMEI: <imei>, ID_CLI: <id>` (`—` =
sin cliente) y su `FECHA`.

**Regla, para cada trabajo cerrado** (`FECHA_FIN` no nula, anterior al arranque del servidor 0.9.8):
1. Si su IMEI tiene apuntes: el `ID_CLI` del **último apunte con `FECHA <= FECHA_FIN`**; si todos son posteriores al
   cierre, **sin cliente**.
2. Si su IMEI no tiene apuntes: el cliente **actual** del teléfono.
3. Si el `ID_CLI` del apunte ya no existe en `Cliente` (borrado): sin cliente.

`UPDATED_AT` se conserva (es un dato añadido, no una edición: no debe provocar «modificado por otro usuario»).

**Fechas.** `Reparacion.FECHA_FIN` es `DATETIME` y `Log_Actividad.FECHA` es `TIMESTAMP`; el servidor las escribe en UTC.
La sesión del relleno fija `time_zone = '+00:00'` para compararlas en la misma zona.

**Hueco conocido.** Quitar el cliente con «— Sin cliente —» en el modal de asignación no deja apunte; tras una de
esas, la regla 1 seguiría viendo el cliente anterior. El análisis lo hace visible (trabajos donde reconstruido y
actual difieren).

**Cómo se aplica (decisión del usuario).** Todo en una transacción, en este orden:
1. Relleno.
2. **Análisis, antes de confirmar:**
   - cuántos trabajos se rellenan y de qué fuente (apunte / cliente actual / sin cliente);
   - cuántos quedan con un cliente **distinto** del actual del teléfono, por cliente;
   - una **muestra** de esos (trabajo, IMEI, fecha de cierre, cliente rellenado, cliente actual) para revisarla;
   - los teléfonos de los dos clientes de incidencias: sus trabajos cerrados y con qué cliente quedan.
3. `COMMIT` a mano si el análisis cuadra; si no, `ROLLBACK`.

En preproducción el análisis se hace sin prisa. En producción, el mismo análisis ya revisado en preproducción se
repasa en un par de minutos: mientras la transacción está abierta, el relleno tiene bloqueadas las filas cerradas de
`Reparacion`. Por eso el relleno de producción se hace en un momento sin actividad.

## 7. Despliegue

Primero preproducción y después producción, en el mismo orden:

1. Migración de §3.
2. Servidor y web 0.9.8. Se anota la hora de arranque del servidor: es el corte del relleno.
3. Relleno de §6 (solo trabajos cerrados antes del corte), con su análisis y `COMMIT` a mano.
4. Comprobación (§8).

Después, en cada entorno, la limpieza de los clientes de incidencias ya ajustada (§9).

## 8. Pruebas

- **Servidor:** cada método de §4 guarda el cliente del teléfono al cerrar; la reapertura lo vacía; las consultas
  aplican la regla y devuelven `clienteTelefono`; contrato de la API con el campo nuevo.
- **Web:** la vista IMEIs agrupa, filtra, exporta y edita con `clienteTelefono`.
- **En preproducción, a mano:** cerrar un trabajo de un teléfono con cliente, cambiarle el cliente al teléfono y
  comprobar que el Historial conserva el anterior y que la vista IMEIs muestra el nuevo; reabrir (borrar la reparación
  que resolvía una incidencia) y ver que la asignación vuelve a seguir al teléfono.

## 9. Relación con la limpieza de incidencias

La limpieza (`2026-10-09-quitar-clientes-incidencias-design.md`) se hace **después** del relleno, en cada entorno:

- Con el relleno hecho, los trabajos cerrados de esas devoluciones conservan «Incidencias Amazon / profesionales» en
  el Historial.
- Los dos clientes se **desactivan** en vez de borrarse (el historial los nombra).
- Su transacción también lleva un **análisis antes del `COMMIT`**: qué IMEIs se quedan sin cliente y la comprobación
  de que ningún trabajo cerrado de esos IMEIs se ha quedado sin su cliente guardado.

## 10. Fuera de alcance

- Mostrar el cliente de cada trabajo en el detalle de un IMEI (hoy el detalle no tiene columna de cliente).
- Guardar el cliente en los trabajos abiertos.
- El JavaFX (retirado desde el corte).
