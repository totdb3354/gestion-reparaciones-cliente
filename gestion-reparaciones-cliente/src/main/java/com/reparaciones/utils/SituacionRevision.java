package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

/** Situación de una fila de la cola de revisión (estado BD EN_REVISION) según su estado efectivo. */
public final class SituacionRevision {

    private SituacionRevision() {}

    public enum Situacion { POR_REVISAR, REVISADO, EN_REPARACION, REPARADO, OTRO }

    public static Situacion de(TelefonoInventario t) {
        if (!"EN_REVISION".equals(t.getEstado())) return Situacion.OTRO;
        return switch (t.getEstadoEfectivo() == null ? "" : t.getEstadoEfectivo()) {
            case "REVISADO"      -> Situacion.REVISADO;
            case "REPARADO"      -> Situacion.REPARADO;
            case "EN_REPARACION" -> Situacion.EN_REPARACION;
            case "EN_REVISION"   -> Situacion.POR_REVISAR;
            default              -> Situacion.OTRO;
        };
    }

    public static String texto(Situacion s) {
        return switch (s) {
            case POR_REVISAR   -> "por revisar";
            case REVISADO      -> "Revisado — esperando decisión";
            case EN_REPARACION -> "en reparación";
            case REPARADO      -> "Reparado — esperando OK";
            case OTRO          -> "";
        };
    }
}
