# Urgente por cliente vencido + filtros de cliente y pieza — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el urgente solo se automatice cuando una reparación pendiente con cliente cruza de día (job en servidor), quitar el urgente-por-defecto al asignar con cliente, y añadir filtros de Cliente (agrupado) y Pieza (plano) al historial.

**Architecture:** El job vive en el servidor (Spring `@Scheduled`, una sola autoridad → sin carreras). Los filtros y el cambio del modal viven en el cliente JavaFX y siguen el patrón existente `filtroTecnico` + `aplicarFiltros()`. Sin migración de BD (se reutiliza el flag `URGENTE`).

**Tech Stack:** Java 17, Spring Boot + JdbcTemplate + MariaDB (servidor); JavaFX (cliente); JUnit 5 + Mockito (tests).

## Global Constraints

- Sin cambios de esquema de BD. El estado se persiste en el flag `URGENTE` de `Reparacion`.
- "Asignación de reparación pendiente" = `ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND FECHA_FIN IS NULL` (los `AP%` son pulidos y quedan **excluidos**).
- Cualquier `UPDATE` sobre `Reparacion` que no deba alterar el optimistic-lock incluye `UPDATED_AT = UPDATED_AT`.
- Zona horaria de referencia: `Europe/Madrid`. Las fechas en BD están en UTC.
- Categorías de pieza y sus etiquetas (de `FormularioReparacionController.traducirTipo`): `g`→Glass, `lcd`→Pantalla, `mc`→Marco, `bat`→Batería, `cam`→Cámara, `cha`→Chasis, `otro`→Otros.
- Filtro de **Cliente**: solo en modo Agrupado. Filtro de **Pieza**: solo en modo Plano. Ambos en las 3 vistas (supertécnico, admin, técnico) y barra de filtros responsive.
- Idioma de commits: español, sin `Co-Authored-By`.

---

## File Structure

**Servidor (`gestion-reparaciones-servidor/`):**
- `src/main/java/com/reparaciones/servidor/App.java` — añadir `@EnableScheduling`.
- `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` — nuevo método `marcarUrgentesClienteVencidas(Timestamp)`.
- `src/main/java/com/reparaciones/servidor/job/UrgenteAutomaticoJob.java` — **nuevo**: tarea programada + cálculo de cutoff + log resumen.
- `src/test/java/com/reparaciones/servidor/job/UrgenteAutomaticoJobTest.java` — **nuevo**: test del cutoff y de la llamada/log.

**Cliente (`gestion-reparaciones-cliente/`):**
- `src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` — quitar urgente-por-defecto.
- `src/main/java/com/reparaciones/utils/Piezas.java` — **nuevo**: `categoria(String sku)` (prefijo→etiqueta).
- `src/test/java/com/reparaciones/utils/PiezasTest.java` — **nuevo**.
- `src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` (+ `...Admin.java`, `...Tecnico.java`) — filtros Cliente y Pieza.
- `src/main/resources/views/ReparacionViewSuperTecnico.fxml`* (+ `ReparacionViewAdmin.fxml`, `ReparacionViewTecnico.fxml`) — controles de filtro + contenedor responsive.
  *(usar el nombre real del FXML del supertécnico; confirmar en el repo).*

---

## Task 1: Servidor — job de urgente automático

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/App.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/job/UrgenteAutomaticoJob.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/job/UrgenteAutomaticoJobTest.java`

**Interfaces:**
- Produces:
  - `ReparacionDAO.marcarUrgentesClienteVencidas(Timestamp cutoffUtc) -> int` (nº de filas marcadas).
  - `UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock clock) -> Timestamp` (static, testeable).
  - `UrgenteAutomaticoJob.ejecutar()` (método anotado `@Scheduled`).

- [ ] **Step 1: Escribir el test del cutoff (falla)**

`UrgenteAutomaticoJobTest.java`:
```java
package com.reparaciones.servidor.job;

import com.reparaciones.servidor.dao.ReparacionDAO;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UrgenteAutomaticoJobTest {

    @Test
    void cutoff_esInicioDeHoyEnMadrid_comoInstanteUtc() {
        // 2026-06-24 09:30 Madrid (verano = UTC+2) -> inicio de hoy Madrid = 2026-06-23 22:00 UTC
        Clock fixed = Clock.fixed(Instant.parse("2026-06-24T07:30:00Z"), ZoneId.of("Europe/Madrid"));
        Timestamp cutoff = UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(fixed);
        assertEquals(Instant.parse("2026-06-23T22:00:00Z"), cutoff.toInstant());
    }

    @Test
    void ejecutar_llamaAlDaoConElCutoff() {
        ReparacionDAO dao = mock(ReparacionDAO.class);
        when(dao.marcarUrgentesClienteVencidas(any())).thenReturn(3);
        UrgenteAutomaticoJob job = new UrgenteAutomaticoJob(dao);
        job.ejecutar();
        verify(dao, times(1)).marcarUrgentesClienteVencidas(any(Timestamp.class));
    }
}
```

- [ ] **Step 2: Ejecutar el test (debe fallar al no compilar)**

Run: `cd gestion-reparaciones-servidor && mvn -q -Dtest=UrgenteAutomaticoJobTest test`
Expected: FAIL de compilación (`UrgenteAutomaticoJob` no existe).

- [ ] **Step 3: Crear `UrgenteAutomaticoJob`**

`UrgenteAutomaticoJob.java`:
```java
package com.reparaciones.servidor.job;

import com.reparaciones.servidor.dao.ReparacionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class UrgenteAutomaticoJob {

    private static final Logger log = LoggerFactory.getLogger(UrgenteAutomaticoJob.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ReparacionDAO reparacionDAO;

    public UrgenteAutomaticoJob(ReparacionDAO reparacionDAO) {
        this.reparacionDAO = reparacionDAO;
    }

    /** Inicio del día de hoy en Madrid, como Timestamp del instante UTC equivalente. */
    static Timestamp cutoffInicioDeHoyMadrid(Clock clock) {
        return Timestamp.from(LocalDate.now(clock).atStartOfDay(MADRID).toInstant());
    }

    /** A las 00:00 Europe/Madrid marca como urgentes las asignaciones de reparación
     *  pendientes con cliente cuya fecha de asignación es de un día anterior a hoy. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Madrid")
    public void ejecutar() {
        Timestamp cutoff = cutoffInicioDeHoyMadrid(Clock.system(MADRID));
        int n = reparacionDAO.marcarUrgentesClienteVencidas(cutoff);
        if (n > 0) log.info("AUTO_URGENTE: {} asignaciones marcadas como urgentes", n);
    }
}
```

- [ ] **Step 4: Añadir el método al DAO**

En `ReparacionDAO.java`, añadir (usa el `jdbc` JdbcTemplate ya presente):
```java
/** Marca URGENTE=true en asignaciones de reparación pendientes, con cliente,
 *  cuya FECHA_ASIG es anterior al cutoff (inicio de hoy en Madrid). Devuelve nº de filas. */
public int marcarUrgentesClienteVencidas(java.sql.Timestamp cutoffUtc) {
    return jdbc.update(
        "UPDATE Reparacion r JOIN Telefono t ON t.IMEI = r.IMEI " +
        "SET r.URGENTE = TRUE, r.UPDATED_AT = r.UPDATED_AT " +
        "WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' " +
        "  AND r.FECHA_FIN IS NULL " +
        "  AND t.ID_CLI IS NOT NULL " +
        "  AND r.URGENTE = FALSE " +
        "  AND r.FECHA_ASIG < ?",
        cutoffUtc);
}
```

- [ ] **Step 5: Habilitar scheduling**

En `App.java`, añadir el import `org.springframework.scheduling.annotation.EnableScheduling;` y la anotación `@EnableScheduling` junto a `@SpringBootApplication`.

- [ ] **Step 6: Ejecutar el test (debe pasar)**

Run: `cd gestion-reparaciones-servidor && mvn -q -Dtest=UrgenteAutomaticoJobTest test`
Expected: PASS (2 tests).

- [ ] **Step 7: Compilar todo y correr la suite**

Run: `cd gestion-reparaciones-servidor && mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/App.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/job/UrgenteAutomaticoJob.java \
        gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/job/UrgenteAutomaticoJobTest.java
git commit -m "feat(urgente): job 00:00 Madrid marca urgentes las asignaciones con cliente vencidas (>1 día)"
```

> Nota: el `UPDATE` (join Reparacion/Telefono, comparación de fecha) se valida en preproducción contra MariaDB; no hay infra de tests de DAO contra BD en el servidor (igual que en cambios previos). El test cubre el cálculo del cutoff y la orquestación del job.

---

## Task 2: Cliente — quitar urgente automático al asignar

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (en el guardado de asignaciones, ~línea 1256)

**Interfaces:** ninguna nueva.

- [ ] **Step 1: Cambiar el flag urgente a false**

Localizar el bloque de guardado:
```java
Integer idCli = e.cliente != null ? e.cliente.getIdCli() : null;
telefonoDAO.insertar(e.imei, e.modeloCode, idCli);
boolean urgente = idCli != null;
```
Reemplazar la última línea por:
```java
boolean urgente = false;   // el urgente ya no se automatiza al asignar (lo hace el job por vencimiento)
```
(El resto del bloque, incl. `insertarAsignacion(..., urgente)`, queda igual.)

- [ ] **Step 2: Compilar y correr la suite**

Run: `cd gestion-reparaciones-cliente && mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Verificación manual**

Asignar una reparación con cliente → la fila aparece **sin** badge "Urgente".

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(urgente): asignar con cliente ya no marca urgente automáticamente"
```

---

## Task 3: Cliente — util de categoría de pieza

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Piezas.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PiezasTest.java`

**Interfaces:**
- Produces: `Piezas.categoria(String sku) -> String` (etiqueta de categoría, o `""` si el SKU es null/no reconocido).

- [ ] **Step 1: Escribir el test (falla)**

`PiezasTest.java`:
```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PiezasTest {
    @Test
    void categoria_derivaDelPrefijoDelSku() {
        assertEquals("Glass",    Piezas.categoria("gi12negra"));
        assertEquals("Pantalla", Piezas.categoria("lcdi12negraic"));
        assertEquals("Marco",    Piezas.categoria("mci12negra"));
        assertEquals("Batería",  Piezas.categoria("bati12"));
        assertEquals("Cámara",   Piezas.categoria("cami12"));
        assertEquals("Chasis",   Piezas.categoria("chai12negro"));
        assertEquals("Otros",    Piezas.categoria("otroi8"));
    }

    @Test
    void categoria_vaciaSiNullODesconocido() {
        assertEquals("", Piezas.categoria(null));
        assertEquals("", Piezas.categoria("xyz123"));
    }
}
```

- [ ] **Step 2: Ejecutar el test (debe fallar)**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=PiezasTest test`
Expected: FAIL de compilación (`Piezas` no existe).

- [ ] **Step 3: Crear `Piezas`**

`Piezas.java`:
```java
package com.reparaciones.utils;

import java.util.List;
import java.util.Map;

/** Deriva la categoría de pieza (etiqueta legible) a partir del prefijo de un SKU. */
public final class Piezas {
    private Piezas() {}

    // Orden por longitud descendente para evitar ambigüedad (p.ej. "cha"/"cam" antes que "g").
    private static final List<String> PREFIJOS = List.of("otro", "cha", "cam", "bat", "lcd", "mc", "g");
    private static final Map<String, String> ETIQUETAS = Map.of(
            "bat", "Batería", "cha", "Chasis", "g", "Glass", "cam", "Cámara",
            "lcd", "Pantalla", "mc", "Marco", "otro", "Otros");

    /** @return etiqueta de categoría, o "" si el SKU es null o no empieza por un prefijo conocido. */
    public static String categoria(String sku) {
        if (sku == null) return "";
        String s = sku.toLowerCase();
        for (String p : PREFIJOS) {
            if (s.startsWith(p)) return ETIQUETAS.get(p);
        }
        return "";
    }
}
```

- [ ] **Step 4: Ejecutar el test (debe pasar)**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=PiezasTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Piezas.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PiezasTest.java
git commit -m "feat(filtros): util Piezas.categoria(sku) para derivar la categoría de pieza"
```

---

## Task 4: Cliente — filtro de Cliente en el historial agrupado (3 vistas) + barra responsive

**Files (aplicar a las TRES vistas):**
- Modify: `ReparacionControllerSuperTecnico.java`, `ReparacionControllerAdmin.java`, `ReparacionControllerTecnico.java`
- Modify: los 3 FXML correspondientes del historial.

**Interfaces:**
- Consumes: `ReparacionResumen.getCliente() -> String` (nombre del cliente o null/"").
- Patrón de referencia: el filtro `filtroTecnico` (`MultiSelectComboBox`) ya existente en `ReparacionControllerSuperTecnico` (declaración `@FXML`, helper de cableado ~líneas 972-977, `aplicarFiltros()`, show/hide por modo).

- [ ] **Step 1: Hacer la barra de filtros responsive (FXML, las 3 vistas)**

En cada FXML del historial, el `HBox` que contiene los filtros (Título · IMEI · Técnico · Desde · Hasta · Incidencias · Limpiar) se sustituye por un contenedor que **envuelve** en pantallas estrechas. Cambiar ese `HBox` por:
```xml
<FlowPane hgap="12" vgap="6" alignment="CENTER_LEFT">
    <!-- ... mismos hijos de filtro que ya había, en el mismo orden ... -->
</FlowPane>
```
Añadir el import `<?import javafx.scene.layout.FlowPane?>`. Mantener el contador y el toggle Agrupado/Plano donde estén (si estaban en el mismo HBox, dejarlos en una fila superior fija o al final del FlowPane según la vista; conservar su `fx:id`).

- [ ] **Step 2: Añadir el control de filtro de cliente (FXML, las 3 vistas)**

Dentro del `FlowPane`, **justo después** del `filtroTecnico` (en la vista del técnico, que no tiene `filtroTecnico`, colócalo tras el `filtroImei`), añadir:
```xml
<com.reparaciones.utils.MultiSelectComboBox fx:id="filtroCliente" prefWidth="150"/>
```

- [ ] **Step 3: Declarar el campo y cablear el filtro (controller, las 3 vistas)**

En cada controller, declarar el campo:
```java
@FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroCliente;
```
Mantener un conjunto con la selección y un texto-resumen, igual que `filtroTecnico`:
```java
private final java.util.Set<String> clientesFiltro = new java.util.HashSet<>();
private static final String SIN_CLIENTE = "(Sin cliente)";
```
Al cargar datos, poblar las opciones con los clientes presentes + "(Sin cliente)" y, al cambiar la selección, recalcular `clientesFiltro` y llamar a `aplicarFiltros()` (replicar el cableado de `filtroTecnico`: listener de selección → actualizar set → `aplicarFiltros()`).

- [ ] **Step 4: Aplicar el predicado en modo Agrupado (controller, las 3 vistas)**

En `aplicarFiltros()`, en la rama **Agrupado** (la que construye `datosFiltrados` antes de `buildTablaItems()`), añadir al `filter(...)` la condición de cliente:
```java
if (!clientesFiltro.isEmpty()) {
    String cli = rep.getCliente();
    boolean sin = (cli == null || cli.isEmpty());
    boolean coincide = (sin && clientesFiltro.contains(SIN_CLIENTE))
                    || (!sin && clientesFiltro.contains(cli));
    if (!coincide) return false;
}
```

- [ ] **Step 5: Mostrar el filtro solo en Agrupado (controller, las 3 vistas)**

En la lógica de cambio de modo (toggle Agrupado/Plano) y en los métodos que ya hacen `filtroTecnico.setVisible(...)`, gestionar la visibilidad del filtro de cliente:
- Modo Agrupado: `filtroCliente.setVisible(true); filtroCliente.setManaged(true);`
- Modo Plano / Detalle: `filtroCliente.setVisible(false); filtroCliente.setManaged(false);`

- [ ] **Step 6: Resetear en "Limpiar filtros" (controller, las 3 vistas)**

En el handler de `limpiarFiltros()`, limpiar también el filtro de cliente (vaciar `clientesFiltro`, deseleccionar el `MultiSelectComboBox`) antes de `aplicarFiltros()`.

- [ ] **Step 7: Compilar y correr la suite**

Run: `cd gestion-reparaciones-cliente && mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Verificación manual (las 3 vistas)**

En modo Agrupado aparece "Cliente▾" tras Técnico; filtra por cliente y por "(Sin cliente)"; en Plano el control desaparece; en pantalla estrecha los filtros bajan de línea; "Limpiar filtros" lo resetea.

- [ ] **Step 9: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionController*.java \
        gestion-reparaciones-cliente/src/main/resources/views/ReparacionView*.fxml
git commit -m "feat(filtros): filtro de cliente en el historial agrupado (3 vistas) + barra de filtros responsive"
```

---

## Task 5: Cliente — filtro de Pieza en el historial plano (3 vistas)

**Files (aplicar a las TRES vistas):**
- Modify: `ReparacionControllerSuperTecnico.java`, `ReparacionControllerAdmin.java`, `ReparacionControllerTecnico.java`
- Modify: los 3 FXML del historial.

**Interfaces:**
- Consumes: `Piezas.categoria(String sku)` (Task 3); `ReparacionResumen.getTipoComponente() -> String` (SKU crudo o null).

- [ ] **Step 1: Añadir el control de filtro de pieza (FXML, las 3 vistas)**

Dentro del `FlowPane` (Task 4, Step 1), añadir:
```xml
<com.reparaciones.utils.MultiSelectComboBox fx:id="filtroPieza" prefWidth="140"/>
```

- [ ] **Step 2: Declarar el campo y cablear (controller, las 3 vistas)**

```java
@FXML private com.reparaciones.utils.MultiSelectComboBox<String> filtroPieza;
private final java.util.Set<String> piezasFiltro = new java.util.HashSet<>();
```
Poblar las opciones con las categorías presentes en los datos (calculadas con `com.reparaciones.utils.Piezas.categoria(rep.getTipoComponente())`, descartando ""), y al cambiar la selección recalcular `piezasFiltro` + `aplicarFiltros()` (mismo cableado que los otros filtros).

- [ ] **Step 3: Aplicar el predicado en modo Plano (controller, las 3 vistas)**

En `aplicarFiltros()`, en la rama **`modoActual == Modo.PLANO`**, añadir al `filter(...)`:
```java
if (!piezasFiltro.isEmpty()) {
    String cat = com.reparaciones.utils.Piezas.categoria(rep.getTipoComponente());
    if (!piezasFiltro.contains(cat)) return false;
}
```

- [ ] **Step 4: Mostrar el filtro solo en Plano (controller, las 3 vistas)**

- Modo Plano: `filtroPieza.setVisible(true); filtroPieza.setManaged(true);`
- Modo Agrupado / Detalle: `filtroPieza.setVisible(false); filtroPieza.setManaged(false);`

- [ ] **Step 5: Resetear en "Limpiar filtros" (controller, las 3 vistas)**

En `limpiarFiltros()`, vaciar `piezasFiltro` y deseleccionar el control antes de `aplicarFiltros()`.

- [ ] **Step 6: Compilar y correr la suite**

Run: `cd gestion-reparaciones-cliente && mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Verificación manual (las 3 vistas)**

En modo Plano aparece "Pieza▾"; filtra por Glass/Pantalla/Marco/Batería/Cámara/Chasis/Otros; en Agrupado desaparece; "Limpiar filtros" lo resetea.

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionController*.java \
        gestion-reparaciones-cliente/src/main/resources/views/ReparacionView*.fxml
git commit -m "feat(filtros): filtro de pieza en el historial plano (3 vistas)"
```

---

## Self-Review (cobertura del spec)

- Punto 1 (asignar con cliente no marca urgente) → Task 2. ✅
- Punto 2 (job 00:00 Madrid, regla, quitar a mano, sin esquema, log resumen) → Task 1. ✅
- Punto 3 (filtro cliente, agrupado, "(Sin cliente)", 3 roles, tras Técnico) → Task 4. ✅
- Punto 4 (filtro pieza, plano, categorías por SKU, 3 roles) → Tasks 3 + 5. ✅
- Barra responsive → Task 4 Step 1 (reusada por Task 5). ✅
- Sin migración de BD; "pendiente" = `A%`/no `AP%`/`FECHA_FIN IS NULL`; `UPDATED_AT = UPDATED_AT` → Global Constraints + Task 1. ✅

## Notas de ejecución

- Confirmar el nombre real de los FXML del historial (super/admin/técnico) antes de editarlos.
- Las 3 vistas comparten el patrón `aplicarFiltros()`/`Modo.PLANO`/`filtroTecnico`; aplicar los mismos cambios en cada una (la del técnico no tiene `filtroTecnico`).
- Versión objetivo de release: **0.11.0** (se versiona al finalizar la rama, no en estas tareas).
