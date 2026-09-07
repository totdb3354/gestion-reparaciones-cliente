# Facilitar la asignación de glass — bloque 2: glass automática al asignar la reparación (0.16.2)

Fecha: 2026-09-05
Estado: **APROBADA por el usuario (brainstorm en sesión, 2026-09-05); codificada (plan de 8 tareas, review final "Ready to merge"). Reglas 2-6 y §5 revisadas el 2026-09-05 (decisión del usuario al ver el resultado): invariante "casilla ⇔ glass en la cola" en los dos sentidos, implementada como Task 9 del plan.** Servidor mergeado y desplegado (`main` `1559a72`).
Línea: **hotfix — ajena a `main` del repo raíz.** Rama de integración `hotfix/0.16.2` (tip `f04e823`, bloque 1 mergeado). El repo raíz NO se toca en `main`.
Ramas:
- Cliente: **rama nueva `feature/glass-prediccion`** desde `hotfix/0.16.2`.
- Servidor: **rama nueva `feature/glass-habilitados`** desde `main` del servidor (`b1b1816`, desplegado en preprod). **Las tareas de servidor nunca tocan el repo raíz.** Va primero: sin servidor nuevo no hay habilitados y el smoke del cliente no se puede hacer.

Relación: segundo y último bloque de "facilitar la asignación de glass" (0.16.2). El bloque 1 (`2026-09-04-glass-modelo-vivo-modal-design.md`) dejó el modelo vivo por IMEI, el técnico pegajoso por cola y el modal en pantallas pequeñas. Este bloque hace que la glass **nazca sola** al asignar la reparación normal. Gitlink del servidor y tag `v0.16.2` son de la release, no de este bloque.

---

## 1. Contexto y dolor

- Una glass casi siempre acompaña a una **reparación normal del mismo IMEI** (abrir el teléfono es reparación normal). Hoy el SuperTécnico asigna dos veces: la reparación en la cola Reparación y, a mano, la glass en la cola Glass, eligiendo técnico cada vez.
- El taller tiene un grupo pequeño y estable de técnicos que hacen glass (abajo). Elegir entre ellos es una decisión mecánica: **el que tenga el día menos lleno**. El modelo de carga diaria ya existe (`CargaTecnicos`, spec 2026-07-09: glass 1/17, chasis 1/8, normal 1/25; alcances Pedidos y Total).
- Decisiones del usuario (2026-09-05): disparo **manual** con una casilla tan simple como "Reparación de chasis"; el programa elige **según la carga**; la glass **nace en el modal, verde**, y si se quiere cambiar se va a la cola Glass; los habilitados viven en un **flag de `Tecnico` en el servidor**, se editan desde **Asignaciones** (SuperTécnico edita, Admin ve); la carga que se mira depende de si el teléfono **tiene cliente**.

## 2. Comportamiento (reglas)

1. **Casilla "Lleva glass"** en el formulario del modal, debajo de "Reparación de chasis", **solo visible en la cola Reparación**. Se comporta como chasis: por IMEI, se resetea entre teléfonos, no es pegajosa y no hace nada hasta pulsar Asignar.
2. **Invariante del modal (revisión 2026-09-05 y 2026-09-07, decisiones del usuario): en una reparación verde, casilla marcada ⇔ hay glass de ese IMEI en la cola Glass**, en los dos sentidos y en todo momento (única excepción: regla 4). En una reparación **pendiente (roja)** la casilla es solo una intención, como chasis: **no crea nada hasta pulsar Asignar**, y entonces la glass **nace directamente verde** con el técnico elegido (regla 3), con el modelo y el cliente de la reparación, **sin comentario**, sin chasis, sin urgente. Si la reparación ya está **verde** (se marca al editarla), la glass nace y se predice en el acto. **Desmarcarla retira la glass** de ese IMEI, esté como esté (roja, verde automática o editada a mano). (El 2026-09-05 se probó que naciera roja al marcar; el usuario lo descartó el 2026-09-07: "que nazca en verde al bajar la reparación".)
3. **Al pulsar "Asignar →"** en la reparación (mismas validaciones de hoy: modelo + algún técnico; nada extra para la glass), si su glass sigue **roja y sin técnicos marcados** la predicción la rellena: **verde con el técnico elegido y pastilla "auto"**; si no hay candidato (ningún habilitado activo, o todos con glass de ese IMEI) se queda **roja** y bloquea Guardar como cualquier otra. Si el usuario ya la había asignado a mano (verde) o ya le había marcado técnicos, no se toca. Reeditar la reparación verde ("Guardar cambios", p. ej. cambiando su técnico) **vuelve a predecir** la glass si sigue siendo automática (sin revisar), con la carga de ese momento (smoke 2026-09-07); una glass editada a mano no se toca. El modelo de la glass se iguala al de la reparación si le faltaba.
4. **La casilla se deshabilita** con la nota "ya tiene glass: <técnico>" cuando el IMEI ya tiene una glass abierta en BD (visible en la tabla de Asignaciones): el teléfono ya está cubierto; no crea nada, y una glass escaneada a mano para ese IMEI no marca la casilla (única excepción a la invariante).
5. **En la pila**, las filas verdes muestran además, en una segunda línea, el **nombre de sus técnicos** (en las dos colas: sale de la misma fila compartida); la pastilla **"auto"** significa "técnico elegido por el programa y aún no revisado": "Guardar cambios" sobre la glass la quita. La pastilla ya no marca el vínculo con la reparación: el vínculo es por IMEI.
6. **✕ en la reparación retira su glass** (esté como esté). **✕ en la glass desmarca la casilla** de la reparación de ese IMEI, y la reparación se queda. **Escanear a mano** una glass para un IMEI que está en Reparación **marca** la casilla de esa reparación; escanear una reparación de un IMEI que ya está en Glass **nace marcada**. Una glass sola, sin reparación en la cola, es válida y no dispara nada.
7. **Cambiar el cliente o el modelo** en la reparación después de nacer la glass se propaga a la glass por IMEI (mecanismos del bloque 1 y del cliente por IMEI) y **no** le quita la marca automática.
8. **Botones de cola con contadores**: cada botón (Reparación / Glass / Pulido) lleva a su derecha una pastilla con el número de entradas de esa cola; la pastilla va en rojo si esa cola tiene alguna entrada pendiente (roja, o pulido sin técnico) y en gris si no. Así se ve desde Reparación que en Glass ha nacido algo y por qué Guardar está bloqueado si nació roja. Con 0 entradas no hay pastilla.
9. **Guardar no cambia**: cada verde se persiste como hoy, con la regla de duplicado técnico+IMEI+categoría y el aviso de conflictos.
10. **Pulido no participa.** El mismo técnico puede ir en reparación y en glass del mismo IMEI (categorías distintas), como hoy.
11. **Cerrar el modal** descarta todo, incluido lo automático (nada de este bloque toca BD antes de Guardar; el modelo manual del bloque 1 sigue con su guardado temprano).

## 3. Habilitados para glass

### 3.1 Base de datos

Migración nueva `gestion-reparaciones-servidor/sql/migracion-glass-habilitados.sql`, con el patrón de `migracion-estadisticas-exclusion.sql` (vista previa → ALTER → verificación):

```sql
ALTER TABLE Tecnico ADD COLUMN ES_GLASS BOOLEAN NOT NULL DEFAULT FALSE;
```

- Aditiva, default a 0: **nadie habilitado hasta que el SuperTécnico lo marque**. Cero impacto en lo existente.
- `crear_bd.sql` en sync (columna tras `ES_ESTADISTICA`), `docs/schema.md` y `docs/api_contract.md` al día.
- MER (`Apuntes/Tabla BBDD(Corregido).drawio`, fuera del repo): `ES_ESTADISTICA` y `ES_GLASS` añadidas a `Tecnico` el 2026-09-07; el resto de tablas ya coincidía con `crear_bd.sql`.
- La aplica el usuario en preprod (vista previa → ALTER → verificación).

### 3.2 Servidor (rama `feature/glass-habilitados`)

- `model/Tecnico`: campo `esGlass` (boolean), constructor y getter `isEsGlass()`. `TecnicoDAO.MAPPER` lee `ES_GLASS`; `getAll` y `getAllActivos` lo seleccionan. Viaja en `GET /api/tecnicos` y `/api/tecnicos/activos` como `"esGlass": true|false`.
- **Endpoint** `PATCH /api/tecnicos/{idTec}/glass`, cuerpo `{ "habilitado": true | false }`, 204 sin cuerpo, en `TecnicoController`. `@PreAuthorize("hasRole('SUPERTECNICO')")` (ADMIN → 403; el rol más usado del servidor). Técnico inexistente → 404. Efecto: `UPDATE Tecnico SET ES_GLASS = ? WHERE ID_TEC = ?` (`TecnicoDAO.setGlass`). Log `HABILITAR_GLASS` / `DESHABILITAR_GLASS`, detalle `ID_TEC: n, NOMBRE: <nombre>` (nombre por `getNombreById`, como `eliminar`).
- Nada más cambia: la asignación de glass sigue entrando por `POST /api/glass/asignaciones`; la carga sigue calculándose en el cliente con `GET` de asignaciones abiertas y completadas hoy.
- **Arranque del contexto Spring verificado** tras el cambio (la suite del servidor no lo cubre).

### 3.3 Cliente: diálogo "Técnicos de glass"

- `models/Tecnico`: `Boolean esGlass` con `isEsGlass()` que devuelve `false` si el JSON no lo trae (servidor anterior). Constructor de 5 argumentos; los existentes delegan con `false`.
- `TecnicoDAO.setGlass(int idTec, boolean habilitado)` → el PATCH (errores como `SQLException` con el mensaje del servidor, patrón existente).
- **Botón "Técnicos de glass"** (`btn-secondary`) en la cabecera de la vista Asignaciones (`PendientesSuperTecnicoView.fxml`, fila del título, a la izquierda de "Carga técnicos"). Abre un `Dialog` calcado de `EstadisticasController.abrirModalTecnicos`:
  - Título "Técnicos de glass", cabecera "A quién se le asigna la glass automáticamente".
  - Una `CheckBox` por **técnico activo** (nombre), marcada según `isEsGlass()`.
  - Nota al pie: "Al marcar «Lleva glass» en una reparación, la glass va al técnico marcado aquí con menos carga hoy (cuentan sus reparaciones y sus glass). Si no hay ninguno, la glass queda pendiente para asignarla a mano."
  - Aceptar manda **solo los cambios** (un PATCH por técnico cambiado); si alguno falla, muestra el error y el diálogo no cierra. El modal de asignación carga los técnicos al abrirse (`getAllActivos`), así que ve el flag nuevo sin recargar la vista.
  - **Admin** (`setSoloLectura`): mismo botón y mismo diálogo, casillas deshabilitadas, solo "Cerrar". El servidor lo blinda igualmente (403).
- `LogController`: `HABILITAR_GLASS`, `DESHABILITAR_GLASS` en la lista de acciones del filtro, junto a las de estadísticas.
- **Etiqueta "glass"** en el modal de asignación: en la lista de técnicos (`etiquetaConCargaNodo`), una pastilla pequeña con la paleta del tipo Glass (`TipoTrabajo.GLASS.colorFondo()/colorTexto()`) junto al nombre de los habilitados, **solo cuando la cola activa es Glass** (en Reparación no aporta). No filtra ni marca nada: solo orienta al cambiar a mano una glass automática.

### 3.4 Compatibilidad

- Cliente 0.16.2 + servidor viejo: `esGlass` ausente → nadie habilitado → toda glass automática nace **roja** (fallback de la regla 3); el diálogo falla al aceptar (404) y lo dice.
- Servidor nuevo + cliente 0.16.1: campo ignorado por Gson; cero efecto.

## 4. Predicción: cómo elige el técnico (lógica pura)

Helper nuevo **`utils/PrediccionGlass`** (clase final con método estático, sin UI, con JUnit desde el principio; reutiliza los topes y `JORNADA_HORAS` de `CargaTecnicos`).

**Entrada:**
- técnicos activos del modal (`Tecnico` con `isEsGlass()`),
- asignaciones abiertas (`datos`) y completadas hoy (`cerradasHoy`) que la vista ya tiene cargadas (las mismas de "Carga técnicos"),
- entradas **verdes del modal** (`pilaRep` y `pilaGlass` con `asignada`): por cada una, sus técnicos, tipo, chasis y si tiene cliente,
- IMEI que se asigna y si **tiene cliente** (la entrada de reparación en el momento de Asignar: cliente de BD o elegido; "— Sin cliente —" explícito cuenta como sin cliente).

**Pasos:**
1. **Candidatos** = activos con `esGlass` que **no** tengan glass abierta de ese IMEI en BD (`tecnicosOcupados(imei, GLASS)`, la misma exclusión que hoy deshabilita casillas en la cola Glass) ni una glass verde de ese IMEI en el modal.
2. **Carga de cada candidato**, en **fracción de jornada de 9h sin escalar** (la misma fórmula de `CargaTecnicos.fraccion9h`: glass 1/17, chasis 1/8, normal 1/25, por cerrar 1/12 de su tipo, solicitud de pieza pendiente 0, pulido 0): hecho hoy + pendiente en BD **+ las entradas verdes del modal** (reparación y glass, sin guardar) que ya le tocaron, con su peso (normal 1/25, chasis 1/8, glass 1/17). **Alcance**: IMEI con cliente → **Pedidos** (solo asignaciones y verdes con cliente); IMEI sin cliente → **Total** (todo).
3. **Gana la menor carga**; empate → orden alfabético del nombre (`compareToIgnoreCase`). Sin candidatos → `null` (la glass nace roja).

**Por qué la fracción sin escalar y no el %:** el % de pantalla es fracción × 9/horas × 100; el factor es común a todos y no cambia el orden entre semana, pero en fin de semana la jornada es 0 y todos los % valen 0, con lo que la glass iría siempre al primero por alfabeto. Con la fracción cruda se reparte igual cualquier día. `CargaTecnicos.fraccion9h` es privada: se hace package-visible o se expone un equivalente estático (sin cambiar `calcularDia`).

**Qué no hace:** no mira a nadie no habilitado, no tiene memoria entre modales (cada apertura parte de la BD + lo verde del modal), no reordena nada ya asignado, no llama a la red.

## 5. Mecánica en el modal (cliente)

Anclajes al tip `f04e823` de `PendientesSuperTecnicoController.java` (reverificar al implementar):

- `EntradaAsignacion` (`:118`): campos nuevos `boolean llevaGlass` (solo tipo Reparación; se resetea entre IMEIs como `esChasis`) y `boolean auto` (solo tipo Glass; true = nacida por predicción y aún no editada a mano).
- `tecnicosOcupados(imei, tipo)` (`:1225`): se reutiliza para la exclusión de candidatos y para la regla 4 (glass abierta en BD → `!tecnicosOcupados(imei, GLASS).isEmpty()`; el nombre de la nota sale de la primera fila `AG` de `datos` con ese IMEI).
- `crearFilaPila` (`:1233`): para entradas verdes, tras el modelo, etiqueta con los nombres de `e.tecnicos` (unidos por ", ", dentro del `contenido` recortable); si `e.auto`, pastilla "auto" (fuente 9.5px, paleta del tipo Glass, `USE_PREF_SIZE`).
- `chkChasis` (`:1955`): debajo, `CheckBox chkLlevaGlass = new CheckBox("Lleva glass")`, mismo estilo; `Label lblGlassNota` (11px, gris) para "ya tiene glass: X". En `cargarEntrada` (`:2180`): visible/managed solo si `e.tipo == REPARACION`; `setSelected(e.llevaGlass)`; deshabilitada + nota si el IMEI tiene glass abierta en BD (y en ese caso `e.llevaGlass = false`).
- Lambdas nuevas antes de `asignarActual` (revisión invariante; todas capturan locales ya declarados): `glassDe(imei)` (entrada de ese IMEI en `pilaGlass` o `null`); `predecirGlass(g)` (si `g` no está asignada y no tiene técnicos: `PrediccionGlass.elegir(tecnicosModal, datos, cerradasHoy, verdesModal, g.imei, g.cliente != null)`; con técnico → `g.tecnicos = [t]`, `asignada = true`, `auto = true`; sin técnico, se queda roja); `crearGlassDe(e)` (si no existe: `tipo = GLASS`, `modeloCode = e.modeloCode` o, si falta, el de `modeloPorImei`, `cliente/sinCliente` de `e`, `comentario = ""`, `seq = ++seqCounter`, `modeloBuscado = true` (sin lookup: modelo y cliente vienen de la reparación), `pilaGlass.add`; si `e.asignada` → `predecirGlass`); `quitarGlassDe(imei)` (`pilaGlass.removeIf`); `vincularGlass(e)` (al escanear: sin glass abierta en BD, una reparación nace con `llevaGlass = glassDe(imei) != null` y una glass pone `llevaGlass = true` en la reparación de ese IMEI).
- `chkLlevaGlass.setOnAction` (solo clic del usuario, como `memorizarTecnicos`): `e = actual[0]` de tipo Reparación → `e.llevaGlass = isSelected()`; desmarcada → `quitarGlassDe(e.imei)`; marcada y `e.asignada` (edición de una verde) → `crearGlassDe(e)` (crea y predice); marcada en una roja → nada hasta Asignar; `renderPila`.
- `asignarActual`, tras el pegajoso, si `e.tipo == REPARACION`: `e.llevaGlass = isSelected() && !isDisabled()`; marcada → `crearGlassDe(e)` (por si venía marcada de nacimiento), `g = glassDe(e.imei)`, `if (!g.tieneModelo()) g.modeloCode = e.modeloCode`, `predecirGlass(g)`; desmarcada → `quitarGlassDe(e.imei)`. `renderPila` ya corre después.
- `asignarActual` para una entrada de tipo Glass: `e.auto = false`.
- `onRemove` en `renderPila` (rojos y verdes): reparación → `pilaGlass.removeIf(imei)`; glass → `llevaGlass = false` en la reparación de ese IMEI en `pilaRep`.
- `intentarAnadir` y el pegado múltiple: tras `sembrarModeloEntrada`, `vincularGlass(e)`.
- `tgTipo`/`selectorTipo` (`:1687-1703`, listener `:2444`): texto de los toggles con contador por cola, actualizado desde `renderPila` (lee `pilaRep`, `pilaGlass`, `lotePulido`); cifra en rojo (`-fx-text-fill` del rojo de `lblRojo`) si esa cola tiene rojas. Los toggles siguen siendo los mismos nodos (no se recrean).
- `etiquetaConCargaNodo` (`:1156`): parámetro extra `boolean mostrarGlass` (o variante) para la pastilla "glass"; en el modal se pasa `tipoActual[0] == GLASS && t.isEsGlass()`. Como los checkboxes se construyen una vez, el nodo de cada uno se refresca al cambiar de cola (en el listener de `tgTipo`, mismo sitio donde se limpia el detalle).
- Guardar (`:2467`): sin cambios (las automáticas verdes se persisten como cualquier glass verde).
- Pegajoso de la cola Glass (`defTecnicos.get(GLASS)`): **no** se toca al nacer una automática (no es una decisión del usuario).

## 6. Casos límite

- IMEI ya en la cola Glass (cualquier color) → no nace segunda entrada; la casilla queda marcada en la reparación sin efecto (regla 4).
- IMEI con glass abierta en BD → casilla deshabilitada con nota; no nace nada.
- Sin habilitados / todos excluidos → glass roja; contador "Glass (n)" en rojo; Guardar bloqueado hasta asignarla a mano o quitarla.
- Falla la carga de técnicos al abrir el modal (`tecnicosModal` vacío) → sin candidatos → roja.
- Otro usuario crea una glass del mismo IMEI+técnico entre abrir el modal y Guardar → aviso de duplicado de siempre; la reparación se crea igual.
- Desmarcar la casilla tras editar la glass a mano → la glass se va igualmente (casilla ⇔ glass, regla 2).
- ✕ en la reparación con glass manual → la glass se va igualmente (regla 6); ✕ en la glass → la reparación se queda y se desmarca.
- Cambio de cliente/modelo en la reparación tras nacer la glass → se propaga, sigue automática.
- Fin de semana → reparte por fracción cruda, sin empates artificiales.
- Servidor viejo → nadie habilitado → siempre roja; diálogo avisa.

## 7. Pruebas

- **Cliente**: JUnit de `PrediccionGlass` (TDD): sin habilitados → null; habilitado con glass abierta de ese IMEI → excluido; habilitado con glass verde de ese IMEI en el modal → excluido; menor carga gana; las verdes del modal desplazan la elección (dos glass seguidas van a técnicos distintos si empataban); IMEI con cliente ignora la carga sin cliente; IMEI sin cliente la cuenta; empate → alfabético; (la elección no recibe el día: compara fracciones sin escalar, así que el sábado no es un caso aparte; el smoke 16 lo verifica); inactivo con flag → fuera. Suite del cliente verde (191 en `f04e823`).
- **Servidor**: sin test nuevo (calca los endpoints de estadísticas, sin test). Arranque del contexto verificado (`mvn spring-boot:run` o test de contexto manual) antes de proponer el merge.
- **Smoke manual** (preproducción; SuperTécnico salvo donde se indica), incluye los casos límite del §6:
  1. Diálogo "Técnicos de glass": marcar dos, Aceptar, reabrir → persisten; `SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico` coincide; vista Log muestra `HABILITAR_GLASS` con el nombre. **Admin**: ve el botón y el diálogo, casillas deshabilitadas, solo Cerrar.
  2. Reparación con "Lleva glass" → Asignar → en Glass hay una verde "auto" con el mismo modelo y cliente, sin comentario, al técnico de menos carga; la fila muestra su nombre; pastilla "1" en el botón Glass.
  3. Seis IMEIs seguidos con glass → se reparten entre los habilitados (no van todos al mismo); el orden coincide con lo esperado por la carga.
  4. IMEI con cliente vs sin cliente → el elegido cambia según Pedidos/Total (comprobar contra la ventana "Carga técnicos" con cada toggle). Ojo: el % de pantalla va redondeado a entero y en fin de semana vale 0 para todos; dos técnicos con el mismo % no tienen por qué empatar para la predicción, que compara la fracción exacta.
  5. Sin habilitados (desmarcar todos) → glass roja, contador en rojo, Guardar bloqueado; asignarla a mano desbloquea; también quitarla con ✕.
  6. IMEI con glass ya abierta en BD → casilla deshabilitada con "ya tiene glass: X"; no nace nada.
  7. IMEI ya escaneado en Glass (roja) → escanearlo en Reparación → nace con la casilla marcada; Asignar → la roja de Glass se rellena con la predicción (verde "auto"); desmarcar → la glass desaparece.
  8. Marcar la casilla en una reparación **roja** → en Glass no aparece nada todavía; Asignar la reparación → la glass nace directamente verde "auto" (pastilla gris "1" en el botón Glass). Marcar al **editar una reparación verde** → la glass nace ya verde "auto" en el acto.
  9. Glass auto → "Guardar cambios" sobre ella → pierde "auto"; desmarcar la casilla en la reparación → la glass se va igualmente; volver a marcar y ✕ en la reparación → se va igualmente.
  10. Cambiar el cliente en la reparación con glass auto ya nacida → la glass muestra el nuevo cliente y sigue "auto".
  11. Modelo elegido a mano (lookup fallido) + "Lleva glass" → la glass nace con ese modelo, sin "Buscando…".
  12. Etiqueta "glass" visible en la lista de técnicos solo en la cola Glass, junto a los habilitados; puntos de carga intactos.
  13. Guardar → dos asignaciones en la tabla (A y AG) con sus técnicos; regla de duplicado intacta (repetir el mismo lote → conflictos); sin regresión en pegajosos por cola, pulido ni modelo vivo.
  14. Cerrar el modal con glass auto sin guardar → nada en BD (salvo el modelo manual del bloque 1).
  15. Pegajoso de Glass: tras nacer una auto para javi, escanear un IMEI en Glass a mano → no propone a javi por la auto (solo el último asignado a mano en esa cola).
  16. Sábado (o simular jornada 0 en test): reparte entre habilitados, no siempre al primero.
  17. ✕ en la glass (auto o manual) → la reparación sigue en su cola y su casilla aparece desmarcada al cargarla; volver a marcarla → la glass nace de nuevo (roja o verde "auto" según esté la reparación). Asignar la glass a mano mientras la reparación está roja y después Asignar la reparación → la glass no cambia. Escanear una glass a mano de un IMEI que está en Reparación → esa reparación aparece marcada.
  19. (smoke 2026-09-07) Dos reparaciones con glass auto a `test`; cambiar el técnico de las reparaciones a `test` con "Guardar cambios" → las glass auto se recalculan y pasan a `supertest` (test ya carga 2/25 + glass). Editar una glass a mano y reeditar su reparación → esa glass no cambia.
  18. (review final) Con 4+ verdes en una cola, la lista verde (filas a dos líneas) hace scroll antes que antes; si se queda corta, subir el alto máximo del scroll verde.

## 8. Fuera de alcance

- Predicción sin marca manual (inferencia): descartada; la casilla es la decisión.
- Cambiar tipo rep↔glass desde Asignaciones; asignación masiva; paridad pulido (backlog `project_backlog_mejoras_asignaciones`).
- Topes de carga por técnico o configurables (F4).
- Glass automática desde otras pantallas (Mis pendientes, Agrupado): solo el modal de asignación.
- Deshacer automáticamente una glass ya guardada en BD.

## 9. Entregables

- **Servidor** (`feature/glass-habilitados`): migración + `crear_bd.sql` + `schema.md` + `api_contract.md`; modelo/DAO/controller; log. Merge a `main`, despliegue en preprod y migración **los ejecuta el usuario** (Claude prepara comandos; no hace SSH). Después, cliente.
- **Cliente** (`feature/glass-prediccion`): `PrediccionGlass` + tests; `Tecnico.esGlass` + `TecnicoDAO.setGlass`; diálogo + botón + solo lectura; cambios del modal (§5); Log; `CHANGELOG.md` `[Unreleased]`: Added "Glass automática al marcar «Lleva glass» en la reparación (técnico por carga)", Added "Diálogo Técnicos de glass en Asignaciones"; Changed "Botones de cola del modal con contadores".
- Merge `--no-ff` a `hotfix/0.16.2` **solo con OK del usuario**; gitlink y tag en la release 0.16.2.
