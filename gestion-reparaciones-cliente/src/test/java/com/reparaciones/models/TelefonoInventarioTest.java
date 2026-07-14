package com.reparaciones.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelefonoInventarioTest {

    private TelefonoInventario tel(int repH, int glassH, int pulH, int pulA, int glassA, int normalA) {
        TelefonoInventario t = new TelefonoInventario();
        t.setRepHechas(repH); t.setGlassHechas(glassH); t.setPulHechos(pulH);
        t.setPulAbiertos(pulA); t.setGlassAbiertos(glassA); t.setNormalAbiertos(normalA);
        return t;
    }

    @Test void resumenTiposOmiteLosCeros() {
        assertEquals("2 Rep · 1 Glass", tel(2, 1, 0, 0, 0, 0).getResumenTipos());
        assertEquals("1 Pul", tel(0, 0, 1, 0, 0, 0).getResumenTipos());
        assertEquals("—", tel(0, 0, 0, 0, 0, 0).getResumenTipos());
    }

    @Test void tieneAsignacionesIgnoraLosPulidos() {
        // paridad con TelefonoDAO.tieneAsignacionesActivas del servidor (A% no AP%)
        assertFalse(tel(0, 0, 0, 3, 0, 0).isTieneAsignaciones());
        assertTrue(tel(0, 0, 0, 0, 1, 0).isTieneAsignaciones());
        assertTrue(tel(0, 0, 0, 0, 0, 1).isTieneAsignaciones());
    }
}
