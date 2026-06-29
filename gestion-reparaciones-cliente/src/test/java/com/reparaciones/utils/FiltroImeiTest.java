package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.reparaciones.utils.FiltroImei.EstadoFiltro.*;

class FiltroImeiTest {

    @Test
    void canonicalizar_parte_un_blob_concatenado_cada_15() {
        assertEquals("352680941087812, 354739185728537, ",
                FiltroImei.canonicalizar("352680941087812354739185728537"));
    }

    @Test
    void canonicalizar_un_imei_completo_anade_separador() {
        assertEquals("352680941087812, ", FiltroImei.canonicalizar("352680941087812"));
    }

    @Test
    void canonicalizar_blob_con_resto_deja_el_resto_como_token() {
        assertEquals("352680941087812, 354739185728537, 12",
                FiltroImei.canonicalizar("35268094108781235473918572853712"));
    }

    @Test
    void canonicalizar_quita_no_digitos_y_normaliza_separadores() {
        assertEquals("352680941087812, ", FiltroImei.canonicalizar("352-680-941-087-812"));
    }

    @Test
    void canonicalizar_es_idempotente() {
        String[] entradas = {"", "3526", "352680941087812",
                "352680941087812354739185728537", "35268094108781235473918572853712"};
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
        assertEquals(Set.of("352680941087812", "354739185728537"),
                FiltroImei.imeisValidos("352680941087812, 354739185728537, 12"));
        assertTrue(FiltroImei.imeisValidos("").isEmpty());
        assertTrue(FiltroImei.imeisValidos("12, 34").isEmpty());
    }

    @Test
    void estado_clasifica() {
        assertEquals(VACIO, FiltroImei.estado(""));
        assertEquals(VACIO, FiltroImei.estado("   "));
        assertEquals(INCOMPLETO, FiltroImei.estado("3526"));
        assertEquals(INCOMPLETO, FiltroImei.estado("352680941087812, 12"));
        assertEquals(VALIDO, FiltroImei.estado("352680941087812, "));
        assertEquals(VALIDO, FiltroImei.estado("352680941087812, 354739185728537, "));
    }
}
