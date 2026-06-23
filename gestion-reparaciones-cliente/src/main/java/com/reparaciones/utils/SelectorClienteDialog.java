package com.reparaciones.utils;

import com.reparaciones.models.Cliente;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.Optional;

/**
 * Diálogo modal reutilizable para seleccionar un cliente de una lista filtrable.
 *
 * <h3>Contrato de retorno</h3>
 * <ul>
 *   <li>{@code Optional.of(-1)} — el usuario eligió "— Sin cliente —" (quitar cliente).</li>
 *   <li>{@code Optional.of(idCli)} — el usuario seleccionó un cliente concreto (idCli > 0).</li>
 *   <li>{@code Optional.empty()} — el usuario canceló o cerró el diálogo sin elegir.</li>
 * </ul>
 *
 * <p>Los llamadores deben traducir el centinela −1 → {@code null} antes de llamar a
 * {@code telefonoDAO.actualizarCliente(imei, null, updatedAt)}.</p>
 */
public class SelectorClienteDialog {

    /** Centinela que representa "Sin cliente" en el Optional devuelto. */
    private static final int SIN_CLIENTE = -1;

    private static final String ESTILO_CONTENEDOR =
            "-fx-background-color: " + Colores.CREMA + ";" +
            "-fx-border-color: #C2C8D0; -fx-border-width: 1;";

    /**
     * Muestra un diálogo modal con campo de búsqueda y lista de clientes.
     *
     * @param activos     lista de clientes activos devuelta por {@code ClienteDAO.getActivos()}
     * @param idCliActual id del cliente actualmente asignado (para pre-selección), o {@code null}
     * @return {@code Optional.of(-1)} = sin cliente; {@code Optional.of(id)} = cliente elegido;
     *         {@code Optional.empty()} = cancelado
     */
    public static Optional<Integer> elegir(List<Cliente> activos, Integer idCliActual) {

        Integer[] resultado = {null};
        boolean[] confirmado = {false};

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.initStyle(StageStyle.UNDECORATED);
        ventana.setResizable(false);

        // ── Título + X ────────────────────────────────────────────────────────
        Label lblTitulo = new Label("Seleccionar cliente");
        lblTitulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + Colores.AZUL_MEDIO + ";");
        lblTitulo.setWrapText(true);

        Label lblX = new Label("✕");
        lblX.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: " + Colores.AZUL_GRIS + ";");
        lblX.setOnMouseClicked(e -> ventana.close());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox barraTop = new HBox(lblTitulo, spacer, lblX);
        barraTop.setAlignment(Pos.CENTER_LEFT);
        barraTop.setPadding(new Insets(0, 0, 8, 0));

        // ── Campo de búsqueda ─────────────────────────────────────────────────
        TextField tfBuscar = new TextField();
        tfBuscar.setPromptText("Buscar cliente...");
        tfBuscar.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-radius: 4; -fx-background-radius: 4;" +
                "-fx-text-fill: #2C3B54; -fx-font-size: 13px;" +
                "-fx-padding: 8 12 8 12;");
        tfBuscar.setMaxWidth(Double.MAX_VALUE);

        // ── Lista filtrable ───────────────────────────────────────────────────
        // La opción "Sin cliente" se representa con id = SIN_CLIENTE.
        // Usamos una clase interna de línea de presentación.
        record Opcion(int id, String nombre) {
            @Override public String toString() { return nombre; }
        }

        ObservableList<Opcion> todas = FXCollections.observableArrayList();
        todas.add(new Opcion(SIN_CLIENTE, "— Sin cliente —"));
        for (Cliente c : activos) todas.add(new Opcion(c.getIdCli(), c.getNombre()));

        FilteredList<Opcion> filtradas = new FilteredList<>(todas, op -> true);

        ListView<Opcion> listaView = new ListView<>(filtradas);
        listaView.setStyle(
                "-fx-background-color: white; -fx-border-color: #C2C8D0;" +
                "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
        listaView.setFixedCellSize(32);
        listaView.setPrefHeight(Math.min(filtradas.size(), 8) * 32 + 4);
        listaView.setMaxHeight(260);
        listaView.setCellFactory(lv -> new ListCell<>() {
            {
                setOnMouseEntered(e -> { if (!isEmpty() && getItem() != null)
                    setStyle("-fx-background-color: #001232; -fx-background-radius: 4;" +
                            "-fx-background-insets: 2 4 2 4;" +
                            "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 4 12 4 12;"); });
                setOnMouseExited(e -> { if (!isEmpty() && getItem() != null)
                    setStyle("-fx-background-color: white; -fx-text-fill: #001232;" +
                            "-fx-font-size: 12px; -fx-padding: 4 12 4 12;"); });
            }
            @Override
            protected void updateItem(Opcion op, boolean empty) {
                super.updateItem(op, empty);
                if (empty || op == null) { setText(null); setStyle(""); return; }
                setText(op.nombre());
                boolean esActual = idCliActual != null && op.id() == idCliActual;
                String base = esActual
                        ? "-fx-background-color: #EAF1FF; -fx-text-fill: #001232;" +
                          "-fx-font-size: 12px; -fx-padding: 4 12 4 12; -fx-font-weight: bold;"
                        : "-fx-background-color: white; -fx-text-fill: #001232;" +
                          "-fx-font-size: 12px; -fx-padding: 4 12 4 12;";
                setStyle(base);
            }
        });

        // Pre-seleccionar cliente actual si existe
        if (idCliActual != null) {
            filtradas.stream()
                    .filter(op -> op.id() == idCliActual)
                    .findFirst()
                    .ifPresent(op -> {
                        listaView.getSelectionModel().select(op);
                        listaView.scrollTo(op);
                    });
        }

        // ── Filtro en tiempo real ─────────────────────────────────────────────
        tfBuscar.textProperty().addListener((obs, oldV, newV) -> {
            String lower = newV == null ? "" : newV.trim().toLowerCase();
            filtradas.setPredicate(op ->
                lower.isEmpty() || op.nombre().toLowerCase().contains(lower));
            listaView.setPrefHeight(Math.min(filtradas.size(), 8) * 32 + 4);
        });

        // ── Botón Seleccionar ─────────────────────────────────────────────────
        Button btnSeleccionar = new Button("Seleccionar");
        btnSeleccionar.setMaxWidth(Double.MAX_VALUE);
        btnSeleccionar.setStyle(
                "-fx-background-color: " + Colores.AZUL_MEDIO + "; -fx-text-fill: " + Colores.CREMA + ";" +
                "-fx-background-radius: 4; -fx-font-size: 12px;" +
                "-fx-padding: 10; -fx-cursor: hand;");
        btnSeleccionar.setDisable(true);

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setMaxWidth(Double.MAX_VALUE);
        btnCancelar.setStyle(
                "-fx-background-color: " + Colores.CREMA + "; -fx-text-fill: " + Colores.AZUL_GRIS + ";" +
                "-fx-border-color: " + Colores.AZUL_GRIS + "; -fx-border-radius: 4;" +
                "-fx-background-radius: 4; -fx-font-size: 12px;" +
                "-fx-padding: 10; -fx-cursor: hand;");

        // Habilitar "Seleccionar" cuando hay selección
        listaView.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) ->
                btnSeleccionar.setDisable(sel == null));

        // Confirmar con doble click en la lista
        listaView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Opcion sel = listaView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    resultado[0] = sel.id();
                    confirmado[0] = true;
                    ventana.close();
                }
            }
        });

        // Enter en la lista
        listaView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Opcion sel = listaView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    resultado[0] = sel.id();
                    confirmado[0] = true;
                    ventana.close();
                }
            }
        });

        btnSeleccionar.setOnAction(e -> {
            Opcion sel = listaView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                resultado[0] = sel.id();
                confirmado[0] = true;
                ventana.close();
            }
        });

        btnCancelar.setOnAction(e -> ventana.close());

        // ── Layout ────────────────────────────────────────────────────────────
        VBox contenido = new VBox(10, barraTop, tfBuscar, listaView, btnSeleccionar, btnCancelar);
        contenido.setPadding(new Insets(24));
        contenido.setPrefWidth(380);
        contenido.setStyle(ESTILO_CONTENEDOR);

        // ── Arrastrar ventana ─────────────────────────────────────────────────
        final double[] drag = new double[2];
        contenido.setOnMousePressed(ev -> { drag[0] = ev.getSceneX(); drag[1] = ev.getSceneY(); });
        contenido.setOnMouseDragged(ev -> {
            ventana.setX(ev.getScreenX() - drag[0]);
            ventana.setY(ev.getScreenY() - drag[1]);
        });

        Scene scene = new Scene(contenido);
        scene.setFill(Color.web(Colores.CREMA));
        ventana.setScene(scene);
        ventana.setOnCloseRequest(e -> {});
        javafx.application.Platform.runLater(tfBuscar::requestFocus);
        ventana.showAndWait();

        if (!confirmado[0]) return Optional.empty();
        return Optional.of(resultado[0]);
    }
}
