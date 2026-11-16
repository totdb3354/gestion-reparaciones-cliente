# Carga de técnicos: «por cerrar» cuenta como hecho al marcar, y tope de normales a 20 — diseño

Fecha: 2026-11-16. Estado: aprobado en conversación. Va en la **0.9.36** (la 0.9.35 es Validar SKU de Pedidos). Solo servidor
+ migración aditiva; la web solo sube su número.

## Por qué

Varios técnicos dicen que el «✓ hecho» del panel de carga no cuadra con lo que llevan en el historial. Caso del 16/11, un
técnico, solo trabajos con cliente: el panel le daba un ✓ ≈ 27 % y su trabajo real rondaba el 75 %. Causas:

1. **La marca «por cerrar» pierde el trabajo hecho.** La marca (spec 2026-07-08-por-cerrar-carga) dice «la mayoría ya está
   hecha, solo falta cerrar el móvil» y la asignación pasa a pesar 1/12. Se diseñó cuando la carga solo medía lo pendiente.
   Con la capacidad diaria («hecho hoy», 0.16.0) el día que se marca la asignación deja de pesar en pendiente pero no suma
   nada al hecho, y el día que se cierra suma solo 1/12: **los 11/12 no aparecen como hechos ningún día**. Ese técnico tenía
   tres trabajos completos marcados «por cerrar» (uno de 4 piezas a lo largo del día, dos chasis de 4 y 5 piezas).
2. **El tope de normales del servidor es 25 y debía ser 20.** El 24/07 se bajó a 20 en el cliente JavaFX (`e90d9a6`); al
   portar la carga al servidor el 22/09 (`8e3ada8`) se copió el 25 antiguo. Cada normal pesa un 20 % menos de lo debido.

Fuera de este cambio, y explicados al usuario: asignaciones con piezas cambiadas sin pulsar «Terminar» (cuentan como
pendientes, es lo correcto), reparaciones sin asignación (la carga no las ve), el panel arranca en «Pedidos» y el historial
enseña una fila por pieza, no por móvil.

## Qué cambia

### 1. Fecha de la marca

- BD: `ALTER TABLE Reparacion ADD COLUMN POR_CERRAR_AT DATETIME NULL` (`sql/migracion-por-cerrar-at.sql`, y en
  `crear_bd.sql`). Aditiva: la web y el servidor viejos la ignoran.
- `PATCH /api/reparaciones/asignaciones/{idRep}/por-cerrar`: al marcar pone `POR_CERRAR = TRUE, POR_CERRAR_AT = NOW()`; al
  quitar, `POR_CERRAR = FALSE, POR_CERRAR_AT = NULL`. Volver a marcar pone la hora nueva. Sigue sin tocar `UPDATED_AT`.
- Las consultas de asignaciones (abiertas y completadas hoy) devuelven `POR_CERRAR_AT`; `ReparacionResumen` lo lleva.
- **Sin relleno de las marcas viejas**: una «por cerrar» sin fecha se trata como marcada antes de hoy, que es lo mismo que
  pasa ahora. Solo pierden el 11/12 las marcas anteriores a esta versión, como ya ocurre.

### 2. Cálculo (`CargaTecnicos.calcularDia`)

Solo para asignaciones de reparación (`A%`, no `AG`/`AP`) con `POR_CERRAR`. «Marcada hoy» = `POR_CERRAR_AT` desde el inicio
de hoy en Madrid, con el mismo corte y la misma comparación que ya usa `FECHA_FIN` para las completadas hoy. `peso` es el de
su tipo (normal o chasis):

| Caso | Hecho hoy | Pendiente |
|---|---|---|
| Marcada hoy, abierta | 11/12 · peso | 1/12 · peso |
| Marcada hoy, cerrada hoy | peso entero | — |
| Marcada otro día o sin fecha, abierta | — | 1/12 · peso |
| Marcada otro día o sin fecha, cerrada hoy | 1/12 · peso | — |

- La regla de «solicitud de pieza pendiente libera carga» manda igual que ahora: si una abierta está en espera de pieza, no
  suma ni en pendiente ni el 11/12 en hecho.
- Alcance «Pedidos»: igual que ahora, solo las que tienen cliente.
- **Desglose del tooltip**: la marcada hoy y abierta cuenta como «por cerrar» en los dos tramos («Hecho hoy» y
  «Pendiente»), para que los números de cada tramo cuadren con su porcentaje.
- `PrediccionGlass.carga` suma hecho + pendiente con `fraccion9h`; se ajusta para dar el mismo total que `calcularDia`
  (una marcada hoy y abierta pesa entera, una marcada hoy y cerrada hoy pesa entera).

### 3. Tope de normales

`CargaTecnicos.TOPE_NORMALES_9H = 20` en el servidor (comentario al día, como el del cliente JavaFX). Lo usan también
`PrediccionGlass` y el modal de asignar.

## Pruebas

- `CargaTecnicosTest`: los cuatro casos de la tabla, para normal y para chasis; marcada hoy y abierta en espera de pieza;
  sin fecha; alcance «Pedidos» sin cliente; desglose de los dos tramos; tope 20 (una normal en lunes = 5 %).
- Corte de medianoche: marcada a las 23:59 de ayer en Madrid no cuenta como hoy; a las 00:00 sí (reloj fijo, como el test de
  zona horaria que ya existe).
- DAO: el PATCH pone y quita `POR_CERRAR_AT`; las consultas de asignaciones lo devuelven.
- `PrediccionGlassTest`: el total coincide con `calcularDia` con una marcada hoy.
- `mvn test` completo (incluye el test de arranque del contexto).

## Despliegue

Orden: migración (aditiva) → servidor → web con el número 0.9.36. Corte despreciable: vale en horario. Vuelta atrás: el
servidor anterior ignora la columna.
