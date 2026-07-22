package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SituacionRevisionTest {

    private TelefonoInventario tel(String estado, String efectivo) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEstado(estado);
        t.setEstadoEfectivo(efectivo);
        return t;
    }

    @Test void clasificaLasCuatroSituacionesDeLaCola() {
        assertEquals(SituacionRevision.Situacion.POR_REVISAR,   SituacionRevision.de(tel("EN_REVISION", "EN_REVISION")));
        assertEquals(SituacionRevision.Situacion.REVISADO,      SituacionRevision.de(tel("EN_REVISION", "REVISADO")));
        assertEquals(SituacionRevision.Situacion.EN_REPARACION, SituacionRevision.de(tel("EN_REVISION", "EN_REPARACION")));
        assertEquals(SituacionRevision.Situacion.REPARADO,      SituacionRevision.de(tel("EN_REVISION", "REPARADO")));
    }

    @Test void fueraDeLaColaEsOtro() {
        assertEquals(SituacionRevision.Situacion.OTRO, SituacionRevision.de(tel("RECIBIDO", "RECIBIDO")));
        assertEquals(SituacionRevision.Situacion.OTRO, SituacionRevision.de(tel("OK", "EN_REPARACION")));
    }

    @Test void textosDeChip() {
        assertEquals("por revisar", SituacionRevision.texto(SituacionRevision.Situacion.POR_REVISAR));
        assertEquals("Revisado — esperando decisión", SituacionRevision.texto(SituacionRevision.Situacion.REVISADO));
        assertEquals("en reparación", SituacionRevision.texto(SituacionRevision.Situacion.EN_REPARACION));
        assertEquals("Reparado — esperando OK", SituacionRevision.texto(SituacionRevision.Situacion.REPARADO));
    }
}
