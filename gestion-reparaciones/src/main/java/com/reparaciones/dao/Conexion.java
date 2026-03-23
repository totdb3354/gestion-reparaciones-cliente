package com.reparaciones.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexion {

    private static final String URL      = "jdbc:mysql://127.0.0.1:3306/reparaciones";
    private static final String USUARIO  = "root";
    private static final String PASSWORD = "2017";

    public static Connection getConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, PASSWORD);
    }
}