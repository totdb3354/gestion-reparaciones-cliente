package com.reparaciones.dao;

import com.reparaciones.models.CompraComponente;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CompraComponenteDAO {

    public List<CompraComponente> getByComponente(int idCom) throws SQLException {
        List<CompraComponente> lista = new ArrayList<>();
        String sql = "SELECT * FROM Compra_componente WHERE ID_COM = ? ORDER BY FECHA_PEDIDO DESC";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCom);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new CompraComponente(
                    rs.getInt("ID_COMPRA"),
                    rs.getInt("ID_COM"),
                    rs.getInt("CANTIDAD"),
                    rs.getBoolean("ES_URGENTE"),
                    rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime()
                ));
            }
        }
        return lista;
    }

    public void insertar(CompraComponente cc) throws SQLException {
        String sqlCompra = "INSERT INTO Compra_componente (ID_COM, CANTIDAD, ES_URGENTE, FECHA_PEDIDO) " +
                           "VALUES (?, ?, ?, ?)";
        String sqlStock = "UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?";

        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(sqlCompra);
                 PreparedStatement ps2 = con.prepareStatement(sqlStock)) {

                ps1.setInt(1, cc.getIdCom());
                ps1.setInt(2, cc.getCantidad());
                ps1.setBoolean(3, cc.isEsUrgente());
                ps1.setTimestamp(4, Timestamp.valueOf(cc.getFechaPedido()));
                ps1.executeUpdate();

                ps2.setInt(1, cc.getCantidad());
                ps2.setInt(2, cc.getIdCom());
                ps2.executeUpdate();

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }
}