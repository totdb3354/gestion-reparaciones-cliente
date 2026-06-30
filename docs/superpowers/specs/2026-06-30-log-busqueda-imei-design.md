# Diseño — IMEI en el detalle de los logs para que el buscador encuentre todos los movimientos

**Fecha:** 2026-06-30
**Estado:** aprobado para plan de implementación

## 1. Motivación

El visor de logs busca por **texto libre** sobre tres campos (usuario, acción, detalle). No hay
campo IMEI estructurado, así que un IMEI solo se encuentra si **aparece escrito en el `detalle`**.
Varias acciones de dispositivo **no** incluyen el IMEI en su detalle (guardan solo el `ID_REP`),
por lo que al buscar por IMEI esos movimientos **no aparecen** — p. ej. "actualizar asignación".
Se quiere que, **hacia delante**, todas las acciones de un solo dispositivo lleven el IMEI en el
detalle, para poder usar el buscador como filtro de IMEI de facto.

## 2. Punto de partida (lo que hay)

- **Cliente** (`LogController`): el buscador hace `contiene(usuario) || contiene(accion) ||
  contiene(detalle)` sobre texto en minúsculas. **No cambia** en esta feature.
- **Servidor**: cada acción escribe su log con `logDao.insertar(idUsu, "ACCION", detalle)`. El
  `detalle` se construye a mano por acción.
- **Inventario de acciones de dispositivo** (las que tocan un IMEI concreto):

  **Ya incluyen el IMEI** (se encuentran; no se tocan): `CREAR_REPARACION`, `CREAR_ASIGNACION`,
  `COMPLETAR_REPARACION` (×2), `MARCAR_INCIDENCIA`, `GUARDAR_FILA_INDIVIDUAL`,
  `ELIMINAR_ASIGNACION`, `ELIMINAR_REPARACION`, `CREAR_ASIGNACION_PULIDO`, y las de
  `TelefonoController` (`ASIGNAR_CLIENTE`, `EDITAR_OBSERVACION`, `CAMBIAR_CLIENTE`).

  **NO incluyen el IMEI** (objetivo de esta feature):
  | Acción | Controlador | Detalle actual | IMEI |
  |---|---|---|---|
  | `ACTUALIZAR_ASIGNACION` | ReparacionController | `ID_REP, [TEC], [COM]` | `ant.getImei()` ya cargado |
  | `EDITAR_REPARACION` | ReparacionController | `ID_REP, [COM], [OBS]` | `ant.getImei()` ya cargado |
  | `MARCAR_URGENTE` | ReparacionController | `ID_REP, URGENTE` | lookup por idRep |
  | `AGOTAR_COMPONENTE` | ReparacionController | `ID_ASIG, TIPO, CANT` | lookup por idAsignacion |
  | `ACTUALIZAR_ASIGNACION_PULIDO` | PulidoController | `ID_REP` | lookup por idAP |
  | `ELIMINAR_ASIGNACION_PULIDO` | PulidoController | `ID_REP` | lookup por idAP (**antes** del delete) |
  | `ELIMINAR_PULIDO` | PulidoController | `ID_REP` | lookup por idP (**antes** del delete) |

  Reparaciones y pulidos comparten la tabla `Reparacion` (los pulidos son `ID_REP` con prefijo
  `AP%`), así que un único `SELECT IMEI FROM Reparacion WHERE ID_REP = ?` sirve para todos.

## 3. Alcance

**Dentro:**
- `ReparacionDAO` (servidor): un método `getImeiByIdRep(String idRep)`.
- Enriquecer el `detalle` de las **7** acciones de la tabla anterior con `, IMEI: <imei>`.

**Fuera (YAGNI):**
- **Backfill histórico** (decidido: solo hacia delante; los logs ya escritos sin IMEI siguen
  como están).
- **`COMPLETAR_PULIDO_LOTE`** (decidido: fuera, es multi-dispositivo y cada pulido del lote ya
  tiene su `CREAR_ASIGNACION_PULIDO` con el IMEI, así que el dispositivo sigue localizable; solo
  no se vería esa fila concreta de completado en lote).
- **Columna IMEI estructurada + filtro de IMEI dedicado** en el visor (sería más "correcto" pero
  toca BD + servidor + cliente y, al ser forward-only, no aporta sobre el histórico).
- **Cliente**: el buscador no cambia (sigue buscando texto sobre el detalle).
- BD: sin migración.

## 4. Diseño (enfoque A)

### 4.1 DAO

`ReparacionDAO` (servidor) añade:

```java
public String getImeiByIdRep(String idRep) {
    return jdbc.query("SELECT IMEI FROM Reparacion WHERE ID_REP = ?",
            rs -> rs.next() ? rs.getString("IMEI") : null, idRep);
}
```

Devuelve `null` si no existe la fila (no debería pasar en el flujo normal, pero se maneja: ver §4.3).

### 4.2 Enriquecer el detalle por acción

En cada una de las 7 acciones, añadir `, IMEI: <imei>` al detalle, con el **mismo formato**
`IMEI: <valor>` que ya usan las acciones que lo llevan (para que el buscador lo encuentre igual):

- **`ACTUALIZAR_ASIGNACION`**, **`EDITAR_REPARACION`**: ya cargan `ant` antes de mutar → usar
  `ant.getImei()`. Añadir el IMEI al inicio de la lista de cambios (p. ej. tras `"ID_REP: " + idRep`).
- **`MARCAR_URGENTE`**, **`AGOTAR_COMPONENTE`**: la fila sigue existiendo tras la operación →
  `getImeiByIdRep(idRep / idAsignacion)`.
- **`ACTUALIZAR_ASIGNACION_PULIDO`**: la fila sigue existiendo → `getImeiByIdRep(idAP)`.
- **`ELIMINAR_ASIGNACION_PULIDO`**, **`ELIMINAR_PULIDO`**: ⚠️ el `delete` va **antes** del log;
  resolver `getImeiByIdRep(idAP / idP)` **antes** de llamar a `dao.eliminar...` (si no, la fila ya
  no existe y devolvería null).

### 4.3 IMEI nulo (defensivo)

Si `getImeiByIdRep` devolviera `null` (fila inexistente), el detalle **omite** el fragmento del
IMEI en vez de escribir `"IMEI: null"` (evita ruido y falsos positivos de búsqueda). En el flujo
normal siempre habrá IMEI.

## 5. Comportamiento / casos

- Tras el deploy, hacer una "actualizar asignación" sobre un IMEI y buscar ese IMEI en el visor →
  la fila `ACTUALIZAR_ASIGNACION` aparece (antes no).
- Los logs **anteriores** al deploy de esas acciones siguen sin IMEI (no aparecen) — es lo
  esperado (solo hacia delante).
- Las acciones que ya llevaban IMEI no cambian.

## 6. Roles / seguridad

Sin cambios de permisos: solo se enriquece el texto del log de acciones ya existentes y ya
autorizadas (todas son acciones de SUPERTECNICO/flujo de reparación). El log se sigue escribiendo
con el mismo `idUsu`.

## 7. Testing

- **Servidor**: el cambio es texto del log + un `SELECT` simple. **Prueba manual** tras el deploy:
  por cada una de las 7 acciones, ejecutarla y comprobar que el detalle del log incluye `IMEI: …`
  y que el buscador del visor la encuentra al buscar ese IMEI. Atención especial a las dos de
  **borrado** (que el IMEI se resuelve antes de borrar).
- **Arranque del contexto Spring**: aunque solo se añade un método a un DAO existente (sin beans
  nuevos), validar que el servidor arranca tras el cambio (el repo no tiene test de contexto que
  lo cubra).

## 8. Riesgos

- **Orden en los borrados** (§4.2): si el IMEI se resolviera después del `delete`, el log saldría
  sin IMEI. Mitigado resolviéndolo antes.
- **Solo servidor**, sin BD ni cliente → blast radius pequeño; requiere **redeploy de la VM** para
  surtir efecto.
- **Sólo hacia delante**: los movimientos históricos de esas acciones no se vuelven buscables (es
  la decisión tomada; gestionar expectativa).
