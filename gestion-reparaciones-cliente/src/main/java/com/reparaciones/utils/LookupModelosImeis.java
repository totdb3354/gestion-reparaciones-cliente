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
    /** Señal de parada cooperativa (spec: cerrar el diálogo debe frenar la cola sin interrumpir el hilo). */
    private volatile boolean detenida;

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
            if (vistos.add(imei)) {
                descartados.remove(imei); // re-encolado tras un descarte anterior: ya no cuenta como descartado
                pendientes.addLast(imei);
            }
        }
    }

    /** Descarta el IMEI (p. ej. quitado de la lista) y lo libera de {@code vistos} para permitir un futuro re-encolar. */
    public synchronized void descartar(String imei) {
        descartados.add(imei);
        vistos.remove(imei);
    }

    /** Detiene la cola: {@link #procesarPendientes()} corta en el siguiente ciclo sin interrumpir el hilo. */
    public void detener() { detenida = true; }

    public void procesarPendientes() {
        while (true) {
            if (detenida) return;
            String imei;
            boolean descartado;
            synchronized (this) {
                imei = pendientes.pollFirst();
                descartado = imei != null && descartados.contains(imei);
            }
            if (imei == null) return;
            if (descartado) continue;
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
