package com.reparaciones.dao;

import com.reparaciones.models.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    /**
     * Busca el usuario por nombre y verifica la password con BCrypt.
     * Devuelve el Usuario si las credenciales son correctas, null si no.
     */
    public Usuario login(String nombreUsuario, String password) throws SQLException {
        String sql = "SELECT ID_USU, NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC " +
                "FROM Usuario WHERE NOMBRE_USUARIO = ?";
        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("PASSWORD");
                if (BCrypt.checkpw(password, hash)) {
                    int idUsu = rs.getInt("ID_USU");
                    String rol = rs.getString("ROL");
                    int idTecRaw = rs.getInt("ID_TEC");
                    Integer idTec = rs.wasNull() ? null : idTecRaw;
                    return new Usuario(idUsu, nombreUsuario, rol, idTec);
                }
            }
        }
        return null;
    }

    /**
     * Verifica si la contraseña introducida coincide con la del admin.
     * Se usa como contraseña maestra para acceder al registro de nuevos técnicos.
     */
    public boolean verificarPasswordAdmin(String password) throws SQLException {
        String sql = "SELECT PASSWORD FROM Usuario WHERE ROL = 'ADMIN' LIMIT 1";
        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return BCrypt.checkpw(password, rs.getString("PASSWORD"));
            }
        }
        return false;
    }

    /**
     * Devuelve todos los usuarios con rol TECNICO para mostrarlos en la tabla
     * de gestión de la vista de registro. El admin no aparece en esta lista.
     */
    public List<Usuario> getUsuariosTecnicos() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT ID_USU, NOMBRE_USUARIO, ROL, ID_TEC FROM Usuario WHERE ROL = 'TECNICO'";
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idTecRaw = rs.getInt("ID_TEC");
                Integer idTec = rs.wasNull() ? null : idTecRaw;
                lista.add(new Usuario(rs.getInt("ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getString("ROL"), idTec));
            }
        }
        return lista;
    }

    /**
     * Borra solo el Usuario (credenciales de login) sin tocar la tabla Tecnico.
     * Las reparaciones históricas del técnico quedan intactas — el nombre
     * sigue apareciendo en la tabla Reparacion a través de ID_TEC.
     */
    public void borrarUsuario(int idUsu) throws SQLException {
        String sql = "DELETE FROM Usuario WHERE ID_USU = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsu);
            ps.executeUpdate();
        }
    }

    /**
     * Registra un nuevo técnico — inserta en Tecnico y en Usuario en transacción.
     * Si falla cualquiera de los dos inserts, se hace rollback de ambos.
     */
    public void registrarTecnico(String nombreTecnico, String nombreUsuario, String password) throws SQLException {
        String sqlTecnico = "INSERT INTO Tecnico (NOMBRE) VALUES (?)";
        String sqlUsuario = "INSERT INTO Usuario (NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, 'TECNICO', ?)";

        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                int idTec;
                try (PreparedStatement ps = con.prepareStatement(sqlTecnico, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nombreTecnico);
                    ps.executeUpdate();
                    ResultSet rs = ps.getGeneratedKeys();
                    rs.next();
                    idTec = rs.getInt(1);
                }

                try (PreparedStatement ps = con.prepareStatement(sqlUsuario)) {
                    ps.setString(1, nombreUsuario);
                    ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
                    ps.setInt(3, idTec);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }
}