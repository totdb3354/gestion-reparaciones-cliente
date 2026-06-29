package com.reparaciones.utils;

/**
 * Marcador de error de conexión transitorio: el servidor no responde (5xx) o no hay
 * red (IOException/timeout). Extiende SQLException para no romper los catch existentes;
 * el código nuevo puede distinguirla con instanceof para mostrar un banner en vez de un modal.
 */
public class ConexionException extends java.sql.SQLException {
    public ConexionException(String mensaje) { super(mensaje); }
    public ConexionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
