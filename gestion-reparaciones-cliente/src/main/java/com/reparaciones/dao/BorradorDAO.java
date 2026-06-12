package com.reparaciones.dao;

import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.Collections;

/** Acceso HTTP al borrador del modal de reparación (por asignación / ID_REP). */
public class BorradorDAO {

    /** @return el JSON del borrador, o null si no hay. */
    public String getBorrador(String idRep) throws SQLException {
        Resp r = ApiClient.get("/api/reparaciones/" + idRep + "/borrador", Resp.class);
        return r != null ? r.contenido : null;
    }

    public void guardar(String idRep, String contenidoJson) throws SQLException {
        ApiClient.put("/api/reparaciones/" + idRep + "/borrador",
                Collections.singletonMap("contenido", contenidoJson));
    }

    public void eliminar(String idRep) throws SQLException {
        ApiClient.delete("/api/reparaciones/" + idRep + "/borrador");
    }

    /** Respuesta del GET: {"contenido": "<json>"} o {"contenido": null}. */
    private static class Resp { String contenido; }
}
