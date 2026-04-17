package com.reparaciones.dao;

import com.reparaciones.models.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de usuarios del sistema.
 * <p>Gestiona autenticación (con BCrypt), altas, bajas lógicas y eliminaciones
 * definitivas de cuentas de técnicos. Los administradores no se gestionan desde
 * aquí; sus cuentas se crean directamente en BD.</p>
 *
 * @role ADMIN (gestión de cuentas); login accesible a todos los roles
 */
public class UsuarioDAO {

    /**
     * Autentica al usuario verificando nombre y contraseña con BCrypt.
     * <p>Rechaza el login si el técnico asociado está desactivado
     * ({@code ACTIVO = false} en la tabla Tecnico).</p>
     *
     * @param nombreUsuario nombre de inicio de sesión
     * @param password      contraseña en texto plano (se verifica contra el hash en BD)
     * @return el {@link com.reparaciones.models.Usuario} autenticado,
     *         o {@code null} si las credenciales son incorrectas o la cuenta está inactiva
     * @throws SQLException si falla la consulta a BD
     */
    public Usuario login(String nombreUsuario, String password) throws SQLException {
        String sql = """
                SELECT u.ID_USU, u.NOMBRE_USUARIO, u.PASSWORD, u.ROL, u.ID_TEC,
                       t.ACTIVO
                FROM Usuario u
                LEFT JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
                WHERE u.NOMBRE_USUARIO = ?
                """;
        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("PASSWORD");
                if (BCrypt.checkpw(password, hash)) {
                    boolean activo = rs.getBoolean("ACTIVO");
                    if (!activo) return null; // técnico desactivado — login denegado
                    int idUsu    = rs.getInt("ID_USU");
                    String rol   = rs.getString("ROL");
                    int idTecRaw = rs.getInt("ID_TEC");
                    Integer idTec = rs.wasNull() ? null : idTecRaw;
                    return new Usuario(idUsu, nombreUsuario, rol, idTec);
                }
            }
        }
        return null;
    }

    /**
     * Devuelve todos los usuarios con rol {@code TECNICO} para la tabla de gestión.
     * <p>Hace JOIN con {@code Tecnico} para incluir nombre visible y estado activo/inactivo.</p>
     *
     * @return lista de técnicos ordenada alfabéticamente por nombre
     * @throws SQLException si falla la consulta
     */
    public List<Usuario> getUsuariosTecnicos() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        String sql = """
                SELECT u.ID_USU, u.NOMBRE_USUARIO, u.ROL, u.ID_TEC,
                       t.NOMBRE AS nombre_tecnico, t.ACTIVO
                FROM Usuario u
                JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
                WHERE u.ROL = 'TECNICO'
                ORDER BY t.NOMBRE
                """;
        try (Connection con = Conexion.getConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new Usuario(
                        rs.getInt("ID_USU"),
                        rs.getString("NOMBRE_USUARIO"),
                        rs.getString("ROL"),
                        rs.getInt("ID_TEC"),
                        rs.getString("nombre_tecnico"),
                        rs.getBoolean("ACTIVO")));
            }
        }
        return lista;
    }

    /**
     * Desactiva la cuenta del técnico ({@code ACTIVO = false}).
     * <p>El técnico deja de aparecer en el gráfico de estadísticas y no puede
     * iniciar sesión, pero sus reparaciones históricas se conservan intactas.</p>
     *
     * @param idTec ID del técnico a desactivar
     * @throws SQLException si falla el update
     */
    public void desactivarTecnico(int idTec) throws SQLException {
        String sql = "UPDATE Tecnico SET ACTIVO = FALSE WHERE ID_TEC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idTec);
            ps.executeUpdate();
        }
    }

    /**
     * Reactiva la cuenta del técnico ({@code ACTIVO = true}).
     *
     * @param idTec ID del técnico a reactivar
     * @throws SQLException si falla el update
     */
    public void activarTecnico(int idTec) throws SQLException {
        String sql = "UPDATE Tecnico SET ACTIVO = TRUE WHERE ID_TEC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idTec);
            ps.executeUpdate();
        }
    }

    /**
     * Comprueba si el técnico tiene alguna reparación asociada (activa o histórica).
     * <p>Si devuelve {@code true}, el técnico no puede eliminarse definitivamente;
     * solo puede desactivarse.</p>
     *
     * @param idTec ID del técnico a comprobar
     * @return {@code true} si existe al menos una reparación para este técnico
     * @throws SQLException si falla la consulta
     */
    public boolean tieneReparaciones(int idTec) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Reparacion WHERE ID_TEC = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idTec);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Elimina completamente el técnico: borra {@code Usuario} y {@code Tecnico}
     * en una sola transacción.
     * <p>Solo llamar cuando {@link #tieneReparaciones(int)} devuelve {@code false};
     * de lo contrario usar {@link #desactivarTecnico(int)}.</p>
     *
     * @param idUsu ID del usuario a eliminar
     * @param idTec ID del técnico a eliminar
     * @throws SQLException si falla alguno de los deletes (se hace rollback automático)
     */
    public void eliminarTecnico(int idUsu, int idTec) throws SQLException {
        String sqlUsu = "DELETE FROM Usuario WHERE ID_USU = ?";
        String sqlTec = "DELETE FROM Tecnico WHERE ID_TEC = ?";
        try (Connection con = Conexion.getConexion()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sqlUsu)) {
                    ps.setInt(1, idUsu);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(sqlTec)) {
                    ps.setInt(1, idTec);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Registra un nuevo técnico: inserta en {@code Tecnico} y en {@code Usuario}
     * en una sola transacción. La contraseña se hashea con BCrypt antes de persistir.
     * <p>Si falla cualquiera de los dos inserts se hace rollback de ambos.</p>
     *
     * @param nombreTecnico  nombre visible del técnico
     * @param nombreUsuario  nombre de inicio de sesión
     * @param password       contraseña en texto plano (se hashea antes de persistir)
     * @throws SQLException si el nombre de usuario ya existe o falla la transacción
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