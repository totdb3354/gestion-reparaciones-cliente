package com.reparaciones.dao;

import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProveedorDAO {

    public List<Proveedor> getAll() throws SQLException {
        return ApiClient.getList("/api/proveedores", Proveedor.class);
    }

    public List<Proveedor> getActivos() throws SQLException {
        return ApiClient.getList("/api/proveedores/activos", Proveedor.class);
    }

    public boolean tienePedidos(int idProv) throws SQLException {
        return ApiClient.getBoolean("/api/proveedores/" + idProv + "/tiene-pedidos");
    }

    public void insertar(String nombre) throws SQLException {
        ApiClient.post("/api/proveedores", Map.of("nombre", nombre));
    }

    public void setActivo(int idProv, boolean activo) throws SQLException {
        ApiClient.patch("/api/proveedores/" + idProv + "/activo", Map.of("activo", activo));
    }

    public void editar(int idProv, String nombre, String divisa, String comentario) throws SQLException {
        ApiClient.put("/api/proveedores/" + idProv,
                Map.of("nombre", nombre, "divisa", divisa, "comentario", comentario == null ? "" : comentario));
    }

    public void borrar(int idProv) throws SQLException {
        ApiClient.delete("/api/proveedores/" + idProv);
    }
}
