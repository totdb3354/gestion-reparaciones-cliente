package com.reparaciones.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TecnicoTest {

    @Test
    void getIdTec_devuelveValorCorrecto() {
        assertEquals(3, new Tecnico(3, "Daniel García", true).getIdTec());
    }

    @Test
    void getNombre_devuelveValorCorrecto() {
        assertEquals("Daniel García", new Tecnico(3, "Daniel García", true).getNombre());
    }

    @Test
    void setIdTec_actualizaCorrectamente() {
        Tecnico t = new Tecnico(1, "Daniel García", true);
        t.setIdTec(5);
        assertEquals(5, t.getIdTec());
    }

    @Test
    void setNombre_actualizaCorrectamente() {
        Tecnico t = new Tecnico(1, "Daniel García", true);
        t.setNombre("Angelo López");
        assertEquals("Angelo López", t.getNombre());
    }

    @Test
    void toString_devuelveNombre() {
        assertEquals("Daniel García", new Tecnico(1, "Daniel García", true).toString());
    }

    @Test
    void esGlass_porDefectoFalse() {
        assertFalse(new Tecnico(1, "Daniel García", true).isEsGlass());
        assertFalse(new Tecnico(1, "Daniel García", true, true).isEsGlass());
    }

    @Test
    void esGlass_constructorCompleto() {
        assertTrue(new Tecnico(1, "Daniel García", true, true, true).isEsGlass());
        assertFalse(new Tecnico(1, "Daniel García", true, true, false).isEsGlass());
    }

    @Test
    void esGlass_jsonSinElCampoCuentaComoNoHabilitado() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Tecnico viejo = gson.fromJson("{\"idTec\":1,\"nombre\":\"Javi\",\"activo\":true}", Tecnico.class);
        Tecnico nuevo = gson.fromJson("{\"idTec\":1,\"nombre\":\"Javi\",\"activo\":true,\"esGlass\":true}", Tecnico.class);
        assertFalse(viejo.isEsGlass());
        assertTrue(nuevo.isEsGlass());
    }
}
