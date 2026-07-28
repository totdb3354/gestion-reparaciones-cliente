package com.reparaciones.utils;

import com.reparaciones.models.MovimientoTelefono;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatoMovimientoTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private MovimientoTelefono mov(String origen, String destino, String usuario, String motivo, String ref) {
        MovimientoTelefono m = new MovimientoTelefono();
        m.setUbicacionOrigen(origen);
        m.setUbicacionDestino(destino);
        m.setFecha(LocalDateTime.of(2026, 7, 27, 10, 0));
        m.setUsuario(usuario);
        m.setMotivo(motivo);
        m.setReferencia(ref);
        return m;
    }

    @Test void lineaCompleta() {
        assertEquals(FechaUtils.formatear(LocalDateTime.of(2026, 7, 27, 10, 0), FMT)
                        + " · ENVIADO → ALMACEN · ana · pantalla amarilla (ENVIO 9)",
                FormatoMovimiento.linea(mov("ENVIADO", "ALMACEN", "ana", "pantalla amarilla", "ENVIO 9"), FMT));
    }

    @Test void origenNullYSinExtras() {
        assertEquals(FechaUtils.formatear(LocalDateTime.of(2026, 7, 27, 10, 0), FMT)
                        + " · — → ALMACEN · ana",
                FormatoMovimiento.linea(mov(null, "ALMACEN", "ana", null, null), FMT));
    }
}
