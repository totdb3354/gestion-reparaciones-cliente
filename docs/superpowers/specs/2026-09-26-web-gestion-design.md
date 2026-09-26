# Sub-proyecto 6 — Gestión: usuarios y técnicos, logs de actividad, cambiar contraseña y menú de usuario

**Fecha:** 2026-09-26
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra, §7 fila 6: "Usuarios y técnicos, logs, registro, cambiar contraseña, historial de pulido, menú de usuario | Validaciones que hoy están en cliente"). Antecesor directo: [Pedidos, formularios de pedido y campana (4b)](2026-09-25-web-almacen-pedidos-design.md). Decisión vigente del 2026-09-26: la web migra solo la línea `hotfix/0.16.3` del cliente; Inventario, importador y Revisión (4c-4e) quedan fuera y el JavaFX no se toca.
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-gestion`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`).

## 1. Objetivo

Sustituir los tres "Pendiente de migrar" que quedan en el menú de usuario: **"Gestionar técnicos"** (`/gestion/tecnicos`: alta de usuario y técnico, tabla de técnicos registrados, activar/desactivar acceso y eliminar), **"Ver logs"** (`/gestion/logs`: visor del log de actividad con sus filtros, el buscador y el detalle por doble clic) y **"Cambiar contraseña"** (diálogo sobre la vista actual). Con ello el menú de usuario queda completo y el ADMIN puede administrar accesos desde la web.

En el servidor sube lo que hoy solo aplica el cliente: la validación del alta de usuarios y del cambio de contraseña (422 con los textos del cliente), la comprobación de datos asociados antes de borrar un técnico (409), el 404 de ids inexistentes, un `limite` opcional y el filtro de fechas en hora de Madrid en el log, y una lista real de acciones del log. Todo aditivo.

Entra además, por decisión del brainstorming (G7), el **CSV de la vista Asignaciones** de la línea `hotfix/0.16.3` (14 columnas con "Entregado"), que la web no tenía.

Entrega: web **v0.8.0**. Por fuera queda como el JavaFX; las diferencias son las de la §10. Referencia: `hotfix/0.16.3` (solo lectura), con paridad verificada contra los inventarios del código de Gestión y las capturas, guardados fuera del repo porque llevan datos reales. Para Gestión no hay hoy ninguna captura del JavaFX: se toman antes de la comparación (§9).

## 2. Fuera de alcance

- **Cambiar el rol** de un usuario: no existe ni en el cliente ni en el servidor; la web no lo añade.
- **Excluir/incluir de estadísticas**: vive en el modal 👥 de Estadísticas (sub-proyecto 5, aplazado a después del corte). Los endpoints ya existen y no se tocan.
- **Técnicos de glass**: ya migrado en el sub-proyecto 3 (`TecnicosGlassDialog`).
- **Historial de pulido**: ya migrado en el sub-proyecto 1 (`/reparaciones/historial/pulidos`).
- Cambios en la gestión de sesión y de la cuenta (caducidad al desactivar, restablecer contraseñas desde ADMIN y afines): sub-proyecto 7 (red y hardening), lista en `Apuntes/plan-futuro.md`.
- Conservar el log de actividad de un usuario eliminado (hoy el servidor lo borra con el usuario): se mantiene el comportamiento actual (G8); cambiarlo exige migración de esquema.
- Tope de longitud de la contraseña (72 bytes de BCrypt): sin texto en el cliente, no se añade.
- CSV del visor de logs y de la página de técnicos: el JavaFX no los tiene (calco).
- La entrada "Estadísticas" del shell sigue como "Pendiente de migrar" (decisión del 2026-09-26).

## 3. Decisiones de este sub-proyecto

| # | Decisión | Por qué |
|---|---|---|
| G1 | **Las validaciones del alta y del cambio de contraseña suben al servidor como 422 con los textos del cliente**; los duplicados siguen siendo 409 (ya existen con los textos exactos); rol inválido pasa de 400 en texto plano a 422 `{message}`; `idTec` inexistente pasa de 500 a 404 | Spec maestra §7 ("validaciones que hoy están en cliente"); mismo patrón que 4a/4b; el JavaFX bloquea antes y no los ve |
| G2 | **El registro vive dentro de "Gestionar técnicos"**, como en el JavaFX (formulario arriba, tabla debajo), pero como **página** `/gestion/tecnicos` del shell con guarda `RequiereAdmin` y "Cerrar" que vuelve a la vista desde la que se abrió | Calco del contenido; una página se enlaza y no encoge en pantallas pequeñas; misma decisión que Clientes |
| G3 | **El visor de logs pide las 1.000 filas más recientes** con un parámetro `limite` opcional y aditivo del servidor (sin él, todo: el JavaFX no cambia) y avisa cuando llega al tope; los filtros de acción, usuario y fechas siguen en el servidor y el buscador en memoria | El JavaFX descarga el log entero en cada cambio de filtro; sin paginación (no aporta en un visor de auditoría) |
| G4 | **`GET /api/logs/acciones`** (`SELECT DISTINCT ACCION ORDER BY ACCION`) alimenta el filtro "Acción..." | La lista fija del cliente tiene 56 códigos frente a ~92 que escribe el servidor y 3 que ya no se generan; un endpoint no se desalinea |
| G5 | **El filtro de fechas del log se evalúa en hora de Madrid** en el servidor (`FECHA >= inicio del día desde` y `< inicio del día siguiente a hasta`, en UTC) | Hoy `DATE(FECHA)` se calcula en UTC y la tabla muestra Madrid: lo ocurrido entre las 00:00 y las 01:59 caía en el día anterior. Afecta al JavaFX solo en esas horas y a mejor |
| G6 | **"Cambiar contraseña" es un diálogo abierto desde el menú de usuario sin navegar**; la ruta `/cuenta/cambiar-password` desaparece del router | Calco del `Stage` modal: conserva la vista de fondo |
| G7 | **El CSV de Asignaciones del hotfix entra** como tarea pequeña (14 columnas, fichero `reparaciones_pendientes`) | Hueco de paridad con la línea que usa la tienda, pendiente del 3a; SP6 es el último bloque de código antes del piloto |
| G8 | **Borrar un técnico**: el servidor comprueba **todas** las referencias (no solo `Reparacion.ID_TEC`) tanto en `tiene-reparaciones` como en el `DELETE`, que responde 409 sin borrar nada; `idUsu` se resuelve desde `idTec` (el parámetro sigue en el contrato y se ignora); el log de actividad del usuario se sigue borrando (calco, anotado) | Hoy un técnico que asignó, entregó glass, pidió stock o movió teléfonos pasa la comprobación y el borrado acaba en 500; el `idUsu` que manda el cliente es redundante con `idTec` |
| G9 | **Diálogos nativos → propios**: los `Alert` de información y confirmación pasan a `mostrarAviso` y `ConfirmDialog`; los errores de carga, activar/desactivar y borrar se quedan **inline** como en el JavaFX, pero se limpian al empezar cada acción y muestran el `message` del servidor en 404/409/422 (texto fijo del JavaFX en el resto) | La web no tiene diálogos nativos (D13 del 4); un texto fijo escondería los 409/422 nuevos |
| G10 | **Filtro "Técnico..." calco** (solo usuarios TECNICO y SUPERTECNICO, `GET /api/usuarios/tecnicos`); los dos popups de acción y usuario pasan a `CampoAutocompletar`; sin ordenación por cabecera en las dos tablas; sin CSV en logs ni en técnicos ("Descargar CSV" deshabilitado en esas rutas) | D13; el JavaFX ordena la fecha como texto; el `Popup` con `ListView` es un artefacto de JavaFX |
| G11 | **`CampoPassword`** (input + ojo) se extrae a `shared/ui` y lo usan el login y el diálogo de contraseña; el texto del login pasa a **"Rellena usuario y contraseña."** (el del JavaFX) | Mismo control en dos sitios; corrige una diferencia no aceptada del sub-proyecto 0 |

## 4. Servidor (rama `feature/web-gestion`)

Todo es **aditivo**: ninguna respuesta que el JavaFX consuma cambia de forma; los 422 nuevos coinciden con lo que el cliente ya bloquea antes de llamar, y los códigos que cambian (400 → 422, 500 → 404/409) solo se daban por llamadas que el cliente no hace.

### 4.1 `UsuarioController` — alta (`POST /api/usuarios/tecnicos`)

Clase `ValidacionUsuarios` (package-private, patrón de `ValidacionPedidos`) con los textos como constantes. Los dos nombres se recortan (`trim`) antes de validar y **se guardan recortados**. Orden de comprobación, parando en la primera:

1. `nombreTecnico`, `nombreUsuario` o `password` nulos o vacíos tras el trim → **422 "Todos los campos son obligatorios."**
2. `password.length() < 6` → **422 "La contraseña debe tener al menos 6 caracteres."**
3. `nombreUsuario` de más de 50 caracteres → **422 "El nombre de usuario no puede superar 50 caracteres."**
4. `nombreTecnico` de más de 100 caracteres → **422 "El nombre del técnico no puede superar 100 caracteres."**
5. `rol` distinto de `TECNICO` y `SUPERTECNICO` → **422 "Rol no permitido."** (`rol` nulo sigue valiendo `TECNICO`; el `IllegalArgumentException` del DAO deja de traducirse a 400 en texto plano)
6. Duplicados como hoy: **409** "Ya existe un técnico con ese nombre." y **409** "Ese nombre de usuario ya existe." (con `LOWER(TRIM())`), incluido el 409 de la excepción de integridad.

Un 422 nunca escribe ni registra log. Éxito: 201 y log `CREAR_USUARIO` como hoy.

### 4.2 `UsuarioController` — activar, desactivar, comprobar y eliminar

- `PATCH …/{idTec}/activar`, `…/desactivar`, `GET …/{idTec}/tiene-reparaciones` y `DELETE …/{idTec}`: si `idTec` no existe → **404 `{message: "Técnico no encontrado."}`** (hoy 500 en las tres primeras y `{value:false}` en la cuarta).
- `GET …/{idTec}/tiene-reparaciones` devuelve `{value: true}` si el técnico o su usuario aparecen en **cualquiera** de: `Reparacion.ID_TEC`, `Reparacion.ID_TEC_ASIGNA`, `Reparacion.ENTREGADO_POR`, `Revision.EST_ID_USU`, `Revision.FUN_ID_USU`, `Envio.ID_USU`, `Envio_Telefono.ID_USU_DEVOLUCION`, `Solicitud_Stock.ID_USU`, `Movimiento_telefono.ID_USU` (`UsuarioDAO.tieneReferencias(idTec)`, que resuelve el `idUsu` del técnico).
- `DELETE …/{idTec}?idUsu=`: el servidor resuelve `idUsu` desde `idTec` (`UsuarioDAO.getIdUsuByIdTec`) e ignora el parámetro (se mantiene en el contrato como opcional para no romper al JavaFX); si `tieneReferencias` → **409 `"\"{nombreTecnico}\" tiene reparaciones asociadas."`** sin borrar nada; si no, borra como hoy (`Log_Actividad` del usuario, `Usuario`, `Tecnico`, en una transacción) y registra `ELIMINAR_USUARIO`. Una violación de integridad que escape a la comprobación sigue deshaciendo la transacción.
- Excluir/incluir de estadísticas: sin cambios.

### 4.3 `TecnicoController`

`POST /api/tecnicos` y `DELETE /api/tecnicos/{idTec}` pasan a `@PreAuthorize("hasRole('ADMIN')")` (sin consumidor en el JavaFX 0.16.x ni en la web). El resto no cambia.

### 4.4 `AuthController` — `PATCH /api/auth/cambiar-password`

En orden: `passwordActual` o `passwordNueva` nulas o vacías → **422 "Rellena todos los campos."**; `passwordNueva.length() < 6` → **422 "La contraseña debe tener al menos 6 caracteres."** (sustituye al 400 sin cuerpo); después el 422 "Contraseña actual incorrecta." de hoy. Éxito 204 y log `CAMBIAR_PASSWORD` como hoy. El texto técnico de BCrypt para `passwordActual` nula deja de poder aparecer.

### 4.5 `LogController` y `LogDAO`

- `GET /api/logs` gana `limite` opcional (`Integer`). Sin él, comportamiento actual (todo). Con él: `1 ≤ limite ≤ 5000` → `LIMIT ?`; fuera de rango → **422 "Límite no válido (debe estar entre 1 y 5000)."**. `ORDER BY l.FECHA DESC, l.ID_LOG DESC` (desempate estable, también sin `limite`).
- Filtro de fechas (G5): `desde` → `FECHA >= inicioDelDiaEnUtc(desde)`; `hasta` → `FECHA < inicioDelDiaEnUtc(hasta + 1 día)`; los límites se calculan con `Europe/Madrid` y se convierten a UTC en Java (mismo criterio que `UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid`); desaparece `DATE(l.FECHA)`. Con `desde > hasta` sigue devolviendo vacío.
- `GET /api/logs/acciones` (`hasRole('ADMIN')`) → `List<String>` con `SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION`.

### 4.6 Contrato

`@Schema(nullable = true)` en `detalle` y `motivo` de `LogActividad`; respuestas 201/404/409/422 documentadas donde procede; `idUsu` del `DELETE` como `required = false`; ruta nueva `/api/logs/acciones` y parámetro `limite` en `OpenApiContractTest`; `schema.d.ts` regenerado por el flujo offline.

### 4.7 Lo que ya existe y se reutiliza

`GET /api/usuarios/tecnicos` (ADMIN), los dos `PATCH` de activar/desactivar, `GET /api/logs` con `accion`, `tecnico`, `desde`, `hasta` (ADMIN), `PATCH /api/auth/cambiar-password` (cualquier autenticado, sobre su propio usuario), `LogDAO.insertar`, `UsuarioDAO.existeNombreTecnico/existeNombreUsuario/getNombreByIdTec`, `ResponseStatusException` con `{message}` (mecanismo de 4a/4b). Cada uno se verifica contra el contrato antes de planificarlo.

## 5. Web

```
app/router.tsx                          /gestion/tecnicos y /gestion/logs bajo <RequiereAdmin />; /cuenta/cambiar-password eliminado
app/shell/UserMenu.tsx (+test)          "Cambiar contraseña" abre <CambiarPasswordDialog /> (estado local, sin navegar);
                                        "Gestionar técnicos"/"Ver logs" navegan con state { volverA: pathname }
app/login/LoginPage.tsx (+test)         usa CampoPassword; "Rellena usuario y contraseña."
shared/ui/CampoPassword.tsx (+test)     input + botón ojo (ojo_activar/ojo_desactivar), aria-label "Mostrar/Ocultar contraseña"
shared/lib/fechas.ts (+test)            patrón 'dd/MM/yyyy HH:mm:ss' (segundos) y FMT_FECHA_LOG
shared/api/client.ts                    alias Usuario, LogActividad
shared/styles/tokens.css                badge-usuario-activo-bg #D4EDDA / -text #2E7D32; badge-usuario-inactivo-bg #F5E6E6 / -text #B03040;
                                        error-password #CC0000; etiqueta-password #555555
public/Lock.png, Unlock.png             copiados del cliente (resources/images)
modules/gestion/
├── rutas.tsx (+test)                   RequiereAdmin (patrón de taller/rutas.tsx: aviso MSG_SIN_PERMISOS y vuelta a /reparaciones)
├── api.ts (+test)                      useUsuariosTecnicos (['usuarios','tecnicos']); lo usan técnicos y el filtro de logs
├── tecnicos/
│   ├── TecnicosPage.tsx (+test)        cabecera, formulario de alta, tabla, pie "Cerrar"
│   ├── api.ts (+test)                  useRegistrar, useActivar, useDesactivar, useTieneReparaciones (lectura bajo demanda), useEliminar; invalidan ['usuarios'] y ['tecnicos']
│   ├── validacion.ts (+test)           validarAlta (orden y textos del JavaFX), duplicadosEnVivo(lista, tecnico, usuario)
│   ├── columnas.tsx (+test)            Técnico, Usuario, Rol, Estado, acciones (candado + papelera)
│   ├── BadgeEstadoUsuario.tsx          "Activo"/"Inactivo" con los tokens de arriba
│   └── textos.ts                       textos fijos de error y de los diálogos
├── logs/
│   ├── LogsPage.tsx (+test)            cabecera, barra de filtros, aviso de tope, tabla, pie "Actualizar"/"Cerrar"
│   ├── api.ts (+test)                  useLogs(filtros) → GET /api/logs?limite=1000&…; useAccionesLog → GET /api/logs/acciones
│   ├── filtros.ts (+test)              coincideTexto (los 9 casos del JavaFX), queryLogs (omite vacíos), FILTROS_LOGS_VACIOS, LIMITE_LOGS = 1000, MSG_TOPE
│   └── columnas.tsx (+test)            Fecha, Usuario, Acción, Detalle; textoDetalle(log) = detalle + "\n\nMOTIVO: " + motivo
└── cuenta/
    ├── CambiarPasswordDialog.tsx (+test)
    ├── validacion.ts (+test)           validarCambioPassword(actual, nueva, confirmar)
    └── api.ts (+test)                  useCambiarPassword → PATCH /api/auth/cambiar-password
modules/taller/asignaciones/csv.ts (+test)   CABECERAS_ASIGNACIONES (14) y filaAsignacionCsv; AsignacionesPage registra el exportable
docs/paridad/tecnicos.md, logs.md, cuenta.md; líneas nuevas en shell.md y asignaciones.md
tests/e2e/gestion.spec.ts
```

Se reutilizan `DataTable` (`vacio`, `ordenacion={false}`; ya maneja el doble clic sobre la fila), `CampoAutocompletar`, `RangoFechas`, `ConfirmDialog`, `AlertaProvider` (`mostrarError`, `mostrarAviso`, `mostrarTexto`), `useErrorServidor`, `ComboNavy`, `Input`, `Button`, `useRegistrarExportable` + `descargarCsv` + `textoForzado`, `tipoDe`/`TIPO_TRABAJO`, `traducirModelo`, `textoCsvEntrega`, `formatear`, `esAdmin`, `MSG_SIN_PERMISOS`. Un módulo no importa de otro: `gestion` no importa de `taller` (la clave `['tecnicos']` se invalida por literal, como se hizo con `['notificaciones']` en 4b); `UserMenu` (en `app`) sí importa `CambiarPasswordDialog` de `modules/gestion/cuenta`. Antes de cada tarea se busca en el repo lo que va a crear.

**Datos al abrir:** técnicos → `['usuarios','tecnicos']`; logs → `['logs', filtrosDeServidor]` (acción, usuario, desde, hasta, con `limite=1000`), `['logs','acciones']` y `['usuarios','tecnicos']`; contraseña → nada.

## 6. Comportamiento

Los inventarios del código de Gestión (fuera del repo) son la referencia de detalle. Aquí va lo que define cada pantalla.

### 6.1 Gestionar técnicos (`/gestion/tecnicos`, solo ADMIN)

**Cabecera:** logo `logo_inicio_sesion.png` 46 px + **"Gestión de usuarios"** (18 px negrita azul medio) y **"Registra o elimina accesos al sistema"** (12 px azul gris). Fondo de página `#EFEFEF` como el JavaFX (el token `--color-fondo-login` ya lo tiene; se renombra o se añade `--color-fondo-gestion` con el mismo valor, a decidir en el plan), contenido con padding lateral 48 px.

**Formulario de alta** (fila de cuatro campos a partes iguales, etiqueta 11 px negrita azul gris encima, campo blanco con borde `#D4D8DE` radio 8 y padding 10/12): **"Nombre del técnico"** (placeholder **"Nombre visible en reparaciones"**), **"Nombre de usuario"** (**"Credencial de login"**), **"Contraseña"** (**"Contraseña"**, tipo password sin ojo), **"Confirmar"** (**"Repite la contraseña"**). Bajo los dos primeros, el error en vivo de duplicado (10 px rojo `texto-error`): **"Ya existe un técnico con ese nombre."** / **"Ese nombre de usuario ya existe."**, calculado contra la lista cargada sin mayúsculas y con trim, y que además deshabilita "Registrar técnico" mientras haya alguno (calco: la lista no incluye ADMIN, así que "admin" pasa en vivo y lo frena el 409). Debajo, la fila de acción: línea de error inline (11 px rojo, ocupa su hueco aunque esté vacía) · `ComboNavy` de 130 px con **"TECNICO"** / **"SUPERTECNICO"** (TECNICO por defecto) · botón navy **"Registrar técnico"** (píldora radio 24). Enter no registra (calco). Separador `#D4D8DE`.

**Validación del alta** (en la línea inline, parando en el primer fallo, mismo orden que el JavaFX): nombre del técnico, usuario o contraseña vacíos (nombres con trim) → **"Todos los campos son obligatorios."**; contraseña ≠ confirmación → **"Las contraseñas no coinciden."**; menos de 6 caracteres → **"La contraseña debe tener al menos 6 caracteres."**. Guardado: `POST /api/usuarios/tecnicos` con `{nombreTecnico, nombreUsuario, password, rol}` (nombres con trim). Éxito → vacía los cuatro campos, combo a TECNICO, línea de error vacía y recarga la tabla; **sin mensaje de éxito** (calco). 409/422 → su `message` en la línea inline; otro error → **"Error al registrar. Inténtalo de nuevo."**.

**"Técnicos registrados"** (13 px negrita) + `DataTable` con **Técnico** (160), **Usuario** (130), **Rol** (110; `TECNICO`/`SUPERTECNICO` tal cual), **Estado** (90; `BadgeEstadoUsuario` **"Activo"** `#2E7D32` sobre `#D4EDDA` / **"Inactivo"** `#B03040` sobre `#F5E6E6`, radio 10, padding 3/10, 11 px negrita) y una columna de acciones sin cabecera que absorbe el resto: botón candado (`Unlock.png` 18 px si activo con tooltip **"Desactivar acceso"**; `Lock.png` si inactivo con tooltip **"Activar acceso"**) y papelera `borrar.png` 22 px **en todas las filas** (calco), sin tooltip. Orden: el del servidor (nombre de técnico). Sin ordenación por cabecera, sin filtros, sin poller, sin "Actualizado". Filas de 35 px; seleccionada navy con texto claro (badge e iconos sin cambiar). Placeholder de tabla vacía: el texto por defecto del JavaFX, a fijar con la captura (previsto **"No hay contenido en la tabla"**).

**Activar / desactivar:** clic en el candado, sin confirmación → `PATCH …/desactivar` o `…/activar` → recarga. Error → **"Error al cambiar el estado del técnico."** en la línea inline, o el `message` del servidor si es 404.

**Eliminar:** clic en la papelera → `GET …/tiene-reparaciones`. Si `true` → `mostrarAviso` con título **"No se puede eliminar"** y texto **`"\"{nombreTecnico}\" tiene reparaciones asociadas."`** + salto + **"No es posible eliminarlo para conservar el historial."** + salto + **"Puedes desactivarlo para bloquear su acceso."**. Si `false` → `ConfirmDialog` título **"Eliminar técnico"**, descripción **`"¿Eliminar a \"{nombreTecnico}\" definitivamente?"`** + salto + **"Se borrarán sus credenciales de acceso y su registro de técnico."**, botón **"Eliminar"** (y "Cancelar") → `DELETE …/{idTec}?idUsu={idUsu}` → recarga. Error del `GET` → **"Error al comprobar las reparaciones del técnico."**; error del `DELETE` → el `message` si es 404 o 409 (la carrera entre la comprobación y el borrado), si no **"Error al eliminar el técnico."**.

**Línea inline:** se vacía al empezar cualquier acción (registrar, candado, papelera) y muestra el error de carga inicial como **"Error al cargar los usuarios."** (la tabla queda vacía). Toda escritura con éxito invalida `['usuarios']` y `['tecnicos']` (los combos y listas de técnicos del resto de la web ven altas y bajas; equivale a la recarga de la vista al cerrar el modal del JavaFX).

**Pie:** **"Cerrar"** (estilo enlace, 12 px azul gris) → navega a `state.volverA` (la ruta desde la que el menú abrió la página) o a `/reparaciones` si no hay. Cierra sin preguntar aunque haya texto (calco).

### 6.2 Ver logs (`/gestion/logs`, solo ADMIN)

**Cabecera:** logo 46 px + **"Log de actividad"** / **"Registro de acciones realizadas en el sistema"**; separador.

**Barra de filtros** (`flex-wrap`, estilo estándar de las barras de la web, G10), en este orden: buscador con placeholder **"Buscar..."** (220 px; en memoria, `contains` sin mayúsculas tras trim sobre usuario, acción y detalle; no mira el motivo ni la fecha; se conserva al recargar), **"Acción..."** (`CampoAutocompletar` de 150 px sobre `['logs','acciones']`; elegir filtra en servidor por igualdad; borrar el texto quita el filtro), **"Técnico..."** (`CampoAutocompletar` de 150 px con los `nombreUsuario` de `GET /api/usuarios/tecnicos` en orden natural de cadena, activos e inactivos; sin ADMIN, calco), **"Desde:"** / **"Hasta:"** (`RangoFechas`), **"Limpiar filtros"** (vacía los cinco y provoca una sola recarga). Con `desde > hasta` la tabla queda vacía sin aviso (calco).

**Aviso de tope:** cuando la respuesta trae exactamente `LIMITE_LOGS` filas, una línea bajo la barra: **"Mostrando los 1.000 registros más recientes; acota con los filtros."**.

**Tabla:** **Fecha** (150; `dd/MM/yyyy HH:mm:ss` en Madrid), **Usuario** (80; nombre de login), **Acción** (180; el código tal cual), **Detalle** (absorbe el resto; una línea con elipsis). Orden del servidor (fecha desc, id desc). Sin ordenación por cabecera, sin colores por acción, sin menú contextual. Placeholder vacío: el mismo texto por defecto que en 6.1. **Doble clic** en una fila → `mostrarTexto("Detalle del log", texto)` con `texto = detalle ?? ""` y, si hay motivo no vacío, `+ "\n\nMOTIVO: " + motivo` (el popup ya existe: título, texto de solo lectura y "Copiar").

**Pie:** **"Actualizar"** (navy, `btn-primary`) recarga con los filtros actuales; **"Cerrar"** como en 6.1.

**Errores:** un fallo al cargar (inicial, "Actualizar" o cambio de filtro) → `mostrarError("Error al cargar los logs: " + mensaje)` y la tabla conserva lo que tenía, salvo 401 y conexión, que siguen la política global (banner y login). Fallo al cargar la lista de acciones o de usuarios: silencioso, el autocompletar queda vacío (calco).

Sin poller, sin "Actualizado HH:mm", sin CSV. Los filtros no sobreviven a salir de la página (calco: al reabrir, vacíos).

### 6.3 Cambiar contraseña (diálogo, los tres roles)

Diálogo de **380 px** sin el marco de `DialogoAlmacen`: barra superior navy `#001232` con **"Cambiar contraseña"** (15 px negrita blanco, padding 16/20) y cuerpo blanco (padding 24, separación 16) con tres `CampoPassword` con etiqueta 11 px negrita `#555`: **"Contraseña actual"** (placeholder **"Contraseña actual"**), **"Nueva contraseña"** (**"Nueva contraseña"**), **"Confirmar nueva contraseña"** (placeholder **"Confirmar contraseña"**, calco). Cada campo tiene su ojo independiente (`ojo_activar` / `ojo_desactivar`, 18 px). Línea de error `#CC0000` 12 px oculta hasta que hay error. Botones a la derecha: **"Cancelar"** (blanco, borde `#C2C8D0`, radio 6) y **"Guardar"** (navy, negrita, radio 6). Enter guarda y Esc cierra (§10). Foco inicial en la actual.

**Validación** (línea de error, parando en el primer fallo, sin trim): alguno vacío → **"Rellena todos los campos."**; nueva de menos de 6 → **"La contraseña debe tener al menos 6 caracteres."**; nueva ≠ confirmar → **"Las contraseñas nuevas no coinciden."**. Guardado: `PATCH /api/auth/cambiar-password` con `{passwordActual, passwordNueva}`; "Guardar" deshabilitado mientras responde. Éxito → cierra el diálogo y `mostrarAviso("Información", "Contraseña cambiada correctamente.")` (título a confirmar con la captura); la sesión sigue igual. 422 → su `message` en la línea (los campos no se vacían); 401 → flujo global de sesión caducada; otro error → el mensaje de la web para ese error. "Cancelar" cierra sin preguntar.

### 6.4 Menú de usuario y login

`UserMenu` ya calca los cinco ítems, sus roles y separadores; solo cambia que "Cambiar contraseña" abre el diálogo en el sitio y que los dos ítems de ADMIN pasan `volverA`. **"Descargar CSV"** queda deshabilitado en las tres pantallas (no registran exportable). `LoginPage` usa `CampoPassword` y dice **"Rellena usuario y contraseña."** con los campos vacíos.

### 6.5 CSV de Asignaciones (G7)

`AsignacionesPage` registra un exportable que descarga `reparaciones_pendientes` con las filas **visibles tras los filtros** y estas 14 cabeceras exactas: **"ID", "Tipo", "Técnico", "IMEI", "Modelo", "Fecha asignación", "Comentario", "Cliente", "Asignado por", "Urgente", "Chasis", "Por cerrar", "Entregado", "En espera de pieza"**. Valores por fila: `idRep`; etiqueta del tipo (`Reparación`/`Glass`/`Pulido` por `tipoDe`); `nombreTecnico ?? ''`; `textoForzado(imei)`; `traducirModelo(modelo)` o `''`; `fechaAsig` como `dd/MM/yyyy HH:mm`; `comentarioAsignacion ?? ''`; `cliente ?? ''`; `nombreTecnicoAsigna ?? '—'`; `urgente`, `esChasis` y `porCerrar` como **"Sí"/"No"**; `textoCsvEntrega(rep)`; en espera de pieza = `esSolicitud > 0 && !(estadoSolicitud === 'GESTIONADA' && stockSolicitud > 0)` como "Sí"/"No". Para SUPERTECNICO y ADMIN (los que ven la vista).

**Roles.** `/gestion/tecnicos` y `/gestion/logs`: solo ADMIN, con `RequiereAdmin` (los demás reciben el aviso genérico de permisos y vuelven a `/reparaciones`). El diálogo de contraseña: los tres roles. El CSV de Asignaciones: quien ve la vista.

## 7. Guardado y recargas

Técnicos: cada escritura es una mutación que, al terminar con éxito, invalida `['usuarios']` y `['tecnicos']`; con error, solo pinta la línea inline. Logs: solo lecturas; "Actualizar" es `refetch`; cambiar un filtro de servidor cambia la clave y dispara una carga; "Limpiar filtros" cambia la clave una sola vez. Contraseña: una mutación sin invalidaciones. Ninguna de las tres pantallas sondea ni recarga al volver a la pestaña (calco: el JavaFX tampoco), y no hay nada que congelar.

## 8. Errores y refresco

- **404/409/422 en técnicos:** el `message` del servidor en la línea inline (§6.1); el resto, los textos fijos del JavaFX.
- **422 en contraseña:** inline; la web valida antes con los mismos textos.
- **Fallo de carga de logs:** "Error al cargar los logs: …" salvo 401 y conexión (política general).
- **403 por URL directa:** `RequiereAdmin` lo evita antes de llamar; si llegara un 403 del servidor, política general.
- Sin refresco automático en las tres pantallas.

## 9. Tests y verificación

- **Servidor:** `UsuarioControllerTest` (matriz de los cinco 422 en orden, trim guardado, los dos 409, 201 con log; 404 de activar/desactivar/tiene-reparaciones/eliminar; 409 del `DELETE` con referencias sin borrar; `idUsu` resuelto e ignorado), `UsuarioDAOReferenciasTest` (cada una de las nueve referencias hace `true`; ninguna, `false`), `RolesUsuarioTecnicoTest` (403 de `POST/DELETE /api/tecnicos` para TECNICO y SUPERTECNICO), `AuthControllerCambiarPasswordTest` (204 con log, 422 vacíos, 422 corta, 422 actual incorrecta), `LogDAOFiltroTest` (`limite`, límites de día en Madrid convertidos a UTC, orden con desempate), `LogControllerTest` (`limite` fuera de rango → 422; `/acciones`), contrato en `OpenApiContractTest`. Suite completa con `mvn -q test`.
- **Web:** `validacion.test` de técnicos (orden y textos), `duplicadosEnVivo`, `TecnicosPage.test` (solo ADMIN, formulario, alta con limpieza y recarga, 409/422 inline, candado con ambos tooltips, aviso "No se puede eliminar", confirmación "Eliminar técnico", 404/409 del borrado inline, textos fijos, invalidaciones, "Cerrar" con `volverA`), `filtros.test` de logs (los 9 casos de `coincideTexto` portados del cliente, `queryLogs`), `LogsPage.test` (filtros de servidor y de memoria, aviso de tope, doble clic → `mostrarTexto` con y sin motivo, "Actualizar", "Limpiar filtros" con una sola carga, error conservando datos), `validacion.test` de cuenta, `CambiarPasswordDialog.test` (validación, ojo por campo, envío, éxito con aviso y cierre, 422 inline, botón deshabilitado), `UserMenu`/`TopBar.test` (el diálogo se abre sin navegar; `volverA`), `rutas.test` de gestión (`RequiereAdmin`), `CampoPassword.test`, `LoginPage.test` (texto nuevo), `csv.test` de asignaciones (14 cabeceras exactas, "Sí"/"No", "—", IMEI forzado, tipo, en espera de pieza) y `AsignacionesPage.test` (exportable registrado).
- **Trazabilidad:** el plan enlaza cada regla de los tres inventarios con el test que la cubre.
- **Smoke** Playwright `gestion.spec.ts` contra producción, en serie, solo con OK del usuario y tras desplegar el servidor, con las credenciales ADMIN de `~/.env.e2e`: registrar `e2e-tecnico-<marca>` (rol TECNICO) desde la página, desactivar, activar, comprobar en `/gestion/logs` un `CREAR_USUARIO` filtrando por acción y por usuario, iniciar sesión con el usuario de prueba y cambiar su contraseña por el diálogo (y volver a la original), y borrarlo desde la página. Lo que quede por fallo se anota para la limpieza.
- **Capturas del JavaFX:** antes de la comparación, el usuario toma las de la §18 de los tres inventarios con el worktree `_ref-hotfix-0163` recreado y los scripts de `Apuntes/herramientas/paridad-capturas/`, con un usuario de prueba creado para ello (nunca uno real), anotando en `CAPTURAS-6.md` las no reproducibles.
- **Paridad antes del tag:** fichas `docs/paridad/{tecnicos,logs,cuenta}.md` marcadas contra las capturas (citadas solo por nombre) y **capturas de la web comparadas lado a lado antes de `v0.8.0`** con `capturas-gestion.mjs` (modelo `capturas-pedidos.mjs`, que bloquea las escrituras salvo las del flujo de prueba).

## 10. Diferencias y calcos

| Asunto | Decisión |
|---|---|
| "Gestionar técnicos" y "Ver logs" como páginas del shell (el JavaFX: ventana modal y ventana independiente) | **Diferencia** (G2); la ventana de logs no modal se sustituye por abrir otra pestaña del navegador |
| "Cerrar" vuelve a la vista desde la que se abrió | **Diferencia** (equivale a cerrar la ventana) |
| Cambiar contraseña como diálogo en el sitio; `/cuenta/cambiar-password` desaparece | **Calco** (G6) |
| Validaciones y 404/409/422 en el servidor | **Diferencia** (G1, G8); el JavaFX no las ve |
| `Alert` nativos → `mostrarAviso` / `ConfirmDialog` con botón "Eliminar" / `mostrarError` | **Diferencia** (G9) |
| Línea de error inline que se limpia al empezar cada acción y muestra el `message` en 404/409/422 | **Diferencia** (G9; el JavaFX deja el último error hasta un alta correcta y usa textos fijos) |
| Log limitado a 1.000 filas con aviso | **Diferencia** (G3) |
| Lista de acciones real y alfabética (el JavaFX: 56 códigos agrupados a mano) | **Diferencia** (G4) |
| Filtro de fechas del log en hora de Madrid | **Diferencia** (G5); también para el JavaFX, a mejor |
| Barra de filtros con salto de línea y estilo estándar (el JavaFX no carga `app.css` en esa ventana) | **Diferencia** |
| "Acción..." y "Técnico..." como `CampoAutocompletar` (teclado y Enter) | **Diferencia** (G10) |
| Sin ordenación por cabecera en las dos tablas | **Diferencia** (G10) |
| Enter guarda y Esc cierra en el diálogo de contraseña | **Diferencia** inocua |
| Sin título de ventana; "Hola, {nombre canónico}"; menú alineado a la derecha | **Diferencia** ya aceptada en el sub-proyecto 0 |
| Texto del login "Rellena usuario y contraseña." | **Calco** (G11; corrige al sub-proyecto 0) |
| Tres nombres para la misma pantalla ("Gestión de usuarios" en la cabecera, "Gestionar técnicos" en el menú; el título de ventana "Gestión de técnicos" no existe) | **Calco** |
| "Registrar técnico" aunque el rol elegido sea SUPERTECNICO; roles en mayúsculas; sin ADMIN en el combo | **Calco** |
| Papelera en todas las filas; sin mensaje de éxito al registrar; error en vivo de duplicados contra la lista cargada (sin ADMIN) | **Calco** |
| Placeholder "Confirmar contraseña" distinto de su etiqueta; confirmación vacía cae en "no coinciden"; sin trim en contraseñas | **Calco** |
| "Descargar CSV" deshabilitado en las tres pantallas; sin CSV de logs | **Calco** |
| Buscador de logs sin motivo ni fecha; motivo solo por doble clic; "Técnico..." sin ADMIN; filtros vacíos al reabrir | **Calco** |
| El log de actividad del usuario eliminado se borra con él | **Calco** (G8), anotado en la ficha |
| CSV de Asignaciones con 14 columnas | **Calco** (G7) |

Cualquier diferencia nueva que aparezca al comparar capturas se decide con el usuario, no sobre la marcha.

## 11. Criterios de cierre

1. `/gestion/tecnicos`, `/gestion/logs` y el diálogo de contraseña se comportan como las fichas; `RequiereAdmin` protege las dos rutas; `/cuenta/cambiar-password` ya no existe.
2. El servidor aplica los 422 del alta y de la contraseña con los textos del cliente, el 409 y el 404 del borrado, la guarda de `/api/tecnicos`, el `limite`, el filtro de fechas en Madrid y `/api/logs/acciones`.
3. "Descargar CSV" en Asignaciones descarga `reparaciones_pendientes` con las 14 columnas.
4. Suites en verde en los tres repos (el cliente JavaFX sin tocar) y smoke en verde.
5. Fichas marcadas y capturas comparadas lado a lado antes del tag.
6. Desplegado en la VDC, servidor antes que web, con el contrato publicado idéntico al que consume la web. Push, merge, tag `v0.8.0` y despliegue, cada uno con el OK del usuario.
7. Limpieza pendiente de la BD de pruebas (4a y 4b) ejecutada en la Task de arranque, y la de este sub-proyecto anotada.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| La conversión de los límites de día a UTC no coincide con la zona de la sesión JDBC en producción | Test de `LogDAO` con instantes de madrugada en verano e invierno; el smoke filtra por el día de hoy y comprueba que sale el `CREAR_USUARIO` recién creado |
| El 409 del `DELETE` con referencias bloquea borrados que el JavaFX permitía | Antes también fallaban (500 por integridad); ahora con texto; anotado en la ficha |
| Un usuario de prueba del smoke queda a medias (creado y no borrado) | El smoke registra el nombre antes de cada paso y la limpieza lo lista; el nombre lleva marca `e2e-` |
| El placeholder de tabla vacía y el título del aviso de éxito dependen de la JVM del JavaFX | Se fijan con la captura antes de la comparación; hasta entonces, los previstos en §6 |
| `CampoPassword` cambia el login | Test del login existente más el nuevo; la comparación de capturas incluye el login |
