package com.reparaciones.dao;

import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;

public class LogDAO {

    public List<LogActividad> getAll() throws SQLException {
        return ApiClient.getList("/api/logs", LogActividad.class);
    }
}
