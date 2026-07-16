package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LookupModelosImeisTest {

    @Test void precedenciaManualDetectadoComun() {
        assertEquals("13pro", LookupModelosImeis.modeloParaFila("13pro", "12", "15"));
        assertEquals("12", LookupModelosImeis.modeloParaFila(null, "12", "15"));
        assertEquals("15", LookupModelosImeis.modeloParaFila(null, null, "15"));
        assertEquals("15", LookupModelosImeis.modeloParaFila("  ", null, "15"));
        assertNull(LookupModelosImeis.modeloParaFila(null, null, null));
    }

    @Test void colaResuelveEnOrdenConEsperaEntreLookups() {
        List<String> consultados = new ArrayList<>();
        List<Long> esperas = new ArrayList<>();
        List<LookupModelosImeis.Resultado> resultados = new ArrayList<>();
        var cola = new LookupModelosImeis(
                imei -> { consultados.add(imei); return Map.of("111", "12", "222", "13").get(imei); },
                esperas::add, resultados::add);
        cola.encolar(List.of("111", "222", "333"));
        cola.procesarPendientes();
        assertEquals(List.of("111", "222", "333"), consultados);
        assertEquals(2, esperas.size()); // no espera antes del primero
        assertEquals("12", resultados.get(0).modelo());
        assertNull(resultados.get(2).modelo()); // 333 sin resolver → callback con null
    }

    @Test void dedupYDescartes() {
        List<String> consultados = new ArrayList<>();
        var cola = new LookupModelosImeis(i -> { consultados.add(i); return null; }, e -> {}, r -> {});
        cola.encolar(List.of("111", "111", "222"));
        cola.descartar("222");
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados);
        cola.encolar(List.of("111")); // ya consultado: no se re-consulta
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados);
    }

    @Test void descartarLiberaParaReEncolar() {
        List<String> consultados = new ArrayList<>();
        List<LookupModelosImeis.Resultado> resultados = new ArrayList<>();
        var cola = new LookupModelosImeis(i -> { consultados.add(i); return "12"; }, e -> {}, resultados::add);
        cola.encolar(List.of("111"));
        cola.descartar("111");
        cola.procesarPendientes();
        assertEquals(List.of(), consultados); // descartado antes de procesar: no se consulta

        cola.encolar(List.of("111")); // re-añadido tras quitarlo de la lista: debe volver a considerarse
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados);
        assertEquals(1, resultados.size());
        assertEquals("12", resultados.get(0).modelo());
    }

    @Test void detenerCortaLaColaSinConsultarElResto() {
        List<String> consultados = new ArrayList<>();
        final LookupModelosImeis[] ref = new LookupModelosImeis[1];
        var cola = new LookupModelosImeis(i -> { consultados.add(i); return null; }, e -> {},
                r -> ref[0].detener());
        ref[0] = cola;
        cola.encolar(List.of("111", "222", "333"));
        cola.procesarPendientes();
        assertEquals(List.of("111"), consultados); // se detiene tras el primer callback: el resto no se consulta
    }
}
