# Specs de diseño por módulo funcional — insumo para la paridad web

Fuente: las 47 specs de `docs/superpowers/specs/` (2026-06-07 → 2026-09-09). Se citan por fecha + nombre corto. Solo se afirma lo que dice alguna spec; los planes no se han usado salvo una consulta puntual (`plans/2026-06-19-timezone-madrid.md`, sin spec propia) para la sección transversal.
Leyenda: **⚠ solo-cliente** = lógica que, según la spec, vive únicamente en el cliente JavaFX (la web debe reimplementarla o llevarla al servidor). "Descartado/aplazado" = decisiones cerradas que no conviene reabrir.

---

## 1. Reparaciones / asignaciones (vista Asignaciones y modal de alta)

**Reglas de negocio**
- Tipos de trabajo por prefijo de `ID_REP` en la única tabla `Reparacion`: `A` asignación reparación, `AG` glass, `AP` pulido; terminados `R`/`G`/`P`. Formato `PREFIJO+yyyyMMdd_N`, fecha en Europe/Madrid (06-30 separar-glass; plan timezone).
- Duplicados: el mismo IMEI puede asignarse al mismo técnico **una vez por categoría** (rep+glass+pulido a la vez), nunca dos veces en la misma; el servidor valida `existeAsignacionParaTecnico(imei, idTec, prefijo)` (06-30 separar-glass). En el commit del lote se revalida y los pares en conflicto se **saltan y se reportan** (06-12 asignacion-lote-pila).
- Crear cualquier asignación (rep/glass/pulido) resetea `REVISION_LOGISTICA=0` del teléfono (06-17 revision-logistica; 06-30 separar-glass).
- "Asignado por" = `ID_TEC_ASIGNA` (técnico del supertécnico que asigna); en reasignación real (cambia `ID_TEC`) se sobrescribe con el reasignador; solo cambiar comentario no lo toca; las filas `R*` copian el de su `A*`; datos antiguos → `—` (06-22 asignador-reparaciones). Pulido: paridad (07-02 paridad-pulido).
- Urgente: **manual** (menú "Marcar/Quitar urgente"); asignar con cliente ya **no** marca urgente; job servidor a las 00:00 Madrid marca `URGENTE` en asignaciones pendientes rep+glass (no pulido) con cliente y `FECHA_ASIG` de día calendario anterior; quitar a mano se re-marca la siguiente medianoche si sigue vencida (06-24 urgente-cliente; 06-30 separar-glass). El job no escribe en `Log_Actividad`, solo log de aplicación.
- Chasis: flag `ES_CHASIS` solo en asignaciones tipo Reparación; se marca en el modal (check, se resetea entre IMEIs como el comentario) o por menú contextual en Asignaciones (SUPERTECNICO/ADMIN) con PATCH + log; no se muestra ni edita en historial (07-06 fase1-quickwins).
- "Por cerrar": flag `POR_CERRAR` solo en `A%` normal; marca/desmarca **solo el dueño** desde Mis pendientes (servidor valida propiedad estricta; ADMIN nunca); badge visible también en Asignaciones; no altera orden, contadores ni filtros; sin lock optimista (07-08 por-cerrar-carga).
- Modal de alta: pila por lotes con **3 colas independientes** (Reparación / Glass / Pulido), cada una con lista izquierda + detalle derecha (07-02 cluster-d fase1/fase2). Rep/Glass: entradas **rojas** (pendientes: sin técnicos o `⚠ falta modelo`) y **verdes** (modelo + ≥1 técnico). Guardar habilitado ⟺ **no hay rojas en ninguna cola** ni pulidos sin técnico; barra "N pendientes" global; nada toca BD hasta Guardar (excepciones: modelo manual, ver Glass §5) (06-12 asignacion-lote-pila; 07-02 cluster-d).
- Técnicos "pegajosos" **por cola** (Rep y Glass memorias separadas; se memoriza al marcar el checkbox, no solo al pulsar Asignar); comentario y chasis **no** se arrastran; cliente **no** se arrastra entre IMEIs distintos pero sí se sincroniza entre colas para el mismo IMEI; modelo idem (09-04 glass-modelo-vivo; 06-23 clientes; 07-02 cliente-sin-cliente).
- Cliente tri-estado por entrada: real / "— Sin cliente —" explícito (sentinel `-1`, `clienteExplicito=true` fija NULL) / no tocado (COALESCE preserva) (07-02 cliente-sin-cliente).
- Modelo: lookup por IMEI (`GET /api/telefonos/{imei}/modelo`); resultado del lookup **no** se persiste hasta Guardar; decisión manual se persiste al instante (`POST /api/telefonos` sin idCli) y se propaga a todas las entradas del IMEI; lookup en vuelo no pisa decisión manual (09-04 glass-modelo-vivo).
- Pegado de lote en el campo de escaneo: >15 dígitos → si múltiplo de 15 se trocea (LOTE), si no **rechazo total** ("Algún IMEI del pegado está corrupto…"); dedup contra la cola; en lote nadie se abre en el detalle; resumen "N IMEIs añadidos · M ya estaban en la lista" (06-29 pegado-lote-imeis). Sin Luhn (descartado).

**Convenciones UX**
- Tabla Asignaciones unificada con columna **Tipo** (píldora Reparación/Glass/Pulido) y filtro por tipo; sub-etiqueta "Chasis" bajo la píldora (06-30; 07-06).
- Sub-indicador "N asignados" bajo el IMEI (10px cursiva `#9AA0AA`, blanco si fila seleccionada) solo si N≥2 técnicos distintos; se oculta con 2 si ya hay píldora Glass/Rep/→ y vuelve con 3+ (06-29 indicador-asignados; 08-28 entrega-glass).
- Badges columna Estado apilados: Urgente · Por cerrar (`#E0F2F1`/`#00796B`, paleta Glass) · entrega ("→ Técnico H" / "Llegó hh:mm", índigo `#E8EAF6`/`#3949AB`) · solicitud (Recibido) (07-08; 08-28).
- Pendientes ordenan urgentes arriba (06-24). Borrado de asignación: papelera `/images/borrar.png` 25×25, `ConfirmDialog` **sin motivo**, texto "El técnico dejará de verla en su lista de pendientes[ y la incidencia se marcará como no activa…]"; incidencia → `borrarIncidenciaPorImei`, normal → `eliminarAsignacion` (06-22 borrar-pendientes-propias).
- Modal: cabeceras navy `#2C3B54`, fondo `#DDE1E7`, tarjetas blancas; botones de cola con pastilla contador (rojo si hay pendientes, gris si no, ninguna con 0); filas verdes muestran técnicos en 2ª línea; pastilla "auto" en glass predicha; etiqueta "glass" junto a técnicos habilitados solo en cola Glass; cerrar con cola no vacía → confirmación "se descartarán N IMEIs" (06-12; 09-05).
- Admin ve Asignaciones en **solo lectura** (`setSoloLectura`): sin papelera, sin crear, sin reasignar/editar/urgente; conserva filtros, buscador, copiar celda, "Asignado por" (06-22 admin-asignaciones-lectura).
- CSV de Asignaciones: columna "Entregado" `dd/MM/yyyy HH:mm` (08-28).

**⚠ solo-cliente**
- ⚠ Conteo "N asignados" derivado en cliente de la lista cargada (técnicos distintos por IMEI), no del endpoint `getTecnicosConAsignacionActiva` (06-29 indicador-asignados).
- ⚠ Modo solo lectura del admin = flag de UI; el servidor no restringe `GET /asignaciones` ni (según spec) las escrituras por rol (06-22 admin-asignaciones-lectura).
- ⚠ Pila/colas del modal, validación de duplicados en cola, bloqueo de Guardar por rojos, técnicos pegajosos, propagación cliente/modelo entre colas, parseo de pegado (`ImeiUtils`) — todo estado local del modal (06-12; 06-29; 07-02; 09-04).
- ⚠ Determinación "urgente al asignar = false" se decide en cliente (06-24).

**Descartado/aplazado**: motivo obligatorio al borrar asignación (solo reparaciones lo piden); Luhn; multi-selección/asignación masiva (backlog); cambiar tipo rep↔glass desde Asignaciones; pesos de carga configurables; paridad total del detalle de pulido (multi-técnico/modelo, Fase 3 cluster D abierta).

## 2. Pendientes y colas (Mis pendientes, badges)

- Mis pendientes: **3 pestañas** Reparaciones | Glass | Pulido (no unificado, porque completar difiere por tipo) (06-30 separar-glass).
- Badge de "Mis pendientes": suma rep+glass+pulido, cap **`99+`**; cada toggle lleva sufijo "(n)" siempre, incluido `(0)`, cap 99+ (07-01 badge-cap99).
- Supertécnico ve papelera en sus pendientes propias (columna `cBorrar` aparte); técnico normal no (06-22 borrar-pendientes-propias).
- Columna Tipo también en Mis pendientes (píldora + marca Chasis) (07-06).
- Menú contextual Mis pendientes (Reparación): Marcar/Quitar por cerrar; "Entregar a <glass>" / "Deshacer entrega" (solo si hay `AG` abierta y no es pestaña Glass). Pestaña Glass: "Marcar que llegó" / "Deshacer llegada"; "Añadir glass" **oculto** mientras exista normal abierta del IMEI sin entrega (08-28 entrega-glass).
- Pulido Mis pendientes: menú "Editar comentario / modelo / cliente / Copiar celda" solo SUPERTECNICO; reasignar solo desde Asignaciones (07-02 paridad-pulido).
- Píldoras bajo el IMEI: "Glass: <técnico>" en fila normal mientras la glass no tenga entrega; "Rep: <técnico>" (azul) en fila glass con normal abierta, persiste tras "Llegó" (08-28).
- ⚠ solo-cliente: badges/contadores calculados en cliente sobre listas cargadas (`getTotalItems` de cada controlador) (07-01); textos de menú/badge/tooltip de entrega en helper `EntregaGlass` (08-28); gating de menús por `Sesion.esSuperTecnico()` (06-22; 07-02).

## 3. Formulario de reparación (modal por componente)

**Reglas**
- Filas por grupo de componente según modelo; el modal recibe **tipo**: Glass → solo `g`+`mc`+`otro`; Reparación → todo menos `g`/`mc` (06-30 separar-glass).
- "Otras acciones": lista de acciones libres con descripción obligatoria; se guardan como fila `Reparacion_componente` con `ID_COM=otroi<modelo>`, `CANTIDAD=0` (neutras de stock), `ES_REUTILIZADO=0`; líneas vacías se descartan; una asignación puede cerrarse solo con "otros"; en historial Componente=`otroi<modelo>`, Observaciones=descripción; editar → editor de texto simple (06-11 rediseno-fila-otro).
- Borrador persistente en servidor (`Reparacion_borrador`, JSON, 1 por asignación): auto-guardado debounced ~2-3 s + flush al cerrar; se elimina el diálogo "salir sin guardar" en flujo nuevo (se mantiene en edición de `R*`); se borra tras guardado exitoso, al borrar asignación (cascade) y al reasignar; sin botón de descartar; al aplicar borrador obsoleto se aplica lo válido e ignora lo inválido; indicador sutil "borrador recuperado" (06-12 borrador-persistente).
- "Guardar fila": visible si `isActiva() && !esAgotadoNuevo()` y sin solicitud bloqueante; inserta un `R*` real con fecha real sin cerrar la asignación; fila bloqueada "✓ Guardada [hora]" (verde `#2E7D32`/`#E8F5E9`); sin deshacer para el técnico (solo SUPERTECNICO borra desde historial); borrador anota `guardada/idRepGenerado/fechaGuardado`; al reabrir con filas guardadas se verifica contra `GET /reparaciones/imei/{imei}` y si el `R*` ya no existe la fila se desbloquea sola; botón final envía solo filas no guardadas y cierra (06-16 guardado-individual). Log `GUARDAR_FILA_INDIVIDUAL`.
- Hueco derecho de la fila alterna: "⚠ Solicitud pieza" / "⚠ En camino" / "✓ Recibido" / "Guardar fila" / "✓ Guardada"; el estado "en camino" solo cuenta pedidos `en_camino` (no `pendiente`) (06-16; 06-12 estado-pendiente).
- Aviso de asignación cruzada: "⚠ Este IMEI también está asignado a — Reparación: Juan (tú) · Glass: Ana · Pulido: Luis"; excluye la asignación en edición; solo categorías con ≥1; "(tú)" si `idTec == sesión`; oculto si no queda ninguna (07-01 aviso-asignacion-cruzada). Endpoint `GET /reparaciones/imei/{imei}/asignaciones-activas`.

**⚠ solo-cliente**: agrupación por prefijo `A/AG/AP` y formato del aviso cruzado (07-01); criterio `isActiva()` y visibilidad de "Guardar fila" (06-16); serialización/aplicación del borrador y detección de filas borradas (06-16); filtrado de filas por tipo y detección "es otro" por prefijo (06-11; 06-30); descarte de líneas vacías y `cantidad=0` (06-11).
**Descartado**: "otro" genérico sin modelo; borrador en edición de `R*` y en pulidos; guardado individual de filas agotado-solo; deshacer accesible al técnico.

## 4. Pulido

- Tipo distinto: sin pieza, sin modal, se completa por lote; asignación `AP`, historial `P` (06-30).
- Cola de pulido en el modal: una sola lista (nace "verde"), técnico superior que hereda cada IMEI escaneado (override por fila), 1 técnico por fila, comentario vacío por IMEI, cliente inline con "— Sin cliente —"; fila sin técnico → "(sin técnico)" en rojo y **bloquea Guardar**; chequeo `existeAsignacionParaTecnico(imei, tec, "P")` al guardar (07-02 cluster-d fase2).
- Paridad: "Asignado por" en Mis pulidos e Historial pulido; reasignar pulido actualiza `ID_TEC_ASIGNA` (campo opcional en PATCH); cliente por IMEI al asignar (07-02 paridad-pulido).
- Pulido **no** cuenta en carga de técnicos ni en predicción de glass; no lleva urgente automático; sí puntúa 0,25 en estadísticas (07-09; 09-05; 06-24; 09-01).
- Revisión logística: crear pulido también resetea el OK y bloquea el toggle (06-30).
- ⚠ solo-cliente: validación de técnico obligatorio y bloqueo de Guardar; sync de cliente por IMEI (07-02 fase2).
- Aplazado: multi-técnico/modelo en el detalle de pulido (Fase 3 cluster D).

## 5. Glass (AG/G, entrega, modelo vivo, predicción)

**Reglas**
- Glass ≈ Reparación (consume stock, mismo modal filtrado, incidencias, se completa por IMEI); completar `AG` genera `G` (parametrizar `nextId`); solo hacia delante, sin reclasificar `R` históricas con pieza glass; incidencias se rastrean por tipo; trabajo combinado = dos asignaciones (06-30 separar-glass).
- Entrega ("Entregar a"/"Llegó"): vive en la fila `AG` (`ENTREGADO_AT`, `ENTREGADO_POR`); entrega el **dueño** de la `A` normal a **todas** las `AG` abiertas del IMEI; "Marcar que llegó" por el dueño de la `AG`; **quien marca, desmarca** (403 si otra firma); re-entregar sobrescribe; se conserva ante reasignaciones de ambos lados; nueva `AG` por incidencia nace sin entrega; la `G` hereda la entrega (sub-etiqueta "Llegó dd/MM hh:mm" en Agrupado e Historial). Validaciones 422/404/403 en orden definido; sin lock optimista (08-28 entrega-glass).
- Modelo vivo en el modal: el modelo pertenece al IMEI (ver §1) (09-04).
- Predicción: casilla "Lleva glass" (solo cola Reparación, por IMEI, se resetea, no pegajosa); invariante en verdes: casilla ⇔ glass del IMEI en cola Glass (ambos sentidos); en rojas es intención hasta Asignar; la glass nace **verde "auto"** con modelo y cliente de la reparación, sin comentario/chasis/urgente; reeditar la reparación re-predice si sigue auto; casilla deshabilitada "ya tiene glass: X" si hay `AG` abierta en BD; ✕ en reparación retira su glass, ✕ en glass desmarca la casilla (09-05 glass-prediccion).
- Habilitados: `Tecnico.ES_GLASS` (default 0), `PATCH /api/tecnicos/{id}/glass` solo SUPERTECNICO (ADMIN 403), log `HABILITAR_GLASS`/`DESHABILITAR_GLASS`; diálogo "Técnicos de glass" en Asignaciones (admin solo lectura) (09-05).
- Algoritmo `PrediccionGlass`: candidatos = activos con `esGlass` sin glass abierta del IMEI (BD) ni glass verde del IMEI en el modal; carga en **fracción de jornada 9h sin escalar** (glass 1/17, chasis 1/8, normal 1/25, por cerrar 1/12 de su tipo, solicitud pendiente 0, pulido 0) = hecho hoy + pendiente BD + verdes del modal; alcance Pedidos si el IMEI tiene cliente, Total si no; gana menor carga, empate alfabético `compareToIgnoreCase`; sin candidato → roja (09-05).

**UX**: badge "→ Técnico H" (sin hora) arriba; "Llegó 10:42" / "Llegó 27/08" si no es hoy; tooltips "Entregado a Técnico H por Técnico J, 28/08 10:42" / "Bajado por Técnico J, 28/08 10:42"; palabra "Llegó" (no "Recibido"); paleta índigo (08-28).
**⚠ solo-cliente**: `PrediccionGlass` completo (elección del técnico) (09-05); `EntregaGlass` (textos/visibilidad de menú) (08-28); propagación/siembra de modelo en el modal (09-04); fallback "nadie habilitado → glass roja" si el servidor no manda `esGlass` (09-05).
**Descartado**: predicción sin marca manual; registrar la devolución glass→arriba; selector manual de destinatario; notificación push al técnico de glass; auto-deshacer glass guardada; estadísticas de tiempo de glass (F4); completar glass por lote.

## 6. Stock / inventario / SKU

- Columna "En camino" del stock = pedidos `en_camino`+`parcial` (nunca `pendiente`) (06-12 estado-pendiente).
- Acciones "otro" y filas con `CANTIDAD=0` no mueven stock (06-11).
- Categorías de pieza por prefijo de SKU: `bat`→Batería, `cha`→Chasis, `g`→Glass, `cam`→Cámara, `lcd`→Pantalla, `mc`→Marco, `otro`→Otros; match más largo primero (06-24); corrección de etiquetas cruzadas en Estadísticas Stock (`g`=Glass, `lcd`=Pantalla) (09-01).
- Filtro de proveedores multiselección (helper `MultiSelectDropdown`, popup con checkboxes, fix de `:selected`) (06-07 multiselect).
- ⚠ solo-cliente: derivación de categoría de pieza desde el SKU y filtro "Pieza" (06-24).
- Aplazado: stock mínimo automático (Fase 5) y unificación STOCK_MINIMO master/slave (07-06 roadmap).

## 7. Compras y pedidos (componentes y "otros")

**Reglas**
- Ciclo: `pendiente → en_camino → {parcial, recibido} | cancelado`; nuevo pedido nace `pendiente`; Confirmar (→en_camino) y Borrar (solo pendiente) con guard `ESTADO='pendiente'` y 409 si cambió; Cancelar no aplica a pendiente; `FECHA_PEDIDO` no se resetea al confirmar; `pendiente` no cuenta en stock ni badges de solicitudes (06-12 estado-pendiente). Renombres API: `/en-camino`, `/cantidad-en-camino/{idCom}`.
- Pedidos "Otros" (`Compra_otro`): misma máquina de estados sin efecto en stock, FK solo a Proveedor, `desrecibir` siempre permitido; `ProveedorDAO.tienePedidos` cuenta también otros; logs `*_OTRO`; validación Concepto no vacío, Cantidad>0, parcial ≤ cantidad (06-26 pedidos-otros).
- Editar "otros": proveedor, cantidad, urgente, precio, divisa y concepto; editable en `pendiente` o `recibido`; solo ADMIN (mismo rol que el editor normal); lock optimista (07-06 fase1-quickwins ítem 3).
- Bloqueo optimista `UPDATED_AT` → `StaleDataException`/409 → cliente recarga y avisa (06-12; 06-23; 06-26).

**UX**
- Menú contextual por estado (solo SUPERTECNICO): pendiente → Confirmar pedido · Editar · Borrar (con confirmación); en_camino → Recepción parcial · Confirmar recibido · Editar · Cancelar; parcial → Recibir resto · Cerrar sin resto; recibido → Revertir a En camino · Editar (06-12).
- Chips de filtro de estado; "pendiente" activo por defecto junto a "en camino" y "parcial"; fila/badge `pendiente` **ámbar suave** (constantes `FILA_PEDIDO_PENDIENTE_BG/BRD`), en_camino gris; ⚠ urgente también en pendientes (06-12).
- Toggle **Componentes | Otros** a la derecha del título (`toggle-pill-left/right`); botón "Nuevo pedido"/"Nuevo otro pedido"; buscador filtra por Concepto en Otros; tabla Otros: Pedido · Concepto · Proveedor · Cant. · P.Unit · EUR · Estado; conversión divisa→EUR reutilizada (06-26).
- Filtros: buscador + DatePicker Desde/Hasta (editor deshabilitado) + "Limpiar filtros" (patrón de referencia, 06-16 filtros-log).
- ⚠ solo-cliente: filtrado de tablas (FilteredList) y menú por estado (06-12; 06-26).
- Descartado: confirmar/borrar en bloque; recalcular tipo de cambio al confirmar; CSV y estadísticas de "otros".

## 8. Lotes / teléfonos / IMEI / ciclo de vida / revisión / envío

**Historial y Agrupado**
- Historial: toggle de 3 `Reparaciones | Glass | Pulido`, **siempre plano**; apartado **"Agrupado"** aparte (maestro por IMEI + drill-down DETALLE con los 3 tipos en orden cronológico), componente compartido para los 3 roles (06-30). (Antes: pill Agrupado|Plano con memoria de sesión, 06-11 desagrupar.)
- Técnico: en DETALLE ve las ajenas **siempre**, atenuadas (opacidad 0.45) debajo de las propias; contador "X propia(s) + Y de otros" / "X reparaciones"; en PLANO solo las suyas (06-22 otros-tecnicos-por-defecto).
- Admin/Super: filtro de técnico en agrupado = IMEIs donde **alguno** intervino (OR); en DETALLE se muestran todas, filtradas arriba y resto atenuado; contador "X de filtrados + Y de otros" (06-22 filtro-tecnico-agrupada).
- Filtros: Cliente (multiselect, solo Agrupado, con "(Sin cliente)"), Pieza (multiselect, solo Plano, categorías presentes); barra responsive (FlowPane) (06-24).
- Columnas: "Asignado por" solo en plano; "Cliente" y `Telefono.OBSERVACION` solo en agrupado (06-22; 06-23).
- Selección persistente: volver del detalle re-selecciona el IMEI y restaura scroll; clicar "IMEIs" en detalle = "← Volver" (07-06 ítem 1).
- Revisión logística: toggle "Revisión" (ON verde "OK" / OFF gris "—"; deshabilitado con opacidad reducida) solo en maestro; bloqueado en OFF si hay **cualquier** asignación abierta; solo SUPERTECNICO edita, Admin solo lectura; 409 → alerta "Este IMEI tiene asignaciones activas…" + refrescar fila; error red → revertir; CSV "Revisión logística" Sí/No; log `MARCAR_REVISION`/`QUITAR_REVISION` (06-17; 06-22 log-revision; 06-30). Sin lock optimista ni push (descartado). Concepto a retirar cuando llegue el OK nuevo de F2.

**Filtro de IMEI (todas las listas, todos los roles)**
- Helper `FiltroImei`: solo dígitos y comas, separador visible `", "`, auto `", "` tras 15 dígitos, tokens >15 se parten en trozos de 15 (resto <15 queda incompleto), idempotente; borde: vacío → sin borde, incompleto → **rojo**, válido → **verde**; se filtra por los IMEIs de 15 dígitos; sin reject-all (06-29 filtro-imei-pegado). Estilos de borde difieren por vista y se preservan (2 vistas con estilo distinto).
- ⚠ solo-cliente: canonicalización y clasificación del filtro (06-29); opacidad/orden de ajenas y contadores (06-22 ×2); predicados de Cliente/Pieza (06-24); agrupación por IMEI (`GrupoImei`) y drill-down (06-11; 06-30).

**Fase 2 — lotes y ciclo de vida (07-07 fase2-lotes, aprobada; F2a hecha, F2b/F2c pendientes)**
- Todo teléfono entra por **lote** (entidad `Lote`; alta manual = lote pequeño). Importador lee la plantilla xlsx de la otra plataforma (cabeceras filas 5-6, datos desde 7); **solo entran filas Status=0**; multi-batch → un lote por Batch Number; duplicados finales (OK/Enviado/Desguace) re-entran previa confirmación, activos → conflicto; se guardan IMEI, modelo mapeado, storage, color, grade→`GRADO_PROVEEDOR`, precio compra, batch/supplier. **⚠ parser en cliente (POI)**; servidor recibe JSON limpio, agnóstico del parser (una web usaría SheetJS).
- Estados: `RECIBIDO → EN_REVISION → BLOQUEADO | EN_REPARACION (implícito) | OK → ENVIADO | DESGUACE`; OK siempre lo da una persona; teléfono OK con trabajo nuevo vuelve a EN_REPARACION; devolución re-entra a EN_REVISION.
- Ubicación **siempre derivada** (Almacén/Para revisar/Bloqueo/Reparaciones/Listos/Pedidos); Reparaciones si hay cualquier trabajo abierto; sub-ubicaciones como conjunto (pantalla: pulido antes que glass; cuerpo: normal); ⏳ pieza solicitada; "es pedido" = tiene cliente; override manual con motivo; `Movimiento_telefono` append-only.
- Revisión: estética (grado propio del chasis C/B/A-/A/A+; PANT = P/G) y funcional (batería % — **<85 → reparación obligatoria**; táctil/quemada/mal; cámara mancha/lente; altavoces/mic; Face ID; MS + texto; bloqueo operador; observación; casilla marcada = defecto), guardadas por separado; lo funcional decide el OK. Históricos fuera del ciclo, sin migrar `REVISION_LOGISTICA`.
- Permisos hasta F3: importar/revisar/OK/mover/enviar/desguace = SUPERTECNICO; ADMIN lectura; técnicos consulta.
- Pospuesto: entidad Pedido, sugerencia automática de reparación, integración del programa de diagnóstico, alertas de caducidad por caja, permisos por caja (F3 rol LOGISTICA).

## 9. Clientes

- Entidad `Cliente` (solo nombre + activo + updated_at); FK `ID_CLI` en `Telefono` (el cliente es del dispositivo: cambiarlo afecta a todas sus reparaciones, asumido); seed genéricos **WEB** y **OTRO** (detalle en `Telefono.OBSERVACION`); opcional al asignar; no se crea al vuelo; borrado = desactivar (inactivos fuera del desplegable, visibles en históricos) (06-23 clientes).
- Permisos: escritura de cliente (catálogo, asignación, cambio por IMEI) y edición de observación **solo SUPERTECNICO** (`@PreAuthorize`); `POST /telefonos` con `idCli` y rol ≠ SUPERTECNICO → 403; ADMIN/TECNICO solo lectura (pantalla en modo `soloLectura`) (06-23).
- Lock optimista completo en `Cliente` y `Telefono` (retrofit de observación y revisión logística) (06-23).
- Logs: `CREAR/EDITAR/ALTA/BAJA_CLIENTE`, `ASIGNAR_CLIENTE`, `CAMBIAR_CLIENTE`, `EDITAR_OBSERVACION` (06-23).
- UX: módulo "Clientes" en navbar (4º botón, visible a todos); selector con buscador (píldora oscura + popup lista filtrada, no ComboBox); "Editar cliente" por menú contextual (icono lápiz) en Asignaciones y Agrupado, abre con el actual preseleccionado, opción "— Sin cliente —", **sin confirmación**; columna estrecha (no apilar bajo IMEI) (06-23).
- ⚠ solo-cliente: ocultación de acciones de escritura por rol en la pantalla y menús (el servidor sí blinda las escrituras de cliente).
- Aplazado: datos de contacto; ficha de cliente / ver sus teléfonos (`GET /clientes/{id}/telefonos`, enfoque 2); filtro rápido descartado como parche; aviso automático para WEB/OTRO descartado.

## 10. Estadísticas (puntos, horario, finde, tarjeta meses)

**Modelo de puntos (servidor)** (09-01 estadisticas-puntos)
- Tabla `Dificultad_puntos` editable: bateria 1,00 · camara 0,70 · chasis 2,00 · marco 0,50 · pantalla 1,00 · glass 0,50 · otro 0,50 · pulido 0,25. Mapeo prefijo→clave en orden `bat, cha, cam, lcd, mc, g` (g al final), no-match → otro.
- R y G: suma de piezas usadas, **cada fila puntúa una vez (CANTIDAD no multiplica)**; reutilizadas e incidencias puntúan; solicitudes puras no; sin piezas → otro; P → pulido fijo. Solo `FECHA_FIN NOT NULL`; fecha del periodo = fecha **de Madrid** (09-08). Valores vigentes re-valoran el pasado (sin histórico).
- Endpoints: `GET /reparaciones/estadisticas/puntos` (granularidad, desde, hasta) → `{nombreTecnico, periodo, puntos, puntosNormales, puntosGlass, puntosPulidos, nNormales, nGlass, nPulidos, nSinPiezas, nImeis, puntosJornada, nImeisJornada}`; `GET/PUT /valores-dificultad` (solo actualiza claves existentes, puntos ≥ 0, log `EDITAR_PUNTOS`); el endpoint viejo no se toca (09-01; 09-08).
- Exclusión: `Tecnico.ES_ESTADISTICA` (default 1), `PATCH /usuarios/tecnicos/{id}/excluir-estadisticas|incluir-estadisticas` solo ADMIN, logs `EXCLUIR/INCLUIR_ESTADISTICAS`; el endpoint de puntos sigue devolviendo a todos (09-02 ronda2).
- Horario (`util/Jornada`): entrada 8:30; salida L-M 18:00, X-J 17:00, V 14:30; S-D sin jornada; margen **30 min antes / 15 después** → franjas 8:00–18:15 / 8:00–17:15 / 8:00–14:45, hora Madrid; `enJornada` por cierre. Constantes en código (09-08 horario).

**Reglas de presentación (cliente)**
- Métricas: Puntos = total; Puntos/día = total ÷ días laborables L-V simples (festivos no descontados); periodo en curso divide por días transcurridos; toggle deshabilitado en Día con tooltip "En granularidad Día ambas métricas coinciden" (09-01; 09-02).
- Promedio (línea discontinua naranja `#C07800`, siempre visible): media por técnico-periodo **trabajado** (ausencias no diluyen) sobre el rango mostrado; "Por encima/Por debajo" con cabecera "Promedio del equipo (30 días con actividad, en horario): …". Medias x̄ por serie visibles por defecto ("Ocultar medias"). Huecos sin actividad se pintan a 0 (09-01).
- **Suma, no promedia**: finde y fuera de horario suman en totales (tarjetas, series, Equipo, numerador Puntos/día, chips IMEIs, popover) pero **no entran en ninguna media** (Promedio, x̄, IMEIs típicos, referencias de tarjetas), en todas las granularidades; par (técnico, periodo) cuenta como trabajado solo con puntos de jornada > 0; tooltip añade "X puntos fuera de horario"; con servidor viejo, fallback = regla del finde solo en Día con coletilla "L–V" (09-07 finde; 09-08 horario).
- Tarjetas: "Puntos · <último mes cerrado> · equipo/tú" con variación real vs mes previo `round((cerrado−previo)/previo×100)`, "+5% vs julio (2322,0)", **verde `#2E7D32` si ≥0, rojo `#C62828` si <0**, sin línea si no hay previo; "Puntos · hoy": puntos de hoy ÷ media de ese día de semana del último mes cerrado (solo jornada; laborable sin muestras → media global por día trabajado; finde sin muestras → sin línea), porcentaje **entero truncado**, gris `#7A8A9A` <100 %, verde ≥100 %, nunca rojo; "IMEIs típicos" media de IMEIs de jornada (09-02; 09-08; 09-09).
- Ventana: sin flechas ni slider; rango = últimos 30 días con actividad / 16 semanas / 12 meses / 5 años o el filtro Desde-Hasta entero; etiqueta "30 días con actividad · rango" (09-01; 09-02).
- Arranque limpio: solo Promedio; "Equipo (suma)" desmarcada; técnicos desde `+ Técnicos` (buscador, activos primero, inactivos tras separador, colores deterministas) y se quitan clicando la leyenda; crosshair en hover; clic en vértice → popover "14,5 pts = 9 normales (11,0) + 3 glass (2,0) + 6 pulidos (1,5) · 2 sin piezas" con "Ver IMEIs" (Agrupado con fechas+técnico) y "Ver en Historial"; Promedio no clicable (09-01).
- Roles: TECNICO y SUPERTECNICO ven **solo su serie y tarjetas "tú"** + Equipo/Promedio/IMEIs típicos anónimos, sin desplegable ni clic en leyenda, navegación solo desde su serie; ADMIN todo + ⚙ Valores + 👥 Técnicos (09-07 solo-propias; 09-02).
- Glass suma con Reparaciones en la vieja estadística (`R% OR G%`), pulido fuera (06-30).

**⚠ solo-cliente (crítico)**
- ⚠ Filtrado por rol: los endpoints devuelven **todos los técnicos** a cualquier autenticado; que Técnico/Super vean solo lo suyo es cliente (09-07; 09-01 §5). Compromiso: en F3 el servidor devolverá a no-ADMIN solo sus filas + agregados de equipo.
- ⚠ Exclusión de técnicos aplicada en cliente (Promedio, Equipo, tarjetas, desplegable) por `nombreTecnico` (09-02).
- ⚠ `PuntosEstadistica`: días laborables, puntos/día, promedio por periodo trabajado, `esLaborable/soloLaborables`, tarjetas (variación, objetivo del día por día de semana, truncado/redondeo, colores), textos de tooltip/popover, extra = puntos − puntosJornada, fallbacks con servidor viejo (09-01; 09-02; 09-07; 09-08; 09-09).
- ⚠ Relleno de huecos a 0, ventana/etiquetas, crosshair, colores de series (09-01).
**Descartado/aplazado**: revisiones en puntos; ranking/dashboard/bullet graphs/XmR (web F4); festivos y jornada por horas en el denominador; histórico de valores; horario configurable; tarjeta de progreso "a la misma altura" y elegir meses con flechas; ocultar Equipo/Promedio a no-admin; CSV de estadísticas (no existe).

## 11. Notificaciones / solicitudes de pieza

Sin spec dedicada. Reglas dispersas: `MainController` tiene un poller propio `poller-notificaciones` (badges/alertas cada 60 s, siempre activo) (06-29 manejo-errores); badge "en camino" de solicitudes cuenta solo pedidos `en_camino` (06-12); estados visuales de solicitud en la fila del modal (06-16); una solicitud activa hace que la asignación cuente 0 en carga hasta recibido/rechazada (07-08); las solicitudes puras no puntúan (09-01). Notificación push al técnico de glass descartada (08-28). Alertas de caducidad por caja pospuestas (07-07).

## 12. Carga / capacidad

- v1 (07-08 por-cerrar-carga): pesos normal 1 · chasis 2 · por cerrar 0,083 (manda sobre chasis) · glass 1,5; solicitud pendiente → 0; solo abiertas **con cliente**, rep+glass, pulido fuera; cifra = unidades (coma decimal), % cuota solo en hover; ventana "Carga de técnicos (Pedidos)" con barras azul `#1565C0` sobre `#E8EAF0`, una fila por técnico activo (0 incluidos), desc; solo SUPERTECNICO y ADMIN; filtros no alteran el %; sin persistencia. Ayuda ⓘ retirada.
- v2 (07-09 carga-capacidad, released 0.16.0): fracción de jornada — chasis 1/8, glass 1/17, normal 1/25 (`TOPE_NORMALES_9H=25`, decisión de taller), por cerrar 8,3 % de su tipo, solicitud 0, pulido 0 (decisión A5, no "arreglar"); jornada L-M 9h, X-J 8h, V 6h, S-D 0 (escala lineal; día sin jornada → "sin jornada hoy", solo unidades); % = (completadas hoy + abiertas) ÷ jornada, puede pasar de 100 (barra satura, número sigue); backlog cuenta.
- UX v2: toggle **Pedidos | Total** solo en la ventana (Pedidos por defecto); punto de identidad fijo (violeta Pedidos, azul Total); color por nivel sincronizado barra+número: azul <70 %, ámbar 70–89 %, rojo ≥90 %; barra en dos tramos (hecho hoy sólido + pendiente claro); hover = unidades + desglose; modal de asignar muestra solo `(●62%)` de Pedidos (07-09).
- Servidor: solo endpoint aditivo "completadas hoy por técnico".
- ⚠ solo-cliente: **todo el cálculo** (`CargaTecnicos`: pesos, topes, jornadas, niveles, alcances) (07-08; 07-09).
- Descartado: pesos/topes configurables o por técnico (F4), polling propio, histórico de carga, vínculo automático por-cerrar↔glass.

## 13. Conexión / errores / sesión / borrador

- Clasificación en `ApiClient`: 401 → `SesionExpiradaException` ("Sesión expirada"); 5xx/IOException/timeout → `ConexionException` ("El servidor no está disponible. Inténtalo de nuevo en unos segundos."); 403 "No tienes permisos"; 409 → `StaleDataException` (mensaje del cuerpo vía `extractMessage`); 404/422 (06-29 manejo-errores; 06-22 validar-nombre).
- Tres cubos: **A** transitorio desde poller/carga inicial → banner global bajo la navbar "⚠ Sin conexión con el servidor. Reintentando…" (ámbar, ~24 px, no bloquea, sin deshabilitar botones), poller pasa de 60 s a **5 s** mientras desconectado; **B** 401 con sesión activa → hook central deduplicado, modal bloqueante "Tu sesión ha caducado. Vuelve a iniciar sesión." → logout + login, se detienen pollers, flag "logout en curso" evita más diálogos; **C** resto (4xx accionables y transitorios de acción de usuario) → diálogo con owner = ventana enfocada, con dedup (06-29).
- 401 **durante el login** = credenciales incorrectas, no dispara auto-logout; en cambio de contraseña, 401 = "Contraseña actual incorrecta." (06-07 cambiar-password; 06-29).
- Evitar flapping con varios pollers (Main + módulo visible) — regla a fijar por último resultado; llamadas fuera del hilo UI deben marshalear (06-29).
- Borrador del formulario: ver §3.
- ⚠ solo-cliente: toda la política de errores/banner/auto-logout; el contrato API no cambia (06-29).
- Descartado: deshabilitar acciones mientras desconectado; barra de conexión elaborada; idempotencia de escrituras (memoria del proyecto).

## 14. Logs / usuarios / auth

**Logs**
- `DETALLE` texto plano "CLAVE: valor, …", cambios "ANT → NUE" solo de los campos que cambian; prioridad traducida 0→Normal, 1→Urgente, 2→Muy urgente; columna `MOTIVO` solo obligatoria en `ELIMINAR_REPARACION` (borrar asignación no pide motivo); tabla completa de acciones y detalles en 06-19 logs-enriquecidos; se elimina `REASIGNAR_TECNICO` (código muerto).
- Todas las acciones de un dispositivo llevan `IMEI: …` en el detalle (forward-only, sin backfill; `COMPLETAR_PULIDO_LOTE` fuera); IMEI se resuelve **antes** del delete; si nulo se omite (06-30 log-busqueda-imei).
- Acciones añadidas por specs posteriores: `MARCAR/QUITAR_REVISION`, `*_CLIENTE`, `EDITAR_OBSERVACION`, `*_OTRO`, glass (`CREAR_ASIGNACION_GLASS`, `COMPLETAR_GLASS`, …), `MARCAR/QUITAR_POR_CERRAR`, `ENTREGAR_GLASS`/`DESHACER_ENTREGA_GLASS`, `EDITAR_PUNTOS`, `EXCLUIR/INCLUIR_ESTADISTICAS`, `HABILITAR/DESHABILITAR_GLASS`, chasis. El job de urgentes **no** loguea en BD.
- Vista de logs (solo ADMIN): filtros acción/técnico/desde/hasta **server-side** (`GET /api/logs?accion&tecnico&desde&hasta`); buscador de texto libre **client-side** sobre usuario|acción|detalle (contains, case-insensitive, campos separados); doble clic en fila → popup con DETALLE + "MOTIVO: …"; DETALLE truncado con elipsis; sin columna MOTIVO; lista de tipos de acción **hardcodeada en cliente** (06-16 filtros-log; 06-19).
- `ConfirmDialog.mostrarConMotivo`: TextArea "Escribe el motivo del borrado…", botón confirmar deshabilitado hasta texto no vacío (06-19).
- ⚠ solo-cliente: buscador de texto libre; lista `TIPOS_ACCION` (debe mantenerse al día con cada acción nueva) (06-16; 06-19; 06-30 separar-glass).
- Descartado: paginación de logs; columna IMEI estructurada; persistir filtros entre aperturas; backfill.

**Usuarios / auth**
- Cambiar contraseña (todos los roles): modal 380 px navy `#001232`, 3 campos con ojo; validación inline en orden: vacíos → "Rellena todos los campos.", <6 → "La contraseña debe tener al menos 6 caracteres.", no coinciden → "Las contraseñas nuevas no coinciden."; `PATCH /api/auth/cambiar-password`; 401 → "Contraseña actual incorrecta."; 204 → alerta "Contraseña cambiada correctamente."; log `CAMBIAR_PASSWORD` (06-07).
- Alta de técnico: nombre de técnico y login únicos **case-insensitive + trim**, validado en servidor (409 "Ya existe un técnico con ese nombre." / "Ese nombre de usuario ya existe."), el cliente muestra el mensaje del servidor (06-22 validar-nombre). Sin UNIQUE en BD (posible refuerzo futuro).
- Roles: TECNICO / SUPERTECNICO / ADMIN; rol LOGISTICA previsto en F3 con enforcement de autorización en servidor (07-06 roadmap; 07-07).
- ⚠ solo-cliente: validaciones previas del formulario (obligatorios, ≥6, coinciden) (06-07; 06-22).

---

## Transversal

- **Fechas/hora**: BD y contenedor en UTC; el cliente convierte a `Europe/Madrid` para mostrar (`FechaUtils`, plan timezone 06-19); formatos vistos en specs: `dd/MM` + `HH:mm` en badges, `dd/MM/yyyy HH:mm` en CSV, "Actualizado HH:mm" = hora local del PC; cortes de día (job urgentes, estadísticas, jornada) en Madrid; IDs `yyyyMMdd` en Madrid (06-24; 08-28; 09-08).
- **Números**: coma decimal; un decimal salvo entero (carga, puntos) (07-08; 09-02).
- **Refresco**: poller 60 s en Main (notificaciones) y en el módulo visible (Stock, Reparaciones); 5 s en modo desconectado; `cargar()` tras cada acción, al recuperar el foco de la ventana y al pulsar "Actualizado HH:mm"; Admin y Estadísticas sin poller propio (06-29; 06-29 indicador-asignados). La web debe replicar la frescura equivalente (polling/refetch on focus).
- **Paginación**: no existe "cargar más" en ninguna spec; listas completas en memoria y filtrado client-side (logs sin paginación por decisión, 06-16).
- **Errores/sesión**: ver §13 (banner no bloqueante para fondo, diálogo para acciones, modal bloqueante para 401).
- **Concurrencia**: lock optimista `UPDATED_AT` → 409 en Cliente, Telefono, Compra_*; **sin** lock en toggles booleanos (urgente, chasis, por cerrar, entrega, revisión) — último gana (06-23; 07-08; 08-28; 06-22 log-revision).
- **Confirmaciones**: `ConfirmDialog` para borrados (motivo solo en reparaciones), descartar lote no vacío, borrar pedido pendiente; **sin** confirmación al editar cliente; sin "salir sin guardar" en el formulario nuevo (hay borrador).
- **Filtros de listas**: barras de filtros responsive (FlowPane); multiselects con checkboxes y etiqueta "N seleccionados"; DatePickers con editor deshabilitado; botón "Limpiar filtros"; pills `toggle-pill-left/right` para modos; filtro IMEI multi con borde rojo/verde (06-07; 06-16; 06-24; 06-29).
- **CSV**: cada vista exporta lo visible con sus columnas (plano = por reparación; agrupado = por IMEI + "Revisión logística"; Asignaciones + "Entregado"); estadísticas y "otros pedidos" no exportan (06-11; 06-17; 06-30; 08-28).
- **Roles en UI**: patrón `setSoloLectura`/gating por `Sesion.es*()`; el servidor solo blinda con `@PreAuthorize` las escrituras de cliente, estadísticas admin, glass habilitados, compras-otros y las validaciones de propiedad de por-cerrar/entrega; el resto de la autorización por rol es de cliente (memoria de seguridad; 09-07).
- **Compatibilidad**: campos de API siempre aditivos y opcionales; cliente tolera `null` con fallback (08-28; 09-02; 09-05; 09-08).

## Lista consolidada de ⚠ solo-cliente

1. Autorización por rol en la UI (solo lectura admin, menús por `esSuperTecnico`, ocultar acciones) — el servidor no lo hace salvo los endpoints listados (06-22; 06-23; 07-02; 09-07).
2. Estadísticas: filtrado "solo las mías" (Técnico/Super), exclusión de técnicos, Promedio/x̄/IMEIs típicos, días laborables, puntos/día, finde/horario en medias (con fallback), tarjetas (variación mes cerrado, objetivo del día por día de semana, truncado vs redondeo, colores), huecos a 0, ventana y textos (09-01; 09-02; 09-07; 09-08; 09-09).
3. Carga de técnicos v1/v2: pesos, topes (8/17/25), jornadas por día, niveles de color, alcances Pedidos/Total, "sin jornada hoy" (07-08; 07-09).
4. Predicción de glass (`PrediccionGlass`): candidatos, carga en fracción cruda, alcance por cliente, empate alfabético (09-05).
5. Modal de asignación: colas rojo/verde, bloqueo de Guardar, dedup por cola, pegajosos por cola, propagación de cliente/modelo por IMEI, tri-estado cliente, invariante casilla⇔glass, contadores de cola, parseo de pegado `ImeiUtils` (06-12; 06-29; 07-02 ×3; 09-04; 09-05).
6. "N asignados" derivado de la lista (no del endpoint) y su ocultación con píldoras (06-29; 08-28).
7. Entrega glass: textos de badge/tooltip/menú y visibilidad (`EntregaGlass`), "Añadir glass" oculto (08-28).
8. Badges de pendientes (suma 3 categorías, cap 99+, sufijos de toggles) (07-01).
9. Formulario de reparación: `isActiva()`/visibilidad "Guardar fila", serialización y aplicación del borrador, desbloqueo de filas borradas, acciones "otro" (`cantidad=0`, descarte de vacías), filtrado de filas por tipo, formato del aviso cruzado (06-11; 06-12; 06-16; 06-30; 07-01).
10. Historial/Agrupado: agrupación por IMEI y drill-down, filtro técnico OR y atenuación 0.45, contadores, filtros Cliente/Pieza (categoría por prefijo SKU), selección persistente, memoria de modo (06-11; 06-22 ×2; 06-24; 07-06).
11. Filtro IMEI (`FiltroImei`): canonicalización, troceo cada 15, estado de borde (06-29).
12. Pedidos: filtrado de tablas, menú contextual por estado, conversión divisa→EUR en formulario (06-12; 06-26).
13. Errores/conexión: clasificación en cubos, banner, poller 60/5 s, auto-logout 401, dedup de diálogos (06-29).
14. Logs: buscador de texto libre y lista `TIPOS_ACCION` hardcodeada (06-16; 06-19).
15. Validaciones de formularios (password, alta de usuario) previas al servidor (06-07; 06-22).
16. Importador de lotes: parser xlsx en cliente, vista previa, mapeo de modelos y clasificación de duplicados (07-07).
17. Fechas: conversión UTC→Madrid para visualización (plan timezone 06-19).
