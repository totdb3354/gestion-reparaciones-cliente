package com.reparaciones.utils;

import com.reparaciones.controllers.CatalogoAtributos;

import java.util.List;
import java.util.Map;

/** Resolución de textos de color de proveedor al color oficial (spec atributos SKU). */
public final class ColorMapper {

    private ColorMapper() {}

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return texto.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Color oficial para el texto del proveedor, restringido a la paleta del modelo
     * (o a la unión completa si el modelo es null/desconocido). null = sin mapear.
     */
    public static String resolver(String textoColor, String modeloInterno, Map<String, String> equivalencias) {
        String norm = normalizar(textoColor);
        if (norm.isEmpty()) return null;
        List<String> paleta = CatalogoAtributos.coloresDe(modeloInterno);
        if (paleta.isEmpty()) paleta = CatalogoAtributos.COLORES_TODOS;
        for (String oficial : paleta) {
            if (normalizar(oficial).equals(norm)) return oficial;
        }
        String porEquivalencia = equivalencias.get(norm);
        return porEquivalencia != null && paleta.contains(porEquivalencia) ? porEquivalencia : null;
    }
}
