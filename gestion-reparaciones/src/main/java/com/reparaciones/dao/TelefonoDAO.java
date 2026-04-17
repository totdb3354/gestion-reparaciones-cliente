package com.reparaciones.dao;

import com.reparaciones.models.Telefono;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code Telefono}.
 * <p>Un teléfono se registra automáticamente la primera vez que se le crea
 * una reparación y se elimina si queda sin ninguna reparación asociada.</p>
 *
 * @role ADMIN
 */
public class TelefonoDAO {

    /**
     * Devuelve todos los teléfonos registrados en BD.
     *
     * @return lista de todos los teléfonos
     * @throws SQLException si falla la consulta
     */
    public List<Telefono> getAll() throws SQLException {
        List<Telefono> lista = new ArrayList<>();
        String sql = "SELECT * FROM Telefono";
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(new Telefono(rs.getString("IMEI")));
        }
        return lista;
    }

    /**
     * Comprueba si un IMEI ya existe en BD.
     *
     * @param imei IMEI a buscar
     * @return {@code true} si el IMEI existe
     * @throws SQLException si falla la consulta
     */
    public boolean exists(String imei) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Telefono WHERE IMEI = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, imei);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        }
        return false;
    }

    /**
     * Inserta un nuevo teléfono en BD.
     *
     * @param imei IMEI del dispositivo
     * @throws SQLException si falla el insert (p. ej. IMEI duplicado)
     */
    public void insertar(String imei) throws SQLException {
        String sql = "INSERT INTO Telefono (IMEI) VALUES (?)";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, imei);
            ps.executeUpdate();
        }
    }

    /**
     * Elimina el teléfono con el IMEI dado.
     * <p>Solo llamar cuando no quedan reparaciones asociadas a este IMEI.</p>
     *
     * @param imei IMEI del dispositivo a eliminar
     * @throws SQLException si falla el delete
     */
    public void eliminar(String imei) throws SQLException {
        String sql = "DELETE FROM Telefono WHERE IMEI = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, imei);
            ps.executeUpdate();
        }
    }
}
