package com.reparaciones.utils;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Estado global de conexión con el servidor.
 * <p>Fuente de verdad síncrona en {@code desconectado} (volatile, legible sin JavaFX para
 * la lógica de delay y los tests). La {@link #desconectadoProperty()} es un espejo para
 * enlazar (bind) el banner; se actualiza siempre en el hilo de JavaFX.</p>
 * <p>{@code enRefresco} marca que estamos dentro de un refresco de fondo (poll): los catch
 * de refresco usan esta marca para no abrir un modal por {@link ConexionException} (lo
 * indica el banner). Solo se toca en el hilo de JavaFX (single-thread), así que un boolean
 * simple basta.</p>
 */
public final class ConexionEstado {

    private static volatile boolean desconectado = false;
    private static final BooleanProperty prop = new SimpleBooleanProperty(false);
    private static boolean enRefresco = false;

    private ConexionEstado() {}

    public static void reportarExito() { set(false); }
    public static void reportarFallo() { set(true); }

    public static boolean isDesconectado() { return desconectado; }
    public static BooleanProperty desconectadoProperty() { return prop; }

    /** 5 s mientras está desconectado (reintento rápido); 60 s en estado normal. */
    public static long delaySegundos() { return desconectado ? 5 : 60; }

    public static void enRefresco(boolean v) { enRefresco = v; }
    public static boolean enRefresco() { return enRefresco; }

    private static void set(boolean v) {
        desconectado = v;                       // fuente de verdad, inmediata
        try {
            if (Platform.isFxApplicationThread()) prop.set(v);
            else Platform.runLater(() -> prop.set(v));
        } catch (IllegalStateException toolkitNoIniciado) {
            // p. ej. en tests sin JavaFX arrancado: el boolean (fuente de verdad) ya está puesto
        }
    }
}
