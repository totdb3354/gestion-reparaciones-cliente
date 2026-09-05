# Facilitar la asignación de glass — bloque 2: glass automática al asignar la reparación (0.16.2)

Fecha: 2026-09-05
Estado: **APROBADA por el usuario (brainstorm en sesión, 2026-09-05).** Pendiente: plan de implementación (writing-plans) y ejecución subagent-driven.
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
2. **Al pulsar "Asignar →"** en Reparación con la casilla marcada nace en la cola Glass una entrada del mismo IMEI. Las validaciones son **las mismas de Asignar** (modelo elegido + algún técnico de reparación): no hay validación extra para la glass, el técnico de glass lo pone la predicción. La entrada nace con el **mismo modelo** (modelo vivo del bloque 1) y el **mismo cliente / sin-cliente** de la reparación, **sin comentario** (el comentario son instrucciones para el técnico de reparación), sin chasis, sin urgente.
3. **Nace verde y marcada como automática** cuando la predicción elige técnico. **Nace roja** (sin técnico, pendiente) cuando la predicción no puede elegir (ningún habilitado activo, o todos los habilitados excluidos por ya tener glass de ese IMEI). Una roja bloquea Guardar como cualquier otra, hasta que se resuelva a mano.
4. **No nace** si ese IMEI ya está en la cola Glass (roja o verde): no se crea otra ni se toca la existente. **La casilla se deshabilita** con la nota "ya tiene glass: <técnico>" cuando el IMEI ya tiene una glass abierta en BD (visible en la tabla de Asignaciones): el teléfono ya está cubierto.
5. **En la cola Glass**, las filas verdes muestran además el **nombre de sus técnicos**; las automáticas llevan una pastilla **"auto"**. Cargar una automática, cambiar lo que sea y "Guardar cambios" la convierte en manual (pierde la pastilla y ya no la retira nadie más que el usuario).
6. **Deshacer**: editar la reparación verde ("Guardar cambios") con la casilla **desmarcada**, o quitar la reparación de la cola con ✕, **retira su glass automática** si sigue siendo automática; si ya es manual, se queda. Marcar la casilla al editar una reparación verde que no la tenía crea la glass entonces (regla 2). Quitar la glass con ✕ no toca la reparación.
7. **Cambiar el cliente o el modelo** en la reparación después de nacer la glass se propaga a la glass por IMEI (mecanismos del bloque 1 y del cliente por IMEI) y **no** le quita la marca automática.
8. **Botones de cola con contadores**: "Reparación (3)", "Glass (2)", "Pulido (1)"; la cifra va en rojo si esa cola tiene alguna entrada pendiente (roja). Así se ve desde Reparación que en Glass ha nacido algo y por qué Guardar está bloqueado si nació roja. Con 0 entradas no se muestra la cifra.
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
- MER (`Apuntes/Tabla BBDD(Corregido).drawio`): pendiente junto con `ES_ESTADISTICA` (dos columnas en `Tecnico`), en la misma nota que ya lleva el usuario para la release.
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
  - Nota al pie: "Al marcar «Lleva glass» en una reparación, la glass va al técnico marcado aquí con menos carga de glass hoy. Si no hay ninguno, la glass queda pendiente para asignarla a mano."
  - Aceptar manda **solo los cambios** (un PATCH por técnico cambiado); si alguno falla, muestra el error y el diálogo no cierra. Tras aceptar, recarga los técnicos de la vista (`tecnicos`) para que el modal de asignación vea el flag nuevo.
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
- glass **verdes del modal** (`pilaGlass` con `asignada`): por cada una, sus técnicos y si tiene cliente,
- IMEI que se asigna y si **tiene cliente** (la entrada de reparación en el momento de Asignar: cliente de BD o elegido; "— Sin cliente —" explícito cuenta como sin cliente).

**Pasos:**
1. **Candidatos** = activos con `esGlass` que **no** tengan glass abierta de ese IMEI en BD (`tecnicosOcupados(imei, GLASS)`, la misma exclusión que hoy deshabilita casillas en la cola Glass) ni una glass verde de ese IMEI en el modal.
2. **Carga de cada candidato**, en **fracción de jornada de 9h sin escalar** (la misma fórmula de `CargaTecnicos.fraccion9h`: glass 1/17, chasis 1/8, normal 1/25, por cerrar 1/12 de su tipo, solicitud de pieza pendiente 0, pulido 0): hecho hoy + pendiente en BD **+ las glass verdes del modal** que ya le tocaron (1/17 cada una). **Alcance**: IMEI con cliente → **Pedidos** (solo asignaciones y verdes con cliente); IMEI sin cliente → **Total** (todo).
3. **Gana la menor carga**; empate → orden alfabético del nombre (`compareToIgnoreCase`). Sin candidatos → `null` (la glass nace roja).

**Por qué la fracción sin escalar y no el %:** el % de pantalla es fracción × 9/horas × 100; el factor es común a todos y no cambia el orden entre semana, pero en fin de semana la jornada es 0 y todos los % valen 0, con lo que la glass iría siempre al primero por alfabeto. Con la fracción cruda se reparte igual cualquier día. `CargaTecnicos.fraccion9h` es privada: se hace package-visible o se expone un equivalente estático (sin cambiar `calcularDia`).

**Qué no hace:** no mira a nadie no habilitado, no tiene memoria entre modales (cada apertura parte de la BD + lo verde del modal), no reordena nada ya asignado, no llama a la red.

## 5. Mecánica en el modal (cliente)

Anclajes al tip `f04e823` de `PendientesSuperTecnicoController.java` (reverificar al implementar):

- `EntradaAsignacion` (`:118`): campos nuevos `boolean llevaGlass` (solo tipo Reparación; se resetea entre IMEIs como `esChasis`) y `boolean auto` (solo tipo Glass; true = nacida por predicción y aún no editada a mano).
- `tecnicosOcupados(imei, tipo)` (`:1225`): se reutiliza para la exclusión de candidatos y para la regla 4 (glass abierta en BD → `!tecnicosOcupados(imei, GLASS).isEmpty()`; el nombre de la nota sale de la primera fila `AG` de `datos` con ese IMEI).
- `crearFilaPila` (`:1233`): para entradas verdes, tras el modelo, etiqueta con los nombres de `e.tecnicos` (unidos por ", ", dentro del `contenido` recortable); si `e.auto`, pastilla "auto" (fuente 9.5px, paleta del tipo Glass, `USE_PREF_SIZE`).
- `chkChasis` (`:1955`): debajo, `CheckBox chkLlevaGlass = new CheckBox("Lleva glass")`, mismo estilo; `Label lblGlassNota` (11px, gris) para "ya tiene glass: X". En `cargarEntrada` (`:2180`): visible/managed solo si `e.tipo == REPARACION`; `setSelected(e.llevaGlass)`; deshabilitada + nota si el IMEI tiene glass abierta en BD (y en ese caso `e.llevaGlass = false`).
- `asignarActual` (`:2261`): tras fijar `e.asignada = true` y el pegajoso, si `e.tipo == REPARACION`:
  - `e.llevaGlass = chkLlevaGlass.isSelected() && !chkLlevaGlass.isDisabled()`;
  - si `llevaGlass` y no hay entrada de ese IMEI en `pilaGlass`: crear `EntradaAsignacion g` con `tipo = GLASS`, `modeloCode = e.modeloCode`, `cliente/sinCliente = e.cliente/e.sinCliente`, `comentario = ""`, `seq = ++seqCounter`, `modeloBuscado = true` (no relanzar el lookup: el modelo ya viene de la reparación, y al Asignar la reparación su cliente ya es decisión manual en `clienteManual`, así que la precarga de cliente de BD no aportaría nada), `auto = true`; técnico = `PrediccionGlass.elegir(...)`; si no es `null`, `g.tecnicos = [t]`, `g.asignada = true`; si es `null`, `g.asignada = false` (roja); `pilaGlass.add(g)`.
  - si `!llevaGlass` (edición de una verde que desmarca) y hay en `pilaGlass` una entrada de ese IMEI con `auto == true`: quitarla.
  - `renderPila` ya corre después.
- `asignarActual` para una entrada de tipo Glass (edición "Guardar cambios" de una automática): `e.auto = false`.
- `onRemove` de una reparación (`renderPila`, `:2047`): además de quitarla de su pila, quitar de `pilaGlass` la entrada de ese IMEI con `auto == true`, si existe.
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
- Desmarcar la casilla tras editar la glass a mano → la glass se queda (ya es manual).
- ✕ en la reparación con glass manual → la glass se queda.
- Cambio de cliente/modelo en la reparación tras nacer la glass → se propaga, sigue automática.
- Fin de semana → reparte por fracción cruda, sin empates artificiales.
- Servidor viejo → nadie habilitado → siempre roja; diálogo avisa.

## 7. Pruebas

- **Cliente**: JUnit de `PrediccionGlass` (TDD): sin habilitados → null; habilitado con glass abierta de ese IMEI → excluido; habilitado con glass verde de ese IMEI en el modal → excluido; menor carga gana; las verdes del modal desplazan la elección (dos glass seguidas van a técnicos distintos si empataban); IMEI con cliente ignora la carga sin cliente; IMEI sin cliente la cuenta; empate → alfabético; jornada 0 (sábado) reparte igual que un martes; inactivo con flag → fuera. Suite del cliente verde (191 en `f04e823`).
- **Servidor**: sin test nuevo (calca los endpoints de estadísticas, sin test). Arranque del contexto verificado (`mvn spring-boot:run` o test de contexto manual) antes de proponer el merge.
- **Smoke manual** (preproducción; SuperTécnico salvo donde se indica), incluye los casos límite del §6:
  1. Diálogo "Técnicos de glass": marcar dos, Aceptar, reabrir → persisten; `SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico` coincide; vista Log muestra `HABILITAR_GLASS` con el nombre. **Admin**: ve el botón y el diálogo, casillas deshabilitadas, solo Cerrar.
  2. Reparación con "Lleva glass" → Asignar → en Glass hay una verde "auto" con el mismo modelo y cliente, sin comentario, al técnico de menos carga; la fila muestra su nombre; "Glass (1)" en el botón de cola.
  3. Seis IMEIs seguidos con glass → se reparten entre los habilitados (no van todos al mismo); el orden coincide con lo esperado por la carga.
  4. IMEI con cliente vs sin cliente → el elegido cambia según Pedidos/Total (comprobar contra la ventana "Carga técnicos" con cada toggle).
  5. Sin habilitados (desmarcar todos) → glass roja, contador en rojo, Guardar bloqueado; asignarla a mano desbloquea; también quitarla con ✕.
  6. IMEI con glass ya abierta en BD → casilla deshabilitada con "ya tiene glass: X"; no nace nada.
  7. IMEI ya escaneado en Glass (roja) → marcar la casilla en Reparación y Asignar → no nace segunda entrada, la roja sigue intacta.
  8. Editar la glass auto en Glass ("Guardar cambios") → pierde "auto"; luego desmarcar la casilla en la reparación → la glass se queda; ✕ en la reparación → la glass se queda.
  9. Glass auto sin tocar → desmarcar la casilla (editar la reparación verde) → desaparece; otra vez con ✕ en la reparación → desaparece.
  10. Cambiar el cliente en la reparación con glass auto ya nacida → la glass muestra el nuevo cliente y sigue "auto".
  11. Modelo elegido a mano (lookup fallido) + "Lleva glass" → la glass nace con ese modelo, sin "Buscando…".
  12. Etiqueta "glass" visible en la lista de técnicos solo en la cola Glass, junto a los habilitados; puntos de carga intactos.
  13. Guardar → dos asignaciones en la tabla (A y AG) con sus técnicos; regla de duplicado intacta (repetir el mismo lote → conflictos); sin regresión en pegajosos por cola, pulido ni modelo vivo.
  14. Cerrar el modal con glass auto sin guardar → nada en BD (salvo el modelo manual del bloque 1).
  15. Pegajoso de Glass: tras nacer una auto para javi, escanear un IMEI en Glass a mano → no propone a javi por la auto (solo el último asignado a mano en esa cola).
  16. Sábado (o simular jornada 0 en test): reparte entre habilitados, no siempre al primero.

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
