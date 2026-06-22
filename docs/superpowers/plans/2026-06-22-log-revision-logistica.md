# Registrar revisión logística en el log — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Registrar en el log de auditoría el marcado/desmarcado del botón OK de revisión logística (`MARCAR_REVISION` / `QUITAR_REVISION`), con IMEI y modelo.

**Architecture:** El servidor inyecta `LogDAO` en `TelefonoController` y registra el log tras actualizar `REVISION_LOGISTICA`. El cliente añade las dos acciones al dropdown de filtro de la vista de logs. Sin BD, sin cambios de concurrencia.

**Tech Stack:** Spring Boot (JdbcTemplate), JavaFX 17.

## Global Constraints

- Dos acciones: `MARCAR_REVISION` (revisado=true) y `QUITAR_REVISION` (revisado=false).
- Detalle del log: `IMEI: <imei>, MODELO: <modelo>` (usando `TelefonoDAO.getModelo(imei)`).
- El log se registra solo si la operación procede (después del guard `tieneAsignacionesActivas` → 409 existente).
- Sin migración de BD. Sin tocar concurrencia ni inmediatez.
- Servidor y cliente se despliegan juntos.

---

## Task 1: Servidor — registrar el log en TelefonoController

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`

**Interfaces:**
- Consumes: `LogDAO.insertar(int, String, String)` (existente), `TelefonoDAO.getModelo(String)` (existente), `UsuarioPrincipal.getIdUsu()` (existente).

- [ ] **Step 1: Añadir imports**

En `TelefonoController.java`, junto a los imports existentes, añadir:
```java
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
```

- [ ] **Step 2: Inyectar LogDAO en el constructor**

Reemplazar el bloque de campos + constructor:
```java
    private final TelefonoDAO dao;
    private final ImeiLookupService imeiLookupService;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
    }
```
por:
```java
    private final TelefonoDAO dao;
    private final ImeiLookupService imeiLookupService;
    private final LogDAO logDao;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService, LogDAO logDao) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
    }
```

- [ ] **Step 3: Registrar el log en actualizarRevisionLogistica**

Reemplazar el método:
```java
    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req) {
        if (dao.tieneAsignacionesActivas(imei)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El IMEI tiene asignaciones activas");
        }
        dao.actualizarRevisionLogistica(imei, req.revisado());
    }
```
por:
```java
    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tieneAsignacionesActivas(imei)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El IMEI tiene asignaciones activas");
        }
        dao.actualizarRevisionLogistica(imei, req.revisado());
        String modelo = dao.getModelo(imei);
        logDao.insertar(principal.getIdUsu(),
                req.revisado() ? "MARCAR_REVISION" : "QUITAR_REVISION",
                "IMEI: " + imei + ", MODELO: " + (modelo != null ? modelo : "?"));
    }
```

- [ ] **Step 4: Compilar el servidor**

Run: `cd gestion-reparaciones-servidor && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java
git commit -m "feat(log-revision): registrar MARCAR_REVISION/QUITAR_REVISION al togglear revisión logística"
```

---

## Task 2: Cliente — añadir las acciones al dropdown de filtro de logs

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java`

**Interfaces:**
- Consumes: la lista `TIPOS_ACCION` (existente) del filtro de acción.

- [ ] **Step 1: Añadir las dos acciones a TIPOS_ACCION**

En `LogController.java`, en la lista `TIPOS_ACCION`, la última línea actual es:
```java
        "LOGIN", "CAMBIAR_PASSWORD"
```
Reemplazarla por:
```java
        "MARCAR_REVISION", "QUITAR_REVISION",
        "LOGIN", "CAMBIAR_PASSWORD"
```

- [ ] **Step 2: Compilar el cliente**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Test manual (cubre ambas tareas)**

Arrancar servidor + cliente. Como **supertécnico**: marcar el OK de un IMEI sin asignaciones activas, luego desmarcarlo. Como **admin**: abrir la vista de logs y verificar:
1. Aparecen dos filas: `MARCAR_REVISION` y `QUITAR_REVISION`, con el usuario correcto y `DETALLE` = `IMEI: ..., MODELO: ...`.
2. El dropdown de filtro por acción ofrece `MARCAR_REVISION` y `QUITAR_REVISION`, y al seleccionarlas filtra correctamente.
3. Regresión: el botón OK sigue funcionando (toggle, guard de asignaciones activas, refresco).

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "feat(log-revision): añadir MARCAR_REVISION/QUITAR_REVISION al filtro de acciones de logs"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Task/Step |
|---|---|
| Inyectar LogDAO en TelefonoController | T1 Steps 1-2 |
| Registrar log tras actualizar (después del guard) | T1 Step 3 |
| Acción MARCAR_REVISION / QUITAR_REVISION según revisado | T1 Step 3 |
| Detalle IMEI + MODELO | T1 Step 3 |
| `@AuthenticationPrincipal` para el idUsu | T1 Step 3 |
| Dropdown cliente con las dos acciones | T2 Step 1 |
| Sin BD, sin concurrencia | Todo el plan |

### Notas
- Sin TDD (controller Spring + UI sin tests automáticos en el proyecto); verificación por compilación + manual.
- El detalle usa `getModelo` que puede devolver null → fallback `"?"`, consistente con los logs enriquecidos.
