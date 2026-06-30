package com.reparaciones.dao;

import com.google.gson.JsonObject;
import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos del flujo de Glass (prefijos {@code AG}/{@code G}) vía API REST.
 * <p>Glass ≈ reparación: la lectura y el alta de asignaciones son propias
 * ({@code /api/glass/*}), pero <b>completar, editar y borrar se reutilizan</b> de
 * {@link ReparacionDAO} porque operan por {@code ID_REP} (las filas {@code AG}/{@code G}
 * viven en la misma tabla {@code Reparacion}).</p>
 *
 * @role SUPERTECNICO (alta de asignaciones); TECNICO (lectura y finalización de sus glass)
 */
public class GlassDAO {

    // ── Lectura ───────────────────────────────────────────────────────────────

    /** Asignaciones de glass pendientes ({@code AG*}, sin {@code FECHA_FIN}). */
    public List<ReparacionResumen> getAsignacionesGlass() throws SQLException {
        return ApiClient.getList("/api/glass/asignaciones", ReparacionResumen.class);
    }

    /** Asignaciones de glass pendientes de un técnico concreto. */
    public List<ReparacionResumen> getAsignacionesGlassPorTecnico(int idTec) throws SQLException {
        return ApiClient.getList("/api/glass/asignaciones?tecnico=" + idTec, ReparacionResumen.class);
    }

    /** Historial de glass finalizado ({@code G*}). */
    public List<ReparacionResumen> getHistorialGlass() throws SQLException {
        return ApiClient.getList("/api/glass/historial", ReparacionResumen.class);
    }

    /** Historial de glass de un técnico concreto. */
    public List<ReparacionResumen> getHistorialGlassPorTecnico(int idTec) throws SQLException {
        return ApiClient.getList("/api/glass/historial?tecnico=" + idTec, ReparacionResumen.class);
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Crea una asignación de glass y devuelve su ID ({@code AG[yyyyMMdd]_N}).
     *
     * @param imei       IMEI del dispositivo
     * @param idTec      ID del técnico asignado
     * @param comentario comentario opcional
     * @param urgente    {@code true} si debe marcarse urgente
     */
    public String insertarAsignacionGlass(String imei, int idTec, String comentario, boolean urgente) throws SQLException {
        Map<String, Object> body = new HashMap<>();
        body.put("imei", imei);
        body.put("idTec", idTec);
        if (comentario != null && !comentario.isBlank()) body.put("comentario", comentario);
        body.put("urgente", urgente);
        JsonObject resp = ApiClient.post("/api/glass/asignaciones", body, JsonObject.class);
        return resp != null ? resp.get("value").getAsString() : null;
    }
}
