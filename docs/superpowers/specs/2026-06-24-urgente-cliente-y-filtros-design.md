# Diseño — Urgente por cliente vencido + filtros de cliente y pieza

**Fecha:** 2026-06-24
**Versión objetivo:** 0.11.0 (minor)
**Repos afectados:** cliente (puntos 1, 3, 4) y servidor (punto 2). Sin migración de BD.

## Objetivo

Cambiar cómo se marca el estado **urgente** de las asignaciones y añadir dos filtros nuevos al historial:

1. Asignar con cliente **deja de marcar urgente automáticamente**.
2. Nueva (y **única**) automatización del urgente: una reparación **pendiente con cliente** que pasa de día (vencida >1 día) se marca **urgente** sola, vía un job en el servidor.
3. Filtro de **Cliente** en el historial **agrupado** (todos los roles).
4. Filtro de **Pieza** en el historial **plano** (todos los roles).

El resto del estado urgente sigue siendo **manual** (menú contextual "Marcar/Quitar urgente").

---

## Contexto actual (lo que hay hoy)

- **Urgente automático por cliente:** en `PendientesSuperTecnicoController` (modal de asignación, sobre la línea 1256):
  ```java
  Integer idCli = e.cliente != null ? e.cliente.getIdCli() : null;
  telefonoDAO.insertar(e.imei, e.modeloCode, idCli);
  boolean urgente = idCli != null;   // ← se elimina esta automatización
  ... insertarAsignacion(..., urgente);
  ```
- **Urgente** se persiste en el flag `URGENTE` de `Reparacion` y se lee con `ReparacionResumen.isUrgente()`. Los pendientes ordenan urgentes arriba (`datos.sort(Comparator.comparing(ReparacionResumen::isUrgente).reversed())`) y pintan un badge "Urgente". Cambio manual: menú contextual → `reparacionDAO.actualizarUrgente(idRep, nuevoEstado)` → `PATCH /api/reparaciones/asignaciones/{idRep}/urgente`.
- **Datos disponibles por fila pendiente:** `cliente` (nombre), `fechaAsig`, `imei`, `telefonoUpdatedAt`.
- **Cliente del teléfono:** `Telefono.ID_CLI` (FK, nullable). Una reparación se enlaza a cliente vía `Reparacion.IMEI → Telefono.ID_CLI`.
- **Filtros del historial agrupado** (vistas `ReparacionControllerSuperTecnico`, `ReparacionControllerAdmin`, `ReparacionControllerTecnico`): barra `HBox` con Título · `filtroImei` · `filtroTecnico` (`MultiSelectComboBox`, solo super/admin) · Desde · Hasta · `filtroIncidencias` · Limpiar · contador · toggle Agrupado/Plano.
- **Categoría de pieza** ya existe como `FormularioReparacionController.traducirTipo(prefijo)`:
  `bat`→Batería, `cha`→Chasis, `g`→Glass, `cam`→Cámara, `lcd`→Pantalla, `mc`→Marco, `otro`→Otros.

---

## Punto 1 — Asignar con cliente ya no marca urgente *(cliente)*

**Cambio:** en `PendientesSuperTecnicoController`, al guardar la asignación, `urgente` pasa a ser **siempre `false`** (se elimina `boolean urgente = idCli != null`). El cliente se sigue guardando igual (`telefonoDAO.insertar(imei, modelo, idCli)`); solo se desacopla del urgente.

**Efecto:** al asignar, la reparación nace **no urgente**. El urgente llega después, o a mano, o por el job (punto 2).

---

## Punto 2 — Urgente automático por cliente vencido *(servidor)*

### Regla
Una reparación se marca `URGENTE=true` cuando, simultáneamente:
- está **pendiente** (no finalizada — mismo criterio que la lista de asignaciones pendientes),
- es una **reparación** (no pulido),
- su teléfono **tiene cliente** (`Telefono.ID_CLI` no nulo),
- su `FECHA_ASIG`, convertida a **Europe/Madrid**, es de un **día calendario anterior** a *hoy* (no son 24h; es el cambio de día a las 00:00), y
- **`URGENTE` ya es `false`** (no se re-escribe lo que ya está urgente).

### Mecanismo
Una **tarea programada** en el servidor (Spring `@Scheduled`, cron diario a las **00:00 `Europe/Madrid`**) ejecuta un `UPDATE` que aplica la regla. **Una sola autoridad** → con varios supertécnicos conectados no hay carreras ni peticiones duplicadas; los clientes solo leen el flag ya puesto en su siguiente refresco.

Cálculo del límite de fecha: en Java con `LocalDate.now(ZoneId.of("Europe/Madrid"))` (o `CONVERT_TZ` en SQL); la app guarda fechas en UTC, así que el corte debe calcularse en hora de Madrid.

`UPDATE` conceptual:
```sql
UPDATE Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
SET r.URGENTE = TRUE
WHERE <r es asignación pendiente (no finalizada)>
  AND t.ID_CLI IS NOT NULL
  AND DATE(CONVERT_TZ(r.FECHA_ASIG,'+00:00','Europe/Madrid')) < <hoy en Madrid>
  AND r.URGENTE = FALSE;
```
> El criterio de "pendiente" debe reutilizar la misma definición que ya usa el endpoint de asignaciones pendientes (no duplicar la lógica).

### Comportamiento de quitar a mano
- **Quitar urgente** (menú contextual) sigue funcionando; pone `URGENTE=false`.
- Si la reparación **sigue con cliente y sigue vencida**, la **próxima medianoche** el job la vuelve a marcar (misma regla; re-nag intencionado).
- Si se le **quita el cliente** al teléfono → deja de cumplir la condición → **no se vuelve a marcar**.

### Logs
El job escribe **un único resumen por ejecución** en el **log de aplicación del servidor** (SLF4J/logback), p.ej. `AUTO_URGENTE: N asignaciones marcadas`. **No** escribe en la tabla de auditoría `Log_Actividad` (que es para acciones de usuario y cuyo `ID_USU` es NOT NULL — evitamos inventar un usuario "sistema"). Los cambios **manuales** de urgente siguen registrándose en `Log_Actividad` como hasta ahora.

### Sin cambios de esquema
Usa el flag `URGENTE` ya existente. No se añaden columnas.

### Limitación conocida
Si el servidor está caído a las 00:00, las que vencen ese día no se marcan hasta la siguiente ejecución del cron. Aceptable (no se añade catch-up al arranque para no re-marcar a media jornada lo que un usuario haya quitado).

---

## Punto 3 — Filtro de Cliente en el historial agrupado *(cliente)*

- **Control:** `MultiSelectComboBox` "Cliente", mismo estilo que `filtroTecnico`, ubicado **justo después de `filtroTecnico`** (los dos multiselect "¿de quién?" juntos).
- **Roles:** las 3 vistas del historial (supertécnico, admin, técnico).
- **Visibilidad:** **solo en modo Agrupado**; al cambiar a Plano se **oculta** (`visible`/`managed=false`) y se desactiva su predicado.
- **Opciones:** los clientes presentes en los datos cargados + una entrada **"(Sin cliente)"** que filtra las filas cuyo teléfono no tiene cliente.
- **Filtrado:** muestra solo las filas agrupadas cuyo cliente esté entre los seleccionados (o sin cliente, si está marcada esa opción). Sin selección = no filtra.
- **Reset:** "Limpiar filtros" lo vacía.

---

## Punto 4 — Filtro de Pieza en el historial plano *(cliente)*

- **Control:** `MultiSelectComboBox` "Pieza", mismo estilo, **solo en modo Plano** (oculto en Agrupado) → simétrico con el filtro de Cliente.
- **Roles:** las 3 vistas.
- **Categorías:** derivadas del **prefijo del SKU** de cada fila (componente), traducidas con la misma lógica que `traducirTipo`: **Glass, Pantalla, Marco, Batería, Cámara, Chasis, Otros**. Se ofrecen las categorías presentes en los datos.
  - Derivación del prefijo: detectar con cuál de los prefijos conocidos (`otro`, `cha`, `cam`, `bat`, `lcd`, `mc`, `g`) empieza el SKU (match más largo primero para evitar ambigüedad).
- **Filtrado:** muestra solo las filas planas cuya categoría de pieza esté seleccionada. Sin selección = no filtra.
- **Reset:** "Limpiar filtros" lo vacía.

---

## Barra de filtros responsive *(cliente, transversal a 3 y 4)*

La barra de filtros del historial pasa a ser **responsive**: los controles se reacomodan automáticamente a la siguiente línea cuando el ancho es insuficiente (p.ej. `FlowPane` o equivalente), para no romperse en monitores de baja resolución. Los filtros dependientes del modo (Cliente en Agrupado, Pieza en Plano) aparecen/desaparecen según el toggle.

---

## Testing

- **Servidor (punto 2):** test del DAO/servicio que aplica la regla del job sobre datos de prueba:
  - pendiente + cliente + `FECHA_ASIG` de ayer + `URGENTE=false` → se marca.
  - pendiente + cliente + `FECHA_ASIG` de hoy → **no** se marca.
  - pendiente **sin** cliente + vencida → **no** se marca.
  - finalizada + cliente + vencida → **no** se marca.
  - ya `URGENTE=true` → no se re-escribe (no cuenta en el resumen).
  - límite de día en `Europe/Madrid` (caso borde alrededor de medianoche / cambio de fecha).
- **Cliente (punto 1):** asignar con cliente → la reparación queda `urgente=false`.
- **Cliente (puntos 3 y 4):** tests de la lógica de filtrado (predicados) — por cliente, "(Sin cliente)", por categoría de pieza derivada del SKU — son funciones puras testeables sin UI, al estilo del test de `extraerModelo`.

## Fuera de alcance

- Pulidos (la regla del urgente y los filtros aplican a reparaciones).
- Cambios de esquema / migraciones.
- Persistir el resumen del job en la tabla de auditoría `Log_Actividad` (va al log de aplicación). Si en el futuro se quiere ver en la vista de Logs, se añadiría un usuario "sistema".

## Versionado

Release **0.11.0** (minor): nuevas funcionalidades + cambio de comportamiento del urgente. Toca cliente y servidor; sin migración de BD.
