package com.reparaciones.dao;

import com.google.gson.JsonObject;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.ApiClient;
import com.reparaciones.utils.StaleDataException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PulidoDAO {

    // ── Lectura ───────────────────────────────────────────────────────────────

    public List<ReparacionResumen> getAsignacionesPulido() throws SQLException {
        return ApiClient.getList("/api/pulidos/asignaciones", ReparacionResumen.class);
    }

    public List<ReparacionResumen> getAsignacionesPulidoPorTecnico(int idTec) throws SQLException {
        return ApiClient.getList("/api/pulidos/asignaciones?tecnico=" + idTec, ReparacionResumen.class);
    }

    public List<ReparacionResumen> getHistorialPulido() throws SQLException {
        return ApiClient.getList("/api/pulidos/historial", ReparacionResumen.class);
    }

    public List<ReparacionResumen> getHistorialPulidoPorTecnico(int idTec) throws SQLException {
        return ApiClient.getList("/api/pulidos/historial?tecnico=" + idTec, ReparacionResumen.class);
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    public String insertarAsignacionPulido(String imei, int idTec, String comentario) throws SQLException {
        var body = new java.util.HashMap<String, Object>();
        body.put("imei", imei);
        body.put("idTec", idTec);
        if (comentario != null && !comentario.isBlank()) body.put("comentario", comentario);
        JsonObject resp = ApiClient.post("/api/pulidos/asignaciones", body, JsonObject.class);
        return resp != null ? resp.get("value").getAsString() : null;
    }

    public void completarPulidoLote(List<String> ids) throws SQLException {
        ApiClient.post("/api/pulidos/asignaciones/completar-lote", Map.of("ids", ids));
    }

    public void actualizarAsignacionPulido(String idAP, int idTec, String comentario,
                                            LocalDateTime updatedAt)
            throws SQLException, StaleDataException {
        ApiClient.patch("/api/pulidos/asignaciones/" + idAP,
                Map.of("idTec", idTec,
                       "comentario", comentario != null ? comentario : "",
                       "updatedAt", updatedAt));
    }

    public void eliminarAsignacionPulido(String idAP) throws SQLException {
        ApiClient.delete("/api/pulidos/asignaciones/" + idAP);
    }

    public void eliminarPulido(String idP) throws SQLException {
        ApiClient.delete("/api/pulidos/historial/" + idP);
    }
}
