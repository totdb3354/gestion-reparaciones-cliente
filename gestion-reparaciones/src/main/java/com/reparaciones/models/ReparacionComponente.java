package com.reparaciones.models;

public class ReparacionComponente {

    private String idRep;
    private int idCom;
    private boolean esReutilizado;
    private boolean esIncidencia;
    private boolean esResuelto;
    private String incidencia;
    private String observaciones;

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

    public String getIdRep() { return idRep; }
    public void setIdRep(String idRep) { this.idRep = idRep; }

    public int getIdCom() { return idCom; }
    public void setIdCom(int idCom) { this.idCom = idCom; }

    public boolean isEsReutilizado() { return esReutilizado; }
    public void setEsReutilizado(boolean esReutilizado) { this.esReutilizado = esReutilizado; }

    public boolean isEsIncidencia() { return esIncidencia; }
    public void setEsIncidencia(boolean esIncidencia) { this.esIncidencia = esIncidencia; }

    public boolean isEsResuelto() { return esResuelto; }
    public void setEsResuelto(boolean esResuelto) { this.esResuelto = esResuelto; }

    public String getIncidencia() { return incidencia; }
    public void setIncidencia(String incidencia) { this.incidencia = incidencia; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}