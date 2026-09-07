# Modo "desagrupar por IMEI" en historial — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir al historial de reparaciones un switch "Agrupar por IMEI" que, al desactivarlo, muestra todas las reparaciones en plano (sin agrupar), con contador de total y CSV en formato plano; replicado en los 3 controladores de rol.

**Architecture:** Se amplía el `enum Modo { MAESTRO, DETALLE }` con un tercer valor `PLANO`. PLANO reutiliza el render de filas planas y las columnas de DETALLE, pero mantiene el filtro de IMEI visible y no tiene drill-down ni barra de navegación. El switch es un `CheckBox` en la barra de filtros (FXML); su estado vive en el control durante toda la sesión (memoria Nivel 1). Los disparadores existentes de reset a MAESTRO solo actúan sobre DETALLE, así que PLANO sobrevive solo a recargas y cambios de panel.

**Tech Stack:** Java 17 + JavaFX 21 (controladores + FXML). Sin tests automatizados de UI (no hay TestFX configurado); verificación por `mvn compile` + checklist manual.

**Nota sobre TDD:** Este proyecto no tiene banco de pruebas para controladores JavaFX. Cada tarea se verifica con `mvn compile -q` (debe terminar sin errores) y, al final, con un checklist manual en los 3 roles. No se escriben tests unitarios porque no existe la infraestructura y montarla (TestFX) está fuera del alcance de este item.

---

## File Structure

Archivos a modificar (3 controladores + 3 FXML, mismo cambio en cada par):

- `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` — controlador rol SUPERTECNICO (referencia, el más complejo: tiene toggles Reparaciones↔Pulidos).
- `src/main/resources/views/ReparacionViewSuperTecnico.fxml` — su vista.
- `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java` — controlador rol ADMIN.
- `src/main/resources/views/ReparacionViewAdmin.fxml` — su vista.
- `src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java` — controlador rol TECNICO (el más simple).
- `src/main/resources/views/ReparacionViewTecnico.fxml` — su vista.

Sin clase base nueva (no se refactoriza la duplicación; deuda conocida documentada en el spec).

---

## SHARED CODE (idéntico en los tres controladores)

Estos bloques son los mismos en los 3 controladores. Cada tarea por controlador indica DÓNDE pegarlos. El único bloque que se **adapta por controlador** es la rama PLANO de `aplicarFiltros()` (ver nota), porque el predicado de filtro debe reflejar el de la rama DETALLE de ESE controlador.

**A. Ampliar el enum** — sustituir la línea del enum:
```java
private enum Modo { MAESTRO, DETALLE, PLANO }
```

**B. Campos @FXML** — añadir junto al resto de campos `@FXML` de filtros (cerca de `filtroImei`):
```java
@FXML private javafx.scene.control.ToggleButton toggleAgrupar;
@FXML private javafx.scene.control.ToggleButton toggleDesagrupar;
@FXML private javafx.scene.control.Label        lblContadorPlano;
```

**C. Método `entrarModoPlano()`** — añadir junto a `resetarModo()`:
```java
/** Entra en modo PLANO: todas las reparaciones sin agrupar, columnas estilo detalle,
 *  filtro de IMEI visible y sin barra de navegación. */
private void entrarModoPlano() {
    modoActual  = Modo.PLANO;
    imeiDetalle = null;
    colIdRep.setVisible(true); colReparador.setVisible(true);
    colObservaciones.setVisible(true); colIncidencia.setVisible(true);
    colIdAnterior.setVisible(true); colObservacionTelefono.setVisible(false);
    colComponente.setText("Componente");
    filtroImei.setVisible(true); filtroImei.setManaged(true);
    if (barraNavegacion != null) { barraNavegacion.setVisible(false); barraNavegacion.setManaged(false); }
    adaptarFiltrosDetalle();
    lblContadorPlano.setVisible(true); lblContadorPlano.setManaged(true);
    aplicarFiltros();
}
```

**D. Cableado del pill (ToggleGroup)** — añadir en `initialize()`, después de `configurarFiltros();` (mismo patrón que el toggle "Reparaciones | Pulidos" existente):
```java
lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
javafx.scene.control.ToggleGroup tgAgrupar = new javafx.scene.control.ToggleGroup();
toggleAgrupar.setToggleGroup(tgAgrupar);
toggleDesagrupar.setToggleGroup(tgAgrupar);
tgAgrupar.selectedToggleProperty().addListener((obs, o, n) -> {
    if (n == null) { toggleAgrupar.setSelected(true); return; }  // no permitir deselección
    if (n == toggleDesagrupar) {                                  // pasar a plano
        entrarModoPlano();
    } else {                                                      // volver a agrupado
        lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
        resetarModo();
        aplicarFiltros();
    }
});
```

**E. Rama PLANO en `aplicarFiltros()` (ADAPTAR por controlador)** — insertar justo DESPUÉS de las declaraciones iniciales de `desde`/`hasta`/`filtrarAbiertas`/`filtrarCerradas`/`filtrarNormales` y ANTES del bloque `if (modoActual == Modo.DETALLE)`. El predicado debe ser **una copia del predicado de la rama DETALLE de este mismo controlador**, quitando el filtro `r.getImei().equals(imeiDetalle)` y añadiendo el filtro multi-IMEI que usa la rama MAESTRO (`parsearImeis`). Plantilla de referencia (la del SuperTécnico):
```java
if (modoActual == Modo.PLANO) {
    java.util.Set<String> imeisFiltro = parsearImeis(filtroImei.getText().trim());
    List<ReparacionResumen> filtradas = datos.stream()
        .filter(rep -> {
            if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(rep.getImei())) return false;
            if (!idsTecFiltro.isEmpty() && !idsTecFiltro.contains(rep.getIdTec())) return false;
            if (desde != null || hasta != null) {
                if (rep.getFechaFin() == null) return false;
                LocalDate fechaFin = rep.getFechaFin().toLocalDate();
                if (desde != null && fechaFin.isBefore(desde)) return false;
                if (hasta != null && fechaFin.isAfter(hasta))  return false;
            }
            if (filtrarAbiertas || filtrarCerradas || filtrarNormales) {
                boolean mostrar = false;
                if (filtrarNormales && !rep.isEsIncidencia())                        mostrar = true;
                if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto())  mostrar = true;
                if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto())  mostrar = true;
                if (!mostrar) return false;
            }
            return true;
        }).collect(java.util.stream.Collectors.toList());
    tablaItems.setAll(filtradas);
    lblContadorPlano.setText(filtradas.size() + " reparaci" + (filtradas.size() == 1 ? "ón" : "ones"));
    return;
}
```
> Si un controlador NO tiene `idsTecFiltro` (p. ej. el de TECNICO si no filtra por técnico), omitir esa línea — debe quedar exactamente igual que su rama DETALLE.

**F. FXML** — en el `<HBox>` de la barra de filtros, insertar al FINAL (a la derecha), JUSTO DESPUÉS de `<Button fx:id="btnLimpiarFiltros" .../>`:
```xml
<Label fx:id="lblContadorPlano" visible="false" managed="false" style="-fx-font-size: 12px; -fx-text-fill: #586376; -fx-font-weight: bold;"/>
<HBox spacing="0" alignment="CENTER_LEFT">
    <ToggleButton fx:id="toggleAgrupar" text="Agrupado" selected="true" styleClass="toggle-pill-left"/>
    <ToggleButton fx:id="toggleDesagrupar" text="Plano" styleClass="toggle-pill-right"/>
</HBox>
```
Asegurar que el FXML tenga los imports `<?import javafx.scene.control.ToggleButton?>`, `<?import javafx.scene.control.Label?>` y `<?import javafx.scene.layout.HBox?>` (añadirlos si faltan).

**G. `exportarCSV()`** — NO requiere cambios: PLANO no es MAESTRO, así que cae automáticamente en la rama de export plano (`filaReparacion`). Solo VERIFICAR que la condición sigue siendo `if (modoActual == Modo.MAESTRO)` y que la rama plana lee de `tablaItems` filtrando `instanceof ReparacionResumen`.

---

## Task 1: Rama de trabajo y commit del spec

**Files:**
- Modify (git): rama nueva `feat/desagrupar-historial-imei`

- [ ] **Step 1: Crear la rama desde main**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente"
git checkout main
git checkout -b feat/desagrupar-historial-imei
```

- [ ] **Step 2: Commitear el spec y este plan**

```bash
git add docs/superpowers/specs/2026-06-11-desagrupar-historial-imei-design.md docs/superpowers/plans/2026-06-11-desagrupar-historial-imei.md
git commit -m "docs: spec y plan del modo desagrupar por IMEI en historial"
```

---

## Task 2: Implementación en SuperTécnico (referencia)

**Files:**
- Modify: `src/main/resources/views/ReparacionViewSuperTecnico.fxml` (barra de filtros, ~línea 65-76)
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`

- [ ] **Step 1: FXML — añadir el switch y el contador**

Aplicar el bloque **SHARED CODE F** en `ReparacionViewSuperTecnico.fxml`: insertar el `<Label>` del contador y el `<HBox>` del pill al final de la barra de filtros, justo después de `<Button fx:id="btnLimpiarFiltros" .../>` (línea 75). Verificar/añadir los imports de `ToggleButton`, `Label` y `HBox` (el FXML del SuperTécnico ya importa `ToggleButton` y `HBox`).

- [ ] **Step 2: Controlador — enum, campos y método**

En `ReparacionControllerSuperTecnico.java`:
- Aplicar **SHARED CODE A** (línea 122: `private enum Modo { MAESTRO, DETALLE }` → `{ MAESTRO, DETALLE, PLANO }`).
- Aplicar **SHARED CODE B** (campos `@FXML` junto a `filtroImei`, ~línea 68).
- Aplicar **SHARED CODE C** (`entrarModoPlano()` junto a `resetarModo()`, ~línea 1002).

- [ ] **Step 3: Controlador — listener y rama de filtros**

- Aplicar **SHARED CODE D** en `initialize()`, justo después de la llamada `configurarFiltros();` (~línea 151).
- Aplicar **SHARED CODE E** en `aplicarFiltros()` (~línea 861), insertando la rama PLANO tras las declaraciones `desde/hasta/filtrar*` (líneas 862-866) y antes del `if (modoActual == Modo.DETALLE)` (línea 867). En SuperTécnico el predicado coincide con la plantilla tal cual.

- [ ] **Step 4: Verificar CSV (sin cambios)**

Aplicar **SHARED CODE G**: comprobar que `exportarCSV()` (~línea 1285) usa `if (modoActual == Modo.MAESTRO)` y que la rama plana lee `tablaItems` filtrando `instanceof ReparacionResumen`. No editar si ya es así.

- [ ] **Step 5: Compilar**

Run: `mvn compile -q`
Expected: termina sin salida (sin errores de compilación).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java src/main/resources/views/ReparacionViewSuperTecnico.fxml
git commit -m "feat: modo desagrupar por IMEI en historial (SuperTecnico)"
```

---

## Task 3: Replicar en Admin

**Files:**
- Modify: `src/main/resources/views/ReparacionViewAdmin.fxml`
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

- [ ] **Step 1: FXML — añadir el switch y el contador**

Aplicar **SHARED CODE F** en `ReparacionViewAdmin.fxml`: localizar el `<HBox>` de filtros (el que contiene `<TextField fx:id="filtroImei" .../>` y `<Button fx:id="btnLimpiarFiltros" .../>`) e insertar el `<Label>` del contador y el `<HBox>` del pill al final, tras `btnLimpiarFiltros`. Verificar/añadir imports de `ToggleButton`, `Label` y `HBox`.

- [ ] **Step 2: Controlador — enum, campos, método**

En `ReparacionControllerAdmin.java`:
- Aplicar **SHARED CODE A** (`enum Modo`, ~línea 105).
- Aplicar **SHARED CODE B** (campos `@FXML` junto a `filtroImei`).
- Aplicar **SHARED CODE C** (`entrarModoPlano()` junto a `resetarModo()`, ~línea 310).

- [ ] **Step 3: Controlador — listener y rama de filtros**

- Aplicar **SHARED CODE D** en `initialize()` tras `configurarFiltros();`.
- Aplicar **SHARED CODE E** en `aplicarFiltros()` (~línea 927). ADAPTAR: copiar el predicado de la rama DETALLE de ESTE controlador, quitar el filtro por `imeiDetalle`, añadir el filtro multi-IMEI. Si no existe `idsTecFiltro` en este controlador, omitir esa línea.

- [ ] **Step 4: Verificar CSV (SHARED CODE G)** — sin cambios si la condición es `Modo.MAESTRO`.

- [ ] **Step 5: Compilar**

Run: `mvn compile -q`
Expected: sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java src/main/resources/views/ReparacionViewAdmin.fxml
git commit -m "feat: modo desagrupar por IMEI en historial (Admin)"
```

---

## Task 4: Replicar en Técnico

**Files:**
- Modify: `src/main/resources/views/ReparacionViewTecnico.fxml`
- Modify: `src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java`

- [ ] **Step 1: FXML — añadir el switch y el contador**

Aplicar **SHARED CODE F** en `ReparacionViewTecnico.fxml`: insertar el `<Label>` del contador y el `<HBox>` del pill al final de la barra de filtros, tras `<Button fx:id="btnLimpiarFiltros" .../>`. Verificar/añadir imports de `ToggleButton`, `Label` y `HBox`.

- [ ] **Step 2: Controlador — enum, campos, método**

En `ReparacionControllerTecnico.java`:
- Aplicar **SHARED CODE A** (`enum Modo`, ~línea 93).
- Aplicar **SHARED CODE B** (campos `@FXML`).
- Aplicar **SHARED CODE C** (`entrarModoPlano()` junto a `resetarModo()`, ~línea 741). NOTA: si en este controlador `adaptarFiltrosDetalle()` o alguna columna referenciada no existe con el mismo nombre, ajustar a los nombres reales de este controlador (leer el archivo primero).

- [ ] **Step 3: Controlador — listener y rama de filtros**

- Aplicar **SHARED CODE D** en `initialize()` tras `configurarFiltros();`.
- Aplicar **SHARED CODE E** en `aplicarFiltros()` (~línea 607). ADAPTAR el predicado al de la rama DETALLE de este controlador. El TECNICO solo ve sus propias reparaciones; si no hay filtro de técnico (`idsTecFiltro`), omitir esa línea — el predicado debe quedar idéntico al de su DETALLE salvo el cambio IMEI.

- [ ] **Step 4: Verificar CSV (SHARED CODE G)** — sin cambios si la condición es `Modo.MAESTRO`.

- [ ] **Step 5: Compilar**

Run: `mvn compile -q`
Expected: sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java src/main/resources/views/ReparacionViewTecnico.fxml
git commit -m "feat: modo desagrupar por IMEI en historial (Tecnico)"
```

---

## Task 5: Verificación manual (los 3 roles)

**Files:** ninguno (pruebas en ejecución).

Generar el app-image (o `mvn javafx:run`) y, para CADA rol (SuperTécnico, Admin, Técnico), recorrer el checklist del spec:

- [ ] **Step 1: Compilación global limpia**

Run: `mvn compile -q`
Expected: sin errores.

- [ ] **Step 2: Checklist funcional por rol**

Para cada uno de los 3 roles, verificar:
1. Desmarcar "Agrupar por IMEI" → la tabla pasa a plana con todas las reparaciones; el filtro de IMEI sigue visible; aparece el contador "N reparaciones".
2. Filtrar por técnico en plano (donde aplique) → solo sus reparaciones; el contador se actualiza.
3. Filtrar por fecha/incidencias en plano → aplican correctamente y el contador se actualiza.
4. Esperar la recarga automática de 60s en plano → sigue en plano (Nivel 0).
5. Cambiar a Pendientes y volver a Historial → sigue en plano (Nivel 1).
6. (SuperTécnico) Toggle a Pulidos y volver a Reparaciones → el modo se conserva.
7. Exportar CSV en plano → formato por-reparación (no agrupado).
8. Volver a marcar "Agrupar por IMEI" → MAESTRO y su drill-down funcionan como antes; el contador desaparece.
9. Cerrar y reabrir la app → arranca en "Agrupado".

- [ ] **Step 3: Si todo correcto, finalizar la rama**

Usar superpowers:finishing-a-development-branch para decidir merge/cierre.

---

## Self-Review (cobertura del spec)

- Pill "Agrupado | Plano", "Agrupado" por defecto → SHARED F (`toggleAgrupar selected="true"`) + Task 2-4 Step 1. ✓
- Modo PLANO sin agrupar ni drill-down → SHARED A/C/E. ✓
- Memoria Nivel 1 (sobrevive recarga/panel/Pulidos) → el estado vive en el pill (ToggleGroup); los resets solo afectan a DETALLE; Task 5 Steps 4-6. ✓
- Nivel 0 (recarga/filtros no resetean) → `aplicarFiltros()` respeta `modoActual`; Task 5 Step 4. ✓
- Filtro IMEI visible en plano → SHARED C (`filtroImei.setVisible(true)`). ✓
- Filtros técnico/fecha/incidencias en plano → SHARED E (predicado). ✓
- Contador de total → SHARED B/C/E (`lblContadorPlano`). ✓
- CSV diferencia la vista → SHARED G (PLANO cae en export plano). ✓
- 3 roles → Tasks 2, 3, 4. ✓
- Arranca en Agrupado al abrir la app → `toggleAgrupar selected="true"` por defecto + Task 5 Step 9. ✓
