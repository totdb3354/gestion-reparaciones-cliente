package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PiezasTest {
    @Test
    void categoria_derivaDelPrefijoDelSku() {
        assertEquals("Glass",    Piezas.categoria("gi12negra"));
        assertEquals("Pantalla", Piezas.categoria("lcdi12negraic"));
        assertEquals("Marco",    Piezas.categoria("mci12negra"));
        assertEquals("Batería",  Piezas.categoria("bati12"));
        assertEquals("Cámara",   Piezas.categoria("cami12"));
        assertEquals("Chasis",   Piezas.categoria("chai12negro"));
        assertEquals("Otros",    Piezas.categoria("otroi8"));
    }

    @Test
    void categoria_vaciaSiNullODesconocido() {
        assertEquals("", Piezas.categoria(null));
        assertEquals("", Piezas.categoria("xyz123"));
    }
}
