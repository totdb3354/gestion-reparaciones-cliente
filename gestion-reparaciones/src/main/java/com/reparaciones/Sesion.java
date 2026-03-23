package com.reparaciones;

import com.reparaciones.models.Usuario;

/**
 * Clase estática que guarda el usuario logueado durante toda la sesión.
 * Se rellena tras el login y se limpia al cerrar sesión.
 * Accesible desde cualquier controller sin inyección.
 */
public class Sesion {

    private static Usuario usuarioActual;

    public static void iniciar(Usuario usuario) { usuarioActual = usuario; }
    public static void cerrar()                 { usuarioActual = null; }
    public static Usuario getUsuario()          { return usuarioActual; }
    public static boolean esAdmin()             { return usuarioActual != null && usuarioActual.esAdmin(); }
    public static Integer getIdTec()            { return usuarioActual != null ? usuarioActual.getIdTec() : null; }
    public static boolean haySession()          { return usuarioActual != null; }
}