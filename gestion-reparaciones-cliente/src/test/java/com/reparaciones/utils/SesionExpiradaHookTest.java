package com.reparaciones.utils;

import com.reparaciones.Sesion;
import com.reparaciones.models.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SesionExpiradaHookTest {

    private AtomicInteger veces;

    @BeforeEach
    void setUp() {
        veces = new AtomicInteger(0);
        ApiClient.setSesionExpiradaHandler(veces::incrementAndGet);
        Sesion.cerrar();
        ApiClient.setToken("tok"); // rearma logoutEnCurso = false
    }

    @AfterEach
    void tearDown() {
        ApiClient.setSesionExpiradaHandler(null);
        Sesion.cerrar();
    }

    @Test
    void sin_sesion_activa_no_dispara() {            // contexto login: 401 = credenciales malas
        Sesion.cerrar();
        ApiClient.dispararSesionExpirada();
        assertEquals(0, veces.get());
    }

    @Test
    void con_sesion_activa_dispara_una_vez_y_deduplica() {
        Sesion.iniciar(new Usuario(1, "ana", "SUPERTECNICO", null));
        ApiClient.dispararSesionExpirada();
        ApiClient.dispararSesionExpirada();          // segundo 401 concurrente
        assertEquals(1, veces.get());
        assertTrue(ApiClient.isLogoutEnCurso());
    }

    @Test
    void setToken_rearma_tras_relogin() {
        Sesion.iniciar(new Usuario(1, "ana", "SUPERTECNICO", null));
        ApiClient.dispararSesionExpirada();
        ApiClient.setToken("nuevo");                 // relogin
        assertFalse(ApiClient.isLogoutEnCurso());
    }
}
