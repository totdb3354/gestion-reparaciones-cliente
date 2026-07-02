# Cluster D — Modal de asignación unificado con 3 colas (asignación masiva)

Fecha: 2026-07-02
Estado: **DESCOMPUESTO, sin implementar** (brainstorm inicial hecho; se retoma en otra sesión).
Origen: backlog post-Glass, cluster D (ítem 8).

## Visión

El modal de asignación (`abrirFormularioAsignacion` en
`PendientesSuperTecnicoController`) debe tener **3 colas independientes**
(Reparación / Glass / Pulido), cada una con el mismo esqueleto **cola + detalle**.
Al separar por tipo, desaparece el toggle de tipo por-entrada del detalle. El
pulido se integra en la misma estructura, **reusando primero su panel actual** y
adaptándolo hacia paridad poco a poco. Objetivo de fondo (ítem 8): asignación
masiva homogénea entre los 3 tipos, conservando el técnico entre asignaciones.

## Estado actual del código (anclajes para la próxima sesión)

Todo en `gestion-reparaciones-cliente/.../controllers/PendientesSuperTecnicoController.java`:

- **Método:** `abrirFormularioAsignacion()` (~950-1700). Método enorme; ojo al
  refactor.
- **Modelos internos:**
  - `EntradaAsignacion` (:104): rico — `imei`, `tipo` (REP/GLASS), `modeloCode`,
    `List<Tecnico> tecnicos` (multi-técnico), `cliente`, `comentario`, `asignada`
    (rojo/verde), `seq`.
  - `FilaPulido` (:121): ligero — `imei`, `tecnico` (uno), `comentario`, `cliente`.
- **Selector superior** `selectorTipo` = tbRep/tbGlass/tbPulido (:1012-1023);
  fija `tipoActual[0]` para los IMEIs nuevos.
- **Layout:** `centro` = `StackPane(richArea, pulidoPane)` (:1619), intercambiado
  por el selector (:1624-1631).
  - `richArea` = VBox(escaneo + `cols`) (:1615). `cols` = `HBox(pilaBox, formBox)` (:1614).
    - `pilaBox` = VBox(rojo=pendiente `scrollRojo`, verde=asignada `scrollVerde`) (:1060).
    - `formBox` = detalle: **toggle tipo `tgEnt`** (tbEntRep/tbEntGlass), checkboxes
      de técnicos, cliente, modelo (lookup), comentario, botón Asignar.
  - `pulidoPane` = `construirPulidoPane(lotePulido, …)` (:829/:1616): panel
    autocontenido (técnico por defecto + comentario + escaneo → filas ligeras).
- **Toggle de tipo por-entrada** `tgEnt` (:1602-1610): cambia `actual[0].tipo`
  (rep↔glass) de la entrada seleccionada. **Esto sobra con colas separadas.**
- **Cola:** `pila` = cola mixta rep+glass (cada entrada con su `tipo`); dedup por
  `(imei, tipo)` (:1545, :1577). `lotePulido` = lote de pulido aparte.
- **Guardado:** `btnGuardar` (:1610-1646) recorre `pila` (rep/glass) + `lotePulido`
  (pulido).
- **Persistencia de técnico (masiva):** `defTecnicos` (rico) se mantiene entre
  entradas; el pulido usa "técnico por defecto" para filas nuevas.

## Descomposición en fases

### Fase 1 — Separar colas rep/glass + quitar `tgEnt` (estructural)
- El selector superior pasa a mostrar/añadir solo la cola del tipo activo
  (Reparación vs Glass como colas distintas). Decisión de impl: mantener una
  `pila` y filtrar el render por `tipoActual`, **o** dos listas separadas.
- Eliminar `tgEnt` del detalle (el tipo de la entrada lo fija la cola).
- Pulido: sin cambios de semántica; sigue con su panel, como 3ª cola.
- Riesgos: dedup, orden `seq`, agrupación rojo/verde por cola, que el botón
  Guardar siga recorriendo lo correcto.

### Fase 2 — Pulido en el esqueleto cola + detalle
- Dar al pulido el mismo esqueleto izquierda(cola)+derecha(detalle) en vez del
  panel autocontenido, reutilizando sus controles actuales (técnico, cliente,
  comentario) como detalle. Mantener 1 técnico por IMEI de momento.

### Fase 3 — Paridad del detalle de pulido
- Añadir edición de modelo y/o multi-técnico al detalle de pulido (según decida
  el usuario). Semántica: hoy pulido es 1 técnico; multi-técnico es un cambio a
  validar.

## Decisiones abiertas (resolver en la spec de cada fase)
- ¿Una `pila` filtrada por tipo o tres listas separadas? (Fase 1)
- ¿Pulido multi-técnico o sigue 1 técnico? (Fase 3; de momento 1)
- ¿Modelo en el detalle de pulido? (Fase 3)
- Persistencia del técnico seleccionado entre las 3 colas (asignación masiva).

## Notas de proceso
- Cada fase = su propia spec + plan + implementación + smoke (cliente JavaFX,
  sin cobertura de tests → smoke manual). Ver [[project_plan_testing]].
- Solo cliente si no cambia semántica de datos; si pulido gana multi-técnico o
  cambia el guardado, revisar servidor ([[feedback_server_spring_startup]]).
- Rama por fase (`feature/…`); merge con OK del usuario ([[feedback_merge_confirmacion]]).
