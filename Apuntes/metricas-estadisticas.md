# Métricas de Estadísticas — diccionario

Referencia de **qué significa cada número** de la vista Estadísticas → Técnicos, con su fórmula, sus casos borde y el porqué de cada decisión. Es el documento a consultar cuando alguien pregunte "¿este número de dónde sale?" — y la spec de la analítica cuando el ERP pase a web.

Fuentes: specs `2026-09-01-estadisticas-puntos-design.md` y `2026-09-02-estadisticas-puntos-ronda2-design.md` + ajustes de smoke (2026-09-01/03). Código: `PuntosCalculo` (servidor, cálculo de puntos) y `PuntosEstadistica` (cliente, agregados y textos) — ambos con test unitario de cada regla.

Los ejemplos usan los valores seed de la tabla; el admin puede cambiarlos en ⚙ Valores.

---

## 1. La tabla de valores (`Dificultad_puntos`)

| Clave | Seed | Qué puntúa |
|---|---|---|
| `bateria` | 1,00 | pieza SKU `bat…` |
| `camara` | 0,70 | pieza SKU `cam…` |
| `chasis` | 2,00 | pieza SKU `cha…` |
| `marco` | 0,50 | pieza SKU `mc…` |
| `pantalla` | 1,00 | pieza SKU `lcd…` |
| `glass` | 0,50 | pieza SKU `g…` |
| `otro` | 0,50 | pieza sin SKU reconocible, acción "otro", reparación sin piezas |
| `pulido` | 0,25 | cada pulido completado |

- Editable por el **admin** en ⚙ Valores; cada cambio deja entrada `EDITAR_PUNTOS` en el Log (`clave: antes → después`, solo lo que cambia).
- **Valor vigente que difiere del seed**: `otro` = **0,25** (decisión de calibración 2026-09-03; pesa sobre todo en la línea de glass, donde ~⅓ de los trabajos llevan una acción "otro").
- **Los puntos se calculan siempre con la tabla vigente**: cambiar un valor re-valora también el pasado (no hay histórico de versiones).
- El mapeo prefijo→clave se evalúa en este orden: `bat`, `cha`, `cam`, `lcd`, `mc`, `g` — la `g` **siempre la última** (si no, "glass" se comería cualquier SKU que empiece por g). Prefijo que no casa → `otro`.

## 2. Puntos de un trabajo

Solo cuentan trabajos **terminados** (`FECHA_FIN` puesta); la fecha del punto es el **día de cierre**.

| Trabajo | Puntos |
|---|---|
| Reparación (R) o glass (G) con piezas | **suma del valor de cada fila de pieza — cada fila UNA vez** |
| Reparación/glass sin ninguna fila | `otro` fija (el trabajo no queda a cero) |
| Pulido (P) | `pulido` fija (aunque tuviera piezas) |

**La cantidad NO multiplica** (decisión 2026-09-03): cantidad > 1 casi siempre significa pieza rota al montar o unidad venida defectuosa — multiplicar **premiaba la rotura** (romper una pantalla y poner otra puntuaba 2,0 frente al 1,0 de hacerlo bien a la primera). Con cantidad ignorada el caso "no fue culpa del técnico" queda neutro, que es lo más justo posible sin poder distinguir culpas en los datos.

Qué cuenta como "fila de pieza" (mismo criterio que el descuento de stock del cierre):
- **Piezas normales**: sí, una vez cada fila.
- **Reutilizadas** (`ES_REUTILIZADO`): sí, igual que una nueva — montar una pieza reutilizada es el mismo trabajo. (Da igual que su fila tenga cantidad 1 o 0.)
- **Acciones "otro"** del formulario: sí — se guardan como fila real con `CANTIDAD = 0` (neutras en stock) y puntúan `otro` cada una. *(Hasta 2026-09-03 puntuaban 0 por la multiplicación por cantidad — bug cazado en smoke.)*
- **Incidencias** (piezas puestas al resolver un reclamo): sí — es trabajo real hecho, aunque implica puntuar el retrabajo si el reclamo era culpa propia (no distinguible con los datos actuales; asumido).
- **Solicitudes puras** (pedir pieza sin ponerla): **no**.

Ejemplos (valores seed): pantalla → 1,0 · pantalla+batería → 2,0 · glass solo → 0,5 · glass+marco → 1,0 · pantalla con cantidad 2 (rotura) → **1,0** · dos acciones "otro" → 1,0 · reparación sin filas → 0,5 · pulido → 0,25.

## 3. Puntos/día (la métrica normalizada)

**Puntos del periodo ÷ días laborables (L–V) del periodo.**

- **Periodo en curso** (semana/mes/año sin terminar): divide solo por los laborables **ya transcurridos, hoy incluido**, para no hundir el periodo a medias.
- Granularidad Día: ÷1 (por eso el toggle Puntos/Puntos·día se deshabilita ahí: coinciden).
- Festivos **no** descontados y jornadas por horas **no** ponderadas (simplificación asumida, como el Excel original).
- **Desfase intradía asumido**: hoy cuenta como día entero en el divisor desde las 00:00, así que la tasa sale deprimida a primera hora y se recupera durante el día (grande el primer día laborable del mes, se diluye después).
- Divisor mínimo 1 (un sábado con trabajo no divide por cero).

## 4. Tarjetas del mes (formato objetivo)

Dos tarjetas con **la misma mecánica de objetivo a dos escalas** (decisión 2026-09-04), del equipo — o del propio técnico si el rol es TECNICO (contra su propio histórico):

- **Puntos · mes**: acumulado del mes en curso; objetivo = `acumulado ÷ total del mes anterior completo`. Sube hacia el 100% conforme avanza el mes.
- **Puntos · hoy (`<día>`)**: lo hecho HOY; objetivo = `puntos de hoy ÷ media de ese día de semana en el mes anterior` — "los viernes contra los viernes". Arranca en 0% cada mañana y se espera alcanzar el 100% al cierre del día, igual que la del mes a fin de mes. La referencia por día de semana **absorbe las jornadas cortas sola** (la media de los viernes ya es de 6 horas) sin mantener ningún calendario; se autocalibra con los datos (~4-5 muestras por día de semana: algo ruidosa, asumido). Día laborable sin muestras el mes anterior → media global por día trabajado; fin de semana sin muestras → sin línea de objetivo (solo la cifra).
- Línea de objetivo: **"`pct`% de `<referencia>` (`valor`)"** — progreso hacia igualar, nunca pérdida.
- **% truncado, no redondeado**: 99,89% se muestra "99%" — el **100% solo aparece al igualar de verdad** (con redondeo, 90,6 sobre 90,7 marcaba 100% verde sin haber llegado).
- Color: **gris** < 100%, **verde** ≥ 100%. **Nunca rojo.**
- Mes anterior sin datos (o a cero) → la línea no se muestra.
- Los excluidos de estadísticas (§8) no cuentan en las tarjetas de equipo; la tarjeta personal de un excluido sí funciona.

## 5. El gráfico

- **Leyenda con IMEIs** (ajuste 2026-09-04): cada chip de técnico muestra "`nombre` · `lleva`/`típico` IMEIs" — lo que tocó en el **último periodo visible** (hoy en Día, esta semana en Semana, este mes en Mes) contra el **típico por técnico** = media por técnico-periodo trabajado de IMEIs del rango de referencia (misma fórmula que el Promedio de puntos, sin excluidos; redondeada en el chip, comparación exacta). Chip en **verde** al alcanzar el típico. Ojo: la línea naranja del gráfico es de PUNTOS — por eso la referencia de IMEIs va incrustada en el chip. Mismo criterio que el Agrupado por IMEI filtrado por técnico; un teléfono con varios trabajos cuenta 1; la cuenta la hace el servidor por periodo (campo aditivo `nImeis`; con servidor antiguo el sufijo no aparece). El detalle de *cuáles* son: popover → "Ver IMEIs". La vista/serie completa de IMEIs queda para la analítica web (F4).

- **Serie por técnico**: sus puntos (o puntos/día) por periodo. Los periodos visibles en los que no trabajó se pintan a **0** (honesto, y evita que el eje de categorías se desordene con huecos).
- **Serie Equipo (suma)**: suma por periodo de los técnicos **que cuentan** (sin excluidos). Checkbox propio, apagada por defecto.
- **Eje X**: solo existen los periodos en los que **alguien** del equipo trabajó — por eso la etiqueta dice "**30 días con actividad**" (o "16 semanas", "12 meses", "5 años") y el rango de fechas abarca más calendario que 30 días.
- **Qué se muestra** (sin flechas de navegación — ajuste 2026-09-04): sin filtro, la última ventana estándar de la granularidad (30 días / 16 semanas / 12 meses / 5 años con actividad); con filtro Desde/Hasta, **el rango filtrado entero** (para rangos largos en Día, sube la granularidad). El pasado se consulta filtrando fechas — así lo mostrado y las varas siempre coinciden.

## 6. Promedio (la línea naranja discontinua)

**Media por técnico-periodo TRABAJADO**: suma de puntos ÷ nº de pares (técnico, periodo) con actividad. **Las ausencias no diluyen** — la línea mide el ritmo de un día/semana trabajado típico del equipo, así que el vértice de un día se compara contra "lo que se trabaja". *(Hasta 2026-09-03 los huecos contaban como 0 en el denominador y todo el mundo salía "por encima".)*

**Rango de referencia = el rango mostrado** (desde el ajuste 2026-09-04, sin flechas, son siempre lo mismo):
- Sin filtro de fechas → la **última ventana estándar** (los últimos 30 días con actividad, 16 semanas…).
- Con filtro de fechas → **todo el rango filtrado**: para juzgar una época contra su propia media, se filtra esa época.
- El tooltip dice el ámbito: "Promedio del equipo (30 días con actividad): 12,5 puntos" / "(rango filtrado)".

## 7. Por encima / Por debajo (tooltip de la línea)

Media personal **por periodo trabajado** de cada técnico sobre el **mismo rango de referencia** que la línea, comparada contra ella. Estable al navegar (habla del rango de referencia, no del tramo en pantalla).

Propiedad matemática: la línea es la media ponderada de esas medias personales (ponderada por días trabajados) → **siempre hay gente a ambos lados** (salvo empate total).

Las líneas **x̄ por serie** (visibles por defecto; el checkbox "Ocultar medias" las quita) usan el **mismo rango de referencia** (ajuste 2026-09-04: antes iban sobre la ventana visible y bailaban al navegar): media de esa serie por periodo trabajado del rango — coinciden con las medias que ordenan el "Por encima/Por debajo". Sus tooltips dicen el ámbito.

## 8. Exclusión de técnicos (👥 Técnicos, solo admin)

Flag `ES_ESTADISTICA` en `Tecnico` (default: cuenta). Un excluido **desaparece de**: Promedio, serie Equipo (y su línea de media, tooltips y popover), tarjetas de equipo y desplegable de técnicos. **No desaparece de**: Historial, Agrupado, Log, ni de **su propia vista** (sigue viendo su línea y sus tarjetas personales; el Equipo/Promedio que ve son los del equipo que cuenta).

Para perfiles que no reparan a jornada completa (logística, supertécnicos con otras tareas) y que desinflarían la media. Cambios firmados en el Log (`EXCLUIR_ESTADISTICAS` / `INCLUIR_ESTADISTICAS`). Complementa (no sustituye) al futuro rol LOGISTICA.

## 9. Simplificaciones asumidas y backlog

- Festivos no descontados; jornada por horas no ponderada. **Sesgo conocido del gráfico en Día**: los puntos siguen las horas de jornada — medido el 2026-09-03 (jun-sep, equipo): lunes 117,6 / martes 127,7 / miércoles 106,0 / jueves 108,3 / viernes 77,5 puntos por día, que dividido por sus horas (9/9/8/8/6) da un ritmo casi constante de ~13-14 puntos/hora. Regla práctica: **un viernes normal ≈ ⅔ de un lunes normal**; la vista Semana neutraliza el efecto sola (misma mezcla de días cada semana). La corrección fina (calendario de festivos, horario individual, días de trabajo por persona) es gestión de personal avanzada → candidata a la analítica web (F4).
- Desfase intradía del divisor (§3).
- El eje X (y "días con actividad") puede incluir días en los que solo trabajaron excluidos (raro; backlog).
- Semanas de cambio de año: la etiqueta usa el año del lunes, no el weekBasedYear ISO (preexistente; backlog).
- Autorización por rol en cliente (salvo los PATCH de exclusión y el PUT de valores, que exigen ADMIN en servidor); llegará server-side con F3/web.

## 10. Dónde vive cada cosa

| Qué | Dónde |
|---|---|
| Puntos de un trabajo, mapeo prefijos, agregación | servidor `util/PuntosCalculo.java` (+ `PuntosCalculoTest`) |
| Endpoint de datos | `GET /api/reparaciones/estadisticas/puntos` (devuelve a todos; el filtrado de exclusión es del cliente) |
| Días laborables, puntos/día, promedio, tarjetas, textos | cliente `utils/PuntosEstadistica.java` (+ `PuntosEstadisticaTest`) |
| Vista (rango de referencia, series, tooltips) | cliente `controllers/EstadisticasController.java` |
| Valores y exclusión | `GET/PUT /api/valores-dificultad`, `PATCH /api/usuarios/tecnicos/{id}/excluir-estadisticas\|incluir-estadisticas` |
