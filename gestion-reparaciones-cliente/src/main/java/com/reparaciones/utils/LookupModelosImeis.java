package com.reparaciones.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

/**
 * Cola de detección de modelo por IMEI para el alta manual (spec reubicación
 * 2026-07-16). Síncrona y determinista: el hilo y el sleep real los pone el
 * diálogo. Pacing: espera de 2 s entre lookups (rate limit API 30 req/min).
 */
public class LookupModelosImeis {

    public record Resultado(String imei, String modelo) {}

    public static final long ESPERA_MS = 2000;

    private final Function<String, String> lookup;
    private final LongConsumer espera;
    private final Consumer<Resultado> callback;
    private final Deque<String> pendientes = new ArrayDeque<>();
    private final Set<String> vistos = new HashSet<>();
    private final Set<String> descartados = new HashSet<>();
    private boolean primero = true;

    public LookupModelosImeis(Function<String, String> lookup, LongConsumer espera, Consumer<Resultado> callback) {
        this.lookup = lookup;
        this.espera = espera;
        this.callback = callback;
    }

    /** Precedencia de la spec: manual por fila > detectado (BD/API) > modelo común. */
    public static String modeloParaFila(String manual, String detectado, String comun) {
        if (manual != null && !manual.isBlank()) return manual;
        if (detectado != null && !detectado.isBlank()) return detectado;
        return comun != null && !comun.isBlank() ? comun : null;
    }

    public synchronized void encolar(Collection<String> imeis) {
        for (String imei : imeis) {
            if (vistos.add(imei)) pendientes.addLast(imei);
        }
    }

    public synchronized void descartar(String imei) { descartados.add(imei); }

    public void procesarPendientes() {
        while (true) {
            String imei;
            synchronized (this) {
                imei = pendientes.pollFirst();
            }
            if (imei == null) return;
            if (descartados.contains(imei)) continue;
            if (!primero) espera.accept(ESPERA_MS);
            primero = false;
            String modelo;
            try {
                modelo = lookup.apply(imei);
            } catch (RuntimeException e) {
                modelo = null; // fallo de red/API: la fila cae al modelo común (spec §6)
            }
            callback.accept(new Resultado(imei, modelo));
        }
    }
}
