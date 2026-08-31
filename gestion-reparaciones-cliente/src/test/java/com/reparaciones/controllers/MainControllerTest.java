package com.reparaciones.controllers;

import com.reparaciones.models.Componente;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Tests del predicado puro de alertas de stock (campana de notificaciones). */
class MainControllerTest {

    private static Componente comp(int stock, int stockMinimo, boolean activo, Integer idComMaster) {
        Componente c = new Componente(1, "bati6s", LocalDateTime.now(),
                stock, stockMinimo, activo, LocalDateTime.now());
        c.setIdComMaster(idComMaster);
        return c;
    }

    @Test
    void master_activo_sin_stock_es_alerta() {
        assertTrue(MainController.esAlertaStock(comp(0, 0, true, null)));
    }

    @Test
    void master_activo_bajo_minimo_es_alerta() {
        assertTrue(MainController.esAlertaStock(comp(2, 5, true, null)));
    }

    @Test
    void master_activo_por_encima_del_minimo_no_es_alerta() {
        assertFalse(MainController.esAlertaStock(comp(6, 5, true, null)));
    }

    @Test
    void master_desactivado_sin_stock_no_es_alerta() {
        assertFalse(MainController.esAlertaStock(comp(0, 0, false, null)),
                "los desactivados se excluyen de conteos y alertas");
    }

    @Test
    void slave_activo_sin_stock_no_es_alerta() {
        assertFalse(MainController.esAlertaStock(comp(0, 0, true, 7)),
                "las alertas se calculan solo sobre filas master");
    }
}
