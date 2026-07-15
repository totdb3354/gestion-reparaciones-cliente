package com.reparaciones.controllers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mapeo de los textos de modelo del xlsx ("iPhone 12 mini") al código interno del
 * catálogo ("12mini"). En {@code controllers} para leer
 * {@link FormularioReparacionController#MODELOS_ORDENADOS} (mismo motivo que
 * {@link SelectorModeloDialog}). Las equivalencias guardadas (BD, tabla
 * Modelo_equivalencia) van indexadas por texto normalizado y tienen prioridad.
 */
public final class ModeloMapper {

    private ModeloMapper() {}

    /** minúsculas, solo [a-z0-9] y sin el prefijo "iphone": "iPhone SE 2020" → "se2020". */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return s.startsWith("iphone") ? s.substring("iphone".length()) : s;
    }

    /** @return mapa texto original → código interno, o null si no hay correspondencia. */
    public static Map<String, String> mapear(Collection<String> textos, Map<String, String> equivalencias) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String texto : textos) {
            if (texto == null || out.containsKey(texto)) continue;
            String clave = normalizar(texto);
            String interno = equivalencias.get(clave);
            if (interno == null && FormularioReparacionController.MODELOS_ORDENADOS.contains(clave))
                interno = clave;
            out.put(texto, interno);
        }
        return out;
    }

    /** true si el texto de modelo del proveedor indica variante eSIM (regla spec atributos SKU). */
    public static boolean esEsim(String textoModelo) {
        return textoModelo != null && normalizar(textoModelo).contains("esim");
    }
}
