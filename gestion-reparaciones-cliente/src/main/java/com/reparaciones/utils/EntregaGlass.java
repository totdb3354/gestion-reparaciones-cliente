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
    private static final DateTimeFormatter FMT_DIA      = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FMT_DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private EntregaGlass() {}

    /** Hoy en Madrid (para decidir si el badge lleva fecha). */
    public static LocalDate hoy() { return LocalDate.now(MADRID); }

    /**
     * Texto del badge o {@code null} si no hay entrega (o la fila es de pulido).
     *
     * <p>Fila {@code A…}: {@code "→ <técnico de glass>"} — sin hora, porque la columna
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
                return "→ " + nombre(rep.getGlassTecnicoNombre());
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
     * Solo pestaña Reparación, filas {@code A…} con glass abierta. "Deshacer entrega" exige la
     * firma: solo quien registró la entrega ({@code glassEntregadoPor}) puede deshacerla
     * (decisión 2026-08-31); re-entregar sobrescribe hora y firma.
     */
    public static String opcionMenu(ReparacionResumen rep, boolean pestanaGlass, Integer idTecSesion) {
        if (rep == null || pestanaGlass) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return null;
        if (!rep.isGlassAbierta()) return null;
        if (rep.getGlassEntregadoAt() == null) return "Entregar a " + nombre(rep.getGlassTecnicoNombre());
        return (idTecSesion != null && idTecSesion.equals(rep.getGlassEntregadoPor()))
                ? "Deshacer entrega" : null;
    }

    /** "Deshacer llegada": pestaña Glass, fila AG con entrega y firma del propio técnico. */
    public static String opcionDeshacerLlegada(ReparacionResumen rep, boolean pestanaGlass, Integer idTecSesion) {
        if (rep == null || !pestanaGlass) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return null;
        if (rep.getEntregadoAt() == null) return null;
        return (idTecSesion != null && idTecSesion.equals(rep.getEntregadoPor()))
                ? "Deshacer llegada" : null;
    }

    /** Columna "Entregado" del CSV de Asignaciones: fecha completa o vacío. */
    public static String textoCsv(ReparacionResumen rep, DateTimeFormatter fmt) {
        if (rep == null) return "";
        if (TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.PULIDO) return "";
        LocalDateTime at = TipoTrabajo.desde(rep.getIdRep()) == TipoTrabajo.GLASS
                ? rep.getEntregadoAt() : rep.getGlassEntregadoAt();
        return FechaUtils.formatear(at, fmt);   // "" si null
    }

    /**
     * Sub-etiqueta bajo el nombre del reparador en las vistas de historial (Agrupado por IMEI,
     * Historial): solo filas de glass (AG/G) con entrega, siempre con día y hora porque en el
     * historial las "Fechas" de una G son las de completar, no las de asignar. Tooltip: {@link #tooltip}.
     */
    public static String subEtiquetaHistorial(ReparacionResumen rep) {
        if (rep == null || rep.getEntregadoAt() == null) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return null;
        return "Llegó " + FechaUtils.formatear(rep.getEntregadoAt(), FMT_DIA_HORA);
    }

    /**
     * Sin teléfono no hay glass (decisión 2026-08-28): el botón "Añadir glass" se oculta mientras haya
     * una reparación normal abierta en el IMEI y esta glass no tenga entrega. Sin normal abierta
     * (glass directa, o normal ya completada sin marcar) no se bloquea a nadie.
     */
    public static boolean ocultarAnadirGlass(ReparacionResumen rep) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return false;
        return rep.isNormalAbierta() && rep.getEntregadoAt() == null;
    }

    /**
     * "Marcar que llegó": válvula de escape del gate — visible solo en la pestaña Glass, en la
     * fila bloqueada (normal abierta y sin entrega). La firma el propio técnico de glass.
     */
    public static boolean mostrarMarcarLlegada(ReparacionResumen rep, boolean pestanaGlass) {
        return pestanaGlass && ocultarAnadirGlass(rep);
    }

    /**
     * Píldora bajo el IMEI de la reparación normal mientras la glass del IMEI no tenga entrega
     * registrada: "Glass: <dueño actual>". Texto neutro a propósito: el teléfono puede estar
     * arriba o ya abajo (abierto y repartido allí); solo dice de quién es la glass. Al entregar
     * → null (la píldora índigo "→ …" de Estado toma el relevo).
     */
    public static String etiquetaGlassPendiente(ReparacionResumen rep) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return null;
        if (!rep.isGlassAbierta() || rep.getGlassEntregadoAt() != null) return null;
        return "Glass: " + nombre(rep.getGlassTecnicoNombre());
    }

    /**
     * El "N asignados" de la vista Asignaciones sobra cuando una píldora ya cuenta quién es el
     * segundo: fila normal con glass abierta (verde si pendiente, índigo si entregada) o fila de
     * glass con la normal abierta (azul), siempre con exactamente 2 asignados. Con 3+ el contador
     * vuelve a aportar y convive con la píldora.
     */
    public static boolean ocultarContadorAsignados(ReparacionResumen rep, int n) {
        if (rep == null || n != 2) return false;
        switch (TipoTrabajo.desde(rep.getIdRep())) {
            case REPARACION: return rep.isGlassAbierta();
            case GLASS:      return rep.isNormalAbierta();
            default:         return false;
        }
    }

    /**
     * Píldora bajo el IMEI de la fila de glass mientras la reparación normal del IMEI siga
     * abierta: "Rep: <dueño de la normal más antigua>". Se mantiene tras el "Llegó": al de
     * glass le dice a quién devolver el teléfono para ensamblar. Desaparece sola al cerrarse
     * la normal (los derivados solo cuentan abiertas).
     */
    public static String etiquetaRepAbierta(ReparacionResumen rep) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return null;
        if (!rep.isNormalAbierta()) return null;
        return "Rep: " + nombreRep(rep.getNormalTecnicoNombre());
    }

    /** Tooltip de la píldora "Rep: …". */
    public static String tooltipRepAbierta(ReparacionResumen rep) {
        if (etiquetaRepAbierta(rep) == null) return null;
        return "Reparación abierta de " + nombreRep(rep.getNormalTecnicoNombre());
    }

    /** Estilo completo de la mini-píldora "Rep: …" (paleta del tipo Reparación). */
    public static String estiloPildoraRepAbierta() {
        return "-fx-background-radius: 8; -fx-padding: 1 8 1 8; -fx-font-size: 10px; -fx-font-weight: bold;"
             + "-fx-background-color: " + TipoTrabajo.REPARACION.colorFondo() + "; -fx-text-fill: " + TipoTrabajo.REPARACION.colorTexto() + ";";
    }

    /** Tooltip de la píldora "Glass: …", con el mismo fallback de nombre que el resto de textos. */
    public static String tooltipGlassPendiente(ReparacionResumen rep) {
        if (etiquetaGlassPendiente(rep) == null) return null;
        return "Glass abierta de " + nombre(rep.getGlassTecnicoNombre()) + " — entrega sin registrar";
    }

    /** Estilo completo de la mini-píldora "Glass: …" (paleta del tipo Glass, tamaño sub-etiqueta). */
    public static String estiloPildoraGlassPendiente() {
        return "-fx-background-radius: 8; -fx-padding: 1 8 1 8; -fx-font-size: 10px; -fx-font-weight: bold;"
             + "-fx-background-color: " + TipoTrabajo.GLASS.colorFondo() + "; -fx-text-fill: " + TipoTrabajo.GLASS.colorTexto() + ";";
    }

    /** Colores del badge, para concatenar al estilo base de pastilla. */
    public static String estiloColores() {
        return "-fx-background-color: " + COLOR_FONDO + "; -fx-text-fill: " + COLOR_TEXTO + ";";
    }

    private static String texto(String prefijo, LocalDateTime utc, LocalDate hoy) {
        if (utc == null) return null;
        // Hoy: la hora (cuánto lleva esperando). Otro día: solo la fecha — ya no es cuestión de
        // minutos, y "Llegó dd/MM HH:mm" no cabía en la columna Estado (100 px). Tooltip con todo.
        boolean esHoy = hoy != null && hoy.equals(FechaUtils.toLocalDate(utc));
        return prefijo + " " + FechaUtils.formatear(utc, esHoy ? FMT_HORA : FMT_DIA);
    }

    private static String nombre(String n) {
        return (n == null || n.isBlank()) ? "glass" : n;
    }

    private static String nombreRep(String n) {
        return (n == null || n.isBlank()) ? "técnico" : n;
    }
}
