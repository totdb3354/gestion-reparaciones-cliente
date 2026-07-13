package com.reparaciones.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Equivalencias de modelo recordadas por el importador (clave = texto normalizado). */
public class EquivalenciaModeloDAO {

    public Map<String, String> getAll() throws SQLException {
        JsonArray arr = ApiClient.get("/api/modelos/equivalencias", JsonArray.class);
        Map<String, String> out = new LinkedHashMap<>();
        if (arr != null) for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.put(o.get("textoExterno").getAsString(), o.get("modeloInterno").getAsString());
        }
        return out;
    }

    public void guardar(String textoExterno, String modeloInterno) throws SQLException {
        ApiClient.put("/api/modelos/equivalencias",
                Map.of("textoExterno", textoExterno, "modeloInterno", modeloInterno));
    }
}
