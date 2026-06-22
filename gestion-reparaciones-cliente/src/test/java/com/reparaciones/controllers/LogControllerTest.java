package com.reparaciones.controllers;

import com.reparaciones.models.LogActividad;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del buscador de texto client-side de la vista de logs.
 * El filtrado por acción, técnico y rango de fechas se realiza ahora en el
 * servidor (ver LogDAO.getFiltered), por lo que aquí solo se prueba
 * {@link LogController#coincideTexto}.
 */
class LogControllerTest {

    private LogActividad log(LocalDateTime fecha, String usuario, String accion, String detalle) {
        return new LogActividad(1, fecha, usuario, accion, detalle);
    }

    private LogActividad logDeEjemplo() {
        return log(LocalDateTime.of(2026, 6, 10, 12, 30),
                "jperez", "CREAR_REPARACION", "ID_REP: 5, IMEI: 123456789012345, ID_TEC: 2");
    }

    @Test
    void coincideTexto_textoVacio_coincideSiempre() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), ""));
    }

    @Test
    void coincideTexto_textoNulo_coincideSiempre() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), null));
    }

    @Test
    void coincideTexto_textoSoloEspacios_coincideSiempre() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), "   "));
    }

    @Test
    void coincideTexto_coincideEnUsuario_ignoraMayusculas_devuelveTrue() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), "JPEREZ"));
    }

    @Test
    void coincideTexto_coincideEnAccion_devuelveTrue() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), "crear_reparacion"));
    }

    @Test
    void coincideTexto_coincideEnDetalle_porImei_devuelveTrue() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), "123456789012345"));
    }

    @Test
    void coincideTexto_coincideParcialEnDetalle_devuelveTrue() {
        assertTrue(LogController.coincideTexto(logDeEjemplo(), "ID_REP"));
    }

    @Test
    void coincideTexto_noCoincideEnNingunCampo_devuelveFalse() {
        assertFalse(LogController.coincideTexto(logDeEjemplo(), "no_existe"));
    }

    @Test
    void coincideTexto_camposNulos_noLanzaYNoCoincide() {
        LogActividad sinCampos = new LogActividad(1, LocalDateTime.now(), null, null, null);
        assertFalse(LogController.coincideTexto(sinCampos, "algo"));
    }
}
