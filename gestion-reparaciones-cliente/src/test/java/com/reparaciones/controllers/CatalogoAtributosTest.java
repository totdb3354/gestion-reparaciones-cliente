package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoAtributosTest {

    @Test void paletaDelModeloConColoresOficiales() {
        assertTrue(CatalogoAtributos.coloresDe("15promax").contains("Natural Titanium"));
        assertTrue(CatalogoAtributos.coloresDe("12").contains("(PRODUCT)RED"));
        assertFalse(CatalogoAtributos.coloresDe("12").contains("Natural Titanium"));
    }

    @Test void capacidadesDelModelo() {
        assertEquals(java.util.List.of(256, 512, 1024), CatalogoAtributos.capacidadesDe("15promax"));
        assertTrue(CatalogoAtributos.capacidadesDe("12").contains(64));
        assertFalse(CatalogoAtributos.capacidadesDe("12").contains(1024));
    }

    @Test void modeloNuloODesconocidoDevuelveVacio() {
        assertTrue(CatalogoAtributos.coloresDe(null).isEmpty());
        assertTrue(CatalogoAtributos.capacidadesDe("nokia3310").isEmpty());
    }

    @Test void todosLosModelosDelCatalogoTienenPaleta() {
        for (String m : FormularioReparacionController.MODELOS_ORDENADOS) {
            assertFalse(CatalogoAtributos.coloresDe(m).isEmpty(), "sin colores: " + m);
            assertFalse(CatalogoAtributos.capacidadesDe(m).isEmpty(), "sin capacidades: " + m);
        }
    }

    @Test void unionesYPertenencia() {
        assertTrue(CatalogoAtributos.COLORES_TODOS.contains("Midnight"));
        assertTrue(CatalogoAtributos.esColorOficial("Sierra Blue"));
        assertFalse(CatalogoAtributos.esColorOficial("Blanco"));
    }
}
