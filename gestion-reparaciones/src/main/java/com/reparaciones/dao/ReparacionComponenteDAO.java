package com.reparaciones.dao;

import com.reparaciones.models.ReparacionComponente;
import com.reparaciones.models.SolicitudResumen;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla {@code Reparacion_componente}.
 * <p>Gestiona la relación entre reparaciones y los componentes utilizados en ellas,
 * incluyendo el ciclo de vida de las solicitudes de reposición de stock
 * ({@code ES_SOLICITUD} / {@code ESTADO_SOLICITUD}).</p>
 */
public class ReparacionComponenteDAO {

    /**
     * Devuelve todos los componentes registrados para una reparación.
     *
     * @param idRep identificador de la reparación ({@code R*} o {@code A*})
     * @return lista de componentes asociados, vacía si no hay ninguno
     * @throws SQLException si falla la consulta
     */
    public List<ReparacionComponente> getByReparacion(String idRep) throws SQLException {
        List<ReparacionComponente> lista = new ArrayList<>();
        String sql = "SELECT * FROM Reparacion_componente WHERE ID_REP = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, idRep);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /**
     * Inserta un componente en la reparación y descuenta stock si corresponde.
     * <p>La operación es atómica: si el descuento de stock falla, el INSERT se revierte.</p>
     *
     * @param rc      datos del componente a insertar
     * @param cantidad unidades a descontar del stock (ignorado si {@code rc.isEsReutilizado()})
     * @throws SQLException si falla la transacción
     */
    public void insertar(ReparacionComponente rc, int cantidad) throws SQLException {
        String sqlInsert =
            "INSERT INTO Reparacion_componente " +
            "(ID_REP, ID_COM, ES_REUTILIZADO, ES_INCIDENCIA, ES_RESUELTO, " +
            " INCIDENCIA, OBSERVACIONES, ES_SOLICITUD, DESCRIPCION_SOLICITUD) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlStock = "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?";

        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {
                ps.setString(1, rc.getIdRep());
                ps.setInt(2, rc.getIdCom());
                ps.setBoolean(3, rc.isEsReutilizado());
                ps.setBoolean(4, rc.isEsIncidencia());
                ps.setBoolean(5, rc.isEsResuelto());
                ps.setString(6, rc.getIncidencia());
                ps.setString(7, rc.getObservaciones());
                ps.setBoolean(8, rc.isEsSolicitud());
                ps.setString(9, rc.getDescripcionSolicitud());
                ps.executeUpdate();

                if (!rc.isEsReutilizado() && cantidad > 0) {
                    try (PreparedStatement ps2 = con.prepareStatement(sqlStock)) {
                        ps2.setInt(1, cantidad);
                        ps2.setInt(2, rc.getIdCom());
                        ps2.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Elimina un componente concreto de una reparación.
     *
     * @param idRep identificador de la reparación
     * @param idCom identificador del componente
     * @throws SQLException si falla el DELETE
     */
    public void eliminar(String idRep, int idCom) throws SQLException {
        String sql = "DELETE FROM Reparacion_componente WHERE ID_REP = ? AND ID_COM = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, idRep);
            ps.setInt(2, idCom);
            ps.executeUpdate();
        }
    }

    /**
     * Marca todos los componentes de una reparación como incidencia abierta.
     *
     * @param idRep     identificador de la reparación
     * @param comentario descripción de la incidencia
     * @throws SQLException si falla el UPDATE
     */
    public void marcarIncidencia(String idRep, String comentario) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ES_INCIDENCIA = TRUE, INCIDENCIA = ? WHERE ID_REP = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, comentario);
            ps.setString(2, idRep);
            ps.executeUpdate();
        }
    }

    /**
     * Cancela la incidencia abierta de una reparación (solo las no resueltas).
     *
     * @param idRep identificador de la reparación
     * @throws SQLException si falla el UPDATE
     */
    public void borrarIncidencia(String idRep) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ES_INCIDENCIA = FALSE, INCIDENCIA = NULL " +
                     "WHERE ID_REP = ? AND ES_RESUELTO = FALSE";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, idRep);
            ps.executeUpdate();
        }
    }

    // ── Solicitudes ───────────────────────────────────────────────────────────

    /**
     * Cuenta las solicitudes de pieza con estado {@code PENDIENTE}.
     * <p>Usado para actualizar el badge de notificaciones en la barra de navegación.</p>
     *
     * @return número de solicitudes pendientes
     * @throws SQLException si falla la consulta
     */
    public int contarSolicitudesPendientes() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Reparacion_componente " +
                     "WHERE ES_SOLICITUD = TRUE AND ESTADO_SOLICITUD = 'PENDIENTE'";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Devuelve las solicitudes de pieza filtradas por estado.
     *
     * @param estado {@code "PENDIENTE"}, {@code "GESTIONADA"}, {@code "RECHAZADA"},
     *               o {@code null} para todas
     * @return lista de resúmenes ordenada por fecha descendente
     * @throws SQLException si falla la consulta
     */
    public List<SolicitudResumen> getSolicitudes(String estado) throws SQLException {
        List<SolicitudResumen> lista = new ArrayList<>();
        String sql =
            "SELECT rc.ID_RC, rc.ID_REP, r.IMEI, t.NOMBRE AS TECNICO, " +
            "       rc.ID_COM, c.TIPO, rc.DESCRIPCION_SOLICITUD, " +
            "       rc.ESTADO_SOLICITUD, rc.UPDATED_AT " +
            "FROM Reparacion_componente rc " +
            "JOIN Reparacion r  ON rc.ID_REP = r.ID_REP " +
            "JOIN Tecnico    t  ON r.ID_TEC  = t.ID_TEC " +
            "JOIN Componente c  ON rc.ID_COM  = c.ID_COM " +
            "WHERE rc.ES_SOLICITUD = TRUE " +
            (estado != null ? "AND rc.ESTADO_SOLICITUD = ? " : "") +
            "ORDER BY rc.UPDATED_AT DESC";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (estado != null) ps.setString(1, estado);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new SolicitudResumen(
                    rs.getInt("ID_RC"),
                    rs.getString("ID_REP"),
                    rs.getString("IMEI"),
                    rs.getString("TECNICO"),
                    rs.getInt("ID_COM"),
                    rs.getString("TIPO"),
                    rs.getString("DESCRIPCION_SOLICITUD"),
                    rs.getString("ESTADO_SOLICITUD"),
                    rs.getTimestamp("UPDATED_AT").toLocalDateTime()
                ));
            }
        }
        return lista;
    }

    /**
     * Actualiza el estado de una solicitud de pieza.
     *
     * @param idRc   clave primaria del registro en {@code Reparacion_componente}
     * @param estado nuevo estado: {@code "PENDIENTE"}, {@code "GESTIONADA"} o {@code "RECHAZADA"}
     * @throws SQLException si falla el UPDATE
     */
    public void actualizarEstadoSolicitud(int idRc, String estado) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ESTADO_SOLICITUD = ? WHERE ID_RC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setInt(2, idRc);
            ps.executeUpdate();
        }
    }

    /**
     * Desactiva una solicitud rechazada ({@code ES_SOLICITUD = FALSE}), eliminándola
     * visualmente del panel de notificaciones sin borrar el registro histórico.
     *
     * @param idRc clave primaria del registro en {@code Reparacion_componente}
     * @throws SQLException si falla el UPDATE
     */
    public void limpiarSolicitud(int idRc) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ES_SOLICITUD = FALSE WHERE ID_RC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idRc);
            ps.executeUpdate();
        }
    }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private ReparacionComponente mapear(ResultSet rs) throws SQLException {
        return new ReparacionComponente(
            rs.getInt("ID_RC"),
            rs.getString("ID_REP"),
            rs.getInt("ID_COM"),
            rs.getBoolean("ES_REUTILIZADO"),
            rs.getBoolean("ES_INCIDENCIA"),
            rs.getBoolean("ES_RESUELTO"),
            rs.getString("INCIDENCIA"),
            rs.getString("OBSERVACIONES"),
            rs.getBoolean("ES_SOLICITUD"),
            rs.getString("DESCRIPCION_SOLICITUD"),
            rs.getString("ESTADO_SOLICITUD")
        );
    }
}
