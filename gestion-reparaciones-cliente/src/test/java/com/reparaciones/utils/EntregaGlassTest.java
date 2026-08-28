package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class EntregaGlassTest {

    /** 2026-08-28 08:42 UTC = 10:42 en Madrid (CEST). */
    private static final LocalDateTime UTC_0842 = LocalDateTime.of(2026, 8, 28, 8, 42);
    private static final LocalDate HOY = LocalDate.of(2026, 8, 28);
    private static final LocalDate MANANA = LocalDate.of(2026, 8, 29);

    private static ReparacionResumen normal(boolean glassAbierta, LocalDateTime entregadoAt) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep("A20260828_1");
        r.setGlassAbierta(glassAbierta);
        r.setGlassEntregadoAt(entregadoAt);
        r.setGlassEntregadoPorNombre(entregadoAt != null ? "Manu" : null);
        r.setGlassTecnicoNombre(glassAbierta ? "Jhona" : null);
        return r;
    }

    private static ReparacionResumen glass(LocalDateTime entregadoAt) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep("AG20260828_3");
        r.setEntregadoAt(entregadoAt);
        r.setEntregadoPorNombre(entregadoAt != null ? "Manu" : null);
        return r;
    }

    // ── Badge ──────────────────────────────────────────────────────────────

    @Test void badgeArribaHoySoloHora() {
        assertEquals("Entregado 10:42", EntregaGlass.textoBadge(normal(true, UTC_0842), HOY));
    }

    @Test void badgeArribaOtroDiaLlevaFecha() {
        assertEquals("Entregado 28/08 10:42", EntregaGlass.textoBadge(normal(true, UTC_0842), MANANA));
    }

    @Test void badgeGlassDiceLlego() {
        assertEquals("Llegó 10:42", EntregaGlass.textoBadge(glass(UTC_0842), HOY));
        assertEquals("Llegó 28/08 10:42", EntregaGlass.textoBadge(glass(UTC_0842), MANANA));
    }

    @Test void sinEntregaNoHayBadge() {
        assertNull(EntregaGlass.textoBadge(normal(true, null), HOY));
        assertNull(EntregaGlass.textoBadge(glass(null), HOY));
    }

    @Test void pulidoNuncaTieneBadge() {
        ReparacionResumen p = new ReparacionResumen();
        p.setIdRep("AP20260828_1");
        p.setEntregadoAt(UTC_0842);
        assertNull(EntregaGlass.textoBadge(p, HOY));
    }

    // ── Tooltip ────────────────────────────────────────────────────────────

    @Test void tooltipArribaDiceAQuienYPorQuien() {
        assertEquals("Entregado a Jhona por Manu, 28/08 10:42", EntregaGlass.tooltip(normal(true, UTC_0842)));
    }

    @Test void tooltipGlassDicePorQuien() {
        assertEquals("Bajado por Manu, 28/08 10:42", EntregaGlass.tooltip(glass(UTC_0842)));
    }

    @Test void tooltipSinEntregaEsNull() {
        assertNull(EntregaGlass.tooltip(normal(true, null)));
    }

    // ── Opción de menú ─────────────────────────────────────────────────────

    @Test void opcionEntregarConGlassAbiertaSinEntrega() {
        assertEquals("Entregar a Jhona", EntregaGlass.opcionMenu(normal(true, null), false));
    }

    @Test void opcionDeshacerConEntrega() {
        assertEquals("Deshacer entrega", EntregaGlass.opcionMenu(normal(true, UTC_0842), false));
    }

    @Test void opcionOcultaSinGlassAbierta() {
        assertNull(EntregaGlass.opcionMenu(normal(false, null), false));
    }

    @Test void opcionOcultaEnPestanaGlassYEnFilasGlass() {
        assertNull(EntregaGlass.opcionMenu(normal(true, null), true));
        assertNull(EntregaGlass.opcionMenu(glass(null), false));
    }

    @Test void opcionOcultaSinFila() {
        assertNull(EntregaGlass.opcionMenu(null, false));
    }

    @Test void nombreVacioCaeEnGlass() {
        ReparacionResumen sinTecnico = normal(true, null);
        sinTecnico.setGlassTecnicoNombre(null);
        assertEquals("Entregar a glass", EntregaGlass.opcionMenu(sinTecnico, false));

        ReparacionResumen conEntrega = normal(true, UTC_0842);
        conEntrega.setGlassTecnicoNombre("");
        conEntrega.setGlassEntregadoPorNombre(null);
        assertEquals("Entregado a glass por glass, 28/08 10:42", EntregaGlass.tooltip(conEntrega));
    }

    // ── CSV ────────────────────────────────────────────────────────────────

    @Test void csvFechaCompletaOVacio() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        assertEquals("28/08/2026 10:42", EntregaGlass.textoCsv(normal(true, UTC_0842), fmt));
        assertEquals("28/08/2026 10:42", EntregaGlass.textoCsv(glass(UTC_0842), fmt));
        assertEquals("", EntregaGlass.textoCsv(normal(true, null), fmt));
    }

    @Test void csvSinFilaEsVacio() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        assertEquals("", EntregaGlass.textoCsv(null, fmt));
        assertEquals("", EntregaGlass.textoCsv(glass(null), fmt));
    }

    @Test void pulidoSinTooltipNiCsv() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        ReparacionResumen p = new ReparacionResumen();
        p.setIdRep("AP20260828_1");
        p.setEntregadoAt(UTC_0842);
        p.setGlassEntregadoAt(UTC_0842);
        assertNull(EntregaGlass.tooltip(p));
        assertEquals("", EntregaGlass.textoCsv(p, fmt));
    }

    @Test void badgeYTooltipSinFilaSonNull() {
        assertNull(EntregaGlass.textoBadge(null, HOY));
        assertNull(EntregaGlass.tooltip(null));
    }

    @Test void hoyNuloUsaFormatoConFecha() {
        assertEquals("Entregado 28/08 10:42", EntregaGlass.textoBadge(normal(true, UTC_0842), null));
    }

    @Test void estiloLlevaLaPaletaIndigo() {
        assertTrue(EntregaGlass.estiloColores().contains("#E8EAF6"));
        assertTrue(EntregaGlass.estiloColores().contains("#3949AB"));
    }
}
