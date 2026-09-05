package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import com.reparaciones.models.Tecnico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Elección del técnico de la glass automática (spec 2026-09-05-glass-prediccion, §4). El helper no recibe
 *  el día de la semana: compara fracciones de 9h sin escalar, así que un sábado reparte igual que un martes. */
class PrediccionGlassTest {

    private static final String IMEI = "111111111111111";

    private static Tecnico tec(int id, String nombre, boolean activo, boolean esGlass) {
        return new Tecnico(id, nombre, activo, true, esGlass);
    }

    /** Asignación con los campos que usa el cálculo; el prefijo de idRep fija el tipo (A = normal, AG = glass, G = glass cerrada). */
    private static ReparacionResumen asig(String idRep, String imei, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null);
        r.setCliente(cliente);
        return r;
    }

    private static final Tecnico JAVI  = tec(1, "javi",  true, true);
    private static final Tecnico JHONA = tec(2, "jhona", true, true);
    private static final Tecnico MANU  = tec(3, "manu",  true, false);   // no habilitado

    @Test void sinHabilitadosDevuelveNull() {
        assertNull(PrediccionGlass.elegir(List.of(MANU), List.of(), List.of(), List.of(), IMEI, true));
        assertNull(PrediccionGlass.elegir(List.of(), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void inactivoConFlagQuedaFuera() {
        Tecnico baja = tec(4, "aaron", false, true);   // iría primero por alfabeto si contara
        assertSame(JAVI, PrediccionGlass.elegir(List.of(baja, JAVI), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void empateAlfabeticoSinDistinguirMayusculas() {
        Tecnico zoe = tec(5, "Zoe", true, true);
        Tecnico ana = tec(6, "ana", true, true);
        assertSame(ana, PrediccionGlass.elegir(List.of(zoe, ana), List.of(), List.of(), List.of(), IMEI, true));
    }

    @Test void conGlassAbiertaDeEseImeiEnBdQuedaFuera() {
        List<ReparacionResumen> abiertas = List.of(asig("AG1", IMEI, 1, "WEB"));   // javi ya tiene glass de ese IMEI
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void unaReparacionNormalDelMismoImeiNoExcluye() {
        // javi: normal del mismo IMEI; jhona: normal de otro IMEI → misma carga (1/25) → empate → javi por alfabeto
        List<ReparacionResumen> abiertas = List.of(asig("A1", IMEI, 1, "WEB"), asig("A2", "222222222222222", 2, "WEB"));
        assertSame(JAVI, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void conGlassVerdeDeEseImeiEnElModalQuedaFuera() {
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal(IMEI, 1, true));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));
    }

    @Test void menorCargaGana() {
        List<ReparacionResumen> abiertas = List.of(
                asig("AG1", "222222222222222", 1, "WEB"), asig("AG2", "333333333333333", 1, "WEB"),   // javi: 2 glass
                asig("AG3", "444444444444444", 2, "WEB"));                                            // jhona: 1 glass
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void loCerradoHoyTambienCuenta() {
        List<ReparacionResumen> cerradas = List.of(asig("G1", "222222222222222", 1, "WEB"), asig("G2", "333333333333333", 1, "WEB"));
        List<ReparacionResumen> abiertas = List.of(asig("AG3", "444444444444444", 2, "WEB"));
        // javi 2/17 hecho hoy > jhona 1/17 pendiente
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, cerradas, List.of(), IMEI, true));
    }

    @Test void lasVerdesDelModalDesplazanLaEleccion() {
        // Sin verdes empatan a 0 → javi por alfabeto; con una verde ya a javi → jhona
        assertSame(JAVI, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), List.of(), IMEI, true));
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("222222222222222", 1, true));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));
    }

    @Test void imeiConClienteMiraSoloPedidosYSinClienteMiraTotal() {
        // javi: 3 glass de stock (sin cliente); jhona: 1 glass con cliente
        List<ReparacionResumen> abiertas = List.of(
                asig("AG1", "222222222222222", 1, null), asig("AG2", "333333333333333", 1, null), asig("AG3", "444444444444444", 1, null),
                asig("AG4", "555555555555555", 2, "WEB"));
        assertSame(JAVI,  PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));    // Pedidos: javi 0 < jhona 1/17
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, false));   // Total: jhona 1/17 < javi 3/17
    }

    @Test void verdeDelModalSinClienteSoloCuentaEnTotal() {
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("222222222222222", 1, false));
        assertSame(JAVI,  PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, true));    // ignorada → empate → javi
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), List.of(), List.of(), verdes, IMEI, false));   // cuenta → jhona
    }

    @Test void laReparacionNormalDeUnHabilitadoMixtoCuenta() {
        // javi: 10 normales (10/25 = 0,40); jhona: 3 glass (3/17 ≈ 0,18) → jhona
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) abiertas.add(asig("A" + i, "22222222222222" + i, 1, "WEB"));
        for (int i = 0; i < 3; i++)  abiertas.add(asig("AG" + i, "33333333333333" + i, 2, "WEB"));
        assertSame(JHONA, PrediccionGlass.elegir(List.of(JAVI, JHONA), abiertas, List.of(), List.of(), IMEI, true));
    }

    @Test void cargaSumaFraccionesCrudasDe9h() {
        List<ReparacionResumen> abiertas = List.of(asig("AG1", "222222222222222", 1, "WEB"), asig("A2", "333333333333333", 1, "WEB"));
        List<PrediccionGlass.GlassEnModal> verdes = List.of(new PrediccionGlass.GlassEnModal("444444444444444", 1, true));
        assertEquals(2.0 / CargaTecnicos.TOPE_GLASS_9H + 1.0 / CargaTecnicos.TOPE_NORMALES_9H,
                PrediccionGlass.carga(1, abiertas, List.of(), verdes, true), 1e-12);
    }
}
