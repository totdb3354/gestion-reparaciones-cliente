# Sub-proyecto 1 — Taller técnico (Pendientes, Historial e IMEIs)

**Fecha:** 2026-09-16
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; las decisiones generales están allí y no se repiten aquí). Antecesor: [Cimientos](2026-09-13-web-cimientos-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-taller`), repo raíz (gitlinks, plan y specs; se queda en `main`)

## 1. Objetivo

Migrar a la web las tres vistas que usan a diario los técnicos, con paridad
verificada contra lo que la tienda tiene instalado hoy (línea hotfix 0.16.2,
idéntica a `hotfix/0.16.3` en estas vistas):

- **Pendientes**: "Mis asignaciones pendientes" en sus tres pestañas (Reparaciones, Glass, Pulidos), con sus acciones (por cerrar, entrega a glass y llegada, completar pulidos por lote, papelera del supertécnico).
- **Historial**: reparaciones, glass y pulidos completados, con filtros, menú de edición del supertécnico y CSV.
- **IMEIs**: el "Agrupado por IMEI" (maestro por teléfono con detalle cronológico de los tres tipos de trabajo).

Y, en el servidor, la primera pieza del bloque de seguridad del programa: el
parámetro `?tecnico=` verificado contra el token, más los contadores de
pendientes. Al cerrar, la web queda lista para el formulario de reparación
(sub-proyecto 2), que es lo único que estas vistas aún no pueden abrir.

## 2. Fuera de alcance

- Formulario de reparación (completar, editar, borrador) y campana de notificaciones: sub-proyecto 2. Los botones y menús que lo abren existen pero están deshabilitados (§9).
- Apartado Asignaciones del supertécnico y sus cálculos (carga, predicción de glass, dedup): sub-proyecto 3. En la columna lateral aparece como "Pendiente de migrar".
- Inventario de teléfonos, lotes, estados, ubicaciones y envíos (vista INVENTARIO de `main`): sub-proyecto 4.
- Navegación desde Estadísticas a Historial/IMEIs con filtros precargados: sub-proyecto 5.
- Retoques menores de UI apuntados por el usuario: se acumulan en `Apuntes/plan-futuro.md` §9 y se harán en bloque más adelante.
- Red, rate limiting y hardening: sub-proyecto 7.
- Deuda menor de Cimientos que no bloquea (§13).

## 3. Decisiones de este sub-proyecto

| Tema | Decisión | Motivo |
|---|---|---|
| Referencia de las vistas | La línea hotfix 0.16.2 (= `hotfix/0.16.3`), que es lo que ven los trabajadores; capturas en `Apuntes/paridad-capturas/taller/` (52, tres roles) | `main` del cliente añade la Fase 2 y le falta la línea hotfix; lo que hay que calcar es la pantalla de la tienda |
| IMEIs | Calco del hotfix **sin la columna Revisión**: el maestro se agrupa en la web a partir de los tres historiales ya cacheados (`GrupoImei` portado a TypeScript) | La vista TALLER de `main` cambia lo que se ve ("Última actividad", "N abiertos", contadores globales para el técnico); el servidor de producción ya no envía `revisionLogistica`, así que el toggle está muerto hoy; el inventario del sub-proyecto 4 será otra pantalla |
| Historial de pulidos | Se incluye ahora (tabla, filtros, "Editar modelo" y "Borrar" con motivo, CSV) aunque la spec maestra lo tenía en el 6 | Es el tercer toggle del Historial; dejarlo vacío rompería la pantalla, y el catálogo de modelos hace falta igualmente para la columna Modelo |
| Acciones que abren el formulario | "Añadir reparación", "Añadir glass" y "Editar" visibles pero deshabilitados con tooltip hasta el sub-proyecto 2 | Calco exacto de la pantalla sin nada roto que pulsar |
| Entrada por rol | TECNICO en Pendientes; ADMIN en Historial; SUPERTECNICO en Historial mientras Asignaciones no exista (en el 3 pasa a Asignaciones) | Aterrizar en una página vacía o en unos pendientes casi siempre a cero no ayuda |
| Estructura del módulo | Una vista por apartado con rutas propias (`modules/taller/{pendientes,historial,imeis}`); toggles y detalle como rutas | URLs enlazables, botón atrás coherente, ficheros pequeños; ni el `StackPane` gigante ni una página por rol |
| Contadores de pendientes | Endpoint del servidor | Lo asignaba la spec maestra; alimenta el badge lateral y los sufijos de los toggles sin descargar tres listas |
| Volumen de datos | Virtualización de filas en `DataTable` | El historial del supertécnico son ~8.000 filas y el maestro ~4.000; el `TableView` virtualiza y la web debe hacerlo |

## 4. Rutas, navegación y refresco

**4.1 Rutas** (todas bajo `RequireSesion` y `AppLayout`):

| Ruta | Vista | Roles |
|---|---|---|
| `/reparaciones` | redirige según rol: TECNICO → `/reparaciones/pendientes`; SUPERTECNICO y ADMIN → `/reparaciones/historial` | los tres |
| `/reparaciones/asignaciones` | `PendienteDeMigrar` ("Asignaciones") | SUPERTECNICO, ADMIN |
| `/reparaciones/pendientes`, `/pendientes/glass`, `/pendientes/pulidos` | Pendientes (rep, glass, pulidos) | TECNICO, SUPERTECNICO (exige `idTec` en sesión; ADMIN no tiene la entrada y la ruta le redirige a Historial) |
| `/reparaciones/historial`, `/historial/glass`, `/historial/pulidos` | Historial (rep, glass, pulidos) | los tres |
| `/reparaciones/imeis` | IMEIs, maestro | los tres |
| `/reparaciones/imeis/:imei` | IMEIs, detalle de un teléfono | los tres |

**4.2 Columna lateral** (`SUBNAV.reparaciones`, en el orden del JavaFX): TECNICO Pendientes · Historial · IMEIs; SUPERTECNICO Asignaciones · Pendientes · Historial · IMEIs; ADMIN Asignaciones · Historial · IMEIs. "Pendientes" lleva el badge con la suma rep + glass + pulidos (tope "99+", oculto si es cero), calcado de `sidebar-badge`. Pulsar "IMEIs" estando en el detalle vuelve al maestro (como hoy). El logo de la barra superior lleva a `/reparaciones` (y de ahí al panel del rol).

**4.3 Toggles**: píldora segmentada calcada de `toggle-pill-left/mid/right` (izquierda redondeada, centro recto, derecha redondeada; activo navy con texto blanco). En Pendientes cada toggle lleva sufijo "(n)" siempre, incluido "(0)", tope "99+"; en Historial sin sufijo. Al cambiar de toggle se conserva el texto del filtro IMEI (los demás filtros son propios de cada tabla).

**4.4 Refresco**: hook `useIntervaloRefresco()` en `shared/api` que devuelve el `refetchInterval` (60 s conectado, 5 s con el banner activo; `false` si la vista no sondea) y, junto a él, documentado el contrato de errores de las consultas: diálogo solo en el primer fallo de una consulta sin datos (`errorUpdateCount === 1`), banner en el resto. Lo aplican Pendientes, Historial e IMEIs para TECNICO y SUPERTECNICO; ADMIN no sondea (solo al volver a la pestaña), como hoy. Cada tabla lleva abajo a la derecha "Actualizado HH:mm" (hora local del PC), clicable para recargar; si esa recarga manual falla por conexión se muestra el diálogo, porque la ha pedido el usuario (hoy la web la trataba como fondo). El apartado IMEIs no lleva la etiqueta (el JavaFX tampoco). El badge lateral usa el endpoint de contadores con el mismo intervalo y se invalida tras cada escritura de Pendientes.

## 5. Servidor (rama `feature/web-taller`; todo aditivo, el JavaFX 0.16.2 sigue funcionando)

**5.1 `?tecnico=` verificado contra el token.** Un helper único (`FiltroTecnico.efectivo(principal, tecnico)`) aplicado en `GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`, `/api/glass/asignaciones`, `/api/pulidos/historial` y `/api/pulidos/asignaciones`:

- Principal TECNICO: el filtro efectivo es siempre su `idTec`. Sin parámetro → lo suyo; parámetro igual al suyo → lo suyo; parámetro distinto → 403 "Solo puedes consultar tus propios trabajos".
- SUPERTECNICO y ADMIN: como hoy (sin parámetro → todo; con parámetro → ese técnico).

Efecto colateral: el JavaFX del técnico, que descarga el historial completo y filtra en cliente, pasa a recibir solo lo suyo sin cambiar.

**5.2 Contadores de pendientes.** `GET /api/reparaciones/pendientes/contadores` → `{ reparaciones, glass, pulidos }` (enteros): asignaciones abiertas (`A%`, `AG%`, `AP%` con `FECHA_FIN IS NULL`) del técnico efectivo según 5.1; ADMIN sin técnico → ceros. Modelo de respuesta tipado (`ContadoresPendientes`) para que el contrato lo publique con nombre. Tres `COUNT` en el DAO.

**5.3 Nullabilidad del contrato** (deuda de Cimientos). Jackson serializa todas las claves (inclusión por defecto), así que un `OpenApiCustomizer` marca como `required` todas las propiedades de todos los esquemas, y los campos que pueden llegar a `null` se anotan con `@Schema(nullable = true)` en los modelos que consume la web: `LoginResponse.idTec`; en `ReparacionResumen` `fechaFin`, `tipoComponente`, `observaciones`, `incidencia`, `idRepAnterior`, `descripcionSolicitud`, `estadoSolicitud`, `tipoSolicitud`, `tiposSolicitud`, `updatedAt`, `modelo`, `comentarioAsignacion`, `observacionTelefono`, `nombreTecnicoAsigna`, `telefonoUpdatedAt`, `cliente`, `entregadoAt`, `entregadoPorNombre`, `entregadoPor`, `glassEntregadoAt`, `glassEntregadoPorNombre`, `glassEntregadoPor`, `glassTecnicoNombre`, `normalTecnicoNombre`; en los cuerpos de petición opcionales (`motivo`, `comentario`, `idCli`; **no** `updatedAt`: los DAO lo desreferencian y todas las columnas `UPDATED_AT` son `NOT NULL`, así que en los cuerpos sigue siendo obligatorio y la web solo ofrece "Editar observación" / "Editar cliente" cuando la fila tiene `telefonoUpdatedAt`). `OpenApiContractTest` comprueba que todo es `required` y que `LoginResponse.idTec` y `ReparacionResumen.fechaFin` son `nullable`; se regenera `api/openapi.json` y `client.ts` pierde `Required<>` y el `Omit` de `idTec` (los tipos generados pasan a `T | null`).

**5.4 Tests.** JUnit de controller con principal simulado para 5.1 (las seis rutas × los tres roles, incluido el 403) y 5.2; los 140 actuales siguen en verde; el arranque del contexto lo cubre `OpenApiContractTest`.

**5.5 Sin tocar.** `PATCH .../por-cerrar`, `.../entrega-glass`, `PATCH`/`DELETE .../llegada`, `DELETE /asignaciones/{id}`, `DELETE /imei/{imei}/incidencia-activa`, `POST /{id}/incidencia`, `DELETE /reparacion-componentes/{id}/incidencia`, `DELETE /reparaciones/{id}` y `/pulidos/historial/{id}` con motivo, `POST /pulidos/asignaciones/completar-lote`, `DELETE /pulidos/asignaciones/{id}`, `PATCH /telefonos/{imei}/observacion` y `/cliente`, `POST /telefonos`, `GET /reparaciones/{id}/referenciadora`, `GET /tecnicos` y `/tecnicos/activos`, `GET /clientes/activos`: ya existen y ya protegen por rol o por propiedad; la web los consume tal cual. `PUT /telefonos/{imei}/revision-logistica` queda huérfano hasta que el sub-proyecto 4 lo retire.

## 6. Web: módulo `taller`

**6.1 Estructura.**

```
src/modules/taller/
  api.ts                 hooks de lectura y mutaciones del módulo (= ReparacionDAO, GlassDAO, PulidoDAO, TelefonoDAO, ReparacionComponenteDAO)
  lib/                   lógica pura portada del JavaFX, con tests (ver 6.2)
  pendientes/            PendientesPage (rep y glass, parametrizada por tipo), PulidosPendientesPage, tests
  historial/             HistorialPage (rep y glass), HistorialPulidosPage, tests
  imeis/                 ImeisPage (maestro), ImeiDetallePage (detalle), tests
  componentes/           piezas propias del módulo: BadgeEstadoPendiente, PildoraImei, MenuTipo, MenuIncidencias, DialogoIncidencia, DialogoObservacion, ...
```

Hooks de lectura: `useHistorial(tipo)` (`['historial', tipo]`), `useAsignaciones(tipo)` (`['asignaciones', tipo]`), `useContadoresPendientes()`, `useTecnicos()`, `useClientesActivos()`; IMEIs no tiene consulta propia: agrupa las tres de historial. Una mutación por acción, con invalidación de las claves afectadas (y de los contadores) en `onSettled`.

**6.2 Lógica pura portada** (`modules/taller/lib`, un test Vitest por cada JUnit del cliente):

| Módulo | Origen JavaFX | Contenido |
|---|---|---|
| `tipoTrabajo.ts` | `TipoTrabajo` | tipo por prefijo del ID (`A`/`R` reparación, `AG`/`G` glass, `AP`/`P` pulido), etiqueta y paleta |
| `entregaGlass.ts` | `EntregaGlass` (+ `EntregaGlassTest`) | badge "→ X" / "Llegó hh:mm" (fecha si no es hoy), tooltips, opción de menú "Entregar a X" / "Deshacer entrega" (firma), "Marcar que llegó" / "Deshacer llegada", píldoras "Glass: X" y "Rep: X", ocultar "Añadir glass", sub-etiqueta "Llegó dd/MM HH:mm" del historial, texto CSV |
| `filtroImei.ts` | `FiltroImei` (+ test) | canonicalizar (solo dígitos y comas, ", " tras 15, troceo de tokens largos), IMEIs válidos, estado vacío/incompleto/válido |
| `grupoImei.ts` | `GrupoImei` (+ test) | agrupación por IMEI de una lista mixta, contadores por tipo, "2 Rep · 1 Glass · 1 Pul", modelo/observación/cliente = primer valor no vacío, fecha más antigua (asignación) y más reciente (fin), incidencias abiertas, orden por actividad más reciente |
| `piezas.ts` | `Piezas` (+ test) | categoría por prefijo de SKU (Batería, Chasis, Glass, Cámara, Pantalla, Marco, Otros) |
| `modelos.ts` | `FormularioReparacionController.MODELOS_ORDENADOS` y `traducirModelo` | catálogo ordenado y traducción código → nombre; duplicado en TypeScript hasta que suba al servidor (§12) |
| `estadoPendiente.ts` | `PendientesTecnicoController` (celda Estado) | qué badges pinta cada fila: Urgente, Por cerrar, entrega, Incidencia / Solicitud / En camino / Recibido con "N piezas", Normal |
| `filtros.ts` | los tres controllers | predicados: tipo (solicitud / incidencia / asignación), incidencias (abiertas / cerradas / sin), fechas por fecha fin (sin fin → oculta si hay rango), técnico (OR en el maestro; en el detalle las de otros al final con opacidad 0,45 y contador "X de filtrados + Y de otros"), cliente con "(Sin cliente)", pieza por categoría; orden de Pendientes urgente → con cliente → normal |

**6.3 Compartidos nuevos en `shared/`** (los usarán los sub-proyectos siguientes):

- `lib/fechas.ts`: instantes UTC del servidor formateados en Europe/Madrid con `Intl` y los patrones del JavaFX (`yyyy/MM/dd HH:mm`, `yyyy/MM/dd`, `dd/MM HH:mm`, `dd/MM`, `HH:mm`, `dd/MM/yyyy`, `dd/MM/yyyy HH:mm` para CSV), `toLocalDate` para los filtros de fecha y la marca `yyyy-MM-dd_HH-mm` del nombre de fichero.
- `lib/csv.ts`: port de `CsvExporter` (separador `;`, BOM, escapado de `;`, comillas y saltos, `="imei"` para que Excel no convierta los IMEIs) y descarga en el navegador con nombre `<base>_yyyy-MM-dd_HH-mm.csv`.
- `ui/TogglePill`, `ui/FiltroImei` (campo con borde rojo/verde y canonicalización al teclear), `ui/RangoFechas` ("Desde:" / "Hasta:" con calendario y campo no editable), `ui/MenuCasillas` (desplegable de casillas con etiqueta resumen; sirve para Tipo e Incidencias), `ui/BadgeTipo` (píldora Reparación / Glass / Pulido con sub-etiqueta "Chasis"), `ui/CeldaFechas` (dos líneas: inicio en gris pequeño y "→ fin"), `ui/TextoExpandible` (celda con elipsis que abre el popup de texto: título, área de texto, botón "Copiar", ✕), `ui/SelectorLista` (lista filtrable para cliente y modelo), `ConfirmDialog` con variante de motivo obligatorio (botón destructivo deshabilitado hasta escribir), menú contextual con "📋 Copiar celda" en todas las tablas (portapapeles y resaltado breve de la celda copiada).
- `DataTable`: `<colgroup>` en vez de columna de relleno, `colSpan` de grupos, ordenación opcional, selección de fila (fondo azul medio, como hoy) y navegación por teclado; **virtualización de filas** con `@tanstack/react-virtual` (única dependencia nueva) manteniendo la cabecera fija y el contenedor con desplazamiento horizontal; `filaClase` sigue pintando los bordes de estado.

**6.4 Resto de la deuda de Cimientos que se paga aquí:** `role="status"` del banner montado siempre (la región viva existe aunque esté vacía); un único blanco en `tokens.css` (`--color-superficie`) usado por tablas, columna lateral y diálogos en lugar de `bg-white` / `bg-card` sueltos; `SessionProvider` movido a `shared/session` (con `useSession`) y la zona de lint de `modules` bloquea también `@/app/**`.

## 7. Vistas (resumen; las fichas de paridad son el criterio de aceptación)

**7.1 Pendientes: Reparaciones y Glass** (`PendientesTecnicoView` × 2). Título "Mis asignaciones pendientes" y píldora "N pendientes" (tope "999+"; "1 pendiente"). Filtros: IMEI, "Tipo" (casillas "Solicitudes pieza", "Incidencias", "Asignaciones"; etiqueta "Tipo" / nombre / "Todas" / "N filtros"), "Limpiar filtros". Columnas: Id Asignación, Tipo (badge + "Chasis"), IMEI (con píldora "Glass: X" en verde en filas de reparación con glass abierta sin entrega, o "Rep: X" en azul en filas de glass con reparación abierta; tooltips), Modelo, Fecha asignación (`yyyy/MM/dd HH:mm`), Comentario, Cliente, Asignado por ("—" si no hay), Estado (badges apilados según `estadoPendiente`), botón "Añadir reparación" / "Añadir glass" (deshabilitado en este sub-proyecto; en glass se oculta mientras haya reparación abierta sin entrega), papelera (solo SUPERTECNICO). Borde izquierdo de 8 px naranja si hay solicitud de pieza, rojo si incidencia; fila seleccionada azul medio. Orden: urgentes, después con cliente, después el resto (estable sobre el orden del servidor). Menú contextual: "📋 Copiar celda"; "Marcar por cerrar" / "Quitar por cerrar" (solo reparaciones); "Entregar a <técnico de glass>" / "Deshacer entrega" (reparaciones con glass abierta; deshacer solo quien entregó); "Marcar que llegó" (glass bloqueada) y "Deshacer llegada" (glass con entrega firmada por uno mismo). Papelera: `ConfirmDialog` "Borrar asignación <id>" / "El técnico dejará de verla en su lista de pendientes." (y "…y la incidencia se marcará como no activa en la tabla principal." si es incidencia) / "Borrar asignación"; borra la asignación o la incidencia activa del IMEI según el caso. Placeholder "No tienes asignaciones pendientes". CSV `mis_pendientes`. Datos: `GET /reparaciones/asignaciones` y `GET /glass/asignaciones` (sin `?tecnico=`: el servidor ya filtra al técnico del token; el supertécnico lo envía con su `idTec`).

**7.2 Pendientes: Pulidos** (`PulidoTecnicoView`). Título "Mis pulidos pendientes" y píldora. Filtros: IMEI, "Limpiar filtros"; botones "Seleccionar todo" (marca o desmarca todas las visibles) y "Completar seleccionados" (primario, deshabilitado sin selección). Columnas: casilla, Id Asignación, IMEI, Modelo, Fecha asignación, Comentario, Cliente, Asignado por, papelera (SUPERTECNICO). Sin badges ni borde de estado. Menú: solo "Copiar celda". Completar: `POST /pulidos/asignaciones/completar-lote` con los ids marcados, limpia la selección y recarga. Papelera: "Borrar asignación <id>" / "El pulido dejará de estar asignado y desaparecerá de tus pendientes." / "Borrar asignación". Placeholder "No tienes pulidos pendientes". CSV `pulidos_pendientes`.

**7.3 Historial: Reparaciones y Glass** (tabla propia de `ReparacionView*`). Título "Mis reparaciones" para TECNICO (también en Glass) y "Historial de reparaciones" para los otros dos; píldora "N reparaciones". Filtros: IMEI; Técnico (solo SUPERTECNICO y ADMIN; multiselección sobre `GET /tecnicos`; "Técnico" / nombre / "N técnicos"); Pieza (categorías presentes en los datos; "Pieza" / nombre / "N piezas"); "Desde:" / "Hasta:" por fecha de fin; Incidencias (Abiertas / Cerradas / Sin incidencia); "Limpiar filtros". Columnas: Id Reparación, IMEI teléfono, Modelo, Reparador (con "Llegó dd/MM HH:mm" debajo en glass con entrega), Asignado por, Fechas (dos líneas `yyyy/MM/dd`), Componente (con "Reutilizado" en cursiva), Observaciones (expandible), Estado (Incidencia / Resuelta / Normal), Incidencia (texto, "Sin incidencia" en cursiva gris; resuelta en gris sobre fondo verde claro; clic abre el popup), Id Rep. Anterior (enlace que selecciona y desplaza a esa fila). Borde izquierdo rojo con incidencia abierta, verde si resuelta. Orden del servidor. Menú: "Copiar celda" (todos); SUPERTECNICO además "Editar" (deshabilitado en este sub-proyecto), "Borrar" (bloqueado con aviso si otra reparación la referencia; si no, `ConfirmDialog` con motivo "Borrar reparación" / "Se borrará <id>. Los componentes usados volverán a stock y, si resolvía una incidencia, esta quedará activa de nuevo. Escribe el motivo." / "Borrar reparación"), "Añadir incidencia" (si no la tiene: diálogo "Añadir incidencia" con "Comentario de incidencia", "Técnico asignado" (activos, preseleccionado el reparador) y botón "Añadir incidencia y asignar" habilitado solo con ambos) y "Cancelar incidencia" (si está abierta: `ConfirmDialog` "Borrar incidencia" / "Esta acción solo es válida si fue un error al añadirla." / "Borrar incidencia"). CSV `mis_reparaciones` / `mis_glass` (TECNICO) y, para SUPERTECNICO y ADMIN, el del historial global con la columna Técnico (nombre de fichero y cabeceras exactas tomados del controller de cada rol y fijados en la ficha). Datos: `GET /reparaciones/historial`, `GET /glass/historial`.

**7.4 Historial: Pulidos** (`HistorialPulidoView`). Título "Historial de pulidos" y píldora "N pulidos". Filtros: IMEI, Técnico (los tres roles, activos), "Desde:" / "Hasta:" por fecha fin, "Limpiar filtros". Columnas: Id Pulido, IMEI, Modelo, Técnico, Fecha asignación, Fecha fin (`yyyy/MM/dd HH:mm`), Comentario, Cliente, Asignado por. Sin borde de estado. Menú: "Copiar celda"; SUPERTECNICO además "Editar modelo" (selector "Editar modelo" / "Selecciona el modelo:" con filtro, Guardar y Cancelar → `POST /telefonos {imei, modelo}`) y "Borrar" (`ConfirmDialog` con motivo "Borrar pulido <id>" / "Se borrará <id> del historial de pulido. Escribe el motivo." / "Borrar" → `DELETE /pulidos/historial/{id}`). Placeholder "No hay pulidos completados". CSV `historial_pulidos`. Datos: `GET /pulidos/historial`.

**7.5 IMEIs: maestro** (`AgrupadoView`). Título "Agrupado por IMEI" y píldora "N IMEIs". Filtros: IMEI, Técnico (los tres roles; deja los IMEIs donde alguno de los marcados intervino), Cliente (valores presentes, "(Sin cliente)" primero), "Desde:" / "Hasta:" por fecha fin de los trabajos, Incidencias (aquí solo "Incidencia" / "Normal"), "Limpiar filtros". Filas = grupos por IMEI de los tres historiales (el TECNICO solo de los suyos), ordenadas por actividad más reciente; fondo `#EEF0F5`, borde izquierdo de 4 px (rojo si hay incidencias abiertas, azul medio si no), cursor de mano. Columnas: IMEI teléfono (negrita, con el icono de historial que abre el detalle), Modelo, Fechas (más antigua → más reciente, `yyyy/MM/dd HH:mm`), Trabajos ("2 Rep · 1 Glass · 1 Pul"), Estado (Incidencia / Normal), Observación (del teléfono, expandible), Cliente (expandible). Doble clic o icono → detalle. Menú: "Copiar celda"; SUPERTECNICO además "Editar observación" (diálogo "Observación del teléfono", "Observación — IMEI <imei>", Guardar y Cerrar → `PATCH /telefonos/{imei}/observacion` con `updatedAt`) y "Editar cliente" (selector "Seleccionar cliente" con buscador, "— Sin cliente —" y los activos, "Nada seleccionado" / nombre, Seleccionar y Cancelar → `PATCH /telefonos/{imei}/cliente` con `updatedAt`). Al volver del detalle se restaura la selección y el desplazamiento. CSV `agrupado_resumen` (IMEI, Modelo, Primera, Última, Reparaciones, Glass, Pulidos, Inc. abiertas, Observación, Cliente).

**7.6 IMEIs: detalle** (`/reparaciones/imeis/:imei`). Barra "← Volver | IMEI: <imei> • <modelo> • N trabajos" (con filtro de técnico: "X de filtrados + Y de otros"). Filtros visibles: Técnico, "Desde:" / "Hasta:", Incidencias (Abiertas / Cerradas / Sin incidencia), "Limpiar filtros" (IMEI y Cliente ocultos). Filas: los trabajos del IMEI en orden cronológico de asignación; con filtro de técnico, primero las suyas y después las ajenas atenuadas. Columnas: Tipo (badge), Id, IMEI teléfono, Modelo, Reparador (con "Llegó…"), Asignado por, Fechas (`yyyy/MM/dd HH:mm`), Componente, Observaciones, Estado, Incidencia, Id Rep. Anterior (oculto en pulidos). Menú como en 7.3, con "Borrar trabajo" como título y botón del diálogo de borrado. CSV `agrupado_<imei>` (Tipo, ID, IMEI, Técnico, Fecha asig., Fecha fin, Componente, Reutilizado, Observaciones, Incidencia, Resuelto, ID Rep. anterior).

## 8. Errores

Política global del shell (banner para red y 5xx en fondo, diálogo en acciones y en el primer fallo de carga, 401 al login). Propios de estas vistas: los 403 de propiedad de los toggles ("Solo puedes marcar tus propias asignaciones", "Solo quien registró la entrega puede deshacerla", …) y los 422 de negocio ("Sin glass abierta para este IMEI", "La entrega ya está registrada", …) se muestran con el mensaje del servidor; 409 al editar observación o cliente del teléfono → "El teléfono fue modificado por otro usuario. Se recargan los datos." y recarga; borrado bloqueado → aviso "No se puede borrar" / "Este trabajo está siendo referenciado" / "La reparación <id> apunta a esta. Bórrala primero."; el 403 de `?tecnico=` no puede darse desde la web (nunca pide otro técnico siendo TECNICO), pero si ocurre se muestra como cualquier 403.

## 9. Diferencias aceptadas respecto al JavaFX

- Sin columna "Revisión" en IMEIs ni "Revisión logística" en su CSV (el servidor ya no la alimenta; hoy siempre "—" / "No").
- "Añadir reparación", "Añadir glass" y "Editar" deshabilitados con tooltip "Disponible con el formulario de reparación (siguiente entrega)" hasta el sub-proyecto 2; "Asignaciones" es un placeholder hasta el 3.
- SUPERTECNICO entra en Historial (hoy en Asignaciones) hasta el sub-proyecto 3.
- CSV como descarga del navegador; los diálogos y ventanas del JavaFX pasan a modales con los mismos títulos, etiquetas y botones; el detalle de IMEIs es una ruta (el botón atrás del navegador equivale a "← Volver").
- El resaltado al copiar una celda es un cambio de fondo breve, sin animación.
- La fila seleccionada existe (fondo azul medio) porque `DataTable` gana selección en este sub-proyecto; en Clientes sigue sin usarse.

## 10. Paridad, tests y verificación

- **Fichas** (antes de codificar, cerradas con el usuario): `docs/paridad/pendientes.md` (rep, glass y pulidos pendientes), `historial.md` (rep, glass y pulidos) e `imeis.md` (maestro y detalle), con la plantilla de la spec maestra, referencias a las capturas de `Apuntes/paridad-capturas/taller/` y las diferencias aceptadas.
- **Tests**: Vitest para toda la lógica pura (portando `EntregaGlassTest`, `GrupoImeiTest`, `FiltroImeiTest`, `PiezasTest`, `CsvExporterTest`); Testing Library + MSW por vista: cada rol, cada filtro, cada acción de menú y papelera, 403/409/422, placeholders y CSV generado; JUnit del servidor (§5.4). `npm run check` y `mvn test` en verde en cada tarea.
- **Verificación**: `capturas-web.mjs` (copia en `Apuntes/herramientas`) ampliado con las vistas del taller para TECNICO y SUPERTECNICO contra el build de la rama con proxy a producción, comparadas lado a lado con las del JavaFX; ADMIN lo comprueba el usuario en el navegador. Smoke Playwright ampliado: login TECNICO → Pendientes con filas → Historial → IMEIs → detalle → volver.

## 11. Criterios de cierre

- Fichas con todas las casillas marcadas o anotadas como diferencia aceptada; capturas de la web revisadas por el usuario.
- `npm run check` y `mvn test` en verde; contrato regenerado e idéntico entre `target/openapi.json` del servidor y `api/openapi.json` de la web; `client.ts` sin `Required<>`.
- Smoke Playwright en verde contra producción tras el despliegue (el servidor cambia: se reconstruyen backend y web en la VM con el runbook, ejecutado por el usuario).
- Merges `--no-ff` a `main` en web y servidor, gitlinks en el raíz, tag `v0.2.0` en la web. Push, merges y tag solo con OK del usuario, uno a uno.
- `Apuntes/plan-futuro.md` §9 marcado y memoria actualizada.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Volumen: tres listas de miles de filas para el supertécnico, refrescadas cada 60 s | Virtualización de filas; refresco solo de las consultas de la vista montada; si en el smoke pesa, endpoint agregado del maestro en el sub-proyecto 3 |
| Catálogo de modelos duplicado en TypeScript | Módulo único `modelos.ts` con test; sube al servidor cuando lo necesite el modal de asignación (sub-proyecto 3) |
| El SUPERTECNICO de pruebas no tiene pendientes propios | Papelera y píldoras de entrega se verifican con el TECNICO de pruebas y con tests; el supertécnico se verifica en Historial e IMEIs |
| Marcar todo `required` en el contrato rompe algún tipo de petición | El test de contrato y `tsc` lo detectan al regenerar; los campos opcionales de petición se anotan `nullable` |
| La virtualización cambia la semántica de los tests de tabla | `DataTable` virtualiza solo por encima de un umbral de filas; los tests de vista usan listas cortas |

## 13. Deuda que queda para más adelante

`onSesionExpirada` con unsubscribe; CI que compare `api/openapi.json` con el snapshot del servidor; `X-Forwarded-Proto` en `/v3/api-docs`; tests débiles de Cimientos (píldora, tokens del banner, "solo hay uno"); colapso de la barra por debajo de 900 px; el catálogo de modelos y la categoría de pieza en el servidor.
