# Borrador persistente del modal de reparación — Diseño

**Fecha:** 2026-06-12
**Item de backlog:** Item 2 (UAT junio 2026). Va después del Item 1 (ya hecho).

## Objetivo

Que lo que el técnico va metiendo en el modal de reparación por componente (sin llegar a completar la asignación) se guarde como **borrador** y persista aunque cierre la app. Al reabrir la asignación, recupera lo apuntado para terminarla.

## Estado actual (contexto)

El modal lo gestiona `FormularioReparacionController`, se abre por asignación (`abrir(imei, idRepAnterior, idAsignacion, onGuardado)` → `init(...)`). Su estado de entrada son las `FilaUI` por componente (cantidad, reutilizado, observación, marca de solicitud/agotado) y la sección `OtrasAccionesUI` (acciones "otro"), más el modelo seleccionado (`cbFiltroModelo`). Hoy, al cerrar con cambios sin guardar, muestra un diálogo "se perderán los cambios". Las solicitudes ya existentes de la asignación se cargan del servidor (`getSolicitudesPorAsignacion`). Gson ya es dependencia del cliente.

## Diseño

### Persistencia: en el servidor (BD)

Se elige persistencia en servidor (no local) por coherencia con la arquitectura cliente-servidor, robustez ante PCs compartidos/varios puestos, y limpieza ligada al ciclo de vida de la asignación.

**Tabla nueva `Reparacion_borrador`** — extensión 1:0..1 de `Reparacion` (entidad débil; `ID_REP` es PK y FK a la vez):
```sql
CREATE TABLE Reparacion_borrador (
    ID_REP     VARCHAR(30) NOT NULL,        -- la asignación (A*)
    CONTENIDO  JSON        NOT NULL,         -- el borrador serializado
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REP),
    CONSTRAINT fk_borrador_reparacion FOREIGN KEY (ID_REP)
        REFERENCES Reparacion (ID_REP) ON DELETE CASCADE
);
```
Un único borrador por asignación. Añadir la tabla a `sql/crear_bd.sql` y a `sql/reset-uat.sql` (borrar antes de las demás por la FK).

**Endpoints (siguen el patrón DAO-sobre-HTTP existente):**
- `PUT  /api/reparaciones/{idRep}/borrador` — upsert del borrador. Body: `{ contenido }`.
- `GET  /api/reparaciones/{idRep}/borrador` — devuelve `{ contenido }` o 404/null si no hay.
- `DELETE /api/reparaciones/{idRep}/borrador` — borra el borrador.

### Qué se guarda (CONTENIDO, JSON)

El borrador son los **inputs no guardados del técnico**, NO el estado ya persistido de la asignación (las solicitudes en BD se cargan aparte como hoy). Se serializa con Gson. Estructura:
- `modelo`: el modelo seleccionado.
- `filas`: por cada componente con input, `{ idCom, cantidad, reutilizado, observacion, solicitudNueva, descripcionSolicitud, agotadoConfirmado, descripcionAgotado }`.
- `otros`: lista de descripciones de las acciones "otro".

Se guarda como un **bloque entero** (nunca se consulta por dentro): por eso una columna JSON, no normalización.

### Cuándo se guarda

- **Auto-guardado debounced**: un temporizador (`PauseTransition` ~2-3s) que se reinicia en cada cambio del modal y dispara el `PUT` al expirar.
- **Flush al cerrar** el modal (por si el debounce no llegó a disparar).
- **Se elimina el diálogo "salir sin guardar"** del flujo de nueva reparación: al cerrar ya queda el borrador, no se pierde nada. (El diálogo del flujo de **edición** de un `R*` completado se mantiene — ese flujo no lleva borrador.)

### Recuperación

- En `init(...)`, tras cargar el estado normal de la asignación, se pide el borrador (`GET`). Si existe, se **aplica** sobre las `FilaUI`/`OtrasAccionesUI`/modelo, y se muestra un **indicador sutil "borrador recuperado"** (banda/etiqueta en el modal).
- **Robustez ante borrador obsoleto**: si un `idCom`/cantidad ya no es válido (stock cambió, SKU desaparecido), se aplica lo válido y se ignora lo que no, sin romper.

### Descarte (automático, sin botón manual)

El borrador se borra:
- **Tras un guardado exitoso** desde el modal (`insertarCompleta`/`agotarComponente`): los inputs ya son reales → el cliente hace `DELETE` del borrador.
- **Al borrar la asignación** (`eliminarAsignacion` → DELETE de la fila `Reparacion`): lo limpia el `ON DELETE CASCADE`.
- **Al reasignar** la asignación a otro técnico (`actualizarTecnico`, que es un UPDATE de `ID_TEC` y NO dispara cascade): el servidor borra el borrador explícitamente en esa operación.

No hay botón manual de "descartar" (el técnico sobrescribe el borrador a mano si quiere).

### Alcance

Solo el flujo de **nueva reparación** (por `idAsignacion`). La edición de un `R*` completado (`initEditar`) NO lleva borrador y se queda como está.

## Componentes afectados

**Servidor:**
- `sql/crear_bd.sql` + `sql/reset-uat.sql` — tabla `Reparacion_borrador`.
- `BorradorDAO` (nuevo) + endpoints `PUT/GET/DELETE` en un controller (p. ej. ampliar `ReparacionController` o uno nuevo `BorradorController`).
- `ReparacionDAO.actualizarTecnico` — borrar el borrador al reasignar.

**Cliente:**
- `BorradorDAO` (nuevo, HTTP) — `guardar(idRep, json)`, `getBorrador(idRep)`, `eliminar(idRep)`.
- `FormularioReparacionController` — serializar el estado a JSON (Gson), auto-guardado debounced + flush al cerrar, recuperar/aplicar el borrador en `init`, indicador "borrador recuperado", quitar el diálogo de salir sin guardar (flujo nuevo), borrar el borrador tras guardado exitoso.
- `FilaUI`/`OtrasAccionesUI` — getters/setters para volcar y restaurar su estado (probablemente ya hay getters; faltarán setters de restauración).

## Riesgos

- **Aplicar un borrador obsoleto** sobre un estado que cambió en BD (stock, solicitudes gestionadas). Mitigación: aplicar solo lo válido, ignorar lo inválido.
- **Doble conteo**: si el borrador no se borra tras un guardado, al reabrir se re-aplicarían inputs ya convertidos en `R*`. Mitigación: borrar el borrador tras todo guardado exitoso.
- **Restaurar el estado de las `FilaUI`** fielmente requiere setters de restauración que hoy quizá no existen (la maquinaria de solicitud/agotado es compleja). Es la parte más delicada del cliente.
- Volumen de llamadas del auto-guardado: trivial con debounce (un técnico, una asignación).

## Pruebas (manual)

1. Abrir una asignación, meter filas/cantidades/observaciones/acciones "otro", **sin completar**. Esperar ~3s.
2. Cerrar el modal (sin diálogo de "salir sin guardar") y reabrir la asignación → se recupera todo, con el indicador "borrador recuperado".
3. Cerrar la app entera y reabrir → el borrador sigue ahí.
4. Completar la reparación → el borrador se borra; reabrir (si la asignación sigue por solicitud pendiente) no re-aplica los inputs ya guardados.
5. Borrar la asignación (supertécnico) → el borrador desaparece (cascade).
6. Reasignar la asignación a otro técnico → el borrador se borra.
7. Borrador con un SKU/cantidad ya inválido (stock cambió) → se aplica lo válido sin romper.
8. Editar un `R*` completado → sigue mostrando su diálogo de "salir sin guardar" (no lleva borrador).

## Fuera de alcance

- Borrador para el flujo de edición de reparaciones completadas.
- Botón manual de "descartar borrador".
- Borrador para el modal de pulidos (solo el de reparación por componente).
