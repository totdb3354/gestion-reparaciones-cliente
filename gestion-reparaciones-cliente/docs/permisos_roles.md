# Permisos por rol

Arquitectura actual con 3 roles: **TECNICO**, **SUPERTECNICO**, **ADMIN**.

---

## TECNICO

### Reparaciones — sus propias asignaciones
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver asignaciones propias | `PendientesTecnicoController` | `ReparacionDAO.getAsignaciones(idTec)` |
| Ver solicitudes de pieza por asignación | `PendientesTecnicoController` | `ReparacionDAO.getSolicitudesPorAsignacion` |
| Abrir formulario de reparación | `FormularioReparacionController` | — |
| Guardar reparación (nueva) | `FormularioReparacionController` | `ReparacionDAO.insertarCompleta` + `ComponenteDAO.actualizarStock` |
| Editar reparación propia | `FormularioReparacionController` | `ReparacionDAO.editarReparacion` |
| Marcar componente agotado | `FormularioReparacionController` | `ReparacionDAO.agotarComponente` |
| Ver historial propio | `HistorialReparacionController` | `ReparacionDAO.getHistorial(idTec)` |
| Ver historial de un IMEI | `HistorialReparacionController` | `ReparacionDAO.getHistorialPorImei` |

### Pulidos — solo historial propio
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver historial de pulidos propios | `HistorialPulidoController` | `ReparacionDAO.getHistorial(idTec)` filtrado a asignaciones `A*` |

### Stock — solo lectura en formulario de reparación
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Leer componentes por tipo | `FormularioReparacionController` | `ComponenteDAO.getAgrupadosPorTipo` |
| Leer chasis por color | `FormularioReparacionController` | `ComponenteDAO.getChasisPorColor` |

### Estadísticas — solo datos propios
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver estadísticas de reparaciones | `EstadisticasController` | `ReparacionDAO.getEstadisticasPorTecnico` (filtrado a idTec) |

### Cuenta
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Cambiar contraseña | `CambiarPasswordController` | `UsuarioDAO.cambiarPassword` |

---

## SUPERTECNICO

Todo lo de TECNICO más:

### Reparaciones — gestión global
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver todas las asignaciones | `PendientesSuperTecnicoController` | `ReparacionDAO.getAsignaciones()` |
| Asignar reparación (nueva) | `PendientesSuperTecnicoController` | `TelefonoDAO.insertar` + `ReparacionDAO.insertarAsignacion` |
| Reasignar técnico | `PendientesSuperTecnicoController` | `ReparacionDAO.actualizarTecnico` |
| Editar asignación (técnico + comentario) | `PendientesSuperTecnicoController` | `ReparacionDAO.actualizarAsignacion` |
| Marcar urgente | `PendientesSuperTecnicoController` | `ReparacionDAO.actualizarUrgente` |
| Borrar asignación | `PendientesSuperTecnicoController` | `ReparacionDAO.eliminarAsignacion` |
| Marcar incidencia y reasignar | `PendientesSuperTecnicoController` | `ReparacionDAO.marcarIncidenciaYAsignar` |
| Borrar incidencia | `PendientesSuperTecnicoController` | `ReparacionDAO.borrarIncidenciaPorImei` |
| Ver historial completo (todos los técnicos) | `HistorialReparacionController` | `ReparacionDAO.getHistorial()` |
| Exportar historial CSV | `HistorialReparacionController` | `CsvExporter.exportar` |

### Pulidos — gestión completa
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver todos los pulidos pendientes | `PulidoSuperTecnicoController` | `ReparacionDAO.getAsignaciones()` filtrado a `A*` |
| Asignar pulido | `PulidoSuperTecnicoController` | `TelefonoDAO.insertar` + `ReparacionDAO.insertarAsignacion` (IMEI lookup automático) |
| Completar lote de pulidos | `PulidoSuperTecnicoController` | `ReparacionDAO.completarPulidoLote` |
| Eliminar asignación de pulido | `PulidoSuperTecnicoController` | `ReparacionDAO.eliminarAsignacion` |
| Editar comentario de asignación | `PulidoSuperTecnicoController` | `ReparacionDAO.actualizarAsignacion` |
| Editar modelo del teléfono | `PulidoSuperTecnicoController` | `TelefonoDAO.insertar` (upsert) |
| Ver historial completo de pulidos | `HistorialPulidoController` | `ReparacionDAO.getHistorial()` filtrado a `A*` |

### Stock — gestión completa
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver stock con info de pedidos | `StockController` | `ComponenteDAO.getAllGestionados` |
| Editar stock manualmente | `StockController` | `ComponenteDAO.actualizar` |
| Ajustar stock mínimo | `StockController` | `ComponenteDAO.setStockMinimo` |
| Activar / desactivar componente | `StockController` | `ComponenteDAO.setActivo` |
| Insertar componente | `StockController` | `ComponenteDAO.insertar` |
| Eliminar componente | `StockController` | `ComponenteDAO.eliminar` |
| Ver pedidos pendientes | `StockController` | `CompraComponenteDAO.getPendientes` |
| Crear pedido | `FormularioCompraController` | `CompraComponenteDAO.insertar` |
| Editar pedido | `FormularioCompraEditarController` | `CompraComponenteDAO.editar` |
| Recibir pedido (completo / parcial) | `StockController` | `CompraComponenteDAO.confirmarRecibido` / `confirmarParcial` |
| Cancelar pedido | `StockController` | `CompraComponenteDAO.cancelar` |
| Gestión de proveedores | `StockController` | `ProveedorDAO.*` |
| Exportar stock / pedidos / proveedores CSV | `StockController` | `CsvExporter.exportar` |

### Estadísticas — datos globales
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver estadísticas de todos los técnicos | `EstadisticasController` | `ReparacionDAO.getEstadisticasPorTecnico` |
| Ver evolución de stock | `EstadisticasController` | `ComponenteDAO.getEvolucionStock` |

### Cuenta
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Cambiar contraseña | `CambiarPasswordController` | `UsuarioDAO.cambiarPassword` |

---

## ADMIN

### Gestión de usuarios
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver técnicos y usuarios | `AdminController` | `UsuarioDAO.getUsuariosTecnicos` |
| Crear técnico | `AdminController` | `UsuarioDAO.registrarTecnico` |
| Activar / desactivar técnico | `AdminController` | `UsuarioDAO.activarTecnico` / `desactivarTecnico` |
| Eliminar técnico | `AdminController` | `UsuarioDAO.eliminarTecnico` |

### Logs de actividad
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver log de actividad global | `LogController` (cliente) | `LogDAO.getAll` |

### Historial — solo lectura
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver historial de reparaciones | `HistorialReparacionController` | `ReparacionDAO.getHistorial()` |
| Ver historial de un IMEI | `HistorialReparacionController` | `ReparacionDAO.getHistorialPorImei` |
| Exportar CSV | `HistorialReparacionController` | `CsvExporter.exportar` |

### Estadísticas — solo lectura
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Ver estadísticas globales | `EstadisticasController` | `ReparacionDAO.getEstadisticasPorTecnico` |

### Cuenta
| Acción | Controlador | DAO / método |
|--------|-------------|--------------|
| Cambiar contraseña | `CambiarPasswordController` | `UsuarioDAO.cambiarPassword` |
