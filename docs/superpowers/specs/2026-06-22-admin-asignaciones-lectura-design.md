# Admin: ver asignaciones en solo lectura — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

El admin, en la vista de reparaciones, gana un sidebar con **Historial** y **Asignaciones** (las generales, de todos los técnicos, con toggle Reparaciones/Pulidos). Las asignaciones se muestran en **solo lectura**: el admin solo consulta la tabla, sin crear, borrar, reasignar, editar comentario ni marcar urgente. El admin **no** ve el panel "Pendientes" (no aplica, no es técnico que repare).

## Restricción clave

Esto toca controladores **compartidos** con el supertécnico (`PendientesSuperTecnicoController`, `PulidoSuperTecnicoController`). El cambio debe ser **quirúrgico**: el modo solo lectura es un flag con valor por defecto `false`, y **solo el admin lo activa**. El supertécnico nunca lo llama, por lo que su comportamiento queda **idéntico**. Cualquier regresión en la vista del supertécnico es un fallo del diseño.

## Alcance

- **Solo cliente.** El servidor no cambia: la config de seguridad es `login permitAll` + `anyRequest authenticated`, y `GET /api/reparaciones/asignaciones` no tiene `@PreAuthorize`, así que el admin (autenticado) ya puede leerlo. Tampoco hay cambios de BD.
- **Fuera de alcance:** panel "Pendientes" para el admin; cualquier acción de escritura del admin sobre asignaciones; refactor de unificación admin/supertécnico.

## Arquitectura

Se reutilizan los paneles del supertécnico (`PendientesSuperTecnicoView`/`Controller` y `PulidoSuperTecnicoView`/`Controller`) mediante un modo solo lectura, embebidos en la vista del admin con `fx:include` — igual que ya hace el supertécnico. Una sola tabla de asignaciones como fuente de verdad.

## Componentes

### 1. Modo solo lectura en los sub-controllers

`PendientesSuperTecnicoController` y `PulidoSuperTecnicoController` reciben un método nuevo:

```java
public void setSoloLectura(boolean soloLectura)
```

- Campo `private boolean soloLectura = false;` (default preserva el comportamiento actual).
- Cuando se llama con `true`:
  - Oculta la **columna de acciones** (papelera/borrar).
  - Oculta el **botón de crear asignación** (lote).
  - Desactiva el **menú contextual de escritura** y el **doble clic** que abren editar/reasignar.
  - Desactiva **marcar urgente**.
- Se mantienen activos: tabla, filtros, buscador, copiar celda (lectura), columna "Asignado por".
- El método aplica los cambios de visibilidad/estado en el momento de invocarse (después de `initialize()`), porque el admin lo llama tras cargar el `fx:include`.

### 2. Sidebar y navegación en la vista admin

`ReparacionViewAdmin.fxml`:
- Se añade un **sidebar** con dos botones: **Historial** y **Asignaciones** (sin "Pendientes").
- El panel **Historial** es el actual (toggle Reparaciones/Pulidos), sin cambios funcionales — solo queda envuelto en la nueva estructura de navegación.
- El panel **Asignaciones** (nuevo) embebe `PendientesSuperTecnicoView.fxml` y `PulidoSuperTecnicoView.fxml` vía `fx:include`, con su toggle Reparaciones/Pulidos.

`ReparacionControllerAdmin`:
- Maneja la navegación Historial ↔ Asignaciones (mostrar/ocultar paneles).
- En `initialize()` (o al cargar), obtiene los sub-controllers inyectados por `fx:include` y llama `setSoloLectura(true)` en ambos.
- La carga de datos de asignaciones la hacen los propios sub-controllers (llaman a `GET /api/reparaciones/asignaciones`), sin lógica nueva en el admin.

## Flujo de datos

Admin entra en "Asignaciones" → el panel embebido (`PendientesSuperTecnicoController` en modo lectura) carga `GET /asignaciones` → muestra la tabla sin controles de escritura. Igual para pulidos con `PulidoSuperTecnicoController`.

## Manejo de errores

Sin rutas de error nuevas: se reutiliza el manejo de los sub-controllers (alertas existentes ante fallo de red). El modo lectura solo afecta a visibilidad/estado de controles de UI.

## Testing

UI sin tests automáticos (patrón del proyecto); validación manual:

**Como admin:**
1. La vista de reparaciones muestra sidebar con Historial y Asignaciones (sin "Pendientes").
2. En Asignaciones: se ve la tabla general (de todos) con toggle Reparaciones/Pulidos; **no** hay papelera, ni botón de crear, ni reasignar/editar/urgente.
3. Filtros, buscador y columna "Asignado por" funcionan (lectura).
4. Historial sigue funcionando como antes.

**Como supertécnico (regresión, crítico):**
5. Su vista de asignaciones sigue **idéntica**: papelera, crear, reasignar, editar comentario y marcar urgente, todo operativo.

## Despliegue

Solo recompilar y distribuir el **cliente**. El servidor y la BD no cambian.
