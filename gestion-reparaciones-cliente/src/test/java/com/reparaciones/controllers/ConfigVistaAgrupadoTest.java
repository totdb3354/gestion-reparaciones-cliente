package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.controllers.ConfigVistaAgrupado.Vista.INVENTARIO;
import static com.reparaciones.controllers.ConfigVistaAgrupado.Vista.TALLER;
import static org.junit.jupiter.api.Assertions.*;

class ConfigVistaAgrupadoTest {

    @Test void inventarioConservaLaVistaActualCompleta() {
        assertEquals(List.of("imei", "modelo", "storage", "color", "grado", "ultimaActividad",
                        "trabajos", "estado", "ubicacion", "lote", "observacionTelefono", "cliente", "revision"),
                ConfigVistaAgrupado.columnasMaestro(INVENTARIO));
        assertTrue(ConfigVistaAgrupado.filtrosVisibles(INVENTARIO).containsAll(
                java.util.Set.of("imei", "tecnico", "cliente", "estado", "ubicacion", "lote", "modelo", "fechas", "incidencias")));
        assertTrue(ConfigVistaAgrupado.botonesImportacion(INVENTARIO));
        assertFalse(ConfigVistaAgrupado.soloConTrabajos(INVENTARIO));
        assertEquals(19, ConfigVistaAgrupado.cabeceraCsvMaestro(INVENTARIO).size());
    }

    @Test void tallerEsElHistorialPreF2aSinOkConEsim() {
        assertEquals(List.of("imei", "modelo", "ultimaActividad", "trabajos", "observacionTelefono", "cliente"),
                ConfigVistaAgrupado.columnasMaestro(TALLER));
        assertEquals(java.util.Set.of("imei", "tecnico", "cliente", "fechas", "incidencias"),
                ConfigVistaAgrupado.filtrosVisibles(TALLER));
        assertFalse(ConfigVistaAgrupado.botonesImportacion(TALLER));
        assertTrue(ConfigVistaAgrupado.soloConTrabajos(TALLER));
    }

    @Test void csvTallerEsEspejoSinRevision() {
        assertEquals(List.of("IMEI", "Modelo", "Última actividad", "Reparaciones", "Glass", "Pulidos",
                        "Abiertos", "Inc. abiertas", "Observación", "Cliente"),
                ConfigVistaAgrupado.cabeceraCsvMaestro(TALLER));
        assertFalse(ConfigVistaAgrupado.columnasMaestro(TALLER).contains("revision"));
    }
}
