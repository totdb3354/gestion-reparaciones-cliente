package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChipsEstadoTest {

    private TelefonoInventario tel(boolean devolucion, String efectivo, int rep, int glass, int pul) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEsDevolucion(devolucion);
        t.setEstadoEfectivo(efectivo);
        t.setNormalAbiertos(rep);
        t.setGlassAbiertos(glass);
        t.setPulAbiertos(pul);
        return t;
    }

    @Test void devolucionSola() {
        assertEquals(List.of("devolución"), ChipsEstado.de(tel(true, "RECIBIDO", 0, 0, 0)));
    }

    @Test void tiposSoloEnReparacion() {
        assertEquals(List.of("rep", "glass"), ChipsEstado.de(tel(false, "EN_REPARACION", 2, 1, 0)));
        assertEquals(List.of("pulido"),       ChipsEstado.de(tel(false, "EN_REPARACION", 0, 0, 1)));
        assertTrue(ChipsEstado.de(tel(false, "REVISADO", 1, 0, 0)).isEmpty());
    }

    @Test void devolucionYTiposConviven() {
        assertEquals(List.of("devolución", "rep"), ChipsEstado.de(tel(true, "EN_REPARACION", 1, 0, 0)));
    }

    @Test void sinNadaListaVacia() {
        assertTrue(ChipsEstado.de(tel(false, "RECIBIDO", 0, 0, 0)).isEmpty());
    }
}
