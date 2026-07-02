# Cluster D · Fase 1 — Colas Rep/Glass independientes (estructural)

Fecha: 2026-07-02
Estado: **APROBADO, sin implementar** (brainstorm cerrado; siguiente paso: plan).
Origen: [cluster D](2026-07-02-cluster-d-modal-asignacion-design.md), Fase 1.
Alcance: **solo cliente**. No toca servidor, DAOs ni semántica de datos.

## Objetivo

En el modal `abrirFormularioAsignacion()`
(`PendientesSuperTecnicoController.java`), Reparación y Glass dejan de
**compartir la misma vista**. Al pulsar *Reparación* se ve solo la cola de
Reparación; al pulsar *Glass*, solo la de Glass. Desaparece el toggle de tipo
por-entrada del detalle. El pulido sigue con su panel actual como 3ª cola (sin
cambios en esta fase).

El guardado produce **exactamente las mismas asignaciones que hoy**: es un
refactor estructural, no un cambio de comportamiento de datos.

## Decisión de estructura (resuelta)

**Dos listas separadas** (opción B), no una pila filtrada. Motivo: la visión es
"3 colas independientes" y el pulido ya es una lista aparte (`lotePulido`); dos
listas explícitas hacen el modelo literal, los contadores por-cola salen
automáticos y no hay "impuesto de filtrar" en cada lectura. Deja el terreno
preparado para Fase 2 (meter pulido al mismo esqueleto).

## Cambios visibles (antes → después)

- **HOY:** *Reparación* y *Glass* muestran el mismo panel con rep+glass
  mezclados en la lista (cada fila con badge de tipo); el detalle tiene un
  toggle `Reparación | Glass` (`tgEnt`).
- **DESPUÉS:** cada pestaña muestra solo su cola. El badge de tipo por fila
  sobra (todas las filas son del mismo tipo). El toggle `tgEnt` del detalle
  **desaparece** (el tipo lo fija la cola en la que estás).

Todo lo demás del modal (selector arriba, escaneo, lista izquierda + detalle
derecha, pulido) permanece igual. A vs B es invisible para el usuario.

## Diseño

### Datos
- `List<EntradaAsignacion> pila` (:988) → se parte en **`pilaRep`** y
  **`pilaGlass`**. `lotePulido` (:994) sin cambios.
- Accesor **`pilaActiva()`** que devuelve `pilaGlass` si `tipoActual[0] ==
  GLASS`, si no `pilaRep`. Derivado de `tipoActual[0]`, siempre correcto (mismo
  patrón que los holders `[0]` existentes).
- `EntradaAsignacion.tipo` (:106) **se conserva** (lo usan el guardado y
  `recomputeOcupados`), pero ya no se muta: se fija al escanear (= cola activa)
  y no cambia más.
- Táctica de refactor: **eliminar la variable `pila`** y dejar que el compilador
  marque cada uso → cero referencias huérfanas.

### Selector superior (`tgTipo`, :1624)
- Rep/Glass muestran `richArea` operando sobre `pilaActiva()`; Pulido muestra
  `pulidoPane` (sin cambios).
- Al conmutar Rep↔Glass, el detalle cargado (`actual[0]`) pertenece a la cola
  vieja → **se limpia**: `actual[0] = null`, `formBox.setDisable(true)`,
  `lblImeiCurso.setText("—")`. El usuario escanea o pincha una fila de la nueva
  cola. (Hoy no se limpiaba porque era la misma pila; ahora limpiar es
  correcto.)

### Escaneo / dedup
- `intentarAnadir` (:1542) y el pegado múltiple (:1575-1583) añaden a
  `pilaActiva()`.
- Dedup pasa a ser solo por `imei` dentro de la lista activa (el tipo lo implica
  la cola): `pilaActiva().stream().anyMatch(x -> x.imei.equals(imei))`
  (:1545, :1577).
- El mismo IMEI puede seguir en Rep **y** en Glass (listas distintas) —
  comportamiento preservado. Mensaje de duplicado sigue usando la etiqueta de
  `tipoActual[0]`.

### Detalle (`formBox`)
- Se eliminan `tgEnt` + `tbEntRep` + `tbEntGlass` + `tipoEntradaBox` + su label
  `lblTipoEnt` (:1266-1275, :1284) y el listener (:1602-1610), más la línea de
  `cargarEntrada` que preseleccionaba el toggle (:1458).
- El resto del detalle (modelo, técnicos, cliente, comentario) intacto.
  `cargarEntrada` sigue llamando a `recomputeOcupados` con `e.tipo` (ya fijado).

### Contadores
- **Junto a cada lista** (`lblRojo`/`lblVerde`, :1362-1363): cuentan la **cola
  activa** (`pilaActiva()`). El render (:1337-1373) opera sobre `pilaActiva()`;
  rojos/verdes se parten solo por `asignada` (la lista ya es de un solo tipo, no
  hace falta filtrar por tipo).
- **Barra inferior** (`lblProg`, :1368) y **botón Guardar** (`btnGuardar`,
  :1371-1372): **globales** = Rep + Glass + Pulido. `sinModelo` global.
- **Guardar bloqueado por rojos globales** (decisión de UX aprobada): no se
  puede guardar si queda algún pendiente en *cualquiera* de las dos colas
  (`nRojoRep + nRojoGlass > 0`). Como `lblProg` es global y muestra "N
  pendientes", la razón es visible aunque el rojo esté en la otra pestaña.
  Paridad con el comportamiento actual.

### Guardado / cierre
- `btnGuardar` (:1645-1681): recorre `pilaRep` + `pilaGlass` (lógica por entrada
  idéntica, sigue usando `e.tipo` para elegir categoría R/G) + `lotePulido`.
- Aviso de descartar en `onCloseRequest` (:1683-1689): total =
  `pilaRep.size() + pilaGlass.size() + lotePulido.size()`.

### `seq` / `seqCounter`
- Contador global compartido, sin cambios. El orden dentro de cada cola sigue
  siendo correcto (se ordena desc por `seq` dentro de la lista activa).

## Riesgos y mitigaciones
- **Referencia perdida a `pila`** → eliminar la variable; el compilador localiza
  todos los usos.
- **Semántica de Guardar cross-cola** → decidida global (bloqueo por rojos de
  ambas colas), con `lblProg` global visible como pista.
- **`actual[0]` de la cola inactiva tras conmutar** → limpiar en el listener del
  selector.
- **Orden rojo/verde por cola** → el render ya parte por `asignada`; al operar
  sobre la lista activa queda naturalmente por-cola.

## Validación
Sin cobertura de tests (UI JavaFX) → **smoke manual**: escanear IMEIs en Rep y
en Glass, comprobar que cada pestaña muestra solo lo suyo, que el detalle ya no
tiene toggle de tipo, que los contadores por-cola y el global cuadran, que
Guardar persiste ambas colas + pulido y bloquea con rojos en cualquier cola.
Ver [[project_plan_testing]].

## Notas de proceso
- Rama `feature/…` por fase; merge con OK del usuario ([[feedback_merge_confirmacion]]).
- Fases 2 y 3 (pulido al esqueleto cola+detalle, paridad de detalle) quedan en
  el doc de descomposición del cluster D.
