# Separar Glass como tipo propio + reestructuración de vistas — Design

**Fecha:** 2026-06-30
**Estado:** Aprobado, pendiente de plan

## Resumen

Hoy una reparación puede llevar cualquier pieza, incluidas **Glass** (SKU prefijo `g`) y **Marco** (SKU prefijo `mc`). Este cambio **separa Glass + Marco como un tercer tipo de trabajo propio**, paralelo a Pulido, con su propio espacio de IDs en la tabla `Reparacion`. A partir del cambio existen **tres tipos**: Reparación, Glass y Pulido.

Idea central validada: **Glass ≈ Reparación**. Glass consume stock, usa el mismo modal de piezas (filtrado), tiene observaciones e incidencias y se completa por IMEI. Lo único que Glass toma del patrón de Pulido es el **truco de prefijo de ID** para poder distinguirlo. **Pulido sigue siendo el tipo distinto** (sin pieza, sin modal, se completa por lote).

El cambio reorganiza además las vistas:
- **Historial**: toggle de 3 (`Reparaciones | Glass | Pulido`), siempre en **vista plana**.
- **Nuevo apartado "Agrupado"** en el sidebar (los 3 roles): maestro por IMEI con drill-down, mostrando los **3 tipos por IMEI**. Sale del Historial.
- **Asignaciones** (supertécnico): **una tabla unificada** de los 3 tipos + **un único modal** de alta con selector de tipo.
- **Pendientes** (técnico): toggle de 3; Glass se completa como una reparación.

## Estado actual (contexto)

Todo vive en **una sola tabla `Reparacion`**, y el tipo se distingue por el **prefijo del `ID_REP`**:

| Prefijo | Significado | Query (servidor `ReparacionDAO`) |
|---|---|---|
| `R` | Reparación terminada (historial) | `HISTORIAL_SELECT … WHERE r.ID_REP LIKE 'R%'` |
| `A` (no `AP`) | Asignación de reparación pendiente | `ASIGNACION_SELECT … WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL` |
| `AP` | Asignación de pulido pendiente | `ASIGNACION_PULIDO_SELECT … WHERE r.ID_REP LIKE 'AP%' AND r.FECHA_FIN IS NULL` |
| `P` | Pulido terminado (historial) | `HISTORIAL_PULIDO_SELECT … WHERE r.ID_REP LIKE 'P%'` |

- Los IDs los genera `ReparacionDAO.nextId(prefijo)` con formato `PREFIJO + yyyyMMdd + "_" + N`.
- Glass/Marco hoy **no son un tipo**: solo piezas dentro de una reparación. En el modal (`FormularioReparacionController.cargarFilas`, líneas 365-397) las filas Glass (`g`) y Marco (`mc`) se pintan al final tras un delimitador gris (`crearDelimitador`).
- El sidebar del supertécnico (`ReparacionViewSuperTecnico.fxml`) tiene 3 secciones: **Asignaciones** (cola de altas), **Pendientes** (las propias por completar), **Historial**. Cada una con un toggle `Reparaciones | Pulidos`. El Historial tiene además un toggle `Agrupado | Plano`.
- La vista agrupada por IMEI (`GrupoImei`, modos `MAESTRO/DETALLE/PLANO`) y los toggles de pulido **existen replicados en los 3 controladores de rol**: `ReparacionControllerSuperTecnico` (~1765 líneas), `ReparacionControllerAdmin` (~1195), `ReparacionControllerTecnico` (~1396). Solo el supertécnico asigna.
- El modal de asignación de reparación (`PendientesSuperTecnicoController.abrirFormularioAsignacion`, líneas 720+) ya es una **pila por lotes**: se escanean IMEIs (sección roja "pendiente"), se configuran y pasan a verde, se guardan todos al final. Pide IMEI, técnico, **modelo**, **cliente**, comentario.
- El modal de asignación de pulido (`PulidoSuperTecnicoController.abrirFormularioAsignacion`, líneas 403+) es más ligero: técnico por defecto + comentario + escaneo de IMEI (el modelo se auto-resuelve en servidor vía `ImeiLookupService`).

## Decisiones tomadas (resueltas con el usuario)

1. **Modelo de datos — "solo de aquí en adelante".** Glass es un tipo nuevo con IDs propios a partir del cambio. **Sin migración**: las reparaciones `R` históricas que usaron pieza Glass/Marco se quedan como "Reparación" (legacy). El histórico antiguo no se reclasifica.
2. **Asignaciones — tabla única + 1 modal con selector.** Desaparece el toggle `Reparaciones|Pulidos` de la sección Asignaciones; pasa a una sola tabla con columna **Tipo** y filtro. Un único botón abre el modal con selector `Reparación | Glass | Pulido`. Reparación y Glass comparten la **misma pila** (rica); Pulido va en **sub-pila** ligera.
3. **Nuevo apartado "Agrupado"** en el sidebar para **los 3 roles**, mostrando los 3 tipos por IMEI. Se construye **una sola vez** como componente compartido (FXML + controller) e incluido vía `fx:include`.
4. **Estadísticas — Glass suma con Reparaciones** por ahora (`LIKE 'R%'` → `R% OR G%`). Los datos quedan en BD con prefijo propio, por lo que la granularidad futura (Reparación vs Glass vs Pulido por técnico/periodo) es solo cambio de query + UI, sin pérdida de datos.
5. **Completar Glass — una a una con modal** (como reparación), filtrado a Glass/Marco/Otro. El "por lote" se conserva donde ya existe: en la **asignación** (la pila).
6. **Nombre del apartado:** "Agrupado".
7. **Revisión logística unificada:** las 3 categorías resetean el OK de logística al crear la asignación, y el toggle queda bloqueado mientras haya **cualquier** asignación pendiente (de cualquier tipo). Corrige el comportamiento actual del pulido, que hoy no resetea ni bloquea.
8. **Validación de duplicados por categoría:** el mismo IMEI puede asignarse al mismo técnico **una vez por categoría** (reparación + glass + pulido a la vez), pero **no dos veces dentro de la misma categoría**. El chequeo pasa a ser por (IMEI, técnico, categoría).
9. **Pendientes: 3 pestañas separadas** (Reparaciones | Glass | Pulido). La unificación es solo de Asignaciones (el alta es un modal); en Pendientes el completar difiere por tipo (pulido por lote, rep/glass por modal por IMEI).
10. **Detalle del Agrupado:** el drill-down muestra todo el recorrido del IMEI — reparaciones, glass y pulidos juntos por orden cronológico.

## Diseño detallado

### Convención de prefijos nueva

| Prefijo | Significado |
|---|---|
| `AG` | Asignación de glass pendiente |
| `G` | Glass terminado (historial) |

Ciclo de vida idéntico al de reparación: `AG` (asignación pendiente) → el técnico completa abriendo el modal filtrado → se crea `G` (con su `Reparacion_componente`) y se cierra la `AG` (`FECHA_FIN`).

### A. Servidor — BD y queries

**Detalle crítico — cada `LIKE 'A%'` necesita tratamiento individual** (NO vale "excluir `AG` en todos"). Con las reglas acordadas (revisión logística cuenta las 3, validación por categoría, urgentes = rep+glass), revisión punto por punto en `ReparacionDAO`:

| Query | Qué debe contar | Acción |
|---|---|---|
| `ASIGNACION_SELECT` (asignaciones de reparación) | solo `A` | **añadir** `AND NOT LIKE 'AG%'` (ya excluye `AP`) |
| `GLASS_ASIGNACION_SELECT` (nueva) | solo `AG` | `LIKE 'AG%'` |
| `TIENE_ASIGNACIONES` (bloqueo de revisión logística) | las 3 (`A`,`AG`,`AP`) | **quitar** el `NOT LIKE 'AP%'` → `LIKE 'A%' AND FECHA_FIN IS NULL` |
| `existeAsignacionParaTecnico` (dedup al asignar) | la categoría que se crea | **parametrizar** por prefijo (`A` excl. `AG`/`AP` · `AG` · `AP`) |
| `borrarIncidenciaPorImei` (cancelar incidencia) | solo la categoría de la incidencia | **acotar** al prefijo correspondiente (incidencias por tipo) |
| `marcarUrgentesClienteVencidas` (job urgentes) | `A`+`AG`, no `AP` | **sin cambios** (ya es `LIKE 'A%' AND NOT LIKE 'AP%'`, que incluye `AG`) |
| `getTecnicosConAsignacionActiva` (indicador "N asignados") | cualquier asignación abierta (`A%`) | **sin cambios** |

**Reset de revisión logística:** `insertarAsignacion`, la nueva `insertarAsignacionGlass` y `insertarAsignacionPulido` las **tres** hacen `UPDATE Telefono SET REVISION_LOGISTICA = 0` (hoy el pulido no lo hace → se corrige).

**Validación de duplicados por categoría:** `existeAsignacionParaTecnico(imei, idTec, prefijo)` comprueba solo dentro de la categoría que se está creando. La deduplicación de la pila del modal pasa de clave `IMEI` a clave `(IMEI, tipo)`.

**Lectura glass** (espejo de pulido pero con forma de reparación):
- `GLASS_ASIGNACION_SELECT`: como `ASIGNACION_SELECT` pero `WHERE r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL`. Mantiene el join a `Reparacion_componente` para solicitudes de pieza (glass puede generar solicitudes igual que reparación).
- `GLASS_HISTORIAL_SELECT`: como `HISTORIAL_SELECT` pero `WHERE r.ID_REP LIKE 'G%'` (trae tipo de componente, incidencia, reutilizado, etc.).
- Métodos DAO: `getAsignacionesGlass(idTec?)`, `getHistorialGlass(idTec?)`, `getAsignacionesGlassPorImei(imei)`, `getHistorialGlassPorImei(imei)`.

**Escritura glass:**
- `insertarAsignacionGlass(imei, idTec, comentario, urgente, idTecAsigna)` → `nextId("AG")`. Igual que `insertarAsignacion` pero con prefijo `AG`.
- **Completar glass reutiliza el flujo de reparación existente** (`insertarCompleta` / `guardarFilaIndividual`): generan filas `R`. → **Decisión a confirmar en el plan:** para que el resultado caiga en el historial **Glass** (`G`), el completado de una asignación `AG` debe generar filas con prefijo **`G`**, no `R`. Esto implica parametrizar `nextId` (el prefijo del resultado depende del prefijo de la asignación: `A`→`R`, `AG`→`G`). Es el punto de implementación más delicado del servidor.
- Editar/borrar glass: reutilizan `editarReparacion` / `eliminar` (operan por `ID_REP`, sirven para `G`); `eliminarAsignacion` sirve para `AG`.

**Endpoints** (`GlassController`, espejo de `PulidoController` pero con semántica de reparación, o ampliación de `ReparacionController` con rutas `/api/glass/...`):
- `GET /api/glass/asignaciones`, `GET /api/glass/historial`
- `POST /api/glass/asignaciones`
- Completar/editar/borrar reutilizando los endpoints de reparación si operan por `ID_REP`, o rutas propias `/api/glass/...`.
- Logs de actividad con acciones `CREAR_ASIGNACION_GLASS`, `COMPLETAR_GLASS`, `ELIMINAR_*_GLASS` (espejo de los de pulido).

### B. Cliente — DAO

- Nuevo `GlassDAO` espejo de `PulidoDAO` + los métodos de completar/editar de reparación que apliquen (apuntando a las rutas glass). Reutiliza `ReparacionResumen`.

### C. Cliente — Asignaciones (supertécnico)

- **Tabla unificada**: una sola `TableView` con columna **Tipo** (Reparación / Glass / Pulido) y filtro por tipo (junto a los filtros IMEI/técnico actuales). Sustituye el toggle `Reparaciones|Pulidos` y sus dos sub-paneles.
- **Origen de datos**: unión de `getAsignaciones` (A) + `getAsignacionesGlass` (AG) + `getAsignacionesPulido` (AP). El tipo se deriva del prefijo del ID.
- **Modal de alta único** (`Asignar`), con **selector `Reparación | Glass | Pulido`** arriba:
  - **Reparación y Glass → misma pila "rica"** (rojo/verde, con modelo + cliente + técnico + comentario). El selector fija el **tipo por defecto** de los IMEIs que se escaneen; cada entrada lleva su etiqueta de tipo (editable por fila). Al guardar, cada entrada crea `A` o `AG` según su etiqueta.
  - **Pulido → sub-pila ligera** (técnico + comentario), como hoy. Al guardar crea `AP`.
  - Se mantiene el alta por lotes en los tres.
- Implementación: extender `PendientesSuperTecnicoController` para la tabla unificada y el modal con selector, reutilizando la maquinaria de pila existente; integrar el flujo ligero de pulido como sub-modo. (Alternativa a evaluar en el plan: un controller de asignaciones nuevo que orqueste; se prefiere extender lo existente para no duplicar la pila.)

### D. Cliente — Historial + nuevo apartado "Agrupado"

- **Historial**: el toggle pasa a 3 (`Reparaciones | Glass | Pulido`), **siempre plano**. Se **elimina** el toggle `Agrupado | Plano` de aquí. El panel Glass reutiliza la misma tabla/columnas que Reparaciones (mismo `ReparacionResumen`), cargando `getHistorialGlass`. El panel Pulido sigue igual (`HistorialPulidoController`).
- **Nuevo apartado "Agrupado"** (4ª entrada del sidebar, los 3 roles):
  - Vista maestro por IMEI con drill-down (la lógica `GrupoImei` + modos `MAESTRO/DETALLE` actuales).
  - Cada grupo agrega **los 3 tipos** del IMEI (hoy solo agrega reparaciones). El detalle muestra Reparación + Glass + Pulido del IMEI, ordenados cronológicamente, con su columna/etiqueta de Tipo.
  - **Componente compartido**: nuevo `AgrupadoView.fxml` + `AgrupadoController`, incluido vía `fx:include` en los 3 FXML de rol, parametrizado por origen de datos (todos / solo del técnico). Esto **saca** la lógica de agrupado de los 3 controladores grandes y la centraliza → menos duplicación y controladores más limpios.
  - `GrupoImei` (modelo) se amplía para contar/contener los 3 tipos.

### E. Cliente — Pendientes (técnico y supertécnico)

- **3 pestañas separadas** (`Reparaciones | Glass | Pulido`), NO unificado. Justificación de la asimetría con Asignaciones: el alta (Asignaciones) es un modal que maneja los 3 tipos → unificable; el completar (Pendientes) difiere por tipo (pulido por lote vs rep/glass por modal por IMEI) → separado.
- **Glass**: lista de asignaciones `AG` del técnico; clic en una fila → abre el **modal de reparación filtrado** (solo Glass/Marco/Otro), elige pieza/cantidad/reutilizado/observaciones, guarda (descuenta stock real, genera `G`).
- **Pulido**: sin cambios (completar por lote).
- Aplica a `PendientesTecnicoController` (mis pendientes) y al panel equivalente del supertécnico.

### F. Modal de reparación (filtrado por tipo)

`FormularioReparacionController` recibe el **tipo** (Reparación / Glass) y filtra qué filas pinta en `cargarFilas`:
- **Glass**: solo filas Glass (`g`) + Marco (`mc`) + Otro (`otro`). (Hoy Glass/Marco ya van separadas tras el delimitador; el cambio es ocultar el resto.)
- **Reparación normal**: todas **menos** Glass y Marco; Otro se mantiene.
- El resto de la maquinaria (modelo, solicitudes, guardado individual, stock) no cambia.

### G. Estadísticas

- `getEstadisticasPorTecnico` (servidor): `WHERE r.ID_REP LIKE 'R%'` → `WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%')`.
- El gráfico no cambia de forma: Glass suma a la cifra de cada técnico. Pulido sigue sin contar.

### H. Logs de auditoría

Toda acción se registra en `Log_Actividad` (`LogDAO.insertar(idUsu, ACCION, DETALLE)`). Hoy el pulido ya emite acciones propias (`CREAR_ASIGNACION_PULIDO`, `COMPLETAR_PULIDO_LOTE`, `ACTUALIZAR_ASIGNACION_PULIDO`, `ELIMINAR_ASIGNACION_PULIDO`, `ELIMINAR_PULIDO`), enriquecidas con el IMEI en el detalle (para buscar todos los movimientos de un IMEI).

- **Glass emite acciones propias** (espejo de pulido/reparación), distinguibles del flujo normal: `CREAR_ASIGNACION_GLASS`, `ACTUALIZAR_ASIGNACION_GLASS`, `ELIMINAR_ASIGNACION_GLASS`, `COMPLETAR_GLASS`, `EDITAR_GLASS`, `ELIMINAR_GLASS`. Decisión: acciones específicas (no reutilizar las de reparación) para que la auditoría distinga glass de reparación normal, igual que se hizo con pulido.
- **Enriquecidas con IMEI** en el detalle, igual que el resto (mantiene la búsqueda "todos los movimientos de un IMEI" reciente).
- **Punto fino de implementación:** glass reutiliza el flujo de completar de reparación (`insertarCompleta`/`guardarFilaIndividual`). El controller debe emitir `COMPLETAR_GLASS` (no `COMPLETAR_REPARACION`) cuando el resultado es `G`; al diferenciar por el prefijo del ID es directo.
- **Visor de logs (`LogController.TIPOS_ACCION`, cliente):** la lista del filtro **no incluye hoy las acciones de pulido** (se registran pero no son filtrables). Al añadir glass, completar el filtro con las **acciones de glass y también las de pulido** que faltan, para que el filtro sea coherente.

## Alcance y roles

- **Asignar** (crear AG): solo **SUPERTECNICO**, como hoy con reparación y pulido.
- **Completar glass**: el técnico asignado (y supertécnico para las propias).
- **Historial 3-way + apartado Agrupado**: los 3 roles (Admin/SuperTécnico/Técnico), cada uno con sus datos (el técnico solo los suyos).
- **Admin**: sigue sin asignar; ve Historial (3-way) y Agrupado.

## Plan por fases (orden sugerido para el plan de implementación)

1. **Servidor**: prefijos `AG/G`, queries glass, revisión punto-por-punto de los `LIKE 'A%'` (ver tabla), parametrizar el prefijo del resultado al completar (`A→R`, `AG→G`), reset de revisión logística en las 3 altas + `TIENE_ASIGNACIONES` contando las 3, validación de duplicados por categoría, endpoints + **logs de glass** (acciones propias enriquecidas con IMEI).
2. **Cliente**: `GlassDAO` + modal de reparación filtrado por tipo + completar el filtro de `TIPOS_ACCION` del visor de logs (glass + pulido que falta).
3. **Cliente**: Asignaciones unificada (tabla + columna Tipo) + modal con selector.
4. **Cliente**: Historial 3-way plano (quitar toggle Agrupado de aquí).
5. **Cliente**: apartado "Agrupado" como componente compartido, con los 3 tipos por IMEI; integrarlo en los 3 roles.
6. **Cliente**: Pendientes 3-way + completar glass por IMEI.
7. **Estadísticas** `R% + G%`.
8. Validación de arranque del servidor (no hay test de contexto Spring) + pruebas en preproducción.

## Casos borde y reglas

- **Incidencias en glass**: glass participa en el flujo de incidencia/reincidencia igual que reparación. Una reincidencia de un trabajo de glass se reasigna como **glass** (`AG`); las incidencias se rastrean **por tipo** (un IMEI puede tener a la vez una incidencia abierta de reparación y otra de glass, independientes).
- **Duplicados por categoría**: un IMEI puede estar asignado al mismo técnico a la vez en reparación, glass y pulido (una vez por categoría); nunca dos veces en la misma categoría. La pila del modal deduplica por `(IMEI, tipo)`.
- **Revisión logística**: crear cualquier asignación (rep/glass/pulido) resetea el OK; el toggle se bloquea mientras haya **cualquier** pendiente. Corrige el comportamiento actual del pulido.
- **"Otro"** aparece en el modal **tanto de glass como de reparación**.
- **Filtro de pieza** en el historial Glass: opciones Glass/Marco/Otro.
- **Reparación normal a partir del cambio**: ya no ofrece Glass/Marco en el modal. Las `R` antiguas con esas piezas siguen visibles en Historial > Reparaciones (legacy).
- **Trabajo combinado** (pantalla + batería): son **dos asignaciones** (una glass, una reparación), consecuencia natural del carve-out.
- **CSV**: cada vista (incluidas Glass y Agrupado) exporta su contenido visible, como hoy.
- **Job de urgentes de cliente**: marca `URGENTE` las asignaciones de **reparación y glass** (`A`+`AG`) vencidas con cliente; el **pulido queda fuera** (sin cambios respecto a hoy).

## Fuera de alcance (YAGNI)

- Migrar/reclasificar el histórico de reparaciones con pieza glass/marco.
- Estadísticas granulares por tipo (Reparación/Glass/Pulido como series separadas) — futuro; los datos ya quedan disponibles en BD.
- Completar glass por lote / auto-SKU por modelo.
- Refactor general de los 3 controladores de rol duplicados (solo se centraliza el nuevo apartado Agrupado; el resto se toca lo mínimo).

## Riesgos

- **Colisión de prefijos `A`/`AG`**: si se olvida algún `NOT LIKE 'AG%'`, las asignaciones de glass se mezclarían con las de reparación. Mitigación: lista exhaustiva de puntos en la fase 1 + pruebas.
- **Prefijo del resultado al completar** (`AG`→`G`): es el cambio más invasivo del servidor (toca `insertarCompleta`/`guardarFilaIndividual`/`nextId`). Requiere cuidado para no romper el flujo de reparación normal.
- **Duplicación x3**: aunque el apartado Agrupado se centraliza, el toggle 3-way de Historial y Pendientes se toca en los 3 controladores de rol. Riesgo de divergencia.
- **Arranque del servidor**: cambios de wiring (nuevo controller/beans) pueden romper el contexto Spring sin que la suite lo detecte → validar arranque.
