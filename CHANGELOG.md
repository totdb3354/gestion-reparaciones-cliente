# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

_(cambios para la próxima versión)_

## [0.11.0] - 2026-06-25

### Added
- **Filtro de Cliente** en el historial agrupado (con opción "(Sin cliente)"), en los tres roles.
- **Filtro de Pieza** en el historial plano (Glass / Pantalla / Marco / Batería / Cámara / Chasis / Otros), en los tres roles.
- **Urgente automático por cliente vencido**: las reparaciones pendientes con cliente que pasan de día se marcan **urgentes** solas (tarea del servidor a las 00:00 Europe/Madrid).
- **Orden de prioridad** en pendientes: urgente → con cliente → normal (Asignaciones y "Mis pendientes").

### Changed
- Asignar una reparación **con cliente** ya **no** la marca urgente automáticamente (ahora lo hace la regla de vencimiento).
- **Barra de filtros responsive**: los filtros se reacomodan a otra línea en pantallas estrechas; el badge de conteo va junto al título y los botones de acción pasan a la fila de filtros (consistente en todas las vistas).
- El contador deja de limitarse a "999+" (muestra el número real).
- Las **cabeceras de columna ya no reordenan** al hacer clic en historial/pendientes/pulidos (el orden lo llevan los filtros y la prioridad).

## [0.10.0] - 2026-06-24

### Added
- **Gestión de clientes**: catálogo de clientes vinculados a los teléfonos por IMEI, con
  columna _Cliente_ en Asignaciones, "Mis pendientes" e Historial; selector de cliente en el
  modal de asignación; edición del cliente por IMEI desde Asignaciones e historial agrupado;
  borrado de clientes que no tengan teléfonos asociados. Permisos: el **SUPERTÉCNICO** gestiona;
  **ADMIN** y **TÉCNICO** lo ven en solo lectura.
- **iPhone serie 17 e iPhone Air**: reconocimiento, ordenación y traducción de los modelos
  iPhone 17, 17 Pro, 17 Pro Max e iPhone Air en el catálogo de piezas.
- **Filtro de piezas activas**: el modal de reparación solo muestra SKU y modelos activos;
  desactivar una pieza la oculta de las reparaciones.
- **Columna "Asignado por"**: indica quién asignó cada reparación.
- **Filtro por técnico** en la vista agrupada del historial (admin y supertécnico).
- **Borrar pendientes propias** desde la vista (solo supertécnico).
- **Urgentes al inicio** de la lista de pendientes, con reordenación en vivo.
- **Logs de auditoría enriquecidos**: filtros en el servidor, filtro por acción, doble clic
  para ver el detalle, motivo obligatorio al borrar y registro del marcado/desmarcado de
  revisión logística.
- **Exportación CSV** con fecha y hora en el nombre sugerido (`yyyy-MM-dd_HH-mm`).
- **Validación de nombre de técnico único** al crear usuarios (en vivo y al enviar).

### Changed
- El **ADMIN** ve las asignaciones en **solo lectura** (con sidebar Historial/Asignaciones),
  en paridad con el supertécnico.
- En el detalle del historial se muestran por defecto las reparaciones de otros técnicos.

### Fixed
- **Zonas horarias**: las fechas en UTC del servidor se convierten a Europe/Madrid en la interfaz.
- Resaltado correcto de la fila seleccionada en la vista agrupada (técnico).
- Menú contextual bloqueado correctamente en modo solo lectura (solo "Copiar celda").
- Limpieza de residuos de nombre en celdas de técnico vacías.

### Removed
- Código muerto (`actualizarTecnico`).

---

_Las versiones 0.9.1 y anteriores no están detalladas aquí: el changelog se inició en la 0.10.0.
Para su historial, consulta los tags de Git._

[Unreleased]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.11.0...HEAD
[0.11.0]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.9.1...v0.10.0
