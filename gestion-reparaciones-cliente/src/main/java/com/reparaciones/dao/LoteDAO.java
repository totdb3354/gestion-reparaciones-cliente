package com.reparaciones.dao;

import com.reparaciones.models.Importacion;
import com.reparaciones.models.Lote;
import com.reparaciones.models.VerificacionImei;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Acceso a lotes e importación vía API REST. */
public class LoteDAO {

    public List<Lote> getAll() throws SQLException {
        return ApiClient.getList("/api/lotes", Lote.class);
    }

    public List<VerificacionImei> verificar(List<String> imeis) throws SQLException {
        // ApiClient.post con tipo de respuesta deserializa un objeto; aquí la respuesta es
        // una lista: usar el mismo patrón getList sobre POST no existe, así que se
        // deserializa como array.
        VerificacionImei[] res = ApiClient.post("/api/lotes/verificar", Map.of("imeis", imeis),
                VerificacionImei[].class);
        return res == null ? List.of() : List.of(res);
    }

    public Importacion.Respuesta importar(Importacion.Request request) throws SQLException {
        return ApiClient.post("/api/lotes/importar", request, Importacion.Respuesta.class);
    }
}
