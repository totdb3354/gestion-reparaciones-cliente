package com.reparaciones.utils;

import java.util.ArrayList;
import java.util.List;

/** Parseo del campo de escaneo de IMEIs (single y pegado de lote concatenado). */
public final class ImeiUtils {

    private ImeiUtils() {}

    public enum TipoPegado { INCOMPLETO, UNICO, LOTE, CORRUPTO }

    public record ResultadoPegado(TipoPegado tipo, List<String> imeis) {}

    /**
     * Clasifica el texto del campo tras quitar todo lo no-numérico:
     * <15 → INCOMPLETO · ==15 → UNICO · >15 múltiplo de 15 → LOTE (troceado en 15) ·
     * >15 no múltiplo → CORRUPTO.
     */
    public static ResultadoPegado parsearPegadoImeis(String texto) {
        String d = texto == null ? "" : texto.replaceAll("\\D", "");
        int len = d.length();
        if (len < 15)      return new ResultadoPegado(TipoPegado.INCOMPLETO, List.of());
        if (len == 15)     return new ResultadoPegado(TipoPegado.UNICO, List.of(d));
        if (len % 15 != 0) return new ResultadoPegado(TipoPegado.CORRUPTO, List.of());
        List<String> chunks = new ArrayList<>(len / 15);
        for (int i = 0; i < len; i += 15) chunks.add(d.substring(i, i + 15));
        return new ResultadoPegado(TipoPegado.LOTE, chunks);
    }
}
