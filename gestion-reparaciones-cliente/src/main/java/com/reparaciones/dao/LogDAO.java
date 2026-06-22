package com.reparaciones.dao;

import com.reparaciones.models.LogActividad;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class LogDAO {

    public List<LogActividad> getAll(String accion, String tecnico,
                                     LocalDate desde, LocalDate hasta) throws SQLException {
        StringBuilder path = new StringBuilder("/api/logs?_=1");
        if (accion  != null && !accion.isBlank())  path.append("&accion=").append(accion);
        if (tecnico != null && !tecnico.isBlank()) path.append("&tecnico=").append(tecnico);
        if (desde   != null) path.append("&desde=").append(desde);
        if (hasta   != null) path.append("&hasta=").append(hasta);
        return ApiClient.getList(path.toString(), LogActividad.class);
    }
}
