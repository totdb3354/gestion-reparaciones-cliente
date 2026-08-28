package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/**
 * Celda de la columna Reparador de las vistas de historial: el nombre y, en filas de glass
 * con entrega, la sub-etiqueta "Llegó dd/MM HH:mm" debajo (mismo estilo que "Chasis" bajo
 * el tipo). Compartida por Agrupado por IMEI y por el Historial de los tres roles.
 */
public final class CeldaReparador {

    private CeldaReparador() {}

    public static TableCell<Object, String> crear() {
        return new TableCell<>() {
            private final Label lblNombre = new Label();
            private final Label lblLlego  = new Label();
            private final VBox  box       = new VBox(1, lblNombre, lblLlego);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                lblLlego.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A94A6;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null || item.isEmpty()) { setGraphic(null); return; }
                Object fila = (getIndex() >= 0 && getIndex() < getTableView().getItems().size())
                        ? getTableView().getItems().get(getIndex()) : null;
                String sub = fila instanceof ReparacionResumen rep ? EntregaGlass.subEtiquetaHistorial(rep) : null;
                lblNombre.setText(item);
                if (sub != null) {
                    lblLlego.setText(sub);
                    lblLlego.setTooltip(new Tooltip(EntregaGlass.tooltip((ReparacionResumen) fila)));
                    lblLlego.setVisible(true); lblLlego.setManaged(true);
                } else {
                    lblLlego.setText(null);
                    lblLlego.setTooltip(null);
                    lblLlego.setVisible(false); lblLlego.setManaged(false);
                }
                setGraphic(box);
            }
        };
    }
}
