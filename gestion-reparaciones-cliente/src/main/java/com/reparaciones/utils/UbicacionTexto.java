package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

import java.util.Map;
import java.util.stream.Collectors;

/** Textos de UI de la ubicación derivada y el estado efectivo (valores canónicos del servidor). */
public final class UbicacionTexto {

    private UbicacionTexto() {}

    private static final Map<String, String> UBICACIONES = Map.of(
            "ALMACEN", "Almacén", "PARA_REVISAR", "Para revisar", "BLOQUEO", "Bloqueo",
            "REPARACIONES", "Reparaciones", "LISTOS", "Listos", "PEDIDOS", "Pedidos");

    private static final Map<String, String> SUBS = Map.of(
            "PULIDO", "Pulido", "GLASS", "Glass", "NORMAL", "Normal");

    private static final Map<String, String> ESTADOS = Map.of(
            "RECIBIDO", "Recibido", "EN_REVISION", "En revisión", "BLOQUEADO", "Bloqueado",
            "EN_REPARACION", "En reparación", "OK", "OK", "ENVIADO", "Enviado", "DESGUACE", "Desguace");

    public static String ubicacion(TelefonoInventario t) {
        if (t.getUbicacion() == null) return "—";
        String base = UBICACIONES.getOrDefault(t.getUbicacion(), t.getUbicacion());
        if (t.getSubUbicaciones() != null && !t.getSubUbicaciones().isEmpty()) {
            base += " → " + t.getSubUbicaciones().stream()
                    .map(s -> SUBS.getOrDefault(s, s)).collect(Collectors.joining(" + "));
        }
        return (t.getSolicitudesPendientes() > 0 ? "⏳ " : "") + base;
    }

    public static String estado(TelefonoInventario t) {
        if (t.getEstadoEfectivo() == null) return "Histórico";
        return ESTADOS.getOrDefault(t.getEstadoEfectivo(), t.getEstadoEfectivo());
    }
}
