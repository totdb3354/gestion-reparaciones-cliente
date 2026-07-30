package com.reparaciones.utils;

/** Textos de UI del resultado del registro de devoluciones. */
public final class TextoResultadoDevolucion {

    private TextoResultadoDevolucion() {}

    public static String texto(String resultado, Integer envio) {
        return switch (resultado == null ? "" : resultado) {
            case "DEVUELTO"   -> envio != null
                    ? "→ ALMACÉN (devolución del envío " + envio + ")"
                    : "→ ALMACÉN (devolución, sin envío registrado)";
            case "NO_ENVIADO" -> "rechazado: no está enviado";
            case "NO_EXISTE"  -> "no existe en el sistema";
            default           -> "resultado desconocido";
        };
    }

    public static boolean esDevuelto(String resultado) { return "DEVUELTO".equals(resultado); }
}
