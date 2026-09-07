# Estadísticas: el fin de semana suma, no promedia (0.16.2)

Fecha: 2026-09-07
Estado: **APROBADA por el usuario (2026-09-07, al ver un sábado de horas extra tirando de las medias).** Ajuste de la ronda 2 de estadísticas por puntos; cambio pequeño de cliente, una tarea.
Línea: hotfix `hotfix/0.16.2`. Rama **`feature/estadisticas-finde-no-promedia`** desde `hotfix/0.16.2`. Servidor: **sin cambios**.
Relación: `2026-09-02-estadisticas-puntos-ronda2-design.md` (Promedio por técnico-periodo trabajado, IMEIs típicos, tarjetas). Diccionario: `Apuntes/metricas-estadisticas.md` (se actualiza en la misma rama).

---

## 1. Contexto

Un sábado con unas horas de trabajo (horas extra) entra hoy en la vista Día como un "día trabajado" más: cuenta en el **Promedio del equipo** (línea naranja), en la **media por técnico** (línea discontinua de cada serie), en **IMEIs típicos por técnico** y, en las tarjetas, en la "media global por día trabajado" que sirve de objetivo cuando un día de semana no tiene muestras. Unas horas de sábado pesan igual que una jornada de 9 h y hunden esas medias. En cambio los totales (tarjetas del mes y del día, series de Semana/Mes/Año, serie Equipo) son correctos: el trabajo hecho es trabajo hecho. Puntos/día ya divide solo entre laborables (L–V), así que ahí el sábado ya era un extra.

## 2. Regla

**El fin de semana suma, no promedia.** Sábados y domingos:

1. **Siguen sumando** en todo lo que es total: tarjetas "Puntos · mes" y "Puntos · hoy", series por Semana/Mes/Año, serie Equipo (suma), Puntos/día (numerador) y los chips "N IMEIs" del periodo.
2. **No cuentan como periodo trabajado** en ninguna media "por día trabajado" de la granularidad Día:
   - Promedio del equipo (línea naranja) y su "Por encima / Por debajo".
   - Media por técnico (línea discontinua de cada serie) y la de la serie Equipo.
   - Tarjeta "IMEIs típicos por técnico".
   - En las tarjetas, la **media global por día trabajado** del mes anterior (objetivo de un día de semana sin muestras): se calcula solo con los días laborables, en numerador y divisor (puntos L–V ÷ días L–V trabajados). El total del mes anterior de la tarjeta "Puntos · mes" sigue incluyendo el finde. (Cazado por el test: quitar el sábado solo del divisor inflaba la media.)
3. **El punto del sábado sigue visible** en el gráfico Día y sigue contando como uno de los "30 días con actividad" de la ventana (pasó, y explica el total del mes). La ventana y el eje X no cambian.
4. Granularidades Semana, Mes y Año: sin cambios (sus periodos son laborables por definición; el finde va dentro de la semana).
5. El objetivo del día en fin de semana sigue como estaba: media de ese día de semana en el mes anterior si hay muestras ("los sábados contra los sábados"); si no, sin línea de objetivo.
6. El tooltip del Promedio en Día dice el ámbito con la coletilla "L–V": "Promedio del equipo (30 días con actividad, L–V): 12,5 puntos".

## 3. Cambio en el cliente

`utils/PuntosEstadistica.java` (puro, con JUnit):
- `public static boolean esLaborable(String periodo, String granularidad)`: en "Día", la fecha no es sábado ni domingo; en el resto de granularidades siempre `true`.
- `public static List<String> soloLaborables(Collection<String> periodos, String granularidad)`: los periodos que pasan `esLaborable`, en el mismo orden.
- `calcularTarjetas`: acumulador nuevo `puntosLaborablesAnterior` y `diasTrabajadosAnterior` solo con días L–V; la media global de respaldo es `puntosLaborablesAnterior / diasTrabajadosAnterior` (y no se calcula si no hay laborables); `puntosAnterior` sigue sumando todos los días para la tarjeta del mes.

`controllers/EstadisticasController.java`:
- `promedioVentanaActual(periodos)`: aplica `soloLaborables(periodos, granularidad)` antes de `promedioVentana`.
- Tarjeta IMEIs típicos (`renderVentana`, referencia del chip): `promedioVentana(datosImeis, soloLaborables(periodosReferencia, granularidad))`.
- `dibujarLineasMedia`: la suma por periodo de Equipo y la media por técnico se calculan sobre `soloLaborables(periodosReferencia, granularidad)`.
- `ambitoReferencia()`: en "Día" añade ", L–V" a la etiqueta de ventana (no al "rango filtrado"… también: "rango filtrado, L–V").

`Apuntes/metricas-estadisticas.md`: regla nueva "Fin de semana: suma, no promedia" en §3 (Puntos/día), §6 (Promedio), el apartado de IMEIs típicos y las tarjetas (§4). `CHANGELOG.md` `[Unreleased]` → Changed.

## 4. Pruebas

- JUnit en `PuntosEstadisticaTest`: `esLaborable` por granularidad (sábado/domingo en Día → false; laborable → true; "2026-W36"/"2026-09"/"2026" → true); `soloLaborables` filtra solo en Día y conserva el orden; tarjetas: mes anterior con lunes 100 y sábado 20, hoy miércoles sin muestras → objetivo = 100 (media por día trabajado sin el sábado), y `puntosAnterior` = 120 (el sábado sí suma).
- Suite del cliente verde.
- Smoke (admin, Día): un sábado con actividad visible en el eje; Promedio, línea de media del técnico e IMEIs típicos iguales a los de quitar ese sábado a mano; tooltip del Promedio con "L–V"; en Semana el sábado sigue sumando en la semana; tarjeta del mes incluye el sábado.

## 5. Fuera de alcance

- Festivos entre semana (no se descuentan, como hasta ahora).
- Ponderar jornadas por horas (viernes de 6 h): la referencia por día de semana de las tarjetas ya lo absorbe; el Promedio no.
- Excluir el sábado del eje X o de la cuenta de "días con actividad" (decisión: visible y contado, solo fuera de las medias).
