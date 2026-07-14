package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColorMapperTest {

    @Test void normaliza() {
        assertEquals("productred", ColorMapper.normalizar("(PRODUCT)RED"));
        assertEquals("naturaltitanium", ColorMapper.normalizar("Natural Titanium"));
    }

    @Test void matchDirectoContraPaletaDelModelo() {
        assertEquals("White", ColorMapper.resolver("white", "12", Map.of()));
        assertEquals("(PRODUCT)RED", ColorMapper.resolver("Product Red", "12", Map.of("productred", "(PRODUCT)RED")));
    }

    @Test void colorRealPeroFueraDePaletaNoResuelve() {
        // "Blue" es oficial en el 12, pero el 15 Pro no lo tiene: debe quedar sin mapear
        assertNull(ColorMapper.resolver("Blue", "15pro", Map.of()));
    }

    @Test void equivalenciaSoloValeSiCaeEnLaPaleta() {
        Map<String, String> eq = Map.of("azul", "Blue Titanium");
        assertEquals("Blue Titanium", ColorMapper.resolver("azul", "15pro", eq));
        assertNull(ColorMapper.resolver("azul", "12", eq)); // Blue Titanium no existe en el 12
    }

    @Test void sinModeloUsaLaUnionCompleta() {
        assertEquals("Sierra Blue", ColorMapper.resolver("sierra blue", null, Map.of()));
    }

    @Test void nuloYVacioDevuelvenNull() {
        assertNull(ColorMapper.resolver(null, "12", Map.of()));
        assertNull(ColorMapper.resolver("  ", "12", Map.of()));
    }
}
