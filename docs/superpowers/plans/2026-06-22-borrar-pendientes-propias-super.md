# Borrar pendientes propias (supertécnico) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir en la celda de acción de `PendientesTecnicoController` una papelera de borrado idéntica a la del panel de Asignaciones, visible solo para el supertécnico.

**Architecture:** Único cambio en `PendientesTecnicoController.cAccion`: la celda pasa a un `HBox` con el botón "Añadir reparación" y, si `Sesion.esSuperTecnico()`, un icono de papelera con el mismo flujo de borrado que `PendientesSuperTecnicoController`. Solo cliente.

**Tech Stack:** JavaFX 17, controlador `PendientesTecnicoController`.

## Global Constraints

- Solo cliente, solo `PendientesTecnicoController`, solo pendientes de reparación.
- La papelera solo se muestra si `Sesion.esSuperTecnico()`; el técnico normal no la ve.
- Borrado de asignación SIN motivo (consistente con el panel de Asignaciones).
- Incidencia → `reparacionDAO.borrarIncidenciaPorImei(imei)`; normal → `reparacionDAO.eliminarAsignacion(idRep)`; luego `cargar()` + `onCerrar` (refresca badges).
- Sin servidor/BD (los endpoints ya existen). UI sin tests automáticos: verificación por compilación + manual.

---

## Task 1: Papelera de borrado en PendientesTecnicoController (solo supertécnico)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java`

**Interfaces:**
- Consumes: `Sesion.esSuperTecnico()`, `ReparacionDAO.eliminarAsignacion(String)`, `ReparacionDAO.borrarIncidenciaPorImei(String)`, `ConfirmDialog.mostrar(String, String, String, Runnable)`, `cargar()`, `onCerrar`, `mostrarError(Exception)` (todos existentes).

- [ ] **Step 1: Añadir imports**

En `PendientesTecnicoController.java`, junto a los imports existentes, añadir:
```java
import com.reparaciones.utils.ConfirmDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
```

- [ ] **Step 2: Reescribir el cellFactory de `cAccion`**

Reemplazar el bloque actual:
```java
        cAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Añadir reparación");
            {
                btn.getStyleClass().add("btn-primary");
                btn.setOnAction(e -> {
                    ReparacionResumen asig = getTableView().getItems().get(getIndex());
                    Runnable alCerrar = () -> {
                        cargar();
                        if (onCerrar != null) onCerrar.run();
                    };
                    FormularioReparacionController.abrir(
                            asig.getImei(), null, asig.getIdRep(), alCerrar);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
```
por:
```java
        Image imgBorrar = new Image(getClass().getResourceAsStream("/images/borrar.png"));
        cAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Añadir reparación");
            private final ImageView ivBorrar = new ImageView(imgBorrar);
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(8);
            {
                btn.getStyleClass().add("btn-primary");
                btn.setOnAction(e -> {
                    ReparacionResumen asig = getTableView().getItems().get(getIndex());
                    Runnable alCerrar = () -> {
                        cargar();
                        if (onCerrar != null) onCerrar.run();
                    };
                    FormularioReparacionController.abrir(
                            asig.getImei(), null, asig.getIdRep(), alCerrar);
                });

                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                box.getChildren().add(btn);

                if (Sesion.esSuperTecnico()) {
                    ivBorrar.setFitWidth(25); ivBorrar.setFitHeight(25); ivBorrar.setPreserveRatio(true);
                    ivBorrar.setStyle("-fx-cursor: hand;");
                    ivBorrar.setOnMouseClicked(e -> {
                        ReparacionResumen rep = getTableView().getItems().get(getIndex());
                        String desc = "El técnico dejará de verla en su lista de pendientes" +
                                (rep.isEsIncidencia()
                                        ? " y la incidencia se marcará como no activa en la tabla principal."
                                        : ".");
                        ConfirmDialog.mostrar("Borrar asignación " + rep.getIdRep(), desc,
                                "Borrar asignación", () -> {
                                    try {
                                        if (rep.isEsIncidencia())
                                            reparacionDAO.borrarIncidenciaPorImei(rep.getImei());
                                        else
                                            reparacionDAO.eliminarAsignacion(rep.getIdRep());
                                        cargar();
                                        if (onCerrar != null) onCerrar.run();
                                    } catch (SQLException ex) { mostrarError(ex); }
                                });
                    });
                    box.getChildren().add(ivBorrar);
                }
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
```

- [ ] **Step 3: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Test manual**

Arrancar servidor + cliente:
1. Como **supertécnico**, en "Pendientes" propias: junto a "Añadir reparación" aparece la **papelera**. Borrar una asignación normal → confirma sin pedir motivo, se elimina, la lista y los badges se actualizan.
2. Borrar una que sea **incidencia** → usa `borrarIncidenciaPorImei` (la incidencia queda no activa en la tabla principal).
3. Como **técnico normal**, en sus pendientes: la papelera **no** aparece; "Añadir reparación" funciona como siempre.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java
git commit -m "feat(borrar-pendientes): papelera de borrado en pendientes propias solo para supertécnico"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Step |
|---|---|
| Papelera junto a "Añadir reparación", solo si `Sesion.esSuperTecnico()` | Step 2 |
| Mismo icono (/images/borrar.png, 25×25, cursor hand) | Step 2 |
| `ConfirmDialog.mostrar` sin motivo + desc según incidencia | Step 2 |
| Incidencia → `borrarIncidenciaPorImei`; normal → `eliminarAsignacion` | Step 2 |
| Tras borrar: `cargar()` + `onCerrar` | Step 2 |
| Técnico normal sin papelera | Step 2 (rama `if (Sesion.esSuperTecnico())`) |
| Solo cliente, solo reparación | Todo el plan |

### Notas
- Sin TDD (UI); verificación por compilación + manual.
- `HBox`/`Pos` se usan con ruta completa para no tocar más imports; `Image`/`ImageView`/`ConfirmDialog` se importan (Step 1). `Sesion`, `reparacionDAO`, `onCerrar`, `mostrarError`, `SQLException` ya existen en el archivo.
