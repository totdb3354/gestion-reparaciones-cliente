package com.reparaciones.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConexionEstadoTest {

    @BeforeEach
    void reset() {
        ConexionEstado.reportarExito();
        ConexionEstado.enRefresco(false);
    }

    @Test
    void arranca_conectado() {
        ConexionEstado.reportarExito();
        assertFalse(ConexionEstado.isDesconectado());
    }

    @Test
    void reportar_fallo_marca_desconectado_y_exito_reconecta() {
        ConexionEstado.reportarFallo();
        assertTrue(ConexionEstado.isDesconectado());
        ConexionEstado.reportarExito();
        assertFalse(ConexionEstado.isDesconectado());
    }

    @Test
    void delay_es_5_desconectado_y_60_conectado() {
        ConexionEstado.reportarFallo();
        assertEquals(5, ConexionEstado.delaySegundos());
        ConexionEstado.reportarExito();
        assertEquals(60, ConexionEstado.delaySegundos());
    }

    @Test
    void flag_enRefresco_por_defecto_false() {
        ConexionEstado.enRefresco(false);
        assertFalse(ConexionEstado.enRefresco());
        ConexionEstado.enRefresco(true);
        assertTrue(ConexionEstado.enRefresco());
        ConexionEstado.enRefresco(false);
    }
}
