package com.reparaciones.models;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agrupa todas las reparaciones de un mismo IMEI para la vista maestra del historial.
 * Se construye en tiempo de ejecución a partir de una lista de {@link ReparacionResumen};
 * no tiene persistencia propia.
 */
public class GrupoImei {

    private final String imei;
    private final String modelo;
    private final String observacion;
    private final LocalDateTime fechaMasAntigua;
    private final LocalDateTime fechaMasReciente;
    private final List<ReparacionResumen> reparaciones;
    private final long countIncAbiertas;
    private final boolean revisionLogistica;
    private final boolean tieneAsignaciones;

    public GrupoImei(String imei, List<ReparacionResumen> reparaciones) {
        this.imei = imei;
        this.reparaciones = reparaciones;

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
        this.revisionLogistica = primero != null && primero.isRevisionLogistica();
        this.tieneAsignaciones = primero != null && primero.isTieneAsignaciones();
    }

    public String getImei()                    { return imei; }
    public String getModelo()                  { return modelo; }
    public String getObservacion()             { return observacion; }
    public LocalDateTime getFechaMasAntigua()  { return fechaMasAntigua; }
    public LocalDateTime getFechaMasReciente() { return fechaMasReciente; }
    public List<ReparacionResumen> getReparaciones() { return reparaciones; }
    public long getCountIncAbiertas()          { return countIncAbiertas; }
    public boolean isRevisionLogistica()       { return revisionLogistica; }
    public boolean isTieneAsignaciones()       { return tieneAsignaciones; }
}
