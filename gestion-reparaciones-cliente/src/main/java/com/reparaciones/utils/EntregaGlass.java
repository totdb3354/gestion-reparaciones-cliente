package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Entrega del teléfono al técnico de glass (spec 2026-08-28): textos de badge, tooltip,
 * opción de menú y CSV. Pura y sin JavaFX para poder testearla; los controladores solo
 * enganchan. Las horas llegan en UTC del servidor y se muestran en hora de Madrid.
 *
 * <p>Fila {@code A…} (técnico de arriba): usa los campos derivados {@code glass*}.
 * Fila {@code AG…} (técnico de glass): usa {@code entregadoAt}/{@code entregadoPorNombre}.</p>
 */
public final class EntregaGlass {

    public static final String COLOR_FONDO = "#E8EAF6";
    public static final String COLOR_TEXTO = "#3949AB";

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FMT_HORA     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private EntregaGlass() {}

    /** Hoy en Madrid (para decidir si el badge lleva fecha). */
    public static LocalDate hoy() { return LocalDate.now(MADRID); }

    /**
     * Texto del badge o {@code null} si no hay entrega (o la fila es de pulido).
     *
     * <p>Fila {@code A…}: {@code "E. <técnico de glass>"} — sin hora, porque la columna
     * Estado mide 100 px y "Entregado dd/MM hh:mm" se cortaba (2026-08-28); la hora y
     * quién entregó van en el {@link #tooltip}. Fila {@code AG…}: {@code "Llegó hh:mm"},
     * con fecha si no es de hoy ({@code hoy == null} ⇒ siempre con fecha).</p>
     */
    public static String textoBadge(ReparacionResumen rep, LocalDate hoy) {
        if (rep == null) return null;
        switch (TipoTrabajo.desde(rep.getIdRep())) {
            case GLASS:
                return texto("Llegó", rep.getEntregadoAt(), hoy);
            case REPARACION:
                if (rep.getGlassEntregadoAt() == null) return null;
                return "E. " + nombre(rep.getGlassTecnicoNombre());
            default:
                return null;
        }
    }

    /** Tooltip del badge o {@code null} si no hay entrega. */
    public static String tooltip(ReparacionResumen rep) {
        if (rep == null) return null;
        switch (TipoTrabajo.desde(rep.getIdRep())) {
            case GLASS:
                if (rep.getEntregadoAt() == null) return null;
                return "Bajado por " + nombre(rep.getEntregadoPorNombre()) + ", "
                        + FechaUtils.formatear(rep.getEntregadoAt(), FMT_DIA_HORA);
            case REPARACION:
                if (rep.getGlassEntregadoAt() == null) return null;
                return "Entregado a " + nombre(rep.getGlassTecnicoNombre()) + " por "
                        + nombre(rep.getGlassEntregadoPorNombre()) + ", "
                        + FechaUtils.formatear(rep.getGlassEntregadoAt(), FMT_DIA_HORA);
            default:
                return null;
        }
    }

    /**
     * Texto de la opción del menú contextual de Mis pendientes, o {@code null} para ocultarla.
     * Solo en la pestaña Reparación, en filas {@code A…} normales con glass abierta.
     */
    public static String opcionMenu(ReparacionResumen rep, boolean pestanaGlass) {
        if (rep == null || pestanaGlass) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return null;
        if (!rep.isGlassAbierta()) return null;
        return rep.getGlassEntregadoAt() != null
                ? "Deshacer entrega"
                : "Entregar a " + nombre(rep.getGlassTecnicoNombre());
    }

    /** Columna "Entregado" del CSV de Asignaciones: fecha completa o vacío. */
    public static String textoCsv(ReparacionResumen rep, DateTimeFormatter fmt) {
        if (rep == null) return "";
        if (TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.PULIDO) return "";
        LocalDateTime at = TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.GLASS
                ? rep.getEntregadoAt() : rep.getGlassEntregadoAt();
        return FechaUtils.formatear(at, fmt);   // "" si null
    }

    /** Colores del badge, para concatenar al estilo base de pastilla. */
    public static String estiloColores() {
        return "-fx-background-color: " + COLOR_FONDO + "; -fx-text-fill: " + COLOR_TEXTO + ";";
    }

    private static String texto(String prefijo, LocalDateTime utc, LocalDate hoy) {
        if (utc == null) return null;
        boolean esHoy = hoy != null && hoy.equals(FechaUtils.toLocalDate(utc));
        return prefijo + " " + FechaUtils.formatear(utc, esHoy ? FMT_HORA : FMT_DIA_HORA);
    }

    private static String nombre(String n) {
        return (n == null || n.isBlank()) ? "glass" : n;
    }
}
