package com.reparaciones.utils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Parseo y clasificación del campo de filtro de IMEI (multi-IMEI por comas + pegado de lote). */
public final class FiltroImei {

    private FiltroImei() {}

    public enum EstadoFiltro { VACIO, INCOMPLETO, VALIDO }

    /** Forma canónica: solo dígitos y comas, separa con ", ", parte cada token de >15
     *  dígitos en trozos de 15 (el resto <15 queda como último token) y añade ", " tras
     *  un IMEI completo. Idempotente. */
    public static String canonicalizar(String texto) {
        if (texto == null) return "";
        String withoutSep = texto.replace(", ", ",");
        String limpio = withoutSep.replaceAll("[^\\d,]", "").replaceAll(",+", ",").replaceAll("^,", "");
        String[] tokens = limpio.split(",", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(',');
            String t = tokens[i];
            if (t.length() > 15) {
                for (int j = 0; j < t.length(); j += 15) {
                    if (j > 0) sb.append(',');
                    sb.append(t, j, Math.min(j + 15, t.length()));
                }
            } else {
                sb.append(t);
            }
        }
        String split = sb.toString();
        String display = split.replace(",", ", ");
        String[] partes = split.split(",", -1);
        if (partes[partes.length - 1].length() == 15 && !display.endsWith(", ")) {
            display = display + ", ";
        }
        return display;
    }

    /** IMEIs de exactamente 15 dígitos presentes en el texto (sustituye a parsearImeis). */
    public static Set<String> imeisValidos(String texto) {
        if (texto == null || texto.isBlank()) return Set.of();
        return Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(Collectors.toSet());
    }

    /** Clasifica el contenido para el borde: VACIO · INCOMPLETO (token <15) · VALIDO (hay 15). */
    public static EstadoFiltro estado(String texto) {
        if (texto == null || texto.trim().isEmpty()) return EstadoFiltro.VACIO;
        boolean hayIncompleto = Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(s -> s.length() < 15);
        if (hayIncompleto) return EstadoFiltro.INCOMPLETO;
        return imeisValidos(texto).isEmpty() ? EstadoFiltro.VACIO : EstadoFiltro.VALIDO;
    }
}
