package com.reparaciones.models;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ReparacionResumenTest {

    private ReparacionResumen reparacion(LocalDateTime fechaFin) {
        return new ReparacionResumen(
                "REP-001", "123456789012345", "Daniel García",
                LocalDateTime.now(), fechaFin,
                "Batería", "Sin observaciones",
                false, false, null, null, 1, 0, null, LocalDateTime.now());
    }

    @Test
    void isPendiente_sinFechaFin_esTrue() {
        assertTrue(reparacion(null).isPendiente());
    }

    @Test
    void isPendiente_conFechaFin_esFalse() {
        assertFalse(reparacion(LocalDateTime.now()).isPendiente());
    }

    @Test
    void isEsIncidencia_porDefecto_esFalse() {
        assertFalse(reparacion(null).isEsIncidencia());
    }

    @Test
    void getIdRep_devuelveValorCorrecto() {
        assertEquals("REP-001", reparacion(null).getIdRep());
    }

    @Test
    void getImei_devuelveValorCorrecto() {
        assertEquals("123456789012345", reparacion(null).getImei());
    }

    @Test
    void tipoComponente_nullable_devuelveNull() {
        ReparacionResumen r = new ReparacionResumen(
                "REP-002", "123456789012345", "Daniel García",
                LocalDateTime.now(), null,
                null, null,
                false, false, null, null, 1, 0, null, LocalDateTime.now());
        assertNull(r.getTipoComponente());
    }

    @Test
    void nombreTecnicoAsigna_getterYSetter() {
        ReparacionResumen r = reparacion(null);
        assertNull(r.getNombreTecnicoAsigna());
        r.setNombreTecnicoAsigna("Diego");
        assertEquals("Diego", r.getNombreTecnicoAsigna());
    }

    @Test
    void telefonoUpdatedAt_setYGet() {
        ReparacionResumen r = reparacion(null);
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 6, 23, 10, 0);
        r.setTelefonoUpdatedAt(t);
        assertEquals(t, r.getTelefonoUpdatedAt());
    }

    @Test
    void cliente_setYGet() {
        ReparacionResumen r = new ReparacionResumen();
        r.setCliente("WEB");
        assertEquals("WEB", r.getCliente());
    }
}
