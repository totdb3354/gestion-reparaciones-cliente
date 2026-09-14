# Inventario del cliente JavaFX (gestion-reparaciones-cliente) para la migración a React

Fecha: 2026-09-13 · Rama `feature/estadisticas-tarjeta-meses` · Versión UI mostrada: 0.16.2 (`MainView.fxml:22`).
Rutas abreviadas: `ctrl/` = `src/main/java/com/reparaciones/controllers/`, `utils/`, `dao/`, `models/` = mismos bajo `com/reparaciones/`; `views/` = `src/main/resources/views/`. Servidor = `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/`.
Tamaño: 25 825 líneas Java (23 controllers, 19 DAOs, 26 modelos, 21 utils), 23 FXML, 1 CSS (623 líneas), 16 PNG, 2 051 líneas de tests.

Leyenda ⚠: **⚠S** = seguridad (filtro/permiso por rol que solo existe en cliente) · **⚠D** = umbral/regla duplicada en cliente y servidor (o que debería vivir en servidor) · **⚠C** = validación que solo hace el cliente y el servidor no comprueba.

---

## 1. Navegación

### 1.1 Arranque y sesión
- `App.java:24-33`: carga `LoginView.fxml` en ventana no redimensionable; `App.java:41` fija `jdk.httpclient.keepalive.timeout=10` (los sockets keep-alive se cierran antes que el firewall).
- `ctrl/LoginController.java:93-114`: usuario/contraseña obligatorios → `UsuarioDAO.login` (POST `/api/auth/login`, reintento único si `ConexionException`, `dao/UsuarioDAO.java:36-42`) → `Sesion.iniciar(u)` → `cargarMainView()` (:121-150): misma Stage, redimensionable, mín. 900×600, maximizada (macOS: bounds visuales).
- `Sesion.java` (estático): `usuarioActual`; `esAdmin()`, `esSuperTecnico()`, `esAdminOSuperTecnico()`, `getIdTec()`, `haySession()`. Roles = strings `"ADMIN"`, `"SUPERTECNICO"`, resto = TECNICO (`models/Usuario.java:90-96`). El servidor decide rol e `idTec` en el login (`UsuarioDAO.java:51-54`).

### 1.2 MainView / MainController (`views/MainView.fxml`, `ctrl/MainController.java`, 1040 líneas)
- **Barra superior** (`MainView.fxml:12-73`, navy `#001232`, 64 px): logo clicable → `irAInicio` (:743-761, vuelve al panel inicial de la vista de reparaciones del rol); título "FSGR: Gestión de Stock y Reparaciones V.0.16.2"; 4 botones `nav-btn`: **Reparaciones, Stock, Estadísticas, Clientes** (visibles para los 3 roles); campana (`campanaPane`, solo SUPERTECNICO, :112-117) con badge numérico; botón de usuario "Hola, <usuario>" con `ContextMenu`.
- **Banner de conexión** (`MainView.fxml:76-79`, :140-141): `HBox` ámbar "⚠ Sin conexión con el servidor. Reintentando…" enlazado a `ConexionEstado.desconectadoProperty()`; no bloqueante.
- **Contenedor** `StackPane contenedor` (`MainView.fxml:83`): `mostrarVista(ruta, activo, inactivos…)` (:950-1017) carga el FXML una sola vez y lo **cachea** (`vistaCache` por ruta); en visitas posteriores llama a `Recargable.recargar()`; inyecta `Navegable` a `EstadisticasController` (:963-971); aplica y consume el filtro pendiente `filtroNav*` en las vistas de reparaciones (:978-993).
- **Vista por rol** (`mostrarReparaciones` :764-771): SUPERTECNICO → `ReparacionViewSuperTecnico.fxml`; ADMIN → `ReparacionViewAdmin.fxml`; resto → `ReparacionViewTecnico.fxml`. Stock, Estadísticas y Clientes cargan el mismo FXML para todos y ajustan permisos dentro (ver §3).
- **Menú de usuario** (:822-835): ADMIN añade "Gestionar técnicos" (`RegisterView`, modal :843-858) y "Ver logs" (`LogView`, ventana no modal :862-872); todos: "Descargar CSV" (delegado al controlador activo si es `Exportable`, :838-841), "Cambiar contraseña" (modal :876-890), "Cerrar sesión" (:917-938: `detenerPolling()` en todas las vistas cacheadas, `Sesion.cerrar()`, vuelve al login).
- **Recarga al recuperar foco** (:128-135): `win.focusedProperty` → `controladorActivo.recargar()` + `actualizarBadge()` (SUPERTECNICO) + refresco del panel de notificaciones si está abierto.
- **Sesión caducada** (:144-145, :896-908): `ApiClient.setSesionExpiradaHandler` → `Alert` bloqueante "Tu sesión ha caducado" → `cerrarSesion()`.
- **Campana / NotificacionesModal** (`views/NotificacionesModal.fxml`, sin controller; se lee por `loader.getNamespace()`, :208-433): Stage UNDECORATED anclado bajo la campana, se cierra al hacer clic fuera / mover / redimensionar / minimizar. Dos pestañas: **Solicitudes** (pendientes y rechazadas: urgentes `ReparacionComponenteDAO.getSolicitudes` + preventivas `SolicitudStockDAO.getSolicitudes`; acciones Rechazar / Recuperar / Limpiar por tarjeta, "Pedir piezas" → `FormularioCompraController.abrirConSolicitudes` y marca todas GESTIONADAS :333-353, "Rechazar todo" :355-364) y **Alertas** (componentes con `esAlertaStock` :726-728: master + activo + `stock <= stockMinimo`; "Pedir" por tarjeta, "Pedir todas", "Ver Stock Completo" → `StockController.irAStockActual`; "→ Ir a pedidos" → `irAPedidos`). Poller propio (:321-331) y badge = `/api/solicitudes/count` + `/api/solicitudes-stock/count` (:153-171). Al arrancar, si hay alertas la campana pulsa con glow amarillo (`iniciarPulso` :174-197).

### 1.3 Sub-barras / pestañas internas de cada vista
- **ReparacionView\*** (sidebar izquierda, todo por `fx:include`): Técnico = Pendientes (badge) · Historial · IMEIs; SuperTécnico = Asignaciones (badge, `PendientesSuperTecnicoView`) · Pendientes (badge) · Historial · IMEIs; Admin = Asignaciones (solo lectura) · Historial · IMEIs. Pendientes e Historial llevan toggle-pill **Reparaciones / Glass / Pulidos** (Pendientes = `PendientesTecnicoView`×2 [rep y glass vía `setModoGlass`] + `PulidoTecnicoView`; Historial = tabla propia + `HistorialPulidoView`). IMEIs = `AgrupadoView` (maestro por IMEI ↔ detalle).
- **StockView**: sidebar Stock · Pedidos (toggle Componentes / Otros) · Proveedores.
- **EstadisticasView**: sidebar Técnicos · Stock; granularidad Día/Semana/Mes/Año; métrica Puntos / Puntos-día.
- **NotificacionesModal**: tabs Solicitudes / Alertas.

### 1.4 Ventanas secundarias (Stage) y modales
| Origen | Ventana | Tipo |
|---|---|---|
| MainController | RegisterView (gestión técnicos), CambiarPasswordView | APPLICATION_MODAL |
| MainController | LogView | Stage normal, redimensionable |
| MainController | NotificacionesModal | UNDECORATED anclado, autocierre |
| PendientesTecnico / Agrupado / Historial | FormularioReparacionView (`abrir` :832, `abrirEditar` :329) | Stage propio |
| PendientesSuperTecnico | "Asignar trabajos" (:2650, ~1000 líneas), "Carga de técnicos" (:978), Dialog "Técnicos de glass" (:916), editor comentario (:2782), selector modelo (:2824), Alert conflictos (:2714) | Stage/Dialog |
| Stock | editar stock (:607), solicitar pieza (:701), editar proveedor (:1787); TextInputDialog mínimo/parcial/resto/nuevo proveedor; FormularioCompra(+Editar), FormularioOtroPedido(+Editar) | Stage modal / TextInputDialog |
| Estadísticas | Dialog valores de dificultad (:249), Dialog técnicos excluidos (:304), Popup desglose por punto (:1116) | Dialog / Popup |
| Agrupado / ReparacionSuperTecnico | Dialog incidencia, Dialog observación teléfono, Alert "referenciada" | Dialog |
| Utils | `ConfirmDialog` (UNDECORATED, cuenta atrás 3 s antes de habilitar la acción destructiva; variantes `mostrar`, `mostrarConMotivo`, `mostrarTexto`), `SelectorClienteDialog` (lista filtrable, devuelve `Optional<Integer>` con centinela −1 = sin cliente), `Alertas.mostrarError` (Alert no modal deduplicado) | |

---

## 2. Tabla de las 23 vistas

Transversales abreviados: P = Poller, R = Recargable, N = Navegable, E = Exportable/CsvExporter, MS = MultiSelectDropdown/ComboBox, CD = ConfirmDialog, SC = SelectorClienteDialog, CR = CeldaReparador, FI = FiltroImei, IU = ImeiUtils, EG = EntregaGlass, TT = TipoTrabajo, CT = CargaTecnicos, PG = PrediccionGlass, SD = StaleDataException, BD = BorradorDAO. Rutas HTTP: prefijo `/api` omitido.

| # | Vista (FXML / controller) | Rol(es) | Propósito | Sub-vistas / pestañas | Acciones principales | Líneas | DAO → endpoints | Transversales |
|---|---|---|---|---|---|---|---|---|
| 1 | LoginView / LoginController | sin sesión | Autenticación | — | `login`, toggle ojo | 155 | UsuarioDAO.login → POST `/auth/login` | ConexionException |
| 2 | MainView / MainController | todos | Shell: navbar, campana, banner, contenedor, notificaciones | ver §1 | nav ×4, campana, menú usuario | 1040 | ReparacionComponenteDAO `/solicitudes[?estado]`, `/solicitudes/count`, PATCH `/solicitudes/{id}/estado`, `/limpiar`; SolicitudStockDAO `/solicitudes-stock…`; ComponenteDAO `/componentes/gestionados` | P, R (orquesta), E (delega) |
| 3 | NotificacionesModal (sin controller) | SUPERTECNICO | Solicitudes de pieza + alertas de stock | tabs Solicitudes / Alertas | rechazar/recuperar/limpiar, pedir, pedir todas, ver stock | (en Main) | los de Main | P |
| 4 | ReparacionViewTecnico / ReparacionControllerTecnico | TECNICO | Raíz del técnico: pendientes, historial, IMEIs | sidebar 3 + toggles Rep/Glass/Pulidos; filtros IMEI, pieza, fechas, incidencias | abrir pendientes, historial, agrupado, limpiar, CSV; ctx "Copiar celda" | 1056 | ReparacionDAO `/reparaciones/historial`; GlassDAO `/glass/historial` | P, R, E, MS, FI, CR, CD, Piezas |
| 5 | ReparacionViewSuperTecnico / …SuperTecnico | SUPERTECNICO | Raíz del supertécnico: + asignaciones globales y edición | sidebar 4 + toggles; filtro técnico | ctx Editar/Borrar (con motivo)/Añadir-Cancelar incidencia/Copiar; CSV | 1314 | + TecnicoDAO `/tecnicos`, `/tecnicos/activos`; POST `/reparaciones/{id}/incidencia`; DELETE `/reparacion-componentes/{id}/incidencia`; GET `/reparaciones/{id}/referenciadora`; DELETE `/reparaciones/{id}` (body motivo) | P, R, E, MS, FI, CR, CD, EG (CSV), TT |
| 6 | ReparacionViewAdmin / …Admin | ADMIN | Solo lectura: asignaciones, historial, IMEIs | sidebar 3 + toggles | ctx solo Copiar; CSV | 823 | historial rep/glass; `/tecnicos` | R (sin P), E, MS, FI, CR |
| 7 | PendientesTecnicoView / PendientesTecnicoController | TECNICO, SUPERTECNICO (×2: rep y glass) | Mis asignaciones abiertas (A / AG) | filtros IMEI, Tipo (solicitud/incidencia/asignación) | "Añadir reparación/glass" → Formulario; ctx por cerrar, entregar a glass, marcar llegada, deshacer; papelera (solo Super) | 560 | `/reparaciones/asignaciones?tecnico=`, `/glass/asignaciones?tecnico=`; PATCH `…/asignaciones/{id}/por-cerrar`, `/entrega-glass`, `/llegada` (+DELETE); DELETE `/asignaciones/{id}`; DELETE `/reparaciones/imei/{imei}/incidencia-activa?tipo=` | EG, TT, FI, CD |
| 8 | PendientesSuperTecnicoView / PendientesSuperTecnicoController | SUPERTECNICO; ADMIN (`setSoloLectura`) | Tabla unificada A+AG+AP, reasignar en celda, carga técnicos, modal asignación masiva | filtros IMEI, técnico, cliente, tipo, estado; botones Técnicos de glass, Carga, Asignar | combo técnico en celda; ctx comentario, modelo (pulido), cliente, urgente, chasis; papelera; modal Asignar | 2899 | `/reparaciones/asignaciones`, `/glass/asignaciones`, `/pulidos/asignaciones`, `/reparaciones/asignaciones/completadas-hoy`; PATCH `/reparaciones/asignaciones/{id}` (+`/urgente`, `/chasis`), `/pulidos/asignaciones/{id}`; `/telefonos` POST, `/telefonos/{imei}/modelo|cliente` GET, PATCH cliente; `/tecnicos/activos`, PATCH `/tecnicos/{id}/glass`; `/clientes/activos`; GET `/reparaciones/imei/{imei}/tiene-asignacion?tecnico=&tipo=`; POST `/reparaciones/asignaciones`, `/glass/asignaciones`, `/pulidos/asignaciones`; DELETE asignaciones | CT, PG, EG, TT, FI, IU, MS, SC, CD, SD |
| 9 | FormularioReparacionView / FormularioReparacionController | TECNICO, SUPERTECNICO (alta y edición) | Cerrar asignación por filas de pieza (SKU, cantidad, reutilizado, solicitud, agotado, "otro"); editar reparación; borrador | filtro modelo, avisos conflicto/incidencia, filas dinámicas `FilaUI` + `OtrasAccionesUI` | Guardar (doble clic), guardar fila individual, solicitud pieza, agotado, observación | 2389 | `/reparaciones/imei/{imei}/incidencia-activa`, `/asignaciones-activas`, `/ya-reparados`, `/acciones`; `/reparaciones/asignaciones/{id}/solicitudes`; `/componentes/agrupados`; `/telefonos/{imei}/modelo`; `/reparaciones/{id}/borrador` GET/PUT/DELETE; POST `/reparaciones/{idAsig}/filas`, `/agotar-componente`, `/reparaciones/completa`; GET `/reparaciones/{id}/detalle-edicion`; PUT `/reparaciones/{id}`; `/tecnicos/activos` | BD (debounce 2 s), SD, CD, Alertas |
| 10 | AgrupadoView / AgrupadoController | los 3 (`configurar(Rol)`) | Historial agrupado por IMEI con drill-down | filtros IMEI, técnico, cliente, fechas, incidencias; maestro/detalle | doble clic → detalle; ctx editar, borrar, incidencias, observación, cliente; toggle Revisión logística; CSV | 1327 | historial rep/glass/pulido (`?tecnico=` si TECNICO); PUT `/telefonos/{imei}/revision-logistica`; PATCH `/observacion`, `/cliente`; incidencia/referenciadora/eliminar | MS, FI, E, CD, SC, SD, TT, CR, GrupoImei |
| 11 | PulidoTecnicoView / PulidoTecnicoController | TECNICO, SUPERTECNICO | Mis pulidos pendientes, completar en lote | filtro IMEI, checks | seleccionar todo, completar; papelera (Super) | 282 | `/pulidos/asignaciones?tecnico=`; POST `/pulidos/asignaciones/completar-lote`; DELETE `/pulidos/asignaciones/{id}` | FI, CD |
| 12 | PulidoSuperTecnicoView / PulidoSuperTecnicoController | **huérfana** (ningún FXML/controller la carga; solo `docs/permisos_roles.md`) | Gestión de asignaciones de pulido (sustituida por #8) | — | reasignar, asignar lote, comentario | 888 | `/pulidos/asignaciones` GET/POST/PATCH/DELETE; `/telefonos` | MS, FI, IU, CD, SD |
| 13 | HistorialPulidoView / HistorialPulidoController | los 3 (include) | Histórico de pulidos | filtros IMEI, técnico, fechas (por fecha fin) | ctx editar modelo, borrar con motivo (Super) | 369 | `/pulidos/historial[?tecnico=]`; DELETE `/pulidos/historial/{id}` (body motivo); POST `/telefonos` | MS, FI, CD |
| 14 | StockView / StockController | todos (permisos por rol dentro) | Inventario, pedidos (componentes/otros), proveedores | sidebar 3; Stock: filtro estado, buscador, PieChart + BarChart; Pedidos: toggle Componentes/Otros, estado, proveedor, fechas; Proveedores | ctx stock: pedir, editar stock, mínimo, desactivar, solicitar pieza; pedidos: confirmar/editar/borrar/recibido/parcial/resto/alterado/cancelar/desrecibir; proveedores CRUD; CSV | 2020 | ComponenteDAO `/componentes/gestionados`, PUT `/componentes/{id}`, PATCH `/stock-minimo`, `/activo`; CompraComponenteDAO `/compras`, `/compras/cantidad-en-camino/{id}`, PATCH `/compras/{id}/{confirmar,confirmar-recibido,confirmar-parcial,recibir-resto,confirmar-alterado,cancelar,desrecibir}`, DELETE; CompraOtroDAO ídem en `/compras-otros`; ProveedorDAO `/proveedores…`; SolicitudStockDAO POST `/solicitudes-stock` | P, R, E, MS, CD, SD (17 usos) |
| 15 | FormularioCompraView / FormularioCompraController | SUPERTECNICO | Alta multilínea de pedidos de componentes | tabla editable con autocompletar | añadir línea, confirmar; `abrir`, `abrirConSolicitudes`, `abrirConComponentes`, `abrirEditar` | 734 | `/componentes/gestionados`, `/proveedores/activos`, `/tipo-cambio/{divisa}`, POST `/compras` | Alertas |
| 16 | FormularioCompraEditar / …EditarController | SUPERTECNICO | Editar pedido de componente | — | confirmar | 163 | PUT `/compras/{id}`, `/tipo-cambio` | SD |
| 17 | FormularioOtroPedidoView / …Controller | SUPERTECNICO | Alta multilínea de "otros" pedidos | tabla editable (concepto libre) | añadir, confirmar | 517 | `/proveedores/activos`, `/tipo-cambio`, POST `/compras-otros` | Alertas |
| 18 | FormularioOtroPedidoEditar / …EditarController | SUPERTECNICO | Editar otro pedido | — | confirmar | 193 | PUT `/compras-otros/{id}` | SD |
| 19 | ClientesView / ClientesController | todos (`soloLectura = !esSuperTecnico`) | CRUD de clientes | filtro MS, badge estado | nuevo, editar, activar/desactivar, borrar (si no tiene teléfonos) | 215 | `/clientes`, `/clientes/{id}/tiene-telefonos`, POST/PUT/PATCH activo/DELETE | MS, CD, SD (no R) |
| 20 | LogView / LogController | ADMIN | Auditoría | filtros texto, acción (60 tipos), usuario, fechas | cargar, limpiar, doble clic detalle | 256 | `/logs?accion&tecnico&desde&hasta`; `/usuarios/tecnicos` | CD.mostrarTexto |
| 21 | EstadisticasView / EstadisticasController | todos (ADMIN: valores, excluidos, desplegable) | Puntos por técnico/periodo, tarjetas, stock por modelo | sidebar Técnicos / Stock; granularidad; métrica; MS técnicos; Equipo; Ocultar medias; LineChart; tarjetas; BarChart | recargar, limpiar, valores, excluir técnicos, popover → "Ver en Historial / IMEIs" | 1588 | `/reparaciones/estadisticas/puntos?granularidad&desde&hasta`; `/tecnicos`; `/valores-dificultad` GET/PUT; PATCH `/usuarios/tecnicos/{id}/{incluir,excluir}-estadisticas`; `/componentes/gestionados` | R (sin P), N, MS (con buscador), PuntosEstadistica |
| 22 | RegisterView / RegisterController | ADMIN | Alta/gestión de usuarios-técnico | form + tabla | registrar, activar/desactivar, eliminar | 311 | `/usuarios/tecnicos` GET/POST, PATCH activar/desactivar, GET `tiene-reparaciones`, DELETE `?idUsu=` | Alert |
| 23 | CambiarPasswordView / CambiarPasswordController | todos | Cambio de contraseña propia | 3 campos | guardar | 124 | PATCH `/auth/cambiar-password` | Alert |

Otros ficheros de `controllers/`: `SelectorModeloDialog.java` (83 líneas) **sin llamadores** (código muerto; PST duplica la lógica inline en `abrirSelectorModelo` :2824). DAOs sin vista propia: `TipoCambioDAO` (EUR = 1.0 local, resto GET), `ValoresDificultadDAO`, `BorradorDAO`, `LogDAO`.

**Sin paginación**: ninguna vista usa "cargar más"/offset/limit; todas descargan la lista completa y filtran con `FilteredList` en cliente. Excepción: Estadísticas ventanea periodos en cliente (Día 30, Semana 16, Mes 12, Año 5) tras pedir el rango completo (sin fechas: 1900-01-01..2999-12-31, `EstadisticasController.java:498-501`).

---

## 3. Lógica de negocio en el cliente (lo que habrá que decidir dónde vive)

### 3.1 utils/ (puros, testeados)
- **`CargaTecnicos.java`** ⚠D — capacidad diaria. Topes de jornada 9 h: `TOPE_CHASIS_9H=8`, `TOPE_GLASS_9H=17`, `TOPE_NORMALES_9H=25` (:46-48); `PESO_POR_CERRAR=1/12` (:21); `JORNADA_HORAS` L9 M9 X8 J8 V6 S0 D0 (:51-55); `fraccion9h` (:61-72): pulido = 0, solicitud de pieza activa no recibida = 0, modo Pedidos ignora sin cliente; `calcularDia` (:74-98): % = fracción × 9/horas × 100. El servidor solo tiene `util/Jornada.java` (entrada 8:30 −30 min, salidas 18/18/17/17/14:30 +15 min) cuyo javadoc dice ser "las mismas JORNADA_HORAS del cliente"; ningún `TOPE_*` en servidor.
- **`PrediccionGlass.java`** ⚠D — elige técnico de glass automático: activo + `esGlass` + sin glass abierta de ese IMEI (BD ni modal), menor carga en fracción 9 h (hecho hoy + pendiente + verdes del modal, alcance Pedidos si el IMEI tiene cliente), empate alfabético (:40-63). Solo cliente.
- **`EntregaGlass.java`** — textos/píldoras de la entrega del teléfono al técnico de glass: badge "→ <glass>" (fila A) / "Llegó HH:mm" o "dd/MM" (fila AG) (:38-52); opción de menú Entregar / Deshacer entrega solo para quien firmó `glassEntregadoPor` (:78-84) ⚠D (servidor comprueba `esSuya`/`entregadoPor` en `ReparacionController.java:277-381`, coherente); "Deshacer llegada" solo el firmante (:88-92); `ocultarAnadirGlass` = glass con normal abierta y sin llegada (:117-120); `mostrarMarcarLlegada` (:127); píldoras "Glass: X" / "Rep: X" (:137-176); `ocultarContadorAsignados` cuando n==2 y es par rep⇄glass (:152-158). Zona horaria Madrid.
- **`PuntosEstadistica.java`** — `periodoAFechas` (:26-45); `diasLaborables` L–V sin festivos (:48-53); `esLaborable` (finde no promedia en Día, :62-66); `puntosDia` = puntos ÷ laborables hasta hoy, divisor mín. 1 (:76-80); `promedioVentana` = media por técnico-periodo con valor >0 (:90-100); `sinExcluidos` (:103-109); `parsePuntos` 0 ≤ v ≤ 99,99 con coma (:112-120) ⚠D (servidor `DificultadController.java:45` mismo rango); formatos "14,5" (:123-134); `puntosJornada`/`puntosExtra` con fallback a servidor antiguo (:153-166); `calcularTarjetas` (:230-304): mes cerrado vs previo `round((c−p)/p×100)`, objetivo de hoy = media del mismo día de semana del mes cerrado con puntos de jornada, fallback media global L–V, `pctHoy = floor(hoy/objetivo×100)`, finde sin fallback.
- **`Piezas.java`** — categoría por prefijo de SKU: `otro, cha, cam, bat, lcd, mc, g` (orden por longitud) → Otros/Chasis/Cámara/Batería/Pantalla/Marco/Glass (:12-15). Servidor tiene una lista de orden `bat, cha, g, mc, lcd` en `dao/ComponenteDAO.java:120` ⚠D.
- **`ImeiUtils.java`** — pegado de IMEIs: <15 INCOMPLETO, ==15 UNICO, múltiplo de 15 LOTE (troceado), resto CORRUPTO (:20-29).
- **`FiltroImei.java`** — canonicaliza el filtro multi-IMEI (solo dígitos y comas, trocea cada 15, añade ", " tras IMEI completo, idempotente) (:17-42); `imeisValidos` = tokens de 15 dígitos (:45-50); `estado` VACIO/INCOMPLETO/VALIDO → borde del campo (:53-59).
- **`TipoTrabajo.java`** — tipo por prefijo del ID: `AG`/`G` glass, `AP`/`P` pulido, resto reparación (:44-49) ⚠D (servidor `ReparacionController.java:45-46` mismo prefijo); paleta de badge por tipo (:17-19); celda "Tipo + Chasis" (:58-79).
- **`FechaUtils.java`** — todas las fechas del servidor son UTC y se muestran en `Europe/Madrid` (:13-22).
- **`CsvExporter.java`** — separador `;`, BOM UTF-8, nombre `<base>_yyyy-MM-dd_HH-mm.csv`, escapado de `;`/`"`/salto, `textoForzado` `="…"` para IMEIs (:31-77).
- **`Alertas.java`** — un solo Alert de error a la vez; silenciado durante el logout (:12-14).
- **`ConexionEstado.java`** — `delaySegundos()` 60 s normal / 5 s desconectado (:32).

### 3.2 PendientesSuperTecnicoController (2899)
- Tabla unificada A+AG+AP (:1259-1261); `completadas-hoy` degradable (:1263-1268); **orden urgente → con cliente → normal** (:1273-1277) duplicado en `PendientesTecnicoController.java:514`.
- Contador "N asignados" por IMEI si n ≥ 2 salvo par rep⇄glass (:1291-1301, :320-323) = alerta de asignación cruzada.
- Reasignación en celda con bloqueo optimista `updatedAt` → `StaleDataException` recarga (:213-235).
- ⚠S `soloLectura` (Admin) oculta menús de escritura, papelera y botón Asignar (:455-467, :627-634); editar modelo solo pulido; chasis solo reparación. Servidor sí protege esas escrituras con `hasRole('SUPERTECNICO')` → redundante pero correcto.
- Badges Estado (:512-590): Urgente, Por cerrar, entrega glass, Incidencia, solicitud → **"Recibido" si `estadoSolicitud=="GESTIONADA" && stockSolicitud>0`, "En camino" si `enCaminoSolicitud`, si no "Solicitud"**, Normal. Borde de fila naranja (solicitud) / rojo (incidencia) (:483-500).
- Filtros (:792-830): IMEI (set 15 dígitos), técnico, cliente incl. "(Sin cliente)", tipo, estado excluyente.
- ⚠D **Umbrales de color de carga**: `pctTotal ≥ 90` rojo, `≥ 70` ámbar, resto azul (:868-880); técnico sin filas = 0 con `sinJornada` (:898-908); ventana Carga ordenada por pct desc (:996-999), barra saturada a 100 (:1136).
- Técnicos de glass: solo envía cambios de `esGlass`; Admin solo lectura (:911-958).
- **Modal "Asignar trabajos"** (:1763-2748): pila roja (pendiente) / verde (asignada) por cola Rep/Glass + sub-lote Pulido; IMEI exactamente 15 dígitos y sin duplicados por cola (:2545-2560, :1731); pegado múltiple vía `ImeiUtils` (:2565-2600); lookup modelo/cliente en hilo aparte, `putIfAbsent` no pisa decisión manual (:2213-2270); modelo decidido se propaga a todas las entradas del IMEI y se persiste al vuelo con POST `/telefonos` (:2142-2158, `propagarModelo` :164-172, testeado); técnicos ya asignados a ese IMEI+categoría deshabilitados (:2272-2291); Asignar exige modelo y ≥1 técnico (:2110); técnicos "pegajosos" por cola (:2300-2347, :2617-2627); chasis y "Lleva glass" solo en Reparación; "Lleva glass" ⇔ entrada glass en cola (:2418-2441); predicción se recalcula mientras la glass sea `auto` (:2409-2416); Guardar deshabilitado con rojas o pulido sin técnico (:2207); **`urgente=false` siempre** (:2688; lo automatiza `servidor/job/UrgenteAutomaticoJob` a las 00:00 Madrid).
- ⚠C **Dedup de asignación** al guardar: N GET `tiene-asignacion?tecnico=&tipo=` desde el cliente, conflictos en Alert (:2690-2705); `servidor/dao/ReparacionDAO.insertarAsignacion` no lo comprueba → dos clientes concurrentes pueden duplicar. Guardado no transaccional (POST por entrada/técnico).

### 3.3 FormularioReparacionController (2389)
- `MODELOS_ORDENADOS` (catálogo hardcodeado 6s…17 Pro Max/Air, :99-109), `extraerModelo` (:873-886, testeado) y `traducirModelo` (:898-936) ⚠D (lista duplicada en servidor `ImeiLookupService.java:27-30`; el catálogo real es el SKU en BD).
- Incidencia activa por IMEI con categoría "G" si la asignación es `AG`, si no "R" (:117-123); aviso de otras asignaciones activas del IMEI con "(tú)" (:132-160).
- Solicitudes: activas bloquean fila y filtro de modelo; RECHAZADAS solo preseleccionan SKU (:168-210); "GESTIONADA && stock>0" ⇒ "✓ Recibido" y fila desbloqueada; `enCamino` ⇒ badge "⚠ En camino" (:1857-1900).
- Modelo desde BD bloquea el combo (:211-222); modelos derivados de SKUs activos (:406-485).
- **Filtrado por tipo de trabajo**: glass (`AG`/`G`) → solo grupos `g`, `mc`, `otro`; reparación excluye `g`/`mc` (:364-380).
- Habilitación de Guardar (:487-517); **borrador persistente** solo flujo nuevo, debounce 2 s, vacío → DELETE (:535-549), recuperación (:224-255), borrado tras guardar (:631-634), flush al cerrar (:845-847).
- Guardar nuevo (:662-760): agotados → `agotar-componente` (sin crear R); fila con uso + solicitud nueva ⇒ 2 filas; acciones "otro" cantidad 0; solo agotadas ⇒ cierra sin crear reparación; `insertarCompleta` con `Sesion.getIdTec()` en el body (:745) ⚠S (el servidor confía en el `idTec` del cuerpo; endpoint sin `@PreAuthorize`).
- Edición (:762-840): `editarReparacion` por fila con `updatedAt`; filas nuevas agrupadas por técnico; `getIdComsYaReparados` bloquea piezas ya usadas en el IMEI (:290-296).
- **Stock en `FilaUI`**: SKU por defecto = primero con stock>0 (:1067); "+" deshabilitado si stock ≤ 0 (:1078); cantidad ≤ stock salvo mismo componente en edición (:1087-1094) ⚠D (servidor también valida "Stock insuficiente", `dao/ReparacionDAO.java:537,622,936`); color SKU stock 0 rojo / `stock ≤ stockMinimo` ámbar (:1333-1341); preview `stock + devuelto − descontado` (:1410-1430); sub-fila agotado si stock 0 ("Solicitar pieza") o cantidad ≥ stock ("Solicitar y descontar") (:1453-1472).

### 3.4 AgrupadoController (1327) y `models/GrupoImei`
- ⚠S TECNICO carga con `?tecnico=Sesion.getIdTec()` en los 3 endpoints de historial (:162-184); servidor no verifica el parámetro.
- `GrupoImei.java:20-78`: cuenta rep/glass/pulido por prefijo; modelo/observación/cliente = primer valor no vacío; `fechaMasAntigua`/`fechaMasReciente`; `countIncAbiertas`; `resumen()` "R·G·P" omitiendo ceros (testeado).
- Maestro: grupo visible si algún trabajo pasa el filtro técnico; orden por `fechaMasReciente` desc (:1026-1052); detalle por `fechaAsig` asc, ajenos atenuados 0.45 (:955-1000); fechas filtran por `fechaFin` (sin fin ⇒ oculta).
- ⚠S Revisión logística: toggle solo `esSuper` (:559); **no se puede marcar si el IMEI tiene asignaciones abiertas** (:565, :605); bloqueo optimista con `telefonoUpdatedAt`.
- ⚠S Menú por rol solo UI: Editar (R/G), Borrar, incidencias, observación y cliente solo `esSuper` (:684-696); servidor protege esas escrituras.
- Borrar bloqueado si `getReferenciadora` devuelve otra reparación; motivo obligatorio (:1239-1264); incidencia exige comentario + técnico (:1126-1187).

### 3.5 ReparacionController{Tecnico,SuperTecnico,Admin} y PendientesTecnicoController
- ⚠S **`ReparacionControllerTecnico.java:721-737, 863`: descarga el historial COMPLETO (rep y glass) sin `?tecnico=` y filtra en cliente `rep.getIdTec() != Sesion.getIdTec()`** → un técnico recibe por HTTP el historial de todos.
- Filtros comunes (Tec :864-893, Super :867-903, Admin :644-680): IMEI, fechas por `fechaFin`, incidencias abiertas/cerradas/normales, pieza por `Piezas.categoria`; Super/Admin + técnico multi.
- Badges sidebar rep+glass+pulido con cap "99+" (Tec :274-292, Super :269-290); badge Estado Incidencia/Resuelta/Normal.
- ⚠S Super: menú Editar solo R/G, Añadir incidencia si no tiene, Cancelar si abierta (:672-675); Admin solo Copiar (:439). Incidencia exige comentario + técnico (:1031-1118); borrar bloqueado por referenciadora + motivo (:1135-1152). CSV: "en espera de pieza" = `esSolicitud>0 && !(GESTIONADA && stock>0)` (:1259-1262) duplica `CargaTecnicos.enEsperaDePieza`.
- Admin: sin Poller (:200), `setSoloLectura(true)` (:157), `configurar(ADMIN)` (:159).
- PendientesTecnico: ⚠S carga `?tecnico=idTec` (:508-517, servidor no valida); menú por estado vía `EntregaGlass` (:215-226); badges como PST + "N piezas" si `esSolicitud>1` (:300-333); "Añadir glass" oculto por `ocultarAnadirGlass` (:369); ⚠S papelera solo `esSuperTecnico` (:374-405; servidor sí exige rol en DELETE); filtro tipo solicitud/incidencia/asignación (:466-485).

### 3.6 StockController (2020)
- Semáforo (:1896-1901): `!activo` → Desactivado; `stock==0` → Sin stock; `stock<=stockMinimo` → Bajo; resto OK ⚠D (servidor `/stock-bajo` usa `STOCK <= STOCK_MINIMO`, `dao/ComponenteDAO.java:81`; "Sin stock" vs "Bajo" solo cliente). PieChart con la misma regla, excluye desactivados (:496-506). Barra roja en Estadísticas/stock con la misma regla (`EstadisticasController.java:1437`).
- SKU compartido: `idComMaster != null` → "(compartido)" (:300); "En camino" lo agrega el servidor por master.
- Orden: activos antes que desactivados (:477); pedidos cancelados al final (:1016, :1283).
- ⚠S Permisos solo UI (:171-175, :354-388, :961, :1036, :1228, :1694): botones "Nuevo…" y menús completos solo SUPERTECNICO; ADMIN sin menú de stock; TECNICO solo "Solicitar pieza"; "En camino" del gráfico solo ADMIN/SUPERTECNICO (:547). Servidor protege escrituras de componentes/compras/proveedores con `hasRole('SUPERTECNICO')` **salvo** `PATCH /api/componentes/{id}/stock` (`ComponenteController.java:93`, sin `@PreAuthorize`) y `/api/solicitudes-stock` (sin `@PreAuthorize`).
- Validaciones: stock y mínimo enteros ≥ 0 (:650-660, :690-696); ⚠C recepción parcial `0 < cant < cantidad` (:1516; servidor `confirmarParcial` no valida rango); ⚠C recibir resto `cant>0` y `recibidas+cant ≤ cantidad` (:1551-1560; servidor cierra si `>=`, sin tope).
- ⚠C **Transiciones de pedido permitidas por estado** (menú :970-1010, otros :1237-1276): pendiente→{confirmar, editar, borrar}; en_camino→{parcial, recibido, editar, cancelar}; parcial→{recibir resto, cerrar sin resto (alterado)}; recibido→{revertir (desrecibir), editar}; cancelado→nada. Servidor solo verifica `ESTADO='pendiente'` en confirmar/borrar (`dao/CompraComponenteDAO.java:149-162`) y stock suficiente en desrecibir (:170-176).
- Colores fila pedidos (:868-876, :1144-1153): pendiente ámbar `#C8961E`; en_camino barra roja solo si urgente; recibido/parcial `Colores.FILA_*`; cancelado opacidad 0.45; badge con "⚠" si urgente y estado ∈ {en_camino, parcial} (:900-916); "!" si recibido con precio/total 0 (:815, :841). Total EUR = unidades (recibidas si recibido) × precioEur (:838-840). Símbolo divisa EUR→€, USD→$ (:810-813).
- Navegación desde alerta: preselecciona pendiente/en camino/parcial (:220-223). Borrar proveedor solo si `!tienePedidos` (:1697-1711); nombre obligatorio (:1779, :1849).

### 3.7 Formularios de compra / otro pedido
- Solo componentes y proveedores **activos** (FC :78-82). Divisa = la del proveedor; tasa EUR = 1.0 local, otras vía `/tipo-cambio/{divisa}` con caché por divisa e hilo `tasa-fetch` (FC :93-117); total línea = `precio × tasa × cantidad` (FC :452-455, FO :386-390).
- ⚠C Validación al confirmar: ≥1 línea, componente/concepto y proveedor obligatorios, `cantidad>0`, `precio≥0` (FC :517-540, FO :444-468, FCE :117-136, FOE :155-163). **`precioEur = precio × tasa` se calcula en cliente y se persiste tal cual** (`dao/CompraComponenteDAO.java:67-74, 93-100`).
- Inserta **una compra por línea**, no transaccional (FC :542-550, FO :470-478). Desde solicitudes: agrupa por `idCom` y **cantidad = nº de solicitudes** (FC :576-591, :606-621); desde alertas: 1 línea por componente, cantidad 1 (:645-657). Editar precarga `cantidadRecibida ?? cantidad` (FCE :73); "otros" conserva `esUrgente` (FOE :171).

### 3.8 EstadisticasController (1588)
- ⚠S **No-ADMIN ve solo lo suyo únicamente en cliente**: preselecciona su técnico y oculta el desplegable (:364-367, :388-393, :1249-1250); tarjetas con `tecnico = nombreTecnicoSesion` (:1504); pero `GET /reparaciones/estadisticas/puntos` devuelve todos los técnicos sin `@PreAuthorize` ni filtro por principal (`servidor/controller/ReparacionController.java:172-179`) → coincide con el pendiente "filtrado en servidor (F3)".
- Botones valores/excluidos solo ADMIN (:182-185; servidor exige ADMIN, OK); quitar serie y navegar solo ADMIN o serie propia (:703-717, :1016-1018).
- Ventanas por granularidad Día 30 / Semana 16 / Mes 12 / Año 5 (:153-156); con filtro de fechas, ventana = todo (:536-539). Excluidos = `!esEstadistica` fuera de Equipo/promedio/tarjetas pero visibles si se seleccionan (:360-362, :590-595). Equipo = suma por periodo (:609-621); eje Y `max(5, ceil(max)+1)` (:641).
- Medias (:749-757, :786-798, :884-903): solo `puntosJornada` y periodos laborables; "por encima" = media > ref, "por debajo" = `0 < media ≤ ref`. IMEIs típicos = `promedioVentana(nImeisJornada)`; chip verde si ≥ media (:656-663, :718-724).
- Tarjetas (:1497-1529): 3 meses en granularidad Día; variación ≥0 verde `#2E7D32` / rojo `#C62828`; objetivo ≥100 verde, nunca rojo.
- Stock por modelo: prefijos `bat/cha/cam/lcd/mc/g` + `extraerModelo`, solo activos, barra roja si `stock ≤ stockMinimo` (:1296-1346, :1437). Color de técnico = hue `idTec×137.508 mod 360` (:448-455).

### 3.9 Resto
- **Pulidos**: ⚠S `PulidoTecnicoController.java:268-271` y `HistorialPulidoController.java:277-282` cargan `?tecnico=idTec` (TECNICO) o todo (Admin/Super); servidor `PulidoController.java:36-44` acepta cualquier `tecnico`. Completar lote requiere selección (:215-231); borrar/editar modelo solo Super (:89-107, HP :120-149; servidor exige rol). "Editar modelo" hace POST `/telefonos` (upsert). Contadores cap "999+".
- **Clientes**: ⚠S `soloLectura = !esSuperTecnico` (:43, :52, :107; servidor protege con `hasRole('SUPERTECNICO')`); borrar solo si `!tieneTelefonos` (:119-124); nombre obligatorio (:174-186); filtro solo activos.
- **Log**: catálogo cerrado de 60 `TIPOS_ACCION` (:42-63); búsqueda de texto en cliente sobre usuario/acción/detalle (:245-255, testeado); acción/usuario/fechas al servidor.
- **Register**: roles ofrecidos TECNICO/SUPERTECNICO (:53); ⚠C duplicado de técnico/usuario en vivo (case-insensitive) contra la lista cargada (:67-82); ⚠C obligatorios + **contraseña ≥ 6** (:262-275; servidor solo lo valida en cambiar-password `AuthController.java:60`); no elimina técnico con reparaciones (:220-230).
- **CambiarPassword**: obligatorios, nueva ≥ 6, confirmación igual (:84-95) ⚠D (servidor duplica el ≥6).
- **Login**: sin bloqueo de intentos ni rate limiting (ni en cliente ni en servidor).
- **MainController**: `esAlertaStock` = master + activo + `stock ≤ stockMinimo` (:726-728, testeado) ⚠D con `/stock-bajo` del servidor (que el cliente no consume).

---

## 4. Transversal

- **Sesion** (`Sesion.java`): usuario, rol como string, `idTec`; sin token (el JWT lo guarda `ApiClient`). `Usuario` también trae `nombreTecnico`, `activo`, `esEstadistica`.
- **ApiClient** (`utils/ApiClient.java`): `HttpClient` con `connectTimeout 5 s` (:39-41) y `timeout 15 s` por petición (:276); base URL de `config.properties` (`api.url=https://api.fonestore.es`, fallback `http://localhost:8080`); cabeceras JSON + `Authorization: Bearer <jwt>` (:277); Gson con `LocalDateTime` ISO y `LocalDate` (:43-71); helpers `get/getList/getBoolean/getInt/getDouble/getString/post/put/patch/delete/deleteWithBody`; **sin reintentos** (solo el login reintenta una vez en `UsuarioDAO`). `handleErrors` (:295-302) → `clasificar` (:305-320, testeado): 401 → `SesionExpiradaException` (dispara el handler una sola vez si hay sesión, :103-109), 403 → "No tienes permisos", 404, **409 → `StaleDataException`** (bloqueo optimista con `updatedAt`, los controladores recargan), 422 → mensaje del servidor (reglas de negocio), ≥500 → `ConexionException`; IOException → `ConexionException` + `ConexionEstado.reportarFallo()` (:281-290). Todas extienden `SQLException` por compatibilidad.
- **ConexionEstado / banner**: `desconectado` volátil + property; éxito 2xx reconecta; `enRefresco` evita modales durante el poll (los catch muestran `Alertas` solo si no es `ConexionException` en refresco).
- **Poller** (`utils/Poller.java`): auto-reprogramación en `ScheduledExecutorService` (hilo daemon) → `Platform.runLater(recargar)` cada 60 s (5 s desconectado). Usan Poller: `ReparacionControllerTecnico` (:199), `ReparacionControllerSuperTecnico` (:231), `StockController` (:179), notificaciones (`MainController.java:328`). Sin Poller: Admin, Estadísticas, Clientes (Clientes ni siquiera es `Recargable`).
- **Recargable** (`recargar()`, `detenerPolling()`), **Navegable** (`navegarAReparaciones(desde, hasta, tecnico, aImeis)`: Estadísticas → Historial/IMEIs con filtros), **Exportable** (`exportarCSV(Stage)`, disparado desde el menú de usuario).
- **Recarga al recuperar foco**: `MainController.java:128-135` (ventana principal) y `:406-408` (panel de notificaciones).
- **Fechas**: UTC del servidor → `Europe/Madrid` (`FechaUtils`). Patrones usados: `dd/MM/yyyy HH:mm` (13 usos), `HH:mm` (13), `yyyy/MM/dd HH:mm` (8, CSV), `dd/MM HH:mm` (3), `dd/MM/yyyy` (2), `dd/MM`, `dd/MM/yy HH:mm`, `dd/MM/yyyy HH:mm:ss` (log), `yyyy/MM/dd`, `yyyy-MM-dd_HH-mm` (nombre CSV). Formato de puntos "14,5" (coma, 1 decimal, HALF_UP).
- **Paginación**: inexistente (ver §2).
- **app.css** (`src/main/resources/styles/app.css`, 623 líneas, secciones: layout, navbar, botones, inputs, tabla, sidebar, combobox, datepicker, menubutton, contextmenu/popups, toggle-pill, badge, banner). Clases: `.vista-container` (fondo `#DDE1E7`), `.vista-titulo`, `.navbar`, `.navbar-logo`, `.navbar-title`, `.navbar-user`, `.navbar-user-btn`, `.navbar-bell-btn`, `.navbar-logo-btn`, `.navbar-badge`, `.navbar-change-user`, `.nav-switch`, `.nav-btn`, `.nav-btn-active`, `.btn-primary`, `.btn-secondary`, `.buscador`, `.form-label`, `.paginacion-label` (sin uso real), `.tabla-reparaciones` (+ `.column-header`, `.table-row-cell:selected`, `.table-cell`), `.stock-sidebar`, `.stock-sidebar-btn`, `.stock-sidebar-btn-active`, `.combo-box-base`, `.combo-box-popup .list-view` (+scrollbars), `.multi-select-popup`, `.date-picker`, `.menu-button`, `.context-menu`, `.menu-item`, `.custom-menu-item`, `.toggle-pill-left/-mid/-right` (+`:selected`), `.sidebar-badge`, `.sidebar-item-active/-inactive`, `.banner-conexion`. **Muchísimo estilo va inline** en los controllers (`setStyle` con hex), no en CSS.
- **Paleta (`utils/Colores.java`)**: marca `AZUL_NOCHE #001232`, `AZUL_MEDIO #2C3B54`, `AZUL_GRIS #586376`, `CREMA #F6F6F6`, `AMARILLO #F1E356`; filas: edición `#EBF4FF/#B3D4F5`, reparado `#EBF5EB/#C5E1C5`, recibido `#C8E6C9/#3a7d44`, parcial `#E8E0F7/#7B5EA7`, cancelado `#E0E0E0/#9E9E9E`, incidencia `#E8C8CE/#B83746`, solicitud `#FDEBC8/#C07800`, urgente borde `#C62828`, modificada `#E0F7FA/#00838F`; texto `TEXTO_ERROR #B03040`, `TEXTO_ACCION #4A6FA5`, `VERDE_OK #4CAF50`; UI `FONDO_INPUT #F3F3F3`, `GRIS_BORDE #A9A9A9`, `GRIS_DISABLED #E7E7E7`, `FILA_SELECTED_BRD #3D5070`, `FILA_SEP #C2C8D0`; `ROJO_ACCION #A84040`, `ROJO_SIN_STOCK #B03040`. Badges de tipo: Reparación `#E3F2FD/#1565C0`, Glass `#E0F2F1/#00796B`, Pulido `#EDE7F6/#5E35B1` (`TipoTrabajo`); entrega glass índigo `#E8EAF6/#3949AB` (`EntregaGlass`). Otros hex frecuentes en CSS: `#FFFFFF`, `#FAFAFA`, `#E8EAF0`, `#F0F2F5`, `#C8CDD5`, `#0A2040`, banner `#F6C453/#5A4500`.
- **Imágenes** (`src/main/resources/images/`, 16 PNG): `icono_programa` (icono ventana), `logoNavBar`, `logo_inicio_sesion`, `logo`, `user` (navbar), `NotfON`/`NotifOFF` + `Badge` (campana), `borrar` (10 usos: papeleras), `editar` (5), `ojo_activar`/`ojo_desactivar` (toggle contraseña), `Lock`/`Unlock` (activar técnico en Register), `Historial`. Muchos iconos son texto/emoji ("✕", "!", "⚠", "✓", "→").

---

## 5. Tests existentes (`src/test`, 25 ficheros, 2 051 líneas; JUnit 5, sin JavaFX ni TestFX)

| Fichero | Qué especifica |
|---|---|
| `utils/CargaTecnicosTest` (7) | Fracciones sobre jornada 9 h, escalado por jornada corta, hecho-hoy en su tramo, modo Pedidos filtra por cliente, pesos (por cerrar, pulido 0, espera pieza 0), finde sin jornada, formato "79%" |
| `utils/PrediccionGlassTest` (16) | Sin habilitados → null; inactivo fuera; empate alfabético; excluye quien ya tiene glass del IMEI (BD o verde); menor carga gana; cerradas hoy y verdes cuentan; alcance Pedidos/Total; chasis verde pesa como chasis |
| `utils/EntregaGlassTest` (~30) | Badges A/AG, tooltips, opciones de menú (entregar/deshacer solo firmante), CSV, píldoras "Glass:"/"Rep:", contador asignados, ocultar "Añadir glass", marcar llegada, paleta índigo |
| `utils/PuntosEstadisticaTest` (~40) | Periodo→fechas, laborables L–V, puntos/día, promedio por técnico-periodo trabajado, parse 0–99,99, formatos, tooltip/popover, etiqueta ventana, tarjetas (mes cerrado vs previo, objetivo por día de semana, fallback media global, finde sin línea, excluidos, −100 %, signo), jornada/extra con servidor viejo/nuevo |
| `utils/FiltroImeiTest`, `ImeiUtilsTest`, `PiezasTest`, `CsvExporterTest` | Canonicalización/estado del filtro IMEI; pegado 15/lote/corrupto; categoría por prefijo SKU; `textoForzado` |
| `utils/ApiClientClasificarTest` (8), `SesionExpiradaHookTest` (3), `ConexionEstadoTest` (3) | Mapeo HTTP → excepciones (401/403/404/409/422/5xx, mensaje 422 conservado); disparo único del handler de sesión caducada y rearme tras relogin; estado conectado/desconectado y flag `enRefresco` |
| `controllers/PendientesSuperTecnicoControllerTest` (10) | `contarTecnicosPorImei`, tipo por prefijo, `propagarModelo` a rojas y verdes del mismo IMEI |
| `controllers/MainControllerTest` (5) | `esAlertaStock`: master activo con stock ≤ mínimo; slave y desactivado no alertan |
| `controllers/LogControllerTest` (9), `FormularioReparacionControllerTest` (1) | `coincideTexto` (vacío/nulo, case-insensitive, por campo); `extraerModelo` (Air) |
| `models/*Test` (9 ficheros) | Getters/`toString`, `Usuario.esAdmin/esSuperTecnico/esAdminOSuperTecnico`, `Tecnico.esGlass` (JSON sin campo = false), `GrupoImei` (conteo por prefijo, resumen, índice por IMEI), `BorradorContenido` round-trip JSON (formato antiguo lanza), `CompraComponente` totales y estado inicial `en_camino`, `ReparacionResumen.isPendiente` |

**No cubierto por tests**: navegación, StockController (transiciones de pedido), FormularioReparacion (salvo `extraerModelo`), Agrupado, Pulidos, Clientes, Register, formularios de compra, CSV de cada vista, todo lo que toca JavaFX.

---

## 6. Sugerencia de mapeo a módulos web

### 6.1 Módulos
| Módulo | Vistas JavaFX que absorbe | Roles |
|---|---|---|
| **Auth/Shell** | Login, MainView (navbar, banner de conexión, menú usuario), CambiarPassword, sesión caducada | todos |
| **Taller · Mis trabajos** | PendientesTecnicoView (rep + glass), PulidoTecnicoView, FormularioReparacionView (+ borrador) | TECNICO, SUPERTECNICO |
| **Taller · Asignaciones** | PendientesSuperTecnicoView (tabla unificada, reasignar, urgente/chasis/cliente/comentario), modal Asignar trabajos, Carga de técnicos, Técnicos de glass | SUPERTECNICO (ADMIN lectura) |
| **Taller · Historial** | Historial rep/glass (tabla de ReparacionView\*), HistorialPulidoView, AgrupadoView (IMEIs), incidencias, revisión logística | los 3 (filtros por rol) |
| **Almacén · Stock** | StockView/Stock, editar stock, mínimo, solicitar pieza, alertas (pestaña Alertas de la campana) | SUPERTECNICO edita; resto lectura |
| **Almacén · Pedidos** | StockView/Pedidos (componentes y otros), FormularioCompra(+Editar), FormularioOtroPedido(+Editar), solicitudes de pieza (pestaña Solicitudes de la campana) | SUPERTECNICO |
| **Almacén · Proveedores** | StockView/Proveedores | SUPERTECNICO |
| **Gestión · Clientes** | ClientesView | SUPERTECNICO edita |
| **Gestión · Estadísticas** | EstadisticasView (puntos, tarjetas, stock por modelo), valores de dificultad, excluidos | todos (ADMIN admin) |
| **Gestión · Administración** | RegisterView (usuarios/técnicos), LogView | ADMIN |
| **Descartar** | PulidoSuperTecnicoView (huérfana), SelectorModeloDialog (sin llamadores) | — |

### 6.2 Componentes reutilizables que se repiten
- **`DataTable` con barra de filtros** (IMEI multi con canonicalización/estado del borde, técnico multi, cliente multi, fechas desde/hasta, estado/tipo excluyente, buscador texto), fila con **borde-izquierdo por estado** y opacidad para ajenos/cancelados/desactivados, menú contextual "Copiar celda", contador "N (99+/999+)". Usado en ≥12 vistas.
- **`Badge`/píldora**: tipo de trabajo (Rep/Glass/Pulido + sub-etiqueta "Chasis"), estado (Urgente, Por cerrar, Incidencia/Resuelta, Solicitud/En camino/Recibido, Normal), entrega glass ("→ X", "Llegó hh:mm"), píldoras bajo IMEI ("Glass: X", "Rep: X", "N asignados"), estado de stock (OK/Bajo/Sin stock/Desactivado), estado de pedido, Activo/Inactivo.
- **`MultiSelect`** con checkboxes, etiqueta resumen, buscador opcional y separador activos/inactivos (`MultiSelectDropdown`).
- **`TogglePill`** (segmentado Rep/Glass/Pulidos, Componentes/Otros, Solicitudes/Alertas, Puntos/Puntos-día).
- **`ConfirmDialog`** con cuenta atrás y variantes con motivo obligatorio / solo texto; **`SelectorCliente`** y **`SelectorModelo`** (lista filtrable).
- **Modal de asignación** (escáner de IMEIs con pegado en lote, pila roja/verde, técnicos pegajosos, predicción glass, lookup modelo/cliente).
- **Tarjeta de carga por técnico** (barra doble hecho/pendiente, umbrales 70/90) y **tarjetas KPI** de estadísticas.
- **Editor inline** (combo técnico en celda, celdas numéricas/decimales de los formularios de compra con commit al perder foco).
- **Banner de conexión + poller** (SWR/React Query con `refetchInterval` 60 s / 5 s y `refetchOnWindowFocus` cubren Poller + recarga al foco), **manejo 401/409/422** centralizado (interceptor → logout, "datos desactualizados: recarga", mensaje de negocio).
- **Exportar CSV** (`;`, BOM, `="imei"`), **formateo de fechas** Madrid y de puntos con coma.

### 6.3 Recomendación de reparto cliente/servidor antes de migrar
Mover al servidor (o al menos duplicar con test de contrato) todo lo marcado ⚠S/⚠C: filtro por técnico atado al principal en `/historial`, `/asignaciones`, `/pulidos/*`, `/estadisticas/puntos`; dedup de asignaciones; transiciones y rangos de pedidos; contraseña ≥ 6 en registro; `precioEur`; `@PreAuthorize` en `PATCH /componentes/{id}/stock` y `/solicitudes-stock`. Valorar exponer `CargaTecnicos`/`PrediccionGlass`/`PuntosEstadistica.calcularTarjetas` como endpoints (o mantenerlos puros en TS con los mismos tests, que hoy son la especificación más completa que existe).
