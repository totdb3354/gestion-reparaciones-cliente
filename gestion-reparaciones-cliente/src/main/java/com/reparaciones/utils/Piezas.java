package com.reparaciones.utils;

import java.util.List;
import java.util.Map;

/** Deriva la categoría de pieza (etiqueta legible) a partir del prefijo de un SKU. */
public final class Piezas {
    private Piezas() {}

    // Orden por longitud descendente para evitar ambigüedad (p.ej. "cha"/"cam" antes que "g").
    private static final List<String> PREFIJOS = List.of("otro", "cha", "cam", "bat", "lcd", "mc", "g");
    private static final Map<String, String> ETIQUETAS = Map.of(
            "bat", "Batería", "cha", "Chasis", "g", "Glass", "cam", "Cámara",
            "lcd", "Pantalla", "mc", "Marco", "otro", "Otros");

    /** @return etiqueta de categoría, o "" si el SKU es null o no empieza por un prefijo conocido. */
    public static String categoria(String sku) {
        if (sku == null) return "";
        String s = sku.toLowerCase();
        for (String p : PREFIJOS) {
            if (s.startsWith(p)) return ETIQUETAS.get(p);
        }
        return "";
    }
}
