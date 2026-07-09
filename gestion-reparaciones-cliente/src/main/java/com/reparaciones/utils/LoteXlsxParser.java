package com.reparaciones.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de la plantilla xlsx de la otra plataforma de stock (spec F2 §1.2).
 * Columnas por posición fija A–N, cabeceras en filas 5–6, datos desde la fila 7.
 * SOLO extrae: la validación de negocio (status, duplicados, mapeo de modelos)
 * la hace {@link ClasificadorImportacion}.
 */
public final class LoteXlsxParser {

    private LoteXlsxParser() {}

    public record Fila(int numFila, String imei, String fabricante, String modeloTexto,
                       Integer storageGb, String color, String grado, BigDecimal precioCompra,
                       String proveedorNombre, String batchNumber, Integer status) {}

    public record Resultado(List<Fila> filas, List<String> avisos) {}

    private static final int PRIMERA_FILA_DATOS = 6;  // fila 7 de Excel

    public static Resultado parsear(InputStream in) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);   // la hoja se llama "Лист1": SIEMPRE por índice
            validarPlantilla(hoja);
            List<Fila> filas = new ArrayList<>();
            List<String> avisos = new ArrayList<>();
            for (int i = PRIMERA_FILA_DATOS; i <= hoja.getLastRowNum(); i++) {
                Row row = hoja.getRow(i);
                if (row == null) continue;
                String imei   = texto(row.getCell(0));
                String modelo = texto(row.getCell(2));
                if ((imei == null || imei.isBlank()) && (modelo == null || modelo.isBlank())) continue;
                Integer status = entero(row.getCell(13));
                filas.add(new Fila(i + 1, imei, texto(row.getCell(1)), modelo,
                        entero(row.getCell(3)), texto(row.getCell(4)), texto(row.getCell(5)),
                        decimal(row.getCell(6)), texto(row.getCell(7)), texto(row.getCell(8)),
                        status == null ? 0 : status));
            }
            return new Resultado(filas, avisos);
        }
    }

    private static void validarPlantilla(Sheet hoja) throws IOException {
        Row r5 = hoja.getRow(4);
        String a5 = r5 == null ? null : texto(r5.getCell(0));
        String i5 = r5 == null ? null : texto(r5.getCell(8));
        if (a5 == null || !a5.startsWith("IMEI/SN") || !"Batch Number".equals(i5)) {
            throw new IOException("El fichero no parece la plantilla de lotes: faltan las cabeceras" +
                    " \"IMEI/SN\" (A5) y \"Batch Number\" (I5).");
        }
    }

    /** Texto de una celda que puede ser STRING o NUMERIC (un IMEI de 15 dígitos cabe exacto en double). */
    private static String texto(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.STRING) {
            String s = c.getStringCellValue().trim();
            return s.isEmpty() ? null : s;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            double v = c.getNumericCellValue();
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        return null;
    }

    private static Integer entero(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
        String s = texto(c);
        if (s == null) return null;
        try { return Integer.parseInt(s.replaceAll("\\D", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal decimal(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        String s = texto(c);
        if (s == null) return null;
        try { return new BigDecimal(s.replace(',', '.')).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return null; }
    }
}
