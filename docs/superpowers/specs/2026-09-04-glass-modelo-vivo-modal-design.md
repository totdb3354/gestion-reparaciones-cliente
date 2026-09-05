# Facilitar la asignación de glass — bloque 1: modelo vivo en el modal de asignación (0.16.2)

Fecha: 2026-09-04
Estado: **IMPLEMENTADA en `feature/glass-modelo-vivo` (2026-09-05, 4 tareas + fix wave del review final) — pendiente smoke del usuario y merge a `hotfix/0.16.2` con su OK.** Ajustes del review final incorporados a §2.3, §3.d, §3.g, §3.i y §6.
Línea: **hotfix — ajena a `main` del repo raíz.** Rama de integración `hotfix/0.16.2` (tip `77ffbb9`, merge de estadísticas ronda 2). El repo raíz NO se toca en `main`.
Ramas: cliente **rama nueva `feature/glass-modelo-vivo`** desde `hotfix/0.16.2`. Servidor: **sin cambios** (el `main` desplegado, `b1b1816`, ya sirve; sin migración ni bump de gitlink por este bloque).
Relación: primer bloque de "facilitar la asignación de glass", cuarto punto de la ronda 2 de la 0.16.2 (ver `2026-09-02-estadisticas-puntos-ronda2-design.md`, §1). El **bloque 2 — predicción de la glass a partir de la reparación normal —** se brainstormea aparte cuando este esté codificado y tendrá su propia spec. La release sigue siendo la 0.16.2.

---

## 1. Contexto y dolor

- Una glass casi siempre va acompañada de una **reparación normal del mismo IMEI**: abrir el teléfono es reparación normal. El SuperTécnico asigna dos veces: primero la reparación en la cola Reparación y luego, a mano, la glass en la cola Glass.
- En el modal (`PendientesSuperTecnicoController.abrirFormularioAsignacion`) **cada entrada resuelve su modelo por su cuenta**: al cargarse en el formulario pide `GET /api/telefonos/{imei}/modelo`, que lee la tabla `Telefono` y, si no hay, hace el lookup externo. Ese endpoint **no graba nada**.
- Si el lookup falla, el SuperTécnico elige el modelo a mano y **esa elección vive solo en esa entrada**. Al escanear el mismo IMEI en Glass, la entrada nueva vuelve a buscar, vuelve a fallar y hay que elegirlo otra vez. Ese es el dolor.
- El modelo **solo se persiste al pulsar Guardar** (upsert de teléfono por entrada, `:2385`). Hasta entonces las colas pueden discrepar.
- El **cliente ya resolvió este mismo problema**: `propagarCliente` (`:2168`) copia la decisión a las entradas del mismo IMEI en las tres colas y `sembrarClienteEntrada` (`:2178`) la siembra en los IMEIs que se escanean después. Este bloque **calca ese mecanismo para el modelo** (decisión usuario: "simular lo del cliente pero con el modelo").

## 2. Comportamiento (reglas)

1. **El modelo pertenece al IMEI, no a la entrada.** En el modal, todas las entradas de un mismo IMEI (Reparación y Glass, rojas y verdes) muestran siempre el mismo modelo.
2. **Decisión manual = guardado inmediato + propagación.** Al elegir o cambiar el modelo a mano en cualquier cola, se manda al instante a BD (upsert de teléfono) y se copia a todas las entradas de ese IMEI. La última decisión manda.
3. **Escanear un IMEI que el modal ya conoce** (por lookup con éxito o por decisión manual) crea la entrada **ya con modelo y sin búsqueda de modelo** (ni "Buscando…" ni lookup externo). La **precarga del cliente de BD sigue corriendo una vez por entrada**, como hoy: la BD manda sobre el cliente pegajoso también en las entradas sembradas (hallazgo del review final, 2026-09-05). Si no conoce el modelo, busca como hoy.
4. **Guardar no cambia.** Sigue persistiendo modelo, cliente y asignaciones de todas las entradas verdes. El guardado temprano es un adelanto, no un sustituto: si fallara, Guardar lo vuelve a mandar.
5. **El modelo que llega solo del lookup automático NO se guarda hasta Guardar** (como hoy). El lookup es repetible y persistirlo al escanear crearía filas de teléfono para IMEIs que solo se escanearon y se quitaron de la cola.
6. **No hay "modelo pegajoso"** entre IMEIs distintos. Del cliente se copia la propagación por IMEI y la siembra, **no** el default para los IMEIs siguientes (`clienteDefaultModal`): un modelo pegajoso entre teléfonos distintos no tiene sentido.
7. **Pulido no participa**: no tiene modelo en el modal.
8. **Técnico pegajoso por cola** (añadido en el smoke, 2026-09-05): el último técnico asignado en Reparación solo se propone en las entradas nuevas de Reparación, y el de Glass solo en Glass. Antes había una sola memoria y el técnico de reparación se arrastraba a la cola Glass, cuando no tiene por qué reparar la glass la misma persona. Pulido conserva su selector propio (ya iba por separado).

## 3. Mecánica en el modal (solo cliente)

Anclajes actuales (tip `77ffbb9`, `PendientesSuperTecnicoController.java`; reverificar líneas al implementar):

- `EntradaAsignacion.modeloCode` (`:120`), `tieneModelo()` (`:133`).
- `confirmarModelo` (`:1985`): **único punto por el que se fija un modelo** en el formulario (pone `modeloSel`, `actual.modeloCode`, texto del campo, cierra el popup). Lo llaman:
  - el **lookup** (`lanzarLookup`, `:2043`; aplica el resultado en `:2060-2061`), y
  - **cuatro vías manuales**: Enter en el campo → primera coincidencia (`tfModelo.setOnAction`, `:2232`), pérdida de foco con coincidencia exacta (`:2247`), clic en la lista (`:2258`) y Enter en la lista (`:2263`).
- `lanzarLookup` **se salta** las entradas con modelo (`tieneModelo()`) o ya buscadas (`modeloBuscado`).
- `cargarEntrada` (`:2119`) pinta `e.modeloCode` en el campo al cargar una entrada; por eso, al cambiar de cola, una entrada propagada se ve sin más.
- El listener de texto del campo (`:2219`) pone `modeloCode = null` mientras se teclea (no es una decisión).
- Escaneo: `intentarAnadir` (`:2268`) y el **pegado múltiple** (bucle sobre `res.imeis()`, `:2296` aprox.) crean la entrada y ya llaman a `sembrarClienteEntrada` / `aplicarClienteDefaultEntrada`.
- Estado del cliente que se calca: `clienteManual` (`:1780`), `propagarCliente` (`:2168`), `sembrarClienteEntrada` (`:2178`).

Cambios:

a. **Mapa `modeloPorImei`** (`Map<String,String>` IMEI → código interno), declarado junto a `clienteManual`. Vive lo que el modal. Lo alimentan el lookup con éxito (`putIfAbsent`: nunca pisa una decisión manual) y toda decisión manual (`put`).

b. **`decidirModelo`** (`Consumer<String>` nuevo, envuelve a `confirmarModelo`): lo usan **las cuatro vías manuales** en lugar de `confirmarModelo`. Hace, en este orden: `confirmarModelo(code)`; `modeloPorImei.put(imei, code)`; `propagarModelo(imei, code)`; `persistirModelo(imei, code)`. El IMEI es el de `actual[0]` (si no hay entrada cargada, no hace nada). El lookup **sigue llamando a `confirmarModelo`** directamente.

c. **`propagarModelo(imei, code)`**: recorre `pilaRep` y `pilaGlass` y copia `modeloCode` a **todas** las entradas de ese IMEI (rojas y verdes), luego `renderPila`. La entrada cargada es la que originó la decisión, así que el campo ya está al día; las demás entradas del IMEI están en la otra cola y se pintan al cargarse.

d. **Sembrar al escanear**: en las dos vías de escaneo, tras crear la entrada y sembrar el cliente: `if (modeloPorImei.containsKey(imei)) e.modeloCode = modeloPorImei.get(imei)`. `lanzarLookup` se salta **solo la mitad de modelo** cuando la entrada ya lo tiene (`buscarModelo = !e.tieneModelo()`: sin "Buscando…", sin `getModelo`, sin prompt "No encontrado"), pero sigue haciendo la **precarga del cliente** (`getClienteId`) una vez por entrada (`modeloBuscado`). Motivo (review final): el lookup también carga el cliente de BD que pisa al pegajoso; saltarlo entero dejaba que un cliente pegajoso de otro IMEI se quedara pintado en la entrada sembrada y acabara en `Telefono` al Guardar.

e. **`persistirModelo(imei, code)`**: en un hilo aparte (mismo patrón que el lookup), `telefonoDAO.insertar(imei, code)` — la versión de **dos argumentos** (`TelefonoDAO.java:48`): `POST /api/telefonos` con `imei` y `modelo`, **sin `idCli` ni `clienteExplicito`**. En el servidor eso hace `MODELO = COALESCE(?, MODELO)` e `ID_CLI = COALESCE(NULL, ID_CLI)`: fija el modelo y **no toca el cliente**; no escribe en el log (solo lo hace con cliente). Si falla: una línea en `System.err` y nada más (sin diálogo ni indicador); Guardar lo persiste igual.

f. **Lookup en vuelo vs decisión manual**: al volver el lookup (`:2060`), el resultado **solo se aplica si la entrada sigue sin modelo** (`!e.tieneModelo()`). Hoy pisa incondicionalmente; con propagación, una decisión manual en la otra cola puede haber llegado mientras el lookup estaba en vuelo y no debe perderse. Además `modeloPorImei.putIfAbsent(imei, res)`.

g. **Micro-fix colateral (una línea, incluido)**: Enter en el campo con el modelo ya confirmado y el texto sin cambiar (`tfModelo.setOnAction`, `:2232`) hoy re-confirma `modelosFiltrados.get(0)`, que tras un confirm es el **primer modelo de toda la lista** (el filtro se resetea a "todos"). Era una rareza inocua; con guardado inmediato pasaría a BD. Guard: si `modeloSel != null` y el texto coincide con `traducirModelo(modeloSel)`, no hacer nada. **Y si el texto está vacío, tampoco** (review final): tras un lookup fallido el campo está vacío y el filtro es "todos", así que Enter decidía el primer modelo del catálogo (`6s`), que ahora se propagaría y se guardaría en BD.

h. **Guardar, borrar entrada de la cola, `asignarActual`, `defTecnicos`**: sin cambios.

i. **Vía de pérdida de foco (hardening, review final)**: el callback corre diferido (`runLater`); se captura `origen = actual[0]` al perder el foco y el callback no decide si `actual[0]` ya es otra entrada. El análisis del review indica que el caso no era alcanzable (el clic en una fila de la pila ejecuta `cargarEntrada` síncrono, que reemplaza el texto antes del callback), pero deja la invariante explícita por dos líneas.

j. **`defTecnicos` por cola** (regla 8): pasa de una `List<Tecnico>` única a `Map<TipoTrabajo, List<Tecnico>>` (`EnumMap` con `REPARACION` y `GLASS`). `cargarEntrada` propone `defTecnicos.get(e.tipo)` a la entrada sin técnicos y `asignarActual` actualiza la lista de `e.tipo`. Pulido no usa `defTecnicos`.

## 4. Servidor

**Sin cambios, sin migración, sin bump de gitlink.** `POST /api/telefonos` (`TelefonoController.insertar`, `:72`) ya existe con la semántica necesaria (upsert; modelo con COALESCE; cliente intacto si no se manda). El modal es del SuperTécnico y el endpoint no restringe el modelo por rol, así que no cambia la autorización.

## 5. Errores y casos límite

- **Fallo de red en el guardado temprano** → silencioso (línea de log); el modal sigue; Guardar vuelve a mandar el modelo.
- **Teclear o borrar en el campo** no persiste ni propaga: solo una elección confirmada por una de las cuatro vías manuales.
- **Lookup en vuelo** cuando llega una decisión manual del mismo IMEI desde la otra cola → el resultado del lookup se descarta (§3.f).
- **Entrada verde en la otra cola** → recibe el modelo nuevo; Guardar lo manda con la asignación.
- **Modelo del lookup** → no toca BD hasta Guardar (regla 5).
- **Cerrar el modal sin guardar** → las decisiones manuales ya están en BD (efecto deseado); el mapa muere con el modal y, al reabrir, el lookup lee la BD y trae ese modelo.
- **Asignaciones ya existentes del IMEI** (p. ej. la reparación asignada ayer) → la tabla de Asignaciones lee el modelo del teléfono; tras `cargar()` (Guardar/cerrar) muestra el nuevo.
- **Cliente del IMEI** → intacto (COALESCE); el guardado temprano nunca lleva `idCli`.
- **Duplicado en la misma cola** → sigue rechazado como hoy ("Ese IMEI ya está en la cola").

## 6. Pruebas

- La lógica vive en lambdas del modal, sin tests unitarios (igual que `propagarCliente`). **No se exige test nuevo**; si el implementador extrae algo puro (p. ej. el guard de §3.g), test unitario.
- **Suite del cliente verde** (188 en `77ffbb9`).
- **Smoke manual** (SuperTécnico, preproducción):
  1. Cola Reparación: IMEI cuyo lookup falla → elegir modelo a mano → cola Glass: escanear el mismo IMEI → **nace con el modelo, sin "Buscando…"**.
  2. Cola Glass: cambiar el modelo → volver a Reparación → la entrada (roja o verde) muestra el nuevo.
  3. Elegir a mano y **cerrar el modal sin guardar** → `SELECT MODELO FROM Telefono WHERE IMEI=…` lo tiene.
  4. Lookup con éxito en Reparación → Glass hereda sin segunda búsqueda; con un IMEI nuevo, **no hay fila en `Telefono` hasta Guardar**.
  5. IMEI con cliente en BD → tras un guardado temprano, el cliente sigue.
  6. Pegado múltiple con IMEIs ya conocidos por el modal → nacen con modelo.
  7. Enter en el campo con el modelo ya confirmado → no cambia nada (§3.g).
  8. Guardar → asignaciones y modelos correctos; sin regresión en cliente pegajoso ni en pulido.
  9. (review final) Asignar 2 IMEIs del cliente D (pegajoso = D), escanear en Reparación un IMEI X que en BD tiene cliente C, **no** asignarlo, pasar a Glass y escanear X → la entrada de Glass muestra **C**, no D.
  10. (review final) Entrada cargada con el campo de modelo vacío (lookup fallido) → Enter → no pasa nada; sin fila nueva en `Telefono`.
  11. (review final) Teclear el nombre exacto de un modelo y hacer clic en "Asignar →" → el modelo se decide y se guarda pero la entrada **no** se asigna; el segundo clic asigna (vía de pérdida de foco).
  12. (review final) Elegir modelo a mano, quitar la entrada con ✕ y volver a escanear el mismo IMEI en el mismo modal → nace con modelo. Ese IMEI aparece ya en Inventario (fila de `Telefono` sin asignación): efecto aceptado, verlo una vez.
  13. (review final) Elegir el modelo A e inmediatamente el B en el mismo IMEI → `SELECT MODELO` da B.
  14. (smoke) Asignar en Reparación al técnico R, pasar a Glass y escanear → ningún técnico marcado (o el último asignado en Glass, si lo hubo); volver a Reparación y escanear → R propuesto.

## 7. Fuera de alcance

- **Bloque 2**: predicción de la asignación de glass a partir de la reparación normal (spec aparte, brainstorm pendiente).
- Modelo pegajoso entre IMEIs distintos.
- Persistir el resultado del lookup al escanear.
- Log de cambios de modelo (`POST /api/telefonos` no lo registra y no se añade).
- Pulido; cambiar tipo rep↔glass desde Asignaciones (backlog).

## 8. Entregables

- Código en `feature/glass-modelo-vivo` (cliente); entrada en `CHANGELOG.md` `[Unreleased]` (Changed: "Modelo del IMEI compartido entre las colas del modal de asignación y guardado al elegirlo").
- Merge `--no-ff` a `hotfix/0.16.2` **solo con OK del usuario**; release 0.16.2 después (gitlink y tag son de la release, no de este bloque).
