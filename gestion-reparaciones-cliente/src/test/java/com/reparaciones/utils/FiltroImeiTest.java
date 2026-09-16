package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.reparaciones.utils.FiltroImei.EstadoFiltro.*;

class FiltroImeiTest {

    @Test
    void canonicalizar_parte_un_blob_concatenado_cada_15() {
        assertEquals("352600000000071, 354700000000091, ",
                FiltroImei.canonicalizar("352600000000071354700000000091"));
    }

    @Test
    void canonicalizar_un_imei_completo_anade_separador() {
        assertEquals("352600000000071, ", FiltroImei.canonicalizar("352600000000071"));
    }

    @Test
    void canonicalizar_blob_con_resto_deja_el_resto_como_token() {
        assertEquals("352600000000071, 354700000000091, 12",
                FiltroImei.canonicalizar("35260000000007135470000000009112"));
    }

    @Test
    void canonicalizar_quita_no_digitos_y_normaliza_separadores() {
        assertEquals("352600000000071, ", FiltroImei.canonicalizar("352-600-000-000-071"));
    }

    @Test
    void canonicalizar_es_idempotente() {
        String[] entradas = {"", "3526", "352600000000071",
                "352600000000071354700000000091", "35260000000007135470000000009112"};
        for (String e : entradas) {
            String once = FiltroImei.canonicalizar(e);
            assertEquals(once, FiltroImei.canonicalizar(once), "no idempotente para: " + e);
        }
    }

    @Test
    void canonicalizar_vacio_o_null() {
        assertEquals("", FiltroImei.canonicalizar(""));
        assertEquals("", FiltroImei.canonicalizar(null));
    }

    @Test
    void imeisValidos_solo_los_de_15() {
        assertEquals(Set.of("352600000000071", "354700000000091"),
                FiltroImei.imeisValidos("352600000000071, 354700000000091, 12"));
        assertTrue(FiltroImei.imeisValidos("").isEmpty());
        assertTrue(FiltroImei.imeisValidos("12, 34").isEmpty());
    }

    @Test
    void estado_clasifica() {
        assertEquals(VACIO, FiltroImei.estado(""));
        assertEquals(VACIO, FiltroImei.estado("   "));
        assertEquals(INCOMPLETO, FiltroImei.estado("3526"));
        assertEquals(INCOMPLETO, FiltroImei.estado("352600000000071, 12"));
        assertEquals(VALIDO, FiltroImei.estado("352600000000071, "));
        assertEquals(VALIDO, FiltroImei.estado("352600000000071, 354700000000091, "));
    }
}
