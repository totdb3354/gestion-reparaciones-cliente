package com.reparaciones.utils;

import com.reparaciones.models.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PuntosEstadisticaTest {

    // ── periodoAFechas ────────────────────────────────────────────────────────
    @Test void periodoAFechasPorGranularidad() {
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)},
                PuntosEstadistica.periodoAFechas("2026-09-01", "Día"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)},
                PuntosEstadistica.periodoAFechas("2026-W36", "Semana"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)},
                PuntosEstadistica.periodoAFechas("2026-09", "Mes"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)},
                PuntosEstadistica.periodoAFechas("2026", "Año"));
    }

    // ── diasLaborables ────────────────────────────────────────────────────────
    @Test void cuentaLunesAViernes() {
        // Semana 2026-W36: lunes 31/08 → domingo 06/09 → 5 laborables
        assertEquals(5, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)));
        // Septiembre 2026 completo: 22 laborables
        assertEquals(22, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        // Sábado a domingo → 0; rango invertido → 0
        assertEquals(0, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6)));
        assertEquals(0, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 1)));
    }

    // ── puntosDia ─────────────────────────────────────────────────────────────
    @Test void divideEntreLaborablesDelPeriodo() {
        assertEquals(9.6, PuntosEstadistica.puntosDia(48, "2026-W36", "Semana", LocalDate.of(2026, 12, 1)), 0.001);
    }

    @Test void periodoEnCursoDivideSoloPorDiasTranscurridos() {
        // hoy = martes 01/09 dentro de la semana W36 → 2 laborables transcurridos (lun+mar)
        assertEquals(5.0, PuntosEstadistica.puntosDia(10, "2026-W36", "Semana", LocalDate.of(2026, 9, 1)), 0.001);
    }

    @Test void diaNoLaborableDivisorUno() {
        // sábado con trabajo: divisor max(1, 0) = 1
        assertEquals(3.0, PuntosEstadistica.puntosDia(3, "2026-09-05", "Día", LocalDate.of(2026, 12, 1)), 0.001);
    }

    // ── promedioVentana ───────────────────────────────────────────────────────
    @Test void promedioEsPorTecnicoPeriodoTrabajado() {
        Map<String, Map<String, Double>> datos = Map.of(
                "Marcos", Map.of("2026-W35", 10.0, "2026-W36", 20.0),
                "Zara",   Map.of("2026-W36", 6.0),
                "Luis",   Map.of());  // sin actividad → no cuenta
        // 3 técnico-periodos trabajados → (10+20+6)/3 = 12,0 (las ausencias no diluyen)
        assertEquals(12.0, PuntosEstadistica.promedioVentana(datos, List.of("2026-W35", "2026-W36")), 0.001);
    }

    @Test void promedioIgnoraPeriodosFueraDeLaVentana() {
        Map<String, Map<String, Double>> datos = Map.of(
                "Marcos", Map.of("2026-W35", 10.0, "2026-W30", 99.0));
        assertEquals(10.0, PuntosEstadistica.promedioVentana(datos, List.of("2026-W35", "2026-W36")), 0.001);
    }

    @Test void promedioSinActividadEsCero() {
        assertEquals(0.0, PuntosEstadistica.promedioVentana(Map.of(), List.of("2026-W36")), 0.001);
    }

    // ── parsePuntos / formatearPuntos ─────────────────────────────────────────
    @Test void parseAceptaComaYPunto() {
        assertEquals(1.5, PuntosEstadistica.parsePuntos("1,5").orElseThrow(), 0.001);
        assertEquals(1.5, PuntosEstadistica.parsePuntos("1.5").orElseThrow(), 0.001);
        assertEquals(0.0, PuntosEstadistica.parsePuntos("0").orElseThrow(), 0.001);
    }

    @Test void parseRechazaInvalidos() {
        assertTrue(PuntosEstadistica.parsePuntos("-1").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("abc").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("100").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos(null).isEmpty());
    }

    @Test void formateaConComaYUnDecimal() {
        assertEquals("14,5", PuntosEstadistica.formatearPuntos(14.5));
        assertEquals("0,0",  PuntosEstadistica.formatearPuntos(0));
        assertEquals("3,3",  PuntosEstadistica.formatearPuntos(3.25)); // HALF_UP
    }

    @Test void formatoEdicionConservaDosDecimales() {
        assertEquals("0,25", PuntosEstadistica.formatearPuntosEdicion(0.25));
        assertEquals("2,0",  PuntosEstadistica.formatearPuntosEdicion(2.0));
        assertEquals("1,5",  PuntosEstadistica.formatearPuntosEdicion(1.5));
    }

    // ── textos ────────────────────────────────────────────────────────────────
    @Test void tooltipConPuntosYTrabajos() {
        assertEquals("2026-W36\n14,5 puntos · 18 trabajos",
                PuntosEstadistica.textoTooltip("2026-W36", 14.5, false, 18));
        assertEquals("2026-W36\n2,9 puntos/día",
                PuntosEstadistica.textoTooltip("2026-W36", 2.9, true, 18));
    }

    @Test void popoverConDesglose() {
        PuntoEstadisticaPuntos p = new PuntoEstadisticaPuntos("Marcos", "2026-W36",
                14.5, 11.0, 2.0, 1.5, 9, 3, 6, 2);
        String texto = PuntosEstadistica.textoPopover(p);
        assertEquals("14,5 puntos\n9 normales (11,0) · 3 glass (2,0) · 6 pulidos (1,5)\n2 sin piezas",
                texto);
    }

    @Test void popoverOmiteCategoriasVacias() {
        PuntoEstadisticaPuntos p = new PuntoEstadisticaPuntos("Zara", "2026-W36",
                2.0, 2.0, 0, 0, 1, 0, 0, 0);
        assertEquals("2,0 puntos\n1 normales (2,0)", PuntosEstadistica.textoPopover(p));
    }

    // ── etiquetaVentana ──────────────────────────────────────────────────────
    @Test void etiquetaVentanaPorGranularidad() {
        assertEquals("30 días con actividad", PuntosEstadistica.etiquetaVentana(30, "Día"));
        assertEquals("1 día con actividad",   PuntosEstadistica.etiquetaVentana(1, "Día"));
        assertEquals("16 semanas", PuntosEstadistica.etiquetaVentana(16, "Semana"));
        assertEquals("1 semana",   PuntosEstadistica.etiquetaVentana(1, "Semana"));
        assertEquals("12 meses",   PuntosEstadistica.etiquetaVentana(12, "Mes"));
        assertEquals("5 años",     PuntosEstadistica.etiquetaVentana(5, "Año"));
    }

    // ── tarjetas ──────────────────────────────────────────────────────────────
    @Test void tarjetasDelEquipoConObjetivo() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Zara", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Zara", "2026-09", 60));
        // hoy = 15/09/2026 → 11 laborables transcurridos; agosto 2026: 21 laborables
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of());
        assertEquals("septiembre", t.mesLabel());
        assertEquals("agosto", t.mesAnteriorLabel());
        assertEquals(110.0, t.puntos(), 0.001);
        assertEquals(10.0, t.puntosDia(), 0.001);           // 110 / 11
        assertEquals(55, t.pctPuntos());                     // 110/200
        assertEquals(200.0, t.puntosAnterior(), 0.001);
        assertEquals(105, t.pctDia());                       // 10 / (200/21 = 9,52)
        assertEquals(200.0 / 21, t.diaAnterior(), 0.001);
    }

    @Test void tarjetasDeUnTecnicoYSinMesAnterior() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Zara", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Marcos", Set.of());
        assertEquals(50.0, t.puntos(), 0.001);
        assertNull(t.pctPuntos());
        assertNull(t.puntosAnterior());
        assertNull(t.pctDia());
        assertNull(t.diaAnterior());
    }

    @Test void tarjetasEquipoIgnoranALosExcluidos() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Laura", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Laura", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of("Laura"));
        assertEquals(50.0, t.puntos(), 0.001);
        assertEquals(100.0, t.puntosAnterior(), 0.001);
        assertEquals(50, t.pctPuntos());
    }

    @Test void tarjetaPersonalDelExcluidoNoSeFiltra() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Laura", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Laura", Set.of("Laura"));
        assertEquals(60.0, t.puntos(), 0.001);
    }

    @Test void tarjetasConMesActualACeroDanCeroPorCiento() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Marcos", "2026-08", 100));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of());
        assertEquals(0, t.pctPuntos());
        assertEquals(100.0, t.puntosAnterior(), 0.001);
    }

    @Test void tarjetasConMesAnteriorACeroNoDanLinea() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 0), fila("Marcos", "2026-09", 50));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null, Set.of());
        assertNull(t.pctPuntos());
        assertNull(t.puntosAnterior());
        assertNull(t.pctDia());
    }

    @Test void textoObjetivoFormatea() {
        assertEquals("46% de agosto (890,0)", PuntosEstadistica.textoObjetivo(46, "agosto", 890.0));
    }

    // ── sinExcluidos ──────────────────────────────────────────────────────────
    @Test void sinExcluidosFiltraSoloLosExcluidos() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Laura", "2026-09", 10));
        List<PuntoEstadisticaPuntos> resultado = PuntosEstadistica.sinExcluidos(filas, Set.of("Laura"));
        assertEquals(1, resultado.size());
        assertEquals("Marcos", resultado.get(0).getNombreTecnico());
    }

    @Test void sinExcluidosConSetVacioDevuelveLaMismaLista() {
        List<PuntoEstadisticaPuntos> filas = List.of(fila("Marcos", "2026-09", 50));
        assertSame(filas, PuntosEstadistica.sinExcluidos(filas, Set.of()));
    }

    private static PuntoEstadisticaPuntos fila(String tec, String periodo, double puntos) {
        return new PuntoEstadisticaPuntos(tec, periodo, puntos, puntos, 0, 0, 1, 0, 0, 0);
    }
}
