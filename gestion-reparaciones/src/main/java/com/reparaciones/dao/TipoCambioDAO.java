package com.reparaciones.dao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.LocalDate;

/**
 * Acceso a datos de tipos de cambio de divisas.
 * <p>Implementa una caché diaria en la tabla {@code TipoCambio}: la primera
 * petición del día para una divisa concreta consulta la API pública
 * <a href="https://www.frankfurter.app">Frankfurter</a> y guarda el resultado;
 * las peticiones posteriores del mismo día leen directamente de BD.</p>
 *
 * @role ADMIN
 */
public class TipoCambioDAO {

    private static final String API_URL = "https://api.frankfurter.app/latest?from=%s&to=EUR";

    // ─── Obtener tasa (caché por día en BD) ───────────────────────────────────

    /**
     * Devuelve la tasa de conversión divisa→EUR para hoy.
     * <p>Si la tasa ya está en caché (BD) la reutiliza directamente.
     * Si no, consulta la API Frankfurter, la persiste y la devuelve.
     * Para {@code "EUR"} devuelve {@code 1.0} sin ninguna consulta.</p>
     *
     * @param divisa código ISO 4217 de la divisa origen (p. ej. {@code "CNY"})
     * @return tasa de conversión: unidades de {@code divisa} equivalentes a 1 EUR
     * @throws SQLException si falla la BD o la llamada HTTP a la API
     */
    public double getTasa(String divisa) throws SQLException {
        if ("EUR".equalsIgnoreCase(divisa)) return 1.0;

        LocalDate hoy = LocalDate.now();

        // 1. Buscar en caché (BD)
        String sqlSelect = "SELECT TASA FROM TipoCambio WHERE DIVISA = ? AND FECHA = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sqlSelect)) {
            ps.setString(1, divisa.toUpperCase());
            ps.setDate(2, Date.valueOf(hoy));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("TASA");
        }

        // 2. No está en caché — consultar API
        double tasa = consultarApi(divisa);

        // 3. Guardar en BD para el resto del día
        String sqlInsert = """
                INSERT INTO TipoCambio (DIVISA, FECHA, TASA)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE TASA = VALUES(TASA)
                """;
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sqlInsert)) {
            ps.setString(1, divisa.toUpperCase());
            ps.setDate(2, Date.valueOf(hoy));
            ps.setDouble(3, tasa);
            ps.executeUpdate();
        }

        return tasa;
    }

    // ─── Llamada a Frankfurter ────────────────────────────────────────────────

    /**
     * Consulta la tasa divisa→EUR a la API pública Frankfurter.
     * <p>Parsea el JSON manualmente para evitar dependencias externas adicionales.</p>
     *
     * @param divisa código ISO 4217 de la divisa
     * @return tasa de conversión hacia EUR
     * @throws SQLException si la respuesta es inesperada o hay error de red
     */
    private double consultarApi(String divisa) throws SQLException {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, divisa.toUpperCase())))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // Parsear JSON manualmente — evita dependencia extra
            // Respuesta: {"amount":1.0,"base":"USD","date":"2026-04-08","rates":{"EUR":0.923456}}
            String body = response.body();
            int idx = body.indexOf("\"EUR\":");
            if (idx == -1) throw new SQLException("Respuesta inesperada de la API de divisas");
            String resto = body.substring(idx + 6).trim();
            return Double.parseDouble(resto.split("[,}]")[0].trim());

        } catch (IOException | InterruptedException e) {
            throw new SQLException("Error al consultar la API de divisas: " + e.getMessage());
        }
    }
}