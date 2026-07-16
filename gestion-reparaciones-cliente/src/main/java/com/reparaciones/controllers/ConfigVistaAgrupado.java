package com.reparaciones.controllers;

import java.util.List;
import java.util.Set;

/**
 * Configuración declarativa de las dos vistas del Agrupado por IMEI
 * (spec reubicación 2026-07-16): TALLER = historial por IMEI (pre-F2a sin
 * columna OK, con sufijo eSIM); INVENTARIO = vista completa actual.
 * Puro y testeable: el controller solo consume estas listas.
 */
public final class ConfigVistaAgrupado {

    public enum Vista { TALLER, INVENTARIO }

    private ConfigVistaAgrupado() {}

    private static final List<String> COLS_INVENTARIO = List.of(
            "imei", "modelo", "storage", "color", "grado", "ultimaActividad",
            "trabajos", "estado", "ubicacion", "lote", "observacionTelefono", "cliente", "revision");

    private static final List<String> COLS_TALLER = List.of(
            "imei", "modelo", "ultimaActividad", "trabajos", "observacionTelefono", "cliente");

    private static final List<String> CSV_INVENTARIO = List.of(
            "IMEI", "Modelo", "Storage", "Color", "Grado propio", "Grado proveedor", "Estado",
            "Ubicación", "Lote", "Proveedor", "Última actividad", "Reparaciones", "Glass",
            "Pulidos", "Abiertos", "Inc. abiertas", "Observación", "Cliente", "Revisión logística");

    private static final List<String> CSV_TALLER = List.of(
            "IMEI", "Modelo", "Última actividad", "Reparaciones", "Glass", "Pulidos",
            "Abiertos", "Inc. abiertas", "Observación", "Cliente");

    public static List<String> columnasMaestro(Vista v) {
        return v == Vista.TALLER ? COLS_TALLER : COLS_INVENTARIO;
    }

    public static Set<String> filtrosVisibles(Vista v) {
        return v == Vista.TALLER
                ? Set.of("imei", "tecnico", "cliente", "fechas", "incidencias")
                : Set.of("imei", "tecnico", "cliente", "estado", "ubicacion", "lote", "modelo", "fechas", "incidencias");
    }

    public static boolean botonesImportacion(Vista v) { return v == Vista.INVENTARIO; }

    public static boolean soloConTrabajos(Vista v) { return v == Vista.TALLER; }

    public static List<String> cabeceraCsvMaestro(Vista v) {
        return v == Vista.TALLER ? CSV_TALLER : CSV_INVENTARIO;
    }
}
