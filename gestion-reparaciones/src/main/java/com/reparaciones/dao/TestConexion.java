package com.reparaciones.dao;

import java.sql.Connection;
import java.sql.SQLException;

public class TestConexion {

    public static void main(String[] args) {
        try {
            Connection con = Conexion.getConexion();
            System.out.println("Conexión exitosa: " + con);
            con.close();
        } catch (SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
        }
    }
}
