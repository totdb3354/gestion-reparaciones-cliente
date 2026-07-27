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

    /** Inventario completo para la vista IMEIs evolucionada (F2a). */
    public List<com.reparaciones.models.TelefonoInventario> getInventario() throws SQLException {
        return ApiClient.getList("/api/telefonos/inventario", com.reparaciones.models.TelefonoInventario.class);
    }

    /** Edición de atributos (modelo/storage/color/grados) con lock optimista. */
    public void actualizarAtributos(String imei, String modelo, Integer storageGb, String color,
                                    String gradoProveedor, String gradoPropio, Boolean esEsim,
                                    java.time.LocalDateTime updatedAt) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("modelo", modelo);
        body.put("storageGb", storageGb);
        body.put("color", color);
        body.put("gradoProveedor", gradoProveedor);
        body.put("gradoPropio", gradoPropio);
        body.put("esEsim", esEsim);
        body.put("updatedAt", updatedAt);
        ApiClient.patch("/api/telefonos/" + imei + "/atributos", body);
    }

    /** F2b: escaneo masivo a revisar. Devuelve el resultado por IMEI (enum del servidor como texto). */
    public java.util.List<com.reparaciones.models.ResultadoARevisar> pasarARevisar(java.util.List<String> imeis) throws SQLException {
        com.reparaciones.models.ResultadoARevisar[] res = ApiClient.post(
                "/api/telefonos/a-revisar", java.util.Map.of("imeis", imeis),
                com.reparaciones.models.ResultadoARevisar[].class);
        return java.util.Arrays.asList(res);
    }

    /** F2b: revisión vigente para la ficha; null si el teléfono nunca pasó por revisión. */
    public com.reparaciones.models.RevisionTelefono getRevision(String imei) throws SQLException {
        RevisionResponse r = ApiClient.get("/api/telefonos/" + imei + "/revision", RevisionResponse.class);
        return (r != null && r.existe) ? r.revision : null;
    }

    private static class RevisionResponse {
        boolean existe;
        com.reparaciones.models.RevisionTelefono revision;
    }

    public void guardarRevisionEstetica(String imei, String grado, String pant) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("grado", grado);
        body.put("pant", pant);
        ApiClient.patch("/api/telefonos/" + imei + "/revision/estetica", body);
    }

    public void guardarRevisionFuncional(String imei, com.reparaciones.models.RevisionTelefono f) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("bateriaPct", f.getFunBateriaPct());
        body.put("pantTactil", f.isFunPantTactil());
        body.put("pantQuemada", f.isFunPantQuemada());
        body.put("pantMal", f.isFunPantMal());
        body.put("camMancha", f.isFunCamMancha());
        body.put("camLente", f.isFunCamLente());
        body.put("altSup", f.isFunAltSup());
        body.put("altInf", f.isFunAltInf());
        body.put("mic", f.isFunMic());
        body.put("faceId", f.isFunFaceId());
        body.put("ms", f.isFunMs());
        body.put("msTexto", f.getFunMsTexto());
        body.put("bloqueoOp", f.isFunBloqueoOp());
        body.put("observacion", f.getFunObservacion());
        ApiClient.patch("/api/telefonos/" + imei + "/revision/funcional", body);
    }

    /** F2b: acciones OK / BLOQUEAR / DESBLOQUEAR / DESGUACE (motivo solo en desguace/bloqueo). */
    public void accionEstado(String imei, String accion, String motivo) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("accion", accion);
        body.put("motivo", motivo);
        ApiClient.post("/api/telefonos/" + imei + "/estado", body);
    }

    /** F2c: remesa de salida. Devuelve id de envío creado (o null) y resultado por IMEI. */
    public com.reparaciones.models.ResultadoEnvioLote enviarTelefonos(Integer idCli, String destinoTexto,
            String referencia, java.util.List<String> imeis) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("idCli", idCli);
        body.put("destinoTexto", destinoTexto);
        body.put("referencia", referencia);
        body.put("imeis", imeis);
        return ApiClient.post("/api/envios", body, com.reparaciones.models.ResultadoEnvioLote.class);
    }

    /** F2c: registro masivo de devoluciones (cada item con su motivo). */
    public java.util.List<com.reparaciones.models.ItemDevolucion> registrarDevoluciones(
            java.util.List<java.util.Map<String, String>> items) throws SQLException {
        com.reparaciones.models.ItemDevolucion[] res = ApiClient.post(
                "/api/telefonos/devoluciones", java.util.Map.of("items", items),
                com.reparaciones.models.ItemDevolucion[].class);
        return java.util.Arrays.asList(res);
    }

    /** F2c: línea de vida del teléfono para el historial de la ficha. */
    public java.util.List<com.reparaciones.models.MovimientoTelefono> getMovimientos(String imei) throws SQLException {
        return ApiClient.getList("/api/telefonos/" + imei + "/movimientos",
                com.reparaciones.models.MovimientoTelefono.class);
    }
}
