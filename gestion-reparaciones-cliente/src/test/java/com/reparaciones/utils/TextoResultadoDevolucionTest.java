package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoDevolucionTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ ALMACÉN (devolución del envío 9)", TextoResultadoDevolucion.texto("DEVUELTO", 9));
        assertEquals("→ ALMACÉN (devolución, sin envío registrado)", TextoResultadoDevolucion.texto("DEVUELTO", null));
        assertEquals("rechazado: no está enviado", TextoResultadoDevolucion.texto("NO_ENVIADO", null));
        assertEquals("no existe en el sistema", TextoResultadoDevolucion.texto("NO_EXISTE", null));
        assertEquals("resultado desconocido", TextoResultadoDevolucion.texto("???", null));
    }

    @Test void soloDevueltoCuentaComoExito() {
        assertTrue(TextoResultadoDevolucion.esDevuelto("DEVUELTO"));
        assertFalse(TextoResultadoDevolucion.esDevuelto("NO_ENVIADO"));
    }
}
