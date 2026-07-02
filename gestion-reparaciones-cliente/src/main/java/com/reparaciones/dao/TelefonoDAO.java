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
     * Inserta o actualiza el teléfono con modelo e idCli dados.
     *
     * @param imei   IMEI del dispositivo
     * @param modelo modelo del teléfono (puede ser null)
     * @param idCli  ID del cliente asociado (puede ser null)
     * @throws SQLException si falla la llamada al servidor
     */
    public void insertar(String imei, String modelo, Integer idCli) throws SQLException {
        insertar(imei, modelo, idCli, false);
    }

    /**
     * Alta/actualización de teléfono. Si {@code clienteExplicito} es true, el servidor
     * fija ID_CLI al valor dado (incluido null → sin cliente); si es false, un idCli
     * null preserva el cliente actual (COALESCE).
     */
    public void insertar(String imei, String modelo, Integer idCli, boolean clienteExplicito) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("imei", imei);
        body.put("modelo", modelo != null ? modelo : "");
        body.put("idCli", idCli); // puede ser null
        body.put("clienteExplicito", clienteExplicito);
        ApiClient.post("/api/telefonos", body);
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

    /** @return id del cliente asociado al IMEI, o {@code null} si no tiene. */
    public Integer getClienteId(String imei) throws SQLException {
        String val = ApiClient.getString("/api/telefonos/" + imei + "/cliente");
        return (val == null || val.isBlank() || val.equals("null")) ? null : Integer.valueOf(val);
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

    public void actualizarObservacion(String imei, String observacion, java.time.LocalDateTime updatedAt) throws SQLException {
        ApiClient.patch("/api/telefonos/" + imei + "/observacion",
                Map.of("observacion", observacion != null ? observacion : "",
                       "updatedAt", updatedAt));
    }

    public void actualizarRevisionLogistica(String imei, boolean revisado, java.time.LocalDateTime updatedAt) throws SQLException {
        ApiClient.put("/api/telefonos/" + imei + "/revision-logistica",
                Map.of("revisado", revisado, "updatedAt", updatedAt));
    }

    /**
     * Actualiza el cliente asociado al teléfono con el IMEI dado.
     *
     * @param imei      IMEI del dispositivo
     * @param idCli     ID del cliente (puede ser null para desvincular)
     * @param updatedAt timestamp de última actualización
     * @throws SQLException si falla la llamada al servidor
     * @throws com.reparaciones.utils.StaleDataException si los datos en el servidor son más recientes
     */
    public void actualizarCliente(String imei, Integer idCli, java.time.LocalDateTime updatedAt)
            throws SQLException, com.reparaciones.utils.StaleDataException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("idCli", idCli); // null = quitar
        body.put("updatedAt", updatedAt);
        ApiClient.patch("/api/telefonos/" + imei + "/cliente", body);
    }
}
