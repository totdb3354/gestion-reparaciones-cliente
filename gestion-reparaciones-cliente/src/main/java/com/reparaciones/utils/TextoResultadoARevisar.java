package com.reparaciones.utils;

/** Textos de UI del resultado del escaneo "a revisar" (enum del servidor como string). */
public final class TextoResultadoARevisar {

    private TextoResultadoARevisar() {}

    public static String texto(String resultado) {
        return switch (resultado == null ? "" : resultado) {
            case "PASADO"           -> "→ EN REVISIÓN";
            case "PASADO_ESTABA_OK" -> "→ EN REVISIÓN (estaba OK)";
            case "YA_ESTABA"        -> "ya estaba en revisión";
            case "EN_REPARACION"    -> "rechazado: en reparación (volverá solo al terminar)";
            case "BLOQUEADO"        -> "rechazado: bloqueado — usar Desbloquear";
            case "FUERA"            -> "rechazado: fuera del circuito (enviado/desguace)";
            case "HISTORICO"        -> "rechazado: histórico — dar de alta en un lote";
            case "NO_EXISTE"        -> "no existe en el sistema";
            default                 -> "resultado desconocido";
        };
    }

    public static boolean esPasado(String resultado) {
        return "PASADO".equals(resultado) || "PASADO_ESTABA_OK".equals(resultado);
    }
}
