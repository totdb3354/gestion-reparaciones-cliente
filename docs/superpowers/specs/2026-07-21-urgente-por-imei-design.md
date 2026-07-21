# Urgente por IMEI (propagación en Reparacion) — Diseño

**Fecha:** 2026-07-21 · **Estado:** aprobado en brainstorm (pendiente review de spec)

## 1. Problema

`URGENTE` vive en `Reparacion` (una fila por asignación A/AG/AP) y cada fila va
por su cuenta: si se asigna un IMEI y se marca urgente, y después se asigna el
mismo IMEI a otra persona, la segunda asignación nace normal. Resultado:
asignaciones del mismo teléfono con urgencias contradictorias.

## 2. Decisión

**Opción B — propagación por IMEI dentro de `Reparacion`, sin ALTER.** El flag
se queda en su columna, pero deja de ser independiente por fila. Se descartó
mover el flag a `Telefono` (opción A): exigía ALTER + migración de ~10 SELECTs,
decidir el destino del dato histórico y programar el apagado; con la semántica
elegida (híbrida, §4) la propagación da el mismo resultado gratis.

**Invariante:** un IMEI con trabajos abiertos tiene UN solo estado de urgencia
— todas sus asignaciones abiertas lo comparten, en todas las vistas (pendientes
del súper, vista del técnico, CSV). Las cerradas conservan congelado el valor
con el que se cerraron (dato hoy no mostrado en ninguna vista; queda en BD).

## 3. Reglas de escritura (las tres puertas, todas server-side)

1. **Marcar/quitar a mano** — `PATCH /api/reparaciones/asignaciones/{idRep}/urgente`
   resuelve el IMEI de esa asignación y actualiza **todas las asignaciones
   abiertas de ese IMEI** (`FECHA_FIN IS NULL`) al valor pedido, en un solo
   UPDATE. La URL no cambia (compat cliente viejo: su toggle ahora propaga).
2. **Crear asignación** (reparación normal, glass, pulido, reasignación por
   incidencia): el servidor calcula `urgenteFinal = urgenteRequest OR
   existe-abierta-urgente-del-IMEI`. Además, **si `urgenteRequest = true` y el
   IMEI ya tenía abiertas no urgentes, se propagan a TRUE** (sin esto la
   incongruencia vuelve: nueva urgente + vieja normal). El pulido no tiene
   checkbox y no lo gana: hereda en silencio.
3. **Job diario** (`UrgenteAutomaticoJob`): mismo disparador de hoy —
   asignaciones rep/glass (`ID_REP LIKE 'A%' AND NOT LIKE 'AP%'`) pendientes,
   con cliente (`Telefono.ID_CLI IS NOT NULL`), `FECHA_ASIG` anterior al inicio
   de hoy (Madrid) — pero al cualificar un teléfono marca **todas** sus
   asignaciones abiertas, pulido incluido.

## 4. Ciclo de vida (híbrido, decisión de usuario)

- La urgencia vive solo en asignaciones abiertas → **al cerrarse el último
  trabajo abierto se apaga sola** (no queda fila que la porte). Un trabajo
  creado después nace normal salvo que se re-marque.
- **Desmarcable a mano** en cualquier momento (regla 1 con valor FALSE).
- Consecuencia asumida: no se puede pre-marcar urgente un teléfono **sin
  ningún trabajo abierto** (con esta semántica no existe esa urgencia).

## 5. Cliente (UI)

- Menú contextual de Pendientes ("Marcar/Quitar urgente",
  `PendientesSuperTecnicoController` ~L370): pasa a estar visible **también en
  filas de pulido** (actúa sobre el teléfono). Texto sin cambios.
- Checkbox urgente del modal de asignación: sin cambios de UI (la propagación
  es del servidor).
- Badges y CSV: sin cambios (leen el mismo campo, que ahora llega coherente).
  Las filas de pulido pueden lucir ahora el badge "Urgente" (heredado).

## 6. Compatibilidad y despliegue

- **Sin ALTER, sin migración**: los datos existentes quedan como están; la
  primera escritura de urgencia sobre un IMEI lo deja coherente.
- Cliente viejo + servidor nuevo: mismos endpoints; sus escrituras propagan
  (mejora silenciosa). Servidor viejo + cliente nuevo: sin ventana peligrosa
  (el cliente no cambia contratos). Orden de despliegue libre; servidor
  primero por costumbre.
- Limitación aceptada (concurrencia): la herencia en el alta lee el estado sin
  bloqueo; un alta cruzada en el mismo milisegundo con un marcado puede nacer
  con el valor previo. Se autocura en la siguiente escritura de urgencia del
  IMEI y con el job nocturno.

## 7. Testing

- Mockito puro (patrón `ProveedorDAOTest`/`UrgenteAutomaticoJobTest`):
  - UPDATE propagado del PATCH (SQL con `IMEI = ? AND FECHA_FIN IS NULL`).
  - Herencia en el insert (`urgenteRequest OR abierta-urgente`) y propagación
    inversa al crear con checkbox.
  - SQL del job: cualificación por rep/glass+cliente+cutoff, marcado de todas
    las abiertas del IMEI cualificado.
- Smoke manual: marcar en una fila → todas las del IMEI (incl. pulido) cambian;
  asignar 2º técnico a IMEI urgente → nace urgente; crear nueva con checkbox
  sobre IMEI con abiertas normales → todas urgentes; cerrar último trabajo →
  siguiente asignación nace normal.

## 8. Fuera de alcance (futuro explícito)

- **Incidencias por IMEI** (hoy `marcarIncidenciaYAsignar` opera por ID_REP):
  el usuario quiere replantearlas por IMEI más adelante; aquí la reasignación
  por incidencia solo hereda la urgencia como cualquier alta (regla 2).
- Pre-marcar urgente un teléfono sin trabajos abiertos (pediría flag en
  `Telefono`; se reevaluará si algún día hace falta).
- El urgente de compras (`ES_URGENTE` de `Compra_componente`/`Compra_otro`) es
  otro dominio y no se toca.
