package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.VBox;

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

    /**
     * Celda de tabla con la píldora del tipo y la sub-etiqueta «Chasis» debajo
     * (visible solo en reparaciones marcadas como chasis). Compartida por las
     * tablas de asignaciones y de mis pendientes.
     */
    public static TableCell<ReparacionResumen, Void> celdaTipoConChasis() {
        return new TableCell<>() {
            private final Label badge     = new Label();
            private final Label lblChasis = new Label("Chasis");
            private final VBox  box       = new VBox(1, badge, lblChasis);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                lblChasis.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A94A6;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) { setGraphic(null); return; }
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                TipoTrabajo tipo = desde(rep.getIdRep());
                badge.setText(tipo.etiqueta());
                badge.setStyle(tipo.estiloBadge());
                boolean chasis = rep.isEsChasis() && tipo == REPARACION;
                lblChasis.setVisible(chasis); lblChasis.setManaged(chasis);
                setGraphic(box);
            }
        };
    }
}
