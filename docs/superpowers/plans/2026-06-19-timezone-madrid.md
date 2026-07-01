# Timezone Europe/Madrid — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corregir el desfase de 2 horas (UTC vs CEST) en la visualización de fechas del cliente y en la generación de IDs en el servidor.

**Architecture:** Una sola clase utilitaria `FechaUtils` en el cliente concentra la conversión UTC→Europe/Madrid. El servidor solo necesita cambiar un `LocalDate.now()` a `LocalDate.now(ZoneId)` en `nextId()`. El contenedor Docker permanece en UTC (correcto según estándar).

**Tech Stack:** Java 17, JavaFX, Spring Boot, MariaDB (UTC).

**Cuándo ejecutar:** Este plan va PRIMERO, antes del plan de prioridad ("muy urgente") y del plan de logs enriquecidos.

**Nota para plan de logs (2026-06-19-logs-enriquecidos.md):** La Task 10 de ese plan reescribe `LogController.java`. Esa reescritura DEBE usar `FechaUtils.formatear(fecha, FMT)` en la columna de fecha, no `fecha.format(FMT)`, para no perder el fix de timezone.

## Global Constraints

- El contenedor Docker NO se toca — permanece en UTC
- La BD almacena fechas en UTC — no se cambia
- Solo se cambia la **visualización** en el cliente y la generación de **IDs** en el servidor
- No modificar el formato de fecha mostrado (solo la zona horaria usada)
- El cliente corre en una máquina Windows en España (CEST/CET) — `LocalDateTime.now()` del cliente ya es correcto, NO debe convertirse

---

## Task 1: Servidor — nextId usa Europe/Madrid

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`

**Why:** `nextId()` llama a `LocalDate.now()` que usa el timezone del JVM (UTC en el contenedor Docker). Entre las 22:00 y 00:00 CEST, el ID generado tendría la fecha del día anterior. `LocalDate.now(ZoneId)` toma la fecha en el timezone explícito.

- [ ] **Step 1: Añadir import ZoneId en ReparacionDAO**

Verificar si `java.time.ZoneId` ya está importado:
```bash
grep -n "import java.time" gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java | head -10
```

Si no está `ZoneId`, añadir el import junto a los demás de `java.time`:
```java
import java.time.ZoneId;
```

- [ ] **Step 2: Modificar nextId para usar Europe/Madrid**

En `ReparacionDAO.java`, buscar el método `nextId` (línea ~755):

Cambiar:
```java
String hoy  = LocalDate.now().format(FMT_ID);
```
Por:
```java
String hoy  = LocalDate.now(ZoneId.of("Europe/Madrid")).format(FMT_ID);
```

- [ ] **Step 3: Compilar el servidor**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Verificar**

Arrancar el servidor. Crear una asignación nueva. Verificar que el ID generado tiene la fecha de hoy en hora española (no UTC). Para simular el caso límite, se puede cambiar temporalmente `"Europe/Madrid"` por `"UTC"` para comparar — pero en producción siempre debe ser `"Europe/Madrid"`.

- [ ] **Step 5: Commit**

```
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "fix: nextId usa Europe/Madrid para generar IDs con fecha española"
```

---

## Task 2: Cliente — crear FechaUtils

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/FechaUtils.java`

**Why:** Centralizar la conversión UTC→Europe/Madrid en un único lugar. Así los controladores solo cambian `ldt.format(FMT)` → `FechaUtils.formatear(ldt, FMT)` y `ldt.toLocalDate()` → `FechaUtils.toLocalDate(ldt)`.

**Interfaces:**
- Produces: `FechaUtils.formatear(LocalDateTime utc, DateTimeFormatter fmt)` → `String`
- Produces: `FechaUtils.toLocalDate(LocalDateTime utc)` → `LocalDate`

- [ ] **Step 1: Crear FechaUtils.java**

```java
package com.reparaciones.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FechaUtils {

    private static final ZoneId UTC    = ZoneId.of("UTC");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /**
     * Convierte una fecha UTC recibida del servidor a hora de Madrid y la formatea.
     * Usar en TODOS los campos de fecha provenientes de la API (server time = UTC).
     * NO usar para LocalDateTime.now() del cliente (ya está en hora local).
     */
    public static String formatear(LocalDateTime utc, DateTimeFormatter fmt) {
        if (utc == null) return "";
        return utc.atZone(UTC).withZoneSameInstant(MADRID).format(fmt);
    }

    /**
     * Convierte una fecha UTC del servidor a LocalDate en hora de Madrid.
     * Usar al comparar fechas de servidor con DatePicker del cliente.
     */
    public static LocalDate toLocalDate(LocalDateTime utc) {
        if (utc == null) return null;
        return utc.atZone(UTC).withZoneSameInstant(MADRID).toLocalDate();
    }
}
```

- [ ] **Step 2: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/FechaUtils.java
git commit -m "feat: añadir FechaUtils para conversión UTC→Europe/Madrid en cliente"
```

---

## Task 3: Cliente — aplicar FechaUtils en todos los controladores con fechas de servidor

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/HistorialPulidoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java`

**Interfaces:**
- Consumes: `FechaUtils.formatear(LocalDateTime, DateTimeFormatter)` de Task 2
- Consumes: `FechaUtils.toLocalDate(LocalDateTime)` de Task 2

**Patrón general de sustitución:**

| Antes | Después |
|---|---|
| `ldt.format(FMT)` | `FechaUtils.formatear(ldt, FMT)` |
| `ldt.toLocalDate()` *(sobre fecha de servidor)* | `FechaUtils.toLocalDate(ldt)` |

NO sustituir `LocalDateTime.now()` ni `LocalTime.now()` — son tiempo de la máquina cliente, ya correcto.

Añadir import en cada archivo modificado:
```java
import com.reparaciones.utils.FechaUtils;
```

---

### 3a: PendientesTecnicoController

- [ ] **Step 1: Buscar todos los `.format(FMT)` sobre fechas de servidor**

```bash
grep -n "getFechaAsig\|getFechaFin\|\.format(FMT)" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java
```

Identificar las líneas con `getFechaAsig().format(FMT)`.

- [ ] **Step 2: Sustituir**

Para cada aparición de `rep.getFechaAsig().format(FMT)`:
```java
// Antes:
rep.getFechaAsig() != null ? rep.getFechaAsig().format(FMT) : ""
// Después:
FechaUtils.formatear(rep.getFechaAsig(), FMT)
```

(`FechaUtils.formatear` ya maneja null internamente, pero el operador ternario también funciona — el resultado es el mismo.)

- [ ] **Step 3: Añadir import FechaUtils**

Añadir junto a los otros imports:
```java
import com.reparaciones.utils.FechaUtils;
```

- [ ] **Step 4: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Expected: BUILD SUCCESS.

---

### 3b: PendientesSuperTecnicoController

- [ ] **Step 1: Buscar**

```bash
grep -n "getFechaAsig\|\.format(FMT)" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
```

- [ ] **Step 2: Sustituir todas las apariciones de `.format(FMT)` sobre fechas de servidor**

Cambiar cualquier `rep.getFechaAsig().format(FMT)` (o similar) por `FechaUtils.formatear(rep.getFechaAsig(), FMT)`.

NO modificar `LocalTime.now().format(...)` — esa es la hora local del cliente para "Actualizado HH:mm".

- [ ] **Step 3: Añadir import FechaUtils**

```java
import com.reparaciones.utils.FechaUtils;
```

- [ ] **Step 4: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

---

### 3c: HistorialPulidoController

- [ ] **Step 1: Buscar**

```bash
grep -n "getFechaAsig\|getFechaFin\|\.format(FMT)\|\.toLocalDate()" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/HistorialPulidoController.java
```

- [ ] **Step 2: Sustituir displays**

Cambiar todas las apariciones de `rep.getFechaAsig().format(FMT)` y `rep.getFechaFin().format(FMT)` por:
```java
FechaUtils.formatear(rep.getFechaAsig(), FMT)
FechaUtils.formatear(rep.getFechaFin(), FMT)
```

- [ ] **Step 3: Sustituir toLocalDate() de filtros de fecha**

Las líneas de filtro por fecha usan `rep.getFechaFin().toLocalDate()` para comparar con `DatePicker`. Cambiar:
```java
// Antes:
if (rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isBefore(desde)) return false;
if (rep.getFechaFin() != null && rep.getFechaFin().toLocalDate().isAfter(hasta))  return false;

// Después:
if (FechaUtils.toLocalDate(rep.getFechaFin()) != null && FechaUtils.toLocalDate(rep.getFechaFin()).isBefore(desde)) return false;
if (FechaUtils.toLocalDate(rep.getFechaFin()) != null && FechaUtils.toLocalDate(rep.getFechaFin()).isAfter(hasta))  return false;
```

O más limpio con variable local:
```java
LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
if (fechaFin != null && desde != null && fechaFin.isBefore(desde)) return false;
if (fechaFin != null && hasta != null && fechaFin.isAfter(hasta))  return false;
```

- [ ] **Step 4: Añadir import FechaUtils**

```java
import com.reparaciones.utils.FechaUtils;
```

- [ ] **Step 5: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

---

### 3d: ReparacionControllerSuperTecnico

- [ ] **Step 1: Buscar**

```bash
grep -n "\.format(FORMATO_FECHA)\|getFechaAsig\|getFechaFin\|getFechaMas\|\.toLocalDate()" \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
```

- [ ] **Step 2: Sustituir displays con FORMATO_FECHA**

Este controlador usa `FORMATO_FECHA` (no `FMT`). Cambiar:

Todas las apariciones de `xxx.format(FORMATO_FECHA)` donde `xxx` es una fecha del servidor (`getFechaAsig`, `getFechaFin`, `getFechaMasAntigua`, `getFechaMasReciente`):
```java
// Antes:
rep.getFechaAsig().format(FORMATO_FECHA)
rep.getFechaFin().format(FORMATO_FECHA)
g.getFechaMasAntigua().format(FORMATO_FECHA)
g.getFechaMasReciente().format(FORMATO_FECHA)

// Después (en cada caso):
FechaUtils.formatear(rep.getFechaAsig(), FORMATO_FECHA)
FechaUtils.formatear(rep.getFechaFin(), FORMATO_FECHA)
FechaUtils.formatear(g.getFechaMasAntigua(), FORMATO_FECHA)
FechaUtils.formatear(g.getFechaMasReciente(), FORMATO_FECHA)
```

NO modificar `LocalTime.now().format(...)` de "Actualizado HH:mm".

- [ ] **Step 3: Sustituir toLocalDate() en filtros de fecha**

Las líneas de filtro por rango de fecha usan `rep.getFechaFin().toLocalDate()`. Cambiar el patrón:
```java
// Antes:
LocalDate fechaFin = rep.getFechaFin().toLocalDate();

// Después:
LocalDate fechaFin = FechaUtils.toLocalDate(rep.getFechaFin());
```

Hay varias ocurrencias (al menos 3 bloques de filtrado distintos). Buscarlas todas.

- [ ] **Step 4: Sustituir en exportarCSV (fmtHora)**

En el método `exportarCSV`, hay un `fmtHora` usado para formatear fechas de servidor. Cambiar:
```java
// Antes:
rep.getFechaFin().format(fmtHora)
// (o similar)

// Después:
FechaUtils.formatear(rep.getFechaFin(), fmtHora)
```

Verificar con grep si hay más usos de `fmtHora` sobre campos de servidor.

- [ ] **Step 5: Añadir import FechaUtils**

```java
import com.reparaciones.utils.FechaUtils;
```

- [ ] **Step 6: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

---

### 3e: LogController

- [ ] **Step 1: Localizar el formatter de la columna fecha**

En `LogController.java`, la columna de fecha tiene:
```java
c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : ""
```

- [ ] **Step 2: Sustituir**

```java
// Antes:
c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : ""

// Después:
FechaUtils.formatear(c.getValue().getFecha(), FMT)
```

- [ ] **Step 3: Añadir import FechaUtils**

```java
import com.reparaciones.utils.FechaUtils;
```

- [ ] **Step 4: Compilar**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

---

### Final: compilación limpia y commit

- [ ] **Step 1: Compilación limpia del cliente**

```bash
cd gestion-reparaciones-cliente
mvn clean compile -q
```

Expected: BUILD SUCCESS sin warnings de compilación.

- [ ] **Step 2: Test manual completo**

Arrancar servidor + cliente. Verificar:
1. Las fechas de asignación en PendientesTecnico muestran +2h respecto a lo anterior
2. Las fechas de reparación en historial muestran +2h
3. Las fechas en log de actividad muestran +2h
4. Los filtros por fecha (DatePicker) en historial siguen funcionando correctamente
5. "Actualizado HH:mm" en cabecera sigue siendo la hora local del PC (no se ha tocado)
6. La generación de un ID nuevo muestra la fecha española en el ID

- [ ] **Step 3: Commit**

```
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/HistorialPulidoController.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "fix: fechas de servidor convertidas a Europe/Madrid en todos los controladores cliente"
```

---

## Self-Review

### Spec coverage

| Requisito | Task |
|---|---|
| Contenedor Docker permanece en UTC | ✅ no se toca |
| Servidor: IDs con fecha española (`nextId`) | Task 1 |
| Cliente: clase utilitaria de conversión UTC→Madrid | Task 2 |
| `PendientesTecnicoController` — fechas correctas | Task 3a |
| `PendientesSuperTecnicoController` — fechas correctas | Task 3b |
| `HistorialPulidoController` — fechas y filtros correctos | Task 3c |
| `ReparacionControllerSuperTecnico` — fechas, GrupoImei, filtros y CSV correctos | Task 3d |
| `LogController` — fecha del log correcta | Task 3e |
| `LocalDateTime.now()` y `LocalTime.now()` del cliente — NO modificados | ✅ excluidos explícitamente |

### Nota sobre Plan 2 (logs enriquecidos)

La Task 10 del plan de logs enriquecidos reescribe `LogController.java` por completo. En ese paso, el nuevo código DEBE usar `FechaUtils.formatear(fecha, FMT)` en lugar de `fecha.format(FMT)` para la columna de fecha. De lo contrario, se perderá el fix de timezone de este plan.

Antes de ejecutar Plan 2 Task 10, editar el código del nuevo `LogController` en el plan para reflejar esto (la línea de `colFecha.setCellValueFactory`).

### Posibles issues

1. **`GrupoImei.getFechaMasAntigua()` y `getFechaMasReciente()`**: estas fechas agregan `LocalDateTime` de `ReparacionResumen` (servidor, UTC). Son correctas de sustituir con `FechaUtils`. Si el modelo `GrupoImei` hace cálculos internos sobre estas fechas (como ordenamiento), esos cálculos internos no necesitan conversión (UTC es consistente para ordenar); solo el display final necesita `FechaUtils`.

2. **Filtros por DatePicker**: el usuario selecciona una fecha local (Spain). El servidor devuelve `LocalDateTime` UTC. Al convertir con `FechaUtils.toLocalDate()` se obtiene la fecha Madrid, que es la que el usuario espera comparar con su DatePicker. Este fix es especialmente importante para los 2 días al año en que cambia el horario (DST).

3. **CSV export en `ReparacionControllerSuperTecnico`**: buscar exhaustivamente todas las referencias a `format(fmtHora)` en ese método — puede haber más de una.
