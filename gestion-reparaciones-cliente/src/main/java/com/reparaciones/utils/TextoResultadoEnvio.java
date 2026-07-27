package com.reparaciones.utils;

/** Textos de UI del resultado del envío masivo (enum del servidor como string). */
public final class TextoResultadoEnvio {

    private TextoResultadoEnvio() {}

    public static String texto(String resultado, String estado) {
        return switch (resultado == null ? "" : resultado) {
            case "ENVIADO"   -> "→ ENVIADO";
            case "NO_OK"     -> "rechazado: está " + textoEstado(estado) + " — solo se envían teléfonos OK";
            case "HISTORICO" -> "rechazado: histórico — dar de alta en un lote";
            case "NO_EXISTE" -> "no existe en el sistema";
            default          -> "resultado desconocido";
        };
    }

    private static String textoEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "RECIBIDO"    -> "Recibido";
            case "EN_REVISION" -> "En revisión";
            case "BLOQUEADO"   -> "Bloqueado";
            case "ENVIADO"     -> "Enviado";
            case "DESGUACE"    -> "Desguace";
            default            -> estado == null ? "?" : estado;
        };
    }

    public static boolean esEnviado(String resultado) { return "ENVIADO".equals(resultado); }
}
