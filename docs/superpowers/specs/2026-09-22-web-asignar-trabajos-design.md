# Sub-proyecto 3b — El modal "Asignar trabajos" y la predicción de glass en el servidor

**Fecha:** 2026-09-22
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; las decisiones generales están allí y no se repiten aquí). Antecesor directo: [Asignaciones del supertécnico (3a)](2026-09-22-web-asignaciones-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-asignar-trabajos`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`)

## 1. Objetivo

Activar el botón **"Asignar"** de la vista de asignaciones, que el 3a dejó
deshabilitado, y abrir con él el modal real: **tres colas** (Reparación, Glass,
Pulido), escaneo y pegado de IMEIs, modelo y cliente por IMEI, técnicos
"pegajosos", chasis, **"Lleva glass" con la glass automática** y guardado de todo
el lote al final.

En el servidor suben dos cosas: la **predicción de glass** (`PrediccionGlass`,
con su suite) y un **guardado por lotes atómico con `Idempotency-Key`**, que
sustituye las N peticiones sueltas del JavaFX.

Con esto, el supertécnico deja de necesitar el JavaFX para crear asignaciones.
Entrega: web **v0.5.0**.

Por fuera el modal queda como el del JavaFX; las únicas diferencias son las seis
de la §10. Referencia: `hotfix/0.16.3` (solo lectura), con paridad verificada
contra las capturas del modal y el inventario de su código, ambos guardados fuera
del repo porque llevan datos reales del taller.

## 2. Fuera de alcance

- Borrador persistente del modal: como en el JavaFX, cerrar o perder la sesión
  pierde el lote.
- Mover el % de carga de la lista de técnicos a medida que se asigna dentro del
  modal: sigue siendo el de la última carga de la tabla, como hoy.
- Bloquear el duplicado de Pulido al seleccionar (D4).
- Marcar urgente al crear: lo sigue haciendo el job por vencimiento o el menú de
  la tabla.
- Cambiar el tipo reparación ↔ glass de una asignación: sigue en el backlog.
- Los endpoints de alta sueltos (`POST /reparaciones/asignaciones`,
  `/glass/asignaciones`, `/pulidos/asignaciones`) no se tocan: el JavaFX los
  sigue usando.

## 3. Decisiones de este sub-proyecto

| # | Decisión | Por qué |
|---|---|---|
| D1 | **Guardado por lotes en una transacción, con red**: una asignación que ya existe (técnico + IMEI + categoría) se salta y se informa; el resto se crea. Un error real deshace el lote entero | Es el comportamiento que la tienda conoce (el aviso "Algunas asignaciones no se crearon"), sin el hueco actual de un guardado a medias |
| D2 | **La predicción se pide en el momento**, al pulsar "Asignar →" con "Lleva glass" (o al marcarla en una verde), y la glass nace verde con "auto" y el técnico visible | El supertécnico ve a quién va la glass y la corrige antes de guardar, como hoy |
| D3 | **Se calca el guardado inmediato del modelo** al decidirlo a mano (`POST /api/telefonos` en segundo plano, errores ignorados) | Función deliberada del JavaFX (2026-09-05): el modelo es un dato cierto del teléfono y guardarlo evita repetir la elección aunque el lote se descarte; el lote lo vuelve a mandar |
| D4 | **Pulido se calca**: su combo de técnico no deshabilita a quien ya tiene ese IMEI en pulido; el duplicado lo frena el lote (D1) | Decisión del usuario. La regla del duplicado es técnico + IMEI + categoría y en Reparación y Glass ya se aplica al seleccionar |
| D5 | El estado del modal es un **reductor puro partido por temas** (colas, cliente, glass, pulido, derivados), con los efectos asíncronos en hooks | Las reglas cruzan colas; separadas y con suite propia no se pierde ninguna al portar, y no se repite el `estado.ts` de 1.097 líneas del sub-proyecto 2 |
| D6 | Cuatro discrepancias menores del JavaFX: se corrige el subtítulo (el comentario no se arrastra); IMEI incompleto + Enter sin mensaje y con los dígitos en el campo; el cliente inactivo de la BD se muestra también en Reparación y Glass, como en Pulido; el pegado sigue sin cargar el detalle en Reparación y Glass | Acordadas una a una en el brainstorming |

## 4. Servidor (rama `feature/web-asignar-trabajos`; el JavaFX 0.16.x sigue funcionando)

Todo es **aditivo**.

### 4.1 `POST /api/glass/prediccion` — rol SUPERTECNICO

Cuerpo:

```json
{ "imei": "…", "conCliente": true,
  "verdes": [ { "imei": "…", "idTec": 3, "tipo": "REPARACION", "esChasis": false, "conCliente": true } ] }
```

Respuesta: `{ "idTec": 5, "nombre": "…" }`, o `{ "idTec": null, "nombre": null }` si
no hay candidato.

Carga lo mismo que `GET /reparaciones/carga-tecnicos` del 3a: abiertas de las
**tres** categorías, cerradas hoy (si esa consulta falla, degrada a solo-pendiente,
como allí) y técnicos activos. Después llama a `PrediccionGlass.elegir`, portada
**sin cambios de comportamiento** junto con sus **16 tests**, reutilizando
`CargaTecnicos.fraccion9h` y las constantes de tope que ya están en el servidor.

Reglas que se portan (spec original `2026-09-05-glass-prediccion` §4): candidatos
activos con `esGlass` sin glass abierta de ese IMEI, ni en BD ni verde en el modal;
gana la menor carga en fracción de jornada de 9 h **sin escalar**, sumando lo hecho
hoy, lo abierto y las verdes del modal (reparación 1/25, chasis 1/8, glass 1/17);
alcance Pedidos si el IMEI tiene cliente y Total si no; empate redondeado a 1e-9 y
desempate alfabético sin distinguir mayúsculas; sin candidato → nulo.

Es un `POST` porque necesita las verdes del modal en el cuerpo, pero **no escribe
nada**, así que no lleva clave de idempotencia.

### 4.2 `POST /api/asignaciones/lote` — rol SUPERTECNICO, `Idempotency-Key` obligatoria

Cuerpo:

```json
{ "telefonos":    [ { "imei": "…", "modelo": "…", "idCli": 12, "clienteExplicito": false } ],
  "asignaciones": [ { "imei": "…", "categoria": "R", "idTec": 3, "comentario": null, "esChasis": false } ] }
```

Un teléfono **por IMEI** (no uno por entrada, como hace el JavaFX) y una asignación
por técnico. `categoria` es `R`, `G` o `P`.

Pasos:

0. **Sin cabecera `Idempotency-Key` → 400.** El registro no interviene cuando no
   hay clave, así que la obligatoriedad la comprueba el propio endpoint.
1. **Validación (422):** categoría válida, IMEI de 15 dígitos, técnico activo, modelo
   obligatorio para los IMEIs con asignaciones `R` o `G` (la misma regla que el botón
   "Asignar →"), y cada IMEI de `asignaciones` presente en `telefonos`.
2. **Fuera de la transacción:** para los IMEIs de pulido sin modelo en BD, el lookup
   de modelo que hoy hace `POST /pulidos/asignaciones`. Es una llamada externa y
   lenta, y no debe retener la conexión de la transacción.
3. **En una transacción:**
   - upsert de cada teléfono con la SQL de siempre (`COALESCE` para modelo y, sin
     `clienteExplicito`, para el cliente) y sus logs `ASIGNAR_CLIENTE` /
     `QUITAR_CLIENTE`;
   - por cada asignación, comprobar si el técnico ya tiene una abierta de ese IMEI en
     esa categoría: si la tiene, va a `conflictos` y se salta (D1); si no, se inserta
     con los DAO existentes, `urgente = false`, el autor tomado del token, y el mismo
     log que hoy (`CREAR_ASIGNACION`, `CREAR_ASIGNACION_GLASS` o
     `CREAR_ASIGNACION_PULIDO`).
4. Respuesta `200`:

```json
{ "creadas":    [ { "idRep": "…", "imei": "…", "idTec": 3, "categoria": "R" } ],
  "conflictos": [ { "imei": "…", "idTec": 5, "nombreTecnico": "…", "categoria": "G" } ] }
```

Un error a mitad deshace todo. Un reintento con la misma clave devuelve la
respuesta guardada sin repetir nada (`RegistroIdempotencia`, ya en el servidor desde
el sub-proyecto 2).

### 4.3 Lo que ya existe y se reutiliza

`GET /api/telefonos/{imei}/modelo` (con el lookup de IMEI en el servidor),
`GET /api/telefonos/{imei}/cliente` y `POST /api/telefonos` (el guardado inmediato
del modelo, D3). Cada uno se verifica contra el contrato OpenAPI antes de
planificarlo.

## 5. Web

```
modules/taller/asignaciones/modal/
├── AsignarTrabajosDialog.tsx   cabecera, toggle con pastillas, centro, barra final, cerrar con "Descartar"
├── api.ts                      predicción, guardado del lote, lookups de modelo y cliente, guardado del modelo
├── estado/
│   ├── tipos.ts                Entrada, FilaPulido, EstadoModal, acciones
│   ├── colas.ts                escanear, pegar, quitar, cargar, asignar, pegajosos por cola, siguiente roja
│   ├── cliente.ts              precedencia manual → BD → pegajoso, propagación a las tres colas
│   ├── glass.ts                invariante casilla ⇔ glass, crear, retirar, aplicar la predicción, "auto"
│   ├── pulido.ts               filas de pulido, técnico de arriba, detalle
│   ├── derivados.ts            contadores, progreso, Guardar habilitado, técnicos ocupados, verdes para la predicción
│   └── reductor.ts             solo compone
├── lote.ts                     estado → cuerpo del POST por lotes (puro)
├── PanelRico.tsx               escaneo, listas roja y verde, detalle (Reparación y Glass)
├── FilaCola.tsx                fila de la lista
├── DetalleEntrada.tsx          modelo, técnicos, cliente, comentario, chasis, "Lleva glass", Asignar →
├── PanelPulido.tsx             técnico de arriba, escaneo, lista y detalle
└── ListaTecnicos.tsx           checks con nombre ● % Pedidos, pastilla "glass", deshabilitados y "N asignados"
```

Piezas nuevas compartidas:

- `shared/lib/pegadoImei.ts`: port de `parsearPegadoImeis` (incompleto, único, lote,
  corrupto) con sus casos.
- `shared/ui/CampoAutocompletar.tsx`: el campo navy con popup (máximo 6 filas
  visibles, filtro "contiene" sin mayúsculas, Enter elige el primero salvo que el
  texto ya sea el elegido o esté vacío, al salir decide la coincidencia exacta o
  restaura). Lo usan el modelo y el cliente.

Se reutilizan `modelos.ts`, `useCargaTecnicos`, `ConfirmDialog`, `TogglePill`,
`PildoraContador`, `BadgeTipo`, `AlertaProvider`, `useInteraccionesAbiertas` y
`crearClavesIdempotencia` del formulario. Antes de cada tarea se busca en el repo
lo que va a crear, por si ya existe.

**Datos al abrir:** técnicos activos, clientes (todos, con su estado: el buscador
solo ofrece activos, pero un cliente inactivo de la BD se muestra, D6) y la tabla
de asignaciones ya cargada, **congelada mientras el modal está abierto**. De ella
salen los técnicos ocupados y el "ya tiene glass: X", igual que en el JavaFX.

## 6. Comportamiento

El inventario del código es la referencia de detalle. Aquí va lo que define el
modal.

**Estructura.** Título "Asignar trabajos" y subtítulo *"Elige el tipo, escanea IMEIs
y configúralos. Los técnicos se mantienen entre IMEIs. Se guardan todos al final."*
(D6). Toggle Reparación | Glass | Pulido, con Reparación por defecto, sin poder
deseleccionar y con una pastilla por cola: número de entradas, roja si hay alguna
pendiente y gris si no. Cambiar de cola vacía el detalle; las tres colas persisten.
Barra final con el progreso y **"Guardar (N)"**.

**Reparación y Glass.** Campo de escaneo solo de dígitos: a los 15 se añade sin
Enter; repetido en la cola activa → *"Ese IMEI ya está en la cola (Reparación)."*;
el mismo IMEI puede estar en las dos colas. El pegado se trocea en bloques de 15:
si no cuadra, *"Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son
válidos."*; si cuadra, *"N IMEIs añadidos."* o *"… · M ya estaban en la lista."*. A la
izquierda, **"Pendiente de asignar (N)"** y **"Asignados (N) · sin guardar"**, ordenadas
de lo último escaneado a lo primero; cada fila con IMEI, badge Rep/Glass, pastilla
de modelo (nombre, "Buscando…" o "⚠ falta modelo"), en las verdes "auto" y los
técnicos, y una ✕. A la derecha, el detalle, deshabilitado sin entrada cargada.

**Detalle.** Modelo con autocompletado y lookup; técnicos con el % de Pedidos,
pastilla "glass" en la cola Glass, y los ocupados deshabilitados con "N asignado(s)";
cliente con autocompletado y "— Sin cliente —"; comentario; "Reparación de chasis" y
"Lleva glass" solo en Reparación ("ya tiene glass: X" si hay glass abierta). "Asignar →"
exige modelo y al menos un técnico; en una verde, el botón dice **"Guardar cambios"**.
Los técnicos son pegajosos **por cola** y se memorizan al marcarlos; comentario y
chasis **no** se arrastran.

**Cliente.** Precedencia: decisión manual del IMEI → cliente del IMEI en BD →
cliente pegajoso del modal (uno solo para todo el modal) → vacío. Elegir un cliente
en cualquier cola lo propaga a todas las entradas de ese IMEI en las tres.

**Modelo.** Pertenece al IMEI: una decisión manual se propaga a las dos colas, se
recuerda para los IMEIs que se escaneen después y se guarda al momento (D3).

**Glass automática.** Casilla marcada ⇔ glass de ese IMEI en la cola Glass, en los
dos sentidos. En una roja, la casilla es intención hasta "Asignar →"; en una verde,
crea la glass y la predice al momento. La glass nace con el modelo y el cliente de
la reparación y sin comentario. Mientras llega la predicción, "Calculando…"; con
candidato, verde y "auto"; sin candidato o si falla, roja. Una glass "auto" se
recalcula al reasignar su reparación; editarla a mano le quita "auto". Quitar la
reparación retira su glass (salvo que haya glass abierta en BD); quitar la glass
desmarca la casilla.

**Pulido.** Combo "Técnico (se aplica a los IMEIs que escanees)"; lista "Nada añadido
aún" / "N en pulido" con *"<técnico> · <cliente>"* o *"(sin técnico) · …"* en rojo;
detalle con técnico, cliente y comentario, sin botón. El pegado sí selecciona la
última fila. Sin modelo, sin chasis, sin "Lleva glass" y sin bloqueo del duplicado (D4).

**Guardar (N).** N = verdes de Reparación y Glass + filas de pulido. Deshabilitado si
hay alguna roja, algún pulido sin técnico o N = 0. Progreso: *"N configurados · M
pendientes"*, más *" · K pulido"* y *" · J sin modelo"* cuando corresponde.

**Cerrar.** Con entradas en alguna cola, la ✕, Esc o un clic fuera abren
"Descartar" / *"Se descartarán los N IMEIs escaneados."*; sin entradas, se cierra
directamente.

## 7. Guardado

1. `lote.ts` convierte el estado en el cuerpo de §4.2 (función pura).
2. La clave sale de `crearClavesIdempotencia`: mismo cuerpo, misma clave; si el lote
   cambia entre dos intentos, clave nueva.
3. Mientras se envía: "Guardando…", botón deshabilitado y el modal no se cierra.
4. **Éxito:** se cierra el modal, se invalidan las consultas de asignaciones y de
   carga (la tabla y el contador del lateral se refrescan) y, si hay conflictos, se
   avisa *"Algunas asignaciones no se crearon:"* con líneas `• <imei> → <técnico> (ya
   asignado · <categoría>)`.
5. **Fallo:** el modal sigue abierto con todo el lote y el error pasa por el mapeo
   común (banner, o el mensaje del 422). Reintentar usa la misma clave: el lote se
   deshizo entero o ya quedó hecho, así que no duplica ni muestra conflictos falsos.

## 8. Errores y refresco

- **Predicción fallida:** la glass queda roja y aparece el aviso *"No se pudo calcular
  la glass automática; asígnala a mano."*. La respuesta solo se aplica si esa glass
  sigue existiendo, sigue "auto" o sin técnicos, y el IMEI es el mismo.
- **Lookups** de modelo y cliente: errores tragados, como hoy; el modelo queda "⚠ falta
  modelo" con el prompt *"No encontrado — selecciona manualmente"*. Sus respuestas
  solo se aplican si la entrada sigue ahí.
- **Guardado inmediato del modelo:** errores ignorados; el lote lo vuelve a mandar.
- La tabla de detrás no se refresca mientras el modal está abierto; se refresca al
  guardar.
- **401** con el modal abierto: al login, como en toda la web; el lote se pierde.

## 9. Tests y verificación

- **Servidor:** los 16 tests de `PrediccionGlass` portados; los del endpoint de
  predicción (roles, sin candidato, degradación a solo-pendiente); los del lote
  (roles, clave obligatoria, reintento idempotente, conflicto saltado, rollback ante un
  error a mitad, validaciones y que el lookup de pulido ocurre fuera de la
  transacción). La suite no tiene test de contexto de Spring: el arranque se
  comprueba a mano tras el cambio de wiring.
- **Web:** una suite por fichero de `estado/` y para `lote.ts`, `pegadoImei.ts` y
  `CampoAutocompletar`; componentes con Testing Library y MSW, con un test del flujo
  completo (escanear → asignar → glass automática → guardar → aviso de conflictos) y
  otro del reintento con la misma clave.
- **Trazabilidad:** el plan enlaza cada regla del inventario del modal con el test que
  la cubre.
- **Smoke** Playwright contra producción, en serie: abrir el modal, escanear un IMEI
  de prueba, asignarlo a un usuario de prueba y guardar; después, borrarlo desde la
  tabla.
- **Paridad antes del tag:** ficha `docs/paridad/asignar-trabajos.md`, marcada contra
  las capturas del modal y referenciándolas solo por nombre; y **capturas de la web
  comparadas con las del JavaFX, lado a lado, antes de `v0.5.0`**.

## 10. Diferencias y calcos

| Asunto | Decisión |
|---|---|
| Subtítulo | **Diferencia**: *"Los técnicos se mantienen entre IMEIs"* (D6) |
| Cliente inactivo de la BD | **Diferencia**: se muestra también en Reparación y Glass (D6) |
| Guardado | **Diferencia**: atómico y con clave; un fallo no deja nada a medias (D1) |
| Predicción | **Diferencia**: se calcula con la BD del momento, no con la tabla de hace hasta 60 s (D2) |
| Esc y clic fuera | **Diferencia**: piden "Descartar" (diálogo web) |
| "Calculando…" y aviso de predicción fallida | **Diferencia**: hay un paso por red nuevo |
| Guardado inmediato del modelo | **Calco** (D3) |
| Duplicado en Pulido | **Calco**: solo lo frena el guardado (D4) |
| IMEI incompleto + Enter | **Calco** del código: sin mensaje y con los dígitos en el campo (D6) |
| Pegado en Reparación y Glass | **Calco**: no carga el detalle (D6) |
| Aviso de conflictos | **Calco** del texto |

Cualquier diferencia nueva que aparezca al comparar capturas se decide con el
usuario, no sobre la marcha.

## 11. Criterios de cierre

1. El botón "Asignar" abre el modal real y el ADMIN sigue sin verlo.
2. Las tres colas se comportan como la ficha, glass automática incluida.
3. La predicción sale del servidor y respeta las reglas portadas.
4. El lote se guarda de forma atómica; un reintento no duplica; los conflictos se
   avisan.
5. Suites en verde en los tres repos (el cliente JavaFX sin tocar) y smoke en verde.
6. Ficha marcada y capturas comparadas lado a lado.
7. Desplegado en la VDC, servidor antes que web, con el contrato publicado idéntico
   al que consume la web. Push, merge, tag y despliegue, cada uno con el OK del
   usuario.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Se pierde alguna regla del JavaFX al portar el modal (~990 líneas de lambdas) | Reductor partido por temas con suite propia y tabla de trazabilidad regla → test (D5) |
| Una respuesta tardía (lookup o predicción) cae sobre otra entrada | Solo se aplica si la entrada o la glass sigue siendo la misma; hay test |
| La transacción del lote se alarga | El lookup externo de pulido va fuera; dentro solo quedan SQL locales |
| El cambio de wiring del servidor rompe el arranque sin que la suite lo vea | Arranque comprobado a mano antes de cerrar |
| La predicción del servidor difiere de la del JavaFX | Los 16 tests viajan con el código sin tocar sus expectativas; la única diferencia es el dato más fresco (D2) |
