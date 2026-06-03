package com.reparaciones.dao;

import com.reparaciones.models.Telefono;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos de la tabla {@code Telefono} vía API REST.
 * <p>Un teléfono se registra automáticamente la primera vez que se le crea
 * una reparación y se elimina si queda sin ninguna reparación asociada.</p>
 *
 * @role ADMIN
 */
public class TelefonoDAO {

    /**
     * Devuelve todos los teléfonos registrados.
     *
     * @return lista de todos los teléfonos
     * @throws SQLException si falla la llamada al servidor
     */
    public List<Telefono> getAll() throws SQLException {
        return ApiClient.getList("/api/telefonos", Telefono.class);
    }

    /**
     * Comprueba si un IMEI ya existe.
     *
     * @param imei IMEI a buscar
     * @return {@code true} si el IMEI existe
     * @throws SQLException si falla la llamada al servidor
     */
    public boolean exists(String imei) throws SQLException {
        return ApiClient.getBoolean("/api/telefonos/" + imei + "/exists");
    }

    /**
     * Inserta o actualiza el teléfono con el modelo dado.
     *
     * @param imei   IMEI del dispositivo
     * @param modelo modelo del teléfono (puede ser null)
     * @throws SQLException si falla la llamada al servidor
     */
    public void insertar(String imei, String modelo) throws SQLException {
        ApiClient.post("/api/telefonos", Map.of("imei", imei, "modelo", modelo != null ? modelo : ""));
    }

    /**
     * Inserta un nuevo teléfono sin especificar modelo.
     *
     * @param imei IMEI del dispositivo
     * @throws SQLException si falla la llamada al servidor
     */
    public void insertar(String imei) throws SQLException {
        insertar(imei, null);
    }

    /**
     * Devuelve el modelo almacenado para el IMEI dado, o null si no hay.
     *
     * @param imei IMEI del dispositivo
     * @throws SQLException si falla la llamada al servidor
     */
    public String getModelo(String imei) throws SQLException {
        String val = ApiClient.getString("/api/telefonos/" + imei + "/modelo");
        return (val == null || val.equals("null")) ? null : val;
    }

    /**
     * Elimina el teléfono con el IMEI dado.
     * <p>Solo llamar cuando no quedan reparaciones asociadas a este IMEI.</p>
     *
     * @param imei IMEI del dispositivo a eliminar
     * @throws SQLException si falla la llamada al servidor
     */
    public void eliminar(String imei) throws SQLException {
        ApiClient.delete("/api/telefonos/" + imei);
    }

    public void actualizarObservacion(String imei, String observacion) throws SQLException {
        ApiClient.patch("/api/telefonos/" + imei + "/observacion",
                Map.of("observacion", observacion != null ? observacion : ""));
    }
}
