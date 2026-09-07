# Filtros en la ventana de Log de actividad

## Contexto

La ventana de logs (vista solo accesible para ADMIN) muestra todo el historial
de `Log_Actividad` (Fecha, Usuario, Acción, Detalle) sin ningún filtro. Con el
tiempo la tabla crece y encontrar un evento concreto (p. ej. todo lo
relacionado con un IMEI, o lo que hizo un técnico un día determinado) requiere
leer la lista entera.

El IMEI no es un campo estructurado del log: aparece como texto libre dentro
de `Detalle` solo en algunas acciones (`CREAR_REPARACION`, `CREAR_ASIGNACION`,
`COMPLETAR_REPARACION`...), no en todas. Por eso el filtro de texto debe
buscar sobre el contenido combinado de Usuario + Acción + Detalle, no sobre un
campo "IMEI" dedicado.

## Alcance

Filtrado **en el cliente**, sobre la lista de logs ya cargada en memoria (la
misma que trae el botón "Actualizar"). No se modifica el servidor, la BD, ni
el endpoint `GET /api/logs`. El volumen de logs actual no justifica un
endpoint de filtrado en servidor; si en el futuro la tabla crece mucho, se
puede revisar.

Esto sigue el mismo patrón ya usado en `StockController` para los filtros de
"Pedidos" (buscador de texto + `DatePicker` Desde/Hasta + botón "Limpiar
filtros"), reutilizando esa convención de UI y de código.

## Diseño

### FXML (`LogView.fxml`)

Se añade una fila de controles entre el título y la `TableView`, dentro del
`VBox` del `center`:

- `TextField fx:id="txtBuscadorLogs"` — `promptText="Buscar..."`, estilo
  `buscador` (igual que `txtBuscador`/`txtBuscadorPedidos` en `StockView`).
- `Label "Desde:"` + `DatePicker fx:id="dpLogsDesde"`
- `Label "Hasta:"` + `DatePicker fx:id="dpLogsHasta"`
- `Button fx:id="btnLimpiarFiltrosLogs" text="Limpiar filtros"` con
  `styleClass="btn-secondary"`, `onAction="#limpiarFiltrosLogs"`

Los `DatePicker` se configuran con el editor deshabilitado
(`getEditor().setDisable(true)`), igual que `dpPedidosDesde`/`dpPedidosHasta`,
para evitar que el usuario escriba fechas inválidas a mano.

### Controller (`LogController.java`)

- Se sustituye la carga directa (`tablaLogs.setItems(FXCollections.observableArrayList(logs))`)
  por un campo de instancia `logsMaster` (`ObservableList<LogActividad>`,
  inicializado vacío) y un `FilteredList<LogActividad> logsFiltrados` que lo
  envuelve, construido una vez en `initialize()`.
- `tablaLogs.setItems(logsFiltrados)` se hace una sola vez en `initialize()`.
- `cargarLogs()` pasa a hacer `logsMaster.setAll(logs)` en lugar de crear una
  lista nueva. Así el `FilteredList` ya enganchado a la tabla se mantiene
  sincronizado, y los filtros activos **no se pierden** al pulsar
  "Actualizar".
- Predicado de filtrado (`Runnable aplicarFiltrosLogs`, recalculado en cada
  cambio de input):
  - **Texto**: si `txtBuscadorLogs` no está vacío, debe aparecer (case
    insensitive, `contains`) en `nombreUsuario`, `accion` o `detalle` del log
    (cualquiera de los tres, no concatenados, para evitar falsos negativos en
    los límites de los campos).
  - **Fecha Desde/Hasta**: si están informadas, se compara
    `log.getFecha().toLocalDate()` contra el rango, ambos límites inclusive.
    Si `fecha` es null o el DatePicker correspondiente está vacío, ese filtro
    no aplica (igual que en Pedidos).
  - Los tres filtros se combinan con AND.
- Filtrado en vivo: listeners en `textProperty()` de `txtBuscadorLogs` y
  `valueProperty()` de ambos `DatePicker`, sin botón "Filtrar" adicional.
- `limpiarFiltrosLogs()`: vacía el texto y ambas fechas (los listeners
  reaplican el filtro automáticamente al quedar todo vacío).

## Fuera de alcance

- No se añade paginación ni límite de filas.
- No se añade filtro por tipo de acción (dropdown) ni por usuario (dropdown):
  el campo de texto libre ya cubre esos casos.
- No se persiste el filtro entre aperturas de la ventana (se resetea cada vez
  que se abre).
