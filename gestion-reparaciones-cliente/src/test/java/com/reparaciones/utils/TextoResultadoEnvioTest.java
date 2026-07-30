package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoEnvioTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ ENVIADO", TextoResultadoEnvio.texto("ENVIADO", null));
        assertEquals("rechazado: está En revisión — solo se envían teléfonos OK",
                TextoResultadoEnvio.texto("NO_OK", "EN_REVISION"));
        assertEquals("rechazado: está Bloqueado — solo se envían teléfonos OK",
                TextoResultadoEnvio.texto("NO_OK", "BLOQUEADO"));
        assertEquals("rechazado: histórico — dar de alta en un lote", TextoResultadoEnvio.texto("HISTORICO", null));
        assertEquals("no existe en el sistema", TextoResultadoEnvio.texto("NO_EXISTE", null));
        assertEquals("resultado desconocido", TextoResultadoEnvio.texto("???", null));
    }

    @Test void soloEnviadoCuentaComoExito() {
        assertTrue(TextoResultadoEnvio.esEnviado("ENVIADO"));
        assertFalse(TextoResultadoEnvio.esEnviado("NO_OK"));
        assertFalse(TextoResultadoEnvio.esEnviado(null));
    }
}
