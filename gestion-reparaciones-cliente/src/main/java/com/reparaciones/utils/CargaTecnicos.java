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

    /** % relativo al total, redondeado a entero. Vacío si no hay carga. */
    public static Map<Integer, Integer> porcentajes(Map<Integer, Desglose> cargas) {
        double total = cargas.values().stream().mapToDouble(Desglose::carga).sum();
        Map<Integer, Integer> out = new HashMap<>();
        if (total <= 0) return out;
        cargas.forEach((idTec, d) -> out.put(idTec, (int) Math.round(d.carga() / total * 100)));
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
}
