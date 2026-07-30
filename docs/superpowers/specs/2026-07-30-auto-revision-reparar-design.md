# Auto-revisión al reparar — Diseño (mini-fase)

**Fecha:** 2026-07-30 · **Estado:** aprobada (brainstorm con decisiones de usuario)
**Contexto:** tras F2c. Entra en el paquete del tag v0.17.0 (antes del super smoke).

## 1. Objetivo

Cuando una reparación consume un componente que "pisa" un campo de la revisión, ese campo
se actualiza solo en la pasada vigente. El re-check post-reparación (la segunda revisión,
la que da el OK) deja de exigir reabrir y reescribir la funcional/estética a mano para
datos que el propio trabajo ya determina:

- **Batería nueva** (SKU prefijo `bat`) → `FUN_BATERIA_PCT = 100`.
- **Chasis nuevo** (SKU prefijo `cha`) → `EST_GRADO = 'A'`.

Ambos **silenciosos** (decisión de usuario: el cambio de chasis es incluso más objetivo que
el de batería — chasis puesto ⇒ grado A). La red de seguridad es el flujo existente: la
revisión post-reparación ve la ficha (con marca "(auto)") antes de marcar OK.

## 2. Disparo

En los **2 puntos con filas** del servidor — los mismos donde F2c añade `COMPONENTES` al
log de actividad:

- `POST /api/reparaciones/completa` (cierre completo con filas)
- `POST /api/reparaciones/{idAsignacion}/filas` (guardar fila individual)

Tras persistir las filas, se derivan los tipos de componente por `idCom`
(`ComponenteDAO#getTipoById`, la misma fuente que el log — el cliente no manda nada nuevo)
y se aplica la regla por prefijo (`bat` / `cha`, convención real de `Componente.TIPO`).

Glass y pulido no llevan estos SKUs por sus flujos propios; el plan lo verifica y NO añade
hooks ahí salvo que el recon demuestre lo contrario.

## 3. Regla de escritura (el corazón del diseño)

Sobre la revisión **vigente** (`RevisionDAO#getVigente`):

1. **Solo valores, nunca autoría ni fechas de parte.** `EST_ID_USU`, `FUN_ID_USU`,
   `EST_FECHA`, `FUN_FECHA` quedan intactos. Un teléfono con la funcional sin guardar
   sigue "pendiente de funcional" aunque su batería marque 100: los estados derivados
   (REVISADO/REPARADO) y el gating del OK siguen exigiendo la revisión real.
2. **Idempotente:** solo se escribe si el valor cambia (batería ya = 100 o grado ya = 'A'
   → no-op, sin marcador duplicado).
3. **Sin pasada vigente → no se escribe nada** (teléfonos pre-F2b nunca revisados;
   decisión de usuario: saltar en silencio, v1 no crea pasadas).
4. **Autor del cambio:** el `idUsu` del técnico que completa/guarda la fila — queda en el
   log de actividad (no en la pasada, ver punto 1).

## 4. Marca "(auto)" y trazabilidad

- Al escribir batería: anexar a `FUN_OBSERVACION` → `"Batería 100% (auto: cambio de batería)"`.
- Al escribir grado: anexar a `FUN_OBSERVACION` → `"Grado A (auto: chasis nuevo)"`.
  (`FUN_OBSERVACION` es el único texto libre de la pasada; el anexo respeta el límite
  VARCHAR(500) truncando con elipsis si hiciera falta — caso patológico.)
- El detalle del log de completar/guardar-fila gana sufijo `", AUTO: batería 100"` /
  `", AUTO: grado A"` (además del `", COMPONENTES: …"` de F2c).

## 5. Interacciones conocidas (aceptadas)

- **Veto OK batería <85**: queda satisfecho automáticamente tras cambio de batería — es el
  objetivo de la fase.
- **Mid-air en la ficha**: si un super tiene la ficha abierta mientras se completa la
  reparación, su guardado dará el 409 de dato-modificado (mecanismo F2b) y recargará.
  Correcto: el dato cambió de verdad. `UPDATED_AT` de la pasada se mueve solo (ON UPDATE).
- **Batería nueva defectuosa / grado A con otros daños**: el automatismo escribe el caso
  típico; el atípico lo corrige la revisión post-reparación editando la ficha (la marca
  "(auto)" le dice de dónde salió el valor). Asumido por diseño.

## 6. Arquitectura

- **Servidor solamente.** Cero cambios de cliente (la ficha ya muestra batería, grado y
  observación), cero migraciones.
- `RevisionDAO#aplicarAutoPorComponentes(String imei, List<String> tipos, int idUsu)`:
  método nuevo, TDD (Mockito a mano, patrón de la casa). Internamente: `getVigente` →
  reglas de §3 → UPDATEs dirigidos por campo.
- `ReparacionController`: en `/completa` y `/{id}/filas`, tras el guardado y reutilizando
  los tipos ya derivados para el log, llamada al método nuevo + sufijo de log.

## 7. Fuera de alcance (v1)

- Pantalla/cámara/altavoces → se estudian con datos de uso (candidatos: SKU `g`/`lcd` →
  resetear checks de pantalla).
- Crear pasadas de revisión automáticas.
- Cambios de UI.
- Consistencia color chasis↔teléfono (sigue en su backlog propio, mini-fase atributos).
