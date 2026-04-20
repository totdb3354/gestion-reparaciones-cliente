package com.reparaciones.models;

/**
 * Relación entre una reparación y el componente utilizado en ella.
 * <p>Corresponde a la tabla {@code Reparacion_componente} en BD.
 * Además de la relación básica reparación-componente, registra
 * si el componente fue reutilizado y si generó una incidencia.</p>
 */
public class ReparacionComponente {

    /** ID de la reparación a la que pertenece este componente. */
    private String idRep;

    /** ID del componente utilizado. */
    private int idCom;

    /**
     * {@code true} si el componente fue extraído de otra reparación
     * (no resta stock al almacén).
     */
    private boolean esReutilizado;

    /** {@code true} si se registró una incidencia con este componente. */
    private boolean esIncidencia;

    /** {@code true} si la incidencia registrada ya está resuelta. */
    private boolean esResuelto;

    /** Descripción de la incidencia, o {@code null} si no hay incidencia. */
    private String incidencia;

    /** Observaciones libres del técnico sobre el uso de este componente. */
    private String observaciones;

    /**
     * @param idRep         ID de la reparación
     * @param idCom         ID del componente
     * @param esReutilizado {@code true} si no resta stock
     * @param esIncidencia  {@code true} si hay incidencia
     * @param esResuelto    {@code true} si la incidencia está resuelta
     * @param incidencia    descripción de la incidencia, o {@code null}
     * @param observaciones notas libres del técnico
     */
    public ReparacionComponente(String idRep, int idCom, boolean esReutilizado,
                                 boolean esIncidencia, boolean esResuelto,
                                 String incidencia, String observaciones) {
        this.idRep = idRep;
        this.idCom = idCom;
        this.esReutilizado = esReutilizado;
        this.esIncidencia = esIncidencia;
        this.esResuelto = esResuelto;
        this.incidencia = incidencia;
        this.observaciones = observaciones;
    }

    /** @return ID de la reparación */
    public String getIdRep() { return idRep; }
    public void setIdRep(String idRep) { this.idRep = idRep; }

    /** @return ID del componente */
    public int getIdCom() { return idCom; }
    public void setIdCom(int idCom) { this.idCom = idCom; }

    /** @return {@code true} si el componente fue reutilizado (no resta stock) */
    public boolean isEsReutilizado() { return esReutilizado; }
    public void setEsReutilizado(boolean esReutilizado) { this.esReutilizado = esReutilizado; }

    /** @return {@code true} si se registró una incidencia */
    public boolean isEsIncidencia() { return esIncidencia; }
    public void setEsIncidencia(boolean esIncidencia) { this.esIncidencia = esIncidencia; }

    /** @return {@code true} si la incidencia está resuelta */
    public boolean isEsResuelto() { return esResuelto; }
    public void setEsResuelto(boolean esResuelto) { this.esResuelto = esResuelto; }

    /** @return descripción de la incidencia, o {@code null} */
    public String getIncidencia() { return incidencia; }
    public void setIncidencia(String incidencia) { this.incidencia = incidencia; }

    /** @return observaciones libres del técnico */
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
