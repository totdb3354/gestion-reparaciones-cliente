package com.reparaciones.dao;

import com.reparaciones.models.Cliente;
import com.reparaciones.utils.ApiClient;
import com.reparaciones.utils.StaleDataException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ClienteDAO {

    public List<Cliente> getAll() throws SQLException {
        return ApiClient.getList("/api/clientes", Cliente.class);
    }

    public List<Cliente> getActivos() throws SQLException {
        return ApiClient.getList("/api/clientes/activos", Cliente.class);
    }

    public boolean tieneTelefonos(int idCli) throws SQLException {
        return ApiClient.getBoolean("/api/clientes/" + idCli + "/tiene-telefonos");
    }

    public void insertar(String nombre) throws SQLException {
        ApiClient.post("/api/clientes", Map.of("nombre", nombre));
    }

    public void editar(int idCli, String nombre, LocalDateTime updatedAt)
            throws SQLException, StaleDataException {
        ApiClient.put("/api/clientes/" + idCli,
                Map.of("nombre", nombre, "updatedAt", updatedAt));
    }

    public void setActivo(int idCli, boolean activo, LocalDateTime updatedAt)
            throws SQLException, StaleDataException {
        ApiClient.patch("/api/clientes/" + idCli + "/activo",
                Map.of("activo", activo, "updatedAt", updatedAt));
    }
}
