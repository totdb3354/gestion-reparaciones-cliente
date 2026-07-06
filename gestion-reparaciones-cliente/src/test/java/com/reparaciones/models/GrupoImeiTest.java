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

    @Test
    void indiceDeEncuentraElGrupoPorImei() {
        GrupoImei g1 = new GrupoImei("111111111111111", java.util.List.of());
        GrupoImei g2 = new GrupoImei("222222222222222", java.util.List.of());
        java.util.List<Object> items = java.util.List.of(g1, "otraCosa", g2);

        org.junit.jupiter.api.Assertions.assertEquals(0, GrupoImei.indiceDe(items, "111111111111111"));
        org.junit.jupiter.api.Assertions.assertEquals(2, GrupoImei.indiceDe(items, "222222222222222"));
        org.junit.jupiter.api.Assertions.assertEquals(-1, GrupoImei.indiceDe(items, "999999999999999"));
        org.junit.jupiter.api.Assertions.assertEquals(-1, GrupoImei.indiceDe(items, null));
    }
}
