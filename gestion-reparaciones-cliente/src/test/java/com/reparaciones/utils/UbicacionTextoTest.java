package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UbicacionTextoTest {

    private TelefonoInventario tel(String estadoEfectivo, String ubicacion, List<String> subs, int solicitudes) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEstadoEfectivo(estadoEfectivo);
        t.setUbicacion(ubicacion);
        t.setSubUbicaciones(subs);
        t.setSolicitudesPendientes(solicitudes);
        return t;
    }

    @Test void formateaUbicacionesSimplesYCompuestas() {
        assertEquals("Almacén", UbicacionTexto.ubicacion(tel("RECIBIDO", "ALMACEN", List.of(), 0)));
        assertEquals("Reparaciones → Pulido + Normal",
                UbicacionTexto.ubicacion(tel("EN_REPARACION", "REPARACIONES", List.of("PULIDO", "NORMAL"), 0)));
        assertEquals("—", UbicacionTexto.ubicacion(tel(null, null, List.of(), 0)));
    }

    @Test void elRelojDeSolicitudPendienteVaDelante() {
        assertEquals("⏳ Reparaciones → Glass",
                UbicacionTexto.ubicacion(tel("EN_REPARACION", "REPARACIONES", List.of("GLASS"), 2)));
    }

    @Test void etiquetasDeEstado() {
        assertEquals("Recibido", UbicacionTexto.estado(tel("RECIBIDO", "ALMACEN", List.of(), 0)));
        assertEquals("En reparación", UbicacionTexto.estado(tel("EN_REPARACION", "REPARACIONES", List.of(), 0)));
        assertEquals("Histórico", UbicacionTexto.estado(tel(null, null, List.of(), 0)));
    }

    @Test void padreSinSubsNiRelojDeSolicitudPendiente() {
        assertEquals("Almacén", UbicacionTexto.padre(tel("RECIBIDO", "ALMACEN", List.of(), 0)));
        assertEquals("Reparaciones",
                UbicacionTexto.padre(tel("EN_REPARACION", "REPARACIONES", List.of("PULIDO", "NORMAL"), 2)));
        assertEquals(UbicacionTexto.FUERA, UbicacionTexto.padre(tel(null, null, List.of(), 0)));
    }
}
