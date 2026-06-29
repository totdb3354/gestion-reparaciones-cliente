package com.reparaciones.utils;

import javafx.application.Platform;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Auto-reprogramación del refresco de fondo: ejecuta {@code tareaFx} en el hilo de JavaFX
 * marcando {@link ConexionEstado#enRefresco(boolean)} alrededor, y se reprograma con
 * {@link ConexionEstado#delaySegundos()} (60 s normal, 5 s desconectado). Se detiene solo
 * cuando el executor se cierra ({@code shutdownNow} en {@code detenerPolling}).
 */
public final class Poller {

    private Poller() {}

    public static void programarSiguiente(ScheduledExecutorService exec, Runnable tareaFx) {
        try {
            exec.schedule(() -> Platform.runLater(() -> {
                ConexionEstado.enRefresco(true);
                try {
                    tareaFx.run();
                } finally {
                    ConexionEstado.enRefresco(false);
                    programarSiguiente(exec, tareaFx);   // reprograma con el estado ya actualizado
                }
            }), ConexionEstado.delaySegundos(), TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // el executor se cerró (detenerPolling): dejar de reprogramar
        }
    }
}
