# Pegado de lote de IMEIs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el campo de escaneo de los modales de asignación (reparaciones normales y pulidos) acepte un pegado de varios IMEIs concatenados y los reparta cada 15 dígitos en varias entradas del lote, rechazando entero un lote corrupto.

**Architecture:** Un helper puro `ImeiUtils.parsearPegadoImeis(String)` clasifica el texto (INCOMPLETO/UNICO/LOTE/CORRUPTO). En cada modal se sustituye la rama del listener que hoy trunca a 15 dígitos por: corrupto → error amable; lote → añadir cada IMEI con la lógica de "añadir uno" existente (con dedup contra el lote completo). El escaneo single (pistola, 15+Enter) no cambia.

**Tech Stack:** Java 21, JavaFX (UI en código en los controllers), JUnit 5 (Jupiter), Maven.

**Spec:** `docs/superpowers/specs/2026-06-29-pegado-lote-imeis-design.md`

## Global Constraints

- Parseo: quitar no-dígitos → `< 15` INCOMPLETO · `== 15` UNICO · `> 15` múltiplo de 15 → LOTE (troceado en 15) · `> 15` no múltiplo → CORRUPTO.
- El controlador **solo invoca el helper en la rama de `length() > 15`** del listener; `< 15` y `== 15` (escaneo con pistola) **no cambian**.
- Lote corrupto → **no añade nada** + mensaje exacto: `"Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos."`
- En lote, los IMEIs entran a **"Pendiente de asignar"** y **ninguno se auto-carga** en el formulario de configuración.
- Dedup contra el **lote completo** (pendientes + asignados/completados sin guardar, que son la misma lista interna).
- Sin checksum Luhn (fuera de alcance).
- Compilar/test desde `gestion-reparaciones-cliente/`. Git desde la raíz `ProgramaReparaciones`, rama `feat/pegado-lote-imeis`. Sin `Co-Authored-By`.

---

### Task 1: Helper `ImeiUtils.parsearPegadoImeis` + test JUnit

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ImeiUtils.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ImeiUtilsTest.java`

**Interfaces:**
- Produces: `ImeiUtils.TipoPegado` (enum: `INCOMPLETO, UNICO, LOTE, CORRUPTO`), `ImeiUtils.ResultadoPegado(TipoPegado tipo, List<String> imeis)` (record), `static ResultadoPegado ImeiUtils.parsearPegadoImeis(String)`.

- [ ] **Step 1: Escribir el test** `src/test/java/com/reparaciones/utils/ImeiUtilsTest.java` (mismo estilo que `PiezasTest`)

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.reparaciones.utils.ImeiUtils.TipoPegado.*;

class ImeiUtilsTest {

    @Test
    void incompleto_si_menos_de_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("12345");
        assertEquals(INCOMPLETO, r.tipo());
        assertTrue(r.imeis().isEmpty());
    }

    @Test
    void unico_si_exactamente_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812");
        assertEquals(UNICO, r.tipo());
        assertEquals(List.of("352680941087812"), r.imeis());
    }

    @Test
    void lote_si_multiplo_de_15() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812354739185728537");
        assertEquals(LOTE, r.tipo());
        assertEquals(List.of("352680941087812", "354739185728537"), r.imeis());
    }

    @Test
    void corrupto_si_mayor_de_15_y_no_multiplo() {
        assertEquals(CORRUPTO, ImeiUtils.parsearPegadoImeis("3526809410878123").tipo());   // 16
        assertEquals(CORRUPTO, ImeiUtils.parsearPegadoImeis("3".repeat(31)).tipo());        // 31
        assertTrue(ImeiUtils.parsearPegadoImeis("3".repeat(16)).imeis().isEmpty());
    }

    @Test
    void quita_separadores_y_no_digitos() {
        ImeiUtils.ResultadoPegado r = ImeiUtils.parsearPegadoImeis("352680941087812\n354739185728537");
        assertEquals(LOTE, r.tipo());
        assertEquals(2, r.imeis().size());
    }

    @Test
    void vacio_o_null_es_incompleto() {
        assertEquals(INCOMPLETO, ImeiUtils.parsearPegadoImeis("").tipo());
        assertEquals(INCOMPLETO, ImeiUtils.parsearPegadoImeis(null).tipo());
    }
}
```

- [ ] **Step 2: Ejecutar el test y ver que FALLA** (no compila: `ImeiUtils` no existe)

Run (desde `gestion-reparaciones-cliente/`): `mvn -o -Dtest=ImeiUtilsTest test`
Expected: FALLO de compilación (`cannot find symbol: class ImeiUtils`).

- [ ] **Step 3: Implementar `ImeiUtils`** `src/main/java/com/reparaciones/utils/ImeiUtils.java`

```java
package com.reparaciones.utils;

import java.util.ArrayList;
import java.util.List;

/** Parseo del campo de escaneo de IMEIs (single y pegado de lote concatenado). */
public final class ImeiUtils {

    private ImeiUtils() {}

    public enum TipoPegado { INCOMPLETO, UNICO, LOTE, CORRUPTO }

    public record ResultadoPegado(TipoPegado tipo, List<String> imeis) {}

    /**
     * Clasifica el texto del campo tras quitar todo lo no-numérico:
     * &lt;15 → INCOMPLETO · ==15 → UNICO · &gt;15 múltiplo de 15 → LOTE (troceado en 15) ·
     * &gt;15 no múltiplo → CORRUPTO.
     */
    public static ResultadoPegado parsearPegadoImeis(String texto) {
        String d = texto == null ? "" : texto.replaceAll("\\D", "");
        int len = d.length();
        if (len < 15)      return new ResultadoPegado(TipoPegado.INCOMPLETO, List.of());
        if (len == 15)     return new ResultadoPegado(TipoPegado.UNICO, List.of(d));
        if (len % 15 != 0) return new ResultadoPegado(TipoPegado.CORRUPTO, List.of());
        List<String> chunks = new ArrayList<>(len / 15);
        for (int i = 0; i < len; i += 15) chunks.add(d.substring(i, i + 15));
        return new ResultadoPegado(TipoPegado.LOTE, chunks);
    }
}
```

- [ ] **Step 4: Ejecutar el test y ver que PASA**

Run (desde `gestion-reparaciones-cliente/`): `mvn -o -Dtest=ImeiUtilsTest test`
Expected: `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (desde la raíz `ProgramaReparaciones`)

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ImeiUtils.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ImeiUtilsTest.java
git commit -m "feat(imei): helper ImeiUtils.parsearPegadoImeis (single/lote/corrupto) + test"
```

---

### Task 2: Pegado de lote en el modal de asignación (reparaciones normales)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `ImeiUtils.parsearPegadoImeis` (Task 1).

> En `abrirFormularioAsignacion`, el listener de `tfScan` (hoy ~líneas 1220-1233) tiene la rama `if (n.length() > 15) { recortado = n.substring(0,15); ... }`. Se reemplaza por el reparto del lote. El "añadir uno" (`intentarAnadir`, ~líneas 1207-1219) crea una `EntradaAsignacion`, le asigna `seq = ++seqCounter[0]`, la mete en `pila` y llama `renderPila[0].run()`; el bucle de lote replica eso **sin** `cargarEntrada[0].accept(...)` (no auto-cargar). `lblScanErr` es el label de mensajes.

- [ ] **Step 1: Sustituir la rama `> 15`** del listener de `tfScan`. Reemplaza exactamente este bloque:

```java
            if (n.length() > 15) {
                String recortado = n.substring(0, 15);
                javafx.application.Platform.runLater(() -> tfScan.setText(recortado));
                return;
            }
```

por:

```java
            if (n.length() > 15) {
                com.reparaciones.utils.ImeiUtils.ResultadoPegado res =
                        com.reparaciones.utils.ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == com.reparaciones.utils.ImeiUtils.TipoPegado.CORRUPTO) {
                    lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                            + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");
                    lblScanErr.setText("Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.");
                    javafx.application.Platform.runLater(tfScan::clear);
                    return;
                }
                int anadidos = 0, duplicados = 0;
                for (String imei : res.imeis()) {
                    if (pila.stream().anyMatch(x -> x.imei.equals(imei))) { duplicados++; continue; }
                    EntradaAsignacion en = new EntradaAsignacion(imei);
                    en.seq = ++seqCounter[0];
                    pila.add(en);
                    anadidos++;
                }
                renderPila[0].run();
                lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-min-height: 15;");
                lblScanErr.setText(anadidos + " IMEIs añadidos"
                        + (duplicados > 0 ? " · " + duplicados + " ya estaban en la lista." : "."));
                javafx.application.Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
                return;
            }
```

> Nota: en la rama de escaneo single (la que sigue, `if (n.length() == 15) intentarAnadir.run();`) y en `lblScanErr.setText("")` previo, conviene **restaurar el estilo de error** de `lblScanErr` por si quedó en verde de un lote anterior. Añade, justo donde hoy hace `lblScanErr.setText("");` (línea ~1231), antes del set:
> ```java
> lblScanErr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.reparaciones.utils.Colores.TEXTO_ERROR + "; -fx-min-height: 15;");
> ```

- [ ] **Step 2: Compilar**

Run (desde `gestion-reparaciones-cliente/`): `mvn -q -o compile`
Expected: EXIT 0.

- [ ] **Step 3: Verificación manual** (arrancando la app, sesión SUPERTECNICO → Asignaciones → "Asignar reparación")
  - Pegar 2-3 IMEIs concatenados (30/45 dígitos) → aparecen N entradas en "Pendiente de asignar"; ninguna abierta en el formulario; mensaje verde "N IMEIs añadidos".
  - Pegar un lote con un dígito de más (p. ej. 31) → no añade nada; mensaje rojo de corrupto.
  - Pegar incluyendo un IMEI ya presente → se salta, el resumen lo refleja.
  - Escanear/teclear un IMEI de 15 (uno a uno) → funciona igual que antes (incluye auto-cargar en el form).

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(asignacion): pegar lote de IMEIs concatenados en el modal de asignación"
```

---

### Task 3: Pegado de lote en el modal de asignación de pulidos

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`

**Interfaces:**
- Consumes: `ImeiUtils.parsearPegadoImeis` (Task 1).

> En `abrirFormularioAsignacion`, `intentarEnviar` (Runnable, ~línea 489) lee `tfImei.getText()`, valida (15, dedup contra `lote`, técnico no nulo), crea un `ImeiConf`, lo mete en `lote`, y **construye una fila UI** (editor expandible por IMEI). El listener de `tfImei` (~líneas 658-671) tiene la misma rama `if (n.length() > 15) { recortado... }` que el de asignación.

- [ ] **Step 1: Refactor — extraer el "añadir uno" a un `Consumer<String>`.**
  Convierte el cuerpo de creación de `intentarEnviar` en un consumer reutilizable que recibe el `imei` ya validado:
  - Declara, justo **antes** de `Runnable intentarEnviar`:
    ```java
    java.util.function.Consumer<String> agregarUnoPulido = imei -> {
        // (cuerpo existente de intentarEnviar desde "Tecnico[] tecSel = {tec};" / "ImeiConf entry = ..."
        //  hasta el final de la construcción de la fila), con estos cambios:
        //   - al principio: Tecnico tec = cbTecnico.getValue();   (el caller garantiza que no es null)
        //   - usar el parámetro `imei` (ya no `tfImei.getText().trim()`)
    };
    ```
    Es decir: mueve TODO el bloque que hoy va desde la obtención del técnico/creación del `ImeiConf` hasta el cierre del builder de la fila al interior de `agregarUnoPulido`, parametrizado por `imei`. **No cambies el contenido del builder de fila** (estilos, botones editar/✕, expandible) — solo de dónde viene `imei` y que `tec` se toma de `cbTecnico.getValue()` dentro del consumer.
  - `intentarEnviar` queda solo con las validaciones + llamada al consumer:
    ```java
    Runnable intentarEnviar = () -> {
        String imei = tfImei.getText().trim();
        if (imei.length() != 15) return;
        if (lote.stream().anyMatch(e -> e.imei().equals(imei))) { lblError.setText("Este IMEI ya está en la lista."); return; }
        if (cbTecnico.getValue() == null) { lblError.setText("Selecciona un técnico primero."); return; }
        lblError.setText("");
        agregarUnoPulido.accept(imei);
    };
    ```
    > Conserva cualquier limpieza/foco del campo que `intentarEnviar` hiciera al final (si lo hacía dentro del builder, queda dentro del consumer; revisa el final del bloque original al moverlo).

- [ ] **Step 2: Sustituir la rama `> 15`** del listener de `tfImei`. Reemplaza exactamente:

```java
            if (n.length() > 15) {
                String recortado = n.substring(0, 15);
                javafx.application.Platform.runLater(() -> tfImei.setText(recortado));
                return;
            }
```

por:

```java
            if (n.length() > 15) {
                com.reparaciones.utils.ImeiUtils.ResultadoPegado res =
                        com.reparaciones.utils.ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == com.reparaciones.utils.ImeiUtils.TipoPegado.CORRUPTO) {
                    lblError.setText("Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.");
                    javafx.application.Platform.runLater(tfImei::clear);
                    return;
                }
                if (cbTecnico.getValue() == null) {
                    lblError.setText("Selecciona un técnico primero.");
                    javafx.application.Platform.runLater(tfImei::clear);
                    return;
                }
                int anadidos = 0, duplicados = 0;
                for (String imei : res.imeis()) {
                    if (lote.stream().anyMatch(e -> e.imei().equals(imei))) { duplicados++; continue; }
                    agregarUnoPulido.accept(imei);
                    anadidos++;
                }
                lblError.setText(anadidos + " IMEIs añadidos"
                        + (duplicados > 0 ? " · " + duplicados + " ya estaban en la lista." : "."));
                javafx.application.Platform.runLater(tfImei::clear);
                return;
            }
```

- [ ] **Step 3: Compilar**

Run (desde `gestion-reparaciones-cliente/`): `mvn -q -o compile`
Expected: EXIT 0.

- [ ] **Step 4: Verificación manual** (app, SUPERTECNICO → Pulidos → "Asignar pulidos")
  - Seleccionar técnico por defecto, pegar 2-3 IMEIs concatenados → aparecen N filas en la lista; mensaje "N IMEIs añadidos".
  - Pegar sin técnico seleccionado → no añade nada, mensaje "Selecciona un técnico primero".
  - Pegar lote corrupto (no múltiplo de 15) → rechazo + mensaje de corrupto.
  - Escaneo/tecleo single de 15 → igual que antes.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "feat(pulidos): pegar lote de IMEIs concatenados en el modal de asignación de pulidos"
```

---

## Self-Review (cobertura del spec)

- §4.1 helper (4 tipos, troceo en 15, quita no-dígitos) → Task 1 (código + 6 tests) ✔.
- §4.2 enganche solo en `>15`, lote no auto-carga, corrupto rechaza → Task 2/3 ✔.
- §4.3 mensajes (corrupto exacto; resumen) → Task 2/3 ✔.
- §4.4 dedup contra lote completo → Task 2 (`pila`) / Task 3 (`lote`) ✔.
- §5 casos límite → cubiertos por el switch del helper + verificación manual ✔.
- §6 roles (modales SUPERTECNICO) → sin cambios, la feature vive dentro ✔.
- §7 testing (helper JUnit; UI manual) → Task 1 test + pasos de verificación manual ✔.
- §8 Luhn fuera de alcance → no se implementa ✔.

**Nota de tipos/consistencia:** `ImeiUtils.parsearPegadoImeis` / `ResultadoPegado.tipo()` / `.imeis()` / `TipoPegado.CORRUPTO|LOTE` se usan idénticos en Task 1 (definición) y Tasks 2-3 (consumo).
