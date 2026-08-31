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

    @Test void badgeArribaDiceAQuienSinHora() {
        // "→ <técnico de glass>": la columna Estado mide 100 px y "Entregado 28/08 10:42"
        // no cabía (2026-08-28). La hora y quién entregó siguen en el tooltip.
        assertEquals("→ Jhona", EntregaGlass.textoBadge(normal(true, UTC_0842), HOY));
    }

    @Test void badgeArribaNoDependeDelDia() {
        assertEquals("→ Jhona", EntregaGlass.textoBadge(normal(true, UTC_0842), MANANA));
        assertEquals("→ Jhona", EntregaGlass.textoBadge(normal(true, UTC_0842), null));
    }

    @Test void badgeGlassDiceLlego() {
        assertEquals("Llegó 10:42", EntregaGlass.textoBadge(glass(UTC_0842), HOY));
        assertEquals("Llegó 28/08", EntregaGlass.textoBadge(glass(UTC_0842), MANANA));   // otro día: solo fecha
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
        assertEquals("→ glass", EntregaGlass.textoBadge(conEntrega, HOY));
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
        assertEquals("Llegó 28/08", EntregaGlass.textoBadge(glass(UTC_0842), null));
    }

    @Test void estiloLlevaLaPaletaIndigo() {
        assertTrue(EntregaGlass.estiloColores().contains("#E8EAF6"));
        assertTrue(EntregaGlass.estiloColores().contains("#3949AB"));
    }

    // ── Sub-etiqueta de historial ─────────────────────────────────────────

    @Test void anadirGlassSeOcultaSoloConNormalAbiertaYSinEntrega() {
        ReparacionResumen g = glass(null);
        g.setNormalAbierta(true);
        assertTrue(EntregaGlass.ocultarAnadirGlass(g));                 // alguien arriba aún no ha entregado
        ReparacionResumen entregada = glass(UTC_0842);
        entregada.setNormalAbierta(true);
        assertFalse(EntregaGlass.ocultarAnadirGlass(entregada));        // ya llegó
        assertFalse(EntregaGlass.ocultarAnadirGlass(glass(null)));      // glass directa, sin normal arriba
        ReparacionResumen normal = normal(true, null);
        normal.setNormalAbierta(true);
        assertFalse(EntregaGlass.ocultarAnadirGlass(normal));           // no es fila de glass
        assertFalse(EntregaGlass.ocultarAnadirGlass(null));
    }

    @Test void subEtiquetaHistorialSoloEnGlassConEntrega() {
        ReparacionResumen g = new ReparacionResumen();
        g.setIdRep("G20260828_64");
        g.setEntregadoAt(UTC_0842);
        g.setEntregadoPorNombre("Manu");
        assertEquals("Llegó 28/08 10:42", EntregaGlass.subEtiquetaHistorial(g));
        assertEquals("Llegó 28/08 10:42", EntregaGlass.subEtiquetaHistorial(glass(UTC_0842)));
        assertNull(EntregaGlass.subEtiquetaHistorial(glass(null)));
        assertNull(EntregaGlass.subEtiquetaHistorial(normal(true, UTC_0842)));
        assertNull(EntregaGlass.subEtiquetaHistorial(null));
    }

    // ── Píldora "Glass: <técnico>" bajo el IMEI ─────────────────────────────

    @Test void etiquetaGlassPendienteSoloEnNormalConGlassSinEntrega() {
        assertEquals("Glass: Jhona", EntregaGlass.etiquetaGlassPendiente(normal(true, null)));
        assertNull(EntregaGlass.etiquetaGlassPendiente(normal(true, UTC_0842)));   // entregada: la cuenta la píldora →
        assertNull(EntregaGlass.etiquetaGlassPendiente(normal(false, null)));      // sin glass
        assertNull(EntregaGlass.etiquetaGlassPendiente(glass(null)));              // fila AG, no aplica
        assertNull(EntregaGlass.etiquetaGlassPendiente(null));
    }

    @Test void contadorAsignadosSeOcultaSoloCuandoUnaPildoraCuentaAlSegundo() {
        assertTrue(EntregaGlass.ocultarContadorAsignados(normal(true, null), 2));      // verde: Glass pendiente
        assertTrue(EntregaGlass.ocultarContadorAsignados(normal(true, UTC_0842), 2));  // indigo: entregada
        ReparacionResumen ag = glass(null);
        ag.setNormalAbierta(true);
        assertTrue(EntregaGlass.ocultarContadorAsignados(ag, 2));                      // azul: Rep abierta
        assertFalse(EntregaGlass.ocultarContadorAsignados(normal(true, null), 3));     // 3+: el contador convive
        assertFalse(EntregaGlass.ocultarContadorAsignados(normal(false, null), 2));    // sin glass
        assertFalse(EntregaGlass.ocultarContadorAsignados(glass(UTC_0842), 2));        // AG sin normal abierta
        assertFalse(EntregaGlass.ocultarContadorAsignados(null, 2));
    }

    @Test void etiquetaRepAbiertaSoloEnGlassConNormalAbierta() {
        ReparacionResumen ag = glass(null);
        ag.setNormalAbierta(true);
        ag.setNormalTecnicoNombre("Manu");
        assertEquals("Rep: Manu", EntregaGlass.etiquetaRepAbierta(ag));
        assertEquals("Reparación abierta de Manu", EntregaGlass.tooltipRepAbierta(ag));

        ReparacionResumen entregada = glass(UTC_0842);
        entregada.setNormalAbierta(true);
        entregada.setNormalTecnicoNombre("Manu");
        assertEquals("Rep: Manu", EntregaGlass.etiquetaRepAbierta(entregada));   // se mantiene tras el "Llegó"

        assertNull(EntregaGlass.etiquetaRepAbierta(glass(null)));                // sin normal abierta
        assertNull(EntregaGlass.etiquetaRepAbierta(normal(true, null)));         // fila A, no aplica
        assertNull(EntregaGlass.etiquetaRepAbierta(null));
        assertNull(EntregaGlass.tooltipRepAbierta(glass(null)));
        assertTrue(EntregaGlass.estiloPildoraRepAbierta().contains(TipoTrabajo.REPARACION.colorFondo()));
        assertTrue(EntregaGlass.estiloPildoraRepAbierta().contains(TipoTrabajo.REPARACION.colorTexto()));
    }

    @Test void tooltipGlassPendienteConFallbackDeNombre() {
        assertEquals("Glass abierta de Jhona — entrega sin registrar",
                EntregaGlass.tooltipGlassPendiente(normal(true, null)));
        ReparacionResumen sinNombre = normal(true, null);
        sinNombre.setGlassTecnicoNombre(null);
        assertEquals("Glass abierta de glass — entrega sin registrar",
                EntregaGlass.tooltipGlassPendiente(sinNombre));
        assertNull(EntregaGlass.tooltipGlassPendiente(normal(true, UTC_0842)));
        assertNull(EntregaGlass.tooltipGlassPendiente(null));
    }

    @Test void estiloPildoraGlassPendienteUsaLaPaletaGlass() {
        assertTrue(EntregaGlass.estiloPildoraGlassPendiente().contains(TipoTrabajo.GLASS.colorFondo()));
        assertTrue(EntregaGlass.estiloPildoraGlassPendiente().contains(TipoTrabajo.GLASS.colorTexto()));
    }
}
