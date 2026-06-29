package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientClasificarTest {

    @Test
    void status_500_es_ConexionException() {
        assertInstanceOf(ConexionException.class, ApiClient.clasificar(500, "x"));
    }

    @Test
    void status_503_es_ConexionException() {
        assertInstanceOf(ConexionException.class, ApiClient.clasificar(503, "x"));
    }

    @Test
    void status_401_es_SesionExpiradaException_con_mensaje_preservado() {
        SQLException e = ApiClient.clasificar(401, "x");
        assertInstanceOf(SesionExpiradaException.class, e);
        assertTrue(e.getMessage().startsWith("Sesión expirada"),
                "el mensaje debe empezar por 'Sesión expirada' (UsuarioDAO depende de ello)");
    }

    @Test
    void status_409_es_StaleDataException() {
        assertInstanceOf(StaleDataException.class, ApiClient.clasificar(409, "conflicto"));
    }

    @Test
    void status_403_es_SQLException_pero_no_marcador() {
        SQLException e = ApiClient.clasificar(403, "x");
        assertFalse(e instanceof ConexionException);
        assertFalse(e instanceof SesionExpiradaException);
    }

    @Test
    void status_404_y_422_son_SQLException_simple() {
        assertFalse(ApiClient.clasificar(404, "x") instanceof ConexionException);
        assertFalse(ApiClient.clasificar(422, "x") instanceof ConexionException);
    }
}
