# Cluster D · Fase 2 — Pulido al esqueleto cola + detalle (master-detail, sin Asignar)

Fecha: 2026-07-02
Estado: **APROBADO, sin implementar**.
Origen: [cluster D](2026-07-02-cluster-d-modal-asignacion-design.md), Fase 2.
Depende de: Fase 1 (colas rep/glass) y el fix de cliente por-IMEI, ambos ya en `main`.
Alcance: **solo cliente**. No toca servidor, DAOs, ni el modelo `FilaPulido`.

## Objetivo

Reorganizar el panel de pulido del modal `abrirFormularioAsignacion`
(`construirPulidoPane`) para que use el mismo esqueleto visual **izquierda
(lista) + derecha (detalle)** que rep/glass, **sin adoptar** el flujo rojo/verde
ni el paso "Asignar →". La fila escaneada sigue quedando lista de inmediato; lo
único que cambia es **dónde se edita**: en vez de controles en la propia fila, se
edita en el **detalle de la derecha** al seleccionar la fila (master-detail).

Decisión de alcance (aprobada): **solo el layout, sin paso Asignar**. Paridad de
detalle mayor (multi-técnico, modelo) queda para Fase 3.

## Estado actual (a reorganizar)

`construirPulidoPane(lote, tecnicosModal, onChange, onClienteCambiado,
refrescadoresCliente, sembrarCliente)`:
- Arriba: `cbTec` ("Técnico por defecto"), `taCom` ("Comentario por defecto"),
  campo de escaneo `tfScan`.
- Escanear → `agregar(imei)` crea una `FilaPulido` con los defaults y **añade una
  fila** a `listaItems` (VBox plano dentro de un `ScrollPane`). Cada fila lleva
  **inline**: `lblImei` + `cbRow` (técnico) + `btnCli` (cliente, vía
  `SelectorClienteDialog`) + `tfRow` (comentario) + `btnX` (borrar).
- Precarga de cliente de BD por fila (respeta el mapa por-IMEI del fix reciente).
- Sin rojo/verde, sin "Asignar". `Guardar` (fuera de este método) persiste
  `lotePulido`.

## Diseño

### Layout
- **Arriba (sin cambios de propósito):** `cbTec` (técnico por defecto) + `taCom`
  (comentario por defecto) + `tfScan`. Siguen pre-rellenando las filas nuevas.
- **Abajo: `HBox(listaBox, detalleBox)`** (misma disposición que `cols` de
  rep/glass):
  - **`listaBox` (izquierda):** la lista de `FilaPulido`. Cada fila = resumen
    compacto **IMEI + técnico + cliente** + **✕** (borrar). Clic → selecciona
    (resalta) y carga en el detalle. Reemplaza los controles inline por el
    resumen; el `ScrollPane`/`listaItems` actuales se reutilizan.
  - **`detalleBox` (derecha):** detalle de la fila seleccionada:
    - **IMEI en curso** (label).
    - **Técnico:** un `ComboBox<Tecnico>` (1 técnico).
    - **Cliente:** botón → `SelectorClienteDialog` (ya trae "— Sin cliente —" y
      respeta el mapa por-IMEI). Mantiene el `onClienteCambiado` + refrescadores.
    - **Comentario:** `TextField`/`TextArea`.
    - Editar cualquiera **aplica en vivo** a la `FilaPulido` seleccionada (sin
      botón "Asignar"). El resumen de la fila en la lista se refresca.

### Flujo
- Escanear IMEI → crea `FilaPulido` con defaults (técnico/comentario por
  defecto), la añade a la lista y **la auto-selecciona** en el detalle (paridad
  con rep/glass: al escanear se carga en el formulario).
- Seleccionar otra fila → el detalle muestra sus valores; editar los cambia.
- Borrar (✕) → quita la fila; si era la seleccionada, limpia el detalle.
- **Guardar** persiste `lotePulido` exactamente igual que hoy.

### Qué NO cambia
- Modelo `FilaPulido` (imei, tecnico, comentario, cliente, sinCliente).
- Guardado (`btnGuardar` recorre `lotePulido`).
- Toda la lógica de cliente sin-cliente + sync por-IMEI (mapa `clienteManual`,
  `sembrarCliente`, `onClienteCambiado`, `refrescadoresCliente`): el picker del
  detalle sigue disparando esos mismos callbacks.
- El selector superior sigue intercambiando `richArea` (rep/glass) ↔ `pulidoPane`.

### Decisión menor (resuelta)
Cliente en el detalle: **se mantiene el botón → `SelectorClienteDialog`** (no se
reimplementa el autocompletado inline de rep/glass). Ya funciona con "sin
cliente" y el mapa por-IMEI; menos trabajo y sin tocar lógica recién
estabilizada.

## Estructura de código
Todo dentro de `construirPulidoPane`. El método construye hoy filas
autocontenidas; pasa a construir (a) filas-resumen para la lista y (b) un panel
de detalle único que edita la `FilaPulido` seleccionada. Introducir un holder de
"fila seleccionada" (`FilaPulido[] seleccionada`) y un `render`/`refrescar` de la
lista análogos a `renderPila`/`crearFilaPila` de rep/glass, pero **sin** partición
rojo/verde. Vigilar que el método no crezca en exceso; si se vuelve inmanejable,
extraer helpers (constructor de fila-resumen, refresco del detalle) dentro de la
misma clase.

## Riesgos
- Reubicar la edición inline → detalle sin perder la precarga de cliente ni los
  `refrescadoresCliente` (que hoy actualizan el botón inline; pasarán a refrescar
  el resumen de la lista y/o el detalle).
- Auto-selección al escanear y estado "sin selección" (detalle deshabilitado).
- Que el borrado de la fila seleccionada limpie el detalle.
- No romper el sync por-IMEI (el detalle debe seguir llamando `onClienteCambiado`).

## Validación
Sin tests de UI → **smoke manual**: escanear varios IMEIs (heredan defaults),
seleccionar y editar técnico/cliente/comentario en el detalle, ver el resumen
actualizarse, borrar filas, "sin cliente" + sync por-IMEI entre colas, y Guardar.
Ver [[project_plan_testing]].

## Notas de proceso
- Rama `feature/…`; merge con OK del usuario ([[feedback_merge_confirmacion]]).
- Fase 3 (paridad de detalle: modelo y/o multi-técnico) queda aparte.
