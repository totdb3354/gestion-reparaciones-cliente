package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CargaTecnicosTest {

    /** Asignación abierta con los campos que usa el cálculo. */
    private ReparacionResumen asig(String idRep, int idTec, String cliente,
                                   boolean chasis, boolean porCerrar) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep(idRep);
        r.setIdTec(idTec);
        r.setCliente(cliente);
        r.setEsChasis(chasis);
        r.setPorCerrar(porCerrar);
        return r;
    }

    @Test void pesaNormalChasisPorCerrarYGlass() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, "WEB", false, false),   // 1
                asig("A20260708_2", 1, "WEB", true,  false),   // 2
                asig("A20260708_3", 1, "WEB", false, true),    // 0,083
                asig("AG20260708_1", 1, "WEB", false, false)));// 1 (glass)
        assertEquals(4.083, cargas.get(1).carga(), 0.001);
        assertEquals(1, cargas.get(1).normales());
        assertEquals(1, cargas.get(1).chasis());
        assertEquals(1, cargas.get(1).porCerrar());
        assertEquals(1, cargas.get(1).glass());
    }

    @Test void porCerrarMandaSobreChasis() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, "WEB", true, true)));
        assertEquals(CargaTecnicos.PESO_POR_CERRAR, cargas.get(1).carga(), 0.0001);
    }

    @Test void sinClienteNoCuentaYPulidoTampoco() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A20260708_1", 1, null, false, false),
                asig("A20260708_2", 1, "",   false, false),
                asig("AP20260708_1", 1, "WEB", false, false)));
        assertTrue(cargas.isEmpty());
    }

    @Test void porcentajesRelativosQueSumanCien() {
        var cargas = CargaTecnicos.calcular(List.of(
                asig("A1", 1, "WEB", false, false),
                asig("A2", 1, "WEB", false, false),
                asig("A3", 1, "WEB", false, false),
                asig("A4", 2, "WEB", false, false)));
        Map<Integer, Integer> pct = CargaTecnicos.porcentajes(cargas);
        assertEquals(75, pct.get(1));
        assertEquals(25, pct.get(2));
    }

    @Test void listaVaciaDaMapasVacios() {
        assertTrue(CargaTecnicos.calcular(List.of()).isEmpty());
        assertTrue(CargaTecnicos.porcentajes(Map.of()).isEmpty());
    }
}
