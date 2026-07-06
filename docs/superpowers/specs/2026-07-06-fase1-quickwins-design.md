# Fase 1 — Quick wins: selección IMEIs, celda fantasma, editar otros pedidos, chasis

Fecha: 2026-07-06
Estado: **Diseño aprobado, pendiente de plan de implementación.**
Origen: lista de mejoras del usuario (2026-07-06). Es la Fase 1 de un roadmap
de 5 fases acordado en el brainstorm.

## Roadmap general (contexto, no es alcance de esta spec)

1. **Fase 1 (esta spec)** — 4 quick wins de UI/UX.
2. **Fase 2** — Lotes y ciclo de vida del teléfono: el teléfono como entidad
   propia (hoy solo existe si se repara), import de lote xlsx/csv + alta manual,
   OK directo para los que llegan bien, localizaciones (dónde está el teléfono
   en cada estado) y replanteo de la sección IMEIs. Spec propia.
3. **Fase 3** — Rol LOGISTICA: como SUPERTECNICO pero sin poder reparar.
   Aprovechar para hacer enforcement de autorización en servidor para lo nuevo.
4. **Fase 4** — Estadísticas: refresco de Reparaciones y Stock (obsoletos) +
   sección nueva IMEIs. Métricas candidatas: producción por técnico/periodo,
   tiempo medio alta→completada, distribución por modelo/avería, reincidencia;
   consumo mensual por componente, previsión de agotamiento, roturas de mínimo,
   rotación, valor de inventario; teléfonos por estado del ciclo, % OK-directo
   por lote, lead time compra→OK, coste medio por teléfono/modelo,
   intervenciones por IMEI, distribución por localización.
5. **Fase 5** — Stock mínimo automático por consumo mensual (se apoya en la
   métrica de consumo de Fase 4). Resolver de paso la inconsistencia
   STOCK_MINIMO master/slave de SKUs compartidos.

Cada fase: rama propia, spec+plan propios, merge `--no-ff` solo con OK del
usuario.

## Ítem 1 — Selección persistente en la vista IMEIs (solo cliente)

**Problema:** al entrar al detalle de un IMEI en la vista Agrupado y volver,
la fila seleccionada se pierde y la tabla vuelve arriba.

**Comportamiento acordado:**
- Al hacer drill-down se guarda el IMEI seleccionado y la posición de scroll
  del maestro.
- `volverAGrupos()` (`AgrupadoController` :1070) repuebla, re-selecciona la
  fila por IMEI y hace `scrollTo` hasta ella. Si el refresco eliminó el grupo,
  no se selecciona nada (comportamiento actual).
- Clicar el botón del sub-sidebar «IMEIs» (`btnTabAgrupado`,
  `ReparacionViewSuperTecnico.fxml` :47 → `mostrarAgrupado`) **estando en modo
  detalle** equivale a «← Volver» (misma restauración). Estando ya en el
  maestro, comportamiento actual (refresco). Aplica a las 3 vistas de rol que
  incluyen `AgrupadoView.fxml` por `fx:include`.

**Anclajes:** `AgrupadoController` — `volverAGrupos()` :1070, barra de
navegación :1036. El controlador persiste entre navegaciones (fx:include), así
que el estado puede vivir en campos del propio controlador.

## Ítem 2 — Bug: celda «reasignar» fantasma (solo cliente)

**Síntoma (reporte del usuario):** al reasignar técnico, aparece una celda de
reasignar «suelta» abajo en la tabla; parece ocurrir cuando la tabla no está
visualmente llena.

**Hipótesis (NO confirmada, no comprometer el fix):** celda de `TableView`
reutilizada sin limpiar en la rama `empty` de `updateItem` — el combo/control
de reasignación queda pintado en una fila vacía.

**Proceso acordado:** systematic-debugging — reproducir primero (tabla con
pocas filas + reasignar), diagnosticar, y el fix sale del diagnóstico.

**Anclajes:** reasignación de técnico en
`PendientesSuperTecnicoController` :199 y `PulidoSuperTecnicoController` :106
(«Guardado directo: reasignar el técnico ya en BD»). Revisar el cell factory
de esa columna en ambas tablas (el bug puede existir en las dos).

## Ítem 3 — Editar pedidos de otros componentes (cliente; PUT ya existe)

**Objetivo:** paridad con el editor de compras normales
(`FormularioCompraEditarController`), sobre todo para corregir el proveedor.

**Alcance acordado (paridad + concepto):** proveedor, cantidad, urgente,
precio, divisa **y concepto** (texto libre del pedido).

**Reglas (espejo del editor normal):**
- Editable en estado `pendiente` o `recibido` (javadoc
  `FormularioCompraEditarController` :17-27). En «otros» no hay ajuste de
  stock (no son componentes de stock), así que esa parte no aplica.
- Solo ADMIN (mismo `@role` que el editor normal).
- Control de concurrencia optimista (`StaleDataException`) como el resto.
- Mismo gesto de entrada que en compras normales (desde la tabla de otros
  pedidos en Stock).

**Estado del código:** `CompraOtroDAO.editar(...)` :72 ya hace
`PUT /api/compras-otros/{id}` con idProv, concepto, cantidad, esUrgente y
precio — **sin llamadores en el cliente**. Falta el modal
(`FormularioOtroPedidoEditar`, calcado de `FormularioCompraEditar.fxml`) y el
gesto de entrada. Verificar al implementar que el endpoint del servidor
existe de verdad y cubre todos los campos (incl. divisa).

## Ítem 4 — Chasis en asignaciones (cliente + servidor + BD)

**Concepto:** flag que marca una asignación de reparación como «de chasis».
Abarca **solo asignaciones y pendientes**; en historial ni se muestra ni se
edita (el componente usado ya indica que fue chasis).

**BD:** columna `ES_CHASIS TINYINT(1) NOT NULL DEFAULT 0` en la tabla
`Reparacion` (compartida por asignaciones `A/AG/AP` e historial `R/G/P`; ver
`TipoTrabajo`). Solo puede ser 1 en tipo Reparación. Al completarse la
asignación el valor queda almacenado pero la UI deja de usarlo. Sin migración
de datos (históricos a 0).

**Modal de asignación** (`abrirFormularioAsignacion` en
`PendientesSuperTecnicoController`, cola+detalle post-cluster-D):
- Check «Chasis» en el detalle, visible solo cuando la cola activa es
  Reparación.
- Se resetea entre IMEIs **como el comentario** (:1614 «el comentario NO se
  mantiene entre IMEIs»); los técnicos sí se mantienen (:1675).
- Campo nuevo `esChasis` en `EntradaAsignacion` (:104) y en el guardado.

**Dónde se ve la marca** (sub-etiqueta discreta «Chasis» bajo la píldora de
tipo, paleta sutil como otras marcas):
- Asignaciones del supertécnico: columna Tipo existente (`cTipo`,
  `PendientesSuperTecnicoView.fxml` :35; badge :158-160 del controlador).
- Mis pendientes del técnico: hoy **no** tiene columna Tipo
  (`PendientesTecnicoView.fxml` :25-34) → **se añade la columna Tipo** con la
  misma píldora Reparación/Glass y la marca debajo. Unifica ambas tablas.
- Solo en filas de tipo Reparación con el flag a 1.

**Menú contextual** (solo vista del supertécnico, filas pendientes de tipo
Reparación, roles SUPERTECNICO/ADMIN):
- «Marcar como chasis» / «Desmarcar chasis» según estado actual.
- Persiste en servidor (endpoint PATCH nuevo), refresca la fila y registra la
  acción en el log de actividad.
- No aparece en filas Glass/Pulido ni para el técnico raso (él solo la ve).

**Servidor:** columna + DTO + guardado del flag al crear asignación + PATCH
de toggle + log. Validar arranque del contexto Spring tras el cambio (no hay
test de contexto).

## Decisiones tomadas en el brainstorm

- Editor de otros: paridad + concepto (no solo proveedor).
- Chasis: solo asignaciones/pendientes; historial no lo muestra (decisión del
  usuario: el componente ya lo indica).
- Edición del flag por **menú contextual** (idea del usuario), no en el modal
  de editar reparación.
- Marca en Mis pendientes: bajo la píldora de Tipo → requiere añadir la
  columna Tipo a esa tabla.
- El check no persiste entre IMEIs en el modal (resetea como el comentario).

## Notas de proceso

- Rama única `feature/fase1-quickwins`; orden de implementación: ítem 1 → 2 →
  3 → 4 (el 4 al final por tocar BD + submódulo servidor).
- Ítems 1-2 solo cliente; ítem 3 cliente (verificar endpoint); ítem 4 cliente
  + servidor + BD.
- Smoke manual por roles al cierre (cliente JavaFX sin cobertura UI); tests
  unitarios donde haya lógica pura (p. ej. derivación de la marca).
- Merge `--no-ff` solo con confirmación del usuario.
