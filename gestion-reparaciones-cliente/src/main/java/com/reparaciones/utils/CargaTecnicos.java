package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga de trabajo por técnico (spec por-cerrar-carga §3). Cuentan solo las
 * asignaciones abiertas CON cliente, de reparación normal y glass (pulido no).
 * Pesos: normal 1 · chasis 2 · por cerrar 1/12 (manda sobre chasis) · glass 1,75.
 * Una asignación con solicitud de pieza activa y aún no recibida pesa 0 y se
 * cuenta aparte (enEsperaPieza), sea cual sea su tipo — "manda" sobre cualquier
 * otro peso. El porcentaje es relativo al total (entre todos suman ~100).
 * Cálculo puro: la UI lo repinta en cada carga de datos.
 */
public final class CargaTecnicos {

    private CargaTecnicos() {}

    public static final double PESO_POR_CERRAR = 1.0 / 12;   // ≈ 0,083
    public static final double PESO_GLASS      = 1.5;   // estimación del usuario (2026-07-09); en la v2 de capacidad diaria pasa a derivarse de los topes por jornada (spec 2026-07-09-carga-capacidad-diaria)

    public record Desglose(int normales, int chasis, int porCerrar, int glass, int enEsperaPieza, double carga) {
        Desglose sumar(int n, int c, int p, int g, int e, double peso) {
            return new Desglose(normales + n, chasis + c, porCerrar + p, glass + g,
                                 enEsperaPieza + e, carga + peso);
        }
    }

    /** {@code true} si la solicitud de pieza de la fila está activa y aún no recibida
     *  (mismo criterio que el badge "Recibido": gestionada y con stock). */
    private static boolean enEsperaDePieza(ReparacionResumen r) {
        if (r.getEsSolicitud() <= 0) return false;
        boolean recibido = "GESTIONADA".equals(r.getEstadoSolicitud()) && r.getStockSolicitud() > 0;
        return !recibido;
    }

    /** Carga por idTec; solo técnicos con alguna asignación que cuente. */
    public static Map<Integer, Desglose> calcular(List<ReparacionResumen> asignaciones) {
        Map<Integer, Desglose> out = new HashMap<>();
        for (ReparacionResumen r : asignaciones) {
            if (r.getFechaFin() != null) continue;                       // solo abiertas
            if (r.getCliente() == null || r.getCliente().isEmpty()) continue;   // solo con cliente
            TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
            if (tipo == TipoTrabajo.PULIDO) continue;                    // pulido no cuenta
            Desglose base = out.getOrDefault(r.getIdTec(), new Desglose(0, 0, 0, 0, 0, 0));
            Desglose nuevo = enEsperaDePieza(r)
                    ? base.sumar(0, 0, 0, 0, 1, 0)                       // pieza pendiente: pesa 0
                    : switch (tipo) {
                        case GLASS      -> base.sumar(0, 0, 0, 1, 0, PESO_GLASS);
                        case REPARACION -> r.isPorCerrar() ? base.sumar(0, 0, 1, 0, 0, PESO_POR_CERRAR)
                                         : r.isEsChasis()  ? base.sumar(0, 1, 0, 0, 0, 2)
                                         :                   base.sumar(1, 0, 0, 0, 0, 1);
                        case PULIDO     -> base;                          // inalcanzable (filtrado arriba)
                      };
            out.put(r.getIdTec(), nuevo);
        }
        return out;
    }

    /** Formato español de la cifra de carga: un decimal con coma, sin decimal si es entero.
     *  Determinista (no depende del Locale por defecto de la JVM). */
    public static String formatearCarga(double carga) {
        double redondeado = Math.round(carga * 10) / 10.0;
        if (redondeado == Math.floor(redondeado)) {
            return String.valueOf((long) redondeado);
        }
        return String.format(java.util.Locale.US, "%.1f", redondeado).replace('.', ',');
    }

    // ── Capacidad diaria (v2, spec 2026-07-09-carga-capacidad-diaria) ────────────
    // Techo de jornada larga (9h) por tipo — el 100% del modelo (spec 2026-07-09-carga-capacidad-diaria).
    // chasis 8 y glass 17: medidos por el usuario en el taller (2026-07-09), "si SOLO haces eso en el día".
    // normales 25: fijado por el usuario por criterio de taller (2026-07-09). El dato de BD daba un
    // mejor día real escalado a 9h de 20,3 (marcos, 18 normales en jornada de 8h del 2026-07-08, era
    // del flag ES_CHASIS), pero la ventana de datos era corta (~8 días laborables) y el usuario estima
    // el techo real por encima. El glass validado: mejor día de javi = 18 escalado vs techo 17.
    // Futuro apuntado: tabla de BD configurable y topes por técnico (F4).
    public static final int TOPE_CHASIS_9H   = 8;
    public static final int TOPE_GLASS_9H    = 17;
    public static final int TOPE_NORMALES_9H = 25;

    /** Horas de jornada por día de semana (dato del usuario, 2026-07-09). */
    public static final java.util.Map<java.time.DayOfWeek, Integer> JORNADA_HORAS = java.util.Map.of(
            java.time.DayOfWeek.MONDAY, 9,  java.time.DayOfWeek.TUESDAY, 9,
            java.time.DayOfWeek.WEDNESDAY, 8, java.time.DayOfWeek.THURSDAY, 8,
            java.time.DayOfWeek.FRIDAY, 6,
            java.time.DayOfWeek.SATURDAY, 0, java.time.DayOfWeek.SUNDAY, 0);

    /** Carga del día de un técnico: % ya escalados a la jornada de hoy (pueden superar 100). */
    public record DiaTecnico(double pctHecho, double pctPendiente,
                             Desglose hecho, Desglose pendiente, boolean sinJornada) {
        public double pctTotal() { return pctHecho + pctPendiente; }
    }

    /** Fracción de jornada de 9h que consume una asignación (0 si no computa). */
    private static double fraccion9h(ReparacionResumen r, boolean esAbierta, boolean soloPedidos) {
        if (soloPedidos && (r.getCliente() == null || r.getCliente().isEmpty())) return 0;
        if (esAbierta && enEsperaDePieza(r)) return 0;   // solicitud pendiente libera carga
        TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
        double base = switch (tipo) {
            case GLASS      -> 1.0 / TOPE_GLASS_9H;
            case REPARACION -> r.isEsChasis() ? 1.0 / TOPE_CHASIS_9H : 1.0 / TOPE_NORMALES_9H;
            case PULIDO     -> 0;   // quien pule, ese día solo pule (decisión A5)
        };
        // Por cerrar = 8,3% del tiempo de su tipo (spec §1); PESO_POR_CERRAR = 1.0/12
        return (r.isPorCerrar() && tipo == TipoTrabajo.REPARACION) ? base * PESO_POR_CERRAR : base;
    }

    public static java.util.Map<Integer, DiaTecnico> calcularDia(
            List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
            java.time.DayOfWeek dia, boolean soloPedidos) {
        int horas = JORNADA_HORAS.getOrDefault(dia, 0);
        boolean sinJornada = horas == 0;
        double factor = sinJornada ? 0 : 9.0 / horas;   // pct del día = fracción9h × 9/horas × 100
        java.util.Map<Integer, double[]> acum = new java.util.HashMap<>();      // [hecho, pendiente]
        java.util.Map<Integer, Desglose[]> desg = new java.util.HashMap<>();    // [hecho, pendiente]
        java.util.function.BiConsumer<ReparacionResumen, Boolean> suma = (r, esAbierta) -> {
            if (TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.PULIDO) return;
            if (soloPedidos && (r.getCliente() == null || r.getCliente().isEmpty())) return;
            double f = fraccion9h(r, esAbierta, soloPedidos);
            int idx = esAbierta ? 1 : 0;
            acum.computeIfAbsent(r.getIdTec(), k -> new double[2])[idx] += f;
            Desglose[] d = desg.computeIfAbsent(r.getIdTec(),
                    k -> new Desglose[]{ new Desglose(0,0,0,0,0,0), new Desglose(0,0,0,0,0,0) });
            d[idx] = sumarDesglose(d[idx], r, esAbierta, f);
        };
        cerradasHoy.forEach(r -> suma.accept(r, false));
        abiertas.forEach(r -> suma.accept(r, true));
        java.util.Map<Integer, DiaTecnico> out = new java.util.HashMap<>();
        boolean finalSin = sinJornada;
        acum.forEach((idTec, a) -> out.put(idTec, new DiaTecnico(
                a[0] * factor * 100, a[1] * factor * 100,
                desg.get(idTec)[0], desg.get(idTec)[1], finalSin)));
        return out;
    }

    /** Cuenta la asignación en el contador que le toca (mismos criterios que calcular v1). */
    private static Desglose sumarDesglose(Desglose base, ReparacionResumen r, boolean esAbierta, double fraccion) {
        if (esAbierta && enEsperaDePieza(r)) return base.sumar(0, 0, 0, 0, 1, 0);
        TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
        if (tipo == TipoTrabajo.GLASS)  return base.sumar(0, 0, 0, 1, 0, fraccion);
        if (r.isPorCerrar())            return base.sumar(0, 0, 1, 0, 0, fraccion);
        if (r.isEsChasis())             return base.sumar(0, 1, 0, 0, 0, fraccion);
        return base.sumar(1, 0, 0, 0, 0, fraccion);
    }

    /** "79%" — entero redondeado; el color lo pone la UI según nivel. */
    public static String formatearPct(double pct) {
        return Math.round(pct) + "%";
    }
}
