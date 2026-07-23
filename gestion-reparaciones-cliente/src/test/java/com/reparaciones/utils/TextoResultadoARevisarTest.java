package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoARevisarTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ EN REVISIÓN", TextoResultadoARevisar.texto("PASADO"));
        assertEquals("→ EN REVISIÓN (estaba OK)", TextoResultadoARevisar.texto("PASADO_ESTABA_OK"));
        assertEquals("ya estaba en revisión", TextoResultadoARevisar.texto("YA_ESTABA"));
        assertEquals("rechazado: en reparación (volverá solo al terminar)", TextoResultadoARevisar.texto("EN_REPARACION"));
        assertEquals("rechazado: bloqueado — usar Desbloquear", TextoResultadoARevisar.texto("BLOQUEADO"));
        assertEquals("rechazado: fuera del circuito (enviado/desguace)", TextoResultadoARevisar.texto("FUERA"));
        assertEquals("rechazado: histórico — dar de alta en un lote", TextoResultadoARevisar.texto("HISTORICO"));
        assertEquals("no existe en el sistema", TextoResultadoARevisar.texto("NO_EXISTE"));
        assertEquals("resultado desconocido", TextoResultadoARevisar.texto("???"));
    }

    @Test void soloLosDosPasadosCuentanComoCambio() {
        assertTrue(TextoResultadoARevisar.esPasado("PASADO"));
        assertTrue(TextoResultadoARevisar.esPasado("PASADO_ESTABA_OK"));
        assertFalse(TextoResultadoARevisar.esPasado("YA_ESTABA"));
        assertFalse(TextoResultadoARevisar.esPasado("NO_EXISTE"));
    }
}
