package com.reparaciones.models;

import com.reparaciones.utils.TipoTrabajo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agrupa todos los trabajos de un mismo IMEI para la vista maestra del apartado Agrupado.
 * La lista mezcla los tres tipos (Reparación + Glass + Pulido); el tipo de cada entrada se
 * deriva del prefijo del {@code ID_REP} ({@link TipoTrabajo#desde}). Se construye en tiempo
 * de ejecución a partir de una lista de {@link ReparacionResumen}; no tiene persistencia propia.
 */
public class GrupoImei {

    private final String imei;
    private final String modelo;
    private final String observacion;
    private final String cliente;
    private final LocalDateTime fechaMasAntigua;
    private final LocalDateTime fechaMasReciente;
    private final List<ReparacionResumen> reparaciones;
    private final long countIncAbiertas;
    private final boolean revisionLogistica;
    private final boolean tieneAsignaciones;
    private final LocalDateTime telefonoUpdatedAt;
    private final int countRep;
    private final int countGlass;
    private final int countPul;

    public GrupoImei(String imei, List<ReparacionResumen> reparaciones) {
        this.imei = imei;
        this.reparaciones = reparaciones;

        int nR = 0, nG = 0, nP = 0;
        for (ReparacionResumen rep : reparaciones) {
            switch (TipoTrabajo.desde(rep.getIdRep())) {
                case GLASS  -> nG++;
                case PULIDO -> nP++;
                default     -> nR++;
            }
        }
        this.countRep   = nR;
        this.countGlass = nG;
        this.countPul   = nP;

        this.modelo = reparaciones.stream()
                .map(ReparacionResumen::getModelo)
                .filter(m -> m != null && !m.isEmpty())
                .findFirst()
                .orElse("");

        this.observacion = reparaciones.stream()
                .map(ReparacionResumen::getObservacionTelefono)
                .filter(o -> o != null && !o.isEmpty())
                .findFirst()
                .orElse(null);

        this.cliente = reparaciones.stream()
                .map(ReparacionResumen::getCliente)
                .filter(c -> c != null && !c.isEmpty())
                .findFirst()
                .orElse(null);

        this.fechaMasAntigua = reparaciones.stream()
                .map(ReparacionResumen::getFechaAsig)
                .filter(f -> f != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        this.fechaMasReciente = reparaciones.stream()
                .map(ReparacionResumen::getFechaFin)
                .filter(f -> f != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        this.countIncAbiertas = reparaciones.stream()
                .filter(r -> r.isEsIncidencia() && !r.isEsResuelto())
                .count();

        ReparacionResumen primero = reparaciones.isEmpty() ? null : reparaciones.get(0);
        this.revisionLogistica  = primero != null && primero.isRevisionLogistica();
        this.tieneAsignaciones  = primero != null && primero.isTieneAsignaciones();
        this.telefonoUpdatedAt  = primero != null ? primero.getTelefonoUpdatedAt() : null;
    }

    public String getImei()                    { return imei; }
    public String getModelo()                  { return modelo; }
    public String getObservacion()             { return observacion; }
    public String getCliente()                 { return cliente; }
    public LocalDateTime getFechaMasAntigua()  { return fechaMasAntigua; }
    public LocalDateTime getFechaMasReciente() { return fechaMasReciente; }
    public List<ReparacionResumen> getReparaciones() { return reparaciones; }
    public long getCountIncAbiertas()          { return countIncAbiertas; }
    public boolean isRevisionLogistica()                    { return revisionLogistica; }
    public boolean isTieneAsignaciones()                    { return tieneAsignaciones; }
    public LocalDateTime getTelefonoUpdatedAt()             { return telefonoUpdatedAt; }
    public int getCountRep()                   { return countRep; }
    public int getCountGlass()                 { return countGlass; }
    public int getCountPul()                   { return countPul; }

    /**
     * Resumen compacto por tipo para la fila maestra, p.ej. {@code "2 Rep · 1 Glass"}.
     * Omite los tipos con cero trabajos. Nunca vacío en la práctica (todo grupo tiene ≥1).
     */
    public String getResumenTipos() {
        StringBuilder sb = new StringBuilder();
        if (countRep   > 0)                       sb.append(countRep).append(" Rep");
        if (countGlass > 0) { sep(sb); sb.append(countGlass).append(" Glass"); }
        if (countPul   > 0) { sep(sb); sb.append(countPul).append(" Pul"); }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    private static void sep(StringBuilder sb) { if (sb.length() > 0) sb.append(" · "); }
}
