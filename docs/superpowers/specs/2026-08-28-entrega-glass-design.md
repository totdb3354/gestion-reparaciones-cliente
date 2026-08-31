# Entrega del teléfono al técnico de glass ("Entregado" / "Llegó")

Fecha: 2026-08-28
Estado: **APROBADA por el usuario (2026-08-28, brainstorm en sesión)** — primer cambio de la hotfix `0.16.1` (la tienda usa la 0.16.0; `main` sigue en construcción hacia la 0.17.0).
Ramas: cliente `feature/entrega-glass` desde `hotfix/0.16.1`; servidor `feature/entrega-glass` desde `main` del servidor (= lo desplegado en preprod).

---

## 1. Contexto y motivación

El taller tiene dos plantas. Las reparaciones normales se asignan casi siempre arriba; el departamento de glass está abajo. Cuando un IMEI tiene asignación de glass, prácticamente siempre tiene también una reparación normal (al revés no: muchas normales no llevan glass). El flujo real es:

1. El técnico de arriba hace su **primera intervención** y **entrega** la mitad del teléfono abajo.
2. Espera a que glass termine. Si le toca a él ensamblar, marca **"Por cerrar"** (carga reducida; marca ya existente e independiente de esta).
3. Glass devuelve → cierra. (Cuando lo cierra el de glass suele ser porque el de arriba ya completó su asignación.)

Hoy el paso 1 no queda en ninguna parte. Consecuencia: el técnico de glass tiene la asignación desde hace mucho pero el teléfono le llega más tarde, y ese hueco parece tiempo extra de glass. Se quiere **sellar el momento de la entrega** (quién y cuándo) y que **ambos extremos lo vean**.

## 2. Comportamiento

- **Quién entrega**: solo el **dueño** de la asignación de reparación normal (`A…`), técnico o supertécnico, cada uno las suyas. ADMIN nunca. Misma regla de propiedad que "Por cerrar".
- **A quién**: se deduce solo — el teléfono se entrega **a la asignación de glass abierta (`AG…`) del mismo IMEI**, no a una persona. Si el IMEI no tiene glass abierta, **no hay acción** (ni sale). Si hubiera varias glass abiertas (nunca ha pasado), se entrega a todas; sin UI específica.
- **Dónde**: menú contextual de **Mis pendientes**, pestaña Reparación, debajo de "Marcar por cerrar":
  - sin entrega → **"Entregar a Jhona"** (nombre del dueño actual de la glass)
  - con entrega → **"Deshacer entrega"**
  - Volver a entregar tras deshacer sobrescribe la hora.
- **Qué ve cada uno** (badge en la pila de la columna Estado, entre "Por cerrar" y el badge de solicitud):

  | Quién | Fila | Badge | Tooltip |
  |---|---|---|---|
  | Técnico de arriba | su `A…` (Mis pendientes, Reparación) | **"→ Jhona"** (sin hora: la columna Estado mide 100 px y "Entregado dd/MM hh:mm" se cortaba — ajuste smoke 2026-08-28) | "Entregado a Jhona por Manu, 28/08 10:42" |
  | Técnico de glass | su `AG…` (Mis pendientes, Glass) | **"Llegó 10:42"** | "Bajado por Manu, 28/08 10:42" |
  | Supertécnico / admin | ambas filas en **Asignaciones** | los mismos badges | los mismos tooltips |

  - Si la entrega **no es de hoy**, el badge "Llegó" muestra solo la fecha: "Llegó 27/08" (ya no es cuestión de minutos, y "Llegó dd/MM hh:mm" no cabía en los 100 px); la hora exacta está en el tooltip. El de arriba no lleva hora.
  - Formato hora `HH:mm`, fecha `dd/MM` (zona Europe/Madrid, como el resto de fechas del cliente).
  - Palabra "Llegó" (no "Recibido"): "Recibido" ya es el badge de *pieza de solicitud recibida* en esa misma columna.
  - Paleta propia suave, índigo (`#E8EAF6` fondo / `#3949AB` texto), misma pastilla y tamaño que Urgente/Por cerrar. Ajustable en el smoke.
- **Sin teléfono no hay glass** (smoke 2026-08-28): en Mis pendientes → Glass, "Añadir glass" se oculta mientras haya una reparación normal abierta en el IMEI y la glass no tenga entrega. Sin normal abierta (glass directa o normal completada sin marcar) no se oculta: nadie queda bloqueado. Campos derivados en filas AG: `normalAbierta`, `normalTecnicoNombre`.
- **"Marcar que llegó"** (2026-08-31): en la fila de glass bloqueada, el propio técnico registra la llegada (menú contextual); queda firmado (`ENTREGADO_POR` = él, log `ENTREGAR_GLASS` con detalle de llegada propia). Cubre a los técnicos de abajo, que no dependen de que nadie "baje" nada.
- **Quien marca, desmarca** (2026-08-31): "Deshacer entrega"/"Deshacer llegada" solo para quien firmó (`ENTREGADO_POR`); el servidor lo valida (403). Si el firmante no está, re-entregar sobrescribe hora y firma.
- **Píldora "Glass: <técnico>"** (smoke 2026-08-31): bajo el IMEI de la reparación normal mientras la glass del IMEI no tenga entrega registrada (paleta del tipo Glass; texto neutro — el teléfono puede estar arriba o ya abajo, abierto y repartido allí). Al entregar desaparece (la "→ …" de Estado toma el relevo).
- **Bidireccional — píldora "Rep: <técnico>"** (smoke 2026-08-31): en las filas de glass, píldora azul (paleta del tipo Reparación) con el dueño de la reparación normal abierta más antigua del IMEI. Se mantiene tras el "Llegó" (dice a quién devolver el teléfono para ensamblar) y desaparece sola al cerrarse la normal.
- **Contador "N asignados"** (Asignaciones): con **2 asignados** y una píldora que ya cuenta al segundo (verde, índigo o azul) se oculta; con **3+** vuelve y convive con la píldora (la píldora dice el nombre relevante, el contador avisa de que hay más gente).
- **Dos datos de naturaleza distinta**:
  - **"Entregado por"** es un **hecho**: se graba (`ENTREGADO_POR`) y no cambia aunque reasignen la reparación normal.
  - **"A quién / quién lo tiene"** es **estado actual**: no se guarda; se lee del dueño de la glass en cada momento.
- **Reasignaciones y ciclo de vida** (la entrega vive en la fila `AG`; completar no borra filas, cierra con `FECHA_FIN`; reasignar cambia `ID_TEC` de la misma fila):

  | Situación | Efecto |
  |---|---|
  | Reasignan la glass (Jhona → Javi) | Se conserva. Javi ve "Llegó 10:42"; arriba el tooltip pasa a "a Javi". |
  | Reasignan la reparación normal | Se conserva (el teléfono ya está abajo). El nuevo dueño ve "→ Jhona" (tooltip "…por Manu, 28/08 10:42") y puede deshacer. |
  | Borran la glass | Desaparece con ella; arriba no queda acción ni badge. |
  | Incidencia y reasignar (nueva `AG`, cierra la vieja) | La nueva nace **sin entrega**; si el teléfono sigue abajo, se vuelve a "Entregar" con un clic. Con dos AG abiertas donde solo la más antigua está entregada, arriba se ve "→ <glass>" (derivado de la más antigua) y la nueva no; para sellar la nueva: Deshacer → Entregar (caso jamás visto, aceptado). |
  | Completan la glass | La fila `G` nueva **hereda** `ENTREGADO_AT`/`ENTREGADO_POR` de la `AG` (y la `AG` cerrada los conserva). En Agrupado por IMEI e Historial, bajo el reparador: sub-etiqueta "Llegó dd/MM hh:mm" (las "Fechas" de una `G` son las de completar, no las de asignar). Futuro F4: tiempo real de glass = `FECHA_FIN − ENTREGADO_AT` en la propia `G`. |
  | Completan la normal antes que la glass | Sin efecto; la glass sigue con su "Llegó". |

- **Log de actividad**: `ENTREGAR_GLASS` / `DESHACER_ENTREGA_GLASS`, detalle `ID_REP: A…, IMEI: …, GLASS: AG…, TECNICO_GLASS: Jhona`. Ambas acciones en el filtro de la vista Log.
- **No cambia**: carga de técnicos (`CargaTecnicos`), orden de las listas, contadores, filtros, modal de asignar, "Por cerrar".

## 3. Base de datos

Migración `gestion-reparaciones-servidor/sql/migracion-entrega-glass.sql` (vista previa → ALTER → comprobación). **APLICADA en preprod el 2026-08-28** (vista previa 0 → ALTER OK, 13.564 filas → comprobación 2).

```sql
ALTER TABLE Reparacion
    ADD COLUMN ENTREGADO_AT  DATETIME NULL AFTER POR_CERRAR,
    ADD COLUMN ENTREGADO_POR INT      NULL AFTER ENTREGADO_AT,
    ADD CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico (ID_TEC);
```

- Solo se rellenan en filas `AG`. Nulas por defecto: cero impacto en lo existente y en el servidor actual (las ignora).
- `crear_bd.sql` en sync (columnas + FK, mismo orden).
- **MER** `Apuntes/Tabla BBDD(Corregido).drawio`: dos líneas al final de la tabla `Reparacion`: `ENTREGADO_AT: DATETIME (nullable, solo filas AG)` y `ENTREGADO_POR: INT (FK Tecnico, nullable)`.

## 4. Servidor

**Endpoint** (calcado de por-cerrar): `PATCH /api/reparaciones/asignaciones/{idRep}/entrega-glass`, body `{ "entregado": true | false }`, 204 sin cuerpo. `idRep` es la **reparación normal** del que entrega. Validaciones en orden:

1. `idRep` no es `A…` normal (`AG`/`AP`) → **422** "Solo aplica a asignaciones de reparación".
2. No existe o ya tiene `FECHA_FIN` → **404**.
3. `principal.idTec` nulo o ≠ `ID_TEC` de la asignación → **403** "Solo puedes entregar tus propias asignaciones".
4. Ninguna `AG` abierta (`ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL`) en ese IMEI → **422** "Sin glass abierta para este IMEI" (también al deshacer: no hay nada que deshacer).

Efecto: `UPDATE Reparacion SET ENTREGADO_AT = NOW(), ENTREGADO_POR = ?, UPDATED_AT = UPDATED_AT WHERE …` sobre **todas** las `AG` abiertas del IMEI (true) o ambas columnas a `NULL` (false). `UPDATED_AT = UPDATED_AT` para no disparar el lock optimista ajeno (paridad con por-cerrar). Sin lock optimista propio: toggle, último gana. Log según §2.

**Válvula "Marcar que llegó"** (2026-08-31): `PATCH /api/reparaciones/asignaciones/{idRep}/llegada`, `idRep` es la propia `AG…`, sin body, 204. Validaciones: no es `AG` → 422 "Solo aplica a asignaciones de glass"; no existe/cerrada → 404; no es el dueño → 403 "Solo puedes registrar la llegada de tus propias asignaciones"; ya entregada → 422 "La entrega ya está registrada". Efecto: `entregarGlass(imei, principal.idTec)` (sella todas las `AG` abiertas del IMEI, firmadas por el propio técnico). Log `ENTREGAR_GLASS`, detalle `ID_REP: AG…, IMEI: …, LLEGADA registrada por el tecnico de glass`.

**Quien marca, desmarca** (2026-08-31): al deshacer la entrega (`entrega-glass` con `entregado:false`) se valida además, en orden, que exista entrega (`glassEntregadoAt` nulo → 422 "No hay entrega que deshacer") y que la firma sea propia (`principal.idTec != glassEntregadoPor` → 403 "Solo quien registró la entrega puede deshacerla"); nuevo `DELETE /api/reparaciones/asignaciones/{idRep}/llegada` (`idRep` la propia `AG…`, sin body, 204) con las mismas dos validaciones de firma sobre `entregadoAt`/`entregadoPor` tras validar tipo/existencia/dueño (422/404/403 calcados de "Marcar que llegó"), efecto `deshacerEntregaGlass(imei)` y log `DESHACER_ENTREGA_GLASS`, detalle `ID_REP: AG…, IMEI: …, LLEGADA deshecha por el tecnico de glass`.

**Lectura**: las queries de asignaciones abiertas (`getAsignaciones`, `getAsignacionesPorImei`, `getAsignacionesGlass`, `getAsignacionesGlassPorImei`) devuelven campos **aditivos** en `ReparacionResumen`:

- en filas `AG`: `entregadoAt` (`LocalDateTime`), `entregadoPorNombre` (LEFT JOIN `Tecnico` por `ENTREGADO_POR`).
- en filas `A`, derivados por subconsulta sobre las `AG` abiertas del mismo IMEI (si hay varias, la de `FECHA_ASIG` más antigua): `glassAbierta` (bool), `glassEntregadoAt`, `glassEntregadoPorNombre`, `glassTecnicoNombre` (dueño **actual** de la `AG`).
- Las queries con `GROUP BY` incorporan las columnas nuevas al agrupado. `getAsignacionesPulido` y las de historial no cambian.

**Compatibilidad**: servidor nuevo + cliente 0.16.0 → campos ignorados (Gson), sin cambios. Cliente 0.16.1 + servidor viejo → campos nulos → `glassAbierta=false` → la acción no aparece, sin badges. Degradación amable en ambos sentidos.

## 5. Cliente

- `ReparacionResumen`: campos de §4 (Gson los rellena; nulos si no vienen).
- `ReparacionDAO.actualizarEntregaGlass(String idRep, boolean entregado)` → el PATCH; errores como `SQLException` con el mensaje del servidor (patrón existente).
- **Clase nueva `utils/EntregaGlass`** (pura, con JUnit): concentra toda la lógica para que los controladores solo enganchen y el futuro merge `hotfix → main` (donde esos controladores han cambiado) tenga conflictos mínimos:
  - `textoBadge(rep, LocalDate hoy)` → fila `A`: `"→ <técnico de glass>"`; fila `AG`: `"Llegó 10:42"` / `"Llegó 27/08"`; `null` si no hay entrega o es pulido.
  - `tooltipArriba(rep)` → "Entregado a Jhona por Manu, 28/08 10:42"; `tooltipGlass(rep)` → "Bajado por Manu, 28/08 10:42".
  - `opcionMenu(rep, esPestanaGlass)` → `null` (oculta) / `"Entregar a Jhona"` / `"Deshacer entrega"`. Oculta si pestaña Glass, si no es `A…` normal o si `!glassAbierta`.
  - Colores del badge como constantes.
- `PendientesTecnicoController`: `MenuItem` nuevo bajo "Marcar por cerrar" (visibilidad y texto por `EntregaGlass.opcionMenu`; al pulsar: DAO + `cargar()`; error → aviso no modal con el mensaje); `Label badgeEntrega` en la pila de Estado, entre `badgePorCerrar` y `badge`, con texto/tooltip por `EntregaGlass` según pestaña.
- `PendientesSuperTecnicoController` (Asignaciones): el mismo badge en la columna Estado, sin acción. **CSV espejo**: columna nueva "Entregado" (`dd/MM/yyyy HH:mm` o vacío) para filas `A` (fecha de `glassEntregadoAt`) y `AG` (`entregadoAt`).
- `LogController`: `ENTREGAR_GLASS`, `DESHACER_ENTREGA_GLASS` en la lista de acciones del filtro.

## 6. Decisiones (con su porqué)

1. **Marca aparte de "Por cerrar"**: son momentos distintos (entregar ocurre siempre; por cerrar solo cuando el de arriba ensamblará). Se combinan libremente.
2. **La entrega vive en la fila `AG`**, no en la `A` ni en la persona: sobrevive a reasignaciones de ambos lados y queda en el historial de glass para estadísticas.
3. **Destinatario deducido**, sin selector: el glass siempre se asigna antes; si no hay glass no hay entrega.
4. **"Llegó" y no "Recibido"** abajo: evita chocar con el badge "Recibido" de pieza en la misma columna.
5. **Columnas, no tabla nueva**: es una hora y un quién; un historial de entregas/devoluciones sería sobredimensionar (si algún día se registra la devolución, se amplía entonces).
6. **Sin lock optimista** en el toggle: paridad con urgente/chasis/por-cerrar.
7. **Lógica en clase nueva** en el cliente: aísla el cambio y minimiza conflictos en el merge posterior a `main`.
8. **Varias glass abiertas** en un IMEI: se sella en todas, sin UI; caso jamás visto.

## 7. Fuera de alcance

Registrar la devolución (glass → arriba). Selector manual de destinatario. Notificación/aviso al técnico de glass (lo ve en su siguiente recarga). Estadísticas de tiempo de glass (F4; el dato ya queda). Cambios en carga, orden, contadores o modal de asignar.

## 8. Ramas y despliegue

- **Servidor**: submódulo a `main` (`6b30768`, lo desplegado) → `feature/entrega-glass` → review → merge `--no-ff` a `main` + push (con OK del usuario) → VM: `git pull` + `docker compose up -d --build` (lo ejecuta el usuario) → **validar arranque** (no hay test de contexto Spring). Después, `feature/auto-revision` (WIP 0.17) se pone al día con merge de `main`.
- **Cliente**: `feature/entrega-glass` desde `hotfix/0.16.1` → review → merge `--no-ff` a `hotfix/0.16.1`.
- **Release 0.16.1** (commit aparte, ficheros por nombre, nunca `-A`): bump en `pom.xml`, `LoginView.fxml`, `MainView.fxml`, `build-installers.yml`; `CHANGELOG.md`; `NOVEDADES-v0.16.1.md`; **gitlink del servidor** al `main` del servidor con esta feature.
- **Orden**: ALTER ✅ → servidor en VM → smoke desde el IDE contra preprod → tag `v0.16.1` → instaladores (artefactos de Actions) → merge `hotfix/0.16.1 → main` (`--no-ff`, inmediato).

## 9. Verificación

- **Servidor** (JUnit sobre BD de test, patrón de la suite actual): entregar sella todas las `AG` abiertas del IMEI y ninguna cerrada ni de otro IMEI; deshacer las limpia; derivados de la fila `A` con entrega / sin entrega / sin glass; validaciones 422/404/403 si el patrón de test de controlador existe (si no, quedan en smoke). Suite completa en verde antes del merge.
- **Cliente**: JUnit de `EntregaGlass` (textos hoy/otro día/nulo, tooltips, visibilidad y texto de la opción). Suite en verde.
- **Smoke en preprod** (checklist):
  1. IMEI con normal + glass: entregar → "→ <glass>" arriba (tooltip con hora), "Llegó hh:mm" en la pestaña Glass del otro técnico, ambos en Asignaciones.
  2. Reasignar la glass → el tooltip de arriba cambia de técnico; el nuevo glass ve "Llegó".
  3. Reasignar la normal → "por Manu" no cambia; el nuevo dueño puede deshacer.
  4. Deshacer → desaparecen ambos badges; volver a entregar → hora nueva.
  5. Borrar la glass → arriba desaparecen acción y badge.
  6. IMEI solo con normal → la opción no aparece.
  7. Entrega de ayer (ajustar `ENTREGADO_AT` a mano en BD) → "Llegó" con fecha; arriba el tooltip con la fecha.
  8. Log: las dos acciones con su detalle; filtro funciona.
  9. CSV de Asignaciones con la columna "Entregado".
  10. Cliente 0.16.0 contra el servidor nuevo: todo igual que antes.
  11. Cambiar contraseña con la actual incorrecta → el diálogo dice "Contraseña actual incorrecta." (con el servidor nuevo y, por el fallback, con uno viejo).
  12. Completar la glass entregada → en Agrupado por IMEI e Historial la fila G muestra "Llegó dd/MM hh:mm" bajo el reparador (tooltip "Bajado por …").
  13. Glass con normal abierta y sin entrega → sin botón "Añadir glass"; tras entregar → aparece; glass sin normal abierta → aparece siempre.
  14. Fila normal con glass sin entrega → píldora verde "Glass: <técnico>" bajo el IMEI (Mis pendientes y Asignaciones; el "2 asignados" no aparece); al entregar → desaparece y queda "→ <técnico>"; con 3 asignados el contador vuelve.
  15. Fila glass con normal abierta → píldora azul "Rep: <técnico>" bajo el IMEI (también tras el "Llegó"); con 3+ asignados, píldora y contador "N asignados" conviven.
  16. Fila glass bloqueada → "Marcar que llegó" en su menú; al usarla el botón "Añadir glass" aparece, arriba se ve "→ <glass>" y el log registra la llegada propia; en una fila ya entregada o sin normal abierta la opción no sale.
  17. Con la llegada auto-marcada, el de rep NO ve "Deshacer entrega" y el de glass sí ve "Deshacer llegada" (y viceversa con la entrega normal); deshacer con firma ajena vía API → 403.
