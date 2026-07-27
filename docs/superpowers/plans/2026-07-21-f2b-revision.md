# F2b Revisión — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar F2b: tabla `Revision`, ficha de revisión (2 partes), veredicto, acciones de estado (a revisar / OK / bloquear / desbloquear / desguace), panel Revisión en la pestaña Inventario y estados derivados REVISADO/REPARADO.

**Architecture:** Dos repos. Servidor (Spring Boot + JdbcTemplate): tabla nueva `Revision` (1:N, vigente = MAX(ID_REVISION)), `RevisionDAO`, endpoints en `TelefonoController`, extensión de `UbicacionDerivador` y de la query de inventario, hook quitar-OK en `ReparacionDAO`. Cliente (JavaFX): panel Revisión como tercer sub-panel de `InventarioView` (patrón Suppliers), diálogos programáticos (masivo + ficha), veredicto evaluado en cliente (clase pura testeable), enlace al modal de asignación existente vía navegación MainController → ReparacionControllerSuperTecnico → PendientesSuperTecnicoController con precarga.

**Tech Stack:** Java 21, Spring Boot 3 (Jakarta), JdbcTemplate, MariaDB, JavaFX 21 + FXML, Gson (ApiClient), JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-07-21-f2b-revision-design.md` (+ canónica F2 `2026-07-07-fase2-lotes-telefonos-design.md`). Diagrama: `docs/superpowers/specs/assets/2026-07-21-f2b-ciclo-estados.html`.

## Global Constraints

- Commits **sin** trailer `Co-Authored-By` (regla del usuario). Merge/push/tag SOLO con OK explícito del usuario.
- Comandos por **Bash** (Maven incluido): `mvn -q test` en el repo que toque.
- Ramas: raíz `feature/f2b-revision` (ya creada, desde main con la spec); servidor `feature/f2b-revision` (la crea la Task 1 desde main `8f63d08`).
- La migración SQL la aplica el **usuario** en la VM con vista previa (Task 12). Orden de despliegue obligatorio: migración → servidor → cliente.
- Roles como strings exactos: `SUPERTECNICO`, `ADMIN`, `TECNICO`. Endpoints de mutación: `@PreAuthorize("hasRole('SUPERTECNICO')")`.
- Estados BD (enum ya existente, sin ALTER): `RECIBIDO`,`EN_REVISION`,`BLOQUEADO`,`OK`,`ENVIADO`,`DESGUACE`. Derivados (solo en memoria): `EN_REPARACION`, `REVISADO`, `REPARADO`.
- Textos de UI en español. "check marcado = defecto" en toda la parte funcional.
- TDD: test RED antes de implementación en toda lógica; los pasos lo marcan.
- **Decisiones de plan** (desviaciones menores de la letra de la spec, presentar en review final): (1) el veredicto se evalúa SOLO en cliente (clase pura testeada); el PATCH funcional devuelve 204 — evita duplicar la misma regla en dos repos; el veto duro del OK sigue en servidor (`marcarOk`). (2) La precarga del modal de asignar mete el IMEI una vez con el tipo PRINCIPAL (PANT manda: P→PULIDO, G→GLASS, si no NORMAL); la persona añade/cambia tipos en el modal, que es quien decide según la spec. (3) La cola NO tiene endpoint propio: reutiliza `GET /api/telefonos/inventario` extendido con la revisión vigente (T5) y el cliente filtra `ESTADO=EN_REVISION` — una sola query alimenta cola, badges e CSV sin drift.

---

### Task 1: Servidor — migración SQL, modelo Revision y RevisionDAO (núcleo)

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-f2b-revision.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (bloque DROP ~L17-32 y CREATE tras `Usuario`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Revision.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/RevisionFuncional.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/RevisionDAOTest.java`

**Interfaces:**
- Consumes: `JdbcTemplate` (inyección constructor, patrón de la casa), tabla `Telefono`/`Usuario` existentes.
- Produces (para T2-T5): `RevisionDAO#getVigente(String imei) → Revision|null`, `#guardarEstetica(String imei, String grado, String pant, int idUsu)`, `#guardarFuncional(String imei, RevisionFuncional f, int idUsu)`, `Revision` (getters `getEstFecha()`, `getFunFecha()`, `getFunBateriaPct()`, `getEstPant()`, …), `RevisionFuncional` (record, 14 campos).

- [ ] **Step 1: Rama servidor**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only && git checkout -b feature/f2b-revision
```
Verificar: `git log --oneline -1` muestra `8f63d08` (o descendiente en main).

- [ ] **Step 2: Escribir la migración**

`sql/migracion-f2b-revision.sql` (cabecera con banda `═`, convención de la casa; NO idempotente):

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2b-revision.sql — F2b: tabla Revision (spec 2026-07-21)
-- La aplica el usuario a mano en la VM con vista previa. NO idempotente
-- (relanzar = error de tabla existente). Solo CREATE TABLE: el enum ESTADO de
-- Telefono ya quedó completo en la migración F2a.
-- 1) Vista previa: el SELECT debe devolver 0 (la tabla no existe aún).
-- 2) Ejecutar el CREATE TABLE.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0 filas
SELECT COUNT(*) AS YA_EXISTE
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Revision';

-- Una fila por PASADA de revisión; la vigente = MAX(ID_REVISION) por IMEI.
-- La fila se crea vacía al pasar a EN_REVISION; FECHA_CREACION = "en revisión desde".
-- Parte guardada ≡ su *_FECHA IS NOT NULL. Checks funcionales: marcado = defecto.
CREATE TABLE Revision (
    ID_REVISION      INT          NOT NULL AUTO_INCREMENT,
    IMEI             VARCHAR(15)  NOT NULL,
    FECHA_CREACION   DATETIME     NOT NULL,
    EST_GRADO        ENUM('C','B','A-','A') NULL,
    EST_PANT         ENUM('P','G') NULL,
    EST_ID_USU       INT          NULL,
    EST_FECHA        DATETIME     NULL,
    FUN_BATERIA_PCT  TINYINT UNSIGNED NULL,
    FUN_PANT_TACTIL  BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_QUEMADA BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_MAL     BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_MANCHA   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_LENTE    BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_SUP      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_INF      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MIC          BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_FACE_ID      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS           BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS_TEXTO     VARCHAR(100) NULL,
    FUN_BLOQUEO_OP   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_OBSERVACION  VARCHAR(500) NULL,
    FUN_ID_USU       INT          NULL,
    FUN_FECHA        DATETIME     NULL,
    UPDATED_AT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REVISION),
    KEY idx_revision_imei (IMEI, ID_REVISION),
    CONSTRAINT fk_revision_telefono FOREIGN KEY (IMEI) REFERENCES Telefono (IMEI),
    CONSTRAINT fk_revision_usu_est  FOREIGN KEY (EST_ID_USU) REFERENCES Usuario (ID_USU),
    CONSTRAINT fk_revision_usu_fun  FOREIGN KEY (FUN_ID_USU) REFERENCES Usuario (ID_USU)
);
```

- [ ] **Step 3: Sync `crear_bd.sql`**

Añadir `DROP TABLE IF EXISTS Revision;` al bloque de drops (junto a los demás, antes de `Telefono`), y el mismo `CREATE TABLE Revision (...)` **después de `CREATE TABLE Usuario`** (tiene FK a `Usuario` además de a `Telefono`; el orden de dependencias manda). Copiar el CREATE byte a byte de la migración.

- [ ] **Step 4: Modelo `Revision` (POJO estilo de la casa) y record `RevisionFuncional`**

`model/Revision.java` — campos privados + getters/setters (sin lombok): `int idRevision; String imei; LocalDateTime fechaCreacion; String estGrado; String estPant; Integer estIdUsu; String estUsuario; LocalDateTime estFecha; Integer funBateriaPct; boolean funPantTactil, funPantQuemada, funPantMal, funCamMancha, funCamLente, funAltSup, funAltInf, funMic, funFaceId, funMs; String funMsTexto; boolean funBloqueoOp; String funObservacion; Integer funIdUsu; String funUsuario; LocalDateTime funFecha;` (constructor vacío; `estUsuario`/`funUsuario` vienen del JOIN con Usuario, para la UI).

`model/RevisionFuncional.java`:

```java
package com.reparaciones.servidor.model;

/** Campos de la parte funcional de una revisión (check marcado = defecto). */
public record RevisionFuncional(Integer bateriaPct, boolean pantTactil, boolean pantQuemada,
                                boolean pantMal, boolean camMancha, boolean camLente,
                                boolean altSup, boolean altInf, boolean mic, boolean faceId,
                                boolean ms, String msTexto, boolean bloqueoOp, String observacion) {}
```

- [ ] **Step 5: Test RED de RevisionDAO (guardado de partes + vigente)**

`RevisionDAOTest.java` — patrón exacto `ReparacionDAOUrgenteTest` (Mockito a mano, verify de SQL literal):

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import com.reparaciones.servidor.model.RevisionFuncional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOTest {

    private static final String IMEI = "351111112222333";

    /** Telefono EN_REVISION con revisión vigente id=7: guardar estética actualiza la fila vigente y espeja el grado. */
    @Test void guardarEsteticaActualizaVigenteYEspejaGrado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(7);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("EN_REVISION");
        dao.guardarEstetica(IMEI, "B", "P", 3);
        verify(jdbc).update("UPDATE Revision SET EST_GRADO = ?, EST_PANT = ?, EST_ID_USU = ?, EST_FECHA = NOW() WHERE ID_REVISION = ?",
                "B", "P", 3, 7);
        verify(jdbc).update("UPDATE Telefono SET GRADO_PROPIO = ? WHERE IMEI = ?", "B", IMEI);
    }

    @Test void guardarEsteticaRechazaSiNoEstaEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(7);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("OK");
        assertThrows(ResponseStatusException.class, () -> dao.guardarEstetica(IMEI, "B", null, 3));
        verify(jdbc, never()).update(contains("UPDATE Revision"), any(), any(), any(), any());
    }

    @Test void guardarFuncionalActualizaVigente() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(9);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("EN_REVISION");
        RevisionFuncional f = new RevisionFuncional(78, false, true, false, false, false,
                false, false, false, false, true, "pantalla", false, "obs");
        dao.guardarFuncional(IMEI, f, 5);
        verify(jdbc).update(contains("UPDATE Revision SET FUN_BATERIA_PCT = ?"),
                eq(78), eq(false), eq(true), eq(false), eq(false), eq(false), eq(false), eq(false),
                eq(false), eq(false), eq(true), eq("pantalla"), eq(false), eq("obs"), eq(5), eq(9));
    }

    @Test void getVigenteDevuelveNullSinFilas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        assertNull(dao.getVigente(IMEI));
    }
}
```

- [ ] **Step 6: Correr y ver RED**

```bash
mvn -q test -Dtest=RevisionDAOTest
```
Expected: FAIL de compilación (`RevisionDAO` no existe).

- [ ] **Step 7: Implementar `RevisionDAO` (núcleo)**

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import com.reparaciones.servidor.model.RevisionFuncional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Revisiones de teléfono (F2b): 1:N con Telefono, una fila por pasada;
 * la vigente = MAX(ID_REVISION) por IMEI. Parte guardada ≡ su *_FECHA IS NOT NULL.
 */
@Repository
public class RevisionDAO {

    private final JdbcTemplate jdbc;

    public RevisionDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** ID de la revisión vigente, o null si el teléfono nunca pasó por revisión. */
    private Integer idVigente(String imei) {
        return jdbc.queryForObject("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?", Integer.class, imei);
    }

    /** Guarda ediciones de partes solo con el teléfono EN_REVISION (la ficha es solo-lectura fuera). */
    private int vigenteEditable(String imei) {
        Integer id = idVigente(imei);
        if (id == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono no tiene revisión abierta");
        String estado = jdbc.queryForObject("SELECT ESTADO FROM Telefono WHERE IMEI = ?", String.class, imei);
        if (!"EN_REVISION".equals(estado))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono no está en revisión");
        return id;
    }

    @Transactional
    public void guardarEstetica(String imei, String grado, String pant, int idUsu) {
        int id = vigenteEditable(imei);
        jdbc.update("UPDATE Revision SET EST_GRADO = ?, EST_PANT = ?, EST_ID_USU = ?, EST_FECHA = NOW() WHERE ID_REVISION = ?",
                grado, pant, idUsu, id);
        // Espejo: el inventario sigue enseñando la verdad vigente en Telefono.GRADO_PROPIO
        jdbc.update("UPDATE Telefono SET GRADO_PROPIO = ? WHERE IMEI = ?", grado, imei);
    }

    @Transactional
    public void guardarFuncional(String imei, RevisionFuncional f, int idUsu) {
        int id = vigenteEditable(imei);
        jdbc.update("UPDATE Revision SET FUN_BATERIA_PCT = ?, FUN_PANT_TACTIL = ?, FUN_PANT_QUEMADA = ?," +
                " FUN_PANT_MAL = ?, FUN_CAM_MANCHA = ?, FUN_CAM_LENTE = ?, FUN_ALT_SUP = ?, FUN_ALT_INF = ?," +
                " FUN_MIC = ?, FUN_FACE_ID = ?, FUN_MS = ?, FUN_MS_TEXTO = ?, FUN_BLOQUEO_OP = ?," +
                " FUN_OBSERVACION = ?, FUN_ID_USU = ?, FUN_FECHA = NOW() WHERE ID_REVISION = ?",
                f.bateriaPct(), f.pantTactil(), f.pantQuemada(), f.pantMal(), f.camMancha(), f.camLente(),
                f.altSup(), f.altInf(), f.mic(), f.faceId(), f.ms(), f.msTexto(), f.bloqueoOp(),
                f.observacion(), idUsu, id);
    }

    /** Revisión vigente con nombres de usuario por parte, o null si nunca hubo. */
    public Revision getVigente(String imei) {
        List<Revision> filas = jdbc.query(
                "SELECT r.*, ue.NOMBRE_USUARIO AS EST_USUARIO, uf.NOMBRE_USUARIO AS FUN_USUARIO" +
                " FROM Revision r" +
                " LEFT JOIN Usuario ue ON ue.ID_USU = r.EST_ID_USU" +
                " LEFT JOIN Usuario uf ON uf.ID_USU = r.FUN_ID_USU" +
                " WHERE r.IMEI = ? ORDER BY r.ID_REVISION DESC LIMIT 1",
                (rs, row) -> {
                    Revision r = new Revision();
                    r.setIdRevision(rs.getInt("ID_REVISION"));
                    r.setImei(rs.getString("IMEI"));
                    r.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
                    r.setEstGrado(rs.getString("EST_GRADO"));
                    r.setEstPant(rs.getString("EST_PANT"));
                    r.setEstIdUsu((Integer) rs.getObject("EST_ID_USU"));
                    r.setEstUsuario(rs.getString("EST_USUARIO"));
                    r.setEstFecha(rs.getTimestamp("EST_FECHA") == null ? null : rs.getTimestamp("EST_FECHA").toLocalDateTime());
                    r.setFunBateriaPct((Integer) rs.getObject("FUN_BATERIA_PCT"));
                    r.setFunPantTactil(rs.getBoolean("FUN_PANT_TACTIL"));
                    r.setFunPantQuemada(rs.getBoolean("FUN_PANT_QUEMADA"));
                    r.setFunPantMal(rs.getBoolean("FUN_PANT_MAL"));
                    r.setFunCamMancha(rs.getBoolean("FUN_CAM_MANCHA"));
                    r.setFunCamLente(rs.getBoolean("FUN_CAM_LENTE"));
                    r.setFunAltSup(rs.getBoolean("FUN_ALT_SUP"));
                    r.setFunAltInf(rs.getBoolean("FUN_ALT_INF"));
                    r.setFunMic(rs.getBoolean("FUN_MIC"));
                    r.setFunFaceId(rs.getBoolean("FUN_FACE_ID"));
                    r.setFunMs(rs.getBoolean("FUN_MS"));
                    r.setFunMsTexto(rs.getString("FUN_MS_TEXTO"));
                    r.setFunBloqueoOp(rs.getBoolean("FUN_BLOQUEO_OP"));
                    r.setFunObservacion(rs.getString("FUN_OBSERVACION"));
                    r.setFunIdUsu((Integer) rs.getObject("FUN_ID_USU"));
                    r.setFunUsuario(rs.getString("FUN_USUARIO"));
                    r.setFunFecha(rs.getTimestamp("FUN_FECHA") == null ? null : rs.getTimestamp("FUN_FECHA").toLocalDateTime());
                    return r;
                }, imei);
        return filas.isEmpty() ? null : filas.get(0);
    }
}
```

- [ ] **Step 8: GREEN + suite completa**

```bash
mvn -q test -Dtest=RevisionDAOTest && mvn -q test
```
Expected: PASS; suite completa verde (29 tests previos + 4 nuevos).

- [ ] **Step 9: Commit**

```bash
git add sql/migracion-f2b-revision.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/model/Revision.java src/main/java/com/reparaciones/servidor/model/RevisionFuncional.java src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/test/java/com/reparaciones/servidor/dao/RevisionDAOTest.java
git commit -m "feat(f2b): tabla Revision, modelo y RevisionDAO nucleo (partes + vigente)"
```

---

### Task 2: Servidor — acción "A revisar" (clasificación por estado + endpoint masivo)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/RevisionDAOARevisarTest.java`

**Interfaces:**
- Consumes: `RevisionDAO` de T1; `LogDAO#insertar(int idUsu, String accion, String detalle)`.
- Produces: `RevisionDAO.ResultadoARevisar` (enum `PASADO, PASADO_ESTABA_OK, YA_ESTABA, EN_REPARACION, BLOQUEADO, FUERA, HISTORICO, NO_EXISTE`), `RevisionDAO#pasarARevisar(String imei) → ResultadoARevisar` (@Transactional por IMEI), endpoint `POST /api/telefonos/a-revisar` body `{"imeis":[...]}` → `[{"imei":"...","resultado":"PASADO"}...]` (JSON del record `ResultadoARevisarResponse(String imei, String resultado)`).

- [ ] **Step 1: Test RED — las 8 reglas de la spec §4**

`RevisionDAOARevisarTest.java` (mismo patrón Mockito). El helper `conTelefono` stubbea el SELECT de estado (query con RowMapper, lista con un elemento — puede ser null) y el COUNT de trabajos abiertos:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOARevisarTest {

    private static final String IMEI = "351111112222333";

    @SuppressWarnings("unchecked")
    private JdbcTemplate conTelefono(String estado, int abiertos) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(estado));   // singletonList admite null (List.of no)
        when(jdbc.queryForObject(contains("COUNT(*) FROM Reparacion"), eq(Integer.class), eq(IMEI))).thenReturn(abiertos);
        return jdbc;
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate sinTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        return jdbc;
    }

    private void verificarSinCambios(JdbcTemplate jdbc) {
        verify(jdbc, never()).update(contains("UPDATE Telefono SET ESTADO"), eq(IMEI));
        verify(jdbc, never()).update(contains("INSERT INTO Revision"), eq(IMEI));
    }

    @Test void recibidoPasaYCreaRevision() {
        JdbcTemplate jdbc = conTelefono("RECIBIDO", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.PASADO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ?", IMEI);
        verify(jdbc).update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", IMEI);
    }

    @Test void okPasaConAvisoYCreaRevisionNueva() {
        JdbcTemplate jdbc = conTelefono("OK", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verify(jdbc).update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", IMEI);
    }

    @Test void yaEnRevisionNoTocaNada() {
        JdbcTemplate jdbc = conTelefono("EN_REVISION", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.YA_ESTABA, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void conTrabajoAbiertoRechazado() {
        JdbcTemplate jdbc = conTelefono("RECIBIDO", 2);
        assertEquals(RevisionDAO.ResultadoARevisar.EN_REPARACION, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void bloqueadoRechazado() {
        JdbcTemplate jdbc = conTelefono("BLOQUEADO", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.BLOQUEADO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void enviadoYDesguaceFuera() {
        assertEquals(RevisionDAO.ResultadoARevisar.FUERA, new RevisionDAO(conTelefono("ENVIADO", 0)).pasarARevisar(IMEI));
        assertEquals(RevisionDAO.ResultadoARevisar.FUERA, new RevisionDAO(conTelefono("DESGUACE", 0)).pasarARevisar(IMEI));
    }

    @Test void historicoSinEstadoRechazado() {
        JdbcTemplate jdbc = conTelefono(null, 0);
        assertEquals(RevisionDAO.ResultadoARevisar.HISTORICO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void imeiDesconocidoNoExiste() {
        JdbcTemplate jdbc = sinTelefono();
        assertEquals(RevisionDAO.ResultadoARevisar.NO_EXISTE, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }
}
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=RevisionDAOARevisarTest
```
Expected: FAIL de compilación (enum y método no existen).

- [ ] **Step 3: Implementar `pasarARevisar` en RevisionDAO**

Añadir al DAO:

```java
    /** Resultado del escaneo "a revisar" (tabla de reglas spec F2b §4). */
    public enum ResultadoARevisar { PASADO, PASADO_ESTABA_OK, YA_ESTABA, EN_REPARACION, BLOQUEADO, FUERA, HISTORICO, NO_EXISTE }

    /**
     * Procesa UN IMEI del escaneo masivo: clasifica según su estado y, si procede,
     * lo pasa a EN_REVISION creando la fila de revisión de la pasada nueva.
     * Transaccional por IMEI: un fallo no tumba el resto del lote escaneado.
     */
    @Transactional
    public ResultadoARevisar pasarARevisar(String imei) {
        List<String> fila = jdbc.query(
                "SELECT ESTADO FROM Telefono WHERE IMEI = ?",
                (rs, row) -> rs.getString("ESTADO"),
                imei);
        if (fila.isEmpty()) return ResultadoARevisar.NO_EXISTE;
        String estado = fila.get(0);
        if (estado == null) return ResultadoARevisar.HISTORICO;   // fuera del ciclo (decisión 15)
        Integer abiertos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
        if (abiertos != null && abiertos > 0) return ResultadoARevisar.EN_REPARACION;
        return switch (estado) {
            case "EN_REVISION" -> ResultadoARevisar.YA_ESTABA;
            case "BLOQUEADO"   -> ResultadoARevisar.BLOQUEADO;
            case "ENVIADO", "DESGUACE" -> ResultadoARevisar.FUERA;
            case "RECIBIDO", "OK" -> {
                jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ?", imei);
                jdbc.update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", imei);
                yield "OK".equals(estado) ? ResultadoARevisar.PASADO_ESTABA_OK : ResultadoARevisar.PASADO;
            }
            default -> ResultadoARevisar.FUERA;
        };
    }
```

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=RevisionDAOARevisarTest
```
Expected: PASS (8-9 tests).

- [ ] **Step 5: Endpoint en TelefonoController**

Inyectar `RevisionDAO revisionDao` por constructor (junto a `dao`/`logDao` existentes). Añadir:

```java
    /** F2b: escaneo masivo "a revisar" — clasifica cada IMEI y pasa a EN_REVISION los que tocan. */
    @PostMapping("/a-revisar")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public List<ResultadoARevisarResponse> aRevisar(@RequestBody ImeisRequest req,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<ResultadoARevisarResponse> out = new java.util.ArrayList<>();
        for (String imei : new java.util.LinkedHashSet<>(req.imeis())) {
            RevisionDAO.ResultadoARevisar r = revisionDao.pasarARevisar(imei);
            if (r == RevisionDAO.ResultadoARevisar.PASADO || r == RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK) {
                logDao.insertar(principal.getIdUsu(), "A_REVISAR", "IMEI: " + imei
                        + (r == RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK ? ", ESTABA_OK" : ""));
            }
            out.add(new ResultadoARevisarResponse(imei, r.name()));
        }
        return out;
    }
```
Y los records privados al final del controller, junto a los existentes:

```java
    private record ImeisRequest(java.util.List<String> imeis) {}
    private record ResultadoARevisarResponse(String imei, String resultado) {}
```

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/test/java/com/reparaciones/servidor/dao/RevisionDAOARevisarTest.java
git commit -m "feat(f2b): escaneo masivo a-revisar con clasificacion por estado"
```

---

### Task 3: Servidor — endpoints de guardado de partes + bloqueo automático + GET revisión

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java` (método `bloquearPorRevision`)
- Test: ampliar `RevisionDAOTest.java`

**Interfaces:**
- Consumes: T1 (`guardarEstetica`/`guardarFuncional`/`getVigente`), `LogDAO#insertar(idUsu, accion, detalle, motivo)`.
- Produces: `PATCH /api/telefonos/{imei}/revision/estetica` body `{"grado":"B","pant":"P"}` → 204; `PATCH /{imei}/revision/funcional` body con los 14 campos de `RevisionFuncional` → 204 (si `bloqueoOp` → además ESTADO=BLOQUEADO); `GET /{imei}/revision` → `{"existe":true,"revision":{...}}` (siempre 200); `RevisionDAO#bloquearPorRevision(String imei) → boolean`.

- [ ] **Step 1: Test RED — bloqueo automático**

Añadir a `RevisionDAOTest`:

```java
    @Test void bloquearPorRevisionSoloDesdeEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        assertTrue(dao.bloquearPorRevision(IMEI));
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(0);
        assertFalse(dao.bloquearPorRevision(IMEI));
    }
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=RevisionDAOTest
```
Expected: FAIL de compilación (`bloquearPorRevision` no existe).

- [ ] **Step 3: Implementar `bloquearPorRevision`**

```java
    /** Bloqueo automático al guardar la funcional con "bloqueo operador". @return true si cambió el estado. */
    public boolean bloquearPorRevision(String imei) {
        return jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", imei) > 0;
    }
```

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=RevisionDAOTest
```
Expected: PASS.

- [ ] **Step 5: Endpoints en TelefonoController**

```java
    /** F2b: guarda la parte estética de la revisión vigente (sella autor+fecha, espeja grado). */
    @PatchMapping("/{imei}/revision/estetica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardarRevisionEstetica(@PathVariable String imei, @RequestBody EsteticaRequest req,
                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        revisionDao.guardarEstetica(imei, req.grado(), req.pant(), principal.getIdUsu());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_REVISION", "IMEI: " + imei + ", PARTE: ESTETICA");
    }

    /** F2b: guarda la parte funcional; con bloqueo de operador marcado, el teléfono pasa a BLOQUEADO. */
    @PatchMapping("/{imei}/revision/funcional")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardarRevisionFuncional(@PathVariable String imei, @RequestBody FuncionalRequest req,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        com.reparaciones.servidor.model.RevisionFuncional f = new com.reparaciones.servidor.model.RevisionFuncional(
                req.bateriaPct(), b(req.pantTactil()), b(req.pantQuemada()), b(req.pantMal()),
                b(req.camMancha()), b(req.camLente()), b(req.altSup()), b(req.altInf()), b(req.mic()),
                b(req.faceId()), b(req.ms()), req.msTexto(), b(req.bloqueoOp()), req.observacion());
        revisionDao.guardarFuncional(imei, f, principal.getIdUsu());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_REVISION", "IMEI: " + imei + ", PARTE: FUNCIONAL");
        if (f.bloqueoOp() && revisionDao.bloquearPorRevision(imei)) {
            logDao.insertar(principal.getIdUsu(), "BLOQUEAR_TELEFONO", "IMEI: " + imei,
                    "Bloqueo de operador detectado en revisión");
        }
    }

    /** F2b: revisión vigente (última pasada) para la ficha; existe=false si nunca hubo. */
    @GetMapping("/{imei}/revision")
    public RevisionResponse getRevision(@PathVariable String imei) {
        com.reparaciones.servidor.model.Revision r = revisionDao.getVigente(imei);
        return new RevisionResponse(r != null, r);
    }

    private static boolean b(Boolean v) { return Boolean.TRUE.equals(v); }
```
Records privados nuevos:

```java
    private record EsteticaRequest(String grado, String pant) {}
    private record FuncionalRequest(Integer bateriaPct, Boolean pantTactil, Boolean pantQuemada, Boolean pantMal,
                                    Boolean camMancha, Boolean camLente, Boolean altSup, Boolean altInf,
                                    Boolean mic, Boolean faceId, Boolean ms, String msTexto,
                                    Boolean bloqueoOp, String observacion) {}
    private record RevisionResponse(boolean existe, com.reparaciones.servidor.model.Revision revision) {}
```

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/test/java/com/reparaciones/servidor/dao/RevisionDAOTest.java
git commit -m "feat(f2b): endpoints guardar partes de revision + bloqueo automatico + GET vigente"
```

---

### Task 4: Servidor — acciones de estado (OK/bloquear/desbloquear/desguace) + hook quitar-OK

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (4 sitios: L396, L409, L718, L996 aprox.)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/RevisionDAOEstadoTest.java` + ampliar `ReparacionDAOUrgenteTest` o test nuevo `ReparacionDAOResetOkTest.java`

**Interfaces:**
- Consumes: T1/T3 (`getVigente`, patrón de excepciones 409).
- Produces: `RevisionDAO#marcarOk(String imei)`, `#bloquear(String)`, `#desbloquear(String)`, `#desguace(String)` (todos lanzan `ResponseStatusException(CONFLICT)` con mensaje si no procede); `POST /api/telefonos/{imei}/estado` body `{"accion":"OK|BLOQUEAR|DESBLOQUEAR|DESGUACE","motivo":"..."}`; `ReparacionDAO` privado `resetRevisionAlAsignar(String imei)` en los 4 puntos de alta de asignación.

- [ ] **Step 1: Test RED — validaciones del OK y transiciones**

`RevisionDAOEstadoTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOEstadoTest {

    private static final String IMEI = "351111112222333";

    /** Revision vigente completa (dos partes) con la batería indicada, servida vía el SELECT de getVigente. */
    private JdbcTemplate conVigente(Integer bateria, boolean ambasPartes, int abiertos) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Revision r = new Revision();
        r.setEstFecha(ambasPartes ? LocalDateTime.now() : null);
        r.setFunFecha(ambasPartes ? LocalDateTime.now() : null);
        r.setFunBateriaPct(bateria);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of(r));
        when(jdbc.queryForObject(contains("COUNT(*) FROM Reparacion"), eq(Integer.class), eq(IMEI))).thenReturn(abiertos);
        return jdbc;
    }

    @Test void okConTodoEnReglaCambiaEstado() {
        JdbcTemplate jdbc = conVigente(92, true, 0);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc).marcarOk(IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI);
    }

    @Test void okVetadoConBateriaBaja() {
        RevisionDAO dao = new RevisionDAO(conVigente(78, true, 0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.marcarOk(IMEI));
        assertTrue(ex.getReason().contains("Batería"));
    }

    @Test void okVetadoConBateriaNull() {
        assertThrows(ResponseStatusException.class, () -> new RevisionDAO(conVigente(null, true, 0)).marcarOk(IMEI));
    }

    @Test void okVetadoConRevisionIncompleta() {
        assertThrows(ResponseStatusException.class, () -> new RevisionDAO(conVigente(92, false, 0)).marcarOk(IMEI));
    }

    @Test void okVetadoConTrabajosAbiertos() {
        assertThrows(ResponseStatusException.class, () -> new RevisionDAO(conVigente(92, true, 2)).marcarOk(IMEI));
    }

    @Test void desbloquearVuelveAEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc).desbloquear(IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'", IMEI);
    }

    @Test void desguaceDesdeRevisionOBloqueo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc).desguace(IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI);
    }

    @Test void transicionSinFilasEs409() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq(IMEI))).thenReturn(0);
        assertThrows(ResponseStatusException.class, () -> new RevisionDAO(jdbc).desbloquear(IMEI));
    }
}
```

Y el test del hook (nuevo `ReparacionDAOResetOkTest.java`, mismo patrón que `ReparacionDAOUrgenteTest`, instanciando `new ReparacionDAO(jdbc, mock(BorradorDAO.class))`): para `insertarAsignacion`, `insertarAsignacionGlass` e `insertarAsignacionPulido`, verificar que tras el INSERT se ejecutan AMBOS updates:

```java
        verify(jdbc).update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
```
(Los stubs necesarios para `nextId`/`tieneAbiertaUrgente` se copian de `ReparacionDAOUrgenteTest`, que ya ejercita estos métodos.)

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest='RevisionDAOEstadoTest,ReparacionDAOResetOkTest'
```
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar acciones en RevisionDAO**

```java
    /** OK humano: exige revisión vigente completa, batería ≥ 85 y sin trabajos abiertos (veto duro en servidor). */
    @Transactional
    public void marcarOk(String imei) {
        Revision v = getVigente(imei);
        if (v == null || v.getEstFecha() == null || v.getFunFecha() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revisión incompleta: faltan partes por guardar");
        if (v.getFunBateriaPct() == null || v.getFunBateriaPct() < 85)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batería < 85: reparación obligatoria antes del OK");
        Integer abiertos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
        if (abiertos != null && abiertos > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tiene trabajos abiertos");
        transicion(imei, "UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'");
    }

    public void bloquear(String imei) {
        transicion(imei, "UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'");
    }

    /** Desbloquear devuelve a EN_REVISION; la derivación decide el resto (§2.1 spec canónica). */
    public void desbloquear(String imei) {
        transicion(imei, "UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'");
    }

    public void desguace(String imei) {
        transicion(imei, "UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')");
    }

    private void transicion(String imei, String sql) {
        if (jdbc.update(sql, imei) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El estado del teléfono cambió: recarga y reintenta");
    }
```

- [ ] **Step 4: Hook quitar-OK en ReparacionDAO**

Añadir junto a `ensureTelefono` (~L1075):

```java
    /** Al asignar trabajo caducan el check antiguo y el OK nuevo (se ponen a mano, se quitan solos). */
    private void resetRevisionAlAsignar(String imei) {
        jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
        jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", imei);
    }
```
Sustituir la línea exacta

```java
        jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
```

por `resetRevisionAlAsignar(imei);` en los CUATRO sitios donde aparece: `insertarAsignacion` (~L396), `insertarAsignacionGlass` (~L409), `marcarIncidenciaYAsignar` (~L718), `insertarAsignacionPulido` (~L996). Verificar con `grep -n "REVISION_LOGISTICA = 0" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` que tras el cambio solo queda la ocurrencia del helper. No tocar los inserts de nacimiento-cerrado (`insertar` prefijo R, `insertarCompleta`, `guardarFilaIndividual`): un trabajo que nace cerrado no reabre el ciclo (decisión de plan, espejo de la herencia urgente).

- [ ] **Step 5: Endpoint acciones**

```java
    /** F2b: acciones de estado de la revisión. OK/BLOQUEAR/DESBLOQUEAR/DESGUACE (motivo obligatorio). */
    @PostMapping("/{imei}/estado")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accionEstado(@PathVariable String imei, @RequestBody EstadoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        switch (req.accion() == null ? "" : req.accion()) {
            case "OK" -> {
                revisionDao.marcarOk(imei);
                logDao.insertar(principal.getIdUsu(), "TELEFONO_OK", "IMEI: " + imei);
            }
            case "BLOQUEAR" -> {
                revisionDao.bloquear(imei);
                logDao.insertar(principal.getIdUsu(), "BLOQUEAR_TELEFONO", "IMEI: " + imei, req.motivo());
            }
            case "DESBLOQUEAR" -> {
                revisionDao.desbloquear(imei);
                logDao.insertar(principal.getIdUsu(), "DESBLOQUEAR_TELEFONO", "IMEI: " + imei);
            }
            case "DESGUACE" -> {
                if (req.motivo() == null || req.motivo().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El desguace requiere motivo");
                revisionDao.desguace(imei);
                logDao.insertar(principal.getIdUsu(), "DESGUACE_TELEFONO", "IMEI: " + imei, req.motivo());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción desconocida");
        }
    }
```
Record: `private record EstadoRequest(String accion, String motivo) {}`.

- [ ] **Step 6: GREEN + suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/test/java/com/reparaciones/servidor/dao/RevisionDAOEstadoTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOResetOkTest.java
git commit -m "feat(f2b): acciones de estado con veto de bateria + hook quitar-OK al asignar"
```

---

### Task 5: Servidor — inventario con datos de revisión + derivados REVISADO/REPARADO

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/UbicacionDerivador.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java` (`getInventario`, L135-208)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/TelefonoInventario.java` (campos nuevos)
- Test: ampliar `UbicacionDerivadorTest.java`

**Interfaces:**
- Consumes: tabla `Revision` (T1); `UbicacionDerivador.derivar(estado, pul, glass, normal, idCli)` existente.
- Produces: sobrecarga `derivar(String estado, int pul, int glass, int normal, Integer idCli, boolean revisionCompleta, boolean repTrasRevision)`; `TelefonoInventario` (servidor) con setters `setRevDesde(LocalDateTime)`, `setEstFecha`, `setEstUsuario(String)`, `setFunFecha`, `setFunUsuario`, `setFunBateriaPct(Integer)`. El JSON del inventario incluye esos campos con esos nombres (el cliente los parsea por nombre en T6). `estadoEfectivo` puede valer ahora `REVISADO`/`REPARADO`.

- [ ] **Step 1: Test RED — derivación nueva**

Ampliar `UbicacionDerivadorTest` (mismo estilo de los existentes):

```java
    @Test void enRevisionConFichaCompletaEsRevisado() {
        var d = UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null, true, false);
        assertEquals("REVISADO", d.estadoEfectivo());
        assertEquals("PARA_REVISAR", d.ubicacion());
    }

    @Test void enRevisionConFichaCompletaYRepCerradaEsReparado() {
        var d = UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null, true, true);
        assertEquals("REPARADO", d.estadoEfectivo());
        assertEquals("PARA_REVISAR", d.ubicacion());
    }

    @Test void enRevisionSinFichaCompletaSigueSiendoEnRevision() {
        assertEquals("EN_REVISION", UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null, false, true).estadoEfectivo());
    }

    @Test void trabajoAbiertoMandaSobreRevisado() {
        assertEquals("EN_REPARACION", UbicacionDerivador.derivar("EN_REVISION", 1, 0, 0, null, true, false).estadoEfectivo());
    }

    @Test void sobrecargaViejaEquivaleASinRevision() {
        assertEquals("EN_REVISION", UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null).estadoEfectivo());
    }
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=UbicacionDerivadorTest
```
Expected: FAIL de compilación (sobrecarga de 7 args no existe).

- [ ] **Step 3: Implementar en UbicacionDerivador**

Mantener la firma de 5 args como delegación y añadir la nueva; el único cambio de lógica es el caso `EN_REVISION`:

```java
    public static Resultado derivar(String estado, int pulAbiertos, int glassAbiertos,
                                    int normalAbiertos, Integer idCli) {
        return derivar(estado, pulAbiertos, glassAbiertos, normalAbiertos, idCli, false, false);
    }

    public static Resultado derivar(String estado, int pulAbiertos, int glassAbiertos,
                                    int normalAbiertos, Integer idCli,
                                    boolean revisionCompleta, boolean repTrasRevision) {
        ...  // idéntico al cuerpo actual salvo el case EN_REVISION:
            case "EN_REVISION" -> new Resultado(
                    revisionCompleta ? (repTrasRevision ? "REPARADO" : "REVISADO") : "EN_REVISION",
                    "PARA_REVISAR", List.of());
        ...
    }
```
(Javadoc del case: `REVISADO` = dos partes guardadas esperando decisión; `REPARADO` = además hubo trabajo cerrado tras crear la pasada, esperando el OK humano.)

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=UbicacionDerivadorTest
```
Expected: PASS.

- [ ] **Step 5: Extender la query del inventario y el modelo**

En `TelefonoDAO.getInventario()`:

1. Tras la línea `"       COALESCE(i.INC_ABIERTAS,0) INC_ABIERTAS, COALESCE(s.SOL_PENDIENTES,0) SOL_PENDIENTES" +` añadir:

```java
            "       , rv.FECHA_CREACION AS REV_DESDE, rv.EST_FECHA, ue.NOMBRE_USUARIO AS EST_USUARIO," +
            "       rv.FUN_FECHA, uf.NOMBRE_USUARIO AS FUN_USUARIO, rv.FUN_BATERIA_PCT," +
            "       (rv.ID_REVISION IS NOT NULL AND EXISTS (SELECT 1 FROM Reparacion rr" +
            "           WHERE rr.IMEI = t.IMEI AND rr.FECHA_FIN IS NOT NULL" +
            "             AND rr.FECHA_FIN >= rv.FECHA_CREACION)) AS REP_TRAS_REVISION" +
```

2. Tras `" LEFT JOIN Proveedor p ON p.ID_PROV = l.ID_PROV" +` añadir:

```java
            " LEFT JOIN Revision rv ON rv.ID_REVISION = (SELECT MAX(r0.ID_REVISION) FROM Revision r0 WHERE r0.IMEI = t.IMEI)" +
            " LEFT JOIN Usuario ue ON ue.ID_USU = rv.EST_ID_USU" +
            " LEFT JOIN Usuario uf ON uf.ID_USU = rv.FUN_ID_USU" +
```

3. En el mapper, antes de la llamada a `UbicacionDerivador.derivar`:

```java
            Timestamp revDesde = rs.getTimestamp("REV_DESDE");
            inv.setRevDesde(revDesde == null ? null : revDesde.toLocalDateTime());
            Timestamp estFecha = rs.getTimestamp("EST_FECHA");
            inv.setEstFecha(estFecha == null ? null : estFecha.toLocalDateTime());
            inv.setEstUsuario(rs.getString("EST_USUARIO"));
            Timestamp funFecha = rs.getTimestamp("FUN_FECHA");
            inv.setFunFecha(funFecha == null ? null : funFecha.toLocalDateTime());
            inv.setFunUsuario(rs.getString("FUN_USUARIO"));
            inv.setFunBateriaPct((Integer) rs.getObject("FUN_BATERIA_PCT"));
```

4. Sustituir la llamada a `derivar` por la sobrecarga nueva:

```java
            var d = UbicacionDerivador.derivar(
                    inv.getEstado(), inv.getPulAbiertos(), inv.getGlassAbiertos(),
                    inv.getNormalAbiertos(), inv.getIdCli(),
                    inv.getEstFecha() != null && inv.getFunFecha() != null,
                    rs.getBoolean("REP_TRAS_REVISION"));
```

5. En `model/TelefonoInventario.java` (servidor): campos + getters/setters `LocalDateTime revDesde; LocalDateTime estFecha; String estUsuario; LocalDateTime funFecha; String funUsuario; Integer funBateriaPct;` (estilo de los existentes).

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/service/UbicacionDerivador.java src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java src/main/java/com/reparaciones/servidor/model/TelefonoInventario.java src/test/java/com/reparaciones/servidor/service/UbicacionDerivadorTest.java
git commit -m "feat(f2b): inventario con revision vigente y derivados REVISADO/REPARADO"
```

---

### Task 6: Cliente — modelos, DAO HTTP, textos de estado y situación de cola

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/TelefonoInventario.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/RevisionTelefono.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ResultadoARevisar.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/UbicacionTexto.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/SituacionRevision.java`
- Test: ampliar `UbicacionTextoTest.java`; Create `src/test/java/com/reparaciones/utils/SituacionRevisionTest.java`

**Interfaces:**
- Consumes: JSON del servidor T3/T5 (nombres de campo exactos: `revDesde`, `estFecha`, `estUsuario`, `funFecha`, `funUsuario`, `funBateriaPct`; GET revisión `{existe, revision:{estGrado, estPant, estUsuario, estFecha, funBateriaPct, funPantTactil, ..., funObservacion, funUsuario, funFecha, fechaCreacion}}`; a-revisar `[{imei, resultado}]`). `ApiClient.getList/post/patch`.
- Produces (para T7-T11): `TelefonoDAO#pasarARevisar(List<String>) → List<ResultadoARevisar>`, `#getRevision(String) → RevisionTelefono|null`, `#guardarRevisionEstetica(String imei, String grado, String pant)`, `#guardarRevisionFuncional(String imei, RevisionTelefono f)`, `#accionEstado(String imei, String accion, String motivo)`; `SituacionRevision.Situacion {POR_REVISAR, REVISADO, EN_REPARACION, REPARADO, OTRO}`, `SituacionRevision.de(TelefonoInventario)`, `.texto(Situacion)`; `UbicacionTexto.estado` mapea `REVISADO→"Revisado"`, `REPARADO→"Reparado"`.

- [ ] **Step 1: Test RED**

Ampliar `UbicacionTextoTest`:

```java
    @Test void estadosDerivadosNuevosDeF2b() {
        assertEquals("Revisado", UbicacionTexto.estado(tel("REVISADO", "PARA_REVISAR", List.of(), 0)));
        assertEquals("Reparado", UbicacionTexto.estado(tel("REPARADO", "PARA_REVISAR", List.of(), 0)));
    }
```

`SituacionRevisionTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SituacionRevisionTest {

    private TelefonoInventario tel(String estado, String efectivo) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEstado(estado);
        t.setEstadoEfectivo(efectivo);
        return t;
    }

    @Test void clasificaLasCuatroSituacionesDeLaCola() {
        assertEquals(SituacionRevision.Situacion.POR_REVISAR,   SituacionRevision.de(tel("EN_REVISION", "EN_REVISION")));
        assertEquals(SituacionRevision.Situacion.REVISADO,      SituacionRevision.de(tel("EN_REVISION", "REVISADO")));
        assertEquals(SituacionRevision.Situacion.EN_REPARACION, SituacionRevision.de(tel("EN_REVISION", "EN_REPARACION")));
        assertEquals(SituacionRevision.Situacion.REPARADO,      SituacionRevision.de(tel("EN_REVISION", "REPARADO")));
    }

    @Test void fueraDeLaColaEsOtro() {
        assertEquals(SituacionRevision.Situacion.OTRO, SituacionRevision.de(tel("RECIBIDO", "RECIBIDO")));
        assertEquals(SituacionRevision.Situacion.OTRO, SituacionRevision.de(tel("OK", "EN_REPARACION")));
    }

    @Test void textosDeChip() {
        assertEquals("por revisar", SituacionRevision.texto(SituacionRevision.Situacion.POR_REVISAR));
        assertEquals("Revisado — esperando decisión", SituacionRevision.texto(SituacionRevision.Situacion.REVISADO));
        assertEquals("en reparación", SituacionRevision.texto(SituacionRevision.Situacion.EN_REPARACION));
        assertEquals("Reparado — esperando OK", SituacionRevision.texto(SituacionRevision.Situacion.REPARADO));
    }
}
```

- [ ] **Step 2: RED**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente
mvn -q test -Dtest='UbicacionTextoTest,SituacionRevisionTest'
```
Expected: FAIL (mapeos y clase no existen).

- [ ] **Step 3: Implementar**

1. `UbicacionTexto.ESTADOS` — añadir dos pares al `Map.of` (queda con 9 entradas, bajo el límite de 10):

```java
    private static final Map<String, String> ESTADOS = Map.of(
            "RECIBIDO", "Recibido", "EN_REVISION", "En revisión", "BLOQUEADO", "Bloqueado",
            "EN_REPARACION", "En reparación", "OK", "OK", "ENVIADO", "Enviado", "DESGUACE", "Desguace",
            "REVISADO", "Revisado", "REPARADO", "Reparado");
```

2. `utils/SituacionRevision.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

/** Situación de una fila de la cola de revisión (estado BD EN_REVISION) según su estado efectivo. */
public final class SituacionRevision {

    private SituacionRevision() {}

    public enum Situacion { POR_REVISAR, REVISADO, EN_REPARACION, REPARADO, OTRO }

    public static Situacion de(TelefonoInventario t) {
        if (!"EN_REVISION".equals(t.getEstado())) return Situacion.OTRO;
        return switch (t.getEstadoEfectivo() == null ? "" : t.getEstadoEfectivo()) {
            case "REVISADO"      -> Situacion.REVISADO;
            case "REPARADO"      -> Situacion.REPARADO;
            case "EN_REPARACION" -> Situacion.EN_REPARACION;
            case "EN_REVISION"   -> Situacion.POR_REVISAR;
            default              -> Situacion.OTRO;
        };
    }

    public static String texto(Situacion s) {
        return switch (s) {
            case POR_REVISAR   -> "por revisar";
            case REVISADO      -> "Revisado — esperando decisión";
            case EN_REPARACION -> "en reparación";
            case REPARADO      -> "Reparado — esperando OK";
            case OTRO          -> "";
        };
    }
}
```

3. `TelefonoInventario` (cliente): campos + getters/setters `LocalDateTime revDesde; LocalDateTime estFecha; String estUsuario; LocalDateTime funFecha; String funUsuario; Integer funBateriaPct;` (junto a los existentes, mismo estilo).

4. `models/RevisionTelefono.java` — POJO Gson espejo del `Revision` del servidor (mismos nombres): `int idRevision; String imei; LocalDateTime fechaCreacion; String estGrado; String estPant; String estUsuario; LocalDateTime estFecha; Integer funBateriaPct; boolean funPantTactil, funPantQuemada, funPantMal, funCamMancha, funCamLente, funAltSup, funAltInf, funMic, funFaceId, funMs; String funMsTexto; boolean funBloqueoOp; String funObservacion; String funUsuario; LocalDateTime funFecha;` con getters/setters y constructor vacío.

5. `models/ResultadoARevisar.java`: `String imei; String resultado;` + getters/setters.

6. `TelefonoDAO` (cliente) — métodos nuevos:

```java
    /** F2b: escaneo masivo a revisar. Devuelve el resultado por IMEI (enum del servidor como texto). */
    public java.util.List<com.reparaciones.models.ResultadoARevisar> pasarARevisar(java.util.List<String> imeis) throws SQLException {
        com.reparaciones.models.ResultadoARevisar[] res = ApiClient.post(
                "/api/telefonos/a-revisar", java.util.Map.of("imeis", imeis),
                com.reparaciones.models.ResultadoARevisar[].class);
        return java.util.Arrays.asList(res);
    }

    /** F2b: revisión vigente para la ficha; null si el teléfono nunca pasó por revisión. */
    public com.reparaciones.models.RevisionTelefono getRevision(String imei) throws SQLException {
        RevisionResponse r = ApiClient.get("/api/telefonos/" + imei + "/revision", RevisionResponse.class);
        return (r != null && r.existe) ? r.revision : null;
    }

    private static class RevisionResponse {
        boolean existe;
        com.reparaciones.models.RevisionTelefono revision;
    }

    public void guardarRevisionEstetica(String imei, String grado, String pant) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("grado", grado);
        body.put("pant", pant);
        ApiClient.patch("/api/telefonos/" + imei + "/revision/estetica", body);
    }

    public void guardarRevisionFuncional(String imei, com.reparaciones.models.RevisionTelefono f) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("bateriaPct", f.getFunBateriaPct());
        body.put("pantTactil", f.isFunPantTactil());
        body.put("pantQuemada", f.isFunPantQuemada());
        body.put("pantMal", f.isFunPantMal());
        body.put("camMancha", f.isFunCamMancha());
        body.put("camLente", f.isFunCamLente());
        body.put("altSup", f.isFunAltSup());
        body.put("altInf", f.isFunAltInf());
        body.put("mic", f.isFunMic());
        body.put("faceId", f.isFunFaceId());
        body.put("ms", f.isFunMs());
        body.put("msTexto", f.getFunMsTexto());
        body.put("bloqueoOp", f.isFunBloqueoOp());
        body.put("observacion", f.getFunObservacion());
        ApiClient.patch("/api/telefonos/" + imei + "/revision/funcional", body);
    }

    /** F2b: acciones OK / BLOQUEAR / DESBLOQUEAR / DESGUACE (motivo solo en desguace/bloqueo). */
    public void accionEstado(String imei, String accion, String motivo) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("accion", accion);
        body.put("motivo", motivo);
        ApiClient.post("/api/telefonos/" + imei + "/estado", body);
    }
```
Nota: `ApiClient.get(path, Type)` ya existe; si su firma es `get(String, java.lang.reflect.Type)`, pasar `RevisionResponse.class` vale tal cual.

- [ ] **Step 4: GREEN + suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/models/TelefonoInventario.java src/main/java/com/reparaciones/models/RevisionTelefono.java src/main/java/com/reparaciones/models/ResultadoARevisar.java src/main/java/com/reparaciones/dao/TelefonoDAO.java src/main/java/com/reparaciones/utils/UbicacionTexto.java src/main/java/com/reparaciones/utils/SituacionRevision.java src/test/java/com/reparaciones/utils/UbicacionTextoTest.java src/test/java/com/reparaciones/utils/SituacionRevisionTest.java
git commit -m "feat(f2b): modelos de revision, metodos DAO y textos de estados derivados"
```

---

### Task 7: Cliente — diálogo masivo "A revisar"

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ARevisarDialog.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/TextoResultadoARevisar.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/TextoResultadoARevisarTest.java`

**Interfaces:**
- Consumes: `TelefonoDAO#pasarARevisar` (T6), `ImeiUtils.parsearPegadoImeis`, patrón de diálogo `NuevoSupplierDialog` (Stage APPLICATION_MODAL + hilo con Platform.runLater), `Alertas.mostrarError`.
- Produces: `ARevisarDialog.abrir(Window owner, Runnable onCambios)` (onCambios se dispara al cerrar si hubo algún PASADO); `TextoResultadoARevisar.texto(String resultado)` y `.esPasado(String resultado)`.

- [ ] **Step 1: Test RED — textos por resultado**

`TextoResultadoARevisarTest.java`:

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoARevisarTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ EN REVISIÓN", TextoResultadoARevisar.texto("PASADO"));
        assertEquals("→ EN REVISIÓN (estaba OK)", TextoResultadoARevisar.texto("PASADO_ESTABA_OK"));
        assertEquals("ya estaba en revisión", TextoResultadoARevisar.texto("YA_ESTABA"));
        assertEquals("rechazado: en reparación (volverá solo al terminar)", TextoResultadoARevisar.texto("EN_REPARACION"));
        assertEquals("rechazado: bloqueado — usar Desbloquear", TextoResultadoARevisar.texto("BLOQUEADO"));
        assertEquals("rechazado: fuera del circuito (enviado/desguace)", TextoResultadoARevisar.texto("FUERA"));
        assertEquals("rechazado: histórico — dar de alta en un lote", TextoResultadoARevisar.texto("HISTORICO"));
        assertEquals("no existe en el sistema", TextoResultadoARevisar.texto("NO_EXISTE"));
        assertEquals("resultado desconocido", TextoResultadoARevisar.texto("???"));
    }

    @Test void soloLosDosPasadosCuentanComoCambio() {
        assertTrue(TextoResultadoARevisar.esPasado("PASADO"));
        assertTrue(TextoResultadoARevisar.esPasado("PASADO_ESTABA_OK"));
        assertFalse(TextoResultadoARevisar.esPasado("YA_ESTABA"));
        assertFalse(TextoResultadoARevisar.esPasado("NO_EXISTE"));
    }
}
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=TextoResultadoARevisarTest
```
Expected: FAIL (clase no existe).

- [ ] **Step 3: Implementar `TextoResultadoARevisar`**

```java
package com.reparaciones.utils;

/** Textos de UI del resultado del escaneo "a revisar" (enum del servidor como string). */
public final class TextoResultadoARevisar {

    private TextoResultadoARevisar() {}

    public static String texto(String resultado) {
        return switch (resultado == null ? "" : resultado) {
            case "PASADO"           -> "→ EN REVISIÓN";
            case "PASADO_ESTABA_OK" -> "→ EN REVISIÓN (estaba OK)";
            case "YA_ESTABA"        -> "ya estaba en revisión";
            case "EN_REPARACION"    -> "rechazado: en reparación (volverá solo al terminar)";
            case "BLOQUEADO"        -> "rechazado: bloqueado — usar Desbloquear";
            case "FUERA"            -> "rechazado: fuera del circuito (enviado/desguace)";
            case "HISTORICO"        -> "rechazado: histórico — dar de alta en un lote";
            case "NO_EXISTE"        -> "no existe en el sistema";
            default                 -> "resultado desconocido";
        };
    }

    public static boolean esPasado(String resultado) {
        return "PASADO".equals(resultado) || "PASADO_ESTABA_OK".equals(resultado);
    }
}
```

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=TextoResultadoARevisarTest
```
Expected: PASS.

- [ ] **Step 5: Implementar `ARevisarDialog`**

Diálogo programático (patrón `NuevoSupplierDialog` para ventana/hilos; patrón `AltaManualLoteDialog` L313-362 para el campo de escaneo con pegado masivo — copiar el listener adaptado). Estructura completa:

```java
package com.reparaciones.controllers;

import com.reparaciones.dao.TelefonoDAO;
import com.reparaciones.models.ResultadoARevisar;
import com.reparaciones.utils.Alertas;
import com.reparaciones.utils.ImeiUtils;
import com.reparaciones.utils.ImeiUtils.ResultadoPegado;
import com.reparaciones.utils.ImeiUtils.TipoPegado;
import com.reparaciones.utils.TextoResultadoARevisar;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * F2b: escaneo masivo "A revisar" — cada IMEI escaneado se envía al servidor, que lo
 * clasifica (tabla de reglas spec §4) y pasa a EN_REVISION los que tocan. Una línea
 * de resultado por IMEI; contador abajo.
 */
public final class ARevisarDialog {

    private ARevisarDialog() {}

    public static void abrir(Window owner, Runnable onCambios) {
        TelefonoDAO dao = new TelefonoDAO();
        Set<String> vistos = new LinkedHashSet<>();
        int[] contadores = new int[3];                 // pasados, avisos, errores
        boolean[] huboCambios = { false };

        Label lblTitulo = new Label("Escanear IMEI:");
        lblTitulo.setStyle("-fx-font-weight: bold;");
        TextField tfScan = new TextField();
        tfScan.setPrefWidth(190);
        tfScan.setPromptText("Enter añade y limpia");
        Label lblScan = new Label();

        ListView<String> lista = new ListView<>();
        lista.setPrefSize(520, 300);
        Label lblContador = new Label("0 pasados a revisión · 0 avisos · 0 errores");

        Runnable actualizarContador = () -> lblContador.setText(
                contadores[0] + " pasados a revisión · " + contadores[1] + " avisos · " + contadores[2] + " errores");

        // Procesa un puñado (1..n IMEIs) contra el servidor en hilo aparte.
        java.util.function.Consumer<List<String>> procesar = imeis -> {
            List<String> nuevos = new ArrayList<>();
            for (String im : imeis) if (vistos.add(im)) nuevos.add(im);
            if (nuevos.isEmpty()) { lblScan.setText("Ya escaneado(s) en esta sesión."); return; }
            lblScan.setText("");
            new Thread(() -> {
                try {
                    List<ResultadoARevisar> res = dao.pasarARevisar(nuevos);
                    Platform.runLater(() -> {
                        for (ResultadoARevisar r : res) {
                            String texto = TextoResultadoARevisar.texto(r.getResultado());
                            lista.getItems().add(r.getImei() + "  ·  " + texto);
                            if (TextoResultadoARevisar.esPasado(r.getResultado())) { contadores[0]++; huboCambios[0] = true; }
                            else if ("NO_EXISTE".equals(r.getResultado()))          contadores[2]++;
                            else                                                     contadores[1]++;
                        }
                        actualizarContador.run();
                        lista.scrollTo(lista.getItems().size() - 1);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        nuevos.forEach(vistos::remove);   // reintentables
                        Alertas.mostrarError(e.getMessage());
                    });
                }
            }, "a-revisar").start();
        };

        Runnable intentarAnadir = () -> {
            String imei = tfScan.getText().trim();
            if (imei.length() != 15) return;
            procesar.accept(List.of(imei));
            Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
        };
        tfScan.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) {
                String solo = n.replaceAll("[^\\d]", "");
                Platform.runLater(() -> tfScan.setText(solo));
                return;
            }
            if (n.length() > 15) {
                ResultadoPegado res = ImeiUtils.parsearPegadoImeis(n);
                if (res.tipo() == TipoPegado.CORRUPTO) {
                    Platform.runLater(() -> {
                        tfScan.clear();
                        lblScan.setText("Algún IMEI del pegado está corrupto.");
                    });
                    return;
                }
                procesar.accept(res.imeis());
                Platform.runLater(() -> { tfScan.clear(); tfScan.requestFocus(); });
                return;
            }
            if (n.length() == 15) intentarAnadir.run();
        });
        tfScan.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) intentarAnadir.run(); });

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.getStyleClass().add("btn-primary");

        HBox fila = new HBox(8, lblTitulo, tfScan, lblScan);
        fila.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pie = new HBox(8, lblContador, spacer, btnCerrar);
        pie.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(10, fila, lista, pie);
        contenido.setPadding(new Insets(14));

        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) ventana.initOwner(owner);
        ventana.setResizable(false);
        ventana.setTitle("A revisar — escaneo masivo");
        btnCerrar.setOnAction(ev -> ventana.close());
        ventana.setOnHidden(ev -> { if (huboCambios[0] && onCambios != null) onCambios.run(); });

        Scene scene = new Scene(contenido);
        scene.getStylesheets().add(ARevisarDialog.class.getResource("/styles/app.css").toExternalForm());
        ventana.setScene(scene);
        Platform.runLater(tfScan::requestFocus);
        ventana.showAndWait();
    }
}
```

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/controllers/ARevisarDialog.java src/main/java/com/reparaciones/utils/TextoResultadoARevisar.java src/test/java/com/reparaciones/utils/TextoResultadoARevisarTest.java
git commit -m "feat(f2b): dialogo masivo A revisar con escaner y resultados por IMEI"
```

---

### Task 8: Cliente — ficha de revisión (estructura + guardado por parte)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java`
- Test: (la lógica testeable de la ficha — veredicto — llega en T9; esta task es UI + guardado, sin test unitario propio: cubierta por el smoke)

**Interfaces:**
- Consumes: `TelefonoDAO#getRevision/#guardarRevisionEstetica/#guardarRevisionFuncional` (T6), `TelefonoInventario` (fila de cola/inventario), `SelectorClienteDialog.elegir(List<Cliente>, Integer)` + `ClienteDAO#getActivos()` + `TelefonoDAO#actualizarCliente` (mecanismo existente), `Sesion.esSuperTecnico()`, estilos `toggle-pill-left/mid/right`, `Alertas.mostrarError`.
- Produces: `FichaRevisionDialog.abrir(Window owner, TelefonoInventario t, Runnable onCambios)`. Campos internos accesibles para T9 (misma clase): `zonaVeredicto` (VBox vacío bajo las columnas), `btnOk`, `btnAsignar`, `btnBloquear`, `btnDesbloquear`, `btnDesguace` (creados aquí deshabilitados/ocultos, cableados en T9), `revisionActual` (RevisionTelefono), `recargarChips()`.

- [ ] **Step 1: Implementar la ficha (layout A aprobado en brainstorm)**

Reglas de construcción (espejo del boceto `ficha-a-v2` + spec §5):

- `Stage` APPLICATION_MODAL con owner, título `"Ficha de revisión — " + imei`, patrón `NuevoSupplierDialog`.
- **Cabecera** (Label multiestilo): `modelo(+" eSIM" si esEsim) · storageGb GB · color · Grado prov. X · Lote batch (proveedor) · Estado: <UbicacionTexto.estado(t)>` — campos null → omitidos.
- **Dos columnas** (`HBox` de dos `VBox` con borde, cada una `VBox.setVgrow` y su botón Guardar en un `HBox` alineado `BOTTOM_RIGHT` tras un `Region` con `Priority.ALWAYS` vertical → los dos Guardar quedan al fondo y a la misma altura, requisito del usuario).
- **Columna estética**: chip de estado de parte (`Label lblChipEst`), toggles de grado `C/B/A-/A` (ToggleGroup, estilos pill; deseleccionable = grado null), toggles PANT `—/P/G` (— ≡ null), botón `Guardar estética`.
- **Columna funcional**: chip `lblChipFun`; `TextField tfBateria` (solo dígitos, máx 3); checks `Táctil/Quemada/Mal` (fila Pantalla), `Mancha/Lente` (Cámara), `Alt. sup/Alt. inf/Micro` (Sonido), `Face ID`, `MS` + `tfMsTexto` (habilitado solo con MS marcado), `Bloqueo operador`, `tfObservacion`; botón `Guardar funcional`.
- **Pie**: `Cliente:` + Hyperlink con el cliente actual (o "— sin cliente —") que abre `SelectorClienteDialog` (calco del handler `editarCli` de `AgrupadoController` L871-887, con `StaleDataException` → aviso y recarga) + zona de acciones: `btnOk` ("Marcar OK", verde), `btnAsignar` ("Asignar trabajos…"), `btnBloquear`, `btnDesbloquear`, `btnDesguace` — **en esta task se crean deshabilitados** (`setDisable(true)`); T9 los cablea. Entre columnas y pie va `zonaVeredicto` (VBox vacío, T9 lo puebla).
- **Chips de parte**: `"guardada · <usuario> · <dd/MM HH:mm>"` (verde) si `estFecha`/`funFecha` != null; `"pendiente"` (ámbar) si null. `recargarChips()` los recalcula desde `revisionActual`.
- **Carga**: al abrir, hilo → `dao.getRevision(imei)`; si null y estado EN_REVISION → ficha vacía editable (la fila Revision existe desde el escaneo; null solo pasa en consulta de teléfonos sin revisión → todo deshabilitado con aviso "Sin revisión"); poblar controles desde `revisionActual`.
- **Editable** = `Sesion.esSuperTecnico() && "EN_REVISION".equals(t.getEstado())`. No editable → todos los controles `setDisable(true)` (consulta ADMIN / teléfono fuera de revisión).
- **Guardar estética**: hilo → `dao.guardarRevisionEstetica(imei, gradoSeleccionado, pantSeleccionado)` → recargar revisión (`getRevision`) → `recargarChips()`; error → `Alertas.mostrarError`. Guardar funcional: ídem con `guardarRevisionFuncional` armando un `RevisionTelefono` desde los controles (bateria vacía → null); si `bloqueoOp` marcado, **antes** de enviar pedir confirmación con `ConfirmDialog.mostrar("Bloquear teléfono", "El check de bloqueo de operador está marcado: al guardar, el teléfono pasa a BLOQUEADO.", "Guardar y bloquear", () -> { ...enviar... })`; tras guardar con bloqueo → cerrar ficha y `onCambios.run()` (salió de la cola).
- Al cerrar (`setOnHidden`): si hubo algún guardado → `onCambios.run()`.

- [ ] **Step 2: Compilar + suite**

```bash
mvn -q test
```
Expected: BUILD SUCCESS (misma cuenta de tests; la ficha aún no se abre desde ningún sitio).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java
git commit -m "feat(f2b): ficha de revision con dos columnas y guardado por parte"
```

---

### Task 9: Cliente — veredicto (banner) + acciones de la ficha + enlace al modal de asignar

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/VeredictoRevision.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/MainController.java` (~L780 + campo estático)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` (~L311, junto a `irAInicio`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`abrirFormularioAsignacion` ~L1606 y ~L1649)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/VeredictoRevisionTest.java`

**Interfaces:**
- Consumes: `RevisionTelefono` (T6), `TelefonoDAO#accionEstado` (T6), `ConfirmDialog.mostrar/mostrarConMotivo`, `TipoTrabajo` (enum existente `REPARACION/GLASS/PULIDO`), `MainController.vistaCache` + `mostrarReparaciones()`, `ReparacionControllerSuperTecnico.mostrarPanel/pnlPendientes/btnTabPendientes`, `PendientesSuperTecnicoController.abrirFormularioAsignacion()` (toggles `tbRep/tbGlass/tbPulido`, `tipoActual`, `tfScan` — locals del método, L1635-1649 y L2250-2294).
- Produces: `VeredictoRevision.evaluar(RevisionTelefono) → Veredicto` (record `Veredicto(boolean bloqueado, boolean bateriaObligatoria, List<String> trabajos, boolean limpio)`); `VeredictoRevision.tipoPrincipal(Veredicto) → TipoTrabajo|null`; `MainController.irAAsignarTelefono(String imei, TipoTrabajo tipo)` (static); `ReparacionControllerSuperTecnico#abrirAsignacionPrecargada(String imei, TipoTrabajo tipo)`; `PendientesSuperTecnicoController#abrirAsignacionPrecargada(String imei, TipoTrabajo tipo)`.

- [ ] **Step 1: Test RED — evaluación del veredicto (única fuente: cliente; decisión de plan nº1)**

`VeredictoRevisionTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.RevisionTelefono;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VeredictoRevisionTest {

    private RevisionTelefono rev(Integer bateria, String pant, boolean defectoFuncional, boolean bloqueo) {
        RevisionTelefono r = new RevisionTelefono();
        r.setFunFecha(LocalDateTime.now());
        r.setFunBateriaPct(bateria);
        r.setEstPant(pant);
        r.setFunPantQuemada(defectoFuncional);
        r.setFunBloqueoOp(bloqueo);
        return r;
    }

    @Test void bloqueoMandaSobreTodo() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(90, "P", true, true));
        assertTrue(v.bloqueado());
        assertFalse(v.limpio());
    }

    @Test void bateriaBajaEsObligatoriaYAnadeNormal() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(78, null, false, false));
        assertTrue(v.bateriaObligatoria());
        assertEquals(List.of("NORMAL"), v.trabajos());
        assertFalse(v.limpio());
    }

    @Test void pantPDisparaPulidoYDefectoFuncionalNormal() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(95, "P", true, false));
        assertEquals(List.of("PULIDO", "NORMAL"), v.trabajos());
        assertFalse(v.limpio());
    }

    @Test void pantGDisparaGlass() {
        assertEquals(List.of("GLASS"), VeredictoRevision.evaluar(rev(95, "G", false, false)).trabajos());
    }

    @Test void limpioSoloConBateriaAltaYSinNada() {
        VeredictoRevision.Veredicto v = VeredictoRevision.evaluar(rev(94, null, false, false));
        assertTrue(v.limpio());
        assertTrue(v.trabajos().isEmpty());
    }

    @Test void bateriaNullNuncaEsLimpio() {
        assertFalse(VeredictoRevision.evaluar(rev(null, null, false, false)).limpio());
    }

    @Test void sinFuncionalGuardadaNoHayVeredicto() {
        RevisionTelefono r = rev(94, null, false, false);
        r.setFunFecha(null);
        assertNull(VeredictoRevision.evaluar(r));
    }

    @Test void tipoPrincipalPantMandaLuegoNormal() {
        assertEquals(TipoTrabajo.PULIDO, VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(95, "P", true, false))));
        assertEquals(TipoTrabajo.GLASS,  VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(95, "G", false, false))));
        assertEquals(TipoTrabajo.REPARACION, VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(78, null, false, false))));
        assertNull(VeredictoRevision.tipoPrincipal(VeredictoRevision.evaluar(rev(94, null, false, false))));
    }
}
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=VeredictoRevisionTest
```
Expected: FAIL (clase no existe).

- [ ] **Step 3: Implementar `VeredictoRevision`**

```java
package com.reparaciones.utils;

import com.reparaciones.models.RevisionTelefono;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluación del veredicto de la revisión funcional (spec F2b §5). Única fuente de la
 * regla (el servidor solo aplica el veto duro del OK y el bloqueo automático).
 * Reglas: bloqueo manda; batería &lt; 85 → reparación obligatoria (añade NORMAL);
 * PANT P/G → PULIDO/GLASS; cualquier check funcional → NORMAL; limpio = nada de lo anterior
 * con batería medida ≥ 85. Devuelve null si la parte funcional no está guardada.
 */
public final class VeredictoRevision {

    private VeredictoRevision() {}

    public record Veredicto(boolean bloqueado, boolean bateriaObligatoria, List<String> trabajos, boolean limpio) {}

    public static Veredicto evaluar(RevisionTelefono r) {
        if (r == null || r.getFunFecha() == null) return null;
        boolean bloqueado = r.isFunBloqueoOp();
        boolean bateriaObligatoria = r.getFunBateriaPct() != null && r.getFunBateriaPct() < 85;
        boolean defectoFuncional = r.isFunPantTactil() || r.isFunPantQuemada() || r.isFunPantMal()
                || r.isFunCamMancha() || r.isFunCamLente() || r.isFunAltSup() || r.isFunAltInf()
                || r.isFunMic() || r.isFunFaceId() || r.isFunMs();
        List<String> trabajos = new ArrayList<>();
        if ("P".equals(r.getEstPant())) trabajos.add("PULIDO");
        if ("G".equals(r.getEstPant())) trabajos.add("GLASS");
        if (defectoFuncional || bateriaObligatoria) trabajos.add("NORMAL");
        boolean limpio = !bloqueado && trabajos.isEmpty()
                && r.getFunBateriaPct() != null && r.getFunBateriaPct() >= 85;
        return new Veredicto(bloqueado, bateriaObligatoria, List.copyOf(trabajos), limpio);
    }

    /** Tipo con el que se precarga el modal de asignar: PANT manda; si no, NORMAL. Null si limpio. */
    public static TipoTrabajo tipoPrincipal(Veredicto v) {
        if (v == null || v.trabajos().isEmpty()) return null;
        return switch (v.trabajos().get(0)) {
            case "PULIDO" -> TipoTrabajo.PULIDO;
            case "GLASS"  -> TipoTrabajo.GLASS;
            default       -> TipoTrabajo.REPARACION;
        };
    }
}
```
(Si el enum `TipoTrabajo` vive en otro paquete o tiene otros nombres de constantes, ajustar aquí y en el test — verificar con `grep -rn "enum TipoTrabajo" src/main/java`.)

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=VeredictoRevisionTest
```
Expected: PASS.

- [ ] **Step 5: Banner + acciones en la ficha**

En `FichaRevisionDialog`:

- `void pintarVeredicto()`: limpia `zonaVeredicto`; `Veredicto v = VeredictoRevision.evaluar(revisionActual)`; si null → nada. Banner = `HBox` con fondo/borde por caso: bloqueado (rojo, "⛔ BLOQUEADO — bloqueo de operador"), batería (ámbar, `"🔋 Batería " + pct + "% — reparación obligatoria (< 85)"`), trabajos (azul, `"🔧 Necesita trabajos: " + trabajos legibles`), limpio (verde, "✅ Sin defectos — candidato a OK"). Batería + trabajos coinciden → un solo banner ámbar con ambas frases. Se llama al abrir y tras cada guardado (recalcula al reabrir, requisito spec).
- Habilitación de acciones (en `recargarChips()`/`pintarVeredicto()`): editable && `revisionActual` con ambas partes (`estFecha!=null && funFecha!=null`) → `btnOk.setDisable(bateriaObligatoria || bateríaNull)`; si falta alguna parte → `btnOk` disabled y, con funcional limpia pero estética pendiente, añadir al banner "— falta estética para el OK". `btnAsignar.setDisable(v == null || v.trabajos().isEmpty())`. `btnBloquear` visible si editable; `btnDesbloquear` visible solo si `"BLOQUEADO".equals(t.getEstado())` && esSuper; `btnDesguace` visible si esSuper && estado EN_REVISION o BLOQUEADO.
- Handlers (todos hilo aparte + `Platform.runLater`, cierre de ficha + `onCambios.run()` al éxito):
  - `btnOk`: `ConfirmDialog.mostrar("Marcar OK", "El teléfono pasará a OK (caja de listos).", "Marcar OK", () -> accion("OK", null))`.
  - `btnBloquear`: `ConfirmDialog.mostrarConMotivo("Bloquear teléfono", "Pasará a la caja de bloqueo.", "Bloquear", motivo -> accion("BLOQUEAR", motivo))`.
  - `btnDesbloquear`: `ConfirmDialog.mostrar("Desbloquear", "Volverá a EN REVISIÓN; la derivación decide el resto.", "Desbloquear", () -> accion("DESBLOQUEAR", null))`.
  - `btnDesguace`: `ConfirmDialog.mostrarConMotivo("Desguace", "Estado terminal, solo registro.", "Desguace", motivo -> accion("DESGUACE", motivo))`.
  - `accion(String, String)` → `dao.accionEstado(imei, accion, motivo)`; error SQLException → `Alertas.mostrarError` (los 409 del servidor traen el mensaje del veto: batería, partes, trabajos).
  - `btnAsignar`: cierra la ficha y llama `MainController.irAAsignarTelefono(imei, VeredictoRevision.tipoPrincipal(v))`.

- [ ] **Step 6: Cadena de navegación al modal de asignar**

1. `MainController`: campo `private static MainController instancia;` asignado al final de `initialize()` (`instancia = this;`). Método nuevo junto a `mostrarStockEnActual()` (~L800):

```java
    /** F2b: navega a Reparaciones → Asignaciones y abre el modal de asignación precargado con el IMEI. */
    public static void irAAsignarTelefono(String imei, com.reparaciones.utils.TipoTrabajo tipo) {
        if (instancia == null || !Sesion.esSuperTecnico()) return;
        instancia.mostrarReparaciones();
        Object[] cached = instancia.vistaCache.get("/views/ReparacionViewSuperTecnico.fxml");
        if (cached != null && cached[1] instanceof ReparacionControllerSuperTecnico rc)
            Platform.runLater(() -> rc.abrirAsignacionPrecargada(imei, tipo));
    }
```

2. `ReparacionControllerSuperTecnico` (junto a `irAInicio()`, ~L311):

```java
    /** F2b: fuerza el apartado Asignaciones y abre el modal de asignación con el IMEI precargado. */
    public void abrirAsignacionPrecargada(String imei, com.reparaciones.utils.TipoTrabajo tipo) {
        mostrarPanel(pnlPendientes, btnTabPendientes);
        pendientesSuperTecnicoController.abrirAsignacionPrecargada(imei, tipo);
    }
```

3. `PendientesSuperTecnicoController`: campos `private String precargaImei; private com.reparaciones.utils.TipoTrabajo precargaTipo;` y método público:

```java
    /** F2b: abre el modal de asignación con un IMEI ya escaneado y el tipo por defecto fijado. */
    public void abrirAsignacionPrecargada(String imei, com.reparaciones.utils.TipoTrabajo tipo) {
        this.precargaImei = imei;
        this.precargaTipo = tipo;
        abrirFormularioAsignacion();
    }
```
Dentro de `abrirFormularioAsignacion()` (~L1606), **justo antes de la llamada que muestra la ventana** (localizar el `showAndWait()`/`show()` del Stage al final del método), consumir la precarga — los locals `tbRep/tbGlass/tbPulido`, `tipoActual` y `tfScan` están en scope:

```java
        // ── F2b: precarga desde la ficha de revisión (IMEI + tipo principal del veredicto) ──
        if (precargaImei != null) {
            if (precargaTipo != null) {
                tipoActual[0] = precargaTipo;
                switch (precargaTipo) {
                    case GLASS  -> tbGlass.setSelected(true);
                    case PULIDO -> tbPulido.setSelected(true);
                    default     -> tbRep.setSelected(true);
                }
            }
            tfScan.setText(precargaImei);   // el listener existente lo añade y limpia el campo
            precargaImei = null;
            precargaTipo = null;
        }
```

- [ ] **Step 7: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/utils/VeredictoRevision.java src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java src/main/java/com/reparaciones/controllers/MainController.java src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java src/test/java/com/reparaciones/utils/VeredictoRevisionTest.java
git commit -m "feat(f2b): veredicto en cliente, acciones de la ficha y precarga del modal de asignar"
```

---

### Task 10: Cliente — panel Revisión (cola) en la pestaña Inventario

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/resources/views/RevisionPanelView.fxml`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/RevisionPanelController.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/InventarioView.fxml` (sidebar L17-25 + StackPane)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/InventarioController.java` (campos L38-42, `initialize` L56-66, `mostrarPanel` L70-92, `recargar` L221-225)

**Interfaces:**
- Consumes: `TelefonoDAO#getInventario()`, `SituacionRevision` (T6), `FichaRevisionDialog.abrir` (T8/T9), `ARevisarDialog.abrir` (T7), `FechaUtils.formatear` (existente, ver uso en `AgrupadoController` L1728), patrón sidebar `stock-sidebar-btn`.
- Produces: `RevisionPanelController#cargar()` público (lo llama `InventarioController` al conmutar/recargar).

- [ ] **Step 1: FXML del panel**

`RevisionPanelView.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<VBox xmlns="http://javafx.com/javafx/21" xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.reparaciones.controllers.RevisionPanelController"
      spacing="12" VBox.vgrow="ALWAYS">
    <HBox alignment="CENTER_LEFT" spacing="12">
        <Label text="Revisión" styleClass="vista-titulo"/>
    </HBox>
    <FlowPane hgap="12" vgap="6" alignment="CENTER_LEFT">
        <Label text="Escanear IMEI:" style="-fx-font-weight: bold;"/>
        <TextField fx:id="tfScan" prefWidth="190" promptText="Enter abre la ficha" styleClass="buscador"/>
        <Label fx:id="lblScan"/>
        <Button fx:id="btnMasivo" text="A revisar (masivo)" styleClass="btn-primary" onAction="#abrirMasivo"/>
    </FlowPane>
    <TableView fx:id="tabla" styleClass="tabla-reparaciones" VBox.vgrow="ALWAYS">
        <columns>
            <TableColumn fx:id="colImei"      text="IMEI"               prefWidth="140"/>
            <TableColumn fx:id="colModelo"    text="Modelo"             prefWidth="150"/>
            <TableColumn fx:id="colLote"      text="Lote"               prefWidth="110"/>
            <TableColumn fx:id="colSituacion" text="Situación"          prefWidth="210"/>
            <TableColumn fx:id="colEstetica"  text="Estética"           prefWidth="120"/>
            <TableColumn fx:id="colFuncional" text="Funcional"          prefWidth="120"/>
            <TableColumn fx:id="colDesde"     text="En revisión desde"  prefWidth="140"/>
        </columns>
    </TableView>
</VBox>
```

- [ ] **Step 2: Controller del panel**

`RevisionPanelController.java` — puntos obligatorios:

- Campos FXML de las columnas y `tabla` (`TableView<TelefonoInventario>`), lista completa `private List<TelefonoInventario> inventario = List.of();`.
- `cargar()`: hilo → `new TelefonoDAO().getInventario()` → `Platform.runLater`: guardar lista completa, poblar tabla con `estado == "EN_REVISION"` ordenados por `revDesde` asc (nulls al final).
- Cell values: IMEI (con `CsvExporter.textoForzado` NO — texto plano), modelo `t.getModelo() + (t.isEsEsim() ? " eSIM" : "")`, lote `getBatchNumber()`, situación `SituacionRevision.texto(SituacionRevision.de(t))`, estética `t.getEstFecha() != null ? "✓ " + t.getEstUsuario() : "pend."` (ídem funcional), desde `FechaUtils.formatear(t.getRevDesde(), fmt)` con el mismo `DateTimeFormatter` que usa `AgrupadoController` para Última actividad.
- RowFactory: filas con situación `EN_REPARACION` atenuadas (`setStyle("-fx-opacity: 0.55;")`, y "" al reciclar); doble clic → `abrirFicha(t)`.
- `abrirFicha(TelefonoInventario t)`: `FichaRevisionDialog.abrir(tabla.getScene().getWindow(), t, this::cargar)`; tras cerrar, `tfScan.requestFocus()`.
- Escáner `tfScan`: listener solo-dígitos (patrón T7) SIN pegado masivo (aquí es de a uno); con 15 dígitos o Enter: buscar el IMEI en `inventario` completo — no existe → `lblScan.setText("No existe en el sistema")`; existe con estado `EN_REVISION` → limpiar campo y `abrirFicha`; existe con otro estado → `lblScan` con el motivo (`"Está " + UbicacionTexto.estado(t) + " — usar A revisar (masivo) si procede"`); estado null → "Histórico — dar de alta en un lote".
- `abrirMasivo()`: `ARevisarDialog.abrir(tabla.getScene().getWindow(), this::cargar)`.
- Gating por rol: constructor/initialize no hace nada especial — las acciones dentro de la ficha ya gatean por `Sesion`; el botón masivo: `if (!Sesion.esSuperTecnico()) { btnMasivo.setVisible(false); btnMasivo.setManaged(false); tfScan.setPromptText("Enter abre la ficha (consulta)"); }`.

- [ ] **Step 3: Integración en la pestaña Inventario**

1. `InventarioView.fxml` — sidebar: botón nuevo entre Inventario y Suppliers:

```xml
            <Button fx:id="btnTabRevision" text="Revisión" maxWidth="Infinity"
                    styleClass="stock-sidebar-btn" onAction="#mostrarRevision"/>
```
Y en el StackPane, tras `pnlInventario`:

```xml
            <!-- Panel: Revisión (cola de EN_REVISION con escáner — F2b) -->
            <VBox fx:id="pnlRevision" visible="false" managed="false" VBox.vgrow="ALWAYS">
                <fx:include source="RevisionPanelView.fxml" fx:id="revisionPanel"/>
            </VBox>
```

2. `InventarioController`: campos `@FXML private Button btnTabRevision; @FXML private VBox pnlRevision; @FXML private RevisionPanelController revisionPanelController;`. Método `@FXML private void mostrarRevision() { mostrarPanel(pnlRevision, btnTabRevision); }`. En `mostrarPanel`: añadir `pnlRevision.setVisible(false); pnlRevision.setManaged(false);` al bloque de ocultar, `btnTabRevision` al array de botones, y la rama `else if (panel == pnlRevision) revisionPanelController.cargar();`. En `recargar()`: rama para `pnlRevision.isVisible() → revisionPanelController.cargar()`. En `exportarCSV`: si `pnlRevision` está visible, delegar en el comportamiento del panel Inventario NO — dejar la rama sin acción (mismo trato que el panel que no exporta; espejo exacto de cómo `exportarCSV` trata hoy `pnlSuppliers`: leer L230-234 y calcar la convención).

- [ ] **Step 4: Suite + commit**

```bash
mvn -q test
git add src/main/resources/views/RevisionPanelView.fxml src/main/java/com/reparaciones/controllers/RevisionPanelController.java src/main/resources/views/InventarioView.fxml src/main/java/com/reparaciones/controllers/InventarioController.java
git commit -m "feat(f2b): panel Revision con cola, escaner y acceso al masivo"
```

---

### Task 11: Cliente — integración en el inventario (badges + menú contextual)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (`configurarColEstado` L688-723, `configurarFilas` L825-909)

**Interfaces:**
- Consumes: `FichaRevisionDialog.abrir` (T8/T9), `ConfigVistaAgrupado.edicionAtributos`/`Vista`, estados efectivos nuevos en `estadoEfectivo`.
- Produces: menú contextual "Ficha de revisión" en filas de teléfono del inventario; badges de estado para `REVISADO`/`REPARADO`.

- [ ] **Step 1: Badges de estado**

En `configurarColEstado` (L688-723): añadir a la lógica de estilos dos casos junto a los existentes (leer el patrón real de "Recibido"/"En reparación" y calcarlo): `"Revisado"` → mismo tratamiento visual que los demás con color cian (`-fx-background-color: #CFFAFE; -fx-text-fill: #0E7490;` si el patrón es estilo inline) y `"Reparado"` → naranja (`#FFEDD5`/`#C2410C`). Los textos ya salen de `UbicacionTexto.estado(t)` (T6), así que la celda y el CSV heredan solos.

- [ ] **Step 2: Menú contextual "Ficha de revisión"**

En `configurarFilas` (L825+): crear `MenuItem fichaRev = new MenuItem("Ficha de revisión");` junto a los existentes y añadirlo al `ContextMenu` tras `editarAtr`. En `menu.setOnShowing`, rama de grupo (L891-909): `fichaRev.setVisible(esGrupo && modoActual == Modo.MAESTRO && vista == ConfigVistaAgrupado.Vista.INVENTARIO);` (visible para todos los roles del tab: la ficha ya es solo-lectura si no procede editar). Handler:

```java
                fichaRev.setOnAction(e -> {
                    if (!(getItem() instanceof TelefonoInventario t)) return;
                    FichaRevisionDialog.abrir(getScene().getWindow(), t, AgrupadoController.this::cargar);
                });
```

- [ ] **Step 3: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/controllers/AgrupadoController.java
git commit -m "feat(f2b): badges de estados derivados y ficha de revision desde el inventario"
```

---

### Task 12: Cierre — suites finales, review y pasos del usuario

**Files:** ninguno nuevo (verificación + operativa).

- [ ] **Step 1: Suites finales en ambos repos**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q test
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente && mvn -q test
```
Expected: BUILD SUCCESS en ambos (servidor ~29+20 nuevos, cliente ~164+15 nuevos; anotar cifras reales).

- [ ] **Step 2: Review final de rama (superpowers:requesting-code-review)** — ambos repos, contra la spec; verificar contratos JSON campo a campo cliente↔servidor (a-revisar, revision GET/PATCH, estado, inventario extendido), la clasificación de los 8 casos, el hook en los 4 sitios de ReparacionDAO, y las 2 decisiones de plan (veredicto solo-cliente; precarga tipo principal).

- [ ] **Step 3: Pasos del usuario (en orden, cada uno con su OK):**

1. Migración `migracion-f2b-revision.sql` en la VM con vista previa (el SELECT debe dar 0).
2. Arranque Spring local con el jar de la rama (contexto limpio, endpoints nuevos responden 403 sin auth).
3. OK merge servidor (`--no-ff`) + push + build VM + restart systemd.
4. Smoke con cliente en rama (checklist spec §10): masivo con los 8 casos, dos usuarios rellenando partes por separado, los 4 banners, veto batería (78% → OK vetado → corregir % → OK), bloqueo automático + desbloquear, desguace con motivo, chips de cola y reaparición del REPARADO al cerrar el último trabajo, precarga del modal de asignar (P→pulido), ADMIN solo-lectura, badges/CSV inventario, log de actividad.
5. OK merge cliente + bump gitlink + actualizar MER (tabla Revision) + checkboxes plan-futuro + decisión tag v0.17.0 (aplazada a después de F2b).

- [ ] **Step 4: Ledger** — anotar cierre en `.superpowers/sdd/progress.md`.
