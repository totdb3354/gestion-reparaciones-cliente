package com.reparaciones.controllers;

import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.TipoTrabajo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests del conteo puro de técnicos distintos por IMEI (sub-indicador "N asignados"). */
class PendientesSuperTecnicoControllerTest {

    /** Construye una fila mínima con solo IMEI + idTec (el resto irrelevante para el conteo). */
    private static ReparacionResumen rr(String imei, int idTec) {
        return new ReparacionResumen(null, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null);
    }

    @Test
    void lista_vacia_da_mapa_vacio() {
        assertTrue(PendientesSuperTecnicoController.contarTecnicosPorImei(List.of()).isEmpty());
    }

    @Test
    void un_imei_con_un_tecnico_cuenta_1() {
        Map<String, Integer> c = PendientesSuperTecnicoController.contarTecnicosPorImei(
                List.of(rr("111111111111111", 7)));
        assertEquals(1, c.get("111111111111111"));
    }

    @Test
    void un_imei_con_dos_tecnicos_distintos_cuenta_2() {
        Map<String, Integer> c = PendientesSuperTecnicoController.contarTecnicosPorImei(
                List.of(rr("111111111111111", 7), rr("111111111111111", 9)));
        assertEquals(2, c.get("111111111111111"));
    }

    @Test
    void mismo_tecnico_repetido_en_un_imei_cuenta_1() {
        Map<String, Integer> c = PendientesSuperTecnicoController.contarTecnicosPorImei(
                List.of(rr("111111111111111", 7), rr("111111111111111", 7)));
        assertEquals(1, c.get("111111111111111"), "cuenta técnicos distintos, no filas");
    }

    @Test
    void varios_imeis_mezclados() {
        Map<String, Integer> c = PendientesSuperTecnicoController.contarTecnicosPorImei(List.of(
                rr("AAA", 1), rr("AAA", 2), rr("AAA", 2),  // AAA -> técnicos {1,2} = 2
                rr("BBB", 5)));                            // BBB -> {5} = 1
        assertEquals(2, c.get("AAA"));
        assertEquals(1, c.get("BBB"));
        assertEquals(2, c.size());
    }

    @Test
    void fila_con_imei_null_se_ignora() {
        Map<String, Integer> c = PendientesSuperTecnicoController.contarTecnicosPorImei(
                List.of(rr(null, 3), rr("222222222222222", 3)));
        assertFalse(c.containsKey(null));
        assertEquals(1, c.get("222222222222222"));
        assertEquals(1, c.size());
    }

    // ─── tipoDe: deriva el tipo de trabajo del prefijo del ID ──────────────────

    @Test
    void tipo_asignacion_reparacion() {
        assertEquals(TipoTrabajo.REPARACION,
                PendientesSuperTecnicoController.tipoDe("A20260630_3"));
    }

    @Test
    void tipo_asignacion_glass() {
        assertEquals(TipoTrabajo.GLASS,
                PendientesSuperTecnicoController.tipoDe("AG20260630_3"));
    }

    @Test
    void tipo_asignacion_pulido() {
        assertEquals(TipoTrabajo.PULIDO,
                PendientesSuperTecnicoController.tipoDe("AP20260630_3"));
    }

    @Test
    void tipo_historial_por_prefijo() {
        assertEquals(TipoTrabajo.REPARACION,
                PendientesSuperTecnicoController.tipoDe("R20260630_1"));
        assertEquals(TipoTrabajo.GLASS,
                PendientesSuperTecnicoController.tipoDe("G20260630_1"));
        assertEquals(TipoTrabajo.PULIDO,
                PendientesSuperTecnicoController.tipoDe("P20260630_1"));
    }

    @Test
    void tipo_null_es_reparacion() {
        assertEquals(TipoTrabajo.REPARACION,
                PendientesSuperTecnicoController.tipoDe(null));
    }
}
