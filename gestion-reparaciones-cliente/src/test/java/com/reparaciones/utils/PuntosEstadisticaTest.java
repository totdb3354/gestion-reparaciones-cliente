package com.reparaciones.utils;

import com.reparaciones.models.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

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
    @Test void promedioSoloTecnicosConActividadYPeriodosVisibles() {
        Map<String, Map<String, Double>> datos = Map.of(
                "Marcos", Map.of("2026-W35", 10.0, "2026-W36", 20.0),
                "Zara",   Map.of("2026-W36", 6.0),
                "Luis",   Map.of());  // sin actividad → no cuenta
        // 2 técnicos activos × 2 periodos; huecos = 0 → (10+20+0+6)/4 = 9,0
        assertEquals(9.0, PuntosEstadistica.promedioVentana(datos, List.of("2026-W35", "2026-W36")), 0.001);
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

    // ── tarjetas ──────────────────────────────────────────────────────────────
    @Test void tarjetasDelEquipoConDelta() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Zara", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Zara", "2026-09", 60));
        // hoy = 15/09/2026 → 11 laborables transcurridos en septiembre
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null);
        assertEquals("septiembre", t.mesLabel());
        assertEquals(110.0, t.puntos(), 0.001);
        assertEquals(10.0, t.puntosDia(), 0.001);            // 110 / 11
        assertEquals(-45.0, t.deltaPuntosPct(), 0.001);      // (110-200)/200
        // agosto 2026: 21 laborables → 200/21 = 9,52; (10 − 9,52)/9,52 = +5,0%
        assertEquals(5.0, t.deltaPuntosDiaPct(), 0.1);
    }

    @Test void tarjetasDeUnTecnicoYSinMesAnterior() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Zara", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Marcos");
        assertEquals(50.0, t.puntos(), 0.001);
        assertNull(t.deltaPuntosPct());
        assertNull(t.deltaPuntosDiaPct());
    }

    private static PuntoEstadisticaPuntos fila(String tec, String periodo, double puntos) {
        return new PuntoEstadisticaPuntos(tec, periodo, puntos, puntos, 0, 0, 1, 0, 0, 0);
    }
}
