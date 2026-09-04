package com.reparaciones.utils;

import com.reparaciones.models.PuntoEstadisticaPuntos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lógica pura de la vista de estadísticas por puntos (spec 2026-09-01):
 * fechas de periodo, días laborables, puntos/día, promedio de ventana,
 * validación del modal de valores y textos de tooltip/popover/tarjetas.
 */
public final class PuntosEstadistica {

    private PuntosEstadistica() {}

    /** Convierte un periodo del servidor al rango [inicio, fin] según la granularidad de la UI. */
    public static LocalDate[] periodoAFechas(String periodo, String granularidad) {
        return switch (granularidad) {
            case "Día" -> {
                LocalDate d = LocalDate.parse(periodo);
                yield new LocalDate[]{d, d};
            }
            case "Mes" -> {
                YearMonth ym = YearMonth.parse(periodo);
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
            case "Año" -> {
                int year = Integer.parseInt(periodo);
                yield new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
            }
            default -> { // Semana: "2026-W36" → lunes-domingo ISO
                LocalDate lunes = LocalDate.parse(periodo + "-1", DateTimeFormatter.ISO_WEEK_DATE);
                yield new LocalDate[]{lunes, lunes.plusDays(6)};
            }
        };
    }

    /** Días laborables (L-V) del rango inclusive; rango invertido → 0. Festivos no descontados. */
    public static int diasLaborables(LocalDate desde, LocalDate hasta) {
        int n = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1))
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        return Math.max(n, 0);
    }

    /** Puntos ÷ laborables del periodo; el periodo en curso corta en hoy; divisor mínimo 1. */
    public static double puntosDia(double puntos, String periodo, String granularidad, LocalDate hoy) {
        LocalDate[] rango = periodoAFechas(periodo, granularidad);
        LocalDate fin = rango[1].isAfter(hoy) ? hoy : rango[1];
        return puntos / Math.max(1, diasLaborables(rango[0], fin));
    }

    /**
     * Media por técnico-periodo TRABAJADO de la ventana: suma de valores ÷ nº de
     * técnico-periodos con actividad entre los visibles. Las ausencias no diluyen:
     * la línea mide el ritmo de un periodo trabajado típico del equipo, y así el
     * vértice de un día/semana se compara contra "lo que se trabaja" (ajuste smoke
     * 2026-09-03; antes los huecos contaban como 0 y salía todo el mundo por encima).
     * Sin actividad → 0.
     */
    public static double promedioVentana(Map<String, Map<String, Double>> valorPorTecnicoYPeriodo,
                                          List<String> periodosVisibles) {
        double suma = 0;
        int trabajados = 0;
        for (Map<String, Double> porPeriodo : valorPorTecnicoYPeriodo.values())
            for (String p : periodosVisibles) {
                double v = porPeriodo.getOrDefault(p, 0.0);
                if (v > 0) { suma += v; trabajados++; }
            }
        return trabajados == 0 ? 0 : suma / trabajados;
    }

    /** Filas sin los técnicos excluidos de estadísticas (ES_ESTADISTICA = 0). Set vacío → misma lista. */
    public static List<PuntoEstadisticaPuntos> sinExcluidos(List<PuntoEstadisticaPuntos> filas,
                                                            Set<String> nombresExcluidos) {
        if (nombresExcluidos.isEmpty()) return filas;
        return filas.stream()
                .filter(f -> !nombresExcluidos.contains(f.getNombreTecnico()))
                .collect(Collectors.toList());
    }

    /** Valida el texto del modal de valores: coma o punto, 0 ≤ v ≤ 99,99. */
    public static Optional<Double> parsePuntos(String texto) {
        if (texto == null || texto.isBlank()) return Optional.empty();
        try {
            double v = Double.parseDouble(texto.trim().replace(',', '.'));
            return (v < 0 || v > 99.99) ? Optional.empty() : Optional.of(v);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** "14,5" — un decimal, coma, HALF_UP. */
    public static String formatearPuntos(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    /** Formato de EDICIÓN: hasta 2 decimales sin ceros de cola (mínimo 1), coma. "0,25", "2,0". */
    public static String formatearPuntosEdicion(double v) {
        String s = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        if (!s.contains(".")) s += ".0";
        return s.replace('.', ',');
    }

    public static String textoTooltip(String periodo, double valor, boolean porDia, int trabajos) {
        return porDia
                ? periodo + "\n" + formatearPuntos(valor) + " puntos/día"
                : periodo + "\n" + formatearPuntos(valor) + " puntos · " + trabajos + " trabajos";
    }

    /** Etiqueta de la barra de navegación: la unidad según la granularidad de la UI.
     *  En Día el eje solo tiene días en los que alguien trabajó, de ahí "con actividad". */
    public static String etiquetaVentana(int n, String granularidad) {
        String unidad = switch (granularidad) {
            case "Día"    -> n == 1 ? "día con actividad" : "días con actividad";
            case "Semana" -> n == 1 ? "semana" : "semanas";
            case "Mes"    -> n == 1 ? "mes" : "meses";
            default       -> n == 1 ? "año" : "años"; // "Año"
        };
        return n + " " + unidad;
    }

    /** Desglose del popover; omite categorías a cero. */
    public static String textoPopover(PuntoEstadisticaPuntos p) {
        StringBuilder sb = new StringBuilder(formatearPuntos(p.getPuntos())).append(" puntos\n");
        StringBuilder linea = new StringBuilder();
        if (p.getnNormales() > 0)
            linea.append(p.getnNormales()).append(" normales (")
                 .append(formatearPuntos(p.getPuntosNormales())).append(")");
        if (p.getnGlass() > 0) {
            if (linea.length() > 0) linea.append(" · ");
            linea.append(p.getnGlass()).append(" glass (")
                 .append(formatearPuntos(p.getPuntosGlass())).append(")");
        }
        if (p.getnPulidos() > 0) {
            if (linea.length() > 0) linea.append(" · ");
            linea.append(p.getnPulidos()).append(" pulidos (")
                 .append(formatearPuntos(p.getPuntosPulidos())).append(")");
        }
        sb.append(linea);
        if (p.getnSinPiezas() > 0)
            sb.append("\n").append(p.getnSinPiezas()).append(" sin piezas");
        return sb.toString();
    }

    // ── Tarjetas resumen ──────────────────────────────────────────────────────

    public record Tarjetas(String mesLabel, String mesAnteriorLabel, String diaHoyLabel,
                           double puntos, double puntosHoy,
                           Integer pctPuntos, Double puntosAnterior,
                           Integer pctHoy, Double objetivoHoy) {}

    /**
     * Tarjetas en formato objetivo, ambas con la misma mecánica a dos escalas
     * (decisión smoke 2026-09-04): la del MES compara el acumulado contra el total
     * del mes anterior; la del DÍA compara lo hecho HOY contra la media de ese día
     * de semana en el mes anterior (los viernes contra los viernes — la referencia
     * absorbe jornadas cortas sin mantener calendarios). El % del día arranca en 0
     * cada mañana y se espera que alcance el 100% al cierre, igual que el del mes
     * a fin de mes. Día de semana laborable sin muestras el mes anterior → media
     * global por día trabajado; fin de semana sin muestras → sin línea de objetivo.
     * Los % van truncados con epsilon: "100%" solo al igualar de verdad.
     *
     * @param filasDiarias resultado del endpoint con granularidad DÍA cubriendo mes anterior y actual
     * @param tecnicoONull null = equipo (sin excluidos); nombre = solo ese técnico (los excluidos
     *                     se ignoran: la tarjeta personal del excluido sigue funcionando)
     * @param excluidos    técnicos con ES_ESTADISTICA=0 (solo aplica a las tarjetas de equipo)
     */
    public static Tarjetas calcularTarjetas(List<PuntoEstadisticaPuntos> filasDiarias,
                                            YearMonth mesActual, LocalDate hoy,
                                            String tecnicoONull, Set<String> excluidos) {
        List<PuntoEstadisticaPuntos> filas = tecnicoONull == null
                ? sinExcluidos(filasDiarias, excluidos) : filasDiarias;
        YearMonth anterior = mesActual.minusMonths(1);

        // Total del ámbito (equipo o técnico) por día de calendario
        Map<LocalDate, Double> porDia = new java.util.HashMap<>();
        for (PuntoEstadisticaPuntos f : filas) {
            if (tecnicoONull != null && !tecnicoONull.equals(f.getNombreTecnico())) continue;
            porDia.merge(LocalDate.parse(f.getPeriodo()), f.getPuntos(), Double::sum);
        }

        double puntosActual = 0, puntosAnterior = 0;
        int diasTrabajadosAnterior = 0;
        Map<DayOfWeek, double[]> porDiaSemana = new java.util.EnumMap<>(DayOfWeek.class); // [suma, n]
        for (var e : porDia.entrySet()) {
            YearMonth ym = YearMonth.from(e.getKey());
            if (ym.equals(mesActual)) {
                puntosActual += e.getValue();
            } else if (ym.equals(anterior)) {
                puntosAnterior += e.getValue();
                diasTrabajadosAnterior++;
                double[] acc = porDiaSemana.computeIfAbsent(e.getKey().getDayOfWeek(), k -> new double[2]);
                acc[0] += e.getValue();
                acc[1]++;
            }
        }

        double puntosHoy = porDia.getOrDefault(hoy, 0.0);

        Integer pctPuntos = null, pctHoy = null;
        Double totalAnterior = null, objetivoHoy = null;
        if (puntosAnterior > 0) {
            pctPuntos = (int) Math.floor(puntosActual / puntosAnterior * 100 + 1e-9);
            totalAnterior = puntosAnterior;

            DayOfWeek diaHoy = hoy.getDayOfWeek();
            double[] acc = porDiaSemana.get(diaHoy);
            boolean finde = diaHoy == DayOfWeek.SATURDAY || diaHoy == DayOfWeek.SUNDAY;
            if (acc != null && acc[1] > 0) {
                objetivoHoy = acc[0] / acc[1];
            } else if (!finde) {
                objetivoHoy = puntosAnterior / diasTrabajadosAnterior; // media global por día trabajado
            }
            if (objetivoHoy != null && objetivoHoy > 0)
                pctHoy = (int) Math.floor(puntosHoy / objetivoHoy * 100 + 1e-9);
            else
                objetivoHoy = null;
        }
        Locale es = new Locale("es", "ES");
        return new Tarjetas(
                mesActual.getMonth().getDisplayName(TextStyle.FULL, es),
                anterior.getMonth().getDisplayName(TextStyle.FULL, es),
                hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, es),
                puntosActual, puntosHoy, pctPuntos, totalAnterior, pctHoy, objetivoHoy);
    }

    /** "46% de agosto (890,0)" — la línea de objetivo de las tarjetas. */
    public static String textoObjetivo(int pct, String mesAnterior, double valorAnterior) {
        return pct + "% de " + mesAnterior + " (" + formatearPuntos(valorAnterior) + ")";
    }

    public record ObjetivoEquipo(Integer pctMes, Double mediaMes, Integer pctHoy, Double mediaHoy) {}

    /**
     * Posición del técnico respecto al equipo, mismo periodo contra mismo periodo
     * (ajuste smoke 2026-09-04): sus puntos del mes en curso vs la media de puntos por
     * técnico del equipo ese mes, y sus puntos de HOY vs la media por técnico de hoy.
     * Equipo = técnicos que cuentan (sin excluidos) con actividad en el periodo, el
     * propio técnico incluido; los puntos del técnico se leen sin filtrar (un excluido
     * puede compararse contra el equipo que cuenta). Periodo sin actividad → null.
     */
    public static ObjetivoEquipo objetivoEquipo(List<PuntoEstadisticaPuntos> filasDiarias,
                                                YearMonth mesActual, LocalDate hoy,
                                                String tecnico, Set<String> excluidos) {
        String pHoy = hoy.toString();
        double totalMes = 0, totalHoy = 0;
        Set<String> conActividadMes = new java.util.HashSet<>();
        Set<String> conActividadHoy = new java.util.HashSet<>();
        for (PuntoEstadisticaPuntos f : sinExcluidos(filasDiarias, excluidos)) {
            if (!YearMonth.from(LocalDate.parse(f.getPeriodo())).equals(mesActual)) continue;
            totalMes += f.getPuntos();
            conActividadMes.add(f.getNombreTecnico());
            if (pHoy.equals(f.getPeriodo())) {
                totalHoy += f.getPuntos();
                conActividadHoy.add(f.getNombreTecnico());
            }
        }
        double tecMes = 0, tecHoy = 0;
        for (PuntoEstadisticaPuntos f : filasDiarias) {
            if (!tecnico.equals(f.getNombreTecnico())) continue;
            if (!YearMonth.from(LocalDate.parse(f.getPeriodo())).equals(mesActual)) continue;
            tecMes += f.getPuntos();
            if (pHoy.equals(f.getPeriodo())) tecHoy += f.getPuntos();
        }
        Integer pctMes = null, pctHoy = null;
        Double mediaMes = null, mediaHoy = null;
        if (!conActividadMes.isEmpty() && totalMes > 0) {
            mediaMes = totalMes / conActividadMes.size();
            pctMes = (int) Math.floor(tecMes / mediaMes * 100 + 1e-9);
        }
        if (!conActividadHoy.isEmpty() && totalHoy > 0) {
            mediaHoy = totalHoy / conActividadHoy.size();
            pctHoy = (int) Math.floor(tecHoy / mediaHoy * 100 + 1e-9);
        }
        return new ObjetivoEquipo(pctMes, mediaMes, pctHoy, mediaHoy);
    }

    /** "96% del equipo (54,6)" — línea de posición de las tarjetas individuales. */
    public static String textoEquipo(int pct, String sufijo, double media) {
        return pct + "% " + sufijo + " (" + formatearPuntos(media) + ")";
    }
}
