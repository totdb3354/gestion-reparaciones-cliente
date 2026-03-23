package com.reparaciones.models;

/**
 * Modelo de usuario del sistema.
 * ROL puede ser ADMIN o TECNICO.
 * idTec es nullable — los admins que reparan también tienen ID_TEC asignado en BD.
 */
public class Usuario {

    private final int idUsu;
    private final String nombreUsuario;
    private final String rol;
    private final Integer idTec; // null solo si el admin no tiene técnico asignado

    public Usuario(int idUsu, String nombreUsuario, String rol, Integer idTec) {
        this.idUsu         = idUsu;
        this.nombreUsuario = nombreUsuario;
        this.rol           = rol;
        this.idTec         = idTec;
    }

    public int     getIdUsu()         { return idUsu; }
    public String  getNombreUsuario() { return nombreUsuario; }
    public String  getRol()           { return rol; }
    public Integer getIdTec()         { return idTec; }
    public boolean esAdmin()          { return "ADMIN".equals(rol); }
}