# Otros técnicos por defecto en el detalle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En la vista DETALLE del técnico, mostrar siempre las reparaciones de otros técnicos (atenuadas, debajo de las propias) y eliminar el botón que hoy las activa.

**Architecture:** Cambio puntual en `ReparacionControllerTecnico`: el bloque DETALLE de `aplicarFiltros()` siempre incluye las ajenas (reutilizando `idsAjenas` y la opacidad ya existentes), y se elimina por completo el `ToggleButton btnOtrosTecnicos`. Solo cliente.

**Tech Stack:** JavaFX 17, controlador `ReparacionControllerTecnico`.

## Global Constraints

- Solo cliente, solo `ReparacionControllerTecnico`, solo modo DETALLE. Sin servidor/BD.
- Vista PLANO del técnico sin cambios (sigue mostrando solo `idTec == sesión`).
- Propias arriba, ajenas debajo y atenuadas (opacity 0.45 vía `idsAjenas`, ya implementado).
- Contador: "X propia(s) + Y de otros" cuando hay ajenas; "X reparaciones" cuando solo hay propias.
- UI sin tests automáticos: verificación por compilación + pruebas manuales.

---

## Task 1: Mostrar ajenas por defecto y eliminar el botón

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java`

**Interfaces:**
- Sin interfaces nuevas. Se elimina el campo `btnOtrosTecnicos`; ningún otro archivo lo referencia (es privado).

- [ ] **Step 1: Forzar las ajenas siempre en el bloque DETALLE de `aplicarFiltros()`**

Localizar en el modo `DETALLE` el bloque que hoy condiciona las ajenas al botón:
```java
            idsAjenas.clear();
            List<ReparacionResumen> resultado = new ArrayList<>(propias);

            if (btnOtrosTecnicos != null && btnOtrosTecnicos.isSelected()) {
                List<ReparacionResumen> ajenas = datos.stream()
                    .filter(r -> r.getImei().equals(imeiDetalle))
                    .filter(r -> idTec == null || r.getIdTec() != idTec)
                    .filter(predicado)
                    .collect(Collectors.toList());
                ajenas.forEach(r -> idsAjenas.add(r.getIdRep()));
                resultado.addAll(ajenas);
                int nA = idsAjenas.size();
                lblNavCount.setText("  •  " + propias.size() + " propia" + (propias.size() != 1 ? "s" : "")
                    + (nA > 0 ? " + " + nA + " de otros" : ""));
            } else {
                lblNavCount.setText("  •  " + propias.size() + " reparaciones");
            }

            tablaItems.setAll(resultado);
            return;
```
Reemplazarlo por (ajenas siempre incluidas):
```java
            idsAjenas.clear();
            List<ReparacionResumen> resultado = new ArrayList<>(propias);

            List<ReparacionResumen> ajenas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(r -> idTec == null || r.getIdTec() != idTec)
                .filter(predicado)
                .collect(Collectors.toList());
            ajenas.forEach(r -> idsAjenas.add(r.getIdRep()));
            resultado.addAll(ajenas);
            int nA = idsAjenas.size();
            if (nA > 0) {
                lblNavCount.setText("  •  " + propias.size() + " propia" + (propias.size() != 1 ? "s" : "")
                    + " + " + nA + " de otros");
            } else {
                lblNavCount.setText("  •  " + propias.size() + " reparaciones");
            }

            tablaItems.setAll(resultado);
            return;
```

- [ ] **Step 2: Eliminar la creación del botón y su listener**

Localizar y eliminar el bloque que crea el botón (incluida la imagen/icono y el listener), que es:
```java
        Image imgHistorial = new Image(getClass().getResourceAsStream("/images/Historial.png"));
        ImageView ivToggle = new ImageView(imgHistorial);
        ivToggle.setFitWidth(22); ivToggle.setFitHeight(22); ivToggle.setPreserveRatio(true);
        btnOtrosTecnicos = new ToggleButton("", ivToggle);
        btnOtrosTecnicos.getStyleClass().add("btn-secondary");
        btnOtrosTecnicos.setTooltip(new Tooltip("Mostrar reparaciones de otros técnicos"));
        btnOtrosTecnicos.selectedProperty().addListener((obs, o, sel) -> aplicarFiltros());

```
(Eliminar esas 8 líneas por completo.)

- [ ] **Step 3: Quitar el botón (y el spacer que lo empujaba) de la barra de navegación**

Localizar la construcción de `barraNavegacion`:
```java
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        barraNavegacion = new HBox(12, btnVolver,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                lblNavImei, lblNavModelo, lblNavCount, spacer, btnOtrosTecnicos);
```
Reemplazar por (sin spacer ni botón):
```java
        barraNavegacion = new HBox(12, btnVolver,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                lblNavImei, lblNavModelo, lblNavCount);
```

- [ ] **Step 4: Eliminar la referencia en `resetarModo()`**

Localizar y eliminar esta línea dentro de `resetarModo()`:
```java
        if (btnOtrosTecnicos != null) btnOtrosTecnicos.setSelected(false);
```
(La línea `idsAjenas.clear();` que le sigue se mantiene.)

- [ ] **Step 5: Eliminar la declaración del campo**

Localizar y eliminar la declaración del campo:
```java
    private ToggleButton  btnOtrosTecnicos;
```

- [ ] **Step 6: Limpiar imports huérfanos si los hubiera**

Tras eliminar el botón, comprobar si `Tooltip` quedó sin uso en el archivo. Buscar:
```bash
grep -n "Tooltip" gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java
```
Si no aparece ninguna otra referencia (solo el import), eliminar el import `import javafx.scene.control.Tooltip;`. NO tocar el import de `ToggleButton` ni `Image`/`ImageView`: siguen usándose en otros puntos del archivo (p. ej. `toggleAgrupar`, iconos de filas). Si alguno quedara sin uso, eliminarlo también; si se usa, dejarlo.

- [ ] **Step 7: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS. Si el compilador señala alguna referencia restante a `btnOtrosTecnicos`, eliminarla (no debería quedar ninguna).

- [ ] **Step 8: Test manual**

Arrancar servidor + cliente como **técnico**:
1. Entrar en el detalle de un IMEI con reparaciones de varios técnicos → se ven las propias arriba y las de otros **atenuadas** debajo, **sin botón** en la barra de navegación.
2. El contador muestra "X propia(s) + Y de otros".
3. Entrar en un IMEI con solo reparaciones propias → solo propias, contador "X reparaciones", nada atenuado.
4. Cambiar a vista **PLANO** → sigue mostrando solo las reparaciones propias (sin cambios).

- [ ] **Step 9: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java
git commit -m "feat(otros-tecnicos): mostrar reparaciones de otros técnicos por defecto en el detalle y eliminar el botón"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Step |
|---|---|
| Ajenas siempre visibles en DETALLE | Step 1 |
| Propias arriba, ajenas debajo | Step 1 (`resultado = propias` + `addAll(ajenas)`) |
| Atenuado conservado (idsAjenas, opacity 0.45) | Sin cambios (Step 1 puebla `idsAjenas`) |
| Contador "X propia(s) + Y de otros" / "X reparaciones" | Step 1 |
| Eliminar `btnOtrosTecnicos` (campo, creación, listener, barra, reset) | Steps 2, 3, 4, 5 |
| Imports sin uso | Step 6 |
| Vista PLANO sin cambios | No se toca (fuera del bloque DETALLE) |
| Solo cliente | Todo el plan |

### Notas
- Sin TDD (UI); verificación por compilación + manual.
- `idsAjenas` y el `rowFactory` que aplica `opacity 0.45` no se tocan: el Step 1 sigue poblando `idsAjenas` igual que antes, solo que ahora siempre.
