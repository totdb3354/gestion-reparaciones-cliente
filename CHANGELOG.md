# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

_(cambios para la próxima versión)_

## [0.14.0] - 2026-07-07

### Added
- **Flag de chasis en asignaciones de reparación**: check en el modal de asignación (solo cola Reparación, se resetea entre IMEIs), marca discreta bajo la píldora de tipo en Asignaciones y Mis pendientes, toggle por menú contextual (SUPERTECNICO, con log `MARCAR_CHASIS`), columna `ES_CHASIS` en BD. Requiere servidor actualizado.
- **Columna Tipo en Mis pendientes** (píldora Reparación/Glass, paridad con Asignaciones), vía celda compartida `TipoTrabajo.celdaTipoConChasis()`.
- **Editor de pedidos de otros componentes** (`FormularioOtroPedidoEditar`): concepto, proveedor, cantidad, precio y divisa, en pendiente/en_camino/recibido; sin gestión de urgencia. Entrada por menú contextual, con concurrencia optimista.
- **Filtro por cliente en Asignaciones** (multiselección, "(Sin cliente)", mismo patrón que IMEIs/Historial).
- **CSV de pedidos con precio unidad, divisa, total EUR y estado**; export propio `pedidos_otros` cuando el toggle Otros está activo (antes exportaba la tabla de componentes).
- **Selección persistente en la vista IMEIs**: al volver del detalle (botón o sub-sidebar) se restaura fila y scroll (`GrupoImei.indiceDe`).

### Changed
- **"Ver Stock Completo"** (notificaciones) navega al apartado de stock actual (`irAStockActual`), paridad con "Ir a pedidos".
- **Datos (preproducción)**: reparaciones históricas de los técnicos de solo-glass reclasificadas de tipo Reparación a Glass (1.276 filas `R→G`/`A→AG`; script `sql/migracion-glass-tecnicos.sql`). El filtro de piezas del historial de Reparaciones queda sin glass/marco.

### Fixed
- **Scroll estable en los dropdowns multiselección** (técnicos/clientes/proveedores): `fixedCellSize` elimina la recolocación del VirtualFlow al hacer hover en el fondo de la lista (ocultaba la última fila).
- **Celda "reasignar" fantasma**: el combo de técnico ya no queda pintado en filas vacías al reciclarse celdas (Asignaciones y Pulidos).
- **Combo de técnico sobre fila equivocada**: se cierra el desplegable antes de recargar tras reasignar (la celda reciclada con popup abierto se saltaba el repintado).
- **Destello de "Copiar celda"** también en la vista IMEIs (maestro y detalle).

### Notas de despliegue
- Requiere el **servidor** con la columna `ES_CHASIS` y el PATCH de chasis (ya desplegado). Retrocompatible con clientes 0.13.0.

## [0.13.0] - 2026-07-03

### Added
- **Glass como tipo de trabajo propio** (`AG`/`G`): asignación separada, pestaña Glass en Mis pendientes (3 roles), modal de completar filtrado por tipo (glass = glass/marco/otro), incidencias independientes por categoría, Historial con selector **Reparaciones | Glass | Pulidos**, exports CSV de Glass, y filtro de logs para acciones de glass/pulido. Requiere servidor actualizado.
- **Apartado "IMEIs"** (antes "Agrupado"), compartido por los 3 roles: agrupa reparaciones, glass y pulidos por dispositivo con contadores por tipo, drill-down y export; **ordenado por actividad más reciente** (cualquier categoría).
- **Modal de asignación con 3 colas independientes** (Reparación / Glass / Pulido): cada pestaña muestra solo su cola (mismo IMEI admitido en varias); desaparece el toggle de tipo por entrada. **Pulido rediseñado a master-detail** (lista + detalle, cliente inline con autocompletado, técnico superior aplicado a los escaneos con override por fila, Guardar bloqueado si hay filas sin técnico).
- **Cliente por IMEI en el modal de asignación**: opción "— Sin cliente —" real (persiste NULL vía flag `clienteExplicito` del servidor), restauración del cliente al perder el foco con texto a medias, y **sincronización en vivo del cliente entre colas** (el último cambio manual prevalece sobre la precarga de BD).
- **Aviso de asignación cruzada**: al asignar, lista todas las asignaciones activas del IMEI en cualquier categoría (rep/glass/pulido), marcando "(tú)".
- **Paridad de Pulido**: "Asignado por" (con reasignación que actualiza el asignador), columna Cliente en Mis pulidos/Historial, editar modelo/comentario/cliente desde Asignaciones, y borrar pulido propio en Mis pendientes (SuperTécnico).
- **Contadores entre categorías**: badge de pendientes con cap **99+** que suma pulidos; conteo por toggle en Mis pendientes.
- **Editar acción "otro" en el modal completo**: la acción se edita en su sección OTRAS ACCIONES; las acciones previas del IMEI aparecen bloqueadas ("✓ Ya reparada"); se pueden activar filas vírgenes y **añadir acciones nuevas en cualquier edición** (antes se ignoraban al guardar).
- **Motivo obligatorio al borrar un pulido completado** (antes borraba sin confirmación); el motivo queda en el log del servidor.

### Changed
- **Editar** disponible también para reparaciones de **glass** en el historial (paridad con IMEIs/Agrupado).
- Lo creado durante la **edición de un glass** (filas nuevas y acciones) nace como **G** (antes se creaba como reparación normal).
- Marcas **"Ya reparado"** del modal de edición calculadas **por categoría** (las ediciones de glass no marcaban).
- Menú contextual de Asignaciones reordenado (los "Editar…" juntos, urgente al final); en **Mis pulidos** el menú queda solo con "Copiar celda" (la edición vive en Asignaciones/Historial).
- CSV maestro del apartado IMEIs renombrado a `agrupado_resumen_…` (no colisiona con los de detalle).

### Fixed
- Badge y contador del toggle de **pulidos** se actualizan en vivo al completar (en el rol Técnico no se actualizaban nunca).
- Badge de Asignaciones: se refresca al cerrar/borrar desde Mis pendientes y ya no arranca con el estilo invertido.
- **Scroll fiable** a la última fila añadida en los modales de pedidos (Componentes y Otros).
- Refrescos más ágiles al completar/borrar: eliminadas recargas duplicadas encadenadas.

### Notas de despliegue
- Requiere el **servidor** con: flag `clienteExplicito`, `categoria` en altas, motivo en borrado de pulido y endpoints de asignaciones-activas/acciones (ya desplegado). Retrocompatible con clientes 0.12.x.
- **Orden de actualización**: drenar el glass pendiente; el SuperTécnico no asigna glass hasta que él y los técnicos de glass tengan la 0.13.0 (actualizarlos juntos); el resto de técnicos, en cualquier momento.

## [0.12.0] - 2026-06-29

### Added
- **Pedidos "Otros"**: registro de pedidos ajenos a las piezas (consumibles) desde la vista de Pedidos, con selector **Componentes | Otros**, tabla, modal multilínea y filtros. No afecta al stock. Requiere el servidor con la tabla `Compra_otro` y el endpoint `/api/compras-otros`.
- **Pegado de lote de IMEIs**: en los modales de asignación (normal y pulidos) y en los filtros de IMEI de todas las vistas, un bloque concatenado se reparte cada 15 dígitos (lote corrupto se rechaza en los modales; en los filtros un trozo inválido se marca en rojo).
- **Sub-indicador "N asignados"** bajo el IMEI en la vista Asignaciones: avisa cuando un dispositivo tiene asignación pendiente de varios técnicos (solo con N ≥ 2; derivado en cliente).
- **Manejo de errores de conexión y sesión**: banner no bloqueante de "sin conexión" que se autocura (sustituye al modal huérfano); auto-logout controlado al caducar la sesión (401); diálogos de error con owner (pegados a su ventana).

### Changed
- **Historial**: doble clic abre el detalle en los tres roles; mejor contraste del IMEI en la vista de técnico.
- **Filtros responsive** (FlowPane) debajo del título en Stock, Clientes y Estadísticas.

## [0.11.1] - 2026-06-25

### Fixed
- **Modal de edición de reparación**: al editar ya no aparecen los botones "Guardar fila" por fila (solo tienen sentido al crear) y el botón **"Guardar cambios"** se muestra correctamente cuando hay cambios válidos.
- Un cambio que afecta **solo al comentario** (observación) de una fila ahora recalcula los botones al instante (antes había que tocar otra cosa para que apareciera "Guardar cambios").
- Editar una fila **no intervenida** ya no provoca el error "La asignación ya fue eliminada".

### Changed
- En el modal de edición se **oculta** el selector de técnico por fila (cambiar el reparador de una reparación existente queda como mejora futura, requiere cambio en el servidor).

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

[Unreleased]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.12.0...HEAD
[0.12.0]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.11.1...v0.12.0
[0.11.1]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/totdb3354/gestion-reparaciones-cliente/compare/v0.9.1...v0.10.0
