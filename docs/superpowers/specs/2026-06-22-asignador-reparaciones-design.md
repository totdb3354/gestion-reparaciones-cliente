# Mostrar quién asignó la reparación — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

En las asignaciones y en el historial de reparaciones se muestra **quién asignó** la tarea al técnico. Si la asignación se reasigna a otro técnico, se muestra **quién la reasignó** (solo el último responsable, reemplaza al anterior). Objetivo: que el técnico sepa quién le asignó sus tareas.

El "asignador" se identifica por su **técnico** (`Tecnico.ID_TEC`), no por su usuario, porque:
- Solo el rol SUPERTECNICO asigna/reasigna, y los supertécnicos tienen técnico asociado.
- `UsuarioPrincipal` ya expone `getIdTec()` → no hace falta lookup extra.
- Mostrar `Tecnico.NOMBRE` (nombre real, ej. "Diego") es más claro que el login, y es consistente con la columna "Técnico" (el que recibe) que ya usa `Tecnico.NOMBRE`.

## Decisiones de alcance

- **Dónde se muestra:** pendientes (todos los roles) **e** historial de reparaciones finalizadas. Registro permanente.
- **Qué se muestra:** solo el **último** asignador (un único nombre). No historial completo de reasignaciones.
- **Datos antiguos:** las asignaciones/reparaciones que ya existen quedan con el campo a `NULL` → la UI muestra `—`. No se rellenan retroactivamente.
- **Fuera de alcance:** pulidos (otro flujo); cadena/historial completo de reasignaciones.

## Arquitectura

Las asignaciones (`ID_REP LIKE 'A%'`, sin `FECHA_FIN`) y las reparaciones finalizadas (`ID_REP LIKE 'R%'`) conviven en la **misma tabla `Reparacion`**. Por tanto el dato del asignador debe persistirse en esa tabla; no hay forma fiable de mostrarlo sin una columna (la alternativa de derivarlo de los logs es frágil y se descartó).

## Cambios en BD

Migración (`gestion-reparaciones-servidor/src/main/resources/db/migracion-asignador.sql`):

```sql
ALTER TABLE Reparacion ADD COLUMN ID_TEC_ASIGNA INT NULL,
  ADD CONSTRAINT fk_rep_tec_asigna FOREIGN KEY (ID_TEC_ASIGNA) REFERENCES Tecnico(ID_TEC);
```

- `NULL` permitido: datos antiguos y posibles asignadores sin técnico asociado.
- Se aplica manualmente en preproducción (único entorno) antes de desplegar el servidor.

## Cambios en servidor

### Escritura (`ReparacionDAO` + `ReparacionController`)
El valor a escribir es `principal.getIdTec()` (Integer nullable; si null, queda null).

- `insertarAsignacion` — al crear una asignación, guarda `ID_TEC_ASIGNA` = técnico que la crea.
- `marcarIncidenciaYAsignar` — la nueva asignación registra como asignador al supertécnico que marca la incidencia.
- `actualizarAsignacion` — **solo si cambia el `ID_TEC`** (reasignación real) actualiza `ID_TEC_ASIGNA` al reasignador. Si solo cambia el comentario, no se toca.
- `insertarCompleta` y `guardarFilaIndividual` — al completar, las nuevas filas `R*` **copian** el `ID_TEC_ASIGNA` leído de la asignación origen (`idAsignacion`), para que el historial conserve quién asignó.

Los métodos del DAO que insertan/actualizan reciben el `idTecAsigna` como parámetro nuevo; el controller lo obtiene de `principal.getIdTec()`.

### Lectura (`ReparacionDAO`)
- `ASIGNACION_SELECT` y `HISTORIAL_SELECT` añaden `LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC` y traen `ta.NOMBRE AS NOMBRE_TEC_ASIGNA`.
- `RESUMEN_MAPPER` rellena el nuevo campo (null → null).

### Modelo (`ReparacionResumen`, servidor)
- Nuevo campo `String nombreTecnicoAsigna` con getter; añadido al constructor/mapper.

## Cambios en cliente

### Modelo (`ReparacionResumen`, cliente)
- Nuevo campo `String nombreTecnicoAsigna` con getter (lo rellena Gson desde el JSON del servidor).

### UI — nueva columna dedicada "Asignado por"
Columna de texto que muestra `nombreTecnicoAsigna`; si es null muestra `—`. Se descarta incrustar el dato en otra celda (tipo badge "reutilizado") porque el asignador es un nombre, no un flag booleano.

- **Pendientes:** `PendientesTecnicoController`, `PendientesSuperTecnicoController` (+ sus FXML). Las listas de pendientes solo tienen filas individuales, así que la columna siempre aplica.
- **Historial:** `ReparacionControllerTecnico`, `ReparacionControllerSuperTecnico`, `ReparacionControllerAdmin` (+ sus FXML). El historial tiene modo **plano** (fila por reparación) y **agrupado por IMEI** (modo maestro, varias reparaciones por fila). La columna "Asignado por" se muestra **solo en modo plano** — en agrupado el dato sería ambiguo (varias reparaciones con posible asignador distinto), así que ahí queda vacío/oculto.

## Testing

- Unitario de modelo: `ReparacionResumenTest` verifica el getter `nombreTecnicoAsigna`.
- Manual:
  1. Crear asignación → "Asignado por" muestra tu nombre.
  2. Reasignar a otro técnico → cambia al reasignador.
  3. Editar solo el comentario → no cambia el asignador.
  4. Completar la reparación → el historial conserva el nombre del asignador.
  5. Asignación/reparación antigua → muestra `—`.

## Despliegue

- Aplicar `migracion-asignador.sql` en preproducción antes de subir el servidor.
- Cliente y servidor se despliegan juntos.
