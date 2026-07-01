package com.reparaciones.utils;

/**
 * Tipo de trabajo de una reparación/asignación, derivado del prefijo del {@code ID_REP}.
 * <p>Los tres tipos comparten la tabla {@code Reparacion} y solo se distinguen por el
 * prefijo del ID: asignaciones ({@code A}/{@code AG}/{@code AP}) e historial
 * ({@code R}/{@code G}/{@code P}). Cada tipo lleva su etiqueta y su paleta de badge.</p>
 */
public enum TipoTrabajo {
    REPARACION("Reparación", "#E3F2FD", "#1565C0"),
    GLASS     ("Glass",      "#E0F2F1", "#00796B"),
    PULIDO    ("Pulido",     "#EDE7F6", "#5E35B1");

    private final String etiqueta;
    private final String colorFondo;
    private final String colorTexto;

    TipoTrabajo(String etiqueta, String colorFondo, String colorTexto) {
        this.etiqueta   = etiqueta;
        this.colorFondo = colorFondo;
        this.colorTexto = colorTexto;
    }

    public String etiqueta()   { return etiqueta; }
    public String colorFondo() { return colorFondo; }
    public String colorTexto() { return colorTexto; }

    /** Estilo inline para un badge (pill) del tipo, listo para {@code Label.setStyle(...)}. */
    public String estiloBadge() {
        return "-fx-background-radius: 10; -fx-padding: 2 10 2 10; -fx-font-size: 11px; -fx-font-weight: bold;"
             + "-fx-background-color: " + colorFondo + "; -fx-text-fill: " + colorTexto + ";";
    }

    /**
     * Deriva el tipo de trabajo a partir del prefijo del {@code ID_REP}.
     * Cubre asignaciones ({@code AG}→glass, {@code AP}→pulido, {@code A}→reparación)
     * e historial ({@code G}→glass, {@code P}→pulido, {@code R}→reparación). Puro y testeable.
     */
    public static TipoTrabajo desde(String idRep) {
        if (idRep == null) return REPARACION;
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return GLASS;
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return PULIDO;
        return REPARACION;
    }
}
