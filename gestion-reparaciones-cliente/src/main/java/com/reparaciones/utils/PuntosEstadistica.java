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
     * Media por técnico-periodo de la ventana: suma de valores de los técnicos con
     * actividad (huecos = 0) ÷ (técnicos con actividad × periodos visibles). Sin actividad → 0.
     */
    public static double promedioVentana(Map<String, Map<String, Double>> valorPorTecnicoYPeriodo,
                                          List<String> periodosVisibles) {
        double suma = 0;
        int tecnicosConActividad = 0;
        for (Map<String, Double> porPeriodo : valorPorTecnicoYPeriodo.values()) {
            double sumaTec = 0;
            for (String p : periodosVisibles) sumaTec += porPeriodo.getOrDefault(p, 0.0);
            if (sumaTec > 0) { suma += sumaTec; tecnicosConActividad++; }
        }
        if (tecnicosConActividad == 0 || periodosVisibles.isEmpty()) return 0;
        return suma / (tecnicosConActividad * periodosVisibles.size());
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

    public record Tarjetas(String mesLabel, double puntos, double puntosDia,
                           Double deltaPuntosPct, Double deltaPuntosDiaPct) {}

    /**
     * @param filasMensuales resultado del endpoint con granularidad mes cubriendo mes anterior y actual
     * @param tecnicoONull   null = equipo entero; nombre = solo ese técnico
     */
    public static Tarjetas calcularTarjetas(List<PuntoEstadisticaPuntos> filasMensuales,
                                            YearMonth mesActual, LocalDate hoy, String tecnicoONull) {
        YearMonth anterior = mesActual.minusMonths(1);
        String pActual = mesActual.toString();     // "2026-09"
        String pAnterior = anterior.toString();

        double puntosActual = 0, puntosAnterior = 0;
        boolean hayAnterior = false;
        for (PuntoEstadisticaPuntos f : filasMensuales) {
            if (tecnicoONull != null && !tecnicoONull.equals(f.getNombreTecnico())) continue;
            if (pActual.equals(f.getPeriodo()))   puntosActual   += f.getPuntos();
            if (pAnterior.equals(f.getPeriodo())) { puntosAnterior += f.getPuntos(); hayAnterior = true; }
        }

        double diaActual = puntosActual
                / Math.max(1, diasLaborables(mesActual.atDay(1),
                        mesActual.atEndOfMonth().isAfter(hoy) ? hoy : mesActual.atEndOfMonth()));
        Double deltaPuntos = null, deltaDia = null;
        if (hayAnterior && puntosAnterior > 0) {
            double diaAnterior = puntosAnterior
                    / Math.max(1, diasLaborables(anterior.atDay(1), anterior.atEndOfMonth()));
            deltaPuntos = (puntosActual - puntosAnterior) / puntosAnterior * 100;
            deltaDia    = (diaActual - diaAnterior) / diaAnterior * 100;
        }
        String mesLabel = mesActual.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        return new Tarjetas(mesLabel, puntosActual, diaActual, deltaPuntos, deltaDia);
    }
}
