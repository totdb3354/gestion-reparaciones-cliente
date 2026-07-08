# Marca "Por cerrar" + % de carga por técnico

Fecha: 2026-07-08
Estado: **APROBADA por el usuario (2026-07-08, brainstorm en sesión)** — cambio urgente intercalado durante la F2a (que queda aparcada en sus ramas y se retoma al cerrar esto).
Rama: `feature/por-cerrar-carga` desde `main` en AMBOS repos (raíz y servidor).

---

## 1. Contexto y motivación

En el taller, una reparación normal puede quedar lista **a falta solo del glass**: otro técnico debe ceder el glass que estaba trabajando para que el primero ensamble y cierre el móvil. Hoy eso no se ve en ninguna parte: una asignación "casi cerrada" pesa visualmente lo mismo que una sin empezar. Se quiere:

1. **Marcar** esas asignaciones ("Por cerrar" — el acto pendiente es solo cerrar el móvil).
2. Un **porcentaje de carga por técnico** que refleje la carga real de trabajo de cliente, donde las "por cerrar" cuentan casi nada y las de chasis el doble.

## 2. Cambio 1 — Marca "Por cerrar"

- **Semántica**: la asignación de reparación normal está mayoritariamente hecha; solo queda cerrar el móvil (habitualmente esperando el glass de otro técnico). Marca manual — solo la persona sabe que "ya hizo la mayoría".
- **Ámbito**: SOLO asignaciones de **reparación normal** (`ID_REP` tipo `A%`, no `AG`/`AP`). Ni glass ni pulido.
- **Quién marca/desmarca**: el **técnico** (solo SUS asignaciones) y el **supertécnico** (cualquiera). Validado en servidor, no solo en UI.
- **Dónde se marca**: menú contextual de la fila, junto a "Marcar urgente"/"Marcar chasis":
  - Vista Asignaciones (supertécnico; admin es solo-lectura y no ve la acción).
  - Vista Mis pendientes (técnico), en su menú contextual existente.
  - Texto: "Marcar por cerrar" / "Quitar por cerrar" (según estado actual).
- **Badge**: pastilla **"Por cerrar"** con la paleta del badge Glass (fondo `#E0F2F1`, texto `#00796B`) — enlace conceptual con glass. Se apila con "Urgente" en la columna Estado (vista Asignaciones) y en la pila de badges de la vista del técnico. **No** altera el orden de la lista, ni contadores, ni filtros.
- **Ciclo de vida**: se quita a mano, o desaparece con la asignación al completarse la reparación. Sin auto-limpieza ni vínculo automático con asignaciones de glass (descartado: no es derivable — ver §5).
- **BD**: `ALTER TABLE Reparacion ADD COLUMN POR_CERRAR BOOLEAN NOT NULL DEFAULT FALSE` (migración `sql/migracion-por-cerrar.sql`, la aplica el usuario; patrón `ES_CHASIS`). Actualizar también `crear_bd.sql`.
- **Servidor**: `PATCH /api/reparaciones/{idRep}/por-cerrar` body `{porCerrar: boolean, updatedAt}` con lock optimista (409 → StaleDataException). Permisos: SUPERTECNICO cualquiera; TECNICO solo si `ID_TEC` de la asignación es el suyo; ambos solo sobre `A%` no `AG%`/`AP%` (403/422 si no). Log de actividad: `MARCAR_POR_CERRAR` / `QUITAR_POR_CERRAR` (detalle: ID + IMEI). Las queries de asignaciones devuelven el campo nuevo.
- **Cliente**: flag `porCerrar` en `ReparacionResumen`; `ReparacionDAO.actualizarPorCerrar(idRep, porCerrar, updatedAt)`; acciones nuevas en el filtro de la vista Log.

## 3. Cambio 2 — % de carga por técnico

- **Qué cuenta**: asignaciones **abiertas** (sin fecha fin) **con cliente asignado** (el teléfono tiene `ID_CLI`), de tipo **reparación normal y glass**. El pulido queda fuera. Urgente/normal no distingue.
- **Pesos**:
  | Asignación | Peso |
  |---|---|
  | Reparación normal | 1 |
  | Reparación con chasis | 2 |
  | Reparación con "por cerrar" | **0,083** (≈ 1/12) |
  | Glass | 1 |
  - Si una asignación tiene chasis **y** por cerrar, **manda "por cerrar"** (0,083): lo que queda es solo cerrar, diera igual lo que fuera antes.
- **Fórmula**: carga(técnico) = Σ pesos de sus asignaciones que cuentan; **% = carga(técnico) / carga(total de todos) × 100**, redondeado a entero (los % suman ~100). Si la carga total es 0, la franja no se muestra.
- **Dónde se ve** (solo **SUPERTECNICO y ADMIN**; el técnico NO ve porcentajes):
  1. **Franja de chips** encima de la tabla de la vista Asignaciones: `Juan 42% · Marta 33% · …`, ordenada de mayor a menor. Tooltip por chip con el desglose (n normales + n chasis + n por cerrar + n glass). La vista Asignaciones es un componente compartido supertécnico/admin (modo solo-lectura), así que sale para ambos sin duplicar.
  2. **Modal de asignar** (asignación masiva): el % junto a cada técnico del selector.
- **Ayuda**: icono **ⓘ** al final de la franja → diálogo pequeño de solo texto (reutiliza `ConfirmDialog.mostrarTexto`) que explica SOLO la fórmula:
  > **¿Cómo se mide la carga?**
  > Cuentan solo las asignaciones abiertas **con cliente**, de reparación y glass (pulido no).
  > Cada asignación vale: normal = 1 · chasis = 2 · por cerrar = 0,083 · glass = 1.
  > El porcentaje de cada técnico es su parte del total: entre todos suman 100%.
- **"En vivo"**: cálculo **puro en cliente** (`CargaTecnicos`, clase de utilidad con tests JUnit) sobre las asignaciones ya cargadas; se recalcula en cada `cargar()` de la vista — es decir, tras asignar, completar, marcar/desmarcar o refrescar. Sin polling ni websockets. Los **filtros de la vista NO alteran el %** (mide la carga global real, no el subconjunto filtrado).
- **Sin persistencia**: el % no se guarda en ninguna parte; siempre derivado.

## 4. Decisiones (con su porqué)

1. **Marca manual**, no derivada de "existe glass abierto en el IMEI": el flag comunica "ya hice la mayoría", que solo lo sabe la persona; un derivado se encendería antes de tiempo.
2. Nombre **"Por cerrar"** (no "a la espera de glass"): describe el acto pendiente.
3. Color del badge = **paleta Glass** existente: enlace conceptual, cero colores nuevos.
4. Peso 0,083 (elegido por el usuario frente a 0): la asignación no desaparece de la carga; cerrar sigue costando algo.
5. **Normal + glass** en la carga (elegido por el usuario); pulido fuera.
6. % **relativo al total** (cuota de reparto que suma 100), no absoluto contra una capacidad.
7. Solo **badge**: sin cambios de orden ni contadores (los contadores del modal tienen su propio backlog).
8. % visible solo para **supertécnico y admin**.
9. Cálculo en cliente: los datos ya viajan en las queries de asignaciones; el servidor no computa nada nuevo.

## 5. Fuera de alcance

- % en la vista del técnico. Polling/refresco automático. Vínculo automático marca↔asignación de glass. Pesos configurables. Histórico de carga (posible en F4 estadísticas).

## 6. Despliegue

Orden obligatorio: **ALTER (usuario) → servidor → cliente**. Validar arranque del servidor (regla de siempre). Merge/push solo con OK del usuario. Al cerrar, se retoma la F2a donde quedó (ledger).
