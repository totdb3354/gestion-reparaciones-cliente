package com.reparaciones.dao;

import com.reparaciones.models.ReparacionComponente;
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
                lista.add(new ReparacionComponente(
                        rs.getString("ID_REP"),
                        rs.getInt("ID_COM"),
                        rs.getBoolean("ES_REUTILIZADO"),
                        rs.getBoolean("ES_INCIDENCIA"),
                        rs.getBoolean("ES_RESUELTO"),
                        rs.getString("INCIDENCIA"),
                        rs.getString("OBSERVACIONES")));
            }
        }
        return lista;
    }

    /**
     * Inserta un componente en una reparación y descuenta stock si no es
     * reutilizado.
     * Usa transacción para garantizar que el INSERT y el UPDATE de stock
     * ocurran juntos o ninguno — evita stock inconsistente si falla a mitad.
     */
    public void insertar(ReparacionComponente rc, int cantidad) throws SQLException {
        String sqlInsert = "INSERT INTO Reparacion_componente " +
                "(ID_REP, ID_COM, ES_REUTILIZADO, ES_INCIDENCIA, ES_RESUELTO, INCIDENCIA, OBSERVACIONES) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String sqlStock = "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?";

        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(sqlInsert)) {
                ps1.setString(1, rc.getIdRep());
                ps1.setInt(2, rc.getIdCom());
                ps1.setBoolean(3, rc.isEsReutilizado());
                ps1.setBoolean(4, rc.isEsIncidencia());
                ps1.setBoolean(5, rc.isEsResuelto());
                ps1.setString(6, rc.getIncidencia());
                ps1.setString(7, rc.getObservaciones());
                ps1.executeUpdate();

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

    /**
     * Marca una incidencia como activa y guarda el comentario.
     * ES_INCIDENCIA pasa a TRUE y se rellena el campo INCIDENCIA.
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
     * Borra una incidencia — solo permitido si ES_RESUELTO=FALSE.
     * ES_INCIDENCIA vuelve a FALSE e INCIDENCIA se pone a NULL.
     */
    public void borrarIncidencia(String idRep) throws SQLException {
        String sql = "UPDATE Reparacion_componente SET ES_INCIDENCIA = FALSE, INCIDENCIA = NULL WHERE ID_REP = ? AND ES_RESUELTO = FALSE";
        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, idRep);
            ps.executeUpdate();
        }
    }
}