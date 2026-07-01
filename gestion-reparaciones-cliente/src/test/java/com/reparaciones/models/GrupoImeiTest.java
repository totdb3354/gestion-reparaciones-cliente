package com.reparaciones.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests del conteo por tipo y el resumen compacto del maestro Agrupado (lógica pura). */
class GrupoImeiTest {

    /** Fila mínima con solo el ID (lo único que determina el tipo). */
    private static ReparacionResumen rr(String idRep) {
        return new ReparacionResumen(idRep, "111111111111111", null, null, null, null, null,
                false, false, null, null, 0, 0, null, null);
    }

    @Test
    void cuenta_por_tipo_segun_prefijo() {
        GrupoImei g = new GrupoImei("111", List.of(
                rr("R20260630_1"), rr("G20260630_1"), rr("G20260630_2"), rr("P20260630_1")));
        assertEquals(1, g.getCountRep());
        assertEquals(2, g.getCountGlass());
        assertEquals(1, g.getCountPul());
    }

    @Test
    void resumen_omite_tipos_con_cero() {
        GrupoImei g = new GrupoImei("111", List.of(rr("R20260630_1"), rr("R20260630_2")));
        assertEquals("2 Rep", g.getResumenTipos());
    }

    @Test
    void resumen_mezcla_en_orden_rep_glass_pul() {
        GrupoImei g = new GrupoImei("111", List.of(
                rr("P20260630_1"), rr("R20260630_1"), rr("G20260630_1")));
        assertEquals("1 Rep · 1 Glass · 1 Pul", g.getResumenTipos());
    }

    @Test
    void resumen_solo_glass() {
        GrupoImei g = new GrupoImei("111", List.of(rr("G20260630_1")));
        assertEquals("1 Glass", g.getResumenTipos());
    }
}
