package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre el parseo (extraerModelo) y la traducción (traducirModelo) de la serie 17,
 * incluido el modelo sin número "air" (iPhone Air), que necesita caso propio.
 */
class FormularioReparacionControllerTest {

    @Test
    void traducirModelo_serie17() {
        assertEquals("iPhone 17",         FormularioReparacionController.traducirModelo("17"));
        assertEquals("iPhone 17 Pro",     FormularioReparacionController.traducirModelo("17pro"));
        assertEquals("iPhone 17 Pro Max", FormularioReparacionController.traducirModelo("17promax"));
        assertEquals("iPhone Air",        FormularioReparacionController.traducirModelo("air"));
    }

    @Test
    void extraerModelo_serie17_distingueModelos() {
        assertEquals("17",       FormularioReparacionController.extraerModelo("chai17negro", "cha"));
        assertEquals("17pro",    FormularioReparacionController.extraerModelo("chai17pronegro", "cha"));
        assertEquals("17promax", FormularioReparacionController.extraerModelo("chai17promaxnegro", "cha"));
        assertEquals("air",      FormularioReparacionController.extraerModelo("chaiairnegro", "cha"));
    }

    @Test
    void extraerModelo_serie17_conSufijoEsim() {
        assertEquals("17pro", FormularioReparacionController.extraerModelo("chai17pronaturalesim", "cha"));
        assertEquals("air",   FormularioReparacionController.extraerModelo("chaiairnegroesim", "cha"));
    }

    @Test
    void extraerModelo_air_otrasPiezas() {
        assertEquals("air", FormularioReparacionController.extraerModelo("batiair", "bat"));
        assertEquals("air", FormularioReparacionController.extraerModelo("giairnegra", "g"));
    }
}
