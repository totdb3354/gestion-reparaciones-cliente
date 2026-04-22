package com.reparaciones.dao;

import com.reparaciones.models.ReparacionComponente;
import com.reparaciones.models.SolicitudResumen;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReparacionComponenteDAO {

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

    public void eliminar(String idRep, int idCom) throws SQLException {
        String sql = "DELETE FROM Reparacion_componente WHERE ID_REP = ? AND ID_COM = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, idRep);
            ps.setInt(2, idCom);
            ps.executeUpdate();
        }
    }

    public void marcarIncidencia(String idRep, String comentario) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ES_INCIDENCIA = TRUE, INCIDENCIA = ? WHERE ID_REP = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, comentario);
            ps.setString(2, idRep);
            ps.executeUpdate();
        }
    }

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

    public int contarSolicitudesPendientes() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Reparacion_componente " +
                     "WHERE ES_SOLICITUD = TRUE AND ESTADO_SOLICITUD = 'PENDIENTE'";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

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

    public void actualizarEstadoSolicitud(int idRc, String estado) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ESTADO_SOLICITUD = ? WHERE ID_RC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setInt(2, idRc);
            ps.executeUpdate();
        }
    }

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
