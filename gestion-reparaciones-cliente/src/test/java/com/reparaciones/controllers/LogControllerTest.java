package com.reparaciones.controllers;

import com.reparaciones.models.LogActividad;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LogControllerTest {

    private LogActividad log(LocalDateTime fecha, String usuario, String accion, String detalle) {
        return new LogActividad(1, fecha, usuario, accion, detalle);
    }

    private LogActividad logDeEjemplo() {
        return log(LocalDateTime.of(2026, 6, 10, 12, 30),
                "jperez", "CREAR_REPARACION", "ID_REP: 5, IMEI: 123456789012345, ID_TEC: 2");
    }

    // --- texto ---

    @Test
    void coincideFiltro_textoVacio_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "", null, null, null));
    }

    @Test
    void coincideFiltro_textoNulo_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnUsuario_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "JPEREZ", null, null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnAccion_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "crear_reparacion", null, null, null));
    }

    @Test
    void coincideFiltro_textoCoincideEnDetalle_porImei_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), "123456789012345", null, null, null));
    }

    @Test
    void coincideFiltro_textoNoCoincideEnNingunCampo_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "no_existe", null, null, null));
    }

    // --- fecha ---

    @Test
    void coincideFiltro_fechaDesdeAnteriorALaDelLog_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, LocalDate.of(2026, 6, 1), null, null));
    }

    @Test
    void coincideFiltro_fechaDesdePosteriorALaDelLog_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), null, LocalDate.of(2026, 6, 11), null, null));
    }

    @Test
    void coincideFiltro_fechaHastaPosteriorALaDelLog_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, LocalDate.of(2026, 6, 30), null));
    }

    @Test
    void coincideFiltro_fechaHastaAnteriorALaDelLog_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), null, null, LocalDate.of(2026, 6, 9), null));
    }

    @Test
    void coincideFiltro_fechaMismoDiaDesdeYHasta_devuelveTrue() {
        LocalDate dia = LocalDate.of(2026, 6, 10);
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, dia, dia, null));
    }

    // --- combinación texto + fecha ---

    @Test
    void coincideFiltro_textoCoincideYFechaFueraDeRango_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "jperez",
                null, LocalDate.of(2026, 6, 9), null));
    }

    @Test
    void coincideFiltro_textoNoCoincideYFechaDentroDeRango_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "no_existe",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null));
    }

    // --- técnico ---

    @Test
    void coincideFiltro_tecnicoNulo_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null, null));
    }

    @Test
    void coincideFiltro_tecnicoVacio_coincideSiempre() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null, ""));
    }

    @Test
    void coincideFiltro_tecnicoCoincideExacto_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null, "jperez"));
    }

    @Test
    void coincideFiltro_tecnicoCoincideIgnorandoMayusculas_devuelveTrue() {
        assertTrue(LogController.coincideFiltro(logDeEjemplo(), null, null, null, "JPEREZ"));
    }

    @Test
    void coincideFiltro_tecnicoDistinto_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), null, null, null, "otro_usuario"));
    }

    @Test
    void coincideFiltro_tecnicoCoincideTextoNoCoincide_devuelveFalse() {
        assertFalse(LogController.coincideFiltro(logDeEjemplo(), "no_existe",
                null, null, "jperez"));
    }
}
