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
                asig("AG20260708_1", 1, "WEB", false, false)));// 1,75 (glass)
        assertEquals(4.833, cargas.get(1).carga(), 0.001);
        assertEquals(1, cargas.get(1).normales());
        assertEquals(1, cargas.get(1).chasis());
        assertEquals(1, cargas.get(1).porCerrar());
        assertEquals(1, cargas.get(1).glass());
        assertEquals(0, cargas.get(1).enEsperaPieza());
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

    @Test void solicitudPendienteLiberaCargaYCuentaEnEsperaPieza() {
        ReparacionResumen r = asig("A20260709_1", 1, "WEB", false, false);
        r.setEsSolicitud(1);
        r.setEstadoSolicitud("PENDIENTE");
        var cargas = CargaTecnicos.calcular(List.of(r));
        assertEquals(0.0, cargas.get(1).carga(), 0.0001);
        assertEquals(0, cargas.get(1).normales());
        assertEquals(1, cargas.get(1).enEsperaPieza());
    }

    @Test void solicitudRecibidaCuentaPesoCompleto() {
        ReparacionResumen r = asig("A20260709_2", 1, "WEB", false, false);
        r.setEsSolicitud(1);
        r.setEstadoSolicitud("GESTIONADA");
        r.setStockSolicitud(1);
        var cargas = CargaTecnicos.calcular(List.of(r));
        assertEquals(1.0, cargas.get(1).carga(), 0.0001);
        assertEquals(1, cargas.get(1).normales());
        assertEquals(0, cargas.get(1).enEsperaPieza());
    }

    @Test void solicitudPendienteLiberaCargaEnGlassTambien() {
        ReparacionResumen r = asig("AG20260709_1", 1, "WEB", false, false);
        r.setEsSolicitud(1);
        r.setEstadoSolicitud("PENDIENTE");
        var cargas = CargaTecnicos.calcular(List.of(r));
        assertEquals(0.0, cargas.get(1).carga(), 0.0001);
        assertEquals(0, cargas.get(1).glass());
        assertEquals(1, cargas.get(1).enEsperaPieza());
    }

    @Test void formatearCargaConDecimalYComa() {
        assertEquals("22,8", CargaTecnicos.formatearCarga(22.8));
    }

    @Test void formatearCargaSinDecimalSiEsEntero() {
        assertEquals("22", CargaTecnicos.formatearCarga(22.0));
    }

    @Test void formatearCargaCero() {
        assertEquals("0", CargaTecnicos.formatearCarga(0));
    }

    @Test void formatearCargaConRedondeo() {
        assertEquals("4,8", CargaTecnicos.formatearCarga(4.833));
        assertEquals("0,1", CargaTecnicos.formatearCarga(CargaTecnicos.PESO_POR_CERRAR));
    }

    @Test void chasisPlusPorCerrarPlusSolicitudPendiente() {
        ReparacionResumen r = asig("A20260709_3", 1, "WEB", true, true);
        r.setEsSolicitud(1);
        r.setEstadoSolicitud("PENDIENTE");
        var cargas = CargaTecnicos.calcular(List.of(r));
        assertEquals(0.0, cargas.get(1).carga(), 0.0001);
        assertEquals(0, cargas.get(1).chasis());
        assertEquals(0, cargas.get(1).porCerrar());
        assertEquals(1, cargas.get(1).enEsperaPieza());
    }
}
