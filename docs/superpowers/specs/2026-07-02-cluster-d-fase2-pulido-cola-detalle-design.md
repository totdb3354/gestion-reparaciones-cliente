# Cluster D · Fase 2 — Pulido al esqueleto cola + detalle (master-detail, sin Asignar)

Fecha: 2026-07-02
Estado: **APROBADO (revisado tras mockups), sin implementar**.
Origen: [cluster D](2026-07-02-cluster-d-modal-asignacion-design.md), Fase 2.
Depende de: Fase 1 y el fix de cliente por-IMEI, ambos en `main`.
Alcance: **solo cliente**. No toca servidor, DAOs, ni el modelo `FilaPulido`.

> Nota: esta spec fue refinada con el usuario (companion visual) respecto a una
> primera versión "master-detail con botón de cliente". Cambios acordados:
> **cliente = autocompletado inline** (igual que rep/glass), **sin sección de
> defaults**, **sin arrastre** (cada IMEI se configura solo), y **bloqueo
> estricto de Guardar** si alguna fila de pulido no tiene técnico.

## Objetivo

Reorganizar el panel de pulido del modal (`construirPulidoPane`) al mismo
esqueleto **izquierda (lista) + derecha (detalle)** que rep/glass, lo más
parecido posible por fuera, **sin** el flujo rojo/verde ni el paso "Asignar →".
La fila escaneada nace ya en la cola (única, estilo "Asignados/verde"); se edita
seleccionándola en el detalle de la derecha (master-detail).

## Decisiones acordadas
- **Una sola cola**, sin "Pendiente": las filas nacen listas (como todo verde).
  Sin "Asignar".
- **Sin sección "por defecto"** arriba (ni técnico ni comentario por defecto).
- **Sin arrastre:** cada IMEI nace **sin técnico** (como rep/glass) y **sin
  comentario**; se configura individualmente en el detalle. El cliente se
  precarga de BD / del mapa por-IMEI (como hoy).
- **Cliente = autocompletado inline** idéntico a rep/glass (con "— Sin cliente
  —", restaurar-al-blur y el mapa `clienteManual` + sync), **no** el botón +
  `SelectorClienteDialog`.
- **1 técnico** por fila (multi-técnico/modelo es Fase 3).
- **Validación (bloqueo estricto):** una fila sin técnico se marca en la lista y
  **bloquea "Guardar"** (equivalente inline a los "rojos" de rep/glass). Se
  mantiene el conflicto de "técnico ya asignado a ese IMEI en pulido" en el
  guardado.

## Estado actual (a reorganizar)
`construirPulidoPane(lote, tecnicosModal, onChange, onClienteCambiado,
refrescadoresCliente, sembrarCliente)`: hoy tiene arriba "técnico/comentario por
defecto" + escaneo, y una lista de filas **autoeditables** (técnico combo +
botón cliente + comentario + ✕). Precarga de cliente por fila (respeta el mapa).
Guardado (`btnGuardar`, en `abrirFormularioAsignacion`) recorre `lotePulido`.

## Diseño

### Layout
- **Arriba:** solo el **escaneo** (`tfScan` + error). Se elimina la sección
  técnico/comentario por defecto.
- **Abajo: `HBox(listaBox, detalleBox)`** (como `cols` de rep/glass):
  - **`listaBox` (izquierda):** la lista de `FilaPulido`, en una caja estilo
    "Asignados/verde". Cada fila = resumen **IMEI** + **"técnico · cliente"**;
    si falta técnico, "(sin técnico)" en rojo. Clic → selecciona (resalta) y
    carga en el detalle. **✕** para quitar.
  - **`detalleBox` (derecha, estilo formBox de rep/glass):**
    - **IMEI en curso** (label mono).
    - **Técnico:** `ComboBox<Tecnico>` (1 técnico), vacío al nacer la fila.
    - **Cliente:** **autocompletado inline** (TextField + popup con lista
      filtrada), con la fila "— Sin cliente —" (sentinel `-1`), restaurar-al-blur
      y registro en el mapa `clienteManual` + `onClienteCambiado` (sync por-IMEI).
      Réplica del control de rep/glass, operando sobre la fila seleccionada.
    - **Comentario:** campo de texto.
    - Editar cualquier campo **aplica en vivo** a la `FilaPulido` seleccionada
      (sin "Asignar") y refresca su resumen en la lista.

### Flujo
- Escanear IMEI → crea `FilaPulido` **sin técnico, sin comentario** (cliente por
  precarga/mapa), la añade a la lista y **la auto-selecciona** en el detalle.
- Seleccionar otra fila → el detalle muestra sus valores; editar los cambia.
- Borrar (✕) → quita la fila; si era la seleccionada, limpia/deshabilita el
  detalle.
- **Guardar** persiste `lotePulido` igual que hoy, pero está **deshabilitado**
  mientras alguna fila de pulido no tenga técnico (ver Validación).

### Validación (bloqueo estricto de Guardar)
- El botón "Guardar" vive en `abrirFormularioAsignacion` y su `disable` se
  calcula en `renderPila`. Añadir a la condición de bloqueo el conteo de filas
  de pulido **sin técnico**: `pulidoSinTecnico = lotePulido.count(f ->
  f.tecnico == null)`; Guardar se deshabilita si `pulidoSinTecnico > 0` (además
  de los rojos de rep/glass ya existentes).
- Para que ese estado se recalcule al editar el técnico en pulido, el detalle de
  pulido debe invocar `onChange` (que dispara `renderPila`) al cambiar el
  técnico (y al añadir/borrar filas, como ya hace).
- La lista de pulido marca "(sin técnico)" en las filas incompletas.
- Se mantiene en el guardado el chequeo `existeAsignacionParaTecnico(imei, tec,
  "P")` → conflicto reportado (como hoy).

### Qué NO cambia
- Modelo `FilaPulido` (imei, tecnico, comentario, cliente, sinCliente).
- El guardado en sí (recorre `lotePulido`); solo cambia el `disable` de Guardar.
- La lógica de cliente sin-cliente + sync por-IMEI: el picker inline de pulido
  usa el mismo mapa (`clienteManual` vía `onClienteCambiado`) y `sembrarCliente`.
- El selector superior sigue intercambiando `richArea` (rep/glass) ↔ `pulidoPane`.

## Estructura de código
- `construirPulidoPane`: reescrito a lista + detalle con `seleccionada`/`render`
  (holders `[]`), incluyendo un **autocompletado de cliente inline** replicado
  del de rep/glass (sentinel "— Sin cliente —", restaurar-al-blur), operando
  sobre `seleccionada[0]`.
- `renderPila` en `abrirFormularioAsignacion`: añadir `pulidoSinTecnico` a la
  condición de `btnGuardar.setDisable`.
- Duplicación aceptada del control de cliente inline (rep/glass ↔ pulido) por
  ahora; una unificación del detalle es candidata futura (no en esta fase).
  Vigilar el tamaño de `construirPulidoPane`; extraer helpers en la misma clase
  si crece en exceso.

## Riesgos
- Replicar el autocompletado de cliente inline sin regresiones (sentinel,
  restaurar-al-blur, no pisar la precarga/mapa).
- Recalcular el bloqueo de Guardar al cambiar técnico en pulido (llamar
  `onChange` → `renderPila`).
- Auto-selección al escanear; estado "sin selección" (detalle deshabilitado);
  borrar la fila seleccionada limpia el detalle.
- No romper el sync por-IMEI (el detalle debe seguir llamando `onClienteCambiado`
  y las filas nuevas `sembrarCliente`).

## Validación (smoke)
Sin tests de UI → **smoke manual**: escanear varios IMEIs (nacen sin técnico →
"(sin técnico)" en rojo, Guardar bloqueado); seleccionar y fijar técnico →
desaparece la marca y Guardar se habilita cuando todas tienen técnico; editar
cliente (inline, con "— Sin cliente —") y comentario; sync por-IMEI entre colas;
borrar filas; Guardar persiste como antes. Ver [[project_plan_testing]].

## Notas de proceso
- Rama `feature/…`; merge con OK del usuario ([[feedback_merge_confirmacion]]).
- Fase 3 (paridad de detalle: modelo y/o multi-técnico) queda aparte.
