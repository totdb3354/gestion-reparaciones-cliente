package com.reparaciones.dao;

import com.reparaciones.models.ValorDificultad;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;

/** DAO de la tabla {@code Dificultad_puntos} vía API REST (spec 2026-09-01). */
public class ValoresDificultadDAO {

    public List<ValorDificultad> getAll() throws SQLException {
        return ApiClient.getList("/api/valores-dificultad", ValorDificultad.class);
    }

    /** Guarda la tabla completa; el servidor solo escribe y loguea las claves que cambian. */
    public void guardar(List<ValorDificultad> valores) throws SQLException {
        ApiClient.put("/api/valores-dificultad", valores);
    }
}
