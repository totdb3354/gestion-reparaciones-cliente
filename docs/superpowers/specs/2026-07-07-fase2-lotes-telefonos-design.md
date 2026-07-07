# Fase 2 — Lotes y ciclo de vida del teléfono

Fecha: 2026-07-07
Estado: **Diseño en revisión por el usuario** (brainstorm completo; este documento ES el contexto canónico de la fase).
Origen: Fase 2 del roadmap de julio 2026 (ver spec de Fase 1, sección roadmap). Diagrama de flujo: `Apuntes/FLUJO_UBICACIONES_v17.drawio` (referencia; el usuario mantiene su v16+bloqueo) y `Apuntes/Modulo Ubicaciones - Spec.md` (borrador previo del usuario, jun 2026).

---

## 1. Contexto de negocio (TODO lo aprendido — no perder)

### 1.1 Cómo entran los teléfonos
- **Todos los teléfonos entran por lote** (grande o pequeño). No hay teléfonos "sueltos": el alta manual = crear un lote pequeño a mano.
- Los lotes se compran a **proveedores** (los mismos de la tabla `Proveedor` — p. ej. "Hy5").
- Hoy los lotes se suben a **otra plataforma de stock** (de origen ruso, hoja "Лист1") con una plantilla xlsx fija. Nuestra app **leerá esa misma plantilla** para minimizar fricción — el mismo fichero sirve para ambos sistemas y no hipoteca un formato propio futuro.

### 1.2 La plantilla xlsx (formato de importación)
Analizada de ficheros reales (`Upload batch with IMEI.xlsx` = plantilla; `Hy5 - 2026.07.06.xlsx` = lote real de ~395 teléfonos):
- Cabeceras en filas 5-6, datos desde la fila 7.
- Columnas: **IMEI/SN\*** · **Manufacturer\*** · **Model\*** (obligatorias) · Storage · Color · Grade · **Purchase price** · Supplier Name · **Batch Number** · Group of Goods · EAN · UPC · Description · **Status**.
- Modelos tipo "iPhone 12 mini", "iPhone 16 eSIM" — casan con el catálogo interno de variantes de la app.
- El fichero **trae el lote dentro** (Batch Number + Supplier) y puede traer **varios batch numbers**: el importador agrupa y crea un lote por cada uno.
- **Status de la otra plataforma** (0-6). En la práctica solo usan 3: `0 In process` (está en la empresa), `5 Released` (vendido y enviado, fuera), `6 Removed` (¡no es un estado real! workaround para "editar": borran y reimportan el teléfono corregido). Regla de importación: **solo entran filas con Status = 0**; el resto se lista como aviso informativo en la vista previa (útil si algún día migran su inventario completo).

### 1.3 El flujo físico del taller (cajas)
Del diagrama v16 del usuario + spec previa: ALMACÉN → CAJA PARA REVISAR → CAJAS REPARACIONES (sub-cajas pulir/glass/normal) / CAJA LISTOS / CAJA PEDIDOS → CAJA ENVIADOS; CAJA DESGUACE terminal; devoluciones re-entran a revisar. **Nueva en esta fase: CAJA BLOQUEO** (bloqueo de operador, faltaba en el diagrama).
- La caja de revisar se llena **a puñados** desde el almacén (no el lote entero de golpe); la revisión es teléfono a teléfono.
- Un teléfono puede estar **físicamente dividido**: la pantalla en el sector glass/pulido y el cuerpo en reparación. No sale de reparaciones hasta estar entero y ensamblado.
- **Orden habitual de completar trabajos: pulido → glass → normal** (costumbre, NO se impone; no siempre hay glass — combinaciones libres pero siempre ese orden relativo).

### 1.4 La revisión (dos procesos independientes)
Se registra hoy en una **etiqueta física** (foto analizada, campos exactos abajo):
- **Estética (grading)** — visual, a juicio del empleado. **El grado lo define SOLO el chasis**: escala propia **C / B / A- / A / A+** (independiente del grado del proveedor, que se guarda tal cual viene). En la etiqueta, la fila PANT no es grado: es la **reparación estética prevista de pantalla**: `P` (pulido) o `G` (glass) o nada.
- **Funcional** — con un **programa externo** de diagnóstico; el empleado transcribe a mano (el programa no exporta nada que integremos por ahora). Campos:
  - **% batería** (número). Regla: **< 85 → reparación obligatoria**; ≥ 85 opcional.
  - Pantalla: **táctil ▢ · quemada ▢ · mal ▢** (cualquiera → cambio de pantalla/LCD).
  - Cámara: **mancha ▢ · lente ▢**.
  - Sonido: **altavoz superior ▢ · altavoz inferior ▢ · micrófono ▢**.
  - **Face ID ▢** (Touch ID no aplica: solo tienen teléfonos con Face ID).
  - **MS ▢ + texto**: aviso iOS de pieza no calibrada; el texto dice qué pieza.
  - **Bloqueo operador ▢** (se detecta aquí, en la revisión).
  - Observación libre. **Semántica: casilla marcada = defecto.**
- En la etiqueta también se apunta el **cliente** → la ficha permite fijar el cliente del teléfono (campo `ID_CLI` existente).
- **Lo funcional determina el OK**; lo estético dispara pulido/glass. Ambas partes se rellenan por separado (momentos/personas distintas).

### 1.5 Conocimiento de dominio (para Fase 4 y futuro)
- Del grado se puede **prever la reparación**: C/B → casi seguro chasis; defectos estéticos de pantalla → pulido o glass; si el glass no salva la pantalla → **reparación de LCD entera**. El **color depende del chasis**: una reparación de chasis puede cambiar el color del teléfono.
- Futuro (post-implementación): que la app **sugiera** la reparación prevista. Por ahora el P/G de PANT lo escribe el empleado en la ficha.
- El **precio de compra por teléfono** viene en el fichero → alimenta el coste por teléfono de las estadísticas (Fase 4).

---

## 2. Modelo conceptual

### 2.1 Estados del teléfono (explícitos — los fija una acción)
`RECIBIDO` (importado, en almacén) → `EN_REVISION` (escaneado a la caja de revisar) → `BLOQUEADO` (bloqueo operador) | `EN_REPARACION` (implícito: tiene trabajo abierto) | `OK` (lo da una persona explícitamente) → `ENVIADO` (fuera; ≈ "Released") | `DESGUACE` (terminal; solo registro, sin alta de piezas — el circuito "reutilizado" existente es la otra punta).
- **Reglas**: un teléfono `OK` al que se le asigna trabajo **vuelve solo** a `EN_REPARACION` (hoy ya pasa con el check de revisión). La `DEVOLUCIÓN` (post-envío; NO llamarla "incidencia", que ya significa reapertura de taller) re-entra a `EN_REVISION` marcada como devolución. Desde `BLOQUEADO`: desbloqueado → destino según su ficha ya guardada; imposible → `DESGUACE`.

### 2.2 Ubicación (SIEMPRE derivada — nadie la mantiene)
- **Nivel padre (exclusivo)**: Almacén / Para revisar / Bloqueo / Reparaciones / Listos / Pedidos / (Enviado y Desguace = fuera). Derivada de estado + trabajos abiertos + cliente.
- **Reparaciones si hay CUALQUIER trabajo abierto** (A/AG/AP sin completar).
- **Sub-ubicaciones de Reparaciones (conjunto, NO exclusivo)** — el teléfono puede estar dividido: **parte pantalla** → el primer trabajo de pantalla abierto (pulido antes que glass); **parte cuerpo** → normal si hay trabajo normal. Ej.: "Reparaciones → Pulido + Normal"; al completar el pulido salta solo a "→ Glass + Normal". El orden es etiqueta, no restricción: si se completa fuera de orden, se recalcula.
- **⏳ Pieza solicitada**: flag visible encima de la sub-ubicación (es el sistema de solicitudes existente — `es_solicitud` del diagrama ≡ solicitudes de pieza actuales).
- **"Es pedido" = tiene cliente asignado** (`ID_CLI`). La derivación vive en UNA función → la futura **entidad Pedido** (agrupación de teléfonos, estados de pedido, envíos — fase posterior, ver §7) se enchufa ahí sin retrabajo.
- **Override manual** = excepción, con motivo obligatorio y registro.
- **Trazabilidad**: tabla de movimientos **append-only** (teléfono, de→a, quién, cuándo, motivo, referencia) — diseño de la spec previa del usuario.

### 2.3 Entidades nuevas / cambios de esquema (borrador para el MER)
- **`Lote`**: ID_LOTE (PK), BATCH_NUMBER, ID_PROV (FK), FECHA_IMPORT, NOTA, UPDATED_AT.
- **`Telefono`** (columnas nuevas): ID_LOTE (FK, null para históricos), ESTADO (enum §2.1, null = histórico fuera del ciclo), STORAGE, COLOR, GRADO_PROVEEDOR (texto), GRADO_PROPIO (enum C/B/A-/A/A+), PRECIO_COMPRA + DIVISA/PRECIO_COMPRA_EUR, ES_DEVOLUCION (o contador), y los que pida la ficha.
- **`Revision`** (1:N con Telefono — histórico de revisiones; una vigente): parte estética (grado, pant_prevision enum —/P/G), parte funcional (bateria_pct, pantalla_tactil/quemada/mal, camara_mancha/lente, alt_sup/alt_inf/mic, face_id, ms + ms_texto, bloqueo_operador, observacion), fechas/usuario por parte (se rellenan por separado).
- **`Movimiento_telefono`** (append-only): IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, FECHA, ID_USU, MOTIVO, REFERENCIA.
- Los **históricos** (miles de teléfonos pre-fase, solo con reparaciones): fuera del ciclo — sin lote, ESTADO null; si tienen trabajo abierto su ubicación derivada es Reparaciones; si re-entran en un lote, re-entrada normal. **No se migra** el check `REVISION_LOGISTICA` (los OK antiguos están en la otra plataforma y son prescindibles); el check actual de la vista IMEIs desaparece como concepto al llegar el OK nuevo.

---

## 3. Importador (cliente lee, servidor persiste)

- **El cliente parsea el xlsx** (Apache POI) — la vista previa interactiva lo exige; el servidor recibe el alta en bloque ya limpia (JSON), **agnóstico del parser** (una futura app web reutiliza el endpoint con SheetJS).
- Vista previa con: teléfonos por lote (multi-batch → un lote por Batch Number), **mapeo de modelos** contra el catálogo (los que no casen → se corrigen en el momento con selector; recordar equivalencias es deseable), duplicados separados en **finales** (OK/Enviado/Desguace → re-entrada legítima al lote nuevo previa confirmación, conservando su historial) y **activos** (Recibido/EnRevisión/EnReparación → conflicto, no entran, aviso), filas **Status ≠ 0 → aviso informativo, no entran**.
- Columnas que se guardan: IMEI, modelo (mapeado), storage, color, grade (→ GRADO_PROVEEDOR), purchase price, batch/supplier (→ Lote). Se ignoran: Group of Goods, EAN, UPC, Description.
- **Alta manual** = mismo flujo con lote creado a mano + pegado masivo de IMEIs (parser existente).

## 4. Revisión, acciones y UI

- **Ficha de revisión** (desde la vista de teléfonos, escaneando IMEI): dos partes guardables por separado (§1.4) + cliente. Al guardar la funcional se evalúa el veredicto: bloqueo → BLOQUEADO; inservible (decisión humana) → DESGUACE; necesita trabajos → botón que **pre-carga el modal de asignación existente** con el IMEI (pulido/glass según PANT, normal según funcional); limpio → candidato a OK.
- **OK**: acción explícita de una persona (nunca automático), también tras completarse las reparaciones.
- **Acciones**: a revisar (escaneo masivo), OK, bloquear/desbloquear, desguace (con motivo), enviar, registrar devolución, editar atributos (modelo/color/storage/grados/cliente — mata el workaround "Removed"), mover ubicación manual (override con motivo).
- **Vista IMEIs evolucionada** = inventario completo: todos los teléfonos (incl. históricos), columnas IMEI/modelo/storage/color/grados/estado/ubicación derivada (con sub-ubicaciones y ⏳)/lote/cliente/última actividad; filtros por estado, ubicación, lote, cliente, modelo, IMEI-pegado; el **drill-down actual de trabajos por IMEI se conserva** como detalle. Vista/panel de **lotes**: lista con % revisado / % OK, abrir → sus teléfonos.
- **Permisos** (hasta la Fase 3): importar, revisar, OK, mover, enviar, desguace = SUPERTECNICO (y ADMIN lectura); técnicos consulta. El rol LOGISTICA (F3) tomará el papel de operario/revisor de la spec previa del usuario.

## 5. Sub-fases de implementación (cada una sale a main funcionando)

- **F2a — Cimientos**: esquema BD (Lote, columnas Telefono, Movimiento) + importador con vista previa + alta manual + vista de teléfonos con edición de atributos y filtros. (Sin revisión aún: estados RECIBIDO y derivación básica.)
- **F2b — Revisión**: tabla Revision + ficha (2 partes) + veredicto y acciones (OK, bloqueo, desguace, enlace al modal de asignar) + estado EN_REVISION/BLOQUEADO/OK.
- **F2c — Ciclo completo**: enviar + devolución + sub-ubicaciones derivadas finas + vista de lotes + movimientos/trazabilidad visible + retirar el check de revisión antiguo.
- Cada sub-fase: su plan (writing-plans), rama, review y smoke; specs de detalle solo si una sub-fase lo pide.

## 6. Decisiones tomadas (con su porqué)

1. Formato de importación = **plantilla de la otra plataforma tal cual** (cero fricción; convive con el sistema actual).
2. **Lote = entidad** (datos propios, vista de lotes, % OK por lote para F4). Todos los teléfonos entran por lote.
3. Duplicados al importar: **finales re-entran / activos conflicto** (re-compra real vs error).
4. Estados de la otra plataforma **no se replican** (solo usan 3 y uno es workaround); nuestro ciclo es el §2.1.
5. **Ubicación derivada, no mantenida**; override manual excepcional con motivo.
6. Sub-ubicaciones = **conjunto** (teléfono divisible pantalla/cuerpo); orden pulido→glass→normal solo como etiqueta.
7. **El grado es el del chasis**; PANT = previsión P/G (dispara reparación estética).
8. **Lo funcional decide el OK**; el OK siempre lo da una persona.
9. Revisión estética y funcional **independientes** (se guardan por separado).
10. **Bloqueo operador**: se detecta en revisión; caja/estado nuevo BLOQUEO.
11. Batería **< 85 → reparación obligatoria**.
12. "Es pedido" = **cliente asignado**; entidad Pedido pospuesta con punto de enchufe único.
13. **Devolución** (nombre) para la vuelta post-envío; re-entra por revisión.
14. Desguace **solo registro** (sin alta de piezas ni conteo).
15. Históricos **fuera del ciclo** (opción A); sin migrar OKs antiguos.
16. Parser en **cliente** (POI), servidor agnóstico con alta en bloque.
17. Escaneo explícito **a revisar** (opción B): diferencia cola de revisión de almacén.

## 7. Futuro explícitamente pospuesto

- **Entidad Pedido** (agrupar teléfonos, estados, envíos, anulación auditada).
- **Sugerencia automática de reparación prevista** por grado/funcional (C/B→chasis; pantalla→P/G/LCD; color↔chasis).
- Integración del **export del programa de diagnóstico** (hoy transcripción manual).
- Alertas de **caducidad por caja** y notificaciones (desguace/devolución) — encajan con F4.
- Permisos finos por caja → **Fase 3 (rol LOGISTICA)**.
- Automatismos extra de ubicación más allá de la derivación (ya casi todo es derivado).

## 8. Pendiente de decidir en los planes

- Nombres/tipos SQL definitivos y actualización del MER (`Tabla BBDD(Corregido).drawio`) al cerrar F2a.
- Detalle de UI de la vista previa del importador y de la ficha (bocetos al empezar F2b).
- Cómo se muestran exactamente los históricos en la vista (badge "histórico" u similar).
