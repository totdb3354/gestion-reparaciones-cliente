package com.reparaciones.dao;

import com.reparaciones.models.Telefono;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TelefonoDAO {

    public List<Telefono> getAll() throws SQLException {
        List<Telefono> lista = new ArrayList<>();
        String sql = "SELECT * FROM Telefono";
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(new Telefono(rs.getLong("IMEI")));
        }
        return lista;
    }

    public boolean exists(long imei) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Telefono WHERE IMEI = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, imei);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        }
        return false;
    }

    public void insertar(long imei) throws SQLException {
        String sql = "INSERT INTO Telefono (IMEI) VALUES (?)";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, imei);
            ps.executeUpdate();
        }
    }

    public void eliminar(long imei) throws SQLException {
        String sql = "DELETE FROM Telefono WHERE IMEI = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, imei);
            ps.executeUpdate();
        }
    }
}