package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModeloMapperTest {

    @Test void normalizaQuitandoPrefijoIphoneYSimbolos() {
        assertEquals("12mini", ModeloMapper.normalizar("iPhone 12 mini"));
        assertEquals("se2020", ModeloMapper.normalizar(" iPhone SE 2020 "));
        assertEquals("xsmax", ModeloMapper.normalizar("iPhone XS Max"));
        assertEquals("16esim", ModeloMapper.normalizar("iPhone 16 eSIM"));
    }

    @Test void mapeaDirectoContraElCatalogo() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 12 mini", "iPhone 12"), Map.of());
        assertEquals("12mini", m.get("iPhone 12 mini"));
        assertEquals("12", m.get("iPhone 12"));
    }

    @Test void sinCorrespondenciaDevuelveNull() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 16 eSIM", "Galaxy S24"), Map.of());
        assertNull(m.get("iPhone 16 eSIM"));
        assertNull(m.get("Galaxy S24"));
    }

    @Test void lasEquivalenciasGuardadasResuelvenLoQueNoCasa() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 16 eSIM"), Map.of("16esim", "16"));
        assertEquals("16", m.get("iPhone 16 eSIM"));
    }

    @Test void laEquivalenciaGanaAlMatchDirecto() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 12"), Map.of("12", "12pro"));
        assertEquals("12pro", m.get("iPhone 12"));
    }
}
