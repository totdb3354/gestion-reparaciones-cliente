package com.reparaciones.models;

public class FilaReparacion {

    private final int     idCom;
    private final int     cantidad;
    private final boolean reutilizado;
    private final String  observacion;
    private final String  prefijo;

    public FilaReparacion(int idCom, int cantidad, boolean reutilizado,
                          String observacion, String prefijo) {
        this.idCom       = idCom;
        this.cantidad    = cantidad;
        this.reutilizado = reutilizado;
        this.observacion = observacion;
        this.prefijo     = prefijo;
    }

    public int     getIdCom()      { return idCom; }
    public int     getCantidad()   { return cantidad; }
    public boolean isReutilizado() { return reutilizado; }
    public String  getObservacion(){ return observacion; }
    public String  getPrefijo()    { return prefijo; }
}