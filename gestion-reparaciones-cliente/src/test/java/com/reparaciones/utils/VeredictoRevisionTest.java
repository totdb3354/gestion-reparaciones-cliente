package com.reparaciones.utils;

import com.reparaciones.models.RevisionTelefono;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VeredictoRevisionTest {

    private RevisionTelefono rev(Integer bateria, String pant, boolean defectoFuncional, boolean bloqueo) {
        RevisionTelefono r = new RevisionTelefono();
        r.setFunFecha(LocalDateTime.now());
        r.setFunBateriaPct(bateria);
        r.setEstPant(pant);
        r.setFunPantQuemada(defectoFuncional);
        r.setFunBloqueoOp(bloqueo);
        return r;
    }

    @Test void bloqueoMandaSobreTodo() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(90, "P", true, true));
        assertTrue(v.bloqueado());
        assertFalse(v.limpio());
    }

    @Test void bateriaBajaEsObligatoriaYAnadeNormal() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(78, null, false, false));
        assertTrue(v.bateriaObligatoria());
        assertEquals(List.of("NORMAL"), v.trabajos());
        assertFalse(v.limpio());
    }

    @Test void pantPDisparaPulidoYDefectoFuncionalNormal() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(95, "P", true, false));
        assertEquals(List.of("PULIDO", "NORMAL"), v.trabajos());
        assertFalse(v.limpio());
    }

    @Test void pantGDisparaGlass() {
        assertEquals(List.of("GLASS"), VeredictoRevision.evaluar(rev(95, "G", false, false)).trabajos());
    }

    @Test void limpioSoloConBateriaAltaYSinNada() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(94, null, false, false));
        assertTrue(v.limpio());
        assertTrue(v.trabajos().isEmpty());
    }

    @Test void bateriaNullNuncaEsLimpio() {
        assertFalse(VeredictoRevision.evaluar(rev(null, null, false, false)).limpio());
    }

    @Test void sinFuncionalGuardadaNoHayVeredicto() {
        RevisionTelefono r = rev(94, null, false, false);
        r.setFunFecha(null);
        assertNull(VeredictoRevision.evaluar(r));
    }

    @Test void tipoPrincipalPantMandaLuegoNormal() {
        assertEquals(TipoTrabajo.PULIDO, VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(95, "P", true, false))));
        assertEquals(TipoTrabajo.GLASS,  VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(95, "G", false, false))));
        assertEquals(TipoTrabajo.REPARACION, VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(78, null, false, false))));
        assertNull(VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(94, null, false, false))));
    }
}
