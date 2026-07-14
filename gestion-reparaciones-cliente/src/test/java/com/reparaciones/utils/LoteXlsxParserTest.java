package com.reparaciones.utils;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoteXlsxParserTest {

    /** Libro con la estructura real de la plantilla: cabeceras en filas 5-6, datos desde la 7. */
    private InputStream libro(Object[]... filasDatos) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Лист1");
            Row r5 = sh.createRow(4);
            r5.createCell(0).setCellValue("IMEI/SN *");
            r5.createCell(5).setCellValue("Grade");
            r5.createCell(6).setCellValue("Purchase price");
            r5.createCell(7).setCellValue("Supplier Name");
            r5.createCell(8).setCellValue("Batch Number");
            r5.createCell(13).setCellValue("Status");
            Row r6 = sh.createRow(5);
            r6.createCell(1).setCellValue("Manufacturer");
            r6.createCell(2).setCellValue("Model");
            r6.createCell(3).setCellValue("Storage");
            r6.createCell(4).setCellValue("Color");
            int n = 6;
            for (Object[] fila : filasDatos) {
                Row r = sh.createRow(n++);
                for (int c = 0; c < fila.length; c++) {
                    if (fila[c] == null) continue;
                    if (fila[c] instanceof Number num) r.createCell(c).setCellValue(num.doubleValue());
                    else r.createCell(c).setCellValue(fila[c].toString());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test void parseaFilaCompletaConCeldasNumericas() throws IOException {
        // IMEI y storage numéricos y precio con muchos decimales, como el fichero real de Hy5
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", 128, "White", null,
                         152.24622210318043, "Hy5", "1445947", "iPhones", null, null, null, 0}));
        assertEquals(1, res.filas().size());
        LoteXlsxParser.Fila f = res.filas().get(0);
        assertEquals(7, f.numFila());
        assertEquals("352513424271910", f.imei());
        assertEquals("Apple", f.fabricante());
        assertEquals("iPhone 12", f.modeloTexto());
        assertEquals(128, f.storageGb());
        assertEquals("White", f.color());
        assertNull(f.grado());
        assertEquals(new BigDecimal("152.25"), f.precioCompra());
        assertEquals("Hy5", f.proveedorNombre());
        assertEquals("1445947", f.batchNumber());
        assertEquals(0, f.status());
    }

    @Test void parseaCeldasDeTexto() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{"352513424271910", "Apple", "iPhone 12 mini", "256", "Black", "A",
                         "117.50", "Hy5", "1445948", null, null, null, null, "5"}));
        LoteXlsxParser.Fila f = res.filas().get(0);
        assertEquals("352513424271910", f.imei());
        assertEquals(256, f.storageGb());
        assertEquals("A", f.grado());
        assertEquals(new BigDecimal("117.50"), f.precioCompra());
        assertEquals(5, f.status());
    }

    @Test void ignoraFilasVacias() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1", null, null, null, null, 0},
            new Object[]{},   // fila totalmente vacía
            new Object[]{352513424271911L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1", null, null, null, null, 0}));
        assertEquals(2, res.filas().size());
        assertEquals(List.of(), res.avisos());
    }

    @Test void statusVacioSeInterpretaComoCero() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1"}));
        assertEquals(0, res.filas().get(0).status());
    }

    @Test void rechazaUnLibroQueNoEsLaPlantilla() {
        assertThrows(IOException.class, () -> {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                wb.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("cualquier cosa");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                LoteXlsxParser.parsear(new ByteArrayInputStream(out.toByteArray()));
            }
        });
    }
}
