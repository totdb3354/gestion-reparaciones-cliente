package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga de trabajo por técnico (spec por-cerrar-carga §3). Cuentan solo las
 * asignaciones abiertas CON cliente, de reparación normal y glass (pulido no).
 * Pesos: normal 1 · chasis 2 · por cerrar 1/12 (manda sobre chasis) · glass 1.
 * El porcentaje es relativo al total (entre todos suman ~100). Cálculo puro:
 * la UI lo repinta en cada carga de datos.
 */
public final class CargaTecnicos {

    private CargaTecnicos() {}

    public static final double PESO_POR_CERRAR = 1.0 / 12;   // ≈ 0,083

    public record Desglose(int normales, int chasis, int porCerrar, int glass, double carga) {
        Desglose sumar(int n, int c, int p, int g, double peso) {
            return new Desglose(normales + n, chasis + c, porCerrar + p, glass + g, carga + peso);
        }
    }

    /** Carga por idTec; solo técnicos con alguna asignación que cuente. */
    public static Map<Integer, Desglose> calcular(List<ReparacionResumen> asignaciones) {
        Map<Integer, Desglose> out = new HashMap<>();
        for (ReparacionResumen r : asignaciones) {
            if (r.getFechaFin() != null) continue;                       // solo abiertas
            if (r.getCliente() == null || r.getCliente().isEmpty()) continue;   // solo con cliente
            TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
            Desglose base = out.getOrDefault(r.getIdTec(), new Desglose(0, 0, 0, 0, 0));
            Desglose nuevo = switch (tipo) {
                case GLASS      -> base.sumar(0, 0, 0, 1, 1);
                case REPARACION -> r.isPorCerrar() ? base.sumar(0, 0, 1, 0, PESO_POR_CERRAR)
                                 : r.isEsChasis()  ? base.sumar(0, 1, 0, 0, 2)
                                 :                   base.sumar(1, 0, 0, 0, 1);
                case PULIDO     -> null;
            };
            if (nuevo != null) out.put(r.getIdTec(), nuevo);
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
}
