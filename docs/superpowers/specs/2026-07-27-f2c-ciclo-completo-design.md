# F2c — Ciclo completo: envíos, devoluciones y trazabilidad (diseño)

**Fecha:** 2026-07-27 · **Estado:** aprobada en brainstorm (pendiente review usuario)
**Spec canónica:** `2026-07-07-fase2-lotes-telefonos-design.md` (§2 ciclo, §95 alcance F2c)
**Contexto previo:** F2b cerrada y en main (revisión + veredicto + panel); entradas
acumuladas del smoke F2b en `Apuntes/plan-futuro.md` §F2c.

## 1. Objetivo

Cerrar el ciclo de vida del teléfono: salida del circuito (**enviar**, individual o
en remesa), la vuelta (**devolución** → almacén), **trazabilidad completa** de
transiciones visible por teléfono, sub-información visual (**tags bajo la píldora**
de estado), retirada del check antiguo `REVISION_LOGISTICA`, y cuatro flecos de
F2b (veto OK con bloqueo-operador, UX del bloqueo, log con componentes, combos de
filtro con los estados nuevos).

## 2. Alcance y fuera de alcance

**Entra:** entidad `Envio` + puente `Envio_Telefono`; acción enviar (2 vías: escáner
masivo + multiselección acotada en el maestro); registro de devolución (masivo, con
motivo por IMEI y envío de origen autodetectado); movimientos escritos en TODAS las
transiciones de estado + historial en la ficha; minitags bajo la píldora (devolución
y tipos de trabajo abierto); retirar `REVISION_LOGISTICA` (UI, lógica, endpoint
tolerante, columna BD en script post-deploy); veto OK con `FUN_BLOQUEO_OP` marcado
(cliente + servidor); aviso junto al check de operador + tooltip del botón Bloquear;
botón "Abrir ficha" en el panel Revisión; log `COMPLETAR_*` con componentes
consumidos; combos de filtro Estado con Revisado/Reparado/Enviado/Desguace.

**Fuera (decidido):** vista de lotes propia y vista de envíos (→ F4, con "calidad
por lote": % devoluciones/desguace por lote — necesita justo los datos que esta fase
empieza a generar); acción compuesta "Enviar a externo…" (backlog: el Bloquear
manual con motivo cubre el caso; revisar con datos del log); ticket hardening F2a
(mini-ticket propio entre fases); precio de venta (facturación = sistemas externos);
alertas de caducidad por caja (F4); sub-ubicaciones derivadas en el derivador (las
sustituyen los tags de UI — decisión de brainstorm).

## 3. Ciclo: enviar y devolución (decisiones de producto)

1. **Enviar** cubre venta individual (cliente de BD) y remesa a mayorista/plataforma
   (texto libre) — un envío lleva `FECHA + destino (ID_CLI o DESTINO_TEXTO, al menos
   uno) + REFERENCIA opcional` (albarán/tracking/pedido externo). Sin precios.
2. Solo se envían teléfonos en estado `OK`. Resultado por IMEI en el diálogo
   (`ENVIADO` / `NO_OK` con su estado / `NO_EXISTE` / `HISTORICO`). El `Envio` solo
   se crea si al menos un IMEI entra (lección del lote-vacío de F2a).
3. Enviar limpia `ES_DEVOLUCION` (el minitag dura el ciclo que nació de la vuelta).
4. **Devolución → ALMACÉN, no a revisión** (cambio consciente sobre la spec
   canónica, que la mandaba a EN_REVISION): registrar devolución (motivo por IMEI,
   envío de origen autodetectado por la fila puente activa) deja el teléfono en
   `ESTADO='RECIBIDO'` con `ES_DEVOLUCION=TRUE`, y entra a revisión MÁS TARDE por el
   masivo "A revisar" normal, como cualquier teléfono del almacén. Calca el flujo
   físico: el paquete llega al almacén, no a la mesa del revisor.
5. Un teléfono puede salir, volver y re-enviarse: cada estancia en una remesa es una
   fila de `Envio_Telefono`; la devolución marca la fila activa (`DEVUELTO=TRUE`,
   motivo, fecha, usuario). "¿De qué envío volvió?" es un dato, no un texto.
6. NO se llama "incidencia" (ese término ya significa reapertura de taller).

## 4. Datos y migraciones (las aplica el usuario, con vista previa)

**Script 1 — ANTES de desplegar el servidor** (`migracion-f2c-envios.sql`, solo CREATEs):

```sql
CREATE TABLE Envio (
    ID_ENVIO      INT          NOT NULL AUTO_INCREMENT,
    FECHA         DATETIME     NOT NULL,
    ID_CLI        INT          NULL,
    DESTINO_TEXTO VARCHAR(150) NULL,
    REFERENCIA    VARCHAR(100) NULL,
    ID_USU        INT          NOT NULL,
    UPDATED_AT    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ENVIO),
    CONSTRAINT fk_envio_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI),
    CONSTRAINT fk_envio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU)
);

CREATE TABLE Envio_Telefono (
    ID_ET               INT          NOT NULL AUTO_INCREMENT,
    ID_ENVIO            INT          NOT NULL,
    IMEI                VARCHAR(15)  NOT NULL,
    DEVUELTO            BOOLEAN      NOT NULL DEFAULT FALSE,
    MOTIVO_DEVOLUCION   VARCHAR(255) NULL,
    FECHA_DEVOLUCION    DATETIME     NULL,
    ID_USU_DEVOLUCION   INT          NULL,
    UPDATED_AT          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ET),
    KEY idx_et_imei (IMEI, ID_ET),
    CONSTRAINT fk_et_envio    FOREIGN KEY (ID_ENVIO)          REFERENCES Envio (ID_ENVIO),
    CONSTRAINT fk_et_telefono FOREIGN KEY (IMEI)              REFERENCES Telefono (IMEI),
    CONSTRAINT fk_et_usu_dev  FOREIGN KEY (ID_USU_DEVOLUCION) REFERENCES Usuario (ID_USU)
);
```

"Al menos un destino" (ID_CLI o DESTINO_TEXTO) se valida en servidor, sin CHECK BD
(estilo de la casa). `Telefono` no cambia en este script.

**Script 2 — DESPUÉS de desplegar servidor y clientes** (`migracion-f2c-drop-check.sql`):
`ALTER TABLE Telefono DROP COLUMN REVISION_LOGISTICA;` — separado porque el servidor
viejo aún escribe la columna (hook al asignar): primero se despliega el servidor que
deja de usarla, luego se dropea. `crear_bd.sql` se sincroniza con ambos.

## 5. Servidor

**Movimientos (el corazón).** Helper único
`registrarMovimiento(imei, origen, destino, idUsu, motivo, referencia)` sobre
`Movimiento_telefono` (tabla F2a, hoy solo escrita al importar). Escriben movimiento
las **transiciones de ESTADO** + enviar/devolución:

El **origen se deriva siempre del estado previo real** (a-revisar acepta RECIBIDO
y OK: origen ALMACEN o LISTOS según el caso); la tabla muestra el caso típico:

| Transición | origen → destino | motivo/referencia |
|---|---|---|
| a-revisar (masivo) | (ALMACEN\|LISTOS) → PARA_REVISAR | — |
| Marcar OK | PARA_REVISAR → LISTOS | — |
| Bloquear (manual) | PARA_REVISAR → BLOQUEO | motivo del usuario |
| Bloqueo automático (operador) | PARA_REVISAR → BLOQUEO | "Bloqueo de operador…" |
| Desbloquear | BLOQUEO → PARA_REVISAR | — |
| Desguace | (PARA_REVISAR\|BLOQUEO) → DESGUACE | motivo |
| Quitar-OK (hook al asignar) | LISTOS → PARA_REVISAR | "Trabajo asignado" |
| Enviar | LISTOS → ENVIADO | ref `ENVIO <id>` |
| Devolución | ENVIADO → ALMACEN | motivo + ref `ENVIO <id>` |

**Decisión explícita:** abrir/cerrar trabajos NO escribe movimientos — ese ir-y-venir
ya lo cuentan los trabajos con sus fechas; la línea de vida traza el ciclo de cajas
de estado, sin ruido por asignación. (Cubre de rebote la nota del smoke: los `G…`
nacidos-cerrados se ven por el historial del teléfono, no hace falta logearlos.)

**Endpoints nuevos** (mutaciones con `@PreAuthorize("hasRole('SUPERTECNICO')")`):
- `POST /api/envios` body `{idCli, destinoTexto, referencia, imeis:[…]}` →
  `[{imei, resultado}]`. Valida destino; por IMEI: solo `OK` entra (transición
  atómica `WHERE ESTADO='OK'`), crea puente, limpia `ES_DEVOLUCION`, movimiento;
  log `ENVIAR_TELEFONOS` (detalle: envío, destino, nº IMEIs).
- `POST /api/telefonos/devoluciones` body `{items:[{imei, motivo}]}` →
  `[{imei, resultado}]` (`DEVUELTO` / `NO_ENVIADO` / `NO_EXISTE`). Fila puente
  activa → `DEVUELTO+motivo+fecha+usuario`; `ESTADO='RECIBIDO'`,
  `ES_DEVOLUCION=TRUE`; movimiento; log `DEVOLUCION_TELEFONO` (motivo en MOTIVO).
  Caso borde: `ENVIADO` sin fila puente activa (pre-F2c o SQL manual) → se procesa
  igualmente con envío de origen vacío ("—"), no se rechaza.
- `GET /api/telefonos/{imei}/movimientos` → historial (JOIN nombre de usuario),
  orden cronológico.

**Retoques a lo existente:**
- `marcarOk`: veto adicional si la vigente tiene `FUN_BLOQUEO_OP=TRUE` (409 "Bloqueo
  de operador marcado en la revisión").
- Log de `COMPLETAR_GLASS/_REPARACION` (los 3 puntos: `/completa`, `/completar`,
  `guardar-fila`): detalle + `", COMPONENTES: <tipo1>, <tipo2>"`.
- Retirada `REVISION_LOGISTICA`: fuera de `resetRevisionAlAsignar` (queda solo el
  UPDATE OK→EN_REVISION; renombrar si procede), fuera de las queries de
  `ReparacionDAO` (L54/L932, mapper defensivo L141) y del inventario
  (`TelefonoDAO` L138/L186, modelo). El endpoint `PUT /{imei}/revision-logistica`
  queda como **no-op tolerante** (204 sin efecto) durante la ventana con clientes
  v0.16; se elimina en F3.
- Inventario: `ES_DEVOLUCION` ya viaja en el SELECT (L138) — verificar que llega al
  modelo/JSON; si no, añadirlo (lo consume el minitag).

## 6. Cliente

**`EnvioDialog`** (un diálogo, dos vías): cabecera de remesa (selector de cliente
**o** texto libre de destino — al menos uno; referencia opcional), escáner con
pegado masivo (patrón A-revisar), lista de añadidos con quitar-fila; al confirmar,
POST único y resultados por IMEI en la lista. Entradas: botón **"Enviar (masivo)"**
en la barra del inventario (junto a Importar lote / Alta manual) y **"Enviar
seleccionados (N)"** en el menú contextual del maestro. **Multiselección acotada:**
`SelectionMode.MULTIPLE` en el maestro, pero la ÚNICA acción masiva es Enviar (el
resto de items siguen actuando sobre la fila bajo el cursor). La selección precarga
el diálogo; el escáner puede seguir añadiendo.

**`DevolucionDialog`:** botón "Registrar devolución" en la misma barra. Escáner
masivo; motivo común arriba que se copia a cada fila (editable por IMEI); envío de
origen autodetectado visible por fila; resultados por IMEI al confirmar.

**Tags bajo la píldora** (celda Estado del maestro): píldora + fila de mini-chips:
`devolución` (si `ES_DEVOLUCION`) y `rep · glass · pulido` (si En reparación; los
tipos con trabajo abierto — contadores que ya viajan). La fila crece solo cuando hay
tags. CSV: columna nueva "Devolución" (Sí/vacío); los tags de tipo NO van al CSV.

**Historial en la ficha de revisión:** sección plegada bajo el veredicto, lista
cronológica solo-lectura `fecha · de→a · usuario · motivo/ref`, carga perezosa al
desplegar (GET movimientos).

**Flecos UI:** botón "Abrir ficha" en el panel Revisión (habilitado con selección;
el doble clic y el escáner se quedan); aviso rojo junto al check "Bloqueo operador"
al marcarlo ("al guardar → BLOQUEADO"); tooltip del botón Bloquear ("Apartar con
motivo — MS externa, pendiente de devolución…; el bloqueo por operadora va por su
check"); combos de filtro Estado del inventario con Revisado/Reparado/Enviado/
Desguace; retirar del inventario la columna/check de revisión antiguo; `btnOk`
deshabilitado también con `bloqueoOp` marcado (espejo del veto servidor).

## 7. Permisos, despliegue, testing

- **Permisos:** enviar/devolución = SUPERTECNICO (server + gating UI); ADMIN
  consulta; técnicos nada nuevo. LOGISTICA llegará en F3.
- **Orden de despliegue obligatorio:** script 1 (CREATEs) → servidor → clientes →
  script 2 (DROP). Ventanas verificadas: cliente viejo + servidor nuevo OK (campos
  nuevos ignorados; check viejo no-op tolerante, se ve desmarcado — cosmético);
  cliente nuevo + servidor viejo NO se da (orden); servidor viejo + columna dropeada
  NO se da (script 2 al final).
- **Testing (TDD):** servidor — EnvioDAO (solo OK entra, envío no se crea vacío,
  limpia ES_DEVOLUCION, transición atómica), devoluciones (puente activa, motivo,
  RECIBIDO+flag), veto bloqueoOp en marcarOk, movimientos por transición (verify
  SQL literal), log COMPONENTES; cliente — clases puras de textos de resultado de
  ambos diálogos, lógica de tags (qué chips tocan por fila), formateo del historial.
  UI por smoke.

## 8. Checklist de smoke (guión para el cierre)

1. Enviar por escáner: mezcla OK / no-OK / inexistente → resultados por IMEI; envío
   con cliente y envío con texto libre + referencia.
2. Enviar por selección: filtro Estado=OK + Lote, ctrl-click, "Enviar seleccionados
   (N)" → diálogo precargado, añadir uno más por escáner, confirmar.
3. Devolución masiva: 2-3 IMEIs enviados (de envíos distintos), motivo común +
   uno editado → a RECIBIDO con minitag "devolución"; envío de origen correcto por
   fila; re-enviar uno → minitag desaparece.
4. Historial en ficha: línea de vida completa de un teléfono que hizo el ciclo
   entero (importado → revisar → OK → enviado → devuelto → revisar…).
5. Tags de tipo: teléfono con rep+glass abiertos → chips `rep · glass`; cerrar uno
   → chip se va al recargar.
6. Veto bloqueo-operador: funcional con check → OK deshabilitado (cliente) y 409
   con mensaje si se fuerza (servidor).
7. Check viejo: columna fuera del inventario nuevo; cliente v0.16 contra servidor
   nuevo no revienta al pulsarlo (no-op).
8. Log: ENVIAR_TELEFONOS, DEVOLUCION_TELEFONO (con motivo), COMPLETAR_* con
   COMPONENTES; buscador encuentra "mci"/"marco".
9. Filtros: combos con los estados nuevos filtran de verdad.
10. Flecos ficha: aviso del check al marcarlo, tooltip Bloquear, botón Abrir ficha.
11. Script 2 aplicado al final: DROP + arranque posterior sin errores.

## 9. Notas para F4 (apuntadas, no alcance)

Calidad por lote (% devoluciones/desguace/OK por lote, con motivo de devolución
agregable), vista/navegador de envíos y lotes, alertas de caducidad por caja,
métricas de revisión (tiempos por pasada, por usuario).
