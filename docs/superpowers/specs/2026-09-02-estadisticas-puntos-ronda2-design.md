# Estadísticas por puntos — ronda 2 (0.16.2)

Fecha: 2026-09-02
Estado: **IMPLEMENTADA en feature/estadisticas-puntos (cliente) y feature/estadisticas-puntos-r2 (servidor) — pendiente migración preprod, smoke y merges (OK del usuario)**.
Línea: **hotfix — ajena a `main` del repo raíz** (la 0.17/F2b sigue su camino; el repo raíz NO se toca en `main`). Rama de integración **nueva `hotfix/0.16.2`** (2026-09-02), creada desde el tip de `hotfix/0.16.1` (`0a75db0`, el mismo punto del que nació la feature); `hotfix/0.16.1` queda intacta como línea de la 0.16.1.
Ramas: cliente **continúa en `feature/estadisticas-puntos`** (viva, sin mergear, nacida de ese mismo tip); servidor **rama nueva `feature/estadisticas-puntos-r2`** desde `main` del servidor (el servidor no tiene línea hotfix: su `main` es lo desplegado; la ronda 1 ya está mergeada ahí).
Spec madre: `2026-09-01-estadisticas-puntos-design.md` (sus tres PENDIENTES del smoke 2026-09-02 se resuelven aquí).

---

## 1. Contexto y alcance

Del smoke de la ronda 1 (2026-09-02) quedaron aparcados tres frentes; el usuario decidió que entran en la **0.16.2** (la release espera a esta ronda):

1. **Tarjetas resumen**: el delta ▲/▼ de "Puntos" compara mes-en-curso contra mes anterior completo y a principios de mes sale un rojo enorme sin significado.
2. **Selector Puntos / Puntos·día**: en granularidad Día (la de por defecto) ambas métricas coinciden y el toggle no hace nada.
3. **Exclusión de técnicos de estadísticas**: la media se desinfla con perfiles que no reparan a jornada completa (logística entrando como supertécnico, supertécnicos con otras tareas). Complementa (no sustituye) al futuro rol LOGISTICA de F3.

**Fuera de esta spec**: "facilitar la asignación de glass" (cuarto punto de la ronda 2 de la 0.16.2) — se brainstormeará aparte cuando lo anterior esté codificado, y su release sigue siendo la misma 0.16.2.

## 2. Tarjetas resumen: formato objetivo

Las dos tarjetas conservan su cifra grande actual (total del mes / tasa por día) y **sustituyen el delta ▲/▼ por una línea de objetivo** contra el mes anterior (decisión usuario: no mostrar la comparación como pérdida sino como objetivo a igualar):

```
┌────────────────────────┐ ┌────────────────────────┐
│ Puntos · septiembre    │ │ Puntos/día · sept      │
│ equipo 412             │ │ equipo 19,6            │
│ 46% de agosto (890)    │ │ 68% de agosto (28,7)   │
└────────────────────────┘ └────────────────────────┘
```

- **Formato**: `<pct>% de <mes anterior> (<valor anterior>)`. Porcentaje **entero truncado** (ajuste smoke 2026-09-03: con HALF_UP, 90,6 vs 90,7 marcaba "100%" en verde sin haber igualado; truncando, el 100 solo aparece al igualar de verdad); el valor anterior con el formato de puntos habitual (coma decimal; en la tasa, un decimal). Nombre del mes dinámico.
- **Cálculo** (ajuste smoke 2026-09-04, misma mecánica a dos escalas): tarjeta Puntos → `total mes en curso ÷ total mes anterior completo`. Tarjeta del día ("Puntos · hoy, `<día>`") → `puntos de HOY ÷ media de ese día de semana en el mes anterior` (los viernes contra los viernes: la referencia absorbe jornadas cortas sin calendario; laborable sin muestras → media global por día trabajado; finde sin muestras → sin línea). El % del día arranca en 0 cada mañana y apunta al 100% al cierre — desaparece el desfase intradía como rareza. Los datos de tarjetas se piden en granularidad **día**. Referencia por día de semana con ~4-5 muestras (ruido asumido).
- **Color**: gris neutro (`#7A8A9A`, el de los subtítulos) mientras `< 100%`; **verde** (el del delta positivo actual de las tarjetas) al alcanzar/superar el 100%. **Nunca rojo**.
- **Mes anterior sin datos** (arranque, técnico nuevo): la línea de objetivo no se muestra; queda solo la cifra del mes (igual que hoy con el delta).
- **Rol técnico**: mismo formato con **sus** cifras contra su propio mes anterior (regla actual de tarjetas personales).
- Todo en cliente (`PuntosEstadistica` + `EstadisticasController`); la llamada al endpoint (mes anterior + actual, granularidad mes) no cambia. Los excluidos de estadísticas (§4) no cuentan en las tarjetas de equipo.

## 3. Selector Puntos / Puntos·día + etiqueta de ventana

- El toggle queda **deshabilitado (gris) cuando la granularidad es Día**, con tooltip **"En granularidad Día ambas métricas coinciden"**. La selección se conserva: al volver a Semana/Mes/Año el toggle se rehabilita con lo que estuviera marcado. Sin cambios de posición ni de layout.
- **Etiqueta de la barra de navegación**: hoy dice "N periodos · <rango>" en todas las granularidades (jerga). En Día pasa a **"30 días con actividad · <rango>"** (tras el fix de huecos a 0, el eje X solo contiene días en los que alguien del equipo trabajó, y la cuenta no cuadra con el calendario); el resto pasa a la unidad natural: "16 semanas", "12 meses", "5 años". (Cierra la micro-decisión "30 periodos vs 30 días con actividad".)

## 4. Exclusión de técnicos de estadísticas

### BD y servidor (rama nueva desde `main` del servidor)

- **Migración** `migracion-estadisticas-exclusion.sql`: `ALTER TABLE Tecnico ADD COLUMN ES_ESTADISTICA TINYINT(1) NOT NULL DEFAULT 1` (aditivo; la aplica el usuario en preprod con vista previa). `crear_bd.sql` y MER al día.
- **`GET /api/tecnicos`** (y `/activos`): el modelo `Tecnico` gana `esEstadistica` y el flag viaja en la respuesta. Los clientes 0.16.1 lo ignoran (Gson descarta campos desconocidos).
- **Endpoints nuevos, calcados de activar/desactivar** (`UsuarioController`): `PATCH /api/usuarios/tecnicos/{idTec}/excluir-estadisticas` y `PATCH /api/usuarios/tecnicos/{idTec}/incluir-estadisticas`, con `@PreAuthorize("hasRole('ADMIN')")` (autorización server-side real, como sus hermanos) y entrada en `Log_Actividad`: acciones **`EXCLUIR_ESTADISTICAS`** / **`INCLUIR_ESTADISTICAS`**, detalle `ID_TEC: x, NOMBRE: y` (mismo formato que `ACTIVAR_USUARIO`/`DESACTIVAR_USUARIO`).
- **El endpoint de puntos no se toca**: sigue devolviendo a todos los técnicos (necesario para que el excluido se vea a sí mismo).
- Sin test de contexto Spring: **validar arranque a mano** tras el wiring.

### Cliente

- **Filtrado en cliente** (coherente con el modelo de autorización actual). En la vista Técnicos, un excluido desaparece de: **Promedio, serie Equipo, tarjetas y desplegable `+ Técnicos`** (por `nombreTecnico`, único por diseño). **Historial, Agrupado y Log intactos.**
- **El excluido en su propia vista** (rol técnico): ve su línea y sus tarjetas personales; la serie Equipo y el Promedio que ve son los del equipo que cuenta (sin él).
- **Panel**: botón **"👥 Técnicos"** junto a ⚙ Valores (**solo ADMIN**, misma visibilidad). Modal con la lista de técnicos (activos primero, inactivos tras separador, como el desplegable) y un checkbox por técnico (marcado = cuenta); aviso fijo: "Los desmarcados no cuentan en Promedio, Equipo ni tarjetas". Guardar → un PATCH por técnico **cambiado** → recarga de gráfico y tarjetas.
- **Filtro del Log**: gana las dos acciones nuevas (como se hizo con `EDITAR_PUNTOS`).

## 5. Roles

| Rol | Cambios visibles |
|---|---|
| TECNICO | Tarjetas personales en formato objetivo; si está excluido, se sigue viendo a sí mismo. |
| SUPERTECNICO | Vista filtrada (sin excluidos); sin 👥 ni ⚙. |
| ADMIN | Todo + botón 👥 Técnicos. |

## 6. Testing

- **Cliente** (JUnit, `PuntosEstadistica` y helpers puros): % de objetivo (entero, HALF_UP; actual 0 → "0%"; anterior vacío → sin línea), textos de las dos tarjetas, umbral de color (99 gris / 100 verde), filtrado de exclusión (promedio/equipo/tarjetas sin los excluidos; lista del desplegable), etiqueta "días con actividad".
- **Servidor** (JUnit + Mockito): los dos PATCH actualizan el flag y loguean con el formato de detalle; GET incluye `esEstadistica`.
- **UI JavaFX**: smoke manual (§8).

## 7. Ramas, entrega y compatibilidad

- Cliente: sigue en `feature/estadisticas-puntos` y mergea `--no-ff` a **`hotfix/0.16.2`** (la rama de integración de la release), nunca a `main` del raíz ni a `hotfix/0.16.1`. La feature de glass nacerá de `hotfix/0.16.2` (tras el merge de esta). El tag v0.16.2 se pondrá en `hotfix/0.16.2`; el merge de la línea hotfix a `main` es un pendiente aparte que decidirá el usuario. Servidor: `feature/estadisticas-puntos-r2` desde `main` del servidor. Merges `--no-ff` **solo con OK del usuario**.
- Orden: migración en preprod (usuario, vista previa) → merge servidor + deploy VM (OK usuario) + arranque validado → smoke → merge cliente a `hotfix/0.16.2` (OK usuario) → **la release 0.16.2 espera además a "facilitar la asignación de glass"** y se etiqueta cuando el usuario diga.
- Clientes 0.16.1 conviven con el ALTER y el campo nuevo (columna DEFAULT 1 invisible; Gson ignora `esEstadistica`).

## 8. Smoke (borrador)

1. Tarjetas: a día de hoy (inicio de mes) muestran "% de agosto (total)" en gris, sin rojos; con datos que superen el 100% (o simulándolo con ⚙ Valores) la línea pasa a verde; cuadre a mano de un % conocido.
2. Rol técnico: tarjetas personales con el mismo formato.
3. Granularidad Día: toggle gris con tooltip; en Semana se rehabilita conservando la selección; etiqueta "N días con actividad · rango".
4. 👥 Técnicos (admin): excluir a un técnico → desaparece de Promedio, Equipo, tarjetas y desplegable; el Promedio sube si era un perfil flojo; incluirlo → vuelve. Entradas `EXCLUIR_ESTADISTICAS`/`INCLUIR_ESTADISTICAS` en el Log y su filtro las ofrece.
5. Sesión del excluido (rol técnico): sigue viendo su línea y sus tarjetas.
6. Supertécnico: vista filtrada, sin 👥 ni ⚙.
7. Historial/Agrupado: el excluido sigue apareciendo con normalidad.
8. Cliente 0.16.1 contra servidor nuevo: técnicos y estadísticas viejas funcionan (campo ignorado).

## 9. Fuera de alcance

- Facilitar la asignación de glass (brainstorm + spec aparte, misma release 0.16.2).
- Festivos y jornada por horas; histórico de valores; autorización server-side del resto de endpoints de estadísticas (F3/roles o web).
- `formatearPeriodo` con weekBasedYear (preexistente, backlog).
