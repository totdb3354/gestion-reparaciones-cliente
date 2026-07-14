package com.reparaciones.utils;

import com.reparaciones.models.VerificacionImei;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClasificadorImportacionTest {

    private LoteXlsxParser.Fila fila(String imei, String modelo, String color, String batch, int status) {
        return new LoteXlsxParser.Fila(7, imei, "Apple", modelo, 128, color, null,
                new BigDecimal("100.00"), "Hy5", batch, status);
    }

    private static final Map<String, String> MAPEO = Map.of("iPhone 12", "12");

    @Test void filaNuevaConStatusCeroEsImportable() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "White", "B1", 0)), MAPEO, Map.of(), Map.of());
        assertEquals(1, plan.lotes().size());
        var f = plan.lotes().get(0).filas().get(0);
        assertEquals(ClasificadorImportacion.Destino.NUEVO, f.destino());
        assertEquals("12", f.modeloInterno());
        assertTrue(plan.excluidas().isEmpty());
    }

    @Test void agrupaPorBatchNumberUnLotePorCadaUno() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "White", "B1", 0),
                fila("352513424271911", "iPhone 12", "White", "B2", 0),
                fila("352513424271912", "iPhone 12", "White", "B1", 0)), MAPEO, Map.of(), Map.of());
        assertEquals(2, plan.lotes().size());
        assertEquals("B1", plan.lotes().get(0).batchNumber());
        assertEquals(2, plan.lotes().get(0).filas().size());
    }

    @Test void statusDistintoDeCeroNoEntraYSeAvisa() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "White", "B1", 5)), MAPEO, Map.of(), Map.of());
        assertTrue(plan.lotes().isEmpty());
        assertEquals(ClasificadorImportacion.Destino.STATUS_DISTINTO, plan.excluidas().get(0).destino());
    }

    @Test void imeiInvalidoNoEntra() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("123", "iPhone 12", "White", "B1", 0),
                fila(null, "iPhone 12", "White", "B1", 0)), MAPEO, Map.of(), Map.of());
        assertEquals(2, plan.excluidas().size());
        assertEquals(ClasificadorImportacion.Destino.INVALIDO, plan.excluidas().get(0).destino());
    }

    @Test void sinBatchNumberEsInvalido() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "White", null, 0)), MAPEO, Map.of(), Map.of());
        assertEquals(ClasificadorImportacion.Destino.INVALIDO, plan.excluidas().get(0).destino());
    }

    @Test void imeiRepetidoEnElFicheroSoloEntraLaPrimera() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "White", "B1", 0),
                fila("352513424271910", "iPhone 12", "White", "B1", 0)), MAPEO, Map.of(), Map.of());
        assertEquals(1, plan.lotes().get(0).filas().size());
        assertEquals(ClasificadorImportacion.Destino.DUPLICADO_FICHERO, plan.excluidas().get(0).destino());
    }

    @Test void modeloSinMapearBloqueaLaFila() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 16 eSIM", "White", "B1", 0)),
                Map.of(), Map.of(), Map.of());
        assertEquals(ClasificadorImportacion.Destino.MODELO_SIN_MAPEAR, plan.excluidas().get(0).destino());
    }

    @Test void imeiActivoEsConflictoYFinalOHistoricoEsReentrada() {
        var verif = Map.of(
                "352513424271910", new VerificacionImei("352513424271910", true, "RECIBIDO", 0, "12"),
                "352513424271911", new VerificacionImei("352513424271911", true, null, 2, "12"),
                "352513424271912", new VerificacionImei("352513424271912", true, null, 0, "12"),
                "352513424271913", new VerificacionImei("352513424271913", true, "ENVIADO", 0, "12"));
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "White", "B1", 0),
                fila("352513424271911", "iPhone 12", "White", "B1", 0),
                fila("352513424271912", "iPhone 12", "White", "B1", 0),
                fila("352513424271913", "iPhone 12", "White", "B1", 0)), MAPEO, Map.of(), verif);
        assertEquals(2, plan.excluidas().size());   // los dos activos
        assertTrue(plan.excluidas().stream().allMatch(f -> f.destino() == ClasificadorImportacion.Destino.CONFLICTO));
        List<ClasificadorImportacion.FilaClasificada> importables = plan.lotes().get(0).filas();
        assertEquals(2, importables.size());        // histórico sin trabajos + enviado ⇒ re-entradas
        assertTrue(importables.stream().allMatch(f -> f.destino() == ClasificadorImportacion.Destino.REENTRADA));
    }

    @Test void colorReconocidoSeResuelveAlOficial() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "white", "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertEquals("White", plan.lotes().get(0).filas().get(0).colorOficial());
    }

    @Test void colorDesconocidoBloqueaLaFila() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "Blanco Roto", "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertTrue(plan.lotes().isEmpty());
        assertEquals(ClasificadorImportacion.Destino.COLOR_SIN_MAPEAR, plan.excluidas().get(0).destino());
    }

    @Test void colorVacioNoBloquea() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", null, "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertEquals(1, plan.lotes().get(0).filas().size());
        assertNull(plan.lotes().get(0).filas().get(0).colorOficial());
    }

    @Test void capacidadFueraDePaletaAvisaPeroEntra() {
        // helper con storage configurable: fila de 276 GB en un iPhone 12
        var f = new LoteXlsxParser.Fila(7, "352513424271910", "Apple", "iPhone 12", 276, "White", null,
                new java.math.BigDecimal("100.00"), "Hy5", "B1", 0);
        var plan = ClasificadorImportacion.clasificar(List.of(f), MAPEO, Map.of(), Map.of());
        var fc = plan.lotes().get(0).filas().get(0);
        assertEquals(ClasificadorImportacion.Destino.NUEVO, fc.destino());
        assertTrue(fc.detalle().contains("276 GB no es oficial"));
    }
}
