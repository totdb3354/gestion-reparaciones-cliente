package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                asig("AG20260708_1", 1, "WEB", false, false)));// 1,5 (glass)
        assertEquals(4.583, cargas.get(1).carga(), 0.001);
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

    @Test void listaVaciaDaMapasVacios() {
        assertTrue(CargaTecnicos.calcular(List.of()).isEmpty());
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

    // Fracciones sobre jornada de 9h (lunes): 4 chasis + 5 glass = 4/8 + 5/17 = 79,4%
    @Test void fraccionesSobreJornadaLarga() {
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
        for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
        var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(0.0, dia.get(1).pctHecho(), 0.01);
        assertEquals(79.41, dia.get(1).pctPendiente(), 0.05);
        assertFalse(dia.get(1).sinJornada());
    }

    // El viernes (6h) la misma carga pesa 9/6 más: 79,4 × 1,5 = 119,1% (>100 permitido)
    @Test void escaladoPorJornadaCorta() {
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
        for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
        var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.FRIDAY, false);
        assertEquals(119.11, dia.get(1).pctPendiente(), 0.1);
    }

    // Hecho hoy (asignaciones cerradas) va al tramo hecho, con los mismos pesos
    @Test void hechoHoySumaEnSuTramo() {
        var cerrada = asig("A_done", 1, "WEB", true, false);
        cerrada.setFechaFin(java.time.LocalDateTime.now());
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_p", 1, "WEB", false, false)),
                List.of(cerrada), java.time.DayOfWeek.MONDAY, false);
        assertEquals(100.0 / 8, dia.get(1).pctHecho(), 0.01);        // 1 chasis = 12,5%
        assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H, dia.get(1).pctPendiente(), 0.01);
        assertEquals(1, dia.get(1).hecho().chasis());
        assertEquals(1, dia.get(1).pendiente().normales());
    }

    // Vista Pedidos: sin cliente no cuenta; vista Total sí
    @Test void vistaPedidosFiltraPorCliente() {
        var conCliente = asig("A_1", 1, "WEB", false, false);
        var sinCliente = asig("A_2", 1, null, false, false);
        var pedidos = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, true);
        var total   = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H,     pedidos.get(1).pctPendiente(), 0.01);
        assertEquals(2 * 100.0 / CargaTecnicos.TOPE_NORMALES_9H, total.get(1).pctPendiente(), 0.01);
    }

    // Por cerrar = 8,3% del tiempo de su tipo; solicitud pendiente libera (solo abiertas); pulido fuera
    @Test void reglasDePesoSeMantienenEnFracciones() {
        var porCerrarChasis = asig("A_pc", 1, "WEB", true, true);            // 0,083 × 1/8
        var conSolicitud    = asig("A_sol", 1, "WEB", false, false);
        conSolicitud.setEsSolicitud(1); conSolicitud.setEstadoSolicitud("PENDIENTE");
        var pulido          = asig("AP_1", 1, "WEB", false, false);
        var dia = CargaTecnicos.calcularDia(List.of(porCerrarChasis, conSolicitud, pulido),
                List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals((1.0 / 12) * (100.0 / 8), dia.get(1).pctPendiente(), 0.01);
        assertEquals(1, dia.get(1).pendiente().enEsperaPieza());
        assertEquals(0, dia.get(1).pendiente().normales());
    }

    // Sábado/domingo: sin jornada
    @Test void finDeSemanaSinJornada() {
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_1", 1, "WEB", false, false)),
                List.of(), java.time.DayOfWeek.SATURDAY, false);
        assertTrue(dia.get(1).sinJornada());
        assertEquals(0.0, dia.get(1).pctTotal(), 0.001);
    }

    @Test void formateaPctEntero() {
        assertEquals("79%", CargaTecnicos.formatearPct(79.41));
        assertEquals("112%", CargaTecnicos.formatearPct(112.4));
        assertEquals("0%", CargaTecnicos.formatearPct(0));
    }
}
