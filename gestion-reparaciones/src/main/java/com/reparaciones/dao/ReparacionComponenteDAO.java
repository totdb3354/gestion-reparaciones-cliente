package com.reparaciones.dao;

import com.reparaciones.models.ReparacionComponente;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code Reparacion_componente}.
 * <p>Gestiona la relación entre reparaciones y componentes, incluyendo
 * el descuento automático de stock al insertar y el control de incidencias.</p>
 * <p>Las operaciones de escritura usan transacciones para garantizar la
 * consistencia entre {@code Reparacion_componente} y {@code Componente.STOCK}.</p>
 *
 * @role ADMIN; TECNICO (insertar y marcar incidencias)
 */
public class ReparacionComponenteDAO {

    /**
     * Devuelve todos los componentes vinculados a una reparación.
     *
     * @param idRep ID de la reparación
     * @return lista de componentes de la reparación (puede estar vacía)
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
     * Vincula un componente a una reparación y descuenta stock si no es reutilizado.
     * <p>El INSERT en {@code Reparacion_componente} y el UPDATE de stock se ejecutan
     * en una transacción: o los dos tienen éxito o ninguno.</p>
     *
     * @param rc       datos de la relación reparación-componente
     * @param cantidad número de unidades a descontar del stock
     * @throws SQLException si falla la transacción
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

    /**
     * Elimina la relación entre una reparación y un componente.
     *
     * @param idRep ID de la reparación
     * @param idCom ID del componente
     * @throws SQLException si falla el delete
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
     * Marca una incidencia activa en el componente de la reparación.
     * <p>{@code ES_INCIDENCIA} pasa a {@code TRUE} y se guarda el comentario
     * en el campo {@code INCIDENCIA}.</p>
     *
     * @param idRep     ID de la reparación
     * @param comentario descripción de la incidencia
     * @throws SQLException si falla el update
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
     * Cancela la incidencia de una reparación, solo si aún no está resuelta.
     * <p>{@code ES_INCIDENCIA} vuelve a {@code FALSE} e {@code INCIDENCIA} a {@code NULL}.
     * Si ya está resuelta ({@code ES_RESUELTO = TRUE}) no hace nada.</p>
     *
     * @param idRep ID de la reparación cuya incidencia se cancela
     * @throws SQLException si falla el update
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