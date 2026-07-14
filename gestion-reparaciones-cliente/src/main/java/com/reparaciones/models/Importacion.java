package com.reparaciones.models;

import java.math.BigDecimal;
import java.util.List;

/** DTOs del alta en bloque del importador (espejo exacto de ImportacionRequest del servidor). */
public final class Importacion {

    private Importacion() {}

    public record TelefonoImport(String imei, String modelo, Integer storageGb, String color,
                                 String gradoProveedor, BigDecimal precioCompra, String divisa,
                                 BigDecimal precioCompraEur) {}

    public record LoteImport(String batchNumber, int idProv, String nota, List<TelefonoImport> telefonos) {}

    public record Request(List<LoteImport> lotes) {}

    public record Respuesta(int lotes, int telefonos, List<String> conflictosOmitidos) {}
}
