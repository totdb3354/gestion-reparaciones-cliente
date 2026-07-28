package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

import java.util.ArrayList;
import java.util.List;

/** Mini-chips bajo la píldora de estado (F2c): devolución + tipos de trabajo abierto. */
public final class ChipsEstado {

    private ChipsEstado() {}

    public static List<String> de(TelefonoInventario t) {
        List<String> chips = new ArrayList<>();
        if (t.isEsDevolucion()) chips.add("devolución");
        if ("EN_REPARACION".equals(t.getEstadoEfectivo())) {
            if (t.getNormalAbiertos() > 0) chips.add("rep");
            if (t.getGlassAbiertos() > 0)  chips.add("glass");
            if (t.getPulAbiertos() > 0)    chips.add("pulido");
        }
        return chips;
    }
}
