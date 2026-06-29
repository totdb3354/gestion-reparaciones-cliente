package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.reparaciones.utils.ImeiUtils.TipoPegado.*;

class ImeiUtilsTest {

    @Test
    void incompleto_si_menos_de_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("12345");
        assertEquals(INCOMPLETO, r.tipo());
        assertTrue(r.imeis().isEmpty());
    }

    @Test
    void unico_si_exactamente_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812");
        assertEquals(UNICO, r.tipo());
        assertEquals(List.of("352680941087812"), r.imeis());
    }

    @Test
    void lote_si_multiplo_de_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812354739185728537");
        assertEquals(LOTE, r.tipo());
        assertEquals(List.of("352680941087812", "354739185728537"), r.imeis());
    }

    @Test
    void corrupto_si_mayor_de_15_y_no_multiplo() {
        assertEquals(CORRUPTO, ImeiUtils.parsearPegadoImeis("3526809410878123").tipo());   // 16
        assertEquals(CORRUPTO, ImeiUtils.parsearPegadoImeis("3".repeat(31)).tipo());        // 31
        assertTrue(ImeiUtils.parsearPegadoImeis("3".repeat(16)).imeis().isEmpty());
    }

    @Test
    void quita_separadores_y_no_digitos() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812\n354739185728537");
        assertEquals(LOTE, r.tipo());
        assertEquals(2, r.imeis().size());
    }

    @Test
    void vacio_o_null_es_incompleto() {
        assertEquals(INCOMPLETO, ImeiUtils.parsearPegadoImeis("").tipo());
        assertEquals(INCOMPLETO, ImeiUtils.parsearPegadoImeis(null).tipo());
    }
}
