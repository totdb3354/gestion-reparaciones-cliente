# Estadísticas por puntos (dificultad) + usabilidad de la vista de técnicos

Fecha: 2026-09-01
Estado: **IMPLEMENTADA en ramas feature/estadisticas-puntos (cliente y servidor) — pendiente migración en preprod, smoke y merges (OK del usuario)**.
Ramas: cliente `feature/estadisticas-puntos` desde `hotfix/0.16.1`; servidor `feature/estadisticas-puntos` desde `main` del servidor (= lo desplegado en preprod).

---

## 1. Contexto y motivación

La pestaña "Reparaciones" de Estadísticas cuenta **filas de reparación** (`R%`/`G%`) por técnico y periodo. Se quedó vieja en tres frentes:

1. **La métrica es pobre**: una batería y un chasis valen igual; los pulidos no cuentan. La tienda ya medía productividad en un Excel ("Seguimiento reparaciones e incidencias", pestaña Técnicos) con un **valor ponderado por dificultad** del trabajo, valor/día y promedio del equipo. Se trae ese modelo a la app. Los IMEIs con al menos una intervención y un futuro puntaje más fino quedan para la analítica de la web (F4).
2. **Usabilidad**: aparecen todas las líneas de golpe (encontrar a alguien entre 12 series cuesta), los controles "Todos"/"Media"/"Actividad" no se explican solos, el slider de ventana pasa desapercibido.
3. **Desfases**: el clic en un vértice navega a una vista de Reparaciones que ha cambiado mucho (pulidos, glass separado, badges de entrega) y en rol técnico ignora el parámetro de técnico. Además `EstadisticasController.PREFIJO_TIPO` etiqueta `g`→"Pantalla" y `lcd`→"LCD", cuando el formulario de reparación (fuente buena) dice `g`→**Glass** y `lcd`→**Pantalla**.

Decisiones de contexto: esto va en la **línea hotfix** (pocos cambios, la 0.17 sigue su camino) y con inversión de UI contenida — el ERP migrará a web y el cliente JavaFX no se pule más de lo necesario (decisión 2026-08-28).

## 2. El sistema de puntos

**Tabla nueva `Dificultad_puntos`** (BD, editable por el admin desde la app):

| CLAVE (PK) | PUNTOS seed | Qué puntúa |
|---|---|---|
| `bateria` | 1,00 | pieza SKU `bat…` |
| `camara` | 0,70 | pieza SKU `cam…` |
| `chasis` | 2,00 | pieza SKU `cha…` |
| `marco` | 0,50 | pieza SKU `mc…` |
| `pantalla` | 1,00 | pieza SKU `lcd…` |
| `glass` | 0,50 | pieza SKU `g…` |
| `otro` | 0,50 | pieza sin SKU ("otros") y reparación sin piezas |
| `pulido` | 0,25 | cada pulido completado (fijo) |

- Origen: tabla de dificultad del usuario (Excel). **Revisiones fuera** por decisión del usuario (2026-09-01): ni fila ni lógica.
- Mapeo prefijo→clave en servidor, en este orden: `bat`, `cha`, `cam`, `lcd`, `mc`, `g` (el orden importa: `g` al final). Prefijo que no casa → `otro`.

**Reglas de puntuación** (solo trabajos terminados, `FECHA_FIN NOT NULL`, fecha = `DATE(FECHA_FIN)` como hoy):

- **Reparación normal (R) y glass (G), regla única**: suma de puntos de sus **piezas usadas** (× `CANTIDAD`). Piezas con SKU puntúan por su clave; piezas "otro" (sin SKU) puntúan `otro`. Un glass solo (pieza `g`) da 0,50; glass+marco da 1,00.
- **Reparación sin ninguna pieza registrada** ("no formalizada") → `otro` (0,50) fija. Así el trabajo no queda a cero y el peso es ajustable.
- **Pulido (P)** → `pulido` (0,25) fijo (los pulidos entran en estadísticas por primera vez).
- "Pieza usada" = el mismo criterio que ya usa el cierre de reparación para descontar stock (filas reales de `Reparacion_componente`; las solicitudes puras no puntúan; reutilizadas e incidencias sí — pieza puesta es trabajo hecho). **El plan verificará el criterio exacto contra la query de cierre antes de escribir la del endpoint.**
- Los puntos se calculan **siempre con la tabla vigente**: editar un valor re-valora también el pasado. Asumido y avisado en el modal (sin histórico de versiones de valores).

**Calibración de `otro`**: consulta SQL anexa (la ejecuta el usuario en preprod, Claude no hace SSH) que compara la distribución de puntos de reparaciones formalizadas con el volumen "otro", para decidir si 0,50 es justo. Ajuste posterior desde el modal, sin release.

## 3. Servidor

- **Migración** `migracion-estadisticas-puntos.sql`: `CREATE TABLE Dificultad_puntos (CLAVE VARCHAR(20) PK, PUNTOS DECIMAL(4,2) NOT NULL, UPDATED_AT TIMESTAMP …)` + seed de las 8 filas. La aplica el usuario en preprod con vista previa. `crear_bd.sql` y MER al día.
- **`GET /reparaciones/estadisticas/puntos`** (`granularidad`, `desde`, `hasta` — mismos parámetros y formato de periodo que el endpoint actual): lista de `{nombreTecnico, periodo, puntos, puntosNormales, puntosGlass, puntosPulidos, nNormales, nGlass, nPulidos, nSinPiezas}` por técnico y periodo (los puntos por categoría alimentan el desglose del popover). El desglose alimenta tooltips, popover y tarjetas sin segunda forma de llamada. Agregación en Java (patrón de `getEstadisticasPorTecnico`), leyendo `Dificultad_puntos` una vez por petición.
- **`GET /valores-dificultad`** → `[{clave, puntos}]`. **`PUT /valores-dificultad`** ← el mismo formato; solo actualiza claves existentes (no crea ni borra), valida `puntos ≥ 0`. Registra en `Log_Actividad` la acción `EDITAR_PUNTOS` con detalle `clave: antes → después` por cada cambio.
- **El endpoint actual `GET /reparaciones/estadisticas` no se toca** (los clientes 0.16.1 en producción siguen funcionando).
- Sin test de contexto Spring: **validar arranque a mano** tras el wiring nuevo.

## 4. Cliente — la vista "Técnicos"

La pestaña "Reparaciones" del sidebar de Estadísticas pasa a llamarse **"Técnicos"**; título "Puntos por técnico". La pestaña **Stock no se toca**.

```
┌──────────────────────────────────────────────────────────────┐
│ Puntos por técnico            [◉ Puntos  ○ Puntos/día]       │
│ ┌──────────────────┐ ┌──────────────────┐    [⚙ Valores]     │
│ │ Puntos · sept    │ │ Puntos/día · sept│       (solo admin) │
│ │ equipo 412  ▲+8% │ │ equipo 19,6 ▼-3% │                    │
│ └──────────────────┘ └──────────────────┘                    │
│ Periodo [Semana ▾]  Desde [__] Hasta [__]  [+ Técnicos ▾]    │
│ ☑ Equipo (suma)   ☐ Ver medias                               │
│                (gráfico: eje Y puntos, eje X tiempo)         │
│  - - - - - - - - Promedio - - - - - - - -  ← discontinua     │
│    ◀  16 sem · 18 may — 30 ago  ▶                            │
└──────────────────────────────────────────────────────────────┘
```

- **Selector de métrica** (dos estados, tipo toggle): **Puntos** = total del periodo; **Puntos/día** = total ÷ días laborables (L–V) del periodo. Cambiarlo recalcula eje, series, promedio, tooltips y tarjetas. Todo en cliente, del mismo dato.
  - Días laborables **simples** (sin ponderar por horas de jornada; decisión usuario 2026-09-01). Festivos no descontados (simplificación conocida, como el Excel).
  - **Periodo en curso**: divide solo por los días laborables ya transcurridos (hoy incluido si es laborable), para no hundir la última semana/mes.
  - Granularidad Día: puntos/día del día = puntos (÷1); días no laborables con trabajo se muestran tal cual.
- **Arranque limpio**: al abrir solo se pintan **Equipo (suma)** (serie negra, la actual "Todos" renombrada) y el **Promedio**. Los técnicos se añaden desde `+ Técnicos` (desplegable multiselección actual + **buscador de texto** arriba; activos primero, inactivos tras el separador, colores deterministas de siempre) y se quitan desde el mismo desplegable o **clicando su nombre en la leyenda** (quitar rápido; añadir, siempre desde el desplegable).
- **Promedio**: línea **discontinua horizontal** (paralela al eje X), naranja actual (`#C07800`), con etiqueta "Promedio". Valor = media por técnico y periodo de la métrica visible en la **ventana visible** (solo técnicos con actividad en ella). Se recalcula al cambiar granularidad, fechas, ventana o métrica. Es la "referencia" de hoy con nombre visible y en puntos; **siempre visible, sin checkbox propio** (es la vara de medir de la vista).
- **"Ver medias"** (off por defecto): las líneas discontinuas de media por técnico actuales, solo de las series visibles. Los checkboxes **"Todos" y "Actividad" desaparecen** (redundantes con la selección explícita).
- **Navegación temporal**: fuera el slider; **flechas ◀ ▶** que desplazan la ventana (misma ventana por granularidad: 30 días / 16 semanas / 12 meses / 5 años) con etiqueta del rango visible en medio ("16 sem · 18 may — 30 ago"). Flecha deshabilitada en el extremo. Al abrir, posicionado en lo más reciente.
- **Tarjetas resumen** (2, discretas): "Puntos · <mes en curso>" y "Puntos/día · <mes en curso>" del **equipo**, con delta porcentual pequeño contra el mes anterior completo (▲/▼). Datos de una llamada propia al endpoint (mes anterior + actual, granularidad mes), independiente de los filtros del gráfico. Para rol técnico: **sus** cifras, no las del equipo.
- **Clic en un vértice** → **popover de desglose** anclado al punto: "Marcos — sem 18-24 ago: **14,5 pts** = 9 normales (11,0) + 3 glass (2,0) + 6 pulidos (1,5) · 2 sin piezas", con botón **"Ver en Historial"** que ejecuta la navegación actual (`setFiltroInicial` con fechas del periodo + técnico) ya como acción deliberada. Serie Equipo → desglose del equipo y navegación sin técnico. Promedio no clicable. En rol técnico solo su serie es clicable (regla actual).
- **Modal "⚙ Valores"** (botón visible solo para ADMIN): tabla clave → puntos editable inline, validación (número ≥ 0, coma o punto decimal), Guardar → `PUT` → recarga del gráfico y tarjetas. Aviso fijo en el modal: "Cambiar un valor re-valora también las estadísticas pasadas".
- **Tooltips de vértice**: "14,5 puntos · 18 trabajos" (o "2,9 puntos/día" en la otra métrica).
- **Corrección de etiquetas**: `PREFIJO_TIPO` de la vista Stock corrige `g`→Glass, `lcd`→Pantalla (afecta al agrupado de la pestaña Stock, que hoy los muestra cruzados).

## 5. Roles y visibilidad

| Rol | Ve |
|---|---|
| TECNICO | Su línea + Equipo (suma) + Promedio. Sin desplegable de técnicos, sin ⚙ Valores. Tarjetas con sus cifras. |
| SUPERTECNICO | Todo menos ⚙ Valores. |
| ADMIN | Todo. |

El filtrado es **en cliente** (el endpoint devuelve a todos): coherente con el modelo de autorización actual y anotado como limitación conocida en la revisión de seguridad (la autorización server-side llega con F3/roles o con la web).

## 6. Modelos y lógica pura (cliente)

- Modelo `PuntoEstadisticaPuntos` (técnico, periodo, puntos, desglose) + método DAO `getEstadisticasPuntos(...)` en `ReparacionDAO` cliente (HTTP, patrón actual).
- Helper puro y testeado **`PuntosEstadistica`** (patrón `EntregaGlass`/`CargaTecnicos`): días laborables de un periodo (con corte "hasta hoy"), puntos/día, promedio de ventana, agregados y deltas de tarjetas, textos de tooltip y popover. `EstadisticasController` solo pinta.

## 7. Testing

- **Servidor** (JUnit + Mockito, suite actual): mapeo prefijo→clave (orden, `g` al final, no-match→otro), suma con cantidades, sin piezas→otro fija, pulidos 0,25, agregación por periodo/granularidad, PUT valida y loguea, GET/PUT de valores.
- **Cliente** (JUnit): todo `PuntosEstadistica` — días laborables (semana normal, mes, periodo en curso, granularidad día), puntos/día, promedio de ventana con técnicos sin actividad, deltas de tarjetas (mes anterior vacío → sin delta), textos.
- **UI JavaFX**: smoke manual (checklist §9).

## 8. Ramas, entrega y compatibilidad

- Patrón entrega-glass: servidor `feature/estadisticas-puntos` desde `main` del servidor; cliente `feature/estadisticas-puntos` desde `hotfix/0.16.1`. Merges `--no-ff` **solo con OK del usuario**.
- Orden de entrega: migración SQL en preprod (usuario, con vista previa) → merge servidor + deploy VM (usuario da el OK) → smoke con jar nuevo → merge cliente → release **0.16.2** (bump 4 sitios + CHANGELOG + NOVEDADES + gitlink) **cuando el usuario diga** → USB.
- Los clientes 0.16.1 conviven con el servidor nuevo (endpoint viejo intacto; tabla nueva invisible para ellos).
- Pendiente previo no bloqueante: merge `hotfix/0.16.1 → main` (se hará cuando el usuario decida; esta rama seguirá el mismo camino después).

## 9. Smoke (borrador)

1. Migración aplicada: tabla con 8 filas seed.
2. Admin: vista abre con Equipo + Promedio solos; añadir 2 técnicos por desplegable (buscador) y quitar 1 por leyenda.
3. Alternar Puntos / Puntos/día: eje, tooltip y tarjetas cambian; mes en curso no se hunde.
4. Granularidades Día/Semana/Mes/Año + flechas ◀ ▶ (extremos deshabilitados, etiqueta de rango correcta).
5. Clic en vértice: popover con desglose que cuadra a mano con 1-2 reparaciones conocidas (normal con 2 piezas, glass+marco, pulido, sin piezas); "Ver en Historial" aterriza con filtros bien puestos.
6. ⚙ Valores: subir `chasis` a 3 → gráfico y tarjetas se re-valoran; entrada en el Log; validación rechaza negativo/texto.
7. Rol técnico: solo su línea + equipo + promedio; sin desplegable ni ⚙; tarjetas personales; clic solo en su serie.
8. Rol supertécnico: todo menos ⚙.
9. Cliente 0.16.1 contra el servidor nuevo: estadísticas viejas siguen funcionando.
10. Pestaña Stock: agrupado con `g`=Glass y `lcd`=Pantalla bien etiquetados.

## 10. Fuera de alcance

- Revisiones (decisión usuario; llegarán con la 0.17 si se quiere, con fila nueva en la tabla).
- Ranking, comparativas, tarjetas por técnico, dashboard completo → web (F4).
- Festivos y jornada por horas en el denominador; topes por técnico.
- Histórico de versiones de los valores de dificultad.
- Autorización server-side de los endpoints nuevos (F3/roles o web).
- Sección IMEIs de estadísticas (roadmap F4).
