package com.reparaciones.models;

import java.time.LocalDateTime;

public class LogActividad {

    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;

    public LogActividad() {}

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
}
