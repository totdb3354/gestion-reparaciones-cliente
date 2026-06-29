# Pegado de lote en los filtros de IMEI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el campo de filtro de IMEI (8 vistas, todos los roles) acepte un pegado de varios IMEIs concatenados y los reparta cada 15 dígitos, integrándose en el filtrado multi-IMEI por comas que ya existe, sin reject-all (el resto <15 queda incompleto → borde rojo, ya existente).

**Architecture:** Un helper puro `FiltroImei` (utils) centraliza el parseo del campo: `canonicalizar` (limpia + **parte cada token de >15 en trozos de 15** + auto-`", "`), `imeisValidos` (sustituye al `parsearImeis` duplicado) y `estado` (VACIO/INCOMPLETO/VALIDO para el borde). Los 8 controllers migran a este helper; **cada uno conserva su propio estilo de borde y su llamada de filtrado** (2 vistas tienen estilos distintos).

**Tech Stack:** Java 21, JavaFX, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-06-29-filtro-imei-pegado-lote-design.md`

## Global Constraints

- `canonicalizar`: solo dígitos y comas; separar con `", "`; **partir cada token de >15 dígitos en trozos de 15** (el resto <15 queda como último token); añadir `", "` tras un IMEI completo de 15. **Idempotente**.
- **Sin reject-all**: un trozo <15 queda como token incompleto (no se rechaza nada).
- `imeisValidos` = tokens de exactamente 15 dígitos (idéntico al `parsearImeis` actual).
- `estado`: VACIO (sin texto) · INCOMPLETO (algún token <15) · VALIDO (hay ≥1 de 15).
- **Cero cambio visual ni de comportamiento**: cada controller conserva su `setStyle(...)` actual (6 estándar, PendientesTecnico recortado, ReparacionControllerTecnico hardcodeado) y su llamada de filtrado (`aplicarFiltros()`, o `aplicarFiltro()` en PulidoTecnico).
- `FiltroImei` es puro (String/Set/enum), sin JavaFX ni `Colores`.
- Compilar/test desde `gestion-reparaciones-cliente/`. Git desde la raíz `ProgramaReparaciones`, rama `feat/filtro-imei-lote`. Sin `Co-Authored-By`.

---

### Task 1: Helper `FiltroImei` + test JUnit

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/FiltroImei.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/FiltroImeiTest.java`

**Interfaces:**
- Produces: `FiltroImei.EstadoFiltro` (enum `VACIO, INCOMPLETO, VALIDO`); `static String FiltroImei.canonicalizar(String)`; `static java.util.Set<String> FiltroImei.imeisValidos(String)`; `static EstadoFiltro FiltroImei.estado(String)`.

- [ ] **Step 1: Escribir el test** `src/test/java/com/reparaciones/utils/FiltroImeiTest.java`

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.reparaciones.utils.FiltroImei.EstadoFiltro.*;

class FiltroImeiTest {

    @Test
    void canonicalizar_parte_un_blob_concatenado_cada_15() {
        assertEquals("352680941087812, 354739185728537, ",
                FiltroImei.canonicalizar("352680941087812354739185728537"));
    }

    @Test
    void canonicalizar_un_imei_completo_anade_separador() {
        assertEquals("352680941087812, ", FiltroImei.canonicalizar("352680941087812"));
    }

    @Test
    void canonicalizar_blob_con_resto_deja_el_resto_como_token() {
        assertEquals("352680941087812, 354739185728537, 12",
                FiltroImei.canonicalizar("35268094108781235473918572853712"));
    }

    @Test
    void canonicalizar_quita_no_digitos_y_normaliza_separadores() {
        assertEquals("352680941087812, ", FiltroImei.canonicalizar("352-680-941-087-812"));
    }

    @Test
    void canonicalizar_es_idempotente() {
        String[] entradas = {"", "3526", "352680941087812",
                "352680941087812354739185728537", "35268094108781235473918572853712"};
        for (String e : entradas) {
            String once = FiltroImei.canonicalizar(e);
            assertEquals(once, FiltroImei.canonicalizar(once), "no idempotente para: " + e);
        }
    }

    @Test
    void canonicalizar_vacio_o_null() {
        assertEquals("", FiltroImei.canonicalizar(""));
        assertEquals("", FiltroImei.canonicalizar(null));
    }

    @Test
    void imeisValidos_solo_los_de_15() {
        assertEquals(Set.of("352680941087812", "354739185728537"),
                FiltroImei.imeisValidos("352680941087812, 354739185728537, 12"));
        assertTrue(FiltroImei.imeisValidos("").isEmpty());
        assertTrue(FiltroImei.imeisValidos("12, 34").isEmpty());
    }

    @Test
    void estado_clasifica() {
        assertEquals(VACIO, FiltroImei.estado(""));
        assertEquals(VACIO, FiltroImei.estado("   "));
        assertEquals(INCOMPLETO, FiltroImei.estado("3526"));
        assertEquals(INCOMPLETO, FiltroImei.estado("352680941087812, 12"));
        assertEquals(VALIDO, FiltroImei.estado("352680941087812, "));
        assertEquals(VALIDO, FiltroImei.estado("352680941087812, 354739185728537, "));
    }
}
```

- [ ] **Step 2: Ejecutar el test y ver que FALLA** (no existe `FiltroImei`)

Run (desde `gestion-reparaciones-cliente/`): `mvn -o -Dtest=FiltroImeiTest test`
Expected: fallo de compilación (`cannot find symbol: class FiltroImei`).

- [ ] **Step 3: Implementar `FiltroImei`** `src/main/java/com/reparaciones/utils/FiltroImei.java`

```java
package com.reparaciones.utils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Parseo y clasificación del campo de filtro de IMEI (multi-IMEI por comas + pegado de lote). */
public final class FiltroImei {

    private FiltroImei() {}

    public enum EstadoFiltro { VACIO, INCOMPLETO, VALIDO }

    /** Forma canónica: solo dígitos y comas, separa con ", ", parte cada token de >15
     *  dígitos en trozos de 15 (el resto <15 queda como último token) y añade ", " tras
     *  un IMEI completo. Idempotente. */
    public static String canonicalizar(String texto) {
        if (texto == null) return "";
        String withoutSep = texto.replace(", ", ",");
        String limpio = withoutSep.replaceAll("[^\\d,]", "").replaceAll(",+", ",").replaceAll("^,", "");
        String[] tokens = limpio.split(",", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(',');
            String t = tokens[i];
            if (t.length() > 15) {
                for (int j = 0; j < t.length(); j += 15) {
                    if (j > 0) sb.append(',');
                    sb.append(t, j, Math.min(j + 15, t.length()));
                }
            } else {
                sb.append(t);
            }
        }
        String split = sb.toString();
        String display = split.replace(",", ", ");
        String[] partes = split.split(",", -1);
        if (partes[partes.length - 1].length() == 15 && !display.endsWith(", ")) {
            display = display + ", ";
        }
        return display;
    }

    /** IMEIs de exactamente 15 dígitos presentes en el texto (sustituye a parsearImeis). */
    public static Set<String> imeisValidos(String texto) {
        if (texto == null || texto.isBlank()) return Set.of();
        return Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> s.length() == 15)
                .collect(Collectors.toSet());
    }

    /** Clasifica el contenido para el borde: VACIO · INCOMPLETO (token <15) · VALIDO (hay 15). */
    public static EstadoFiltro estado(String texto) {
        if (texto == null || texto.trim().isEmpty()) return EstadoFiltro.VACIO;
        boolean hayIncompleto = Arrays.stream(texto.split(",", -1))
                .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(s -> s.length() < 15);
        if (hayIncompleto) return EstadoFiltro.INCOMPLETO;
        return imeisValidos(texto).isEmpty() ? EstadoFiltro.VACIO : EstadoFiltro.VALIDO;
    }
}
```

- [ ] **Step 4: Ejecutar el test y ver que PASA**

Run (desde `gestion-reparaciones-cliente/`): `mvn -o -Dtest=FiltroImeiTest test`
Expected: `BUILD SUCCESS`, `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (desde la raíz `ProgramaReparaciones`)

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/FiltroImei.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/FiltroImeiTest.java
git commit -m "feat(imei): helper FiltroImei (canonicalizar+split/imeisValidos/estado) + test"
```

---

## Receta de migración (común a Tasks 2 y 3)

Para **cada** controller:

**(a) Sustituir el cuerpo del listener** `filtroImei.textProperty().addListener((obs, o, n) -> { ... })` por:

```java
        filtroImei.textProperty().addListener((obs, o, n) -> {
            String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
            if (!can.equals(n)) {
                javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
                return;
            }
            switch (com.reparaciones.utils.FiltroImei.estado(n)) {
                case VACIO      -> filtroImei.setStyle("");
                case INCOMPLETO -> filtroImei.setStyle(/* ESTILO-INCOMPLETO ACTUAL DE ESTA VISTA, verbatim */);
                case VALIDO     -> filtroImei.setStyle(/* ESTILO-VALIDO ACTUAL DE ESTA VISTA, verbatim */);
            }
            <LLAMADA DE FILTRO DE ESTA VISTA>;   // aplicarFiltros();  (o aplicarFiltro(); en PulidoTecnico)
        });
```
> **Importante:** en `INCOMPLETO`/`VALIDO`, copia **los mismos strings de `setStyle`** que la vista tiene hoy (lee el listener actual del fichero y reutilízalos tal cual). No los unifiques. Conserva la indentación/estructura circundante (en PendientesTecnico el listener está dentro de un `if`).

**(b) Sustituir las llamadas a `parsearImeis`** en la lógica de filtrado por `com.reparaciones.utils.FiltroImei.imeisValidos(...)` (mismo argumento). En el listener ya no se llama (lo reemplaza `estado(n)`).

**(c) Borrar** el método privado `private static java.util.Set<String> parsearImeis(String texto) { ... }` del controller (ya no se usa).

Tras los 3 pasos en un fichero, **no debe quedar ninguna referencia a `parsearImeis`** en él.

---

### Task 2: Migrar los 5 controllers con un solo sitio de filtrado

**Files (Modify):**
- `.../controllers/HistorialPulidoController.java`
- `.../controllers/PendientesTecnicoController.java`
- `.../controllers/PulidoTecnicoController.java`
- `.../controllers/PendientesSuperTecnicoController.java`
- `.../controllers/PulidoSuperTecnicoController.java`

(todos bajo `gestion-reparaciones-cliente/src/main/java/com/reparaciones/`)

**Interfaces:**
- Consumes: `FiltroImei.canonicalizar`, `FiltroImei.imeisValidos`, `FiltroImei.estado` (Task 1).

Aplica la **Receta de migración** a cada uno. Datos por fichero:

| Controller | Llamada de filtro | Estilo del borde | Sitios de `parsearImeis` a cambiar |
|------------|-------------------|------------------|-----------------------------------|
| HistorialPulido | `aplicarFiltros()` | estándar | 1 (en `aplicarFiltros`) + el del listener (se elimina) |
| **PendientesTecnico** | `aplicarFiltros()` | **recortado** (solo fondo + `-fx-border-color`, SIN redondeo/padding/font) | 1 + listener |
| PulidoTecnico | **`aplicarFiltro()`** (singular) | estándar | 1 + listener |
| PendientesSuperTecnico | `aplicarFiltros()` | estándar | 1 + listener |
| PulidoSuperTecnico | `aplicarFiltros()` | estándar | 1 + listener |

> **PendientesTecnico**: su `setStyle` de INCOMPLETO/VALIDO es la versión **corta** (p. ej. `"-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-border-color: " + Colores.FILA_INCIDENCIA_BRD + ";"` para incompleto y `...FILA_REPARADO_ICO...` para válido). Cópialos **tal cual**, no la versión estándar.

- [ ] **Step 1:** Aplicar la receta (a)+(b)+(c) a los 5 ficheros.
- [ ] **Step 2: Compilar** — Run (desde `gestion-reparaciones-cliente/`): `mvn -q -o compile`. Expected: EXIT 0. (Si queda algún `parsearImeis` sin migrar en alguno, el borrado del método dará error de compilación → migra ese call-site.)
- [ ] **Step 3: Verificación manual** (app): en una de estas vistas, pegar `352680941087812354739185728537` en el filtro → se convierte en `352680941087812, 354739185728537, ` (borde verde) y filtra; pegar 47 dígitos → termina en `, 12` con borde **rojo** y filtra por los válidos; teclear un IMEI suelto → igual que antes; comprobar que el **borde se ve igual que antes** en cada vista (incl. PendientesTecnico).
- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/HistorialPulidoController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoTecnicoController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git commit -m "feat(filtro): pegado de lote de IMEIs en 5 vistas (helper FiltroImei)"
```

---

### Task 3: Migrar los 3 controllers de reparaciones (dos sitios de filtrado)

**Files (Modify):**
- `.../controllers/ReparacionControllerAdmin.java`
- `.../controllers/ReparacionControllerSuperTecnico.java`
- `.../controllers/ReparacionControllerTecnico.java`

**Interfaces:**
- Consumes: `FiltroImei.canonicalizar`, `FiltroImei.imeisValidos`, `FiltroImei.estado` (Task 1).

Aplica la **Receta de migración** a cada uno. Estos tienen **DOS** sitios de filtrado con `parsearImeis` (agrupado + plano/detalle), además del listener. Datos por fichero:

| Controller | Llamada de filtro | Estilo del borde | Sitios de `parsearImeis` a cambiar |
|------------|-------------------|------------------|-----------------------------------|
| ReparacionControllerAdmin | `aplicarFiltros()` | estándar | 2 (filtrado agrupado + plano) + listener |
| ReparacionControllerSuperTecnico | `aplicarFiltros()` | estándar | 2 + listener |
| **ReparacionControllerTecnico** | `aplicarFiltros()` | **hardcodeado** (fondo `#F3F3F3`, verde `#8AC7AF`) | 2 + listener |

> **ReparacionControllerTecnico**: su `setStyle` usa colores hardcodeados (incompleto: `"-fx-background-color: #F3F3F3; -fx-border-color: " + Colores.FILA_INCIDENCIA_BRD + ";..."`; válido: `"-fx-background-color: #F3F3F3; -fx-border-color: #8AC7AF;..."`). Cópialos **tal cual**, NO uses `FONDO_INPUT`/`FILA_REPARADO_ICO`.

- [ ] **Step 1:** Aplicar la receta (a)+(b)+(c) a los 3 ficheros. Recuerda: **2 call-sites de `parsearImeis`** por fichero a migrar antes de borrar el método.
- [ ] **Step 2: Compilar** — Run (desde `gestion-reparaciones-cliente/`): `mvn -q -o compile`. Expected: EXIT 0.
- [ ] **Step 3: Verificación manual** (app): en el historial de cada rol, en **agrupado y en plano**, pegar un blob concatenado → se reparte y filtra en ambos modos; pegar con resto → borde rojo + filtra por válidos; borde igual que antes (incl. el look hardcodeado de Tecnico).
- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java
git commit -m "feat(filtro): pegado de lote de IMEIs en historial (3 roles, agrupado y plano)"
```

---

## Self-Review (cobertura del spec)

- §4.1 helper (canonicalizar+split, imeisValidos, estado enum) → Task 1 (código + 8 tests) ✔.
- §4.2 migración (listener thin + estado→estilo propio + imeisValidos + borrar parsearImeis) → Tasks 2-3 (receta) ✔.
- §5 casos (split, resto en rojo, single, vacío) → tests del helper + verificación manual ✔.
- §6 diferencias preservadas (PendientesTecnico recortado, ReparacionControllerTecnico hardcodeado, PulidoTecnico `aplicarFiltro`) → tablas de Task 2/3 ✔.
- §7 roles (todas las vistas) → 8 controllers migrados ✔.
- §8 testing (JUnit helper; UI manual) → Task 1 test + pasos manuales ✔.
- §9 Luhn fuera de alcance → no se implementa ✔.

**Consistencia de tipos:** `FiltroImei.canonicalizar(String):String`, `imeisValidos(String):Set<String>`, `estado(String):EstadoFiltro` (`VACIO/INCOMPLETO/VALIDO`) se definen en Task 1 y se consumen igual en Tasks 2-3.
