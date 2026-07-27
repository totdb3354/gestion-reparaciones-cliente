# F2c Ciclo Completo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar el ciclo de vida del teléfono: envíos (entidad `Envio` + puente), devoluciones a almacén, trazabilidad completa en `Movimiento_telefono` con historial en la ficha, tags bajo la píldora, retirada de `REVISION_LOGISTICA` y 4 flecos de F2b.

**Architecture:** Dos repos. Servidor (Spring Boot + JdbcTemplate): `MovimientoDAO` (helper único de trazabilidad) inyectado en `RevisionDAO`/`ReparacionDAO`/`EnvioDAO`; tablas nuevas `Envio` + `Envio_Telefono`; endpoints `POST /api/envios`, `POST /api/telefonos/devoluciones`, `GET /{imei}/movimientos`; retirada del check antiguo con endpoint no-op tolerante. Cliente (JavaFX): `EnvioDialog`/`DevolucionDialog` programáticos (patrón A-revisar), multiselección acotada en el maestro, tags bajo la píldora (dato ya presente), historial perezoso en la ficha.

**Tech Stack:** Java 21, Spring Boot 3 (Jakarta), JdbcTemplate, MariaDB, JavaFX 21 + FXML, Gson (ApiClient), JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-07-27-f2c-ciclo-completo-design.md` (+ canónica F2 `2026-07-07`).

## Global Constraints

- Commits **sin** trailer `Co-Authored-By`. Merge/push/tag SOLO con OK explícito del usuario.
- Comandos por **Bash** (Maven incluido): `mvn -q test` en el repo que toque.
- Ramas: raíz `feature/f2c-ciclo-completo` (ya creada, con spec+plan); servidor `feature/f2c-ciclo-completo` (la crea la Task 1 desde main `d9a4b0b`).
- Migraciones las aplica el **usuario** con vista previa: script 1 (CREATEs) ANTES del deploy del servidor; script 2 (DROP `REVISION_LOGISTICA`) DESPUÉS de desplegar servidor y clientes. Orden global: script 1 → servidor → clientes → script 2.
- Roles exactos: mutaciones nuevas `@PreAuthorize("hasRole('SUPERTECNICO')")`.
- Ubicaciones (vocabulario `Movimiento_telefono`, exacto): `ALMACEN`, `PARA_REVISAR`, `BLOQUEO`, `REPARACIONES`, `LISTOS`, `PEDIDOS`, `ENVIADO`, `DESGUACE`. El origen de un movimiento se deriva SIEMPRE del estado previo real (OK → `LISTOS` o `PEDIDOS` según `ID_CLI`).
- Los movimientos los escriben las transiciones de ESTADO + enviar/devolución. Abrir/cerrar trabajos NO escribe movimientos (decisión de spec §5).
- Textos de UI en español. TDD: test RED antes de implementación en toda lógica; los pasos lo marcan.
- El gitlink `gestion-reparaciones-servidor` NUNCA se commitea en la raíz (se bumpea solo al cierre con OK).
- **Decisiones de plan** (presentar en review final): (1) `enviarLote` es UNA transacción para toda la remesa (resultados por IMEI, sin abortar por rechazados; el `Envio` se crea perezosamente al primer éxito — nunca vacío); (2) el endpoint viejo `PUT /{imei}/revision-logistica` queda como no-op tolerante 204 (clientes ≤v0.16) y se elimina en F3; (3) multiselección del maestro acotada: la ÚNICA acción masiva es "Enviar seleccionados (N)".

---

### Task 1: Servidor — migración CREATEs, modelo Movimiento y MovimientoDAO

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-f2c-envios.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (bloque DROP + CREATEs tras `Revision`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/MovimientoTelefono.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/MovimientoDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/MovimientoDAOTest.java`

**Interfaces:**
- Consumes: `JdbcTemplate` (constructor, patrón de la casa); tabla `Movimiento_telefono` (F2a) y las nuevas `Envio`/`Envio_Telefono`.
- Produces (para T2-T4): `MovimientoDAO#registrar(String imei, String origen, String destino, int idUsu, String motivo, String referencia)`, `#getPorImei(String) → List<MovimientoTelefono>`, `MovimientoDAO.ubicacionDe(String estado, Integer idCli) → String|null` (static). `MovimientoTelefono` POJO con getters `getFecha()`, `getUbicacionOrigen()`, `getUbicacionDestino()`, `getUsuario()`, `getMotivo()`, `getReferencia()`.

- [ ] **Step 1: Rama servidor**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only && git checkout -b feature/f2c-ciclo-completo
```
Verificar: `git log --oneline -1` muestra `d9a4b0b` (o descendiente en main).

- [ ] **Step 2: Escribir la migración (script 1)**

`sql/migracion-f2c-envios.sql` (cabecera banda `═`, NO idempotente):

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2c-envios.sql — F2c: tablas Envio y Envio_Telefono (spec 2026-07-27)
-- La aplica el usuario a mano en la VM con vista previa, ANTES de desplegar el
-- servidor F2c. NO idempotente (relanzar = error de tabla existente).
-- El DROP de REVISION_LOGISTICA va en migracion-f2c-drop-check.sql (DESPUÉS del
-- deploy de servidor y clientes — el servidor viejo aún escribe esa columna).
-- 1) Vista previa: el SELECT debe devolver 0 (ninguna de las dos existe).
-- 2) Ejecutar los dos CREATE TABLE.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Envio', 'Envio_Telefono');

-- Remesa de salida: venta individual (ID_CLI) o mayorista/plataforma (DESTINO_TEXTO).
-- Al menos un destino, validado en servidor. REFERENCIA = albarán/tracking externo.
CREATE TABLE Envio (
    ID_ENVIO      INT          NOT NULL AUTO_INCREMENT,
    FECHA         DATETIME     NOT NULL,
    ID_CLI        INT          NULL,
    DESTINO_TEXTO VARCHAR(150) NULL,
    REFERENCIA    VARCHAR(100) NULL,
    ID_USU        INT          NOT NULL,
    UPDATED_AT    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ENVIO),
    CONSTRAINT fk_envio_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI),
    CONSTRAINT fk_envio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU)
);

-- Estancia de un teléfono en una remesa; la activa = MAX(ID_ET) con DEVUELTO=0.
-- La devolución marca la fila (DEVUELTO + motivo + fecha + usuario), nunca borra.
CREATE TABLE Envio_Telefono (
    ID_ET             INT          NOT NULL AUTO_INCREMENT,
    ID_ENVIO          INT          NOT NULL,
    IMEI              VARCHAR(15)  NOT NULL,
    DEVUELTO          BOOLEAN      NOT NULL DEFAULT FALSE,
    MOTIVO_DEVOLUCION VARCHAR(255) NULL,
    FECHA_DEVOLUCION  DATETIME     NULL,
    ID_USU_DEVOLUCION INT          NULL,
    UPDATED_AT        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ET),
    KEY idx_et_imei (IMEI, ID_ET),
    CONSTRAINT fk_et_envio    FOREIGN KEY (ID_ENVIO)          REFERENCES Envio (ID_ENVIO),
    CONSTRAINT fk_et_telefono FOREIGN KEY (IMEI)              REFERENCES Telefono (IMEI),
    CONSTRAINT fk_et_usu_dev  FOREIGN KEY (ID_USU_DEVOLUCION) REFERENCES Usuario (ID_USU)
);
```

- [ ] **Step 3: Sync `crear_bd.sql` (solo los CREATEs)**

Añadir al bloque de drops (dentro del bloque `SET FOREIGN_KEY_CHECKS=0`, antes de `DROP TABLE IF EXISTS Revision;`):

```sql
DROP TABLE IF EXISTS Envio_Telefono;
DROP TABLE IF EXISTS Envio;
```

Y los dos `CREATE TABLE` **después del bloque `CREATE TABLE Revision (...)`** (dependencias: `Envio` → Cliente/Usuario; `Envio_Telefono` → Envio/Telefono/Usuario, todas ya creadas a esa altura). Copiar byte a byte de la migración. NO tocar la columna `REVISION_LOGISTICA` de `Telefono` en esta task (eso es T5).

- [ ] **Step 4: Modelo `MovimientoTelefono`**

`model/MovimientoTelefono.java` — POJO estilo de la casa (campos privados + getters/setters, constructor vacío): `int idMov; String imei; String ubicacionOrigen; String ubicacionDestino; LocalDateTime fecha; Integer idUsu; String usuario; String motivo; String referencia;` (`usuario` viene del JOIN con `Usuario.NOMBRE_USUARIO`, para la UI).

- [ ] **Step 5: Test RED de MovimientoDAO**

`MovimientoDAOTest.java` (patrón `RevisionDAOTest`: Mockito a mano, verify de SQL literal):

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.MovimientoTelefono;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MovimientoDAOTest {

    private static final String IMEI = "351111112222333";

    @Test void registrarInsertaConTodosLosCampos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new MovimientoDAO(jdbc).registrar(IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
        verify(jdbc).update(
                "INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA) VALUES (?, ?, ?, ?, ?, ?)",
                IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void getPorImeiVacioDevuelveListaVacia() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("FROM Movimiento_telefono m"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        assertTrue(new MovimientoDAO(jdbc).getPorImei(IMEI).isEmpty());
    }

    @Test void ubicacionDeMapeaTodosLosEstados() {
        assertEquals("ALMACEN",      MovimientoDAO.ubicacionDe("RECIBIDO", null));
        assertEquals("PARA_REVISAR", MovimientoDAO.ubicacionDe("EN_REVISION", null));
        assertEquals("BLOQUEO",      MovimientoDAO.ubicacionDe("BLOQUEADO", null));
        assertEquals("LISTOS",       MovimientoDAO.ubicacionDe("OK", null));
        assertEquals("PEDIDOS",      MovimientoDAO.ubicacionDe("OK", 5));
        assertEquals("ENVIADO",      MovimientoDAO.ubicacionDe("ENVIADO", null));
        assertEquals("DESGUACE",     MovimientoDAO.ubicacionDe("DESGUACE", null));
        assertNull(MovimientoDAO.ubicacionDe(null, null));
    }
}
```

- [ ] **Step 6: RED**

```bash
mvn -q test -Dtest=MovimientoDAOTest
```
Expected: FAIL de compilación (`MovimientoDAO` no existe).

- [ ] **Step 7: Implementar `MovimientoDAO`**

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.MovimientoTelefono;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Trazabilidad append-only del ciclo de vida (F2c): un movimiento por transición
 * de ESTADO + enviar/devolución. Abrir/cerrar trabajos NO escribe movimientos
 * (spec F2c §5): ese ir-y-venir ya lo cuentan los trabajos con sus fechas.
 */
@Repository
public class MovimientoDAO {

    private final JdbcTemplate jdbc;

    public MovimientoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Caja derivada de un ESTADO almacenado (vocabulario de Movimiento_telefono). */
    public static String ubicacionDe(String estado, Integer idCli) {
        if (estado == null) return null;
        return switch (estado) {
            case "RECIBIDO"    -> "ALMACEN";
            case "EN_REVISION" -> "PARA_REVISAR";
            case "BLOQUEADO"   -> "BLOQUEO";
            case "OK"          -> idCli != null ? "PEDIDOS" : "LISTOS";
            case "ENVIADO"     -> "ENVIADO";
            case "DESGUACE"    -> "DESGUACE";
            default            -> null;
        };
    }

    public void registrar(String imei, String origen, String destino, int idUsu,
                          String motivo, String referencia) {
        jdbc.update("INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA) VALUES (?, ?, ?, ?, ?, ?)",
                imei, origen, destino, idUsu, motivo, referencia);
    }

    /** Línea de vida cronológica (con nombre de usuario) para el historial de la ficha. */
    public List<MovimientoTelefono> getPorImei(String imei) {
        return jdbc.query(
                "SELECT m.ID_MOV, m.IMEI, m.UBICACION_ORIGEN, m.UBICACION_DESTINO, m.FECHA," +
                "       m.ID_USU, u.NOMBRE_USUARIO, m.MOTIVO, m.REFERENCIA" +
                " FROM Movimiento_telefono m" +
                " JOIN Usuario u ON u.ID_USU = m.ID_USU" +
                " WHERE m.IMEI = ? ORDER BY m.FECHA, m.ID_MOV",
                (rs, row) -> {
                    MovimientoTelefono mv = new MovimientoTelefono();
                    mv.setIdMov(rs.getInt("ID_MOV"));
                    mv.setImei(rs.getString("IMEI"));
                    mv.setUbicacionOrigen(rs.getString("UBICACION_ORIGEN"));
                    mv.setUbicacionDestino(rs.getString("UBICACION_DESTINO"));
                    mv.setFecha(rs.getTimestamp("FECHA").toLocalDateTime());
                    mv.setIdUsu((Integer) rs.getObject("ID_USU"));
                    mv.setUsuario(rs.getString("NOMBRE_USUARIO"));
                    mv.setMotivo(rs.getString("MOTIVO"));
                    mv.setReferencia(rs.getString("REFERENCIA"));
                    return mv;
                }, imei);
    }
}
```

- [ ] **Step 8: GREEN + suite completa**

```bash
mvn -q test -Dtest=MovimientoDAOTest && mvn -q test
```
Expected: PASS; suite completa verde (58 previos + 3 nuevos).

- [ ] **Step 9: Commit**

```bash
git add sql/migracion-f2c-envios.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/model/MovimientoTelefono.java src/main/java/com/reparaciones/servidor/dao/MovimientoDAO.java src/test/java/com/reparaciones/servidor/dao/MovimientoDAOTest.java
git commit -m "feat(f2c): tablas Envio, modelo Movimiento y MovimientoDAO (trazabilidad nucleo)"
```

---

### Task 2: Servidor — movimientos en las transiciones existentes + hook sin check viejo

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (constructor, `resetRevisionAlAsignar` ~L1080, firmas de los 4 puntos de alta)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java` (call sites con `principal.getIdUsu()`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (call sites de los 4 puntos de alta)
- Test: ampliar `RevisionDAOTest`, `RevisionDAOARevisarTest`, `RevisionDAOEstadoTest`, `ReparacionDAOResetOkTest`, `ReparacionDAOUrgenteTest` (instanciaciones + verifies nuevos)

**Interfaces:**
- Consumes: `MovimientoDAO` (T1).
- Produces (contratos que consumen T3-T5 y el controller): `RevisionDAO(JdbcTemplate, MovimientoDAO)`; firmas nuevas `pasarARevisar(String imei, int idUsu)`, `marcarOk(String imei, int idUsu)`, `bloquear(String imei, int idUsu, String motivo)`, `desbloquear(String imei, int idUsu)`, `desguace(String imei, int idUsu, String motivo)`, `bloquearPorRevision(String imei, int idUsu)`. `ReparacionDAO(JdbcTemplate, BorradorDAO, MovimientoDAO)`; los 4 puntos de alta ganan un último parámetro `int idUsu`: `insertarAsignacion(..., int idUsu)`, `insertarAsignacionGlass(..., int idUsu)`, `marcarIncidenciaYAsignar(..., int idUsu)`, `insertarAsignacionPulido(..., int idUsu)`.

- [ ] **Step 1: Test RED — movimientos por transición**

Ampliar `RevisionDAOARevisarTest` (helper `conTelefono` pasa a stubbear `SELECT ESTADO, ID_CLI`; ver Step 3) con:

```java
    @Test void recibidoQuePasaEscribeMovimiento() {
        JdbcTemplate jdbc = conTelefono("RECIBIDO", 0);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).pasarARevisar(IMEI, 3);
        verify(mov).registrar(IMEI, "ALMACEN", "PARA_REVISAR", 3, null, null);
    }

    @Test void okQuePasaEscribeMovimientoDesdeListos() {
        JdbcTemplate jdbc = conTelefono("OK", 0);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).pasarARevisar(IMEI, 3);
        verify(mov).registrar(IMEI, "LISTOS", "PARA_REVISAR", 3, null, null);
    }

    @Test void rechazadoNoEscribeMovimiento() {
        JdbcTemplate jdbc = conTelefono("BLOQUEADO", 0);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).pasarARevisar(IMEI, 3);
        verifyNoInteractions(mov);
    }
```

Ampliar `RevisionDAOEstadoTest` con:

```java
    @Test void okEscribeMovimientoAListosOPedidos() {
        JdbcTemplate jdbc = conVigente(92, true, 0);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("SELECT ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(java.util.Collections.singletonList((Integer) null));
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).marcarOk(IMEI, 7);
        verify(mov).registrar(IMEI, "PARA_REVISAR", "LISTOS", 7, null, null);
    }

    @Test void bloquearEscribeMovimientoConMotivo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).bloquear(IMEI, 7, "MS externa");
        verify(mov).registrar(IMEI, "PARA_REVISAR", "BLOQUEO", 7, "MS externa", null);
    }

    @Test void desguaceEscribeMovimientoDesdeEstadoPrevio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(java.util.Collections.singletonList("BLOQUEADO"));
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI)).thenReturn(1);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).desguace(IMEI, 7, "placa muerta");
        verify(mov).registrar(IMEI, "BLOQUEO", "DESGUACE", 7, "placa muerta", null);
    }
```

Y en `ReparacionDAOResetOkTest`: sustituir el verify de `REVISION_LOGISTICA` por el de movimiento (el hook deja de tocar el check viejo):

```java
        // ANTES verificaba: UPDATE Telefono SET REVISION_LOGISTICA = 0 ... — ELIMINADO (F2c)
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
        // Con flip OK->EN_REVISION (update devuelve 1) el hook escribe el movimiento:
        verify(mov).registrar(eq(IMEI), eq("LISTOS"), eq("PARA_REVISAR"), eq(ID_USU), eq("Trabajo asignado"), isNull());
```

(los stubs del test deben hacer `thenReturn(1)` para ese UPDATE, y `SELECT ID_CLI` → `singletonList((Integer) null)`; añadir un caso `flipNoOcurreNoEscribeMovimiento` con `thenReturn(0)` y `verifyNoInteractions(mov)`).

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest='RevisionDAOARevisarTest,RevisionDAOEstadoTest,ReparacionDAOResetOkTest'
```
Expected: FAIL de compilación (constructores y firmas nuevas no existen).

- [ ] **Step 3: Implementar en RevisionDAO**

1. Constructor: `public RevisionDAO(JdbcTemplate jdbc, MovimientoDAO movimientoDao)` (campo `private final MovimientoDAO movimientoDao;`).
2. `pasarARevisar(String imei, int idUsu)`: el SELECT inicial pasa a `"SELECT ESTADO, ID_CLI FROM Telefono WHERE IMEI = ?"` con mapper `(rs, row) -> new Object[]{ rs.getString("ESTADO"), (Integer) rs.getObject("ID_CLI") }` (una fila → `fila.get(0)`); en la rama de éxito `RECIBIDO/OK`, tras el INSERT de la pasada:

```java
                movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe(estado, idCli), "PARA_REVISAR", idUsu, null, null);
```

3. `marcarOk(String imei, int idUsu)`: tras `transicion(...)` con éxito, leer `ID_CLI` (`jdbc.query("SELECT ID_CLI FROM Telefono WHERE IMEI = ?", (rs, row) -> (Integer) rs.getObject("ID_CLI"), imei)`, primera fila o null) y:

```java
        movimientoDao.registrar(imei, "PARA_REVISAR", MovimientoDAO.ubicacionDe("OK", idCli), idUsu, null, null);
```

4. `bloquear(String imei, int idUsu, String motivo)`: tras transición, `movimientoDao.registrar(imei, "PARA_REVISAR", "BLOQUEO", idUsu, motivo, null);`. `bloquearPorRevision(String imei, int idUsu)`: si el UPDATE devuelve >0, `movimientoDao.registrar(imei, "PARA_REVISAR", "BLOQUEO", idUsu, "Bloqueo de operador detectado en revisión", null);`.
5. `desbloquear(String imei, int idUsu)`: tras transición, `registrar(imei, "BLOQUEO", "PARA_REVISAR", idUsu, null, null)`.
6. `desguace(String imei, int idUsu, String motivo)`: ANTES de la transición leer estado previo (`SELECT ESTADO FROM Telefono WHERE IMEI = ?` con mapper string, primera fila); tras transición, `registrar(imei, MovimientoDAO.ubicacionDe(estadoPrevio, null), "DESGUACE", idUsu, motivo, null)`.
7. `TelefonoController`: actualizar los call sites (`aRevisar` → `pasarARevisar(imei, principal.getIdUsu())`; `accionEstado` → `marcarOk(imei, principal.getIdUsu())`, `bloquear(imei, principal.getIdUsu(), req.motivo())`, `desbloquear(imei, principal.getIdUsu())`, `desguace(imei, principal.getIdUsu(), req.motivo())`; `guardarRevisionFuncional` → `bloquearPorRevision(imei, principal.getIdUsu())`). La lógica de logs del controller NO cambia.

- [ ] **Step 4: Implementar el hook en ReparacionDAO**

1. Constructor: `public ReparacionDAO(JdbcTemplate jdbc, BorradorDAO borradorDao, MovimientoDAO movimientoDao)`.
2. `resetRevisionAlAsignar(String imei)` pasa a `resetRevisionAlAsignar(String imei, int idUsu)` (~L1080):

```java
    /** Al asignar trabajo, un teléfono OK vuelve solo al ciclo de revisión (pasada nueva NO: misma pasada). */
    private void resetRevisionAlAsignar(String imei, int idUsu) {
        Integer idCli = jdbc.query("SELECT ID_CLI FROM Telefono WHERE IMEI = ?",
                (rs, row) -> (Integer) rs.getObject("ID_CLI"), imei).stream().findFirst().orElse(null);
        if (jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", imei) > 0) {
            movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe("OK", idCli), "PARA_REVISAR", idUsu, "Trabajo asignado", null);
        }
    }
```

(La línea `UPDATE Telefono SET REVISION_LOGISTICA = 0 ...` se ELIMINA — verificar con `grep -n "REVISION_LOGISTICA = 0" src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` que no queda ninguna.)
3. Los 4 puntos de alta (`insertarAsignacion`, `insertarAsignacionGlass`, `marcarIncidenciaYAsignar`, `insertarAsignacionPulido`) ganan último parámetro `int idUsu` y llaman `resetRevisionAlAsignar(imei, idUsu)`. `ReparacionController` pasa `principal.getIdUsu()` en sus 4 call sites (L185 y análogos — localizar por nombre de método).
4. Tests existentes: actualizar TODAS las instanciaciones `new ReparacionDAO(jdbc, mock(BorradorDAO.class))` → `new ReparacionDAO(jdbc, mock(BorradorDAO.class), mov)` (con `MovimientoDAO mov = mock(MovimientoDAO.class);`) y `new RevisionDAO(jdbc)` → `new RevisionDAO(jdbc, mock(MovimientoDAO.class))` en los tests que no verifican movimientos.

- [ ] **Step 5: GREEN + suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/test/java/com/reparaciones/servidor/dao/
git commit -m "feat(f2c): movimientos en todas las transiciones de estado y hook sin check viejo"
```

---

### Task 3: Servidor — EnvioDAO + POST /api/envios

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/EnvioDAO.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/EnvioController.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/EnvioDAOTest.java`

**Interfaces:**
- Consumes: `MovimientoDAO` (T1), `LogDAO#insertar(int, String, String)`, `SimpleJdbcInsert` NO (usar `jdbc.update` + `queryForObject LAST_INSERT_ID()` — patrón: ver nota Step 3).
- Produces (para T6-T7 cliente): `EnvioDAO.ItemEnvio` record `(String imei, String resultado, String estado)` con resultado ∈ `ENVIADO|NO_OK|NO_EXISTE|HISTORICO`; `EnvioDAO#enviarLote(Integer idCli, String destinoTexto, String referencia, List<String> imeis, int idUsu) → ResultadoLote` record `(Integer idEnvio, List<ItemEnvio> items)`; endpoint `POST /api/envios` body `{"idCli":n|null,"destinoTexto":"...","referencia":"...","imeis":[...]}` → `{"idEnvio":n|null,"items":[{"imei","resultado","estado"}]}`; 400 si ambos destinos vacíos.

- [ ] **Step 1: Test RED — reglas del envío**

`EnvioDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnvioDAOTest {

    private static final String IMEI = "351111112222333";

    @SuppressWarnings("unchecked")
    private JdbcTemplate conTelefono(String estado, Integer idCli) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ estado, idCli }));
        when(jdbc.update(eq("UPDATE Telefono SET ESTADO = 'ENVIADO', ES_DEVOLUCION = 0 WHERE IMEI = ? AND ESTADO = 'OK'"), eq(IMEI)))
                .thenReturn("OK".equals(estado) ? 1 : 0);
        when(jdbc.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Integer.class))).thenReturn(7);
        return jdbc;
    }

    @Test void telefonoOkSeEnviaCreaEnvioYPuente() {
        JdbcTemplate jdbc = conTelefono("OK", null);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ResultadoLote r = new EnvioDAO(jdbc, mov).enviarLote(null, "CashPhone", "ALB-99", List.of(IMEI), 3);
        assertEquals(7, r.idEnvio());
        assertEquals("ENVIADO", r.items().get(0).resultado());
        verify(jdbc).update(eq("INSERT INTO Envio (FECHA, ID_CLI, DESTINO_TEXTO, REFERENCIA, ID_USU) VALUES (NOW(), ?, ?, ?, ?)"),
                isNull(), eq("CashPhone"), eq("ALB-99"), eq(3));
        verify(jdbc).update("INSERT INTO Envio_Telefono (ID_ENVIO, IMEI) VALUES (?, ?)", 7, IMEI);
        verify(mov).registrar(IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void telefonoOkConClienteSaleDePedidos() {
        JdbcTemplate jdbc = conTelefono("OK", 5);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new EnvioDAO(jdbc, mov).enviarLote(null, "CashPhone", null, List.of(IMEI), 3);
        verify(mov).registrar(IMEI, "PEDIDOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void noOkSeRechazaConSuEstadoYNoCreaEnvio() {
        JdbcTemplate jdbc = conTelefono("EN_REVISION", null);
        EnvioDAO.ResultadoLote r = new EnvioDAO(jdbc, mock(MovimientoDAO.class)).enviarLote(null, "X", null, List.of(IMEI), 3);
        assertNull(r.idEnvio());
        assertEquals("NO_OK", r.items().get(0).resultado());
        assertEquals("EN_REVISION", r.items().get(0).estado());
        verify(jdbc, never()).update(contains("INSERT INTO Envio"), any(), any(), any(), any());
    }

    @Test void historicoYNoExistente() {
        assertEquals("HISTORICO", new EnvioDAO(conTelefono(null, null), mock(MovimientoDAO.class))
                .enviarLote(null, "X", null, List.of(IMEI), 3).items().get(0).resultado());
        JdbcTemplate sin = mock(JdbcTemplate.class);
        when(sin.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        assertEquals("NO_EXISTE", new EnvioDAO(sin, mock(MovimientoDAO.class))
                .enviarLote(null, "X", null, List.of(IMEI), 3).items().get(0).resultado());
    }
}
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=EnvioDAOTest
```
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar `EnvioDAO`**

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Remesas de salida (F2c). enviarLote = UNA transacción para toda la remesa
 * (decisión de plan nº1): resultados por IMEI sin abortar por rechazados; el
 * Envio se crea perezosamente al primer éxito — nunca queda vacío (lección F2a).
 */
@Repository
public class EnvioDAO {

    public record ItemEnvio(String imei, String resultado, String estado) {}
    public record ResultadoLote(Integer idEnvio, List<ItemEnvio> items) {}

    private final JdbcTemplate jdbc;
    private final MovimientoDAO movimientoDao;

    public EnvioDAO(JdbcTemplate jdbc, MovimientoDAO movimientoDao) {
        this.jdbc = jdbc;
        this.movimientoDao = movimientoDao;
    }

    @Transactional
    public ResultadoLote enviarLote(Integer idCli, String destinoTexto, String referencia,
                                    List<String> imeis, int idUsu) {
        Integer idEnvio = null;
        List<ItemEnvio> items = new ArrayList<>();
        for (String imei : new java.util.LinkedHashSet<>(imeis)) {
            List<Object[]> fila = jdbc.query("SELECT ESTADO, ID_CLI FROM Telefono WHERE IMEI = ?",
                    (rs, row) -> new Object[]{ rs.getString("ESTADO"), (Integer) rs.getObject("ID_CLI") }, imei);
            if (fila.isEmpty()) { items.add(new ItemEnvio(imei, "NO_EXISTE", null)); continue; }
            String estado = (String) fila.get(0)[0];
            Integer idCliTel = (Integer) fila.get(0)[1];
            if (estado == null) { items.add(new ItemEnvio(imei, "HISTORICO", null)); continue; }
            int flip = jdbc.update("UPDATE Telefono SET ESTADO = 'ENVIADO', ES_DEVOLUCION = 0 WHERE IMEI = ? AND ESTADO = 'OK'", imei);
            if (flip == 0) { items.add(new ItemEnvio(imei, "NO_OK", estado)); continue; }
            if (idEnvio == null) {
                jdbc.update("INSERT INTO Envio (FECHA, ID_CLI, DESTINO_TEXTO, REFERENCIA, ID_USU) VALUES (NOW(), ?, ?, ?, ?)",
                        idCli, destinoTexto, referencia, idUsu);
                idEnvio = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
            }
            jdbc.update("INSERT INTO Envio_Telefono (ID_ENVIO, IMEI) VALUES (?, ?)", idEnvio, imei);
            movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe("OK", idCliTel), "ENVIADO", idUsu, null, "ENVIO " + idEnvio);
            items.add(new ItemEnvio(imei, "ENVIADO", null));
        }
        return new ResultadoLote(idEnvio, items);
    }
}
```

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=EnvioDAOTest
```
Expected: PASS.

- [ ] **Step 5: `EnvioController`**

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EnvioDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** F2c: remesas de salida. Solo teléfonos OK entran; resultado por IMEI. */
@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final EnvioDAO envioDao;
    private final LogDAO logDao;

    public EnvioController(EnvioDAO envioDao, LogDAO logDao) {
        this.envioDao = envioDao;
        this.logDao = logDao;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public EnvioDAO.ResultadoLote enviar(@RequestBody EnvioRequest req,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean sinCliente = req.idCli() == null;
        boolean sinTexto = req.destinoTexto() == null || req.destinoTexto().isBlank();
        if (sinCliente && sinTexto)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El envío necesita un destino (cliente o texto)");
        EnvioDAO.ResultadoLote r = envioDao.enviarLote(req.idCli(), req.destinoTexto(), req.referencia(), req.imeis(), principal.getIdUsu());
        if (r.idEnvio() != null) {
            long enviados = r.items().stream().filter(i -> "ENVIADO".equals(i.resultado())).count();
            logDao.insertar(principal.getIdUsu(), "ENVIAR_TELEFONOS",
                    "ID_ENVIO: " + r.idEnvio() + ", DESTINO: " + (req.idCli() != null ? "CLI " + req.idCli() : req.destinoTexto())
                    + (req.referencia() != null && !req.referencia().isBlank() ? ", REF: " + req.referencia() : "")
                    + ", ENVIADOS: " + enviados + "/" + r.items().size());
        }
        return r;
    }

    private record EnvioRequest(Integer idCli, String destinoTexto, String referencia, List<String> imeis) {}
}
```

Nota: verificar el paquete real de `UsuarioPrincipal` con `grep -rn "class UsuarioPrincipal" src/main/java` y ajustar el import (los demás controllers lo importan — copiar el suyo).

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/dao/EnvioDAO.java src/main/java/com/reparaciones/servidor/controller/EnvioController.java src/test/java/com/reparaciones/servidor/dao/EnvioDAOTest.java
git commit -m "feat(f2c): EnvioDAO y POST /api/envios con reglas por IMEI"
```

---

### Task 4: Servidor — devoluciones + GET movimientos

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/EnvioDAO.java` (método `devolver`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java` (endpoints devoluciones + movimientos)
- Test: ampliar `EnvioDAOTest.java`

**Interfaces:**
- Consumes: T1 (`MovimientoDAO`), T3 (`EnvioDAO`), `LogDAO#insertar(idUsu, accion, detalle, motivo)` (4 args).
- Produces (para T6/T8/T10 cliente): `EnvioDAO.ItemDevolucion` record `(String imei, String resultado, Integer envio)` con resultado ∈ `DEVUELTO|NO_ENVIADO|NO_EXISTE`; `EnvioDAO#devolver(String imei, String motivo, int idUsu) → ItemDevolucion`; `POST /api/telefonos/devoluciones` body `{"items":[{"imei":"...","motivo":"..."}]}` → `[{"imei","resultado","envio"}]`; `GET /api/telefonos/{imei}/movimientos` → array de movimientos `{idMov, imei, ubicacionOrigen, ubicacionDestino, fecha, idUsu, usuario, motivo, referencia}` (cualquier autenticado, sin PreAuthorize — como el GET revisión).

- [ ] **Step 1: Test RED — reglas de la devolución**

Ampliar `EnvioDAOTest`:

```java
    @Test void devolucionMarcaPuenteYVuelveAlAlmacen() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'RECIBIDO', ES_DEVOLUCION = 1 WHERE IMEI = ? AND ESTADO = 'ENVIADO'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("FROM Envio_Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ 42, 9 }));   // ID_ET, ID_ENVIO
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ItemDevolucion r = new EnvioDAO(jdbc, mov).devolver(IMEI, "pantalla amarilla", 3);
        assertEquals("DEVUELTO", r.resultado());
        assertEquals(9, r.envio());
        verify(jdbc).update("UPDATE Envio_Telefono SET DEVUELTO = 1, MOTIVO_DEVOLUCION = ?, FECHA_DEVOLUCION = NOW(), ID_USU_DEVOLUCION = ? WHERE ID_ET = ?",
                "pantalla amarilla", 3, 42);
        verify(mov).registrar(IMEI, "ENVIADO", "ALMACEN", 3, "pantalla amarilla", "ENVIO 9");
    }

    @Test void devolucionSinPuenteActivaSeProcesaConEnvioVacio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'RECIBIDO', ES_DEVOLUCION = 1 WHERE IMEI = ? AND ESTADO = 'ENVIADO'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("FROM Envio_Telefono"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ItemDevolucion r = new EnvioDAO(jdbc, mov).devolver(IMEI, "sin caja", 3);
        assertEquals("DEVUELTO", r.resultado());
        assertNull(r.envio());
        verify(mov).registrar(IMEI, "ENVIADO", "ALMACEN", 3, "sin caja", null);
    }

    @Test void noEnviadoYNoExistente() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("SET ESTADO = 'RECIBIDO'"), eq(IMEI))).thenReturn(0);
        when(jdbc.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ "OK", null }));
        assertEquals("NO_ENVIADO", new EnvioDAO(jdbc, mock(MovimientoDAO.class)).devolver(IMEI, "x", 3).resultado());
        JdbcTemplate sin = mock(JdbcTemplate.class);
        when(sin.update(contains("SET ESTADO = 'RECIBIDO'"), eq(IMEI))).thenReturn(0);
        when(sin.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        assertEquals("NO_EXISTE", new EnvioDAO(sin, mock(MovimientoDAO.class)).devolver(IMEI, "x", 3).resultado());
    }
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=EnvioDAOTest
```
Expected: FAIL de compilación (`devolver`/`ItemDevolucion` no existen).

- [ ] **Step 3: Implementar `devolver` en EnvioDAO**

```java
    public record ItemDevolucion(String imei, String resultado, Integer envio) {}

    /**
     * Devolución post-envío (spec §3.4): vuelve al ALMACÉN (RECIBIDO) marcada como
     * devolución; entra a revisión más tarde por el masivo normal. Caso borde: ENVIADO
     * sin puente activa (pre-F2c/manual) se procesa con envío de origen vacío.
     */
    @Transactional
    public ItemDevolucion devolver(String imei, String motivo, int idUsu) {
        int flip = jdbc.update("UPDATE Telefono SET ESTADO = 'RECIBIDO', ES_DEVOLUCION = 1 WHERE IMEI = ? AND ESTADO = 'ENVIADO'", imei);
        if (flip == 0) {
            List<Object[]> fila = jdbc.query("SELECT ESTADO, ID_CLI FROM Telefono WHERE IMEI = ?",
                    (rs, row) -> new Object[]{ rs.getString("ESTADO"), (Integer) rs.getObject("ID_CLI") }, imei);
            return new ItemDevolucion(imei, fila.isEmpty() ? "NO_EXISTE" : "NO_ENVIADO", null);
        }
        List<Object[]> puente = jdbc.query(
                "SELECT ID_ET, ID_ENVIO FROM Envio_Telefono WHERE IMEI = ? AND DEVUELTO = 0 ORDER BY ID_ET DESC LIMIT 1",
                (rs, row) -> new Object[]{ rs.getInt("ID_ET"), rs.getInt("ID_ENVIO") }, imei);
        Integer idEnvio = null;
        if (!puente.isEmpty()) {
            int idEt = (Integer) puente.get(0)[0];
            idEnvio = (Integer) puente.get(0)[1];
            jdbc.update("UPDATE Envio_Telefono SET DEVUELTO = 1, MOTIVO_DEVOLUCION = ?, FECHA_DEVOLUCION = NOW(), ID_USU_DEVOLUCION = ? WHERE ID_ET = ?",
                    motivo, idUsu, idEt);
        }
        movimientoDao.registrar(imei, "ENVIADO", "ALMACEN", idUsu, motivo, idEnvio != null ? "ENVIO " + idEnvio : null);
        return new ItemDevolucion(imei, "DEVUELTO", idEnvio);
    }
```

- [ ] **Step 4: GREEN**

```bash
mvn -q test -Dtest=EnvioDAOTest
```
Expected: PASS.

- [ ] **Step 5: Endpoints en TelefonoController**

Inyectar `EnvioDAO envioDao` y `MovimientoDAO movimientoDao` por constructor (junto a los existentes). Añadir:

```java
    /** F2c: registro masivo de devoluciones — cada teléfono vuelve al almacén marcado. */
    @PostMapping("/devoluciones")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public List<com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion> devoluciones(
            @RequestBody DevolucionesRequest req, @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion> out = new java.util.ArrayList<>();
        for (DevolucionItem item : req.items()) {
            com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion r = envioDao.devolver(item.imei(), item.motivo(), principal.getIdUsu());
            if ("DEVUELTO".equals(r.resultado())) {
                logDao.insertar(principal.getIdUsu(), "DEVOLUCION_TELEFONO",
                        "IMEI: " + item.imei() + (r.envio() != null ? ", ENVIO: " + r.envio() : ""), item.motivo());
            }
            out.add(r);
        }
        return out;
    }

    /** F2c: línea de vida del teléfono para el historial de la ficha. */
    @GetMapping("/{imei}/movimientos")
    public List<com.reparaciones.servidor.model.MovimientoTelefono> getMovimientos(@PathVariable String imei) {
        return movimientoDao.getPorImei(imei);
    }
```

Records privados nuevos (junto a los existentes): `private record DevolucionesRequest(java.util.List<DevolucionItem> items) {}` y `private record DevolucionItem(String imei, String motivo) {}`.

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/servidor/dao/EnvioDAO.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/test/java/com/reparaciones/servidor/dao/EnvioDAOTest.java
git commit -m "feat(f2c): devoluciones al almacen con puente marcada + GET movimientos"
```

---

### Task 5: Servidor — retirada REVISION_LOGISTICA + veto bloqueoOp + log COMPONENTES

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-f2c-drop-check.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (quitar columna de `Telefono`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (L54, L932, L141)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/TelefonoDAO.java` (L98-106, L138, L186)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/TelefonoInventario.java` y `model/ReparacionResumen.java` (campo `revisionLogistica` fuera)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java` (PUT no-op ~L122)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/RevisionDAO.java` (veto en `marcarOk`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (COMPONENTES en `/completa` L196 y `/filas` L363)
- Test: ampliar `RevisionDAOEstadoTest.java`

**Interfaces:**
- Consumes: `ComponenteDAO#getTipoById(int) → String` (existe, L278-281).
- Produces: `marcarOk` veta también con `FUN_BLOQUEO_OP` (409 "Bloqueo de operador marcado en la revisión"); `PUT /{imei}/revision-logistica` = 204 no-op (compat clientes ≤v0.16, eliminar en F3); JSON de historial/inventario YA NO llevan `revisionLogistica` (los clientes viejos lo ignoran — default false, cosmético).

- [ ] **Step 1: Test RED — veto bloqueoOp**

En `RevisionDAOEstadoTest`, ampliar el helper `conVigente` con una sobrecarga `conVigente(Integer bateria, boolean ambasPartes, int abiertos, boolean bloqueoOp)` que además haga `r.setFunBloqueoOp(bloqueoOp)` (la existente delega con `false`). Añadir:

```java
    @Test void okVetadoConBloqueoOperadorMarcado() {
        RevisionDAO dao = new RevisionDAO(conVigente(92, true, 0, true), mock(MovimientoDAO.class));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.marcarOk(IMEI, 3));
        assertTrue(ex.getReason().contains("operador"));
    }
```

- [ ] **Step 2: RED**

```bash
mvn -q test -Dtest=RevisionDAOEstadoTest
```
Expected: FAIL (el veto no existe — marcarOk pasa).

- [ ] **Step 3: Implementar el veto**

En `marcarOk`, tras el check de batería y antes del de trabajos abiertos:

```java
        if (v.isFunBloqueoOp())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bloqueo de operador marcado en la revisión");
```

GREEN: `mvn -q test -Dtest=RevisionDAOEstadoTest`.

- [ ] **Step 4: Retirada de REVISION_LOGISTICA**

1. `sql/migracion-f2c-drop-check.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2c-drop-check.sql — F2c: retirar el check antiguo REVISION_LOGISTICA
-- APLICAR SOLO DESPUÉS de desplegar el servidor F2c Y actualizar los clientes
-- (el servidor viejo aún escribe la columna; el nuevo ya no la usa).
-- 1) Vista previa: debe devolver 1 (la columna existe todavía).
-- 2) Ejecutar el ALTER.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Telefono'
   AND COLUMN_NAME = 'REVISION_LOGISTICA';

ALTER TABLE Telefono DROP COLUMN REVISION_LOGISTICA;
```

2. `crear_bd.sql`: eliminar la línea `REVISION_LOGISTICA  BOOLEAN      NOT NULL DEFAULT FALSE,` del CREATE de `Telefono`.
3. `ReparacionDAO`: eliminar la línea `" COALESCE(tel.REVISION_LOGISTICA, 0) AS REVISION_LOGISTICA," +` en los DOS selects (L54 `HISTORIAL_SELECT` y L932 `GLASS_HISTORIAL_SELECT`) y la línea defensiva del mapper (L141). Verificar: `grep -n "REVISION_LOGISTICA" src/main/java/ | wc -l` → 0 al terminar la task (tras los puntos 4-6).
4. `TelefonoDAO`: eliminar el método `actualizarRevisionLogistica` (L98-106), quitar `t.REVISION_LOGISTICA,` del SELECT del inventario (L138) y la línea del mapper (L186).
5. Modelos servidor: quitar campo+getter+setter `revisionLogistica` de `TelefonoInventario` y `ReparacionResumen`.
6. `TelefonoController` (~L122): el PUT queda no-op tolerante:

```java
    /**
     * F2c: el check antiguo de revisión ya no existe (lo sustituye el ciclo de F2b).
     * Se mantiene como no-op tolerante para clientes ≤v0.16 durante la ventana de
     * actualización; ELIMINAR en F3 (pasada de autorización).
     */
    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req) {
        // no-op
    }
```

(quitar el parámetro `principal` y los imports que queden muertos; el record `RevisionLogisticaRequest` se queda — parsea el body).

- [ ] **Step 5: COMPONENTES en los 2 logs con filas**

`ReparacionController`: inyectar `ComponenteDAO componenteDao` por constructor. Helper privado al final de la clase:

```java
    /** Tipos de los componentes consumidos, para el detalle del log ("" si no hay filas con pieza). */
    private String componentesDe(List<FilaReparacion> filas) {
        if (filas == null) return "";
        String tipos = filas.stream()
                .filter(f -> f.getIdCom() > 0)
                .map(f -> { try { return componenteDao.getTipoById(f.getIdCom()); } catch (Exception e) { return "?"; } })
                .collect(java.util.stream.Collectors.joining(", "));
        return tipos.isEmpty() ? "" : ", COMPONENTES: " + tipos;
    }
```

En `/completa` (L196-209): el detalle del log pasa a `... + ", TECNICO: " + tecnico + componentesDe(req.filas())`. En `/{idAsignacion}/filas` (L363-376): ídem con `componentesDe(req.filas())`. `PATCH /{idRep}/completar` NO se toca (no consume filas — spec §5).

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add sql/migracion-f2c-drop-check.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/ src/test/java/com/reparaciones/servidor/dao/RevisionDAOEstadoTest.java
git commit -m "feat(f2c): retirar check antiguo (no-op tolerante), veto OK con bloqueo operador y log con componentes"
```

---

### Task 6: Cliente — modelos, DAO HTTP y textos de resultado

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/MovimientoTelefono.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ItemEnvio.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ItemDevolucion.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java` (métodos nuevos, tras el bloque F2b L163-214)
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/TextoResultadoEnvio.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/TextoResultadoDevolucion.java`
- Test: Create `src/test/java/com/reparaciones/utils/TextoResultadoEnvioTest.java` y `TextoResultadoDevolucionTest.java`

**Interfaces:**
- Consumes: JSON del servidor T3/T4 (nombres exactos: envíos → `{idEnvio, items:[{imei, resultado, estado}]}`; devoluciones → `[{imei, resultado, envio}]`; movimientos → `[{idMov, imei, ubicacionOrigen, ubicacionDestino, fecha, idUsu, usuario, motivo, referencia}]`). `ApiClient.post(String, Object, Class)` (L223) y `ApiClient.getList(String, Class)` (L144).
- Produces (para T7-T10): `TelefonoDAO#enviarTelefonos(Integer idCli, String destinoTexto, String referencia, List<String> imeis) → ResultadoEnvioLote` (POJO `{Integer idEnvio; List<ItemEnvio> items;}` — clase interna pública del DAO o modelo propio: modelo propio `models/ResultadoEnvioLote.java`), `#registrarDevoluciones(List<ItemDevolucionRequest>)` — usar `List<Map<String,String>>` con claves `imei`/`motivo` para el body (sin modelo request), `→ List<ItemDevolucion>`, `#getMovimientos(String imei) → List<MovimientoTelefono>`; `TextoResultadoEnvio.texto(String resultado, String estado)` y `.esEnviado(String)`; `TextoResultadoDevolucion.texto(String resultado, Integer envio)` y `.esDevuelto(String)`.

- [ ] **Step 1: Test RED — textos**

`TextoResultadoEnvioTest.java`:

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoEnvioTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ ENVIADO", TextoResultadoEnvio.texto("ENVIADO", null));
        assertEquals("rechazado: está En revisión — solo se envían teléfonos OK",
                TextoResultadoEnvio.texto("NO_OK", "EN_REVISION"));
        assertEquals("rechazado: está Bloqueado — solo se envían teléfonos OK",
                TextoResultadoEnvio.texto("NO_OK", "BLOQUEADO"));
        assertEquals("rechazado: histórico — dar de alta en un lote", TextoResultadoEnvio.texto("HISTORICO", null));
        assertEquals("no existe en el sistema", TextoResultadoEnvio.texto("NO_EXISTE", null));
        assertEquals("resultado desconocido", TextoResultadoEnvio.texto("???", null));
    }

    @Test void soloEnviadoCuentaComoExito() {
        assertTrue(TextoResultadoEnvio.esEnviado("ENVIADO"));
        assertFalse(TextoResultadoEnvio.esEnviado("NO_OK"));
        assertFalse(TextoResultadoEnvio.esEnviado(null));
    }
}
```

`TextoResultadoDevolucionTest.java`:

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoResultadoDevolucionTest {

    @Test void textosDeCadaResultado() {
        assertEquals("→ ALMACÉN (devolución del envío 9)", TextoResultadoDevolucion.texto("DEVUELTO", 9));
        assertEquals("→ ALMACÉN (devolución, sin envío registrado)", TextoResultadoDevolucion.texto("DEVUELTO", null));
        assertEquals("rechazado: no está enviado", TextoResultadoDevolucion.texto("NO_ENVIADO", null));
        assertEquals("no existe en el sistema", TextoResultadoDevolucion.texto("NO_EXISTE", null));
        assertEquals("resultado desconocido", TextoResultadoDevolucion.texto("???", null));
    }

    @Test void soloDevueltoCuentaComoExito() {
        assertTrue(TextoResultadoDevolucion.esDevuelto("DEVUELTO"));
        assertFalse(TextoResultadoDevolucion.esDevuelto("NO_ENVIADO"));
    }
}
```

- [ ] **Step 2: RED**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente
mvn -q test -Dtest='TextoResultadoEnvioTest,TextoResultadoDevolucionTest'
```
Expected: FAIL (clases no existen).

- [ ] **Step 3: Implementar textos**

`utils/TextoResultadoEnvio.java`:

```java
package com.reparaciones.utils;

/** Textos de UI del resultado del envío masivo (enum del servidor como string). */
public final class TextoResultadoEnvio {

    private TextoResultadoEnvio() {}

    public static String texto(String resultado, String estado) {
        return switch (resultado == null ? "" : resultado) {
            case "ENVIADO"   -> "→ ENVIADO";
            case "NO_OK"     -> "rechazado: está " + textoEstado(estado) + " — solo se envían teléfonos OK";
            case "HISTORICO" -> "rechazado: histórico — dar de alta en un lote";
            case "NO_EXISTE" -> "no existe en el sistema";
            default          -> "resultado desconocido";
        };
    }

    private static String textoEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "RECIBIDO"    -> "Recibido";
            case "EN_REVISION" -> "En revisión";
            case "BLOQUEADO"   -> "Bloqueado";
            case "ENVIADO"     -> "Enviado";
            case "DESGUACE"    -> "Desguace";
            default            -> estado == null ? "?" : estado;
        };
    }

    public static boolean esEnviado(String resultado) { return "ENVIADO".equals(resultado); }
}
```

`utils/TextoResultadoDevolucion.java`:

```java
package com.reparaciones.utils;

/** Textos de UI del resultado del registro de devoluciones. */
public final class TextoResultadoDevolucion {

    private TextoResultadoDevolucion() {}

    public static String texto(String resultado, Integer envio) {
        return switch (resultado == null ? "" : resultado) {
            case "DEVUELTO"   -> envio != null
                    ? "→ ALMACÉN (devolución del envío " + envio + ")"
                    : "→ ALMACÉN (devolución, sin envío registrado)";
            case "NO_ENVIADO" -> "rechazado: no está enviado";
            case "NO_EXISTE"  -> "no existe en el sistema";
            default           -> "resultado desconocido";
        };
    }

    public static boolean esDevuelto(String resultado) { return "DEVUELTO".equals(resultado); }
}
```

- [ ] **Step 4: Modelos y DAO**

1. `models/MovimientoTelefono.java` — POJO Gson espejo del servidor (mismos nombres): `int idMov; String imei; String ubicacionOrigen; String ubicacionDestino; LocalDateTime fecha; Integer idUsu; String usuario; String motivo; String referencia;` + getters/setters + constructor vacío.
2. `models/ItemEnvio.java`: `String imei; String resultado; String estado;` + getters/setters. `models/ItemDevolucion.java`: `String imei; String resultado; Integer envio;` + getters/setters. `models/ResultadoEnvioLote.java`: `Integer idEnvio; List<ItemEnvio> items;` + getters/setters.
3. `TelefonoDAO` (cliente), tras el bloque F2b:

```java
    /** F2c: remesa de salida. Devuelve id de envío creado (o null) y resultado por IMEI. */
    public com.reparaciones.models.ResultadoEnvioLote enviarTelefonos(Integer idCli, String destinoTexto,
            String referencia, java.util.List<String> imeis) throws SQLException {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("idCli", idCli);
        body.put("destinoTexto", destinoTexto);
        body.put("referencia", referencia);
        body.put("imeis", imeis);
        return ApiClient.post("/api/envios", body, com.reparaciones.models.ResultadoEnvioLote.class);
    }

    /** F2c: registro masivo de devoluciones (cada item con su motivo). */
    public java.util.List<com.reparaciones.models.ItemDevolucion> registrarDevoluciones(
            java.util.List<java.util.Map<String, String>> items) throws SQLException {
        com.reparaciones.models.ItemDevolucion[] res = ApiClient.post(
                "/api/telefonos/devoluciones", java.util.Map.of("items", items),
                com.reparaciones.models.ItemDevolucion[].class);
        return java.util.Arrays.asList(res);
    }

    /** F2c: línea de vida del teléfono para el historial de la ficha. */
    public java.util.List<com.reparaciones.models.MovimientoTelefono> getMovimientos(String imei) throws SQLException {
        return ApiClient.getList("/api/telefonos/" + imei + "/movimientos",
                com.reparaciones.models.MovimientoTelefono.class);
    }
```

- [ ] **Step 5: GREEN + suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/models/MovimientoTelefono.java src/main/java/com/reparaciones/models/ItemEnvio.java src/main/java/com/reparaciones/models/ItemDevolucion.java src/main/java/com/reparaciones/models/ResultadoEnvioLote.java src/main/java/com/reparaciones/dao/TelefonoDAO.java src/main/java/com/reparaciones/utils/TextoResultadoEnvio.java src/main/java/com/reparaciones/utils/TextoResultadoDevolucion.java src/test/java/com/reparaciones/utils/TextoResultadoEnvioTest.java src/test/java/com/reparaciones/utils/TextoResultadoDevolucionTest.java
git commit -m "feat(f2c): modelos de envio/devolucion/movimiento, metodos DAO y textos de resultado"
```

---

### Task 7: Cliente — EnvioDialog + multiselección acotada + entradas

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EnvioDialog.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/AgrupadoView.fxml` (botón junto a `btnAltaManual`, L26-27)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (campo botón + gating en `configurar()` L226-234, `SelectionMode.MULTIPLE` en `initialize()` L178-191, MenuItem "Enviar seleccionados" en `configurarFilas()` L832+)

**Interfaces:**
- Consumes: `TelefonoDAO#enviarTelefonos` (T6), `TextoResultadoEnvio` (T6), `ImeiUtils.parsearPegadoImeis` + `ResultadoPegado`/`TipoPegado`, `ClienteDAO#getActivos()` (para el selector de cliente; verificar firma real con grep), patrón ventana/hilos `ARevisarDialog` (Stage APPLICATION_MODAL + Thread + Platform.runLater), `Alertas.mostrarError`, `Sesion.esSuperTecnico()`, `ConfigVistaAgrupado.Vista`.
- Produces: `EnvioDialog.abrir(Window owner, List<TelefonoInventario> preseleccion, Runnable onCambios)` (preseleccion puede ser vacía/null; onCambios al cerrar si hubo algún ENVIADO).

- [ ] **Step 1: Implementar `EnvioDialog` (reglas de construcción, patrón ARevisarDialog)**

- Stage APPLICATION_MODAL con owner, título `"Enviar teléfonos — remesa"`, stylesheet `/styles/app.css`, `setResizable(false)`.
- **Cabecera de remesa** (GridPane/HBox): ComboBox de cliente (items `ClienteDAO.getActivos()`, cargados en hilo; opción vacía "— sin cliente —" al principio) + TextField `tfDestino` (promptText "Destino libre (mayorista/plataforma)") + TextField `tfReferencia` (promptText "Referencia (albarán/tracking)"). Regla: cliente y texto NO son excluyentes en la UI; la validación al confirmar exige AL MENOS uno (cliente elegido o texto no vacío) — si falta, `lblScan` en rojo "El envío necesita destino (cliente o texto)".
- **Escáner** `tfScan` (calco del listener de `ARevisarDialog`: solo dígitos con guard de recursión vía `Platform.runLater`, pegado masivo con `parsearPegadoImeis`, CORRUPTO → aviso). A diferencia de A-revisar, escanear NO llama al servidor: añade el IMEI a la lista local (dedupe con `LinkedHashSet vistos`).
- **Lista** `ListView<String>` (520x300) con los IMEIs añadidos; menú contextual "Quitar" que elimina fila y su entrada en `vistos`. La `preseleccion` (si llega) puebla la lista al abrir (IMEIs de los `TelefonoInventario`).
- **Pie**: `lblContador` ("N teléfonos"), spacer, `btnEnviar` ("Enviar remesa", btn-primary, disabled con lista vacía) y `btnCerrar` ("Cerrar").
- **Confirmar**: valida destino → deshabilita `btnEnviar` (guard doble-click) → hilo → `dao.enviarTelefonos(idCli, destinoTexto, referencia, imeis)` → `Platform.runLater`: la lista pasa a modo resultados (cada fila `imei + "  ·  " + TextoResultadoEnvio.texto(...)`), contador "X enviados · Y rechazados", `huboCambios=true` si algún ENVIADO, `btnEnviar` se oculta (la remesa es una: para otra, cerrar y reabrir). `SQLException` → `Alertas.mostrarError` + re-habilitar `btnEnviar`.
- Al cerrar (`setOnHidden`): si `huboCambios` → `onCambios.run()`.

- [ ] **Step 2: Botón en la barra + gating**

`AgrupadoView.fxml`, tras `btnAltaManual` (L27):

```xml
<Button fx:id="btnEnviar" text="Enviar (masivo)" styleClass="btn-secondary" onAction="#enviarMasivo" visible="false" managed="false"/>
```

`AgrupadoController`: campo `@FXML private Button btnEnviar;` junto a `btnImportar`/`btnAltaManual` (L102-103). En `configurar()` (L226-234), misma condición de visibilidad que los botones de importación (`ConfigVistaAgrupado.botonesImportacion(vista) && esSuper` — leer la condición real y calcarla). Handler:

```java
    @FXML
    private void enviarMasivo() {
        EnvioDialog.abrir(tabla.getScene().getWindow(), java.util.List.of(), this::cargar);
    }
```

- [ ] **Step 3: Multiselección acotada + "Enviar seleccionados (N)"**

1. En `initialize()` (junto a L178-191): `tabla.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);`
2. En `configurarFilas()` (L832-840): `MenuItem enviarSel = new MenuItem("Enviar seleccionados");` tras `fichaRev` (L840); añadirlo al `menu.getItems().addAll(...)` tras `fichaRev`; en la rama de grupo del `setOnShowing` (L898-918):

```java
                java.util.List<TelefonoInventario> sel = seleccionInventario();
                enviarSel.setVisible(esGrupo && modoActual == Modo.MAESTRO
                        && vista == ConfigVistaAgrupado.Vista.INVENTARIO
                        && Sesion.esSuperTecnico() && !sel.isEmpty());
                enviarSel.setText("Enviar seleccionados (" + sel.size() + ")");
```

y en la rama `ReparacionResumen`, `enviarSel.setVisible(false);` (simetría con `fichaRev`, reciclado de celdas). Handler:

```java
                enviarSel.setOnAction(e -> {
                    java.util.List<TelefonoInventario> seleccion = seleccionInventario();
                    if (!seleccion.isEmpty())
                        EnvioDialog.abrir(getScene().getWindow(), seleccion, AgrupadoController.this::cargar);
                });
```

3. Helper privado en `AgrupadoController`:

```java
    /** Filas TelefonoInventario de la selección múltiple (ignora filas de otros tipos). */
    private java.util.List<TelefonoInventario> seleccionInventario() {
        java.util.List<TelefonoInventario> out = new java.util.ArrayList<>();
        for (Object o : tabla.getSelectionModel().getSelectedItems())
            if (o instanceof TelefonoInventario t) out.add(t);
        return out;
    }
```

REGLA: ningún otro MenuItem cambia de comportamiento (siguen sobre la fila bajo el cursor). Decisión de plan nº3.

- [ ] **Step 4: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/controllers/EnvioDialog.java src/main/resources/views/AgrupadoView.fxml src/main/java/com/reparaciones/controllers/AgrupadoController.java
git commit -m "feat(f2c): EnvioDialog con remesa, escaner y multiseleccion acotada"
```
Expected: BUILD SUCCESS, misma cuenta de tests (UI sin test unitario, cubierta por smoke).

---

### Task 8: Cliente — DevolucionDialog

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/DevolucionDialog.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/AgrupadoView.fxml` (botón tras `btnEnviar`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (campo + gating + handler, calco de T7)

**Interfaces:**
- Consumes: `TelefonoDAO#registrarDevoluciones` (T6), `TextoResultadoDevolucion` (T6), patrón `EnvioDialog`/`ARevisarDialog`.
- Produces: `DevolucionDialog.abrir(Window owner, Runnable onCambios)` (onCambios si hubo algún DEVUELTO).

- [ ] **Step 1: Implementar `DevolucionDialog` (reglas)**

- Stage APPLICATION_MODAL, título `"Registrar devolución"`, patrón de T7.
- **Cabecera**: TextField `tfMotivoComun` (promptText "Motivo común (se copia a cada teléfono)").
- **Escáner** `tfScan` (calco T7: solo dígitos + pegado masivo, dedupe local): añade filas a una `TableView` de dos columnas: IMEI (solo lectura) y Motivo (celda editable `TextFieldTableCell`, inicializada con el valor de `tfMotivoComun` en el momento de añadir; editable por fila). Menú contextual "Quitar".
- **Pie**: contador "N devoluciones", `btnRegistrar` ("Registrar", btn-primary, disabled con tabla vacía; guard doble-click) y `btnCerrar`.
- **Confirmar**: hilo → `dao.registrarDevoluciones(items)` (cada item `Map.of("imei", ..., "motivo", ...)`; motivo vacío → mandar el común; ambos vacíos → mandar "") → `Platform.runLater`: tabla pasa a resultados (columna Motivo sustituida por el texto `TextoResultadoDevolucion.texto(resultado, envio)` — más simple: la tabla se reemplaza por un `ListView` de resultados como en T7), contador "X devueltas · Y rechazadas", `huboCambios` si algún DEVUELTO. Error → `Alertas.mostrarError` + re-habilitar.
- `setOnHidden` → `onCambios` si `huboCambios`.

- [ ] **Step 2: Botón + gating**

FXML tras `btnEnviar`: `<Button fx:id="btnDevolucion" text="Registrar devolución" styleClass="btn-secondary" onAction="#registrarDevolucion" visible="false" managed="false"/>`. Controller: campo + misma visibilidad que `btnEnviar` en `configurar()` + handler `DevolucionDialog.abrir(tabla.getScene().getWindow(), this::cargar)`.

- [ ] **Step 3: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/controllers/DevolucionDialog.java src/main/resources/views/AgrupadoView.fxml src/main/java/com/reparaciones/controllers/AgrupadoController.java
git commit -m "feat(f2c): DevolucionDialog masivo con motivo por fila"
```

---

### Task 9: Cliente — tags bajo la píldora + CSV

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ChipsEstado.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (`configurarColEstado` L688-725, `valoresCsvMaestro` L1725-1747)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ConfigVistaAgrupado.java` (`CSV_INVENTARIO` L25-28)
- Test: Create `src/test/java/com/reparaciones/utils/ChipsEstadoTest.java`; Modify `ConfigVistaAgrupadoTest` si pina la cabecera CSV

**Interfaces:**
- Consumes: `TelefonoInventario` cliente (`isEsDevolucion()` L79-80 — ya existe; contadores de trabajos abiertos: verificar getters reales con `grep -n "Abiertos" src/main/java/com/reparaciones/models/TelefonoInventario.java` — F2a los trae; si los nombres difieren de `getPulAbiertos/getGlassAbiertos/getNormalAbiertos`, adaptar aquí y en el test).
- Produces: `ChipsEstado.de(TelefonoInventario t) → List<String>` (chips en orden: `"devolución"` si `isEsDevolucion()`; y si `"EN_REPARACION".equals(getEstadoEfectivo())`: `"rep"`, `"glass"`, `"pulido"` según contador > 0).

- [ ] **Step 1: Test RED**

`ChipsEstadoTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChipsEstadoTest {

    private TelefonoInventario tel(boolean devolucion, String efectivo, int rep, int glass, int pul) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEsDevolucion(devolucion);
        t.setEstadoEfectivo(efectivo);
        t.setNormalAbiertos(rep);
        t.setGlassAbiertos(glass);
        t.setPulAbiertos(pul);
        return t;
    }

    @Test void devolucionSola() {
        assertEquals(List.of("devolución"), ChipsEstado.de(tel(true, "RECIBIDO", 0, 0, 0)));
    }

    @Test void tiposSoloEnReparacion() {
        assertEquals(List.of("rep", "glass"), ChipsEstado.de(tel(false, "EN_REPARACION", 2, 1, 0)));
        assertEquals(List.of("pulido"),       ChipsEstado.de(tel(false, "EN_REPARACION", 0, 0, 1)));
        assertTrue(ChipsEstado.de(tel(false, "REVISADO", 1, 0, 0)).isEmpty());
    }

    @Test void devolucionYTiposConviven() {
        assertEquals(List.of("devolución", "rep"), ChipsEstado.de(tel(true, "EN_REPARACION", 1, 0, 0)));
    }

    @Test void sinNadaListaVacia() {
        assertTrue(ChipsEstado.de(tel(false, "RECIBIDO", 0, 0, 0)).isEmpty());
    }
}
```

- [ ] **Step 2: RED + implementar**

```bash
mvn -q test -Dtest=ChipsEstadoTest
```
Expected: FAIL. Implementar:

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

import java.util.ArrayList;
import java.util.List;

/** Mini-chips bajo la píldora de estado (F2c): devolución + tipos de trabajo abierto. */
public final class ChipsEstado {

    private ChipsEstado() {}

    public static List<String> de(TelefonoInventario t) {
        List<String> chips = new ArrayList<>();
        if (t.isEsDevolucion()) chips.add("devolución");
        if ("EN_REPARACION".equals(t.getEstadoEfectivo())) {
            if (t.getNormalAbiertos() > 0) chips.add("rep");
            if (t.getGlassAbiertos() > 0)  chips.add("glass");
            if (t.getPulAbiertos() > 0)    chips.add("pulido");
        }
        return chips;
    }
}
```

GREEN: `mvn -q test -Dtest=ChipsEstadoTest`.

- [ ] **Step 3: Celda de estado con chips**

En `configurarColEstado` (L688-725): la celda pasa de un `Label badge` a un `VBox` (spacing 2, alignment CENTER_LEFT) con el badge arriba (estilos EXACTOS actuales del switch L700-706, sin tocar) y debajo, solo si `ChipsEstado.de(t)` no está vacío, un `HBox` (spacing 4) de mini-labels: estilo `-fx-font-size: 9px; -fx-padding: 0 4 0 4; -fx-background-radius: 6; -fx-background-color: #EEF1F6; -fx-text-fill: #586376;` — el chip `devolución` con `-fx-background-color: #FFEDD5; -fx-text-fill: #C2410C;` (naranja suave). Al reciclar celda sin item: `setGraphic(null)` (leer el patrón actual de la celda y conservarlo). El texto de la píldora sigue saliendo de `UbicacionTexto.estado(t)` — sin cambios de plumbing.

- [ ] **Step 4: CSV — swap de columna**

1. `ConfigVistaAgrupado.CSV_INVENTARIO` (L25-28): sustituir `"Revisión logística"` por `"Devolución"` (misma posición, la lista queda de 19).
2. `valoresCsvMaestro` (L1725-1747): sustituir la entrada `m.put("Revisión logística", ...)` por `m.put("Devolución", t.isEsDevolucion() ? "Sí" : "");`.
3. Si `ConfigVistaAgrupadoTest` pina la cabecera, actualizar el literal esperado.

- [ ] **Step 5: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/utils/ChipsEstado.java src/main/java/com/reparaciones/controllers/AgrupadoController.java src/main/java/com/reparaciones/controllers/ConfigVistaAgrupado.java src/test/java/com/reparaciones/utils/ChipsEstadoTest.java src/test/java/com/reparaciones/controllers/ConfigVistaAgrupadoTest.java
git commit -m "feat(f2c): chips bajo la pildora (devolucion + tipos) y columna Devolucion en CSV"
```

---

### Task 10: Cliente — historial en la ficha + flecos de la ficha + Abrir ficha

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/FormatoMovimiento.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java` (historial tras `zonaVeredicto` L159; aviso junto a `chkBloqueoOp` L321; tooltip `btnBloquear` L387-391; veto en `actualizarAcciones` L647-652)
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/RevisionPanelView.fxml` (botón en la FlowPane L12-17)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/RevisionPanelController.java` (campo + handler + binding selección)
- Test: Create `src/test/java/com/reparaciones/utils/FormatoMovimientoTest.java`

**Interfaces:**
- Consumes: `TelefonoDAO#getMovimientos` (T6), `MovimientoTelefono` (T6), `FechaUtils.formatear(LocalDateTime, DateTimeFormatter)` (UTC→Madrid), `VeredictoRevision.Veredicto#bloqueado()` (existe de F2b).
- Produces: `FormatoMovimiento.linea(MovimientoTelefono m, DateTimeFormatter fmt) → String` con formato exacto `"dd/MM HH:mm · ORIGEN → DESTINO · usuario"` + `" · motivo"` si hay motivo + `" (ref)"` si hay referencia; origen null → `"—"`.

- [ ] **Step 1: Test RED — formateo**

`FormatoMovimientoTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.MovimientoTelefono;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatoMovimientoTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private MovimientoTelefono mov(String origen, String destino, String usuario, String motivo, String ref) {
        MovimientoTelefono m = new MovimientoTelefono();
        m.setUbicacionOrigen(origen);
        m.setUbicacionDestino(destino);
        m.setFecha(LocalDateTime.of(2026, 7, 27, 10, 0));
        m.setUsuario(usuario);
        m.setMotivo(motivo);
        m.setReferencia(ref);
        return m;
    }

    @Test void lineaCompleta() {
        assertEquals(FechaUtils.formatear(LocalDateTime.of(2026, 7, 27, 10, 0), FMT)
                        + " · ENVIADO → ALMACEN · ana · pantalla amarilla (ENVIO 9)",
                FormatoMovimiento.linea(mov("ENVIADO", "ALMACEN", "ana", "pantalla amarilla", "ENVIO 9"), FMT));
    }

    @Test void origenNullYSinExtras() {
        assertEquals(FechaUtils.formatear(LocalDateTime.of(2026, 7, 27, 10, 0), FMT)
                        + " · — → ALMACEN · ana",
                FormatoMovimiento.linea(mov(null, "ALMACEN", "ana", null, null), FMT));
    }
}
```

- [ ] **Step 2: RED + implementar**

```bash
mvn -q test -Dtest=FormatoMovimientoTest
```
Expected: FAIL. Implementar:

```java
package com.reparaciones.utils;

import com.reparaciones.models.MovimientoTelefono;

import java.time.format.DateTimeFormatter;

/** Línea del historial de la ficha (F2c): fecha · de→a · quién · motivo (ref). */
public final class FormatoMovimiento {

    private FormatoMovimiento() {}

    public static String linea(MovimientoTelefono m, DateTimeFormatter fmt) {
        StringBuilder sb = new StringBuilder();
        sb.append(FechaUtils.formatear(m.getFecha(), fmt));
        sb.append(" · ").append(m.getUbicacionOrigen() == null ? "—" : m.getUbicacionOrigen());
        sb.append(" → ").append(m.getUbicacionDestino());
        sb.append(" · ").append(m.getUsuario());
        if (m.getMotivo() != null && !m.getMotivo().isBlank()) sb.append(" · ").append(m.getMotivo());
        if (m.getReferencia() != null && !m.getReferencia().isBlank()) sb.append(" (").append(m.getReferencia()).append(")");
        return sb.toString();
    }
}
```

GREEN: `mvn -q test -Dtest=FormatoMovimientoTest`.

- [ ] **Step 3: Historial en la ficha (reglas)**

En `FichaRevisionDialog`: un `TitledPane "Historial"` (colapsado, `setAnimated(false)`) insertado en el `VBox contenido` (L159) ENTRE `zonaVeredicto` y `pie`. Contenido: `ListView<String>` (alto ~140). Carga perezosa: listener a `expandedProperty()` — la primera expansión lanza hilo → `dao.getMovimientos(t.getImei())` → `Platform.runLater` puebla con `FormatoMovimiento.linea(m, FMT_CHIP)` (reutilizar el formatter de los chips) + `ventana.sizeToScene()`; error → item único "No se pudo cargar el historial". Cache: no recargar en expansiones siguientes (flag `historialCargado`). Disponible también en consulta (ADMIN): el historial NO se deshabilita con `editable=false`.

- [ ] **Step 4: Flecos de la ficha**

1. **Aviso del check** (junto a `chkBloqueoOp`, L321): `Label lblAvisoBloqueo` ("al guardar → BLOQUEADO", estilo `-fx-text-fill: #B83746; -fx-font-size: 10px;`), `visible/managed` bindeados a `chkBloqueoOp.selectedProperty()` (con `managedProperty().bind(visibleProperty())`), colocado en el mismo HBox del check.
2. **Tooltip del botón** (L387-391): `btnBloquear.setTooltip(new Tooltip("Apartar el teléfono con motivo (MS externa, pendiente de devolución…).\nEl bloqueo por operadora va por su check en la funcional."));`
3. **Veto espejo en btnOk** (L647-652): la condición pasa a `btnOk.setDisable(v.bateriaObligatoria() || bateriaNull || v.bloqueado());` (el `v.bloqueado()` del veredicto ya refleja `funBloqueoOp`).

- [ ] **Step 5: Botón "Abrir ficha" en el panel Revisión**

`RevisionPanelView.fxml` (FlowPane L12-17), tras `btnMasivo`:

```xml
<Button fx:id="btnAbrirFicha" text="Abrir ficha" styleClass="btn-secondary" onAction="#abrirFichaSeleccion" disable="true"/>
```

`RevisionPanelController`: campo `@FXML private Button btnAbrirFicha;`; en `initialize()`: `tabla.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> btnAbrirFicha.setDisable(n == null));`. Handler:

```java
    @FXML
    private void abrirFichaSeleccion() {
        TelefonoInventario t = tabla.getSelectionModel().getSelectedItem();
        if (t != null) abrirFicha(t);
    }
```

(el doble clic y el escáner se quedan tal cual; el botón se muestra a TODOS los roles del panel — la ficha ya es solo-lectura si no procede).

- [ ] **Step 6: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/utils/FormatoMovimiento.java src/main/java/com/reparaciones/controllers/FichaRevisionDialog.java src/main/resources/views/RevisionPanelView.fxml src/main/java/com/reparaciones/controllers/RevisionPanelController.java src/test/java/com/reparaciones/utils/FormatoMovimientoTest.java
git commit -m "feat(f2c): historial en la ficha, aviso del check, tooltip bloquear, veto espejo y boton Abrir ficha"
```

---

### Task 11: Cliente — retirar el check viejo + combos de filtro con estados nuevos

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/AgrupadoController.java` (columna `colRevision` L101/L211/L761-825; combos L1104-1112)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/TelefonoDAO.java` (quitar `actualizarRevisionLogistica` L121-124)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/TelefonoInventario.java` (quitar `revisionLogistica` L26/L100-101)
- Modify: FXML del maestro si declara la columna (localizar `colRevision` en `AgrupadoView.fxml` y quitarla)

**Interfaces:**
- Consumes: `UbicacionTexto` (textos de estado/ubicación EXACTOS — las opciones de filtro deben coincidir con lo que pinta la tabla).
- Produces: combos de filtro con las listas nuevas (ver Step 2); cero referencias a `revisionLogistica` en el cliente.

- [ ] **Step 1: Retirar la columna y el método**

1. `AgrupadoController`: eliminar campo `colRevision` (L101), su registro en `columnaPorClave` (L211), el método completo `configurarColRevision()` (L761-825) y su call site en `configurarColumnas()`. Buscar restos: `grep -n "colRevision\|configurarColRevision\|revisionLogistica\|actualizarRevisionLogistica" src/main/java/ src/main/resources/` → tras la task, 0 resultados.
2. `AgrupadoView.fxml`: eliminar la `TableColumn` con `fx:id="colRevision"` (localizar por grep).
3. `TelefonoDAO` cliente: eliminar `actualizarRevisionLogistica` (L121-124). `TelefonoInventario` cliente: eliminar campo/getter/setter `revisionLogistica` (L26, L100-101). Si `ConfigVistaAgrupado` gestiona la visibilidad de la columna por clave (buscar la clave de `colRevision` en sus listas de columnas por vista), quitar la entrada.

- [ ] **Step 2: Combos de filtro**

En `configurarFiltros()`:
1. L1104-1105 — filtro Estado, lista nueva (textos EXACTOS de `UbicacionTexto.ESTADOS` + "Histórico"):

```java
        filtroEstadoHandle = MultiSelectDropdown.setup(filtroEstado,
                List.of("Recibido", "En revisión", "Revisado", "Reparado", "En reparación",
                        "Bloqueado", "OK", "Enviado", "Desguace", "Histórico"), ...);
```

(conservar los argumentos restantes del `setup` tal cual están hoy).
2. L1111-1112 — filtro Ubicación, lista nueva (textos EXACTOS de `UbicacionTexto.UBICACIONES` — leer el map L13-15 y usar SUS valores; la lista esperada, verificar contra el map):

```java
        filtroUbicacionHandle = MultiSelectDropdown.setup(filtroUbicacion,
                List.of("Almacén", "Para revisar", "Bloqueo", "Reparaciones", "Listos", "Pedidos", UbicacionTexto.FUERA), ...);
```

3. Verificación manual obligada del implementador: cada literal de las dos listas debe existir como salida real de `UbicacionTexto.estado(...)` / `UbicacionTexto.ubicacion(...)`-equivalente (leer la clase); si algún texto difiere (p.ej. acentos), manda el de `UbicacionTexto`.

- [ ] **Step 3: Suite + commit**

```bash
mvn -q test
git add src/main/java/com/reparaciones/controllers/AgrupadoController.java src/main/resources/views/AgrupadoView.fxml src/main/java/com/reparaciones/dao/TelefonoDAO.java src/main/java/com/reparaciones/models/TelefonoInventario.java src/main/java/com/reparaciones/controllers/ConfigVistaAgrupado.java
git commit -m "feat(f2c): retirar check antiguo del inventario y combos de filtro con el ciclo completo"
```
(incluir `ConfigVistaAgrupado.java` en el add solo si se tocó.)

---

### Task 12: Cierre — suites finales, review y pasos del usuario

**Files:** ninguno nuevo (verificación + operativa).

- [ ] **Step 1: Suites finales en ambos repos**

```bash
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q test
cd /c/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente && mvn -q test
```
Expected: BUILD SUCCESS en ambos (anotar cifras reales).

- [ ] **Step 2: Review final de rama (superpowers:requesting-code-review)** — ambos repos, contra la spec; verificar contratos JSON campo a campo (envios, devoluciones, movimientos), la tabla de movimientos por transición (§5 de la spec, origen derivado del estado previo), la retirada completa de `REVISION_LOGISTICA` (greps a 0 en ambos repos salvo el no-op y su record), y las 3 decisiones de plan (transacción única por remesa; PUT no-op tolerante; multiselección acotada) — presentarlas al usuario con el veredicto.

- [ ] **Step 3: Pasos del usuario (en orden, cada uno con su OK):**

1. Script 1 (`migracion-f2c-envios.sql`) en la VM con vista previa (el SELECT debe dar 0).
2. Arranque Spring local con el jar de la rama (contexto limpio; endpoints nuevos responden 403 sin auth).
3. OK merge servidor (`--no-ff`) + push → `git pull` + build + restart systemd en la VM.
4. Smoke con cliente en rama (checklist spec §8: envío por escáner y por selección, devolución masiva con re-envío, historial, chips, veto operador, no-op del check con cliente v0.16, log con COMPONENTES, filtros nuevos, flecos de la ficha).
5. OK merge cliente + bump gitlink + push.
6. Script 2 (`migracion-f2c-drop-check.sql`) en la VM (vista previa debe dar 1) + verificación de arranque posterior.
7. MER (tablas `Envio`/`Envio_Telefono`, quitar `REVISION_LOGISTICA` de `Telefono`) + checkboxes plan-futuro + decisión tag v0.17.0.

- [ ] **Step 4: Ledger** — anotar cierre en `.superpowers/sdd/progress.md`.
