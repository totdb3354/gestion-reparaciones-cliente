package com.reparaciones.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Equivalencias de color recordadas por el importador (clave = texto normalizado). */
public class ColorEquivalenciaDAO {

    public Map<String, String> getAll() throws SQLException {
        JsonArray arr = ApiClient.get("/api/colores/equivalencias", JsonArray.class);
        Map<String, String> out = new LinkedHashMap<>();
        if (arr != null) for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.put(o.get("textoExterno").getAsString(), o.get("colorOficial").getAsString());
        }
        return out;
    }

    public void guardar(String textoExterno, String colorOficial) throws SQLException {
        ApiClient.put("/api/colores/equivalencias",
                Map.of("textoExterno", textoExterno, "colorOficial", colorOficial));
    }
}
