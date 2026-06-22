# Diseño: Logs de auditoría enriquecidos

**Fecha:** 2026-06-19  
**Estado:** Aprobado

---

## Resumen

Se enriquecen los logs de auditoría para que respondan a: quién, cuándo, qué, **sobre qué** (nombres legibles, no solo IDs) y **antes/después** (valores previos y nuevos en ediciones). Se añade una columna `MOTIVO` para justificar borrados, con campo obligatorio en cliente. Se elimina el endpoint/acción redundante `REASIGNAR_TECNICO`. Se añade filtro por tipo de acción en la vista de logs, y clic en fila para ver el detalle completo.

---

## Base de datos

```sql
ALTER TABLE Log_Actividad ADD COLUMN MOTIVO TEXT NULL;
```

`NULL` en registros históricos y en acciones que no son borrado.

---

## Formato del campo DETALLE

Texto plano con pares clave: valor, separados por coma. Los cambios antes/después usan `→`:

```
TEC_ANT: Juan → TEC_NUE: Pedro
COM_ANT: '' → COM_NUE: 'lleva funda'
PRIORIDAD_ANT: Normal → PRIORIDAD_NUE: Urgente
```

Solo se incluyen los campos que realmente cambian (comparar estado previo con valores del request). Los valores de prioridad se traducen a texto legible en el servidor: `0→Normal`, `1→Urgente`, `2→Muy urgente`.

---

## Tabla de acciones y detalles resultantes

| Acción | DETALLE | MOTIVO |
|---|---|---|
| `CREAR_REPARACION` | `ID_REP: A001, IMEI: 351..., MODELO: iPhone 14` | — |
| `CREAR_ASIGNACION` | `ID_REP: A001, IMEI: 351..., MODELO: iPhone 14, TECNICO: Juan` | — |
| `COMPLETAR_REPARACION` | `ID_REP: A001, IMEI: 351..., MODELO: iPhone 14, TECNICOS: Juan, Pedro` | — |
| `ACTUALIZAR_ASIGNACION` | Solo campos que cambian: `TEC_ANT: Juan → TEC_NUE: Pedro` y/o `COM_ANT: '' → COM_NUE: 'nota'` | — |
| `CAMBIAR_PRIORIDAD` | `ID_REP: A001, PRIORIDAD_ANT: Normal → PRIORIDAD_NUE: Urgente` | — |
| `EDITAR_REPARACION` | `ID_REP: A001, COM_ANT: Pantalla → COM_NUE: Batería, OBS_ANT: '' → OBS_NUE: 'falla carga'` | — |
| `MARCAR_INCIDENCIA` | `ID_REP: A001, IMEI: 351..., TECNICO_NUE: Pedro` | — |
| `GUARDAR_FILA_INDIVIDUAL` | `ID_REP: A001, ID_ASIG: A001, IMEI: 351..., TECNICO: Juan` | — |
| `AGOTAR_COMPONENTE` | `ID_ASIG: A001, TIPO: Pantalla, CANT: 2` | — |
| `ELIMINAR_ASIGNACION` | `ID_REP: A001, IMEI: 351..., MODELO: iPhone 14, TECNICO: Juan` | — |
| `ELIMINAR_REPARACION` | `ID_REP: A001, IMEI: 351..., MODELO: iPhone 14, TECNICOS: Juan, Pedro` | Obligatorio |
| `CREAR_PEDIDO` | `ID_COMPRA: X, COMPONENTE: Pantalla, CANT: 5, PROVEEDOR: Nombre` | — |
| `EDITAR_PEDIDO` | `ID_COMPRA: X` (sin antes/después — edición libre) | — |
| `RECIBIR_PEDIDO` | `ID_COMPRA: X, COMPONENTE: Pantalla, CANT: 5` | — |
| `RECIBIR_PARCIAL` | `ID_COMPRA: X, CANT_RECIBIDA: 3` | — |
| `RECIBIR_RESTO` | `ID_COMPRA: X` | — |
| `CANCELAR_PEDIDO` | `ID_COMPRA: X` | — |
| `CONFIRMAR_PEDIDO` | `ID_COMPRA: X` | — |
| `BORRAR_PEDIDO` | `ID_COMPRA: X` | — |
| `DESRECIBIR_PEDIDO` | `ID_COMPRA: X` | — |
| `CREAR_COMPONENTE` | `TIPO: Pantalla` | — |
| `EDITAR_COMPONENTE` | `ID_COM: 5, TIPO: Pantalla, STOCK_ANT: 3 → STOCK_NUE: 5` | — |
| `ELIMINAR_COMPONENTE` | `ID_COM: 5, TIPO: Pantalla` | — |
| `CREAR_TECNICO` | `NOMBRE: Juan` | — |
| `ELIMINAR_TECNICO` | `ID_TEC: 3, NOMBRE: Juan` | — |
| `CREAR_USUARIO` | `NOMBRE_USUARIO: juanl` | — |
| `ACTIVAR_USUARIO` | `ID_TEC: 3, NOMBRE: Juan` | — |
| `DESACTIVAR_USUARIO` | `ID_TEC: 3, NOMBRE: Juan` | — |
| `ELIMINAR_USUARIO` | `ID_TEC: 3, NOMBRE: Juan` | — |
| `SOLICITAR_STOCK` | `ID_SOL: X, TIPO: Pantalla` | — |
| `LOGIN` | *(vacío)* | — |
| `CAMBIAR_PASSWORD` | *(vacío)* | — |

---
1
## Eliminación de código redundante

El endpoint `PATCH /asignaciones/{idRep}/tecnico` y su acción `REASIGNAR_TECNICO` son código muerto — el cliente nunca los llama. Se eliminan:

- **Servidor:** endpoint `actualizarTecnico` + record `TecnicoRequest` en `ReparacionController`
- **Cliente:** método `ReparacionDAO.actualizarTecnico()`

---

## Servidor (`gestion-reparaciones-servidor`)

### `LogActividad` (modelo)
- Añadir `String motivo` con getter `getMotivo()`
- El constructor sin argumentos (usado por Jackson) ya existe — Jackson rellena el campo desde JSON

### `LogDAO`
- Nuevo método: `insertar(int idUsu, String accion, String detalle, String motivo)` — INSERT con 4 columnas
- Método existente `insertar(int idUsu, String accion, String detalle)` → delega al nuevo con `motivo=null`
- `getAll()` → `getFiltered(String accion, String tecnico, LocalDate desde, LocalDate hasta)`: añade `MOTIVO` al SELECT y al mapper; construye WHERE dinámico con los parámetros no nulos:
  - `accion != null` → `AND l.ACCION = ?`
  - `tecnico != null` → `AND u.NOMBRE_USUARIO = ?`
  - `desde != null` → `AND DATE(l.FECHA) >= ?`
  - `hasta != null` → `AND DATE(l.FECHA) <= ?`

### `LogController`
- `getAll()` acepta 4 `@RequestParam(required=false)`: `String accion`, `String tecnico`, `LocalDate desde`, `LocalDate hasta`; llama a `logDao.getFiltered(accion, tecnico, desde, hasta)`

### Controladores — enriquecimiento del detalle

Para cada acción que requiere estado previo, el controlador hace un SELECT antes del UPDATE/DELETE:

**`ReparacionController`:**
- `insertarAsignacion`: obtener `nombreTecnico` y `modelo` desde `dao.getAsignacionById` → incluir en DETALLE
- `insertarCompleta` / `completar`: obtener tecnicos y modelo antes de completar
- `actualizarAsignacion`: obtener estado actual → comparar con request → incluir solo campos que cambian
- `actualizarPrioridad`: obtener prioridad actual → traducir ambos valores a texto → DETALLE con ANT→NUE
- `editarReparacion`: obtener estado actual → incluir COM_ANT, OBS_ANT → COM_NUE, OBS_NUE
- `marcarIncidenciaYAsignar`: obtener modelo → incluir en DETALLE
- `guardarFilaIndividual`: obtener tecnico nombre → incluir en DETALLE
- `agotarComponente`: obtener tipo del componente → incluir en DETALLE
- `eliminarAsignacion`: obtener estado actual (IMEI, modelo, tecnico) antes de borrar; acepta `@RequestBody(required=false) MotivoRequest req` (el cliente no envía motivo para asignaciones, por lo que `MOTIVO` queda null); llama a `logDao.insertar(idUsu, accion, detalle, motivo)`
- `eliminar` (reparación): obtener IMEI, modelo, lista de tecnicos antes de borrar; acepta `@RequestBody(required=false) MotivoRequest req`; llama a `logDao.insertar(idUsu, accion, detalle, motivo)`
- Eliminar endpoint `actualizarTecnico` y record `TecnicoRequest`

**`ComponenteController`:**
- `editarComponente` (stock): obtener stock actual antes de actualizar → STOCK_ANT → STOCK_NUE
- `eliminarComponente`: obtener tipo del componente antes de borrar → incluir TIPO en DETALLE

**`TecnicoController` / `UsuarioController`:**
- Enriquecer con nombres legibles donde solo hay IDs actualmente

Nuevo record en `ReparacionController`:
```java
private record MotivoRequest(String motivo) {}
```

---

## Cliente (`gestion-reparaciones-cliente`)

### `LogActividad` (modelo)
- Añadir `String motivo` con getter `getMotivo()`

### `LogDAO`
- `getAll()` → `getAll(String accion, String tecnico, LocalDate desde, LocalDate hasta)`: llama a `/api/logs` con los parámetros no nulos como query params (`?accion=X&tecnico=Juan&desde=2026-01-01&hasta=2026-06-19`)
- Todos los parámetros son opcionales — null significa sin filtro para ese campo

### `ConfirmDialog` — nuevo método `mostrarConMotivo`
```
mostrarConMotivo(String titulo, String descripcion, String textoAccion, Consumer<String> onConfirm)
```
- Añade un `TextArea` editable encima de los botones (placeholder: "Escribe el motivo del borrado...")
- El botón de confirmación arranca **deshabilitado** y se habilita cuando `textArea.getText().isBlank() == false`
- Al confirmar, llama a `onConfirm.accept(textArea.getText().trim())`
- Misma estética que `ConfirmDialog.mostrar()`

### `ReparacionDAO`
- `eliminarAsignacion(String idAsig)`: se mantiene sin motivo (DELETE sin body) — el borrado de asignación no pide motivo
- `eliminar(String idRep)` → `eliminar(String idRep, String motivo)`: envía `{"motivo": motivo}` en el body del DELETE
- Eliminar método `actualizarTecnico()`

### Controladores cliente que borran
- `PendientesSuperTecnicoController`: el borrado de asignación mantiene `ConfirmDialog.mostrar(...)` (sin motivo); llama a `reparacionDAO.eliminarAsignacion(id)`
- `ReparacionControllerSuperTecnico`: el borrado de reparación usa `ConfirmDialog.mostrarConMotivo(...)`, pasando el motivo a `reparacionDAO.eliminar(id, motivo)`

> **Nota (revisión post-implementación):** Originalmente el spec contemplaba motivo obligatorio también en el borrado de asignaciones. Se decidió que **solo el borrado de reparaciones pide motivo**; el de asignaciones vuelve a ser como antes (sin texto). El log de `ELIMINAR_ASIGNACION` sigue enriquecido (IMEI/MODELO/TECNICO) pero con `MOTIVO` vacío.

### Vista de logs (`LogController`)

**Filtros — todos server-side:**

Todos los filtros (acción, técnico, fecha desde/hasta) se mandan al servidor. Cada vez que cambia cualquier filtro, se llama a `logDAO.getAll(accion, tecnico, desde, hasta)` y se recarga `logsMaster`. El `FilteredList` con predicate desaparece — ya no hace falta `coincideFiltro()`.

El buscador de texto libre (`txtBuscadorLogs`) sigue siendo client-side: filtra sobre `logsMaster` ya cargado con `FilteredList` (es un subconjunto pequeño tras los filtros server-side).

**Filtro por tipo de acción (nuevo):**
- Nuevo `TextField txtFiltroAccion` + popup `ListView<String>` — mismo patrón visual que `txtFiltroTecnico`
- Lista de tipos de acción hardcodeada en cliente (son fijos y conocidos): `CREAR_ASIGNACION`, `ACTUALIZAR_ASIGNACION`, `CAMBIAR_PRIORIDAD`, `EDITAR_REPARACION`, `COMPLETAR_REPARACION`, `ELIMINAR_ASIGNACION`, `ELIMINAR_REPARACION`, `CREAR_PEDIDO`, `EDITAR_PEDIDO`, `RECIBIR_PEDIDO`, `RECIBIR_PARCIAL`, `CANCELAR_PEDIDO`, `CONFIRMAR_PEDIDO`, `BORRAR_PEDIDO`, `CREAR_COMPONENTE`, `EDITAR_COMPONENTE`, `ELIMINAR_COMPONENTE`, `CREAR_TECNICO`, `ELIMINAR_TECNICO`, `CREAR_USUARIO`, `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO`, `ELIMINAR_USUARIO`, `LOGIN`, `CAMBIAR_PASSWORD`

**Filtros existentes (técnico, fecha) — migrados a server-side:**
- Los listeners de `txtFiltroTecnico`, `dpLogsDesde`, `dpLogsHasta` pasan de actualizar un predicate a llamar al DAO y recargar `logsMaster`
- `limpiarFiltrosLogs()` resetea todos los filtros y recarga sin parámetros

**Clic en fila → detalle completo:**
- `tablaLogs.setOnMouseClicked`: con **doble clic** (`clickCount == 2`) abre `ConfirmDialog.mostrarTexto()` con el contenido completo: DETALLE + (si motivo no es null/vacío) `"\n\nMOTIVO: " + motivo`

**Columna DETALLE:**
- Se mantiene con texto truncado por elipsis (comportamiento JavaFX por defecto)
- No se añade columna separada para MOTIVO — el motivo se ve en el popup de detalle completo

---

## Ficheros afectados

### Servidor
- `sql/migracion-logs.sql` (nuevo)
- `model/LogActividad.java`
- `dao/LogDAO.java`
- `controller/LogController.java`
- `controller/ReparacionController.java`
- `controller/ComponenteController.java`
- `controller/TecnicoController.java`
- `controller/UsuarioController.java`

### Cliente
- `models/LogActividad.java`
- `dao/LogDAO.java`
- `dao/ReparacionDAO.java`
- `utils/ConfirmDialog.java`
- `controllers/LogController.java`
- `controllers/PendientesSuperTecnicoController.java`
- `controllers/ReparacionControllerSuperTecnico.java`
- `views/LogView.fxml` (añadir txtFiltroAccion)
