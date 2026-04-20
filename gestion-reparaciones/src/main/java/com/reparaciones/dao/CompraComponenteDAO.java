package com.reparaciones.dao;

import com.reparaciones.models.CompraComponente;
import com.reparaciones.models.CompraComponente.Estado;
import com.reparaciones.utils.StaleDataException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code Compra_componente}.
 * <p>Gestiona el ciclo de vida completo de los pedidos de componentes:
 * creación, edición, recepción total/parcial y cancelación.</p>
 * <p>Todas las operaciones que modifican stock se ejecutan en transacciones
 * para garantizar consistencia entre {@code Compra_componente} y {@code Componente.STOCK}.
 * Las operaciones de edición y confirmación usan control de concurrencia optimista
 * ({@link com.reparaciones.utils.StaleDataException}) comparando {@code UPDATED_AT}.</p>
 *
 * @role ADMIN
 */
public class CompraComponenteDAO {

    private static final String SQL_BASE = """
            SELECT cc.*, c.TIPO AS tipo_componente, p.NOMBRE AS nombre_proveedor
            FROM Compra_componente cc
            JOIN Componente c ON cc.ID_COM  = c.ID_COM
            JOIN Proveedor  p ON cc.ID_PROV = p.ID_PROV
            """;

    // ─── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Devuelve todos los pedidos ordenados por fecha descendente.
     *
     * @return lista completa de pedidos
     * @throws SQLException si falla la consulta
     */
    public List<CompraComponente> getAll() throws SQLException {
        List<CompraComponente> lista = new ArrayList<>();
        String sql = SQL_BASE + " ORDER BY cc.FECHA_PEDIDO DESC, cc.ID_COMPRA DESC";
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /**
     * Devuelve los pedidos en estado {@code pendiente}, ordenados por urgencia y fecha.
     *
     * @return lista de pedidos pendientes (urgentes primero, luego por fecha ascendente)
     * @throws SQLException si falla la consulta
     */
    public List<CompraComponente> getPendientes() throws SQLException {
        List<CompraComponente> lista = new ArrayList<>();
        String sql = SQL_BASE + " WHERE cc.ESTADO = 'pendiente' ORDER BY cc.ES_URGENTE DESC, cc.FECHA_PEDIDO ASC";
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    // ─── Insertar ─────────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo pedido en estado {@code pendiente}.
     *
     * @param idCom       ID del componente pedido
     * @param idProv      ID del proveedor
     * @param cantidad    número de unidades a pedir
     * @param esUrgente   {@code true} si el pedido es urgente
     * @param precioUnidad precio por unidad en la divisa elegida
     * @param divisa      código ISO 4217 de la divisa
     * @param precioEur   precio por unidad convertido a EUR
     * @throws SQLException si falla el insert
     */
    public void insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) throws SQLException {
        String sql = """
                INSERT INTO Compra_componente
                    (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE,
                     PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pendiente')
                """;
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCom);
            ps.setInt(2, idProv);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            ps.executeUpdate();
        }
    }

    // ─── Editar pedido ────────────────────────────────────────────────────────

    /**
     * Edita los campos principales de un pedido con control de concurrencia optimista.
     * <p>Si el pedido ya estaba {@code recibido}, ajusta el stock por la diferencia entre
     * la cantidad anterior y la nueva, y sincroniza {@code CANTIDAD_RECIBIDA}.</p>
     *
     * @param pedido       pedido original con {@code updatedAt} para la comparación optimista
     * @param idProv       nuevo proveedor
     * @param cantidad     nueva cantidad
     * @param esUrgente    nuevo flag de urgencia
     * @param precioUnidad nuevo precio por unidad
     * @param divisa       nuevo código de divisa
     * @param precioEur    nuevo precio en EUR
     * @throws SQLException      si falla la transacción o el stock quedaría negativo
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void editar(CompraComponente pedido, int idProv, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur)
            throws SQLException, StaleDataException {
        String sql = """
                UPDATE Compra_componente
                SET ID_PROV = ?, CANTIDAD = ?, ES_URGENTE = ?,
                    PRECIO_UNIDAD_PEDIDO = ?, DIVISA = ?, PRECIO_EUR = ?
                WHERE ID_COMPRA = ? AND UPDATED_AT = ?
                """;
        String sqlCantidadRecibida = """
                UPDATE Compra_componente SET CANTIDAD_RECIBIDA = ?
                WHERE ID_COMPRA = ?
                """;
        String sqlStock = """
                UPDATE Componente SET STOCK = STOCK + ?
                WHERE ID_COM = (SELECT ID_COM FROM Compra_componente WHERE ID_COMPRA = ?)
                """;
        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, idProv);
                    ps.setInt(2, cantidad);
                    ps.setBoolean(3, esUrgente);
                    ps.setDouble(4, precioUnidad);
                    ps.setString(5, divisa);
                    ps.setDouble(6, precioEur);
                    ps.setInt(7, pedido.getIdCompra());
                    ps.setTimestamp(8, Timestamp.valueOf(pedido.getUpdatedAt()));
                    if (ps.executeUpdate() == 0)
                        throw new StaleDataException(
                                "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
                }
                // Si el pedido ya estaba recibido, ajustar stock por la diferencia y sincronizar CANTIDAD_RECIBIDA
                if (pedido.getEstado() == Estado.recibido) {
                    int cantidadRecibida = pedido.getCantidadRecibida() != null
                            ? pedido.getCantidadRecibida() : pedido.getCantidad();
                    int diferencia = cantidad - cantidadRecibida;
                    if (diferencia < 0) {
                        String sqlCheckStock = """
                                SELECT c.STOCK FROM Componente c
                                JOIN Compra_componente cc ON cc.ID_COM = c.ID_COM
                                WHERE cc.ID_COMPRA = ?
                                """;
                        try (PreparedStatement ps = con.prepareStatement(sqlCheckStock)) {
                            ps.setInt(1, pedido.getIdCompra());
                            ResultSet rs = ps.executeQuery();
                            if (rs.next() && rs.getInt("STOCK") + diferencia < 0)
                                throw new SQLException(
                                        "Stock insuficiente: reducir la cantidad en " + Math.abs(diferencia) +
                                        " dejaría el stock en negativo.");
                        }
                    }
                    if (diferencia != 0) {
                        try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                            ps.setInt(1, diferencia);
                            ps.setInt(2, pedido.getIdCompra());
                            ps.executeUpdate();
                        }
                    }
                    // Sincronizar CANTIDAD_RECIBIDA con la nueva cantidad
                    try (PreparedStatement ps = con.prepareStatement(sqlCantidadRecibida)) {
                        ps.setInt(1, cantidad);
                        ps.setInt(2, pedido.getIdCompra());
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // ─── Confirmar llegada ────────────────────────────────────────────────────

    /**
     * Marca el pedido como {@code recibido} e incrementa el stock con toda la cantidad pedida.
     * <p>Opera en transacción; usa {@code UPDATED_AT} para control optimista.</p>
     *
     * @param pedido pedido a confirmar
     * @throws SQLException       si falla la transacción
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarRecibido(CompraComponente pedido)
            throws SQLException, StaleDataException {
        String sqlUpdate = """
                UPDATE Compra_componente
                SET ESTADO = 'recibido', FECHA_LLEGADA = NOW(),
                    CANTIDAD_RECIBIDA = CANTIDAD
                WHERE ID_COMPRA = ? AND UPDATED_AT = ?
                """;
        String sqlStock = """
                UPDATE Componente SET STOCK = STOCK + (
                    SELECT CANTIDAD FROM Compra_componente WHERE ID_COMPRA = ?
                ) WHERE ID_COM = (
                    SELECT ID_COM FROM Compra_componente WHERE ID_COMPRA = ?
                )
                """;
        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = con.prepareStatement(sqlUpdate)) {
                    ps.setInt(1, pedido.getIdCompra());
                    ps.setTimestamp(2, Timestamp.valueOf(pedido.getUpdatedAt()));
                    rows = ps.executeUpdate();
                }
                if (rows == 0) throw new StaleDataException(
                        "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
                try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                    ps.setInt(1, pedido.getIdCompra());
                    ps.setInt(2, pedido.getIdCompra());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Cierra un pedido {@code parcial} marcándolo como {@code recibido}.
     * <p>No modifica el stock porque ya se incrementó al registrar el parcial.
     * Usa {@code UPDATED_AT} para control optimista.</p>
     *
     * @param pedido pedido parcial a cerrar
     * @throws SQLException       si falla el update
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarAlterado(CompraComponente pedido)
            throws SQLException, StaleDataException {
        // Solo cambia estado a recibido — el stock ya fue incrementado al registrar el parcial
        String sql = """
                UPDATE Compra_componente
                SET ESTADO = 'recibido', FECHA_LLEGADA = NOW()
                WHERE ID_COMPRA = ? AND ESTADO = 'parcial' AND UPDATED_AT = ?
                """;
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, pedido.getIdCompra());
            ps.setTimestamp(2, Timestamp.valueOf(pedido.getUpdatedAt()));
            if (ps.executeUpdate() == 0)
                throw new StaleDataException(
                        "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
        }
    }

    // ─── Recepción parcial ────────────────────────────────────────────────────

    /**
     * Registra la llegada parcial de un pedido: suma {@code cantidadRecibida} al stock
     * y cambia el estado a {@code parcial} (pedido permanece abierto).
     *
     * @param pedido           pedido del que llega la parte
     * @param cantidadRecibida unidades que han llegado en esta entrega
     * @throws SQLException       si falla la transacción
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void confirmarParcial(CompraComponente pedido, int cantidadRecibida)
            throws SQLException, StaleDataException {
        String sqlUpdate = """
                UPDATE Compra_componente
                SET ESTADO = 'parcial', FECHA_LLEGADA = NOW(),
                    CANTIDAD_RECIBIDA = ?
                WHERE ID_COMPRA = ? AND UPDATED_AT = ?
                """;
        String sqlStock = """
                UPDATE Componente SET STOCK = STOCK + ?
                WHERE ID_COM = (SELECT ID_COM FROM Compra_componente WHERE ID_COMPRA = ?)
                """;
        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = con.prepareStatement(sqlUpdate)) {
                    ps.setInt(1, cantidadRecibida);
                    ps.setInt(2, pedido.getIdCompra());
                    ps.setTimestamp(3, Timestamp.valueOf(pedido.getUpdatedAt()));
                    rows = ps.executeUpdate();
                }
                if (rows == 0) throw new StaleDataException(
                        "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
                try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                    ps.setInt(1, cantidadRecibida);
                    ps.setInt(2, pedido.getIdCompra());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Añade más unidades a un pedido {@code parcial}.
     * <p>Si la nueva suma total alcanza la cantidad pedida, el pedido se cierra
     * como {@code recibido}; en caso contrario sigue {@code parcial}.</p>
     *
     * @param pedido        pedido parcial al que llegan más unidades
     * @param cantidadExtra número de unidades adicionales recibidas
     * @throws SQLException       si falla la transacción
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void recibirResto(CompraComponente pedido, int cantidadExtra)
            throws SQLException, StaleDataException {
        String sqlUpdate = """
                UPDATE Compra_componente
                SET CANTIDAD_RECIBIDA = ?,
                    ESTADO = CASE WHEN ? >= CANTIDAD THEN 'recibido' ELSE 'parcial' END,
                    FECHA_LLEGADA = CASE WHEN ? >= CANTIDAD THEN NOW() ELSE FECHA_LLEGADA END
                WHERE ID_COMPRA = ? AND ESTADO = 'parcial' AND UPDATED_AT = ?
                """;
        String sqlStock = """
                UPDATE Componente SET STOCK = STOCK + ?
                WHERE ID_COM = (SELECT ID_COM FROM Compra_componente WHERE ID_COMPRA = ?)
                """;
        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                int rows;
                int yaRecibidas = pedido.getCantidadRecibida() != null ? pedido.getCantidadRecibida() : 0;
                int nuevaTotal  = yaRecibidas + cantidadExtra;
                try (PreparedStatement ps = con.prepareStatement(sqlUpdate)) {
                    ps.setInt(1, nuevaTotal);
                    ps.setInt(2, nuevaTotal);
                    ps.setInt(3, nuevaTotal);
                    ps.setInt(4, pedido.getIdCompra());
                    ps.setTimestamp(5, Timestamp.valueOf(pedido.getUpdatedAt()));
                    rows = ps.executeUpdate();
                }
                if (rows == 0) throw new StaleDataException(
                        "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
                try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                    ps.setInt(1, cantidadExtra);
                    ps.setInt(2, pedido.getIdCompra());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ─── Cancelar ─────────────────────────────────────────────────────────────

    /**
     * Cancela un pedido en estado {@code pendiente}.
     * <p>No modifica stock porque aún no se había recibido nada.
     * Usa {@code UPDATED_AT} para control optimista.</p>
     *
     * @param pedido pedido a cancelar (debe estar en estado {@code pendiente})
     * @throws SQLException       si falla el update
     * @throws StaleDataException si otro usuario modificó el pedido antes que este
     */
    public void cancelar(CompraComponente pedido) throws SQLException, StaleDataException {
        String sql = "UPDATE Compra_componente SET ESTADO = 'cancelado' WHERE ID_COMPRA = ? AND ESTADO = 'pendiente' AND UPDATED_AT = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, pedido.getIdCompra());
            ps.setTimestamp(2, Timestamp.valueOf(pedido.getUpdatedAt()));
            if (ps.executeUpdate() == 0)
                throw new StaleDataException(
                        "El pedido fue modificado por otro usuario. Recarga los datos e inténtalo de nuevo.");
        }
    }


    // ─── Consultas auxiliares ─────────────────────────────────────────────────

    /**
     * Suma las unidades pendientes de recibir para un componente concreto.
     *
     * @param idCom ID del componente
     * @return total de unidades en estado {@code pendiente} para este componente
     * @throws SQLException si falla la consulta
     */
    public int getCantidadPendientePorComponente(int idCom) throws SQLException {
        String sql = "SELECT COALESCE(SUM(CANTIDAD), 0) FROM Compra_componente WHERE ID_COM = ? AND ESTADO = 'pendiente'";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCom);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ─── Mapeo ────────────────────────────────────────────────────────────────

    /** Mapea una fila del {@code ResultSet} a un {@link com.reparaciones.models.CompraComponente}. */
    private CompraComponente mapear(ResultSet rs) throws SQLException {
        Timestamp tsLlegada = rs.getTimestamp("FECHA_LLEGADA");
        return new CompraComponente(
                rs.getInt("ID_COMPRA"),
                rs.getInt("ID_COM"),
                rs.getString("tipo_componente"),
                rs.getInt("ID_PROV"),
                rs.getString("nombre_proveedor"),
                rs.getInt("CANTIDAD"),
                rs.getObject("CANTIDAD_RECIBIDA") != null ? rs.getInt("CANTIDAD_RECIBIDA") : null,
                rs.getBoolean("ES_URGENTE"),
                rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime(),
                tsLlegada != null ? tsLlegada.toLocalDateTime() : null,
                rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
                rs.getString("DIVISA"),
                rs.getDouble("PRECIO_EUR"),
                Estado.valueOf(rs.getString("ESTADO")),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        );
    }
}
