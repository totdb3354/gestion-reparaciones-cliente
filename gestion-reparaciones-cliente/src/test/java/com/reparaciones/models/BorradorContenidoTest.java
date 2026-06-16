package com.reparaciones.models;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BorradorContenidoTest {

    private final Gson gson = new Gson();

    @Test
    void fila_guardada_sobrevive_round_trip_json() {
        BorradorContenido b = new BorradorContenido();
        BorradorContenido.Fila f = new BorradorContenido.Fila();
        f.prefijo = "lcd";
        f.idCom = 12;
        f.guardada = true;
        f.idRepGenerado = "R20260616_1";
        f.fechaGuardado = "16/06 14:32";
        b.filas.add(f);

        String json = gson.toJson(b);
        BorradorContenido b2 = gson.fromJson(json, BorradorContenido.class);

        assertEquals(1, b2.filas.size());
        BorradorContenido.Fila f2 = b2.filas.get(0);
        assertTrue(f2.guardada);
        assertEquals("R20260616_1", f2.idRepGenerado);
        assertEquals("16/06 14:32", f2.fechaGuardado);
    }

    @Test
    void otra_accion_guardada_sobrevive_round_trip_json() {
        BorradorContenido b = new BorradorContenido();
        BorradorContenido.OtraAccion a = new BorradorContenido.OtraAccion();
        a.descripcion = "Limpiar cámara";
        a.guardada = true;
        a.idRepGenerado = "R20260616_2";
        a.fechaGuardado = "16/06 15:00";
        b.otros.add(a);

        String json = gson.toJson(b);
        BorradorContenido b2 = gson.fromJson(json, BorradorContenido.class);

        assertEquals(1, b2.otros.size());
        BorradorContenido.OtraAccion a2 = b2.otros.get(0);
        assertTrue(a2.guardada);
        assertEquals("R20260616_2", a2.idRepGenerado);
        assertEquals("Limpiar cámara", a2.descripcion);
    }

    @Test
    void otros_guardados_como_strings_formato_antiguo_lanza_json_syntax() {
        // borrador antiguo: otros era List<String>, ahora List<OtraAccion>
        // Gson no puede deserializar String como OtraAccion → lanza JsonSyntaxException
        // El código cliente debe protegerse con try/catch al cargar borradores viejos
        String jsonAntiguo = "{\"modelo\":\"12\",\"filas\":[],\"otros\":[\"Limpiar cámara\"]}";
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> gson.fromJson(jsonAntiguo, BorradorContenido.class));
    }
}
