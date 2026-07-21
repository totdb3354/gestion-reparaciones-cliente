package com.reparaciones.dao;

import com.reparaciones.models.Proveedor;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProveedorDAO {

    public static final String TIPO_COMPONENTES = "COMPONENTES";
    public static final String TIPO_TELEFONOS   = "TELEFONOS";

    public List<Proveedor> getAll(String tipo) throws SQLException {
        return ApiClient.getList("/api/proveedores" + (tipo == null ? "" : "?tipo=" + tipo), Proveedor.class);
    }

    public List<Proveedor> getActivos(String tipo) throws SQLException {
        return ApiClient.getList("/api/proveedores/activos" + (tipo == null ? "" : "?tipo=" + tipo), Proveedor.class);
    }

    public boolean tienePedidos(int idProv) throws SQLException {
        return ApiClient.getBoolean("/api/proveedores/" + idProv + "/tiene-pedidos");
    }

    public void insertar(String nombre, String divisa, String tipo) throws SQLException {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", nombre);
        if (divisa != null) body.put("divisa", divisa);
        if (tipo != null) body.put("tipo", tipo);
        ApiClient.post("/api/proveedores", body);
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
