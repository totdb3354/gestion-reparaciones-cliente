package com.reparaciones.models;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ClienteTest {

    @Test
    void getters_devuelvenValores() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 23, 12, 0);
        Cliente c = new Cliente(7, "WEB", true, t);
        assertEquals(7, c.getIdCli());
        assertEquals("WEB", c.getNombre());
        assertTrue(c.isActivo());
        assertEquals(t, c.getUpdatedAt());
    }

    @Test
    void setNombre_actualiza() {
        Cliente c = new Cliente(1, "OTRO", true, null);
        c.setNombre("Tienda Centro");
        assertEquals("Tienda Centro", c.getNombre());
    }

    @Test
    void toString_devuelveNombre() {
        assertEquals("WEB", new Cliente(1, "WEB", true, null).toString());
    }
}
