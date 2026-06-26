package com.reparaciones.dao;

import com.reparaciones.models.CompraOtro;
import com.reparaciones.utils.ApiClient;
import com.reparaciones.utils.StaleDataException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos de la tabla {@code Compra_otro} vía API REST.
 * <p>Gestiona el ciclo de vida completo de los pedidos de otros artículos:
 * creación, edición, recepción total/parcial y cancelación.</p>
 * <p>Las operaciones de edición y confirmación usan control de concurrencia optimista
 * ({@link StaleDataException}) comparando {@code UPDATED_AT}.</p>
 *
 * @role ADMIN
 */
public class CompraOtroDAO {

    /**
     * Devuelve todos los pedidos ordenados por fecha descendente.
     *
     * @return lista completa de pedidos
     * @throws SQLException si falla la llamada al servidor
     */
    public List<CompraOtro> getAll() throws SQLException {
        return ApiClient.getList("/api/compras-otros", CompraOtro.class);
    }

    /**
     * Inserta un nuevo pedido en estado {@code pendiente}.
     *
     * @param idProv       ID del proveedor
     * @param concepto     descripción del artículo pedido
     * @param cantidad     número de unidades a pedir
     * @param esUrgente    {@code true} si el pedido es urgente
     * @param precioUnidad precio por unidad en la divisa elegida
     * @param divisa       código ISO 4217 de la divisa
     * @param precioEur    precio por unidad convertido a EUR
     * @throws SQLException si falla la llamada al servidor
     */
    public void insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) throws SQLException {
        ApiClient.post("/api/compras-otros", Map.of(
                "idProv",       idProv,
                "concepto",     concepto,
                "cantidad",     cantidad,
                "esUrgente",    esUrgente,
                "precioUnidad", precioUnidad,
                "divisa",       divisa,
                "precioEur",    precioEur));
    }

    /**
     * Edita los campos principales de un pedido con control de concurrencia optimista.
     *
     * @param p            pedido original con {@code updatedAt} para la comparación optimista
     * @param idProv       nuevo proveedor
     * @param concepto     nueva descripción
     * @param cantidad     nueva cantidad
     * @param esUrgente    nuevo flag de urgencia
     * @param precioUnidad nuevo precio por unidad
     * @param divisa       nuevo código de divisa
     * @param precioEur    nuevo precio en EUR
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void editar(CompraOtro p, int idProv, String concepto, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur)
            throws SQLException, StaleDataException {
        ApiClient.put("/api/compras-otros/" + p.getIdCompraOtro(), Map.of(
                "idProv",       idProv,
                "concepto",     concepto,
                "cantidad",     cantidad,
                "esUrgente",    esUrgente,
                "precioUnidad", precioUnidad,
                "divisa",       divisa,
                "precioEur",    precioEur,
                "updatedAt",    p.getUpdatedAt()));
    }

    /**
     * Confirma un pedido {@code pendiente}: pasa a {@code en_camino}.
     *
     * @param p pedido pendiente a confirmar
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmar(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    /**
     * Marca el pedido como {@code recibido} e incrementa el stock con toda la cantidad pedida.
     *
     * @param p pedido a confirmar
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarRecibido(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar-recibido",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    /**
     * Registra la llegada parcial de un pedido y cambia el estado a {@code parcial}.
     *
     * @param p                pedido del que llega la parte
     * @param cantidadRecibida unidades que han llegado en esta entrega
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarParcial(CompraOtro p, int cantidadRecibida)
            throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar-parcial", Map.of(
                "cantidadRecibida", cantidadRecibida,
                "updatedAt",        p.getUpdatedAt()));
    }

    /**
     * Añade más unidades a un pedido {@code parcial}.
     *
     * @param p             pedido parcial al que llegan más unidades
     * @param cantidadExtra número de unidades adicionales recibidas
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void recibirResto(CompraOtro p, int cantidadExtra)
            throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/recibir-resto", Map.of(
                "cantidadExtra", cantidadExtra,
                "updatedAt",     p.getUpdatedAt()));
    }

    /**
     * Cierra un pedido {@code parcial} marcándolo como {@code recibido}.
     *
     * @param p pedido parcial a cerrar
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarAlterado(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar-alterado",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    /**
     * Cancela un pedido en estado {@code en_camino}.
     *
     * @param p pedido a cancelar
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void cancelar(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/cancelar",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    /**
     * Revierte un pedido {@code recibido} a {@code en_camino} y descuenta el stock añadido.
     * El servidor rechaza la operación si el stock actual es insuficiente.
     *
     * @param p pedido a revertir
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void desrecibir(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/desrecibir",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    /**
     * Borra un pedido en estado {@code pendiente}.
     *
     * @param p pedido pendiente a borrar
     * @throws SQLException       si falla la llamada al servidor
     * @throws StaleDataException si el pedido ya no está pendiente
     */
    public void borrar(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.delete("/api/compras-otros/" + p.getIdCompraOtro());
    }
}
