# Estadísticas: lo de fuera de horario suma, no promedia

Fecha: 2026-09-08
Estado: **APROBADA por el usuario (2026-09-08, brainstorm).** Generaliza la regla del fin de semana (spec 2026-09-07) a las horas extra de cualquier día: cambio pequeño en servidor y cliente.
Línea: rama **`feature/estadisticas-horario`** desde `hotfix/0.16.2` (misma línea que las dos specs de estadísticas del 2026-09-07). Servidor: rama `feature/estadisticas-horario` desde `main` del submódulo (`1559a72`). La versión de release (0.16.3 o 0.17.0) la decide el usuario al cerrar.
Relación: `2026-09-07-estadisticas-finde-no-promedia-design.md` (regla que aquí se generaliza), `2026-09-02-estadisticas-puntos-ronda2-design.md` (Promedio por periodo trabajado, IMEIs típicos, tarjetas), `2026-07-09-carga-capacidad-diaria-design.md` §2 (las horas de jornada por día, de donde sale el horario). Diccionario: `gestion-reparaciones-cliente/docs/metricas-estadisticas.md` (se actualiza en la misma rama).

---

## 1. Contexto

Con la regla del finde, un sábado ya no cuenta como día trabajado en las medias. Pero el mismo problema aparece entre semana: un técnico que se queda hasta las 21:00 un lunes cierra en ese día muchos más puntos que en una jornada normal, y ese día entra en el Promedio del equipo, en su media personal y en la referencia de las tarjetas con el mismo peso que una jornada de 9 h. Caso real que motiva el cambio: **Alex, 28 de agosto de 2026**, con 38,5 puntos por horas extra, tirando de su media hacia arriba.

La regla que se quiere es la misma que la del finde: **el trabajo hecho es trabajo hecho (suma), pero la media mide el ritmo de una jornada normal (no pondera)**. El fin de semana pasa a ser un caso particular de "fuera de horario" (jornada de 0 h).

## 2. El horario

Horario del taller (dato del usuario, 2026-09-08; coincide con las horas de jornada de la carga de capacidad: 9 / 9 / 8 / 8 / 6):

| Día | Entrada | Salida | Horas |
|---|---|---|---|
| Lunes, Martes | 8:30 | 18:00 | 9 (con media hora de comida) |
| Miércoles, Jueves | 8:30 | 17:00 | 8 (con media hora de comida) |
| Viernes | 8:30 | 14:30 | 6 |
| Sábado, Domingo | — | — | 0 (sin jornada) |

Decisiones:

- **Solo cuentan inicio y fin.** La media hora de comida no tiene hora fija y un cierre a esa hora no es hora extra: la jornada se trata como franja continua.
- **Margen de 15 minutos a cada lado.** Cerrar el último móvil a las 18:03 no es hora extra. Franja efectiva: **8:15–18:15** (L-M), **8:15–17:15** (X-J), **8:15–14:45** (V). Fuera de esa franja, o en fin de semana, el cierre es **extra**.
- **Hora de Madrid.** La BD y el contenedor van en UTC; la hora de cierre se convierte a `Europe/Madrid` antes de compararla (el horario de verano lo absorbe la zona).
- **Constante en el servidor** con comentario de procedencia, como `JORNADA_HORAS` en el cliente. Cambiar el horario es redesplegar el servidor, igual que cambiar los topes de carga. Tabla configurable: fuera de alcance (misma nota de futuro que la carga).

## 3. Regla

**Lo de fuera de horario suma, no promedia**, en **todas las granularidades** (Día, Semana, Mes y Año). Un trabajo terminado fuera de la franja efectiva, o en fin de semana:

1. **Sigue sumando** en todo lo que es total: puntos del punto del gráfico (series por técnico y serie Equipo), tarjetas "Puntos · mes" y "Puntos · hoy", total del mes anterior de la tarjeta del mes, numerador de Puntos/día, chips "N IMEIs" del último periodo, popover de desglose.
2. **No entra en ninguna media**. Las medias se calculan solo con los **puntos de jornada** (cerrados dentro de la franja efectiva de un día laborable), y un par (técnico, periodo) cuenta como **trabajado** solo si tiene puntos de jornada > 0:
   - Promedio del equipo (línea naranja) y su "Por encima / Por debajo".
   - Media por técnico (línea x̄ de cada serie) y la de la serie Equipo.
   - Tarjeta "IMEIs típicos por técnico": media de **IMEIs de jornada** (IMEIs con al menos un cierre en horario en el periodo) por técnico-periodo trabajado.
   - Tarjetas: la media por día de semana del mes anterior ("los viernes contra los viernes") y la media global por día trabajado de respaldo se calculan con puntos de jornada, en numerador y divisor.
3. **Qué cambia respecto a la regla del finde**: en Semana, Mes y Año el sábado (y las horas extra) ya no entran en la media de la semana o del mes. Hasta ahora entraban porque "la semana es la unidad trabajada". Con la regla única, la media de cualquier granularidad es "ritmo de horario". Consecuencia visible y buscada: un técnico con muchas horas extra queda sistemáticamente por encima de su propia línea x̄ en todas las vistas; el tooltip lo explica.
4. **Sin cambios**: el eje X y "N días con actividad" (un día solo con extra sigue siendo un día con actividad), el orden y forma del gráfico, Puntos/día (divide entre laborables como hasta ahora), la exclusión de técnicos, el objetivo de fin de semana en las tarjetas (media de los sábados del mes anterior si hay muestras; ahora esa media es de puntos de jornada, es decir 0 → sin línea de objetivo, coherente con "sábado sin jornada").

Caso de aceptación (smoke): en Día, con agosto filtrado, el punto de Alex del 28 sigue en 38,5 y su tooltip indica cuántos puntos fueron fuera de horario; la x̄ de Alex y el Promedio del equipo bajan respecto a la 0.16.2 y coinciden con recalcularlos a mano sin los puntos extra.

## 4. Visibilidad: solo tooltips

Nada nuevo en la interfaz (decisión del usuario). Cambian los textos:

- **Tooltip del punto** (`textoTooltip`): si el punto tiene extra, tercera línea "`X` puntos fuera de horario". Ejemplo: `2026-08-28` / `38,5 puntos · 14 trabajos` / `12,0 puntos fuera de horario`. Con la métrica Puntos/día la primera línea sigue en puntos/día y el extra se da siempre en puntos. Para la serie Equipo, el extra es la suma de los técnicos que cuentan. Sin extra, el tooltip queda como hoy.
- **Ámbito de las varas** (`ambitoReferencia`): la coletilla "L–V" pasa a "**en horario**" y aparece en **todas** las granularidades: "Promedio del equipo (30 días con actividad, en horario): 12,5 puntos", "Media Alex (rango filtrado, en horario): 20,1 puntos", tarjeta IMEIs "por día trabajado · 30 días con actividad, en horario".
- **Servidor antiguo** (sin los campos nuevos): el cliente se comporta exactamente como la 0.16.2 (regla del finde en Día, coletilla "L–V" en Día, sin línea de extra en el tooltip).

## 5. Cambio en el servidor

`util/Jornada.java` (nuevo, puro, con JUnit `JornadaTest`):
- `ZoneId MADRID`, `LocalTime ENTRADA = 08:30`, `Map<DayOfWeek, LocalTime> SALIDA` {MON/TUE 18:00, WED/THU 17:00, FRI 14:30} (sábado y domingo ausentes = sin jornada), `Duration MARGEN = 15 min`. Cada constante con su comentario de procedencia (usuario 2026-09-08; horas = `JORNADA_HORAS` de la carga de capacidad).
- `static boolean enJornada(ZonedDateTime cierreMadrid)`: día con salida definida y hora local en `[ENTRADA − MARGEN, SALIDA + MARGEN]` (extremos incluidos).
- `static ZonedDateTime aMadrid(Timestamp utc)`: `utc.toInstant().atZone(MADRID)`.

`util/PuntosCalculo.java`:
- `FilaPuntos` gana `boolean enJornada`; el record interno `Rep` lo conserva (una reparación tiene una sola `FECHA_FIN`, así que todas sus filas de pieza llevan el mismo valor).
- `agregar`: acumula además `puntosJornada` (suma de `pts` de las reparaciones con `enJornada`) y `imeisJornada` (set de IMEIs con al menos una reparación `enJornada` en el periodo). Salida: dos campos nuevos.

`model/PuntoEstadisticaPuntos.java`: campos aditivos `double puntosJornada` y `int nImeisJornada`, con getters y constructor ampliado.

`dao/ReparacionDAO.getEstadisticasPuntos`:
- Selecciona `r.FECHA_FIN` completo en vez de `DATE(r.FECHA_FIN)`.
- Filtro de rango por instante: `r.FECHA_FIN >= ? AND r.FECHA_FIN < ?` con `desde` a las 00:00 de Madrid y `hasta + 1 día` a las 00:00 de Madrid, convertidos a `Timestamp` UTC (mismo patrón que `cutoffInicioDeHoyMadrid`).
- El mapper convierte con `Jornada.aMadrid` y rellena `fecha = cierre.toLocalDate()` (fecha **de Madrid**, corrige el cierre entre 00:00 y 02:00 de verano que hoy cae en el día anterior) y `enJornada = Jornada.enJornada(cierre)`.

`docs/api_contract.md`: respuesta del endpoint con `nImeis` (faltaba), `puntosJornada` y `nImeisJornada`, y la nota de que la fecha del periodo es la de Madrid.

Sin migración de BD.

## 6. Cambio en el cliente

`models/PuntoEstadisticaPuntos.java`: `Double puntosJornada` e `Integer nImeisJornada` (`null` contra un servidor sin los campos), con getters.

`utils/PuntosEstadistica.java` (puro, con JUnit):
- `static double puntosJornada(PuntoEstadisticaPuntos p)`: el campo si no es `null`; si no, `p.getPuntos()` (fallback = comportamiento 0.16.2).
- `static double puntosExtra(PuntoEstadisticaPuntos p)`: `max(0, puntos − puntosJornada)`; 0 con servidor antiguo.
- `static int imeisJornada(PuntoEstadisticaPuntos p)`: el campo si no es `null`; si no, `nImeis` (o 0 si también es `null`).
- `esLaborable` / `soloLaborables`: **sin cambios**. Con servidor nuevo son redundantes (un sábado tiene puntos de jornada 0 y no cuenta como trabajado por sí solo) pero inocuos, y sostienen el fallback.
- `textoTooltip(periodo, valor, porDia, trabajos, extra)`: parámetro nuevo; con `extra > 0` añade la tercera línea "`X` puntos fuera de horario".
- `calcularTarjetas`: segundo mapa `porDiaJornada` (suma de `puntosJornada` por día); `porDiaSemana`, `puntosLaborablesAnterior` y `diasTrabajadosAnterior` se alimentan de `porDiaJornada` (un día cuenta como trabajado si su jornada > 0); `puntosActual`, `puntosAnterior` y `puntosHoy` siguen saliendo de `porDia` (totales).

`controllers/EstadisticasController.java`:
- `valorJornadaDe(p)`: gemelo de `valorDe` sobre `puntosJornada(p)` (en Puntos/día divide entre los mismos laborables).
- `promedioVentanaActual`, `dibujarLineasMedia` (suma Equipo, media por técnico) y el cálculo de "Por encima / Por debajo": pasan de `valorDe` a `valorJornadaDe`. El filtro `soloLaborables` se mantiene.
- IMEIs típicos: `datosImeis` se construye con `imeisJornada(p)`; la condición `servidorConImeis` no cambia; los chips "N IMEIs" siguen con `nImeis`.
- `extraDe(tecnico, periodo)`: gemelo de `trabajosDe` que suma `puntosExtra`; alimenta el nuevo parámetro de `textoTooltip` en `aplicarColores`.
- `ambitoReferencia`: `servidorConJornada` (algún `puntosJornada != null`) → coletilla ", en horario" en todas las granularidades; si no, la coletilla ", L–V" solo en Día como hoy.

`gestion-reparaciones-cliente/docs/metricas-estadisticas.md`: sección nueva "Horario y puntos de jornada" (tabla del §2, margen, hora de Madrid) y actualización de §3 (la regla del finde pasa a ser un caso de la de horario, todas las granularidades), §4 (tarjetas), §5 (IMEIs típicos con IMEIs de jornada), §6-§7 (Promedio y medias "en horario"), §9 (backlog: tabla configurable de horario; festivos siguen sin descontarse) y §10 (`Jornada.java`, campos nuevos). `CHANGELOG.md` de la raíz, `[Unreleased]` → Changed (el servidor no lleva changelog; su cambio queda en `api_contract.md`).

## 7. Pruebas

Servidor:
- `JornadaTest`: lunes 8:14 → extra; 8:15 → jornada; 18:15 → jornada; 18:16 → extra; miércoles 17:15 / 17:16; viernes 14:45 / 14:46; sábado 10:00 → extra; conversión: `2026-08-28T20:30Z` es viernes 22:30 Madrid (extra) y `2026-08-28T22:30Z` es sábado 00:30 Madrid (extra, fecha 29).
- `PuntosCalculoTest`: `puntosJornada` suma solo las reparaciones con `enJornada`; una reparación con dos piezas en jornada puntúa las dos; `nImeisJornada` cuenta el IMEI con un cierre en horario y otro fuera una vez, y no cuenta el IMEI solo con cierres fuera; `puntos` y `nImeis` no cambian.
- Suite del servidor verde. Arranque de contexto comprobado (el servidor no tiene test de contexto Spring).

Cliente:
- `PuntosEstadisticaTest`: `puntosJornada`/`puntosExtra`/`imeisJornada` con y sin campos (fallback); `textoTooltip` con y sin extra, en puntos y en puntos/día; tarjetas: mes anterior con lunes 100 (80 de jornada) y sábado 20 (0 de jornada), hoy lunes → objetivo 80; hoy miércoles sin muestras → media global 80 (solo el lunes cuenta como trabajado); `puntosAnterior` = 120.
- Suite del cliente verde.
- Smoke (admin, servidor nuevo en preproducción): caso Alex 28 de agosto del §3; tooltips con "en horario" en las cuatro granularidades; en Semana la semana del 28 suma todo pero la x̄ baja; tarjeta del mes con total intacto y referencia recalculada; un cierre de prueba fuera de horario aparece en el tooltip como extra. Smoke con cliente nuevo contra servidor viejo (o campos a `null` en test): comportamiento 0.16.2.

## 8. Fuera de alcance

- Horario configurable por tabla o por técnico; festivos; jornadas reducidas puntuales. Se apuntan en el backlog del diccionario (candidatos a la analítica web, F4).
- Tarjeta o contador de "puntos extra" en la interfaz (decisión: solo tooltips).
- Línea de extra en el popover de desglose (no pedido; tooltips solo).
- Excluir del eje X los días con solo trabajo extra (siguen contando como "días con actividad").
- Exportación CSV (la vista de estadísticas no tiene).
