# Spec: Revisión logística por IMEI

**Fecha:** 2026-06-17
**Área:** Vista agrupada de IMEIs (modo Maestro)

---

## Resumen

Añadir una columna **"Revisión"** en la vista agrupada de IMEIs con un ToggleSwitch (On/Off) que permite al SuperTécnico confirmar manualmente que un dispositivo ha sido revisado físicamente. El estado se persiste en BD y tiene reglas automáticas de reset y bloqueo basadas en asignaciones activas.

---

## Lógica de negocio

### Estados posibles de un IMEI

| `REVISION_LOGISTICA` en BD | Asignaciones activas | Toggle mostrado | Toggle editable |
|---|---|---|---|
| `0` (no revisado) | Sí | OFF | No (deshabilitado) |
| `1` (revisado) | Sí | OFF | No (deshabilitado) |
| `0` (no revisado) | No | OFF | Sí (solo SuperTécnico) |
| `1` (revisado) | No | ON | Sí (solo SuperTécnico) |

> Cuando hay asignaciones activas, el toggle **siempre** se muestra en OFF independientemente del valor en BD.

### Flujo completo

1. IMEI entra en asignación → servidor pone `REVISION_LOGISTICA = 0` automáticamente; toggle bloqueado en OFF.
2. Asignación se cierra → toggle se habilita, estado permanece en OFF.
3. SuperTécnico revisa físicamente el dispositivo → pone manualmente el toggle a ON.
4. Se crea una nueva asignación para ese IMEI → vuelta al paso 1.

### Qué es "asignación activa"

Fila en tabla `Reparacion` donde:
```sql
ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND FECHA_FIN IS NULL
```

Incluye reparaciones normales, incidencias y solicitudes de pieza pendientes — cualquier asignación abierta.

---

## Roles

| Rol | Ve columna | Puede editar toggle |
|---|---|---|
| SuperTécnico | Sí | Sí (cuando habilitado) |
| Admin | Sí | No (solo lectura) |
| Técnico | No aplica (sin vista agrupada) | — |

---

## Cambios requeridos

### 1. Base de datos

```sql
ALTER TABLE Telefono
  ADD COLUMN REVISION_LOGISTICA TINYINT(1) NOT NULL DEFAULT 0;
```

### 2. Servidor — `HISTORIAL_SELECT` (ReparacionDAO)

Añadir al SELECT existente dos columnas nuevas (el JOIN con `Telefono` ya existe):

```sql
tel.REVISION_LOGISTICA,
(SELECT COUNT(*) FROM Reparacion r2
 WHERE r2.IMEI = r.IMEI
   AND r2.ID_REP LIKE 'A%'
   AND r2.ID_REP NOT LIKE 'AP%'
   AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES
```

### 3. Servidor — `ReparacionDAO.insertar()` (creación de asignación)

Al insertar cualquier asignación nueva, ejecutar en la misma transacción:

```sql
UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?
```

Esto garantiza que si un IMEI estaba marcado como revisado y entra en una nueva asignación, el flag se resetea automáticamente.

### 4. Servidor — nuevo endpoint en `TelefonoController`

```
PUT /api/telefonos/{imei}/revision-logistica
Body: { "revisado": true | false }
Rol requerido: SUPERTECNICO
```

**Lógica del endpoint:**
1. Comprobar si el IMEI tiene asignaciones activas (`COUNT(*) WHERE ID_REP LIKE 'A%' ...`).
2. Si las tiene → devolver `409 Conflict` con mensaje: `"El IMEI tiene asignaciones activas"`.
3. Si no las tiene → `UPDATE Telefono SET REVISION_LOGISTICA = ? WHERE IMEI = ?`.

### 5. Servidor — `TelefonoDAO`

Añadir método:
```java
public void actualizarRevisionLogistica(String imei, boolean revisado)
```

Y método de comprobación (reutilizable):
```java
public boolean tieneAsignacionesActivas(String imei)
```

### 6. Cliente — modelos

**`ReparacionResumen`** — dos campos nuevos:
- `boolean revisionLogistica`
- `boolean tieneAsignaciones`

Leídos en `RESUMEN_MAPPER` desde las columnas `REVISION_LOGISTICA` y `TIENE_ASIGNACIONES`.

**`GrupoImei`** — dos campos derivados del primer elemento de la lista:
- `boolean revisionLogistica` → `reparaciones.get(0).isRevisionLogistica()`
- `boolean tieneAsignaciones` → `reparaciones.get(0).isTieneAsignaciones()`

### 7. Cliente — `ReparacionDAO` (HTTP)

Añadir método:
```java
public void actualizarRevisionLogistica(String imei, boolean revisado) throws Exception
```

Llamada `PUT /api/telefonos/{imei}/revision-logistica` con body JSON.

### 8. Cliente — vista agrupada (`ReparacionControllerAdmin` y `ReparacionControllerSuperTecnico`)

**Nueva columna `colRevision`:**
- Ancho fijo, título "Revisión"
- Solo visible en modo Maestro (filas `GrupoImei`); en filas `ReparacionResumen` de detalle, celda vacía.

**Componente visual — ToggleSwitch custom:**
- Basado en `ToggleButton` con CSS que simula un switch deslizante.
- Estado ON: fondo verde, texto "OK".
- Estado OFF: fondo gris, texto "—".
- Deshabilitado: opacidad reducida, cursor normal (sin mano).

**Comportamiento del toggle:**
- Si `grupo.isTieneAsignaciones()` → muestra OFF, `setDisable(true)`.
- Si no tiene asignaciones y rol ≠ SuperTécnico → muestra estado real, `setDisable(true)`.
- Si no tiene asignaciones y rol = SuperTécnico → muestra estado real, `setDisable(false)`.
- Al cambiar el toggle: llamar `actualizarRevisionLogistica(imei, nuevoValor)`.
  - En caso de `409` → mostrar alerta ("Este IMEI tiene asignaciones activas, no se puede marcar como revisado"), refrescar la fila desde servidor.
  - En caso de error de red → revertir toggle al estado anterior, mostrar error genérico.

### 9. Cliente — exportación CSV

Añadir columna **"Revisión logística"** con valor `"Sí"` / `"No"` junto al resto de columnas del historial agrupado.

---

## Concurrencia

El servidor valida la ausencia de asignaciones activas en el mismo `PUT` antes de escribir (opción B acordada). Si entre que el cliente cargó la vista y pulsó el toggle se creó una asignación, el servidor devuelve `409` y el cliente refresca la fila. No hay window de inconsistencia persistente.

---

## Fuera de alcance

- Vista de Técnico (no tiene vista agrupada de IMEIs).
- Historial de cambios de `REVISION_LOGISTICA` (no se loguea en `Log_actividad`).
- Filtro por "revisado / no revisado" en la vista agrupada (se puede añadir en el futuro).
