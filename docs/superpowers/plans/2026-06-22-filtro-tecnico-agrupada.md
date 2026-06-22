# Filtro de técnico en vista agrupada del historial — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el historial de admin y supertécnico, ofrecer el filtro de técnico en la vista agrupada (grupos donde intervino el técnico, OR) y, al abrir un IMEI, mostrar todas las reparaciones con las de los técnicos filtrados arriba y el resto atenuado.

**Architecture:** Cambios paralelos y simétricos en `ReparacionControllerSuperTecnico` y `ReparacionControllerAdmin` (estructura idéntica): mostrar `filtroTecnico` en MAESTRO, filtrar grupos por intervención en `buildTablaItems`, reescribir el bloque DETALLE para mostrar todo con reordenado + opacidad, y añadir el mecanismo `idsAjenas` + `setOpacity` en el rowFactory. Solo cliente.

**Tech Stack:** JavaFX 17, controladores `ReparacionControllerSuperTecnico` / `ReparacionControllerAdmin`.

## Global Constraints

- Solo cliente. Sin servidor/BD (`datos` ya contiene todo el historial).
- Aplica solo a admin y supertécnico. Vista PLANA sin cambios; vista del técnico no se toca.
- Filtro en MAESTRO = OR: un IMEI aparece si al menos una de sus reparaciones es de un técnico seleccionado; el grupo conserva TODAS sus reparaciones.
- DETALLE con filtro: técnicos filtrados arriba, resto atenuado (opacity 0.45). Sin filtro: todas normales, sin reordenar.
- `idsAjenas` debe limpiarse fuera de DETALLE para que la vista PLANA (filas `ReparacionResumen`) no se atenúe por error.
- UI sin tests automáticos: verificación por compilación + pruebas manuales.

---

## Task 1: ReparacionControllerSuperTecnico

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`

**Interfaces:**
- Reutiliza el campo existente `Set<Integer> idsTecFiltro`.
- Produce el campo `Set<String> idsAjenas` (privado, uso interno del controller).

- [ ] **Step 1: Añadir el campo `idsAjenas`**

Junto a la declaración de `idsTecFiltro` (`private final Set<Integer> idsTecFiltro = new HashSet<>();`), añadir:
```java
    private final Set<String> idsAjenas = new HashSet<>();
```
(`Set` y `HashSet` ya están importados — los usa `idsTecFiltro`.)

- [ ] **Step 2: Mostrar el filtro de técnico en la vista agrupada**

En `adaptarFiltrosMaestro()`, cambiar la primera línea:
```java
        filtroTecnico.setVisible(false); filtroTecnico.setManaged(false);
```
por:
```java
        filtroTecnico.setVisible(true); filtroTecnico.setManaged(true);
```

- [ ] **Step 3: Limpiar `idsAjenas` al inicio de `aplicarFiltros()`**

En `aplicarFiltros()`, tras las líneas que leen los filtros (después de `boolean filtrarNormales = cbNormales.isSelected();`), añadir:
```java
        idsAjenas.clear();
```
(Solo el bloque DETALLE lo repoblará; así PLANO y MAESTRO nunca atenúan filas.)

- [ ] **Step 4: Filtrar grupos por intervención del técnico en `buildTablaItems()`**

En `buildTablaItems()`, dentro del bucle `for (Map.Entry<String, List<ReparacionResumen>> e : porImei.entrySet())`, justo después de `GrupoImei grupo = new GrupoImei(e.getKey(), e.getValue());`, añadir el filtro por técnico (OR):
```java
            if (!idsTecFiltro.isEmpty()
                    && e.getValue().stream().noneMatch(r -> idsTecFiltro.contains(r.getIdTec())))
                continue;
```
(Queda antes del bloque `if (filtrarInc || filtrarNormal)` existente.)

- [ ] **Step 5: Reescribir el bloque DETALLE de `aplicarFiltros()`**

Localizar el bloque `if (modoActual == Modo.DETALLE) { ... }` y reemplazarlo por:
```java
        if (modoActual == Modo.DETALLE) {
            lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
            List<ReparacionResumen> todas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(rep -> {
                    if (desde != null || hasta != null) {
                        if (rep.getFechaFin() == null) return false;
                        LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
                        if (desde != null && fechaFin.isBefore(desde)) return false;
                        if (hasta != null && fechaFin.isAfter(hasta))  return false;
                    }
                    if (filtrarAbiertas || filtrarCerradas || filtrarNormales) {
                        boolean mostrar = false;
                        if (filtrarNormales && !rep.isEsIncidencia())                        mostrar = true;
                        if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto()) mostrar = true;
                        if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto()) mostrar = true;
                        if (!mostrar) return false;
                    }
                    return true;
                }).collect(Collectors.toList());

            if (idsTecFiltro.isEmpty()) {
                tablaItems.setAll(todas);
                lblNavCount.setText("  •  " + todas.size() + " reparaci" + (todas.size() == 1 ? "ón" : "ones"));
            } else {
                List<ReparacionResumen> delFiltro = todas.stream()
                    .filter(r -> idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                List<ReparacionResumen> otras = todas.stream()
                    .filter(r -> !idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                otras.forEach(r -> idsAjenas.add(r.getIdRep()));
                List<ReparacionResumen> resultado = new ArrayList<>(delFiltro);
                resultado.addAll(otras);
                tablaItems.setAll(resultado);
                int nO = otras.size();
                lblNavCount.setText("  •  " + delFiltro.size() + " de filtrados"
                    + (nO > 0 ? " + " + nO + " de otros" : ""));
            }
            return;
        }
```

- [ ] **Step 6: Aplicar la opacidad en el rowFactory**

En el `rowFactory` de `tablaReparaciones`, en el `updateItem`, que hoy es:
```java
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
            }
```
añadir la opacidad de las ajenas:
```java
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
                setOpacity(item instanceof ReparacionResumen rep && idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0);
            }
```

- [ ] **Step 7: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
git commit -m "feat(filtro-tecnico): filtro por técnico en vista agrupada del historial (supertécnico) con detalle opaco"
```

---

## Task 2: ReparacionControllerAdmin

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

**Interfaces:**
- Reutiliza el campo existente `Set<Integer> idsTecFiltro`.
- Produce el campo `Set<String> idsAjenas` (privado, uso interno del controller).

- [ ] **Step 1: Añadir el campo `idsAjenas`**

Junto a `private final Set<Integer> idsTecFiltro = new HashSet<>();`, añadir:
```java
    private final Set<String> idsAjenas = new HashSet<>();
```

- [ ] **Step 2: Mostrar el filtro de técnico en la vista agrupada**

En `adaptarFiltrosMaestro()`, cambiar:
```java
        filtroTecnico.setVisible(false); filtroTecnico.setManaged(false);
```
por:
```java
        filtroTecnico.setVisible(true); filtroTecnico.setManaged(true);
```

- [ ] **Step 3: Limpiar `idsAjenas` al inicio de `aplicarFiltros()`**

En `aplicarFiltros()`, tras leer los filtros (después de `boolean filtrarNormales = cbNormales.isSelected();`), añadir:
```java
        idsAjenas.clear();
```

- [ ] **Step 4: Filtrar grupos por intervención del técnico en `buildTablaItems()`**

Dentro del bucle de `buildTablaItems()`, tras `GrupoImei grupo = new GrupoImei(e.getKey(), e.getValue());`, añadir:
```java
            if (!idsTecFiltro.isEmpty()
                    && e.getValue().stream().noneMatch(r -> idsTecFiltro.contains(r.getIdTec())))
                continue;
```

- [ ] **Step 5: Reescribir el bloque DETALLE de `aplicarFiltros()`**

Reemplazar el bloque `if (modoActual == Modo.DETALLE) { ... }` por:
```java
        if (modoActual == Modo.DETALLE) {
            lblContadorPlano.setVisible(false); lblContadorPlano.setManaged(false);
            List<ReparacionResumen> todas = datos.stream()
                .filter(r -> r.getImei().equals(imeiDetalle))
                .filter(rep -> {
                    if (desde != null || hasta != null) {
                        if (rep.getFechaFin() == null) return false;
                        LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
                        if (desde != null && fechaFin.isBefore(desde)) return false;
                        if (hasta != null && fechaFin.isAfter(hasta))  return false;
                    }
                    if (filtrarAbiertas || filtrarCerradas || filtrarNormales) {
                        boolean mostrar = false;
                        if (filtrarNormales && !rep.isEsIncidencia())                        mostrar = true;
                        if (filtrarAbiertas && rep.isEsIncidencia() && !rep.isEsResuelto()) mostrar = true;
                        if (filtrarCerradas && rep.isEsIncidencia() &&  rep.isEsResuelto()) mostrar = true;
                        if (!mostrar) return false;
                    }
                    return true;
                }).collect(Collectors.toList());

            if (idsTecFiltro.isEmpty()) {
                tablaItems.setAll(todas);
                lblNavCount.setText("  •  " + todas.size() + " reparaci" + (todas.size() == 1 ? "ón" : "ones"));
            } else {
                List<ReparacionResumen> delFiltro = todas.stream()
                    .filter(r -> idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                List<ReparacionResumen> otras = todas.stream()
                    .filter(r -> !idsTecFiltro.contains(r.getIdTec())).collect(Collectors.toList());
                otras.forEach(r -> idsAjenas.add(r.getIdRep()));
                List<ReparacionResumen> resultado = new ArrayList<>(delFiltro);
                resultado.addAll(otras);
                tablaItems.setAll(resultado);
                int nO = otras.size();
                lblNavCount.setText("  •  " + delFiltro.size() + " de filtrados"
                    + (nO > 0 ? " + " + nO + " de otros" : ""));
            }
            return;
        }
```

- [ ] **Step 6: Aplicar la opacidad en el rowFactory**

En el `updateItem` del `rowFactory` de `tablaReparaciones`, que hoy es:
```java
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
            }
```
añadir:
```java
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                aplicarEstilo(item, empty);
                setOpacity(item instanceof ReparacionResumen rep && idsAjenas.contains(rep.getIdRep()) ? 0.45 : 1.0);
            }
```

- [ ] **Step 7: Compilar**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Test manual (cubre ambos controllers)**

Arrancar servidor + cliente. Como **supertécnico** y como **admin**:
1. Vista agrupada → el filtro de técnico **aparece**.
2. Seleccionar un técnico → solo IMEIs donde intervino; varios → IMEIs donde intervino alguno (OR).
3. Abrir un IMEI filtrado → arriba las de los técnicos filtrados, debajo y **atenuadas** las de otros (si las hay); contador "X de filtrados + Y de otros".
4. Sin filtro → agrupada y detalle como antes (todas normales, sin atenuar).
5. Vista **plana** → sin cambios y **sin filas atenuadas** (verifica el reset de `idsAjenas`: entra en un detalle filtrado, vuelve a grupos, pasa a plano → nada atenuado).

- [ ] **Step 9: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java
git commit -m "feat(filtro-tecnico): filtro por técnico en vista agrupada del historial (admin) con detalle opaco"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Task/Step |
|---|---|
| Mostrar filtro de técnico en MAESTRO | T1/T2 Step 2 |
| Filtrar grupos por intervención (OR), conservando todas las reparaciones | T1/T2 Step 4 |
| DETALLE: todas las reparaciones, filtrados arriba | T1/T2 Step 5 |
| DETALLE: resto atenuado (idsAjenas + opacity 0.45) | T1/T2 Steps 5 y 6 |
| DETALLE sin filtro: todas normales | T1/T2 Step 5 (rama `idsTecFiltro.isEmpty()`) |
| Contador "X de filtrados + Y de otros" / "X reparaciones" | T1/T2 Step 5 |
| Mecanismo de opacidad en rowFactory | T1/T2 Step 6 |
| `idsAjenas` no afecta a la vista PLANA | T1/T2 Step 3 (clear al inicio de aplicarFiltros) |
| Aplica a Super y Admin | Tasks 1 y 2 |
| PLANA sin cambios | No se toca el bloque PLANO |
| Solo cliente | Todo el plan |

### Notas
- Sin TDD (UI); verificación por compilación + manual, con foco en la regresión de la vista PLANA (Step 8 punto 5).
- Tasks 1 y 2 son simétricas (mismo cambio en controllers gemelos); el test manual del Step 8 de la Task 2 valida ambos roles.
- El bloque DETALLE deja de filtrar por `idsTecFiltro` a nivel de descarte; ahora particiona en filtrados/otros. El filtro de fecha e incidencias se mantiene aplicado a todas.
