package com.reparaciones.utils;

import com.reparaciones.models.VerificacionImei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clasifica las filas parseadas del xlsx cruzándolas con el mapeo de modelos y la
 * verificación de IMEIs del servidor (spec F2 §3): solo entran filas Status=0;
 * duplicados finales/históricos = re-entrada legítima, activos = conflicto.
 * Lógica pura sin UI ni red: la vista previa (ImportadorLoteDialog) solo pinta esto.
 */
public final class ClasificadorImportacion {

    private ClasificadorImportacion() {}

    public enum Destino { NUEVO, REENTRADA, CONFLICTO, STATUS_DISTINTO, INVALIDO, DUPLICADO_FICHERO, MODELO_SIN_MAPEAR }

    public record FilaClasificada(LoteXlsxParser.Fila fila, Destino destino, String modeloInterno, String detalle) {}

    public record LotePlan(String batchNumber, String proveedorNombre, List<FilaClasificada> filas) {}

    public record Plan(List<LotePlan> lotes, List<FilaClasificada> excluidas) {}

    public static Plan clasificar(List<LoteXlsxParser.Fila> filas,
                                  Map<String, String> mapeoModelos,
                                  Map<String, VerificacionImei> existentes) {
        Map<String, LotePlan> lotes = new LinkedHashMap<>();
        List<FilaClasificada> excluidas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (LoteXlsxParser.Fila f : filas) {
            String imei = f.imei() == null ? "" : f.imei().replaceAll("\\D", "");
            if (f.status() != null && f.status() != 0) {
                excluidas.add(new FilaClasificada(f, Destino.STATUS_DISTINTO, null,
                        "Status " + f.status() + " (solo entran filas con Status 0)"));
                continue;
            }
            if (imei.length() != 15) {
                excluidas.add(new FilaClasificada(f, Destino.INVALIDO, null,
                        "IMEI inválido: \"" + (f.imei() == null ? "" : f.imei()) + "\""));
                continue;
            }
            if (f.batchNumber() == null || f.batchNumber().isBlank()) {
                excluidas.add(new FilaClasificada(f, Destino.INVALIDO, null, "Fila sin Batch Number"));
                continue;
            }
            if (!vistos.add(imei)) {
                excluidas.add(new FilaClasificada(f, Destino.DUPLICADO_FICHERO, null,
                        "IMEI repetido en el fichero (solo entra la primera aparición)"));
                continue;
            }
            String interno = mapeoModelos.get(f.modeloTexto());
            if (interno == null) {
                excluidas.add(new FilaClasificada(f, Destino.MODELO_SIN_MAPEAR, null,
                        "Modelo \"" + f.modeloTexto() + "\" sin correspondencia en el catálogo"));
                continue;
            }
            VerificacionImei v = existentes.get(imei);
            Destino destino = Destino.NUEVO;
            String detalle = null;
            if (v != null && v.esActivo()) {
                excluidas.add(new FilaClasificada(f, Destino.CONFLICTO, interno,
                        "Ya está activo en el sistema (estado " +
                        (v.getEstado() == null ? "histórico con trabajo abierto" : v.getEstado()) + ")"));
                continue;
            }
            if (v != null) { destino = Destino.REENTRADA; detalle = "Re-entrada: conserva su historial"; }
            lotes.computeIfAbsent(f.batchNumber(),
                    b -> new LotePlan(b, f.proveedorNombre(), new ArrayList<>()))
                 .filas().add(new FilaClasificada(f, destino, interno, detalle));
        }
        return new Plan(new ArrayList<>(lotes.values()), excluidas);
    }
}
