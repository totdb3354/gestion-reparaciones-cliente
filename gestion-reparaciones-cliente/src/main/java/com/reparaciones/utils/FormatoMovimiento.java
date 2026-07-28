package com.reparaciones.utils;

import com.reparaciones.models.MovimientoTelefono;

import java.time.format.DateTimeFormatter;

/** Línea del historial de la ficha (F2c): fecha · de→a · quién · motivo (ref). */
public final class FormatoMovimiento {

    private FormatoMovimiento() {}

    public static String linea(MovimientoTelefono m, DateTimeFormatter fmt) {
        StringBuilder sb = new StringBuilder();
        sb.append(FechaUtils.formatear(m.getFecha(), fmt));
        sb.append(" · ").append(m.getUbicacionOrigen() == null ? "—" : m.getUbicacionOrigen());
        sb.append(" → ").append(m.getUbicacionDestino());
        sb.append(" · ").append(m.getUsuario());
        if (m.getMotivo() != null && !m.getMotivo().isBlank()) sb.append(" · ").append(m.getMotivo());
        if (m.getReferencia() != null && !m.getReferencia().isBlank()) sb.append(" (").append(m.getReferencia()).append(")");
        return sb.toString();
    }
}
