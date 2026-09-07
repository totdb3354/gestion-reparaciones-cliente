# Borrador persistente del modal de reparación — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistir en servidor (tabla `Reparacion_borrador`, JSON) lo que el técnico va metiendo en el modal de reparación por componente sin completar, y recuperarlo al reabrir la asignación.

**Architecture:** El borrador es un JSON por asignación (`ID_REP`) guardado en una tabla nueva 1:0..1 de `Reparacion` (PK=FK, `ON DELETE CASCADE`). El cliente auto-guarda (debounce) los inputs no guardados del modal vía endpoints REST nuevos, los recupera al abrir (aplicando lo válido), y borra el borrador tras un guardado real. Solo el flujo de **nueva reparación** (`init`), no edición.

**Tech Stack:** Servidor Spring Boot + MariaDB (JdbcTemplate); cliente Java 17 + JavaFX 21 + Gson (ya dependencia).

**Spec:** `docs/superpowers/specs/2026-06-12-borrador-persistente-modal-design.md`

---

## Notas previas

- **Sin tests automáticos.** Verificación: `mvn -q compile` en cada módulo + checklist manual + `curl` para los endpoints. Comandos:
  - Servidor: `mvn -q -f ../gestion-reparaciones-servidor/pom.xml compile`
  - Cliente: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile` (con la app cerrada)
- **Despliegue BD:** la tabla nueva hay que crearla en la BD viva de la VM con el script de migración (`sql/migracion-borrador.sql`), además de dejarla en `crear_bd.sql`/`reset-uat.sql`.
- **Patrón DAO servidor:** `JdbcTemplate jdbc` inyectado por constructor; ver `CompraComponenteDAO`. Columna JSON de MariaDB se lee/escribe como `String` (`rs.getString`, parámetro `String`).
- **Patrón endpoint:** `@RestController @RequestMapping("/api/reparaciones")` en `ReparacionController`; `@PreAuthorize`, `@PathVariable`, `@RequestBody record`.
- **Patrón DAO cliente:** `ApiClient.getX/putX/postX/deleteX(...)`; ver `CompraComponenteDAO` cliente. Gson: `new com.google.gson.Gson()`.
- **Orden de recuperación (clave):** en `init`, primero se carga el estado de BD (solicitudes vía `activarSolicitud`), y **después** se aplica el borrador encima, **solo en filas no bloqueadas por una solicitud de BD** (aplicar lo válido, ignorar lo inválido).

---

## File Structure

**Servidor:**
- `sql/crear_bd.sql` — añadir tabla `Reparacion_borrador`.
- `sql/reset-uat.sql` — `DELETE FROM Reparacion_borrador`.
- `sql/migracion-borrador.sql` (nuevo) — `CREATE TABLE` para la BD viva.
- `dao/BorradorDAO.java` (nuevo) — upsert/get/delete por `idRep`.
- `controller/ReparacionController.java` — 3 endpoints `borrador`.
- `dao/ReparacionDAO.java` — borrar borrador al cambiar de técnico (`actualizarAsignacion`, `actualizarTecnico`).

**Cliente:**
- `dao/BorradorDAO.java` (nuevo, HTTP).
- `models/BorradorContenido.java` (nuevo, POJO Gson).
- `controllers/FormularioReparacionController.java` — captura/restauración + auto-guardado + recuperación + quitar diálogo + borrar tras guardar; métodos `capturarBorrador`/`aplicarBorrador` en `FilaUI` y `OtrasAccionesUI`.

---

## TAREAS DE SERVIDOR

### Task S1: Esquema — tabla `Reparacion_borrador`

**Files:**
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql`
- Modify: `gestion-reparaciones-servidor/sql/reset-uat.sql`
- Create: `gestion-reparaciones-servidor/sql/migracion-borrador.sql`

- [ ] **Step 1: Añadir el DROP en crear_bd.sql**

En `crear_bd.sql`, dentro del bloque `SET FOREIGN_KEY_CHECKS = 0; ... = 1;` (líneas 12-26), añade el DROP **antes** de `DROP TABLE IF EXISTS Reparacion;`:

```sql
DROP TABLE IF EXISTS Reparacion_borrador;
```

- [ ] **Step 2: Añadir la tabla en crear_bd.sql**

Justo **después** del `CREATE TABLE Reparacion (...);` (termina en la línea con `CONSTRAINT Reparacion_ibfk_3 ...);`), añade:

```sql
CREATE TABLE Reparacion_borrador (
    ID_REP     VARCHAR(30) NOT NULL,
    CONTENIDO  LONGTEXT    NOT NULL COMMENT 'Borrador del modal serializado en JSON',
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REP),
    CONSTRAINT fk_borrador_reparacion FOREIGN KEY (ID_REP)
        REFERENCES Reparacion (ID_REP) ON DELETE CASCADE
);
```
(En MariaDB `JSON` es solo un alias de `LONGTEXT` + un `CHECK json_valid` dependiente de versión; usamos `LONGTEXT` directamente porque el contenido es un blob opaco que nunca se consulta con funciones JSON.)

- [ ] **Step 3: Añadir el DELETE en reset-uat.sql**

En `reset-uat.sql`, antes del paso 5 (`-- 5. Romper auto-referencia...`), añade:

```sql
-- 4b. Borradores de reparación
DELETE FROM Reparacion_borrador;
```

- [ ] **Step 4: Crear el script de migración**

Crea `gestion-reparaciones-servidor/sql/migracion-borrador.sql` con:

```sql
-- Migración: tabla de borradores del modal de reparación (Item 2).
-- Aplicar en la BD viva de la VM. Idempotente.
USE gestion_reparaciones;

CREATE TABLE IF NOT EXISTS Reparacion_borrador (
    ID_REP     VARCHAR(30) NOT NULL,
    CONTENIDO  LONGTEXT    NOT NULL COMMENT 'Borrador del modal serializado en JSON',
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REP),
    CONSTRAINT fk_borrador_reparacion FOREIGN KEY (ID_REP)
        REFERENCES Reparacion (ID_REP) ON DELETE CASCADE
);
```

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/sql/crear_bd.sql gestion-reparaciones-servidor/sql/reset-uat.sql gestion-reparaciones-servidor/sql/migracion-borrador.sql
git commit -m "feat: tabla Reparacion_borrador (esquema + reset + migracion)"
```

---

### Task S2: `BorradorDAO` (servidor)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/BorradorDAO.java`

- [ ] **Step 1: Crear el DAO**

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Persistencia del borrador (JSON) del modal de reparación, 1:0..1 con Reparacion. */
@Repository
public class BorradorDAO {

    private final JdbcTemplate jdbc;

    public BorradorDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return el JSON del borrador de esa asignación, o null si no hay. */
    public String get(String idRep) {
        List<String> filas = jdbc.query(
                "SELECT CONTENIDO FROM Reparacion_borrador WHERE ID_REP = ?",
                (rs, n) -> rs.getString("CONTENIDO"), idRep);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** Inserta o actualiza el borrador (upsert por PK). */
    public void guardar(String idRep, String contenidoJson) {
        jdbc.update(
                "INSERT INTO Reparacion_borrador (ID_REP, CONTENIDO) VALUES (?, ?)" +
                " ON DUPLICATE KEY UPDATE CONTENIDO = VALUES(CONTENIDO)",
                idRep, contenidoJson);
    }

    public void eliminar(String idRep) {
        jdbc.update("DELETE FROM Reparacion_borrador WHERE ID_REP = ?", idRep);
    }
}
```

- [ ] **Step 2: Compilar**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/BorradorDAO.java
git commit -m "feat: BorradorDAO servidor (get/guardar/eliminar)"
```

---

### Task S3: Endpoints del borrador en `ReparacionController`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

- [ ] **Step 1: Inyectar `BorradorDAO`**

En el constructor del controlador, añade el parámetro `BorradorDAO borradorDao` y el campo. Busca el constructor existente (`public ReparacionController(ReparacionDAO dao, ...)`), añade `private final BorradorDAO borradorDao;`, el parámetro y `this.borradorDao = borradorDao;`.

- [ ] **Step 2: Añadir los 3 endpoints**

Justo antes del cierre de la clase (la última `}`), añade:

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @GetMapping("/{idRep}/borrador")
    public java.util.Map<String, String> getBorrador(@PathVariable String idRep) {
        String contenido = borradorDao.get(idRep);
        return java.util.Collections.singletonMap("contenido", contenido);
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @PutMapping("/{idRep}/borrador")
    public void guardarBorrador(@PathVariable String idRep, @RequestBody BorradorRequest req) {
        borradorDao.guardar(idRep, req.contenido());
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @DeleteMapping("/{idRep}/borrador")
    public void eliminarBorrador(@PathVariable String idRep) {
        borradorDao.eliminar(idRep);
    }

    private record BorradorRequest(String contenido) {}
```

(Nota: `getBorrador` devuelve `{"contenido": null}` si no hay borrador — Jackson serializa el null; el cliente lo trata como "sin borrador".)

- [ ] **Step 3: Compilar**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat: endpoints GET/PUT/DELETE /api/reparaciones/{idRep}/borrador"
```

---

### Task S4: Borrar el borrador al reasignar el técnico

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java:385-408`

El borrador es del técnico asignado; si la asignación se reasigna a **otro** técnico, hay que descartarlo. Las dos rutas que cambian `ID_TEC` son `actualizarAsignacion` (PATCH `/asignaciones/{idRep}`) y `actualizarTecnico` (PATCH `/asignaciones/{idRep}/tecnico`). Solo se borra si el técnico **cambia** (no en una edición de solo comentario).

- [ ] **Step 1: Inyectar `BorradorDAO` en `ReparacionDAO`**

En el constructor de `ReparacionDAO`, añade `BorradorDAO borradorDao` como parámetro, el campo `private final BorradorDAO borradorDao;` y `this.borradorDao = borradorDao;`. (Spring lo inyecta.)

- [ ] **Step 2: Borrar borrador en `actualizarAsignacion` cuando cambia el técnico**

Reemplaza el método `actualizarAsignacion` (385-393) por:

```java
    @org.springframework.transaction.annotation.Transactional
    public void actualizarAsignacion(String idRep, int idTec, String comentarioAsignacion, LocalDateTime updatedAt) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idRep);
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ?, COMENTARIO_ASIGNACION = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, comentarioAsignacion, idRep,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (idTecActual != null && idTecActual != idTec) {
            borradorDao.eliminar(idRep);
        }
    }
```

- [ ] **Step 3: Borrar borrador en `actualizarTecnico` cuando cambia el técnico**

Reemplaza el método `actualizarTecnico` (399-408) por:

```java
    @org.springframework.transaction.annotation.Transactional
    public void actualizarTecnico(String idRep, int idTec, LocalDateTime updatedAt) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idRep);
        // UPDATE atómico: el WHERE con UPDATED_AT evita la ventana SELECT→UPDATE del patrón TOCTOU
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, idRep,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (idTecActual != null && idTecActual != idTec) {
            borradorDao.eliminar(idRep);
        }
    }
```

- [ ] **Step 4: Compilar**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat: borrar borrador al reasignar el tecnico de una asignacion"
```

---

## TAREAS DE CLIENTE

### Task C1: DTO `BorradorContenido` (POJO Gson)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/BorradorContenido.java`

- [ ] **Step 1: Crear el POJO**

```java
package com.reparaciones.models;

import java.util.ArrayList;
import java.util.List;

/** Contenido serializable (Gson) del borrador del modal de reparación. */
public class BorradorContenido {

    public String modelo;                 // valor de cbFiltroModelo, o null
    public List<Fila> filas = new ArrayList<>();
    public List<String> otros = new ArrayList<>();   // descripciones de "Otras acciones"

    /** Inputs no guardados de una fila de componente. */
    public static class Fila {
        public String prefijo;            // identifica la fila (tipo de componente)
        public int idCom;                 // SKU seleccionado, o -1
        public int cantidad;
        public boolean reutilizado;
        public String observacion;        // null si no hay
        public boolean solicitudNueva;    // solicitud marcada en sesión, sin guardar
        public String descripcionSolicitud;
        public boolean agotadoConfirmado; // agotado confirmado en sesión, sin guardar
        public String descripcionAgotado;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/BorradorContenido.java
git commit -m "feat: DTO BorradorContenido (Gson)"
```

---

### Task C2: `BorradorDAO` (cliente, HTTP)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/BorradorDAO.java`

> **Antes de escribir:** abre `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/CompraComponenteDAO.java` y mira la firma exacta de los helpers de `ApiClient` (p. ej. `ApiClient.getMap`, `ApiClient.put`, `ApiClient.delete`, `ApiClient.postVoid`...). Usa los que existan; abajo se asume `ApiClient.put(path, body)`, `ApiClient.delete(path)` y un GET que devuelve el JSON crudo. Si los nombres difieren, ajústalos (mismo patrón que los demás DAO cliente).

- [ ] **Step 1: Crear el DAO**

```java
package com.reparaciones.dao;

import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.Map;

/** Acceso HTTP al borrador del modal de reparación (por asignación / ID_REP). */
public class BorradorDAO {

    /** @return el JSON del borrador, o null si no hay. */
    public String getBorrador(String idRep) throws SQLException {
        Map<String, Object> resp = ApiClient.getMap("/api/reparaciones/" + idRep + "/borrador");
        Object c = resp != null ? resp.get("contenido") : null;
        return c != null ? c.toString() : null;
    }

    public void guardar(String idRep, String contenidoJson) throws SQLException {
        ApiClient.put("/api/reparaciones/" + idRep + "/borrador",
                Map.of("contenido", contenidoJson));
    }

    public void eliminar(String idRep) throws SQLException {
        ApiClient.delete("/api/reparaciones/" + idRep + "/borrador");
    }
}
```

- [ ] **Step 2: Verificar/ajustar contra `ApiClient`**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS. Si falla por nombres de método de `ApiClient`, ajústalos a los reales (mira `ApiClient.java` y otros DAO) — la semántica es: GET→Map, PUT con body Map, DELETE.

- [ ] **Step 3: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/BorradorDAO.java
git commit -m "feat: BorradorDAO cliente (HTTP get/guardar/eliminar)"
```

---

### Task C3: Captura y restauración en `FilaUI` y `OtrasAccionesUI`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

Los **getters de captura ya existen** en `FilaUI` (`getPrefijo`, `getIdComSeleccionado`, `getCantidad`, `isReutilizado`, `getObservacion`, `isSolicitudNueva`, `getDescripcionSolicitud`, `esAgotadoNuevo`, `getDescripcionAgotado`). Solo hay que añadir el método de **restauración** `aplicarBorrador`.

- [ ] **Step 1: Añadir `capturarEnBorrador()` a `FilaUI`**

Dentro de `FilaUI` (junto a los getters, ~línea 1436), añade:

```java
        /** Vuelca los inputs no guardados de esta fila al DTO, o null si la fila está vacía. */
        com.reparaciones.models.BorradorContenido.Fila capturarEnBorrador() {
            boolean vacia = cantidad == 0 && !isReutilizado()
                    && (observacion == null || observacion.isBlank())
                    && !isSolicitudNueva() && !esAgotadoNuevo();
            if (vacia) return null;
            com.reparaciones.models.BorradorContenido.Fila f = new com.reparaciones.models.BorradorContenido.Fila();
            f.prefijo = prefijo;
            f.idCom = getIdComSeleccionado();
            f.cantidad = cantidad;
            f.reutilizado = isReutilizado();
            f.observacion = observacion;
            f.solicitudNueva = isSolicitudNueva();
            f.descripcionSolicitud = descripcionSolicitud;
            f.agotadoConfirmado = esAgotadoNuevo();
            f.descripcionAgotado = descripcionAgotado;
            return f;
        }
```

- [ ] **Step 2: Añadir `aplicarBorrador(...)` a `FilaUI`**

Dentro de `FilaUI`, añade el método de restauración. Reutiliza `preseleccionarSku`, `actualizarContador` y replica el toggle visual de la observación (igual que `abrirObservacion` al guardar). **Guarda contra conflicto**: si la fila ya tiene una solicitud de BD activa (`solicitudActiva`), no se le aplica el borrador (lo válido se aplica, lo inválido se ignora):

```java
        /** Restaura los inputs de un borrador sobre esta fila (solo si no está bloqueada por solicitud de BD). */
        void aplicarBorrador(com.reparaciones.models.BorradorContenido.Fila f) {
            if (solicitudActiva) return;   // fila ya gestionada por una solicitud de BD: ignorar borrador
            if (f.idCom > 0) preseleccionarSku(f.idCom);
            chkReutilizado.setSelected(f.reutilizado);
            cantidad = Math.max(0, f.cantidad);
            actualizarContador();
            if (f.observacion != null && !f.observacion.isBlank()) {
                observacion = f.observacion;
                lblObservacion.setText(observacion);
                btnObservacion.setVisible(false); btnObservacion.setManaged(false);
                lblObservacion.setVisible(true);  lblObservacion.setManaged(true);
                btnBorrarObs.setVisible(true);     btnBorrarObs.setManaged(true);
            }
            // Solicitud nueva sin guardar: marcar estado + descripción (mirror de abrirSolicitud al guardar).
            if (f.solicitudNueva && !prefijo.equals("otro")) {
                solicitudNuevaEnEstaSesion = true;
                solicitudActiva = true;
                descripcionSolicitud = f.descripcionSolicitud;
                btnSolicitud.setText("✓ Solicitud");
                btnSolicitud.setStyle(STYLE_SOL_ACTIVA);
            }
            // Agotado confirmado sin guardar: marcar estado + sub-fila confirmada.
            if (f.agotadoConfirmado && !prefijo.equals("otro") && !solicitudNuevaEnEstaSesion) {
                agotadoConfirmado = true;
                descripcionAgotado = f.descripcionAgotado;
                String desc = (descripcionAgotado != null && !descripcionAgotado.isBlank()) ? " — " + descripcionAgotado : "";
                lblSubAgotado.setText("✓  Solicitud de reposición pendiente" + desc);
                lblSubAgotado.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                btnSubAgotado.setVisible(false); btnSubAgotado.setManaged(false);
                btnEditarDesc.setVisible(true);  btnEditarDesc.setManaged(true);
                subFilaAgotado.setStyle("-fx-background-color: #E8F5E9; -fx-padding: 4 8 4 70;");
                subFilaAgotado.setVisible(true);  subFilaAgotado.setManaged(true);
            }
            notificar();
        }
```

> **Verificación en ejecución:** el botón/estilo de "solicitud nueva" (texto `"✓ Solicitud"` + `STYLE_SOL_ACTIVA`) debe coincidir con lo que pone `abrirSolicitud` al guardar una solicitud nueva. Abre `abrirSolicitud` (~línea 1438) y confirma el texto/estilo exactos; ajústalos si difieren. Igual para el bloque agotado vs el handler de `abrirDialogoAgotado` (~1238).

- [ ] **Step 3: Añadir captura/restauración a `OtrasAccionesUI`**

`OtrasAccionesUI` ya tiene `getDescripciones()` (devuelve las descripciones de las acciones "otro"). Añade un `setDescripciones(List<String>)` que reconstruya las líneas de acción. Abre `OtrasAccionesUI` (~línea 1696) y localiza el método que **añade una acción con una descripción** (el que usa "+ Añadir acción" / `cargarFilas`). Implementa:

```java
        void aplicarDescripciones(java.util.List<String> descripciones) {
            if (descripciones == null) return;
            for (String d : descripciones) {
                if (d != null && !d.isBlank()) anadirAccionConDescripcion(d);  // usar el método real de añadir+rellenar
            }
        }
```

Sustituye `anadirAccionConDescripcion(d)` por el método real de `OtrasAccionesUI` que crea una acción ya rellena con una descripción (si solo existe "añadir vacía", créalo a partir de él rellenando el `TextArea`/campo). Añade también un getter `getDescripciones()` si no existiera.

- [ ] **Step 4: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: capturar/aplicar borrador en FilaUI y OtrasAccionesUI"
```

---

### Task C4: Orquestación en `FormularioReparacionController` — captura, auto-guardado, borrado tras guardar

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

- [ ] **Step 1: Campos nuevos**

Junto a los campos del controlador (~línea 78-81, donde están `idAsignacion`, `onGuardado`, `filasUI`), añade:

```java
    private final com.reparaciones.dao.BorradorDAO borradorDAO = new com.reparaciones.dao.BorradorDAO();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private javafx.animation.PauseTransition autoGuardado;   // debounce
    private boolean recuperandoBorrador = false;             // evita auto-guardar mientras se aplica el borrador
```

- [ ] **Step 2: Método `capturarBorrador()`**

Añade (método privado del controlador):

```java
    private com.reparaciones.models.BorradorContenido capturarBorrador() {
        com.reparaciones.models.BorradorContenido b = new com.reparaciones.models.BorradorContenido();
        b.modelo = cbFiltroModelo.getValue();
        for (FilaUI fila : filasUI) {
            com.reparaciones.models.BorradorContenido.Fila f = fila.capturarEnBorrador();
            if (f != null) b.filas.add(f);
        }
        if (otrasAcciones != null) b.otros = new java.util.ArrayList<>(otrasAcciones.getDescripciones());
        return b;
    }

    private boolean borradorVacio(com.reparaciones.models.BorradorContenido b) {
        return b.filas.isEmpty() && b.otros.isEmpty() && (b.modelo == null);
    }
```

- [ ] **Step 3: Auto-guardado debounced**

Añade un método que programa el guardado y un `guardarBorradorAhora`:

```java
    private void programarAutoGuardado() {
        if (idAsignacion == null || recuperandoBorrador) return;   // solo flujo nuevo
        if (autoGuardado == null) {
            autoGuardado = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            autoGuardado.setOnFinished(e -> guardarBorradorAhora());
        }
        autoGuardado.playFromStart();   // reinicia el debounce
    }

    private void guardarBorradorAhora() {
        if (idAsignacion == null || recuperandoBorrador) return;
        try {
            com.reparaciones.models.BorradorContenido b = capturarBorrador();
            if (borradorVacio(b)) borradorDAO.eliminar(idAsignacion);
            else borradorDAO.guardar(idAsignacion, gson.toJson(b));
        } catch (Exception ex) {
            // silencioso: un fallo de auto-guardado no debe molestar al técnico
        }
    }
```

- [ ] **Step 4: Disparar el auto-guardado en cada cambio del modal**

En `init(...)` (tras crear las filas), engancha el auto-guardado a los cambios. Las filas notifican vía `onCambio`/`notificar()`. Localiza dónde se asigna el callback de cambio global (busca `onCambio` o `actualizarBoton` como listener de cambios). Añade, después de `cargarFilas()` y de configurar las filas, que **cada cambio** llame también a `programarAutoGuardado()`:
- Si hay un `Runnable onCambioGlobal`/`actualizarBoton` que ya se invoca en cada cambio de fila, añade ahí `programarAutoGuardado();`.
- Engancha también `cbFiltroModelo.valueProperty().addListener((o,a,b) -> programarAutoGuardado());`.
- Para "Otras acciones", engancha su callback de cambio a `programarAutoGuardado()` igual.

```java
        // (en init, tras montar filas y otrasAcciones)
        cbFiltroModelo.valueProperty().addListener((obs, o, n) -> programarAutoGuardado());
        for (FilaUI fila : filasUI) fila.setOnCambioExtra(this::programarAutoGuardado);
```

Para `setOnCambioExtra`, añade en `FilaUI` un segundo callback y llámalo en `notificar()`:

```java
        // en FilaUI: campo + setter + invocación en notificar()
        private Runnable onCambioExtra;
        void setOnCambioExtra(Runnable r) { this.onCambioExtra = r; }
        // y dentro de notificar(): if (onCambioExtra != null) onCambioExtra.run();
```

- [ ] **Step 5: Borrar el borrador tras un guardado real**

En `guardar()` / `ejecutarGuardarNueva()`, tras un guardado **exitoso** (justo antes o después de `onGuardado.run()` en la rama del flujo nuevo, ~líneas 530-541), añade el borrado del borrador y para el debounce:

```java
            if (autoGuardado != null) autoGuardado.stop();
            try { if (idAsignacion != null) borradorDAO.eliminar(idAsignacion); } catch (Exception ignore) {}
```

(Colócalo en la rama de éxito de `ejecutarGuardarNueva`, no en la de edición.)

- [ ] **Step 6: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: auto-guardado debounced del borrador + borrado tras guardar"
```

---

### Task C5: Recuperación en `init`, indicador, y quitar el diálogo "salir sin guardar" (flujo nuevo)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

- [ ] **Step 1: Recuperar y aplicar el borrador en `init`**

Al **final** de `init(...)` (después de cargar las solicitudes de BD vía `getSolicitudesPorAsignacion` y montar las filas — ~línea 170), añade la recuperación. Se aplica **después** del estado de BD; `recuperandoBorrador` evita que el aplicado dispare auto-guardados:

```java
        if (idAsignacion != null && !modoEdicion) {
            try {
                String json = borradorDAO.getBorrador(idAsignacion);
                if (json != null && !json.isBlank()) {
                    com.reparaciones.models.BorradorContenido b =
                            gson.fromJson(json, com.reparaciones.models.BorradorContenido.class);
                    if (b != null && !borradorVacio(b)) {
                        recuperandoBorrador = true;
                        try {
                            if (b.modelo != null && cbFiltroModelo.getItems().contains(b.modelo)
                                    && !cbFiltroModelo.isDisable()) {
                                cbFiltroModelo.setValue(b.modelo);
                            }
                            for (com.reparaciones.models.BorradorContenido.Fila f : b.filas) {
                                filasUI.stream()
                                        .filter(fila -> fila.getPrefijo().equals(f.prefijo))
                                        .findFirst()
                                        .ifPresent(fila -> fila.aplicarBorrador(f));
                            }
                            if (otrasAcciones != null) otrasAcciones.aplicarDescripciones(b.otros);
                        } finally {
                            recuperandoBorrador = false;
                        }
                        mostrarIndicadorBorrador();
                    }
                }
            } catch (Exception ex) {
                // borrador corrupto/no disponible: se ignora, el modal abre normal
            }
        }
```

- [ ] **Step 2: Indicador "borrador recuperado"**

Añade el método del indicador. Coloca una etiqueta sutil arriba del contenido del modal (reutiliza el contenedor raíz del FXML; si hay un `VBox` raíz accesible por `@FXML`, inserta la etiqueta arriba). Implementación mínima:

```java
    private void mostrarIndicadorBorrador() {
        Label aviso = new Label("✓ Borrador recuperado");
        aviso.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0; -fx-font-size: 11px;"
                + " -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 3 10 3 10;");
        // Inserta 'aviso' al principio del contenedor raíz del modal.
        // Busca el nodo raíz @FXML (p. ej. un VBox 'contenedorPrincipal' o similar) y haz:
        //   contenedorPrincipal.getChildren().add(0, aviso);
        // Si no hay un contenedor con fx:id, añade uno al FXML o usa el padre de un nodo @FXML existente:
        //   ((javafx.scene.layout.Pane) algunNodoFXML.getParent()).getChildren().add(0, aviso);
    }
```

> **En ejecución:** localiza el contenedor raíz real del FXML `FormularioReparacionView.fxml` (mira los `fx:id` y el `@FXML` del controlador) y mete la etiqueta arriba. Si no hay contenedor con `fx:id`, añade `fx:id="contenedorPrincipal"` al `VBox` raíz del FXML y un `@FXML private VBox contenedorPrincipal;`.

- [ ] **Step 3: Quitar el diálogo "salir sin guardar" del flujo nuevo**

En `abrir(...)` (~línea 613-640), el `stage.setOnCloseRequest` muestra el `ConfirmDialog` "Tienes cambios sin guardar...". Con auto-guardado ya no se pierde nada. Sustituye ese handler por uno que **haga flush del borrador y cierre** sin preguntar:

```java
                stage.setOnCloseRequest(ev -> {
                    if (ctrl.autoGuardado != null) ctrl.autoGuardado.stop();
                    ctrl.guardarBorradorAhora();   // flush por si el debounce no disparó
                });
```

(El handler de `abrirEditar` en ~268 **NO se toca**: la edición de un R* completado conserva su diálogo de "salir sin guardar".)

Para que `abrir` pueda llamar a `ctrl.autoGuardado`/`ctrl.guardarBorradorAhora()`, esos miembros deben ser accesibles desde el método estático `abrir` (ya están en la misma clase; si `guardarBorradorAhora` es `private`, es accesible desde el método estático de la misma clase a través de la instancia `ctrl`).

- [ ] **Step 4: Compilar**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git commit -m "feat: recuperar borrador en init + indicador + quitar dialogo salir sin guardar (flujo nuevo)"
```

---

### Task C6: Verificación manual (UAT)

- [ ] **Step 1: Aplicar la migración en la BD de la VM**

Ejecuta `sql/migracion-borrador.sql` contra la BD viva (o recrea con `crear_bd.sql` si es entorno de pruebas). Reinicia el servidor.

- [ ] **Step 2: Arrancar y probar**

Run cliente: `mvn -q -f gestion-reparaciones-cliente/pom.xml javafx:run`

Checklist:
1. Abrir una asignación (flujo nuevo), meter cantidades/observaciones/acciones "otro" **sin completar**. Esperar ~2-3s.
2. Cerrar el modal (**sin** diálogo de "salir sin guardar") y reabrir la asignación → se recupera todo, con el indicador **"Borrador recuperado"**.
3. Cerrar la app entera y reabrir → el borrador sigue.
4. Completar la reparación → el borrador se borra; si la asignación sigue viva (por otra solicitud) y se reabre, no re-aplica inputs ya guardados.
5. Borrar la asignación (supertécnico) → el borrador desaparece (cascade).
6. Reasignar la asignación a otro técnico → el borrador se borra.
7. Editar un R* completado → **sigue** mostrando su diálogo de "salir sin guardar" (no lleva borrador).
8. Borrador con SKU/cantidad ya inválido (stock cambió) → aplica lo válido sin romper.

- [ ] **Step 3: Commit de ajustes (si hubo)**

```bash
git add -A && git commit -m "fix: ajustes UAT del borrador persistente"
```

---

## Self-Review (autor del plan)

**Cobertura del spec:**
- Persistencia servidor (tabla JSON 1:0..1, FK CASCADE) → S1. ✓
- Endpoints PUT/GET/DELETE → S3. ✓
- CONTENIDO JSON (modelo, filas, otros) → C1 (DTO). ✓
- Auto-guardado debounced + flush al cerrar → C4 (PauseTransition) + C5 Step 3. ✓
- Quitar diálogo "salir sin guardar" (solo flujo nuevo) → C5 Step 3 (abrirEditar intacto). ✓
- Recuperación + indicador + aplicar válido/ignorar inválido → C5 Step 1-2 (guarda `solicitudActiva`, try/catch). ✓
- Descarte: tras guardado → C4 Step 5; al borrar asignación → CASCADE (S1); al reasignar → S4. ✓
- Alcance solo flujo nuevo (init, no initEditar) → recuperación condicionada a `!modoEdicion`; abrirEditar intacto. ✓

**Consistencia de tipos/nombres:** `BorradorContenido`/`.Fila` (campos: modelo, filas, otros; prefijo, idCom, cantidad, reutilizado, observacion, solicitudNueva, descripcionSolicitud, agotadoConfirmado, descripcionAgotado) usados igual en C1, C3, C4, C5. DAO cliente `getBorrador/guardar/eliminar`; servidor `get/guardar/eliminar`. Endpoints `/api/reparaciones/{idRep}/borrador`.

**Zonas delicadas (verificar en ejecución, marcadas en el plan):**
- C3 Step 2: el texto/estilo de "solicitud nueva" y del bloque "agotado" en `aplicarBorrador` deben cuadrar con los handlers reales (`abrirSolicitud` ~1438, `abrirDialogoAgotado` ~1238).
- C3 Step 3: el método real de `OtrasAccionesUI` para añadir una acción con descripción.
- C2 Step 2: nombres reales de los helpers de `ApiClient`.
- C4 Step 4 / C5 Step 1: el punto exacto donde enganchar el callback de cambio global y la recuperación al final de `init`.
- C5 Step 2: el contenedor raíz del FXML para el indicador.
