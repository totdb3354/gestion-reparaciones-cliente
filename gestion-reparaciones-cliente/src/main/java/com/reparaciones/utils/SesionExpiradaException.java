package com.reparaciones.utils;

/**
 * Marcador de sesión caducada (HTTP 401). Extiende SQLException para no romper los catch
 * existentes. El mensaje se conserva como "Sesión expirada. Vuelve a iniciar sesión."
 * porque UsuarioDAO.login lo comprueba con startsWith("Sesión expirada").
 */
public class SesionExpiradaException extends java.sql.SQLException {
    public SesionExpiradaException(String mensaje) { super(mensaje); }
}
